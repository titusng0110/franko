import java.util.Arrays;

final class DynamicArrayAnalysisState {
    static final byte UNINITED = 0;
    static final byte INITED = 1;
    static final byte MAYBE_INITED = 2;

    static final byte FREED_NEVER = 0;
    static final byte FREED_MAYBE = 1;
    static final byte FREED_YES = 2;

    static final byte POST_UNCHANGED = 0;
    static final byte POST_INITED = 1;
    static final byte POST_UNINITED = 2;
    static final byte POST_MAYBE = 3;
    static final byte POST_HAVOC = 4;

    static final int ALIAS_NONE = -1;
    static final int ALIAS_UNKNOWN = -2;

    static final int BINDING_STRIDE = 7;
    static final int BINDING_ARRAY = 0;
    static final int BINDING_LIFECYCLE = 1;
    static final int BINDING_FREED = 2;
    static final int BINDING_ALIAS_ROOT = 3;
    static final int BINDING_ALIAS_EPOCH = 4;
    static final int BINDING_EPOCH = 5;
    static final int BINDING_ROOT_ARG = 6;

    final MemoryAnalysisContext context;
    final MemoryTemplateRegistry templates;

    final int arrayCount;
    final int[] arrayVariableIds;
    final int[] arrayIndexByVariable;

    final int ownerCount;
    final int[] ownerVariableIds;
    final int[] ownerIndexByVariable;

    final int pointerCount;
    final int[] pointerVariableIds;
    final int[] pointerIndexByVariable;

    final int arrayWords;
    final int paramCount;
    final int paramWords;
    final int siteCount;
    final int siteWords;
    final int templateWords;

    byte[] lifecycle;
    byte[] freed;
    boolean[] escaped;
    int[] epochMin;
    int[] epochMax;
    long[] ownerArrayBits;
    boolean[] ownerUnknown;
    int[] aliasRoot;
    int[] aliasEpoch;

    byte[] paramPost;
    long[] requireMust;
    long[] requireMay;
    long[] invalidateMust;
    long[] invalidateMay;
    long[] freeMust;
    long[] freeMay;
    long[] useMust;
    long[] useMay;
    long[] escapeMust;
    long[] escapeMay;
    long[] symAlias;

    private byte[] lifecycleSnapshots = new byte[0];
    private byte[] freedSnapshots = new byte[0];
    private boolean[] escapedSnapshots = new boolean[0];
    private int[] epochMinSnapshots = new int[0];
    private int[] epochMaxSnapshots = new int[0];
    private long[] ownerBitsSnapshots = new long[0];
    private boolean[] ownerUnknownSnapshots = new boolean[0];
    private int[] aliasRootSnapshots = new int[0];
    private int[] aliasEpochSnapshots = new int[0];
    private byte[] paramPostSnapshots = new byte[0];
    private long[] requireMustSnapshots = new long[0];
    private long[] requireMaySnapshots = new long[0];
    private long[] invalidateMustSnapshots = new long[0];
    private long[] invalidateMaySnapshots = new long[0];
    private long[] freeMustSnapshots = new long[0];
    private long[] freeMaySnapshots = new long[0];
    private long[] useMustSnapshots = new long[0];
    private long[] useMaySnapshots = new long[0];
    private long[] escapeMustSnapshots = new long[0];
    private long[] escapeMaySnapshots = new long[0];
    private long[] symAliasSnapshots = new long[0];

    int capacity;
    int snapshotCount;

    private int[][] blockLocals = new int[32][];

