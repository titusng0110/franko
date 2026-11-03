final class ExpressionLowering {

    final ExpressionAnalyzer analyzer;
    final SemanticTypeResolver typeResolver;

    ExpressionLowering(
            SymbolTable symbolTable,
            ConstExpressionEvaluator constEval,
            ArrayLiteralLowerer.Hoister hoister
    ) {
        this.analyzer = new ExpressionAnalyzer(symbolTable, constEval);
        this.typeResolver = new SemanticTypeResolver(symbolTable, analyzer);
        ArrayLowerer arrayLowerer = new ArrayLowerer(symbolTable, analyzer);
        ArrayLiteralLowerer arrayLiteralLowerer = new ArrayLiteralLowerer(
                symbolTable,
                analyzer,
                hoister
        );

        analyzer.wire(
                arrayLowerer,
                typeResolver::analyzeNormalType,
                arrayLiteralLowerer
        );
    }
}
