import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class DanglingPointerChecker {
    private static final String INTRA = "Intraprocedural: ";
    private static final String INTER = "Interprocedural: ";

    private static final byte CAPTURE_MAY = 1;
    private static final byte CAPTURE_MUST = 2;

    private static final int SINK_GLOBAL = 0;
    private static final int SINK_POINTEE = 1;
    private static final int SINK_POINTEE_PATH = 2;
    private static final int SINK_UNKNOWN = 3;

    private static final class DanglingSummary {
        final long[] returnSet;
        final int captureCount;
        final int[] captureSinkKinds;
        final int[] captureSinkParams;
        final int[] captureSinkGlobals;
        final String[] captureFieldPaths;
        final byte[] captureCertainty;
        final long[] captureSources;
        final int edgeCount;
        final int[] edgeCallSiteIds;
        final long[] edgeValueSets;
        final long[] edgePointeeSets;
        final int maxParams;
        final int templateWords;

        DanglingSummary(
                long[] returnSet,
                int captureCount,
                int[] captureSinkKinds,
                int[] captureSinkParams,
                int[] captureSinkGlobals,
                String[] captureFieldPaths,
                byte[] captureCertainty,
                long[] captureSources,
                int edgeCount,
                int[] edgeCallSiteIds,
                long[] edgeValueSets,
                long[] edgePointeeSets,
                int maxParams,
                int templateWords
        ) {
            this.returnSet = returnSet;
            this.captureCount = captureCount;
            this.captureSinkKinds = captureSinkKinds;
            this.captureSinkParams = captureSinkParams;
            this.captureSinkGlobals = captureSinkGlobals;
            this.captureFieldPaths = captureFieldPaths;
            this.captureCertainty = captureCertainty;
            this.captureSources = captureSources;
            this.edgeCount = edgeCount;
            this.edgeCallSiteIds = edgeCallSiteIds;
            this.edgeValueSets = edgeValueSets;
            this.edgePointeeSets = edgePointeeSets;
            this.maxParams = maxParams;
            this.templateWords = templateWords;
        }
    }

    private static final class DanglingSummaryBuilder {
        final int edgeCount;
        final int maxParams;
        final int templateWords;
        final long[] edgeValueSets;
        final long[] edgePointeeSets;

        DanglingSummaryBuilder(int edgeCount, int maxParams, int templateWords) {
            this.edgeCount = edgeCount;
            this.maxParams = maxParams;
            this.templateWords = templateWords;
            this.edgeValueSets = new long[edgeCount * maxParams * templateWords];
            this.edgePointeeSets = new long[edgeCount * maxParams * templateWords];
        }

        int offset(int edgeIndex, int param) {
            return (edgeIndex * maxParams + param) * templateWords;
        }
    }

    private final DiagnosticBag diagnostics;

    private MemoryAnalysisContext context;
    private DanglingPointerState state;
    private MemoryDataflowDriver driver;
    private MemorySummaryBfs bfs;
    private DanglingSummary[] summaries;

    private boolean summaryMode;
    private int currentFunctionId = -1;
    private DanglingSummaryBuilder builder;
    private final List<long[]> exitReturnSets = new ArrayList<>();

    private DanglingOrigin[] callResultOrigins;
    private boolean[] callResultResolving;
    private MemoryBfsResult currentBfsResult;

    private long[] addressTaken;
    private long[] reportedReturnSites;
    private long[] reportedStoreSites;
    private long[] reportedDerefSites;
    private long[] reportedUseSites;
    private long[] reportedCallSites;

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

    public void check(SemanticASTNode node) {
        if (!(node instanceof SemanticProgramNode program)) {
            throw new IllegalArgumentException(
                    "Dangling-pointer checking requires a semantic program root"
            );
        }

        check(new SastDocument(
                SastPhase.SEMANTIC,
                EntryKind.NONE,
                new SourceTable(),
                new SymbolRegistry(),
                program
        ));
    }

    public PassResult check(SastDocument document) {
        diagnostics.clear();
        ensureContext(document.root);

        runSummaryPass();
        runCheckPass();

        return new PassResult(
                document.withPhase(SastPhase.DANGLING),
                diagnostics.diagnostics()
        );
    }

    private void ensureContext(SemanticProgramNode root) {
        if (context == null || context.root != root) {
            context = MemoryAnalysisContext.build(root);
            state = null;
            bfs = null;
            summaries = null;
            callResultOrigins = null;
            callResultResolving = null;
            addressTaken = null;
            reportedReturnSites = null;
            reportedStoreSites = null;
            reportedDerefSites = null;
            reportedUseSites = null;
            reportedCallSites = null;
        }

        if (state == null) {
            state = new DanglingPointerState(context);
            bfs = new MemorySummaryBfs(context);
            summaries = new DanglingSummary[context.callGraph.functionCount()];
            callResultOrigins = new DanglingOrigin[context.callSiteCount];
            callResultResolving = new boolean[context.callSiteCount];
            addressTaken = new long[Math.max(1, (context.pointerVariableCount + 63) >>> 6)];
            reportedReturnSites = new long[Math.max(1, (context.siteCount + 63) >>> 6)];
            reportedStoreSites = new long[Math.max(1, (context.siteCount + 63) >>> 6)];
            reportedDerefSites = new long[Math.max(1, (context.siteCount + 63) >>> 6)];
            reportedUseSites = new long[Math.max(1, (context.siteCount + 63) >>> 6)];
            reportedCallSites = new long[Math.max(1, (context.callSiteCount + 63) >>> 6)];
        }
    }

    private void runSummaryPass() {
        summaryMode = true;
        summaries = new DanglingSummary[context.callGraph.functionCount()];
        driver = new MemoryDataflowDriver(context, diagnostics, new DanglingDomain());
        driver.run(context.root);
        summaryMode = false;
    }

    private void runCheckPass() {
        summaryMode = false;
        driver = new MemoryDataflowDriver(context, diagnostics, new DanglingDomain());
        driver.run(context.root);
    }

    private final class DanglingDomain implements MemoryDataflowDriver.Domain {
        private boolean suppressed;

        @Override
        public void beginFunction(int functionId) {
            currentFunctionId = functionId;
            state.reset();
            exitReturnSets.clear();
            Arrays.fill(callResultOrigins, null);
            Arrays.fill(callResultResolving, false);
            Arrays.fill(addressTaken, 0L);
            Arrays.fill(reportedReturnSites, 0L);
            Arrays.fill(reportedStoreSites, 0L);
            Arrays.fill(reportedDerefSites, 0L);
            Arrays.fill(reportedUseSites, 0L);
            Arrays.fill(reportedCallSites, 0L);
            currentBfsResult = null;
            suppressed = false;

            SemanticFunctionDeclNode decl = context.functionDecl(functionId);

            if (decl == null) {
                builder = null;
                return;
            }

            if (summaryMode) {
                builder = new DanglingSummaryBuilder(
                        context.callGraph.edgeCount[functionId],
                        context.templates.paramCount,
                        context.templates.words
                );
                seedSummaryParameters(decl);
            } else {
                builder = null;
                seedCheckParameters(decl);
            }

            seedGlobals();
        }

        private void seedSummaryParameters(SemanticFunctionDeclNode decl) {
            for (int i = 0; i < decl.parameterVariables.size(); i++) {
                VariableSymbol parameter = decl.parameterVariables.get(i);

                if (!(parameter.type instanceof SemanticPtrType)) {
                    continue;
                }

                int pointerIndex = state.pointerIndex(parameter);

                if (pointerIndex < 0) {
                    continue;
                }

                long[] value = templates().newSet();
                long[] pointee = templates().newSet();
                long[] cell = templates().newSet();

                if (isOperatorOther(decl, i)) {
                    int variableId = context.variableId(parameter);
                    templates().set(value, MemoryTemplate.NONNULL);
                    templates().set(
                            value,
                            context.templates.localValue(variableId)
                    );
                    templates().set(pointee, MemoryTemplate.UNKNOWN);
                    templates().set(
                            cell,
                            context.templates.localValue(variableId)
                    );
                } else {
                    templates().set(
                            value,
                            context.templates.valueAny(i)
                    );
                    templates().set(
                            pointee,
                            context.templates.pointeeAny(i)
                    );
                    templates().set(
                            cell,
                            context.templates.paramCell(i)
                    );
                }

                state.setSymValue(pointerIndex, value);
                state.setSymPointee(pointerIndex, pointee);
                state.setSymCell(pointerIndex, cell);
            }
        }

        private boolean isOperatorOther(
                SemanticFunctionDeclNode decl,
                int parameterIndex
        ) {
            return decl.symbol instanceof MethodSymbol method
                    && OperatorMethods.isOperatorMethodName(method.name)
                    && parameterIndex == 1;
        }

        private void seedCheckParameters(SemanticFunctionDeclNode decl) {
            for (int i = 0; i < decl.parameterVariables.size(); i++) {
                VariableSymbol parameter = decl.parameterVariables.get(i);
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

                if (summaryMode) {
                    long[] value = templates().newSet();
                    long[] pointee = templates().newSet();
                    templates().set(value, MemoryTemplate.UNKNOWN);
                    templates().set(pointee, MemoryTemplate.UNKNOWN);
                    state.setSymValue(pointerIndex, value);
                    state.setSymPointee(pointerIndex, pointee);
                    state.setSymCell(pointerIndex, templates().newSet());
                } else {
                    state.setPointer(pointerIndex, originUnknown());
                }
            }
        }

        @Override
        public void endFunction(int exitCount, boolean fallthrough) {
            if (summaryMode) {
                summaries[currentFunctionId] = buildSummary(exitCount);
            }

            currentFunctionId = -1;
            builder = null;
            currentBfsResult = null;
        }

        private DanglingSummary buildSummary(int exitCount) {
            long[] returnSet = templates().newSet();

            for (long[] exitSet : exitReturnSets) {
                templates().union(returnSet, exitSet);
            }

            List<Integer> captureSites = new ArrayList<>();
            List<Byte> certainties = new ArrayList<>();

            for (int site = 0; site < state.siteCount; site++) {
                if (state.captureSinkKind[site] < 0) {
                    continue;
                }

                boolean must = exitCount > 0;
                boolean may = false;

                for (int exit = 0; exit < exitCount; exit++) {
                    int slot = driver.exitSlot(exit);
                    must &= state.captureMustAt(slot, site);
                    may |= state.captureMayAt(slot, site);
                }

                if (!must && !may) {
                    continue;
                }

                captureSites.add(site);
                certainties.add(must ? CAPTURE_MUST : CAPTURE_MAY);
            }

            int captureCount = captureSites.size();
            int[] kinds = new int[captureCount];
            int[] params = new int[captureCount];
            int[] globals = new int[captureCount];
            String[] paths = new String[captureCount];
            byte[] certainty = new byte[captureCount];
            long[] sources = new long[captureCount * context.templates.words];

            for (int i = 0; i < captureCount; i++) {
                int site = captureSites.get(i);
                kinds[i] = state.captureSinkKind[site];
                params[i] = state.captureSinkParam[site];
                globals[i] = state.captureSinkGlobal[site];
                paths[i] = state.captureFieldPath[site];
                certainty[i] = certainties.get(i);

                int sourceOffset = site * context.templates.words;
                System.arraycopy(
                        state.captureSource,
                        sourceOffset,
                        sources,
                        i * context.templates.words,
                        context.templates.words
                );
            }

            int edgeCount = builder == null ? 0 : builder.edgeCount;
            int[] edgeCallSiteIds = new int[edgeCount];

            if (edgeCount > 0) {
                System.arraycopy(
                        context.callGraph.edgeCallSiteIds,
                        context.callGraph.edgeStart[currentFunctionId],
                        edgeCallSiteIds,
                        0,
                        edgeCount
                );
            }

            return new DanglingSummary(
                    returnSet,
                    captureCount,
                    kinds,
                    params,
                    globals,
                    paths,
                    certainty,
                    sources,
                    edgeCount,
                    edgeCallSiteIds,
                    builder == null
                            ? new long[0]
                            : builder.edgeValueSets,
                    builder == null
                            ? new long[0]
                            : builder.edgePointeeSets,
                    context.templates.paramCount,
                    context.templates.words
            );
        }

        @Override
        public void enterBlock(SemanticBlockNode node, int depth) {
            state.enterBlock(node, depth);
        }

        @Override
        public void leaveBlock(
                SemanticBlockNode node,
                int depth,
                boolean terminated
        ) {
            state.leaveBlock(node, depth, terminated);
        }

        @Override
        public void markLocalsDeadForJump(int targetBlockDepth, int snapshotSlot) {
            state.markLocalsDeadForJump(targetBlockDepth, snapshotSlot);
        }

        @Override
        public void markLoopBodyLocalsDead(SemanticStmtNode body) {
            if (!(body instanceof SemanticBlockNode block)) {
                return;
            }

            int[] locals = context.blockLocalIds(block);

            for (int variableId : locals) {
                state.markDead(variableId);
            }
        }

        @Override
        public void checkVarDecl(SemanticVarDeclNode node) {
            diagnostics.push(node.span);

            try {
                int variableId = context.variableId(node.symbol);
                state.markAlive(variableId);

                int pointerIndex = state.pointerIndex(node.symbol);

                if (pointerIndex >= 0) {
                    if (summaryMode) {
                        long[] value = templates().newSet();
                        templates().set(value, MemoryTemplate.NULL);
                        state.setSymValue(pointerIndex, value);
                        state.setSymPointee(pointerIndex, templates().newSet());
                        state.setSymCell(pointerIndex, templates().newSet());
                    } else {
                        state.setPointer(pointerIndex, originEmpty());
                    }
                }

                checkExpr(node.staticInitializer);
            } finally {
                diagnostics.pop();
            }
        }

        @Override
        public void checkAssign(SemanticAssignNode node) {
            diagnostics.push(node.span);

            try {
                checkExpr(node.target);
                checkExpr(node.value);

                if (!(node.target.type instanceof SemanticPtrType)
                        || node.isCompound()) {
                    return;
                }

                if (summaryMode) {
                    applySummaryStore(node);
                } else {
                    applyCheckStore(node);
                }
            } finally {
                diagnostics.pop();
            }
        }

        private void applySummaryStore(SemanticAssignNode node) {
            long[] value = symbolicValueOf(node.value);
            long[] pointee = symbolicPointeeOf(node.value);
            long[] cell = symbolicCellOf(node.value);

            if (node.target instanceof SemanticVarExprNode variable) {
                int pointerIndex = state.pointerIndex(variable.symbol);

                if (pointerIndex >= 0) {
                    state.setSymValue(pointerIndex, value);
                    state.setSymPointee(pointerIndex, pointee);
                    state.setSymCell(pointerIndex, cell);
                }
            } else if (node.target instanceof SemanticDerefNode deref) {
                updateSymbolicCells(deref.expr, value, pointee, cell);
            }

            int site = context.siteId(node);
            CaptureSink sink = captureSink(node.target);

            if (sink != null && site >= 0) {
                state.captureMust[site >>> 6] |= 1L << (site & 63);
                state.captureMay[site >>> 6] |= 1L << (site & 63);
                state.captureSinkKind[site] = sink.kind;
                state.captureSinkParam[site] = sink.param;
                state.captureSinkGlobal[site] = sink.global;
                state.captureFieldPath[site] = sink.fieldPath;

                int sourceOffset = site * context.templates.words;

                for (int i = 0; i < context.templates.words; i++) {
                    state.captureSource[sourceOffset + i] |= value[i];
                }
            }
        }

        private void updateSymbolicCells(
                SemanticExprNode pointerExpression,
                long[] value,
                long[] pointee,
                long[] cell
        ) {
            long[] cells = symbolicCellOf(pointerExpression);

            for (int word = 0; word < context.templates.words; word++) {
                long bits = cells[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int localVariable = context.templates.variableOfLocal(template);

                    if (localVariable >= 0) {
                        int pointerIndex =
                                state.pointerIndexByVariableId(localVariable);

                        if (pointerIndex >= 0) {
                            state.setSymValue(pointerIndex, value);
                            state.setSymPointee(pointerIndex, pointee);
                            state.setSymCell(pointerIndex, cell);
                        }
                    }

                    bits &= bits - 1;
                }
            }
        }

        private void applyCheckStore(SemanticAssignNode node) {
            DanglingOrigin value = valueOrigins(node.value);
            DanglingOrigin targetStorage = storageOrigins(node.target);

            if (value.hasFrame()
                    && (targetStorage.unknown || targetStorage.hasGlobal())) {
                int site = context.siteId(node);

                if (site >= 0 && addBit(reportedStoreSites, site)) {
                    diagnostics.error(
                            INTRA
                            + "Cannot store a view of frame or operator-borrowed"
                            + " storage into a global or caller-owned location"
                    );
                }
            }

            if (node.target instanceof SemanticVarExprNode variable) {
                int pointerIndex = state.pointerIndex(variable.symbol);

                if (pointerIndex >= 0) {
                    state.setPointer(pointerIndex, value);
                }

                return;
            }

            if (node.target instanceof SemanticDerefNode deref) {
                DanglingOrigin cells = valueOrigins(deref.expr);
                int cell = exactlyOnePointerCell(cells);

                if (cell >= 0) {
                    state.setPointer(cell, value);
                }
            }
        }

        @Override
        public void checkExpr(SemanticExprNode expression) {
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

        private void checkExprInner(SemanticExprNode expression) {
            if (expression instanceof SemanticIntLiteralNode
                    || expression instanceof SemanticNullLiteralNode
                    || expression instanceof SemanticVarExprNode) {
                return;
            }

            if (expression instanceof SemanticUnaryOpNode unary) {
                checkExpr(unary.expr);
                return;
            }

            if (expression instanceof SemanticBinOpNode binary) {
                checkExpr(binary.left);
                checkExpr(binary.right);
                return;
            }

            if (expression instanceof SemanticArrayAccessNode access) {
                checkExpr(access.target);
                checkExpr(access.index);
                return;
            }

            if (expression instanceof SemanticMemberAccessNode member) {
                checkExpr(member.target);
                return;
            }

            if (expression instanceof SemanticFunctionCallNode call) {
                for (SemanticExprNode argument : call.args) {
                    checkExpr(argument);
                }

                if (summaryMode) {
                    recordSummaryCall(call);
                } else {
                    handleCheckCall(call);
                }

                return;
            }

            if (expression instanceof SemanticArrayIntrinsicCallNode intrinsic) {
                checkExpr(intrinsic.receiver);

                for (SemanticExprNode argument : intrinsic.args) {
                    checkExpr(argument);
                }

                return;
            }

            if (expression instanceof SemanticAggregateLiteralNode aggregate) {
                for (SemanticAggregateFieldInit field : aggregate.fields) {
                    checkExpr(field.value);
                }

                return;
            }

            if (expression instanceof SemanticGetPtrNode getPtr) {
                checkExpr(getPtr.target);

                if (!summaryMode) {
                    markAddressTaken(getPtr);
                }

                return;
            }

            if (expression instanceof SemanticDerefNode deref) {
                checkExpr(deref.expr);

                if (!summaryMode && !suppressed) {
                    checkDerefLiveness(deref);
                }

                return;
            }

            if (expression instanceof SemanticCastExprNode cast) {
                checkExpr(cast.expr);
            }
        }

        private void checkDerefLiveness(SemanticDerefNode deref) {
            DanglingOrigin origins = valueOrigins(deref.expr);
            int site = context.siteId(deref);

            if (origins.hasDefiniteDead(state.mustDead)) {
                if (site >= 0 && addBit(reportedDerefSites, site)) {
                    diagnostics.error(
                            INTRA
                            + "Cannot dereference pointer to "
                            + formatDeadOrigins(origins, state.mustDead)
                            + " after its scope ended"
                    );
                }
            } else if (origins.hasPossibleDead(state.mayDead)) {
                if (site >= 0 && addBit(reportedDerefSites, site)) {
                    diagnostics.warning(
                            INTRA
                            + "Pointer may refer to "
                            + formatDeadOrigins(origins, state.mayDead)
                            + " after its scope ended"
                    );
                }
            }
        }

        private void checkUseLiveness(
                SemanticExprNode expression,
                SemanticASTNode site
        ) {
            if (!(expression.type instanceof SemanticPtrType)) {
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
                    );
                }
            } else if (origins.hasPossibleDead(state.mayDead)) {
                if (siteId >= 0 && addBit(reportedUseSites, siteId)) {
                    diagnostics.warning(
                            INTRA
                            + "Pointer may refer to "
                            + formatDeadOrigins(origins, state.mayDead)
                            + " after its scope ended"
                    );
                }
            }
        }

        private void markAddressTaken(SemanticGetPtrNode node) {
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

        @Override
        public void onReturn(SemanticReturnNode node) {
            if (summaryMode) {
                exitReturnSets.add(
                        node.value == null
                                ? templates().newSet()
                                : symbolicValueOf(node.value)
                );
                return;
            }

            if (suppressed || node.value == null) {
                return;
            }

            if (!(node.value.type instanceof SemanticPtrType)) {
                return;
            }

            int site = context.siteId(node);

            if (site >= 0 && !addBit(reportedReturnSites, site)) {
                return;
            }

            DanglingOrigin origins = valueOrigins(node.value);

            if (origins.hasDefiniteDead(state.mustDead)) {
                diagnostics.error(
                        INTRA
                        + "Returning a pointer to "
                        + formatDeadOrigins(origins, state.mustDead)
                        + " from function '"
                        + functionName(node.function)
                        + "' (dangling after return)"
                );
                return;
            }

            if (origins.hasPossibleDead(state.mayDead)) {
                diagnostics.warning(
                        INTRA
                        + "Returning a pointer that may refer to "
                        + formatDeadOrigins(origins, state.mayDead)
                        + " from function '"
                        + functionName(node.function)
                        + "' (possibly dangling after return)"
                );
                return;
            }

            if (origins.definiteDangle()) {
                diagnostics.error(
                        INTRA
                        + "Returning a pointer to "
                        + formatFrameOrigins(origins)
                        + " from function '"
                        + functionName(node.function)
                        + "' (dangling after return)"
                );
            } else if (origins.hasFrame()) {
                diagnostics.warning(
                        INTRA
                        + "Returning a pointer that may refer to "
                        + formatFrameOrigins(origins)
                        + " from function '"
                        + functionName(node.function)
                        + "' (possibly dangling after return)"
                );
            }
        }

        @Override
        public void onFallthrough() {
            if (summaryMode) {
                long[] set = templates().newSet();
                templates().set(set, MemoryTemplate.UNKNOWN);
                exitReturnSets.add(set);
            }
        }

        @Override
        public boolean guardPathFeasible(
                SemanticExprNode condition,
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

            if (summaryMode) {
                long[] set = state.symValueOf(pointerIndex);
                return assertedNull ? mayBeNull(set) : mayBeNonNull(set);
            }

            DanglingOrigin current = state.pointerOrigin(pointerIndex);
            return assertedNull
                    ? !current.nonNull
                    : !current.definitelyNull();
        }

        @Override
        public void refineFromGuard(
                SemanticExprNode condition,
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

            if (summaryMode) {
                long[] refined = templates().newSet();

                if (assertedNull) {
                    templates().set(refined, MemoryTemplate.NULL);
                } else {
                    long[] current = state.symValueOf(pointerIndex);

                    for (int i = 0; i < context.templates.words; i++) {
                        refined[i] = current[i];
                    }

                    templates().set(refined, MemoryTemplate.NONNULL);
                }

                state.setSymValue(pointerIndex, refined);
                return;
            }

            DanglingOrigin current = state.pointerOrigin(pointerIndex);

            if (assertedNull) {
                if (!current.nonNull) {
                    state.setPointer(pointerIndex, originEmpty());
                }
            } else if (!current.definitelyNull()) {
                state.setPointer(pointerIndex, current.withNonNull());
            }
        }

        @Override
        public int snapshot() {
            return state.snapshot();
        }

        @Override
        public void restore(int slot) {
            state.restore(slot);
        }

        @Override
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

        @Override
        public void mergeLoopExitStates(int[] slots, int count) {
            state.mergeLoopExitStates(slots, count);
        }

        @Override
        public void mergeStateIntoCurrent(int slot) {
            state.mergeStateIntoCurrent(slot);
        }

        @Override
        public boolean isSuppressed() {
            return suppressed;
        }

        @Override
        public void setSuppressed(boolean value) {
            suppressed = value;
        }

        private void recordSummaryCall(SemanticFunctionCallNode call) {
            int callSiteId = context.callSiteId(call);
            int edgeIndex = context.callGraph.callSiteLocalIndex[callSiteId];

            if (builder == null
                    || edgeIndex < 0
                    || edgeIndex >= builder.edgeCount) {
                return;
            }

            for (int param = 0;
                    param < call.function.parameters.size();
                    param++) {
                SemanticExprNode argument = param < call.args.size()
                        ? call.args.get(param)
                        : null;

                if (argument == null) {
                    continue;
                }

                long[] value = symbolicValueOf(argument);
                long[] pointee = symbolicPointeeOf(argument);
                int offset = builder.offset(edgeIndex, param);

                for (int i = 0; i < context.templates.words; i++) {
                    builder.edgeValueSets[offset + i] |= value[i];
                    builder.edgePointeeSets[offset + i] |= pointee[i];
                }
            }
        }

        private void handleCheckCall(SemanticFunctionCallNode call) {
            for (SemanticExprNode argument : call.args) {
                checkUseLiveness(argument, call);
            }

            int callSiteId = context.callSiteId(call);
            int calleeId = context.callGraph.callSiteCallee[callSiteId];
            int paramCount = call.function.parameters.size();
            int[] rootBinding = new int[
                    paramCount * DanglingPointerState.BINDING_STRIDE];

            for (int i = 0; i < paramCount; i++) {
                SemanticExprNode argument = call.args.get(i);
                int offset = i * DanglingPointerState.BINDING_STRIDE;
                rootBinding[offset + DanglingPointerState.BINDING_VALUE] =
                        context.templateSets.intern(
                                originToTemplateSet(valueOrigins(argument))
                        );
                rootBinding[offset + DanglingPointerState.BINDING_POINTEE] =
                        context.templateSets.intern(
                                originToTemplateSet(
                                        concretePointeeOrigins(argument)
                                )
                        );
                rootBinding[offset + DanglingPointerState.BINDING_ROOT_ARG] = i;
                rootBinding[offset + DanglingPointerState.BINDING_CELL] = -1;
            }

            MemoryBfsResult result = bfs.resolve(
                    callSiteId,
                    calleeId,
                    rootBinding,
                    node -> processNode(node)
            );

            currentBfsResult = result;

            if (result.containsTop()) {
                callResultOrigins[callSiteId] = originUnknown();
                invalidateForCall(call);
                currentBfsResult = null;
                return;
            }

            for (int node = 0; node < result.nodeCount(); node++) {
                int functionId = result.functionId(node);
                DanglingSummary summary = summaries[functionId];

                if (summary == null) {
                    continue;
                }

                for (int capture = 0; capture < summary.captureCount; capture++) {
                    int sourceOffset = capture * summary.templateWords;
                    DanglingOrigin source = evalSet(
                            summary.captureSources,
                            sourceOffset,
                            summary.templateWords,
                            result,
                            node
                    );

                    if (!source.hasFrame()) {
                        continue;
                    }

                    emitCaptureDiagnostic(
                            call,
                            callSiteId,
                            summary,
                            capture,
                            source,
                            result,
                            node
                    );
                }
            }

            DanglingOrigin returnOrigin = resolveCallReturnOrigin(
                    result,
                    callSiteId
            );
            callResultOrigins[callSiteId] = returnOrigin;
            invalidateForCall(call);
            currentBfsResult = null;
        }

        private void emitCaptureDiagnostic(
                SemanticFunctionCallNode call,
                int callSiteId,
                DanglingSummary summary,
                int capture,
                DanglingOrigin source,
                MemoryBfsResult result,
                int node
        ) {
            boolean definiteFrame = !source.unknown && !source.hasGlobal();
            byte certainty = summary.captureCertainty[capture];
            String sink = captureSinkDescription(
                    summary,
                    capture,
                    call,
                    result,
                    node
            );
            String function = call.function.qualifiedName();

            if (definiteFrame && certainty == CAPTURE_MUST) {
                if (addBit(reportedCallSites, callSiteId)) {
                    diagnostics.error(
                            INTER
                            + "Call to '" + function + "' stores a view of frame"
                            + " storage into " + sink
                    );
                }
            } else if (addBit(reportedCallSites, callSiteId)) {
                diagnostics.warning(
                        INTER
                        + "Call to '" + function + "' may store a view of frame"
                        + " storage into " + sink
                );
            }
        }

        private String captureSinkDescription(
                DanglingSummary summary,
                int capture,
                SemanticFunctionCallNode call,
                MemoryBfsResult result,
                int node
        ) {
            int kind = summary.captureSinkKinds[capture];

            if (kind == SINK_GLOBAL) {
                int global = summary.captureSinkGlobals[capture];

                if (global >= 0 && global < context.globalVariableIds.length) {
                    VariableSymbol symbol = context.variable(
                            context.globalVariableIds[global]
                    );
                    String path = summary.captureFieldPaths[capture];
                    String name = symbol == null
                            ? "<global>"
                            : symbol.qualifiedName();

                    return path == null
                            ? "global '" + name + "'"
                            : "global field '" + name + "." + path + "'";
                }
            }

            if (kind == SINK_POINTEE || kind == SINK_POINTEE_PATH) {
                int param = summary.captureSinkParams[capture];
                int rootArg = result.bindingAt(
                        node,
                        param * DanglingPointerState.BINDING_STRIDE
                                + DanglingPointerState.BINDING_ROOT_ARG
                );
                String path = summary.captureFieldPaths[capture];
                String target = "caller-owned storage";

                if (rootArg >= 0 && rootArg < call.args.size()) {
                    target = "caller-owned storage passed as argument "
                            + (rootArg + 1);
                }

                return path == null
                        ? target
                        : target + " field '" + path + "'";
            }

            return "a global or caller-owned location";
        }

        private void processNode(int node) {
            int functionId = bfs.functionId(node);
            DanglingSummary summary = summaries[functionId];

            if (summary == null) {
                return;
            }

            for (int edge = 0; edge < summary.edgeCount; edge++) {
                int callSite = summary.edgeCallSiteIds[edge];
                int callee = context.callGraph.callSiteCallee[callSite];
                int calleeParams = context.callGraph.functions[callee]
                        .parameters.size();
                int[] binding = new int[
                        calleeParams * DanglingPointerState.BINDING_STRIDE];

                for (int param = 0; param < calleeParams; param++) {
                    int offset = param * DanglingPointerState.BINDING_STRIDE;
                    int valueOffset = (edge * summary.maxParams + param)
                            * summary.templateWords;

                    DanglingOrigin value = evalSet(
                            summary.edgeValueSets,
                            valueOffset,
                            summary.templateWords,
                            null,
                            node
                    );
                    DanglingOrigin pointee = evalSet(
                            summary.edgePointeeSets,
                            valueOffset,
                            summary.templateWords,
                            null,
                            node
                    );

                    binding[offset + DanglingPointerState.BINDING_VALUE] =
                            context.templateSets.intern(
                                    originToTemplateSet(value)
                            );
                    binding[offset + DanglingPointerState.BINDING_POINTEE] =
                            context.templateSets.intern(
                                    originToTemplateSet(pointee)
                            );
                    binding[offset + DanglingPointerState.BINDING_ROOT_ARG] =
                            computeRootArg(summary, edge, param, node);
                    binding[offset + DanglingPointerState.BINDING_CELL] = -1;
                }

                bfs.enqueue(functionId, callee, binding, callSite);
            }
        }

        private int computeRootArg(
                DanglingSummary summary,
                int edge,
                int param,
                int node
        ) {
            int valueOffset = (edge * summary.maxParams + param)
                    * summary.templateWords;
            int first = firstTemplate(
                    summary.edgeValueSets,
                    valueOffset,
                    summary.templateWords
            );
            int count = countTemplates(
                    summary.edgeValueSets,
                    valueOffset,
                    summary.templateWords
            );

            if (count != 1 || first < 0) {
                return -1;
            }

            int valueParam = context.templates.paramOfValue(first);

            if (valueParam < 0) {
                valueParam = context.templates.paramOfPointee(first);
            }

            if (valueParam < 0) {
                return -1;
            }

            return bfs.bindingAt(
                    node,
                    valueParam * DanglingPointerState.BINDING_STRIDE
                            + DanglingPointerState.BINDING_ROOT_ARG
            );
        }

        private DanglingOrigin resolveCallReturnOrigin(
                MemoryBfsResult result,
                int callSiteId
        ) {
            if (callSiteId < 0 || callSiteId >= callResultOrigins.length) {
                return originUnknown();
            }

            if (callResultOrigins[callSiteId] != null) {
                return callResultOrigins[callSiteId];
            }

            if (result.isCallSiteTop(callSiteId)) {
                callResultOrigins[callSiteId] = originUnknown();
                return originUnknown();
            }

            int node = result.callSiteNodeIndex(callSiteId);

            if (node < 0) {
                callResultOrigins[callSiteId] = originUnknown();
                return originUnknown();
            }

            if (callResultResolving[callSiteId]) {
                return originUnknown();
            }

            int functionId = result.functionId(node);
            DanglingSummary summary = summaries[functionId];

            if (summary == null) {
                callResultOrigins[callSiteId] = originUnknown();
                return originUnknown();
            }

            callResultResolving[callSiteId] = true;
            DanglingOrigin origin = evalSet(
                    summary.returnSet,
                    0,
                    summary.templateWords,
                    result,
                    node
            );
            callResultResolving[callSiteId] = false;
            callResultOrigins[callSiteId] = origin;
            return origin;
        }

        private DanglingOrigin evalSet(
                long[] sets,
                int offset,
                int words,
                MemoryBfsResult result,
                int node
        ) {
            DanglingOrigin origin = originEmpty();

            for (int word = 0; word < words; word++) {
                long bits = sets[offset + word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    origin = origin.union(
                            evalTemplate(template, result, node)
                    );
                    bits &= bits - 1;
                }
            }

            return origin;
        }

        private DanglingOrigin evalTemplate(
                int template,
                MemoryBfsResult result,
                int node
        ) {
            if (template == MemoryTemplate.NULL) {
                return originEmpty();
            }

            if (template == MemoryTemplate.NONNULL
                    || template == MemoryTemplate.CONST) {
                return originNonNull();
            }

            if (template == MemoryTemplate.UNKNOWN
                    || template == MemoryTemplate.LOCAL) {
                return originUnknown();
            }

            int localVariable = context.templates.variableOfLocal(template);

            if (localVariable >= 0) {
                return originFrame(localVariable);
            }

            int global = context.templates.globalIndexOf(template);

            if (global >= 0) {
                return originGlobal(context.globalVariableIds[global]);
            }

            int valueParam = context.templates.paramOfValue(template);

            if (valueParam >= 0) {
                int offset = valueParam * DanglingPointerState.BINDING_STRIDE;
                int setId = bfs.bindingAt(
                        node,
                        offset + DanglingPointerState.BINDING_VALUE
                );
                DanglingOrigin origin = evalSet(
                        context.templateSets.get(setId),
                        0,
                        context.templates.words,
                        result,
                        node
                );
                return applyConstraint(
                        origin,
                        context.templates.constraintOfValue(template)
                );
            }

            int pointeeParam = context.templates.paramOfPointee(template);

            if (pointeeParam >= 0) {
                int offset = pointeeParam * DanglingPointerState.BINDING_STRIDE;
                int setId = bfs.bindingAt(
                        node,
                        offset + DanglingPointerState.BINDING_POINTEE
                );
                DanglingOrigin origin = evalSet(
                        context.templateSets.get(setId),
                        0,
                        context.templates.words,
                        result,
                        node
                );
                return applyConstraint(
                        origin,
                        context.templates.constraintOfPointee(template)
                );
            }

            int callSite = context.templates.callSiteIndexOf(template);

            if (callSite >= 0) {
                MemoryBfsResult effective = result == null
                        ? currentBfsResult
                        : result;

                if (effective == null) {
                    return originUnknown();
                }

                return resolveCallReturnOrigin(effective, callSite);
            }

            return originUnknown();
        }

        private DanglingOrigin applyConstraint(
                DanglingOrigin origin,
                int constraint
        ) {
            if (constraint == MemoryTemplate.CONSTRAINT_NONNULL
                    && origin.definitelyNull()) {
                return originEmpty();
            }

            if (constraint == MemoryTemplate.CONSTRAINT_NULL
                    && origin.nonNull) {
                return originEmpty();
            }

            return origin;
        }

        private void invalidateForCall(SemanticFunctionCallNode call) {
            for (int globalIndex = 0;
                    globalIndex < context.globalVariableIds.length;
                    globalIndex++) {
                int pointerIndex = state.pointerIndexByVariableId(
                        context.globalVariableIds[globalIndex]
                );

                if (pointerIndex >= 0) {
                    state.setPointer(pointerIndex, originUnknown());
                }
            }

            for (int word = 0; word < addressTaken.length; word++) {
                long bits = addressTaken[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int pointerIndex = (word << 6) + bit;
                    state.setPointer(pointerIndex, originUnknown());
                    bits &= bits - 1;
                }
            }

            for (SemanticExprNode argument : call.args) {
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

        private DanglingOrigin storageOrigins(SemanticExprNode expression) {
            if (expression == null) {
                return originUnknown();
            }

            if (expression instanceof SemanticVarExprNode variable) {
                int variableId = context.variableId(variable.symbol);

                if (variable.symbol instanceof GlobalVariableSymbol) {
                    return originGlobal(variableId);
                }

                return originFrame(variableId);
            }

            if (expression instanceof SemanticDerefNode deref) {
                return valueOrigins(deref.expr);
            }

            if (expression instanceof SemanticMemberAccessNode member) {
                return storageOrigins(member.target);
            }

            if (expression instanceof SemanticArrayAccessNode access) {
                return storageOrigins(access.target);
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return storageOrigins(cast.expr);
            }

            return originUnknown();
        }

        private DanglingOrigin valueOrigins(SemanticExprNode expression) {
            if (expression == null) {
                return originUnknown();
            }

            if (expression instanceof SemanticNullLiteralNode) {
                return originEmpty();
            }

            if (expression instanceof SemanticGetPtrNode getPtr) {
                return storageOrigins(getPtr.target).withNonNull();
            }

            if (expression instanceof SemanticVarExprNode variable) {
                int pointerIndex = state.pointerIndex(variable.symbol);

                return pointerIndex < 0
                        ? originUnknown()
                        : state.pointerOrigin(pointerIndex);
            }

            if (expression instanceof SemanticDerefNode deref) {
                return readPointerCells(valueOrigins(deref.expr));
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return valueOrigins(cast.expr);
            }

            if (expression instanceof SemanticFunctionCallNode call) {
                int callSiteId = context.callSiteId(call);

                if (callSiteId < 0) {
                    return originUnknown();
                }

                DanglingOrigin origin = callResultOrigins[callSiteId];
                return origin == null ? originUnknown() : origin;
            }

            return originUnknown();
        }

        private DanglingOrigin concretePointeeOrigins(
                SemanticExprNode expression
        ) {
            if (expression == null) {
                return originUnknown();
            }

            if (expression instanceof SemanticVarExprNode variable) {
                int pointerIndex = state.pointerIndex(variable.symbol);

                return pointerIndex < 0
                        ? originUnknown()
                        : readPointerCells(state.pointerOrigin(pointerIndex));
            }

            if (expression instanceof SemanticGetPtrNode getPtr) {
                return valueOrigins(getPtr.target);
            }

            if (expression instanceof SemanticDerefNode deref) {
                return readPointerCells(valueOrigins(deref.expr));
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return concretePointeeOrigins(cast.expr);
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

        private long[] symbolicValueOf(SemanticExprNode expression) {
            long[] set = templates().newSet();

            if (expression == null) {
                templates().set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression instanceof SemanticNullLiteralNode) {
                templates().set(set, MemoryTemplate.NULL);
                return set;
            }

            if (expression instanceof SemanticGetPtrNode getPtr) {
                long[] storage = symbolicStorageOrigins(getPtr.target);
                templates().union(set, storage);
                templates().set(set, MemoryTemplate.NONNULL);
                return set;
            }

            if (expression instanceof SemanticVarExprNode variable) {
                int pointerIndex = state.pointerIndex(variable.symbol);

                if (pointerIndex >= 0) {
                    return state.symValueOf(pointerIndex);
                }

                templates().set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression instanceof SemanticDerefNode deref) {
                return symbolicPointeeOf(deref.expr);
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return symbolicValueOf(cast.expr);
            }

            if (expression instanceof SemanticFunctionCallNode call) {
                int callSiteId = context.callSiteId(call);
                templates().set(
                        set,
                        context.templates.callResult(callSiteId)
                );
                return set;
            }

            templates().set(set, MemoryTemplate.UNKNOWN);
            return set;
        }

        private long[] symbolicPointeeOf(SemanticExprNode expression) {
            long[] set = templates().newSet();

            if (expression == null) {
                templates().set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression instanceof SemanticVarExprNode variable) {
                int pointerIndex = state.pointerIndex(variable.symbol);

                if (pointerIndex >= 0) {
                    return state.symPointeeOf(pointerIndex);
                }
            }

            if (expression instanceof SemanticGetPtrNode getPtr) {
                return symbolicValueOf(getPtr.target);
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return symbolicPointeeOf(cast.expr);
            }

            templates().set(set, MemoryTemplate.UNKNOWN);
            return set;
        }

        private long[] symbolicStorageOrigins(SemanticExprNode expression) {
            long[] set = templates().newSet();

            if (expression == null) {
                templates().set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression instanceof SemanticVarExprNode variable) {
                int variableId = context.variableId(variable.symbol);

                if (variable.symbol instanceof GlobalVariableSymbol) {
                    int globalIndex = globalIndexOfVariable(variableId);

                    if (globalIndex >= 0) {
                        templates().set(
                                set,
                                context.templates.globalValue(globalIndex)
                        );
                    } else {
                        templates().set(set, MemoryTemplate.UNKNOWN);
                    }
                } else {
                    templates().set(
                            set,
                            context.templates.localValue(variableId)
                    );
                }

                return set;
            }

            if (expression instanceof SemanticDerefNode deref) {
                return symbolicValueOf(deref.expr);
            }

            if (expression instanceof SemanticMemberAccessNode member) {
                return symbolicStorageOrigins(member.target);
            }

            if (expression instanceof SemanticArrayAccessNode access) {
                return symbolicStorageOrigins(access.target);
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return symbolicStorageOrigins(cast.expr);
            }

            templates().set(set, MemoryTemplate.UNKNOWN);
            return set;
        }

        private long[] symbolicCellOf(SemanticExprNode expression) {
            long[] set = templates().newSet();

            if (expression == null) {
                return set;
            }

            if (expression instanceof SemanticVarExprNode variable) {
                int pointerIndex = state.pointerIndex(variable.symbol);

                if (pointerIndex >= 0) {
                    return state.symCellOf(pointerIndex);
                }

                return set;
            }

            if (expression instanceof SemanticGetPtrNode getPtr) {
                if (getPtr.target instanceof SemanticVarExprNode variable) {
                    int variableId = context.variableId(variable.symbol);
                    int globalIndex = globalIndexOfVariable(variableId);

                    if (globalIndex >= 0) {
                        templates().set(
                                set,
                                context.templates.globalCell(globalIndex)
                        );
                    } else {
                        templates().set(
                                set,
                                context.templates.localValue(variableId)
                        );
                    }

                    return set;
                }

                if (getPtr.target instanceof SemanticDerefNode deref) {
                    return symbolicCellOf(deref.expr);
                }

                if (getPtr.target instanceof SemanticCastExprNode cast) {
                    return symbolicCellOf(cast.expr);
                }

                return set;
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return symbolicCellOf(cast.expr);
            }

            return set;
        }

        private CaptureSink captureSink(SemanticExprNode target) {
            if (target instanceof SemanticVarExprNode variable
                    && variable.symbol instanceof GlobalVariableSymbol) {
                int globalIndex = globalIndexOfVariable(
                        context.variableId(variable.symbol)
                );

                if (globalIndex >= 0) {
                    return new CaptureSink(SINK_GLOBAL, -1, globalIndex, null);
                }
            }

            if (target instanceof SemanticMemberAccessNode member) {
                CaptureSink base = captureSink(member.target);

                if (base != null) {
                    String path = member.field.name;

                    if (base.fieldPath != null) {
                        path = base.fieldPath + "." + path;
                    }

                    return new CaptureSink(
                            base.kind == SINK_GLOBAL
                                    ? SINK_GLOBAL
                                    : SINK_POINTEE_PATH,
                            base.param,
                            base.global,
                            path
                    );
                }
            }

            if (target instanceof SemanticDerefNode deref) {
                int param = parameterIndexFromSet(
                        symbolicValueOf(deref.expr)
                );

                if (param < 0) {
                    param = parameterIndexFromCellSet(
                            symbolicCellOf(deref.expr)
                    );
                }

                if (param >= 0) {
                    return new CaptureSink(SINK_POINTEE, param, -1, null);
                }

                return new CaptureSink(SINK_UNKNOWN, -1, -1, null);
            }

            return null;
        }

        private int parameterIndexFromSet(long[] set) {
            for (int word = 0; word < context.templates.words; word++) {
                long bits = set[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int param = context.templates.paramOfValue(template);

                    if (param >= 0) {
                        return param;
                    }

                    bits &= bits - 1;
                }
            }

            return -1;
        }

        private int parameterIndexFromCellSet(long[] set) {
            for (int word = 0; word < context.templates.words; word++) {
                long bits = set[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int param = context.templates.paramOfCell(template);

                    if (param >= 0) {
                        return param;
                    }

                    bits &= bits - 1;
                }
            }

            return -1;
        }

        private long[] originToTemplateSet(DanglingOrigin origin) {
            long[] set = templates().newSet();

            for (int word = 0; word < state.varWords; word++) {
                long bits = origin.frame[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int variableId = (word << 6) + bit;
                    templates().set(
                            set,
                            context.templates.localValue(variableId)
                    );
                    bits &= bits - 1;
                }

                bits = origin.global[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int variableId = (word << 6) + bit;
                    int globalIndex = globalIndexOfVariable(variableId);

                    if (globalIndex >= 0) {
                        templates().set(
                                set,
                                context.templates.globalValue(globalIndex)
                        );
                    }

                    bits &= bits - 1;
                }
            }

            if (origin.unknown) {
                templates().set(set, MemoryTemplate.UNKNOWN);
            }

            if (origin.nonNull) {
                templates().set(set, MemoryTemplate.NONNULL);
            }

            if (templates().isEmpty(set)) {
                templates().set(set, MemoryTemplate.NULL);
            }

            return set;
        }

        private int globalIndexOfVariable(int variableId) {
            return context.globalIndexByVariableId(variableId);
        }

        private boolean mayBeNull(long[] set) {
            if (templates().isEmpty(set)) {
                return true;
            }

            for (int word = 0; word < context.templates.words; word++) {
                long bits = set[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;

                    if (template == MemoryTemplate.NULL
                            || template == MemoryTemplate.UNKNOWN) {
                        return true;
                    }

                    int valueParam = context.templates.paramOfValue(template);

                    if (valueParam >= 0
                            && context.templates.constraintOfValue(template)
                                    != MemoryTemplate.CONSTRAINT_NONNULL) {
                        return true;
                    }

                    int pointeeParam = context.templates.paramOfPointee(template);

                    if (pointeeParam >= 0
                            && context.templates.constraintOfPointee(template)
                                    != MemoryTemplate.CONSTRAINT_NONNULL) {
                        return true;
                    }

                    if (context.templates.callSiteIndexOf(template) >= 0) {
                        return true;
                    }

                    bits &= bits - 1;
                }
            }

            return false;
        }

        private boolean mayBeNonNull(long[] set) {
            if (templates().isEmpty(set)) {
                return true;
            }

            for (int word = 0; word < context.templates.words; word++) {
                long bits = set[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;

                    if (template == MemoryTemplate.NONNULL
                            || template == MemoryTemplate.UNKNOWN
                            || template == MemoryTemplate.LOCAL
                            || template == MemoryTemplate.CONST) {
                        return true;
                    }

                    int valueParam = context.templates.paramOfValue(template);

                    if (valueParam >= 0
                            && context.templates.constraintOfValue(template)
                                    != MemoryTemplate.CONSTRAINT_NULL) {
                        return true;
                    }

                    int pointeeParam = context.templates.paramOfPointee(template);

                    if (pointeeParam >= 0
                            && context.templates.constraintOfPointee(template)
                                    != MemoryTemplate.CONSTRAINT_NULL) {
                        return true;
                    }

                    if (context.templates.callSiteIndexOf(template) >= 0) {
                        return true;
                    }

                    bits &= bits - 1;
                }
            }

            return false;
        }

        private int firstTemplate(long[] sets, int offset, int words) {
            for (int word = 0; word < words; word++) {
                long bits = sets[offset + word];

                if (bits != 0L) {
                    return (word << 6) + Long.numberOfTrailingZeros(bits);
                }
            }

            return -1;
        }

        private int countTemplates(long[] sets, int offset, int words) {
            int count = 0;

            for (int word = 0; word < words; word++) {
                count += Long.bitCount(sets[offset + word]);
            }

            return count;
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

        private MemoryTemplateRegistry templates() {
            return context.templates;
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

    private DanglingOrigin originNonNull() {
        int words = state == null ? 1 : state.varWords;
        return new DanglingOrigin(new long[words], new long[words], false, true);
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

    private String formatFrameOrigins(DanglingOrigin origin) {
        List<String> parameters = new ArrayList<>();
        List<String> locals = new ArrayList<>();

        for (int word = 0; word < state.varWords; word++) {
            long bits = origin.frame[word];

            while (bits != 0L) {
                int bit = Long.numberOfTrailingZeros(bits);
                int variableId = (word << 6) + bit;
                VariableSymbol symbol = context.variable(variableId);

                if (symbol != null) {
                    if (symbol instanceof ParameterSymbol) {
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
                VariableSymbol symbol = context.variable(variableId);

                if (symbol != null) {
                    if (symbol instanceof ParameterSymbol) {
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
        sorted.sort(String::compareTo);
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }

            builder.append('\'').append(sorted.get(i)).append('\'');
        }

        return builder.toString();
    }

    private String functionName(FunctionSymbol function) {
        return function == null ? "<unknown>" : function.qualifiedName();
    }

    private int pointerIndexOfExpression(SemanticExprNode expression) {
        if (expression instanceof SemanticVarExprNode variable) {
            return state.pointerIndex(variable.symbol);
        }

        return -1;
    }

    private static final class CaptureSink {
        final int kind;
        final int param;
        final int global;
        final String fieldPath;

        CaptureSink(int kind, int param, int global, String fieldPath) {
            this.kind = kind;
            this.param = param;
            this.global = global;
            this.fieldPath = fieldPath;
        }
    }

    private static final class NullGuard {
        final SemanticExprNode variable;
        final boolean isEquality;

        NullGuard(SemanticExprNode variable, boolean isEquality) {
            this.variable = variable;
            this.isEquality = isEquality;
        }
    }

    private static NullGuard nullGuard(SemanticExprNode condition) {
        if (!(condition instanceof SemanticBinOpNode binary)) {
            return null;
        }

        if (!binary.op.equals("==") && !binary.op.equals("!=")) {
            return null;
        }

        if (binary.left instanceof SemanticNullLiteralNode) {
            return new NullGuard(binary.right, binary.op.equals("=="));
        }

        if (binary.right instanceof SemanticNullLiteralNode) {
            return new NullGuard(binary.left, binary.op.equals("=="));
        }

        return null;
    }
}
