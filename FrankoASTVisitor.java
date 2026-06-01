import java.util.ArrayList;
import java.util.List;

// ================= VISITOR =================

public class FrankoASTVisitor extends FrankoBaseVisitor<ASTNode> {

    // program
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

    // statement labeled alternatives
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

    // simpleStmt dispatch
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

    // block
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

    // if
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

    // while
    @Override
    public ASTNode visitWhileStmt(FrankoParser.WhileStmtContext ctx) {
        ASTNode condition = visit(ctx.expr());
        ASTNode body = visit(ctx.statement());

        return new WhileNode(condition, body);
    }

    // ================= TYPE VISITORS =================

    @Override
    public ASTNode visitInt32Type(FrankoParser.Int32TypeContext ctx) {
        // if lexer maps both "int32_t" and "int" to INT32_T, both become INT32
        return new PrimitiveTypeNode(PrimitiveKind.INT32);
    }

    @Override
    public ASTNode visitUint32Type(FrankoParser.Uint32TypeContext ctx) {
        return new PrimitiveTypeNode(PrimitiveKind.UINT32);
    }

    @Override
    public ASTNode visitFloat32Type(FrankoParser.Float32TypeContext ctx) {
        return new PrimitiveTypeNode(PrimitiveKind.FLOAT32);
    }

    @Override
    public ASTNode visitChar8Type(FrankoParser.Char8TypeContext ctx) {
        return new PrimitiveTypeNode(PrimitiveKind.CHAR8);
    }

    @Override
    public ASTNode visitDynamicArrayType(FrankoParser.DynamicArrayTypeContext ctx) {
        TypeNode elemType = (TypeNode) visit(ctx.type());
        return new DynamicArrayTypeNode(elemType);
    }

    @Override
    public ASTNode visitStaticArrayType(FrankoParser.StaticArrayTypeContext ctx) {
        TypeNode elemType = (TypeNode) visit(ctx.type());
        int size = Integer.parseInt(ctx.INT_LITERAL().getText());
        return new StaticArrayTypeNode(elemType, size);
    }

    // ================= DECLARATIONS =================

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

    // ================= OTHER STATEMENTS =================

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
        return new ArrayUninitNode(ctx.IDENTIFIER().getText());
    }

    @Override
    public ASTNode visitArrayMemsetStmt(FrankoParser.ArrayMemsetStmtContext ctx) {
        return new ArrayMemsetNode(
            ctx.IDENTIFIER().getText(),
            visit(ctx.expr())
        );
    }

    @Override
    public ASTNode visitArrayMemcpyStmt(FrankoParser.ArrayMemcpyStmtContext ctx) {
        return new ArrayMemcpyNode(
            ctx.IDENTIFIER(0).getText(),
            ctx.IDENTIFIER(1).getText()
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

    // ================= EXPRESSIONS =================

    @Override
    public ASTNode visitExprStmt(FrankoParser.ExprStmtContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public ASTNode visitLvalue(FrankoParser.LvalueContext ctx) {
        if (ctx.expr() == null) {
            return new VarNode(ctx.IDENTIFIER().getText());
        } else {
            return new ArrayAccessNode(
                ctx.IDENTIFIER().getText(),
                visit(ctx.expr())
            );
        }
    }

    @Override
    public ASTNode visitAtom(FrankoParser.AtomContext ctx) {
        if (ctx.INT_LITERAL() != null) {
            return new IntNode(Integer.parseInt(ctx.INT_LITERAL().getText()));
        }

        if (ctx.IDENTIFIER() != null && ctx.expr() == null) {
            return new VarNode(ctx.IDENTIFIER().getText());
        }

        if (ctx.IDENTIFIER() != null && ctx.expr() != null) {
            return new ArrayAccessNode(
                ctx.IDENTIFIER().getText(),
                visit(ctx.expr())
            );
        }

        return visit(ctx.expr());
    }

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
