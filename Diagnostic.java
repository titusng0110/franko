import java.util.Objects;

public record Diagnostic(Severity severity, String message, SourceSpan span) {

    public enum Severity {
        ERROR("error"),
        WARNING("warning");

        private final String label;

        Severity(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public Diagnostic {
        severity = Objects.requireNonNull(severity);
        message = message == null ? "<null diagnostic>" : message;
        span = span == null ? SourceSpan.NONE : span;
    }

    public static Diagnostic error(String message) {
        return new Diagnostic(Severity.ERROR, message, SourceSpan.NONE);
    }

    public static Diagnostic error(SourceSpan span, String message) {
        return new Diagnostic(Severity.ERROR, message, span);
    }

    public static Diagnostic warning(String message) {
        return new Diagnostic(Severity.WARNING, message, SourceSpan.NONE);
    }

    public static Diagnostic warning(SourceSpan span, String message) {
        return new Diagnostic(Severity.WARNING, message, span);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public String format() {
        return span.isPresent() ? span.format() + ": " + message : message;
    }
}
