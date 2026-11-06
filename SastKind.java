enum SastKind {
    PROGRAM("program"),
    NAMESPACE("namespace"),
    FUNCTION_DECL("functionDecl"),
    STRUCT_DECL("structDecl"),
    ENUM_DECL("enumDecl"),
    BLOCK("block"),
    VAR_DECL("varDecl"),
    ASSIGN("assign"),
    IF("if"),
    WHILE("while"),
    BREAK("break"),
    CONTINUE("continue"),
    RETURN("return"),
    EXPR_STMT("exprStmt"),
    INT_LITERAL("intLiteral"),
    NULL_LITERAL("nullLiteral"),
    VAR_EXPR("varExpr"),
    UNARY_OP("unaryOp"),
    BIN_OP("binOp"),
    ARRAY_ACCESS("arrayAccess"),
    MEMBER_ACCESS("memberAccess"),
    FUNCTION_CALL("functionCall"),
    ARRAY_INTRINSIC_CALL("arrayIntrinsicCall"),
    GET_PTR("getPtr"),
    DEREF("deref"),
    CAST("cast"),
    AGGREGATE_LITERAL("aggregateLiteral"),
    ENUM_VARIANT("enumVariant"),
    AGGREGATE_FIELD_INIT("aggregateFieldInit");

    private final String jsonName;

    SastKind(String jsonName) {
        this.jsonName = jsonName;
    }

    String jsonName() {
        return jsonName;
    }
}
