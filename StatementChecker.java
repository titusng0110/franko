/**
 * ============================================================================
 * STATEMENT CHECKER
 * ============================================================================
 *
 * PURPOSE:
 * StatementChecker validates statement-level Franko legality rules over the
 * fully lowered, symbol-resolved, type-decorated Semantic AST.
 *
 * The SemanticAnalyzer has already:
 *
 *   - resolved variable references to VariableSymbol objects,
 *   - assigned every expression a SemanticType,
 *   - folded integer constants into BigInteger values when possible,
 *   - lowered array intrinsic syntax into dedicated Semantic AST nodes,
 *   - performed structural lvalue checks needed for correct lowering.
 *
 * StatementChecker performs stricter statement-level legality checks:
 *
 *   - assignment target validity,
 *   - assignment type compatibility,
 *   - if/while condition validity,
 *   - delete legality,
 *   - dynamic array initialization legality,
 *   - dynamic array uninitialization legality,
 *   - array memset legality,
 *   - array memcpy legality,
 *   - recursive traversal through blocks and nested statements.
 *
 * ----------------------------------------------------------------------------
 * ARRAY INTRINSIC RULES
 * ----------------------------------------------------------------------------
 *
 * Franko arrays have two categories:
 *
 *   array<T>        dynamic array
 *   array<T, N>     static array
 *
 * Intrinsic support:
 *
 *   target(size)
 *      - target must be a storage-backed dynamic array lvalue
 *      - size must be uint32_t, or a positive constant fitting uint32_t
 *
 *   target.uninit()
 *      - target must be a storage-backed dynamic array lvalue
 *
 *   target.memset(byte)
 *      - target may be dynamic or static array
 *      - receiver must be a storage-backed lvalue
 *      - fill value must be uint8_t/char, or a constant fitting uint8_t
 *
 *   target.memcpy(source)
 *      - target and source may be dynamic or static arrays
 *      - target and source must both be storage-backed lvalue arrays
 *      - target/source element types must match
 *      - static/dynamic shape and static lengths do not need to match
 *
 * ============================================================================ 
 */
public class StatementChecker {
    private final SemanticAnalyzer.Context ctx;
    private final DeclarationChecker declarations;
    private final ExpressionChecker expressions;
    private final TypeChecker types;

    public StatementChecker(
            SemanticAnalyzer.Context ctx,
            DeclarationChecker declarations,
            ExpressionChecker expressions,
            TypeChecker types
    ) {
        this.ctx = ctx;
        this.declarations = declarations;
        this.expressions = expressions;
        this.types = types;
    }

    public void checkStmt(SemanticStmtNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticBlockNode n) {
            visitBlock(n);
            return;
        }

        if (node instanceof SemanticVarDeclNode n) {
            declarations.checkVarDecl(n);
            return;
        }

        if (node instanceof SemanticAssignNode n) {
            visitAssign(n);
            return;
        }

        if (node instanceof SemanticIfNode n) {
            visitIf(n);
            return;
        }

        if (node instanceof SemanticWhileNode n) {
            visitWhile(n);
            return;
        }

        if (node instanceof SemanticDelNode n) {
            visitDel(n);
            return;
        }

        if (node instanceof SemanticPrintNode n) {
            visitPrint(n);
            return;
        }

        if (node instanceof SemanticExprStmtNode n) {
            visitExprStmt(n);
            return;
        }

        if (node instanceof SemanticArrayInitNode n) {
            visitArrayInit(n);
            return;
        }

        if (node instanceof SemanticArrayUninitNode n) {
            visitArrayUninit(n);
            return;
        }

        if (node instanceof SemanticArrayMemsetNode n) {
            visitArrayMemset(n);
            return;
        }

        if (node instanceof SemanticArrayMemcpyNode n) {
            visitArrayMemcpy(n);
            return;
        }

