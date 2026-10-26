import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// ============================================================
// Base Symbol
// ============================================================


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

// ============================================================
// Variable Symbols
// ============================================================

class VariableSymbol extends Symbol {
    /*
     * Name of the namespace this variable was declared in, or null for
     * the global namespace.
     *
     * Only namespace-level variables (globals) carry a namespace.
     * Locals and parameters are function-scoped and always null.
     */
    final String namespace;

    VariableSymbol(String namespace, String name, SemanticType type) {
        super(name, type);
        this.namespace = namespace;
    }

    /**
     * The qualified source name of this symbol, e.g. "util::offset" for
     * a variable declared in namespace util, or plain "offset" for a
     * global-namespace variable, local, or parameter.
     */
    public String qualifiedName() {
        return namespace == null ? name : namespace + "::" + name;
    }
}

/**
 * Function parameters are variables inside the function body.
 *
 * They are represented as VariableSymbol subclasses so expression resolution
 * can treat parameters and local variables uniformly.
 */
class ParameterSymbol extends VariableSymbol {
    final int index;

    ParameterSymbol(String name, SemanticType type, int index) {
        super(null, name, type);
        this.index = index;
    }
}

/**
 * A namespace-level variable: a global variable declared in the global
 * namespace (namespace null) or inside a namespace.
 *
 * The subclass distinction matters for C++ emission: locals and
 * parameters are function-scoped and are never namespace-qualified,
 * while globals may need cross-namespace qualification.
 */
class GlobalVariableSymbol extends VariableSymbol {
    /*
     * Compile-time constant value for the os::IS_* target constants
     * (namespace "os" only, stamped by SemanticAnalyzer from TargetInfo).
     *
     * Null for every ordinary global. Non-null means references to this
     * global fold into integer literals during expression lowering, so
     * the global is never read at runtime.
     */
    BigInteger compileTimeConstantValue = null;

    GlobalVariableSymbol(String namespace, String name, SemanticType type) {
        super(namespace, name, type);
    }
}

// ============================================================
// Callable Symbols
// ============================================================

abstract class CallableSymbol extends Symbol {
    CallableSymbol(String name, SemanticFunctionType type) {
        super(name, type);
    }

    @Override
    public SemanticFunctionType type() {
        return (SemanticFunctionType) type;
    }
}

// ============================================================
// Function Symbols
// ============================================================

/**
 * FunctionSymbol represents a named function declaration.
 *
 * Important design distinction:
 *
 *   SemanticFunctionType = function's type/signature
 *   FunctionSymbol       = named function symbol in the function table
 *
 * Overload identity should be:
 *
 *   function name + ordered parameter types
 *
 * Return type is intentionally not part of overload identity.
 *
 * Therefore these conflict:
 *
 *   func f(int32_t x) -> int32_t
 *   func f(int32_t x) -> uint32_t
 *
 * But these are valid overloads:
 *
 *   func f(int32_t x) -> int32_t
 *   func f(uint32_t x) -> uint32_t
 */
class FunctionSymbol extends CallableSymbol {
    final List<ParameterSymbol> parameters;
    final boolean isBuiltin;

    /*
     * Name of the namespace this function was declared in, or null for
     * the global namespace.
     */
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

    /**
     * The qualified source name of this function, e.g. "util::doubleIt"
     * for a function declared in namespace util, or plain "doubleIt"
     * for a global-namespace function.
     */
    public String qualifiedName() {
        return namespace == null ? name : namespace + "::" + name;
    }

    /**
     * The local C++ function name for this function, before namespace
     * qualification: `FUNC_<name>_HASH_<8hex>`.
     *
     * The hash is the FNV-1a 32-bit hash of the canonical signature
     * key (declared parameter types in order, then the return type),
     * so overloads of the same name emit distinct C++ names without
     * relying on g++ overload resolution.
     */
    public String mangledName() {
        return "FUNC_" + name + "_HASH_" + Mangler.hashHex(functionType().signatureKey());
    }

    /**
     * The local C++ name actually emitted for this function.
     *
     * Every function emits its mangled name, including the entry-point
     * `main`: the generated C++ entry point is a small wrapper in the
     * program template that calls the mangled Franko `main`.
     */
    public String emittedLocalName() {
        return mangledName();
    }
}

// ============================================================
// Method Symbols
// ============================================================

/**
 * MethodSymbol represents a method declared inside a struct body.
 *
 * A method is a function whose first parameter must be literally
 * `addr<X> self` for the enclosing struct X. The receiver is passed
 * explicitly as `self`; method calls lower to ordinary function calls
 * with a synthesized `getaddr(receiver)` first argument.
 *
 * Overload identity matches functions: method name + ordered parameter
 * types. The `self` parameter is always `addr<X>` so it does not affect
 * resolution between overloads.
 */
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

    /**
     * The qualified source name of this method, e.g. "X.increment" for a
     * method declared in struct X, or "util::X.increment" for a method in
     * a namespaced struct.
     */
    @Override
    public String qualifiedName() {
        return owner.qualifiedName() + "." + name;
    }

    /**
     * The local C++ function name for this method, before namespace
     * qualification: `METH_<name>_OFSTRUCT_<StructName>_HASH_<8hex>`.
     *
     * The hash follows the same rule as functions: FNV-1a 32-bit over
     * the canonical signature key. The implicit `self` parameter
     * (`addr<X>`) is a declared parameter and participates in the hash.
     */
    @Override
    public String mangledName() {
        return "METH_"
                + name
                + "_OFSTRUCT_"
                + owner.name
                + "_HASH_"
                + Mangler.hashHex(functionType().signatureKey());
    }

    /**
     * Methods always emit their mangled name, exactly like functions.
     */
    @Override
    public String emittedLocalName() {
        return mangledName();
    }
}

