import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PointerNullStateChecker {
    private static final int MAX_BRANCH_DEPTH = 10;

    private enum PointerState {
        POINTING,
        NULL,
        MAYBE_NULL
    }

    private final DiagnosticBag diagnostics;

    private final IdentityHashMap<VariableSymbol, Integer> pointerIndexBySymbol =
            new IdentityHashMap<>();

    private VariableSymbol[] pointerSymbols = new VariableSymbol[0];
    private int pointerCount = 0;

    private PointerState[] pointerState = new PointerState[0];

    private PointerState[][] pointerSnapshots = new PointerState[0][];

    private int snapshotCount = 0;

    private int branchDepth = 0;

    private static final class LoopFrame {
        final List<Integer> breakStates = new ArrayList<>();
        final List<Integer> continueStates = new ArrayList<>();
    }

    private final List<LoopFrame> loopStack = new ArrayList<>();

    private boolean suppressNullDerefChecks = false;

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

    private String varNameOf(SemanticExprNode expr) {
        if (expr instanceof SemanticVarExprNode n
                && n.symbol != null) {
            return n.symbol.qualifiedName();
        }

        return "<expression>";
    }

    private VariableSymbol symbolOf(SemanticExprNode expr) {
        if (expr instanceof SemanticVarExprNode n) {
            return n.symbol;
        }

        return null;
    }

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

        for (int i = 0; i < blockPointerCount; i++) {
            pointerState[blockPointerIndices[i]] = PointerState.MAYBE_NULL;
        }

        return false;
    }

    private void checkVarDecl(SemanticVarDeclNode node) {
        if (node == null || node.symbol == null) {
            return;
        }

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

        boolean bodyFeasible = guardPathFeasible(node.condition, true);

        LoopFrame frame = new LoopFrame();
        loopStack.add(frame);

        boolean bodyTerminates = false;
        int afterBody = -1;

        if (bodyFeasible) {

            refinePointerStatesFromGuard(node.condition, true);

            bodyTerminates = checkStmt(node.body);
            afterBody = snapshot();
        } else {

            suppressNullDerefChecks = true;
            checkStmt(node.body);
            suppressNullDerefChecks = false;
        }

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

            suppressNullDerefChecks = true;

            for (SemanticStmtNode updateStmt : node.update) {
                checkStmt(updateStmt);
            }

            suppressNullDerefChecks = false;
        }

        loopStack.remove(loopStack.size() - 1);

        restore(before);

        if (bodyFeasible && !backEdgeStates.isEmpty()) {
            mergeLoopStates(before, backEdgeStates);
        }

        if (!bodyFeasible || !bodyTerminates || !backEdgeStates.isEmpty()) {

            refinePointerStatesFromGuard(node.condition, false);
        }

        if (bodyFeasible) {
            for (int breakState : frame.breakStates) {
                mergeStateIntoCurrent(breakState);
            }
        }

        return false;
    }

    private void recordJumpState(int levels, boolean isContinue) {
        if (loopStack.isEmpty()) {

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

    private void mergeStateIntoCurrent(int slot) {
        for (int i = 0; i < pointerCount; i++) {
            pointerState[i] = mergePointerStateValue(
                    pointerState[i],
                    pointerStateFromSnapshot(slot, i, slot)
            );
        }
    }

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
