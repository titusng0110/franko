import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class BranchPruner {

    private static final int MAX_ATOMS = 12;

    private enum Determination {
        TRUE,
        FALSE,
        UNDECIDED,
        UNPRUNABLE
    }

    private interface Node {}

    private record Lit(boolean value) implements Node {}

    private record Var(int index) implements Node {}

    private record Not(Node inner) implements Node {}

    private record And(Node left, Node right) implements Node {}

    private record Or(Node left, Node right) implements Node {}

    SastNode prune(SastNode root) {
        return pruneItem(root);
    }

    SastDocument prune(SastDocument document) {
        return new SastDocument(
                SastPhase.BRANCH_PRUNED,
                document.entryKind,
                document.sources,
                document.symbols,
                (SastNode) pruneItem(document.root)
        );
    }

    private SastNode pruneItem(SastNode node) {

        if (node.kind == SastKind.PROGRAM) {
            List<SastNode> items = pruneItemList(node.topLevelItems);

            return items == node.topLevelItems
                    ? node
                    : SastNode.program(node.span, items);
        }

        if (node.kind == SastKind.NAMESPACE) {
            List<SastNode> items = pruneItemList(node.items);

            return items == node.items
                    ? node
                    : SastNode.namespace(
                            node.span,
                            node.name,
                            items
                    );
        }

        if (node.kind == SastKind.FUNCTION_DECL) {
            SastNode body = pruneBlock(node.body);

            return body == node.body
                    ? node
                    : SastNode.functionDecl(
                            node.span,
                            node.symbol,
                            node.parameterVariables,
                            body
                    );
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            List<SastNode> methods = null;

            for (int i = 0; i < node.methods.size(); i++) {
                SastNode method = node.methods.get(i);
                SastNode pruned =
                        (SastNode) pruneItem(method);

                if (methods == null && pruned != method) {
                    methods = new ArrayList<>(node.methods.subList(0, i));
                }

                if (methods != null) {
                    methods.add(pruned);
                }
            }

            return methods == null
                    ? node
                    : SastNode.structDecl(
                            node.span,
                            node.structSymbol,
                            methods
                    );
        }

        return node;
    }

    private List<SastNode> pruneItemList(List<SastNode> items) {
        List<SastNode> out = null;

        for (int i = 0; i < items.size(); i++) {
            SastNode item = items.get(i);
            SastNode pruned = pruneItem(item);

            if (out == null && pruned != item) {
                out = new ArrayList<>(items.subList(0, i));
            }

            if (out != null) {
                out.add(pruned);
            }
        }

        return out == null ? items : out;
    }

    private SastNode pruneStmt(SastNode stmt) {

        if (stmt.kind == SastKind.BLOCK) {
            return pruneBlock(stmt);
        }

        if (stmt.kind == SastKind.IF) {
            return pruneIf(stmt);
        }

        if (stmt.kind == SastKind.WHILE) {
            return pruneWhile(stmt);
        }

        return stmt;
    }

    private SastNode pruneBlock(SastNode block) {
        List<SastNode> out = null;

        for (int i = 0; i < block.statements.size(); i++) {
            SastNode stmt = block.statements.get(i);
            SastNode pruned = pruneStmt(stmt);

            if (out == null && pruned != stmt) {
                out = new ArrayList<>(block.statements.subList(0, i));
            }

            if (out != null && pruned != null) {
                out.add(pruned);
            }
        }

        return out == null ? block : SastNode.block(block.span, out);
    }

    private List<SastNode> pruneStmtList(List<SastNode> stmts) {
        List<SastNode> out = null;

        for (int i = 0; i < stmts.size(); i++) {
            SastNode stmt = stmts.get(i);
            SastNode pruned = pruneStmt(stmt);

            if (out == null && pruned != stmt) {
                out = new ArrayList<>(stmts.subList(0, i));
            }

            if (out != null && pruned != null) {
                out.add(pruned);
            }
        }

        return out == null ? stmts : out;
    }

    private SastNode pruneIf(SastNode node) {

        switch (determine(node.condition)) {

            case TRUE -> {
                return pruneStmt(node.thenBranch);
            }

            case FALSE -> {
                return node.elseBranch == null ? null : pruneStmt(node.elseBranch);
            }

            default -> {
                SastNode thenBranch = pruneStmt(node.thenBranch);
                SastNode elseBranch =
                        node.elseBranch == null ? null : pruneStmt(node.elseBranch);

                if (thenBranch == node.thenBranch && elseBranch == node.elseBranch) {
                    return node;
                }

                return SastNode.ifStmt(
                        node.span,
                        node.condition,
                        thenBranch == null ? emptyBlock(node.span) : thenBranch,
                        elseBranch
                );
            }
        }
    }

    private SastNode pruneWhile(SastNode node) {

        Determination determination = determine(node.condition);

        if (determination == Determination.FALSE) {
            return null;
        }

        SastNode body = pruneStmt(node.body);

        if (body == null) {
            body = emptyBlock(node.span);
        }

        List<SastNode> update = pruneStmtList(node.update);

        if (determination == Determination.TRUE) {
            SastNode trueLiteral = SastNode.intLiteral(
                    node.condition.span,
                    SastType.primitive(SemanticPrimitiveKind.UINT8),
                    BigInteger.ONE,
                    "1"
            );

            return SastNode.whileStmt(node.span, trueLiteral, body, update);
        }

        if (body == node.body && update == node.update) {
            return node;
        }

        return SastNode.whileStmt(node.span, node.condition, body, update);
    }

    private static SastNode emptyBlock(SourceSpan span) {
        return SastNode.block(span, List.of());
    }

    private static Determination determine(SastNode condition) {

        if (condition.isConstant()) {
            return condition.constantValue.signum() == 0
                    ? Determination.FALSE
                    : Determination.TRUE;
        }

        if (containsCall(condition)) {
            return Determination.UNPRUNABLE;
        }

        List<SastNode> atoms = new ArrayList<>();
        Node formula = classify(condition, atoms);

        if (formula == null || atoms.size() > MAX_ATOMS) {
            return Determination.UNPRUNABLE;
        }

        boolean sawTrue = false;
        boolean sawFalse = false;
        int rows = 1 << atoms.size();

        for (int mask = 0; mask < rows; mask++) {

            if (eval(formula, mask)) {
                sawTrue = true;
            } else {
                sawFalse = true;
            }

            if (sawTrue && sawFalse) {
                return Determination.UNDECIDED;
            }
        }

        return sawTrue ? Determination.TRUE : Determination.FALSE;
    }

    private static Node classify(SastNode expr, List<SastNode> atoms) {

        if (expr.isConstant()) {
            return new Lit(expr.constantValue.signum() != 0);
        }

        if (expr.kind == SastKind.BIN_OP) {

            if (expr.op.equals("&&")) {
                Node left = classify(expr.left, atoms);
                Node right = classify(expr.right, atoms);

                return left == null || right == null ? null : new And(left, right);
            }

            if (expr.op.equals("||")) {
                Node left = classify(expr.left, atoms);
                Node right = classify(expr.right, atoms);

                return left == null || right == null ? null : new Or(left, right);
            }
        }

        if (expr.kind == SastKind.UNARY_OP && expr.op.equals("!")) {
            Node inner = classify(expr.expr, atoms);

            return inner == null ? null : new Not(inner);
        }

        return atomFor(expr, atoms);
    }

    private static Node atomFor(SastNode expr, List<SastNode> atoms) {

        if (!TypeChecker.isByteType(expr.type) || !isEffectFree(expr)) {
            return null;
        }

        if (expr.kind == SastKind.BIN_OP
                && (expr.op.equals("==") || expr.op.equals("!="))) {

            boolean complement = expr.op.equals("!=");

            for (int i = 0; i < atoms.size(); i++) {

                if (sameComparisonOperands(atoms.get(i), expr)) {
                    Node base = new Var(i);

                    return complement ? new Not(base) : base;
                }
            }

            atoms.add(expr);

            Node base = new Var(atoms.size() - 1);

            return complement ? new Not(base) : base;
        }

        for (int i = 0; i < atoms.size(); i++) {

            if (sameAtom(atoms.get(i), expr)) {
                return new Var(i);
            }
        }

        atoms.add(expr);

        return new Var(atoms.size() - 1);
    }

    private static boolean sameComparisonOperands(
            SastNode existing,
            SastNode bin
    ) {
        return existing.kind == SastKind.BIN_OP
                && (existing.op.equals("==") || existing.op.equals("!="))
                && sameAtom(existing.left, bin.left)
                && sameAtom(existing.right, bin.right);
    }

    private static boolean eval(Node node, int mask) {

        if (node instanceof Lit lit) {
            return lit.value();
        }

        if (node instanceof Var var) {
            return (mask & (1 << var.index())) != 0;
        }

        if (node instanceof Not not) {
            return !eval(not.inner(), mask);
        }

        if (node instanceof And and) {
            return eval(and.left(), mask) && eval(and.right(), mask);
        }

        if (node instanceof Or or) {
            return eval(or.left(), mask) || eval(or.right(), mask);
        }

        throw new IllegalStateException("Unknown formula node: " + node);
    }

    private static boolean containsCall(SastNode expr) {

        if (expr.kind == SastKind.FUNCTION_CALL
                || expr.kind == SastKind.ARRAY_INTRINSIC_CALL) {
            return true;
        }

        if (expr.kind == SastKind.UNARY_OP) {
            return containsCall(expr.expr);
        }

        if (expr.kind == SastKind.BIN_OP) {
            return containsCall(expr.left) || containsCall(expr.right);
        }

        if (expr.kind == SastKind.ARRAY_ACCESS) {
            return containsCall(expr.target) || containsCall(expr.index);
        }

        if (expr.kind == SastKind.MEMBER_ACCESS) {
            return containsCall(expr.target);
        }

        if (expr.kind == SastKind.GET_PTR) {
            return containsCall(expr.target);
        }

        if (expr.kind == SastKind.DEREF) {
            return containsCall(expr.expr);
        }

        if (expr.kind == SastKind.CAST) {
            return containsCall(expr.expr);
        }

        return false;
    }

    private static boolean isEffectFree(SastNode expr) {

        if (expr.kind == SastKind.FUNCTION_CALL
                || expr.kind == SastKind.ARRAY_INTRINSIC_CALL) {
            return false;
        }

        if (expr.kind == SastKind.VAR_EXPR
                || expr.kind == SastKind.INT_LITERAL
                || expr.kind == SastKind.NULL_LITERAL) {
            return true;
        }

        if (expr.kind == SastKind.UNARY_OP) {
            return isEffectFree(expr.expr);
        }

        if (expr.kind == SastKind.BIN_OP) {
            return isEffectFree(expr.left) && isEffectFree(expr.right);
        }

        if (expr.kind == SastKind.ARRAY_ACCESS) {
            return isEffectFree(expr.target) && isEffectFree(expr.index);
        }

        if (expr.kind == SastKind.MEMBER_ACCESS) {
            return isEffectFree(expr.target);
        }

        if (expr.kind == SastKind.GET_PTR) {
            return isEffectFree(expr.target);
        }

        if (expr.kind == SastKind.DEREF) {
            return isEffectFree(expr.expr);
        }

        if (expr.kind == SastKind.CAST) {
            return isEffectFree(expr.expr);
        }

        return false;
    }

    private static boolean sameAtom(SastNode a, SastNode b) {

        if (a == b) {
            return true;
        }

        if (a.kind == SastKind.VAR_EXPR && b.kind == SastKind.VAR_EXPR) {
            return a.symbol == b.symbol;
        }

        if (a.kind == SastKind.INT_LITERAL && b.kind == SastKind.INT_LITERAL) {
            return Objects.equals(a.constantValue, b.constantValue)
                    && a.type.equals(b.type);
        }

        if (a.kind == SastKind.NULL_LITERAL && b.kind == SastKind.NULL_LITERAL) {
            return true;
        }

        if (a.kind == SastKind.BIN_OP && b.kind == SastKind.BIN_OP) {
            return a.op.equals(b.op)
                    && sameAtom(a.left, b.left)
                    && sameAtom(a.right, b.right);
        }

        if (a.kind == SastKind.UNARY_OP && b.kind == SastKind.UNARY_OP) {
            return a.op.equals(b.op) && sameAtom(a.expr, b.expr);
        }

        if (a.kind == SastKind.ARRAY_ACCESS && b.kind == SastKind.ARRAY_ACCESS) {
            return sameAtom(a.target, b.target) && sameAtom(a.index, b.index);
        }

        if (a.kind == SastKind.MEMBER_ACCESS && b.kind == SastKind.MEMBER_ACCESS) {
            return a.field == b.field && sameAtom(a.target, b.target);
        }

        if (a.kind == SastKind.GET_PTR && b.kind == SastKind.GET_PTR) {
            return sameAtom(a.target, b.target);
        }

        if (a.kind == SastKind.DEREF && b.kind == SastKind.DEREF) {
            return sameAtom(a.expr, b.expr);
        }

        if (a.kind == SastKind.CAST && b.kind == SastKind.CAST) {
            return a.checked == b.checked
                    && a.type.equals(b.type)
                    && sameAtom(a.expr, b.expr);
        }

        return false;
    }
}
