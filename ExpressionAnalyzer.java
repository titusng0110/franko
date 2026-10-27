import java.math.BigInteger;
import java.util.*;

/**
 * ============================================================================
 * EXPRESSION ANALYZER
 * ============================================================================
 *
 * PURPOSE
 *
 * ExpressionAnalyzer lowers parser expression AST nodes into
 * SemanticExprNode trees.
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS PHASE DOES
 * ----------------------------------------------------------------------------
 *
 * This phase is responsible for:
 *
 *   - resolving variable references to VariableSymbol,
 *   - resolving function calls to FunctionSymbol overloads,
 *   - resolving array intrinsic calls,
 *   - inferring expression result types,
 *   - attaching constant folding metadata (BigInteger),
 *   - lowering structural constructs:
 *       - array indexing
 *       - unary and binary operations
 *       - pointer-of and dereference
 *   - validating structural requirements for lvalues where needed.
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS PHASE DOES NOT DO
 * ----------------------------------------------------------------------------
 *
 * This phase intentionally does NOT validate:
 *
 *   - assignment compatibility,
 *   - operator type legality,
 *   - function argument correctness,
 *   - return type correctness,
 *   - bounds safety,
 *   - dynamic array initialization correctness,
 *   - delete legality,
 *   - full constant expression legality.
 *
 * These are handled by later checkers.
 *
 * ----------------------------------------------------------------------------
 * IMPORTANT: ARRAY INITIALIZER LISTS
 * ----------------------------------------------------------------------------
 *
 * Array initializer lists MUST NOT reach this phase.
 *
 * They are eliminated by the desugarer into indexed assignments:
 *
 *     xs = [1,2,3]
 *
 * becomes:
 *
 *     xs[0] = 1
 *     xs[1] = 2
 *     xs[2] = 3
 *
 * Encountering ArrayLiteralNode here indicates a compiler pipeline bug.
 *
 */
public final class ExpressionAnalyzer {

    private final SymbolTable ctx;
    private final ConstExpressionEvaluator constEval;

    private ArrayLowerer arrayLowerer = null;

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

    // ============================================================
    // Public Expression Lowering API
    // ============================================================

