import java.util.Arrays;

final class PointerNullAnalysisState {
    static final byte NULL = 0;
    static final byte POINTING = 1;
    static final byte MAYBE_NULL = 2;

    static final int BINDING_STRIDE = 4;
    static final int BINDING_VALUE = 0;
    static final int BINDING_POINTEE = 1;
    static final int BINDING_CELL = 2;
    static final int BINDING_ROOT_ARG = 3;

    final MemoryAnalysisContext context;
    final MemoryTemplateRegistry templates;

    final int pointerCount;
    final int[] pointerVariableIds;
    final int[] pointerIndexByVariable;

    final int variableCount;
    final int varWords;
    final int paramCount;
    final int paramWords;
    final int siteCount;
    final int siteWords;
    final int templateWords;

    byte[] nullState;
    long[] cellTargets;
    long[] symValue;
    long[] symPointee;
    long[] symCell;

    long[] derefMust;
    long[] derefMay;
    long[] pointeeDerefMust;
    long[] pointeeDerefMay;
    long[] slotMust;
    long[] slotMay;
    long[] slotSource;
    int[] slotTargetParam;

    private byte[] nullSnapshots = new byte[0];
    private long[] cellSnapshots = new long[0];
    private long[] symValueSnapshots = new long[0];
    private long[] symPointeeSnapshots = new long[0];
    private long[] symCellSnapshots = new long[0];
    private long[] derefMustSnapshots = new long[0];
    private long[] derefMaySnapshots = new long[0];
    private long[] pointeeDerefMustSnapshots = new long[0];
    private long[] pointeeDerefMaySnapshots = new long[0];
    private long[] slotMustSnapshots = new long[0];
    private long[] slotMaySnapshots = new long[0];
    private long[] slotSourceSnapshots = new long[0];

    int capacity;
    int snapshotCount;

    PointerNullAnalysisState(MemoryAnalysisContext context) {
        this.context = context;
        this.templates = context.templates;
        this.pointerCount = context.pointerVariableCount;
        this.pointerVariableIds = context.pointerVariableIds;
        this.pointerIndexByVariable = context.pointerIndexByVariable;
        this.variableCount = context.variables.length;
        this.varWords = Math.max(1, (variableCount + 63) >>> 6);
        this.paramCount = templates.paramCount;
        this.paramWords = Math.max(1, (paramCount + 63) >>> 6);
        this.siteCount = context.siteCount;
        this.siteWords = Math.max(1, (siteCount + 63) >>> 6);
        this.templateWords = templates.words;

        allocateState();
    }

    private void allocateState() {
        nullState = new byte[pointerCount];
        cellTargets = new long[pointerCount * varWords];
        symValue = new long[pointerCount * templateWords];
        symPointee = new long[pointerCount * templateWords];
        symCell = new long[pointerCount * templateWords];
        derefMust = new long[paramWords];
        derefMay = new long[paramWords];
        pointeeDerefMust = new long[paramWords];
        pointeeDerefMay = new long[paramWords];
        slotMust = new long[siteWords];
        slotMay = new long[siteWords];
        slotSource = new long[siteCount * templateWords];
        slotTargetParam = new int[siteCount];
        Arrays.fill(slotTargetParam, -1);
    }

    void reset() {
        Arrays.fill(nullState, MAYBE_NULL);
        Arrays.fill(cellTargets, 0L);
        Arrays.fill(symValue, 0L);
        Arrays.fill(symPointee, 0L);
        Arrays.fill(symCell, 0L);
        Arrays.fill(derefMust, 0L);
        Arrays.fill(derefMay, 0L);
        Arrays.fill(pointeeDerefMust, 0L);
        Arrays.fill(pointeeDerefMay, 0L);
        Arrays.fill(slotMust, 0L);
        Arrays.fill(slotMay, 0L);
        Arrays.fill(slotSource, 0L);
        Arrays.fill(slotTargetParam, -1);
        snapshotCount = 0;
    }

    int pointerIndex(Symbol symbol) {
        int variableId = context.variableId(symbol);

        if (variableId < 0) {
            return -1;
        }

        return pointerIndexByVariable[variableId];
    }

    int pointerIndexByVariableId(int variableId) {
        if (variableId < 0 || variableId >= pointerIndexByVariable.length) {
            return -1;
        }

        return pointerIndexByVariable[variableId];
    }

    Symbol pointerVariable(int pointerIndex) {
        return context.variable(pointerVariableIds[pointerIndex]);
    }

    int variableIdOf(int pointerIndex) {
        return pointerVariableIds[pointerIndex];
    }

    byte stateOf(int pointerIndex) {
        return nullState[pointerIndex];
    }

    void setState(int pointerIndex, byte state) {
        nullState[pointerIndex] = state;
    }

    void clearCellTargets(int pointerIndex) {
        int offset = pointerIndex * varWords;
        Arrays.fill(cellTargets, offset, offset + varWords, 0L);
    }

    void setCellTarget(int pointerIndex, int variableId) {
        if (variableId < 0 || variableId >= variableCount) {
            return;
        }

        cellTargets[pointerIndex * varWords + (variableId >>> 6)]
                |= 1L << (variableId & 63);
    }

