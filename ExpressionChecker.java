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

    public TypeNode inferExprType(ASTNode node) {
        if (node == null) {
            ctx.error("Internal error: null expression encountered");
            return types.int32Type();
        }

        if (node instanceof IntNode) {
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
            types.ensureNumeric(exprType,
                    "Unary '-' requires a numeric scalar operand, got " + types.typeToString(exprType));
            return types.numericPromotion(exprType, null);
        }

        if (node instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) node;
            TypeNode left = inferExprType(n.left);
            TypeNode right = inferExprType(n.right);
            String op = n.op;

            if ("+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op)) {
                types.ensureNumeric(left,
                        "Left operand of '" + op + "' must be numeric scalar, got " + types.typeToString(left));
                types.ensureNumeric(right,
                        "Right operand of '" + op + "' must be numeric scalar, got " + types.typeToString(right));
                return types.numericPromotion(left, right);
            }

            if ("==".equals(op) || "!=".equals(op) || "<".equals(op) || ">".equals(op)
                    || "<=".equals(op) || ">=".equals(op)) {
                if (types.isArrayType(left) || types.isArrayType(right)) {
                    ctx.error("Comparison operator '" + op + "' does not support array operands: "
                            + types.typeToString(left) + " and " + types.typeToString(right));
                } else if (!types.areComparable(left, right)) {
                    ctx.error("Operands of comparison '" + op + "' are not comparable: "
                            + types.typeToString(left) + " and " + types.typeToString(right));
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

            types.ensureArrayType(targetType,
                    "Indexed expression must be an array, got " + types.typeToString(targetType));
            types.ensureIntegral(indexType,
                    "Array index must be an integral scalar, got " + types.typeToString(indexType));

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
}
