/**
 * ============================================================================
 * SEMANTIC ANALYZER
 * AST Lowering, Symbol Resolution, Type Decoration, and Structural Validation
 * ============================================================================
 *
 * PURPOSE:
 * The SemanticAnalyzer bridges the gap between the raw syntactic parser output
 * and the later semantic legality checkers. Its primary job is to transform the
 * untyped parser AST (ASTNode) into a strongly-typed, scope-resolved Semantic AST
 * (SemanticASTNode).
 *
 * This phase performs the compiler's first semantic pass. It does not try to
 * prove that every language rule is satisfied. Instead, it builds a decorated
 * semantic tree where:
 *
 *   - variable names are resolved to symbols,
 *   - types are mapped into canonical SemanticType objects,
 *   - expressions carry mechanically inferred result types,
 *   - foldable integer expressions carry constantValue metadata,
 *   - syntactic sugar / intrinsic call forms are lowered into dedicated
 *     Semantic AST nodes,
 *   - and storage-sensitive constructs are checked well enough to ensure that
 *     the lowered tree has a meaningful shape.
 *
 * In short:
 *
 *   Parser AST
 *       raw syntax, names, and grammar-level structure
 *
 *   SemanticAnalyzer
 *       scope resolution, type decoration, constant folding, intrinsic lowering,
 *       and structural semantic validation required for correct lowering
 *
 *   MasterChecker
 *       full language legality, type compatibility, constraint checking,
 *       ownership/lifetime/heap policy, and exact rule enforcement
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS PHASE DOES
 * ----------------------------------------------------------------------------
 *
 * 1. Scope and Symbol Management
 *
 *    The analyzer maintains a lexical scope stack. Variable declarations are
 *    registered in the current scope as VariableSymbol objects.
 *
 *    Raw variable references such as:
 *
 *        x
 *
 *    are resolved into semantic variable expression nodes that directly point
 *    to their corresponding VariableSymbol.
 *
 *    This phase detects basic symbol-resolution errors such as:
 *
 *      - duplicate variable declarations in the same scope,
 *      - use of undeclared variables,
 *      - deleting an undeclared variable.
 *
 *    When possible, the analyzer creates fallback symbols/types after reporting
 *    an error so the semantic tree can remain structurally contiguous and can
 *    expose more errors in a single pass.
 *
 *
 * 2. Type Mapping and Mechanical Type Inference
 *
 *    The analyzer converts raw parser TypeNode objects into canonical
 *    SemanticType objects.
 *
 *    Examples:
 *
 *        int32_t              -> SemanticPrimitiveType(INT32)
 *        uint8_t              -> SemanticPrimitiveType(UINT8)
 *        array<int32_t>       -> SemanticDynamicArrayType(INT32)
 *        array<uint8_t, 128>  -> SemanticStaticArrayType(UINT8, "128")
 *        addr<int32_t>        -> SemanticAddrType(INT32)
 *
 *    It also mechanically assigns expression result types using Franko's basic
 *    inference rules.
 *
 *    Examples:
 *
 *      - integer literals default to int32_t,
 *      - comparison/logical operators produce uint8_t,
 *      - arithmetic and bitwise binary operators inherit the left-hand side type,
 *      - array indexing produces the array element type,
 *      - getaddr(x) produces addr<typeof(x)>,
 *      - deref(p) produces the referenced type of p when p is address-typed.
 *
 *    This is mechanical decoration, not full compatibility checking.
 *
 *
 * 3. Constant Folding
 *
 *    The analyzer performs basic compile-time folding for integer literals and
 *    pure integer unary/binary operators.
 *
 *    Folded values are stored in SemanticExprNode.constantValue.
 *
 *    Examples:
 *
 *        1 + 2       -> constantValue = 3
 *        4 * 5       -> constantValue = 20
 *        10 == 10    -> constantValue = 1
 *        !0          -> constantValue = 1
 *
 *    Folding is conservative. If folding would require invalid arithmetic, such
 *    as division by zero or an impossible shift amount, the analyzer simply
 *    leaves constantValue as null and lets later checking/reporting decide what
 *    to do.
 *
 *
 * 4. Intrinsic Lowering
 *
 *    Some Franko operations are parsed as ordinary expression-call syntax but
 *    are semantically compiler intrinsics.
 *
 *    The analyzer lowers these forms:
 *
 *        arr(100)
 *            -> SemanticArrayInitNode
 *
 *        arr.uninit()
 *            -> SemanticArrayUninitNode
 *
 *        arr.memset(value)
 *            -> SemanticArrayMemsetNode
 *
 *        arr.memcpy(source)
 *            -> SemanticArrayMemcpyNode
 *
 *    This prevents later phases from needing to rediscover these special forms
 *    from generic CallNode / MemberAccessNode syntax.
 *
 *
 * 5. Structural LValue / Storage Validation Required for Correct Lowering
 *
 *    This phase now validates lvalue-ness where the lowered Semantic AST would
 *    otherwise be structurally misleading or invalid.
 *
 *    The following constructs require storage-backed lvalues:
 *
 *      - assignment targets,
 *      - getaddr(...) targets,
 *      - array intrinsic receivers:
 *
 *            arr.uninit()
 *            arr.memset(value)
 *            arr.memcpy(source)
 *
 *      - memcpy source, if Franko defines memcpy as copying from real storage.
 *
 *    This is not considered full legality checking. It is a structural
 *    precondition for building the correct semantic node.
 *
 *    For example:
 *
 *        (a + b).memset(0)
 *
 *    should not be lowered as though "(a + b)" were valid mutable array storage.
 *    The analyzer therefore reports an lvalue/storage error while keeping the
 *    semantic tree contiguous where possible.
 *
 *    Similarly:
 *
 *        getaddr(1 + 2)
 *
 *    is rejected during semantic analysis because an address can only be taken
 *    from an lvalue-backed storage location.
 *
 *
 * 6. Array-Type Structural Checks for Intrinsic Lowering
 *
 *    The analyzer verifies that array intrinsics are being lowered on values that
 *    are at least array-typed.
 *
 *    For example:
 *
 *        x.memset(0)
 *
 *    where x is int32_t is rejected during lowering because memset is an array
 *    intrinsic, not a generic method call.
 *
 *    Deeper array rules remain deferred to MasterChecker.
 *
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS PHASE DOES NOT DO
 * ----------------------------------------------------------------------------
 *
 * 1. It Does Not Enforce General Operator Legality
 *
 *    The analyzer may still construct SemanticBinOpNode for expressions whose
 *    operator use is illegal according to Franko's strict rules.
 *
 *    Example:
 *
 *        arr + 5
 *
 *    The analyzer decorates this expression mechanically. The MasterChecker is
 *    responsible for rejecting it.
 *
 *
 * 2. It Does Not Enforce Assignment Type Compatibility
 *
 *    The analyzer checks that the assignment target is an lvalue, but it does not
 *    decide whether the value can legally be assigned to the target type.
 *
 *    Example:
 *
 *        uint8_t x
 *        uint32_t y
 *        x = y
 *
 *    The analyzer lowers this as an assignment from y to x. The MasterChecker
 *    decides whether the conversion/assignment is legal.
 *
 *
 * 3. It Does Not Enforce Integer Range Constraints
 *
 *    The analyzer may parse and fold integer constants, but it does not decide
 *    whether a value fits in a particular primitive type.
 *
 *    Examples:
 *
 *        uint8_t x = 999
 *        array<int32_t, 999999999999999999999999> xs
 *
 *    Range and representability checks are deferred.
 *
 *
 * 4. It Does Not Enforce Full Array Constraints
 *
 *    The analyzer checks that array intrinsics structurally operate on arrays,
 *    but it does not enforce all array-specific language rules.
 *
 *    Deferred examples include:
 *
 *      - whether dynamic/static array initialization is allowed in a given
 *        context,
 *      - whether an array size expression is valid,
 *      - whether a static array size fits into uint32_t,
 *      - whether memset value type is compatible with the array element type,
 *      - whether memcpy source and target element types/sizes are compatible,
 *      - whether uninit is legal for a given array allocation mode.
 *
 *
 * 5. It Does Not Enforce Heap / Lifetime / Ownership Rules
 *
 *    The analyzer resolves symbols used by del statements, but it does not fully
 *    decide whether deletion is legal.
 *
 *    Deferred examples include:
 *
 *      - deleting stack variables,
 *      - double delete,
 *      - use after delete,
 *      - uninitializing arrays in invalid states,
 *      - ownership transfer rules, if Franko later adds them.
 *
 *
 * 6. It Does Not Implement General Function Call Semantics Yet
 *
 *    At this stage, CallNode syntax is used primarily to identify Franko
 *    intrinsics such as:
 *
 *        arr(100)
 *        arr.memset(0)
 *        arr.memcpy(other)
 *        arr.uninit()
 *
 *    General user-defined function resolution and SemanticCallNode lowering are
 *    intentionally left for a future function-semantics pass.
 *
 *
 * ----------------------------------------------------------------------------
 * ARCHITECTURAL BOUNDARY
 * ----------------------------------------------------------------------------
 *
 * The SemanticAnalyzer is allowed to reject input when the AST cannot be lowered
 * into a meaningful Semantic AST shape.
 *
 * Therefore, checks such as:
 *
 *      "assignment target must be an lvalue"
 *      "getaddr target must be an lvalue"
 *      "memset receiver must be an array lvalue"
 *
 * belong here.
 *
 * But checks such as:
 *
 *      "can this uint32_t be assigned to this uint8_t?"
 *      "is this operator valid for these two types?"
 *      "does this array size fit in uint32_t?"
 *      "is this delete allowed by heap/lifetime policy?"
 *
 * belong in MasterChecker.
 *
 * This keeps the pipeline clean:
 *
 *      SemanticAnalyzer builds a decorated, scope-resolved, structurally valid
 *      Semantic AST.
 *
 *      MasterChecker enforces Franko's strict language rules over that decorated
 *      tree.
 *
 * ============================================================================
 */

