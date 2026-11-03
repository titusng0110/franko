import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DynamicArrayMemoryChecker {
    private static final int MAX_BRANCH_DEPTH = 12;

    private final DynamicArrayAnalysisState analysisState =
            new DynamicArrayAnalysisState();

    private static final class LoopFrame {
        final List<Integer> breakStates = new ArrayList<>();
        final List<Integer> continueStates = new ArrayList<>();
    }

    private final DiagnosticBag diagnostics;

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

        analysisState.prepare(node);

        branchDepth = 0;
        analysisState.snapshotCount = 0;
        loopStack.clear();
        addressTakenPointers.clear();
        reportedErrorSites.clear();
        reportedWarningSites.clear();
        suppressLifecycleChecks = false;
        currentFunction = null;

        Arrays.fill(analysisState.arrayState, ArrayState.UNINITED);
        Arrays.fill(analysisState.freedState, FreedState.NEVER);
        Arrays.fill(analysisState.escaped, false);
        Arrays.fill(analysisState.pointerState, ArrayOrigin.UNKNOWN);

        checkNode(node);
    }

    public PassResult check(SastDocument document) {
        check(document.root);
        return new PassResult(
                document.withPhase(SastPhase.DYN_ARRAY),
                diagnostics.diagnostics()
        );
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
        int beforeFunction = analysisState.snapshot();

        Arrays.fill(analysisState.arrayState, ArrayState.UNINITED);
        Arrays.fill(analysisState.freedState, FreedState.NEVER);
        Arrays.fill(analysisState.escaped, false);
        Arrays.fill(analysisState.pointerState, ArrayOrigin.UNKNOWN);

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

        analysisState.restore(beforeFunction);
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
                int arrayIndex = analysisState.arrayIndexOfSymbol(decl.symbol);

                if (arrayIndex >= 0) {
                    blockArrayIndices[blockArrayCount++] = arrayIndex;
                }

                int pointerIndex = analysisState.pointerIndexOfSymbol(decl.symbol);

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
                ? "end of function '" + DynamicArrayAnalysisState.functionDisplayName(currentFunction) + "'"
                : "end of scope";

        if (!suppressLifecycleChecks) {
            for (int i = 0; i < blockArrayCount; i++) {
                reportLeakIfNeeded(blockArrayIndices[i], where);
            }
        }

        for (int i = 0; i < blockArrayCount; i++) {
            int index = blockArrayIndices[i];
            analysisState.arrayState[index] = ArrayState.UNINITED;
            analysisState.freedState[index] = FreedState.NEVER;
            analysisState.escaped[index] = false;
        }

        for (int i = 0; i < blockPointerCount; i++) {
            analysisState.pointerState[blockPointerIndices[i]] = ArrayOrigin.UNKNOWN;
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

        int arrayIndex = analysisState.arrayIndexOfSymbol(node.symbol);

        if (arrayIndex >= 0) {
            analysisState.arrayState[arrayIndex] = ArrayState.UNINITED;
            analysisState.freedState[arrayIndex] = FreedState.NEVER;
            analysisState.escaped[arrayIndex] = false;
        }

        int pointerIndex = analysisState.pointerIndexOfSymbol(node.symbol);

        if (pointerIndex >= 0) {
            analysisState.pointerState[pointerIndex] = ArrayOrigin.EMPTY;
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

        int before = analysisState.snapshot();

        branchDepth++;

        boolean thenFeasible = guardPathFeasible(node.condition, true);

        analysisState.restore(before);

        if (thenFeasible) {
            refineFromGuard(node.condition, true);
        }

        boolean previousSuppression = suppressLifecycleChecks;
        suppressLifecycleChecks = previousSuppression || !thenFeasible;
        boolean thenReturns = checkStmt(node.thenBranch);
        suppressLifecycleChecks = previousSuppression;

        int thenState = thenFeasible ? analysisState.snapshot() : -1;

        analysisState.restore(before);

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

            elseState = elseFeasible ? analysisState.snapshot() : -1;
        } else if (elseFeasible) {
            refineFromGuard(node.condition, false);
            fallthroughState = analysisState.snapshot();
        }

        analysisState.restore(before);

        branchDepth--;

        analysisState.mergeAfterIf(
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
        int before = analysisState.snapshot();

        boolean thenReturns = checkStmt(node.thenBranch);
        int thenState = analysisState.snapshot();

        analysisState.restore(before);

        boolean elseReturns = false;
        int elseState = -1;

        if (node.elseBranch != null) {
            elseReturns = checkStmt(node.elseBranch);
            elseState = analysisState.snapshot();
        }

        analysisState.restore(before);

        analysisState.mergeAfterIf(
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
                    DynamicArrayAnalysisState.negate(inner.op),
                    inner.constant
            );
        }

        if (!(condition instanceof SemanticBinOpNode bin)
                || !DynamicArrayAnalysisState.isComparison(bin.op)) {
            return null;
        }

        boolean leftLength = DynamicArrayAnalysisState.isLengthCall(bin.left);
        boolean rightLength = DynamicArrayAnalysisState.isLengthCall(bin.right);

        if (leftLength == rightLength) {
            return null;
        }

        SemanticExprNode lengthSide = leftLength ? bin.left : bin.right;
        SemanticExprNode constantSide = leftLength ? bin.right : bin.left;

        if (constantSide.constantValue == null) {
            return null;
        }

        String op = leftLength ? bin.op : DynamicArrayAnalysisState.reverse(bin.op);
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
            case ZERO -> analysisState.arrayState[index] != ArrayState.INITED;
            case NONZERO -> analysisState.arrayState[index] != ArrayState.UNINITED;
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
            if (analysisState.arrayState[index] != ArrayState.UNINITED) {
                analysisState.arrayState[index] = ArrayState.UNINITED;
                analysisState.freedState[index] = FreedState.MAYBE;
            }
        } else if (assertion == LengthAssertion.NONZERO) {
            analysisState.arrayState[index] = ArrayState.INITED;
            analysisState.freedState[index] = FreedState.NEVER;
        }
    }

    private boolean checkWhile(SemanticWhileNode node) {
        checkExpr(node.condition);

        int before = analysisState.snapshot();

        boolean bodyFeasible = guardPathFeasible(node.condition, true);

        LoopFrame frame = new LoopFrame();
        loopStack.add(frame);

        List<Integer> exitStates = new ArrayList<>();

        if (guardPathFeasible(node.condition, false)) {
            analysisState.restore(before);
            refineFromGuard(node.condition, false);
            exitStates.add(analysisState.snapshot());
        }

        boolean bodyTerminates = false;
        List<Integer> backEdgeStates = new ArrayList<>();

        if (bodyFeasible) {
            analysisState.restore(before);
            refineFromGuard(node.condition, true);

            bodyTerminates = checkStmt(node.body);
            int afterBody = bodyTerminates ? -1 : analysisState.snapshot();

            if (!node.update.isEmpty()) {
                if (!bodyTerminates) {
                    analysisState.restore(afterBody);

                    if (checkUpdates(node.update)) {
                        backEdgeStates.add(analysisState.snapshot());
                    }
                }

                for (int continueState : frame.continueStates) {
                    analysisState.restore(continueState);

                    if (checkUpdates(node.update)) {
                        backEdgeStates.add(analysisState.snapshot());
                    }
                }
            } else {
                if (!bodyTerminates) {
                    backEdgeStates.add(afterBody);
                }

                backEdgeStates.addAll(frame.continueStates);
            }

            for (int backEdgeState : backEdgeStates) {
                analysisState.restore(backEdgeState);

                if (guardPathFeasible(node.condition, false)) {
                    refineFromGuard(node.condition, false);
                    exitStates.add(analysisState.snapshot());
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

        analysisState.restore(before);

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

        int slot = analysisState.snapshot();

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

        for (int i = 0; i < analysisState.arrayCount; i++) {
            ArrayState state =
                    analysisState.arrayStateFromSnapshot(first, i, first);
            FreedState freed =
                    analysisState.freedStateFromSnapshot(first, i, first);

            for (int j = 1; j < slots.size(); j++) {
                int slot = slots.get(j);

                state = analysisState.mergeArrayState(
                        state,
                        analysisState.arrayStateFromSnapshot(slot, i, first)
                );

                freed = analysisState.mergeFreedState(
                        freed,
                        analysisState.freedStateFromSnapshot(slot, i, first)
                );
            }

            if (state == ArrayState.INITED) {
                freed = FreedState.NEVER;
            } else if (state == ArrayState.MAYBE_INITED) {
                freed = FreedState.MAYBE;
            }

            analysisState.arrayState[i] = state;
            analysisState.freedState[i] = freed;
        }

        for (int i = 0; i < analysisState.pointerCount; i++) {
            ArrayOrigin origin =
                    analysisState.pointerOriginFromSnapshot(first, i, first);

            for (int j = 1; j < slots.size(); j++) {
                origin = analysisState.mergeOrigins(
                        origin,
                        analysisState.pointerOriginFromSnapshot(slots.get(j), i, first)
                );
            }

            analysisState.pointerState[i] = origin;
        }
    }

    private void mergeStateIntoCurrent(int slot) {
        for (int i = 0; i < analysisState.arrayCount; i++) {
            analysisState.arrayState[i] = analysisState.mergeArrayState(
                    analysisState.arrayState[i],
                    analysisState.arrayStateFromSnapshot(slot, i, slot)
            );

            analysisState.freedState[i] = analysisState.mergeFreedState(
                    analysisState.freedState[i],
                    analysisState.freedStateFromSnapshot(slot, i, slot)
            );

            if (analysisState.arrayState[i] == ArrayState.INITED) {
                analysisState.freedState[i] = FreedState.NEVER;
            } else if (analysisState.arrayState[i] == ArrayState.MAYBE_INITED) {
                analysisState.freedState[i] = FreedState.MAYBE;
            }
        }

        for (int i = 0; i < analysisState.pointerCount; i++) {
            analysisState.pointerState[i] = analysisState.mergeOrigins(
                    analysisState.pointerState[i],
                    analysisState.pointerOriginFromSnapshot(slot, i, slot)
            );
        }
    }

    private boolean checkReturn(SemanticReturnNode node) {
        checkExpr(node.value);

        if (!suppressLifecycleChecks) {
            reportLiveArrays(
                    "return from function '"
                            + DynamicArrayAnalysisState.functionDisplayName(node.function)
                            + "'"
            );
        }

        return true;
    }

    private void reportLiveArrays(String where) {
        for (int i = 0; i < analysisState.arrayCount; i++) {
            reportLeakIfNeeded(i, where);
        }
    }

    private void reportLeakIfNeeded(int index, String where) {
        if (suppressLifecycleChecks
                || index < 0
                || index >= analysisState.arrayCount
                || analysisState.escaped[index]) {
            return;
        }

        String name = analysisState.arraySymbols[index].qualifiedName();

        if (analysisState.arrayState[index] == ArrayState.INITED) {
            diagnostics.warning(
                    "Dynamic array '"
                            + name
                            + "' is still initialized at "
                            + where
                            + " (missing explicit uninit)"
            );
        } else if (analysisState.arrayState[index] == ArrayState.MAYBE_INITED) {
            diagnostics.warning(
                    "Dynamic array '"
                            + name
                            + "' may still be initialized at "
                            + where
                            + " (missing explicit uninit)"
            );
        }
    }

    private int singleArrayIndex(SemanticExprNode expr) {
        ArrayOrigin origins = storageOrigins(expr);

        if (origins == null
                || origins.unknown
                || origins.arrays.size() != 1) {
            return -1;
        }

        VariableSymbol symbol = origins.arrays.iterator().next();

        return analysisState.arrayIndexOfSymbol(symbol);
    }

    private ArrayOrigin storageOrigins(SemanticExprNode expr) {
        if (expr == null) {
            return ArrayOrigin.EMPTY;
        }

        if (expr instanceof SemanticVarExprNode n) {
            if (n.symbol instanceof GlobalVariableSymbol) {
                return ArrayOrigin.EMPTY;
            }

            int index = analysisState.arrayIndexOfSymbol(n.symbol);

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
            int index = analysisState.pointerIndexOfSymbol(n.symbol);

            return index < 0
                    ? ArrayOrigin.UNKNOWN
                    : analysisState.pointerState[index];
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
            int index = analysisState.pointerIndexOfSymbol(n.symbol);

            if (index >= 0) {
                analysisState.pointerState[index] = value;

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
                int index = analysisState.arrayIndexOfSymbol(symbol);

                if (index < 0) {
                    continue;
                }

                if (analysisState.arrayState[index] != ArrayState.INITED) {
                    analysisState.arrayState[index] = ArrayState.MAYBE_INITED;
                    analysisState.freedState[index] = FreedState.MAYBE;
                }
            }

            if (origins.unknown || origins.arrays.isEmpty()) {
                havocEscapedArrays();
            }

            return;
        }

        VariableSymbol symbol = origins.arrays.iterator().next();
        int index = analysisState.arrayIndexOfSymbol(symbol);

        if (index < 0) {
            havocEscapedArrays();
            return;
        }

        analysisState.arrayState[index] = ArrayState.INITED;
        analysisState.freedState[index] = FreedState.NEVER;
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
        int index = analysisState.arrayIndexOfSymbol(symbol);

        if (index < 0) {
            havocEscapedArrays();
            return;
        }

        if (analysisState.arrayState[index] == ArrayState.MAYBE_INITED) {
            reportWarningOnce(
                    site,
                    "Possible use of uninitialized dynamic array '"
                            + symbol.qualifiedName()
                            + "'"
            );
        } else if (analysisState.arrayState[index] == ArrayState.UNINITED) {
            reportUninitializedUse(index, site);
        }

        analysisState.arrayState[index] = ArrayState.INITED;
        analysisState.freedState[index] = FreedState.NEVER;
    }

    private void applyUninit(
            SemanticExprNode receiver,
            SemanticASTNode site
    ) {
        ArrayOrigin origins = storageOrigins(receiver);

        if (origins.unknown || origins.arrays.size() != 1) {
            for (VariableSymbol symbol : origins.arrays) {
                int index = analysisState.arrayIndexOfSymbol(symbol);

                if (index >= 0
                        && analysisState.arrayState[index] != ArrayState.UNINITED) {
                    analysisState.arrayState[index] = ArrayState.MAYBE_INITED;
                    analysisState.freedState[index] = FreedState.MAYBE;
                }
            }

            if (origins.unknown || origins.arrays.isEmpty()) {
                havocEscapedArrays();
            }

            return;
        }

        VariableSymbol symbol = origins.arrays.iterator().next();
        int index = analysisState.arrayIndexOfSymbol(symbol);

        if (index < 0) {
            havocEscapedArrays();
            return;
        }

        if (analysisState.arrayState[index] == ArrayState.INITED) {
            analysisState.arrayState[index] = ArrayState.UNINITED;
            analysisState.freedState[index] = FreedState.YES;
            return;
        }

        if (analysisState.arrayState[index] == ArrayState.MAYBE_INITED) {
            reportWarningOnce(
                    site,
                    "Possible invalid free or double free of dynamic array '"
                            + symbol.qualifiedName()
                            + "'"
            );

            analysisState.arrayState[index] = ArrayState.UNINITED;
            analysisState.freedState[index] = FreedState.YES;
            return;
        }

        if (analysisState.freedState[index] == FreedState.YES) {
            reportErrorOnce(
                    site,
                    "Double free of dynamic array '"
                            + symbol.qualifiedName()
                            + "' (uninit called twice)"
            );
        } else if (analysisState.freedState[index] == FreedState.NEVER) {
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

        analysisState.arrayState[index] = ArrayState.UNINITED;
        analysisState.freedState[index] = FreedState.YES;
    }

    private void requireInitialized(
            SemanticExprNode receiver,
            SemanticASTNode site
    ) {
        int index = singleArrayIndex(receiver);

        if (index < 0) {
            return;
        }

        if (analysisState.arrayState[index] == ArrayState.INITED) {
            return;
        }

        if (analysisState.arrayState[index] == ArrayState.MAYBE_INITED) {
            reportWarningOnce(
                    site,
                    "Possible use of uninitialized dynamic array '"
                            + analysisState.arraySymbols[index].qualifiedName()
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
        int index = analysisState.arrayIndexOfSymbol(symbol);

        if (index < 0 || analysisState.arrayState[index] == ArrayState.INITED) {
            return;
        }

        if (analysisState.arrayState[index] == ArrayState.MAYBE_INITED) {
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
        String name = analysisState.arraySymbols[index].qualifiedName();

        if (analysisState.freedState[index] == FreedState.YES) {
            reportErrorOnce(
                    site,
                    "Use of dynamic array '" + name + "' after uninit"
            );
        } else if (analysisState.freedState[index] == FreedState.NEVER) {
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

        int index = analysisState.pointerIndexOfSymbol(var.symbol);

        if (index < 0) {
            return;
        }

        addressTakenPointers.add(var.symbol);
        markEscaped(analysisState.pointerState[index]);
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

        for (int i = 0; i < analysisState.pointerCount; i++) {
            if (analysisState.pointerSymbols[i] instanceof GlobalVariableSymbol) {
                analysisState.pointerState[i] = ArrayOrigin.UNKNOWN;
            }
        }

        for (VariableSymbol symbol : addressTakenPointers) {
            int index = analysisState.pointerIndexOfSymbol(symbol);

            if (index >= 0) {
                analysisState.pointerState[index] = ArrayOrigin.UNKNOWN;
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
            int index = analysisState.arrayIndexOfSymbol(symbol);

            if (index >= 0) {
                analysisState.arrayState[index] = ArrayState.MAYBE_INITED;
                analysisState.freedState[index] = FreedState.MAYBE;
            }
        }
    }

    private void markEscaped(ArrayOrigin origins) {
        if (origins == null) {
            return;
        }

        for (VariableSymbol symbol : origins.arrays) {
            int index = analysisState.arrayIndexOfSymbol(symbol);

            if (index >= 0) {
                analysisState.escaped[index] = true;
            }
        }
    }

    private void havocEscapedArrays() {
        for (int i = 0; i < analysisState.arrayCount; i++) {
            if (analysisState.escaped[i]) {
                analysisState.arrayState[i] = ArrayState.MAYBE_INITED;
                analysisState.freedState[i] = FreedState.MAYBE;
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

}
