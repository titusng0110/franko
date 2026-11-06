import java.util.Objects;

public class LegalityChecker {

    private final DiagnosticBag diagnostics;

    private final TypeChecker typeChecker;
    private final ExpressionChecker expressionChecker;
    private final DeclarationChecker declarationChecker;
    private final StatementChecker statementChecker;
    private final FunctionChecker functionChecker;

    public LegalityChecker() {
        this(new DiagnosticBag());
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

    public void check(SastNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cannot check a null AST.");
        }

        diagnostics.clear();

        checkNode(node);
    }

    public PassResult check(SastDocument document) {
        check(document.root);
        return new PassResult(
                document.withPhase(SastPhase.LEGALITY),
                diagnostics.diagnostics()
        );
    }

    private void checkNode(SastNode node) {
        diagnostics.push(node == null ? SourceSpan.NONE : node.span);
        try {
            checkNodeInner(node);
        } finally {
            diagnostics.pop();
        }
    }

    private void checkNodeInner(SastNode node) {
        if (node == null) {
            return;
        }

        if (node.kind == SastKind.PROGRAM) {
            checkProgram(node);
            return;
        }

        if (node.kind == SastKind.NAMESPACE) {
            for (SastNode item : node.items) {
                checkNode(item);
            }
            return;
        }

        if (node.kind == SastKind.FUNCTION_DECL) {
            functionChecker.checkFunction(node);
            return;
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            declarationChecker.checkStructDecl(node);

            for (SastNode method : node.methods) {
                functionChecker.checkFunction(method);
            }

            return;
        }

        if (node.kind == SastKind.ENUM_DECL) {
            return;
        }

        if (node.isStmt()) {
            statementChecker.checkStmt(node);
            return;
        }

        if (node.isExpr()) {
            expressionChecker.checkExpr(node);
            return;
        }

        diagnostics.error("Unknown SastNode type passed to MasterChecker: "
                + node.getClass().getSimpleName());
    }

    private void checkProgram(SastNode program) {
        for (SastNode item : program.topLevelItems) {
            if (item == null) {
                diagnostics.error("Program contains null top-level item");
                continue;
            }

            if (item.kind == SastKind.NAMESPACE) {
                for (SastNode namespaceItem : item.items) {
                    checkNode(namespaceItem);
                }
                continue;
            }

            if (item.kind == SastKind.FUNCTION_DECL) {
                functionChecker.checkFunction(item);
                continue;
            }

            if (item.kind == SastKind.STRUCT_DECL) {
                declarationChecker.checkStructDecl(item);

                for (SastNode method : item.methods) {
                    functionChecker.checkFunction(method);
                }

                continue;
            }

            if (item.kind == SastKind.ENUM_DECL) {
                continue;
            }

            if (item.isStmt()) {
                statementChecker.checkStmt(item);
                continue;
            }

            if (item.isExpr()) {

                expressionChecker.checkExpr(item);
                continue;
            }

            diagnostics.error("Invalid top-level item in program: "
                    + item.kind.jsonName());
        }
    }
}
