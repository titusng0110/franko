import java.util.ArrayList;
import java.util.List;

// ================= VISITOR =================

public class FrankoASTVisitor extends FrankoBaseVisitor<ASTNode> {

    // ============================================================
    // Program / Statements
    // ============================================================

    @Override
    public ASTNode visitProgram(FrankoParser.ProgramContext ctx) {
        List<ASTNode> statements = new ArrayList<>();

        for (FrankoParser.StatementContext stmt : ctx.statement()) {
            ASTNode node = visit(stmt);
            if (node != null) {
                statements.add(node);
            }
        }

        return new ProgramNode(statements);
    }

    @Override
    public ASTNode visitSimpleStatement(FrankoParser.SimpleStatementContext ctx) {
        return visit(ctx.simpleStmt());
    }

    @Override
    public ASTNode visitIfStatement(FrankoParser.IfStatementContext ctx) {
        return visit(ctx.ifStmt());
    }

    @Override
    public ASTNode visitWhileStatement(FrankoParser.WhileStatementContext ctx) {
        return visit(ctx.whileStmt());
    }

    @Override
    public ASTNode visitBlockStatement(FrankoParser.BlockStatementContext ctx) {
        return visit(ctx.block());
    }

    @Override
    public ASTNode visitSimpleStmt(FrankoParser.SimpleStmtContext ctx) {
        if (ctx.varDecl() != null) return visit(ctx.varDecl());
        if (ctx.delStmt() != null) return visit(ctx.delStmt());
        if (ctx.assignStmt() != null) return visit(ctx.assignStmt());
        if (ctx.arrayInitStmt() != null) return visit(ctx.arrayInitStmt());
        if (ctx.arrayUninitStmt() != null) return visit(ctx.arrayUninitStmt());
        if (ctx.arrayMemsetStmt() != null) return visit(ctx.arrayMemsetStmt());
        if (ctx.arrayMemcpyStmt() != null) return visit(ctx.arrayMemcpyStmt());
        if (ctx.printStmt() != null) return visit(ctx.printStmt());
        if (ctx.exprStmt() != null) return visit(ctx.exprStmt());

        return null;
    }

    @Override
    public ASTNode visitBlock(FrankoParser.BlockContext ctx) {
        List<ASTNode> statements = new ArrayList<>();

        for (FrankoParser.StatementContext stmt : ctx.statement()) {
            ASTNode node = visit(stmt);
            if (node != null) {
                statements.add(node);
            }
        }

        return new BlockNode(statements);
    }

    @Override
    public ASTNode visitIfStmt(FrankoParser.IfStmtContext ctx) {
        ASTNode condition = visit(ctx.expr());
        ASTNode thenBranch = visit(ctx.statement(0));
        ASTNode elseBranch = null;

        if (ctx.statement().size() > 1) {
            elseBranch = visit(ctx.statement(1));
        }

        return new IfNode(condition, thenBranch, elseBranch);
    }

    @Override
    public ASTNode visitWhileStmt(FrankoParser.WhileStmtContext ctx) {
        ASTNode condition = visit(ctx.expr());
        ASTNode body = visit(ctx.statement());

        return new WhileNode(condition, body);
    }

    // ============================================================
    // Type visitors
    // ============================================================

    @Override
    public ASTNode visitInt8Type(FrankoParser.Int8TypeContext ctx) {
        return new PrimitiveTypeNode(PrimitiveKind.INT8);
    }

    @Override
    public ASTNode visitInt16Type(FrankoParser.Int16TypeContext ctx) {
        return new PrimitiveTypeNode(PrimitiveKind.INT16);
    }

    @Override
    public ASTNode visitInt32Type(FrankoParser.Int32TypeContext ctx) {
        return new PrimitiveTypeNode(PrimitiveKind.INT32);
    }

    @Override
    public ASTNode visitInt64Type(FrankoParser.Int64TypeContext ctx) {
        return new PrimitiveTypeNode(PrimitiveKind.INT64);
    }

    @Override
    public ASTNode visitUint8Type(FrankoParser.Uint8TypeContext ctx) {
        return new PrimitiveTypeNode(PrimitiveKind.UINT8);
    }

    @Override
    public ASTNode visitUint16Type(FrankoParser.Uint16TypeContext ctx) {
        return new PrimitiveTypeNode(PrimitiveKind.UINT16);
    }

    @Override
    public ASTNode visitUint32Type(FrankoParser.Uint32TypeContext ctx) {
        return new PrimitiveTypeNode(PrimitiveKind.UINT32);
    }

    @Override
    public ASTNode visitUint64Type(FrankoParser.Uint64TypeContext ctx) {
        return new PrimitiveTypeNode(PrimitiveKind.UINT64);
    }

    @Override
    public ASTNode visitDynamicArrayType(FrankoParser.DynamicArrayTypeContext ctx) {
        TypeNode elemType = (TypeNode) visit(ctx.type());
        return new DynamicArrayTypeNode(elemType);
    }

    @Override
    public ASTNode visitStaticArrayType(FrankoParser.StaticArrayTypeContext ctx) {
        TypeNode elemType = (TypeNode) visit(ctx.type());
        String sizeLiteral = ctx.integerLiteral().getText();
        return new StaticArrayTypeNode(elemType, sizeLiteral);
    }

    // ============================================================
    // Declarations
    // ============================================================

