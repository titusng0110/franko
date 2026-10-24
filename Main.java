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
        // Defaults
        String sourceFile = "test.fr";
        String templateFile = "ProgramTemplate.cpp";
        String outputFile = "test.cpp";
        Path importSearchDir = Path.of("import");
        boolean verbose = false;

        // ------------------------------------------------------------
        // Parse CLI arguments
        // ------------------------------------------------------------
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

                default:
                    throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        // ----- Lex / parse -----
        CharStream input = CharStreams.fromFileName(sourceFile, StandardCharsets.UTF_8);
        FrankoLexer lexer = new FrankoLexer(input);

        /*
         * Lexing must be a hard gate. ANTLR's lexer only prints a "token
         * recognition error" and then silently skips characters that match no
         * token rule, which would let programs such as `c = a % b` or
         * `c = ~a + 1` compile into a different (wrong) program. Collect
         * every lexer error and reject the program if any occurred.
         */
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

        // ----- Build AST -----
        FrankoASTVisitor visitor = new FrankoASTVisitor();
        ASTNode rawAst = visitor.visit(tree);
        if (verbose) {
            System.out.println("==== Raw AST ====");
            ASTPrinter.print(rawAst, 0);
        }

        // ----- Resolve imports -----
        ImportResolver importResolver = new ImportResolver(importSearchDir);
        ASTNode ast = importResolver.resolve(rawAst, Path.of(sourceFile));

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

        // ----- Desugar AST -----
        Desugarer desugarer = new Desugarer();
        ast = desugarer.desugar(ast);
        if (desugarer.hasErrors()) {
            throw new RuntimeException(desugarer.formatErrors());
        }
        if (verbose) {
            System.out.println("==== Desugared AST ====");
            ASTPrinter.print(ast, 0);
        }

        // ----- Semantic analysis -----
        SemanticAnalyzer sema = new SemanticAnalyzer();
        SemanticASTNode semaAST = sema.analyze(ast);
        if (verbose) {
            System.out.println("==== Semantic AST ====");
            SemanticASTPrinter.print(semaAST, 0);
        }

        // ----- Report non-fatal warnings -----
        if (sema.hasWarnings()) {
            System.out.println("==== Warnings ====");

            for (String warning : sema.warnings()) {
                System.out.println("  - " + warning);
            }
        }

        // ----- Legality checking -----
        System.out.println("==== Legality Checking ====");
        DiagnosticBag legalityDiagnostics = new DiagnosticBag("Legality checking failed:");
        LegalityChecker legalityChecker = new LegalityChecker(legalityDiagnostics);
        legalityChecker.check(semaAST);

        // ----- Memory safety checking -----
        System.out.println("==== Memory Safety Checking ====");
        DiagnosticBag memorySafetyDiagnostics = new DiagnosticBag("Memory safety checking failed:");
        MemorySafetyChecker memorySafetyChecker = new MemorySafetyChecker(memorySafetyDiagnostics);
        memorySafetyChecker.check(semaAST);

        if (memorySafetyDiagnostics.hasWarnings()) {
            System.out.println("==== Memory Safety Warnings ====");

            for (String warning : memorySafetyDiagnostics.getWarnings()) {
                System.out.println("  - " + warning);
            }
        }

        System.out.println("All checks passed.");

        // ----- Generate C++ body -----
        Cpp14Codegen codegen = new Cpp14Codegen();
        String generatedBody = codegen.generate(semaAST);

        // ----- Load template -----
        String template = Files.readString(Path.of(templateFile), StandardCharsets.UTF_8);

        if (!template.contains("__FRANKO_PROGRAM__")) {
            throw new RuntimeException("Template does not contain __FRANKO_PROGRAM__ placeholder.");
        }

        // ----- Inject generated code into template -----
        String finalCpp = template.replace(
            "__FRANKO_PROGRAM__",
            indentBlock(generatedBody, 0).trim()
        );

        // ----- Ensure output directory exists if needed -----
        Path outPath = Path.of(outputFile);
        Path parent = outPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // ----- Write output file -----
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

    /**
     * Collects lexer diagnostics instead of merely printing them.
     *
     * ANTLR's lexer reports an unrecognizable character as a "token
     * recognition error" and then skips it, silently changing the program's
     * token stream. This listener reformats those errors in the compiler's
     * diagnostic style so the driver can reject the program.
     */
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
            /*
             * ANTLR's lexer message has the shape:
             *
             *   token recognition error at: '~'
             *
             * Extract the offending text between the quotes and reformat it.
             */
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