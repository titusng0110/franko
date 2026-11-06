import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

final class MemoryAnalysisContext {
    static final int MAX_BRANCH_DEPTH = 12;

    final SastNode root;
    final MemoryCallGraph callGraph;
    final MemoryShapeIndex shapes;
    final MemoryTemplateRegistry templates;
    final MemoryTemplateSetPool templateSets;

    final Symbol[] variables;
    final IdentityHashMap<Symbol, Integer> variableIds;
    final int[] pointerVariableIds;
    final int pointerVariableCount;
    final int[] pointerIndexByVariable;

    final int[] globalVariableIds;
    final int globalCount;
    final int[] globalIndexByVariable;
    final IdentityHashMap<Symbol, Integer> functionIds;

    final SastNode[] callSiteNodes;
    final int callSiteCount;
    final int[] callSiteIdBySite;

    final int[] snapshotDemand;
    final int maxSnapshotDemand;

    final int siteCount;
    final IdentityHashMap<SastNode, Integer> siteIds;

    private final IdentityHashMap<SastNode, int[]> blockLocals;
    private final int[][] functionLocalIds;

    private MemoryAnalysisContext(
            SastNode root,
            MemoryCallGraph callGraph,
            MemoryShapeIndex shapes,
            MemoryTemplateRegistry templates,
            MemoryTemplateSetPool templateSets,
            Symbol[] variables,
            IdentityHashMap<Symbol, Integer> variableIds,
            int[] pointerVariableIds,
            int[] pointerIndexByVariable,
            int[] globalVariableIds,
            int[] globalIndexByVariable,
            IdentityHashMap<Symbol, Integer> functionIds,
            SastNode[] callSiteNodes,
            int[] callSiteIdBySite,
            int[] snapshotDemand,
            int maxSnapshotDemand,
            int siteCount,
            IdentityHashMap<SastNode, Integer> siteIds,
            IdentityHashMap<SastNode, int[]> blockLocals,
            int[][] functionLocalIds
    ) {
        this.root = root;
        this.callGraph = callGraph;
        this.shapes = shapes;
        this.templates = templates;
        this.templateSets = templateSets;
        this.variables = variables;
        this.variableIds = variableIds;
        this.pointerVariableIds = pointerVariableIds;
        this.pointerVariableCount = pointerVariableIds.length;
        this.pointerIndexByVariable = pointerIndexByVariable;
        this.globalVariableIds = globalVariableIds;
        this.globalCount = globalVariableIds.length;
        this.globalIndexByVariable = globalIndexByVariable;
        this.functionIds = functionIds;
        this.callSiteNodes = callSiteNodes;
        this.callSiteCount = callSiteNodes.length;
        this.callSiteIdBySite = callSiteIdBySite;
        this.snapshotDemand = snapshotDemand;
        this.maxSnapshotDemand = maxSnapshotDemand;
        this.siteCount = siteCount;
        this.siteIds = siteIds;
        this.blockLocals = blockLocals;
        this.functionLocalIds = functionLocalIds;
    }

