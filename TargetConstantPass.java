import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TargetConstantPass {

    private TargetConstantPass() {
    }

    static List<Diagnostic> apply(
            AstDocument document,
            TargetInfo target,
            ModuleDescriptor module
    ) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (document == null || document.root == null || module == null) {
            return diagnostics;
        }

        Map<String, ModuleVariable> bindings = new LinkedHashMap<>();

        for (ModuleVariable variable : module.variables) {
            if (variable.target != null || variable.compileTimeConstant) {
                bindings.put(variable.qualifiedName(), variable);
            }
        }

        if (bindings.isEmpty()) {
            return diagnostics;
        }

        Set<String> found = new HashSet<>();
        applyItems(
                document.root.topLevelItems,
                null,
                target,
                module,
                bindings,
                found,
                diagnostics
        );

        for (ModuleVariable variable : bindings.values()) {
            if (variable.target != null && !found.contains(variable.qualifiedName())) {
                diagnostics.add(Diagnostic.error(
                        "Module '" + module.id + "': target constant '"
                                + variable.qualifiedName()
                                + "' is not declared"
                ));
            }
        }

        return diagnostics;
    }

    private static void applyItems(
            List<ASTNode> items,
            String parentNamespace,
            TargetInfo target,
            ModuleDescriptor module,
            Map<String, ModuleVariable> bindings,
            Set<String> found,
            List<Diagnostic> diagnostics
    ) {
        for (ASTNode item : items) {
            if (item instanceof NamespaceNode namespace) {
                String fullName = parentNamespace == null
                        ? namespace.name
                        : parentNamespace + "::" + namespace.name;
                applyItems(
                        namespace.items,
                        fullName,
                        target,
                        module,
                        bindings,
                        found,
                        diagnostics
                );
                continue;
            }

            if (!(item instanceof GlobalVarDeclNode global)) {
                continue;
            }

            String qualifiedName = parentNamespace == null
                    ? global.name
                    : parentNamespace + "::" + global.name;
            ModuleVariable variable = bindings.get(qualifiedName);

            if (variable == null) {
                continue;
            }

            found.add(qualifiedName);

            if (variable.target != null) {
                if (target == null) {
                    diagnostics.add(Diagnostic.error(
                            global.span,
                            "Module '" + module.id + "': target constant '"
                                    + qualifiedName
                                    + "' requires a compilation target"
                    ));
                    continue;
                }

                long value = variable.target.resolve(target);

                if (!isIntegerType(global.type)) {
                    diagnostics.add(Diagnostic.error(
                            global.span,
                            "Module '" + module.id + "': target constant '"
                                    + qualifiedName
                                    + "' must be declared as an integer type"
                    ));
                    continue;
                }

                PrimitiveTypeNode primitive = (PrimitiveTypeNode) global.type;
                SemanticPrimitiveKind kind = SemanticPrimitiveKind.valueOf(
                        primitive.kind.name()
                );

                if (!TypeChecker.fitsBigIntegerToPrimitive(
                        BigInteger.valueOf(value),
                        kind
                )) {
                    diagnostics.add(Diagnostic.error(
                            global.span,
                            "Module '" + module.id + "': target constant '"
                                    + qualifiedName
                                    + "' value " + value
                                    + " does not fit " + primitive.kind.name()
                    ));
                    continue;
                }

                SourceSpan span = global.constInitializer == null
                        ? global.span
                        : global.constInitializer.span;
                global.constInitializer = new IntNode(
                        span,
                        Long.toString(value)
                );
            }

            if (variable.compileTimeConstant) {
                global.compileTimeConstant = true;
            }
        }
    }

    private static boolean isIntegerType(TypeNode type) {
        return type instanceof PrimitiveTypeNode;
    }
}
