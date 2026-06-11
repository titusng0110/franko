import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

// ================= VISITOR =================

public class FrankoASTVisitor extends FrankoBaseVisitor<ASTNode> {

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Left-folds a binary-expression rule whose parse tree shape is:
     *
     *   operand (op operand)*
     */
    private ASTNode foldSimpleBinaryRule(ParserRuleContext ctx) {
        ASTNode current = visit(ctx.getChild(0));

        for (int i = 1; i < ctx.getChildCount(); i += 2) {
            String op = ctx.getChild(i).getText();
            ASTNode rhs = visit(ctx.getChild(i + 1));
            current = new BinOpNode(op, current, rhs);
        }

        return current;
    }

    /**
     * Left-folds shiftExpr for grammar:
     *
     *   shiftExpr
     *       : additiveExpr ((LT LT | GT GT) additiveExpr)*
     *       ;
     */
    private ASTNode foldShiftExpr(FrankoParser.ShiftExprContext ctx) {
        ASTNode current = visit(ctx.additiveExpr(0));

        int exprIndex = 1;
        int childIndex = 1;

        while (childIndex < ctx.getChildCount()) {
            String first = ctx.getChild(childIndex).getText();
            String second = ctx.getChild(childIndex + 1).getText();

            String op;
            if ("<".equals(first) && "<".equals(second)) {
                op = "<<";
            } else if (">".equals(first) && ">".equals(second)) {
                op = ">>";
            } else {
                throw new RuntimeException(
                    "Internal AST visitor error: expected shift operator token pair, got '"
                    + first + "' and '" + second + "'"
                );
            }

            ASTNode rhs = visit(ctx.additiveExpr(exprIndex));
            current = new BinOpNode(op, current, rhs);

            exprIndex++;
            childIndex += 3;
        }

        return current;
    }

    /**
     * Left-folds constShiftExpr for grammar:
     *
     *   constShiftExpr
     *       : constAdditiveExpr ((LT LT | GT GT) constAdditiveExpr)*
     *       ;
     */
    private ASTNode foldConstShiftExpr(FrankoParser.ConstShiftExprContext ctx) {
        ASTNode current = visit(ctx.constAdditiveExpr(0));

        int exprIndex = 1;
        int childIndex = 1;

        while (childIndex < ctx.getChildCount()) {
            String first = ctx.getChild(childIndex).getText();
            String second = ctx.getChild(childIndex + 1).getText();

            String op;
            if ("<".equals(first) && "<".equals(second)) {
                op = "<<";
            } else if (">".equals(first) && ">".equals(second)) {
                op = ">>";
            } else {
                throw new RuntimeException(
                    "Internal AST visitor error: expected const shift operator token pair, got '"
                    + first + "' and '" + second + "'"
                );
            }

            ASTNode rhs = visit(ctx.constAdditiveExpr(exprIndex));
            current = new BinOpNode(op, current, rhs);

            exprIndex++;
            childIndex += 3;
        }

        return current;
    }

    private ASTNode applyPostfixSuffixes(
        ASTNode base,
        List<FrankoParser.PostfixSuffixContext> suffixes
    ) {
        ASTNode current = base;

        for (FrankoParser.PostfixSuffixContext suffix : suffixes) {
            if (suffix.indexSuffix() != null) {
                ASTNode index = visit(suffix.indexSuffix().expr());
                current = new ArrayAccessNode(current, index);
            } else if (suffix.callSuffix() != null) {
                List<ASTNode> args = new ArrayList<>();

                if (suffix.callSuffix().argumentList() != null) {
                    for (FrankoParser.ExprContext argCtx : suffix.callSuffix().argumentList().expr()) {
                        args.add(visit(argCtx));
                    }
                }

                current = new CallNode(current, args);
            } else if (suffix.memberSuffix() != null) {
                String memberName = suffix.memberSuffix().memberName().getText();
                current = new MemberAccessNode(current, memberName);
            } else {
                throw new RuntimeException(
                    "Internal AST visitor error: unknown postfix suffix: " + suffix.getText()
                );
            }
        }

        return current;
    }

    private ASTNode applyLvalueSuffixes(
        ASTNode base,
        List<FrankoParser.LvalueSuffixContext> suffixes
    ) {
        ASTNode current = base;

        for (FrankoParser.LvalueSuffixContext suffix : suffixes) {
            if (suffix.indexSuffix() != null) {
                ASTNode index = visit(suffix.indexSuffix().expr());
                current = new ArrayAccessNode(current, index);
            } else if (suffix.memberSuffix() != null) {
                String memberName = suffix.memberSuffix().memberName().getText();
                current = new MemberAccessNode(current, memberName);
            } else {
                throw new RuntimeException(
                    "Internal AST visitor error: unknown lvalue suffix: " + suffix.getText()
                );
            }
        }

        return current;
    }

