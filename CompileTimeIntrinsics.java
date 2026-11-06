import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class CompileTimeIntrinsics {

    interface StringHoister {
        Symbol hoistStaticString(
                List<BigInteger> bytes,
                SourceSpan span
        );
    }

    private static final SastType BOOL_TYPE =
            SastType.primitive(SemanticPrimitiveKind.UINT8);

    private static final SastType INT32_TYPE =
            SastType.primitive(SemanticPrimitiveKind.INT32);

    private static final SastType VOID_TYPE =
            SemanticTypeResolver.VOID_TYPE;

    private final SymbolTable ctx;
    private final TypeChecker typeChecker;
    private final StringHoister stringHoister;
    private final TargetInfo target;
    private final Function<AstNode, SastType> typeResolver;

    CompileTimeIntrinsics(
            SymbolTable ctx,
            TargetInfo target,
            StringHoister stringHoister,
            Function<AstNode, SastType> typeResolver
    ) {
        this.ctx = ctx;
        this.target = target;
        this.typeChecker = new TypeChecker(ctx.diagnostics());
        this.stringHoister = stringHoister;
        this.typeResolver = typeResolver;
    }

    SemanticExprNode analyzeCall(AstNode call) {
        return materialize(evaluateCall(call.name, call.args, call.span), call.span);
    }

    SemanticExprNode analyzeName(AstNode name) {
        return materialize(evaluateName(name.name, name.span), name.span);
    }

    private StaticValue evaluateCall(
            String name,
            List<AstNode> args,
            SourceSpan span
    ) {
        return switch (name) {
            case "is_primitive" -> evaluateTypePredicate(
                    name, args, type -> type.kind == SastTypeKind.PRIMITIVE);
            case "is_enum" -> evaluateTypePredicate(
                    name, args, type -> type.kind == SastTypeKind.ENUM);
            case "is_memcpy_safe" -> evaluateTypePredicate(
                    name, args, typeChecker::isMemcpySafe);
            case "has_default_value" -> evaluateTypePredicate(
                    name, args, typeChecker::hasDefaultValue);
            case "is_memset_safe" -> evaluateTypePredicate(
                    name, args, typeChecker::isMemsetSafe);
            case "is_same_type" -> evaluateSameType(name, args);
            case "what_type" -> evaluateWhatType(name, args);
            case "strcat" -> evaluateStrcat(name, args);
            case "strcmp" -> evaluateStrcmp(name, args);
            case "panic" -> evaluatePanic(name, args);
            case "is_matchable" -> evaluateTypePredicate(
                    name, args, this::isMatchable);
            case "is_comparable" -> evaluateTypePredicate(
                    name, args, this::isComparable);
            case "is_assignable" -> evaluateTypePredicate(
                    name, args, this::isAssignable);
            case "is_value_assignable" -> evaluateTypePredicate(
                    name, args, typeChecker::isValueAssignableType);
            default -> {
                ctx.error("Unknown compile-time reflection function '#"
                        + name
                        + "'");
                yield null;
            }
        };
    }

    private StaticValue evaluateName(String name, SourceSpan span) {
        Boolean enabled = switch (name) {
            case "IS_LINUX" -> target.isLinux();
            case "IS_WINDOWS" -> target.isWindows();
            case "IS_MACOS" -> target.isMacos();
            case "IS_X86_64" -> target.isX86_64();
            case "IS_ARM64" -> target.isArm64();
            default -> null;
        };

        if (enabled == null) {
            ctx.diagnostics().push(span);
            try {
                ctx.error("Unknown compile-time reflection name '#"
                        + name
                        + "'");
            } finally {
                ctx.diagnostics().pop();
            }
            return null;
        }

        return new StaticIntValue(enabled ? BigInteger.ONE : BigInteger.ZERO,
                BOOL_TYPE);
    }

    private interface TypePredicate {
        boolean test(SastType type);
    }

    private StaticValue evaluateTypePredicate(
            String name,
            List<AstNode> args,
            TypePredicate predicate
    ) {
        if (!requireArity(name, args, 1)) {
            return null;
        }

        SastType type = resolveTypeOrValueArgument(args.get(0), name);

        if (type == null) {
            return null;
        }

        BigInteger folded = predicate.test(type)
                ? BigInteger.ONE
                : BigInteger.ZERO;

        return new StaticIntValue(folded, BOOL_TYPE);
    }

    private StaticValue evaluateSameType(String name, List<AstNode> args) {
        if (!requireArity(name, args, 2)) {
            return null;
        }

        SastType left = resolveTypeOrValueArgument(args.get(0), name);
        SastType right = resolveTypeOrValueArgument(args.get(1), name);

        if (left == null || right == null) {
            return null;
        }

        BigInteger folded = TypeChecker.sameType(left, right)
                ? BigInteger.ONE
                : BigInteger.ZERO;

        return new StaticIntValue(folded, BOOL_TYPE);
    }

    private StaticValue evaluateWhatType(String name, List<AstNode> args) {
        if (!requireArity(name, args, 1)) {
            return null;
        }

        SastType type = resolveTypeOrValueArgument(args.get(0), name);

        if (type == null) {
            return null;
        }

        return new StaticStringValue(utf8Bytes(type.mangleKey()));
    }

    private StaticValue evaluateStrcat(String name, List<AstNode> args) {
        if (args.isEmpty()) {
            ctx.error("#strcat expects at least one compile-time string "
                    + "argument");
            return null;
        }

        List<BigInteger> combined = new ArrayList<>();

        for (int i = 0; i < args.size(); i++) {
            List<BigInteger> part = evaluateStaticStringArgument(
                    args.get(i),
                    name,
                    i + 1
            );

            if (part == null) {
                return null;
            }

            combined.addAll(part);
        }

        return new StaticStringValue(combined);
    }

    private StaticValue evaluateStrcmp(String name, List<AstNode> args) {
        if (!requireArity(name, args, 2)) {
            return null;
        }

        List<BigInteger> left = evaluateStaticStringArgument(
                args.get(0),
                name,
                1
        );
        List<BigInteger> right = evaluateStaticStringArgument(
                args.get(1),
                name,
                2
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

    private StaticValue evaluatePanic(String name, List<AstNode> args) {
        if (!requireArity(name, args, 1)) {
            return null;
        }

        List<BigInteger> message = evaluateStaticStringArgument(
                args.get(0),
                name,
                1
        );

        if (message == null) {
            return null;
        }

        ctx.error(decodeText(message));

        return new StaticVoidValue();
    }

    private List<BigInteger> evaluateStaticStringArgument(
            AstNode arg,
            String consumerName,
            int position
    ) {
        List<BigInteger> bytes;

        if (arg.kind == AstKind.STRING_LITERAL) {
            bytes = decodeStringBytes(arg);
        } else if (arg.kind == AstKind.COMPILE_TIME_CALL) {
            AstNode call = arg;
            StaticValue value = evaluateCall(call.name, call.args, call.span);

            if (value == null || value instanceof StaticVoidValue) {
                return null;
            }

            if (!(value instanceof StaticStringValue string)) {
                ctx.error("#"
                        + consumerName
                        + " argument "
                        + position
                        + " must be a compile-time string, got "
                        + describeStaticValue(value));
                return null;
            }

            bytes = string.bytes();
        } else {
            ctx.error("#"
                    + consumerName
                    + " argument "
                    + position
                    + " must be a compile-time string, got "
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
        Symbol global = stringHoister.hoistStaticString(
                bytes,
                span
        );

        SastType arrayType = SastType.staticArray(
                SastType.primitive(SemanticPrimitiveKind.UINT8),
                Integer.toString(bytes.size())
        );

        SemanticExprNode target = new SemanticVarExprNode(
                span,
                arrayType,
                global
        );

        return new SemanticGetPtrNode(
                span,
                SastType.view(arrayType),
                target
        );
    }

    private boolean requireArity(
            String name,
            List<AstNode> args,
            int expected
    ) {
        if (args.size() == expected) {
            return true;
        }

        ctx.error("#"
                + name
                + " expects exactly "
                + expected
                + " argument"
                + (expected == 1 ? "" : "s")
                + ", got "
                + args.size());

        return false;
    }

    private SastType resolveTypeOrValueArgument(AstNode arg, String name) {
        if (arg.kind == AstKind.VAR) {
            Symbol symbol = ctx.resolve(arg.name);

            if (symbol == null) {
                ctx.error("#"
                        + name
                        + " argument '"
                        + arg.name
                        + "' is not a declared variable or type");
                return null;
            }

            return symbol.type;
        }

        if (arg.kind == AstKind.QUALIFIED_NAME) {
            return resolveQualifiedValue(arg, name);
        }

        if (arg.kind == AstKind.STRUCT_TYPE) {
            return resolveStructOrValue(arg, name);
        }

        if (arg.isType()) {
            return typeResolver.apply(arg);
        }

        ctx.error("#"
                + name
                + " argument must be a variable identifier or a type, got "
                + describeArgumentKind(arg));

        return null;
    }

    private SastType resolveStructOrValue(
            AstNode struct,
            String name
    ) {
        if (struct.names == null
                || struct.names.isEmpty()) {
            Symbol variable = ctx.resolve(struct.name);

            if (variable != null) {
                return variable.type;
            }

            StructSymbol symbol = ctx.resolveStruct(struct.name);

            if (symbol != null) {
                return SastType.structType(symbol);
            }

            EnumSymbol enumSymbol = ctx.resolveEnum(struct.name);

            if (enumSymbol != null) {
                return SastType.enumType(enumSymbol);
            }

            ctx.error("#"
                    + name
                    + " argument '"
                    + struct.name
                    + "' is not a declared variable or type");
            return null;
        }

        List<String> fullPath = new ArrayList<>(struct.names);
        fullPath.add(struct.name);
        SastType variantType = tryResolveEnumVariantType(fullPath);

        if (variantType != null) {
            return variantType;
        }

        NamespaceSymbol namespace =
                ctx.resolveNamespace(struct.names);

        if (namespace == null) {
            ctx.error("Unknown namespace '"
                    + String.join("::", struct.names)
                    + "'");
            return null;
        }

        Symbol variable = ctx.resolveGlobalVarIn(namespace, struct.name);

        if (variable != null) {
            return variable.type;
        }

        StructSymbol symbol = ctx.resolveStructIn(namespace, struct.name);

        if (symbol != null) {
            return SastType.structType(symbol);
        }

        EnumSymbol enumSymbol = ctx.resolveEnumIn(namespace, struct.name);

        if (enumSymbol != null) {
            return SastType.enumType(enumSymbol);
        }

        ctx.error("#"
                + name
                + " argument '"
                + String.join("::", struct.names)
                + "::"
                + struct.name
                + "' is not a declared variable or type");
        return null;
    }

    private SastType resolveQualifiedValue(
            AstNode qualified,
            String name
    ) {
        SastType variantType = tryResolveEnumVariantType(qualified.names);

        if (variantType != null) {
            return variantType;
        }

        List<String> namespaceSegments = qualified.names.subList(
                0,
                qualified.names.size() - 1
        );
        String variableName =
                qualified.names.get(qualified.names.size() - 1);

        NamespaceSymbol namespace = ctx.resolveNamespace(namespaceSegments);

        if (namespace == null) {
            ctx.error("Unknown namespace '"
                    + String.join("::", namespaceSegments)
                    + "'");
            return null;
        }

        Symbol variable = ctx.resolveGlobalVarIn(namespace, variableName);

        if (variable != null) {
            return variable.type;
        }

        StructSymbol symbol = ctx.resolveStructIn(namespace, variableName);

        if (symbol != null) {
            return SastType.structType(symbol);
        }

        EnumSymbol enumSymbol = ctx.resolveEnumReference(
                new ArrayList<>(qualified.names.subList(0, qualified.names.size() - 1)));

        if (enumSymbol != null) {
            EnumVariant variant = enumSymbol.resolveVariant(variableName);

            if (variant != null) {
                return SastType.enumType(enumSymbol);
            }
        }

        ctx.error("#"
                + name
                + " argument '"
                + String.join("::", qualified.names)
                + "' is not a declared variable or type");
        return null;
    }

    private SastType tryResolveEnumVariantType(List<String> segments) {
        if (segments.size() < 2) {
            return null;
        }

        List<String> enumPath = segments.subList(0, segments.size() - 1);
        String variantName = segments.get(segments.size() - 1);
        EnumSymbol enumSymbol = ctx.resolveEnumReference(new ArrayList<>(enumPath));

        if (enumSymbol == null) {
            return null;
        }

        if (enumSymbol.resolveVariant(variantName) == null) {
            return null;
        }

        return SastType.enumType(enumSymbol);
    }

    private List<BigInteger> decodeStringBytes(AstNode literal) {
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

    private boolean isMatchable(SastType type) {
        if (type.kind == SastTypeKind.PRIMITIVE
                || type.kind == SastTypeKind.PTR
                || type.kind == SastTypeKind.ENUM) {
            return true;
        }

        if (type.kind == SastTypeKind.STRUCT) {
            return hasComparisonMethod(type.structSymbol, "OPERATOR_EQ");
        }

        return false;
    }

    private boolean isComparable(SastType type) {
        if (type.kind == SastTypeKind.PRIMITIVE
                || type.kind == SastTypeKind.ENUM) {
            return true;
        }

        if (type.kind == SastTypeKind.STRUCT) {
            return hasComparisonMethod(type.structSymbol, "OPERATOR_LT");
        }

        return false;
    }

    private boolean isAssignable(SastType type) {
        if (type.kind == SastTypeKind.PRIMITIVE
                || type.kind == SastTypeKind.PTR
                || type.kind == SastTypeKind.ENUM) {
            return true;
        }

        if (type.kind == SastTypeKind.TUPLE) {
            return typeChecker.isValueAssignableType(type);
        }

        if (type.kind == SastTypeKind.STRUCT) {
            return typeChecker.isValueAssignableType(type)
                    || hasSameTypeMoveMethod(type.structSymbol);
        }

        return false;
    }

    private static boolean hasComparisonMethod(
            StructSymbol owner,
            String methodName
    ) {
        SastType structType = SastType.structType(owner);

        for (Symbol method : owner.getMethods(methodName)) {
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
        SastType structType = SastType.structType(owner);

        for (Symbol method : owner.getMethods("OPERATOR_MOV")) {
            if (method.parameters.size() != 2) {
                continue;
            }

            if (!isOwnerPointerTo(method.parameters.get(0).type, structType)
                    || !isReadOnlyViewOf(
                            method.parameters.get(1).type,
                            structType)) {
                continue;
            }

            if (method.returnType().kind == SastTypeKind.VOID) {
                return true;
            }
        }

        return false;
    }

    private static boolean isReadOnlyViewOf(
            SastType type,
            SastType referencedType
    ) {
        return type.kind == SastTypeKind.PTR
                && type.permission == PtrPermission.READ_ONLY
                && type.referencedType.equals(referencedType);
    }

    private static boolean isOwnerPointerTo(
            SastType type,
            SastType referencedType
    ) {
        return type.kind == SastTypeKind.PTR
                && type.permission == PtrPermission.OWNER
                && type.referencedType.equals(referencedType);
    }

    private static boolean isUint8(SastType type) {
        return type.kind == SastTypeKind.PRIMITIVE
                && type.primitive == SemanticPrimitiveKind.UINT8;
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

    private static String describeArgumentKind(AstNode arg) {
        if (arg.kind == AstKind.STRING_LITERAL) {
            return "a string literal";
        }

        if (arg.kind == AstKind.INT) {
            return "an integer literal";
        }

        if (arg.kind == AstKind.ARRAY_LITERAL) {
            return "an array literal";
        }

        if (arg.kind == AstKind.MEMBER_ACCESS) {
            return "a field access";
        }

        if (arg.kind == AstKind.ARRAY_ACCESS) {
            return "an array element";
        }

        if (arg.kind == AstKind.CALL) {
            return "a function call";
        }

        if (arg.kind == AstKind.COMPILE_TIME_CALL) {
            return "a compile-time call";
        }

        if (arg.kind == AstKind.UNARY || arg.kind == AstKind.BINARY) {
            return "an arithmetic expression";
        }

        if (arg.kind == AstKind.NULL) {
            return "null";
        }

        return "an expression";
    }

    private sealed interface StaticValue
            permits StaticIntValue, StaticStringValue, StaticVoidValue {
    }

    private record StaticIntValue(
            BigInteger value,
            SastType type
    ) implements StaticValue {
    }

    private record StaticStringValue(
            List<BigInteger> bytes
    ) implements StaticValue {
    }

    private record StaticVoidValue() implements StaticValue {
    }
}
