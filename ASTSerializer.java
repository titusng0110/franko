import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public final class ASTSerializer {

    private static final String FORMAT = "franko-ast";
    private static final int VERSION = 1;

    private ASTSerializer() {
    }

    public static String toJson(AstDocument doc) {
        if (doc == null) {
            throw new IllegalArgumentException("Cannot serialize a null AST document");
        }

        JsonObject root = nodeToJson(doc.root, doc.sources);
        JsonObject document = new JsonObject();
        document.put("format", FORMAT);
        document.put("version", VERSION);
        document.put("phase", doc.phase.jsonName());
        document.put("sources", JsonAstSupport.sourceArray(doc.sources));
        document.put("root", root);

        return Json.write(document, true) + "\n";
    }

    public static AstDocument fromJson(String json) {
        JsonObject document = JsonAstSupport.requireDocument(json, "AST", FORMAT, VERSION);
        AstPhase phase = JsonAstSupport.requireEnumName(
                document.requireString("phase"),
                AstPhase.class
        );
        SourceTable sources = JsonAstSupport.sourceTable(document.requireArray("sources"), "AST");
        AstNode root = nodeFromJson(document.requireObject("root"), sources);

        if (root == null || root.kind != AstKind.PROGRAM) {
            throw new IllegalArgumentException("AST document root must be a program node");
        }

        return new AstDocument(phase, sources, root);
    }

    static JsonObject nodeToJson(AstNode node, SourceTable sources) {
        if (node == null) {
            return null;
        }

        if (node.isType()) {
            return typeToJson(node, sources);
        }

        JsonObject obj = new JsonObject();
        obj.put("span", spanToJson(node.span, sources));

        switch (node.kind) {
            case PROGRAM -> {
                obj.put("node", node.kind.jsonName());
                obj.put("items", nodeArray(node.items, sources));
            }
            case NAMESPACE -> {
                obj.put("node", node.kind.jsonName());
                obj.put("name", node.name);
                obj.put("items", nodeArray(node.items, sources));
            }
            case IMPORT -> {
                obj.put("node", node.kind.jsonName());
                obj.put("path", node.text);
            }
            case BLOCK -> {
                obj.put("node", node.kind.jsonName());
                obj.put("statements", nodeArray(node.items, sources));
            }
            case FUNCTION_DECL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("name", node.name);
                obj.put("returnType", typeToJson(node.type, sources));
                obj.put("parameters", nodeArray(node.parameters, sources));
                obj.put("body", nodeToJson(node.body, sources));
            }
            case PARAMETER -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("name", node.name);
            }
            case STRUCT_DECL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("name", node.name);
                obj.put("fields", nodeArray(node.fields, sources));
                obj.put("methods", nodeArray(node.methods, sources));
                obj.put("members", nodeArray(node.members, sources));
            }
            case STRUCT_FIELD_DECL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("name", node.name);
                obj.put("constInitializer", nodeToJson(node.value, sources));
            }
            case ENUM_DECL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("name", node.name);
                obj.put("backingType", typeToJson(node.type, sources));
                obj.put("variants", nodeArray(node.variants, sources));
            }
            case ENUM_VARIANT -> {
                obj.put("node", node.kind.jsonName());
                obj.put("name", node.name);
                obj.put("constInitializer", nodeToJson(node.value, sources));
            }
            case GLOBAL_VAR_DECL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("name", node.name);
                obj.put("constInitializer", nodeToJson(node.value, sources));
                obj.put("literalBytes", JsonAstSupport.bigIntegerArray(node.literalBytes));
                obj.put("compileTimeConstant", node.compileTimeConstant);
            }
            case VAR_DECL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("name", node.name);
            }
            case VAR_DECL_INIT -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("name", node.name);
                obj.put("init", nodeToJson(node.init, sources));
            }
            case VAR_DECL_FIELDS_INIT -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("name", node.name);
                obj.put("fields", nodeArray(node.fields, sources));
            }
            case FIELD_INIT -> {
                obj.put("node", node.kind.jsonName());
                obj.put("name", node.name);
                obj.put("positional", node.positional);
                obj.put("value", nodeToJson(node.value, sources));
            }
            case AGGREGATE_LITERAL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("fields", nodeArray(node.fields, sources));
            }
            case EXPR_STMT -> {
                obj.put("node", node.kind.jsonName());
                obj.put("expr", nodeToJson(node.expr, sources));
            }
            case ASSIGN -> {
                obj.put("node", node.kind.jsonName());
                obj.put("op", node.text);
                obj.put("target", nodeToJson(node.target, sources));
                obj.put("value", nodeToJson(node.value, sources));
            }
            case INC_DEC -> {
                obj.put("node", node.kind.jsonName());
                obj.put("op", node.text);
                obj.put("target", nodeToJson(node.target, sources));
            }
            case IF -> {
                obj.put("node", node.kind.jsonName());
                obj.put("condition", nodeToJson(node.condition, sources));
                obj.put("thenBranch", nodeToJson(node.thenBranch, sources));
                obj.put("elseBranch", nodeToJson(node.elseBranch, sources));
            }
            case COMPILE_TIME_IF -> {
                obj.put("node", node.kind.jsonName());
                obj.put("condition", nodeToJson(node.condition, sources));
                obj.put("thenBranch", nodeToJson(node.thenBranch, sources));
                obj.put("elseBranch", nodeToJson(node.elseBranch, sources));
            }
            case COMPILE_TIME_CALL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("name", node.name);
                obj.put("args", nodeArray(node.args, sources));
            }
            case COMPILE_TIME_NAME -> {
                obj.put("node", node.kind.jsonName());
                obj.put("name", node.name);
            }
            case DECL_IF -> {
                obj.put("node", node.kind.jsonName());
                obj.put("condition", nodeToJson(node.condition, sources));
                obj.put("thenItems", nodeArray(node.thenItems, sources));
                obj.put("elseItems", nodeArray(node.elseItems, sources));
            }
            case WHILE -> {
                obj.put("node", node.kind.jsonName());
                obj.put("condition", nodeToJson(node.condition, sources));
                obj.put("body", nodeToJson(node.body, sources));
                obj.put("update", nodeArray(node.updateItems, sources));
            }
            case BREAK -> {
                obj.put("node", node.kind.jsonName());
                obj.put("level", nodeToJson(node.level, sources));
            }
            case CONTINUE -> {
                obj.put("node", node.kind.jsonName());
                obj.put("level", nodeToJson(node.level, sources));
            }
            case FOR -> {
                obj.put("node", node.kind.jsonName());
                obj.put("init", nodeToJson(node.init, sources));
                obj.put("condition", nodeToJson(node.condition, sources));
                obj.put("update", nodeToJson(node.update, sources));
                obj.put("body", nodeToJson(node.body, sources));
            }
            case RETURN -> {
                obj.put("node", node.kind.jsonName());
                obj.put("value", nodeToJson(node.value, sources));
            }
            case VAR -> {
                obj.put("node", node.kind.jsonName());
                obj.put("name", node.name);
            }
            case INT -> {
                obj.put("node", node.kind.jsonName());
                obj.put("value", node.text);
            }
            case INFERRED_SIZE -> obj.put("node", node.kind.jsonName());
            case ARRAY_LITERAL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("elements", nodeArray(node.elements, sources));
            }
            case STRING_LITERAL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("rawText", node.text);
            }
            case UNARY -> {
                obj.put("node", node.kind.jsonName());
                obj.put("op", node.text);
                obj.put("expr", nodeToJson(node.expr, sources));
            }
            case BINARY -> {
                obj.put("node", node.kind.jsonName());
                obj.put("op", node.text);
                obj.put("left", nodeToJson(node.left, sources));
                obj.put("right", nodeToJson(node.right, sources));
            }
            case ARRAY_ACCESS -> {
                obj.put("node", node.kind.jsonName());
                obj.put("target", nodeToJson(node.target, sources));
                obj.put("index", nodeToJson(node.index, sources));
            }
            case MEMBER_ACCESS -> {
                obj.put("node", node.kind.jsonName());
                obj.put("target", nodeToJson(node.target, sources));
                obj.put("member", node.name);
            }
            case CALL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("callee", nodeToJson(node.callee, sources));
                obj.put("args", nodeArray(node.args, sources));
            }
            case GET_PTR -> {
                obj.put("node", node.kind.jsonName());
                obj.put("permission", node.permission.name());
                obj.put("target", nodeToJson(node.target, sources));
            }
            case DEREF -> {
                obj.put("node", node.kind.jsonName());
                obj.put("expr", nodeToJson(node.expr, sources));
            }
            case CAST -> {
                obj.put("node", node.kind.jsonName());
                obj.put("unsafe", node.unsafe);
                obj.put("target", typeToJson(node.type, sources));
                obj.put("expr", nodeToJson(node.expr, sources));
            }
            case NULL -> obj.put("node", node.kind.jsonName());
            case QUALIFIED_NAME -> {
                obj.put("node", node.kind.jsonName());
                obj.put("segments", stringArray(node.names));
            }
            default -> throw new IllegalArgumentException(
                    "Unknown AST node kind: " + node.kind
            );
        }

        return obj;
    }

    static AstNode nodeFromJson(JsonObject obj, SourceTable sources) {
        if (obj == null) {
            return null;
        }

        if (isTypeObject(obj)) {
            return typeFromJson(obj, sources);
        }

        String kindName = obj.requireString("node");
        AstKind kind = AstKind.fromJsonName(kindName);

        if (kind == null) {
            throw new IllegalArgumentException("Unknown AST node kind '" + kindName + "'");
        }

        if (!obj.has("span")) {
            throw new IllegalArgumentException("Missing required JSON field 'span'");
        }

        SourceSpan span = spanFromJson(obj, sources);

        return switch (kind) {
            case PROGRAM -> AstNode.program(
                    span,
                    nodesFromArray(obj.requireArray("items"), sources)
            );
            case NAMESPACE -> AstNode.namespace(
                    span,
                    obj.requireString("name"),
                    nodesFromArray(obj.requireArray("items"), sources)
            );
            case IMPORT -> AstNode.importPath(span, obj.requireString("path"));
            case BLOCK -> AstNode.block(
                    span,
                    nodesFromArray(obj.requireArray("statements"), sources)
            );
            case FUNCTION_DECL -> AstNode.functionDecl(
                    span,
                    obj.requireString("name"),
                    nodesFromArray(obj.requireArray("parameters"), sources),
                    typeFromJson(obj.requireObject("returnType"), sources),
                    nodeFromJson(obj.optObject("body"), sources)
            );
            case PARAMETER -> AstNode.parameter(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name")
            );
            case STRUCT_DECL -> AstNode.structDecl(
                    span,
                    obj.requireString("name"),
                    nodesFromArray(obj.requireArray("fields"), sources),
                    nodesFromArray(obj.requireArray("methods"), sources),
                    nodesFromArray(obj.requireArray("members"), sources)
            );
            case STRUCT_FIELD_DECL -> AstNode.structFieldDecl(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name"),
                    nodeFromJson(obj.optObject("constInitializer"), sources)
            );
            case ENUM_DECL -> AstNode.enumDecl(
                    span,
                    obj.requireString("name"),
                    typeFromJson(obj.requireObject("backingType"), sources),
                    nodesFromArray(obj.requireArray("variants"), sources)
            );
            case ENUM_VARIANT -> AstNode.enumVariant(
                    span,
                    obj.requireString("name"),
                    nodeFromJson(obj.optObject("constInitializer"), sources)
            );
            case GLOBAL_VAR_DECL -> {
                AstNode node = AstNode.globalVarDecl(
                        span,
                        typeFromJson(obj.requireObject("type"), sources),
                        obj.requireString("name"),
                        nodeFromJson(obj.optObject("constInitializer"), sources)
                );
                node.literalBytes = bigIntegersFromArray(obj.optArray("literalBytes"));
                node.compileTimeConstant = obj.has("compileTimeConstant")
                        && obj.requireBool("compileTimeConstant");
                yield node;
            }
            case VAR_DECL -> AstNode.varDecl(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name")
            );
            case VAR_DECL_INIT -> AstNode.varDeclInit(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name"),
                    nodeFromJson(obj.optObject("init"), sources)
            );
            case VAR_DECL_FIELDS_INIT -> AstNode.varDeclFieldsInit(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name"),
                    nodesFromArray(obj.requireArray("fields"), sources)
            );
            case FIELD_INIT -> AstNode.fieldInit(
                    span,
                    obj.optString("name"),
                    nodeFromJson(obj.requireObject("value"), sources),
                    obj.has("positional") && obj.requireBool("positional")
            );
            case AGGREGATE_LITERAL -> AstNode.aggregateLiteral(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    nodesFromArray(obj.requireArray("fields"), sources)
            );
            case EXPR_STMT -> AstNode.exprStmt(
                    span,
                    nodeFromJson(obj.requireObject("expr"), sources)
            );
            case ASSIGN -> AstNode.assign(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("target"), sources),
                    nodeFromJson(obj.requireObject("value"), sources)
            );
            case INC_DEC -> AstNode.incDec(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("target"), sources)
            );
            case IF -> AstNode.ifStmt(
                    span,
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodeFromJson(obj.requireObject("thenBranch"), sources),
                    nodeFromJson(obj.optObject("elseBranch"), sources)
            );
            case COMPILE_TIME_IF -> AstNode.compileTimeIf(
                    span,
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodeFromJson(obj.requireObject("thenBranch"), sources),
                    nodeFromJson(obj.optObject("elseBranch"), sources)
            );
            case COMPILE_TIME_CALL -> AstNode.compileTimeCall(
                    span,
                    obj.requireString("name"),
                    nodesFromArray(obj.requireArray("args"), sources)
            );
            case COMPILE_TIME_NAME -> AstNode.compileTimeName(
                    span,
                    obj.requireString("name")
            );
            case DECL_IF -> AstNode.declIf(
                    span,
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodesFromArray(obj.requireArray("thenItems"), sources),
                    nodesFromArray(obj.requireArray("elseItems"), sources)
            );
            case WHILE -> AstNode.whileStmt(
                    span,
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodeFromJson(obj.requireObject("body"), sources),
                    nodesFromArray(obj.requireArray("update"), sources)
            );
            case BREAK -> AstNode.breakStmt(
                    span,
                    nodeFromJson(obj.optObject("level"), sources)
            );
            case CONTINUE -> AstNode.continueStmt(
                    span,
                    nodeFromJson(obj.optObject("level"), sources)
            );
            case FOR -> AstNode.forStmt(
                    span,
                    nodeFromJson(obj.optObject("init"), sources),
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodeFromJson(obj.optObject("update"), sources),
                    nodeFromJson(obj.requireObject("body"), sources)
            );
            case RETURN -> AstNode.returnStmt(
                    span,
                    nodeFromJson(obj.optObject("value"), sources)
            );
            case VAR -> AstNode.var(span, obj.requireString("name"));
            case INT -> AstNode.intLiteral(span, obj.requireString("value"));
            case INFERRED_SIZE -> AstNode.inferredSize(span);
            case ARRAY_LITERAL -> AstNode.arrayLiteral(
                    span,
                    nodesFromArray(obj.requireArray("elements"), sources)
            );
            case STRING_LITERAL -> AstNode.stringLiteral(
                    span,
                    obj.requireString("rawText")
            );
            case UNARY -> AstNode.unary(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("expr"), sources)
            );
            case BINARY -> AstNode.binary(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("left"), sources),
                    nodeFromJson(obj.requireObject("right"), sources)
            );
            case ARRAY_ACCESS -> AstNode.arrayAccess(
                    span,
                    nodeFromJson(obj.requireObject("target"), sources),
                    nodeFromJson(obj.requireObject("index"), sources)
            );
            case MEMBER_ACCESS -> AstNode.memberAccess(
                    span,
                    nodeFromJson(obj.requireObject("target"), sources),
                    obj.requireString("member")
            );
            case CALL -> AstNode.call(
                    span,
                    nodeFromJson(obj.requireObject("callee"), sources),
                    nodesFromArray(obj.requireArray("args"), sources)
            );
            case GET_PTR -> AstNode.getPtr(
                    span,
                    PtrPermission.valueOf(obj.requireString("permission")),
                    nodeFromJson(obj.requireObject("target"), sources)
            );
            case DEREF -> AstNode.deref(
                    span,
                    nodeFromJson(obj.requireObject("expr"), sources)
            );
            case CAST -> AstNode.cast(
                    span,
                    typeFromJson(obj.requireObject("target"), sources),
                    nodeFromJson(obj.requireObject("expr"), sources),
                    obj.requireBool("unsafe")
            );
            case NULL -> AstNode.nullLiteral(span);
            case QUALIFIED_NAME -> AstNode.qualifiedName(
                    span,
                    stringsFromArray(obj.requireArray("segments"))
            );
            default -> throw new IllegalArgumentException(
                    "Unknown AST node kind '" + kindName + "'"
            );
        };
    }

    private static boolean isTypeObject(JsonObject obj) {
        return obj.has("type") && !obj.has("node");
    }

    static JsonObject typeToJson(AstNode type, SourceTable sources) {
        if (type == null) {
            return null;
        }

        JsonObject obj = new JsonObject();
        obj.put("span", spanToJson(type.span, sources));

        switch (type.kind) {
            case PRIMITIVE_TYPE -> {
                obj.put("type", type.kind.jsonName());
                obj.put("kind", type.primitive.name());
            }
            case VOID_TYPE -> obj.put("type", type.kind.jsonName());
            case DYNAMIC_ARRAY_TYPE -> {
                obj.put("type", type.kind.jsonName());
                obj.put("elementType", typeToJson(type.type, sources));
            }
            case STATIC_ARRAY_TYPE -> {
                obj.put("type", type.kind.jsonName());
                obj.put("elementType", typeToJson(type.type, sources));
                obj.put("sizeExpr", nodeToJson(type.sizeExpr, sources));
            }
            case PTR_TYPE -> {
                obj.put("type", type.kind.jsonName());
                obj.put("permission", type.permission.name());
                obj.put("referencedType", typeToJson(type.type, sources));
            }
            case STRUCT_TYPE -> {
                obj.put("type", type.kind.jsonName());
                obj.put(
                        "namespace",
                        type.names == null || type.names.isEmpty()
                                ? null
                                : String.join("::", type.names)
                );
                obj.put("name", type.name);
            }
            case TUPLE_TYPE -> {
                obj.put("type", type.kind.jsonName());
                JsonArray elements = new JsonArray();

                for (AstNode element : type.elements) {
                    JsonObject elementObj = new JsonObject();
                    elementObj.put("span", spanToJson(element.span, sources));
                    elementObj.put("name", element.name);
                    elementObj.put("type", typeToJson(element.type, sources));
                    elements.add(elementObj);
                }

                obj.put("elements", elements);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown AST type kind: " + type.kind
            );
        }

        return obj;
    }

    static AstNode typeFromJson(JsonObject obj, SourceTable sources) {
        String kindName = obj.requireString("type");
        AstKind kind = AstKind.fromJsonName(kindName);

        if (kind == null) {
            throw new IllegalArgumentException("Unknown AST type kind '" + kindName + "'");
        }

        if (!obj.has("span")) {
            throw new IllegalArgumentException("Missing required JSON field 'span'");
        }

        SourceSpan span = spanFromJson(obj, sources);

        return switch (kind) {
            case PRIMITIVE_TYPE -> AstNode.primitiveType(
                    span,
                    PrimitiveKind.valueOf(obj.requireString("kind"))
            );
            case VOID_TYPE -> AstNode.voidType(span);
            case DYNAMIC_ARRAY_TYPE -> AstNode.dynamicArrayType(
                    span,
                    typeFromJson(obj.requireObject("elementType"), sources)
            );
            case STATIC_ARRAY_TYPE -> AstNode.staticArrayType(
                    span,
                    typeFromJson(obj.requireObject("elementType"), sources),
                    nodeFromJson(obj.requireObject("sizeExpr"), sources)
            );
            case PTR_TYPE -> AstNode.ptrType(
                    span,
                    PtrPermission.valueOf(obj.requireString("permission")),
                    typeFromJson(obj.requireObject("referencedType"), sources)
            );
            case STRUCT_TYPE -> {
                String namespace = obj.optString("namespace");
                List<String> segments = namespace == null || namespace.isEmpty()
                        ? List.of()
                        : List.of(namespace.split("::"));
                yield AstNode.structType(span, segments, obj.requireString("name"));
            }
            case TUPLE_TYPE -> {
                List<AstNode> elements = new ArrayList<>();

                for (JsonValue elementValue : obj.requireArray("elements").values()) {
                    JsonObject element = JsonAstSupport.requireObject(
                            elementValue,
                            "tuple element"
                    );
                    elements.add(AstNode.tupleTypeElement(
                            spanFromJson(element, sources),
                            typeFromJson(element.requireObject("type"), sources),
                            element.optString("name")
                    ));
                }

                yield AstNode.tupleType(span, elements);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown AST type kind '" + kindName + "'"
            );
        };
    }

    static JsonValue spanToJson(SourceSpan span, SourceTable sources) {
        if (span == null || !span.isPresent()) {
            return JsonNull.INSTANCE;
        }

        JsonObject obj = new JsonObject();
        obj.put("src", sources.intern(span.source()));
        obj.put("line", span.startLine());
        obj.put("column", span.startColumn());
        obj.put("endLine", span.endLine());
        obj.put("endColumn", span.endColumn());
        return obj;
    }

    static SourceSpan spanFromJson(JsonObject obj, SourceTable sources) {
        JsonValue value = obj.get("span");

        if (value == null || value instanceof JsonNull) {
            return SourceSpan.NONE;
        }

        if (!(value instanceof JsonObject span)) {
            throw new IllegalArgumentException("JSON field 'span' must be an object or null");
        }

        int sourceIndex = span.requireInt("src");
        return new SourceSpan(
                sources.name(sourceIndex),
                span.requireInt("line"),
                span.requireInt("column"),
                span.requireInt("endLine"),
                span.requireInt("endColumn")
        );
    }

    private static JsonArray nodeArray(List<AstNode> nodes, SourceTable sources) {
        JsonArray array = new JsonArray();

        if (nodes != null) {
            for (AstNode node : nodes) {
                array.add(nodeToJson(node, sources));
            }
        }

        return array;
    }

    private static List<AstNode> nodesFromArray(JsonArray array, SourceTable sources) {
        List<AstNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            nodes.add(nodeFromJson(JsonAstSupport.requireObject(value, "AST node"), sources));
        }

        return nodes;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();

        if (values != null) {
            for (String value : values) {
                array.add(value);
            }
        }

        return array;
    }

    private static List<String> stringsFromArray(JsonArray array) {
        List<String> values = new ArrayList<>();

        for (JsonValue value : array.values()) {
            if (!(value instanceof JsonString string)) {
                throw new IllegalArgumentException("Expected a JSON string");
            }

            values.add(string.value());
        }

        return values;
    }

    private static List<BigInteger> bigIntegersFromArray(JsonArray array) {
        if (array == null) {
            return null;
        }

        List<BigInteger> values = new ArrayList<>();

        for (JsonValue value : array.values()) {
            if (!(value instanceof JsonString string)) {
                throw new IllegalArgumentException("Expected a decimal string");
            }

            values.add(new BigInteger(string.value()));
        }

        return values;
    }
}
