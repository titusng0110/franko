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

    public void checkStmt(SemanticStmtNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticBlockNode n) {
            visitBlock(n);
            return;
        }

        if (node instanceof SemanticVarDeclNode n) {
            declarations.checkVarDecl(n);
            return;
        }

        if (node instanceof SemanticAssignNode n) {
            visitAssign(n);
            return;
        }

        if (node instanceof SemanticIfNode n) {
            visitIf(n);
            return;
        }

        if (node instanceof SemanticWhileNode n) {
            visitWhile(n);
            return;
        }

        if (node instanceof SemanticBreakNode) {

            return;
        }

        if (node instanceof SemanticContinueNode) {

            return;
        }

        if (node instanceof SemanticReturnNode n) {
            visitReturnOutsideFunctionChecker(n);
            return;
        }

        if (node instanceof SemanticExprStmtNode n) {
            visitExprStmt(n);
            return;
        }

        diagnostics.error("Unknown semantic statement node: "
                + node.getClass().getSimpleName());
    }

    private void visitBlock(SemanticBlockNode node) {
        for (SemanticStmtNode stmt : node.statements) {
            checkStmt(stmt);
        }
    }

    private void visitExprStmt(SemanticExprStmtNode node) {
        expressions.checkExpr(node.expr);
    }

    private void visitAssign(SemanticAssignNode node) {
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

        if (!ensureValueExpression(
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

    private void checkCompoundAssign(SemanticAssignNode node) {
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

    private void visitIf(SemanticIfNode node) {
        expressions.checkExpr(node.condition);

        if (ensureValueExpression(
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

    private void visitWhile(SemanticWhileNode node) {
        expressions.checkExpr(node.condition);

        if (ensureValueExpression(
                node.condition,
                "while condition"
        )) {
            types.ensureIntegral(
                    node.condition.type,
                    "while condition must be an integer"
            );
        }

        checkStmt(node.body);

        for (SemanticStmtNode updateStmt : node.update) {
            checkStmt(updateStmt);
        }
    }

    private void visitReturnOutsideFunctionChecker(SemanticReturnNode node) {
        if (node.value != null) {
            expressions.checkExpr(node.value);
        }

        diagnostics.error("Return statement was checked outside FunctionChecker");
    }

    private boolean ensureValueExpression(
            SemanticExprNode expr,
            String where
    ) {
        if (expr == null) {
            diagnostics.error(where + " cannot be null");
            return false;
        }

        if (types.isVoidType(expr.type)) {
            diagnostics.error(where + " cannot be void");
            return false;
        }

        return true;
    }

    private boolean isStorageBackedLValue(SemanticExprNode expr) {
        if (expr == null || !expr.isLValue()) {
            return false;
        }

        if (expr instanceof SemanticVarExprNode) {
            return true;
        }

        if (expr instanceof SemanticDerefNode) {
            return true;
        }

        if (expr instanceof SemanticArrayAccessNode arrayAccess) {
            return isStorageBackedLValue(arrayAccess.target);
        }

        if (expr instanceof SemanticMemberAccessNode memberAccess) {
            return isStorageBackedLValue(memberAccess.target);
        }

        return false;
    }
}
