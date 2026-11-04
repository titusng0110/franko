import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ModuleValidator {

    private ModuleValidator() {
    }

    static List<Diagnostic> validate(ProgramNode root, ModuleDescriptor module) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (root == null || module == null) {
            return diagnostics;
        }

        Map<String, SourceSpan> functions = new LinkedHashMap<>();

        collectItems(root.topLevelItems, null, functions);

        Set<String> seenFunctions = new LinkedHashSet<>();

        for (ModuleFunction function : module.functions) {
            String name = function.qualifiedName();
            requireUnique(module, "function", name, seenFunctions, diagnostics);
            requireDeclared(module, "function", name, functions, diagnostics);
        }

        return diagnostics;
    }

    private static void collectItems(
            List<ASTNode> items,
            String parentNamespace,
            Map<String, SourceSpan> functions
    ) {
        for (ASTNode item : items) {
            if (item instanceof NamespaceNode namespace) {
                String fullName = parentNamespace == null
                        ? namespace.name
                        : parentNamespace + "::" + namespace.name;
                collectItems(namespace.items, fullName, functions);
                continue;
            }

            if (item instanceof DeclIfNode declIf) {
                collectItems(declIf.thenItems, parentNamespace, functions);
                collectItems(declIf.elseItems, parentNamespace, functions);
                continue;
            }

            if (item instanceof FunctionDeclNode function) {
                functions.put(qualified(parentNamespace, function.name), function.span);
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
