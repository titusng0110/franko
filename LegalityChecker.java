import java.util.Objects;

/**
 * ============================================================================
 * MASTER CHECKER
 * ============================================================================
 *
 * PURPOSE:
 * MasterChecker orchestrates the Franko legality checking phase over the fully
 * lowered, symbol-resolved, type-decorated Semantic AST.
 *
 * The SemanticAnalyzer has already produced a SemanticASTNode tree where:
 *
 *   - variable names are resolved to VariableSymbol objects,
 *   - function declarations are resolved to FunctionSymbol objects,
 *   - function calls are resolved to selected overload FunctionSymbol objects,
 *   - types are mechanically inferred/decorated,
 *   - integer constants are folded where possible,
 *   - array intrinsic syntax is lowered into dedicated semantic nodes,
 *   - structural lvalue checks required for lowering have already happened.
 *
 * MasterChecker coordinates the stricter legality checking passes:
 *
 *   - DeclarationChecker validates declared types,
 *   - ExpressionChecker validates expression/operator/address/index/call rules,
 *   - StatementChecker validates statement-level rules and array intrinsics,
 *   - FunctionChecker validates function-level rules,
 *   - TypeChecker provides shared type-system utilities.
 *
 * This checker reports all accumulated legality errors at the end of the pass.
 */
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

        /*
         * Option B:
         *
         * The legality-checking phase now uses DiagnosticBag directly.
         *
         * These constructors assume the sub-checkers are refactored from:
         *
         *   SomeChecker(SymbolTable ctx, ...)
         *
         * to:
         *
         *   SomeChecker(DiagnosticBag diagnostics, ...)
         *
         * If the sub-checkers have not been refactored yet, these lines are
         * expected to be the compile points that guide the next migration step.
         */
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

    /**
     * Entry point to validate the semantic legality of the lowered program.
     *
     * @param node The fully inferred Semantic AST from SemanticAnalyzer.
     */
    public void check(SemanticASTNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cannot check a null AST.");
        }

        /*
         * Clear previous checker diagnostics.
         *
         * DiagnosticBag is now checker-phase-local state. It does not own
         * scopes, function overload tables, or semantic-analysis registration
         * metadata.
         */
        diagnostics.clear();

        checkNode(node);

        if (diagnostics.hasErrors()) {
            throw new SemanticAnalyzer.SemanticException(
                    diagnostics.formatErrors()
            );
        }
    }

    // ============================================================
    // Top-Level Dispatch
    // ============================================================

    private void checkNode(SemanticASTNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticProgramNode program) {
            checkProgram(program);
            return;
        }

        if (node instanceof SemanticFunctionDeclNode fn) {
            functionChecker.checkFunction(fn);
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
            if (item instanceof SemanticFunctionDeclNode fn) {
                functionChecker.checkFunction(fn);
                continue;
            }

            if (item instanceof SemanticStmtNode stmt) {
                statementChecker.checkStmt(stmt);
                continue;
            }

            if (item instanceof SemanticExprNode expr) {
                /*
                 * This should not normally occur in a lowered ProgramNode,
                 * because expressions should be wrapped in SemanticExprStmtNode
                 * when used as statements. Still, handling it here makes the
                 * checker defensive for manually-created test ASTs.
                 */
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