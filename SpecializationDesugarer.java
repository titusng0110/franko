public final class SpecializationDesugarer extends Desugarer {

    public SpecializationDesugarer(DiagnosticBag diagnostics) {
        super(diagnostics);
    }

    AstNode desugarSpecialization(AstNode item) {
        return desugarSingleTopLevelItem(item);
    }

    AstNode desugarSpecializationMethod(AstNode method) {
        return super.desugarSpecializationMethod(method);
    }
}
