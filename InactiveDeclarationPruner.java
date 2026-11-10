import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class InactiveDeclarationPruner {

    private final Set<Symbol> keptFunctions = newSet();
    private final Set<Symbol> keptVariables = newSet();
    private final Set<StructSymbol> keptStructs = newSet();
    private final Set<EnumSymbol> keptEnums = newSet();

    private final IdentityHashMap<Symbol, SastNode> declBySymbol =
            new IdentityHashMap<>();

    private final Deque<StructSymbol> structWorklist = new ArrayDeque<>();

    private boolean entryPointFound = false;

    private final List<SastNode> globalVarDecls = new ArrayList<>();

    private static <T> Set<T> newSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    SastDocument prune(SastDocument document) {
        return new SastDocument(
                SastPhase.INACTIVE_PRUNED,
                document.entryKind,
                document.sources,
                document.symbols,
                prune(document.root)
        );
    }

    SastNode prune(SastNode root) {
        indexDeclarations(root);

        if (!entryPointFound) {
            return root;
        }

        computeKeptFunctions();
        computeKeptVariables();
        computeKeptStructs(root);

        return rebuildItem(root);
    }

    private void indexDeclarations(SastNode node) {

        if (node.kind == SastKind.PROGRAM) {
            for (SastNode item : node.topLevelItems) {
                indexDeclarations(item);
            }

            return;
        }

        if (node.kind == SastKind.NAMESPACE) {
            for (SastNode item : node.items) {
                indexDeclarations(item);
            }

            return;
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            for (SastNode method : node.methods) {
                indexFunction(method);
            }

            return;
        }

        if (node.kind == SastKind.FUNCTION_DECL) {
            indexFunction(node);
            return;
        }

        if (node.kind == SastKind.VAR_DECL) {
            globalVarDecls.add(node);
        }
    }

    private void indexFunction(SastNode fn) {
        declBySymbol.put(fn.symbol, fn);

        if (fn.symbol.kind == SymbolKind.METHOD) {
            return;
        }

        if (fn.symbol.name.equals("main") && fn.symbol.namespace == null) {
            entryPointFound = true;
        }
    }

    private void computeKeptFunctions() {

        SastNode main = null;

        for (SastNode fn : declBySymbol.values()) {

            if (!(fn.symbol.kind == SymbolKind.METHOD)
                    && fn.symbol.name.equals("main")
                    && fn.symbol.namespace == null) {
                main = fn;
                break;
            }
        }

        if (main == null) {
            return;
        }

        Deque<Symbol> worklist = new ArrayDeque<>();

        keptFunctions.add(main.symbol);
        worklist.add(main.symbol);

        while (!worklist.isEmpty()) {
            Symbol fn = worklist.poll();
            SastNode node = declBySymbol.get(fn);

            if (node == null) {
                continue;
            }

            Set<Symbol> calls = newSet();
            collectCalls(node.body, calls);

            for (Symbol callee : calls) {

                if (keptFunctions.add(callee)) {
                    worklist.add(callee);
                }
            }
        }
    }

    private void computeKeptVariables() {

        for (Symbol fn : keptFunctions) {
            SastNode node = declBySymbol.get(fn);

            if (node == null) {
                continue;
            }

            Set<Symbol> refs = newSet();
            collectVariables(node.body, refs);

            keptVariables.addAll(refs);
        }

        boolean changed = true;

        while (changed) {
            changed = false;

            for (SastNode global : globalVarDecls) {
                if (!keptVariables.contains(global.symbol)) {
                    continue;
                }

                Set<Symbol> refs = newSet();

                if (global.staticInitializer != null) {
                    collectExprVariables(global.staticInitializer, refs);
                }

                for (Symbol ref : refs) {
                    if (keptVariables.add(ref)) {
                        changed = true;
                    }
                }
            }
        }
    }

    private void computeKeptStructs(SastNode root) {

        for (Symbol fn : keptFunctions) {
            markType(fn.type());

            if (fn.kind == SymbolKind.METHOD) {
                markStruct(fn.owner);
            }
        }

        collectKeptVarDeclTypes(root);

        for (Symbol fn : keptFunctions) {
            SastNode node = declBySymbol.get(fn);

            if (node != null) {
                collectExprTypes(node.body);
            }
        }

        while (!structWorklist.isEmpty()) {
            StructSymbol struct = structWorklist.poll();

            for (FieldSymbol field : struct.fields) {
                markType(field.type);
            }

            List<Symbol> kept = new ArrayList<>();

            for (List<Symbol> overloads : struct.methods.values()) {
                for (Symbol method : overloads) {
                    if (keptFunctions.contains(method)) {
                        kept.add(method);
                    }
                }
            }

            for (Symbol method : kept) {
                markType(method.type());
            }
        }
    }

    private void collectKeptVarDeclTypes(SastNode node) {

        if (node.kind == SastKind.VAR_DECL) {

            if (keptVariables.contains(node.symbol)) {
                markType(node.symbol.type());
            }

            return;
        }

        if (node.kind == SastKind.PROGRAM) {
            for (SastNode item : node.topLevelItems) {
                collectKeptVarDeclTypes(item);
            }

            return;
        }

        if (node.kind == SastKind.NAMESPACE) {
            for (SastNode item : node.items) {
                collectKeptVarDeclTypes(item);
            }

            return;
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            return;
        }

        if (node.kind == SastKind.FUNCTION_DECL) {

            if (!keptFunctions.contains(node.symbol)) {
                return;
            }

            for (Symbol param : node.parameterVariables) {
                markType(param.type());
            }

            collectStmtVarDeclTypes(node.body);

            return;
        }
    }

    private void collectStmtVarDeclTypes(SastNode stmt) {

        if (stmt.kind == SastKind.VAR_DECL) {

            if (keptVariables.contains(stmt.symbol)) {
                markType(stmt.symbol.type());
            }

            return;
        }

        if (stmt.kind == SastKind.VAR_DECL_INIT) {

            if (keptVariables.contains(stmt.symbol)) {
                markType(stmt.symbol.type());

                for (SastNode initializer : stmt.initializers) {
                    collectExprTypes(initializer);
                }
            }

            return;
        }

        if (stmt.kind == SastKind.BLOCK) {
            for (SastNode inner : stmt.statements) {
                collectStmtVarDeclTypes(inner);
            }

            return;
        }

        if (stmt.kind == SastKind.IF) {
            collectStmtVarDeclTypes(stmt.thenBranch);

            if (stmt.elseBranch != null) {
                collectStmtVarDeclTypes(stmt.elseBranch);
            }

            return;
        }

        if (stmt.kind == SastKind.WHILE) {
            collectStmtVarDeclTypes(stmt.body);

            for (SastNode update : stmt.update) {
                collectStmtVarDeclTypes(update);
            }
        }
    }

    private void markType(SastType type) {

        if (type.kind == SastTypeKind.ENUM) {
            keptEnums.add(type.enumSymbol);

            return;
        }

        if (type.kind == SastTypeKind.STRUCT) {
            markStruct(type.structSymbol);

            return;
        }

        if (type.kind == SastTypeKind.TUPLE) {
            markStruct(type.cppSymbol);

            return;
        }

        if (type.kind == SastTypeKind.PTR) {
            markType(type.referencedType);

            return;
        }

        if (type.kind == SastTypeKind.DYNAMIC_ARRAY) {
            markType(type.elementType);

            return;
        }

        if (type.kind == SastTypeKind.STATIC_ARRAY) {
            markType(type.elementType);

            return;
        }

        if (type.kind == SastTypeKind.FUNCTION) {
            for (SastType param : type.parameterTypes) {
                markType(param);
            }

            markType(type.returnType);
        }
    }

    private void markStruct(StructSymbol struct) {

        if (keptStructs.add(struct)) {
            structWorklist.add(struct);
        }
    }

    private SastNode rebuildItem(SastNode node) {

        if (node.kind == SastKind.PROGRAM) {
            List<SastNode> items = rebuildItemList(node.topLevelItems);

            return items == node.topLevelItems
                    ? node
                    : SastNode.program(node.span, items);
        }

        if (node.kind == SastKind.NAMESPACE) {
            List<SastNode> items = rebuildItemList(node.items);

            if (items == node.items) {
                return node;
            }

            return items.isEmpty()
                    ? null
                    : SastNode.namespace(node.span, node.name, items);
        }

        if (node.kind == SastKind.FUNCTION_DECL) {

            if (!keptFunctions.contains(node.symbol)) {
                return null;
            }

            SastNode body = rebuildBlock(node.body);

            return body == node.body
                    ? node
                    : SastNode.functionDecl(
                            node.span,
                            node.symbol,
                            node.parameterVariables,
                            body
                    );
        }

        if (node.kind == SastKind.VAR_DECL) {

            if (!keptVariables.contains(node.symbol)) {
                return null;
            }

            return node;
        }

        if (node.kind == SastKind.STRUCT_DECL) {

            if (!keptStructs.contains(node.structSymbol)) {
                return null;
            }

            List<SastNode> methods = null;

            for (int i = 0; i < node.methods.size(); i++) {
                SastNode method = node.methods.get(i);

                if (!keptFunctions.contains(method.symbol)) {
                    methods = methods == null
                            ? new ArrayList<>(node.methods.subList(0, i))
                            : methods;

                    continue;
                }

                SastNode rebuilt = rebuildItem(method);

                if (methods == null && rebuilt != method) {
                    methods = new ArrayList<>(node.methods.subList(0, i));
                }

                if (methods != null) {
                    methods.add(rebuilt);
                }
            }

            return methods == null
                    ? node
                    : SastNode.structDecl(
                            node.span,
                            node.structSymbol,
                            methods
                    );
        }

        if (node.kind == SastKind.ENUM_DECL) {
            return keptEnums.contains(node.enumSymbol) ? node : null;
        }

        return node;
    }

    private List<SastNode> rebuildItemList(List<SastNode> items) {
        List<SastNode> out = null;

        for (int i = 0; i < items.size(); i++) {
            SastNode item = items.get(i);
            SastNode rebuilt = rebuildItem(item);

            if (out == null && rebuilt != item) {
                out = new ArrayList<>(items.subList(0, i));
            }

            if (out != null && rebuilt != null) {
                out.add(rebuilt);
            }
        }

        return out == null ? items : out;
    }

    private SastNode rebuildBlock(SastNode block) {
        List<SastNode> out = null;

        for (int i = 0; i < block.statements.size(); i++) {
            SastNode stmt = block.statements.get(i);
            SastNode rebuilt = rebuildStmt(stmt);

            if (out == null && rebuilt != stmt) {
                out = new ArrayList<>(block.statements.subList(0, i));
            }

            if (out != null && rebuilt != null) {
                out.add(rebuilt);
            }
        }

        return out == null ? block : SastNode.block(block.span, out);
    }

    private SastNode rebuildStmt(SastNode stmt) {

        if (stmt.kind == SastKind.VAR_DECL
                || stmt.kind == SastKind.VAR_DECL_INIT) {

            if (!keptVariables.contains(stmt.symbol)) {
                return null;
            }

            return stmt;
        }

        if (stmt.kind == SastKind.BLOCK) {
            SastNode rebuilt = rebuildBlock(stmt);

            return rebuilt == stmt ? stmt : rebuilt;
        }

        if (stmt.kind == SastKind.IF) {
            SastNode thenBranch = rebuildStmt(stmt.thenBranch);
            SastNode elseBranch =
                    stmt.elseBranch == null ? null : rebuildStmt(stmt.elseBranch);

            if (thenBranch == stmt.thenBranch && elseBranch == stmt.elseBranch) {
                return stmt;
            }

            return SastNode.ifStmt(
                    stmt.span,
                    stmt.condition,
                    thenBranch == null
                            ? SastNode.block(stmt.span, List.of())
                            : thenBranch,
                    elseBranch
            );
        }

        if (stmt.kind == SastKind.WHILE) {
            SastNode body = rebuildStmt(stmt.body);

            if (body == stmt.body) {
                return stmt;
            }

            return SastNode.whileStmt(
                    stmt.span,
                    stmt.condition,
                    body == null
                            ? SastNode.block(stmt.span, List.of())
                            : body,
                    stmt.update
            );
        }

        return stmt;
    }

    private static void collectCalls(SastNode stmt, Set<Symbol> out) {

        if (stmt.kind == SastKind.BLOCK) {
            for (SastNode inner : stmt.statements) {
                collectCalls(inner, out);
            }

            return;
        }

        if (stmt.kind == SastKind.VAR_DECL) {
            return;
        }

        if (stmt.kind == SastKind.VAR_DECL_INIT) {
            for (SastNode initializer : stmt.initializers) {
                collectCalls(initializer, out);
            }

            return;
        }

        if (stmt.kind == SastKind.ASSIGN) {
            collectExprCalls(stmt.target, out);
            collectExprCalls(stmt.value, out);

            return;
        }

        if (stmt.kind == SastKind.IF) {
            collectExprCalls(stmt.condition, out);
            collectCalls(stmt.thenBranch, out);

            if (stmt.elseBranch != null) {
                collectCalls(stmt.elseBranch, out);
            }

            return;
        }

        if (stmt.kind == SastKind.WHILE) {
            collectExprCalls(stmt.condition, out);
            collectCalls(stmt.body, out);

            for (SastNode update : stmt.update) {
                collectCalls(update, out);
            }

            return;
        }

        if (stmt.kind == SastKind.RETURN) {

            if (stmt.value != null) {
                collectExprCalls(stmt.value, out);
            }

            return;
        }

        if (stmt.kind == SastKind.EXPR_STMT) {
            collectExprCalls(stmt.expr, out);
        }
    }

    private static void collectExprCalls(SastNode expr, Set<Symbol> out) {

        if (expr.kind == SastKind.FUNCTION_CALL) {
            out.add(expr.function);

            for (SastNode arg : expr.args) {
                collectExprCalls(arg, out);
            }

            return;
        }

        if (expr.kind == SastKind.ARRAY_INTRINSIC_CALL) {
            collectExprCalls(expr.receiver, out);

            for (SastNode arg : expr.args) {
                collectExprCalls(arg, out);
            }

            return;
        }

        if (expr.kind == SastKind.UNARY_OP) {
            collectExprCalls(expr.expr, out);

            return;
        }

        if (expr.kind == SastKind.BIN_OP) {
            collectExprCalls(expr.left, out);
            collectExprCalls(expr.right, out);

            return;
        }

        if (expr.kind == SastKind.ARRAY_ACCESS) {
            collectExprCalls(expr.target, out);
            collectExprCalls(expr.index, out);

            return;
        }

        if (expr.kind == SastKind.MEMBER_ACCESS) {
            collectExprCalls(expr.target, out);

            return;
        }

        if (expr.kind == SastKind.GET_PTR) {
            collectExprCalls(expr.target, out);

            return;
        }

        if (expr.kind == SastKind.DEREF) {
            collectExprCalls(expr.expr, out);

            return;
        }

        if (expr.kind == SastKind.CAST) {
            collectExprCalls(expr.expr, out);
            return;
        }

        if (expr.kind == SastKind.AGGREGATE_LITERAL) {
            for (SastNode field : expr.fields) {
                collectExprCalls(field.value, out);
            }
        }
    }

    private static void collectVariables(SastNode stmt, Set<Symbol> out) {

        if (stmt.kind == SastKind.BLOCK) {
            for (SastNode inner : stmt.statements) {
                collectVariables(inner, out);
            }

            return;
        }

        if (stmt.kind == SastKind.VAR_DECL_INIT) {
            Set<Symbol> refs = newSet();
            boolean hasEffects = false;

            for (SastNode initializer : stmt.initializers) {
                hasEffects |= stmtHasSideEffects(initializer);
                collectVariables(initializer, refs);
            }

            refs.remove(stmt.symbol);

            if (hasEffects) {
                out.add(stmt.symbol);
            }

            out.addAll(refs);

            return;
        }

        if (stmt.kind == SastKind.ASSIGN) {
            collectExprVariables(stmt.target, out);
            collectExprVariables(stmt.value, out);

            return;
        }

        if (stmt.kind == SastKind.IF) {
            collectExprVariables(stmt.condition, out);
            collectVariables(stmt.thenBranch, out);

            if (stmt.elseBranch != null) {
                collectVariables(stmt.elseBranch, out);
            }

            return;
        }

        if (stmt.kind == SastKind.WHILE) {
            collectExprVariables(stmt.condition, out);
            collectVariables(stmt.body, out);

            for (SastNode update : stmt.update) {
                collectVariables(update, out);
            }

            return;
        }

        if (stmt.kind == SastKind.RETURN) {

            if (stmt.value != null) {
                collectExprVariables(stmt.value, out);
            }

            return;
        }

        if (stmt.kind == SastKind.EXPR_STMT) {
            collectExprVariables(stmt.expr, out);
        }
    }

    private static void collectExprVariables(SastNode expr, Set<Symbol> out) {

        if (expr.kind == SastKind.VAR_EXPR) {
            out.add(expr.symbol);

            return;
        }

        if (expr.kind == SastKind.ARRAY_INTRINSIC_CALL) {
            collectExprVariables(expr.receiver, out);

            for (SastNode arg : expr.args) {
                collectExprVariables(arg, out);
            }

            return;
        }

        if (expr.kind == SastKind.FUNCTION_CALL) {
            for (SastNode arg : expr.args) {
                collectExprVariables(arg, out);
            }

            return;
        }

        if (expr.kind == SastKind.UNARY_OP) {
            collectExprVariables(expr.expr, out);

            return;
        }

        if (expr.kind == SastKind.BIN_OP) {
            collectExprVariables(expr.left, out);
            collectExprVariables(expr.right, out);

            return;
        }

        if (expr.kind == SastKind.ARRAY_ACCESS) {
            collectExprVariables(expr.target, out);
            collectExprVariables(expr.index, out);

            return;
        }

        if (expr.kind == SastKind.MEMBER_ACCESS) {
            collectExprVariables(expr.target, out);

            return;
        }

        if (expr.kind == SastKind.GET_PTR) {
            collectExprVariables(expr.target, out);

            return;
        }

        if (expr.kind == SastKind.DEREF) {
            collectExprVariables(expr.expr, out);

            return;
        }

        if (expr.kind == SastKind.CAST) {
            collectExprVariables(expr.expr, out);
            return;
        }

        if (expr.kind == SastKind.AGGREGATE_LITERAL) {
            for (SastNode field : expr.fields) {
                collectExprVariables(field.value, out);
            }
        }
    }

    private void collectExprTypes(SastNode stmt) {

        if (stmt.kind == SastKind.BLOCK) {
            for (SastNode inner : stmt.statements) {
                collectExprTypes(inner);
            }

            return;
        }

        if (stmt.kind == SastKind.VAR_DECL) {

            if (keptVariables.contains(stmt.symbol)) {
                markType(stmt.symbol.type());
            }

            return;
        }

        if (stmt.kind == SastKind.VAR_DECL_INIT) {

            if (keptVariables.contains(stmt.symbol)) {
                markType(stmt.symbol.type());

                for (SastNode initializer : stmt.initializers) {
                    collectExprTypes(initializer);
                }
            }

            return;
        }

        if (stmt.kind == SastKind.ASSIGN) {
            markType(stmt.value.type);
            collectExprTypeExprs(stmt.target);
            collectExprTypeExprs(stmt.value);

            return;
        }

        if (stmt.kind == SastKind.IF) {
            markType(stmt.condition.type);
            collectExprTypeExprs(stmt.condition);
            collectExprTypes(stmt.thenBranch);

            if (stmt.elseBranch != null) {
                collectExprTypes(stmt.elseBranch);
            }

            return;
        }

        if (stmt.kind == SastKind.WHILE) {
            markType(stmt.condition.type);
            collectExprTypeExprs(stmt.condition);
            collectExprTypes(stmt.body);

            for (SastNode update : stmt.update) {
                collectExprTypes(update);
            }

            return;
        }

        if (stmt.kind == SastKind.RETURN) {

            if (stmt.value != null) {
                markType(stmt.value.type);
                collectExprTypeExprs(stmt.value);
            }

            return;
        }

        if (stmt.kind == SastKind.EXPR_STMT) {
            markType(stmt.expr.type);
            collectExprTypeExprs(stmt.expr);
        }
    }

    private void collectExprTypeExprs(SastNode expr) {
        markType(expr.type);

        if (expr.kind == SastKind.ARRAY_INTRINSIC_CALL) {
            collectExprTypeExprs(expr.receiver);

            for (SastNode arg : expr.args) {
                collectExprTypeExprs(arg);
            }

            return;
        }

        if (expr.kind == SastKind.FUNCTION_CALL) {

            for (SastNode arg : expr.args) {
                collectExprTypeExprs(arg);
            }

            return;
        }

        if (expr.kind == SastKind.UNARY_OP) {
            collectExprTypeExprs(expr.expr);

            return;
        }

        if (expr.kind == SastKind.BIN_OP) {
            collectExprTypeExprs(expr.left);
            collectExprTypeExprs(expr.right);

            return;
        }

        if (expr.kind == SastKind.ARRAY_ACCESS) {
            collectExprTypeExprs(expr.target);
            collectExprTypeExprs(expr.index);

            return;
        }

        if (expr.kind == SastKind.MEMBER_ACCESS) {
            collectExprTypeExprs(expr.target);

            return;
        }

        if (expr.kind == SastKind.GET_PTR) {
            collectExprTypeExprs(expr.target);

            return;
        }

        if (expr.kind == SastKind.DEREF) {
            collectExprTypeExprs(expr.expr);

            return;
        }

        if (expr.kind == SastKind.CAST) {
            collectExprTypeExprs(expr.expr);
            return;
        }

        if (expr.kind == SastKind.AGGREGATE_LITERAL) {
            markType(expr.type);

            for (SastNode field : expr.fields) {
                collectExprTypeExprs(field.value);
            }
        }
    }

    private static boolean stmtHasSideEffects(SastNode stmt) {
        if (stmt == null) {
            return false;
        }

        if (stmt.kind == SastKind.ASSIGN) {
            return exprHasSideEffects(stmt.target)
                    || exprHasSideEffects(stmt.value);
        }

        if (stmt.kind == SastKind.VAR_DECL_INIT) {
            for (SastNode initializer : stmt.initializers) {
                if (stmtHasSideEffects(initializer)) {
                    return true;
                }
            }

            return false;
        }

        return true;
    }

    private static boolean exprHasSideEffects(SastNode expr) {
        if (expr == null) {
            return false;
        }

        if (expr.kind == SastKind.INT_LITERAL
                || expr.kind == SastKind.NULL_LITERAL
                || expr.kind == SastKind.VAR_EXPR
                || expr.kind == SastKind.ENUM_VARIANT) {
            return false;
        }

        if (expr.kind == SastKind.UNARY_OP) {
            return exprHasSideEffects(expr.expr);
        }

        if (expr.kind == SastKind.BIN_OP) {
            return exprHasSideEffects(expr.left)
                    || exprHasSideEffects(expr.right);
        }

        if (expr.kind == SastKind.MEMBER_ACCESS) {
            return exprHasSideEffects(expr.target);
        }

        if (expr.kind == SastKind.GET_PTR) {
            return exprHasSideEffects(expr.target);
        }

        if (expr.kind == SastKind.CAST) {
            return expr.checked || exprHasSideEffects(expr.expr);
        }

        if (expr.kind == SastKind.AGGREGATE_LITERAL) {
            for (SastNode field : expr.fields) {
                if (exprHasSideEffects(field.value)) {
                    return true;
                }
            }

            return false;
        }

        if (expr.kind == SastKind.ARRAY_INTRINSIC_CALL) {
            if (expr.intrinsicKind != SemanticArrayIntrinsicKind.LENGTH) {
                return true;
            }

            if (exprHasSideEffects(expr.receiver)) {
                return true;
            }

            for (SastNode arg : expr.args) {
                if (exprHasSideEffects(arg)) {
                    return true;
                }
            }

            return false;
        }

        return true;
    }
}
