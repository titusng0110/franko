import java.util.ArrayList;
import java.util.List;

final class CompileTimeInjector {

    static final String STATIC_MODULE_PATH = "franko/static";
    static final String STATIC_MODULE_FILE_PATH = "franko/static.fr";
    static final String STATIC_MODULE_KEY = "<franko/static>";

    private final TargetInfo target;

    private NamespaceNode injectedStaticNamespace = null;

    CompileTimeInjector(TargetInfo target) {
        this.target = target;
    }

    static boolean isStaticModule(String pathText) {
        return STATIC_MODULE_PATH.equals(pathText)
                || STATIC_MODULE_FILE_PATH.equals(pathText);
    }

    NamespaceNode injectStaticNamespace(SourceSpan span) {
        if (injectedStaticNamespace != null) {
            return injectedStaticNamespace;
        }

        List<ASTNode> items = new ArrayList<>();

        items.add(targetConstant(span, "IS_LINUX", target.isLinux()));
        items.add(targetConstant(span, "IS_WINDOWS", target.isWindows()));
        items.add(targetConstant(span, "IS_MACOS", target.isMacos()));
        items.add(targetConstant(span, "IS_X86_64", target.isX86_64()));
        items.add(targetConstant(span, "IS_ARM64", target.isArm64()));

        injectedStaticNamespace = new NamespaceNode(
                span,
                "static",
                items
        );

        return injectedStaticNamespace;
    }

    private static GlobalVarDeclNode targetConstant(
            SourceSpan span,
            String name,
            boolean enabled
    ) {
        GlobalVarDeclNode decl = new GlobalVarDeclNode(
                span,
                new PrimitiveTypeNode(span, PrimitiveKind.UINT8),
                name,
                new IntNode(span, enabled ? "1" : "0")
        );

        decl.compileTimeConstant = true;

        return decl;
    }
}
