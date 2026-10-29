import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

abstract class SemanticASTNode {}

abstract class SemanticStmtNode extends SemanticASTNode {}

abstract class SemanticExprNode extends SemanticASTNode {
    public final SemanticType type;
    public final BigInteger constantValue;

    protected SemanticExprNode(SemanticType type, BigInteger constantValue) {
        this.type = Objects.requireNonNull(type);
        this.constantValue = constantValue;
    }

    public boolean isConstant() {
        return constantValue != null;
    }

    public abstract boolean isLValue();
}

class SemanticProgramNode extends SemanticASTNode {
    public final List<SemanticASTNode> topLevelItems;

    public SemanticProgramNode(List<SemanticASTNode> topLevelItems) {
        this.topLevelItems = List.copyOf(topLevelItems);
    }
}

class SemanticNamespaceNode extends SemanticASTNode {
    public final String name;
    public final List<SemanticASTNode> items;

    public SemanticNamespaceNode(String name, List<SemanticASTNode> items) {
        this.name = Objects.requireNonNull(name);
        this.items = List.copyOf(items);
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

class SemanticStructDeclNode extends SemanticASTNode {
    public final StructSymbol symbol;
    public final List<SemanticFunctionDeclNode> methods;

    public SemanticStructDeclNode(StructSymbol symbol) {
        this(symbol, List.of());
    }

    public SemanticStructDeclNode(
            StructSymbol symbol,
            List<SemanticFunctionDeclNode> methods
    ) {
        this.symbol = Objects.requireNonNull(symbol);
        this.methods = List.copyOf(methods);
    }
}

class SemanticVarDeclNode extends SemanticStmtNode {
    public final VariableSymbol symbol;
    public final BigInteger foldedConstInitializer;

    public final SemanticExprNode staticInitializer;

    public SemanticVarDeclNode(VariableSymbol symbol) {
        this(symbol, null, null);
    }

    public SemanticVarDeclNode(
            VariableSymbol symbol,
            BigInteger foldedConstInitializer
    ) {
        this(symbol, foldedConstInitializer, null);
    }

    public SemanticVarDeclNode(
            VariableSymbol symbol,
            BigInteger foldedConstInitializer,
            SemanticExprNode staticInitializer
    ) {
        this.symbol = Objects.requireNonNull(symbol);
        this.foldedConstInitializer = foldedConstInitializer;
        this.staticInitializer = staticInitializer;
    }
}

class SemanticAssignNode extends SemanticStmtNode {
    public final SemanticExprNode target;
    public final SemanticExprNode value;

    public final String op;

    public SemanticAssignNode(SemanticExprNode target, SemanticExprNode value) {
        this("=", target, value);
    }

    public SemanticAssignNode(
            String op,
            SemanticExprNode target,
            SemanticExprNode value
    ) {
        this.op = Objects.requireNonNull(op);
        this.target = Objects.requireNonNull(target);
        this.value = Objects.requireNonNull(value);
    }

    public boolean isCompound() {
        return !"=".equals(op);
    }

    public String baseOp() {
        return isCompound() ? op.substring(0, op.length() - 1) : "=";
    }
}

class SemanticIfNode extends SemanticStmtNode {
    public final SemanticExprNode condition;
    public final SemanticStmtNode thenBranch;
    public final SemanticStmtNode elseBranch;

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

    public final List<SemanticStmtNode> update;

    public SemanticWhileNode(SemanticExprNode condition, SemanticStmtNode body) {
        this.condition = Objects.requireNonNull(condition);
        this.body = Objects.requireNonNull(body);
        this.update = List.of();
    }

    public SemanticWhileNode(
            SemanticExprNode condition,
            SemanticStmtNode body,
            List<SemanticStmtNode> update
    ) {
        this.condition = Objects.requireNonNull(condition);
        this.body = Objects.requireNonNull(body);
        this.update = List.copyOf(update);
    }
}

class SemanticBreakNode extends SemanticStmtNode {
    public final int levels;

    public SemanticBreakNode(int levels) {
        if (levels < 1) {
            throw new IllegalArgumentException(
                    "break level must be >= 1, got: " + levels
            );
        }

        this.levels = levels;
    }
}

class SemanticContinueNode extends SemanticStmtNode {
}

class SemanticReturnNode extends SemanticStmtNode {
    public final FunctionSymbol function;
    public final SemanticExprNode value;

    public SemanticReturnNode(FunctionSymbol function, SemanticExprNode value) {
        this.function = Objects.requireNonNull(function);
        this.value = value;
    }
}

enum SemanticArrayIntrinsicKind {
    INIT("init", true),
    INIT_ZERO("init_zero", true),
    RESIZE("resize", true),

    UNINIT("uninit", false),

    MEMSET("memset", false),
    MEMCPY("memcpy", false),
    MEMMOVE("memmove", false),

    LENGTH("length", false, true);

    public final String runtimeName;
    public final boolean returnsStatus;
    public final boolean returnsLength;

    SemanticArrayIntrinsicKind(String runtimeName, boolean returnsStatus) {
        this(runtimeName, returnsStatus, false);
    }

    SemanticArrayIntrinsicKind(String runtimeName, boolean returnsStatus, boolean returnsLength) {
        this.runtimeName = Objects.requireNonNull(runtimeName);
        this.returnsStatus = returnsStatus;
        this.returnsLength = returnsLength;
    }

    public SemanticType returnType() {
        if (returnsStatus) {
            return new SemanticPrimitiveType(SemanticPrimitiveKind.INT32);
        }

        if (returnsLength) {
            return new SemanticPrimitiveType(SemanticPrimitiveKind.UINT32);
        }

        return new SemanticVoidType();
    }

    public boolean returnsVoid() {
        return !returnsStatus && !returnsLength;
    }
}

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

class SemanticIntLiteralNode extends SemanticExprNode {
    public final String rawValue;

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

class SemanticNullLiteralNode extends SemanticExprNode {
    public SemanticNullLiteralNode(SemanticType type) {
        super(type, null);
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

class SemanticMemberAccessNode extends SemanticExprNode {
    public final SemanticExprNode target;
    public final FieldSymbol field;

    public SemanticMemberAccessNode(
            SemanticType type,
            SemanticExprNode target,
            FieldSymbol field
    ) {
        super(type, null);
        this.target = Objects.requireNonNull(target);
        this.field = Objects.requireNonNull(field);
    }

    @Override
    public boolean isLValue() {
        return target.isLValue();
    }
}

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

class SemanticGetPtrNode extends SemanticExprNode {
    public final SemanticExprNode target;

    public SemanticGetPtrNode(SemanticType type, SemanticExprNode target) {
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

class SemanticCastExprNode extends SemanticExprNode {
    public final SemanticExprNode expr;
    public final boolean checked;

    public SemanticCastExprNode(
            SemanticType type,
            BigInteger constantValue,
            SemanticExprNode expr,
            boolean checked
    ) {
        super(type, constantValue);
        this.expr = Objects.requireNonNull(expr);
        this.checked = checked;
    }

    @Override
    public boolean isLValue() {
        return false;
    }
}
