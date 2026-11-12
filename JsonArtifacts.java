import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JsonArtifacts {

    private final Path base;
    private final boolean enabled;
    private SourceTable lastSources;

    public JsonArtifacts(Path outputFile, boolean enabled) {
        this.base = basePath(outputFile);
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void deleteStaleArtifacts() {
        if (!enabled) {
            return;
        }

        for (AstPhase phase : AstPhase.values()) {
            deleteIfExists(Path.of(base + "." + phase.artifactName() + ".ast.json"));
        }

        for (SastPhase phase : SastPhase.values()) {
            deleteIfExists(Path.of(base + "." + phase.artifactName() + ".sast.json"));
        }

        deleteIfExists(Path.of(base + ".diagnostic.json"));
        deleteTree(Path.of(base + ".ast"));
    }

    public void writeAst(AstPhase phase, AstDocument doc) {
        if (!enabled || doc == null) {
            return;
        }

        lastSources = doc.sources;
        write(Path.of(base + "." + phase.artifactName() + ".ast.json"), ASTSerializer.toJson(doc));
    }

    public void writeAst(Path source, AstPhase phase, AstDocument doc) {
        if (!enabled || doc == null) {
            return;
        }

        write(perFileArtifact(source, phase.artifactName() + ".ast.json"), ASTSerializer.toJson(doc));
    }

    public void writeSast(SastPhase phase, SastDocument doc) {
        if (!enabled || doc == null) {
            return;
        }

        lastSources = doc.sources;
        write(Path.of(base + "." + phase.artifactName() + ".sast.json"), SastSerializer.toJson(doc));
    }

    public void writeDiagnostics(DiagnosticLog log) {
        if (!enabled) {
            return;
        }

        SourceTable sources = new SourceTable();

        if (lastSources != null) {
            sources.addAll(lastSources);
        }

        List<DiagnosticLog.Entry> selected = new ArrayList<>();

        if (log.hasErrors()) {
            for (DiagnosticLog.Entry entry : log.entries()) {
                if (entry.diagnostic().isError()) {
                    selected.add(entry);
                }
            }
        } else {
            for (DiagnosticLog.Entry entry : log.entries()) {
                if (!entry.diagnostic().isError()) {
                    selected.add(entry);
                }
            }
        }

        JsonValue diagnostics = JsonValue.array();

        for (DiagnosticLog.Entry entry : selected) {
            Diagnostic diagnostic = entry.diagnostic();
            JsonValue obj = JsonValue.object();
            obj.put("phase", entry.phase());
            obj.put("kind", diagnostic.origin().label());
            obj.put("severity", diagnostic.severity().label());
            obj.put("message", diagnostic.message());
            obj.put("span", ASTSerializer.spanToJson(diagnostic.span(), sources));
            diagnostics.add(obj);
        }

        JsonValue document = JsonValue.object();
        document.put("format", "franko-diagnostics");
        document.put("version", 1);
        document.put("sources", JsonAstSupport.sourceArray(sources));
        document.put("diagnostics", diagnostics);

        write(Path.of(base + ".diagnostic.json"), Json.write(document, true));
    }

    private Path perFileArtifact(Path source, String suffix) {
        String relative = SourcePaths.display(source);

        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }

        if (relative.length() > 1 && relative.charAt(1) == ':') {
            relative = relative.substring(2);
        }

        if (relative.isEmpty()) {
            relative = "source";
        }

        return Path.of(base + ".ast").resolve(relative + "." + suffix);
    }

    private void write(Path path, String text) {
        try {
            Path parent = path.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            String content = text.endsWith("\n") ? text : text + "\n";
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to write JSON artifact '" + path + "': " + e.getMessage(),
                    e
            );
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to delete stale artifact '" + path + "': " + e.getMessage(),
                    e
            );
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }

        try {
            if (Files.isDirectory(root)) {
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
                    for (Path entry : entries) {
                        deleteTree(entry);
                    }
                }
            }

            Files.deleteIfExists(root);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to delete stale artifact directory '" + root + "': "
                            + e.getMessage(),
                    e
            );
        }
    }

    private static Path basePath(Path outputFile) {
        if (outputFile == null) {
            return Path.of("output");
        }

        String name = outputFile.getFileName() == null
                ? outputFile.toString()
                : outputFile.getFileName().toString();
        int dot = name.lastIndexOf('.');

        if (dot <= 0) {
            return outputFile;
        }

        String stem = name.substring(0, dot);

        return outputFile.getParent() == null
                ? Path.of(stem)
                : outputFile.getParent().resolve(stem);
    }
}
