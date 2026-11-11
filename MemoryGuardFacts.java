import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class MemoryGuardFacts {
    static final byte FACT_NULL = 1;
    static final byte FACT_NONNULL = 2;
    static final byte FACT_LENGTH_ZERO = 3;
    static final byte FACT_LENGTH_NONZERO = 4;

    static final int MAX_CASES = 16;
    static final int MAX_FACTS = 32;

    private static final class GuardCase {
        SastNode[] expressions = new SastNode[MAX_FACTS];
        byte[] kinds = new byte[MAX_FACTS];
        int count;
        boolean invalid;

        GuardCase copy() {
            GuardCase copy = new GuardCase();
            copy.count = count;
            copy.invalid = invalid;
            System.arraycopy(expressions, 0, copy.expressions, 0, count);
            System.arraycopy(kinds, 0, copy.kinds, 0, count);
            return copy;
        }
    }

    private static final class Budget {
        boolean overflow;
    }

    private static final GuardCase EMPTY_CASE = new GuardCase();

    private SastNode[] expressionByFact = new SastNode[0];
    private byte[] kindByFact = new byte[0];
    private int[] caseStart = new int[0];
    private int[] caseLength = new int[0];
    private int caseCount;
    private int factCount;

    private MemoryGuardFacts() {
    }

    static MemoryGuardFacts prepare(SastNode condition, boolean holds) {
        MemoryGuardFacts result = new MemoryGuardFacts();
        Budget budget = new Budget();
        List<GuardCase> cases = collect(condition, holds, budget);

        if (budget.overflow || cases.size() > MAX_CASES) {
            result.clearToEmptyCase();
            return result;
        }

        for (GuardCase guardCase : cases) {
            if (result.caseCount >= MAX_CASES
                    || result.factCount + guardCase.count
                    > MAX_CASES * MAX_FACTS) {
                result.clearToEmptyCase();
                return result;
            }

            result.appendCase(guardCase);
        }

        return result;
    }

    static MemoryGuardFacts unconstrained() {
        MemoryGuardFacts result = new MemoryGuardFacts();
        result.clearToEmptyCase();
        return result;
    }

    private void clearToEmptyCase() {
        expressionByFact = new SastNode[0];
        kindByFact = new byte[0];
        caseStart = new int[] { 0 };
        caseLength = new int[] { 0 };
        caseCount = 1;
        factCount = 0;
    }

    private void appendCase(GuardCase guardCase) {
        if (caseCount == caseStart.length) {
            int capacity = Math.max(4, caseStart.length * 2);
            caseStart = java.util.Arrays.copyOf(caseStart, capacity);
            caseLength = java.util.Arrays.copyOf(caseLength, capacity);
        }

        int start = factCount;
        int needed = factCount + guardCase.count;

        if (needed > expressionByFact.length) {
            int capacity = Math.max(16, needed * 2);
            expressionByFact = java.util.Arrays.copyOf(
                    expressionByFact,
                    capacity
            );
            kindByFact = java.util.Arrays.copyOf(kindByFact, capacity);
        }

        System.arraycopy(
                guardCase.expressions,
                0,
                expressionByFact,
                start,
                guardCase.count
        );
        System.arraycopy(
                guardCase.kinds,
                0,
                kindByFact,
                start,
                guardCase.count
        );

        caseStart[caseCount] = start;
        caseLength[caseCount] = guardCase.count;
        caseCount++;
        factCount = needed;
    }

    int caseCount() {
        return caseCount;
    }

    int caseFactStart(int guardCase) {
        return caseStart[guardCase];
    }

    int caseFactCount(int guardCase) {
        return caseLength[guardCase];
    }

    SastNode expressionAt(int fact) {
        return expressionByFact[fact];
    }

    byte kindAt(int fact) {
        return kindByFact[fact];
    }

    private static List<GuardCase> collect(
            SastNode condition,
            boolean holds,
            Budget budget
    ) {
        if (budget.overflow) {
            return List.of(EMPTY_CASE.copy());
        }

        if (condition == null) {
            return List.of(EMPTY_CASE.copy());
        }

        if (condition.constantValue != null) {
            boolean truth = condition.constantValue.signum() != 0;
            return truth == holds
                    ? List.of(EMPTY_CASE.copy())
                    : List.of();
        }

        if (condition.kind == SastKind.UNARY_OP
                && condition.op.equals("!")) {
            return collect(condition.expr, !holds, budget);
        }

        if (condition.kind == SastKind.BIN_OP) {
            if (condition.op.equals("&&")) {
                return holds
                        ? cross(
                                collect(condition.left, true, budget),
                                collect(condition.right, true, budget),
                                budget
                        )
                        : union(
                                collect(condition.left, false, budget),
                                cross(
                                        collect(condition.left, true, budget),
                                        collect(
                                                condition.right,
                                                false,
                                                budget
                                        ),
                                        budget
                                ),
                                budget
                        );
            }

            if (condition.op.equals("||")) {
                return holds
                        ? union(
                                collect(condition.left, true, budget),
                                cross(
                                        collect(condition.left, false, budget),
                                        collect(
                                                condition.right,
                                                true,
                                                budget
                                        ),
                                        budget
                                ),
                                budget
                        )
                        : cross(
                                collect(condition.left, false, budget),
                                collect(condition.right, false, budget),
                                budget
                        );
            }
        }

        return atom(condition, holds);
    }

    private static List<GuardCase> atom(SastNode condition, boolean holds) {
        if (condition.kind == SastKind.ARRAY_INTRINSIC_CALL
                && condition.intrinsicKind == SemanticArrayIntrinsicKind.LENGTH) {
            GuardCase guardCase = new GuardCase();
            addFact(
                    guardCase,
                    condition.receiver,
                    holds ? FACT_LENGTH_NONZERO : FACT_LENGTH_ZERO
            );
            return List.of(guardCase);
        }

        if (condition.kind != SastKind.BIN_OP
                || (!condition.op.equals("==")
                && !condition.op.equals("!=")
                && !condition.op.equals("<")
                && !condition.op.equals("<=")
                && !condition.op.equals(">")
                && !condition.op.equals(">="))) {
            return List.of(EMPTY_CASE.copy());
        }

        SastNode nullSide = null;
        SastNode otherSide = null;

        if (condition.left.kind == SastKind.NULL_LITERAL) {
            nullSide = condition.left;
            otherSide = condition.right;
        } else if (condition.right.kind == SastKind.NULL_LITERAL) {
            nullSide = condition.right;
            otherSide = condition.left;
        }

        if (nullSide != null
                && (condition.op.equals("==") || condition.op.equals("!="))) {
            boolean assertedNull = condition.op.equals("==") == holds;
            GuardCase guardCase = new GuardCase();
            addFact(
                    guardCase,
                    otherSide,
                    assertedNull ? FACT_NULL : FACT_NONNULL
            );
            return List.of(guardCase);
        }

        boolean leftLength = isLengthCall(condition.left);
        boolean rightLength = isLengthCall(condition.right);

        if (leftLength != rightLength) {
            SastNode lengthSide = leftLength
                    ? condition.left
                    : condition.right;
            SastNode constantSide = leftLength
                    ? condition.right
                    : condition.left;

            if (constantSide.constantValue != null) {
                String op = leftLength
                        ? condition.op
                        : reverse(condition.op);
                byte fact = lengthFact(op, constantSide.constantValue, holds);

                if (fact != 0) {
                    GuardCase guardCase = new GuardCase();
                    addFact(guardCase, lengthSide.receiver, fact);
                    return List.of(guardCase);
                }
            }
        }

        return List.of(EMPTY_CASE.copy());
    }

    private static boolean isLengthCall(SastNode expression) {
        return expression.kind == SastKind.ARRAY_INTRINSIC_CALL
                && expression.intrinsicKind == SemanticArrayIntrinsicKind.LENGTH;
    }

    private static byte lengthFact(
            String op,
            BigInteger constant,
            boolean holds
    ) {
        String effective = holds ? op : negate(op);
        int sign = constant.signum();

        switch (effective) {
            case "==":
                if (sign == 0) {
                    return FACT_LENGTH_ZERO;
                }

                return sign > 0 ? FACT_LENGTH_NONZERO : 0;
            case "!=":
                return sign == 0 ? FACT_LENGTH_NONZERO : 0;
            case "<":
                return constant.equals(BigInteger.ONE)
                        ? FACT_LENGTH_ZERO
                        : 0;
            case "<=":
                return sign == 0 ? FACT_LENGTH_ZERO : 0;
            case ">":
                return sign >= 0 ? FACT_LENGTH_NONZERO : 0;
            case ">=":
                return constant.compareTo(BigInteger.ONE) >= 0
                        ? FACT_LENGTH_NONZERO
                        : 0;
            default:
                return 0;
        }
    }

    private static String reverse(String op) {
        switch (op) {
            case "==": return "==";
            case "!=": return "!=";
            case "<": return ">";
            case "<=": return ">=";
            case ">": return "<";
            case ">=": return "<=";
            default: return op;
        }
    }

    private static String negate(String op) {
        switch (op) {
            case "==": return "!=";
            case "!=": return "==";
            case "<": return ">=";
            case "<=": return ">";
            case ">": return "<=";
            case ">=": return "<";
            default: return op;
        }
    }

    private static void addFact(
            GuardCase guardCase,
            SastNode expression,
            byte kind
    ) {
        if (guardCase.invalid || expression == null) {
            return;
        }

        byte opposite = opposite(kind);

        for (int i = 0; i < guardCase.count; i++) {
            if (guardCase.expressions[i] != expression) {
                continue;
            }

            if (guardCase.kinds[i] == kind) {
                return;
            }

            if (guardCase.kinds[i] == opposite) {
                guardCase.invalid = true;
                return;
            }
        }

        if (guardCase.count >= MAX_FACTS) {
            guardCase.invalid = true;
            return;
        }

        guardCase.expressions[guardCase.count] = expression;
        guardCase.kinds[guardCase.count] = kind;
        guardCase.count++;
    }

    private static byte opposite(byte kind) {
        switch (kind) {
            case FACT_NULL: return FACT_NONNULL;
            case FACT_NONNULL: return FACT_NULL;
            case FACT_LENGTH_ZERO: return FACT_LENGTH_NONZERO;
            case FACT_LENGTH_NONZERO: return FACT_LENGTH_ZERO;
            default: return 0;
        }
    }

    private static List<GuardCase> union(
            List<GuardCase> left,
            List<GuardCase> right,
            Budget budget
    ) {
        if (budget.overflow) {
            return List.of(EMPTY_CASE.copy());
        }

        List<GuardCase> result = new ArrayList<>(left.size() + right.size());

        for (GuardCase guardCase : left) {
            if (!guardCase.invalid) {
                result.add(guardCase);
            }
        }

        for (GuardCase guardCase : right) {
            if (!guardCase.invalid) {
                result.add(guardCase);
            }
        }

        if (result.size() > MAX_CASES) {
            budget.overflow = true;
            return List.of(EMPTY_CASE.copy());
        }

        return result;
    }

    private static List<GuardCase> cross(
            List<GuardCase> left,
            List<GuardCase> right,
            Budget budget
    ) {
        if (budget.overflow) {
            return List.of(EMPTY_CASE.copy());
        }

        List<GuardCase> result = new ArrayList<>(left.size() * right.size());

        for (GuardCase leftCase : left) {
            if (leftCase.invalid) {
                continue;
            }

            for (GuardCase rightCase : right) {
                if (rightCase.invalid) {
                    continue;
                }

                GuardCase merged = leftCase.copy();

                for (int i = 0; i < rightCase.count; i++) {
                    addFact(
                            merged,
                            rightCase.expressions[i],
                            rightCase.kinds[i]
                    );
                }

                if (!merged.invalid) {
                    result.add(merged);
                }
            }
        }

        if (result.size() > MAX_CASES) {
            budget.overflow = true;
            return List.of(EMPTY_CASE.copy());
        }

        return result;
    }
}
