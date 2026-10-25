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

/**
 * ============================================================================
 * IMPORT RESOLVER
 * ============================================================================
 *
 * PURPOSE
 *
 * ImportResolver processes the import directives of a parsed program and
 * splices every imported file's top-level items into the root ProgramNode.
 *
 * It runs immediately after AST construction and before desugaring:
 *
 *   parse -> [ImportResolver] -> desugar -> semantic analysis -> ...
 *
 * After resolution the whole program behaves exactly like a single source
 * file, so every later phase (desugaring, semantic analysis, legality and
 * memory safety checking, code generation) works unchanged: the imported
 * symbols are compiled into one global AST.
 *
 * ----------------------------------------------------------------------------
 * IMPORT SYNTAX
 * ----------------------------------------------------------------------------
 *
 *   import "abc"
 *   import "lib/util.fr"
 *   import "../shared/util"
 *   import "my-lib/foo bar"
 *
 * The import path is always a string literal. Hyphens and spaces are
 * ordinary characters inside the quoted path.
 *
 * ----------------------------------------------------------------------------
 * PATH RESOLUTION
 * ----------------------------------------------------------------------------
 *
 * The literal content is escape-decoded, then:
 *
 *   - a path with an explicit './', '../', or '/' prefix resolves
 *     relative to the importing file (or as an absolute path), exactly
 *     as written;
 *
 *   - a prefix-free path is looked up in two places, in order:
 *
 *       1. the import search directory given to the compiler with -p
 *          (default: ./import, relative to the compiler directory),
 *       2. the directory of the importing file.
 *
 *     The first location that contains a regular file wins, so a
 *     module supplied through -p shadows a same-named file next to the
 *     importing source file.
 *
 *   - '.fr' is appended when the file name has no extension;
 *
 *   - a non-.fr extension is rejected:
 *
 *       import "foo.txt"   ->  error
 *
 * Examples (search dir = <compiler>/import):
 *
 *   import "foo"            ->  <compiler>/import/foo.fr, else ./foo.fr
 *   import "lib/foo"        ->  <compiler>/import/lib/foo.fr, else ./lib/foo.fr
 *   import "./lib/foo"      ->  ./lib/foo.fr
 *   import "../shared/foo"  ->  ../shared/foo.fr
 *   import "/abs/foo"       ->  /abs/foo.fr
 *
 * A missing search directory is not an error; resolution simply falls
 * through to the importing file's directory.
 *
 * ----------------------------------------------------------------------------
 * WINDOWS ABSOLUTE PATHS
 * ----------------------------------------------------------------------------
 *
 * Import paths are always written in POSIX form with '/' separators,
 * on every platform. When the compiler runs on Windows, a
 * '/'-prefixed string literal path is translated when it reaches the
 * filesystem:
 *
 *   import "/abs/foo"  ->  <currentDrive>:\abs\foo.fr
 *
 * The current drive is the drive of the current working directory, and
 * '/' separators in the translated path become '\'. The grammar keeps
 * accepting the POSIX form only.
 *
 * ----------------------------------------------------------------------------
 * CYCLES AND REPEATS
 * ----------------------------------------------------------------------------
 *
 * The resolver builds an import graph while it walks the program:
 *
 *   Map<canonical importing file, ordered canonical imported files>
 *
 * Every file that has been processed at least once is remembered. Any
 * import that reaches an already-processed file - a repeated import
 * (diamond) or an import cycle - is skipped silently, so each file's
 * symbols are collected exactly once.
 *
 * ----------------------------------------------------------------------------
 * COLLABORATORS
 * ----------------------------------------------------------------------------
 *
 * FrankoLexer / FrankoParser / FrankoASTVisitor:
 *   - each imported file is lexed, parsed, and lowered to a raw AST
 *     exactly like the root file.
 *
 * StringLiteralDecoder:
 *   - escape-decodes string-literal import paths.
 */
public class ImportResolver {

    /**
     * True when the compiler runs on Windows.
     *
     * Import paths are written in POSIX form with '/' separators on all
     * platforms; this flag controls only how an absolute path is
     * translated when it reaches the filesystem.
     */
    private static final boolean IS_WINDOWS = System
            .getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("windows");