    DynamicArrayAnalysisState(MemoryAnalysisContext context) {
        this.context = context;
        this.templates = context.templates;

        int arrayTotal = 0;
        int ownerTotal = 0;
        int pointerTotal = 0;

        for (int i = 0; i < context.variables.length; i++) {
            Symbol symbol = context.variables[i];

            if (symbol.kind == SymbolKind.GLOBAL) {
                continue;
            }

            if (symbol.type.kind == SastTypeKind.DYNAMIC_ARRAY) {
                arrayTotal++;
            }

            if (symbol.type.kind == SastTypeKind.PTR
                    && symbol.type.permission == PtrPermission.OWNER
                    && symbol.type.referencedType.kind == SastTypeKind.DYNAMIC_ARRAY) {
                ownerTotal++;
            }

            if (symbol.type.kind == SastTypeKind.PTR) {
                pointerTotal++;
            }
        }

        arrayCount = arrayTotal;
        ownerCount = ownerTotal;
        pointerCount = pointerTotal;
        arrayVariableIds = new int[arrayCount];
        ownerVariableIds = new int[ownerCount];
        pointerVariableIds = new int[pointerCount];
        arrayIndexByVariable = new int[context.variables.length];
        ownerIndexByVariable = new int[context.variables.length];
        pointerIndexByVariable = new int[context.variables.length];
        Arrays.fill(arrayIndexByVariable, -1);
        Arrays.fill(ownerIndexByVariable, -1);
        Arrays.fill(pointerIndexByVariable, -1);

        int arrayIndex = 0;
        int ownerIndex = 0;
        int pointerIndex = 0;

        for (int i = 0; i < context.variables.length; i++) {
            Symbol symbol = context.variables[i];

            if (symbol.kind == SymbolKind.GLOBAL) {
                continue;
            }

            if (symbol.type.kind == SastTypeKind.DYNAMIC_ARRAY) {
                arrayVariableIds[arrayIndex] = i;
                arrayIndexByVariable[i] = arrayIndex++;
            }

            if (symbol.type.kind == SastTypeKind.PTR
                    && symbol.type.permission == PtrPermission.OWNER
                    && symbol.type.referencedType.kind == SastTypeKind.DYNAMIC_ARRAY) {
                ownerVariableIds[ownerIndex] = i;
                ownerIndexByVariable[i] = ownerIndex++;
            }

            if (symbol.type.kind == SastTypeKind.PTR) {
                pointerVariableIds[pointerIndex] = i;
                pointerIndexByVariable[i] = pointerIndex++;
            }
        }

        arrayWords = Math.max(1, (arrayCount + 63) >>> 6);
        paramCount = context.templates.paramCount;
        paramWords = Math.max(1, (paramCount + 63) >>> 6);
        siteCount = context.siteCount;
        siteWords = Math.max(1, (siteCount + 63) >>> 6);
        templateWords = context.templates.words;

        lifecycle = new byte[arrayCount];
        freed = new byte[arrayCount];
        escaped = new boolean[arrayCount];
        epochMin = new int[arrayCount];
        epochMax = new int[arrayCount];
        ownerArrayBits = new long[ownerCount * arrayWords];
        ownerUnknown = new boolean[ownerCount];
        aliasRoot = new int[pointerCount];
        aliasEpoch = new int[pointerCount];

        paramPost = new byte[paramCount];
        requireMust = new long[paramWords];
        requireMay = new long[paramWords];
        invalidateMust = new long[paramWords];
        invalidateMay = new long[paramWords];
        freeMust = new long[paramWords];
        freeMay = new long[paramWords];
        useMust = new long[paramWords];
        useMay = new long[paramWords];
        escapeMust = new long[paramWords];
        escapeMay = new long[paramWords];
        symAlias = new long[pointerCount * templateWords];
    }

    void reset() {
        Arrays.fill(lifecycle, UNINITED);
        Arrays.fill(freed, FREED_NEVER);
        Arrays.fill(escaped, false);
        Arrays.fill(epochMin, 0);
        Arrays.fill(epochMax, 0);
        Arrays.fill(ownerArrayBits, 0L);
        Arrays.fill(ownerUnknown, false);
        Arrays.fill(aliasRoot, ALIAS_NONE);
        Arrays.fill(aliasEpoch, 0);
        Arrays.fill(paramPost, POST_UNCHANGED);
        Arrays.fill(requireMust, 0L);
        Arrays.fill(requireMay, 0L);
        Arrays.fill(invalidateMust, 0L);
        Arrays.fill(invalidateMay, 0L);
        Arrays.fill(freeMust, 0L);
        Arrays.fill(freeMay, 0L);
        Arrays.fill(useMust, 0L);
        Arrays.fill(useMay, 0L);
        Arrays.fill(escapeMust, 0L);
        Arrays.fill(escapeMay, 0L);
        Arrays.fill(symAlias, 0L);
        snapshotCount = 0;
    }

    int arrayIndexOf(Symbol symbol) {
        int variableId = context.variableId(symbol);

        if (variableId < 0) {
            return -1;
        }

        return arrayIndexByVariable[variableId];
    }

    int ownerIndexOf(Symbol symbol) {
        int variableId = context.variableId(symbol);

        if (variableId < 0) {
            return -1;
        }

        return ownerIndexByVariable[variableId];
    }

