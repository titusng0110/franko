import java.util.ArrayList;
import java.util.List;

public final class DiagnosticLog {

    public record Entry(String phase, Diagnostic diagnostic) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public void add(String phase, DiagnosticBag bag) {
        add(phase, bag.diagnostics());
    }

    public void add(String phase, List<Diagnostic> diagnostics) {
        for (Diagnostic diagnostic : diagnostics) {
            entries.add(new Entry(phase, diagnostic));
        }
    }

    public void add(String phase, Diagnostic diagnostic) {
        entries.add(new Entry(phase, diagnostic));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public List<Diagnostic> diagnostics() {
        return entries.stream().map(Entry::diagnostic).toList();
    }

    public List<Diagnostic> errors() {
        return diagnostics().stream().filter(Diagnostic::isError).toList();
    }

    public List<Diagnostic> warnings() {
        return diagnostics().stream().filter(d -> !d.isError()).toList();
    }

    public boolean hasErrors() {
        return entries.stream().anyMatch(e -> e.diagnostic().isError());
    }

    public void clear() {
        entries.clear();
    }
}
