import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DanglingPointerChecker {
    private static final class DecaySource {
        final SemanticType sourceType;
        final String label;

        DecaySource(SemanticType sourceType, String label) {
            this.sourceType = Objects.requireNonNull(sourceType);
            this.label = Objects.requireNonNull(label);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof DecaySource other)) {
                return false;
            }

            return sourceType.equals(other.sourceType)
                    && label.equals(other.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceType, label);
        }
    }

    private static final class OriginSet {
        static final OriginSet EMPTY =
                new OriginSet(Set.of(), Set.of(), Set.of(), false);

        static final OriginSet UNKNOWN =
                new OriginSet(Set.of(), Set.of(), Set.of(), true);

        final Set<VariableSymbol> frameCells;
        final Set<VariableSymbol> globalCells;
        final Set<DecaySource> decaySources;
        final boolean unknown;

        private OriginSet(
                Set<VariableSymbol> frameCells,
                Set<VariableSymbol> globalCells,
                Set<DecaySource> decaySources,
                boolean unknown
        ) {
            this.frameCells = frameCells;
            this.globalCells = globalCells;
            this.decaySources = decaySources;
            this.unknown = unknown;
        }

        static OriginSet frame(VariableSymbol symbol) {
            return EMPTY.withFrameCell(symbol);
        }

        static OriginSet global(VariableSymbol symbol) {
            return EMPTY.withGlobalCell(symbol);
        }

        boolean hasDecay() {
            return !decaySources.isEmpty();
        }

        boolean mayDangle() {
            return !frameCells.isEmpty() || !decaySources.isEmpty();
        }

        boolean definiteDangle() {
            return !decaySources.isEmpty()
                    || (!frameCells.isEmpty()
                        && !unknown
                        && globalCells.isEmpty());
        }

        boolean possibleDangle() {
            return mayDangle() && !definiteDangle();
        }

        OriginSet withFrameCell(VariableSymbol symbol) {
            if (frameCells.contains(symbol)) {
                return this;
            }

            Set<VariableSymbol> next = new LinkedHashSet<>(frameCells);
            next.add(symbol);

            return new OriginSet(
                    Set.copyOf(next),
                    globalCells,
                    decaySources,
                    unknown
            );
        }

        OriginSet withGlobalCell(VariableSymbol symbol) {
            if (globalCells.contains(symbol)) {
                return this;
            }

            Set<VariableSymbol> next = new LinkedHashSet<>(globalCells);
            next.add(symbol);

            return new OriginSet(
                    frameCells,
                    Set.copyOf(next),
                    decaySources,
                    unknown
            );
        }

        OriginSet withDecay(SemanticType sourceType, String label) {
            DecaySource source = new DecaySource(sourceType, label);

            if (decaySources.contains(source)) {
                return this;
            }

            Set<DecaySource> next = new LinkedHashSet<>(decaySources);
            next.add(source);

            return new OriginSet(
                    frameCells,
                    globalCells,
                    Set.copyOf(next),
                    unknown
            );
        }

        OriginSet withUnknown() {
            if (unknown) {
                return this;
            }

            return new OriginSet(
                    frameCells,
                    globalCells,
                    decaySources,
                    true
            );
        }

        OriginSet union(OriginSet other) {
            if (other == null || other == EMPTY) {
                return this;
            }

            if (this == EMPTY) {
                return other;
            }

            Set<VariableSymbol> frame = new LinkedHashSet<>(frameCells);
            frame.addAll(other.frameCells);

            Set<VariableSymbol> global = new LinkedHashSet<>(globalCells);
            global.addAll(other.globalCells);

            Set<DecaySource> decay = new LinkedHashSet<>(decaySources);
            decay.addAll(other.decaySources);

            return new OriginSet(
                    Set.copyOf(frame),
                    Set.copyOf(global),
                    Set.copyOf(decay),
                    unknown || other.unknown
            );
        }
    }

    private static final class LoopFrame {
        final List<Integer> breakStates = new ArrayList<>();
        final List<Integer> continueStates = new ArrayList<>();
    }

    private final DiagnosticBag diagnostics;

    private final IdentityHashMap<VariableSymbol, Integer> pointerIndexBySymbol =
            new IdentityHashMap<>();

    private VariableSymbol[] pointerSymbols = new VariableSymbol[0];
    private int pointerCount = 0;

    private OriginSet[] pointerState = new OriginSet[0];
    private OriginSet[][] pointerSnapshots = new OriginSet[0][];

    private int snapshotCount = 0;

    private final List<LoopFrame> loopStack = new ArrayList<>();

    private final Set<VariableSymbol> addressTakenPointers =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<SemanticReturnNode> reportedReturnSites =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public DanglingPointerChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public void check(SemanticASTNode node) {
        diagnostics.clear();

        prepareAnalysis(node);

        snapshotCount = 0;
        loopStack.clear();
        addressTakenPointers.clear();
        reportedReturnSites.clear();

        Arrays.fill(pointerState, OriginSet.UNKNOWN);

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

        pointerState = new OriginSet[pointerCount];
        Arrays.fill(pointerState, OriginSet.UNKNOWN);

        pointerSnapshots = new OriginSet[16][pointerCount];

        for (int i = 0; i < pointerSnapshots.length; i++) {
            Arrays.fill(pointerSnapshots[i], OriginSet.UNKNOWN);
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

    private int pointerIndexOfSymbol(VariableSymbol symbol) {
        if (symbol == null
                || !(symbol.type instanceof SemanticPtrType)) {
            return -1;
        }

        Integer index = pointerIndexBySymbol.get(symbol);

        return index == null ? -1 : index;
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
        Arrays.fill(pointerState, OriginSet.UNKNOWN);

        snapshotCount = 0;
        loopStack.clear();
        addressTakenPointers.clear();
        reportedReturnSites.clear();

        if (node.body != null) {
            checkBlock(node.body);
        }
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

            boolean terminated = checkStmt(stmt);

            if (terminated) {
                return true;
            }
        }

        for (int i = 0; i < blockPointerCount; i++) {
            pointerState[blockPointerIndices[i]] = OriginSet.UNKNOWN;
        }

        return false;
    }

    private void checkVarDecl(SemanticVarDeclNode node) {
        if (node == null || node.symbol == null) {
            return;
        }

        int index = pointerIndexOfSymbol(node.symbol);

        if (index >= 0) {
            pointerState[index] = OriginSet.UNKNOWN;
        }

        checkExpr(node.staticInitializer);
    }

    private void checkAssign(SemanticAssignNode node) {
        checkExpr(node.target);
        checkExpr(node.value);

        if (!(node.target.type instanceof SemanticPtrType)
                || node.isCompound()) {
            return;
        }

        OriginSet value = valueOrigins(node.value);

        if (PointerPermissions.isStaticArrayViewDecay(
                node.value.type,
                node.target.type)) {
            value = value.withDecay(
                    node.value.type,
                    describeExpr(node.value)
            );
        }

        applyPointerStore(node.target, value);
    }

    private boolean checkIf(SemanticIfNode node) {
        checkExpr(node.condition);

        int before = snapshot();

        boolean thenTerminates = checkStmt(node.thenBranch);
        int thenState = snapshot();

        restore(before);

        boolean elseTerminates = false;
        int elseState = -1;

        if (node.elseBranch != null) {
            elseTerminates = checkStmt(node.elseBranch);
            elseState = snapshot();

            restore(before);
        }

        mergeAfterIf(
                before,
                thenState,
                thenTerminates,
                elseState,
                elseTerminates,
                node.elseBranch != null
        );

        return node.elseBranch != null
                && thenTerminates
                && elseTerminates;
    }

    private boolean checkWhile(SemanticWhileNode node) {
        checkExpr(node.condition);

        int before = snapshot();

        LoopFrame frame = new LoopFrame();
        loopStack.add(frame);

        boolean bodyTerminates = checkStmt(node.body);
        int afterBody = snapshot();

        List<Integer> backEdgeStates = new ArrayList<>();

        if (!node.update.isEmpty()) {
            if (!bodyTerminates) {
                boolean iterationFallsThrough = true;

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

        loopStack.remove(loopStack.size() - 1);

        restore(before);

        if (!backEdgeStates.isEmpty()) {
            mergeLoopStates(before, backEdgeStates);
        }

        for (int breakState : frame.breakStates) {
            mergeStateIntoCurrent(breakState);
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

    private boolean checkReturn(SemanticReturnNode node) {
        checkExpr(node.value);

        if (node.value == null
                || !(node.value.type instanceof SemanticPtrType)) {
            return true;
        }

        if (!reportedReturnSites.add(node)) {
            return true;
        }

        OriginSet origins = valueOrigins(node.value);

        DecaySource directDecay = directDecaySource(node.value);

        boolean implicitReturnDecay =
                PointerPermissions.isStaticArrayViewDecay(
                        node.value.type,
                        node.function.returnType()
                );

        if (implicitReturnDecay) {
            origins = origins.withDecay(
                    node.value.type,
                    describeExpr(node.value)
            );

            if (directDecay == null) {
                directDecay = new DecaySource(
                        node.value.type,
                        describeExpr(node.value)
                );
            }
        }

        if (origins.hasDecay()) {
            if (directDecay != null) {
                reportDecayEscape(node, directDecay, origins);
            } else {
                reportIndirectDecayEscape(node, origins);
            }

            return true;
        }

        if (origins.definiteDangle()) {
            diagnostics.error(
                    "Returning a pointer to "
                            + formatLocalOrigins(origins.frameCells)
                            + " from function '"
                            + functionDisplayName(node.function)
                            + "' (dangling after return)"
            );
        } else if (origins.possibleDangle()) {
            diagnostics.warning(
                    "Returning a pointer that may refer to "
                            + formatLocalOrigins(origins.frameCells)
                            + " from function '"
                            + functionDisplayName(node.function)
                            + "' (possibly dangling after return)"
            );
        }

        return true;
    }

    private void reportDecayEscape(
            SemanticReturnNode node,
            DecaySource source,
            OriginSet origins
    ) {
        String functionName = functionDisplayName(node.function);
        String returnType = node.function.returnType().describe();
        String sourceType = source.sourceType.describe();

        StringBuilder message = new StringBuilder();

        message.append("Cannot return a decayed array view from function '")
                .append(functionName)
                .append("': the return type '")
                .append(returnType)
                .append("' needs a hidden dynamic-array descriptor, but the value '")
                .append(source.label)
                .append("' has type '")
                .append(sourceType)
                .append("' and points directly at static array storage. "
                        + "The compiler creates that descriptor as a hidden local "
                        + "(Franko_Dynamic_Array) in the stack frame of '")
                .append(functionName)
                .append("'; it is destroyed when '")
                .append(functionName)
                .append("' returns, so the caller receives a view pointing to "
                        + "dead stack storage. Did you mean to take '")
                .append(returnType)
                .append("' as the parameter instead, so the caller performs the "
                        + "decay at the call site and the function only consumes "
                        + "the view? If the function must return a view of the "
                        + "static array, return '")
                .append(sourceType)
                .append("' and let the caller decay the result. A literal-backed "
                        + "view or an existing global dynamic view is also safe "
                        + "to return.");

        if (!origins.globalCells.isEmpty()) {
            message.append(" The array data itself is global (")
                    .append(quoteList(sortedNames(origins.globalCells)))
                    .append("), but the returned view points at the stack "
                            + "descriptor, not at that global data; making the "
                            + "data global does not keep the descriptor alive.");
        }

        if (!origins.frameCells.isEmpty()) {
            message.append(" The descriptor's data also points into ")
                    .append(formatLocalOrigins(origins.frameCells))
                    .append(origins.frameCells.size() == 1
                            ? ", which dies at the same return."
                            : ", which die at the same return.");
        }

        diagnostics.error(message.toString());
    }

    private void reportIndirectDecayEscape(
            SemanticReturnNode node,
            OriginSet origins
    ) {
        String functionName = functionDisplayName(node.function);

        List<String> sourceTypes = new ArrayList<>();

        for (DecaySource source : origins.decaySources) {
            String described = source.sourceType.describe();

            if (!sourceTypes.contains(described)) {
                sourceTypes.add(described);
            }
        }

        Collections.sort(sourceTypes);

        String subject = node.value instanceof SemanticVarExprNode
                ? "variable '" + describeExpr(node.value) + "'"
                : "value '" + describeExpr(node.value) + "'";

        StringBuilder message = new StringBuilder();

        message.append("Cannot return a decayed array view from function '")
                .append(functionName)
                .append("': ")
                .append(subject);

        if (origins.decaySources.size() == 1) {
            message.append(" holds a value produced by decaying a static "
                    + "array view.");
        } else {
            message.append(" may hold a value produced by decaying a static "
                    + "array view (possible source types: ")
                    .append(quoteList(sourceTypes))
                    .append(").");
        }

        message.append(" The decay descriptor is local to '")
                .append(functionName)
                .append("' and dies when '")
                .append(functionName)
                .append("' returns. To fix this, return the static view type "
                        + "instead and let the caller perform the decay.");

        diagnostics.error(message.toString());
    }

    private DecaySource directDecaySource(SemanticExprNode value) {
        if (!(value instanceof SemanticCastExprNode cast)) {
            return null;
        }

        if (PointerPermissions.isStaticArrayViewDecay(
                cast.expr.type,
                cast.type)) {
            return new DecaySource(
                    cast.expr.type,
                    describeExpr(cast.expr)
            );
        }

        return directDecaySource(cast.expr);
    }

    private OriginSet storageOrigins(SemanticExprNode expr) {
        if (expr == null) {
            return OriginSet.UNKNOWN;
        }

        if (expr instanceof SemanticVarExprNode n) {
            if (n.symbol instanceof GlobalVariableSymbol) {
                return OriginSet.global(n.symbol);
            }

            return OriginSet.frame(n.symbol);
        }

        if (expr instanceof SemanticDerefNode n) {
            return valueOrigins(n.expr);
        }

        if (expr instanceof SemanticMemberAccessNode n) {
            return storageOrigins(n.target);
        }

        if (expr instanceof SemanticArrayAccessNode n) {
            return storageOrigins(n.target);
        }

        if (expr instanceof SemanticCastExprNode n) {
            return storageOrigins(n.expr);
        }

        return OriginSet.UNKNOWN;
    }

    private OriginSet valueOrigins(SemanticExprNode expr) {
        if (expr == null) {
            return OriginSet.UNKNOWN;
        }

        if (expr instanceof SemanticNullLiteralNode) {
            return OriginSet.UNKNOWN;
        }

        if (expr instanceof SemanticGetPtrNode n) {
            return storageOrigins(n.target);
        }

        if (expr instanceof SemanticVarExprNode n) {
            int index = pointerIndexOfSymbol(n.symbol);

            return index < 0
                    ? OriginSet.UNKNOWN
                    : pointerState[index];
        }

        if (expr instanceof SemanticDerefNode n) {
            return readPointerCells(valueOrigins(n.expr));
        }

        if (expr instanceof SemanticCastExprNode n) {
            OriginSet origins = valueOrigins(n.expr);

            if (PointerPermissions.isStaticArrayViewDecay(
                    n.expr.type,
                    n.type)) {
                origins = origins.withDecay(
                        n.expr.type,
                        describeExpr(n.expr)
                );
            }

            return origins;
        }

        if (expr instanceof SemanticFunctionCallNode) {
            return OriginSet.UNKNOWN;
        }

        if (expr instanceof SemanticMemberAccessNode) {
            return OriginSet.UNKNOWN;
        }

        if (expr instanceof SemanticArrayAccessNode) {
            return OriginSet.UNKNOWN;
        }

        return OriginSet.UNKNOWN;
    }

    private OriginSet readPointerCells(OriginSet cells) {
        if (cells == null) {
            return OriginSet.UNKNOWN;
        }

        OriginSet result = cells.unknown
                ? OriginSet.UNKNOWN
                : OriginSet.EMPTY;

        if (!cells.decaySources.isEmpty()) {
            result = result.withUnknown();
        }

        for (VariableSymbol cell : cells.frameCells) {
            result = result.union(readPointerCell(cell));
        }

        for (VariableSymbol cell : cells.globalCells) {
            result = result.union(readPointerCell(cell));
        }

        return result;
    }

    private OriginSet readPointerCell(VariableSymbol cell) {
        int index = pointerIndexOfSymbol(cell);

        if (index < 0) {
            return OriginSet.UNKNOWN;
        }

        return pointerState[index];
    }

    private void applyPointerStore(
            SemanticExprNode target,
            OriginSet value
    ) {
        if (target instanceof SemanticVarExprNode n) {
            int index = pointerIndexOfSymbol(n.symbol);

            if (index >= 0) {
                pointerState[index] = value;
            } else {
                invalidateAllPointerState();
            }

            return;
        }

        if (target instanceof SemanticDerefNode n) {
            VariableSymbol cell = exactlyOneTrackedPointerCell(
                    valueOrigins(n.expr)
            );

            if (cell != null) {
                pointerState[pointerIndexOfSymbol(cell)] = value;
            } else {
                invalidateAllPointerState();
            }

            return;
        }

        invalidateAllPointerState();
    }

    private VariableSymbol exactlyOneTrackedPointerCell(OriginSet cells) {
        if (cells == null
                || cells.unknown
                || !cells.decaySources.isEmpty()) {
            return null;
        }

        if (cells.frameCells.size() + cells.globalCells.size() != 1) {
            return null;
        }

        VariableSymbol cell = !cells.frameCells.isEmpty()
                ? cells.frameCells.iterator().next()
                : cells.globalCells.iterator().next();

        return pointerIndexOfSymbol(cell) < 0 ? null : cell;
    }

    private void invalidateAllPointerState() {
        Arrays.fill(pointerState, OriginSet.UNKNOWN);
    }

    private void checkExpr(SemanticExprNode expr) {
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

            invalidateAfterCall(n.args);
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
            markAddressTaken(n);
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

    private void invalidateAfterCall(List<SemanticExprNode> args) {
        for (int i = 0; i < pointerCount; i++) {
            if (pointerSymbols[i] instanceof GlobalVariableSymbol) {
                pointerState[i] = OriginSet.UNKNOWN;
            }
        }

        for (SemanticExprNode arg : args) {
            if (!(arg.type instanceof SemanticPtrType)) {
                continue;
            }

            OriginSet origins = valueOrigins(arg);

            for (VariableSymbol cell : origins.frameCells) {
                int index = pointerIndexOfSymbol(cell);

                if (index >= 0) {
                    pointerState[index] = OriginSet.UNKNOWN;
                }
            }

            for (VariableSymbol cell : origins.globalCells) {
                int index = pointerIndexOfSymbol(cell);

                if (index >= 0) {
                    pointerState[index] = OriginSet.UNKNOWN;
                }
            }
        }

        for (VariableSymbol symbol : addressTakenPointers) {
            int index = pointerIndexOfSymbol(symbol);

            if (index >= 0) {
                pointerState[index] = OriginSet.UNKNOWN;
            }
        }
    }

    private void markAddressTaken(SemanticGetPtrNode node) {
        PtrPermission permission =
                PointerPermissions.pointerPermission(node.type);

        if (permission == PtrPermission.READ_ONLY) {
            return;
        }

        OriginSet storage = storageOrigins(node.target);

        for (VariableSymbol cell : storage.frameCells) {
            if (pointerIndexOfSymbol(cell) >= 0) {
                addressTakenPointers.add(cell);
            }
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
            pointerSnapshots[i] = new OriginSet[pointerCount];
            Arrays.fill(pointerSnapshots[i], OriginSet.UNKNOWN);
        }
    }

    private void mergeAfterIf(
            int before,
            int thenState,
            boolean thenTerminates,
            int elseState,
            boolean elseTerminates,
            boolean hasElse
    ) {
        for (int i = 0; i < pointerCount; i++) {
            boolean hasContinuingPath = false;
            OriginSet merged = null;

            if (!thenTerminates) {
                hasContinuingPath = true;
                merged = mergeOriginSets(
                        merged,
                        originSetFromSnapshot(thenState, i, before)
                );
            }

            if (hasElse) {
                if (!elseTerminates) {
                    hasContinuingPath = true;
                    merged = mergeOriginSets(
                            merged,
                            originSetFromSnapshot(elseState, i, before)
                    );
                }
            } else {
                hasContinuingPath = true;
                merged = mergeOriginSets(
                        merged,
                        originSetFromSnapshot(before, i, before)
                );
            }

            if (hasContinuingPath && merged != null) {
                pointerState[i] = merged;
            } else {
                pointerState[i] = originSetFromSnapshot(before, i, before);
            }
        }
    }

    private void mergeLoopStates(
            int before,
            List<Integer> iterationStates
    ) {
        for (int i = 0; i < pointerCount; i++) {
            OriginSet merged = originSetFromSnapshot(before, i, before);

            for (int slot : iterationStates) {
                merged = mergeOriginSets(
                        merged,
                        originSetFromSnapshot(slot, i, before)
                );
            }

            pointerState[i] = merged == null ? OriginSet.UNKNOWN : merged;
        }
    }

    private void mergeStateIntoCurrent(int slot) {
        for (int i = 0; i < pointerCount; i++) {
            pointerState[i] = mergeOriginSets(
                    pointerState[i],
                    originSetFromSnapshot(slot, i, slot)
            );
        }
    }

    private OriginSet originSetFromSnapshot(
            int snapshotSlot,
            int index,
            int fallbackSlot
    ) {
        if (index < 0 || index >= pointerCount) {
            return OriginSet.UNKNOWN;
        }

        if (snapshotSlot >= 0
                && snapshotSlot < snapshotCount
                && pointerSnapshots[snapshotSlot] != null) {
            OriginSet value = pointerSnapshots[snapshotSlot][index];

            if (value != null) {
                return value;
            }
        }

        if (fallbackSlot >= 0
                && fallbackSlot < snapshotCount
                && pointerSnapshots[fallbackSlot] != null) {
            OriginSet fallback = pointerSnapshots[fallbackSlot][index];

            if (fallback != null) {
                return fallback;
            }
        }

        return OriginSet.UNKNOWN;
    }

    private OriginSet mergeOriginSets(OriginSet left, OriginSet right) {
        if (left == null) {
            return right == null ? OriginSet.UNKNOWN : right;
        }

        if (right == null) {
            return left;
        }

        return left.union(right);
    }

    private String functionDisplayName(FunctionSymbol function) {
        if (function == null) {
            return "<unknown>";
        }

        return function.qualifiedName();
    }

    private String formatLocalOrigins(Set<VariableSymbol> cells) {
        List<String> parameters = new ArrayList<>();
        List<String> locals = new ArrayList<>();

        for (VariableSymbol symbol : cells) {
            if (symbol instanceof ParameterSymbol) {
                parameters.add(symbol.name);
            } else {
                locals.add(symbol.qualifiedName());
            }
        }

        Collections.sort(parameters);
        Collections.sort(locals);

        List<String> parts = new ArrayList<>();

        if (!parameters.isEmpty()) {
            parts.add(
                    (parameters.size() == 1 ? "parameter " : "parameters ")
                            + quoteList(parameters)
            );
        }

        if (!locals.isEmpty()) {
            parts.add(
                    (locals.size() == 1 ? "local variable " : "local variables ")
                            + quoteList(locals)
            );
        }

        if (parts.isEmpty()) {
            return "local storage";
        }

        return String.join(" and ", parts);
    }

    private List<String> sortedNames(Set<VariableSymbol> symbols) {
        List<String> names = new ArrayList<>();

        for (VariableSymbol symbol : symbols) {
            names.add(symbol.qualifiedName());
        }

        Collections.sort(names);

        return names;
    }

    private String quoteList(List<String> values) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append('\'').append(values.get(i)).append('\'');
        }

        return sb.toString();
    }

    private String describeExpr(SemanticExprNode expr) {
        if (expr == null) {
            return "<expression>";
        }

        if (expr instanceof SemanticVarExprNode n) {
            return n.symbol.qualifiedName();
        }

        if (expr instanceof SemanticGetPtrNode n) {
            PtrPermission permission =
                    PointerPermissions.pointerPermission(n.type);

            return PointerPermissions.creationIntrinsic(permission)
                    + "("
                    + describeExpr(n.target)
                    + ")";
        }

        if (expr instanceof SemanticDerefNode n) {
            return "$(" + describeExpr(n.expr) + ")";
        }

        if (expr instanceof SemanticMemberAccessNode n) {
            return describeExpr(n.target) + "." + n.field.name;
        }

        if (expr instanceof SemanticArrayAccessNode n) {
            return describeExpr(n.target) + "[" + describeExpr(n.index) + "]";
        }

        if (expr instanceof SemanticCastExprNode n) {
            return (n.checked ? "unsafe_cast" : "cast")
                    + "<"
                    + n.type.describe()
                    + ">("
                    + describeExpr(n.expr)
                    + ")";
        }

        if (expr instanceof SemanticNullLiteralNode) {
            return "null";
        }

        if (expr instanceof SemanticIntLiteralNode n) {
            return n.rawValue;
        }

        if (expr instanceof SemanticFunctionCallNode n) {
            return n.function.name + "(...)";
        }

        return "<expression>";
    }
}
