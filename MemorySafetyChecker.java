import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * ============================================================================
 * MEMORY SAFETY CHECKER
 * ============================================================================
 *
 * PURPOSE:
 * MemorySafetyChecker validates Franko heap-memory lifetime rules over the
 * fully lowered, legality-checked Semantic AST.
 *
 * This pass owns transient memory-state analysis. VariableSymbol must not store
 * deleted/free/use-after-free state.
 *
 * Rules checked:
 *
 *   - del target must be heap-allocated,
 *   - double free is invalid,
 *   - possible double free after branch merges is invalid,
 *   - use after free is invalid,
 *   - possible use after free after branch merges is invalid,
 *   - heap variables must be deleted before leaving their scope,
 *   - heap variables must be deleted before returning from a function,
 *   - deletion state from branches that definitely return does not flow into
 *     following code,
 *   - branch-sensitive analysis is limited to MAX_BRANCH_DEPTH,
 *   - null is only valid for address types; integer 0 is not null,
 *   - address declarations are guaranteed null-initialized,
 *   - dereferencing a NULL address variable is an error,
 *   - dereferencing a MAYBE_NULL address variable is a warning,
 *   - only getaddr(...) certifies an address variable as POINTING; an
 *     assignment from another address variable copies that variable's exact
 *     state, so POINTING propagates only through certified sources,
 *   - address state is tracked in a separate matrix from heap memory state,
 *     path-sensitively up to MAX_BRANCH_DEPTH, with guard refinement for
 *     'p == null' and 'p != null' conditions,
 *   - at function entry every tracked address variable is MAYBE_NULL;
 *     declarations and assignments refine it.
 *
 * This checker is intentionally intra-procedural. It does not try to infer
 * whether called functions delete memory through aliases. In current Franko,
 * del is symbol-based, so memory ownership remains local to the heap variable.
 *
 * KNOWN LIMITATION (deferred to a later change):
 *
 *   Dereferencing an address whose pointee was freed by del cannot be caught
 *   at compile time by this checker. del is symbol-based, so an address may
 *   be copied into another variable (or a parameter) before the pointee is
 *   deleted; the copy keeps its POINTING state while the pointee is already
 *   gone. A runtime dangling/null check is planned as a later change and is
 *   intentionally not part of this checker.
 */
public final class MemorySafetyChecker {
    private static final int MAX_BRANCH_DEPTH = 10;

    /*
     * Memory states for heap variables.
     *
     * UNTRACKED:
     *   Internal state used for heap variables that exist in the Semantic AST
     *   but are not currently live in the current function/block traversal.
     *
     * ALIVE:
     *   The heap variable is allocated and not deleted on the current path.
     *
     * DELETED:
     *   The heap variable is definitely deleted on the current path.
     *
     * MAYBE_DELETED:
     *   The heap variable is deleted on some continuing paths and alive on
     *   others. Uses and deletes are therefore unsafe because one path may be
     *   use-after-free or double-free.
     */
    private enum MemoryState {
        UNTRACKED,
        ALIVE,
        DELETED,
        MAYBE_DELETED
    }

    /*
     * Address-state matrix for address-typed variables.
     *
     * This is a separate matrix from the heap memory-state matrix above.
     * A symbol can appear in both matrices (heap variables of address type
     * participate in both).
     *
     * POINTING:
     *   The variable is certified to hold a real storage address on the
     *   current path.
     *
     *   The only expression that certifies POINTING is getaddr(...).
     *   Assignment from another variable copies that variable's state, so
     *   POINTING propagates only transitively through certified sources.
     *
     * NULL:
     *   The variable is definitely null on the current path. Address
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
    private enum AddressState {
        POINTING,
        NULL,
        MAYBE_NULL
    }

    private final DiagnosticBag diagnostics;

    /*
     * Symbol indexing.
     *
     * The checker uses arrays for active memory state and snapshots. A small
     * identity map is used only to translate a VariableSymbol identity into a
     * stable array index.
     */
    private final IdentityHashMap<VariableSymbol, Integer> heapIndexBySymbol =
            new IdentityHashMap<>();

    private VariableSymbol[] heapSymbols = new VariableSymbol[0];
    private int heapCount = 0;

    /*
     * Active memory state.
     *
     * state[i] is the memory state for heapSymbols[i].
     */
    private MemoryState[] state = new MemoryState[0];

