import java.math.BigInteger;
import java.util.*;

/**
 * ============================================================================
 * EXPRESSION ANALYZER
 * ============================================================================
 *
 * PURPOSE
 *
 * ExpressionAnalyzer lowers parser expression AST nodes into
 * SemanticExprNode trees.
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS PHASE DOES
 * ----------------------------------------------------------------------------
 *
 * This phase is responsible for:
 *
 *   - resolving variable references to VariableSymbol,
 *   - resolving function calls to FunctionSymbol overloads,
 *   - inferring expression result types,
 *   - attaching constant folding metadata (BigInteger),
 *   - lowering structural constructs:
 *       - array indexing
 *       - unary and binary operations
 *       - address-of and dereference
 *   - validating structural requirements for lvalues where needed.
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS PHASE DOES NOT DO
 * ----------------------------------------------------------------------------
 *
 * This phase intentionally does NOT validate:
 *
 *   - assignment compatibility,
 *   - operator type legality,
 *   - function argument correctness,
 *   - return type correctness,
 *   - bounds safety,
 *   - dynamic array initialization correctness,
 *   - delete legality,
 *   - full constant expression legality.
 *
 * These are handled by later checkers.
 *
 * ----------------------------------------------------------------------------
 * IMPORTANT: ARRAY INITIALIZER LISTS
 * ----------------------------------------------------------------------------
 *
 * Array initializer lists MUST NOT reach this phase.
 *
 * They are eliminated by the desugarer into indexed assignments:
 *
 *     xs = [1,2,3]
 *
 * becomes:
 *
 *     xs[0] = 1
 *     xs[1] = 2
 *     xs[2] = 3
 *
 * Encountering ArrayLiteralNode here indicates a compiler pipeline bug.
 *
 */
public final class ExpressionAnalyzer {

    private final SymbolTable ctx;
    private final ConstExpressionEvaluator constEval;

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

    public ExpressionAnalyzer(
            SymbolTable ctx,
            ConstExpressionEvaluator constEval
    ) {
        this.ctx = Objects.requireNonNull(ctx);
        this.constEval = Objects.requireNonNull(constEval);
    }

    // ============================================================
    // Public Expression Lowering API
    // ============================================================

    public SemanticExprNode analyzeExpr(ASTNode node) {
        if (node instanceof IntNode n) {
            BigInteger val = constEval.parseIntegerLiteralSafe(n.value);
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

            BigInteger folded = constEval.foldUnary(
                    n.op,
                    expr.constantValue
            );

            return new SemanticUnaryOpNode(
                    type,
                    folded,
                    n.op,
                    expr
            );
        }

        if (node instanceof BinOpNode n) {
            SemanticExprNode left = analyzeExpr(n.left);
            SemanticExprNode right = analyzeExpr(n.right);

            SemanticType type = inferBinaryResultType(
                    n.op,
                    left,
                    right
            );

            BigInteger folded = constEval.foldBinary(
                    n.op,
                    left.constantValue,
                    right.constantValue
            );

            return new SemanticBinOpNode(
                    type,
                    folded,
                    n.op,
                    left,
                    right
            );
        }

        if (node instanceof ArrayAccessNode n) {
            SemanticExprNode target = analyzeExpr(n.target);
            SemanticExprNode index = analyzeExpr(n.index);

            SemanticType elemType = extractElementType(target.type);

            return new SemanticArrayAccessNode(
                    elemType,
                    target,
                    index
            );
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

        if (node instanceof ArrayLiteralNode) {
            throw new RuntimeException(
                    "ArrayLiteralNode should not reach ExpressionAnalyzer. Desugarer bug."
            );
        }

        ctx.error("Unrecognized expression node: "
                + node.getClass().getSimpleName());

        return new SemanticIntLiteralNode(
                FALLBACK_TYPE,
                BigInteger.ZERO,
                "0"
        );
    }

    // ============================================================
    // Const Expression Helpers
    // ============================================================

    public SemanticExprNode analyzeConstExprNode(
            ASTNode expr,
            String roleDescription
    ) {
        if (expr == null) {
            return new SemanticIntLiteralNode(
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        return analyzeExpr(expr);
    }

    public BigInteger analyzeConstExprValue(
            ASTNode expr,
            String roleDescription
    ) {
        SemanticExprNode lowered = analyzeConstExprNode(
                expr,
                roleDescription
        );

        return lowered.constantValue != null
                ? lowered.constantValue
                : BigInteger.ZERO;
    }

    // ============================================================
    // LValue Requirements
    // ============================================================

    public SemanticExprNode analyzeRequiredLValueExpr(
            ASTNode node,
            String roleDescription
    ) {
        SemanticExprNode expr = analyzeExpr(node);
        requireLValue(expr, roleDescription);
        return expr;
    }

    public void requireLValue(
            SemanticExprNode expr,
            String roleDescription
    ) {
        if (!isValidStorageBackedLValue(expr)) {
            ctx.error(roleDescription + " must be a storage-backed lvalue");
        }
    }

    public void requireArrayType(
            SemanticExprNode expr,
            String roleDescription
    ) {
        if (!isArrayType(expr.type)) {
            ctx.error(roleDescription + " must have array type, got "
                    + expr.type.describe());
        }
    }

    public void requireDynamicArrayType(
            SemanticExprNode expr,
            String roleDescription
    ) {
        if (!isDynamicArrayType(expr.type)) {
            ctx.error(roleDescription + " must have dynamic array type, got "
                    + expr.type.describe());
        }
    }

    public boolean isArrayType(SemanticType type) {
        return type instanceof SemanticDynamicArrayType
                || type instanceof SemanticStaticArrayType;
    }

    public boolean isDynamicArrayType(SemanticType type) {
        return type instanceof SemanticDynamicArrayType;
    }

    private boolean isValidStorageBackedLValue(SemanticExprNode expr) {
        if (expr == null || !expr.isLValue()) {
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

        return expr.isLValue();
    }

    // ============================================================
    // Call Lowering
    // ============================================================

    private SemanticExprNode analyzeCallExpr(CallNode call) {
        if (call.callee instanceof VarNode calleeVar) {
            List<SemanticExprNode> args = new ArrayList<>();
            List<SemanticType> argTypes = new ArrayList<>();

            for (ASTNode arg : call.args) {
                SemanticExprNode loweredArg = analyzeExpr(arg);
                args.add(loweredArg);
                argTypes.add(loweredArg.type);
            }

            FunctionSymbol fn = ctx.resolveFunction(
                    calleeVar.name,
                    args
            );

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

    // ============================================================
    // Type Helpers
    // ============================================================

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

            if (leftConst && !rightConst) return right.type;
            if (!leftConst && rightConst) return left.type;

            return left.type;
        }

        return left.type;
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

    // ============================================================
    // Formatting
    // ============================================================

    private String formatCallSignature(
            String name,
            List<SemanticType> argTypes
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append(name).append("(");

        for (int i = 0; i < argTypes.size(); i++) {
            if (i > 0) sb.append(", ");

            SemanticType type = argTypes.get(i);
            sb.append(type == null ? "<null>" : type.describe());
        }

        sb.append(")");

        return sb.toString();
    }
}