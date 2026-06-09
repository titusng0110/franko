import java.math.BigInteger;

/**
 * ============================================================================
 * CONSTANT EXPRESSION EVALUATOR
 * Integer Literal Parsing and Compile-Time Integer Folding
 * ============================================================================
 *
 * This class owns the mechanically foldable integer-expression logic that was
 * previously embedded in SemanticAnalyzer.
 *
 * It deliberately does not perform full semantic legality validation.
 */
public final class ConstExpressionEvaluator {

    private final SymbolTable ctx;

    public ConstExpressionEvaluator(SymbolTable ctx) {
        this.ctx = ctx;
    }

    public BigInteger parseIntegerLiteralSafe(String val) {
        try {
            String s = val.trim();

            if (s.startsWith("+")) {
                s = s.substring(1);
            }

            if (s.toLowerCase().startsWith("0b")) {
                return new BigInteger(s.substring(2), 2);
            }

            if (s.toLowerCase().startsWith("0x")) {
                return new BigInteger(s.substring(2), 16);
            }

            return new BigInteger(s, 10);
        } catch (Exception e) {
            ctx.error("Invalid integer literal format: " + val);
            return BigInteger.ZERO;
        }
    }

    public BigInteger foldUnary(String op, BigInteger val) {
        if (val == null) {
            return null;
        }

        return switch (op) {
            case "-" -> val.negate();
            case "!" -> val.signum() == 0 ? BigInteger.ONE : BigInteger.ZERO;
            default -> null;
        };
    }

    public BigInteger foldBinary(
            String op,
            BigInteger left,
            BigInteger right
    ) {
        if (left == null || right == null) {
            return null;
        }

        try {
            return switch (op) {
                case "+" -> left.add(right);
                case "-" -> left.subtract(right);
                case "*" -> left.multiply(right);
                case "/" -> right.signum() == 0 ? null : left.divide(right);

                case "&" -> left.and(right);
                case "|" -> left.or(right);
                case "^" -> left.xor(right);

                case "<<" -> {
                    if (right.signum() < 0) {
                        yield null;
                    }

                    try {
                        yield left.shiftLeft(right.intValueExact());
                    } catch (ArithmeticException ex) {
                        yield null;
                    }
                }

                case ">>" -> {
                    if (right.signum() < 0) {
                        yield null;
                    }

                    try {
                        yield left.shiftRight(right.intValueExact());
                    } catch (ArithmeticException ex) {
                        yield null;
                    }
                }

                case "==" -> left.equals(right) ? BigInteger.ONE : BigInteger.ZERO;
                case "!=" -> !left.equals(right) ? BigInteger.ONE : BigInteger.ZERO;
                case "<" -> left.compareTo(right) < 0 ? BigInteger.ONE : BigInteger.ZERO;
                case ">" -> left.compareTo(right) > 0 ? BigInteger.ONE : BigInteger.ZERO;
                case "<=" -> left.compareTo(right) <= 0 ? BigInteger.ONE : BigInteger.ZERO;
                case ">=" -> left.compareTo(right) >= 0 ? BigInteger.ONE : BigInteger.ZERO;

                case "&&" ->
                        left.signum() != 0 && right.signum() != 0
                                ? BigInteger.ONE
                                : BigInteger.ZERO;

                case "||" ->
                        left.signum() != 0 || right.signum() != 0
                                ? BigInteger.ONE
                                : BigInteger.ZERO;

                default -> null;
            };
        } catch (ArithmeticException e) {
            return null;
        }
    }
}
