import java.util.List;
import java.util.Objects;

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
    final boolean isHeap;
    boolean deleted;

    VariableSymbol(String name, SemanticType type, boolean isHeap) {
        super(name, type);
        this.isHeap = isHeap;
        this.deleted = false;
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
        super(name, type, false);
        this.index = index;
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

    FunctionSymbol(
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

        sb.append(name).append("(");

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
}