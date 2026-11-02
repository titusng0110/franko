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

        Map<String, SourceSpan> variables = new LinkedHashMap<>();
        Map<String, SourceSpan> structs = new LinkedHashMap<>();
        Map<String, SourceSpan> functions = new LinkedHashMap<>();

        collectItems(
                root.topLevelItems,
                null,
                variables,
                structs,
                functions
        );

        Set<String> seenVariables = new LinkedHashSet<>();
        Set<String> seenStructs = new LinkedHashSet<>();
        Set<String> seenFunctions = new LinkedHashSet<>();

        for (ModuleVariable variable : module.variables) {
            String name = variable.qualifiedName();
            requireUnique(module, "variable", name, seenVariables, diagnostics);
            requireDeclared(module, "variable", name, variables, diagnostics);
        }

        for (ModuleStruct struct : module.structs) {
            String name = struct.qualifiedName();
            requireUnique(module, "struct", name, seenStructs, diagnostics);
            requireDeclared(module, "struct", name, structs, diagnostics);
        }

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
            Map<String, SourceSpan> variables,
            Map<String, SourceSpan> structs,
            Map<String, SourceSpan> functions
    ) {
        for (ASTNode item : items) {
            if (item instanceof NamespaceNode namespace) {
                String fullName = parentNamespace == null
                        ? namespace.name
                        : parentNamespace + "::" + namespace.name;
                collectItems(
                        namespace.items,
                        fullName,
                        variables,
                        structs,
                        functions
                );
                continue;
            }

            if (item instanceof GlobalVarDeclNode global) {
                variables.put(qualified(parentNamespace, global.name), global.span);
                continue;
            }

            if (item instanceof StructDeclNode struct) {
                structs.put(qualified(parentNamespace, struct.name), struct.span);
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
