final class EarlyTargetFolder extends AstRewriter {

    private final TargetInfo target;

    EarlyTargetFolder(TargetInfo target) {
        this.target = target;
    }

    static AstNode fold(AstNode root, TargetInfo target) {
        return new EarlyTargetFolder(target).rewrite(root);
    }

    @Override
    AstNode rewriteNode(AstNode node) {
        if (node.kind != AstKind.COMPILE_TIME_NAME) {
            return node;
        }

        Boolean enabled = targetValue(node.name);

        if (enabled == null) {
            return node;
        }

        return AstNode.typedIntLiteral(
                node.span,
                enabled ? "1" : "0",
                PrimitiveKind.UINT8
        );
    }

    private Boolean targetValue(String name) {
        if ("IS_LINUX".equals(name)) {
            return target.isLinux();
        }

        if ("IS_WINDOWS".equals(name)) {
            return target.isWindows();
        }

        if ("IS_MACOS".equals(name)) {
            return target.isMacos();
        }

        if ("IS_X86_64".equals(name)) {
            return target.isX86_64();
        }

        if ("IS_ARM64".equals(name)) {
            return target.isArm64();
        }

        return null;
    }
}
