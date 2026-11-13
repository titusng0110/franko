import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class CompileTimeIntrinsicCore {

    enum Predicate {
        IS_PRIMITIVE,
        IS_ENUM,
        IS_MEMCPY_SAFE,
        HAS_DEFAULT_VALUE,
        IS_MEMSET_SAFE,
        IS_MATCHABLE,
        IS_COMPARABLE,
        IS_ASSIGNABLE,
        IS_VALUE_ASSIGNABLE
    }

    enum RawKind {
        INT,
        STRING,
        VOID
    }

    static final class RawValue {
        final RawKind kind;
        final BigInteger value;
        final List<BigInteger> bytes;

        private RawValue(RawKind kind, BigInteger value, List<BigInteger> bytes) {
            this.kind = kind;
            this.value = value;
            this.bytes = bytes;
        }

        static RawValue integer(BigInteger value) {
            return new RawValue(RawKind.INT, value, List.of());
        }

        static RawValue string(List<BigInteger> bytes) {
            return new RawValue(RawKind.STRING, null, bytes);
        }

        static RawValue voidValue() {
            return new RawValue(RawKind.VOID, null, List.of());
        }
    }

    private CompileTimeIntrinsicCore() {
    }

    static Boolean targetValue(String name, TargetInfo target) {
        if ("IS_LINUX".equals(name)) {
            return target.isLinux();
        }

        if ("IS_WINDOWS".equals(name)) {
            return target.isWindows();
        }

        if ("IS_MACOS".equals(name)) {
            return target.isMacos();
        }

        if ("IS_X86_64".equals(name)) {
            return target.isX86_64();
        }

        if ("IS_ARM64".equals(name)) {
            return target.isArm64();
        }

        return null;
    }

    static PrimitiveKind integerResultKind(String name) {
        return "strcmp".equals(name) ? PrimitiveKind.INT32 : PrimitiveKind.UINT8;
    }

    static BigInteger tryEvaluateInt(AstNode expr, TargetInfo target) {
        RawValue value = tryEvaluate(expr, target);

        if (value == null || value.kind != RawKind.INT) {
            return null;
        }

        return value.value;
    }

    static RawValue tryEvaluate(AstNode expr, TargetInfo target) {
        if (expr == null) {
            return null;
        }

        if (expr.kind == AstKind.INT) {
            BigInteger parsed = ConstExpressionEvaluator.parseIntegerLiteralPure(
                    expr.text
            );

            return parsed == null ? null : RawValue.integer(parsed);
        }

        if (expr.kind == AstKind.UNARY) {
            BigInteger value = tryEvaluateInt(expr.expr, target);

            if (value == null) {
                return null;
            }

            BigInteger folded = ConstExpressionEvaluator.tryFoldUnary(
                    expr.text,
                    value
            );

            return folded == null ? null : RawValue.integer(folded);
        }

        if (expr.kind == AstKind.BINARY) {
            BigInteger left = tryEvaluateInt(expr.left, target);
            BigInteger right = tryEvaluateInt(expr.right, target);

            if (left == null || right == null) {
                return null;
            }

            if (("/".equals(expr.text) || "%".equals(expr.text))
                    && right.signum() == 0) {
                return null;
            }

            BigInteger folded = ConstExpressionEvaluator.tryFoldBinary(
                    expr.text,
                    left,
                    right
            );

            return folded == null ? null : RawValue.integer(folded);
        }

        if (expr.kind == AstKind.COMPILE_TIME_NAME) {
            Boolean enabled = targetValue(expr.name, target);

            return enabled == null
                    ? null
                    : RawValue.integer(enabled
                            ? BigInteger.ONE
                            : BigInteger.ZERO);
        }

        if (expr.kind == AstKind.COMPILE_TIME_CALL) {
            return tryEvaluateCall(expr, target);
        }

        return null;
    }

    static RawValue tryEvaluateCall(AstNode call, TargetInfo target) {
        String name = call.name;
        List<AstNode> args = call.args;

        if ("panic".equals(name) || "warning".equals(name)) {
            return null;
        }

        Predicate predicate = predicateFor(name);

        if (predicate != null) {
            if (args.size() != 1) {
                return null;
            }

            String key = rawTypeKey(args.get(0));

            if (key == null) {
                return null;
            }

            Boolean result = testRawPredicate(predicate, key);

            if (result == null) {
                return null;
            }

            return RawValue.integer(result ? BigInteger.ONE : BigInteger.ZERO);
        }

        if ("is_same_type".equals(name)) {
            if (args.size() != 2) {
                return null;
            }

            String left = rawTypeKey(args.get(0));
            String right = rawTypeKey(args.get(1));

            if (left == null || right == null) {
                return null;
            }

            return RawValue.integer(left.equals(right)
                    ? BigInteger.ONE
                    : BigInteger.ZERO);
        }

        if ("what_type".equals(name)) {
            if (args.size() != 1) {
                return null;
            }

            String key = rawTypeKey(args.get(0));

            if (key == null) {
                return null;
            }

            return RawValue.string(utf8Bytes(key));
        }

        if ("strcat".equals(name)) {
            List<BigInteger> combined = new ArrayList<>();

            for (AstNode arg : args) {
                List<BigInteger> part = tryStaticString(arg, target);

                if (part == null) {
                    return null;
                }

                combined.addAll(part);
            }

            if (combined.isEmpty()) {
                return null;
            }

            return RawValue.string(combined);
        }

        if ("strcmp".equals(name)) {
            if (args.size() != 2) {
                return null;
            }

            List<BigInteger> left = tryStaticString(args.get(0), target);
            List<BigInteger> right = tryStaticString(args.get(1), target);

            if (left == null || right == null) {
                return null;
            }

            return RawValue.integer(BigInteger.valueOf(compareBytes(left, right)));
        }

        return null;
    }

    static List<BigInteger> tryStaticString(AstNode arg, TargetInfo target) {
        if (arg == null) {
            return null;
        }

        if (arg.kind == AstKind.STRING_LITERAL) {
            try {
                return StringLiteralDecoder.toUtf8Bytes(arg);
            } catch (RuntimeException e) {
                return null;
            }
        }

        if (arg.kind == AstKind.COMPILE_TIME_CALL) {
            RawValue value = tryEvaluateCall(arg, target);

            if (value == null || value.kind != RawKind.STRING) {
                return null;
            }

            return value.bytes;
        }

        return null;
    }

    static String rawTypeKey(AstNode arg) {
        if (arg == null) {
            return null;
        }

        AstNode type = arg;

        if (arg.kind == AstKind.GENERIC_ARG) {
            if (!arg.typeParameter) {
                return null;
            }

            type = arg.type;
        }

        if (type.kind == AstKind.STRUCT_TYPE) {
            return null;
        }

        if (type.kind == AstKind.PRIMITIVE_TYPE) {
            return type.primitive.name().toLowerCase();
        }

        if (type.kind == AstKind.PTR_TYPE) {
            String inner = rawTypeKey(type.type);

            if (inner == null) {
                return null;
            }

            return type.permission.name() + "<" + inner + ">";
        }

        if (type.kind == AstKind.DYNAMIC_ARRAY_TYPE) {
            String inner = rawTypeKey(type.type);

            return inner == null ? null : "[" + inner + "]";
        }

        if (type.kind == AstKind.STATIC_ARRAY_TYPE) {
            String inner = rawTypeKey(type.type);
            BigInteger size = ConstExpressionEvaluator.tryFold(type.sizeExpr);

            if (inner == null || size == null) {
                return null;
            }

            return "[" + inner + "," + size.toString(10) + "]";
        }

        if (type.kind == AstKind.TUPLE_TYPE) {
            List<String> parts = new ArrayList<>();

            for (AstNode element : type.elements) {
                String part = rawTypeKey(element.type);

                if (part == null) {
                    return null;
                }

                parts.add(part);
            }

            return "(" + String.join(",", parts) + ")";
        }

        return null;
    }

    static Boolean testRawPredicate(Predicate predicate, String key) {
        boolean array = key.startsWith("[");
        boolean tuple = key.startsWith("(");
        boolean pointer = key.contains("<") && !array && !tuple
                && (key.startsWith("OWNER<")
                        || key.startsWith("MUTABLE<")
                        || key.startsWith("READ_ONLY<"));
        boolean primitive = !array && !tuple && !pointer && key.indexOf('<') < 0;

        switch (predicate) {
            case IS_PRIMITIVE:
                return primitive;
            case IS_ENUM:
                return primitive ? Boolean.FALSE : null;
            case IS_MATCHABLE:
                if (primitive || pointer) {
                    return true;
                }
                return array || tuple ? Boolean.FALSE : null;
            case IS_COMPARABLE:
                if (primitive) {
                    return true;
                }
                return pointer || array || tuple ? Boolean.FALSE : null;
            case IS_ASSIGNABLE:
            case IS_VALUE_ASSIGNABLE:
                if (primitive || pointer) {
                    return true;
                }
                if (array) {
                    return false;
                }
                if (tuple) {
                    return tupleAll(key, predicate);
                }
                return null;
            case IS_MEMCPY_SAFE:
                if (primitive || pointer) {
                    return true;
                }
                if (array) {
                    String inner = arrayInner(key);
                    return inner != null && isStaticArray(key)
                            ? testRawPredicate(predicate, inner)
                            : false;
                }
                if (tuple) {
                    return tupleAll(key, predicate);
                }
                return null;
            case IS_MEMSET_SAFE:
                if (primitive) {
                    return true;
                }
                if (pointer) {
                    return false;
                }
                if (array) {
                    String inner = arrayInner(key);
                    return inner != null && isStaticArray(key)
                            ? testRawPredicate(predicate, inner)
                            : false;
                }
                if (tuple) {
                    return tupleAll(key, predicate);
                }
                return null;
            case HAS_DEFAULT_VALUE:
                if (primitive) {
                    return false;
                }
                if (pointer) {
                    return true;
                }
                if (array) {
                    String inner = arrayInner(key);
                    return inner == null ? null : isStaticArray(key)
                            ? testRawPredicate(predicate, inner)
                            : true;
                }
                if (tuple) {
                    return tupleAny(key, predicate);
                }
                return null;
            default:
                return null;
        }
    }

    private static Predicate predicateFor(String name) {
        switch (name) {
            case "is_primitive": return Predicate.IS_PRIMITIVE;
            case "is_enum": return Predicate.IS_ENUM;
            case "is_memcpy_safe": return Predicate.IS_MEMCPY_SAFE;
            case "has_default_value": return Predicate.HAS_DEFAULT_VALUE;
            case "is_memset_safe": return Predicate.IS_MEMSET_SAFE;
            case "is_matchable": return Predicate.IS_MATCHABLE;
            case "is_comparable": return Predicate.IS_COMPARABLE;
            case "is_assignable": return Predicate.IS_ASSIGNABLE;
            case "is_value_assignable": return Predicate.IS_VALUE_ASSIGNABLE;
            default: return null;
        }
    }

    private static boolean isStaticArray(String key) {
        int depth = 0;
        int comma = -1;

        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);

            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == ',' && depth == 1) {
                comma = i;
                break;
            }
        }

        return comma >= 0;
    }

    private static String arrayInner(String key) {
        if (!key.startsWith("[") || !key.endsWith("]")) {
            return null;
        }

        String inner = key.substring(1, key.length() - 1);
        int depth = 0;

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);

            if (c == '[' || c == '(' || c == '<') {
                depth++;
            } else if (c == ']' || c == ')' || c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return inner.substring(0, i);
            }
        }

        return inner;
    }

    private static Boolean tupleAll(String key, Predicate predicate) {
        for (String part : splitTopLevel(key.substring(1, key.length() - 1))) {
            Boolean result = testRawPredicate(predicate, part);

            if (result == null || !result) {
                return result;
            }
        }

        return true;
    }

    private static Boolean tupleAny(String key, Predicate predicate) {
        for (String part : splitTopLevel(key.substring(1, key.length() - 1))) {
            Boolean result = testRawPredicate(predicate, part);

            if (result == null) {
                return null;
            }

            if (result) {
                return true;
            }
        }

        return false;
    }

    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '[' || c == '(' || c == '<') {
                depth++;
            } else if (c == ']' || c == ')' || c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }

        parts.add(text.substring(start));
        return parts;
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

    static List<BigInteger> utf8Bytes(String text) {
        List<BigInteger> bytes = new ArrayList<>();

        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            bytes.add(BigInteger.valueOf(b & 0xFF));
        }

        return bytes;
    }
}
