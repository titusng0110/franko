import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class InactiveDeclarationPruner {

    private final Set<FunctionSymbol> keptFunctions = newSet();
    private final Set<VariableSymbol> keptVariables = newSet();
    private final Set<StructSymbol> keptStructs = newSet();

    private final IdentityHashMap<FunctionSymbol, SemanticFunctionDeclNode> declBySymbol =
            new IdentityHashMap<>();

    private final Deque<StructSymbol> structWorklist = new ArrayDeque<>();

    private boolean entryPointFound = false;

    private final List<SemanticVarDeclNode> globalVarDecls = new ArrayList<>();

    private static <T> Set<T> newSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    SemanticASTNode prune(SemanticASTNode root) {
        indexDeclarations(root);

        if (!entryPointFound) {
            return root;
        }

        computeKeptFunctions();
        computeKeptVariables();
        computeKeptStructs(root);

        return rebuildItem(root);
    }

    private void indexDeclarations(SemanticASTNode node) {

        if (node instanceof SemanticProgramNode program) {
            for (SemanticASTNode item : program.topLevelItems) {
                indexDeclarations(item);
            }

            return;
        }

        if (node instanceof SemanticNamespaceNode namespace) {
            for (SemanticASTNode item : namespace.items) {
                indexDeclarations(item);
            }

            return;
        }

        if (node instanceof SemanticStructDeclNode struct) {
            for (SemanticFunctionDeclNode method : struct.methods) {
                indexFunction(method);
            }

            return;
        }

        if (node instanceof SemanticFunctionDeclNode fn) {
            indexFunction(fn);
            return;
        }

        if (node instanceof SemanticVarDeclNode varDecl) {
            globalVarDecls.add(varDecl);
        }
    }

    private void indexFunction(SemanticFunctionDeclNode fn) {
        declBySymbol.put(fn.symbol, fn);

        if (fn.symbol instanceof MethodSymbol) {
            return;
        }

        if (fn.symbol.name.equals("main") && fn.symbol.namespace == null) {
            entryPointFound = true;
        }
    }

    private void computeKeptFunctions() {

        SemanticFunctionDeclNode main = null;

        for (SemanticFunctionDeclNode fn : declBySymbol.values()) {

            if (!(fn.symbol instanceof MethodSymbol)
                    && fn.symbol.name.equals("main")
                    && fn.symbol.namespace == null) {
                main = fn;
                break;
            }
        }

        if (main == null) {
            return;
        }

        Deque<FunctionSymbol> worklist = new ArrayDeque<>();

        keptFunctions.add(main.symbol);
        worklist.add(main.symbol);

        while (!worklist.isEmpty()) {
            FunctionSymbol fn = worklist.poll();
            SemanticFunctionDeclNode node = declBySymbol.get(fn);

            if (node == null) {
                continue;
            }

            Set<FunctionSymbol> calls = newSet();
            collectCalls(node.body, calls);

            for (FunctionSymbol callee : calls) {

                if (keptFunctions.add(callee)) {
                    worklist.add(callee);
                }
            }
        }
    }

    private void computeKeptVariables() {

        for (FunctionSymbol fn : keptFunctions) {
            SemanticFunctionDeclNode node = declBySymbol.get(fn);

            if (node == null) {
                continue;
            }

            Set<VariableSymbol> refs = newSet();
            collectVariables(node.body, refs);

            keptVariables.addAll(refs);
        }

        boolean changed = true;

        while (changed) {
            changed = false;

            for (SemanticVarDeclNode global : globalVarDecls) {
                if (global.staticInitializer == null
                        || !keptVariables.contains(global.symbol)) {
                    continue;
                }

                Set<VariableSymbol> refs = newSet();
                collectExprVariables(global.staticInitializer, refs);

                for (VariableSymbol ref : refs) {
                    if (keptVariables.add(ref)) {
                        changed = true;
                    }
                }
            }
        }
    }

    private void computeKeptStructs(SemanticASTNode root) {

        for (FunctionSymbol fn : keptFunctions) {
            markType(fn.type());

            if (fn instanceof MethodSymbol method) {
                markStruct(method.owner);
            }
        }

        collectKeptVarDeclTypes(root);

        for (FunctionSymbol fn : keptFunctions) {
            SemanticFunctionDeclNode node = declBySymbol.get(fn);

            if (node != null) {
                collectExprTypes(node.body);
            }
        }

        while (!structWorklist.isEmpty()) {
            StructSymbol struct = structWorklist.poll();

            for (FieldSymbol field : struct.fields) {
                markType(field.type);
            }

            List<MethodSymbol> kept = struct.methods.values().stream()
                    .flatMap(List::stream)
                    .filter(keptFunctions::contains)
                    .toList();

            for (MethodSymbol method : kept) {
                markType(method.type());
            }
        }
    }

    private void collectKeptVarDeclTypes(SemanticASTNode node) {

        if (node instanceof SemanticVarDeclNode varDecl) {

            if (keptVariables.contains(varDecl.symbol)) {
                markType(varDecl.symbol.type());
            }

            return;
        }

        if (node instanceof SemanticProgramNode program) {
            for (SemanticASTNode item : program.topLevelItems) {
                collectKeptVarDeclTypes(item);
            }

            return;
        }

        if (node instanceof SemanticNamespaceNode namespace) {
            for (SemanticASTNode item : namespace.items) {
                collectKeptVarDeclTypes(item);
            }

            return;
        }

        if (node instanceof SemanticStructDeclNode) {
            return;
        }

        if (node instanceof SemanticFunctionDeclNode fn) {

            if (!keptFunctions.contains(fn.symbol)) {
                return;
            }

            for (VariableSymbol param : fn.parameterVariables) {
                markType(param.type());
            }

            collectStmtVarDeclTypes(fn.body);

            return;
        }
    }

    private void collectStmtVarDeclTypes(SemanticStmtNode stmt) {

        if (stmt instanceof SemanticVarDeclNode varDecl) {

            if (keptVariables.contains(varDecl.symbol)) {
                markType(varDecl.symbol.type());
            }

            return;
        }

        if (stmt instanceof SemanticBlockNode block) {
            for (SemanticStmtNode inner : block.statements) {
                collectStmtVarDeclTypes(inner);
            }

            return;
        }

        if (stmt instanceof SemanticIfNode ifNode) {
            collectStmtVarDeclTypes(ifNode.thenBranch);

            if (ifNode.elseBranch != null) {
                collectStmtVarDeclTypes(ifNode.elseBranch);
            }

            return;
        }

        if (stmt instanceof SemanticWhileNode whileNode) {
            collectStmtVarDeclTypes(whileNode.body);

            for (SemanticStmtNode update : whileNode.update) {
                collectStmtVarDeclTypes(update);
            }
        }
    }

    private void markType(SemanticType type) {

        if (type instanceof SemanticStructType struct) {
            markStruct(struct.structSymbol);

            return;
        }

        if (type instanceof SemanticPtrType ptr) {
            markType(ptr.referencedType);

            return;
        }

        if (type instanceof SemanticDynamicArrayType dynamic) {
            markType(dynamic.elementType);

            return;
        }

        if (type instanceof SemanticStaticArrayType staticArray) {
            markType(staticArray.elementType);

            return;
        }

        if (type instanceof SemanticFunctionType functionType) {
            for (SemanticType param : functionType.parameterTypes) {
                markType(param);
            }

            markType(functionType.returnType);
        }
    }

    private void markStruct(StructSymbol struct) {

        if (keptStructs.add(struct)) {
            structWorklist.add(struct);
        }
    }

    private SemanticASTNode rebuildItem(SemanticASTNode node) {

        if (node instanceof SemanticProgramNode program) {
            List<SemanticASTNode> items = rebuildItemList(program.topLevelItems);

            return items == program.topLevelItems
                    ? program
                    : new SemanticProgramNode(items);
        }

        if (node instanceof SemanticNamespaceNode namespace) {
            List<SemanticASTNode> items = rebuildItemList(namespace.items);

            if (items == namespace.items) {
                return namespace;
            }

            return items.isEmpty() ? null : new SemanticNamespaceNode(namespace.name, items);
        }

        if (node instanceof SemanticFunctionDeclNode fn) {

            if (!keptFunctions.contains(fn.symbol)) {
                return null;
            }

            SemanticBlockNode body = rebuildBlock(fn.body);

            return body == fn.body
                    ? fn
                    : new SemanticFunctionDeclNode(fn.symbol, fn.parameterVariables, body);
        }

        if (node instanceof SemanticVarDeclNode varDecl) {

            if (!keptVariables.contains(varDecl.symbol)) {
                return null;
            }

            return varDecl;
        }

        if (node instanceof SemanticStructDeclNode struct) {

            if (!keptStructs.contains(struct.symbol)) {
                return null;
            }

            List<SemanticFunctionDeclNode> methods = null;

            for (int i = 0; i < struct.methods.size(); i++) {
                SemanticFunctionDeclNode method = struct.methods.get(i);

                if (!keptFunctions.contains(method.symbol)) {
                    methods = methods == null
                            ? new ArrayList<>(struct.methods.subList(0, i))
                            : methods;

                    continue;
                }

                SemanticFunctionDeclNode rebuilt =
                        (SemanticFunctionDeclNode) rebuildItem(method);

                if (methods == null && rebuilt != method) {
                    methods = new ArrayList<>(struct.methods.subList(0, i));
                }

                if (methods != null) {
                    methods.add(rebuilt);
                }
            }

            return methods == null
                    ? struct
                    : new SemanticStructDeclNode(struct.symbol, methods);
        }

        return node;
    }

    private List<SemanticASTNode> rebuildItemList(List<SemanticASTNode> items) {
        List<SemanticASTNode> out = null;

        for (int i = 0; i < items.size(); i++) {
            SemanticASTNode item = items.get(i);
            SemanticASTNode rebuilt = rebuildItem(item);

            if (out == null && rebuilt != item) {
                out = new ArrayList<>(items.subList(0, i));
            }

            if (out != null && rebuilt != null) {
                out.add(rebuilt);
            }
        }

        return out == null ? items : out;
    }

    private SemanticBlockNode rebuildBlock(SemanticBlockNode block) {
        List<SemanticStmtNode> out = null;

        for (int i = 0; i < block.statements.size(); i++) {
            SemanticStmtNode stmt = block.statements.get(i);
            SemanticStmtNode rebuilt = rebuildStmt(stmt);

            if (out == null && rebuilt != stmt) {
                out = new ArrayList<>(block.statements.subList(0, i));
            }

            if (out != null && rebuilt != null) {
                out.add(rebuilt);
            }
        }

        return out == null ? block : new SemanticBlockNode(out);
    }

    private SemanticStmtNode rebuildStmt(SemanticStmtNode stmt) {

        if (stmt instanceof SemanticVarDeclNode varDecl) {

            if (!keptVariables.contains(varDecl.symbol)) {
                return null;
            }

            return varDecl;
        }

        if (stmt instanceof SemanticBlockNode block) {
            SemanticBlockNode rebuilt = rebuildBlock(block);

            return rebuilt == block ? block : rebuilt;
        }

        if (stmt instanceof SemanticIfNode ifNode) {
            SemanticStmtNode thenBranch = rebuildStmt(ifNode.thenBranch);
            SemanticStmtNode elseBranch =
                    ifNode.elseBranch == null ? null : rebuildStmt(ifNode.elseBranch);

            if (thenBranch == ifNode.thenBranch && elseBranch == ifNode.elseBranch) {
                return ifNode;
            }

            return new SemanticIfNode(
                    ifNode.condition,
                    thenBranch == null ? new SemanticBlockNode(List.of()) : thenBranch,
                    elseBranch
            );
        }

        if (stmt instanceof SemanticWhileNode whileNode) {
            SemanticStmtNode body = rebuildStmt(whileNode.body);

            if (body == whileNode.body) {
                return whileNode;
            }

            return new SemanticWhileNode(
                    whileNode.condition,
                    body == null ? new SemanticBlockNode(List.of()) : body,
                    whileNode.update
            );
        }

        return stmt;
    }

    private static void collectCalls(SemanticStmtNode stmt, Set<FunctionSymbol> out) {

        if (stmt instanceof SemanticBlockNode block) {
            for (SemanticStmtNode inner : block.statements) {
                collectCalls(inner, out);
            }

            return;
        }

        if (stmt instanceof SemanticVarDeclNode) {
            return;
        }

        if (stmt instanceof SemanticAssignNode assign) {
            collectExprCalls(assign.target, out);
            collectExprCalls(assign.value, out);

            return;
        }

        if (stmt instanceof SemanticIfNode ifNode) {
            collectExprCalls(ifNode.condition, out);
            collectCalls(ifNode.thenBranch, out);

            if (ifNode.elseBranch != null) {
                collectCalls(ifNode.elseBranch, out);
            }

            return;
        }

        if (stmt instanceof SemanticWhileNode whileNode) {
            collectExprCalls(whileNode.condition, out);
            collectCalls(whileNode.body, out);

            for (SemanticStmtNode update : whileNode.update) {
                collectCalls(update, out);
            }

            return;
        }

        if (stmt instanceof SemanticPrintNode print) {
            for (SemanticExprNode arg : print.args) {
                collectExprCalls(arg, out);
            }

            return;
        }

        if (stmt instanceof SemanticReturnNode returnNode) {

            if (returnNode.value != null) {
                collectExprCalls(returnNode.value, out);
            }

            return;
        }

        if (stmt instanceof SemanticExprStmtNode exprStmt) {
            collectExprCalls(exprStmt.expr, out);
        }
    }

    private static void collectExprCalls(SemanticExprNode expr, Set<FunctionSymbol> out) {

        if (expr instanceof SemanticFunctionCallNode call) {
            out.add(call.function);

            for (SemanticExprNode arg : call.args) {
                collectExprCalls(arg, out);
            }

            return;
        }

        if (expr instanceof SemanticArrayIntrinsicCallNode intrinsic) {
            collectExprCalls(intrinsic.receiver, out);

            for (SemanticExprNode arg : intrinsic.args) {
                collectExprCalls(arg, out);
            }

            return;
        }

        if (expr instanceof SemanticUnaryOpNode unary) {
            collectExprCalls(unary.expr, out);

            return;
        }

        if (expr instanceof SemanticBinOpNode bin) {
            collectExprCalls(bin.left, out);
            collectExprCalls(bin.right, out);

            return;
        }

        if (expr instanceof SemanticArrayAccessNode access) {
            collectExprCalls(access.target, out);
            collectExprCalls(access.index, out);

            return;
        }

        if (expr instanceof SemanticMemberAccessNode member) {
            collectExprCalls(member.target, out);

            return;
        }

        if (expr instanceof SemanticGetPtrNode getPtr) {
            collectExprCalls(getPtr.target, out);

            return;
        }

        if (expr instanceof SemanticDerefNode deref) {
            collectExprCalls(deref.expr, out);

            return;
        }

        if (expr instanceof SemanticCastExprNode cast) {
            collectExprCalls(cast.expr, out);
        }
    }

    private static void collectVariables(SemanticStmtNode stmt, Set<VariableSymbol> out) {

        if (stmt instanceof SemanticBlockNode block) {
            for (SemanticStmtNode inner : block.statements) {
                collectVariables(inner, out);
            }

            return;
        }

        if (stmt instanceof SemanticAssignNode assign) {
            collectExprVariables(assign.target, out);
            collectExprVariables(assign.value, out);

            return;
        }

        if (stmt instanceof SemanticIfNode ifNode) {
            collectExprVariables(ifNode.condition, out);
            collectVariables(ifNode.thenBranch, out);

            if (ifNode.elseBranch != null) {
                collectVariables(ifNode.elseBranch, out);
            }

            return;
        }

        if (stmt instanceof SemanticWhileNode whileNode) {
            collectExprVariables(whileNode.condition, out);
            collectVariables(whileNode.body, out);

            for (SemanticStmtNode update : whileNode.update) {
                collectVariables(update, out);
            }

            return;
        }

        if (stmt instanceof SemanticPrintNode print) {
            for (SemanticExprNode arg : print.args) {
                collectExprVariables(arg, out);
            }

            return;
        }

        if (stmt instanceof SemanticReturnNode returnNode) {

            if (returnNode.value != null) {
                collectExprVariables(returnNode.value, out);
            }

            return;
        }

        if (stmt instanceof SemanticExprStmtNode exprStmt) {
            collectExprVariables(exprStmt.expr, out);
        }
    }

    private static void collectExprVariables(SemanticExprNode expr, Set<VariableSymbol> out) {

        if (expr instanceof SemanticVarExprNode varExpr) {
            out.add(varExpr.symbol);

            return;
        }

        if (expr instanceof SemanticArrayIntrinsicCallNode intrinsic) {
            collectExprVariables(intrinsic.receiver, out);

            for (SemanticExprNode arg : intrinsic.args) {
                collectExprVariables(arg, out);
            }

            return;
        }

        if (expr instanceof SemanticFunctionCallNode call) {
            for (SemanticExprNode arg : call.args) {
                collectExprVariables(arg, out);
            }

            return;
        }

        if (expr instanceof SemanticUnaryOpNode unary) {
            collectExprVariables(unary.expr, out);

            return;
        }

        if (expr instanceof SemanticBinOpNode bin) {
            collectExprVariables(bin.left, out);
            collectExprVariables(bin.right, out);

            return;
        }

        if (expr instanceof SemanticArrayAccessNode access) {
            collectExprVariables(access.target, out);
            collectExprVariables(access.index, out);

            return;
        }

        if (expr instanceof SemanticMemberAccessNode member) {
            collectExprVariables(member.target, out);

            return;
        }

        if (expr instanceof SemanticGetPtrNode getPtr) {
            collectExprVariables(getPtr.target, out);

            return;
        }

        if (expr instanceof SemanticDerefNode deref) {
            collectExprVariables(deref.expr, out);

            return;
        }

        if (expr instanceof SemanticCastExprNode cast) {
            collectExprVariables(cast.expr, out);
        }
    }

    private void collectExprTypes(SemanticStmtNode stmt) {

        if (stmt instanceof SemanticBlockNode block) {
            for (SemanticStmtNode inner : block.statements) {
                collectExprTypes(inner);
            }

            return;
        }

        if (stmt instanceof SemanticVarDeclNode varDecl) {

            if (keptVariables.contains(varDecl.symbol)) {
                markType(varDecl.symbol.type());
            }

            return;
        }

        if (stmt instanceof SemanticAssignNode assign) {
            markType(assign.value.type);
            collectExprTypeExprs(assign.target);
            collectExprTypeExprs(assign.value);

            return;
        }

        if (stmt instanceof SemanticIfNode ifNode) {
            markType(ifNode.condition.type);
            collectExprTypeExprs(ifNode.condition);
            collectExprTypes(ifNode.thenBranch);

            if (ifNode.elseBranch != null) {
                collectExprTypes(ifNode.elseBranch);
            }

            return;
        }

        if (stmt instanceof SemanticWhileNode whileNode) {
            markType(whileNode.condition.type);
            collectExprTypeExprs(whileNode.condition);
            collectExprTypes(whileNode.body);

            for (SemanticStmtNode update : whileNode.update) {
                collectExprTypes(update);
            }

            return;
        }

        if (stmt instanceof SemanticPrintNode print) {
            for (SemanticExprNode arg : print.args) {
                markType(arg.type);
                collectExprTypeExprs(arg);
            }

            return;
        }

        if (stmt instanceof SemanticReturnNode returnNode) {

            if (returnNode.value != null) {
                markType(returnNode.value.type);
                collectExprTypeExprs(returnNode.value);
            }

            return;
        }

        if (stmt instanceof SemanticExprStmtNode exprStmt) {
            markType(exprStmt.expr.type);
            collectExprTypeExprs(exprStmt.expr);
        }
    }

    private void collectExprTypeExprs(SemanticExprNode expr) {
        markType(expr.type);

        if (expr instanceof SemanticArrayIntrinsicCallNode intrinsic) {
            collectExprTypeExprs(intrinsic.receiver);

            for (SemanticExprNode arg : intrinsic.args) {
                collectExprTypeExprs(arg);
            }

            return;
        }

        if (expr instanceof SemanticFunctionCallNode call) {

            for (SemanticExprNode arg : call.args) {
                collectExprTypeExprs(arg);
            }

            return;
        }

        if (expr instanceof SemanticUnaryOpNode unary) {
            collectExprTypeExprs(unary.expr);

            return;
        }

        if (expr instanceof SemanticBinOpNode bin) {
            collectExprTypeExprs(bin.left);
            collectExprTypeExprs(bin.right);

            return;
        }

        if (expr instanceof SemanticArrayAccessNode access) {
            collectExprTypeExprs(access.target);
            collectExprTypeExprs(access.index);

            return;
        }

        if (expr instanceof SemanticMemberAccessNode member) {
            collectExprTypeExprs(member.target);

            return;
        }

        if (expr instanceof SemanticGetPtrNode getPtr) {
            collectExprTypeExprs(getPtr.target);

            return;
        }

        if (expr instanceof SemanticDerefNode deref) {
            collectExprTypeExprs(deref.expr);

            return;
        }

        if (expr instanceof SemanticCastExprNode cast) {
            collectExprTypeExprs(cast.expr);
        }
    }
}
