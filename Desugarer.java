import java.util.ArrayList;
import java.util.List;

public class Desugarer {

    public ASTNode desugar(ASTNode root) {
        if (root == null) return null;

        if (root instanceof ProgramNode) {
            ProgramNode p = (ProgramNode) root;
            return new ProgramNode(desugarTopLevelItemList(p.topLevelItems));
        }

        return desugarStmt(root);
    }

    // ============================================================
    // Top-level handling
    // ============================================================

    /**
     * Desugars a list of top-level items.
     *
     * Top-level items may be:
     *   - FunctionDeclNode
     *   - statements
     *
     * Statement-level declaration sugar can still expand into multiple
     * top-level statements.
     *
     * Function declarations themselves do not expand, but their bodies
     * are recursively desugared.
     */
    private List<ASTNode> desugarTopLevelItemList(List<ASTNode> items) {
        List<ASTNode> out = new ArrayList<>();

        for (ASTNode item : items) {
            appendDesugaredTopLevelItem(item, out);
        }

        return out;
    }

    /**
     * Appends a desugared top-level item into the output list.
     *
     * Function declarations remain one top-level item.
     * Top-level statement sugar may expand into multiple statements.
     */
    private void appendDesugaredTopLevelItem(ASTNode item, List<ASTNode> out) {
        if (item == null) return;

        if (item instanceof FunctionDeclNode) {
            out.add(desugarFunctionDecl((FunctionDeclNode) item));
            return;
        }

        appendDesugaredStmt(item, out);
    }

    private FunctionDeclNode desugarFunctionDecl(FunctionDeclNode fn) {
        List<ParameterNode> parameters = new ArrayList<>();

        for (ParameterNode param : fn.parameters) {
            parameters.add(desugarParameter(param));
        }

        BlockNode body = null;
        if (fn.body != null) {
            body = new BlockNode(desugarStmtList(fn.body.statements));
        }

        return new FunctionDeclNode(
            fn.name,
            parameters,
            desugarType(fn.returnType),
            body
        );
    }

    private ParameterNode desugarParameter(ParameterNode param) {
        return new ParameterNode(
            desugarType(param.type),
            param.name
        );
    }

    // ============================================================
    // Statement-list handling
    // ============================================================

