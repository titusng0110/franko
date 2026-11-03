import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ModuleDescriptor {

    final String id;
    final Path manifestPath;
    final Path sourcePath;
    final Path bridgePath;
    final List<ModuleFunction> functions = new ArrayList<>();

    ModuleDescriptor(String id, Path manifestPath, Path sourcePath, Path bridgePath) {
        this.id = id;
        this.manifestPath = manifestPath;
        this.sourcePath = sourcePath;
        this.bridgePath = bridgePath;
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
        return !functions.isEmpty();
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

final class ModuleFunction {

    final String namespace;
    final String name;
    final Path bridgePath;

    ModuleFunction(
            String namespace,
            String name,
            Path bridgePath
    ) {
        this.namespace = ModuleDescriptor.normalizeNamespace(namespace);
        this.name = name;
        this.bridgePath = bridgePath;
    }

    String qualifiedName() {
        return namespace == null ? name : namespace + "::" + name;
    }
}
