import java.math.BigInteger;
import java.util.List;

final class ArrayLiteralHoister {

    private final SemanticAnalyzer analyzer;

    ArrayLiteralHoister(SemanticAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    ArrayLiteralHoister() {
        this.analyzer = null;
    }

    SastNode hoistArrayLiteral(
            SastType arrayType,
            List<BigInteger> values,
            SourceSpan span
    ) {
        if (analyzer != null) {
            return analyzer.hoistArrayLiteralGlobal(arrayType, values, span);
        }

        Symbol symbol = Symbol.global(null, "_CT_ARRAY", arrayType);
        symbol.literalValues = List.copyOf(values);
        return SastNode.varExpr(span, arrayType, symbol);
    }
}
