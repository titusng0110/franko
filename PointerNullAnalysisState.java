import java.util.Arrays;
import java.util.Locale;

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
    final long snapshotRowBytes;
    final int snapshotLimitSlots;

    private int[] activePointerIndices = new int[0];
    private int[] activeVariableWordIds = new int[0];
    private int[] activeAssignmentSiteIds = new int[0];
    private int[] activeAssignmentSiteWordIds = new int[0];
    private int[] activeAssignmentSiteWordIndexByWord = new int[0];

    private int activePointerCount;
    private int activeVariableWordCount;
    private int activeAssignmentSiteCount;
    private int activeAssignmentSiteWordCount;
    private int currentFunctionId = -1;

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
        this.snapshotRowBytes =
                (long) context.maxActivePointerCount
                + 8L
                        * context.maxActivePointerCount
                        * context.maxActiveVariableWordCount
                + 24L
                        * context.maxActivePointerCount
                        * templateWords
                + 32L * paramWords
                + 16L * context.maxActiveAssignmentSiteWordCount
                + 8L
                        * context.maxActiveAssignmentSiteCount
                        * templateWords;
        this.snapshotLimitSlots = snapshotLimitSlots(snapshotRowBytes);

        allocateState();

        int initialCapacity = Math.min(
                Math.max(1, context.maxSnapshotDemand),
                snapshotLimitSlots
        );
        allocateSnapshots(initialCapacity);
    }

    private static int snapshotLimitSlots(long snapshotRowBytes) {
        if (snapshotRowBytes <= 0) {
            return Integer.MAX_VALUE;
        }

        long slots = MemoryAnalysisContext.MAX_SNAPSHOT_ARENA_BYTES
                / snapshotRowBytes;
        return (int) Math.min(Integer.MAX_VALUE, slots);
    }

    long snapshotRowBytes() {
        return snapshotRowBytes;
    }

    private String snapshotComplexity() {
        return count(activeAssignmentSiteCount, "assignment site")
                + " and "
                + count(activePointerCount, "active pointer");
    }

    private static String count(int value, String noun) {
        return String.format(Locale.ROOT, "%,d %s%s",
                value, noun, value == 1 ? "" : "s");
    }

    private String snapshotRowOwners() {
        StringBuilder result = new StringBuilder();

        appendOwner(
                result,
                "assignment site",
                context.maxActiveAssignmentSiteCount,
                context.maxActiveAssignmentSiteFunctionId
        );
        appendOwner(
                result,
                "active pointer",
                context.maxActivePointerCount,
                context.maxActivePointerFunctionId
        );
        appendOwner(
                result,
                "variable word",
                context.maxActiveVariableWordCount,
                context.maxActiveVariableWordFunctionId
        );
        appendOwner(
                result,
                "assignment-site word",
                context.maxActiveAssignmentSiteWordCount,
                context.maxActiveAssignmentSiteWordFunctionId
        );

        return result.toString();
    }

    private void appendOwner(
            StringBuilder result,
            String noun,
            int value,
            int functionId
    ) {
        if (value <= 0 || functionId < 0 || functionId == currentFunctionId) {
            return;
        }

        if (result.length() > 0) {
            result.append(", ");
        }

        result.append("function '")
                .append(functionName(functionId))
                .append("' with ")
                .append(count(value, noun));
    }

    private String functionName(int functionId) {
        if (functionId < 0
                || functionId >= context.callGraph.functions.length) {
            return "<unknown>";
        }

        Symbol symbol = context.callGraph.functions[functionId];
        return symbol == null ? "<unknown>" : symbol.qualifiedName();
    }

    void setFunction(int functionId) {
        currentFunctionId = functionId;
        activePointerIndices = context.functionPointerIndices[functionId];
        activeVariableWordIds = context.functionVariableWordIds[functionId];
        activeAssignmentSiteIds = context.functionAssignmentSiteIds[functionId];
        activeAssignmentSiteWordIds =
                context.functionAssignmentSiteWordIds[functionId];
        activePointerCount = activePointerIndices.length;
        activeVariableWordCount = activeVariableWordIds.length;
        activeAssignmentSiteCount = activeAssignmentSiteIds.length;
        activeAssignmentSiteWordCount = activeAssignmentSiteWordIds.length;

        if (activeAssignmentSiteWordIndexByWord.length != siteWords) {
            activeAssignmentSiteWordIndexByWord = new int[siteWords];
        }

        Arrays.fill(activeAssignmentSiteWordIndexByWord, -1);

        for (int i = 0; i < activeAssignmentSiteWordIds.length; i++) {
            activeAssignmentSiteWordIndexByWord[
                    activeAssignmentSiteWordIds[i]
            ] = i;
        }
    }

    int activeAssignmentSiteCount() {
        return activeAssignmentSiteCount;
    }

    int activeAssignmentSiteId(int index) {
        return activeAssignmentSiteIds[index];
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
        for (int pointer : activePointerIndices) {
            nullState[pointer] = MAYBE_NULL;
            Arrays.fill(
                    cellTargets,
                    pointer * varWords,
                    pointer * varWords + varWords,
                    0L
            );
            Arrays.fill(
                    symValue,
                    pointer * templateWords,
                    pointer * templateWords + templateWords,
                    0L
            );
            Arrays.fill(
                    symPointee,
                    pointer * templateWords,
                    pointer * templateWords + templateWords,
                    0L
            );
            Arrays.fill(
                    symCell,
                    pointer * templateWords,
                    pointer * templateWords + templateWords,
                    0L
            );
        }

        Arrays.fill(derefMust, 0L);
        Arrays.fill(derefMay, 0L);
        Arrays.fill(pointeeDerefMust, 0L);
        Arrays.fill(pointeeDerefMay, 0L);

        for (int word : activeAssignmentSiteWordIds) {
            slotMust[word] = 0L;
            slotMay[word] = 0L;
        }

        for (int site : activeAssignmentSiteIds) {
            Arrays.fill(
                    slotSource,
                    site * templateWords,
                    site * templateWords + templateWords,
                    0L
            );
            slotTargetParam[site] = -1;
        }

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
        int word = siteId >>> 6;
        int localWord = activeAssignmentSiteWordIndexByWord[word];

        if (localWord < 0) {
            return false;
        }

        return (slotMustSnapshots[
                slot * activeAssignmentSiteWordCount + localWord
        ] & (1L << (siteId & 63))) != 0L;
    }

    boolean slotMayAt(int slot, int siteId) {
        int word = siteId >>> 6;
        int localWord = activeAssignmentSiteWordIndexByWord[word];

        if (localWord < 0) {
            return false;
        }

        return (slotMaySnapshots[
                slot * activeAssignmentSiteWordCount + localWord
        ] & (1L << (siteId & 63))) != 0L;
    }

    private static boolean bitAt(long[] bits, int offset, int index) {
        return (bits[offset + (index >>> 6)] & (1L << (index & 63))) != 0L;
    }

    int snapshot() {
        ensureCapacity(snapshotCount + 1);

        int slot = snapshotCount++;

        int pointerBase = slot * activePointerCount;
        int cellBase = slot
                * activePointerCount
                * activeVariableWordCount;
        int symbolBase = slot * activePointerCount * templateWords;
        int parameterBase = slot * paramWords;
        int siteWordBase = slot * activeAssignmentSiteWordCount;
        int sourceBase = slot
                * activeAssignmentSiteCount
                * templateWords;

        for (int i = 0; i < activePointerCount; i++) {
            int pointer = activePointerIndices[i];
            nullSnapshots[pointerBase + i] = nullState[pointer];

            for (int j = 0; j < activeVariableWordCount; j++) {
                int word = activeVariableWordIds[j];
                cellSnapshots[cellBase + i * activeVariableWordCount + j] =
                        cellTargets[pointer * varWords + word];
            }

            int symbolOffset = symbolBase + i * templateWords;
            int stateOffset = pointer * templateWords;
            System.arraycopy(
                    symValue,
                    stateOffset,
                    symValueSnapshots,
                    symbolOffset,
                    templateWords
            );
            System.arraycopy(
                    symPointee,
                    stateOffset,
                    symPointeeSnapshots,
                    symbolOffset,
                    templateWords
            );
            System.arraycopy(
                    symCell,
                    stateOffset,
                    symCellSnapshots,
                    symbolOffset,
                    templateWords
            );
        }

        System.arraycopy(
                derefMust,
                0,
                derefMustSnapshots,
                parameterBase,
                paramWords
        );
        System.arraycopy(
                derefMay,
                0,
                derefMaySnapshots,
                parameterBase,
                paramWords
        );
        System.arraycopy(
                pointeeDerefMust,
                0,
                pointeeDerefMustSnapshots,
                parameterBase,
                paramWords
        );
        System.arraycopy(
                pointeeDerefMay,
                0,
                pointeeDerefMaySnapshots,
                parameterBase,
                paramWords
        );

        for (int i = 0; i < activeAssignmentSiteWordCount; i++) {
            int word = activeAssignmentSiteWordIds[i];
            slotMustSnapshots[siteWordBase + i] = slotMust[word];
            slotMaySnapshots[siteWordBase + i] = slotMay[word];
        }

        for (int i = 0; i < activeAssignmentSiteCount; i++) {
            int site = activeAssignmentSiteIds[i];
            System.arraycopy(
                    slotSource,
                    site * templateWords,
                    slotSourceSnapshots,
                    sourceBase + i * templateWords,
                    templateWords
            );
        }

        return slot;
    }

    void restore(int slot) {
        if (slot < 0) {
            return;
        }

        int pointerBase = slot * activePointerCount;
        int cellBase = slot
                * activePointerCount
                * activeVariableWordCount;
        int symbolBase = slot * activePointerCount * templateWords;
        int parameterBase = slot * paramWords;
        int siteWordBase = slot * activeAssignmentSiteWordCount;
        int sourceBase = slot
                * activeAssignmentSiteCount
                * templateWords;

        for (int i = 0; i < activePointerCount; i++) {
            int pointer = activePointerIndices[i];
            nullState[pointer] = nullSnapshots[pointerBase + i];

            for (int j = 0; j < activeVariableWordCount; j++) {
                int word = activeVariableWordIds[j];
                cellTargets[pointer * varWords + word] =
                        cellSnapshots[
                                cellBase + i * activeVariableWordCount + j
                        ];
            }

            int symbolOffset = symbolBase + i * templateWords;
            int stateOffset = pointer * templateWords;
            System.arraycopy(
                    symValueSnapshots,
                    symbolOffset,
                    symValue,
                    stateOffset,
                    templateWords
            );
            System.arraycopy(
                    symPointeeSnapshots,
                    symbolOffset,
                    symPointee,
                    stateOffset,
                    templateWords
            );
            System.arraycopy(
                    symCellSnapshots,
                    symbolOffset,
                    symCell,
                    stateOffset,
                    templateWords
            );
        }

        System.arraycopy(
                derefMustSnapshots,
                parameterBase,
                derefMust,
                0,
                paramWords
        );
        System.arraycopy(
                derefMaySnapshots,
                parameterBase,
                derefMay,
                0,
                paramWords
        );
        System.arraycopy(
                pointeeDerefMustSnapshots,
                parameterBase,
                pointeeDerefMust,
                0,
                paramWords
        );
        System.arraycopy(
                pointeeDerefMaySnapshots,
                parameterBase,
                pointeeDerefMay,
                0,
                paramWords
        );

        for (int i = 0; i < activeAssignmentSiteWordCount; i++) {
            int word = activeAssignmentSiteWordIds[i];
            slotMust[word] = slotMustSnapshots[siteWordBase + i];
            slotMay[word] = slotMaySnapshots[siteWordBase + i];
        }

        for (int i = 0; i < activeAssignmentSiteCount; i++) {
            int site = activeAssignmentSiteIds[i];
            System.arraycopy(
                    slotSourceSnapshots,
                    sourceBase + i * templateWords,
                    slotSource,
                    site * templateWords,
                    templateWords
            );
        }
    }

    private void ensureCapacity(int required) {
        if (required <= capacity) {
            return;
        }

        if (required > snapshotLimitSlots) {
            long projectedBytes = (long) required * snapshotRowBytes;
            throw new MemoryAnalysisContext.SnapshotLimitExceeded(
                    required,
                    projectedBytes,
                    snapshotComplexity(),
                    snapshotRowOwners()
            );
        }

        allocateSnapshots(required);
    }

    private void allocateSnapshots(int newCapacity) {
        nullSnapshots = grow(
                nullSnapshots,
                newCapacity * context.maxActivePointerCount
        );
        cellSnapshots = grow(
                cellSnapshots,
                newCapacity
                        * context.maxActivePointerCount
                        * context.maxActiveVariableWordCount
        );
        symValueSnapshots = grow(
                symValueSnapshots,
                newCapacity * context.maxActivePointerCount * templateWords
        );
        symPointeeSnapshots = grow(
                symPointeeSnapshots,
                newCapacity * context.maxActivePointerCount * templateWords
        );
        symCellSnapshots = grow(
                symCellSnapshots,
                newCapacity * context.maxActivePointerCount * templateWords
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
                newCapacity * context.maxActiveAssignmentSiteWordCount
        );
        slotMaySnapshots = grow(
                slotMaySnapshots,
                newCapacity * context.maxActiveAssignmentSiteWordCount
        );
        slotSourceSnapshots = grow(
                slotSourceSnapshots,
                newCapacity
                        * context.maxActiveAssignmentSiteCount
                        * templateWords
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
        int pointerBase = slot * activePointerCount;
        int cellBase = slot
                * activePointerCount
                * activeVariableWordCount;
        int symbolBase = slot * activePointerCount * templateWords;
        int parameterBase = slot * paramWords;
        int siteWordBase = slot * activeAssignmentSiteWordCount;
        int sourceBase = slot
                * activeAssignmentSiteCount
                * templateWords;

        for (int i = 0; i < activePointerCount; i++) {
            int pointer = activePointerIndices[i];
            byte current = nullState[pointer];
            byte incoming = nullSnapshots[pointerBase + i];

            if (current != incoming) {
                nullState[pointer] = MAYBE_NULL;
            }

            for (int j = 0; j < activeVariableWordCount; j++) {
                int word = activeVariableWordIds[j];
                cellTargets[pointer * varWords + word] |=
                        cellSnapshots[
                                cellBase + i * activeVariableWordCount + j
                        ];
            }

            int symbolOffset = symbolBase + i * templateWords;
            int stateOffset = pointer * templateWords;

            for (int word = 0; word < templateWords; word++) {
                symValue[stateOffset + word] |=
                        symValueSnapshots[symbolOffset + word];
                symPointee[stateOffset + word] |=
                        symPointeeSnapshots[symbolOffset + word];
                symCell[stateOffset + word] |=
                        symCellSnapshots[symbolOffset + word];
            }
        }

        for (int i = 0; i < paramWords; i++) {
            derefMust[i] &= derefMustSnapshots[parameterBase + i];
            derefMay[i] |= derefMaySnapshots[parameterBase + i];
            pointeeDerefMust[i] &=
                    pointeeDerefMustSnapshots[parameterBase + i];
            pointeeDerefMay[i] |=
                    pointeeDerefMaySnapshots[parameterBase + i];
        }

        for (int i = 0; i < activeAssignmentSiteWordCount; i++) {
            int word = activeAssignmentSiteWordIds[i];
            slotMust[word] &= slotMustSnapshots[siteWordBase + i];
            slotMay[word] |= slotMaySnapshots[siteWordBase + i];
        }

        for (int i = 0; i < activeAssignmentSiteCount; i++) {
            int site = activeAssignmentSiteIds[i];
            int snapshotOffset = sourceBase + i * templateWords;
            int stateOffset = site * templateWords;

            for (int word = 0; word < templateWords; word++) {
                slotSource[stateOffset + word] |=
                        slotSourceSnapshots[snapshotOffset + word];
            }
        }
    }
}
