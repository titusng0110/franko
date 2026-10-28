import java.math.BigInteger;
import java.util.*;

public class SemanticAnalyzer {

    public static final class SemanticException extends RuntimeException {
        public SemanticException(String message) {
            super(message);
        }
    }

    public enum EntryKind {
        NONE,
        NO_ARGS,
        ARGS
    }

    private final SymbolTable symbolTable = new SymbolTable();

    private final ConstExpressionEvaluator constEval;
    private final ExpressionAnalyzer exprAnalyzer;

    private FunctionSymbol currentFunction = null;

    private int loopDepth = 0;

    private TargetInfo target = null;

    public void setTarget(TargetInfo target) {
        this.target = target;
    }

    private EntryKind entryKind = EntryKind.NONE;
    private int globalMainCount = 0;

    public EntryKind entryKind() {
        return entryKind;
    }

    private static final SemanticType FALLBACK_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.INT32);

    private static final SemanticType VOID_TYPE =
            new SemanticVoidType();

    public SemanticAnalyzer() {
        this.constEval = new ConstExpressionEvaluator(symbolTable);
        this.exprAnalyzer = new ExpressionAnalyzer(symbolTable, constEval);

        ArrayLowerer arrayLowerer =
                new ArrayLowerer(symbolTable, exprAnalyzer);

        this.exprAnalyzer.setArrayLowerer(arrayLowerer);
        this.exprAnalyzer.setTypeResolver(this::analyzeNormalType);
    }

    public List<String> warnings() {
        return symbolTable.warnings();
    }

    public boolean hasWarnings() {
        return symbolTable.hasWarnings();
    }

    public SemanticASTNode analyze(ASTNode root) {
        symbolTable.clear();
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

        if (symbolTable.hasErrors()) {
            throw new SemanticException(symbolTable.formatErrors());
        }

        return result;
    }

    public List<SemanticStmtNode> analyze(List<ASTNode> nodes) {
        symbolTable.clear();
        symbolTable.pushScope();

        List<SemanticStmtNode> out = new ArrayList<>();

        for (ASTNode stmt : nodes) {
            appendAnalyzedStmt(stmt, out);
        }

        symbolTable.popScope();

        if (symbolTable.hasErrors()) {
            throw new SemanticException(symbolTable.formatErrors());
        }

        return out;
    }

    private static final class NamespaceProgramItems {
        final NamespaceSymbol space;
        final List<ASTNode> items = new ArrayList<>();

        NamespaceProgramItems(NamespaceSymbol space) {
            this.space = space;
        }
    }

    private SemanticProgramNode analyzeProgram(ProgramNode program) {

        for (ASTNode item : program.topLevelItems) {
            if (item instanceof StructDeclNode
                    || item instanceof GlobalVarDeclNode
                    || item instanceof FunctionDeclNode
                    || item instanceof NamespaceNode) {
                continue;
            }

            symbolTable.error("Invalid top-level item '"
                    + item.getClass().getSimpleName()
                    + "': only struct declarations, global variable declarations, function declarations, and namespace declarations are allowed at global scope");
        }

        List<NamespaceProgramItems> spaces = new ArrayList<>();
        Map<String, NamespaceProgramItems> spacesByName = new LinkedHashMap<>();

        NamespaceProgramItems globalSpace = new NamespaceProgramItems(null);
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

                    SemanticType fieldType = analyzeNormalType(fieldNode.type);

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

                SemanticType type = analyzeNormalType(n.type);

                GlobalVariableSymbol sym = new GlobalVariableSymbol(
                        currentNamespaceName(),
                        n.name,
                        type
                );

                if (target != null
                        && OsFfi.isTargetConstantName(
                                currentNamespaceName(), n.name)) {
                    if (!new SemanticPrimitiveType(
                            SemanticPrimitiveKind.UINT8).equals(type)) {
                        symbolTable.error("FFI error: 'os::"
                                + n.name
                                + "' must be declared as 'byte' in"
                                + " import/franko/os.fr: os.fr drifted out"
                                + " of sync with the compiler");
                    } else {
                        sym.compileTimeConstantValue =
                                target.constantFor(n.name);
                    }
                }

                if (n.literalBytes != null) {
                    sym.literalBytes = n.literalBytes;
                }

                if (symbolTable.declareGlobalVariable(sym)) {
                    globalSymbols.put(n, sym);
                }

                if (n.literalBytes != null) {
                    continue;
                }

                if (n.constInitializer instanceof GetPtrNode) {
                    globalStaticInitializers.put(n, n.constInitializer);
                } else if (n.constInitializer != null) {
                    globalInitializers.put(n, exprAnalyzer.preEvaluatedConstValue(
                            n.constInitializer,
                            "global variable initializer"
                    ));
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
                                : exprAnalyzer.analyzeExpr(staticInit);

                        loweredByNode.put(item, new SemanticVarDeclNode(
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
                        space.space.name,
                        loweredItemsOf(space, loweredByNode)
                ));
            }
        }

        symbolTable.warnStructNameClashes();

        if (globalMainCount == 0) {
            symbolTable.error(
                    "No entry-point function 'main' declared: declare either 'func main() -> int32_t' or 'func main(view<array<array<byte>>> <name>) -> int32_t'"
            );
        }

        return new SemanticProgramNode(semanticItems);
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
                            new NamespaceProgramItems(space);
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
                    analyzeNormalType(param.type),
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
                analyzeReturnType(fn.returnType),
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
                        "The entry-point function 'main' cannot be overloaded: declare either 'func main() -> int32_t' or 'func main(view<array<array<byte>>> <name>) -> int32_t', not both"
                );
            } else if (!returnsInt32 || (!noArgs && !hasArgsShape)) {
                symbolTable.error(
                        "The entry-point function 'main' must be declared as 'func main() -> int32_t' or 'func main(view<array<array<byte>>> <name>) -> int32_t'"
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
                    analyzeNormalType(param.type),
                    i
            ));
        }

        String display = owner.qualifiedName() + "." + methodNode.name;

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
            } else if (!isSelfTypeForOwner(first.type, owner)) {
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
                analyzeReturnType(methodNode.returnType),
                false
        );

        symbolTable.declareMethod(owner, methodNode, symbol);
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

    private SemanticFunctionDeclNode analyzeFunctionDecl(FunctionDeclNode fn) {
        FunctionSymbol symbol = symbolTable.findRegisteredFunction(fn);

        if (symbol == null) {

            List<ParameterSymbol> fallbackParams = new ArrayList<>();

            for (int i = 0; i < fn.parameters.size(); i++) {
                ParameterNode param = fn.parameters.get(i);

                fallbackParams.add(new ParameterSymbol(
                        param.name,
                        analyzeNormalType(param.type),
                        i
                ));
            }

            symbol = new FunctionSymbol(
                    currentNamespaceName(),
                    fn.name,
                    fallbackParams,
                    analyzeReturnType(fn.returnType),
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
                symbol,
                parameterVariables,
                new SemanticBlockNode(bodyStatements)
        );
    }

    private SemanticFunctionDeclNode analyzeMethodDecl(
            StructSymbol owner,
            FunctionDeclNode methodNode
    ) {
        MethodSymbol symbol = symbolTable.findRegisteredMethod(owner, methodNode);

        if (symbol == null) {

            List<ParameterSymbol> fallbackParams = new ArrayList<>();

            for (int i = 0; i < methodNode.parameters.size(); i++) {
                ParameterNode param = methodNode.parameters.get(i);

                fallbackParams.add(new ParameterSymbol(
                        param.name,
                        analyzeNormalType(param.type),
                        i
                ));
            }

            symbol = new MethodSymbol(
                    owner,
                    methodNode.name,
                    fallbackParams,
                    analyzeReturnType(methodNode.returnType),
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
                symbol,
                parameterVariables,
                new SemanticBlockNode(bodyStatements)
        );
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

        if (node instanceof BlockNode n) {
            symbolTable.pushScope();

            List<SemanticStmtNode> stmts = new ArrayList<>();

            for (ASTNode stmt : n.statements) {
                appendAnalyzedStmt(stmt, stmts);
            }

            symbolTable.popScope();

            return new SemanticBlockNode(stmts);
        }

        if (node instanceof VarDeclInitNode n) {

            symbolTable.pushScope();
            List<SemanticStmtNode> stmts = analyzeVarDeclInitStmtList(n);
            symbolTable.popScope();

            return new SemanticBlockNode(stmts);
        }

        if (node instanceof VarDeclNode n) {

            VariableSymbol registered =
                    symbolTable.findRegisteredVarDeclSymbol(n);

            if (registered != null) {
                return new SemanticVarDeclNode(registered);
            }

            VariableSymbol sym = new VariableSymbol(
                    null,
                    n.name,
                    analyzeNormalType(n.type)
            );

            symbolTable.declare(sym);

            return new SemanticVarDeclNode(sym);
        }

        if (node instanceof AssignNode n) {
            SemanticExprNode target =
                    exprAnalyzer.analyzeRequiredLValueExpr(
                            n.target,
                            "Assignment target"
                    );

            SemanticExprNode value =
                    exprAnalyzer.analyzeExpr(n.value);

            return new SemanticAssignNode(n.op, target, value);
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

            return new SemanticWhileNode(condition, body, update);
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

            return new SemanticContinueNode();
        }

        if (node instanceof PrintNode n) {
            List<SemanticExprNode> args = new ArrayList<>();

            for (ASTNode arg : n.args) {
                args.add(exprAnalyzer.analyzeExpr(arg));
            }

            return new SemanticPrintNode(args);
        }

        if (node instanceof ReturnNode n) {
            return analyzeReturnStmt(n);
        }

        if (node instanceof ExprStmtNode n) {
            return new SemanticExprStmtNode(
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
    }

    private List<SemanticStmtNode> analyzeVarDeclInitStmtList(
            VarDeclInitNode n
    ) {

        SemanticType type = analyzeNormalType(n.type);

        VariableSymbol sym = new VariableSymbol(
                null,
                n.name,
                type
        );

        symbolTable.declare(sym);

        List<SemanticStmtNode> stmts = new ArrayList<>();
        stmts.add(new SemanticVarDeclNode(sym));

        if (n.init != null) {
            SemanticExprNode target =
                    new SemanticVarExprNode(sym.type, sym);

            SemanticExprNode value =
                    exprAnalyzer.analyzeExpr(n.init);

            stmts.add(new SemanticAssignNode(target, value));
        }

        return stmts;
    }

    private SemanticReturnNode analyzeReturnStmt(ReturnNode n) {
        SemanticExprNode value =
                n.value != null
                        ? exprAnalyzer.analyzeExpr(n.value)
                        : null;

        if (currentFunction == null) {
            symbolTable.error("return statement is only valid inside a function");

            FunctionSymbol fallbackFn = new FunctionSymbol(
                    null,
                    "<invalid-return-context>",
                    List.of(),
                    VOID_TYPE,
                    false
            );

            return new SemanticReturnNode(
                    fallbackFn,
                    value
            );
        }

        return new SemanticReturnNode(
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
            return new SemanticBreakNode(1);
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

        return new SemanticBreakNode(level.intValue());
    }

    private SemanticType analyzeNormalType(TypeNode node) {
        if (node == null) {
            symbolTable.error("Missing type");
            return FALLBACK_TYPE;
        }

        if (node instanceof PrimitiveTypeNode p) {
            return new SemanticPrimitiveType(
                    SemanticPrimitiveKind.valueOf(p.kind.name())
            );
        }

        if (node instanceof StructTypeNode s) {
            StructSymbol structSymbol = resolveStructTypeSymbol(s);

            if (structSymbol == null) {
                return FALLBACK_TYPE;
            }

            return new SemanticStructType(structSymbol);
        }

        if (node instanceof DynamicArrayTypeNode d) {
            return new SemanticDynamicArrayType(
                    analyzeNormalType(d.elementType)
            );
        }

        if (node instanceof StaticArrayTypeNode s) {

            if (s.sizeExpr instanceof InferredSizeNode) {
                symbolTable.error("'?' array size is only allowed in a declaration with an array-literal initializer");
                return FALLBACK_TYPE;
            }

            BigInteger canonicalSize =
                    exprAnalyzer.preEvaluatedConstValue(
                            s.sizeExpr,
                            "static array size"
                    );

            return new SemanticStaticArrayType(
                    analyzeNormalType(s.elementType),
                    canonicalSize.toString(10)
            );
        }

        if (node instanceof PtrTypeNode a) {
            SemanticType referencedType = analyzeNormalType(a.referencedType);

            return switch (a.kind) {
                case OWNER -> new SemanticPtrType(referencedType);
                case MUTABLE -> new SemanticMViewType(referencedType);
                case READ_ONLY -> new SemanticViewType(referencedType);
            };
        }

        if (node instanceof VoidTypeNode) {
            symbolTable.error("void is not a valid ordinary type");
            return FALLBACK_TYPE;
        }

        symbolTable.error("Unknown type node: "
                + node.getClass().getSimpleName());

        return FALLBACK_TYPE;
    }

    private SemanticType analyzeReturnType(TypeNode node) {
        if (node instanceof VoidTypeNode) {
            return VOID_TYPE;
        }

        if (node == null) {
            symbolTable.error("Function declaration is missing a return type");
            return FALLBACK_TYPE;
        }

        return analyzeNormalType(node);
    }

    private StructSymbol resolveStructTypeSymbol(StructTypeNode s) {
        if (s.namespaceSegments == null || s.namespaceSegments.isEmpty()) {
            StructSymbol structSymbol = symbolTable.resolveStruct(s.name);

            if (structSymbol == null) {
                symbolTable.error("Unknown struct type '" + s.name + "'");
            }

            return structSymbol;
        }

        List<String> namespaceSegments = s.namespaceSegments;
        String qualified = String.join("::", namespaceSegments)
                + "::"
                + s.name;

        NamespaceSymbol namespace =
                symbolTable.resolveNamespace(namespaceSegments);

        if (namespace == null) {
            symbolTable.error("Unknown namespace '"
                    + String.join("::", namespaceSegments)
                    + "'");
            return null;
        }

        StructSymbol structSymbol = symbolTable.resolveStructIn(namespace, s.name);

        if (structSymbol == null) {
            symbolTable.error("Unknown struct type '"
                    + qualified
                    + "'");
            return null;
        }

        return structSymbol;
    }
}
