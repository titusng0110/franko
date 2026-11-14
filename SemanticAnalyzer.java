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
    private final TypeChecker typeChecker;

    private Symbol currentFunction = null;

    private int loopDepth = 0;

    private final Map<String, Symbol> hoistedArrayLiteralGlobals =
            new LinkedHashMap<>();

    private final List<SastNode> pendingHoistedArrayLiterals =
            new ArrayList<>();

    private final Map<String, Symbol> hoistedStaticStringGlobals =
            new LinkedHashMap<>();

    private final List<SastNode> pendingHoistedStaticStrings =
            new ArrayList<>();

    private EntryKind entryKind = EntryKind.NONE;
    private int globalMainCount = 0;

    private final TargetInfo target;

    private final DeferredDirectives deferredDirectives =
            new DeferredDirectives();

    public SemanticAnalyzer(TargetInfo target) {
        this.target = target;
        this.constEval = new ConstExpressionEvaluator(symbolTable);

        ExpressionLowering lowering = new ExpressionLowering(
                symbolTable,
                constEval,
                new ArrayLiteralHoister(this),
                new StaticStringHoister(this),
                target,
                deferredDirectives
        );

        this.exprAnalyzer = lowering.analyzer;
        this.typeResolver = lowering.typeResolver;
        this.typeChecker = new TypeChecker(symbolTable.diagnostics());
    }

    public SastNode analyze(AstNode root) {
        symbolTable.clear();
        typeResolver.resetTupleTypes();
        hoistedArrayLiteralGlobals.clear();
        pendingHoistedArrayLiterals.clear();
        hoistedStaticStringGlobals.clear();
        pendingHoistedStaticStrings.clear();
        symbolTable.pushScope();
        entryKind = EntryKind.NONE;
        globalMainCount = 0;

        SastNode result;

        if (root != null && root.kind == AstKind.PROGRAM) {
            root = PreGenericsCompileTimeFolder.fold(root, target);

            DeclarationSelector preSelector =
                    new DeclarationSelector(target, deferredDirectives);
            DeclarationSelector.Selection preSelection =
                    preSelector.selectPreGenerics(root);

            GenericsResolver resolver =
                    new GenericsResolver(symbolTable.diagnostics(), target);
            AstNode expanded = resolver.expand(preSelection.program);
            expanded = PostGenericsCompileTimeFolder.fold(expanded, target);

            DeclarationSelector postSelector =
                    new DeclarationSelector(target, deferredDirectives);
            DeclarationSelector.Selection postSelection =
                    postSelector.selectPostGenerics(expanded);

            AstNode methodExpanded =
                    resolver.expandMethods(postSelection.program);

            symbolTable.diagnostics().addAll(preSelection.diagnostics);
            symbolTable.diagnostics().addAll(postSelection.diagnostics);

            result = analyzeProgram(methodExpanded);
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

        SastNode root = analyze(ast.root);
        SastNode program;

        if (root == null) {
            program = SastNode.program(
                    ast.root == null ? SourceSpan.NONE : ast.root.span,
                    List.of()
            );
        } else if (root.kind == SastKind.PROGRAM) {
            program = root;
        } else {
            program = SastNode.program(root.span, List.of(root));
        }

        SymbolRegistry registry = new SymbolRegistry();
        SastSerializer.registerFromNode(program, registry);
        registry.registerAll(symbolTable.allSymbols());
        deferredDirectives.flush(symbolTable.diagnostics());
        SastDocument document = new SastDocument(
                SastPhase.SEMANTIC,
                entryKind,
                ast.sources,
                registry,
                program
        );

        return new PassResult(document, symbolTable.diagnostics().diagnostics());
    }

    public List<SastNode> analyze(List<AstNode> nodes) {
        symbolTable.clear();
        typeResolver.resetTupleTypes();
        hoistedArrayLiteralGlobals.clear();
        pendingHoistedArrayLiterals.clear();
        hoistedStaticStringGlobals.clear();
        pendingHoistedStaticStrings.clear();
        symbolTable.pushScope();

        List<SastNode> out = new ArrayList<>();

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

    SastNode hoistArrayLiteralGlobal(
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
            return SastNode.varExpr(span, existing.type, existing);
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
        pendingHoistedArrayLiterals.add(SastNode.varDecl(span, sym));

        return SastNode.varExpr(span, sym.type, sym);
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
                            SastNode.varDecl(span, sym));
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

    private SastNode analyzeProgram(AstNode program) {

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
                    + item.kind.jsonName()
                    + "': only struct declarations, global variable declarations, function declarations, and namespace declarations are allowed at global scope. Global scope holds declarations only, because executable statements have no enclosing function or runtime order. Move the item into a function body or replace it with a declaration.");
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

        Map<AstNode, SastNode> loweredByNode = new IdentityHashMap<>();

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
                    StructSymbol struct =
                            new StructSymbol(currentNamespaceName(), item.name);
                    struct.genericHash = item.genericHash;
                    struct.genericBaseName = item.genericBaseName;
                    symbolTable.declareStruct(struct);
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
                                + "'. Struct fields must have unique names so member access is unambiguous. Rename or remove the duplicate field.");
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
                        type,
                        item.isConst
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
            resolveParameterDefaults(space);
        }

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (AstNode item : space.items) {
                if (item.kind == AstKind.ENUM_DECL) {
                    EnumSymbol enumSymbol = symbolTable.resolveEnum(item.name);

                    if (enumSymbol != null) {
                        loweredByNode.put(
                                item,
                                SastNode.enumDecl(item.span, enumSymbol)
                        );
                    }

                    continue;
                }

                if (item.kind == AstKind.STRUCT_DECL) {
                    StructSymbol structSymbol = symbolTable.resolveStruct(item.name);

                    if (structSymbol != null) {
                        List<SastNode> loweredMethods =
                                new ArrayList<>();

                        for (AstNode methodNode : item.methods) {
                            SastNode loweredMethod =
                                    analyzeMethodDecl(structSymbol, methodNode);

                            if (loweredMethod != null) {
                                loweredMethods.add(loweredMethod);
                            }
                        }

                        loweredByNode.put(
                                item,
                                SastNode.structDecl(
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

                        SastNode loweredStaticInit = staticInit == null
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

                        loweredByNode.put(item, SastNode.varDecl(
                                item.span,
                                sym,
                                globalInitializers.get(item),
                                loweredStaticInit
                        ));
                    }

                    continue;
                }

                if (item.kind == AstKind.FUNCTION_DECL) {
                    SastNode lowered = analyzeFunctionDecl(item);

                    if (lowered != null) {
                        loweredByNode.put(item, lowered);
                    }
                }
            }
        }

        symbolTable.setCurrentNamespace(null);

        List<SastNode> semanticItems = new ArrayList<>();
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

            SastNode lowered = loweredByNode.get(item);

            if (lowered != null) {
                semanticItems.add(lowered);
            }
        }

        for (NamespaceProgramItems space : spaces) {
            if (space.space == null) {
                continue;
            }

            if (emittedNamespaces.add(space.space.name)) {
                semanticItems.add(SastNode.namespace(
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

        List<SastNode> pendingTuples =
                typeResolver.pendingTupleDecls();

        for (int i = pendingTuples.size() - 1; i >= 0; i--) {
            semanticItems.add(0, pendingTuples.get(i));
        }

        symbolTable.warnStructNameClashes();

        if (globalMainCount == 0) {
            symbolTable.error(
                    "No entry-point function 'main' declared: declare either 'func main() -> int32' or 'func main(view<[[byte]]> <name>) -> int32'. Every Franko program starts execution at a global main with one of these two supported signatures. Add a matching 'func main' at global scope with no optional parameters or overloads."
            );
        }

        return SastNode.program(program.span, semanticItems);
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
                    + "' at the top level instead. Franko derives nested namespace paths from declaration placement, so a path header inside a namespace would produce a name that depends on both the header and its enclosing scope. Move the declaration to global scope and use the full path shown above.");
            return;
        } else {
            fullPath = parentPath + "::" + node.name;
        }

        NamespaceSymbol space = symbolTable.declareNamespace(fullPath);

        NamespaceProgramItems spaceItems = spacesByName.get(fullPath);

        if (spaceItems == null) {
            spaceItems = new NamespaceProgramItems(space, node.span);
            spacesByName.put(fullPath, spaceItems);
            spaces.add(spaceItems);
        }

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
            Map<AstNode, SastNode> loweredByNode,
            List<SastNode> semanticItems,
            Set<String> emittedNamespaces
    ) {
        NamespaceProgramItems space = spacesByName.get(path);

        if (space != null && emittedNamespaces.add(path)) {
            semanticItems.add(SastNode.namespace(
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
                semanticItems.add(SastNode.namespace(
                        nested.span,
                        nested.space.name,
                        loweredItemsOf(nested, loweredByNode)
                ));
            }
        }
    }

    private List<SastNode> loweredItemsOf(
            NamespaceProgramItems space,
            Map<AstNode, SastNode> loweredByNode
    ) {
        List<SastNode> out = new ArrayList<>();

        for (AstNode item : space.items) {
            SastNode lowered = loweredByNode.get(item);

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
                    + "'. The runtime entry point is resolved by its unqualified global name, so a namespaced main would never be called. Move main out of the namespace and declare it at global scope.");
        }

        List<Symbol> params = new ArrayList<>();

        for (int i = 0; i < fn.parameters.size(); i++) {
            AstNode param = fn.parameters.get(i);

            params.add(Symbol.parameter(
                    param.name,
                    typeResolver.analyzeNormalType(param.type),
                    i,
                    param.isConst
            ));
        }

        for (Symbol param : params) {
            if ("self".equals(param.name)) {
                symbolTable.error("'self' is only valid as the first parameter of a struct method. The 'self' name is the receiver that binds a method call to its struct instance, and the receiver slot is always parameter zero. Remove the 'self' parameter here or move this declaration into a struct method.");
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

        symbol.inlineMode = fn.inlineMode;
        symbol.genericGroup = fn.genericGroup;
        symbol.genericHash = fn.genericHash;

        if ("main".equals(fn.name) && currentNamespaceName() == null) {
            boolean returnsInt32 = symbol.returnType().kind == SastTypeKind.PRIMITIVE
                    && symbol.returnType().primitive == SemanticPrimitiveKind.INT32;

            boolean noArgs = params.isEmpty();
            boolean hasArgsShape = params.size() == 1
                    && isArgsArrayParamType(params.get(0).type);
            boolean hasOptional = false;

            for (AstNode param : fn.parameters) {
                if (param.defaultValue != null) {
                    hasOptional = true;
                    break;
                }
            }

            globalMainCount++;

            if (hasOptional) {
                symbolTable.error("The entry-point function 'main' cannot"
                        + " declare optional parameters. The runtime invokes main with a fixed argument shape and cannot supply defaults. Remove the default value from main's parameters or move the logic into a helper function.");
            } else if (globalMainCount > 1) {
                symbolTable.error(
                        "The entry-point function 'main' cannot be overloaded: declare either 'func main() -> int32' or 'func main(view<[[byte]]> <name>) -> int32', not both. A program needs one unambiguous entry point for the runtime to call. Keep exactly one global main declaration and rename or remove the other overload."
                );
            } else if (!returnsInt32 || (!noArgs && !hasArgsShape)) {
                symbolTable.error(
                        "The entry-point function 'main' must be declared as 'func main() -> int32' or 'func main(view<[[byte]]> <name>) -> int32'. The runtime only knows how to pass arguments to these two main shapes and expects an int32 exit code. Change main's parameters and return type to match one of these signatures."
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
                    i,
                    param.isConst
                            || (i == 0 && "self".equals(param.name))
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

            symbol.inlineMode = methodNode.inlineMode;
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
                    + "> self'). Every Franko method is bound to an explicit receiver so calls know which struct instance to operate on. Add the receiver as the first parameter, choosing ptr, mview, or view as appropriate.");
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
                        + "> self'). Every Franko method is bound to an explicit receiver so calls know which struct instance to operate on. Add the receiver as the first parameter, choosing ptr, mview, or view as appropriate.");
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
                        + ">). A method receiver must point to the struct that owns the method so field access and permission checks use the right type. Change the self parameter to ptr<"
                        + owner.qualifiedName()
                        + ">, mview<"
                        + owner.qualifiedName()
                        + ">, or view<"
                        + owner.qualifiedName()
                        + "> for the owning struct.");
            }

            for (int i = 1; i < params.size(); i++) {
                if ("self".equals(params.get(i).name)) {
                    symbolTable.error("Only the first parameter of a method may be named 'self'. The 'self' name is reserved for the receiver slot that binds the call to its struct instance. Rename this parameter or move it to the first position as the receiver.");
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

        symbol.inlineMode = methodNode.inlineMode;
        symbol.genericGroup = methodNode.genericGroup;
        symbol.genericHash = methodNode.genericHash;
        symbolTable.declareMethod(owner, methodNode, symbol);
    }

    private void resolveParameterDefaults(NamespaceProgramItems space) {
        for (AstNode item : space.items) {
            if (item.kind == AstKind.FUNCTION_DECL) {
                Symbol symbol = symbolTable.findRegisteredFunction(item);
                resolveParameterDefaults(item, symbol);
                continue;
            }

            if (item.kind != AstKind.STRUCT_DECL) {
                continue;
            }

            StructSymbol structSymbol = symbolTable.resolveStruct(item.name);

            if (structSymbol == null) {
                continue;
            }

            for (AstNode methodNode : item.methods) {
                Symbol methodSymbol = symbolTable.findRegisteredMethod(
                        structSymbol,
                        methodNode
                );

                resolveParameterDefaults(methodNode, methodSymbol);
            }
        }
    }

    private void resolveParameterDefaults(
            AstNode declaration,
            Symbol functionSymbol
    ) {
        if (functionSymbol == null) {
            return;
        }

        symbolTable.pushScope();

        try {
            for (Symbol parameter : functionSymbol.parameters) {
                symbolTable.declare(parameter);
            }

            for (int i = 0; i < declaration.parameters.size(); i++) {
                AstNode parameterAst = declaration.parameters.get(i);

                if (parameterAst.defaultValue == null
                        || i >= functionSymbol.parameters.size()) {
                    continue;
                }

                Symbol parameter = functionSymbol.parameters.get(i);
                symbolTable.diagnostics().push(parameterAst.defaultValue.span);

                try {
                    SastNode value = exprAnalyzer.analyzeExprWithExpectedType(
                            parameterAst.defaultValue,
                            parameter.type
                    );

                    parameter.parameterDefault = canonicalizeStaticDefault(
                            value,
                            parameter,
                            functionSymbol,
                            parameterAst.defaultValue.span
                    );
                } finally {
                    symbolTable.diagnostics().pop();
                }
            }
        } finally {
            symbolTable.popScope();
        }
    }

    private SastNode canonicalizeStaticDefault(
            SastNode value,
            Symbol parameter,
            Symbol function,
            SourceSpan span
    ) {
        String where = "Default value for parameter '"
                + parameter.name
                + "' of function '"
                + function.qualifiedName()
                + "'";

        if (value != null
                && value.kind == SastKind.AGGREGATE_LITERAL
                && (value.type == null
                    || value.type.kind != SastTypeKind.STRUCT
                    || !typeChecker.isValueAssignableType(value.type))) {
            symbolTable.error("Default aggregate literal for parameter '"
                    + parameter.name
                    + "' must be a value-assignable struct literal. Parameter defaults are stored as static compile-time values, so the literal's struct cannot contain owning pointers or other non-copyable state. Use a literal of a value-assignable struct or pass the value explicitly at the call site.");
            return fallbackStaticDefault(parameter, span);
        }

        SastNode canonical = canonicalStaticValue(
                value,
                parameter.type,
                where,
                span
        );

        if (canonical == null) {
            return fallbackStaticDefault(parameter, span);
        }

        if (!isAssignableStaticDefault(parameter.type, canonical)) {
            symbolTable.error(where
                    + " has type '"
                    + canonical.type.describe()
                    + "', expected '"
                    + parameter.type.describe()
                    + "'. A static default must be assignable to the declared parameter or field type so it can be substituted at each use. Change the default expression to produce '"
                    + parameter.type.describe()
                    + "'.");

            return fallbackStaticDefault(parameter, span);
        }

        return canonical;
    }

    private SastNode canonicalStaticValue(
            SastNode value,
            SastType expectedType,
            String where,
            SourceSpan span
    ) {
        if (value == null) {
            symbolTable.error(where + " must be a compile-time value. Franko stores static defaults in the compiled declaration and cannot evaluate runtime expressions there. Replace it with a literal, named constant, enum variant, or constant expression.");
            return null;
        }

        if (value.isConstant()
                && value.type != null
                && value.type.kind == SastTypeKind.PRIMITIVE
                && (value.kind == SastKind.INT_LITERAL
                    || value.kind == SastKind.UNARY_OP
                    || value.kind == SastKind.BIN_OP)) {
            if (expectedType.kind == SastTypeKind.PRIMITIVE) {
                if (!TypeChecker.fitsBigIntegerToPrimitive(
                        value.constantValue,
                        expectedType.primitive)) {
                    symbolTable.error(where
                            + " does not fit type '"
                            + expectedType.describe()
                            + "'. The constant must be representable in the target's storage width and signedness. Use a value within that range or widen the declared type.");
                    return null;
                }

                return SastNode.intLiteral(
                        value.span,
                        expectedType,
                        value.constantValue,
                        value.rawValue == null
                                ? value.constantValue.toString(10)
                                : value.rawValue
                );
            }

            return SastNode.intLiteral(
                    value.span,
                    value.type,
                    value.constantValue,
                    value.rawValue == null
                            ? value.constantValue.toString(10)
                            : value.rawValue
            );
        }

        if (value.kind == SastKind.NULL_LITERAL) {
            return SastNode.nullLiteral(value.span, value.type);
        }

        if (value.kind == SastKind.ENUM_VARIANT) {
            SastNode copy = SastNode.enumVariant(
                    value.span,
                    value.enumSymbol,
                    value.variant
            );
            copy.type = value.type;
            return copy;
        }

        if (value.kind == SastKind.VAR_EXPR) {
            if (value.symbol != null
                    && (value.symbol.literalBytes != null
                        || value.symbol.literalValues != null)) {
                return SastNode.varExpr(value.span, value.type, value.symbol);
            }

            symbolTable.error(where + " must be a compile-time value. Franko stores static defaults in the compiled declaration and cannot evaluate runtime expressions there. Replace it with a literal, named constant, enum variant, or constant expression.");
            return null;
        }

        if (value.kind == SastKind.GET_PTR) {
            if (value.target != null
                    && value.target.kind == SastKind.VAR_EXPR
                    && value.target.symbol != null
                    && (value.target.symbol.literalBytes != null
                        || value.target.symbol.literalValues != null)) {
                return SastNode.getPtr(
                        value.span,
                        value.type,
                        SastNode.varExpr(
                                value.target.span,
                                value.target.type,
                                value.target.symbol
                        ),
                        value.temporary
                );
            }

            symbolTable.error(where + " must be a compile-time value. Franko stores static defaults in the compiled declaration and cannot evaluate runtime expressions there. Replace it with a literal, named constant, enum variant, or constant expression.");
            return null;
        }

        if (value.kind == SastKind.AGGREGATE_LITERAL) {
            if (value.type == null
                    || value.type.kind != SastTypeKind.STRUCT
                    || !typeChecker.isValueAssignableType(value.type)) {
                symbolTable.error(where
                        + " must be a value-assignable struct literal. Static defaults are stored by value, so the literal's struct cannot contain owning pointers or other non-copyable fields. Use a literal of a value-assignable struct or initialize the value at runtime.");
                return null;
            }

            List<SastNode> fields = new ArrayList<>();

            for (SastNode fieldInit : value.fields) {
                if (fieldInit.kind != SastKind.AGGREGATE_FIELD_INIT
                        || fieldInit.field == null) {
                    symbolTable.error(where + " must be a compile-time value. Franko stores static defaults in the compiled declaration and cannot evaluate runtime expressions there. Replace it with a literal, named constant, enum variant, or constant expression.");
                    return null;
                }

                SastNode fieldValue = canonicalStaticValue(
                        fieldInit.value,
                        fieldInit.field.type,
                        "Default aggregate literal field '"
                                + fieldInit.field.name
                                + "'",
                        span
                );

                if (fieldValue == null) {
                    return null;
                }

                fields.add(SastNode.aggregateFieldInit(
                        fieldInit.field,
                        fieldValue
                ));
            }

            return SastNode.aggregateLiteral(
                    value.span,
                    value.type,
                    fields
            );
        }

        symbolTable.error(where + " must be a compile-time value. Franko stores static defaults in the compiled declaration and cannot evaluate runtime expressions there. Replace it with a literal, named constant, enum variant, or constant expression.");
        return null;
    }

    private boolean isAssignableStaticDefault(
            SastType targetType,
            SastNode value
    ) {
        if (targetType == null || value == null || value.type == null) {
            return false;
        }

        if (value.type.kind == SastTypeKind.NULL) {
            return targetType.kind == SastTypeKind.PTR;
        }

        if (TypeChecker.sameType(targetType, value.type)) {
            return true;
        }

        if (value.isConstant()
                && targetType.kind == SastTypeKind.PRIMITIVE) {
            return TypeChecker.fitsBigIntegerToPrimitive(
                    value.constantValue,
                    targetType.primitive
            );
        }

        return PointerPermissions.isPointerConversion(
                value.type,
                targetType
        );
    }

    private SastNode fallbackStaticDefault(
            Symbol parameter,
            SourceSpan span
    ) {
        SastType type = parameter.type;

        if (type.kind == SastTypeKind.PTR) {
            return SastNode.nullLiteral(span, SastType.nullType());
        }

        if (type.kind == SastTypeKind.ENUM
                && type.enumSymbol != null
                && !type.enumSymbol.variants.isEmpty()) {
            return SastNode.enumVariant(
                    span,
                    type.enumSymbol,
                    type.enumSymbol.variants.get(0)
            );
        }

        if (type.kind == SastTypeKind.STRUCT) {
            return SastNode.aggregateLiteral(span, type, List.of());
        }

        if (type.kind == SastTypeKind.PRIMITIVE) {
            return SastNode.intLiteral(
                    span,
                    type,
                    BigInteger.ZERO,
                    "0"
            );
        }

        return SastNode.intLiteral(
                span,
                SastType.primitive(SemanticPrimitiveKind.INT32),
                BigInteger.ZERO,
                "0"
        );
    }

    private SastNode analyzeFunctionDecl(AstNode fn) {
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
                        i,
                        param.isConst
                ));
            }

            symbol = Symbol.function(
                    currentNamespaceName(),
                    fn.name,
                    fallbackParams,
                    typeResolver.analyzeReturnType(fn.type),
                    false
            );

            symbol.inlineMode = fn.inlineMode;
            symbol.genericGroup = fn.genericGroup;
            symbol.genericHash = fn.genericHash;
        }

        Symbol savedFunction = currentFunction;
        currentFunction = symbol;

        symbolTable.pushScope();

        List<Symbol> parameterVariables = new ArrayList<>();

        for (Symbol param : symbol.parameters) {
            symbolTable.declare(param);
            parameterVariables.add(param);
        }

        List<SastNode> bodyStatements = new ArrayList<>();

        if (fn.body != null) {
            for (AstNode stmt : fn.body.items) {
                appendAnalyzedStmt(stmt, bodyStatements);
            }
        } else {
            symbolTable.error("Function '" + fn.name + "' is missing a body. Every Franko function needs a body so calls have an implementation to link and execute. Add a function body or remove the declaration.");
        }

        symbolTable.popScope();

        currentFunction = savedFunction;

        return SastNode.functionDecl(
                fn.span,
                symbol,
                parameterVariables,
                SastNode.block(
                        fn.body == null ? fn.span : fn.body.span,
                        bodyStatements
                )
        );
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private SastNode analyzeMethodDecl(
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
                        i,
                        param.isConst
                                || (i == 0 && "self".equals(param.name))
                ));
            }

            symbol = Symbol.method(
                    owner,
                    methodNode.name,
                    fallbackParams,
                    typeResolver.analyzeReturnType(methodNode.type),
                    false
            );

            symbol.inlineMode = methodNode.inlineMode;
            symbol.genericGroup = methodNode.genericGroup;
            symbol.genericHash = methodNode.genericHash;
        }

        Symbol savedFunction = currentFunction;
        currentFunction = symbol;

        symbolTable.pushScope();

        List<Symbol> parameterVariables = new ArrayList<>();

        for (Symbol param : symbol.parameters) {
            symbolTable.declare(param);
            parameterVariables.add(param);
        }

        List<SastNode> bodyStatements = new ArrayList<>();

        if (methodNode.body != null) {
            for (AstNode stmt : methodNode.body.items) {
                appendAnalyzedStmt(stmt, bodyStatements);
            }
        } else {
            symbolTable.error("Method '"
                    + owner.qualifiedName()
                    + "."
                    + methodNode.name
                    + "' is missing a body. Every Franko method needs a body so calls have an implementation to link and execute. Add a method body or remove the declaration.");
        }

        symbolTable.popScope();

        currentFunction = savedFunction;

        return SastNode.functionDecl(
                methodNode.span,
                symbol,
                parameterVariables,
                SastNode.block(
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
            List<SastNode> out
    ) {
        if (stmt == null) {
            return;
        }

        if (stmt.kind == AstKind.COMPILE_TIME_IF) {
            appendCompileTimeIf(stmt, out);
            return;
        }

        if (stmt.kind == AstKind.VAR_DECL_INIT) {
            out.add(analyzeVarDeclInitStmt(stmt));
            return;
        }

        if (stmt.kind == AstKind.VAR_DECL_FIELDS_INIT) {
            out.add(analyzeVarDeclFieldsInitStmt(stmt));
            return;
        }

        if (isUserCompileTimeDirective(stmt)) {
            exprAnalyzer.analyzeCompileTimeDirective(stmt.expr);
            return;
        }

        SastNode lowered = analyzeStmt(stmt);

        if (lowered != null) {
            out.add(lowered);
        }
    }

    private static boolean isUserCompileTimeDirective(AstNode stmt) {
        if (stmt.kind != AstKind.EXPR_STMT || stmt.expr == null) {
            return false;
        }

        if (stmt.expr.kind != AstKind.COMPILE_TIME_CALL) {
            return false;
        }

        return "panic".equals(stmt.expr.name)
                || "warning".equals(stmt.expr.name);
    }

    private void appendCompileTimeIf(
            AstNode node,
            List<SastNode> out
    ) {
        AstNode selected = selectCompileTimeBranch(node);

        if (selected == null) {
            return;
        }

        appendCompileTimeBranch(selected, out);
    }

    private void appendCompileTimeBranch(
            AstNode selected,
            List<SastNode> out
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
            SastNode condition =
                    exprAnalyzer.analyzeExpr(node.condition);

            if (!(condition.type.kind == SastTypeKind.PRIMITIVE)) {
                symbolTable.error("#if condition must be an integer, got "
                        + condition.type.describe()
                        + ". The #if directive selects a branch at compile time by testing an integer for zero or nonzero. Use an integer constant expression as the condition.");
                return null;
            }

            if (!condition.isConstant()) {
                symbolTable.error(
                        "#if condition must be a compile-time constant. The #if directive chooses which branch to compile before semantic analysis, so it cannot depend on runtime values. Use literals, named constants, or other constant expressions in the condition.");
                return null;
            }

            return condition.constantValue.signum() != 0
                    ? node.thenBranch
                    : node.elseBranch;
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private SastNode analyzeStmt(AstNode node) {
        if (node == null) {
            return null;
        }

        symbolTable.diagnostics().push(node.span);
        try {

        if (node.kind == AstKind.BLOCK) {
            symbolTable.pushScope();

            List<SastNode> stmts = new ArrayList<>();

            for (AstNode stmt : node.items) {
                appendAnalyzedStmt(stmt, stmts);
            }

            symbolTable.popScope();

            return SastNode.block(node.span, stmts);
        }

        if (node.kind == AstKind.VAR_DECL_INIT) {
            symbolTable.pushScope();
            SastNode lowered = analyzeVarDeclInitStmt(node);
            symbolTable.popScope();

            return lowered;
        }

        if (node.kind == AstKind.VAR_DECL_FIELDS_INIT) {
            symbolTable.pushScope();
            SastNode lowered = analyzeVarDeclFieldsInitStmt(node);
            symbolTable.popScope();

            return lowered;
        }

        if (node.kind == AstKind.VAR_DECL) {

            Symbol registered =
                    symbolTable.findRegisteredVarDeclSymbol(node);

            if (registered != null) {
                return SastNode.varDecl(node.span, registered);
            }

            Symbol sym = Symbol.variable(
                    null,
                    node.name,
                    typeResolver.analyzeNormalType(node.type),
                    node.isConst
            );

            symbolTable.declare(sym);

            return SastNode.varDecl(node.span, sym);
        }

        if (node.kind == AstKind.ASSIGN) {
            SastNode target =
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
                        return SastNode.exprStmt(
                                node.span,
                                lowered.call
                        );
                    }

                    if (lowered.fallbackValue != null) {
                        return SastNode.assign(
                                node.span,
                                node.text,
                                target,
                                lowered.fallbackValue
                        );
                    }
                }
            }

            SastNode value =
                    exprAnalyzer.analyzeExprWithExpectedType(
                            node.value,
                            target.type);

            return SastNode.assign(node.span, node.text, target, value);
        }

        if (node.kind == AstKind.COMPILE_TIME_IF) {
            AstNode selected = selectCompileTimeBranch(node);

            if (selected == null) {
                return null;
            }

            List<SastNode> stmts = new ArrayList<>();
            appendCompileTimeBranch(selected, stmts);
            return SastNode.block(node.span, stmts);
        }

        if (node.kind == AstKind.IF) {
            SastNode condition =
                    exprAnalyzer.analyzeExpr(node.condition);

            SastNode thenBranch =
                    analyzeStmt(node.thenBranch);

            SastNode elseBranch =
                    node.elseBranch != null
                            ? analyzeStmt(node.elseBranch)
                            : null;

            return SastNode.ifStmt(
                    node.span,
                    condition,
                    thenBranch,
                    elseBranch
            );
        }

        if (node.kind == AstKind.WHILE) {
            SastNode condition =
                    exprAnalyzer.analyzeExpr(node.condition);

            loopDepth++;

            SastNode body =
                    analyzeStmt(node.body);

            List<SastNode> update = new ArrayList<>();

            for (AstNode updateStmt : node.updateItems) {
                appendAnalyzedStmt(updateStmt, update);
            }

            loopDepth--;

            return SastNode.whileStmt(node.span, condition, body, update);
        }

        if (node.kind == AstKind.BREAK) {
            return analyzeBreakStmt(node);
        }

        if (node.kind == AstKind.CONTINUE) {
            if (node.level != null) {
                symbolTable.error(
                        "continue does not take a level: `continue N` is not"
                                + " valid; continue always targets the"
                                + " innermost loop. Loop levels are meaningful only for break, which can exit multiple enclosing loops. Replace `continue N` with plain `continue`.");
                return null;
            }

            if (loopDepth == 0) {
                symbolTable.error(
                        "continue statement outside of a loop. The continue statement jumps to the next iteration of an enclosing loop, and there is no loop to resume here. Move the continue into a loop body or remove it.");
                return null;
            }

            return SastNode.continueStmt(node.span);
        }

        if (node.kind == AstKind.RETURN) {
            return analyzeReturnStmt(node);
        }

        if (node.kind == AstKind.EXPR_STMT) {
            return SastNode.exprStmt(
                    node.span,
                    exprAnalyzer.analyzeExpr(node.expr)
            );
        }

        if (node.kind == AstKind.FUNCTION_DECL) {
            symbolTable.error("Function declaration '"
                    + node.name
                    + "' is only valid in the global top-level scope. Franko functions are top-level declarations and cannot be nested inside blocks or other functions. Move the function declaration to global or namespace scope.");
            return null;
        }

        if (node.kind == AstKind.ENUM_DECL) {
            symbolTable.error("Enum declaration '"
                    + node.name
                    + "' is only valid at global or namespace scope. Enum declarations define named types and cannot be local to a function or block. Move the enum declaration to global or namespace scope.");
            return null;
        }

        symbolTable.error("Unrecognized statement node: "
                + node.kind.jsonName());

        return null;
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private SastNode analyzeVarDeclInitStmt(
            AstNode n
    ) {
        symbolTable.diagnostics().push(n.span);
        try {
        SastType type = typeResolver.analyzeNormalType(n.type);

        Symbol sym = Symbol.variable(
                null,
                n.name,
                type,
                n.isConst
        );

        symbolTable.declare(sym);

        SastNode target =
                SastNode.varExpr(n.span, sym.type, sym);

        List<SastNode> initializers = new ArrayList<>();

        if (n.init != null) {
            ExpressionAnalyzer.OperatorAssignmentLowering lowered = null;

            if (target.type.kind == SastTypeKind.STRUCT && !n.isConst) {
                lowered = exprAnalyzer.tryLowerOperatorAssignment(
                        target,
                        "=",
                        n.init,
                        n.span
                );
            }

            if (lowered != null) {
                if (lowered.call != null) {
                    initializers.add(SastNode.exprStmt(n.span, lowered.call));
                    return SastNode.varDeclInit(n.span, sym, initializers);
                }

                if (lowered.fallbackValue != null) {
                    initializers.add(SastNode.assign(
                            n.span,
                            target,
                            lowered.fallbackValue));
                    return SastNode.varDeclInit(n.span, sym, initializers);
                }
            }

            SastNode value =
                    exprAnalyzer.analyzeExprWithExpectedType(n.init, type);

            initializers.add(SastNode.assign(n.span, target, value));
        }

        return SastNode.varDeclInit(n.span, sym, initializers);
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private SastNode analyzeVarDeclFieldsInitStmt(
            AstNode n
    ) {
        symbolTable.diagnostics().push(n.span);
        try {
        SastType type = typeResolver.analyzeNormalType(n.type);

        Symbol sym = Symbol.variable(
                null,
                n.name,
                type,
                n.isConst
        );

        symbolTable.declare(sym);

        List<SastNode> initializers = new ArrayList<>();
        int position = 0;

        for (AstNode entry : n.fields) {
            String fieldName;

            if (entry.positional) {
                fieldName = "_" + position;
                position++;
            } else {
                fieldName = entry.name;
            }

            AstNode rawTarget = AstNode.memberAccess(
                    entry.span,
                    AstNode.var(n.span, n.name),
                    fieldName
            );

            SastNode target = exprAnalyzer.analyzeRequiredLValueExpr(
                    rawTarget,
                    "Field initializer target"
            );

            SastNode value = exprAnalyzer.analyzeExprWithExpectedType(
                    entry.value,
                    target.type
            );

            initializers.add(SastNode.assign(
                    entry.span,
                    target,
                    value
            ));
        }

        return SastNode.varDeclInit(n.span, sym, initializers);
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private SastNode analyzeReturnStmt(AstNode n) {
        SastNode value =
                n.value != null
                        ? exprAnalyzer.analyzeExprWithExpectedType(
                                n.value,
                                currentFunction == null
                                        ? null
                                        : currentFunction.returnType())
                        : null;

        if (currentFunction == null) {
            symbolTable.error("return statement is only valid inside a function. A return needs a function signature to supply its result type and a call frame to exit. Move the return into a function body or remove it.");

            Symbol fallbackFn = Symbol.function(
                    null,
                    "<invalid-return-context>",
                    List.of(),
                    SemanticTypeResolver.VOID_TYPE,
                    false
            );

            return SastNode.returnStmt(
                    n.span,
                    fallbackFn,
                    value
            );
        }

        return SastNode.returnStmt(
                n.span,
                currentFunction,
                value
        );
    }

    private SastNode analyzeBreakStmt(AstNode n) {
        if (loopDepth == 0) {
            symbolTable.error("break statement outside of a loop. The break statement exits the innermost enclosing loop, and there is no loop to exit here. Move the break into a loop body or remove it.");
            return null;
        }

        if (n.level == null) {
            return SastNode.breakStmt(n.span, 1);
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
                    "break level must be a constant integer expression. The compiler must resolve the target loop statically to validate the level against the nesting depth. Use an integer literal or named constant for the break level.");
            return null;
        }

        if (level.signum() <= 0) {
            symbolTable.error(
                    "break level must be greater than 0, got: " + level + ". A level of 1 targets the innermost loop, and larger levels count outward from there. Use a positive level no greater than the enclosing loop depth.");
            return null;
        }

        if (level.compareTo(BigInteger.valueOf(loopDepth)) > 0) {
            symbolTable.error(
                    "break level "
                            + level
                            + " exceeds the enclosing loop depth "
                            + loopDepth
                            + ". A break level counts outward from the innermost loop, so it cannot be larger than the number of enclosing loops. Reduce the level to "
                            + loopDepth
                            + " or less, or add another enclosing loop.");
            return null;
        }

        return SastNode.breakStmt(n.span, level.intValue());
    }

}
