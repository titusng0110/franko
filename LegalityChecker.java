import java.util.Objects;

public class LegalityChecker {

    private final DiagnosticBag diagnostics;

    private final TypeChecker typeChecker;
    private final ExpressionChecker expressionChecker;
    private final DeclarationChecker declarationChecker;
    private final StatementChecker statementChecker;
    private final FunctionChecker functionChecker;

    public LegalityChecker() {
        this(new DiagnosticBag("Master checking failed:"));
    }

    public LegalityChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);

        this.typeChecker = new TypeChecker(diagnostics);

        this.expressionChecker =
                new ExpressionChecker(diagnostics, typeChecker);

        this.declarationChecker =
                new DeclarationChecker(
                        diagnostics,
                        expressionChecker,
                        typeChecker
                );

        this.statementChecker =
                new StatementChecker(
                        diagnostics,
                        declarationChecker,
                        expressionChecker,
                        typeChecker
                );

        this.functionChecker =
                new FunctionChecker(
                        diagnostics,
                        declarationChecker,
                        statementChecker,
                        expressionChecker,
                        typeChecker
                );
    }

    public void check(SemanticASTNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cannot check a null AST.");
        }

        diagnostics.clear();

        checkNode(node);

        if (diagnostics.hasErrors()) {
            throw new SemanticAnalyzer.SemanticException(
                    diagnostics.formatErrors()
            );
        }
    }

    private void checkNode(SemanticASTNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticProgramNode program) {
            checkProgram(program);
            return;
        }

        if (node instanceof SemanticNamespaceNode namespace) {
            for (SemanticASTNode item : namespace.items) {
                checkNode(item);
            }
            return;
        }

        if (node instanceof SemanticFunctionDeclNode fn) {
            functionChecker.checkFunction(fn);
            return;
        }

        if (node instanceof SemanticStructDeclNode struct) {
            declarationChecker.checkStructDecl(struct);

            for (SemanticFunctionDeclNode method : struct.methods) {
                functionChecker.checkFunction(method);
            }

            return;
        }

        if (node instanceof SemanticStmtNode stmt) {
            statementChecker.checkStmt(stmt);
            return;
        }

        if (node instanceof SemanticExprNode expr) {
            expressionChecker.checkExpr(expr);
            return;
        }

        diagnostics.error("Unknown SemanticASTNode type passed to MasterChecker: "
                + node.getClass().getSimpleName());
    }

    private void checkProgram(SemanticProgramNode program) {
        for (SemanticASTNode item : program.topLevelItems) {
            if (item instanceof SemanticNamespaceNode namespace) {
                for (SemanticASTNode namespaceItem : namespace.items) {
                    checkNode(namespaceItem);
                }
                continue;
            }

            if (item instanceof SemanticFunctionDeclNode fn) {
                functionChecker.checkFunction(fn);
                continue;
            }

            if (item instanceof SemanticStructDeclNode struct) {
                declarationChecker.checkStructDecl(struct);

                for (SemanticFunctionDeclNode method : struct.methods) {
                    functionChecker.checkFunction(method);
                }

                continue;
            }

            if (item instanceof SemanticStmtNode stmt) {
                statementChecker.checkStmt(stmt);
                continue;
            }

            if (item instanceof SemanticExprNode expr) {

                expressionChecker.checkExpr(expr);
                continue;
            }

            if (item == null) {
                diagnostics.error("Program contains null top-level item");
            } else {
                diagnostics.error("Invalid top-level item in program: "
                        + item.getClass().getSimpleName());
            }
        }
    }
}
