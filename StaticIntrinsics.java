import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class StaticIntrinsics {

    interface StringHoister {
        GlobalVariableSymbol hoistStaticString(
                List<BigInteger> bytes,
                SourceSpan span
        );
    }

    private static final SemanticType BOOL_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8);

    private static final SemanticType INT32_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.INT32);

    private static final SemanticType VOID_TYPE =
            SemanticTypeResolver.VOID_TYPE;

    private final SymbolTable ctx;
    private final TypeChecker typeChecker;
    private final StringHoister stringHoister;

    StaticIntrinsics(
            SymbolTable ctx,
            StringHoister stringHoister
    ) {
        this.ctx = ctx;
        this.typeChecker = new TypeChecker(ctx.diagnostics());
        this.stringHoister = stringHoister;
    }

    static boolean isStaticCallee(QualifiedNameNode callee) {
        return callee.segments.size() == 2
                && "static".equals(callee.segments.get(0));
    }

    private sealed interface StaticValue
            permits StaticIntValue, StaticStringValue, StaticVoidValue {
    }

    private record StaticIntValue(
            BigInteger value,
            SemanticType type
    ) implements StaticValue {
    }

    private record StaticStringValue(
            List<BigInteger> bytes
    ) implements StaticValue {
    }

    private record StaticVoidValue() implements StaticValue {
    }

    SemanticExprNode analyzeCall(
            CallNode call,
            QualifiedNameNode callee
    ) {
        return materialize(evaluateStaticCall(call, callee), call.span);
    }

    private StaticValue evaluateStaticCall(
            CallNode call,
            QualifiedNameNode callee
    ) {
        String name = callee.segments.get(1);

        return switch (name) {
            case "is_primitive" -> evaluateTypePredicate(
                    call, name, type -> type instanceof SemanticPrimitiveType);
            case "is_memcpy_safe" -> evaluateTypePredicate(
                    call, name, typeChecker::isMemcpySafe);
            case "is_trivial" -> evaluateTypePredicate(
                    call, name, typeChecker::isStaticTrivial);
            case "is_memset_safe" -> evaluateTypePredicate(
                    call, name, typeChecker::isMemsetSafe);
            case "is_same_type" -> evaluateSameType(call, name);
            case "what_type" -> evaluateWhatType(call, name);
            case "strcmp" -> evaluateStrcmp(call, name);
            case "panic" -> evaluatePanic(call, name);
            case "is_matchable" -> evaluateTypePredicate(
                    call, name, this::isMatchable);
            case "is_comparable" -> evaluateTypePredicate(
                    call, name, this::isComparable);
            case "is_same_type_assignable" -> evaluateTypePredicate(
                    call, name, this::isSameTypeAssignable);
            case "is_passable_by_value" -> evaluateTypePredicate(
                    call, name, typeChecker::isAssignableValueType);
            default -> {
                ctx.error("Unknown compile-time reflection function 'static::"
                        + name
                        + "'");
                yield null;
            }
        };
    }

    private interface TypePredicate {
        boolean test(SemanticType type);
    }

    private StaticValue evaluateTypePredicate(
            CallNode call,
            String name,
            TypePredicate predicate
    ) {
        if (!requireArity(call, name, 1)) {
            return null;
        }

        SemanticType type = resolveIdentifierArgument(call.args.get(0), name);

        if (type == null) {
            return null;
        }

        BigInteger folded = predicate.test(type)
                ? BigInteger.ONE
                : BigInteger.ZERO;

        return new StaticIntValue(folded, BOOL_TYPE);
    }

    private StaticValue evaluateSameType(CallNode call, String name) {
        if (!requireArity(call, name, 2)) {
            return null;
        }

        SemanticType left = resolveIdentifierArgument(call.args.get(0), name);
        SemanticType right = resolveIdentifierArgument(call.args.get(1), name);

        if (left == null || right == null) {
            return null;
        }

        BigInteger folded = TypeChecker.sameType(left, right)
                ? BigInteger.ONE
                : BigInteger.ZERO;

        return new StaticIntValue(folded, BOOL_TYPE);
    }

    private StaticValue evaluateWhatType(CallNode call, String name) {
        if (!requireArity(call, name, 1)) {
            return null;
        }

        SemanticType type = resolveIdentifierArgument(call.args.get(0), name);

        if (type == null) {
            return null;
        }

        return new StaticStringValue(utf8Bytes(type.describe()));
    }

    private StaticValue evaluateStrcmp(CallNode call, String name) {
        if (!requireArity(call, name, 2)) {
            return null;
        }

        List<BigInteger> left = evaluateStaticStringArgument(
                call.args.get(0),
                name
        );
        List<BigInteger> right = evaluateStaticStringArgument(
                call.args.get(1),
                name
        );

        if (left == null || right == null) {
            return null;
        }

        int comparison = compareBytes(left, right);

        return new StaticIntValue(
                BigInteger.valueOf(comparison),
                INT32_TYPE
        );
    }

    private StaticValue evaluatePanic(CallNode call, String name) {
        if (!requireArity(call, name, 1)) {
            return null;
        }

        List<BigInteger> message = evaluateStaticStringArgument(
                call.args.get(0),
                name
        );

        if (message == null) {
            return null;
        }

        ctx.error(decodeText(message));

        return new StaticVoidValue();
    }

    private List<BigInteger> evaluateStaticStringArgument(
            ASTNode arg,
            String consumerName
    ) {
        List<BigInteger> bytes;

        if (arg instanceof StringLiteralNode literal) {
            bytes = decodeStringBytes(literal);
        } else if (arg instanceof CallNode call
                && call.callee instanceof QualifiedNameNode callee
                && isStaticCallee(callee)) {
            StaticValue value = evaluateStaticCall(call, callee);

            if (value == null || value instanceof StaticVoidValue) {
                return null;
            }

            if (!(value instanceof StaticStringValue string)) {
                ctx.error("static::"
                        + consumerName
                        + " argument must be a compile-time string literal,"
                        + " got "
                        + describeStaticValue(value));
                return null;
            }

            bytes = string.bytes();
        } else {
            ctx.error("static::"
                    + consumerName
                    + " argument must be a compile-time string literal, got "
                    + describeArgumentKind(arg));
            return null;
        }

        if (bytes == null) {
            return null;
        }

        if (bytes.isEmpty()) {
            ctx.error("Empty string literals are not supported: "
                    + "a string literal is a view<[byte, N]> value with "
                    + "N >= 1");
            return null;
        }

        return bytes;
    }

    private SemanticExprNode materialize(
            StaticValue value,
            SourceSpan span
    ) {
        if (value instanceof StaticIntValue integer) {
            return new SemanticIntLiteralNode(
                    span,
                    integer.type(),
                    integer.value(),
                    integer.value().toString(10)
            );
        }

        if (value instanceof StaticStringValue string) {
            return materializeStaticString(string.bytes(), span);
        }

        if (value instanceof StaticVoidValue) {
            return new SemanticIntLiteralNode(
                    span,
                    VOID_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        return fallback(span);
    }

    private SemanticExprNode fallback(SourceSpan span) {
        return new SemanticIntLiteralNode(
                span,
                INT32_TYPE,
                BigInteger.ZERO,
                "0"
        );
    }

    private SemanticExprNode materializeStaticString(
            List<BigInteger> bytes,
            SourceSpan span
    ) {
        GlobalVariableSymbol global = stringHoister.hoistStaticString(
                bytes,
                span
        );

        SemanticStaticArrayType arrayType = new SemanticStaticArrayType(
                new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8),
                Integer.toString(bytes.size())
        );

        SemanticExprNode target = new SemanticVarExprNode(
                span,
                arrayType,
                global
        );

        return new SemanticGetPtrNode(
                span,
                new SemanticViewType(arrayType),
                target
        );
    }

    private boolean requireArity(CallNode call, String name, int expected) {
        if (call.args.size() == expected) {
            return true;
        }

        ctx.error("static::"
                + name
                + " expects exactly "
                + expected
                + " argument"
                + (expected == 1 ? "" : "s")
                + ", got "
                + call.args.size());

        return false;
    }

    private SemanticType resolveIdentifierArgument(ASTNode arg, String name) {
        if (arg instanceof VarNode var) {
            VariableSymbol symbol = ctx.resolve(var.name);

            if (symbol == null) {
                ctx.error("static::"
                        + name
                        + " argument '"
                        + var.name
                        + "' is not a declared variable");
                return null;
            }

            return symbol.type;
        }

        if (arg instanceof QualifiedNameNode qualified) {
            List<String> namespaceSegments = qualified.segments.subList(
                    0,
                    qualified.segments.size() - 1
            );
            String variableName =
                    qualified.segments.get(qualified.segments.size() - 1);

            NamespaceSymbol namespace = ctx.resolveNamespace(namespaceSegments);

            if (namespace == null) {
                if (namespaceSegments.size() == 1
                        && "static".equals(namespaceSegments.get(0))) {
                    ctx.error("Unknown namespace 'static'; import "
                            + "\"franko/static\" to use compile-time "
                            + "reflection");
                } else {
                    ctx.error("Unknown namespace '"
                            + String.join("::", namespaceSegments)
                            + "'");
                }

                return null;
            }

            VariableSymbol symbol = ctx.resolveGlobalVarIn(
                    namespace,
                    variableName
            );

            if (symbol == null) {
                ctx.error("static::"
                        + name
                        + " argument '"
                        + String.join("::", qualified.segments)
                        + "' is not a declared variable");
                return null;
            }

            return symbol.type;
        }

        ctx.error("static::"
                + name
                + " argument must be a variable identifier"
                + " (a local, parameter, self, or qualified global), got "
                + describeArgumentKind(arg));

        return null;
    }

    private List<BigInteger> decodeStringBytes(StringLiteralNode literal) {
        try {
            return StringLiteralDecoder.toUtf8Bytes(literal);
        } catch (RuntimeException e) {
            ctx.error(e.getMessage());
            return null;
        }
    }

    private static String describeStaticValue(StaticValue value) {
        if (value instanceof StaticIntValue) {
            return "a compile-time integer result";
        }

        if (value instanceof StaticStringValue) {
            return "a compile-time string result";
        }

        if (value instanceof StaticVoidValue) {
            return "a compile-time void result";
        }

        return "a compile-time value";
    }

    private boolean isMatchable(SemanticType type) {
        if (type instanceof SemanticPrimitiveType
                || type instanceof SemanticPtrType) {
            return true;
        }

        if (type instanceof SemanticStructType struct) {
            return hasComparisonMethod(struct.structSymbol, "OPERATOR_EQ");
        }

        return false;
    }

    private boolean isComparable(SemanticType type) {
        if (type instanceof SemanticPrimitiveType) {
            return true;
        }

        if (type instanceof SemanticStructType struct) {
            return hasComparisonMethod(struct.structSymbol, "OPERATOR_LT");
        }

        return false;
    }

    private boolean isSameTypeAssignable(SemanticType type) {
        if (type instanceof SemanticPrimitiveType
                || type instanceof SemanticPtrType) {
            return true;
        }

        if (type instanceof SemanticStructType struct) {
            return typeChecker.isAssignableValueType(type)
                    || hasSameTypeMoveMethod(struct.structSymbol);
        }

        return false;
    }

    private static boolean hasComparisonMethod(
            StructSymbol owner,
            String methodName
    ) {
        SemanticType structType = new SemanticStructType(owner);

        for (MethodSymbol method : owner.getMethods(methodName)) {
            if (method.parameters.size() != 2) {
                continue;
            }

            if (!isReadOnlyViewOf(method.parameters.get(0).type, structType)
                    || !isReadOnlyViewOf(
                            method.parameters.get(1).type,
                            structType)) {
                continue;
            }

            if (isUint8(method.returnType())) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasSameTypeMoveMethod(StructSymbol owner) {
        SemanticType structType = new SemanticStructType(owner);

        for (MethodSymbol method : owner.getMethods("OPERATOR_MOV")) {
            if (method.parameters.size() != 2) {
                continue;
            }

            if (!isOwnerPointerTo(method.parameters.get(0).type, structType)
                    || !isReadOnlyViewOf(
                            method.parameters.get(1).type,
                            structType)) {
                continue;
            }

            if (method.returnType() instanceof SemanticVoidType) {
                return true;
            }
        }

        return false;
    }

    private static boolean isReadOnlyViewOf(
            SemanticType type,
            SemanticType referencedType
    ) {
        return type instanceof SemanticPtrType pointer
                && pointer.permission == PtrPermission.READ_ONLY
                && pointer.referencedType.equals(referencedType);
    }

    private static boolean isOwnerPointerTo(
            SemanticType type,
            SemanticType referencedType
    ) {
        return type instanceof SemanticPtrType pointer
                && pointer.permission == PtrPermission.OWNER
                && pointer.referencedType.equals(referencedType);
    }

    private static boolean isUint8(SemanticType type) {
        return type instanceof SemanticPrimitiveType primitive
                && primitive.kind == SemanticPrimitiveKind.UINT8;
    }

    private static int compareBytes(
            List<BigInteger> left,
            List<BigInteger> right
    ) {
        int common = Math.min(left.size(), right.size());

        for (int i = 0; i < common; i++) {
            int comparison = left.get(i).compareTo(right.get(i));

            if (comparison != 0) {
                return comparison < 0 ? -1 : 1;
            }
        }

        return Integer.compare(left.size(), right.size());
    }

    private static List<BigInteger> utf8Bytes(String text) {
        List<BigInteger> bytes = new ArrayList<>();

        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            bytes.add(BigInteger.valueOf(b & 0xFF));
        }

        return bytes;
    }

    private static String decodeText(List<BigInteger> bytes) {
        byte[] raw = new byte[bytes.size()];

        for (int i = 0; i < bytes.size(); i++) {
            raw[i] = bytes.get(i).byteValue();
        }

        return new String(raw, StandardCharsets.UTF_8);
    }

    private static String describeArgumentKind(ASTNode arg) {
        if (arg instanceof StringLiteralNode) {
            return "a string literal";
        }

        if (arg instanceof IntNode) {
            return "an integer literal";
        }

        if (arg instanceof ArrayLiteralNode) {
            return "an array literal";
        }

        if (arg instanceof MemberAccessNode) {
            return "a field access";
        }

        if (arg instanceof ArrayAccessNode) {
            return "an array element";
        }

        if (arg instanceof CallNode) {
            return "a function call";
        }

        if (arg instanceof UnaryOpNode || arg instanceof BinOpNode) {
            return "an arithmetic expression";
        }

        if (arg instanceof NullNode) {
            return "null";
        }

        return "an expression";
    }

}
