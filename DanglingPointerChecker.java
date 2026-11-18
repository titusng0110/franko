import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class DanglingPointerChecker {
    private static final String INTRA = "Intraprocedural: ";

    private final DiagnosticBag diagnostics;

    private MemoryAnalysisContext context;
    private DanglingPointerState state;
    private MemoryDataflowDriver driver;

    private long[] addressTaken;
    private long[] reportedDerefSites;
    private long[] reportedUseSites;

    public DanglingPointerChecker(DiagnosticBag diagnostics) {
        this(diagnostics, null);
    }

    public DanglingPointerChecker(
            DiagnosticBag diagnostics,
            MemoryAnalysisContext context
    ) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
        this.context = context;
    }

    public void check(SastNode node) {
        if (!(node.kind == SastKind.PROGRAM)) {
            throw new IllegalArgumentException(
                    "Dangling-pointer checking requires a semantic node root"
            );
        }

        check(new SastDocument(
                SastPhase.SEMANTIC,
                EntryKind.NONE,
                new SourceTable(),
                new SymbolRegistry(),
                node
        ));
    }

    public PassResult check(SastDocument document) {
        diagnostics.clear();
        ensureContext(document.root);

        runCheckPass();

        return new PassResult(
                document.withPhase(SastPhase.DANGLING),
                diagnostics.diagnostics()
        );
    }

    private void ensureContext(SastNode root) {
        if (context == null || context.root != root) {
            context = MemoryAnalysisContext.build(root);
            state = null;
            addressTaken = null;
            reportedDerefSites = null;
            reportedUseSites = null;
        }

        if (state == null) {
            state = new DanglingPointerState(context);
            addressTaken = new long[Math.max(1, (context.pointerVariableCount + 63) >>> 6)];
            reportedDerefSites = new long[Math.max(1, (context.siteCount + 63) >>> 6)];
            reportedUseSites = new long[Math.max(1, (context.siteCount + 63) >>> 6)];
        }
    }

    private void runCheckPass() {
        driver = new MemoryDataflowDriver(context, diagnostics, new MemoryDataflowDriver.Domain(new DanglingDomain()));
        driver.run(context.root);
    }

    final class DanglingDomain {
        private boolean suppressed;

        public void beginFunction(int functionId) {
            state.setFunction(functionId);
            state.reset();
            Arrays.fill(addressTaken, 0L);
            Arrays.fill(reportedDerefSites, 0L);
            Arrays.fill(reportedUseSites, 0L);
            suppressed = false;

            SastNode decl = context.functionDecl(functionId);

            if (decl == null) {
                return;
            }

            seedCheckParameters(decl);

            seedGlobals();
        }

        private boolean isOperatorOther(
                SastNode decl,
                int parameterIndex
        ) {
            return decl.symbol.kind == SymbolKind.METHOD
                    && OperatorMethods.isOperatorMethodName(decl.symbol.name)
                    && parameterIndex == 1;
        }

        private void seedCheckParameters(SastNode decl) {
            for (int i = 0; i < decl.parameterVariables.size(); i++) {
                Symbol parameter = decl.parameterVariables.get(i);
                int pointerIndex = state.pointerIndex(parameter);

                if (pointerIndex < 0) {
                    continue;
                }

                if (isOperatorOther(decl, i)) {
                    state.setPointer(
                            pointerIndex,
                            originFrame(context.variableId(parameter))
                    );
                } else {
                    state.setPointer(pointerIndex, originUnknown());
                }
            }
        }

        private void seedGlobals() {
            for (int globalIndex = 0;
                    globalIndex < context.globalVariableIds.length;
                    globalIndex++) {
                int variableId = context.globalVariableIds[globalIndex];
                int pointerIndex = state.pointerIndexByVariableId(variableId);

                if (pointerIndex < 0) {
                    continue;
                }

                state.setPointer(pointerIndex, originUnknown());
            }
        }

        public void endFunction(int exitCount, boolean fallthrough) {
        }

        public void enterBlock(SastNode node, int depth) {
            state.enterBlock(node, depth);
        }

        public void leaveBlock(
                SastNode node,
                int depth,
                boolean terminated
        ) {
            state.leaveBlock(node, depth, terminated);
        }

        public void markLocalsDeadForJump(int targetBlockDepth, int snapshotSlot) {
            state.markLocalsDeadForJump(targetBlockDepth, snapshotSlot);
        }

        public void markLoopBodyLocalsDead(SastNode body) {
            if (!(body.kind == SastKind.BLOCK)) {
                return;
            }

            int[] locals = context.blockLocalIds(body);

            for (int variableId : locals) {
                state.markDead(variableId);
            }
        }

        public void checkVarDecl(SastNode node) {
            diagnostics.push(node.span);

            try {
                int variableId = context.variableId(node.symbol);
                state.markAlive(variableId);

                int pointerIndex = state.pointerIndex(node.symbol);

                if (pointerIndex >= 0) {
                    state.setPointer(pointerIndex, originEmpty());
                }

                checkExpr(node.staticInitializer);
            } finally {
                diagnostics.pop();
            }
        }

        public void checkAssign(SastNode node) {
            diagnostics.push(node.span);

            try {
                checkExpr(node.target);
                checkExpr(node.value);

                if (!(node.target.type.kind == SastTypeKind.PTR)
                        || node.isCompound()) {
                    return;
                }

                applyCheckStore(node);
            } finally {
                diagnostics.pop();
            }
        }

        private void applyCheckStore(SastNode node) {
            DanglingOrigin value = valueOrigins(node.value);

            if (node.target.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(node.target.symbol);

                if (pointerIndex >= 0) {
                    state.setPointer(pointerIndex, value);
                }

                return;
            }

            if (node.target.kind == SastKind.DEREF) {
                DanglingOrigin cells = valueOrigins(node.target.expr);
                int cell = exactlyOnePointerCell(cells);

                if (cell >= 0) {
                    state.setPointer(cell, value);
                }
            }
        }

        public void checkExpr(SastNode expression) {
            if (expression == null) {
                return;
            }

            diagnostics.push(expression.span);

            try {
                checkExprInner(expression);
            } finally {
                diagnostics.pop();
            }
        }

        private void checkExprInner(SastNode expression) {
            if (expression.kind == SastKind.INT_LITERAL
                    || expression.kind == SastKind.NULL_LITERAL
                    || expression.kind == SastKind.VAR_EXPR
                    || expression.kind == SastKind.ENUM_VARIANT) {
                return;
            }

            if (expression.kind == SastKind.UNARY_OP) {
                checkExpr(expression.expr);
                return;
            }

            if (expression.kind == SastKind.BIN_OP) {
                checkExpr(expression.left);
                checkExpr(expression.right);
                return;
            }

            if (expression.kind == SastKind.ARRAY_ACCESS) {
                checkExpr(expression.target);
                checkExpr(expression.index);
                return;
            }

            if (expression.kind == SastKind.MEMBER_ACCESS) {
                checkExpr(expression.target);
                return;
            }

            if (expression.kind == SastKind.FUNCTION_CALL) {
                for (SastNode argument : expression.args) {
                    checkExpr(argument);
                }

                handleCheckCall(expression);

                return;
            }

            if (expression.kind == SastKind.ARRAY_INTRINSIC_CALL) {
                checkExpr(expression.receiver);

                for (SastNode argument : expression.args) {
                    checkExpr(argument);
                }

                return;
            }

            if (expression.kind == SastKind.AGGREGATE_LITERAL) {
                for (SastNode field : expression.fields) {
                    checkExpr(field.value);
                }

                return;
            }

            if (expression.kind == SastKind.GET_PTR) {
                checkExpr(expression.target);

                markAddressTaken(expression);

                return;
            }

            if (expression.kind == SastKind.DEREF) {
                checkExpr(expression.expr);

                if (!suppressed) {
                    checkDerefLiveness(expression);
                }

                return;
            }

            if (expression.kind == SastKind.CAST) {
                checkExpr(expression.expr);
            }
        }

        private void checkDerefLiveness(SastNode deref) {
            DanglingOrigin origins = valueOrigins(deref.expr);
            int site = context.siteId(deref);

            if (origins.hasDefiniteDead(state.mustDead)) {
                if (site >= 0 && addBit(reportedDerefSites, site)) {
                    diagnostics.error(
                            INTRA
                            + "Cannot dereference pointer to "
                            + formatDeadOrigins(origins, state.mustDead)
                            + " after its scope ended"
                            + ". The referenced storage's lifetime has ended,"
                            + " so the pointer is dangling. Copy the value out"
                            + " before the scope ends, or keep the pointee in"
                            + " longer-lived storage.",
                            "dangling.deref"
                    );
                }
            } else if (origins.hasPossibleDead(state.mayDead)) {
                if (site >= 0 && addBit(reportedDerefSites, site)) {
                    diagnostics.warning(
                            INTRA
                            + "Pointer may refer to "
                            + formatDeadOrigins(origins, state.mayDead)
                            + " after its scope ended",
                            "dangling.deref"
                    );
                }
            }
        }

        private void checkUseLiveness(
                SastNode expression,
                SastNode site
        ) {
            if (!(expression.type.kind == SastTypeKind.PTR)) {
                return;
            }

            DanglingOrigin origins = valueOrigins(expression);
            int siteId = context.siteId(site);

            if (origins.hasDefiniteDead(state.mustDead)) {
                if (siteId >= 0 && addBit(reportedUseSites, siteId)) {
                    diagnostics.error(
                            INTRA
                            + "Cannot use pointer to "
                            + formatDeadOrigins(origins, state.mustDead)
                            + " after its scope ended"
                            + ". The referenced storage's lifetime has ended,"
                            + " so the pointer is dangling. Stop using the"
                            + " pointer after the pointee's scope ends, or move"
                            + " the pointee to longer-lived storage.",
                            "dangling.use"
                    );
                }
            } else if (origins.hasPossibleDead(state.mayDead)) {
                if (siteId >= 0 && addBit(reportedUseSites, siteId)) {
                    diagnostics.warning(
                            INTRA
                            + "Pointer may refer to "
                            + formatDeadOrigins(origins, state.mayDead)
                            + " after its scope ended",
                            "dangling.use"
                    );
                }
            }
        }

        private void markAddressTaken(SastNode node) {
            PtrPermission permission =
                    PointerPermissions.pointerPermission(node.type);

            if (permission == PtrPermission.READ_ONLY) {
                return;
            }

            DanglingOrigin storage = storageOrigins(node.target);

            for (int word = 0; word < state.varWords; word++) {
                long bits = storage.frame[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int variableId = (word << 6) + bit;
                    int pointerIndex =
                            state.pointerIndexByVariableId(variableId);

                    if (pointerIndex >= 0) {
                        addressTaken[pointerIndex >>> 6]
                                |= 1L << (pointerIndex & 63);
                    }

                    bits &= bits - 1;
                }
            }
        }

        public void onReturn(SastNode node) {
        }

        public void onFallthrough() {
        }

        public boolean guardPathFeasible(
                SastNode condition,
                boolean guardHolds
        ) {
            if (condition.constantValue != null) {
                return guardHolds == (condition.constantValue.signum() != 0);
            }

            NullGuard guard = nullGuard(condition);

            if (guard == null) {
                return true;
            }

            int pointerIndex = pointerIndexOfExpression(guard.variable);

            if (pointerIndex < 0) {
                return true;
            }

            boolean assertedNull = guard.isEquality == guardHolds;

            DanglingOrigin current = state.pointerOrigin(pointerIndex);
            return assertedNull
                    ? !current.nonNull
                    : !current.definitelyNull();
        }

        public void refineFromGuard(
                SastNode condition,
                boolean guardHolds
        ) {
            NullGuard guard = nullGuard(condition);

            if (guard == null) {
                return;
            }

            int pointerIndex = pointerIndexOfExpression(guard.variable);

            if (pointerIndex < 0) {
                return;
            }

            boolean assertedNull = guard.isEquality == guardHolds;

            DanglingOrigin current = state.pointerOrigin(pointerIndex);

            if (assertedNull) {
                if (!current.nonNull) {
                    state.setPointer(pointerIndex, originEmpty());
                }
            } else if (!current.definitelyNull()) {
                state.setPointer(pointerIndex, current.withNonNull());
            }
        }

        public boolean guardCaseFeasible(
                MemoryGuardFacts facts,
                int guardCase
        ) {
            int start = facts.caseFactStart(guardCase);
            int count = facts.caseFactCount(guardCase);

            for (int i = 0; i < count; i++) {
                byte kind = facts.kindAt(start + i);

                if (kind != MemoryGuardFacts.FACT_NULL
                        && kind != MemoryGuardFacts.FACT_NONNULL) {
                    continue;
                }

                SastNode variable = facts.expressionAt(start + i);
                int pointerIndex = pointerIndexOfExpression(variable);

                if (pointerIndex < 0) {
                    continue;
                }

                boolean assertedNull = kind == MemoryGuardFacts.FACT_NULL;

                DanglingOrigin current = state.pointerOrigin(pointerIndex);
                boolean feasible = assertedNull
                        ? !current.nonNull
                        : !current.definitelyNull();

                if (!feasible) {
                    return false;
                }
            }

            return true;
        }

        public void applyGuardCase(
                MemoryGuardFacts facts,
                int guardCase
        ) {
            int start = facts.caseFactStart(guardCase);
            int count = facts.caseFactCount(guardCase);

            for (int i = 0; i < count; i++) {
                byte kind = facts.kindAt(start + i);

                if (kind != MemoryGuardFacts.FACT_NULL
                        && kind != MemoryGuardFacts.FACT_NONNULL) {
                    continue;
                }

                SastNode variable = facts.expressionAt(start + i);
                int pointerIndex = pointerIndexOfExpression(variable);

                if (pointerIndex < 0) {
                    continue;
                }

                boolean assertedNull = kind == MemoryGuardFacts.FACT_NULL;

                DanglingOrigin current = state.pointerOrigin(pointerIndex);

                if (assertedNull) {
                    if (!current.nonNull) {
                        state.setPointer(pointerIndex, originEmpty());
                    }
                } else if (!current.definitelyNull()) {
                    state.setPointer(pointerIndex, current.withNonNull());
                }
            }
        }

        public int snapshot() {
            return state.snapshot();
        }

        public void restore(int slot) {
            state.restore(slot);
        }

        public void mergeAfterIf(
                int before,
                int thenState,
                boolean thenLeaves,
                int elseState,
                boolean elseLeaves,
                boolean hasElse,
                int fallthroughState
        ) {
            state.mergeAfterIf(
                    before,
                    thenState,
                    thenLeaves,
                    elseState,
                    elseLeaves,
                    hasElse,
                    fallthroughState
            );
        }

        public void mergeLoopExitStates(int[] slots, int count) {
            state.mergeLoopExitStates(slots, count);
        }

        public void mergeStateIntoCurrent(int slot) {
            state.mergeStateIntoCurrent(slot);
        }

        long[][] diagnosticDedup() {
            return new long[][] {
                    java.util.Arrays.copyOf(
                            reportedDerefSites,
                            reportedDerefSites.length
                    ),
                    java.util.Arrays.copyOf(
                            reportedUseSites,
                            reportedUseSites.length
                    )
            };
        }

        void restoreDiagnosticDedup(long[][] saved) {
            System.arraycopy(
                    saved[0],
                    0,
                    reportedDerefSites,
                    0,
                    reportedDerefSites.length
            );
            System.arraycopy(
                    saved[1],
                    0,
                    reportedUseSites,
                    0,
                    reportedUseSites.length
            );
        }

        public boolean isSuppressed() {
            return suppressed;
        }

        public void setSuppressed(boolean value) {
            suppressed = value;
        }

        int snapshotCapacity() {
            return state.capacity;
        }

        int snapshotCount() {
            return state.snapshotCount;
        }

        long snapshotRowBytes() {
            return state.snapshotRowBytes();
        }

        private void handleCheckCall(SastNode call) {
            for (SastNode argument : call.args) {
                checkUseLiveness(argument, call);
            }

            invalidateForCall(call);
        }

        private void invalidateForCall(SastNode call) {
            for (int word = 0; word < addressTaken.length; word++) {
                long bits = addressTaken[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int pointerIndex = (word << 6) + bit;
                    state.setPointer(pointerIndex, originUnknown());
                    bits &= bits - 1;
                }
            }

            for (SastNode argument : call.args) {
                DanglingOrigin origins = valueOrigins(argument);
                invalidateCells(origins);
            }
        }

        private void invalidateCells(DanglingOrigin origins) {
            for (int word = 0; word < state.varWords; word++) {
                long bits = origins.frame[word] | origins.global[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int variableId = (word << 6) + bit;
                    int pointerIndex =
                            state.pointerIndexByVariableId(variableId);

                    if (pointerIndex >= 0) {
                        state.setPointer(pointerIndex, originUnknown());
                    }

                    bits &= bits - 1;
                }
            }
        }

        private DanglingOrigin storageOrigins(SastNode expression) {
            if (expression == null) {
                return originUnknown();
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int variableId = context.variableId(expression.symbol);

                if (expression.symbol.kind == SymbolKind.GLOBAL) {
                    return originGlobal(variableId);
                }

                return originFrame(variableId);
            }

            if (expression.kind == SastKind.DEREF) {
                return valueOrigins(expression.expr);
            }

            if (expression.kind == SastKind.MEMBER_ACCESS) {
                return storageOrigins(expression.target);
            }

            if (expression.kind == SastKind.ARRAY_ACCESS) {
                return storageOrigins(expression.target);
            }

            if (expression.kind == SastKind.CAST) {
                return storageOrigins(expression.expr);
            }

            return originUnknown();
        }

        private DanglingOrigin valueOrigins(SastNode expression) {
            if (expression == null) {
                return originUnknown();
            }

            if (expression.kind == SastKind.NULL_LITERAL) {
                return originEmpty();
            }

            if (expression.kind == SastKind.GET_PTR) {
                return storageOrigins(expression.target).withNonNull();
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(expression.symbol);

                return pointerIndex < 0
                        ? originUnknown()
                        : state.pointerOrigin(pointerIndex);
            }

            if (expression.kind == SastKind.DEREF) {
                return readPointerCells(valueOrigins(expression.expr));
            }

            if (expression.kind == SastKind.CAST) {
                return valueOrigins(expression.expr);
            }

            if (expression.kind == SastKind.FUNCTION_CALL) {
                return originUnknown();
            }

            return originUnknown();
        }

        private DanglingOrigin readPointerCells(DanglingOrigin cells) {
            DanglingOrigin result = null;

            if (cells.unknown) {
                result = originUnknown();
            }

            for (int word = 0; word < state.varWords; word++) {
                long bits = cells.frame[word] | cells.global[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int variableId = (word << 6) + bit;
                    int pointerIndex =
                            state.pointerIndexByVariableId(variableId);

                    if (pointerIndex >= 0) {
                        DanglingOrigin cell = state.pointerOrigin(pointerIndex);
                        result = result == null ? cell : result.union(cell);
                    }

                    bits &= bits - 1;
                }
            }

            return result == null ? originEmpty() : result;
        }

        private int exactlyOnePointerCell(DanglingOrigin cells) {
            int found = -1;

            for (int word = 0; word < state.varWords; word++) {
                long bits = cells.frame[word] | cells.global[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int variableId = (word << 6) + bit;
                    int pointerIndex =
                            state.pointerIndexByVariableId(variableId);

                    if (pointerIndex >= 0) {
                        if (found >= 0) {
                            return -1;
                        }

                        found = pointerIndex;
                    }

                    bits &= bits - 1;
                }
            }

            return found;
        }

        private boolean addBit(long[] bits, int index) {
            if (index < 0 || index >= bits.length * 64) {
                return false;
            }

            int word = index >>> 6;
            long mask = 1L << (index & 63);

            if ((bits[word] & mask) != 0L) {
                return false;
            }

            bits[word] |= mask;
            return true;
        }

    }

    private DanglingOrigin originEmpty() {
        int words = state == null ? 1 : state.varWords;
        return new DanglingOrigin(new long[words], new long[words], false, false);
    }

    private DanglingOrigin originUnknown() {
        int words = state == null ? 1 : state.varWords;
        return new DanglingOrigin(new long[words], new long[words], true, false);
    }

    private DanglingOrigin originFrame(int variableId) {
        int words = state.varWords;
        long[] frame = new long[words];

        if (variableId >= 0) {
            frame[variableId >>> 6] |= 1L << (variableId & 63);
        }

        return new DanglingOrigin(frame, new long[words], false, true);
    }

    private DanglingOrigin originGlobal(int variableId) {
        int words = state.varWords;
        long[] global = new long[words];

        if (variableId >= 0) {
            global[variableId >>> 6] |= 1L << (variableId & 63);
        }

        return new DanglingOrigin(new long[words], global, false, true);
    }

    private String formatDeadOrigins(
            DanglingOrigin origin,
            long[] deadBits
    ) {
        List<String> parameters = new ArrayList<>();
        List<String> locals = new ArrayList<>();

        for (int word = 0; word < state.varWords; word++) {
            long bits = origin.frame[word] & deadBits[word];

            while (bits != 0L) {
                int bit = Long.numberOfTrailingZeros(bits);
                int variableId = (word << 6) + bit;
                Symbol symbol = context.variable(variableId);

                if (symbol != null) {
                    if (symbol.kind == SymbolKind.PARAMETER) {
                        parameters.add(symbol.name);
                    } else {
                        locals.add(symbol.qualifiedName());
                    }
                }

                bits &= bits - 1;
            }
        }

        return formatOriginNames(parameters, locals);
    }

    private String formatOriginNames(
            List<String> parameters,
            List<String> locals
    ) {
        List<String> parts = new ArrayList<>();

        if (!parameters.isEmpty()) {
            parts.add(
                    (parameters.size() == 1 ? "parameter " : "parameters ")
                            + quoteList(parameters)
            );
        }

        if (!locals.isEmpty()) {
            parts.add(
                    (locals.size() == 1 ? "local variable " : "local variables ")
                            + quoteList(locals)
            );
        }

        return parts.isEmpty() ? "local storage" : String.join(" and ", parts);
    }

    private String quoteList(List<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(null);
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }

            builder.append('\'').append(sorted.get(i)).append('\'');
        }

        return builder.toString();
    }

    private int pointerIndexOfExpression(SastNode expression) {
        if (expression.kind == SastKind.VAR_EXPR) {
            return state.pointerIndex(expression.symbol);
        }

        return -1;
    }

    private static final class NullGuard {
        final SastNode variable;
        final boolean isEquality;

        NullGuard(SastNode variable, boolean isEquality) {
            this.variable = variable;
            this.isEquality = isEquality;
        }
    }

    private static NullGuard nullGuard(SastNode condition) {
        if (!(condition.kind == SastKind.BIN_OP)) {
            return null;
        }

        if (!condition.op.equals("==") && !condition.op.equals("!=")) {
            return null;
        }

        if (condition.left.kind == SastKind.NULL_LITERAL) {
            return new NullGuard(condition.right, condition.op.equals("=="));
        }

        if (condition.right.kind == SastKind.NULL_LITERAL) {
            return new NullGuard(condition.left, condition.op.equals("=="));
        }

        return null;
    }
}
