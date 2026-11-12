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

        runSummaryPass();

        if (driver.limitExceeded()) {
            return new PassResult(
                    document.withPhase(SastPhase.DANGLING),
                    diagnostics.diagnostics()
            );
        }

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
        driver = new MemoryDataflowDriver(context, diagnostics, new MemoryDataflowDriver.Domain(new DanglingDomain()));
        driver.run(context.root);
        summaryMode = false;
    }

    private void runCheckPass() {
        summaryMode = false;
        driver = new MemoryDataflowDriver(context, diagnostics, new MemoryDataflowDriver.Domain(new DanglingDomain()));
        driver.run(context.root);
    }

    final class DanglingDomain {
        private boolean suppressed;

        public void beginFunction(int functionId) {
            currentFunctionId = functionId;
            state.setFunction(functionId);
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

            SastNode decl = context.functionDecl(functionId);

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

        private void seedSummaryParameters(SastNode decl) {
            for (int i = 0; i < decl.parameterVariables.size(); i++) {
                Symbol parameter = decl.parameterVariables.get(i);

                if (!(parameter.type.kind == SastTypeKind.PTR)) {
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

            for (int index = 0;
                    index < state.activeAssignmentSiteCount();
                    index++) {
                int site = state.activeAssignmentSiteId(index);

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

        public void checkAssign(SastNode node) {
            diagnostics.push(node.span);

            try {
                checkExpr(node.target);
                checkExpr(node.value);

                if (!(node.target.type.kind == SastTypeKind.PTR)
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

        private void applySummaryStore(SastNode node) {
            long[] value = symbolicValueOf(node.value);
            long[] pointee = symbolicPointeeOf(node.value);
            long[] cell = symbolicCellOf(node.value);

            if (node.target.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(node.target.symbol);

                if (pointerIndex >= 0) {
                    state.setSymValue(pointerIndex, value);
                    state.setSymPointee(pointerIndex, pointee);
                    state.setSymCell(pointerIndex, cell);
                }
            } else if (node.target.kind == SastKind.DEREF) {
                updateSymbolicCells(node.target.expr, value, pointee, cell);
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
                SastNode pointerExpression,
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

        private void applyCheckStore(SastNode node) {
            DanglingOrigin value = valueOrigins(node.value);
            DanglingOrigin targetStorage = storageOrigins(node.target);

            if (value.hasFrame()
                    && (targetStorage.unknown || targetStorage.hasGlobal())) {
                int site = context.siteId(node);

                if (site >= 0 && addBit(reportedStoreSites, site)) {
                    diagnostics.error(
                            INTRA
                            + "Cannot store a view of frame or operator-borrowed"
                            + " storage into a global or caller-owned location",
                            "dangling.store"
                    );
                }
            }

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

                if (summaryMode) {
                    recordSummaryCall(expression);
                } else {
                    handleCheckCall(expression);
                }

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

                if (!summaryMode) {
                    markAddressTaken(expression);
                }

                return;
            }

            if (expression.kind == SastKind.DEREF) {
                checkExpr(expression.expr);

                if (!summaryMode && !suppressed) {
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
                            + " after its scope ended",
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
                            + " after its scope ended",
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

            if (!(node.value.type.kind == SastTypeKind.PTR)) {
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
                        + "' (dangling after return)",
                        "dangling.return"
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
                        + "' (possibly dangling after return)",
                        "dangling.return"
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
                        + "' (dangling after return)",
                        "dangling.return"
                );
            } else if (origins.hasFrame()) {
                diagnostics.warning(
                        INTRA
                        + "Returning a pointer that may refer to "
                        + formatFrameOrigins(origins)
                        + " from function '"
                        + functionName(node.function)
                        + "' (possibly dangling after return)",
                        "dangling.return"
                );
            }
        }

        public void onFallthrough() {
            if (summaryMode) {
                long[] set = templates().newSet();
                templates().set(set, MemoryTemplate.UNKNOWN);
                exitReturnSets.add(set);
            }
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

            if (summaryMode) {
                long[] set = state.symValueOf(pointerIndex);
                return assertedNull ? mayBeNull(set) : mayBeNonNull(set);
            }

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

                if (summaryMode) {
                    long[] set = state.symValueOf(pointerIndex);
                    boolean feasible = assertedNull
                            ? mayBeNull(set)
                            : mayBeNonNull(set);

                    if (!feasible) {
                        return false;
                    }
                } else {
                    DanglingOrigin current = state.pointerOrigin(pointerIndex);
                    boolean feasible = assertedNull
                            ? !current.nonNull
                            : !current.definitelyNull();

                    if (!feasible) {
                        return false;
                    }
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

                if (summaryMode) {
                    long[] refined = templates().newSet();

                    if (assertedNull) {
                        templates().set(refined, MemoryTemplate.NULL);
                    } else {
                        long[] current = state.symValueOf(pointerIndex);

                        for (int word = 0;
                                word < context.templates.words;
                                word++) {
                            refined[word] = current[word];
                        }

                        templates().set(refined, MemoryTemplate.NONNULL);
                    }

                    state.setSymValue(pointerIndex, refined);
                } else {
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
                            reportedReturnSites,
                            reportedReturnSites.length
                    ),
                    java.util.Arrays.copyOf(
                            reportedStoreSites,
                            reportedStoreSites.length
                    ),
                    java.util.Arrays.copyOf(
                            reportedDerefSites,
                            reportedDerefSites.length
                    ),
                    java.util.Arrays.copyOf(
                            reportedUseSites,
                            reportedUseSites.length
                    ),
                    java.util.Arrays.copyOf(
                            reportedCallSites,
                            reportedCallSites.length
                    )
            };
        }

        void restoreDiagnosticDedup(long[][] saved) {
            System.arraycopy(
                    saved[0],
                    0,
                    reportedReturnSites,
                    0,
                    reportedReturnSites.length
            );
            System.arraycopy(
                    saved[1],
                    0,
                    reportedStoreSites,
                    0,
                    reportedStoreSites.length
            );
            System.arraycopy(
                    saved[2],
                    0,
                    reportedDerefSites,
                    0,
                    reportedDerefSites.length
            );
            System.arraycopy(
                    saved[3],
                    0,
                    reportedUseSites,
                    0,
                    reportedUseSites.length
            );
            System.arraycopy(
                    saved[4],
                    0,
                    reportedCallSites,
                    0,
                    reportedCallSites.length
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

        private void recordSummaryCall(SastNode call) {
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
                SastNode argument = param < call.args.size()
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

        private void handleCheckCall(SastNode call) {
            for (SastNode argument : call.args) {
                checkUseLiveness(argument, call);
            }

            int callSiteId = context.callSiteId(call);
            int calleeId = context.callGraph.callSiteCallee[callSiteId];
            int paramCount = call.function.parameters.size();
            int[] rootBinding = new int[
                    paramCount * DanglingPointerState.BINDING_STRIDE];

            for (int i = 0; i < paramCount; i++) {
                SastNode argument = call.args.get(i);
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
                    new MemorySummaryBfs.NodeProcessor(this)
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
                SastNode call,
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
                            + " storage into " + sink,
                            "dangling.capture"
                    );
                }
            } else if (addBit(reportedCallSites, callSiteId)) {
                diagnostics.warning(
                        INTER
                        + "Call to '" + function + "' may store a view of frame"
                        + " storage into " + sink,
                        "dangling.capture"
                );
            }
        }

        private String captureSinkDescription(
                DanglingSummary summary,
                int capture,
                SastNode call,
                MemoryBfsResult result,
                int node
        ) {
            int kind = summary.captureSinkKinds[capture];

            if (kind == SINK_GLOBAL) {
                int global = summary.captureSinkGlobals[capture];

                if (global >= 0 && global < context.globalVariableIds.length) {
                    Symbol symbol = context.variable(
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

        void processNode(int node) {
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

        private void invalidateForCall(SastNode call) {
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
                int callSiteId = context.callSiteId(expression);

                if (callSiteId < 0) {
                    return originUnknown();
                }

                DanglingOrigin origin = callResultOrigins[callSiteId];
                return origin == null ? originUnknown() : origin;
            }

            return originUnknown();
        }

        private DanglingOrigin concretePointeeOrigins(
                SastNode expression
        ) {
            if (expression == null) {
                return originUnknown();
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(expression.symbol);

                return pointerIndex < 0
                        ? originUnknown()
                        : readPointerCells(state.pointerOrigin(pointerIndex));
            }

            if (expression.kind == SastKind.GET_PTR) {
                return valueOrigins(expression.target);
            }

            if (expression.kind == SastKind.DEREF) {
                return readPointerCells(valueOrigins(expression.expr));
            }

            if (expression.kind == SastKind.CAST) {
                return concretePointeeOrigins(expression.expr);
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

        private long[] symbolicValueOf(SastNode expression) {
            long[] set = templates().newSet();

            if (expression == null) {
                templates().set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression.kind == SastKind.NULL_LITERAL) {
                templates().set(set, MemoryTemplate.NULL);
                return set;
            }

            if (expression.kind == SastKind.GET_PTR) {
                long[] storage = symbolicStorageOrigins(expression.target);
                templates().union(set, storage);
                templates().set(set, MemoryTemplate.NONNULL);
                return set;
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(expression.symbol);

                if (pointerIndex >= 0) {
                    return state.symValueOf(pointerIndex);
                }

                templates().set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression.kind == SastKind.DEREF) {
                return symbolicPointeeOf(expression.expr);
            }

            if (expression.kind == SastKind.CAST) {
                return symbolicValueOf(expression.expr);
            }

            if (expression.kind == SastKind.FUNCTION_CALL) {
                int callSiteId = context.callSiteId(expression);
                templates().set(
                        set,
                        context.templates.callResult(callSiteId)
                );
                return set;
            }

            templates().set(set, MemoryTemplate.UNKNOWN);
            return set;
        }

        private long[] symbolicPointeeOf(SastNode expression) {
            long[] set = templates().newSet();

            if (expression == null) {
                templates().set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(expression.symbol);

                if (pointerIndex >= 0) {
                    return state.symPointeeOf(pointerIndex);
                }
            }

            if (expression.kind == SastKind.GET_PTR) {
                return symbolicValueOf(expression.target);
            }

            if (expression.kind == SastKind.CAST) {
                return symbolicPointeeOf(expression.expr);
            }

            templates().set(set, MemoryTemplate.UNKNOWN);
            return set;
        }

        private long[] symbolicStorageOrigins(SastNode expression) {
            long[] set = templates().newSet();

            if (expression == null) {
                templates().set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int variableId = context.variableId(expression.symbol);

                if (expression.symbol.kind == SymbolKind.GLOBAL) {
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

            if (expression.kind == SastKind.DEREF) {
                return symbolicValueOf(expression.expr);
            }

            if (expression.kind == SastKind.MEMBER_ACCESS) {
                return symbolicStorageOrigins(expression.target);
            }

            if (expression.kind == SastKind.ARRAY_ACCESS) {
                return symbolicStorageOrigins(expression.target);
            }

            if (expression.kind == SastKind.CAST) {
                return symbolicStorageOrigins(expression.expr);
            }

            templates().set(set, MemoryTemplate.UNKNOWN);
            return set;
        }

        private long[] symbolicCellOf(SastNode expression) {
            long[] set = templates().newSet();

            if (expression == null) {
                return set;
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(expression.symbol);

                if (pointerIndex >= 0) {
                    return state.symCellOf(pointerIndex);
                }

                return set;
            }

            if (expression.kind == SastKind.GET_PTR) {
                if (expression.target.kind == SastKind.VAR_EXPR) {
                    int variableId = context.variableId(expression.target.symbol);
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

                if (expression.target.kind == SastKind.DEREF) {
                    return symbolicCellOf(expression.target.expr);
                }

                if (expression.target.kind == SastKind.CAST) {
                    return symbolicCellOf(expression.target.expr);
                }

                return set;
            }

            if (expression.kind == SastKind.CAST) {
                return symbolicCellOf(expression.expr);
            }

            return set;
        }

        private CaptureSink captureSink(SastNode target) {
            if (target.kind == SastKind.VAR_EXPR
                    && target.symbol.kind == SymbolKind.GLOBAL) {
                int globalIndex = globalIndexOfVariable(
                        context.variableId(target.symbol)
                );

                if (globalIndex >= 0) {
                    return new CaptureSink(SINK_GLOBAL, -1, globalIndex, null);
                }
            }

            if (target.kind == SastKind.MEMBER_ACCESS) {
                CaptureSink base = captureSink(target.target);

                if (base != null) {
                    String path = target.field.name;

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

            if (target.kind == SastKind.DEREF) {
                int param = parameterIndexFromSet(
                        symbolicValueOf(target.expr)
                );

                if (param < 0) {
                    param = parameterIndexFromCellSet(
                            symbolicCellOf(target.expr)
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

    private String functionName(Symbol function) {
        return function == null ? "<unknown>" : function.qualifiedName();
    }

    private int pointerIndexOfExpression(SastNode expression) {
        if (expression.kind == SastKind.VAR_EXPR) {
            return state.pointerIndex(expression.symbol);
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
