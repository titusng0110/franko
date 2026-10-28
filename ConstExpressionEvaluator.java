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

        return switch (op) {
            case "-" -> val.negate();
            case "!" -> val.signum() == 0 ? BigInteger.ONE : BigInteger.ZERO;
            default -> null;
        };
    }

    public static BigInteger tryFoldBinary(
            String op,
            BigInteger left,
            BigInteger right
    ) {
        if (left == null || right == null) {
            return null;
        }

        return switch (op) {
            case "+" -> left.add(right);
            case "-" -> left.subtract(right);
            case "*" -> left.multiply(right);
            case "/" -> right.signum() == 0 ? null : left.divide(right);

            case "%" -> right.signum() == 0 ? null : left.remainder(right);
            case "&" -> left.and(right);
            case "|" -> left.or(right);
            case "^" -> left.xor(right);
            case "<<" -> right.signum() < 0 ? null : left.shiftLeft(right.intValueExact());
            case ">>" -> right.signum() < 0 ? null : left.shiftRight(right.intValueExact());
            case "==" -> left.equals(right) ? BigInteger.ONE : BigInteger.ZERO;
            case "!=" -> !left.equals(right) ? BigInteger.ONE : BigInteger.ZERO;
            case "<" -> left.compareTo(right) < 0 ? BigInteger.ONE : BigInteger.ZERO;
            case ">" -> left.compareTo(right) > 0 ? BigInteger.ONE : BigInteger.ZERO;
            case "<=" -> left.compareTo(right) <= 0 ? BigInteger.ONE : BigInteger.ZERO;
            case ">=" -> left.compareTo(right) >= 0 ? BigInteger.ONE : BigInteger.ZERO;
            case "&&" -> left.signum() != 0 && right.signum() != 0 ? BigInteger.ONE : BigInteger.ZERO;
            case "||" -> left.signum() != 0 || right.signum() != 0 ? BigInteger.ONE : BigInteger.ZERO;
            default -> null;
        };
    }

    public static final class UnevaluableConstant extends Exception {
        private final String diagnostic;

        public UnevaluableConstant(String diagnostic) {
            super(diagnostic);
            this.diagnostic = diagnostic;
        }

        public String getDiagnostic() {
            return diagnostic;
        }
    }

    public static BigInteger tryFold(ASTNode expr) {
        try {
            return evaluatePure(expr);
        } catch (UnevaluableConstant e) {
            return null;
        }
    }

    public static BigInteger evaluatePure(ASTNode expr) throws UnevaluableConstant {
        if (expr instanceof IntNode n) {
            BigInteger parsed = parseIntegerLiteralPure(n.value);

            if (parsed == null) {
                throw new UnevaluableConstant(null);
            }

            return parsed;
        }

        if (expr instanceof UnaryOpNode u) {
            BigInteger folded = tryFoldUnary(u.op, evaluatePure(u.expr));

            if (folded == null) {
                throw new UnevaluableConstant(null);
            }

            return folded;
        }

        if (expr instanceof BinOpNode b) {
            BigInteger left = evaluatePure(b.left);
            BigInteger right = evaluatePure(b.right);

            if (("/".equals(b.op) || "%".equals(b.op)) && right.signum() == 0) {
                throw new UnevaluableConstant("Division by zero in constant expression");
            }

            try {
                BigInteger folded = tryFoldBinary(b.op, left, right);

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
        } catch (Exception e) {
            return null;
        }
    }
}
