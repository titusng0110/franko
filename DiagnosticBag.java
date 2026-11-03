import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class DiagnosticBag {

    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Deque<SourceSpan> spanStack = new ArrayDeque<>();

    public DiagnosticBag() {
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

    public SourceSpan currentSpan() {
        SourceSpan span = spanStack.peek();
        return span == null ? SourceSpan.NONE : span;
    }

    public void error(String msg) {
        diagnostics.add(Diagnostic.error(currentSpan(), msg));
    }

    public void warning(String msg) {
        diagnostics.add(Diagnostic.warning(currentSpan(), msg));
    }

    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public static String formatErrors(
            String header,
            List<Diagnostic> diagnostics
    ) {
        StringBuilder sb = new StringBuilder(header);

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

}
