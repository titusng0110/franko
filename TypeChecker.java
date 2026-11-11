import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class TypeChecker {
    private final DiagnosticBag diagnostics;

    public TypeChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public static boolean sameType(SastType a, SastType b) {
        if (a == null || b == null) {
            return false;
        }

        return a.equals(b);
    }

    public String describeSafe(SastType type) {
        return type == null ? "<null>" : type.describe();
    }

    public boolean isVoidType(SastType t) {
        return t.kind == SastTypeKind.VOID;
    }

    public boolean isIntegral(SastType t) {
        return t.kind == SastTypeKind.PRIMITIVE;
    }

    public boolean isEnumType(SastType t) {
        return t.kind == SastTypeKind.ENUM;
    }

    public SemanticPrimitiveKind enumBackingKind(SastType t) {
        return t.kind == SastTypeKind.ENUM ? t.enumSymbol.backingKind : null;
    }

    public boolean isValuePreservingEnumToIntegerCast(SastType enumType, SemanticPrimitiveKind targetKind) {
        if (enumType == null || targetKind == null) {
            return false;
        }

        SemanticPrimitiveKind backingKind = enumType.enumSymbol.backingKind;

        if (backingKind == targetKind) {
            return true;
        }

        return isValuePreservingIntegerWidening(backingKind, targetKind);
    }

    public static BigInteger wrapBigIntegerToPrimitive(BigInteger value, SemanticPrimitiveKind kind) {
        if (value == null || kind == null) {
            return BigInteger.ZERO;
        }

        int bits = bitWidth(kind);
        BigInteger modulus = BigInteger.valueOf(2).pow(bits);
        BigInteger wrapped = value.mod(modulus);

        if (isSigned(kind)) {
            BigInteger half = BigInteger.valueOf(2).pow(bits - 1);

            if (wrapped.compareTo(half) >= 0) {
                wrapped = wrapped.subtract(modulus);
            }
        }

        return wrapped;
    }

    public boolean isSignedIntegral(SastType t) {
        SemanticPrimitiveKind kind = primitiveKindOf(t);
        return kind != null && isSigned(kind);
    }

    public boolean isUnsignedIntegral(SastType t) {
        SemanticPrimitiveKind kind = primitiveKindOf(t);
        return kind != null && !isSigned(kind);
    }

    public boolean isArrayType(SastType t) {
        return t.kind == SastTypeKind.DYNAMIC_ARRAY
                || t.kind == SastTypeKind.STATIC_ARRAY;
    }

    public boolean isDynamicArrayType(SastType t) {
        return t.kind == SastTypeKind.DYNAMIC_ARRAY;
    }

    public boolean isStaticArrayType(SastType t) {
        return t.kind == SastTypeKind.STATIC_ARRAY;
    }

    public boolean isPointerType(SastType t) {
        return t.kind == SastTypeKind.PTR;
    }

    public boolean containsOwnedPointer(SastType type) {
        return containsOwnedPointer(type, new HashSet<>());
    }

    private boolean containsOwnedPointer(
            SastType type,
            Set<SastType> visiting
    ) {
        if (type == null) {
            return false;
        }

        if (!visiting.add(type)) {
            return false;
        }

        try {
            if (type.kind == SastTypeKind.PTR) {
                if (type.permission == PtrPermission.OWNER) {
                    return true;
                }

                return containsOwnedPointer(type.referencedType, visiting);
            }

            if (type.kind == SastTypeKind.DYNAMIC_ARRAY
                    || type.kind == SastTypeKind.STATIC_ARRAY) {
                return containsOwnedPointer(type.elementType, visiting);
            }

            if (type.kind == SastTypeKind.STRUCT) {
                if (type.structSymbol == null) {
                    return false;
                }

                for (FieldSymbol field : type.structSymbol.fields) {
                    if (containsOwnedPointer(field.type, visiting)) {
                        return true;
                    }
                }

                return false;
            }

            if (type.kind == SastTypeKind.TUPLE) {
                for (FieldSymbol field : type.fields) {
                    if (containsOwnedPointer(field.type, visiting)) {
                        return true;
                    }
                }

                return false;
            }

            return false;
        } finally {
            visiting.remove(type);
        }
    }

    public boolean isForbiddenOwnedPointerAggregate(SastType type) {
        if (type == null) {
            return false;
        }

        if (type.kind == SastTypeKind.PTR) {
            return isForbiddenOwnedPointerAggregate(type.referencedType);
        }

        if (type.kind == SastTypeKind.DYNAMIC_ARRAY
                || type.kind == SastTypeKind.STATIC_ARRAY) {
            return containsOwnedPointer(type.elementType);
        }

        if (type.kind == SastTypeKind.STRUCT
                || type.kind == SastTypeKind.TUPLE) {
            return containsOwnedPointer(type);
        }

        return false;
    }

    public boolean isForbiddenAggregateMemberType(SastType type) {
        return containsOwnedPointer(type);
    }

    public void ensureNoOwnedPointerInAggregate(
            SastType type,
            String where
    ) {
        if (type != null && type.kind == SastTypeKind.STRUCT) {
            return;
        }

        if (isForbiddenOwnedPointerAggregate(type)) {
            reportOwnedPointerInAggregate(type, where);
        }
    }

    public void ensureNoOwnedPointerAggregateMember(
            SastType type,
            String where
    ) {
        if (isForbiddenAggregateMemberType(type)) {
            reportOwnedPointerInAggregate(type, where);
        }
    }

    private void reportOwnedPointerInAggregate(
            SastType type,
            String where
    ) {
        String nestedArrayHint = wantsNestedArrayHint(type)
                ? " If you mean to build an array of arrays, prefer a nested "
                        + "dynamic array such as [[T]] over [ptr<[T]>]: a "
                        + "nested dynamic array owns its inner arrays and "
                        + "manages their storage and lifetime itself, so it is "
                        + "safe and cannot leak or dangle the inner storage."
                : "";

        diagnostics.error(where
                + " has type '"
                + describeSafe(type)
                + "': owning pointer type 'ptr<T>' cannot appear inside an "
                + "array, struct, or tuple, recursively (including through "
                + "view<T>/mview<T> referents). Embedded owning pointers let "
                + "allocation and free responsibility escape into unrelated "
                + "code, so the storage lifetime cannot be reasoned about. "
                + "Keep owning pointers in direct variables, parameters, or "
                + "globals; store view<T>/mview<T> in aggregates; use the "
                + "pool allocator (franko/pool) for linked or node-like data."
                + nestedArrayHint);
    }

    private boolean wantsNestedArrayHint(SastType type) {
        if (type == null) {
            return false;
        }

        if (type.kind == SastTypeKind.DYNAMIC_ARRAY
                || type.kind == SastTypeKind.STATIC_ARRAY) {
            return true;
        }

        if (type.kind == SastTypeKind.PTR) {
            return wantsNestedArrayHint(type.referencedType);
        }

        return false;
    }

    public boolean isNullType(SastType t) {
        return t.kind == SastTypeKind.NULL;
    }

    public boolean isValueAssignableType(SastType t) {
        return isValueAssignableType(t, new HashSet<>());
    }

    private boolean isValueAssignableType(
            SastType t,
            Set<SastType> visited
    ) {
        if (t == null) {
            return false;
        }

        if (t.kind == SastTypeKind.PRIMITIVE
                || t.kind == SastTypeKind.PTR
                || t.kind == SastTypeKind.ENUM) {
            return true;
        }

        if (t.kind == SastTypeKind.STRUCT) {
            if (!visited.add(t)) {
                return false;
            }

            boolean assignable = true;

            for (FieldSymbol field : t.structSymbol.fields) {
                if (!isValueAssignableType(field.type, visited)) {
                    assignable = false;
                    break;
                }
            }

            visited.remove(t);

            return assignable;
        }

        if (t.kind == SastTypeKind.TUPLE) {
            if (!visited.add(t)) {
                return false;
            }

            boolean assignable = true;

            for (FieldSymbol field : t.fields) {
                if (!isValueAssignableType(field.type, visited)) {
                    assignable = false;
                    break;
                }
            }

            visited.remove(t);

            return assignable;
        }

        return false;
    }

    public boolean isValidFunctionReturnType(SastType t) {
        return isVoidType(t) || isValueAssignableType(t);
    }

    public SemanticPrimitiveKind primitiveKindOf(SastType type) {
        return type.kind == SastTypeKind.PRIMITIVE ? type.primitive : null;
    }

    public SastType elementTypeOfArray(SastType type) {
        if (type.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return type.elementType;
        }

        if (type.kind == SastTypeKind.STATIC_ARRAY) {
            return type.elementType;
        }

        return null;
    }

    public SastType referencedTypeOfPointer(SastType type) {
        return type.kind == SastTypeKind.PTR ? type.referencedType : null;
    }

    public boolean ensureValueExpression(
            SastNode expr,
            String where
    ) {
        if (expr == null) {
            diagnostics.error(where + " cannot be null");
            return false;
        }

        if (isVoidType(expr.type)) {
            diagnostics.error(where + " cannot be void");
            return false;
        }

        return true;
    }

    public static boolean isSelfTypeForOwner(
            SastType type,
            StructSymbol owner
    ) {
        if (!(type.kind == SastTypeKind.PTR)) {
            return false;
        }

        if (!(type.referencedType.kind == SastTypeKind.STRUCT)) {
            return false;
        }

        if (type.referencedType.structSymbol == null) {
            return false;
        }

        return type.referencedType.structSymbol.qualifiedName().equals(
                owner.qualifiedName());
    }

    public void ensureNotVoid(SastType t, String message) {
        if (isVoidType(t)) {
            diagnostics.error(message + ", got void");
        }
    }

    public void ensureIntegral(SastType t, String message) {
        if (!isIntegral(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureSignedIntegral(SastType t, String message) {
        if (!isSignedIntegral(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureUnsignedIntegral(SastType t, String message) {
        if (!isUnsignedIntegral(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureArrayType(SastType t, String message) {
        if (!isArrayType(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureDynamicArrayType(SastType t, String message) {
        if (!isDynamicArrayType(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureStaticArrayType(SastType t, String message) {
        if (!isStaticArrayType(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensurePointerType(SastType t, String message) {
        if (!isPointerType(t)) {
            diagnostics.error(message + ", got " + describeSafe(t));
        }
    }

    public void ensureSameType(
            SastType expected,
            SastType actual,
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
            SastType type,
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
            diagnostics.error(message + ": arrays cannot be returned directly; use ptr<"
                    + type.describe()
                    + "> instead");
            return;
        }

        diagnostics.error(message + ": invalid function return type "
                + describeSafe(type));
    }

    public void ensureAssignable(
            SastType targetType,
            SastNode expr,
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

        if (expr.type.kind == SastTypeKind.NULL) {
            if (!(targetType.kind == SastTypeKind.PTR)) {
                diagnostics.error(message
                        + ": null is only valid for pointer types, got target "
                        + targetType.describe());
            }
            return;
        }

        if (isArrayType(targetType) || isArrayType(expr.type)) {
            if (isArrayType(targetType)
                    && expr.type.kind == SastTypeKind.PTR
                    && isArrayType(expr.type.referencedType)) {
                if (isStringLiteralView(expr)) {
                    diagnostics.error(message + ": a string literal is a "
                            + expr.type.describe()
                            + " value and cannot be assigned to an array; copy "
                            + "the bytes with '<target>.memcpy(<source>)'");
                } else if (isArrayLiteralView(expr)) {
                    diagnostics.error(message + ": an array literal is a "
                            + expr.type.describe()
                            + " value and cannot be assigned to an array; copy "
                            + "the elements with '<target>.memcpy(<source>)'");
                } else {
                    diagnostics.error(message + ": "
                            + expr.type.describe()
                            + " cannot be assigned to an array; copy "
                            + "the bytes with '<target>.memcpy(<source>)'");
                }
                return;
            }

            diagnostics.error(message + ": arrays cannot be directly assigned");
            return;
        }

        if (targetType.kind == SastTypeKind.STRUCT
                && !isValueAssignableType(targetType)) {
            diagnostics.error(message + ": struct type "
                    + targetType.describe()
                    + " contains non-assignable fields and cannot be assigned"
                    + " as a whole value");
            return;
        }

        if (targetType.kind == SastTypeKind.TUPLE
                && !isValueAssignableType(targetType)) {
            diagnostics.error(message + ": tuple type "
                    + targetType.describe()
                    + " contains non-assignable elements and cannot be"
                    + " assigned as a whole value");
            return;
        }

        if (targetType.kind == SastTypeKind.ENUM) {
            if (expr.type.kind == SastTypeKind.ENUM) {
                if (expr.type.enumSymbol != targetType.enumSymbol) {
                    diagnostics.error(message + ": expected "
                            + targetType.describe()
                            + ", got "
                            + expr.type.describe());
                }
                return;
            }

            diagnostics.error(message + ": expected "
                    + targetType.describe()
                    + ", got "
                    + expr.type.describe());
            return;
        }

        if (expr.type.kind == SastTypeKind.ENUM) {
            diagnostics.error(message + ": expected "
                    + targetType.describe()
                    + ", got "
                    + expr.type.describe());
            return;
        }

        if (targetType.kind == SastTypeKind.PRIMITIVE) {
            if (expr.isConstant()) {
                requireAssignableConstant(
                        expr.constantValue,
                        targetType.primitive,
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

        if (PointerPermissions.isPermissionDowngrade(expr.type, targetType)
                || PointerPermissions.isStaticArrayViewDecay(expr.type, targetType)) {
            return;
        }

        if (!sameType(targetType, expr.type)) {
            diagnostics.error(message + ": expected "
                    + targetType.describe()
                    + ", got "
                    + expr.type.describe());
        }
    }

    private static boolean isStringLiteralView(SastNode expr) {
        return expr.kind == SastKind.GET_PTR
                && expr.target.kind == SastKind.VAR_EXPR
                && expr.target.symbol.kind == SymbolKind.GLOBAL
                && expr.target.symbol.literalBytes != null;
    }

    private static boolean isArrayLiteralView(SastNode expr) {
        return expr.kind == SastKind.GET_PTR
                && expr.target.kind == SastKind.VAR_EXPR
                && expr.target.symbol.kind == SymbolKind.GLOBAL
                && expr.target.symbol.literalValues != null;
    }

    private void requireAssignableConstant(
            BigInteger value,
            SemanticPrimitiveKind kind,
            SastType targetType,
            String message
    ) {
        if (!fitsBigIntegerToPrimitive(value, kind)) {
            diagnostics.error(message + ": constant value "
                    + value
                    + " does not fit in "
                    + targetType.describe());
        }
    }

    public void ensureSameIntegralTypeOrFit(
            SastNode left,
            SastNode right,
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

    public void ensureMixedIntegralOperandsWithConstantFit(
            SastNode left,
            SastNode right,
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
            SastNode left,
            SastNode right,
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

    public void ensureValidShiftOperands(
            SastNode left,
            SastNode right,
            String op
    ) {
        if (!ensureIntegralOperands(left, right, op)) {
            return;
        }

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
        switch (kind) {
            case INT8, UINT8: return SemanticPrimitiveKind.UINT8;
            case INT16, UINT16: return SemanticPrimitiveKind.UINT16;
            case INT32, UINT32: return SemanticPrimitiveKind.UINT32;
            case INT64, UINT64: return SemanticPrimitiveKind.UINT64;
            default: throw new IllegalStateException("Unknown primitive kind: " + kind);
        }
    }

    public SemanticPrimitiveKind signedVariantOf(SemanticPrimitiveKind kind) {
        switch (kind) {
            case INT8, UINT8: return SemanticPrimitiveKind.INT8;
            case INT16, UINT16: return SemanticPrimitiveKind.INT16;
            case INT32, UINT32: return SemanticPrimitiveKind.INT32;
            case INT64, UINT64: return SemanticPrimitiveKind.INT64;
            default: throw new IllegalStateException("Unknown primitive kind: " + kind);
        }
    }

    public void requireConstantFitsType(
            BigInteger value,
            SastType contextType,
            String message
    ) {
        if (!(contextType.kind == SastTypeKind.PRIMITIVE)) {
            diagnostics.error(message + ": expected primitive integer context, got "
                    + describeSafe(contextType));
            return;
        }

        requireConstantFitsPrimitive(value, contextType.primitive, message);
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
                    + kind.name().toLowerCase());
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
        switch (kind) {
            case INT8, UINT8: return 8;
            case INT16, UINT16: return 16;
            case INT32, UINT32: return 32;
            case INT64, UINT64: return 64;
            default: throw new IllegalStateException("Unknown primitive kind: " + kind);
        }
    }

    public static boolean isSigned(SemanticPrimitiveKind kind) {
        switch (kind) {
            case INT8, INT16, INT32, INT64: return true;
            case UINT8, UINT16, UINT32, UINT64: return false;
            default: throw new IllegalStateException("Unknown primitive kind: " + kind);
        }
    }

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

    public boolean isMemsetSafe(SastType t) {
        return isMemsetSafe(t, new HashSet<>());
    }

    private boolean isMemsetSafe(SastType t, Set<SastType> visited) {
        if (t == null || isVoidType(t)) {
            return false;
        }

        if (t.kind == SastTypeKind.ENUM) {
            return false;
        }

        if (t.kind == SastTypeKind.PRIMITIVE) {
            return true;
        }

        if (t.kind == SastTypeKind.STATIC_ARRAY) {
            return isMemsetSafe(t.elementType, visited);
        }

        if (t.kind == SastTypeKind.STRUCT) {

            if (!visited.add(t)) {
                return false;
            }

            boolean memsetSafe = true;

            for (FieldSymbol field : t.structSymbol.fields) {
                if (!isMemsetSafe(field.type, visited)) {
                    memsetSafe = false;
                    break;
                }
            }

            visited.remove(t);

            return memsetSafe;
        }

        if (t.kind == SastTypeKind.TUPLE) {
            if (!visited.add(t)) {
                return false;
            }

            boolean memsetSafe = true;

            for (FieldSymbol field : t.fields) {
                if (!isMemsetSafe(field.type, visited)) {
                    memsetSafe = false;
                    break;
                }
            }

            visited.remove(t);

            return memsetSafe;
        }

        return false;
    }

    public boolean isFullyDefaultInitialized(SastType t) {
        return isFullyDefaultInitialized(t, new HashSet<>());
    }

    private boolean isFullyDefaultInitialized(
            SastType t,
            Set<SastType> visited
    ) {
        if (t == null) {
            return false;
        }

        if (t.kind == SastTypeKind.ENUM
                || t.kind == SastTypeKind.PTR
                || t.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return true;
        }

        if (t.kind == SastTypeKind.PRIMITIVE) {
            return false;
        }

        if (t.kind == SastTypeKind.STATIC_ARRAY) {
            return isFullyDefaultInitialized(t.elementType, visited);
        }

        if (t.kind == SastTypeKind.STRUCT) {
            if (!visited.add(t)) {
                return false;
            }

            boolean fullyDefaulted = true;

            for (FieldSymbol field : t.structSymbol.fields) {
                if (field.foldedDefault == null
                        && !isFullyDefaultInitialized(field.type, visited)) {
                    fullyDefaulted = false;
                    break;
                }
            }

            visited.remove(t);

            return fullyDefaulted;
        }

        if (t.kind == SastTypeKind.TUPLE) {
            if (!visited.add(t)) {
                return false;
            }

            boolean fullyDefaulted = true;

            for (FieldSymbol field : t.fields) {
                if (!isFullyDefaultInitialized(field.type, visited)) {
                    fullyDefaulted = false;
                    break;
                }
            }

            visited.remove(t);

            return fullyDefaulted;
        }

        return false;
    }

    public boolean hasDefaultValue(SastType t) {
        return hasDefaultValue(t, new HashSet<>());
    }

    private boolean hasDefaultValue(
            SastType t,
            Set<SastType> visited
    ) {
        if (t == null || isVoidType(t)) {
            return false;
        }

        if (t.kind == SastTypeKind.ENUM) {
            return true;
        }

        if (t.kind == SastTypeKind.PRIMITIVE) {
            return false;
        }

        if (t.kind == SastTypeKind.PTR
                || t.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return true;
        }

        if (t.kind == SastTypeKind.STATIC_ARRAY) {
            return hasDefaultValue(t.elementType, visited);
        }

        if (t.kind == SastTypeKind.STRUCT) {
            if (!visited.add(t)) {
                return false;
            }

            boolean hasDefault = false;

            for (FieldSymbol field : t.structSymbol.fields) {
                if (field.foldedDefault != null
                        || hasDefaultValue(field.type, visited)) {
                    hasDefault = true;
                    break;
                }
            }

            visited.remove(t);

            return hasDefault;
        }

        if (t.kind == SastTypeKind.TUPLE) {
            if (!visited.add(t)) {
                return false;
            }

            boolean hasDefault = false;

            for (FieldSymbol field : t.fields) {
                if (hasDefaultValue(field.type, visited)) {
                    hasDefault = true;
                    break;
                }
            }

            visited.remove(t);

            return hasDefault;
        }

        return false;
    }

    public boolean isMemcpySafe(SastType t) {
        return isMemcpySafe(t, new HashSet<>());
    }

    private boolean isMemcpySafe(SastType t, Set<SastType> visited) {
        if (t == null || isVoidType(t)) {
            return false;
        }

        if (t.kind == SastTypeKind.PRIMITIVE
                || t.kind == SastTypeKind.PTR
                || t.kind == SastTypeKind.ENUM) {
            return true;
        }

        if (t.kind == SastTypeKind.STATIC_ARRAY) {
            return isMemcpySafe(t.elementType, visited);
        }

        if (t.kind == SastTypeKind.STRUCT) {

            if (!visited.add(t)) {
                return false;
            }

            boolean memcpySafe = true;

            for (FieldSymbol field : t.structSymbol.fields) {
                if (!isMemcpySafe(field.type, visited)) {
                    memcpySafe = false;
                    break;
                }
            }

            visited.remove(t);

            return memcpySafe;
        }

        if (t.kind == SastTypeKind.TUPLE) {
            if (!visited.add(t)) {
                return false;
            }

            boolean memcpySafe = true;

            for (FieldSymbol field : t.fields) {
                if (!isMemcpySafe(field.type, visited)) {
                    memcpySafe = false;
                    break;
                }
            }

            visited.remove(t);

            return memcpySafe;
        }

        return false;
    }

    public static boolean isByteType(SastType t) {
        return t.kind == SastTypeKind.PRIMITIVE
                && t.primitive == SemanticPrimitiveKind.UINT8;
    }

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
