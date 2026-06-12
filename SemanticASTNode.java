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
 * Program node contains top-level semantic items.
 *
 * Valid top-level items currently include:
 *
 *   - SemanticFunctionDeclNode
 *   - SemanticStmtNode
 *
 * In practice, SemanticAnalyzer currently permits only global variable
 * declarations and function declarations at global scope.
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
 * state. SemanticAnalyzer/SymbolTable maps raw FunctionDeclNode objects to
 * their registered FunctionSymbol.
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
// Array Intrinsic Calls
// Lowered from CallNode / MemberAccessNode by SemanticAnalyzer
// ============================================================

/**
 * Built-in array intrinsic kind.
 *
 * These correspond to the Franko runtime array methods:
 *
 *   Dynamic-array-only, status-returning:
 *
 *     arr.init(size)       -> INT32
 *     arr.init_zero(size)  -> INT32
 *     arr.resize(size)     -> INT32
 *
 *   Dynamic-array-only, void-returning:
 *
 *     arr.uninit()         -> void
 *
 *   Static-or-dynamic-array, void-returning:
 *
 *     arr.memset(value)                    -> void
 *     arr.memset(value, start, count)      -> void
 *
 *     dst.memcpy(src)                      -> void
 *     dst.memcpy(src, dstStart, srcStart, count)
 *                                          -> void
 *
 *     arr.memmove(dstStart, srcStart, count)
 *                                          -> void
 *
 * Legacy shorthand:
 *
 *     arr(size)
 *     deref(p)(size)
 *
 * should lower to:
 *
 *     SemanticArrayIntrinsicKind.INIT
 */
enum SemanticArrayIntrinsicKind {
    INIT("init", true),
    INIT_ZERO("init_zero", true),
    RESIZE("resize", true),

    UNINIT("uninit", false),

    MEMSET("memset", false),
    MEMCPY("memcpy", false),
    MEMMOVE("memmove", false);

    public final String runtimeName;
    public final boolean returnsStatus;

    SemanticArrayIntrinsicKind(String runtimeName, boolean returnsStatus) {
        this.runtimeName = Objects.requireNonNull(runtimeName);
        this.returnsStatus = returnsStatus;
    }

    /**
     * Returns the semantic return type of this intrinsic.
     *
     * Status-returning array intrinsics map to INT32 because the runtime uses:
     *
     *   int32_t init(...)
     *   int32_t init_zero(...)
     *   int32_t resize(...)
     *
     * Void intrinsics map to SemanticVoidType.
     */
    public SemanticType returnType() {
        if (returnsStatus) {
            return new SemanticPrimitiveType(SemanticPrimitiveKind.INT32);
        }

        return new SemanticVoidType();
    }

    public boolean returnsVoid() {
        return !returnsStatus;
    }
}

/**
 * Unified semantic array intrinsic call expression.
 *
 * This node is intentionally expression-shaped, like SemanticFunctionCallNode.
 * That is important because some array intrinsics return a value:
 *
 *   xs.init(10)       -> INT32
 *   xs.init_zero(10)  -> INT32
 *   xs.resize(20)     -> INT32
 *
 * while others are void:
 *
 *   xs.uninit()       -> void
 *   xs.memset(...)    -> void
 *   dst.memcpy(...)   -> void
 *   xs.memmove(...)   -> void
 *
 * This means all of the following can be represented uniformly:
 *
 *   xs.init(10);
 *
 * as:
 *
 *   SemanticExprStmtNode(
 *       SemanticArrayIntrinsicCallNode(INIT, xs, [10])
 *   )
 *
 * and:
 *
 *   status = xs.init(10);
 *
 * as:
 *
 *   SemanticAssignNode(
 *       status,
 *       SemanticArrayIntrinsicCallNode(INIT, xs, [10])
 *   )
 *
 * For void-returning intrinsics, later semantic checkers should reject
 * value-use contexts:
 *
 *   x = xs.memset(0);       // invalid: memset returns void
 *   return dst.memcpy(src); // invalid unless handled as void return context
 *
 * Receiver:
 *
 *   The receiver is stored separately from args, like an implicit `this`.
 *
 * Examples:
 *
 *   xs.init(10)
 *
 * lowers to:
 *
 *   kind     = INIT
 *   receiver = xs
 *   args     = [10]
 *
 *   xs.memset(0, 2, 5)
 *
 * lowers to:
 *
 *   kind     = MEMSET
 *   receiver = xs
 *   args     = [0, 2, 5]
 *
 *   dst.memcpy(src, 0, 4, 8)
 *
 * lowers to:
 *
 *   kind     = MEMCPY
 *   receiver = dst
 *   args     = [src, 0, 4, 8]
 */
class SemanticArrayIntrinsicCallNode extends SemanticExprNode {
    public final SemanticArrayIntrinsicKind kind;
    public final SemanticExprNode receiver;
    public final List<SemanticExprNode> args;

    public SemanticArrayIntrinsicCallNode(
            SemanticArrayIntrinsicKind kind,
            SemanticExprNode receiver,
            List<SemanticExprNode> args
    ) {
        super(
                Objects.requireNonNull(kind).returnType(),
                null
        );

        this.kind = kind;
        this.receiver = Objects.requireNonNull(receiver);
        this.args = List.copyOf(args);
    }

    @Override
    public boolean isLValue() {
        return false;
    }

    public boolean returnsVoid() {
        return type instanceof SemanticVoidType;
    }

    public boolean returnsStatus() {
        return kind.returnsStatus;
    }

    public int arity() {
        return args.size();
    }

    /**
     * Convenience helpers for overload-shaped intrinsics.
     *
     * These do not replace proper checker validation. They are merely useful
     * for StatementChecker, ExpressionChecker, and codegen.
     */
    public boolean isFullArrayMemset() {
        return kind == SemanticArrayIntrinsicKind.MEMSET
                && args.size() == 1;
    }

    public boolean isRangedMemset() {
        return kind == SemanticArrayIntrinsicKind.MEMSET
                && args.size() == 3;
    }

    public boolean isFullArrayMemcpy() {
        return kind == SemanticArrayIntrinsicKind.MEMCPY
                && args.size() == 1;
    }

    public boolean isRangedMemcpy() {
        return kind == SemanticArrayIntrinsicKind.MEMCPY
                && args.size() == 4;
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