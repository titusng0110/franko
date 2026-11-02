import java.util.List;

public final class Result<T> {

    public final T value;
    public final List<Diagnostic> diagnostics;

    public Result(T value, List<Diagnostic> diagnostics) {
        this.value = value;
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::isError);
    }
}