        ctx.error("Unknown semantic statement node: "
                + node.getClass().getSimpleName());
    }

    // ============================================================
    // General Statements
    // ============================================================

    private void visitBlock(SemanticBlockNode node) {
        for (SemanticStmtNode stmt : node.statements) {
            checkStmt(stmt);
        }
    }

    private void visitExprStmt(SemanticExprStmtNode node) {
        expressions.checkExpr(node.expr);
    }

    private void visitAssign(SemanticAssignNode node) {
        expressions.checkExpr(node.target);
        expressions.checkExpr(node.value);

        if (!isStorageBackedLValue(node.target)) {
            ctx.error("Left-hand side of assignment must be an addressable storage-backed lvalue");
        }

        types.ensureAssignable(
                node.target.type,
                node.value,
                "Invalid assignment"
        );
    }

    private void visitIf(SemanticIfNode node) {
        expressions.checkExpr(node.condition);

        types.ensureIntegral(
                node.condition.type,
                "if condition must be an integer"
        );

        checkStmt(node.thenBranch);

        if (node.elseBranch != null) {
            checkStmt(node.elseBranch);
        }
    }

    private void visitWhile(SemanticWhileNode node) {
        expressions.checkExpr(node.condition);

        types.ensureIntegral(
                node.condition.type,
                "while condition must be an integer"
        );

        checkStmt(node.body);
    }

    private void visitPrint(SemanticPrintNode node) {
        for (SemanticExprNode arg : node.args) {
            expressions.checkExpr(arg);
        }
    }

    // ============================================================
    // Delete
    // ============================================================

    private void visitDel(SemanticDelNode node) {
        VariableSymbol sym = node.symbol;

        if (sym == null) {
            ctx.error("Cannot delete null symbol");
            return;
        }

        if (!sym.isHeap) {
            ctx.error("Cannot delete non-heap variable '" + sym.name + "'");
            return;
        }

        if (sym.deleted) {
            ctx.error("Variable '" + sym.name + "' has already been deleted");
            return;
        }

        sym.deleted = true;
    }

    // ============================================================
    // Array Intrinsics
    // ============================================================

    /**
     * Checks:
     *
     *   target(size)
     *
     * Rules:
     *
     *   - target must be a storage-backed lvalue,
     *   - target must be a dynamic array,
     *   - size must be a valid dynamic array size.
     *
     * This now supports:
     *
     *   arr(20);
     *   deref(p)(20);
     *
     * because SemanticArrayInitNode stores SemanticExprNode target instead of
     * only a direct VariableSymbol.
     */
    private void visitArrayInit(SemanticArrayInitNode node) {
        expressions.checkExpr(node.target);

        if (!isStorageBackedLValue(node.target)) {
            ctx.error("Array init target must be a storage-backed lvalue");
        }

        if (!types.isDynamicArrayType(node.target.type)) {
            ctx.error("Array init requires dynamic array type, got "
                    + types.describeSafe(node.target.type));
        }

        expressions.ensureArraySizeCompatible(
                node.size,
                "Array size"
        );
    }

    /**
     * Checks:
     *
     *   target.uninit()
     *
     * Rule:
     *
     *   uninit is available only on dynamic arrays.
     */
    private void visitArrayUninit(SemanticArrayUninitNode node) {
        expressions.checkExpr(node.receiver);

        if (!isStorageBackedLValue(node.receiver)) {
            ctx.error("uninit() receiver must be a storage-backed lvalue");
        }

        if (!types.isDynamicArrayType(node.receiver.type)) {
            ctx.error("uninit() receiver must be a dynamic array, got "
                    + types.describeSafe(node.receiver.type));
        }
    }

    /**
     * Checks:
     *
     *   target.memset(value)
     *
     * Rule:
     *
     *   memset is available on both dynamic and static arrays.
     */
    private void visitArrayMemset(SemanticArrayMemsetNode node) {
        expressions.checkExpr(node.receiver);
        expressions.checkExpr(node.value);

        if (!isStorageBackedLValue(node.receiver)) {
            ctx.error("memset() receiver must be a storage-backed lvalue");
        }

        types.ensureArrayType(
                node.receiver.type,
                "memset() receiver must be an array"
        );

        SemanticType elementType = types.elementTypeOfArray(node.receiver.type);

        if (elementType != null && !types.isMemsetable(elementType)) {
            ctx.error("memset() receiver element type is not memsetable: "
                    + elementType.describe());
        }

        ensureMemsetValueCompatible(node.value);
    }

    /**
     * Checks:
     *
     *   target.memcpy(source)
     *
     * Rule:
     *
     *   memcpy is available between dynamic/static arrays as long as their
     *   element types match.
     */
    private void visitArrayMemcpy(SemanticArrayMemcpyNode node) {
        expressions.checkExpr(node.target);
        expressions.checkExpr(node.source);

        if (!isStorageBackedLValue(node.target)) {
            ctx.error("memcpy() target must be a storage-backed lvalue");
        }

        if (!isStorageBackedLValue(node.source)) {
            ctx.error("memcpy() source must be a storage-backed lvalue");
        }

        types.ensureArrayType(
                node.target.type,
                "memcpy() target must be an array"
        );

        types.ensureArrayType(
                node.source.type,
                "memcpy() source must be an array"
        );

        SemanticType targetElem = types.elementTypeOfArray(node.target.type);
        SemanticType sourceElem = types.elementTypeOfArray(node.source.type);

        if (targetElem != null && sourceElem != null) {
            if (!types.sameType(targetElem, sourceElem)) {
                ctx.error("memcpy() requires source and target arrays to have identical element types, got "
                        + targetElem.describe()
                        + " and "
                        + sourceElem.describe());
            }
        }

        if (targetElem != null && !types.isMemcpyable(targetElem)) {
            ctx.error("memcpy() target array element type is not memcpyable: "
                    + targetElem.describe());
        }

        if (sourceElem != null && !types.isMemcpyable(sourceElem)) {
            ctx.error("memcpy() source array element type is not memcpyable: "
                    + sourceElem.describe());
        }
    }

    // ============================================================
    // Intrinsic Argument Helpers
    // ============================================================

    private void ensureMemsetValueCompatible(SemanticExprNode value) {
        if (value == null) {
            ctx.error("memset() fill value cannot be null");
            return;
        }

        if (value.isConstant()) {
            types.requireConstantFitsPrimitive(
                    value.constantValue,
                    SemanticPrimitiveKind.UINT8,
                    "memset() fill value"
            );
            return;
        }

        if (!(value.type instanceof SemanticPrimitiveType pt
                && pt.kind == SemanticPrimitiveKind.UINT8)) {
            ctx.error("memset() fill value must be uint8_t/char, got "
                    + types.describeSafe(value.type));
        }
    }

    // ============================================================
    // LValue / Storage Helpers
    // ============================================================

    /**
     * Stronger than SemanticExprNode.isLValue().
     *
     * Storage-backed lvalues are currently:
     *
     *   - variables,
     *   - dereferenced addresses,
     *   - array elements whose target is storage-backed.
     *
     * This intentionally rejects synthetic/non-storage lvalues unless their
     * storage semantics are explicitly added here later.
     */
    private boolean isStorageBackedLValue(SemanticExprNode expr) {
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
            return true;
        }

        if (expr instanceof SemanticArrayAccessNode arrayAccess) {
            return isStorageBackedLValue(arrayAccess.target);
        }

        return false;
    }
}