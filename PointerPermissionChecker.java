import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PointerPermissionChecker {
    private final DiagnosticBag diagnostics;

    private final Map<VariableSymbol, PtrPermission> highWaterMark =
            new IdentityHashMap<>();

    private final List<VariableSymbol> trackedOrder = new ArrayList<>();

    public PointerPermissionChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public void check(SemanticASTNode node) {
        diagnostics.clear();

        checkNode(node);
    }

    private void checkNode(SemanticASTNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticProgramNode n) {
            for (SemanticASTNode item : n.topLevelItems) {
                checkNode(item);
            }
            return;
        }

        if (node instanceof SemanticNamespaceNode n) {
            for (SemanticASTNode item : n.items) {
                checkNode(item);
            }
            return;
        }

        if (node instanceof SemanticFunctionDeclNode n) {
            checkFunction(n);
            return;
        }

        if (node instanceof SemanticStructDeclNode n) {
            for (SemanticFunctionDeclNode method : n.methods) {
                checkFunction(method);
            }
            return;
        }
    }

    private void checkFunction(SemanticFunctionDeclNode node) {
        highWaterMark.clear();
        trackedOrder.clear();

        for (VariableSymbol parameter : node.parameterVariables) {
            trackIfPointer(parameter);
        }

        if (node.body != null) {
            checkStmt(node.body);
        }

        reportUnusedPermissions();
    }

    private void trackIfPointer(VariableSymbol symbol) {
        if (symbol == null
                || symbol instanceof GlobalVariableSymbol
                || !(symbol.type instanceof SemanticPtrType ptr)) {
            return;
        }

        if (ptr.permission == PtrPermission.READ_ONLY) {
            return;
        }

        if (!highWaterMark.containsKey(symbol)) {
            highWaterMark.put(symbol, null);
            trackedOrder.add(symbol);
        }
    }

    private void reportUnusedPermissions() {
        for (VariableSymbol symbol : trackedOrder) {
            PtrPermission exercised = highWaterMark.get(symbol);

            if (exercised == null) {
                continue;
            }

            SemanticPtrType type = (SemanticPtrType) symbol.type;

            if (exercised == PtrPermission.READ_ONLY) {
                diagnostics.warning("Warning: '"
                        + type.describe()
                        + " "
                        + symbol.name
                        + "' is only ever read from. Consider downgrading to "
                        + "'view<"
                        + type.referencedType.describe()
                        + ">' for maximum safety.");
                continue;
            }

            if (exercised == PtrPermission.MUTABLE
                    && type.permission == PtrPermission.OWNER) {
                diagnostics.warning("Warning: '"
                        + type.describe()
                        + " "
                        + symbol.name
                        + "' never exercises allocation or lifecycle "
                        + "permissions. Consider downgrading to 'mview<"
                        + type.referencedType.describe()
                        + ">' for tighter security.");
            }
        }
    }

    private void credit(VariableSymbol symbol, PtrPermission level) {
        if (symbol == null) {
            return;
        }

        trackIfPointer(symbol);

        if (!highWaterMark.containsKey(symbol)) {
            return;
        }

        PtrPermission current = highWaterMark.get(symbol);

        if (current == null || level.ordinal() < current.ordinal()) {
            highWaterMark.put(symbol, level);
        }
    }

    private void checkStmt(SemanticStmtNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticBlockNode n) {
            for (SemanticStmtNode stmt : n.statements) {
                checkStmt(stmt);
            }
            return;
        }

        if (node instanceof SemanticVarDeclNode n) {
            trackIfPointer(n.symbol);
            return;
        }

        if (node instanceof SemanticAssignNode n) {
            markPath(n.target, PtrPermission.MUTABLE);
            markValue(n.value, requiredLevel(n.target.type));
            return;
        }

        if (node instanceof SemanticIfNode n) {
            markValue(n.condition, PtrPermission.READ_ONLY);
            checkStmt(n.thenBranch);
            checkStmt(n.elseBranch);
            return;
        }

        if (node instanceof SemanticWhileNode n) {
            markValue(n.condition, PtrPermission.READ_ONLY);
            checkStmt(n.body);

            for (SemanticStmtNode updateStmt : n.update) {
                checkStmt(updateStmt);
            }

            return;
        }

        if (node instanceof SemanticReturnNode n) {
            if (n.value != null) {
                markValue(
                        n.value,
                        requiredLevel(n.function.returnType())
                );
            }

            return;
        }

        if (node instanceof SemanticExprStmtNode n) {
            markValue(n.expr, PtrPermission.READ_ONLY);
        }
    }

    private static PtrPermission requiredLevel(SemanticType type) {
        return PointerPermissions.pointerPermission(type);
    }

    private void markValue(SemanticExprNode expr, PtrPermission level) {
        if (expr == null) {
            return;
        }

        if (expr instanceof SemanticVarExprNode n) {
            credit(n.symbol, level);
            return;
        }

        if (expr instanceof SemanticDerefNode
                || expr instanceof SemanticArrayAccessNode
                || expr instanceof SemanticMemberAccessNode) {
            markPath(expr, PtrPermission.READ_ONLY);
            return;
        }

        if (expr instanceof SemanticGetPtrNode n) {
            markPath(n.target, PointerPermissions.pointerPermission(n.type));
            return;
        }

        if (expr instanceof SemanticCastExprNode n) {
            markValue(n.expr, requiredLevel(n.type));
            return;
        }

        if (expr instanceof SemanticFunctionCallNode n) {
            for (int i = 0; i < n.args.size(); i++) {
                markValue(n.args.get(i), parameterLevel(n, i));
            }

            return;
        }

        if (expr instanceof SemanticArrayIntrinsicCallNode n) {
            markIntrinsic(n);
            return;
        }

        if (expr instanceof SemanticUnaryOpNode n) {
            markValue(n.expr, PtrPermission.READ_ONLY);
            return;
        }

        if (expr instanceof SemanticBinOpNode n) {
            markValue(n.left, PtrPermission.READ_ONLY);
            markValue(n.right, PtrPermission.READ_ONLY);
        }
    }

    private static PtrPermission parameterLevel(
            SemanticFunctionCallNode call,
            int index
    ) {
        List<SemanticType> parameterTypes = call.function.parameterTypes();

        if (index >= parameterTypes.size()) {
            return PtrPermission.READ_ONLY;
        }

        return requiredLevel(parameterTypes.get(index));
    }

    private void markIntrinsic(SemanticArrayIntrinsicCallNode node) {
        switch (node.kind) {
            case INIT, INIT_ZERO, RESIZE, UNINIT ->
                    markPath(node.receiver, PtrPermission.OWNER);

            case MEMSET, MEMCPY, MEMMOVE,
                 WRITE_UINT16_LE, WRITE_UINT16_BE,
                 WRITE_UINT32_LE, WRITE_UINT32_BE,
                 WRITE_UINT64_LE, WRITE_UINT64_BE,
                 WRITE_INT16_LE, WRITE_INT16_BE,
                 WRITE_INT32_LE, WRITE_INT32_BE,
                 WRITE_INT64_LE, WRITE_INT64_BE,
                 WRITE_BIT, FLIP_BIT,
                 BITWISE_NOT_MASK, BITWISE_XOR_MASK,
                 BITWISE_AND_MASK, BITWISE_OR_MASK,
                 SHIFT_BITS_LEFT, SHIFT_BITS_RIGHT ->
                    markPath(node.receiver, PtrPermission.MUTABLE);

            case LENGTH, MEMCMP, MEMCHR, MEMMEM,
                 READ_UINT16_LE, READ_UINT16_BE,
                 READ_UINT32_LE, READ_UINT32_BE,
                 READ_UINT64_LE, READ_UINT64_BE,
                 READ_INT16_LE, READ_INT16_BE,
                 READ_INT32_LE, READ_INT32_BE,
                 READ_INT64_LE, READ_INT64_BE,
                 READ_BIT, COUNT_SET_BITS ->
                    markPath(node.receiver, PtrPermission.READ_ONLY);
        }

        for (SemanticExprNode arg : node.args) {
            markValue(arg, PtrPermission.READ_ONLY);
        }
    }

    private void markPath(SemanticExprNode path, PtrPermission level) {
        if (path == null) {
            return;
        }

        if (path instanceof SemanticDerefNode n) {
            markRegion(n, level);
            return;
        }

        if (path instanceof SemanticArrayAccessNode n) {
            markPath(n.target, level);
            markValue(n.index, PtrPermission.READ_ONLY);
            return;
        }

        if (path instanceof SemanticMemberAccessNode n) {
            markPath(n.target, level);
        }
    }

    private void markRegion(SemanticDerefNode deref, PtrPermission level) {
        if (deref.expr instanceof SemanticVarExprNode n
                && n.type instanceof SemanticPtrType) {
            credit(n.symbol, level);
            return;
        }

        markValue(deref.expr, PtrPermission.READ_ONLY);
    }
}
