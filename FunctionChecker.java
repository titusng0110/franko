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

    public void checkFunction(SastNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkFunctionInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkFunctionInner(SastNode node) {
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

        if (node.symbol.kind == SymbolKind.METHOD) {
            if (OperatorMethods.isOperatorMethodName(node.symbol.name)) {
                validateOperatorMethod(node.symbol);
            } else {
                validateMethodSelf(node.symbol);
            }
        } else if (OperatorMethods.isOperatorMethodName(node.symbol.name)) {
            diagnostics.error("'"
                    + node.symbol.name
                    + "' is reserved for struct operator methods; only structs"
                    + " can overload operators");
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

    private void validateParameters(SastNode node) {
        Set<String> names = new HashSet<>();
        int start = node.symbol.kind == SymbolKind.METHOD ? 1 : 0;
        int firstOptional = -1;

        for (int i = 0; i < node.parameterVariables.size(); i++) {
            Symbol param = node.parameterVariables.get(i);

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

            declarations.checkVarDecl(SastNode.varDecl(node.span, param));

            if (!types.isValueAssignableType(param.type)) {
                diagnostics.error("Parameter '"
                        + param.name
                        + "' in function '"
                        + node.symbol.qualifiedName()
                        + "' has invalid parameter type "
                        + types.describeSafe(param.type)
                        + ": parameters must have assignable value types");
            }

            if (param.parameterDefault != null) {
                types.ensureAssignable(
                        param.type,
                        param.parameterDefault,
                        "Default value for parameter '"
                                + param.name
                                + "' of function '"
                                + node.symbol.qualifiedName()
                                + "'"
                );
            }

            if (i < start) {
                if (param.parameterDefault != null) {
                    diagnostics.error(
                            "Parameter 'self' cannot have a default value");
                }

                continue;
            }

            if (param.parameterDefault != null) {
                if (firstOptional < 0) {
                    firstOptional = i;
                }
            } else if (firstOptional >= 0) {
                diagnostics.error("Optional parameter '"
                        + node.parameterVariables.get(firstOptional).name
                        + "' in function '"
                        + node.symbol.qualifiedName()
                        + "' must follow all required parameters");
                firstOptional = -1;
            }
        }

        if (node.symbol.kind == SymbolKind.FUNCTION
                && "main".equals(node.symbol.name)
                && node.symbol.namespace == null) {
            for (Symbol param : node.parameterVariables) {
                if (param != null && param.parameterDefault != null) {
                    diagnostics.error("The entry-point function 'main'"
                            + " cannot declare optional parameters");
                    break;
                }
            }
        }
    }

    private void validateMethodSelf(Symbol method) {
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

        Symbol first = method.parameters.get(0);

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
        } else if (!TypeChecker.isSelfTypeForOwner(first.type, method.owner)) {
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

    private void validateOperatorMethod(Symbol method) {
        for (Symbol parameter : method.parameters) {
            if (parameter.parameterDefault != null) {
                diagnostics.error("Operator method '"
                        + method.qualifiedName()
                        + "' cannot declare optional parameters");
                break;
            }
        }

        if (OperatorMethods.isComparisonMethodName(method.name)) {
            validateComparisonOperatorMethod(method);
        } else {
            validateAssignmentOperatorMethod(method);
        }
    }

    private void validateComparisonOperatorMethod(Symbol method) {
        boolean valid = method.parameters.size() == 2
                && "self".equals(method.parameters.get(0).name)
                && isViewOfOwner(
                        method.parameters.get(0).type,
                        method.owner)
                && isViewOfOwner(
                        method.parameters.get(1).type,
                        method.owner)
                && !"self".equals(method.parameters.get(1).name)
                && TypeChecker.isByteType(method.returnType());

        if (valid) {
            return;
        }

        String ownerName = method.owner.qualifiedName();

        diagnostics.error("Comparison operator method '"
                + method.qualifiedName()
                + "' must have signature 'func "
                + method.name
                + "(view<"
                + ownerName
                + "> self, view<"
                + ownerName
                + "> other) -> uint8'");
    }

    private void validateAssignmentOperatorMethod(Symbol method) {
        if ("OPERATOR_MOV".equals(method.name)
                && types.isValueAssignableType(
                        SastType.structType(method.owner))) {
            diagnostics.error("Struct '"
                    + method.owner.qualifiedName()
                    + "' is value-assignable and cannot declare OPERATOR_MOV;"
                    + " built-in whole-value assignment is always used for"
                    + " value-assignable structs");
        }

        boolean valid = method.parameters.size() == 2
                && "self".equals(method.parameters.get(0).name)
                && isOwnerPtrOfOwner(
                        method.parameters.get(0).type,
                        method.owner)
                && method.parameters.get(1).type.kind == SastTypeKind.PTR
                && method.parameters.get(1).type.permission == PtrPermission.READ_ONLY
                && method.parameters.get(1).type.referencedType.kind != SastTypeKind.VOID
                && !"self".equals(method.parameters.get(1).name)
                && types.isVoidType(method.returnType());

        if (valid) {
            return;
        }

        String ownerName = method.owner.qualifiedName();

        diagnostics.error("Assignment operator method '"
                + method.qualifiedName()
                + "' must have signature 'func "
                + method.name
                + "(ptr<"
                + ownerName
                + "> self, view<T> other) -> void'");
    }

    private boolean isViewOfOwner(SastType type, StructSymbol owner) {
        return type.kind == SastTypeKind.PTR
                && type.permission == PtrPermission.READ_ONLY
                && type.referencedType.kind == SastTypeKind.STRUCT
                && type.referencedType.structSymbol != null
                && type.referencedType.structSymbol.qualifiedName().equals(
                        owner.qualifiedName());
    }

    private boolean isOwnerPtrOfOwner(SastType type, StructSymbol owner) {
        return type.kind == SastTypeKind.PTR
                && type.permission == PtrPermission.OWNER
                && type.referencedType.kind == SastTypeKind.STRUCT
                && type.referencedType.structSymbol != null
                && type.referencedType.structSymbol.qualifiedName().equals(
                        owner.qualifiedName());
    }

    private void validateReturnType(Symbol fn) {
        SastType returnType = fn.returnType();

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
            if (returnType.kind == SastTypeKind.STRUCT
                    && !types.isValueAssignableType(returnType)) {
                diagnostics.error("Function '"
                        + fn.fullSignatureString()
                        + "' cannot return pointer-only struct '"
                        + returnType.describe()
                        + "' by value: its fields are not all value-assignable. "
                        + "Return ptr<"
                        + returnType.describe()
                        + ">, or write through a ptr<"
                        + returnType.describe()
                        + "> output parameter.");
                return;
            }

            if (returnType.kind == SastTypeKind.TUPLE
                    && !types.isValueAssignableType(returnType)) {
                diagnostics.error("Function '"
                        + fn.fullSignatureString()
                        + "' cannot return pointer-only tuple '"
                        + returnType.describe()
                        + "' by value: its elements are not all value-assignable. "
                        + "Return ptr<"
                        + returnType.describe()
                        + ">, or write through a ptr<"
                        + returnType.describe()
                        + "> output parameter.");
                return;
            }

            diagnostics.error("Function '"
                    + fn.fullSignatureString()
                    + "' has invalid return type "
                    + types.describeSafe(returnType)
                    + ": return type must be void or an assignable value type");
        }
    }

    private boolean checkFunctionBody(SastNode fn) {
        boolean sawReturn = false;

        for (SastNode stmt : fn.body.statements) {
            if (checkStmtInsideFunction(stmt, fn.symbol)) {
                sawReturn = true;
            }
        }

        return sawReturn;
    }

    private boolean checkStmtInsideFunction(
            SastNode stmt,
            Symbol function
    ) {
        if (stmt == null) {
            return false;
        }

        if (stmt.kind == SastKind.RETURN) {
            visitReturn(stmt, function);
            return true;
        }

        if (stmt.kind == SastKind.BLOCK) {
            return visitBlock(stmt, function);
        }

        if (stmt.kind == SastKind.IF) {
            return visitIf(stmt, function);
        }

        if (stmt.kind == SastKind.WHILE) {
            return visitWhile(stmt, function);
        }

        statements.checkStmt(stmt);
        return false;
    }

    private boolean visitBlock(
            SastNode node,
            Symbol function
    ) {
        boolean sawReturn = false;

        for (SastNode stmt : node.statements) {
            if (checkStmtInsideFunction(stmt, function)) {
                sawReturn = true;
            }
        }

        return sawReturn;
    }

    private boolean visitIf(
            SastNode node,
            Symbol function
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
            SastNode node,
            Symbol function
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

        for (SastNode updateStmt : node.update) {
            if (checkStmtInsideFunction(updateStmt, function)) {
                sawReturn = true;
            }
        }

        return sawReturn;
    }

    private void visitReturn(
            SastNode node,
            Symbol enclosingFunction
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

        SastType declaredReturnType = enclosingFunction.returnType();

        if (types.isVoidType(declaredReturnType)) {
            visitVoidFunctionReturn(node, enclosingFunction);
            return;
        }

        visitNonVoidFunctionReturn(node, enclosingFunction);
    }

    private void visitVoidFunctionReturn(
            SastNode node,
            Symbol function
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
            SastNode node,
            Symbol function
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
