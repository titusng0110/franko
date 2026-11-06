import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SemanticAnalyzer {

    private final SymbolTable symbolTable = new SymbolTable();

    private final ConstExpressionEvaluator constEval;
    private final ExpressionAnalyzer exprAnalyzer;
    private final SemanticTypeResolver typeResolver;

    private Symbol currentFunction = null;

    private int loopDepth = 0;

    private final Map<String, Symbol> hoistedArrayLiteralGlobals =
            new LinkedHashMap<>();

    private final List<SemanticVarDeclNode> pendingHoistedArrayLiterals =
            new ArrayList<>();

    private final Map<String, Symbol> hoistedStaticStringGlobals =
            new LinkedHashMap<>();

    private final List<SemanticVarDeclNode> pendingHoistedStaticStrings =
            new ArrayList<>();

    private EntryKind entryKind = EntryKind.NONE;
    private int globalMainCount = 0;

    private final TargetInfo target;

    public SemanticAnalyzer(TargetInfo target) {
        this.target = target;
        this.constEval = new ConstExpressionEvaluator(symbolTable);

        ExpressionLowering lowering = new ExpressionLowering(
                symbolTable,
                constEval,
                new ArrayLiteralLowerer.Hoister() {
                    @Override
                    public SemanticVarExprNode hoistArrayLiteral(
                            SastType arrayType,
                            List<BigInteger> values,
                            SourceSpan span) {
                        return hoistArrayLiteralGlobal(arrayType, values, span);
                    }
                },
                this::hoistStaticStringGlobal,
                target
        );

        this.exprAnalyzer = lowering.analyzer;
        this.typeResolver = lowering.typeResolver;
    }

    public SemanticASTNode analyze(AstNode root) {
        symbolTable.clear();
        typeResolver.resetTupleTypes();
        hoistedArrayLiteralGlobals.clear();
        pendingHoistedArrayLiterals.clear();
        hoistedStaticStringGlobals.clear();
        pendingHoistedStaticStrings.clear();
        symbolTable.pushScope();
        entryKind = EntryKind.NONE;
        globalMainCount = 0;

        SemanticASTNode result;

        if (root != null && root.kind == AstKind.PROGRAM) {
            DeclarationSelector.Selection selection =
                    new DeclarationSelector(target).select(root);
            symbolTable.diagnostics().addAll(selection.diagnostics);
            result = analyzeProgram(selection.program);
        } else if (root != null && root.kind == AstKind.FUNCTION_DECL) {

            registerFunctionSignature(root);
            result = analyzeFunctionDecl(root);
        } else {
            result = analyzeStmt(root);

            if (result == null) {
                result = exprAnalyzer.analyzeExpr(root);
            }
        }

        symbolTable.popScope();

        return result;
    }

    public PassResult analyze(AstDocument ast) {
        if (ast == null) {
            throw new IllegalArgumentException("Cannot analyze a null AST document");
        }

        SemanticASTNode root = analyze(ast.root);
        SemanticProgramNode program;

        if (root instanceof SemanticProgramNode semanticProgram) {
            program = semanticProgram;
        } else if (root == null) {
            program = new SemanticProgramNode(
                    ast.root == null ? SourceSpan.NONE : ast.root.span,
                    List.of()
            );
        } else {
            program = new SemanticProgramNode(root.span, List.of(root));
        }

        SymbolRegistry registry = new SymbolRegistry();
        SemanticASTSerializer.registerFromNode(program, registry);
        registry.registerAll(symbolTable.allSymbols());
        SastDocument document = new SastDocument(
                SastPhase.SEMANTIC,
                entryKind,
                ast.sources,
                registry,
                program
        );

        return new PassResult(document, symbolTable.diagnostics().diagnostics());
    }

    public List<SemanticStmtNode> analyze(List<AstNode> nodes) {
        symbolTable.clear();
        typeResolver.resetTupleTypes();
        hoistedArrayLiteralGlobals.clear();
        pendingHoistedArrayLiterals.clear();
        hoistedStaticStringGlobals.clear();
        pendingHoistedStaticStrings.clear();
        symbolTable.pushScope();

        List<SemanticStmtNode> out = new ArrayList<>();

        for (AstNode stmt : nodes) {
            appendAnalyzedStmt(stmt, out);
        }

        symbolTable.popScope();

        return out;
    }

    private static final class NamespaceProgramItems {
        final NamespaceSymbol space;
        final SourceSpan span;
        final List<AstNode> items = new ArrayList<>();

        NamespaceProgramItems(NamespaceSymbol space, SourceSpan span) {
            this.space = space;
            this.span = span;
        }
    }

    SemanticVarExprNode hoistArrayLiteralGlobal(
            SastType arrayType,
            List<BigInteger> values,
            SourceSpan span
    ) {
        StringBuilder key = new StringBuilder(arrayType.mangleKey());
        key.append('|');

        for (BigInteger value : values) {
            key.append(value.toString(10)).append(',');
        }

        String keyText = key.toString();

        Symbol existing =
                hoistedArrayLiteralGlobals.get(keyText);

        if (existing != null) {
            return new SemanticVarExprNode(span, existing.type, existing);
        }

        String baseName = "ARRLIT_" + Mangler.hashHex64(keyText);
        String name = baseName;
        int suffix = 0;

        Symbol sym;

        while (true) {
            Symbol candidate =
                    Symbol.global(null, name, arrayType);

            if (symbolTable.declareGlobalNamespaceVariable(candidate)) {
                sym = candidate;
                break;
            }

            suffix++;
            name = baseName + "_" + suffix;
        }

        sym.literalValues = List.copyOf(values);
        hoistedArrayLiteralGlobals.put(keyText, sym);
        pendingHoistedArrayLiterals.add(new SemanticVarDeclNode(span, sym));

        return new SemanticVarExprNode(span, sym.type, sym);
    }

    Symbol hoistStaticStringGlobal(
            List<BigInteger> bytes,
            SourceSpan span
    ) {
        StringBuilder key = new StringBuilder();

        for (BigInteger value : bytes) {
            key.append(value.toString(10)).append(',');
        }

        String keyText = key.toString();

        Symbol existing =
                hoistedStaticStringGlobals.get(keyText);

        if (existing != null) {
            return existing;
        }

        SastType arrayType = SastType.staticArray(
                SastType.primitive(SemanticPrimitiveKind.UINT8),
                Integer.toString(bytes.size())
        );

        String baseName = "STRLIT_" + Mangler.hashHex64(keyText);
        String name = baseName;
        int suffix = 0;

        while (true) {
            Symbol candidate = symbolTable.resolveGlobalVarIn(null, name);

            if (candidate == null) {
                Symbol sym = Symbol.global(
                        null,
                        name,
                        arrayType
                );

                if (symbolTable.declareGlobalNamespaceVariable(sym)) {
                    sym.literalBytes = List.copyOf(bytes);
                    hoistedStaticStringGlobals.put(keyText, sym);
                    pendingHoistedStaticStrings.add(
                            new SemanticVarDeclNode(span, sym));
                    return sym;
                }
            } else if (candidate.kind == SymbolKind.GLOBAL
                    && candidate.literalBytes != null
                    && candidate.literalBytes.equals(bytes)
                    && candidate.type.equals(arrayType)) {
                hoistedStaticStringGlobals.put(keyText, candidate);
                return candidate;
            }

            suffix++;
            name = baseName + "_" + suffix;
        }
    }

    private SemanticProgramNode analyzeProgram(AstNode program) {

        for (AstNode item : program.items) {
            symbolTable.diagnostics().push(item.span);
            try {
            if (item.kind == AstKind.STRUCT_DECL
                    || item.kind == AstKind.ENUM_DECL
                    || item.kind == AstKind.GLOBAL_VAR_DECL
                    || item.kind == AstKind.FUNCTION_DECL
                    || item.kind == AstKind.NAMESPACE) {
                continue;
            }

            symbolTable.error("Invalid top-level item '"
                    + item.kind.legacyClassName()
                    + "': only struct declarations, global variable declarations, function declarations, and namespace declarations are allowed at global scope");
            } finally {
                symbolTable.diagnostics().pop();
            }
        }

        List<NamespaceProgramItems> spaces = new ArrayList<>();
        Map<String, NamespaceProgramItems> spacesByName = new LinkedHashMap<>();

        NamespaceProgramItems globalSpace = new NamespaceProgramItems(null, program.span);
        spaces.add(globalSpace);

        for (AstNode item : program.items) {
            if (item.kind == AstKind.NAMESPACE) {
                collectNamespaceDecl(item, null, spaces, spacesByName);
                continue;
            }

            globalSpace.items.add(item);
        }

        Map<AstNode, Symbol> globalSymbols =
                new IdentityHashMap<>();
        Map<AstNode, BigInteger> globalInitializers =
                new IdentityHashMap<>();
        Map<AstNode, AstNode> globalStaticInitializers =
                new IdentityHashMap<>();
        Map<AstNode, AstNode> globalArrayLiteralInitializers =
                new IdentityHashMap<>();

        Map<AstNode, SemanticASTNode> loweredByNode = new IdentityHashMap<>();

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (AstNode item : space.items) {
                if (item.kind == AstKind.ENUM_DECL) {
                    EnumSymbol enumSymbol = typeResolver.analyzeEnumDecl(item, currentNamespaceName());
                    symbolTable.declareEnum(enumSymbol);
                }
            }
        }

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (AstNode item : space.items) {
                if (item.kind == AstKind.STRUCT_DECL) {
                    symbolTable.declareStruct(
                            new StructSymbol(currentNamespaceName(), item.name)
                    );
                }
            }
        }

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (AstNode item : space.items) {
                if (item.kind != AstKind.STRUCT_DECL) {
                    continue;
                }

                StructSymbol structSymbol = symbolTable.resolveStruct(item.name);

                if (structSymbol == null) {
                    continue;
                }

                Set<String> seenFieldNames = new HashSet<>();

                for (AstNode fieldNode : item.fields) {
                    if (!seenFieldNames.add(fieldNode.name)) {
                        symbolTable.error("Duplicate field '"
                                + fieldNode.name
                                + "' in struct '"
                                + structSymbol.qualifiedName()
                                + "'");
                        continue;
                    }

                    SastType fieldType = typeResolver.analyzeNormalType(fieldNode.type);

                    BigInteger fieldDefault = null;

                    if (fieldNode.value != null) {
                        fieldDefault = exprAnalyzer.preEvaluatedConstValue(
                                fieldNode.value,
                                "struct field initializer"
                        );
                    }

                    structSymbol.fields.add(new FieldSymbol(
                            fieldNode.name,
                            fieldType,
                            fieldDefault
                    ));
                }
            }
        }

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (AstNode item : space.items) {
                if (item.kind != AstKind.STRUCT_DECL) {
                    continue;
                }

                StructSymbol structSymbol = symbolTable.resolveStruct(item.name);

                if (structSymbol == null) {
                    continue;
                }

                for (AstNode methodNode : item.methods) {
                    registerMethodSignature(structSymbol, methodNode);
                }
            }
        }

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (AstNode item : space.items) {
                if (item.kind != AstKind.GLOBAL_VAR_DECL) {
                    continue;
                }

                SastType type = typeResolver.analyzeNormalType(item.type);

                Symbol sym = Symbol.global(
                        currentNamespaceName(),
                        item.name,
                        type
                );

                if (item.literalBytes != null) {
                    sym.literalBytes = item.literalBytes;
                }

                if (symbolTable.declareGlobalVariable(sym)) {
                    globalSymbols.put(item, sym);
                }

                if (item.literalBytes != null) {
                    continue;
                }

                if (item.value != null && item.value.kind == AstKind.ARRAY_LITERAL) {
                    globalArrayLiteralInitializers.put(item, item.value);
                } else if (item.value != null && item.value.kind == AstKind.GET_PTR) {
                    globalStaticInitializers.put(item, item.value);
                } else if (item.value != null) {
                    globalInitializers.put(item, exprAnalyzer.preEvaluatedConstValue(
                            item.value,
                            "global variable initializer"
                    ));
                }

                if (item.compileTimeConstant) {
                    BigInteger value = globalInitializers.get(item);

                    if (value != null) {
                        sym.compileTimeConstantValue = value;
                    }
                }
            }
        }

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (AstNode item : space.items) {
                if (item.kind == AstKind.FUNCTION_DECL) {
                    registerFunctionSignature(item);
                }
            }
        }

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (AstNode item : space.items) {
                if (item.kind == AstKind.ENUM_DECL) {
                    EnumSymbol enumSymbol = symbolTable.resolveEnum(item.name);

                    if (enumSymbol != null) {
                        loweredByNode.put(
                                item,
                                new SemanticEnumDeclNode(item.span, enumSymbol)
                        );
                    }

                    continue;
                }

                if (item.kind == AstKind.STRUCT_DECL) {
                    StructSymbol structSymbol = symbolTable.resolveStruct(item.name);

                    if (structSymbol != null) {
                        List<SemanticFunctionDeclNode> loweredMethods =
                                new ArrayList<>();

                        for (AstNode methodNode : item.methods) {
                            SemanticFunctionDeclNode loweredMethod =
                                    analyzeMethodDecl(structSymbol, methodNode);

                            if (loweredMethod != null) {
                                loweredMethods.add(loweredMethod);
                            }
                        }

                        loweredByNode.put(
                                item,
                                new SemanticStructDeclNode(
                                        item.span,
                                        structSymbol,
                                        loweredMethods
                                )
                        );
                    }

                    continue;
                }

                if (item.kind == AstKind.GLOBAL_VAR_DECL) {
                    Symbol sym = globalSymbols.get(item);

                    if (sym != null) {
                        AstNode staticInit = globalStaticInitializers.get(item);

                        SemanticExprNode loweredStaticInit = staticInit == null
                                ? null
                                : exprAnalyzer.analyzeExprWithExpectedType(
                                        staticInit,
                                        sym.type);

                        AstNode arrayLiteralInit =
                                globalArrayLiteralInitializers.get(item);

                        if (arrayLiteralInit != null) {
                            loweredStaticInit =
                                    exprAnalyzer.analyzeExprWithExpectedType(
                                            arrayLiteralInit,
                                            sym.type);
                        }

                        loweredByNode.put(item, new SemanticVarDeclNode(
                                item.span,
                                sym,
                                globalInitializers.get(item),
                                loweredStaticInit
                        ));
                    }

                    continue;
                }

                if (item.kind == AstKind.FUNCTION_DECL) {
                    SemanticFunctionDeclNode lowered = analyzeFunctionDecl(item);

                    if (lowered != null) {
                        loweredByNode.put(item, lowered);
                    }
                }
            }
        }

        symbolTable.setCurrentNamespace(null);

        List<SemanticASTNode> semanticItems = new ArrayList<>();
        Set<String> emittedNamespaces = new LinkedHashSet<>();

        for (AstNode item : program.items) {
            if (item.kind == AstKind.NAMESPACE) {
                emitNamespacePath(
                        item.name,
                        spaces,
                        spacesByName,
                        loweredByNode,
                        semanticItems,
                        emittedNamespaces
                );

                continue;
            }

            SemanticASTNode lowered = loweredByNode.get(item);

            if (lowered != null) {
                semanticItems.add(lowered);
            }
        }

        for (NamespaceProgramItems space : spaces) {
            if (space.space == null) {
                continue;
            }

            if (emittedNamespaces.add(space.space.name)) {
                semanticItems.add(new SemanticNamespaceNode(
                        space.span,
                        space.space.name,
                        loweredItemsOf(space, loweredByNode)
                ));
            }
        }

        for (int i = pendingHoistedStaticStrings.size() - 1; i >= 0; i--) {
            semanticItems.add(0, pendingHoistedStaticStrings.get(i));
        }

        for (int i = pendingHoistedArrayLiterals.size() - 1; i >= 0; i--) {
            semanticItems.add(0, pendingHoistedArrayLiterals.get(i));
        }

        List<SemanticStructDeclNode> pendingTuples =
                typeResolver.pendingTupleDecls();

        for (int i = pendingTuples.size() - 1; i >= 0; i--) {
            semanticItems.add(0, pendingTuples.get(i));
        }

        symbolTable.warnStructNameClashes();

        if (globalMainCount == 0) {
            symbolTable.error(
                    "No entry-point function 'main' declared: declare either 'func main() -> int32' or 'func main(view<[[byte]]> <name>) -> int32'"
            );
        }

        return new SemanticProgramNode(program.span, semanticItems);
    }

    private void collectNamespaceDecl(
            AstNode node,
            String parentPath,
            List<NamespaceProgramItems> spaces,
            Map<String, NamespaceProgramItems> spacesByName
    ) {
        String fullPath;

        if (parentPath == null) {
            fullPath = node.name;
        } else if (node.name.contains("::")) {
            symbolTable.error("Nested namespace paths are not allowed: '"
                    + node.name
                    + "' is declared inside namespace '"
                    + parentPath
                    + "'; declare it as '"
                    + parentPath + "::" + node.name
                    + "' at the top level instead");
            return;
        } else {
            fullPath = parentPath + "::" + node.name;
        }

        NamespaceSymbol space = symbolTable.declareNamespace(fullPath);

        NamespaceProgramItems spaceItems = spacesByName.computeIfAbsent(
                fullPath,
                ignored -> {
                    NamespaceProgramItems created =
                            new NamespaceProgramItems(space, node.span);
                    spaces.add(created);
                    return created;
                }
        );

        for (AstNode item : node.items) {
            if (item.kind == AstKind.NAMESPACE) {
                collectNamespaceDecl(item, fullPath, spaces, spacesByName);
                continue;
            }

            spaceItems.items.add(item);
        }
    }

    private void emitNamespacePath(
            String path,
            List<NamespaceProgramItems> spaces,
            Map<String, NamespaceProgramItems> spacesByName,
            Map<AstNode, SemanticASTNode> loweredByNode,
            List<SemanticASTNode> semanticItems,
            Set<String> emittedNamespaces
    ) {
        NamespaceProgramItems space = spacesByName.get(path);

        if (space != null && emittedNamespaces.add(path)) {
            semanticItems.add(new SemanticNamespaceNode(
                    space.span,
                    path,
                    loweredItemsOf(space, loweredByNode)
            ));
        }

        String prefix = path + "::";

        for (NamespaceProgramItems nested : spaces) {
            if (nested.space == null) {
                continue;
            }

            if (!nested.space.name.startsWith(prefix)) {
                continue;
            }

            if (emittedNamespaces.add(nested.space.name)) {
                semanticItems.add(new SemanticNamespaceNode(
                        nested.span,
                        nested.space.name,
                        loweredItemsOf(nested, loweredByNode)
                ));
            }
        }
    }

    private List<SemanticASTNode> loweredItemsOf(
            NamespaceProgramItems space,
            Map<AstNode, SemanticASTNode> loweredByNode
    ) {
        List<SemanticASTNode> out = new ArrayList<>();

        for (AstNode item : space.items) {
            SemanticASTNode lowered = loweredByNode.get(item);

            if (lowered != null) {
                out.add(lowered);
            }
        }

        return out;
    }

    private String currentNamespaceName() {
        NamespaceSymbol space = symbolTable.currentNamespace();

        return space == null ? null : space.name;
    }

    private void registerFunctionSignature(AstNode fn) {

        if ("main".equals(fn.name) && currentNamespaceName() != null) {
            symbolTable.error("The entry-point function 'main' must be declared in the global namespace, not in namespace '"
                    + currentNamespaceName()
                    + "'");
        }

        List<Symbol> params = new ArrayList<>();

        for (int i = 0; i < fn.parameters.size(); i++) {
            AstNode param = fn.parameters.get(i);

            params.add(Symbol.parameter(
                    param.name,
                    typeResolver.analyzeNormalType(param.type),
                    i
            ));
        }

        for (Symbol param : params) {
            if ("self".equals(param.name)) {
                symbolTable.error("'self' is only valid as the first parameter of a struct method");
                break;
            }
        }

        Symbol symbol = Symbol.function(
                currentNamespaceName(),
                fn.name,
                params,
                typeResolver.analyzeReturnType(fn.type),
                false
        );

        if ("main".equals(fn.name) && currentNamespaceName() == null) {
            boolean returnsInt32 = symbol.returnType().kind == SastTypeKind.PRIMITIVE
                    && symbol.returnType().primitive == SemanticPrimitiveKind.INT32;

            boolean noArgs = params.isEmpty();
            boolean hasArgsShape = params.size() == 1
                    && isArgsArrayParamType(params.get(0).type);

            globalMainCount++;

            if (globalMainCount > 1) {
                symbolTable.error(
                        "The entry-point function 'main' cannot be overloaded: declare either 'func main() -> int32' or 'func main(view<[[byte]]> <name>) -> int32', not both"
                );
            } else if (!returnsInt32 || (!noArgs && !hasArgsShape)) {
                symbolTable.error(
                        "The entry-point function 'main' must be declared as 'func main() -> int32' or 'func main(view<[[byte]]> <name>) -> int32'"
                );
            } else {
                entryKind = noArgs ? EntryKind.NO_ARGS : EntryKind.ARGS;
            }
        }

        symbolTable.declareFunction(fn, symbol);
    }

    private static boolean isArgsArrayParamType(SastType type) {
        if (!(type.kind == SastTypeKind.PTR)
                || type.permission != PtrPermission.READ_ONLY) {
            return false;
        }

        if (!(type.referencedType.kind == SastTypeKind.DYNAMIC_ARRAY)) {
            return false;
        }

        if (!(type.referencedType.elementType.kind == SastTypeKind.DYNAMIC_ARRAY)) {
            return false;
        }

        return type.referencedType.elementType.elementType.kind == SastTypeKind.PRIMITIVE
                && type.referencedType.elementType.elementType.primitive == SemanticPrimitiveKind.UINT8;
    }

    private void registerMethodSignature(
            StructSymbol owner,
            AstNode methodNode
    ) {
        List<Symbol> params = new ArrayList<>();

        for (int i = 0; i < methodNode.parameters.size(); i++) {
            AstNode param = methodNode.parameters.get(i);

            params.add(Symbol.parameter(
                    param.name,
                    typeResolver.analyzeNormalType(param.type),
                    i
            ));
        }

        String display = owner.qualifiedName() + "." + methodNode.name;

        if (OperatorMethods.isOperatorMethodName(methodNode.name)) {
            Symbol symbol = Symbol.method(
                    owner,
                    methodNode.name,
                    params,
                    typeResolver.analyzeReturnType(methodNode.type),
                    false
            );

            symbolTable.declareMethod(owner, methodNode, symbol);
            return;
        }

        if (params.isEmpty()) {
            symbolTable.error("Method '"
                    + display
                    + "' must have 'ptr<"
                    + owner.qualifiedName()
                    + "> self' as its first parameter"
                    + " (also allowed: 'mview<"
                    + owner.qualifiedName()
                    + "> self', 'view<"
                    + owner.qualifiedName()
                    + "> self')");
        } else {
            Symbol first = params.get(0);

            if (!"self".equals(first.name)) {
                symbolTable.error("Method '"
                        + display
                        + "' must have 'ptr<"
                        + owner.qualifiedName()
                        + "> self' as its first parameter"
                        + " (also allowed: 'mview<"
                        + owner.qualifiedName()
                        + "> self', 'view<"
                        + owner.qualifiedName()
                        + "> self')");
            } else if (!TypeChecker.isSelfTypeForOwner(first.type, owner)) {
                symbolTable.error("Method '"
                        + display
                        + "' has a 'self' parameter of type "
                        + first.type.describe()
                        + "; it must be ptr<"
                        + owner.qualifiedName()
                        + "> (or mview<"
                        + owner.qualifiedName()
                        + "> or view<"
                        + owner.qualifiedName()
                        + ">)");
            }

            for (int i = 1; i < params.size(); i++) {
                if ("self".equals(params.get(i).name)) {
                    symbolTable.error("Only the first parameter of a method may be named 'self'");
                    break;
                }
            }
        }

        Symbol symbol = Symbol.method(
                owner,
                methodNode.name,
                params,
                typeResolver.analyzeReturnType(methodNode.type),
                false
        );

        symbolTable.declareMethod(owner, methodNode, symbol);
    }

    private SemanticFunctionDeclNode analyzeFunctionDecl(AstNode fn) {
        symbolTable.diagnostics().push(fn.span);
        try {
        Symbol symbol = symbolTable.findRegisteredFunction(fn);

        if (symbol == null) {

            List<Symbol> fallbackParams = new ArrayList<>();

            for (int i = 0; i < fn.parameters.size(); i++) {
                AstNode param = fn.parameters.get(i);

                fallbackParams.add(Symbol.parameter(
                        param.name,
                        typeResolver.analyzeNormalType(param.type),
                        i
                ));
            }

            symbol = Symbol.function(
                    currentNamespaceName(),
                    fn.name,
                    fallbackParams,
                    typeResolver.analyzeReturnType(fn.type),
                    false
            );
        }

        Symbol previousFunction = currentFunction;
        currentFunction = symbol;

        symbolTable.pushScope();

        List<Symbol> parameterVariables = new ArrayList<>();

        for (Symbol param : symbol.parameters) {
            symbolTable.declare(param);
            parameterVariables.add(param);
        }

        List<SemanticStmtNode> bodyStatements = new ArrayList<>();

        if (fn.body != null) {
            for (AstNode stmt : fn.body.items) {
                appendAnalyzedStmt(stmt, bodyStatements);
            }
        } else {
            symbolTable.error("Function '" + fn.name + "' is missing a body");
        }

        symbolTable.popScope();

        currentFunction = previousFunction;

        return new SemanticFunctionDeclNode(
                fn.span,
                symbol,
                parameterVariables,
                new SemanticBlockNode(
                        fn.body == null ? fn.span : fn.body.span,
                        bodyStatements
                )
        );
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private SemanticFunctionDeclNode analyzeMethodDecl(
            StructSymbol owner,
            AstNode methodNode
    ) {
        symbolTable.diagnostics().push(methodNode.span);
        try {
        Symbol symbol = symbolTable.findRegisteredMethod(owner, methodNode);

        if (symbol == null) {

            List<Symbol> fallbackParams = new ArrayList<>();

            for (int i = 0; i < methodNode.parameters.size(); i++) {
                AstNode param = methodNode.parameters.get(i);

                fallbackParams.add(Symbol.parameter(
                        param.name,
                        typeResolver.analyzeNormalType(param.type),
                        i
                ));
            }

            symbol = Symbol.method(
                    owner,
                    methodNode.name,
                    fallbackParams,
                    typeResolver.analyzeReturnType(methodNode.type),
                    false
            );
        }

        Symbol previousFunction = currentFunction;
        currentFunction = symbol;

        symbolTable.pushScope();

        List<Symbol> parameterVariables = new ArrayList<>();

        for (Symbol param : symbol.parameters) {
            symbolTable.declare(param);
            parameterVariables.add(param);
        }

        List<SemanticStmtNode> bodyStatements = new ArrayList<>();

        if (methodNode.body != null) {
            for (AstNode stmt : methodNode.body.items) {
                appendAnalyzedStmt(stmt, bodyStatements);
            }
        } else {
            symbolTable.error("Method '"
                    + owner.qualifiedName()
                    + "."
                    + methodNode.name
                    + "' is missing a body");
        }

        symbolTable.popScope();

        currentFunction = previousFunction;

        return new SemanticFunctionDeclNode(
                methodNode.span,
                symbol,
                parameterVariables,
                new SemanticBlockNode(
                        methodNode.body == null
                                ? methodNode.span
                                : methodNode.body.span,
                        bodyStatements
                )
        );
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private void appendAnalyzedStmt(
            AstNode stmt,
            List<SemanticStmtNode> out
    ) {
        if (stmt == null) {
            return;
        }

        if (stmt.kind == AstKind.COMPILE_TIME_IF) {
            appendCompileTimeIf(stmt, out);
            return;
        }

        if (stmt.kind == AstKind.VAR_DECL_INIT) {
            out.addAll(analyzeVarDeclInitStmtList(stmt));
            return;
        }

        SemanticStmtNode lowered = analyzeStmt(stmt);

        if (lowered != null) {
            out.add(lowered);
        }
    }

    private void appendCompileTimeIf(
            AstNode node,
            List<SemanticStmtNode> out
    ) {
        AstNode selected = selectCompileTimeBranch(node);

        if (selected == null) {
            return;
        }

        appendCompileTimeBranch(selected, out);
    }

    private void appendCompileTimeBranch(
            AstNode selected,
            List<SemanticStmtNode> out
    ) {
        if (selected.kind == AstKind.BLOCK) {
            for (AstNode stmt : selected.items) {
                appendAnalyzedStmt(stmt, out);
            }
            return;
        }

        appendAnalyzedStmt(selected, out);
    }

    private AstNode selectCompileTimeBranch(AstNode node) {
        symbolTable.diagnostics().push(node.span);
        try {
            SemanticExprNode condition =
                    exprAnalyzer.analyzeExpr(node.condition);

            if (!(condition.type.kind == SastTypeKind.PRIMITIVE)) {
                symbolTable.error("#if condition must be an integer, got "
                        + condition.type.describe());
                return null;
            }

            if (!condition.isConstant()) {
                symbolTable.error(
                        "#if condition must be a compile-time constant");
                return null;
            }

            return condition.constantValue.signum() != 0
                    ? node.thenBranch
                    : node.elseBranch;
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private SemanticStmtNode analyzeStmt(AstNode node) {
        if (node == null) {
            return null;
        }

        symbolTable.diagnostics().push(node.span);
        try {

        if (node.kind == AstKind.BLOCK) {
            symbolTable.pushScope();

            List<SemanticStmtNode> stmts = new ArrayList<>();

            for (AstNode stmt : node.items) {
                appendAnalyzedStmt(stmt, stmts);
            }

            symbolTable.popScope();

            return new SemanticBlockNode(node.span, stmts);
        }

        if (node.kind == AstKind.VAR_DECL_INIT) {

            symbolTable.pushScope();
            List<SemanticStmtNode> stmts = analyzeVarDeclInitStmtList(node);
            symbolTable.popScope();

            return new SemanticBlockNode(node.span, stmts);
        }

        if (node.kind == AstKind.VAR_DECL) {

            Symbol registered =
                    symbolTable.findRegisteredVarDeclSymbol(node);

            if (registered != null) {
                return new SemanticVarDeclNode(node.span, registered);
            }

            Symbol sym = Symbol.variable(
                    null,
                    node.name,
                    typeResolver.analyzeNormalType(node.type)
            );

            symbolTable.declare(sym);

            return new SemanticVarDeclNode(node.span, sym);
        }

        if (node.kind == AstKind.ASSIGN) {
            SemanticExprNode target =
                    exprAnalyzer.analyzeRequiredLValueExpr(
                            node.target,
                            "Assignment target"
                    );

            if (target.type.kind == SastTypeKind.STRUCT) {
                ExpressionAnalyzer.OperatorAssignmentLowering lowered =
                        exprAnalyzer.tryLowerOperatorAssignment(
                                target,
                                node.text,
                                node.value,
                                node.span
                        );

                if (lowered != null) {
                    if (lowered.call != null) {
                        return new SemanticExprStmtNode(
                                node.span,
                                lowered.call
                        );
                    }

                    if (lowered.fallbackValue != null) {
                        return new SemanticAssignNode(
                                node.span,
                                node.text,
                                target,
                                lowered.fallbackValue
                        );
                    }
                }
            }

            SemanticExprNode value =
                    exprAnalyzer.analyzeExprWithExpectedType(
                            node.value,
                            target.type);

            return new SemanticAssignNode(node.span, node.text, target, value);
        }

        if (node.kind == AstKind.COMPILE_TIME_IF) {
            AstNode selected = selectCompileTimeBranch(node);

            if (selected == null) {
                return null;
            }

            List<SemanticStmtNode> stmts = new ArrayList<>();
            appendCompileTimeBranch(selected, stmts);
            return new SemanticBlockNode(node.span, stmts);
        }

        if (node.kind == AstKind.IF) {
            SemanticExprNode condition =
                    exprAnalyzer.analyzeExpr(node.condition);

            SemanticStmtNode thenBranch =
                    analyzeStmt(node.thenBranch);

            SemanticStmtNode elseBranch =
                    node.elseBranch != null
                            ? analyzeStmt(node.elseBranch)
                            : null;

            return new SemanticIfNode(
                    node.span,
                    condition,
                    thenBranch,
                    elseBranch
            );
        }

        if (node.kind == AstKind.WHILE) {
            SemanticExprNode condition =
                    exprAnalyzer.analyzeExpr(node.condition);

            loopDepth++;

            SemanticStmtNode body =
                    analyzeStmt(node.body);

            List<SemanticStmtNode> update = new ArrayList<>();

            for (AstNode updateStmt : node.updateItems) {
                appendAnalyzedStmt(updateStmt, update);
            }

            loopDepth--;

            return new SemanticWhileNode(node.span, condition, body, update);
        }

        if (node.kind == AstKind.BREAK) {
            return analyzeBreakStmt(node);
        }

        if (node.kind == AstKind.CONTINUE) {
            if (node.level != null) {
                symbolTable.error(
                        "continue does not take a level: `continue N` is not"
                                + " valid; continue always targets the"
                                + " innermost loop");
                return null;
            }

            if (loopDepth == 0) {
                symbolTable.error(
                        "continue statement outside of a loop");
                return null;
            }

            return new SemanticContinueNode(node.span);
        }

        if (node.kind == AstKind.RETURN) {
            return analyzeReturnStmt(node);
        }

        if (node.kind == AstKind.EXPR_STMT) {
            return new SemanticExprStmtNode(
                    node.span,
                    exprAnalyzer.analyzeExpr(node.expr)
            );
        }

        if (node.kind == AstKind.FUNCTION_DECL) {
            symbolTable.error("Function declaration '"
                    + node.name
                    + "' is only valid in the global top-level scope");
            return null;
        }

        if (node.kind == AstKind.ENUM_DECL) {
            symbolTable.error("Enum declaration '"
                    + node.name
                    + "' is only valid at global or namespace scope");
            return null;
        }

        symbolTable.error("Unrecognized statement node: "
                + node.kind.legacyClassName());

        return null;
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private List<SemanticStmtNode> analyzeVarDeclInitStmtList(
            AstNode n
    ) {
        symbolTable.diagnostics().push(n.span);
        try {
        SastType type = typeResolver.analyzeNormalType(n.type);

        Symbol sym = Symbol.variable(
                null,
                n.name,
                type
        );

        symbolTable.declare(sym);

        List<SemanticStmtNode> stmts = new ArrayList<>();
        stmts.add(new SemanticVarDeclNode(n.span, sym));

        if (n.init != null) {
            SemanticExprNode target =
                    new SemanticVarExprNode(n.span, sym.type, sym);

            ExpressionAnalyzer.OperatorAssignmentLowering lowered = null;

            if (target.type.kind == SastTypeKind.STRUCT) {
                lowered = exprAnalyzer.tryLowerOperatorAssignment(
                        target,
                        "=",
                        n.init,
                        n.span
                );
            }

            if (lowered != null) {
                if (lowered.call != null) {
                    stmts.add(new SemanticExprStmtNode(n.span, lowered.call));
                    return stmts;
                }

                if (lowered.fallbackValue != null) {
                    stmts.add(new SemanticAssignNode(
                            n.span,
                            target,
                            lowered.fallbackValue));
                    return stmts;
                }
            }

            SemanticExprNode value =
                    exprAnalyzer.analyzeExprWithExpectedType(n.init, type);

            stmts.add(new SemanticAssignNode(n.span, target, value));
        }

        return stmts;
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private SemanticReturnNode analyzeReturnStmt(AstNode n) {
        SemanticExprNode value =
                n.value != null
                        ? exprAnalyzer.analyzeExprWithExpectedType(
                                n.value,
                                currentFunction == null
                                        ? null
                                        : currentFunction.returnType())
                        : null;

        if (currentFunction == null) {
            symbolTable.error("return statement is only valid inside a function");

            Symbol fallbackFn = Symbol.function(
                    null,
                    "<invalid-return-context>",
                    List.of(),
                    SemanticTypeResolver.VOID_TYPE,
                    false
            );

            return new SemanticReturnNode(
                    n.span,
                    fallbackFn,
                    value
            );
        }

        return new SemanticReturnNode(
                n.span,
                currentFunction,
                value
        );
    }

    private SemanticStmtNode analyzeBreakStmt(AstNode n) {
        if (loopDepth == 0) {
            symbolTable.error("break statement outside of a loop");
            return null;
        }

        if (n.level == null) {
            return new SemanticBreakNode(n.span, 1);
        }

        BigInteger level = null;

        try {
            level = ConstExpressionEvaluator.evaluatePure(n.level);
        } catch (ConstExpressionEvaluator.UnevaluableConstant e) {
            if (e.getDiagnostic() != null) {
                symbolTable.error(e.getDiagnostic());
                return null;
            }

        }

        if (level == null) {
            symbolTable.error(
                    "break level must be a constant integer expression");
            return null;
        }

        if (level.signum() <= 0) {
            symbolTable.error(
                    "break level must be greater than 0, got: " + level);
            return null;
        }

        if (level.compareTo(BigInteger.valueOf(loopDepth)) > 0) {
            symbolTable.error(
                    "break level "
                            + level
                            + " exceeds the enclosing loop depth "
                            + loopDepth);
            return null;
        }

        return new SemanticBreakNode(n.span, level.intValue());
    }

}