import java.math.BigInteger;
import java.util.*;

public class SemanticAnalyzer {

    public static final class SemanticException extends RuntimeException {
        public SemanticException(String message) {
            super(message);
        }
    }

    public static final class Context {
        private final Deque<Map<String, VariableSymbol>> scopes = new ArrayDeque<>();
        private final List<String> errors = new ArrayList<>();

        void clear() {
            scopes.clear();
            errors.clear();
        }

        void pushScope() {
            scopes.push(new LinkedHashMap<>());
        }

        void popScope() {
            scopes.pop();
        }

        void declare(VariableSymbol sym) {
            Map<String, VariableSymbol> current = scopes.peek();
            if (current == null) {
                throw new IllegalStateException("Internal error: no active scope.");
            }

            if (current.containsKey(sym.name)) {
                error("Duplicate declaration of variable '" + sym.name + "' in the same scope");
                return;
            }

            current.put(sym.name, sym);
        }

        VariableSymbol resolve(String name) {
            for (Map<String, VariableSymbol> scope : scopes) {
                VariableSymbol sym = scope.get(name);
                if (sym != null)
                    return sym;
            }
            return null;
        }

        void error(String msg) {
            errors.add(msg);
        }

        List<String> getErrors() {
            return List.copyOf(errors);
        }

