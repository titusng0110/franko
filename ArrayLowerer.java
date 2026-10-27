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
 *
 * and length returns uint32_t:
 *
 *   xs.length()
 *   ys.length()
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

        /*
         * Qualified callees (ns::f) are never array intrinsics and never
         * dynamic-array init shorthand, so skip straight to ordinary
         * overload resolution.
         */
        if (call.callee instanceof QualifiedNameNode) {
            return null;
        }

        if (call.callee instanceof MemberAccessNode mac) {
            SemanticArrayIntrinsicCallNode intrinsic =
                    tryLowerMemberIntrinsic(call, mac);

            if (intrinsic != null) {
                return intrinsic;
            }

            /*
             * Not a named array intrinsic. Fall through to the dynamic-array
             * init shorthand so member receivers work too:
             *
             *   b.data(4)   ->  b.data.init(4)
             *
             * Non-array member callees fall out of the shorthand as null and
             * reach the ordinary member-call diagnostic in ExpressionAnalyzer.
             */
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
                 "memmove",
                 "length" -> true;

            default -> false;
        };
    }

    /**
     * Peeks at a member-call receiver to decide intrinsic vs method dispatch.
     *
     * A struct-typed receiver means the call is a struct method call, even
     * when the member name coincides with an array intrinsic name. The peek
     * analyzes the target once; the chosen lowering path re-analyzes it,
     * so invalid targets may report duplicate diagnostics. Duplicates are
     * tolerated: invalid programs are rejected either way, and valid
     * targets produce no diagnostics on re-analysis.
     */
    private boolean isStructReceiver(MemberAccessNode mac) {
        SemanticExprNode target = exprAnalyzer.analyzeExpr(mac.target);

        return target.type instanceof SemanticStructType structType
                && structType.structSymbol != null;
    }

    /**
     * Returns true when a call that could not be lowered to an array intrinsic
     * should not fall through to ordinary overload resolution, because doing so
     * would emit a misleading "no matching overload" diagnostic.
     *
     * Covers:
     *
     *   - reserved array-intrinsic member names on receivers that are not valid
     *     intrinsic receivers (the lowering already reported the real problem),
     *
     *   - argument-list init shorthand applied to a variable that is not a
     *     dynamic array (tryLowerDynamicArrayInitShorthand reported the real
     *     problem).
     */
    public boolean shouldSuppressCallDiagnostic(CallNode call) {
        if (call.callee instanceof MemberAccessNode mac) {
            if (!isArrayIntrinsicMemberName(mac.memberName)) {
                return false;
            }

            /*
             * Struct receivers with intrinsic-coinciding method names fall
             * through to method resolution, so their diagnostics must not
             * be suppressed here.
             */
            return !isStructReceiver(mac);
        }

        if (call.args.size() == 1
                && call.callee instanceof VarNode calleeVar) {
            VariableSymbol var = ctx.resolve(calleeVar.name);

            return var != null
                    && !ctx.hasAnyFunctionNamed(calleeVar.name)
                    && !exprAnalyzer.isDynamicArrayType(var.type);
        }

        return false;
    }

    // ============================================================
    // Member Intrinsics
    // ============================================================

    private SemanticArrayIntrinsicCallNode tryLowerMemberIntrinsic(
            CallNode call,
            MemberAccessNode mac
    ) {
        /*
         * Type-directed dispatch: a struct-typed receiver is a method call,
         * never an array intrinsic, even when the member name coincides
         * with an intrinsic name (e.g. a method named `init`). Return null
         * silently so method lowering handles it without a spurious
         * array-receiver diagnostic.
         */
        if (isStructReceiver(mac)) {
            return null;
        }

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

            case "length" ->
                    lowerLength(call, mac);

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
     *   target.memcpy(sourcePtr)
     *   target.memcpy(sourcePtr, dstStart, srcStart, count)
     *
     * The source argument is intentionally analyzed as a normal expression.
     *
     * Franko arrays are not passed as values. Therefore memcpy source should be
     * an pointer to an array:
     *
     *   ptr<array<T>>
     *   ptr<array<T, N>>
     *
     * Example source-level shape:
     *
     *   dst.memcpy(getptr(src));
     *
     * Full validation that sourcePtr is ptr<array<...>> belongs in
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

        SemanticExprNode sourcePtr =
                exprAnalyzer.analyzeExpr(call.args.get(0));

        List<SemanticExprNode> args = new ArrayList<>();

        args.add(sourcePtr);

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
    // length
    // ============================================================

    /**
     * Lowers:
     *
     *   target.length()
     *
     * The receiver only needs an array type. Unlike the mutating
     * intrinsics, length is read-only, so a plain expression (not
     * necessarily a storage-backed lvalue) is accepted.
     */
    private SemanticArrayIntrinsicCallNode lowerLength(
            CallNode call,
            MemberAccessNode mac
    ) {
        if (!call.args.isEmpty()) {
            ctx.error("Array intrinsic 'length' expects exactly 0 arguments");
            return null;
        }

        SemanticExprNode receiver =
                exprAnalyzer.analyzeExpr(mac.target);

        exprAnalyzer.requireArrayType(
                receiver,
                "length() receiver"
        );

        return new SemanticArrayIntrinsicCallNode(
                SemanticArrayIntrinsicKind.LENGTH,
                receiver,
                List.of()
        );
    }

    // ============================================================
    // Dynamic Array Init Shorthand
    // ============================================================

    /**
     * Lowers:
     *
     *   xs(size)
     *   $(p)(size)
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

        /*
         * Peek at member-call receivers before analyzing the full callee.
         *
         * Analyzing `s.method` as a field access would report "has no field
         * named 'method'" before method lowering runs, producing a spurious
         * error alongside the real method resolution. When the receiver is
         * a struct and the member is not a dynamic-array field, this cannot
         * be dynamic-array init shorthand, so return early and let method
         * lowering handle the call without polluting diagnostics.
         */
        if (call.callee instanceof MemberAccessNode mac) {
            SemanticExprNode targetPeek =
                    exprAnalyzer.analyzeExpr(mac.target);

            if (targetPeek.type instanceof SemanticStructType structType
                    && structType.structSymbol != null) {
                FieldSymbol field =
                        structType.structSymbol.resolveField(mac.memberName);

                if (field == null) {
                    return null;
                }

                if (!exprAnalyzer.isDynamicArrayType(field.type)) {
                    return null;
                }
            }
        }

        SemanticExprNode receiver =
                exprAnalyzer.analyzeExpr(call.callee);

        if (!exprAnalyzer.isDynamicArrayType(receiver.type)) {
            /*
             * The callee resolved to a variable that is not a dynamic array, so
             * this cannot be dynamic-array init shorthand. Report a clear
             * diagnostic here; ExpressionAnalyzer suppresses the misleading
             * "no matching overload" fallback for this exact case.
             */
            if (call.callee instanceof VarNode calleeVar
                    && ctx.resolve(calleeVar.name) != null
                    && !ctx.hasAnyFunctionNamed(calleeVar.name)) {
                ctx.error("'" + calleeVar.name
                        + "' is not a dynamic array, so it cannot be initialized with an argument list");
            }

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