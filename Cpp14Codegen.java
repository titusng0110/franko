import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final List<String> pendingPrelude = new ArrayList<>();
    private int nextDecayTempId = 0;

    private final String osFfiBridgeText;

    private final TargetInfo target;

    private final Set<OsFfi.Kind> usedOsFfiKinds = new LinkedHashSet<>();

    public Cpp14Codegen() {
        this(null);
    }

    public Cpp14Codegen(String osFfiBridgeText) {
        this(osFfiBridgeText, null);
    }

    public Cpp14Codegen(String osFfiBridgeText, TargetInfo target) {
        this.osFfiBridgeText = osFfiBridgeText;
        this.target = target;
    }

    private String currentEmitNamespace = null;

    private static final SemanticType UINT8_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8);

    private static final SemanticType UINT32_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.UINT32);

    public String generate(SemanticASTNode root) {
        if (root == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot generate code for null Semantic AST"
            );
        }

        out.setLength(0);
        indentLevel = 0;
        usedOsFfiKinds.clear();
        pendingPrelude.clear();
        nextDecayTempId = 0;

        emitNode(root);

        String body = out.toString();

        if (osFfiBridgeText != null && !usedOsFfiKinds.isEmpty()) {
            return OsFfi.buildBridge(osFfiBridgeText, usedOsFfiKinds) + body;
        }

        return body;
    }

    private void emitNode(SemanticASTNode node) {
        if (node instanceof SemanticProgramNode program) {
            emitProgram(program);
            return;
        }

        if (node instanceof SemanticNamespaceNode namespace) {

            emitProgram(new SemanticProgramNode(List.of(namespace)));
            return;
        }

        if (node instanceof SemanticFunctionDeclNode fn) {
            emitFunctionDecl(fn);
            return;
        }

        if (node instanceof SemanticStructDeclNode struct) {
            emitStructDecl(struct);
            return;
        }

        if (node instanceof SemanticStmtNode stmt) {
            emitStmt(stmt);
            return;
        }

        if (node instanceof SemanticExprNode expr) {
            emitLine(emitExpr(expr) + ";");
            return;
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported SemanticASTNode for codegen: "
                        + node.getClass().getSimpleName()
        );
    }

    private void emitProgram(SemanticProgramNode node) {

        List<SemanticStructDeclNode> globalStructs = new ArrayList<>();
        List<SemanticVarDeclNode> globalGlobals = new ArrayList<>();
        List<SemanticFunctionDeclNode> globalFunctions = new ArrayList<>();

        Map<String, List<SemanticStructDeclNode>> nsStructs = new LinkedHashMap<>();
        Map<String, List<SemanticVarDeclNode>> nsGlobals = new LinkedHashMap<>();
        Map<String, List<SemanticFunctionDeclNode>> nsFunctions = new LinkedHashMap<>();

        for (SemanticASTNode item : node.topLevelItems) {
            if (item instanceof SemanticNamespaceNode namespace) {
                partitionNamespaceItems(
                        namespace,
                        nsStructs,
                        nsGlobals,
                        nsFunctions
                );
                continue;
            }

            partitionItem(item, globalStructs, globalGlobals, globalFunctions);
        }

        for (Map.Entry<String, List<SemanticStructDeclNode>> entry : nsStructs.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            openNamespace(entry.getKey());

            for (SemanticStructDeclNode struct : entry.getValue()) {
                emitLine("struct " + emitStructName(struct.symbol) + ";");
            }

            closeNamespace(entry.getKey());
        }

        if (!globalStructs.isEmpty()) {
            for (SemanticStructDeclNode struct : globalStructs) {
                emitLine("struct " + emitStructName(struct.symbol) + ";");
            }
        }

        if (anyNonEmpty(nsStructs) || !globalStructs.isEmpty()) {
            emitLine("");
        }

        List<SemanticStructDeclNode> allStructs = new ArrayList<>();

        for (List<SemanticStructDeclNode> structs : nsStructs.values()) {
            allStructs.addAll(structs);
        }

        allStructs.addAll(globalStructs);

        if (!allStructs.isEmpty()) {
            for (SemanticStructDeclNode struct : orderStructsByValueDependency(allStructs)) {
                emitStructDecl(struct);
            }

            emitLine("");
        }

        if (anyNonEmpty(nsGlobals) || !globalGlobals.isEmpty()) {
            for (Map.Entry<String, List<SemanticVarDeclNode>> entry : nsGlobals.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }

                openNamespace(entry.getKey());

                for (SemanticVarDeclNode global : entry.getValue()) {
                    emitGlobalForwardDeclaration(global);
                }

                closeNamespace(entry.getKey());
            }

            for (SemanticVarDeclNode global : globalGlobals) {
                emitGlobalForwardDeclaration(global);
            }

            emitLine("");

            for (Map.Entry<String, List<SemanticVarDeclNode>> entry : nsGlobals.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }

                openNamespace(entry.getKey());

                for (SemanticVarDeclNode global : entry.getValue()) {
                    emitVarDecl(global);
                }

                closeNamespace(entry.getKey());
            }

            for (SemanticVarDeclNode global : globalGlobals) {
                emitVarDecl(global);
            }

            emitLine("");
        }

        Map<String, List<SemanticFunctionDeclNode>> nsMethods = collectNamespaceMethods(nsStructs);
        List<SemanticFunctionDeclNode> globalMethods = collectGlobalMethods(globalStructs);

        checkEmittedNameCollisions(globalFunctions, globalMethods, nsFunctions, nsMethods);

        if (anyNonEmpty(nsFunctions)
                || anyNonEmpty(nsMethods)
                || !globalFunctions.isEmpty()
                || !globalMethods.isEmpty()) {
            for (Map.Entry<String, List<SemanticFunctionDeclNode>> entry : nsFunctions.entrySet()) {
                List<SemanticFunctionDeclNode> combined = new ArrayList<>(entry.getValue());
                combined.addAll(nsMethods.getOrDefault(entry.getKey(), List.of()));

                if (combined.isEmpty()) {
                    continue;
                }

                openNamespace(entry.getKey());

                for (SemanticFunctionDeclNode fn : combined) {
                    emitFunctionPrototype(fn);
                }

                closeNamespace(entry.getKey());
            }

            for (Map.Entry<String, List<SemanticFunctionDeclNode>> entry : nsMethods.entrySet()) {
                if (nsFunctions.containsKey(entry.getKey())) {
                    continue;
                }

                if (entry.getValue().isEmpty()) {
                    continue;
                }

                openNamespace(entry.getKey());

                for (SemanticFunctionDeclNode fn : entry.getValue()) {
                    emitFunctionPrototype(fn);
                }

                closeNamespace(entry.getKey());
            }

            for (SemanticFunctionDeclNode fn : globalFunctions) {
                emitFunctionPrototype(fn);
            }

            for (SemanticFunctionDeclNode fn : globalMethods) {
                emitFunctionPrototype(fn);
            }

            emitLine("");
        }

        for (Map.Entry<String, List<SemanticFunctionDeclNode>> entry : nsFunctions.entrySet()) {
            List<SemanticFunctionDeclNode> combined = new ArrayList<>(entry.getValue());
            combined.addAll(nsMethods.getOrDefault(entry.getKey(), List.of()));

            if (combined.isEmpty()) {
                continue;
            }

            openNamespace(entry.getKey());

            for (SemanticFunctionDeclNode fn : combined) {
                emitFunctionDecl(fn);
                emitLine("");
            }

            closeNamespace(entry.getKey());
        }

        for (Map.Entry<String, List<SemanticFunctionDeclNode>> entry : nsMethods.entrySet()) {
            if (nsFunctions.containsKey(entry.getKey())) {
                continue;
            }

            if (entry.getValue().isEmpty()) {
                continue;
            }

            openNamespace(entry.getKey());

            for (SemanticFunctionDeclNode fn : entry.getValue()) {
                emitFunctionDecl(fn);
                emitLine("");
            }

            closeNamespace(entry.getKey());
        }

        for (SemanticFunctionDeclNode fn : globalFunctions) {
            emitFunctionDecl(fn);
            emitLine("");
        }

        for (SemanticFunctionDeclNode fn : globalMethods) {
            emitFunctionDecl(fn);
            emitLine("");
        }
    }

    private static List<SemanticFunctionDeclNode> collectGlobalMethods(
            List<SemanticStructDeclNode> globalStructs
    ) {
        List<SemanticFunctionDeclNode> out = new ArrayList<>();

        for (SemanticStructDeclNode struct : globalStructs) {
            out.addAll(struct.methods);
        }

        return out;
    }

    private static Map<String, List<SemanticFunctionDeclNode>> collectNamespaceMethods(
            Map<String, List<SemanticStructDeclNode>> nsStructs
    ) {
        Map<String, List<SemanticFunctionDeclNode>> out = new LinkedHashMap<>();

        for (Map.Entry<String, List<SemanticStructDeclNode>> entry : nsStructs.entrySet()) {
            List<SemanticFunctionDeclNode> methods = out.computeIfAbsent(
                    entry.getKey(),
                    ignored -> new ArrayList<>()
            );

            for (SemanticStructDeclNode struct : entry.getValue()) {
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
            List<SemanticFunctionDeclNode> globalFunctions,
            List<SemanticFunctionDeclNode> globalMethods,
            Map<String, List<SemanticFunctionDeclNode>> nsFunctions,
            Map<String, List<SemanticFunctionDeclNode>> nsMethods
    ) {
        Map<String, String> seen = new LinkedHashMap<>();

        List<SemanticFunctionDeclNode> all = new ArrayList<>();
        all.addAll(globalFunctions);
        all.addAll(globalMethods);

        for (List<SemanticFunctionDeclNode> fns : nsFunctions.values()) {
            all.addAll(fns);
        }

        for (List<SemanticFunctionDeclNode> fns : nsMethods.values()) {
            all.addAll(fns);
        }

        for (SemanticFunctionDeclNode fn : all) {
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

    private static String fullyQualifiedEmittedName(FunctionSymbol symbol) {
        String local;
        String namespace;

        if (symbol instanceof MethodSymbol method) {
            local = method.emittedLocalName();
            namespace = method.owner.namespace;
        } else {
            local = symbol.emittedLocalName();
            namespace = symbol.namespace;
        }

        if (namespace == null) {
            return local;
        }

        return cppNamespacePrefix(namespace) + "::" + local;
    }

    private void partitionNamespaceItems(
            SemanticNamespaceNode namespace,
            Map<String, List<SemanticStructDeclNode>> nsStructs,
            Map<String, List<SemanticVarDeclNode>> nsGlobals,
            Map<String, List<SemanticFunctionDeclNode>> nsFunctions
    ) {
        List<SemanticStructDeclNode> structs = nsStructs.computeIfAbsent(
                namespace.name,
                ignored -> new ArrayList<>()
        );

        List<SemanticVarDeclNode> globals = nsGlobals.computeIfAbsent(
                namespace.name,
                ignored -> new ArrayList<>()
        );

        List<SemanticFunctionDeclNode> functions = nsFunctions.computeIfAbsent(
                namespace.name,
                ignored -> new ArrayList<>()
        );

        for (SemanticASTNode item : namespace.items) {
            partitionItem(item, structs, globals, functions);
        }
    }

    private void partitionItem(
            SemanticASTNode item,
            List<SemanticStructDeclNode> structs,
            List<SemanticVarDeclNode> globals,
            List<SemanticFunctionDeclNode> functions
    ) {
        if (item instanceof SemanticStructDeclNode struct) {
            structs.add(struct);
            return;
        }

        if (item instanceof SemanticVarDeclNode global) {
            globals.add(global);
            return;
        }

        if (item instanceof SemanticFunctionDeclNode fn) {
            functions.add(fn);
            return;
        }

        if (item == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null top-level item in SemanticProgramNode"
            );
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported global top-level item for codegen: "
                        + item.getClass().getSimpleName()
        );
    }

    private void emitStructDecl(SemanticStructDeclNode node) {
        if (node == null || node.symbol == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null struct declaration or symbol"
            );
        }

        openNamespace(node.symbol.namespace);

        emitLine("struct " + emitStructName(node.symbol));
        emitLine("{");
        indentLevel++;

        for (FieldSymbol field : node.symbol.fields) {
            emitStructField(field);
        }

        indentLevel--;
        emitLine("};");

        closeNamespace(node.symbol.namespace);
    }

    private void emitStructField(FieldSymbol field) {
        if (field == null || field.type == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null struct field or field type"
            );
        }

        StringBuilder sb = new StringBuilder();

        sb.append(emitType(field.type));
        sb.append(" ");
        sb.append(field.name);

        if (field.foldedDefault != null) {
            sb.append(" = ");
            sb.append(emitBigIntegerLiteral(field.foldedDefault));
        } else if (field.type instanceof SemanticPtrType) {

            sb.append(" = nullptr");
        }

        sb.append(";");

        emitLine(sb.toString());
    }

    private List<SemanticStructDeclNode> orderStructsByValueDependency(
            List<SemanticStructDeclNode> structs
    ) {

        Map<String, SemanticStructDeclNode> declByName = new LinkedHashMap<>();

        for (SemanticStructDeclNode struct : structs) {
            declByName.put(struct.symbol.qualifiedName(), struct);
        }

        LinkedHashSet<String> emitted = new LinkedHashSet<>();
        LinkedHashSet<String> inProgress = new LinkedHashSet<>();
        List<SemanticStructDeclNode> ordered = new ArrayList<>();

        for (SemanticStructDeclNode struct : structs) {
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
            SemanticStructDeclNode node,
            Map<String, SemanticStructDeclNode> declByName,
            LinkedHashSet<String> emitted,
            LinkedHashSet<String> inProgress,
            List<SemanticStructDeclNode> ordered
    ) {
        String name = node.symbol.qualifiedName();

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

        for (FieldSymbol field : node.symbol.fields) {
            for (String dependencyName : byValueStructDependencies(field.type)) {
                SemanticStructDeclNode dependency =
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

    private List<String> byValueStructDependencies(SemanticType type) {
        List<String> names = new ArrayList<>();

        if (type instanceof SemanticStructType struct) {
            names.add(struct.structSymbol.qualifiedName());
            return names;
        }

        if (type instanceof SemanticStaticArrayType staticArray) {
            names.addAll(byValueStructDependencies(staticArray.elementType));
        }

        return names;
    }

    private void emitFunctionPrototype(SemanticFunctionDeclNode node) {
        emitLine(emitFunctionHeader(node) + ";");
    }

    private void emitFunctionDecl(SemanticFunctionDeclNode node) {

        if (osFfiBridgeText != null && OsFfi.kindOf(node.symbol) != null) {
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

    private String emitFunctionHeader(SemanticFunctionDeclNode node) {
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

        List<ParameterSymbol> params = node.symbol.parameters;

        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            ParameterSymbol param = params.get(i);
            sb.append(emitType(param.type));
            sb.append(" ");
            sb.append(emitSymbolName(param));
        }

        sb.append(")");

        return sb.toString();
    }

    private void emitStmt(SemanticStmtNode node) {
        if (node == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot emit null statement"
            );
        }

        if (node instanceof SemanticBlockNode block) {
            emitBlock(block);
            return;
        }

        if (node instanceof SemanticVarDeclNode decl) {
            emitVarDecl(decl);
            return;
        }

        if (node instanceof SemanticAssignNode assign) {
            emitAssign(assign);
            return;
        }

        if (node instanceof SemanticIfNode ifNode) {
            emitIf(ifNode);
            return;
        }

        if (node instanceof SemanticWhileNode whileNode) {
            emitWhile(whileNode);
            return;
        }

        if (node instanceof SemanticBreakNode brk) {
            emitBreak(brk);
            return;
        }

        if (node instanceof SemanticContinueNode) {
            emitContinue();
            return;
        }

        if (node instanceof SemanticPrintNode print) {
            emitPrint(print);
            return;
        }

        if (node instanceof SemanticReturnNode ret) {
            emitReturn(ret);
            return;
        }

        if (node instanceof SemanticExprStmtNode exprStmt) {
            String expr = emitExpr(exprStmt.expr);
            flushPrelude();
            emitLine(expr + ";");
            return;
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported semantic statement for codegen: "
                        + node.getClass().getSimpleName()
        );
    }

    private void emitBlock(SemanticBlockNode node) {
        emitLine("{");
        indentLevel++;

        for (SemanticStmtNode stmt : node.statements) {
            emitStmt(stmt);
        }

        indentLevel--;
        emitLine("}");
    }

    private void emitGlobalForwardDeclaration(SemanticVarDeclNode node) {
        VariableSymbol sym = node.symbol;

        String cppType = emitType(sym.type);
        String name = emitSymbolName(sym);
        String qualifier = "";

        if (sym instanceof GlobalVariableSymbol global
                && (global.literalBytes != null
                    || global.literalValues != null
                    || global.literalHeaderData != null)) {
            qualifier = "const ";
        }

        emitLine("extern " + qualifier + cppType + " " + name + ";");
    }

    private void emitVarDecl(SemanticVarDeclNode node) {
        VariableSymbol sym = node.symbol;

        String cppType = emitType(sym.type);
        String name = emitSymbolName(sym);

        if (sym instanceof GlobalVariableSymbol global
                && (global.literalBytes != null
                    || global.literalValues != null
                    || global.literalHeaderData != null)) {
            String initializer;

            if (global.literalHeaderData != null) {
                initializer = emitLiteralViewHeaderInitializer(global);
            } else if (global.literalBytes != null) {
                initializer =
                        emitByteArrayInitializer(global.literalBytes);
            } else if (sym.type instanceof SemanticStaticArrayType staticArray) {
                initializer = emitStaticArrayLiteralInitializer(
                        staticArray,
                        global.literalValues);
            } else {
                throw new IllegalStateException(
                        "Internal compiler error: array literal global '"
                                + sym.qualifiedName()
                                + "' does not have a static array type");
            }

            emitLine("const "
                    + cppType
                    + " "
                    + name
                    + " = "
                    + initializer
                    + ";");
            return;
        }

        String initializer = "";

        if (node.foldedConstInitializer != null) {
            initializer = " = "
                    + emitBigIntegerLiteral(node.foldedConstInitializer);
        }

        if (node.staticInitializer != null) {
            String raw = emitExpr(node.staticInitializer);

            if (PointerPermissions.isStaticArrayViewDecay(
                    node.staticInitializer.type,
                    sym.type)) {
                initializer = " = "
                        + emitDecayedViewTemp(
                                raw,
                                node.staticInitializer.type,
                                sym.type
                        );
            } else {
                initializer = " = " + raw;
            }
        }

        if (initializer.isEmpty()
                && sym.type instanceof SemanticPtrType) {
            initializer = " = nullptr";
        }

        flushPrelude();
        emitLine(cppType + " " + name + initializer + ";");
    }

    private String emitLiteralViewHeaderInitializer(
            GlobalVariableSymbol global
    ) {
        if (!(global.type instanceof SemanticDynamicArrayType dynamicArray)) {
            throw new IllegalStateException(
                    "Internal compiler error: literal view header '"
                            + global.qualifiedName()
                            + "' does not have a dynamic array type");
        }

        GlobalVariableSymbol data = global.literalHeaderData;

        if (!(data.type instanceof SemanticStaticArrayType staticArray)) {
            throw new IllegalStateException(
                    "Internal compiler error: literal view header '"
                            + global.qualifiedName()
                            + "' is not backed by a static array global");
        }

        return "{"
                + staticArray.sizeLiteral
                + ", const_cast<"
                + emitType(dynamicArray.elementType)
                + "*>("
                + emitSymbolName(data)
                + ".data), true}";
    }

    private String emitStaticArrayLiteralInitializer(
            SemanticStaticArrayType type,
            List<BigInteger> values
    ) {
        List<BigInteger> safeValues =
                values == null ? List.of() : values;

        int[] cursor = new int[]{0};

        return emitStaticArrayLiteralValue(type, safeValues, cursor);
    }

    private String emitStaticArrayLiteralValue(
            SemanticStaticArrayType type,
            List<BigInteger> values,
            int[] cursor
    ) {
        int size = parseStaticArraySize(type.sizeLiteral);

        StringBuilder sb = new StringBuilder();

        sb.append("{");

        if (type.elementType instanceof SemanticStaticArrayType rowType) {
            sb.append("{");

            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                sb.append(emitStaticArrayLiteralValue(
                        rowType,
                        values,
                        cursor));
            }

            sb.append("}");
        } else {
            sb.append("{");

            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                BigInteger value = cursor[0] < values.size()
                        ? values.get(cursor[0]++)
                        : BigInteger.ZERO;

                sb.append(emitBigIntegerLiteral(value));
            }

            sb.append("}");
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
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Internal compiler error: invalid static array size "
                            + "literal for literal emission: "
                            + sizeLiteral);
        }
    }

    private String emitByteArrayInitializer(List<BigInteger> bytes) {
        StringBuilder sb = new StringBuilder();

        sb.append("{{");

        for (int i = 0; i < bytes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(bytes.get(i).toString(10));
        }

        sb.append("}}");

        return sb.toString();
    }

    private void emitAssign(SemanticAssignNode node) {

        String target = emitLValue(node.target);
        String value = emitExprAsType(node.value, node.target.type);
        flushPrelude();

        emitLine(
                target
                        + " "
                        + node.op
                        + " "
                        + value
                        + ";"
        );
    }

    private void emitIf(SemanticIfNode node) {

        String condition = emitExpr(node.condition);
        flushPrelude();
        emitLine("if (" + condition + ")");
        emitControlledStmt(node.thenBranch);

        if (node.elseBranch != null) {
            emitLine("else");
            emitControlledStmt(node.elseBranch);
        }
    }

    private void emitWhile(SemanticWhileNode node) {

        boolean needsContinueLabel =
                scanForContinueTarget(node.body, 0);
        boolean needsBreakLabel =
                scanForBreakTarget(node.body, 0);

        LoopFrame frame = new LoopFrame(nextLoopId++);
        loopStack.add(frame);

        String condition = emitExpr(node.condition);

        if (!pendingPrelude.isEmpty()) {
            emitLine("L_top_" + frame.id + ": ;");
            flushPrelude();
            emitLine("if (!(" + condition + ")) goto L_brk_" + frame.id + ";");
            emitStmt(node.body);

            if (needsContinueLabel) {
                emitLine("L_cont_" + frame.id + ": ;");
            }

            for (SemanticStmtNode updateStmt : node.update) {
                emitStmt(updateStmt);
            }

            emitLine("goto L_top_" + frame.id + ";");
            emitLine("L_brk_" + frame.id + ": ;");

            loopStack.remove(loopStack.size() - 1);
            return;
        }

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

            for (SemanticStmtNode updateStmt : node.update) {
                emitStmt(updateStmt);
            }

            indentLevel--;
            emitLine("}");
        } else if (hasUpdate) {

            emitLine("{");
            indentLevel++;

            if (node.body instanceof SemanticBlockNode bodyBlock) {
                for (SemanticStmtNode stmt : bodyBlock.statements) {
                    emitStmt(stmt);
                }
            } else {
                emitStmt(node.body);
            }

            for (SemanticStmtNode updateStmt : node.update) {
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

    private void emitBreak(SemanticBreakNode node) {
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

    private boolean scanForContinueTarget(SemanticStmtNode node, int depth) {
        if (node == null) {
            return false;
        }

        if (node instanceof SemanticContinueNode) {
            return depth == 0;
        }

        if (node instanceof SemanticBreakNode) {
            return false;
        }

        if (node instanceof SemanticWhileNode n) {
            if (scanForContinueTarget(n.body, depth + 1)) {
                return true;
            }

            for (SemanticStmtNode updateStmt : n.update) {
                if (scanForContinueTarget(updateStmt, depth + 1)) {
                    return true;
                }
            }

            return false;
        }

        if (node instanceof SemanticBlockNode n) {
            for (SemanticStmtNode stmt : n.statements) {
                if (scanForContinueTarget(stmt, depth)) {
                    return true;
                }
            }

            return false;
        }

        if (node instanceof SemanticIfNode n) {
            return scanForContinueTarget(n.thenBranch, depth)
                    || scanForContinueTarget(n.elseBranch, depth);
        }

        return false;
    }

    private boolean scanForBreakTarget(SemanticStmtNode node, int depth) {
        if (node == null) {
            return false;
        }

        if (node instanceof SemanticBreakNode brk) {
            return brk.levels == depth + 1;
        }

        if (node instanceof SemanticContinueNode) {
            return false;
        }

        if (node instanceof SemanticWhileNode n) {
            if (scanForBreakTarget(n.body, depth + 1)) {
                return true;
            }

            for (SemanticStmtNode updateStmt : n.update) {
                if (scanForBreakTarget(updateStmt, depth + 1)) {
                    return true;
                }
            }

            return false;
        }

        if (node instanceof SemanticBlockNode n) {
            for (SemanticStmtNode stmt : n.statements) {
                if (scanForBreakTarget(stmt, depth)) {
                    return true;
                }
            }

            return false;
        }

        if (node instanceof SemanticIfNode n) {
            return scanForBreakTarget(n.thenBranch, depth)
                    || scanForBreakTarget(n.elseBranch, depth);
        }

        return false;
    }

    private void emitControlledStmt(SemanticStmtNode node) {
        if (node instanceof SemanticBlockNode) {
            emitStmt(node);
            return;
        }

        indentLevel++;
        emitStmt(node);
        indentLevel--;
    }

    private void emitPrint(SemanticPrintNode node) {
        if (node.args == null || node.args.isEmpty()) {
            emitLine("std::cout << '\\n';");
            return;
        }

        StringBuilder sb = new StringBuilder("std::cout");

        for (int i = 0; i < node.args.size(); i++) {
            if (i > 0) {
                sb.append(" << ' '");
            }

            SemanticExprNode arg = node.args.get(i);
            sb.append(" << ").append(emitPrintableExpr(arg));
        }

        sb.append(" << '\\n';");

        flushPrelude();
        emitLine(sb.toString());
    }

    private void emitReturn(SemanticReturnNode node) {
        if (node.value == null) {
            emitLine("return;");
            return;
        }

        SemanticType targetType = node.function != null
                ? node.function.returnType()
                : node.value.type;

        String value = emitExprAsType(node.value, targetType);
        flushPrelude();

        emitLine("return " + value + ";");
    }

    private String emitExpr(SemanticExprNode node) {
        if (node == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot emit null expression"
            );
        }

        if (node instanceof SemanticIntLiteralNode literal) {
            return emitIntLiteral(literal);
        }

        if (node instanceof SemanticNullLiteralNode) {

            return "nullptr";
        }

        if (node instanceof SemanticVarExprNode var) {
            return emitSymbolName(var.symbol);
        }

        if (node instanceof SemanticUnaryOpNode unary) {
            return emitUnaryOp(unary);
        }

        if (node instanceof SemanticBinOpNode bin) {
            return emitBinOp(bin);
        }

        if (node instanceof SemanticArrayAccessNode access) {
            return emitArrayAccess(access);
        }

        if (node instanceof SemanticFunctionCallNode call) {
            return emitFunctionCall(call);
        }

        if (node instanceof SemanticArrayIntrinsicCallNode intrinsic) {
            return emitArrayIntrinsicCall(intrinsic);
        }

        if (node instanceof SemanticGetPtrNode getPtr) {
            return emitGetPtr(getPtr);
        }


        if (node instanceof SemanticDerefNode deref) {
            return emitDeref(deref);
        }

        if (node instanceof SemanticCastExprNode cast) {
            return emitCast(cast);
        }

        if (node instanceof SemanticMemberAccessNode member) {
            return emitMemberAccess(member);
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported semantic expression for codegen: "
                        + node.getClass().getSimpleName()
        );
    }

    private String emitIntLiteral(SemanticIntLiteralNode node) {

        String raw = node.rawValue;

        if (isPlainDecimal(raw)
                && node.constantValue != null
                && node.constantValue.compareTo(
                        BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return raw + "ULL";
        }

        return raw;
    }

    private boolean isPlainDecimal(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }

        if (raw.startsWith("0x") || raw.startsWith("0X")
                || raw.startsWith("0b") || raw.startsWith("0B")) {
            return false;
        }

        return true;
    }

    private String emitBigIntegerLiteral(BigInteger value) {
        boolean negative = value.signum() < 0;

        BigInteger magnitude = value.abs();
        String digits = magnitude.toString();

        boolean needsUllSuffix =
                magnitude.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0;

        String core = needsUllSuffix ? digits + "ULL" : digits;

        return negative ? "-" + core : core;
    }

    private String emitUnaryOp(SemanticUnaryOpNode node) {

        if (node.isConstant() && node.constantValue != null) {
            return emitBigIntegerLiteral(node.constantValue);
        }

        String inner = emitExpr(node.expr);
        String raw = "(" + node.op + inner + ")";

        return castToSemanticExprTypeIfNeeded(node, raw);
    }

    private String emitBinOp(SemanticBinOpNode node) {

        if (node.isConstant() && node.constantValue != null) {
            return emitBigIntegerLiteral(node.constantValue);
        }

        String left = emitExpr(node.left);
        String right = emitExpr(node.right);

        String raw = "(" + left + " " + node.op + " " + right + ")";

        return castToSemanticExprTypeIfNeeded(node, raw);
    }

    private String emitArrayAccess(SemanticArrayAccessNode node) {
        return "("
                + emitExpr(node.target)
                + "["
                + emitExprAsType(
                        node.index,
                        UINT32_TYPE
                )
                + "])";
    }

    private String emitFunctionCall(SemanticFunctionCallNode node) {
        FunctionSymbol fn = node.function;

        if (fn == null) {
            throw new IllegalStateException(
                    "Internal compiler error: function call has null resolved function"
            );
        }

        if (osFfiBridgeText != null) {
            OsFfi.Kind ffiKind = OsFfi.kindOf(fn);

            if (ffiKind != null) {

                OsFfi.checkTargetAllowed(ffiKind, target);
                usedOsFfiKinds.add(ffiKind);
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append(emitFunctionName(fn));
        sb.append("(");

        List<SemanticType> parameterTypes = fn.parameterTypes();

        if (parameterTypes.size() != node.args.size()) {
            throw new IllegalStateException(
                    "Internal compiler error: resolved function call arity mismatch for "
                            + fn.fullSignatureString()
                            + ": expected "
                            + parameterTypes.size()
                            + ", got "
                            + node.args.size()
            );
        }

        for (int i = 0; i < node.args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            SemanticExprNode arg = node.args.get(i);
            SemanticType paramType = parameterTypes.get(i);

            sb.append(emitExprAsType(arg, paramType));
        }

        sb.append(")");

        return sb.toString();
    }

    private String emitArrayIntrinsicCall(SemanticArrayIntrinsicCallNode node) {
        if (node.kind == null) {
            throw new IllegalStateException(
                    "Internal compiler error: array intrinsic call has null kind"
            );
        }

        if (node.kind == SemanticArrayIntrinsicKind.LENGTH) {
            return emitArrayLengthCall(node);
        }

        validateArrayIntrinsicArityForCodegen(node);

        StringBuilder sb = new StringBuilder();

        sb.append(emitLValue(node.receiver));
        sb.append(".");
        sb.append(node.kind.runtimeName);
        sb.append("(");

        for (int i = 0; i < node.args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(emitArrayIntrinsicArg(node, i));
        }

        sb.append(")");

        return sb.toString();
    }

    private String emitArrayLengthCall(SemanticArrayIntrinsicCallNode node) {
        if (!node.args.isEmpty()) {
            throw new IllegalStateException(
                    "Internal compiler error: invalid arity for array intrinsic 'length'"
            );
        }

        if (node.receiver.type instanceof SemanticStaticArrayType staticArray) {
            return "static_cast<uint32_t>(" + staticArray.sizeLiteral + ")";
        }

        if (node.receiver.type instanceof SemanticDynamicArrayType) {
            return emitExpr(node.receiver) + ".length()";
        }

        throw new IllegalStateException(
                "Internal compiler error: length() receiver is not an array"
        );
    }

    private String emitArrayIntrinsicArg(
            SemanticArrayIntrinsicCallNode node,
            int index
    ) {
        SemanticExprNode arg = node.args.get(index);

        return switch (node.kind) {
            case INIT, INIT_ZERO, RESIZE ->
                    emitExprAsType(arg, UINT32_TYPE);

            case UNINIT ->
                    throw new IllegalStateException(
                            "Internal compiler error: uninit() should not have arguments"
                    );

            case MEMSET -> {
                if (index == 0) {
                    yield emitExprAsType(arg, UINT8_TYPE);
                }

                yield emitExprAsType(arg, UINT32_TYPE);
            }

            case MEMCPY -> {
                if (index == 0) {

                    yield emitExpr(arg);
                }

                yield emitExprAsType(arg, UINT32_TYPE);
            }

            case MEMMOVE ->
                    emitExprAsType(arg, UINT32_TYPE);

            case LENGTH ->
                    throw new IllegalStateException(
                            "Internal compiler error: length() should not have arguments"
                    );
        };
    }

    private void validateArrayIntrinsicArityForCodegen(
            SemanticArrayIntrinsicCallNode node
    ) {
        boolean ok = switch (node.kind) {
            case INIT, INIT_ZERO, RESIZE ->
                    node.args.size() == 1;

            case UNINIT ->
                    node.args.isEmpty();

            case MEMSET ->
                    node.args.size() == 1 || node.args.size() == 3;

            case MEMCPY ->
                    node.args.size() == 1 || node.args.size() == 4;

            case MEMMOVE ->
                    node.args.size() == 3;

            case LENGTH ->
                    node.args.isEmpty();
        };

        if (!ok) {
            throw new IllegalStateException(
                    "Internal compiler error: invalid arity for array intrinsic '"
                            + node.kind.runtimeName
                            + "': got "
                            + node.args.size()
            );
        }
    }

    private String emitGetPtr(SemanticGetPtrNode node) {

        return "(&" + emitLValue(node.target) + ")";
    }

    private String emitDeref(SemanticDerefNode node) {

        return "(*" + emitExpr(node.expr) + ")";
    }

    private String emitCast(SemanticCastExprNode node) {

        if (node.isConstant() && node.constantValue != null) {
            return emitBigIntegerLiteral(node.constantValue);
        }

        String raw = emitExpr(node.expr);

        if (PointerPermissions.isStaticArrayViewDecay(node.expr.type, node.type)) {
            return emitDecayedViewTemp(raw, node.expr.type, node.type);
        }

        if (!node.checked) {

            return "static_cast<" + emitType(node.type) + ">(" + raw + ")";
        }

        return "franko_unsafe_cast<" + emitType(node.type) + ">(" + raw + ")";
    }

    private String emitLValue(SemanticExprNode node) {
        if (node instanceof SemanticVarExprNode var) {
            return emitSymbolName(var.symbol);
        }

        if (node instanceof SemanticArrayAccessNode access) {
            return emitArrayAccess(access);
        }

        if (node instanceof SemanticDerefNode deref) {
            return emitDeref(deref);
        }

        if (node instanceof SemanticMemberAccessNode member) {
            return emitMemberAccess(member);
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported lvalue expression for codegen: "
                        + node.getClass().getSimpleName()
        );
    }

    private String emitMemberAccess(SemanticMemberAccessNode node) {
        return emitExpr(node.target) + "." + node.field.name;
    }

    private String emitType(SemanticType type) {
        if (type instanceof SemanticVoidType) {
            return "void";
        }

        if (type instanceof SemanticPrimitiveType primitive) {
            return emitPrimitiveType(primitive.kind);
        }

        if (type instanceof SemanticDynamicArrayType dynamicArray) {
            return "Franko_Dynamic_Array<"
                    + emitType(dynamicArray.elementType)
                    + ">";
        }

        if (type instanceof SemanticStaticArrayType staticArray) {
            return "Franko_Static_Array<"
                    + emitType(staticArray.elementType)
                    + ", "
                    + staticArray.sizeLiteral
                    + ">";
        }

        if (type instanceof SemanticPtrType ptr) {
            String pointee = emitType(ptr.referencedType);

            return ptr.permission == PtrPermission.READ_ONLY
                    ? pointee + " const*"
                    : pointee + "*";
        }

        if (type instanceof SemanticStructType struct) {

            return emitStructName(struct.structSymbol);
        }

        if (type instanceof SemanticNullType) {

            throw new IllegalStateException(
                    "Internal compiler error: the null type has no C++ representation"
            );
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported semantic type for codegen: "
                        + type.getClass().getSimpleName()
        );
    }

    private String emitPrimitiveType(SemanticPrimitiveKind kind) {
        return switch (kind) {
            case INT8 -> "int8_t";
            case INT16 -> "int16_t";
            case INT32 -> "int32_t";
            case INT64 -> "int64_t";

            case UINT8 -> "uint8_t";
            case UINT16 -> "uint16_t";
            case UINT32 -> "uint32_t";
            case UINT64 -> "uint64_t";
        };
    }

    private String emitSymbolName(VariableSymbol symbol) {

        if (symbol == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null symbol in codegen"
            );
        }

        if (symbol instanceof GlobalVariableSymbol global) {
            return qualify(global.namespace, "VAR_" + symbol.name);
        }

        return "VAR_" + symbol.name;
    }

    private String emitFunctionName(FunctionSymbol symbol) {

        if (symbol instanceof MethodSymbol method) {
            return qualify(method.owner.namespace, method.emittedLocalName());
        }

        return qualify(symbol.namespace, symbol.emittedLocalName());
    }

    private String emitStructName(StructSymbol symbol) {
        return qualify(symbol.namespace, "STRUCT_" + symbol.name);
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

    private String emitExprAsType(
            SemanticExprNode expr,
            SemanticType targetType
    ) {
        String raw = emitExpr(expr);

        if (targetType instanceof SemanticPrimitiveType) {

            if (expr.isConstant()) {
                return "static_cast<" + emitType(targetType) + ">(" + raw + ")";
            }

            if (sameSemanticType(expr.type, targetType)) {
                return raw;
            }

            return "static_cast<" + emitType(targetType) + ">(" + raw + ")";
        }

        if (PointerPermissions.isStaticArrayViewDecay(expr.type, targetType)) {
            return emitDecayedViewTemp(raw, expr.type, targetType);
        }

        return raw;
    }

    private String emitDecayedViewTemp(
            String raw,
            SemanticType sourceType,
            SemanticType targetType
    ) {
        SemanticStaticArrayType staticArray = (SemanticStaticArrayType)
                ((SemanticPtrType) sourceType).referencedType;
        SemanticDynamicArrayType dynamicArray = (SemanticDynamicArrayType)
                ((SemanticPtrType) targetType).referencedType;

        String elementType = emitType(dynamicArray.elementType);
        String name = "_TMP_VIEW_" + (nextDecayTempId++);

        pendingPrelude.add(
                "Franko_Dynamic_Array<"
                        + elementType
                        + "> "
                        + name
                        + " = {"
                        + staticArray.sizeLiteral
                        + ", const_cast<"
                        + elementType
                        + "*>("
                        + raw
                        + "->data), true};"
        );

        return "(&" + name + ")";
    }

    private void flushPrelude() {
        for (String line : pendingPrelude) {
            emitLine(line);
        }

        pendingPrelude.clear();
    }

    private String castToSemanticExprTypeIfNeeded(
            SemanticExprNode node,
            String raw
    ) {
        if (node.type instanceof SemanticPrimitiveType) {
            return "static_cast<" + emitType(node.type) + ">(" + raw + ")";
        }

        return raw;
    }

    private String emitPrintableExpr(SemanticExprNode expr) {
        String emitted = emitExpr(expr);

        if (expr.type instanceof SemanticPrimitiveType primitive) {

            if (primitive.kind == SemanticPrimitiveKind.INT8
                    || primitive.kind == SemanticPrimitiveKind.UINT8) {
                return "(+(" + emitted + "))";
            }

            return emitted;
        }

        if (expr.type instanceof SemanticPtrType) {

            return "static_cast<const void*>(" + emitted + ")";
        }

        if (expr instanceof SemanticNullLiteralNode) {

            return "static_cast<const void*>(" + emitted + ")";
        }

        return emitted;
    }

    private void emitLine(String s) {
        for (int i = 0; i < indentLevel; i++) {
            out.append("    ");
        }

        out.append(s).append('\n');
    }

    private boolean sameSemanticType(SemanticType a, SemanticType b) {
        if (a == null || b == null) {
            return false;
        }

        return a.equals(b);
    }
}
