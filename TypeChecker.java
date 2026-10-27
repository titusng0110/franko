import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * ============================================================================
 * TYPE CHECKER
 * ============================================================================
 *
 * PURPOSE:
 * TypeChecker provides reusable Franko type-system predicates and validation
 * helpers for the MasterChecker sub-checkers.
 *
 * This class does not traverse the AST by itself. Instead, StatementChecker,
 * ExpressionChecker, FunctionChecker, and DeclarationChecker call into
 * TypeChecker whenever they need to answer questions such as:
 *
 *   - are these two SemanticType objects exactly the same type?
 *   - is this type void?
 *   - is this type an integer primitive?
 *   - is this type an unsigned integer primitive?
 *   - is this type an array?
 *   - is this type an address?
 *   - can this BigInteger constant fit into a target primitive type?
 *   - is this assignment type-compatible?
 *   - is this type valid as a function return type?
 *   - are these operands legal for arithmetic, bitwise, logical, comparison,
 *     or shift operators?
 *
 * ----------------------------------------------------------------------------
 * VOID RULE
 * ----------------------------------------------------------------------------
 *
 * void is not an ordinary value type.
 *
 * It is valid only where Franko explicitly permits it, currently:
 *
 *   - function return types.
 *
 * It is not valid for:
 *
 *   - variables,
 *   - array element types,
 *   - address referenced types,
 *   - assignment values,
 *   - ordinary expression value contexts.
 *
 * ----------------------------------------------------------------------------
 * FUNCTION RETURN TYPE RULE
 * ----------------------------------------------------------------------------
 *
 * A function return type must be either:
 *
 *   - void,
 *   - a primitive integer type,
 *   - an address type addr<T>.
 *
 * Array types cannot be returned directly because arrays are not assignable in
 * Franko. Functions that need to expose arrays should return:
 *
 *   addr<array<T>>
 *   addr<array<T, N>>
 *
 * ----------------------------------------------------------------------------
 * IMPORTANT: FLUID INTEGER CONSTANTS
 * ----------------------------------------------------------------------------
 *
 * Franko integer literals and folded constant integer expressions are fluid.
 *
 * The SemanticAnalyzer stores integer literal values as BigInteger constants
 * and gives literal nodes a fallback SemanticType, typically int32_t. That
 * fallback type is useful for keeping the Semantic AST fully decorated, but it
 * is not treated as a binding source-language type when the expression is a
 * constant.
 *
 * Instead, during checking:
 *
 *   - a constant expression is checked by its BigInteger value;
 *   - when a constant appears next to a nonconstant typed integer expression,
 *     the constant must fit the other side's concrete type;
 *   - when a constant is assigned to a primitive integer target, it must fit
 *     that target type;
 *   - when both operands are constants, the expression may remain a BigInteger
 *     constant until a later contextual check, such as assignment, array size,
 *     intrinsic argument checking, return checking, or call argument checking.
 *
 * ----------------------------------------------------------------------------
 * ADDRESS RULES
 * ----------------------------------------------------------------------------
 *
 * Franko addresses are strongly typed addr<T> values.
 *
 * They are:
 *
 *   - assignable,
 *   - copyable,
 *   - dereferenceable,
 *   - comparable with compatible address types.
 *
 * They are not integer arithmetic values.
 *
 * Therefore:
 *
 *   - addr<T> assignment requires exactly addr<T> on the RHS;
 *   - raw integer constants cannot be assigned to address variables;
 *   - address comparisons require identical addr<T> types;
 *   - arithmetic, bitwise, and shift operators reject addresses because
 *     addresses are not integral types.
 *
 * ============================================================================
 */
public class TypeChecker {
    private final DiagnosticBag diagnostics;

    public TypeChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    // ============================================================
    // Type Equality / Type Structure
    // ============================================================

    /**
     * Returns true if two Franko semantic types are exactly the same type.
     *
     * Notes:
     *
     *   - void equals void;
     *   - primitive types must have the same primitive kind;
     *   - dynamic arrays must have the same element type;
     *   - static arrays must have the same element type and numerically equal
     *     size literals;
     *   - address types must reference the same type;
     *   - function types must have equal parameter types and return type.
     */
    public boolean sameType(SemanticType a, SemanticType b) {
        if (a == null || b == null) {
            return false;
        }

        /*
         * Every SemanticType subclass implements structural equals/hashCode,
         * and SemanticStaticArrayType compares its size numerically after
         * unfolding the constant size expression. equals is therefore the
         * single source of truth for type equality, keeping overload
         * resolution, assignment checking, and diagnostics consistent.
         */
        return a.equals(b);
    }

