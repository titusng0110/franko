import java.util.Arrays;

final class MemoryTemplate {
    static final int NULL = 0;
    static final int NONNULL = 1;
    static final int UNKNOWN = 2;
    static final int CONST = 3;
    static final int LOCAL = 4;
    static final int UNKNOWN_CELL = 5;
    static final int LOCAL_CELL = 6;

    static final int CONSTRAINT_ANY = 0;
    static final int CONSTRAINT_NONNULL = 1;
    static final int CONSTRAINT_NULL = 2;

    private MemoryTemplate() {}
}

final class MemoryTemplateRegistry {
    private static final int PARAM_STRIDE = 10;
    private static final int VALUE_ANY = 0;
    private static final int VALUE_NONNULL = 1;
    private static final int VALUE_NULL = 2;
    private static final int POINTEE_ANY = 3;
    private static final int POINTEE_NONNULL = 4;
    private static final int POINTEE_NULL = 5;
    private static final int CELL = 6;
    private static final int ELEMENT_OF = 7;
    private static final int ALIAS_OF_POINTER = 8;
    private static final int WHOLE_ARRAY = 9;

    final int paramCount;
    final int globalCount;
    final int callSiteCount;
    final int variableCount;

    final int paramBase = 7;
    final int globalBase;
    final int callBase;
    final int localBase;
    final int localElementBase;
    final int globalCellBase;
    final int templateCount;
    final int words;

    MemoryTemplateRegistry(
            int paramCount,
            int globalCount,
            int callSiteCount,
            int variableCount
    ) {
        this.paramCount = paramCount;
        this.globalCount = globalCount;
        this.callSiteCount = callSiteCount;
        this.variableCount = variableCount;

        this.globalBase = paramBase + paramCount * PARAM_STRIDE;
        this.callBase = globalBase + globalCount;
        this.localBase = callBase + callSiteCount;
        this.localElementBase = localBase + variableCount;
        this.globalCellBase = localElementBase + variableCount;
        this.templateCount = globalCellBase + globalCount;
        this.words = Math.max(1, (templateCount + 63) >>> 6);
    }

    int valueAny(int param) {
        return paramBase + param * PARAM_STRIDE + VALUE_ANY;
    }

    int valueNonNull(int param) {
        return paramBase + param * PARAM_STRIDE + VALUE_NONNULL;
    }

    int valueNull(int param) {
        return paramBase + param * PARAM_STRIDE + VALUE_NULL;
    }

    int pointeeAny(int param) {
        return paramBase + param * PARAM_STRIDE + POINTEE_ANY;
    }

    int pointeeNonNull(int param) {
        return paramBase + param * PARAM_STRIDE + POINTEE_NONNULL;
    }

    int pointeeNull(int param) {
        return paramBase + param * PARAM_STRIDE + POINTEE_NULL;
    }

    int paramCell(int param) {
        return paramBase + param * PARAM_STRIDE + CELL;
    }

    int elementOf(int param) {
        return paramBase + param * PARAM_STRIDE + ELEMENT_OF;
    }

    int aliasOfPointer(int param) {
        return paramBase + param * PARAM_STRIDE + ALIAS_OF_POINTER;
    }

    int wholeArray(int param) {
        return paramBase + param * PARAM_STRIDE + WHOLE_ARRAY;
    }

    int globalValue(int globalIndex) {
        return globalBase + globalIndex;
    }

    int callResult(int callSiteIndex) {
        return callBase + callSiteIndex;
    }

    int localValue(int variableId) {
        return localBase + variableId;
    }

    int localElement(int variableId) {
        return localElementBase + variableId;
    }

    int globalCell(int globalIndex) {
        return globalCellBase + globalIndex;
    }

    int paramOfValue(int template) {
        int offset = template - paramBase;

        if (offset < 0 || offset >= paramCount * PARAM_STRIDE) {
            return -1;
        }

        int slot = offset % PARAM_STRIDE;

        if (slot != VALUE_ANY && slot != VALUE_NONNULL && slot != VALUE_NULL) {
            return -1;
        }

        return offset / PARAM_STRIDE;
    }

    int paramOfPointee(int template) {
        int offset = template - paramBase;

        if (offset < 0 || offset >= paramCount * PARAM_STRIDE) {
            return -1;
        }

        int slot = offset % PARAM_STRIDE;

        if (slot != POINTEE_ANY
                && slot != POINTEE_NONNULL
                && slot != POINTEE_NULL) {
            return -1;
        }

        return offset / PARAM_STRIDE;
    }

    int paramOfCell(int template) {
        int offset = template - paramBase;

        if (offset < 0 || offset >= paramCount * PARAM_STRIDE) {
            return -1;
        }

        return offset % PARAM_STRIDE == CELL ? offset / PARAM_STRIDE : -1;
    }

    int paramOfElement(int template) {
        int offset = template - paramBase;

        if (offset < 0 || offset >= paramCount * PARAM_STRIDE) {
            return -1;
        }

        return offset % PARAM_STRIDE == ELEMENT_OF ? offset / PARAM_STRIDE : -1;
    }

    int paramOfAlias(int template) {
        int offset = template - paramBase;

        if (offset < 0 || offset >= paramCount * PARAM_STRIDE) {
            return -1;
        }

        return offset % PARAM_STRIDE == ALIAS_OF_POINTER
                ? offset / PARAM_STRIDE
                : -1;
    }

