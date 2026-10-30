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

enum SemanticIntrinsicArgKind {
    BYTE(SemanticPrimitiveKind.UINT8),
    UINT16(SemanticPrimitiveKind.UINT16),
    UINT32(SemanticPrimitiveKind.UINT32),
    UINT64(SemanticPrimitiveKind.UINT64),
    INT16(SemanticPrimitiveKind.INT16),
    INT32(SemanticPrimitiveKind.INT32),
    INT64(SemanticPrimitiveKind.INT64),
    POINTER(null);

    public final SemanticPrimitiveKind primitiveKind;

    SemanticIntrinsicArgKind(SemanticPrimitiveKind primitiveKind) {
        this.primitiveKind = primitiveKind;
    }

    public boolean isPointer() {
        return primitiveKind == null;
    }

    public SemanticType primitiveType() {
        if (primitiveKind == null) {
            throw new IllegalStateException(
                    "pointer intrinsic argument has no primitive type"
            );
        }

        return new SemanticPrimitiveType(primitiveKind);
    }
}

enum SemanticIntrinsicElementRule {
    NONE,
    TRIVIAL,
    COPIABLE,
    BYTE
}

enum SemanticArrayIntrinsicKind {
    INIT("init", SemanticPrimitiveKind.INT32, true,
            SemanticIntrinsicElementRule.NONE, PtrPermission.OWNER,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    INIT_ZERO("init_zero", SemanticPrimitiveKind.INT32, true,
            SemanticIntrinsicElementRule.NONE, PtrPermission.OWNER,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    RESIZE("resize", SemanticPrimitiveKind.INT32, true,
            SemanticIntrinsicElementRule.NONE, PtrPermission.OWNER,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),

    UNINIT("uninit", null, true,
            SemanticIntrinsicElementRule.NONE, PtrPermission.OWNER,
            new int[]{0}),

    MEMSET("memset", null, false,
            SemanticIntrinsicElementRule.TRIVIAL, PtrPermission.MUTABLE,
            new int[]{1, 3},
            SemanticIntrinsicArgKind.BYTE,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),
    MEMCPY("memcpy", null, false,
            SemanticIntrinsicElementRule.COPIABLE, PtrPermission.MUTABLE,
            new int[]{1, 4},
            SemanticIntrinsicArgKind.POINTER,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),
    MEMMOVE("memmove", null, false,
            SemanticIntrinsicElementRule.COPIABLE, PtrPermission.MUTABLE,
            new int[]{3},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),

    LENGTH("length", SemanticPrimitiveKind.UINT32, false,
            SemanticIntrinsicElementRule.NONE, PtrPermission.READ_ONLY,
            new int[]{0}),

    MEMCMP("memcmp", SemanticPrimitiveKind.INT32, false,
            SemanticIntrinsicElementRule.COPIABLE, PtrPermission.READ_ONLY,
            new int[]{1, 4},
            SemanticIntrinsicArgKind.POINTER,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),

    MEMCHR("memchr", SemanticPrimitiveKind.UINT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1, 3},
            SemanticIntrinsicArgKind.BYTE,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),
    MEMMEM("memmem", SemanticPrimitiveKind.UINT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1, 3},
            SemanticIntrinsicArgKind.POINTER,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),

