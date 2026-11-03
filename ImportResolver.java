import java.math.BigInteger;
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

    private final CompileTimeInjector compileTimeInjector;

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private final Map<String, GlobalVarDeclNode> hoistedStringGlobals =
            new LinkedHashMap<>();

    public ImportResolver(
            Path importSearchDir,
            Frontend frontend,
            CompileTimeInjector compileTimeInjector
    ) {
        this.importSearchDir = importSearchDir;
        this.frontend = frontend;
        this.compileTimeInjector = compileTimeInjector;
    }

    public ImportResult resolve(AstDocument root, Path sourceFile) {
        Path rootPath = sourceFile.toAbsolutePath().normalize();
        String rootKey = SourcePaths.canonicalKey(rootPath);

        processedFiles.add(rootKey);
        importGraph.computeIfAbsent(rootKey, key -> new ArrayList<>());

        SourceTable sources = new SourceTable();
        sources.addAll(root.sources);

        List<ASTNode> items = new ArrayList<>();
        spliceItems(root.root.topLevelItems, rootPath, items, sources);

        AstDocument merged = new AstDocument(
                AstPhase.IMPORT_RESOLVED,
                sources,
                new ProgramNode(root.root.span, items)
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
            List<ASTNode> items,
            Path importingFile,
            List<ASTNode> out,
            SourceTable sources
    ) {
        for (ASTNode item : items) {
            if (item instanceof ImportNode importNode) {
                processImport(importNode, importingFile, out, sources);
                continue;
            }

            addItem(item, out);
        }
    }

    private void addItem(ASTNode item, List<ASTNode> out) {
        if (item instanceof GlobalVarDeclNode global
                && global.literalBytes != null
                && global.name.startsWith("_STR_")) {
            String key = global.name
                    + "|"
                    + typeKey(global.type)
                    + "|"
                    + literalBytesKey(global.literalBytes);

            if (hoistedStringGlobals.containsKey(key)) {
                return;
            }

            hoistedStringGlobals.put(key, global);
        }

        out.add(item);
    }

    private void processImport(
            ImportNode importNode,
            Path importingFile,
            List<ASTNode> out,
            SourceTable sources
    ) {
        String pathText = computePathText(importNode, importingFile);

        if (CompileTimeInjector.isStaticModule(pathText)) {
            importGraph
                    .computeIfAbsent(
                            SourcePaths.canonicalKey(importingFile),
                            key -> new ArrayList<>())
                    .add(CompileTimeInjector.STATIC_MODULE_KEY);

            if (processedFiles.contains(CompileTimeInjector.STATIC_MODULE_KEY)) {
                return;
            }

            processedFiles.add(CompileTimeInjector.STATIC_MODULE_KEY);
            importGraph.computeIfAbsent(
                    CompileTimeInjector.STATIC_MODULE_KEY,
                    key -> new ArrayList<>()
            );

            out.add(compileTimeInjector.injectStaticNamespace(importNode.span));
            return;
        }

        Path target = resolveTarget(pathText, importingFile);
        String targetKey = SourcePaths.canonicalKey(target);

        importGraph
                .computeIfAbsent(SourcePaths.canonicalKey(importingFile), key -> new ArrayList<>())
                .add(targetKey);

        if (processedFiles.contains(targetKey)) {
            return;
        }

        if (!Files.isRegularFile(target)) {
            diagnostics.add(Diagnostic.error(
                    importNode.span,
                    "Import error: cannot read imported file"
                            + candidateDescription(pathText, target, importingFile)
            ));
            return;
        }

        processedFiles.add(targetKey);
        importGraph.computeIfAbsent(targetKey, key -> new ArrayList<>());

        Result<AstDocument> result = frontend.loadDesugared(target);
        diagnostics.addAll(result.diagnostics);

        if (result.value == null) {
            return;
        }

        sources.addAll(result.value.sources);
        spliceItems(result.value.root.topLevelItems, target, out, sources);
    }

    private String computePathText(ImportNode importNode, Path importingFile) {
        String raw = importNode.rawPathText;
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

    private static String typeKey(TypeNode type) {
        if (type == null) {
            return "<null>";
        }

        if (type instanceof PrimitiveTypeNode primitive) {
            return primitive.kind.name();
        }

        if (type instanceof VoidTypeNode) {
            return "void";
        }

        if (type instanceof DynamicArrayTypeNode dynamic) {
            return "[" + typeKey(dynamic.elementType) + "]";
        }

        if (type instanceof StaticArrayTypeNode staticArray) {
            String size = staticArray.sizeExpr instanceof IntNode intNode
                    ? intNode.value
                    : "?";
            return "[" + typeKey(staticArray.elementType) + ";" + size + "]";
        }

        if (type instanceof PtrTypeNode ptr) {
            return ptr.kind.name() + "<" + typeKey(ptr.referencedType) + ">";
        }

        if (type instanceof StructTypeNode struct) {
            return String.join("::", struct.namespaceSegments) + "::" + struct.name;
        }

        return type.getClass().getSimpleName();
    }

    private static String literalBytesKey(List<BigInteger> values) {
        StringBuilder sb = new StringBuilder();

        for (BigInteger value : values) {
            sb.append(value.toString(10)).append(',');
        }

        return sb.toString();
    }
}
