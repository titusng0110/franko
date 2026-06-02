import java.math.BigInteger;

/**
 * TypeChecker contains type-system helpers used by the other checkers.
 *
 * It does not orchestrate statement traversal; it only answers questions about
 * types and reports type-related semantic errors through the shared context.
 */
public class TypeChecker {
    private final SemanticAnalyzer.Context ctx;

    private static final PrimitiveTypeNode INT32 = new PrimitiveTypeNode(PrimitiveKind.INT32);
    private static final PrimitiveTypeNode UINT32 = new PrimitiveTypeNode(PrimitiveKind.UINT32);

    public TypeChecker(SemanticAnalyzer.Context ctx) {
        this.ctx = ctx;
    }

    public TypeNode int32Type() {
        return INT32;
    }

    public TypeNode uint32Type() {
        return UINT32;
    }

    public boolean sameType(TypeNode a, TypeNode b) {
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;

        if (a instanceof PrimitiveTypeNode) {
            PrimitiveTypeNode pa = (PrimitiveTypeNode) a;
            PrimitiveTypeNode pb = (PrimitiveTypeNode) b;
            return pa.kind == pb.kind;
        }

        if (a instanceof DynamicArrayTypeNode) {
            DynamicArrayTypeNode da = (DynamicArrayTypeNode) a;
            DynamicArrayTypeNode db = (DynamicArrayTypeNode) b;
            return sameType(da.elementType, db.elementType);
        }

        if (a instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode sa = (StaticArrayTypeNode) a;
            StaticArrayTypeNode sb = (StaticArrayTypeNode) b;
            return sameType(sa.elementType, sb.elementType)
                    && sameStaticArraySize(sa.sizeLiteral, sb.sizeLiteral);
        }

        return false;
    }

    public boolean isPrimitive(TypeNode t) {
        return t instanceof PrimitiveTypeNode;
    }

    public boolean isNumeric(TypeNode t) {
        return isIntegral(t);
    }

    public boolean isIntegral(TypeNode t) {
        if (!(t instanceof PrimitiveTypeNode)) return false;

        PrimitiveKind k = ((PrimitiveTypeNode) t).kind;
        return switch (k) {
            case INT8, INT16, INT32, INT64,
                 UINT8, UINT16, UINT32, UINT64 -> true;
        };
    }

    public boolean isSignedIntegral(TypeNode t) {
        if (!(t instanceof PrimitiveTypeNode)) return false;

        PrimitiveKind k = ((PrimitiveTypeNode) t).kind;
        return switch (k) {
            case INT8, INT16, INT32, INT64 -> true;
            case UINT8, UINT16, UINT32, UINT64 -> false;
        };
    }

    public boolean isUnsignedIntegral(TypeNode t) {
        if (!(t instanceof PrimitiveTypeNode)) return false;

        PrimitiveKind k = ((PrimitiveTypeNode) t).kind;
        return switch (k) {
            case UINT8, UINT16, UINT32, UINT64 -> true;
            case INT8, INT16, INT32, INT64 -> false;
        };
    }

    public boolean isArrayType(TypeNode t) {
        return t instanceof DynamicArrayTypeNode || t instanceof StaticArrayTypeNode;
    }

    public boolean isMemsetable(TypeNode t) {
        if (t == null) return false;

        if (t instanceof PrimitiveTypeNode) {
            return true;
        }

        if (t instanceof DynamicArrayTypeNode) {
            return false;
        }

        if (t instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode s = (StaticArrayTypeNode) t;
            return isMemsetable(s.elementType);
        }

        return false;
    }

    public void ensureArrayType(TypeNode t, String message) {
        if (!isArrayType(t)) {
            ctx.error(message);
        }
    }

    public void ensureDynamicArrayType(TypeNode t, String message) {
        if (!(t instanceof DynamicArrayTypeNode)) {
            ctx.error(message + ", got " + typeToString(t));
        }
    }

    public void ensureNumeric(TypeNode t, String message) {
        if (!isNumeric(t)) {
            ctx.error(message);
        }
    }

    public void ensureIntegral(TypeNode t, String message) {
        if (!isIntegral(t)) {
            ctx.error(message);
        }
    }

    public void ensureConditionType(TypeNode t, String where) {
        if (isArrayType(t) || !isPrimitive(t)) {
            ctx.error(where + " must be a scalar primitive expression, got " + typeToString(t));
        }
    }


    /**
     * Assignment compatibility policy for NON-LITERAL expressions:
     *
     *  - exact same type only
     *  - arrays are not assignable at all
     *
     * Integer literal assignments are handled separately by the semantic layer
     * via ensureIntegerLiteralFitsType(...).
     */
    public void ensureAssignable(TypeNode target, TypeNode value, String messageOnFailure) {
        if (target == null || value == null) {
            ctx.error(messageOnFailure);
            return;
        }

        // Arrays are never assignable through ordinary assignment/initialization.
        if (isArrayType(target) || isArrayType(value)) {
            ctx.error(messageOnFailure);
            return;
        }

        // For non-literals, assignment must be exact same type.
        if (!sameType(target, value)) {
            ctx.error(messageOnFailure);
        }
    }


