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
 *   - delete legality,
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

    private final SymbolTable symbolTable = new SymbolTable();

    private final ConstExpressionEvaluator constEval;
    private final ExpressionAnalyzer exprAnalyzer;

    private FunctionSymbol currentFunction = null;

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

    // ============================================================
    // Entry Points
    // ============================================================

    /**
     * Lowers one raw AST root into a semantic AST root.
     */
    public SemanticASTNode analyze(ASTNode root) {
        symbolTable.clear();
        symbolTable.pushScope();

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

    private SemanticProgramNode analyzeProgram(ProgramNode program) {
        /*
         * Global scope currently allows only:
         *
         *   - variable declarations,
         *   - function declarations.
         *
         * Executable statements at global scope are rejected here.
         */
        for (ASTNode item : program.topLevelItems) {
            if (item instanceof VarDeclNode
                    || item instanceof FunctionDeclNode) {
                continue;
            }

            symbolTable.error("Invalid top-level item '"
                    + item.getClass().getSimpleName()
                    + "': only variable declarations and function declarations are allowed at global scope");
        }

        /*
         * Phase 1:
         * Register all global variables before function bodies are analyzed.
         *
         * This allows functions to reference globals declared later in source.
         */
        for (ASTNode item : program.topLevelItems) {
            if (!(item instanceof VarDeclNode n)) {
                continue;
            }

            SemanticType type = analyzeNormalType(n.type);

            VariableSymbol sym = new VariableSymbol(
                    n.name,
                    type,
                    n.isHeap
            );

            if (symbolTable.declare(sym)) {
                symbolTable.registerVarDeclSymbol(n, sym);
            }
        }

        /*
         * Phase 2:
         * Register all function signatures before analyzing any body.
         *
         * This enables forward references, recursion, and mutual recursion.
         */
        for (ASTNode item : program.topLevelItems) {
            if (item instanceof FunctionDeclNode fn) {
                registerFunctionSignature(fn);
            }
        }

        /*
         * Phase 3:
         * Lower all valid top-level items.
         */
        List<SemanticASTNode> semanticItems = new ArrayList<>();

        for (ASTNode item : program.topLevelItems) {
            if (item instanceof VarDeclNode n) {
                VariableSymbol sym =
                        symbolTable.findRegisteredVarDeclSymbol(n);

                /*
                 * Null usually means registration failed due to a duplicate
                 * declaration. The diagnostic has already been recorded.
                 */
                if (sym != null) {
                    semanticItems.add(new SemanticVarDeclNode(sym));
                }

                continue;
            }

            if (item instanceof FunctionDeclNode fn) {
                SemanticFunctionDeclNode lowered = analyzeFunctionDecl(fn);

                if (lowered != null) {
                    semanticItems.add(lowered);
                }
            }
        }

        return new SemanticProgramNode(semanticItems);
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
        List<ParameterSymbol> params = new ArrayList<>();

        for (int i = 0; i < fn.parameters.size(); i++) {
            ParameterNode param = fn.parameters.get(i);

            params.add(new ParameterSymbol(
                    param.name,
                    analyzeNormalType(param.type),
                    i
            ));
        }

        FunctionSymbol symbol = new FunctionSymbol(
                fn.name,
                params,
                analyzeReturnType(fn.returnType),
                false
        );

        symbolTable.declareFunction(fn, symbol);
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
                    n.name,
                    analyzeNormalType(n.type),
                    n.isHeap
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

        if (node instanceof DelNode n) {
            VariableSymbol sym = symbolTable.resolve(n.name);

            if (sym == null) {
                symbolTable.error("Cannot delete undeclared variable '"
                        + n.name
                        + "'");

                sym = new VariableSymbol(
                        n.name,
                        FALLBACK_TYPE,
                        true
                );
            }

            return new SemanticDelNode(sym);
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
                n.name,
                type,
                n.isHeap
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

        if (node instanceof DynamicArrayTypeNode d) {
            return new SemanticDynamicArrayType(
                    analyzeNormalType(d.elementType)
            );
        }

        if (node instanceof StaticArrayTypeNode s) {
            /*
             * Static array size expressions are folded mechanically here so
             * SemanticStaticArrayType can carry canonical size metadata.
             *
             * Legality is checked later:
             *
             *   - foldability,
             *   - positivity,
             *   - uint32_t range,
             *   - other static-size rules.
             */
            BigInteger canonicalSize =
                    exprAnalyzer.analyzeConstExprValue(
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
}