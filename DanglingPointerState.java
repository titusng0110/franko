import java.util.Arrays;
import java.util.Locale;

final class DanglingPointerState {
    static final int FLAG_UNKNOWN = 1;
    static final int FLAG_NONNULL = 2;

    static final int BINDING_STRIDE = 4;
    static final int BINDING_VALUE = 0;
    static final int BINDING_POINTEE = 1;
    static final int BINDING_ROOT_ARG = 2;
    static final int BINDING_CELL = 3;

    final MemoryAnalysisContext context;
    final MemoryTemplateRegistry templates;

    final int pointerCount;
    final int[] pointerVariableIds;
    final int[] pointerIndexByVariable;

    final int variableCount;
    final int varWords;
    final int siteCount;
    final int siteWords;
    final int templateWords;

    long[] frameBits;
    long[] globalBits;
    byte[] originFlags;
    long[] mustDead;
    long[] mayDead;

    long[] symValue;
    long[] symPointee;
    long[] symCell;
    long[] captureMust;
    long[] captureMay;
    long[] captureSource;
    int[] captureSinkKind;
    int[] captureSinkParam;
    int[] captureSinkGlobal;
    String[] captureFieldPath;

    private long[] frameSnapshots = new long[0];
    private long[] globalSnapshots = new long[0];
    private byte[] flagSnapshots = new byte[0];
    private long[] mustDeadSnapshots = new long[0];
    private long[] mayDeadSnapshots = new long[0];
    private long[] symValueSnapshots = new long[0];
    private long[] symPointeeSnapshots = new long[0];
    private long[] symCellSnapshots = new long[0];
    private long[] captureMustSnapshots = new long[0];
    private long[] captureMaySnapshots = new long[0];
    private long[] captureSourceSnapshots = new long[0];

    int capacity;
    int snapshotCount;
    final long snapshotRowBytes;
    final int snapshotLimitSlots;

    private int[] activePointerIndices = new int[0];
    private int[] activeVariableWordIds = new int[0];
    private int[] activeAssignmentSiteIds = new int[0];
    private int[] activeAssignmentSiteWordIds = new int[0];
    private int[] activeVariableWordIndexByWord = new int[0];
    private int[] activeAssignmentSiteWordIndexByWord = new int[0];

    private int activePointerCount;
    private int activeVariableWordCount;
    private int activeAssignmentSiteCount;
    private int activeAssignmentSiteWordCount;
    private int currentFunctionId = -1;

    private int[][] blockLocals = new int[32][];
    private int blockDepth;