    /**
     * Import graph: canonical importing file path -> canonical imported file
     * paths in declaration order.
     *
     * Edges are recorded for every declared import, including imports that
     * were skipped because their target was already processed.
     */
    private final Map<String, List<String>> importGraph = new LinkedHashMap<>();

    /**
     * Canonical paths of all files whose top-level items have already been
     * collected. A file is marked as processed before its items are spliced
     * so import cycles terminate.
     */
    private final Set<String> processedFiles = new LinkedHashSet<>();

    /**
     * Directory searched first for prefix-free import paths.
     *
     * Supplied with the compiler's -p flag; defaults to ./import
     * relative to the compiler's working directory.
     */
    private final Path importSearchDir;

    /**
     * Built-in module specifiers imported by the program, e.g. "franko/os".
     *
     * A specifier is recorded when its normalized import path text matches
     * the built-in module file ("franko/os" and "franko/os.fr" both
     * normalize to "franko/os.fr"). Only the canonical specifier form
     * activates compiler built-in behavior; importing the same file through
     * a relative path keeps it an ordinary Franko file.
     */
    private final Set<String> importedBuiltinModules = new LinkedHashSet<>();

    /**
     * Resolved file of the `import "franko/os"` module, or null when the
     * program does not import it through the canonical specifier.
     *
     * The FFI bridge (ffi.cpp) is located relative to this file.
     */
    private Path frankoOsModuleFile = null;

    public ImportResolver(Path importSearchDir) {
        this.importSearchDir = importSearchDir;
    }

    // ============================================================
    // Entry Point
    // ============================================================

    /**
     * Resolves all imports reachable from the given raw AST root.
     *
     * Returns a new ProgramNode whose top-level items are the root file's
     * items with every ImportNode replaced by the imported file's items.
     *
     * Non-program roots (tests, partial ASTs) are returned unchanged.
     */
    public ASTNode resolve(ASTNode root, Path sourceFile) {
        if (!(root instanceof ProgramNode program)) {
            return root;
        }

        Path rootPath = sourceFile.toAbsolutePath().normalize();
        String rootKey = canonicalKey(rootPath);

        /*
         * The root is marked processed before its items are spliced, so an
         * import cycle that reaches the root again is skipped silently.
         */
        processedFiles.add(rootKey);
        importGraph.computeIfAbsent(rootKey, key -> new ArrayList<>());

        List<ASTNode> items = new ArrayList<>();
        spliceItems(program.topLevelItems, rootPath, items);

        return new ProgramNode(items);
    }

    /**
     * The import graph built during resolution.
     *
     * Keys and values are canonical file paths; values keep declaration
     * order.
     */
    public Map<String, List<String>> importGraph() {
        return importGraph;
    }

    /**
     * Built-in module specifiers imported through their canonical path
     * text (currently only "franko/os").
     */
    public Set<String> importedBuiltinModules() {
        return new LinkedHashSet<>(importedBuiltinModules);
    }

    /**
     * Resolved file of the `import "franko/os"` module, or null when it
     * was not imported through the canonical specifier.
     */
    public Path frankoOsModuleFile() {
        return frankoOsModuleFile;
    }

    // ============================================================
    // Splicing
    // ============================================================

    /**
     * Copies one file's top-level items into the output list, replacing
     * every ImportNode with the items of the file it references.
     */
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

    /**
     * Resolves one import directive and appends the referenced file's
     * top-level items.
     *
     * Imports of already-processed files (repeats and cycles) are skipped
     * silently.
     */
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

        /*
         * Built-in module detection: the normalized path text "franko/os.fr"
         * covers both `import "franko/os"` and `import "franko/os.fr"`.
         * Recorded after the existence check so a missing module still
         * reports the ordinary import error below instead.
         */
        if (pathText.equals("franko/os.fr")) {
            importedBuiltinModules.add("franko/os");

            if (frankoOsModuleFile == null) {
                frankoOsModuleFile = target;
            }
        }

