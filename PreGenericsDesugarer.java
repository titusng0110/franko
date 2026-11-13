import java.util.List;
import java.util.Map;

public final class PreGenericsDesugarer extends Desugarer {

    public PreGenericsDesugarer(DiagnosticBag diagnostics) {
        super(diagnostics);
    }

    public PreGenericsDesugarer(
            DiagnosticBag diagnostics,
            Map<String, AstNode> hoistedStringGlobals
    ) {
        super(diagnostics, hoistedStringGlobals);
    }

    @Override
    protected void appendDesugaredTopLevelItem(AstNode item, List<AstNode> out) {
        if (item != null && item.isGeneric) {
            out.add(item);
            return;
        }

        super.appendDesugaredTopLevelItem(item, out);
    }
}