    DanglingPointerState(MemoryAnalysisContext context) {
        this.context = context;
        this.templates = context.templates;
        this.pointerCount = context.pointerVariableCount;
        this.pointerVariableIds = context.pointerVariableIds;
        this.pointerIndexByVariable = context.pointerIndexByVariable;
        this.variableCount = context.variables.length;
        this.varWords = Math.max(1, (variableCount + 63) >>> 6);
        this.siteCount = context.siteCount;
        this.siteWords = Math.max(1, (siteCount + 63) >>> 6);
        this.templateWords = templates.words;
        this.snapshotRowBytes =
                16L
                        * context.maxActivePointerCount
                        * context.maxActiveVariableWordCount
                + context.maxActivePointerCount
                + 16L * context.maxActiveVariableWordCount
                + 24L
                        * context.maxActivePointerCount
                        * templateWords
                + 16L * context.maxActiveAssignmentSiteWordCount
                + 8L
                        * context.maxActiveAssignmentSiteCount
                        * templateWords;
        this.snapshotLimitSlots = snapshotLimitSlots(snapshotRowBytes);

        frameBits = new long[pointerCount * varWords];
        globalBits = new long[pointerCount * varWords];
        originFlags = new byte[pointerCount];
        mustDead = new long[varWords];
        mayDead = new long[varWords];
        symValue = new long[pointerCount * templateWords];
        symPointee = new long[pointerCount * templateWords];
        symCell = new long[pointerCount * templateWords];
        captureMust = new long[siteWords];
        captureMay = new long[siteWords];
        captureSource = new long[siteCount * templateWords];
        captureSinkKind = new int[siteCount];
        captureSinkParam = new int[siteCount];
        captureSinkGlobal = new int[siteCount];
        captureFieldPath = new String[siteCount];
        Arrays.fill(captureSinkKind, -1);
        Arrays.fill(captureSinkParam, -1);
        Arrays.fill(captureSinkGlobal, -1);

        allocateSnapshots(Math.min(
                Math.max(1, context.maxSnapshotDemand),
                snapshotLimitSlots
        ));
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

        if (activeVariableWordIndexByWord.length != varWords) {
            activeVariableWordIndexByWord = new int[varWords];
        }

        Arrays.fill(activeVariableWordIndexByWord, -1);

        for (int i = 0; i < activeVariableWordIds.length; i++) {
            activeVariableWordIndexByWord[activeVariableWordIds[i]] = i;
        }

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

    void reset() {
        for (int pointer : activePointerIndices) {
            Arrays.fill(
                    frameBits,
                    pointer * varWords,
                    pointer * varWords + varWords,
                    0L
            );
            Arrays.fill(
                    globalBits,
                    pointer * varWords,
                    pointer * varWords + varWords,
                    0L
            );
            originFlags[pointer] = 0;

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

        for (int word : activeVariableWordIds) {
            mustDead[word] = 0L;
            mayDead[word] = 0L;
        }

        for (int word : activeAssignmentSiteWordIds) {
            captureMust[word] = 0L;
            captureMay[word] = 0L;
        }

        for (int site : activeAssignmentSiteIds) {
            Arrays.fill(
                    captureSource,
                    site * templateWords,
                    site * templateWords + templateWords,
                    0L
            );
            captureSinkKind[site] = -1;
            captureSinkParam[site] = -1;
            captureSinkGlobal[site] = -1;
            captureFieldPath[site] = null;
        }

        snapshotCount = 0;
        blockDepth = 0;
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

    int variableIdOf(int pointerIndex) {
        return pointerVariableIds[pointerIndex];
    }

    void clearPointer(int pointerIndex) {
        int offset = pointerIndex * varWords;
        Arrays.fill(frameBits, offset, offset + varWords, 0L);
        Arrays.fill(globalBits, offset, offset + varWords, 0L);
        originFlags[pointerIndex] = 0;
    }

    void setPointer(int pointerIndex, DanglingOrigin origin) {
        int offset = pointerIndex * varWords;
        System.arraycopy(origin.frame, 0, frameBits, offset, varWords);
        System.arraycopy(origin.global, 0, globalBits, offset, varWords);
        originFlags[pointerIndex] = origin.flags();
    }

    void copyPointer(int target, int source) {
        int targetOffset = target * varWords;
        int sourceOffset = source * varWords;
        System.arraycopy(frameBits, sourceOffset, frameBits, targetOffset, varWords);
        System.arraycopy(globalBits, sourceOffset, globalBits, targetOffset, varWords);
        originFlags[target] = originFlags[source];
    }

    DanglingOrigin pointerOrigin(int pointerIndex) {
        int offset = pointerIndex * varWords;
        long[] frame = Arrays.copyOfRange(frameBits, offset, offset + varWords);
        long[] global = Arrays.copyOfRange(globalBits, offset, offset + varWords);
        return new DanglingOrigin(
                frame,
                global,
                (originFlags[pointerIndex] & FLAG_UNKNOWN) != 0,
                (originFlags[pointerIndex] & FLAG_NONNULL) != 0
        );
    }

    void setSymValue(int pointerIndex, long[] set) {
        System.arraycopy(
                set,
                0,
                symValue,
                pointerIndex * templateWords,
                templateWords
        );
    }

    void setSymPointee(int pointerIndex, long[] set) {
        System.arraycopy(
                set,
                0,
                symPointee,
                pointerIndex * templateWords,
                templateWords
        );
    }

    long[] symValueOf(int pointerIndex) {
        int offset = pointerIndex * templateWords;
        return Arrays.copyOfRange(symValue, offset, offset + templateWords);
    }

    long[] symPointeeOf(int pointerIndex) {
        int offset = pointerIndex * templateWords;
        return Arrays.copyOfRange(symPointee, offset, offset + templateWords);
    }

    void setSymCell(int pointerIndex, long[] set) {
        System.arraycopy(
                set,
                0,
                symCell,
                pointerIndex * templateWords,
                templateWords
        );
    }

    long[] symCellOf(int pointerIndex) {
        int offset = pointerIndex * templateWords;
        return Arrays.copyOfRange(symCell, offset, offset + templateWords);
    }

    boolean captureMustAt(int slot, int site) {
        int word = site >>> 6;
        int localWord = activeAssignmentSiteWordIndexByWord[word];

        if (localWord < 0) {
            return false;
        }

        return (captureMustSnapshots[
                slot * activeAssignmentSiteWordCount + localWord
        ] & (1L << (site & 63))) != 0L;
    }

    boolean captureMayAt(int slot, int site) {
        int word = site >>> 6;
        int localWord = activeAssignmentSiteWordIndexByWord[word];

        if (localWord < 0) {
            return false;
        }

        return (captureMaySnapshots[
                slot * activeAssignmentSiteWordCount + localWord
        ] & (1L << (site & 63))) != 0L;
    }

    boolean mustDead(int variableId) {        return variableId >= 0
                && (mustDead[variableId >>> 6] & (1L << (variableId & 63))) != 0L;
    }

    boolean mayDead(int variableId) {
        return variableId >= 0
                && (mayDead[variableId >>> 6] & (1L << (variableId & 63))) != 0L;
    }

    void markAlive(int variableId) {
        if (variableId < 0) {
            return;
        }

        mustDead[variableId >>> 6] &= ~(1L << (variableId & 63));
        mayDead[variableId >>> 6] &= ~(1L << (variableId & 63));
    }

    void markDead(int variableId) {
        if (variableId < 0) {
            return;
        }

        mustDead[variableId >>> 6] |= 1L << (variableId & 63);
        mayDead[variableId >>> 6] |= 1L << (variableId & 63);
    }

    void enterBlock(SastNode node, int depth) {
        if (depth >= blockLocals.length) {
            blockLocals = Arrays.copyOf(blockLocals, blockLocals.length * 2);
        }

        blockLocals[depth] = context.blockLocalIds(node);
        blockDepth = depth;
    }

    void leaveBlock(SastNode node, int depth, boolean terminated) {
        int[] locals = blockLocals[depth];

        if (!terminated && locals != null) {
            for (int variableId : locals) {
                markDead(variableId);
            }
        }

        blockLocals[depth] = null;
        blockDepth = depth - 1;
    }

    void markLocalsDeadForJump(int targetBlockDepth, int snapshotSlot) {
        int from = Math.max(targetBlockDepth, 0);

        for (int depth = blockDepth; depth >= from; depth--) {
            int[] locals = blockLocals[depth];

            if (locals == null) {
                continue;
            }

            for (int variableId : locals) {
                int word = variableId >>> 6;
                int localWord = activeVariableWordIndexByWord[word];

                if (localWord < 0) {
                    continue;
                }

                int offset = snapshotSlot * activeVariableWordCount + localWord;
                mustDeadSnapshots[offset] |= 1L << (variableId & 63);
                mayDeadSnapshots[offset] |= 1L << (variableId & 63);
            }
        }
    }

    int snapshot() {
        ensureCapacity(snapshotCount + 1);

        int slot = snapshotCount++;

        int pointerBase = slot * activePointerCount;
        int frameBase = slot
                * activePointerCount
                * activeVariableWordCount;
        int variableWordBase = slot * activeVariableWordCount;
        int symbolBase = slot * activePointerCount * templateWords;
        int siteWordBase = slot * activeAssignmentSiteWordCount;
        int sourceBase = slot
                * activeAssignmentSiteCount
                * templateWords;

        for (int i = 0; i < activePointerCount; i++) {
            int pointer = activePointerIndices[i];
            flagSnapshots[pointerBase + i] = originFlags[pointer];

            for (int j = 0; j < activeVariableWordCount; j++) {
                int word = activeVariableWordIds[j];
                int stateOffset = pointer * varWords + word;
                int snapshotOffset = frameBase
                        + i * activeVariableWordCount
                        + j;
                frameSnapshots[snapshotOffset] = frameBits[stateOffset];
                globalSnapshots[snapshotOffset] = globalBits[stateOffset];
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

        for (int i = 0; i < activeVariableWordCount; i++) {
            int word = activeVariableWordIds[i];
            mustDeadSnapshots[variableWordBase + i] = mustDead[word];
            mayDeadSnapshots[variableWordBase + i] = mayDead[word];
        }

        for (int i = 0; i < activeAssignmentSiteWordCount; i++) {
            int word = activeAssignmentSiteWordIds[i];
            captureMustSnapshots[siteWordBase + i] = captureMust[word];
            captureMaySnapshots[siteWordBase + i] = captureMay[word];
        }

        for (int i = 0; i < activeAssignmentSiteCount; i++) {
            int site = activeAssignmentSiteIds[i];
            System.arraycopy(
                    captureSource,
                    site * templateWords,
                    captureSourceSnapshots,
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
        int frameBase = slot
                * activePointerCount
                * activeVariableWordCount;
        int variableWordBase = slot * activeVariableWordCount;
        int symbolBase = slot * activePointerCount * templateWords;
        int siteWordBase = slot * activeAssignmentSiteWordCount;
        int sourceBase = slot
                * activeAssignmentSiteCount
                * templateWords;

        for (int i = 0; i < activePointerCount; i++) {
            int pointer = activePointerIndices[i];
            originFlags[pointer] = flagSnapshots[pointerBase + i];

            for (int j = 0; j < activeVariableWordCount; j++) {
                int word = activeVariableWordIds[j];
                int stateOffset = pointer * varWords + word;
                int snapshotOffset = frameBase
                        + i * activeVariableWordCount
                        + j;
                frameBits[stateOffset] = frameSnapshots[snapshotOffset];
                globalBits[stateOffset] = globalSnapshots[snapshotOffset];
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

        for (int i = 0; i < activeVariableWordCount; i++) {
            int word = activeVariableWordIds[i];
            mustDead[word] = mustDeadSnapshots[variableWordBase + i];
            mayDead[word] = mayDeadSnapshots[variableWordBase + i];
        }

        for (int i = 0; i < activeAssignmentSiteWordCount; i++) {
            int word = activeAssignmentSiteWordIds[i];
            captureMust[word] = captureMustSnapshots[siteWordBase + i];
            captureMay[word] = captureMaySnapshots[siteWordBase + i];
        }

        for (int i = 0; i < activeAssignmentSiteCount; i++) {
            int site = activeAssignmentSiteIds[i];
            System.arraycopy(
                    captureSourceSnapshots,
                    sourceBase + i * templateWords,
                    captureSource,
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
        frameSnapshots = grow(
                frameSnapshots,
                newCapacity
                        * context.maxActivePointerCount
                        * context.maxActiveVariableWordCount
        );
        globalSnapshots = grow(
                globalSnapshots,
                newCapacity
                        * context.maxActivePointerCount
                        * context.maxActiveVariableWordCount
        );
        flagSnapshots = grow(
                flagSnapshots,
                newCapacity * context.maxActivePointerCount
        );
        mustDeadSnapshots = grow(
                mustDeadSnapshots,
                newCapacity * context.maxActiveVariableWordCount
        );
        mayDeadSnapshots = grow(
                mayDeadSnapshots,
                newCapacity * context.maxActiveVariableWordCount
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
        captureMustSnapshots = grow(
                captureMustSnapshots,
                newCapacity * context.maxActiveAssignmentSiteWordCount
        );
        captureMaySnapshots = grow(
                captureMaySnapshots,
                newCapacity * context.maxActiveAssignmentSiteWordCount
        );
        captureSourceSnapshots = grow(
                captureSourceSnapshots,
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
        int frameBase = slot
                * activePointerCount
                * activeVariableWordCount;
        int variableWordBase = slot * activeVariableWordCount;
        int symbolBase = slot * activePointerCount * templateWords;
        int siteWordBase = slot * activeAssignmentSiteWordCount;
        int sourceBase = slot
                * activeAssignmentSiteCount
                * templateWords;

        for (int i = 0; i < activePointerCount; i++) {
            int pointer = activePointerIndices[i];

            for (int j = 0; j < activeVariableWordCount; j++) {
                int word = activeVariableWordIds[j];
                int stateOffset = pointer * varWords + word;
                int snapshotOffset = frameBase
                        + i * activeVariableWordCount
                        + j;
                frameBits[stateOffset] |= frameSnapshots[snapshotOffset];
                globalBits[stateOffset] |= globalSnapshots[snapshotOffset];
            }

            int current = originFlags[pointer] & 0xFF;
            int incoming = flagSnapshots[pointerBase + i] & 0xFF;
            int unknown = (current | incoming) & FLAG_UNKNOWN;
            int nonNull = (current & incoming) & FLAG_NONNULL;
            originFlags[pointer] = (byte) (unknown | nonNull);

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

        for (int i = 0; i < activeVariableWordCount; i++) {
            int word = activeVariableWordIds[i];
            mustDead[word] &= mustDeadSnapshots[variableWordBase + i];
            mayDead[word] |= mayDeadSnapshots[variableWordBase + i];
        }

        for (int i = 0; i < activeAssignmentSiteWordCount; i++) {
            int word = activeAssignmentSiteWordIds[i];
            captureMust[word] &= captureMustSnapshots[siteWordBase + i];
            captureMay[word] |= captureMaySnapshots[siteWordBase + i];
        }

        for (int i = 0; i < activeAssignmentSiteCount; i++) {
            int site = activeAssignmentSiteIds[i];
            int snapshotOffset = sourceBase + i * templateWords;
            int stateOffset = site * templateWords;

            for (int word = 0; word < templateWords; word++) {
                captureSource[stateOffset + word] |=
                        captureSourceSnapshots[snapshotOffset + word];
            }
        }
    }
}

final class DanglingOrigin {
    static final DanglingOrigin EMPTY = new DanglingOrigin(
            new long[0],
            new long[0],
            false,
            false
    );

    static final DanglingOrigin UNKNOWN = new DanglingOrigin(
            new long[0],
            new long[0],
            true,
            false
    );

    final long[] frame;
    final long[] global;
    final boolean unknown;
    final boolean nonNull;

    DanglingOrigin(
            long[] frame,
            long[] global,
            boolean unknown,
            boolean nonNull
    ) {
        this.frame = frame;
        this.global = global;
        this.unknown = unknown;
        this.nonNull = nonNull;
    }

    static DanglingOrigin frame(int variableId, int words) {
        long[] bits = new long[words];
        bits[variableId >>> 6] |= 1L << (variableId & 63);
        return new DanglingOrigin(bits, new long[words], false, true);
    }

    static DanglingOrigin global(int variableId, int words) {
        long[] bits = new long[words];
        bits[variableId >>> 6] |= 1L << (variableId & 63);
        return new DanglingOrigin(new long[words], bits, false, true);
    }

    static DanglingOrigin local(int variableId, int words) {
        return frame(variableId, words);
    }

    static DanglingOrigin nonNull(int words) {
        return new DanglingOrigin(new long[words], new long[words], false, true);
    }

    byte flags() {
        return (byte) ((unknown ? DanglingPointerState.FLAG_UNKNOWN : 0)
                | (nonNull ? DanglingPointerState.FLAG_NONNULL : 0));
    }

    DanglingOrigin withNonNull() {
        if (nonNull) {
            return this;
        }

        return new DanglingOrigin(frame, global, unknown, true);
    }

    DanglingOrigin withoutFrame() {
        return new DanglingOrigin(
                new long[frame.length],
                global,
                unknown,
                nonNull
        );
    }

    DanglingOrigin union(DanglingOrigin other) {
        if (other == null) {
            return this;
        }

        long[] mergedFrame = new long[frame.length];
        long[] mergedGlobal = new long[global.length];

        for (int i = 0; i < frame.length; i++) {
            mergedFrame[i] = frame[i] | other.frame[i];
            mergedGlobal[i] = global[i] | other.global[i];
        }

        return new DanglingOrigin(
                mergedFrame,
                mergedGlobal,
                unknown || other.unknown,
                nonNull && other.nonNull
        );
    }

    boolean hasFrame() {
        for (long word : frame) {
            if (word != 0L) {
                return true;
            }
        }

        return false;
    }

    boolean hasGlobal() {
        for (long word : global) {
            if (word != 0L) {
                return true;
            }
        }

        return false;
    }

    boolean definitelyNull() {
        return !nonNull && !unknown && !hasFrame() && !hasGlobal();
    }

    boolean definiteDangle() {
        return hasFrame() && !unknown && !hasGlobal() && nonNull;
    }

    boolean hasDefiniteDead(long[] mustDeadBits) {
        for (int i = 0; i < frame.length; i++) {
            if ((frame[i] & mustDeadBits[i]) != 0L) {
                return true;
            }
        }

        return false;
    }

    boolean hasPossibleDead(long[] mayDeadBits) {
        for (int i = 0; i < frame.length; i++) {
            if ((frame[i] & mayDeadBits[i]) != 0L) {
                return true;
            }
        }

        return false;
    }
}
