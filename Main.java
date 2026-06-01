import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

public class Main {
    public static void main(String[] args) throws Exception {
        CharStream input = CharStreams.fromFileName("test.fr");
        FrankoLexer lexer = new FrankoLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FrankoParser parser = new FrankoParser(tokens);

        ParseTree tree = parser.program();
        System.out.println(tree.toStringTree(parser));

        
        FrankoASTVisitor visitor = new FrankoASTVisitor();
        ASTNode ast = visitor.visit(tree);

        System.out.println("\n==== AST ====");
        ASTPrinter.print(ast, 0);


    }
}