import java.util.ArrayList;
import java.util.List;

public class Desugarer {

    public ASTNode desugar(ASTNode root) {
        if (root == null) return null;

        if (root instanceof ProgramNode) {
            ProgramNode p = (ProgramNode) root;
            return new ProgramNode(desugarStmtList(p.statements));
        }

        return desugarStmt(root);
    }

    /**
     * Desugars a list of statements, flattening top-level or block-level
     * declaration sugar into core statements.
     *
     * Examples:
     *
     *   int32_t x = 1;
     * becomes:
     *   int32_t x;
     *   x = 1;
     *
     *   array<int32_t> arr(20);
     * becomes:
     *   array<int32_t> arr;
     *   arr(20);
     */
    private List<ASTNode> desugarStmtList(List<ASTNode> stmts) {
        List<ASTNode> out = new ArrayList<>();

        for (ASTNode stmt : stmts) {
            appendDesugaredStmt(stmt, out);
        }

        return out;
    }

    /**
     * Appends a desugared statement into an output statement list.
     *
     * This is where we FLATTEN declaration sugar into multiple statements.
     */
    private void appendDesugaredStmt(ASTNode stmt, List<ASTNode> out) {
        if (stmt == null) return;

        // Sugar: int32_t x = 1;
        if (stmt instanceof VarDeclInitNode) {
            VarDeclInitNode n = (VarDeclInitNode) stmt;

            out.add(new VarDeclNode(n.type, n.name, n.isHeap));

            if (n.init != null) {
                out.add(new AssignNode(
                    new VarNode(n.name),
                    desugarExpr(n.init)
                ));
            }
            return;
        }

        // Sugar: array<int32_t> arr(20);
        //        alloc array<int32_t> arr(20);
        if (stmt instanceof VarDeclArrayInitNode) {
            VarDeclArrayInitNode n = (VarDeclArrayInitNode) stmt;

            out.add(new VarDeclNode(n.type, n.name, n.isHeap));

            if (n.size != null) {
                out.add(new ArrayInitNode(
                    n.name,
                    desugarExpr(n.size)
                ));
            }
            return;
        }

        // Blocks need recursive statement-list desugaring
        if (stmt instanceof BlockNode) {
            BlockNode b = (BlockNode) stmt;
            out.add(new BlockNode(desugarStmtList(b.statements)));
            return;
        }

        // If / else bodies are single-statement positions
        if (stmt instanceof IfNode) {
            IfNode n = (IfNode) stmt;
            out.add(new IfNode(
                desugarExpr(n.condition),
                desugarStmt(n.thenBranch),
                n.elseBranch != null ? desugarStmt(n.elseBranch) : null
            ));
            return;
        }

        if (stmt instanceof WhileNode) {
            WhileNode n = (WhileNode) stmt;
            out.add(new WhileNode(
                desugarExpr(n.condition),
                desugarStmt(n.body)
            ));
            return;
        }

        // Ordinary statement: recursively rewrite children if needed
        out.add(desugarStmt(stmt));
    }

