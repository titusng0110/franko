public record SourceSpan(
        String source,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn
) {
    public static final SourceSpan NONE = new SourceSpan("", 0, 0, 0, 0);

    public SourceSpan {
        source = source == null ? "" : source;
    }

    public boolean isPresent() {
        return startLine > 0 && startColumn > 0 && !source.isEmpty();
    }

    public String format() {
        return source + ":" + startLine + ":" + startColumn;
    }
}