    void unionCellTargets(int targetPointerIndex, int sourcePointerIndex) {
        int targetOffset = targetPointerIndex * varWords;
        int sourceOffset = sourcePointerIndex * varWords;

        for (int i = 0; i < varWords; i++) {
            cellTargets[targetOffset + i] |= cellTargets[sourceOffset + i];
        }
    }

    void copyCellTargets(int targetPointerIndex, int sourcePointerIndex) {
        int targetOffset = targetPointerIndex * varWords;
        int sourceOffset = sourcePointerIndex * varWords;
        System.arraycopy(
                cellTargets,
                sourceOffset,
                cellTargets,
                targetOffset,
                varWords
        );
    }

    boolean cellTargetHas(int pointerIndex, int variableId) {
        if (variableId < 0 || variableId >= variableCount) {
            return false;
        }

        return (cellTargets[pointerIndex * varWords + (variableId >>> 6)]
                & (1L << (variableId & 63))) != 0L;
    }

    int singleCellTarget(int pointerIndex) {
        int offset = pointerIndex * varWords;
        int found = -1;

        for (int word = 0; word < varWords; word++) {
            long bits = cellTargets[offset + word];

            while (bits != 0L) {
                int bit = Long.numberOfTrailingZeros(bits);
                int variableId = (word << 6) + bit;

                if (found >= 0) {
                    return -1;
                }

                found = variableId;
                bits &= bits - 1;
            }
        }

        return found;
    }

    int symValueOffset(int pointerIndex) {
        return pointerIndex * templateWords;
    }

    int symPointeeOffset(int pointerIndex) {
        return pointerIndex * templateWords;
    }

    int symCellOffset(int pointerIndex) {
        return pointerIndex * templateWords;
    }

    int slotSourceOffset(int siteId) {
        return siteId * templateWords;
    }

    void markDeref(int param, boolean definite) {
        if (param < 0 || param >= paramCount) {
            return;
        }

        derefMust[param >>> 6] |= 1L << (param & 63);
        derefMay[param >>> 6] |= 1L << (param & 63);
    }

    void markPointeeDeref(int param) {
        if (param < 0 || param >= paramCount) {
            return;
        }

        pointeeDerefMust[param >>> 6] |= 1L << (param & 63);
        pointeeDerefMay[param >>> 6] |= 1L << (param & 63);
    }

    boolean derefMustAt(int slot, int param) {
        return bitAt(derefMustSnapshots, slot * paramWords, param);
    }

    boolean derefMayAt(int slot, int param) {
        return bitAt(derefMaySnapshots, slot * paramWords, param);
    }

    boolean pointeeDerefMustAt(int slot, int param) {
        return bitAt(pointeeDerefMustSnapshots, slot * paramWords, param);
    }

    boolean pointeeDerefMayAt(int slot, int param) {
        return bitAt(pointeeDerefMaySnapshots, slot * paramWords, param);
    }

    boolean slotMustAt(int slot, int siteId) {
        return bitAt(slotMustSnapshots, slot * siteWords, siteId);
    }

    boolean slotMayAt(int slot, int siteId) {
        return bitAt(slotMaySnapshots, slot * siteWords, siteId);
    }

    private static boolean bitAt(long[] bits, int offset, int index) {
        return (bits[offset + (index >>> 6)] & (1L << (index & 63))) != 0L;
    }

    int snapshot() {
        ensureCapacity(snapshotCount + 1);

        int slot = snapshotCount++;

        System.arraycopy(
                nullState,
                0,
                nullSnapshots,
                slot * pointerCount,
                pointerCount
        );
        System.arraycopy(
                cellTargets,
                0,
                cellSnapshots,
                slot * pointerCount * varWords,
                pointerCount * varWords
        );
        System.arraycopy(
                symValue,
                0,
                symValueSnapshots,
                slot * pointerCount * templateWords,
                pointerCount * templateWords
        );
        System.arraycopy(
                symPointee,
                0,
                symPointeeSnapshots,
                slot * pointerCount * templateWords,
                pointerCount * templateWords
        );
        System.arraycopy(
                symCell,
                0,
                symCellSnapshots,
                slot * pointerCount * templateWords,
                pointerCount * templateWords
        );
        System.arraycopy(
                derefMust,
                0,
                derefMustSnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                derefMay,
                0,
                derefMaySnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                pointeeDerefMust,
                0,
                pointeeDerefMustSnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                pointeeDerefMay,
                0,
                pointeeDerefMaySnapshots,
                slot * paramWords,
                paramWords
        );
        System.arraycopy(
                slotMust,
                0,
                slotMustSnapshots,
                slot * siteWords,
                siteWords
        );
        System.arraycopy(
                slotMay,
                0,
                slotMaySnapshots,
                slot * siteWords,
                siteWords
        );
        System.arraycopy(
                slotSource,
                0,
                slotSourceSnapshots,
                slot * siteCount * templateWords,
                siteCount * templateWords
        );

        return slot;
    }

