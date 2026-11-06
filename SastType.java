import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

enum SastTypeKind {
    PRIMITIVE,
    VOID,
    DYNAMIC_ARRAY,
    STATIC_ARRAY,
    PTR,
    NULL,
    STRUCT,
    TUPLE,
    ENUM,
    FUNCTION
}

final class SastType {
    final SastTypeKind kind;

    SemanticPrimitiveKind primitive;
    SastType elementType;
    String sizeLiteral;
    PtrPermission permission;
    SastType referencedType;
    StructSymbol structSymbol;
    EnumSymbol enumSymbol;
    StructSymbol cppSymbol;
    List<FieldSymbol> fields;
    List<SastType> parameterTypes;
    SastType returnType;

    private SastType(SastTypeKind kind) {
        this.kind = kind;
    }

    static SastType primitive(SemanticPrimitiveKind primitive) {
        SastType type = new SastType(SastTypeKind.PRIMITIVE);
        type.primitive = Objects.requireNonNull(primitive);
        return type;
    }

    static SastType voidType() {
        return new SastType(SastTypeKind.VOID);
    }

    static SastType dynamicArray(SastType elementType) {
        SastType type = new SastType(SastTypeKind.DYNAMIC_ARRAY);
        type.elementType = Objects.requireNonNull(elementType);
        return type;
    }

    static SastType staticArray(SastType elementType, String sizeLiteral) {
        SastType type = new SastType(SastTypeKind.STATIC_ARRAY);
        type.elementType = Objects.requireNonNull(elementType);
        type.sizeLiteral = Objects.requireNonNull(sizeLiteral);
        return type;
    }

    static SastType ptr(SastType referencedType) {
        return pointer(referencedType, PtrPermission.OWNER);
    }

    static SastType mview(SastType referencedType) {
        return pointer(referencedType, PtrPermission.MUTABLE);
    }

    static SastType view(SastType referencedType) {
        return pointer(referencedType, PtrPermission.READ_ONLY);
    }

    static SastType pointer(SastType referencedType, PtrPermission permission) {
        SastType type = new SastType(SastTypeKind.PTR);
        type.referencedType = Objects.requireNonNull(referencedType);
        type.permission = Objects.requireNonNull(permission);
        return type;
    }

    static SastType nullType() {
        return new SastType(SastTypeKind.NULL);
    }

    static SastType structType(StructSymbol structSymbol) {
        SastType type = new SastType(SastTypeKind.STRUCT);
        type.structSymbol = Objects.requireNonNull(structSymbol);
        return type;
    }

    static SastType tupleType(List<FieldSymbol> fields, StructSymbol cppSymbol) {
        SastType type = new SastType(SastTypeKind.TUPLE);
        type.fields = List.copyOf(fields);
        type.cppSymbol = Objects.requireNonNull(cppSymbol);
        return type;
    }

    static SastType enumType(EnumSymbol enumSymbol) {
        SastType type = new SastType(SastTypeKind.ENUM);
        type.enumSymbol = Objects.requireNonNull(enumSymbol);
        return type;
    }

    static SastType functionType(List<SastType> parameterTypes, SastType returnType) {
        SastType type = new SastType(SastTypeKind.FUNCTION);
        type.parameterTypes = List.copyOf(parameterTypes);
        type.returnType = Objects.requireNonNull(returnType);
        return type;
    }

    boolean isPrimitive() {
        return kind == SastTypeKind.PRIMITIVE;
    }

    boolean isVoid() {
        return kind == SastTypeKind.VOID;
    }

    boolean isPtr() {
        return kind == SastTypeKind.PTR;
    }

    boolean isStruct() {
        return kind == SastTypeKind.STRUCT;
    }

    boolean isTuple() {
        return kind == SastTypeKind.TUPLE;
    }

    boolean isEnum() {
        return kind == SastTypeKind.ENUM;
    }

    boolean isFunction() {
        return kind == SastTypeKind.FUNCTION;
    }

    boolean isNull() {
        return kind == SastTypeKind.NULL;
    }