    int paramOfWholeArray(int template) {
        int offset = template - paramBase;

        if (offset < 0 || offset >= paramCount * PARAM_STRIDE) {
            return -1;
        }

        return offset % PARAM_STRIDE == WHOLE_ARRAY
                ? offset / PARAM_STRIDE
                : -1;
    }

    int constraintOfValue(int template) {
        int offset = template - paramBase;

        if (offset < 0 || offset >= paramCount * PARAM_STRIDE) {
            return -1;
        }

        switch (offset % PARAM_STRIDE) {
            case VALUE_ANY: return MemoryTemplate.CONSTRAINT_ANY;
            case VALUE_NONNULL: return MemoryTemplate.CONSTRAINT_NONNULL;
            case VALUE_NULL: return MemoryTemplate.CONSTRAINT_NULL;
            default: return -1;
        }
    }

    int constraintOfPointee(int template) {
        int offset = template - paramBase;

        if (offset < 0 || offset >= paramCount * PARAM_STRIDE) {
            return -1;
        }

        switch (offset % PARAM_STRIDE) {
            case POINTEE_ANY: return MemoryTemplate.CONSTRAINT_ANY;
            case POINTEE_NONNULL: return MemoryTemplate.CONSTRAINT_NONNULL;
            case POINTEE_NULL: return MemoryTemplate.CONSTRAINT_NULL;
            default: return -1;
        }
    }

    int globalIndexOf(int template) {
        int offset = template - globalBase;
        return offset >= 0 && offset < globalCount ? offset : -1;
    }

    int callSiteIndexOf(int template) {
        int offset = template - callBase;
        return offset >= 0 && offset < callSiteCount ? offset : -1;
    }

    int variableOfLocal(int template) {
        int offset = template - localBase;
        return offset >= 0 && offset < variableCount ? offset : -1;
    }

    int variableOfLocalElement(int template) {
        int offset = template - localElementBase;
        return offset >= 0 && offset < variableCount ? offset : -1;
    }

    int globalIndexOfCell(int template) {
        int offset = template - globalCellBase;
        return offset >= 0 && offset < globalCount ? offset : -1;
    }

    long[] newSet() {
        return new long[words];
    }

    void set(long[] bits, int template) {
        bits[template >>> 6] |= 1L << (template & 63);
    }

    void set(long[] bits, int offset, int template) {
        bits[offset + (template >>> 6)] |= 1L << (template & 63);
    }

    void clear(long[] bits, int template) {
        bits[template >>> 6] &= ~(1L << (template & 63));
    }

    boolean has(long[] bits, int template) {
        return (bits[template >>> 6] & (1L << (template & 63))) != 0;
    }

    void clearAll(long[] bits) {
        Arrays.fill(bits, 0L);
    }

    void clearAll(long[] bits, int offset) {
        Arrays.fill(bits, offset, offset + words, 0L);
    }

    void copy(long[] source, long[] target) {
        System.arraycopy(source, 0, target, 0, words);
    }

    void union(long[] target, long[] source) {
        for (int i = 0; i < words; i++) {
            target[i] |= source[i];
        }
    }

    void intersect(long[] target, long[] source) {
        for (int i = 0; i < words; i++) {
            target[i] &= source[i];
        }
    }

    boolean isEmpty(long[] bits) {
        for (long word : bits) {
            if (word != 0L) {
                return false;
            }
        }

        return true;
    }

    int first(long[] bits) {
        for (int i = 0; i < words; i++) {
            long word = bits[i];

            if (word != 0L) {
                return (i << 6) + Long.numberOfTrailingZeros(word);
            }
        }

        return -1;
    }

    int count(long[] bits) {
        int total = 0;

        for (long word : bits) {
            total += Long.bitCount(word);
        }

        return total;
    }
}

final class MemoryTemplateSetPool {
    private final MemoryTemplateRegistry registry;
    private long[][] sets = new long[64][];
    private int[] setHashes = new int[64];
    private int[] table = new int[128];
    private int mask = 127;
    private int count;

    MemoryTemplateSetPool(MemoryTemplateRegistry registry) {
        this.registry = registry;
    }

    int intern(long[] bits) {
        int hash = Arrays.hashCode(bits);
        int slot = mix(hash) & mask;

        while (table[slot] != 0) {
            int id = table[slot] - 1;

            if (setHashes[id] == hash && Arrays.equals(sets[id], bits)) {
                return id;
            }

            slot = (slot + 1) & mask;
        }

        if (count == sets.length) {
            int capacity = sets.length * 2;
            sets = Arrays.copyOf(sets, capacity);
            setHashes = Arrays.copyOf(setHashes, capacity);
        }

        sets[count] = Arrays.copyOf(bits, bits.length);
        setHashes[count] = hash;
        table[slot] = count + 1;
        count++;

        if (count * 2 >= table.length) {
            growTable();
        }

        return count - 1;
    }

    private void growTable() {
        int[] oldTable = table;
        table = new int[oldTable.length * 2];
        mask = table.length - 1;

        for (int i = 0; i < count; i++) {
            int slot = mix(setHashes[i]) & mask;

            while (table[slot] != 0) {
                slot = (slot + 1) & mask;
            }

            table[slot] = i + 1;
        }
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    long[] get(int id) {
        return sets[id];
    }

    int count() {
        return count;
    }

    MemoryTemplateRegistry registry() {
        return registry;
    }
}
