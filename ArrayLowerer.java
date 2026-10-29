import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    public SemanticArrayIntrinsicCallNode tryLowerIntrinsicCall(ASTNode expr) {
        if (!(expr instanceof CallNode call)) {
            return null;
        }

        if (call.callee instanceof QualifiedNameNode) {
            return null;
        }

        if (call.callee instanceof MemberAccessNode mac) {
            SemanticArrayIntrinsicCallNode intrinsic =
                    tryLowerMemberIntrinsic(call, mac);

            if (intrinsic != null) {
                return intrinsic;
            }

        }

        return tryLowerDynamicArrayInitShorthand(call);
    }

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

    private boolean isStructReceiver(MemberAccessNode mac) {
        SemanticExprNode target = exprAnalyzer.analyzeExpr(mac.target);

        return target.type instanceof SemanticStructType structType
                && structType.structSymbol != null;
    }

    public boolean shouldSuppressCallDiagnostic(CallNode call) {
        if (call.callee instanceof MemberAccessNode mac) {
            if (!isArrayIntrinsicMemberName(mac.memberName)) {
                return false;
            }

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

    private SemanticArrayIntrinsicCallNode tryLowerMemberIntrinsic(
            CallNode call,
            MemberAccessNode mac
    ) {

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
                exprAnalyzer.analyzeExprWithExpectedType(
                        call.args.get(0),
                        memcpySourceContext(receiver.type));

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

    private static SemanticType memcpySourceContext(SemanticType receiverType) {
        SemanticType elementType = null;

        if (receiverType instanceof SemanticDynamicArrayType dynamicArray) {
            elementType = dynamicArray.elementType;
        } else if (receiverType instanceof SemanticStaticArrayType staticArray) {
            elementType = staticArray.elementType;
        }

        if (elementType == null) {
            return null;
        }

        return new SemanticViewType(
                new SemanticDynamicArrayType(elementType)
        );
    }

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

    private SemanticArrayIntrinsicCallNode tryLowerDynamicArrayInitShorthand(
            CallNode call
    ) {
        if (call.args.size() != 1) {
            return null;
        }

        if (call.callee instanceof VarNode calleeVar) {
            VariableSymbol var = ctx.resolve(calleeVar.name);

            if (var == null && ctx.hasAnyFunctionNamed(calleeVar.name)) {
                return null;
            }

            if (var == null) {
                return null;
            }
        }

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