    public String describeSafe(SemanticType type) {
        return type == null ? "<null>" : type.describe();
    }

    // ============================================================
    // Type Classification
    // ============================================================

    public boolean isVoidType(SemanticType t) {
        return t instanceof SemanticVoidType;
    }

    public boolean isIntegral(SemanticType t) {
        return t instanceof SemanticPrimitiveType;
    }

    public boolean isSignedIntegral(SemanticType t) {
        SemanticPrimitiveKind kind = primitiveKindOf(t);
        return kind != null && isSigned(kind);
    }

    public boolean isUnsignedIntegral(SemanticType t) {
        SemanticPrimitiveKind kind = primitiveKindOf(t);
        return kind != null && !isSigned(kind);
    }

    public boolean isArrayType(SemanticType t) {
        return t instanceof SemanticDynamicArrayType
                || t instanceof SemanticStaticArrayType;
    }

    public boolean isDynamicArrayType(SemanticType t) {
        return t instanceof SemanticDynamicArrayType;
    }

    public boolean isStaticArrayType(SemanticType t) {
        return t instanceof SemanticStaticArrayType;
    }

    public boolean isAddressType(SemanticType t) {
        return t instanceof SemanticAddrType;
    }

    public boolean isNullType(SemanticType t) {
        return t instanceof SemanticNullType;
    }

    /**
     * Returns true if a value of this type can participate in ordinary Franko
     * assignment with '='.
     *
     * Current assignable value types:
     *
     *   - primitive integer types;
     *   - address types;
     *   - struct types whose fields are all assignable value types.
     *
     * Non-assignable:
     *
     *   - void;
     *   - dynamic arrays;
     *   - static arrays;
     *   - function types.
     */
    public boolean isAssignableValueType(SemanticType t) {
        return isAssignableValueType(t, new HashSet<>());
    }

    private boolean isAssignableValueType(
            SemanticType t,
            Set<SemanticType> visited
    ) {
        if (t == null) {
            return false;
        }

        if (t instanceof SemanticPrimitiveType
                || t instanceof SemanticAddrType) {
            return true;
        }

        if (t instanceof SemanticStructType struct) {
            if (!visited.add(t)) {
                return false;
            }

            /*
             * The visited set is path-scoped: entries are removed once their
             * subtree is checked. This keeps diamond-shaped field graphs (the
             * same struct appearing as two sibling fields) assignable while
             * still terminating on by-value cycles.
             */
            boolean assignable = true;

            for (FieldSymbol field : struct.structSymbol.fields) {
                if (!isAssignableValueType(field.type, visited)) {
                    assignable = false;
                    break;
                }
            }

            visited.remove(t);

            return assignable;
        }

        return false;
    }

    /**
     * Valid function return types are:
     *
     *   - void;
     *   - primitive integer types;
     *   - address types;
     *   - struct types whose fields are all assignable value types.
     *
     * Array return types are invalid because arrays are not assignable.
     */
    public boolean isValidFunctionReturnType(SemanticType t) {
        return isVoidType(t) || isAssignableValueType(t);
    }

    public SemanticPrimitiveKind primitiveKindOf(SemanticType type) {
        return type instanceof SemanticPrimitiveType pt ? pt.kind : null;
    }

    public SemanticType elementTypeOfArray(SemanticType type) {
        if (type instanceof SemanticDynamicArrayType d) {
            return d.elementType;
        }

        if (type instanceof SemanticStaticArrayType s) {
            return s.elementType;
        }

        return null;
    }

    public SemanticType referencedTypeOfAddress(SemanticType type) {
        return type instanceof SemanticAddrType a ? a.referencedType : null;
    }

    // ============================================================
    // Basic Ensure Helpers
    // ============================================================

    public void ensureNotVoid(SemanticType t, String message) {
        if (isVoidType(t)) {
            diagnostics.error(message + ", got void");
        }
    }