    int pointerIndexOf(Symbol symbol) {
        int variableId = context.variableId(symbol);

        if (variableId < 0) {
            return -1;
        }

        return pointerIndexByVariable[variableId];
    }

    int ownerIndexOfVariable(int variableId) {
        if (variableId < 0 || variableId >= ownerIndexByVariable.length) {
            return -1;
        }

        return ownerIndexByVariable[variableId];
    }

    int pointerIndexOfVariable(int variableId) {
        if (variableId < 0 || variableId >= pointerIndexByVariable.length) {
            return -1;
        }

        return pointerIndexByVariable[variableId];
    }

    int arrayIndexOfVariable(int variableId) {
        if (variableId < 0 || variableId >= arrayIndexByVariable.length) {
            return -1;
        }

        return arrayIndexByVariable[variableId];
    }

    void clearOwner(int ownerIndex) {
        int offset = ownerIndex * arrayWords;
        Arrays.fill(ownerArrayBits, offset, offset + arrayWords, 0L);
        ownerUnknown[ownerIndex] = false;
    }

    void addOwner(int ownerIndex, int arrayIndex) {
        if (arrayIndex < 0) {
            return;
        }

        ownerArrayBits[ownerIndex * arrayWords + (arrayIndex >>> 6)]
                |= 1L << (arrayIndex & 63);
    }

    void setOwnerUnknown(int ownerIndex) {
        ownerUnknown[ownerIndex] = true;
    }

    void copyOwner(int target, int source) {
        int targetOffset = target * arrayWords;
        int sourceOffset = source * arrayWords;
        System.arraycopy(
                ownerArrayBits,
                sourceOffset,
                ownerArrayBits,
                targetOffset,
                arrayWords
        );
        ownerUnknown[target] = ownerUnknown[source];
    }

    int singleOwner(int ownerIndex) {
        if (ownerUnknown[ownerIndex]) {
            return -1;
        }

        int offset = ownerIndex * arrayWords;
        int found = -1;

        for (int word = 0; word < arrayWords; word++) {
            long bits = ownerArrayBits[offset + word];

            while (bits != 0L) {
                int bit = Long.numberOfTrailingZeros(bits);
                int arrayIndex = (word << 6) + bit;

                if (found >= 0) {
                    return -1;
                }

                found = arrayIndex;
                bits &= bits - 1;
            }
        }

        return found;
    }

    void setAlias(int pointerIndex, int root, int epoch) {
        aliasRoot[pointerIndex] = root;
        aliasEpoch[pointerIndex] = epoch;
    }

    void clearAlias(int pointerIndex) {
        aliasRoot[pointerIndex] = ALIAS_NONE;
        aliasEpoch[pointerIndex] = 0;
    }

    void copyAlias(int target, int source) {
        aliasRoot[target] = aliasRoot[source];
        aliasEpoch[target] = aliasEpoch[source];
    }

    void enterBlock(SastNode node, int depth) {
        if (depth >= blockLocals.length) {
            blockLocals = Arrays.copyOf(blockLocals, blockLocals.length * 2);
        }

        blockLocals[depth] = context.blockLocalIds(node);
    }

    void leaveBlock(SastNode node, int depth, boolean terminated) {
        int[] locals = blockLocals[depth];

        if (locals != null) {
            for (int variableId : locals) {
                int arrayIndex = arrayIndexByVariable[variableId];

                if (arrayIndex >= 0) {
                    clearArrayAliases(arrayIndex);
                    lifecycle[arrayIndex] = UNINITED;
                    freed[arrayIndex] = FREED_NEVER;
                    escaped[arrayIndex] = false;
                    epochMin[arrayIndex] = 0;
                    epochMax[arrayIndex] = 0;
                }
            }
        }

        blockLocals[depth] = null;
    }

    void clearArrayAliases(int arrayIndex) {
        for (int i = 0; i < pointerCount; i++) {
            if (aliasRoot[i] == arrayIndex) {
                aliasRoot[i] = ALIAS_NONE;
                aliasEpoch[i] = 0;
            }
        }

        for (int i = 0; i < ownerCount; i++) {
            int offset = i * arrayWords;
            ownerArrayBits[offset + (arrayIndex >>> 6)]
                    &= ~(1L << (arrayIndex & 63));
        }
    }