    static MemoryAnalysisContext build(SastNode root) {
        MemoryCallGraph.Builder builder = new MemoryCallGraph.Builder();
        IdentityHashMap<Symbol, Integer> variableIds = new IdentityHashMap<>();
        List<Symbol> variables = new ArrayList<>();
        List<Integer> globals = new ArrayList<>();
        IdentityHashMap<SastNode, Integer> siteIds = new IdentityHashMap<>();
        IdentityHashMap<SastNode, int[]> blockLocals = new IdentityHashMap<>();
        int[] siteCount = {0};

        collectDeclarations(
                root,
                builder,
                variableIds,
                variables,
                globals
        );

        int functionCount = builder.functionCount();
        List<List<Integer>> functionLocals = new ArrayList<>();

        for (int i = 0; i < functionCount; i++) {
            functionLocals.add(new ArrayList<>());
        }

        collectBodies(
                root,
                builder,
                variableIds,
                variables,
                siteIds,
                siteCount,
                blockLocals,
                functionLocals
        );

        MemoryCallGraph callGraph = builder.build();
        int variableCount = variables.size();
        int maxParameters = 1;

        for (Symbol function : callGraph.functions) {
            maxParameters = Math.max(maxParameters, function.parameters.size());
        }

        int[] globalVariableIds = new int[globals.size()];
        int[] globalIndexByVariable = new int[variableCount];
        Arrays.fill(globalIndexByVariable, -1);

        for (int i = 0; i < globals.size(); i++) {
            globalVariableIds[i] = globals.get(i);
            globalIndexByVariable[globalVariableIds[i]] = i;
        }

        IdentityHashMap<Symbol, Integer> functionIds =
                new IdentityHashMap<>();

        for (int i = 0; i < callGraph.functions.length; i++) {
            functionIds.put(callGraph.functions[i], i);
        }

        int[] pointerIndexByVariable = new int[variableCount];
        Arrays.fill(pointerIndexByVariable, -1);
        List<Integer> pointerVariables = new ArrayList<>();

        for (int i = 0; i < variableCount; i++) {
            Symbol symbol = variables.get(i);

            if (symbol.type.kind == SastTypeKind.PTR) {
                pointerIndexByVariable[i] = pointerVariables.size();
                pointerVariables.add(i);
            }
        }

        int[] pointerVariableIds = new int[pointerVariables.size()];

        for (int i = 0; i < pointerVariables.size(); i++) {
            pointerVariableIds[i] = pointerVariables.get(i);
        }

        int[] snapshotDemand = new int[functionCount];
        int maxSnapshotDemand = 0;

        for (int i = 0; i < functionCount; i++) {
            SastNode decl = callGraph.decls[i];

            if (decl != null && decl.body != null) {
                snapshotDemand[i] = estimateSnapshotDemand(decl.body, 0);
            }

            maxSnapshotDemand = Math.max(maxSnapshotDemand, snapshotDemand[i]);
        }

        int[][] localIds = new int[functionCount][];

        for (int i = 0; i < functionCount; i++) {
            List<Integer> ids = functionLocals.get(i);
            localIds[i] = new int[ids.size()];

            for (int j = 0; j < ids.size(); j++) {
                localIds[i][j] = ids.get(j);
            }
        }

        MemoryTemplateRegistry templates = new MemoryTemplateRegistry(
                maxParameters,
                globalVariableIds.length,
                callGraph.callSiteCount(),
                variableCount
        );

        int[] callSiteIdBySite = new int[siteCount[0]];
        Arrays.fill(callSiteIdBySite, -1);

        for (int i = 0; i < callGraph.callSiteNodes.length; i++) {
            int site = siteIds.getOrDefault(callGraph.callSiteNodes[i], -1);

            if (site >= 0) {
                callSiteIdBySite[site] = i;
            }
        }

        return new MemoryAnalysisContext(
                root,
                callGraph,
                new MemoryShapeIndex(),
                templates,
                new MemoryTemplateSetPool(templates),
                variables.toArray(new Symbol[0]),
                variableIds,
                pointerVariableIds,
                pointerIndexByVariable,
                globalVariableIds,
                globalIndexByVariable,
                functionIds,
                callGraph.callSiteNodes,
                callSiteIdBySite,
                snapshotDemand,
                maxSnapshotDemand,
                siteCount[0],
                siteIds,
                blockLocals,
                localIds
        );
    }

    private static void collectDeclarations(
            SastNode node,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<Symbol, Integer> variableIds,
            List<Symbol> variables,
            List<Integer> globals
    ) {
        if (node == null) {
            return;
        }

        if (node.kind == SastKind.PROGRAM) {
            for (SastNode item : node.topLevelItems) {
                collectDeclarations(item, builder, variableIds, variables, globals);
            }

            return;
        }

        if (node.kind == SastKind.NAMESPACE) {
            for (SastNode item : node.items) {
                collectDeclarations(item, builder, variableIds, variables, globals);
            }

            return;
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            for (SastNode method : node.methods) {
                builder.addFunction(method.symbol, method);
            }

            return;
        }

        if (node.kind == SastKind.FUNCTION_DECL) {
            builder.addFunction(node.symbol, node);
            return;
        }

        if (node.kind == SastKind.VAR_DECL
                && node.symbol.kind == SymbolKind.GLOBAL) {
            int variableId = addVariable(
                    node.symbol,
                    variableIds,
                    variables
            );

            globals.add(variableId);
        }
    }

