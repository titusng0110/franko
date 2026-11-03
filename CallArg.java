import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class CallArg {

    final SemanticExprNode lowered;
    final ArrayLiteralNode literal;
    private final boolean operator;

    private CallArg(
            SemanticExprNode lowered,
            ArrayLiteralNode literal,
            boolean operator
    ) {
        this.lowered = lowered;
        this.literal = literal;
        this.operator = operator;
    }

    static CallArg lowered(SemanticExprNode expr) {
        return new CallArg(Objects.requireNonNull(expr), null, false);
    }

    static CallArg literal(ArrayLiteralNode literal) {
        return new CallArg(null, Objects.requireNonNull(literal), false);
    }

    static CallArg operatorLowered(SemanticExprNode expr) {
        return new CallArg(Objects.requireNonNull(expr), null, true);
    }

    static CallArg operatorLiteral(ArrayLiteralNode literal) {
        return new CallArg(null, Objects.requireNonNull(literal), true);
    }

    static List<CallArg> listOf(List<SemanticExprNode> args) {
        List<CallArg> out = new ArrayList<>(args.size());

        for (SemanticExprNode arg : args) {
            out.add(lowered(arg));
        }

        return out;
    }

    boolean isLiteral() {
        return literal != null;
    }

    SemanticType type() {
        return isLiteral() ? null : lowered.type;
    }

    String describeForSignature() {
        if (isLiteral()) {
            return "[array literal with "
                    + literal.elements.size()
                    + " element(s)]";
        }

        if (operator) {
            if (lowered == null || lowered.type == null) {
                return "<null>";
            }

            return "view<" + lowered.type.describe() + ">";
        }

        return lowered.type == null ? "<null>" : lowered.type.describe();
    }
}
