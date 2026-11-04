final class ExpressionLowering {

    final ExpressionAnalyzer analyzer;
    final SemanticTypeResolver typeResolver;

    ExpressionLowering(
            SymbolTable symbolTable,
            ConstExpressionEvaluator constEval,
            ArrayLiteralLowerer.Hoister hoister,
            CompileTimeIntrinsics.StringHoister staticStringHoister,
            TargetInfo target
    ) {
        this.analyzer = new ExpressionAnalyzer(symbolTable, constEval);
        this.typeResolver = new SemanticTypeResolver(symbolTable, analyzer);
        ArrayLowerer arrayLowerer = new ArrayLowerer(symbolTable, analyzer);
        ArrayLiteralLowerer arrayLiteralLowerer = new ArrayLiteralLowerer(
                symbolTable,
                analyzer,
                hoister
        );
        CompileTimeIntrinsics staticIntrinsics = new CompileTimeIntrinsics(
                symbolTable,
                target,
                staticStringHoister,
                typeResolver::analyzeNormalType
        );

        analyzer.wire(
                arrayLowerer,
                typeResolver::analyzeNormalType,
                arrayLiteralLowerer,
                staticIntrinsics
        );
    }
}
