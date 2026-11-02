public final class SastDocument {

    public final SastPhase phase;
    public final SemanticAnalyzer.EntryKind entryKind;
    public final SourceTable sources;
    public final SymbolRegistry symbols;
    public final SemanticProgramNode root;

    public SastDocument(
            SastPhase phase,
            SemanticAnalyzer.EntryKind entryKind,
            SourceTable sources,
            SymbolRegistry symbols,
            SemanticProgramNode root
    ) {
        this.phase = phase;
        this.entryKind = entryKind == null ? SemanticAnalyzer.EntryKind.NONE : entryKind;
        this.sources = sources == null ? new SourceTable() : sources;
        this.symbols = symbols == null ? new SymbolRegistry() : symbols;
        this.root = root;
    }

    public SastDocument withPhase(SastPhase newPhase) {
        return new SastDocument(newPhase, entryKind, sources, symbols, root);
    }

    public String toJson() {
        return SemanticASTSerializer.toJson(this);
    }

    public static SastDocument fromJson(String json) {
        return SemanticASTSerializer.fromJson(json);
    }
}

enum SastPhase {
    SEMANTIC("semantic", "semantic"),
    LEGALITY("legality", "legality"),
    BRANCH_PRUNED("branch-pruned", "branch-pruned"),
    INACTIVE_PRUNED("inactive-pruned", "inactive-pruned"),
    NULL_STATE("null-state", "null-state"),
    DANGLING("dangling", "dangling"),
    DYN_ARRAY("dyn-array", "dyn-array"),
    PERMISSIONS("permissions", "permissions"),
    PRIVATE_ID("private-id", "private-id"),
    FFI_VERIFIED("ffi-verified", "ffi-verified"),
    CHECKED("checked", "checked");

    private final String jsonName;
    private final String artifactName;

    SastPhase(String jsonName, String artifactName) {
        this.jsonName = jsonName;
        this.artifactName = artifactName;
    }

    public String jsonName() {
        return jsonName;
    }

    public String artifactName() {
        return artifactName;
    }
}
