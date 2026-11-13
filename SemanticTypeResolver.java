import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SemanticTypeResolver {

    static final SastType FALLBACK_TYPE =
            SastType.primitive(SemanticPrimitiveKind.INT32);

    static final SastType VOID_TYPE =
            SastType.voidType();

    private final SymbolTable symbolTable;
    private final ExpressionAnalyzer exprAnalyzer;

    private final Map<String, StructSymbol> tupleCppSymbols =
            new LinkedHashMap<>();

    private final Map<String, String> tupleNameKeys =
            new LinkedHashMap<>();

    private final List<SastNode> pendingTupleDecls =
            new ArrayList<>();

    SemanticTypeResolver(
            SymbolTable symbolTable,
            ExpressionAnalyzer exprAnalyzer
    ) {
        this.symbolTable = symbolTable;
        this.exprAnalyzer = exprAnalyzer;
    }

    void resetTupleTypes() {
        tupleCppSymbols.clear();
        tupleNameKeys.clear();
        pendingTupleDecls.clear();
    }

    List<SastNode> pendingTupleDecls() {
        return List.copyOf(pendingTupleDecls);
    }

    SastType analyzeNormalType(AstNode node) {
        symbolTable.diagnostics().push(node == null ? SourceSpan.NONE : node.span);
        try {
            return analyzeNormalTypeInner(node);
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private SastType analyzeNormalTypeInner(AstNode node) {
        if (node == null) {
            symbolTable.error("Missing type. Every declaration and type position must name a type so the compiler can determine storage size and allowed operations. Write an explicit type such as int32 at the missing position.");
            return FALLBACK_TYPE;
        }

        if (node.kind == AstKind.PRIMITIVE_TYPE) {
            return SastType.primitive(
                    SemanticPrimitiveKind.valueOf(node.primitive.name())
            );
        }

        if (node.kind == AstKind.STRUCT_TYPE) {
            AstNode s = node;

            if (s.genericArgs != null && !s.genericArgs.isEmpty()) {
                symbolTable.error("Generic type '"
                        + (s.names == null || s.names.isEmpty()
                                ? s.name
                                : String.join("::", s.names) + "::" + s.name)
                        + "' was not resolved to a concrete instance. Generic structs must be instantiated with concrete arguments before semantic analysis so their field layout and method signatures are known. Specify the type arguments at the use site, such as Box<int32>.");
                return FALLBACK_TYPE;
            }

            StructSymbol structSymbol = resolveStructTypeSymbolQuiet(s);

            if (structSymbol != null) {
                return SastType.structType(structSymbol);
            }

            EnumSymbol enumSymbol = resolveEnumTypeSymbolQuiet(s);

            if (enumSymbol != null) {
                return SastType.enumType(enumSymbol);
            }

            if (s.names == null || s.names.isEmpty()) {
                symbolTable.error("Unknown type '" + s.name + "'. A type name must refer to a declared struct, enum, or generic type argument visible at this point. Declare the type, bind the generic parameter, or qualify the name with the namespace that declares it.");
            } else {
                symbolTable.error("Unknown type '"
                        + String.join("::", s.names)
                        + "::"
                        + s.name
                        + "'. A type name must refer to a declared struct, enum, or generic type argument visible at this point. Declare the type, bind the generic parameter, or qualify the name with the namespace that declares it.");
            }

            return FALLBACK_TYPE;
        }

        if (node.kind == AstKind.TUPLE_TYPE) {
            AstNode tuple = node;
            if (tuple.elements.size() < 2) {
                symbolTable.error(
                        "Tuple types must have at least two elements. A single-element grouping has no tuple layout or destructuring meaning in Franko. Add another element or use the element type directly.");
            }

            List<SastType> elementTypes = new ArrayList<>();

            for (AstNode element : tuple.elements) {
                elementTypes.add(analyzeNormalType(element.type));
            }

            List<FieldSymbol> fields = new ArrayList<>();
            Set<String> effectiveNames = new HashSet<>();
            String tupleDescription = describeTupleElementTypes(elementTypes);

            for (int i = 0; i < tuple.elements.size(); i++) {
                AstNode element = tuple.elements.get(i);

                String effectiveName = element.name == null
                        ? "_" + i
                        : element.name;

                if (!effectiveNames.add(effectiveName)) {
                    symbolTable.diagnostics().push(element.span);
                    try {
                        symbolTable.error("Duplicate tuple element name '"
                                + effectiveName
                                + "' in tuple type "
                                + tupleDescription
                                + ". Tuple elements share one field namespace, so duplicate names make named access ambiguous. Rename or remove the duplicate element name.");
                    } finally {
                        symbolTable.diagnostics().pop();
                    }
                }

                fields.add(new FieldSymbol(
                        effectiveName,
                        elementTypes.get(i),
                        null,
                        i
                ));
            }

            StructSymbol cppSymbol = internTupleCppSymbol(
                    tupleStructuralKey(fields),
                    fields,
                    tuple.span
            );

            return SastType.tupleType(fields, cppSymbol);
        }

        if (node.kind == AstKind.DYNAMIC_ARRAY_TYPE) {
            return SastType.dynamicArray(
                    analyzeNormalType(node.type)
            );
        }

        if (node.kind == AstKind.STATIC_ARRAY_TYPE) {
            AstNode s = node;

            if (s.sizeExpr.kind == AstKind.INFERRED_SIZE) {
                symbolTable.error("'?' array size is only allowed in a declaration with an array-literal initializer. The compiler infers the missing length from the literal's element count during declaration analysis. Use '?' only with an array literal, or write the explicit length here.");
                return FALLBACK_TYPE;
            }

            BigInteger canonicalSize =
                    exprAnalyzer.preEvaluatedConstValue(
                            s.sizeExpr,
                            "static array size"
                    );

            return SastType.staticArray(
                    analyzeNormalType(s.type),
                    canonicalSize.toString(10)
            );
        }

        if (node.kind == AstKind.PTR_TYPE) {
            AstNode a = node;
            SastType referencedType = analyzeNormalType(a.type);

            switch (a.permission) {
                case OWNER: return SastType.ptr(referencedType);
                case MUTABLE: return SastType.mview(referencedType);
                case READ_ONLY: return SastType.view(referencedType);
            }
        }

        if (node.kind == AstKind.VOID_TYPE) {
            symbolTable.error("void is not a valid ordinary type. The void type marks the absence of a return value and has no size or storage representation. Use void only as a function return type and choose a concrete type here.");
            return FALLBACK_TYPE;
        }

        symbolTable.error("Unknown type node: "
                + node.kind.jsonName());

        return FALLBACK_TYPE;
    }

    SastType analyzeReturnType(AstNode node) {
        if (node == null) {
            symbolTable.error("Function declaration is missing a return type. Franko function signatures must state their result type so calls can be type-checked and emitted. Add '-> void' for no result or '-> T' for a result.");
            return FALLBACK_TYPE;
        }

        if (node.kind == AstKind.VOID_TYPE) {
            return VOID_TYPE;
        }

        return analyzeNormalType(node);
    }

    private static String describeTupleElementTypes(List<SastType> elementTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        for (int i = 0; i < elementTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(elementTypes.get(i).describe());
        }

        sb.append(")");
        return sb.toString();
    }

    private static String tupleStructuralKey(List<FieldSymbol> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }

            sb.append(fields.get(i).type.mangleKey());
        }

        sb.append(")");
        return sb.toString();
    }

    private StructSymbol internTupleCppSymbol(
            String structuralKey,
            List<FieldSymbol> declarationFields,
            SourceSpan span
    ) {
        StructSymbol existing = tupleCppSymbols.get(structuralKey);

        if (existing != null) {
            return existing;
        }

        String emittedName = "TUPLE_" + Mangler.hashHex(structuralKey);
        String existingKey = tupleNameKeys.get(emittedName);

        if (existingKey != null && !existingKey.equals(structuralKey)) {
            throw new IllegalStateException(
                    "Internal compiler error: emitted C++ name collision for '"
                            + emittedName
                            + "' between tuple types '"
                            + existingKey
                            + "' and '"
                            + structuralKey
                            + "' (distinct tuple signatures hash to the same suffix)"
            );
        }

        StructSymbol symbol = new StructSymbol(null, emittedName, true);

        for (int i = 0; i < declarationFields.size(); i++) {
            symbol.fields.add(new FieldSymbol(
                    "_" + i,
                    declarationFields.get(i).type,
                    null,
                    i
            ));
        }

        tupleCppSymbols.put(structuralKey, symbol);
        tupleNameKeys.put(emittedName, structuralKey);
        pendingTupleDecls.add(SastNode.structDecl(span, symbol));

        return symbol;
    }

    private StructSymbol resolveStructTypeSymbolQuiet(AstNode s) {
        if (s.names == null || s.names.isEmpty()) {
            return symbolTable.resolveStruct(s.name);
        }

        NamespaceSymbol namespace =
                symbolTable.resolveNamespace(s.names);

        if (namespace == null) {
            return null;
        }

        return symbolTable.resolveStructIn(namespace, s.name);
    }

    private EnumSymbol resolveEnumTypeSymbolQuiet(AstNode s) {
        if (s.names == null || s.names.isEmpty()) {
            return symbolTable.resolveEnum(s.name);
        }

        NamespaceSymbol namespace =
                symbolTable.resolveNamespace(s.names);

        if (namespace == null) {
            return null;
        }

        return symbolTable.resolveEnumIn(namespace, s.name);
    }

    EnumSymbol analyzeEnumDecl(AstNode node, String namespace) {
        SemanticPrimitiveKind backingKind = SemanticPrimitiveKind.INT32;

        if (node.type.kind == AstKind.PRIMITIVE_TYPE) {
            backingKind = SemanticPrimitiveKind.valueOf(node.type.primitive.name());
        } else {
            String got = describeTypeNodeForEnumError(node.type);
            symbolTable.error("Enum backing type must be one of the eight primitive integer types, got " + got + ". Enums are represented as integers at runtime, so the backing type fixes their storage size and value range. Use int8, uint8, int16, uint16, int32, uint32, int64, or uint64 as the backing type.");
        }

        String enumDisplay = namespace == null ? node.name : namespace + "::" + node.name;

        if (node.variants.isEmpty()) {
            symbolTable.error("Enum '" + enumDisplay + "' must declare at least one variant. An enum with no variants has no default value and no representable states. Declare at least one variant inside the enum body.");
            return new EnumSymbol(node.name, namespace, backingKind, List.of());
        }

        Set<String> seenNames = new HashSet<>();
        List<AstNode> uniqueVariants = new ArrayList<>();

        for (AstNode variant : node.variants) {
            if (!seenNames.add(variant.name)) {
                symbolTable.diagnostics().push(variant.span);
                try {
                    symbolTable.error("Duplicate variant '" + variant.name + "' in enum '" + enumDisplay + "'. Variant names form the enum's member namespace, so duplicates make qualified references ambiguous. Rename or remove the duplicate variant.");
                } finally {
                    symbolTable.diagnostics().pop();
                }
                continue;
            }

            uniqueVariants.add(variant);
        }

        int explicitCount = 0;

        for (AstNode variant : uniqueVariants) {
            if (variant.value != null) {
                explicitCount++;
            }
        }

        if (explicitCount > 0 && explicitCount < uniqueVariants.size()) {
            symbolTable.error("Enum '" + enumDisplay + "' must provide explicit values for every variant or for none; mixing explicit and implicit values is not allowed. Mixing the two forms makes auto-increment results depend on neighboring declarations and hides which values are intended. Add explicit '= constExpr' values to every variant or delete all explicit values and let them auto-increment from zero.");
        }

        boolean allExplicit = explicitCount == uniqueVariants.size();
        List<EnumVariant> computed = new ArrayList<>();
        Map<BigInteger, String> valueToVariant = new LinkedHashMap<>();

        if (allExplicit) {
            for (AstNode variant : uniqueVariants) {
                BigInteger value = exprAnalyzer.preEvaluatedConstValue(
                        variant.value,
                        "enum variant value"
                );

                if (!TypeChecker.fitsBigIntegerToPrimitive(value, backingKind)) {
                    symbolTable.diagnostics().push(variant.span);
                    try {
                        symbolTable.error("Enum variant '" + variant.name + "' value " + value + " does not fit in " + backingKind.name().toLowerCase() + ". Each explicit variant value must be representable in the enum's declared backing type. Choose a value within that type's range or declare a wider backing type.");
                    } finally {
                        symbolTable.diagnostics().pop();
                    }
                }

                if (valueToVariant.containsKey(value)) {
                    symbolTable.diagnostics().push(variant.span);
                    try {
                        symbolTable.error("Duplicate value '" + value + "' in enum '" + enumDisplay + "'. Variant values must be unique so the enum's underlying integer representation identifies exactly one variant. Use a different value for one of these variants.");
                    } finally {
                        symbolTable.diagnostics().pop();
                    }
                } else {
                    valueToVariant.put(value, variant.name);
                }

                computed.add(new EnumVariant(variant.name, value));
            }
        } else {
            BigInteger lastValue = null;

            for (AstNode variant : uniqueVariants) {
                BigInteger value;

                if (variant.value != null) {
                    value = exprAnalyzer.preEvaluatedConstValue(
                            variant.value,
                            "enum variant value"
                    );

                    if (!TypeChecker.fitsBigIntegerToPrimitive(value, backingKind)) {
                        symbolTable.diagnostics().push(variant.span);
                        try {
                            symbolTable.error("Enum variant '" + variant.name + "' value " + value + " does not fit in " + backingKind.name().toLowerCase() + ". Each explicit variant value must be representable in the enum's declared backing type. Choose a value within that type's range or declare a wider backing type.");
                        } finally {
                            symbolTable.diagnostics().pop();
                        }
                    }
                } else if (lastValue == null) {
                    value = BigInteger.ZERO;
                    value = TypeChecker.wrapBigIntegerToPrimitive(value, backingKind);
                } else {
                    value = TypeChecker.wrapBigIntegerToPrimitive(lastValue.add(BigInteger.ONE), backingKind);
                }

                if (valueToVariant.containsKey(value)) {
                    symbolTable.diagnostics().push(variant.span);
                    try {
                        symbolTable.error("Duplicate value '" + value + "' in enum '" + enumDisplay + "'. Variant values must be unique so the enum's underlying integer representation identifies exactly one variant. Use a different value for one of these variants.");
                    } finally {
                        symbolTable.diagnostics().pop();
                    }
                } else {
                    valueToVariant.put(value, variant.name);
                }

                computed.add(new EnumVariant(variant.name, value));
                lastValue = value;
            }
        }

        return new EnumSymbol(node.name, namespace, backingKind, computed);
    }

    private static String describeTypeNodeForEnumError(AstNode node) {
        if (node == null) {
            return "<null>";
        }

        if (node.kind == AstKind.PRIMITIVE_TYPE) {
            return node.primitive.name().toLowerCase();
        }

        if (node.kind == AstKind.VOID_TYPE) {
            return "void";
        }

        if (node.kind == AstKind.STRUCT_TYPE) {
            if (node.names == null || node.names.isEmpty()) {
                return node.name;
            }

            return String.join("::", node.names) + "::" + node.name;
        }

        if (node.kind == AstKind.DYNAMIC_ARRAY_TYPE) {
            return "[" + describeTypeNodeForEnumError(node.type) + "]";
        }

        if (node.kind == AstKind.STATIC_ARRAY_TYPE) {
            String size = "?";

            if (node.sizeExpr.kind == AstKind.INT) {
                size = node.sizeExpr.text;
            }

            return "[" + describeTypeNodeForEnumError(node.type) + ", " + size + "]";
        }

        if (node.kind == AstKind.PTR_TYPE) {
            return node.permission.spelling() + "<" + describeTypeNodeForEnumError(node.type) + ">";
        }

        if (node.kind == AstKind.TUPLE_TYPE) {
            AstNode t = node;
            StringBuilder sb = new StringBuilder();
            sb.append("(");

            for (int i = 0; i < t.elements.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                sb.append(describeTypeNodeForEnumError(t.elements.get(i).type));
            }

            sb.append(")");
            return sb.toString();
        }

        return node.kind.jsonName();
    }
}
