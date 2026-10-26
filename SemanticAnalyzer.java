import java.math.BigInteger;
import java.util.*;

/**
 * ============================================================================
 * SEMANTIC ANALYZER
 * ============================================================================
 *
 * PURPOSE
 *
 * SemanticAnalyzer performs the first semantic pass over the AST.
 *
 * It converts parser AST nodes into a typed, symbol-resolved semantic AST.
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS PHASE DOES
 * ----------------------------------------------------------------------------
 *
 * This phase is responsible for:
 *
 *   - symbol table construction and scope management,
 *   - variable and function declaration registration,
 *   - function signature registration and lookup,
 *   - function body lowering,
 *   - statement lowering,
 *   - parser TypeNode -> SemanticType mapping,
 *   - delegating expression lowering to ExpressionAnalyzer,
 *   - wiring array intrinsic lowering through ExpressionAnalyzer.
 *
 * It guarantees that:
 *
 *   - declared variables become VariableSymbol objects,
 *   - declared functions become FunctionSymbol objects,
 *   - function parameters are declared as local variables in function scope,
 *   - expressions are lowered into typed SemanticExprNode objects,
 *   - function calls are resolved to FunctionSymbol candidates,
 *   - the resulting tree is structurally suitable for later checkers.
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS PHASE DOES NOT DO
 * ----------------------------------------------------------------------------
 *
 * This phase deliberately does NOT enforce full language correctness.
 *
 * Later semantic checkers are responsible for:
 *
 *   - assignment compatibility,
 *   - operator legality,
 *   - return type correctness,
 *   - missing returns,
 *   - void-returning calls in value contexts,
 *   - array bounds safety,
 *   - dynamic array initialization-before-use,
 *   - static array size positivity/range validity,
 *   - runtime/lifetime rules.
 *
 * ----------------------------------------------------------------------------
 * ARRAY INITIALIZER LISTS
 * ----------------------------------------------------------------------------
 *
 * Array initializer lists are not handled in this phase.
 *
 * They are eliminated by the desugaring phase.
 *
 * Example:
 *
 *   xs = [1, 2, 3];
 *
 * desugars before semantic analysis into:
 *
 *   xs[0] = 1;
 *   xs[1] = 2;
 *   xs[2] = 3;
 *
 * Therefore SemanticAnalyzer should not normally see ArrayLiteralNode.
 * Array behavior from initializer lists is checked later through ordinary
 * assignment and indexing rules.
 *
 * ----------------------------------------------------------------------------
 * COLLABORATORS
 * ----------------------------------------------------------------------------
 *
 * SymbolTable:
 *   - lexical scopes,
 *   - variable symbols,
 *   - function overload tables,
 *   - diagnostics.
 *
 * ExpressionAnalyzer:
 *   - expression lowering,
 *   - variable expression resolution,
 *   - function call overload resolution,
 *   - array intrinsic call lowering,
 *   - constant metadata propagation,
 *   - structural lvalue helpers.
 *
 * ArrayLowerer:
 *   - array intrinsic lowering:
 *       arr(size)
 *       arr.init(...)
 *       arr.init_zero(...)
 *       arr.resize(...)
 *       arr.memset(...)
 *       arr.memcpy(...)
 *       arr.memmove(...)
 *       arr.uninit()
 *
 * ConstExpressionEvaluator:
 *   - integer literal parsing,
 *   - unary/binary constant folding.
 */
public class SemanticAnalyzer {

    public static final class SemanticException extends RuntimeException {
        public SemanticException(String message) {
            super(message);
        }
    }

    /**
     * The entry-point shape declared by the program's global `main`.
     *
     * NONE means no valid global `main` was registered.
     */
    public enum EntryKind {
        NONE,
        NO_ARGS,
        ARGS
    }

    private final SymbolTable symbolTable = new SymbolTable();

    private final ConstExpressionEvaluator constEval;
    private final ExpressionAnalyzer exprAnalyzer;

