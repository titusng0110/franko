import java.math.BigInteger;
import java.util.*;

/**
 * ============================================================================
 * SEMANTIC ANALYZER
 * AST Lowering, Symbol Resolution, Type Decoration, and Structural Validation
 * ============================================================================
 *
 * PURPOSE:
 * SemanticAnalyzer bridges the gap between the raw parser AST and the later
 * legality checkers. It lowers an untyped ASTNode tree into a strongly typed,
 * scope-resolved SemanticASTNode tree.
 *
 * This phase performs the compiler's first semantic pass. It does not try to
 * prove every Franko legality rule. Instead, it builds a decorated semantic
 * tree where:
 *
 *   - variable names are resolved to VariableSymbol objects,
 *   - function calls are resolved to overloaded FunctionSymbol objects,
 *   - parser TypeNode objects become canonical SemanticType objects,
 *   - expressions carry mechanically inferred result types,
 *   - foldable integer expressions carry BigInteger constantValue metadata,
 *   - array storage intrinsics are lowered into dedicated Semantic AST nodes,
 *   - storage-sensitive constructs are shaped enough for later checkers.
 *
 * ----------------------------------------------------------------------------
 * GLOBAL SCOPE RULE
 * ----------------------------------------------------------------------------
 *
 * Global scope currently allows only:
 *
 *   - variable declarations,
 *   - function declarations.
 *
 * No executable statements are allowed globally for now:
 *
 *   - assignments,
 *   - prints,
 *   - function calls,
 *   - if / while,
 *   - return,
 *   - del,
 *   - blocks,
 *   - array intrinsic calls.
 *
 * Execution should be rooted in:
 *
 *   func main() -> int32_t { ... }
 *
 * ----------------------------------------------------------------------------
 * IMPORTANT:
 * ----------------------------------------------------------------------------
 *
 * This analyzer intentionally does not perform every language legality check.
 *
 * Later checkers still validate:
 *
 *   - return; in non-void functions,
 *   - return expr; in void functions,
 *   - return expression compatibility,
 *   - missing return in non-void functions,
 *   - void-returning calls in value contexts,
 *   - assignment compatibility,
 *   - arithmetic/operator validity,
 *   - array intrinsic argument compatibility,
 *   - delete legality.
 */
public class SemanticAnalyzer {

    public static final class SemanticException extends RuntimeException {
        public SemanticException(String message) {
            super(message);
        }
    }

    public static final class Context {
        // ============================================================
        // Scope State
        // ============================================================

        private final Deque<Map<String, VariableSymbol>> scopes =
                new ArrayDeque<>();

        /*
         * Raw global VarDeclNode -> registered global symbol.
         *
         * Used so global declarations are registered before function body
         * analysis, then reused when lowering the final SemanticProgramNode.
         */
        private final IdentityHashMap<VarDeclNode, VariableSymbol> varDeclSymbols =
                new IdentityHashMap<>();

        // ============================================================
        // Function State
        // ============================================================

        /*
         * Function overload table.
         *
         * Declaration identity:
         *
         *   function name + ordered parameter types
         *
         * Parameter names and return type are not part of the signature.
         */
        private final Map<String, List<FunctionSymbol>> functions =
                new LinkedHashMap<>();

        /*
         * Raw FunctionDeclNode -> registered FunctionSymbol.
         *
         * Used for two-phase function analysis:
         *
         *   1. register all signatures,
         *   2. analyze bodies using the exact registered symbol.
         */
        private final IdentityHashMap<FunctionDeclNode, FunctionSymbol> functionDeclSymbols =
                new IdentityHashMap<>();

        // ============================================================
        // Diagnostics
        // ============================================================

        private final List<String> errors = new ArrayList<>();

