import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

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
 *
 * ----------------------------------------------------------------------------
 * IMPORTANT STATE NOTE
 * ----------------------------------------------------------------------------
 *
 * Statement checking mutates VariableSymbol.deleted while validating del/use
 * rules. Therefore, MasterChecker resets all discovered VariableSymbol.deleted
 * flags before each check(...) call so repeated checks over the same Semantic AST
 * are deterministic.
 */
public class MasterChecker {

    private final SemanticAnalyzer.Context ctx;

    private final TypeChecker typeChecker;
    private final ExpressionChecker expressionChecker;
    private final DeclarationChecker declarationChecker;
    private final StatementChecker statementChecker;
    private final FunctionChecker functionChecker;

    public MasterChecker() {
        this.ctx = new SemanticAnalyzer.Context();

        this.typeChecker = new TypeChecker(ctx);

        this.expressionChecker =
                new ExpressionChecker(ctx, typeChecker);

        this.declarationChecker =
                new DeclarationChecker(ctx, expressionChecker, typeChecker);

        this.statementChecker =
                new StatementChecker(
                        ctx,
                        declarationChecker,
                        expressionChecker,
                        typeChecker
                );

        /*
         * Assumed FunctionChecker API:
         *
         *   new FunctionChecker(
         *       ctx,
         *       declarationChecker,
         *       statementChecker,
         *       expressionChecker,
         *       typeChecker
         *   )
         *
         * and:
         *
         *   functionChecker.checkFunction(SemanticFunctionDeclNode fn)
         *
         * If your FunctionChecker constructor has a different parameter order,
         * this is the only line you should need to adjust.
         */
        this.functionChecker =
                new FunctionChecker(
                        ctx,
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
         * Clear previous error state.
         *
         * The checker context is used only for diagnostics in this phase, not
         * for semantic-analysis scope management.
         */
        ctx.clear();

        /*
         * Reset checker-owned deletion state so this MasterChecker can safely
         * be reused on the same Semantic AST.
         */
        resetDeletedFlags(node);

        checkNode(node);

        if (ctx.hasErrors()) {
            throw new SemanticAnalyzer.SemanticException(formatErrors());
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

        ctx.error("Unknown SemanticASTNode type passed to MasterChecker: "
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
                ctx.error("Program contains null top-level item");
            } else {
                ctx.error("Invalid top-level item in program: "
                        + item.getClass().getSimpleName());
            }
        }
    }

    private String formatErrors() {
        StringBuilder sb = new StringBuilder("Master checking failed:\n");

        for (int i = 0; i < ctx.getErrors().size(); i++) {
            sb.append("  ")
                    .append(i + 1)
                    .append(". ")
                    .append(ctx.getErrors().get(i))
                    .append('\n');
        }

        return sb.toString();
    }

    // ============================================================
    // Deleted-State Reset
    // ============================================================

    private void resetDeletedFlags(SemanticASTNode root) {
        Set<VariableSymbol> symbols =
                Collections.newSetFromMap(new IdentityHashMap<>());

        collectSymbols(root, symbols);

        for (VariableSymbol symbol : symbols) {
            if (symbol != null) {
                symbol.deleted = false;
            }
        }
    }

    private void collectSymbols(
            SemanticASTNode node,
            Set<VariableSymbol> out
    ) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticProgramNode n) {
            for (SemanticASTNode item : n.topLevelItems) {
                collectSymbols(item, out);
            }
            return;
        }

        if (node instanceof SemanticFunctionDeclNode n) {
            /*
             * Function parameters are local variables in the function body's
             * initial scope, so their deleted flags must be reset as well.
             */
            for (VariableSymbol param : n.parameterVariables) {
                out.add(param);
            }

            /*
             * The function body may contain local declarations and references.
             */
            collectSymbols(n.body, out);
            return;
        }

        if (node instanceof SemanticBlockNode n) {
            for (SemanticStmtNode stmt : n.statements) {
                collectSymbols(stmt, out);
            }
            return;
        }

        if (node instanceof SemanticVarDeclNode n) {
            out.add(n.symbol);
            return;
        }

        if (node instanceof SemanticAssignNode n) {
            collectSymbolsFromExpr(n.target, out);
            collectSymbolsFromExpr(n.value, out);
            return;
        }

        if (node instanceof SemanticIfNode n) {
            collectSymbolsFromExpr(n.condition, out);
            collectSymbols(n.thenBranch, out);
            collectSymbols(n.elseBranch, out);
            return;
        }

        if (node instanceof SemanticWhileNode n) {
            collectSymbolsFromExpr(n.condition, out);
            collectSymbols(n.body, out);
            return;
        }

        if (node instanceof SemanticDelNode n) {
            out.add(n.symbol);
            return;
        }

        if (node instanceof SemanticPrintNode n) {
            for (SemanticExprNode arg : n.args) {
                collectSymbolsFromExpr(arg, out);
            }
            return;
        }

        if (node instanceof SemanticReturnNode n) {
            collectSymbolsFromExpr(n.value, out);
            return;
        }

        if (node instanceof SemanticExprStmtNode n) {
            collectSymbolsFromExpr(n.expr, out);
            return;
        }

        /*
         * Lvalue-based dynamic array initialization.
         *
         * Examples:
         *
         *   arr(20);
         *   deref(p)(20);
         *   arrs[i](20);
         */
        if (node instanceof SemanticArrayInitNode n) {
            collectSymbolsFromExpr(n.target, out);
            collectSymbolsFromExpr(n.size, out);
            return;
        }

        if (node instanceof SemanticArrayUninitNode n) {
            collectSymbolsFromExpr(n.receiver, out);
            return;
        }

        if (node instanceof SemanticArrayMemsetNode n) {
            collectSymbolsFromExpr(n.receiver, out);
            collectSymbolsFromExpr(n.value, out);
            return;
        }

        if (node instanceof SemanticArrayMemcpyNode n) {
            collectSymbolsFromExpr(n.target, out);
            collectSymbolsFromExpr(n.source, out);
            return;
        }

        if (node instanceof SemanticExprNode expr) {
            collectSymbolsFromExpr(expr, out);
            return;
        }
    }

    private void collectSymbolsFromExpr(
            SemanticExprNode expr,
            Set<VariableSymbol> out
    ) {
        if (expr == null) {
            return;
        }

        if (expr instanceof SemanticIntLiteralNode) {
            return;
        }

        if (expr instanceof SemanticVarExprNode n) {
            out.add(n.symbol);
            return;
        }

        if (expr instanceof SemanticUnaryOpNode n) {
            collectSymbolsFromExpr(n.expr, out);
            return;
        }

        if (expr instanceof SemanticBinOpNode n) {
            collectSymbolsFromExpr(n.left, out);
            collectSymbolsFromExpr(n.right, out);
            return;
        }

        if (expr instanceof SemanticArrayAccessNode n) {
            collectSymbolsFromExpr(n.target, out);
            collectSymbolsFromExpr(n.index, out);
            return;
        }

        if (expr instanceof SemanticFunctionCallNode n) {
            for (SemanticExprNode arg : n.args) {
                collectSymbolsFromExpr(arg, out);
            }
            return;
        }

        if (expr instanceof SemanticGetAddrNode n) {
            collectSymbolsFromExpr(n.target, out);
            return;
        }

        if (expr instanceof SemanticDerefNode n) {
            collectSymbolsFromExpr(n.expr, out);
            return;
        }
    }
}