import java.math.BigInteger;
import java.util.List;

abstract class ASTNode {}

abstract class TypeNode extends ASTNode {}

class ProgramNode extends ASTNode {
    List<ASTNode> topLevelItems;

    ProgramNode(List<ASTNode> topLevelItems) {
        this.topLevelItems = topLevelItems;
    }
}

class BlockNode extends ASTNode {
    List<ASTNode> statements;

    BlockNode(List<ASTNode> statements) {
        this.statements = statements;
    }
}

class ExprStmtNode extends ASTNode {
    ASTNode expr;

    ExprStmtNode(ASTNode expr) {
        this.expr = expr;
    }
}

class AssignNode extends ASTNode {
    String op;
    ASTNode target;
    ASTNode value;

    AssignNode(ASTNode target, ASTNode value) {
        this("=", target, value);
    }

    AssignNode(String op, ASTNode target, ASTNode value) {
        this.op = op;
        this.target = target;
        this.value = value;
    }
}

class IncDecNode extends ASTNode {
    String op;
    ASTNode target;

    IncDecNode(String op, ASTNode target) {
        this.op = op;
        this.target = target;
    }
}

class IfNode extends ASTNode {
    ASTNode condition;
    ASTNode thenBranch;
    ASTNode elseBranch;

    IfNode(ASTNode condition, ASTNode thenBranch, ASTNode elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }
}

class WhileNode extends ASTNode {
    ASTNode condition;
    ASTNode body;

    List<ASTNode> update;

    WhileNode(ASTNode condition, ASTNode body) {
        this.condition = condition;
        this.body = body;
        this.update = List.of();
    }

    WhileNode(ASTNode condition, ASTNode body, List<ASTNode> update) {
        this.condition = condition;
        this.body = body;
        this.update = List.copyOf(update);
    }
}

class BreakNode extends ASTNode {
    ASTNode level;

    BreakNode(ASTNode level) {
        this.level = level;
    }
}

class ContinueNode extends ASTNode {
    ASTNode level;

    ContinueNode() {
        this.level = null;
    }

    ContinueNode(ASTNode level) {
        this.level = level;
    }
}

class ForNode extends ASTNode {
    ASTNode init;
    ASTNode condition;
    ASTNode update;
    ASTNode body;

    ForNode(ASTNode init, ASTNode condition, ASTNode update, ASTNode body) {
        this.init = init;
        this.condition = condition;
        this.update = update;
        this.body = body;
    }
}

class PrintNode extends ASTNode {
    List<ASTNode> args;

    PrintNode(List<ASTNode> args) {
        this.args = args;
    }
}

class ReturnNode extends ASTNode {
    ASTNode value;

    ReturnNode(ASTNode value) {
        this.value = value;
    }
}

class FunctionDeclNode extends ASTNode {
    String name;
    List<ParameterNode> parameters;
    TypeNode returnType;
    BlockNode body;

    FunctionDeclNode(
        String name,
        List<ParameterNode> parameters,
        TypeNode returnType,
        BlockNode body
    ) {
        this.name = name;
        this.parameters = parameters;
        this.returnType = returnType;
        this.body = body;
    }
}

class ParameterNode extends ASTNode {
    TypeNode type;
    String name;

    ParameterNode(TypeNode type, String name) {
        this.type = type;
        this.name = name;
    }
}

class StructDeclNode extends ASTNode {
    String name;
    List<StructFieldDeclNode> fields;
    List<FunctionDeclNode> methods;

    StructDeclNode(String name, List<StructFieldDeclNode> fields) {
        this(name, fields, List.of());
    }

    StructDeclNode(
        String name,
        List<StructFieldDeclNode> fields,
        List<FunctionDeclNode> methods
    ) {
        this.name = name;
        this.fields = fields;
        this.methods = methods;
    }
}

class StructFieldDeclNode extends ASTNode {
    TypeNode type;
    String name;
    ASTNode constInitializer;

    StructFieldDeclNode(TypeNode type, String name, ASTNode constInitializer) {
        this.type = type;
        this.name = name;
        this.constInitializer = constInitializer;
    }
}

class GlobalVarDeclNode extends ASTNode {
    TypeNode type;
    String name;
    ASTNode constInitializer;

    List<BigInteger> literalBytes;

    GlobalVarDeclNode(TypeNode type, String name, ASTNode constInitializer) {
        this.type = type;
        this.name = name;
        this.constInitializer = constInitializer;
    }
}

class ImportNode extends ASTNode {
    String rawPathText;

    ImportNode(String rawPathText) {
        this.rawPathText = rawPathText;
    }
}

class NamespaceNode extends ASTNode {
    String name;
    List<ASTNode> items;

    NamespaceNode(String name, List<ASTNode> items) {
        this.name = name;
        this.items = items;
    }
}

class QualifiedNameNode extends ASTNode {
    List<String> segments;

