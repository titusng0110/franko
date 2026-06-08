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
    public final BigInteger constantValue; // Populated by SemanticAnalyzer if foldable

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

/**
 * Program node now contains top-level items, not only statements.
 *
 * Valid top-level items currently include:
 *
 *   - SemanticFunctionDeclNode
 *   - SemanticStmtNode
 *
 * This matches the raw grammar:
 *
 *   program
 *       : separators* (topLevelItem separators*)* EOF
 *       ;
 *
 *   topLevelItem
 *       : functionDecl
 *       | statement
 *       ;
 */
class SemanticProgramNode extends SemanticASTNode {
    public final List<SemanticASTNode> topLevelItems;

    public SemanticProgramNode(List<SemanticASTNode> topLevelItems) {
        this.topLevelItems = List.copyOf(topLevelItems);
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

/**
 * Semantic function declaration.
 *
 * The FunctionSymbol stores:
 *
 *   - function name,
 *   - parameter symbols,
 *   - function type/signature,
 *   - builtin flag.
 *
 * Raw AST declaration ownership is analyzer-local bookkeeping, not symbol
 * state. SemanticAnalyzer.Context maps raw FunctionDeclNode objects to their
 * registered FunctionSymbol using IdentityHashMap.
 *
 * parameterVariables are the function parameters as ordinary local variables
 * declared in the function's initial scope.
 */
class SemanticFunctionDeclNode extends SemanticASTNode {
    public final FunctionSymbol symbol;
    public final List<VariableSymbol> parameterVariables;
    public final SemanticBlockNode body;

    public SemanticFunctionDeclNode(
            FunctionSymbol symbol,
            List<VariableSymbol> parameterVariables,
            SemanticBlockNode body
    ) {
        this.symbol = Objects.requireNonNull(symbol);
        this.parameterVariables = List.copyOf(parameterVariables);
        this.body = Objects.requireNonNull(body);
    }
}

// ============================================================
// Declarations
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

    public SemanticIfNode(
            SemanticExprNode condition,
            SemanticStmtNode thenBranch,
            SemanticStmtNode elseBranch
    ) {
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

/**
 * Return statement.
 *
 * The SemanticAnalyzer attaches the function that owns this return.
 *
 * value is:
 *
 *   - null for `return;`
 *   - non-null for `return expr;`
 *
 * The analyzer does not enforce return compatibility.
 * Later statement/type checkers should validate:
 *
 *   - return outside a function,
 *   - return; in non-void functions,
 *   - return expr; in void functions,
 *   - return expression type compatibility,
 *   - missing return in non-void functions.
 */
class SemanticReturnNode extends SemanticStmtNode {
    public final FunctionSymbol function;
    public final SemanticExprNode value; // null for `return;`

    public SemanticReturnNode(FunctionSymbol function, SemanticExprNode value) {
        this.function = Objects.requireNonNull(function);
        this.value = value;
    }
}

// ============================================================
// Intrinsics
// Lowered from CallNode / MemberAccessNode by SemanticAnalyzer
// ============================================================

/**
 * Dynamic array initialization:
 *
 *     target(size)
 *
 * This used to store a direct VariableSymbol. That was too restrictive because
 * it prevented valid storage-backed array lvalues such as:
 *
 *     deref(p)(20);
 *
 * The node now stores the initialized array target as a SemanticExprNode.
 *
 * Valid targets are checked later by StatementChecker:
 *
 *   - target must be a storage-backed lvalue,
 *   - target must have dynamic array type,
 *   - size must be a valid uint32_t-compatible array size.
 */
class SemanticArrayInitNode extends SemanticStmtNode {
    public final SemanticExprNode target;
    public final SemanticExprNode size;

    public SemanticArrayInitNode(SemanticExprNode target, SemanticExprNode size) {
        this.target = Objects.requireNonNull(target);
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

    public SemanticArrayMemsetNode(
            SemanticExprNode receiver,
            SemanticExprNode value
    ) {
        this.receiver = Objects.requireNonNull(receiver);
        this.value = Objects.requireNonNull(value);
    }
}

class SemanticArrayMemcpyNode extends SemanticStmtNode {
    public final SemanticExprNode target;
    public final SemanticExprNode source;

    public SemanticArrayMemcpyNode(
            SemanticExprNode target,
            SemanticExprNode source
    ) {
        this.target = Objects.requireNonNull(target);
        this.source = Objects.requireNonNull(source);
    }
}

// ============================================================
// Expressions
// ============================================================

class SemanticIntLiteralNode extends SemanticExprNode {
    public final String rawValue; // Keeping for exact codegen representation

    public SemanticIntLiteralNode(
            SemanticType type,
            BigInteger constantValue,
            String rawValue
    ) {
        super(type, constantValue);
        this.rawValue = Objects.requireNonNull(rawValue);
    }

    @Override
    public boolean isLValue() {
        return false;
    }
}

class SemanticVarExprNode extends SemanticExprNode {
    public final VariableSymbol symbol;

    public SemanticVarExprNode(SemanticType type, VariableSymbol symbol) {
        super(type, null);
        this.symbol = Objects.requireNonNull(symbol);
    }

    @Override
    public boolean isLValue() {
        return true;
    }
}

class SemanticUnaryOpNode extends SemanticExprNode {
    public final String op;
    public final SemanticExprNode expr;

    public SemanticUnaryOpNode(
            SemanticType type,
            BigInteger constantValue,
            String op,
            SemanticExprNode expr
    ) {
        super(type, constantValue);
        this.op = Objects.requireNonNull(op);
        this.expr = Objects.requireNonNull(expr);
    }

    @Override
    public boolean isLValue() {
        return false;
    }
}

class SemanticBinOpNode extends SemanticExprNode {
    public final String op;
    public final SemanticExprNode left;
    public final SemanticExprNode right;

    public SemanticBinOpNode(
            SemanticType type,
            BigInteger constantValue,
            String op,
            SemanticExprNode left,
            SemanticExprNode right
    ) {
        super(type, constantValue);
        this.op = Objects.requireNonNull(op);
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
    }

    @Override
    public boolean isLValue() {
        return false;
    }
}

class SemanticArrayAccessNode extends SemanticExprNode {
    public final SemanticExprNode target;
    public final SemanticExprNode index;

    public SemanticArrayAccessNode(
            SemanticType type,
            SemanticExprNode target,
            SemanticExprNode index
    ) {
        super(type, null);
        this.target = Objects.requireNonNull(target);
        this.index = Objects.requireNonNull(index);
    }

    @Override
    public boolean isLValue() {
        return true;
    }
}

/**
 * User-defined function call.
 *
 * This is produced from raw:
 *
 *   CallNode(
 *       callee = VarNode("foo"),
 *       args = [...]
 *   )
 *
 * after overload resolution.
 *
 * The expression type is the selected function's return type.
 * If the return type is void, the later checker should reject value-use
 * contexts where void is not allowed.
 */
class SemanticFunctionCallNode extends SemanticExprNode {
    public final FunctionSymbol function;
    public final List<SemanticExprNode> args;

    public SemanticFunctionCallNode(
            SemanticType type,
            FunctionSymbol function,
            List<SemanticExprNode> args
    ) {
        super(type, null);
        this.function = Objects.requireNonNull(function);
        this.args = List.copyOf(args);
    }

    @Override
    public boolean isLValue() {
        return false;
    }
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

    @Override
    public boolean isLValue() {
        return false;
    }
}

class SemanticDerefNode extends SemanticExprNode {
    public final SemanticExprNode expr;

    public SemanticDerefNode(SemanticType type, SemanticExprNode expr) {
        super(type, null);
        this.expr = Objects.requireNonNull(expr);
    }

    @Override
    public boolean isLValue() {
        return true;
    }
}