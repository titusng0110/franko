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
 */
final class SemanticStaticArrayType extends SemanticType {
    final SemanticType elementType;
    final String sizeLiteral; // preserve exact source spelling if desired

    SemanticStaticArrayType(SemanticType elementType, String sizeLiteral) {
        this.elementType = Objects.requireNonNull(elementType);
        this.sizeLiteral = Objects.requireNonNull(sizeLiteral);
    }

    @Override
    public String describe() {
        return "array<" + elementType.describe() + ", " + sizeLiteral + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SemanticStaticArrayType other)) {
            return false;
        }

        /*
         * For now, static array type equality uses exact sizeLiteral spelling.
         *
         * This means:
         *
         *   array<int32_t, 10>
         *   array<int32_t, 0xA>
         *
         * are considered different here.
         *
         * If you later canonicalize integer type sizes, replace sizeLiteral
         * with a parsed/canonical BigInteger size.
         */
        return elementType.equals(other.elementType)
                && sizeLiteral.equals(other.sizeLiteral);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                SemanticStaticArrayType.class,
                elementType,
                sizeLiteral
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