    /*
     * Address symbol indexing.
     *
     * The address matrix is indexed independently of the heap matrix so
     * that non-heap address variables are fully trackable.
     */
    private final IdentityHashMap<VariableSymbol, Integer> addrIndexBySymbol =
            new IdentityHashMap<>();

    private VariableSymbol[] addrSymbols = new VariableSymbol[0];
    private int addrCount = 0;

    /*
     * Active address state.
     *
     * addrState[i] is the address state for addrSymbols[i].
     */
    private AddressState[] addrState = new AddressState[0];

    /*
     * Preallocated snapshot storage.
     *
     * snapshots[k] is a full copy of state at some control-flow point.
     * Snapshot capacity is estimated from the number of if/else structures,
     * nested branch depth, loops, and functions.
     */
    private MemoryState[][] snapshots = new MemoryState[0][];
    private int snapshotCount = 0;

    /*
     * Preallocated address-state snapshot storage.
     *
     * addrSnapshots[k] shares slot indices with snapshots[k].
     */
    private AddressState[][] addrSnapshots = new AddressState[0][];

    private int branchDepth = 0;

    /*
     * Suppresses null-deref diagnostics while checking code that cannot
     * execute:
     *
     *   - the second abstract while-body pass, where a deref that is invalid
     *     only because of a state carried across an infeasible extra
     *     iteration would otherwise produce a false positive (any deref that
     *     is invalid within a single body execution is already reported in
     *     the first pass),
     *
     *   - bodies of statically infeasible guard paths, where the guard's
     *     assertion contradicts a definite current state.
     */
    private boolean suppressNullDerefChecks = false;

    /*
     * Address variables that have already produced a maybe-null deref
     * warning.
     *
     * Path-sensitive exploration revisits the same deref site once per path
     * through ancestor branches, and desugared array initializers duplicate
     * a single source-level deref into one deref node per element. To keep
     * the warning volume bounded and useful, each address variable warns at
     * most once per check.
     */
    private final Set<VariableSymbol> warnedMaybeNullVars =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public MemorySafetyChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public void check(SemanticASTNode node) {
        diagnostics.clear();

        prepareAnalysis(node);

        branchDepth = 0;
        snapshotCount = 0;

        Arrays.fill(state, MemoryState.UNTRACKED);
        Arrays.fill(addrState, AddressState.MAYBE_NULL);

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
        heapIndexBySymbol.clear();
        heapSymbols = new VariableSymbol[16];
        heapCount = 0;

        addrIndexBySymbol.clear();
        addrSymbols = new VariableSymbol[16];
        addrCount = 0;

        collectHeapSymbols(root);

        heapSymbols = Arrays.copyOf(heapSymbols, heapCount);
        addrSymbols = Arrays.copyOf(addrSymbols, addrCount);

        state = new MemoryState[heapCount];
        Arrays.fill(state, MemoryState.UNTRACKED);

        addrState = new AddressState[addrCount];
        Arrays.fill(addrState, AddressState.MAYBE_NULL);

        int estimatedSnapshots =
                Math.max(2*MAX_BRANCH_DEPTH, estimateSnapshotDemand(root, 0) + MAX_BRANCH_DEPTH);

        snapshots = new MemoryState[estimatedSnapshots][heapCount];
        addrSnapshots = new AddressState[estimatedSnapshots][addrCount];

        for (int i = 0; i < snapshots.length; i++) {
            Arrays.fill(snapshots[i], MemoryState.UNTRACKED);
            Arrays.fill(addrSnapshots[i], AddressState.MAYBE_NULL);
        }
    }

    private void collectHeapSymbols(SemanticASTNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticProgramNode n) {
            for (SemanticASTNode item : n.topLevelItems) {
                collectHeapSymbols(item);
            }
            return;
        }

        if (node instanceof SemanticFunctionDeclNode n) {
            collectHeapSymbols(n.body);
            return;
        }

        if (node instanceof SemanticBlockNode n) {
            for (SemanticStmtNode stmt : n.statements) {
                collectHeapSymbols(stmt);
            }
            return;
        }

        if (node instanceof SemanticVarDeclNode n) {
            addTrackedSymbolIfNeeded(n.symbol);
            return;
        }

        if (node instanceof SemanticAssignNode n) {
            collectHeapSymbolsFromExpr(n.target);
            collectHeapSymbolsFromExpr(n.value);
            return;
        }

