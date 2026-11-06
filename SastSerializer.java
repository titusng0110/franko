import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class SastArtifact {
    final SastPhase phase;
    final EntryKind entryKind;
    final SourceTable sources;
    final SymbolRegistry symbols;
    final SastNode root;

    SastArtifact(
            SastPhase phase,
            EntryKind entryKind,
            SourceTable sources,
            SymbolRegistry symbols,
            SastNode root
    ) {
        this.phase = phase;
        this.entryKind = entryKind == null ? EntryKind.NONE : entryKind;
        this.sources = sources == null ? new SourceTable() : sources;
        this.symbols = symbols == null ? new SymbolRegistry() : symbols;
        this.root = root;
    }
}

public final class SastSerializer {

    private static final String FORMAT = "franko-sast";
    private static final int VERSION = 1;

    private SastSerializer() {
    }

    public static String toJson(SastArtifact doc) {
        if (doc == null) {
            throw new IllegalArgumentException("Cannot serialize a null SAST document");
        }

        registerFromNode(doc.root, doc.symbols);
        SemanticASTSerializer.registerRelatedSymbols(doc.symbols);

        JsonObject root = nodeToJson(doc.root, doc.symbols, doc.sources);
        SemanticASTSerializer.registerRelatedSymbols(doc.symbols);
        JsonArray symbols = SemanticASTSerializer.symbolsToJson(doc.symbols);

        JsonObject document = new JsonObject();
        document.put("format", FORMAT);
        document.put("version", VERSION);
        document.put("phase", doc.phase.jsonName());
        document.put("entryKind", doc.entryKind.name());
        document.put("sources", JsonAstSupport.sourceArray(doc.sources));
        document.put("symbols", symbols);
        document.put("root", root);

        return Json.write(document, true) + "\n";
    }

    public static SastArtifact fromJson(String json) {
        JsonObject document = JsonAstSupport.requireDocument(json, "SAST", FORMAT, VERSION);
        SastPhase phase = JsonAstSupport.requireEnumName(
                document.requireString("phase"),
                SastPhase.class
        );
        EntryKind entryKind = JsonAstSupport.requireEnumName(
                document.requireString("entryKind"),
                EntryKind.class
        );
        SourceTable sources = JsonAstSupport.sourceTable(document.requireArray("sources"), "SAST");
        JsonArray symbolArray = document.requireArray("symbols");
        SymbolRegistry registry = SemanticASTSerializer.symbolsFromJson(symbolArray, sources);
        SastNode root = nodeFromJson(
                document.requireObject("root"),
                registry,
                sources
        );

        if (root == null || root.kind != SastKind.PROGRAM) {
            throw new IllegalArgumentException("SAST document root must be a program node");
        }

        return new SastArtifact(phase, entryKind, sources, registry, root);
    }

    private static void registerFromNode(SastNode node, SymbolRegistry registry) {
        if (node == null) {
            return;
        }

        switch (node.kind) {
            case PROGRAM -> {
                for (SastNode item : node.topLevelItems) {
                    registerFromNode(item, registry);
                }
            }
            case NAMESPACE -> {
                for (SastNode item : node.items) {
                    registerFromNode(item, registry);
                }
            }
            case STRUCT_DECL -> {
                registry.register(node.structSymbol);

                for (FieldSymbol field : node.structSymbol.fields) {
                    registry.register(field);
                }

                for (SastNode method : node.methods) {
                    registerFunction(method, registry);
                }
            }
            case ENUM_DECL -> registry.register(node.enumSymbol);
            case FUNCTION_DECL -> registerFunction(node, registry);
            case VAR_DECL -> {
                registry.register(node.symbol);

                if (node.staticInitializer != null) {
                    registerFromExpr(node.staticInitializer, registry);
                }
            }
            case BLOCK -> {
                for (SastNode stmt : node.statements) {
                    registerFromNode(stmt, registry);
                }
            }
            case ASSIGN -> {
                registerFromExpr(node.target, registry);
                registerFromExpr(node.value, registry);
            }
            case IF -> {
                registerFromExpr(node.condition, registry);
                registerFromNode(node.thenBranch, registry);

                if (node.elseBranch != null) {
                    registerFromNode(node.elseBranch, registry);
                }
            }
            case WHILE -> {
                registerFromExpr(node.condition, registry);
                registerFromNode(node.body, registry);

                for (SastNode update : node.update) {
                    registerFromNode(update, registry);
                }
            }
            case RETURN -> {
                registry.register(node.function);

                if (node.value != null) {
                    registerFromExpr(node.value, registry);
                }
            }
            case EXPR_STMT -> registerFromExpr(node.expr, registry);
            default -> {
            }
        }
    }

