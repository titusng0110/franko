import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ModuleDescriptor {

    final String id;
    final Path manifestPath;
    final Path sourcePath;
    final Path bridgePath;
    final List<ModuleVariable> variables = new ArrayList<>();
    final List<ModuleStruct> structs = new ArrayList<>();
    final List<ModuleFunction> functions = new ArrayList<>();

    ModuleDescriptor(String id, Path manifestPath, Path sourcePath, Path bridgePath) {
        this.id = id;
        this.manifestPath = manifestPath;
        this.sourcePath = sourcePath;
        this.bridgePath = bridgePath;
    }

    ModuleVariable findVariable(String namespace, String name) {
        for (ModuleVariable variable : variables) {
            if (sameNamespace(variable.namespace, namespace)
                    && variable.name.equals(name)) {
                return variable;
            }
        }

        return null;
    }

    ModuleStruct findStruct(String namespace, String name) {
        for (ModuleStruct struct : structs) {
            if (sameNamespace(struct.namespace, namespace)
                    && struct.name.equals(name)) {
                return struct;
            }
        }

        return null;
    }

    ModuleFunction findFunction(String namespace, String name) {
        for (ModuleFunction function : functions) {
            if (sameNamespace(function.namespace, namespace)
                    && function.name.equals(name)) {
                return function;
            }
        }

        return null;
    }

    boolean hasBridgedEntities() {
        for (ModuleVariable variable : variables) {
            if (variable.bridge) {
                return true;
            }
        }

        for (ModuleStruct struct : structs) {
            if (struct.bridge) {
                return true;
            }
        }

        for (ModuleFunction function : functions) {
            if (function.bridge) {
                return true;
            }
        }

        return false;
    }

    static boolean sameNamespace(String left, String right) {
        String a = normalizeNamespace(left);
        String b = normalizeNamespace(right);

        if (a == null) {
            return b == null;
        }

        return a.equals(b);
    }

    static String normalizeNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return null;
        }

        return namespace;
    }
}

final class ModuleVariable {

    final String namespace;
    final String name;
    final boolean bridge;
    final Path bridgePath;
    final boolean compileTimeConstant;
    final TargetBinding target;

    ModuleVariable(
            String namespace,
            String name,
            boolean bridge,
            Path bridgePath,
            boolean compileTimeConstant,
            TargetBinding target
    ) {
        this.namespace = ModuleDescriptor.normalizeNamespace(namespace);
        this.name = name;
        this.bridge = bridge;
        this.bridgePath = bridgePath;
        this.compileTimeConstant = compileTimeConstant;
        this.target = target;
    }

    String qualifiedName() {
        return namespace == null ? name : namespace + "::" + name;
    }
}

final class ModuleStruct {

    final String namespace;
    final String name;
    final boolean bridge;
    final Path bridgePath;

    ModuleStruct(String namespace, String name, boolean bridge, Path bridgePath) {
        this.namespace = ModuleDescriptor.normalizeNamespace(namespace);
        this.name = name;
        this.bridge = bridge;
        this.bridgePath = bridgePath;
    }

    String qualifiedName() {
        return namespace == null ? name : namespace + "::" + name;
    }
}

final class ModuleFunction {

    final String namespace;
    final String name;
    final boolean bridge;
    final Path bridgePath;
    final TargetFilter targets;

    ModuleFunction(
            String namespace,
            String name,
            boolean bridge,
            Path bridgePath,
            TargetFilter targets
    ) {
        this.namespace = ModuleDescriptor.normalizeNamespace(namespace);
        this.name = name;
        this.bridge = bridge;
        this.bridgePath = bridgePath;
        this.targets = targets == null ? TargetFilter.any() : targets;
    }

    String qualifiedName() {
        return namespace == null ? name : namespace + "::" + name;
    }
}

final class TargetBinding {

    final long defaultValue;
    final List<TargetOverride> overrides;

    TargetBinding(long defaultValue, List<TargetOverride> overrides) {
        this.defaultValue = defaultValue;
        this.overrides = List.copyOf(overrides);
    }

    long resolve(TargetInfo target) {
        for (TargetOverride override : overrides) {
            if (override.when.matches(target)) {
                return override.value;
            }
        }

        return defaultValue;
    }
}

final class TargetOverride {

    final TargetFilter when;
    final long value;

    TargetOverride(TargetFilter when, long value) {
        this.when = when;
        this.value = value;
    }
}

final class TargetFilter {

    final List<String> os;
    final List<String> arch;
    final List<String> canonical;

    TargetFilter(
            List<String> os,
            List<String> arch,
            List<String> canonical
    ) {
        this.os = List.copyOf(os);
        this.arch = List.copyOf(arch);
        this.canonical = List.copyOf(canonical);
    }

    static TargetFilter any() {
        return new TargetFilter(List.of(), List.of(), List.of());
    }

    boolean matches(TargetInfo target) {
        if (target == null) {
            return true;
        }

        if (!os.isEmpty() && !os.contains(target.os)) {
            return false;
        }

        if (!arch.isEmpty() && !arch.contains(target.arch)) {
            return false;
        }

        if (!canonical.isEmpty() && !canonical.contains(target.canonical())) {
            return false;
        }

        return true;
    }

    String describe() {
        if (!canonical.isEmpty()) {
            return String.join(" or ", canonical);
        }

        String osText = os.isEmpty() ? null : String.join(" or ", os);
        String archText = arch.isEmpty() ? null : String.join(" or ", arch);

        if (osText != null && archText != null) {
            return osText + " on " + archText;
        }

        if (osText != null) {
            return osText;
        }

        if (archText != null) {
            return archText;
        }

        return "any target";
    }
}
