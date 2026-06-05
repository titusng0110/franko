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
 *   - types are mechanically inferred/decorated,
 *   - integer constants are folded where possible,
 *   - array intrinsic syntax is lowered into dedicated semantic nodes,
 *   - structural lvalue checks required for lowering have already happened.
 *
 * MasterChecker coordinates the stricter legality checking passes:
 *
 *   - DeclarationChecker validates declared types,
 *   - ExpressionChecker validates expression/operator/address/index rules,
 *   - StatementChecker validates statement-level rules and array intrinsics,
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
 *
 * ============================================================================
 */
public class MasterChecker {

    private final SemanticAnalyzer.Context ctx;
    private final TypeChecker typeChecker;
    private final ExpressionChecker expressionChecker;
    private final DeclarationChecker declarationChecker;
    private final StatementChecker statementChecker;

    public MasterChecker() {
        this.ctx = new SemanticAnalyzer.Context();

        this.typeChecker = new TypeChecker(ctx);
        this.expressionChecker = new ExpressionChecker(ctx, typeChecker);
        this.declarationChecker =
            new DeclarationChecker(ctx, expressionChecker, typeChecker);
        this.statementChecker =
            new StatementChecker(ctx, declarationChecker, expressionChecker, typeChecker);
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
         * for scope management.
         */
        ctx.clear();

        /*
         * Reset checker-owned deletion state so this MasterChecker can safely
         * be reused on the same Semantic AST.
         */
        resetDeletedFlags(node);

        if (node instanceof SemanticProgramNode program) {
            for (SemanticStmtNode stmt : program.statements) {
                statementChecker.checkStmt(stmt);
            }
        } else if (node instanceof SemanticStmtNode stmt) {
            statementChecker.checkStmt(stmt);
        } else if (node instanceof SemanticExprNode expr) {
            expressionChecker.checkExpr(expr);
        } else {
            ctx.error("Unknown SemanticASTNode type passed to MasterChecker: "
                + node.getClass().getSimpleName());
        }

        if (ctx.hasErrors()) {
            throw new SemanticAnalyzer.SemanticException(formatErrors());
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
            for (SemanticStmtNode stmt : n.statements) {
                collectSymbols(stmt, out);
            }
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

        if (node instanceof SemanticExprStmtNode n) {
            collectSymbolsFromExpr(n.expr, out);
            return;
        }

        if (node instanceof SemanticArrayInitNode n) {
            out.add(n.symbol);
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

        if (expr instanceof SemanticGetAddrNode n) {
            collectSymbolsFromExpr(n.target, out);
            return;
        }

        if (expr instanceof SemanticDerefNode n) {
            collectSymbolsFromExpr(n.expr, out);
        }
    }
}