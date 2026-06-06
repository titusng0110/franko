import java.math.BigInteger;
import java.util.Set;

/**
 * ============================================================================
 * EXPRESSION CHECKER
 * ============================================================================
 *
 * PURPOSE:
 * ExpressionChecker validates expression-level legality rules on the fully
 * lowered and type-decorated Semantic AST.
 *
 * Important behavior in this version:
 *
 *   - Binary expression result typing may use the nonconstant side when one
 *     side is a fluid constant.
 *
 *   - For shifts, this means:
 *
 *         1 << x
 *
 *     may be decorated with x's type.
 *
 *   - However, RHS shift rules remain strict:
 *
 *         x << y
 *
 *     requires y to be unsigned integer if y is nonconstant.
 *
 *   - Constant fitting is still enforced:
 *
 *         x << 256
 *
 *     must be rejected if 256 does not fit the required unsigned shift-count
 *     type.
 *
 *   - Additional check added here:
 *
 *         999 << uint8Var
 *
 *     rejects if 999 does not fit uint8_t, because Option A result typing uses
 *     the nonconstant side as the contextual type when the other side is a
 *     literal/fluid constant.
 *
 * ============================================================================
 */
public class ExpressionChecker {
    private static final Set<String> LOGICAL_OPS =
            Set.of("&&", "||");

    private static final Set<String> COMPARISON_OPS =
            Set.of("==", "!=", "<", ">", "<=", ">=");

    private static final Set<String> SHIFT_OPS =
            Set.of("<<", ">>");

    private static final Set<String> ARITHMETIC_OPS =
            Set.of("+", "-", "*", "/");

    private static final Set<String> BITWISE_OPS =
            Set.of("&", "|", "^");

    private final SemanticAnalyzer.Context ctx;
    private final TypeChecker types;

    public ExpressionChecker(
            SemanticAnalyzer.Context ctx,
            TypeChecker types
    ) {
        this.ctx = ctx;
        this.types = types;
    }

    /**
     * Validates a semantic expression node.
     *
     * This method assumes type inference has already happened. It does not
     * compute expression types; it only checks whether the already-inferred
     * expression is legal according to Franko's rules.
     */
    public void checkExpr(SemanticExprNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticIntLiteralNode) {
            return;
        }

        if (node instanceof SemanticVarExprNode n) {
            visitVarExpr(n);
            return;
        }

        if (node instanceof SemanticUnaryOpNode n) {
            visitUnaryOp(n);
            return;
        }

        if (node instanceof SemanticBinOpNode n) {
            visitBinOp(n);
            return;
        }

        if (node instanceof SemanticArrayAccessNode n) {
            visitArrayAccess(n);
            return;
        }

        if (node instanceof SemanticGetAddrNode n) {
            visitGetAddr(n);
            return;
        }

        if (node instanceof SemanticDerefNode n) {
            visitDeref(n);
            return;
        }

