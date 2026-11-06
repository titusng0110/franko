import java.math.BigInteger;

public final class ConstExpressionEvaluator {

    private final SymbolTable ctx;

    public ConstExpressionEvaluator(SymbolTable ctx) {
        this.ctx = ctx;
    }

    public BigInteger parseIntegerLiteralSafe(String val) {
        BigInteger parsed = parseIntegerLiteralPure(val);

        if (parsed == null) {
            ctx.error("Invalid integer literal format: " + val);
            return BigInteger.ZERO;
        }

        return parsed;
    }

    public BigInteger foldUnary(String op, BigInteger val) {
        return tryFoldUnary(op, val);
    }

    public BigInteger foldBinary(
            String op,
            BigInteger left,
            BigInteger right
    ) {
        if (left == null || right == null) {
            return null;
        }

        if (("/".equals(op) || "%".equals(op)) && right.signum() == 0) {
            ctx.error("Division by zero in constant expression");
            return null;
        }

        try {
            return tryFoldBinary(op, left, right);
        } catch (ArithmeticException e) {
            ctx.error("Arithmetic error during constant folding: " + e.getMessage());
            return null;
        }
    }

    public static BigInteger tryFoldUnary(String op, BigInteger val) {
        if (val == null) {
            return null;
        }

        switch (op) {
            case "-":
                return val.negate();
            case "!":
                return val.signum() == 0 ? BigInteger.ONE : BigInteger.ZERO;
            case "~":
                return val.not();
            default:
                return null;
        }
    }

    public static BigInteger tryFoldBinary(
            String op,
            BigInteger left,
            BigInteger right
    ) {
        if (left == null || right == null) {
            return null;
        }

        switch (op) {
            case "+":
                return left.add(right);
            case "-":
                return left.subtract(right);
            case "*":
                return left.multiply(right);
            case "/":
                return right.signum() == 0 ? null : left.divide(right);
            case "%":
                return right.signum() == 0 ? null : left.remainder(right);
            case "&":
                return left.and(right);
            case "|":
                return left.or(right);
            case "^":
                return left.xor(right);
            case "<<":
                return right.signum() < 0 ? null : left.shiftLeft(right.intValueExact());
            case ">>":
                return right.signum() < 0 ? null : left.shiftRight(right.intValueExact());
            case "==":
                return left.equals(right) ? BigInteger.ONE : BigInteger.ZERO;
            case "!=":
                return !left.equals(right) ? BigInteger.ONE : BigInteger.ZERO;
            case "<":
                return left.compareTo(right) < 0 ? BigInteger.ONE : BigInteger.ZERO;
            case ">":
                return left.compareTo(right) > 0 ? BigInteger.ONE : BigInteger.ZERO;
            case "<=":
                return left.compareTo(right) <= 0 ? BigInteger.ONE : BigInteger.ZERO;
            case ">=":
                return left.compareTo(right) >= 0 ? BigInteger.ONE : BigInteger.ZERO;
            case "&&":
                return left.signum() != 0 && right.signum() != 0
                        ? BigInteger.ONE : BigInteger.ZERO;
            case "||":
                return left.signum() != 0 || right.signum() != 0
                        ? BigInteger.ONE : BigInteger.ZERO;
            default:
                return null;
        }
    }

    public static final class UnevaluableConstant extends Exception {
        private static final long serialVersionUID = 1L;
        private final String diagnostic;

        public UnevaluableConstant(String diagnostic) {
            super(diagnostic);
            this.diagnostic = diagnostic;
        }

        public String getDiagnostic() {
            return diagnostic;
        }
    }

    public static BigInteger tryFold(AstNode expr) {
        try {
            return evaluatePure(expr);
        } catch (UnevaluableConstant e) {
            return null;
        }
    }

    public static BigInteger evaluatePure(AstNode expr) throws UnevaluableConstant {
        if (expr != null && expr.kind == AstKind.INT) {
            BigInteger parsed = parseIntegerLiteralPure(expr.text);

            if (parsed == null) {
                throw new UnevaluableConstant(null);
            }

            return parsed;
        }

        if (expr != null && expr.kind == AstKind.UNARY) {
            BigInteger folded = tryFoldUnary(expr.text, evaluatePure(expr.expr));

            if (folded == null) {
                throw new UnevaluableConstant(null);
            }

            return folded;
        }

        if (expr != null && expr.kind == AstKind.BINARY) {
            BigInteger left = evaluatePure(expr.left);
            BigInteger right = evaluatePure(expr.right);

            if (("/".equals(expr.text) || "%".equals(expr.text)) && right.signum() == 0) {
                throw new UnevaluableConstant("Division by zero in constant expression");
            }

            try {
                BigInteger folded = tryFoldBinary(expr.text, left, right);

                if (folded == null) {
                    throw new UnevaluableConstant(null);
                }

                return folded;
            } catch (ArithmeticException e) {
                throw new UnevaluableConstant(
                    "Arithmetic error during constant folding: " + e.getMessage()
                );
            }
        }

        throw new UnevaluableConstant(null);
    }

    public static BigInteger parseIntegerLiteralPure(String val) {
        if (val == null) {
            return null;
        }

        try {
            String s = val.trim();

            if (s.startsWith("+")) {
                s = s.substring(1);
            }

            String lower = s.toLowerCase();

            if (lower.startsWith("0b")) {
                return new BigInteger(s.substring(2), 2);
            }

            if (lower.startsWith("0x")) {
                return new BigInteger(s.substring(2), 16);
            }

            return new BigInteger(s, 10);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
