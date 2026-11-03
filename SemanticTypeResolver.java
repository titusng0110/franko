import java.math.BigInteger;
import java.util.List;

final class SemanticTypeResolver {

    static final SemanticType FALLBACK_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.INT32);

    static final SemanticType VOID_TYPE =
            new SemanticVoidType();

    private final SymbolTable symbolTable;
    private final ExpressionAnalyzer exprAnalyzer;

    SemanticTypeResolver(
            SymbolTable symbolTable,
            ExpressionAnalyzer exprAnalyzer
    ) {
        this.symbolTable = symbolTable;
        this.exprAnalyzer = exprAnalyzer;
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
            StructSymbol structSymbol = resolveStructTypeSymbol(s);

            if (structSymbol == null) {
                return FALLBACK_TYPE;
            }

            return new SemanticStructType(structSymbol);
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

    private StructSymbol resolveStructTypeSymbol(StructTypeNode s) {
        if (s.namespaceSegments == null || s.namespaceSegments.isEmpty()) {
            StructSymbol structSymbol = symbolTable.resolveStruct(s.name);

            if (structSymbol == null) {
                symbolTable.error("Unknown struct type '" + s.name + "'");
            }

            return structSymbol;
        }

        List<String> namespaceSegments = s.namespaceSegments;
        String qualified = String.join("::", namespaceSegments)
                + "::"
                + s.name;

        NamespaceSymbol namespace =
                symbolTable.resolveNamespace(namespaceSegments);

        if (namespace == null) {
            symbolTable.error("Unknown namespace '"
                    + String.join("::", namespaceSegments)
                    + "'");
            return null;
        }

        StructSymbol structSymbol = symbolTable.resolveStructIn(namespace, s.name);

        if (structSymbol == null) {
            symbolTable.error("Unknown struct type '"
                    + qualified
                    + "'");
            return null;
        }

        return structSymbol;
    }
}
