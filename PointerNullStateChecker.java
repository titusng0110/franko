import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class PointerNullStateChecker {
    private static final String INTRA = "Intraprocedural: ";
    private static final String INTER = "Interprocedural: ";

    private static final byte REQUIRE_NONE = 0;
    private static final byte REQUIRE_MAY = 1;
    private static final byte REQUIRE_MUST = 2;

    private static final class NullSummary {
        final byte[] paramReq;
        final byte[] pointeeReq;
        final int[] slotTargetParams;
        final byte[] slotCertainty;
        final long[] slotSource;
        final int slotCount;
        final long[] returnSet;
        final int edgeCount;
        final int[] edgeCallSiteIds;
        final long[] edgeValueSets;
        final long[] edgePointeeSets;
        final int[] edgeCellTargets;
        final int maxParams;
        final int templateWords;

        NullSummary(
                byte[] paramReq,
                byte[] pointeeReq,
                int[] slotTargetParams,
                byte[] slotCertainty,
                long[] slotSource,
                int slotCount,
                long[] returnSet,
                int edgeCount,
                int[] edgeCallSiteIds,
                long[] edgeValueSets,
                long[] edgePointeeSets,
                int[] edgeCellTargets,
                int maxParams,
                int templateWords
        ) {
            this.paramReq = paramReq;
            this.pointeeReq = pointeeReq;
            this.slotTargetParams = slotTargetParams;
            this.slotCertainty = slotCertainty;
            this.slotSource = slotSource;
            this.slotCount = slotCount;
            this.returnSet = returnSet;
            this.edgeCount = edgeCount;
            this.edgeCallSiteIds = edgeCallSiteIds;
            this.edgeValueSets = edgeValueSets;
            this.edgePointeeSets = edgePointeeSets;
            this.edgeCellTargets = edgeCellTargets;
            this.maxParams = maxParams;
            this.templateWords = templateWords;
        }
    }

    private static final class NullSummaryBuilder {
        final int edgeCount;
        final int maxParams;
        final int templateWords;
        final long[] edgeValueSets;
        final long[] edgePointeeSets;
        final int[] edgeCellTargets;

        NullSummaryBuilder(int edgeCount, int maxParams, int templateWords) {
            this.edgeCount = edgeCount;
            this.maxParams = maxParams;
            this.templateWords = templateWords;
            this.edgeValueSets = new long[edgeCount * maxParams * templateWords];
            this.edgePointeeSets = new long[edgeCount * maxParams * templateWords];
            this.edgeCellTargets = new int[edgeCount * maxParams];
            Arrays.fill(this.edgeCellTargets, -1);
        }

        int valueOffset(int edgeIndex, int param) {
            return (edgeIndex * maxParams + param) * templateWords;
        }
    }

    private final DiagnosticBag diagnostics;

    private MemoryAnalysisContext context;
    private PointerNullAnalysisState state;
    private MemoryDataflowDriver driver;
    private MemorySummaryBfs bfs;
    private NullSummary[] summaries;

    private boolean summaryMode;
    private int currentFunctionId = -1;
    private NullSummaryBuilder builder;
    private final List<long[]> exitReturnSets = new ArrayList<>();

    private byte[] callResultStates;
    private boolean[] callResultResolving;
    private MemoryBfsResult currentBfsResult;

    private long[] warnedVariables;
    private long[] warnedSites;

    public PointerNullStateChecker(DiagnosticBag diagnostics) {
        this(diagnostics, null);
    }

    public PointerNullStateChecker(
            DiagnosticBag diagnostics,
            MemoryAnalysisContext context
    ) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
        this.context = context;
    }

    public void check(SastNode node) {
        if (!(node.kind == SastKind.PROGRAM)) {
            throw new IllegalArgumentException(
                    "Pointer null-state checking requires a semantic node root"
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
        runCheckPass();

        return new PassResult(
                document.withPhase(SastPhase.NULL_STATE),
                diagnostics.diagnostics()
        );
    }

    private void ensureContext(SastNode root) {
        if (context == null || context.root != root) {
            context = MemoryAnalysisContext.build(root);
            state = null;
            bfs = null;
            summaries = null;
            callResultStates = null;
            callResultResolving = null;
            warnedVariables = null;
            warnedSites = null;
        }

        if (state == null) {
            state = new PointerNullAnalysisState(context);
            bfs = new MemorySummaryBfs(context);
            summaries = new NullSummary[context.callGraph.functionCount()];
            callResultStates = new byte[context.callSiteCount];
            callResultResolving = new boolean[context.callSiteCount];
            warnedVariables = new long[Math.max(1, (context.variables.length + 63) >>> 6)];
            warnedSites = new long[Math.max(1, (context.siteCount + 63) >>> 6)];
        }
    }

    private void runSummaryPass() {
        summaryMode = true;
        summaries = new NullSummary[context.callGraph.functionCount()];
        driver = new MemoryDataflowDriver(context, diagnostics, new MemoryDataflowDriver.Domain(new NullDomain()));
        driver.run(context.root);
        summaryMode = false;
    }

    private void runCheckPass() {
        summaryMode = false;
        driver = new MemoryDataflowDriver(context, diagnostics, new MemoryDataflowDriver.Domain(new NullDomain()));
        driver.run(context.root);
    }

    final class NullDomain {
        private boolean suppressed;

        public void beginFunction(int functionId) {
            currentFunctionId = functionId;
            state.reset();
            exitReturnSets.clear();
            Arrays.fill(callResultStates, (byte) -1);
            Arrays.fill(callResultResolving, false);
            Arrays.fill(warnedVariables, 0L);
            Arrays.fill(warnedSites, 0L);
            currentBfsResult = null;
            suppressed = false;

            SastNode decl = context.functionDecl(functionId);

            if (decl == null) {
                builder = null;
                return;
            }

            if (summaryMode) {
                builder = new NullSummaryBuilder(
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

                int offset = state.symValueOffset(pointerIndex);
                state.templates.clearAll(state.symValue, offset);

                if (isSelfParameter(decl, parameter)) {
                    state.symValue[offset + (MemoryTemplate.NONNULL >>> 6)]
                            |= 1L << (MemoryTemplate.NONNULL & 63);
                    state.symPointee[state.symPointeeOffset(pointerIndex)
                            + (MemoryTemplate.UNKNOWN >>> 6)]
                            |= 1L << (MemoryTemplate.UNKNOWN & 63);
                    state.templates.clearAll(
                            state.symCell,
                            state.symCellOffset(pointerIndex)
                    );
                } else if (isMainArgs(decl, parameter)) {
                    state.symValue[offset + (MemoryTemplate.NONNULL >>> 6)]
                            |= 1L << (MemoryTemplate.NONNULL & 63);
                    state.symPointee[state.symPointeeOffset(pointerIndex)
                            + (MemoryTemplate.UNKNOWN >>> 6)]
                            |= 1L << (MemoryTemplate.UNKNOWN & 63);
                    state.templates.clearAll(
                            state.symCell,
                            state.symCellOffset(pointerIndex)
                    );
                } else {
                    state.templates.set(
                            state.symValue,
                            offset,
                            context.templates.valueAny(i)
                    );
                    state.templates.set(
                            state.symPointee,
                            state.symPointeeOffset(pointerIndex),
                            context.templates.pointeeAny(i)
                    );
                    state.templates.set(
                            state.symCell,
                            state.symCellOffset(pointerIndex),
                            context.templates.paramCell(i)
                    );
                }
            }
        }

        private void seedCheckParameters(SastNode decl) {
            for (int i = 0; i < decl.parameterVariables.size(); i++) {
                Symbol parameter = decl.parameterVariables.get(i);
                int pointerIndex = state.pointerIndex(parameter);

                if (pointerIndex < 0) {
                    continue;
                }

                state.setState(pointerIndex, PointerNullAnalysisState.MAYBE_NULL);
                state.clearCellTargets(pointerIndex);

                if (isSelfParameter(decl, parameter)
                        || isMainArgs(decl, parameter)) {
                    state.setState(pointerIndex, PointerNullAnalysisState.POINTING);
                }
            }
        }

        private boolean isSelfParameter(
                SastNode decl,
                Symbol parameter
        ) {
            return decl.symbol.kind == SymbolKind.METHOD
                    && !decl.parameterVariables.isEmpty()
                    && decl.parameterVariables.get(0) == parameter;
        }

        private boolean isMainArgs(
                SastNode decl,
                Symbol parameter
        ) {
            return !(decl.symbol.kind == SymbolKind.METHOD)
                    && "main".equals(decl.symbol.name)
                    && decl.symbol.namespace == null
                    && decl.parameterVariables.size() == 1
                    && decl.parameterVariables.get(0) == parameter;
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
                    int offset = state.symValueOffset(pointerIndex);
                    state.templates.clearAll(state.symValue, offset);
                    state.templates.set(
                            state.symValue,
                            offset,
                            MemoryTemplate.UNKNOWN
                    );
                    state.templates.clearAll(
                            state.symPointee,
                            state.symPointeeOffset(pointerIndex)
                    );
                    state.templates.set(
                            state.symPointee,
                            state.symPointeeOffset(pointerIndex),
                            MemoryTemplate.UNKNOWN
                    );
                    state.templates.clearAll(
                            state.symCell,
                            state.symCellOffset(pointerIndex)
                    );
                } else {
                    state.setState(
                            pointerIndex,
                            PointerNullAnalysisState.MAYBE_NULL
                    );
                    state.clearCellTargets(pointerIndex);
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

        private NullSummary buildSummary(int exitCount) {
            byte[] paramReq = new byte[context.templates.paramCount];
            byte[] pointeeReq = new byte[context.templates.paramCount];

            for (int param = 0; param < paramReq.length; param++) {
                boolean must = exitCount > 0;
                boolean may = false;

                for (int exit = 0; exit < exitCount; exit++) {
                    int slot = driver.exitSlot(exit);
                    must &= state.derefMustAt(slot, param);
                    may |= state.derefMayAt(slot, param);
                }

                if (must) {
                    paramReq[param] = REQUIRE_MUST;
                } else if (may) {
                    paramReq[param] = REQUIRE_MAY;
                }

                must = exitCount > 0;
                may = false;

                for (int exit = 0; exit < exitCount; exit++) {
                    int slot = driver.exitSlot(exit);
                    must &= state.pointeeDerefMustAt(slot, param);
                    may |= state.pointeeDerefMayAt(slot, param);
                }

                if (must) {
                    pointeeReq[param] = REQUIRE_MUST;
                } else if (may) {
                    pointeeReq[param] = REQUIRE_MAY;
                }
            }

            List<Integer> slotParams = new ArrayList<>();
            List<Byte> slotCertainties = new ArrayList<>();
            List<Integer> slotSites = new ArrayList<>();

            for (int site = 0; site < state.siteCount; site++) {
                if (state.slotTargetParam[site] < 0) {
                    continue;
                }

                boolean must = exitCount > 0;
                boolean may = false;

                for (int exit = 0; exit < exitCount; exit++) {
                    int slot = driver.exitSlot(exit);
                    must &= state.slotMustAt(slot, site);
                    may |= state.slotMayAt(slot, site);
                }

                if (!must && !may) {
                    continue;
                }

                slotParams.add(state.slotTargetParam[site]);
                slotCertainties.add(must ? REQUIRE_MUST : REQUIRE_MAY);
                slotSites.add(site);
            }

            int[] slotTargets = new int[slotParams.size()];
            int[] slotSiteIds = new int[slotParams.size()];
            byte[] slotCertainty = new byte[slotParams.size()];
            long[] slotSource = new long[
                    slotParams.size() * context.templates.words];

            for (int i = 0; i < slotParams.size(); i++) {
                slotTargets[i] = slotParams.get(i);
                slotCertainty[i] = slotCertainties.get(i);
                slotSiteIds[i] = slotSites.get(i);
            }

            for (int i = 0; i < slotSiteIds.length; i++) {
                int offset = state.slotSourceOffset(slotSiteIds[i]);
                System.arraycopy(
                        state.slotSource,
                        offset,
                        slotSource,
                        i * context.templates.words,
                        context.templates.words
                );
            }

            long[] returnSet = context.templates.newSet();

            for (long[] exitSet : exitReturnSets) {
                context.templates.union(returnSet, exitSet);
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

            return new NullSummary(
                    paramReq,
                    pointeeReq,
                    slotTargets,
                    slotCertainty,
                    slotSource,
                    slotTargets.length,
                    returnSet,
                    edgeCount,
                    edgeCallSiteIds,
                    builder == null
                            ? new long[0]
                            : builder.edgeValueSets,
                    builder == null
                            ? new long[0]
                            : builder.edgePointeeSets,
                    builder == null
                            ? new int[0]
                            : builder.edgeCellTargets,
                    context.templates.paramCount,
                    context.templates.words
            );
        }

        public void enterBlock(SastNode node, int depth) {
        }

        public void leaveBlock(
                SastNode node,
                int depth,
                boolean terminated
        ) {
        }

        public void checkVarDecl(SastNode node) {
            diagnostics.push(node.span);

            try {
                int pointerIndex = state.pointerIndex(node.symbol);

                if (pointerIndex >= 0) {
                    if (summaryMode) {
                        int valueOffset = state.symValueOffset(pointerIndex);
                        state.templates.clearAll(state.symValue, valueOffset);
                        state.templates.set(
                                state.symValue,
                                valueOffset,
                                MemoryTemplate.NULL
                        );
                        state.templates.clearAll(
                                state.symPointee,
                                state.symPointeeOffset(pointerIndex)
                        );
                        state.templates.clearAll(
                                state.symCell,
                                state.symCellOffset(pointerIndex)
                        );
                    } else {
                        state.setState(
                                pointerIndex,
                                PointerNullAnalysisState.NULL
                        );
                        state.clearCellTargets(pointerIndex);
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

                if (node.isCompound()) {
                    return;
                }

                int targetPointerIndex = state.pointerIndex(pointerIndexSymbol(node.target));

                if (targetPointerIndex >= 0) {
                    if (summaryMode) {
                        applySummaryAssignment(targetPointerIndex, node.value);
                    } else {
                        applyCheckAssignment(targetPointerIndex, node.value);
                    }

                    return;
                }

                if (node.target.kind == SastKind.DEREF) {
                    if (summaryMode) {
                        applySummaryCellWrite(node, node.target);
                    } else {
                        applyCheckCellWrite(node.target, node.value);
                    }
                }
            } finally {
                diagnostics.pop();
            }
        }

        private Symbol pointerIndexSymbol(SastNode expression) {
            return expression.kind == SastKind.VAR_EXPR
                    ? expression.symbol
                    : null;
        }

        private void applySummaryAssignment(
                int pointerIndex,
                SastNode value
        ) {
            long[] valueSet = symbolicValueOf(value);
            long[] pointeeSet = symbolicPointeeOf(value);
            long[] cellSet = symbolicCellOf(value);

            System.arraycopy(
                    valueSet,
                    0,
                    state.symValue,
                    state.symValueOffset(pointerIndex),
                    context.templates.words
            );
            System.arraycopy(
                    pointeeSet,
                    0,
                    state.symPointee,
                    state.symPointeeOffset(pointerIndex),
                    context.templates.words
            );
            System.arraycopy(
                    cellSet,
                    0,
                    state.symCell,
                    state.symCellOffset(pointerIndex),
                    context.templates.words
            );
        }

        private void applyCheckAssignment(
                int pointerIndex,
                SastNode value
        ) {
            state.setState(pointerIndex, concreteValueOf(value));
            state.clearCellTargets(pointerIndex);
            copyConcreteCellTargets(value, pointerIndex);
        }

        private void applySummaryCellWrite(
                SastNode assignment,
                SastNode deref
        ) {
            long[] valueSet = symbolicValueOf(assignment.value);
            long[] cellSet = symbolicCellOf(deref.expr);
            int site = context.siteId(assignment);

            for (int word = 0; word < context.templates.words; word++) {
                long bits = cellSet[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int localVariable = context.templates.variableOfLocal(template);
                    int param = context.templates.paramOfCell(template);

                    if (localVariable >= 0) {
                        int pointerIndex =
                                state.pointerIndexByVariableId(localVariable);

                        if (pointerIndex >= 0) {
                            System.arraycopy(
                                    valueSet,
                                    0,
                                    state.symValue,
                                    state.symValueOffset(pointerIndex),
                                    context.templates.words
                            );
                            state.templates.clearAll(
                                    state.symPointee,
                                    state.symPointeeOffset(pointerIndex)
                            );
                            state.templates.set(
                                    state.symPointee,
                                    state.symPointeeOffset(pointerIndex),
                                    MemoryTemplate.UNKNOWN
                            );
                            state.templates.clearAll(
                                    state.symCell,
                                    state.symCellOffset(pointerIndex)
                            );
                        }
                    } else if (param >= 0 && site >= 0) {
                        state.slotMust[site >>> 6] |= 1L << (site & 63);
                        state.slotMay[site >>> 6] |= 1L << (site & 63);

                        if (state.slotTargetParam[site] < 0) {
                            state.slotTargetParam[site] = param;
                        } else if (state.slotTargetParam[site] != param) {
                            state.slotTargetParam[site] = -2;
                        }

                        int sourceOffset = state.slotSourceOffset(site);
                        for (int i = 0; i < context.templates.words; i++) {
                            state.slotSource[sourceOffset + i] |= valueSet[i];
                        }

                        SastNode decl =
                                context.functionDecl(currentFunctionId);

                        if (decl != null && param < decl.parameterVariables.size()) {
                            int pointerIndex = state.pointerIndex(
                                    decl.parameterVariables.get(param)
                            );

                            if (pointerIndex >= 0) {
                                System.arraycopy(
                                        valueSet,
                                        0,
                                        state.symPointee,
                                        state.symPointeeOffset(pointerIndex),
                                        context.templates.words
                                );
                            }
                        }
                    }

                    bits &= bits - 1;
                }
            }
        }

        private void applyCheckCellWrite(
                SastNode deref,
                SastNode value
        ) {
            int cellVariable = concreteCellTargetVariable(deref.expr);
            int pointerIndex = state.pointerIndexByVariableId(cellVariable);

            if (pointerIndex < 0) {
                return;
            }

            state.setState(pointerIndex, concreteValueOf(value));
            state.clearCellTargets(pointerIndex);
            copyConcreteCellTargets(value, pointerIndex);
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
                return;
            }

            if (expression.kind == SastKind.DEREF) {
                checkExpr(expression.expr);

                if (summaryMode) {
                    markSummaryDeref(expression.expr);
                } else if (!suppressed) {
                    checkConcreteDeref(expression);
                }

                return;
            }

            if (expression.kind == SastKind.CAST) {
                checkExpr(expression.expr);
            }
        }

        private void markSummaryDeref(SastNode pointerExpression) {
            long[] valueSet = symbolicValueOf(pointerExpression);

            for (int word = 0; word < context.templates.words; word++) {
                long bits = valueSet[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int valueParam = context.templates.paramOfValue(template);
                    int pointeeParam = context.templates.paramOfPointee(template);

                    if (valueParam >= 0) {
                        if (context.templates.constraintOfValue(template)
                                != MemoryTemplate.CONSTRAINT_NONNULL) {
                            state.markDeref(valueParam, true);
                        }
                    } else if (pointeeParam >= 0) {
                        if (context.templates.constraintOfPointee(template)
                                != MemoryTemplate.CONSTRAINT_NONNULL) {
                            state.markPointeeDeref(pointeeParam);
                        }
                    }

                    bits &= bits - 1;
                }
            }
        }

        private void checkConcreteDeref(SastNode deref) {
            if (!(deref.expr.kind == SastKind.VAR_EXPR)
                    && !(deref.expr.kind == SastKind.FUNCTION_CALL)
                    && !(deref.expr.kind == SastKind.CAST)
                    && !(deref.expr.kind == SastKind.DEREF)) {
                return;
            }

            byte valueState = concreteValueOf(deref.expr);

            if (valueState == PointerNullAnalysisState.NULL) {
                if (deref.expr.kind == SastKind.VAR_EXPR) {
                    diagnostics.error(
                            INTRA
                                    + "Cannot dereference null variable '"
                                    + deref.expr.symbol.qualifiedName()
                                    + "'"
                    );
                } else {
                    diagnostics.error(
                            INTRA + "Cannot dereference a null pointer"
                    );
                }

                return;
            }

            if (valueState != PointerNullAnalysisState.MAYBE_NULL) {
                return;
            }

            if (deref.expr.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(deref.expr.symbol);

                if (pointerIndex >= 0
                        && addBit(warnedVariables, pointerIndex)) {
                    diagnostics.warning(
                            INTRA
                                    + "Deref of possibly null variable '"
                                    + deref.expr.symbol.qualifiedName()
                                    + "'"
                    );
                }
            } else {
                int site = context.siteId(deref);

                if (site >= 0 && addBit(warnedSites, site)) {
                    diagnostics.warning(
                            INTRA + "Deref of a possibly null pointer"
                    );
                }
            }
        }

        public void onReturn(SastNode node) {
            if (summaryMode) {
                long[] set = node.value == null
                        ? context.templates.newSet()
                        : symbolicValueOf(node.value);
                exitReturnSets.add(set);
            }
        }

        public void onFallthrough() {
            if (summaryMode) {
                long[] set = context.templates.newSet();
                context.templates.set(set, MemoryTemplate.UNKNOWN);
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

            if (summaryMode) {
                int pointerIndex = pointerIndexOfExpression(guard.variable);

                if (pointerIndex < 0) {
                    return true;
                }

                long[] set = Arrays.copyOfRange(
                        state.symValue,
                        state.symValueOffset(pointerIndex),
                        state.symValueOffset(pointerIndex)
                                + context.templates.words
                );
                boolean assertedNull = guard.isEquality == guardHolds;

                return assertedNull
                        ? mayBeNull(set)
                        : mayBeNonNull(set);
            }

            int pointerIndex = pointerIndexOfExpression(guard.variable);

            if (pointerIndex < 0) {
                return true;
            }

            boolean assertedNull = guard.isEquality == guardHolds;
            byte current = state.stateOf(pointerIndex);

            if (assertedNull) {
                return current != PointerNullAnalysisState.POINTING;
            }

            return current != PointerNullAnalysisState.NULL;
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
                refineSummaryGuard(pointerIndex, assertedNull);
                return;
            }

            byte current = state.stateOf(pointerIndex);

            if (assertedNull) {
                if (current != PointerNullAnalysisState.POINTING) {
                    state.setState(
                            pointerIndex,
                            PointerNullAnalysisState.NULL
                    );
                }
            } else if (current != PointerNullAnalysisState.NULL) {
                state.setState(
                        pointerIndex,
                        PointerNullAnalysisState.POINTING
                );
            }
        }

        private void refineSummaryGuard(int pointerIndex, boolean assertedNull) {
            int offset = state.symValueOffset(pointerIndex);
            long[] refined = context.templates.newSet();

            for (int word = 0; word < context.templates.words; word++) {
                long bits = state.symValue[offset + word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int valueParam = context.templates.paramOfValue(template);
                    int pointeeParam = context.templates.paramOfPointee(template);

                    if (assertedNull) {
                        if (template == MemoryTemplate.NULL
                                || context.templates.callSiteIndexOf(template) >= 0) {
                            context.templates.set(refined, template);
                        } else if (template == MemoryTemplate.UNKNOWN) {
                            context.templates.set(refined, MemoryTemplate.NULL);
                        } else if (valueParam >= 0) {
                            int constraint = context.templates.constraintOfValue(template);

                            if (constraint != MemoryTemplate.CONSTRAINT_NONNULL) {
                                context.templates.set(
                                        refined,
                                        context.templates.valueNull(valueParam)
                                );
                            }
                        } else if (pointeeParam >= 0) {
                            int constraint =
                                    context.templates.constraintOfPointee(template);

                            if (constraint != MemoryTemplate.CONSTRAINT_NONNULL) {
                                context.templates.set(
                                        refined,
                                        context.templates.pointeeNull(pointeeParam)
                                );
                            }
                        }
                    } else {
                        if (template == MemoryTemplate.NONNULL
                                || template == MemoryTemplate.UNKNOWN) {
                            context.templates.set(
                                    refined,
                                    template == MemoryTemplate.UNKNOWN
                                            ? MemoryTemplate.NONNULL
                                            : template
                            );
                        } else if (valueParam >= 0) {
                            int constraint = context.templates.constraintOfValue(template);

                            if (constraint != MemoryTemplate.CONSTRAINT_NULL) {
                                context.templates.set(
                                        refined,
                                        context.templates.valueNonNull(valueParam)
                                );
                            }
                        } else if (pointeeParam >= 0) {
                            int constraint =
                                    context.templates.constraintOfPointee(template);

                            if (constraint != MemoryTemplate.CONSTRAINT_NULL) {
                                context.templates.set(
                                        refined,
                                        context.templates.pointeeNonNull(pointeeParam)
                                );
                            }
                        } else if (template >= context.templates.callBase) {
                            context.templates.set(refined, template);
                        }
                    }

                    bits &= bits - 1;
                }
            }

            System.arraycopy(
                    refined,
                    0,
                    state.symValue,
                    offset,
                    context.templates.words
            );
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

        public void markLocalsDeadForJump(int targetBlockDepth, int snapshotSlot) {
        }

        public void markLoopBodyLocalsDead(SastNode body) {
        }

        public boolean isSuppressed() {
            return suppressed;
        }

        public void setSuppressed(boolean value) {
            suppressed = value;
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

                long[] valueSet = symbolicValueOf(argument);
                long[] pointeeSet = symbolicPointeeOf(argument);
                long[] cellSet = symbolicCellOf(argument);
                int offset = builder.valueOffset(edgeIndex, param);

                for (int i = 0; i < context.templates.words; i++) {
                    builder.edgeValueSets[offset + i] |= valueSet[i];
                    builder.edgePointeeSets[offset + i] |= pointeeSet[i];
                }

                int cell = context.templates.first(cellSet);
                int cellCount = context.templates.count(cellSet);

                if (cellCount == 1) {
                    builder.edgeCellTargets[edgeIndex * builder.maxParams + param] =
                            cell;
                }
            }
        }

        private void handleCheckCall(SastNode call) {
            int callSiteId = context.callSiteId(call);
            int calleeId = context.callGraph.callSiteCallee[callSiteId];
            int paramCount = call.function.parameters.size();
            int[] rootBinding = new int[
                    paramCount * PointerNullAnalysisState.BINDING_STRIDE];

            for (int i = 0; i < paramCount; i++) {
                SastNode argument = call.args.get(i);
                int offset = i * PointerNullAnalysisState.BINDING_STRIDE;
                rootBinding[offset + PointerNullAnalysisState.BINDING_VALUE] =
                        concreteValueOf(argument);
                rootBinding[offset + PointerNullAnalysisState.BINDING_POINTEE] =
                        concretePointeeState(argument);
                rootBinding[offset + PointerNullAnalysisState.BINDING_CELL] =
                        concreteCellTargetVariable(argument);
                rootBinding[offset + PointerNullAnalysisState.BINDING_ROOT_ARG] = i;
            }

            byte[] rootRequirements = new byte[paramCount];
            byte[] rootPointeeRequirements = new byte[paramCount];

            MemoryBfsResult result = bfs.resolve(
                    callSiteId,
                    calleeId,
                    rootBinding,
                    new MemorySummaryBfs.NodeProcessor(this)
            );

            currentBfsResult = result;

            if (result.containsTop()) {
                callResultStates[callSiteId] = PointerNullAnalysisState.MAYBE_NULL;
                havocRootCells(call);
                invalidateGlobalPointers();
                currentBfsResult = null;
                return;
            }

            for (int node = 0; node < result.nodeCount(); node++) {
                int functionId = result.functionId(node);
                NullSummary summary = summaries[functionId];

                if (summary == null) {
                    continue;
                }

                for (int param = 0; param < summary.maxParams; param++) {
                    int offset = param * PointerNullAnalysisState.BINDING_STRIDE;
                    int rootArg = result.bindingAt(
                            node,
                            offset + PointerNullAnalysisState.BINDING_ROOT_ARG
                    );

                    if (rootArg < 0 || rootArg >= paramCount) {
                        continue;
                    }

                    byte requirement = summary.paramReq[param];

                    if (requirement != REQUIRE_NONE) {
                        mergeRequirement(
                                rootRequirements,
                                rootArg,
                                requirement
                        );
                    }

                    requirement = summary.pointeeReq[param];

                    if (requirement != REQUIRE_NONE) {
                        mergeRequirement(
                                rootPointeeRequirements,
                                rootArg,
                                requirement
                        );
                    }
                }
            }

            byte[] slotUpdates = new byte[state.pointerCount];
            byte[] slotUpdateCertainty = new byte[state.pointerCount];
            Arrays.fill(slotUpdates, (byte) -1);

            for (int node = 0; node < result.nodeCount(); node++) {
                int functionId = result.functionId(node);
                NullSummary summary = summaries[functionId];

                if (summary == null) {
                    continue;
                }

                for (int write = 0; write < summary.slotCount; write++) {
                    int targetParam = summary.slotTargetParams[write];
                    int offset = targetParam
                            * PointerNullAnalysisState.BINDING_STRIDE;
                    int cellVariable = result.bindingAt(
                            node,
                            offset + PointerNullAnalysisState.BINDING_CELL
                    );
                    int pointerIndex =
                            state.pointerIndexByVariableId(cellVariable);

                    if (pointerIndex < 0) {
                        continue;
                    }

                    int sourceOffset = write * summary.templateWords;
                    byte sourceState = evalValueSet(
                            summary.slotSource,
                            sourceOffset,
                            result,
                            node
                    );

                    byte certainty = summary.slotCertainty[write];

                    if (slotUpdates[pointerIndex] < 0) {
                        slotUpdates[pointerIndex] = sourceState;
                        slotUpdateCertainty[pointerIndex] = certainty;
                    } else if (slotUpdates[pointerIndex] != sourceState) {
                        slotUpdates[pointerIndex] =
                                PointerNullAnalysisState.MAYBE_NULL;
                        slotUpdateCertainty[pointerIndex] = REQUIRE_MAY;
                    } else if (certainty > slotUpdateCertainty[pointerIndex]) {
                        slotUpdateCertainty[pointerIndex] = certainty;
                    }
                }
            }

            for (int i = 0; i < slotUpdates.length; i++) {
                if (slotUpdates[i] < 0) {
                    continue;
                }

                if (slotUpdateCertainty[i] == REQUIRE_MUST) {
                    state.setState(i, slotUpdates[i]);
                } else if (state.stateOf(i) != slotUpdates[i]) {
                    state.setState(
                            i,
                            PointerNullAnalysisState.MAYBE_NULL
                    );
                }
            }

            byte returnState = resolveCallReturn(result, callSiteId);
            callResultStates[callSiteId] = returnState;
            emitCallRequirements(
                    call,
                    rootRequirements,
                    rootPointeeRequirements
            );
            invalidateGlobalPointers();
            currentBfsResult = null;
        }

        private void invalidateGlobalPointers() {
            for (int globalIndex = 0;
                    globalIndex < context.globalVariableIds.length;
                    globalIndex++) {
                int pointerIndex = state.pointerIndexByVariableId(
                        context.globalVariableIds[globalIndex]
                );

                if (pointerIndex >= 0) {
                    state.setState(
                            pointerIndex,
                            PointerNullAnalysisState.MAYBE_NULL
                    );
                    state.clearCellTargets(pointerIndex);
                }
            }
        }

        void processNode(int node) {
            int functionId = bfs.functionId(node);
            NullSummary summary = summaries[functionId];

            if (summary == null) {
                return;
            }

            for (int edge = 0; edge < summary.edgeCount; edge++) {
                int callSite = summary.edgeCallSiteIds[edge];
                int callee = context.callGraph.callSiteCallee[callSite];
                int calleeParams = context.callGraph.functions[callee]
                        .parameters.size();
                int[] binding = new int[
                        calleeParams * PointerNullAnalysisState.BINDING_STRIDE];

                for (int param = 0; param < calleeParams; param++) {
                    int offset = param * PointerNullAnalysisState.BINDING_STRIDE;
                    int valueOffset = (edge * summary.maxParams + param)
                            * summary.templateWords;
                    int pointeeOffset = valueOffset;

                    binding[offset + PointerNullAnalysisState.BINDING_VALUE] =
                            evalValueSet(
                                    summary.edgeValueSets,
                                    valueOffset,
                                    null,
                                    node
                            );
                    binding[offset + PointerNullAnalysisState.BINDING_POINTEE] =
                            evalValueSet(
                                    summary.edgePointeeSets,
                                    pointeeOffset,
                                    null,
                                    node
                            );

                    int cellTemplate = summary.edgeCellTargets[
                            edge * summary.maxParams + param];
                    binding[offset + PointerNullAnalysisState.BINDING_CELL] =
                            evalCellTarget(cellTemplate, node);

                    binding[offset + PointerNullAnalysisState.BINDING_ROOT_ARG] =
                            computeRootArg(
                                    summary,
                                    edge,
                                    param,
                                    cellTemplate,
                                    node
                            );
                }

                bfs.enqueue(functionId, callee, binding, callSite);
            }
        }

        private int computeRootArg(
                NullSummary summary,
                int edge,
                int param,
                int cellTemplate,
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

            if (count == 1 && first >= 0) {
                int valueParam = context.templates.paramOfValue(first);

                if (valueParam < 0) {
                    valueParam = context.templates.paramOfPointee(first);
                }

                if (valueParam >= 0) {
                    return resultRootArg(node, valueParam);
                }
            }

            int cellParam = context.templates.paramOfCell(cellTemplate);

            if (cellParam >= 0) {
                return resultRootArg(node, cellParam);
            }

            return -1;
        }

        private int resultRootArg(int node, int param) {
            if (bfs == null) {
                return -1;
            }

            return bfs.bindingAt(
                    node,
                    param * PointerNullAnalysisState.BINDING_STRIDE
                            + PointerNullAnalysisState.BINDING_ROOT_ARG
            );
        }

        private int evalCellTarget(int template, int node) {
            if (template < 0) {
                return -1;
            }

            int localVariable = context.templates.variableOfLocal(template);

            if (localVariable >= 0) {
                return localVariable;
            }

            int globalCell = context.templates.globalIndexOfCell(template);

            if (globalCell >= 0) {
                return context.globalVariableIds[globalCell];
            }

            int param = context.templates.paramOfCell(template);

            if (param >= 0) {
                return bfs.bindingAt(
                        node,
                        param * PointerNullAnalysisState.BINDING_STRIDE
                                + PointerNullAnalysisState.BINDING_CELL
                );
            }

            return -1;
        }

        private byte evalValueSet(
                long[] sets,
                int offset,
                MemoryBfsResult result,
                int node
        ) {
            boolean hasNull = false;
            boolean hasNonNull = false;
            boolean unknown = false;

            for (int word = 0; word < context.templates.words; word++) {
                long bits = sets[offset + word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    byte value = evalValueTemplate(template, result, node);

                    if (value < 0) {
                        bits &= bits - 1;
                        continue;
                    }

                    if (value == PointerNullAnalysisState.NULL) {
                        hasNull = true;
                    } else if (value == PointerNullAnalysisState.POINTING) {
                        hasNonNull = true;
                    } else {
                        unknown = true;
                    }

                    bits &= bits - 1;
                }
            }

            if (unknown || (hasNull && hasNonNull)) {
                return PointerNullAnalysisState.MAYBE_NULL;
            }

            if (hasNull) {
                return PointerNullAnalysisState.NULL;
            }

            if (hasNonNull) {
                return PointerNullAnalysisState.POINTING;
            }

            return PointerNullAnalysisState.MAYBE_NULL;
        }

        private byte evalValueTemplate(
                int template,
                MemoryBfsResult result,
                int node
        ) {
            if (template == MemoryTemplate.NULL) {
                return PointerNullAnalysisState.NULL;
            }

            if (template == MemoryTemplate.NONNULL) {
                return PointerNullAnalysisState.POINTING;
            }

            if (template == MemoryTemplate.UNKNOWN) {
                return PointerNullAnalysisState.MAYBE_NULL;
            }

            if (template == MemoryTemplate.LOCAL
                    || template == MemoryTemplate.CONST) {
                return PointerNullAnalysisState.POINTING;
            }

            int valueParam = context.templates.paramOfValue(template);

            if (valueParam >= 0) {
                int offset = valueParam * PointerNullAnalysisState.BINDING_STRIDE;
                byte value = bindingValue(node, offset);
                int constraint = context.templates.constraintOfValue(template);
                return applyConstraint(value, constraint);
            }

            int pointeeParam = context.templates.paramOfPointee(template);

            if (pointeeParam >= 0) {
                int offset = pointeeParam * PointerNullAnalysisState.BINDING_STRIDE;
                byte value = bindingPointee(node, offset);
                int constraint = context.templates.constraintOfPointee(template);
                return applyConstraint(value, constraint);
            }

            int callSite = context.templates.callSiteIndexOf(template);

            if (callSite >= 0) {
                MemoryBfsResult effective = result == null
                        ? currentBfsResult
                        : result;

                if (effective == null) {
                    return PointerNullAnalysisState.MAYBE_NULL;
                }

                return resolveCallReturn(effective, callSite);
            }

            int global = context.templates.globalIndexOf(template);

            if (global >= 0) {
                return PointerNullAnalysisState.POINTING;
            }

            return PointerNullAnalysisState.MAYBE_NULL;
        }

        private byte bindingValue(int node, int offset) {
            return (byte) bfs.bindingAt(
                    node,
                    offset + PointerNullAnalysisState.BINDING_VALUE
            );
        }

        private byte bindingPointee(int node, int offset) {
            return (byte) bfs.bindingAt(
                    node,
                    offset + PointerNullAnalysisState.BINDING_POINTEE
            );
        }

        private byte applyConstraint(byte value, int constraint) {
            if (constraint == MemoryTemplate.CONSTRAINT_NONNULL) {
                if (value == PointerNullAnalysisState.NULL) {
                    return -1;
                }

                return PointerNullAnalysisState.POINTING;
            }

            if (constraint == MemoryTemplate.CONSTRAINT_NULL) {
                if (value == PointerNullAnalysisState.POINTING) {
                    return -1;
                }

                return PointerNullAnalysisState.NULL;
            }

            return value;
        }

        private byte resolveCallReturn(
                MemoryBfsResult result,
                int callSiteId
        ) {
            if (callSiteId < 0 || callSiteId >= callResultStates.length) {
                return PointerNullAnalysisState.MAYBE_NULL;
            }

            if (callResultStates[callSiteId] >= 0) {
                return callResultStates[callSiteId];
            }

            if (result.isCallSiteTop(callSiteId)) {
                callResultStates[callSiteId] = PointerNullAnalysisState.MAYBE_NULL;
                return PointerNullAnalysisState.MAYBE_NULL;
            }

            int node = result.callSiteNodeIndex(callSiteId);

            if (node < 0) {
                callResultStates[callSiteId] = PointerNullAnalysisState.MAYBE_NULL;
                return PointerNullAnalysisState.MAYBE_NULL;
            }

            if (callResultResolving[callSiteId]) {
                return PointerNullAnalysisState.MAYBE_NULL;
            }

            int functionId = result.functionId(node);
            NullSummary summary = summaries[functionId];

            if (summary == null) {
                callResultStates[callSiteId] = PointerNullAnalysisState.MAYBE_NULL;
                return PointerNullAnalysisState.MAYBE_NULL;
            }

            callResultResolving[callSiteId] = true;
            byte stateValue = evalValueSet(
                    summary.returnSet,
                    0,
                    result,
                    node
            );
            callResultResolving[callSiteId] = false;
            callResultStates[callSiteId] = stateValue;
            return stateValue;
        }

        private void emitCallRequirements(
                SastNode call,
                byte[] requirements,
                byte[] pointeeRequirements
        ) {
            for (int i = 0; i < requirements.length; i++) {
                if (requirements[i] == REQUIRE_NONE
                        && pointeeRequirements[i] == REQUIRE_NONE) {
                    continue;
                }

                String parameterName = call.function.parameters.get(i).name;
                String functionName = call.function.qualifiedName();
                SastNode argument = call.args.get(i);
                byte value = concreteValueOf(argument);
                byte pointee = concretePointeeState(argument);

                if (requirements[i] != REQUIRE_NONE) {
                    reportRequirement(
                            value,
                            requirements[i],
                            parameterName,
                            functionName
                    );
                }

                if (pointeeRequirements[i] != REQUIRE_NONE) {
                    reportRequirement(
                            pointee,
                            pointeeRequirements[i],
                            parameterName,
                            functionName
                    );
                }
            }
        }

        private void reportRequirement(
                byte value,
                byte requirement,
                String parameterName,
                String functionName
        ) {
            if (value == PointerNullAnalysisState.POINTING) {
                return;
            }

            if (requirement == REQUIRE_MUST) {
                if (value == PointerNullAnalysisState.NULL) {
                    diagnostics.error(
                            INTER
                                    + "Passing null to parameter '"
                                    + parameterName
                                    + "' of '"
                                    + functionName
                                    + "' which is dereferenced"
                    );
                } else {
                    diagnostics.warning(
                            INTER
                                    + "Passing a possibly null value to parameter '"
                                    + parameterName
                                    + "' of '"
                                    + functionName
                                    + "' which may be dereferenced"
                    );
                }

                return;
            }

            if (value == PointerNullAnalysisState.NULL) {
                diagnostics.warning(
                        INTER
                                + "Passing null to parameter '"
                                + parameterName
                                + "' of '"
                                + functionName
                                + "' which may be dereferenced"
                );
            } else {
                diagnostics.warning(
                        INTER
                                + "Passing a possibly null value to parameter '"
                                + parameterName
                                + "' of '"
                                + functionName
                                + "' which may be dereferenced"
                );
            }
        }

        private void mergeRequirement(byte[] requirements, int index, byte value) {
            if (value > requirements[index]) {
                requirements[index] = value;
            }
        }

        private void havocRootCells(SastNode call) {
            for (SastNode argument : call.args) {
                int cellVariable = concreteCellTargetVariable(argument);
                int pointerIndex = state.pointerIndexByVariableId(cellVariable);

                if (pointerIndex >= 0) {
                    state.setState(
                            pointerIndex,
                            PointerNullAnalysisState.MAYBE_NULL
                    );
                }
            }
        }

        private byte concreteValueOf(SastNode expression) {
            if (expression == null) {
                return PointerNullAnalysisState.MAYBE_NULL;
            }

            if (expression.kind == SastKind.NULL_LITERAL) {
                return PointerNullAnalysisState.NULL;
            }

            if (expression.kind == SastKind.GET_PTR) {
                return PointerNullAnalysisState.POINTING;
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(expression.symbol);

                return pointerIndex < 0
                        ? PointerNullAnalysisState.MAYBE_NULL
                        : state.stateOf(pointerIndex);
            }

            if (expression.kind == SastKind.CAST) {
                return concreteValueOf(expression.expr);
            }

            if (expression.kind == SastKind.DEREF) {
                return concretePointeeState(expression.expr);
            }

            if (expression.kind == SastKind.FUNCTION_CALL) {
                int callSiteId = context.callSiteId(expression);
                byte value = callResultStates[callSiteId];

                return value < 0
                        ? PointerNullAnalysisState.MAYBE_NULL
                        : value;
            }

            return PointerNullAnalysisState.MAYBE_NULL;
        }

        private byte concretePointeeState(SastNode expression) {
            if (expression == null) {
                return PointerNullAnalysisState.MAYBE_NULL;
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(expression.symbol);

                if (pointerIndex < 0) {
                    return PointerNullAnalysisState.MAYBE_NULL;
                }

                int cellVariable = state.singleCellTarget(pointerIndex);
                int cellPointer = state.pointerIndexByVariableId(cellVariable);

                return cellPointer < 0
                        ? PointerNullAnalysisState.MAYBE_NULL
                        : state.stateOf(cellPointer);
            }

            if (expression.kind == SastKind.GET_PTR) {
                if (expression.target.type.kind == SastTypeKind.PTR) {
                    return concreteValueOf(expression.target);
                }

                return PointerNullAnalysisState.MAYBE_NULL;
            }

            if (expression.kind == SastKind.CAST) {
                return concretePointeeState(expression.expr);
            }

            return PointerNullAnalysisState.MAYBE_NULL;
        }

        private int concreteCellTargetVariable(SastNode expression) {
            if (expression == null) {
                return -1;
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(expression.symbol);

                return pointerIndex < 0
                        ? -1
                        : state.singleCellTarget(pointerIndex);
            }

            if (expression.kind == SastKind.GET_PTR) {
                if (expression.target.kind == SastKind.VAR_EXPR) {
                    return context.variableId(expression.target.symbol);
                }

                if (expression.target.kind == SastKind.DEREF) {
                    return concreteCellTargetVariable(expression.target.expr);
                }

                if (expression.target.kind == SastKind.CAST) {
                    return concreteCellTargetVariable(expression.target.expr);
                }

                return -1;
            }

            if (expression.kind == SastKind.CAST) {
                return concreteCellTargetVariable(expression.expr);
            }

            return -1;
        }

        private void copyConcreteCellTargets(
                SastNode expression,
                int targetPointerIndex
        ) {
            if (expression.kind == SastKind.VAR_EXPR) {
                int sourcePointerIndex = state.pointerIndex(expression.symbol);

                if (sourcePointerIndex >= 0) {
                    state.copyCellTargets(
                            targetPointerIndex,
                            sourcePointerIndex
                    );
                }

                return;
            }

            if (expression.kind == SastKind.GET_PTR) {
                if (expression.target.kind == SastKind.VAR_EXPR) {
                    state.setCellTarget(
                            targetPointerIndex,
                            context.variableId(expression.target.symbol)
                    );
                } else if (expression.target.kind == SastKind.DEREF) {
                    int cellVariable = concreteCellTargetVariable(expression.target.expr);

                    if (cellVariable >= 0) {
                        state.setCellTarget(targetPointerIndex, cellVariable);
                    }
                } else if (expression.target.kind == SastKind.CAST) {
                    copyConcreteCellTargets(expression.target.expr, targetPointerIndex);
                }

                return;
            }

            if (expression.kind == SastKind.CAST) {
                copyConcreteCellTargets(expression.expr, targetPointerIndex);
            }
        }

        private long[] symbolicValueOf(SastNode expression) {
            long[] set = context.templates.newSet();

            if (expression == null) {
                context.templates.set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression.kind == SastKind.NULL_LITERAL) {
                context.templates.set(set, MemoryTemplate.NULL);
                return set;
            }

            if (expression.kind == SastKind.GET_PTR) {
                context.templates.set(set, MemoryTemplate.NONNULL);
                return set;
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(expression.symbol);

                if (pointerIndex >= 0) {
                    int offset = state.symValueOffset(pointerIndex);

                    for (int i = 0; i < context.templates.words; i++) {
                        set[i] = state.symValue[offset + i];
                    }

                    return set;
                }

                context.templates.set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression.kind == SastKind.DEREF) {
                long[] pointee = symbolicPointeeOf(expression.expr);

                for (int i = 0; i < context.templates.words; i++) {
                    set[i] = pointee[i];
                }

                return set;
            }

            if (expression.kind == SastKind.CAST) {
                long[] inner = symbolicValueOf(expression.expr);

                for (int i = 0; i < context.templates.words; i++) {
                    set[i] = inner[i];
                }

                return set;
            }

            if (expression.kind == SastKind.FUNCTION_CALL) {
                int callSiteId = context.callSiteId(expression);
                context.templates.set(
                        set,
                        context.templates.callResult(callSiteId)
                );
                return set;
            }

            context.templates.set(set, MemoryTemplate.UNKNOWN);
            return set;
        }

        private long[] symbolicPointeeOf(SastNode expression) {
            long[] set = context.templates.newSet();

            if (expression == null) {
                context.templates.set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(expression.symbol);

                if (pointerIndex >= 0) {
                    int offset = state.symPointeeOffset(pointerIndex);

                    for (int i = 0; i < context.templates.words; i++) {
                        set[i] = state.symPointee[offset + i];
                    }

                    return set;
                }
            }

            if (expression.kind == SastKind.GET_PTR) {
                if (expression.target.type.kind == SastTypeKind.PTR) {
                    return symbolicValueOf(expression.target);
                }

                context.templates.set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression.kind == SastKind.CAST) {
                long[] inner = symbolicPointeeOf(expression.expr);

                for (int i = 0; i < context.templates.words; i++) {
                    set[i] = inner[i];
                }

                return set;
            }

            context.templates.set(set, MemoryTemplate.UNKNOWN);
            return set;
        }

        private long[] symbolicCellOf(SastNode expression) {
            long[] set = context.templates.newSet();

            if (expression == null) {
                return set;
            }

            if (expression.kind == SastKind.VAR_EXPR) {
                int pointerIndex = state.pointerIndex(expression.symbol);

                if (pointerIndex >= 0) {
                    int offset = state.symCellOffset(pointerIndex);

                    for (int i = 0; i < context.templates.words; i++) {
                        set[i] = state.symCell[offset + i];
                    }
                }

                return set;
            }

            if (expression.kind == SastKind.GET_PTR) {
                if (expression.target.kind == SastKind.VAR_EXPR) {
                    int variableId = context.variableId(expression.target.symbol);
                    int globalIndex = context.globalIndex(expression.target.symbol);

                    if (globalIndex >= 0) {
                        context.templates.set(
                                set,
                                context.templates.globalCell(globalIndex)
                        );
                    } else if (variableId >= 0) {
                        context.templates.set(
                                set,
                                context.templates.localValue(variableId)
                        );
                    }

                    return set;
                }

                if (expression.target.kind == SastKind.DEREF) {
                    long[] inner = symbolicCellOf(expression.target.expr);

                    for (int i = 0; i < context.templates.words; i++) {
                        set[i] = inner[i];
                    }

                    return set;
                }

                if (expression.target.kind == SastKind.CAST) {
                    long[] inner = symbolicCellOf(expression.target.expr);

                    for (int i = 0; i < context.templates.words; i++) {
                        set[i] = inner[i];
                    }

                    return set;
                }

                return set;
            }

            if (expression.kind == SastKind.CAST) {
                long[] inner = symbolicCellOf(expression.expr);

                for (int i = 0; i < context.templates.words; i++) {
                    set[i] = inner[i];
                }
            }

            return set;
        }

        private boolean mayBeNull(long[] set) {
            if (context.templates.isEmpty(set)) {
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
            if (context.templates.isEmpty(set)) {
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
            int word = index >>> 6;
            long mask = 1L << (index & 63);

            if ((bits[word] & mask) != 0L) {
                return false;
            }

            bits[word] |= mask;
            return true;
        }
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
