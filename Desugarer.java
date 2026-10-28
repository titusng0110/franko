import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class Desugarer {

    private final DiagnosticBag diagnostics;

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

    /**
     * Internal control-flow signal for `?` size inference failures.
     *
     * The message is reported as a desugaring diagnostic; the declaration
     * falls back to a placeholder size so the pipeline can continue
     * without cascading internal errors.
     */
    private static final class InferenceFailure extends RuntimeException {
        InferenceFailure(String message) {
            super(message);
        }
    }

    public ASTNode desugar(ASTNode root) {
        if (root == null) return null;

        if (root instanceof ProgramNode) {
            ProgramNode p = (ProgramNode) root;
            return new ProgramNode(desugarTopLevelItemList(p.topLevelItems));
        }

        return desugarStmt(root);
    }

    // ============================================================
    // String-literal initializer handling
    // ============================================================

    /**
     * Converts initializer-like values before ordinary assignment/declaration
     * lowering.
     *
     * String literals lower to ArrayLiteralNode of UTF-8 byte values.
     *
     * Example:
     *
     *   "a\nb"
     *
     * becomes:
     *
     *   [97, 10, 98]
     *
     * No null terminator is appended.
     *
     * The returned ArrayLiteralNode is then handled by the existing
     * array-initializer lowering in this same desugarer pass.
     */
    private ASTNode desugarInitializerValue(ASTNode value) {
        if (value == null) return null;

        if (value instanceof StringLiteralNode) {
            return StringLiteralDecoder.toUtf8ByteArrayLiteral(
                (StringLiteralNode) value
            );
        }

        return value;
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
     * Struct declarations remain one top-level item with desugared field
     * types; field const initializers are left untouched.
     *
     * Global variable declarations remain one top-level item with a desugared
     * type; the const initializer is left untouched and must never be
     * rewritten into assignment statements.
     *
     * Top-level statement sugar may expand into multiple statements.
     */
    private void appendDesugaredTopLevelItem(ASTNode item, List<ASTNode> out) {
        if (item == null) return;

        /*
         * Import directives are consumed by the ImportResolver before
         * desugaring. Reaching this point means the resolver was skipped
         * or failed to splice a file.
         */
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

    /**
     * Desugars a namespace declaration.
     *
     * The namespace name (single or path form) is untouched; its items
     * are desugared with the same top-level handling as ordinary
     * program items, recursing into nested namespace declarations.
     */
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
                + "'; '?' is only allowed in a local declaration with an array-literal or string-literal initializer");

            return new GlobalVarDeclNode(
                desugarType(replaceInferredSizesWithFallback(n.type)),
                n.name,
                evalConstExpr(n.constInitializer, "global variable initializer")
            );
        }

        return new GlobalVarDeclNode(
            desugarType(n.type),
            n.name,
            evalConstExpr(n.constInitializer, "global variable initializer")
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
     * String literal declaration sugar:
     *
     *   array<byte, 3> c = "a\nb";
     *
     * first becomes:
     *
     *   array<byte, 3> c = [97, 10, 98];
     *
     * then, in the same desugarer pass, becomes:
     *
     *   array<byte, 3> c;
     *   c[0] = 97;
     *   c[1] = 10;
     *   c[2] = 98;
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
        ASTNode rawInit = n.init == null ? null : desugarInitializerValue(n.init);

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
                + "' requires an array-literal or string-literal initializer, not a size argument");

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

    // ============================================================
    // Inferred static array sizes (`?`)
    // ============================================================

    /**
     * Returns true when the declared type contains a `?` size marker
     * anywhere (static sizes or nested positions).
     */
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

    /**
     * Returns true when every array level in the type is static.
     *
     * `?` inference requires a fully static nest: a dynamic array level
     * (or an ptr level) anywhere in the type rejects inference.
     */
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

    /**
     * Returns true when the type is a static array level (possibly with
     * `?` markers), i.e. its initializer must be a nested literal.
     */
    private boolean isStaticArrayLevel(TypeNode type) {
        return type instanceof StaticArrayTypeNode;
    }

    /**
     * Resolves `?` markers in a declaration type against an initializer.
     *
     * On success returns the type with every marker replaced by an
     * IntNode count. On failure reports one diagnostic and returns a
     * fallback type (markers replaced by size 1) so the pipeline can
     * continue.
     */
    private TypeNode inferStaticSizes(TypeNode declared, ASTNode init, String varName) {
        if (!isFullyStaticArrayNest(declared)) {
            diagnostics.error("'?' size inference for '"
                + varName
                + "' requires a fully static array type");

            return desugarType(replaceInferredSizesWithFallback(declared));
        }

        ArrayLiteralNode literal = normalizeInitializer(init);

        if (literal == null) {
            diagnostics.error("'?' array size for '"
                + varName
                + "' requires an array-literal or string-literal initializer");

            return desugarType(replaceInferredSizesWithFallback(declared));
        }

        try {
            return desugarType(inferLevel(declared, literal, varName));
        } catch (InferenceFailure f) {
            diagnostics.error(f.getMessage());

            return desugarType(replaceInferredSizesWithFallback(declared));
        }
    }

    /**
     * Normalizes a declaration initializer into an array literal for
     * shape analysis: string literals decode to UTF-8 byte lists.
     *
     * Returns null when the initializer is not a literal list.
     */
    private ArrayLiteralNode normalizeInitializer(ASTNode init) {
        if (init instanceof ArrayLiteralNode lit) {
            return lit;
        }

        if (init instanceof StringLiteralNode s) {
            return StringLiteralDecoder.toUtf8ByteArrayLiteral(s);
        }

        return null;
    }

    /**
     * Infers one static type level against a literal.
     *
     * Rules enforced here (only when `?` is used in the declaration):
     *
     *   - a `?` level takes the literal's element count; an empty
     *     literal cannot infer a size;
     *   - an explicit level must match the literal's element count
     *     exactly (no partial fill);
     *   - when the element type is itself a static level, every element
     *     must be a nested array or string literal and all siblings
     *     must have equal length (perfect rectangle).
     */
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
                        + "' requires nested array or string literals");
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

    /**
     * Verifies a literal against a fully inferred (concrete) static type.
     *
     * Every level must match exactly; nested levels must be rectangular.
     */
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
                    + "' requires nested array or string literals");
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

    /**
     * Rebuilds a type with every `?` marker replaced by size 1.
     *
     * Used only for error recovery so the pipeline continues with a
     * well-formed type after an inference diagnostic.
     */
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

        /*
         * Compound assignment: the right-hand side is an ordinary
         * expression, never an initializer. String literals are left
         * untouched so the semantic analyzer rejects them with a proper
         * diagnostic instead of an internal error.
         */
        if (!"=".equals(n.op)) {
            out.add(new AssignNode(
                n.op,
                target,
                desugarExpr(n.value)
            ));
            return;
        }

        ASTNode value = desugarInitializerValue(n.value);

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

    /**
     * Lowers `x++` / `x--` into `x += 1` / `x -= 1`.
     *
     * The compound assignment carries an explicit literal 1, which stays
     * a fluid integer constant: contextual checks against the target's
     * type apply exactly as they do for a hand-written `x += 1`.
     */
    private ASTNode lowerIncDec(IncDecNode n) {
        return new AssignNode(
            "++".equals(n.op) ? "+=" : "-=",
            desugarLValue(n.target),
            new IntNode("1")
        );
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

            if (!"=".equals(n.op)) {
                /* Compound assignment: value is an ordinary expression. */
                return new AssignNode(
                    n.op,
                    desugarLValue(n.target),
                    desugarExpr(n.value)
                );
            }

            ASTNode value = desugarInitializerValue(n.value);

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

    /**
     * Desugars a controlled statement, i.e. the body of an if/while/for.
     *
     * Single-statement bodies are wrapped in an implicit block so that:
     *
     *   - braceless and braced bodies scope identically,
     *   - declarations inside a braceless body are scoped to that body
     *     and are not visible after the if/while/for.
     *
     * Example:
     *
     *   if (c) int32_t x;
     *
     * is desugared to:
     *
     *   if (c) { int32_t x; }
     */
    private ASTNode desugarControlledStmt(ASTNode stmt) {
        ASTNode lowered = desugarStmt(stmt);

        if (lowered instanceof BlockNode) {
            return lowered;
        }

        List<ASTNode> wrapped = new ArrayList<>();
        wrapped.add(lowered);

        return new BlockNode(wrapped);
    }

    /**
     * Lowers a for loop into an equivalent block + while loop.
     *
     *   for (init; condition; update) body
     *
     * becomes:
     *
     *   {
     *       init
     *       while (condition) {
     *           body
     *           update      <- carried as the while loop's epilogue
     *       }
     *   }
     *
     * The wrapping block scopes the loop variable declared by init so it
     * is not visible after the loop.
     *
     * The init and update statements are lowered through the ordinary
     * statement machinery, so declaration initializer sugar inside the
     * header expands normally:
     *
     *   for (int32_t i = 0; i < n; i = i + 1) { print(i) }
     *
     * becomes:
     *
     *   {
     *       int32_t i;
     *       i = 0;
     *       while (i < n) {
     *           print(i)
     *           i = i + 1;
     *       }
     *   }
     *
     * The update is carried as the while loop's epilogue rather than
     * being appended to the body block. It still runs at the end of
     * every iteration, including the last, but codegen places the
     * continue label BEFORE the epilogue, so `continue` runs the update
     * and `break` skips it, exactly like C's for loop.
     */
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
     * String literal elements are first converted to UTF-8 byte array
     * literals and then recursively lowered:
     *
     *   target = ["hi", "ok"];
     *
     * becomes:
     *
     *   target[0][0] = 104;
     *   target[0][1] = 105;
     *   target[1][0] = 111;
     *   target[1][1] = 107;
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

        /*
         * An empty initializer list emits no indexed assignments.
         *
         * The target is still emitted as an expression statement so that it
         * is resolved and checked as an expression: an undeclared or
         * otherwise invalid target is still diagnosed even though the
         * initializer list itself is a no-op.
         */
        if (literal.elements.isEmpty()) {
            out.add(new ExprStmtNode(loweredTarget));
            return;
        }

        for (int i = 0; i < literal.elements.size(); i++) {
            ASTNode element = desugarInitializerValue(literal.elements.get(i));

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

        /*
         * String literals are lowered to UTF-8 byte array literals only in
         * initializer contexts (see desugarInitializerValue).
         *
         * In ordinary expression contexts the literal is preserved unchanged
         * so that the semantic analyzer can report a proper diagnostic
         * instead of an internal compiler error.
         */
        if (expr instanceof StringLiteralNode) {
            return expr;
        }

        if (expr instanceof VarNode) {
            return expr;
        }

        /*
         * Qualified name references (ns::name) are leaf expressions.
         * The semantic analyzer resolves them against namespace tables;
         * desugaring never rewrites them.
         */
        if (expr instanceof QualifiedNameNode) {
            return expr;
        }

        /*
         * Null literals are leaf expressions that require no desugaring.
         * Semantic analysis checks that null is only used in pointer contexts.
         */
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
                n.target,
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
     * Evaluates a constExpr slot into a canonical decimal literal.
     *
     * Static array sizes, global initializers, and struct field defaults
     * are computed here, before semantic analysis, so later phases only
     * validate the resulting value (positivity, range, fit) instead of
     * folding it.
     *
     * Evaluation failures are reported as desugaring diagnostics and
     * recover with a placeholder size of 1.
     */
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

    // ============================================================
    // Lvalue handling
    // ============================================================

    private ASTNode desugarLValue(ASTNode node) {
        if (node == null) return null;

        if (node instanceof VarNode) {
            return node;
        }

        /*
         * Qualified name lvalues (ns::g) are leaves; the semantic
         * analyzer resolves them against namespace tables.
         */
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
                diagnostics.error("'?' array size is only allowed in a declaration with an array-literal or string-literal initializer");

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
                desugarType(n.referencedType)
            );
        }

        throw new RuntimeException(
            "Unsupported type in desugarer: " + type.getClass().getSimpleName()
        );
    }
}