import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

enum SymbolKind {
    VARIABLE,
    PARAMETER,
    GLOBAL,
    FUNCTION,
    METHOD
}

final class Symbol {
    final SymbolKind kind;
    final String name;
    final String namespace;
    final SastType type;

    final List<Symbol> parameters;
    final boolean isBuiltin;
    final boolean isConst;
    final StructSymbol owner;
    final int index;

    InlineMode inlineMode = InlineMode.DEFAULT;
    String genericGroup;
    String genericHash;

    BigInteger compileTimeConstantValue;
    List<BigInteger> literalBytes;
    List<BigInteger> literalValues;
    SastNode parameterDefault;

    private Symbol(
            SymbolKind kind,
            String name,
            String namespace,
            SastType type,
            List<Symbol> parameters,
            boolean isBuiltin,
            boolean isConst,
            StructSymbol owner,
            int index
    ) {
        this.kind = kind;
        this.name = Objects.requireNonNull(name);
        this.namespace = namespace;
        this.type = Objects.requireNonNull(type);
        this.parameters = parameters == null ? List.of() : List.copyOf(parameters);
        this.isBuiltin = isBuiltin;
        this.isConst = isConst;
        this.owner = owner;
        this.index = index;
    }

    static Symbol variable(String namespace, String name, SastType type) {
        return variable(namespace, name, type, false);
    }

    static Symbol variable(
            String namespace,
            String name,
            SastType type,
            boolean isConst
    ) {
        return new Symbol(
                SymbolKind.VARIABLE,
                name,
                namespace,
                type,
                List.of(),
                false,
                isConst,
                null,
                -1
        );
    }

    static Symbol parameter(String name, SastType type, int index) {
        return parameter(name, type, index, false);
    }

    static Symbol parameter(
            String name,
            SastType type,
            int index,
            boolean isConst
    ) {
        return new Symbol(
                SymbolKind.PARAMETER,
                name,
                null,
                type,
                List.of(),
                false,
                isConst,
                null,
                index
        );
    }

    static Symbol global(String namespace, String name, SastType type) {
        return global(namespace, name, type, false);
    }

    static Symbol global(
            String namespace,
            String name,
            SastType type,
            boolean isConst
    ) {
        return new Symbol(
                SymbolKind.GLOBAL,
                name,
                namespace,
                type,
                List.of(),
                false,
                isConst,
                null,
                -1
        );
    }

    static Symbol function(
            String namespace,
            String name,
            List<Symbol> parameters,
            SastType returnType,
            boolean isBuiltin
    ) {
        List<SastType> parameterTypes = new ArrayList<>();

        for (Symbol parameter : parameters) {
            parameterTypes.add(parameter.type);
        }

        return new Symbol(
                SymbolKind.FUNCTION,
                name,
                namespace,
                SastType.functionType(parameterTypes, returnType),
                parameters,
                isBuiltin,
                false,
                null,
                -1
        );
    }

    static Symbol method(
            StructSymbol owner,
            String name,
            List<Symbol> parameters,
            SastType returnType,
            boolean isBuiltin
    ) {
        List<SastType> parameterTypes = new ArrayList<>();

        for (Symbol parameter : parameters) {
            parameterTypes.add(parameter.type);
        }

        return new Symbol(
                SymbolKind.METHOD,
                name,
                owner == null ? null : owner.namespace,
                SastType.functionType(parameterTypes, returnType),
                parameters,
                isBuiltin,
                false,
                owner,
                -1
        );
    }

    boolean isVariable() {
        return kind == SymbolKind.VARIABLE;
    }

    boolean isParameter() {
        return kind == SymbolKind.PARAMETER;
    }

    boolean isGlobal() {
        return kind == SymbolKind.GLOBAL;
    }

    boolean isFunction() {
        return kind == SymbolKind.FUNCTION;
    }

    boolean isMethod() {
        return kind == SymbolKind.METHOD;
    }

    boolean isCallable() {
        return kind == SymbolKind.FUNCTION || kind == SymbolKind.METHOD;
    }

    SastType type() {
        return type;
    }

    String describe() {
        return name + ": " + type.describe();
    }

    String qualifiedName() {
        if (kind == SymbolKind.METHOD) {
            return owner.qualifiedName() + "." + name;
        }

        if (kind == SymbolKind.PARAMETER) {
            return name;
        }

        return namespace == null ? name : namespace + "::" + name;
    }

    SastType functionType() {
        return type;
    }

    SastType returnType() {
        return type.returnType;
    }

    List<SastType> parameterTypes() {
        return type.parameterTypes;
    }

    int arity() {
        return parameters.size();
    }

    String signatureString() {
        StringBuilder sb = new StringBuilder();
        sb.append(qualifiedName()).append("(");

        List<SastType> parameterTypes = parameterTypes();

        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(parameterTypes.get(i).describe());
        }

