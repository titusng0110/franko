public final class SastDocument {

    public final SastPhase phase;
    public final EntryKind entryKind;
    public final SourceTable sources;
    public final SymbolRegistry symbols;
    public final SastNode root;

    public SastDocument(
            SastPhase phase,
            EntryKind entryKind,
            SourceTable sources,
            SymbolRegistry symbols,
            SastNode root
    ) {
        this.phase = phase;
        this.entryKind = entryKind == null ? EntryKind.NONE : entryKind;
        this.sources = sources == null ? new SourceTable() : sources;
        this.symbols = symbols == null ? new SymbolRegistry() : symbols;
        this.root = root;
    }

    public SastDocument withPhase(SastPhase newPhase) {
        return new SastDocument(newPhase, entryKind, sources, symbols, root);
    }
}

enum SastPhase {
    SEMANTIC("semantic", "semantic"),
    USELESS_GENERIC_PRUNED("useless-generic-pruned", "useless-generic-pruned"),
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
