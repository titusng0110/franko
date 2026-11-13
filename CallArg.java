import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class CallArg {

    final SastNode lowered;
    final AstNode literal;
    final String name;
    private final boolean operator;

    private CallArg(
            SastNode lowered,
            AstNode literal,
            String name,
            boolean operator
    ) {
        this.lowered = lowered;
        this.literal = literal;
        this.name = name == null ? "" : name;
        this.operator = operator;
    }

    static CallArg lowered(SastNode expr) {
        return lowered(expr, "");
    }

    static CallArg lowered(SastNode expr, String name) {
        return new CallArg(Objects.requireNonNull(expr), null, name, false);
    }

    static CallArg literal(AstNode literal) {
        return literal(literal, "");
    }

    static CallArg literal(AstNode literal, String name) {
        return new CallArg(null, Objects.requireNonNull(literal), name, false);
    }

    static CallArg operatorLowered(SastNode expr) {
        return new CallArg(Objects.requireNonNull(expr), null, "", true);
    }

    static CallArg operatorLiteral(AstNode literal) {
        return new CallArg(null, Objects.requireNonNull(literal), "", true);
    }

    static List<CallArg> listOf(List<SastNode> args) {
        List<CallArg> out = new ArrayList<>(args.size());

        for (SastNode arg : args) {
            out.add(lowered(arg));
        }

        return out;
    }

    boolean isLiteral() {
        return literal != null;
    }

    boolean isNamed() {
        return name != null && !name.isEmpty();
    }

    SastType type() {
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
