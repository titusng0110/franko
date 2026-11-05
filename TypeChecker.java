import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class TypeChecker {
    private final DiagnosticBag diagnostics;

    public TypeChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public static boolean sameType(SemanticType a, SemanticType b) {
        if (a == null || b == null) {
            return false;
        }

        return a.equals(b);
    }

    public String describeSafe(SemanticType type) {
        return type == null ? "<null>" : type.describe();
    }

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

    public boolean isPointerType(SemanticType t) {
        return t instanceof SemanticPtrType;
    }

    public boolean isNullType(SemanticType t) {
        return t instanceof SemanticNullType;
    }

    public boolean isValueAssignableType(SemanticType t) {
        return isValueAssignableType(t, new HashSet<>());
    }

    private boolean isValueAssignableType(
            SemanticType t,
            Set<SemanticType> visited
    ) {
        if (t == null) {
            return false;
        }

        if (t instanceof SemanticPrimitiveType
                || t instanceof SemanticPtrType) {
            return true;
        }

        if (t instanceof SemanticStructType struct) {
            if (!visited.add(t)) {
                return false;
            }

            boolean assignable = true;

            for (FieldSymbol field : struct.structSymbol.fields) {
                if (!isValueAssignableType(field.type, visited)) {
                    assignable = false;
                    break;
                }
            }

            visited.remove(t);

            return assignable;
        }

        if (t instanceof SemanticTupleType tuple) {
            if (!visited.add(t)) {
                return false;
            }

            boolean assignable = true;

            for (FieldSymbol field : tuple.fields) {
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

    public boolean isValidFunctionReturnType(SemanticType t) {
        return isVoidType(t) || isValueAssignableType(t);
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

    public SemanticType referencedTypeOfPointer(SemanticType type) {
        return type instanceof SemanticPtrType a ? a.referencedType : null;
    }

    public boolean ensureValueExpression(
            SemanticExprNode expr,
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
            SemanticType type,
            StructSymbol owner
    ) {
        if (!(type instanceof SemanticPtrType ptr)) {
            return false;
        }

        if (!(ptr.referencedType instanceof SemanticStructType structType)) {
            return false;
        }

        if (structType.structSymbol == null) {
            return false;
        }

        return structType.structSymbol.qualifiedName().equals(
                owner.qualifiedName());
    }

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

    public void ensurePointerType(SemanticType t, String message) {
        if (!isPointerType(t)) {
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
            diagnostics.error(message + ": arrays cannot be returned directly; use ptr<"
                    + type.describe()
                    + "> instead");
            return;
        }

        diagnostics.error(message + ": invalid function return type "
                + describeSafe(type));
    }

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

        if (expr.type instanceof SemanticNullType) {
            if (!(targetType instanceof SemanticPtrType)) {
                diagnostics.error(message
                        + ": null is only valid for pointer types, got target "
                        + targetType.describe());
            }
            return;
        }

        if (isArrayType(targetType) || isArrayType(expr.type)) {
            if (isArrayType(targetType)
                    && expr.type instanceof SemanticPtrType sourcePtr
                    && isArrayType(sourcePtr.referencedType)) {
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

        if (targetType instanceof SemanticStructType
                && !isValueAssignableType(targetType)) {
            diagnostics.error(message + ": struct type "
                    + targetType.describe()
                    + " contains non-assignable fields and cannot be assigned"
                    + " as a whole value");
            return;
        }

        if (targetType instanceof SemanticTupleType
                && !isValueAssignableType(targetType)) {
            diagnostics.error(message + ": tuple type "
                    + targetType.describe()
                    + " contains non-assignable elements and cannot be"
                    + " assigned as a whole value");
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

    private static boolean isStringLiteralView(SemanticExprNode expr) {
        return expr instanceof SemanticGetPtrNode get
                && get.target instanceof SemanticVarExprNode var
                && var.symbol instanceof GlobalVariableSymbol global
                && global.literalBytes != null;
    }

    private static boolean isArrayLiteralView(SemanticExprNode expr) {
        return expr instanceof SemanticGetPtrNode get
                && get.target instanceof SemanticVarExprNode var
                && var.symbol instanceof GlobalVariableSymbol global
                && global.literalValues != null;
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

    public void ensureValidShiftOperands(
            SemanticExprNode left,
            SemanticExprNode right,
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

    public boolean isMemsetSafe(SemanticType t) {
        return isMemsetSafe(t, new HashSet<>());
    }

    private boolean isMemsetSafe(SemanticType t, Set<SemanticType> visited) {
        if (t == null || isVoidType(t)) {
            return false;
        }

        if (t instanceof SemanticPrimitiveType) {
            return true;
        }

        if (t instanceof SemanticStaticArrayType s) {
            return isMemsetSafe(s.elementType, visited);
        }

        if (t instanceof SemanticStructType struct) {

            if (!visited.add(t)) {
                return false;
            }

            boolean memsetSafe = true;

            for (FieldSymbol field : struct.structSymbol.fields) {
                if (!isMemsetSafe(field.type, visited)) {
                    memsetSafe = false;
                    break;
                }
            }

            visited.remove(t);

            return memsetSafe;
        }

        if (t instanceof SemanticTupleType tuple) {
            if (!visited.add(t)) {
                return false;
            }

            boolean memsetSafe = true;

            for (FieldSymbol field : tuple.fields) {
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

    public boolean hasDefaultValue(SemanticType t) {
        return hasDefaultValue(t, new HashSet<>());
    }

    private boolean hasDefaultValue(
            SemanticType t,
            Set<SemanticType> visited
    ) {
        if (t == null || isVoidType(t)) {
            return false;
        }

        if (t instanceof SemanticPrimitiveType) {
            return false;
        }

        if (t instanceof SemanticPtrType
                || t instanceof SemanticDynamicArrayType) {
            return true;
        }

        if (t instanceof SemanticStaticArrayType s) {
            return hasDefaultValue(s.elementType, visited);
        }

        if (t instanceof SemanticStructType struct) {
            if (!visited.add(t)) {
                return false;
            }

            boolean hasDefault = false;

            for (FieldSymbol field : struct.structSymbol.fields) {
                if (field.foldedDefault != null
                        || hasDefaultValue(field.type, visited)) {
                    hasDefault = true;
                    break;
                }
            }

            visited.remove(t);

            return hasDefault;
        }

        if (t instanceof SemanticTupleType tuple) {
            if (!visited.add(t)) {
                return false;
            }

            boolean hasDefault = false;

            for (FieldSymbol field : tuple.fields) {
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

    public boolean isMemcpySafe(SemanticType t) {
        return isMemcpySafe(t, new HashSet<>());
    }

    private boolean isMemcpySafe(SemanticType t, Set<SemanticType> visited) {
        if (t == null || isVoidType(t)) {
            return false;
        }

        if (t instanceof SemanticPrimitiveType
                || t instanceof SemanticPtrType) {
            return true;
        }

        if (t instanceof SemanticStaticArrayType s) {
            return isMemcpySafe(s.elementType, visited);
        }

        if (t instanceof SemanticStructType struct) {

            if (!visited.add(t)) {
                return false;
            }

            boolean memcpySafe = true;

            for (FieldSymbol field : struct.structSymbol.fields) {
                if (!isMemcpySafe(field.type, visited)) {
                    memcpySafe = false;
                    break;
                }
            }

            visited.remove(t);

            return memcpySafe;
        }

        if (t instanceof SemanticTupleType tuple) {
            if (!visited.add(t)) {
                return false;
            }

            boolean memcpySafe = true;

            for (FieldSymbol field : tuple.fields) {
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

    public static boolean isByteType(SemanticType t) {
        return t instanceof SemanticPrimitiveType pt
                && pt.kind == SemanticPrimitiveKind.UINT8;
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
