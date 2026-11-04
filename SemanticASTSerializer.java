import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SemanticASTSerializer {

    private static final String FORMAT = "franko-sast";
    private static final int VERSION = 1;

    private SemanticASTSerializer() {
    }

    public static String toJson(SastDocument doc) {
        if (doc == null) {
            throw new IllegalArgumentException("Cannot serialize a null SAST document");
        }

        registerFromNode(doc.root, doc.symbols);
        registerRelatedSymbols(doc.symbols);
        CURRENT_SOURCES.set(doc.sources);

        try {
            JsonObject root = nodeToJson(doc.root, doc.symbols);
            registerRelatedSymbols(doc.symbols);
            JsonArray symbols = symbolsToJson(doc.symbols);

            JsonObject document = new JsonObject();
            document.put("format", FORMAT);
            document.put("version", VERSION);
            document.put("phase", doc.phase.jsonName());
            document.put("entryKind", doc.entryKind.name());
            document.put("sources", JsonAstSupport.sourceArray(doc.sources));
            document.put("symbols", symbols);
            document.put("root", root);

            return Json.write(document, true) + "\n";
        } finally {
            CURRENT_SOURCES.remove();
        }
    }

    public static SastDocument fromJson(String json) {
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
        SymbolRegistry registry = symbolsFromJson(symbolArray, sources);
        SemanticProgramNode root = requireProgram(nodeFromJson(
                document.requireObject("root"),
                registry,
                sources
        ));

        return new SastDocument(phase, entryKind, sources, registry, root);
    }

    private static SemanticProgramNode requireProgram(SemanticASTNode node) {
        if (!(node instanceof SemanticProgramNode program)) {
            throw new IllegalArgumentException("SAST document root must be a program node");
        }

        return program;
    }

    static void registerFromNode(SemanticASTNode node, SymbolRegistry registry) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticProgramNode program) {
            for (SemanticASTNode item : program.topLevelItems) {
                registerFromNode(item, registry);
            }

            return;
        }

        if (node instanceof SemanticNamespaceNode namespace) {
            for (SemanticASTNode item : namespace.items) {
                registerFromNode(item, registry);
            }

            return;
        }

        if (node instanceof SemanticStructDeclNode struct) {
            registry.register(struct.symbol);

            for (FieldSymbol field : struct.symbol.fields) {
                registry.register(field);
            }

            for (SemanticFunctionDeclNode method : struct.methods) {
                registerFunction(method, registry);
            }

            return;
        }

        if (node instanceof SemanticFunctionDeclNode function) {
            registerFunction(function, registry);
            return;
        }

        if (node instanceof SemanticVarDeclNode varDecl) {
            registry.register(varDecl.symbol);

            if (varDecl.staticInitializer != null) {
                registerFromExpr(varDecl.staticInitializer, registry);
            }

            return;
        }

        if (node instanceof SemanticBlockNode block) {
            for (SemanticStmtNode stmt : block.statements) {
                registerFromNode(stmt, registry);
            }

            return;
        }

        if (node instanceof SemanticAssignNode assign) {
            registerFromExpr(assign.target, registry);
            registerFromExpr(assign.value, registry);
            return;
        }

        if (node instanceof SemanticIfNode ifNode) {
            registerFromExpr(ifNode.condition, registry);
            registerFromNode(ifNode.thenBranch, registry);

            if (ifNode.elseBranch != null) {
                registerFromNode(ifNode.elseBranch, registry);
            }

            return;
        }

        if (node instanceof SemanticWhileNode whileNode) {
            registerFromExpr(whileNode.condition, registry);
            registerFromNode(whileNode.body, registry);

            for (SemanticStmtNode update : whileNode.update) {
                registerFromNode(update, registry);
            }

            return;
        }

        if (node instanceof SemanticReturnNode returnNode) {
            registry.register(returnNode.function);

            if (returnNode.value != null) {
                registerFromExpr(returnNode.value, registry);
            }

            return;
        }

        if (node instanceof SemanticExprStmtNode exprStmt) {
            registerFromExpr(exprStmt.expr, registry);
        }
    }

    private static void registerFunction(
            SemanticFunctionDeclNode function,
            SymbolRegistry registry
    ) {
        registry.register(function.symbol);

        for (VariableSymbol parameter : function.parameterVariables) {
            registry.register(parameter);
        }

        registerFromNode(function.body, registry);
    }

    private static void registerFromExpr(SemanticExprNode expr, SymbolRegistry registry) {
        if (expr == null) {
            return;
        }

        if (expr instanceof SemanticVarExprNode varExpr) {
            registry.register(varExpr.symbol);
            return;
        }

        if (expr instanceof SemanticUnaryOpNode unary) {
            registerFromExpr(unary.expr, registry);
            return;
        }

        if (expr instanceof SemanticBinOpNode bin) {
            registerFromExpr(bin.left, registry);
            registerFromExpr(bin.right, registry);
            return;
        }

        if (expr instanceof SemanticArrayAccessNode access) {
            registerFromExpr(access.target, registry);
            registerFromExpr(access.index, registry);
            return;
        }

        if (expr instanceof SemanticMemberAccessNode member) {
            registry.register(member.field);
            registerFromExpr(member.target, registry);
            return;
        }

        if (expr instanceof SemanticFunctionCallNode call) {
            registry.register(call.function);

            for (SemanticExprNode arg : call.args) {
                registerFromExpr(arg, registry);
            }

            return;
        }

        if (expr instanceof SemanticArrayIntrinsicCallNode intrinsic) {
            registerFromExpr(intrinsic.receiver, registry);

            for (SemanticExprNode arg : intrinsic.args) {
                registerFromExpr(arg, registry);
            }

            return;
        }

        if (expr instanceof SemanticGetPtrNode getPtr) {
            registerFromExpr(getPtr.target, registry);
            return;
        }

        if (expr instanceof SemanticDerefNode deref) {
            registerFromExpr(deref.expr, registry);
            return;
        }

        if (expr instanceof SemanticCastExprNode cast) {
            registerFromExpr(cast.expr, registry);
        }
    }

    private static void registerRelatedSymbols(SymbolRegistry registry) {
        boolean changed = true;

        while (changed) {
            changed = false;

            for (Object symbol : registry.symbols()) {
                if (symbol instanceof StructSymbol struct) {
                    for (FieldSymbol field : struct.fields) {
                        changed |= registerNew(registry, field);
                    }

                    for (List<MethodSymbol> overloads : struct.methods.values()) {
                        for (MethodSymbol method : overloads) {
                            changed |= registerNew(registry, method);
                        }
                    }
                } else if (symbol instanceof MethodSymbol method) {
                    changed |= registerNew(registry, method.owner);

                    for (ParameterSymbol parameter : method.parameters) {
                        changed |= registerNew(registry, parameter);
                    }
                } else if (symbol instanceof FunctionSymbol function) {
                    for (ParameterSymbol parameter : function.parameters) {
                        changed |= registerNew(registry, parameter);
                    }
                }
            }
        }
    }

    private static boolean registerNew(SymbolRegistry registry, Object symbol) {
        if (registry.contains(symbol)) {
            return false;
        }

        registry.register(symbol);
        return true;
    }

    private static JsonArray symbolsToJson(SymbolRegistry registry) {
        JsonArray array = new JsonArray();

        for (Map.Entry<String, Object> entry : registry.entries()) {
            array.add(symbolToJson(entry.getKey(), entry.getValue(), registry));
        }

        return array;
    }

    private static JsonObject symbolToJson(
            String id,
            Object symbol,
            SymbolRegistry registry
    ) {
        JsonObject obj = new JsonObject();
        obj.put("id", id);

        if (symbol instanceof StructSymbol struct) {
            obj.put("kind", "struct");
            obj.put("name", struct.name);
            obj.put("namespace", struct.namespace);

            JsonArray fields = new JsonArray();

            for (FieldSymbol field : struct.fields) {
                fields.add(registry.idFor(field));
            }

            obj.put("fields", fields);

            JsonObject methods = new JsonObject();

            for (Map.Entry<String, List<MethodSymbol>> entry : struct.methods.entrySet()) {
                JsonArray overloads = new JsonArray();

                for (MethodSymbol method : entry.getValue()) {
                    overloads.add(registry.idFor(method));
                }

                methods.put(entry.getKey(), overloads);
            }

            obj.put("methods", methods);

            if (struct.isTuple) {
                obj.put("tuple", true);
            }
        } else if (symbol instanceof FieldSymbol field) {
            obj.put("kind", "field");
            obj.put("name", field.name);
            obj.put("type", typeToJson(field.type, registry));
            obj.put("foldedDefault", stringOrNull(field.foldedDefault));

            if (field.tupleIndex >= 0) {
                obj.put("tupleIndex", field.tupleIndex);
            }
        } else if (symbol instanceof MethodSymbol method) {
            obj.put("kind", "method");
            obj.put("name", method.name);
            obj.put("namespace", method.namespace);
            obj.put("owner", registry.idFor(method.owner));
            obj.put("parameters", parameterIds(method.parameters, registry));
            obj.put("returnType", typeToJson(method.returnType(), registry));
            obj.put("isBuiltin", method.isBuiltin);
        } else if (symbol instanceof FunctionSymbol function) {
            obj.put("kind", "function");
            obj.put("name", function.name);
            obj.put("namespace", function.namespace);
            obj.put("parameters", parameterIds(function.parameters, registry));
            obj.put("returnType", typeToJson(function.returnType(), registry));
            obj.put("isBuiltin", function.isBuiltin);
        } else if (symbol instanceof ParameterSymbol parameter) {
            obj.put("kind", "parameter");
            obj.put("name", parameter.name);
            obj.put("index", parameter.index);
            obj.put("type", typeToJson(parameter.type, registry));
        } else if (symbol instanceof GlobalVariableSymbol global) {
            obj.put("kind", "global");
            obj.put("name", global.name);
            obj.put("namespace", global.namespace);
            obj.put("type", typeToJson(global.type, registry));
            obj.put("compileTimeConstantValue", stringOrNull(global.compileTimeConstantValue));
            obj.put("literalBytes", JsonAstSupport.bigIntegerArray(global.literalBytes));
            obj.put("literalValues", JsonAstSupport.bigIntegerArray(global.literalValues));
        } else if (symbol instanceof VariableSymbol variable) {
            obj.put("kind", "variable");
            obj.put("name", variable.name);
            obj.put("namespace", variable.namespace);
            obj.put("type", typeToJson(variable.type, registry));
        } else {
            throw new IllegalArgumentException(
                    "Unknown symbol type: " + symbol.getClass().getName()
            );
        }

        return obj;
    }

    private static JsonArray parameterIds(
            List<ParameterSymbol> parameters,
            SymbolRegistry registry
    ) {
        JsonArray array = new JsonArray();

        for (ParameterSymbol parameter : parameters) {
            array.add(registry.idFor(parameter));
        }

        return array;
    }

    private static JsonValue stringOrNull(Object value) {
        return value == null ? JsonNull.INSTANCE : new JsonString(value.toString());
    }

    private static SymbolRegistry symbolsFromJson(JsonArray array, SourceTable sources) {
        SymbolRegistry registry = new SymbolRegistry();
        List<String> inputOrder = new ArrayList<>();
        Map<String, JsonObject> structShells = new LinkedHashMap<>();
        Map<String, JsonObject> pendingFields = new LinkedHashMap<>();
        Map<String, JsonObject> pendingParameters = new LinkedHashMap<>();
        Map<String, JsonObject> pendingFunctions = new LinkedHashMap<>();
        Map<String, JsonObject> pendingGlobals = new LinkedHashMap<>();

        for (JsonValue value : array.values()) {
            JsonObject obj = JsonAstSupport.requireObject(value, "symbol");
            String id = obj.requireString("id");
            String kind = obj.requireString("kind");
            inputOrder.add(id);

            switch (kind) {
                case "struct" -> {
                    StructSymbol struct = new StructSymbol(
                            obj.optString("namespace"),
                            obj.requireString("name"),
                            obj.has("tuple") && obj.requireBool("tuple")
                    );
                    registry.registerWithId(id, struct);
                    structShells.put(id, obj);
                }
                case "field" -> pendingFields.put(id, obj);
                case "parameter" -> pendingParameters.put(id, obj);
                case "function", "method" -> pendingFunctions.put(id, obj);
                case "global", "variable" -> pendingGlobals.put(id, obj);
                default -> throw new IllegalArgumentException(
                        "Unknown symbol kind '" + kind + "'"
                );
            }
        }

        for (Map.Entry<String, JsonObject> entry : pendingFields.entrySet()) {
            JsonObject obj = entry.getValue();
            FieldSymbol field = new FieldSymbol(
                    obj.requireString("name"),
                    typeFromJson(obj.requireObject("type"), registry),
                    bigIntegerOrNull(obj.get("foldedDefault")),
                    obj.has("tupleIndex") ? obj.requireInt("tupleIndex") : -1
            );
            registry.registerWithId(entry.getKey(), field);
        }

        for (Map.Entry<String, JsonObject> entry : pendingParameters.entrySet()) {
            JsonObject obj = entry.getValue();
            ParameterSymbol parameter = new ParameterSymbol(
                    obj.requireString("name"),
                    typeFromJson(obj.requireObject("type"), registry),
                    obj.requireInt("index")
            );
            registry.registerWithId(entry.getKey(), parameter);
        }

        for (Map.Entry<String, JsonObject> entry : pendingGlobals.entrySet()) {
            JsonObject obj = entry.getValue();
            String kind = obj.requireString("kind");
            String name = obj.requireString("name");
            String namespace = obj.optString("namespace");
            SemanticType type = typeFromJson(obj.requireObject("type"), registry);

            if ("global".equals(kind)) {
                GlobalVariableSymbol global = new GlobalVariableSymbol(
                        namespace,
                        name,
                        type
                );
                global.compileTimeConstantValue = bigIntegerOrNull(
                        obj.get("compileTimeConstantValue")
                );
                global.literalBytes = bigIntegersOrNull(obj.get("literalBytes"));
                global.literalValues = bigIntegersOrNull(obj.get("literalValues"));
                registry.registerWithId(entry.getKey(), global);
            } else {
                registry.registerWithId(
                        entry.getKey(),
                        new VariableSymbol(namespace, name, type)
                );
            }
        }

        for (Map.Entry<String, JsonObject> entry : pendingFunctions.entrySet()) {
            JsonObject obj = entry.getValue();
            String kind = obj.requireString("kind");
            String name = obj.requireString("name");
            List<ParameterSymbol> parameters = parameterList(
                    obj.requireArray("parameters"),
                    registry
            );
            SemanticType returnType = typeFromJson(
                    obj.requireObject("returnType"),
                    registry
            );
            boolean isBuiltin = obj.requireBool("isBuiltin");

            if ("method".equals(kind)) {
                StructSymbol owner = (StructSymbol) registry.require(
                        obj.requireString("owner")
                );
                registry.registerWithId(
                        entry.getKey(),
                        new MethodSymbol(owner, name, parameters, returnType, isBuiltin)
                );
            } else {
                registry.registerWithId(
                        entry.getKey(),
                        new FunctionSymbol(
                                obj.optString("namespace"),
                                name,
                                parameters,
                                returnType,
                                isBuiltin
                        )
                );
            }
        }

        for (Map.Entry<String, JsonObject> entry : structShells.entrySet()) {
            StructSymbol struct = (StructSymbol) registry.require(entry.getKey());
            JsonObject obj = entry.getValue();
            JsonArray fields = obj.requireArray("fields");

            for (JsonValue fieldValue : fields.values()) {
                String fieldId = requireId(fieldValue);
                Object field = registry.require(fieldId);

                if (!(field instanceof FieldSymbol fieldSymbol)) {
                    throw new IllegalArgumentException(
                            "Struct field reference '" + fieldId + "' is not a field"
                    );
                }

                struct.fields.add(fieldSymbol);
            }

            JsonObject methods = obj.requireObject("methods");

            for (Map.Entry<String, JsonValue> methodEntry : methods.entries()) {
                JsonArray overloads = requireArray(methodEntry.getValue(), "method overloads");
                List<MethodSymbol> symbols = new ArrayList<>();

                for (JsonValue methodValue : overloads.values()) {
                    String methodId = requireId(methodValue);
                    Object method = registry.require(methodId);

                    if (!(method instanceof MethodSymbol methodSymbol)) {
                        throw new IllegalArgumentException(
                                "Method reference '" + methodId + "' is not a method"
                        );
                    }

                    symbols.add(methodSymbol);
                }

                struct.methods.put(methodEntry.getKey(), symbols);
            }
        }

        registry.reorder(inputOrder);
        return registry;
    }

    private static List<ParameterSymbol> parameterList(
            JsonArray array,
            SymbolRegistry registry
    ) {
        List<ParameterSymbol> parameters = new ArrayList<>();

        for (JsonValue value : array.values()) {
            Object symbol = registry.require(requireId(value));

            if (!(symbol instanceof ParameterSymbol parameter)) {
                throw new IllegalArgumentException("Expected a parameter symbol");
            }

            parameters.add(parameter);
        }

        return parameters;
    }

    private static BigInteger bigIntegerOrNull(JsonValue value) {
        if (value == null || value instanceof JsonNull) {
            return null;
        }

        if (!(value instanceof JsonString string)) {
            throw new IllegalArgumentException("Expected a decimal string");
        }

        return new BigInteger(string.value());
    }

    private static List<BigInteger> bigIntegersOrNull(JsonValue value) {
        if (value == null || value instanceof JsonNull) {
            return null;
        }

        if (!(value instanceof JsonArray array)) {
            throw new IllegalArgumentException("Expected an array of decimal strings");
        }

        List<BigInteger> values = new ArrayList<>();

        for (JsonValue element : array.values()) {
            values.add(bigIntegerOrNull(element));
        }

        return values;
    }

    static JsonObject typeToJson(SemanticType type, SymbolRegistry registry) {
        if (type == null) {
            throw new IllegalArgumentException("Cannot serialize a null semantic type");
        }

        JsonObject obj = new JsonObject();

        if (type instanceof SemanticPrimitiveType primitive) {
            obj.put("type", "primitive");
            obj.put("kind", primitive.kind.name());
        } else if (type instanceof SemanticVoidType) {
            obj.put("type", "void");
        } else if (type instanceof SemanticDynamicArrayType dynamic) {
            obj.put("type", "dynamicArray");
            obj.put("elementType", typeToJson(dynamic.elementType, registry));
        } else if (type instanceof SemanticStaticArrayType staticArray) {
            obj.put("type", "staticArray");
            obj.put("elementType", typeToJson(staticArray.elementType, registry));
            obj.put("size", staticArray.sizeLiteral);
        } else if (type instanceof SemanticPtrType ptr) {
            obj.put("type", "ptr");
            obj.put("permission", ptr.permission.name());
            obj.put("referencedType", typeToJson(ptr.referencedType, registry));
        } else if (type instanceof SemanticNullType) {
            obj.put("type", "null");
        } else if (type instanceof SemanticStructType struct) {
            obj.put("type", "struct");
            obj.put("symbol", registry.idFor(struct.structSymbol));
        } else if (type instanceof SemanticTupleType tuple) {
            obj.put("type", "tuple");
            obj.put("symbol", registry.idFor(tuple.cppSymbol));

            JsonArray elements = new JsonArray();

            for (FieldSymbol field : tuple.fields) {
                JsonObject element = new JsonObject();
                element.put("name", field.name);
                element.put("type", typeToJson(field.type, registry));
                elements.add(element);
            }

            obj.put("elements", elements);
        } else if (type instanceof SemanticFunctionType function) {
            obj.put("type", "function");
            JsonArray parameters = new JsonArray();

            for (SemanticType parameter : function.parameterTypes) {
                parameters.add(typeToJson(parameter, registry));
            }

            obj.put("parameters", parameters);
            obj.put("returnType", typeToJson(function.returnType, registry));
        } else {
            throw new IllegalArgumentException(
                    "Unknown semantic type: " + type.getClass().getName()
            );
        }

        return obj;
    }

    static SemanticType typeFromJson(JsonObject obj, SymbolRegistry registry) {
        String kind = obj.requireString("type");

        return switch (kind) {
            case "primitive" -> new SemanticPrimitiveType(
                    SemanticPrimitiveKind.valueOf(obj.requireString("kind"))
            );
            case "void" -> new SemanticVoidType();
            case "dynamicArray" -> new SemanticDynamicArrayType(
                    typeFromJson(obj.requireObject("elementType"), registry)
            );
            case "staticArray" -> new SemanticStaticArrayType(
                    typeFromJson(obj.requireObject("elementType"), registry),
                    obj.requireString("size")
            );
            case "ptr" -> {
                SemanticType referenced = typeFromJson(
                        obj.requireObject("referencedType"),
                        registry
                );
                PtrPermission permission = PtrPermission.valueOf(
                        obj.requireString("permission")
                );
                yield switch (permission) {
                    case OWNER -> new SemanticPtrType(referenced);
                    case MUTABLE -> new SemanticMViewType(referenced);
                    case READ_ONLY -> new SemanticViewType(referenced);
                };
            }
            case "null" -> new SemanticNullType();
            case "struct" -> new SemanticStructType(
                    (StructSymbol) registry.require(obj.requireString("symbol"))
            );
            case "tuple" -> {
                StructSymbol cppSymbol = (StructSymbol) registry.require(
                        obj.requireString("symbol")
                );
                List<FieldSymbol> fields = new ArrayList<>();
                int index = 0;

                for (JsonValue value : obj.requireArray("elements").values()) {
                    JsonObject element = JsonAstSupport.requireObject(
                            value,
                            "tuple element"
                    );
                    String name = element.optString("name");

                    if (name == null) {
                        name = "_" + index;
                    }

                    fields.add(new FieldSymbol(
                            name,
                            typeFromJson(
                                    element.requireObject("type"),
                                    registry
                            ),
                            null,
                            index
                    ));
                    index++;
                }

                yield new SemanticTupleType(fields, cppSymbol);
            }
            case "function" -> {
                List<SemanticType> parameters = new ArrayList<>();

                for (JsonValue value : obj.requireArray("parameters").values()) {
                    parameters.add(typeFromJson(
                            JsonAstSupport.requireObject(value, "function parameter type"),
                            registry
                    ));
                }

                yield new SemanticFunctionType(
                        parameters,
                        typeFromJson(obj.requireObject("returnType"), registry)
                );
            }
            default -> throw new IllegalArgumentException(
                    "Unknown semantic type kind '" + kind + "'"
            );
        };
    }

    private static JsonObject nodeToJson(
            SemanticASTNode node,
            SymbolRegistry registry
    ) {
        if (node == null) {
            return null;
        }

        JsonObject obj = new JsonObject();
        obj.put("span", ASTSerializer.spanToJson(node.span, CURRENT_SOURCES.get()));
        return nodeToJsonInto(obj, node, registry);
    }

    private static final ThreadLocal<SourceTable> CURRENT_SOURCES = new ThreadLocal<>();

    private static JsonObject nodeToJsonInto(
            JsonObject obj,
            SemanticASTNode node,
            SymbolRegistry registry
    ) {
        if (node instanceof SemanticProgramNode program) {
            obj.put("node", "program");
            obj.put("items", nodeArray(program.topLevelItems, registry));
        } else if (node instanceof SemanticNamespaceNode namespace) {
            obj.put("node", "namespace");
            obj.put("name", namespace.name);
            obj.put("items", nodeArray(namespace.items, registry));
        } else if (node instanceof SemanticFunctionDeclNode function) {
            obj.put("node", "functionDecl");
            obj.put("symbol", registry.idFor(function.symbol));
            obj.put("parameterVariables", parameterVariableIds(
                    function.parameterVariables,
                    registry
            ));
            obj.put("body", nodeToJson(function.body, registry));
        } else if (node instanceof SemanticStructDeclNode struct) {
            obj.put("node", "structDecl");
            obj.put("symbol", registry.idFor(struct.symbol));
            obj.put("methods", nodeArray(struct.methods, registry));
        } else if (node instanceof SemanticBlockNode block) {
            obj.put("node", "block");
            obj.put("statements", nodeArray(block.statements, registry));
        } else if (node instanceof SemanticVarDeclNode varDecl) {
            obj.put("node", "varDecl");
            obj.put("symbol", registry.idFor(varDecl.symbol));
            obj.put("foldedConstInitializer", stringOrNull(varDecl.foldedConstInitializer));
            obj.put("staticInitializer", nodeToJson(varDecl.staticInitializer, registry));
        } else if (node instanceof SemanticAssignNode assign) {
            obj.put("node", "assign");
            obj.put("op", assign.op);
            obj.put("target", nodeToJson(assign.target, registry));
            obj.put("value", nodeToJson(assign.value, registry));
        } else if (node instanceof SemanticIfNode ifNode) {
            obj.put("node", "if");
            obj.put("condition", nodeToJson(ifNode.condition, registry));
            obj.put("thenBranch", nodeToJson(ifNode.thenBranch, registry));
            obj.put("elseBranch", nodeToJson(ifNode.elseBranch, registry));
        } else if (node instanceof SemanticWhileNode whileNode) {
            obj.put("node", "while");
            obj.put("condition", nodeToJson(whileNode.condition, registry));
            obj.put("body", nodeToJson(whileNode.body, registry));
            obj.put("update", nodeArray(whileNode.update, registry));
        } else if (node instanceof SemanticBreakNode breakNode) {
            obj.put("node", "break");
            obj.put("levels", breakNode.levels);
        } else if (node instanceof SemanticContinueNode) {
            obj.put("node", "continue");
        } else if (node instanceof SemanticReturnNode returnNode) {
            obj.put("node", "return");
            obj.put("function", registry.idFor(returnNode.function));
            obj.put("value", nodeToJson(returnNode.value, registry));
        } else if (node instanceof SemanticExprStmtNode exprStmt) {
            obj.put("node", "exprStmt");
            obj.put("expr", nodeToJson(exprStmt.expr, registry));
        } else if (node instanceof SemanticExprNode expr) {
            exprToJson(obj, expr, registry);
        } else {
            throw new IllegalArgumentException(
                    "Unknown SAST node type: " + node.getClass().getName()
            );
        }

        return obj;
    }

    private static void exprToJson(
            JsonObject obj,
            SemanticExprNode expr,
            SymbolRegistry registry
    ) {
        obj.put("type", typeToJson(expr.type, registry));
        obj.put("constantValue", stringOrNull(expr.constantValue));

        if (expr instanceof SemanticIntLiteralNode literal) {
            obj.put("node", "intLiteral");
            obj.put("rawValue", literal.rawValue);
        } else if (expr instanceof SemanticNullLiteralNode) {
            obj.put("node", "nullLiteral");
        } else if (expr instanceof SemanticVarExprNode varExpr) {
            obj.put("node", "varExpr");
            obj.put("symbol", registry.idFor(varExpr.symbol));
        } else if (expr instanceof SemanticUnaryOpNode unary) {
            obj.put("node", "unaryOp");
            obj.put("op", unary.op);
            obj.put("expr", nodeToJson(unary.expr, registry));
        } else if (expr instanceof SemanticBinOpNode bin) {
            obj.put("node", "binOp");
            obj.put("op", bin.op);
            obj.put("left", nodeToJson(bin.left, registry));
            obj.put("right", nodeToJson(bin.right, registry));
        } else if (expr instanceof SemanticArrayAccessNode access) {
            obj.put("node", "arrayAccess");
            obj.put("target", nodeToJson(access.target, registry));
            obj.put("index", nodeToJson(access.index, registry));
        } else if (expr instanceof SemanticMemberAccessNode member) {
            obj.put("node", "memberAccess");
            obj.put("field", registry.idFor(member.field));
            obj.put("target", nodeToJson(member.target, registry));
        } else if (expr instanceof SemanticFunctionCallNode call) {
            obj.put("node", "functionCall");
            obj.put("function", registry.idFor(call.function));
            obj.put("args", nodeArray(call.args, registry));
        } else if (expr instanceof SemanticArrayIntrinsicCallNode intrinsic) {
            obj.put("node", "arrayIntrinsicCall");
            obj.put("kind", intrinsic.kind.name());
            obj.put("receiver", nodeToJson(intrinsic.receiver, registry));
            obj.put("args", nodeArray(intrinsic.args, registry));
        } else if (expr instanceof SemanticGetPtrNode getPtr) {
            obj.put("node", "getPtr");
            obj.put("target", nodeToJson(getPtr.target, registry));
            if (getPtr.temporary) {
                obj.put("temporary", true);
            }
        } else if (expr instanceof SemanticDerefNode deref) {
            obj.put("node", "deref");
            obj.put("expr", nodeToJson(deref.expr, registry));
        } else if (expr instanceof SemanticCastExprNode cast) {
            obj.put("node", "cast");
            obj.put("checked", cast.checked);
            obj.put("expr", nodeToJson(cast.expr, registry));
        } else {
            throw new IllegalArgumentException(
                    "Unknown SAST expression type: " + expr.getClass().getName()
            );
        }
    }

    private static JsonArray nodeArray(
            List<? extends SemanticASTNode> nodes,
            SymbolRegistry registry
    ) {
        JsonArray array = new JsonArray();

        if (nodes != null) {
            for (SemanticASTNode node : nodes) {
                array.add(nodeToJson(node, registry));
            }
        }

        return array;
    }

    private static JsonArray parameterVariableIds(
            List<VariableSymbol> parameters,
            SymbolRegistry registry
    ) {
        JsonArray array = new JsonArray();

        for (VariableSymbol parameter : parameters) {
            array.add(registry.idFor(parameter));
        }

        return array;
    }

    private static SemanticASTNode nodeFromJson(
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
            case "program" -> new SemanticProgramNode(
                    span,
                    itemsFromArray(obj.requireArray("items"), registry, sources)
            );
            case "namespace" -> new SemanticNamespaceNode(
                    span,
                    obj.requireString("name"),
                    itemsFromArray(obj.requireArray("items"), registry, sources)
            );
            case "functionDecl" -> new SemanticFunctionDeclNode(
                    span,
                    (FunctionSymbol) registry.require(obj.requireString("symbol")),
                    variablesFromArray(
                            obj.requireArray("parameterVariables"),
                            registry
                    ),
                    (SemanticBlockNode) nodeFromJson(
                            obj.requireObject("body"),
                            registry,
                            sources
                    )
            );
            case "structDecl" -> new SemanticStructDeclNode(
                    span,
                    (StructSymbol) registry.require(obj.requireString("symbol")),
                    methodNodesFromArray(obj.requireArray("methods"), registry, sources)
            );
            case "block" -> new SemanticBlockNode(
                    span,
                    stmtNodesFromArray(
                            obj.requireArray("statements"),
                            registry,
                            sources
                    )
            );
            case "varDecl" -> new SemanticVarDeclNode(
                    span,
                    (VariableSymbol) registry.require(obj.requireString("symbol")),
                    bigIntegerOrNull(obj.get("foldedConstInitializer")),
                    (SemanticExprNode) nodeFromJson(
                            obj.optObject("staticInitializer"),
                            registry,
                            sources
                    )
            );
            case "assign" -> new SemanticAssignNode(
                    span,
                    obj.requireString("op"),
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("target"),
                            registry,
                            sources
                    ),
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("value"),
                            registry,
                            sources
                    )
            );
            case "if" -> new SemanticIfNode(
                    span,
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("condition"),
                            registry,
                            sources
                    ),
                    (SemanticStmtNode) nodeFromJson(
                            obj.requireObject("thenBranch"),
                            registry,
                            sources
                    ),
                    (SemanticStmtNode) nodeFromJson(
                            obj.optObject("elseBranch"),
                            registry,
                            sources
                    )
            );
            case "while" -> new SemanticWhileNode(
                    span,
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("condition"),
                            registry,
                            sources
                    ),
                    (SemanticStmtNode) nodeFromJson(
                            obj.requireObject("body"),
                            registry,
                            sources
                    ),
                    stmtNodesFromArray(obj.requireArray("update"), registry, sources)
            );
            case "break" -> new SemanticBreakNode(span, obj.requireInt("levels"));
            case "continue" -> new SemanticContinueNode(span);
            case "return" -> new SemanticReturnNode(
                    span,
                    (FunctionSymbol) registry.require(obj.requireString("function")),
                    (SemanticExprNode) nodeFromJson(
                            obj.optObject("value"),
                            registry,
                            sources
                    )
            );
            case "exprStmt" -> new SemanticExprStmtNode(
                    span,
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("expr"),
                            registry,
                            sources
                    )
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
                    "arrayIntrinsicCall", "getPtr", "deref", "cast" -> true;
            default -> false;
        };
    }

    private static SemanticExprNode exprFromJson(
            JsonObject obj,
            String kind,
            SymbolRegistry registry,
            SourceTable sources,
            SourceSpan span
    ) {
        SemanticType type = typeFromJson(obj.requireObject("type"), registry);
        BigInteger constantValue = bigIntegerOrNull(obj.get("constantValue"));

        return switch (kind) {
            case "intLiteral" -> new SemanticIntLiteralNode(
                    span,
                    type,
                    constantValue,
                    obj.requireString("rawValue")
            );
            case "nullLiteral" -> new SemanticNullLiteralNode(span, type);
            case "varExpr" -> new SemanticVarExprNode(
                    span,
                    type,
                    (VariableSymbol) registry.require(obj.requireString("symbol"))
            );
            case "unaryOp" -> new SemanticUnaryOpNode(
                    span,
                    type,
                    constantValue,
                    obj.requireString("op"),
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("expr"),
                            registry,
                            sources
                    )
            );
            case "binOp" -> new SemanticBinOpNode(
                    span,
                    type,
                    constantValue,
                    obj.requireString("op"),
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("left"),
                            registry,
                            sources
                    ),
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("right"),
                            registry,
                            sources
                    )
            );
            case "arrayAccess" -> new SemanticArrayAccessNode(
                    span,
                    type,
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("target"),
                            registry,
                            sources
                    ),
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("index"),
                            registry,
                            sources
                    )
            );
            case "memberAccess" -> new SemanticMemberAccessNode(
                    span,
                    type,
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("target"),
                            registry,
                            sources
                    ),
                    (FieldSymbol) registry.require(obj.requireString("field"))
            );
            case "functionCall" -> new SemanticFunctionCallNode(
                    span,
                    type,
                    (FunctionSymbol) registry.require(obj.requireString("function")),
                    exprListFromArray(obj.requireArray("args"), registry, sources)
            );
            case "arrayIntrinsicCall" -> new SemanticArrayIntrinsicCallNode(
                    span,
                    SemanticArrayIntrinsicKind.valueOf(obj.requireString("kind")),
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("receiver"),
                            registry,
                            sources
                    ),
                    exprListFromArray(obj.requireArray("args"), registry, sources)
            );
            case "getPtr" -> new SemanticGetPtrNode(
                    span,
                    type,
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("target"),
                            registry,
                            sources
                    ),
                    obj.has("temporary") && obj.requireBool("temporary")
            );
            case "deref" -> new SemanticDerefNode(
                    span,
                    type,
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("expr"),
                            registry,
                            sources
                    )
            );
            case "cast" -> new SemanticCastExprNode(
                    span,
                    type,
                    constantValue,
                    (SemanticExprNode) nodeFromJson(
                            obj.requireObject("expr"),
                            registry,
                            sources
                    ),
                    obj.requireBool("checked")
            );
            default -> throw new IllegalArgumentException(
                    "Unknown SAST expression kind '" + kind + "'"
            );
        };
    }

    private static List<SemanticASTNode> itemsFromArray(
            JsonArray array,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        List<SemanticASTNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            nodes.add(nodeFromJson(
                    JsonAstSupport.requireObject(value, "SAST item"),
                    registry,
                    sources
            ));
        }

        return nodes;
    }

    private static List<SemanticStmtNode> stmtNodesFromArray(
            JsonArray array,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        List<SemanticStmtNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            nodes.add((SemanticStmtNode) nodeFromJson(
                    JsonAstSupport.requireObject(value, "SAST statement"),
                    registry,
                    sources
            ));
        }

        return nodes;
    }

    private static List<SemanticFunctionDeclNode> methodNodesFromArray(
            JsonArray array,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        List<SemanticFunctionDeclNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            nodes.add((SemanticFunctionDeclNode) nodeFromJson(
                    JsonAstSupport.requireObject(value, "SAST method"),
                    registry,
                    sources
            ));
        }

        return nodes;
    }

    private static List<SemanticExprNode> exprListFromArray(
            JsonArray array,
            SymbolRegistry registry,
            SourceTable sources
    ) {
        List<SemanticExprNode> nodes = new ArrayList<>();

        for (JsonValue value : array.values()) {
            nodes.add((SemanticExprNode) nodeFromJson(
                    JsonAstSupport.requireObject(value, "SAST expression"),
                    registry,
                    sources
            ));
        }

        return nodes;
    }

    private static List<VariableSymbol> variablesFromArray(
            JsonArray array,
            SymbolRegistry registry
    ) {
        List<VariableSymbol> variables = new ArrayList<>();

        for (JsonValue value : array.values()) {
            Object symbol = registry.require(requireId(value));

            if (!(symbol instanceof VariableSymbol variable)) {
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

    private static JsonArray requireArray(JsonValue value, String description) {
        if (!(value instanceof JsonArray array)) {
            throw new IllegalArgumentException("Expected " + description + " to be an array");
        }

        return array;
    }

}
