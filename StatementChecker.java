import java.math.BigInteger;

public class StatementChecker {
    private final DiagnosticBag diagnostics;
    private final DeclarationChecker declarations;
    private final ExpressionChecker expressions;
    private final TypeChecker types;

    public StatementChecker(
            DiagnosticBag diagnostics,
            DeclarationChecker declarations,
            ExpressionChecker expressions,
            TypeChecker types
    ) {
        this.diagnostics = diagnostics;
        this.declarations = declarations;
        this.expressions = expressions;
        this.types = types;
    }

    public void checkStmt(SastNode node) {
        if (node == null) {
            return;
        }

        if (node.kind == SastKind.BLOCK) {
            visitBlock(node);
            return;
        }

        if (node.kind == SastKind.VAR_DECL) {
            declarations.checkVarDecl(node);
            return;
        }

        if (node.kind == SastKind.ASSIGN) {
            visitAssign(node);
            return;
        }

        if (node.kind == SastKind.IF) {
            visitIf(node);
            return;
        }

        if (node.kind == SastKind.WHILE) {
            visitWhile(node);
            return;
        }

        if (node.kind == SastKind.BREAK) {

            return;
        }

        if (node.kind == SastKind.CONTINUE) {

            return;
        }

        if (node.kind == SastKind.RETURN) {
            visitReturnOutsideFunctionChecker(node);
            return;
        }

        if (node.kind == SastKind.EXPR_STMT) {
            visitExprStmt(node);
            return;
        }

        diagnostics.error("Unknown semantic statement node: "
                + node.getClass().getSimpleName());
    }

    private void visitBlock(SastNode node) {
        for (SastNode stmt : node.statements) {
            checkStmt(stmt);
        }
    }

    private void visitExprStmt(SastNode node) {
        expressions.checkExpr(node.expr);
    }

    private void visitAssign(SastNode node) {
        expressions.checkExpr(node.target);
        expressions.checkExpr(node.value);

        if (!isStorageBackedLValue(node.target)) {
            diagnostics.error("Left-hand side of assignment must be a pointable storage-backed lvalue");
        } else {
            expressions.ensureRegionPermission(
                    node.target,
                    PtrPermission.MUTABLE,
                    "assignment"
            );
        }

        if (types.isVoidType(node.target.type)) {
            diagnostics.error("Left-hand side of assignment cannot have void type");
            return;
        }

        if (!types.ensureValueExpression(
                node.value,
                "Right-hand side of assignment"
        )) {
            return;
        }

        if (node.isCompound()) {
            checkCompoundAssign(node);
            return;
        }

        types.ensureAssignable(
                node.target.type,
                node.value,
                "Invalid assignment"
        );
    }

    private void checkCompoundAssign(SastNode node) {
        String baseOp = node.baseOp();

        if (("/".equals(baseOp) || "%".equals(baseOp))
                && node.value.isConstant()
                && BigInteger.ZERO.equals(node.value.constantValue)) {
            diagnostics.error("Division by zero in compile-time constant expression");
        }

        types.ensureSameIntegralTypeOrFit(
                node.target,
                node.value,
                node.op
        );
    }

    private void visitIf(SastNode node) {
        expressions.checkExpr(node.condition);

        if (types.ensureValueExpression(
                node.condition,
                "if condition"
        )) {
            types.ensureIntegral(
                    node.condition.type,
                    "if condition must be an integer"
            );
        }

        checkStmt(node.thenBranch);

        if (node.elseBranch != null) {
            checkStmt(node.elseBranch);
        }
    }

    private void visitWhile(SastNode node) {
        expressions.checkExpr(node.condition);

        if (types.ensureValueExpression(
                node.condition,
                "while condition"
        )) {
            types.ensureIntegral(
                    node.condition.type,
                    "while condition must be an integer"
            );
        }

        checkStmt(node.body);

        for (SastNode updateStmt : node.update) {
            checkStmt(updateStmt);
        }
    }

    private void visitReturnOutsideFunctionChecker(SastNode node) {
        if (node.value != null) {
            expressions.checkExpr(node.value);
        }

        diagnostics.error("Return statement was checked outside FunctionChecker");
    }

    private boolean isStorageBackedLValue(SastNode expr) {
        if (expr == null || !expr.isLValue()) {
            return false;
        }

        if (expr.kind == SastKind.VAR_EXPR) {
            return true;
        }

        if (expr.kind == SastKind.DEREF) {
            return true;
        }

        if (expr.kind == SastKind.ARRAY_ACCESS) {
            return isStorageBackedLValue(expr.target);
        }

        if (expr.kind == SastKind.MEMBER_ACCESS) {
            return isStorageBackedLValue(expr.target);
        }

        return false;
    }
}