        ctx.error("Unknown semantic expression node: "
                + node.getClass().getSimpleName());
    }

    // ============================================================
    // Basic Expressions
    // ============================================================

    private void visitVarExpr(SemanticVarExprNode node) {
        if (node.symbol.deleted) {
            ctx.error("Use of deleted variable '" + node.symbol.name + "'");
        }
    }

    private void visitUnaryOp(SemanticUnaryOpNode node) {
        checkExpr(node.expr);

        switch (node.op) {
            case "!" -> {
                types.ensureIntegral(
                        node.expr.type,
                        "Unary '!' requires an integer operand"
                );
            }

            case "-" -> {
                types.ensureIntegral(
                        node.expr.type,
                        "Unary '-' requires an integer operand"
                );

                /*
                 * If the operand is a folded constant, the analyzer already
                 * folded the negated BigInteger value into this node.
                 *
                 * Do not range-check here. The result remains a fluid constant
                 * until assigned to or otherwise checked against a contextual
                 * concrete integer type.
                 */
            }

            default -> {
                ctx.error("Unknown unary operator '" + node.op + "'");
            }
        }
    }

    // ============================================================
    // Binary Operators
    // ============================================================

    private void visitBinOp(SemanticBinOpNode node) {
        checkExpr(node.left);
        checkExpr(node.right);

        String op = node.op;

        if (op.equals("/")
                && node.right.isConstant()
                && BigInteger.ZERO.equals(node.right.constantValue)) {
            ctx.error("Division by zero in compile-time constant expression");
        }

        /*
         * Logical operators:
         *
         *   && ||
         *
         * Franko rules:
         *
         *   - operands must be integer expressions,
         *   - mixed integer types are allowed,
         *   - if one side is a constant, it must fit the other side's type,
         *   - result is uint8_t, already assigned by SemanticAnalyzer.
         */
        if (LOGICAL_OPS.contains(op)) {
            types.ensureMixedIntegralOperandsWithConstantFit(
                    node.left,
                    node.right,
                    op
            );
            return;
        }

        /*
         * Comparison operators:
         *
         *   == != < > <= >=
         */
        if (COMPARISON_OPS.contains(op)) {
            visitComparison(node, op);
            return;
        }

        /*
         * Shift operators:
         *
         *   << >>
         *
         * Franko rules:
         *
         *   - lhs must be an integer expression,
         *   - rhs must be an integer expression,
         *   - nonconstant rhs must be unsigned integer type,
         *   - constant rhs must be >= 0,
         *   - constant rhs must fit the unsigned variant of lhs type,
         *   - result type is assigned by SemanticAnalyzer.
         *
         * Requested Option A:
         *
         *   If exactly one side is a literal/fluid constant, the analyzer
         *   infers the result type from the nonconstant side.
         *
         * Extra checker rule here:
         *
         *   If lhs is constant and rhs is nonconstant, require lhs constant to
         *   fit rhs's concrete primitive type.
         */
        if (SHIFT_OPS.contains(op)) {
            types.ensureValidShiftOperands(
                    node.left,
                    node.right,
                    op
            );

            ensureLeftShiftLiteralFitsRightSideWhenNeeded(node, op);

            return;
        }

        /*
         * Arithmetic operators:
         *
         *   + - * /
         */
        if (ARITHMETIC_OPS.contains(op)) {
            types.ensureSameIntegralTypeOrFit(
                    node.left,
                    node.right,
                    op
            );
            return;
        }

        /*
         * Bitwise operators:
         *
         *   & | ^
         */
        if (BITWISE_OPS.contains(op)) {
            types.ensureSameIntegralTypeOrFit(
                    node.left,
                    node.right,
                    op
            );
            return;
        }

        ctx.error("Unknown binary operator '" + op + "'");
    }

    private void visitComparison(
            SemanticBinOpNode node,
            String op
    ) {
        boolean leftAddr = types.isAddressType(node.left.type);
        boolean rightAddr = types.isAddressType(node.right.type);

        if (leftAddr && rightAddr) {
            if (!types.sameType(node.left.type, node.right.type)) {
                ctx.error("Address comparison '" + op
                        + "' requires identical address types, got "
                        + node.left.type.describe()
                        + " and "
                        + node.right.type.describe());
            }
            return;
        }

        if (leftAddr || rightAddr) {
            ctx.error("Cannot compare address and non-address using '" + op
                    + "', got "
                    + node.left.type.describe()
                    + " and "
                    + node.right.type.describe());
            return;
        }

        types.ensureMixedIntegralOperandsWithConstantFit(
                node.left,
                node.right,
                op
        );
    }

    /**
     * Extra Option-A shift check.
     *
     * Because SemanticAnalyzer now decorates:
     *
     *   1 << x
     *
     * using x's type when x is nonconstant, the left fluid constant should also
     * be required to fit x's concrete primitive type.
     *
     * Example:
     *
     *   uint8_t x;
     *
     *   1 << x;     valid, 1 fits uint8_t
     *   999 << x;   invalid, 999 does not fit uint8_t
     *
     * This does not replace TypeChecker.ensureValidShiftOperands(...). That
     * method should still enforce the normal Franko shift-count rules,
     * especially that the RHS is unsigned when nonconstant.
     */
    private void ensureLeftShiftLiteralFitsRightSideWhenNeeded(
            SemanticBinOpNode node,
            String op
    ) {
        if (!SHIFT_OPS.contains(op)) {
            return;
        }

        if (!node.left.isConstant()) {
            return;
        }

        if (node.right.isConstant()) {
            return;
        }

        if (!(node.right.type instanceof SemanticPrimitiveType rightPrimitive)) {
            /*
             * ensureValidShiftOperands should already report that the RHS is not
             * an integer. Avoid producing a confusing duplicate error here.
             */
            return;
        }

        if (!types.fitsBigIntegerToPrimitive(
                node.left.constantValue,
                rightPrimitive.kind
        )) {
            ctx.error("Left constant operand of shift '" + op
                    + "' does not fit contextual type "
                    + node.right.type.describe());
        }
    }

    // ============================================================
    // Array Access
    // ============================================================

    private void visitArrayAccess(SemanticArrayAccessNode node) {
        checkExpr(node.target);

        types.ensureArrayType(
                node.target.type,
                "Indexed expression must be an array"
        );

        ensureArrayIndexCompatible(
                node.index,
                "Array index"
        );
    }

    // ============================================================
    // Address Expressions
    // ============================================================

    private void visitGetAddr(SemanticGetAddrNode node) {
        checkExpr(node.target);

        if (!isStorageBackedLValue(node.target)) {
            ctx.error("Operand of 'getaddr' must be an addressable storage-backed lvalue");
        }
    }

    private void visitDeref(SemanticDerefNode node) {
        checkExpr(node.expr);

        types.ensureAddressType(
                node.expr.type,
                "Operand of 'deref' must be an address type"
        );
    }

    // ============================================================
    // LValue / Storage Helpers
    // ============================================================

    /**
     * Stronger than a plain node.isLValue() test.
     *
     * Today, Franko's storage-backed lvalues are:
     *
     *   - variables,
     *   - array elements whose target is storage-backed,
     *   - dereferenced addresses.
     */
    private boolean isStorageBackedLValue(SemanticExprNode expr) {
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
            return isStorageBackedLValue(arrayAccess.target);
        }

        /*
         * Future lvalue nodes, such as SemanticMemberAccessNode, should be
         * explicitly added above once their storage semantics are known.
         */
        return false;
    }

    // ============================================================
    // Array Size Helper
    // ============================================================

    /**
     * Checks expressions used as array sizes.
     *
     * Franko rule:
     *
     *   - constant size expressions must be positive and fit uint32_t,
     *   - nonconstant size expressions must have exactly uint32_t type.
     */
    public void ensureArraySizeCompatible(
            SemanticExprNode size,
            String message
    ) {
        checkExpr(size);

        if (size.isConstant()) {
            if (size.constantValue.signum() <= 0) {
                ctx.error(message + ": array size must be greater than zero");
            } else if (!types.fitsBigIntegerToPrimitive(
                    size.constantValue,
                    SemanticPrimitiveKind.UINT32
            )) {
                ctx.error(message + ": array size does not fit in uint32_t");
            }

            return;
        }

        if (!(size.type instanceof SemanticPrimitiveType pt
                && pt.kind == SemanticPrimitiveKind.UINT32)) {
            ctx.error(message + ": expected uint32_t, got "
                    + size.type.describe());
        }
    }

    /**
     * Checks expressions used as array indexes.
     *
     * Franko array index rule:
     *
     *   - constant index expressions must be nonnegative and fit uint32_t,
     *   - nonconstant index expressions must have exactly uint32_t type.
     */
    public void ensureArrayIndexCompatible(
            SemanticExprNode index,
            String message
    ) {
        checkExpr(index);

        if (index == null) {
            ctx.error(message + ": index expression cannot be null");
            return;
        }

        if (index.isConstant()) {
            if (index.constantValue == null) {
                ctx.error(message + ": missing constant index value");
                return;
            }

            if (index.constantValue.signum() < 0) {
                ctx.error(message + ": array index cannot be negative");
                return;
            }

            if (!types.fitsBigIntegerToPrimitive(
                    index.constantValue,
                    SemanticPrimitiveKind.UINT32
            )) {
                ctx.error(message + ": array index does not fit in uint32_t");
            }

            return;
        }

        if (!(index.type instanceof SemanticPrimitiveType pt
                && pt.kind == SemanticPrimitiveKind.UINT32)) {
            ctx.error(message + ": expected uint32_t, got "
                    + types.describeSafe(index.type));
        }
    }
}
