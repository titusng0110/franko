import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class DeferredDirectives {

    enum Kind {
        PANIC,
        WARNING
    }

    static final class Entry {
        final Kind kind;
        final String message;
        final SourceSpan span;

        Entry(Kind kind, String message, SourceSpan span) {
            this.kind = kind;
            this.message = message;
            this.span = span == null ? SourceSpan.NONE : span;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    void panic(String message, SourceSpan span) {
        entries.add(new Entry(Kind.PANIC, message, span));
    }

    void warning(String message, SourceSpan span) {
        entries.add(new Entry(Kind.WARNING, message, span));
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    void flush(DiagnosticBag diagnostics) {
        List<Entry> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator
                .comparing((Entry entry) -> entry.span.source())
                .thenComparingInt(entry -> entry.span.startLine())
                .thenComparingInt(entry -> entry.span.startColumn()));

        for (Entry entry : ordered) {
            diagnostics.push(entry.span);
            try {
                if (entry.kind == Kind.PANIC) {
                    diagnostics.panic(entry.message);
                } else {
                    diagnostics.userWarning(entry.message);
                }
            } finally {
                diagnostics.pop();
            }
        }

        entries.clear();
    }
}
