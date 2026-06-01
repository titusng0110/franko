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
        String outputFile = "build/out.cpp";

        // ------------------------------------------------------------
        // Parse CLI arguments
        //
        // Supported:
        //   java Main
        //   java Main bfs.fr
        //   java Main bfs.fr -o bfs.cpp
        //   java Main bfs.fr -o bfs.cpp -t ProgramTemplate.cpp
        // ------------------------------------------------------------
        if (args.length > 0) {
            sourceFile = args[0];
        }

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-o":
                    if (i + 1 >= args.length) {
                        die("Missing value after -o");
                    }
                    outputFile = args[++i];
                    break;

                case "-t":
                    if (i + 1 >= args.length) {
                        die("Missing value after -t");
                    }
                    templateFile = args[++i];
                    break;

                default:
                    die("Unknown argument: " + args[i]);
            }
        }

        // ----- Lex / parse -----
        CharStream input = CharStreams.fromFileName(sourceFile);
        FrankoLexer lexer = new FrankoLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FrankoParser parser = new FrankoParser(tokens);

        ParseTree tree = parser.program();

        // Stop if syntax errors occurred
        if (parser.getNumberOfSyntaxErrors() > 0) {
            System.err.println("Parse failed: " + parser.getNumberOfSyntaxErrors() + " syntax error(s).");
            return;
        }

        // ----- Build AST -----
        FrankoASTVisitor visitor = new FrankoASTVisitor();
        ASTNode rawAst = visitor.visit(tree);

        // ----- Debug: print raw AST -----
        System.out.println("==== Raw AST ====");
        ASTPrinter.print(rawAst, 0);

        // ----- Desugar AST -----
        Desugarer desugarer = new Desugarer();
        ASTNode ast = desugarer.desugar(rawAst);

        // ----- Debug: print desugared AST -----
        System.out.println("==== Desugared AST ====");
        ASTPrinter.print(ast, 0);

        // ----- Generate C++ body -----
        Cpp14Codegen codegen = new Cpp14Codegen();
        String generatedBody = codegen.generate(ast);

        System.out.println("\n==== GENERATED FRANKO PROGRAM BODY ====");
        System.out.println(generatedBody);

        // ----- Load template -----
        String template = Files.readString(Path.of(templateFile), StandardCharsets.UTF_8);

        if (!template.contains("__FRANKO_PROGRAM__")) {
            throw new RuntimeException("Template does not contain __FRANKO_PROGRAM__ placeholder.");
        }

        // ----- Inject generated code into template -----
        String finalCpp = template.replace("__FRANKO_PROGRAM__", indentBlock(generatedBody, 1).trim());

        // ----- Ensure output directory exists if needed -----
        Path outPath = Path.of(outputFile);
        Path parent = outPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // ----- Write output file -----
        Files.writeString(outPath, finalCpp, StandardCharsets.UTF_8);

        System.out.println("\nC++ code written to: " + outputFile);
    }

    private static void die(String msg) {
        System.err.println("Error: " + msg);
        System.err.println("Usage:");
        System.err.println("  java Main [source.fr] [-o output.cpp] [-t template.cpp]");
        System.exit(1);
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
