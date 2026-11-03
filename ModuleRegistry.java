import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ModuleRegistry {

    private final Map<String, ModuleDescriptor> bySource = new HashMap<>();
    private final Set<String> checkedSources = new HashSet<>();
    private final List<ModuleDescriptor> all = new ArrayList<>();

    ModuleDescriptor loadFor(Path sourceFile) {
        Path absolute = sourceFile.toAbsolutePath().normalize();
        String key = SourcePaths.canonicalKey(absolute);

        if (checkedSources.contains(key)) {
            return bySource.get(key);
        }

        checkedSources.add(key);

        Path manifest = moduleManifestPath(absolute);

        if (!Files.isRegularFile(manifest)) {
            return null;
        }

        String text;

        try {
            text = Files.readString(manifest, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Cannot read module manifest '" + manifest + "': " + e.getMessage()
            );
        }

        ModuleDescriptor descriptor = parse(manifest, absolute, text);
        bySource.put(key, descriptor);
        all.add(descriptor);
        return descriptor;
    }

    ModuleDescriptor descriptorForSource(Path sourceFile) {
        return loadFor(sourceFile);
    }

    List<ModuleDescriptor> all() {
        return List.copyOf(all);
    }

    boolean hasFfiEntities() {
        for (ModuleDescriptor descriptor : all) {
            if (descriptor.hasBridgedEntities()) {
                return true;
            }
        }

        return false;
    }

    static Path moduleManifestPath(Path sourceFile) {
        String fileName = sourceFile.getFileName() == null
                ? sourceFile.toString()
                : sourceFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot <= 0 ? fileName : fileName.substring(0, dot);
        return sourceFile.resolveSibling(stem + ".module.json");
    }

    private static ModuleDescriptor parse(
            Path manifest,
            Path sourceFile,
            String text
    ) {
        JsonValue value = Json.parse(text);

        if (!(value instanceof JsonObject root)) {
            throw new IllegalArgumentException(
                    "Module manifest '" + manifest + "' must contain a JSON object"
            );
        }

        String format = root.requireString("format");

        if (!"franko-module".equals(format)) {
            throw new IllegalArgumentException(
                    "Module manifest '" + manifest + "' has unsupported format '"
                            + format + "'"
            );
        }

        int version = root.requireInt("version");

        if (version != 1) {
            throw new IllegalArgumentException(
                    "Module manifest '" + manifest + "' has unsupported version "
                            + version
            );
        }

        String id = root.optString("id");

        if (id == null || id.isEmpty()) {
            id = stripExtension(sourceFile.getFileName().toString());
        }

        Path moduleBridge = resolveOptionalPath(
                manifest.getParent(),
                root.optString("bridge"),
                manifest
        );

        ModuleDescriptor descriptor = new ModuleDescriptor(
                id,
                manifest,
                sourceFile,
                moduleBridge
        );

        parseFunctions(root.optArray("functions"), descriptor, manifest);

        return descriptor;
    }

    private static void parseFunctions(
            JsonArray array,
            ModuleDescriptor descriptor,
            Path manifest
    ) {
        if (array == null) {
            return;
        }

        for (JsonValue value : array.values()) {
            JsonObject obj = requireObject(value, "module function", manifest);
            String name = obj.requireString("name");
            Path bridgePath = entityBridgePath(obj, descriptor, manifest);

            descriptor.functions.add(new ModuleFunction(
                    obj.optString("namespace"),
                    name,
                    bridgePath
            ));
        }
    }

    private static Path entityBridgePath(
            JsonObject obj,
            ModuleDescriptor descriptor,
            Path manifest
    ) {
        String override = obj.optString("bridgeFile");

        if (override != null && !override.isEmpty()) {
            return resolvePath(manifest.getParent(), override, manifest);
        }

        if (descriptor.bridgePath == null) {
            throw new IllegalArgumentException(
                    "Module manifest '" + manifest
                            + "' declares a bridged function but has no bridge file"
            );
        }

        return descriptor.bridgePath;
    }

    private static Path resolveOptionalPath(
            Path base,
            String text,
            Path manifest
    ) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        return resolvePath(base, text, manifest);
    }

    private static Path resolvePath(Path base, String text, Path manifest) {
        if (base == null) {
            throw new IllegalArgumentException(
                    "Module manifest '" + manifest + "' has no parent directory"
            );
        }

        return base.resolve(text).normalize().toAbsolutePath();
    }

    private static JsonObject requireObject(
            JsonValue value,
            String description,
            Path manifest
    ) {
        if (!(value instanceof JsonObject object)) {
            throw new IllegalArgumentException(
                    "Module manifest '" + manifest + "': expected "
                            + description + " object"
            );
        }

        return object;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
}