// ============================================================
// Struct Symbols
// ============================================================

/**
 * FieldSymbol represents one declared field of a struct.
 *
 * Field types are resolved SemanticTypes produced by SemanticAnalyzer during
 * struct field resolution.
 *
 * A field's optional initializer is a constExpr that has already been folded
 * into a canonical BigInteger; it is null when the field has no initializer.
 */
class FieldSymbol {
    final String name;
    final SemanticType type;
    final BigInteger foldedDefault; // null if absent

    FieldSymbol(String name, SemanticType type, BigInteger foldedDefault) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.foldedDefault = foldedDefault;
    }
}

/**
 * StructSymbol represents a named struct declaration.
 *
 * Struct types are nominal: two distinct struct declarations are distinct
 * types even when their layouts are identical. Duplicate struct names are
 * rejected by SymbolTable.declareStruct.
 *
 * Fields are kept in declaration order.
 *
 * Methods are kept in a per-name overload table, mirroring the function
 * overload tables on NamespaceSymbol. A method may not share its name
 * with a field of the same struct.
 */
class StructSymbol {
    final String name;
    final List<FieldSymbol> fields;

    /*
     * Name of the namespace this struct was declared in, or null for
     * the global namespace.
     */
    final String namespace;

    /**
     * Method overloads by method name, in declaration order.
     */
    final Map<String, List<MethodSymbol>> methods = new LinkedHashMap<>();

    /**
     * Raw FunctionDeclNode -> registered MethodSymbol for this struct.
     */
    final IdentityHashMap<FunctionDeclNode, MethodSymbol> methodDeclSymbols =
            new IdentityHashMap<>();

    /**
     * Placeholder constructor used by SemanticAnalyzer phase 1.
     *
     * Fields are appended in declaration order during phase 2, so the field
     * list is a mutable ArrayList.
     */
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

    /**
     * The qualified source name of this struct, e.g. "util::Point" for
     * a struct declared in namespace util, or plain "Point" for a
     * global-namespace struct.
     */
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

// ============================================================
// Namespace Symbols
// ============================================================

/**
 * NamespaceSymbol represents one declared namespace.
 *
 * A namespace is identified by its full path: "util" for a
 * top-level namespace, "a::b" for a namespace b nested inside a.
 * The first declaration creates the symbol chain; later declarations
 * of the same path (in the same file or in imported files, in either
 * the path-header or the nested-block form) merge into it, so each
 * table below accumulates items from every declaration.
 *
 * A namespace carries its own struct, function overload, and global
 * variable tables. Lookup inside a namespace falls back to the global
 * namespace only after the namespace's own tables miss; enclosing
 * namespaces are not searched.
 */
class NamespaceSymbol {
    /**
     * Full path of this namespace, e.g. "a::b". Top-level namespaces
     * carry a single segment.
     */
    final String name;

    /**
     * Enclosing namespace, or null for a top-level namespace.
     * Intermediate namespaces are created implicitly by path-header
     * declarations (namespace a::b creates a when missing); they may
     * hold no items of their own.
     */
    final NamespaceSymbol parent;

    /**
     * Nested namespaces by single-segment name.
     */
    final Map<String, NamespaceSymbol> children = new LinkedHashMap<>();

    final Map<String, StructSymbol> structs = new LinkedHashMap<>();
    final Map<String, List<FunctionSymbol>> functions = new LinkedHashMap<>();
    final Map<String, VariableSymbol> globalVariables = new LinkedHashMap<>();

    /**
     * Names of namespace-level variables declared in this namespace.
     * Used for name-clash warnings within this namespace only.
     */
    final Set<String> variableNames = new LinkedHashSet<>();

    /**
     * Raw FunctionDeclNode -> registered FunctionSymbol for this
     * namespace, mirroring the global-namespace registration map.
     */
    final IdentityHashMap<FunctionDeclNode, FunctionSymbol> functionDeclSymbols =
            new IdentityHashMap<>();

    NamespaceSymbol(String name) {
        this(name, null);
    }

    NamespaceSymbol(String name, NamespaceSymbol parent) {
        this.name = Objects.requireNonNull(name);
        this.parent = parent;
    }

    /**
     * Single-segment name of this namespace, e.g. "b" for "a::b".
     */
    String simpleName() {
        int idx = name.lastIndexOf("::");
        return idx < 0 ? name : name.substring(idx + 2);
    }
}