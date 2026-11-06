import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

abstract class SemanticType {
    public abstract String describe();

    public abstract String mangleKey();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();
}

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
        return kind.name().toLowerCase();
    }

    @Override
    public String mangleKey() {
        return switch (kind) {
            case INT8 -> "int8";
            case INT16 -> "int16";
            case INT32 -> "int32";
            case INT64 -> "int64";
            case UINT8 -> "uint8";
            case UINT16 -> "uint16";
            case UINT32 -> "uint32";
            case UINT64 -> "uint64";
        };
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

final class SemanticVoidType extends SemanticType {

    @Override
    public String describe() {
        return "void";
    }

    @Override
    public String mangleKey() {
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

final class SemanticDynamicArrayType extends SemanticType {
    final SemanticType elementType;

    SemanticDynamicArrayType(SemanticType elementType) {
        this.elementType = Objects.requireNonNull(elementType);
    }

    @Override
    public String describe() {
        return "[" + elementType.describe() + "]";
    }

    @Override
    public String mangleKey() {
        return "[" + elementType.mangleKey() + "]";
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

final class SemanticStaticArrayType extends SemanticType {
    final SemanticType elementType;
    final String sizeLiteral;

    SemanticStaticArrayType(SemanticType elementType, String sizeLiteral) {
        this.elementType = Objects.requireNonNull(elementType);
        this.sizeLiteral = Objects.requireNonNull(sizeLiteral);
    }

    @Override
    public String describe() {
        return "[" + elementType.describe() + ", " + sizeLiteral + "]";
    }

    @Override
    public String mangleKey() {
        BigInteger canonical = unfoldedSize(sizeLiteral);
        String sizeKey = canonical != null ? canonical.toString(10) : sizeLiteral.trim();
        return "[" + elementType.mangleKey() + "," + sizeKey + "]";
    }

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
        } catch (NumberFormatException e) {
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

enum PtrPermission {
    OWNER("ptr"),
    MUTABLE("mview"),
    READ_ONLY("view");

    private final String spelling;

    PtrPermission(String spelling) {
        this.spelling = spelling;
    }

    String spelling() {
        return spelling;
    }

    boolean allows(PtrPermission other) {
        return this.ordinal() <= other.ordinal();
    }
}

class SemanticPtrType extends SemanticType {
    final SemanticType referencedType;
    final PtrPermission permission;

    SemanticPtrType(SemanticType referencedType) {
        this(referencedType, PtrPermission.OWNER);
    }

    protected SemanticPtrType(
            SemanticType referencedType,
            PtrPermission permission
    ) {
        this.referencedType = Objects.requireNonNull(referencedType);
        this.permission = Objects.requireNonNull(permission);
    }

    @Override
    public String describe() {
        return permission.spelling() + "<" + referencedType.describe() + ">";
    }

    @Override
    public String mangleKey() {
        return permission.spelling() + "<" + referencedType.mangleKey() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SemanticPtrType other)) {
            return false;
        }

        if (getClass() != other.getClass()) {
            return false;
        }

        return referencedType.equals(other.referencedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), referencedType);
    }

}

final class SemanticMViewType extends SemanticPtrType {

    SemanticMViewType(SemanticType referencedType) {
        super(referencedType, PtrPermission.MUTABLE);
    }
}

final class SemanticViewType extends SemanticPtrType {

    SemanticViewType(SemanticType referencedType) {
        super(referencedType, PtrPermission.READ_ONLY);
    }
}

final class SemanticNullType extends SemanticType {

    @Override
    public String describe() {
        return "null";
    }

    @Override
    public String mangleKey() {
        throw new IllegalStateException(
                "Internal compiler error: 'null' type has no mangled spelling"
        );
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

        return structSymbol.qualifiedName();
    }

    @Override
    public String mangleKey() {
        return structSymbol.qualifiedName();
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

final class SemanticTupleType extends SemanticType {
    final List<FieldSymbol> fields;
    final StructSymbol cppSymbol;

    SemanticTupleType(List<FieldSymbol> fields, StructSymbol cppSymbol) {
        this.fields = List.copyOf(fields);
        this.cppSymbol = Objects.requireNonNull(cppSymbol);
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(fields.get(i).type.describe());
        }

        sb.append(")");
        return sb.toString();
    }

    @Override
    public String mangleKey() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }

            sb.append(fields.get(i).type.mangleKey());
        }

        sb.append(")");
        return sb.toString();
    }

    FieldSymbol resolveField(String name) {
        for (FieldSymbol field : fields) {
            if (field.name.equals(name)) {
                return field;
            }
        }

        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SemanticTupleType other)) {
            return false;
        }

        if (fields.size() != other.fields.size()) {
            return false;
        }

        for (int i = 0; i < fields.size(); i++) {
            if (!fields.get(i).type.equals(other.fields.get(i).type)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        List<SemanticType> elementTypes = fields.stream()
                .map(field -> field.type)
                .toList();

        return Objects.hash(SemanticTupleType.class, elementTypes);
    }
}

final class SemanticEnumType extends SemanticType {
    final EnumSymbol enumSymbol;

    SemanticEnumType(EnumSymbol enumSymbol) {
        this.enumSymbol = Objects.requireNonNull(enumSymbol);
    }

    @Override
    public String describe() {
        return enumSymbol.qualifiedName();
    }

    @Override
    public String mangleKey() {
        return enumSymbol.qualifiedName();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SemanticEnumType other)) {
            return false;
        }

        return enumSymbol == other.enumSymbol;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(enumSymbol);
    }
}

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
    public String mangleKey() {
        throw new IllegalStateException(
                "Internal compiler error: function types cannot appear in a mangled signature"
        );
    }

    public String signatureKey() {
        StringBuilder sb = new StringBuilder();

        sb.append("fn(");

        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }

            sb.append(parameterTypes.get(i).mangleKey());
        }

        sb.append(")->");
        sb.append(returnType.mangleKey());

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
