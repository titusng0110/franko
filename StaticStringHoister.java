import java.math.BigInteger;
import java.util.List;

final class StaticStringHoister {

    private final SemanticAnalyzer analyzer;

    StaticStringHoister(SemanticAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    StaticStringHoister() {
        this.analyzer = null;
    }

    Symbol hoistStaticString(
            List<BigInteger> bytes,
            SourceSpan span
    ) {
        if (analyzer != null) {
            return analyzer.hoistStaticStringGlobal(bytes, span);
        }

        SastType arrayType = SastType.staticArray(
                SastType.primitive(SemanticPrimitiveKind.UINT8),
                Integer.toString(bytes.size())
        );
        Symbol symbol = Symbol.global(null, "_CT_STR", arrayType);
        symbol.literalBytes = List.copyOf(bytes);
        return symbol;
    }
}
