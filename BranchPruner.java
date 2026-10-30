import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class BranchPruner {

    private static final int MAX_ATOMS = 12;

    private static final SemanticIntLiteralNode TRUE_LITERAL =
            new SemanticIntLiteralNode(
                    new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8),
                    BigInteger.ONE,
                    "1"
            );

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

    SemanticASTNode prune(SemanticASTNode root) {
        return pruneItem(root);
    }

    private SemanticASTNode pruneItem(SemanticASTNode node) {

        if (node instanceof SemanticProgramNode program) {
            List<SemanticASTNode> items = pruneItemList(program.topLevelItems);

            return items == program.topLevelItems
                    ? program
                    : new SemanticProgramNode(items);
        }

        if (node instanceof SemanticNamespaceNode namespace) {
            List<SemanticASTNode> items = pruneItemList(namespace.items);

            return items == namespace.items
                    ? namespace
                    : new SemanticNamespaceNode(namespace.name, items);
        }

        if (node instanceof SemanticFunctionDeclNode fn) {
            SemanticBlockNode body = pruneBlock(fn.body);

            return body == fn.body
                    ? fn
                    : new SemanticFunctionDeclNode(fn.symbol, fn.parameterVariables, body);
        }

        if (node instanceof SemanticStructDeclNode struct) {
            List<SemanticFunctionDeclNode> methods = null;

            for (int i = 0; i < struct.methods.size(); i++) {
                SemanticFunctionDeclNode method = struct.methods.get(i);
                SemanticFunctionDeclNode pruned =
                        (SemanticFunctionDeclNode) pruneItem(method);

                if (methods == null && pruned != method) {
                    methods = new ArrayList<>(struct.methods.subList(0, i));
                }

                if (methods != null) {
                    methods.add(pruned);
                }
            }

            return methods == null
                    ? struct
                    : new SemanticStructDeclNode(struct.symbol, methods);
        }

        return node;
    }

    private List<SemanticASTNode> pruneItemList(List<SemanticASTNode> items) {
        List<SemanticASTNode> out = null;

        for (int i = 0; i < items.size(); i++) {
            SemanticASTNode item = items.get(i);
            SemanticASTNode pruned = pruneItem(item);

            if (out == null && pruned != item) {
                out = new ArrayList<>(items.subList(0, i));
            }

            if (out != null) {
                out.add(pruned);
            }
        }

        return out == null ? items : out;
    }

    private SemanticStmtNode pruneStmt(SemanticStmtNode stmt) {

        if (stmt instanceof SemanticBlockNode block) {
            return pruneBlock(block);
        }

        if (stmt instanceof SemanticIfNode ifNode) {
            return pruneIf(ifNode);
        }

        if (stmt instanceof SemanticWhileNode whileNode) {
            return pruneWhile(whileNode);
        }

        return stmt;
    }

    private SemanticBlockNode pruneBlock(SemanticBlockNode block) {
        List<SemanticStmtNode> out = null;

        for (int i = 0; i < block.statements.size(); i++) {
            SemanticStmtNode stmt = block.statements.get(i);
            SemanticStmtNode pruned = pruneStmt(stmt);

            if (out == null && pruned != stmt) {
                out = new ArrayList<>(block.statements.subList(0, i));
            }

            if (out != null && pruned != null) {
                out.add(pruned);
            }
        }

        return out == null ? block : new SemanticBlockNode(out);
    }

    private List<SemanticStmtNode> pruneStmtList(List<SemanticStmtNode> stmts) {
        List<SemanticStmtNode> out = null;

        for (int i = 0; i < stmts.size(); i++) {
            SemanticStmtNode stmt = stmts.get(i);
            SemanticStmtNode pruned = pruneStmt(stmt);

            if (out == null && pruned != stmt) {
                out = new ArrayList<>(stmts.subList(0, i));
            }

            if (out != null && pruned != null) {
                out.add(pruned);
            }
        }

        return out == null ? stmts : out;
    }

    private SemanticStmtNode pruneIf(SemanticIfNode node) {

        switch (determine(node.condition)) {

            case TRUE -> {
                return pruneStmt(node.thenBranch);
            }

            case FALSE -> {
                return node.elseBranch == null ? null : pruneStmt(node.elseBranch);
            }

            default -> {
                SemanticStmtNode thenBranch = pruneStmt(node.thenBranch);
                SemanticStmtNode elseBranch =
                        node.elseBranch == null ? null : pruneStmt(node.elseBranch);

                if (thenBranch == node.thenBranch && elseBranch == node.elseBranch) {
                    return node;
                }

                return new SemanticIfNode(
                        node.condition,
                        thenBranch == null ? emptyBlock() : thenBranch,
                        elseBranch
                );
            }
        }
    }

    private SemanticStmtNode pruneWhile(SemanticWhileNode node) {

        Determination determination = determine(node.condition);

        if (determination == Determination.FALSE) {
            return null;
        }

        SemanticStmtNode body = pruneStmt(node.body);

        if (body == null) {
            body = emptyBlock();
        }

        List<SemanticStmtNode> update = pruneStmtList(node.update);

        if (determination == Determination.TRUE) {
            return new SemanticWhileNode(TRUE_LITERAL, body, update);
        }

        if (body == node.body && update == node.update) {
            return node;
        }

        return new SemanticWhileNode(node.condition, body, update);
    }

    private static SemanticBlockNode emptyBlock() {
        return new SemanticBlockNode(List.of());
    }

    private static Determination determine(SemanticExprNode condition) {

        if (condition.isConstant()) {
            return condition.constantValue.signum() == 0
                    ? Determination.FALSE
                    : Determination.TRUE;
        }

        if (containsCall(condition)) {
            return Determination.UNPRUNABLE;
        }

        List<SemanticExprNode> atoms = new ArrayList<>();
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

    private static Node classify(SemanticExprNode expr, List<SemanticExprNode> atoms) {

        if (expr.isConstant()) {
            return new Lit(expr.constantValue.signum() != 0);
        }

        if (expr instanceof SemanticBinOpNode bin) {

            if (bin.op.equals("&&")) {
                Node left = classify(bin.left, atoms);
                Node right = classify(bin.right, atoms);

                return left == null || right == null ? null : new And(left, right);
            }

            if (bin.op.equals("||")) {
                Node left = classify(bin.left, atoms);
                Node right = classify(bin.right, atoms);

                return left == null || right == null ? null : new Or(left, right);
            }
        }

        if (expr instanceof SemanticUnaryOpNode unary && unary.op.equals("!")) {
            Node inner = classify(unary.expr, atoms);

            return inner == null ? null : new Not(inner);
        }

        return atomFor(expr, atoms);
    }

    private static Node atomFor(SemanticExprNode expr, List<SemanticExprNode> atoms) {

        if (!isBooleanTyped(expr.type) || !isEffectFree(expr)) {
            return null;
        }

        if (expr instanceof SemanticBinOpNode bin
                && (bin.op.equals("==") || bin.op.equals("!="))) {

            boolean complement = bin.op.equals("!=");

            for (int i = 0; i < atoms.size(); i++) {

                if (sameComparisonOperands(atoms.get(i), bin)) {
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
            SemanticExprNode existing,
            SemanticBinOpNode bin
    ) {
        return existing instanceof SemanticBinOpNode other
                && (other.op.equals("==") || other.op.equals("!="))
                && sameAtom(other.left, bin.left)
                && sameAtom(other.right, bin.right);
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

    private static boolean containsCall(SemanticExprNode expr) {

        if (expr instanceof SemanticFunctionCallNode
                || expr instanceof SemanticArrayIntrinsicCallNode) {
            return true;
        }

        if (expr instanceof SemanticUnaryOpNode unary) {
            return containsCall(unary.expr);
        }

        if (expr instanceof SemanticBinOpNode bin) {
            return containsCall(bin.left) || containsCall(bin.right);
        }

        if (expr instanceof SemanticArrayAccessNode access) {
            return containsCall(access.target) || containsCall(access.index);
        }

        if (expr instanceof SemanticMemberAccessNode member) {
            return containsCall(member.target);
        }

        if (expr instanceof SemanticGetPtrNode getPtr) {
            return containsCall(getPtr.target);
        }

        if (expr instanceof SemanticDerefNode deref) {
            return containsCall(deref.expr);
        }

        if (expr instanceof SemanticCastExprNode cast) {
            return containsCall(cast.expr);
        }

        return false;
    }

    private static boolean isEffectFree(SemanticExprNode expr) {

        if (expr instanceof SemanticFunctionCallNode
                || expr instanceof SemanticArrayIntrinsicCallNode) {
            return false;
        }

        if (expr instanceof SemanticVarExprNode
                || expr instanceof SemanticIntLiteralNode
                || expr instanceof SemanticNullLiteralNode) {
            return true;
        }

        if (expr instanceof SemanticUnaryOpNode unary) {
            return isEffectFree(unary.expr);
        }

        if (expr instanceof SemanticBinOpNode bin) {
            return isEffectFree(bin.left) && isEffectFree(bin.right);
        }

        if (expr instanceof SemanticArrayAccessNode access) {
            return isEffectFree(access.target) && isEffectFree(access.index);
        }

        if (expr instanceof SemanticMemberAccessNode member) {
            return isEffectFree(member.target);
        }

        if (expr instanceof SemanticGetPtrNode getPtr) {
            return isEffectFree(getPtr.target);
        }

        if (expr instanceof SemanticDerefNode deref) {
            return isEffectFree(deref.expr);
        }

        if (expr instanceof SemanticCastExprNode cast) {
            return isEffectFree(cast.expr);
        }

        return false;
    }

    private static boolean isBooleanTyped(SemanticType type) {
        return type instanceof SemanticPrimitiveType primitive
                && primitive.kind == SemanticPrimitiveKind.UINT8;
    }

    private static boolean sameAtom(SemanticExprNode a, SemanticExprNode b) {

        if (a == b) {
            return true;
        }

        if (a instanceof SemanticVarExprNode va && b instanceof SemanticVarExprNode vb) {
            return va.symbol == vb.symbol;
        }

        if (a instanceof SemanticIntLiteralNode ia && b instanceof SemanticIntLiteralNode ib) {
            return Objects.equals(ia.constantValue, ib.constantValue)
                    && ia.type.equals(ib.type);
        }

        if (a instanceof SemanticNullLiteralNode && b instanceof SemanticNullLiteralNode) {
            return true;
        }

        if (a instanceof SemanticBinOpNode ba && b instanceof SemanticBinOpNode bb) {
            return ba.op.equals(bb.op)
                    && sameAtom(ba.left, bb.left)
                    && sameAtom(ba.right, bb.right);
        }

        if (a instanceof SemanticUnaryOpNode ua && b instanceof SemanticUnaryOpNode ub) {
            return ua.op.equals(ub.op) && sameAtom(ua.expr, ub.expr);
        }

        if (a instanceof SemanticArrayAccessNode aa && b instanceof SemanticArrayAccessNode ab) {
            return sameAtom(aa.target, ab.target) && sameAtom(aa.index, ab.index);
        }

        if (a instanceof SemanticMemberAccessNode ma && b instanceof SemanticMemberAccessNode mb) {
            return ma.field == mb.field && sameAtom(ma.target, mb.target);
        }

        if (a instanceof SemanticGetPtrNode ga && b instanceof SemanticGetPtrNode gb) {
            return sameAtom(ga.target, gb.target);
        }

        if (a instanceof SemanticDerefNode da && b instanceof SemanticDerefNode db) {
            return sameAtom(da.expr, db.expr);
        }

        if (a instanceof SemanticCastExprNode ca && b instanceof SemanticCastExprNode cb) {
            return ca.checked == cb.checked
                    && ca.type.equals(cb.type)
                    && sameAtom(ca.expr, cb.expr);
        }

        return false;
    }
}
