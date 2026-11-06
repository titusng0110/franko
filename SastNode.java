import java.math.BigInteger;
import java.util.List;

final class SastNode {
    final SastKind kind;
    final SourceSpan span;

    SastType type;
    BigInteger constantValue;

    Symbol symbol;
    Symbol function;
    StructSymbol structSymbol;
    FieldSymbol field;
    EnumSymbol enumSymbol;
    EnumVariant variant;
    SemanticArrayIntrinsicKind intrinsicKind;

    String name;
    String op;
    String rawValue;
    int levels;
    boolean temporary;
    boolean checked;

    SastNode target;
    SastNode value;
    SastNode receiver;
    SastNode condition;
    SastNode thenBranch;
    SastNode elseBranch;
    SastNode body;
    SastNode expr;
    SastNode left;
    SastNode right;
    SastNode index;
    SastNode staticInitializer;

    List<SastNode> topLevelItems;
    List<SastNode> items;
    List<SastNode> statements;
    List<SastNode> methods;
    List<SastNode> args;
    List<SastNode> fields;
    List<SastNode> update;
    List<Symbol> parameterVariables;

    BigInteger foldedConstInitializer;

    private SastNode(SastKind kind, SourceSpan span) {
        this.kind = kind;
        this.span = span == null ? SourceSpan.NONE : span;
    }

    private static SastNode node(SastKind kind, SourceSpan span) {
        return new SastNode(kind, span);
    }

    private static List<SastNode> copy(List<SastNode> nodes) {
        return nodes == null ? List.of() : List.copyOf(nodes);
    }

    static SastNode program(SourceSpan span, List<SastNode> topLevelItems) {
        SastNode node = node(SastKind.PROGRAM, span);
        node.topLevelItems = copy(topLevelItems);
        return node;
    }

    static SastNode namespace(SourceSpan span, String name, List<SastNode> items) {
        SastNode node = node(SastKind.NAMESPACE, span);
        node.name = name;
        node.items = copy(items);
        return node;
    }

    static SastNode functionDecl(
            SourceSpan span,
            Symbol symbol,
            List<Symbol> parameterVariables,
            SastNode body
    ) {
        SastNode node = node(SastKind.FUNCTION_DECL, span);
        node.symbol = symbol;
        node.parameterVariables = List.copyOf(parameterVariables);
        node.body = body;
        return node;
    }

    static SastNode structDecl(SourceSpan span, StructSymbol symbol) {
        return structDecl(span, symbol, List.of());
    }

    static SastNode structDecl(
            SourceSpan span,
            StructSymbol symbol,
            List<SastNode> methods
    ) {
        SastNode node = node(SastKind.STRUCT_DECL, span);
        node.structSymbol = symbol;
        node.methods = copy(methods);
        return node;
    }

    static SastNode enumDecl(SourceSpan span, EnumSymbol symbol) {
        SastNode node = node(SastKind.ENUM_DECL, span);
        node.enumSymbol = symbol;
        return node;
    }

    static SastNode block(SourceSpan span, List<SastNode> statements) {
        SastNode node = node(SastKind.BLOCK, span);
        node.statements = copy(statements);
        return node;
    }

    static SastNode varDecl(SourceSpan span, Symbol symbol) {
        return varDecl(span, symbol, null, null);
    }

    static SastNode varDecl(
            SourceSpan span,
            Symbol symbol,
            BigInteger foldedConstInitializer
    ) {
        return varDecl(span, symbol, foldedConstInitializer, null);
    }

    static SastNode varDecl(
            SourceSpan span,
            Symbol symbol,
            BigInteger foldedConstInitializer,
            SastNode staticInitializer
    ) {
        SastNode node = node(SastKind.VAR_DECL, span);
        node.symbol = symbol;
        node.foldedConstInitializer = foldedConstInitializer;
        node.staticInitializer = staticInitializer;
        return node;
    }

    static SastNode assign(SourceSpan span, SastNode target, SastNode value) {
        return assign(span, "=", target, value);
    }

    static SastNode assign(SourceSpan span, String op, SastNode target, SastNode value) {
        SastNode node = node(SastKind.ASSIGN, span);
        node.op = op;
        node.target = target;
        node.value = value;
        return node;
    }

    static SastNode ifStmt(
            SourceSpan span,
            SastNode condition,
            SastNode thenBranch,
            SastNode elseBranch
    ) {
        SastNode node = node(SastKind.IF, span);
        node.condition = condition;
        node.thenBranch = thenBranch;
        node.elseBranch = elseBranch;
        return node;
    }

    static SastNode whileStmt(
            SourceSpan span,
            SastNode condition,
            SastNode body,
            List<SastNode> update
    ) {
        SastNode node = node(SastKind.WHILE, span);
        node.condition = condition;
        node.body = body;
        node.update = copy(update);
        return node;
    }

    static SastNode breakStmt(SourceSpan span, int levels) {
        SastNode node = node(SastKind.BREAK, span);
        node.levels = levels;
        return node;
    }

    static SastNode continueStmt(SourceSpan span) {
        return node(SastKind.CONTINUE, span);
    }

    static SastNode returnStmt(SourceSpan span, Symbol function, SastNode value) {
        SastNode node = node(SastKind.RETURN, span);
        node.function = function;
        node.value = value;
        return node;
    }

    static SastNode exprStmt(SourceSpan span, SastNode expr) {
        SastNode node = node(SastKind.EXPR_STMT, span);
        node.expr = expr;
        return node;
    }

