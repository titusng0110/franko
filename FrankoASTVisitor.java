import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

public class FrankoASTVisitor extends FrankoBaseVisitor<AstNode> {

    private final String sourcePath;

    public FrankoASTVisitor(String sourcePath) {
        this.sourcePath = sourcePath == null ? "" : sourcePath;
    }

    private SourceSpan span(ParserRuleContext ctx) {
        if (ctx == null) {
            return SourceSpan.NONE;
        }

        Token start = ctx.getStart();
        Token stop = ctx.getStop();

        if (start == null || stop == null) {
            return SourceSpan.NONE;
        }

        return new SourceSpan(
                sourcePath,
                start.getLine(),
                start.getCharPositionInLine() + 1,
                stop.getLine(),
                stop.getCharPositionInLine() + stop.getText().length() + 1
        );
    }

    private SourceSpan span(Token token) {
        if (token == null) {
            return SourceSpan.NONE;
        }

        return new SourceSpan(
                sourcePath,
                token.getLine(),
                token.getCharPositionInLine() + 1,
                token.getLine(),
                token.getCharPositionInLine() + token.getText().length() + 1
        );
    }

    private static SourceSpan merge(SourceSpan left, SourceSpan right) {
        if (left == null || !left.isPresent()) {
            return right == null ? SourceSpan.NONE : right;
        }

        if (right == null || !right.isPresent()) {
            return left;
        }

        return new SourceSpan(
                left.source(),
                left.startLine(),
                left.startColumn(),
                right.endLine(),
                right.endColumn()
        );
    }

    private AstNode foldSimpleBinaryRule(ParserRuleContext ctx) {
        AstNode current = visit(ctx.getChild(0));

        for (int i = 1; i < ctx.getChildCount(); i += 2) {
            String op = ctx.getChild(i).getText();
            AstNode rhs = visit(ctx.getChild(i + 1));
            current = AstNode.binary(span(ctx), op, current, rhs);
        }

        return current;
    }

    private AstNode foldShiftExpr(FrankoParser.ShiftExprContext ctx) {
        AstNode current = visit(ctx.additiveExpr(0));

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

            AstNode rhs = visit(ctx.additiveExpr(exprIndex));
            current = AstNode.binary(span(ctx), op, current, rhs);

            exprIndex++;
            childIndex += 3;
        }

