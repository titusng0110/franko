import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FfiMatch {

    final ModuleDescriptor module;
    final ModuleFunction function;
    final String qualifiedName;
    final String blockName;
    final Path bridgePath;
    final String emittedName;

    FfiMatch(
            ModuleDescriptor module,
            ModuleFunction function,
            String qualifiedName,
            String blockName,
            Path bridgePath,
            String emittedName
    ) {
        this.module = module;
        this.function = function;
        this.qualifiedName = qualifiedName;
        this.blockName = blockName;
        this.bridgePath = bridgePath;
        this.emittedName = emittedName;
    }

    String key() {
        return bridgePath + "|" + blockName;
    }
}

final class FfiHandler {

    private final ModuleRegistry registry;
    private final Map<String, FfiBridge> bridges = new LinkedHashMap<>();

    FfiHandler(ModuleRegistry registry) {
        this.registry = registry;
    }

    FfiMatch matchFunction(Symbol function) {
        if (function == null || function.kind == SymbolKind.METHOD) {
            return null;
        }

        String namespace = function.namespace;
        String name = function.name;

        for (ModuleDescriptor module : registry.all()) {
            ModuleFunction descriptor = module.findFunction(namespace, name);

            if (descriptor == null) {
                continue;
            }

            String qualifiedName = descriptor.qualifiedName();

            return new FfiMatch(
                    module,
                    descriptor,
                    qualifiedName,
                    qualifiedName,
                    descriptor.bridgePath,
                    function.mangledName()
            );
        }

        return null;
    }

    PassResult verify(SastDocument document) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<SastNode> functions = new ArrayList<>();
        collectFunctions(document.root, functions);

        for (SastNode declaration : functions) {
            FfiMatch match = matchFunction(declaration.symbol);

            if (match == null) {
                continue;
            }

            FfiBridge bridge = bridgeFor(
                    match.bridgePath,
                    declaration.span,
                    diagnostics
            );

            if (bridge == null) {
                continue;
            }

            if (!bridge.hasBlock(match.blockName)) {
                diagnostics.add(Diagnostic.error(
                        declaration.span,
                        "FFI error: bridge file '" + match.bridgePath
                                + "' is missing block '" + match.blockName + "'"
                ));
                continue;
            }

            String block = bridge.block(match.blockName);

            if (!block.contains(match.emittedName)) {
                diagnostics.add(Diagnostic.error(
                        declaration.span,
                        "FFI error: bridge block '" + match.blockName
                                + "' is missing definition for '"
                                + match.qualifiedName
                                + "' (expected emitted name '"
                                + match.emittedName + "')"
                ));
            }
        }

        return new PassResult(
                document.withPhase(SastPhase.FFI_VERIFIED),
                diagnostics
        );
    }

    String buildBridge(List<FfiMatch> used) {
        Map<Path, List<String>> blocksByPath = new LinkedHashMap<>();

        for (FfiMatch match : used) {
            List<String> names = blocksByPath.get(match.bridgePath);

            if (names == null) {
                names = new ArrayList<>();
                blocksByPath.put(match.bridgePath, names);
            }

            names.add(match.blockName);
        }

        StringBuilder out = new StringBuilder();

        for (Map.Entry<Path, List<String>> entry : blocksByPath.entrySet()) {
            FfiBridge bridge = bridgeFor(
                    entry.getKey(),
                    SourceSpan.NONE,
                    new ArrayList<>()
            );

            if (bridge == null) {
                throw new IllegalStateException(
                        "Internal compiler error: FFI bridge '"
                                + entry.getKey() + "' was not available at codegen"
                );
            }

            out.append(bridge.build(entry.getValue()));
        }

        return out.toString();
    }

    private FfiBridge bridgeFor(
            Path path,
            SourceSpan span,
            List<Diagnostic> diagnostics
    ) {
        if (path == null) {
            diagnostics.add(Diagnostic.error(
                    span,
                    "FFI error: bridged function has no bridge file"
            ));
            return null;
        }

        String key = path.toString();
        FfiBridge cached = bridges.get(key);

        if (cached != null) {
            return cached;
        }

        if (!Files.isRegularFile(path)) {
            diagnostics.add(Diagnostic.error(
                    span,
                    "FFI error: bridge file '" + path + "' is missing"
            ));
            return null;
        }

        String text;

        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            diagnostics.add(Diagnostic.error(
                    span,
                    "FFI error: cannot read bridge file '" + path
                            + "': " + e.getMessage()
            ));
            return null;
        }

        FfiBridge bridge;

        try {
            bridge = FfiBridge.parse(text);
        } catch (IllegalArgumentException e) {
            diagnostics.add(Diagnostic.error(span, e.getMessage()));
            return null;
        }

        bridges.put(key, bridge);
        return bridge;
    }

    private static void collectFunctions(
            SastNode node,
            List<SastNode> out
    ) {
        if (node.kind == SastKind.PROGRAM) {
            for (SastNode item : node.topLevelItems) {
                collectFunctions(item, out);
            }
            return;
        }

        if (node.kind == SastKind.NAMESPACE) {
            for (SastNode item : node.items) {
                collectFunctions(item, out);
            }
            return;
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            for (SastNode method : node.methods) {
                collectFunctions(method, out);
            }
            return;
        }

        if (node.kind == SastKind.FUNCTION_DECL) {
            out.add(node);
        }
    }
}
