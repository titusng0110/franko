import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SemanticAnalyzer {

    private final SymbolTable symbolTable = new SymbolTable();

    private final ConstExpressionEvaluator constEval;
    private final ExpressionAnalyzer exprAnalyzer;
    private final SemanticTypeResolver typeResolver;

    private FunctionSymbol currentFunction = null;

    private int loopDepth = 0;

    private final Map<String, GlobalVariableSymbol> hoistedArrayLiteralGlobals =
            new LinkedHashMap<>();

    private final List<SemanticVarDeclNode> pendingHoistedArrayLiterals =
            new ArrayList<>();

    private EntryKind entryKind = EntryKind.NONE;
    private int globalMainCount = 0;

    public SemanticAnalyzer() {
        this.constEval = new ConstExpressionEvaluator(symbolTable);

        ExpressionLowering lowering = new ExpressionLowering(
                symbolTable,
                constEval,
                new ArrayLiteralLowerer.Hoister() {
                    @Override
                    public SemanticVarExprNode hoistArrayLiteral(
                            SemanticStaticArrayType arrayType,
                            List<BigInteger> values,
                            SourceSpan span) {
                        return hoistArrayLiteralGlobal(arrayType, values, span);
                    }
                }
        );

        this.exprAnalyzer = lowering.analyzer;
        this.typeResolver = lowering.typeResolver;
    }

    public SemanticASTNode analyze(ASTNode root) {
        symbolTable.clear();
        hoistedArrayLiteralGlobals.clear();
        pendingHoistedArrayLiterals.clear();
        symbolTable.pushScope();
        entryKind = EntryKind.NONE;
        globalMainCount = 0;

        SemanticASTNode result;

        if (root instanceof ProgramNode p) {
            result = analyzeProgram(p);
        } else if (root instanceof FunctionDeclNode fn) {

            registerFunctionSignature(fn);
            result = analyzeFunctionDecl(fn);
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

    public List<SemanticStmtNode> analyze(List<ASTNode> nodes) {
        symbolTable.clear();
        hoistedArrayLiteralGlobals.clear();
        pendingHoistedArrayLiterals.clear();
        symbolTable.pushScope();

        List<SemanticStmtNode> out = new ArrayList<>();

        for (ASTNode stmt : nodes) {
            appendAnalyzedStmt(stmt, out);
        }

        symbolTable.popScope();

        return out;
    }

    private static final class NamespaceProgramItems {
        final NamespaceSymbol space;
        final SourceSpan span;
        final List<ASTNode> items = new ArrayList<>();

        NamespaceProgramItems(NamespaceSymbol space, SourceSpan span) {
            this.space = space;
            this.span = span;
        }
    }

    SemanticVarExprNode hoistArrayLiteralGlobal(
            SemanticStaticArrayType arrayType,
            List<BigInteger> values,
            SourceSpan span
    ) {
        StringBuilder key = new StringBuilder(arrayType.mangleKey());
        key.append('|');

        for (BigInteger value : values) {
            key.append(value.toString(10)).append(',');
        }

        String keyText = key.toString();

        GlobalVariableSymbol existing =
                hoistedArrayLiteralGlobals.get(keyText);

        if (existing != null) {
            return new SemanticVarExprNode(span, existing.type, existing);
        }

        String baseName = "_ARR_" + Mangler.hashHex(keyText);
        String name = baseName;
        int suffix = 0;

        GlobalVariableSymbol sym;

        while (true) {
            GlobalVariableSymbol candidate =
                    new GlobalVariableSymbol(null, name, arrayType);

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

    private SemanticProgramNode analyzeProgram(ProgramNode program) {

        for (ASTNode item : program.topLevelItems) {
            symbolTable.diagnostics().push(item.span);
            try {
            if (item instanceof StructDeclNode
                    || item instanceof GlobalVarDeclNode
                    || item instanceof FunctionDeclNode
                    || item instanceof NamespaceNode) {
                continue;
            }

            symbolTable.error("Invalid top-level item '"
                    + item.getClass().getSimpleName()
                    + "': only struct declarations, global variable declarations, function declarations, and namespace declarations are allowed at global scope");
            } finally {
                symbolTable.diagnostics().pop();
            }
        }

        List<NamespaceProgramItems> spaces = new ArrayList<>();
        Map<String, NamespaceProgramItems> spacesByName = new LinkedHashMap<>();

        NamespaceProgramItems globalSpace = new NamespaceProgramItems(null, program.span);
        spaces.add(globalSpace);

        for (ASTNode item : program.topLevelItems) {
            if (item instanceof NamespaceNode n) {
                collectNamespaceDecl(n, null, spaces, spacesByName);
                continue;
            }

            globalSpace.items.add(item);
        }

        Map<GlobalVarDeclNode, VariableSymbol> globalSymbols =
                new IdentityHashMap<>();
        Map<GlobalVarDeclNode, BigInteger> globalInitializers =
                new IdentityHashMap<>();
        Map<GlobalVarDeclNode, ASTNode> globalStaticInitializers =
                new IdentityHashMap<>();
        Map<GlobalVarDeclNode, ArrayLiteralNode> globalArrayLiteralInitializers =
                new IdentityHashMap<>();

        Map<ASTNode, SemanticASTNode> loweredByNode = new IdentityHashMap<>();

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (ASTNode item : space.items) {
                if (item instanceof StructDeclNode n) {
                    symbolTable.declareStruct(
                            new StructSymbol(currentNamespaceName(), n.name)
                    );
                }
            }
        }

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (ASTNode item : space.items) {
                if (!(item instanceof StructDeclNode n)) {
                    continue;
                }

                StructSymbol structSymbol = symbolTable.resolveStruct(n.name);

                if (structSymbol == null) {
                    continue;
                }

                Set<String> seenFieldNames = new HashSet<>();

                for (StructFieldDeclNode fieldNode : n.fields) {
                    if (!seenFieldNames.add(fieldNode.name)) {
                        symbolTable.error("Duplicate field '"
                                + fieldNode.name
                                + "' in struct '"
                                + structSymbol.qualifiedName()
                                + "'");
                        continue;
                    }

                    SemanticType fieldType = typeResolver.analyzeNormalType(fieldNode.type);

                    BigInteger fieldDefault = null;

                    if (fieldNode.constInitializer != null) {
                        fieldDefault = exprAnalyzer.preEvaluatedConstValue(
                                fieldNode.constInitializer,
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

            for (ASTNode item : space.items) {
                if (!(item instanceof StructDeclNode n)) {
                    continue;
                }

                StructSymbol structSymbol = symbolTable.resolveStruct(n.name);

                if (structSymbol == null) {
                    continue;
                }

                for (FunctionDeclNode methodNode : n.methods) {
                    registerMethodSignature(structSymbol, methodNode);
                }
            }
        }

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (ASTNode item : space.items) {
                if (!(item instanceof GlobalVarDeclNode n)) {
                    continue;
                }

                SemanticType type = typeResolver.analyzeNormalType(n.type);

                GlobalVariableSymbol sym = new GlobalVariableSymbol(
                        currentNamespaceName(),
                        n.name,
                        type
                );

                if (n.literalBytes != null) {
                    sym.literalBytes = n.literalBytes;
                }

                if (symbolTable.declareGlobalVariable(sym)) {
                    globalSymbols.put(n, sym);
                }

                if (n.literalBytes != null) {
                    continue;
                }

                if (n.constInitializer instanceof ArrayLiteralNode arrayLiteral) {
                    globalArrayLiteralInitializers.put(n, arrayLiteral);
                } else if (n.constInitializer instanceof GetPtrNode) {
                    globalStaticInitializers.put(n, n.constInitializer);
                } else if (n.constInitializer != null) {
                    globalInitializers.put(n, exprAnalyzer.preEvaluatedConstValue(
                            n.constInitializer,
                            "global variable initializer"
                    ));
                }

                if (n.compileTimeConstant) {
                    BigInteger value = globalInitializers.get(n);

                    if (value != null) {
                        sym.compileTimeConstantValue = value;
                    }
                }
            }
        }

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (ASTNode item : space.items) {
                if (item instanceof FunctionDeclNode fn) {
                    registerFunctionSignature(fn);
                }
            }
        }

        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (ASTNode item : space.items) {
                if (item instanceof StructDeclNode n) {
                    StructSymbol structSymbol = symbolTable.resolveStruct(n.name);

                    if (structSymbol != null) {
                        List<SemanticFunctionDeclNode> loweredMethods =
                                new ArrayList<>();

                        for (FunctionDeclNode methodNode : n.methods) {
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

                if (item instanceof GlobalVarDeclNode n) {
                    VariableSymbol sym = globalSymbols.get(n);

                    if (sym != null) {
                        ASTNode staticInit = globalStaticInitializers.get(n);

                        SemanticExprNode loweredStaticInit = staticInit == null
                                ? null
                                : exprAnalyzer.analyzeExprWithExpectedType(
                                        staticInit,
                                        sym.type);

                        ArrayLiteralNode arrayLiteralInit =
                                globalArrayLiteralInitializers.get(n);

                        if (arrayLiteralInit != null) {
                            loweredStaticInit =
                                    exprAnalyzer.analyzeExprWithExpectedType(
                                            arrayLiteralInit,
                                            sym.type);
                        }

                        loweredByNode.put(item, new SemanticVarDeclNode(
                                item.span,
                                sym,
                                globalInitializers.get(n),
                                loweredStaticInit
                        ));
                    }

                    continue;
                }

                if (item instanceof FunctionDeclNode fn) {
                    SemanticFunctionDeclNode lowered = analyzeFunctionDecl(fn);

                    if (lowered != null) {
                        loweredByNode.put(item, lowered);
                    }
                }
            }
        }

        symbolTable.setCurrentNamespace(null);

        List<SemanticASTNode> semanticItems = new ArrayList<>();
        Set<String> emittedNamespaces = new LinkedHashSet<>();

        for (ASTNode item : program.topLevelItems) {
            if (item instanceof NamespaceNode n) {
                emitNamespacePath(
                        n.name,
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

        for (int i = pendingHoistedArrayLiterals.size() - 1; i >= 0; i--) {
            semanticItems.add(0, pendingHoistedArrayLiterals.get(i));
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
            NamespaceNode node,
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

        for (ASTNode item : node.items) {
            if (item instanceof NamespaceNode nested) {
                collectNamespaceDecl(nested, fullPath, spaces, spacesByName);
                continue;
            }

            spaceItems.items.add(item);
        }
    }

    private void emitNamespacePath(
            String path,
            List<NamespaceProgramItems> spaces,
            Map<String, NamespaceProgramItems> spacesByName,
            Map<ASTNode, SemanticASTNode> loweredByNode,
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
            Map<ASTNode, SemanticASTNode> loweredByNode
    ) {
        List<SemanticASTNode> out = new ArrayList<>();

        for (ASTNode item : space.items) {
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

    private void registerFunctionSignature(FunctionDeclNode fn) {

        if ("main".equals(fn.name) && currentNamespaceName() != null) {
            symbolTable.error("The entry-point function 'main' must be declared in the global namespace, not in namespace '"
                    + currentNamespaceName()
                    + "'");
        }

        List<ParameterSymbol> params = new ArrayList<>();

        for (int i = 0; i < fn.parameters.size(); i++) {
            ParameterNode param = fn.parameters.get(i);

            params.add(new ParameterSymbol(
                    param.name,
                    typeResolver.analyzeNormalType(param.type),
                    i
            ));
        }

        for (ParameterSymbol param : params) {
            if ("self".equals(param.name)) {
                symbolTable.error("'self' is only valid as the first parameter of a struct method");
                break;
            }
        }

        FunctionSymbol symbol = new FunctionSymbol(
                currentNamespaceName(),
                fn.name,
                params,
                typeResolver.analyzeReturnType(fn.returnType),
                false
        );

        if ("main".equals(fn.name) && currentNamespaceName() == null) {
            boolean returnsInt32 = symbol.returnType() instanceof SemanticPrimitiveType prim
                    && prim.kind == SemanticPrimitiveKind.INT32;

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

    private static boolean isArgsArrayParamType(SemanticType type) {
        if (!(type instanceof SemanticPtrType ptr)
                || ptr.permission != PtrPermission.READ_ONLY) {
            return false;
        }

        if (!(ptr.referencedType instanceof SemanticDynamicArrayType outer)) {
            return false;
        }

        if (!(outer.elementType instanceof SemanticDynamicArrayType inner)) {
            return false;
        }

        return inner.elementType instanceof SemanticPrimitiveType prim
                && prim.kind == SemanticPrimitiveKind.UINT8;
    }

    private void registerMethodSignature(
            StructSymbol owner,
            FunctionDeclNode methodNode
    ) {
        List<ParameterSymbol> params = new ArrayList<>();

        for (int i = 0; i < methodNode.parameters.size(); i++) {
            ParameterNode param = methodNode.parameters.get(i);

            params.add(new ParameterSymbol(
                    param.name,
                    typeResolver.analyzeNormalType(param.type),
                    i
            ));
        }

        String display = owner.qualifiedName() + "." + methodNode.name;

        if (OperatorMethods.isOperatorMethodName(methodNode.name)) {
            MethodSymbol symbol = new MethodSymbol(
                    owner,
                    methodNode.name,
                    params,
                    typeResolver.analyzeReturnType(methodNode.returnType),
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
            ParameterSymbol first = params.get(0);

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

        MethodSymbol symbol = new MethodSymbol(
                owner,
                methodNode.name,
                params,
                typeResolver.analyzeReturnType(methodNode.returnType),
                false
        );

        symbolTable.declareMethod(owner, methodNode, symbol);
    }

    private SemanticFunctionDeclNode analyzeFunctionDecl(FunctionDeclNode fn) {
        symbolTable.diagnostics().push(fn.span);
        try {
        FunctionSymbol symbol = symbolTable.findRegisteredFunction(fn);

        if (symbol == null) {

            List<ParameterSymbol> fallbackParams = new ArrayList<>();

            for (int i = 0; i < fn.parameters.size(); i++) {
                ParameterNode param = fn.parameters.get(i);

                fallbackParams.add(new ParameterSymbol(
                        param.name,
                        typeResolver.analyzeNormalType(param.type),
                        i
                ));
            }

            symbol = new FunctionSymbol(
                    currentNamespaceName(),
                    fn.name,
                    fallbackParams,
                    typeResolver.analyzeReturnType(fn.returnType),
                    false
            );
        }

        FunctionSymbol previousFunction = currentFunction;
        currentFunction = symbol;

        symbolTable.pushScope();

        List<VariableSymbol> parameterVariables = new ArrayList<>();

        for (ParameterSymbol param : symbol.parameters) {
            symbolTable.declare(param);
            parameterVariables.add(param);
        }

        List<SemanticStmtNode> bodyStatements = new ArrayList<>();

        if (fn.body != null) {
            for (ASTNode stmt : fn.body.statements) {
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
            FunctionDeclNode methodNode
    ) {
        symbolTable.diagnostics().push(methodNode.span);
        try {
        MethodSymbol symbol = symbolTable.findRegisteredMethod(owner, methodNode);

        if (symbol == null) {

            List<ParameterSymbol> fallbackParams = new ArrayList<>();

            for (int i = 0; i < methodNode.parameters.size(); i++) {
                ParameterNode param = methodNode.parameters.get(i);

                fallbackParams.add(new ParameterSymbol(
                        param.name,
                        typeResolver.analyzeNormalType(param.type),
                        i
                ));
            }

            symbol = new MethodSymbol(
                    owner,
                    methodNode.name,
                    fallbackParams,
                    typeResolver.analyzeReturnType(methodNode.returnType),
                    false
            );
        }

        FunctionSymbol previousFunction = currentFunction;
        currentFunction = symbol;

        symbolTable.pushScope();

        List<VariableSymbol> parameterVariables = new ArrayList<>();

        for (ParameterSymbol param : symbol.parameters) {
            symbolTable.declare(param);
            parameterVariables.add(param);
        }

        List<SemanticStmtNode> bodyStatements = new ArrayList<>();

        if (methodNode.body != null) {
            for (ASTNode stmt : methodNode.body.statements) {
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
            ASTNode stmt,
            List<SemanticStmtNode> out
    ) {
        if (stmt == null) {
            return;
        }

        if (stmt instanceof VarDeclInitNode n) {
            out.addAll(analyzeVarDeclInitStmtList(n));
            return;
        }

        SemanticStmtNode lowered = analyzeStmt(stmt);

        if (lowered != null) {
            out.add(lowered);
        }
    }

    private SemanticStmtNode analyzeStmt(ASTNode node) {
        if (node == null) {
            return null;
        }

        symbolTable.diagnostics().push(node.span);
        try {

        if (node instanceof BlockNode n) {
            symbolTable.pushScope();

            List<SemanticStmtNode> stmts = new ArrayList<>();

            for (ASTNode stmt : n.statements) {
                appendAnalyzedStmt(stmt, stmts);
            }

            symbolTable.popScope();

            return new SemanticBlockNode(n.span, stmts);
        }

        if (node instanceof VarDeclInitNode n) {

            symbolTable.pushScope();
            List<SemanticStmtNode> stmts = analyzeVarDeclInitStmtList(n);
            symbolTable.popScope();

            return new SemanticBlockNode(n.span, stmts);
        }

        if (node instanceof VarDeclNode n) {

            VariableSymbol registered =
                    symbolTable.findRegisteredVarDeclSymbol(n);

            if (registered != null) {
                return new SemanticVarDeclNode(n.span, registered);
            }

            VariableSymbol sym = new VariableSymbol(
                    null,
                    n.name,
                    typeResolver.analyzeNormalType(n.type)
            );

            symbolTable.declare(sym);

            return new SemanticVarDeclNode(n.span, sym);
        }

        if (node instanceof AssignNode n) {
            SemanticExprNode target =
                    exprAnalyzer.analyzeRequiredLValueExpr(
                            n.target,
                            "Assignment target"
                    );

            if (target.type instanceof SemanticStructType) {
                ExpressionAnalyzer.OperatorAssignmentLowering lowered =
                        exprAnalyzer.tryLowerOperatorAssignment(
                                target,
                                n.op,
                                n.value,
                                n.span
                        );

                if (lowered != null) {
                    if (lowered.call != null) {
                        return new SemanticExprStmtNode(
                                n.span,
                                lowered.call
                        );
                    }

                    if (lowered.fallbackValue != null) {
                        return new SemanticAssignNode(
                                n.span,
                                n.op,
                                target,
                                lowered.fallbackValue
                        );
                    }
                }
            }

            SemanticExprNode value =
                    exprAnalyzer.analyzeExprWithExpectedType(
                            n.value,
                            target.type);

            return new SemanticAssignNode(n.span, n.op, target, value);
        }

        if (node instanceof IfNode n) {
            SemanticExprNode condition =
                    exprAnalyzer.analyzeExpr(n.condition);

            SemanticStmtNode thenBranch =
                    analyzeStmt(n.thenBranch);

            SemanticStmtNode elseBranch =
                    n.elseBranch != null
                            ? analyzeStmt(n.elseBranch)
                            : null;

            return new SemanticIfNode(
                    n.span,
                    condition,
                    thenBranch,
                    elseBranch
            );
        }

        if (node instanceof WhileNode n) {
            SemanticExprNode condition =
                    exprAnalyzer.analyzeExpr(n.condition);

            loopDepth++;

            SemanticStmtNode body =
                    analyzeStmt(n.body);

            List<SemanticStmtNode> update = new ArrayList<>();

            for (ASTNode updateStmt : n.update) {
                appendAnalyzedStmt(updateStmt, update);
            }

            loopDepth--;

            return new SemanticWhileNode(n.span, condition, body, update);
        }

        if (node instanceof BreakNode n) {
            return analyzeBreakStmt(n);
        }

        if (node instanceof ContinueNode n) {
            if (n.level != null) {
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

        if (node instanceof ReturnNode n) {
            return analyzeReturnStmt(n);
        }

        if (node instanceof ExprStmtNode n) {
            return new SemanticExprStmtNode(
                    n.span,
                    exprAnalyzer.analyzeExpr(n.expr)
            );
        }

        if (node instanceof FunctionDeclNode fn) {
            symbolTable.error("Function declaration '"
                    + fn.name
                    + "' is only valid in the global top-level scope");
            return null;
        }

        symbolTable.error("Unrecognized statement node: "
                + node.getClass().getSimpleName());

        return null;
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private List<SemanticStmtNode> analyzeVarDeclInitStmtList(
            VarDeclInitNode n
    ) {
        symbolTable.diagnostics().push(n.span);
        try {
        SemanticType type = typeResolver.analyzeNormalType(n.type);

        VariableSymbol sym = new VariableSymbol(
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

            if (target.type instanceof SemanticStructType) {
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

    private SemanticReturnNode analyzeReturnStmt(ReturnNode n) {
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

            FunctionSymbol fallbackFn = new FunctionSymbol(
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

    private SemanticStmtNode analyzeBreakStmt(BreakNode n) {
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
