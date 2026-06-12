import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Objects;

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
 *   - branch-sensitive analysis is limited to MAX_BRANCH_DEPTH.
 *
 * This checker is intentionally intra-procedural. It does not try to infer
 * whether called functions delete memory through aliases. In current Franko,
 * del is symbol-based, so memory ownership remains local to the heap variable.
 */
public final class MemorySafetyChecker {
    private static final int MAX_BRANCH_DEPTH = 8;

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
     * Preallocated snapshot storage.
     *
     * snapshots[k] is a full copy of state at some control-flow point.
     * Snapshot capacity is estimated from the number of if/else structures,
     * nested branch depth, loops, and functions.
     */
    private MemoryState[][] snapshots = new MemoryState[0][];
    private int snapshotCount = 0;

    private int branchDepth = 0;

    public MemorySafetyChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public void check(SemanticASTNode node) {
        diagnostics.clear();

        prepareAnalysis(node);

        branchDepth = 0;
        snapshotCount = 0;

        Arrays.fill(state, MemoryState.UNTRACKED);

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

        collectHeapSymbols(root);

        heapSymbols = Arrays.copyOf(heapSymbols, heapCount);

        state = new MemoryState[heapCount];
        Arrays.fill(state, MemoryState.UNTRACKED);

        int estimatedSnapshots =
                Math.max(16, estimateSnapshotDemand(root, 0) + 8);

        snapshots = new MemoryState[estimatedSnapshots][heapCount];

        for (int i = 0; i < snapshots.length; i++) {
            Arrays.fill(snapshots[i], MemoryState.UNTRACKED);
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
            addHeapSymbolIfNeeded(n.symbol);
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
            addHeapSymbolIfNeeded(n.symbol);
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
            addHeapSymbolIfNeeded(n.symbol);
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

        for (SemanticStmtNode stmt : node.statements) {
            if (stmt instanceof SemanticVarDeclNode decl
                    && decl.symbol != null
                    && decl.symbol.isHeap) {
                int index = heapIndexOf(decl.symbol);

                if (index >= 0) {
                    blockHeapIndices[blockHeapCount++] = index;
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
    }

    private boolean checkIf(SemanticIfNode node) {
        checkExpr(node.condition);

        if (branchDepth >= MAX_BRANCH_DEPTH) {
            return checkIfPathInsensitive(node);
        }

        int before = snapshot();

        branchDepth++;

        restore(before);
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

        branchDepth--;

        mergeAfterIf(
                before,
                thenState,
                thenReturns,
                elseState,
                elseReturns,
                node.elseBranch != null
        );

        return node.elseBranch != null && thenReturns && elseReturns;
    }

    /**
     * Fallback when branch nesting exceeds MAX_BRANCH_DEPTH.
     *
     * This remains conservative but stops deeper path splitting. It checks both
     * branches and merges their continuing effects.
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
                node.elseBranch != null
        );

        return node.elseBranch != null && thenReturns && elseReturns;
    }

    private boolean checkWhile(SemanticWhileNode node) {
        checkExpr(node.condition);

        int before = snapshot();

        /*
         * Check one body execution for internal memory errors.
         */
        boolean bodyReturns = checkStmt(node.body);
        int afterOneIteration = snapshot();

        /*
         * Check a second body execution from the post-body state. This catches
         * simple loop double-frees such as:
         *
         *   while (cond) {
         *       del x
         *   }
         *
         * because the second abstract iteration starts with x deleted.
         */
        if (!bodyReturns) {
            restore(afterOneIteration);
            checkStmt(node.body);
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

        if (!bodyReturns) {
            mergeLoopState(before, afterOneIteration);
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
    }

    private void ensureSnapshotCapacity() {
        if (snapshotCount < snapshots.length) {
            return;
        }

        int oldLength = snapshots.length;
        int newLength = Math.max(16, oldLength * 2);

        snapshots = Arrays.copyOf(snapshots, newLength);

        for (int i = oldLength; i < newLength; i++) {
            snapshots[i] = new MemoryState[heapCount];
            Arrays.fill(snapshots[i], MemoryState.UNTRACKED);
        }
    }

    private void mergeAfterIf(
            int before,
            int thenState,
            boolean thenReturns,
            int elseState,
            boolean elseReturns,
            boolean hasElse
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
            } else {
                hasContinuingPath = true;
                merged = mergeStateValue(
                        merged,
                        stateFromSnapshot(before, i, before)
                );
            }

            if (hasContinuingPath && merged != null) {
                state[i] = merged;
            } else {
                state[i] = stateFromSnapshot(before, i, before);
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