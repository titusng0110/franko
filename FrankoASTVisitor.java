import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

public class FrankoASTVisitor extends FrankoBaseVisitor<ASTNode> {

    private ASTNode foldSimpleBinaryRule(ParserRuleContext ctx) {
        ASTNode current = visit(ctx.getChild(0));

        for (int i = 1; i < ctx.getChildCount(); i += 2) {
            String op = ctx.getChild(i).getText();
            ASTNode rhs = visit(ctx.getChild(i + 1));
            current = new BinOpNode(op, current, rhs);
        }

        return current;
    }

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

        if (ctx.structDecl() != null) {
            return visit(ctx.structDecl());
        }

        if (ctx.globalVarDecl() != null) {
            return visit(ctx.globalVarDecl());
        }

        if (ctx.importDecl() != null) {
            return visit(ctx.importDecl());
        }

        if (ctx.namespaceDecl() != null) {
            return visit(ctx.namespaceDecl());
        }

        throw new RuntimeException("Unknown topLevelItem: " + ctx.getText());
    }

    @Override
    public ASTNode visitNamespaceDecl(FrankoParser.NamespaceDeclContext ctx) {

        List<String> segments = new ArrayList<>();

        for (TerminalNode identifier : ctx.IDENTIFIER()) {
            segments.add(identifier.getText());
        }

        String name = String.join("::", segments);

        List<ASTNode> items = new ArrayList<>();

        for (FrankoParser.NamespaceItemContext itemCtx : ctx.namespaceItem()) {
            ASTNode node = visit(itemCtx);
            if (node != null) {
                items.add(node);
            }
        }

        return new NamespaceNode(name, items);
    }

    @Override
    public ASTNode visitNamespaceItem(FrankoParser.NamespaceItemContext ctx) {
        if (ctx.functionDecl() != null) {
            return visit(ctx.functionDecl());
        }

        if (ctx.structDecl() != null) {
            return visit(ctx.structDecl());
        }

        if (ctx.globalVarDecl() != null) {
            return visit(ctx.globalVarDecl());
        }

        if (ctx.namespaceDecl() != null) {
            return visit(ctx.namespaceDecl());
        }

        throw new RuntimeException("Unknown namespaceItem: " + ctx.getText());
    }

    private List<String> qualifiedNameSegments(FrankoParser.QualifiedNameContext ctx) {
        List<String> segments = new ArrayList<>();

        for (TerminalNode identifier : ctx.IDENTIFIER()) {
            segments.add(identifier.getText());
        }

        return segments;
    }

    @Override
    public ASTNode visitQualifiedStructType(FrankoParser.QualifiedStructTypeContext ctx) {
        List<String> segments = qualifiedNameSegments(ctx.qualifiedName());

        List<String> namespaceSegments =
                new ArrayList<>(segments.subList(0, segments.size() - 1));

        String name = segments.get(segments.size() - 1);

        return new StructTypeNode(namespaceSegments, name);
    }

    @Override
    public ASTNode visitImportDecl(FrankoParser.ImportDeclContext ctx) {
        String rawText = ctx.STRING_LITERAL().getText();

        String rawContent = rawText.substring(1, rawText.length() - 1);
        String decoded = StringLiteralDecoder.decodeRawContent(rawContent);

        return new ImportNode(decoded);
    }

    @Override
    public ASTNode visitStructDecl(FrankoParser.StructDeclContext ctx) {
        String name = ctx.IDENTIFIER().getText();

        List<StructFieldDeclNode> fields = new ArrayList<>();
        List<FunctionDeclNode> methods = new ArrayList<>();

        for (FrankoParser.StructMemberContext memberCtx : ctx.structMember()) {
            if (memberCtx.structFieldDecl() != null) {
                fields.add((StructFieldDeclNode) visit(memberCtx.structFieldDecl()));
            } else if (memberCtx.functionDecl() != null) {
                methods.add((FunctionDeclNode) visit(memberCtx.functionDecl()));
            } else {
                throw new RuntimeException("Unknown structMember: " + memberCtx.getText());
            }
        }

        return new StructDeclNode(name, fields, methods);
    }

    @Override
    public ASTNode visitStructFieldDecl(FrankoParser.StructFieldDeclContext ctx) {
        TypeNode type = (TypeNode) visit(ctx.type());
        String name = ctx.IDENTIFIER().getText();

        ASTNode constInitializer = null;

        if (ctx.constDeclSuffix() != null) {
            constInitializer = visit(ctx.constDeclSuffix().constDeclInitializer());
        }

        return new StructFieldDeclNode(type, name, constInitializer);
    }

    @Override
    public ASTNode visitGlobalVarDecl(FrankoParser.GlobalVarDeclContext ctx) {
        TypeNode type = (TypeNode) visit(ctx.type());
        String name = ctx.IDENTIFIER().getText();

        ASTNode constInitializer = null;

        if (ctx.globalVarInitializer() != null) {
            constInitializer =
                    visit(ctx.globalVarInitializer().globalVarInitValue());
        }

        return new GlobalVarDeclNode(type, name, constInitializer);
    }

    @Override
    public ASTNode visitGlobalVarInitValue(
            FrankoParser.GlobalVarInitValueContext ctx
    ) {
        if (ctx.constExpr() != null) {
            return visit(ctx.constExpr());
        }

        if (ctx.stringLiteral() != null) {
            return visit(ctx.stringLiteral());
        }

        if (ctx.arrayLiteral() != null) {
            return visit(ctx.arrayLiteral());
        }

        if (ctx.getPtrExpr() != null) {
            return visit(ctx.getPtrExpr());
        }

        if (ctx.getViewExpr() != null) {
            return visit(ctx.getViewExpr());
        }

        if (ctx.getMViewExpr() != null) {
            return visit(ctx.getMViewExpr());
        }

        return visit(ctx.nullLiteral());
    }

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
        String name;

        if (ctx.SELF() != null) {
            name = "self";
        } else {
            name = ctx.IDENTIFIER().getText();
        }

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
    public ASTNode visitBreakStatement(FrankoParser.BreakStatementContext ctx) {
        return visit(ctx.breakStmt());
    }

    @Override
    public ASTNode visitContinueStatement(FrankoParser.ContinueStatementContext ctx) {
        return visit(ctx.continueStmt());
    }

    @Override
    public ASTNode visitSimpleStmt(FrankoParser.SimpleStmtContext ctx) {
        if (ctx.varDecl() != null) return visit(ctx.varDecl());
        if (ctx.returnStmt() != null) return visit(ctx.returnStmt());
        if (ctx.assignStmt() != null) return visit(ctx.assignStmt());
        if (ctx.compoundAssignStmt() != null) return visit(ctx.compoundAssignStmt());
        if (ctx.incDecStmt() != null) return visit(ctx.incDecStmt());
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
    public ASTNode visitForStatement(FrankoParser.ForStatementContext ctx) {
        return visit(ctx.forStmt());
    }

    @Override
    public ASTNode visitForStmt(FrankoParser.ForStmtContext ctx) {
        ASTNode init = null;

        if (ctx.forInit() != null) {
            init = visit(ctx.forInit().simpleStmt());
        }

        ASTNode condition = visit(ctx.expr());

        ASTNode update = null;

        if (ctx.forUpdate() != null) {
            update = visit(ctx.forUpdate().simpleStmt());
        }

        ASTNode body = visit(ctx.statement());

        return new ForNode(init, condition, update, body);
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
    public ASTNode visitBreakStmt(FrankoParser.BreakStmtContext ctx) {
        ASTNode level = null;

        if (ctx.constExpr() != null) {
            level = visit(ctx.constExpr());
        }

        return new BreakNode(level);
    }

    @Override
    public ASTNode visitContinueStmt(FrankoParser.ContinueStmtContext ctx) {
        ASTNode level = null;

        if (ctx.constExpr() != null) {
            level = visit(ctx.constExpr());
        }

        return new ContinueNode(level);
    }

    @Override
    public ASTNode visitAssignStmt(FrankoParser.AssignStmtContext ctx) {
        ASTNode target = visit(ctx.lvalue());
        ASTNode value = visit(ctx.expr());

        return new AssignNode(target, value);
    }

    @Override
    public ASTNode visitCompoundAssignStmt(
            FrankoParser.CompoundAssignStmtContext ctx
    ) {
        ASTNode target = visit(ctx.lvalue());
        ASTNode value = visit(ctx.expr());

        return new AssignNode(
            ctx.compoundAssignOp().getText(),
            target,
            value
        );
    }

    @Override
    public ASTNode visitIncDecStmt(FrankoParser.IncDecStmtContext ctx) {
        ASTNode target = visit(ctx.lvalue());

        return new IncDecNode(ctx.PLUSPLUS() != null ? "++" : "--", target);
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
    public ASTNode visitExplicitArraySize(FrankoParser.ExplicitArraySizeContext ctx) {
        return visit(ctx.constExpr());
    }

    @Override
    public ASTNode visitInferredArraySize(FrankoParser.InferredArraySizeContext ctx) {
        return new InferredSizeNode();
    }

    @Override
    public ASTNode visitStaticArrayType(FrankoParser.StaticArrayTypeContext ctx) {
        TypeNode elementType = (TypeNode) visit(ctx.type());
        ASTNode sizeExpr = visit(ctx.arraySize());

        return new StaticArrayTypeNode(elementType, sizeExpr);
    }

    @Override
    public ASTNode visitPtrType(FrankoParser.PtrTypeContext ctx) {
        TypeNode referencedType = (TypeNode) visit(ctx.type());
        return new PtrTypeNode(PtrPermission.OWNER, referencedType);
    }

    @Override
    public ASTNode visitMViewType(FrankoParser.MViewTypeContext ctx) {
        TypeNode referencedType = (TypeNode) visit(ctx.type());
        return new PtrTypeNode(PtrPermission.MUTABLE, referencedType);
    }

    @Override
    public ASTNode visitViewType(FrankoParser.ViewTypeContext ctx) {
        TypeNode referencedType = (TypeNode) visit(ctx.type());
        return new PtrTypeNode(PtrPermission.READ_ONLY, referencedType);
    }

    @Override
    public ASTNode visitStructType(FrankoParser.StructTypeContext ctx) {
        return new StructTypeNode(List.of(), ctx.IDENTIFIER().getText());
    }

    @Override
    public ASTNode visitVarDecl(FrankoParser.VarDeclContext ctx) {
        TypeNode type = (TypeNode) visit(ctx.type());
        String name = ctx.IDENTIFIER().getText();

        if (ctx.declSuffix() == null) {
            return new VarDeclNode(type, name);
        }

        if (ctx.declSuffix() instanceof FrankoParser.DeclAssignInitContext) {
            FrankoParser.DeclAssignInitContext initCtx =
                (FrankoParser.DeclAssignInitContext) ctx.declSuffix();

            ASTNode init = visit(initCtx.expr());

            return new VarDeclInitNode(type, name, init);
        }

        if (ctx.declSuffix() instanceof FrankoParser.DeclArrayInitContext) {
            FrankoParser.DeclArrayInitContext initCtx =
                (FrankoParser.DeclArrayInitContext) ctx.declSuffix();

            ASTNode size = visit(initCtx.expr());

            return new VarDeclArrayInitNode(type, name, size);
        }

        throw new RuntimeException("Unknown declSuffix: " + ctx.declSuffix().getText());
    }

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

        if (ctx.SELF() != null) {
            return new VarNode("self");
        }

        if (ctx.qualifiedName() != null) {
            return new QualifiedNameNode(
                    qualifiedNameSegments(ctx.qualifiedName())
            );
        }

        if (ctx.derefExpr() != null) {
            return visit(ctx.derefExpr());
        }

        if (ctx.lvalue() != null) {
            return visit(ctx.lvalue());
        }

        throw new RuntimeException("Unknown lvalueAtom: " + ctx.getText());
    }

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

        if (ctx.arrayLiteral() != null) {
            return visit(ctx.arrayLiteral());
        }

        if (ctx.nullLiteral() != null) {
            return visit(ctx.nullLiteral());
        }

        if (ctx.IDENTIFIER() != null) {
            return new VarNode(ctx.IDENTIFIER().getText());
        }

        if (ctx.SELF() != null) {
            return new VarNode("self");
        }

        if (ctx.qualifiedName() != null) {
            return new QualifiedNameNode(
                    qualifiedNameSegments(ctx.qualifiedName())
            );
        }

        if (ctx.getPtrExpr() != null) {
            return visit(ctx.getPtrExpr());
        }

        if (ctx.getViewExpr() != null) {
            return visit(ctx.getViewExpr());
        }

        if (ctx.getMViewExpr() != null) {
            return visit(ctx.getMViewExpr());
        }

        if (ctx.derefExpr() != null) {
            return visit(ctx.derefExpr());
        }

        if (ctx.castExpr() != null) {
            return visit(ctx.castExpr());
        }

        if (ctx.unsafeCastExpr() != null) {
            return visit(ctx.unsafeCastExpr());
        }

        if (ctx.expr() != null) {
            return visit(ctx.expr());
        }

        throw new RuntimeException("Unknown primary: " + ctx.getText());
    }

    @Override
    public ASTNode visitNullLiteral(FrankoParser.NullLiteralContext ctx) {
        return new NullNode();
    }

    @Override
    public ASTNode visitGetPtrExpr(FrankoParser.GetPtrExprContext ctx) {
        ASTNode target = visit(ctx.lvalue());
        return new GetPtrNode(PtrPermission.OWNER, target);
    }

    @Override
    public ASTNode visitGetViewExpr(FrankoParser.GetViewExprContext ctx) {
        ASTNode target = visit(ctx.lvalue());
        return new GetPtrNode(PtrPermission.READ_ONLY, target);
    }

    @Override
    public ASTNode visitGetMViewExpr(FrankoParser.GetMViewExprContext ctx) {
        ASTNode target = visit(ctx.lvalue());
        return new GetPtrNode(PtrPermission.MUTABLE, target);
    }

    @Override
    public ASTNode visitDerefExpr(FrankoParser.DerefExprContext ctx) {
        ASTNode expr = visit(ctx.expr());
        return new DerefNode(expr);
    }

    @Override
    public ASTNode visitCastExpr(FrankoParser.CastExprContext ctx) {
        TypeNode target = (TypeNode) visit(ctx.type());
        return new CastNode(target, visit(ctx.expr()));
    }

    @Override
    public ASTNode visitUnsafeCastExpr(FrankoParser.UnsafeCastExprContext ctx) {
        TypeNode target = new PrimitiveTypeNode(
                primitiveKindOf(ctx.primitiveType())
        );

        return new CastNode(target, visit(ctx.expr()), true);
    }

    private static PrimitiveKind primitiveKindOf(
            FrankoParser.PrimitiveTypeContext target
    ) {
        if (target.INT8_T() != null) {
            return PrimitiveKind.INT8;
        } else if (target.INT16_T() != null) {
            return PrimitiveKind.INT16;
        } else if (target.INT32_T() != null) {
            return PrimitiveKind.INT32;
        } else if (target.INT64_T() != null) {
            return PrimitiveKind.INT64;
        } else if (target.UINT8_T() != null) {
            return PrimitiveKind.UINT8;
        } else if (target.UINT16_T() != null) {
            return PrimitiveKind.UINT16;
        } else if (target.UINT32_T() != null) {
            return PrimitiveKind.UINT32;
        } else if (target.UINT64_T() != null) {
            return PrimitiveKind.UINT64;
        }

        throw new RuntimeException(
                "Internal compiler error: cast target is not a primitive integer type"
        );
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
