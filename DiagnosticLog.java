import java.util.ArrayList;
import java.util.List;

public final class DiagnosticLog {

    public static final class Entry {
        public final String phase;
        public final Diagnostic diagnostic;

        Entry(String phase, Diagnostic diagnostic) {
            this.phase = phase;
            this.diagnostic = diagnostic;
        }

        public String phase() {
            return phase;
        }

        public Diagnostic diagnostic() {
            return diagnostic;
        }
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
        List<Diagnostic> out = new ArrayList<>();

        for (Entry entry : entries) {
            out.add(entry.diagnostic());
        }

        return out;
    }

    public List<Diagnostic> errors() {
        List<Diagnostic> out = new ArrayList<>();

        for (Entry entry : entries) {
            if (entry.diagnostic().isError()) {
                out.add(entry.diagnostic());
            }
        }

        return out;
    }

    public List<Diagnostic> warnings() {
        List<Diagnostic> out = new ArrayList<>();

        for (Entry entry : entries) {
            if (!entry.diagnostic().isError()) {
                out.add(entry.diagnostic());
            }
        }

        return out;
    }

    public boolean hasErrors() {
        for (Entry entry : entries) {
            if (entry.diagnostic().isError()) {
                return true;
            }
        }

        return false;
    }

    public void clear() {
        entries.clear();
    }
}
