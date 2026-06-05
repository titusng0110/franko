import java.util.List;
import java.util.Objects;

abstract class Symbol {
    final String name;
    final SemanticType type;

    Symbol(String name, SemanticType type) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }
}

class VariableSymbol extends Symbol {
    final boolean isHeap;
    boolean deleted;

    VariableSymbol(String name, SemanticType type, boolean isHeap) {
        super(name, type);
        this.isHeap = isHeap;
        this.deleted = false;
    }
}

// preemptive: functions not yet implemented
class ParameterSymbol extends VariableSymbol {
    final int index;

    ParameterSymbol(String name, SemanticType type, int index) {
        super(name, type, false);
        this.index = index;
    }
}

abstract class CallableSymbol extends Symbol {
    CallableSymbol(String name, SemanticType type) {
        super(name, type);
    }
}

// preemptive: functions not yet implemented
class FunctionSymbol extends CallableSymbol {
    final List<ParameterSymbol> parameters;
    final SemanticType returnType;
    final boolean isBuiltin;

    FunctionSymbol(
        String name,
        List<ParameterSymbol> parameters,
        SemanticType returnType,
        boolean isBuiltin
    ) {
        super(name, new SemanticFunctionType(
            parameters.stream().map(p -> p.type).toList(),
            returnType
        ));
        this.parameters = List.copyOf(parameters);
        this.returnType = Objects.requireNonNull(returnType);
        this.isBuiltin = isBuiltin;
    }
}
