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
        ProgramNode root = requireProgram(nodeFromJson(
                document.requireObject("root"),
                sources
        ));

        return new AstDocument(phase, sources, root);
    }

    static JsonObject nodeToJson(ASTNode node, SourceTable sources) {
        if (node == null) {
            return null;
        }

        JsonObject obj = new JsonObject();
        obj.put("span", spanToJson(node.span, sources));

        if (node instanceof ProgramNode n) {
            obj.put("node", "program");
            obj.put("items", nodeArray(n.topLevelItems, sources));
        } else if (node instanceof NamespaceNode n) {
            obj.put("node", "namespace");
            obj.put("name", n.name);
            obj.put("items", nodeArray(n.items, sources));
        } else if (node instanceof ImportNode n) {
            obj.put("node", "import");
            obj.put("path", n.rawPathText);
        } else if (node instanceof BlockNode n) {
            obj.put("node", "block");
            obj.put("statements", nodeArray(n.statements, sources));
        } else if (node instanceof FunctionDeclNode n) {
            obj.put("node", "functionDecl");
            obj.put("name", n.name);
            obj.put("returnType", typeToJson(n.returnType, sources));
            obj.put("parameters", parameterArray(n.parameters, sources));
            obj.put("body", nodeToJson(n.body, sources));
        } else if (node instanceof ParameterNode n) {
            obj.put("node", "parameter");
            obj.put("type", typeToJson(n.type, sources));
            obj.put("name", n.name);
        } else if (node instanceof StructDeclNode n) {
            obj.put("node", "structDecl");
            obj.put("name", n.name);
            obj.put("fields", fieldArray(n.fields, sources));
            obj.put("methods", methodArray(n.methods, sources));
        } else if (node instanceof StructFieldDeclNode n) {
            obj.put("node", "structFieldDecl");
            obj.put("type", typeToJson(n.type, sources));
            obj.put("name", n.name);
            obj.put("constInitializer", nodeToJson(n.constInitializer, sources));
        } else if (node instanceof GlobalVarDeclNode n) {
            obj.put("node", "globalVarDecl");
            obj.put("type", typeToJson(n.type, sources));
            obj.put("name", n.name);
            obj.put("constInitializer", nodeToJson(n.constInitializer, sources));
            obj.put("literalBytes", JsonAstSupport.bigIntegerArray(n.literalBytes));
            obj.put("compileTimeConstant", n.compileTimeConstant);
        } else if (node instanceof VarDeclNode n) {
            obj.put("node", "varDecl");
            obj.put("type", typeToJson(n.type, sources));
            obj.put("name", n.name);
        } else if (node instanceof VarDeclInitNode n) {
            obj.put("node", "varDeclInit");
            obj.put("type", typeToJson(n.type, sources));
            obj.put("name", n.name);
            obj.put("init", nodeToJson(n.init, sources));
        } else if (node instanceof ExprStmtNode n) {
            obj.put("node", "exprStmt");
            obj.put("expr", nodeToJson(n.expr, sources));
        } else if (node instanceof AssignNode n) {
            obj.put("node", "assign");
            obj.put("op", n.op);
            obj.put("target", nodeToJson(n.target, sources));
            obj.put("value", nodeToJson(n.value, sources));
        } else if (node instanceof IncDecNode n) {
            obj.put("node", "incDec");
            obj.put("op", n.op);
            obj.put("target", nodeToJson(n.target, sources));
        } else if (node instanceof IfNode n) {
            obj.put("node", "if");
            obj.put("condition", nodeToJson(n.condition, sources));
            obj.put("thenBranch", nodeToJson(n.thenBranch, sources));
            obj.put("elseBranch", nodeToJson(n.elseBranch, sources));
        } else if (node instanceof WhileNode n) {
            obj.put("node", "while");
            obj.put("condition", nodeToJson(n.condition, sources));
            obj.put("body", nodeToJson(n.body, sources));
            obj.put("update", nodeArray(n.update, sources));
        } else if (node instanceof BreakNode n) {
            obj.put("node", "break");
            obj.put("level", nodeToJson(n.level, sources));
        } else if (node instanceof ContinueNode n) {
            obj.put("node", "continue");
            obj.put("level", nodeToJson(n.level, sources));
        } else if (node instanceof ForNode n) {
            obj.put("node", "for");
            obj.put("init", nodeToJson(n.init, sources));
            obj.put("condition", nodeToJson(n.condition, sources));
            obj.put("update", nodeToJson(n.update, sources));
            obj.put("body", nodeToJson(n.body, sources));
        } else if (node instanceof ReturnNode n) {
            obj.put("node", "return");
            obj.put("value", nodeToJson(n.value, sources));
        } else if (node instanceof VarNode n) {
            obj.put("node", "var");
            obj.put("name", n.name);
        } else if (node instanceof IntNode n) {
            obj.put("node", "int");
            obj.put("value", n.value);
        } else if (node instanceof InferredSizeNode) {
            obj.put("node", "inferredSize");
        } else if (node instanceof ArrayLiteralNode n) {
            obj.put("node", "arrayLiteral");
            obj.put("elements", nodeArray(n.elements, sources));
        } else if (node instanceof StringLiteralNode n) {
            obj.put("node", "stringLiteral");
            obj.put("rawText", n.rawText);
        } else if (node instanceof UnaryOpNode n) {
            obj.put("node", "unary");
            obj.put("op", n.op);
            obj.put("expr", nodeToJson(n.expr, sources));
        } else if (node instanceof BinOpNode n) {
            obj.put("node", "binary");
            obj.put("op", n.op);
            obj.put("left", nodeToJson(n.left, sources));
            obj.put("right", nodeToJson(n.right, sources));
        } else if (node instanceof ArrayAccessNode n) {
            obj.put("node", "arrayAccess");
            obj.put("target", nodeToJson(n.target, sources));
            obj.put("index", nodeToJson(n.index, sources));
        } else if (node instanceof MemberAccessNode n) {
            obj.put("node", "memberAccess");
            obj.put("target", nodeToJson(n.target, sources));
            obj.put("member", n.memberName);
        } else if (node instanceof CallNode n) {
            obj.put("node", "call");
            obj.put("callee", nodeToJson(n.callee, sources));
            obj.put("args", nodeArray(n.args, sources));
        } else if (node instanceof GetPtrNode n) {
            obj.put("node", "getPtr");
            obj.put("permission", n.kind.name());
            obj.put("target", nodeToJson(n.target, sources));
        } else if (node instanceof DerefNode n) {
            obj.put("node", "deref");
            obj.put("expr", nodeToJson(n.expr, sources));
        } else if (node instanceof CastNode n) {
            obj.put("node", "cast");
            obj.put("unsafe", n.unsafe);
            obj.put("target", typeToJson(n.target, sources));
            obj.put("expr", nodeToJson(n.expr, sources));
        } else if (node instanceof NullNode) {
            obj.put("node", "null");
        } else if (node instanceof QualifiedNameNode n) {
            obj.put("node", "qualifiedName");
            obj.put("segments", stringArray(n.segments));
        } else if (node instanceof TypeNode n) {
            return typeToJson(n, sources);
        } else {
            throw new IllegalArgumentException(
                    "Unknown AST node type: " + node.getClass().getName()
            );
        }

        return obj;
    }

    static ASTNode nodeFromJson(JsonObject obj, SourceTable sources) {
        if (obj == null) {
            return null;
        }

        TypeNode type = maybeTypeFromJson(obj, sources);

        if (type != null) {
            return type;
        }

        String kind = obj.requireString("node");
        SourceSpan span = spanFromJson(obj, sources);

        if (!obj.has("span")) {
            throw new IllegalArgumentException("Missing required JSON field 'span'");
        }

        return switch (kind) {
            case "program" -> new ProgramNode(
                    span,
                    nodesFromArray(obj.requireArray("items"), sources)
            );
            case "namespace" -> new NamespaceNode(
                    span,
                    obj.requireString("name"),
                    nodesFromArray(obj.requireArray("items"), sources)
            );
            case "import" -> new ImportNode(span, obj.requireString("path"));
            case "block" -> new BlockNode(
                    span,
                    nodesFromArray(obj.requireArray("statements"), sources)
            );
            case "functionDecl" -> new FunctionDeclNode(
                    span,
                    obj.requireString("name"),
                    parametersFromArray(obj.requireArray("parameters"), sources),
                    typeFromJson(obj.requireObject("returnType"), sources),
                    (BlockNode) nodeFromJson(obj.optObject("body"), sources)
            );
            case "parameter" -> new ParameterNode(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name")
            );
            case "structDecl" -> new StructDeclNode(
                    span,
                    obj.requireString("name"),
                    fieldsFromArray(obj.requireArray("fields"), sources),
                    methodsFromArray(obj.requireArray("methods"), sources)
            );
            case "structFieldDecl" -> new StructFieldDeclNode(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name"),
                    nodeFromJson(obj.optObject("constInitializer"), sources)
            );
            case "globalVarDecl" -> {
                GlobalVarDeclNode node = new GlobalVarDeclNode(
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
            case "varDecl" -> new VarDeclNode(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name")
            );
            case "varDeclInit" -> new VarDeclInitNode(
                    span,
                    typeFromJson(obj.requireObject("type"), sources),
                    obj.requireString("name"),
                    nodeFromJson(obj.optObject("init"), sources)
            );
            case "exprStmt" -> new ExprStmtNode(
                    span,
                    nodeFromJson(obj.requireObject("expr"), sources)
            );
            case "assign" -> new AssignNode(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("target"), sources),
                    nodeFromJson(obj.requireObject("value"), sources)
            );
            case "incDec" -> new IncDecNode(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("target"), sources)
            );
            case "if" -> new IfNode(
                    span,
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodeFromJson(obj.requireObject("thenBranch"), sources),
                    nodeFromJson(obj.optObject("elseBranch"), sources)
            );
            case "while" -> new WhileNode(
                    span,
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodeFromJson(obj.requireObject("body"), sources),
                    nodesFromArray(obj.requireArray("update"), sources)
            );
            case "break" -> new BreakNode(
                    span,
                    nodeFromJson(obj.optObject("level"), sources)
            );
            case "continue" -> new ContinueNode(
                    span,
                    nodeFromJson(obj.optObject("level"), sources)
            );
            case "for" -> new ForNode(
                    span,
                    nodeFromJson(obj.optObject("init"), sources),
                    nodeFromJson(obj.requireObject("condition"), sources),
                    nodeFromJson(obj.optObject("update"), sources),
                    nodeFromJson(obj.requireObject("body"), sources)
            );
            case "return" -> new ReturnNode(
                    span,
                    nodeFromJson(obj.optObject("value"), sources)
            );
            case "var" -> new VarNode(span, obj.requireString("name"));
            case "int" -> new IntNode(span, obj.requireString("value"));
            case "inferredSize" -> new InferredSizeNode(span);
            case "arrayLiteral" -> new ArrayLiteralNode(
                    span,
                    nodesFromArray(obj.requireArray("elements"), sources)
            );
            case "stringLiteral" -> new StringLiteralNode(
                    span,
                    obj.requireString("rawText")
            );
            case "unary" -> new UnaryOpNode(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("expr"), sources)
            );
            case "binary" -> new BinOpNode(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("left"), sources),
                    nodeFromJson(obj.requireObject("right"), sources)
            );
            case "arrayAccess" -> new ArrayAccessNode(
                    span,
                    nodeFromJson(obj.requireObject("target"), sources),
                    nodeFromJson(obj.requireObject("index"), sources)
            );
            case "memberAccess" -> new MemberAccessNode(
                    span,
                    nodeFromJson(obj.requireObject("target"), sources),
                    obj.requireString("member")
            );
            case "call" -> new CallNode(
                    span,
                    nodeFromJson(obj.requireObject("callee"), sources),
                    nodesFromArray(obj.requireArray("args"), sources)
            );
            case "getPtr" -> new GetPtrNode(
                    span,
                    PtrPermission.valueOf(obj.requireString("permission")),
                    nodeFromJson(obj.requireObject("target"), sources)
            );
            case "deref" -> new DerefNode(
                    span,
                    nodeFromJson(obj.requireObject("expr"), sources)
            );
            case "cast" -> new CastNode(
                    span,
                    typeFromJson(obj.requireObject("target"), sources),
                    nodeFromJson(obj.requireObject("expr"), sources),
                    obj.requireBool("unsafe")
            );
            case "null" -> new NullNode(span);
            case "qualifiedName" -> new QualifiedNameNode(
                    span,
                    stringsFromArray(obj.requireArray("segments"))
            );
            default -> throw new IllegalArgumentException(
                    "Unknown AST node kind '" + kind + "'"
            );
        };
    }

    private static TypeNode maybeTypeFromJson(JsonObject obj, SourceTable sources) {
        return obj.has("type") && !obj.has("node")
                ? typeFromJson(obj, sources)
                : null;
    }

    static JsonObject typeToJson(TypeNode type, SourceTable sources) {
        if (type == null) {
            return null;
        }

        JsonObject obj = new JsonObject();
        obj.put("span", spanToJson(type.span, sources));

        if (type instanceof PrimitiveTypeNode n) {
            obj.put("type", "primitive");
            obj.put("kind", n.kind.name());
        } else if (type instanceof VoidTypeNode) {
            obj.put("type", "void");
        } else if (type instanceof DynamicArrayTypeNode n) {
            obj.put("type", "dynamicArray");
            obj.put("elementType", typeToJson(n.elementType, sources));
        } else if (type instanceof StaticArrayTypeNode n) {
            obj.put("type", "staticArray");
            obj.put("elementType", typeToJson(n.elementType, sources));
            obj.put("sizeExpr", nodeToJson(n.sizeExpr, sources));
        } else if (type instanceof PtrTypeNode n) {
            obj.put("type", "ptr");
            obj.put("permission", n.kind.name());
            obj.put("referencedType", typeToJson(n.referencedType, sources));
        } else if (type instanceof StructTypeNode n) {
            obj.put("type", "struct");
            obj.put(
                    "namespace",
                    n.namespaceSegments == null || n.namespaceSegments.isEmpty()
                            ? null
                            : String.join("::", n.namespaceSegments)
            );
            obj.put("name", n.name);
        } else {
            throw new IllegalArgumentException(
                    "Unknown AST type node: " + type.getClass().getName()
            );
        }

        return obj;
    }

    static TypeNode typeFromJson(JsonObject obj, SourceTable sources) {
        String kind = obj.requireString("type");
        SourceSpan span = spanFromJson(obj, sources);

        if (!obj.has("span")) {
            throw new IllegalArgumentException("Missing required JSON field 'span'");
        }

        return switch (kind) {
            case "primitive" -> new PrimitiveTypeNode(
                    span,
                    PrimitiveKind.valueOf(obj.requireString("kind"))
            );
            case "void" -> new VoidTypeNode(span);
            case "dynamicArray" -> new DynamicArrayTypeNode(
                    span,
                    typeFromJson(obj.requireObject("elementType"), sources)
            );
            case "staticArray" -> new StaticArrayTypeNode(
                    span,
                    typeFromJson(obj.requireObject("elementType"), sources),
                    nodeFromJson(obj.requireObject("sizeExpr"), sources)
            );
            case "ptr" -> new PtrTypeNode(
                    span,
                    PtrPermission.valueOf(obj.requireString("permission")),
                    typeFromJson(obj.requireObject("referencedType"), sources)
            );
            case "struct" -> {
                String namespace = obj.optString("namespace");
                List<String> segments = namespace == null || namespace.isEmpty()
                        ? List.of()
                        : List.of(namespace.split("::"));
                yield new StructTypeNode(span, segments, obj.requireString("name"));
            }
            default -> throw new IllegalArgumentException(
                    "Unknown AST type kind '" + kind + "'"
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

    private static ProgramNode requireProgram(ASTNode node) {
        if (!(node instanceof ProgramNode program)) {
            throw new IllegalArgumentException("AST document root must be a program node");
        }

        return program;
    }

    private static JsonArray nodeArray(List<? extends ASTNode> nodes, SourceTable sources) {
        JsonArray array = new JsonArray();

        if (nodes != null) {
            for (ASTNode node : nodes) {
                array.add(nodeToJson(node, sources));
            }
        }

        return array;
    }

    private static JsonArray parameterArray(List<ParameterNode> nodes, SourceTable sources) {
        JsonArray array = new JsonArray();

        for (ParameterNode node : nodes) {
            array.add(nodeToJson(node, sources));
        }

        return array;
    }

    private static JsonArray fieldArray(List<StructFieldDeclNode> nodes, SourceTable sources) {
        JsonArray array = new JsonArray();

        for (StructFieldDeclNode node : nodes) {
            array.add(nodeToJson(node, sources));
        }

        return array;
    }

    private static JsonArray methodArray(List<FunctionDeclNode> nodes, SourceTable sources) {
        JsonArray array = new JsonArray();

        for (FunctionDeclNode node : nodes) {
            array.add(nodeToJson(node, sources));
        }

        return array;
    }

    private static List<ASTNode> nodesFromArray(JsonArray array, SourceTable sources) {
        List<ASTNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            nodes.add(nodeFromJson(JsonAstSupport.requireObject(value, "AST node"), sources));
        }

        return nodes;
    }

    private static List<ParameterNode> parametersFromArray(
            JsonArray array,
            SourceTable sources
    ) {
        List<ParameterNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            ASTNode node = nodeFromJson(JsonAstSupport.requireObject(value, "parameter"), sources);

            if (!(node instanceof ParameterNode parameter)) {
                throw new IllegalArgumentException("Expected a parameter AST node");
            }

            nodes.add(parameter);
        }

        return nodes;
    }

    private static List<StructFieldDeclNode> fieldsFromArray(
            JsonArray array,
            SourceTable sources
    ) {
        List<StructFieldDeclNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            ASTNode node = nodeFromJson(
                    JsonAstSupport.requireObject(value, "struct field"),
                    sources
            );

            if (!(node instanceof StructFieldDeclNode field)) {
                throw new IllegalArgumentException("Expected a struct field AST node");
            }

            nodes.add(field);
        }

        return nodes;
    }

    private static List<FunctionDeclNode> methodsFromArray(
            JsonArray array,
            SourceTable sources
    ) {
        List<FunctionDeclNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            ASTNode node = nodeFromJson(JsonAstSupport.requireObject(value, "method"), sources);

            if (!(node instanceof FunctionDeclNode method)) {
                throw new IllegalArgumentException("Expected a method AST node");
            }

            nodes.add(method);
        }

        return nodes;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();

        for (String value : values) {
            array.add(value);
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
