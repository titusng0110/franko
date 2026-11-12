import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
    final int realArrayCount;
    final int shadowParamCount;
    final Symbol[] shadowParameters;
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
    byte[] paramAssume;
    boolean[] paramInited;
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
    private byte[] paramAssumeSnapshots = new byte[0];
    private boolean[] paramInitedSnapshots = new boolean[0];
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
    final long snapshotRowBytes;
    final int snapshotLimitSlots;

    private int[] activeArrayIndices = new int[0];
    private int[] activeOwnerIndices = new int[0];
    private int[] activePointerIndices = new int[0];
    private int[] activeArrayWordIds = new int[0];
    private int[] activeArrayWordIndexByWord = new int[0];

    private int activeArrayCount;
    private int activeOwnerCount;
    private int activePointerCount;
    private int activeArrayWordCount;

    private int maxActiveArrayCount;
    private int maxActiveOwnerCount;
    private int maxActivePointerCount;
    private int maxActiveArrayWordCount;
    private int maxActiveArrayFunctionId = -1;
    private int maxActiveOwnerFunctionId = -1;
    private int maxActivePointerFunctionId = -1;
    private int maxActiveArrayWordFunctionId = -1;
    private int currentFunctionId = -1;

    private int[][] blockLocals = new int[32][];

    DynamicArrayAnalysisState(MemoryAnalysisContext context) {
        this.context = context;
        this.templates = context.templates;

        int arrayTotal = 0;
        int ownerTotal = 0;
        int pointerTotal = 0;
        int shadowTotal = 0;

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

                if (symbol.kind == SymbolKind.PARAMETER) {
                    shadowTotal++;
                }
            }

            if (symbol.type.kind == SastTypeKind.PTR) {
                pointerTotal++;
            }
        }

        arrayCount = arrayTotal + shadowTotal;
        realArrayCount = arrayTotal;
        shadowParamCount = shadowTotal;
        ownerCount = ownerTotal;
        pointerCount = pointerTotal;
        arrayVariableIds = new int[arrayCount];
        shadowParameters = new Symbol[shadowTotal];
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

        int shadowIndex = 0;

        for (int i = 0; i < context.variables.length; i++) {
            Symbol symbol = context.variables[i];

            if (symbol.kind != SymbolKind.PARAMETER
                    || symbol.type.kind != SastTypeKind.PTR
                    || symbol.type.permission != PtrPermission.OWNER
                    || symbol.type.referencedType.kind != SastTypeKind.DYNAMIC_ARRAY) {
                continue;
            }

            arrayVariableIds[realArrayCount + shadowIndex] = -1;
            shadowParameters[shadowIndex] = symbol;
            shadowIndex++;
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
        paramAssume = new byte[paramCount];
        paramInited = new boolean[paramCount];
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

        computeMaxActiveLayouts();
        this.snapshotRowBytes =
                11L * maxActiveArrayCount
                + 8L
                        * maxActiveOwnerCount
                        * maxActiveArrayWordCount
                + maxActiveOwnerCount
                + 8L * maxActivePointerCount
                + 3L * paramCount
                + 80L * paramWords
                + 8L * maxActivePointerCount * templateWords;
        this.snapshotLimitSlots = snapshotLimitSlots(snapshotRowBytes);
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
        return count(activeArrayCount, "active array")
                + ", "
                + count(activeOwnerCount, "owner pointer")
                + ", and "
                + count(activePointerCount, "tracked pointer");
    }

    private static String count(int value, String noun) {
        return String.format(Locale.ROOT, "%,d %s%s",
                value, noun, value == 1 ? "" : "s");
    }

    private String snapshotRowOwners() {
        StringBuilder result = new StringBuilder();

        appendOwner(
                result,
                "active array",
                maxActiveArrayCount,
                maxActiveArrayFunctionId
        );
        appendOwner(
                result,
                "owner pointer",
                maxActiveOwnerCount,
                maxActiveOwnerFunctionId
        );
        appendOwner(
                result,
                "tracked pointer",
                maxActivePointerCount,
                maxActivePointerFunctionId
        );
        appendOwner(
                result,
                "array word",
                maxActiveArrayWordCount,
                maxActiveArrayWordFunctionId
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
        int[] activeVariableIds = context.functionVariableIds[functionId];
        boolean[] activeVariables = new boolean[context.variables.length];

        for (int variableId : activeVariableIds) {
            activeVariables[variableId] = true;
        }

        List<Integer> arrays = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();
        List<Integer> pointers = new ArrayList<>();

        for (int i = 0; i < realArrayCount; i++) {
            int variableId = arrayVariableIds[i];

            if (variableId >= 0 && activeVariables[variableId]) {
                arrays.add(i);
            }
        }

        for (int i = 0; i < shadowParamCount; i++) {
            int variableId = context.variableId(shadowParameters[i]);

            if (variableId >= 0 && activeVariables[variableId]) {
                arrays.add(realArrayCount + i);
            }
        }

        for (int i = 0; i < ownerCount; i++) {
            int variableId = ownerVariableIds[i];

            if (activeVariables[variableId]) {
                owners.add(i);
            }
        }

        for (int i = 0; i < pointerCount; i++) {
            int variableId = pointerVariableIds[i];

            if (activeVariables[variableId]) {
                pointers.add(i);
            }
        }

        activeArrayIndices = toIntArray(arrays);
        activeOwnerIndices = toIntArray(owners);
        activePointerIndices = toIntArray(pointers);
        activeArrayCount = activeArrayIndices.length;
        activeOwnerCount = activeOwnerIndices.length;
        activePointerCount = activePointerIndices.length;

        boolean[] arrayWordActive = new boolean[arrayWords];

        for (int index : activeArrayIndices) {
            arrayWordActive[index >>> 6] = true;
        }

        List<Integer> arrayWordList = new ArrayList<>();

        for (int word = 0; word < arrayWords; word++) {
            if (arrayWordActive[word]) {
                arrayWordList.add(word);
            }
        }

        activeArrayWordIds = toIntArray(arrayWordList);
        activeArrayWordCount = activeArrayWordIds.length;

        if (activeArrayWordIndexByWord.length != arrayWords) {
            activeArrayWordIndexByWord = new int[arrayWords];
        }

        Arrays.fill(activeArrayWordIndexByWord, -1);

        for (int i = 0; i < activeArrayWordIds.length; i++) {
            activeArrayWordIndexByWord[activeArrayWordIds[i]] = i;
        }
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];

        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }

        return result;
    }

    int activeArrayCount() {
        return activeArrayCount;
    }

    int activeArrayIndex(int index) {
        return activeArrayIndices[index];
    }

    private void computeMaxActiveLayouts() {
        for (int functionId = 0;
                functionId < context.callGraph.functionCount();
                functionId++) {
            setFunction(functionId);

            if (activeArrayCount > maxActiveArrayCount) {
                maxActiveArrayCount = activeArrayCount;
                maxActiveArrayFunctionId = functionId;
            }

            if (activeOwnerCount > maxActiveOwnerCount) {
                maxActiveOwnerCount = activeOwnerCount;
                maxActiveOwnerFunctionId = functionId;
            }

            if (activePointerCount > maxActivePointerCount) {
                maxActivePointerCount = activePointerCount;
                maxActivePointerFunctionId = functionId;
            }

            if (activeArrayWordCount > maxActiveArrayWordCount) {
                maxActiveArrayWordCount = activeArrayWordCount;
                maxActiveArrayWordFunctionId = functionId;
            }
        }

        if (context.callGraph.functionCount() > 0) {
            setFunction(0);
        }
    }

    void reset() {
        for (int index : activeArrayIndices) {
            lifecycle[index] = UNINITED;
            freed[index] = FREED_NEVER;
            escaped[index] = false;
            epochMin[index] = 0;
            epochMax[index] = 0;
        }

        for (int owner : activeOwnerIndices) {
            for (int word = 0; word < arrayWords; word++) {
                ownerArrayBits[owner * arrayWords + word] = 0L;
            }

            ownerUnknown[owner] = false;
        }

        for (int pointer : activePointerIndices) {
            aliasRoot[pointer] = ALIAS_NONE;
            aliasEpoch[pointer] = 0;
            Arrays.fill(
                    symAlias,
                    pointer * templateWords,
                    pointer * templateWords + templateWords,
                    0L
            );
        }

        Arrays.fill(paramPost, POST_UNCHANGED);
        Arrays.fill(paramAssume, POST_UNCHANGED);
        Arrays.fill(paramInited, false);
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
        snapshotCount = 0;
    }

    int arrayIndexOf(Symbol symbol) {
        int variableId = context.variableId(symbol);

        if (variableId < 0) {
            return -1;
        }

        return arrayIndexByVariable[variableId];
    }

    int shadowArrayOf(Symbol parameter) {
        for (int i = 0; i < shadowParamCount; i++) {
            if (shadowParameters[i] == parameter) {
                return realArrayCount + i;
            }
        }

        return -1;
    }

    boolean isShadowArray(int arrayIndex) {
        return arrayIndex >= realArrayCount;
    }

    Symbol shadowParameterOf(int arrayIndex) {
        if (!isShadowArray(arrayIndex)) {
            return null;
        }

        return shadowParameters[arrayIndex - realArrayCount];
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

        int arrayBase = slot * activeArrayCount;
        int ownerBase = slot
                * activeOwnerCount
                * activeArrayWordCount;
        int ownerUnknownBase = slot * activeOwnerCount;
        int pointerBase = slot * activePointerCount;
        int parameterBase = slot * paramCount;
        int parameterWordBase = slot * paramWords;
        int symbolBase = slot * activePointerCount * templateWords;

        for (int i = 0; i < activeArrayCount; i++) {
            int array = activeArrayIndices[i];
            lifecycleSnapshots[arrayBase + i] = lifecycle[array];
            freedSnapshots[arrayBase + i] = freed[array];
            escapedSnapshots[arrayBase + i] = escaped[array];
            epochMinSnapshots[arrayBase + i] = epochMin[array];
            epochMaxSnapshots[arrayBase + i] = epochMax[array];
        }

        for (int i = 0; i < activeOwnerCount; i++) {
            int owner = activeOwnerIndices[i];
            int ownerOffset = owner * arrayWords;
            int snapshotOffset = ownerBase + i * activeArrayWordCount;

            for (int j = 0; j < activeArrayWordCount; j++) {
                int word = activeArrayWordIds[j];
                ownerBitsSnapshots[snapshotOffset + j] =
                        ownerArrayBits[ownerOffset + word];
            }

            ownerUnknownSnapshots[ownerUnknownBase + i] =
                    ownerUnknown[owner];
        }

        for (int i = 0; i < activePointerCount; i++) {
            int pointer = activePointerIndices[i];
            aliasRootSnapshots[pointerBase + i] = aliasRoot[pointer];
            aliasEpochSnapshots[pointerBase + i] = aliasEpoch[pointer];

            System.arraycopy(
                    symAlias,
                    pointer * templateWords,
                    symAliasSnapshots,
                    symbolBase + i * templateWords,
                    templateWords
            );
        }

        System.arraycopy(
                paramPost,
                0,
                paramPostSnapshots,
                parameterBase,
                paramCount
        );
        System.arraycopy(
                paramAssume,
                0,
                paramAssumeSnapshots,
                parameterBase,
                paramCount
        );
        System.arraycopy(
                paramInited,
                0,
                paramInitedSnapshots,
                parameterBase,
                paramCount
        );
        System.arraycopy(
                requireMust,
                0,
                requireMustSnapshots,
                parameterWordBase,
                paramWords
        );
        System.arraycopy(
                requireMay,
                0,
                requireMaySnapshots,
                parameterWordBase,
                paramWords
        );
        System.arraycopy(
                invalidateMust,
                0,
                invalidateMustSnapshots,
                parameterWordBase,
                paramWords
        );
        System.arraycopy(
                invalidateMay,
                0,
                invalidateMaySnapshots,
                parameterWordBase,
                paramWords
        );
        System.arraycopy(
                freeMust,
                0,
                freeMustSnapshots,
                parameterWordBase,
                paramWords
        );
        System.arraycopy(
                freeMay,
                0,
                freeMaySnapshots,
                parameterWordBase,
                paramWords
        );
        System.arraycopy(
                useMust,
                0,
                useMustSnapshots,
                parameterWordBase,
                paramWords
        );
        System.arraycopy(
                useMay,
                0,
                useMaySnapshots,
                parameterWordBase,
                paramWords
        );
        System.arraycopy(
                escapeMust,
                0,
                escapeMustSnapshots,
                parameterWordBase,
                paramWords
        );
        System.arraycopy(
                escapeMay,
                0,
                escapeMaySnapshots,
                parameterWordBase,
                paramWords
        );

        return slot;
    }

    void restore(int slot) {
        if (slot < 0) {
            return;
        }

        int arrayBase = slot * activeArrayCount;
        int ownerBase = slot
                * activeOwnerCount
                * activeArrayWordCount;
        int ownerUnknownBase = slot * activeOwnerCount;
        int pointerBase = slot * activePointerCount;
        int parameterBase = slot * paramCount;
        int parameterWordBase = slot * paramWords;
        int symbolBase = slot * activePointerCount * templateWords;

        for (int i = 0; i < activeArrayCount; i++) {
            int array = activeArrayIndices[i];
            lifecycle[array] = lifecycleSnapshots[arrayBase + i];
            freed[array] = freedSnapshots[arrayBase + i];
            escaped[array] = escapedSnapshots[arrayBase + i];
            epochMin[array] = epochMinSnapshots[arrayBase + i];
            epochMax[array] = epochMaxSnapshots[arrayBase + i];
        }

        for (int i = 0; i < activeOwnerCount; i++) {
            int owner = activeOwnerIndices[i];
            int ownerOffset = owner * arrayWords;
            int snapshotOffset = ownerBase + i * activeArrayWordCount;

            for (int j = 0; j < activeArrayWordCount; j++) {
                int word = activeArrayWordIds[j];
                ownerArrayBits[ownerOffset + word] =
                        ownerBitsSnapshots[snapshotOffset + j];
            }

            ownerUnknown[owner] = ownerUnknownSnapshots[
                    ownerUnknownBase + i
            ];
        }

        for (int i = 0; i < activePointerCount; i++) {
            int pointer = activePointerIndices[i];
            aliasRoot[pointer] = aliasRootSnapshots[pointerBase + i];
            aliasEpoch[pointer] = aliasEpochSnapshots[pointerBase + i];

            System.arraycopy(
                    symAliasSnapshots,
                    symbolBase + i * templateWords,
                    symAlias,
                    pointer * templateWords,
                    templateWords
            );
        }

        System.arraycopy(
                paramPostSnapshots,
                parameterBase,
                paramPost,
                0,
                paramCount
        );
        System.arraycopy(
                paramAssumeSnapshots,
                parameterBase,
                paramAssume,
                0,
                paramCount
        );
        System.arraycopy(
                paramInitedSnapshots,
                parameterBase,
                paramInited,
                0,
                paramCount
        );
        System.arraycopy(
                requireMustSnapshots,
                parameterWordBase,
                requireMust,
                0,
                paramWords
        );
        System.arraycopy(
                requireMaySnapshots,
                parameterWordBase,
                requireMay,
                0,
                paramWords
        );
        System.arraycopy(
                invalidateMustSnapshots,
                parameterWordBase,
                invalidateMust,
                0,
                paramWords
        );
        System.arraycopy(
                invalidateMaySnapshots,
                parameterWordBase,
                invalidateMay,
                0,
                paramWords
        );
        System.arraycopy(
                freeMustSnapshots,
                parameterWordBase,
                freeMust,
                0,
                paramWords
        );
        System.arraycopy(
                freeMaySnapshots,
                parameterWordBase,
                freeMay,
                0,
                paramWords
        );
        System.arraycopy(
                useMustSnapshots,
                parameterWordBase,
                useMust,
                0,
                paramWords
        );
        System.arraycopy(
                useMaySnapshots,
                parameterWordBase,
                useMay,
                0,
                paramWords
        );
        System.arraycopy(
                escapeMustSnapshots,
                parameterWordBase,
                escapeMust,
                0,
                paramWords
        );
        System.arraycopy(
                escapeMaySnapshots,
                parameterWordBase,
                escapeMay,
                0,
                paramWords
        );
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
        lifecycleSnapshots = grow(
                lifecycleSnapshots,
                newCapacity * maxActiveArrayCount
        );
        freedSnapshots = grow(
                freedSnapshots,
                newCapacity * maxActiveArrayCount
        );
        escapedSnapshots = grow(
                escapedSnapshots,
                newCapacity * maxActiveArrayCount
        );
        epochMinSnapshots = grow(
                epochMinSnapshots,
                newCapacity * maxActiveArrayCount
        );
        epochMaxSnapshots = grow(
                epochMaxSnapshots,
                newCapacity * maxActiveArrayCount
        );
        ownerBitsSnapshots = grow(
                ownerBitsSnapshots,
                newCapacity
                        * maxActiveOwnerCount
                        * maxActiveArrayWordCount
        );
        ownerUnknownSnapshots = grow(
                ownerUnknownSnapshots,
                newCapacity * maxActiveOwnerCount
        );
        aliasRootSnapshots = grow(
                aliasRootSnapshots,
                newCapacity * maxActivePointerCount
        );
        aliasEpochSnapshots = grow(
                aliasEpochSnapshots,
                newCapacity * maxActivePointerCount
        );
        paramPostSnapshots = grow(
                paramPostSnapshots,
                newCapacity * paramCount
        );
        paramAssumeSnapshots = grow(
                paramAssumeSnapshots,
                newCapacity * paramCount
        );
        paramInitedSnapshots = grow(
                paramInitedSnapshots,
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
                newCapacity * maxActivePointerCount * templateWords
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
        int arrayBase = slot * activeArrayCount;
        int ownerBase = slot
                * activeOwnerCount
                * activeArrayWordCount;
        int ownerUnknownBase = slot * activeOwnerCount;
        int pointerBase = slot * activePointerCount;
        int parameterBase = slot * paramCount;
        int parameterWordBase = slot * paramWords;
        int symbolBase = slot * activePointerCount * templateWords;

        for (int i = 0; i < activeArrayCount; i++) {
            int array = activeArrayIndices[i];
            byte incomingLifecycle = lifecycleSnapshots[arrayBase + i];
            byte incomingFreed = freedSnapshots[arrayBase + i];

            if (lifecycle[array] != incomingLifecycle) {
                lifecycle[array] = MAYBE_INITED;
            }

            if (freed[array] != incomingFreed) {
                freed[array] = FREED_MAYBE;
            }

            escaped[array] |= escapedSnapshots[arrayBase + i];
            epochMin[array] = Math.min(
                    epochMin[array],
                    epochMinSnapshots[arrayBase + i]
            );
            epochMax[array] = Math.max(
                    epochMax[array],
                    epochMaxSnapshots[arrayBase + i]
            );
        }

        for (int i = 0; i < activeOwnerCount; i++) {
            int owner = activeOwnerIndices[i];
            int ownerOffset = owner * arrayWords;
            int snapshotOffset = ownerBase + i * activeArrayWordCount;

            for (int j = 0; j < activeArrayWordCount; j++) {
                int word = activeArrayWordIds[j];
                ownerArrayBits[ownerOffset + word] |=
                        ownerBitsSnapshots[snapshotOffset + j];
            }

            ownerUnknown[owner] |= ownerUnknownSnapshots[
                    ownerUnknownBase + i
            ];
        }

        for (int i = 0; i < activePointerCount; i++) {
            int pointer = activePointerIndices[i];
            int incomingRoot = aliasRootSnapshots[pointerBase + i];
            int incomingEpoch = aliasEpochSnapshots[pointerBase + i];

            if (aliasRoot[pointer] != incomingRoot
                    || aliasEpoch[pointer] != incomingEpoch) {
                aliasRoot[pointer] = ALIAS_UNKNOWN;
                aliasEpoch[pointer] = 0;
            }

            int stateOffset = pointer * templateWords;
            int snapshotOffset = symbolBase + i * templateWords;

            for (int word = 0; word < templateWords; word++) {
                symAlias[stateOffset + word] |=
                        symAliasSnapshots[snapshotOffset + word];
            }
        }

        for (int i = 0; i < paramCount; i++) {
            byte incoming = paramPostSnapshots[parameterBase + i];

            if (paramPost[i] != incoming) {
                if (paramPost[i] == POST_HAVOC || incoming == POST_HAVOC) {
                    paramPost[i] = POST_HAVOC;
                } else {
                    paramPost[i] = POST_MAYBE;
                }
            }

            incoming = paramAssumeSnapshots[parameterBase + i];

            if (paramAssume[i] != incoming) {
                if (paramAssume[i] == POST_HAVOC
                        || incoming == POST_HAVOC) {
                    paramAssume[i] = POST_HAVOC;
                } else {
                    paramAssume[i] = POST_MAYBE;
                }
            }

            paramInited[i] &= paramInitedSnapshots[parameterBase + i];
        }

        for (int i = 0; i < paramWords; i++) {
            requireMust[i] &= requireMustSnapshots[parameterWordBase + i];
            requireMay[i] |= requireMaySnapshots[parameterWordBase + i];
            invalidateMust[i] &=
                    invalidateMustSnapshots[parameterWordBase + i];
            invalidateMay[i] |=
                    invalidateMaySnapshots[parameterWordBase + i];
            freeMust[i] &= freeMustSnapshots[parameterWordBase + i];
            freeMay[i] |= freeMaySnapshots[parameterWordBase + i];
            useMust[i] &= useMustSnapshots[parameterWordBase + i];
            useMay[i] |= useMaySnapshots[parameterWordBase + i];
            escapeMust[i] &= escapeMustSnapshots[parameterWordBase + i];
            escapeMay[i] |= escapeMaySnapshots[parameterWordBase + i];
        }
    }
}
