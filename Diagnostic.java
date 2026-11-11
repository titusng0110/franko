import java.util.Objects;

public final class Diagnostic {

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

    public final Severity severity;
    public final String message;
    public final SourceSpan span;
    public final String code;

    public Diagnostic(Severity severity, String message, SourceSpan span) {
        this(severity, message, span, null);
    }

    public Diagnostic(
            Severity severity,
            String message,
            SourceSpan span,
            String code
    ) {
        this.severity = Objects.requireNonNull(severity);
        this.message = message == null ? "<null diagnostic>" : message;
        this.span = span == null ? SourceSpan.NONE : span;
        this.code = code;
    }

    public Severity severity() {
        return severity;
    }

    public String message() {
        return message;
    }

    public SourceSpan span() {
        return span;
    }

    public static Diagnostic error(String message) {
        return new Diagnostic(Severity.ERROR, message, SourceSpan.NONE);
    }

    public static Diagnostic error(String message, String code) {
        return new Diagnostic(
                Severity.ERROR,
                message,
                SourceSpan.NONE,
                code
        );
    }

    public static Diagnostic error(SourceSpan span, String message) {
        return new Diagnostic(Severity.ERROR, message, span);
    }

    public static Diagnostic error(
            SourceSpan span,
            String message,
            String code
    ) {
        return new Diagnostic(Severity.ERROR, message, span, code);
    }

    public static Diagnostic warning(String message) {
        return new Diagnostic(Severity.WARNING, message, SourceSpan.NONE);
    }

    public static Diagnostic warning(String message, String code) {
        return new Diagnostic(
                Severity.WARNING,
                message,
                SourceSpan.NONE,
                code
        );
    }

    public static Diagnostic warning(SourceSpan span, String message) {
        return new Diagnostic(Severity.WARNING, message, span);
    }

    public static Diagnostic warning(
            SourceSpan span,
            String message,
            String code
    ) {
        return new Diagnostic(Severity.WARNING, message, span, code);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public String format() {
        return span.isPresent() ? span.format() + ": " + message : message;
    }
}
