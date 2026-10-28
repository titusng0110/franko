import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class FunctionChecker {
    private final DiagnosticBag diagnostics;
    private final DeclarationChecker declarations;
    private final StatementChecker statements;
    private final ExpressionChecker expressions;
    private final TypeChecker types;

    public FunctionChecker(
            DiagnosticBag diagnostics,
            DeclarationChecker declarations,
            StatementChecker statements,
            ExpressionChecker expressions,
            TypeChecker types
    ) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
        this.declarations = Objects.requireNonNull(declarations);
        this.statements = Objects.requireNonNull(statements);
        this.expressions = Objects.requireNonNull(expressions);
        this.types = Objects.requireNonNull(types);
    }

    public void checkFunction(SemanticFunctionDeclNode node) {
        if (node == null) {
            diagnostics.error("Function declaration node cannot be null");
            return;
        }

        if (node.symbol == null) {
            diagnostics.error("Function declaration has null symbol");
            return;
        }

        validateReturnType(node.symbol);
        validateParameters(node);

        if (node.symbol instanceof MethodSymbol method) {
            validateMethodSelf(method);
        }

        boolean sawReturn = false;

        if (node.body == null) {
            diagnostics.error("Function '" + node.symbol.qualifiedName() + "' has null body");
        } else {
            sawReturn = checkFunctionBody(node);
        }

        if (!types.isVoidType(node.symbol.returnType()) && !sawReturn) {
            diagnostics.error("Non-void function '"
                    + node.symbol.fullSignatureString()
                    + "' must contain a return statement with an expression");
        }
    }

    private void validateParameters(SemanticFunctionDeclNode node) {
        Set<String> names = new HashSet<>();

        for (VariableSymbol param : node.parameterVariables) {
            if (param == null) {
                diagnostics.error("Function '"
                        + node.symbol.qualifiedName()
                        + "' has null parameter symbol");
                continue;
            }

            if (!names.add(param.name)) {
                diagnostics.error("Duplicate parameter name '"
                        + param.name
                        + "' in function '"
                        + node.symbol.qualifiedName()
                        + "'");
            }

            declarations.checkVarDecl(new SemanticVarDeclNode(param));

            if (!types.isAssignableValueType(param.type)) {
                diagnostics.error("Parameter '"
                        + param.name
                        + "' in function '"
                        + node.symbol.qualifiedName()
                        + "' has invalid parameter type "
                        + types.describeSafe(param.type)
                        + ": parameters must have assignable value types");
            }
        }
    }

    private void validateMethodSelf(MethodSymbol method) {
        String display = method.qualifiedName();
        String ownerName = method.owner.qualifiedName();

        if (method.parameters.isEmpty()) {
            diagnostics.error("Method '"
                    + display
                    + "' must have 'ptr<"
                    + ownerName
                    + "> self' as its first parameter"
                    + " (also allowed: 'mview<"
                    + ownerName
                    + "> self', 'view<"
                    + ownerName
                    + "> self')");
            return;
        }

        ParameterSymbol first = method.parameters.get(0);

        if (!"self".equals(first.name)) {
            diagnostics.error("Method '"
                    + display
                    + "' must have 'ptr<"
                    + ownerName
                    + "> self' as its first parameter"
                    + " (also allowed: 'mview<"
                    + ownerName
                    + "> self', 'view<"
                    + ownerName
                    + "> self')");
        } else if (!isSelfTypeForOwner(first.type, method.owner)) {
            diagnostics.error("Method '"
                    + display
                    + "' has a 'self' parameter of type "
                    + types.describeSafe(first.type)
                    + "; it must be ptr<"
                    + ownerName
                    + "> (or mview<"
                    + ownerName
                    + "> or view<"
                    + ownerName
                    + ">)");
        }

        for (int i = 1; i < method.parameters.size(); i++) {
            if ("self".equals(method.parameters.get(i).name)) {
                diagnostics.error("Only the first parameter of a method may be named 'self'");
                break;
            }
        }
    }

    private boolean isSelfTypeForOwner(SemanticType type, StructSymbol owner) {
        if (!(type instanceof SemanticPtrType ptr)) {
            return false;
        }

        if (!(ptr.referencedType instanceof SemanticStructType structType)) {
            return false;
        }

        if (structType.structSymbol == null) {
            return false;
        }

        return structType.structSymbol.qualifiedName().equals(owner.qualifiedName());
    }
    private void validateReturnType(FunctionSymbol fn) {
        SemanticType returnType = fn.returnType();

        if (returnType == null) {
            diagnostics.error("Function '"
                    + fn.fullSignatureString()
                    + "' has null return type");
            return;
        }

        if (!types.isVoidType(returnType)) {
            declarations.checkDeclaredType(
                    returnType,
                    "Return type of function '" + fn.fullSignatureString() + "'"
            );
        }

        if (!types.isValidFunctionReturnType(returnType)) {
            diagnostics.error("Function '"
                    + fn.fullSignatureString()
                    + "' has invalid return type "
                    + types.describeSafe(returnType)
                    + ": return type must be void or an assignable value type");
        }
    }

    private boolean checkFunctionBody(SemanticFunctionDeclNode fn) {
        boolean sawReturn = false;

        for (SemanticStmtNode stmt : fn.body.statements) {
            if (checkStmtInsideFunction(stmt, fn.symbol)) {
                sawReturn = true;
            }
        }

        return sawReturn;
    }

    private boolean checkStmtInsideFunction(
            SemanticStmtNode stmt,
            FunctionSymbol function
    ) {
        if (stmt == null) {
            return false;
        }

        if (stmt instanceof SemanticReturnNode n) {
            visitReturn(n, function);
            return true;
        }

        if (stmt instanceof SemanticBlockNode n) {
            return visitBlock(n, function);
        }

        if (stmt instanceof SemanticIfNode n) {
            return visitIf(n, function);
        }

        if (stmt instanceof SemanticWhileNode n) {
            return visitWhile(n, function);
        }

        statements.checkStmt(stmt);
        return false;
    }

    private boolean visitBlock(
            SemanticBlockNode node,
            FunctionSymbol function
    ) {
        boolean sawReturn = false;

        for (SemanticStmtNode stmt : node.statements) {
            if (checkStmtInsideFunction(stmt, function)) {
                sawReturn = true;
            }
        }

        return sawReturn;
    }

    private boolean visitIf(
            SemanticIfNode node,
            FunctionSymbol function
    ) {
        expressions.checkExpr(node.condition);

        if (types.isVoidType(node.condition.type)) {
            diagnostics.error("if condition cannot be void");
            return false;
        }

        types.ensureIntegral(
                node.condition.type,
                "if condition must be an integer"
        );

        boolean thenReturns = checkStmtInsideFunction(
                node.thenBranch,
                function
        );

        boolean elseReturns = false;

        if (node.elseBranch != null) {
            elseReturns = checkStmtInsideFunction(
                    node.elseBranch,
                    function
            );
        }

        return thenReturns || elseReturns;
    }

    private boolean visitWhile(
            SemanticWhileNode node,
            FunctionSymbol function
    ) {
        expressions.checkExpr(node.condition);

        if (types.isVoidType(node.condition.type)) {
            diagnostics.error("while condition cannot be void");
            return false;
        }

        types.ensureIntegral(
                node.condition.type,
                "while condition must be an integer"
        );

        boolean sawReturn = checkStmtInsideFunction(node.body, function);

        for (SemanticStmtNode updateStmt : node.update) {
            if (checkStmtInsideFunction(updateStmt, function)) {
                sawReturn = true;
            }
        }

        return sawReturn;
    }

    private void visitReturn(
            SemanticReturnNode node,
            FunctionSymbol enclosingFunction
    ) {
        if (node.function == null) {
            diagnostics.error("Return statement has null owning function");
            return;
        }

        if (node.function != enclosingFunction) {
            diagnostics.error("Return statement is attached to function '"
                    + node.function.fullSignatureString()
                    + "' but appears while checking function '"
                    + enclosingFunction.fullSignatureString()
                    + "'");
        }

        SemanticType declaredReturnType = enclosingFunction.returnType();

        if (types.isVoidType(declaredReturnType)) {
            visitVoidFunctionReturn(node, enclosingFunction);
            return;
        }

        visitNonVoidFunctionReturn(node, enclosingFunction);
    }

    private void visitVoidFunctionReturn(
            SemanticReturnNode node,
            FunctionSymbol function
    ) {
        if (node.value == null) {

            return;
        }

        expressions.checkExpr(node.value);

        diagnostics.error("Void function '"
                + function.fullSignatureString()
                + "' must not return a value");
    }

    private void visitNonVoidFunctionReturn(
            SemanticReturnNode node,
            FunctionSymbol function
    ) {
        if (node.value == null) {
            diagnostics.error("Non-void function '"
                    + function.fullSignatureString()
                    + "' must return an expression");
            return;
        }

        expressions.checkExpr(node.value);

        if (types.isVoidType(node.value.type)) {
            diagnostics.error("Non-void function '"
                    + function.fullSignatureString()
                    + "' cannot return a void expression");
            return;
        }

        types.ensureAssignable(
                function.returnType(),
                node.value,
                "Invalid return from function '" + function.fullSignatureString() + "'"
        );
    }
}