    private static void registerFunction(SastNode function, SymbolRegistry registry) {
        registry.register(function.symbol);

        for (Symbol parameter : function.parameterVariables) {
            registry.register(parameter);
        }

        registerFromNode(function.body, registry);
    }

    private static void registerFromExpr(SastNode expr, SymbolRegistry registry) {
        if (expr == null) {
            return;
        }

        switch (expr.kind) {
            case VAR_EXPR -> registry.register(expr.symbol);
            case UNARY_OP -> registerFromExpr(expr.expr, registry);
            case BIN_OP -> {
                registerFromExpr(expr.left, registry);
                registerFromExpr(expr.right, registry);
            }
            case ARRAY_ACCESS -> {
                registerFromExpr(expr.target, registry);
                registerFromExpr(expr.index, registry);
            }
            case MEMBER_ACCESS -> {
                registry.register(expr.field);
                registerFromExpr(expr.target, registry);
            }
            case FUNCTION_CALL -> {
                registry.register(expr.function);

                for (SastNode arg : expr.args) {
                    registerFromExpr(arg, registry);
                }
            }
            case ARRAY_INTRINSIC_CALL -> {
                registerFromExpr(expr.receiver, registry);

                for (SastNode arg : expr.args) {
                    registerFromExpr(arg, registry);
                }
            }
            case GET_PTR -> registerFromExpr(expr.target, registry);
            case DEREF -> registerFromExpr(expr.expr, registry);
            case CAST -> registerFromExpr(expr.expr, registry);
            case AGGREGATE_LITERAL -> {
                for (SastNode field : expr.fields) {
                    registry.register(field.field);
                    registerFromExpr(field.value, registry);
                }
            }
            case ENUM_VARIANT -> registry.register(expr.enumSymbol);
            default -> {
            }
        }
    }