    private static void collectBodies(
            SastNode node,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<Symbol, Integer> variableIds,
            List<Symbol> variables,
            IdentityHashMap<SastNode, Integer> siteIds,
            int[] siteCount,
            IdentityHashMap<SastNode, int[]> blockLocals,
            List<List<Integer>> functionLocals
    ) {
        if (node == null) {
            return;
        }

        if (node.kind == SastKind.PROGRAM) {
            for (SastNode item : node.topLevelItems) {
                collectBodies(
                        item,
                        builder,
                        variableIds,
                        variables,
                        siteIds,
                        siteCount,
                        blockLocals,
                        functionLocals
                );
            }

            return;
        }

        if (node.kind == SastKind.NAMESPACE) {
            for (SastNode item : node.items) {
                collectBodies(
                        item,
                        builder,
                        variableIds,
                        variables,
                        siteIds,
                        siteCount,
                        blockLocals,
                        functionLocals
                );
            }

            return;
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            for (SastNode method : node.methods) {
                collectFunctionBody(
                        method,
                        builder,
                        variableIds,
                        variables,
                        siteIds,
                        siteCount,
                        blockLocals,
                        functionLocals
                );
            }

            return;
        }

        if (node.kind == SastKind.FUNCTION_DECL) {
            collectFunctionBody(
                    node,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount,
                    blockLocals,
                    functionLocals
            );
        }
    }

    private static void collectFunctionBody(
            SastNode function,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<Symbol, Integer> variableIds,
            List<Symbol> variables,
            IdentityHashMap<SastNode, Integer> siteIds,
            int[] siteCount,
            IdentityHashMap<SastNode, int[]> blockLocals,
            List<List<Integer>> functionLocals
    ) {
        int functionId = builder.functionId(function.symbol);

        for (int i = 0; i < function.parameterVariables.size(); i++) {
            Symbol parameter = function.parameterVariables.get(i);
            int variableId = addVariable(parameter, variableIds, variables);
            builder.setParameterVariableId(functionId, i, variableId);
        }

        if (function.body != null) {
            collectBlock(
                    function.body,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount,
                    blockLocals,
                    functionLocals.get(functionId)
            );
        }
    }

    private static void collectBlock(
            SastNode block,
            int functionId,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<Symbol, Integer> variableIds,
            List<Symbol> variables,
            IdentityHashMap<SastNode, Integer> siteIds,
            int[] siteCount,
            IdentityHashMap<SastNode, int[]> blockLocals,
            List<Integer> functionLocals
    ) {
        assignSite(block, siteIds, siteCount);

        List<Integer> directLocals = new ArrayList<>();

        for (SastNode statement : block.statements) {
            if (statement.kind == SastKind.VAR_DECL) {
                int variableId = addVariable(
                        statement.symbol,
                        variableIds,
                        variables
                );

                directLocals.add(variableId);

                if (!(statement.symbol.kind == SymbolKind.GLOBAL)) {
                    functionLocals.add(variableId);
                }
            }

            collectStatement(
                    statement,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount,
                    blockLocals,
                    functionLocals
            );
        }

        int[] locals = new int[directLocals.size()];

        for (int i = 0; i < locals.length; i++) {
            locals[i] = directLocals.get(i);
        }

        blockLocals.put(block, locals);
    }

