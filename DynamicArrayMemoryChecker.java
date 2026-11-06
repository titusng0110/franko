import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class DynamicArrayMemoryChecker {
    private static final String INTRA = "Intraprocedural: ";
    private static final String INTER = "Interprocedural: ";

    private static final byte REQUIRE_NONE = 0;
    private static final byte REQUIRE_MAY = 1;
    private static final byte REQUIRE_MUST = 2;

    private static final class DynamicSummary {
        final byte[] paramReq;
        final byte[] paramPost;
        final byte[] invalidateMust;
        final byte[] invalidateMay;
        final byte[] freeMust;
        final byte[] freeMay;
        final byte[] escapeMust;
        final byte[] escapeMay;
        final byte[] useMust;
        final byte[] useMay;
        final long[] returnAliasSet;
        final int edgeCount;
        final int[] edgeCallSiteIds;
        final long[] edgeAliasSets;
        final int maxParams;
        final int templateWords;

        DynamicSummary(
                byte[] paramReq,
                byte[] paramPost,
                byte[] invalidateMust,
                byte[] invalidateMay,
                byte[] freeMust,
                byte[] freeMay,
                byte[] escapeMust,
                byte[] escapeMay,
                byte[] useMust,
                byte[] useMay,
                long[] returnAliasSet,
                int edgeCount,
                int[] edgeCallSiteIds,
                long[] edgeAliasSets,
                int maxParams,
                int templateWords
        ) {
            this.paramReq = paramReq;
            this.paramPost = paramPost;
            this.invalidateMust = invalidateMust;
            this.invalidateMay = invalidateMay;
            this.freeMust = freeMust;
            this.freeMay = freeMay;
            this.escapeMust = escapeMust;
            this.escapeMay = escapeMay;
            this.useMust = useMust;
            this.useMay = useMay;
            this.returnAliasSet = returnAliasSet;
            this.edgeCount = edgeCount;
            this.edgeCallSiteIds = edgeCallSiteIds;
            this.edgeAliasSets = edgeAliasSets;
            this.maxParams = maxParams;
            this.templateWords = templateWords;
        }
    }

    private static final class DynamicSummaryBuilder {
        final int edgeCount;
        final int maxParams;
        final int templateWords;
        final long[] edgeAliasSets;

        DynamicSummaryBuilder(int edgeCount, int maxParams, int templateWords) {
            this.edgeCount = edgeCount;
            this.maxParams = maxParams;
            this.templateWords = templateWords;
            this.edgeAliasSets = new long[edgeCount * maxParams * templateWords];
        }

        int offset(int edgeIndex, int param) {
            return (edgeIndex * maxParams + param) * templateWords;
        }
    }

    private static final class AliasValue {
        static final AliasValue NONE = new AliasValue(
                -1, DynamicArrayAnalysisState.UNINITED,
                DynamicArrayAnalysisState.FREED_NEVER,
                DynamicArrayAnalysisState.ALIAS_NONE, 0, 0, false, false
        );
        static final AliasValue UNKNOWN = new AliasValue(
                -1, DynamicArrayAnalysisState.MAYBE_INITED,
                DynamicArrayAnalysisState.FREED_MAYBE,
                DynamicArrayAnalysisState.ALIAS_UNKNOWN, 0, 0, false, true
        );

        final int arrayIndex;
        final int lifecycle;
        final int freed;
        final int aliasRoot;
        final int aliasEpoch;
        final int epoch;
        final boolean element;
        final boolean unknown;

        AliasValue(
                int arrayIndex,
                int lifecycle,
                int freed,
                int aliasRoot,
                int aliasEpoch,
                int epoch,
                boolean element,
                boolean unknown
        ) {
            this.arrayIndex = arrayIndex;
            this.lifecycle = lifecycle;
            this.freed = freed;
            this.aliasRoot = aliasRoot;
            this.aliasEpoch = aliasEpoch;
            this.epoch = epoch;
            this.element = element;
            this.unknown = unknown;
        }
    }

    private static final class ArrayOrigins {
        final long[] bits;
        final boolean unknown;

        ArrayOrigins(long[] bits, boolean unknown) {
            this.bits = bits;
            this.unknown = unknown;
        }

        int count() {
            int total = 0;

            for (long word : bits) {
                total += Long.bitCount(word);
            }

            return total;
        }

        int first() {
            for (int word = 0; word < bits.length; word++) {
                if (bits[word] != 0L) {
                    return (word << 6) + Long.numberOfTrailingZeros(bits[word]);
                }
            }

            return -1;
        }

    }

    private final DiagnosticBag diagnostics;

    private MemoryAnalysisContext context;
    private DynamicArrayAnalysisState state;
    private MemoryDataflowDriver driver;
    private MemorySummaryBfs bfs;
    private DynamicSummary[] summaries;

    private boolean summaryMode;
    private int currentFunctionId = -1;
    private DynamicSummaryBuilder builder;
    private final List<long[]> exitReturnSets = new ArrayList<>();

    private AliasValue[] callResultAliases;
    private boolean[] callResultResolving;
    private MemoryBfsResult currentBfsResult;

    private long[] reportedErrorSites;
    private long[] reportedWarningSites;

    public DynamicArrayMemoryChecker(DiagnosticBag diagnostics) {
        this(diagnostics, null);
    }

    public DynamicArrayMemoryChecker(
            DiagnosticBag diagnostics,
            MemoryAnalysisContext context
    ) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
        this.context = context;
    }

    public void check(SemanticASTNode node) {
        if (!(node instanceof SemanticProgramNode program)) {
            throw new IllegalArgumentException(
                    "Dynamic-array checking requires a semantic program root"
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
                document.withPhase(SastPhase.DYN_ARRAY),
                diagnostics.diagnostics()
        );
    }

    private void ensureContext(SemanticProgramNode root) {
        if (context == null || context.root != root) {
            context = MemoryAnalysisContext.build(root);
            state = null;
            bfs = null;
            summaries = null;
            callResultAliases = null;
            callResultResolving = null;
            reportedErrorSites = null;
            reportedWarningSites = null;
        }

        if (state == null) {
            state = new DynamicArrayAnalysisState(context);
            bfs = new MemorySummaryBfs(context);
            summaries = new DynamicSummary[context.callGraph.functionCount()];
            callResultAliases = new AliasValue[context.callSiteCount];
            callResultResolving = new boolean[context.callSiteCount];
            reportedErrorSites = new long[Math.max(1, (context.siteCount + 63) >>> 6)];
            reportedWarningSites = new long[Math.max(1, (context.siteCount + 63) >>> 6)];
        }
    }

    private void runSummaryPass() {
        summaryMode = true;
        summaries = new DynamicSummary[context.callGraph.functionCount()];
        driver = new MemoryDataflowDriver(context, diagnostics, new DynamicDomain());
        driver.run(context.root);
        summaryMode = false;
    }

    private void runCheckPass() {
        summaryMode = false;
        driver = new MemoryDataflowDriver(context, diagnostics, new DynamicDomain());
        driver.run(context.root);
    }

    private final class DynamicDomain implements MemoryDataflowDriver.Domain {
        private boolean suppressed;

        @Override
        public void beginFunction(int functionId) {
            currentFunctionId = functionId;
            state.reset();
            exitReturnSets.clear();
            Arrays.fill(callResultAliases, null);
            Arrays.fill(callResultResolving, false);
            Arrays.fill(reportedErrorSites, 0L);
            Arrays.fill(reportedWarningSites, 0L);
            currentBfsResult = null;
            suppressed = false;

            SemanticFunctionDeclNode decl = context.functionDecl(functionId);

            if (decl == null) {
                builder = null;
                return;
            }

            if (summaryMode) {
                builder = new DynamicSummaryBuilder(
                        context.callGraph.edgeCount[functionId],
                        context.templates.paramCount,
                        context.templates.words
                );
                seedSummaryParameters(decl);
                seedSummaryGlobals();
            } else {
                builder = null;
                seedCheckParameters(decl);
                seedGlobalOwners();
            }
        }

        private void seedGlobalOwners() {
            for (int globalIndex = 0;
                    globalIndex < context.globalVariableIds.length;
                    globalIndex++) {
                int variableId = context.globalVariableIds[globalIndex];
                int ownerIndex = state.ownerIndexOfVariable(variableId);
                int pointerIndex = state.pointerIndexOfVariable(variableId);

                if (ownerIndex >= 0) {
                    state.clearOwner(ownerIndex);
                    state.setOwnerUnknown(ownerIndex);
                }

                if (pointerIndex >= 0) {
                    state.clearAlias(pointerIndex);
                }
            }
        }

        private void seedSummaryParameters(SemanticFunctionDeclNode decl) {
            for (int i = 0; i < decl.parameterVariables.size(); i++) {
                VariableSymbol parameter = decl.parameterVariables.get(i);

                if (!(parameter.type instanceof SemanticPtrType)) {
                    continue;
                }

                int pointerIndex = state.pointerIndexOf(parameter);

                if (pointerIndex < 0) {
                    continue;
                }

                long[] alias = context.templates.newSet();
                context.templates.set(
                        alias,
                        context.templates.aliasOfPointer(i)
                );
                System.arraycopy(
                        alias,
                        0,
                        state.symAlias,
                        pointerIndex * context.templates.words,
                        context.templates.words
                );

                if (isOwnerArrayParameter(parameter)) {
                    state.paramPost[i] = DynamicArrayAnalysisState.POST_UNCHANGED;
                }
            }
        }

        private void seedSummaryGlobals() {
            for (int globalIndex = 0;
                    globalIndex < context.globalVariableIds.length;
                    globalIndex++) {
                int variableId = context.globalVariableIds[globalIndex];
                int pointerIndex = state.pointerIndexOfVariable(variableId);

                if (pointerIndex < 0) {
                    continue;
                }

                long[] alias = context.templates.newSet();
                context.templates.set(alias, MemoryTemplate.UNKNOWN);
                System.arraycopy(
                        alias,
                        0,
                        state.symAlias,
                        pointerIndex * context.templates.words,
                        context.templates.words
                );
            }
        }

        private boolean isOwnerArrayParameter(VariableSymbol parameter) {
            return parameter.type instanceof SemanticPtrType pointer
                    && pointer.permission == PtrPermission.OWNER
                    && pointer.referencedType instanceof SemanticDynamicArrayType;
        }

        private void seedCheckParameters(SemanticFunctionDeclNode decl) {
            for (int i = 0; i < decl.parameterVariables.size(); i++) {
                VariableSymbol parameter = decl.parameterVariables.get(i);
                int ownerIndex = state.ownerIndexOf(parameter);
                int pointerIndex = state.pointerIndexOf(parameter);

                if (ownerIndex >= 0) {
                    state.clearOwner(ownerIndex);
                    state.setOwnerUnknown(ownerIndex);
                }

                if (pointerIndex >= 0) {
                    state.clearAlias(pointerIndex);
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

        private DynamicSummary buildSummary(int exitCount) {
            int params = context.templates.paramCount;
            byte[] paramReq = new byte[params];
            byte[] paramPost = new byte[params];
            byte[] invalidateMust = new byte[params];
            byte[] invalidateMay = new byte[params];
            byte[] freeMust = new byte[params];
            byte[] freeMay = new byte[params];
            byte[] escapeMust = new byte[params];
            byte[] escapeMay = new byte[params];
            byte[] useMust = new byte[params];
            byte[] useMay = new byte[params];

            for (int param = 0; param < params; param++) {
                paramReq[param] = requirement(exitCount, param, true);
                paramPost[param] = mergedPost(exitCount, param);
                invalidateMust[param] = certainty(exitCount, param, 0, true);
                invalidateMay[param] = certainty(exitCount, param, 0, false);
                freeMust[param] = certainty(exitCount, param, 1, true);
                freeMay[param] = certainty(exitCount, param, 1, false);
                escapeMust[param] = certainty(exitCount, param, 2, true);
                escapeMay[param] = certainty(exitCount, param, 2, false);
                useMust[param] = certainty(exitCount, param, 3, true);
                useMay[param] = certainty(exitCount, param, 3, false);
            }

            long[] returnAlias = context.templates.newSet();

            for (long[] exitSet : exitReturnSets) {
                context.templates.union(returnAlias, exitSet);
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

            return new DynamicSummary(
                    paramReq,
                    paramPost,
                    invalidateMust,
                    invalidateMay,
                    freeMust,
                    freeMay,
                    escapeMust,
                    escapeMay,
                    useMust,
                    useMay,
                    returnAlias,
                    edgeCount,
                    edgeCallSiteIds,
                    builder == null
                            ? new long[0]
                            : builder.edgeAliasSets,
                    context.templates.paramCount,
                    context.templates.words
            );
        }

        private byte requirement(int exitCount, int param, boolean require) {
            boolean must = exitCount > 0;
            boolean may = false;

            for (int exit = 0; exit < exitCount; exit++) {
                int slot = driver.exitSlot(exit);
                must &= bitAt(
                        state.requireMustSnapshots(slot),
                        slot * state.paramWords,
                        param
                );
                may |= bitAt(
                        state.requireMaySnapshots(slot),
                        slot * state.paramWords,
                        param
                );
            }

            if (must) {
                return REQUIRE_MUST;
            }

            return may ? REQUIRE_MAY : REQUIRE_NONE;
        }

        private byte certainty(
                int exitCount,
                int param,
                int category,
                boolean mustSide
        ) {
            boolean must = exitCount > 0;
            boolean may = false;

            for (int exit = 0; exit < exitCount; exit++) {
                int slot = driver.exitSlot(exit);
                long[] mustBits = switch (category) {
                    case 0 -> state.invalidateMustSnapshots(slot);
                    case 1 -> state.freeMustSnapshots(slot);
                    case 2 -> state.escapeMustSnapshots(slot);
                    default -> state.useMustSnapshots(slot);
                };
                long[] mayBits = switch (category) {
                    case 0 -> state.invalidateMaySnapshots(slot);
                    case 1 -> state.freeMaySnapshots(slot);
                    case 2 -> state.escapeMaySnapshots(slot);
                    default -> state.useMaySnapshots(slot);
                };

                must &= bitAt(mustBits, slot * state.paramWords, param);
                may |= bitAt(mayBits, slot * state.paramWords, param);
            }

            if (must) {
                return REQUIRE_MUST;
            }

            return may ? REQUIRE_MAY : REQUIRE_NONE;
        }

        private byte mergedPost(int exitCount, int param) {
            if (exitCount <= 0) {
                return DynamicArrayAnalysisState.POST_UNCHANGED;
            }

            byte result = state.paramPostSnapshot(driver.exitSlot(0), param);

            for (int exit = 1; exit < exitCount; exit++) {
                byte incoming = state.paramPostSnapshot(
                        driver.exitSlot(exit),
                        param
                );

                if (result != incoming) {
                    result = DynamicArrayAnalysisState.POST_MAYBE;
                }
            }

            return result;
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
            if (!summaryMode && !terminated && !suppressed) {
                int[] locals = context.blockLocalIds(node);

                for (int variableId : locals) {
                    int arrayIndex = state.arrayIndexOfVariable(variableId);

                    if (arrayIndex >= 0) {
                        reportLeakIfNeeded(arrayIndex, "end of scope");
                    }
                }
            }

            state.leaveBlock(node, depth, terminated);
        }

        @Override
        public void markLocalsDeadForJump(int targetBlockDepth, int snapshotSlot) {
        }

        @Override
        public void markLoopBodyLocalsDead(SemanticStmtNode body) {
        }

        @Override
        public void checkVarDecl(SemanticVarDeclNode node) {
            diagnostics.push(node.span);

            try {
                int arrayIndex = state.arrayIndexOf(node.symbol);

                if (arrayIndex >= 0) {
                    state.lifecycle[arrayIndex] =
                            DynamicArrayAnalysisState.UNINITED;
                    state.freed[arrayIndex] =
                            DynamicArrayAnalysisState.FREED_NEVER;
                    state.escaped[arrayIndex] = false;
                    state.epochMin[arrayIndex] = 0;
                    state.epochMax[arrayIndex] = 0;
                }

                int ownerIndex = state.ownerIndexOf(node.symbol);

                if (ownerIndex >= 0) {
                    state.clearOwner(ownerIndex);
                }

                int pointerIndex = state.pointerIndexOf(node.symbol);

                if (pointerIndex >= 0) {
                    state.clearAlias(pointerIndex);

                    if (summaryMode) {
                        long[] alias = context.templates.newSet();
                        System.arraycopy(
                                alias,
                                0,
                                state.symAlias,
                                pointerIndex * context.templates.words,
                                context.templates.words
                        );
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

                if (node.isCompound()) {
                    return;
                }

                if (summaryMode) {
                    applySummaryAssignment(node);
                } else {
                    applyCheckAssignment(node);
                }
            } finally {
                diagnostics.pop();
            }
        }

        private void applySummaryAssignment(SemanticAssignNode node) {
            long[] alias = symbolicAliasOf(node.value);

            if (node.target instanceof SemanticVarExprNode variable) {
                int pointerIndex = state.pointerIndexOf(variable.symbol);

                if (pointerIndex >= 0) {
                    System.arraycopy(
                            alias,
                            0,
                            state.symAlias,
                            pointerIndex * context.templates.words,
                            context.templates.words
                    );
                }
            }

            if (isEscapeSink(node.target)) {
                markEscape(alias);
            }
        }

        private boolean isEscapeSink(SemanticExprNode target) {
            if (target instanceof SemanticVarExprNode variable) {
                return variable.symbol instanceof GlobalVariableSymbol;
            }

            if (target instanceof SemanticMemberAccessNode) {
                return true;
            }

            if (target instanceof SemanticDerefNode) {
                return true;
            }

            return false;
        }

        private void markEscape(long[] alias) {
            for (int word = 0; word < context.templates.words; word++) {
                long bits = alias[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int param = context.templates.paramOfAlias(template);

                    if (param < 0) {
                        param = context.templates.paramOfWholeArray(template);
                    }

                    if (param >= 0) {
                        state.escapeMust[param >>> 6] |= 1L << (param & 63);
                        state.escapeMay[param >>> 6] |= 1L << (param & 63);
                    }

                    bits &= bits - 1;
                }
            }
        }

        private void applyCheckAssignment(SemanticAssignNode node) {
            AliasValue value = concreteAliasOf(node.value);

            if (node.target instanceof SemanticVarExprNode variable) {
                int ownerIndex = state.ownerIndexOf(variable.symbol);
                int pointerIndex = state.pointerIndexOf(variable.symbol);

                if (ownerIndex >= 0) {
                    state.clearOwner(ownerIndex);

                    if (value.arrayIndex >= 0) {
                        state.addOwner(ownerIndex, value.arrayIndex);
                    } else if (value.unknown) {
                        state.setOwnerUnknown(ownerIndex);
                    }
                }

                if (pointerIndex >= 0) {
                    if (value.aliasRoot >= 0) {
                        state.setAlias(
                                pointerIndex,
                                value.aliasRoot,
                                value.aliasEpoch
                        );
                    } else {
                        state.clearAlias(pointerIndex);
                    }
                }

                if (variable.symbol instanceof GlobalVariableSymbol
                        && value.arrayIndex >= 0) {
                    state.escaped[value.arrayIndex] = true;
                }

                return;
            }

            if (node.target instanceof SemanticDerefNode deref) {
                int ownerIndex = ownerIndexForCell(deref.expr);

                if (ownerIndex >= 0) {
                    state.clearOwner(ownerIndex);

                    if (value.arrayIndex >= 0) {
                        state.addOwner(ownerIndex, value.arrayIndex);
                        state.escaped[value.arrayIndex] = true;
                    } else if (value.unknown) {
                        state.setOwnerUnknown(ownerIndex);
                    }
                }

                return;
            }

            if (isEscapeSink(node.target) && value.arrayIndex >= 0) {
                state.escaped[value.arrayIndex] = true;
            }
        }

        private int ownerIndexForCell(SemanticExprNode pointerExpression) {
            if (pointerExpression instanceof SemanticVarExprNode variable) {
                return state.ownerIndexOf(variable.symbol);
            }

            return -1;
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
                    || expression instanceof SemanticVarExprNode
                    || expression instanceof SemanticEnumVariantNode) {
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

                if (summaryMode) {
                    markSummaryUse(access.target);
                    markSummaryRequire(access.target);
                } else {
                    checkAliasUse(access.target, access);
                    requireInitializedForExpression(access.target, access);
                }

                return;
            }

            if (expression instanceof SemanticMemberAccessNode member) {
                checkExpr(member.target);

                if (!summaryMode) {
                    checkAliasUse(member.target, member);
                }

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

                if (summaryMode) {
                    applySummaryIntrinsic(intrinsic);
                } else {
                    checkAliasUse(intrinsic.receiver, intrinsic);
                    applyCheckIntrinsic(intrinsic);
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
                    checkAliasUse(getPtr.target, getPtr);
                } else {
                    markSummaryUse(getPtr.target);
                }

                return;
            }

            if (expression instanceof SemanticDerefNode deref) {
                checkExpr(deref.expr);

                if (summaryMode) {
                    markSummaryUse(deref.expr);
                } else {
                    checkAliasUse(deref.expr, deref);
                }

                return;
            }

            if (expression instanceof SemanticCastExprNode cast) {
                checkExpr(cast.expr);
            }
        }

        private void checkAliasUse(
                SemanticExprNode expression,
                SemanticASTNode site
        ) {
            int pointerIndex = pointerIndexOfExpression(expression);

            if (pointerIndex < 0) {
                return;
            }

            int root = state.aliasRoot[pointerIndex];

            if (root < 0 || root >= state.arrayCount) {
                return;
            }

            int epoch = state.aliasEpoch[pointerIndex];

            if (epoch < state.epochMin[root]) {
                reportAliasError(
                        site,
                        "Pointer '" + pointerName(pointerIndex)
                                + "' was invalidated by resize of dynamic array '"
                                + arrayName(root)
                                + "'"
                );
            } else if (epoch < state.epochMax[root]) {
                reportAliasWarning(
                        site,
                        "Pointer '" + pointerName(pointerIndex)
                                + "' may have been invalidated by resize or"
                                + " uninit of dynamic array '"
                                + arrayName(root)
                                + "'"
                );
            }
        }

        private int pointerIndexOfExpression(SemanticExprNode expression) {
            if (expression instanceof SemanticVarExprNode variable) {
                return state.pointerIndexOf(variable.symbol);
            }

            if (expression instanceof SemanticDerefNode deref) {
                return pointerIndexOfExpression(deref.expr);
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return pointerIndexOfExpression(cast.expr);
            }

            return -1;
        }

        private void reportAliasError(SemanticASTNode site, String message) {
            if (suppressed) {
                return;
            }

            int siteId = context.siteId(site);

            if (siteId < 0 || addBit(reportedErrorSites, siteId)) {
                diagnostics.error(INTRA + message);
            }
        }

        private void reportAliasWarning(SemanticASTNode site, String message) {
            if (suppressed) {
                return;
            }

            int siteId = context.siteId(site);

            if (siteId < 0 || addBit(reportedWarningSites, siteId)) {
                diagnostics.warning(INTRA + message);
            }
        }

        private void requireInitializedForExpression(
                SemanticExprNode expression,
                SemanticASTNode site
        ) {
            ArrayOrigins origins = storageArrayOrigins(expression);

            if (origins.unknown) {
                return;
            }

            for (int word = 0; word < origins.bits.length; word++) {
                long bits = origins.bits[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int arrayIndex = (word << 6) + bit;
                    requireInitialized(arrayIndex, site);
                    bits &= bits - 1;
                }
            }
        }

        private void applyCheckIntrinsic(
                SemanticArrayIntrinsicCallNode intrinsic
        ) {
            switch (intrinsic.kind) {
                case INIT:
                case INIT_ZERO:
                    applyInit(intrinsic.receiver);
                    return;

                case RESIZE:
                    applyResize(intrinsic.receiver, intrinsic);
                    return;

                case UNINIT:
                    applyUninit(intrinsic.receiver, intrinsic);
                    return;

                case LENGTH:
                    return;

                default:
                    requireInitializedForExpression(
                            intrinsic.receiver,
                            intrinsic
                    );

                    if (intrinsic.kind == SemanticArrayIntrinsicKind.MEMCPY
                            || intrinsic.kind == SemanticArrayIntrinsicKind.MEMCMP
                            || intrinsic.kind == SemanticArrayIntrinsicKind.MEMMEM) {
                        if (!intrinsic.args.isEmpty()) {
                            requirePointerSourceInitialized(
                                    intrinsic.args.get(0),
                                    intrinsic
                            );
                        }
                    }
            }
        }

        private void requirePointerSourceInitialized(
                SemanticExprNode source,
                SemanticASTNode site
        ) {
            AliasValue value = concreteAliasOf(source);

            if (value.arrayIndex >= 0) {
                requireInitialized(value.arrayIndex, site);
            }
        }

        private void applySummaryIntrinsic(
                SemanticArrayIntrinsicCallNode intrinsic
        ) {
            long[] aliases = symbolicArrayOf(intrinsic.receiver);
            boolean require = intrinsic.kind != SemanticArrayIntrinsicKind.INIT
                    && intrinsic.kind != SemanticArrayIntrinsicKind.INIT_ZERO
                    && intrinsic.kind != SemanticArrayIntrinsicKind.LENGTH;
            boolean invalidate = intrinsic.kind == SemanticArrayIntrinsicKind.INIT
                    || intrinsic.kind == SemanticArrayIntrinsicKind.INIT_ZERO
                    || intrinsic.kind == SemanticArrayIntrinsicKind.RESIZE
                    || intrinsic.kind == SemanticArrayIntrinsicKind.UNINIT;
            boolean free = intrinsic.kind == SemanticArrayIntrinsicKind.UNINIT;

            for (int word = 0; word < context.templates.words; word++) {
                long bits = aliases[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int param = context.templates.paramOfAlias(template);

                    if (param < 0) {
                        param = context.templates.paramOfWholeArray(template);
                    }

                    if (param >= 0) {
                        if (require) {
                            markSummaryRequireParam(param);
                        }

                        if (invalidate) {
                            state.invalidateMust[param >>> 6] |=
                                    1L << (param & 63);
                            state.invalidateMay[param >>> 6] |=
                                    1L << (param & 63);
                        }

                        if (free) {
                            state.freeMust[param >>> 6] |=
                                    1L << (param & 63);
                            state.freeMay[param >>> 6] |=
                                    1L << (param & 63);
                        }

                        byte newPost = postForIntrinsic(intrinsic.kind);

                        if (newPost
                                != DynamicArrayAnalysisState.POST_UNCHANGED) {
                            state.paramPost[param] = newPost;
                        }
                    } else {
                        int localVariable =
                                context.templates.variableOfLocal(template);

                        if (localVariable < 0) {
                            localVariable = context.templates
                                    .variableOfLocalElement(template);
                        }

                        if (localVariable >= 0) {
                            int arrayIndex = state.arrayIndexOfVariable(
                                    localVariable
                            );

                            if (arrayIndex >= 0) {
                                applyLocalIntrinsic(arrayIndex, intrinsic);
                            }
                        }
                    }

                    bits &= bits - 1;
                }
            }

            if (intrinsic.kind == SemanticArrayIntrinsicKind.LENGTH) {
                return;
            }

            markSummaryUse(intrinsic.receiver);
        }

        private byte postForIntrinsic(SemanticArrayIntrinsicKind kind) {
            return switch (kind) {
                case INIT, INIT_ZERO, RESIZE ->
                        DynamicArrayAnalysisState.POST_INITED;
                case UNINIT -> DynamicArrayAnalysisState.POST_UNINITED;
                default -> DynamicArrayAnalysisState.POST_UNCHANGED;
            };
        }

        private void markSummaryRequire(SemanticExprNode expression) {
            long[] aliases = symbolicArrayOf(expression);

            for (int word = 0; word < context.templates.words; word++) {
                long bits = aliases[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int param = context.templates.paramOfAlias(template);

                    if (param < 0) {
                        param = context.templates.paramOfWholeArray(template);
                    }

                    if (param < 0) {
                        param = context.templates.paramOfElement(template);
                    }

                    if (param >= 0) {
                        markSummaryRequireParam(param);
                    }

                    bits &= bits - 1;
                }
            }
        }

        private void markSummaryRequireParam(int param) {
            if (state.paramPost[param]
                    == DynamicArrayAnalysisState.POST_INITED) {
                return;
            }

            state.requireMust[param >>> 6] |= 1L << (param & 63);
            state.requireMay[param >>> 6] |= 1L << (param & 63);
        }

        private int summaryOwnerParam(SemanticExprNode expression) {
            long[] aliases = symbolicArrayOf(expression);

            for (int word = 0; word < context.templates.words; word++) {
                long bits = aliases[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int param = context.templates.paramOfAlias(template);

                    if (param < 0) {
                        param = context.templates.paramOfWholeArray(template);
                    }

                    if (param < 0) {
                        param = context.templates.paramOfElement(template);
                    }

                    if (param >= 0) {
                        return param;
                    }

                    bits &= bits - 1;
                }
            }

            return -1;
        }

        private int summaryLocalArray(SemanticExprNode expression) {
            long[] aliases = symbolicArrayOf(expression);

            for (int word = 0; word < context.templates.words; word++) {
                long bits = aliases[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int variableId = context.templates.variableOfLocal(template);

                    if (variableId < 0) {
                        variableId = context.templates
                                .variableOfLocalElement(template);
                    }

                    if (variableId >= 0) {
                        int arrayIndex =
                                state.arrayIndexOfVariable(variableId);

                        if (arrayIndex >= 0) {
                            return arrayIndex;
                        }
                    }

                    bits &= bits - 1;
                }
            }

            return -1;
        }

        private void markSummaryUse(SemanticExprNode expression) {
            long[] aliases = symbolicArrayOf(expression);

            for (int word = 0; word < context.templates.words; word++) {
                long bits = aliases[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    int param = context.templates.paramOfAlias(template);

                    if (param < 0) {
                        param = context.templates.paramOfWholeArray(template);
                    }

                    if (param < 0) {
                        param = context.templates.paramOfElement(template);
                    }

                    if (param >= 0) {
                        state.useMust[param >>> 6] |= 1L << (param & 63);
                        state.useMay[param >>> 6] |= 1L << (param & 63);
                    }

                    bits &= bits - 1;
                }
            }
        }

        private void applyLocalIntrinsic(
                int arrayIndex,
                SemanticArrayIntrinsicCallNode intrinsic
        ) {
            switch (intrinsic.kind) {
                case INIT:
                case INIT_ZERO:
                    state.lifecycle[arrayIndex] =
                            DynamicArrayAnalysisState.INITED;
                    state.freed[arrayIndex] =
                            DynamicArrayAnalysisState.FREED_NEVER;
                    advanceEpoch(arrayIndex, true);
                    return;

                case RESIZE:
                    state.lifecycle[arrayIndex] =
                            DynamicArrayAnalysisState.INITED;
                    state.freed[arrayIndex] =
                            DynamicArrayAnalysisState.FREED_NEVER;
                    advanceEpoch(arrayIndex, true);
                    return;

                case UNINIT:
                    state.lifecycle[arrayIndex] =
                            DynamicArrayAnalysisState.UNINITED;
                    state.freed[arrayIndex] =
                            DynamicArrayAnalysisState.FREED_YES;
                    advanceEpoch(arrayIndex, true);
                    return;

                case LENGTH:
                    return;

                default:
                    return;
            }
        }

        private void applyInit(SemanticExprNode receiver) {
            ArrayOrigins origins = storageArrayOrigins(receiver);

            if (origins.unknown || origins.count() == 0) {
                havocEscapedArrays();
                return;
            }

            for (int word = 0; word < origins.bits.length; word++) {
                long bits = origins.bits[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int arrayIndex = (word << 6) + bit;
                    state.lifecycle[arrayIndex] =
                            DynamicArrayAnalysisState.INITED;
                    state.freed[arrayIndex] =
                            DynamicArrayAnalysisState.FREED_NEVER;
                    advanceEpoch(arrayIndex, true);
                    bits &= bits - 1;
                }
            }
        }

        private void applyResize(
                SemanticExprNode receiver,
                SemanticASTNode site
        ) {
            ArrayOrigins origins = storageArrayOrigins(receiver);

            if (origins.unknown || origins.count() == 0) {
                havocEscapedArrays();
                return;
            }

            for (int word = 0; word < origins.bits.length; word++) {
                long bits = origins.bits[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int arrayIndex = (word << 6) + bit;
                    requireInitialized(arrayIndex, site);
                    state.lifecycle[arrayIndex] =
                            DynamicArrayAnalysisState.INITED;
                    state.freed[arrayIndex] =
                            DynamicArrayAnalysisState.FREED_NEVER;
                    advanceEpoch(arrayIndex, true);
                    bits &= bits - 1;
                }
            }
        }

        private void applyUninit(
                SemanticExprNode receiver,
                SemanticASTNode site
        ) {
            ArrayOrigins origins = storageArrayOrigins(receiver);

            if (origins.unknown || origins.count() == 0) {
                havocEscapedArrays();
                return;
            }

            for (int word = 0; word < origins.bits.length; word++) {
                long bits = origins.bits[word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int arrayIndex = (word << 6) + bit;
                    applyUninitToArray(arrayIndex, site);
                    bits &= bits - 1;
                }
            }
        }

        private void applyUninitToArray(int arrayIndex, SemanticASTNode site) {
            byte lifecycle = state.lifecycle[arrayIndex];

            if (lifecycle == DynamicArrayAnalysisState.INITED) {
                state.lifecycle[arrayIndex] =
                        DynamicArrayAnalysisState.UNINITED;
                state.freed[arrayIndex] =
                        DynamicArrayAnalysisState.FREED_YES;
                advanceEpoch(arrayIndex, true);
                return;
            }

            if (lifecycle == DynamicArrayAnalysisState.MAYBE_INITED) {
                reportWarningOnce(
                        site,
                        "Possible invalid free or double free of dynamic array '"
                                + arrayName(arrayIndex)
                                + "'"
                );
                state.lifecycle[arrayIndex] =
                        DynamicArrayAnalysisState.UNINITED;
                state.freed[arrayIndex] =
                        DynamicArrayAnalysisState.FREED_YES;
                advanceEpoch(arrayIndex, true);
                return;
            }

            if (state.freed[arrayIndex]
                    == DynamicArrayAnalysisState.FREED_YES) {
                reportErrorOnce(
                        site,
                        "Double free of dynamic array '"
                                + arrayName(arrayIndex)
                                + "' (uninit called twice)"
                );
            } else if (state.freed[arrayIndex]
                    == DynamicArrayAnalysisState.FREED_NEVER) {
                reportErrorOnce(
                        site,
                        "Invalid free of dynamic array '"
                                + arrayName(arrayIndex)
                                + "' (uninit before init)"
                );
            } else {
                reportWarningOnce(
                        site,
                        "Possible invalid free or double free of dynamic array '"
                                + arrayName(arrayIndex)
                                + "'"
                );
            }

            state.lifecycle[arrayIndex] =
                    DynamicArrayAnalysisState.UNINITED;
            state.freed[arrayIndex] =
                    DynamicArrayAnalysisState.FREED_YES;
            advanceEpoch(arrayIndex, true);
        }

        private void requireInitialized(
                int arrayIndex,
                SemanticASTNode site
        ) {
            byte lifecycle = state.lifecycle[arrayIndex];

            if (lifecycle == DynamicArrayAnalysisState.INITED) {
                return;
            }

            if (lifecycle == DynamicArrayAnalysisState.MAYBE_INITED) {
                reportWarningOnce(
                        site,
                        "Possible use of uninitialized dynamic array '"
                                + arrayName(arrayIndex)
                                + "'"
                );
                return;
            }

            if (state.freed[arrayIndex]
                    == DynamicArrayAnalysisState.FREED_YES) {
                reportErrorOnce(
                        site,
                        "Use of dynamic array '"
                                + arrayName(arrayIndex)
                                + "' after uninit"
                );
            } else if (state.freed[arrayIndex]
                    == DynamicArrayAnalysisState.FREED_NEVER) {
                reportErrorOnce(
                        site,
                        "Use of uninitialized dynamic array '"
                                + arrayName(arrayIndex)
                                + "' before init"
                );
            } else {
                reportWarningOnce(
                        site,
                        "Dynamic array '"
                                + arrayName(arrayIndex)
                                + "' may be uninitialized or freed"
                );
            }
        }

        private void advanceEpoch(int arrayIndex, boolean must) {
            if (must) {
                state.epochMin[arrayIndex]++;
                state.epochMax[arrayIndex]++;
            } else {
                state.epochMax[arrayIndex]++;
            }
        }

        private void reportLeakIfNeeded(int arrayIndex, String where) {
            if (state.escaped[arrayIndex]) {
                return;
            }

            if (state.lifecycle[arrayIndex]
                    == DynamicArrayAnalysisState.INITED) {
                diagnostics.warning(
                        INTRA
                        + "Dynamic array '"
                        + arrayName(arrayIndex)
                        + "' is still initialized at "
                        + where
                        + " (missing explicit uninit)"
                );
            } else if (state.lifecycle[arrayIndex]
                    == DynamicArrayAnalysisState.MAYBE_INITED) {
                diagnostics.warning(
                        INTRA
                        + "Dynamic array '"
                        + arrayName(arrayIndex)
                        + "' may still be initialized at "
                        + where
                        + " (missing explicit uninit)"
                );
            }
        }

        private void havocEscapedArrays() {
            for (int i = 0; i < state.arrayCount; i++) {
                if (state.escaped[i]) {
                    state.lifecycle[i] =
                            DynamicArrayAnalysisState.MAYBE_INITED;
                    state.freed[i] =
                            DynamicArrayAnalysisState.FREED_MAYBE;
                }
            }
        }

        private void reportErrorOnce(SemanticASTNode site, String message) {
            if (suppressed) {
                return;
            }

            int siteId = context.siteId(site);

            if (siteId < 0 || addBit(reportedErrorSites, siteId)) {
                diagnostics.error(INTRA + message);
            }
        }

        private void reportWarningOnce(SemanticASTNode site, String message) {
            if (suppressed) {
                return;
            }

            int siteId = context.siteId(site);

            if (siteId < 0 || addBit(reportedWarningSites, siteId)) {
                diagnostics.warning(INTRA + message);
            }
        }

        private void reportInterErrorOnce(
                SemanticASTNode site,
                String message
        ) {
            if (suppressed) {
                return;
            }

            int siteId = context.siteId(site);

            if (siteId < 0 || addBit(reportedErrorSites, siteId)) {
                diagnostics.error(INTER + message);
            }
        }

        private void reportInterWarningOnce(
                SemanticASTNode site,
                String message
        ) {
            if (suppressed) {
                return;
            }

            int siteId = context.siteId(site);

            if (siteId < 0 || addBit(reportedWarningSites, siteId)) {
                diagnostics.warning(INTER + message);
            }
        }

        @Override
        public void onReturn(SemanticReturnNode node) {
            if (summaryMode) {
                exitReturnSets.add(
                        node.value == null
                                ? context.templates.newSet()
                                : symbolicAliasOf(node.value)
                );
                return;
            }

            if (!suppressed) {
                for (int i = 0; i < state.arrayCount; i++) {
                    reportLeakIfNeeded(
                            i,
                            "return from function '"
                                    + functionName(node.function)
                                    + "'"
                    );
                }

                if (node.value != null) {
                    checkAliasUse(node.value, node);
                }
            }
        }

        @Override
        public void onFallthrough() {
            if (summaryMode) {
                long[] set = context.templates.newSet();
                context.templates.set(set, MemoryTemplate.UNKNOWN);
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

            LengthGuard guard = lengthGuard(condition);

            if (guard == null) {
                return true;
            }

            LengthAssertion assertion = guard.assertion(guardHolds);

            if (summaryMode) {
                int param = summaryOwnerParam(guard.receiver);

                if (param >= 0) {
                    byte post = state.paramPost[param];

                    return switch (assertion) {
                        case NONE -> true;
                        case ZERO -> post
                                != DynamicArrayAnalysisState.POST_INITED;
                        case NONZERO -> post
                                != DynamicArrayAnalysisState.POST_UNINITED;
                    };
                }

                int arrayIndex = summaryLocalArray(guard.receiver);

                if (arrayIndex >= 0) {
                    return lifecycleFeasible(arrayIndex, assertion);
                }

                return true;
            }

            ArrayOrigins origins = storageArrayOrigins(guard.receiver);

            if (origins.unknown || origins.count() != 1) {
                return true;
            }

            return lifecycleFeasible(origins.first(), assertion);
        }

        private boolean lifecycleFeasible(
                int arrayIndex,
                LengthAssertion assertion
        ) {
            return switch (assertion) {
                case NONE -> true;
                case ZERO -> state.lifecycle[arrayIndex]
                        != DynamicArrayAnalysisState.INITED;
                case NONZERO -> state.lifecycle[arrayIndex]
                        != DynamicArrayAnalysisState.UNINITED;
            };
        }

        @Override
        public void refineFromGuard(
                SemanticExprNode condition,
                boolean guardHolds
        ) {
            LengthGuard guard = lengthGuard(condition);

            if (guard == null) {
                return;
            }

            LengthAssertion assertion = guard.assertion(guardHolds);

            if (summaryMode) {
                int param = summaryOwnerParam(guard.receiver);

                if (param >= 0) {
                    if (assertion == LengthAssertion.ZERO) {
                        state.paramPost[param] =
                                DynamicArrayAnalysisState.POST_UNINITED;
                    } else if (assertion == LengthAssertion.NONZERO) {
                        state.paramPost[param] =
                                DynamicArrayAnalysisState.POST_INITED;
                    }

                    return;
                }

                int arrayIndex = summaryLocalArray(guard.receiver);

                if (arrayIndex >= 0) {
                    refineLocalArray(arrayIndex, assertion);
                }

                return;
            }

            ArrayOrigins origins = storageArrayOrigins(guard.receiver);

            if (origins.unknown || origins.count() != 1) {
                return;
            }

            refineLocalArray(origins.first(), assertion);
        }

        private void refineLocalArray(
                int arrayIndex,
                LengthAssertion assertion
        ) {
            if (assertion == LengthAssertion.ZERO) {
                if (state.lifecycle[arrayIndex]
                        != DynamicArrayAnalysisState.UNINITED) {
                    state.lifecycle[arrayIndex] =
                            DynamicArrayAnalysisState.UNINITED;
                    state.freed[arrayIndex] =
                            DynamicArrayAnalysisState.FREED_MAYBE;
                }
            } else if (assertion == LengthAssertion.NONZERO) {
                state.lifecycle[arrayIndex] =
                        DynamicArrayAnalysisState.INITED;
                state.freed[arrayIndex] =
                        DynamicArrayAnalysisState.FREED_NEVER;
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

                long[] alias = symbolicAliasOf(argument);
                int offset = builder.offset(edgeIndex, param);

                for (int i = 0; i < context.templates.words; i++) {
                    builder.edgeAliasSets[offset + i] |= alias[i];
                }
            }
        }

        private void handleCheckCall(SemanticFunctionCallNode call) {
            for (SemanticExprNode argument : call.args) {
                checkAliasUse(argument, call);
            }

            int callSiteId = context.callSiteId(call);
            int calleeId = context.callGraph.callSiteCallee[callSiteId];
            int paramCount = call.function.parameters.size();
            int[] rootBinding = new int[
                    paramCount * DynamicArrayAnalysisState.BINDING_STRIDE];

            for (int i = 0; i < paramCount; i++) {
                SemanticExprNode argument = call.args.get(i);
                AliasValue value = concreteAliasOf(argument);
                int offset = i * DynamicArrayAnalysisState.BINDING_STRIDE;
                rootBinding[offset + DynamicArrayAnalysisState.BINDING_ARRAY] =
                        value.arrayIndex;
                rootBinding[offset + DynamicArrayAnalysisState.BINDING_LIFECYCLE] =
                        value.lifecycle;
                rootBinding[offset + DynamicArrayAnalysisState.BINDING_FREED] =
                        value.freed;
                rootBinding[
                        offset + DynamicArrayAnalysisState.BINDING_ALIAS_ROOT] =
                        value.aliasRoot;
                rootBinding[
                        offset + DynamicArrayAnalysisState.BINDING_ALIAS_EPOCH] =
                        value.aliasEpoch;
                rootBinding[offset + DynamicArrayAnalysisState.BINDING_EPOCH] =
                        value.epoch;
                rootBinding[
                        offset + DynamicArrayAnalysisState.BINDING_ROOT_ARG] = i;
            }

            MemoryBfsResult result = bfs.resolve(
                    callSiteId,
                    calleeId,
                    rootBinding,
                    node -> processNode(node)
            );

            currentBfsResult = result;

            if (result.containsTop()) {
                callResultAliases[callSiteId] = AliasValue.UNKNOWN;
                markOwnerArgumentsMaybeInited(call);
                invalidateGlobalOwners();
                currentBfsResult = null;
                return;
            }

            for (int node = 0; node < result.nodeCount(); node++) {
                int functionId = result.functionId(node);
                DynamicSummary summary = summaries[functionId];

                if (summary == null) {
                    continue;
                }

                for (int param = 0; param < summary.maxParams; param++) {
                    int offset = param * DynamicArrayAnalysisState.BINDING_STRIDE;
                    int arrayIndex = result.bindingAt(
                            node,
                            offset + DynamicArrayAnalysisState.BINDING_ARRAY
                    );
                    int rootArg = result.bindingAt(
                            node,
                            offset + DynamicArrayAnalysisState.BINDING_ROOT_ARG
                    );

                    if (arrayIndex >= 0) {
                        emitRequirementDiagnostics(
                                call,
                                summary,
                                param,
                                arrayIndex,
                                rootArg,
                                result,
                                node
                        );
                        applyCallEffects(
                                summary,
                                param,
                                arrayIndex,
                                result,
                                node
                        );
                    } else {
                        applyUnknownCallEffects(summary, param);
                    }
                }

                emitCrossParameterWarnings(call, summary, result, node);
            }

            AliasValue returnAlias = resolveCallReturnAlias(result, callSiteId);
            callResultAliases[callSiteId] = returnAlias;

            if (returnAlias.arrayIndex >= 0) {
                state.escaped[returnAlias.arrayIndex] = true;
            }

            invalidateGlobalOwners();
            currentBfsResult = null;
        }

        private void invalidateGlobalOwners() {
            for (int globalIndex = 0;
                    globalIndex < context.globalVariableIds.length;
                    globalIndex++) {
                int variableId = context.globalVariableIds[globalIndex];
                int ownerIndex = state.ownerIndexOfVariable(variableId);
                int pointerIndex = state.pointerIndexOfVariable(variableId);

                if (ownerIndex >= 0) {
                    state.clearOwner(ownerIndex);
                    state.setOwnerUnknown(ownerIndex);
                }

                if (pointerIndex >= 0) {
                    state.clearAlias(pointerIndex);
                }
            }
        }

        private void emitRequirementDiagnostics(
                SemanticFunctionCallNode call,
                DynamicSummary summary,
                int param,
                int arrayIndex,
                int rootArg,
                MemoryBfsResult result,
                int node
        ) {
            byte requirement = summary.paramReq[param];

            if (requirement == REQUIRE_NONE) {
                return;
            }

            int offset = param * DynamicArrayAnalysisState.BINDING_STRIDE;
            int lifecycle = result.bindingAt(
                    node,
                    offset + DynamicArrayAnalysisState.BINDING_LIFECYCLE
            );
            int freed = result.bindingAt(
                    node,
                    offset + DynamicArrayAnalysisState.BINDING_FREED
            );
            String arrayName = arrayName(arrayIndex);
            String functionName = call.function.qualifiedName();
            SemanticASTNode site = call;

            if (lifecycle == DynamicArrayAnalysisState.UNINITED) {
                String message = freed == DynamicArrayAnalysisState.FREED_YES
                        ? "Passing freed dynamic array '" + arrayName
                                + "' to function '" + functionName
                                + "' which requires initialized storage"
                        : "Passing uninitialized dynamic array '" + arrayName
                                + "' to function '" + functionName
                                + "' which requires initialized storage";

                if (requirement == REQUIRE_MUST) {
                    reportInterErrorOnce(site, message);
                } else {
                    reportInterWarningOnce(site, message);
                }
            } else if (lifecycle == DynamicArrayAnalysisState.MAYBE_INITED) {
                reportInterWarningOnce(
                        site,
                        "Passing possibly uninitialized dynamic array '"
                                + arrayName
                                + "' to function '"
                                + functionName
                                + "' which requires initialized storage"
                );
            }
        }

        private void applyCallEffects(
                DynamicSummary summary,
                int param,
                int arrayIndex,
                MemoryBfsResult result,
                int node
        ) {
            byte post = summary.paramPost[param];

            if (post == DynamicArrayAnalysisState.POST_INITED) {
                state.lifecycle[arrayIndex] =
                        DynamicArrayAnalysisState.INITED;
                state.freed[arrayIndex] =
                        DynamicArrayAnalysisState.FREED_NEVER;
            } else if (post == DynamicArrayAnalysisState.POST_UNINITED) {
                state.lifecycle[arrayIndex] =
                        DynamicArrayAnalysisState.UNINITED;
                state.freed[arrayIndex] =
                        DynamicArrayAnalysisState.FREED_YES;
            } else if (post == DynamicArrayAnalysisState.POST_MAYBE
                    || post == DynamicArrayAnalysisState.POST_HAVOC) {
                state.lifecycle[arrayIndex] =
                        DynamicArrayAnalysisState.MAYBE_INITED;
                state.freed[arrayIndex] =
                        DynamicArrayAnalysisState.FREED_MAYBE;
            }

            if (summary.invalidateMust[param] == REQUIRE_MUST) {
                advanceEpoch(arrayIndex, true);
            } else if (summary.invalidateMay[param] != REQUIRE_NONE) {
                advanceEpoch(arrayIndex, false);
            }

            if (summary.freeMust[param] == REQUIRE_MUST) {
                state.lifecycle[arrayIndex] =
                        DynamicArrayAnalysisState.UNINITED;
                state.freed[arrayIndex] =
                        DynamicArrayAnalysisState.FREED_YES;
            } else if (summary.freeMay[param] != REQUIRE_NONE) {
                state.freed[arrayIndex] =
                        DynamicArrayAnalysisState.FREED_MAYBE;
            }

            if (summary.escapeMust[param] != REQUIRE_NONE
                    || summary.escapeMay[param] != REQUIRE_NONE) {
                state.escaped[arrayIndex] = true;
            }
        }

        private void applyUnknownCallEffects(
                DynamicSummary summary,
                int param
        ) {
            if (summary.escapeMust[param] != REQUIRE_NONE
                    || summary.escapeMay[param] != REQUIRE_NONE) {
                havocEscapedArrays();
            }
        }

        private void emitCrossParameterWarnings(
                SemanticFunctionCallNode call,
                DynamicSummary summary,
                MemoryBfsResult result,
                int node
        ) {
            for (int i = 0; i < summary.maxParams; i++) {
                if (summary.invalidateMust[i] == REQUIRE_NONE
                        && summary.invalidateMay[i] == REQUIRE_NONE) {
                    continue;
                }

                int iOffset = i * DynamicArrayAnalysisState.BINDING_STRIDE;
                int arrayIndex = result.bindingAt(
                        node,
                        iOffset + DynamicArrayAnalysisState.BINDING_ARRAY
                );

                if (arrayIndex < 0) {
                    continue;
                }

                for (int j = 0; j < summary.maxParams; j++) {
                    if (summary.useMust[j] == REQUIRE_NONE
                            && summary.useMay[j] == REQUIRE_NONE) {
                        continue;
                    }

                    int jOffset = j * DynamicArrayAnalysisState.BINDING_STRIDE;
                    int aliasRoot = result.bindingAt(
                            node,
                            jOffset
                                    + DynamicArrayAnalysisState.BINDING_ALIAS_ROOT
                    );

                    if (aliasRoot != arrayIndex) {
                        continue;
                    }

                    if (j >= call.args.size()) {
                        continue;
                    }

                    SemanticExprNode argument = call.args.get(j);
                    String pointerName = pointerExpressionName(argument);
                    reportInterWarningOnce(
                            call,
                            "Call to '" + call.function.qualifiedName()
                                    + "' may invalidate array '"
                                    + arrayName(arrayIndex)
                                    + "' while using element pointer '"
                                    + pointerName
                                    + "' into it"
                    );
                    return;
                }
            }
        }

        private void markOwnerArgumentsMaybeInited(
                SemanticFunctionCallNode call
        ) {
            for (SemanticExprNode argument : call.args) {
                AliasValue value = concreteAliasOf(argument);

                if (value.arrayIndex >= 0) {
                    state.lifecycle[value.arrayIndex] =
                            DynamicArrayAnalysisState.MAYBE_INITED;
                    state.freed[value.arrayIndex] =
                            DynamicArrayAnalysisState.FREED_MAYBE;
                    state.escaped[value.arrayIndex] = true;
                }
            }
        }

        private void processNode(int node) {
            int functionId = bfs.functionId(node);
            DynamicSummary summary = summaries[functionId];

            if (summary == null) {
                return;
            }

            for (int edge = 0; edge < summary.edgeCount; edge++) {
                int callSite = summary.edgeCallSiteIds[edge];
                int callee = context.callGraph.callSiteCallee[callSite];
                int calleeParams = context.callGraph.functions[callee]
                        .parameters.size();
                int[] binding = new int[
                        calleeParams * DynamicArrayAnalysisState.BINDING_STRIDE];

                for (int param = 0; param < calleeParams; param++) {
                    int offset = param
                            * DynamicArrayAnalysisState.BINDING_STRIDE;
                    int aliasOffset = (edge * summary.maxParams + param)
                            * summary.templateWords;
                    AliasValue value = evalAliasSet(
                            summary.edgeAliasSets,
                            aliasOffset,
                            summary.templateWords,
                            null,
                            node
                    );

                    binding[offset + DynamicArrayAnalysisState.BINDING_ARRAY] =
                            value.arrayIndex;
                    binding[offset
                            + DynamicArrayAnalysisState.BINDING_LIFECYCLE] =
                            value.lifecycle;
                    binding[offset + DynamicArrayAnalysisState.BINDING_FREED] =
                            value.freed;
                    binding[offset
                            + DynamicArrayAnalysisState.BINDING_ALIAS_ROOT] =
                            value.aliasRoot;
                    binding[offset
                            + DynamicArrayAnalysisState.BINDING_ALIAS_EPOCH] =
                            value.aliasEpoch;
                    binding[offset + DynamicArrayAnalysisState.BINDING_EPOCH] =
                            value.epoch;
                    binding[offset
                            + DynamicArrayAnalysisState.BINDING_ROOT_ARG] =
                            computeRootArg(summary, edge, param, node);
                }

                bfs.enqueue(functionId, callee, binding, callSite);
            }
        }

        private int computeRootArg(
                DynamicSummary summary,
                int edge,
                int param,
                int node
        ) {
            int offset = (edge * summary.maxParams + param)
                    * summary.templateWords;
            int first = firstTemplate(
                    summary.edgeAliasSets,
                    offset,
                    summary.templateWords
            );
            int count = countTemplates(
                    summary.edgeAliasSets,
                    offset,
                    summary.templateWords
            );

            if (count != 1 || first < 0) {
                return -1;
            }

            int paramIndex = context.templates.paramOfAlias(first);

            if (paramIndex < 0) {
                paramIndex = context.templates.paramOfWholeArray(first);
            }

            if (paramIndex < 0) {
                paramIndex = context.templates.paramOfElement(first);
            }

            if (paramIndex < 0) {
                return -1;
            }

            return bfs.bindingAt(
                    node,
                    paramIndex * DynamicArrayAnalysisState.BINDING_STRIDE
                            + DynamicArrayAnalysisState.BINDING_ROOT_ARG
            );
        }

        private AliasValue resolveCallReturnAlias(
                MemoryBfsResult result,
                int callSiteId
        ) {
            if (callSiteId < 0 || callSiteId >= callResultAliases.length) {
                return AliasValue.UNKNOWN;
            }

            if (callResultAliases[callSiteId] != null) {
                return callResultAliases[callSiteId];
            }

            if (result.isCallSiteTop(callSiteId)) {
                callResultAliases[callSiteId] = AliasValue.UNKNOWN;
                return AliasValue.UNKNOWN;
            }

            int node = result.callSiteNodeIndex(callSiteId);

            if (node < 0) {
                callResultAliases[callSiteId] = AliasValue.UNKNOWN;
                return AliasValue.UNKNOWN;
            }

            if (callResultResolving[callSiteId]) {
                return AliasValue.UNKNOWN;
            }

            int functionId = result.functionId(node);
            DynamicSummary summary = summaries[functionId];

            if (summary == null) {
                callResultAliases[callSiteId] = AliasValue.UNKNOWN;
                return AliasValue.UNKNOWN;
            }

            callResultResolving[callSiteId] = true;
            AliasValue value = evalAliasSet(
                    summary.returnAliasSet,
                    0,
                    summary.templateWords,
                    result,
                    node
            );
            callResultResolving[callSiteId] = false;
            callResultAliases[callSiteId] = value;
            return value;
        }

        private AliasValue evalAliasSet(
                long[] sets,
                int offset,
                int words,
                MemoryBfsResult result,
                int node
        ) {
            AliasValue merged = null;

            for (int word = 0; word < words; word++) {
                long bits = sets[offset + word];

                while (bits != 0L) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    int template = (word << 6) + bit;
                    AliasValue value = evalAliasTemplate(
                            template,
                            result,
                            node
                    );
                    merged = mergeAlias(merged, value);
                    bits &= bits - 1;
                }
            }

            return merged == null ? AliasValue.NONE : merged;
        }

        private AliasValue evalAliasTemplate(
                int template,
                MemoryBfsResult result,
                int node
        ) {
            if (template == MemoryTemplate.NULL) {
                return AliasValue.NONE;
            }

            if (template == MemoryTemplate.UNKNOWN
                    || template == MemoryTemplate.LOCAL) {
                return AliasValue.UNKNOWN;
            }

            if (template == MemoryTemplate.NONNULL
                    || template == MemoryTemplate.CONST) {
                return AliasValue.UNKNOWN;
            }

            int aliasParam = context.templates.paramOfAlias(template);

            if (aliasParam >= 0) {
                int offset = aliasParam
                        * DynamicArrayAnalysisState.BINDING_STRIDE;
                int arrayIndex = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_ARRAY
                );
                int lifecycle = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_LIFECYCLE
                );
                int freed = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_FREED
                );
                int epoch = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_EPOCH
                );

                return arrayIndex < 0
                        ? AliasValue.UNKNOWN
                        : new AliasValue(
                                arrayIndex,
                                lifecycle,
                                freed,
                                DynamicArrayAnalysisState.ALIAS_NONE,
                                0,
                                epoch,
                                false,
                                false
                        );
            }

            int wholeParam = context.templates.paramOfWholeArray(template);

            if (wholeParam >= 0) {
                int offset = wholeParam
                        * DynamicArrayAnalysisState.BINDING_STRIDE;
                int arrayIndex = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_ARRAY
                );
                int lifecycle = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_LIFECYCLE
                );
                int freed = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_FREED
                );
                int epoch = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_EPOCH
                );

                return arrayIndex < 0
                        ? AliasValue.UNKNOWN
                        : new AliasValue(
                                arrayIndex,
                                lifecycle,
                                freed,
                                DynamicArrayAnalysisState.ALIAS_NONE,
                                0,
                                epoch,
                                false,
                                false
                        );
            }

            int elementParam = context.templates.paramOfElement(template);

            if (elementParam >= 0) {
                int offset = elementParam
                        * DynamicArrayAnalysisState.BINDING_STRIDE;
                int arrayIndex = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_ARRAY
                );
                int lifecycle = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_LIFECYCLE
                );
                int freed = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_FREED
                );
                int epoch = bfs.bindingAt(
                        node,
                        offset + DynamicArrayAnalysisState.BINDING_EPOCH
                );

                return arrayIndex < 0
                        ? AliasValue.UNKNOWN
                        : new AliasValue(
                                arrayIndex,
                                lifecycle,
                                freed,
                                arrayIndex,
                                epoch,
                                epoch,
                                true,
                                false
                        );
            }

            int callSite = context.templates.callSiteIndexOf(template);

            if (callSite >= 0) {
                MemoryBfsResult effective = result == null
                        ? currentBfsResult
                        : result;

                if (effective == null) {
                    return AliasValue.UNKNOWN;
                }

                return resolveCallReturnAlias(effective, callSite);
            }

            return AliasValue.UNKNOWN;
        }

        private AliasValue mergeAlias(AliasValue left, AliasValue right) {
            if (left == null) {
                return right;
            }

            if (right == null) {
                return left;
            }

            if (left.arrayIndex != right.arrayIndex
                    || left.aliasRoot != right.aliasRoot
                    || left.aliasEpoch != right.aliasEpoch) {
                return AliasValue.UNKNOWN;
            }

            if (left.lifecycle != right.lifecycle
                    || left.freed != right.freed) {
                return new AliasValue(
                        left.arrayIndex,
                        DynamicArrayAnalysisState.MAYBE_INITED,
                        DynamicArrayAnalysisState.FREED_MAYBE,
                        left.aliasRoot,
                        left.aliasEpoch,
                        left.epoch,
                        left.element,
                        left.unknown
                );
            }

            return left;
        }

        private AliasValue concreteAliasOf(SemanticExprNode expression) {
            if (expression == null) {
                return AliasValue.UNKNOWN;
            }

            if (expression instanceof SemanticNullLiteralNode) {
                return AliasValue.NONE;
            }

            if (expression instanceof SemanticVarExprNode variable) {
                int pointerIndex = state.pointerIndexOf(variable.symbol);

                if (pointerIndex >= 0) {
                    int root = state.aliasRoot[pointerIndex];

                    if (root >= 0) {
                        return new AliasValue(
                                root,
                                state.lifecycle[root],
                                state.freed[root],
                                root,
                                state.aliasEpoch[pointerIndex],
                                state.epochMin[root],
                                true,
                                false
                        );
                    }

                    int ownerIndex = state.ownerIndexOf(variable.symbol);

                    if (ownerIndex >= 0) {
                        int arrayIndex = state.singleOwner(ownerIndex);

                        if (arrayIndex >= 0) {
                            return new AliasValue(
                                    arrayIndex,
                                    state.lifecycle[arrayIndex],
                                    state.freed[arrayIndex],
                                    DynamicArrayAnalysisState.ALIAS_NONE,
                                    0,
                                    state.epochMin[arrayIndex],
                                    false,
                                    false
                            );
                        }

                        if (state.ownerUnknown[ownerIndex]) {
                            return AliasValue.UNKNOWN;
                        }
                    }
                }

                int arrayIndex = state.arrayIndexOf(variable.symbol);

                if (arrayIndex >= 0) {
                    return new AliasValue(
                            arrayIndex,
                            state.lifecycle[arrayIndex],
                            state.freed[arrayIndex],
                            DynamicArrayAnalysisState.ALIAS_NONE,
                            0,
                            state.epochMin[arrayIndex],
                            false,
                            false
                    );
                }

                return AliasValue.UNKNOWN;
            }

            if (expression instanceof SemanticGetPtrNode getPtr) {
                int elementArray = elementArrayOf(getPtr.target);

                if (elementArray >= 0) {
                    return new AliasValue(
                            elementArray,
                            state.lifecycle[elementArray],
                            state.freed[elementArray],
                            elementArray,
                            state.epochMin[elementArray],
                            state.epochMin[elementArray],
                            true,
                            false
                    );
                }

                return concreteAliasOf(getPtr.target);
            }

            if (expression instanceof SemanticDerefNode deref) {
                return concreteAliasOf(deref.expr);
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return concreteAliasOf(cast.expr);
            }

            if (expression instanceof SemanticFunctionCallNode call) {
                int callSiteId = context.callSiteId(call);

                if (callSiteId < 0) {
                    return AliasValue.UNKNOWN;
                }

                AliasValue value = callResultAliases[callSiteId];
                return value == null ? AliasValue.UNKNOWN : value;
            }

            return AliasValue.UNKNOWN;
        }

        private int elementArrayOf(SemanticExprNode expression) {
            if (expression instanceof SemanticArrayAccessNode access) {
                ArrayOrigins origins = storageArrayOrigins(access.target);

                if (!origins.unknown && origins.count() == 1) {
                    return origins.first();
                }

                return elementArrayOf(access.target);
            }

            if (expression instanceof SemanticMemberAccessNode member) {
                return elementArrayOf(member.target);
            }

            return -1;
        }

        private ArrayOrigins storageArrayOrigins(SemanticExprNode expression) {
            long[] bits = new long[state.arrayWords];

            if (expression == null) {
                return new ArrayOrigins(bits, true);
            }

            if (expression instanceof SemanticVarExprNode variable) {
                int arrayIndex = state.arrayIndexOf(variable.symbol);

                if (arrayIndex >= 0) {
                    bits[arrayIndex >>> 6] |= 1L << (arrayIndex & 63);
                    return new ArrayOrigins(bits, false);
                }

                int ownerIndex = state.ownerIndexOf(variable.symbol);

                if (ownerIndex >= 0) {
                    int offset = ownerIndex * state.arrayWords;
                    System.arraycopy(
                            state.ownerArrayBits,
                            offset,
                            bits,
                            0,
                            state.arrayWords
                    );
                    return new ArrayOrigins(bits, state.ownerUnknown[ownerIndex]);
                }

                return new ArrayOrigins(bits, true);
            }

            if (expression instanceof SemanticDerefNode deref) {
                return storageArrayOrigins(deref.expr);
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return storageArrayOrigins(cast.expr);
            }

            return new ArrayOrigins(bits, true);
        }

        private long[] symbolicAliasOf(SemanticExprNode expression) {
            long[] set = context.templates.newSet();

            if (expression == null) {
                context.templates.set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression instanceof SemanticNullLiteralNode) {
                context.templates.set(set, MemoryTemplate.NULL);
                return set;
            }

            if (expression instanceof SemanticVarExprNode variable) {
                int pointerIndex = state.pointerIndexOf(variable.symbol);

                if (pointerIndex >= 0) {
                    int offset = pointerIndex * context.templates.words;

                    for (int i = 0; i < context.templates.words; i++) {
                        set[i] = state.symAlias[offset + i];
                    }

                    return set;
                }

                int arrayIndex = state.arrayIndexOf(variable.symbol);

                if (arrayIndex >= 0) {
                    context.templates.set(
                            set,
                            context.templates.localValue(
                                    context.variableId(variable.symbol)
                            )
                    );
                    return set;
                }

                context.templates.set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression instanceof SemanticGetPtrNode getPtr) {
                if (getPtr.target instanceof SemanticArrayAccessNode
                        || getPtr.target instanceof SemanticMemberAccessNode) {
                    long[] base = symbolicElementArrayOf(getPtr.target);

                    for (int word = 0; word < context.templates.words; word++) {
                        long bits = base[word];

                        while (bits != 0L) {
                            int bit = Long.numberOfTrailingZeros(bits);
                            int template = (word << 6) + bit;
                            int param = context.templates.paramOfAlias(template);

                            if (param >= 0) {
                                context.templates.set(
                                        set,
                                        context.templates.elementOf(param)
                                );
                            } else {
                                int variableId =
                                        context.templates.variableOfLocal(template);

                                if (variableId >= 0) {
                                    context.templates.set(
                                            set,
                                            context.templates.localElement(
                                                    variableId
                                            )
                                    );
                                } else {
                                    context.templates.set(
                                            set,
                                            MemoryTemplate.UNKNOWN
                                    );
                                }
                            }

                            bits &= bits - 1;
                        }
                    }

                    return set;
                }

                if (getPtr.target instanceof SemanticDerefNode deref) {
                    return symbolicAliasOf(deref.expr);
                }

                if (getPtr.target instanceof SemanticCastExprNode cast) {
                    return symbolicAliasOf(cast.expr);
                }

                return symbolicAliasOf(getPtr.target);
            }

            if (expression instanceof SemanticDerefNode deref) {
                return symbolicAliasOf(deref.expr);
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return symbolicAliasOf(cast.expr);
            }

            if (expression instanceof SemanticFunctionCallNode call) {
                int callSiteId = context.callSiteId(call);
                context.templates.set(
                        set,
                        context.templates.callResult(callSiteId)
                );
                return set;
            }

            context.templates.set(set, MemoryTemplate.UNKNOWN);
            return set;
        }

        private long[] symbolicElementArrayOf(SemanticExprNode expression) {
            if (expression instanceof SemanticArrayAccessNode access) {
                return symbolicArrayOf(access.target);
            }

            if (expression instanceof SemanticMemberAccessNode member) {
                return symbolicElementArrayOf(member.target);
            }

            return context.templates.newSet();
        }

        private long[] symbolicArrayOf(SemanticExprNode expression) {
            long[] set = context.templates.newSet();

            if (expression == null) {
                context.templates.set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression instanceof SemanticVarExprNode variable) {
                int arrayIndex = state.arrayIndexOf(variable.symbol);

                if (arrayIndex >= 0) {
                    context.templates.set(
                            set,
                            context.templates.localValue(
                                    context.variableId(variable.symbol)
                            )
                    );
                    return set;
                }

                int pointerIndex = state.pointerIndexOf(variable.symbol);

                if (pointerIndex >= 0) {
                    int offset = pointerIndex * context.templates.words;

                    for (int i = 0; i < context.templates.words; i++) {
                        set[i] = state.symAlias[offset + i];
                    }

                    return set;
                }

                context.templates.set(set, MemoryTemplate.UNKNOWN);
                return set;
            }

            if (expression instanceof SemanticDerefNode deref) {
                return symbolicArrayOf(deref.expr);
            }

            if (expression instanceof SemanticCastExprNode cast) {
                return symbolicArrayOf(cast.expr);
            }

            context.templates.set(set, MemoryTemplate.UNKNOWN);
            return set;
        }

        private LengthGuard lengthGuard(SemanticExprNode condition) {
            if (condition == null) {
                return null;
            }

            if (condition instanceof SemanticArrayIntrinsicCallNode call
                    && call.kind == SemanticArrayIntrinsicKind.LENGTH) {
                return new LengthGuard(
                        call.receiver,
                        "!=",
                        BigInteger.ZERO
                );
            }

            if (condition instanceof SemanticUnaryOpNode unary
                    && unary.op.equals("!")) {
                LengthGuard inner = lengthGuard(unary.expr);

                if (inner == null) {
                    return null;
                }

                return new LengthGuard(
                        inner.receiver,
                        negate(inner.op),
                        inner.constant
                );
            }

            if (!(condition instanceof SemanticBinOpNode binary)
                    || !isComparison(binary.op)) {
                return null;
            }

            boolean leftLength = isLengthCall(binary.left);
            boolean rightLength = isLengthCall(binary.right);

            if (leftLength == rightLength) {
                return null;
            }

            SemanticExprNode lengthSide = leftLength
                    ? binary.left
                    : binary.right;
            SemanticExprNode constantSide = leftLength
                    ? binary.right
                    : binary.left;

            if (constantSide.constantValue == null) {
                return null;
            }

            String op = leftLength ? binary.op : reverse(binary.op);
            SemanticExprNode receiver =
                    ((SemanticArrayIntrinsicCallNode) lengthSide).receiver;

            return new LengthGuard(receiver, op, constantSide.constantValue);
        }

        private boolean isLengthCall(SemanticExprNode expression) {
            return expression instanceof SemanticArrayIntrinsicCallNode call
                    && call.kind == SemanticArrayIntrinsicKind.LENGTH;
        }

        private boolean isComparison(String op) {
            return op.equals("==")
                    || op.equals("!=")
                    || op.equals("<")
                    || op.equals("<=")
                    || op.equals(">")
                    || op.equals(">=");
        }

        private String negate(String op) {
            return switch (op) {
                case "==" -> "!=";
                case "!=" -> "==";
                case "<" -> ">=";
                case "<=" -> ">";
                case ">" -> "<=";
                case ">=" -> "<";
                default -> op;
            };
        }

        private String reverse(String op) {
            return switch (op) {
                case "<" -> ">";
                case "<=" -> ">=";
                case ">" -> "<";
                case ">=" -> "<=";
                default -> op;
            };
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

        private String arrayName(int arrayIndex) {
            int variableId = state.arrayVariableIds[arrayIndex];
            VariableSymbol symbol = context.variable(variableId);
            return symbol == null ? "<array>" : symbol.qualifiedName();
        }

        private String pointerName(int pointerIndex) {
            int variableId = state.pointerVariableIds[pointerIndex];
            VariableSymbol symbol = context.variable(variableId);
            return symbol == null ? "<pointer>" : symbol.qualifiedName();
        }

        private String pointerExpressionName(SemanticExprNode expression) {
            if (expression instanceof SemanticVarExprNode variable) {
                return variable.symbol.qualifiedName();
            }

            return "<expression>";
        }

        private String functionName(FunctionSymbol function) {
            return function == null ? "<unknown>" : function.qualifiedName();
        }
    }

    private static boolean bitAt(long[] bits, int offset, int index) {
        return (bits[offset + (index >>> 6)] & (1L << (index & 63))) != 0L;
    }

    private static final class LengthGuard {        final SemanticExprNode receiver;
        final String op;
        final BigInteger constant;

        LengthGuard(
                SemanticExprNode receiver,
                String op,
                BigInteger constant
        ) {
            this.receiver = receiver;
            this.op = op;
            this.constant = constant;
        }

        LengthAssertion assertion(boolean holds) {
            String effective = holds ? op : negate(op);
            int sign = constant.signum();

            return switch (effective) {
                case "==" -> sign == 0
                        ? LengthAssertion.ZERO
                        : sign > 0
                                ? LengthAssertion.NONZERO
                                : LengthAssertion.NONE;
                case "!=" -> sign == 0
                        ? LengthAssertion.NONZERO
                        : LengthAssertion.NONE;
                case "<" -> constant.equals(BigInteger.ONE)
                        ? LengthAssertion.ZERO
                        : LengthAssertion.NONE;
                case "<=" -> sign == 0
                        ? LengthAssertion.ZERO
                        : LengthAssertion.NONE;
                case ">" -> sign >= 0
                        ? LengthAssertion.NONZERO
                        : LengthAssertion.NONE;
                case ">=" -> constant.compareTo(BigInteger.ONE) >= 0
                        ? LengthAssertion.NONZERO
                        : LengthAssertion.NONE;
                default -> LengthAssertion.NONE;
            };
        }

        private static String negate(String op) {
            return switch (op) {
                case "==" -> "!=";
                case "!=" -> "==";
                case "<" -> ">=";
                case "<=" -> ">";
                case ">" -> "<=";
                case ">=" -> "<";
                default -> op;
            };
        }
    }

    private enum LengthAssertion {
        NONE,
        ZERO,
        NONZERO
    }
}
