import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class StringConstantOps {

    private StringConstantOps() {
    }

    static boolean isComparison(String op) {
        return "==".equals(op)
                || "!=".equals(op)
                || "<".equals(op)
                || ">".equals(op)
                || "<=".equals(op)
                || ">=".equals(op);
    }

    static boolean isStringOperator(String op) {
        return "+".equals(op) || isComparison(op);
    }

    static List<BigInteger> concat(
            List<BigInteger> left,
            List<BigInteger> right
    ) {
        List<BigInteger> combined = new ArrayList<>(
                left.size() + right.size()
        );
        combined.addAll(left);
        combined.addAll(right);
        return List.copyOf(combined);
    }

    static int compare(
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

    static BigInteger foldComparison(
            String op,
            List<BigInteger> left,
            List<BigInteger> right
    ) {
        int comparison = compare(left, right);
        boolean result;

        switch (op) {
            case "==":
                result = comparison == 0;
                break;
            case "!=":
                result = comparison != 0;
                break;
            case "<":
                result = comparison < 0;
                break;
            case ">":
                result = comparison > 0;
                break;
            case "<=":
                result = comparison <= 0;
                break;
            case ">=":
                result = comparison >= 0;
                break;
            default:
                throw new IllegalArgumentException(
                        "Not a string comparison operator: " + op
                );
        }

        return result ? BigInteger.ONE : BigInteger.ZERO;
    }
}
