import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Frontend {

    private final JsonArtifacts artifacts;
    private final ModuleRegistry modules;

    public Frontend(JsonArtifacts artifacts) {
        this(artifacts, new ModuleRegistry());
    }

    public Frontend(JsonArtifacts artifacts, ModuleRegistry modules) {
        this.artifacts = artifacts;
        this.modules = modules == null ? new ModuleRegistry() : modules;
    }

    public Result<AstDocument> parseFile(Path file) {
        String sourcePath = SourcePaths.display(file);
        List<Diagnostic> diagnostics = new ArrayList<>();

        CharStream input;

        try {
            input = CharStreams.fromPath(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            diagnostics.add(Diagnostic.error(
                    "Failed to read '" + sourcePath + "': " + e.getMessage()
            ));
            return new Result<>(null, diagnostics);
        }

        FrankoLexer lexer = new FrankoLexer(input);
        ParseErrorCollector errors = new ParseErrorCollector(sourcePath);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FrankoParser parser = new FrankoParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        ParseTree tree = parser.program();
        diagnostics.addAll(errors.diagnostics);

        int syntaxErrors = parser.getNumberOfSyntaxErrors();

        if (syntaxErrors > 0) {
            diagnostics.add(Diagnostic.error(
                    "Parse failed: " + syntaxErrors + " syntax error(s)."
            ));
        }

        if (!errors.diagnostics.isEmpty() || syntaxErrors > 0) {
            return new Result<>(null, diagnostics);
        }

        ASTNode root;

        try {
            FrankoASTVisitor visitor = new FrankoASTVisitor(sourcePath);
            root = visitor.visit(tree);
        } catch (RuntimeException e) {
            diagnostics.add(Diagnostic.error(
                    "Internal AST error in '" + sourcePath + "': " + e.getMessage()
            ));
            return new Result<>(null, diagnostics);
        }

        if (!(root instanceof ProgramNode program)) {
            diagnostics.add(Diagnostic.error(
                    "Internal AST error in '" + sourcePath
                            + "': program did not parse into a program node"
            ));
            return new Result<>(null, diagnostics);
        }

        AstDocument raw = new AstDocument(
                AstPhase.RAW,
                new SourceTable(),
                program
        );

        ModuleDescriptor module;

        try {
            module = modules.loadFor(file.toAbsolutePath().normalize());
        } catch (RuntimeException e) {
            diagnostics.add(Diagnostic.error(
                    "Module manifest error for '" + sourcePath + "': "
                            + e.getMessage()
            ));
            return new Result<>(raw, diagnostics);
        }

        if (module != null) {
            diagnostics.addAll(ModuleValidator.validate(program, module));
        }

        return new Result<>(raw, diagnostics);
    }

    public Result<AstDocument> desugar(AstDocument raw) {
        if (raw == null || raw.root == null) {
            return new Result<>(null, List.of());
        }

        DiagnosticBag diagnostics = new DiagnosticBag();
        Desugarer desugarer = new Desugarer(diagnostics);
        ASTNode root = desugarer.desugar(raw.root);

        if (!(root instanceof ProgramNode program)) {
            diagnostics.error("Internal desugarer error: program did not desugar into a program node");
            return new Result<>(null, diagnostics.diagnostics());
        }

        return new Result<>(
                new AstDocument(AstPhase.DESUGARED, raw.sources, program),
                diagnostics.diagnostics()
        );
    }

    public Result<AstDocument> loadDesugared(Path file) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Result<AstDocument> rawResult = parseFile(file);
        diagnostics.addAll(rawResult.diagnostics);

        AstDocument raw = rawResult.value;

        if (raw == null) {
            return new Result<>(null, diagnostics);
        }

        if (artifacts != null) {
            artifacts.writeAst(file, AstPhase.RAW, raw);
        }

        if (rawResult.hasErrors()) {
            return new Result<>(null, diagnostics);
        }

        Result<AstDocument> desugaredResult = desugar(raw);
        diagnostics.addAll(desugaredResult.diagnostics);

        if (desugaredResult.value != null && artifacts != null) {
            artifacts.writeAst(file, AstPhase.DESUGARED, desugaredResult.value);
        }

        return new Result<>(desugaredResult.value, diagnostics);
    }

    private static final class ParseErrorCollector extends BaseErrorListener {
        private final String sourcePath;
        private final List<Diagnostic> diagnostics = new ArrayList<>();

        ParseErrorCollector(String sourcePath) {
            this.sourcePath = sourcePath;
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
            boolean lexer = recognizer instanceof org.antlr.v4.runtime.Lexer;

            if (lexer) {
                String text = msg;
                String marker = "token recognition error at: '";
                int start = msg.indexOf(marker);

                if (start >= 0 && msg.endsWith("'")
                        && msg.length() > start + marker.length()) {
                    text = msg.substring(
                            start + marker.length(),
                            msg.length() - 1
                    );
                }

                diagnostics.add(Diagnostic.error(
                        span(line, charPositionInLine, text.length()),
                        "Lex error: \"" + text + "\" is not a recognizable token."
                ));
                return;
            }

            int length = offendingSymbol instanceof Token token
                    ? Math.max(1, token.getText().length())
                    : 1;

            diagnostics.add(Diagnostic.error(
                    span(line, charPositionInLine, length),
                    msg
            ));
        }

        private SourceSpan span(int line, int column, int length) {
            return new SourceSpan(
                    sourcePath,
                    line,
                    column + 1,
                    line,
                    column + 1 + Math.max(1, length)
            );
        }
    }
}
