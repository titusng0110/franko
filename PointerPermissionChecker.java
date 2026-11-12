import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PointerPermissionChecker {
    private final DiagnosticBag diagnostics;

    private final Map<Symbol, PtrPermission> highWaterMark =
            new IdentityHashMap<>();

    private final List<Symbol> trackedOrder = new ArrayList<>();

    public PointerPermissionChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public void check(SastNode node) {
        diagnostics.clear();

        checkNode(node);
    }

    public PassResult check(SastDocument document) {
        check(document.root);
        return new PassResult(
                document.withPhase(SastPhase.PERMISSIONS),
                diagnostics.diagnostics()
        );
    }

    private void checkNode(SastNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkNodeInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkNodeInner(SastNode node) {
        if (node == null) {
            return;
        }

        if (node.kind == SastKind.PROGRAM) {
            for (SastNode item : node.topLevelItems) {
                checkNode(item);
            }
            return;
        }

        if (node.kind == SastKind.NAMESPACE) {
            for (SastNode item : node.items) {
                checkNode(item);
            }
            return;
        }

        if (node.kind == SastKind.FUNCTION_DECL) {
            checkFunction(node);
            return;
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            for (SastNode method : node.methods) {
                checkFunction(method);
            }
            return;
        }
    }

    private void checkFunction(SastNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkFunctionInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkFunctionInner(SastNode node) {
        highWaterMark.clear();
        trackedOrder.clear();

        for (Symbol parameter : node.parameterVariables) {
            trackIfPointer(parameter);
        }

        if (node.body != null) {
            checkStmt(node.body);
        }

        reportUnusedPermissions();
    }

    private void trackIfPointer(Symbol symbol) {
        if (symbol == null
                || symbol.kind == SymbolKind.GLOBAL
                || !(symbol.type.kind == SastTypeKind.PTR)) {
            return;
        }

        if (symbol.type.permission == PtrPermission.READ_ONLY) {
            return;
        }

        if (!highWaterMark.containsKey(symbol)) {
            highWaterMark.put(symbol, null);
            trackedOrder.add(symbol);
        }
    }

    private void reportUnusedPermissions() {
        for (Symbol symbol : trackedOrder) {
            PtrPermission exercised = highWaterMark.get(symbol);

            if (exercised == null) {
                continue;
            }

            SastType type = symbol.type;

            if (exercised == PtrPermission.READ_ONLY) {
                diagnostics.warning("'"
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
                diagnostics.warning("'"
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

    private void credit(Symbol symbol, PtrPermission level) {
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

    private void checkStmt(SastNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkStmtInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkStmtInner(SastNode node) {
        if (node == null) {
            return;
        }

        if (node.kind == SastKind.BLOCK) {
            for (SastNode stmt : node.statements) {
                checkStmt(stmt);
            }
            return;
        }

        if (node.kind == SastKind.VAR_DECL) {
            trackIfPointer(node.symbol);
            return;
        }

        if (node.kind == SastKind.VAR_DECL_INIT) {
            trackIfPointer(node.symbol);

            for (SastNode initializer : node.initializers) {
                checkStmt(initializer);
            }

            return;
        }

        if (node.kind == SastKind.ASSIGN) {
            markPath(node.target, PtrPermission.MUTABLE);
            markValue(node.value, requiredLevel(node.target.type));
            return;
        }

        if (node.kind == SastKind.IF) {
            markValue(node.condition, PtrPermission.READ_ONLY);
            checkStmt(node.thenBranch);
            checkStmt(node.elseBranch);
            return;
        }

        if (node.kind == SastKind.WHILE) {
            markValue(node.condition, PtrPermission.READ_ONLY);
            checkStmt(node.body);

            for (SastNode updateStmt : node.update) {
                checkStmt(updateStmt);
            }

            return;
        }

        if (node.kind == SastKind.RETURN) {
            if (node.value != null) {
                markValue(
                        node.value,
                        requiredLevel(node.function.returnType())
                );
            }

            return;
        }

        if (node.kind == SastKind.EXPR_STMT) {
            markValue(node.expr, PtrPermission.READ_ONLY);
        }
    }

    private static PtrPermission requiredLevel(SastType type) {
        return PointerPermissions.pointerPermission(type);
    }

    private void markValue(SastNode expr, PtrPermission level) {
        if (expr == null) {
            return;
        }

        if (expr.kind == SastKind.VAR_EXPR) {
            credit(expr.symbol, level);
            return;
        }

        if (expr.kind == SastKind.DEREF
                || expr.kind == SastKind.ARRAY_ACCESS
                || expr.kind == SastKind.MEMBER_ACCESS) {
            markPath(expr, PtrPermission.READ_ONLY);
            return;
        }

        if (expr.kind == SastKind.GET_PTR) {
            markPath(expr.target, PointerPermissions.pointerPermission(expr.type));
            return;
        }

        if (expr.kind == SastKind.CAST) {
            markValue(expr.expr, requiredLevel(expr.type));
            return;
        }

        if (expr.kind == SastKind.AGGREGATE_LITERAL) {
            for (SastNode field : expr.fields) {
                markValue(field.value, requiredLevel(field.field.type));
            }

            return;
        }

        if (expr.kind == SastKind.FUNCTION_CALL) {
            for (int i = 0; i < expr.args.size(); i++) {
                markValue(expr.args.get(i), parameterLevel(expr, i));
            }

            return;
        }

        if (expr.kind == SastKind.ARRAY_INTRINSIC_CALL) {
            markIntrinsic(expr);
            return;
        }

        if (expr.kind == SastKind.UNARY_OP) {
            markValue(expr.expr, PtrPermission.READ_ONLY);
            return;
        }

        if (expr.kind == SastKind.BIN_OP) {
            markValue(expr.left, PtrPermission.READ_ONLY);
            markValue(expr.right, PtrPermission.READ_ONLY);
        }
    }

    private static PtrPermission parameterLevel(
            SastNode call,
            int index
    ) {
        List<SastType> parameterTypes = call.function.parameterTypes();

        if (index >= parameterTypes.size()) {
            return PtrPermission.READ_ONLY;
        }

        return requiredLevel(parameterTypes.get(index));
    }

    private void markIntrinsic(SastNode node) {
        switch (node.intrinsicKind) {
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

        for (SastNode arg : node.args) {
            markValue(arg, PtrPermission.READ_ONLY);
        }
    }

    private void markPath(SastNode path, PtrPermission level) {
        if (path == null) {
            return;
        }

        if (path.kind == SastKind.DEREF) {
            markRegion(path, level);
            return;
        }

        if (path.kind == SastKind.ARRAY_ACCESS) {
            markPath(path.target, level);
            markValue(path.index, PtrPermission.READ_ONLY);
            return;
        }

        if (path.kind == SastKind.MEMBER_ACCESS) {
            markPath(path.target, level);
        }
    }

    private void markRegion(SastNode deref, PtrPermission level) {
        if (deref.expr.kind == SastKind.VAR_EXPR
                && deref.expr.type.kind == SastTypeKind.PTR) {
            credit(deref.expr.symbol, level);
            return;
        }

        markValue(deref.expr, PtrPermission.READ_ONLY);
    }
}
