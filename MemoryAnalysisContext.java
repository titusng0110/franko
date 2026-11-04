import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

final class MemoryAnalysisContext {
    static final int MAX_BRANCH_DEPTH = 12;

    final SemanticProgramNode root;
    final MemoryCallGraph callGraph;
    final MemoryShapeIndex shapes;
    final MemoryTemplateRegistry templates;
    final MemoryTemplateSetPool templateSets;

    final VariableSymbol[] variables;
    final IdentityHashMap<VariableSymbol, Integer> variableIds;
    final int[] pointerVariableIds;
    final int pointerVariableCount;
    final int[] pointerIndexByVariable;

    final int[] globalVariableIds;
    final int globalCount;
    final int[] globalIndexByVariable;
    final IdentityHashMap<FunctionSymbol, Integer> functionIds;

    final SemanticFunctionCallNode[] callSiteNodes;
    final int callSiteCount;
    final int[] callSiteIdBySite;

    final int[] snapshotDemand;
    final int maxSnapshotDemand;

    final int siteCount;
    final IdentityHashMap<SemanticASTNode, Integer> siteIds;

    private final IdentityHashMap<SemanticBlockNode, int[]> blockLocals;
    private final int[][] functionLocalIds;

    private MemoryAnalysisContext(
            SemanticProgramNode root,
            MemoryCallGraph callGraph,
            MemoryShapeIndex shapes,
            MemoryTemplateRegistry templates,
            MemoryTemplateSetPool templateSets,
            VariableSymbol[] variables,
            IdentityHashMap<VariableSymbol, Integer> variableIds,
            int[] pointerVariableIds,
            int[] pointerIndexByVariable,
            int[] globalVariableIds,
            int[] globalIndexByVariable,
            IdentityHashMap<FunctionSymbol, Integer> functionIds,
            SemanticFunctionCallNode[] callSiteNodes,
            int[] callSiteIdBySite,
            int[] snapshotDemand,
            int maxSnapshotDemand,
            int siteCount,
            IdentityHashMap<SemanticASTNode, Integer> siteIds,
            IdentityHashMap<SemanticBlockNode, int[]> blockLocals,
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

    static MemoryAnalysisContext build(SemanticProgramNode root) {
        MemoryCallGraph.Builder builder = new MemoryCallGraph.Builder();
        IdentityHashMap<VariableSymbol, Integer> variableIds = new IdentityHashMap<>();
        List<VariableSymbol> variables = new ArrayList<>();
        List<Integer> globals = new ArrayList<>();
        IdentityHashMap<SemanticASTNode, Integer> siteIds = new IdentityHashMap<>();
        IdentityHashMap<SemanticBlockNode, int[]> blockLocals = new IdentityHashMap<>();
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

        for (FunctionSymbol function : callGraph.functions) {
            maxParameters = Math.max(maxParameters, function.parameters.size());
        }

        int[] globalVariableIds = new int[globals.size()];
        int[] globalIndexByVariable = new int[variableCount];
        Arrays.fill(globalIndexByVariable, -1);

        for (int i = 0; i < globals.size(); i++) {
            globalVariableIds[i] = globals.get(i);
            globalIndexByVariable[globalVariableIds[i]] = i;
        }

        IdentityHashMap<FunctionSymbol, Integer> functionIds =
                new IdentityHashMap<>();

        for (int i = 0; i < callGraph.functions.length; i++) {
            functionIds.put(callGraph.functions[i], i);
        }

        int[] pointerIndexByVariable = new int[variableCount];
        Arrays.fill(pointerIndexByVariable, -1);
        List<Integer> pointerVariables = new ArrayList<>();

        for (int i = 0; i < variableCount; i++) {
            VariableSymbol symbol = variables.get(i);

            if (symbol.type instanceof SemanticPtrType) {
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
            SemanticFunctionDeclNode decl = callGraph.decls[i];

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
                variables.toArray(new VariableSymbol[0]),
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
            SemanticASTNode node,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<VariableSymbol, Integer> variableIds,
            List<VariableSymbol> variables,
            List<Integer> globals
    ) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticProgramNode program) {
            for (SemanticASTNode item : program.topLevelItems) {
                collectDeclarations(item, builder, variableIds, variables, globals);
            }

            return;
        }

        if (node instanceof SemanticNamespaceNode namespace) {
            for (SemanticASTNode item : namespace.items) {
                collectDeclarations(item, builder, variableIds, variables, globals);
            }

            return;
        }

        if (node instanceof SemanticStructDeclNode struct) {
            for (SemanticFunctionDeclNode method : struct.methods) {
                builder.addFunction(method.symbol, method);
            }

            return;
        }

        if (node instanceof SemanticFunctionDeclNode function) {
            builder.addFunction(function.symbol, function);
            return;
        }

        if (node instanceof SemanticVarDeclNode declaration
                && declaration.symbol instanceof GlobalVariableSymbol) {
            int variableId = addVariable(
                    declaration.symbol,
                    variableIds,
                    variables
            );

            globals.add(variableId);
        }
    }

    private static void collectBodies(
            SemanticASTNode node,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<VariableSymbol, Integer> variableIds,
            List<VariableSymbol> variables,
            IdentityHashMap<SemanticASTNode, Integer> siteIds,
            int[] siteCount,
            IdentityHashMap<SemanticBlockNode, int[]> blockLocals,
            List<List<Integer>> functionLocals
    ) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticProgramNode program) {
            for (SemanticASTNode item : program.topLevelItems) {
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

        if (node instanceof SemanticNamespaceNode namespace) {
            for (SemanticASTNode item : namespace.items) {
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

        if (node instanceof SemanticStructDeclNode struct) {
            for (SemanticFunctionDeclNode method : struct.methods) {
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

        if (node instanceof SemanticFunctionDeclNode function) {
            collectFunctionBody(
                    function,
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
            SemanticFunctionDeclNode function,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<VariableSymbol, Integer> variableIds,
            List<VariableSymbol> variables,
            IdentityHashMap<SemanticASTNode, Integer> siteIds,
            int[] siteCount,
            IdentityHashMap<SemanticBlockNode, int[]> blockLocals,
            List<List<Integer>> functionLocals
    ) {
        int functionId = builder.functionId(function.symbol);

        for (int i = 0; i < function.parameterVariables.size(); i++) {
            VariableSymbol parameter = function.parameterVariables.get(i);
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
            SemanticBlockNode block,
            int functionId,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<VariableSymbol, Integer> variableIds,
            List<VariableSymbol> variables,
            IdentityHashMap<SemanticASTNode, Integer> siteIds,
            int[] siteCount,
            IdentityHashMap<SemanticBlockNode, int[]> blockLocals,
            List<Integer> functionLocals
    ) {
        assignSite(block, siteIds, siteCount);

        List<Integer> directLocals = new ArrayList<>();

        for (SemanticStmtNode statement : block.statements) {
            if (statement instanceof SemanticVarDeclNode declaration) {
                int variableId = addVariable(
                        declaration.symbol,
                        variableIds,
                        variables
                );

                directLocals.add(variableId);

                if (!(declaration.symbol instanceof GlobalVariableSymbol)) {
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
            SemanticStmtNode statement,
            int functionId,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<VariableSymbol, Integer> variableIds,
            List<VariableSymbol> variables,
            IdentityHashMap<SemanticASTNode, Integer> siteIds,
            int[] siteCount,
            IdentityHashMap<SemanticBlockNode, int[]> blockLocals,
            List<Integer> functionLocals
    ) {
        if (statement == null) {
            return;
        }

        assignSite(statement, siteIds, siteCount);

        if (statement instanceof SemanticBlockNode block) {
            collectBlock(
                    block,
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

        if (statement instanceof SemanticVarDeclNode declaration) {
            collectExpression(
                    declaration.staticInitializer,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (statement instanceof SemanticAssignNode assignment) {
            collectExpression(
                    assignment.target,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            collectExpression(
                    assignment.value,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (statement instanceof SemanticIfNode branch) {
            collectExpression(
                    branch.condition,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            collectStatement(
                    branch.thenBranch,
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
                    branch.elseBranch,
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

        if (statement instanceof SemanticWhileNode loop) {
            collectExpression(
                    loop.condition,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            collectStatement(
                    loop.body,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount,
                    blockLocals,
                    functionLocals
            );

            for (SemanticStmtNode update : loop.update) {
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

        if (statement instanceof SemanticReturnNode returned) {
            collectExpression(
                    returned.value,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (statement instanceof SemanticExprStmtNode expressionStatement) {
            collectExpression(
                    expressionStatement.expr,
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
            SemanticExprNode expression,
            int functionId,
            MemoryCallGraph.Builder builder,
            IdentityHashMap<VariableSymbol, Integer> variableIds,
            List<VariableSymbol> variables,
            IdentityHashMap<SemanticASTNode, Integer> siteIds,
            int[] siteCount
    ) {
        if (expression == null) {
            return;
        }

        assignSite(expression, siteIds, siteCount);

        if (expression instanceof SemanticIntLiteralNode
                || expression instanceof SemanticNullLiteralNode
                || expression instanceof SemanticVarExprNode) {
            return;
        }

        if (expression instanceof SemanticUnaryOpNode unary) {
            collectExpression(
                    unary.expr,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression instanceof SemanticBinOpNode binary) {
            collectExpression(
                    binary.left,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            collectExpression(
                    binary.right,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression instanceof SemanticArrayAccessNode access) {
            collectExpression(
                    access.target,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            collectExpression(
                    access.index,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression instanceof SemanticMemberAccessNode member) {
            collectExpression(
                    member.target,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression instanceof SemanticFunctionCallNode call) {
            int calleeId = builder.functionId(call.function);
            builder.addCallSite(call, functionId, calleeId);

            for (SemanticExprNode argument : call.args) {
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

        if (expression instanceof SemanticArrayIntrinsicCallNode intrinsic) {
            collectExpression(
                    intrinsic.receiver,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );

            for (SemanticExprNode argument : intrinsic.args) {
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

        if (expression instanceof SemanticGetPtrNode getPtr) {
            collectExpression(
                    getPtr.target,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression instanceof SemanticDerefNode deref) {
            collectExpression(
                    deref.expr,
                    functionId,
                    builder,
                    variableIds,
                    variables,
                    siteIds,
                    siteCount
            );
            return;
        }

        if (expression instanceof SemanticCastExprNode cast) {
            collectExpression(
                    cast.expr,
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
            VariableSymbol symbol,
            IdentityHashMap<VariableSymbol, Integer> variableIds,
            List<VariableSymbol> variables
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
            SemanticASTNode node,
            IdentityHashMap<SemanticASTNode, Integer> siteIds,
            int[] siteCount
    ) {
        if (node == null || siteIds.containsKey(node)) {
            return;
        }

        siteIds.put(node, siteCount[0]++);
    }

    private static int estimateSnapshotDemand(
            SemanticASTNode node,
            int depth
    ) {
        if (node == null) {
            return 0;
        }

        if (node instanceof SemanticBlockNode block) {
            int total = 0;

            for (SemanticStmtNode statement : block.statements) {
                total += estimateSnapshotDemand(statement, depth);
            }

            return total;
        }

        if (node instanceof SemanticIfNode branch) {
            int local = branch.elseBranch == null ? 2 : 3;
            int nextDepth = depth < MAX_BRANCH_DEPTH ? depth + 1 : depth;

            return local
                    + estimateSnapshotDemand(branch.thenBranch, nextDepth)
                    + estimateSnapshotDemand(branch.elseBranch, nextDepth);
        }

        if (node instanceof SemanticWhileNode loop) {
            int total = 2 + estimateSnapshotDemand(loop.body, depth);

            for (SemanticStmtNode update : loop.update) {
                total += estimateSnapshotDemand(update, depth);
            }

            return total;
        }

        if (node instanceof SemanticBreakNode
                || node instanceof SemanticContinueNode) {
            return 1;
        }

        return 0;
    }

    int variableId(VariableSymbol symbol) {
        if (symbol == null) {
            return -1;
        }

        Integer id = variableIds.get(symbol);
        return id == null ? -1 : id;
    }

    VariableSymbol variable(int variableId) {
        if (variableId < 0 || variableId >= variables.length) {
            return null;
        }

        return variables[variableId];
    }

    int pointerIndex(VariableSymbol symbol) {
        int variableId = variableId(symbol);

        if (variableId < 0) {
            return -1;
        }

        return pointerIndexByVariable[variableId];
    }

    int globalIndex(VariableSymbol symbol) {
        return globalIndexByVariableId(variableId(symbol));
    }

    int globalIndexByVariableId(int variableId) {
        if (variableId < 0 || variableId >= globalIndexByVariable.length) {
            return -1;
        }

        return globalIndexByVariable[variableId];
    }

    int siteId(SemanticASTNode node) {
        if (node == null) {
            return -1;
        }

        Integer id = siteIds.get(node);
        return id == null ? -1 : id;
    }

    int callSiteId(SemanticASTNode node) {
        int site = siteId(node);

        if (site < 0 || site >= callSiteIdBySite.length) {
            return -1;
        }

        return callSiteIdBySite[site];
    }

    int[] blockLocalIds(SemanticBlockNode block) {
        int[] ids = blockLocals.get(block);
        return ids == null ? new int[0] : ids;
    }

    int[] functionLocalIds(int functionId) {
        if (functionId < 0 || functionId >= functionLocalIds.length) {
            return new int[0];
        }

        return functionLocalIds[functionId];
    }

    int functionId(FunctionSymbol symbol) {
        if (symbol == null) {
            return -1;
        }

        Integer id = functionIds.get(symbol);
        return id == null ? -1 : id;
    }

    SemanticFunctionDeclNode functionDecl(int functionId) {
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