        void clear() {
            scopes.clear();
            varDeclSymbols.clear();
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

        boolean declare(VariableSymbol sym) {
            Map<String, VariableSymbol> current = scopes.peek();

            if (current == null) {
                throw new IllegalStateException("Internal error: no active scope.");
            }

            if (current.containsKey(sym.name)) {
                error("Duplicate declaration of variable '" + sym.name + "' in the same scope");
                return false;
            }

            current.put(sym.name, sym);
            return true;
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

        FunctionSymbol findRegisteredFunction(FunctionDeclNode decl) {
            return functionDeclSymbols.get(decl);
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

                /*
                 * Return one candidate as fallback so analysis can continue.
                 * The call is already invalid because an error was reported.
                 */
                return matches.get(0);
            }

            return null;
        }

        boolean hasAnyFunctionNamed(String name) {
            List<FunctionSymbol> overloads = functions.get(name);
            return overloads != null && !overloads.isEmpty();
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
             * Folded integer constants are fluid during call resolution.
             *
             * A constant argument may match any primitive integer parameter
             * whose range can represent the constant value.
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

                SemanticType type = argTypes.get(i);
                sb.append(type == null ? "<null>" : type.describe());
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
             * Register its signature first so recursive calls can resolve.
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
         * Phase 0:
         * Validate top-level item kinds.
         */
        for (ASTNode item : program.topLevelItems) {
            if (item instanceof VarDeclNode || item instanceof FunctionDeclNode) {
                continue;
            }

            ctx.error("Invalid global top-level item '"
                    + item.getClass().getSimpleName()
                    + "': only variable declarations and function declarations are allowed at global scope");
        }

        /*
         * Phase 1:
         * Register all global variables before function body analysis.
         *
         * This lets functions reference global variables declared later in the
         * source file.
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

            if (ctx.declare(sym)) {
                ctx.varDeclSymbols.put(n, sym);
            }
        }

        /*
         * Phase 2:
         * Register all function signatures before analyzing any function body.
         *
         * This enables forward references, direct recursion, and mutual
         * recursion.
         */
        for (ASTNode item : program.topLevelItems) {
            if (item instanceof FunctionDeclNode fn) {
                registerFunctionSignature(fn);
            }
        }

        /*
         * Phase 3:
         * Lower allowed top-level items.
         *
         * Invalid top-level items are not lowered.
         */
        List<SemanticASTNode> semanticItems = new ArrayList<>();

        for (ASTNode item : program.topLevelItems) {
            if (item instanceof VarDeclNode n) {
                VariableSymbol sym = ctx.varDeclSymbols.get(n);

                /*
                 * Null means registration failed, usually because this was a
                 * duplicate global variable declaration. The error was already
                 * reported by ctx.declare(...).
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

                continue;
            }

            /*
             * Invalid top-level items were already reported in Phase 0.
             */
        }

        return new SemanticProgramNode(semanticItems);
    }

    /**
     * Registers only the function declaration signature:
     *
     *   - function identifier,
     *   - ordered parameter type list,
     *   - return type metadata.
     *
     * Function declaration identity is:
     *
     *   identifier + ordered parameter type list
     *
     * Parameter names and return type are not part of declaration identity.
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
             * Usually means signature registration failed, for example due to a
             * duplicate function declaration. Build a fallback symbol so the
             * body can still be analyzed and additional errors can be collected.
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
         * scope. ParameterSymbol extends VariableSymbol, so declare the exact
         * ParameterSymbol object.
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
            /*
             * Global variable declarations are pre-registered by analyzeProgram.
             * Local variable declarations are declared here.
             */
            VariableSymbol registered = ctx.varDeclSymbols.get(n);

            if (registered != null) {
                return new SemanticVarDeclNode(registered);
            }

            SemanticType type = analyzeNormalType(n.type);

            VariableSymbol sym = new VariableSymbol(
                    n.name,
                    type,
                    n.isHeap
            );

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

        if (node instanceof FunctionDeclNode fn) {
            ctx.error("Function declaration '"
                    + fn.name
                    + "' is only valid in the global top-level scope");
            return null;
        }

        ctx.error("Unrecognized statement node: "
                + node.getClass().getSimpleName());

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

        return new SemanticReturnNode(currentFunction, value);
    }

    /**
     * Lowers expression-statement forms that are Franko storage intrinsics:
     *
     *   dynArray(size);
     *   dynArray.uninit();
     *   array.memset(value);
     *   array.memcpy(source);
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
         * If the callee is a known function name, leave it as an ordinary call.
         */
        if (call.args.size() == 1) {
            if (call.callee instanceof VarNode calleeVar) {
                VariableSymbol var = ctx.resolve(calleeVar.name);

                if (var == null && ctx.hasAnyFunctionNamed(calleeVar.name)) {
                    return null;
                }

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
            SemanticExprNode target = analyzeExpr(n.target);

            ctx.error("Unsupported member access expression '"
                    + n.memberName
                    + "' on type "
                    + target.type.describe());

            return new SemanticIntLiteralNode(
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        if (node instanceof GetAddrNode n) {
            SemanticExprNode target = analyzeRequiredLValueExpr(
                    n.target,
                    "getaddr(...) target"
            );

            return new SemanticGetAddrNode(
                    new SemanticAddrType(target.type),
                    target
            );
        }

        if (node instanceof DerefNode n) {
            SemanticExprNode expr = analyzeExpr(n.expr);

            SemanticType refType = FALLBACK_TYPE;

            if (expr.type instanceof SemanticAddrType addrT) {
                refType = addrT.referencedType;
            } else {
                ctx.error("Cannot dereference non-address type");
            }

            return new SemanticDerefNode(refType, expr);
        }

        ctx.error("Unrecognized expression node: "
                + node.getClass().getSimpleName());

        return new SemanticIntLiteralNode(
                FALLBACK_TYPE,
                BigInteger.ZERO,
                "0"
        );
    }

    private SemanticExprNode analyzeCallExpr(CallNode call) {
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

            return new SemanticFunctionCallNode(
                    fn.returnType(),
                    fn,
                    args
            );
        }

        if (call.callee instanceof MemberAccessNode mac) {
            ctx.error("Unsupported member call expression '"
                    + mac.memberName
                    + "'");

            return new SemanticIntLiteralNode(
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        ctx.error("Unsupported call expression");

        return new SemanticIntLiteralNode(
                FALLBACK_TYPE,
                BigInteger.ZERO,
                "0"
        );
    }

    /**
     * Infers the decorated result type for a binary expression.
     *
     * For integer-producing operators, if exactly one side is a folded constant
     * and the other side is nonconstant, use the nonconstant side's type.
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
         * Future semantic lvalue nodes, such as field access, can be added here.
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
    // Type Mapping
    // ============================================================

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

    private SemanticType analyzeReturnType(TypeNode node) {
        if (node instanceof VoidTypeNode) {
            return VOID_TYPE;
        }

        if (node == null) {
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
    // Constant Folding
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

    private String formatCallSignature(
            String name,
            List<SemanticType> argTypes
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append(name).append("(");

        for (int i = 0; i < argTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            SemanticType type = argTypes.get(i);
            sb.append(type == null ? "<null>" : type.describe());
        }

        sb.append(")");

        return sb.toString();
    }
}