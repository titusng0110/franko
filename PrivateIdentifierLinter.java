import java.util.Objects;

public final class PrivateIdentifierLinter {
    private final DiagnosticBag diagnostics;

    private StructSymbol currentStruct;
    private Symbol currentFunction;
    private String currentNamespace;

    public PrivateIdentifierLinter(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public void check(SastNode node) {
        diagnostics.clear();
        currentStruct = null;
        currentFunction = null;
        currentNamespace = null;
        checkTopLevel(node);
    }

    public PassResult check(SastDocument document) {
        check(document.root);
        return new PassResult(
                document.withPhase(SastPhase.PRIVATE_ID),
                diagnostics.diagnostics()
        );
    }

    private void checkTopLevel(SastNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkTopLevelInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkTopLevelInner(SastNode node) {
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

        if (node.kind == SastKind.FUNCTION_DECL) {
            checkFunction(node, null);
            return;
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            if (node.structSymbol.isTuple) {
                return;
            }

            checkStruct(node);
            return;
        }

        if (node.kind == SastKind.VAR_DECL) {
            String previousNamespace = currentNamespace;
            currentNamespace = node.symbol.namespace;
            checkType(node.symbol.type);
            currentNamespace = previousNamespace;
        }
    }

    private void checkStruct(SastNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkStructInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkStructInner(SastNode node) {
        StructSymbol previousStruct = currentStruct;
        String previousNamespace = currentNamespace;

        currentStruct = node.structSymbol;
        currentNamespace = node.structSymbol.namespace;

        for (FieldSymbol field : node.structSymbol.fields) {
            checkType(field.type);
        }

        for (SastNode method : node.methods) {
            checkFunction(method, node.structSymbol);
        }

        currentStruct = previousStruct;
        currentNamespace = previousNamespace;
    }

    private void checkFunction(
            SastNode node,
            StructSymbol owner
    ) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkFunctionInner(node, owner);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkFunctionInner(
            SastNode node,
            StructSymbol owner
    ) {
        StructSymbol previousStruct = currentStruct;
        Symbol previousFunction = currentFunction;
        String previousNamespace = currentNamespace;

        currentStruct = owner;
        currentFunction = node.symbol;
        currentNamespace = node.symbol.namespace;

        for (Symbol parameter : node.parameterVariables) {
            checkType(parameter.type);
        }

        checkType(node.symbol.returnType());
        checkStmt(node.body);

        currentStruct = previousStruct;
        currentFunction = previousFunction;
        currentNamespace = previousNamespace;
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

        if (node.kind == SastKind.EXPR_STMT) {
            checkExpr(node.expr);
            return;
        }

        if (node.kind == SastKind.VAR_DECL) {
            checkType(node.symbol.type);
            return;
        }

        if (node.kind == SastKind.ASSIGN) {
            checkExpr(node.target);
            checkExpr(node.value);
            return;
        }

        if (node.kind == SastKind.IF) {
            checkExpr(node.condition);
            checkStmt(node.thenBranch);
            checkStmt(node.elseBranch);
            return;
        }

        if (node.kind == SastKind.WHILE) {
            checkExpr(node.condition);
            checkStmt(node.body);

            for (SastNode update : node.update) {
                checkStmt(update);
            }

            return;
        }

        if (node.kind == SastKind.RETURN) {
            checkExpr(node.value);
        }
    }

    private void checkExpr(SastNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkExprInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkExprInner(SastNode node) {
        if (node == null) {
            return;
        }

        if (node.kind == SastKind.UNARY_OP) {
            checkExpr(node.expr);
            return;
        }

        if (node.kind == SastKind.BIN_OP) {
            checkExpr(node.left);
            checkExpr(node.right);
            return;
        }

        if (node.kind == SastKind.ARRAY_ACCESS) {
            checkExpr(node.target);
            checkExpr(node.index);
            return;
        }

        if (node.kind == SastKind.MEMBER_ACCESS) {
            checkFieldAccess(node);
            checkExpr(node.target);
            return;
        }

        if (node.kind == SastKind.FUNCTION_CALL) {
            checkCall(node);

            for (SastNode arg : node.args) {
                checkExpr(arg);
            }

            return;
        }

        if (node.kind == SastKind.ARRAY_INTRINSIC_CALL) {
            checkExpr(node.receiver);

            for (SastNode arg : node.args) {
                checkExpr(arg);
            }

            return;
        }

        if (node.kind == SastKind.GET_PTR) {
            checkExpr(node.target);
            return;
        }

        if (node.kind == SastKind.DEREF) {
            checkExpr(node.expr);
            return;
        }

        if (node.kind == SastKind.CAST) {
            checkType(node.type);
            checkExpr(node.expr);
            return;
        }

        if (node.kind == SastKind.ENUM_VARIANT) {
            checkEnumAccess(node.enumSymbol);
            return;
        }

        if (node.kind == SastKind.AGGREGATE_LITERAL) {
            for (SastNode init : node.fields) {
                if (node.type.kind == SastTypeKind.STRUCT) {
                    checkFieldAccess(
                            node.type.structSymbol,
                            init.field,
                            init.value.span
                    );
                }

                checkExpr(init.value);
            }
        }
    }

    private void checkFieldAccess(SastNode node) {
        if (!(node.target.type.kind == SastTypeKind.STRUCT)) {
            return;
        }

        checkFieldAccess(
                node.target.type.structSymbol,
                node.field,
                node.span
        );
    }

    private void checkFieldAccess(
            StructSymbol owner,
            FieldSymbol field,
            SourceSpan span
    ) {
        if (field.tupleIndex >= 0) {
            return;
        }

        if (!field.name.startsWith("_")) {
            return;
        }

        if (owner == currentStruct) {
            return;
        }

        diagnostics.push(span);
        try {
            diagnostics.warning("'"
                    + owner.qualifiedName()
                    + "."
                    + field.name
                    + "' is a private field of struct '"
                    + owner.qualifiedName()
                    + "'. Are you sure you want to access it from "
                    + accessContextDescription()
                    + "? Fields and methods starting with '_' are private to the struct they are in.");
        } finally {
            diagnostics.pop();
        }
    }

    private void checkCall(SastNode node) {
        Symbol function = node.function;

        if (function.kind == SymbolKind.METHOD) {
            if (function.name.startsWith("_") && function.owner != currentStruct) {
                diagnostics.warning("'"
                        + function.owner.qualifiedName()
                        + "."
                        + function.name
                        + "' is a private method of struct '"
                        + function.owner.qualifiedName()
                        + "'. Are you sure you want to call it from "
                        + accessContextDescription()
                        + "? Fields and methods starting with '_' are private to the struct they are in.");
            }

            return;
        }

        if (!function.name.startsWith("__")) {
            return;
        }

        if (isInternalToCurrentNamespace(function.namespace)) {
            return;
        }

        diagnostics.warning("'"
                + function.qualifiedName()
                + "' is an internal function of "
                + namespaceDescription(function.namespace)
                + ". Are you sure you want to call it from "
                + accessContextDescription()
                + "? Functions starting with '__' are internal to the namespace they are in.");
    }

    private void checkType(SastType type) {
        if (type == null) {
            return;
        }

        if (type.kind == SastTypeKind.STRUCT) {
            checkStructAccess(type.structSymbol);
            return;
        }

        if (type.kind == SastTypeKind.ENUM) {
            checkEnumAccess(type.enumSymbol);
            return;
        }

        if (type.kind == SastTypeKind.TUPLE) {
            for (FieldSymbol field : type.fields) {
                checkType(field.type);
            }

            return;
        }

        if (type.kind == SastTypeKind.DYNAMIC_ARRAY) {
            checkType(type.elementType);
            return;
        }

        if (type.kind == SastTypeKind.STATIC_ARRAY) {
            checkType(type.elementType);
            return;
        }

        if (type.kind == SastTypeKind.PTR) {
            checkType(type.referencedType);
        }
    }

    private void checkStructAccess(StructSymbol structSymbol) {
        if (!structSymbol.name.startsWith("__")) {
            return;
        }

        if (isInternalToCurrentNamespace(structSymbol.namespace)) {
            return;
        }

        diagnostics.warning("'"
                + structSymbol.qualifiedName()
                + "' is an internal struct of "
                + namespaceDescription(structSymbol.namespace)
                + ". Are you sure you want to reference it from "
                + accessContextDescription()
                + "? Structs starting with '__' are internal to the namespace they are in.");
    }

    private void checkEnumAccess(EnumSymbol enumSymbol) {
        if (!enumSymbol.name.startsWith("__")) {
            return;
        }

        if (isInternalToCurrentNamespace(enumSymbol.namespace)) {
            return;
        }

        diagnostics.warning("'"
                + enumSymbol.qualifiedName()
                + "' is an internal enum of "
                + namespaceDescription(enumSymbol.namespace)
                + ". Are you sure you want to reference it from "
                + accessContextDescription()
                + "? Enums starting with '__' are internal to the namespace they are in.");
    }

    private boolean isInternalToCurrentNamespace(String declaringNamespace) {
        if (declaringNamespace == null) {
            return currentNamespace == null;
        }

        if (currentNamespace == null) {
            return false;
        }

        return currentNamespace.equals(declaringNamespace)
                || currentNamespace.startsWith(declaringNamespace + "::");
    }

    private String accessContextDescription() {
        if (currentFunction.kind == SymbolKind.METHOD) {
            return "method '" + currentFunction.qualifiedName() + "'";
        }

        if (currentFunction != null) {
            return "function '" + currentFunction.qualifiedName() + "'";
        }

        if (currentStruct != null) {
            return "struct '" + currentStruct.qualifiedName() + "'";
        }

        if (currentNamespace != null) {
            return "namespace '" + currentNamespace + "'";
        }

        return "global scope";
    }

    private static String namespaceDescription(String namespace) {
        return namespace == null
                ? "the global namespace"
                : "namespace '" + namespace + "'";
    }
}
