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

    static final SemanticType UINT32_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.UINT32);

    public String generate(SemanticASTNode root) {
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

    private void emitNode(SemanticASTNode node) {
        if (node instanceof SemanticProgramNode program) {
            emitProgram(program);
            return;
        }

        if (node instanceof SemanticNamespaceNode namespace) {

            emitProgram(new SemanticProgramNode(
                    namespace.span,
                    List.of(namespace)
            ));
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

        if (node instanceof SemanticEnumDeclNode enumDecl) {
            emitEnumDecl(enumDecl);
            return;
        }

        if (node instanceof SemanticStmtNode stmt) {
            emitStmt(stmt);
            return;
        }

        if (node instanceof SemanticExprNode expr) {
            emitLine(expressionEmitter.emitExpr(this, expr) + ";");
            return;
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported SemanticASTNode for codegen: "
                        + node.getClass().getSimpleName()
        );
    }

    private void emitProgram(SemanticProgramNode node) {

        List<SemanticStructDeclNode> globalStructs = new ArrayList<>();
        List<SemanticEnumDeclNode> globalEnums = new ArrayList<>();
        List<SemanticVarDeclNode> globalGlobals = new ArrayList<>();
        List<SemanticFunctionDeclNode> globalFunctions = new ArrayList<>();

        Map<String, List<SemanticStructDeclNode>> nsStructs = new LinkedHashMap<>();
        Map<String, List<SemanticEnumDeclNode>> nsEnums = new LinkedHashMap<>();
        Map<String, List<SemanticVarDeclNode>> nsGlobals = new LinkedHashMap<>();
        Map<String, List<SemanticFunctionDeclNode>> nsFunctions = new LinkedHashMap<>();

        for (SemanticASTNode item : node.topLevelItems) {
            if (item instanceof SemanticNamespaceNode namespace) {
                partitionNamespaceItems(
                        namespace,
                        nsStructs,
                        nsEnums,
                        nsGlobals,
                        nsFunctions
                );
                continue;
            }

            partitionItem(item, globalStructs, globalEnums, globalGlobals, globalFunctions);
        }

        for (SemanticEnumDeclNode enumDecl : globalEnums) {
            emitEnumDecl(enumDecl);
        }

        if (!globalEnums.isEmpty()) {
            emitLine("");
        }

        for (Map.Entry<String, List<SemanticEnumDeclNode>> entry : nsEnums.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            openNamespace(entry.getKey());

            for (SemanticEnumDeclNode enumDecl : entry.getValue()) {
                emitEnumDeclBody(enumDecl);
            }

            closeNamespace(entry.getKey());
        }

        if (anyNonEmpty(nsEnums)) {
            emitLine("");
        }

        for (Map.Entry<String, List<SemanticStructDeclNode>> entry : nsStructs.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            openNamespace(entry.getKey());

            for (SemanticStructDeclNode struct : entry.getValue()) {
                emitLine("struct "
                        + qualify(
                                struct.symbol.namespace,
                                emittedStructTypeName(struct.symbol)
                        )
                        + ";");
            }

            closeNamespace(entry.getKey());
        }

        if (!globalStructs.isEmpty()) {
            for (SemanticStructDeclNode struct : globalStructs) {
                emitLine("struct "
                        + qualify(
                                struct.symbol.namespace,
                                emittedStructTypeName(struct.symbol)
                        )
                        + ";");
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
                    emitLine(emitFunctionHeader(fn) + ";");
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
                    emitLine(emitFunctionHeader(fn) + ";");
                }

                closeNamespace(entry.getKey());
            }

            for (SemanticFunctionDeclNode fn : globalFunctions) {
                emitLine(emitFunctionHeader(fn) + ";");
            }

            for (SemanticFunctionDeclNode fn : globalMethods) {
                emitLine(emitFunctionHeader(fn) + ";");
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
            Map<String, List<SemanticEnumDeclNode>> nsEnums,
            Map<String, List<SemanticVarDeclNode>> nsGlobals,
            Map<String, List<SemanticFunctionDeclNode>> nsFunctions
    ) {
        List<SemanticStructDeclNode> structs = nsStructs.computeIfAbsent(
                namespace.name,
                ignored -> new ArrayList<>()
        );

        List<SemanticEnumDeclNode> enums = nsEnums.computeIfAbsent(
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
            partitionItem(item, structs, enums, globals, functions);
        }
    }

    private void partitionItem(
            SemanticASTNode item,
            List<SemanticStructDeclNode> structs,
            List<SemanticEnumDeclNode> enums,
            List<SemanticVarDeclNode> globals,
            List<SemanticFunctionDeclNode> functions
    ) {
        if (item instanceof SemanticStructDeclNode struct) {
            structs.add(struct);
            return;
        }

        if (item instanceof SemanticEnumDeclNode enumDecl) {
            enums.add(enumDecl);
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

        emitLine("struct " + qualify(
                node.symbol.namespace,
                emittedStructTypeName(node.symbol)
        ));
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

        sb.append(emitDecl(field.type, field.emittedName()));

        if (field.foldedDefault != null) {
            sb.append(" = ");
            sb.append(expressionEmitter.emitBigIntegerLiteral(this, field.foldedDefault));
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

        if (type instanceof SemanticTupleType tuple) {
            names.add(tuple.cppSymbol.qualifiedName());
            return names;
        }

        if (type instanceof SemanticStaticArrayType staticArray) {
            names.addAll(byValueStructDependencies(staticArray.elementType));
        }

        return names;
    }

    private void emitFunctionDecl(SemanticFunctionDeclNode node) {

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
            sb.append(emitDecl(param.type, emitSymbolName(param)));
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

        if (node instanceof SemanticReturnNode ret) {
            emitReturn(ret);
            return;
        }

        if (node instanceof SemanticExprStmtNode exprStmt) {
            String expr = expressionEmitter.emitExpr(this, exprStmt.expr);
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

        String qualifier = "";

        if (sym instanceof GlobalVariableSymbol global
                && (global.literalBytes != null
                    || global.literalValues != null)) {
            qualifier = "const ";
        }

        emitLine("extern " + qualifier
                + emitDecl(sym.type, emitSymbolName(sym)) + ";");
    }

    private void emitVarDecl(SemanticVarDeclNode node) {
        VariableSymbol sym = node.symbol;

        String declaration = emitDecl(sym.type, emitSymbolName(sym));

        if (sym instanceof GlobalVariableSymbol global
                && (global.literalBytes != null
                    || global.literalValues != null)) {
            String initializer;

            if (global.literalBytes != null) {
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
                && sym.type instanceof SemanticPtrType) {
            initializer = " = nullptr";
        }

        emitLine(declaration + initializer + ";");
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

        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }

            if (type.elementType instanceof SemanticStaticArrayType rowType) {
                sb.append(emitStaticArrayLiteralValue(
                        rowType,
                        values,
                        cursor));
                continue;
            }

            BigInteger value = cursor[0] < values.size()
                    ? values.get(cursor[0]++)
                    : BigInteger.ZERO;

            if (type.elementType instanceof SemanticEnumType enumType) {
                EnumVariant variant = enumType.enumSymbol.variantWithValue(value);

                if (variant == null && !enumType.enumSymbol.variants.isEmpty()) {
                    variant = enumType.enumSymbol.variants.get(0);
                }

                if (variant != null) {
                    sb.append(qualify(
                            enumType.enumSymbol.namespace,
                            emittedEnumTypeName(enumType.enumSymbol) + "::VARIANT_" + variant.name + "()"));
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

    private void emitAssign(SemanticAssignNode node) {

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

    private void emitIf(SemanticIfNode node) {

        String condition = expressionEmitter.emitExpr(this, node.condition);
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

    private void emitReturn(SemanticReturnNode node) {
        if (node.value == null) {
            emitLine("return;");
            return;
        }

        SemanticType targetType = node.function != null
                ? node.function.returnType()
                : node.value.type;

        String value = expressionEmitter.emitAsType(this, node.value, targetType);

        emitLine("return " + value + ";");
    }

    String emitType(SemanticType type) {
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
            StringBuilder dimensions = new StringBuilder();
            SemanticType elementType = staticArray;

            while (elementType instanceof SemanticStaticArrayType row) {
                dimensions.append("[").append(row.sizeLiteral).append("]");
                elementType = row.elementType;
            }

            return emitType(elementType) + dimensions;
        }

        if (type instanceof SemanticPtrType ptr) {
            if (ptr.referencedType instanceof SemanticStaticArrayType
                    || ptr.referencedType instanceof SemanticDynamicArrayType) {
                return "Franko_Fat_Pointer<"
                        + emitType(pointerElementType(ptr.referencedType))
                        + ">";
            }

            return "Franko_Thin_Pointer<"
                    + emitType(ptr.referencedType)
                    + ">";
        }

        if (type instanceof SemanticStructType struct) {

            return qualify(
                    struct.structSymbol.namespace,
                    emittedStructTypeName(struct.structSymbol)
            );
        }

        if (type instanceof SemanticTupleType tuple) {
            return qualify(
                    tuple.cppSymbol.namespace,
                    emittedStructTypeName(tuple.cppSymbol)
            );
        }

        if (type instanceof SemanticEnumType enumType) {
            return qualify(
                    enumType.enumSymbol.namespace,
                    emittedEnumTypeName(enumType.enumSymbol)
            );
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

    private String emitDecl(SemanticType type, String name) {
        if (type instanceof SemanticStaticArrayType staticArray) {
            StringBuilder dimensions = new StringBuilder();
            SemanticType elementType = staticArray;

            while (elementType instanceof SemanticStaticArrayType row) {
                dimensions.append("[").append(row.sizeLiteral).append("]");
                elementType = row.elementType;
            }

            return emitType(elementType) + " " + name + dimensions;
        }

        return emitType(type) + " " + name;
    }

    private static SemanticType pointerElementType(SemanticType referencedType) {
        if (referencedType instanceof SemanticStaticArrayType staticArray) {
            return staticArray.elementType;
        }

        if (referencedType instanceof SemanticDynamicArrayType dynamicArray) {
            return dynamicArray.elementType;
        }

        return referencedType;
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

    String emitSymbolName(VariableSymbol symbol) {

        if (symbol == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null symbol in codegen"
            );
        }

        if (symbol instanceof GlobalVariableSymbol global) {
            if (global.literalBytes != null || global.literalValues != null) {
                return qualify(global.namespace, symbol.name);
            }

            return qualify(global.namespace, "VAR_" + symbol.name);
        }

        return "VAR_" + symbol.name;
    }

    String emitFunctionName(FunctionSymbol symbol) {

        if (symbol instanceof MethodSymbol method) {
            return qualify(method.owner.namespace, method.emittedLocalName());
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

    private void emitEnumDecl(SemanticEnumDeclNode node) {
        openNamespace(node.symbol.namespace);
        emitEnumDeclBody(node);
        closeNamespace(node.symbol.namespace);
    }

    private void emitEnumDeclBody(SemanticEnumDeclNode node) {
        EnumSymbol symbol = node.symbol;
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

        emitLine("franko_panic((\"Invalid value for enum " + symbol.qualifiedName() + ": \"");
        indentLevel++;
        emitLine("+ std::to_string(v)");
        emitLine("+ \" is not a declared variant (valid variants: " + validList + ")\").c_str());");
        indentLevel--;
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

    String emitEnumVariant(SemanticEnumVariantNode node) {
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
