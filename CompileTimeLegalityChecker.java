import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CompileTimeLegalityChecker {
    private final DiagnosticBag diagnostics;

    CompileTimeLegalityChecker(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    void checkDocument(SastNode root) {
        if (root == null) {
            return;
        }

        List<SastNode> functions = new ArrayList<>();
        collectDeclarations(root, functions);

        Map<Symbol, List<Symbol>> callsBySymbol = new IdentityHashMap<>();

        for (SastNode function : functions) {
            if (function.symbol == null) {
                continue;
            }

            List<Symbol> calls = new ArrayList<>();
            collectCalls(function.body, calls);
            callsBySymbol.put(function.symbol, calls);
        }

        for (SastNode function : functions) {
            Symbol symbol = function.symbol;

            if (symbol == null || symbol.inlineMode != InlineMode.INLINE) {
                continue;
            }

            List<Symbol> path = new ArrayList<>();
            IdentityHashMap<Symbol, Boolean> onPath = new IdentityHashMap<>();
            IdentityHashMap<Symbol, Boolean> finished = new IdentityHashMap<>();
            path.add(symbol);
            onPath.put(symbol, Boolean.TRUE);

            List<Symbol> cycle = findCycle(
                    symbol,
                    callsBySymbol,
                    onPath,
                    finished,
                    path
            );

            if (cycle == null) {
                continue;
            }

            diagnostics.push(function.span);
            try {
                diagnostics.error("Inline function '"
                        + symbol.qualifiedName()
                        + "' cannot participate in call-graph recursion: "
                        + formatPath(cycle)
                        + ". #inline functions are expanded at every call"
                        + " site, so a recursive cycle would expand forever."
                        + " Remove #inline from one function on the cycle or"
                        + " break the recursion.");
            } finally {
                diagnostics.pop();
            }
        }
    }

    private static void collectDeclarations(
            SastNode node,
            List<SastNode> out
    ) {
        if (node == null) {
            return;
        }

        switch (node.kind) {
            case PROGRAM -> {
                for (SastNode item : node.topLevelItems) {
                    collectDeclarations(item, out);
                }
            }
            case NAMESPACE -> {
                for (SastNode item : node.items) {
                    collectDeclarations(item, out);
                }
            }
            case STRUCT_DECL -> {
                for (SastNode method : node.methods) {
                    collectDeclarations(method, out);
                }
            }
            case FUNCTION_DECL -> out.add(node);
            default -> {
            }
        }
    }

    private static void collectCalls(SastNode node, List<Symbol> out) {
        if (node == null) {
            return;
        }

        switch (node.kind) {
            case BLOCK -> {
                for (SastNode statement : node.statements) {
                    collectCalls(statement, out);
                }
            }
            case VAR_DECL -> collectCalls(node.staticInitializer, out);
            case VAR_DECL_INIT -> {
                for (SastNode initializer : node.initializers) {
                    collectCalls(initializer, out);
                }
            }
            case ASSIGN -> {
                collectCalls(node.target, out);
                collectCalls(node.value, out);
            }
            case IF -> {
                collectCalls(node.condition, out);
                collectCalls(node.thenBranch, out);
                collectCalls(node.elseBranch, out);
            }
            case WHILE -> {
                collectCalls(node.condition, out);
                collectCalls(node.body, out);

                for (SastNode update : node.update) {
                    collectCalls(update, out);
                }
            }
            case RETURN -> collectCalls(node.value, out);
            case EXPR_STMT -> collectCalls(node.expr, out);
            case UNARY_OP -> collectCalls(node.expr, out);
            case BIN_OP -> {
                collectCalls(node.left, out);
                collectCalls(node.right, out);
            }
            case ARRAY_ACCESS -> {
                collectCalls(node.target, out);
                collectCalls(node.index, out);
            }
            case MEMBER_ACCESS -> collectCalls(node.target, out);
            case FUNCTION_CALL -> {
                if (node.function != null) {
                    out.add(node.function);
                }

                for (SastNode arg : node.args) {
                    collectCalls(arg, out);
                }
            }
            case ARRAY_INTRINSIC_CALL -> {
                collectCalls(node.receiver, out);

                for (SastNode arg : node.args) {
                    collectCalls(arg, out);
                }
            }
            case GET_PTR -> collectCalls(node.target, out);
            case DEREF -> collectCalls(node.expr, out);
            case CAST -> collectCalls(node.expr, out);
            case AGGREGATE_LITERAL -> {
                for (SastNode field : node.fields) {
                    collectCalls(field, out);
                }
            }
            case AGGREGATE_FIELD_INIT -> collectCalls(node.value, out);
            default -> {
            }
        }
    }

    private static List<Symbol> findCycle(
            Symbol start,
            Map<Symbol, List<Symbol>> callsBySymbol,
            IdentityHashMap<Symbol, Boolean> onPath,
            IdentityHashMap<Symbol, Boolean> finished,
            List<Symbol> path
    ) {
        Symbol current = path.get(path.size() - 1);
        List<Symbol> calls = callsBySymbol.get(current);

        if (calls == null) {
            finished.put(current, Boolean.TRUE);
            return null;
        }

        for (Symbol callee : calls) {
            if (callee == null) {
                continue;
            }

            if (callee == start) {
                List<Symbol> cycle = new ArrayList<>(path);
                cycle.add(start);
                return cycle;
            }

            if (onPath.containsKey(callee)
                    || finished.containsKey(callee)
                    || !callsBySymbol.containsKey(callee)) {
                continue;
            }

            onPath.put(callee, Boolean.TRUE);
            path.add(callee);

            List<Symbol> cycle = findCycle(
                    start,
                    callsBySymbol,
                    onPath,
                    finished,
                    path
            );

            if (cycle != null) {
                return cycle;
            }

            path.remove(path.size() - 1);
            onPath.remove(callee);
            finished.put(callee, Boolean.TRUE);
        }

        finished.put(current, Boolean.TRUE);
        return null;
    }

    private static String formatPath(List<Symbol> path) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                sb.append(" -> ");
            }

            sb.append(path.get(i).qualifiedName());
        }

        return sb.toString();
    }
}
