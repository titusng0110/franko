import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

// ============================================================
// Semantic Types
// ============================================================

abstract class SemanticType {
    public abstract String describe();

    /**
     * Semantic type equality should be structural.
     *
     * Subclasses override equals/hashCode so overload resolution,
     * assignment checking, return checking, and diagnostics can compare
     * types reliably.
     */
    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();
}

// ============================================================
// Primitive Types
// ============================================================

enum SemanticPrimitiveKind {
    INT8,
    INT16,
    INT32,
    INT64,
    UINT8,
    UINT16,
    UINT32,
    UINT64
}

final class SemanticPrimitiveType extends SemanticType {
    final SemanticPrimitiveKind kind;

    SemanticPrimitiveType(SemanticPrimitiveKind kind) {
        this.kind = Objects.requireNonNull(kind);
    }

    @Override
    public String describe() {
        return kind.name();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SemanticPrimitiveType other)) {
            return false;
        }

        return kind == other.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(SemanticPrimitiveType.class, kind);
    }
}

// ============================================================
// Void Type
// ============================================================

/**
 * Void is not an ordinary value type.
 *
 * It is valid as a function return type:
 *
 *   func f() -> void { ... }
 *
 * But it should not be accepted for ordinary declarations:
 *
 *   void x;              // invalid
 *   array<void> xs;      // invalid
 *   addr<void> p;        // invalid
 *
 * The grammar already prevents most of these, but semantic analysis should
 * still treat void specially and defensively.
 */
final class SemanticVoidType extends SemanticType {

    @Override
    public String describe() {
        return "void";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SemanticVoidType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(SemanticVoidType.class);
    }
}

// ============================================================
// Array Types
// ============================================================

final class SemanticDynamicArrayType extends SemanticType {
    final SemanticType elementType;

    SemanticDynamicArrayType(SemanticType elementType) {
        this.elementType = Objects.requireNonNull(elementType);
    }

    @Override
    public String describe() {
        return "array<" + elementType.describe() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SemanticDynamicArrayType other)) {
            return false;
        }

        return elementType.equals(other.elementType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(SemanticDynamicArrayType.class, elementType);
    }
}

/*
 * Static array size is stored as canonical folded decimal text.
 *
 * Therefore:
 *
 *   array<int32_t, 10>
 *   array<int32_t, 0xA>
 *   array<int32_t, 5 + 5>
 *
 * become the same semantic static array type if all fold to 10.
 *
 * Type equality is structural after unfolding the constant size expression:
 * two static array types are equal iff their element types are equal and
 * their unfolded sizes are numerically equal. The size text is preserved only
 * for diagnostics and for C++ code emission.
 */
final class SemanticStaticArrayType extends SemanticType {
    final SemanticType elementType;
    final String sizeLiteral; // canonical folded decimal size

    SemanticStaticArrayType(SemanticType elementType, String sizeLiteral) {
        this.elementType = Objects.requireNonNull(elementType);
        this.sizeLiteral = Objects.requireNonNull(sizeLiteral);
    }

    @Override
    public String describe() {
        return "array<" + elementType.describe() + ", " + sizeLiteral + ">";
    }

    /**
     * Unfolds the stored size text into a BigInteger, or returns null when the
     * text is not a plain integer literal (defensive: SemanticAnalyzer always
     * stores canonical decimal text, which is always parseable).
     */
    private static BigInteger unfoldedSize(String literal) {
        try {
            String s = literal.trim();

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
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SemanticStaticArrayType other)) {
            return false;
        }

        if (!elementType.equals(other.elementType)) {
            return false;
        }

        BigInteger a = unfoldedSize(sizeLiteral);
        BigInteger b = unfoldedSize(other.sizeLiteral);

        if (a != null && b != null) {
            return a.equals(b);
        }

        return sizeLiteral.equals(other.sizeLiteral);
    }

    @Override
    public int hashCode() {
        BigInteger size = unfoldedSize(sizeLiteral);

        Object sizeKey = size != null ? size : sizeLiteral;

        return Objects.hash(
                SemanticStaticArrayType.class,
                elementType,
                sizeKey
        );
    }
}

// ============================================================
// Address Types
// ============================================================

final class SemanticAddrType extends SemanticType {
    final SemanticType referencedType;

    SemanticAddrType(SemanticType referencedType) {
        this.referencedType = Objects.requireNonNull(referencedType);
    }

    @Override
    public String describe() {
        return "addr<" + referencedType.describe() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SemanticAddrType other)) {
            return false;
        }

        return referencedType.equals(other.referencedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(SemanticAddrType.class, referencedType);
    }
}

// ============================================================
// Null Type
// ============================================================

/**
 * The type of the 'null' literal.
 *
 * 'null' is only valid where an address type is expected:
 *
 *   addr<int32_t> p = null;
 *   p == null
 *   p != null
 *   p = null
 *
 * It is not a value type that can be assigned to primitives, arrays,
 * or structs, and it is not the same as integer 0.
 */
final class SemanticNullType extends SemanticType {

    @Override
    public String describe() {
        return "null";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SemanticNullType;
    }

    @Override
    public int hashCode() {
        return SemanticNullType.class.hashCode();
    }
}

final class SemanticStructType extends SemanticType {
    final StructSymbol structSymbol;

    SemanticStructType(StructSymbol structSymbol) {
        this.structSymbol = Objects.requireNonNull(structSymbol);
    }

    @Override
    public String describe() {
        return structSymbol.name;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SemanticStructType other)) {
            return false;
        }

        return structSymbol == other.structSymbol;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(structSymbol);
    }
}

// ============================================================
// Function Types
// ============================================================

/**
 * SemanticFunctionType represents the type/signature of a callable:
 *
 *   fn(INT32, UINT8) -> VOID
 *
 * This should be kept.
 *
 * However, SemanticFunctionType is not itself the function symbol.
 * FunctionSymbol stores the name, parameter symbols, builtin flag, and this
 * SemanticFunctionType as its type.
 *
 * Raw declaration association is maintained separately by SemanticAnalyzer
 * during analysis.
 */
final class SemanticFunctionType extends SemanticType {
    final List<SemanticType> parameterTypes;
    final SemanticType returnType;

    SemanticFunctionType(List<SemanticType> parameterTypes, SemanticType returnType) {
        this.parameterTypes = List.copyOf(parameterTypes);
        this.returnType = Objects.requireNonNull(returnType);
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder();

        sb.append("fn(");

        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(parameterTypes.get(i).describe());
        }

        sb.append(") -> ");
        sb.append(returnType.describe());

        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SemanticFunctionType other)) {
            return false;
        }

        return parameterTypes.equals(other.parameterTypes)
                && returnType.equals(other.returnType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                SemanticFunctionType.class,
                parameterTypes,
                returnType
        );
    }
}