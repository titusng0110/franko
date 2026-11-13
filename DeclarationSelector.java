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
        final AstNode program;
        final List<Diagnostic> diagnostics;

        Selection(AstNode program, List<Diagnostic> diagnostics) {
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

    private final DiagnosticBag diagnostics = new DiagnosticBag();
    private final SymbolTable ctx = new SymbolTable(diagnostics);
    private final ConstExpressionEvaluator constEval;
    private final ExpressionAnalyzer analyzer;
    private final SemanticTypeResolver typeResolver;

    private final Map<String, NamespaceSymbol> namespaces =
            new LinkedHashMap<>();
    private final Map<AstNode, StructSymbol> structSymbols =
            new IdentityHashMap<>();
    private final Map<AstNode, Scope> structScopes =
            new IdentityHashMap<>();
    private final Map<AstNode, EnumSymbol> enumSymbols =
            new IdentityHashMap<>();
    private final Map<AstNode, Scope> enumScopes =
            new IdentityHashMap<>();
    private final Set<AstNode> registeredMembers =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<AstNode> registeredGlobals =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<AstNode> processedStructs =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<AstNode, AstNode> selectedStructs =
            new IdentityHashMap<>();

    private boolean preGenerics = false;

    DeclarationSelector(TargetInfo target) {
        this(target, null);
    }

    DeclarationSelector(TargetInfo target, DeferredDirectives directives) {
        this.constEval = new ConstExpressionEvaluator(ctx);

        ExpressionLowering lowering = new ExpressionLowering(
                ctx,
                constEval,
                new ArrayLiteralHoister(),
                new StaticStringHoister(),
                target,
                directives
        );

        this.analyzer = lowering.analyzer;
        this.typeResolver = lowering.typeResolver;
    }

    Selection select(AstNode program) {
        return select(program, false);
    }

    Selection selectPreGenerics(AstNode program) {
        return select(program, true);
    }

    Selection selectPostGenerics(AstNode program) {
        return select(program, false);
    }

    private Selection select(AstNode program, boolean preGenericsMode) {
        this.preGenerics = preGenericsMode;
        ctx.pushScope();

        Scope global = new Scope(null, null);
        preRegisterShells(program.items, global);
        preRegisterBodies(program.items, global);

        for (AstNode struct : new ArrayList<>(structSymbols.keySet())) {
            processStruct(struct);
        }

        List<AstNode> selected = selectItems(program.items, global);

        ctx.popScope();

        return new Selection(
                AstNode.program(program.span, selected),
                diagnostics.diagnostics()
        );
    }

    private void preRegisterShells(List<AstNode> items, Scope scope) {
        for (AstNode item : items) {
            if (item.kind == AstKind.NAMESPACE) {
                Scope child = namespaceFor(item, scope);
                preRegisterShells(item.items, child);
            } else if (item.kind == AstKind.STRUCT_DECL) {
                if (preGenerics && item.isGeneric) {
                    continue;
                }
                registerStructShell(item, scope);
            } else if (item.kind == AstKind.ENUM_DECL) {
                registerEnumShell(item, scope);
            }
        }
    }

    private void preRegisterBodies(List<AstNode> items, Scope scope) {
        for (AstNode item : items) {
            if (item.kind == AstKind.NAMESPACE) {
                preRegisterBodies(item.items, namespaceFor(item, scope));
            } else if (item.kind == AstKind.STRUCT_DECL) {
                if (preGenerics && item.isGeneric) {
                    continue;
                }
                StructSymbol symbol = registerStructShell(item, scope);
                registerUnconditionalMembers(item, symbol);
            } else if (item.kind == AstKind.GLOBAL_VAR_DECL) {
                registerGlobal(item, scope);
            }
        }
    }

    private Scope namespaceFor(AstNode node, Scope parent) {
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
            AstNode node,
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

    private EnumSymbol registerEnumShell(
            AstNode node,
            Scope scope
    ) {
        ctx.setCurrentNamespace(scope.symbol);

        EnumSymbol existing = enumSymbols.get(node);

        if (existing != null) {
            return existing;
        }

        String namespaceName = scope.symbol == null
                ? null
                : scope.symbol.name;
        EnumSymbol symbol = buildEnumSymbolQuiet(node, namespaceName);

        if (!ctx.declareEnum(symbol)) {
            EnumSymbol resolved = ctx.resolveEnumIn(scope.symbol, node.name);

            if (resolved != null) {
                symbol = resolved;
            }
        }

        enumSymbols.put(node, symbol);
        enumScopes.put(node, scope);
        return symbol;
    }

    private EnumSymbol buildEnumSymbolQuiet(AstNode node, String namespaceName) {
        SemanticPrimitiveKind backingKind = SemanticPrimitiveKind.INT32;

        if (node.type != null && node.type.kind == AstKind.PRIMITIVE_TYPE) {
            try {
                backingKind = SemanticPrimitiveKind.valueOf(node.type.primitive.name());
            } catch (IllegalArgumentException ignored) {
                backingKind = SemanticPrimitiveKind.INT32;
            }
        }

        if (node.variants.isEmpty()) {
            return new EnumSymbol(node.name, namespaceName, backingKind, List.of());
        }

        java.util.Set<String> seenNames = new java.util.HashSet<>();
        List<AstNode> uniqueVariants = new ArrayList<>();

        for (AstNode variant : node.variants) {
            if (!seenNames.add(variant.name)) {
                continue;
            }

            uniqueVariants.add(variant);
        }

        int explicitCount = 0;

        for (AstNode variant : uniqueVariants) {
            if (variant.value != null) {
                explicitCount++;
            }
        }

        boolean allExplicit = explicitCount == uniqueVariants.size();
        List<EnumVariant> computed = new ArrayList<>();

        if (allExplicit) {
            for (AstNode variant : uniqueVariants) {
                BigInteger value = parseEnumVariantValueQuiet(variant);
                computed.add(new EnumVariant(variant.name, value));
            }
        } else {
            BigInteger lastValue = null;

            for (AstNode variant : uniqueVariants) {
                BigInteger value;

                if (variant.value != null) {
                    value = parseEnumVariantValueQuiet(variant);
                } else if (lastValue == null) {
                    value = TypeChecker.wrapBigIntegerToPrimitive(BigInteger.ZERO, backingKind);
                } else {
                    value = TypeChecker.wrapBigIntegerToPrimitive(lastValue.add(BigInteger.ONE), backingKind);
                }

                computed.add(new EnumVariant(variant.name, value));
                lastValue = value;
            }
        }

        return new EnumSymbol(node.name, namespaceName, backingKind, computed);
    }

    private static BigInteger parseEnumVariantValueQuiet(AstNode variant) {
        if (variant.value != null && variant.value.kind == AstKind.INT) {
            try {
                BigInteger parsed = ConstExpressionEvaluator.parseIntegerLiteralPure(variant.value.text);

                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }

        if (variant.value != null) {
            BigInteger folded = ConstExpressionEvaluator.tryFold(variant.value);

            if (folded != null) {
                return folded;
            }
        }

        return BigInteger.ZERO;
    }

    private void registerUnconditionalMembers(
            AstNode node,
            StructSymbol symbol
    ) {
        for (AstNode member : node.members) {
            if (member.kind == AstKind.DECL_IF) {
                continue;
            }

            registerMember(member, symbol);
        }
    }

    private void registerMember(AstNode member, StructSymbol symbol) {
        if (!registeredMembers.add(member)) {
            return;
        }

        if (member.kind == AstKind.STRUCT_FIELD_DECL) {
            if (symbol.hasFieldName(member.name)) {
                return;
            }

            SastType fieldType = resolveTypeForSelection(member.type);
            BigInteger defaultValue = null;

            if (member.value != null) {
                defaultValue = analyzer.preEvaluatedConstValue(
                        member.value,
                        "struct field initializer"
                );
            }

            symbol.fields.add(new FieldSymbol(
                    member.name,
                    fieldType,
                    defaultValue
            ));
            return;
        }

        if (member.kind == AstKind.FUNCTION_DECL) {
            if (preGenerics && member.isGeneric) {
                return;
            }

            List<Symbol> params = new ArrayList<>();

            for (int i = 0; i < member.parameters.size(); i++) {
                AstNode param = member.parameters.get(i);
                params.add(Symbol.parameter(
                        param.name,
                        resolveTypeForSelection(param.type),
                        i,
                        param.isConst
                                || (i == 0 && "self".equals(param.name))
                ));
            }

            Symbol method = Symbol.method(
                    symbol,
                    member.name,
                    params,
                    resolveReturnTypeForSelection(member.type),
                    false
            );

            ctx.declareMethod(symbol, member, method);
        }
    }

    private void registerGlobal(AstNode node, Scope scope) {
        if (!registeredGlobals.add(node)) {
            return;
        }

        ctx.setCurrentNamespace(scope.symbol);

        SastType type = resolveTypeForSelection(node.type);
        String namespaceName = scope.symbol == null
                ? null
                : scope.symbol.name;

        Symbol symbol = Symbol.global(
                namespaceName,
                node.name,
                type,
                node.isConst
        );

        ctx.declareGlobalVariable(symbol);
    }

    private SastType resolveTypeForSelection(AstNode type) {
        if (preGenerics && GenericsResolver.containsGenericUse(type)) {
            return SemanticTypeResolver.FALLBACK_TYPE;
        }

        return typeResolver.analyzeNormalType(type);
    }

    private SastType resolveReturnTypeForSelection(AstNode type) {
        if (type != null && type.kind == AstKind.VOID_TYPE) {
            return SemanticTypeResolver.VOID_TYPE;
        }

        return resolveTypeForSelection(type);
    }

    private void processStruct(AstNode node) {
        if (!processedStructs.add(node)) {
            return;
        }

        StructSymbol symbol = structSymbols.get(node);
        Scope scope = structScopes.get(node);

        if (symbol == null || scope == null) {
            symbol = registerStructShell(node, new Scope(null, null));
            scope = structScopes.get(node);
        }

        List<AstNode> fields = new ArrayList<>();
        List<AstNode> methods = new ArrayList<>();
        List<AstNode> members = new ArrayList<>();

        ctx.setCurrentNamespace(scope.symbol);

        appendSelectedMembers(
                node.members,
                symbol,
                scope,
                fields,
                methods,
                members
        );

        selectedStructs.put(node, AstNode.structDecl(
                node.span,
                node.name,
                fields,
                methods,
                members
        ));
    }

    private void appendSelectedMembers(
            List<AstNode> items,
            StructSymbol symbol,
            Scope scope,
            List<AstNode> fields,
            List<AstNode> methods,
            List<AstNode> members
    ) {
        for (AstNode item : items) {
            if (item.kind == AstKind.DECL_IF) {
                if (preGenerics
                        && GenericsResolver.containsGenericUse(item.condition)) {
                    members.add(item);
                    continue;
                }

                boolean condition = evaluateCondition(item.condition, scope);
                List<AstNode> branch = condition
                        ? item.thenItems
                        : item.elseItems;

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

            if (item.kind == AstKind.COMPILE_TIME_CALL
                    || item.kind == AstKind.COMPILE_TIME_NAME) {
                evaluateDeclarationItem(item, scope);
                continue;
            }

            registerMember(item, symbol);

            if (item.kind == AstKind.STRUCT_FIELD_DECL) {
                if (!fields.contains(item)) {
                    fields.add(item);
                    members.add(item);
                }
            } else if (item.kind == AstKind.FUNCTION_DECL) {
                if (!methods.contains(item)) {
                    methods.add(item);
                    members.add(item);
                }
            }
        }
    }

    private void evaluateDeclarationItem(AstNode item, Scope scope) {
        ctx.setCurrentNamespace(scope == null ? null : scope.symbol);
        diagnostics.push(item.span);

        try {
            if (item.kind == AstKind.COMPILE_TIME_CALL
                    && !"panic".equals(item.name)
                    && !"warning".equals(item.name)) {
                diagnostics.error("Only #panic and #warning are allowed as a "
                        + "declaration-level compile-time item");
                return;
            }

            if (item.kind == AstKind.COMPILE_TIME_CALL) {
                analyzer.analyzeCompileTimeDirective(item);
                return;
            }

            analyzer.analyzeExpr(item);
        } finally {
            diagnostics.pop();
        }
    }

    private boolean evaluateCondition(AstNode condition, Scope scope) {
        ctx.setCurrentNamespace(scope == null ? null : scope.symbol);
        diagnostics.push(condition.span);

        try {
            SastNode value = analyzer.analyzeExpr(condition);

            if (!(value.type.kind == SastTypeKind.PRIMITIVE)) {
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

    private List<AstNode> selectItems(List<AstNode> items, Scope scope) {
        List<AstNode> out = new ArrayList<>();

        for (AstNode item : items) {
            if (item.kind == AstKind.DECL_IF) {
                if (preGenerics
                        && GenericsResolver.containsGenericUse(item.condition)) {
                    out.add(item);
                    continue;
                }

                boolean condition = evaluateCondition(item.condition, scope);
                List<AstNode> branch = condition
                        ? item.thenItems
                        : item.elseItems;

                out.addAll(selectItems(branch, scope));
                continue;
            }

            if (item.kind == AstKind.COMPILE_TIME_CALL
                    || item.kind == AstKind.COMPILE_TIME_NAME) {
                evaluateDeclarationItem(item, scope);
                continue;
            }

            if (item.kind == AstKind.NAMESPACE) {
                Scope child = namespaceFor(item, scope);
                List<AstNode> selected = selectItems(item.items, child);

                out.add(AstNode.namespace(item.span, item.name, selected));
                continue;
            }

            if (item.isGeneric) {
                if (preGenerics) {
                    out.add(item);
                }

                continue;
            }

            if (item.kind == AstKind.STRUCT_DECL) {
                registerStructShell(item, scope);
                StructSymbol symbol = structSymbols.get(item);

                if (symbol != null) {
                    registerUnconditionalMembers(item, symbol);
                }

                processStruct(item);
                out.add(selectedStructs.get(item));
                continue;
            }

            if (item.kind == AstKind.ENUM_DECL) {
                registerEnumShell(item, scope);
                out.add(item);
                continue;
            }

            if (item.kind == AstKind.GLOBAL_VAR_DECL) {
                registerGlobal(item, scope);
                out.add(item);
                continue;
            }

            out.add(item);
        }

        return out;
    }
}
