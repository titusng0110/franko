import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ============================================================================
 * POINTER NULL-STATE CHECKER
 * ============================================================================
 *
 * PURPOSE:
 * PointerNullStateChecker validates Franko pointer rules over the fully
 * lowered, legality-checked Semantic AST.
 *
 * It tracks one fact per pointer-typed variable: whether the variable is
 * definitely null, definitely pointing, or possibly null on the current
 * execution path.
 *
 * Rules checked:
 *
 *   - null is only valid for pointer types; integer 0 is not null,
 *   - pointer declarations are guaranteed null-initialized,
 *   - dereferencing a NULL pointer variable is an error,
 *   - dereferencing a MAYBE_NULL pointer variable is a warning,
 *   - only getptr(...) certifies an pointer variable as POINTING; an
 *     assignment from another pointer variable copies that variable's exact
 *     state, so POINTING propagates only through certified sources,
 *   - pointer state is tracked path-sensitively up to MAX_BRANCH_DEPTH,
 *     with guard refinement for 'p == null' and 'p != null' conditions,
 *   - at function entry every tracked pointer variable is MAYBE_NULL;
 *     declarations and assignments refine it.
 *
 * This checker is intentionally intra-procedural.
 */
public final class PointerNullStateChecker {
    private static final int MAX_BRANCH_DEPTH = 10;

    /*
     * Pointer-state matrix for pointer-typed variables.
     *
     * POINTING:
     *   The variable is certified to hold a real storage pointer on the
     *   current path.
     *
     *   The only expression that certifies POINTING is getptr(...).
     *   Assignment from another variable copies that variable's state, so
     *   POINTING propagates only transitively through certified sources.
     *
     * NULL:
     *   The variable is definitely null on the current path. Pointer
     *   declarations are guaranteed null-initialized, so a fresh
     *   declaration starts here.
     *
     * MAYBE_NULL:
     *   The variable may be null on the current path. This is the entry
     *   state for function parameters, globals, and values of unknown
     *   provenance (call results, member/array reads).
     *
     * In all three states, dereferencing a NULL or MAYBE_NULL variable is
     * invalid.
     */
    private enum PointerState {
        POINTING,
        NULL,
        MAYBE_NULL
    }

    private final DiagnosticBag diagnostics;

    /*
     * Symbol indexing.
     *
     * The checker uses arrays for active state and snapshots. A small
     * identity map is used only to translate a VariableSymbol identity into a
     * stable array index.
     */
    private final IdentityHashMap<VariableSymbol, Integer> pointerIndexBySymbol =
            new IdentityHashMap<>();

    private VariableSymbol[] pointerSymbols = new VariableSymbol[0];
    private int pointerCount = 0;

    /*
     * Active pointer state.
     *
     * pointerState[i] is the pointer state for pointerSymbols[i].
     */
    private PointerState[] pointerState = new PointerState[0];

    /*
     * Preallocated snapshot storage.
     *
     * pointerSnapshots[k] is a full copy of pointerState at some control-flow
     * point. Snapshot capacity is estimated from the number of if/else
     * structures, nested branch depth, loops, and functions.
     */
    private PointerState[][] pointerSnapshots = new PointerState[0][];

    private int snapshotCount = 0;

    private int branchDepth = 0;

    /*
     * Enclosing-loop context for break/continue.
     *
     * Each frame records the snapshot slots of the abrupt jump targets
     * inside that loop:
     *
     *   - breakStates:   states at `break` points (jump past the loop,
     *                    skipping the epilogue),
     *   - continueStates: states at `continue` points (jump to the loop's
     *                    continue label, running the epilogue next).
     *
     * break N targets the frame N levels up (levels are 1-based; the
     * innermost loop is the last frame). continue always targets the
     * innermost frame.
     */
    private static final class LoopFrame {
        final List<Integer> breakStates = new ArrayList<>();
        final List<Integer> continueStates = new ArrayList<>();
    }

