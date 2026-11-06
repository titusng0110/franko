import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ExpressionAnalyzer {

    private final SymbolTable ctx;
    private final ConstExpressionEvaluator constEval;

    private ArrayLowerer arrayLowerer = null;

    private SemanticTypeResolver typeResolver = null;

    private static final SastType FALLBACK_TYPE =
            SastType.primitive(SemanticPrimitiveKind.INT32);

    private static final SastType BOOL_TYPE =
            SastType.primitive(SemanticPrimitiveKind.UINT8);

    private static final SastType NULL_TYPE =
            SastType.nullType();

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

    private ArrayLiteralLowerer arrayLiteralLowerer = null;

    private CompileTimeIntrinsics staticIntrinsics = null;

    void wire(
            ArrayLowerer arrayLowerer,
            SemanticTypeResolver typeResolver,
            ArrayLiteralLowerer arrayLiteralLowerer,
            CompileTimeIntrinsics staticIntrinsics
    ) {
        this.arrayLowerer = Objects.requireNonNull(arrayLowerer);
        this.typeResolver = Objects.requireNonNull(typeResolver);
        this.arrayLiteralLowerer = Objects.requireNonNull(arrayLiteralLowerer);
        this.staticIntrinsics = Objects.requireNonNull(staticIntrinsics);
    }

    public SastNode analyzeExprWithExpectedType(
            AstNode node,
            SastType expectedType
    ) {
        if (node.kind == AstKind.ARRAY_LITERAL) {
            return lowerArrayLiteral(node, expectedType);
        }

        return analyzeValueExpr(node);
    }

    public SastNode analyzeValueExpr(AstNode node) {
        return analyzeExpr(node, true);
    }

    public SastNode analyzeExpr(AstNode node) {
        return analyzeExpr(node, false);
    }

    private SastNode analyzeExpr(
            AstNode node,
            boolean allowAggregateLiteral
    ) {
        ctx.diagnostics().push(node.span);
        try {
            return analyzeExprInner(node, allowAggregateLiteral);
        } finally {
            ctx.diagnostics().pop();
        }
    }

    private SastNode analyzeExprInner(
            AstNode node,
            boolean allowAggregateLiteral
    ) {
        if (node.kind == AstKind.AGGREGATE_LITERAL) {
            AstNode lit = node;
            if (!allowAggregateLiteral) {
                ctx.error("Aggregate literals are only valid as assignment "
                        + "values, return values, call arguments, or aggregate "
                        + "field values");
            }

            return lowerAggregateLiteral(lit);
        }

        if (node.kind == AstKind.ARRAY_LITERAL) {
            return lowerArrayLiteral(node, null);
        }

        if (node.kind == AstKind.INT) {
            AstNode n = node;
            BigInteger val = constEval.parseIntegerLiteralSafe(n.text);
            return SastNode.intLiteral(n.span, FALLBACK_TYPE, val, n.text);
        }

        if (node.kind == AstKind.NULL) {
            return SastNode.nullLiteral(node.span, NULL_TYPE);
        }

        if (node.kind == AstKind.VAR) {
            AstNode n = node;
            Symbol sym = ctx.resolve(n.name);

            if (sym == null) {
                if ("self".equals(n.name)) {
                    ctx.error("keyword 'self' is only valid inside a method body");
                } else if (ctx.hasAnyFunctionNamed(n.name)) {
                    ctx.error("Function name '" + n.name + "' cannot be used as a value");
                } else {
                    ctx.error("Undeclared variable '" + n.name + "'");
                }

                sym = Symbol.variable(null, n.name, FALLBACK_TYPE);
            }

            return SastNode.varExpr(node.span, sym.type, sym);
        }

        if (node.kind == AstKind.QUALIFIED_NAME) {
            return analyzeQualifiedNameExpr(node);
        }

        if (node.kind == AstKind.COMPILE_TIME_CALL) {
            AstNode n = node;
            if (staticIntrinsics == null) {
                ctx.error("Internal compiler error: no compile-time "
                        + "intrinsics wired into ExpressionAnalyzer");
                return SastNode.intLiteral(
                        n.span,
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }

            return staticIntrinsics.analyzeCall(n);
        }

        if (node.kind == AstKind.COMPILE_TIME_NAME) {
            if (staticIntrinsics == null) {
                ctx.error("Internal compiler error: no compile-time "
                        + "intrinsics wired into ExpressionAnalyzer");
                return SastNode.intLiteral(
                        node.span,
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }

            return staticIntrinsics.analyzeName(node);
        }

        if (node.kind == AstKind.UNARY) {
            AstNode n = node;
            SastNode expr = analyzeExpr(n.expr);

            SastType type = n.text.equals("!") ? BOOL_TYPE : expr.type;

            BigInteger folded = constEval.foldUnary(
                    n.text,
                    expr.constantValue
            );

            return SastNode.unaryOp(
                    node.span,
                    type,
                    folded,
                    n.text,
                    expr
            );
        }

        if (node.kind == AstKind.BINARY) {
            AstNode n = node;
            SastNode left = analyzeExpr(n.left);
            SastNode right = analyzeExpr(n.right);

            if (OperatorMethods.isComparisonOperator(n.text)
                    && (left.type.kind == SastTypeKind.STRUCT
                        || right.type.kind == SastTypeKind.STRUCT)) {
                return lowerStructComparison(n.text, left, right, node.span);
            }

            SastType type = inferBinaryResultType(
                    n.text,
                    left,
                    right
            );

            BigInteger folded = constEval.foldBinary(
                    n.text,
                    left.constantValue,
                    right.constantValue
            );

            return SastNode.binOp(
                    node.span,
                    type,
                    folded,
                    n.text,
                    left,
                    right
            );
        }

        if (node.kind == AstKind.ARRAY_ACCESS) {
            AstNode n = node;
            SastNode target = analyzeExpr(n.target);
            SastNode index = analyzeExpr(n.index);

            SastType elemType = extractElementType(target.type);

            return SastNode.arrayAccess(
                    node.span,
                    elemType,
                    target,
                    index
            );
        }

        if (node.kind == AstKind.CALL) {
            return analyzeCallExpr(node);
        }

        if (node.kind == AstKind.MEMBER_ACCESS) {
            AstNode n = node;
            SastNode target = analyzeExpr(n.target);

            if (target.type.kind == SastTypeKind.TUPLE) {
                FieldSymbol field = target.type.resolveField(n.name);

                if (field == null) {
                    ctx.error("Tuple type '"
                            + target.type.describe()
                            + "' has no element named '"
                            + n.name
                            + "'");

                    return SastNode.intLiteral(
                            node.span,
                            FALLBACK_TYPE,
                            BigInteger.ZERO,
                            "0"
                    );
                }

                return SastNode.memberAccess(
                        node.span,
                        field.type,
                        target,
                        field
                );
            }

            if (!(target.type.kind == SastTypeKind.STRUCT)) {
                ctx.error("Member access '"
                        + n.name
                        + "' is only valid on struct or tuple values, got type "
                        + target.type.describe());

                return SastNode.intLiteral(
                        node.span,
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }

            FieldSymbol field = target.type.structSymbol.resolveField(
                    n.name
            );

            if (field == null) {
                ctx.error("Struct '"
                        + target.type.structSymbol.qualifiedName()
                        + "' has no field named '"
                        + n.name
                        + "'");

                return SastNode.intLiteral(
                        node.span,
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }

            return SastNode.memberAccess(
                    node.span,
                    field.type,
                    target,
                    field
            );
        }

        if (node.kind == AstKind.GET_PTR) {
            AstNode n = node;
            String intrinsicName;

            if (n.permission == PtrPermission.OWNER) {
                intrinsicName = "getptr";
            } else if (n.permission == PtrPermission.MUTABLE) {
                intrinsicName = "getmview";
            } else {
                intrinsicName = "getview";
            }

            SastNode target = analyzeRequiredLValueExpr(
                    n.target,
                    intrinsicName + "(...) target"
            );

            return SastNode.getPtr(
                    node.span,
                    pointerTypeFor(n.permission, target.type),
                    target
            );
        }

        if (node.kind == AstKind.DEREF) {
            AstNode n = node;
            SastNode expr = analyzeExpr(n.expr);

            SastType refType = FALLBACK_TYPE;

            if (expr.type.kind == SastTypeKind.PTR) {
                refType = expr.type.referencedType;
            } else if (expr.type.kind == SastTypeKind.NULL) {
                ctx.error("Cannot dereference null");
            } else {
                ctx.error("Cannot dereference non-pointer type");
            }

            return SastNode.deref(node.span, refType, expr);
        }

        if (node.kind == AstKind.CAST) {
            AstNode n = node;
            SastType targetType = resolveAstNode(n.type);
            SastNode expr = analyzeExpr(n.expr);

            String syntax = n.unsafe ? "unsafe_cast" : "cast";

            if (expr.type.kind == SastTypeKind.VOID) {
                ctx.error("Operand of '" + syntax + "' cannot have void type");

                return SastNode.cast(
                        node.span,
                        targetType,
                        null,
                        expr,
                        n.unsafe
                );
            }

            boolean targetIsEnum = targetType.kind == SastTypeKind.ENUM;
            boolean sourceIsEnum = expr.type.kind == SastTypeKind.ENUM;

            if (targetIsEnum || sourceIsEnum) {
                return SastNode.cast(
                        node.span,
                        targetType,
                        null,
                        expr,
                        n.unsafe
                );
            }

            if (n.unsafe
                    && (targetType.kind == SastTypeKind.PRIMITIVE)
                    && !(expr.type.kind == SastTypeKind.PRIMITIVE)) {
                ctx.error("Operand of 'unsafe_cast' must be an integer "
                        + "expression, got " + expr.type.describe());

                return SastNode.cast(
                        node.span,
                        targetType,
                        null,
                        expr,
                        true
                );
            }

            if (targetType.kind == SastTypeKind.PRIMITIVE
                    && expr.type.kind == SastTypeKind.PRIMITIVE) {
                if (expr.type.primitive == targetType.primitive) {
                    return expr;
                }

                return SastNode.cast(
                        node.span,
                        targetType,
                        expr.constantValue,
                        expr,
                        n.unsafe
                );
            }

            return SastNode.cast(
                    node.span,
                    targetType,
                    null,
                    expr,
                    n.unsafe
            );
        }

        if (node.kind == AstKind.STRING_LITERAL) {
            ctx.error("String literal is not a valid expression here; a "
                    + "string literal is a view<[byte, N]> value");

            return SastNode.intLiteral(
                    node.span,
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        ctx.error("Unrecognized expression node: "
                + node.kind.jsonName());

        return SastNode.intLiteral(
                node.span,
                FALLBACK_TYPE,
                BigInteger.ZERO,
                "0"
        );
    }

    private SastNode lowerArrayLiteral(
            AstNode lit,
            SastType expectedType
    ) {
        if (arrayLiteralLowerer == null) {
            ctx.error("Internal compiler error: no array literal lowerer "
                    + "wired into ExpressionAnalyzer");

            return SastNode.intLiteral(
                    lit.span,
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        return arrayLiteralLowerer.lower(lit, expectedType);
    }

    private SastNode lowerAggregateLiteral(AstNode lit) {
        SastType literalType = resolveAstNode(lit.type);

        if (literalType.kind == SastTypeKind.STRUCT) {
            StructSymbol owner = literalType.structSymbol;
            List<SastNode> fields = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            boolean reportedPositional = false;

            for (AstNode init : lit.fields) {
                if (init.positional) {
                    if (!reportedPositional) {
                        reportedPositional = true;
                        ctx.error("Struct literal fields must be named; "
                                + "positional initializers are only allowed for "
                                + "all-unnamed tuple types");
                    }

                    analyzeExprWithExpectedType(init.value, FALLBACK_TYPE);
                    continue;
                }

                FieldSymbol field = owner.resolveField(init.name);

                if (field == null) {
                    ctx.error("Struct '"
                            + owner.qualifiedName()
                            + "' has no field named '"
                            + init.name
                            + "'");
                    analyzeExprWithExpectedType(init.value, FALLBACK_TYPE);
                    continue;
                }

                if (!seen.add(init.name)) {
                    ctx.error("Duplicate initializer for field '"
                            + init.name
                            + "' of struct '"
                            + owner.qualifiedName()
                            + "'");
                    analyzeExprWithExpectedType(init.value, field.type);
                    continue;
                }

                fields.add(SastNode.aggregateFieldInit(
                        field,
                        analyzeExprWithExpectedType(init.value, field.type)
                ));
            }

            return SastNode.aggregateLiteral(
                    lit.span,
                    literalType,
                    fields
            );
        }

        if (literalType.kind == SastTypeKind.TUPLE) {
            requireUniformTupleLiteralNames(lit);

            boolean anyPositional = false;
            boolean anyNamed = false;

            for (AstNode init : lit.fields) {
                if (init.positional) {
                    anyPositional = true;
                } else {
                    anyNamed = true;
                }
            }

            if (anyPositional && anyNamed) {
                ctx.error("Cannot mix positional and named tuple element "
                        + "initializers");
            }

            boolean unnamedLiteral = isAllUnnamedTupleLiteral(lit);
            boolean reportedPositionalNotAllowed = false;
            boolean reportedTooMany = false;
            int position = 0;

            List<SastNode> fields = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (AstNode init : lit.fields) {
                FieldSymbol field;

                if (init.positional) {
                    if (!unnamedLiteral) {
                        if (!reportedPositionalNotAllowed) {
                            reportedPositionalNotAllowed = true;
                            ctx.error("Positional tuple literal fields are "
                                    + "only allowed for an all-unnamed tuple "
                                    + "type");
                        }

                        analyzeExprWithExpectedType(
                                init.value,
                                FALLBACK_TYPE
                        );
                        position++;
                        continue;
                    }

                    if (position >= literalType.fields.size()) {
                        if (!reportedTooMany) {
                            reportedTooMany = true;
                            ctx.error("Positional initializer for tuple type '"
                                    + literalType.describe()
                                    + "' has more entries than the tuple has "
                                    + "elements");
                        }

                        analyzeExprWithExpectedType(
                                init.value,
                                FALLBACK_TYPE
                        );
                        position++;
                        continue;
                    }

                    field = literalType.fields.get(position);
                    position++;
                } else {
                    field = literalType.resolveField(init.name);

                    if (field == null) {
                        ctx.error("Tuple type '"
                                + literalType.describe()
                                + "' has no element named '"
                                + init.name
                                + "'");
                        analyzeExprWithExpectedType(
                                init.value,
                                FALLBACK_TYPE
                        );
                        continue;
                    }
                }

                if (!seen.add(field.name)) {
                    ctx.error("Duplicate initializer for tuple element '"
                            + field.name
                            + "' of tuple type '"
                            + literalType.describe()
                            + "'");
                    analyzeExprWithExpectedType(init.value, field.type);
                    continue;
                }

                fields.add(SastNode.aggregateFieldInit(
                        field,
                        analyzeExprWithExpectedType(init.value, field.type)
                ));
            }

            return SastNode.aggregateLiteral(
                    lit.span,
                    literalType,
                    fields
            );
        }

        ctx.error("Aggregate literal requires a struct or tuple type");

        for (AstNode init : lit.fields) {
            analyzeExprWithExpectedType(init.value, FALLBACK_TYPE);
        }

        return operatorFallbackLiteral(lit.span);
    }

    private void requireUniformTupleLiteralNames(AstNode lit) {
        if (lit.type == null || lit.type.kind != AstKind.TUPLE_TYPE) {
            return;
        }

        AstNode tuple = lit.type;
        int named = 0;

        for (AstNode element : tuple.elements) {
            if (element.name != null) {
                named++;
            }
        }

        if (named != 0 && named != tuple.elements.size()) {
            ctx.error("Tuple literal type must name all elements or no "
                    + "elements; mixed spellings are not allowed");
        }
    }

    private boolean isAllUnnamedTupleLiteral(AstNode lit) {
        if (lit.type == null || lit.type.kind != AstKind.TUPLE_TYPE) {
            return false;
        }

        AstNode tuple = lit.type;

        for (AstNode element : tuple.elements) {
            if (element.name != null) {
                return false;
            }
        }

        return true;
    }

    private SastType resolveAstNode(AstNode node) {
        if (typeResolver == null) {
            ctx.error("Internal compiler error: no type resolver wired into "
                    + "ExpressionAnalyzer");
            return FALLBACK_TYPE;
        }

        return typeResolver.analyzeNormalType(node);
    }

    private static SastType pointerTypeFor(
            PtrPermission kind,
            SastType referencedType
    ) {
        switch (kind) {
            case OWNER: return SastType.ptr(referencedType);
            case MUTABLE: return SastType.mview(referencedType);
            case READ_ONLY: return SastType.view(referencedType);
            default: throw new IllegalStateException("Unknown permission: " + kind);
        }
    }

    public BigInteger preEvaluatedConstValue(
            AstNode expr,
            String roleDescription
    ) {
        if (expr != null && expr.kind == AstKind.INT) {
            BigInteger parsed =
                    ConstExpressionEvaluator.parseIntegerLiteralPure(expr.text);

            if (parsed != null) {
                return parsed;
            }
        }

        ctx.error("Invalid constant expression for " + roleDescription);
        return BigInteger.ZERO;
    }

    public SastNode analyzeRequiredLValueExpr(
            AstNode node,
            String roleDescription
    ) {
        SastNode expr = analyzeExpr(node);
        requireLValue(expr, roleDescription);
        return expr;
    }

    public void requireLValue(
            SastNode expr,
            String roleDescription
    ) {
        if (!isValidStorageBackedLValue(expr)) {
            ctx.error(roleDescription + " must be a storage-backed lvalue");
        }
    }

    public void requireArrayType(
            SastNode expr,
            String roleDescription
    ) {
        if (!isArrayType(expr.type)) {
            ctx.error(roleDescription + " must have array type, got "
                    + expr.type.describe());
        }
    }

    public void requireDynamicArrayType(
            SastNode expr,
            String roleDescription
    ) {
        if (!isDynamicArrayType(expr.type)) {
            ctx.error(roleDescription + " must have dynamic array type, got "
                    + expr.type.describe());
        }
    }

    public boolean isArrayType(SastType type) {
        return type.kind == SastTypeKind.DYNAMIC_ARRAY
                || type.kind == SastTypeKind.STATIC_ARRAY;
    }

    public boolean isDynamicArrayType(SastType type) {
        return type.kind == SastTypeKind.DYNAMIC_ARRAY;
    }

    private boolean isValidStorageBackedLValue(SastNode expr) {
        if (expr == null || !expr.isLValue()) {
            return false;
        }

        if (expr.kind == SastKind.VAR_EXPR) {
            return true;
        }

        if (expr.kind == SastKind.DEREF) {
            return true;
        }

        if (expr.kind == SastKind.ARRAY_ACCESS) {
            return isValidStorageBackedLValue(expr.target);
        }

        return expr.isLValue();
    }

    private SastNode lowerStructComparison(
            String op,
            SastNode left,
            SastNode right,
            SourceSpan span
    ) {
        if (left.type.kind != SastTypeKind.STRUCT
                || right.type.kind != SastTypeKind.STRUCT
                || left.type.structSymbol != right.type.structSymbol) {
            ctx.error("Cannot compare values of type '"
                    + left.type.describe()
                    + "' and type '"
                    + right.type.describe()
                    + "': comparison operators on structs require both "
                    + "operands to have the same struct type");

            return operatorFallbackLiteral(span);
        }

        StructSymbol owner = left.type.structSymbol;
        String methodName = OperatorMethods.comparisonMethodName(op);

        SastNode leftView = makeOperatorView(left, span);
        SastNode rightView = makeOperatorView(right, span);

        Symbol method = ctx.resolveMethodWithLiterals(
                owner,
                methodName,
                List.of(CallArg.lowered(rightView))
        );

        if (method == null) {
            ctx.error("No matching overload for operator '"
                    + op
                    + "' on struct '"
                    + owner.qualifiedName()
                    + "': expected '"
                    + methodName
                    + "(view<"
                    + owner.qualifiedName()
                    + "> self, view<"
                    + owner.qualifiedName()
                    + "> other) -> uint8'");

            return operatorFallbackLiteral(span);
        }

        return SastNode.functionCall(
                span,
                method.returnType(),
                method,
                List.of(leftView, rightView)
        );
    }

    private SastNode makeOperatorView(
            SastNode value,
            SourceSpan span
    ) {
        SastType viewType = SastType.view(value.type);
        boolean temporary = !isValidStorageBackedLValue(value);

        return SastNode.getPtr(span, viewType, value, temporary);
    }

    static final class OperatorAssignmentLowering {
        final SastNode call;
        final SastNode fallbackValue;
        final boolean fallbackNeedsContext;

        private OperatorAssignmentLowering(
                SastNode call,
                SastNode fallbackValue,
                boolean fallbackNeedsContext
        ) {
            this.call = call;
            this.fallbackValue = fallbackValue;
            this.fallbackNeedsContext = fallbackNeedsContext;
        }

        static OperatorAssignmentLowering call(SastNode call) {
            return new OperatorAssignmentLowering(
                    Objects.requireNonNull(call),
                    null,
                    false
            );
        }

        static OperatorAssignmentLowering fallback(SastNode value) {
            return new OperatorAssignmentLowering(
                    null,
                    Objects.requireNonNull(value),
                    false
            );
        }

        static OperatorAssignmentLowering lowerRaw() {
            return new OperatorAssignmentLowering(null, null, true);
        }
    }

    public OperatorAssignmentLowering tryLowerOperatorAssignment(
            SastNode target,
            String op,
            AstNode rawValue,
            SourceSpan span
    ) {
        if (!(target.type.kind == SastTypeKind.STRUCT)) {
            return null;
        }

        String methodName = OperatorMethods.assignmentMethodName(op);

        if (methodName == null) {
            return null;
        }

        StructSymbol owner = target.type.structSymbol;

        if (!owner.hasMethod(methodName)) {
            if ("=".equals(op)) {
                return null;
            }

            ctx.error("Struct '"
                    + owner.qualifiedName()
                    + "' does not define operator '"
                    + op
                    + "' (expected method '"
                    + methodName
                    + "')");

            return OperatorAssignmentLowering.call(
                    operatorFallbackLiteral(span)
            );
        }

        SastNode loweredValue = null;
        CallArg arg;

        if (rawValue != null && rawValue.kind == AstKind.ARRAY_LITERAL) {
            arg = CallArg.operatorLiteral(rawValue);
        } else {
            loweredValue = analyzeValueExpr(rawValue);

            if (loweredValue.type.kind == SastTypeKind.VOID) {
                ctx.error("Right-hand side of operator '"
                        + op
                        + "' cannot have void type");

                return OperatorAssignmentLowering.call(
                        operatorFallbackLiteral(span)
                );
            }

            if (loweredValue.type.kind == SastTypeKind.NULL) {
                if ("=".equals(op)) {
                    return OperatorAssignmentLowering.fallback(
                            loweredValue
                    );
                }

                ctx.error("Struct '"
                        + owner.qualifiedName()
                        + "' cannot apply operator '"
                        + op
                        + "' to null");

                return OperatorAssignmentLowering.call(
                        operatorFallbackLiteral(span)
                );
            }

            arg = CallArg.operatorLowered(loweredValue);
        }

        Symbol method = ctx.resolveOperatorMethodWithLiterals(
                owner,
                methodName,
                arg
        );

        if (method == null) {
            if ("=".equals(op)) {
                return arg.isLiteral()
                        ? OperatorAssignmentLowering.lowerRaw()
                        : OperatorAssignmentLowering.fallback(loweredValue);
            }

            ctx.error("No matching overload for operator '"
                    + op
                    + "' on struct '"
                    + owner.qualifiedName()
                    + "' (expected method '"
                    + methodName
                    + "' with signature '"
                    + methodName
                    + "(ptr<"
                    + owner.qualifiedName()
                    + "> self, view<T> other) -> void')");

            return OperatorAssignmentLowering.call(
                    operatorFallbackLiteral(span)
            );
        }

        SastType parameterType = method.parameters.get(1).type;
        SastNode userArg;

        if (arg.isLiteral()) {
            SastType inner = parameterType.referencedType;

            SastNode literalValue = lowerArrayLiteral(
                    rawValue,
                    inner
            );

            userArg = SastNode.getPtr(
                    span,
                    parameterType,
                    literalValue,
                    true
            );
        } else {
            boolean temporary = !isValidStorageBackedLValue(loweredValue);

            userArg = SastNode.getPtr(
                    span,
                    parameterType,
                    loweredValue,
                    temporary
            );
        }

        SastType selfType = SastType.ptr(target.type);

        if (!method.parameters.isEmpty()
                && method.parameters.get(0).type.kind == SastTypeKind.PTR) {
            selfType = method.parameters.get(0).type;
        }

        SastNode selfArg = SastNode.getPtr(
                target.span,
                selfType,
                target
        );

        SastNode call = SastNode.functionCall(
                span,
                method.returnType(),
                method,
                List.of(selfArg, userArg)
        );

        return OperatorAssignmentLowering.call(call);
    }

    private SastNode operatorFallbackLiteral(SourceSpan span) {
        return SastNode.intLiteral(
                span,
                FALLBACK_TYPE,
                BigInteger.ZERO,
                "0"
        );
    }

    private SastNode analyzeQualifiedNameExpr(AstNode q) {
        SastNode enumVariant = tryAnalyzeEnumVariant(q);

        if (enumVariant != null) {
            return enumVariant;
        }

        List<String> namespaceSegments =
                q.names.subList(0, q.names.size() - 1);
        String name = q.names.get(q.names.size() - 1);
        String qualified = String.join("::", q.names);
        String namespacePath = String.join("::", namespaceSegments);

        NamespaceSymbol namespace = ctx.resolveNamespace(namespaceSegments);

        if (namespace == null) {
            ctx.error("Unknown namespace '" + namespacePath + "'");

            return SastNode.intLiteral(
                    q.span,
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        Symbol sym = ctx.resolveGlobalVarIn(namespace, name);

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

            sym = Symbol.global(namespace.name, name, FALLBACK_TYPE);
        }

        if (sym.kind == SymbolKind.GLOBAL
                && sym.compileTimeConstantValue != null) {
            return SastNode.intLiteral(
                    q.span,
                    sym.type,
                    sym.compileTimeConstantValue,
                    sym.compileTimeConstantValue.toString(10)
            );
        }

        return SastNode.varExpr(q.span, sym.type, sym);
    }

    private SastNode tryAnalyzeEnumVariant(AstNode q) {
        if (q.names.size() < 2) {
            return null;
        }

        List<String> enumPath = q.names.subList(0, q.names.size() - 1);
        String variantName = q.names.get(q.names.size() - 1);
        EnumSymbol enumSymbol = ctx.resolveEnumReference(enumPath);

        if (enumSymbol == null) {
            return null;
        }

        EnumVariant variant = enumSymbol.resolveVariant(variantName);

        if (variant == null) {
            ctx.error("Enum '" + enumSymbol.qualifiedName() + "' has no variant named '" + variantName + "'");

            if (!enumSymbol.variants.isEmpty()) {
                return SastNode.enumVariant(q.span, enumSymbol, enumSymbol.variants.get(0));
            }

            return SastNode.intLiteral(
                    q.span,
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        return SastNode.enumVariant(q.span, enumSymbol, variant);
    }

    private List<CallArg> lowerCallArgs(List<AstNode> rawArgs) {
        List<CallArg> out = new ArrayList<>();

        for (AstNode arg : rawArgs) {
            if (arg.kind == AstKind.ARRAY_LITERAL) {
                out.add(CallArg.literal(arg));
            } else {
                out.add(CallArg.lowered(analyzeValueExpr(arg)));
            }
        }

        return out;
    }

    private List<SastNode> finalizeCallArgs(
            List<CallArg> callArgs,
            List<SastType> parameterTypes
    ) {
        List<SastNode> out = new ArrayList<>();

        for (int i = 0; i < callArgs.size(); i++) {
            CallArg callArg = callArgs.get(i);

            if (!callArg.isLiteral()) {
                out.add(callArg.lowered);
                continue;
            }

            SastType context = parameterTypes != null
                    && i < parameterTypes.size()
                    ? parameterTypes.get(i)
                    : null;

            out.add(lowerArrayLiteral(callArg.literal, context));
        }

        return out;
    }

    private static List<SastType> callArgTypes(List<SastNode> args) {
        List<SastType> out = new ArrayList<>();

        for (SastNode arg : args) {
            out.add(arg == null ? null : arg.type);
        }

        return out;
    }

    private SastNode analyzeCallExpr(AstNode call) {
        if (arrayLowerer != null) {
            SastNode intrinsic =
                    arrayLowerer.tryLowerIntrinsicCall(call);

            if (intrinsic != null) {
                return intrinsic;
            }

            if (arrayLowerer.shouldSuppressCallDiagnostic(call)) {
                return SastNode.intLiteral(
                        call.span,
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }
        }

        if (call.callee.kind == AstKind.VAR) {
            AstNode calleeVar = call.callee;
            List<CallArg> callArgs = lowerCallArgs(call.args);

            Symbol fn = ctx.resolveFunctionWithLiterals(
                    calleeVar.name,
                    callArgs
            );

            List<SastType> parameterTypes =
                    fn == null ? null : fn.parameterTypes();

            List<SastNode> args =
                    finalizeCallArgs(callArgs, parameterTypes);

            if (fn == null) {
                Symbol resolved = ctx.resolveFunction(
                        calleeVar.name,
                        args
                );

                if (resolved == null) {
                    if (ctx.resolve(calleeVar.name) != null
                            && !ctx.hasAnyFunctionNamed(calleeVar.name)) {
                        ctx.error("'" + calleeVar.name + "' is not a function");
                    } else {
                        ctx.error("No matching overload for function call '"
                                + formatCallSignature(
                                        calleeVar.name,
                                        callArgTypes(args))
                                + "'");
                    }
                }

                Symbol fallbackFn = Symbol.function(
                        null,
                        calleeVar.name,
                        List.of(),
                        FALLBACK_TYPE,
                        false
                );

                return SastNode.functionCall(
                        call.span,
                        FALLBACK_TYPE,
                        fallbackFn,
                        args
                );
            }

            return SastNode.functionCall(
                    call.span,
                    fn.returnType(),
                    fn,
                    args
            );
        }

        if (call.callee.kind == AstKind.QUALIFIED_NAME) {
            AstNode calleeQ = call.callee;
            List<CallArg> callArgs = lowerCallArgs(call.args);

            List<String> namespaceSegments = calleeQ.names.subList(
                    0, calleeQ.names.size() - 1);
            String calleeName =
                    calleeQ.names.get(calleeQ.names.size() - 1);
            String qualifiedCallee = String.join("::", calleeQ.names);

            NamespaceSymbol namespace =
                    ctx.resolveNamespace(namespaceSegments);

            if (namespace == null) {
                ctx.error("Unknown namespace '"
                        + String.join("::", namespaceSegments)
                        + "'");

                return SastNode.intLiteral(
                        call.span,
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }

            Symbol fn = ctx.resolveFunctionInWithLiterals(
                    namespace,
                    calleeName,
                    callArgs
            );

            List<SastType> parameterTypes =
                    fn == null ? null : fn.parameterTypes();

            List<SastNode> args =
                    finalizeCallArgs(callArgs, parameterTypes);

            if (fn == null) {
                Symbol resolved = ctx.resolveFunctionIn(
                        namespace,
                        calleeName,
                        args
                );

                if (resolved == null) {
                    ctx.error("No matching overload for function call '"
                            + formatCallSignature(
                                    qualifiedCallee,
                                    callArgTypes(args))
                            + "'");
                }

                Symbol fallbackFn = Symbol.function(
                        namespace.name,
                        calleeName,
                        List.of(),
                        FALLBACK_TYPE,
                        false
                );

                return SastNode.functionCall(
                        call.span,
                        FALLBACK_TYPE,
                        fallbackFn,
                        args
                );
            }

            return SastNode.functionCall(
                    call.span,
                    fn.returnType(),
                    fn,
                    args
            );
        }

        if (call.callee.kind == AstKind.MEMBER_ACCESS) {
            return analyzeMethodCallExpr(call, call.callee);
        }

        ctx.error("Unsupported call expression");

        return SastNode.intLiteral(
                call.span,
                FALLBACK_TYPE,
                BigInteger.ZERO,
                "0"
        );
    }

    private SastNode analyzeMethodCallExpr(
            AstNode call,
            AstNode mac
    ) {
        SastNode target = analyzeExpr(mac.target);

        if (target.type.kind == SastTypeKind.TUPLE) {
            ctx.error("Method call '"
                    + mac.name
                    + "' is only valid on struct values; tuple values have"
                    + " no methods");

            return SastNode.intLiteral(
                    call.span,
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        if (!(target.type.kind == SastTypeKind.STRUCT)
                || target.type.structSymbol == null) {
            if (target.type.kind == SastTypeKind.PTR) {
                ctx.error("Method call '"
                        + mac.name
                        + "' is only valid on struct values, got type "
                        + target.type.describe()
                        + "; use $(p)."
                        + mac.name
                        + "(...) for pointer receivers");
            } else {
                ctx.error("Method call '"
                        + mac.name
                        + "' is only valid on struct values, got type "
                        + target.type.describe());
            }

            return SastNode.intLiteral(
                    call.span,
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        StructSymbol owner = target.type.structSymbol;

        if (!isValidStorageBackedLValue(target)) {
            ctx.error("Method call receiver must be a storage-backed lvalue: "
                    + "a variable, field, array element, or $(...) expression");

            return SastNode.intLiteral(
                    call.span,
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        List<CallArg> callArgs = lowerCallArgs(call.args);

        Symbol method = ctx.resolveMethodWithLiterals(owner, mac.name, callArgs);

        List<SastType> userParameterTypes = null;

        if (method != null) {
            userParameterTypes = new ArrayList<>();

            for (int i = 1; i < method.parameters.size(); i++) {
                userParameterTypes.add(method.parameters.get(i).type);
            }
        }

        List<SastNode> userArgs =
                finalizeCallArgs(callArgs, userParameterTypes);

        if (method == null) {
            List<SastType> userArgTypes = callArgTypes(userArgs);

            Symbol resolved = ctx.resolveMethod(
                    owner,
                    mac.name,
                    userArgs
            );

            if (resolved == null) {
                ctx.error("No matching overload for method call '"
                        + owner.qualifiedName()
                        + "."
                        + formatCallSignature(mac.name, userArgTypes)
                        + "'");
            }
        }

        SastType selfType = SastType.ptr(target.type);

        if (method != null
                && !method.parameters.isEmpty()
                && method.parameters.get(0).type.kind == SastTypeKind.PTR) {
            selfType = method.parameters.get(0).type;
        }

        SastNode selfArg = SastNode.getPtr(target.span, selfType, target);

        List<SastNode> fullArgs = new ArrayList<>();
        fullArgs.add(selfArg);
        fullArgs.addAll(userArgs);

        if (method == null) {
            Symbol fallbackFn = Symbol.method(
                    owner,
                    mac.name,
                    List.of(),
                    FALLBACK_TYPE,
                    false
            );

            return SastNode.functionCall(
                    call.span,
                    FALLBACK_TYPE,
                    fallbackFn,
                    fullArgs
            );
        }

        return SastNode.functionCall(
                call.span,
                method.returnType(),
                method,
                fullArgs
        );
    }

    private SastType inferBinaryResultType(
            String op,
            SastNode left,
            SastNode right
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

    private SastType extractElementType(SastType type) {
        if (type.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return type.elementType;
        }

        if (type.kind == SastTypeKind.STATIC_ARRAY) {
            return type.elementType;
        }

        ctx.error("Indexed expression is not an array");
        return FALLBACK_TYPE;
    }

    private String formatCallSignature(
            String name,
            List<SastType> argTypes
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append(name).append("(");

        for (int i = 0; i < argTypes.size(); i++) {
            if (i > 0) sb.append(", ");

            SastType type = argTypes.get(i);
            sb.append(type == null ? "<null>" : type.describe());
        }

        sb.append(")");

        return sb.toString();
    }
}
