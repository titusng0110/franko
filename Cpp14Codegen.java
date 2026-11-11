import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class Cpp14Codegen {

    private final StringBuilder out = new StringBuilder();
    private int indentLevel = 0;

    private static final class LoopFrame {
        final int id;

        LoopFrame(int id) {
            this.id = id;
        }
    }

    private final List<LoopFrame> loopStack = new ArrayList<>();
    private int nextLoopId = 0;

    final FfiHandler ffiHandler;

    final boolean uncheckedStaticArrayAccess;

    final Map<String, FfiMatch> usedFfiMatches = new LinkedHashMap<>();

    private final Cpp14ExpressionEmitter expressionEmitter =
            new Cpp14ExpressionEmitter();

    public Cpp14Codegen() {
        this(null, false);
    }

    public Cpp14Codegen(
            FfiHandler ffiHandler,
            boolean uncheckedStaticArrayAccess
    ) {
        this.ffiHandler = ffiHandler;
        this.uncheckedStaticArrayAccess = uncheckedStaticArrayAccess;
    }

    private String currentEmitNamespace = null;

    static final SastType UINT32_TYPE =
            SastType.primitive(SemanticPrimitiveKind.UINT32);

    public String generate(SastNode root) {
        if (root == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot generate code for null Semantic AST"
            );
        }

        out.setLength(0);
        indentLevel = 0;
        usedFfiMatches.clear();

        emitNode(root);

        String body = out.toString();

        if (ffiHandler != null && !usedFfiMatches.isEmpty()) {
            return ffiHandler.buildBridge(
                    new ArrayList<>(usedFfiMatches.values())
            ) + body;
        }

        return body;
    }

    private void emitNode(SastNode node) {
        if (node.kind == SastKind.PROGRAM) {
            emitProgram(node);
            return;
        }

        if (node.kind == SastKind.NAMESPACE) {

            emitProgram(SastNode.program(
                    node.span,
                    List.of(node)
            ));
            return;
        }

        if (node.kind == SastKind.FUNCTION_DECL) {
            emitFunctionDecl(node);
            return;
        }

        if (node.kind == SastKind.STRUCT_DECL) {
            emitStructDecl(node);
            return;
        }

        if (node.kind == SastKind.ENUM_DECL) {
            emitEnumDecl(node);
            return;
        }

        if (node.isStmt()) {
            emitStmt(node);
            return;
        }

        if (node.isExpr()) {
            emitLine(expressionEmitter.emitExpr(this, node) + ";");
            return;
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported SastNode for codegen: "
                        + node.getClass().getSimpleName()
        );
    }

    private void emitProgram(SastNode node) {

        List<SastNode> globalStructs = new ArrayList<>();
        List<SastNode> globalEnums = new ArrayList<>();
        List<SastNode> globalGlobals = new ArrayList<>();
        List<SastNode> globalFunctions = new ArrayList<>();

        Map<String, List<SastNode>> nsStructs = new LinkedHashMap<>();
        Map<String, List<SastNode>> nsEnums = new LinkedHashMap<>();
        Map<String, List<SastNode>> nsGlobals = new LinkedHashMap<>();
        Map<String, List<SastNode>> nsFunctions = new LinkedHashMap<>();

        for (SastNode item : node.topLevelItems) {
            if (item.kind == SastKind.NAMESPACE) {
                partitionNamespaceItems(
                        item,
                        nsStructs,
                        nsEnums,
                        nsGlobals,
                        nsFunctions
                );
                continue;
            }

            partitionItem(item, globalStructs, globalEnums, globalGlobals, globalFunctions);
        }

        for (SastNode enumDecl : globalEnums) {
            emitEnumDecl(enumDecl);
        }

        if (!globalEnums.isEmpty()) {
            emitLine("");
        }

        for (Map.Entry<String, List<SastNode>> entry : nsEnums.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            openNamespace(entry.getKey());

            for (SastNode enumDecl : entry.getValue()) {
                emitEnumDeclBody(enumDecl);
            }

            closeNamespace(entry.getKey());
        }

        if (anyNonEmpty(nsEnums)) {
            emitLine("");
        }

        for (Map.Entry<String, List<SastNode>> entry : nsStructs.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            openNamespace(entry.getKey());

            for (SastNode struct : entry.getValue()) {
                emitLine("struct "
                        + qualify(
                                struct.structSymbol.namespace,
                                emittedStructTypeName(struct.structSymbol)
                        )
                        + ";");
            }

            closeNamespace(entry.getKey());
        }

        if (!globalStructs.isEmpty()) {
            for (SastNode struct : globalStructs) {
                emitLine("struct "
                        + qualify(
                                struct.structSymbol.namespace,
                                emittedStructTypeName(struct.structSymbol)
                        )
                        + ";");
            }
        }

        if (anyNonEmpty(nsStructs) || !globalStructs.isEmpty()) {
            emitLine("");
        }

        List<SastNode> allStructs = new ArrayList<>();

        for (List<SastNode> structs : nsStructs.values()) {
            allStructs.addAll(structs);
        }

        allStructs.addAll(globalStructs);

        if (!allStructs.isEmpty()) {
            for (SastNode struct : orderStructsByValueDependency(allStructs)) {
                emitStructDecl(struct);
            }

            emitLine("");
        }

        if (anyNonEmpty(nsGlobals) || !globalGlobals.isEmpty()) {
            for (Map.Entry<String, List<SastNode>> entry : nsGlobals.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }

                openNamespace(entry.getKey());

                for (SastNode global : entry.getValue()) {
                    emitGlobalForwardDeclaration(global);
                }

                closeNamespace(entry.getKey());
            }

            for (SastNode global : globalGlobals) {
                emitGlobalForwardDeclaration(global);
            }

            emitLine("");

            for (Map.Entry<String, List<SastNode>> entry : nsGlobals.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }

                openNamespace(entry.getKey());

                for (SastNode global : entry.getValue()) {
                    emitVarDecl(global);
                }

                closeNamespace(entry.getKey());
            }

            for (SastNode global : globalGlobals) {
                emitVarDecl(global);
            }

            emitLine("");
        }

        Map<String, List<SastNode>> nsMethods = collectNamespaceMethods(nsStructs);
        List<SastNode> globalMethods = collectGlobalMethods(globalStructs);

        checkEmittedNameCollisions(globalFunctions, globalMethods, nsFunctions, nsMethods);

        if (anyNonEmpty(nsFunctions)
                || anyNonEmpty(nsMethods)
                || !globalFunctions.isEmpty()
                || !globalMethods.isEmpty()) {
            for (Map.Entry<String, List<SastNode>> entry : nsFunctions.entrySet()) {
                List<SastNode> combined = new ArrayList<>(entry.getValue());
                combined.addAll(nsMethods.getOrDefault(entry.getKey(), List.of()));

                if (combined.isEmpty()) {
                    continue;
                }

                openNamespace(entry.getKey());

                for (SastNode fn : combined) {
                    emitLine(emitFunctionHeader(fn) + ";");
                }

                closeNamespace(entry.getKey());
            }

            for (Map.Entry<String, List<SastNode>> entry : nsMethods.entrySet()) {
                if (nsFunctions.containsKey(entry.getKey())) {
                    continue;
                }

                if (entry.getValue().isEmpty()) {
                    continue;
                }

                openNamespace(entry.getKey());

                for (SastNode fn : entry.getValue()) {
                    emitLine(emitFunctionHeader(fn) + ";");
                }

                closeNamespace(entry.getKey());
            }

            for (SastNode fn : globalFunctions) {
                emitLine(emitFunctionHeader(fn) + ";");
            }

            for (SastNode fn : globalMethods) {
                emitLine(emitFunctionHeader(fn) + ";");
            }

            emitLine("");
        }

        for (Map.Entry<String, List<SastNode>> entry : nsFunctions.entrySet()) {
            List<SastNode> combined = new ArrayList<>(entry.getValue());
            combined.addAll(nsMethods.getOrDefault(entry.getKey(), List.of()));

            if (combined.isEmpty()) {
                continue;
            }

            openNamespace(entry.getKey());

            for (SastNode fn : combined) {
                emitFunctionDecl(fn);
                emitLine("");
            }

            closeNamespace(entry.getKey());
        }

        for (Map.Entry<String, List<SastNode>> entry : nsMethods.entrySet()) {
            if (nsFunctions.containsKey(entry.getKey())) {
                continue;
            }

            if (entry.getValue().isEmpty()) {
                continue;
            }

            openNamespace(entry.getKey());

            for (SastNode fn : entry.getValue()) {
                emitFunctionDecl(fn);
                emitLine("");
            }

            closeNamespace(entry.getKey());
        }

        for (SastNode fn : globalFunctions) {
            emitFunctionDecl(fn);
            emitLine("");
        }

        for (SastNode fn : globalMethods) {
            emitFunctionDecl(fn);
            emitLine("");
        }
    }

    private static List<SastNode> collectGlobalMethods(
            List<SastNode> globalStructs
    ) {
        List<SastNode> out = new ArrayList<>();

        for (SastNode struct : globalStructs) {
            out.addAll(struct.methods);
        }

        return out;
    }

    private static Map<String, List<SastNode>> collectNamespaceMethods(
            Map<String, List<SastNode>> nsStructs
    ) {
        Map<String, List<SastNode>> out = new LinkedHashMap<>();

        for (Map.Entry<String, List<SastNode>> entry : nsStructs.entrySet()) {
            List<SastNode> methods = namespaceList(out, entry.getKey());

            for (SastNode struct : entry.getValue()) {
                methods.addAll(struct.methods);
            }
        }

        return out;
    }

    private static boolean anyNonEmpty(Map<String, ? extends List<?>> byNamespace) {
        for (List<?> items : byNamespace.values()) {
            if (!items.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static void checkEmittedNameCollisions(
            List<SastNode> globalFunctions,
            List<SastNode> globalMethods,
            Map<String, List<SastNode>> nsFunctions,
            Map<String, List<SastNode>> nsMethods
    ) {
        Map<String, String> seen = new LinkedHashMap<>();

        List<SastNode> all = new ArrayList<>();
        all.addAll(globalFunctions);
        all.addAll(globalMethods);

        for (List<SastNode> fns : nsFunctions.values()) {
            all.addAll(fns);
        }

        for (List<SastNode> fns : nsMethods.values()) {
            all.addAll(fns);
        }

        for (SastNode fn : all) {
            if (fn == null || fn.symbol == null) {
                continue;
            }

            String qualified = fullyQualifiedEmittedName(fn.symbol);
            String key = fn.symbol.functionType().signatureKey();
            String prev = seen.putIfAbsent(qualified, key);

            if (prev != null && !prev.equals(key)) {
                throw new IllegalStateException(
                        "Internal compiler error: emitted C++ name collision for '"
                                + qualified
                                + "' (distinct signatures hash to the same suffix)"
                );
            }
        }
    }

    private static String fullyQualifiedEmittedName(Symbol symbol) {
        String local;
        String namespace;

        if (symbol.kind == SymbolKind.METHOD) {
            local = symbol.emittedLocalName();
            namespace = symbol.owner.namespace;
        } else {
            local = symbol.emittedLocalName();
            namespace = symbol.namespace;
        }

        if (namespace == null) {
            return local;
        }

        return cppNamespacePrefix(namespace) + "::" + local;
    }

    private static List<SastNode> namespaceList(
            Map<String, List<SastNode>> map,
            String name
    ) {
        List<SastNode> list = map.get(name);

        if (list == null) {
            list = new ArrayList<>();
            map.put(name, list);
        }

        return list;
    }

    private void partitionNamespaceItems(
            SastNode namespace,
            Map<String, List<SastNode>> nsStructs,
            Map<String, List<SastNode>> nsEnums,
            Map<String, List<SastNode>> nsGlobals,
            Map<String, List<SastNode>> nsFunctions
    ) {
        List<SastNode> structs = namespaceList(nsStructs, namespace.name);
        List<SastNode> enums = namespaceList(nsEnums, namespace.name);
        List<SastNode> globals = namespaceList(nsGlobals, namespace.name);
        List<SastNode> functions = namespaceList(nsFunctions, namespace.name);

        for (SastNode item : namespace.items) {
            partitionItem(item, structs, enums, globals, functions);
        }
    }

    private void partitionItem(
            SastNode item,
            List<SastNode> structs,
            List<SastNode> enums,
            List<SastNode> globals,
            List<SastNode> functions
    ) {
        if (item == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null top-level item in SastNode"
            );
        }

        if (item.kind == SastKind.STRUCT_DECL) {
            structs.add(item);
            return;
        }

        if (item.kind == SastKind.ENUM_DECL) {
            enums.add(item);
            return;
        }

        if (item.kind == SastKind.VAR_DECL) {
            globals.add(item);
            return;
        }

        if (item.kind == SastKind.FUNCTION_DECL) {
            functions.add(item);
            return;
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported global top-level item for codegen: "
                        + item.getClass().getSimpleName()
        );
    }

    private void emitStructDecl(SastNode node) {
        if (node == null || node.structSymbol == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null struct declaration or symbol"
            );
        }

        openNamespace(node.structSymbol.namespace);

        emitLine("struct " + qualify(
                node.structSymbol.namespace,
                emittedStructTypeName(node.structSymbol)
        ));
        emitLine("{");
        indentLevel++;

        for (FieldSymbol field : node.structSymbol.fields) {
            emitStructField(field);
        }

        indentLevel--;
        emitLine("};");

        closeNamespace(node.structSymbol.namespace);
    }

    private void emitStructField(FieldSymbol field) {
        if (field == null || field.type == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null struct field or field type"
            );
        }

        StringBuilder sb = new StringBuilder();

        sb.append(emitDecl(field.type, field.emittedName()));

        if (field.foldedDefault != null) {
            sb.append(" = ");
            sb.append(expressionEmitter.emitBigIntegerLiteral(this, field.foldedDefault));
        } else if (field.type.kind == SastTypeKind.PTR) {

            sb.append(" = nullptr");
        }

        sb.append(";");

        emitLine(sb.toString());
    }

    private List<SastNode> orderStructsByValueDependency(
            List<SastNode> structs
    ) {

        Map<String, SastNode> declByName = new LinkedHashMap<>();

        for (SastNode struct : structs) {
            declByName.put(struct.structSymbol.qualifiedName(), struct);
        }

        LinkedHashSet<String> emitted = new LinkedHashSet<>();
        LinkedHashSet<String> inProgress = new LinkedHashSet<>();
        List<SastNode> ordered = new ArrayList<>();

        for (SastNode struct : structs) {
            visitStructDependency(
                    struct,
                    declByName,
                    emitted,
                    inProgress,
                    ordered
            );
        }

        return ordered;
    }

    private void visitStructDependency(
            SastNode node,
            Map<String, SastNode> declByName,
            LinkedHashSet<String> emitted,
            LinkedHashSet<String> inProgress,
            List<SastNode> ordered
    ) {
        String name = node.structSymbol.qualifiedName();

        if (emitted.contains(name)) {
            return;
        }

        if (!inProgress.add(name)) {
            throw new IllegalStateException(
                    "Internal compiler error: by-value struct cycle involving '"
                            + name
                            + "' reached codegen; DeclarationChecker should have rejected it"
            );
        }

        for (FieldSymbol field : node.structSymbol.fields) {
            for (String dependencyName : byValueStructDependencies(field.type)) {
                SastNode dependency =
                        declByName.get(dependencyName);

                if (dependency != null) {
                    visitStructDependency(
                            dependency,
                            declByName,
                            emitted,
                            inProgress,
                            ordered
                    );
                }
            }
        }

        inProgress.remove(name);
        emitted.add(name);
        ordered.add(node);
    }

    private List<String> byValueStructDependencies(SastType type) {
        List<String> names = new ArrayList<>();

        if (type.kind == SastTypeKind.STRUCT) {
            names.add(type.structSymbol.qualifiedName());
            return names;
        }

        if (type.kind == SastTypeKind.TUPLE) {
            names.add(type.cppSymbol.qualifiedName());
            return names;
        }

        if (type.kind == SastTypeKind.STATIC_ARRAY) {
            names.addAll(byValueStructDependencies(type.elementType));
        }

        return names;
    }

    private void emitFunctionDecl(SastNode node) {

        if (ffiHandler != null && ffiHandler.matchFunction(node.symbol) != null) {
            return;
        }

        emitLine(emitFunctionHeader(node));

        if (node.body == null) {
            throw new IllegalStateException(
                    "Internal compiler error: function '"
                            + node.symbol.name
                            + "' has null body"
            );
        }

        emitBlock(node.body);
    }

    private String emitFunctionHeader(SastNode node) {
        if (node == null || node.symbol == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null function declaration or symbol"
            );
        }

        StringBuilder sb = new StringBuilder();

        sb.append(emitType(node.symbol.returnType()));
        sb.append(" ");
        sb.append(emitFunctionName(node.symbol));
        sb.append("(");

        List<Symbol> params = node.symbol.parameters;

        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            Symbol param = params.get(i);
            sb.append(emitDecl(param.type, emitSymbolName(param)));
        }

        sb.append(")");

        return sb.toString();
    }

    private void emitStmt(SastNode node) {
        if (node == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot emit null statement"
            );
        }

        if (node.kind == SastKind.BLOCK) {
            emitBlock(node);
            return;
        }

        if (node.kind == SastKind.VAR_DECL) {
            emitVarDecl(node);
            return;
        }

        if (node.kind == SastKind.VAR_DECL_INIT) {
            emitVarDecl(node);

            for (SastNode initializer : node.initializers) {
                emitStmt(initializer);
            }

            return;
        }

        if (node.kind == SastKind.ASSIGN) {
            emitAssign(node);
            return;
        }

        if (node.kind == SastKind.IF) {
            emitIf(node);
            return;
        }

        if (node.kind == SastKind.WHILE) {
            emitWhile(node);
            return;
        }

        if (node.kind == SastKind.BREAK) {
            emitBreak(node);
            return;
        }

        if (node.kind == SastKind.CONTINUE) {
            emitContinue();
            return;
        }

        if (node.kind == SastKind.RETURN) {
            emitReturn(node);
            return;
        }

        if (node.kind == SastKind.EXPR_STMT) {
            String expr = expressionEmitter.emitExpr(this, node.expr);
            emitLine(expr + ";");
            return;
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported semantic statement for codegen: "
                        + node.getClass().getSimpleName()
        );
    }

    private void emitBlock(SastNode node) {
        emitLine("{");
        indentLevel++;

        for (SastNode stmt : node.statements) {
            emitStmt(stmt);
        }

        indentLevel--;
        emitLine("}");
    }

    private void emitGlobalForwardDeclaration(SastNode node) {
        Symbol sym = node.symbol;

        String qualifier = "";

        if (sym.kind == SymbolKind.GLOBAL
                && (sym.literalBytes != null
                    || sym.literalValues != null)) {
            qualifier = "const ";
        }

        emitLine("extern " + qualifier
                + emitDecl(sym.type, emitSymbolName(sym)) + ";");
    }

    private void emitVarDecl(SastNode node) {
        Symbol sym = node.symbol;

        String declaration = emitDecl(sym.type, emitSymbolName(sym));

        if (sym.kind == SymbolKind.GLOBAL
                && (sym.literalBytes != null
                    || sym.literalValues != null)) {
            String initializer;

            if (sym.literalBytes != null) {
                initializer =
                        emitByteArrayInitializer(sym.literalBytes);
            } else if (sym.type.kind == SastTypeKind.STATIC_ARRAY) {
                initializer = emitStaticArrayLiteralInitializer(
                        sym.type,
                        sym.literalValues);
            } else {
                throw new IllegalStateException(
                        "Internal compiler error: array literal global '"
                                + sym.qualifiedName()
                                + "' does not have a static array type");
            }

            emitLine("const "
                    + declaration
                    + " = "
                    + initializer
                    + ";");
            return;
        }

        String initializer = "";

        if (node.foldedConstInitializer != null) {
            initializer = " = "
                    + expressionEmitter.emitBigIntegerLiteral(this, node.foldedConstInitializer);
        }

        if (node.staticInitializer != null) {
            initializer = " = " + expressionEmitter.emitExpr(this, node.staticInitializer);
        }

        if (initializer.isEmpty()
                && sym.type.kind == SastTypeKind.PTR) {
            initializer = " = nullptr";
        }

        emitLine(declaration + initializer + ";");
    }

    private String emitStaticArrayLiteralInitializer(
            SastType type,
            List<BigInteger> values
    ) {
        List<BigInteger> safeValues =
                values == null ? List.of() : values;

        int[] cursor = new int[]{0};

        return emitStaticArrayLiteralValue(type, safeValues, cursor);
    }

    private String emitStaticArrayLiteralValue(
            SastType type,
            List<BigInteger> values,
            int[] cursor
    ) {
        int size = parseStaticArraySize(type.sizeLiteral);

        StringBuilder sb = new StringBuilder();

        sb.append("{");

        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }

            if (type.elementType.kind == SastTypeKind.STATIC_ARRAY) {
                sb.append(emitStaticArrayLiteralValue(
                        type.elementType,
                        values,
                        cursor));
                continue;
            }

            BigInteger value = cursor[0] < values.size()
                    ? values.get(cursor[0]++)
                    : BigInteger.ZERO;

            if (type.elementType.kind == SastTypeKind.ENUM) {
                EnumVariant variant = type.elementType.enumSymbol.variantWithValue(value);

                if (variant == null && !type.elementType.enumSymbol.variants.isEmpty()) {
                    variant = type.elementType.enumSymbol.variants.get(0);
                }

                if (variant != null) {
                    sb.append(qualify(
                            type.elementType.enumSymbol.namespace,
                            emittedEnumTypeName(type.elementType.enumSymbol) + "::VARIANT_" + variant.name + "()"));
                } else {
                    sb.append(expressionEmitter.emitBigIntegerLiteral(this, value));
                }

                continue;
            }

            sb.append(expressionEmitter.emitBigIntegerLiteral(this, value));
        }

        sb.append("}");

        return sb.toString();
    }

    private static int parseStaticArraySize(String sizeLiteral) {
        String text = sizeLiteral == null ? "" : sizeLiteral.trim();

        try {
            if (text.startsWith("+")) {
                text = text.substring(1);
            }

            String lower = text.toLowerCase();

            if (lower.startsWith("0b")) {
                return new BigInteger(text.substring(2), 2).intValueExact();
            }

            if (lower.startsWith("0x")) {
                return new BigInteger(text.substring(2), 16).intValueExact();
            }

            return new BigInteger(text, 10).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalStateException(
                    "Internal compiler error: invalid static array size "
                            + "literal for literal emission: "
                            + sizeLiteral);
        }
    }

    private String emitByteArrayInitializer(List<BigInteger> bytes) {
        StringBuilder sb = new StringBuilder();

        sb.append("{");

        for (int i = 0; i < bytes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(bytes.get(i).toString(10));
        }

        sb.append("}");

        return sb.toString();
    }

    private void emitAssign(SastNode node) {

        String target = expressionEmitter.emitLValue(this, node.target);
        String value = expressionEmitter.emitAsType(this, node.value, node.target.type);

        emitLine(
                target
                        + " "
                        + node.op
                        + " "
                        + value
                        + ";"
        );
    }

    private void emitIf(SastNode node) {

        String condition = expressionEmitter.emitExpr(this, node.condition);
        emitLine("if (" + condition + ")");
        emitControlledStmt(node.thenBranch);

        if (node.elseBranch != null) {
            emitLine("else");
            emitControlledStmt(node.elseBranch);
        }
    }

    private void emitWhile(SastNode node) {

        boolean needsContinueLabel =
                scanForContinueTarget(node.body, 0);
        boolean needsBreakLabel =
                scanForBreakTarget(node.body, 0);

        LoopFrame frame = new LoopFrame(nextLoopId++);
        loopStack.add(frame);

        String condition = expressionEmitter.emitExpr(this, node.condition);

        emitLine("while (" + condition + ")");

        boolean hasUpdate = !node.update.isEmpty();

        if (needsContinueLabel) {

            emitLine("{");
            indentLevel++;

            emitLine("{");
            indentLevel++;
            emitStmt(node.body);
            indentLevel--;
            emitLine("}");

            emitLine("L_cont_" + frame.id + ": ;");

            for (SastNode updateStmt : node.update) {
                emitStmt(updateStmt);
            }

            indentLevel--;
            emitLine("}");
        } else if (hasUpdate) {

            emitLine("{");
            indentLevel++;

            if (node.body.kind == SastKind.BLOCK) {
                for (SastNode stmt : node.body.statements) {
                    emitStmt(stmt);
                }
            } else {
                emitStmt(node.body);
            }

            for (SastNode updateStmt : node.update) {
                emitStmt(updateStmt);
            }

            indentLevel--;
            emitLine("}");
        } else {
            emitControlledStmt(node.body);
        }

        loopStack.remove(loopStack.size() - 1);

        if (needsBreakLabel) {
            emitLine("L_brk_" + frame.id + ": ;");
        }
    }

    private void emitBreak(SastNode node) {
        if (loopStack.isEmpty() || node.levels > loopStack.size()) {
            throw new IllegalStateException(
                    "Internal compiler error: break with no enclosing loop"
            );
        }

        int targetIndex = loopStack.size() - node.levels;
        LoopFrame target = loopStack.get(targetIndex);

        emitLine("goto L_brk_" + target.id + ";");
    }

    private void emitContinue() {
        if (loopStack.isEmpty()) {
            throw new IllegalStateException(
                    "Internal compiler error: continue with no enclosing loop"
            );
        }

        LoopFrame target = loopStack.get(loopStack.size() - 1);

        emitLine("goto L_cont_" + target.id + ";");
    }

    private boolean scanForContinueTarget(SastNode node, int depth) {
        if (node == null) {
            return false;
        }

        if (node.kind == SastKind.CONTINUE) {
            return depth == 0;
        }

        if (node.kind == SastKind.BREAK) {
            return false;
        }

        if (node.kind == SastKind.WHILE) {
            if (scanForContinueTarget(node.body, depth + 1)) {
                return true;
            }

            for (SastNode updateStmt : node.update) {
                if (scanForContinueTarget(updateStmt, depth + 1)) {
                    return true;
                }
            }

            return false;
        }

        if (node.kind == SastKind.BLOCK) {
            for (SastNode stmt : node.statements) {
                if (scanForContinueTarget(stmt, depth)) {
                    return true;
                }
            }

            return false;
        }

        if (node.kind == SastKind.IF) {
            return scanForContinueTarget(node.thenBranch, depth)
                    || scanForContinueTarget(node.elseBranch, depth);
        }

        return false;
    }

    private boolean scanForBreakTarget(SastNode node, int depth) {
        if (node == null) {
            return false;
        }

        if (node.kind == SastKind.BREAK) {
            return node.levels == depth + 1;
        }

        if (node.kind == SastKind.CONTINUE) {
            return false;
        }

        if (node.kind == SastKind.WHILE) {
            if (scanForBreakTarget(node.body, depth + 1)) {
                return true;
            }

            for (SastNode updateStmt : node.update) {
                if (scanForBreakTarget(updateStmt, depth + 1)) {
                    return true;
                }
            }

            return false;
        }

        if (node.kind == SastKind.BLOCK) {
            for (SastNode stmt : node.statements) {
                if (scanForBreakTarget(stmt, depth)) {
                    return true;
                }
            }

            return false;
        }

        if (node.kind == SastKind.IF) {
            return scanForBreakTarget(node.thenBranch, depth)
                    || scanForBreakTarget(node.elseBranch, depth);
        }

        return false;
    }

    private void emitControlledStmt(SastNode node) {
        if (node.kind == SastKind.BLOCK) {
            emitStmt(node);
            return;
        }

        indentLevel++;
        emitStmt(node);
        indentLevel--;
    }

    private void emitReturn(SastNode node) {
        if (node.value == null) {
            emitLine("return;");
            return;
        }

        SastType targetType = node.function != null
                ? node.function.returnType()
                : node.value.type;

        String value = expressionEmitter.emitAsType(this, node.value, targetType);

        emitLine("return " + value + ";");
    }

    String emitType(SastType type) {
        if (type.kind == SastTypeKind.VOID) {
            return "void";
        }

        if (type.kind == SastTypeKind.PRIMITIVE) {
            return emitPrimitiveType(type.primitive);
        }

        if (type.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return "Franko_Dynamic_Array<"
                    + emitType(type.elementType)
                    + ">";
        }

        if (type.kind == SastTypeKind.STATIC_ARRAY) {
            StringBuilder dimensions = new StringBuilder();
            SastType elementType = type;

            while (elementType.kind == SastTypeKind.STATIC_ARRAY) {
                dimensions.append("[").append(elementType.sizeLiteral).append("]");
                elementType = elementType.elementType;
            }

            return emitType(elementType) + dimensions;
        }

        if (type.kind == SastTypeKind.PTR) {
            if (type.referencedType.kind == SastTypeKind.STATIC_ARRAY
                    || type.referencedType.kind == SastTypeKind.DYNAMIC_ARRAY) {
                return "Franko_Fat_Pointer<"
                        + emitType(pointerElementType(type.referencedType))
                        + ">";
            }

            return "Franko_Thin_Pointer<"
                    + emitType(type.referencedType)
                    + ">";
        }

        if (type.kind == SastTypeKind.STRUCT) {

            return qualify(
                    type.structSymbol.namespace,
                    emittedStructTypeName(type.structSymbol)
            );
        }

        if (type.kind == SastTypeKind.TUPLE) {
            return qualify(
                    type.cppSymbol.namespace,
                    emittedStructTypeName(type.cppSymbol)
            );
        }

        if (type.kind == SastTypeKind.ENUM) {
            return qualify(
                    type.enumSymbol.namespace,
                    emittedEnumTypeName(type.enumSymbol)
            );
        }

        if (type.kind == SastTypeKind.NULL) {

            throw new IllegalStateException(
                    "Internal compiler error: the null type has no C++ representation"
            );
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported semantic type for codegen: "
                        + type.getClass().getSimpleName()
        );
    }

    private String emitDecl(SastType type, String name) {
        if (type.kind == SastTypeKind.STATIC_ARRAY) {
            StringBuilder dimensions = new StringBuilder();
            SastType elementType = type;

            while (elementType.kind == SastTypeKind.STATIC_ARRAY) {
                dimensions.append("[").append(elementType.sizeLiteral).append("]");
                elementType = elementType.elementType;
            }

            return emitType(elementType) + " " + name + dimensions;
        }

        return emitType(type) + " " + name;
    }

    private static SastType pointerElementType(SastType referencedType) {
        if (referencedType.kind == SastTypeKind.STATIC_ARRAY) {
            return referencedType.elementType;
        }

        if (referencedType.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return referencedType.elementType;
        }

        return referencedType;
    }

    private String emitPrimitiveType(SemanticPrimitiveKind kind) {
        switch (kind) {
            case INT8: return "int8_t";
            case INT16: return "int16_t";
            case INT32: return "int32_t";
            case INT64: return "int64_t";

            case UINT8: return "uint8_t";
            case UINT16: return "uint16_t";
            case UINT32: return "uint32_t";
            case UINT64: return "uint64_t";
            default: throw new IllegalStateException("Unknown primitive kind: " + kind);
        }
    }

    String emitSymbolName(Symbol symbol) {

        if (symbol == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null symbol in codegen"
            );
        }

        if (symbol.kind == SymbolKind.PARAMETER) {
            return "PARAM_" + symbol.name;
        }

        if (symbol.kind == SymbolKind.GLOBAL) {
            if (symbol.literalBytes != null || symbol.literalValues != null) {
                return qualify(symbol.namespace, symbol.name);
            }

            return qualify(symbol.namespace, "VAR_" + symbol.name);
        }

        return "VAR_" + symbol.name;
    }

    String emitFunctionName(Symbol symbol) {

        if (symbol.kind == SymbolKind.METHOD) {
            return qualify(symbol.owner.namespace, symbol.emittedLocalName());
        }

        return qualify(symbol.namespace, symbol.emittedLocalName());
    }

    private static String emittedStructTypeName(StructSymbol symbol) {
        return symbol.isTuple ? symbol.name : "STRUCT_" + symbol.name;
    }

    static String emittedEnumTypeName(EnumSymbol symbol) {
        return "ENUM_" + symbol.name;
    }

    String emitBackingLiteral(BigInteger value, SemanticPrimitiveKind backingKind) {
        if (backingKind == SemanticPrimitiveKind.INT64
                && value.equals(BigInteger.valueOf(Long.MIN_VALUE))) {
            return "(-9223372036854775807LL - 1)";
        }

        return expressionEmitter.emitBigIntegerLiteral(this, value);
    }

    private void emitEnumDecl(SastNode node) {
        openNamespace(node.enumSymbol.namespace);
        emitEnumDeclBody(node);
        closeNamespace(node.enumSymbol.namespace);
    }

    private void emitEnumDeclBody(SastNode node) {
        EnumSymbol symbol = node.enumSymbol;
        String backingCpp = emitPrimitiveType(symbol.backingKind);
        String enumCpp = emittedEnumTypeName(symbol);

        String firstValue = symbol.variants.isEmpty()
                ? "0"
                : emitBackingLiteral(symbol.variants.get(0).value, symbol.backingKind);

        emitLine("struct " + enumCpp);
        emitLine("{");
        indentLevel++;
        emitLine(backingCpp + " value;");
        emitLine("");
        emitLine("constexpr " + enumCpp + "() : value(static_cast<" + backingCpp + ">(" + firstValue + ")) {}");
        emitLine("constexpr explicit " + enumCpp + "(" + backingCpp + " v) : value(v) {}");
        emitLine("");

        for (EnumVariant variant : symbol.variants) {
            String literal = emitBackingLiteral(variant.value, symbol.backingKind);
            emitLine("static constexpr " + enumCpp + " VARIANT_" + variant.name + "()");
            emitLine("{");
            indentLevel++;
            emitLine("return " + enumCpp + "(static_cast<" + backingCpp + ">(" + literal + "));");
            indentLevel--;
            emitLine("}");
            emitLine("");
        }

        emitLine("template <typename InputT>");
        emitLine("static " + enumCpp + " from_integer_checked(InputT v)");
        emitLine("{");
        indentLevel++;
        emitLine(backingCpp + " backing = franko_integer_unsafe_cast<" + backingCpp + ">(v);");
        emitLine("");
        emitLine("switch (backing)");
        emitLine("{");
        indentLevel++;

        for (EnumVariant variant : symbol.variants) {
            String literal = emitBackingLiteral(variant.value, symbol.backingKind);
            emitLine("case static_cast<" + backingCpp + ">(" + literal + "): return VARIANT_" + variant.name + "();");
        }

        indentLevel--;
        emitLine("default:");
        indentLevel++;
        StringBuilder validList = new StringBuilder();

        for (int i = 0; i < symbol.variants.size(); i++) {
            if (i > 0) {
                validList.append(", ");
            }

            validList.append(symbol.variants.get(i).name).append("=").append(symbol.variants.get(i).value.toString(10));
        }

        boolean unsignedBacking = symbol.backingKind.name().startsWith("UINT");
        String formatSpec = unsignedBacking ? "%llu" : "%lld";
        String valueCast = unsignedBacking
                ? "static_cast<unsigned long long>(backing)"
                : "static_cast<long long>(backing)";

        emitLine("char message[256];");
        emitLine("std::snprintf(message, sizeof(message), \"Invalid value for enum "
                + symbol.qualifiedName() + ": " + formatSpec
                + " is not a declared variant (valid variants: " + validList
                + ")\", " + valueCast + ");");
        emitLine("franko_panic(message);");
        indentLevel--;
        emitLine("}");
        indentLevel--;
        emitLine("}");
        emitLine("");
        emitLine("constexpr bool operator==(" + enumCpp + " other) const { return value == other.value; }");
        emitLine("constexpr bool operator!=(" + enumCpp + " other) const { return value != other.value; }");
        emitLine("constexpr bool operator< (" + enumCpp + " other) const { return value <  other.value; }");
        emitLine("constexpr bool operator> (" + enumCpp + " other) const { return value >  other.value; }");
        emitLine("constexpr bool operator<=(" + enumCpp + " other) const { return value <= other.value; }");
        emitLine("constexpr bool operator>=(" + enumCpp + " other) const { return value >= other.value; }");
        indentLevel--;
        emitLine("};");
    }

    String emitEnumVariant(SastNode node) {
        return qualify(
                node.enumSymbol.namespace,
                emittedEnumTypeName(node.enumSymbol) + "::VARIANT_" + node.variant.name + "()"
        );
    }

    private String qualify(String symbolNamespace, String localName) {
        if (symbolNamespace == null) {
            return currentEmitNamespace == null
                    ? localName
                    : "::" + localName;
        }

        if (symbolNamespace.equals(currentEmitNamespace)) {
            return localName;
        }

        return cppNamespacePrefix(symbolNamespace) + "::" + localName;
    }

    private static String cppNamespacePrefix(String namespacePath) {
        StringBuilder sb = new StringBuilder();

        for (String segment : SymbolTable.splitNamespacePath(namespacePath)) {
            if (sb.length() > 0) {
                sb.append("::");
            }

            sb.append("NS_").append(segment);
        }

        return sb.toString();
    }

    private void openNamespace(String namespacePath) {
        if (namespacePath == null) {
            return;
        }

        for (String segment : SymbolTable.splitNamespacePath(namespacePath)) {
            emitLine("namespace NS_" + segment);
            emitLine("{");
            indentLevel++;
        }

        currentEmitNamespace = namespacePath;
    }

    private void closeNamespace(String namespacePath) {
        if (namespacePath == null) {
            return;
        }

        int depth = SymbolTable.splitNamespacePath(namespacePath).size();

        for (int i = 0; i < depth; i++) {
            indentLevel--;
            emitLine("}");
        }

        currentEmitNamespace = null;
    }

    private void emitLine(String s) {
        for (int i = 0; i < indentLevel; i++) {
            out.append("    ");
        }

        out.append(s).append('\n');
    }

}
