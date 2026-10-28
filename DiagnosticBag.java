import java.util.ArrayList;
import java.util.List;

public final class DiagnosticBag {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private String header;

    public DiagnosticBag() {
        this("Diagnostics failed:");
    }

    public DiagnosticBag(String header) {
        this.header = header == null || header.isBlank()
                ? "Diagnostics failed:"
                : header;
    }

    public void setHeader(String header) {
        this.header = header == null || header.isBlank()
                ? "Diagnostics failed:"
                : header;
    }

    public String getHeader() {
        return header;
    }

    public void clear() {
        errors.clear();
        warnings.clear();
    }

    public void error(String msg) {
        errors.add(msg == null ? "<null diagnostic>" : msg);
    }

    public void warning(String msg) {
        warnings.add(msg == null ? "<null warning>" : msg);
    }

    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public List<String> getErrors() {
        return List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String formatErrors() {
        StringBuilder sb = new StringBuilder();

        sb.append(header);

        if (!header.endsWith("\n")) {
            sb.append('\n');
        }

        for (int i = 0; i < errors.size(); i++) {
            sb.append("  ")
                    .append(i + 1)
                    .append(". ")
                    .append(errors.get(i))
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
}
