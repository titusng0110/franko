import java.math.BigInteger;
import java.util.*;

/**
 * ============================================================================
 * SEMANTIC ANALYZER
 * AST Lowering, Symbol Resolution, Type Decoration, and Structural Validation
 * ============================================================================
 *
 * PURPOSE:
 * The SemanticAnalyzer bridges the gap between the raw syntactic parser output
 * and the later semantic legality checkers. Its primary job is to transform the
 * untyped parser AST (ASTNode) into a strongly-typed, scope-resolved Semantic AST
 * (SemanticASTNode).
 *
 * This phase performs the compiler's first semantic pass. It does not try to
 * prove that every language rule is satisfied. Instead, it builds a decorated
 * semantic tree where:
 *
 *   - variable names are resolved to symbols,
 *   - function names are resolved to overloaded function symbols,
 *   - types are mapped into canonical SemanticType objects,
 *   - expressions carry mechanically inferred result types,
 *   - foldable integer expressions carry constantValue metadata,
 *   - storage intrinsics are lowered into dedicated Semantic AST nodes,
 *   - and storage-sensitive constructs are checked well enough to ensure that
 *     the lowered tree has a meaningful shape.
 *
 * IMPORTANT:
 * This analyzer intentionally does not perform every language legality check.
 *
 * In particular, later checkers should still validate:
 *
 *   - return; in non-void functions,
 *   - return expr; in void functions,
 *   - return expression compatibility with the declared return type,
 *   - missing return in non-void functions,
 *   - whether a void-returning function call appears in a value context,
 *   - full assignment compatibility,
 *   - full arithmetic/operator validity,
 *   - array intrinsic argument compatibility,
 *   - deletion legality.
 */
public class SemanticAnalyzer {

    public static final class SemanticException extends RuntimeException {
        public SemanticException(String message) {
            super(message);
        }
    }

    public static final class Context {
        private final Deque<Map<String, VariableSymbol>> scopes = new ArrayDeque<>();

        /*
        * Callable function overload table.
        *
        * Functions are grouped by identifier. Within a group, declarations are
        * rejected as duplicates if they have the same function declaration
        * signature:
        *
        *   function identifier
        *   + ordered parameter type list
        *
        * Parameter names are intentionally not part of declaration identity.
        * Return type is also intentionally not part of declaration identity.
        *
        * Therefore these are rejected as duplicates:
        *
        *   func f(int32_t x) -> int32_t
        *   func f(int32_t y) -> uint32_t
        *
        * because both have the same signature:
        *
        *   f(int32_t)
        *
        * But these are distinct overloads:
        *
        *   func f(int32_t x, uint32_t y) -> int32_t
        *   func f(uint32_t y, int32_t x) -> int32_t
        *
        * because parameter order is part of the signature.
        *
        * This mirrors variable declaration behavior: the duplicate is reported
        * as a compilation error and is not inserted into the symbol table.
        */
        private final Map<String, List<FunctionSymbol>> functions = new LinkedHashMap<>();

        /*
         * Analyzer-local mapping from raw function declaration nodes to the
         * FunctionSymbol registered for that declaration.
         *
         * FunctionSymbol intentionally does not store rawDecl.
         *
         * This map preserves the two-phase analysis workflow:
         *
         *   Phase 1: register all function signatures.
         *   Phase 2: analyze each function body using the symbol registered
         *            for that exact raw declaration object.
         *
         * IdentityHashMap is intentional. Raw AST nodes are compiler objects,
         * not semantic value objects, so declaration association is based on
         * object identity rather than equals/hashCode.
         *
         * Duplicate function declarations are not inserted here, matching the
         * behavior of duplicate variable declarations. During body analysis,
         * such declarations receive fallback symbols so additional errors can
         * still be collected.
         */
        private final IdentityHashMap<FunctionDeclNode, FunctionSymbol> functionDeclSymbols =
                new IdentityHashMap<>();

        private final List<String> errors = new ArrayList<>();

        void clear() {
            scopes.clear();
            functions.clear();
            functionDeclSymbols.clear();
            errors.clear();
        }

        void pushScope() {
            scopes.push(new LinkedHashMap<>());
        }

        void popScope() {
            scopes.pop();
        }

        void declare(VariableSymbol sym) {
            Map<String, VariableSymbol> current = scopes.peek();

            if (current == null) {
                throw new IllegalStateException("Internal error: no active scope.");
            }

            if (current.containsKey(sym.name)) {
                error("Duplicate declaration of variable '" + sym.name + "' in the same scope");
                return;
            }

            current.put(sym.name, sym);
        }

        VariableSymbol resolve(String name) {
            for (Map<String, VariableSymbol> scope : scopes) {
                VariableSymbol sym = scope.get(name);

                if (sym != null) {
                    return sym;
                }
            }

            return null;
        }

        void declareFunction(FunctionDeclNode decl, FunctionSymbol fn) {
            List<FunctionSymbol> overloads =
                    functions.computeIfAbsent(fn.name, ignored -> new ArrayList<>());

            for (FunctionSymbol existing : overloads) {
                if (sameParameterSignature(existing.parameters, fn.parameters)) {
                    error("Duplicate declaration of function '"
                            + fn.signatureString()
                            + "' in the same scope");
                    return;
                }
            }

            overloads.add(fn);
            functionDeclSymbols.put(decl, fn);
        }

