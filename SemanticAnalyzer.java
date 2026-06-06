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
 *   - types are mapped into canonical SemanticType objects,
 *   - expressions carry mechanically inferred result types,
 *   - foldable integer expressions carry constantValue metadata,
 *   - syntactic sugar / intrinsic call forms are lowered into dedicated
 *     Semantic AST nodes,
 *   - and storage-sensitive constructs are checked well enough to ensure that
 *     the lowered tree has a meaningful shape.
 */
public class SemanticAnalyzer {

    public static final class SemanticException extends RuntimeException {
        public SemanticException(String message) {
            super(message);
        }
    }

    public static final class Context {
        private final Deque<Map<String, VariableSymbol>> scopes = new ArrayDeque<>();
        private final List<String> errors = new ArrayList<>();

        void clear() {
            scopes.clear();
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

    private static final SemanticType FALLBACK_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.INT32);

    private static final SemanticType BOOL_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8);

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
     * Entry point: Lowers an ASTNode into a SemanticASTNode.
     */
    public SemanticASTNode analyze(ASTNode root) {
        ctx.clear();
        ctx.pushScope();

        SemanticASTNode result;

        if (root instanceof ProgramNode p) {
            List<SemanticStmtNode> semanticStmts = new ArrayList<>();

            for (ASTNode stmt : p.statements) {
                SemanticStmtNode lowered = analyzeStmt(stmt);
                if (lowered != null) {
                    semanticStmts.add(lowered);
                }
            }

            result = new SemanticProgramNode(semanticStmts);
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
            SemanticType type = analyzeType(n.type);
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

        if (node instanceof ExprStmtNode n) {
            SemanticStmtNode intrinsic = tryLowerIntrinsicExprStmt(n.expr);

            if (intrinsic != null) {
                return intrinsic;
            }

            return new SemanticExprStmtNode(analyzeExpr(n.expr));
        }

        ctx.error("Unrecognized statement node: " + node.getClass().getSimpleName());
        return null;
    }

    /**
     * Lowers expression-statement forms that are actually Franko intrinsics:
     *
     *   target(size)
     *   target.uninit()
     *   target.memset(value)
     *   target.memcpy(source)
     *
     * Important fix:
     *
     *   Dynamic array initialization is now lvalue-based:
     *
     *       arr(20);
     *       deref(p)(20);
     *
     *   The target must be an array-typed storage-backed lvalue.
     *
     *   The SemanticArrayInitNode must therefore store:
     *
     *       SemanticExprNode target
     *
     *   instead of:
     *
     *       VariableSymbol array
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

                requireArrayType(receiver, "uninit receiver");

                return new SemanticArrayUninitNode(receiver);
            }

            return null;
        }

        /*
         * Lvalue-based dynamic array initialization:
         *
         *   arr(20)
         *   deref(p)(20)
         *   arrs[i](20)
         *
         * We only lower this form when the callee expression is array-typed.
         *
         * This preserves future room for general function calls. For example,
         * if foo(20) is later valid function-call syntax, it will not have been
         * mis-lowered as array initialization unless foo is array-typed.
         */
        if (call.args.size() == 1) {
            SemanticExprNode target = analyzeExpr(call.callee);

            if (isArrayType(target.type)) {
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
                ctx.error("Undeclared variable '" + n.name + "'");
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

            return new SemanticDerefNode(refType, expr);
        }

        ctx.error("Unrecognized expression node: " + node.getClass().getSimpleName());

        return new SemanticIntLiteralNode(FALLBACK_TYPE, BigInteger.ZERO, "0");
    }

    /**
     * Infers the decorated result type for a binary expression.
     *
     * Important change:
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
     * and, by requested Option A, also means:
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

    // ============================================================
    // Types Mapping
    // ============================================================

    private SemanticType analyzeType(TypeNode node) {
        if (node instanceof PrimitiveTypeNode p) {
            return new SemanticPrimitiveType(
                    SemanticPrimitiveKind.valueOf(p.kind.name())
            );
        }

        if (node instanceof DynamicArrayTypeNode d) {
            return new SemanticDynamicArrayType(analyzeType(d.elementType));
        }

        if (node instanceof StaticArrayTypeNode s) {
            return new SemanticStaticArrayType(
                    analyzeType(s.elementType),
                    s.sizeLiteral
            );
        }

        if (node instanceof AddrTypeNode a) {
            return new SemanticAddrType(analyzeType(a.referencedType));
        }

        ctx.error("Unknown type node.");
        return FALLBACK_TYPE;
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
}
