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
        switch (kind) {
            case PRIMITIVE:
                return primitive.name().toLowerCase();
            case VOID:
                return "void";
            case DYNAMIC_ARRAY:
                return "[" + elementType.describe() + "]";
            case STATIC_ARRAY:
                return "[" + elementType.describe() + ", " + sizeLiteral + "]";
            case PTR:
                return permission.spelling() + "<" + referencedType.describe() + ">";
            case NULL:
                return "null";
            case STRUCT:
                return structSymbol.qualifiedName();
            case TUPLE:
                return describeTuple();
            case ENUM:
                return enumSymbol.qualifiedName();
            case FUNCTION:
                return describeFunction();
            default:
                throw new IllegalStateException("Unknown type kind: " + kind);
        }
    }

    String mangleKey() {
        switch (kind) {
            case PRIMITIVE:
                return primitive.name().toLowerCase();
            case VOID:
                return "void";
            case DYNAMIC_ARRAY:
                return "[" + elementType.mangleKey() + "]";
            case STATIC_ARRAY: {
                BigInteger canonical = unfoldedSize(sizeLiteral);
                String sizeKey = canonical != null
                        ? canonical.toString(10)
                        : sizeLiteral.trim();
                return "[" + elementType.mangleKey() + "," + sizeKey + "]";
            }
            case PTR:
                return permission.spelling() + "<" + referencedType.mangleKey() + ">";
            case NULL:
                throw new IllegalStateException(
                        "Internal compiler error: 'null' type has no mangled spelling"
                );
            case STRUCT:
                return structSymbol.qualifiedName();
            case TUPLE:
                return mangleTuple();
            case ENUM:
                return enumSymbol.qualifiedName();
            case FUNCTION:
                throw new IllegalStateException(
                        "Internal compiler error: function types cannot appear in a mangled signature"
                );
            default:
                throw new IllegalStateException("Unknown type kind: " + kind);
        }
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

        if (!(obj instanceof SastType)) {
            return false;
        }

        SastType other = (SastType) obj;

        if (kind != other.kind) {
            return false;
        }

        switch (kind) {
            case PRIMITIVE:
                return primitive == other.primitive;
            case VOID:
                return true;
            case NULL:
                return true;
            case DYNAMIC_ARRAY:
                return elementType.equals(other.elementType);
            case STATIC_ARRAY:
                return staticArrayEquals(other);
            case PTR:
                return permission == other.permission
                        && referencedType.equals(other.referencedType);
            case STRUCT:
                return structSymbol == other.structSymbol;
            case TUPLE:
                return tupleEquals(other);
            case ENUM:
                return enumSymbol == other.enumSymbol;
            case FUNCTION:
                return parameterTypes.equals(other.parameterTypes)
                        && returnType.equals(other.returnType);
            default:
                return false;
        }
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
        switch (kind) {
            case PRIMITIVE:
                return Objects.hash(kind, primitive);
            case VOID:
                return kind.hashCode();
            case DYNAMIC_ARRAY:
                return Objects.hash(kind, elementType);
            case STATIC_ARRAY: {
                BigInteger size = unfoldedSize(sizeLiteral);
                Object sizeKey = size != null ? size : sizeLiteral;
                return Objects.hash(kind, elementType, sizeKey);
            }
            case PTR:
                return Objects.hash(kind, permission, referencedType);
            case NULL:
                return kind.hashCode();
            case STRUCT:
                return System.identityHashCode(structSymbol);
            case TUPLE: {
                List<SastType> elementTypes = new java.util.ArrayList<>();
                for (FieldSymbol field : fields) {
                    elementTypes.add(field.type);
                }
                return Objects.hash(kind, elementTypes);
            }
            case ENUM:
                return System.identityHashCode(enumSymbol);
            case FUNCTION:
                return Objects.hash(kind, parameterTypes, returnType);
            default:
                return kind.hashCode();
        }
    }
}
