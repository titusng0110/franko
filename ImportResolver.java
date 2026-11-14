import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ImportResolver {

    private static final boolean IS_WINDOWS = System
            .getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("windows");

    private final Map<String, List<String>> importGraph = new LinkedHashMap<>();

    private final Set<String> processedFiles = new LinkedHashSet<>();

    private final Path importSearchDir;

    private final Frontend frontend;

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public ImportResolver(
            Path importSearchDir,
            Frontend frontend
    ) {
        this.importSearchDir = importSearchDir;
        this.frontend = frontend;
    }

    public ImportResult resolve(AstDocument root, Path sourceFile) {
        Path rootPath = sourceFile.toAbsolutePath().normalize();
        String rootKey = SourcePaths.canonicalKey(rootPath);

        processedFiles.add(rootKey);
        importEdges(rootKey);

        SourceTable sources = new SourceTable();
        sources.addAll(root.sources);

        List<AstNode> items = new ArrayList<>();
        spliceItems(root.root.items, rootPath, items, sources);

        AstDocument merged = new AstDocument(
                AstPhase.IMPORT_RESOLVED,
                sources,
                AstNode.program(root.root.span, items)
        );

        return new ImportResult(
                merged,
                new LinkedHashMap<>(importGraph),
                List.copyOf(diagnostics)
        );
    }

    public Map<String, List<String>> importGraph() {
        return importGraph;
    }

    private void spliceItems(
            List<AstNode> items,
            Path importingFile,
            List<AstNode> out,
            SourceTable sources
    ) {
        for (AstNode item : items) {
            if (item.kind == AstKind.IMPORT) {
                processImport(item, importingFile, out, sources);
                continue;
            }

            addItem(item, out);
        }
    }

    private void addItem(AstNode item, List<AstNode> out) {
        out.add(item);
    }

    private void processImport(
            AstNode importNode,
            Path importingFile,
            List<AstNode> out,
            SourceTable sources
    ) {
        String pathText = computePathText(importNode, importingFile);

        Path target = resolveTarget(pathText, importingFile);
        String targetKey = SourcePaths.canonicalKey(target);

        importEdges(SourcePaths.canonicalKey(importingFile)).add(targetKey);

        if (processedFiles.contains(targetKey)) {
            return;
        }

        if (!Files.isRegularFile(target)) {
            diagnostics.add(Diagnostic.error(
                    importNode.span,
                    "Import error: cannot read imported file"
                            + candidateDescription(pathText, target, importingFile)
                            + ". Imports are loaded before analysis, so the"
                            + " compiler must be able to read the target file."
                            + " Check the path spelling and the -p import"
                            + " directory."
            ));
            return;
        }

        processedFiles.add(targetKey);
        importEdges(targetKey);

        Result<AstDocument> result = frontend.loadDesugared(target);
        diagnostics.addAll(result.diagnostics);

        if (result.value == null) {
            return;
        }

        sources.addAll(result.value.sources);
        spliceItems(result.value.root.items, target, out, sources);
    }

    private String computePathText(AstNode importNode, Path importingFile) {
        String raw = importNode.text;
        String pathText = raw;

        String fileName = pathText.substring(pathText.lastIndexOf('/') + 1);

        if (fileName.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    importNode.span,
                    "Import error: import path '"
                            + raw
                            + "' in '"
                            + importingFile
                            + "' has no file name"
                            + ". An import must name a .fr source file, not a"
                            + " directory. Point the import at a concrete file"
                            + " such as '../lib/foo.fr'."
            ));
            return pathText;
        }

        int dot = fileName.lastIndexOf('.');

        if (dot > 0) {
            String extension = fileName.substring(dot + 1);

            if (!extension.equals("fr")) {
                diagnostics.add(Diagnostic.error(
                        importNode.span,
                        "Import error: import path '"
                                + raw
                                + "' in '"
                                + importingFile
                                + "' must reference a .fr file"
                                + ". Franko only imports source files with the"
                                + " .fr extension. Change the import path to end"
                                + " in .fr."
                ));
                return pathText;
            }

            return pathText;
        }

        return pathText + ".fr";
    }

    private static boolean hasDirectoryPrefix(String pathText) {
        return pathText.startsWith("./")
                || pathText.startsWith("../")
                || pathText.startsWith("/");
    }

    private Path resolveTarget(String pathText, Path importingFile) {
        Path fileRelative = resolveRelativeToImportingFile(pathText, importingFile);

        if (hasDirectoryPrefix(pathText)) {
            return fileRelative;
        }

        Path searchCandidate = searchCandidate(pathText);

        if (Files.isRegularFile(searchCandidate)) {
            return searchCandidate;
        }

        return fileRelative;
    }

    private Path searchCandidate(String pathText) {
        return importSearchDir
                .toAbsolutePath()
                .normalize()
                .resolve(pathText)
                .normalize();
    }

    private String candidateDescription(
            String pathText,
            Path fileRelative,
            Path importingFile
    ) {
        if (hasDirectoryPrefix(pathText)) {
            return " '" + fileRelative
                    + "' (imported from '" + importingFile + "')";
        }

        return " '" + searchCandidate(pathText)
                + "' or '" + fileRelative
                + "' (imported from '" + importingFile + "')";
    }

    private static Path resolveRelativeToImportingFile(String pathText, Path importingFile) {
        if (IS_WINDOWS && pathText.startsWith("/")) {
            Path currentDriveRoot = Path.of(System.getProperty("user.dir")).getRoot();

            if (currentDriveRoot != null) {
                return translateWindowsAbsolutePath(
                        pathText,
                        String.valueOf(currentDriveRoot)
                );
            }
        }

        Path base = importingFile.toAbsolutePath().getParent();

        if (base == null) {
            base = importingFile.toAbsolutePath().getRoot();
        }

        return base.resolve(pathText).normalize();
    }

    static Path translateWindowsAbsolutePath(String pathText, String driveRoot) {
        String relative = pathText.replaceAll("^/+", "").replace('/', '\\');

        String root = driveRoot;
        if (!root.endsWith("\\") && !root.endsWith("/")) {
            root = root + "\\";
        }

        return Path.of(root + relative).normalize();
    }

    private List<String> importEdges(String key) {
        List<String> edges = importGraph.get(key);

        if (edges == null) {
            edges = new ArrayList<>();
            importGraph.put(key, edges);
        }

        return edges;
    }
}
