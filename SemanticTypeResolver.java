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

    private final List<SemanticStructDeclNode> pendingTupleDecls =
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

    List<SemanticStructDeclNode> pendingTupleDecls() {
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
            symbolTable.error("Missing type");
            return FALLBACK_TYPE;
        }

        if (node.kind == AstKind.PRIMITIVE_TYPE) {
            return SastType.primitive(
                    SemanticPrimitiveKind.valueOf(node.primitive.name())
            );
        }

        if (node.kind == AstKind.STRUCT_TYPE) {
            AstNode s = node;
            StructSymbol structSymbol = resolveStructTypeSymbolQuiet(s);

            if (structSymbol != null) {
                return SastType.structType(structSymbol);
            }

            EnumSymbol enumSymbol = resolveEnumTypeSymbolQuiet(s);

            if (enumSymbol != null) {
                return SastType.enumType(enumSymbol);
            }

            if (s.names == null || s.names.isEmpty()) {
                symbolTable.error("Unknown type '" + s.name + "'");
            } else {
                symbolTable.error("Unknown type '"
                        + String.join("::", s.names)
                        + "::"
                        + s.name
                        + "'");
            }

            return FALLBACK_TYPE;
        }

        if (node.kind == AstKind.TUPLE_TYPE) {
            AstNode tuple = node;
            if (tuple.elements.size() < 2) {
                symbolTable.error(
                        "Tuple types must have at least two elements");
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
                                + tupleDescription);
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
                symbolTable.error("'?' array size is only allowed in a declaration with an array-literal initializer");
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

            return switch (a.permission) {
                case OWNER -> SastType.ptr(referencedType);
                case MUTABLE -> SastType.mview(referencedType);
                case READ_ONLY -> SastType.view(referencedType);
            };
        }

        if (node.kind == AstKind.VOID_TYPE) {
            symbolTable.error("void is not a valid ordinary type");
            return FALLBACK_TYPE;
        }

        symbolTable.error("Unknown type node: "
                + node.kind.legacyClassName());

        return FALLBACK_TYPE;
    }

    SastType analyzeReturnType(AstNode node) {
        if (node.kind == AstKind.VOID_TYPE) {
            return VOID_TYPE;
        }

        if (node == null) {
            symbolTable.error("Function declaration is missing a return type");
            return FALLBACK_TYPE;
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
        String previousKey = tupleNameKeys.get(emittedName);

        if (previousKey != null && !previousKey.equals(structuralKey)) {
            throw new IllegalStateException(
                    "Internal compiler error: emitted C++ name collision for '"
                            + emittedName
                            + "' between tuple types '"
                            + previousKey
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
        pendingTupleDecls.add(new SemanticStructDeclNode(span, symbol));

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
            symbolTable.error("Enum backing type must be one of the eight primitive integer types, got " + got);
        }

        String enumDisplay = namespace == null ? node.name : namespace + "::" + node.name;

        if (node.variants.isEmpty()) {
            symbolTable.error("Enum '" + enumDisplay + "' must declare at least one variant");
            return new EnumSymbol(node.name, namespace, backingKind, List.of());
        }

        Set<String> seenNames = new HashSet<>();
        List<AstNode> uniqueVariants = new ArrayList<>();

        for (AstNode variant : node.variants) {
            if (!seenNames.add(variant.name)) {
                symbolTable.diagnostics().push(variant.span);
                try {
                    symbolTable.error("Duplicate variant '" + variant.name + "' in enum '" + enumDisplay + "'");
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
            symbolTable.error("Enum '" + enumDisplay + "' must provide explicit values for every variant or for none; mixing explicit and implicit values is not allowed");
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
                        symbolTable.error("Enum variant '" + variant.name + "' value " + value + " does not fit in " + backingKind.name().toLowerCase());
                    } finally {
                        symbolTable.diagnostics().pop();
                    }
                }

                if (valueToVariant.containsKey(value)) {
                    symbolTable.diagnostics().push(variant.span);
                    try {
                        symbolTable.error("Duplicate value '" + value + "' in enum '" + enumDisplay + "'");
                    } finally {
                        symbolTable.diagnostics().pop();
                    }
                } else {
                    valueToVariant.put(value, variant.name);
                }

                computed.add(new EnumVariant(variant.name, value));
            }
        } else {
            BigInteger previous = null;

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
                            symbolTable.error("Enum variant '" + variant.name + "' value " + value + " does not fit in " + backingKind.name().toLowerCase());
                        } finally {
                            symbolTable.diagnostics().pop();
                        }
                    }
                } else if (previous == null) {
                    value = BigInteger.ZERO;
                    value = TypeChecker.wrapBigIntegerToPrimitive(value, backingKind);
                } else {
                    value = TypeChecker.wrapBigIntegerToPrimitive(previous.add(BigInteger.ONE), backingKind);
                }

                if (valueToVariant.containsKey(value)) {
                    symbolTable.diagnostics().push(variant.span);
                    try {
                        symbolTable.error("Duplicate value '" + value + "' in enum '" + enumDisplay + "'");
                    } finally {
                        symbolTable.diagnostics().pop();
                    }
                } else {
                    valueToVariant.put(value, variant.name);
                }

                computed.add(new EnumVariant(variant.name, value));
                previous = value;
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

        return node.kind.legacyClassName();
    }
}
