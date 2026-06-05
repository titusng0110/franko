import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws Exception {
        // Defaults
        String sourceFile = "test.fr";
        String templateFile = "ProgramTemplate.cpp";
        String outputFile = "test.cpp";
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

                case "-v":
                    verbose = true;
                    break;

                default:
                    throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        // ----- Lex / parse -----
        CharStream input = CharStreams.fromFileName(sourceFile);
        FrankoLexer lexer = new FrankoLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FrankoParser parser = new FrankoParser(tokens);

        ParseTree tree = parser.program();

        if (parser.getNumberOfSyntaxErrors() > 0) {
            throw new RuntimeException(
                "Parse failed: " + parser.getNumberOfSyntaxErrors() + " syntax error(s)."
            );
        }

        // ----- Build AST -----
        FrankoASTVisitor visitor = new FrankoASTVisitor();
        ASTNode rawAst = visitor.visit(tree);

        // Conditional debug print
        if (verbose) {
            System.out.println("==== Raw AST ====");
            ASTPrinter.print(rawAst, 0);
        }

        // ----- Desugar AST -----
        Desugarer desugarer = new Desugarer();
        ASTNode ast = desugarer.desugar(rawAst);

        // Conditional debug print
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

        // ----- Legality checking -----
        System.out.println("==== Legality Checking ====");
        MasterChecker masterChecker = new MasterChecker();
        masterChecker.check(semaAST);
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
            indentBlock(generatedBody, 1).trim()
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
}