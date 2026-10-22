import java.math.BigInteger;
import java.util.ArrayList;
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

    VariableSymbol(String name, SemanticType type, boolean isHeap) {
        super(name, type);
        this.isHeap = isHeap;
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
 */
class StructSymbol {
    final String name;
    final List<FieldSymbol> fields;

    /**
     * Placeholder constructor used by SemanticAnalyzer phase 1.
     *
     * Fields are appended in declaration order during phase 2, so the field
     * list is a mutable ArrayList.
     */
    StructSymbol(String name) {
        this.name = Objects.requireNonNull(name);
        this.fields = new ArrayList<>();
    }

    StructSymbol(String name, List<FieldSymbol> fields) {
        this.name = Objects.requireNonNull(name);
        this.fields = new ArrayList<>(fields);
    }

    public FieldSymbol resolveField(String fieldName) {
        for (FieldSymbol field : fields) {
            if (field.name.equals(fieldName)) {
                return field;
            }
        }

        return null;
    }
}