    public SemanticExprNode analyzeExpr(ASTNode node) {
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
            SemanticExprNode target = analyzeRequiredLValueExpr(
                    n.target,
                    "getptr(...) target"
            );

            return new SemanticGetPtrNode(
                    new SemanticPtrType(target.type),
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
            SemanticPrimitiveKind targetKind =
                    SemanticPrimitiveKind.valueOf(n.target.name());

            SemanticType targetType =
                    new SemanticPrimitiveType(targetKind);

            SemanticExprNode expr = analyzeExpr(n.expr);

            if (expr.type instanceof SemanticVoidType) {
                ctx.error("Operand of 'cast' cannot have void type");
                return new SemanticCastExprNode(
                        targetType,
                        null,
                        expr,
                        false
                );
            }

            if (!(expr.type instanceof SemanticPrimitiveType sourcePrimitive)) {
                ctx.error("Operand of 'cast' must be an integer expression, got "
                        + expr.type.describe());
                return new SemanticCastExprNode(
                        targetType,
                        null,
                        expr,
                        true
                );
            }

            /*
             * Same-type casts are no-ops: return the operand unchanged so
             * no cast node (and no redundant C++ cast) is emitted.
             */
            if (sourcePrimitive.kind == targetKind) {
                return expr;
            }

            /*
             * Constants keep their folded value through the cast so the
             * later checker can validate the value against the target
             * range exactly like any other constant in a typed context.
             */
            return new SemanticCastExprNode(
                    targetType,
                    expr.constantValue,
                    expr,
                    !TypeChecker.isValuePreservingIntegerWidening(
                            sourcePrimitive.kind,
                            targetKind
                    )
            );
        }

        if (node instanceof ArrayLiteralNode) {
            throw new RuntimeException(
                    "ArrayLiteralNode should not reach ExpressionAnalyzer. Desugarer bug."
            );
        }

        if (node instanceof StringLiteralNode) {
            ctx.error("String literal is not a valid expression; string literals are only allowed as assignment or declaration initializers");

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

    // ============================================================
    // Const Expression Helpers
    // ============================================================

    /**
     * Reads a pre-evaluated constant slot.
     *
     * Constant-expression slots (static array sizes, global
     * initializers, struct field defaults) are evaluated by the
     * desugarer into canonical IntNode literals before semantic
     * analysis. This only parses the literal; validation (positivity,
     * range, fit) stays in later checkers.
     */
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

    // ============================================================
    // LValue Requirements
    // ============================================================

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

    // ============================================================
    // Call Lowering
    // ============================================================

    /**
     * Lowers a qualified variable reference:
     *
     *   util::offset
     *   a::b::limit
     *
     * The reference resolves strictly inside the named namespace path,
     * starting from the top level; there is no fallback to the global
     * namespace and no relative resolution through the current
     * namespace.
     */
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

        /*
         * The os::IS_* compile-time target constants fold into integer
         * literals here. Downstream this means conditions like
         * `os::IS_WINDOWS == 1` carry a constant value (so the backend
         * can prune the dead branch), and uses that would mutate or
         * observe the global (assignment, getptr) are rejected by the
         * existing literal checks with no special-casing.
         */
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

    private SemanticExprNode analyzeCallExpr(CallNode call) {
        if (arrayLowerer != null) {
            SemanticArrayIntrinsicCallNode intrinsic =
                    arrayLowerer.tryLowerIntrinsicCall(call);

            if (intrinsic != null) {
                return intrinsic;
            }

            /*
             * A clear diagnostic was already reported while attempting to lower
             * this call as an array intrinsic. Stop here instead of falling
             * through to ordinary overload resolution, which would emit a
             * second, misleading "no matching overload" error.
             */
            if (arrayLowerer.shouldSuppressCallDiagnostic(call)) {
                return new SemanticIntLiteralNode(
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }
        }

        if (call.callee instanceof VarNode calleeVar) {
            List<SemanticExprNode> args = new ArrayList<>();
            List<SemanticType> argTypes = new ArrayList<>();

            for (ASTNode arg : call.args) {
                SemanticExprNode loweredArg = analyzeExpr(arg);
                args.add(loweredArg);
                argTypes.add(loweredArg.type);
            }

            FunctionSymbol fn = ctx.resolveFunction(
                    calleeVar.name,
                    args
            );

            if (fn == null) {
                ctx.error("No matching overload for function call '"
                        + formatCallSignature(calleeVar.name, argTypes)
                        + "'");

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
            List<SemanticExprNode> args = new ArrayList<>();
            List<SemanticType> argTypes = new ArrayList<>();

            for (ASTNode arg : call.args) {
                SemanticExprNode loweredArg = analyzeExpr(arg);
                args.add(loweredArg);
                argTypes.add(loweredArg.type);
            }

            /*
             * All but the last segment must name a declared namespace
             * path, resolved from the top level; the last segment is
             * the called function.
             */
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

            FunctionSymbol fn = ctx.resolveFunctionIn(
                    namespace,
                    calleeName,
                    args
            );

            if (fn == null) {
                ctx.error("No matching overload for function call '"
                        + formatCallSignature(
                                qualifiedCallee,
                                argTypes
                        )
                        + "'");

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

    /**
     * Lowers a struct method call:
     *
     *   receiver.name(args...)
     *
     * into an ordinary function call to the resolved MethodSymbol with a
     * synthesized `getptr(receiver)` first argument for `self`.
     *
     * The receiver must be a struct-valued storage-backed lvalue
     * (a variable, field, array element, or $(...) chain). Pointer
     * receivers require an explicit deref: `$(p).method(...)`.
     */
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

        List<SemanticExprNode> userArgs = new ArrayList<>();
        List<SemanticType> userArgTypes = new ArrayList<>();

        for (ASTNode arg : call.args) {
            SemanticExprNode loweredArg = analyzeExpr(arg);
            userArgs.add(loweredArg);
            userArgTypes.add(loweredArg.type);
        }

        SemanticGetPtrNode selfArg = new SemanticGetPtrNode(
                new SemanticPtrType(target.type),
                target
        );

        List<SemanticExprNode> fullArgs = new ArrayList<>();
        fullArgs.add(selfArg);
        fullArgs.addAll(userArgs);

        MethodSymbol method = ctx.resolveMethod(owner, mac.memberName, fullArgs);

        if (method == null) {
            ctx.error("No matching overload for method call '"
                    + owner.qualifiedName()
                    + "."
                    + formatCallSignature(mac.memberName, userArgTypes)
                    + "'");

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

    // ============================================================
    // Type Helpers
    // ============================================================

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

    // ============================================================
    // Formatting
    // ============================================================

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