import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class StringLiteralFolder extends AstRewriter {

    static AstNode fold(AstNode root) {
        return new StringLiteralFolder().rewrite(root);
    }

    static boolean isStringExpression(AstNode node) {
        if (node == null) {
            return false;
        }

        if (node.kind == AstKind.STRING_LITERAL) {
            return true;
        }

        if (node.kind == AstKind.COMPILE_TIME_CALL) {
            return "what_type".equals(node.name);
        }

        if (node.kind == AstKind.BINARY && "+".equals(node.text)) {
            return isStringExpression(node.left)
                    && isStringExpression(node.right);
        }

        return false;
    }

    @Override
    AstNode rewrite(AstNode node) {
        if (node == null) {
            return null;
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
        node.defaultValue = rewrite(node.defaultValue);

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

        return rewriteNode(node);
    }

    @Override
    AstNode rewriteNode(AstNode node) {
        if (node.kind != AstKind.BINARY || node.left == null || node.right == null) {
            return node;
        }

        if (node.left.kind != AstKind.STRING_LITERAL
                || node.right.kind != AstKind.STRING_LITERAL) {
            return node;
        }

        List<BigInteger> left;
        List<BigInteger> right;

        try {
            left = StringLiteralDecoder.toUtf8Bytes(node.left);
            right = StringLiteralDecoder.toUtf8Bytes(node.right);
        } catch (RuntimeException e) {
            return node;
        }

        if ("+".equals(node.text)) {
            return AstNode.stringLiteralBytes(
                    node.span,
                    StringConstantOps.concat(left, right)
            );
        }

        if (StringConstantOps.isComparison(node.text)) {
            BigInteger value = StringConstantOps.foldComparison(
                    node.text,
                    left,
                    right
            );

            return AstNode.typedIntLiteral(
                    node.span,
                    value.toString(10),
                    PrimitiveKind.UINT8
            );
        }

        return node;
    }

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
