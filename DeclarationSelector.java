import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DeclarationSelector {

    static final class Selection {
        final ProgramNode program;
        final List<Diagnostic> diagnostics;

        Selection(ProgramNode program, List<Diagnostic> diagnostics) {
            this.program = program;
            this.diagnostics = diagnostics;
        }
    }

    private static final class Scope {
        final NamespaceSymbol symbol;
        final String path;

        Scope(NamespaceSymbol symbol, String path) {
            this.symbol = symbol;
            this.path = path;
        }
    }

    private final TargetInfo target;
    private final DiagnosticBag diagnostics = new DiagnosticBag();
    private final SymbolTable ctx = new SymbolTable(diagnostics);
    private final ConstExpressionEvaluator constEval;
    private final ExpressionAnalyzer analyzer;
    private final SemanticTypeResolver typeResolver;

    private final Map<String, NamespaceSymbol> namespaces =
            new LinkedHashMap<>();
    private final Map<StructDeclNode, StructSymbol> structSymbols =
            new IdentityHashMap<>();
    private final Map<StructDeclNode, Scope> structScopes =
            new IdentityHashMap<>();
    private final Set<ASTNode> registeredMembers =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<GlobalVarDeclNode> registeredGlobals =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<StructDeclNode> processedStructs =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<StructDeclNode, StructDeclNode> selectedStructs =
            new IdentityHashMap<>();

    DeclarationSelector(TargetInfo target) {
        this.target = target;
        this.constEval = new ConstExpressionEvaluator(ctx);

        ExpressionLowering lowering = new ExpressionLowering(
                ctx,
                constEval,
                (arrayType, values, span) -> {
                    GlobalVariableSymbol symbol = new GlobalVariableSymbol(
                            null,
                            "_CT_ARRAY",
                            arrayType
                    );
                    symbol.literalValues = List.copyOf(values);
                    return new SemanticVarExprNode(span, arrayType, symbol);
                },
                (bytes, span) -> {
                    SemanticStaticArrayType arrayType =
                            new SemanticStaticArrayType(
                                    new SemanticPrimitiveType(
                                            SemanticPrimitiveKind.UINT8),
                                    Integer.toString(bytes.size())
                            );
                    GlobalVariableSymbol symbol = new GlobalVariableSymbol(
                            null,
                            "_CT_STR",
                            arrayType
                    );
                    symbol.literalBytes = List.copyOf(bytes);
                    return symbol;
                },
                target
        );

        this.analyzer = lowering.analyzer;
        this.typeResolver = lowering.typeResolver;
    }

    Selection select(ProgramNode program) {
        ctx.pushScope();

        Scope global = new Scope(null, null);
        preRegisterShells(program.topLevelItems, global);
        preRegisterBodies(program.topLevelItems, global);

        for (StructDeclNode struct : new ArrayList<>(structSymbols.keySet())) {
            processStruct(struct);
        }

        List<ASTNode> selected = selectItems(program.topLevelItems, global);

        ctx.popScope();

        return new Selection(
                new ProgramNode(program.span, selected),
                diagnostics.diagnostics()
        );
    }

    private void preRegisterShells(List<ASTNode> items, Scope scope) {
        for (ASTNode item : items) {
            if (item instanceof NamespaceNode n) {
                Scope child = namespaceFor(n, scope);
                preRegisterShells(n.items, child);
            } else if (item instanceof StructDeclNode s) {
                registerStructShell(s, scope);
            }
        }
    }

    private void preRegisterBodies(List<ASTNode> items, Scope scope) {
        for (ASTNode item : items) {
            if (item instanceof NamespaceNode n) {
                preRegisterBodies(n.items, namespaceFor(n, scope));
            } else if (item instanceof StructDeclNode s) {
                StructSymbol symbol = registerStructShell(s, scope);
                registerUnconditionalMembers(s, symbol);
            } else if (item instanceof GlobalVarDeclNode g) {
                registerGlobal(g, scope);
            }
        }
    }

    private Scope namespaceFor(NamespaceNode node, Scope parent) {
        String fullPath;

        if (parent == null || parent.path == null) {
            fullPath = node.name;
        } else {
            fullPath = parent.path + "::" + node.name;
        }

        NamespaceSymbol symbol = namespaces.get(fullPath);

        if (symbol == null) {
            symbol = ctx.declareNamespace(fullPath);
            namespaces.put(fullPath, symbol);
        }

        return new Scope(symbol, fullPath);
    }

    private StructSymbol registerStructShell(
            StructDeclNode node,
            Scope scope
    ) {
        ctx.setCurrentNamespace(scope.symbol);

        StructSymbol existing = structSymbols.get(node);

        if (existing != null) {
            return existing;
        }

        String namespaceName = scope.symbol == null
                ? null
                : scope.symbol.name;
        StructSymbol symbol = new StructSymbol(namespaceName, node.name);

        if (!ctx.declareStruct(symbol)) {
            symbol = ctx.resolveStructIn(scope.symbol, node.name);
        }

        structSymbols.put(node, symbol);
        structScopes.put(node, scope);
        return symbol;
    }

    private void registerUnconditionalMembers(
            StructDeclNode node,
            StructSymbol symbol
    ) {
        for (ASTNode member : node.memberItems) {
            if (member instanceof DeclIfNode) {
                continue;
            }

            registerMember(member, symbol);
        }
    }

    private void registerMember(ASTNode member, StructSymbol symbol) {
        if (!registeredMembers.add(member)) {
            return;
        }

        if (member instanceof StructFieldDeclNode field) {
            if (symbol.hasFieldName(field.name)) {
                return;
            }

            SemanticType fieldType = typeResolver.analyzeNormalType(field.type);
            BigInteger defaultValue = null;

            if (field.constInitializer != null) {
                defaultValue = analyzer.preEvaluatedConstValue(
                        field.constInitializer,
                        "struct field initializer"
                );
            }

            symbol.fields.add(new FieldSymbol(
                    field.name,
                    fieldType,
                    defaultValue
            ));
            return;
        }

        if (member instanceof FunctionDeclNode methodNode) {
            List<ParameterSymbol> params = new ArrayList<>();

            for (int i = 0; i < methodNode.parameters.size(); i++) {
                ParameterNode param = methodNode.parameters.get(i);
                params.add(new ParameterSymbol(
                        param.name,
                        typeResolver.analyzeNormalType(param.type),
                        i
                ));
            }

            MethodSymbol method = new MethodSymbol(
                    symbol,
                    methodNode.name,
                    params,
                    typeResolver.analyzeReturnType(methodNode.returnType),
                    false
            );

            ctx.declareMethod(symbol, methodNode, method);
        }
    }

    private void registerGlobal(GlobalVarDeclNode node, Scope scope) {
        if (!registeredGlobals.add(node)) {
            return;
        }

        ctx.setCurrentNamespace(scope.symbol);

        SemanticType type = typeResolver.analyzeNormalType(node.type);
        String namespaceName = scope.symbol == null
                ? null
                : scope.symbol.name;

        GlobalVariableSymbol symbol = new GlobalVariableSymbol(
                namespaceName,
                node.name,
                type
        );

        ctx.declareGlobalVariable(symbol);
    }

    private void processStruct(StructDeclNode node) {
        if (!processedStructs.add(node)) {
            return;
        }

        StructSymbol symbol = structSymbols.get(node);
        Scope scope = structScopes.get(node);

        if (symbol == null || scope == null) {
            symbol = registerStructShell(node, new Scope(null, null));
            scope = structScopes.get(node);
        }

        List<StructFieldDeclNode> fields = new ArrayList<>();
        List<FunctionDeclNode> methods = new ArrayList<>();
        List<ASTNode> members = new ArrayList<>();

        ctx.setCurrentNamespace(scope.symbol);

        appendSelectedMembers(
                node.memberItems,
                symbol,
                scope,
                fields,
                methods,
                members
        );

        selectedStructs.put(node, new StructDeclNode(
                node.span,
                node.name,
                fields,
                methods,
                members
        ));
    }

    private void appendSelectedMembers(
            List<ASTNode> items,
            StructSymbol symbol,
            Scope scope,
            List<StructFieldDeclNode> fields,
            List<FunctionDeclNode> methods,
            List<ASTNode> members
    ) {
        for (ASTNode item : items) {
            if (item instanceof DeclIfNode declIf) {
                boolean condition = evaluateCondition(declIf.condition, scope);
                List<ASTNode> branch = condition
                        ? declIf.thenItems
                        : declIf.elseItems;

                appendSelectedMembers(
                        branch,
                        symbol,
                        scope,
                        fields,
                        methods,
                        members
                );
                continue;
            }

            if (item instanceof CompileTimeCallNode
                    || item instanceof CompileTimeNameNode) {
                evaluateDeclarationItem(item, scope);
                continue;
            }

            registerMember(item, symbol);

            if (item instanceof StructFieldDeclNode field) {
                if (!fields.contains(field)) {
                    fields.add(field);
                    members.add(field);
                }
            } else if (item instanceof FunctionDeclNode method) {
                if (!methods.contains(method)) {
                    methods.add(method);
                    members.add(method);
                }
            }
        }
    }

    private void evaluateDeclarationItem(ASTNode item, Scope scope) {
        ctx.setCurrentNamespace(scope == null ? null : scope.symbol);
        diagnostics.push(item.span);

        try {
            if (item instanceof CompileTimeCallNode call
                    && !"panic".equals(call.name)) {
                diagnostics.error("Only #panic is allowed as a "
                        + "declaration-level compile-time item");
                return;
            }

            analyzer.analyzeExpr(item);
        } finally {
            diagnostics.pop();
        }
    }

    private boolean evaluateCondition(ASTNode condition, Scope scope) {
        ctx.setCurrentNamespace(scope == null ? null : scope.symbol);
        diagnostics.push(condition.span);

        try {
            SemanticExprNode value = analyzer.analyzeExpr(condition);

            if (!(value.type instanceof SemanticPrimitiveType)) {
                diagnostics.error("#if condition must be an integer, got "
                        + value.type.describe());
                return false;
            }

            if (!value.isConstant()) {
                diagnostics.error("#if condition must be a compile-time constant");
                return false;
            }

            return value.constantValue.signum() != 0;
        } finally {
            diagnostics.pop();
        }
    }

    private List<ASTNode> selectItems(List<ASTNode> items, Scope scope) {
        List<ASTNode> out = new ArrayList<>();

        for (ASTNode item : items) {
            if (item instanceof DeclIfNode declIf) {
                boolean condition = evaluateCondition(declIf.condition, scope);
                List<ASTNode> branch = condition
                        ? declIf.thenItems
                        : declIf.elseItems;

                out.addAll(selectItems(branch, scope));
                continue;
            }

            if (item instanceof CompileTimeCallNode
                    || item instanceof CompileTimeNameNode) {
                evaluateDeclarationItem(item, scope);
                continue;
            }

            if (item instanceof NamespaceNode node) {
                Scope child = namespaceFor(node, scope);
                List<ASTNode> selected = selectItems(node.items, child);

                out.add(new NamespaceNode(node.span, node.name, selected));
                continue;
            }

            if (item instanceof StructDeclNode struct) {
                registerStructShell(struct, scope);
                StructSymbol symbol = structSymbols.get(struct);

                if (symbol != null) {
                    registerUnconditionalMembers(struct, symbol);
                }

                processStruct(struct);
                out.add(selectedStructs.get(struct));
                continue;
            }

            if (item instanceof GlobalVarDeclNode global) {
                registerGlobal(global, scope);
                out.add(global);
                continue;
            }

            out.add(item);
        }

        return out;
    }
}
