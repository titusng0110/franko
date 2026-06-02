// Type node
sealed abstract class TypeNode extends ASTNode
    permits PrimitiveTypeNode, DynamicArrayTypeNode, StaticArrayTypeNode {}

enum PrimitiveKind {
    INT8,
    INT16,
    INT32,
    INT64,
    UINT8,
    UINT16,
    UINT32,
    UINT64
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
    String sizeLiteral;

    StaticArrayTypeNode(TypeNode elementType, String sizeLiteral) {
        this.elementType = elementType;
        this.sizeLiteral = sizeLiteral;
    }
}