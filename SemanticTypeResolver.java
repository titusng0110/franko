import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SemanticTypeResolver {

    static final SemanticType FALLBACK_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.INT32);

    static final SemanticType VOID_TYPE =
            new SemanticVoidType();

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

    SemanticType analyzeNormalType(TypeNode node) {
        symbolTable.diagnostics().push(node == null ? SourceSpan.NONE : node.span);
        try {
            return analyzeNormalTypeInner(node);
        } finally {
            symbolTable.diagnostics().pop();
        }
    }

    private SemanticType analyzeNormalTypeInner(TypeNode node) {
        if (node == null) {
            symbolTable.error("Missing type");
            return FALLBACK_TYPE;
        }

        if (node instanceof PrimitiveTypeNode p) {
            return new SemanticPrimitiveType(
                    SemanticPrimitiveKind.valueOf(p.kind.name())
            );
        }

        if (node instanceof StructTypeNode s) {
            StructSymbol structSymbol = resolveStructTypeSymbolQuiet(s);

            if (structSymbol != null) {
                return new SemanticStructType(structSymbol);
            }

            EnumSymbol enumSymbol = resolveEnumTypeSymbolQuiet(s);

            if (enumSymbol != null) {
                return new SemanticEnumType(enumSymbol);
            }

            if (s.namespaceSegments == null || s.namespaceSegments.isEmpty()) {
                symbolTable.error("Unknown type '" + s.name + "'");
            } else {
                symbolTable.error("Unknown type '"
                        + String.join("::", s.namespaceSegments)
                        + "::"
                        + s.name
                        + "'");
            }

            return FALLBACK_TYPE;
        }

        if (node instanceof TupleTypeNode tuple) {
            if (tuple.elements.size() < 2) {
                symbolTable.error(
                        "Tuple types must have at least two elements");
            }

            List<SemanticType> elementTypes = new ArrayList<>();

            for (TupleTypeElementNode element : tuple.elements) {
                elementTypes.add(analyzeNormalType(element.type));
            }

            List<FieldSymbol> fields = new ArrayList<>();
            Set<String> effectiveNames = new HashSet<>();
            String tupleDescription = describeTupleElementTypes(elementTypes);

            for (int i = 0; i < tuple.elements.size(); i++) {
                TupleTypeElementNode element = tuple.elements.get(i);

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

            return new SemanticTupleType(fields, cppSymbol);
        }

        if (node instanceof DynamicArrayTypeNode d) {
            return new SemanticDynamicArrayType(
                    analyzeNormalType(d.elementType)
            );
        }

        if (node instanceof StaticArrayTypeNode s) {

            if (s.sizeExpr instanceof InferredSizeNode) {
                symbolTable.error("'?' array size is only allowed in a declaration with an array-literal initializer");
                return FALLBACK_TYPE;
            }

            BigInteger canonicalSize =
                    exprAnalyzer.preEvaluatedConstValue(
                            s.sizeExpr,
                            "static array size"
                    );

            return new SemanticStaticArrayType(
                    analyzeNormalType(s.elementType),
                    canonicalSize.toString(10)
            );
        }

        if (node instanceof PtrTypeNode a) {
            SemanticType referencedType = analyzeNormalType(a.referencedType);

            return switch (a.kind) {
                case OWNER -> new SemanticPtrType(referencedType);
                case MUTABLE -> new SemanticMViewType(referencedType);
                case READ_ONLY -> new SemanticViewType(referencedType);
            };
        }

        if (node instanceof VoidTypeNode) {
            symbolTable.error("void is not a valid ordinary type");
            return FALLBACK_TYPE;
        }

        symbolTable.error("Unknown type node: "
                + node.getClass().getSimpleName());

        return FALLBACK_TYPE;
    }

    SemanticType analyzeReturnType(TypeNode node) {
        if (node instanceof VoidTypeNode) {
            return VOID_TYPE;
        }

        if (node == null) {
            symbolTable.error("Function declaration is missing a return type");
            return FALLBACK_TYPE;
        }

        return analyzeNormalType(node);
    }

    private static String describeTupleElementTypes(List<SemanticType> elementTypes) {
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

    private StructSymbol resolveStructTypeSymbolQuiet(StructTypeNode s) {
        if (s.namespaceSegments == null || s.namespaceSegments.isEmpty()) {
            return symbolTable.resolveStruct(s.name);
        }

        NamespaceSymbol namespace =
                symbolTable.resolveNamespace(s.namespaceSegments);

        if (namespace == null) {
            return null;
        }

        return symbolTable.resolveStructIn(namespace, s.name);
    }

    private EnumSymbol resolveEnumTypeSymbolQuiet(StructTypeNode s) {
        if (s.namespaceSegments == null || s.namespaceSegments.isEmpty()) {
            return symbolTable.resolveEnum(s.name);
        }

        NamespaceSymbol namespace =
                symbolTable.resolveNamespace(s.namespaceSegments);

        if (namespace == null) {
            return null;
        }

        return symbolTable.resolveEnumIn(namespace, s.name);
    }

    EnumSymbol analyzeEnumDecl(EnumDeclNode node, String namespace) {
        SemanticPrimitiveKind backingKind = SemanticPrimitiveKind.INT32;

        if (node.backingType instanceof PrimitiveTypeNode p) {
            backingKind = SemanticPrimitiveKind.valueOf(p.kind.name());
        } else {
            String got = describeTypeNodeForEnumError(node.backingType);
            symbolTable.error("Enum backing type must be one of the eight primitive integer types, got " + got);
        }

        String enumDisplay = namespace == null ? node.name : namespace + "::" + node.name;

        if (node.variants.isEmpty()) {
            symbolTable.error("Enum '" + enumDisplay + "' must declare at least one variant");
            return new EnumSymbol(node.name, namespace, backingKind, List.of());
        }

        Set<String> seenNames = new HashSet<>();
        List<EnumVariantNode> uniqueVariants = new ArrayList<>();

        for (EnumVariantNode variant : node.variants) {
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

        for (EnumVariantNode variant : uniqueVariants) {
            if (variant.constInitializer != null) {
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
            for (EnumVariantNode variant : uniqueVariants) {
                BigInteger value = exprAnalyzer.preEvaluatedConstValue(
                        variant.constInitializer,
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

            for (EnumVariantNode variant : uniqueVariants) {
                BigInteger value;

                if (variant.constInitializer != null) {
                    value = exprAnalyzer.preEvaluatedConstValue(
                            variant.constInitializer,
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

    private static String describeTypeNodeForEnumError(TypeNode node) {
        if (node == null) {
            return "<null>";
        }

        if (node instanceof PrimitiveTypeNode p) {
            return p.kind.name().toLowerCase();
        }

        if (node instanceof VoidTypeNode) {
            return "void";
        }

        if (node instanceof StructTypeNode s) {
            if (s.namespaceSegments == null || s.namespaceSegments.isEmpty()) {
                return s.name;
            }

            return String.join("::", s.namespaceSegments) + "::" + s.name;
        }

        if (node instanceof DynamicArrayTypeNode d) {
            return "[" + describeTypeNodeForEnumError(d.elementType) + "]";
        }

        if (node instanceof StaticArrayTypeNode s) {
            String size = "?";

            if (s.sizeExpr instanceof IntNode n) {
                size = n.value;
            }

            return "[" + describeTypeNodeForEnumError(s.elementType) + ", " + size + "]";
        }

        if (node instanceof PtrTypeNode a) {
            return a.kind.spelling() + "<" + describeTypeNodeForEnumError(a.referencedType) + ">";
        }

        if (node instanceof TupleTypeNode t) {
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

        return node.getClass().getSimpleName();
    }
}
