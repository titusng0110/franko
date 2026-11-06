import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class AstNode {
    final AstKind kind;
    final SourceSpan span;

    String name;
    String text;
    String rawContent;
    PrimitiveKind primitive;
    AstNode type;
    PtrPermission permission;

    boolean positional;
    boolean unsafe;
    boolean compileTimeConstant;

    AstNode target;
    AstNode value;
    AstNode condition;
    AstNode thenBranch;
    AstNode elseBranch;
    AstNode body;
    AstNode expr;
    AstNode left;
    AstNode right;
    AstNode index;
    AstNode callee;
    AstNode init;
    AstNode update;
    AstNode level;
    AstNode sizeExpr;

    List<AstNode> items;
    List<AstNode> parameters;
    List<AstNode> fields;
    List<AstNode> methods;
    List<AstNode> members;
    List<AstNode> variants;
    List<AstNode> args;
    List<AstNode> elements;
    List<AstNode> thenItems;
    List<AstNode> elseItems;
    List<AstNode> updateItems;
    List<String> names;
    List<BigInteger> literalBytes;

    private AstNode(AstKind kind, SourceSpan span) {
        this.kind = kind;
        this.span = span == null ? SourceSpan.NONE : span;
    }

    private static AstNode node(AstKind kind, SourceSpan span) {
        return new AstNode(kind, span);
    }

    private static List<AstNode> copy(List<AstNode> nodes) {
        return nodes == null ? List.of() : List.copyOf(nodes);
    }

    static AstNode program(SourceSpan span, List<AstNode> items) {
        AstNode node = node(AstKind.PROGRAM, span);
        node.items = copy(items);
        return node;
    }

    static AstNode namespace(SourceSpan span, String name, List<AstNode> items) {
        AstNode node = node(AstKind.NAMESPACE, span);
        node.name = name;
        node.items = copy(items);
        return node;
    }

    static AstNode importPath(SourceSpan span, String path) {
        AstNode node = node(AstKind.IMPORT, span);
        node.text = path;
        return node;
    }

    static AstNode block(SourceSpan span, List<AstNode> statements) {
        AstNode node = node(AstKind.BLOCK, span);
        node.items = copy(statements);
        return node;
    }

    static AstNode functionDecl(
            SourceSpan span,
            String name,
            List<AstNode> parameters,
            AstNode returnType,
            AstNode body
    ) {
        AstNode node = node(AstKind.FUNCTION_DECL, span);
        node.name = name;
        node.parameters = copy(parameters);
        node.type = returnType;
        node.body = body;
        return node;
    }

    static AstNode parameter(SourceSpan span, AstNode type, String name) {
        AstNode node = node(AstKind.PARAMETER, span);
        node.type = type;
        node.name = name;
        return node;
    }

    static AstNode structDecl(
            SourceSpan span,
            String name,
            List<AstNode> fields,
            List<AstNode> methods,
            List<AstNode> members
    ) {
        AstNode node = node(AstKind.STRUCT_DECL, span);
        node.name = name;
        node.fields = copy(fields);
        node.methods = copy(methods);
        node.members = copy(members);
        return node;
    }

    static AstNode structDecl(
            SourceSpan span,
            String name,
            List<AstNode> fields,
            List<AstNode> methods
    ) {
        List<AstNode> members = new ArrayList<>();
        members.addAll(fields);
        members.addAll(methods);
        return structDecl(span, name, fields, methods, members);
    }

    static AstNode structFieldDecl(
            SourceSpan span,
            AstNode type,
            String name,
            AstNode constInitializer
    ) {
        AstNode node = node(AstKind.STRUCT_FIELD_DECL, span);
        node.type = type;
        node.name = name;
        node.value = constInitializer;
        return node;
    }

    static AstNode enumDecl(
            SourceSpan span,
            String name,
            AstNode backingType,
            List<AstNode> variants
    ) {
        AstNode node = node(AstKind.ENUM_DECL, span);
        node.name = name;
        node.type = backingType;
        node.variants = copy(variants);
        return node;
    }

    static AstNode enumVariant(SourceSpan span, String name, AstNode constInitializer) {
        AstNode node = node(AstKind.ENUM_VARIANT, span);
        node.name = name;
        node.value = constInitializer;
        return node;
    }

    static AstNode globalVarDecl(
            SourceSpan span,
            AstNode type,
            String name,
            AstNode constInitializer
    ) {
        AstNode node = node(AstKind.GLOBAL_VAR_DECL, span);
        node.type = type;
        node.name = name;
        node.value = constInitializer;
        return node;
    }

    static AstNode varDecl(SourceSpan span, AstNode type, String name) {
        AstNode node = node(AstKind.VAR_DECL, span);
        node.type = type;
        node.name = name;
        return node;
    }

    static AstNode varDeclInit(SourceSpan span, AstNode type, String name, AstNode init) {
        AstNode node = node(AstKind.VAR_DECL_INIT, span);
        node.type = type;
        node.name = name;
        node.init = init;
        return node;
    }

    static AstNode varDeclFieldsInit(
            SourceSpan span,
            AstNode type,
            String name,
            List<AstNode> fields
    ) {
        AstNode node = node(AstKind.VAR_DECL_FIELDS_INIT, span);
        node.type = type;
        node.name = name;
        node.fields = copy(fields);
        return node;
    }

    static AstNode fieldInit(SourceSpan span, String name, AstNode value, boolean positional) {
        AstNode node = node(AstKind.FIELD_INIT, span);
        node.name = name;
        node.value = value;
        node.positional = positional;
        return node;
    }

    static AstNode aggregateLiteral(SourceSpan span, AstNode type, List<AstNode> fields) {
        AstNode node = node(AstKind.AGGREGATE_LITERAL, span);
        node.type = type;
        node.fields = copy(fields);
        return node;
    }

    static AstNode exprStmt(SourceSpan span, AstNode expr) {
        AstNode node = node(AstKind.EXPR_STMT, span);
        node.expr = expr;
        return node;
    }

    static AstNode assign(SourceSpan span, String op, AstNode target, AstNode value) {
        AstNode node = node(AstKind.ASSIGN, span);
        node.text = op;
        node.target = target;
        node.value = value;
        return node;
    }

    static AstNode incDec(SourceSpan span, String op, AstNode target) {
        AstNode node = node(AstKind.INC_DEC, span);
        node.text = op;
        node.target = target;
        return node;
    }

    static AstNode ifStmt(
            SourceSpan span,
            AstNode condition,
            AstNode thenBranch,
            AstNode elseBranch
    ) {
        AstNode node = node(AstKind.IF, span);
        node.condition = condition;
        node.thenBranch = thenBranch;
        node.elseBranch = elseBranch;
        return node;
    }

    static AstNode compileTimeIf(
            SourceSpan span,
            AstNode condition,
            AstNode thenBranch,
            AstNode elseBranch
    ) {
        AstNode node = node(AstKind.COMPILE_TIME_IF, span);
        node.condition = condition;
        node.thenBranch = thenBranch;
        node.elseBranch = elseBranch;
        return node;
    }

    static AstNode compileTimeCall(SourceSpan span, String name, List<AstNode> args) {
        AstNode node = node(AstKind.COMPILE_TIME_CALL, span);
        node.name = name;
        node.args = copy(args);
        return node;
    }

    static AstNode compileTimeName(SourceSpan span, String name) {
        AstNode node = node(AstKind.COMPILE_TIME_NAME, span);
        node.name = name;
        return node;
    }

    static AstNode declIf(
            SourceSpan span,
            AstNode condition,
            List<AstNode> thenItems,
            List<AstNode> elseItems
    ) {
        AstNode node = node(AstKind.DECL_IF, span);
        node.condition = condition;
        node.thenItems = copy(thenItems);
        node.elseItems = copy(elseItems);
        return node;
    }

    static AstNode whileStmt(
            SourceSpan span,
            AstNode condition,
            AstNode body,
            List<AstNode> updateItems
    ) {
        AstNode node = node(AstKind.WHILE, span);
        node.condition = condition;
        node.body = body;
        node.updateItems = copy(updateItems);
        return node;
    }

    static AstNode breakStmt(SourceSpan span, AstNode level) {
        AstNode node = node(AstKind.BREAK, span);
        node.level = level;
        return node;
    }

    static AstNode continueStmt(SourceSpan span, AstNode level) {
        AstNode node = node(AstKind.CONTINUE, span);
        node.level = level;
        return node;
    }

    static AstNode forStmt(
            SourceSpan span,
            AstNode init,
            AstNode condition,
            AstNode update,
            AstNode body
    ) {
        AstNode node = node(AstKind.FOR, span);
        node.init = init;
        node.condition = condition;
        node.update = update;
        node.body = body;
        return node;
    }

    static AstNode returnStmt(SourceSpan span, AstNode value) {
        AstNode node = node(AstKind.RETURN, span);
        node.value = value;
        return node;
    }

    static AstNode var(SourceSpan span, String name) {
        AstNode node = node(AstKind.VAR, span);
        node.name = name;
        return node;
    }

    static AstNode intLiteral(SourceSpan span, String value) {
        AstNode node = node(AstKind.INT, span);
        node.text = value;
        return node;
    }

    static AstNode inferredSize(SourceSpan span) {
        return node(AstKind.INFERRED_SIZE, span);
    }

    static AstNode arrayLiteral(SourceSpan span, List<AstNode> elements) {
        AstNode node = node(AstKind.ARRAY_LITERAL, span);
        node.elements = copy(elements);
        return node;
    }

    static AstNode stringLiteral(SourceSpan span, String rawText) {
        AstNode node = node(AstKind.STRING_LITERAL, span);
        node.text = rawText;

        if (rawText == null || rawText.length() < 2) {
            throw new RuntimeException("Invalid string literal token: " + rawText);
        }

        node.rawContent = rawText.substring(1, rawText.length() - 1);
        return node;
    }

    static AstNode unary(SourceSpan span, String op, AstNode expr) {
        AstNode node = node(AstKind.UNARY, span);
        node.text = op;
        node.expr = expr;
        return node;
    }

    static AstNode binary(SourceSpan span, String op, AstNode left, AstNode right) {
        AstNode node = node(AstKind.BINARY, span);
        node.text = op;
        node.left = left;
        node.right = right;
        return node;
    }

    static AstNode arrayAccess(SourceSpan span, AstNode target, AstNode index) {
        AstNode node = node(AstKind.ARRAY_ACCESS, span);
        node.target = target;
        node.index = index;
        return node;
    }

    static AstNode memberAccess(SourceSpan span, AstNode target, String memberName) {
        AstNode node = node(AstKind.MEMBER_ACCESS, span);
        node.target = target;
        node.name = memberName;
        return node;
    }

    static AstNode call(SourceSpan span, AstNode callee, List<AstNode> args) {
        AstNode node = node(AstKind.CALL, span);
        node.callee = callee;
        node.args = copy(args);
        return node;
    }

    static AstNode getPtr(SourceSpan span, PtrPermission permission, AstNode target) {
        AstNode node = node(AstKind.GET_PTR, span);
        node.permission = permission;
        node.target = target;
        return node;
    }

    static AstNode deref(SourceSpan span, AstNode expr) {
        AstNode node = node(AstKind.DEREF, span);
        node.expr = expr;
        return node;
    }

    static AstNode cast(SourceSpan span, AstNode target, AstNode expr, boolean unsafe) {
        AstNode node = node(AstKind.CAST, span);
        node.type = target;
        node.expr = expr;
        node.unsafe = unsafe;
        return node;
    }

    static AstNode nullLiteral(SourceSpan span) {
        return node(AstKind.NULL, span);
    }

    static AstNode qualifiedName(SourceSpan span, List<String> segments) {
        AstNode node = node(AstKind.QUALIFIED_NAME, span);
        node.names = segments == null ? List.of() : List.copyOf(segments);
        return node;
    }

    static AstNode primitiveType(SourceSpan span, PrimitiveKind kind) {
        AstNode node = node(AstKind.PRIMITIVE_TYPE, span);
        node.primitive = kind;
        return node;
    }

    static AstNode voidType(SourceSpan span) {
        return node(AstKind.VOID_TYPE, span);
    }

    static AstNode dynamicArrayType(SourceSpan span, AstNode elementType) {
        AstNode node = node(AstKind.DYNAMIC_ARRAY_TYPE, span);
        node.type = elementType;
        return node;
    }

    static AstNode staticArrayType(SourceSpan span, AstNode elementType, AstNode sizeExpr) {
        AstNode node = node(AstKind.STATIC_ARRAY_TYPE, span);
        node.type = elementType;
        node.sizeExpr = sizeExpr;
        return node;
    }

    static AstNode ptrType(SourceSpan span, PtrPermission permission, AstNode referencedType) {
        AstNode node = node(AstKind.PTR_TYPE, span);
        node.permission = permission;
        node.type = referencedType;
        return node;
    }

    static AstNode structType(SourceSpan span, List<String> namespaceSegments, String name) {
        AstNode node = node(AstKind.STRUCT_TYPE, span);
        node.names = namespaceSegments == null ? List.of() : List.copyOf(namespaceSegments);
        node.name = name;
        return node;
    }

    static AstNode tupleType(SourceSpan span, List<AstNode> elements) {
        AstNode node = node(AstKind.TUPLE_TYPE, span);
        node.elements = copy(elements);
        return node;
    }

    static AstNode tupleTypeElement(SourceSpan span, AstNode type, String name) {
        AstNode node = node(AstKind.TUPLE_TYPE_ELEMENT, span);
        node.type = type;
        node.name = name;
        return node;
    }

    boolean isType() {
        return switch (kind) {
            case PRIMITIVE_TYPE, VOID_TYPE, DYNAMIC_ARRAY_TYPE, STATIC_ARRAY_TYPE,
                    PTR_TYPE, STRUCT_TYPE, TUPLE_TYPE -> true;
            default -> false;
        };
    }

    static String kindName(AstNode node) {
        return node == null ? "null" : node.kind.jsonName();
    }
}
