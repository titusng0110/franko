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
     * Desugars a list of statements, flattening declaration sugar into core statements.
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
     * This is where declaration sugar can expand into multiple statements.
     */
    private void appendDesugaredStmt(ASTNode stmt, List<ASTNode> out) {
        if (stmt == null) return;

        // Sugar: int32_t x = 1;
        // becomes:
        //   int32_t x;
        //   x = 1;
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
        // becomes:
        //   array<int32_t> arr;
        //   arr(20);
        //
        // Since call syntax is now a normal expression,
        // this lowers to an ExprStmtNode(CallNode(...)).
        if (stmt instanceof VarDeclArrayInitNode) {
            VarDeclArrayInitNode n = (VarDeclArrayInitNode) stmt;

            out.add(new VarDeclNode(n.type, n.name, n.isHeap));

            if (n.size != null) {
                List<ASTNode> args = new ArrayList<>();
                args.add(desugarExpr(n.size));

                out.add(new ExprStmtNode(
                    new CallNode(
                        new VarNode(n.name),
                        args
                    )
                ));
            }
            return;
        }

        // Blocks recursively flatten statement sugar inside them.
        if (stmt instanceof BlockNode) {
            BlockNode b = (BlockNode) stmt;
            out.add(new BlockNode(desugarStmtList(b.statements)));
            return;
        }

        // If/else single-statement positions may require wrapping
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

        out.add(desugarStmt(stmt));
    }

    /**
     * Desugars a statement in a single-statement position such as:
     *   if (...) <statement>
     *   else <statement>
     *   while (...) <statement>
     *
     * If sugar expands to multiple statements, wrap the lowered result in a BlockNode.
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
                List<ASTNode> args = new ArrayList<>();
                args.add(desugarExpr(n.size));

                lowered.add(new ExprStmtNode(
                    new CallNode(
                        new VarNode(n.name),
                        args
                    )
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

        if (stmt instanceof ExprStmtNode) {
            ExprStmtNode n = (ExprStmtNode) stmt;
            return new ExprStmtNode(desugarExpr(n.expr));
        }

        if (stmt instanceof PrintNode) {
            PrintNode n = (PrintNode) stmt;
            List<ASTNode> args = new ArrayList<>();

            for (ASTNode arg : n.args) {
                args.add(desugarExpr(arg));
            }

            return new PrintNode(args);
        }

        if (stmt instanceof DelNode) {
            return stmt;
        }

        if (stmt instanceof VarDeclNode) {
            return stmt;
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
            return new UnaryOpNode(
                n.op,
                desugarExpr(n.expr)
            );
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
                desugarExpr(n.target),
                desugarExpr(n.index)
            );
        }

        if (expr instanceof MemberAccessNode) {
            MemberAccessNode n = (MemberAccessNode) expr;
            return new MemberAccessNode(
                desugarExpr(n.target),
                n.memberName
            );
        }

        if (expr instanceof CallNode) {
            CallNode n = (CallNode) expr;

            List<ASTNode> args = new ArrayList<>();
            for (ASTNode arg : n.args) {
                args.add(desugarExpr(arg));
            }

            return new CallNode(
                desugarExpr(n.callee),
                args
            );
        }

        if (expr instanceof GetAddrNode) {
            GetAddrNode n = (GetAddrNode) expr;
            return new GetAddrNode(
                desugarLValue(n.target)
            );
        }

        if (expr instanceof DerefNode) {
            DerefNode n = (DerefNode) expr;
            return new DerefNode(
                desugarExpr(n.expr)
            );
        }

        throw new RuntimeException(
            "Unsupported expression in desugarer: " + expr.getClass().getSimpleName()
        );
    }

    private ASTNode desugarLValue(ASTNode node) {
        if (node == null) return null;

        if (node instanceof VarNode) {
            return node;
        }

        if (node instanceof DerefNode) {
            DerefNode n = (DerefNode) node;
            return new DerefNode(
                desugarExpr(n.expr)
            );
        }

        if (node instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) node;
            return new ArrayAccessNode(
                desugarLValue(n.target),
                desugarExpr(n.index)
            );
        }

        if (node instanceof MemberAccessNode) {
            MemberAccessNode n = (MemberAccessNode) node;
            return new MemberAccessNode(
                desugarLValue(n.target),
                n.memberName
            );
        }

        throw new RuntimeException(
            "Unsupported lvalue in desugarer: " + node.getClass().getSimpleName()
        );
    }
}