    private final List<LoopFrame> loopStack = new ArrayList<>();

    /*
     * Suppresses null-deref diagnostics while checking code that cannot
     * execute:
     *
     *   - bodies of statically infeasible guard paths, where the guard's
     *     assertion contradicts a definite current state.
     */
    private boolean suppressNullDerefChecks = false;

    /*
     * Pointer variables that have already produced a maybe-null deref
     * warning.
     *
     * Path-sensitive exploration revisits the same deref site once per path
     * through ancestor branches, and desugared array initializers duplicate
     * a single source-level deref into one deref node per element. To keep
     * the warning volume bounded and useful, each pointer variable warns at
     * most once per check.
     */
    private final Set<VariableSymbol> warnedMaybeNullVars =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public PointerNullStateChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public void check(SemanticASTNode node) {
        diagnostics.clear();

        prepareAnalysis(node);

        branchDepth = 0;
        snapshotCount = 0;

        Arrays.fill(pointerState, PointerState.MAYBE_NULL);

        warnedMaybeNullVars.clear();

        checkNode(node);

        if (diagnostics.hasErrors()) {
            throw new SemanticAnalyzer.SemanticException(
                    diagnostics.formatErrors()
            );
        }
    }

    // ============================================================
    // Analysis Preparation
    // ============================================================

