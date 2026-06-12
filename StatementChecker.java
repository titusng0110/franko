/**
 * ============================================================================
 * STATEMENT CHECKER
 * ============================================================================
 *
 * PURPOSE:
 * StatementChecker validates statement-level Franko legality rules over the
 * fully lowered, symbol-resolved, type-decorated Semantic AST.
 *
 * The SemanticAnalyzer has already:
 *
 *   - resolved variable references to VariableSymbol objects,
 *   - resolved function calls to FunctionSymbol overloads,
 *   - assigned every expression a SemanticType,
 *   - folded integer constants into BigInteger values when possible,
 *   - lowered array intrinsic syntax into dedicated Semantic AST nodes,
 *   - performed structural lvalue checks needed for correct lowering.
 *
 * StatementChecker performs stricter statement-level legality checks:
 *
 *   - assignment target validity,
 *   - assignment type compatibility,
 *   - void-expression rejection in value-producing statement contexts,
 *   - if/while condition validity,
 *   - print argument validity,
 *   - delete legality,
 *   - recursive traversal through blocks and nested statements.
 *
 * ----------------------------------------------------------------------------
 * FUNCTION / RETURN NOTE
 * ----------------------------------------------------------------------------
 *
 * Function-level return rules are handled by FunctionChecker, not here.
 *
 * FunctionChecker should intercept SemanticReturnNode before delegating normal
 * statements to StatementChecker.
 *
 * If StatementChecker sees a SemanticReturnNode directly, it reports a defensive
 * error instead of treating it as an unknown node.
 *
 * ----------------------------------------------------------------------------
 * VOID VALUE-CONTEXT RULE
 * ----------------------------------------------------------------------------
 *
 * A void-returning function call is allowed as an expression statement:
 *
 *     logDone();
 *
 * But a void expression is invalid in value-producing contexts:
 *
 *     x = logDone();       invalid
 *     if (logDone()) {}    invalid
 *     print(logDone());    invalid
 *     arr(logDone());      invalid
 *
 * StatementChecker enforces this for statement-level value contexts.
 *
 * ============================================================================
 */
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

        if (node instanceof SemanticDelNode n) {
            visitDel(n);
            return;
        }

        if (node instanceof SemanticPrintNode n) {
            visitPrint(n);
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

    // ============================================================
    // General Statements
    // ============================================================

    private void visitBlock(SemanticBlockNode node) {
        for (SemanticStmtNode stmt : node.statements) {
            checkStmt(stmt);
        }
    }

    /**
     * Expression statements are allowed even if the expression has void type.
     *
     * This is what permits:
     *
     *     logDone();
     *
     * where logDone returns void.
     *
     * Array intrinsics are also checked here because they are now expression
     * nodes:
     *
     *     xs.init(10);
     *     xs.memset(0);
     *     dst.memcpy(getaddr(src));
     */
    private void visitExprStmt(SemanticExprStmtNode node) {
        expressions.checkExpr(node.expr);
    }

    private void visitAssign(SemanticAssignNode node) {
        expressions.checkExpr(node.target);
        expressions.checkExpr(node.value);

        if (!isStorageBackedLValue(node.target)) {
            diagnostics.error("Left-hand side of assignment must be an addressable storage-backed lvalue");
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

        types.ensureAssignable(
                node.target.type,
                node.value,
                "Invalid assignment"
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
    }

    private void visitPrint(SemanticPrintNode node) {
        for (SemanticExprNode arg : node.args) {
            expressions.checkExpr(arg);

            ensureValueExpression(
                    arg,
                    "print argument"
            );
        }
    }

    /**
     * Return statements should be handled by FunctionChecker because return
     * legality depends on the enclosing function's declared return type.
     *
     * This defensive branch prevents returns from being reported as unknown
     * statements if they accidentally reach StatementChecker.
     */
    private void visitReturnOutsideFunctionChecker(SemanticReturnNode node) {
        if (node.value != null) {
            expressions.checkExpr(node.value);
        }

        diagnostics.error("Return statement was checked outside FunctionChecker");
    }

    // ============================================================
    // Delete
    // ============================================================

    private void visitDel(SemanticDelNode node) {
        VariableSymbol sym = node.symbol;

        if (sym == null) {
            diagnostics.error("Cannot delete null symbol");
            return;
        }
    }

    // ============================================================
    // Value Context Helpers
    // ============================================================

    /**
     * Ensures an expression is usable as a value.
     *
     * This is where StatementChecker enforces:
     *
     *   - void-returning calls are allowed as statements,
     *   - void-returning calls are not allowed where a value is required.
     */
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

    // ============================================================
    // LValue / Storage Helpers
    // ============================================================

    /**
     * Stronger than SemanticExprNode.isLValue().
     *
     * Storage-backed lvalues are currently:
     *
     *   - variables,
     *   - dereferenced addresses,
     *   - array elements whose target is storage-backed.
     *
     * This intentionally rejects synthetic/non-storage lvalues unless their
     * storage semantics are explicitly added here later.
     */
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

        return false;
    }
}