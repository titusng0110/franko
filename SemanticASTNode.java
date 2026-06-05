import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

// ============================================================
// Base Nodes
// ============================================================

abstract class SemanticASTNode {}

abstract class SemanticStmtNode extends SemanticASTNode {}

abstract class SemanticExprNode extends SemanticASTNode {
    public final SemanticType type;
    public final BigInteger constantValue; // Populated by Semantic Analyzer if foldable

    protected SemanticExprNode(SemanticType type, BigInteger constantValue) {
        this.type = Objects.requireNonNull(type);
        this.constantValue = constantValue;
    }

    public boolean isConstant() {
        return constantValue != null;
    }

    public abstract boolean isLValue();
}

// ============================================================
// Program / Blocks
// ============================================================

class SemanticProgramNode extends SemanticStmtNode {
    public final List<SemanticStmtNode> statements;
    public SemanticProgramNode(List<SemanticStmtNode> statements) {
        this.statements = List.copyOf(statements);
    }
}

class SemanticBlockNode extends SemanticStmtNode {
    public final List<SemanticStmtNode> statements;
    public SemanticBlockNode(List<SemanticStmtNode> statements) {
        this.statements = List.copyOf(statements);
    }
}

class SemanticExprStmtNode extends SemanticStmtNode {
    public final SemanticExprNode expr;
    public SemanticExprStmtNode(SemanticExprNode expr) {
        this.expr = Objects.requireNonNull(expr);
    }
}

// ============================================================
// Declarations (String names replaced by VariableSymbol)
// ============================================================

class SemanticVarDeclNode extends SemanticStmtNode {
    public final VariableSymbol symbol;
    public SemanticVarDeclNode(VariableSymbol symbol) {
        this.symbol = Objects.requireNonNull(symbol);
    }
}


// ============================================================
// General Statements
// ============================================================

class SemanticAssignNode extends SemanticStmtNode {
    public final SemanticExprNode target;
    public final SemanticExprNode value;
    public SemanticAssignNode(SemanticExprNode target, SemanticExprNode value) {
        this.target = Objects.requireNonNull(target);
        this.value = Objects.requireNonNull(value);
    }
}

class SemanticIfNode extends SemanticStmtNode {
    public final SemanticExprNode condition;
    public final SemanticStmtNode thenBranch;
    public final SemanticStmtNode elseBranch; // null if absent
    public SemanticIfNode(SemanticExprNode condition, SemanticStmtNode thenBranch, SemanticStmtNode elseBranch) {
        this.condition = Objects.requireNonNull(condition);
        this.thenBranch = Objects.requireNonNull(thenBranch);
        this.elseBranch = elseBranch;
    }
}

class SemanticWhileNode extends SemanticStmtNode {
    public final SemanticExprNode condition;
    public final SemanticStmtNode body;
    public SemanticWhileNode(SemanticExprNode condition, SemanticStmtNode body) {
        this.condition = Objects.requireNonNull(condition);
        this.body = Objects.requireNonNull(body);
    }
}

class SemanticDelNode extends SemanticStmtNode {
    public final VariableSymbol symbol;
    public SemanticDelNode(VariableSymbol symbol) {
        this.symbol = Objects.requireNonNull(symbol);
    }
}

class SemanticPrintNode extends SemanticStmtNode {
    public final List<SemanticExprNode> args;
    public SemanticPrintNode(List<SemanticExprNode> args) {
        this.args = List.copyOf(args);
    }
}

// ============================================================
// Intrinsics (Lowered from Call/MemberAccess by Analyzer)
// ============================================================

class SemanticArrayInitNode extends SemanticStmtNode {
    public final VariableSymbol symbol;
    public final SemanticExprNode size;
    public SemanticArrayInitNode(VariableSymbol symbol, SemanticExprNode size) {
        this.symbol = Objects.requireNonNull(symbol);
        this.size = Objects.requireNonNull(size);
    }
}

class SemanticArrayUninitNode extends SemanticStmtNode {
    public final SemanticExprNode receiver;
    public SemanticArrayUninitNode(SemanticExprNode receiver) {
        this.receiver = Objects.requireNonNull(receiver);
    }
}

class SemanticArrayMemsetNode extends SemanticStmtNode {
    public final SemanticExprNode receiver;
    public final SemanticExprNode value;
    public SemanticArrayMemsetNode(SemanticExprNode receiver, SemanticExprNode value) {
        this.receiver = Objects.requireNonNull(receiver);
        this.value = Objects.requireNonNull(value);
    }
}

class SemanticArrayMemcpyNode extends SemanticStmtNode {
    public final SemanticExprNode target;
    public final SemanticExprNode source;
    public SemanticArrayMemcpyNode(SemanticExprNode target, SemanticExprNode source) {
        this.target = Objects.requireNonNull(target);
        this.source = Objects.requireNonNull(source);
    }
}

// ============================================================
// Expressions
// ============================================================

class SemanticIntLiteralNode extends SemanticExprNode {
    public final String rawValue; // Keeping for exact codegen representation
    public SemanticIntLiteralNode(SemanticType type, BigInteger constantValue, String rawValue) {
        super(type, constantValue);
        this.rawValue = Objects.requireNonNull(rawValue);
    }
    @Override public boolean isLValue() { return false; }
}

class SemanticVarExprNode extends SemanticExprNode {
    public final VariableSymbol symbol;
    public SemanticVarExprNode(SemanticType type, VariableSymbol symbol) {
        super(type, null);
        this.symbol = Objects.requireNonNull(symbol);
    }
    @Override public boolean isLValue() { return true; }
}

class SemanticUnaryOpNode extends SemanticExprNode {
    public final String op;
    public final SemanticExprNode expr;
    public SemanticUnaryOpNode(SemanticType type, BigInteger constantValue, String op, SemanticExprNode expr) {
        super(type, constantValue);
        this.op = Objects.requireNonNull(op);
        this.expr = Objects.requireNonNull(expr);
    }
    @Override public boolean isLValue() { return false; }
}

class SemanticBinOpNode extends SemanticExprNode {
    public final String op;
    public final SemanticExprNode left;
    public final SemanticExprNode right;
    public SemanticBinOpNode(SemanticType type, BigInteger constantValue, String op, SemanticExprNode left, SemanticExprNode right) {
        super(type, constantValue);
        this.op = Objects.requireNonNull(op);
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
    }
    @Override public boolean isLValue() { return false; }
}

class SemanticArrayAccessNode extends SemanticExprNode {
    public final SemanticExprNode target;
    public final SemanticExprNode index;
    public SemanticArrayAccessNode(SemanticType type, SemanticExprNode target, SemanticExprNode index) {
        super(type, null);
        this.target = Objects.requireNonNull(target);
        this.index = Objects.requireNonNull(index);
    }
    @Override public boolean isLValue() { return true; }
}

// ============================================================
// Address / Pointer Expressions
// ============================================================

class SemanticGetAddrNode extends SemanticExprNode {
    public final SemanticExprNode target;
    public SemanticGetAddrNode(SemanticType type, SemanticExprNode target) {
        super(type, null);
        this.target = Objects.requireNonNull(target);
    }
    @Override public boolean isLValue() { return false; }
}

class SemanticDerefNode extends SemanticExprNode {
    public final SemanticExprNode expr;
    public SemanticDerefNode(SemanticType type, SemanticExprNode expr) {
        super(type, null);
        this.expr = Objects.requireNonNull(expr);
    }
    @Override public boolean isLValue() { return true; }
}
