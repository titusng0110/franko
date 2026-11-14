import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class UselessGenericPruner {

    private final Set<Symbol> keptFunctions = newSet();
    private final Set<StructSymbol> keptStructs = newSet();

    private final IdentityHashMap<Symbol, SastNode> declBySymbol =
            new IdentityHashMap<>();
    private final List<SastNode> structDecls = new ArrayList<>();
    private final List<SastNode> globalVarDecls = new ArrayList<>();

    private final Deque<StructSymbol> structWorklist = new ArrayDeque<>();

    private boolean entryPointFound = false;

    private static <T> Set<T> newSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    SastDocument prune(SastDocument document) {
        return new SastDocument(
                SastPhase.USELESS_GENERIC_PRUNED,
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
        computeKeptStructs();

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
            structDecls.add(node);

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

    private void indexFunction(SastNode function) {
        declBySymbol.put(function.symbol, function);

        if (function.symbol.kind != SymbolKind.METHOD
                && function.symbol.name.equals("main")
                && function.symbol.namespace == null) {
            entryPointFound = true;
        }
    }

    private void computeKeptFunctions() {
        Deque<Symbol> worklist = new ArrayDeque<>();

        for (Symbol function : declBySymbol.keySet()) {
            if (!isGeneratedFunction(function)) {
                keptFunctions.add(function);
                worklist.add(function);
            }
        }

        while (!worklist.isEmpty()) {
            Symbol function = worklist.poll();
            SastNode node = declBySymbol.get(function);

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

    private void computeKeptStructs() {
        for (SastNode declaration : structDecls) {
            StructSymbol struct = declaration.structSymbol;

            if (struct != null && !isGeneratedStruct(struct)) {
                markStruct(struct);
            }
        }

        for (Symbol function : keptFunctions) {
            markType(function.type());

            if (function.kind == SymbolKind.METHOD) {
                markStruct(function.owner);
            }
        }

        for (SastNode global : globalVarDecls) {
            markType(global.symbol.type());

            if (global.staticInitializer != null) {
                collectExprTypeExprs(global.staticInitializer);
            }
        }

        for (Symbol function : keptFunctions) {
            SastNode node = declBySymbol.get(function);

            if (node != null) {
                collectExprTypes(node.body);
            }
        }

        while (!structWorklist.isEmpty()) {
            StructSymbol struct = structWorklist.poll();

            for (FieldSymbol field : struct.fields) {
                markType(field.type);
            }
        }
    }

    private void markType(SastType type) {
        if (type == null) {
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

        if (type.kind == SastTypeKind.PTR
                || type.kind == SastTypeKind.DYNAMIC_ARRAY
                || type.kind == SastTypeKind.STATIC_ARRAY) {
            markType(type.referencedType != null
                    ? type.referencedType
                    : type.elementType);
            return;
        }

        if (type.kind == SastTypeKind.FUNCTION) {
            for (SastType parameter : type.parameterTypes) {
                markType(parameter);
            }

            markType(type.returnType);
        }
    }

    private void markStruct(StructSymbol struct) {
        if (struct == null) {
            return;
        }

        if (keptStructs.add(struct)) {
            structWorklist.add(struct);
        }
    }

    private static boolean isGeneratedFunction(Symbol function) {
        if (function == null) {
            return false;
        }

        if (function.genericGroup != null) {
            return true;
        }

        return function.kind == SymbolKind.METHOD
                && function.owner != null
                && function.owner.genericHash != null;
    }

    private static boolean isGeneratedStruct(StructSymbol struct) {
        return struct != null && struct.genericHash != null;
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
            return isGeneratedFunction(node.symbol)
                    && !keptFunctions.contains(node.symbol)
                    ? null
                    : node;
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            if (isGeneratedStruct(node.structSymbol)
                    && !keptStructs.contains(node.structSymbol)) {
                return null;
            }

            List<SastNode> methods = null;

            for (int i = 0; i < node.methods.size(); i++) {
                SastNode method = node.methods.get(i);
                boolean keep = !isGeneratedFunction(method.symbol)
                        || keptFunctions.contains(method.symbol);

                if (methods == null && !keep) {
                    methods = new ArrayList<>(node.methods.subList(0, i));
                }

                if (methods != null && keep) {
                    methods.add(method);
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

    private static void collectCalls(SastNode stmt, Set<Symbol> out) {
        if (stmt == null) {
            return;
        }

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
        if (expr == null) {
            return;
        }

        if (expr.kind == SastKind.FUNCTION_CALL) {
            if (expr.function != null) {
                out.add(expr.function);
            }

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

    private void collectExprTypes(SastNode stmt) {
        if (stmt == null) {
            return;
        }

        if (stmt.kind == SastKind.BLOCK) {
            for (SastNode inner : stmt.statements) {
                collectExprTypes(inner);
            }

            return;
        }

        if (stmt.kind == SastKind.VAR_DECL) {
            markType(stmt.symbol.type());
            return;
        }

        if (stmt.kind == SastKind.VAR_DECL_INIT) {
            markType(stmt.symbol.type());

            for (SastNode initializer : stmt.initializers) {
                collectExprTypeExprs(initializer);
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
        if (expr == null) {
            return;
        }

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
}