    int snapshot() {
        ensureCapacity(snapshotCount + 1);

        int slot = snapshotCount++;

        System.arraycopy(
                lifecycle,
                0,
                lifecycleSnapshots,
                slot * arrayCount,
                arrayCount
        );
        System.arraycopy(
                freed,
                0,
                freedSnapshots,
                slot * arrayCount,
                arrayCount
        );
        System.arraycopy(
                escaped,
                0,
                escapedSnapshots,
                slot * arrayCount,
                arrayCount
        );
        System.arraycopy(
                epochMin,
                0,
                epochMinSnapshots,
                slot * arrayCount,
                arrayCount
        );
        System.arraycopy(
                epochMax,
                0,
                epochMaxSnapshots,
                slot * arrayCount,
                arrayCount
        );
        System.arraycopy(
                ownerArrayBits,
                0,
                ownerBitsSnapshots,
                slot * ownerCount * arrayWords,
                ownerCount * arrayWords
        );
        System.arraycopy(
                ownerUnknown,
                0,
                ownerUnknownSnapshots,
                slot * ownerCount,
                ownerCount
        );
        System.arraycopy(
                aliasRoot,
                0,
                aliasRootSnapshots,
                slot * pointerCount,
                pointerCount
        );
        System.arraycopy(
                aliasEpoch,
                0,
                aliasEpochSnapshots,
                slot * pointerCount,
                pointerCount
        );
        System.arraycopy(
                paramPost,
                0,
                paramPostSnapshots,
                slot * paramCount,
                paramCount
        );
        System.arraycopy(
                requireMust,
                0,
                requireMustSnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                requireMay,
                0,
                requireMaySnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                invalidateMust,
                0,
                invalidateMustSnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                invalidateMay,
                0,
                invalidateMaySnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                freeMust,
                0,
                freeMustSnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                freeMay,
                0,
                freeMaySnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                useMust,
                0,
                useMustSnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                useMay,
                0,
                useMaySnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                escapeMust,
                0,
                escapeMustSnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                escapeMay,
                0,
                escapeMaySnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                symAlias,
                0,
                symAliasSnapshots,
                slot * pointerCount * templateWords,
                pointerCount * templateWords
        );

        return slot;
    }

    void restore(int slot) {
        if (slot < 0) {
            return;
        }

        System.arraycopy(
                lifecycleSnapshots,
                slot * arrayCount,
                lifecycle,
                0,
                arrayCount
        );
        System.arraycopy(
                freedSnapshots,
                slot * arrayCount,
                freed,
                0,
                arrayCount
        );
        System.arraycopy(
                escapedSnapshots,
                slot * arrayCount,
                escaped,
                0,
                arrayCount
        );
        System.arraycopy(
                epochMinSnapshots,
                slot * arrayCount,
                epochMin,
                0,
                arrayCount
        );
        System.arraycopy(
                epochMaxSnapshots,
                slot * arrayCount,
                epochMax,
                0,
                arrayCount
        );
        System.arraycopy(
                ownerBitsSnapshots,
                slot * ownerCount * arrayWords,
                ownerArrayBits,
                0,
                ownerCount * arrayWords
        );
        System.arraycopy(
                ownerUnknownSnapshots,
                slot * ownerCount,
                ownerUnknown,
                0,
                ownerCount
        );
        System.arraycopy(
                aliasRootSnapshots,
                slot * pointerCount,
                aliasRoot,
                0,
                pointerCount
        );
        System.arraycopy(
                aliasEpochSnapshots,
                slot * pointerCount,
                aliasEpoch,
                0,
                pointerCount
        );
        System.arraycopy(
                paramPostSnapshots,
                slot * paramCount,
                paramPost,
                0,
                paramCount
        );
        System.arraycopy(
                requireMustSnapshots,
                slot * paramWords,
                requireMust,
                0,
                paramWords
        );
        System.arraycopy(
                requireMaySnapshots,
                slot * paramWords,
                requireMay,
                0,
                paramWords
        );
        System.arraycopy(
                invalidateMustSnapshots,
                slot * paramWords,
                invalidateMust,
                0,
                paramWords
        );
        System.arraycopy(
                invalidateMaySnapshots,
                slot * paramWords,
                invalidateMay,
                0,
                paramWords
        );
        System.arraycopy(
                freeMustSnapshots,
                slot * paramWords,
                freeMust,
                0,
                paramWords
        );
        System.arraycopy(
                freeMaySnapshots,
                slot * paramWords,
                freeMay,
                0,
                paramWords
        );
        System.arraycopy(
                useMustSnapshots,
                slot * paramWords,
                useMust,
                0,
                paramWords
        );
        System.arraycopy(
                useMaySnapshots,
                slot * paramWords,
                useMay,
                0,
                paramWords
        );
        System.arraycopy(
                escapeMustSnapshots,
                slot * paramWords,
                escapeMust,
                0,
                paramWords
        );
        System.arraycopy(
                escapeMaySnapshots,
                slot * paramWords,
                escapeMay,
                0,
                paramWords
        );
        System.arraycopy(
                symAliasSnapshots,
                slot * pointerCount * templateWords,
                symAlias,
                0,
                pointerCount * templateWords
        );
    }