    // ============================================================
    // Program / top-level items
    // ============================================================

    @Override
    public ASTNode visitProgram(FrankoParser.ProgramContext ctx) {
        List<ASTNode> topLevelItems = new ArrayList<>();

        for (FrankoParser.TopLevelItemContext itemCtx : ctx.topLevelItem()) {
            ASTNode node = visit(itemCtx);
            if (node != null) {
                topLevelItems.add(node);
            }
        }

        return new ProgramNode(topLevelItems);
    }

    @Override
    public ASTNode visitTopLevelItem(FrankoParser.TopLevelItemContext ctx) {
        if (ctx.functionDecl() != null) {
            return visit(ctx.functionDecl());
        }

        if (ctx.statement() != null) {
            return visit(ctx.statement());
        }

        throw new RuntimeException("Unknown topLevelItem: " + ctx.getText());
    }

    // ============================================================
    // Function declarations
    // ============================================================

    @Override
    public ASTNode visitFunctionDecl(FrankoParser.FunctionDeclContext ctx) {
        String name = ctx.IDENTIFIER().getText();

        List<ParameterNode> parameters = new ArrayList<>();

        if (ctx.parameterList() != null) {
            for (FrankoParser.ParameterContext paramCtx : ctx.parameterList().parameter()) {
                parameters.add((ParameterNode) visit(paramCtx));
            }
        }

        TypeNode returnType = (TypeNode) visit(ctx.returnType());
        BlockNode body = (BlockNode) visit(ctx.block());

        return new FunctionDeclNode(name, parameters, returnType, body);
    }

    @Override
    public ASTNode visitParameter(FrankoParser.ParameterContext ctx) {
        TypeNode type = (TypeNode) visit(ctx.type());
        String name = ctx.IDENTIFIER().getText();

        return new ParameterNode(type, name);
    }

    @Override
    public ASTNode visitReturnType(FrankoParser.ReturnTypeContext ctx) {
        if (ctx.type() != null) {
            return visit(ctx.type());
        }

        if (ctx.VOID() != null) {
            return new VoidTypeNode();
        }

        throw new RuntimeException("Unknown returnType: " + ctx.getText());
    }

    // ============================================================
    // Statements
    // ============================================================

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
        if (ctx.returnStmt() != null) return visit(ctx.returnStmt());
        if (ctx.assignStmt() != null) return visit(ctx.assignStmt());
        if (ctx.printStmt() != null) return visit(ctx.printStmt());
        if (ctx.exprStmt() != null) return visit(ctx.exprStmt());

        throw new RuntimeException("Unknown simpleStmt: " + ctx.getText());
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

    @Override
    public ASTNode visitReturnStmt(FrankoParser.ReturnStmtContext ctx) {
        ASTNode value = null;

        if (ctx.expr() != null) {
            value = visit(ctx.expr());
        }

        return new ReturnNode(value);
    }

    @Override
    public ASTNode visitAssignStmt(FrankoParser.AssignStmtContext ctx) {
        ASTNode target = visit(ctx.lvalue());
        ASTNode value = visit(ctx.assignmentInitializer());

        return new AssignNode(target, value);
    }

    @Override
    public ASTNode visitAssignmentInitializer(
            FrankoParser.AssignmentInitializerContext ctx
    ) {
        if (ctx.arrayLiteral() != null) {
            return visit(ctx.arrayLiteral());
        }

        if (ctx.expr() != null) {
            return visit(ctx.expr());
        }

        throw new RuntimeException("Unknown assignmentInitializer: " + ctx.getText());
    }

    @Override
    public ASTNode visitDelStmt(FrankoParser.DelStmtContext ctx) {
        return new DelNode(ctx.IDENTIFIER().getText());
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

    @Override
    public ASTNode visitExprStmt(FrankoParser.ExprStmtContext ctx) {
        return new ExprStmtNode(visit(ctx.expr()));
    }

    // ============================================================
    // Types
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
        TypeNode elementType = (TypeNode) visit(ctx.type());
        return new DynamicArrayTypeNode(elementType);
    }

    @Override
    public ASTNode visitStaticArrayType(FrankoParser.StaticArrayTypeContext ctx) {
        TypeNode elementType = (TypeNode) visit(ctx.type());
        ASTNode sizeExpr = visit(ctx.constExpr());

        return new StaticArrayTypeNode(elementType, sizeExpr);
    }

    @Override
    public ASTNode visitNdArrayType(FrankoParser.NdArrayTypeContext ctx) {
        TypeNode elementType = (TypeNode) visit(ctx.type());

        List<ASTNode> dimensions = new ArrayList<>();

        for (FrankoParser.ConstExprContext dimCtx : ctx.constExprList().constExpr()) {
            dimensions.add(visit(dimCtx));
        }

        return new NdArrayTypeNode(elementType, dimensions);
    }

