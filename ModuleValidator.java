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

        Map<String, List<SourceSpan>> functions = new LinkedHashMap<>();
        Map<String, SourceSpan> genericFunctions = new LinkedHashMap<>();
        Map<String, AstNode> functionNodes = new LinkedHashMap<>();

        collectItems(root.items, null, functions, genericFunctions, functionNodes);

        Set<String> seenFunctions = new LinkedHashSet<>();

        for (ModuleFunction function : module.functions) {
            String name = function.qualifiedName();
            requireUnique(module, "function", name, seenFunctions, diagnostics);

            if (genericFunctions.containsKey(name)) {
                diagnostics.add(Diagnostic.error(
                        genericFunctions.get(name),
                        "Module '" + module.id + "': generic function '" + name
                                + "' cannot be FFI-bridged"
                                + ". A generic function has no single concrete"
                                + " C++ signature for a bridge to define. Make"
                                + " the function non-generic or remove it from"
                                + " the module's function list."
                ));
                continue;
            }

            List<SourceSpan> declarations = functions.get(name);

            if (declarations == null || declarations.isEmpty()) {
                requireDeclared(module, "function", name, functions, diagnostics);
                continue;
            }

            AstNode decl = functionNodes.get(name);

            if (decl != null && decl.type != null && decl.type.kind == AstKind.PTR_TYPE) {
                diagnostics.add(Diagnostic.error(
                        decl.span,
                        "Module '" + module.id + "': bridged function '" + name
                                + "' cannot return a pointer type."
                                + " Pointers are downward-only values and cannot flow back up through returns."
                                + " Change the return type to a primitive, enum, or value struct/tuple, or write the result through an out-parameter that points at non-pointer storage."
                ));
            }
        }

        return diagnostics;
    }

    private static void collectItems(
            List<AstNode> items,
            String parentNamespace,
            Map<String, List<SourceSpan>> functions,
            Map<String, SourceSpan> genericFunctions,
            Map<String, AstNode> functionNodes
    ) {
        for (AstNode item : items) {
            if (item.kind == AstKind.NAMESPACE) {
                String fullName = parentNamespace == null
                        ? item.name
                        : parentNamespace + "::" + item.name;
                collectItems(item.items, fullName, functions, genericFunctions, functionNodes);
                continue;
            }

            if (item.kind == AstKind.DECL_IF) {
                collectItems(item.thenItems, parentNamespace, functions, genericFunctions, functionNodes);
                collectItems(item.elseItems, parentNamespace, functions, genericFunctions, functionNodes);
                continue;
            }

            if (item.kind == AstKind.FUNCTION_DECL) {
                String name = qualified(parentNamespace, item.name);

                if (item.isGeneric) {
                    genericFunctions.put(name, item.span);
                } else {
                    functions.computeIfAbsent(
                            name,
                            ignored -> new ArrayList<>()
                    ).add(item.span);
                    functionNodes.putIfAbsent(name, item);
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
                            + ". Duplicate entries make the FFI surface"
                            + " ambiguous and can define the same symbol twice."
                            + " Remove one of the duplicate entries from the"
                            + " module manifest."
            ));
        }
    }

    private static void requireDeclared(
            ModuleDescriptor module,
            String kind,
            String name,
            Map<String, List<SourceSpan>> declarations,
            List<Diagnostic> diagnostics
    ) {
        if (!declarations.containsKey(name)) {
            diagnostics.add(Diagnostic.error(
                    "Module '" + module.id + "': " + kind + " '" + name
                            + "' is not declared in " + module.sourcePath
                            + ". The manifest may only expose functions the"
                            + " module actually declares. Add the function to"
                            + " the source file or remove the manifest entry."
            ));
        }
    }

    private static String qualified(String namespace, String name) {
        return namespace == null ? name : namespace + "::" + name;
    }
}