    private void ensureCapacity(int required) {
        if (required <= capacity) {
            return;
        }

        int newCapacity = Math.max(16, capacity);

        while (newCapacity < required) {
            newCapacity *= 2;
        }

        lifecycleSnapshots = grow(lifecycleSnapshots, newCapacity * arrayCount);
        freedSnapshots = grow(freedSnapshots, newCapacity * arrayCount);
        escapedSnapshots = grow(escapedSnapshots, newCapacity * arrayCount);
        epochMinSnapshots = grow(epochMinSnapshots, newCapacity * arrayCount);
        epochMaxSnapshots = grow(epochMaxSnapshots, newCapacity * arrayCount);
        ownerBitsSnapshots = grow(
                ownerBitsSnapshots,
                newCapacity * ownerCount * arrayWords
        );
        ownerUnknownSnapshots = grow(
                ownerUnknownSnapshots,
                newCapacity * ownerCount
        );
        aliasRootSnapshots = grow(
                aliasRootSnapshots,
                newCapacity * pointerCount
        );
        aliasEpochSnapshots = grow(
                aliasEpochSnapshots,
                newCapacity * pointerCount
        );
        paramPostSnapshots = grow(
                paramPostSnapshots,
                newCapacity * paramCount
        );
        requireMustSnapshots = grow(
                requireMustSnapshots,
                newCapacity * paramWords
        );
        requireMaySnapshots = grow(
                requireMaySnapshots,
                newCapacity * paramWords
        );
        invalidateMustSnapshots = grow(
                invalidateMustSnapshots,
                newCapacity * paramWords
        );
        invalidateMaySnapshots = grow(
                invalidateMaySnapshots,
                newCapacity * paramWords
        );
        freeMustSnapshots = grow(
                freeMustSnapshots,
                newCapacity * paramWords
        );
        freeMaySnapshots = grow(
                freeMaySnapshots,
                newCapacity * paramWords
        );
        useMustSnapshots = grow(
                useMustSnapshots,
                newCapacity * paramWords
        );
        useMaySnapshots = grow(
                useMaySnapshots,
                newCapacity * paramWords
        );
        escapeMustSnapshots = grow(
                escapeMustSnapshots,
                newCapacity * paramWords
        );
        escapeMaySnapshots = grow(
                escapeMaySnapshots,
                newCapacity * paramWords
        );
        symAliasSnapshots = grow(
                symAliasSnapshots,
                newCapacity * pointerCount * templateWords
        );

        capacity = newCapacity;
    }

    private static byte[] grow(byte[] array, int size) {
        return Arrays.copyOf(array, size);
    }

    private static int[] grow(int[] array, int size) {
        return Arrays.copyOf(array, size);
    }

    private static long[] grow(long[] array, int size) {
        return Arrays.copyOf(array, size);
    }

    private static boolean[] grow(boolean[] array, int size) {
        return Arrays.copyOf(array, size);
    }

    void mergeAfterIf(
            int before,
            int thenState,
            boolean thenLeaves,
            int elseState,
            boolean elseLeaves,
            boolean hasElse,
            int fallthroughState
    ) {
        int[] continuing = new int[3];
        int count = 0;

        if (!thenLeaves) {
            continuing[count++] = thenState;
        }

        if (hasElse) {
            if (!elseLeaves) {
                continuing[count++] = elseState;
            }
        } else if (fallthroughState >= 0) {
            continuing[count++] = fallthroughState;
        }

        if (count == 0) {
            restore(before);
            return;
        }

        restore(continuing[0]);

        for (int i = 1; i < count; i++) {
            mergeFromSnapshot(continuing[i]);
        }
    }

