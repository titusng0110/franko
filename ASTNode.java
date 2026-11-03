import java.math.BigInteger;
import java.util.List;

abstract class ASTNode {
    final SourceSpan span;

    ASTNode(SourceSpan span) {
        this.span = span == null ? SourceSpan.NONE : span;
    }
}

abstract class TypeNode extends ASTNode {
    TypeNode(SourceSpan span) {
        super(span);
    }
}

class ProgramNode extends ASTNode {
    List<ASTNode> topLevelItems;

    ProgramNode(SourceSpan span, List<ASTNode> topLevelItems) {
        super(span);
        this.topLevelItems = topLevelItems;
    }
}

class BlockNode extends ASTNode {
    List<ASTNode> statements;

    BlockNode(SourceSpan span, List<ASTNode> statements) {
        super(span);
        this.statements = statements;
    }
}

class ExprStmtNode extends ASTNode {
    ASTNode expr;

    ExprStmtNode(SourceSpan span, ASTNode expr) {
        super(span);
        this.expr = expr;
    }
}

class AssignNode extends ASTNode {
    String op;
    ASTNode target;
    ASTNode value;

    AssignNode(SourceSpan span, String op, ASTNode target, ASTNode value) {
        super(span);
        this.op = op;
        this.target = target;
        this.value = value;
    }
}

class IncDecNode extends ASTNode {
    String op;
    ASTNode target;

    IncDecNode(SourceSpan span, String op, ASTNode target) {
        super(span);
        this.op = op;
        this.target = target;
    }
}

class IfNode extends ASTNode {
    ASTNode condition;
    ASTNode thenBranch;
    ASTNode elseBranch;

    IfNode(SourceSpan span, ASTNode condition, ASTNode thenBranch, ASTNode elseBranch) {
        super(span);
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }
}

class StaticIfNode extends ASTNode {
    ASTNode condition;
    ASTNode thenBranch;
    ASTNode elseBranch;

    StaticIfNode(SourceSpan span, ASTNode condition, ASTNode thenBranch, ASTNode elseBranch) {
        super(span);
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }
}

class WhileNode extends ASTNode {
    ASTNode condition;
    ASTNode body;

    List<ASTNode> update;

    WhileNode(SourceSpan span, ASTNode condition, ASTNode body, List<ASTNode> update) {
        super(span);
        this.condition = condition;
        this.body = body;
        this.update = List.copyOf(update);
    }
}

class BreakNode extends ASTNode {
    ASTNode level;

    BreakNode(SourceSpan span, ASTNode level) {
        super(span);
        this.level = level;
    }
}

class ContinueNode extends ASTNode {
    ASTNode level;

    ContinueNode(SourceSpan span, ASTNode level) {
        super(span);
        this.level = level;
    }
}

class ForNode extends ASTNode {
    ASTNode init;
    ASTNode condition;
    ASTNode update;
    ASTNode body;

    ForNode(SourceSpan span, ASTNode init, ASTNode condition, ASTNode update, ASTNode body) {
        super(span);
        this.init = init;
        this.condition = condition;
        this.update = update;
        this.body = body;
    }
}

class ReturnNode extends ASTNode {
    ASTNode value;

    ReturnNode(SourceSpan span, ASTNode value) {
        super(span);
        this.value = value;
    }
}

class FunctionDeclNode extends ASTNode {
    String name;
    List<ParameterNode> parameters;
    TypeNode returnType;
    BlockNode body;

    FunctionDeclNode(
        SourceSpan span,
        String name,
        List<ParameterNode> parameters,
        TypeNode returnType,
        BlockNode body
    ) {
        super(span);
        this.name = name;
        this.parameters = parameters;
        this.returnType = returnType;
        this.body = body;
    }
}

class ParameterNode extends ASTNode {
    TypeNode type;
    String name;

    ParameterNode(SourceSpan span, TypeNode type, String name) {
        super(span);
        this.type = type;
        this.name = name;
    }
}

class StructDeclNode extends ASTNode {
    String name;
    List<StructFieldDeclNode> fields;
    List<FunctionDeclNode> methods;

    StructDeclNode(
        SourceSpan span,
        String name,
        List<StructFieldDeclNode> fields,
        List<FunctionDeclNode> methods
    ) {
        super(span);
        this.name = name;
        this.fields = fields;
        this.methods = methods;
    }
}

class StructFieldDeclNode extends ASTNode {
    TypeNode type;
    String name;
    ASTNode constInitializer;

    StructFieldDeclNode(SourceSpan span, TypeNode type, String name, ASTNode constInitializer) {
        super(span);
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

    boolean compileTimeConstant;

    GlobalVarDeclNode(SourceSpan span, TypeNode type, String name, ASTNode constInitializer) {
        super(span);
        this.type = type;
        this.name = name;
        this.constInitializer = constInitializer;
    }
}

class ImportNode extends ASTNode {
    String rawPathText;

    ImportNode(SourceSpan span, String rawPathText) {
        super(span);
        this.rawPathText = rawPathText;
    }
}

class NamespaceNode extends ASTNode {
    String name;
    List<ASTNode> items;