        FunctionSymbol resolveFunction(String name, List<SemanticExprNode> args) {
            List<FunctionSymbol> overloads = functions.get(name);

            if (overloads == null || overloads.isEmpty()) {
                return null;
            }

            List<FunctionSymbol> matches = new ArrayList<>();

            for (FunctionSymbol fn : overloads) {
                if (isApplicableFunction(fn, args)) {
                    matches.add(fn);
                }
            }

            if (matches.size() == 1) {
                return matches.get(0);
            }

            List<SemanticType> argTypes = args.stream()
                    .map(arg -> arg == null ? null : arg.type)
                    .toList();

            if (matches.size() > 1) {
                error("Ambiguous call to overloaded function '"
                        + formatCallSignature(name, argTypes)
                        + "': argument constants fit multiple overloads");
                return matches.get(0);
            }

            return null;
        }

        boolean hasAnyFunctionNamed(String name) {
            List<FunctionSymbol> overloads = functions.get(name);
            return overloads != null && !overloads.isEmpty();
        }

        FunctionSymbol findRegisteredFunction(FunctionDeclNode decl) {
            return functionDeclSymbols.get(decl);
        }

        List<FunctionSymbol> getFunctionsByName(String name) {
            return List.copyOf(functions.getOrDefault(name, List.of()));
        }

        Collection<FunctionSymbol> allFunctions() {
            List<FunctionSymbol> out = new ArrayList<>();

            for (List<FunctionSymbol> overloads : functions.values()) {
                out.addAll(overloads);
            }

            return List.copyOf(out);
        }

        private static boolean sameParameterSignature(
                List<ParameterSymbol> a,
                List<ParameterSymbol> b
        ) {
            if (a.size() != b.size()) {
                return false;
            }

            for (int i = 0; i < a.size(); i++) {
                ParameterSymbol left = a.get(i);
                ParameterSymbol right = b.get(i);

                /*
                * Franko function declaration identity uses ordered parameter types.
                *
                * Parameter names are not part of the function signature.
                * Return type is also not part of the function signature.
                */
                if (!sameType(left.type, right.type)) {
                    return false;
                }
            }

            return true;
        }

        private static boolean isApplicableFunction(
                FunctionSymbol fn,
                List<SemanticExprNode> args
        ) {
            if (fn.arity() != args.size()) {
                return false;
            }

            List<SemanticType> parameterTypes = fn.parameterTypes();

            for (int i = 0; i < args.size(); i++) {
                SemanticType parameterType = parameterTypes.get(i);
                SemanticExprNode arg = args.get(i);

                if (!isArgumentApplicableToParameter(arg, parameterType)) {
                    return false;
                }
            }

            return true;
        }

        private static boolean isArgumentApplicableToParameter(
                SemanticExprNode arg,
                SemanticType parameterType
        ) {
            if (arg == null || parameterType == null || arg.type == null) {
                return false;
            }

            /*
            * Fluid integer constants may match any primitive integer parameter
            * whose range can represent the constant value.
            *
            * Examples:
            *
            *   func f(uint8_t x)
            *   f(255)     valid candidate
            *   f(256)     not a uint8_t candidate
            *
            * If multiple overloads are valid candidates, the call is ambiguous.
            */
            if (arg.isConstant()
                    && parameterType instanceof SemanticPrimitiveType primitive) {
                return fitsBigIntegerToPrimitive(
                        arg.constantValue,
                        primitive.kind
                );
            }

            /*
            * Nonconstant expressions must match exactly.
            *
            * This also covers:
            *
            *   - address arguments,
            *   - array arguments,
            *   - nonconstant primitive arguments.
            */
            return sameType(arg.type, parameterType);
        }

        private static boolean fitsBigIntegerToPrimitive(
                BigInteger value,
                SemanticPrimitiveKind kind
        ) {
            if (value == null || kind == null) {
                return false;
            }

            int bits = bitWidth(kind);
            boolean signed = isSigned(kind);

            BigInteger two = BigInteger.valueOf(2);

            BigInteger min = signed
                    ? two.pow(bits - 1).negate()
                    : BigInteger.ZERO;

            BigInteger max = signed
                    ? two.pow(bits - 1).subtract(BigInteger.ONE)
                    : two.pow(bits).subtract(BigInteger.ONE);

            return value.compareTo(min) >= 0
                    && value.compareTo(max) <= 0;
        }

        private static int bitWidth(SemanticPrimitiveKind kind) {
            return switch (kind) {
                case INT8, UINT8 -> 8;
                case INT16, UINT16 -> 16;
                case INT32, UINT32 -> 32;
                case INT64, UINT64 -> 64;
            };
        }

        private static boolean isSigned(SemanticPrimitiveKind kind) {
            return switch (kind) {
                case INT8, INT16, INT32, INT64 -> true;
                case UINT8, UINT16, UINT32, UINT64 -> false;
            };
        }

