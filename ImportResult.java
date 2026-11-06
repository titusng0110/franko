import java.util.List;
import java.util.Map;

public final class ImportResult {

    public final AstDocument document;
    public final Map<String, List<String>> importGraph;
    public final List<Diagnostic> diagnostics;

    public ImportResult(
            AstDocument document,
            Map<String, List<String>> importGraph,
            List<Diagnostic> diagnostics
    ) {
        this.document = document;
        this.importGraph = importGraph;
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