    QualifiedNameNode(List<String> segments) {
        this.segments = segments;
    }
}

class VarDeclNode extends ASTNode {
    TypeNode type;
    String name;

    VarDeclNode(TypeNode type, String name) {
        this.type = type;
        this.name = name;
    }
}

class VarDeclInitNode extends ASTNode {
    TypeNode type;
    String name;
    ASTNode init;

    VarDeclInitNode(TypeNode type, String name, ASTNode init) {
        this.type = type;
        this.name = name;
        this.init = init;
    }
}

class VarDeclArrayInitNode extends ASTNode {
    TypeNode type;
    String name;
    ASTNode size;

    VarDeclArrayInitNode(TypeNode type, String name, ASTNode size) {
        this.type = type;
        this.name = name;
        this.size = size;
    }
}

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

class PrimitiveTypeNode extends TypeNode {
    PrimitiveKind kind;

    PrimitiveTypeNode(PrimitiveKind kind) {
        this.kind = kind;
    }
}

class VoidTypeNode extends TypeNode {
    VoidTypeNode() {}
}

class DynamicArrayTypeNode extends TypeNode {
    TypeNode elementType;

    DynamicArrayTypeNode(TypeNode elementType) {
        this.elementType = elementType;
    }
}

class StaticArrayTypeNode extends TypeNode {
    TypeNode elementType;

    ASTNode sizeExpr;

    StaticArrayTypeNode(TypeNode elementType, ASTNode sizeExpr) {
        this.elementType = elementType;
        this.sizeExpr = sizeExpr;
    }
}

class PtrTypeNode extends TypeNode {
    PtrPermission kind;
    TypeNode referencedType;

    PtrTypeNode(TypeNode referencedType) {
        this(PtrPermission.OWNER, referencedType);
    }

    PtrTypeNode(PtrPermission kind, TypeNode referencedType) {
        this.kind = kind;
        this.referencedType = referencedType;
    }
}

class StructTypeNode extends TypeNode {

    List<String> namespaceSegments;
    String name;

    StructTypeNode(List<String> namespaceSegments, String name) {
        this.namespaceSegments = namespaceSegments;
        this.name = name;
    }
}

class VarNode extends ASTNode {
    String name;

    VarNode(String name) {
        this.name = name;
    }
}

class IntNode extends ASTNode {
    String value;

    IntNode(String value) {
        this.value = value;
    }
}

class InferredSizeNode extends ASTNode {
    InferredSizeNode() {}
}

class ArrayLiteralNode extends ASTNode {
    List<ASTNode> elements;

    ArrayLiteralNode(List<ASTNode> elements) {
        this.elements = elements;
    }
}

class StringLiteralNode extends ASTNode {
    String rawText;
    String rawContent;

    StringLiteralNode(String rawText) {
        this.rawText = rawText;

        if (rawText.length() >= 2) {
            this.rawContent = rawText.substring(1, rawText.length() - 1);
        } else {
            throw new RuntimeException("Invalid string literal token: " + rawText);
        }
    }
}

class UnaryOpNode extends ASTNode {
    String op;
    ASTNode expr;

    UnaryOpNode(String op, ASTNode expr) {
        this.op = op;
        this.expr = expr;
    }
}

class BinOpNode extends ASTNode {
    String op;
    ASTNode left;
    ASTNode right;

    BinOpNode(String op, ASTNode left, ASTNode right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }
}

class ArrayAccessNode extends ASTNode {
    ASTNode target;
    ASTNode index;

    ArrayAccessNode(ASTNode target, ASTNode index) {
        this.target = target;
        this.index = index;
    }
}

class MemberAccessNode extends ASTNode {
    ASTNode target;
    String memberName;

    MemberAccessNode(ASTNode target, String memberName) {
        this.target = target;
        this.memberName = memberName;
    }
}

class CallNode extends ASTNode {
    ASTNode callee;
    List<ASTNode> args;

    CallNode(ASTNode callee, List<ASTNode> args) {
        this.callee = callee;
        this.args = args;
    }
}

class GetPtrNode extends ASTNode {
    PtrPermission kind;
    ASTNode target;

    GetPtrNode(ASTNode target) {
        this(PtrPermission.OWNER, target);
    }

    GetPtrNode(PtrPermission kind, ASTNode target) {
        this.kind = kind;
        this.target = target;
    }
}

class DerefNode extends ASTNode {
    ASTNode expr;

    DerefNode(ASTNode expr) {
        this.expr = expr;
    }
}

class CastNode extends ASTNode {
    TypeNode target;
    ASTNode expr;

    boolean unsafe;

    CastNode(TypeNode target, ASTNode expr) {
        this(target, expr, false);
    }

    CastNode(TypeNode target, ASTNode expr, boolean unsafe) {
        this.target = target;
        this.expr = expr;
        this.unsafe = unsafe;
    }
}

class NullNode extends ASTNode {
}
