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

    public SastNode tryLowerIntrinsicCall(AstNode expr) {
        ctx.diagnostics().push(expr.span);
        try {
            return tryLowerIntrinsicCallInner(expr);
        } finally {
            ctx.diagnostics().pop();
        }
    }

    private SastNode tryLowerIntrinsicCallInner(AstNode expr) {
        if (expr == null || expr.kind != AstKind.CALL
                || expr.callee == null || expr.callee.kind != AstKind.MEMBER_ACCESS) {
            return null;
        }

        return tryLowerMemberIntrinsic(expr, expr.callee);
    }

    public boolean isArrayIntrinsicMemberName(String name) {
        return SemanticArrayIntrinsicKind.fromRuntimeName(name) != null;
    }

    private boolean isAggregateReceiver(AstNode mac) {
        SastNode target = exprAnalyzer.analyzeExpr(mac.target);

        if (target.type.kind == SastTypeKind.STRUCT) {
            return target.type.structSymbol != null;
        }

        return target.type.kind == SastTypeKind.TUPLE;
    }

    public boolean shouldSuppressCallDiagnostic(AstNode call) {
        if (call.callee == null || call.callee.kind != AstKind.MEMBER_ACCESS) {
            return false;
        }

        if (!isArrayIntrinsicMemberName(call.callee.name)) {
            return false;
        }

        return !isAggregateReceiver(call.callee);
    }

    private SastNode tryLowerMemberIntrinsic(
            AstNode call,
            AstNode mac
    ) {

        if (isAggregateReceiver(mac)) {
            return null;
        }

        if ("init".equals(mac.name)) {
            return lowerDynamicArrayAlloc(
                    call,
                    mac,
                    SemanticArrayIntrinsicKind.INIT
            );
        }

        if ("init_zero".equals(mac.name)) {
            return lowerDynamicArrayAlloc(
                    call,
                    mac,
                    SemanticArrayIntrinsicKind.INIT_ZERO
            );
        }

        if ("resize".equals(mac.name)) {
            return lowerDynamicArrayAlloc(
                    call,
                    mac,
                    SemanticArrayIntrinsicKind.RESIZE
            );
        }

        if ("uninit".equals(mac.name)) {
            return lowerUninit(call, mac);
        }

        if ("memset".equals(mac.name)) {
            return lowerMemset(call, mac);
        }

        if ("memcpy".equals(mac.name)) {
            return lowerMemcpy(call, mac);
        }

        if ("memmove".equals(mac.name)) {
            return lowerMemmove(call, mac);
        }

        if ("length".equals(mac.name)) {
            return lowerLength(call, mac);
        }

        return lowerSignatureIntrinsic(call, mac);
    }

    private SastNode lowerSignatureIntrinsic(
            AstNode call,
            AstNode mac
    ) {
        SemanticArrayIntrinsicKind kind =
                SemanticArrayIntrinsicKind.fromRuntimeName(mac.name);

        if (kind == null) {
            return null;
        }

        if (!kind.acceptsArity(call.args.size())) {
            ctx.error("Array intrinsic '"
                    + kind.runtimeName
                    + "' expects "
                    + kind.arityDescription()
                    + " arguments, got "
                    + call.args.size());
            return null;
        }

        SastNode receiver = kind.requiresStorageLValue()
                ? exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        kind.runtimeName + " receiver"
                )
                : exprAnalyzer.analyzeExpr(mac.target);

        exprAnalyzer.requireArrayType(
                receiver,
                kind.runtimeName + "() receiver"
        );

        List<SastNode> args = new ArrayList<>();

        for (int i = 0; i < call.args.size(); i++) {
            SemanticIntrinsicArgKind argKind = kind.argKind(i);

            SastNode arg;

            if (argKind.isPointer()) {
                arg = exprAnalyzer.analyzeExprWithExpectedType(
                        call.args.get(i),
                        memcpySourceContext(receiver.type)
                );
            } else {
                arg = exprAnalyzer.analyzeExpr(call.args.get(i));
            }

            args.add(arg);
        }

        return SastNode.arrayIntrinsicCall(
                call.span,
                kind,
                receiver,
                args
        );
    }

    private SastNode lowerDynamicArrayAlloc(
            AstNode call,
            AstNode mac,
            SemanticArrayIntrinsicKind kind
    ) {
        if (call.args.size() != 1) {
            ctx.error("Array intrinsic '"
                    + kind.runtimeName
                    + "' expects exactly 1 argument");
            return null;
        }

        SastNode receiver =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        kind.runtimeName + " receiver"
                );

        exprAnalyzer.requireDynamicArrayType(
                receiver,
                kind.runtimeName + " receiver"
        );

        SastNode size =
                exprAnalyzer.analyzeExpr(call.args.get(0));

        return SastNode.arrayIntrinsicCall(
                call.span,
                kind,
                receiver,
                List.of(size)
        );
    }

    private SastNode lowerUninit(
            AstNode call,
            AstNode mac
    ) {
        if (!call.args.isEmpty()) {
            ctx.error("Array intrinsic 'uninit' expects exactly 0 arguments");
            return null;
        }

        SastNode receiver =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        "uninit receiver"
                );

        exprAnalyzer.requireDynamicArrayType(
                receiver,
                "uninit receiver"
        );

        return SastNode.arrayIntrinsicCall(
                call.span,
                SemanticArrayIntrinsicKind.UNINIT,
                receiver,
                List.of()
        );
    }

    private SastNode lowerMemset(
            AstNode call,
            AstNode mac
    ) {
        if (call.args.size() != 1 && call.args.size() != 3) {
            ctx.error("Array intrinsic 'memset' expects either 1 or 3 arguments");
            return null;
        }

        SastNode receiver =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        "memset receiver"
                );

        exprAnalyzer.requireArrayType(
                receiver,
                "memset receiver"
        );

        List<SastNode> args = new ArrayList<>();

        SastNode value =
                exprAnalyzer.analyzeExpr(call.args.get(0));

        args.add(value);

        if (call.args.size() == 3) {
            SastNode start =
                    exprAnalyzer.analyzeExpr(call.args.get(1));

            SastNode count =
                    exprAnalyzer.analyzeExpr(call.args.get(2));

            args.add(start);
            args.add(count);
        }

        return SastNode.arrayIntrinsicCall(
                call.span,
                SemanticArrayIntrinsicKind.MEMSET,
                receiver,
                args
        );
    }

    private SastNode lowerMemcpy(
            AstNode call,
            AstNode mac
    ) {
        if (call.args.size() != 1 && call.args.size() != 4) {
            ctx.error("Array intrinsic 'memcpy' expects either 1 or 4 arguments");
            return null;
        }

        SastNode receiver =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        "memcpy target"
                );

        exprAnalyzer.requireArrayType(
                receiver,
                "memcpy target"
        );

        SastNode sourcePtr =
                exprAnalyzer.analyzeExprWithExpectedType(
                        call.args.get(0),
                        memcpySourceContext(receiver.type));

        List<SastNode> args = new ArrayList<>();

        args.add(sourcePtr);

        if (call.args.size() == 4) {
            SastNode dstStart =
                    exprAnalyzer.analyzeExpr(call.args.get(1));

            SastNode srcStart =
                    exprAnalyzer.analyzeExpr(call.args.get(2));

            SastNode count =
                    exprAnalyzer.analyzeExpr(call.args.get(3));

            args.add(dstStart);
            args.add(srcStart);
            args.add(count);
        }

        return SastNode.arrayIntrinsicCall(
                call.span,
                SemanticArrayIntrinsicKind.MEMCPY,
                receiver,
                args
        );
    }

    private static SastType memcpySourceContext(SastType receiverType) {
        SastType elementType = null;

        if (receiverType.kind == SastTypeKind.DYNAMIC_ARRAY) {
            elementType = receiverType.elementType;
        } else if (receiverType.kind == SastTypeKind.STATIC_ARRAY) {
            elementType = receiverType.elementType;
        }

        if (elementType == null) {
            return null;
        }

        return SastType.view(
                SastType.dynamicArray(elementType)
        );
    }

    private SastNode lowerMemmove(
            AstNode call,
            AstNode mac
    ) {
        if (call.args.size() != 3) {
            ctx.error("Array intrinsic 'memmove' expects exactly 3 arguments");
            return null;
        }

        SastNode receiver =
                exprAnalyzer.analyzeRequiredLValueExpr(
                        mac.target,
                        "memmove receiver"
                );

        exprAnalyzer.requireArrayType(
                receiver,
                "memmove receiver"
        );

        SastNode dstStart =
                exprAnalyzer.analyzeExpr(call.args.get(0));

        SastNode srcStart =
                exprAnalyzer.analyzeExpr(call.args.get(1));

        SastNode count =
                exprAnalyzer.analyzeExpr(call.args.get(2));

        return SastNode.arrayIntrinsicCall(
                call.span,
                SemanticArrayIntrinsicKind.MEMMOVE,
                receiver,
                List.of(
                        dstStart,
                        srcStart,
                        count
                )
        );
    }

    private SastNode lowerLength(
            AstNode call,
            AstNode mac
    ) {
        if (!call.args.isEmpty()) {
            ctx.error("Array intrinsic 'length' expects exactly 0 arguments");
            return null;
        }

        SastNode receiver =
                exprAnalyzer.analyzeExpr(mac.target);

        exprAnalyzer.requireArrayType(
                receiver,
                "length() receiver"
        );

        return SastNode.arrayIntrinsicCall(
                call.span,
                SemanticArrayIntrinsicKind.LENGTH,
                receiver,
                List.of()
        );
    }

}
