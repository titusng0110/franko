import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.nio.charset.StandardCharsets;
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

    private final Set<String> importedBuiltinModules = new LinkedHashSet<>();

    private Path frankoOsModuleFile = null;

    public ImportResolver(Path importSearchDir) {
        this.importSearchDir = importSearchDir;
    }

    public ASTNode resolve(ASTNode root, Path sourceFile) {
        if (!(root instanceof ProgramNode program)) {
            return root;
        }

        Path rootPath = sourceFile.toAbsolutePath().normalize();
        String rootKey = canonicalKey(rootPath);

        processedFiles.add(rootKey);
        importGraph.computeIfAbsent(rootKey, key -> new ArrayList<>());

        List<ASTNode> items = new ArrayList<>();
        spliceItems(program.topLevelItems, rootPath, items);

        return new ProgramNode(items);
    }

    public Map<String, List<String>> importGraph() {
        return importGraph;
    }

    public Set<String> importedBuiltinModules() {
        return new LinkedHashSet<>(importedBuiltinModules);
    }

    public Path frankoOsModuleFile() {
        return frankoOsModuleFile;
    }

    private void spliceItems(
            List<ASTNode> items,
            Path importingFile,
            List<ASTNode> out
    ) {
        for (ASTNode item : items) {
            if (item instanceof ImportNode importNode) {
                processImport(importNode, importingFile, out);
                continue;
            }

            out.add(item);
        }
    }

    private void processImport(
            ImportNode importNode,
            Path importingFile,
            List<ASTNode> out
    ) {
        String pathText = computePathText(importNode, importingFile);

        Path target = resolveTarget(pathText, importingFile);
        String targetKey = canonicalKey(target);

        importGraph
                .computeIfAbsent(canonicalKey(importingFile), key -> new ArrayList<>())
                .add(targetKey);

        if (processedFiles.contains(targetKey)) {
            return;
        }

        if (!Files.isRegularFile(target)) {
            throw new RuntimeException(
                    "Import error: cannot read imported file"
                            + candidateDescription(pathText, target, importingFile)
            );
        }

        if (pathText.equals("franko/os.fr")) {
            importedBuiltinModules.add("franko/os");

            if (frankoOsModuleFile == null) {
                frankoOsModuleFile = target;
            }
        }

        collectFile(target, out);
    }

    private void collectFile(Path file, List<ASTNode> out) {
        String key = canonicalKey(file);

        processedFiles.add(key);
        importGraph.computeIfAbsent(key, k -> new ArrayList<>());

        CharStream input;
        try {
            input = CharStreams.fromPath(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Import error: cannot read file '" + file + "': " + e.getMessage(),
                    e
            );
        }

        FrankoLexer lexer = new FrankoLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FrankoParser parser = new FrankoParser(tokens);

        CollectingErrorListener errorListener = new CollectingErrorListener(file.toString());

        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        ParseTree tree = parser.program();

        if (!errorListener.errors.isEmpty()
                || parser.getNumberOfSyntaxErrors() > 0) {
            throw new RuntimeException(
                    "Parse failed in imported file '"
                            + file
                            + "':\n"
                            + String.join("\n", errorListener.errors)
            );
        }

        FrankoASTVisitor visitor = new FrankoASTVisitor();
        ASTNode imported = visitor.visit(tree);

        if (!(imported instanceof ProgramNode program)) {
            throw new RuntimeException(
                    "Internal import error: file '"
                            + file
                            + "' did not parse into a program"
            );
        }

        spliceItems(program.topLevelItems, file, out);
    }

    private String computePathText(ImportNode importNode, Path importingFile) {
        String raw = importNode.rawPathText;
        String pathText = raw;

        String fileName = pathText.substring(pathText.lastIndexOf('/') + 1);

        if (fileName.isEmpty()) {
            throw new RuntimeException(
                    "Import error: import path '"
                            + raw
                            + "' in '"
                            + importingFile
                            + "' has no file name"
            );
        }

        int dot = fileName.lastIndexOf('.');

        if (dot > 0) {
            String extension = fileName.substring(dot + 1);

            if (!extension.equals("fr")) {
                throw new RuntimeException(
                        "Import error: import path '"
                                + raw
                                + "' in '"
                                + importingFile
                                + "' must reference a .fr file"
                );
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

    private static String canonicalKey(Path path) {
        try {
            return path.toRealPath().toString();
        } catch (Exception e) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    private static final class CollectingErrorListener extends BaseErrorListener {
        private final String fileName;
        final List<String> errors = new ArrayList<>();

        CollectingErrorListener(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e
        ) {
            errors.add(fileName + ":" + line + ":" + charPositionInLine + ": " + msg);
        }
    }
}
