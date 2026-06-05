import java.util.List;
import java.util.Objects;

abstract class SemanticType {
    public abstract String describe();
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
        return kind.name();
    }
}

final class SemanticDynamicArrayType extends SemanticType {
    final SemanticType elementType;

    SemanticDynamicArrayType(SemanticType elementType) {
        this.elementType = Objects.requireNonNull(elementType);
    }

    @Override
    public String describe() {
        return "array<" + elementType.describe() + ">";
    }
}

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
}

final class SemanticAddrType extends SemanticType {
    final SemanticType referencedType;

    SemanticAddrType(SemanticType referencedType) {
        this.referencedType = Objects.requireNonNull(referencedType);
    }

    @Override
    public String describe() {
        return "addr<" + referencedType.describe() + ">";
    }
}

// preemptive: functions not yet implemented
final class SemanticFunctionType extends SemanticType {
    final List<SemanticType> parameterTypes;
    final SemanticType returnType;

    SemanticFunctionType(List<SemanticType> parameterTypes, SemanticType returnType) {
        this.parameterTypes = List.copyOf(parameterTypes);
        this.returnType = Objects.requireNonNull(returnType);
    }

    @Override
    public String describe() {
        return "fn(...) -> " + returnType.describe();
    }
}