    public boolean areComparable(TypeNode a, TypeNode b) {
        if (sameType(a, b)) return true;
        return isPrimitive(a) && isPrimitive(b);
    }

    /**
     * Integer promotion policy:
     *  - same signedness: choose wider
     *  - mixed signed/unsigned:
     *      * if signed width > unsigned width, keep signed
     *      * otherwise keep unsigned
     */
    public TypeNode numericPromotion(TypeNode a, TypeNode b) {
        PrimitiveKind ka = kindOf(a);
        PrimitiveKind kb = kindOf(b);

        if (ka == kb) {
            return new PrimitiveTypeNode(ka);
        }

        boolean aSigned = isSigned(ka);
        boolean bSigned = isSigned(kb);
        int wa = bitWidth(ka);
        int wb = bitWidth(kb);

        if (aSigned == bSigned) {
            return new PrimitiveTypeNode(wa >= wb ? ka : kb);
        }

        PrimitiveKind signedKind = aSigned ? ka : kb;
        PrimitiveKind unsignedKind = aSigned ? kb : ka;

        int signedWidth = bitWidth(signedKind);
        int unsignedWidth = bitWidth(unsignedKind);

        if (signedWidth > unsignedWidth) {
            return new PrimitiveTypeNode(signedKind);
        }

        return new PrimitiveTypeNode(unsignedKind);
    }

    public PrimitiveKind kindOf(TypeNode t) {
        if (!(t instanceof PrimitiveTypeNode)) {
            return PrimitiveKind.INT32;
        }
        return ((PrimitiveTypeNode) t).kind;
    }

    public TypeNode elementTypeOf(TypeNode t) {
        if (t instanceof DynamicArrayTypeNode) {
            return ((DynamicArrayTypeNode) t).elementType;
        }
        if (t instanceof StaticArrayTypeNode) {
            return ((StaticArrayTypeNode) t).elementType;
        }

        ctx.error("Attempted to fetch element type of non-array type " + typeToString(t));
        return INT32;
    }

    /**
     * Converts a positive integer literal string into a BigInteger.
     *
     * Supported forms:
     *   123
     *   0b1010
     *   0B1010
     *   0xFF
     *   0XFF
     *
     * The AST stores unary minus separately, so this method expects
     * an unsigned textual literal only.
     */
    public BigInteger parseIntegerLiteral(String literalText) {
        if (literalText == null || literalText.isEmpty()) {
            throw new IllegalArgumentException("Empty integer literal");
        }

        if (literalText.startsWith("0b") || literalText.startsWith("0B")) {
            return new BigInteger(literalText.substring(2), 2);
        }

        if (literalText.startsWith("0x") || literalText.startsWith("0X")) {
            return new BigInteger(literalText.substring(2), 16);
        }

        return new BigInteger(literalText, 10);
    }

    /**
     * Returns a canonical decimal string for a positive integer literal.
     *
     * Examples:
     *   "42"       -> "42"
     *   "0b001010" -> "10"
     *   "0xFF"     -> "255"
     */
    public String normalizeIntegerLiteralToDecimal(String literalText) {
        return parseIntegerLiteral(literalText).toString();
    }

    /**
     * Returns a canonical decimal string, optionally applying unary minus.
     *
     * Examples:
     *   ("0x80", false) -> "128"
     *   ("0x80", true)  -> "-128"
     */
    public String normalizeIntegerLiteralToDecimal(String literalText, boolean negative) {
        BigInteger value = parseIntegerLiteral(literalText);
        if (negative) {
            value = value.negate();
        }
        return value.toString();
    }

    /**
     * Checks whether an integer literal (with sign represented separately)
     * fits inside the target primitive integer type.
     */
    public boolean fitsIntegerLiteralToType(String literalText, boolean negative, TypeNode targetType) {
        if (!(targetType instanceof PrimitiveTypeNode)) {
            return false;
        }

        PrimitiveKind kind = ((PrimitiveTypeNode) targetType).kind;
        BigInteger value = parseIntegerLiteral(literalText);

        if (negative) {
            value = value.negate();
        }

        return fitsBigIntegerToPrimitive(value, kind);
    }

    /**
     * Same as fitsIntegerLiteralToType, but reports a semantic error on failure.
     */
    public void ensureIntegerLiteralFitsType(
            String literalText,
            boolean negative,
            TypeNode targetType,
            String messageOnFailure
    ) {
        if (!(targetType instanceof PrimitiveTypeNode)) {
            ctx.error(messageOnFailure);
            return;
        }

        try {
            if (!fitsIntegerLiteralToType(literalText, negative, targetType)) {
                String rendered = negative ? "-" + literalText : literalText;
                ctx.error(
                    messageOnFailure
                    + ": literal " + rendered
                    + " does not fit in " + typeToString(targetType)
                );
            }
        } catch (IllegalArgumentException ex) {
            String rendered = negative ? "-" + literalText : literalText;
            ctx.error(
                messageOnFailure
                + ": invalid integer literal " + rendered
            );
        }
    }

