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

        return adoptExpectedConstant(analyzeValueExpr(node), expectedType);
    }

    private SastNode adoptExpectedConstant(
            SastNode expr,
            SastType expectedType
    ) {
        if (expr == null
                || expectedType == null
                || expectedType.kind != SastTypeKind.PRIMITIVE
                || !expr.untypedConstant
                || !expr.isConstant()
                || expr.constantValue == null) {
            return expr;
        }

        if (!TypeChecker.fitsBigIntegerToPrimitive(
                expr.constantValue,
                expectedType.primitive)) {
            return expr;
        }

        expr.type = expectedType;
        expr.untypedConstant = false;
        return expr;
    }

    private SastType defaultConstantType(BigInteger value) {
        SemanticPrimitiveKind kind =
                TypeChecker.defaultConstantPrimitive(value);

        if (kind == null) {
            ctx.error("Integer constant "
                    + value
                    + " does not fit any primitive integer type. Franko integer storage is limited to the eight fixed-width integer types, so no context can represent this value. Reduce the value or rewrite the computation so it stays within a primitive range.");
            return FALLBACK_TYPE;
        }

        return SastType.primitive(kind);
    }

    public SastNode analyzeValueExpr(AstNode node) {
        return analyzeExpr(node, true, true);
    }

    public SastNode analyzeExpr(AstNode node) {
        return analyzeExpr(node, false, false);
    }

    public void analyzeCompileTimeDirective(AstNode call) {
        ctx.diagnostics().push(call.span);
        try {
            if (staticIntrinsics == null) {
                ctx.error("Internal compiler error: no compile-time "
                        + "intrinsics wired into ExpressionAnalyzer");
                return;
            }

            staticIntrinsics.analyzeDirective(call);
        } finally {
            ctx.diagnostics().pop();
        }
    }

    private SastNode analyzeExpr(
            AstNode node,
            boolean allowAggregateLiteral,
            boolean allowStringMaterialization
    ) {
        ctx.diagnostics().push(node.span);
        try {
            return analyzeExprInner(
                    node,
                    allowAggregateLiteral,
                    allowStringMaterialization
            );
        } finally {
            ctx.diagnostics().pop();
        }
    }

    private SastNode analyzeExprInner(
            AstNode node,
            boolean allowAggregateLiteral,
            boolean allowStringMaterialization
    ) {
        if (node.kind == AstKind.AGGREGATE_LITERAL) {
            AstNode lit = node;
            if (!allowAggregateLiteral) {
                ctx.error("Aggregate literals are only valid as assignment "
                        + "values, return values, call arguments, or aggregate "
                        + "field values. Aggregate construction needs a "
                        + "destination that fixes the struct or tuple type. "
                        + "Move the literal into one of those positions, or "
                        + "bind it to a local variable first.");
            }

            return lowerAggregateLiteral(lit);
        }

        if (node.kind == AstKind.ARRAY_LITERAL) {
            return lowerArrayLiteral(node, null);
        }

        if (node.kind == AstKind.INT) {
            AstNode n = node;
            BigInteger val = constEval.parseIntegerLiteralSafe(n.text);
            boolean explicitType = n.literalPrimitive != null;
            SastType literalType = explicitType
                    ? SastType.primitive(
                            SemanticPrimitiveKind.valueOf(
                                    n.literalPrimitive.name()))
                    : defaultConstantType(val);
            SastNode literal =
                    SastNode.intLiteral(n.span, literalType, val, n.text);
            literal.untypedConstant = !explicitType;
            return literal;
        }

        if (node.kind == AstKind.NULL) {
            return SastNode.nullLiteral(node.span, NULL_TYPE);
        }

        if (node.kind == AstKind.VAR) {
            AstNode n = node;
            Symbol sym = ctx.resolve(n.name);

            if (sym == null) {
                if ("self".equals(n.name)) {
                    ctx.error("keyword 'self' is only valid inside a method "
                            + "body. The compiler binds 'self' to the implicit "
                            + "receiver only while checking a struct method. "
                            + "Move this use into a method, or replace it with "
                            + "an explicit pointer variable.");
                } else if (ctx.hasAnyFunctionNamed(n.name)) {
                    ctx.error("Function name '" + n.name + "' cannot be used "
                            + "as a value. Franko has no first-class function "
                            + "values, so a function name only names a call "
                            + "target. Call the function with its argument list, "
                            + "or store the call result in a variable.");
                } else {
                    ctx.error("Undeclared variable '" + n.name + "'. Every "
                            + "variable must be declared before it is used, "
                            + "and name lookup does not search enclosing "
                            + "namespaces. Declare the variable in an enclosing "
                            + "scope, or correct its spelling and namespace "
                            + "path.");
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

            return staticIntrinsics.analyzeCall(n, allowStringMaterialization);
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
            BigInteger folded = constEval.foldUnary(
                    n.text,
                    expr.constantValue
            );

            boolean untyped = !n.text.equals("!")
                    && expr.untypedConstant
                    && folded != null;
            SastType type = n.text.equals("!")
                    ? BOOL_TYPE
                    : untyped
                            ? defaultConstantType(folded)
                            : expr.type;

            SastNode unary = SastNode.unaryOp(
                    node.span,
                    type,
                    folded,
                    n.text,
                    expr
            );
            unary.untypedConstant = untyped;
            return unary;
        }

        if (node.kind == AstKind.BINARY) {
            AstNode n = node;
            boolean leftString = StringLiteralFolder.isStringExpression(n.left);
            boolean rightString = StringLiteralFolder.isStringExpression(n.right);

            if (leftString || rightString) {
                if (!StringConstantOps.isStringOperator(n.text)) {
                    ctx.error("Operator '"
                            + n.text
                            + "' is not defined for compile-time string "
                            + "expressions. A string literal has no type; "
                            + "compile-time strings support only '+' "
                            + "concatenation and the ==, !=, <, >, <=, and >= "
                            + "comparisons, which fold at compile time. Use one "
                            + "of those operators, or materialize the literal "
                            + "as a byte view first.");
                    return fallbackInt(node.span);
                }

                if (!leftString || !rightString) {
                    if (StringConstantOps.isComparison(n.text)) {
                        ctx.error("Cannot compare a compile-time string "
                                + "expression with a runtime value. A string "
                                + "literal has no type, so both operands of a "
                                + "string comparison must be compile-time "
                                + "strings; the comparison then folds to 1 or "
                                + "0. Assign the literal to a byte view first "
                                + "if you intended pointer equality.");
                    } else {
                        ctx.error("Cannot combine a compile-time string "
                                + "expression with a runtime value using '"
                                + n.text
                                + "'. A string literal has no type and folds "
                                + "only with another compile-time string. "
                                + "Assign or pass the literal to a byte view "
                                + "first, or use an explicit operation on that "
                                + "typed value.");
                    }

                    return fallbackInt(node.span);
                }

                List<BigInteger> leftBytes = evaluateStringExpression(n.left);
                List<BigInteger> rightBytes = evaluateStringExpression(n.right);

                if (leftBytes == null || rightBytes == null) {
                    return fallbackInt(node.span);
                }

                if ("+".equals(n.text)) {
                    if (!allowStringMaterialization) {
                        reportStringConstantWithoutType(node.span);
                        return fallbackInt(node.span);
                    }

                    return staticIntrinsics.materializeStringConstant(
                            StringConstantOps.concat(leftBytes, rightBytes),
                            node.span
                    );
                }

                BigInteger folded = StringConstantOps.foldComparison(
                        n.text,
                        leftBytes,
                        rightBytes
                );

                return SastNode.intLiteral(
                        node.span,
                        BOOL_TYPE,
                        folded,
                        folded.toString(10)
                );
            }

            SastNode left = analyzeExpr(n.left);
            SastNode right = analyzeExpr(n.right);

            if (OperatorMethods.isComparisonOperator(n.text)
                    && (left.type.kind == SastTypeKind.STRUCT
                        || right.type.kind == SastTypeKind.STRUCT)) {
                return lowerStructComparison(n.text, left, right, node.span);
            }

            BigInteger folded = constEval.foldBinary(
                    n.text,
                    left.constantValue,
                    right.constantValue
            );

            SastType type = inferBinaryResultType(
                    n.text,
                    left,
                    right,
                    folded
            );

            SastNode binary = SastNode.binOp(
                    node.span,
                    type,
                    folded,
                    n.text,
                    left,
                    right
            );
            binary.untypedConstant = isUntypedBinaryConstant(
                    n.text,
                    left,
                    right,
                    folded
            );
            return binary;
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
                            + "'. A tuple exposes only the element names "
                            + "written in its type, with unnamed elements named "
                            + "_0, _1, and so on. Use a name declared in the "
                            + "tuple type; unnamed elements use their _N "
                            + "names.");

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
                        + target.type.describe()
                        + ". The dot operator resolves a declared field or "
                        + "element, and primitive, pointer, array, and enum "
                        + "types expose no members. Use a struct or tuple "
                        + "value; for a pointer, dereference it with $(...) "
                        + "first.");

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
                        + "'. Struct fields are fixed by the declaration, and "
                        + "member access resolves only those declared names. "
                        + "Use a declared field name, or add the field to "
                        + "struct '"
                        + target.type.structSymbol.qualifiedName()
                        + "'.");

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
                ctx.error("Cannot dereference null. Null is the empty pointer "
                        + "value and refers to no storage, so there is no "
                        + "pointee to read or write. Guard the pointer with a "
                        + "null check and dereference it only on the non-null "
                        + "path, or initialize it from live storage with "
                        + "getptr/getview/getmview.");
            } else {
                ctx.error("Cannot dereference non-pointer type. The $ "
                        + "operator reads the pointee of ptr, mview, or view "
                        + "storage and has no meaning for ordinary values. "
                        + "Create a pointer with getptr/getview/getmview, or "
                        + "remove the $ and use the value directly.");
            }

            return SastNode.deref(node.span, refType, expr);
        }

        if (node.kind == AstKind.CAST) {
            AstNode n = node;
            SastType targetType = resolveAstNode(n.type);

            if (StringLiteralFolder.isStringExpression(n.expr)) {
                ctx.error("A string literal or compile-time string expression "
                        + "has no type and cannot be cast. A string literal "
                        + "materializes only as an assignment value, a function "
                        + "argument, or a return value. Assign or pass it to a "
                        + "byte view first; casts apply to typed values, not to "
                        + "compile-time string constants.");
                return fallbackInt(node.span);
            }

            SastNode expr = analyzeExpr(n.expr);

            if (targetType.kind == SastTypeKind.PRIMITIVE) {
                expr = adoptExpectedConstant(expr, targetType);
            }

            String syntax = n.unsafe ? "unsafe_cast" : "cast";

            if (expr.type.kind == SastTypeKind.VOID) {
                ctx.error("Operand of '" + syntax + "' cannot have void type. "
                        + "A void call produces no value, so there is nothing "
                        + "to convert. Remove the cast, or call a function that "
                        + "returns a value and cast that result.");

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
                        + "expression, got " + expr.type.describe()
                        + ". unsafe_cast performs a checked integer conversion "
                        + "and does not apply to pointers, structs, tuples, or "
                        + "arrays. Pass an integer value, or use cast<...> for "
                        + "a pointer permission downgrade.");

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
            if (!allowStringMaterialization) {
                reportStringConstantWithoutType(node.span);
                return fallbackInt(node.span);
            }

            List<BigInteger> bytes;

            try {
                bytes = StringLiteralDecoder.toUtf8Bytes(node);
            } catch (RuntimeException e) {
                ctx.error(e.getMessage());
                return fallbackInt(node.span);
            }

            return staticIntrinsics.materializeStringConstant(
                    bytes,
                    node.span
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
                                + "all-unnamed tuple types. Struct fields are "
                                + "identified by name and may omit defaulted "
                                + "fields, so position alone cannot identify "
                                + "which field an entry initializes. Write each "
                                + "entry as 'field = value' using a field "
                                + "declared by the struct.");
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
                            + "'. Struct fields are fixed by the declaration, "
                            + "and member access resolves only those declared "
                            + "names. Use a declared field name, or add the "
                            + "field to struct '"
                            + owner.qualifiedName()
                            + "'.");
                    analyzeExprWithExpectedType(init.value, FALLBACK_TYPE);
                    continue;
                }

                if (!seen.add(init.name)) {
                    ctx.error("Duplicate initializer for field '"
                            + init.name
                            + "' of struct '"
                            + owner.qualifiedName()
                            + "'. Each field may be initialized at most once, "
                            + "otherwise the final field value would be "
                            + "ambiguous. Remove or merge the duplicate "
                            + "initializer.");
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
                        + "initializers. Positional entries fill elements in "
                        + "order while named entries select by name, so a "
                        + "mixed literal has no single rule for locating its "
                        + "elements. Use only positional entries in order, or "
                        + "name every entry.");
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
                                    + "type. A named tuple spelling defines "
                                    + "element names, so its literal is "
                                    + "name-based and does not accept "
                                    + "positional slots. Name every entry in "
                                    + "the literal, or spell the tuple type "
                                    + "without element names.");
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
                                    + "elements. A positional entry fills "
                                    + "exactly one element slot, and the tuple "
                                    + "type fixes the number of slots. Remove "
                                    + "the extra entries, or use a tuple type "
                                    + "with more elements.");
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
                                + "'. A tuple exposes only the element names "
                                + "declared in its type, with unnamed elements "
                                + "named _0, _1, and so on. Use a name declared "
                                + "in the tuple type; unnamed elements use "
                                + "their _N names.");
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
                            + "'. Each element may be initialized at most "
                            + "once, and repeated names would make the final "
                            + "value ambiguous. Remove or merge the duplicate "
                            + "initializer.");
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

        ctx.error("Aggregate literal requires a struct or tuple type. Only "
                + "structs and tuples have named or positional fields that a "
                + "braced initializer can fill. Spell a struct or tuple type "
                + "before the braces, or use an array literal for array "
                + "values.");

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
                    + "elements; mixed spellings are not allowed. A tuple "
                    + "literal resolves entries by name only when every "
                    + "element is named, and by position only for the "
                    + "all-unnamed form. Name every element in the tuple type, "
                    + "or remove all element names.");
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

        ctx.error("Invalid constant expression for " + roleDescription
                + ". This position requires a compile-time integer constant "
                + "so its value can be fixed before code generation. Replace "
                + "the expression with an integer literal or a constant "
                + "expression built from literals and folded # intrinsics.");
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
            ctx.error(roleDescription + " must be a storage-backed lvalue. "
                    + "Franko can take a pointer or assign only to a variable, "
                    + "field, array element, or $(...) chain that names real "
                    + "storage. Pass such an lvalue, or store the value in a "
                    + "local variable and pass that variable instead.");
        }
    }

    public void requireArrayType(
            SastNode expr,
            String roleDescription
    ) {
        if (!isArrayType(expr.type)) {
            ctx.error(roleDescription + " must have array type, got "
                    + expr.type.describe()
                    + ". This operation reads element slots from a static "
                    + "[T, N] or dynamic [T] array and has no meaning for "
                    + "other value types. Pass a static or dynamic array value "
                    + "instead.");
        }
    }

    public void requireDynamicArrayType(
            SastNode expr,
            String roleDescription
    ) {
        if (!isDynamicArrayType(expr.type)) {
            ctx.error(roleDescription + " must have dynamic array type, got "
                    + expr.type.describe()
                    + ". Resizing and uninit apply only to dynamic arrays, "
                    + "whose storage can be allocated and released at runtime. "
                    + "Declare the value as [T] instead of a static array, or "
                    + "use a static-array operation.");
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
                    + "operands to have the same struct type. Struct "
                    + "comparison dispatches to the struct's OPERATOR_* "
                    + "method, which is declared per nominal struct type. "
                    + "Compare values of the same struct type, or compare "
                    + "their individual fields.");

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
                    + "> other) -> uint8'. Comparison operators on structs "
                    + "are never built in; each one must be declared as the "
                    + "matching OPERATOR_* method with that exact "
                    + "view-receiver signature. Declare the expected method "
                    + "on the struct, or compare its fields directly.");

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
        Symbol storage = target.storageSymbol();

        if (storage != null && storage.isConst) {
            return null;
        }

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
                    + "'). Franko has no built-in compound assignment for "
                    + "structs; the operator must map to a declared OPERATOR_* "
                    + "method. Declare method '"
                    + methodName
                    + "' on struct '"
                    + owner.qualifiedName()
                    + "', or use the equivalent explicit method call.");

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
                        + "' cannot have void type. A void call produces no "
                        + "value for the operator method to receive. Pass a "
                        + "value-producing expression, or remove the void "
                        + "call.");

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
                        + "' to null. Null is a pointer value, and the "
                        + "operator method expects a value view of a non-null "
                        + "operand. Pass a struct value of type '"
                        + owner.qualifiedName()
                        + "' instead, or handle null before the operator.");

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
                    + "> self, view<T> other) -> void'). Compound assignment "
                    + "on structs dispatches to the matching OPERATOR_*MOV "
                    + "method, and the argument must convert to its view<T> "
                    + "parameter. Declare the expected method, or pass an "
                    + "argument whose type matches that parameter.");

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
            ctx.error("Unknown namespace '" + namespacePath + "'. Qualified "
                    + "names resolve strictly from the first path segment as "
                    + "a top-level namespace, with no fallback to enclosing "
                    + "namespaces. Declare the namespace, correct the path, or "
                    + "import the file that declares it.");

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
                        + "' cannot be used as a value. Franko has no "
                        + "first-class function values, so a qualified "
                        + "function name only names a call target. Call the "
                        + "function with its argument list, or store the call "
                        + "result in a variable.");
            } else {
                ctx.error("Undeclared variable '"
                        + qualified
                        + "'. Every variable must be declared before it is "
                        + "used, and qualified names resolve only through an "
                        + "existing top-level namespace path. Declare the "
                        + "variable, or correct its spelling and namespace "
                        + "path.");
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
            ctx.error("Enum '" + enumSymbol.qualifiedName()
                    + "' has no variant named '" + variantName
                    + "'. Variants are fixed by the enum declaration and are "
                    + "reached as 'Enum::variant'. Use a declared variant "
                    + "name, or add the variant to enum '"
                    + enumSymbol.qualifiedName()
                    + "'.");

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

    private List<CallArg> lowerCallArgs(
            List<AstNode> rawArgs,
            List<String> argumentNames
    ) {
        List<CallArg> out = new ArrayList<>();

        for (int i = 0; i < rawArgs.size(); i++) {
            AstNode arg = rawArgs.get(i);
            String name = argumentNames != null && i < argumentNames.size()
                    ? argumentNames.get(i)
                    : "";

            if (arg.kind == AstKind.ARRAY_LITERAL) {
                out.add(CallArg.literal(arg, name));
            } else {
                out.add(CallArg.lowered(analyzeValueExpr(arg), name));
            }
        }

        return out;
    }

    private List<SastNode> finalizeCallArgs(
            List<CallArg> callArgs,
            Symbol fn,
            boolean method
    ) {
        if (fn == null) {
            return finalizePositionalCallArgs(callArgs);
        }

        int[] argAt = OverloadResolution.bindArguments(fn, callArgs, method);

        if (argAt == null) {
            return finalizePositionalCallArgs(callArgs);
        }

        List<SastNode> out = new ArrayList<>();
        int start = method ? 1 : 0;

        for (int p = start; p < fn.parameters.size(); p++) {
            int argIndex = argAt[p];
            Symbol parameter = fn.parameters.get(p);

            if (argIndex >= 0) {
                out.add(finalizeCallArg(
                        callArgs.get(argIndex),
                        parameter.type
                ));
                continue;
            }

            SastNode defaultValue = parameter.parameterDefault == null
                    ? null
                    : parameter.parameterDefault.copyStaticDefault();

            if (defaultValue == null) {
                ctx.error("Internal compiler error: parameter '"
                        + parameter.name
                        + "' of '"
                        + fn.qualifiedName()
                        + "' has no resolved default value");
                defaultValue = SastNode.intLiteral(
                        SourceSpan.NONE,
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }

            out.add(defaultValue);
        }

        return out;
    }

    private List<SastNode> finalizePositionalCallArgs(
            List<CallArg> callArgs
    ) {
        List<SastNode> out = new ArrayList<>();

        for (CallArg callArg : callArgs) {
            if (!callArg.isLiteral()) {
                out.add(callArg.lowered);
                continue;
            }

            out.add(lowerArrayLiteral(callArg.literal, null));
        }

        return out;
    }

    private SastNode finalizeCallArg(
            CallArg callArg,
            SastType parameterType
    ) {
        if (!callArg.isLiteral()) {
            return adoptExpectedConstant(callArg.lowered, parameterType);
        }

        return lowerArrayLiteral(callArg.literal, parameterType);
    }

    private boolean validateCallShape(
            String displayName,
            List<CallArg> args,
            boolean method
    ) {
        boolean valid = true;
        boolean namedSeen = false;
        Set<String> names = new HashSet<>();

        for (CallArg arg : args) {
            if (arg == null) {
                valid = false;
                continue;
            }

            if (!arg.isNamed()) {
                if (namedSeen) {
                    ctx.error("Positional arguments must appear before named"
                            + " arguments in call to '" + displayName
                            + "'. The compiler binds positional arguments by "
                            + "position and named arguments by parameter name, "
                            + "so a positional argument after a named one has "
                            + "no stable slot. Move all positional arguments to "
                            + "the front, or name every argument.");
                    valid = false;
                }

                continue;
            }

            namedSeen = true;

            if (method && "self".equals(arg.name)) {
                ctx.error("Named argument 'self' is not valid in a method"
                        + " call; self is implicit. The receiver is passed "
                        + "automatically as the method's first argument, and "
                        + "self is never a user argument. Remove the 'self = "
                        + "...' argument and call the method on the receiver.");
                valid = false;
            }

            if (!names.add(arg.name)) {
                ctx.error("Named argument '" + arg.name + "' is specified"
                        + " more than once in call to '" + displayName
                        + "'. Each parameter can receive at most one argument, "
                        + "and duplicate names would make the selected value "
                        + "ambiguous. Remove the duplicate named argument.");
                valid = false;
            }
        }

        return valid;
    }

    private boolean reportCallBindingProblem(
            String displayName,
            List<Symbol> overloads,
            List<CallArg> args,
            boolean method
    ) {
        if (overloads == null || overloads.size() != 1) {
            return false;
        }

        Symbol overload = overloads.get(0);
        boolean relevant = false;

        for (CallArg arg : args) {
            if (arg != null && arg.isNamed()) {
                relevant = true;
                break;
            }
        }

        if (!relevant) {
            for (Symbol parameter : overload.parameters) {
                if (parameter.parameterDefault != null) {
                    relevant = true;
                    break;
                }
            }
        }

        if (!relevant) {
            return false;
        }

        String problem = OverloadResolution.describeBindingProblem(
                displayName,
                overload,
                args,
                method
        );

        if (problem == null) {
            return false;
        }

        ctx.error(problem);
        return true;
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
            List<CallArg> callArgs = lowerCallArgs(
                    call.args,
                    call.argumentNames
            );
            boolean shapeValid = validateCallShape(
                    calleeVar.name,
                    callArgs,
                    false
            );

            Symbol fn = ctx.resolveFunctionWithLiterals(
                    calleeVar.name,
                    callArgs,
                    call.genericGroup
            );

            List<SastNode> args = finalizeCallArgs(callArgs, fn, false);

            if (fn == null) {
                boolean explained = !shapeValid;

                if (ctx.resolve(calleeVar.name) != null
                        && !ctx.hasAnyFunctionNamed(calleeVar.name)) {
                    ctx.error("'" + calleeVar.name + "' is not a function. "
                            + "The name resolves to a variable, and Franko has "
                            + "no first-class function values, so variables "
                            + "cannot be called. Call a declared function or "
                            + "method by name, or remove the argument list to "
                            + "use the variable.");
                    explained = true;
                } else if (shapeValid) {
                    explained = reportCallBindingProblem(
                            calleeVar.name,
                            ctx.functionOverloadsNamed(
                                    calleeVar.name,
                                    call.genericGroup),
                            callArgs,
                            false
                    );
                }

                if (!explained) {
                    ctx.error("No matching overload for function call '"
                            + formatCallSignature(
                                    calleeVar.name,
                                    callArgTypes(args))
                            + "'. The argument types must match a declared "
                            + "overload, with only the documented constant "
                            + "fitting and pointer conversions. Pass arguments "
                            + "matching one declared signature, or add an "
                            + "overload that accepts these types.");
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
            List<CallArg> callArgs = lowerCallArgs(
                    call.args,
                    call.argumentNames
            );

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
                        + "'. Qualified names resolve strictly from the first "
                        + "path segment as a top-level namespace, with no "
                        + "fallback to enclosing namespaces. Declare the "
                        + "namespace, correct the path, or import the file "
                        + "that declares it.");

                return SastNode.intLiteral(
                        call.span,
                        FALLBACK_TYPE,
                        BigInteger.ZERO,
                        "0"
                );
            }

            boolean shapeValid = validateCallShape(
                    qualifiedCallee,
                    callArgs,
                    false
            );

            Symbol fn = ctx.resolveFunctionInWithLiterals(
                    namespace,
                    calleeName,
                    callArgs,
                    call.genericGroup
            );

            List<SastNode> args = finalizeCallArgs(callArgs, fn, false);

            if (fn == null) {
                boolean explained = !shapeValid;

                if (shapeValid) {
                    explained = reportCallBindingProblem(
                            qualifiedCallee,
                            ctx.functionOverloadsNamedIn(
                                    namespace,
                                    calleeName,
                                    call.genericGroup
                            ),
                            callArgs,
                            false
                    );
                }

                if (!explained) {
                    ctx.error("No matching overload for function call '"
                            + formatCallSignature(
                                    qualifiedCallee,
                                    callArgTypes(args))
                            + "'. The argument types must match a declared "
                            + "overload, with only the documented constant "
                            + "fitting and pointer conversions. Pass arguments "
                            + "matching one declared signature, or add an "
                            + "overload that accepts these types.");
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

        ctx.error("Unsupported call expression. Franko calls must name a "
                + "function, a namespace-qualified function, or a struct "
                + "method directly. Rewrite the call to use a declared "
                + "function or method name.");

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
                    + " no methods. Methods belong to struct declarations, "
                    + "and tuples are anonymous aggregates without a method "
                    + "table. Call a free function on the tuple, or wrap the "
                    + "data in a struct that declares the method.");

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
                        + "(...) for pointer receivers. Franko has no "
                        + "implicit auto-deref for method calls. Dereference "
                        + "the pointer with $(...) and call the method on the "
                        + "resulting struct value.");
            } else {
                ctx.error("Method call '"
                        + mac.name
                        + "' is only valid on struct values, got type "
                        + target.type.describe()
                        + ". Methods are declared inside struct bodies and "
                        + "are dispatched on a struct receiver. Use a "
                        + "struct-typed receiver, or call a free function for "
                        + "this value.");
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
                    + "a variable, field, array element, or $(...) expression. "
                    + "The receiver is passed to the method as a self pointer, "
                    + "which requires stable storage to point at. Store the "
                    + "receiver in a local variable, or use an existing "
                    + "variable, field, array element, or $(...) chain.");

            return SastNode.intLiteral(
                    call.span,
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        List<CallArg> callArgs = lowerCallArgs(
                call.args,
                call.argumentNames
        );

        String displayName = owner.qualifiedName() + "." + mac.name;

        if (OperatorMethods.isOperatorMethodName(mac.name)
                && call.hasNamedArguments()) {
            ctx.error("Named arguments are not supported for operator method"
                    + " calls. Operator methods are fixed-shape and are "
                    + "invoked through operator syntax, which has no argument "
                    + "names. Pass the operand positionally, because operator "
                    + "methods reject named arguments even when called "
                    + "explicitly.");
        }

        boolean shapeValid = validateCallShape(
                displayName,
                callArgs,
                true
        );

        Symbol method = ctx.resolveMethodWithLiterals(
                owner,
                mac.name,
                callArgs,
                call.genericGroup
        );

        List<SastNode> userArgs = finalizeCallArgs(
                callArgs,
                method,
                true
        );

        if (method == null) {
            List<SastType> userArgTypes = callArgTypes(userArgs);
            boolean explained = !shapeValid;

            if (shapeValid) {
                explained = reportCallBindingProblem(
                        displayName,
                        ctx.methodOverloadsNamed(
                                owner,
                                mac.name,
                                call.genericGroup
                        ),
                        callArgs,
                        true
                );
            }

            if (!explained) {
                ctx.error("No matching overload for method call '"
                        + owner.qualifiedName()
                        + "."
                        + formatCallSignature(mac.name, userArgTypes)
                        + "'. The receiver's struct must declare a method "
                        + "with this name whose user parameter types match "
                        + "the call. Pass arguments matching a declared "
                        + "method, or declare the missing overload on struct "
                        + "'"
                        + owner.qualifiedName()
                        + "'.");
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

    private SastNode fallbackInt(SourceSpan span) {
        return SastNode.intLiteral(
                span,
                FALLBACK_TYPE,
                BigInteger.ZERO,
                "0"
        );
    }

    private List<BigInteger> evaluateStringExpression(AstNode expr) {
        if (staticIntrinsics == null) {
            ctx.error("Internal compiler error: no compile-time intrinsics "
                    + "wired into ExpressionAnalyzer");
            return null;
        }

        return staticIntrinsics.evaluateStringExpression(expr);
    }

    private void reportStringConstantWithoutType(SourceSpan span) {
        ctx.error("A string literal has no type and cannot be used here. It "
                + "materializes only as an assignment value, a function "
                + "argument, or a return value. Use it in one of those "
                + "positions, or fold it with another compile-time string "
                + "using '+' or a comparison. A string literal cannot be "
                + "dereferenced, indexed, accessed as a member, cast, or used "
                + "in a condition.");
    }

    private SastType inferBinaryResultType(
            String op,
            SastNode left,
            SastNode right,
            BigInteger folded
    ) {
        if (BOOL_RESULT_OPS.contains(op)) {
            return BOOL_TYPE;
        }

        if (NON_BOOL_INTEGER_RESULT_OPS.contains(op)) {
            boolean leftConst = left != null && left.isConstant();
            boolean rightConst = right != null && right.isConstant();

            if (leftConst && rightConst) {
                if (left.untypedConstant && right.untypedConstant) {
                    return folded == null
                            ? left.type
                            : defaultConstantType(folded);
                }

                if (left.untypedConstant) {
                    return right.type;
                }

                if (right.untypedConstant) {
                    return left.type;
                }

                return left.type;
            }

            if (leftConst) return right.type;
            if (rightConst) return left.type;

            return left.type;
        }

        return left.type;
    }

    private boolean isUntypedBinaryConstant(
            String op,
            SastNode left,
            SastNode right,
            BigInteger folded
    ) {
        return !BOOL_RESULT_OPS.contains(op)
                && folded != null
                && left != null
                && right != null
                && left.untypedConstant
                && right.untypedConstant;
    }

    private SastType extractElementType(SastType type) {
        if (type.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return type.elementType;
        }

        if (type.kind == SastTypeKind.STATIC_ARRAY) {
            return type.elementType;
        }

        ctx.error("Indexed expression is not an array. The [] subscript "
                + "reads an element slot from a static or dynamic array and "
                + "has no meaning for other types. Index an array value, or "
                + "use a pointer dereference with $ for pointer-backed "
                + "storage.");
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