    @Override
    public ASTNode visitAddrType(FrankoParser.AddrTypeContext ctx) {
        TypeNode referencedType = (TypeNode) visit(ctx.type());
        return new AddrTypeNode(referencedType);
    }

    // ============================================================
    // Declarations
    // ============================================================

    @Override
    public ASTNode visitVarDecl(FrankoParser.VarDeclContext ctx) {
        TypeNode type = (TypeNode) visit(ctx.type());
        String name = ctx.IDENTIFIER().getText();
        boolean isHeap = ctx.ALLOC() != null;

        if (ctx.declSuffix() == null) {
            return new VarDeclNode(type, name, isHeap);
        }

        if (ctx.declSuffix() instanceof FrankoParser.DeclAssignInitContext) {
            FrankoParser.DeclAssignInitContext initCtx =
                (FrankoParser.DeclAssignInitContext) ctx.declSuffix();

            ASTNode init = visit(initCtx.declInitializer());

            return new VarDeclInitNode(type, name, isHeap, init);
        }

        if (ctx.declSuffix() instanceof FrankoParser.DeclArrayInitContext) {
            FrankoParser.DeclArrayInitContext initCtx =
                (FrankoParser.DeclArrayInitContext) ctx.declSuffix();

            ASTNode size = visit(initCtx.expr());

            return new VarDeclArrayInitNode(type, name, isHeap, size);
        }

        throw new RuntimeException("Unknown declSuffix: " + ctx.declSuffix().getText());
    }

    @Override
    public ASTNode visitDeclInitializer(FrankoParser.DeclInitializerContext ctx) {
        if (ctx.arrayLiteral() != null) {
            return visit(ctx.arrayLiteral());
        }

        if (ctx.expr() != null) {
            return visit(ctx.expr());
        }

        throw new RuntimeException("Unknown declInitializer: " + ctx.getText());
    }

    // ============================================================
    // Lvalues
    // ============================================================

    @Override
    public ASTNode visitLvalue(FrankoParser.LvalueContext ctx) {
        ASTNode base = visit(ctx.lvalueAtom());
        return applyLvalueSuffixes(base, ctx.lvalueSuffix());
    }

    @Override
    public ASTNode visitLvalueAtom(FrankoParser.LvalueAtomContext ctx) {
        if (ctx.IDENTIFIER() != null) {
            return new VarNode(ctx.IDENTIFIER().getText());
        }

        if (ctx.derefExpr() != null) {
            return visit(ctx.derefExpr());
        }

        if (ctx.lvalue() != null) {
            return visit(ctx.lvalue());
        }

        throw new RuntimeException("Unknown lvalueAtom: " + ctx.getText());
    }

    // ============================================================
    // Expressions
    // ============================================================

    @Override
    public ASTNode visitExpr(FrankoParser.ExprContext ctx) {
        return visit(ctx.logicalOrExpr());
    }