    private static void collectStatement(
            SastNode statement,
            int functionId,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<Symbol, Integer> variableIds,
            List<Symbol> variables,
            IdentityHashMap<SastNode, Integer> siteIds,
            int[] siteCount,
            IdentityHashMap<SastNode, int[]> blockLocals,
            List<Integer> functionLocals
    ) {
        if (statement == null) {
            return;
        }

        assignSite(statement, siteIds, siteCount);

        if (statement.kind == SastKind.BLOCK) {
            collectBlock(
                    statement,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount,
                    blockLocals,
                    functionLocals
            );
            return;
        }

        if (statement.kind == SastKind.VAR_DECL) {
            collectExpression(
                    statement.staticInitializer,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (statement.kind == SastKind.ASSIGN) {
            collectExpression(
                    statement.target,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            collectExpression(
                    statement.value,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (statement.kind == SastKind.IF) {
            collectExpression(
                    statement.condition,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            collectStatement(
                    statement.thenBranch,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount,
                    blockLocals,
                    functionLocals
            );
            collectStatement(
                    statement.elseBranch,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount,
                    blockLocals,
                    functionLocals
            );
            return;
        }

        if (statement.kind == SastKind.WHILE) {
            collectExpression(
                    statement.condition,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            collectStatement(
                    statement.body,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount,
                    blockLocals,
                    functionLocals
            );

            for (SastNode update : statement.update) {
                collectStatement(
                        update,
                        functionId,
                        builder,
                        variableIds,
                        variables,
                        siteIds,
                        siteCount,
                        blockLocals,
                        functionLocals
                );
            }

            return;
        }

        if (statement.kind == SastKind.RETURN) {
            collectExpression(
                    statement.value,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (statement.kind == SastKind.EXPR_STMT) {
            collectExpression(
                    statement.expr,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
        }
    }

    private static void collectExpression(
            SastNode expression,
            int functionId,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<Symbol, Integer> variableIds,
            List<Symbol> variables,
            IdentityHashMap<SastNode, Integer> siteIds,
            int[] siteCount
    ) {
        if (expression == null) {
            return;
        }

        assignSite(expression, siteIds, siteCount);

        if (expression.kind == SastKind.INT_LITERAL
                || expression.kind == SastKind.NULL_LITERAL
                || expression.kind == SastKind.VAR_EXPR
                || expression.kind == SastKind.ENUM_VARIANT) {
            return;
        }

        if (expression.kind == SastKind.UNARY_OP) {
            collectExpression(
                    expression.expr,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression.kind == SastKind.BIN_OP) {
            collectExpression(
                    expression.left,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            collectExpression(
                    expression.right,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression.kind == SastKind.ARRAY_ACCESS) {
            collectExpression(
                    expression.target,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            collectExpression(
                    expression.index,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression.kind == SastKind.MEMBER_ACCESS) {
            collectExpression(
                    expression.target,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression.kind == SastKind.FUNCTION_CALL) {
            int calleeId = builder.functionId(expression.function);
            builder.addCallSite(expression, functionId, calleeId);

            for (SastNode argument : expression.args) {
                collectExpression(
                        argument,
                        functionId,
                        builder,
                        variableIds,
                        variables,
                        siteIds,
                        siteCount
                );
            }

            return;
        }

        if (expression.kind == SastKind.ARRAY_INTRINSIC_CALL) {
            collectExpression(
                    expression.receiver,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );

            for (SastNode argument : expression.args) {
                collectExpression(
                        argument,
                        functionId,
                        builder,
                        variableIds,
                        variables,
                        siteIds,
                        siteCount
                );
            }

            return;
        }

        if (expression.kind == SastKind.AGGREGATE_LITERAL) {
            for (SastNode field : expression.fields) {
                collectExpression(
                        field.value,
                        functionId,
                        builder,
                        variableIds,
                        variables,
                        siteIds,
                        siteCount
                );
            }
            return;
        }

        if (expression.kind == SastKind.GET_PTR) {
            collectExpression(
                    expression.target,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression.kind == SastKind.DEREF) {
            collectExpression(
                    expression.expr,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression.kind == SastKind.CAST) {
            collectExpression(
                    expression.expr,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
        }
    }

    private static int addVariable(
            Symbol symbol,
            IdentityHashMap<Symbol, Integer> variableIds,
            List<Symbol> variables
    ) {
        Integer existing = variableIds.get(symbol);

        if (existing != null) {
            return existing;
        }

        int id = variables.size();
        variableIds.put(symbol, id);
        variables.add(symbol);
        return id;
    }

    private static void assignSite(
            SastNode node,
            IdentityHashMap<SastNode, Integer> siteIds,
            int[] siteCount
    ) {
        if (node == null || siteIds.containsKey(node)) {
            return;
        }

        siteIds.put(node, siteCount[0]++);
    }

    private static int estimateSnapshotDemand(
            SastNode node,
            int depth
    ) {
        if (node == null) {
            return 0;
        }

        if (node.kind == SastKind.BLOCK) {
            int total = 0;

            for (SastNode statement : node.statements) {
                total += estimateSnapshotDemand(statement, depth);
            }

            return total;
        }

        if (node.kind == SastKind.IF) {
            int local = node.elseBranch == null ? 2 : 3;
            int nextDepth = depth < MAX_BRANCH_DEPTH ? depth + 1 : depth;

            return local
                    + estimateSnapshotDemand(node.thenBranch, nextDepth)
                    + estimateSnapshotDemand(node.elseBranch, nextDepth);
        }

        if (node.kind == SastKind.WHILE) {
            int total = 2 + estimateSnapshotDemand(node.body, depth);

            for (SastNode update : node.update) {
                total += estimateSnapshotDemand(update, depth);
            }

            return total;
        }

        if (node.kind == SastKind.BREAK
                || node.kind == SastKind.CONTINUE) {
            return 1;
        }

        return 0;
    }

    int variableId(Symbol symbol) {
        if (symbol == null) {
            return -1;
        }

        Integer id = variableIds.get(symbol);
        return id == null ? -1 : id;
    }

    Symbol variable(int variableId) {
        if (variableId < 0 || variableId >= variables.length) {
            return null;
        }

        return variables[variableId];
    }

    int pointerIndex(Symbol symbol) {
        int variableId = variableId(symbol);

        if (variableId < 0) {
            return -1;
        }

        return pointerIndexByVariable[variableId];
    }

    int globalIndex(Symbol symbol) {
        return globalIndexByVariableId(variableId(symbol));
    }

    int globalIndexByVariableId(int variableId) {
        if (variableId < 0 || variableId >= globalIndexByVariable.length) {
            return -1;
        }

        return globalIndexByVariable[variableId];
    }

    int siteId(SastNode node) {
        if (node == null) {
            return -1;
        }

        Integer id = siteIds.get(node);
        return id == null ? -1 : id;
    }

    int callSiteId(SastNode node) {
        int site = siteId(node);

        if (site < 0 || site >= callSiteIdBySite.length) {
            return -1;
        }

        return callSiteIdBySite[site];
    }

    int[] blockLocalIds(SastNode block) {
        int[] ids = blockLocals.get(block);
        return ids == null ? new int[0] : ids;
    }

    int[] functionLocalIds(int functionId) {
        if (functionId < 0 || functionId >= functionLocalIds.length) {
            return new int[0];
        }

        return functionLocalIds[functionId];
    }

    int functionId(Symbol symbol) {
        if (symbol == null) {
            return -1;
        }

        Integer id = functionIds.get(symbol);
        return id == null ? -1 : id;
    }

    SastNode functionDecl(int functionId) {
        if (functionId < 0 || functionId >= callGraph.decls.length) {
            return null;
        }

        return callGraph.decls[functionId];
    }

    boolean hasBody(int functionId) {
        return functionId >= 0
                && functionId < callGraph.hasBody.length
                && callGraph.hasBody[functionId];
    }

    int snapshotDemand(int functionId) {
        if (functionId < 0 || functionId >= snapshotDemand.length) {
            return maxSnapshotDemand;
        }

        return Math.max(snapshotDemand[functionId], maxSnapshotDemand);
    }
}
