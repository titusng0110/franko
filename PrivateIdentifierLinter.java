import java.util.Objects;

public final class PrivateIdentifierLinter {
    private final DiagnosticBag diagnostics;

    private StructSymbol currentStruct;
    private Symbol currentFunction;
    private String currentNamespace;

    public PrivateIdentifierLinter(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public void check(SemanticASTNode node) {
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

    private void checkTopLevel(SemanticASTNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkTopLevelInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkTopLevelInner(SemanticASTNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticProgramNode n) {
            for (SemanticASTNode item : n.topLevelItems) {
                checkTopLevel(item);
            }

            return;
        }

        if (node instanceof SemanticNamespaceNode n) {
            for (SemanticASTNode item : n.items) {
                checkTopLevel(item);
            }

            return;
        }

        if (node instanceof SemanticFunctionDeclNode n) {
            checkFunction(n, null);
            return;
        }

        if (node instanceof SemanticStructDeclNode n) {
            if (n.symbol.isTuple) {
                return;
            }

            checkStruct(n);
            return;
        }

        if (node instanceof SemanticVarDeclNode n) {
            String previousNamespace = currentNamespace;
            currentNamespace = n.symbol.namespace;
            checkType(n.symbol.type);
            currentNamespace = previousNamespace;
        }
    }

    private void checkStruct(SemanticStructDeclNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkStructInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkStructInner(SemanticStructDeclNode node) {
        StructSymbol previousStruct = currentStruct;
        String previousNamespace = currentNamespace;

        currentStruct = node.symbol;
        currentNamespace = node.symbol.namespace;

        for (FieldSymbol field : node.symbol.fields) {
            checkType(field.type);
        }

        for (SemanticFunctionDeclNode method : node.methods) {
            checkFunction(method, node.symbol);
        }

        currentStruct = previousStruct;
        currentNamespace = previousNamespace;
    }

    private void checkFunction(
            SemanticFunctionDeclNode node,
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
            SemanticFunctionDeclNode node,
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

    private void checkStmt(SemanticStmtNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkStmtInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkStmtInner(SemanticStmtNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticBlockNode n) {
            for (SemanticStmtNode stmt : n.statements) {
                checkStmt(stmt);
            }

            return;
        }

        if (node instanceof SemanticExprStmtNode n) {
            checkExpr(n.expr);
            return;
        }

        if (node instanceof SemanticVarDeclNode n) {
            checkType(n.symbol.type);
            return;
        }

        if (node instanceof SemanticAssignNode n) {
            checkExpr(n.target);
            checkExpr(n.value);
            return;
        }

        if (node instanceof SemanticIfNode n) {
            checkExpr(n.condition);
            checkStmt(n.thenBranch);
            checkStmt(n.elseBranch);
            return;
        }

        if (node instanceof SemanticWhileNode n) {
            checkExpr(n.condition);
            checkStmt(n.body);

            for (SemanticStmtNode update : n.update) {
                checkStmt(update);
            }

            return;
        }

        if (node instanceof SemanticReturnNode n) {
            checkExpr(n.value);
        }
    }

    private void checkExpr(SemanticExprNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkExprInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkExprInner(SemanticExprNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticUnaryOpNode n) {
            checkExpr(n.expr);
            return;
        }

        if (node instanceof SemanticBinOpNode n) {
            checkExpr(n.left);
            checkExpr(n.right);
            return;
        }

        if (node instanceof SemanticArrayAccessNode n) {
            checkExpr(n.target);
            checkExpr(n.index);
            return;
        }

        if (node instanceof SemanticMemberAccessNode n) {
            checkFieldAccess(n);
            checkExpr(n.target);
            return;
        }

        if (node instanceof SemanticFunctionCallNode n) {
            checkCall(n);

            for (SemanticExprNode arg : n.args) {
                checkExpr(arg);
            }

            return;
        }

        if (node instanceof SemanticArrayIntrinsicCallNode n) {
            checkExpr(n.receiver);

            for (SemanticExprNode arg : n.args) {
                checkExpr(arg);
            }

            return;
        }

        if (node instanceof SemanticGetPtrNode n) {
            checkExpr(n.target);
            return;
        }

        if (node instanceof SemanticDerefNode n) {
            checkExpr(n.expr);
            return;
        }

        if (node instanceof SemanticCastExprNode n) {
            checkType(n.type);
            checkExpr(n.expr);
            return;
        }

        if (node instanceof SemanticEnumVariantNode n) {
            checkEnumAccess(n.enumSymbol);
            return;
        }

        if (node instanceof SemanticAggregateLiteralNode n) {
            for (SemanticAggregateFieldInit init : n.fields) {
                if (n.type.kind == SastTypeKind.STRUCT) {
                    checkFieldAccess(
                            n.type.structSymbol,
                            init.field,
                            init.value.span
                    );
                }

                checkExpr(init.value);
            }
        }
    }

    private void checkFieldAccess(SemanticMemberAccessNode node) {
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

    private void checkCall(SemanticFunctionCallNode node) {
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