    void mergeLoopExitStates(int[] slots, int count) {
        if (count <= 0) {
            return;
        }

        restore(slots[0]);

        for (int i = 1; i < count; i++) {
            mergeFromSnapshot(slots[i]);
        }
    }

    void mergeStateIntoCurrent(int slot) {
        mergeFromSnapshot(slot);
    }

    long[] requireMustSnapshots(int slot) {
        return requireMustSnapshots;
    }

    long[] requireMaySnapshots(int slot) {
        return requireMaySnapshots;
    }

    long[] invalidateMustSnapshots(int slot) {
        return invalidateMustSnapshots;
    }

    long[] invalidateMaySnapshots(int slot) {
        return invalidateMaySnapshots;
    }

    long[] freeMustSnapshots(int slot) {
        return freeMustSnapshots;
    }

    long[] freeMaySnapshots(int slot) {
        return freeMaySnapshots;
    }

    long[] escapeMustSnapshots(int slot) {
        return escapeMustSnapshots;
    }

    long[] escapeMaySnapshots(int slot) {
        return escapeMaySnapshots;
    }

    long[] useMustSnapshots(int slot) {
        return useMustSnapshots;
    }

    long[] useMaySnapshots(int slot) {
        return useMaySnapshots;
    }

    byte paramPostSnapshot(int slot, int param) {
        return paramPostSnapshots[slot * paramCount + param];
    }

    private void mergeFromSnapshot(int slot) {
        for (int i = 0; i < arrayCount; i++) {
            byte incomingLifecycle = lifecycleSnapshots[slot * arrayCount + i];
            byte incomingFreed = freedSnapshots[slot * arrayCount + i];

            if (lifecycle[i] != incomingLifecycle) {
                lifecycle[i] = MAYBE_INITED;
            }

            if (freed[i] != incomingFreed) {
                freed[i] = FREED_MAYBE;
            }

            escaped[i] |= escapedSnapshots[slot * arrayCount + i];
            epochMin[i] = Math.min(
                    epochMin[i],
                    epochMinSnapshots[slot * arrayCount + i]
            );
            epochMax[i] = Math.max(
                    epochMax[i],
                    epochMaxSnapshots[slot * arrayCount + i]
            );
        }

        for (int i = 0; i < ownerCount * arrayWords; i++) {
            ownerArrayBits[i] |= ownerBitsSnapshots[
                    slot * ownerCount * arrayWords + i];
        }

        for (int i = 0; i < ownerCount; i++) {
            ownerUnknown[i] |= ownerUnknownSnapshots[slot * ownerCount + i];
        }

        for (int i = 0; i < pointerCount; i++) {
            int incomingRoot = aliasRootSnapshots[slot * pointerCount + i];
            int incomingEpoch = aliasEpochSnapshots[slot * pointerCount + i];

            if (aliasRoot[i] != incomingRoot || aliasEpoch[i] != incomingEpoch) {
                aliasRoot[i] = ALIAS_UNKNOWN;
                aliasEpoch[i] = 0;
            }
        }

        for (int i = 0; i < paramCount; i++) {
            byte incoming = paramPostSnapshots[slot * paramCount + i];

            if (paramPost[i] != incoming) {
                if (paramPost[i] == POST_HAVOC || incoming == POST_HAVOC) {
                    paramPost[i] = POST_HAVOC;
                } else {
                    paramPost[i] = POST_MAYBE;
                }
            }
        }

        for (int i = 0; i < paramWords; i++) {
            requireMust[i] &= requireMustSnapshots[slot * paramWords + i];
            requireMay[i] |= requireMaySnapshots[slot * paramWords + i];
            invalidateMust[i] &= invalidateMustSnapshots[slot * paramWords + i];
            invalidateMay[i] |= invalidateMaySnapshots[slot * paramWords + i];
            freeMust[i] &= freeMustSnapshots[slot * paramWords + i];
            freeMay[i] |= freeMaySnapshots[slot * paramWords + i];
            useMust[i] &= useMustSnapshots[slot * paramWords + i];
            useMay[i] |= useMaySnapshots[slot * paramWords + i];
            escapeMust[i] &= escapeMustSnapshots[slot * paramWords + i];
            escapeMay[i] |= escapeMaySnapshots[slot * paramWords + i];
        }

        for (int i = 0; i < pointerCount * templateWords; i++) {
            symAlias[i] |= symAliasSnapshots[
                    slot * pointerCount * templateWords + i];
        }
    }
}