    /**
     * Desugars a list of statements, flattening declaration and initializer
     * sugar into core statements.
     *
     * Ordinary declaration initializer:
     *
     *   int32_t x = 1;
     *
     * becomes:
     *
     *   int32_t x;
     *   x = 1;
     *
     * Dynamic array init declaration sugar:
     *
     *   array<int32_t> arr(20);
     *
     * becomes:
     *
     *   array<int32_t> arr;
     *   arr(20);
     *
     * Array initializer-list declaration sugar:
     *
     *   array<int32_t, 3> xs = [1, 2, 3];
     *
     * becomes:
     *
     *   array<int32_t, 3> xs;
     *   xs[0] = 1;
     *   xs[1] = 2;
     *   xs[2] = 3;
     *
     * For dynamic arrays, no allocation/init is emitted:
     *
     *   array<int32_t> xs = [1, 2, 3];
     *
     * becomes:
     *
     *   array<int32_t> xs;
     *   xs[0] = 1;
     *   xs[1] = 2;
     *   xs[2] = 3;
     *
     * Later checkers reject this if xs was not initialized.
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
     * This is where statement sugar can expand into multiple statements.
     */
    private void appendDesugaredStmt(ASTNode stmt, List<ASTNode> out) {
        if (stmt == null) return;

        if (stmt instanceof VarDeclInitNode) {
            appendDesugaredVarDeclInit((VarDeclInitNode) stmt, out);
            return;
        }

        if (stmt instanceof VarDeclArrayInitNode) {
            appendDesugaredVarDeclArrayInit((VarDeclArrayInitNode) stmt, out);
            return;
        }

        if (stmt instanceof AssignNode) {
            appendDesugaredAssign((AssignNode) stmt, out);
            return;
        }

        if (stmt instanceof BlockNode) {
            BlockNode b = (BlockNode) stmt;
            out.add(new BlockNode(desugarStmtList(b.statements)));
            return;
        }

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

    private void appendDesugaredVarDeclInit(
        VarDeclInitNode n,
        List<ASTNode> out
    ) {
        TypeNode loweredType = desugarType(n.type);

        out.add(new VarDeclNode(
            loweredType,
            n.name,
            n.isHeap
        ));

        if (n.init == null) {
            return;
        }

        ASTNode target = new VarNode(n.name);

        if (n.init instanceof ArrayLiteralNode) {
            appendArrayInitializerAssignments(
                target,
                (ArrayLiteralNode) n.init,
                out
            );
            return;
        }

        out.add(new AssignNode(
            target,
            desugarExpr(n.init)
        ));
    }

    private void appendDesugaredVarDeclArrayInit(
        VarDeclArrayInitNode n,
        List<ASTNode> out
    ) {
        out.add(new VarDeclNode(
            desugarType(n.type),
            n.name,
            n.isHeap
        ));

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
    }

    private void appendDesugaredAssign(
        AssignNode n,
        List<ASTNode> out
    ) {
        ASTNode target = desugarLValue(n.target);

        if (n.value instanceof ArrayLiteralNode) {
            appendArrayInitializerAssignments(
                target,
                (ArrayLiteralNode) n.value,
                out
            );
            return;
        }

        out.add(new AssignNode(
            target,
            desugarExpr(n.value)
        ));
    }

    /**
     * Desugars a statement in a single-statement position such as:
     *
     *   if (...) <statement>
     *   else <statement>
     *   while (...) <statement>
     *
     * If sugar expands to multiple statements, the lowered result is wrapped
     * in a BlockNode.
     */
    private ASTNode desugarStmt(ASTNode stmt) {
        if (stmt == null) return null;

        if (stmt instanceof VarDeclInitNode) {
            List<ASTNode> lowered = new ArrayList<>();
            appendDesugaredVarDeclInit((VarDeclInitNode) stmt, lowered);
            return new BlockNode(lowered);
        }

        if (stmt instanceof VarDeclArrayInitNode) {
            List<ASTNode> lowered = new ArrayList<>();
            appendDesugaredVarDeclArrayInit((VarDeclArrayInitNode) stmt, lowered);
            return new BlockNode(lowered);
        }

        if (stmt instanceof AssignNode) {
            AssignNode n = (AssignNode) stmt;

            if (n.value instanceof ArrayLiteralNode) {
                List<ASTNode> lowered = new ArrayList<>();
                appendDesugaredAssign(n, lowered);
                return new BlockNode(lowered);
            }

            return new AssignNode(
                desugarLValue(n.target),
                desugarExpr(n.value)
            );
        }

        if (stmt instanceof ProgramNode) {
            ProgramNode p = (ProgramNode) stmt;
            return new ProgramNode(desugarTopLevelItemList(p.topLevelItems));
        }

        if (stmt instanceof FunctionDeclNode) {
            return desugarFunctionDecl((FunctionDeclNode) stmt);
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

        if (stmt instanceof ReturnNode) {
            ReturnNode n = (ReturnNode) stmt;

            return new ReturnNode(
                n.value != null ? desugarExpr(n.value) : null
            );
        }

        if (stmt instanceof DelNode) {
            return stmt;
        }

        if (stmt instanceof VarDeclNode) {
            VarDeclNode n = (VarDeclNode) stmt;

            return new VarDeclNode(
                desugarType(n.type),
                n.name,
                n.isHeap
            );
        }

        throw new RuntimeException(
            "Unsupported statement in desugarer: " + stmt.getClass().getSimpleName()
        );
    }

    // ============================================================
    // Array initializer-list lowering
    // ============================================================

    /**
     * Lowers:
     *
     *   target = [a, b, c];
     *
     * into:
     *
     *   target[0] = a;
     *   target[1] = b;
     *   target[2] = c;
     *
     * Nested initializer lists recursively lower:
     *
     *   target = [[a, b], [c, d]];
     *
     * into:
     *
     *   target[0][0] = a;
     *   target[0][1] = b;
     *   target[1][0] = c;
     *   target[1][1] = d;
     *
     * This lowering is purely syntactic.
     *
     * It does not:
     *   - check target type,
     *   - check element type,
     *   - check bounds,
     *   - allocate or initialize dynamic arrays,
     *   - resize dynamic arrays.
     */
    private void appendArrayInitializerAssignments(
        ASTNode target,
        ArrayLiteralNode literal,
        List<ASTNode> out
    ) {
        ASTNode loweredTarget = desugarLValue(target);

        for (int i = 0; i < literal.elements.size(); i++) {
            ASTNode element = literal.elements.get(i);

            ASTNode indexedTarget = new ArrayAccessNode(
                cloneLValue(loweredTarget),
                new IntNode(Integer.toString(i))
            );

            if (element instanceof ArrayLiteralNode) {
                appendArrayInitializerAssignments(
                    indexedTarget,
                    (ArrayLiteralNode) element,
                    out
                );
                continue;
            }

            out.add(new AssignNode(
                indexedTarget,
                desugarExpr(element)
            ));
        }
    }

    // ============================================================
    // Expression handling
    // ============================================================

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

        if (expr instanceof ArrayLiteralNode) {
            throw new RuntimeException(
                "ArrayLiteralNode cannot be desugared as an ordinary expression"
            );
        }

        throw new RuntimeException(
            "Unsupported expression in desugarer: " + expr.getClass().getSimpleName()
        );
    }

    // ============================================================
    // Constant-expression handling
    // ============================================================

    /**
     * Structurally desugars a constExpr AST.
     *
     * Used for static array size expressions.
     */
    private ASTNode desugarConstExpr(ASTNode expr) {
        if (expr == null) return null;

        if (expr instanceof IntNode) {
            return expr;
        }

        if (expr instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) expr;

            return new UnaryOpNode(
                n.op,
                desugarConstExpr(n.expr)
            );
        }

        if (expr instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) expr;

            return new BinOpNode(
                n.op,
                desugarConstExpr(n.left),
                desugarConstExpr(n.right)
            );
        }

