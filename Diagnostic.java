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

    public enum Origin {
        COMPILER("compiler"),
        USER_PANIC("user-panic"),
        USER_WARNING("user-warning");

        private final String label;

        Origin(String label) {
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
    public final Origin origin;

    public Diagnostic(Severity severity, String message, SourceSpan span) {
        this(severity, message, span, null, Origin.COMPILER);
    }

    public Diagnostic(
            Severity severity,
            String message,
            SourceSpan span,
            String code
    ) {
        this(severity, message, span, code, Origin.COMPILER);
    }

    public Diagnostic(
            Severity severity,
            String message,
            SourceSpan span,
            String code,
            Origin origin
    ) {
        this.severity = Objects.requireNonNull(severity);
        this.message = message == null ? "<null diagnostic>" : message;
        this.span = span == null ? SourceSpan.NONE : span;
        this.code = code;
        this.origin = Objects.requireNonNull(origin);
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

    public Origin origin() {
        return origin;
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

    public static Diagnostic panic(SourceSpan span, String message) {
        return new Diagnostic(
                Severity.ERROR,
                message,
                span,
                null,
                Origin.USER_PANIC
        );
    }

    public static Diagnostic userWarning(SourceSpan span, String message) {
        return new Diagnostic(
                Severity.WARNING,
                message,
                span,
                null,
                Origin.USER_WARNING
        );
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isUserPanic() {
        return origin == Origin.USER_PANIC;
    }

    public boolean isUserWarning() {
        return origin == Origin.USER_WARNING;
    }

    public boolean isCompilerError() {
        return isError() && !isUserPanic();
    }

    public boolean isCompilerWarning() {
        return !isError() && !isUserWarning();
    }

    public String format() {
        return span.isPresent() ? span.format() + ": " + message : message;
    }
}
