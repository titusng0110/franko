import java.util.List;

public final class PreGenericsDesugarer extends Desugarer {

    public PreGenericsDesugarer(DiagnosticBag diagnostics) {
        super(diagnostics);
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