        return current;
    }

    private AstNode foldConstShiftExpr(FrankoParser.ConstShiftExprContext ctx) {
        AstNode current = visit(ctx.constAdditiveExpr(0));

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

            AstNode rhs = visit(ctx.constAdditiveExpr(exprIndex));
            current = AstNode.binary(span(ctx), op, current, rhs);

            exprIndex++;
            childIndex += 3;
        }

        return current;
    }

    private AstNode applyPostfixSuffixes(
        AstNode base,
        List<FrankoParser.PostfixSuffixContext> suffixes
    ) {
        AstNode current = base;

        for (FrankoParser.PostfixSuffixContext suffix : suffixes) {
            if (suffix.indexSuffix() != null) {
                AstNode index = visit(suffix.indexSuffix().expr());
                current = AstNode.arrayAccess(
                        merge(current.span, span(suffix)),
                        current,
                        index
                );
            } else if (suffix.genericCallSuffix() != null) {
                List<AstNode> genericArgs = genericArguments(
                        suffix.genericCallSuffix().genericArgList());
                List<AstNode> args = new ArrayList<>();

                if (suffix.genericCallSuffix().argumentList() != null) {
                    for (FrankoParser.ExprContext argCtx : suffix.genericCallSuffix().argumentList().expr()) {
                        args.add(visit(argCtx));
                    }
                }

                current = AstNode.call(
                        merge(current.span, span(suffix)),
                        current,
                        args,
                        genericArgs
                );
            } else if (suffix.callSuffix() != null) {
                List<AstNode> args = new ArrayList<>();

                if (suffix.callSuffix().argumentList() != null) {
                    for (FrankoParser.ExprContext argCtx : suffix.callSuffix().argumentList().expr()) {
                        args.add(visit(argCtx));
                    }
                }

                current = AstNode.call(merge(current.span, span(suffix)), current, args);
            } else if (suffix.memberSuffix() != null) {
                String memberName = suffix.memberSuffix().memberName().getText();
                current = AstNode.memberAccess(
                        merge(current.span, span(suffix)),
                        current,
                        memberName
                );
            } else {
                throw new RuntimeException(
                    "Internal AST visitor error: unknown postfix suffix: " + suffix.getText()
                );
            }
        }

        return current;
    }

    private AstNode applyLvalueSuffixes(
        AstNode base,
        List<FrankoParser.LvalueSuffixContext> suffixes
    ) {
        AstNode current = base;

        for (FrankoParser.LvalueSuffixContext suffix : suffixes) {
            if (suffix.indexSuffix() != null) {
                AstNode index = visit(suffix.indexSuffix().expr());
                current = AstNode.arrayAccess(
                        merge(current.span, span(suffix)),
                        current,
                        index
                );
            } else if (suffix.memberSuffix() != null) {
                String memberName = suffix.memberSuffix().memberName().getText();
                current = AstNode.memberAccess(
                        merge(current.span, span(suffix)),
                        current,
                        memberName
                );
            } else {
                throw new RuntimeException(
                    "Internal AST visitor error: unknown lvalue suffix: " + suffix.getText()
                );
            }
        }

        return current;
    }

    @Override
    public AstNode visitProgram(FrankoParser.ProgramContext ctx) {
        List<AstNode> topLevelItems = new ArrayList<>();

        for (FrankoParser.TopLevelItemContext itemCtx : ctx.topLevelItem()) {
            AstNode node = visit(itemCtx);
            if (node != null) {
                topLevelItems.add(node);
            }
        }

        return AstNode.program(span(ctx), topLevelItems);
    }

    @Override
    public AstNode visitTopLevelItem(FrankoParser.TopLevelItemContext ctx) {
        if (ctx.functionDecl() != null) {
            return visit(ctx.functionDecl());
        }

        if (ctx.structDecl() != null) {
            return visit(ctx.structDecl());
        }

        if (ctx.enumDecl() != null) {
            return visit(ctx.enumDecl());
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

        if (ctx.compileTimeDeclIf() != null) {
            return visit(ctx.compileTimeDeclIf());
        }

        if (ctx.compileTimeDeclItem() != null) {
            return visit(ctx.compileTimeDeclItem());
        }

        throw new RuntimeException("Unknown topLevelItem: " + ctx.getText());
    }

    @Override
    public AstNode visitNamespaceDecl(FrankoParser.NamespaceDeclContext ctx) {

        List<String> segments = new ArrayList<>();

        for (TerminalNode identifier : ctx.IDENTIFIER()) {
            segments.add(identifier.getText());
        }

        String name = String.join("::", segments);

        List<AstNode> items = new ArrayList<>();

        for (FrankoParser.NamespaceItemContext itemCtx : ctx.namespaceItem()) {
            AstNode node = visit(itemCtx);
            if (node != null) {
                items.add(node);
            }
        }

        return AstNode.namespace(span(ctx), name, items);
    }

    @Override
    public AstNode visitNamespaceItem(FrankoParser.NamespaceItemContext ctx) {
        if (ctx.functionDecl() != null) {
            return visit(ctx.functionDecl());
        }

        if (ctx.structDecl() != null) {
            return visit(ctx.structDecl());
        }

        if (ctx.enumDecl() != null) {
            return visit(ctx.enumDecl());
        }

        if (ctx.globalVarDecl() != null) {
            return visit(ctx.globalVarDecl());
        }

        if (ctx.namespaceDecl() != null) {
            return visit(ctx.namespaceDecl());
        }

        if (ctx.compileTimeDeclIf() != null) {
            return visit(ctx.compileTimeDeclIf());
        }

        if (ctx.compileTimeDeclItem() != null) {
            return visit(ctx.compileTimeDeclItem());
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

    private List<AstNode> genericParameters(FrankoParser.GenericParamListContext ctx) {
        List<AstNode> params = new ArrayList<>();

        if (ctx == null) {
            return params;
        }

        for (FrankoParser.GenericParamContext paramCtx : ctx.genericParam()) {
            if (paramCtx.TYPENAME() != null) {
                params.add(AstNode.genericParam(
                        span(paramCtx),
                        paramCtx.IDENTIFIER().getText(),
                        null,
                        true
                ));
            } else {
                params.add(AstNode.genericParam(
                        span(paramCtx),
                        paramCtx.IDENTIFIER().getText(),
                        visit(paramCtx.type()),
                        false
                ));
            }
        }

        return params;
    }

    private List<AstNode> genericArguments(FrankoParser.GenericArgListContext ctx) {
        List<AstNode> args = new ArrayList<>();

        if (ctx == null) {
            return args;
        }

        for (FrankoParser.GenericArgContext argCtx : ctx.genericArg()) {
            if (argCtx.type() != null) {
                args.add(AstNode.genericArgType(
                        span(argCtx),
                        visit(argCtx.type())
                ));
            } else {
                args.add(AstNode.genericArgValue(
                        span(argCtx),
                        visit(argCtx.constExpr())
                ));
            }
        }

        return args;
    }

    @Override
    public AstNode visitQualifiedStructType(FrankoParser.QualifiedStructTypeContext ctx) {
        List<String> segments = qualifiedNameSegments(ctx.qualifiedName());

        List<String> namespaceSegments =
                new ArrayList<>(segments.subList(0, segments.size() - 1));

        String name = segments.get(segments.size() - 1);

        return AstNode.structType(
                span(ctx),
                namespaceSegments,
                name,
                genericArguments(ctx.genericArgList())
        );
    }

    @Override
    public AstNode visitImportDecl(FrankoParser.ImportDeclContext ctx) {
        String rawText = ctx.STRING_LITERAL().getText();

        String rawContent = rawText.substring(1, rawText.length() - 1);
        String decoded = StringLiteralDecoder.decodeRawContent(rawContent);

        return AstNode.importPath(span(ctx), decoded);
    }

    @Override
    public AstNode visitStructDecl(FrankoParser.StructDeclContext ctx) {
        String name = ctx.IDENTIFIER().getText();

        List<AstNode> fields = new ArrayList<>();
        List<AstNode> methods = new ArrayList<>();
        List<AstNode> nodes = new ArrayList<>();

        for (FrankoParser.StructMemberContext memberCtx : ctx.structMember()) {
            if (memberCtx.structFieldDecl() != null) {
                AstNode field =
                        visit(memberCtx.structFieldDecl());
                fields.add(field);
                nodes.add(field);
            } else if (memberCtx.functionDecl() != null) {
                AstNode method =
                        visit(memberCtx.functionDecl());
                methods.add(method);
                nodes.add(method);
            } else if (memberCtx.structMemberIf() != null) {
                nodes.add(visit(memberCtx.structMemberIf()));
            } else if (memberCtx.compileTimeDeclItem() != null) {
                nodes.add(visit(memberCtx.compileTimeDeclItem()));
            } else {
                throw new RuntimeException("Unknown structMember: " + memberCtx.getText());
            }
        }

        AstNode node = AstNode.structDecl(
                span(ctx),
                name,
                fields,
                methods,
                nodes,
                genericParameters(ctx.genericParamList())
        );
        node.genericModifier = ctx.HASH_GENERIC() != null;
        node.isGeneric = node.genericModifier || !node.genericParams.isEmpty();
        return node;
    }

    @Override
    public AstNode visitStructFieldDecl(FrankoParser.StructFieldDeclContext ctx) {
        AstNode type = visit(ctx.type());
        String name = ctx.IDENTIFIER().getText();

        AstNode constInitializer = null;

        if (ctx.constDeclSuffix() != null) {
            constInitializer = visit(ctx.constDeclSuffix().constDeclInitializer());
        }

        return AstNode.structFieldDecl(
                span(ctx),
                type,
                name,
                constInitializer,
                ctx.CONST() != null
        );
    }

    @Override
    public AstNode visitEnumDecl(FrankoParser.EnumDeclContext ctx) {
        AstNode backingType = visit(ctx.type());
        String name = ctx.IDENTIFIER().getText();

        List<AstNode> variants = new ArrayList<>();

        if (ctx.enumVariantList() != null) {
            for (FrankoParser.EnumVariantContext variantCtx : ctx.enumVariantList().enumVariant()) {
                variants.add(visit(variantCtx));
            }
        }

        return AstNode.enumDecl(span(ctx), name, backingType, variants);
    }

    @Override
    public AstNode visitEnumVariant(FrankoParser.EnumVariantContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        AstNode constInitializer = null;

        if (ctx.constExpr() != null) {
            constInitializer = visit(ctx.constExpr());
        }

        return AstNode.enumVariant(span(ctx), name, constInitializer);
    }

    @Override
    public AstNode visitGlobalVarDecl(FrankoParser.GlobalVarDeclContext ctx) {
        AstNode type = visit(ctx.type());
        String name = ctx.IDENTIFIER().getText();

        AstNode constInitializer = null;

        if (ctx.globalVarInitializer() != null) {
            constInitializer =
                    visit(ctx.globalVarInitializer().globalVarInitValue());
        }

        return AstNode.globalVarDecl(
                span(ctx),
                type,
                name,
                constInitializer,
                ctx.CONST() != null
        );
    }

    @Override
    public AstNode visitGlobalVarInitValue(
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
    public AstNode visitFunctionDecl(FrankoParser.FunctionDeclContext ctx) {
        String name = ctx.IDENTIFIER().getText();

        List<AstNode> parameters = new ArrayList<>();

        if (ctx.parameterList() != null) {
            for (FrankoParser.ParameterContext paramCtx : ctx.parameterList().parameter()) {
                parameters.add(visit(paramCtx));
            }
        }

        AstNode returnType = visit(ctx.returnType());
        AstNode body = visit(ctx.block());

        InlineMode inlineMode = InlineMode.DEFAULT;

        if (ctx.functionModifier() != null) {
            inlineMode = ctx.functionModifier().HASH_INLINE() != null
                    ? InlineMode.INLINE
                    : InlineMode.NOINLINE;
        }

        AstNode node = AstNode.functionDecl(
                span(ctx),
                name,
                parameters,
                returnType,
                body,
                inlineMode,
                genericParameters(ctx.genericParamList())
        );
        node.genericModifier = ctx.HASH_GENERIC() != null;
        node.isGeneric = node.genericModifier || !node.genericParams.isEmpty();
        return node;
    }

    @Override
    public AstNode visitParameter(FrankoParser.ParameterContext ctx) {
        AstNode type = visit(ctx.type());
        String name;

        if (ctx.SELF() != null) {
            name = "self";
        } else {
            name = ctx.IDENTIFIER().getText();
        }

        return AstNode.parameter(
                span(ctx),
                type,
                name,
                ctx.CONST() != null
        );
    }

    @Override
    public AstNode visitReturnType(FrankoParser.ReturnTypeContext ctx) {
        if (ctx.type() != null) {
            return visit(ctx.type());
        }

        if (ctx.VOID() != null) {
            return AstNode.voidType(span(ctx));
        }

        throw new RuntimeException("Unknown returnType: " + ctx.getText());
    }

    @Override
    public AstNode visitSimpleStatement(FrankoParser.SimpleStatementContext ctx) {
        return visit(ctx.simpleStmt());
    }

    @Override
    public AstNode visitIfStatement(FrankoParser.IfStatementContext ctx) {
        return visit(ctx.ifStmt());
    }

    @Override
    public AstNode visitCompileTimeIfStatement(
            FrankoParser.CompileTimeIfStatementContext ctx
    ) {
        return visit(ctx.compileTimeIfStmt());
    }

    @Override
    public AstNode visitCompileTimeIfStmt(FrankoParser.CompileTimeIfStmtContext ctx) {
        AstNode condition = visit(ctx.expr());
        AstNode thenBranch = visit(ctx.statement());
        AstNode elseBranch = null;

        if (ctx.compileTimeElseClause() != null) {
            elseBranch = visit(ctx.compileTimeElseClause().compileTimeElseBody());
        }

        return AstNode.compileTimeIf(span(ctx), condition, thenBranch, elseBranch);
    }

    @Override
    public AstNode visitCompileTimeElseBody(
            FrankoParser.CompileTimeElseBodyContext ctx
    ) {
        if (ctx.compileTimeIfStmt() != null) {
            return visit(ctx.compileTimeIfStmt());
        }

        return visit(ctx.statement());
    }

    @Override
    public AstNode visitCompileTimeDeclIf(
            FrankoParser.CompileTimeDeclIfContext ctx
    ) {
        AstNode condition = visit(ctx.expr());
        List<AstNode> thenItems = declarationItems(ctx.declIfBody());
        List<AstNode> elseItems = List.of();

        if (ctx.declIfElse() != null) {
            FrankoParser.DeclIfElseContext elseCtx = ctx.declIfElse();

            if (elseCtx.compileTimeDeclIf() != null) {
                elseItems = List.of(visit(elseCtx.compileTimeDeclIf()));
            } else {
                elseItems = declarationItems(elseCtx.declIfBody());
            }
        }

        return AstNode.declIf(span(ctx), condition, thenItems, elseItems);
    }

    private List<AstNode> declarationItems(FrankoParser.DeclIfBodyContext ctx) {
        List<AstNode> items = new ArrayList<>();

        if (ctx.declarationItem() != null) {
            for (FrankoParser.DeclarationItemContext itemCtx : ctx.declarationItem()) {
                items.add(visit(itemCtx));
            }
        }

        return items;
    }

    private List<AstNode> structMemberItems(FrankoParser.StructMemberBodyContext ctx) {
        List<AstNode> items = new ArrayList<>();

        if (ctx.structMember() != null) {
            for (FrankoParser.StructMemberContext itemCtx : ctx.structMember()) {
                items.add(visit(itemCtx));
            }
        }

        return items;
    }

    @Override
    public AstNode visitStructMemberIf(FrankoParser.StructMemberIfContext ctx) {
        AstNode condition = visit(ctx.expr());
        List<AstNode> thenItems = structMemberItems(ctx.structMemberBody());
        List<AstNode> elseItems = List.of();

        if (ctx.structMemberElse() != null) {
            FrankoParser.StructMemberElseContext elseCtx = ctx.structMemberElse();

            if (elseCtx.structMemberIf() != null) {
                elseItems = List.of(visit(elseCtx.structMemberIf()));
            } else {
                elseItems = structMemberItems(elseCtx.structMemberBody());
            }
        }

        return AstNode.declIf(span(ctx), condition, thenItems, elseItems);
    }

    @Override
    public AstNode visitStructMember(FrankoParser.StructMemberContext ctx) {
        if (ctx.structFieldDecl() != null) {
            return visit(ctx.structFieldDecl());
        }

        if (ctx.functionDecl() != null) {
            return visit(ctx.functionDecl());
        }

        if (ctx.structMemberIf() != null) {
            return visit(ctx.structMemberIf());
        }

        if (ctx.compileTimeDeclItem() != null) {
            return visit(ctx.compileTimeDeclItem());
        }

        throw new RuntimeException("Unknown structMember: " + ctx.getText());
    }

    @Override
    public AstNode visitDeclarationItem(FrankoParser.DeclarationItemContext ctx) {
        if (ctx.structDecl() != null) {
            return visit(ctx.structDecl());
        }

        if (ctx.enumDecl() != null) {
            return visit(ctx.enumDecl());
        }

        if (ctx.functionDecl() != null) {
            return visit(ctx.functionDecl());
        }

        if (ctx.globalVarDecl() != null) {
            return visit(ctx.globalVarDecl());
        }

        if (ctx.namespaceDecl() != null) {
            return visit(ctx.namespaceDecl());
        }

        if (ctx.compileTimeDeclIf() != null) {
            return visit(ctx.compileTimeDeclIf());
        }

        if (ctx.compileTimeDeclItem() != null) {
            return visit(ctx.compileTimeDeclItem());
        }

        throw new RuntimeException("Unknown declarationItem: " + ctx.getText());
    }

    @Override
    public AstNode visitCompileTimeDeclItem(
            FrankoParser.CompileTimeDeclItemContext ctx
    ) {
        String name = ctx.IDENTIFIER().getText();
        List<AstNode> args = new ArrayList<>();

        if (ctx.hashArgList() != null) {
            for (FrankoParser.HashArgContext argCtx : ctx.hashArgList().hashArg()) {
                args.add(visit(argCtx));
            }
        }

        return AstNode.compileTimeCall(span(ctx), name, args);
    }

    @Override
    public AstNode visitCompileTimeCall(FrankoParser.CompileTimeCallContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        List<AstNode> args = new ArrayList<>();

        if (ctx.hashArgList() != null) {
            for (FrankoParser.HashArgContext argCtx : ctx.hashArgList().hashArg()) {
                args.add(visit(argCtx));
            }
        }

        return AstNode.compileTimeCall(span(ctx), name, args);
    }

    @Override
    public AstNode visitCompileTimeName(FrankoParser.CompileTimeNameContext ctx) {
        return AstNode.compileTimeName(span(ctx), ctx.IDENTIFIER().getText());
    }

    @Override
    public AstNode visitHashArg(FrankoParser.HashArgContext ctx) {
        if (ctx.type() != null) {
            return visit(ctx.type());
        }

        return visit(ctx.expr());
    }

    @Override
    public AstNode visitWhileStatement(FrankoParser.WhileStatementContext ctx) {
        return visit(ctx.whileStmt());
    }

    @Override
    public AstNode visitBlockStatement(FrankoParser.BlockStatementContext ctx) {
        return visit(ctx.block());
    }

    @Override
    public AstNode visitBreakStatement(FrankoParser.BreakStatementContext ctx) {
        return visit(ctx.breakStmt());
    }

    @Override
    public AstNode visitContinueStatement(FrankoParser.ContinueStatementContext ctx) {
        return visit(ctx.continueStmt());
    }

    @Override
    public AstNode visitSimpleStmt(FrankoParser.SimpleStmtContext ctx) {
        if (ctx.varDecl() != null) return visit(ctx.varDecl());
        if (ctx.returnStmt() != null) return visit(ctx.returnStmt());
        if (ctx.assignStmt() != null) return visit(ctx.assignStmt());
        if (ctx.compoundAssignStmt() != null) return visit(ctx.compoundAssignStmt());
        if (ctx.incDecStmt() != null) return visit(ctx.incDecStmt());
        if (ctx.exprStmt() != null) return visit(ctx.exprStmt());

        throw new RuntimeException("Unknown simpleStmt: " + ctx.getText());
    }

    @Override
    public AstNode visitBlock(FrankoParser.BlockContext ctx) {
        List<AstNode> statements = new ArrayList<>();

        for (FrankoParser.StatementContext stmt : ctx.statement()) {
            AstNode node = visit(stmt);
            if (node != null) {
                statements.add(node);
            }
        }

        return AstNode.block(span(ctx), statements);
    }

    @Override
    public AstNode visitIfStmt(FrankoParser.IfStmtContext ctx) {
        AstNode condition = visit(ctx.expr());
        AstNode thenBranch = visit(ctx.statement(0));
        AstNode elseBranch = null;

        if (ctx.statement().size() > 1) {
            elseBranch = visit(ctx.statement(1));
        }

        return AstNode.ifStmt(span(ctx), condition, thenBranch, elseBranch);
    }

    @Override
    public AstNode visitWhileStmt(FrankoParser.WhileStmtContext ctx) {
        AstNode condition = visit(ctx.expr());
        AstNode body = visit(ctx.statement());

        return AstNode.whileStmt(span(ctx), condition, body, List.of());
    }

    @Override
    public AstNode visitForStatement(FrankoParser.ForStatementContext ctx) {
        return visit(ctx.forStmt());
    }

    @Override
    public AstNode visitForStmt(FrankoParser.ForStmtContext ctx) {
        AstNode init = null;

        if (ctx.forInit() != null) {
            init = visit(ctx.forInit().forHeaderStmt());
        }

        AstNode condition = visit(ctx.expr());

        AstNode update = null;

        if (ctx.forUpdate() != null) {
            update = visit(ctx.forUpdate().forHeaderStmt());
        }

        AstNode body = visit(ctx.statement());

        return AstNode.forStmt(span(ctx), init, condition, update, body);
    }

    @Override
    public AstNode visitReturnStmt(FrankoParser.ReturnStmtContext ctx) {
        AstNode value = null;

        if (ctx.expr() != null) {
            value = visit(ctx.expr());
        }

        return AstNode.returnStmt(span(ctx), value);
    }

    @Override
    public AstNode visitBreakStmt(FrankoParser.BreakStmtContext ctx) {
        AstNode level = null;

        if (ctx.constExpr() != null) {
            level = visit(ctx.constExpr());
        }

        return AstNode.breakStmt(span(ctx), level);
    }

    @Override
    public AstNode visitContinueStmt(FrankoParser.ContinueStmtContext ctx) {
        AstNode level = null;

        if (ctx.constExpr() != null) {
            level = visit(ctx.constExpr());
        }

        return AstNode.continueStmt(span(ctx), level);
    }

    @Override
    public AstNode visitAssignStmt(FrankoParser.AssignStmtContext ctx) {
        AstNode target = visit(ctx.lvalue());
        AstNode value = visit(ctx.expr());

        return AstNode.assign(span(ctx), "=", target, value);
    }

    @Override
    public AstNode visitCompoundAssignStmt(
            FrankoParser.CompoundAssignStmtContext ctx
    ) {
        AstNode target = visit(ctx.lvalue());
        AstNode value = visit(ctx.expr());

        return AstNode.assign(
            span(ctx),
            ctx.compoundAssignOp().getText(),
            target,
            value
        );
    }

    @Override
    public AstNode visitIncDecStmt(FrankoParser.IncDecStmtContext ctx) {
        AstNode target = visit(ctx.lvalue());

        return AstNode.incDec(span(ctx), ctx.PLUSPLUS() != null ? "++" : "--", target);
    }

    @Override
    public AstNode visitExprStmt(FrankoParser.ExprStmtContext ctx) {
        return AstNode.exprStmt(span(ctx), visit(ctx.expr()));
    }

    @Override
    public AstNode visitInt8Type(FrankoParser.Int8TypeContext ctx) {
        return AstNode.primitiveType(span(ctx), PrimitiveKind.INT8);
    }

    @Override
    public AstNode visitInt16Type(FrankoParser.Int16TypeContext ctx) {
        return AstNode.primitiveType(span(ctx), PrimitiveKind.INT16);
    }

    @Override
    public AstNode visitInt32Type(FrankoParser.Int32TypeContext ctx) {
        return AstNode.primitiveType(span(ctx), PrimitiveKind.INT32);
    }

    @Override
    public AstNode visitInt64Type(FrankoParser.Int64TypeContext ctx) {
        return AstNode.primitiveType(span(ctx), PrimitiveKind.INT64);
    }

    @Override
    public AstNode visitUint8Type(FrankoParser.Uint8TypeContext ctx) {
        return AstNode.primitiveType(span(ctx), PrimitiveKind.UINT8);
    }

    @Override
    public AstNode visitUint16Type(FrankoParser.Uint16TypeContext ctx) {
        return AstNode.primitiveType(span(ctx), PrimitiveKind.UINT16);
    }

    @Override
    public AstNode visitUint32Type(FrankoParser.Uint32TypeContext ctx) {
        return AstNode.primitiveType(span(ctx), PrimitiveKind.UINT32);
    }

    @Override
    public AstNode visitUint64Type(FrankoParser.Uint64TypeContext ctx) {
        return AstNode.primitiveType(span(ctx), PrimitiveKind.UINT64);
    }

    @Override
    public AstNode visitDynamicArrayType(FrankoParser.DynamicArrayTypeContext ctx) {
        AstNode elementType = visit(ctx.type());
        return AstNode.dynamicArrayType(span(ctx), elementType);
    }

    @Override
    public AstNode visitExplicitArraySize(FrankoParser.ExplicitArraySizeContext ctx) {
        return visit(ctx.constExpr());
    }

    @Override
    public AstNode visitInferredArraySize(FrankoParser.InferredArraySizeContext ctx) {
        return AstNode.inferredSize(span(ctx));
    }

    @Override
    public AstNode visitStaticArrayType(FrankoParser.StaticArrayTypeContext ctx) {
        AstNode elementType = visit(ctx.type());
        AstNode sizeExpr = visit(ctx.arraySize());

        return AstNode.staticArrayType(span(ctx), elementType, sizeExpr);
    }

    @Override
    public AstNode visitPtrType(FrankoParser.PtrTypeContext ctx) {
        AstNode referencedType = visit(ctx.type());
        return AstNode.ptrType(span(ctx), PtrPermission.OWNER, referencedType);
    }

    @Override
    public AstNode visitMViewType(FrankoParser.MViewTypeContext ctx) {
        AstNode referencedType = visit(ctx.type());
        return AstNode.ptrType(span(ctx), PtrPermission.MUTABLE, referencedType);
    }

    @Override
    public AstNode visitViewType(FrankoParser.ViewTypeContext ctx) {
        AstNode referencedType = visit(ctx.type());
        return AstNode.ptrType(span(ctx), PtrPermission.READ_ONLY, referencedType);
    }

    @Override
    public AstNode visitStructType(FrankoParser.StructTypeContext ctx) {
        return AstNode.structType(
                span(ctx),
                List.of(),
                ctx.IDENTIFIER().getText(),
                genericArguments(ctx.genericArgList())
        );
    }

    @Override
    public AstNode visitTupleType(FrankoParser.TupleTypeContext ctx) {
        return tupleTypeNode(ctx.tupleTypeElement(), span(ctx));
    }

    private AstNode tupleTypeNode(
            List<FrankoParser.TupleTypeElementContext> elementCtxs,
            SourceSpan span
    ) {
        List<AstNode> elements = new ArrayList<>();

        for (FrankoParser.TupleTypeElementContext elementCtx : elementCtxs) {
            AstNode elementType = visit(elementCtx.type());
            String elementName = elementCtx.IDENTIFIER() == null
                    ? null
                    : elementCtx.IDENTIFIER().getText();

            elements.add(AstNode.tupleTypeElement(
                    span(elementCtx),
                    elementType,
                    elementName
            ));
        }

        return AstNode.tupleType(span, elements);
    }

    @Override
    public AstNode visitAggregateLiteral(
            FrankoParser.AggregateLiteralContext ctx
    ) {
        AstNode type;
        boolean structLiteral = ctx.structTypeName() != null;

        if (structLiteral) {
            FrankoParser.StructTypeNameContext nameCtx = ctx.structTypeName();

            if (nameCtx.qualifiedName() != null) {
                List<String> segments =
                        qualifiedNameSegments(nameCtx.qualifiedName());
                List<String> namespaceSegments =
                        new ArrayList<>(segments.subList(0, segments.size() - 1));
                type = AstNode.structType(
                        span(nameCtx),
                        namespaceSegments,
                        segments.get(segments.size() - 1),
                        genericArguments(nameCtx.genericArgList())
                );
            } else {
                type = AstNode.structType(
                        span(nameCtx),
                        List.of(),
                        nameCtx.IDENTIFIER().getText(),
                        genericArguments(nameCtx.genericArgList())
                );
            }
        } else {
            type = tupleTypeNode(
                    ctx.tupleTypeElement(),
                    span(ctx)
            );
        }

        return AstNode.aggregateLiteral(
                span(ctx),
                type,
                structLiteral
                        ? fieldInits(ctx.fieldInitList())
                        : tupleInits(ctx.tupleInitList())
        );
    }

    private List<AstNode> fieldInits(
            FrankoParser.FieldInitListContext ctx
    ) {
        return initItems(ctx == null ? null : ctx.initItem());
    }

    private List<AstNode> tupleInits(
            FrankoParser.TupleInitListContext ctx
    ) {
        return initItems(ctx == null ? null : ctx.initItem());
    }

    private List<AstNode> declInits(
            FrankoParser.DeclInitListContext ctx
    ) {
        return initItems(ctx == null ? null : ctx.initItem());
    }

    private List<AstNode> initItems(
            List<FrankoParser.InitItemContext> items
    ) {
        List<AstNode> fields = new ArrayList<>();

        if (items == null) {
            return fields;
        }

        for (FrankoParser.InitItemContext item : items) {
            if (item.fieldInit() != null) {
                FrankoParser.FieldInitContext fieldCtx = item.fieldInit();

                fields.add(AstNode.fieldInit(
                        span(fieldCtx),
                        fieldCtx.IDENTIFIER().getText(),
                        visit(fieldCtx.expr()),
                        false
                ));
            } else {
                fields.add(AstNode.fieldInit(
                        span(item),
                        null,
                        visit(item.expr()),
                        true
                ));
            }
        }

        return fields;
    }

    @Override
    public AstNode visitVarDecl(FrankoParser.VarDeclContext ctx) {
        AstNode type = visit(ctx.type());
        String name = ctx.IDENTIFIER().getText();

        boolean isConst = ctx.CONST() != null;

        if (ctx.declSuffix() == null) {
            return AstNode.varDecl(span(ctx), type, name, isConst);
        }

        if (ctx.declSuffix() instanceof FrankoParser.DeclFieldInitContext) {
            FrankoParser.DeclFieldInitContext fieldCtx =
                    (FrankoParser.DeclFieldInitContext) ctx.declSuffix();
            return AstNode.varDeclFieldsInit(
                    span(ctx),
                    type,
                    name,
                    declInits(fieldCtx.declInitList()),
                    isConst
            );
        }

        FrankoParser.DeclAssignInitContext initCtx =
            (FrankoParser.DeclAssignInitContext) ctx.declSuffix();

        AstNode init = visit(initCtx.expr());

        return AstNode.varDeclInit(span(ctx), type, name, init, isConst);
    }

    @Override
    public AstNode visitLvalue(FrankoParser.LvalueContext ctx) {
        AstNode base = visit(ctx.lvalueAtom());
        return applyLvalueSuffixes(base, ctx.lvalueSuffix());
    }

    @Override
    public AstNode visitLvalueAtom(FrankoParser.LvalueAtomContext ctx) {
        if (ctx.IDENTIFIER() != null) {
            return AstNode.var(
                    span(ctx.IDENTIFIER().getSymbol()),
                    ctx.IDENTIFIER().getText()
            );
        }

        if (ctx.SELF() != null) {
            return AstNode.var(span(ctx.SELF().getSymbol()), "self");
        }

        if (ctx.qualifiedName() != null) {
            return AstNode.qualifiedName(
                    span(ctx),
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
    public AstNode visitExpr(FrankoParser.ExprContext ctx) {
        return visit(ctx.logicalOrExpr());
    }

    @Override
    public AstNode visitLogicalOrExpr(FrankoParser.LogicalOrExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitLogicalAndExpr(FrankoParser.LogicalAndExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitBitwiseOrExpr(FrankoParser.BitwiseOrExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitBitwiseXorExpr(FrankoParser.BitwiseXorExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitBitwiseAndExpr(FrankoParser.BitwiseAndExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitEqualityExpr(FrankoParser.EqualityExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitRelationalExpr(FrankoParser.RelationalExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitShiftExpr(FrankoParser.ShiftExprContext ctx) {
        return foldShiftExpr(ctx);
    }

    @Override
    public AstNode visitAdditiveExpr(FrankoParser.AdditiveExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitMultiplicativeExpr(FrankoParser.MultiplicativeExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitUnaryMinus(FrankoParser.UnaryMinusContext ctx) {
        return AstNode.unary(span(ctx), "-", visit(ctx.unaryExpr()));
    }

    @Override
    public AstNode visitLogicalNot(FrankoParser.LogicalNotContext ctx) {
        return AstNode.unary(span(ctx), "!", visit(ctx.unaryExpr()));
    }

    @Override
    public AstNode visitBitwiseNot(FrankoParser.BitwiseNotContext ctx) {
        return AstNode.unary(span(ctx), "~", visit(ctx.unaryExpr()));
    }

    @Override
    public AstNode visitPostfixExprOnly(FrankoParser.PostfixExprOnlyContext ctx) {
        return visit(ctx.postfixExpr());
    }

    @Override
    public AstNode visitConstExpr(FrankoParser.ConstExprContext ctx) {
        return visit(ctx.constLogicalOrExpr());
    }

    @Override
    public AstNode visitConstLogicalOrExpr(FrankoParser.ConstLogicalOrExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitConstLogicalAndExpr(FrankoParser.ConstLogicalAndExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitConstBitwiseOrExpr(FrankoParser.ConstBitwiseOrExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitConstBitwiseXorExpr(FrankoParser.ConstBitwiseXorExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitConstBitwiseAndExpr(FrankoParser.ConstBitwiseAndExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitConstEqualityExpr(FrankoParser.ConstEqualityExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitConstRelationalExpr(FrankoParser.ConstRelationalExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitConstShiftExpr(FrankoParser.ConstShiftExprContext ctx) {
        return foldConstShiftExpr(ctx);
    }

    @Override
    public AstNode visitConstAdditiveExpr(FrankoParser.ConstAdditiveExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitConstMultiplicativeExpr(FrankoParser.ConstMultiplicativeExprContext ctx) {
        return foldSimpleBinaryRule(ctx);
    }

    @Override
    public AstNode visitConstUnaryExpr(FrankoParser.ConstUnaryExprContext ctx) {
        if (ctx.MINUS() != null) {
            return AstNode.unary(span(ctx), "-", visit(ctx.constUnaryExpr()));
        }

        if (ctx.BANG() != null) {
            return AstNode.unary(span(ctx), "!", visit(ctx.constUnaryExpr()));
        }

        if (ctx.TILDE() != null) {
            return AstNode.unary(span(ctx), "~", visit(ctx.constUnaryExpr()));
        }

        if (ctx.constPrimary() != null) {
            return visit(ctx.constPrimary());
        }

        throw new RuntimeException("Unknown constUnaryExpr: " + ctx.getText());
    }

    @Override
    public AstNode visitConstPrimary(FrankoParser.ConstPrimaryContext ctx) {
        if (ctx.integerLiteral() != null) {
            return visit(ctx.integerLiteral());
        }

        if (ctx.IDENTIFIER() != null) {
            return AstNode.var(
                    span(ctx.IDENTIFIER().getSymbol()),
                    ctx.IDENTIFIER().getText()
            );
        }

        if (ctx.constExpr() != null) {
            return visit(ctx.constExpr());
        }

        throw new RuntimeException("Unknown constPrimary: " + ctx.getText());
    }

    @Override
    public AstNode visitArrayLiteral(FrankoParser.ArrayLiteralContext ctx) {
        List<AstNode> elements = new ArrayList<>();

        if (ctx.arrayLiteralElements() != null) {
            for (FrankoParser.ArrayLiteralElementContext elementCtx
                    : ctx.arrayLiteralElements().arrayLiteralElement()) {
                elements.add(visit(elementCtx));
            }
        }

        return AstNode.arrayLiteral(span(ctx), elements);
    }

    @Override
    public AstNode visitArrayLiteralElement(FrankoParser.ArrayLiteralElementContext ctx) {
        if (ctx.arrayLiteral() != null) {
            return visit(ctx.arrayLiteral());
        }

        if (ctx.expr() != null) {
            return visit(ctx.expr());
        }

        throw new RuntimeException("Unknown arrayLiteralElement: " + ctx.getText());
    }

    @Override
    public AstNode visitPostfixExpr(FrankoParser.PostfixExprContext ctx) {
        AstNode base = visit(ctx.primary());
        return applyPostfixSuffixes(base, ctx.postfixSuffix());
    }

    @Override
    public AstNode visitPrimary(FrankoParser.PrimaryContext ctx) {
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

        if (ctx.hashExpr() != null) {
            return visit(ctx.hashExpr());
        }

        if (ctx.aggregateLiteral() != null) {
            return visit(ctx.aggregateLiteral());
        }

        if (ctx.IDENTIFIER() != null) {
            return AstNode.var(
                    span(ctx.IDENTIFIER().getSymbol()),
                    ctx.IDENTIFIER().getText()
            );
        }

        if (ctx.SELF() != null) {
            return AstNode.var(span(ctx.SELF().getSymbol()), "self");
        }

        if (ctx.qualifiedName() != null) {
            return AstNode.qualifiedName(
                    span(ctx),
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
    public AstNode visitNullLiteral(FrankoParser.NullLiteralContext ctx) {
        return AstNode.nullLiteral(span(ctx));
    }

    @Override
    public AstNode visitGetPtrExpr(FrankoParser.GetPtrExprContext ctx) {
        AstNode target = visit(ctx.lvalue());
        return AstNode.getPtr(span(ctx), PtrPermission.OWNER, target);
    }

    @Override
    public AstNode visitGetViewExpr(FrankoParser.GetViewExprContext ctx) {
        AstNode target = visit(ctx.lvalue());
        return AstNode.getPtr(span(ctx), PtrPermission.READ_ONLY, target);
    }

    @Override
    public AstNode visitGetMViewExpr(FrankoParser.GetMViewExprContext ctx) {
        AstNode target = visit(ctx.lvalue());
        return AstNode.getPtr(span(ctx), PtrPermission.MUTABLE, target);
    }

    @Override
    public AstNode visitDerefExpr(FrankoParser.DerefExprContext ctx) {
        AstNode expr = visit(ctx.expr());
        return AstNode.deref(span(ctx), expr);
    }

    @Override
    public AstNode visitCastExpr(FrankoParser.CastExprContext ctx) {
        AstNode target = visit(ctx.type());
        return AstNode.cast(span(ctx), target, visit(ctx.expr()), false);
    }

    @Override
    public AstNode visitUnsafeCastExpr(FrankoParser.UnsafeCastExprContext ctx) {
        AstNode target = visit(ctx.type());

        return AstNode.cast(span(ctx), target, visit(ctx.expr()), true);
    }

    @Override
    public AstNode visitIntegerLiteral(FrankoParser.IntegerLiteralContext ctx) {
        return AstNode.intLiteral(span(ctx.getStart()), ctx.getText());
    }

    @Override
    public AstNode visitStringLiteral(FrankoParser.StringLiteralContext ctx) {
        return AstNode.stringLiteral(
                span(ctx.STRING_LITERAL().getSymbol()),
                ctx.STRING_LITERAL().getText()
        );
    }
}
