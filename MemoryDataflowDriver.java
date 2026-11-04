final class MemoryDataflowDriver {
    static final int MAX_BRANCH_DEPTH = MemoryAnalysisContext.MAX_BRANCH_DEPTH;

    interface Domain {
        void beginFunction(int functionId);

        void endFunction(int exitCount, boolean fallthrough);

        void enterBlock(SemanticBlockNode node, int depth);

        void leaveBlock(SemanticBlockNode node, int depth, boolean terminated);

        void checkVarDecl(SemanticVarDeclNode node);

        void checkAssign(SemanticAssignNode node);

        void checkExpr(SemanticExprNode expr);

        void onReturn(SemanticReturnNode node);

        void onFallthrough();

        boolean guardPathFeasible(SemanticExprNode condition, boolean guardHolds);

        void refineFromGuard(SemanticExprNode condition, boolean guardHolds);

        int snapshot();

        void restore(int slot);

        void mergeAfterIf(
                int before,
                int thenState,
                boolean thenLeaves,
                int elseState,
                boolean elseLeaves,
                boolean hasElse,
                int fallthroughState
        );

        void mergeLoopExitStates(int[] slots, int count);

        void mergeStateIntoCurrent(int slot);

        void markLocalsDeadForJump(int targetBlockDepth, int snapshotSlot);

        void markLoopBodyLocalsDead(SemanticStmtNode body);

        boolean isSuppressed();

        void setSuppressed(boolean value);
    }

    private static final class LoopFrame {
        int breakHead = -1;
        int continueHead = -1;
        int bodyBlockDepth;
    }

    final MemoryAnalysisContext context;
    final DiagnosticBag diagnostics;
    final Domain domain;

    private int branchDepth;
    private int blockDepth;
    private int loopDepth;

    private final LoopFrame[] loopFrames = new LoopFrame[64];
    private int[] jumpSlots = new int[256];
    private int[] jumpNext = new int[256];
    private int jumpCount;

    private int[] exitSlots = new int[64];
    private int exitCount;

    MemoryDataflowDriver(
            MemoryAnalysisContext context,
            DiagnosticBag diagnostics,
            Domain domain
    ) {
        this.context = context;
        this.diagnostics = diagnostics;
        this.domain = domain;

        for (int i = 0; i < loopFrames.length; i++) {
            loopFrames[i] = new LoopFrame();
        }
    }

    void run(SemanticProgramNode root) {
        checkTopLevel(root);
    }

    private void checkTopLevel(SemanticASTNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticProgramNode program) {
            for (SemanticASTNode item : program.topLevelItems) {
                checkTopLevel(item);
            }

            return;
        }

        if (node instanceof SemanticNamespaceNode namespace) {
            for (SemanticASTNode item : namespace.items) {
                checkTopLevel(item);
            }

            return;
        }

        if (node instanceof SemanticStructDeclNode struct) {
            for (SemanticFunctionDeclNode method : struct.methods) {
                checkFunction(method);
            }

            return;
        }

        if (node instanceof SemanticFunctionDeclNode function) {
            checkFunction(function);
        }
    }

    private void checkFunction(SemanticFunctionDeclNode function) {
        int functionId = context.functionId(function.symbol);

        branchDepth = 0;
        blockDepth = 0;
        loopDepth = 0;
        jumpCount = 0;
        exitCount = 0;

        domain.setSuppressed(false);
        domain.beginFunction(functionId);

        boolean terminated = false;

        if (function.body != null) {
            terminated = checkStmt(function.body);
        }

        if (!terminated) {
            domain.onFallthrough();
            addExit();
        }

        domain.endFunction(exitCount, !terminated);
    }

    private void addExit() {
        if (exitCount == exitSlots.length) {
            int[] grown = new int[exitSlots.length * 2];
            System.arraycopy(exitSlots, 0, grown, 0, exitSlots.length);
            exitSlots = grown;
        }

        exitSlots[exitCount++] = domain.snapshot();
    }

    int exitSlot(int index) {
        return exitSlots[index];
    }

    private boolean checkStmt(SemanticStmtNode node) {
        if (node == null) {
            return false;
        }

        diagnostics.push(node.span);

        try {
            return checkStmtInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private boolean checkStmtInner(SemanticStmtNode node) {
        if (node instanceof SemanticBlockNode block) {
            return checkBlock(block);
        }

        if (node instanceof SemanticVarDeclNode declaration) {
            domain.checkVarDecl(declaration);
            return false;
        }

        if (node instanceof SemanticAssignNode assignment) {
            domain.checkAssign(assignment);
            return false;
        }

        if (node instanceof SemanticIfNode branch) {
            return checkIf(branch);
        }

        if (node instanceof SemanticWhileNode loop) {
            return checkWhile(loop);
        }

        if (node instanceof SemanticBreakNode jump) {
            recordJumpState(jump.levels, false);
            return true;
        }

        if (node instanceof SemanticContinueNode) {
            recordJumpState(1, true);
            return true;
        }

        if (node instanceof SemanticReturnNode returned) {
            domain.checkExpr(returned.value);
            domain.onReturn(returned);
            addExit();
            return true;
        }

        if (node instanceof SemanticExprStmtNode expressionStatement) {
            domain.checkExpr(expressionStatement.expr);
            return false;
        }

        return false;
    }

    private boolean checkBlock(SemanticBlockNode block) {
        int depth = ++blockDepth;
        domain.enterBlock(block, depth);

        boolean terminated = false;

        try {
            for (SemanticStmtNode statement : block.statements) {
                if (checkStmt(statement)) {
                    terminated = true;
                    break;
                }
            }
        } finally {
            blockDepth--;
            domain.leaveBlock(block, depth, terminated);
        }

        return terminated;
    }

    private boolean checkIf(SemanticIfNode node) {
        domain.checkExpr(node.condition);

        if (branchDepth >= MAX_BRANCH_DEPTH) {
            return checkIfPathInsensitive(node);
        }

        int before = domain.snapshot();
        branchDepth++;

        boolean thenFeasible = domain.guardPathFeasible(node.condition, true);
        domain.restore(before);

        if (thenFeasible) {
            domain.refineFromGuard(node.condition, true);
        }

        boolean previousSuppression = domain.isSuppressed();
        domain.setSuppressed(previousSuppression || !thenFeasible);
        boolean thenLeaves = checkStmt(node.thenBranch);
        domain.setSuppressed(previousSuppression);

        int thenState = thenFeasible ? domain.snapshot() : -1;
        domain.restore(before);

        boolean elseFeasible = domain.guardPathFeasible(node.condition, false);
        boolean elseLeaves = false;
        int elseState = -1;
        int fallthroughState = -1;

        if (node.elseBranch != null) {
            if (elseFeasible) {
                domain.refineFromGuard(node.condition, false);
            }

            domain.setSuppressed(previousSuppression || !elseFeasible);
            elseLeaves = checkStmt(node.elseBranch);
            domain.setSuppressed(previousSuppression);

            elseState = elseFeasible ? domain.snapshot() : -1;
        } else if (elseFeasible) {
            domain.refineFromGuard(node.condition, false);
            fallthroughState = domain.snapshot();
        }

        domain.restore(before);
        branchDepth--;

        boolean thenActuallyLeaves = thenLeaves || !thenFeasible;
        boolean elseActuallyLeaves = node.elseBranch != null
                ? elseLeaves || !elseFeasible
                : !elseFeasible;

        domain.mergeAfterIf(
                before,
                thenState,
                thenActuallyLeaves,
                elseState,
                elseActuallyLeaves,
                node.elseBranch != null,
                fallthroughState
        );

        return thenActuallyLeaves && elseActuallyLeaves;
    }

    private boolean checkIfPathInsensitive(SemanticIfNode node) {
        int before = domain.snapshot();

        boolean thenLeaves = checkStmt(node.thenBranch);
        int thenState = domain.snapshot();
        domain.restore(before);

        boolean elseLeaves = false;
        int elseState = -1;

        if (node.elseBranch != null) {
            elseLeaves = checkStmt(node.elseBranch);
            elseState = domain.snapshot();
        }

        domain.restore(before);

        domain.mergeAfterIf(
                before,
                thenState,
                thenLeaves,
                elseState,
                elseLeaves,
                node.elseBranch != null,
                before
        );

        return node.elseBranch != null && thenLeaves && elseLeaves;
    }

    private boolean checkWhile(SemanticWhileNode node) {
        domain.checkExpr(node.condition);

        int before = domain.snapshot();
        boolean bodyFeasible = domain.guardPathFeasible(node.condition, true);

        int frameIndex = loopDepth++;
        LoopFrame frame = loopFrames[frameIndex];
        frame.breakHead = -1;
        frame.continueHead = -1;
        frame.bodyBlockDepth = node.body instanceof SemanticBlockNode
                ? blockDepth + 1
                : blockDepth;

        int capacity = Math.max(
                16,
                context.maxSnapshotDemand + MAX_BRANCH_DEPTH + 4
        );
        int[] loopExitSlots = new int[capacity];
        int[] backEdgeSlots = new int[capacity];
        int exitCountLocal = 0;
        int backEdgeCount = 0;

        if (domain.guardPathFeasible(node.condition, false)) {
            domain.restore(before);
            domain.refineFromGuard(node.condition, false);
            loopExitSlots[exitCountLocal++] = domain.snapshot();
        }

        if (bodyFeasible) {
            domain.restore(before);
            domain.refineFromGuard(node.condition, true);

            boolean bodyTerminates = checkStmt(node.body);
            int afterBody = bodyTerminates ? -1 : domain.snapshot();

            if (!node.update.isEmpty()) {
                if (!bodyTerminates) {
                    domain.restore(afterBody);

                    if (checkUpdates(node.update)) {
                        backEdgeSlots[backEdgeCount++] = domain.snapshot();
                    }
                }

                for (int i = frame.continueHead; i >= 0; i = jumpNext[i]) {
                    domain.restore(jumpSlots[i]);

                    if (checkUpdates(node.update)) {
                        backEdgeSlots[backEdgeCount++] = domain.snapshot();
                    }
                }
            } else {
                if (!bodyTerminates) {
                    backEdgeSlots[backEdgeCount++] = afterBody;
                }

                for (int i = frame.continueHead; i >= 0; i = jumpNext[i]) {
                    backEdgeSlots[backEdgeCount++] = jumpSlots[i];
                }
            }

            for (int i = 0; i < backEdgeCount; i++) {
                domain.restore(backEdgeSlots[i]);

                if (domain.guardPathFeasible(node.condition, false)) {
                    domain.refineFromGuard(node.condition, false);
                    loopExitSlots[exitCountLocal++] = domain.snapshot();
                }
            }
        } else {
            boolean previousSuppression = domain.isSuppressed();
            domain.setSuppressed(true);
            checkStmt(node.body);

            for (SemanticStmtNode update : node.update) {
                checkStmt(update);
            }

            domain.setSuppressed(previousSuppression);
        }

        loopDepth--;

        domain.restore(before);

        if (exitCountLocal > 0) {
            domain.mergeLoopExitStates(loopExitSlots, exitCountLocal);
        }

        if (bodyFeasible) {
            for (int i = frame.breakHead; i >= 0; i = jumpNext[i]) {
                domain.mergeStateIntoCurrent(jumpSlots[i]);
            }
        }

        domain.markLoopBodyLocalsDead(node.body);

        boolean hasExit = exitCountLocal > 0
                || (bodyFeasible && frame.breakHead >= 0);

        return !hasExit;
    }

    private boolean checkUpdates(java.util.List<SemanticStmtNode> updates) {
        for (SemanticStmtNode update : updates) {
            if (checkStmt(update)) {
                return false;
            }
        }

        return true;
    }

    private void recordJumpState(int levels, boolean isContinue) {
        if (loopDepth == 0 || domain.isSuppressed()) {
            return;
        }

        int slot = domain.snapshot();

        int targetIndex = loopDepth - levels;

        if (targetIndex < 0) {
            targetIndex = 0;
        }

        LoopFrame target = loopFrames[targetIndex];
        domain.markLocalsDeadForJump(target.bodyBlockDepth, slot);

        ensureJumpCapacity();

        jumpSlots[jumpCount] = slot;
        jumpNext[jumpCount] = isContinue ? target.continueHead : target.breakHead;

        if (isContinue) {
            target.continueHead = jumpCount;
        } else {
            target.breakHead = jumpCount;
        }

        jumpCount++;
    }

    private void ensureJumpCapacity() {
        if (jumpCount < jumpSlots.length) {
            return;
        }

        int[] grownSlots = new int[jumpSlots.length * 2];
        int[] grownNext = new int[jumpNext.length * 2];
        System.arraycopy(jumpSlots, 0, grownSlots, 0, jumpSlots.length);
        System.arraycopy(jumpNext, 0, grownNext, 0, jumpNext.length);
        jumpSlots = grownSlots;
        jumpNext = grownNext;
    }
}