        if (node instanceof SemanticIfNode n) {
            collectHeapSymbolsFromExpr(n.condition);
            collectHeapSymbols(n.thenBranch);
            collectHeapSymbols(n.elseBranch);
            return;
        }

        if (node instanceof SemanticWhileNode n) {
            collectHeapSymbolsFromExpr(n.condition);
            collectHeapSymbols(n.body);
            return;
        }

        if (node instanceof SemanticDelNode n) {
            addTrackedSymbolIfNeeded(n.symbol);
            return;
        }

        if (node instanceof SemanticPrintNode n) {
            for (SemanticExprNode arg : n.args) {
                collectHeapSymbolsFromExpr(arg);
            }
            return;
        }

        if (node instanceof SemanticReturnNode n) {
            collectHeapSymbolsFromExpr(n.value);
            return;
        }

        if (node instanceof SemanticExprStmtNode n) {
            collectHeapSymbolsFromExpr(n.expr);
            return;
        }

        if (node instanceof SemanticExprNode expr) {
            collectHeapSymbolsFromExpr(expr);
        }
    }

    private void collectHeapSymbolsFromExpr(SemanticExprNode expr) {
        if (expr == null) {
            return;
        }

        if (expr instanceof SemanticIntLiteralNode) {
            return;
        }

        if (expr instanceof SemanticVarExprNode n) {
            addTrackedSymbolIfNeeded(n.symbol);
            return;
        }

        if (expr instanceof SemanticUnaryOpNode n) {
            collectHeapSymbolsFromExpr(n.expr);
            return;
        }

        if (expr instanceof SemanticBinOpNode n) {
            collectHeapSymbolsFromExpr(n.left);
            collectHeapSymbolsFromExpr(n.right);
            return;
        }

        if (expr instanceof SemanticArrayAccessNode n) {
            collectHeapSymbolsFromExpr(n.target);
            collectHeapSymbolsFromExpr(n.index);
            return;
        }

        if (expr instanceof SemanticMemberAccessNode n) {
            collectHeapSymbolsFromExpr(n.target);
            return;
        }

        if (expr instanceof SemanticFunctionCallNode n) {
            for (SemanticExprNode arg : n.args) {
                collectHeapSymbolsFromExpr(arg);
            }
            return;
        }

        if (expr instanceof SemanticArrayIntrinsicCallNode n) {
            collectHeapSymbolsFromExpr(n.receiver);

            for (SemanticExprNode arg : n.args) {
                collectHeapSymbolsFromExpr(arg);
            }

            return;
        }

        if (expr instanceof SemanticGetAddrNode n) {
            collectHeapSymbolsFromExpr(n.target);
            return;
        }

        if (expr instanceof SemanticDerefNode n) {
            collectHeapSymbolsFromExpr(n.expr);
        }
    }

    private void addHeapSymbolIfNeeded(VariableSymbol symbol) {
        if (symbol == null || !symbol.isHeap) {
            return;
        }

        if (heapIndexBySymbol.containsKey(symbol)) {
            return;
        }

        if (heapCount == heapSymbols.length) {
            heapSymbols = Arrays.copyOf(
                    heapSymbols,
                    Math.max(4, heapSymbols.length * 2)
            );
        }

        heapIndexBySymbol.put(symbol, heapCount);
        heapSymbols[heapCount] = symbol;
        heapCount++;
    }

    /*
     * Registers a symbol in both tracking matrices when applicable.
     *
     * Heap-allocated symbols join the heap memory-state matrix; address-typed
     * symbols join the address null-state matrix. A symbol may join both.
     */
    private void addTrackedSymbolIfNeeded(VariableSymbol symbol) {
        addHeapSymbolIfNeeded(symbol);
        addAddressSymbolIfNeeded(symbol);
    }

    private void addAddressSymbolIfNeeded(VariableSymbol symbol) {
        if (symbol == null
                || !(symbol.type instanceof SemanticAddrType)) {
            return;
        }

        if (addrIndexBySymbol.containsKey(symbol)) {
            return;
        }

        if (addrCount == addrSymbols.length) {
            addrSymbols = Arrays.copyOf(
                    addrSymbols,
                    Math.max(4, addrSymbols.length * 2)
            );
        }

        addrIndexBySymbol.put(symbol, addrCount);
        addrSymbols[addrCount] = symbol;
        addrCount++;
    }

    /*
     * Resolves a SemanticExprNode to the address-matrix index of the plain
     * address-typed variable it denotes.
     *
     * Only plain variable expressions with an address type are tracked.
     * Other shapes (array elements, struct fields, deref chains, call
     * results) are not tracked.
     *
     * Returns the address-matrix index, or -1 when not trackable.
     */
    private int addrIndexOf(SemanticExprNode expr) {
        if (!(expr instanceof SemanticVarExprNode n)) {
            return -1;
        }

        return addrIndexOfSymbol(n.symbol);
    }

    private int addrIndexOfSymbol(VariableSymbol symbol) {
        if (symbol == null
                || !(symbol.type instanceof SemanticAddrType)) {
            return -1;
        }

        Integer index = addrIndexBySymbol.get(symbol);

        return index == null ? -1 : index;
    }

    /*
     * Human-readable name for diagnostics about an address expression.
     */
    private String varNameOf(SemanticExprNode expr) {
        if (expr instanceof SemanticVarExprNode n
                && n.symbol != null) {
            return n.symbol.name;
        }

        return "<expression>";
    }

    /*
     * Resolves the tracked variable symbol denoted by an address expression,
     * or null when the expression is not a plain variable.
     */
    private VariableSymbol symbolOf(SemanticExprNode expr) {
        if (expr instanceof SemanticVarExprNode n) {
            return n.symbol;
        }

        return null;
    }

    /*
     * Computes the address state a variable takes when assigned the given
     * value expression.
     *
     *   null literal         -> NULL      (definitely null)
     *   getaddr(...)         -> POINTING  (the only certification of a
     *                                         real storage address)
     *   address variable     -> copy that variable's exact state:
     *
     *                             addr<int> b = null;
     *                             addr<int> a = b;   a: NULL, not MAYBE_NULL
     *
     *                         A variable only becomes POINTING when it was
     *                         itself certified, directly by getaddr or
     *                         transitively through certified variables.
     *
     *   anything else        -> MAYBE_NULL (unknown provenance: call results,
     *                          parameters, member/array reads, deref chains)
     */
    private AddressState addrStateOfValue(SemanticExprNode value) {
        if (value instanceof SemanticNullLiteralNode) {
            return AddressState.NULL;
        }

        if (value instanceof SemanticGetAddrNode) {
            return AddressState.POINTING;
        }

        int sourceIndex = addrIndexOf(value);

        if (sourceIndex >= 0) {
            return addrState[sourceIndex];
        }

        return AddressState.MAYBE_NULL;
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

        if (node instanceof SemanticFunctionDeclNode n) {
            return 1 + estimateSnapshotDemand(n.body, depth);
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
            return 2 + estimateSnapshotDemand(n.body, depth);
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

        if (node instanceof SemanticFunctionDeclNode n) {
            checkFunction(n);
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

        Arrays.fill(state, MemoryState.UNTRACKED);

        /*
         * At function entry every address state is unknown: parameters carry
         * caller values, globals may have been modified by earlier calls.
         * Local declarations set their own state (NULL for plain address
         * declarations, POINTING for alloc'd addresses).
         */
        Arrays.fill(addrState, AddressState.MAYBE_NULL);

        if (node.body != null) {
            boolean definitelyReturns = checkBlock(node.body, true);

            /*
             * If the function can fall through normally, checkBlock(..., true)
             * already reports leaks for function-scope heap variables at the
             * end of the body.
             *
             * If the function definitely returns, return checking reports live
             * heap variables at each return path.
             */
            if (!definitelyReturns) {
                // Nothing extra needed. checkBlock handled normal body exit.
            }
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
            return checkBlock(n, false);
        }

        if (node instanceof SemanticVarDeclNode n) {
            checkVarDecl(n);
            return false;
        }

        if (node instanceof SemanticAssignNode n) {
            checkExpr(n.target);
            checkExpr(n.value);

            /*
             * Address null-state propagation.
             *
             *   p = null;            -> NULL      (definitely null)
             *   p = getaddr(x);      -> POINTING  (the only certification)
             *   p = q;               -> copy q's exact state
             *   p = <anything else>; -> MAYBE_NULL (unknown provenance)
             */
            int targetIndex = addrIndexOf(n.target);

            if (targetIndex >= 0) {
                addrState[targetIndex] = addrStateOfValue(n.value);
            }

            return false;
        }

        if (node instanceof SemanticIfNode n) {
            return checkIf(n);
        }

        if (node instanceof SemanticWhileNode n) {
            return checkWhile(n);
        }

        if (node instanceof SemanticDelNode n) {
            checkDel(n);
            return false;
        }

        if (node instanceof SemanticPrintNode n) {
            for (SemanticExprNode arg : n.args) {
                checkExpr(arg);
            }
            return false;
        }

        if (node instanceof SemanticReturnNode n) {
            checkExpr(n.value);
            reportLiveHeapVariables("return statement");
            return true;
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
        int[] blockHeapIndices = new int[Math.max(1, node.statements.size())];
        int blockHeapCount = 0;

        int[] blockAddrIndices = new int[Math.max(1, node.statements.size())];
        int blockAddrCount = 0;

        for (SemanticStmtNode stmt : node.statements) {
            if (stmt instanceof SemanticVarDeclNode decl
                    && decl.symbol != null) {
                if (decl.symbol.isHeap) {
                    int index = heapIndexOf(decl.symbol);

                    if (index >= 0) {
                        blockHeapIndices[blockHeapCount++] = index;
                    }
                }

                if (decl.symbol.type instanceof SemanticAddrType) {
                    int index = addrIndexOfSymbol(decl.symbol);

                    if (index >= 0) {
                        blockAddrIndices[blockAddrCount++] = index;
                    }
                }
            }

            boolean returned = checkStmt(stmt);

            if (returned) {
                return true;
            }
        }

        /*
         * Normal block exit.
         *
         * Heap variables declared in this block must be deleted before the block
         * exits normally, otherwise their pointer variable goes out of scope and
         * the heap object leaks.
         *
         * For function bodies, this also catches heap variables that remain live
         * at normal function fallthrough.
         */
        for (int i = 0; i < blockHeapCount; i++) {
            int index = blockHeapIndices[i];
            VariableSymbol symbol = heapSymbols[index];

            if (isAliveOrMaybeDeleted(index)) {
                diagnostics.error("Heap variable '"
                        + symbol.name
                        + "' may leak at end of "
                        + (functionBody ? "function body" : "scope")
                        + "; missing del");
            }

            state[index] = MemoryState.UNTRACKED;
        }

        /*
         * Address variables declared in this block leave scope at block exit.
         * Reset them to MAYBE_NULL so later code cannot inherit their
         * refined state.
         */
        for (int i = 0; i < blockAddrCount; i++) {
            addrState[blockAddrIndices[i]] = AddressState.MAYBE_NULL;
        }

        return false;
    }

    private void checkVarDecl(SemanticVarDeclNode node) {
        if (node == null || node.symbol == null) {
            return;
        }

        if (node.symbol.isHeap) {
            int index = heapIndexOf(node.symbol);

            if (index >= 0) {
                state[index] = MemoryState.ALIVE;
            }
        }

        /*
         * Address declarations are guaranteed null-initialized.
         *
         *   addr<T> p;             -> p is definitely NULL
         *   alloc addr<T> p;       -> p points at freshly allocated storage
         *
         * An explicit initializer lowers to a separate assignment statement,
         * which then refines this state.
         */
        int addrIndex = addrIndexOfSymbol(node.symbol);

        if (addrIndex >= 0) {
            addrState[addrIndex] = node.symbol.isHeap
                    ? AddressState.POINTING
                    : AddressState.NULL;
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
            refineAddrStatesFromGuard(node.condition, true);
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
                refineAddrStatesFromGuard(node.condition, false);
            }

            suppressNullDerefChecks = !elseFeasible;
            elseReturns = checkStmt(node.elseBranch);
            suppressNullDerefChecks = false;

            elseState = elseFeasible ? snapshot() : -1;
        } else if (elseFeasible) {
            /*
             * With no else branch, the continuing path is the guard-failing
             * path. Capture its refined state so the merge below sees the
             * guard's else-path implications for address variables.
             *
             * When the guard-failing path is infeasible (the guard is
             * statically true), no fall-through snapshot is contributed, so
             * only the then-branch state flows onward.
             */
            refineAddrStatesFromGuard(node.condition, false);
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
     * Reports whether a guard path is feasible for tracked address variables.
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

        int index = addrIndexOf(varSide);

        if (index < 0) {
            return true;
        }

        boolean isEquality = bin.op.equals("==");

        AddressState asserted = (isEquality == guardHolds)
                ? AddressState.NULL
                : AddressState.POINTING;

        AddressState current = addrState[index];

        return current == AddressState.MAYBE_NULL
                || current == asserted;
    }

    /*
     * Applies guard refinement for a condition of the form:
     *
     *   p == null   /   null == p
     *   p != null   /   null != p
     *
     * where p is a tracked address variable.
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
     *   p = getaddr(x);        p: POINTING
     *   if (p != null) { }     then-path: POINTING (matches)
     *                          else-path: infeasible, stays POINTING
     *   deref(p);              still POINTING, no error
     */
    private void refineAddrStatesFromGuard(
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

        int index = addrIndexOf(varSide);

        if (index < 0) {
            return;
        }

        boolean isEquality = bin.op.equals("==");

        AddressState asserted = (isEquality == guardHolds)
                ? AddressState.NULL
                : AddressState.POINTING;

        AddressState current = addrState[index];

        if (current == AddressState.MAYBE_NULL || current == asserted) {
            addrState[index] = asserted;
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

        boolean bodyReturns = false;
        int afterOneIteration = -1;

        if (bodyFeasible) {
            /*
             * Loop-condition guard refinement: inside the body the condition
             * holds, so `p != null` certifies p as POINTING for the body.
             */
            refineAddrStatesFromGuard(node.condition, true);

            /*
             * Check one body execution for internal memory errors.
             */
            bodyReturns = checkStmt(node.body);
            afterOneIteration = snapshot();

            /*
             * Check a second body execution from the post-body state. This
             * catches simple loop double-frees such as:
             *
             *   while (cond) {
             *       del x
             *   }
             *
             * because the second abstract iteration starts with x deleted.
             *
             * Null-deref diagnostics are suppressed in this pass; see the
             * field comment on suppressNullDerefChecks.
             */
            if (!bodyReturns) {
                restore(afterOneIteration);
                refineAddrStatesFromGuard(node.condition, true);

                suppressNullDerefChecks = true;
                checkStmt(node.body);
                suppressNullDerefChecks = false;
            }
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
         * A while loop may execute zero times. Therefore the state after the
         * loop is a merge of:
         *
         *   - before loop,
         *   - after one continuing body execution.
         *
         * We do not attempt to prove loop termination.
         */
        restore(before);

        if (bodyFeasible && !bodyReturns) {
            mergeLoopState(before, afterOneIteration);
        }

        if (!bodyFeasible || !bodyReturns) {
            /*
             * Continuing code runs only when the condition failed, so the
             * guard-failing refinement holds after the loop. For
             * `while (p != null)`, this proves p is null on loop exit.
             */
            refineAddrStatesFromGuard(node.condition, false);
        }

        return false;
    }

    private void checkDel(SemanticDelNode node) {
        VariableSymbol symbol = node.symbol;

        if (symbol == null) {
            diagnostics.error("Cannot delete null symbol");
            return;
        }

        if (!symbol.isHeap) {
            diagnostics.error("Cannot delete non-heap variable '"
                    + symbol.name
                    + "'");
            return;
        }

        int index = heapIndexOf(symbol);

        if (index < 0) {
            return;
        }

        MemoryState current = stateOf(index);

        if (current == MemoryState.DELETED) {
            diagnostics.error("Variable '"
                    + symbol.name
                    + "' has already been deleted");
            return;
        }

        if (current == MemoryState.MAYBE_DELETED) {
            diagnostics.error("Variable '"
                    + symbol.name
                    + "' may have already been deleted");
            state[index] = MemoryState.DELETED;
            return;
        }

        state[index] = MemoryState.DELETED;

        /*
         * Codegen sets a deleted heap variable to nullptr, so an address-typed
         * heap variable is definitely null after del.
         */
        int addrIndex = addrIndexOfSymbol(symbol);

        if (addrIndex >= 0) {
            addrState[addrIndex] = AddressState.NULL;
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

        if (expr instanceof SemanticVarExprNode n) {
            checkVarUse(n.symbol);
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

        if (expr instanceof SemanticGetAddrNode n) {
            checkExpr(n.target);
            return;
        }

        if (expr instanceof SemanticDerefNode n) {
            /*
             * Null dereference checking: a deref of an address variable in
             * the NULL or MAYBE_NULL state is an error.
             *
             * Suppressed during the loop re-check pass; see the field
             * comment on inLoopRecheckPass.
             */
            if (!suppressNullDerefChecks) {
                int targetIndex = addrIndexOf(n.expr);

                if (targetIndex >= 0) {
                    AddressState current = addrState[targetIndex];
                    String varName = varNameOf(n.expr);

                    if (current == AddressState.NULL) {
                        diagnostics.error("Cannot dereference null variable '"
                                + varName
                                + "'");
                    } else if (current == AddressState.MAYBE_NULL) {
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
    }

    private void checkVarUse(VariableSymbol symbol) {
        if (symbol == null || !symbol.isHeap) {
            return;
        }

        int index = heapIndexOf(symbol);

        if (index < 0) {
            return;
        }

        MemoryState current = stateOf(index);

        if (current == MemoryState.DELETED) {
            diagnostics.error("Use of deleted variable '"
                    + symbol.name
                    + "'");
        } else if (current == MemoryState.MAYBE_DELETED) {
            diagnostics.error("Use of possibly deleted variable '"
                    + symbol.name
                    + "'");
        }
    }

    // ============================================================
    // Leak Checking
    // ============================================================

    private void reportLiveHeapVariables(String where) {
        for (int i = 0; i < heapCount; i++) {
            VariableSymbol symbol = heapSymbols[i];

            if (symbol == null || !symbol.isHeap) {
                continue;
            }

            MemoryState current = state[i];

            if (current == MemoryState.ALIVE) {
                diagnostics.error("Heap variable '"
                        + symbol.name
                        + "' leaks at "
                        + where
                        + "; missing del");
            } else if (current == MemoryState.MAYBE_DELETED) {
                diagnostics.error("Heap variable '"
                        + symbol.name
                        + "' may leak at "
                        + where
                        + "; missing del on some path");
            }
        }
    }

    private boolean isAliveOrMaybeDeleted(int index) {
        MemoryState current = stateOf(index);
        return current == MemoryState.ALIVE
                || current == MemoryState.MAYBE_DELETED;
    }

    // ============================================================
    // State Helpers
    // ============================================================

    private int heapIndexOf(VariableSymbol symbol) {
        Integer index = heapIndexBySymbol.get(symbol);
        return index == null ? -1 : index;
    }

    private MemoryState stateOf(int index) {
        if (index < 0 || index >= heapCount) {
            return MemoryState.ALIVE;
        }

        MemoryState value = state[index];

        if (value == null || value == MemoryState.UNTRACKED) {
            /*
             * Heap variables should normally enter the active state at their
             * declaration.
             *
             * If a heap symbol appears without a declaration in this traversal,
             * treat it as alive so memory errors are not hidden.
             */
            return MemoryState.ALIVE;
        }

        return value;
    }

    private int snapshot() {
        ensureSnapshotCapacity();

        int slot = snapshotCount++;

        System.arraycopy(
                state,
                0,
                snapshots[slot],
                0,
                heapCount
        );

        System.arraycopy(
                addrState,
                0,
                addrSnapshots[slot],
                0,
                addrCount
        );

        return slot;
    }

    private void restore(int snapshotSlot) {
        if (snapshotSlot < 0) {
            return;
        }

        System.arraycopy(
                snapshots[snapshotSlot],
                0,
                state,
                0,
                heapCount
        );

        System.arraycopy(
                addrSnapshots[snapshotSlot],
                0,
                addrState,
                0,
                addrCount
        );
    }

    private void ensureSnapshotCapacity() {
        if (snapshotCount < snapshots.length) {
            return;
        }

        int oldLength = snapshots.length;
        int newLength = Math.max(16, oldLength * 2);

        snapshots = Arrays.copyOf(snapshots, newLength);
        addrSnapshots = Arrays.copyOf(addrSnapshots, newLength);

        for (int i = oldLength; i < newLength; i++) {
            snapshots[i] = new MemoryState[heapCount];
            Arrays.fill(snapshots[i], MemoryState.UNTRACKED);

            addrSnapshots[i] = new AddressState[addrCount];
            Arrays.fill(addrSnapshots[i], AddressState.MAYBE_NULL);
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
        for (int i = 0; i < heapCount; i++) {
            boolean hasContinuingPath = false;
            MemoryState merged = null;

            if (!thenReturns) {
                hasContinuingPath = true;
                merged = mergeStateValue(
                        merged,
                        stateFromSnapshot(thenState, i, before)
                );
            }

            if (hasElse) {
                if (!elseReturns) {
                    hasContinuingPath = true;
                    merged = mergeStateValue(
                            merged,
                            stateFromSnapshot(elseState, i, before)
                    );
                }
            } else if (fallthroughState >= 0) {
                hasContinuingPath = true;
                merged = mergeStateValue(
                        merged,
                        stateFromSnapshot(fallthroughState, i, before)
                );
            }

            if (hasContinuingPath && merged != null) {
                state[i] = merged;
            } else {
                state[i] = stateFromSnapshot(before, i, before);
            }
        }

        for (int i = 0; i < addrCount; i++) {
            boolean hasContinuingPath = false;
            AddressState merged = null;

            if (!thenReturns) {
                hasContinuingPath = true;
                merged = mergeAddressStateValue(
                        merged,
                        addrStateFromSnapshot(thenState, i, before)
                );
            }

            if (hasElse) {
                if (!elseReturns) {
                    hasContinuingPath = true;
                    merged = mergeAddressStateValue(
                            merged,
                            addrStateFromSnapshot(elseState, i, before)
                    );
                }
            } else if (fallthroughState >= 0) {
                hasContinuingPath = true;
                merged = mergeAddressStateValue(
                        merged,
                        addrStateFromSnapshot(fallthroughState, i, before)
                );
            }

            if (hasContinuingPath && merged != null) {
                addrState[i] = merged;
            } else {
                addrState[i] = addrStateFromSnapshot(before, i, before);
            }
        }
    }

    private void mergeLoopState(
            int before,
            int afterOneIteration
    ) {
        for (int i = 0; i < heapCount; i++) {
            MemoryState merged = null;

            merged = mergeStateValue(
                    merged,
                    stateFromSnapshot(before, i, before)
            );

            merged = mergeStateValue(
                    merged,
                    stateFromSnapshot(afterOneIteration, i, before)
            );

            state[i] = merged == null
                    ? MemoryState.UNTRACKED
                    : merged;
        }

        for (int i = 0; i < addrCount; i++) {
            AddressState merged = null;

            merged = mergeAddressStateValue(
                    merged,
                    addrStateFromSnapshot(before, i, before)
            );

            merged = mergeAddressStateValue(
                    merged,
                    addrStateFromSnapshot(afterOneIteration, i, before)
            );

            addrState[i] = merged == null
                    ? AddressState.MAYBE_NULL
                    : merged;
        }
    }

    private MemoryState stateFromSnapshot(
            int snapshotSlot,
            int index,
            int fallbackSlot
    ) {
        if (index < 0 || index >= heapCount) {
            return MemoryState.ALIVE;
        }

        if (snapshotSlot >= 0
                && snapshotSlot < snapshotCount
                && snapshots[snapshotSlot] != null) {
            MemoryState value = snapshots[snapshotSlot][index];

            if (value != null) {
                return value;
            }
        }

        if (fallbackSlot >= 0
                && fallbackSlot < snapshotCount
                && snapshots[fallbackSlot] != null) {
            MemoryState fallback = snapshots[fallbackSlot][index];

            if (fallback != null) {
                return fallback;
            }
        }

        return MemoryState.UNTRACKED;
    }

    private AddressState addrStateFromSnapshot(
            int snapshotSlot,
            int index,
            int fallbackSlot
    ) {
        if (index < 0 || index >= addrCount) {
            return AddressState.MAYBE_NULL;
        }

        if (snapshotSlot >= 0
                && snapshotSlot < snapshotCount
                && addrSnapshots[snapshotSlot] != null) {
            AddressState value = addrSnapshots[snapshotSlot][index];

            if (value != null) {
                return value;
            }
        }

        if (fallbackSlot >= 0
                && fallbackSlot < snapshotCount
                && addrSnapshots[fallbackSlot] != null) {
            AddressState fallback = addrSnapshots[fallbackSlot][index];

            if (fallback != null) {
                return fallback;
            }
        }

        return AddressState.MAYBE_NULL;
    }

    private AddressState mergeAddressStateValue(
            AddressState left,
            AddressState right
    ) {
        if (right == null) {
            right = AddressState.MAYBE_NULL;
        }

        if (left == null) {
            return right;
        }

        if (left == right) {
            return left;
        }

        return AddressState.MAYBE_NULL;
    }

    private MemoryState mergeStateValue(
            MemoryState left,
            MemoryState right
    ) {
        if (right == null) {
            right = MemoryState.UNTRACKED;
        }

        if (left == null) {
            return right;
        }

        if (left == right) {
            return left;
        }

        if (left == MemoryState.UNTRACKED) {
            return right;
        }

        if (right == MemoryState.UNTRACKED) {
            return left;
        }

        return MemoryState.MAYBE_DELETED;
    }
}