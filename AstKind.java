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

    String legacyClassName() {
        return switch (this) {
            case PROGRAM -> "ProgramNode";
            case NAMESPACE -> "NamespaceNode";
            case IMPORT -> "ImportNode";
            case BLOCK -> "BlockNode";
            case FUNCTION_DECL -> "FunctionDeclNode";
            case PARAMETER -> "ParameterNode";
            case STRUCT_DECL -> "StructDeclNode";
            case STRUCT_FIELD_DECL -> "StructFieldDeclNode";
            case ENUM_DECL -> "EnumDeclNode";
            case ENUM_VARIANT -> "EnumVariantNode";
            case GLOBAL_VAR_DECL -> "GlobalVarDeclNode";
            case VAR_DECL -> "VarDeclNode";
            case VAR_DECL_INIT -> "VarDeclInitNode";
            case VAR_DECL_FIELDS_INIT -> "VarDeclFieldsInitNode";
            case FIELD_INIT -> "FieldInitNode";
            case AGGREGATE_LITERAL -> "AggregateLiteralNode";
            case EXPR_STMT -> "ExprStmtNode";
            case ASSIGN -> "AssignNode";
            case INC_DEC -> "IncDecNode";
            case IF -> "IfNode";
            case COMPILE_TIME_IF -> "CompileTimeIfNode";
            case COMPILE_TIME_CALL -> "CompileTimeCallNode";
            case COMPILE_TIME_NAME -> "CompileTimeNameNode";
            case DECL_IF -> "DeclIfNode";
            case WHILE -> "WhileNode";
            case BREAK -> "BreakNode";
            case CONTINUE -> "ContinueNode";
            case FOR -> "ForNode";
            case RETURN -> "ReturnNode";
            case VAR -> "VarNode";
            case INT -> "IntNode";
            case INFERRED_SIZE -> "InferredSizeNode";
            case ARRAY_LITERAL -> "ArrayLiteralNode";
            case STRING_LITERAL -> "StringLiteralNode";
            case UNARY -> "UnaryOpNode";
            case BINARY -> "BinOpNode";
            case ARRAY_ACCESS -> "ArrayAccessNode";
            case MEMBER_ACCESS -> "MemberAccessNode";
            case CALL -> "CallNode";
            case GET_PTR -> "GetPtrNode";
            case DEREF -> "DerefNode";
            case CAST -> "CastNode";
            case NULL -> "NullNode";
            case QUALIFIED_NAME -> "QualifiedNameNode";
            case PRIMITIVE_TYPE -> "PrimitiveTypeNode";
            case VOID_TYPE -> "VoidTypeNode";
            case DYNAMIC_ARRAY_TYPE -> "DynamicArrayTypeNode";
            case STATIC_ARRAY_TYPE -> "StaticArrayTypeNode";
            case PTR_TYPE -> "PtrTypeNode";
            case STRUCT_TYPE -> "StructTypeNode";
            case TUPLE_TYPE -> "TupleTypeNode";
            case TUPLE_TYPE_ELEMENT -> "TupleTypeElementNode";
        };
    }
}