        collectFile(target, out);
    }

    /**
     * Parses one imported file and appends its top-level items.
     *
     * The file is marked as processed before its items are spliced so a
     * cycle that reaches it again terminates instead of recursing forever.
     */
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

        /*
         * Route all lexer and parser diagnostics through a collector that
         * names the imported file, then fail with the full list.
         */
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

        /*
         * Imports inside the imported file are resolved relative to that
         * file's own directory.
         */
        spliceItems(program.topLevelItems, file, out);
    }

    // ============================================================
    // Path Rules
    // ============================================================

    /**
     * Applies the import path rules to an ImportNode's raw path text.
     *
     * Returns the path string used for file resolution:
     *
     *   - '.fr' is appended when the file name has no extension;
     *
     *   - an error is raised when the extension is present but not '.fr'.
     *
     * A prefix-free path is kept prefix-free: the resolver decides
     * whether to try the -p search directory or the importing file's
     * directory first.
     */
    private String computePathText(ImportNode importNode, Path importingFile) {
        String raw = importNode.rawPathText;
        String pathText = raw;

        /*
         * Extension rules apply to the file name only, so a '.' in a
         * directory name (e.g. "my.dir/util") does not count.
         */
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

    /**
     * True when the path already carries a directory prefix, in which case
     * it resolves relative to the importing file (or as an absolute path)
     * and the -p search directory is skipped: './', '../', and '/'.
     */
    private static boolean hasDirectoryPrefix(String pathText) {
        return pathText.startsWith("./")
                || pathText.startsWith("../")
                || pathText.startsWith("/");
    }

    /**
     * Picks the file a completed import path refers to.
     *
     * Prefix-free paths are looked up in the -p search directory first;
     * the first location that holds a regular file wins. Every other
     * path, and any prefix-free path with no search-directory match,
     * resolves relative to the importing file's directory.
     *
     * The returned path may not exist; the caller reports that as an
     * import error naming every location that was tried.
     */
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

    /**
     * Path of a prefix-free import inside the -p search directory.
     */
    private Path searchCandidate(String pathText) {
        return importSearchDir
                .toAbsolutePath()
                .normalize()
                .resolve(pathText)
                .normalize();
    }

    /**
     * Describes the location(s) tried for a missing import, for the
     * diagnostic message.
     */
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

    /**
     * Turns a path string into an absolute, normalized path relative to the
     * importing file's directory.
     *
     * On Windows, a '/'-prefixed path is POSIX-form absolute. It is
     * translated to a current-drive absolute path:
     *
     *   /abs/foo  ->  <currentDrive>:\abs\foo
     *
     * where the current drive is the drive of the current working
     * directory, and '/' separators become '\'.
     */
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

    /**
     * Translates one POSIX-form absolute path to a Windows current-drive
     * absolute path.
     *
     * Leading '/' characters are dropped, remaining '/' separators become
     * '\', and the result is anchored at the given drive root:
     *
     *   "/abs/foo.fr" with drive root "C:\"  ->  C:\abs\foo.fr
     *
     * The drive root is normalized to end with a separator first, so the
     * translated path is assembled as a plain string and then parsed.
     * On Windows the final normalize() folds '.' and '..' segments with
     * real drive-absolute semantics.
     *
     * Package-private and side-effect free so the translation can be
     * exercised on any platform.
     */
    static Path translateWindowsAbsolutePath(String pathText, String driveRoot) {
        String relative = pathText.replaceAll("^/+", "").replace('/', '\\');

        String root = driveRoot;
        if (!root.endsWith("\\") && !root.endsWith("/")) {
            root = root + "\\";
        }

        return Path.of(root + relative).normalize();
    }

    /**
     * Canonical identity of a file for graph bookkeeping.
     *
     * Existing files are resolved through the filesystem so symlinks and
     * case-insensitive filesystems collapse to one identity; missing files
     * fall back to their normalized absolute path.
     */
    private static String canonicalKey(Path path) {
        try {
            return path.toRealPath().toString();
        } catch (Exception e) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    // ============================================================
    // Diagnostics
    // ============================================================

    /**
     * Collects lexer and parser error messages, prefixing each with the
     * file it came from.
     */
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
