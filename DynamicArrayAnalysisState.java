import java.math.BigInteger;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

final class DynamicArrayAnalysisState {
    private static final int MAX_BRANCH_DEPTH = 12;

    final IdentityHashMap<VariableSymbol, Integer> arrayIndexBySymbol =
            new IdentityHashMap<>();

    VariableSymbol[] arraySymbols = new VariableSymbol[0];
    int arrayCount = 0;

    final IdentityHashMap<VariableSymbol, Integer> pointerIndexBySymbol =
            new IdentityHashMap<>();

    VariableSymbol[] pointerSymbols = new VariableSymbol[0];
    int pointerCount = 0;

    ArrayState[] arrayState = new ArrayState[0];
    FreedState[] freedState = new FreedState[0];
    boolean[] escaped = new boolean[0];

    ArrayOrigin[] pointerState = new ArrayOrigin[0];

    ArrayState[][] arraySnapshots = new ArrayState[0][];
    FreedState[][] freedSnapshots = new FreedState[0][];
    ArrayOrigin[][] pointerSnapshots = new ArrayOrigin[0][];

    int snapshotCount = 0;

    void prepare(SemanticASTNode root) {
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

    int arrayIndexOfSymbol(VariableSymbol symbol) {
        if (symbol == null) {
            return -1;
        }

        Integer index = arrayIndexBySymbol.get(symbol);

        return index == null ? -1 : index;
    }

    int pointerIndexOfSymbol(VariableSymbol symbol) {
        if (symbol == null) {
            return -1;
        }

        Integer index = pointerIndexBySymbol.get(symbol);

        return index == null ? -1 : index;
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

    int snapshot() {
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

    void restore(int snapshotSlot) {
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

    void ensureSnapshotCapacity() {
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

    void mergeAfterIf(
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

    ArrayState arrayStateFromSnapshot(
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

    FreedState freedStateFromSnapshot(
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

    ArrayOrigin pointerOriginFromSnapshot(
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

    ArrayState mergeArrayState(
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

    FreedState mergeFreedState(
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

    ArrayOrigin mergeOrigins(
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

    static boolean isLengthCall(SemanticExprNode expr) {
        return expr instanceof SemanticArrayIntrinsicCallNode n
                && n.kind == SemanticArrayIntrinsicKind.LENGTH;
    }

    static boolean isComparison(String op) {
        return op.equals("==")
                || op.equals("!=")
                || op.equals("<")
                || op.equals("<=")
                || op.equals(">")
                || op.equals(">=");
    }

    static String negate(String op) {
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

    static String reverse(String op) {
        return switch (op) {
            case "<" -> ">";
            case "<=" -> ">=";
            case ">" -> "<";
            case ">=" -> "<=";
            default -> op;
        };
    }

    static String functionDisplayName(FunctionSymbol function) {
        if (function == null) {
            return "<unknown>";
        }

        return function.qualifiedName();
    }
}

enum ArrayState {
    UNINITED,
    INITED,
    MAYBE_INITED
}

enum FreedState {
    NEVER,
    MAYBE,
    YES
}

enum LengthAssertion {
    NONE,
    ZERO,
    NONZERO
}

final class ArrayOrigin {
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

final class LengthGuard {
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
