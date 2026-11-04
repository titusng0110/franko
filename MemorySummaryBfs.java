import java.util.Arrays;

final class MemorySummaryBfs {
    static final int MAX_BFS_VISITED = 2048;

    interface NodeProcessor {
        void processNode(int nodeIndex);
    }

    private final MemoryAnalysisContext context;

    private int[] queue = new int[64];
    private int queueHead;
    private int queueTail;

    private int[] bindingPool = new int[1024];
    private int bindingCount;

    private int[] nodeFunctions = new int[64];
    private int[] nodeBindingOffsets = new int[64];
    private int[] nodeBindingLengths = new int[64];
    private int nodeCount;

    private int[] hashKeys = new int[128];
    private int[] hashNodes = new int[128];
    private int hashMask = 127;
    private int hashSize;

    private int budget;
    private boolean containsTop;

    private int[] callSiteNodeIndex = new int[0];
    private boolean[] callSiteTop = new boolean[0];

    private int rootNodeIndex;

    MemorySummaryBfs(MemoryAnalysisContext context) {
        this.context = context;
    }

    MemoryBfsResult resolve(
            int rootCallSiteId,
            int calleeFunctionId,
            int[] rootBinding,
            NodeProcessor processor
    ) {
        queueHead = 0;
        queueTail = 0;
        bindingCount = 0;
        nodeCount = 0;
        Arrays.fill(hashKeys, 0);
        Arrays.fill(hashNodes, 0);
        hashSize = 0;
        budget = MAX_BFS_VISITED;
        containsTop = false;

        if (callSiteNodeIndex.length != context.callSiteCount) {
            callSiteNodeIndex = new int[context.callSiteCount];
            callSiteTop = new boolean[context.callSiteCount];
        }

        Arrays.fill(callSiteNodeIndex, -1);
        Arrays.fill(callSiteTop, false);

        rootNodeIndex = -1;

        int rootNode = enqueue(
                -1,
                calleeFunctionId,
                rootBinding,
                rootCallSiteId
        );

        rootNodeIndex = rootNode;

        while (queueHead < queueTail) {
            if (budget <= 0) {
                containsTop = true;
                break;
            }

            int node = queue[queueHead++];
            budget--;
            processor.processNode(node);
        }

        if (queueHead < queueTail) {
            containsTop = true;
        }

        return new MemoryBfsResult(this);
    }

    int enqueue(
            int callerFunctionId,
            int calleeFunctionId,
            int[] binding,
            int callSiteId
    ) {
        if (calleeFunctionId < 0 || !context.hasBody(calleeFunctionId)) {
            markTop(callSiteId);
            return -1;
        }

        if (callerFunctionId >= 0
                && context.callGraph.sameScc(callerFunctionId, calleeFunctionId)) {
            markTop(callSiteId);
            return -1;
        }

        int hash = bindingHash(calleeFunctionId, binding);
        int slot = mix(hash) & hashMask;

        while (hashNodes[slot] != 0) {
            int node = hashNodes[slot] - 1;

            if (hashKeys[slot] == hash
                    && nodeFunctions[node] == calleeFunctionId
                    && bindingEquals(node, binding)) {
                if (callSiteId >= 0) {
                    callSiteNodeIndex[callSiteId] = node;
                }

                return node;
            }

            slot = (slot + 1) & hashMask;
        }

        int node = createNode(calleeFunctionId, binding);
        hashKeys[slot] = hash;
        hashNodes[slot] = node + 1;
        hashSize++;

        if (hashSize * 2 >= hashNodes.length) {
            growHash();
        }

        if (callSiteId >= 0) {
            callSiteNodeIndex[callSiteId] = node;
        }

        if (queueTail == queue.length) {
            int[] grown = new int[queue.length * 2];
            System.arraycopy(queue, 0, grown, 0, queue.length);
            queue = grown;
        }

        queue[queueTail++] = node;
        return node;
    }

    private void markTop(int callSiteId) {
        containsTop = true;

        if (callSiteId >= 0 && callSiteId < callSiteTop.length) {
            callSiteTop[callSiteId] = true;
            callSiteNodeIndex[callSiteId] = -1;
        }
    }

