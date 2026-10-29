import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

public final class ExpressionAnalyzer {

    private final SymbolTable ctx;
    private final ConstExpressionEvaluator constEval;

    private ArrayLowerer arrayLowerer = null;

    private Function<TypeNode, SemanticType> typeResolver = null;

    private static final SemanticType FALLBACK_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.INT32);

    private static final SemanticType BOOL_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8);

    private static final SemanticType NULL_TYPE =
            new SemanticNullType();

    private static final Set<String> BOOL_RESULT_OPS = Set.of(
            "==", "!=", "<", ">", "<=", ">=", "&&", "||"
    );

    private static final Set<String> NON_BOOL_INTEGER_RESULT_OPS = Set.of(
            "+", "-", "*", "/", "%",
            "&", "|", "^",
            "<<", ">>"
    );

    public ExpressionAnalyzer(
            SymbolTable ctx,
            ConstExpressionEvaluator constEval
    ) {
        this.ctx = Objects.requireNonNull(ctx);
        this.constEval = Objects.requireNonNull(constEval);
    }

    public void setArrayLowerer(ArrayLowerer arrayLowerer) {
        this.arrayLowerer = Objects.requireNonNull(arrayLowerer);
    }

    public void setTypeResolver(
            Function<TypeNode, SemanticType> typeResolver
    ) {
        this.typeResolver = Objects.requireNonNull(typeResolver);
    }

    private ArrayLiteralLowerer arrayLiteralLowerer = null;

    public void setArrayLiteralLowerer(ArrayLiteralLowerer lowerer) {
        this.arrayLiteralLowerer = Objects.requireNonNull(lowerer);
    }

    public SemanticExprNode analyzeExprWithExpectedType(
            ASTNode node,
            SemanticType expectedType
    ) {
        SemanticExprNode expr;

        if (node instanceof ArrayLiteralNode lit) {
            expr = lowerArrayLiteral(lit, expectedType);
        } else {
            expr = analyzeExpr(node);
        }

        if (arrayLiteralLowerer == null) {
            return expr;
        }

        return arrayLiteralLowerer.resolveImmediateDecay(expr, expectedType);
    }

    private SemanticExprNode lowerArrayLiteral(
            ArrayLiteralNode lit,
            SemanticType expectedType
    ) {
        if (arrayLiteralLowerer == null) {
            ctx.error("Internal compiler error: no array literal lowerer "
                    + "wired into ExpressionAnalyzer");

            return new SemanticIntLiteralNode(
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        return arrayLiteralLowerer.lower(lit, expectedType);
    }

    public SemanticExprNode analyzeExpr(ASTNode node) {
        if (node instanceof ArrayLiteralNode lit) {
            return lowerArrayLiteral(lit, null);
        }

        if (node instanceof IntNode n) {
            BigInteger val = constEval.parseIntegerLiteralSafe(n.value);
            return new SemanticIntLiteralNode(FALLBACK_TYPE, val, n.value);
        }

        if (node instanceof NullNode) {
            return new SemanticNullLiteralNode(NULL_TYPE);
        }

        if (node instanceof VarNode n) {
            VariableSymbol sym = ctx.resolve(n.name);

            if (sym == null) {
                if ("self".equals(n.name)) {
                    ctx.error("keyword 'self' is only valid inside a method body");
                } else if (ctx.hasAnyFunctionNamed(n.name)) {
                    ctx.error("Function name '" + n.name + "' cannot be used as a value");
                } else {
                    ctx.error("Undeclared variable '" + n.name + "'");
                }

                sym = new VariableSymbol(null, n.name, FALLBACK_TYPE);
            }

            return new SemanticVarExprNode(sym.type, sym);
        }

        if (node instanceof QualifiedNameNode n) {
            return analyzeQualifiedNameExpr(n);
        }

        if (node instanceof UnaryOpNode n) {
            SemanticExprNode expr = analyzeExpr(n.expr);

            SemanticType type = n.op.equals("!") ? BOOL_TYPE : expr.type;

            BigInteger folded = constEval.foldUnary(
                    n.op,
                    expr.constantValue
            );

            return new SemanticUnaryOpNode(
                    type,
                    folded,
                    n.op,
                    expr
            );
        }

        if (node instanceof BinOpNode n) {
            SemanticExprNode left = analyzeExpr(n.left);
            SemanticExprNode right = analyzeExpr(n.right);

            SemanticType type = inferBinaryResultType(
                    n.op,
                    left,
                    right
            );

            BigInteger folded = constEval.foldBinary(
                    n.op,
                    left.constantValue,
                    right.constantValue
            );

            return new SemanticBinOpNode(
                    type,
                    folded,
                    n.op,
                    left,
                    right
            );
        }

        if (node instanceof ArrayAccessNode n) {
            SemanticExprNode target = analyzeExpr(n.target);
            SemanticExprNode index = analyzeExpr(n.index);

            SemanticType elemType = extractElementType(target.type);

            return new SemanticArrayAccessNode(
                    elemType,
                    target,
                    index
            );
        }

        if (node instanceof CallNode n) {
            return analyzeCallExpr(n);
        }

        if (node instanceof MemberAccessNode n) {
            SemanticExprNode target = analyzeExpr(n.target);

            if (!(target.type instanceof SemanticStructType structType)) {
                ctx.error("Member access '"
                        + n.memberName
                        + "' is only valid on struct values, got type "
                        + target.type.describe());

                return new SemanticIntLiteralNode(
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }

            FieldSymbol field = structType.structSymbol.resolveField(
                    n.memberName
            );

            if (field == null) {
                ctx.error("Struct '"
                        + structType.structSymbol.qualifiedName()
                        + "' has no field named '"
                        + n.memberName
                        + "'");

                return new SemanticIntLiteralNode(
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }

            return new SemanticMemberAccessNode(
                    field.type,
                    target,
                    field
            );
        }

        if (node instanceof GetPtrNode n) {
            String intrinsicName = switch (n.kind) {
                case OWNER -> "getptr";
                case MUTABLE -> "getmview";
                case READ_ONLY -> "getview";
            };

            SemanticExprNode target = analyzeRequiredLValueExpr(
                    n.target,
                    intrinsicName + "(...) target"
            );

            return new SemanticGetPtrNode(
                    pointerTypeFor(n.kind, target.type),
                    target
            );
        }

        if (node instanceof DerefNode n) {
            SemanticExprNode expr = analyzeExpr(n.expr);

            SemanticType refType = FALLBACK_TYPE;

            if (expr.type instanceof SemanticPtrType ptrT) {
                refType = ptrT.referencedType;
            } else if (expr.type instanceof SemanticNullType) {
                ctx.error("Cannot dereference null");
            } else {
                ctx.error("Cannot dereference non-pointer type");
            }

            return new SemanticDerefNode(refType, expr);
        }

        if (node instanceof CastNode n) {
            SemanticType targetType = resolveTypeNode(n.target);
            SemanticExprNode expr = analyzeExpr(n.expr);

            String syntax = n.unsafe ? "unsafe_cast" : "cast";

            if (expr.type instanceof SemanticVoidType) {
                ctx.error("Operand of '" + syntax + "' cannot have void type");

                return new SemanticCastExprNode(
                        targetType,
                        null,
                        expr,
                        n.unsafe
                );
            }

            if (n.unsafe
                    && (!(targetType instanceof SemanticPrimitiveType)
                        || !(expr.type instanceof SemanticPrimitiveType))) {
                ctx.error("Operand of 'unsafe_cast' must be an integer "
                        + "expression, got " + expr.type.describe());

                return new SemanticCastExprNode(
                        targetType,
                        null,
                        expr,
                        true
                );
            }

            if (targetType instanceof SemanticPrimitiveType targetPrimitive
                    && expr.type instanceof SemanticPrimitiveType sourcePrimitive) {
                if (sourcePrimitive.kind == targetPrimitive.kind) {
                    return expr;
                }

                return new SemanticCastExprNode(
                        targetType,
                        expr.constantValue,
                        expr,
                        n.unsafe
                );
            }

            return new SemanticCastExprNode(
                    targetType,
                    null,
                    expr,
                    n.unsafe
            );
        }

        if (node instanceof StringLiteralNode) {
            ctx.error("String literal is not a valid expression here; a "
                    + "string literal is a view<[byte, N]> value");

            return new SemanticIntLiteralNode(
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        ctx.error("Unrecognized expression node: "
                + node.getClass().getSimpleName());

        return new SemanticIntLiteralNode(
                FALLBACK_TYPE,
                BigInteger.ZERO,
                "0"
        );
    }

    private SemanticType resolveTypeNode(TypeNode node) {
        if (typeResolver == null) {
            ctx.error("Internal compiler error: no type resolver wired into "
                    + "ExpressionAnalyzer");
            return FALLBACK_TYPE;
        }

        return typeResolver.apply(node);
    }

    private static SemanticType pointerTypeFor(
            PtrPermission kind,
            SemanticType referencedType
    ) {
        return switch (kind) {
            case OWNER -> new SemanticPtrType(referencedType);
            case MUTABLE -> new SemanticMViewType(referencedType);
            case READ_ONLY -> new SemanticViewType(referencedType);
        };
    }

    public BigInteger preEvaluatedConstValue(
            ASTNode expr,
            String roleDescription
    ) {
        if (expr instanceof IntNode n) {
            BigInteger parsed =
                    ConstExpressionEvaluator.parseIntegerLiteralPure(n.value);

            if (parsed != null) {
                return parsed;
            }
        }

        ctx.error("Invalid constant expression for " + roleDescription);
        return BigInteger.ZERO;
    }

    public SemanticExprNode analyzeRequiredLValueExpr(
            ASTNode node,
            String roleDescription
    ) {
        SemanticExprNode expr = analyzeExpr(node);
        requireLValue(expr, roleDescription);
        return expr;
    }

    public void requireLValue(
            SemanticExprNode expr,
            String roleDescription
    ) {
        if (!isValidStorageBackedLValue(expr)) {
            ctx.error(roleDescription + " must be a storage-backed lvalue");
        }
    }

    public void requireArrayType(
            SemanticExprNode expr,
            String roleDescription
    ) {
        if (!isArrayType(expr.type)) {
            ctx.error(roleDescription + " must have array type, got "
                    + expr.type.describe());
        }
    }

    public void requireDynamicArrayType(
            SemanticExprNode expr,
            String roleDescription
    ) {
        if (!isDynamicArrayType(expr.type)) {
            ctx.error(roleDescription + " must have dynamic array type, got "
                    + expr.type.describe());
        }
    }

    public boolean isArrayType(SemanticType type) {
        return type instanceof SemanticDynamicArrayType
                || type instanceof SemanticStaticArrayType;
    }

    public boolean isDynamicArrayType(SemanticType type) {
        return type instanceof SemanticDynamicArrayType;
    }

    private boolean isValidStorageBackedLValue(SemanticExprNode expr) {
        if (expr == null || !expr.isLValue()) {
            return false;
        }

        if (expr instanceof SemanticVarExprNode) {
            return true;
        }

        if (expr instanceof SemanticDerefNode) {
            return true;
        }

        if (expr instanceof SemanticArrayAccessNode arrayAccess) {
            return isValidStorageBackedLValue(arrayAccess.target);
        }

        return expr.isLValue();
    }

    private SemanticExprNode analyzeQualifiedNameExpr(QualifiedNameNode q) {
        List<String> namespaceSegments =
                q.segments.subList(0, q.segments.size() - 1);
        String name = q.segments.get(q.segments.size() - 1);
        String qualified = String.join("::", q.segments);
        String namespacePath = String.join("::", namespaceSegments);

        NamespaceSymbol namespace = ctx.resolveNamespace(namespaceSegments);

        if (namespace == null) {
            ctx.error("Unknown namespace '" + namespacePath + "'");

            return new SemanticIntLiteralNode(
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        VariableSymbol sym = ctx.resolveGlobalVarIn(namespace, name);

        if (sym == null) {
            if (ctx.hasAnyFunctionNamedIn(namespace, name)) {
                ctx.error("Function name '"
                        + qualified
                        + "' cannot be used as a value");
            } else {
                ctx.error("Undeclared variable '"
                        + qualified
                        + "'");
            }

            sym = new GlobalVariableSymbol(namespace.name, name, FALLBACK_TYPE);
        }

        if (sym instanceof GlobalVariableSymbol global
                && global.compileTimeConstantValue != null) {
            return new SemanticIntLiteralNode(
                    sym.type,
                    global.compileTimeConstantValue,
                    global.compileTimeConstantValue.toString(10)
            );
        }

        return new SemanticVarExprNode(sym.type, sym);
    }

    private List<SymbolTable.CallArg> lowerCallArgs(List<ASTNode> rawArgs) {
        List<SymbolTable.CallArg> out = new ArrayList<>();

        for (ASTNode arg : rawArgs) {
            if (arg instanceof ArrayLiteralNode lit) {
                out.add(SymbolTable.CallArg.literal(lit));
            } else {
                out.add(SymbolTable.CallArg.lowered(analyzeExpr(arg)));
            }
        }

        return out;
    }

    private List<SemanticExprNode> finalizeCallArgs(
            List<SymbolTable.CallArg> callArgs,
            List<SemanticType> parameterTypes
    ) {
        List<SemanticExprNode> out = new ArrayList<>();

        for (int i = 0; i < callArgs.size(); i++) {
            SymbolTable.CallArg callArg = callArgs.get(i);

            if (!callArg.isLiteral()) {
                SemanticType context = parameterTypes != null
                        && i < parameterTypes.size()
                        ? parameterTypes.get(i)
                        : null;

                out.add(arrayLiteralLowerer == null
                        ? callArg.lowered
                        : arrayLiteralLowerer.resolveImmediateDecay(
                                callArg.lowered,
                                context));

                continue;
            }

            SemanticType context = parameterTypes != null
                    && i < parameterTypes.size()
                    ? parameterTypes.get(i)
                    : null;

            out.add(lowerArrayLiteral(callArg.literal, context));
        }

        return out;
    }

    private static List<SemanticType> callArgTypes(List<SemanticExprNode> args) {
        List<SemanticType> out = new ArrayList<>();

        for (SemanticExprNode arg : args) {
            out.add(arg == null ? null : arg.type);
        }

        return out;
    }

    private SemanticExprNode analyzeCallExpr(CallNode call) {
        if (arrayLowerer != null) {
            SemanticArrayIntrinsicCallNode intrinsic =
                    arrayLowerer.tryLowerIntrinsicCall(call);

            if (intrinsic != null) {
                return intrinsic;
            }

            if (arrayLowerer.shouldSuppressCallDiagnostic(call)) {
                return new SemanticIntLiteralNode(
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }
        }

        if (call.callee instanceof VarNode calleeVar) {
            List<SymbolTable.CallArg> callArgs = lowerCallArgs(call.args);

            FunctionSymbol fn = ctx.resolveFunctionWithLiterals(
                    calleeVar.name,
                    callArgs
            );

            List<SemanticType> parameterTypes =
                    fn == null ? null : fn.parameterTypes();

            List<SemanticExprNode> args =
                    finalizeCallArgs(callArgs, parameterTypes);

            if (fn == null) {
                FunctionSymbol legacy = ctx.resolveFunction(
                        calleeVar.name,
                        args
                );

                if (legacy == null) {
                    ctx.error("No matching overload for function call '"
                            + formatCallSignature(
                                    calleeVar.name,
                                    callArgTypes(args))
                            + "'");
                }

                FunctionSymbol fallbackFn = new FunctionSymbol(
                        null,
                        calleeVar.name,
                        List.of(),
                        FALLBACK_TYPE,
                        false
                );

                return new SemanticFunctionCallNode(
                        FALLBACK_TYPE,
                        fallbackFn,
                        args
                );
            }

            return new SemanticFunctionCallNode(
                    fn.returnType(),
                    fn,
                    args
            );
        }

        if (call.callee instanceof QualifiedNameNode calleeQ) {
            List<SymbolTable.CallArg> callArgs = lowerCallArgs(call.args);

            List<String> namespaceSegments = calleeQ.segments.subList(
                    0, calleeQ.segments.size() - 1);
            String calleeName =
                    calleeQ.segments.get(calleeQ.segments.size() - 1);
            String qualifiedCallee = String.join("::", calleeQ.segments);

            NamespaceSymbol namespace =
                    ctx.resolveNamespace(namespaceSegments);

            if (namespace == null) {
                ctx.error("Unknown namespace '"
                        + String.join("::", namespaceSegments)
                        + "'");

                return new SemanticIntLiteralNode(
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }

            FunctionSymbol fn = ctx.resolveFunctionInWithLiterals(
                    namespace,
                    calleeName,
                    callArgs
            );

            List<SemanticType> parameterTypes =
                    fn == null ? null : fn.parameterTypes();

            List<SemanticExprNode> args =
                    finalizeCallArgs(callArgs, parameterTypes);

            if (fn == null) {
                FunctionSymbol legacy = ctx.resolveFunctionIn(
                        namespace,
                        calleeName,
                        args
                );

                if (legacy == null) {
                    ctx.error("No matching overload for function call '"
                            + formatCallSignature(
                                    qualifiedCallee,
                                    callArgTypes(args))
                            + "'");
                }

                FunctionSymbol fallbackFn = new FunctionSymbol(
                        namespace.name,
                        calleeName,
                        List.of(),
                        FALLBACK_TYPE,
                        false
                );

                return new SemanticFunctionCallNode(
                        FALLBACK_TYPE,
                        fallbackFn,
                        args
                );
            }

            return new SemanticFunctionCallNode(
                    fn.returnType(),
                    fn,
                    args
            );
        }

        if (call.callee instanceof MemberAccessNode mac) {
            return analyzeMethodCallExpr(call, mac);
        }

        ctx.error("Unsupported call expression");

        return new SemanticIntLiteralNode(
                FALLBACK_TYPE,
                BigInteger.ZERO,
                "0"
        );
    }

    private SemanticExprNode analyzeMethodCallExpr(
            CallNode call,
            MemberAccessNode mac
    ) {
        SemanticExprNode target = analyzeExpr(mac.target);

        if (!(target.type instanceof SemanticStructType structType)
                || structType.structSymbol == null) {
            if (target.type instanceof SemanticPtrType) {
                ctx.error("Method call '"
                        + mac.memberName
                        + "' is only valid on struct values, got type "
                        + target.type.describe()
                        + "; use $(p)."
                        + mac.memberName
                        + "(...) for pointer receivers");
            } else {
                ctx.error("Method call '"
                        + mac.memberName
                        + "' is only valid on struct values, got type "
                        + target.type.describe());
            }

            return new SemanticIntLiteralNode(
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        StructSymbol owner = structType.structSymbol;

        if (!isValidStorageBackedLValue(target)) {
            ctx.error("Method call receiver must be a storage-backed lvalue: "
                    + "a variable, field, array element, or $(...) expression");

            return new SemanticIntLiteralNode(
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        List<SymbolTable.CallArg> callArgs = lowerCallArgs(call.args);

        MethodSymbol method = ctx.resolveMethodWithLiterals(owner, mac.memberName, callArgs);

        List<SemanticType> userParameterTypes = null;

        if (method != null) {
            userParameterTypes = new ArrayList<>();

            for (int i = 1; i < method.parameters.size(); i++) {
                userParameterTypes.add(method.parameters.get(i).type);
            }
        }

        List<SemanticExprNode> userArgs =
                finalizeCallArgs(callArgs, userParameterTypes);

        if (method == null) {
            List<SemanticType> userArgTypes = callArgTypes(userArgs);

            MethodSymbol legacy = ctx.resolveMethod(
                    owner,
                    mac.memberName,
                    userArgs
            );

            if (legacy == null) {
                ctx.error("No matching overload for method call '"
                        + owner.qualifiedName()
                        + "."
                        + formatCallSignature(mac.memberName, userArgTypes)
                        + "'");
            }
        }

        SemanticType selfType = new SemanticPtrType(target.type);

        if (method != null
                && !method.parameters.isEmpty()
                && method.parameters.get(0).type instanceof SemanticPtrType declaredSelf) {
            selfType = declaredSelf;
        }

        SemanticGetPtrNode selfArg = new SemanticGetPtrNode(selfType, target);

        List<SemanticExprNode> fullArgs = new ArrayList<>();
        fullArgs.add(selfArg);
        fullArgs.addAll(userArgs);

        if (method == null) {
            MethodSymbol fallbackFn = new MethodSymbol(
                    owner,
                    mac.memberName,
                    List.of(),
                    FALLBACK_TYPE,
                    false
            );

            return new SemanticFunctionCallNode(
                    FALLBACK_TYPE,
                    fallbackFn,
                    fullArgs
            );
        }

        return new SemanticFunctionCallNode(
                method.returnType(),
                method,
                fullArgs
        );
    }

    private SemanticType inferBinaryResultType(
            String op,
            SemanticExprNode left,
            SemanticExprNode right
    ) {
        if (BOOL_RESULT_OPS.contains(op)) {
            return BOOL_TYPE;
        }

        if (NON_BOOL_INTEGER_RESULT_OPS.contains(op)) {
            boolean leftConst = left != null && left.isConstant();
            boolean rightConst = right != null && right.isConstant();

            if (leftConst && !rightConst) return right.type;
            if (!leftConst && rightConst) return left.type;

            return left.type;
        }

        return left.type;
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

    private String formatCallSignature(
            String name,
            List<SemanticType> argTypes
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append(name).append("(");

        for (int i = 0; i < argTypes.size(); i++) {
            if (i > 0) sb.append(", ");

            SemanticType type = argTypes.get(i);
            sb.append(type == null ? "<null>" : type.describe());
        }

        sb.append(")");

        return sb.toString();
    }
}
