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
 * The SemanticAnalyzer has already:
 *
 *   - resolved variable names to VariableSymbol objects,
 *   - assigned every expression a mechanical SemanticType,
 *   - folded integer constants into BigInteger constantValue fields where
 *     possible,
 *   - lowered intrinsic syntax such as array memset/memcpy/uninit,
 *   - performed structural lvalue checks needed for correct lowering.
 *
 * This checker performs the stricter Franko legality checks for expressions.
 *
 * ----------------------------------------------------------------------------
 * IMPORTANT INTEGER CONSTANT RULE
 * ----------------------------------------------------------------------------
 *
 * Integer literals and folded constant integer expressions are "fluid".
 *
 * Their SemanticType may be the analyzer fallback type, usually int32_t, but
 * that type is not binding during legality checking.
 *
 * Instead:
 *
 *   - constants are represented as BigInteger values,
 *   - when used with a typed nonconstant expression, the constant must fit the
 *     other side's contextual type,
 *   - if both sides are constants, the expression may remain a BigInteger
 *     constant until checked by a later contextual use such as assignment,
 *     array-size checking, or intrinsic argument checking.
 *
 * Examples:
 *
 *   uint8_t x;
 *
 *   x + 1       valid, because 1 fits uint8_t
 *   x + 255     valid, because 255 fits uint8_t
 *   x + 256     invalid, because 256 does not fit uint8_t
 *
 *   x << 7      valid
 *   x << 255    valid
 *   x << 256    invalid, because RHS must fit uint8_t for uint8_t lhs
 *
 *   int32_t y;
 *   y << 9999999999999999999999
 *               invalid, because RHS does not fit uint32_t
 *
 * ----------------------------------------------------------------------------
 * ADDRESS RULES
 * ----------------------------------------------------------------------------
 *
 * Addresses are:
 *
 *   - assignable,
 *   - copyable,
 *   - dereferenceable,
 *   - comparable with compatible address types.
 *
 * Addresses are not integer arithmetic values.
 *
 * Therefore:
 *
 *   - address comparisons are allowed only between identical addr<T> types,
 *   - address arithmetic, bitwise operations, and shifts are rejected,
 *   - deref(expr) requires expr to have addr<T> type,
 *   - getaddr(expr) requires expr to be an addressable storage-backed lvalue.
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
         *   - result is uint8_t/char, already assigned by SemanticAnalyzer.
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
         *
         * Franko rules:
         *
         *   - integer comparisons allow mixed integer types,
         *   - if one integer side is a constant, it must fit the other side's
         *     concrete type,
         *   - address comparisons are allowed only for identical addr<T> types,
         *   - address-vs-integer comparison is invalid,
         *   - result is uint8_t/char, already assigned by SemanticAnalyzer.
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
         *   - nonconstant rhs must be unsigned integer type,
         *   - constant rhs must be >= 0,
         *   - constant rhs must fit the unsigned variant of lhs type,
         *   - result type is lhs type, already assigned by SemanticAnalyzer.
         */
        if (SHIFT_OPS.contains(op)) {
            types.ensureValidShiftOperands(
                node.left,
                node.right,
                op
            );
            return;
        }

        /*
         * Arithmetic operators:
         *
         *   + - * /
         *
         * Franko rules:
         *
         *   - operands must be integers,
         *   - nonconstant operands must have exactly the same integer type,
         *   - fluid constants must fit the other side's concrete type,
         *   - addresses and arrays are rejected because they are not integers.
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
         *
         * Franko rules:
         *
         *   - operands must be integers,
         *   - nonconstant operands must have exactly the same integer type,
         *   - fluid constants must fit the other side's concrete type,
         *   - addresses and arrays are rejected because they are not integers.
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
     *
     * This mirrors the SemanticAnalyzer's structural lvalue validation so the
     * MasterChecker remains robust even if a Semantic AST is constructed
     * manually or by a future compiler phase.
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
     * Franko rule currently used by the checker:
     *
     *   - constant size expressions must be nonnegative and fit uint32_t,
     *   - nonconstant size expressions must have exactly uint32_t type.
     *
     * This method is used by StatementChecker for dynamic array init.
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
     *
     * This matches the generated C++ runtime representation where array indexing
     * operators take uint32_t indexes.
     *
     * Examples:
     *
     *   arr[0]          valid
     *   arr[123]        valid if 123 fits uint32_t
     *   arr[-1]         invalid
     *   arr[999999999999999999999999] invalid
     *
     *   uint32_t i;
     *   arr[i]          valid
     *
     *   int32_t i;
     *   arr[i]          invalid
     *
     *   uint8_t i;
     *   arr[i]          invalid, even though uint8_t is unsigned;
     *                   nonconstant indexes must be exactly uint32_t.
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