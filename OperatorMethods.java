import java.util.Map;

final class OperatorMethods {
    private OperatorMethods() {}

    private static final Map<String, String> COMPARISON = Map.of(
            "==", "OPERATOR_EQ",
            "!=", "OPERATOR_NEQ",
            ">", "OPERATOR_GT",
            "<", "OPERATOR_LT",
            ">=", "OPERATOR_GTEQ",
            "<=", "OPERATOR_LTEQ"
    );

    private static final Map<String, String> ASSIGNMENT = Map.of(
            "=", "OPERATOR_MOV",
            "+=", "OPERATOR_ADDMOV",
            "-=", "OPERATOR_SUBMOV",
            "*=", "OPERATOR_MULMOV",
            "/=", "OPERATOR_DIVMOV",
            "%=", "OPERATOR_MODMOV",
            "|=", "OPERATOR_ORMOV",
            "&=", "OPERATOR_ANDMOV",
            "^=", "OPERATOR_XORMOV"
    );

    static String comparisonMethodName(String op) {
        return COMPARISON.get(op);
    }

    static String assignmentMethodName(String op) {
        return ASSIGNMENT.get(op);
    }

    static boolean isComparisonOperator(String op) {
        return COMPARISON.containsKey(op);
    }

    static boolean isAssignmentOperator(String op) {
        return ASSIGNMENT.containsKey(op);
    }

    static boolean isComparisonMethodName(String name) {
        return COMPARISON.containsValue(name);
    }

    static boolean isAssignmentMethodName(String name) {
        return ASSIGNMENT.containsValue(name);
    }

    static boolean isOperatorMethodName(String name) {
        return isComparisonMethodName(name) || isAssignmentMethodName(name);
    }
}
