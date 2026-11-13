final class PostGenericsCompileTimeFolder extends AstRewriter {

    private final TargetInfo target;

    PostGenericsCompileTimeFolder(TargetInfo target) {
        this.target = target;
    }

    static AstNode fold(AstNode root, TargetInfo target) {
        if (target == null) {
            return root;
        }

        return new PostGenericsCompileTimeFolder(target).rewrite(root);
    }

    @Override
    AstNode rewriteNode(AstNode node) {
        if (node.kind == AstKind.COMPILE_TIME_NAME) {
            Boolean enabled = CompileTimeIntrinsicCore.targetValue(
                    node.name,
                    target
            );

            if (enabled != null) {
                return AstNode.typedIntLiteral(
                        node.span,
                        enabled ? "1" : "0",
                        PrimitiveKind.UINT8
                );
            }

            return node;
        }

        if (node.kind == AstKind.COMPILE_TIME_CALL) {
            CompileTimeIntrinsicCore.RawValue value =
                    CompileTimeIntrinsicCore.tryEvaluateCall(node, target);

            if (value != null
                    && value.kind == CompileTimeIntrinsicCore.RawKind.INT) {
                return AstNode.typedIntLiteral(
                        node.span,
                        value.value.toString(10),
                        CompileTimeIntrinsicCore.integerResultKind(node.name)
                );
            }
        }

        return node;
    }
}