    @Override
    public ASTNode visitVarDecl(FrankoParser.VarDeclContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        TypeNode type = (TypeNode) visit(ctx.type());
        boolean isHeap = ctx.ALLOC() != null;

        if (ctx.declSuffix() == null) {
            return new VarDeclNode(type, name, isHeap);
        }

        if (ctx.declSuffix() instanceof FrankoParser.DeclAssignInitContext) {
            ASTNode init = visit(((FrankoParser.DeclAssignInitContext) ctx.declSuffix()).expr());
            return new VarDeclInitNode(type, name, isHeap, init);
        }

        if (ctx.declSuffix() instanceof FrankoParser.DeclArrayInitContext) {
            ASTNode size = visit(((FrankoParser.DeclArrayInitContext) ctx.declSuffix()).expr());
            return new VarDeclArrayInitNode(type, name, isHeap, size);
        }

        throw new RuntimeException("Unknown declSuffix: " + ctx.declSuffix().getText());
    }

    // ============================================================
    // Statements
    // ============================================================

    @Override
    public ASTNode visitAssignStmt(FrankoParser.AssignStmtContext ctx) {
        ASTNode target = visit(ctx.lvalue());
        ASTNode value = visit(ctx.expr());
        return new AssignNode(target, value);
    }

    @Override
    public ASTNode visitDelStmt(FrankoParser.DelStmtContext ctx) {
        return new DelNode(ctx.IDENTIFIER().getText());
    }

    @Override
    public ASTNode visitArrayInitStmt(FrankoParser.ArrayInitStmtContext ctx) {
        return new ArrayInitNode(
            ctx.IDENTIFIER().getText(),
            visit(ctx.expr())
        );
    }

    @Override
    public ASTNode visitArrayUninitStmt(FrankoParser.ArrayUninitStmtContext ctx) {
        return new ArrayUninitNode(
            visit(ctx.receiverExpr())
        );
    }

    @Override
    public ASTNode visitArrayMemsetStmt(FrankoParser.ArrayMemsetStmtContext ctx) {
        return new ArrayMemsetNode(
            visit(ctx.receiverExpr()),
            visit(ctx.expr())
        );
    }

    @Override
    public ASTNode visitArrayMemcpyStmt(FrankoParser.ArrayMemcpyStmtContext ctx) {
        return new ArrayMemcpyNode(
            visit(ctx.receiverExpr(0)),
            visit(ctx.receiverExpr(1))
        );
    }

    @Override
    public ASTNode visitPrintStmt(FrankoParser.PrintStmtContext ctx) {
        List<ASTNode> args = new ArrayList<>();

        if (ctx.exprList() != null) {
            for (FrankoParser.ExprContext exprCtx : ctx.exprList().expr()) {
                args.add(visit(exprCtx));
            }
        }

        return new PrintNode(args);
    }

    // ============================================================
    // Receiver / Lvalue / Postfix expressions
    // ============================================================

    /**
     * Builds nested ArrayAccessNode chains from receiverExpr.
     *
     * Examples:
     *   arr       -> VarNode("arr")
     *   arr[i]    -> ArrayAccessNode(VarNode("arr"), i)
     *   map[r][c] -> ArrayAccessNode(ArrayAccessNode(VarNode("map"), r), c)
     */
    @Override
    public ASTNode visitReceiverExpr(FrankoParser.ReceiverExprContext ctx) {
        ASTNode current = new VarNode(ctx.IDENTIFIER().getText());

        for (FrankoParser.ExprContext indexCtx : ctx.expr()) {
            current = new ArrayAccessNode(current, visit(indexCtx));
        }

        return current;
    }

    @Override
    public ASTNode visitLvalue(FrankoParser.LvalueContext ctx) {
        ASTNode current = new VarNode(ctx.IDENTIFIER().getText());

        for (FrankoParser.ExprContext indexCtx : ctx.expr()) {
            current = new ArrayAccessNode(current, visit(indexCtx));
        }

        return current;
    }

    @Override
    public ASTNode visitExprStmt(FrankoParser.ExprStmtContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public ASTNode visitPostfixExprOnly(FrankoParser.PostfixExprOnlyContext ctx) {
        return visit(ctx.postfixExpr());
    }

    @Override
    public ASTNode visitPostfixExpr(FrankoParser.PostfixExprContext ctx) {
        ASTNode current = visit(ctx.primary());

        for (FrankoParser.ExprContext indexCtx : ctx.expr()) {
            current = new ArrayAccessNode(current, visit(indexCtx));
        }

        return current;
    }

    @Override
    public ASTNode visitPrimary(FrankoParser.PrimaryContext ctx) {
        if (ctx.integerLiteral() != null) {
            return visit(ctx.integerLiteral());
        }

        if (ctx.IDENTIFIER() != null) {
            return new VarNode(ctx.IDENTIFIER().getText());
        }

        return visit(ctx.expr());
    }

    @Override
    public ASTNode visitIntegerLiteral(FrankoParser.IntegerLiteralContext ctx) {
        return new IntNode(ctx.getText());
    }

    // ============================================================
    // Expressions
    // ============================================================

    @Override
    public ASTNode visitUnaryMinus(FrankoParser.UnaryMinusContext ctx) {
        return new UnaryOpNode("-", visit(ctx.expr()));
    }

    @Override
    public ASTNode visitAddSub(FrankoParser.AddSubContext ctx) {
        return new BinOpNode(
            ctx.op.getText(),
            visit(ctx.expr(0)),
            visit(ctx.expr(1))
        );
    }

    @Override
    public ASTNode visitMulDiv(FrankoParser.MulDivContext ctx) {
        return new BinOpNode(
            ctx.op.getText(),
            visit(ctx.expr(0)),
            visit(ctx.expr(1))
        );
    }

    @Override
    public ASTNode visitCompare(FrankoParser.CompareContext ctx) {
        return new BinOpNode(
            ctx.op.getText(),
            visit(ctx.expr(0)),
            visit(ctx.expr(1))
        );
    }
}