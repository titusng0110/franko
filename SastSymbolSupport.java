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
                if (symbol instanceof StructSymbol) {
                    StructSymbol struct = (StructSymbol) symbol;

                    for (FieldSymbol field : struct.fields) {
                        changed |= registerNew(registry, field);
                    }

                    for (List<Symbol> overloads : struct.methods.values()) {
                        for (Symbol method : overloads) {
                            changed |= registerNew(registry, method);
                        }
                    }
                } else if (symbol instanceof Symbol) {
                    Symbol sym = (Symbol) symbol;

                    if (sym.kind == SymbolKind.METHOD) {
                        changed |= registerNew(registry, sym.owner);

                        for (Symbol parameter : sym.parameters) {
                            changed |= registerNew(registry, parameter);
                        }
                    } else if (sym.kind == SymbolKind.FUNCTION) {
                        for (Symbol parameter : sym.parameters) {
                            changed |= registerNew(registry, parameter);
                        }
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

    static JsonValue symbolsToJson(SymbolRegistry registry) {
        JsonValue array = JsonValue.array();

        for (Map.Entry<String, Object> entry : registry.entries()) {
            array.add(symbolToJson(entry.getKey(), entry.getValue(), registry));
        }

        return array;
    }

    static JsonValue symbolToJson(
            String id,
            Object symbol,
            SymbolRegistry registry
    ) {
        JsonValue obj = JsonValue.object();
        obj.put("id", id);

        if (symbol instanceof StructSymbol) {
            StructSymbol struct = (StructSymbol) symbol;
            obj.put("kind", "struct");
            obj.put("name", struct.name);
            obj.put("namespace", struct.namespace);

            JsonValue fields = JsonValue.array();

            for (FieldSymbol field : struct.fields) {
                fields.add(registry.idFor(field));
            }

            obj.put("fields", fields);

            JsonValue methods = JsonValue.object();

            for (Map.Entry<String, List<Symbol>> entry : struct.methods.entrySet()) {
                JsonValue overloads = JsonValue.array();

                for (Symbol method : entry.getValue()) {
                    overloads.add(registry.idFor(method));
                }

                methods.put(entry.getKey(), overloads);
            }

            obj.put("methods", methods);

            if (struct.isTuple) {
                obj.put("tuple", true);
            }

            if (struct.genericHash != null) {
                obj.put("genericHash", struct.genericHash);
            }

            if (struct.genericBaseName != null) {
                obj.put("genericBaseName", struct.genericBaseName);
            }
        } else if (symbol instanceof EnumSymbol) {
            EnumSymbol enumSymbol = (EnumSymbol) symbol;
            obj.put("kind", "enum");
            obj.put("name", enumSymbol.name);
            obj.put("namespace", enumSymbol.namespace);
            obj.put("backing", enumSymbol.backingKind.name());

            JsonValue variants = JsonValue.array();

            for (EnumVariant variant : enumSymbol.variants) {
                JsonValue variantObj = JsonValue.object();
                variantObj.put("name", variant.name);
                variantObj.put("value", variant.value.toString(10));
                variants.add(variantObj);
            }

            obj.put("variants", variants);
        } else if (symbol instanceof FieldSymbol) {
            FieldSymbol field = (FieldSymbol) symbol;
            obj.put("kind", "field");
            obj.put("name", field.name);
            obj.put("type", typeToJson(field.type, registry));
            obj.put("foldedDefault", stringOrNull(field.foldedDefault));

            if (field.tupleIndex >= 0) {
                obj.put("tupleIndex", field.tupleIndex);
            }
        } else if (symbol instanceof Symbol) {
            Symbol sym = (Symbol) symbol;

            if (sym.kind == SymbolKind.METHOD) {
                obj.put("kind", "method");
                obj.put("name", sym.name);
                obj.put("namespace", sym.namespace);
                obj.put("owner", registry.idFor(sym.owner));
                obj.put("parameters", parameterIds(sym.parameters, registry));
                obj.put("returnType", typeToJson(sym.returnType(), registry));
                obj.put("isBuiltin", sym.isBuiltin);

                if (sym.inlineMode != InlineMode.DEFAULT) {
                    obj.put("inlineMode", sym.inlineMode.name());
                }

                if (sym.genericGroup != null) {
                    obj.put("genericGroup", sym.genericGroup);
                }

                if (sym.genericHash != null) {
                    obj.put("genericHash", sym.genericHash);
                }
            } else if (sym.kind == SymbolKind.FUNCTION) {
                obj.put("kind", "function");
                obj.put("name", sym.name);
                obj.put("namespace", sym.namespace);
                obj.put("parameters", parameterIds(sym.parameters, registry));
                obj.put("returnType", typeToJson(sym.returnType(), registry));
                obj.put("isBuiltin", sym.isBuiltin);

                if (sym.inlineMode != InlineMode.DEFAULT) {
                    obj.put("inlineMode", sym.inlineMode.name());
                }

                if (sym.genericGroup != null) {
                    obj.put("genericGroup", sym.genericGroup);
                }

                if (sym.genericHash != null) {
                    obj.put("genericHash", sym.genericHash);
                }
            } else if (sym.kind == SymbolKind.PARAMETER) {
                obj.put("kind", "parameter");
                obj.put("name", sym.name);
                obj.put("index", sym.index);
                obj.put("type", typeToJson(sym.type, registry));

                if (sym.isConst) {
                    obj.put("const", true);
                }
            } else if (sym.kind == SymbolKind.GLOBAL) {
                obj.put("kind", "global");
                obj.put("name", sym.name);
                obj.put("namespace", sym.namespace);
                obj.put("type", typeToJson(sym.type, registry));
                obj.put("compileTimeConstantValue", stringOrNull(sym.compileTimeConstantValue));
                obj.put("literalBytes", JsonAstSupport.bigIntegerArray(sym.literalBytes));
                obj.put("literalValues", JsonAstSupport.bigIntegerArray(sym.literalValues));

                if (sym.isConst) {
                    obj.put("const", true);
                }
            } else {
                obj.put("kind", "variable");
                obj.put("name", sym.name);
                obj.put("namespace", sym.namespace);
                obj.put("type", typeToJson(sym.type, registry));

                if (sym.isConst) {
                    obj.put("const", true);
                }
            }
        } else {
            throw new IllegalArgumentException(
                    "Unknown symbol type: " + symbol.getClass().getName()
            );
        }

        return obj;
    }

    static JsonValue parameterIds(
            List<Symbol> parameters,
            SymbolRegistry registry
    ) {
        JsonValue array = JsonValue.array();

        for (Symbol parameter : parameters) {
            array.add(registry.idFor(parameter));
        }

        return array;
    }

    static JsonValue stringOrNull(Object value) {
        return value == null ? JsonValue.nullValue() : JsonValue.string(value.toString());
    }

    static SymbolRegistry symbolsFromJson(JsonValue array, SourceTable sources) {
        SymbolRegistry registry = new SymbolRegistry();
        List<String> inputOrder = new ArrayList<>();
        Map<String, JsonValue> structShells = new LinkedHashMap<>();
        Map<String, JsonValue> pendingFields = new LinkedHashMap<>();
        Map<String, JsonValue> pendingParameters = new LinkedHashMap<>();
        Map<String, JsonValue> pendingFunctions = new LinkedHashMap<>();
        Map<String, JsonValue> pendingGlobals = new LinkedHashMap<>();

        for (JsonValue value : array.values()) {
            JsonValue obj = JsonAstSupport.requireObject(value, "symbol");
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
                    struct.genericHash = obj.optString("genericHash");
                    struct.genericBaseName = obj.optString("genericBaseName");
                    registry.registerWithId(id, struct);
                    structShells.put(id, obj);
                }
                case "enum" -> {
                    SemanticPrimitiveKind backing = SemanticPrimitiveKind.valueOf(
                            obj.requireString("backing")
                    );
                    List<EnumVariant> variants = new ArrayList<>();

                    for (JsonValue variantValue : obj.requireArray("variants").values()) {
                        JsonValue variantObj = JsonAstSupport.requireObject(variantValue, "enum variant");
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

        for (Map.Entry<String, JsonValue> entry : pendingFields.entrySet()) {
            JsonValue obj = entry.getValue();
            FieldSymbol field = new FieldSymbol(
                    obj.requireString("name"),
                    typeFromJson(obj.requireObject("type"), registry),
                    bigIntegerOrNull(obj.get("foldedDefault")),
                    obj.has("tupleIndex") ? obj.requireInt("tupleIndex") : -1
            );
            registry.registerWithId(entry.getKey(), field);
        }

        for (Map.Entry<String, JsonValue> entry : pendingParameters.entrySet()) {
            JsonValue obj = entry.getValue();
            Symbol parameter = Symbol.parameter(
                    obj.requireString("name"),
                    typeFromJson(obj.requireObject("type"), registry),
                    obj.requireInt("index"),
                    obj.has("const") && obj.requireBool("const")
            );
            registry.registerWithId(entry.getKey(), parameter);
        }

        for (Map.Entry<String, JsonValue> entry : pendingGlobals.entrySet()) {
            JsonValue obj = entry.getValue();
            String kind = obj.requireString("kind");
            String name = obj.requireString("name");
            String namespace = obj.optString("namespace");
            SastType type = typeFromJson(obj.requireObject("type"), registry);

            boolean isConst = obj.has("const") && obj.requireBool("const");

            if ("global".equals(kind)) {
                Symbol global = Symbol.global(namespace, name, type, isConst);
                global.compileTimeConstantValue = bigIntegerOrNull(
                        obj.get("compileTimeConstantValue")
                );
                global.literalBytes = bigIntegersOrNull(obj.get("literalBytes"));
                global.literalValues = bigIntegersOrNull(obj.get("literalValues"));
                registry.registerWithId(entry.getKey(), global);
            } else {
                registry.registerWithId(
                        entry.getKey(),
                        Symbol.variable(namespace, name, type, isConst)
                );
            }
        }

        for (Map.Entry<String, JsonValue> entry : pendingFunctions.entrySet()) {
            JsonValue obj = entry.getValue();
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
            InlineMode inlineMode = obj.has("inlineMode")
                    ? JsonAstSupport.requireEnumName(
                            obj.requireString("inlineMode"),
                            InlineMode.class
                    )
                    : InlineMode.DEFAULT;

            if ("method".equals(kind)) {
                StructSymbol owner = (StructSymbol) registry.require(
                        obj.requireString("owner")
                );
                Symbol method = Symbol.method(
                        owner,
                        name,
                        parameters,
                        returnType,
                        isBuiltin
                );
                method.inlineMode = inlineMode;
                method.genericGroup = obj.optString("genericGroup");
                method.genericHash = obj.optString("genericHash");
                registry.registerWithId(entry.getKey(), method);
            } else {
                Symbol function = Symbol.function(
                        obj.optString("namespace"),
                        name,
                        parameters,
                        returnType,
                        isBuiltin
                );
                function.inlineMode = inlineMode;
                function.genericGroup = obj.optString("genericGroup");
                function.genericHash = obj.optString("genericHash");
                registry.registerWithId(entry.getKey(), function);
            }
        }

        for (Map.Entry<String, JsonValue> entry : structShells.entrySet()) {
            StructSymbol struct = (StructSymbol) registry.require(entry.getKey());
            JsonValue obj = entry.getValue();
            JsonValue fields = obj.requireArray("fields");

            for (JsonValue fieldValue : fields.values()) {
                String fieldId = requireId(fieldValue);
                Object field = registry.require(fieldId);

                if (!(field instanceof FieldSymbol)) {
                    throw new IllegalArgumentException(
                            "Struct field reference '" + fieldId + "' is not a field"
                    );
                }

                struct.fields.add((FieldSymbol) field);
            }

            JsonValue methods = obj.requireObject("methods");

            for (Map.Entry<String, JsonValue> methodEntry : methods.entries()) {
                JsonValue overloads = requireArray(methodEntry.getValue(), "method overloads");
                List<Symbol> symbols = new ArrayList<>();

                for (JsonValue methodValue : overloads.values()) {
                    String methodId = requireId(methodValue);
                    Object method = registry.require(methodId);

                    if (!(method instanceof Symbol)) {
                        throw new IllegalArgumentException(
                                "Method reference '" + methodId + "' is not a method"
                        );
                    }

                    Symbol methodSymbol = (Symbol) method;

                    if (methodSymbol.kind != SymbolKind.METHOD) {
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
            JsonValue array,
            SymbolRegistry registry
    ) {
        List<Symbol> parameters = new ArrayList<>();

        for (JsonValue value : array.values()) {
            Object symbol = registry.require(requireId(value));

            if (!(symbol instanceof Symbol)) {
                throw new IllegalArgumentException("Expected a parameter symbol");
            }

            Symbol parameter = (Symbol) symbol;

            if (parameter.kind != SymbolKind.PARAMETER) {
                throw new IllegalArgumentException("Expected a parameter symbol");
            }

            parameters.add(parameter);
        }

        return parameters;
    }

    static BigInteger bigIntegerOrNull(JsonValue value) {
        if (value == null || value.isNull()) {
            return null;
        }

        if (!value.isString()) {
            throw new IllegalArgumentException("Expected a decimal string");
        }

        return new BigInteger(value.value());
    }

    static List<BigInteger> bigIntegersOrNull(JsonValue value) {
        if (value == null || value.isNull()) {
            return null;
        }

        if (!value.isArray()) {
            throw new IllegalArgumentException("Expected an array of decimal strings");
        }

        List<BigInteger> values = new ArrayList<>();

        for (JsonValue element : value.values()) {
            values.add(bigIntegerOrNull(element));
        }

        return values;
    }

    static JsonValue typeToJson(SastType type, SymbolRegistry registry) {
        if (type == null) {
            throw new IllegalArgumentException("Cannot serialize a null semantic type");
        }

        JsonValue obj = JsonValue.object();

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

                JsonValue elements = JsonValue.array();

                for (FieldSymbol field : type.fields) {
                    JsonValue element = JsonValue.object();
                    element.put("name", field.name);
                    element.put("type", typeToJson(field.type, registry));
                    elements.add(element);
                }

                obj.put("elements", elements);
            }
            case FUNCTION -> {
                obj.put("type", "function");
                JsonValue parameters = JsonValue.array();

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

    static SastType typeFromJson(JsonValue obj, SymbolRegistry registry) {
        String kind = obj.requireString("type");

        switch (kind) {
            case "primitive": return SastType.primitive(
                    SemanticPrimitiveKind.valueOf(obj.requireString("kind"))
            );
            case "void": return SastType.voidType();
            case "dynamicArray": return SastType.dynamicArray(
                    typeFromJson(obj.requireObject("elementType"), registry)
            );
            case "staticArray": return SastType.staticArray(
                    typeFromJson(obj.requireObject("elementType"), registry),
                    obj.requireString("size")
            );
            case "ptr": {
                SastType referenced = typeFromJson(
                        obj.requireObject("referencedType"),
                        registry
                );
                PtrPermission permission = PtrPermission.valueOf(
                        obj.requireString("permission")
                );
                return switch (permission) {
                    case OWNER -> SastType.ptr(referenced);
                    case MUTABLE -> SastType.mview(referenced);
                    case READ_ONLY -> SastType.view(referenced);
                };
            }
            case "null": return SastType.nullType();
            case "struct": return SastType.structType(
                    (StructSymbol) registry.require(obj.requireString("symbol"))
            );
            case "enum": return SastType.enumType(
                    (EnumSymbol) registry.require(obj.requireString("symbol"))
            );
            case "tuple": {
                StructSymbol cppSymbol = (StructSymbol) registry.require(
                        obj.requireString("symbol")
                );
                List<FieldSymbol> fields = new ArrayList<>();
                int index = 0;

                for (JsonValue value : obj.requireArray("elements").values()) {
                    JsonValue element = JsonAstSupport.requireObject(
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

                return SastType.tupleType(fields, cppSymbol);
            }
            case "function": {
                List<SastType> parameters = new ArrayList<>();

                for (JsonValue value : obj.requireArray("parameters").values()) {
                    parameters.add(typeFromJson(
                            JsonAstSupport.requireObject(value, "function parameter type"),
                            registry
                    ));
                }

                return SastType.functionType(
                        parameters,
                        typeFromJson(obj.requireObject("returnType"), registry)
                );
            }
            default: throw new IllegalArgumentException(
                    "Unknown semantic type kind '" + kind + "'"
            );
        }
    }

    private static String requireId(JsonValue value) {
        if (!value.isString()) {
            throw new IllegalArgumentException("Expected a symbol ID string");
        }

        return value.value();
    }

    private static JsonValue requireArray(JsonValue value, String description) {
        if (!value.isArray()) {
            throw new IllegalArgumentException("Expected " + description + " to be an array");
        }

        return value;
    }
}
