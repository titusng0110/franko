import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FfiBridge {

    private static final String MARKER = "// @@";
    private static final String END = "// @@end";
    private static final String COMMON = "common";

    private final Map<String, String> blocks = new LinkedHashMap<>();

    private FfiBridge() {
    }

    static FfiBridge parse(String text) {
        FfiBridge bridge = new FfiBridge();
        String currentName = null;
        StringBuilder current = new StringBuilder();

        for (String line : text.split("\\R", -1)) {
            String trimmed = line.trim();

            if (END.equals(trimmed)) {
                if (currentName == null) {
                    throw new IllegalArgumentException(
                            "FFI bridge has '// @@end' without an open block"
                    );
                }

                if (bridge.blocks.containsKey(currentName)) {
                    throw new IllegalArgumentException(
                            "FFI bridge defines block '" + currentName
                                    + "' more than once"
                    );
                }

                bridge.blocks.put(currentName, current.toString());
                currentName = null;
                current.setLength(0);
                continue;
            }

            if (trimmed.startsWith(MARKER)) {
                if (currentName != null) {
                    throw new IllegalArgumentException(
                            "FFI bridge opens block '"
                                    + trimmed.substring(MARKER.length())
                                    + "' before closing block '"
                                    + currentName + "'"
                    );
                }

                currentName = trimmed.substring(MARKER.length()).trim();

                if (currentName.isEmpty()) {
                    throw new IllegalArgumentException(
                            "FFI bridge has an empty block name"
                    );
                }

                continue;
            }

            if (currentName != null) {
                current.append(line).append('\n');
            }
        }

        if (currentName != null) {
            throw new IllegalArgumentException(
                    "FFI bridge block '" + currentName + "' is never closed"
            );
        }

        return bridge;
    }

    boolean hasBlock(String name) {
        return blocks.containsKey(name);
    }

    String block(String name) {
        return blocks.get(name);
    }

    String commonBlock() {
        String common = blocks.get(COMMON);

        if (common == null) {
            throw new IllegalArgumentException(
                    "FFI bridge is missing the '" + COMMON + "' block"
            );
        }

        return common;
    }

    String build(List<String> blockNames) {
        StringBuilder out = new StringBuilder();
        String common = commonBlock();
        out.append(common);

        if (!common.endsWith("\n")) {
            out.append('\n');
        }

        Set<String> emitted = new LinkedHashSet<>();

        for (String name : blockNames) {
            if (!emitted.add(name)) {
                continue;
            }

            String block = blocks.get(name);

            if (block == null) {
                throw new IllegalArgumentException(
                        "FFI bridge is missing block '" + name + "'"
                );
            }

            out.append(block);

            if (!block.endsWith("\n")) {
                out.append('\n');
            }
        }

        return out.toString();
    }

    List<String> blockNames() {
        return new ArrayList<>(blocks.keySet());
    }
}
