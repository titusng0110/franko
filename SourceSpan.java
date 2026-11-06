public final class SourceSpan {

    public static final SourceSpan NONE = new SourceSpan("", 0, 0, 0, 0);

    public final String source;
    public final int startLine;
    public final int startColumn;
    public final int endLine;
    public final int endColumn;

    public SourceSpan(
            String source,
            int startLine,
            int startColumn,
            int endLine,
            int endColumn
    ) {
        this.source = source == null ? "" : source;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }

    public String source() {
        return source;
    }

    public int startLine() {
        return startLine;
    }

    public int startColumn() {
        return startColumn;
    }

    public int endLine() {
        return endLine;
    }

    public int endColumn() {
        return endColumn;
    }

    public boolean isPresent() {
        return startLine > 0 && startColumn > 0 && !source.isEmpty();
    }

    public String format() {
        return source + ":" + startLine + ":" + startColumn;
    }
}
