import java.util.List;

// ============================================================
// Base nodes
// ============================================================

abstract class ASTNode {}

// Expressions and types are also AST nodes
abstract class TypeNode extends ASTNode {}

// ============================================================
// Program / top-level / block / statements
// ============================================================

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
    ASTNode target;
    ASTNode value;

    AssignNode(ASTNode target, ASTNode value) {
        this.target = target;
        this.value = value;
    }
}

class IfNode extends ASTNode {
    ASTNode condition;
    ASTNode thenBranch;
    ASTNode elseBranch; // null if absent

    IfNode(ASTNode condition, ASTNode thenBranch, ASTNode elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }
}

class WhileNode extends ASTNode {
    ASTNode condition;
    ASTNode body;

    WhileNode(ASTNode condition, ASTNode body) {
        this.condition = condition;
        this.body = body;
    }
}

class DelNode extends ASTNode {
    String name;

    DelNode(String name) {
        this.name = name;
    }
}

class PrintNode extends ASTNode {
    List<ASTNode> args;

    PrintNode(List<ASTNode> args) {
        this.args = args;
    }
}

class ReturnNode extends ASTNode {
    ASTNode value; // null for `return;`

    ReturnNode(ASTNode value) {
        this.value = value;
    }
}

// ============================================================
// Function declarations
// ============================================================

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

// ============================================================
// Declarations
// ============================================================

class VarDeclNode extends ASTNode {
    TypeNode type;
    String name;
    boolean isHeap;

    VarDeclNode(TypeNode type, String name, boolean isHeap) {
        this.type = type;
        this.name = name;
        this.isHeap = isHeap;
    }
}

// After desugaring, this ASTNode should not exist.
//
// Handles ordinary declaration initializers:
//
//   int32_t x = 1 + 2;
//
// and array literal declaration initializers:
//
//   array<int32_t, 3> xs = [1, 2, 3];
//
class VarDeclInitNode extends ASTNode {
    TypeNode type;
    String name;
    boolean isHeap;
    ASTNode init;

    VarDeclInitNode(TypeNode type, String name, boolean isHeap, ASTNode init) {
        this.type = type;
        this.name = name;
        this.isHeap = isHeap;
        this.init = init;
    }
}

// After desugaring, this ASTNode should not exist.
//
// Existing dynamic array initialization sugar:
//
//   array<int32_t> xs(3);
//
class VarDeclArrayInitNode extends ASTNode {
    TypeNode type;
    String name;
    boolean isHeap;
    ASTNode size;

    VarDeclArrayInitNode(TypeNode type, String name, boolean isHeap, ASTNode size) {
        this.type = type;
        this.name = name;
        this.isHeap = isHeap;
        this.size = size;
    }
}

// ============================================================
// Types
// ============================================================

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

    // Static array sizes are now constExpr, not just integerLiteral.
    //
    // Examples:
    //   array<int32_t, 3>
    //   array<int32_t, 1 + 2>
    //   array<int32_t, 1 << 3>
    //
    // The semantic checker should fold this expression and check:
    //   - it is valid at compile time
    //   - it is positive
    //   - it fits uint32_t
    ASTNode sizeExpr;

    StaticArrayTypeNode(TypeNode elementType, ASTNode sizeExpr) {
        this.elementType = elementType;
        this.sizeExpr = sizeExpr;
    }
}

class AddrTypeNode extends TypeNode {
    TypeNode referencedType;

    AddrTypeNode(TypeNode referencedType) {
        this.referencedType = referencedType;
    }
}

// ============================================================
// Primary / atoms
// ============================================================

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

// ============================================================
// Array initializer lists
// ============================================================
//
// ArrayLiteralNode represents initializer-list syntax:
//
//   [1, 2, 3]
//   [[1, 2], [3, 4]]
//
// It is not an ordinary expression value.
//
// It may appear only in assignment/declaration initializer contexts:
//
//   xs = [1, 2, 3];
//   array<int, 3> xs = [1, 2, 3];
//
// Desugaring lowers:
//
//   target = [a, b, c];
//
// into:
//
//   target[0] = a;
//   target[1] = b;
//   target[2] = c;
//
// Nested initializer lists recursively lower to indexed assignments.
//
// The initializer list does not allocate, resize, or initialize dynamic
// array storage.

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

// ============================================================
// Unary / binary expressions
// ============================================================

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

// ============================================================
// Postfix / access / call
// ============================================================

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

// ============================================================
// Address / pointer-style expressions
// ============================================================

class GetAddrNode extends ASTNode {
    ASTNode target;

    GetAddrNode(ASTNode target) {
        this.target = target;
    }
}

class DerefNode extends ASTNode {
    ASTNode expr;

    DerefNode(ASTNode expr) {
        this.expr = expr;
    }
}