    /**
     * Array-size literals must fit in uint32_t.
     *
     * This is intended for:
     *   - static array type sizes: array<int8_t, 16>
     *   - literal runtime array init sizes, if you choose to enforce it there
     */
    public boolean fitsArraySizeLiteral(String literalText) {
        try {
            BigInteger value = parseIntegerLiteral(literalText);
            return fitsBigIntegerToPrimitive(value, PrimitiveKind.UINT32);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Reports an error if an array-size literal is invalid or does not fit uint32_t.
     */
    public void ensureArraySizeLiteralFitsUint32(String literalText, String messageOnFailure) {
        try {
            BigInteger value = parseIntegerLiteral(literalText);

            if (!fitsBigIntegerToPrimitive(value, PrimitiveKind.UINT32)) {
                ctx.error(
                    messageOnFailure
                    + ": array size literal " + literalText
                    + " does not fit in uint32_t"
                );
            }
        } catch (IllegalArgumentException ex) {
            ctx.error(
                messageOnFailure
                + ": invalid array size literal " + literalText
            );
        }
    }

    /**
     * For runtime expressions that have already been reduced to a signed value.
     * Array sizes must be in uint32_t range.
     */
    public boolean fitsArraySizeValue(BigInteger value) {
        return fitsBigIntegerToPrimitive(value, PrimitiveKind.UINT32);
    }

    /**
     * Compares static array size literals semantically.
     *
     * So these compare equal:
     *   array<int, 16>
     *   array<int, 0x10>
     *   array<int, 0b10000>
     *
     * Invalid or out-of-range sizes are NOT considered equal.
     */
    public boolean sameStaticArraySize(String a, String b) {
        try {
            BigInteger av = parseIntegerLiteral(a);
            BigInteger bv = parseIntegerLiteral(b);

            if (!fitsBigIntegerToPrimitive(av, PrimitiveKind.UINT32)) return false;
            if (!fitsBigIntegerToPrimitive(bv, PrimitiveKind.UINT32)) return false;

            return av.equals(bv);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public int bitWidth(TypeNode t) {
        if (!(t instanceof PrimitiveTypeNode)) return 32;
        return bitWidth(((PrimitiveTypeNode) t).kind);
    }

    public int bitWidth(PrimitiveKind kind) {
        return switch (kind) {
            case INT8, UINT8 -> 8;
            case INT16, UINT16 -> 16;
            case INT32, UINT32 -> 32;
            case INT64, UINT64 -> 64;
        };
    }

    public boolean isSigned(PrimitiveKind kind) {
        return switch (kind) {
            case INT8, INT16, INT32, INT64 -> true;
            case UINT8, UINT16, UINT32, UINT64 -> false;
        };
    }

    public BigInteger minValue(PrimitiveKind kind) {
        int bits = bitWidth(kind);

        if (isSigned(kind)) {
            return BigInteger.valueOf(2).pow(bits - 1).negate();
        }

        return BigInteger.ZERO;
    }

    public BigInteger maxValue(PrimitiveKind kind) {
        int bits = bitWidth(kind);

        if (isSigned(kind)) {
            return BigInteger.valueOf(2).pow(bits - 1).subtract(BigInteger.ONE);
        }

        return BigInteger.valueOf(2).pow(bits).subtract(BigInteger.ONE);
    }

    public boolean fitsBigIntegerToPrimitive(BigInteger value, PrimitiveKind kind) {
        return value.compareTo(minValue(kind)) >= 0
                && value.compareTo(maxValue(kind)) <= 0;
    }

    public String typeToString(TypeNode t) {
        if (t == null) return "<null>";

        if (t instanceof PrimitiveTypeNode) {
            PrimitiveKind k = ((PrimitiveTypeNode) t).kind;
            return switch (k) {
                case INT8 -> "int8_t";
                case INT16 -> "int16_t";
                case INT32 -> "int32_t";
                case INT64 -> "int64_t";
                case UINT8 -> "uint8_t";
                case UINT16 -> "uint16_t";
                case UINT32 -> "uint32_t";
                case UINT64 -> "uint64_t";
            };
        }

        if (t instanceof DynamicArrayTypeNode) {
            return "array<" + typeToString(((DynamicArrayTypeNode) t).elementType) + ">";
        }

        if (t instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode s = (StaticArrayTypeNode) t;
            return "array<" + typeToString(s.elementType) + ", " + s.sizeLiteral + ">";
        }

        return t.getClass().getSimpleName();
    }
}