        boolean hasErrors() {
            return !errors.isEmpty();
        }

        String formatErrors() {
            StringBuilder sb = new StringBuilder("Semantic analysis failed:\n");
            for (int i = 0; i < errors.size(); i++) {
                sb.append("  ")
                        .append(i + 1)
                        .append(". ")
                        .append(errors.get(i))
                        .append('\n');
            }
            return sb.toString();
        }
    }

    private final Context ctx = new Context();

    private static final SemanticType FALLBACK_TYPE = new SemanticPrimitiveType(SemanticPrimitiveKind.INT32);

    private static final SemanticType BOOL_TYPE = new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8);

    private static final Set<String> BOOL_RESULT_OPS = Set.of(
            "==", "!=", "<", ">", "<=", ">=", "&&", "||");

    public SemanticAnalyzer() {
    }

    /**
     * Entry point: Lowers an ASTNode into a SemanticASTNode.
     */
    public SemanticASTNode analyze(ASTNode root) {
        ctx.clear();
        ctx.pushScope();

        SemanticASTNode result;

        if (root instanceof ProgramNode p) {
            List<SemanticStmtNode> semanticStmts = new ArrayList<>();

            for (ASTNode stmt : p.statements) {
                SemanticStmtNode lowered = analyzeStmt(stmt);
                if (lowered != null) {
                    semanticStmts.add(lowered);
                }
            }

            result = new SemanticProgramNode(semanticStmts);
        } else {
            result = analyzeStmt(root);

            if (result == null) {
                result = analyzeExpr(root);
            }
        }

        ctx.popScope();

        if (ctx.hasErrors()) {
            throw new SemanticException(ctx.formatErrors());
        }

        return result;
    }

    /**
     * Entry point variant for a raw list of statements.
     */
    public List<SemanticStmtNode> analyze(List<ASTNode> nodes) {
        ctx.clear();
        ctx.pushScope();

        List<SemanticStmtNode> semanticStmts = new ArrayList<>();

        for (ASTNode stmt : nodes) {
            SemanticStmtNode lowered = analyzeStmt(stmt);
            if (lowered != null) {
                semanticStmts.add(lowered);
            }
        }

        ctx.popScope();

        if (ctx.hasErrors()) {
            throw new SemanticException(ctx.formatErrors());
        }

        return semanticStmts;
    }

    // ============================================================
    // Statements & Lowering
    // ============================================================

    private SemanticStmtNode analyzeStmt(ASTNode node) {
        if (node == null)
            return null;

        if (node instanceof BlockNode n) {
            ctx.pushScope();

            List<SemanticStmtNode> stmts = new ArrayList<>();

            for (ASTNode s : n.statements) {
                SemanticStmtNode ss = analyzeStmt(s);
                if (ss != null) {
                    stmts.add(ss);
                }
            }

            ctx.popScope();

            return new SemanticBlockNode(stmts);
        }

        if (node instanceof VarDeclNode n) {
            SemanticType type = analyzeType(n.type);
            VariableSymbol sym = new VariableSymbol(n.name, type, n.isHeap);
            ctx.declare(sym);
            return new SemanticVarDeclNode(sym);
        }

        if (node instanceof AssignNode n) {
            SemanticExprNode target = analyzeRequiredLValueExpr(n.target, "Assignment target");

            SemanticExprNode value = analyzeExpr(n.value);

            return new SemanticAssignNode(target, value);
        }

        if (node instanceof IfNode n) {
            SemanticExprNode cond = analyzeExpr(n.condition);
            SemanticStmtNode thenB = analyzeStmt(n.thenBranch);
            SemanticStmtNode elseB = n.elseBranch != null ? analyzeStmt(n.elseBranch) : null;

            return new SemanticIfNode(cond, thenB, elseB);
        }

        if (node instanceof WhileNode n) {
            SemanticExprNode cond = analyzeExpr(n.condition);
            SemanticStmtNode body = analyzeStmt(n.body);

            return new SemanticWhileNode(cond, body);
        }

        if (node instanceof DelNode n) {
            VariableSymbol sym = ctx.resolve(n.name);

            if (sym == null) {
                ctx.error("Cannot delete undeclared variable '" + n.name + "'");
                sym = new VariableSymbol(n.name, FALLBACK_TYPE, true);
            }

            return new SemanticDelNode(sym);
        }

        if (node instanceof PrintNode n) {
            List<SemanticExprNode> args = new ArrayList<>();

            for (ASTNode arg : n.args) {
                args.add(analyzeExpr(arg));
            }

            return new SemanticPrintNode(args);
        }

        if (node instanceof ExprStmtNode n) {
            SemanticStmtNode intrinsic = tryLowerIntrinsicExprStmt(n.expr);

            if (intrinsic != null) {
                return intrinsic;
            }

            return new SemanticExprStmtNode(analyzeExpr(n.expr));
        }

        ctx.error("Unrecognized statement node: " + node.getClass().getSimpleName());
        return null;
    }

    /**
     * Lowers expression-statement forms that are actually Franko intrinsics:
     *
     * arr(100)
     * arr.uninit()
     * arr.memset(value)
     * arr.memcpy(source)
     *
     * This method deliberately performs structural semantic checks needed for
     * correct lowering:
     *
     * - destructive array methods require lvalue receivers
     * - memcpy source is also required to be a storage-backed lvalue
     * - array intrinsic receivers must be array-typed
     * - direct array init requires a direct variable symbol
     *
     * Deeper rules such as size validity, element compatibility, heap policy,
     * and exact assignment compatibility remain deferred to MasterChecker.
     */
    private SemanticStmtNode tryLowerIntrinsicExprStmt(ASTNode expr) {
        if (!(expr instanceof CallNode call)) {
            return null;
        }

        if (call.callee instanceof MemberAccessNode mac) {
            String member = mac.memberName;

            if (member.equals("memset")) {
                if (call.args.size() != 1) {
                    ctx.error("Array intrinsic 'memset' expects exactly 1 argument");
                    return null;
                }

                SemanticExprNode receiver = analyzeRequiredLValueExpr(mac.target, "memset receiver");

                requireArrayType(receiver, "memset receiver");

                SemanticExprNode value = analyzeExpr(call.args.get(0));

                return new SemanticArrayMemsetNode(receiver, value);
            }

            if (member.equals("memcpy")) {
                if (call.args.size() != 1) {
                    ctx.error("Array intrinsic 'memcpy' expects exactly 1 argument");
                    return null;
                }

                SemanticExprNode target = analyzeRequiredLValueExpr(mac.target, "memcpy target");

                requireArrayType(target, "memcpy target");

                SemanticExprNode source = analyzeRequiredLValueExpr(call.args.get(0), "memcpy source");

                requireArrayType(source, "memcpy source");

                return new SemanticArrayMemcpyNode(target, source);
            }

            if (member.equals("uninit")) {
                if (!call.args.isEmpty()) {
                    ctx.error("Array intrinsic 'uninit' expects exactly 0 arguments");
                    return null;
                }

                SemanticExprNode receiver = analyzeRequiredLValueExpr(mac.target, "uninit receiver");

                requireArrayType(receiver, "uninit receiver");

                return new SemanticArrayUninitNode(receiver);
            }

            return null;
        }

        /*
         * Direct-call array initialization:
         *
         * arr(100)
         *
         * This intentionally only lowers a direct variable call.
         * It does NOT lower arbitrary lvalues such as:
         *
         * deref(p)(100)
         * arrs[i](100)
         *
         * because SemanticArrayInitNode currently stores a VariableSymbol,
         * not an arbitrary SemanticExprNode receiver.
         */
        if (call.callee instanceof VarNode v && call.args.size() == 1) {
            VariableSymbol sym = ctx.resolve(v.name);

            if (sym == null) {
                /*
                 * Do not create an array-init node because we do not know that
                 * this name denotes an array. Let the generic expression path
                 * also report unsupported CallNode if applicable.
                 */
                ctx.error("Undeclared variable '" + v.name + "'");
                analyzeExpr(call.args.get(0));
                return new SemanticExprStmtNode(
                        new SemanticIntLiteralNode(FALLBACK_TYPE, BigInteger.ZERO, "0"));
            }

            if (isArrayType(sym.type)) {
                SemanticExprNode size = analyzeExpr(call.args.get(0));
                return new SemanticArrayInitNode(sym, size);
            }

            /*
             * If functions are added later, x(100) where x is not an array may
             * become a valid call expression. For now, do not mis-lower it as
             * array initialization.
             */
            return null;
        }

        return null;
    }

    // ============================================================
    // Expressions & Inference
    // ============================================================

    private SemanticExprNode analyzeExpr(ASTNode node) {
        if (node instanceof IntNode n) {
            BigInteger val = parseIntegerLiteralSafe(n.value);
            return new SemanticIntLiteralNode(FALLBACK_TYPE, val, n.value);
        }

        if (node instanceof VarNode n) {
            VariableSymbol sym = ctx.resolve(n.name);

            if (sym == null) {
                ctx.error("Undeclared variable '" + n.name + "'");
                sym = new VariableSymbol(n.name, FALLBACK_TYPE, false);
            }

            return new SemanticVarExprNode(sym.type, sym);
        }

        if (node instanceof UnaryOpNode n) {
            SemanticExprNode expr = analyzeExpr(n.expr);

            SemanticType type = n.op.equals("!") ? BOOL_TYPE : expr.type;

            BigInteger folded = foldUnary(n.op, expr.constantValue);

            return new SemanticUnaryOpNode(type, folded, n.op, expr);
        }

        if (node instanceof BinOpNode n) {
            SemanticExprNode left = analyzeExpr(n.left);
            SemanticExprNode right = analyzeExpr(n.right);

            SemanticType type = BOOL_RESULT_OPS.contains(n.op) ? BOOL_TYPE : left.type;

            BigInteger folded = foldBinary(n.op, left.constantValue, right.constantValue);

            return new SemanticBinOpNode(type, folded, n.op, left, right);
        }

        if (node instanceof ArrayAccessNode n) {
            SemanticExprNode target = analyzeExpr(n.target);
            SemanticExprNode index = analyzeExpr(n.index);

            SemanticType elemType = extractElementType(target.type);

            return new SemanticArrayAccessNode(elemType, target, index);
        }

        if (node instanceof GetAddrNode n) {
            SemanticExprNode target = analyzeRequiredLValueExpr(n.target, "getaddr(...) target");

            return new SemanticGetAddrNode(new SemanticAddrType(target.type), target);
        }

        if (node instanceof DerefNode n) {
            SemanticExprNode expr = analyzeExpr(n.expr);

            SemanticType refType = FALLBACK_TYPE;

            if (expr.type instanceof SemanticAddrType addrT) {
                refType = addrT.referencedType;
            } else {
                ctx.error("Cannot dereference non-address type");
            }

            return new SemanticDerefNode(refType, expr);
        }

        ctx.error("Unrecognized expression node: " + node.getClass().getSimpleName());

        return new SemanticIntLiteralNode(FALLBACK_TYPE, BigInteger.ZERO, "0");
    }

    // ============================================================
    // LValue / Structural Semantic Requirements
    // ============================================================

    private SemanticExprNode analyzeRequiredLValueExpr(
            ASTNode node,
            String roleDescription) {
        SemanticExprNode expr = analyzeExpr(node);
        requireLValue(expr, roleDescription);
        return expr;
    }

    private void requireLValue(
            SemanticExprNode expr,
            String roleDescription) {
        if (!isValidStorageBackedLValue(expr)) {
            ctx.error(roleDescription + " must be a storage-backed lvalue");
        }
    }

    /**
     * Stronger than merely calling expr.isLValue().
     *
     * Reason:
     *
     * SemanticArrayAccessNode currently returns true for isLValue()
     * unconditionally. However, semantically:
     *
     * arr[i]
     *
     * is an assignable lvalue only if arr itself denotes valid storage.
     *
     * This prevents malformed/future AST forms such as:
     *
     * (a + b)[0] = x
     * makeArray()[0] = x
     *
     * from becoming silently accepted as storage-backed lvalues.
     */
    private boolean isValidStorageBackedLValue(SemanticExprNode expr) {
        if (expr == null) {
            return false;
        }

        if (!expr.isLValue()) {
            return false;
        }

        if (expr instanceof SemanticVarExprNode) {
            return true;
        }

        if (expr instanceof SemanticDerefNode) {
            /*
             * deref(p) is storage-backed if p is a valid address value.
             * analyzeExpr(DerefNode) already reports an error when p is not
             * address-typed, while still keeping the tree contiguous.
             */
            return true;
        }

        if (expr instanceof SemanticArrayAccessNode arrayAccess) {
            return isValidStorageBackedLValue(arrayAccess.target);
        }

        /*
         * Future semantic lvalue nodes, such as field access, can either be
         * explicitly added above or trusted here if their isLValue()
         * implementation is already storage-aware.
         */
        return expr.isLValue();
    }

    private void requireArrayType(
            SemanticExprNode expr,
            String roleDescription) {
        if (!isArrayType(expr.type)) {
            ctx.error(roleDescription + " must have array type, got " + expr.type.describe());
        }
    }

    // ============================================================
    // Types Mapping
    // ============================================================

    private SemanticType analyzeType(TypeNode node) {
        if (node instanceof PrimitiveTypeNode p) {
            return new SemanticPrimitiveType(
                    SemanticPrimitiveKind.valueOf(p.kind.name()));
        }

        if (node instanceof DynamicArrayTypeNode d) {
            return new SemanticDynamicArrayType(analyzeType(d.elementType));
        }

        if (node instanceof StaticArrayTypeNode s) {
            return new SemanticStaticArrayType(
                    analyzeType(s.elementType),
                    s.sizeLiteral);
        }

        if (node instanceof AddrTypeNode a) {
            return new SemanticAddrType(analyzeType(a.referencedType));
        }

        ctx.error("Unknown type node.");
        return FALLBACK_TYPE;
    }

    private SemanticType extractElementType(SemanticType type) {
        if (type instanceof SemanticDynamicArrayType d) {
            return d.elementType;
        }

        if (type instanceof SemanticStaticArrayType s) {
            return s.elementType;
        }

        ctx.error("Indexed expression is not an array");
        return FALLBACK_TYPE;
    }

    private boolean isArrayType(SemanticType type) {
        return type instanceof SemanticDynamicArrayType
                || type instanceof SemanticStaticArrayType;
    }

    // ============================================================
    // Constant Folding Logic
    // ============================================================

    private BigInteger parseIntegerLiteralSafe(String val) {
        try {
            String s = val.trim();

            if (s.startsWith("+")) {
                s = s.substring(1);
            }

            if (s.toLowerCase().startsWith("0b")) {
                return new BigInteger(s.substring(2), 2);
            }

            if (s.toLowerCase().startsWith("0x")) {
                return new BigInteger(s.substring(2), 16);
            }

            return new BigInteger(s, 10);
        } catch (Exception e) {
            ctx.error("Invalid integer literal format: " + val);
            return BigInteger.ZERO;
        }
    }

    private BigInteger foldUnary(String op, BigInteger val) {
        if (val == null)
            return null;

        return switch (op) {
            case "-" -> val.negate();
            case "!" -> val.signum() == 0 ? BigInteger.ONE : BigInteger.ZERO;
            default -> null;
        };
    }

    private BigInteger foldBinary(String op, BigInteger left, BigInteger right) {
        if (left == null || right == null)
            return null;

        try {
            return switch (op) {
                case "+" -> left.add(right);
                case "-" -> left.subtract(right);
                case "*" -> left.multiply(right);
                case "/" -> right.signum() == 0 ? null : left.divide(right);

                case "&" -> left.and(right);
                case "|" -> left.or(right);
                case "^" -> left.xor(right);

                case "<<" -> {
                    if (right.signum() < 0)
                        yield null;

                    try {
                        yield left.shiftLeft(right.intValueExact());
                    } catch (ArithmeticException ex) {
                        yield null;
                    }
                }

                case ">>" -> {
                    if (right.signum() < 0)
                        yield null;

                    try {
                        yield left.shiftRight(right.intValueExact());
                    } catch (ArithmeticException ex) {
                        yield null;
                    }
                }

                case "==" -> left.equals(right) ? BigInteger.ONE : BigInteger.ZERO;
                case "!=" -> !left.equals(right) ? BigInteger.ONE : BigInteger.ZERO;
                case "<" -> left.compareTo(right) < 0 ? BigInteger.ONE : BigInteger.ZERO;
                case ">" -> left.compareTo(right) > 0 ? BigInteger.ONE : BigInteger.ZERO;
                case "<=" -> left.compareTo(right) <= 0 ? BigInteger.ONE : BigInteger.ZERO;
                case ">=" -> left.compareTo(right) >= 0 ? BigInteger.ONE : BigInteger.ZERO;

                case "&&" ->
                    left.signum() != 0 && right.signum() != 0
                            ? BigInteger.ONE
                            : BigInteger.ZERO;

                case "||" ->
                    left.signum() != 0 || right.signum() != 0
                            ? BigInteger.ONE
                            : BigInteger.ZERO;

                default -> null;
            };
        } catch (ArithmeticException e) {
            return null;
        }
    }
}