    /**
     * Desugars a statement in a SINGLE-STMT position, such as:
     * - if (...) <statement>
     * - else <statement>
     * - while (...) <statement>
     *
     * If the statement is sugar that expands to multiple statements,
     * wrap the lowered result in a BlockNode.
     */
    private ASTNode desugarStmt(ASTNode stmt) {
        if (stmt == null) return null;

        // Sugar in single-statement position:
        //   if (cond) int32_t x = 1;
        // becomes:
        //   if (cond) { int32_t x; x = 1; }
        if (stmt instanceof VarDeclInitNode) {
            VarDeclInitNode n = (VarDeclInitNode) stmt;

            List<ASTNode> lowered = new ArrayList<>();
            lowered.add(new VarDeclNode(n.type, n.name, n.isHeap));

            if (n.init != null) {
                lowered.add(new AssignNode(
                    new VarNode(n.name),
                    desugarExpr(n.init)
                ));
            }

            return new BlockNode(lowered);
        }

        // Sugar in single-statement position:
        //   if (cond) array<int32_t> arr(20);
        // becomes:
        //   if (cond) { array<int32_t> arr; arr(20); }
        if (stmt instanceof VarDeclArrayInitNode) {
            VarDeclArrayInitNode n = (VarDeclArrayInitNode) stmt;

            List<ASTNode> lowered = new ArrayList<>();
            lowered.add(new VarDeclNode(n.type, n.name, n.isHeap));

            if (n.size != null) {
                lowered.add(new ArrayInitNode(
                    n.name,
                    desugarExpr(n.size)
                ));
            }

            return new BlockNode(lowered);
        }

        if (stmt instanceof ProgramNode) {
            ProgramNode p = (ProgramNode) stmt;
            return new ProgramNode(desugarStmtList(p.statements));
        }

        if (stmt instanceof BlockNode) {
            BlockNode b = (BlockNode) stmt;
            return new BlockNode(desugarStmtList(b.statements));
        }

        if (stmt instanceof IfNode) {
            IfNode n = (IfNode) stmt;
            return new IfNode(
                desugarExpr(n.condition),
                desugarStmt(n.thenBranch),
                n.elseBranch != null ? desugarStmt(n.elseBranch) : null
            );
        }

        if (stmt instanceof WhileNode) {
            WhileNode n = (WhileNode) stmt;
            return new WhileNode(
                desugarExpr(n.condition),
                desugarStmt(n.body)
            );
        }

        if (stmt instanceof AssignNode) {
            AssignNode n = (AssignNode) stmt;
            return new AssignNode(
                desugarLValue(n.target),
                desugarExpr(n.value)
            );
        }

        if (stmt instanceof ArrayInitNode) {
            ArrayInitNode n = (ArrayInitNode) stmt;
            return new ArrayInitNode(n.name, desugarExpr(n.size));
        }

        if (stmt instanceof ArrayUninitNode) {
            return stmt;
        }

        if (stmt instanceof ArrayMemsetNode) {
            ArrayMemsetNode n = (ArrayMemsetNode) stmt;
            return new ArrayMemsetNode(n.name, desugarExpr(n.value));
        }

        if (stmt instanceof ArrayMemcpyNode) {
            return stmt;
        }

        if (stmt instanceof DelNode) {
            return stmt;
        }

        if (stmt instanceof PrintNode) {
            PrintNode n = (PrintNode) stmt;
            List<ASTNode> args = new ArrayList<>();
            for (ASTNode arg : n.args) {
                args.add(desugarExpr(arg));
            }
            return new PrintNode(args);
        }

        if (stmt instanceof VarDeclNode) {
            return stmt;
        }

        // Expression statements
        if (stmt instanceof IntNode
                || stmt instanceof VarNode
                || stmt instanceof UnaryOpNode
                || stmt instanceof BinOpNode
                || stmt instanceof ArrayAccessNode) {
            return desugarExpr(stmt);
        }

        throw new RuntimeException(
            "Unsupported statement in desugarer: " + stmt.getClass().getSimpleName()
        );
    }

    private ASTNode desugarExpr(ASTNode expr) {
        if (expr == null) return null;

        if (expr instanceof IntNode) {
            return expr;
        }

        if (expr instanceof VarNode) {
            return expr;
        }

        if (expr instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) expr;
            return new UnaryOpNode(n.op, desugarExpr(n.expr));
        }

        if (expr instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) expr;
            return new BinOpNode(
                n.op,
                desugarExpr(n.left),
                desugarExpr(n.right)
            );
        }

        if (expr instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) expr;
            return new ArrayAccessNode(
                n.name,
                desugarExpr(n.index)
            );
        }

        throw new RuntimeException(
            "Unsupported expression in desugarer: " + expr.getClass().getSimpleName()
        );
    }

    private ASTNode desugarLValue(ASTNode node) {
        if (node instanceof VarNode) {
            return node;
        }

        if (node instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) node;
            return new ArrayAccessNode(n.name, desugarExpr(n.index));
        }

        throw new RuntimeException(
            "Unsupported lvalue in desugarer: " + node.getClass().getSimpleName()
        );
    }
}