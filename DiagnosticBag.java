import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class DiagnosticBag {

    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Deque<SourceSpan> spanStack = new ArrayDeque<>();

    private String header;

    public DiagnosticBag() {
        this("Diagnostics failed:");
    }

    public DiagnosticBag(String header) {
        this.header = normalizeHeader(header);
    }

    public void setHeader(String header) {
        this.header = normalizeHeader(header);
    }

    public String getHeader() {
        return header;
    }

    public void clear() {
        diagnostics.clear();
        spanStack.clear();
    }

    public SourceSpan push(SourceSpan span) {
        SourceSpan normalized = span == null ? SourceSpan.NONE : span;
        spanStack.push(normalized);
        return normalized;
    }

    public SourceSpan pop() {
        return spanStack.pop();
    }

    public Scope at(SourceSpan span) {
        spanStack.push(span == null ? SourceSpan.NONE : span);
        return new Scope();
    }

    public SourceSpan currentSpan() {
        SourceSpan span = spanStack.peek();
        return span == null ? SourceSpan.NONE : span;
    }

    public void error(String msg) {
        diagnostics.add(Diagnostic.error(currentSpan(), msg));
    }

    public void errorAt(SourceSpan span, String msg) {
        diagnostics.add(Diagnostic.error(span, msg));
    }

    public void warning(String msg) {
        diagnostics.add(Diagnostic.warning(currentSpan(), msg));
    }

    public void warningAt(SourceSpan span, String msg) {
        diagnostics.add(Diagnostic.warning(span, msg));
    }

    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public List<String> getWarnings() {
        List<String> out = new ArrayList<>();

        for (Diagnostic diagnostic : diagnostics) {
            if (!diagnostic.isError()) {
                out.add(diagnostic.format());
            }
        }

        return List.copyOf(out);
    }

    public boolean hasWarnings() {
        return diagnostics.stream().anyMatch(d -> !d.isError());
    }

    public List<String> getErrors() {
        List<String> out = new ArrayList<>();

        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.isError()) {
                out.add(diagnostic.format());
            }
        }

        return List.copyOf(out);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::isError);
    }

    public String formatErrors() {
        StringBuilder sb = new StringBuilder();

        sb.append(header);

        if (!header.endsWith("\n")) {
            sb.append('\n');
        }

        int index = 0;

        for (Diagnostic diagnostic : diagnostics) {
            if (!diagnostic.isError()) {
                continue;
            }

            index++;
            sb.append("  ")
                    .append(index)
                    .append(". ")
                    .append(diagnostic.format())
                    .append('\n');
        }

        return sb.toString();
    }

    public String formatErrors(String overrideHeader) {
        String oldHeader = this.header;

        try {
            setHeader(overrideHeader);
            return formatErrors();
        } finally {
            this.header = oldHeader;
        }
    }

    public final class Scope implements AutoCloseable {

        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (!closed) {
                spanStack.pop();
                closed = true;
            }
        }
    }

    private static String normalizeHeader(String header) {
        return header == null || header.isBlank()
                ? "Diagnostics failed:"
                : header;
    }
}
