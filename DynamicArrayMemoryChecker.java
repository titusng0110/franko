import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DynamicArrayMemoryChecker {
    private static final int MAX_BRANCH_DEPTH = 12;

    private enum ArrayState {
        UNINITED,
        INITED,
        MAYBE_INITED
    }

    private enum FreedState {
        NEVER,
        MAYBE,
        YES
    }

    private enum LengthAssertion {
        NONE,
        ZERO,
        NONZERO
    }

    private static final class ArrayOrigin {
        static final ArrayOrigin EMPTY =
                new ArrayOrigin(Set.of(), false);

        static final ArrayOrigin UNKNOWN =
                new ArrayOrigin(Set.of(), true);

        final Set<VariableSymbol> arrays;
        final boolean unknown;

        private ArrayOrigin(
                Set<VariableSymbol> arrays,
                boolean unknown
        ) {
            this.arrays = arrays;
            this.unknown = unknown;
        }

        static ArrayOrigin of(VariableSymbol symbol) {
            return new ArrayOrigin(Set.of(symbol), false);
        }

        ArrayOrigin union(ArrayOrigin other) {
            if (other == null) {
                other = UNKNOWN;
            }

            if (this == other) {
                return this;
            }

            Set<VariableSymbol> merged = new LinkedHashSet<>(arrays);
            merged.addAll(other.arrays);

            return new ArrayOrigin(
                    Set.copyOf(merged),
                    unknown || other.unknown
            );
        }
    }

    private static final class LengthGuard {
        final SemanticExprNode receiver;
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
    }

    private static final class LoopFrame {
        final List<Integer> breakStates = new ArrayList<>();
        final List<Integer> continueStates = new ArrayList<>();
    }

    private final DiagnosticBag diagnostics;

    private final IdentityHashMap<VariableSymbol, Integer> arrayIndexBySymbol =
            new IdentityHashMap<>();

    private VariableSymbol[] arraySymbols = new VariableSymbol[0];
    private int arrayCount = 0;

    private final IdentityHashMap<VariableSymbol, Integer> pointerIndexBySymbol =
            new IdentityHashMap<>();

    private VariableSymbol[] pointerSymbols = new VariableSymbol[0];
    private int pointerCount = 0;

    private ArrayState[] arrayState = new ArrayState[0];
    private FreedState[] freedState = new FreedState[0];
    private boolean[] escaped = new boolean[0];

    private ArrayOrigin[] pointerState = new ArrayOrigin[0];

    private ArrayState[][] arraySnapshots = new ArrayState[0][];
    private FreedState[][] freedSnapshots = new FreedState[0][];
    private ArrayOrigin[][] pointerSnapshots = new ArrayOrigin[0][];

    private int snapshotCount = 0;
    private int branchDepth = 0;

    private final List<LoopFrame> loopStack = new ArrayList<>();

    private final Set<VariableSymbol> addressTakenPointers =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<SemanticASTNode> reportedErrorSites =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<SemanticASTNode> reportedWarningSites =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private boolean suppressLifecycleChecks = false;

    private FunctionSymbol currentFunction = null;

    public DynamicArrayMemoryChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public void check(SemanticASTNode node) {
        diagnostics.clear();

        prepareAnalysis(node);

        branchDepth = 0;
        snapshotCount = 0;
        loopStack.clear();
        addressTakenPointers.clear();
        reportedErrorSites.clear();
        reportedWarningSites.clear();
        suppressLifecycleChecks = false;
        currentFunction = null;

        Arrays.fill(arrayState, ArrayState.UNINITED);
        Arrays.fill(freedState, FreedState.NEVER);
        Arrays.fill(escaped, false);
        Arrays.fill(pointerState, ArrayOrigin.UNKNOWN);

        checkNode(node);
    }

    public PassResult check(SastDocument document) {
        check(document.root);
        return new PassResult(
                document.withPhase(SastPhase.DYN_ARRAY),
                diagnostics.diagnostics()
        );
    }

    private void prepareAnalysis(SemanticASTNode root) {
        arrayIndexBySymbol.clear();
        arraySymbols = new VariableSymbol[16];
        arrayCount = 0;

        pointerIndexBySymbol.clear();
        pointerSymbols = new VariableSymbol[16];
        pointerCount = 0;

        collectSymbols(root);

        arraySymbols = Arrays.copyOf(arraySymbols, arrayCount);
        pointerSymbols = Arrays.copyOf(pointerSymbols, pointerCount);

        arrayState = new ArrayState[arrayCount];
        freedState = new FreedState[arrayCount];
        escaped = new boolean[arrayCount];

        Arrays.fill(arrayState, ArrayState.UNINITED);
        Arrays.fill(freedState, FreedState.NEVER);

        pointerState = new ArrayOrigin[pointerCount];
        Arrays.fill(pointerState, ArrayOrigin.UNKNOWN);

        int estimatedSnapshots = Math.max(
                2 * MAX_BRANCH_DEPTH,
                estimateSnapshotDemand(root, 0) + MAX_BRANCH_DEPTH
        );

        arraySnapshots = new ArrayState[estimatedSnapshots][arrayCount];
        freedSnapshots = new FreedState[estimatedSnapshots][arrayCount];
        pointerSnapshots = new ArrayOrigin[estimatedSnapshots][pointerCount];

        for (int i = 0; i < estimatedSnapshots; i++) {
            Arrays.fill(arraySnapshots[i], ArrayState.UNINITED);
            Arrays.fill(freedSnapshots[i], FreedState.NEVER);
            Arrays.fill(pointerSnapshots[i], ArrayOrigin.UNKNOWN);
        }
    }

    private void collectSymbols(SemanticASTNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticProgramNode n) {
            for (SemanticASTNode item : n.topLevelItems) {
                collectSymbols(item);
            }
            return;
        }

        if (node instanceof SemanticNamespaceNode n) {
            for (SemanticASTNode item : n.items) {
                collectSymbols(item);
            }
            return;
        }

        if (node instanceof SemanticFunctionDeclNode n) {
            for (VariableSymbol parameter : n.parameterVariables) {
                addPointerSymbolIfNeeded(parameter);
            }

            collectSymbols(n.body);
            return;
        }

        if (node instanceof SemanticStructDeclNode n) {
            for (SemanticFunctionDeclNode method : n.methods) {
                collectSymbols(method);
            }

            return;
        }

        if (node instanceof SemanticBlockNode n) {
            for (SemanticStmtNode stmt : n.statements) {
                collectSymbols(stmt);
            }
            return;
        }

        if (node instanceof SemanticVarDeclNode n) {
            addArraySymbolIfNeeded(n.symbol);
            addPointerSymbolIfNeeded(n.symbol);
            collectSymbolsFromExpr(n.staticInitializer);
            return;
        }

        if (node instanceof SemanticAssignNode n) {
            collectSymbolsFromExpr(n.target);
            collectSymbolsFromExpr(n.value);
            return;
        }

        if (node instanceof SemanticIfNode n) {
            collectSymbolsFromExpr(n.condition);
            collectSymbols(n.thenBranch);
            collectSymbols(n.elseBranch);
            return;
        }

        if (node instanceof SemanticWhileNode n) {
            collectSymbolsFromExpr(n.condition);
            collectSymbols(n.body);

            for (SemanticStmtNode updateStmt : n.update) {
                collectSymbols(updateStmt);
            }
            return;
        }

        if (node instanceof SemanticReturnNode n) {
            collectSymbolsFromExpr(n.value);
            return;
        }

        if (node instanceof SemanticExprStmtNode n) {
            collectSymbolsFromExpr(n.expr);
            return;
        }

        if (node instanceof SemanticExprNode expr) {
            collectSymbolsFromExpr(expr);
        }
    }

    private void collectSymbolsFromExpr(SemanticExprNode expr) {
        if (expr == null) {
            return;
        }

        if (expr instanceof SemanticIntLiteralNode
                || expr instanceof SemanticNullLiteralNode) {
            return;
        }

        if (expr instanceof SemanticVarExprNode n) {
            addArraySymbolIfNeeded(n.symbol);
            addPointerSymbolIfNeeded(n.symbol);
            return;
        }

        if (expr instanceof SemanticUnaryOpNode n) {
            collectSymbolsFromExpr(n.expr);
            return;
        }

        if (expr instanceof SemanticBinOpNode n) {
            collectSymbolsFromExpr(n.left);
            collectSymbolsFromExpr(n.right);
            return;
        }

        if (expr instanceof SemanticArrayAccessNode n) {
            collectSymbolsFromExpr(n.target);
            collectSymbolsFromExpr(n.index);
            return;
        }

        if (expr instanceof SemanticMemberAccessNode n) {
            collectSymbolsFromExpr(n.target);
            return;
        }

        if (expr instanceof SemanticFunctionCallNode n) {
            for (SemanticExprNode arg : n.args) {
                collectSymbolsFromExpr(arg);
            }
            return;
        }

        if (expr instanceof SemanticArrayIntrinsicCallNode n) {
            collectSymbolsFromExpr(n.receiver);

            for (SemanticExprNode arg : n.args) {
                collectSymbolsFromExpr(arg);
            }

            return;
        }

        if (expr instanceof SemanticGetPtrNode n) {
            collectSymbolsFromExpr(n.target);
            return;
        }

        if (expr instanceof SemanticDerefNode n) {
            collectSymbolsFromExpr(n.expr);
            return;
        }

        if (expr instanceof SemanticCastExprNode n) {
            collectSymbolsFromExpr(n.expr);
        }
    }

    private void addArraySymbolIfNeeded(VariableSymbol symbol) {
        if (symbol == null
                || symbol instanceof GlobalVariableSymbol
                || !(symbol.type instanceof SemanticDynamicArrayType)) {
            return;
        }

        if (arrayIndexBySymbol.containsKey(symbol)) {
            return;
        }

        if (arrayCount == arraySymbols.length) {
            arraySymbols = Arrays.copyOf(
                    arraySymbols,
                    Math.max(4, arraySymbols.length * 2)
            );
        }

        arrayIndexBySymbol.put(symbol, arrayCount);
        arraySymbols[arrayCount] = symbol;
        arrayCount++;
    }

    private void addPointerSymbolIfNeeded(VariableSymbol symbol) {
        if (symbol == null
                || !(symbol.type instanceof SemanticPtrType pointer)
                || pointer.permission != PtrPermission.OWNER
                || !(pointer.referencedType instanceof SemanticDynamicArrayType)) {
            return;
        }

        if (pointerIndexBySymbol.containsKey(symbol)) {
            return;
        }

        if (pointerCount == pointerSymbols.length) {
            pointerSymbols = Arrays.copyOf(
                    pointerSymbols,
                    Math.max(4, pointerSymbols.length * 2)
            );
        }

        pointerIndexBySymbol.put(symbol, pointerCount);
        pointerSymbols[pointerCount] = symbol;
        pointerCount++;
    }

    private int arrayIndexOfSymbol(VariableSymbol symbol) {
        if (symbol == null) {
            return -1;
        }

        Integer index = arrayIndexBySymbol.get(symbol);

        return index == null ? -1 : index;
    }

    private int pointerIndexOfSymbol(VariableSymbol symbol) {
        if (symbol == null) {
            return -1;
        }

        Integer index = pointerIndexBySymbol.get(symbol);

        return index == null ? -1 : index;
    }

    private int singleArrayIndex(SemanticExprNode expr) {
        ArrayOrigin origins = storageOrigins(expr);

        if (origins == null
                || origins.unknown
                || origins.arrays.size() != 1) {
            return -1;
        }

        VariableSymbol symbol = origins.arrays.iterator().next();

        return arrayIndexOfSymbol(symbol);
    }

    private int estimateSnapshotDemand(
            SemanticASTNode node,
            int depth
    ) {
        if (node == null) {
            return 0;
        }

        if (node instanceof SemanticProgramNode n) {
            int total = 0;

            for (SemanticASTNode item : n.topLevelItems) {
                total += estimateSnapshotDemand(item, depth);
            }

            return total;
        }

        if (node instanceof SemanticNamespaceNode n) {
            int total = 0;

            for (SemanticASTNode item : n.items) {
                total += estimateSnapshotDemand(item, depth);
            }

            return total;
        }

        if (node instanceof SemanticFunctionDeclNode n) {
            return 1 + estimateSnapshotDemand(n.body, depth);
        }

        if (node instanceof SemanticStructDeclNode n) {
            int total = 0;

            for (SemanticFunctionDeclNode method : n.methods) {
                total += 1 + estimateSnapshotDemand(method.body, depth);
            }

            return total;
        }

        if (node instanceof SemanticBlockNode n) {
            int total = 0;

            for (SemanticStmtNode stmt : n.statements) {
                total += estimateSnapshotDemand(stmt, depth);
            }

            return total;
        }

        if (node instanceof SemanticIfNode n) {
            int local = n.elseBranch == null ? 2 : 3;

            int nextDepth = depth < MAX_BRANCH_DEPTH
                    ? depth + 1
                    : depth;

            return local
                    + estimateSnapshotDemand(n.thenBranch, nextDepth)
                    + estimateSnapshotDemand(n.elseBranch, nextDepth);
        }

        if (node instanceof SemanticWhileNode n) {
            int total = 2 + estimateSnapshotDemand(n.body, depth);

            for (SemanticStmtNode updateStmt : n.update) {
                total += estimateSnapshotDemand(updateStmt, depth);
            }

            return total;
        }

        if (node instanceof SemanticBreakNode
                || node instanceof SemanticContinueNode) {
            return 1;
        }

        return 0;
    }

    private boolean checkNode(SemanticASTNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            return checkNodeInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private boolean checkNodeInner(SemanticASTNode node) {
        if (node == null) {
            return false;
        }

        if (node instanceof SemanticProgramNode n) {
            for (SemanticASTNode item : n.topLevelItems) {
                checkNode(item);
            }
            return false;
        }

        if (node instanceof SemanticNamespaceNode n) {
            for (SemanticASTNode item : n.items) {
                checkNode(item);
            }
            return false;
        }

        if (node instanceof SemanticFunctionDeclNode n) {
            checkFunction(n);
            return false;
        }

        if (node instanceof SemanticStructDeclNode n) {
            for (SemanticFunctionDeclNode method : n.methods) {
                checkFunction(method);
            }

            return false;
        }

        if (node instanceof SemanticVarDeclNode n) {
            if (n.symbol instanceof GlobalVariableSymbol) {
                return false;
            }

            checkVarDecl(n);
            return false;
        }

        if (node instanceof SemanticStmtNode n) {
            return checkStmt(n);
        }

        if (node instanceof SemanticExprNode n) {
            checkExpr(n);
            return false;
        }

        return false;
    }

    private void checkFunction(SemanticFunctionDeclNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkFunctionInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkFunctionInner(SemanticFunctionDeclNode node) {
        int beforeFunction = snapshot();

        Arrays.fill(arrayState, ArrayState.UNINITED);
        Arrays.fill(freedState, FreedState.NEVER);
        Arrays.fill(escaped, false);
        Arrays.fill(pointerState, ArrayOrigin.UNKNOWN);

        loopStack.clear();
        addressTakenPointers.clear();
        reportedErrorSites.clear();
        reportedWarningSites.clear();
        suppressLifecycleChecks = false;
        branchDepth = 0;
        currentFunction = node.symbol;

        if (node.body != null) {
            checkBlock(node.body, true);
        }

        restore(beforeFunction);
        currentFunction = null;
    }

    private boolean checkStmt(SemanticStmtNode node) {
        if (node == null) {
            return false;
        }

        if (node instanceof SemanticBlockNode n) {
            return checkBlock(n, false);
        }

        if (node instanceof SemanticVarDeclNode n) {
            checkVarDecl(n);
            return false;
        }

        if (node instanceof SemanticAssignNode n) {
            checkAssign(n);
            return false;
        }

        if (node instanceof SemanticIfNode n) {
            return checkIf(n);
        }

        if (node instanceof SemanticWhileNode n) {
            return checkWhile(n);
        }

        if (node instanceof SemanticBreakNode n) {
            recordJumpState(n.levels, false);
            return true;
        }

        if (node instanceof SemanticContinueNode) {
            recordJumpState(1, true);
            return true;
        }

        if (node instanceof SemanticReturnNode n) {
            return checkReturn(n);
        }

        if (node instanceof SemanticExprStmtNode n) {
            checkExpr(n.expr);
            return false;
        }

        return false;
    }

    private boolean checkBlock(
            SemanticBlockNode node,
            boolean functionBody
    ) {
        int[] blockArrayIndices =
                new int[Math.max(1, node.statements.size())];
        int blockArrayCount = 0;

        int[] blockPointerIndices =
                new int[Math.max(1, node.statements.size())];
        int blockPointerCount = 0;

        for (SemanticStmtNode stmt : node.statements) {
            if (stmt instanceof SemanticVarDeclNode decl
                    && decl.symbol != null) {
                int arrayIndex = arrayIndexOfSymbol(decl.symbol);

                if (arrayIndex >= 0) {
                    blockArrayIndices[blockArrayCount++] = arrayIndex;
                }

                int pointerIndex = pointerIndexOfSymbol(decl.symbol);

                if (pointerIndex >= 0) {
                    blockPointerIndices[blockPointerCount++] = pointerIndex;
                }
            }

            boolean terminated = checkStmt(stmt);

            if (terminated) {
                return true;
            }
        }

        String where = functionBody
                ? "end of function '" + functionDisplayName(currentFunction) + "'"
                : "end of scope";

        if (!suppressLifecycleChecks) {
            for (int i = 0; i < blockArrayCount; i++) {
                reportLeakIfNeeded(blockArrayIndices[i], where);
            }
        }

        for (int i = 0; i < blockArrayCount; i++) {
            int index = blockArrayIndices[i];
            arrayState[index] = ArrayState.UNINITED;
            freedState[index] = FreedState.NEVER;
            escaped[index] = false;
        }

        for (int i = 0; i < blockPointerCount; i++) {
            pointerState[blockPointerIndices[i]] = ArrayOrigin.UNKNOWN;
        }

        return false;
    }

    private void checkVarDecl(SemanticVarDeclNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkVarDeclInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkVarDeclInner(SemanticVarDeclNode node) {
        if (node == null || node.symbol == null) {
            return;
        }

        int arrayIndex = arrayIndexOfSymbol(node.symbol);

        if (arrayIndex >= 0) {
            arrayState[arrayIndex] = ArrayState.UNINITED;
            freedState[arrayIndex] = FreedState.NEVER;
            escaped[arrayIndex] = false;
        }

        int pointerIndex = pointerIndexOfSymbol(node.symbol);

        if (pointerIndex >= 0) {
            pointerState[pointerIndex] = ArrayOrigin.EMPTY;
        }

        checkExpr(node.staticInitializer);
    }

    private void checkAssign(SemanticAssignNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkAssignInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkAssignInner(SemanticAssignNode node) {
        checkExpr(node.target);
        checkExpr(node.value);

        if (!(node.target.type instanceof SemanticPtrType)
                || node.isCompound()) {
            return;
        }

        applyPointerStore(node.target, pointerOrigins(node.value));
    }

    private boolean checkIf(SemanticIfNode node) {
        checkExpr(node.condition);

        if (branchDepth >= MAX_BRANCH_DEPTH) {
            return checkIfPathInsensitive(node);
        }

        int before = snapshot();

        branchDepth++;

        boolean thenFeasible = guardPathFeasible(node.condition, true);

        restore(before);

        if (thenFeasible) {
            refineFromGuard(node.condition, true);
        }

        boolean previousSuppression = suppressLifecycleChecks;
        suppressLifecycleChecks = previousSuppression || !thenFeasible;
        boolean thenReturns = checkStmt(node.thenBranch);
        suppressLifecycleChecks = previousSuppression;

        int thenState = thenFeasible ? snapshot() : -1;

        restore(before);

        boolean elseFeasible = guardPathFeasible(node.condition, false);

        boolean elseReturns = false;
        int elseState = -1;
        int fallthroughState = -1;

        if (node.elseBranch != null) {
            if (elseFeasible) {
                refineFromGuard(node.condition, false);
            }

            suppressLifecycleChecks = previousSuppression || !elseFeasible;
            elseReturns = checkStmt(node.elseBranch);
            suppressLifecycleChecks = previousSuppression;

            elseState = elseFeasible ? snapshot() : -1;
        } else if (elseFeasible) {
            refineFromGuard(node.condition, false);
            fallthroughState = snapshot();
        }

        restore(before);

        branchDepth--;

        mergeAfterIf(
                before,
                thenState,
                thenReturns || !thenFeasible,
                elseState,
                elseReturns || !elseFeasible,
                node.elseBranch != null,
                fallthroughState
        );

        boolean thenLeaves = thenReturns || !thenFeasible;
        boolean elseLeaves = node.elseBranch != null
                ? elseReturns || !elseFeasible
                : !elseFeasible;

        return thenLeaves && elseLeaves;
    }

    private boolean checkIfPathInsensitive(SemanticIfNode node) {
        int before = snapshot();

        boolean thenReturns = checkStmt(node.thenBranch);
        int thenState = snapshot();

        restore(before);

        boolean elseReturns = false;
        int elseState = -1;

        if (node.elseBranch != null) {
            elseReturns = checkStmt(node.elseBranch);
            elseState = snapshot();
        }

        restore(before);

        mergeAfterIf(
                before,
                thenState,
                thenReturns,
                elseState,
                elseReturns,
                node.elseBranch != null,
                before
        );

        return node.elseBranch != null && thenReturns && elseReturns;
    }

    private LengthGuard lengthGuard(SemanticExprNode condition) {
        if (condition == null) {
            return null;
        }

        if (condition instanceof SemanticArrayIntrinsicCallNode n
                && n.kind == SemanticArrayIntrinsicKind.LENGTH) {
            return new LengthGuard(n.receiver, "!=", BigInteger.ZERO);
        }

        if (condition instanceof SemanticUnaryOpNode n
                && n.op.equals("!")) {
            LengthGuard inner = lengthGuard(n.expr);

            if (inner == null) {
                return null;
            }

            return new LengthGuard(
                    inner.receiver,
                    negate(inner.op),
                    inner.constant
            );
        }

        if (!(condition instanceof SemanticBinOpNode bin)
                || !isComparison(bin.op)) {
            return null;
        }

        boolean leftLength = isLengthCall(bin.left);
        boolean rightLength = isLengthCall(bin.right);

        if (leftLength == rightLength) {
            return null;
        }

        SemanticExprNode lengthSide = leftLength ? bin.left : bin.right;
        SemanticExprNode constantSide = leftLength ? bin.right : bin.left;

        if (constantSide.constantValue == null) {
            return null;
        }

        String op = leftLength ? bin.op : reverse(bin.op);
        SemanticExprNode receiver =
                ((SemanticArrayIntrinsicCallNode) lengthSide).receiver;

        return new LengthGuard(receiver, op, constantSide.constantValue);
    }

    private boolean guardPathFeasible(
            SemanticExprNode condition,
            boolean guardHolds
    ) {
        if (condition.constantValue != null) {
            boolean truthy = condition.constantValue.signum() != 0;

            return guardHolds == truthy;
        }

        LengthGuard guard = lengthGuard(condition);

        if (guard == null) {
            return true;
        }

        int index = singleArrayIndex(guard.receiver);

        if (index < 0) {
            return true;
        }

        LengthAssertion assertion = guard.assertion(guardHolds);

        return switch (assertion) {
            case NONE -> true;
            case ZERO -> arrayState[index] != ArrayState.INITED;
            case NONZERO -> arrayState[index] != ArrayState.UNINITED;
        };
    }

    private void refineFromGuard(
            SemanticExprNode condition,
            boolean guardHolds
    ) {
        LengthGuard guard = lengthGuard(condition);

        if (guard == null) {
            return;
        }

        int index = singleArrayIndex(guard.receiver);

        if (index < 0) {
            return;
        }

        LengthAssertion assertion = guard.assertion(guardHolds);

        if (assertion == LengthAssertion.ZERO) {
            if (arrayState[index] != ArrayState.UNINITED) {
                arrayState[index] = ArrayState.UNINITED;
                freedState[index] = FreedState.MAYBE;
            }
        } else if (assertion == LengthAssertion.NONZERO) {
            arrayState[index] = ArrayState.INITED;
            freedState[index] = FreedState.NEVER;
        }
    }

    private boolean checkWhile(SemanticWhileNode node) {
        checkExpr(node.condition);

        int before = snapshot();

        boolean bodyFeasible = guardPathFeasible(node.condition, true);

        LoopFrame frame = new LoopFrame();
        loopStack.add(frame);

        List<Integer> exitStates = new ArrayList<>();

        if (guardPathFeasible(node.condition, false)) {
            restore(before);
            refineFromGuard(node.condition, false);
            exitStates.add(snapshot());
        }

        boolean bodyTerminates = false;
        List<Integer> backEdgeStates = new ArrayList<>();

        if (bodyFeasible) {
            restore(before);
            refineFromGuard(node.condition, true);

            bodyTerminates = checkStmt(node.body);
            int afterBody = bodyTerminates ? -1 : snapshot();

            if (!node.update.isEmpty()) {
                if (!bodyTerminates) {
                    restore(afterBody);

                    if (checkUpdates(node.update)) {
                        backEdgeStates.add(snapshot());
                    }
                }

                for (int continueState : frame.continueStates) {
                    restore(continueState);

                    if (checkUpdates(node.update)) {
                        backEdgeStates.add(snapshot());
                    }
                }
            } else {
                if (!bodyTerminates) {
                    backEdgeStates.add(afterBody);
                }

                backEdgeStates.addAll(frame.continueStates);
            }

            for (int backEdgeState : backEdgeStates) {
                restore(backEdgeState);

                if (guardPathFeasible(node.condition, false)) {
                    refineFromGuard(node.condition, false);
                    exitStates.add(snapshot());
                }
            }
        } else {
            boolean previousSuppression = suppressLifecycleChecks;
            suppressLifecycleChecks = true;

            checkStmt(node.body);

            for (SemanticStmtNode updateStmt : node.update) {
                checkStmt(updateStmt);
            }

            suppressLifecycleChecks = previousSuppression;
        }

        loopStack.remove(loopStack.size() - 1);

        restore(before);

        if (!exitStates.isEmpty()) {
            mergeLoopExitStates(exitStates);
        }

        if (bodyFeasible) {
            for (int breakState : frame.breakStates) {
                mergeStateIntoCurrent(breakState);
            }
        }

        boolean hasExit = !exitStates.isEmpty()
                || (bodyFeasible && !frame.breakStates.isEmpty());

        return !hasExit;
    }

    private boolean checkUpdates(List<SemanticStmtNode> updates) {
        for (SemanticStmtNode update : updates) {
            if (checkStmt(update)) {
                return false;
            }
        }

        return true;
    }

    private void recordJumpState(int levels, boolean isContinue) {
        if (loopStack.isEmpty() || suppressLifecycleChecks) {
            return;
        }

        int slot = snapshot();

        int frameIndex = loopStack.size() - levels;

        if (frameIndex < 0) {
            frameIndex = 0;
        }

        LoopFrame frame = loopStack.get(frameIndex);

        if (isContinue) {
            frame.continueStates.add(slot);
        } else {
            frame.breakStates.add(slot);
        }
    }

    private void mergeLoopExitStates(List<Integer> slots) {
        if (slots.isEmpty()) {
            return;
        }

        int first = slots.get(0);

        for (int i = 0; i < arrayCount; i++) {
            ArrayState state =
                    arrayStateFromSnapshot(first, i, first);
            FreedState freed =
                    freedStateFromSnapshot(first, i, first);

            for (int j = 1; j < slots.size(); j++) {
                int slot = slots.get(j);

                state = mergeArrayState(
                        state,
                        arrayStateFromSnapshot(slot, i, first)
                );

                freed = mergeFreedState(
                        freed,
                        freedStateFromSnapshot(slot, i, first)
                );
            }

            if (state == ArrayState.INITED) {
                freed = FreedState.NEVER;
            } else if (state == ArrayState.MAYBE_INITED) {
                freed = FreedState.MAYBE;
            }

            arrayState[i] = state;
            freedState[i] = freed;
        }

        for (int i = 0; i < pointerCount; i++) {
            ArrayOrigin origin =
                    pointerOriginFromSnapshot(first, i, first);

            for (int j = 1; j < slots.size(); j++) {
                origin = mergeOrigins(
                        origin,
                        pointerOriginFromSnapshot(slots.get(j), i, first)
                );
            }

            pointerState[i] = origin;
        }
    }

    private void mergeStateIntoCurrent(int slot) {
        for (int i = 0; i < arrayCount; i++) {
            arrayState[i] = mergeArrayState(
                    arrayState[i],
                    arrayStateFromSnapshot(slot, i, slot)
            );

            freedState[i] = mergeFreedState(
                    freedState[i],
                    freedStateFromSnapshot(slot, i, slot)
            );

            if (arrayState[i] == ArrayState.INITED) {
                freedState[i] = FreedState.NEVER;
            } else if (arrayState[i] == ArrayState.MAYBE_INITED) {
                freedState[i] = FreedState.MAYBE;
            }
        }

        for (int i = 0; i < pointerCount; i++) {
            pointerState[i] = mergeOrigins(
                    pointerState[i],
                    pointerOriginFromSnapshot(slot, i, slot)
            );
        }
    }

    private boolean checkReturn(SemanticReturnNode node) {
        checkExpr(node.value);

        if (!suppressLifecycleChecks) {
            reportLiveArrays(
                    "return from function '"
                            + functionDisplayName(node.function)
                            + "'"
            );
        }

        return true;
    }

    private void reportLiveArrays(String where) {
        for (int i = 0; i < arrayCount; i++) {
            reportLeakIfNeeded(i, where);
        }
    }

    private void reportLeakIfNeeded(int index, String where) {
        if (suppressLifecycleChecks
                || index < 0
                || index >= arrayCount
                || escaped[index]) {
            return;
        }

        String name = arraySymbols[index].qualifiedName();

        if (arrayState[index] == ArrayState.INITED) {
            diagnostics.warning(
                    "Dynamic array '"
                            + name
                            + "' is still initialized at "
                            + where
                            + " (missing explicit uninit)"
            );
        } else if (arrayState[index] == ArrayState.MAYBE_INITED) {
            diagnostics.warning(
                    "Dynamic array '"
                            + name
                            + "' may still be initialized at "
                            + where
                            + " (missing explicit uninit)"
            );
        }
    }

    private ArrayOrigin storageOrigins(SemanticExprNode expr) {
        if (expr == null) {
            return ArrayOrigin.EMPTY;
        }

        if (expr instanceof SemanticVarExprNode n) {
            if (n.symbol instanceof GlobalVariableSymbol) {
                return ArrayOrigin.EMPTY;
            }

            int index = arrayIndexOfSymbol(n.symbol);

            return index < 0
                    ? ArrayOrigin.EMPTY
                    : ArrayOrigin.of(n.symbol);
        }

        if (expr instanceof SemanticDerefNode n) {
            return pointerOrigins(n.expr);
        }

        if (expr instanceof SemanticCastExprNode n) {
            return storageOrigins(n.expr);
        }

        if (expr instanceof SemanticMemberAccessNode) {
            return ArrayOrigin.EMPTY;
        }

        if (expr instanceof SemanticArrayAccessNode) {
            return ArrayOrigin.EMPTY;
        }

        return ArrayOrigin.EMPTY;
    }

    private ArrayOrigin pointerOrigins(SemanticExprNode expr) {
        if (expr == null) {
            return ArrayOrigin.UNKNOWN;
        }

        if (expr instanceof SemanticNullLiteralNode) {
            return ArrayOrigin.EMPTY;
        }

        if (expr instanceof SemanticGetPtrNode n) {
            return storageOrigins(n.target);
        }

        if (expr instanceof SemanticVarExprNode n) {
            int index = pointerIndexOfSymbol(n.symbol);

            return index < 0
                    ? ArrayOrigin.UNKNOWN
                    : pointerState[index];
        }

        if (expr instanceof SemanticDerefNode) {
            return ArrayOrigin.UNKNOWN;
        }

        if (expr instanceof SemanticCastExprNode n) {
            return pointerOrigins(n.expr);
        }

        if (expr instanceof SemanticFunctionCallNode) {
            return ArrayOrigin.UNKNOWN;
        }

        if (expr instanceof SemanticMemberAccessNode) {
            return ArrayOrigin.UNKNOWN;
        }

        if (expr instanceof SemanticArrayAccessNode) {
            return ArrayOrigin.UNKNOWN;
        }

        return ArrayOrigin.UNKNOWN;
    }

    private void applyPointerStore(
            SemanticExprNode target,
            ArrayOrigin value
    ) {
        if (target instanceof SemanticVarExprNode n) {
            int index = pointerIndexOfSymbol(n.symbol);

            if (index >= 0) {
                pointerState[index] = value;

                if (n.symbol instanceof GlobalVariableSymbol) {
                    markEscaped(value);
                }
            } else if (isOwnerDynamicArrayPointer(target.type)) {
                markEscaped(value);
            }

            return;
        }

        if (isOwnerDynamicArrayPointer(target.type)) {
            markEscaped(value);
        }
    }

    private void checkExpr(SemanticExprNode expr) {
        diagnostics.push(expr == null ? SourceSpan.NONE : expr.span);
        try {
            checkExprInner(expr);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkExprInner(SemanticExprNode expr) {
        if (expr == null) {
            return;
        }

        if (expr instanceof SemanticIntLiteralNode
                || expr instanceof SemanticNullLiteralNode
                || expr instanceof SemanticVarExprNode) {
            return;
        }

        if (expr instanceof SemanticUnaryOpNode n) {
            checkExpr(n.expr);
            return;
        }

        if (expr instanceof SemanticBinOpNode n) {
            checkExpr(n.left);
            checkExpr(n.right);
            return;
        }

        if (expr instanceof SemanticArrayAccessNode n) {
            checkExpr(n.target);
            checkExpr(n.index);

            if (n.target.type instanceof SemanticDynamicArrayType) {
                requireInitialized(n.target, n);
            }

            return;
        }

        if (expr instanceof SemanticMemberAccessNode n) {
            checkExpr(n.target);
            return;
        }

        if (expr instanceof SemanticFunctionCallNode n) {
            for (SemanticExprNode arg : n.args) {
                checkExpr(arg);
            }

            invalidateAfterCall(n);
            return;
        }

        if (expr instanceof SemanticArrayIntrinsicCallNode n) {
            checkExpr(n.receiver);

            for (SemanticExprNode arg : n.args) {
                checkExpr(arg);
            }

            applyArrayIntrinsic(n);
            return;
        }

        if (expr instanceof SemanticGetPtrNode n) {
            checkExpr(n.target);
            markPointerAddressTaken(n);
            return;
        }

        if (expr instanceof SemanticDerefNode n) {
            checkExpr(n.expr);
            return;
        }

        if (expr instanceof SemanticCastExprNode n) {
            checkExpr(n.expr);
        }
    }

    private void applyArrayIntrinsic(SemanticArrayIntrinsicCallNode node) {
        switch (node.kind) {
            case INIT:
            case INIT_ZERO:
                applyInit(node.receiver);
                return;

            case RESIZE:
                applyResize(node.receiver, node);
                return;

            case UNINIT:
                applyUninit(node.receiver, node);
                return;

            case LENGTH:
                return;

            default:
                requireInitialized(node.receiver, node);

                if (node.kind == SemanticArrayIntrinsicKind.MEMCPY
                        || node.kind == SemanticArrayIntrinsicKind.MEMCMP
                        || node.kind == SemanticArrayIntrinsicKind.MEMMEM) {
                    if (!node.args.isEmpty()) {
                        requirePointerSourceInitialized(node.args.get(0), node);
                    }
                }
        }
    }

    private void applyInit(SemanticExprNode receiver) {
        ArrayOrigin origins = storageOrigins(receiver);

        if (origins.unknown || origins.arrays.size() != 1) {
            for (VariableSymbol symbol : origins.arrays) {
                int index = arrayIndexOfSymbol(symbol);

                if (index < 0) {
                    continue;
                }

                if (arrayState[index] != ArrayState.INITED) {
                    arrayState[index] = ArrayState.MAYBE_INITED;
                    freedState[index] = FreedState.MAYBE;
                }
            }

            if (origins.unknown || origins.arrays.isEmpty()) {
                havocEscapedArrays();
            }

            return;
        }

        VariableSymbol symbol = origins.arrays.iterator().next();
        int index = arrayIndexOfSymbol(symbol);

        if (index < 0) {
            havocEscapedArrays();
            return;
        }

        arrayState[index] = ArrayState.INITED;
        freedState[index] = FreedState.NEVER;
    }

    private void applyResize(
            SemanticExprNode receiver,
            SemanticASTNode site
    ) {
        ArrayOrigin origins = storageOrigins(receiver);

        if (origins.unknown || origins.arrays.size() != 1) {
            if (origins.unknown || origins.arrays.isEmpty()) {
                havocEscapedArrays();
            }

            return;
        }

        VariableSymbol symbol = origins.arrays.iterator().next();
        int index = arrayIndexOfSymbol(symbol);

        if (index < 0) {
            havocEscapedArrays();
            return;
        }

        if (arrayState[index] == ArrayState.MAYBE_INITED) {
            reportWarningOnce(
                    site,
                    "Possible use of uninitialized dynamic array '"
                            + symbol.qualifiedName()
                            + "'"
            );
        } else if (arrayState[index] == ArrayState.UNINITED) {
            reportUninitializedUse(index, site);
        }

        arrayState[index] = ArrayState.INITED;
        freedState[index] = FreedState.NEVER;
    }

    private void applyUninit(
            SemanticExprNode receiver,
            SemanticASTNode site
    ) {
        ArrayOrigin origins = storageOrigins(receiver);

        if (origins.unknown || origins.arrays.size() != 1) {
            for (VariableSymbol symbol : origins.arrays) {
                int index = arrayIndexOfSymbol(symbol);

                if (index >= 0
                        && arrayState[index] != ArrayState.UNINITED) {
                    arrayState[index] = ArrayState.MAYBE_INITED;
                    freedState[index] = FreedState.MAYBE;
                }
            }

            if (origins.unknown || origins.arrays.isEmpty()) {
                havocEscapedArrays();
            }

            return;
        }

        VariableSymbol symbol = origins.arrays.iterator().next();
        int index = arrayIndexOfSymbol(symbol);

        if (index < 0) {
            havocEscapedArrays();
            return;
        }

        if (arrayState[index] == ArrayState.INITED) {
            arrayState[index] = ArrayState.UNINITED;
            freedState[index] = FreedState.YES;
            return;
        }

        if (arrayState[index] == ArrayState.MAYBE_INITED) {
            reportWarningOnce(
                    site,
                    "Possible invalid free or double free of dynamic array '"
                            + symbol.qualifiedName()
                            + "'"
            );

            arrayState[index] = ArrayState.UNINITED;
            freedState[index] = FreedState.YES;
            return;
        }

        if (freedState[index] == FreedState.YES) {
            reportErrorOnce(
                    site,
                    "Double free of dynamic array '"
                            + symbol.qualifiedName()
                            + "' (uninit called twice)"
            );
        } else if (freedState[index] == FreedState.NEVER) {
            reportErrorOnce(
                    site,
                    "Invalid free of dynamic array '"
                            + symbol.qualifiedName()
                            + "' (uninit before init)"
            );
        } else {
            reportWarningOnce(
                    site,
                    "Possible invalid free or double free of dynamic array '"
                            + symbol.qualifiedName()
                            + "'"
            );
        }

        arrayState[index] = ArrayState.UNINITED;
        freedState[index] = FreedState.YES;
    }

    private void requireInitialized(
            SemanticExprNode receiver,
            SemanticASTNode site
    ) {
        int index = singleArrayIndex(receiver);

        if (index < 0) {
            return;
        }

        if (arrayState[index] == ArrayState.INITED) {
            return;
        }

        if (arrayState[index] == ArrayState.MAYBE_INITED) {
            reportWarningOnce(
                    site,
                    "Possible use of uninitialized dynamic array '"
                            + arraySymbols[index].qualifiedName()
                            + "'"
            );
            return;
        }

        reportUninitializedUse(index, site);
    }

    private void requirePointerSourceInitialized(
            SemanticExprNode source,
            SemanticASTNode site
    ) {
        ArrayOrigin origins = pointerOrigins(source);

        if (origins.unknown || origins.arrays.size() != 1) {
            return;
        }

        VariableSymbol symbol = origins.arrays.iterator().next();
        int index = arrayIndexOfSymbol(symbol);

        if (index < 0 || arrayState[index] == ArrayState.INITED) {
            return;
        }

        if (arrayState[index] == ArrayState.MAYBE_INITED) {
            reportWarningOnce(
                    site,
                    "Possible use of uninitialized dynamic array '"
                            + symbol.qualifiedName()
                            + "'"
            );
            return;
        }

        reportUninitializedUse(index, site);
    }

    private void reportUninitializedUse(
            int index,
            SemanticASTNode site
    ) {
        String name = arraySymbols[index].qualifiedName();

        if (freedState[index] == FreedState.YES) {
            reportErrorOnce(
                    site,
                    "Use of dynamic array '" + name + "' after uninit"
            );
        } else if (freedState[index] == FreedState.NEVER) {
            reportErrorOnce(
                    site,
                    "Use of uninitialized dynamic array '"
                            + name
                            + "' before init"
            );
        } else {
            reportWarningOnce(
                    site,
                    "Dynamic array '"
                            + name
                            + "' may be uninitialized or freed"
            );
        }
    }

    private void markPointerAddressTaken(SemanticGetPtrNode node) {
        if (!(node.target instanceof SemanticVarExprNode var)) {
            return;
        }

        int index = pointerIndexOfSymbol(var.symbol);

        if (index < 0) {
            return;
        }

        addressTakenPointers.add(var.symbol);
        markEscaped(pointerState[index]);
    }

    private void invalidateAfterCall(SemanticFunctionCallNode call) {
        for (int i = 0; i < call.args.size(); i++) {
            if (i >= call.function.parameters.size()) {
                continue;
            }

            SemanticType parameterType =
                    call.function.parameters.get(i).type;

            if (!isOwnerDynamicArrayPointer(parameterType)) {
                continue;
            }

            ArrayOrigin origins = pointerOrigins(call.args.get(i));

            markMaybeInited(origins);
            markEscaped(origins);
        }

        for (int i = 0; i < pointerCount; i++) {
            if (pointerSymbols[i] instanceof GlobalVariableSymbol) {
                pointerState[i] = ArrayOrigin.UNKNOWN;
            }
        }

        for (VariableSymbol symbol : addressTakenPointers) {
            int index = pointerIndexOfSymbol(symbol);

            if (index >= 0) {
                pointerState[index] = ArrayOrigin.UNKNOWN;
            }
        }

        havocEscapedArrays();
    }

    private boolean isOwnerDynamicArrayPointer(SemanticType type) {
        return type instanceof SemanticPtrType pointer
                && pointer.permission == PtrPermission.OWNER
                && pointer.referencedType instanceof SemanticDynamicArrayType;
    }

    private void markMaybeInited(ArrayOrigin origins) {
        if (origins == null) {
            return;
        }

        for (VariableSymbol symbol : origins.arrays) {
            int index = arrayIndexOfSymbol(symbol);

            if (index >= 0) {
                arrayState[index] = ArrayState.MAYBE_INITED;
                freedState[index] = FreedState.MAYBE;
            }
        }
    }

    private void markEscaped(ArrayOrigin origins) {
        if (origins == null) {
            return;
        }

        for (VariableSymbol symbol : origins.arrays) {
            int index = arrayIndexOfSymbol(symbol);

            if (index >= 0) {
                escaped[index] = true;
            }
        }
    }

    private void havocEscapedArrays() {
        for (int i = 0; i < arrayCount; i++) {
            if (escaped[i]) {
                arrayState[i] = ArrayState.MAYBE_INITED;
                freedState[i] = FreedState.MAYBE;
            }
        }
    }

    private void reportErrorOnce(SemanticASTNode site, String message) {
        if (suppressLifecycleChecks) {
            return;
        }

        if (site == null || reportedErrorSites.add(site)) {
            diagnostics.error(message);
        }
    }

    private void reportWarningOnce(SemanticASTNode site, String message) {
        if (suppressLifecycleChecks) {
            return;
        }

        if (site == null || reportedWarningSites.add(site)) {
            diagnostics.warning(message);
        }
    }

    private int snapshot() {
        ensureSnapshotCapacity();

        int slot = snapshotCount++;

        System.arraycopy(
                arrayState,
                0,
                arraySnapshots[slot],
                0,
                arrayCount
        );

        System.arraycopy(
                freedState,
                0,
                freedSnapshots[slot],
                0,
                arrayCount
        );

        System.arraycopy(
                pointerState,
                0,
                pointerSnapshots[slot],
                0,
                pointerCount
        );

        return slot;
    }

    private void restore(int snapshotSlot) {
        if (snapshotSlot < 0) {
            return;
        }

        System.arraycopy(
                arraySnapshots[snapshotSlot],
                0,
                arrayState,
                0,
                arrayCount
        );

        System.arraycopy(
                freedSnapshots[snapshotSlot],
                0,
                freedState,
                0,
                arrayCount
        );

        System.arraycopy(
                pointerSnapshots[snapshotSlot],
                0,
                pointerState,
                0,
                pointerCount
        );
    }

    private void ensureSnapshotCapacity() {
        if (snapshotCount < arraySnapshots.length) {
            return;
        }

        int oldLength = arraySnapshots.length;
        int newLength = Math.max(16, oldLength * 2);

        arraySnapshots = Arrays.copyOf(arraySnapshots, newLength);
        freedSnapshots = Arrays.copyOf(freedSnapshots, newLength);
        pointerSnapshots = Arrays.copyOf(pointerSnapshots, newLength);

        for (int i = oldLength; i < newLength; i++) {
            arraySnapshots[i] = new ArrayState[arrayCount];
            freedSnapshots[i] = new FreedState[arrayCount];
            pointerSnapshots[i] = new ArrayOrigin[pointerCount];

            Arrays.fill(arraySnapshots[i], ArrayState.UNINITED);
            Arrays.fill(freedSnapshots[i], FreedState.NEVER);
            Arrays.fill(pointerSnapshots[i], ArrayOrigin.UNKNOWN);
        }
    }

    private void mergeAfterIf(
            int before,
            int thenState,
            boolean thenTerminates,
            int elseState,
            boolean elseTerminates,
            boolean hasElse,
            int fallthroughState
    ) {
        for (int i = 0; i < arrayCount; i++) {
            boolean hasContinuingPath = false;
            ArrayState mergedState = null;
            FreedState mergedFreed = null;

            if (!thenTerminates) {
                hasContinuingPath = true;
                mergedState = mergeArrayState(
                        mergedState,
                        arrayStateFromSnapshot(thenState, i, before)
                );
                mergedFreed = mergeFreedState(
                        mergedFreed,
                        freedStateFromSnapshot(thenState, i, before)
                );
            }

            if (hasElse) {
                if (!elseTerminates) {
                    hasContinuingPath = true;
                    mergedState = mergeArrayState(
                            mergedState,
                            arrayStateFromSnapshot(elseState, i, before)
                    );
                    mergedFreed = mergeFreedState(
                            mergedFreed,
                            freedStateFromSnapshot(elseState, i, before)
                    );
                }
            } else if (fallthroughState >= 0) {
                hasContinuingPath = true;
                mergedState = mergeArrayState(
                        mergedState,
                        arrayStateFromSnapshot(fallthroughState, i, before)
                );
                mergedFreed = mergeFreedState(
                        mergedFreed,
                        freedStateFromSnapshot(fallthroughState, i, before)
                );
            }

            if (hasContinuingPath && mergedState != null) {
                arrayState[i] = mergedState;
                freedState[i] = mergedFreed == null
                        ? FreedState.MAYBE
                        : mergedFreed;

                if (arrayState[i] == ArrayState.INITED) {
                    freedState[i] = FreedState.NEVER;
                } else if (arrayState[i] == ArrayState.MAYBE_INITED) {
                    freedState[i] = FreedState.MAYBE;
                }
            } else {
                arrayState[i] = arrayStateFromSnapshot(before, i, before);
                freedState[i] = freedStateFromSnapshot(before, i, before);
            }
        }

        for (int i = 0; i < pointerCount; i++) {
            boolean hasContinuingPath = false;
            ArrayOrigin merged = null;

            if (!thenTerminates) {
                hasContinuingPath = true;
                merged = mergeOrigins(
                        merged,
                        pointerOriginFromSnapshot(thenState, i, before)
                );
            }

            if (hasElse) {
                if (!elseTerminates) {
                    hasContinuingPath = true;
                    merged = mergeOrigins(
                            merged,
                            pointerOriginFromSnapshot(elseState, i, before)
                    );
                }
            } else if (fallthroughState >= 0) {
                hasContinuingPath = true;
                merged = mergeOrigins(
                        merged,
                        pointerOriginFromSnapshot(fallthroughState, i, before)
                );
            }

            if (hasContinuingPath && merged != null) {
                pointerState[i] = merged;
            } else {
                pointerState[i] =
                        pointerOriginFromSnapshot(before, i, before);
            }
        }
    }

    private ArrayState arrayStateFromSnapshot(
            int snapshotSlot,
            int index,
            int fallbackSlot
    ) {
        if (index < 0 || index >= arrayCount) {
            return ArrayState.UNINITED;
        }

        if (snapshotSlot >= 0
                && snapshotSlot < snapshotCount
                && arraySnapshots[snapshotSlot] != null) {
            ArrayState value = arraySnapshots[snapshotSlot][index];

            if (value != null) {
                return value;
            }
        }

        if (fallbackSlot >= 0
                && fallbackSlot < snapshotCount
                && arraySnapshots[fallbackSlot] != null) {
            ArrayState fallback = arraySnapshots[fallbackSlot][index];

            if (fallback != null) {
                return fallback;
            }
        }

        return ArrayState.UNINITED;
    }

    private FreedState freedStateFromSnapshot(
            int snapshotSlot,
            int index,
            int fallbackSlot
    ) {
        if (index < 0 || index >= arrayCount) {
            return FreedState.NEVER;
        }

        if (snapshotSlot >= 0
                && snapshotSlot < snapshotCount
                && freedSnapshots[snapshotSlot] != null) {
            FreedState value = freedSnapshots[snapshotSlot][index];

            if (value != null) {
                return value;
            }
        }

        if (fallbackSlot >= 0
                && fallbackSlot < snapshotCount
                && freedSnapshots[fallbackSlot] != null) {
            FreedState fallback = freedSnapshots[fallbackSlot][index];

            if (fallback != null) {
                return fallback;
            }
        }

        return FreedState.NEVER;
    }

    private ArrayOrigin pointerOriginFromSnapshot(
            int snapshotSlot,
            int index,
            int fallbackSlot
    ) {
        if (index < 0 || index >= pointerCount) {
            return ArrayOrigin.UNKNOWN;
        }

        if (snapshotSlot >= 0
                && snapshotSlot < snapshotCount
                && pointerSnapshots[snapshotSlot] != null) {
            ArrayOrigin value = pointerSnapshots[snapshotSlot][index];

            if (value != null) {
                return value;
            }
        }

        if (fallbackSlot >= 0
                && fallbackSlot < snapshotCount
                && pointerSnapshots[fallbackSlot] != null) {
            ArrayOrigin fallback =
                    pointerSnapshots[fallbackSlot][index];

            if (fallback != null) {
                return fallback;
            }
        }

        return ArrayOrigin.UNKNOWN;
    }

    private ArrayState mergeArrayState(
            ArrayState left,
            ArrayState right
    ) {
        if (right == null) {
            right = ArrayState.UNINITED;
        }

        if (left == null) {
            return right;
        }

        if (left == right) {
            return left;
        }

        return ArrayState.MAYBE_INITED;
    }

    private FreedState mergeFreedState(
            FreedState left,
            FreedState right
    ) {
        if (right == null) {
            right = FreedState.NEVER;
        }

        if (left == null) {
            return right;
        }

        if (left == right) {
            return left;
        }

        return FreedState.MAYBE;
    }

    private ArrayOrigin mergeOrigins(
            ArrayOrigin left,
            ArrayOrigin right
    ) {
        if (left == null) {
            return right == null ? ArrayOrigin.UNKNOWN : right;
        }

        if (right == null) {
            return left;
        }

        return left.union(right);
    }

    private static boolean isLengthCall(SemanticExprNode expr) {
        return expr instanceof SemanticArrayIntrinsicCallNode n
                && n.kind == SemanticArrayIntrinsicKind.LENGTH;
    }

    private static boolean isComparison(String op) {
        return op.equals("==")
                || op.equals("!=")
                || op.equals("<")
                || op.equals("<=")
                || op.equals(">")
                || op.equals(">=");
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

    private static String reverse(String op) {
        return switch (op) {
            case "<" -> ">";
            case "<=" -> ">=";
            case ">" -> "<";
            case ">=" -> "<=";
            default -> op;
        };
    }

    private String functionDisplayName(FunctionSymbol function) {
        if (function == null) {
            return "<unknown>";
        }

        return function.qualifiedName();
    }
}