    static SastNode intLiteral(
            SourceSpan span,
            SastType type,
            BigInteger constantValue,
            String rawValue
    ) {
        SastNode node = node(SastKind.INT_LITERAL, span);
        node.type = type;
        node.constantValue = constantValue;
        node.rawValue = rawValue;
        return node;
    }

    static SastNode nullLiteral(SourceSpan span, SastType type) {
        SastNode node = node(SastKind.NULL_LITERAL, span);
        node.type = type;
        return node;
    }

    static SastNode varExpr(SourceSpan span, SastType type, Symbol symbol) {
        SastNode node = node(SastKind.VAR_EXPR, span);
        node.type = type;
        node.symbol = symbol;
        return node;
    }

    static SastNode unaryOp(
            SourceSpan span,
            SastType type,
            BigInteger constantValue,
            String op,
            SastNode expr
    ) {
        SastNode node = node(SastKind.UNARY_OP, span);
        node.type = type;
        node.constantValue = constantValue;
        node.op = op;
        node.expr = expr;
        return node;
    }

    static SastNode binOp(
            SourceSpan span,
            SastType type,
            BigInteger constantValue,
            String op,
            SastNode left,
            SastNode right
    ) {
        SastNode node = node(SastKind.BIN_OP, span);
        node.type = type;
        node.constantValue = constantValue;
        node.op = op;
        node.left = left;
        node.right = right;
        return node;
    }

    static SastNode arrayAccess(
            SourceSpan span,
            SastType type,
            SastNode target,
            SastNode index
    ) {
        SastNode node = node(SastKind.ARRAY_ACCESS, span);
        node.type = type;
        node.target = target;
        node.index = index;
        return node;
    }

    static SastNode memberAccess(
            SourceSpan span,
            SastType type,
            SastNode target,
            FieldSymbol field
    ) {
        SastNode node = node(SastKind.MEMBER_ACCESS, span);
        node.type = type;
        node.target = target;
        node.field = field;
        return node;
    }

    static SastNode functionCall(
            SourceSpan span,
            SastType type,
            Symbol function,
            List<SastNode> args
    ) {
        SastNode node = node(SastKind.FUNCTION_CALL, span);
        node.type = type;
        node.function = function;
        node.args = copy(args);
        return node;
    }

    static SastNode arrayIntrinsicCall(
            SourceSpan span,
            SemanticArrayIntrinsicKind intrinsicKind,
            SastNode receiver,
            List<SastNode> args
    ) {
        SastNode node = node(SastKind.ARRAY_INTRINSIC_CALL, span);
        node.intrinsicKind = intrinsicKind;
        node.receiver = receiver;
        node.args = copy(args);
        node.type = intrinsicKind.returnType();
        return node;
    }

    static SastNode getPtr(SourceSpan span, SastType type, SastNode target) {
        return getPtr(span, type, target, false);
    }

    static SastNode getPtr(
            SourceSpan span,
            SastType type,
            SastNode target,
            boolean temporary
    ) {
        SastNode node = node(SastKind.GET_PTR, span);
        node.type = type;
        node.target = target;
        node.temporary = temporary;
        return node;
    }

    static SastNode deref(SourceSpan span, SastType type, SastNode expr) {
        SastNode node = node(SastKind.DEREF, span);
        node.type = type;
        node.expr = expr;
        return node;
    }

    static SastNode cast(
            SourceSpan span,
            SastType type,
            BigInteger constantValue,
            SastNode expr,
            boolean checked
    ) {
        SastNode node = node(SastKind.CAST, span);
        node.type = type;
        node.constantValue = constantValue;
        node.expr = expr;
        node.checked = checked;
        return node;
    }

    static SastNode aggregateLiteral(
            SourceSpan span,
            SastType type,
            List<SastNode> fields
    ) {
        SastNode node = node(SastKind.AGGREGATE_LITERAL, span);
        node.type = type;
        node.fields = copy(fields);
        return node;
    }

    static SastNode enumVariant(
            SourceSpan span,
            EnumSymbol enumSymbol,
            EnumVariant variant
    ) {
        SastNode node = node(SastKind.ENUM_VARIANT, span);
        node.enumSymbol = enumSymbol;
        node.variant = variant;
        node.type = SastType.enumType(enumSymbol);
        return node;
    }

    static SastNode aggregateFieldInit(FieldSymbol field, SastNode value) {
        SastNode node = node(SastKind.AGGREGATE_FIELD_INIT, SourceSpan.NONE);
        node.field = field;
        node.value = value;
        return node;
    }

    boolean isConstant() {
        return constantValue != null;
    }

    boolean isLValue() {
        return switch (kind) {
            case VAR_EXPR, ARRAY_ACCESS, DEREF -> true;
            case MEMBER_ACCESS -> target != null && target.isLValue();
            default -> false;
        };
    }

    boolean isStmt() {
        return switch (kind) {
            case BLOCK, VAR_DECL, ASSIGN, IF, WHILE, BREAK, CONTINUE, RETURN,
                    EXPR_STMT -> true;
            default -> false;
        };
    }

    boolean isExpr() {
        return switch (kind) {
            case INT_LITERAL, NULL_LITERAL, VAR_EXPR, UNARY_OP, BIN_OP,
                    ARRAY_ACCESS, MEMBER_ACCESS, FUNCTION_CALL,
                    ARRAY_INTRINSIC_CALL, GET_PTR, DEREF, CAST,
                    AGGREGATE_LITERAL, ENUM_VARIANT -> true;
            default -> false;
        };
    }

    boolean isCompound() {
        return kind == SastKind.ASSIGN && !"=".equals(op);
    }

    String baseOp() {
        return isCompound() ? op.substring(0, op.length() - 1) : "=";
    }
}
