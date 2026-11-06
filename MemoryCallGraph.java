import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

final class MemoryCallGraph {
    final Symbol[] functions;
    final SemanticFunctionDeclNode[] decls;
    final int[][] parameterVariableIds;
    final boolean[] hasBody;
    final int[] edgeStart;
    final int[] edgeCount;
    final int[] edgeCallSiteIds;
    final SemanticFunctionCallNode[] callSiteNodes;
    final int[] callSiteCaller;
    final int[] callSiteCallee;
    final int[] callSiteLocalIndex;
    final int[] sccId;

    MemoryCallGraph(
            Symbol[] functions,
            SemanticFunctionDeclNode[] decls,
            int[][] parameterVariableIds,
            boolean[] hasBody,
            int[] edgeStart,
            int[] edgeCount,
            int[] edgeCallSiteIds,
            SemanticFunctionCallNode[] callSiteNodes,
            int[] callSiteCaller,
            int[] callSiteCallee,
            int[] callSiteLocalIndex
    ) {
        this.functions = functions;
        this.decls = decls;
        this.parameterVariableIds = parameterVariableIds;
        this.hasBody = hasBody;
        this.edgeStart = edgeStart;
        this.edgeCount = edgeCount;
        this.edgeCallSiteIds = edgeCallSiteIds;
        this.callSiteNodes = callSiteNodes;
        this.callSiteCaller = callSiteCaller;
        this.callSiteCallee = callSiteCallee;
        this.callSiteLocalIndex = callSiteLocalIndex;
        this.sccId = computeScc(functions.length, edgeStart, edgeCount, edgeCallSiteIds, callSiteCallee);
    }

    int functionCount() {
        return functions.length;
    }

    int callSiteCount() {
        return callSiteNodes.length;
    }

    boolean sameScc(int left, int right) {
        return left == right || (left >= 0 && right >= 0 && sccId[left] == sccId[right]);
    }

    private static int[] computeScc(
            int functionCount,
            int[] edgeStart,
            int[] edgeCount,
            int[] edgeCallSiteIds,
            int[] callSiteCallee
    ) {
        int[] index = new int[functionCount];
        int[] low = new int[functionCount];
        int[] scc = new int[functionCount];
        boolean[] onStack = new boolean[functionCount];
        int[] stack = new int[functionCount];
        int[] stackTop = {0};
        int[] nextIndex = {0};
        int[] nextScc = {0};

        Arrays.fill(index, -1);
        Arrays.fill(scc, -1);

        for (int i = 0; i < functionCount; i++) {
            if (index[i] < 0) {
                strongConnect(
                        i,
                        edgeStart,
                        edgeCount,
                        edgeCallSiteIds,
                        callSiteCallee,
                        index,
                        low,
                        scc,
                        onStack,
                        stack,
                        stackTop,
                        nextIndex,
                        nextScc
                );
            }
        }

        return scc;
    }

    private static void strongConnect(
            int functionId,
            int[] edgeStart,
            int[] edgeCount,
            int[] edgeCallSiteIds,
            int[] callSiteCallee,
            int[] index,
            int[] low,
            int[] scc,
            boolean[] onStack,
            int[] stack,
            int[] stackTop,
            int[] nextIndex,
            int[] nextScc
    ) {
        index[functionId] = nextIndex[0];
        low[functionId] = nextIndex[0];
        nextIndex[0]++;

        stack[stackTop[0]++] = functionId;
        onStack[functionId] = true;

        int start = edgeStart[functionId];
        int count = edgeCount[functionId];

        for (int i = 0; i < count; i++) {
            int callSite = edgeCallSiteIds[start + i];
            int callee = callSiteCallee[callSite];

            if (index[callee] < 0) {
                strongConnect(
                        callee,
                        edgeStart,
                        edgeCount,
                        edgeCallSiteIds,
                        callSiteCallee,
                        index,
                        low,
                        scc,
                        onStack,
                        stack,
                        stackTop,
                        nextIndex,
                        nextScc
                );

                low[functionId] = Math.min(low[functionId], low[callee]);
            } else if (onStack[callee]) {
                low[functionId] = Math.min(low[functionId], index[callee]);
            }
        }

        if (low[functionId] == index[functionId]) {
            int member;

            do {
                member = stack[--stackTop[0]];
                onStack[member] = false;
                scc[member] = nextScc[0];
            } while (member != functionId);

            nextScc[0]++;
        }
    }