    void restore(int slot) {
        if (slot < 0) {
            return;
        }

        System.arraycopy(
                nullSnapshots,
                slot * pointerCount,
                nullState,
                0,
                pointerCount
        );
        System.arraycopy(
                cellSnapshots,
                slot * pointerCount * varWords,
                cellTargets,
                0,
                pointerCount * varWords
        );
        System.arraycopy(
                symValueSnapshots,
                slot * pointerCount * templateWords,
                symValue,
                0,
                pointerCount * templateWords
        );
        System.arraycopy(
                symPointeeSnapshots,
                slot * pointerCount * templateWords,
                symPointee,
                0,
                pointerCount * templateWords
        );
        System.arraycopy(
                symCellSnapshots,
                slot * pointerCount * templateWords,
                symCell,
                0,
                pointerCount * templateWords
        );
        System.arraycopy(
                derefMustSnapshots,
                slot * paramWords,
                derefMust,
                0,
                paramWords
        );
        System.arraycopy(
                derefMaySnapshots,
                slot * paramWords,
                derefMay,
                0,
                paramWords
        );
        System.arraycopy(
                pointeeDerefMustSnapshots,
                slot * paramWords,
                pointeeDerefMust,
                0,
                paramWords
        );
        System.arraycopy(
                pointeeDerefMaySnapshots,
                slot * paramWords,
                pointeeDerefMay,
                0,
                paramWords
        );
        System.arraycopy(
                slotMustSnapshots,
                slot * siteWords,
                slotMust,
                0,
                siteWords
        );
        System.arraycopy(
                slotMaySnapshots,
                slot * siteWords,
                slotMay,
                0,
                siteWords
        );
        System.arraycopy(
                slotSourceSnapshots,
                slot * siteCount * templateWords,
                slotSource,
                0,
                siteCount * templateWords
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

        nullSnapshots = grow(nullSnapshots, newCapacity * pointerCount);
        cellSnapshots = grow(
                cellSnapshots,
                newCapacity * pointerCount * varWords
        );
        symValueSnapshots = grow(
                symValueSnapshots,
                newCapacity * pointerCount * templateWords
        );
        symPointeeSnapshots = grow(
                symPointeeSnapshots,
                newCapacity * pointerCount * templateWords
        );
        symCellSnapshots = grow(
                symCellSnapshots,
                newCapacity * pointerCount * templateWords
        );
        derefMustSnapshots = grow(
                derefMustSnapshots,
                newCapacity * paramWords
        );
        derefMaySnapshots = grow(
                derefMaySnapshots,
                newCapacity * paramWords
        );
        pointeeDerefMustSnapshots = grow(
                pointeeDerefMustSnapshots,
                newCapacity * paramWords
        );
        pointeeDerefMaySnapshots = grow(
                pointeeDerefMaySnapshots,
                newCapacity * paramWords
        );
        slotMustSnapshots = grow(
                slotMustSnapshots,
                newCapacity * siteWords
        );
        slotMaySnapshots = grow(
                slotMaySnapshots,
                newCapacity * siteWords
        );
        slotSourceSnapshots = grow(
                slotSourceSnapshots,
                newCapacity * siteCount * templateWords
        );

        capacity = newCapacity;
    }

    private static byte[] grow(byte[] array, int size) {
        return Arrays.copyOf(array, size);
    }

    private static long[] grow(long[] array, int size) {
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

    private void mergeFromSnapshot(int slot) {
        for (int p = 0; p < pointerCount; p++) {
            byte current = nullState[p];
            byte incoming = nullSnapshots[slot * pointerCount + p];

            if (current != incoming) {
                nullState[p] = MAYBE_NULL;
            }
        }

        for (int i = 0; i < pointerCount * varWords; i++) {
            cellTargets[i] |= cellSnapshots[slot * pointerCount * varWords + i];
        }

        for (int i = 0; i < pointerCount * templateWords; i++) {
            symValue[i] |= symValueSnapshots[
                    slot * pointerCount * templateWords + i];
            symPointee[i] |= symPointeeSnapshots[
                    slot * pointerCount * templateWords + i];
            symCell[i] |= symCellSnapshots[
                    slot * pointerCount * templateWords + i];
        }

        for (int i = 0; i < paramWords; i++) {
            derefMust[i] &= derefMustSnapshots[slot * paramWords + i];
            derefMay[i] |= derefMaySnapshots[slot * paramWords + i];
            pointeeDerefMust[i] &=
                    pointeeDerefMustSnapshots[slot * paramWords + i];
            pointeeDerefMay[i] |=
                    pointeeDerefMaySnapshots[slot * paramWords + i];
        }

        for (int i = 0; i < siteWords; i++) {
            slotMust[i] &= slotMustSnapshots[slot * siteWords + i];
            slotMay[i] |= slotMaySnapshots[slot * siteWords + i];
        }

        for (int i = 0; i < siteCount * templateWords; i++) {
            slotSource[i] |= slotSourceSnapshots[
                    slot * siteCount * templateWords + i];
        }
    }
}
