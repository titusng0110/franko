import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws Exception {
        // Input / output file paths
        String sourceFile = "test.fr";
        String templateFile = "ProgramTemplate.cpp";
        String outputFile = "out.cpp";

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
        ASTNode ast = visitor.visit(tree);

        // ----- Debug: print AST -----
        System.out.println("==== AST ====");
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

        // ----- Write output file -----
        Files.writeString(Path.of(outputFile), finalCpp, StandardCharsets.UTF_8);

        System.out.println("\nC++ code written to: " + outputFile);
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