        private static boolean sameType(SemanticType a, SemanticType b) {
            if (a == null || b == null) {
                return false;
            }

            /*
             * SemanticType classes should implement structural equals/hashCode.
             * The describe() fallback keeps overload resolution useful even if
             * a future SemanticType subclass forgets to override equality.
             */
            return a.equals(b) || a.describe().equals(b.describe());
        }

        private static String formatCallSignature(
                String name,
                List<SemanticType> argTypes
        ) {
            StringBuilder sb = new StringBuilder();

            sb.append(name).append("(");

            for (int i = 0; i < argTypes.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                sb.append(argTypes.get(i).describe());
            }

            sb.append(")");

            return sb.toString();
        }

        void error(String msg) {
            errors.add(msg);
        }

        List<String> getErrors() {
            return List.copyOf(errors);
        }

        boolean hasErrors() {
            return !errors.isEmpty();
        }

        String formatErrors() {
            StringBuilder sb = new StringBuilder("Semantic analysis failed:\n");

            for (int i = 0; i < errors.size(); i++) {
                sb.append("  ")
                        .append(i + 1)
                        .append(". ")
                        .append(errors.get(i))
                        .append('\n');
            }

            return sb.toString();
        }
    }

    private final Context ctx = new Context();

    private FunctionSymbol currentFunction = null;