    boolean isStaticArray() {
        return kind == SastTypeKind.STATIC_ARRAY;
    }

    boolean isDynamicArray() {
        return kind == SastTypeKind.DYNAMIC_ARRAY;
    }

    String describe() {
        return switch (kind) {
            case PRIMITIVE -> primitive.name().toLowerCase();
            case VOID -> "void";
            case DYNAMIC_ARRAY -> "[" + elementType.describe() + "]";
            case STATIC_ARRAY -> "[" + elementType.describe() + ", " + sizeLiteral + "]";
            case PTR -> permission.spelling() + "<" + referencedType.describe() + ">";
            case NULL -> "null";
            case STRUCT -> structSymbol.qualifiedName();
            case TUPLE -> describeTuple();
            case ENUM -> enumSymbol.qualifiedName();
            case FUNCTION -> describeFunction();
        };
    }

    String mangleKey() {
        return switch (kind) {
            case PRIMITIVE -> primitive.name().toLowerCase();
            case VOID -> "void";
            case DYNAMIC_ARRAY -> "[" + elementType.mangleKey() + "]";
            case STATIC_ARRAY -> {
                BigInteger canonical = unfoldedSize(sizeLiteral);
                String sizeKey = canonical != null
                        ? canonical.toString(10)
                        : sizeLiteral.trim();
                yield "[" + elementType.mangleKey() + "," + sizeKey + "]";
            }
            case PTR -> permission.spelling() + "<" + referencedType.mangleKey() + ">";
            case NULL -> throw new IllegalStateException(
                    "Internal compiler error: 'null' type has no mangled spelling"
            );
            case STRUCT -> structSymbol.qualifiedName();
            case TUPLE -> mangleTuple();
            case ENUM -> enumSymbol.qualifiedName();
            case FUNCTION -> throw new IllegalStateException(
                    "Internal compiler error: function types cannot appear in a mangled signature"
            );
        };
    }

    String signatureKey() {
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

    FieldSymbol resolveField(String name) {
        for (FieldSymbol field : fields) {
            if (field.name.equals(name)) {
                return field;
            }
        }

        return null;
    }

    private String describeTuple() {
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

    private String mangleTuple() {
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

    private String describeFunction() {
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
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof SastType other) || kind != other.kind) {
            return false;
        }

        return switch (kind) {
            case PRIMITIVE -> primitive == other.primitive;
            case VOID, NULL -> true;
            case DYNAMIC_ARRAY -> elementType.equals(other.elementType);
            case STATIC_ARRAY -> staticArrayEquals(other);
            case PTR -> permission == other.permission
                    && referencedType.equals(other.referencedType);
            case STRUCT -> structSymbol == other.structSymbol;
            case TUPLE -> tupleEquals(other);
            case ENUM -> enumSymbol == other.enumSymbol;
            case FUNCTION -> parameterTypes.equals(other.parameterTypes)
                    && returnType.equals(other.returnType);
        };
    }

    private boolean staticArrayEquals(SastType other) {
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

    private boolean tupleEquals(SastType other) {
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
        return switch (kind) {
            case PRIMITIVE -> Objects.hash(kind, primitive);
            case VOID -> kind.hashCode();
            case DYNAMIC_ARRAY -> Objects.hash(kind, elementType);
            case STATIC_ARRAY -> {
                BigInteger size = unfoldedSize(sizeLiteral);
                Object sizeKey = size != null ? size : sizeLiteral;
                yield Objects.hash(kind, elementType, sizeKey);
            }
            case PTR -> Objects.hash(kind, permission, referencedType);
            case NULL -> kind.hashCode();
            case STRUCT -> System.identityHashCode(structSymbol);
            case TUPLE -> {
                List<SastType> elementTypes = new java.util.ArrayList<>();
                for (FieldSymbol field : fields) {
                    elementTypes.add(field.type);
                }
                yield Objects.hash(kind, elementTypes);
            }
            case ENUM -> System.identityHashCode(enumSymbol);
            case FUNCTION -> Objects.hash(kind, parameterTypes, returnType);
        };
    }
}