    private void prepareAnalysis(SemanticASTNode root) {
        pointerIndexBySymbol.clear();
        pointerSymbols = new VariableSymbol[16];
        pointerCount = 0;

        collectSymbols(root);

        pointerSymbols = Arrays.copyOf(pointerSymbols, pointerCount);

        pointerState = new PointerState[pointerCount];
        Arrays.fill(pointerState, PointerState.MAYBE_NULL);

        int estimatedSnapshots =
                Math.max(2*MAX_BRANCH_DEPTH, estimateSnapshotDemand(root, 0) + MAX_BRANCH_DEPTH);

        pointerSnapshots = new PointerState[estimatedSnapshots][pointerCount];

        for (int i = 0; i < pointerSnapshots.length; i++) {
            Arrays.fill(pointerSnapshots[i], PointerState.MAYBE_NULL);
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
            collectSymbols(n.body);
            return;
        }

        if (node instanceof SemanticStructDeclNode n) {
            for (SemanticFunctionDeclNode method : n.methods) {
                collectSymbols(method.body);
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
            addPointerSymbolIfNeeded(n.symbol);
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

        if (node instanceof SemanticPrintNode n) {
            for (SemanticExprNode arg : n.args) {
                collectSymbolsFromExpr(arg);
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

        if (expr instanceof SemanticIntLiteralNode) {
            return;
        }

        if (expr instanceof SemanticVarExprNode n) {
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
        }

        if (expr instanceof SemanticCastExprNode n) {
            collectSymbolsFromExpr(n.expr);
        }
    }

    private void addPointerSymbolIfNeeded(VariableSymbol symbol) {
        if (symbol == null
                || !(symbol.type instanceof SemanticPtrType)) {
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

    /*
     * Resolves a SemanticExprNode to the pointer-matrix index of the plain
     * pointer-typed variable it denotes.
     *
     * Only plain variable expressions with an pointer type are tracked.
     * Other shapes (array elements, struct fields, deref chains, call
     * results) are not tracked.
     *
     * Returns the pointer-matrix index, or -1 when not trackable.
     */
    private int pointerIndexOf(SemanticExprNode expr) {
        if (!(expr instanceof SemanticVarExprNode n)) {
            return -1;
        }

        return pointerIndexOfSymbol(n.symbol);
    }

    private int pointerIndexOfSymbol(VariableSymbol symbol) {
        if (symbol == null
                || !(symbol.type instanceof SemanticPtrType)) {
            return -1;
        }

        Integer index = pointerIndexBySymbol.get(symbol);

        return index == null ? -1 : index;
    }

    /*
     * Human-readable name for diagnostics about an pointer expression.
     */
    private String varNameOf(SemanticExprNode expr) {
        if (expr instanceof SemanticVarExprNode n
                && n.symbol != null) {
            return n.symbol.qualifiedName();
        }

        return "<expression>";
    }

    /*
     * Resolves the tracked variable symbol denoted by an pointer expression,
     * or null when the expression is not a plain variable.
     */
    private VariableSymbol symbolOf(SemanticExprNode expr) {
        if (expr instanceof SemanticVarExprNode n) {
            return n.symbol;
        }

        return null;
    }

    /*
     * Computes the pointer state a variable takes when assigned the given
     * value expression.
     *
     *   null literal         -> NULL      (definitely null)
     *   getptr(...)         -> POINTING  (the only certification of a
     *                                         real storage pointer)
     *   pointer variable     -> copy that variable's exact state:
     *
     *                             ptr<int> b = null;
     *                             ptr<int> a = b;   a: NULL, not MAYBE_NULL
     *
     *                         A variable only becomes POINTING when it was
     *                         itself certified, directly by getptr or
     *                         transitively through certified variables.
     *
     *   anything else        -> MAYBE_NULL (unknown provenance: call results,
     *                          parameters, member/array reads, deref chains)
     */
    private PointerState pointerStateOfValue(SemanticExprNode value) {
        if (value instanceof SemanticNullLiteralNode) {
            return PointerState.NULL;
        }

        if (value instanceof SemanticGetPtrNode) {
            return PointerState.POINTING;
        }

        int sourceIndex = pointerIndexOf(value);

        if (sourceIndex >= 0) {
            return pointerState[sourceIndex];
        }

        return PointerState.MAYBE_NULL;
    }

    /*
     * Estimate snapshot demand from control-flow shape.
     *
     * if/else:
     *   - before snapshot
     *   - thenState snapshot
     *   - optional elseState snapshot
     *
     * while:
     *   - before snapshot
     *   - afterOneIteration snapshot
     *
     * function:
     *   - beforeFunction snapshot
     *
     * The estimate is intentionally conservative and capped by
     * MAX_BRANCH_DEPTH for path-sensitive if analysis.
     */
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
            /*
             * 2: before-loop and after-one-iteration snapshots. Each
             * break/continue point additionally captures one snapshot.
             */
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

    // ============================================================
    // Top-Level Dispatch
    // ============================================================

    private boolean checkNode(SemanticASTNode node) {
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
        int beforeFunction = snapshot();

        /*
         * At function entry every pointer state is unknown: parameters carry
         * caller values, globals may have been modified by earlier calls.
         * Local declarations set their own state (NULL for pointer
         * declarations).
         *
         * Exception: a method's `self` parameter is synthesized by the
         * compiler as getptr(receiver) over a storage-backed lvalue, so
         * it is definitionally pointing at entry. Seed it as POINTING so
         * dereferencing `self` without a redundant null guard does not
         * warn. Later assignments (e.g. `self = other`, `self = null`)
         * flow normally through assignment propagation.
         */
        Arrays.fill(pointerState, PointerState.MAYBE_NULL);

        if (node.symbol instanceof MethodSymbol method
                && !method.parameters.isEmpty()) {
            VariableSymbol selfSymbol = method.parameters.get(0);

            if ("self".equals(selfSymbol.name)) {
                int selfIndex = pointerIndexOfSymbol(selfSymbol);

                if (selfIndex >= 0) {
                    pointerState[selfIndex] = PointerState.POINTING;
                }
            }
        }

        /*
         * Exception: the global entry-point `main` in its args form
         * receives `ptr<array<array<byte>>>` from the template wrapper,
         * which always passes a live pointer. Seed it as POINTING so
         * dereferencing the args parameter without a redundant null
         * guard does not warn. Later assignments flow normally through
         * assignment propagation.
         */
        if (!(node.symbol instanceof MethodSymbol)
                && "main".equals(node.symbol.name)
                && node.symbol.namespace == null
                && node.symbol.parameters.size() == 1) {
            int argsIndex = pointerIndexOfSymbol(node.symbol.parameters.get(0));

            if (argsIndex >= 0) {
                pointerState[argsIndex] = PointerState.POINTING;
            }
        }

        if (node.body != null) {
            checkBlock(node.body);
        }

        restore(beforeFunction);
    }

    // ============================================================
    // Statement Checking
    // ============================================================

    private boolean checkStmt(SemanticStmtNode node) {
        if (node == null) {
            return false;
        }

        if (node instanceof SemanticBlockNode n) {
            return checkBlock(n);
        }

        if (node instanceof SemanticVarDeclNode n) {
            checkVarDecl(n);
            return false;
        }

        if (node instanceof SemanticAssignNode n) {
            checkExpr(n.target);
            checkExpr(n.value);

            /*
             * Pointer null-state propagation.
             *
             *   p = null;            -> NULL      (definitely null)
             *   p = getptr(x);      -> POINTING  (the only certification)
             *   p = q;               -> copy q's exact state
             *   p = <anything else>; -> MAYBE_NULL (unknown provenance)
             *
             * Compound assignment never targets an pointer variable (the
             * arithmetic operand rules reject pointer operands), so state
             * changes only through plain `=`. A compound target that is an
             * pointer is already an error; no state is rewritten for it.
             */
            int targetIndex = pointerIndexOf(n.target);

            if (targetIndex >= 0 && !n.isCompound()) {
                pointerState[targetIndex] = pointerStateOfValue(n.value);
            }

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

        if (node instanceof SemanticPrintNode n) {
            for (SemanticExprNode arg : n.args) {
                checkExpr(arg);
            }
            return false;
        }

        if (node instanceof SemanticReturnNode n) {
            checkExpr(n.value);
            return true;
        }

        if (node instanceof SemanticExprStmtNode n) {
            checkExpr(n.expr);
            return false;
        }

        return false;
    }

    private boolean checkBlock(SemanticBlockNode node) {
        int[] blockPointerIndices = new int[Math.max(1, node.statements.size())];
        int blockPointerCount = 0;

        for (SemanticStmtNode stmt : node.statements) {
            if (stmt instanceof SemanticVarDeclNode decl
                    && decl.symbol != null
                    && decl.symbol.type instanceof SemanticPtrType) {
                int index = pointerIndexOfSymbol(decl.symbol);

                if (index >= 0) {
                    blockPointerIndices[blockPointerCount++] = index;
                }
            }

            boolean returned = checkStmt(stmt);

            if (returned) {
                return true;
            }
        }

        /*
         * Pointer variables declared in this block leave scope at block exit.
         * Reset them to MAYBE_NULL so later code cannot inherit their
         * refined state.
         */
        for (int i = 0; i < blockPointerCount; i++) {
            pointerState[blockPointerIndices[i]] = PointerState.MAYBE_NULL;
        }

        return false;
    }

    private void checkVarDecl(SemanticVarDeclNode node) {
        if (node == null || node.symbol == null) {
            return;
        }

        /*
         * Pointer declarations are guaranteed null-initialized: the
         * generated C++ null-initializes the storage, so the variable holds
         * null until an explicit assignment (getptr(...), a certified
         * variable, or null) refines it.
         *
         *   ptr<T> p;             -> p is definitely NULL
         *
         * An explicit initializer lowers to a separate assignment statement,
         * which then refines this state.
         */
        int pointerIndex = pointerIndexOfSymbol(node.symbol);

        if (pointerIndex >= 0) {
            pointerState[pointerIndex] = PointerState.NULL;
        }
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
            refinePointerStatesFromGuard(node.condition, true);
        }

        suppressNullDerefChecks = !thenFeasible;
        boolean thenReturns = checkStmt(node.thenBranch);
        suppressNullDerefChecks = false;

        int thenState = thenFeasible ? snapshot() : -1;

        restore(before);

        boolean elseFeasible = node.elseBranch == null
                || guardPathFeasible(node.condition, false);

        boolean elseReturns = false;
        int elseState = -1;
        int fallthroughState = -1;

        if (node.elseBranch != null) {
            if (elseFeasible) {
                refinePointerStatesFromGuard(node.condition, false);
            }

            suppressNullDerefChecks = !elseFeasible;
            elseReturns = checkStmt(node.elseBranch);
            suppressNullDerefChecks = false;

            elseState = elseFeasible ? snapshot() : -1;
        } else if (elseFeasible) {
            /*
             * With no else branch, the continuing path is the guard-failing
             * path. Capture its refined state so the merge below sees the
             * guard's else-path implications for pointer variables.
             *
             * When the guard-failing path is infeasible (the guard is
             * statically true), no fall-through snapshot is contributed, so
             * only the then-branch state flows onward.
             */
            refinePointerStatesFromGuard(node.condition, false);
            fallthroughState = snapshot();
        }

        restore(before);

        branchDepth--;

        mergeAfterIf(
                before,
                thenState,
                thenReturns || !thenFeasible,
                elseState,
                elseReturns,
                node.elseBranch != null,
                fallthroughState
        );

        return node.elseBranch != null
                && (thenReturns || !thenFeasible)
                && (elseReturns || !elseFeasible);
    }

    /**
     * Fallback when branch nesting exceeds MAX_BRANCH_DEPTH.
     *
     * This remains conservative but stops deeper path splitting. It checks both
     * branches and merges their continuing effects. No guard refinement is
     * applied at the depth cap.
     */
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

    /*
     * Reports whether a guard path is feasible for tracked pointer variables.
     *
     * The guard-holding path of 'p == null' asserts that p is NULL, and the
     * guard-failing path asserts that p is POINTING ('p != null' swaps the
     * two). When the asserted state contradicts the variable's definite
     * current state, that path cannot execute at runtime.
     *
     * Returns false only for such statically infeasible paths; every other
     * condition shape is treated as feasible.
     */
    private boolean guardPathFeasible(
            SemanticExprNode condition,
            boolean guardHolds
    ) {
        if (!(condition instanceof SemanticBinOpNode bin)) {
            return true;
        }

        if (!bin.op.equals("==") && !bin.op.equals("!=")) {
            return true;
        }

        SemanticExprNode varSide;

        if (bin.left instanceof SemanticNullLiteralNode) {
            varSide = bin.right;
        } else if (bin.right instanceof SemanticNullLiteralNode) {
            varSide = bin.left;
        } else {
            return true;
        }

        int index = pointerIndexOf(varSide);

        if (index < 0) {
            return true;
        }

        boolean isEquality = bin.op.equals("==");

        PointerState asserted = (isEquality == guardHolds)
                ? PointerState.NULL
                : PointerState.POINTING;

        PointerState current = pointerState[index];

        return current == PointerState.MAYBE_NULL
                || current == asserted;
    }

    /*
     * Applies guard refinement for a condition of the form:
     *
     *   p == null   /   null == p
     *   p != null   /   null != p
     *
     * where p is a tracked pointer variable.
     *
     * On the path where the guard holds, the asserted state is:
     *
     *   p == null holds  ->  p is NULL
     *   p != null holds  ->  p is POINTING
     *
     * and on the path where the guard fails, the opposite state is asserted.
     *
     * Refinement intersects the asserted state with the variable's current
     * state. When the assertion conflicts with a definite current state the
     * path is infeasible and the current state is kept, which prevents the
     * infeasible path from polluting later merges:
     *
     *   p = getptr(x);        p: POINTING
     *   if (p != null) { }     then-path: POINTING (matches)
     *                          else-path: infeasible, stays POINTING
     *   $(p);              still POINTING, no error
     */
    private void refinePointerStatesFromGuard(
            SemanticExprNode condition,
            boolean guardHolds
    ) {
        if (!(condition instanceof SemanticBinOpNode bin)) {
            return;
        }

        if (!bin.op.equals("==") && !bin.op.equals("!=")) {
            return;
        }

        SemanticExprNode varSide;

        if (bin.left instanceof SemanticNullLiteralNode) {
            varSide = bin.right;
        } else if (bin.right instanceof SemanticNullLiteralNode) {
            varSide = bin.left;
        } else {
            return;
        }

        int index = pointerIndexOf(varSide);

        if (index < 0) {
            return;
        }

        boolean isEquality = bin.op.equals("==");

        PointerState asserted = (isEquality == guardHolds)
                ? PointerState.NULL
                : PointerState.POINTING;

        PointerState current = pointerState[index];

        if (current == PointerState.MAYBE_NULL || current == asserted) {
            pointerState[index] = asserted;
        }
    }

    private boolean checkWhile(SemanticWhileNode node) {
        checkExpr(node.condition);

        int before = snapshot();

        /*
         * Loop-condition guard feasibility: when the guard is statically
         * false for every tracked variable (for example `while (p != null)`
         * with p definitely null), the body never executes.
         */
        boolean bodyFeasible = guardPathFeasible(node.condition, true);

        LoopFrame frame = new LoopFrame();
        loopStack.add(frame);

        boolean bodyTerminates = false;
        int afterBody = -1;

        if (bodyFeasible) {
            /*
             * Loop-condition guard refinement: inside the body the condition
             * holds, so `p != null` certifies p as POINTING for the body.
             */
            refinePointerStatesFromGuard(node.condition, true);

            bodyTerminates = checkStmt(node.body);
            afterBody = snapshot();
        } else {
            /*
             * The body never executes. Check it once with null-deref
             * diagnostics suppressed so unrelated errors still surface, then
             * discard its state effects.
             */
            suppressNullDerefChecks = true;
            checkStmt(node.body);
            suppressNullDerefChecks = false;
        }

        /*
         * Back-edge states: every state from which control re-tests the
         * loop condition.
         *
         *   - the body (and epilogue) fell through,
         *   - each continue point (which jumps to just before the
         *     epilogue, so the epilogue runs next).
         *
         * Break states are NOT back-edge states: they jump past the loop.
         */
        List<Integer> backEdgeStates = new ArrayList<>();

        if (bodyFeasible) {
            if (!node.update.isEmpty()) {
                boolean iterationFallsThrough = !bodyTerminates;

                if (iterationFallsThrough) {
                    for (SemanticStmtNode updateStmt : node.update) {
                        if (checkStmt(updateStmt)) {
                            iterationFallsThrough = false;
                            break;
                        }
                    }

                    if (iterationFallsThrough) {
                        backEdgeStates.add(snapshot());
                    }
                }

                /*
                 * A continue jumps to the continue label placed just before
                 * the epilogue, so the epilogue runs on every continue path
                 * as well. Re-run it from each captured continue state.
                 */
                for (int continueState : frame.continueStates) {
                    restore(continueState);

                    boolean continueFallsThrough = true;

                    for (SemanticStmtNode updateStmt : node.update) {
                        if (checkStmt(updateStmt)) {
                            continueFallsThrough = false;
                            break;
                        }
                    }

                    if (continueFallsThrough) {
                        backEdgeStates.add(snapshot());
                    }
                }
            } else {
                if (!bodyTerminates) {
                    backEdgeStates.add(afterBody);
                }

                backEdgeStates.addAll(frame.continueStates);
            }
        } else {
            /*
             * The body never executes, so neither does the epilogue.
             * Check it once with diagnostics suppressed.
             */
            suppressNullDerefChecks = true;

            for (SemanticStmtNode updateStmt : node.update) {
                checkStmt(updateStmt);
            }

            suppressNullDerefChecks = false;
        }

        loopStack.remove(loopStack.size() - 1);

        /*
         * A while loop may execute zero times. Therefore the state after the
         * loop is a merge of:
         *
         *   - before loop,
         *   - every state that flows around the back edge.
         *
         * We do not attempt to prove loop termination.
         */
        restore(before);

        if (bodyFeasible && !backEdgeStates.isEmpty()) {
            mergeLoopStates(before, backEdgeStates);
        }

        if (!bodyFeasible || !bodyTerminates || !backEdgeStates.isEmpty()) {
            /*
             * Continuing code runs only when the condition failed, so the
             * guard-failing refinement holds after the loop for every path
             * that re-tested the condition. For `while (p != null)`, this
             * proves p is null on loop exit.
             *
             * Break paths do NOT re-test the condition, so their states are
             * merged in AFTER this refinement.
             */
            refinePointerStatesFromGuard(node.condition, false);
        }

        if (bodyFeasible) {
            for (int breakState : frame.breakStates) {
                mergeStateIntoCurrent(breakState);
            }
        }

        return false;
    }

    /*
     * Records the current state as the target of a break/continue jump.
     *
     * levels is 1-based: `break` / `break 1` / continue target the
     * innermost enclosing loop, `break 2` the one outside it, and so on.
     */
    private void recordJumpState(int levels, boolean isContinue) {
        if (loopStack.isEmpty()) {
            /* Rejected earlier by semantic analysis; defensive no-op. */
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

    /*
     * Merges the given iteration-end states with the before-loop state
     * into the current state (the loop-exit merge).
     */
    private void mergeLoopStates(int before, List<Integer> iterationStates) {
        for (int i = 0; i < pointerCount; i++) {
            PointerState merged =
                    mergePointerStateValue(
                            null,
                            pointerStateFromSnapshot(before, i, before)
                    );

            for (int slot : iterationStates) {
                merged = mergePointerStateValue(
                        merged,
                        pointerStateFromSnapshot(slot, i, before)
                );
            }

            pointerState[i] = merged == null
                    ? PointerState.MAYBE_NULL
                    : merged;
        }
    }

    /*
     * Merges the given break-exit states into the current state.
     */
    private void mergeStateIntoCurrent(int slot) {
        for (int i = 0; i < pointerCount; i++) {
            pointerState[i] = mergePointerStateValue(
                    pointerState[i],
                    pointerStateFromSnapshot(slot, i, slot)
            );
        }
    }

    // ============================================================
    // Expression Checking
    // ============================================================

    private void checkExpr(SemanticExprNode expr) {
        if (expr == null) {
            return;
        }

        if (expr instanceof SemanticIntLiteralNode) {
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
            return;
        }

        if (expr instanceof SemanticArrayIntrinsicCallNode n) {
            checkExpr(n.receiver);

            for (SemanticExprNode arg : n.args) {
                checkExpr(arg);
            }

            return;
        }

        if (expr instanceof SemanticGetPtrNode n) {
            checkExpr(n.target);
            return;
        }

        if (expr instanceof SemanticDerefNode n) {
            /*
             * Null dereference checking: a deref of an pointer variable in
             * the NULL or MAYBE_NULL state is an error.
             *
             * Suppressed while checking code that cannot execute; see the
             * field comment on suppressNullDerefChecks.
             */
            if (!suppressNullDerefChecks) {
                int targetIndex = pointerIndexOf(n.expr);

                if (targetIndex >= 0) {
                    PointerState current = pointerState[targetIndex];
                    String varName = varNameOf(n.expr);

                    if (current == PointerState.NULL) {
                        diagnostics.error("Cannot dereference null variable '"
                                + varName
                                + "'");
                    } else if (current == PointerState.MAYBE_NULL) {
                        /*
                         * A maybe-null dereference is a warning, not an
                         * error. It means the variable is null on some
                         * continuing path, which is not statically provable
                         * to be the executing path.
                         *
                         * Each variable warns at most once per check; see
                         * the field comment on warnedMaybeNullVars.
                         */
                        VariableSymbol targetSymbol =
                                symbolOf(n.expr);

                        if (targetSymbol != null
                                && warnedMaybeNullVars.add(targetSymbol)) {
                            diagnostics.warning("Deref of possibly null variable '"
                                    + varName
                                    + "'");
                        }
                    }
                }
            }

            checkExpr(n.expr);
        }

        if (expr instanceof SemanticCastExprNode n) {
            checkExpr(n.expr);
        }
    }

    // ============================================================
    // State Helpers
    // ============================================================

    private int snapshot() {
        ensureSnapshotCapacity();

        int slot = snapshotCount++;

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
                pointerSnapshots[snapshotSlot],
                0,
                pointerState,
                0,
                pointerCount
        );
    }

    private void ensureSnapshotCapacity() {
        if (snapshotCount < pointerSnapshots.length) {
            return;
        }

        int oldLength = pointerSnapshots.length;
        int newLength = Math.max(16, oldLength * 2);

        pointerSnapshots = Arrays.copyOf(pointerSnapshots, newLength);

        for (int i = oldLength; i < newLength; i++) {
            pointerSnapshots[i] = new PointerState[pointerCount];
            Arrays.fill(pointerSnapshots[i], PointerState.MAYBE_NULL);
        }
    }

    private void mergeAfterIf(
            int before,
            int thenState,
            boolean thenReturns,
            int elseState,
            boolean elseReturns,
            boolean hasElse,
            int fallthroughState
    ) {
        for (int i = 0; i < pointerCount; i++) {
            boolean hasContinuingPath = false;
            PointerState merged = null;

            if (!thenReturns) {
                hasContinuingPath = true;
                merged = mergePointerStateValue(
                        merged,
                        pointerStateFromSnapshot(thenState, i, before)
                );
            }

            if (hasElse) {
                if (!elseReturns) {
                    hasContinuingPath = true;
                    merged = mergePointerStateValue(
                            merged,
                            pointerStateFromSnapshot(elseState, i, before)
                    );
                }
            } else if (fallthroughState >= 0) {
                hasContinuingPath = true;
                merged = mergePointerStateValue(
                        merged,
                        pointerStateFromSnapshot(fallthroughState, i, before)
                );
            }

            if (hasContinuingPath && merged != null) {
                pointerState[i] = merged;
            } else {
                pointerState[i] = pointerStateFromSnapshot(before, i, before);
            }
        }
    }

    private PointerState pointerStateFromSnapshot(
            int snapshotSlot,
            int index,
            int fallbackSlot
    ) {
        if (index < 0 || index >= pointerCount) {
            return PointerState.MAYBE_NULL;
        }

        if (snapshotSlot >= 0
                && snapshotSlot < snapshotCount
                && pointerSnapshots[snapshotSlot] != null) {
            PointerState value = pointerSnapshots[snapshotSlot][index];

            if (value != null) {
                return value;
            }
        }

        if (fallbackSlot >= 0
                && fallbackSlot < snapshotCount
                && pointerSnapshots[fallbackSlot] != null) {
            PointerState fallback = pointerSnapshots[fallbackSlot][index];

            if (fallback != null) {
                return fallback;
            }
        }

        return PointerState.MAYBE_NULL;
    }

    private PointerState mergePointerStateValue(
            PointerState left,
            PointerState right
    ) {
        if (right == null) {
            right = PointerState.MAYBE_NULL;
        }

        if (left == null) {
            return right;
        }

        if (left == right) {
            return left;
        }

        return PointerState.MAYBE_NULL;
    }
}