    static final class Builder {
        private final IdentityHashMap<Symbol, Integer> functionIds =
                new IdentityHashMap<>();
        private final List<Symbol> functions = new ArrayList<>();
        private final List<SemanticFunctionDeclNode> decls = new ArrayList<>();
        private final List<int[]> parameterIds = new ArrayList<>();
        private final List<Boolean> hasBody = new ArrayList<>();
        private final List<SemanticFunctionCallNode> callSites = new ArrayList<>();
        private final List<Integer> callSiteCallers = new ArrayList<>();
        private final List<Integer> callSiteCallees = new ArrayList<>();
        private final List<List<Integer>> callerEdges = new ArrayList<>();

        int functionId(Symbol symbol) {
            if (symbol == null) {
                return -1;
            }

            Integer existing = functionIds.get(symbol);

            if (existing != null) {
                return existing;
            }

            return addFunction(symbol, null);
        }

        int addFunction(Symbol symbol, SemanticFunctionDeclNode decl) {
            Integer existing = functionIds.get(symbol);

            if (existing != null) {
                if (decl != null && decls.get(existing) == null) {
                    decls.set(existing, decl);
                    hasBody.set(existing, true);
                }

                return existing;
            }

            int id = functions.size();
            functionIds.put(symbol, id);
            functions.add(symbol);
            decls.add(decl);
            hasBody.add(decl != null);

            List<Symbol> parameters = symbol.parameters;
            int[] ids = new int[parameters.size()];

            for (int i = 0; i < parameters.size(); i++) {
                ids[i] = -1;
            }

            parameterIds.add(ids);
            callerEdges.add(new ArrayList<>());
            return id;
        }

        void setParameterVariableId(int functionId, int parameterIndex, int variableId) {
            parameterIds.get(functionId)[parameterIndex] = variableId;
        }

        int addCallSite(
                SemanticFunctionCallNode node,
                int callerFunctionId,
                int calleeFunctionId
        ) {
            int callSiteId = callSites.size();
            callSites.add(node);
            callSiteCallers.add(callerFunctionId);
            callSiteCallees.add(calleeFunctionId);
            callerEdges.get(callerFunctionId).add(callSiteId);
            return callSiteId;
        }

        int functionCount() {
            return functions.size();
        }

        int callSiteCount() {
            return callSites.size();
        }

        MemoryCallGraph build() {
            int functionCount = functions.size();
            int callSiteCount = callSites.size();

            int[] edgeStart = new int[functionCount];
            int[] edgeCount = new int[functionCount];
            int totalEdges = 0;

            for (int i = 0; i < functionCount; i++) {
                edgeStart[i] = totalEdges;
                edgeCount[i] = callerEdges.get(i).size();
                totalEdges += edgeCount[i];
            }

            int[] edgeCallSiteIds = new int[totalEdges];
            int[] callSiteLocalIndex = new int[callSiteCount];

            for (int i = 0; i < functionCount; i++) {
                List<Integer> edges = callerEdges.get(i);
                int offset = edgeStart[i];

                for (int j = 0; j < edges.size(); j++) {
                    int callSiteId = edges.get(j);
                    edgeCallSiteIds[offset + j] = callSiteId;
                    callSiteLocalIndex[callSiteId] = j;
                }
            }

            int[] callSiteCaller = new int[callSiteCount];
            int[] callSiteCallee = new int[callSiteCount];

            for (int i = 0; i < callSiteCount; i++) {
                callSiteCaller[i] = callSiteCallers.get(i);
                callSiteCallee[i] = callSiteCallees.get(i);
            }

            return new MemoryCallGraph(
                    functions.toArray(new Symbol[0]),
                    decls.toArray(new SemanticFunctionDeclNode[0]),
                    parameterIds.toArray(new int[0][]),
                    toBooleanArray(hasBody),
                    edgeStart,
                    edgeCount,
                    edgeCallSiteIds,
                    callSites.toArray(new SemanticFunctionCallNode[0]),
                    callSiteCaller,
                    callSiteCallee,
                    callSiteLocalIndex
            );
        }

        private static boolean[] toBooleanArray(List<Boolean> values) {
            boolean[] result = new boolean[values.size()];

            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i);
            }

            return result;
        }
    }
}