    NamespaceNode(SourceSpan span, String name, List<ASTNode> items) {
        super(span);
        this.name = name;
        this.items = items;
    }
}

class QualifiedNameNode extends ASTNode {
    List<String> segments;

    QualifiedNameNode(SourceSpan span, List<String> segments) {
        super(span);
        this.segments = segments;
    }
}

class VarDeclNode extends ASTNode {
    TypeNode type;
    String name;

    VarDeclNode(SourceSpan span, TypeNode type, String name) {
        super(span);
        this.type = type;
        this.name = name;
    }
}

class VarDeclInitNode extends ASTNode {
    TypeNode type;
    String name;
    ASTNode init;

    VarDeclInitNode(SourceSpan span, TypeNode type, String name, ASTNode init) {
        super(span);
        this.type = type;
        this.name = name;
        this.init = init;
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

    PrimitiveTypeNode(SourceSpan span, PrimitiveKind kind) {
        super(span);
        this.kind = kind;
    }
}

class VoidTypeNode extends TypeNode {
    VoidTypeNode(SourceSpan span) {
        super(span);
    }
}

class DynamicArrayTypeNode extends TypeNode {
    TypeNode elementType;

    DynamicArrayTypeNode(SourceSpan span, TypeNode elementType) {
        super(span);
        this.elementType = elementType;
    }
}

class StaticArrayTypeNode extends TypeNode {
    TypeNode elementType;

    ASTNode sizeExpr;

    StaticArrayTypeNode(SourceSpan span, TypeNode elementType, ASTNode sizeExpr) {
        super(span);
        this.elementType = elementType;
        this.sizeExpr = sizeExpr;
    }
}

class PtrTypeNode extends TypeNode {
    PtrPermission kind;
    TypeNode referencedType;

    PtrTypeNode(SourceSpan span, PtrPermission kind, TypeNode referencedType) {
        super(span);
        this.kind = kind;
        this.referencedType = referencedType;
    }
}

class StructTypeNode extends TypeNode {

    List<String> namespaceSegments;
    String name;

    StructTypeNode(SourceSpan span, List<String> namespaceSegments, String name) {
        super(span);
        this.namespaceSegments = namespaceSegments;
        this.name = name;
    }
}

class VarNode extends ASTNode {
    String name;

    VarNode(SourceSpan span, String name) {
        super(span);
        this.name = name;
    }
}

class IntNode extends ASTNode {
    String value;

    IntNode(SourceSpan span, String value) {
        super(span);
        this.value = value;
    }
}

class InferredSizeNode extends ASTNode {
    InferredSizeNode(SourceSpan span) {
        super(span);
    }
}

class ArrayLiteralNode extends ASTNode {
    List<ASTNode> elements;

    ArrayLiteralNode(SourceSpan span, List<ASTNode> elements) {
        super(span);
        this.elements = elements;
    }
}

class StringLiteralNode extends ASTNode {
    String rawText;
    String rawContent;

    StringLiteralNode(SourceSpan span, String rawText) {
        super(span);
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

    UnaryOpNode(SourceSpan span, String op, ASTNode expr) {
        super(span);
        this.op = op;
        this.expr = expr;
    }
}

class BinOpNode extends ASTNode {
    String op;
    ASTNode left;
    ASTNode right;

    BinOpNode(SourceSpan span, String op, ASTNode left, ASTNode right) {
        super(span);
        this.op = op;
        this.left = left;
        this.right = right;
    }
}

class ArrayAccessNode extends ASTNode {
    ASTNode target;
    ASTNode index;

    ArrayAccessNode(SourceSpan span, ASTNode target, ASTNode index) {
        super(span);
        this.target = target;
        this.index = index;
    }
}

class MemberAccessNode extends ASTNode {
    ASTNode target;
    String memberName;

    MemberAccessNode(SourceSpan span, ASTNode target, String memberName) {
        super(span);
        this.target = target;
        this.memberName = memberName;
    }
}

class CallNode extends ASTNode {
    ASTNode callee;
    List<ASTNode> args;

    CallNode(SourceSpan span, ASTNode callee, List<ASTNode> args) {
        super(span);
        this.callee = callee;
        this.args = args;
    }
}

class GetPtrNode extends ASTNode {
    PtrPermission kind;
    ASTNode target;

    GetPtrNode(SourceSpan span, PtrPermission kind, ASTNode target) {
        super(span);
        this.kind = kind;
        this.target = target;
    }
}

class DerefNode extends ASTNode {
    ASTNode expr;

    DerefNode(SourceSpan span, ASTNode expr) {
        super(span);
        this.expr = expr;
    }
}

class CastNode extends ASTNode {
    TypeNode target;
    ASTNode expr;

    boolean unsafe;

    CastNode(SourceSpan span, TypeNode target, ASTNode expr, boolean unsafe) {
        super(span);
        this.target = target;
        this.expr = expr;
        this.unsafe = unsafe;
    }
}

class NullNode extends ASTNode {
    NullNode(SourceSpan span) {
        super(span);
    }
}