    READ_UINT16_LE("read_uint16_LE", SemanticPrimitiveKind.UINT16, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_UINT16_BE("read_uint16_BE", SemanticPrimitiveKind.UINT16, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_UINT16_LE("write_uint16_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT16),
    WRITE_UINT16_BE("write_uint16_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT16),

    READ_UINT32_LE("read_uint32_LE", SemanticPrimitiveKind.UINT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_UINT32_BE("read_uint32_BE", SemanticPrimitiveKind.UINT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_UINT32_LE("write_uint32_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),
    WRITE_UINT32_BE("write_uint32_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),

    READ_UINT64_LE("read_uint64_LE", SemanticPrimitiveKind.UINT64, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_UINT64_BE("read_uint64_BE", SemanticPrimitiveKind.UINT64, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_UINT64_LE("write_uint64_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT64),
    WRITE_UINT64_BE("write_uint64_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT64),

    READ_INT16_LE("read_int16_LE", SemanticPrimitiveKind.INT16, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_INT16_BE("read_int16_BE", SemanticPrimitiveKind.INT16, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_INT16_LE("write_int16_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT16),
    WRITE_INT16_BE("write_int16_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT16),

    READ_INT32_LE("read_int32_LE", SemanticPrimitiveKind.INT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_INT32_BE("read_int32_BE", SemanticPrimitiveKind.INT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_INT32_LE("write_int32_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT32),
    WRITE_INT32_BE("write_int32_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT32),

    READ_INT64_LE("read_int64_LE", SemanticPrimitiveKind.INT64, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_INT64_BE("read_int64_BE", SemanticPrimitiveKind.INT64, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_INT64_LE("write_int64_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT64),
    WRITE_INT64_BE("write_int64_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT64),

    READ_BIT("read_bit", SemanticPrimitiveKind.UINT8, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT64),
    WRITE_BIT("write_bit", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT64,
            SemanticIntrinsicArgKind.BYTE),
    FLIP_BIT("flip_bit", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.UINT64),

    BITWISE_NOT_MASK("bitwise_not_mask", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{0}),
    BITWISE_XOR_MASK("bitwise_xor_mask", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.BYTE),
    BITWISE_AND_MASK("bitwise_and_mask", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.BYTE),
    BITWISE_OR_MASK("bitwise_or_mask", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.BYTE),

    SHIFT_BITS_LEFT("shift_bits_left", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.UINT64),
    SHIFT_BITS_RIGHT("shift_bits_right", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.UINT64),

    COUNT_SET_BITS("count_set_bits", SemanticPrimitiveKind.UINT64, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{0});

    public final String runtimeName;
    public final SemanticPrimitiveKind returnKind;
    public final boolean dynamicOnly;
    public final SemanticIntrinsicElementRule elementRule;
    public final PtrPermission permission;
    public final int[] allowedArities;
    public final SemanticIntrinsicArgKind[] argKinds;

    SemanticArrayIntrinsicKind(
            String runtimeName,
            SemanticPrimitiveKind returnKind,
            boolean dynamicOnly,
            SemanticIntrinsicElementRule elementRule,
            PtrPermission permission,
            int[] allowedArities,
            SemanticIntrinsicArgKind... argKinds
    ) {
        this.runtimeName = Objects.requireNonNull(runtimeName);
        this.returnKind = returnKind;
        this.dynamicOnly = dynamicOnly;
        this.elementRule = Objects.requireNonNull(elementRule);
        this.permission = Objects.requireNonNull(permission);
        this.allowedArities = allowedArities;
        this.argKinds = argKinds;
    }

    public static SemanticArrayIntrinsicKind fromRuntimeName(String name) {
        for (SemanticArrayIntrinsicKind kind : values()) {
            if (kind.runtimeName.equals(name)) {
                return kind;
            }
        }

        return null;
    }

    public SemanticType returnType() {
        if (returnKind == null) {
            return new SemanticVoidType();
        }

        return new SemanticPrimitiveType(returnKind);
    }

    public boolean returnsVoid() {
        return returnKind == null;
    }

    public boolean returnsStatus() {
        return this == INIT || this == INIT_ZERO || this == RESIZE;
    }

    public boolean requiresStorageLValue() {
        return permission != PtrPermission.READ_ONLY;
    }

    public int maxArity() {
        int max = 0;

        for (int arity : allowedArities) {
            max = Math.max(max, arity);
        }

        return max;
    }

    public boolean acceptsArity(int arity) {
        for (int allowed : allowedArities) {
            if (allowed == arity) {
                return true;
            }
        }

        return false;
    }

    public SemanticIntrinsicArgKind argKind(int index) {
        if (index < 0 || index >= argKinds.length) {
            throw new IllegalStateException(
                    "intrinsic '" + runtimeName + "' has no argument " + index
            );
        }

        return argKinds[index];
    }

    public boolean hasVariadicArity() {
        return allowedArities.length > 1;
    }

    public String arityDescription() {
        if (allowedArities.length == 1) {
            return "exactly " + allowedArities[0];
        }

        return "either " + allowedArities[0] + " or " + allowedArities[1];
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
        return kind.returnsStatus();
    }

    public int arity() {
        return args.size();
    }

    public boolean isFullArrayForm() {
        return kind.hasVariadicArity() && args.size() == 1;
    }

    public boolean isRangedArrayForm() {
        return kind.hasVariadicArity() && args.size() == kind.maxArity();
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
