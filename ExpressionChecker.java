import java.math.BigInteger;

/**
 * ExpressionChecker infers expression types and validates expression-level
 * semantics, delegating reusable type logic to TypeChecker.
 *
 * Operator rules implemented:
 *
 *  Logical:
 *    &&, ||, !
 *      - operands: any integer type
 *      - result: uint8_t
 *
 *  Comparison:
 *    <, >, <=, >=, ==, !=
 *      - operands: any integer types, mixed allowed
 *      - result: uint8_t
 *
 *  Bitwise:
 *    &, |, ^
 *      - operands: same integer type
 *      - result: that same type
 *
 *  Arithmetic:
 *    +, -, *, /
 *      - operands: same integer type
 *      - result: that same type
 *
 *  Shift:
 *    <<, >>
 *      - left: any integer type
 *      - right: unsigned integer type
 *      - result: left operand type
 *
 * Arrays are never valid operands for these operators.
 *
 * Integer literal behavior:
 *   - fully literal expressions are compile-time evaluated
 *   - if one side is a literal constant and the other side has a concrete type,
 *     the literal is checked against that concrete type where the operator rules
 *     make that meaningful
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

        // ------------------------------------------------------------
        // Integer literal
        // ------------------------------------------------------------
        if (node instanceof IntNode) {
            /*
             * Raw integer literals still default to int32_t when seen in
             * isolation. Mixed literal/non-literal typing is handled higher
             * up in binary/unary operator logic via compile-time constant
             * detection and fit checks.
             */
            return types.int32Type();
        }

        // ------------------------------------------------------------
        // Variable reference
        // ------------------------------------------------------------
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

        // ------------------------------------------------------------
        // Unary operators
        // ------------------------------------------------------------
        if (node instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) node;

            if ("-".equals(n.op)) {
                BigInteger folded = tryEvaluateIntegerConstant(n);
                if (folded != null) {
                    // Compile-time constant expression. Keep default literal
                    // expression type here; target-fit is checked later.
                    return types.int32Type();
                }

                TypeNode operandType = inferExprType(n.expr);
                types.ensureIntegral(
                        operandType,
                        "Unary '-' requires an integer scalar operand, got "
                                + types.typeToString(operandType)
                );
                return operandType;
            }

            if ("!".equals(n.op)) {
                BigInteger folded = tryEvaluateIntegerConstant(n);
                if (folded != null) {
                    return types.uint8Type();
                }

                TypeNode operandType = inferExprType(n.expr);
                types.ensureIntegral(
                        operandType,
                        "Unary '!' requires an integer scalar operand, got "
                                + types.typeToString(operandType)
                );
                return types.uint8Type();
            }

            TypeNode operandType = inferExprType(n.expr);
            ctx.error("Unsupported unary operator '" + n.op + "'");
            return operandType;
        }

        // ------------------------------------------------------------
        // Binary operators
        // ------------------------------------------------------------
        if (node instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) node;
            String op = n.op;

            BigInteger leftConst = tryEvaluateIntegerConstant(n.left);
            BigInteger rightConst = tryEvaluateIntegerConstant(n.right);
            boolean leftIsConst = leftConst != null;
            boolean rightIsConst = rightConst != null;

            TypeNode left = inferExprType(n.left);
            TypeNode right = inferExprType(n.right);

            // ---------- logical ----------
            
            if ("&&".equals(op) || "||".equals(op)) {
                if (leftIsConst && rightIsConst) {
                    tryEvaluateIntegerConstant(n); // force validation / folding
                    return types.uint8Type();
                }

                types.ensureIntegral(
                        left,
                        "Left operand of '" + op + "' must be an integer scalar, got "
                                + types.typeToString(left)
                );
                types.ensureIntegral(
                        right,
                        "Right operand of '" + op + "' must be an integer scalar, got "
                                + types.typeToString(right)
                );

                // mixed variable/literal adaptation stays
                if (leftIsConst && !rightIsConst) {
                    ensureConstantFitsType(
                            leftConst,
                            right,
                            "Left integer literal of '" + op + "' does not fit type "
                                    + types.typeToString(right)
                    );
                } else if (!leftIsConst && rightIsConst) {
                    ensureConstantFitsType(
                            rightConst,
                            left,
                            "Right integer literal of '" + op + "' does not fit type "
                                    + types.typeToString(left)
                    );
                }

                return types.uint8Type();
            }


            // ---------- comparison ----------
            if ("==".equals(op) || "!=".equals(op)
                || "<".equals(op) || ">".equals(op)
                || "<=".equals(op) || ">=".equals(op)) {

            if (leftIsConst && rightIsConst) {
                tryEvaluateIntegerConstant(n); // force validation / folding
                return types.uint8Type();
            }

            if (!types.areComparable(left, right)) {
                ctx.error(
                        "Operands of comparison '" + op + "' must both be integer scalars; got "
                                + types.typeToString(left) + " and " + types.typeToString(right)
                );
            }

            // mixed variable/literal adaptation stays
            if (leftIsConst && !rightIsConst) {
                ensureConstantFitsType(
                        leftConst,
                        right,
                        "Left integer literal of comparison '" + op + "' does not fit type "
                                + types.typeToString(right)
                );
            } else if (!leftIsConst && rightIsConst) {
                ensureConstantFitsType(
                        rightConst,
                        left,
                        "Right integer literal of comparison '" + op + "' does not fit type "
                                + types.typeToString(left)
                );
            }

            return types.uint8Type();
        }

            // ---------- bitwise ----------
            if ("&".equals(op) || "|".equals(op) || "^".equals(op)) {
                if (leftIsConst && rightIsConst) {
                    tryEvaluateIntegerConstant(n); // force validation / folding
                    return types.int32Type();
                }

                if (leftIsConst && !rightIsConst) {
                    types.ensureIntegral(
                            right,
                            "Right operand of '" + op + "' must be an integer scalar, got "
                                    + types.typeToString(right)
                    );

                    ensureConstantFitsType(
                            leftConst,
                            right,
                            "Left integer literal of '" + op + "' does not fit type "
                                    + types.typeToString(right)
                    );

                    return right;
                }

                if (!leftIsConst && rightIsConst) {
                    types.ensureIntegral(
                            left,
                            "Left operand of '" + op + "' must be an integer scalar, got "
                                    + types.typeToString(left)
                    );

                    ensureConstantFitsType(
                            rightConst,
                            left,
                            "Right integer literal of '" + op + "' does not fit type "
                                    + types.typeToString(left)
                    );

                    return left;
                }

                types.ensureIntegral(
                        left,
                        "Left operand of '" + op + "' must be an integer scalar, got "
                                + types.typeToString(left)
                );
                types.ensureIntegral(
                        right,
                        "Right operand of '" + op + "' must be an integer scalar, got "
                                + types.typeToString(right)
                );

                if (!types.areSameIntegralType(left, right)) {
                    ctx.error(
                            "Operands of '" + op + "' must have the same integer type; got "
                                    + types.typeToString(left) + " and " + types.typeToString(right)
                    );
                }

                return left;
            }


            // ---------- arithmetic ----------
            if ("+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op)) {
                if (leftIsConst && rightIsConst) {
                    tryEvaluateIntegerConstant(n); // force validation / folding
                    return types.int32Type();
                }

                if (leftIsConst && !rightIsConst) {
                    types.ensureIntegral(
                            right,
                            "Right operand of '" + op + "' must be an integer scalar, got "
                                    + types.typeToString(right)
                    );

                    ensureConstantFitsType(
                            leftConst,
                            right,
                            "Left integer literal of '" + op + "' does not fit type "
                                    + types.typeToString(right)
                    );

                    return right;
                }

                if (!leftIsConst && rightIsConst) {
                    types.ensureIntegral(
                            left,
                            "Left operand of '" + op + "' must be an integer scalar, got "
                                    + types.typeToString(left)
                    );

                    ensureConstantFitsType(
                            rightConst,
                            left,
                            "Right integer literal of '" + op + "' does not fit type "
                                    + types.typeToString(left)
                    );

                    return left;
                }

                types.ensureIntegral(
                        left,
                        "Left operand of '" + op + "' must be an integer scalar, got "
                                + types.typeToString(left)
                );
                types.ensureIntegral(
                        right,
                        "Right operand of '" + op + "' must be an integer scalar, got "
                                + types.typeToString(right)
                );

                if (!types.areSameIntegralType(left, right)) {
                    ctx.error(
                            "Operands of '" + op + "' must have the same integer type; got "
                                    + types.typeToString(left) + " and " + types.typeToString(right)
                    );
                }

                return left;
            }

            // ---------- shift ----------
            if ("<<".equals(op) || ">>".equals(op)) {
                if (leftIsConst && rightIsConst) {
                    tryEvaluateIntegerConstant(n); // force validation / folding
                    return types.int32Type();
                }

                // Non-fully-constant case:
                // left must be integral
                // right must be unsigned integral if non-constant
                // if right is a constant literal, only require >= 0 here

                if (!leftIsConst) {
                    types.ensureIntegral(
                            left,
                            "Left operand of '" + op + "' must be an integer scalar, got "
                                    + types.typeToString(left)
                    );
                }

                if (rightIsConst) {
                    if (rightConst.signum() < 0) {
                        ctx.error("Right operand of '" + op + "' must be a non-negative integer literal");
                    }
                } else {
                    types.ensureUnsignedIntegral(
                            right,
                            "Right operand of '" + op + "' must be an unsigned integer scalar, got "
                                    + types.typeToString(right)
                    );
                }

                if (!leftIsConst && rightIsConst) {
                    return left;
                }

                if (leftIsConst && !rightIsConst) {
                    // Keep your current behavior here:
                    // literal lhs remains default int32_t in mixed shift expressions.
                    return types.int32Type();
                }

                return left;
            }


            ctx.error("Unsupported binary operator '" + op + "'");
            return left;
        }

        // ------------------------------------------------------------
        // Array access
        // ------------------------------------------------------------
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
                    "Array index must be an integer scalar, got " + types.typeToString(indexType)
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
     * If expr is a compile-time integer constant expression,
     * ensure it fits the given target type.
     *
     * Non-constant expressions are ignored here.
     */
    public void ensureExprFitsTargetType(ASTNode expr, TypeNode targetType, String messageOnFailure) {
        BigInteger value = tryEvaluateIntegerConstant(expr);
        if (value == null) {
            return;
        }

        ensureConstantFitsType(value, targetType, messageOnFailure);
    }

    /**
     * If expr is a compile-time integer constant expression,
     * ensure it is a valid array size:
     *   - must not be negative
     *   - must fit uint32_t
     *
     * Non-constant expressions are ignored here; callers should separately
     * type-check that the expression is integral.
     */
    public void ensureExprFitsArraySize(ASTNode expr, String messageOnFailure) {
        BigInteger value = tryEvaluateIntegerConstant(expr);
        if (value == null) {
            return;
        }

        if (value.signum() < 0) {
            ctx.error(messageOnFailure + ": array size cannot be negative");
            return;
        }

        if (!types.fitsArraySizeValue(value)) {
            ctx.error(
                    messageOnFailure
                    + ": array size value " + value
                    + " does not fit in uint32_t"
            );
        }
    }

    
    /**
     * Assignment / initialization compatibility rule:
     *
     * - if expr is a compile-time integer constant expression, treat it like an
     *   integer literal and only check whether its value fits the target type
     * - otherwise fall back to ordinary assignability rules
     */
    public void ensureExprAssignableToType(ASTNode expr, TypeNode targetType, String messageOnFailure) {
        if (isCompileTimeIntegerConstantExpr(expr)) {
            ensureExprFitsTargetType(expr, targetType, messageOnFailure);
            return;
        }

        TypeNode valueType = inferExprType(expr);
        types.ensureAssignable(targetType, valueType, messageOnFailure);
    }

    
    public void ensureArraySizeExprCompatible(ASTNode expr, String messageOnFailure) {
        if (isCompileTimeIntegerConstantExpr(expr)) {
            ensureExprFitsArraySize(expr, messageOnFailure);
            return;
        }

        TypeNode sizeType = inferExprType(expr);
        if (!types.sameType(sizeType, types.uint32Type())) {
            ctx.error(messageOnFailure + ": expected uint32_t, got " + types.typeToString(sizeType));
        }
    }


    /**
     * Returns true iff the expression is a compile-time integer constant
     * expression under the current folding rules.
     */
    public boolean isCompileTimeIntegerConstantExpr(ASTNode expr) {
        return tryEvaluateIntegerConstant(expr) != null;
    }

    // ============================================================
    // Compile-time constant evaluation helpers
    // ============================================================

    private int forceInt32(BigInteger value) {
        // Deliberately truncates/wraps to low 32 bits.
        // This is the behavior you said you want for compile-time constant folding.
        return value.intValue();
    }

    private BigInteger boxedInt32(int value) {
        return BigInteger.valueOf(value);
    }

    /**
     * Evaluates an expression as a compile-time integer constant expression.
     *
     * Returns:
     *   - BigInteger value if fully constant
     *   - null if not a constant integer expression
     *
     * Side effect:
     *   emits semantic errors for invalid constant operations
     *   (e.g. division by zero, negative shift counts)
     */
    private BigInteger tryEvaluateIntegerConstant(ASTNode expr) {
        if (expr == null) {
            return null;
        }

        if (expr instanceof IntNode) {
            IntNode n = (IntNode) expr;
            try {
                return types.parseIntegerLiteral(n.value);
            } catch (IllegalArgumentException ex) {
                ctx.error("Invalid integer literal '" + n.value + "'");
                return BigInteger.ZERO;
            }
        }

        if (expr instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) expr;
            BigInteger inner = tryEvaluateIntegerConstant(n.expr);
            if (inner == null) {
                return null;
            }

            if ("-".equals(n.op)) {
                int v = forceInt32(inner);
                return boxedInt32(-v);
            }

            if ("!".equals(n.op)) {
                int v = forceInt32(inner);
                return v == 0 ? BigInteger.ONE : BigInteger.ZERO;
            }

            return null;
        }

        if (expr instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) expr;

            BigInteger left = tryEvaluateIntegerConstant(n.left);
            BigInteger right = tryEvaluateIntegerConstant(n.right);

            if (left == null || right == null) {
                return null;
            }

            return evaluateBinaryConstant(n.op, left, right);
        }

        return null;
    }

    private BigInteger evaluateBinaryConstant(String op, BigInteger left, BigInteger right) {
        int l = forceInt32(left);
        int r = forceInt32(right);

        switch (op) {
            // ---------- arithmetic ----------
            case "+":
                return boxedInt32(l + r);

            case "-":
                return boxedInt32(l - r);

            case "*":
                return boxedInt32(l * r);

            case "/":
                if (r == 0) {
                    ctx.error("Division by zero in compile-time constant expression");
                    return BigInteger.ZERO;
                }
                return boxedInt32(l / r);

            // ---------- bitwise ----------
            case "&":
                return boxedInt32(l & r);

            case "|":
                return boxedInt32(l | r);

            case "^":
                return boxedInt32(l ^ r);

            // ---------- shift ----------
            case "<<":
                // Special rule you requested:
                // check original recursively-evaluated RHS is >= 0, then shift.
                if (right.signum() < 0) {
                    ctx.error("Left shift count cannot be negative in compile-time constant expression");
                    return BigInteger.ZERO;
                }
                return boxedInt32(l << r);

            case ">>":
                if (right.signum() < 0) {
                    ctx.error("Right shift count cannot be negative in compile-time constant expression");
                    return BigInteger.ZERO;
                }
                return boxedInt32(l >> r);

            // ---------- comparison ----------
            case "==":
                return l == r ? BigInteger.ONE : BigInteger.ZERO;

            case "!=":
                return l != r ? BigInteger.ONE : BigInteger.ZERO;

            case "<":
                return l < r ? BigInteger.ONE : BigInteger.ZERO;

            case ">":
                return l > r ? BigInteger.ONE : BigInteger.ZERO;

            case "<=":
                return l <= r ? BigInteger.ONE : BigInteger.ZERO;

            case ">=":
                return l >= r ? BigInteger.ONE : BigInteger.ZERO;

            // ---------- logical ----------
            case "&&":
                return (l != 0 && r != 0) ? BigInteger.ONE : BigInteger.ZERO;

            case "||":
                return (l != 0 || r != 0) ? BigInteger.ONE : BigInteger.ZERO;

            default:
                return null;
        }
    }

    private void ensureConstantFitsType(BigInteger value, TypeNode targetType, String messageOnFailure) {
        if (!(targetType instanceof PrimitiveTypeNode)) {
            ctx.error(messageOnFailure + ": target type is not a primitive integer type");
            return;
        }

        PrimitiveKind kind = ((PrimitiveTypeNode) targetType).kind;
        if (!types.fitsBigIntegerToPrimitive(value, kind)) {
            ctx.error(
                    messageOnFailure
                            + ": value " + value
                            + " does not fit in " + types.typeToString(targetType)
            );
        }
    }
}