import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lowers Franko array intrinsics into SemanticArrayIntrinsicCallNode.
 *
 * The lowered node is an expression, not a statement.
 *
 * This is important because some array intrinsics return int32_t:
 *
 *   xs.init(n)
 *   xs.init_zero(n)
 *   xs.resize(n)
 *
 * while others return void:
 *
 *   xs.uninit()
 *   xs.memset(...)
 *   xs.memcpy(...)
 *   xs.memmove(...)
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

    /**
     * Attempts to lower an array intrinsic call expression.
     *
     * Returns null if the expression is not an array intrinsic.
     */
    public SemanticArrayIntrinsicCallNode tryLowerIntrinsicCall(ASTNode expr) {
        if (!(expr instanceof CallNode call)) {
            return null;
        }

        if (call.callee instanceof MemberAccessNode mac) {
            return tryLowerMemberIntrinsic(call, mac);
        }

        return tryLowerDynamicArrayInitShorthand(call);
    }

    /**
     * Returns true if the given member name is reserved as a built-in
     * array intrinsic member.
     *
     * ExpressionAnalyzer uses this to avoid emitting a second misleading
     * "unsupported member call" diagnostic after ArrayLowerer has already
     * diagnosed an invalid array intrinsic call.
     */
    public boolean isArrayIntrinsicMemberName(String name) {
        return switch (name) {
            case "init",
                 "init_zero",
                 "resize",
                 "uninit",
                 "memset",
                 "memcpy",
                 "memmove" -> true;

            default -> false;
        };
    }

    // ============================================================
    // Member Intrinsics
    // ============================================================

    private SemanticArrayIntrinsicCallNode tryLowerMemberIntrinsic(
            CallNode call,
            MemberAccessNode mac
    ) {
        return switch (mac.memberName) {
            case "init" ->
                    lowerDynamicArrayAlloc(
                            call,
                            mac,
                            SemanticArrayIntrinsicKind.INIT
                    );

            case "init_zero" ->
                    lowerDynamicArrayAlloc(
                            call,
                            mac,
                            SemanticArrayIntrinsicKind.INIT_ZERO
                    );

            case "resize" ->
                    lowerDynamicArrayAlloc(
                            call,
                            mac,
                            SemanticArrayIntrinsicKind.RESIZE
                    );

            case "uninit" ->
                    lowerUninit(call, mac);

            case "memset" ->
                    lowerMemset(call, mac);

            case "memcpy" ->
                    lowerMemcpy(call, mac);

            case "memmove" ->
                    lowerMemmove(call, mac);

            default -> null;
        };
    }

    // ============================================================
    // init / init_zero / resize
    // ============================================================

    private SemanticArrayIntrinsicCallNode lowerDynamicArrayAlloc(
            CallNode call,
            MemberAccessNode mac,
            SemanticArrayIntrinsicKind kind
    ) {
        if (call.args.size() != 1) {
            ctx.error("Array intrinsic '"
                    + kind.runtimeName
                    + "' expects exactly 1 argument");
            return null;
        }

        SemanticExprNode receiver =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        kind.runtimeName + " receiver"
                );

        exprAnalyzer.requireDynamicArrayType(
                receiver,
                kind.runtimeName + " receiver"
        );

        SemanticExprNode size =
                exprAnalyzer.analyzeExpr(call.args.get(0));

        return new SemanticArrayIntrinsicCallNode(
                kind,
                receiver,
                List.of(size)
        );
    }

    // ============================================================
    // uninit
    // ============================================================

    private SemanticArrayIntrinsicCallNode lowerUninit(
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

        return new SemanticArrayIntrinsicCallNode(
                SemanticArrayIntrinsicKind.UNINIT,
                receiver,
                List.of()
        );
    }

    // ============================================================
    // memset
    // ============================================================

    private SemanticArrayIntrinsicCallNode lowerMemset(
            CallNode call,
            MemberAccessNode mac
    ) {
        if (call.args.size() != 1 && call.args.size() != 3) {
            ctx.error("Array intrinsic 'memset' expects either 1 or 3 arguments");
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

        List<SemanticExprNode> args = new ArrayList<>();

        SemanticExprNode value =
                exprAnalyzer.analyzeExpr(call.args.get(0));

        args.add(value);

        if (call.args.size() == 3) {
            SemanticExprNode start =
                    exprAnalyzer.analyzeExpr(call.args.get(1));

            SemanticExprNode count =
                    exprAnalyzer.analyzeExpr(call.args.get(2));

            args.add(start);
            args.add(count);
        }

        return new SemanticArrayIntrinsicCallNode(
                SemanticArrayIntrinsicKind.MEMSET,
                receiver,
                args
        );
    }

    // ============================================================
    // memcpy
    // ============================================================

    /**
     * Lowers:
     *
     *   target.memcpy(sourceAddr)
     *   target.memcpy(sourceAddr, dstStart, srcStart, count)
     *
     * The source argument is intentionally analyzed as a normal expression.
     *
     * Franko arrays are not passed as values. Therefore memcpy source should be
     * an address to an array:
     *
     *   addr<array<T>>
     *   addr<array<T, N>>
     *
     * Example source-level shape:
     *
     *   dst.memcpy(getaddr(src));
     *
     * Full validation that sourceAddr is addr<array<...>> belongs in
     * ExpressionChecker.
     */
    private SemanticArrayIntrinsicCallNode lowerMemcpy(
            CallNode call,
            MemberAccessNode mac
    ) {
        if (call.args.size() != 1 && call.args.size() != 4) {
            ctx.error("Array intrinsic 'memcpy' expects either 1 or 4 arguments");
            return null;
        }

        SemanticExprNode receiver =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        "memcpy target"
                );

        exprAnalyzer.requireArrayType(
                receiver,
                "memcpy target"
        );

        SemanticExprNode sourceAddr =
                exprAnalyzer.analyzeExpr(call.args.get(0));

        List<SemanticExprNode> args = new ArrayList<>();

        args.add(sourceAddr);

        if (call.args.size() == 4) {
            SemanticExprNode dstStart =
                    exprAnalyzer.analyzeExpr(call.args.get(1));

            SemanticExprNode srcStart =
                    exprAnalyzer.analyzeExpr(call.args.get(2));

            SemanticExprNode count =
                    exprAnalyzer.analyzeExpr(call.args.get(3));

            args.add(dstStart);
            args.add(srcStart);
            args.add(count);
        }

        return new SemanticArrayIntrinsicCallNode(
                SemanticArrayIntrinsicKind.MEMCPY,
                receiver,
                args
        );
    }

    // ============================================================
    // memmove
    // ============================================================

    private SemanticArrayIntrinsicCallNode lowerMemmove(
            CallNode call,
            MemberAccessNode mac
    ) {
        if (call.args.size() != 3) {
            ctx.error("Array intrinsic 'memmove' expects exactly 3 arguments");
            return null;
        }

        SemanticExprNode receiver =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        "memmove receiver"
                );

        exprAnalyzer.requireArrayType(
                receiver,
                "memmove receiver"
        );

        SemanticExprNode dstStart =
                exprAnalyzer.analyzeExpr(call.args.get(0));

        SemanticExprNode srcStart =
                exprAnalyzer.analyzeExpr(call.args.get(1));

        SemanticExprNode count =
                exprAnalyzer.analyzeExpr(call.args.get(2));

        return new SemanticArrayIntrinsicCallNode(
                SemanticArrayIntrinsicKind.MEMMOVE,
                receiver,
                List.of(
                        dstStart,
                        srcStart,
                        count
                )
        );
    }

    // ============================================================
    // Dynamic Array Init Shorthand
    // ============================================================

    /**
     * Lowers:
     *
     *   xs(size)
     *   deref(p)(size)
     *
     * into:
     *
     *   xs.init(size)
     *
     * semantically.
     */
    private SemanticArrayIntrinsicCallNode tryLowerDynamicArrayInitShorthand(
            CallNode call
    ) {
        if (call.args.size() != 1) {
            return null;
        }

        /*
         * Disambiguate ordinary function calls:
         *
         *   f(x)
         *
         * from dynamic array init shorthand:
         *
         *   xs(x)
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

        SemanticExprNode receiver =
                exprAnalyzer.analyzeExpr(call.callee);

        if (!exprAnalyzer.isDynamicArrayType(receiver.type)) {
            return null;
        }

        exprAnalyzer.requireLValue(
                receiver,
                "array initialization target"
        );

        SemanticExprNode size =
                exprAnalyzer.analyzeExpr(call.args.get(0));

        return new SemanticArrayIntrinsicCallNode(
                SemanticArrayIntrinsicKind.INIT,
                receiver,
                List.of(size)
        );
    }
}