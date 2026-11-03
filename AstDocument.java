public final class AstDocument {

    public final AstPhase phase;
    public final SourceTable sources;
    public final ProgramNode root;

    public AstDocument(AstPhase phase, SourceTable sources, ProgramNode root) {
        this.phase = phase;
        this.sources = sources == null ? new SourceTable() : sources;
        this.root = root;
    }

    public AstDocument withPhase(AstPhase newPhase) {
        return new AstDocument(newPhase, sources, root);
    }
}

enum AstPhase {
    RAW("raw"),
    DESUGARED("desugared"),
    IMPORT_RESOLVED("import-resolved");

    private final String jsonName;

    AstPhase(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }

    public String artifactName() {
        return this == IMPORT_RESOLVED ? "imports" : jsonName;
    }
}
