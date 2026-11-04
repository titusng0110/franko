import java.util.Arrays;

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
    }

    void reset() {
        Arrays.fill(frameBits, 0L);
        Arrays.fill(globalBits, 0L);
        Arrays.fill(originFlags, (byte) 0);
        Arrays.fill(mustDead, 0L);
        Arrays.fill(mayDead, 0L);
        Arrays.fill(symValue, 0L);
        Arrays.fill(symPointee, 0L);
        Arrays.fill(symCell, 0L);
        Arrays.fill(captureMust, 0L);
        Arrays.fill(captureMay, 0L);
        Arrays.fill(captureSource, 0L);
        Arrays.fill(captureSinkKind, -1);
        Arrays.fill(captureSinkParam, -1);
        Arrays.fill(captureSinkGlobal, -1);
        Arrays.fill(captureFieldPath, null);
        snapshotCount = 0;
        blockDepth = 0;
    }

    int pointerIndex(VariableSymbol symbol) {
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
        return (captureMustSnapshots[slot * siteWords + (site >>> 6)]
                & (1L << (site & 63))) != 0L;
    }

    boolean captureMayAt(int slot, int site) {
        return (captureMaySnapshots[slot * siteWords + (site >>> 6)]
                & (1L << (site & 63))) != 0L;
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

    void enterBlock(SemanticBlockNode node, int depth) {
        if (depth >= blockLocals.length) {
            blockLocals = Arrays.copyOf(blockLocals, blockLocals.length * 2);
        }

        blockLocals[depth] = context.blockLocalIds(node);
        blockDepth = depth;
    }

    void leaveBlock(SemanticBlockNode node, int depth, boolean terminated) {
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
                int offset = snapshotSlot * varWords;
                mustDeadSnapshots[offset + (variableId >>> 6)]
                        |= 1L << (variableId & 63);
                mayDeadSnapshots[offset + (variableId >>> 6)]
                        |= 1L << (variableId & 63);
            }
        }
    }

    int snapshot() {
        ensureCapacity(snapshotCount + 1);

        int slot = snapshotCount++;

        System.arraycopy(
                frameBits,
                0,
                frameSnapshots,
                slot * pointerCount * varWords,
                pointerCount * varWords
        );
        System.arraycopy(
                globalBits,
                0,
                globalSnapshots,
                slot * pointerCount * varWords,
                pointerCount * varWords
        );
        System.arraycopy(
                originFlags,
                0,
                flagSnapshots,
                slot * pointerCount,
                pointerCount
        );
        System.arraycopy(
                mustDead,
                0,
                mustDeadSnapshots,
                slot * varWords,
                varWords
        );
        System.arraycopy(
                mayDead,
                0,
                mayDeadSnapshots,
                slot * varWords,
                varWords
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
                captureMust,
                0,
                captureMustSnapshots,
                slot * siteWords,
                siteWords
        );
        System.arraycopy(
                captureMay,
                0,
                captureMaySnapshots,
                slot * siteWords,
                siteWords
        );
        System.arraycopy(
                captureSource,
                0,
                captureSourceSnapshots,
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
                frameSnapshots,
                slot * pointerCount * varWords,
                frameBits,
                0,
                pointerCount * varWords
        );
        System.arraycopy(
                globalSnapshots,
                slot * pointerCount * varWords,
                globalBits,
                0,
                pointerCount * varWords
        );
        System.arraycopy(
                flagSnapshots,
                slot * pointerCount,
                originFlags,
                0,
                pointerCount
        );
        System.arraycopy(
                mustDeadSnapshots,
                slot * varWords,
                mustDead,
                0,
                varWords
        );
        System.arraycopy(
                mayDeadSnapshots,
                slot * varWords,
                mayDead,
                0,
                varWords
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
                captureMustSnapshots,
                slot * siteWords,
                captureMust,
                0,
                siteWords
        );
        System.arraycopy(
                captureMaySnapshots,
                slot * siteWords,
                captureMay,
                0,
                siteWords
        );
        System.arraycopy(
                captureSourceSnapshots,
                slot * siteCount * templateWords,
                captureSource,
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

        frameSnapshots = grow(
                frameSnapshots,
                newCapacity * pointerCount * varWords
        );
        globalSnapshots = grow(
                globalSnapshots,
                newCapacity * pointerCount * varWords
        );
        flagSnapshots = grow(flagSnapshots, newCapacity * pointerCount);
        mustDeadSnapshots = grow(
                mustDeadSnapshots,
                newCapacity * varWords
        );
        mayDeadSnapshots = grow(
                mayDeadSnapshots,
                newCapacity * varWords
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
        captureMustSnapshots = grow(
                captureMustSnapshots,
                newCapacity * siteWords
        );
        captureMaySnapshots = grow(
                captureMaySnapshots,
                newCapacity * siteWords
        );
        captureSourceSnapshots = grow(
                captureSourceSnapshots,
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
        for (int i = 0; i < pointerCount * varWords; i++) {
            frameBits[i] |= frameSnapshots[
                    slot * pointerCount * varWords + i];
            globalBits[i] |= globalSnapshots[
                    slot * pointerCount * varWords + i];
        }

        for (int i = 0; i < pointerCount; i++) {
            int current = originFlags[i] & 0xFF;
            int incoming = flagSnapshots[slot * pointerCount + i] & 0xFF;
            int unknown = (current | incoming) & FLAG_UNKNOWN;
            int nonNull = (current & incoming) & FLAG_NONNULL;
            originFlags[i] = (byte) (unknown | nonNull);
        }

        for (int i = 0; i < varWords; i++) {
            mustDead[i] &= mustDeadSnapshots[slot * varWords + i];
            mayDead[i] |= mayDeadSnapshots[slot * varWords + i];
        }

        for (int i = 0; i < pointerCount * templateWords; i++) {
            symValue[i] |= symValueSnapshots[
                    slot * pointerCount * templateWords + i];
            symPointee[i] |= symPointeeSnapshots[
                    slot * pointerCount * templateWords + i];
            symCell[i] |= symCellSnapshots[
                    slot * pointerCount * templateWords + i];
        }

        for (int i = 0; i < siteWords; i++) {
            captureMust[i] &= captureMustSnapshots[slot * siteWords + i];
            captureMay[i] |= captureMaySnapshots[slot * siteWords + i];
        }

        for (int i = 0; i < siteCount * templateWords; i++) {
            captureSource[i] |= captureSourceSnapshots[
                    slot * siteCount * templateWords + i];
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
