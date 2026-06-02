import java.util.List;

// Base AST node
abstract class ASTNode {}

// Program
class ProgramNode extends ASTNode {
    List<ASTNode> statements;

    ProgramNode(List<ASTNode> statements) {
        this.statements = statements;
    }
}

// Block
class BlockNode extends ASTNode {
    List<ASTNode> statements;

    BlockNode(List<ASTNode> statements) {
        this.statements = statements;
    }
}

// Plain variable declaration (core form)
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

// Sugar: declaration with scalar initializer
// Example:
//   int32_t x = 1;
//   alloc int32_t y = 10;
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

// Sugar: declaration with array-style init
// Example:
//   array<int32_t> arr(20);
//   alloc array<int32_t> arr(20);
//
// Later, the Desugarer can lower this into:
//   VarDeclNode(...)
//   ArrayInitNode(name, size)
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

// Assignment
class AssignNode extends ASTNode {
    ASTNode target;
    ASTNode value;

    AssignNode(ASTNode target, ASTNode value) {
        this.target = target;
        this.value = value;
    }
}

// If
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

// While
class WhileNode extends ASTNode {
    ASTNode condition;
    ASTNode body;

    WhileNode(ASTNode condition, ASTNode body) {
        this.condition = condition;
        this.body = body;
    }
}

// Variable reference
class VarNode extends ASTNode {
    String name;

    VarNode(String name) {
        this.name = name;
    }
}

// Integer literal
//
// Keep the original source text exactly as written.
// Examples:
//   "123"
//   "0b10101010"
//   "0xFF"
class IntNode extends ASTNode {
    String value;

    IntNode(String value) {
        this.value = value;
    }
}

// Unary operation
class UnaryOpNode extends ASTNode {
    String op;
    ASTNode expr;

    UnaryOpNode(String op, ASTNode expr) {
        this.op = op;
        this.expr = expr;
    }
}

// Binary operation
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

// Array access
class ArrayAccessNode extends ASTNode {
    ASTNode target;
    ASTNode index;

    ArrayAccessNode(ASTNode target, ASTNode index) {
        this.target = target;
        this.index = index;
    }
}

// Array init
class ArrayInitNode extends ASTNode {
    String name;
    ASTNode size;

    ArrayInitNode(String name, ASTNode size) {
        this.name = name;
        this.size = size;
    }
}

// Array uninit
class ArrayUninitNode extends ASTNode {
    ASTNode receiver;

    ArrayUninitNode(ASTNode receiver) {
        this.receiver = receiver;
    }
}

// Array memset
class ArrayMemsetNode extends ASTNode {
    ASTNode receiver;
    ASTNode value;

    ArrayMemsetNode(ASTNode receiver, ASTNode value) {
        this.receiver = receiver;
        this.value = value;
    }
}

// Array memcpy
class ArrayMemcpyNode extends ASTNode {
    ASTNode target;
    ASTNode source;

    ArrayMemcpyNode(ASTNode target, ASTNode source) {
        this.target = target;
        this.source = source;
    }
}

// Delete
class DelNode extends ASTNode {
    String name;

    DelNode(String name) {
        this.name = name;
    }
}

// Print
class PrintNode extends ASTNode {
    List<ASTNode> args;

    PrintNode(List<ASTNode> args) {
        this.args = args;
    }
}
