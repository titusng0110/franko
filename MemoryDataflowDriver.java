final class MemoryDataflowDriver {
    static final int MAX_BRANCH_DEPTH = MemoryAnalysisContext.MAX_BRANCH_DEPTH;

    enum DomainKind {
        NULL_STATE,
        DANGLING,
        DYNAMIC_ARRAY
    }

    static final class Domain {
        final DomainKind kind;
        final PointerNullStateChecker.NullDomain nullState;
        final DanglingPointerChecker.DanglingDomain dangling;
        final DynamicArrayMemoryChecker.DynamicDomain dynamicArray;

        Domain(PointerNullStateChecker.NullDomain domain) {
            this.kind = DomainKind.NULL_STATE;
            this.nullState = domain;
            this.dangling = null;
            this.dynamicArray = null;
        }

        Domain(DanglingPointerChecker.DanglingDomain domain) {
            this.kind = DomainKind.DANGLING;
            this.nullState = null;
            this.dangling = domain;
            this.dynamicArray = null;
        }

        Domain(DynamicArrayMemoryChecker.DynamicDomain domain) {
            this.kind = DomainKind.DYNAMIC_ARRAY;
            this.nullState = null;
            this.dangling = null;
            this.dynamicArray = domain;
        }

        void beginFunction(int functionId) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.beginFunction(functionId);
            } else if (kind == DomainKind.DANGLING) {
                dangling.beginFunction(functionId);
            } else {
                dynamicArray.beginFunction(functionId);
            }
        }

        void endFunction(int exitCount, boolean fallthrough) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.endFunction(exitCount, fallthrough);
            } else if (kind == DomainKind.DANGLING) {
                dangling.endFunction(exitCount, fallthrough);
            } else {
                dynamicArray.endFunction(exitCount, fallthrough);
            }
        }

        void enterBlock(SastNode node, int depth) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.enterBlock(node, depth);
            } else if (kind == DomainKind.DANGLING) {
                dangling.enterBlock(node, depth);
            } else {
                dynamicArray.enterBlock(node, depth);
            }
        }

        void leaveBlock(SastNode node, int depth, boolean terminated) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.leaveBlock(node, depth, terminated);
            } else if (kind == DomainKind.DANGLING) {
                dangling.leaveBlock(node, depth, terminated);
            } else {
                dynamicArray.leaveBlock(node, depth, terminated);
            }
        }

        void checkVarDecl(SastNode node) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.checkVarDecl(node);
            } else if (kind == DomainKind.DANGLING) {
                dangling.checkVarDecl(node);
            } else {
                dynamicArray.checkVarDecl(node);
            }
        }

        void checkAssign(SastNode node) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.checkAssign(node);
            } else if (kind == DomainKind.DANGLING) {
                dangling.checkAssign(node);
            } else {
                dynamicArray.checkAssign(node);
            }
        }

        void checkExpr(SastNode expr) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.checkExpr(expr);
            } else if (kind == DomainKind.DANGLING) {
                dangling.checkExpr(expr);
            } else {
                dynamicArray.checkExpr(expr);
            }
        }

        void onReturn(SastNode node) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.onReturn(node);
            } else if (kind == DomainKind.DANGLING) {
                dangling.onReturn(node);
            } else {
                dynamicArray.onReturn(node);
            }
        }

        void onFallthrough() {
            if (kind == DomainKind.NULL_STATE) {
                nullState.onFallthrough();
            } else if (kind == DomainKind.DANGLING) {
                dangling.onFallthrough();
            } else {
                dynamicArray.onFallthrough();
            }
        }

        boolean guardPathFeasible(SastNode condition, boolean guardHolds) {
            if (kind == DomainKind.NULL_STATE) {
                return nullState.guardPathFeasible(condition, guardHolds);
            }

            if (kind == DomainKind.DANGLING) {
                return dangling.guardPathFeasible(condition, guardHolds);
            }

            return dynamicArray.guardPathFeasible(condition, guardHolds);
        }

        void refineFromGuard(SastNode condition, boolean guardHolds) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.refineFromGuard(condition, guardHolds);
            } else if (kind == DomainKind.DANGLING) {
                dangling.refineFromGuard(condition, guardHolds);
            } else {
                dynamicArray.refineFromGuard(condition, guardHolds);
            }
        }

        int snapshot() {
            if (kind == DomainKind.NULL_STATE) {
                return nullState.snapshot();
            }

            if (kind == DomainKind.DANGLING) {
                return dangling.snapshot();
            }

            return dynamicArray.snapshot();
        }

        void restore(int slot) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.restore(slot);
            } else if (kind == DomainKind.DANGLING) {
                dangling.restore(slot);
            } else {
                dynamicArray.restore(slot);
            }
        }

        void mergeAfterIf(
                int before,
                int thenState,
                boolean thenLeaves,
                int elseState,
                boolean elseLeaves,
                boolean hasElse,
                int fallthroughState
        ) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.mergeAfterIf(
                        before,
                        thenState,
                        thenLeaves,
                        elseState,
                        elseLeaves,
                        hasElse,
                        fallthroughState
                );
            } else if (kind == DomainKind.DANGLING) {
                dangling.mergeAfterIf(
                        before,
                        thenState,
                        thenLeaves,
                        elseState,
                        elseLeaves,
                        hasElse,
                        fallthroughState
                );
            } else {
                dynamicArray.mergeAfterIf(
                        before,
                        thenState,
                        thenLeaves,
                        elseState,
                        elseLeaves,
                        hasElse,
                        fallthroughState
                );
            }
        }

        void mergeLoopExitStates(int[] slots, int count) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.mergeLoopExitStates(slots, count);
            } else if (kind == DomainKind.DANGLING) {
                dangling.mergeLoopExitStates(slots, count);
            } else {
                dynamicArray.mergeLoopExitStates(slots, count);
            }
        }

        void mergeStateIntoCurrent(int slot) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.mergeStateIntoCurrent(slot);
            } else if (kind == DomainKind.DANGLING) {
                dangling.mergeStateIntoCurrent(slot);
            } else {
                dynamicArray.mergeStateIntoCurrent(slot);
            }
        }

        void markLocalsDeadForJump(int targetBlockDepth, int snapshotSlot) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.markLocalsDeadForJump(targetBlockDepth, snapshotSlot);
            } else if (kind == DomainKind.DANGLING) {
                dangling.markLocalsDeadForJump(targetBlockDepth, snapshotSlot);
            } else {
                dynamicArray.markLocalsDeadForJump(targetBlockDepth, snapshotSlot);
            }
        }

        void markLoopBodyLocalsDead(SastNode body) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.markLoopBodyLocalsDead(body);
            } else if (kind == DomainKind.DANGLING) {
                dangling.markLoopBodyLocalsDead(body);
            } else {
                dynamicArray.markLoopBodyLocalsDead(body);
            }
        }

        boolean isSuppressed() {
            if (kind == DomainKind.NULL_STATE) {
                return nullState.isSuppressed();
            }

            if (kind == DomainKind.DANGLING) {
                return dangling.isSuppressed();
            }

            return dynamicArray.isSuppressed();
        }

        void setSuppressed(boolean value) {
            if (kind == DomainKind.NULL_STATE) {
                nullState.setSuppressed(value);
            } else if (kind == DomainKind.DANGLING) {
                dangling.setSuppressed(value);
            } else {
                dynamicArray.setSuppressed(value);
            }
        }
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

    void run(SastNode root) {
        checkTopLevel(root);
    }

    private void checkTopLevel(SastNode node) {
        if (node == null) {
            return;
        }

        if (node.kind == SastKind.PROGRAM) {
            for (SastNode item : node.topLevelItems) {
                checkTopLevel(item);
            }

            return;
        }

        if (node.kind == SastKind.NAMESPACE) {
            for (SastNode item : node.items) {
                checkTopLevel(item);
            }

            return;
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            for (SastNode method : node.methods) {
                checkFunction(method);
            }

            return;
        }

        if (node.kind == SastKind.FUNCTION_DECL) {
            checkFunction(node);
        }
    }

    private void checkFunction(SastNode function) {
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

    private boolean checkStmt(SastNode node) {
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

    private boolean checkStmtInner(SastNode node) {
        if (node.kind == SastKind.BLOCK) {
            return checkBlock(node);
        }

        if (node.kind == SastKind.VAR_DECL) {
            domain.checkVarDecl(node);
            return false;
        }

        if (node.kind == SastKind.ASSIGN) {
            domain.checkAssign(node);
            return false;
        }

        if (node.kind == SastKind.IF) {
            return checkIf(node);
        }

        if (node.kind == SastKind.WHILE) {
            return checkWhile(node);
        }

        if (node.kind == SastKind.BREAK) {
            recordJumpState(node.levels, false);
            return true;
        }

        if (node.kind == SastKind.CONTINUE) {
            recordJumpState(1, true);
            return true;
        }

        if (node.kind == SastKind.RETURN) {
            domain.checkExpr(node.value);
            domain.onReturn(node);
            addExit();
            return true;
        }

        if (node.kind == SastKind.EXPR_STMT) {
            domain.checkExpr(node.expr);
            return false;
        }

        return false;
    }

    private boolean checkBlock(SastNode block) {
        int depth = ++blockDepth;
        domain.enterBlock(block, depth);

        boolean terminated = false;

        try {
            for (SastNode statement : block.statements) {
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

    private boolean checkIf(SastNode node) {
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

        boolean savedSuppression = domain.isSuppressed();
        domain.setSuppressed(savedSuppression || !thenFeasible);
        boolean thenLeaves = checkStmt(node.thenBranch);
        domain.setSuppressed(savedSuppression);

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

            domain.setSuppressed(savedSuppression || !elseFeasible);
            elseLeaves = checkStmt(node.elseBranch);
            domain.setSuppressed(savedSuppression);

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

    private boolean checkIfPathInsensitive(SastNode node) {
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

    private boolean checkWhile(SastNode node) {
        domain.checkExpr(node.condition);

        int before = domain.snapshot();
        boolean bodyFeasible = domain.guardPathFeasible(node.condition, true);

        int frameIndex = loopDepth++;
        LoopFrame frame = loopFrames[frameIndex];
        frame.breakHead = -1;
        frame.continueHead = -1;
        frame.bodyBlockDepth = node.body.kind == SastKind.BLOCK
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
            boolean savedSuppression = domain.isSuppressed();
            domain.setSuppressed(true);
            checkStmt(node.body);

            for (SastNode update : node.update) {
                checkStmt(update);
            }

            domain.setSuppressed(savedSuppression);
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

    private boolean checkUpdates(java.util.List<SastNode> updates) {
        for (SastNode update : updates) {
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
