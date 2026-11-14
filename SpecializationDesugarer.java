import java.util.Map;

public final class SpecializationDesugarer extends Desugarer {

    public SpecializationDesugarer(
            DiagnosticBag diagnostics,
            Map<String, AstNode> hoistedStringGlobals
    ) {
        super(diagnostics, hoistedStringGlobals);
    }

    AstNode desugarSpecialization(AstNode item) {
        return desugarSingleTopLevelItem(item);
    }

    AstNode desugarSpecializationMethod(AstNode method) {
        return super.desugarSpecializationMethod(method);
    }
}
