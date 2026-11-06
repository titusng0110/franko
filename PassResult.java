import java.util.List;

public final class PassResult {

    public final SastDocument document;
    public final List<Diagnostic> diagnostics;

    public PassResult(SastDocument document, List<Diagnostic> diagnostics) {
        this.document = document;
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.isError()) {
                return true;
            }
        }

        return false;
    }
}
