enum AstKind {
    PROGRAM("program"),
    NAMESPACE("namespace"),
    IMPORT("import"),
    BLOCK("block"),
    FUNCTION_DECL("functionDecl"),
    PARAMETER("parameter"),
    STRUCT_DECL("structDecl"),
    STRUCT_FIELD_DECL("structFieldDecl"),
    ENUM_DECL("enumDecl"),
    ENUM_VARIANT("enumVariant"),
    GLOBAL_VAR_DECL("globalVarDecl"),
    VAR_DECL("varDecl"),
    VAR_DECL_INIT("varDeclInit"),
    VAR_DECL_FIELDS_INIT("varDeclFieldsInit"),
    FIELD_INIT("fieldInit"),
    AGGREGATE_LITERAL("aggregateLiteral"),
    EXPR_STMT("exprStmt"),
    ASSIGN("assign"),
    INC_DEC("incDec"),
    IF("if"),
    COMPILE_TIME_IF("compileTimeIf"),
    COMPILE_TIME_CALL("compileTimeCall"),
    COMPILE_TIME_NAME("compileTimeName"),
    DECL_IF("declIf"),
    WHILE("while"),
    BREAK("break"),
    CONTINUE("continue"),
    FOR("for"),
    RETURN("return"),
    VAR("var"),
    INT("int"),
    INFERRED_SIZE("inferredSize"),
    ARRAY_LITERAL("arrayLiteral"),
    STRING_LITERAL("stringLiteral"),
    UNARY("unary"),
    BINARY("binary"),
    ARRAY_ACCESS("arrayAccess"),
    MEMBER_ACCESS("memberAccess"),
    CALL("call"),
    GET_PTR("getPtr"),
    DEREF("deref"),
    CAST("cast"),
    NULL("null"),
    QUALIFIED_NAME("qualifiedName"),
    GENERIC_PARAM("genericParam"),
    GENERIC_ARG("genericArg"),
    PRIMITIVE_TYPE("primitive"),
    VOID_TYPE("void"),
    DYNAMIC_ARRAY_TYPE("dynamicArray"),
    STATIC_ARRAY_TYPE("staticArray"),
    PTR_TYPE("ptr"),
    STRUCT_TYPE("struct"),
    TUPLE_TYPE("tuple"),
    TUPLE_TYPE_ELEMENT("tupleElement");

    private final String jsonName;

    AstKind(String jsonName) {
        this.jsonName = jsonName;
    }

    String jsonName() {
        return jsonName;
    }

    static AstKind fromJsonName(String name) {
        for (AstKind kind : values()) {
            if (kind.jsonName.equals(name)) {
                return kind;
            }
        }

        return null;
    }
}