        sb.append(")");
        return sb.toString();
    }

    String fullSignatureString() {
        return signatureString() + " -> " + returnType().describe();
    }

    String mangledName() {
        if (kind == SymbolKind.METHOD) {
            String base = "METH_"
                    + name
                    + "_OFSTRUCT_"
                    + owner.name
                    + "_"
                    + Mangler.hashHex(type.signatureKey());

            if (genericHash != null) {
                return "GEN_" + genericHash + "_" + base;
            }

            return base;
        }

        String signatureHash = Mangler.hashHex(type.signatureKey());

        if (genericHash != null) {
            return "GEN_" + genericHash + "_FUNC_" + name + "_" + signatureHash;
        }

        return "FUNC_" + name + "_" + signatureHash;
    }

    String emittedLocalName() {
        return mangledName();
    }
}

class FieldSymbol {
    final String name;
    final SastType type;
    final BigInteger foldedDefault;
    final int tupleIndex;

    FieldSymbol(String name, SastType type, BigInteger foldedDefault) {
        this(name, type, foldedDefault, -1);
    }

    FieldSymbol(
            String name,
            SastType type,
            BigInteger foldedDefault,
            int tupleIndex
    ) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.foldedDefault = foldedDefault;
        this.tupleIndex = tupleIndex;
    }

    String emittedName() {
        return tupleIndex >= 0 ? "_" + tupleIndex : "FIELD_" + name;
    }
}

final class EnumVariant {
    final String name;
    final BigInteger value;

    EnumVariant(String name, BigInteger value) {
        this.name = Objects.requireNonNull(name);
        this.value = Objects.requireNonNull(value);
    }
}

final class EnumSymbol {
    final String name;
    final String namespace;
    final SemanticPrimitiveKind backingKind;
    final List<EnumVariant> variants;

    EnumSymbol(String name, String namespace, SemanticPrimitiveKind backingKind, List<EnumVariant> variants) {
        this.name = Objects.requireNonNull(name);
        this.namespace = namespace;
        this.backingKind = Objects.requireNonNull(backingKind);
        this.variants = List.copyOf(variants);
    }

    public String qualifiedName() {
        return namespace == null ? name : namespace + "::" + name;
    }

    public EnumVariant resolveVariant(String variantName) {
        for (EnumVariant variant : variants) {
            if (variant.name.equals(variantName)) {
                return variant;
            }
        }

        return null;
    }

    public EnumVariant variantWithValue(BigInteger value) {
        for (EnumVariant variant : variants) {
            if (variant.value.equals(value)) {
                return variant;
            }
        }

        return null;
    }
}

class StructSymbol {
    final String name;
    final List<FieldSymbol> fields;

    final String namespace;

    final boolean isTuple;

    String genericHash;
    String genericBaseName;

    final Map<String, List<Symbol>> methods = new LinkedHashMap<>();

    final IdentityHashMap<AstNode, Symbol> methodDeclSymbols =
            new IdentityHashMap<>();

    StructSymbol(String namespace, String name) {
        this(namespace, name, List.of(), false);
    }

    StructSymbol(String namespace, String name, List<FieldSymbol> fields) {
        this(namespace, name, fields, false);
    }

    StructSymbol(String namespace, String name, boolean isTuple) {
        this(namespace, name, List.of(), isTuple);
    }

    StructSymbol(
            String namespace,
            String name,
            List<FieldSymbol> fields,
            boolean isTuple
    ) {
        this.namespace = namespace;
        this.name = Objects.requireNonNull(name);
        this.fields = new ArrayList<>(fields);
        this.isTuple = isTuple;
    }

    public String qualifiedName() {
        return namespace == null ? name : namespace + "::" + name;
    }

    public FieldSymbol resolveField(String fieldName) {
        for (FieldSymbol field : fields) {
            if (field.name.equals(fieldName)) {
                return field;
            }
        }

        return null;
    }

    public boolean hasFieldName(String fieldName) {
        return resolveField(fieldName) != null;
    }

    public boolean hasMethod(String methodName) {
        List<Symbol> overloads = methods.get(methodName);
        return overloads != null && !overloads.isEmpty();
    }

    public List<Symbol> getMethods(String methodName) {
        List<Symbol> overloads = methods.get(methodName);
        return overloads == null ? List.of() : List.copyOf(overloads);
    }
}

class NamespaceSymbol {

    final String name;

    final NamespaceSymbol parent;

    final Map<String, NamespaceSymbol> children = new LinkedHashMap<>();

    final Map<String, StructSymbol> structs = new LinkedHashMap<>();
    final Map<String, EnumSymbol> enums = new LinkedHashMap<>();
    final Map<String, List<Symbol>> functions = new LinkedHashMap<>();
    final Map<String, Symbol> globalVariables = new LinkedHashMap<>();

    final Set<String> variableNames = new LinkedHashSet<>();

    final IdentityHashMap<AstNode, Symbol> functionDeclSymbols =
            new IdentityHashMap<>();

    NamespaceSymbol(String name) {
        this(name, null);
    }

    NamespaceSymbol(String name, NamespaceSymbol parent) {
        this.name = Objects.requireNonNull(name);
        this.parent = parent;
    }

    String simpleName() {
        int idx = name.lastIndexOf("::");
        return idx < 0 ? name : name.substring(idx + 2);
    }
}