    public void ensureIntegral(SemanticType t, String message) {
        if (!isIntegral(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureSignedIntegral(SemanticType t, String message) {
        if (!isSignedIntegral(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureUnsignedIntegral(SemanticType t, String message) {
        if (!isUnsignedIntegral(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureArrayType(SemanticType t, String message) {
        if (!isArrayType(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureDynamicArrayType(SemanticType t, String message) {
        if (!isDynamicArrayType(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureStaticArrayType(SemanticType t, String message) {
        if (!isStaticArrayType(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureAddressType(SemanticType t, String message) {
        if (!isAddressType(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureSameType(
            SemanticType expected,
            SemanticType actual,
            String message
    ) {
        if (!sameType(expected, actual)) {
            diagnostics.error(message + ": expected "
                    + describeSafe(expected)
                    + ", got "
                    + describeSafe(actual));
        }
    }

    public void ensureValidFunctionReturnType(
            SemanticType type,
            String message
    ) {
        if (type == null) {
            diagnostics.error(message + ": return type is null");
            return;
        }

        if (isValidFunctionReturnType(type)) {
            return;
        }

        if (isArrayType(type)) {
            diagnostics.error(message + ": arrays cannot be returned directly; use addr<"
                    + type.describe()
                    + "> instead");
            return;
        }

        diagnostics.error(message + ": invalid function return type "
                + describeSafe(type));
    }

    // ============================================================
    // Assignment Compatibility
    // ============================================================

    /**
     * Checks Franko assignment legality.
     *
     * Rules:
     *
     *   1. void cannot participate in assignment.
     *
     *   2. Arrays are not directly assignable.
     *
     *      array<int> a;
     *      array<int> b;
     *      a = b;           invalid
     *
     *      Array operations must use array intrinsics such as init, memcpy,
     *      memset, or uninit.
     *
     *   3. Primitive integer targets accept:
     *
     *      - nonconstant expressions of exactly the same primitive type;
     *      - constant BigInteger expressions if the value fits the target type.
     *
     *      Moving between integer widths is never implicit: use the
     *      explicit cast&lt;T&gt;(expr) intrinsic.
     *
     *   4. Address targets require exact same address type.
     *
     *   5. Other types require exact type equality unless Franko later defines
     *      special conversion rules.
     */
    public void ensureAssignable(
            SemanticType targetType,
            SemanticExprNode expr,
            String message
    ) {
        if (targetType == null || expr == null || expr.type == null) {
            diagnostics.error(message + ": internal null type");
            return;
        }

        if (isVoidType(targetType)) {
            diagnostics.error(message + ": cannot assign to void type");
            return;
        }

        if (isVoidType(expr.type)) {
            diagnostics.error(message + ": cannot assign void value");
            return;
        }

        /*
         * null is only valid for address targets.
         *
         *   addr<int32_t> p;
         *   p = null;            valid
         *
         *   int32_t x;
         *   x = null;            invalid: 0 is not null
         */
        if (expr.type instanceof SemanticNullType) {
            if (!(targetType instanceof SemanticAddrType)) {
                diagnostics.error(message
                        + ": null is only valid for address types, got target "
                        + targetType.describe());
            }
            return;
        }

        if (isArrayType(targetType) || isArrayType(expr.type)) {
            diagnostics.error(message + ": arrays cannot be directly assigned");
            return;
        }

        if (targetType instanceof SemanticStructType
                && !isAssignableValueType(targetType)) {
            diagnostics.error(message + ": struct type "
                    + targetType.describe()
                    + " contains non-assignable fields and cannot be assigned"
                    + " as a whole value");
            return;
        }

        if (targetType instanceof SemanticPrimitiveType targetPrimitive) {
            if (expr.isConstant()) {
                requireAssignableConstant(
                        expr.constantValue,
                        targetPrimitive.kind,
                        targetType,
                        message
                );
                return;
            }

            if (!sameType(targetType, expr.type)) {
                diagnostics.error(message + ": expected "
                        + targetType.describe()
                        + ", got "
                        + expr.type.describe());
            }

            return;
        }

        if (!sameType(targetType, expr.type)) {
            diagnostics.error(message + ": expected "
                    + targetType.describe()
                    + ", got "
                    + expr.type.describe());
        }
    }

    private void requireAssignableConstant(
            BigInteger value,
            SemanticPrimitiveKind kind,
            SemanticType targetType,
            String message
    ) {
        if (!fitsBigIntegerToPrimitive(value, kind)) {
            diagnostics.error(message + ": constant value "
                    + value
                    + " does not fit in "
                    + targetType.describe());
        }
    }

    // ============================================================
    // Operator Helpers: Arithmetic / Bitwise
    // ============================================================

    /**
     * Checks arithmetic and bitwise operand compatibility.
     *
     * Applies to:
     *
     *   + - * / % & | ^
     *
     * and, with the compound operator text ("+=", "/=", "%=", ...), to the
     * compound assignment forms in StatementChecker.
     *
     * Franko rules:
     *
     *   - both operands must be integer expressions;
     *   - arrays, addresses, and void are rejected because they are not
     *     integral;
     *   - if both operands are nonconstant, their types must be exactly equal;
     *   - if exactly one operand is constant, that constant must fit the other
     *     operand's concrete integer type;
     *   - if both operands are constants, allow the expression for now because
     *     the folded BigInteger result is checked later in a contextual position.
     */
    public void ensureSameIntegralTypeOrFit(
            SemanticExprNode left,
            SemanticExprNode right,
            String op
    ) {
        if (!ensureIntegralOperands(left, right, op)) {
            return;
        }

        boolean leftConst = left.isConstant();
        boolean rightConst = right.isConstant();

        if (leftConst && rightConst) {
            return;
        }

        if (leftConst) {
            requireConstantFitsType(
                    left.constantValue,
                    right.type,
                    "Left constant for operator '" + op + "'"
            );
            return;
        }

        if (rightConst) {
            requireConstantFitsType(
                    right.constantValue,
                    left.type,
                    "Right constant for operator '" + op + "'"
            );
            return;
        }

        if (!sameType(left.type, right.type)) {
            diagnostics.error("Operands of '" + op + "' must have the same integer type, got "
                    + left.type.describe()
                    + " and "
                    + right.type.describe());
        }
    }

    // ============================================================
    // Operator Helpers: Logical / Comparison Integer Side
    // ============================================================

    /**
     * Checks integer operands for operators that allow mixed integer types.
     *
     * Applies to the integer side of:
     *
     *   && ||
     *   == != < > <= >=
     *
     * Franko rules:
     *
     *   - operands must be integer expressions;
     *   - mixed nonconstant integer types are allowed;
     *   - if one operand is a constant and the other is nonconstant, the
     *     constant must fit the nonconstant side's concrete integer type;
     *   - if both operands are constants, allow the expression for now;
     *   - if both operands are nonconstant, no same-type requirement applies.
     *
     * Address comparisons are handled separately in ExpressionChecker before
     * this helper is called.
     */
    public void ensureMixedIntegralOperandsWithConstantFit(
            SemanticExprNode left,
            SemanticExprNode right,
            String op
    ) {
        if (!ensureIntegralOperands(left, right, op)) {
            return;
        }

        boolean leftConst = left.isConstant();
        boolean rightConst = right.isConstant();

        if (leftConst && !rightConst) {
            requireConstantFitsType(
                    left.constantValue,
                    right.type,
                    "Left constant for operator '" + op + "'"
            );
            return;
        }

        if (!leftConst && rightConst) {
            requireConstantFitsType(
                    right.constantValue,
                    left.type,
                    "Right constant for operator '" + op + "'"
            );
        }
    }

    private boolean ensureIntegralOperands(
            SemanticExprNode left,
            SemanticExprNode right,
            String op
    ) {
        if (left == null || right == null) {
            diagnostics.error("Operands of '" + op + "' cannot be null");
            return false;
        }

        ensureIntegral(left.type, "Left operand of '" + op + "' must be an integer");
        ensureIntegral(right.type, "Right operand of '" + op + "' must be an integer");

        return isIntegral(left.type) && isIntegral(right.type);
    }

    // ============================================================
    // Operator Helpers: Shift
    // ============================================================

    /**
     * Checks Franko shift operator operands.
     *
     * Applies to:
     *
     *   << >>
     *
     * Rules:
     *
     *   - left operand must be an integer expression;
     *   - right operand must be an integer expression;
     *   - nonconstant right operand must be unsigned integer type;
     *   - constant right operand must be nonnegative;
     *   - constant right operand must fit the unsigned variant of the left
     *     operand's primitive type.
     */
    public void ensureValidShiftOperands(
            SemanticExprNode left,
            SemanticExprNode right,
            String op
    ) {
        if (!ensureIntegralOperands(left, right, op)) {
            return;
        }

        /*
         * If the left side is itself a constant, validate it against its current
         * semantic type. Today that is usually the analyzer's fallback int32_t.
         *
         * Pure constant expressions can still be contextually checked later by
         * assignment or another enclosing operation.
         */
        if (left.isConstant()) {
            requireConstantFitsType(
                    left.constantValue,
                    left.type,
                    "Left constant for operator '" + op + "'"
            );
        }

        if (right.isConstant()) {
            if (right.constantValue == null) {
                diagnostics.error("Right constant for operator '" + op + "' has no constant value");
                return;
            }

            if (right.constantValue.signum() < 0) {
                diagnostics.error("Right operand of '" + op + "' cannot be negative");
                return;
            }

            SemanticPrimitiveKind leftKind = primitiveKindOf(left.type);

            if (leftKind == null) {
                diagnostics.error("Left operand of '" + op + "' must be a primitive integer, got "
                        + describeSafe(left.type));
                return;
            }

            requireConstantFitsPrimitive(
                    right.constantValue,
                    unsignedVariantOf(leftKind),
                    "Right constant for operator '" + op + "'"
            );

            return;
        }

        ensureUnsignedIntegral(
                right.type,
                "Right operand of '" + op + "' must be unsigned"
        );
    }

    public SemanticPrimitiveKind unsignedVariantOf(SemanticPrimitiveKind kind) {
        return switch (kind) {
            case INT8, UINT8 -> SemanticPrimitiveKind.UINT8;
            case INT16, UINT16 -> SemanticPrimitiveKind.UINT16;
            case INT32, UINT32 -> SemanticPrimitiveKind.UINT32;
            case INT64, UINT64 -> SemanticPrimitiveKind.UINT64;
        };
    }

    public SemanticPrimitiveKind signedVariantOf(SemanticPrimitiveKind kind) {
        return switch (kind) {
            case INT8, UINT8 -> SemanticPrimitiveKind.INT8;
            case INT16, UINT16 -> SemanticPrimitiveKind.INT16;
            case INT32, UINT32 -> SemanticPrimitiveKind.INT32;
            case INT64, UINT64 -> SemanticPrimitiveKind.INT64;
        };
    }

    // ============================================================
    // Constant Fit Helpers
    // ============================================================

    public void requireConstantFitsType(
            BigInteger value,
            SemanticType contextType,
            String message
    ) {
        if (!(contextType instanceof SemanticPrimitiveType pt)) {
            diagnostics.error(message + ": expected primitive integer context, got "
                    + describeSafe(contextType));
            return;
        }

        requireConstantFitsPrimitive(value, pt.kind, message);
    }

    public void requireConstantFitsPrimitive(
            BigInteger value,
            SemanticPrimitiveKind kind,
            String message
    ) {
        if (value == null) {
            diagnostics.error(message + ": missing constant value");
            return;
        }

        if (kind == null) {
            diagnostics.error(message + ": missing primitive kind");
            return;
        }

        if (!fitsBigIntegerToPrimitive(value, kind)) {
            diagnostics.error(message + ": constant value "
                    + value
                    + " does not fit in "
                    + kind.name());
        }
    }

    public static boolean fitsBigIntegerToPrimitive(
            BigInteger value,
            SemanticPrimitiveKind kind
    ) {
        if (value == null || kind == null) {
            return false;
        }

        BigInteger two = BigInteger.valueOf(2);
        int bits = bitWidth(kind);

        BigInteger min = isSigned(kind)
                ? two.pow(bits - 1).negate()
                : BigInteger.ZERO;

        BigInteger max = isSigned(kind)
                ? two.pow(bits - 1).subtract(BigInteger.ONE)
                : two.pow(bits).subtract(BigInteger.ONE);

        return value.compareTo(min) >= 0
                && value.compareTo(max) <= 0;
    }

    public static int bitWidth(SemanticPrimitiveKind kind) {
        return switch (kind) {
            case INT8, UINT8 -> 8;
            case INT16, UINT16 -> 16;
            case INT32, UINT32 -> 32;
            case INT64, UINT64 -> 64;
        };
    }

    public static boolean isSigned(SemanticPrimitiveKind kind) {
        return switch (kind) {
            case INT8, INT16, INT32, INT64 -> true;
            case UINT8, UINT16, UINT32, UINT64 -> false;
        };
    }

    /**
     * One-way integer widening: true when every value of the source type is
     * representable in the target type.
     *
     * This is the shared predicate behind cast&lt;T&gt; code selection:
     * ExpressionAnalyzer uses it to decide whether a cast compiles to a
     * plain static_cast (true) or a runtime-checked franko_safe_cast
     * (false). It is static so analysis phases without a TypeChecker
     * instance can use it.
     *
     * Allowed:
     *
     *   - strictly wider, same signedness:
     *     uint8_t -> uint16_t -> uint32_t -> uint64_t,
     *     int8_t -> int16_t -> int32_t -> int64_t;
     *   - unsigned to signed of strictly greater width:
     *     uint8_t -> int16_t, uint16_t -> int32_t, uint32_t -> int64_t.
     *
     * Rejected (same-width, narrowing, or signed-to-unsigned):
     *
     *   uint8_t -> int8_t, int32_t -> uint32_t, int32_t -> uint64_t,
     *   uint64_t -> int64_t, any narrowing.
     *
     * Same-kind pairs return false here; exact equality is handled by
     * sameType at the call site.
     */
    public static boolean isValuePreservingIntegerWidening(
            SemanticPrimitiveKind from,
            SemanticPrimitiveKind to
    ) {
        if (from == null || to == null || from == to) {
            return false;
        }

        if (bitWidth(to) <= bitWidth(from)) {
            return false;
        }

        if (isSigned(from) == isSigned(to)) {
            return true;
        }

        return !isSigned(from) && isSigned(to);
    }

    public static boolean isUnsigned(SemanticPrimitiveKind kind) {
        return !isSigned(kind);
    }

    // ============================================================
    // Memset / Memcpy Element Constraints
    // ============================================================

    /**
     * Returns true if values of this type can be safely used as elements of an
     * array that is memset.
     *
     * Current conservative rule:
     *
     *   - primitive integer elements are memsetable;
     *   - static arrays are memsetable if their element type is memsetable;
     *   - dynamic arrays are not memsetable as element values;
     *   - addresses are not memsetable;
     *   - void is not memsetable.
     */
    public boolean isMemsetable(SemanticType t) {
        return isMemsetable(t, new HashSet<>());
    }

    private boolean isMemsetable(SemanticType t, Set<SemanticType> visited) {
        if (t == null || isVoidType(t)) {
            return false;
        }

        if (t instanceof SemanticPrimitiveType) {
            return true;
        }

        if (t instanceof SemanticStaticArrayType s) {
            return isMemsetable(s.elementType, visited);
        }

        if (t instanceof SemanticStructType struct) {
            /*
             * Path-scoped visited set: diamonds stay memsetable, by-value
             * cycles terminate as false instead of recursing forever.
             */
            if (!visited.add(t)) {
                return false;
            }

            boolean memsetable = true;

            for (FieldSymbol field : struct.structSymbol.fields) {
                if (!isMemsetable(field.type, visited)) {
                    memsetable = false;
                    break;
                }
            }

            visited.remove(t);

            return memsetable;
        }

        return false;
    }

    /**
     * Returns true if values of this type can be copied byte-for-byte by memcpy.
     *
     * Current conservative rule:
     *
     *   - primitive integer elements are memcpyable;
     *   - static arrays are memcpyable if their element type is memcpyable;
     *   - dynamic arrays are not memcpyable as element values;
     *   - addresses are memcpyable;
     *   - void is not memcpyable.
     */
    public boolean isMemcpyable(SemanticType t) {
        return isMemcpyable(t, new HashSet<>());
    }

    private boolean isMemcpyable(SemanticType t, Set<SemanticType> visited) {
        if (t == null || isVoidType(t)) {
            return false;
        }

        if (t instanceof SemanticPrimitiveType
                || t instanceof SemanticAddrType) {
            return true;
        }

        if (t instanceof SemanticStaticArrayType s) {
            return isMemcpyable(s.elementType, visited);
        }

        if (t instanceof SemanticStructType struct) {
            /*
             * Path-scoped visited set: diamonds stay memcpyable, by-value
             * cycles terminate as false instead of recursing forever.
             */
            if (!visited.add(t)) {
                return false;
            }

            boolean memcpyable = true;

            for (FieldSymbol field : struct.structSymbol.fields) {
                if (!isMemcpyable(field.type, visited)) {
                    memcpyable = false;
                    break;
                }
            }

            visited.remove(t);

            return memcpyable;
        }

        return false;
    }

    // ============================================================
    // Array Size / Literal Utilities
    // ============================================================

    public BigInteger parseIntegerLiteral(String val) {
        if (val == null) {
            throw new IllegalArgumentException("null integer literal");
        }

        String s = val.trim();

        if (s.startsWith("+")) {
            s = s.substring(1);
        }

        String lower = s.toLowerCase();

        if (lower.startsWith("0b")) {
            return new BigInteger(s.substring(2), 2);
        }

        if (lower.startsWith("0x")) {
            return new BigInteger(s.substring(2), 16);
        }

        return new BigInteger(s, 10);
    }
}