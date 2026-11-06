final class ExpressionLowering {

    final ExpressionAnalyzer analyzer;
    final SemanticTypeResolver typeResolver;

    ExpressionLowering(
            SymbolTable symbolTable,
            ConstExpressionEvaluator constEval,
            ArrayLiteralHoister hoister,
            StaticStringHoister staticStringHoister,
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
                typeResolver
        );

        analyzer.wire(
                arrayLowerer,
                typeResolver,
                arrayLiteralLowerer,
                staticIntrinsics
        );
    }
}
