import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class DanglingPointerState {

    static OriginSet mergeOriginSets(OriginSet left, OriginSet right) {
        if (left == null) {
            return right == null ? OriginSet.UNKNOWN : right;
        }

        if (right == null) {
            return left;
        }

        return left.union(right);
    }

    static String functionDisplayName(FunctionSymbol function) {
        if (function == null) {
            return "<unknown>";
        }

        return function.qualifiedName();
    }

    static String formatLocalOrigins(Set<VariableSymbol> cells) {
        List<String> parameters = new ArrayList<>();
        List<String> locals = new ArrayList<>();

        for (VariableSymbol symbol : cells) {
            if (symbol instanceof ParameterSymbol) {
                parameters.add(symbol.name);
            } else {
                locals.add(symbol.qualifiedName());
            }
        }

        Collections.sort(parameters);
        Collections.sort(locals);

        List<String> parts = new ArrayList<>();

        if (!parameters.isEmpty()) {
            parts.add(
                    (parameters.size() == 1 ? "parameter " : "parameters ")
                            + quoteList(parameters)
            );
        }

        if (!locals.isEmpty()) {
            parts.add(
                    (locals.size() == 1 ? "local variable " : "local variables ")
                            + quoteList(locals)
            );
        }

        if (parts.isEmpty()) {
            return "local storage";
        }

        return String.join(" and ", parts);
    }

    static String quoteList(List<String> values) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append('\'').append(values.get(i)).append('\'');
        }

        return sb.toString();
    }

    static NullGuard nullGuard(SemanticExprNode condition) {
        if (!(condition instanceof SemanticBinOpNode bin)) {
            return null;
        }

        if (!bin.op.equals("==") && !bin.op.equals("!=")) {
            return null;
        }

        if (bin.left instanceof SemanticNullLiteralNode) {
            return new NullGuard(bin.right, bin.op.equals("=="));
        }

        if (bin.right instanceof SemanticNullLiteralNode) {
            return new NullGuard(bin.left, bin.op.equals("=="));
        }

        return null;
    }
}

final class OriginSet {

    static final OriginSet EMPTY =
            new OriginSet(Set.of(), Set.of(), false, false);

    static final OriginSet UNKNOWN =
            new OriginSet(Set.of(), Set.of(), true, false);

    final Set<VariableSymbol> frameCells;
    final Set<VariableSymbol> globalCells;
    final boolean unknown;
    final boolean nonNull;

    private OriginSet(
            Set<VariableSymbol> frameCells,
            Set<VariableSymbol> globalCells,
            boolean unknown,
            boolean nonNull
    ) {
        this.frameCells = frameCells;
        this.globalCells = globalCells;
        this.unknown = unknown;
        this.nonNull = nonNull;
    }

    static OriginSet frame(VariableSymbol symbol) {
        return new OriginSet(
                Set.of(symbol),
                Set.of(),
                false,
                true
        );
    }

    static OriginSet global(VariableSymbol symbol) {
        return new OriginSet(
                Set.of(),
                Set.of(symbol),
                false,
                true
        );
    }

    boolean definiteDangle() {
        return !frameCells.isEmpty()
                && !unknown
                && globalCells.isEmpty()
                && nonNull;
    }

    boolean definitelyNull() {
        return !nonNull
                && !unknown
                && frameCells.isEmpty()
                && globalCells.isEmpty();
    }

    OriginSet withNonNull() {
        if (nonNull) {
            return this;
        }

        if (frameCells.isEmpty() && globalCells.isEmpty()) {
            return new OriginSet(
                    Set.of(),
                    Set.of(),
                    true,
                    true
            );
        }

        return new OriginSet(
                frameCells,
                globalCells,
                unknown,
                true
        );
    }

    OriginSet union(OriginSet other) {
        if (other == null) {
            other = UNKNOWN;
        }

        if (this == other) {
            return this;
        }

        Set<VariableSymbol> frame = new LinkedHashSet<>(frameCells);
        frame.addAll(other.frameCells);

        Set<VariableSymbol> global = new LinkedHashSet<>(globalCells);
        global.addAll(other.globalCells);

        return new OriginSet(
                Set.copyOf(frame),
                Set.copyOf(global),
                unknown || other.unknown,
                nonNull && other.nonNull
        );
    }
}

final class NullGuard {

    final SemanticExprNode variable;
    final boolean isEquality;

    NullGuard(SemanticExprNode variable, boolean isEquality) {
        this.variable = variable;
        this.isEquality = isEquality;
    }
}