        throw new RuntimeException(
            "Unsupported constant expression in desugarer: "
            + expr.getClass().getSimpleName()
        );
    }

    // ============================================================
    // Lvalue handling
    // ============================================================

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

    private ASTNode cloneLValue(ASTNode node) {
        if (node == null) return null;

        if (node instanceof VarNode) {
            VarNode n = (VarNode) node;
            return new VarNode(n.name);
        }

        if (node instanceof DerefNode) {
            DerefNode n = (DerefNode) node;
            return new DerefNode(desugarExpr(n.expr));
        }

        if (node instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) node;

            return new ArrayAccessNode(
                cloneLValue(n.target),
                desugarExpr(n.index)
            );
        }

        if (node instanceof MemberAccessNode) {
            MemberAccessNode n = (MemberAccessNode) node;

            return new MemberAccessNode(
                cloneLValue(n.target),
                n.memberName
            );
        }

        throw new RuntimeException(
            "Unsupported lvalue clone in desugarer: "
            + node.getClass().getSimpleName()
        );
    }

    // ============================================================
    // Type handling
    // ============================================================

    private TypeNode desugarType(TypeNode type) {
        if (type == null) return null;

        if (type instanceof PrimitiveTypeNode) {
            return type;
        }

        if (type instanceof VoidTypeNode) {
            return type;
        }

        if (type instanceof DynamicArrayTypeNode) {
            DynamicArrayTypeNode n = (DynamicArrayTypeNode) type;

            return new DynamicArrayTypeNode(
                desugarType(n.elementType)
            );
        }

        if (type instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode n = (StaticArrayTypeNode) type;

            return new StaticArrayTypeNode(
                desugarType(n.elementType),
                desugarConstExpr(n.sizeExpr)
            );
        }

        if (type instanceof AddrTypeNode) {
            AddrTypeNode n = (AddrTypeNode) type;

            return new AddrTypeNode(
                desugarType(n.referencedType)
            );
        }

        throw new RuntimeException(
            "Unsupported type in desugarer: " + type.getClass().getSimpleName()
        );
    }
}
