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
        Map<String, SourceSpan> genericFunctions = new LinkedHashMap<>();

        collectItems(root.items, null, functions, genericFunctions);

        Set<String> seenFunctions = new LinkedHashSet<>();

        for (ModuleFunction function : module.functions) {
            String name = function.qualifiedName();
            requireUnique(module, "function", name, seenFunctions, diagnostics);

            if (genericFunctions.containsKey(name)
                    && !functions.containsKey(name)) {
                diagnostics.add(Diagnostic.error(
                        genericFunctions.get(name),
                        "Module '" + module.id + "': generic function '" + name
                                + "' cannot be FFI-bridged"
                ));
                continue;
            }

            requireDeclared(module, "function", name, functions, diagnostics);
        }

        return diagnostics;
    }

    private static void collectItems(
            List<AstNode> items,
            String parentNamespace,
            Map<String, SourceSpan> functions,
            Map<String, SourceSpan> genericFunctions
    ) {
        for (AstNode item : items) {
            if (item.kind == AstKind.NAMESPACE) {
                String fullName = parentNamespace == null
                        ? item.name
                        : parentNamespace + "::" + item.name;
                collectItems(item.items, fullName, functions, genericFunctions);
                continue;
            }

            if (item.kind == AstKind.DECL_IF) {
                collectItems(item.thenItems, parentNamespace, functions, genericFunctions);
                collectItems(item.elseItems, parentNamespace, functions, genericFunctions);
                continue;
            }

            if (item.kind == AstKind.FUNCTION_DECL) {
                String name = qualified(parentNamespace, item.name);

                if (item.isGeneric) {
                    genericFunctions.put(name, item.span);
                } else {
                    functions.put(name, item.span);
                }
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
