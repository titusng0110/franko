import java.util.List;

public final class SastResult {

    public final SastDocument document;
    public final List<Diagnostic> diagnostics;

    public SastResult(SastDocument document, List<Diagnostic> diagnostics) {
        this.document = document;
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::isError);
    }
}
