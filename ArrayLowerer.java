import java.util.Objects;

/**
 * ============================================================================
 * ARRAY LOWERER
 * ============================================================================
 *
 * PURPOSE:
 * ArrayLowerer lowers array-specific expression-statement forms that require
 * semantic information.
 *
 * It recognizes:
 *
 *   - dynamic array initialization:
 *
 *       arr(size);
 *       deref(p)(size);
 *
 *   - array member intrinsics:
 *
 *       arr.uninit();
 *       arr.memset(value);
 *       arr.memcpy(source);
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS CLASS DOES
 * ----------------------------------------------------------------------------
 *
 * This class performs structural lowering for array intrinsics after expression
 * names and types can be resolved.
 *
 * This is semantic-phase logic because syntax alone cannot always distinguish:
 *
 *   f(x);      // ordinary function call
 *   arr(x);    // dynamic array initialization
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS CLASS DOES NOT DO
 * ----------------------------------------------------------------------------
 *
 * This class does not lower array initializer lists.
 *
 * Array initializer lists are eliminated earlier by Desugarer:
 *
 *   xs = [1, 2, 3];
 *
 * becomes:
 *
 *   xs[0] = 1;
 *   xs[1] = 2;
 *   xs[2] = 3;
 *
 * Therefore ArrayLowerer only handles array intrinsics and dynamic-array
 * init-call syntax.
 *
 * Full legality is still checked later by StatementChecker / ExpressionChecker.
 */
public final class ArrayLowerer {

    private final SymbolTable ctx;
    private final ExpressionAnalyzer exprAnalyzer;

    public ArrayLowerer(
            SymbolTable ctx,
            ExpressionAnalyzer exprAnalyzer
    ) {
        this.ctx = Objects.requireNonNull(ctx);
        this.exprAnalyzer = Objects.requireNonNull(exprAnalyzer);
    }

    // ============================================================
    // Intrinsic Expression-Statement Lowering
    // ============================================================

    /**
     * Attempts to lower expression-statement forms that are Franko array
     * intrinsics.
     *
     * Returns null if the expression statement is not an array intrinsic.
     */
    public SemanticStmtNode tryLowerIntrinsicExprStmt(ASTNode expr) {
        if (!(expr instanceof CallNode call)) {
            return null;
        }

        if (call.callee instanceof MemberAccessNode mac) {
            return tryLowerMemberIntrinsic(call, mac);
        }

        return tryLowerDynamicArrayInit(call);
    }

    // ============================================================
    // Member Intrinsics
    // ============================================================

    private SemanticStmtNode tryLowerMemberIntrinsic(
            CallNode call,
            MemberAccessNode mac
    ) {
        String member = mac.memberName;

        if (member.equals("memset")) {
            return lowerMemset(call, mac);
        }

        if (member.equals("memcpy")) {
            return lowerMemcpy(call, mac);
        }

        if (member.equals("uninit")) {
            return lowerUninit(call, mac);
        }

        return null;
    }

    private SemanticStmtNode lowerMemset(
            CallNode call,
            MemberAccessNode mac
    ) {
        if (call.args.size() != 1) {
            ctx.error("Array intrinsic 'memset' expects exactly 1 argument");
            return null;
        }

        SemanticExprNode receiver =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        "memset receiver"
                );

        exprAnalyzer.requireArrayType(
                receiver,
                "memset receiver"
        );

        SemanticExprNode value =
                exprAnalyzer.analyzeExpr(call.args.get(0));

        return new SemanticArrayMemsetNode(receiver, value);
    }

    private SemanticStmtNode lowerMemcpy(
            CallNode call,
            MemberAccessNode mac
    ) {
        if (call.args.size() != 1) {
            ctx.error("Array intrinsic 'memcpy' expects exactly 1 argument");
            return null;
        }

        SemanticExprNode target =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        "memcpy target"
                );

        exprAnalyzer.requireArrayType(
                target,
                "memcpy target"
        );

        SemanticExprNode source =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        call.args.get(0),
                        "memcpy source"
                );

        exprAnalyzer.requireArrayType(
                source,
                "memcpy source"
        );

        return new SemanticArrayMemcpyNode(target, source);
    }

    private SemanticStmtNode lowerUninit(
            CallNode call,
            MemberAccessNode mac
    ) {
        if (!call.args.isEmpty()) {
            ctx.error("Array intrinsic 'uninit' expects exactly 0 arguments");
            return null;
        }

        SemanticExprNode receiver =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        "uninit receiver"
                );

        exprAnalyzer.requireDynamicArrayType(
                receiver,
                "uninit receiver"
        );

        return new SemanticArrayUninitNode(receiver);
    }

    // ============================================================
    // Dynamic Array Init Call
    // ============================================================

    /**
     * Attempts to lower:
     *
     *   arr(size);
     *   deref(p)(size);
     *
     * into:
     *
     *   SemanticArrayInitNode(target, size)
     *
     * Important ambiguity:
     *
     *   printArray(n);
     *
     * is also syntactically a one-argument call. If the callee is a known
     * function name and not a variable, this method leaves it alone so the
     * normal expression-call path can handle it.
     */
    private SemanticStmtNode tryLowerDynamicArrayInit(CallNode call) {
        if (call.args.size() != 1) {
            return null;
        }

        /*
         * Disambiguate:
         *
         *   f(x)      ordinary function call
         *   arr(x)    possible dynamic array initialization
         *
         * If the callee is a bare identifier and resolves to no variable but
         * does name a function, do not analyze it as a value.
         *
         * Function names are not values. ExpressionAnalyzer would correctly
         * report that as an error if asked to analyze VarNode("f") as a value.
         * But during intrinsic probing, that would be a false diagnostic.
         */
        if (call.callee instanceof VarNode calleeVar) {
            VariableSymbol var = ctx.resolve(calleeVar.name);

            if (var == null && ctx.hasAnyFunctionNamed(calleeVar.name)) {
                return null;
            }

            if (var == null) {
                return null;
            }
        }

        SemanticExprNode target =
                exprAnalyzer.analyzeExpr(call.callee);

        if (!exprAnalyzer.isDynamicArrayType(target.type)) {
            return null;
        }

        exprAnalyzer.requireLValue(
                target,
                "array initialization target"
        );

        SemanticExprNode size =
                exprAnalyzer.analyzeExpr(call.args.get(0));

        return new SemanticArrayInitNode(target, size);
    }
}