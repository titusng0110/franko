import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SastSymbolSupport {

    private SastSymbolSupport() {
    }

    static void registerRelatedSymbols(SymbolRegistry registry) {
        boolean changed = true;

        while (changed) {
            changed = false;

            for (Object symbol : registry.symbols()) {
                if (symbol instanceof StructSymbol struct) {
                    for (FieldSymbol field : struct.fields) {
                        changed |= registerNew(registry, field);
                    }

                    for (List<Symbol> overloads : struct.methods.values()) {
                        for (Symbol method : overloads) {
                            changed |= registerNew(registry, method);
                        }
                    }
                } else if (symbol instanceof Symbol sym
                        && sym.kind == SymbolKind.METHOD) {
                    changed |= registerNew(registry, sym.owner);

                    for (Symbol parameter : sym.parameters) {
                        changed |= registerNew(registry, parameter);
                    }
                } else if (symbol instanceof Symbol sym
                        && sym.kind == SymbolKind.FUNCTION) {
                    for (Symbol parameter : sym.parameters) {
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

    static JsonArray symbolsToJson(SymbolRegistry registry) {
        JsonArray array = new JsonArray();

        for (Map.Entry<String, Object> entry : registry.entries()) {
            array.add(symbolToJson(entry.getKey(), entry.getValue(), registry));
        }

        return array;
    }

    static JsonObject symbolToJson(
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

            for (Map.Entry<String, List<Symbol>> entry : struct.methods.entrySet()) {
                JsonArray overloads = new JsonArray();

                for (Symbol method : entry.getValue()) {
                    overloads.add(registry.idFor(method));
                }

                methods.put(entry.getKey(), overloads);
            }

            obj.put("methods", methods);

            if (struct.isTuple) {
                obj.put("tuple", true);
            }
        } else if (symbol instanceof EnumSymbol enumSymbol) {
            obj.put("kind", "enum");
            obj.put("name", enumSymbol.name);
            obj.put("namespace", enumSymbol.namespace);
            obj.put("backing", enumSymbol.backingKind.name());

            JsonArray variants = new JsonArray();

            for (EnumVariant variant : enumSymbol.variants) {
                JsonObject variantObj = new JsonObject();
                variantObj.put("name", variant.name);
                variantObj.put("value", variant.value.toString(10));
                variants.add(variantObj);
            }

            obj.put("variants", variants);
        } else if (symbol instanceof FieldSymbol field) {
            obj.put("kind", "field");
            obj.put("name", field.name);
            obj.put("type", typeToJson(field.type, registry));
            obj.put("foldedDefault", stringOrNull(field.foldedDefault));

            if (field.tupleIndex >= 0) {
                obj.put("tupleIndex", field.tupleIndex);
            }
        } else if (symbol instanceof Symbol sym
                && sym.kind == SymbolKind.METHOD) {
            obj.put("kind", "method");
            obj.put("name", sym.name);
            obj.put("namespace", sym.namespace);
            obj.put("owner", registry.idFor(sym.owner));
            obj.put("parameters", parameterIds(sym.parameters, registry));
            obj.put("returnType", typeToJson(sym.returnType(), registry));
            obj.put("isBuiltin", sym.isBuiltin);
        } else if (symbol instanceof Symbol sym
                && sym.kind == SymbolKind.FUNCTION) {
            obj.put("kind", "function");
            obj.put("name", sym.name);
            obj.put("namespace", sym.namespace);
            obj.put("parameters", parameterIds(sym.parameters, registry));
            obj.put("returnType", typeToJson(sym.returnType(), registry));
            obj.put("isBuiltin", sym.isBuiltin);
        } else if (symbol instanceof Symbol sym
                && sym.kind == SymbolKind.PARAMETER) {
            obj.put("kind", "parameter");
            obj.put("name", sym.name);
            obj.put("index", sym.index);
            obj.put("type", typeToJson(sym.type, registry));
        } else if (symbol instanceof Symbol sym
                && sym.kind == SymbolKind.GLOBAL) {
            obj.put("kind", "global");
            obj.put("name", sym.name);
            obj.put("namespace", sym.namespace);
            obj.put("type", typeToJson(sym.type, registry));
            obj.put("compileTimeConstantValue", stringOrNull(sym.compileTimeConstantValue));
            obj.put("literalBytes", JsonAstSupport.bigIntegerArray(sym.literalBytes));
            obj.put("literalValues", JsonAstSupport.bigIntegerArray(sym.literalValues));
        } else if (symbol instanceof Symbol sym) {
            obj.put("kind", "variable");
            obj.put("name", sym.name);
            obj.put("namespace", sym.namespace);
            obj.put("type", typeToJson(sym.type, registry));
        } else {
            throw new IllegalArgumentException(
                    "Unknown symbol type: " + symbol.getClass().getName()
            );
        }

        return obj;
    }

    static JsonArray parameterIds(
            List<Symbol> parameters,
            SymbolRegistry registry
    ) {
        JsonArray array = new JsonArray();

        for (Symbol parameter : parameters) {
            array.add(registry.idFor(parameter));
        }

        return array;
    }

    static JsonValue stringOrNull(Object value) {
        return value == null ? JsonNull.INSTANCE : new JsonString(value.toString());
    }

    static SymbolRegistry symbolsFromJson(JsonArray array, SourceTable sources) {
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
                case "enum" -> {
                    SemanticPrimitiveKind backing = SemanticPrimitiveKind.valueOf(
                            obj.requireString("backing")
                    );
                    List<EnumVariant> variants = new ArrayList<>();

                    for (JsonValue variantValue : obj.requireArray("variants").values()) {
                        JsonObject variantObj = JsonAstSupport.requireObject(variantValue, "enum variant");
                        variants.add(new EnumVariant(
                                variantObj.requireString("name"),
                                new java.math.BigInteger(variantObj.requireString("value"))
                        ));
                    }

                    EnumSymbol enumSymbol = new EnumSymbol(
                            obj.requireString("name"),
                            obj.optString("namespace"),
                            backing,
                            variants
                    );
                    registry.registerWithId(id, enumSymbol);
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
            Symbol parameter = Symbol.parameter(
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
            SastType type = typeFromJson(obj.requireObject("type"), registry);

            if ("global".equals(kind)) {
                Symbol global = Symbol.global(namespace, name, type);
                global.compileTimeConstantValue = bigIntegerOrNull(
                        obj.get("compileTimeConstantValue")
                );
                global.literalBytes = bigIntegersOrNull(obj.get("literalBytes"));
                global.literalValues = bigIntegersOrNull(obj.get("literalValues"));
                registry.registerWithId(entry.getKey(), global);
            } else {
                registry.registerWithId(
                        entry.getKey(),
                        Symbol.variable(namespace, name, type)
                );
            }
        }

        for (Map.Entry<String, JsonObject> entry : pendingFunctions.entrySet()) {
            JsonObject obj = entry.getValue();
            String kind = obj.requireString("kind");
            String name = obj.requireString("name");
            List<Symbol> parameters = parameterList(
                    obj.requireArray("parameters"),
                    registry
            );
            SastType returnType = typeFromJson(
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
                        Symbol.method(owner, name, parameters, returnType, isBuiltin)
                );
            } else {
                registry.registerWithId(
                        entry.getKey(),
                        Symbol.function(
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
                List<Symbol> symbols = new ArrayList<>();

                for (JsonValue methodValue : overloads.values()) {
                    String methodId = requireId(methodValue);
                    Object method = registry.require(methodId);

                    if (!(method instanceof Symbol methodSymbol)
                            || methodSymbol.kind != SymbolKind.METHOD) {
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

    static List<Symbol> parameterList(
            JsonArray array,
            SymbolRegistry registry
    ) {
        List<Symbol> parameters = new ArrayList<>();

        for (JsonValue value : array.values()) {
            Object symbol = registry.require(requireId(value));

            if (!(symbol instanceof Symbol parameter)
                    || parameter.kind != SymbolKind.PARAMETER) {
                throw new IllegalArgumentException("Expected a parameter symbol");
            }

            parameters.add(parameter);
        }

        return parameters;
    }

    static BigInteger bigIntegerOrNull(JsonValue value) {
        if (value == null || value instanceof JsonNull) {
            return null;
        }

        if (!(value instanceof JsonString string)) {
            throw new IllegalArgumentException("Expected a decimal string");
        }

        return new BigInteger(string.value());
    }

    static List<BigInteger> bigIntegersOrNull(JsonValue value) {
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

    static JsonObject typeToJson(SastType type, SymbolRegistry registry) {
        if (type == null) {
            throw new IllegalArgumentException("Cannot serialize a null semantic type");
        }

        JsonObject obj = new JsonObject();

        switch (type.kind) {
            case PRIMITIVE -> {
                obj.put("type", "primitive");
                obj.put("kind", type.primitive.name());
            }
            case VOID -> obj.put("type", "void");
            case DYNAMIC_ARRAY -> {
                obj.put("type", "dynamicArray");
                obj.put("elementType", typeToJson(type.elementType, registry));
            }
            case STATIC_ARRAY -> {
                obj.put("type", "staticArray");
                obj.put("elementType", typeToJson(type.elementType, registry));
                obj.put("size", type.sizeLiteral);
            }
            case PTR -> {
                obj.put("type", "ptr");
                obj.put("permission", type.permission.name());
                obj.put("referencedType", typeToJson(type.referencedType, registry));
            }
            case NULL -> obj.put("type", "null");
            case STRUCT -> {
                obj.put("type", "struct");
                obj.put("symbol", registry.idFor(type.structSymbol));
            }
            case ENUM -> {
                obj.put("type", "enum");
                obj.put("symbol", registry.idFor(type.enumSymbol));
            }
            case TUPLE -> {
                obj.put("type", "tuple");
                obj.put("symbol", registry.idFor(type.cppSymbol));

                JsonArray elements = new JsonArray();

                for (FieldSymbol field : type.fields) {
                    JsonObject element = new JsonObject();
                    element.put("name", field.name);
                    element.put("type", typeToJson(field.type, registry));
                    elements.add(element);
                }

                obj.put("elements", elements);
            }
            case FUNCTION -> {
                obj.put("type", "function");
                JsonArray parameters = new JsonArray();

                for (SastType parameter : type.parameterTypes) {
                    parameters.add(typeToJson(parameter, registry));
                }

                obj.put("parameters", parameters);
                obj.put("returnType", typeToJson(type.returnType, registry));
            }
            default -> throw new IllegalArgumentException(
                    "Unknown semantic type: " + type.kind
            );
        }

        return obj;
    }

    static SastType typeFromJson(JsonObject obj, SymbolRegistry registry) {
        String kind = obj.requireString("type");

        return switch (kind) {
            case "primitive" -> SastType.primitive(
                    SemanticPrimitiveKind.valueOf(obj.requireString("kind"))
            );
            case "void" -> SastType.voidType();
            case "dynamicArray" -> SastType.dynamicArray(
                    typeFromJson(obj.requireObject("elementType"), registry)
            );
            case "staticArray" -> SastType.staticArray(
                    typeFromJson(obj.requireObject("elementType"), registry),
                    obj.requireString("size")
            );
            case "ptr" -> {
                SastType referenced = typeFromJson(
                        obj.requireObject("referencedType"),
                        registry
                );
                PtrPermission permission = PtrPermission.valueOf(
                        obj.requireString("permission")
                );
                yield switch (permission) {
                    case OWNER -> SastType.ptr(referenced);
                    case MUTABLE -> SastType.mview(referenced);
                    case READ_ONLY -> SastType.view(referenced);
                };
            }
            case "null" -> SastType.nullType();
            case "struct" -> SastType.structType(
                    (StructSymbol) registry.require(obj.requireString("symbol"))
            );
            case "enum" -> SastType.enumType(
                    (EnumSymbol) registry.require(obj.requireString("symbol"))
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

                yield SastType.tupleType(fields, cppSymbol);
            }
            case "function" -> {
                List<SastType> parameters = new ArrayList<>();

                for (JsonValue value : obj.requireArray("parameters").values()) {
                    parameters.add(typeFromJson(
                            JsonAstSupport.requireObject(value, "function parameter type"),
                            registry
                    ));
                }

                yield SastType.functionType(
                        parameters,
                        typeFromJson(obj.requireObject("returnType"), registry)
                );
            }
            default -> throw new IllegalArgumentException(
                    "Unknown semantic type kind '" + kind + "'"
            );
        };
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
