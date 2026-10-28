import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Desugarer {

    private final DiagnosticBag diagnostics;

    private final Map<String, GlobalVarDeclNode> hoistedStringGlobals =
        new LinkedHashMap<>();

    public Desugarer() {
        this(new DiagnosticBag("Desugaring failed:"));
    }

    public Desugarer(DiagnosticBag diagnostics) {
        this.diagnostics = diagnostics == null
                ? new DiagnosticBag("Desugaring failed:")
                : diagnostics;
    }

    public boolean hasErrors() {
        return diagnostics.hasErrors();
    }

    public String formatErrors() {
        return diagnostics.formatErrors();
    }

    private static final class InferenceFailure extends RuntimeException {
        InferenceFailure(String message) {
            super(message);
        }
    }

    public ASTNode desugar(ASTNode root) {
        if (root == null) return null;

        if (root instanceof ProgramNode) {
            ProgramNode p = (ProgramNode) root;
            List<ASTNode> items =
                desugarTopLevelItemList(p.topLevelItems);

            items.addAll(0, hoistedStringGlobals.values());

            return new ProgramNode(items);
        }

        return desugarStmt(root);
    }

    private ASTNode hoistStringLiteral(StringLiteralNode literal) {
        List<BigInteger> bytes =
            StringLiteralDecoder.toUtf8Bytes(literal);

        if (bytes.isEmpty()) {
            diagnostics.error("Empty string literals are not supported: "
                + "a string literal is a view<array<byte, N>> value with "
                + "N >= 1");
            bytes = new ArrayList<>();
            bytes.add(BigInteger.ZERO);
        }

        StringBuilder contentKey = new StringBuilder();

        for (BigInteger b : bytes) {
            contentKey.append(b.toString(10)).append(',');
        }

        String key = contentKey.toString();

        GlobalVarDeclNode decl = hoistedStringGlobals.get(key);

        if (decl == null) {
            decl = new GlobalVarDeclNode(
                new StaticArrayTypeNode(
                    new PrimitiveTypeNode(PrimitiveKind.UINT8),
                    new IntNode(Integer.toString(bytes.size()))
                ),
                "_STR_" + Mangler.hashHex(key),
                null
            );

            decl.literalBytes = bytes;
            hoistedStringGlobals.put(key, decl);
        }

        return new GetPtrNode(
            PtrPermission.READ_ONLY,
            new VarNode(decl.name)
        );
    }

    private List<ASTNode> desugarTopLevelItemList(List<ASTNode> items) {
        List<ASTNode> out = new ArrayList<>();

        for (ASTNode item : items) {
            appendDesugaredTopLevelItem(item, out);
        }

        return out;
    }

    private void appendDesugaredTopLevelItem(ASTNode item, List<ASTNode> out) {
        if (item == null) return;

        if (item instanceof ImportNode) {
            throw new RuntimeException(
                "Internal desugarer error: unresolved ImportNode '"
                    + ((ImportNode) item).rawPathText
                    + "' reached desugaring"
            );
        }

        if (item instanceof FunctionDeclNode) {
            out.add(desugarFunctionDecl((FunctionDeclNode) item));
            return;
        }

        if (item instanceof StructDeclNode) {
            out.add(desugarStructDecl((StructDeclNode) item));
            return;
        }

        if (item instanceof GlobalVarDeclNode) {
            out.add(desugarGlobalVarDecl((GlobalVarDeclNode) item));
            return;
        }

        if (item instanceof NamespaceNode) {
            out.add(desugarNamespaceDecl((NamespaceNode) item));
            return;
        }

        appendDesugaredStmt(item, out);
    }

    private NamespaceNode desugarNamespaceDecl(NamespaceNode n) {
        return new NamespaceNode(n.name, desugarTopLevelItemList(n.items));
    }

    private StructDeclNode desugarStructDecl(StructDeclNode n) {
        List<StructFieldDeclNode> fields = new ArrayList<>();

        for (StructFieldDeclNode field : n.fields) {
            fields.add(new StructFieldDeclNode(
                desugarType(field.type),
                field.name,
                evalConstExpr(field.constInitializer, "struct field initializer")
            ));
        }

        List<FunctionDeclNode> methods = new ArrayList<>();

        for (FunctionDeclNode method : n.methods) {
            methods.add(desugarFunctionDecl(method));
        }

        return new StructDeclNode(n.name, fields, methods);
    }

    private GlobalVarDeclNode desugarGlobalVarDecl(GlobalVarDeclNode n) {
        if (usesInferredSize(n.type)) {
            diagnostics.error("'?' array size is not allowed for global variable '"
                + n.name
                + "'; '?' is only allowed in a local declaration with an array-literal initializer");

            return new GlobalVarDeclNode(
                desugarType(replaceInferredSizesWithFallback(n.type)),
                n.name,
                evalConstExpr(n.constInitializer, "global variable initializer")
            );
        }

        TypeNode type = desugarType(n.type);
        ASTNode init = n.constInitializer;

        if (init instanceof StringLiteralNode s) {
            ASTNode hoisted = hoistStringLiteral(s);

            if (!(type instanceof PtrTypeNode)) {
                diagnostics.error("Global variable '"
                    + n.name
                    + "': a string literal is a view<array<byte, N>> value "
                    + "and cannot initialize a non-pointer global");

                return new GlobalVarDeclNode(type, n.name, null);
            }

            return new GlobalVarDeclNode(type, n.name, hoisted);
        }

        if (init instanceof GetPtrNode) {
            if (!(type instanceof PtrTypeNode)) {
                diagnostics.error("Global variable '"
                    + n.name
                    + "': only pointer-typed globals may be initialized with "
                    + "getptr/getmview/getview");

                return new GlobalVarDeclNode(type, n.name, null);
            }

            return new GlobalVarDeclNode(
                type,
                n.name,
                desugarExpr(init)
            );
        }

        if (init instanceof NullNode) {
            if (!(type instanceof PtrTypeNode)) {
                diagnostics.error("Global variable '"
                    + n.name
                    + "': null is only valid for pointer types");

                return new GlobalVarDeclNode(type, n.name, null);
            }

            return new GlobalVarDeclNode(type, n.name, null);
        }

        return new GlobalVarDeclNode(
            type,
            n.name,
            evalConstExpr(init, "global variable initializer")
        );
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

    private List<ASTNode> desugarStmtList(List<ASTNode> stmts) {
        List<ASTNode> out = new ArrayList<>();

        for (ASTNode stmt : stmts) {
            appendDesugaredStmt(stmt, out);
        }

        return out;
    }

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

        if (stmt instanceof IncDecNode) {
            out.add(lowerIncDec((IncDecNode) stmt));
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
                desugarControlledStmt(n.thenBranch),
                n.elseBranch != null
                        ? desugarControlledStmt(n.elseBranch)
                        : null
            ));

            return;
        }

        if (stmt instanceof WhileNode) {
            WhileNode n = (WhileNode) stmt;

            List<ASTNode> loweredUpdate = new ArrayList<>();

            for (ASTNode updateStmt : n.update) {
                appendDesugaredStmt(updateStmt, loweredUpdate);
            }

            out.add(new WhileNode(
                desugarExpr(n.condition),
                desugarControlledStmt(n.body),
                loweredUpdate
            ));

            return;
        }

        if (stmt instanceof ForNode) {
            out.add(desugarForStmt((ForNode) stmt));
            return;
        }

        if (stmt instanceof BreakNode) {
            BreakNode n = (BreakNode) stmt;

            out.add(new BreakNode(
                n.level != null ? desugarExpr(n.level) : null
            ));
            return;
        }

        if (stmt instanceof ContinueNode) {
            ContinueNode n = (ContinueNode) stmt;

            out.add(new ContinueNode(
                n.level != null ? desugarExpr(n.level) : null
            ));
            return;
        }

        out.add(desugarStmt(stmt));
    }

    private void appendDesugaredVarDeclInit(
        VarDeclInitNode n,
        List<ASTNode> out
    ) {
        ASTNode rawInit = n.init;

        TypeNode loweredType;

        if (usesInferredSize(n.type)) {
            loweredType = inferStaticSizes(n.type, rawInit, n.name);
        } else {
            loweredType = desugarType(n.type);
        }

        out.add(new VarDeclNode(
            loweredType,
            n.name
        ));

        if (rawInit == null) {
            return;
        }

        ASTNode target = new VarNode(n.name);

        if (rawInit instanceof ArrayLiteralNode) {
            appendArrayInitializerAssignments(
                target,
                (ArrayLiteralNode) rawInit,
                out
            );
            return;
        }

        out.add(new AssignNode(
            target,
            desugarExpr(rawInit)
        ));
    }

    private void appendDesugaredVarDeclArrayInit(
        VarDeclArrayInitNode n,
        List<ASTNode> out
    ) {
        if (usesInferredSize(n.type)) {
            diagnostics.error("'?' array size for '"
                + n.name
                + "' requires an array-literal initializer, not a size argument");

            out.add(new VarDeclNode(
                desugarType(replaceInferredSizesWithFallback(n.type)),
                n.name
            ));

            return;
        }

        out.add(new VarDeclNode(
            desugarType(n.type),
            n.name
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

    private boolean usesInferredSize(TypeNode type) {
        if (type == null) {
            return false;
        }

        if (type instanceof StaticArrayTypeNode s) {
            return s.sizeExpr instanceof InferredSizeNode
                || usesInferredSize(s.elementType);
        }

        if (type instanceof DynamicArrayTypeNode d) {
            return usesInferredSize(d.elementType);
        }

        if (type instanceof PtrTypeNode a) {
            return usesInferredSize(a.referencedType);
        }

        return false;
    }

    private boolean isFullyStaticArrayNest(TypeNode type) {
        if (type instanceof StaticArrayTypeNode s) {
            return isFullyStaticArrayNest(s.elementType);
        }

        if (type instanceof DynamicArrayTypeNode) {
            return false;
        }

        if (type instanceof PtrTypeNode) {
            return false;
        }

        return true;
    }

    private boolean isStaticArrayLevel(TypeNode type) {
        return type instanceof StaticArrayTypeNode;
    }

    private TypeNode inferStaticSizes(TypeNode declared, ASTNode init, String varName) {
        if (!isFullyStaticArrayNest(declared)) {
            diagnostics.error("'?' size inference for '"
                + varName
                + "' requires a fully static array type");

            return desugarType(replaceInferredSizesWithFallback(declared));
        }

        if (init instanceof StringLiteralNode) {
            diagnostics.error("'?' array size for '"
                + varName
                + "' cannot be inferred from a string literal: a string "
                + "literal is a view<array<byte, N>> value; declare the size "
                + "explicitly and copy the bytes with '"
                + varName
                + ".memcpy(\"...\")'");

            return desugarType(replaceInferredSizesWithFallback(declared));
        }

        ArrayLiteralNode literal = normalizeInitializer(init);

        if (literal == null) {
            diagnostics.error("'?' array size for '"
                + varName
                + "' requires an array-literal initializer");

            return desugarType(replaceInferredSizesWithFallback(declared));
        }

        try {
            return desugarType(inferLevel(declared, literal, varName));
        } catch (InferenceFailure f) {
            diagnostics.error(f.getMessage());

            return desugarType(replaceInferredSizesWithFallback(declared));
        }
    }

    private ArrayLiteralNode normalizeInitializer(ASTNode init) {
        if (init instanceof ArrayLiteralNode lit) {
            return lit;
        }

        return null;
    }

    private TypeNode inferLevel(TypeNode type, ArrayLiteralNode literal, String varName) {
        if (!(type instanceof StaticArrayTypeNode s)) {
            throw new InferenceFailure("Internal inference error: expected a static array level for '"
                + varName
                + "'");
        }

        int count = literal.elements.size();

        ASTNode sizeNode;

        if (s.sizeExpr instanceof InferredSizeNode) {
            if (count == 0) {
                throw new InferenceFailure("'?' array size for '"
                    + varName
                    + "' cannot be inferred from an empty initializer list");
            }

            sizeNode = new IntNode(Integer.toString(count));
        } else {
            BigInteger declared = ConstExpressionEvaluator.tryFold(s.sizeExpr);

            if (declared != null && !declared.equals(BigInteger.valueOf(count))) {
                throw new InferenceFailure("Initializer for '"
                    + varName
                    + "' has "
                    + count
                    + " elements but declared size is "
                    + declared
                    + " (partial initialization is not allowed when '?' is used)");
            }

            sizeNode = s.sizeExpr;
        }

        TypeNode inferredElem = s.elementType;

        if (isStaticArrayLevel(s.elementType)) {
            List<ArrayLiteralNode> rows = new ArrayList<>();

            for (ASTNode element : literal.elements) {
                ArrayLiteralNode row = normalizeInitializer(element);

                if (row == null) {
                    throw new InferenceFailure("'?' array size inference for '"
                        + varName
                        + "' requires nested array literals");
                }

                rows.add(row);
            }

            requireRectangular(rows, varName);

            inferredElem = inferLevel(s.elementType, rows.get(0), varName);

            for (int i = 1; i < rows.size(); i++) {
                checkLevel(inferredElem, rows.get(i), varName);
            }
        }

        return new StaticArrayTypeNode(inferredElem, sizeNode);
    }

    private void checkLevel(TypeNode concrete, ArrayLiteralNode literal, String varName) {
        if (!(concrete instanceof StaticArrayTypeNode s)) {
            return;
        }

        int count = literal.elements.size();
        BigInteger expected = ConstExpressionEvaluator.tryFold(s.sizeExpr);

        if (expected != null && !expected.equals(BigInteger.valueOf(count))) {
            throw new InferenceFailure("Initializer for '"
                + varName
                + "' has "
                + count
                + " elements but inferred size is "
                + expected);
        }

        if (!isStaticArrayLevel(s.elementType)) {
            return;
        }

        List<ArrayLiteralNode> rows = new ArrayList<>();

        for (ASTNode element : literal.elements) {
            ArrayLiteralNode row = normalizeInitializer(element);

            if (row == null) {
                throw new InferenceFailure("'?' array size inference for '"
                    + varName
                    + "' requires nested array literals");
            }

            rows.add(row);
        }

        requireRectangular(rows, varName);

        for (ArrayLiteralNode row : rows) {
            checkLevel(s.elementType, row, varName);
        }
    }

    private void requireRectangular(List<ArrayLiteralNode> rows, String varName) {
        if (rows.isEmpty()) {
            return;
        }

        int first = rows.get(0).elements.size();

        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).elements.size() != first) {
                throw new InferenceFailure("'?' array size inference for '"
                    + varName
                    + "' requires a rectangular initializer list");
            }
        }
    }

    private TypeNode replaceInferredSizesWithFallback(TypeNode type) {
        if (type instanceof StaticArrayTypeNode s) {
            ASTNode size = s.sizeExpr instanceof InferredSizeNode
                    ? new IntNode("1")
                    : s.sizeExpr;

            return new StaticArrayTypeNode(
                replaceInferredSizesWithFallback(s.elementType),
                size
            );
        }

        if (type instanceof DynamicArrayTypeNode d) {
            return new DynamicArrayTypeNode(
                replaceInferredSizesWithFallback(d.elementType)
            );
        }

        if (type instanceof PtrTypeNode a) {
            return new PtrTypeNode(
                a.kind,
                replaceInferredSizesWithFallback(a.referencedType)
            );
        }

        return type;
    }

    private void appendDesugaredAssign(
        AssignNode n,
        List<ASTNode> out
    ) {
        ASTNode target = desugarLValue(n.target);

        if (!"=".equals(n.op)) {
            out.add(new AssignNode(
                n.op,
                target,
                desugarExpr(n.value)
            ));
            return;
        }

        ASTNode value = n.value;

        if (value instanceof ArrayLiteralNode) {
            appendArrayInitializerAssignments(
                target,
                (ArrayLiteralNode) value,
                out
            );
            return;
        }

        out.add(new AssignNode(
            n.op,
            target,
            desugarExpr(value)
        ));
    }

    private ASTNode lowerIncDec(IncDecNode n) {
        return new AssignNode(
            "++".equals(n.op) ? "+=" : "-=",
            desugarLValue(n.target),
            new IntNode("1")
        );
    }

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

            if (!"=".equals(n.op)) {

                return new AssignNode(
                    n.op,
                    desugarLValue(n.target),
                    desugarExpr(n.value)
                );
            }

            ASTNode value = n.value;

            if (value instanceof ArrayLiteralNode) {
                List<ASTNode> lowered = new ArrayList<>();
                appendDesugaredAssign(n, lowered);
                return new BlockNode(lowered);
            }

            return new AssignNode(
                n.op,
                desugarLValue(n.target),
                desugarExpr(value)
            );
        }

        if (stmt instanceof IncDecNode) {
            return lowerIncDec((IncDecNode) stmt);
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
                desugarControlledStmt(n.thenBranch),
                n.elseBranch != null
                        ? desugarControlledStmt(n.elseBranch)
                        : null
            );
        }

        if (stmt instanceof WhileNode) {
            WhileNode n = (WhileNode) stmt;

            List<ASTNode> loweredUpdate = new ArrayList<>();

            for (ASTNode updateStmt : n.update) {
                appendDesugaredStmt(updateStmt, loweredUpdate);
            }

            return new WhileNode(
                desugarExpr(n.condition),
                desugarControlledStmt(n.body),
                loweredUpdate
            );
        }

        if (stmt instanceof ForNode) {
            return desugarForStmt((ForNode) stmt);
        }

        if (stmt instanceof BreakNode) {
            BreakNode n = (BreakNode) stmt;

            return new BreakNode(
                n.level != null ? desugarExpr(n.level) : null
            );
        }

        if (stmt instanceof ContinueNode) {
            ContinueNode n = (ContinueNode) stmt;

            return new ContinueNode(
                n.level != null ? desugarExpr(n.level) : null
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

        if (stmt instanceof VarDeclNode) {
            VarDeclNode n = (VarDeclNode) stmt;

            return new VarDeclNode(
                desugarType(n.type),
                n.name
            );
        }

        throw new RuntimeException(
            "Unsupported statement in desugarer: " + stmt.getClass().getSimpleName()
        );
    }

    private ASTNode desugarControlledStmt(ASTNode stmt) {
        ASTNode lowered = desugarStmt(stmt);

        if (lowered instanceof BlockNode) {
            return lowered;
        }

        List<ASTNode> wrapped = new ArrayList<>();
        wrapped.add(lowered);

        return new BlockNode(wrapped);
    }

    private ASTNode desugarForStmt(ForNode n) {
        List<ASTNode> outer = new ArrayList<>();

        if (n.init != null) {
            appendDesugaredStmt(n.init, outer);
        }

        ASTNode loweredBody = desugarControlledStmt(n.body);

        if (!(loweredBody instanceof BlockNode)) {
            throw new RuntimeException(
                "Internal desugarer error: for-loop body did not lower to a block"
            );
        }

        BlockNode bodyBlock = (BlockNode) loweredBody;

        List<ASTNode> loweredUpdate = new ArrayList<>();

        if (n.update != null) {
            appendDesugaredStmt(n.update, loweredUpdate);
        }

        outer.add(new WhileNode(
            desugarExpr(n.condition),
            bodyBlock,
            loweredUpdate
        ));

        return new BlockNode(outer);
    }

    private void appendArrayInitializerAssignments(
        ASTNode target,
        ArrayLiteralNode literal,
        List<ASTNode> out
    ) {
        ASTNode loweredTarget = desugarLValue(target);

        if (literal.elements.isEmpty()) {
            out.add(new ExprStmtNode(loweredTarget));
            return;
        }

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

    private ASTNode desugarExpr(ASTNode expr) {
        if (expr == null) return null;

        if (expr instanceof IntNode) {
            return expr;
        }

        if (expr instanceof StringLiteralNode) {
            return hoistStringLiteral((StringLiteralNode) expr);
        }

        if (expr instanceof VarNode) {
            return expr;
        }

        if (expr instanceof QualifiedNameNode) {
            return expr;
        }

        if (expr instanceof NullNode) {
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

        if (expr instanceof GetPtrNode) {
            GetPtrNode n = (GetPtrNode) expr;

            return new GetPtrNode(
                n.kind,
                desugarLValue(n.target)
            );
        }

        if (expr instanceof DerefNode) {
            DerefNode n = (DerefNode) expr;

            return new DerefNode(
                desugarExpr(n.expr)
            );
        }

        if (expr instanceof CastNode) {
            CastNode n = (CastNode) expr;

            return new CastNode(
                desugarType(n.target),
                desugarExpr(n.expr),
                n.unsafe
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

    private ASTNode evalConstExpr(ASTNode expr, String role) {
        if (expr == null) return null;

        try {
            return new IntNode(
                ConstExpressionEvaluator.evaluatePure(expr).toString(10)
            );
        } catch (ConstExpressionEvaluator.UnevaluableConstant e) {
            if (e.getDiagnostic() != null) {
                diagnostics.error(e.getDiagnostic());
            } else {
                diagnostics.error("Invalid constant expression for " + role);
            }

            return new IntNode("1");
        }
    }

    private ASTNode desugarLValue(ASTNode node) {
        if (node == null) return null;

        if (node instanceof VarNode) {
            return node;
        }

        if (node instanceof QualifiedNameNode) {
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

        if (node instanceof QualifiedNameNode) {
            return node;
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

    private TypeNode desugarType(TypeNode type) {
        if (type == null) return null;

        if (type instanceof PrimitiveTypeNode) {
            return type;
        }

        if (type instanceof VoidTypeNode) {
            return type;
        }

        if (type instanceof StructTypeNode) {
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

            if (n.sizeExpr instanceof InferredSizeNode) {
                diagnostics.error("'?' array size is only allowed in a declaration with an array-literal initializer");

                return new StaticArrayTypeNode(
                    desugarType(n.elementType),
                    new IntNode("1")
                );
            }

            return new StaticArrayTypeNode(
                desugarType(n.elementType),
                evalConstExpr(n.sizeExpr, "static array size")
            );
        }

        if (type instanceof PtrTypeNode) {
            PtrTypeNode n = (PtrTypeNode) type;

            return new PtrTypeNode(
                n.kind,
                desugarType(n.referencedType)
            );
        }

        throw new RuntimeException(
            "Unsupported type in desugarer: " + type.getClass().getSimpleName()
        );
    }
}
