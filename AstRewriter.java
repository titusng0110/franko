import java.util.ArrayList;
import java.util.List;

abstract class AstRewriter {

    AstNode rewrite(AstNode node) {
        if (node == null) {
            return null;
        }

        AstNode replacement = rewriteNode(node);

        if (replacement == null) {
            return null;
        }

        if (replacement != node) {
            return rewrite(replacement);
        }

        node.type = rewrite(node.type);
        node.target = rewrite(node.target);
        node.value = rewrite(node.value);
        node.condition = rewrite(node.condition);
        node.thenBranch = rewrite(node.thenBranch);
        node.elseBranch = rewrite(node.elseBranch);
        node.body = rewrite(node.body);
        node.expr = rewrite(node.expr);
        node.left = rewrite(node.left);
        node.right = rewrite(node.right);
        node.index = rewrite(node.index);
        node.callee = rewrite(node.callee);
        node.init = rewrite(node.init);
        node.update = rewrite(node.update);
        node.level = rewrite(node.level);
        node.sizeExpr = rewrite(node.sizeExpr);

        node.items = rewriteList(node.items);
        node.parameters = rewriteList(node.parameters);
        node.fields = rewriteList(node.fields);
        node.methods = rewriteList(node.methods);
        node.members = rewriteList(node.members);
        node.variants = rewriteList(node.variants);
        node.args = rewriteList(node.args);
        node.elements = rewriteList(node.elements);
        node.thenItems = rewriteList(node.thenItems);
        node.elseItems = rewriteList(node.elseItems);
        node.updateItems = rewriteList(node.updateItems);
        node.genericParams = rewriteList(node.genericParams);
        node.genericArgs = rewriteList(node.genericArgs);

        return node;
    }

    abstract AstNode rewriteNode(AstNode node);

    private List<AstNode> rewriteList(List<AstNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return nodes;
        }

        List<AstNode> out = new ArrayList<>(nodes.size());

        for (AstNode node : nodes) {
            AstNode rewritten = rewrite(node);

            if (rewritten != null) {
                out.add(rewritten);
            }
        }

        return List.copyOf(out);
    }
}
