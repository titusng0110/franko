/**
 * ExpressionChecker infers expression types and validates expression-level
 * semantics, delegating reusable type logic to TypeChecker.
 */
public class ExpressionChecker {
    private final SemanticAnalyzer.Context ctx;
    private final TypeChecker types;

    public ExpressionChecker(SemanticAnalyzer.Context ctx, TypeChecker types) {
        this.ctx = ctx;
        this.types = types;
    }

    /**
     * Small helper record describing an integer literal expression.
     *
     * Examples:
     *   IntNode("123")                  -> ("123", false)
     *   IntNode("0xFF")                 -> ("0xFF", false)
     *   UnaryOpNode("-", IntNode("1"))  -> ("1", true)
     *   UnaryOpNode("-", IntNode("0x80")) -> ("0x80", true)
     */
    private static final class IntegerLiteralInfo {
        final String literalText;
        final boolean negative;

        IntegerLiteralInfo(String literalText, boolean negative) {
            this.literalText = literalText;
            this.negative = negative;
        }
    }

    public TypeNode inferExprType(ASTNode node) {
        if (node == null) {
            ctx.error("Internal error: null expression encountered");
            return types.int32Type();
        }

        if (node instanceof IntNode) {
            // Raw integer literals are preserved textually in the AST.
            // Their actual fit against a target type is checked later in
            // declaration/assignment/array-size contexts.
            return types.int32Type();
        }

        if (node instanceof VarNode) {
            VarNode n = (VarNode) node;
            SemanticAnalyzer.Symbol sym = ctx.resolve(n.name);
            if (sym == null) {
                ctx.error("Use of undeclared variable '" + n.name + "'");
                return types.int32Type();
            }
            if (sym.deleted) {
                ctx.error("Use of deleted variable '" + n.name + "'");
            }
            return sym.type;
        }

        if (node instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) node;
            TypeNode exprType = inferExprType(n.expr);

            if (!"-".equals(n.op)) {
                ctx.error("Unsupported unary operator '" + n.op + "'");
                return exprType;
            }

            types.ensureNumeric(
                    exprType,
                    "Unary '-' requires a numeric scalar operand, got " + types.typeToString(exprType)
            );

            // Keep unary result type simple at this stage.
            // Literal overflow is checked later when the expression is used
            // in a target-typed context.
            return exprType;
        }

        if (node instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) node;
            TypeNode left = inferExprType(n.left);
            TypeNode right = inferExprType(n.right);
            String op = n.op;

            if ("+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op)) {
                types.ensureNumeric(
                        left,
                        "Left operand of '" + op + "' must be numeric scalar, got " + types.typeToString(left)
                );
                types.ensureNumeric(
                        right,
                        "Right operand of '" + op + "' must be numeric scalar, got " + types.typeToString(right)
                );
                return types.numericPromotion(left, right);
            }

            if ("==".equals(op) || "!=".equals(op) || "<".equals(op) || ">".equals(op)
                    || "<=".equals(op) || ">=".equals(op)) {

                if (types.isArrayType(left) || types.isArrayType(right)) {
                    ctx.error(
                            "Comparison operator '" + op + "' does not support array operands: "
                                    + types.typeToString(left) + " and " + types.typeToString(right)
                    );
                } else if (!types.areComparable(left, right)) {
                    ctx.error(
                            "Operands of comparison '" + op + "' are not comparable: "
                                    + types.typeToString(left) + " and " + types.typeToString(right)
                    );
                }

                // No bool type yet; comparisons are treated as int32-like scalar conditions.
                return types.int32Type();
            }

            ctx.error("Unsupported binary operator '" + op + "'");
            return left;
        }

        if (node instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) node;
            TypeNode targetType = inferExprType(n.target);
            TypeNode indexType = inferExprType(n.index);

            types.ensureArrayType(
                    targetType,
                    "Indexed expression must be an array, got " + types.typeToString(targetType)
            );

            types.ensureIntegral(
                    indexType,
                    "Array index must be an integral scalar, got " + types.typeToString(indexType)
            );

            return types.elementTypeOf(targetType);
        }

        ctx.error("Unsupported AST node in expression context: " + node.getClass().getSimpleName());
        return types.int32Type();
    }

    public TypeNode inferLValueType(ASTNode node) {
        if (node instanceof VarNode || node instanceof ArrayAccessNode) {
            return inferExprType(node);
        }

        ctx.error("Left-hand side is not assignable: " + node.getClass().getSimpleName());
        return types.int32Type();
    }

    /**
     * If expr is an integer literal expression (possibly unary-minus),
     * ensure it fits the given target type.
     *
     * Non-literal expressions are ignored here.
     */
    public void ensureExprFitsTargetType(ASTNode expr, TypeNode targetType, String messageOnFailure) {
        IntegerLiteralInfo lit = extractIntegerLiteral(expr);
        if (lit == null) {
            return;
        }

        types.ensureIntegerLiteralFitsType(
                lit.literalText,
                lit.negative,
                targetType,
                messageOnFailure
        );
    }

    /**
     * If expr is an integer literal expression (possibly unary-minus),
     * ensure it is a valid array size:
     *   - must not be negative
     *   - must fit uint32_t
     *
     * Non-literal expressions are ignored here; callers should separately
     * type-check that the expression is integral.
     */
    public void ensureExprFitsArraySize(ASTNode expr, String messageOnFailure) {
        IntegerLiteralInfo lit = extractIntegerLiteral(expr);
        if (lit == null) {
            return;
        }

        if (lit.negative) {
            ctx.error(messageOnFailure + ": array size cannot be negative");
            return;
        }

        types.ensureArraySizeLiteralFitsUint32(
                lit.literalText,
                messageOnFailure
        );
    }

    /**
     * Returns true iff the expression is:
     *   - IntNode(...)
     *   - UnaryOpNode("-", IntNode(...))
     */
    public boolean isIntegerLiteralExpr(ASTNode expr) {
        return extractIntegerLiteral(expr) != null;
    }

    private IntegerLiteralInfo extractIntegerLiteral(ASTNode expr) {
        if (expr instanceof IntNode) {
            IntNode n = (IntNode) expr;
            return new IntegerLiteralInfo(n.value, false);
        }

        if (expr instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) expr;
            if ("-".equals(n.op) && n.expr instanceof IntNode) {
                IntNode inner = (IntNode) n.expr;
                return new IntegerLiteralInfo(inner.value, true);
            }
        }

        return null;
    }
}