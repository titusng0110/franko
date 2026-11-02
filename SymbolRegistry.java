import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SymbolRegistry {

    private final IdentityHashMap<Object, String> idByIdentity = new IdentityHashMap<>();
    private final LinkedHashMap<String, Object> symbolsById = new LinkedHashMap<>();
    private int nextId = 0;

    public String register(Object symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("Cannot register a null symbol");
        }

        String existing = idByIdentity.get(symbol);

        if (existing != null) {
            return existing;
        }

        String id = "s" + nextId++;
        idByIdentity.put(symbol, id);
        symbolsById.put(id, symbol);
        return id;
    }

    public String idFor(Object symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("Cannot find the ID of a null symbol");
        }

        String id = idByIdentity.get(symbol);

        if (id == null) {
            id = register(symbol);
        }

        return id;
    }

    public String idOf(Object symbol) {
        return symbol == null ? null : idByIdentity.get(symbol);
    }

    public Object get(String id) {
        return symbolsById.get(id);
    }

    public Object require(String id) {
        Object symbol = symbolsById.get(id);

        if (symbol == null) {
            throw new IllegalArgumentException("Unknown symbol ID '" + id + "'");
        }

        return symbol;
    }

    public boolean contains(Object symbol) {
        return idByIdentity.containsKey(symbol);
    }

    public void registerWithId(String id, Object symbol) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Symbol ID must not be empty");
        }

        if (symbolsById.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate symbol ID '" + id + "'");
        }

        String existing = idByIdentity.get(symbol);

        if (existing != null && !existing.equals(id)) {
            throw new IllegalArgumentException(
                    "Symbol already registered as '" + existing + "'"
            );
        }

        idByIdentity.put(symbol, id);
        symbolsById.put(id, symbol);

        if (id.startsWith("s")) {
            try {
                int numeric = Integer.parseInt(id.substring(1));
                nextId = Math.max(nextId, numeric + 1);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public List<String> ids() {
        return List.copyOf(symbolsById.keySet());
    }

    public List<Object> symbols() {
        return List.copyOf(symbolsById.values());
    }

    public List<Map.Entry<String, Object>> entries() {
        return new ArrayList<>(symbolsById.entrySet());
    }

    public void reorder(List<String> ids) {
        LinkedHashMap<String, Object> reordered = new LinkedHashMap<>();

        for (String id : ids) {
            Object symbol = symbolsById.get(id);

            if (symbol != null) {
                reordered.put(id, symbol);
            }
        }

        for (Map.Entry<String, Object> entry : symbolsById.entrySet()) {
            reordered.putIfAbsent(entry.getKey(), entry.getValue());
        }

        symbolsById.clear();
        symbolsById.putAll(reordered);
    }

    public void registerAll(Iterable<?> symbols) {
        for (Object symbol : symbols) {
            register(symbol);
        }
    }
}
