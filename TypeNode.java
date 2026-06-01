
// Type node
sealed abstract class TypeNode extends ASTNode
    permits PrimitiveTypeNode, DynamicArrayTypeNode, StaticArrayTypeNode {}

enum PrimitiveKind {
    INT32,
    UINT32,
    FLOAT32,
    CHAR8
}

final class PrimitiveTypeNode extends TypeNode {
    PrimitiveKind kind;

    PrimitiveTypeNode(PrimitiveKind kind) {
        this.kind = kind;
    }
}

final class DynamicArrayTypeNode extends TypeNode {
    TypeNode elementType;

    DynamicArrayTypeNode(TypeNode elementType) {
        this.elementType = elementType;
    }
}

final class StaticArrayTypeNode extends TypeNode {
    TypeNode elementType;
    int size;

    StaticArrayTypeNode(TypeNode elementType, int size) {
        this.elementType = elementType;
        this.size = size;
    }
}