    private static JsonObject nodeToJson(
            SastNode node,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        if (node == null) {
            return null;
        }

        JsonObject obj = new JsonObject();
        obj.put("span", ASTSerializer.spanToJson(node.span, sources));

        if (node.isExpr()) {
            exprToJson(obj, node, registry, sources);
            return obj;
        }

        switch (node.kind) {
            case PROGRAM -> {
                obj.put("node", "program");
                obj.put("items", nodeArray(node.topLevelItems, registry, sources));
            }
            case NAMESPACE -> {
                obj.put("node", "namespace");
                obj.put("name", node.name);
                obj.put("items", nodeArray(node.items, registry, sources));
            }
            case FUNCTION_DECL -> {
                obj.put("node", "functionDecl");
                obj.put("symbol", registry.idFor(node.symbol));
                obj.put("parameterVariables", parameterVariableIds(
                        node.parameterVariables,
                        registry
                ));
                obj.put("body", nodeToJson(node.body, registry, sources));
            }
            case STRUCT_DECL -> {
                obj.put("node", "structDecl");
                obj.put("symbol", registry.idFor(node.structSymbol));
                obj.put("methods", nodeArray(node.methods, registry, sources));
            }
            case ENUM_DECL -> {
                obj.put("node", "enumDecl");
                obj.put("symbol", registry.idFor(node.enumSymbol));
            }
            case BLOCK -> {
                obj.put("node", "block");
                obj.put("statements", nodeArray(node.statements, registry, sources));
            }
            case VAR_DECL -> {
                obj.put("node", "varDecl");
                obj.put("symbol", registry.idFor(node.symbol));
                obj.put("foldedConstInitializer", SemanticASTSerializer.stringOrNull(
                        node.foldedConstInitializer));
                obj.put("staticInitializer", nodeToJson(
                        node.staticInitializer, registry, sources));
            }
            case ASSIGN -> {
                obj.put("node", "assign");
                obj.put("op", node.op);
                obj.put("target", nodeToJson(node.target, registry, sources));
                obj.put("value", nodeToJson(node.value, registry, sources));
            }
            case IF -> {
                obj.put("node", "if");
                obj.put("condition", nodeToJson(node.condition, registry, sources));
                obj.put("thenBranch", nodeToJson(node.thenBranch, registry, sources));
                obj.put("elseBranch", nodeToJson(node.elseBranch, registry, sources));
            }
            case WHILE -> {
                obj.put("node", "while");
                obj.put("condition", nodeToJson(node.condition, registry, sources));
                obj.put("body", nodeToJson(node.body, registry, sources));
                obj.put("update", nodeArray(node.update, registry, sources));
            }
            case BREAK -> {
                obj.put("node", "break");
                obj.put("levels", node.levels);
            }
            case CONTINUE -> obj.put("node", "continue");
            case RETURN -> {
                obj.put("node", "return");
                obj.put("function", registry.idFor(node.function));
                obj.put("value", nodeToJson(node.value, registry, sources));
            }
            case EXPR_STMT -> {
                obj.put("node", "exprStmt");
                obj.put("expr", nodeToJson(node.expr, registry, sources));
            }
            default -> throw new IllegalArgumentException(
                    "Unknown SAST node kind: " + node.kind
            );
        }

        return obj;
    }

    private static void exprToJson(
            JsonObject obj,
            SastNode expr,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        obj.put("type", SemanticASTSerializer.typeToJson(expr.type, registry));
        obj.put("constantValue", SemanticASTSerializer.stringOrNull(expr.constantValue));

        switch (expr.kind) {
            case INT_LITERAL -> {
                obj.put("node", "intLiteral");
                obj.put("rawValue", expr.rawValue);
            }
            case NULL_LITERAL -> obj.put("node", "nullLiteral");
            case VAR_EXPR -> {
                obj.put("node", "varExpr");
                obj.put("symbol", registry.idFor(expr.symbol));
            }
            case UNARY_OP -> {
                obj.put("node", "unaryOp");
                obj.put("op", expr.op);
                obj.put("expr", nodeToJson(expr.expr, registry, sources));
            }
            case BIN_OP -> {
                obj.put("node", "binOp");
                obj.put("op", expr.op);
                obj.put("left", nodeToJson(expr.left, registry, sources));
                obj.put("right", nodeToJson(expr.right, registry, sources));
            }
            case ARRAY_ACCESS -> {
                obj.put("node", "arrayAccess");
                obj.put("target", nodeToJson(expr.target, registry, sources));
                obj.put("index", nodeToJson(expr.index, registry, sources));
            }
            case MEMBER_ACCESS -> {
                obj.put("node", "memberAccess");
                obj.put("field", registry.idFor(expr.field));
                obj.put("target", nodeToJson(expr.target, registry, sources));
            }
            case FUNCTION_CALL -> {
                obj.put("node", "functionCall");
                obj.put("function", registry.idFor(expr.function));
                obj.put("args", nodeArray(expr.args, registry, sources));
            }
            case ARRAY_INTRINSIC_CALL -> {
                obj.put("node", "arrayIntrinsicCall");
                obj.put("kind", expr.intrinsicKind.name());
                obj.put("receiver", nodeToJson(expr.receiver, registry, sources));
                obj.put("args", nodeArray(expr.args, registry, sources));
            }
            case GET_PTR -> {
                obj.put("node", "getPtr");
                obj.put("target", nodeToJson(expr.target, registry, sources));

                if (expr.temporary) {
                    obj.put("temporary", true);
                }
            }
            case DEREF -> {
                obj.put("node", "deref");
                obj.put("expr", nodeToJson(expr.expr, registry, sources));
            }
            case CAST -> {
                obj.put("node", "cast");
                obj.put("checked", expr.checked);
                obj.put("expr", nodeToJson(expr.expr, registry, sources));
            }
            case ENUM_VARIANT -> {
                obj.put("node", "enumVariant");
                obj.put("enum", registry.idFor(expr.enumSymbol));
                obj.put("variant", expr.variant.name);
            }
            case AGGREGATE_LITERAL -> {
                obj.put("node", "aggregateLiteral");
                JsonArray fields = new JsonArray();

                for (SastNode init : expr.fields) {
                    JsonObject field = new JsonObject();
                    field.put("field", registry.idFor(init.field));
                    field.put("value", nodeToJson(init.value, registry, sources));
                    fields.add(field);
                }

                obj.put("fields", fields);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown SAST expression kind: " + expr.kind
            );
        }
    }