    private int createNode(int functionId, int[] binding) {
        if (nodeCount == nodeFunctions.length) {
            int capacity = nodeFunctions.length * 2;
            nodeFunctions = Arrays.copyOf(nodeFunctions, capacity);
            nodeBindingOffsets = Arrays.copyOf(nodeBindingOffsets, capacity);
            nodeBindingLengths = Arrays.copyOf(nodeBindingLengths, capacity);
        }

        int offset = bindingCount;

        if (bindingCount + binding.length > bindingPool.length) {
            int capacity = Math.max(
                    bindingPool.length * 2,
                    bindingCount + binding.length
            );
            bindingPool = Arrays.copyOf(bindingPool, capacity);
        }

        System.arraycopy(binding, 0, bindingPool, offset, binding.length);
        bindingCount += binding.length;

        int node = nodeCount++;
        nodeFunctions[node] = functionId;
        nodeBindingOffsets[node] = offset;
        nodeBindingLengths[node] = binding.length;
        return node;
    }

    private boolean bindingEquals(int node, int[] binding) {
        int length = nodeBindingLengths[node];

        if (length != binding.length) {
            return false;
        }

        int offset = nodeBindingOffsets[node];

        for (int i = 0; i < length; i++) {
            if (bindingPool[offset + i] != binding[i]) {
                return false;
            }
        }

        return true;
    }

    private static int bindingHash(int functionId, int[] binding) {
        int hash = functionId * 0x9E3779B1;

        for (int value : binding) {
            hash = hash * 31 + value;
        }

        return hash;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    private void growHash() {
        int[] oldKeys = hashKeys;
        int[] oldNodes = hashNodes;
        hashKeys = new int[oldKeys.length * 2];
        hashNodes = new int[oldNodes.length * 2];
        hashMask = hashKeys.length - 1;
        hashSize = 0;

        for (int i = 0; i < oldNodes.length; i++) {
            if (oldNodes[i] == 0) {
                continue;
            }

            int slot = mix(oldKeys[i]) & hashMask;

            while (hashNodes[slot] != 0) {
                slot = (slot + 1) & hashMask;
            }

            hashKeys[slot] = oldKeys[i];
            hashNodes[slot] = oldNodes[i];
            hashSize++;
        }
    }

    int nodeCount() {
        return nodeCount;
    }

    int functionId(int node) {
        return nodeFunctions[node];
    }

    int bindingLength(int node) {
        return nodeBindingLengths[node];
    }

    int bindingAt(int node, int index) {
        return bindingPool[nodeBindingOffsets[node] + index];
    }

    int bindingOffset(int node) {
        return nodeBindingOffsets[node];
    }

    int[] bindingCopy(int node) {
        int offset = nodeBindingOffsets[node];
        int length = nodeBindingLengths[node];
        int[] copy = new int[length];
        System.arraycopy(bindingPool, offset, copy, 0, length);
        return copy;
    }

    int callSiteNodeIndex(int callSiteId) {
        if (callSiteId < 0 || callSiteId >= callSiteNodeIndex.length) {
            return -1;
        }

        return callSiteNodeIndex[callSiteId];
    }

    boolean isCallSiteTop(int callSiteId) {
        if (callSiteId < 0 || callSiteId >= callSiteTop.length) {
            return true;
        }

        return callSiteTop[callSiteId];
    }

    int rootNodeIndex() {
        return rootNodeIndex;
    }

    boolean containsTop() {
        return containsTop;
    }
}

final class MemoryBfsResult {
    private final MemorySummaryBfs engine;

    MemoryBfsResult(MemorySummaryBfs engine) {
        this.engine = engine;
    }

    MemorySummaryBfs engine() {
        return engine;
    }

    int nodeCount() {
        return engine.nodeCount();
    }

    int functionId(int node) {
        return engine.functionId(node);
    }

    int bindingLength(int node) {
        return engine.bindingLength(node);
    }

    int bindingAt(int node, int index) {
        return engine.bindingAt(node, index);
    }

    int[] bindingCopy(int node) {
        return engine.bindingCopy(node);
    }

    int callSiteNodeIndex(int callSiteId) {
        return engine.callSiteNodeIndex(callSiteId);
    }

    boolean isCallSiteTop(int callSiteId) {
        return engine.isCallSiteTop(callSiteId);
    }

    int rootNodeIndex() {
        return engine.rootNodeIndex();
    }

    boolean containsTop() {
        return engine.containsTop();
    }
}
