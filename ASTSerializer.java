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

        JsonValue root = nodeToJson(doc.root, doc.sources);
        JsonValue document = JsonValue.object();
        document.put("format", FORMAT);
        document.put("version", VERSION);
        document.put("phase", doc.phase.jsonName());
        document.put("sources", JsonAstSupport.sourceArray(doc.sources));
        document.put("root", root);

        return Json.write(document, true) + "\n";
    }

    public static AstDocument fromJson(String json) {
        JsonValue document = JsonAstSupport.requireDocument(json, "AST", FORMAT, VERSION);
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

    static JsonValue nodeToJson(AstNode node, SourceTable sources) {
        if (node == null) {
            return null;
        }

        if (node.isType()) {
            return typeToJson(node, sources);
        }

        JsonValue obj = JsonValue.object();
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

                if (node.inlineMode != InlineMode.DEFAULT) {
                    obj.put("inlineMode", node.inlineMode.name());
                }

                if (node.isGeneric) {
                    obj.put("generic", true);
                    obj.put("genericParams", nodeArray(node.genericParams, sources));
                }

                if (node.genericGroup != null) {
                    obj.put("genericGroup", node.genericGroup);
                }

                if (node.genericHash != null) {
                    obj.put("genericHash", node.genericHash);
                }
            }
            case PARAMETER -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("name", node.name);

                if (node.defaultValue != null) {
                    obj.put("default", nodeToJson(node.defaultValue, sources));
                }

                if (node.isConst) {
                    obj.put("const", true);
                }
            }
            case STRUCT_DECL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("name", node.name);
                obj.put("fields", nodeArray(node.fields, sources));
                obj.put("methods", nodeArray(node.methods, sources));
                obj.put("members", nodeArray(node.members, sources));

                if (node.isGeneric) {
                    obj.put("generic", true);
                    obj.put("genericParams", nodeArray(node.genericParams, sources));
                }

                if (node.genericHash != null) {
                    obj.put("genericHash", node.genericHash);
                }

                if (node.genericBaseName != null) {
                    obj.put("genericBaseName", node.genericBaseName);
                }
            }
            case GENERIC_PARAM -> {
                obj.put("node", node.kind.jsonName());
                obj.put("name", node.name);
                obj.put("typeParam", node.typeParameter);

                if (!node.typeParameter) {
                    obj.put("type", typeToJson(node.type, sources));
                }
            }
            case GENERIC_ARG -> {
                obj.put("node", node.kind.jsonName());

                if (node.typeParameter) {
                    obj.put("type", typeToJson(node.type, sources));
                } else {
                    obj.put("value", nodeToJson(node.value, sources));
                }
            }
            case STRUCT_FIELD_DECL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("name", node.name);
                obj.put("constInitializer", nodeToJson(node.value, sources));

                if (node.isConst) {
                    obj.put("const", true);
                }
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

                if (node.isConst) {
                    obj.put("const", true);
                }
            }
            case VAR_DECL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("name", node.name);

                if (node.isConst) {
                    obj.put("const", true);
                }
            }
            case VAR_DECL_INIT -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("name", node.name);
                obj.put("init", nodeToJson(node.init, sources));

                if (node.isConst) {
                    obj.put("const", true);
                }
            }
            case VAR_DECL_FIELDS_INIT -> {
                obj.put("node", node.kind.jsonName());
                obj.put("type", typeToJson(node.type, sources));
                obj.put("name", node.name);
                obj.put("fields", nodeArray(node.fields, sources));

                if (node.isConst) {
                    obj.put("const", true);
                }
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

                if (node.literalPrimitive != null) {
                    obj.put("literalType", node.literalPrimitive.name());
                }
            }
            case INFERRED_SIZE -> obj.put("node", node.kind.jsonName());
            case ARRAY_LITERAL -> {
                obj.put("node", node.kind.jsonName());
                obj.put("elements", nodeArray(node.elements, sources));
            }
            case STRING_LITERAL -> {
                obj.put("node", node.kind.jsonName());

                if (node.literalBytes != null) {
                    obj.put("literalBytes", JsonAstSupport.bigIntegerArray(node.literalBytes));
                } else {
                    obj.put("rawText", node.text);
                }
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

                if (node.genericArgs != null && !node.genericArgs.isEmpty()) {
                    obj.put("typeArgs", nodeArray(node.genericArgs, sources));
                }

                if (node.hasNamedArguments()) {
                    obj.put("argumentNames", stringArray(node.argumentNames));
                }

                if (node.genericGroup != null) {
                    obj.put("genericGroup", node.genericGroup);
                }
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

    static AstNode nodeFromJson(JsonValue obj, SourceTable sources) {
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

        switch (kind) {
            case PROGRAM: return AstNode.program(
                    span,
                    nodesFromArray(obj.requireArray("items"), sources)
            );
            case NAMESPACE: return AstNode.namespace(
                    span,
                    obj.requireString("name"),
                    nodesFromArray(obj.requireArray("items"), sources)
            );
            case IMPORT: return AstNode.importPath(span, obj.requireString("path"));
            case BLOCK: return AstNode.block(
                    span,
                    nodesFromArray(obj.requireArray("statements"), sources)
            );
            case FUNCTION_DECL: {
                InlineMode inlineMode = obj.has("inlineMode")
                        ? JsonAstSupport.requireEnumName(
                                obj.requireString("inlineMode"),
                                InlineMode.class
                        )
                        : InlineMode.DEFAULT;

                AstNode node = AstNode.functionDecl(
                        span,
                        obj.requireString("name"),
                        nodesFromArray(obj.requireArray("parameters"), sources),
                        typeFromJson(obj.requireObject("returnType"), sources),
                        nodeFromJson(obj.optObject("body"), sources),
                        inlineMode,
                        genericParamsFromJson(obj, sources)
                );

                if (obj.has("generic") && obj.requireBool("generic")) {
                    node.genericModifier = true;
                    node.isGeneric = true;
                }

                node.genericGroup = obj.optString("genericGroup");
                node.genericHash = obj.optString("genericHash");
                return node;
            }
            case PARAMETER: return AstNode.parameter(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name"),
                    obj.has("const") && obj.requireBool("const"),
                    nodeFromJson(obj.optObject("default"), sources)
            );
            case STRUCT_DECL: {
                AstNode node = AstNode.structDecl(
                        span,
                        obj.requireString("name"),
                        nodesFromArray(obj.requireArray("fields"), sources),
                        nodesFromArray(obj.requireArray("methods"), sources),
                        nodesFromArray(obj.requireArray("members"), sources),
                        genericParamsFromJson(obj, sources)
                );

                if (obj.has("generic") && obj.requireBool("generic")) {
                    node.genericModifier = true;
                    node.isGeneric = true;
                }

                node.genericHash = obj.optString("genericHash");
                node.genericBaseName = obj.optString("genericBaseName");
                return node;
            }
            case GENERIC_PARAM: {
                boolean typeParam = obj.has("typeParam")
                        && obj.requireBool("typeParam");
                return AstNode.genericParam(
                        span,
                        obj.requireString("name"),
                        typeParam
                                ? null
                                : typeFromJson(obj.requireObject("type"), sources),
                        typeParam
                );
            }
            case GENERIC_ARG: {
                if (obj.has("type")) {
                    return AstNode.genericArgType(
                            span,
                            typeFromJson(obj.requireObject("type"), sources)
                    );
                }

                return AstNode.genericArgValue(
                        span,
                        nodeFromJson(obj.requireObject("value"), sources)
                );
            }
            case STRUCT_FIELD_DECL: return AstNode.structFieldDecl(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name"),
                    nodeFromJson(obj.optObject("constInitializer"), sources),
                    obj.has("const") && obj.requireBool("const")
            );
            case ENUM_DECL: return AstNode.enumDecl(
                    span,
                    obj.requireString("name"),
                    typeFromJson(obj.requireObject("backingType"), sources),
                    nodesFromArray(obj.requireArray("variants"), sources)
            );
            case ENUM_VARIANT: return AstNode.enumVariant(
                    span,
                    obj.requireString("name"),
                    nodeFromJson(obj.optObject("constInitializer"), sources)
            );
            case GLOBAL_VAR_DECL: {
                AstNode node = AstNode.globalVarDecl(
                        span,
                        typeFromJson(obj.requireObject("type"), sources),
                        obj.requireString("name"),
                        nodeFromJson(obj.optObject("constInitializer"), sources),
                        obj.has("const") && obj.requireBool("const")
                );
                node.literalBytes = bigIntegersFromArray(obj.optArray("literalBytes"));
                node.compileTimeConstant = obj.has("compileTimeConstant")
                        && obj.requireBool("compileTimeConstant");
                return node;
            }
            case VAR_DECL: return AstNode.varDecl(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name"),
                    obj.has("const") && obj.requireBool("const")
            );
            case VAR_DECL_INIT: return AstNode.varDeclInit(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name"),
                    nodeFromJson(obj.optObject("init"), sources),
                    obj.has("const") && obj.requireBool("const")
            );
            case VAR_DECL_FIELDS_INIT: return AstNode.varDeclFieldsInit(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name"),
                    nodesFromArray(obj.requireArray("fields"), sources),
                    obj.has("const") && obj.requireBool("const")
            );
            case FIELD_INIT: return AstNode.fieldInit(
                    span,
                    obj.optString("name"),
                    nodeFromJson(obj.requireObject("value"), sources),
                    obj.has("positional") && obj.requireBool("positional")
            );
            case AGGREGATE_LITERAL: return AstNode.aggregateLiteral(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    nodesFromArray(obj.requireArray("fields"), sources)
            );
            case EXPR_STMT: return AstNode.exprStmt(
                    span,
                    nodeFromJson(obj.requireObject("expr"), sources)
            );
            case ASSIGN: return AstNode.assign(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("target"), sources),
                    nodeFromJson(obj.requireObject("value"), sources)
            );
            case INC_DEC: return AstNode.incDec(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("target"), sources)
            );
            case IF: return AstNode.ifStmt(
                    span,
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodeFromJson(obj.requireObject("thenBranch"), sources),
                    nodeFromJson(obj.optObject("elseBranch"), sources)
            );
            case COMPILE_TIME_IF: return AstNode.compileTimeIf(
                    span,
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodeFromJson(obj.requireObject("thenBranch"), sources),
                    nodeFromJson(obj.optObject("elseBranch"), sources)
            );
            case COMPILE_TIME_CALL: return AstNode.compileTimeCall(
                    span,
                    obj.requireString("name"),
                    nodesFromArray(obj.requireArray("args"), sources)
            );
            case COMPILE_TIME_NAME: return AstNode.compileTimeName(
                    span,
                    obj.requireString("name")
            );
            case DECL_IF: return AstNode.declIf(
                    span,
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodesFromArray(obj.requireArray("thenItems"), sources),
                    nodesFromArray(obj.requireArray("elseItems"), sources)
            );
            case WHILE: return AstNode.whileStmt(
                    span,
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodeFromJson(obj.requireObject("body"), sources),
                    nodesFromArray(obj.requireArray("update"), sources)
            );
            case BREAK: return AstNode.breakStmt(
                    span,
                    nodeFromJson(obj.optObject("level"), sources)
            );
            case CONTINUE: return AstNode.continueStmt(
                    span,
                    nodeFromJson(obj.optObject("level"), sources)
            );
            case FOR: return AstNode.forStmt(
                    span,
                    nodeFromJson(obj.optObject("init"), sources),
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodeFromJson(obj.optObject("update"), sources),
                    nodeFromJson(obj.requireObject("body"), sources)
            );
            case RETURN: return AstNode.returnStmt(
                    span,
                    nodeFromJson(obj.optObject("value"), sources)
            );
            case VAR: return AstNode.var(span, obj.requireString("name"));
            case INT: {
                AstNode node = AstNode.intLiteral(
                        span,
                        obj.requireString("value")
                );

                if (obj.has("literalType")) {
                    node.literalPrimitive = PrimitiveKind.valueOf(
                            obj.requireString("literalType")
                    );
                }

                return node;
            }
            case INFERRED_SIZE: return AstNode.inferredSize(span);
            case ARRAY_LITERAL: return AstNode.arrayLiteral(
                    span,
                    nodesFromArray(obj.requireArray("elements"), sources)
            );
            case STRING_LITERAL: {
                if (obj.has("literalBytes")) {
                    return AstNode.stringLiteralBytes(
                            span,
                            bigIntegersFromArray(obj.requireArray("literalBytes"))
                    );
                }

                return AstNode.stringLiteral(
                        span,
                        obj.requireString("rawText")
                );
            }
            case UNARY: return AstNode.unary(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("expr"), sources)
            );
            case BINARY: return AstNode.binary(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("left"), sources),
                    nodeFromJson(obj.requireObject("right"), sources)
            );
            case ARRAY_ACCESS: return AstNode.arrayAccess(
                    span,
                    nodeFromJson(obj.requireObject("target"), sources),
                    nodeFromJson(obj.requireObject("index"), sources)
            );
            case MEMBER_ACCESS: return AstNode.memberAccess(
                    span,
                    nodeFromJson(obj.requireObject("target"), sources),
                    obj.requireString("member")
            );
            case CALL: {
                List<AstNode> args =
                        nodesFromArray(obj.requireArray("args"), sources);
                List<String> argumentNames = obj.has("argumentNames")
                        ? stringsFromArray(obj.requireArray("argumentNames"))
                        : List.of();

                if (!argumentNames.isEmpty()
                        && argumentNames.size() != args.size()) {
                    throw new IllegalArgumentException(
                            "CALL argumentNames length "
                                    + argumentNames.size()
                                    + " does not match args length "
                                    + args.size());
                }

                AstNode call = AstNode.call(
                        span,
                        nodeFromJson(obj.requireObject("callee"), sources),
                        args,
                        genericArgsFromJson(obj, sources),
                        argumentNames
                );
                call.genericGroup = obj.optString("genericGroup");
                return call;
            }
            case GET_PTR: return AstNode.getPtr(
                    span,
                    PtrPermission.valueOf(obj.requireString("permission")),
                    nodeFromJson(obj.requireObject("target"), sources)
            );
            case DEREF: return AstNode.deref(
                    span,
                    nodeFromJson(obj.requireObject("expr"), sources)
            );
            case CAST: return AstNode.cast(
                    span,
                    typeFromJson(obj.requireObject("target"), sources),
                    nodeFromJson(obj.requireObject("expr"), sources),
                    obj.requireBool("unsafe")
            );
            case NULL: return AstNode.nullLiteral(span);
            case QUALIFIED_NAME: return AstNode.qualifiedName(
                    span,
                    stringsFromArray(obj.requireArray("segments"))
            );
            default: throw new IllegalArgumentException(
                    "Unknown AST node kind '" + kindName + "'"
            );
        }
    }

    private static boolean isTypeObject(JsonValue obj) {
        return obj.has("type") && !obj.has("node");
    }

    static JsonValue typeToJson(AstNode type, SourceTable sources) {
        if (type == null) {
            return null;
        }

        JsonValue obj = JsonValue.object();
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

                if (type.genericArgs != null && !type.genericArgs.isEmpty()) {
                    obj.put("typeArgs", nodeArray(type.genericArgs, sources));
                }
            }
            case TUPLE_TYPE -> {
                obj.put("type", type.kind.jsonName());
                JsonValue elements = JsonValue.array();

                for (AstNode element : type.elements) {
                    JsonValue elementObj = JsonValue.object();
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

    static AstNode typeFromJson(JsonValue obj, SourceTable sources) {
        String kindName = obj.requireString("type");
        AstKind kind = AstKind.fromJsonName(kindName);

        if (kind == null) {
            throw new IllegalArgumentException("Unknown AST type kind '" + kindName + "'");
        }

        if (!obj.has("span")) {
            throw new IllegalArgumentException("Missing required JSON field 'span'");
        }

        SourceSpan span = spanFromJson(obj, sources);

        switch (kind) {
            case PRIMITIVE_TYPE: return AstNode.primitiveType(
                    span,
                    PrimitiveKind.valueOf(obj.requireString("kind"))
            );
            case VOID_TYPE: return AstNode.voidType(span);
            case DYNAMIC_ARRAY_TYPE: return AstNode.dynamicArrayType(
                    span,
                    typeFromJson(obj.requireObject("elementType"), sources)
            );
            case STATIC_ARRAY_TYPE: return AstNode.staticArrayType(
                    span,
                    typeFromJson(obj.requireObject("elementType"), sources),
                    nodeFromJson(obj.requireObject("sizeExpr"), sources)
            );
            case PTR_TYPE: return AstNode.ptrType(
                    span,
                    PtrPermission.valueOf(obj.requireString("permission")),
                    typeFromJson(obj.requireObject("referencedType"), sources)
            );
            case STRUCT_TYPE: {
                String namespace = obj.optString("namespace");
                List<String> segments = namespace == null || namespace.isEmpty()
                        ? List.of()
                        : List.of(namespace.split("::"));
                return AstNode.structType(
                        span,
                        segments,
                        obj.requireString("name"),
                        genericArgsFromJson(obj, sources)
                );
            }
            case TUPLE_TYPE: {
                List<AstNode> elements = new ArrayList<>();

                for (JsonValue elementValue : obj.requireArray("elements").values()) {
                    JsonValue element = JsonAstSupport.requireObject(
                            elementValue,
                            "tuple element"
                    );
                    elements.add(AstNode.tupleTypeElement(
                            spanFromJson(element, sources),
                            typeFromJson(element.requireObject("type"), sources),
                            element.optString("name")
                    ));
                }

                return AstNode.tupleType(span, elements);
            }
            default: throw new IllegalArgumentException(
                    "Unknown AST type kind '" + kindName + "'"
            );
        }
    }

    static JsonValue spanToJson(SourceSpan span, SourceTable sources) {
        if (span == null || !span.isPresent()) {
            return JsonValue.nullValue();
        }

        JsonValue obj = JsonValue.object();
        obj.put("src", sources.intern(span.source()));
        obj.put("line", span.startLine());
        obj.put("column", span.startColumn());
        obj.put("endLine", span.endLine());
        obj.put("endColumn", span.endColumn());
        return obj;
    }

    static SourceSpan spanFromJson(JsonValue obj, SourceTable sources) {
        JsonValue value = obj.get("span");

        if (value == null || value.isNull()) {
            return SourceSpan.NONE;
        }

        if (!value.isObject()) {
            throw new IllegalArgumentException("JSON field 'span' must be an object or null");
        }

        JsonValue span = value;
        int sourceIndex = span.requireInt("src");
        return new SourceSpan(
                sources.name(sourceIndex),
                span.requireInt("line"),
                span.requireInt("column"),
                span.requireInt("endLine"),
                span.requireInt("endColumn")
        );
    }

    private static JsonValue nodeArray(List<AstNode> nodes, SourceTable sources) {
        JsonValue array = JsonValue.array();

        if (nodes != null) {
            for (AstNode node : nodes) {
                array.add(nodeToJson(node, sources));
            }
        }

        return array;
    }

    private static List<AstNode> nodesFromArray(JsonValue array, SourceTable sources) {
        List<AstNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            nodes.add(nodeFromJson(JsonAstSupport.requireObject(value, "AST node"), sources));
        }

        return nodes;
    }

    private static List<AstNode> genericParamsFromJson(
            JsonValue obj,
            SourceTable sources
    ) {
        if (!obj.has("genericParams")) {
            return List.of();
        }

        return nodesFromArray(obj.requireArray("genericParams"), sources);
    }

    private static List<AstNode> genericArgsFromJson(
            JsonValue obj,
            SourceTable sources
    ) {
        if (!obj.has("typeArgs")) {
            return List.of();
        }

        return nodesFromArray(obj.requireArray("typeArgs"), sources);
    }

    private static JsonValue stringArray(List<String> values) {
        JsonValue array = JsonValue.array();

        if (values != null) {
            for (String value : values) {
                array.add(value);
            }
        }

        return array;
    }

    private static List<String> stringsFromArray(JsonValue array) {
        List<String> values = new ArrayList<>();

        for (JsonValue value : array.values()) {
            if (!value.isString()) {
                throw new IllegalArgumentException("Expected a JSON string");
            }

            values.add(value.value());
        }

        return values;
    }

    private static List<BigInteger> bigIntegersFromArray(JsonValue array) {
        if (array == null) {
            return null;
        }

        List<BigInteger> values = new ArrayList<>();

        for (JsonValue value : array.values()) {
            if (!value.isString()) {
                throw new IllegalArgumentException("Expected a decimal string");
            }

            values.add(new BigInteger(value.value()));
        }

        return values;
    }
}