    private static JsonArray nodeArray(
            List<SastNode> nodes,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        JsonArray array = new JsonArray();

        if (nodes != null) {
            for (SastNode node : nodes) {
                array.add(nodeToJson(node, registry, sources));
            }
        }

        return array;
    }

    private static JsonArray parameterVariableIds(
            List<Symbol> parameters,
            SymbolRegistry registry
    ) {
        JsonArray array = new JsonArray();

        for (Symbol parameter : parameters) {
            array.add(registry.idFor(parameter));
        }

        return array;
    }

    private static SastNode nodeFromJson(
            JsonObject obj,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        if (obj == null) {
            return null;
        }

        String kind = obj.requireString("node");
        SourceSpan span = ASTSerializer.spanFromJson(obj, sources);

        if (!obj.has("span")) {
            throw new IllegalArgumentException("Missing required JSON field 'span'");
        }

        if (isExprKind(kind)) {
            return exprFromJson(obj, kind, registry, sources, span);
        }

        return switch (kind) {
            case "program" -> SastNode.program(
                    span,
                    itemsFromArray(obj.requireArray("items"), registry, sources)
            );
            case "namespace" -> SastNode.namespace(
                    span,
                    obj.requireString("name"),
                    itemsFromArray(obj.requireArray("items"), registry, sources)
            );
            case "functionDecl" -> SastNode.functionDecl(
                    span,
                    (Symbol) registry.require(obj.requireString("symbol")),
                    variablesFromArray(
                            obj.requireArray("parameterVariables"),
                            registry
                    ),
                    nodeFromJson(obj.requireObject("body"), registry, sources)
            );
            case "structDecl" -> SastNode.structDecl(
                    span,
                    (StructSymbol) registry.require(obj.requireString("symbol")),
                    methodNodesFromArray(
                            obj.requireArray("methods"), registry, sources)
            );
            case "enumDecl" -> SastNode.enumDecl(
                    span,
                    (EnumSymbol) registry.require(obj.requireString("symbol"))
            );
            case "block" -> SastNode.block(
                    span,
                    stmtNodesFromArray(
                            obj.requireArray("statements"), registry, sources)
            );
            case "varDecl" -> SastNode.varDecl(
                    span,
                    (Symbol) registry.require(obj.requireString("symbol")),
                    SemanticASTSerializer.bigIntegerOrNull(
                            obj.get("foldedConstInitializer")),
                    nodeFromJson(obj.optObject("staticInitializer"), registry, sources)
            );
            case "assign" -> SastNode.assign(
                    span,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("target"), registry, sources),
                    nodeFromJson(obj.requireObject("value"), registry, sources)
            );
            case "if" -> SastNode.ifStmt(
                    span,
                    nodeFromJson(obj.requireObject("condition"), registry, sources),
                    nodeFromJson(obj.requireObject("thenBranch"), registry, sources),
                    nodeFromJson(obj.optObject("elseBranch"), registry, sources)
            );
            case "while" -> SastNode.whileStmt(
                    span,
                    nodeFromJson(obj.requireObject("condition"), registry, sources),
                    nodeFromJson(obj.requireObject("body"), registry, sources),
                    stmtNodesFromArray(obj.requireArray("update"), registry, sources)
            );
            case "break" -> SastNode.breakStmt(span, obj.requireInt("levels"));
            case "continue" -> SastNode.continueStmt(span);
            case "return" -> SastNode.returnStmt(
                    span,
                    (Symbol) registry.require(obj.requireString("function")),
                    nodeFromJson(obj.optObject("value"), registry, sources)
            );
            case "exprStmt" -> SastNode.exprStmt(
                    span,
                    nodeFromJson(obj.requireObject("expr"), registry, sources)
            );
            default -> throw new IllegalArgumentException(
                    "Unknown SAST node kind '" + kind + "'"
            );
        };
    }

    private static boolean isExprKind(String kind) {
        return switch (kind) {
            case "intLiteral", "nullLiteral", "varExpr", "unaryOp", "binOp",
                    "arrayAccess", "memberAccess", "functionCall",
                    "arrayIntrinsicCall", "getPtr", "deref", "cast",
                    "aggregateLiteral", "enumVariant" -> true;
            default -> false;
        };
    }

    private static SastNode exprFromJson(
            JsonObject obj,
            String kind,
            SymbolRegistry registry,
            SourceTable sources,
            SourceSpan span
    ) {
        SastType type = SemanticASTSerializer.typeFromJson(
                obj.requireObject("type"), registry);
        BigInteger constantValue = SemanticASTSerializer.bigIntegerOrNull(
                obj.get("constantValue"));

        return switch (kind) {
            case "intLiteral" -> SastNode.intLiteral(
                    span,
                    type,
                    constantValue,
                    obj.requireString("rawValue")
            );
            case "nullLiteral" -> SastNode.nullLiteral(span, type);
            case "varExpr" -> SastNode.varExpr(
                    span,
                    type,
                    (Symbol) registry.require(obj.requireString("symbol"))
            );
            case "unaryOp" -> SastNode.unaryOp(
                    span,
                    type,
                    constantValue,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("expr"), registry, sources)
            );
            case "binOp" -> SastNode.binOp(
                    span,
                    type,
                    constantValue,
                    obj.requireString("op"),
                    nodeFromJson(obj.requireObject("left"), registry, sources),
                    nodeFromJson(obj.requireObject("right"), registry, sources)
            );
            case "arrayAccess" -> SastNode.arrayAccess(
                    span,
                    type,
                    nodeFromJson(obj.requireObject("target"), registry, sources),
                    nodeFromJson(obj.requireObject("index"), registry, sources)
            );
            case "memberAccess" -> SastNode.memberAccess(
                    span,
                    type,
                    nodeFromJson(obj.requireObject("target"), registry, sources),
                    (FieldSymbol) registry.require(obj.requireString("field"))
            );
            case "functionCall" -> SastNode.functionCall(
                    span,
                    type,
                    (Symbol) registry.require(obj.requireString("function")),
                    exprListFromArray(obj.requireArray("args"), registry, sources)
            );
            case "arrayIntrinsicCall" -> SastNode.arrayIntrinsicCall(
                    span,
                    SemanticArrayIntrinsicKind.valueOf(obj.requireString("kind")),
                    nodeFromJson(obj.requireObject("receiver"), registry, sources),
                    exprListFromArray(obj.requireArray("args"), registry, sources)
            );
            case "getPtr" -> SastNode.getPtr(
                    span,
                    type,
                    nodeFromJson(obj.requireObject("target"), registry, sources),
                    obj.has("temporary") && obj.requireBool("temporary")
            );
            case "deref" -> SastNode.deref(
                    span,
                    type,
                    nodeFromJson(obj.requireObject("expr"), registry, sources)
            );
            case "cast" -> SastNode.cast(
                    span,
                    type,
                    constantValue,
                    nodeFromJson(obj.requireObject("expr"), registry, sources),
                    obj.requireBool("checked")
            );
            case "enumVariant" -> {
                EnumSymbol enumSymbol = (EnumSymbol) registry.require(
                        obj.requireString("enum"));
                String variantName = obj.requireString("variant");
                EnumVariant variant = enumSymbol.resolveVariant(variantName);

                if (variant == null && !enumSymbol.variants.isEmpty()) {
                    variant = enumSymbol.variants.get(0);
                }

                if (variant == null) {
                    throw new IllegalArgumentException(
                            "Unknown enum variant '" + variantName
                                    + "' for enum '" + enumSymbol.qualifiedName() + "'"
                    );
                }

                yield SastNode.enumVariant(span, enumSymbol, variant);
            }
            case "aggregateLiteral" -> {
                List<SastNode> fields = new ArrayList<>();

                for (JsonValue value : obj.requireArray("fields").values()) {
                    JsonObject field = JsonAstSupport.requireObject(
                            value,
                            "aggregate field initializer"
                    );
                    fields.add(SastNode.aggregateFieldInit(
                            (FieldSymbol) registry.require(
                                    field.requireString("field")
                            ),
                            nodeFromJson(
                                    field.requireObject("value"),
                                    registry,
                                    sources
                            )
                    ));
                }

                yield SastNode.aggregateLiteral(span, type, fields);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown SAST expression kind '" + kind + "'"
            );
        };
    }

    private static List<SastNode> itemsFromArray(
            JsonArray array,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        List<SastNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            nodes.add(nodeFromJson(
                    JsonAstSupport.requireObject(value, "SAST item"),
                    registry,
                    sources
            ));
        }

        return nodes;
    }

    private static List<SastNode> stmtNodesFromArray(
            JsonArray array,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        List<SastNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            nodes.add(nodeFromJson(
                    JsonAstSupport.requireObject(value, "SAST statement"),
                    registry,
                    sources
            ));
        }

        return nodes;
    }

    private static List<SastNode> methodNodesFromArray(
            JsonArray array,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        List<SastNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            nodes.add(nodeFromJson(
                    JsonAstSupport.requireObject(value, "SAST method"),
                    registry,
                    sources
            ));
        }

        return nodes;
    }

    private static List<SastNode> exprListFromArray(
            JsonArray array,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        List<SastNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            nodes.add(nodeFromJson(
                    JsonAstSupport.requireObject(value, "SAST expression"),
                    registry,
                    sources
            ));
        }

        return nodes;
    }

    private static List<Symbol> variablesFromArray(
            JsonArray array,
            SymbolRegistry registry
    ) {
        List<Symbol> variables = new ArrayList<>();

        for (JsonValue value : array.values()) {
            Object symbol = registry.require(requireId(value));

            if (!(symbol instanceof Symbol variable)
                    || (variable.kind != SymbolKind.VARIABLE
                        && variable.kind != SymbolKind.PARAMETER
                        && variable.kind != SymbolKind.GLOBAL)) {
                throw new IllegalArgumentException("Expected a variable symbol");
            }

            variables.add(variable);
        }

        return variables;
    }

    private static String requireId(JsonValue value) {
        if (!(value instanceof JsonString string)) {
            throw new IllegalArgumentException("Expected a symbol ID string");
        }

        return string.value();
    }
}