    private FunctionSymbol currentFunction = null;

    /*
     * Compilation target for the os::IS_* target constants, or null when
     * the program does not import "franko/os".
     *
     * The driver sets this only for programs importing the os module, so
     * a user-defined `os` namespace without that import keeps ordinary
     * mutable globals.
     */
    private TargetInfo target = null;

    /**
     * Sets the compilation target for os::IS_* stamping.
     *
     * Call only when the program imports "franko/os" through its
     * canonical specifier.
     */
    public void setTarget(TargetInfo target) {
        this.target = target;
    }

    private EntryKind entryKind = EntryKind.NONE;
    private int globalMainCount = 0;

    /**
     * The entry-point shape declared by the analyzed program.
     *
     * The driver uses this to select the C++ program template.
     */
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
    }

    /**
     * Non-fatal warnings collected during semantic analysis.
     *
     * Warnings do not fail compilation; the driver prints them after a
     * successful analyze() call.
     */
    public List<String> warnings() {
        return symbolTable.warnings();
    }

    public boolean hasWarnings() {
        return symbolTable.hasWarnings();
    }

    // ============================================================
    // Entry Points
    // ============================================================

    /**
     * Lowers one raw AST root into a semantic AST root.
     */
    public SemanticASTNode analyze(ASTNode root) {
        symbolTable.clear();
        symbolTable.pushScope();
        entryKind = EntryKind.NONE;
        globalMainCount = 0;

        SemanticASTNode result;

        if (root instanceof ProgramNode p) {
            result = analyzeProgram(p);
        } else if (root instanceof FunctionDeclNode fn) {
            /*
             * Standalone function analysis is useful for tests.
             *
             * Register the signature first so recursive calls can resolve.
             */
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

    /**
     * Statement-oriented entry point for tests or partial analysis.
     *
     * Use analyze(ProgramNode) for full programs containing functions.
     */
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

    // ============================================================
    // Program / Function Handling
    // ============================================================

    /**
     * One namespace path's pending work during program analysis.
     *
     * space is null for the global namespace. Multiple declarations of
     * the same path merge into one instance, so its items list
     * accumulates across declarations in source order.
     */
    private static final class NamespaceProgramItems {
        final NamespaceSymbol space; // null = global namespace
        final List<ASTNode> items = new ArrayList<>();

        NamespaceProgramItems(NamespaceSymbol space) {
            this.space = space;
        }
    }

    private SemanticProgramNode analyzeProgram(ProgramNode program) {
        /*
         * Global scope currently allows only:
         *
         *   - struct declarations,
         *   - global variable declarations,
         *   - function declarations,
         *   - namespace declarations.
         *
         * Executable statements at global scope are rejected here.
         */
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

        /*
         * Collect the global namespace and one item list per declared
         * namespace path. Multiple declarations of the same path merge
         * into one space, in first-declaration order. Nested namespace
         * declarations are flattened here: each nested path gets its
         * own entry keyed by full path, so later phases only ever see
         * top-level paths.
         */
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

        /*
         * Global const initializers are folded in phase 3 and kept in this
         * map so phase 5 can attach them to the lowered SemanticVarDeclNode
         * without folding twice. Keys are raw nodes, which are unique
         * across all namespaces.
         */
        Map<GlobalVarDeclNode, VariableSymbol> globalSymbols =
                new IdentityHashMap<>();
        Map<GlobalVarDeclNode, BigInteger> globalInitializers =
                new IdentityHashMap<>();

        /*
         * Lowered semantic items by raw node, filled in phase 5. Keys are
         * unique across all namespaces, so one map serves the whole
         * program.
         */
        Map<ASTNode, SemanticASTNode> loweredByNode = new IdentityHashMap<>();

        /*
         * Phase 1:
         * Register struct names, in every namespace.
         *
         * A placeholder StructSymbol is registered first so struct field
         * types can reference the struct itself (e.g. addr<Node> next).
         * Fields are resolved into the same symbol in phase 2.
         */
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

        /*
         * Phase 2:
         * Resolve struct field types and fold field defaults, in every
         * namespace.
         *
         * Fields are appended in declaration order. The struct symbol was
         * registered in phase 1, so field types may reference the struct
         * itself, any struct declared earlier in the same namespace, and
         * any struct in the global namespace.
         */
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

        /*
         * Phase 2b:
         * Register method signatures, in every namespace.
         *
         * Struct names (phase 1) are registered, so `addr<X> self`
         * resolves. Fields (phase 2) are resolved first so
         * method/field name clashes can be detected at registration.
         */
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

        /*
         * Phase 3:
         * Register all global variables, in every namespace, before
         * function bodies are analyzed.
         *
         * This allows functions to reference globals declared later in
         * source and in other namespaces.
         */
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

                /*
                 * Stamp the os::IS_* compile-time target constants so
                 * every reference folds into an integer literal during
                 * expression lowering. A non-byte declaration is an os.fr
                 * drift error, mirroring the intrinsic signature checks.
                 */
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

                if (symbolTable.declareGlobalVariable(sym)) {
                    globalSymbols.put(n, sym);
                }

                if (n.constInitializer != null) {
                    globalInitializers.put(n, exprAnalyzer.preEvaluatedConstValue(
                            n.constInitializer,
                            "global variable initializer"
                    ));
                }
            }
        }

        /*
         * Phase 4:
         * Register all function signatures, in every namespace, before
         * analyzing any body.
         *
         * This enables forward references, recursion, and mutual recursion
         * across namespaces.
         */
        for (NamespaceProgramItems space : spaces) {
            symbolTable.setCurrentNamespace(space.space);

            for (ASTNode item : space.items) {
                if (item instanceof FunctionDeclNode fn) {
                    registerFunctionSignature(fn);
                }
            }
        }

        /*
         * Phase 5:
         * Lower all valid top-level items, in every namespace.
         */
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

                    /*
                     * Null usually means registration failed due to a
                     * duplicate declaration. The diagnostic has already been
                     * recorded.
                     */
                    if (sym != null) {
                        loweredByNode.put(item, new SemanticVarDeclNode(
                                sym,
                                globalInitializers.get(n)
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

        /*
         * Assemble the semantic program in source order. A namespace's
         * merged items are emitted at the position of its first
         * declaration; later declarations of the same path only
         * contributed items. Nested paths are emitted right after
         * their outermost declared ancestor.
         */
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

        /*
         * Safety net: every collected path is introduced by some
         * top-level declaration, so the loop above emits all of them.
         * Emit any leftovers in discovery order rather than dropping
         * them silently.
         */
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

        /*
         * All struct, variable, and function names are registered at this
         * point, so cross-namespace name clashes can be detected in one pass.
         */
        symbolTable.warnStructNameClashes();

        /*
         * A program without a global entry-point function has nothing
         * for the template wrapper to call.
         */
        if (globalMainCount == 0) {
            symbolTable.error(
                    "No entry-point function 'main' declared: declare either 'func main() -> int32_t' or 'func main(addr<array<array<byte>>> <name>) -> int32_t'"
            );
        }

        return new SemanticProgramNode(semanticItems);
    }

    /**
     * Collects one namespace declaration (and any namespaces nested
     * inside it) into the merged per-path item lists.
     *
     * parentPath is null for top-level declarations. A path header
     * ("a::b") is only valid at the top level; a nested declaration
     * must use a single name, and its full path is the enclosing path
     * plus that name. A nested path header is rejected here and its
     * items are skipped.
     */
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

    /**
     * Emits the merged node for one declared namespace path, followed
     * by any not-yet-emitted nested paths below it, in
     * first-discovery order. Merged declarations of the same path
     * emit once, at the first declaration's position.
     */
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

    /**
     * Lowers one namespace's items in the order they were declared,
     * across every declaration that contributed to the namespace.
     */
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

    /**
     * The name of the namespace currently being analyzed, or null for
     * the global namespace.
     */
    private String currentNamespaceName() {
        NamespaceSymbol space = symbolTable.currentNamespace();

        return space == null ? null : space.name;
    }

    /**
     * Registers only a function's signature.
     *
     * Function identity is:
     *
     *   name + ordered parameter types
     *
     * Return type and parameter names are not part of overload identity.
     */
    private void registerFunctionSignature(FunctionDeclNode fn) {
        /*
         * The entry-point function must live in the global namespace: the
         * template wrapper calls the global mangled `main` directly.
         */
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

        /*
         * The entry-point function keeps a mangled C++ name like every
         * other function; the generated C++ entry point is a small
         * wrapper in the program template that calls it. Two shapes are
         * legal in the global namespace:
         *
         *   func main() -> int32_t
         *   func main(addr<array<array<byte>>> <name>) -> int32_t
         *
         * The parameter name of the args form is free. Any other shape
         * cannot be called by a template wrapper, and overloading main
         * is forbidden (C++ forbids overloading main).
         */
        if ("main".equals(fn.name) && currentNamespaceName() == null) {
            boolean returnsInt32 = symbol.returnType() instanceof SemanticPrimitiveType prim
                    && prim.kind == SemanticPrimitiveKind.INT32;

            boolean noArgs = params.isEmpty();
            boolean hasArgsShape = params.size() == 1
                    && isArgsArrayParamType(params.get(0).type);

            globalMainCount++;

            if (globalMainCount > 1) {
                symbolTable.error(
                        "The entry-point function 'main' cannot be overloaded: declare either 'func main() -> int32_t' or 'func main(addr<array<array<byte>>> <name>) -> int32_t', not both"
                );
            } else if (!returnsInt32 || (!noArgs && !hasArgsShape)) {
                symbolTable.error(
                        "The entry-point function 'main' must be declared as 'func main() -> int32_t' or 'func main(addr<array<array<byte>>> <name>) -> int32_t'"
                );
            } else {
                entryKind = noArgs ? EntryKind.NO_ARGS : EntryKind.ARGS;
            }
        }

        symbolTable.declareFunction(fn, symbol);
    }

    /**
     * Whether a type is the entry-point args parameter type:
     * `addr<array<array<byte>>>`.
     *
     * `byte` lexes as `uint8_t`, so the check is structural on
     * UINT8. The parameter name is free and plays no role.
     */
    private static boolean isArgsArrayParamType(SemanticType type) {
        if (!(type instanceof SemanticAddrType addr)) {
            return false;
        }

        if (!(addr.referencedType instanceof SemanticDynamicArrayType outer)) {
            return false;
        }

        if (!(outer.elementType instanceof SemanticDynamicArrayType inner)) {
            return false;
        }

        return inner.elementType instanceof SemanticPrimitiveType prim
                && prim.kind == SemanticPrimitiveKind.UINT8;
    }

    /**
     * Registers only a method's signature on its owning struct.
     *
     * The first parameter must be literally `addr<X> self` for the
     * enclosing struct X. Only the first parameter may be named `self`.
     */
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
                    + "' must have 'addr<"
                    + owner.qualifiedName()
                    + "> self' as its first parameter");
        } else {
            ParameterSymbol first = params.get(0);

            if (!"self".equals(first.name)) {
                symbolTable.error("Method '"
                        + display
                        + "' must have 'addr<"
                        + owner.qualifiedName()
                        + "> self' as its first parameter");
            } else if (!isSelfTypeForOwner(first.type, owner)) {
                symbolTable.error("Method '"
                        + display
                        + "' has a 'self' parameter of type "
                        + first.type.describe()
                        + "; it must be addr<"
                        + owner.qualifiedName()
                        + ">");
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
        if (!(type instanceof SemanticAddrType addr)) {
            return false;
        }

        if (!(addr.referencedType instanceof SemanticStructType structType)) {
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
            /*
             * Signature registration can fail, usually because this function
             * duplicates an existing overload.
             *
             * Still build a fallback symbol with the real parameters so the
             * body can be analyzed and more diagnostics can be collected.
             */
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

        /*
         * Parameters are ordinary variables inside the function body.
         *
         * ParameterSymbol extends VariableSymbol, so each parameter is declared
         * directly into the function's initial local scope.
         */
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
            /*
             * Signature registration can fail, usually because this method
             * duplicates an existing overload or conflicts with a field.
             *
             * Still build a fallback symbol with the real parameters so the
             * body can be analyzed and more diagnostics can be collected.
             */
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

    // ============================================================
    // Statement List Lowering
    // ============================================================

    /**
     * Appends one lowered semantic statement into a statement list.
     *
     * Most statements lower to one semantic statement.
     *
     * VarDeclInitNode may lower to:
     *
     *   SemanticVarDeclNode
     *   SemanticAssignNode
     *
     * This method is defensive. In the normal pipeline, most declaration
     * initializer sugar is already removed by the desugarer.
     */
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

    // ============================================================
    // Statements
    // ============================================================

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
            /*
             * Defensive fallback for declaration initializer sugar that reaches
             * a single-statement position.
             *
             * If multiple semantic statements are produced, wrap them in a
             * semantic block to preserve statement structure.
             */
            symbolTable.pushScope();
            List<SemanticStmtNode> stmts = analyzeVarDeclInitStmtList(n);
            symbolTable.popScope();

            return new SemanticBlockNode(stmts);
        }

        if (node instanceof VarDeclNode n) {
            /*
             * Global declarations are pre-registered by analyzeProgram.
             * Local declarations are declared here.
             */
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

            return new SemanticAssignNode(target, value);
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

            SemanticStmtNode body =
                    analyzeStmt(n.body);

            return new SemanticWhileNode(condition, body);
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

    // ============================================================
    // Declaration Initializers
    // ============================================================

    private List<SemanticStmtNode> analyzeVarDeclInitStmtList(
            VarDeclInitNode n
    ) {
        /*
         * Defensive fallback.
         *
         * In the normal pipeline, declaration initializer sugar should already
         * be removed by Desugarer.
         *
         * This method handles remaining:
         *
         *   T x = expr;
         *
         * as:
         *
         *   SemanticVarDeclNode(x)
         *   SemanticAssignNode(x, expr)
         *
         * ArrayLiteralNode should not reach this point because array
         * initializer lists are desugared into indexed assignments earlier.
         */
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

    // ============================================================
    // Type Mapping
    // ============================================================

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
            /*
             * Static array size expressions arrive pre-evaluated: the
             * desugarer folds every size into a canonical decimal
             * literal, so this only reads the value.
             *
             * Legality is checked later:
             *
             *   - positivity,
             *   - uint32_t range,
             *   - other static-size rules.
             *
             * A surviving InferredSizeNode means the desugarer missed a `?`
             * (defensive: inference or the misplaced-`?` diagnostic should
             * have handled it first).
             */
            if (s.sizeExpr instanceof InferredSizeNode) {
                symbolTable.error("'?' array size is only allowed in a declaration with an array-literal or string-literal initializer");
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

        if (node instanceof AddrTypeNode a) {
            return new SemanticAddrType(
                    analyzeNormalType(a.referencedType)
            );
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

    /**
     * Resolves a struct type reference to its StructSymbol.
     *
     * Unqualified names use the namespace chain: the namespace currently
     * being analyzed first, then the global namespace. (Enclosing
     * namespaces are not searched.)
     *
     * Qualified names resolve strictly inside the named namespace path,
     * starting from the top level: all but the last segment must name
     * a declared namespace path, and the last segment names a struct
     * declared in it.
     */
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