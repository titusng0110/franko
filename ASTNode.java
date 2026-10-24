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

// After desugaring, this ASTNode should not exist.
//
// for (init; condition; update) body
//
// init and update are optional simple statements; condition is a
// required expression.
//
// The desugarer lowers this to:
//
//   {
//       init
//       while (condition) {
//           body
//           update
//       }
//   }
//
// The wrapping block scopes the loop variable declared by init so it is
// not visible after the loop.
class ForNode extends ASTNode {
    ASTNode init;      // simple statement or null
    ASTNode condition;
    ASTNode update;    // simple statement or null
    ASTNode body;

    ForNode(ASTNode init, ASTNode condition, ASTNode update, ASTNode body) {
        this.init = init;
        this.condition = condition;
        this.update = update;
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
// Struct declarations
// ============================================================
//
// structDecl produces a StructDeclNode holding ordered field
// declarations and ordered method declarations. Field initializers,
// when present, are raw constExpr ASTs that the semantic analyzer
// folds into canonical constants. Method declarations are raw
// FunctionDeclNode objects whose first parameter must be
// `addr<X> self` for the enclosing struct X.

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
    ASTNode constInitializer; // null if absent

    StructFieldDeclNode(TypeNode type, String name, ASTNode constInitializer) {
        this.type = type;
        this.name = name;
        this.constInitializer = constInitializer;
    }
}

// ============================================================
// Global variable declarations
// ============================================================
//
// Distinct from VarDeclNode: the Desugarer keys top-level handling off
// node classes, and global const initializers must never be rewritten
// into assignment statements. The initializer is a constExpr AST that
// the desugarer evaluates into a canonical literal before semantic
// analysis; later phases only validate the resulting value.

class GlobalVarDeclNode extends ASTNode {
    TypeNode type;
    String name;
    ASTNode constInitializer; // null if absent

    GlobalVarDeclNode(TypeNode type, String name, ASTNode constInitializer) {
        this.type = type;
        this.name = name;
        this.constInitializer = constInitializer;
    }
}

// ============================================================
// Imports
// ============================================================
//
// import "abc"
//
// This node is an import *directive*, not a symbol declaration.
//
// It exists only in the raw parser AST. The ImportResolver consumes it
// before desugaring: it loads and parses the referenced file and
// splices that file's top-level items into the ProgramNode in place of
// this node. An ImportNode must therefore never reach the Desugarer.
//
// rawPathText holds the escape-decoded literal content without the
// surrounding quotes, e.g. "lib/util" for the source text "lib/util".
//
// Keeping the original path form (instead of a pre-resolved filename)
// leaves a clean seam for namespace-qualified imports in a later
// update.
class ImportNode extends ASTNode {
    String rawPathText;

    ImportNode(String rawPathText) {
        this.rawPathText = rawPathText;
    }
}

// ============================================================
// Namespaces
// ============================================================
//
// namespace util {
//     struct Point { ... }
//     int32_t offset = 100
//     func doubleIt(...) -> ... { ... }
// }
//
// A namespace groups struct, global variable, function, and nested
// namespace declarations under a qualified name.
//
// The name may be a single segment ("util") or a full path ("a::b")
// when the declaration uses the path header form at the top level.
// Nested NamespaceNodes inside items always carry a single-segment
// name; the semantic analyzer prefixes it with the enclosing path
// and rejects path headers nested inside a body.
//
// The same namespace may be declared more than once (same file or
// across imported files, in either form); the semantic analyzer
// merges the contents.
class NamespaceNode extends ASTNode {
    String name;
    List<ASTNode> items;

    NamespaceNode(String name, List<ASTNode> items) {
        this.name = name;
        this.items = items;
    }
}

// ============================================================
// Qualified name references
// ============================================================
//
// ns::name
// a::b::c
//
// Produced wherever a qualified name is syntactically valid:
//
//   - struct type positions:    addr<util::Point>
//   - primary expressions:      util::offset
//   - lvalue positions:         util::offset = 1
//   - call callees:             util::doubleIt(5)
//   - nested paths:             a::b::helper(1)
//
// The last segment names the referenced symbol; every preceding
// segment names a namespace along the path from the top level.
class QualifiedNameNode extends ASTNode {
    List<String> segments;

    QualifiedNameNode(List<String> segments) {
        this.segments = segments;
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

class NdArrayTypeNode extends TypeNode {
    TypeNode elementType;

    /*
     * Dimension expressions in access order.
     *
     * Example:
     *
     *   ndarray<byte, 12, 20, 30>
     *
     * dimensions:
     *
     *   [12, 20, 30]
     *
     * Later desugaring should lower this to:
     *
     *   array<array<array<byte, 30>, 20>, 12>
     *
     * so that:
     *
     *   arr[i][j][k]
     *
     * follows the declared dimension order.
     */
    List<ASTNode> dimensions;

    NdArrayTypeNode(TypeNode elementType, List<ASTNode> dimensions) {
        this.elementType = elementType;
        this.dimensions = dimensions;
    }
}

class AddrTypeNode extends TypeNode {
    TypeNode referencedType;

    AddrTypeNode(TypeNode referencedType) {
        this.referencedType = referencedType;
    }
}

// Struct types are parsed as plain identifiers:
//
//   struct X { int32_t n }
//   X x
//   array<X, 4> xs
//   addr<X> p
//
// The parser accepts any IDENTIFIER as a possible struct type.
// Semantic analysis must verify that the identifier actually names
// a declared struct.
class StructTypeNode extends TypeNode {
    /*
     * Namespace path the struct type is qualified with, e.g. ["util"]
     * for util::Point or ["a", "b"] for a::b::Point. Empty for an
     * unqualified struct type name.
     */
    List<String> namespaceSegments;
    String name;

    StructTypeNode(List<String> namespaceSegments, String name) {
        this.namespaceSegments = namespaceSegments;
        this.name = name;
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
// Inferred static array sizes
// ============================================================
//
// InferredSizeNode is a marker for `?` in a static array size or
// ndarray dimension:
//
//   array<int32_t, ?>
//   ndarray<byte, ?, 3>
//
// It is resolved by the desugarer (only in declarations with an
// array-literal or string-literal initializer) into an IntNode before
// semantic analysis. Any InferredSizeNode that survives desugaring is
// a misplaced `?` and is rejected.

class InferredSizeNode extends ASTNode {
    InferredSizeNode() {}
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

// ============================================================
// Null literal
// ============================================================

class NullNode extends ASTNode {
}