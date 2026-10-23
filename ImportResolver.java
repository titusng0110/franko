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
 *   import importPath
 *
 * importPath is either a string literal or an import name:
 *
 *   import util
 *   import lib/util
 *   import "util"
 *   import "lib/util.fr"
 *   import "../shared/util"
 *
 * ----------------------------------------------------------------------------
 * PATH RESOLUTION
 * ----------------------------------------------------------------------------
 *
 * Paths are always resolved relative to the directory of the file that
 * contains the import statement.
 *
 * importName form:
 *
 *   - './' is prepended and '.fr' is appended:
 *
 *       import lib/abc   ->  ./lib/abc.fr
 *
 * stringLiteral form:
 *
 *   - './' is prepended when the path has no directory prefix. The
 *     prefixes './', '../', and '/' all count as directory prefixes.
 *
 *   - '.fr' is appended when the file name has no extension.
 *
 *   - a non-.fr extension is rejected:
 *
 *       import "foo.txt"   ->  error
 *
 * Examples (relative to the importing file's directory):
 *
 *   import lib/abc          ->  ./lib/abc.fr
 *   import "foo"            ->  ./foo.fr
 *   import "lib/foo"        ->  ./lib/foo.fr
 *   import "lib/foo.fr"     ->  ./lib/foo.fr
 *   import "./lib/foo"      ->  ./lib/foo.fr
 *   import "../shared/foo"  ->  ../shared/foo.fr
 *   import "/abs/foo"       ->  /abs/foo.fr
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
                    "Import error: cannot read imported file '"
                            + target
                            + "' (imported from '"
                            + importingFile
                            + "')"
            );
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
     *   - importName form: './' + name + '.fr'
     *
     *   - stringLiteral form: './' prepended when no directory prefix is
     *     present, '.fr' appended when the file name has no extension,
     *     and an error when the extension is present but not '.fr'.
     */
    private String computePathText(ImportNode importNode, Path importingFile) {
        String raw = importNode.rawPathText;

        if (!importNode.isStringLiteral) {
            return "./" + raw + ".fr";
        }

        String pathText = raw;

        if (!hasDirectoryPrefix(pathText)) {
            pathText = "./" + pathText;
        }

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
     * './' must not be prepended: './', '../', and '/'.
     */
    private static boolean hasDirectoryPrefix(String pathText) {
        return pathText.startsWith("./")
                || pathText.startsWith("../")
                || pathText.startsWith("/");
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
    private static Path resolveTarget(String pathText, Path importingFile) {
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
