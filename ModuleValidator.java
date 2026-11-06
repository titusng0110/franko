import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ModuleValidator {

    private ModuleValidator() {
    }

    static List<Diagnostic> validate(AstNode root, ModuleDescriptor module) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (root == null || module == null) {
            return diagnostics;
        }

        Map<String, SourceSpan> functions = new LinkedHashMap<>();

        collectItems(root.items, null, functions);

        Set<String> seenFunctions = new LinkedHashSet<>();

        for (ModuleFunction function : module.functions) {
            String name = function.qualifiedName();
            requireUnique(module, "function", name, seenFunctions, diagnostics);
            requireDeclared(module, "function", name, functions, diagnostics);
        }

        return diagnostics;
    }

    private static void collectItems(
            List<AstNode> items,
            String parentNamespace,
            Map<String, SourceSpan> functions
    ) {
        for (AstNode item : items) {
            if (item.kind == AstKind.NAMESPACE) {
                String fullName = parentNamespace == null
                        ? item.name
                        : parentNamespace + "::" + item.name;
                collectItems(item.items, fullName, functions);
                continue;
            }

            if (item.kind == AstKind.DECL_IF) {
                collectItems(item.thenItems, parentNamespace, functions);
                collectItems(item.elseItems, parentNamespace, functions);
                continue;
            }

            if (item.kind == AstKind.FUNCTION_DECL) {
                functions.put(qualified(parentNamespace, item.name), item.span);
            }
        }
    }

    private static void requireUnique(
            ModuleDescriptor module,
            String kind,
            String name,
            Set<String> seen,
            List<Diagnostic> diagnostics
    ) {
        if (!seen.add(name)) {
            diagnostics.add(Diagnostic.error(
                    "Module '" + module.id + "': duplicate " + kind
                            + " descriptor for '" + name + "'"
            ));
        }
    }

    private static void requireDeclared(
            ModuleDescriptor module,
            String kind,
            String name,
            Map<String, SourceSpan> declarations,
            List<Diagnostic> diagnostics
    ) {
        if (!declarations.containsKey(name)) {
            diagnostics.add(Diagnostic.error(
                    "Module '" + module.id + "': " + kind + " '" + name
                            + "' is not declared in " + module.sourcePath
            ));
        }
    }

    private static String qualified(String namespace, String name) {
        return namespace == null ? name : namespace + "::" + name;
    }
}
