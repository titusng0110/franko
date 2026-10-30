import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {

        String sourceFile = "test.fr";
        String templateFile = null;
        boolean explicitTemplate = false;
        String outputFile = "test.cpp";
        Path importSearchDir = Path.of("import");
        boolean verbose = false;
        String targetSpec = null;
        boolean optimize = false;
        boolean maxOptimize = false;

        if (args.length > 0) {
            sourceFile = args[0];
        }

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-o":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value after -o");
                    }
                    outputFile = args[++i];
                    break;

                case "-t":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value after -t");
                    }
                    templateFile = args[++i];
                    explicitTemplate = true;
                    break;

                case "-p":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value after -p");
                    }
                    importSearchDir = Path.of(args[++i]);
                    break;

                case "-v":
                    verbose = true;
                    break;

                case "-O":
                    if (maxOptimize) {
                        throw new IllegalArgumentException("-O and -Omax are mutually exclusive");
                    }
                    optimize = true;
                    break;

                case "-Omax":
                    if (optimize) {
                        throw new IllegalArgumentException("-O and -Omax are mutually exclusive");
                    }
                    maxOptimize = true;
                    break;

                case "--target":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value after --target");
                    }
                    targetSpec = args[++i];
                    break;

                default:
                    throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        CharStream input = CharStreams.fromFileName(sourceFile, StandardCharsets.UTF_8);
        FrankoLexer lexer = new FrankoLexer(input);

        LexErrorCollector lexErrors = new LexErrorCollector();
        lexer.removeErrorListeners();
        lexer.addErrorListener(lexErrors);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FrankoParser parser = new FrankoParser(tokens);

        ParseTree tree = parser.program();

        if (!lexErrors.errors.isEmpty()) {
            throw new RuntimeException(String.join("\n", lexErrors.errors));
        }

        if (parser.getNumberOfSyntaxErrors() > 0) {
            throw new RuntimeException(
                "Parse failed: " + parser.getNumberOfSyntaxErrors() + " syntax error(s)."
            );
        }

        FrankoASTVisitor visitor = new FrankoASTVisitor();
        ASTNode rawAst = visitor.visit(tree);
        if (verbose) {
            System.out.println("==== Raw AST ====");
            ASTPrinter.print(rawAst, 0);
        }

        ImportResolver importResolver = new ImportResolver(importSearchDir);
        ASTNode ast = importResolver.resolve(rawAst, Path.of(sourceFile));

        TargetInfo target = targetSpec == null
                ? TargetInfo.detectHost()
                : TargetInfo.parse(targetSpec);

        boolean osModuleImported =
                importResolver.importedBuiltinModules().contains("franko/os");

        if (verbose) {
            System.out.println("Target: " + target.canonical());
        }

        if (osModuleImported) {
            OsFfi.applyTargetConstants(ast, target);
        }

        if (verbose) {
            System.out.println("==== Imports ====");

            for (Map.Entry<String, List<String>> entry : importResolver.importGraph().entrySet()) {
                System.out.println("  " + entry.getKey());

                for (String imported : entry.getValue()) {
                    System.out.println("    -> " + imported);
                }
            }

            System.out.println("==== Import-resolved AST ====");
            ASTPrinter.print(ast, 0);
        }

        Desugarer desugarer = new Desugarer();
        ast = desugarer.desugar(ast);
        if (desugarer.hasErrors()) {
            throw new RuntimeException(desugarer.formatErrors());
        }
        if (verbose) {
            System.out.println("==== Desugared AST ====");
            ASTPrinter.print(ast, 0);
        }

        SemanticAnalyzer sema = new SemanticAnalyzer();

        if (osModuleImported) {
            sema.setTarget(target);
        }

        SemanticASTNode semaAST = sema.analyze(ast);
        if (verbose) {
            System.out.println("==== Semantic AST ====");
            SemanticASTPrinter.print(semaAST, 0);
        }

        if (sema.hasWarnings()) {
            System.out.println("==== Warnings ====");

            for (String warning : sema.warnings()) {
                System.out.println("  - " + warning);
            }
        }

        System.out.println("==== Legality Checking ====");
        DiagnosticBag legalityDiagnostics = new DiagnosticBag("Legality checking failed:");
        LegalityChecker legalityChecker = new LegalityChecker(legalityDiagnostics);
        legalityChecker.check(semaAST);

        semaAST = new BranchPruner().prune(semaAST);
        semaAST = new InactiveDeclarationPruner().prune(semaAST);

        System.out.println("==== Pointer Null-State Checking ====");
        DiagnosticBag pointerStateDiagnostics = new DiagnosticBag("Pointer null-state checking failed:");
        PointerNullStateChecker pointerStateChecker = new PointerNullStateChecker(pointerStateDiagnostics);
        pointerStateChecker.check(semaAST);

        if (pointerStateDiagnostics.hasWarnings()) {
            System.out.println("==== Pointer Null-State Warnings ====");

            for (String warning : pointerStateDiagnostics.getWarnings()) {
                System.out.println("  - " + warning);
            }
        }

        System.out.println("==== Pointer Permission Linting ====");
        DiagnosticBag permissionDiagnostics =
                new DiagnosticBag("Pointer permission linting failed:");
        PointerPermissionChecker permissionChecker =
                new PointerPermissionChecker(permissionDiagnostics);
        permissionChecker.check(semaAST);

        if (permissionDiagnostics.hasWarnings()) {
            System.out.println("==== Pointer Permission Warnings ====");

            for (String warning : permissionDiagnostics.getWarnings()) {
                System.out.println("  - " + warning);
            }
        }

        System.out.println("All checks passed.");

        String osFfiBridgeText = null;

        if (osModuleImported) {
            Path osModuleFile = importResolver.frankoOsModuleFile();

            if (osModuleFile == null) {
                throw new RuntimeException(
                    "Internal compiler error: 'franko/os' was imported but its module file was not recorded"
                );
            }

            Path ffiBridgeFile = osModuleFile
                    .toAbsolutePath()
                    .getParent()
                    .resolve("os.ffi.cpp")
                    .normalize();

            if (!Files.isRegularFile(ffiBridgeFile)) {
                throw new RuntimeException(
                    "FFI error: 'franko/os' was imported but the FFI bridge file is missing: '"
                            + ffiBridgeFile
                            + "' (expected next to the resolved os.fr module)"
                );
            }

            osFfiBridgeText = Files.readString(ffiBridgeFile, StandardCharsets.UTF_8);

            OsFfi.verifyBridge(semaAST, osFfiBridgeText);
        }

        Cpp14Codegen codegen = new Cpp14Codegen(osFfiBridgeText, target);
        String generatedBody = codegen.generate(semaAST);

        if (!explicitTemplate) {
            templateFile = switch (sema.entryKind()) {
                case ARGS -> "ProgramTemplateWithRawCArgs.cpp";
                case NO_ARGS -> "ProgramTemplate.cpp";
                case NONE -> throw new RuntimeException(
                    "Internal compiler error: no entry-point function 'main' after successful analysis"
                );
            };
        }

        String template = Files.readString(Path.of(templateFile), StandardCharsets.UTF_8);

        if (!template.contains("__FRANKO_PROGRAM__")) {
            throw new RuntimeException("Template does not contain __FRANKO_PROGRAM__ placeholder.");
        }

        // Without -O the program uses the system allocator (malloc/free)
        // so AddressSanitizer instrumentation applies; with -O it uses
        // the bundled jemalloc header; with -Omax it uses the jemalloc
        // header variant with bounds and unsafe_cast checking stripped.
        // Custom -t templates that pin their own runtime #include keep
        // working: the splice only fires when the placeholder is present,
        // but stock templates must carry it.
        String runtimeHeader = maxOptimize ? "FrankoRuntimeMax.hpp"
                : optimize ? "FrankoRuntime.hpp"
                : "FrankoRuntimeWithAsan.hpp";
        if (!explicitTemplate && !template.contains("__FRANKO_RUNTIME_HEADER__")) {
            throw new RuntimeException("Template does not contain __FRANKO_RUNTIME_HEADER__ placeholder.");
        }

        if (!explicitTemplate) {
            String expectedEntryCall = switch (sema.entryKind()) {
                case ARGS -> "FUNC_main_HASH_747cdf98";
                case NO_ARGS -> "FUNC_main_HASH_d7d11c43";
                case NONE -> throw new RuntimeException(
                    "Internal compiler error: no entry-point function 'main' after successful analysis"
                );
            };

            if (!template.contains(expectedEntryCall)) {
                throw new RuntimeException(
                    "Template '" + templateFile + "' does not call the expected entry point '"
                        + expectedEntryCall + "'."
                );
            }
        }

        String finalCpp = template.replace(
            "__FRANKO_PROGRAM__",
            indentBlock(generatedBody, 0).trim()
        ).replace("__FRANKO_RUNTIME_HEADER__", runtimeHeader);

        Path outPath = Path.of(outputFile);
        Path parent = outPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(outPath, finalCpp, StandardCharsets.UTF_8);

        System.out.println("Successfully compiled to C++14: " + outputFile);
    }

    private static String indentBlock(String text, int levels) {
        String indent = "    ".repeat(levels);
        String[] lines = text.split("\\R", -1);

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!line.isEmpty()) {
                sb.append(indent).append(line);
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private static final class LexErrorCollector extends BaseErrorListener {
        final List<String> errors = new ArrayList<>();

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e
        ) {

            String text = msg;
            String marker = "token recognition error at: '";
            int start = msg.indexOf(marker);

            if (start >= 0 && msg.endsWith("'") && msg.length() > start + marker.length()) {
                text = msg.substring(start + marker.length(), msg.length() - 1);
            }

            errors.add("Lex error: \"" + text + "\" is not a recognizable token. (line "
                    + line + ", column " + (charPositionInLine + 1) + ")");
        }
    }
}
