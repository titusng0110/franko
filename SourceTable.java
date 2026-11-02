import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SourceTable {

    private final Map<String, Integer> indexByName = new LinkedHashMap<>();
    private final List<String> names = new ArrayList<>();

    public int intern(String source) {
        if (source == null || source.isEmpty()) {
            return -1;
        }

        Integer existing = indexByName.get(source);

        if (existing != null) {
            return existing;
        }

        int index = names.size();
        names.add(source);
        indexByName.put(source, index);
        return index;
    }

    public int indexOf(String source) {
        Integer index = indexByName.get(source);
        return index == null ? -1 : index;
    }

    public String name(int index) {
        if (index < 0 || index >= names.size()) {
            throw new IllegalArgumentException("Unknown source index: " + index);
        }

        return names.get(index);
    }

    public int size() {
        return names.size();
    }

    public List<String> names() {
        return List.copyOf(names);
    }

    public void addAll(SourceTable other) {
        for (String name : other.names) {
            intern(name);
        }
    }

    public static SourceTable fromNames(List<String> sources) {
        SourceTable table = new SourceTable();

        for (String source : sources) {
            table.intern(source);
        }

        return table;
    }
}
