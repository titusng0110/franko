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

    public List<Diagnostic> compilerErrors() {
        List<Diagnostic> out = new ArrayList<>();

        for (Entry entry : entries) {
            if (entry.diagnostic().isCompilerError()) {
                out.add(entry.diagnostic());
            }
        }

        return out;
    }

    public List<Diagnostic> userPanics() {
        List<Diagnostic> out = new ArrayList<>();

        for (Entry entry : entries) {
            if (entry.diagnostic().isUserPanic()) {
                out.add(entry.diagnostic());
            }
        }

        return out;
    }

    public List<Diagnostic> compilerWarnings() {
        List<Diagnostic> out = new ArrayList<>();

        for (Entry entry : entries) {
            if (entry.diagnostic().isCompilerWarning()) {
                out.add(entry.diagnostic());
            }
        }

        return out;
    }

    public List<Diagnostic> userWarnings() {
        List<Diagnostic> out = new ArrayList<>();

        for (Entry entry : entries) {
            if (entry.diagnostic().isUserWarning()) {
                out.add(entry.diagnostic());
            }
        }

        return out;
    }

    public String formatReport() {
        StringBuilder sb = new StringBuilder();

        appendSection(sb, "======== Franko Compiler Errors ========", compilerErrors());
        appendSection(sb, "======== User-defined Compiler Panics ========", userPanics());
        appendSection(sb, "======== Franko Compiler Warnings ========", compilerWarnings());
        appendSection(sb, "======== User-defined Compiler Warnings ========", userWarnings());

        return sb.toString();
    }

    private static void appendSection(
            StringBuilder sb,
            String heading,
            List<Diagnostic> diagnostics
    ) {
        if (diagnostics.isEmpty()) {
            return;
        }

        if (sb.length() > 0) {
            sb.append('\n');
        }

        sb.append(heading).append('\n');

        int index = 0;

        for (Diagnostic diagnostic : diagnostics) {
            index++;
            sb.append(index)
                    .append(". ")
                    .append(diagnostic.format())
                    .append('\n');
        }
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