    private static final SemanticType FALLBACK_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.INT32);

    private static final SemanticType BOOL_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8);

    private static final SemanticType VOID_TYPE =
            new SemanticVoidType();

    private static final Set<String> BOOL_RESULT_OPS = Set.of(
            "==", "!=", "<", ">", "<=", ">=", "&&", "||"
    );

    private static final Set<String> NON_BOOL_INTEGER_RESULT_OPS = Set.of(
            "+", "-", "*", "/",
            "&", "|", "^",
            "<<", ">>"
    );

    public SemanticAnalyzer() {
    }

    /**
     * Entry point: lowers an ASTNode into a SemanticASTNode.
     */
    public SemanticASTNode analyze(ASTNode root) {
        ctx.clear();
        ctx.pushScope();

        SemanticASTNode result;

        if (root instanceof ProgramNode p) {
            result = analyzeProgram(p);
        } else if (root instanceof FunctionDeclNode fn) {
            /*
             * Allow analyzing a standalone function declaration for tests.
             * We still register its signature first so recursive calls can
             * resolve.
             */
            registerFunctionSignature(fn);
            result = analyzeFunctionDecl(fn);
        } else {
            result = analyzeStmt(root);

            if (result == null) {
                result = analyzeExpr(root);
            }
        }

        ctx.popScope();

        if (ctx.hasErrors()) {
            throw new SemanticException(ctx.formatErrors());
        }

        return result;
    }

    /**
     * Entry point variant for a raw list of statements.
     *
     * This is intentionally statement-oriented, not top-level-item-oriented.
     * Use analyze(ProgramNode) when functions are present.
     */
    public List<SemanticStmtNode> analyze(List<ASTNode> nodes) {
        ctx.clear();
        ctx.pushScope();

        List<SemanticStmtNode> semanticStmts = new ArrayList<>();

        for (ASTNode stmt : nodes) {
            SemanticStmtNode lowered = analyzeStmt(stmt);

            if (lowered != null) {
                semanticStmts.add(lowered);
            }
        }

        ctx.popScope();

        if (ctx.hasErrors()) {
            throw new SemanticException(ctx.formatErrors());
        }

        return semanticStmts;
    }

    // ============================================================
    // Program / Function Handling
    // ============================================================

    private SemanticProgramNode analyzeProgram(ProgramNode program) {
        /*
         * Phase 1:
         * Register all function signatures before analyzing any body.
         *
         * This allows forward references:
         *
         *   func a() -> int32_t { return b(); }
         *   func b() -> int32_t { return 1; }
         *
         * It also enables direct recursion and mutual recursion.
         */
        for (ASTNode item : program.topLevelItems) {
            if (item instanceof FunctionDeclNode fn) {
                registerFunctionSignature(fn);
            }
        }

        /*
         * Phase 2:
         * Analyze top-level items.
         *
         * Function declarations become SemanticFunctionDeclNode.
         * Top-level statements remain SemanticStmtNode.
         */
        List<SemanticASTNode> semanticItems = new ArrayList<>();

        for (ASTNode item : program.topLevelItems) {
            if (item instanceof FunctionDeclNode fn) {
                SemanticFunctionDeclNode lowered = analyzeFunctionDecl(fn);

                if (lowered != null) {
                    semanticItems.add(lowered);
                }
            } else {
                SemanticStmtNode lowered = analyzeStmt(item);

                if (lowered != null) {
                    semanticItems.add(lowered);
                }
            }
        }

        return new SemanticProgramNode(semanticItems);
    }

    /**
     * Registers only the function declaration signature:
     *
     *   - function identifier,
     *   - ordered parameter type list,
     *   - return type as metadata.
     *
     * Function declaration identity is:
     *
     *   identifier + ordered parameter type list
     *
     * Parameter names are intentionally not part of function declaration identity.
     * Return type is also intentionally not part of function declaration identity.
     *
     * Therefore these are rejected as duplicates:
     *
     *   func f(int32_t x) -> int32_t
     *   func f(int32_t y) -> uint32_t
     *
     * because both have the same signature:
     *
     *   f(int32_t)
     *
     * The duplicate function declaration behaves like a duplicate variable
     * declaration: it is reported and not inserted into the function table.
     */
    private void registerFunctionSignature(FunctionDeclNode fn) {
        String name = fn.name;

        List<ParameterSymbol> params = new ArrayList<>();

        for (int i = 0; i < fn.parameters.size(); i++) {
            ParameterNode param = fn.parameters.get(i);
            SemanticType paramType = analyzeNormalType(param.type);

            params.add(new ParameterSymbol(
                    param.name,
                    paramType,
                    i
            ));
        }

        SemanticType returnType = analyzeReturnType(fn.returnType);

        FunctionSymbol symbol = new FunctionSymbol(
                name,
                params,
                returnType,
                false
        );

        ctx.declareFunction(fn, symbol);
    }

    private SemanticFunctionDeclNode analyzeFunctionDecl(FunctionDeclNode fn) {
        FunctionSymbol symbol = ctx.findRegisteredFunction(fn);

        if (symbol == null) {
            /*
             * This usually means signature registration failed, for example due
             * to a duplicate function declaration. Build a fallback symbol so
             * the body can still be analyzed and additional errors can be
             * collected.
             *
             * The fallback symbol is intentionally not inserted into the
             * overload table.
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

        ctx.pushScope();

        List<VariableSymbol> parameterVariables = new ArrayList<>();

        /*
         * Parameters are ordinary variables in the function body's initial
         * scope.
         *
         * Because ParameterSymbol extends VariableSymbol, we declare the exact
         * ParameterSymbol object directly. This preserves index metadata for
         * later phases while keeping expression resolution simple.
         */
        for (ParameterSymbol param : symbol.parameters) {
            ctx.declare(param);
            parameterVariables.add(param);
        }

        List<SemanticStmtNode> bodyStatements = new ArrayList<>();

        if (fn.body != null) {
            for (ASTNode stmt : fn.body.statements) {
                SemanticStmtNode lowered = analyzeStmt(stmt);

                if (lowered != null) {
                    bodyStatements.add(lowered);
                }
            }
        } else {
            /*
             * The grammar requires a block body, but this defensive check keeps
             * the analyzer robust if manually-created ASTs are passed in tests.
             */
            ctx.error("Function '" + fn.name + "' is missing a body");
        }

        ctx.popScope();

        currentFunction = previousFunction;

        return new SemanticFunctionDeclNode(
                symbol,
                parameterVariables,
                new SemanticBlockNode(bodyStatements)
        );
    }

    // ============================================================
    // Statements & Lowering
    // ============================================================

    private SemanticStmtNode analyzeStmt(ASTNode node) {
        if (node == null) {
            return null;
        }

        if (node instanceof BlockNode n) {
            ctx.pushScope();

            List<SemanticStmtNode> stmts = new ArrayList<>();

            for (ASTNode s : n.statements) {
                SemanticStmtNode ss = analyzeStmt(s);

                if (ss != null) {
                    stmts.add(ss);
                }
            }

            ctx.popScope();

            return new SemanticBlockNode(stmts);
        }

        if (node instanceof VarDeclNode n) {
            SemanticType type = analyzeNormalType(n.type);
            VariableSymbol sym = new VariableSymbol(n.name, type, n.isHeap);
            ctx.declare(sym);
            return new SemanticVarDeclNode(sym);
        }

        if (node instanceof AssignNode n) {
            SemanticExprNode target = analyzeRequiredLValueExpr(
                    n.target,
                    "Assignment target"
            );

            SemanticExprNode value = analyzeExpr(n.value);

            return new SemanticAssignNode(target, value);
        }

        if (node instanceof IfNode n) {
            SemanticExprNode cond = analyzeExpr(n.condition);
            SemanticStmtNode thenB = analyzeStmt(n.thenBranch);
            SemanticStmtNode elseB = n.elseBranch != null
                    ? analyzeStmt(n.elseBranch)
                    : null;

            return new SemanticIfNode(cond, thenB, elseB);
        }

        if (node instanceof WhileNode n) {
            SemanticExprNode cond = analyzeExpr(n.condition);
            SemanticStmtNode body = analyzeStmt(n.body);

            return new SemanticWhileNode(cond, body);
        }

        if (node instanceof DelNode n) {
            VariableSymbol sym = ctx.resolve(n.name);

            if (sym == null) {
                ctx.error("Cannot delete undeclared variable '" + n.name + "'");
                sym = new VariableSymbol(n.name, FALLBACK_TYPE, true);
            }

            return new SemanticDelNode(sym);
        }

        if (node instanceof PrintNode n) {
            List<SemanticExprNode> args = new ArrayList<>();

            for (ASTNode arg : n.args) {
                args.add(analyzeExpr(arg));
            }

            return new SemanticPrintNode(args);
        }

        if (node instanceof ReturnNode n) {
            return analyzeReturnStmt(n);
        }

        if (node instanceof ExprStmtNode n) {
            SemanticStmtNode intrinsic = tryLowerIntrinsicExprStmt(n.expr);

            if (intrinsic != null) {
                return intrinsic;
            }

            return new SemanticExprStmtNode(analyzeExpr(n.expr));
        }

        /*
         * Function declarations are top-level items and are normally handled by
         * analyzeProgram(...), not analyzeStmt(...). This fallback helps if a
         * manually-created AST puts a function in a statement position.
         */
        if (node instanceof FunctionDeclNode fn) {
            ctx.error("Function declaration '" + fn.name + "' is only valid as a top-level item");
            return null;
        }

        ctx.error("Unrecognized statement node: " + node.getClass().getSimpleName());
        return null;
    }

    private SemanticReturnNode analyzeReturnStmt(ReturnNode n) {
        if (currentFunction == null) {
            ctx.error("return statement is only valid inside a function");

            SemanticExprNode value = n.value != null
                    ? analyzeExpr(n.value)
                    : null;

            FunctionSymbol fallbackFn = new FunctionSymbol(
                    "<invalid-return-context>",
                    List.of(),
                    VOID_TYPE,
                    false
            );

            return new SemanticReturnNode(fallbackFn, value);
        }

        SemanticExprNode value = n.value != null
                ? analyzeExpr(n.value)
                : null;

        /*
         * Do not check here:
         *
         *   - return; inside non-void function,
         *   - return expr; inside void function,
         *   - return expression compatibility,
         *   - missing return from non-void function.
         *
         * Those are statement/type-checker responsibilities.
         */
        return new SemanticReturnNode(currentFunction, value);
    }

    /**
     * Lowers expression-statement forms that are Franko storage intrinsics:
     *
     *   dynArray(size);
     *   dynArray.uninit();
     *   array.memset(value);
     *   array.memcpy(source);
     *
     * Supported receiver types:
     *
     *   array<T>:
     *     - init through call syntax: arr(size)
     *     - uninit:                  arr.uninit()
     *     - memset:                  arr.memset(value)
     *     - memcpy:                  arr.memcpy(source)
     *
     *   array<T, N>:
     *     - memset:                  arr.memset(value)
     *     - memcpy:                  arr.memcpy(source)
     *
     * These operations are intentionally lowered to dedicated semantic
     * statement nodes instead of ordinary FunctionSymbol calls. They mutate
     * storage and require storage-backed lvalue receivers.
     *
     * Dynamic array initialization supports lvalue targets such as:
     *
     *   arr(20);
     *   deref(p)(20);
     *
     * Therefore SemanticArrayInitNode stores the initialized target as a
     * SemanticExprNode rather than a VariableSymbol.
     */
    private SemanticStmtNode tryLowerIntrinsicExprStmt(ASTNode expr) {
        if (!(expr instanceof CallNode call)) {
            return null;
        }

        if (call.callee instanceof MemberAccessNode mac) {
            String member = mac.memberName;

            if (member.equals("memset")) {
                if (call.args.size() != 1) {
                    ctx.error("Array intrinsic 'memset' expects exactly 1 argument");
                    return null;
                }

                SemanticExprNode receiver = analyzeRequiredLValueExpr(
                        mac.target,
                        "memset receiver"
                );

                requireArrayType(receiver, "memset receiver");

                SemanticExprNode value = analyzeExpr(call.args.get(0));

                return new SemanticArrayMemsetNode(receiver, value);
            }

            if (member.equals("memcpy")) {
                if (call.args.size() != 1) {
                    ctx.error("Array intrinsic 'memcpy' expects exactly 1 argument");
                    return null;
                }

                SemanticExprNode target = analyzeRequiredLValueExpr(
                        mac.target,
                        "memcpy target"
                );

                requireArrayType(target, "memcpy target");

                SemanticExprNode source = analyzeRequiredLValueExpr(
                        call.args.get(0),
                        "memcpy source"
                );

                requireArrayType(source, "memcpy source");

                return new SemanticArrayMemcpyNode(target, source);
            }

            if (member.equals("uninit")) {
                if (!call.args.isEmpty()) {
                    ctx.error("Array intrinsic 'uninit' expects exactly 0 arguments");
                    return null;
                }

                SemanticExprNode receiver = analyzeRequiredLValueExpr(
                        mac.target,
                        "uninit receiver"
                );

                /*
                 * Only dynamic arrays own dynamic storage that can be uninitialized.
                 * Static arrays do not support uninit().
                 */
                requireDynamicArrayType(receiver, "uninit receiver");

                return new SemanticArrayUninitNode(receiver);
            }

            return null;
        }

        /*
         * Lvalue-based dynamic array initialization:
         *
         *   arr(20);
         *   deref(p)(20);
         *
         * We only lower this form when the callee expression has dynamic array
         * type:
         *
         *   array<T>
         *
         * Static arrays, array<T, N>, do not support call-style initialization.
         *
         * This preserves general function calls:
         *
         *   foo(20);
         *
         * If foo is a function, this remains a normal expression statement.
         */
        if (call.args.size() == 1) {
            if (call.callee instanceof VarNode calleeVar) {
                VariableSymbol var = ctx.resolve(calleeVar.name);

                /*
                 * If the name is not a variable but is a known function name,
                 * definitely leave it as an ordinary function call expression.
                 */
                if (var == null && ctx.hasAnyFunctionNamed(calleeVar.name)) {
                    return null;
                }

                /*
                 * If the name is neither a variable nor a known function, also
                 * leave it alone. analyzeCallExpr(...) will produce the more
                 * appropriate "No matching overload" diagnostic.
                 */
                if (var == null) {
                    return null;
                }
            }

            SemanticExprNode target = analyzeExpr(call.callee);

            if (isDynamicArrayType(target.type)) {
                requireLValue(target, "array initialization target");

                SemanticExprNode size = analyzeExpr(call.args.get(0));

                return new SemanticArrayInitNode(target, size);
            }

            return null;
        }

        return null;
    }

    // ============================================================
    // Expressions & Inference
    // ============================================================

    private SemanticExprNode analyzeExpr(ASTNode node) {
        if (node instanceof IntNode n) {
            BigInteger val = parseIntegerLiteralSafe(n.value);
            return new SemanticIntLiteralNode(FALLBACK_TYPE, val, n.value);
        }

        if (node instanceof VarNode n) {
            VariableSymbol sym = ctx.resolve(n.name);

            if (sym == null) {
                /*
                 * A bare function name is not currently a first-class value.
                 * It only resolves as a function when it appears as the callee
                 * of a CallNode.
                 */
                if (ctx.hasAnyFunctionNamed(n.name)) {
                    ctx.error("Function name '" + n.name + "' cannot be used as a value");
                } else {
                    ctx.error("Undeclared variable '" + n.name + "'");
                }

                sym = new VariableSymbol(n.name, FALLBACK_TYPE, false);
            }

            return new SemanticVarExprNode(sym.type, sym);
        }

        if (node instanceof UnaryOpNode n) {
            SemanticExprNode expr = analyzeExpr(n.expr);

            SemanticType type = n.op.equals("!") ? BOOL_TYPE : expr.type;

            BigInteger folded = foldUnary(n.op, expr.constantValue);

            return new SemanticUnaryOpNode(type, folded, n.op, expr);
        }

        if (node instanceof BinOpNode n) {
            SemanticExprNode left = analyzeExpr(n.left);
            SemanticExprNode right = analyzeExpr(n.right);

            SemanticType type = inferBinaryResultType(n.op, left, right);

            BigInteger folded = foldBinary(
                    n.op,
                    left.constantValue,
                    right.constantValue
            );

            return new SemanticBinOpNode(type, folded, n.op, left, right);
        }

        if (node instanceof ArrayAccessNode n) {
            SemanticExprNode target = analyzeExpr(n.target);
            SemanticExprNode index = analyzeExpr(n.index);

            SemanticType elemType = extractElementType(target.type);

            return new SemanticArrayAccessNode(elemType, target, index);
        }

        if (node instanceof CallNode n) {
            return analyzeCallExpr(n);
        }

        if (node instanceof MemberAccessNode n) {
            /*
             * Generic field/member expressions are not semantically supported
             * yet. Array intrinsics are handled through CallNode(MemberAccessNode)
             * in tryLowerIntrinsicExprStmt(...).
             *
             * Future struct-field support should produce a real semantic member
             * access node here.
             */
            SemanticExprNode target = analyzeExpr(n.target);
            ctx.error("Unsupported member access expression '"
                    + n.memberName
                    + "' on type "
                    + target.type.describe());

            return new SemanticIntLiteralNode(FALLBACK_TYPE, BigInteger.ZERO, "0");
        }

        if (node instanceof GetAddrNode n) {
            SemanticExprNode target = analyzeRequiredLValueExpr(
                    n.target,
                    "getaddr(...) target"
            );

            return new SemanticGetAddrNode(new SemanticAddrType(target.type), target);
        }

        if (node instanceof DerefNode n) {
            SemanticExprNode expr = analyzeExpr(n.expr);

            SemanticType refType = FALLBACK_TYPE;

            if (expr.type instanceof SemanticAddrType addrT) {
                refType = addrT.referencedType;
            } else {
                ctx.error("Cannot dereference non-address type");
            }

            /*
             * deref(...) is intentionally represented as a dedicated expression
             * node because it produces an lvalue. A normal function call cannot
             * represent that property in the current semantic model.
             */
            return new SemanticDerefNode(refType, expr);
        }

        ctx.error("Unrecognized expression node: " + node.getClass().getSimpleName());

        return new SemanticIntLiteralNode(FALLBACK_TYPE, BigInteger.ZERO, "0");
    }

    private SemanticExprNode analyzeCallExpr(CallNode call) {
        /*
        * User-defined global function call:
        *
        *   foo()
        *   foo(1, x)
        *
        * Raw AST shape:
        *
        *   CallNode(
        *       callee = VarNode("foo"),
        *       args = [...]
        *   )
        */
        if (call.callee instanceof VarNode calleeVar) {
            List<SemanticExprNode> args = new ArrayList<>();
            List<SemanticType> argTypes = new ArrayList<>();

            for (ASTNode arg : call.args) {
                SemanticExprNode loweredArg = analyzeExpr(arg);
                args.add(loweredArg);
                argTypes.add(loweredArg.type);
            }

            FunctionSymbol fn = ctx.resolveFunction(calleeVar.name, args);

            if (fn == null) {
                ctx.error("No matching overload for function call '"
                        + formatCallSignature(calleeVar.name, argTypes)
                        + "'");

                FunctionSymbol fallbackFn = new FunctionSymbol(
                        calleeVar.name,
                        List.of(),
                        FALLBACK_TYPE,
                        false
                );

                return new SemanticFunctionCallNode(
                        FALLBACK_TYPE,
                        fallbackFn,
                        args
                );
            }

            /*
            * A call expression has the selected function's return type, not
            * the function type itself.
            */
            return new SemanticFunctionCallNode(
                    fn.returnType(),
                    fn,
                    args
            );
        }

        if (call.callee instanceof MemberAccessNode mac) {
            ctx.error("Unsupported member call expression '" + mac.memberName + "'");
            return new SemanticIntLiteralNode(FALLBACK_TYPE, BigInteger.ZERO, "0");
        }

        ctx.error("Unsupported call expression");
        return new SemanticIntLiteralNode(FALLBACK_TYPE, BigInteger.ZERO, "0");
    }

    /**
     * Infers the decorated result type for a binary expression.
     *
     * For integer-producing operators, including shifts, if exactly one side is
     * a folded constant and the other side is nonconstant, use the nonconstant
     * side's type.
     *
     * This fixes:
     *
     *   uint8_t x;
     *   uint8_t y;
     *   y = 1 + x;
     *
     * and also means:
     *
     *   uint8_t x;
     *   y = 1 << x;
     *
     * decorates the result as uint8_t.
     *
     * Legality is still checked later by ExpressionChecker/TypeChecker.
     */
    private SemanticType inferBinaryResultType(
            String op,
            SemanticExprNode left,
            SemanticExprNode right
    ) {
        if (BOOL_RESULT_OPS.contains(op)) {
            return BOOL_TYPE;
        }

        if (NON_BOOL_INTEGER_RESULT_OPS.contains(op)) {
            boolean leftConst = left != null && left.isConstant();
            boolean rightConst = right != null && right.isConstant();

            if (leftConst && !rightConst) {
                return right.type;
            }

            if (!leftConst && rightConst) {
                return left.type;
            }

            /*
             * If both sides are constants, keep the historical fallback behavior.
             * The folded BigInteger remains fluid and will be checked later in a
             * contextual position such as assignment.
             *
             * If both sides are nonconstant, the checker will require exact type
             * equality where Franko requires it. Keep left-side mechanical type.
             */
            return left.type;
        }

        return left.type;
    }

    // ============================================================
    // LValue / Structural Semantic Requirements
    // ============================================================

    private SemanticExprNode analyzeRequiredLValueExpr(
            ASTNode node,
            String roleDescription
    ) {
        SemanticExprNode expr = analyzeExpr(node);
        requireLValue(expr, roleDescription);
        return expr;
    }

    private void requireLValue(
            SemanticExprNode expr,
            String roleDescription
    ) {
        if (!isValidStorageBackedLValue(expr)) {
            ctx.error(roleDescription + " must be a storage-backed lvalue");
        }
    }

    /**
     * Stronger than merely calling expr.isLValue().
     *
     * Today, Franko's storage-backed lvalues are:
     *
     *   - variables,
     *   - dereferenced addresses,
     *   - array elements whose target is storage-backed.
     */
    private boolean isValidStorageBackedLValue(SemanticExprNode expr) {
        if (expr == null) {
            return false;
        }

        if (!expr.isLValue()) {
            return false;
        }

        if (expr instanceof SemanticVarExprNode) {
            return true;
        }

        if (expr instanceof SemanticDerefNode) {
            return true;
        }

        if (expr instanceof SemanticArrayAccessNode arrayAccess) {
            return isValidStorageBackedLValue(arrayAccess.target);
        }

        /*
         * Future semantic lvalue nodes, such as field access, can either be
         * explicitly added above or trusted here if their isLValue()
         * implementation is already storage-aware.
         */
        return expr.isLValue();
    }

    private void requireArrayType(
            SemanticExprNode expr,
            String roleDescription
    ) {
        if (!isArrayType(expr.type)) {
            ctx.error(roleDescription + " must have array type, got "
                    + expr.type.describe());
        }
    }

    private void requireDynamicArrayType(
            SemanticExprNode expr,
            String roleDescription
    ) {
        if (!isDynamicArrayType(expr.type)) {
            ctx.error(roleDescription + " must have dynamic array type, got "
                    + expr.type.describe());
        }
    }

    // ============================================================
    // Types Mapping
    // ============================================================

    /**
     * Maps ordinary Franko types.
     *
     * Ordinary types are used in:
     *
     *   - variable declarations,
     *   - parameter declarations,
     *   - array element types,
     *   - address referenced types.
     *
     * void is intentionally not an ordinary type.
     */
    private SemanticType analyzeNormalType(TypeNode node) {
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
            * Canonicalize static array sizes to decimal spelling so equivalent
            * literals produce equal semantic types:
            *
            *   array<int, 2>
            *   array<int, 0x2>
            *   array<int, 0b10>
            */
            BigInteger canonicalSize = parseIntegerLiteralSafe(s.sizeLiteral);

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
            ctx.error("void is not a valid ordinary type");
            return FALLBACK_TYPE;
        }

        ctx.error("Unknown type node.");
        return FALLBACK_TYPE;
    }

    /**
     * Maps function return types.
     *
     * Unlike ordinary types, a function return type may be void.
     */
    private SemanticType analyzeReturnType(TypeNode node) {
        if (node instanceof VoidTypeNode) {
            return VOID_TYPE;
        }

        if (node == null) {
            /*
             * The grammar requires a return type after ->, including void.
             * This defensive branch handles manually-constructed invalid ASTs.
             */
            ctx.error("Function declaration is missing a return type");
            return FALLBACK_TYPE;
        }

        return analyzeNormalType(node);
    }

    private SemanticType extractElementType(SemanticType type) {
        if (type instanceof SemanticDynamicArrayType d) {
            return d.elementType;
        }

        if (type instanceof SemanticStaticArrayType s) {
            return s.elementType;
        }

        ctx.error("Indexed expression is not an array");
        return FALLBACK_TYPE;
    }

    private boolean isArrayType(SemanticType type) {
        return type instanceof SemanticDynamicArrayType
                || type instanceof SemanticStaticArrayType;
    }

    private boolean isDynamicArrayType(SemanticType type) {
        return type instanceof SemanticDynamicArrayType;
    }

    // ============================================================
    // Constant Folding Logic
    // ============================================================

    private BigInteger parseIntegerLiteralSafe(String val) {
        try {
            String s = val.trim();

            if (s.startsWith("+")) {
                s = s.substring(1);
            }

            if (s.toLowerCase().startsWith("0b")) {
                return new BigInteger(s.substring(2), 2);
            }

            if (s.toLowerCase().startsWith("0x")) {
                return new BigInteger(s.substring(2), 16);
            }

            return new BigInteger(s, 10);
        } catch (Exception e) {
            ctx.error("Invalid integer literal format: " + val);
            return BigInteger.ZERO;
        }
    }

    private BigInteger foldUnary(String op, BigInteger val) {
        if (val == null) {
            return null;
        }

        return switch (op) {
            case "-" -> val.negate();
            case "!" -> val.signum() == 0 ? BigInteger.ONE : BigInteger.ZERO;
            default -> null;
        };
    }

    private BigInteger foldBinary(String op, BigInteger left, BigInteger right) {
        if (left == null || right == null) {
            return null;
        }

        try {
            return switch (op) {
                case "+" -> left.add(right);
                case "-" -> left.subtract(right);
                case "*" -> left.multiply(right);
                case "/" -> right.signum() == 0 ? null : left.divide(right);

                case "&" -> left.and(right);
                case "|" -> left.or(right);
                case "^" -> left.xor(right);

                case "<<" -> {
                    if (right.signum() < 0) {
                        yield null;
                    }

                    try {
                        yield left.shiftLeft(right.intValueExact());
                    } catch (ArithmeticException ex) {
                        yield null;
                    }
                }

                case ">>" -> {
                    if (right.signum() < 0) {
                        yield null;
                    }

                    try {
                        yield left.shiftRight(right.intValueExact());
                    } catch (ArithmeticException ex) {
                        yield null;
                    }
                }

                case "==" -> left.equals(right) ? BigInteger.ONE : BigInteger.ZERO;
                case "!=" -> !left.equals(right) ? BigInteger.ONE : BigInteger.ZERO;
                case "<" -> left.compareTo(right) < 0 ? BigInteger.ONE : BigInteger.ZERO;
                case ">" -> left.compareTo(right) > 0 ? BigInteger.ONE : BigInteger.ZERO;
                case "<=" -> left.compareTo(right) <= 0 ? BigInteger.ONE : BigInteger.ZERO;
                case ">=" -> left.compareTo(right) >= 0 ? BigInteger.ONE : BigInteger.ZERO;

                case "&&" ->
                        left.signum() != 0 && right.signum() != 0
                                ? BigInteger.ONE
                                : BigInteger.ZERO;

                case "||" ->
                        left.signum() != 0 || right.signum() != 0
                                ? BigInteger.ONE
                                : BigInteger.ZERO;

                default -> null;
            };
        } catch (ArithmeticException e) {
            return null;
        }
    }

    // ============================================================
    // Formatting Helpers
    // ============================================================

    private String formatCallSignature(String name, List<SemanticType> argTypes) {
        StringBuilder sb = new StringBuilder();

        sb.append(name).append("(");

        for (int i = 0; i < argTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(argTypes.get(i).describe());
        }

        sb.append(")");

        return sb.toString();
    }
}