    @Override
    public ASTNode visitLogicalOrExpr(FrankoParser.LogicalOrExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitLogicalAndExpr(FrankoParser.LogicalAndExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitBitwiseOrExpr(FrankoParser.BitwiseOrExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitBitwiseXorExpr(FrankoParser.BitwiseXorExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitBitwiseAndExpr(FrankoParser.BitwiseAndExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitEqualityExpr(FrankoParser.EqualityExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitRelationalExpr(FrankoParser.RelationalExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitShiftExpr(FrankoParser.ShiftExprContext ctx) {
        return foldShiftExpr(ctx);
    }

    @Override
    public ASTNode visitAdditiveExpr(FrankoParser.AdditiveExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitMultiplicativeExpr(FrankoParser.MultiplicativeExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitUnaryMinus(FrankoParser.UnaryMinusContext ctx) {
        return new UnaryOpNode("-", visit(ctx.unaryExpr()));
    }

    @Override
    public ASTNode visitLogicalNot(FrankoParser.LogicalNotContext ctx) {
        return new UnaryOpNode("!", visit(ctx.unaryExpr()));
    }

    @Override
    public ASTNode visitPostfixExprOnly(FrankoParser.PostfixExprOnlyContext ctx) {
        return visit(ctx.postfixExpr());
    }

    // ============================================================
    // Constant expressions
    // ============================================================
    //
    // These use the same AST expression nodes as normal expressions:
    //
    //   IntNode
    //   UnaryOpNode
    //   BinOpNode
    //
    // The grammar has already restricted constExpr so it cannot contain
    // identifiers, calls, indexing, member access, getaddr, deref, etc.
    // The semantic checker should still fold and validate the expression.

    @Override
    public ASTNode visitConstExpr(FrankoParser.ConstExprContext ctx) {
        return visit(ctx.constLogicalOrExpr());
    }

    @Override
    public ASTNode visitConstLogicalOrExpr(FrankoParser.ConstLogicalOrExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitConstLogicalAndExpr(FrankoParser.ConstLogicalAndExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitConstBitwiseOrExpr(FrankoParser.ConstBitwiseOrExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitConstBitwiseXorExpr(FrankoParser.ConstBitwiseXorExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitConstBitwiseAndExpr(FrankoParser.ConstBitwiseAndExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitConstEqualityExpr(FrankoParser.ConstEqualityExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitConstRelationalExpr(FrankoParser.ConstRelationalExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitConstShiftExpr(FrankoParser.ConstShiftExprContext ctx) {
        return foldConstShiftExpr(ctx);
    }

    @Override
    public ASTNode visitConstAdditiveExpr(FrankoParser.ConstAdditiveExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitConstMultiplicativeExpr(FrankoParser.ConstMultiplicativeExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public ASTNode visitConstUnaryExpr(FrankoParser.ConstUnaryExprContext ctx) {
        if (ctx.MINUS() != null) {
            return new UnaryOpNode("-", visit(ctx.constUnaryExpr()));
        }

        if (ctx.BANG() != null) {
            return new UnaryOpNode("!", visit(ctx.constUnaryExpr()));
        }

        if (ctx.constPrimary() != null) {
            return visit(ctx.constPrimary());
        }

        throw new RuntimeException("Unknown constUnaryExpr: " + ctx.getText());
    }

    @Override
    public ASTNode visitConstPrimary(FrankoParser.ConstPrimaryContext ctx) {
        if (ctx.integerLiteral() != null) {
            return visit(ctx.integerLiteral());
        }

        if (ctx.constExpr() != null) {
            return visit(ctx.constExpr());
        }

        throw new RuntimeException("Unknown constPrimary: " + ctx.getText());
    }

    // ============================================================
    // Array literals
    // ============================================================

    @Override
    public ASTNode visitArrayLiteral(FrankoParser.ArrayLiteralContext ctx) {
        List<ASTNode> elements = new ArrayList<>();

        if (ctx.arrayLiteralElements() != null) {
            for (FrankoParser.ArrayLiteralElementContext elementCtx
                    : ctx.arrayLiteralElements().arrayLiteralElement()) {
                elements.add(visit(elementCtx));
            }
        }

        return new ArrayLiteralNode(elements);
    }

    @Override
    public ASTNode visitArrayLiteralElement(FrankoParser.ArrayLiteralElementContext ctx) {
        if (ctx.arrayLiteral() != null) {
            return visit(ctx.arrayLiteral());
        }

        if (ctx.expr() != null) {
            return visit(ctx.expr());
        }

        throw new RuntimeException("Unknown arrayLiteralElement: " + ctx.getText());
    }

    // ============================================================
    // Postfix / call / member / index
    // ============================================================

    @Override
    public ASTNode visitPostfixExpr(FrankoParser.PostfixExprContext ctx) {
        ASTNode base = visit(ctx.primary());
        return applyPostfixSuffixes(base, ctx.postfixSuffix());
    }

    @Override
    public ASTNode visitPrimary(FrankoParser.PrimaryContext ctx) {
        if (ctx.integerLiteral() != null) {
            return visit(ctx.integerLiteral());
        }

        if (ctx.stringLiteral() != null) {
            return visit(ctx.stringLiteral());
        }

        if (ctx.IDENTIFIER() != null) {
            return new VarNode(ctx.IDENTIFIER().getText());
        }

        if (ctx.getAddrExpr() != null) {
            return visit(ctx.getAddrExpr());
        }

        if (ctx.derefExpr() != null) {
            return visit(ctx.derefExpr());
        }

        if (ctx.expr() != null) {
            return visit(ctx.expr());
        }

        throw new RuntimeException("Unknown primary: " + ctx.getText());
    }

    @Override
    public ASTNode visitGetAddrExpr(FrankoParser.GetAddrExprContext ctx) {
        ASTNode target = visit(ctx.lvalue());
        return new GetAddrNode(target);
    }

    @Override
    public ASTNode visitDerefExpr(FrankoParser.DerefExprContext ctx) {
        ASTNode expr = visit(ctx.expr());
        return new DerefNode(expr);
    }

    @Override
    public ASTNode visitIntegerLiteral(FrankoParser.IntegerLiteralContext ctx) {
        return new IntNode(ctx.getText());
    }

    @Override
    public ASTNode visitStringLiteral(FrankoParser.StringLiteralContext ctx) {
        return new StringLiteralNode(ctx.STRING_LITERAL().getText());
    }
}