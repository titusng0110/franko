import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

abstract class Symbol {
    final String name;
    final SemanticType type;

    Symbol(String name, SemanticType type) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    public SemanticType type() {
        return type;
    }

    public String describe() {
        return name + ": " + type.describe();
    }
}

class VariableSymbol extends Symbol {

    final String namespace;

    VariableSymbol(String namespace, String name, SemanticType type) {
        super(name, type);
        this.namespace = namespace;
    }

    public String qualifiedName() {
        return namespace == null ? name : namespace + "::" + name;
    }
}

class ParameterSymbol extends VariableSymbol {
    final int index;

    ParameterSymbol(String name, SemanticType type, int index) {
        super(null, name, type);
        this.index = index;
    }
}

class GlobalVariableSymbol extends VariableSymbol {

    BigInteger compileTimeConstantValue = null;

    GlobalVariableSymbol(String namespace, String name, SemanticType type) {
        super(namespace, name, type);
    }
}

abstract class CallableSymbol extends Symbol {
    CallableSymbol(String name, SemanticFunctionType type) {
        super(name, type);
    }

    @Override
    public SemanticFunctionType type() {
        return (SemanticFunctionType) type;
    }
}

class FunctionSymbol extends CallableSymbol {
    final List<ParameterSymbol> parameters;
    final boolean isBuiltin;

    final String namespace;

    FunctionSymbol(
            String namespace,
            String name,
            List<ParameterSymbol> parameters,
            SemanticType returnType,
            boolean isBuiltin
    ) {
        super(
                name,
                new SemanticFunctionType(
                        parameters.stream().map(p -> p.type).toList(),
                        returnType
                )
        );

        this.namespace = namespace;
        this.parameters = List.copyOf(parameters);
        this.isBuiltin = isBuiltin;
    }

    public SemanticFunctionType functionType() {
        return (SemanticFunctionType) type;
    }

    public SemanticType returnType() {
        return functionType().returnType;
    }

    public List<SemanticType> parameterTypes() {
        return functionType().parameterTypes;
    }

    public int arity() {
        return parameters.size();
    }

    public String signatureString() {
        StringBuilder sb = new StringBuilder();

        sb.append(qualifiedName()).append("(");

        List<SemanticType> parameterTypes = parameterTypes();

        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(parameterTypes.get(i).describe());
        }

        sb.append(")");

        return sb.toString();
    }

    public String fullSignatureString() {
        return signatureString() + " -> " + returnType().describe();
    }

    public String qualifiedName() {
        return namespace == null ? name : namespace + "::" + name;
    }

    public String mangledName() {
        return "FUNC_" + name + "_HASH_" + Mangler.hashHex(functionType().signatureKey());
    }

    public String emittedLocalName() {
        return mangledName();
    }
}

class MethodSymbol extends FunctionSymbol {
    final StructSymbol owner;

    MethodSymbol(
            StructSymbol owner,
            String name,
            List<ParameterSymbol> parameters,
            SemanticType returnType,
            boolean isBuiltin
    ) {
        super(
                owner == null ? null : owner.namespace,
                name,
                parameters,
                returnType,
                isBuiltin
        );

        this.owner = Objects.requireNonNull(owner);
    }

    @Override
    public String qualifiedName() {
        return owner.qualifiedName() + "." + name;
    }

    @Override
    public String mangledName() {
        return "METH_"
                + name
                + "_OFSTRUCT_"
                + owner.name
                + "_HASH_"
                + Mangler.hashHex(functionType().signatureKey());
    }

    @Override
    public String emittedLocalName() {
        return mangledName();
    }
}

class FieldSymbol {
    final String name;
    final SemanticType type;
    final BigInteger foldedDefault;

    FieldSymbol(String name, SemanticType type, BigInteger foldedDefault) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.foldedDefault = foldedDefault;
    }
}

class StructSymbol {
    final String name;
    final List<FieldSymbol> fields;

    final String namespace;

    final Map<String, List<MethodSymbol>> methods = new LinkedHashMap<>();

    final IdentityHashMap<FunctionDeclNode, MethodSymbol> methodDeclSymbols =
            new IdentityHashMap<>();

    StructSymbol(String namespace, String name) {
        this.namespace = namespace;
        this.name = Objects.requireNonNull(name);
        this.fields = new ArrayList<>();
    }

    StructSymbol(String namespace, String name, List<FieldSymbol> fields) {
        this.namespace = namespace;
        this.name = Objects.requireNonNull(name);
        this.fields = new ArrayList<>(fields);
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
        List<MethodSymbol> overloads = methods.get(methodName);
        return overloads != null && !overloads.isEmpty();
    }

    public List<MethodSymbol> getMethods(String methodName) {
        List<MethodSymbol> overloads = methods.get(methodName);
        return overloads == null ? List.of() : List.copyOf(overloads);
    }
}

class NamespaceSymbol {

    final String name;

    final NamespaceSymbol parent;

    final Map<String, NamespaceSymbol> children = new LinkedHashMap<>();

    final Map<String, StructSymbol> structs = new LinkedHashMap<>();
    final Map<String, List<FunctionSymbol>> functions = new LinkedHashMap<>();
    final Map<String, VariableSymbol> globalVariables = new LinkedHashMap<>();

    final Set<String> variableNames = new LinkedHashSet<>();

    final IdentityHashMap<FunctionDeclNode, FunctionSymbol> functionDeclSymbols =
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
