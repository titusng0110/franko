import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * CPP14 CODE GENERATOR
 * ============================================================================
 *
 * PURPOSE:
 * Cpp14Codegen emits C++14 code from the fully lowered, type-checked Semantic
 * AST.
 *
 * This generator assumes these phases have already succeeded:
 *
 *   1. Parser
 *   2. AST visitor
 *   3. Desugarer
 *   4. SemanticAnalyzer
 *   5. MasterChecker
 *
 * Therefore, this code generator does not perform legality checking. It trusts
 * that:
 *
 *   - variables are resolved to VariableSymbol objects,
 *   - function calls are resolved to FunctionSymbol overloads,
 *   - expression types are already inferred,
 *   - invalid operators were rejected,
 *   - invalid assignments were rejected,
 *   - invalid address arithmetic was rejected,
 *   - invalid array intrinsic usage was rejected,
 *   - invalid function return usage was rejected.
 *
 * ----------------------------------------------------------------------------
 * FUNCTION REPRESENTATION
 * ----------------------------------------------------------------------------
 *
 * Franko:
 *
 *   func main() -> int32_t {
 *       return 0;
 *   }
 *
 * emits as:
 *
 *   int32_t main()
 *   {
 *       return static_cast<int32_t>(0);
 *   }
 *
 * Function prototypes are emitted before function definitions so Franko forward
 * references and mutual recursion compile in C++.
 *
 * Function calls are emitted using the selected FunctionSymbol. Primitive
 * arguments are cast to the selected parameter type to preserve Franko overload
 * resolution in C++.
 *
 * ----------------------------------------------------------------------------
 * ADDRESS REPRESENTATION
 * ----------------------------------------------------------------------------
 *
 * Franko:
 *
 *   addr<T>
 *
 * is emitted as:
 *
 *   T*
 *
 * Franko:
 *
 *   getaddr(x)
 *
 * is emitted as:
 *
 *   (&x)
 *
 * Franko:
 *
 *   deref(p)
 *
 * is emitted as:
 *
 *   (*p)
 *
 * ----------------------------------------------------------------------------
 * ARRAY REPRESENTATION
 * ----------------------------------------------------------------------------
 *
 * Franko dynamic arrays:
 *
 *   array<T>
 *
 * emit as:
 *
 *   Franko_Dynamic_Array<T>
 *
 * Franko static arrays:
 *
 *   array<T, N>
 *
 * emit as:
 *
 *   Franko_Static_Array<T, N>
 *
 * Array intrinsics are lowered by SemanticAnalyzer into expression nodes:
 *
 *   target(size)                         -> SemanticArrayIntrinsicCallNode INIT
 *   target.init(size)                    -> SemanticArrayIntrinsicCallNode INIT
 *   target.init_zero(size)               -> SemanticArrayIntrinsicCallNode INIT_ZERO
 *   target.resize(size)                  -> SemanticArrayIntrinsicCallNode RESIZE
 *   target.uninit()                      -> SemanticArrayIntrinsicCallNode UNINIT
 *   target.memset(value)                 -> SemanticArrayIntrinsicCallNode MEMSET
 *   target.memset(value, start, count)   -> SemanticArrayIntrinsicCallNode MEMSET
 *   target.memcpy(sourceAddr)            -> SemanticArrayIntrinsicCallNode MEMCPY
 *   target.memcpy(sourceAddr, dstStart, srcStart, count)
 *                                      -> SemanticArrayIntrinsicCallNode MEMCPY
 *   target.memmove(dstStart, srcStart, count)
 *                                      -> SemanticArrayIntrinsicCallNode MEMMOVE
 *
 * Because array intrinsics are expressions, status-returning intrinsics such as
 * init/init_zero/resize may appear in value contexts after checking:
 *
 *   status = xs.init(10);
 *
 * Void-returning intrinsics are emitted the same way, but MasterChecker rejects
 * their use in value contexts.
 *
 * ============================================================================
 */
public class Cpp14Codegen {

    private final StringBuilder out = new StringBuilder();
    private int indentLevel = 0;

    /*
     * Name of the C++ namespace currently being emitted, or null while
     * emitting at global scope.
     *
     * References to symbols from other namespaces are qualified
     * (ns::name); references to global-namespace symbols from inside a
     * namespace block are rooted (::name) so they cannot be captured by
     * same-named symbols in that namespace.
     */
    private String currentEmitNamespace = null;

    private static final SemanticType UINT8_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8);

    private static final SemanticType UINT32_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.UINT32);

    /**
     * Generates the Franko program as C++14 source content intended to replace
     * __FRANKO_PROGRAM__ inside ProgramTemplate.cpp.
     */
    public String generate(SemanticASTNode root) {
        if (root == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot generate code for null Semantic AST"
            );
        }

        out.setLength(0);
        indentLevel = 0;

        emitNode(root);

        return out.toString();
    }

    // ============================================================
    // Root Dispatch
    // ============================================================

    private void emitNode(SemanticASTNode node) {
        if (node instanceof SemanticProgramNode program) {
            emitProgram(program);
            return;
        }

        if (node instanceof SemanticNamespaceNode namespace) {
            /*
             * Defensive: emitProgram partitions namespaces before emission,
             * so a namespace node reaching emitNode directly only happens
             * for non-program roots. Emit it as one namespace block with
             * the same item handling as a program.
             */
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

    /**
     * Emits top-level items.
     *
     * Current Franko global-scope rule:
     *
     *   global scope may contain only:
     *
     *     - struct declarations,
     *     - global variable declarations,
     *     - function declarations.
     *
     * Codegen emits in dependency-safe C++ order:
     *
     *   1. all struct declarations,
     *   2. all global variable declarations,
     *   3. all function prototypes,
     *   4. all function definitions.
     *
     * This matters because Franko allows functions to reference global variables
     * declared later in source order:
     *
     *   func main() -> int32_t {
     *       g = 1
     *       return 0
     *   }
     *
     *   uint32_t g
     *
     * The generated C++ must declare g before main's function body.
     */
    private void emitProgram(SemanticProgramNode node) {
        /*
         * Partition top-level items by namespace.
         *
         * SemanticAnalyzer should already ensure only struct declarations,
         * global variable declarations, function declarations, and
         * namespace declarations appear here. The fallback error below is
         * defensive.
         */
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

        /*
         * 1. Forward-declare every struct, grouped by namespace, so
         *    addr<Struct> fields, global pointer types, and function
         *    signatures can name structs defined later.
         */
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

        /*
         * 2. Emit struct definitions in one by-value dependency order
         *    across all namespaces.
         *
         * Structs must be emitted before global variables and functions
         * because they can be referenced in global variable types and
         * function signatures. A struct used by value inside another
         * struct must be defined first, so declarations are topologically
         * sorted over by-value field dependencies. addr fields and
         * dynamic-array fields are pointer-backed and impose no ordering
         * constraint.
         *
         * Each definition opens its own namespace block, so one global
         * dependency order works across namespaces.
         */
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

        /*
         * 3. Emit global variables, namespace by namespace, then the
         *    global namespace.
         */
        if (anyNonEmpty(nsGlobals) || !globalGlobals.isEmpty()) {
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

        /*
         * 4. Emit function prototypes.
         *
         * This supports forward references, direct recursion, and mutual
         * recursion across namespaces.
         */
        if (anyNonEmpty(nsFunctions) || !globalFunctions.isEmpty()) {
            for (Map.Entry<String, List<SemanticFunctionDeclNode>> entry : nsFunctions.entrySet()) {
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

            emitLine("");
        }

        /*
         * 5. Emit function definitions.
         *
         * Definitions are emitted in source order relative to other
         * functions of the same namespace.
         */
        for (Map.Entry<String, List<SemanticFunctionDeclNode>> entry : nsFunctions.entrySet()) {
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
    }

    /**
     * True when any namespace list in the map is non-empty.
     *
     * Namespaces are registered in these maps even when they contribute
     * no items of a given kind, so map emptiness alone would emit empty
     * namespace blocks.
     */
    private static boolean anyNonEmpty(Map<String, ? extends List<?>> byNamespace) {
        for (List<?> items : byNamespace.values()) {
            if (!items.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Partitions one namespace's semantic items into its struct, global
     * variable, and function lists.
     */
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

    /**
     * Partitions one semantic item into the given lists.
     */
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

    // ============================================================
    // Struct Declarations
    // ============================================================

    /**
     * Emits a C++ struct definition for a Franko struct declaration.
     *
     * Fields are emitted in declaration order. A field's folded const
     * initializer, when present, becomes a C++ non-static data member
     * initializer (NSDMI):
     *
     *   struct X
     *   {
     *       int32_t num = 0;
     *       int64_t longnum = 3;
     *   };
     */
    private void emitStructDecl(SemanticStructDeclNode node) {
        if (node == null || node.symbol == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null struct declaration or symbol"
            );
        }

        /*
         * A namespace struct is emitted inside its own C++ namespace
         * block, so struct definitions can be interleaved in one global
         * by-value dependency order across namespaces.
         */
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
        } else if (field.type instanceof SemanticAddrType) {
            /*
             * Address fields are guaranteed null-initialized. C++ default
             * member initializers apply whenever a struct instance is
             * created without explicit field initialization.
             */
            sb.append(" = nullptr");
        }

        sb.append(";");

        emitLine(sb.toString());
    }

    /**
     * Orders struct declarations so that every struct used by value inside
     * another struct is defined first.
     *
     * By-value edges are:
     *
     *   - a field whose type is a struct,
     *   - a field whose type is a static array of structs (recursively).
     *
     * addr fields and dynamic-array fields are pointer-backed and impose no
     * ordering constraint.
     *
     * By-value cycles are rejected by DeclarationChecker before codegen, so a
     * cycle reaching this method is an internal compiler error.
     */
    private List<SemanticStructDeclNode> orderStructsByValueDependency(
            List<SemanticStructDeclNode> structs
    ) {
        /*
         * Keyed by qualified name: different namespaces may declare
         * structs with the same simple name.
         */
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

    /**
     * Collects struct names reachable by value from a field type:
     *
     *   - direct struct field types,
     *   - struct element types of static arrays (recursively).
     */
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

    // ============================================================
    // Functions
    // ============================================================

    private void emitFunctionPrototype(SemanticFunctionDeclNode node) {
        emitLine(emitFunctionHeader(node) + ";");
    }

    private void emitFunctionDecl(SemanticFunctionDeclNode node) {
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

    // ============================================================
    // Statements
    // ============================================================

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

        if (node instanceof SemanticDelNode del) {
            emitDel(del);
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
            emitLine(emitExpr(exprStmt.expr) + ";");
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

    private void emitVarDecl(SemanticVarDeclNode node) {
        VariableSymbol sym = node.symbol;

        String cppType = emitType(sym.type);
        String name = emitSymbolName(sym);

        /*
         * Global variable declarations may carry a folded const initializer.
         * The initializer is emitted as static initialization; it is null for
         * ordinary locals and for globals without an initializer.
         */
        String initializer = "";

        if (node.foldedConstInitializer != null) {
            initializer = " = "
                    + emitBigIntegerLiteral(node.foldedConstInitializer);
        }

        if (sym.isHeap) {
            emitLine(cppType + "* " + name + " = static_cast<" + cppType + "*>(je_malloc(sizeof(" + cppType + ")));");
            emitLine("if (!" + name + ") throw std::bad_alloc();");
            emitLine("new (" + name + ") " + cppType + ";");

            if (sym.type instanceof SemanticAddrType) {
                /*
                 * Heap storage holding an address is null-initialized so that
                 * deref of a never-assigned heap address is a well-defined
                 * null dereference instead of undefined behavior.
                 */
                emitLine("*" + name + " = nullptr;");
            }
        } else {
            /*
             * Address declarations are guaranteed null-initialized, unlike
             * ordinary declarations which are left uninitialized.
             */
            if (initializer.isEmpty()
                    && sym.type instanceof SemanticAddrType) {
                initializer = " = nullptr";
            }

            emitLine(cppType + " " + name + initializer + ";");
        }
    }

    private void emitAssign(SemanticAssignNode node) {
        emitLine(
                emitLValue(node.target)
                        + " = "
                        + emitExprAsType(node.value, node.target.type)
                        + ";"
        );
    }

    private void emitIf(SemanticIfNode node) {
        emitLine("if (" + emitExpr(node.condition) + ")");
        emitControlledStmt(node.thenBranch);

        if (node.elseBranch != null) {
            emitLine("else");
            emitControlledStmt(node.elseBranch);
        }
    }

    private void emitWhile(SemanticWhileNode node) {
        emitLine("while (" + emitExpr(node.condition) + ")");
        emitControlledStmt(node.body);
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

    private void emitDel(SemanticDelNode node) {
        VariableSymbol sym = node.symbol;

        if (!sym.isHeap) {
            throw new IllegalStateException(
                    "Internal compiler error: non-heap del reached codegen for variable '"
                            + sym.name
                            + "'. MasterChecker should have rejected this."
            );
        }

        /*
         * Scalars (primitives and addresses) have no destructors in C++14,
         * and a pseudo-destructor call cannot spell a pointer type. Only
         * class-type pointees (arrays, structs) need explicit destruction
         * before the storage is freed.
         */
        if (sym.type instanceof SemanticDynamicArrayType
                || sym.type instanceof SemanticStaticArrayType
                || sym.type instanceof SemanticStructType) {
            emitLine(emitSymbolName(sym) + "->~" + emitType(sym.type) + "();");
        }

        emitLine("je_free(" + emitSymbolName(sym) + ");");
        emitLine(emitSymbolName(sym) + " = nullptr;");
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

        emitLine("return " + emitExprAsType(node.value, targetType) + ";");
    }

    // ============================================================
    // Expressions
    // ============================================================

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
            /*
             * Franko null -> C++ nullptr.
             */
            return "nullptr";
        }

        if (node instanceof SemanticVarExprNode var) {
            return emitVarAccess(var.symbol);
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

        if (node instanceof SemanticGetAddrNode getAddr) {
            return emitGetAddr(getAddr);
        }

        if (node instanceof SemanticDerefNode deref) {
            return emitDeref(deref);
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
        /*
         * Preserve exact source spelling:
         *
         *   123
         *   0b1010
         *   0xff
         *
         * Binary and hexadecimal literals are valid in C++14 and fall back to
         * unsigned long long when large.
         *
         * An unsuffixed decimal literal whose value exceeds LLONG_MAX is
         * ill-formed in C++14 because decimal literals do not fall back to
         * unsigned integer types. Append a ULL suffix so the surrounding
         * static_cast<T>(...) keeps the generated code well-formed.
         */
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

    /**
     * Emits a folded BigInteger constant as a well-formed C++14 integer
     * literal.
     *
     * Plain decimal literals whose magnitude exceeds LLONG_MAX get a ULL
     * suffix so the literal itself remains well-formed C++14; decimal
     * literals in C++14 do not fall back to unsigned integer types.
     */
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
        /*
         * Folded constant expressions are emitted as their folded value
         * directly. This avoids emitting C++ expressions whose evaluation
         * would be undefined behavior (for example constant shifts such as
         * 1 << 40), and matches the Franko BigInteger folding semantics.
         *
         * No cast to the node's fallback type is emitted here: constants are
         * fluid in Franko, and value-consuming contexts cast through
         * emitExprAsType(...), exactly like emitIntLiteral(...).
         */
        if (node.isConstant() && node.constantValue != null) {
            return emitBigIntegerLiteral(node.constantValue);
        }

        String inner = emitExpr(node.expr);
        String raw = "(" + node.op + inner + ")";

        return castToSemanticExprTypeIfNeeded(node, raw);
    }

    private String emitBinOp(SemanticBinOpNode node) {
        /*
         * Folded constant expressions are emitted as their folded value
         * directly. This avoids emitting C++ expressions whose evaluation
         * would be undefined behavior (for example constant shifts such as
         * 1 << 40), and matches the Franko BigInteger folding semantics.
         *
         * No cast to the node's fallback type is emitted here: constants are
         * fluid in Franko, and value-consuming contexts cast through
         * emitExprAsType(...), exactly like emitIntLiteral(...).
         */
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

            /*
             * This cast is essential for preserving Franko overload resolution.
             *
             * Franko has already selected exactly one overload. C++ overload
             * resolution should not get a chance to reinterpret literal or
             * folded-constant arguments differently.
             */
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
                    /*
                     * memcpy source is addr<array<...>>.
                     *
                     * Example:
                     *
                     *   dst.memcpy(getaddr(src))
                     *
                     * emits:
                     *
                     *   dst.memcpy((&src))
                     *
                     * The runtime expects a pointer to the source array object.
                     */
                    yield emitExpr(arg);
                }

                yield emitExprAsType(arg, UINT32_TYPE);
            }

            case MEMMOVE ->
                    emitExprAsType(arg, UINT32_TYPE);
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

    private String emitGetAddr(SemanticGetAddrNode node) {
        /*
         * Franko getaddr(lvalue) -> C++ &lvalue
         */
        return "(&" + emitLValue(node.target) + ")";
    }

    private String emitDeref(SemanticDerefNode node) {
        /*
         * Franko deref(p) -> C++ (*p)
         */
        return "(*" + emitExpr(node.expr) + ")";
    }

    // ============================================================
    // LValues
    // ============================================================

    private String emitLValue(SemanticExprNode node) {
        if (node instanceof SemanticVarExprNode var) {
            return emitVarAccess(var.symbol);
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

    /**
     * Emits a struct member access:
     *
     *   obj.field          ->  obj.field
     *   alloc X s; s.field ->  (*s).field
     *   deref(p).field     ->  (*p).field
     *
     * Heap variables lower to (*sym) through emitVarAccess, so heap struct
     * field access composes without special handling here.
     */
    private String emitMemberAccess(SemanticMemberAccessNode node) {
        return emitExpr(node.target) + "." + node.field.name;
    }

    // ============================================================
    // Types
    // ============================================================

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

        if (type instanceof SemanticAddrType addr) {
            return emitType(addr.referencedType) + "*";
        }

        if (type instanceof SemanticStructType struct) {
            /*
             * Struct types are emitted with a "STRUCT_" prefix so a struct
             * name can never collide with a variable or function name in the
             * generated C++.
             */
            return emitStructName(struct.structSymbol);
        }

        if (type instanceof SemanticNullType) {
            /*
             * The null type is the type of the null literal only. It is never
             * a declared variable, field, parameter, or return type, so it
             * must not reach type emission.
             */
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

    // ============================================================
    // Symbol / Name Emission
    // ============================================================

    private String emitVarAccess(VariableSymbol symbol) {
        if (symbol == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null symbol in codegen"
            );
        }

        if (symbol.isHeap) {
            return "(*" + emitSymbolName(symbol) + ")";
        }

        return emitSymbolName(symbol);
    }

    private String emitSymbolName(VariableSymbol symbol) {
        /*
         * Variables (locals, globals, parameters, heap variables) are emitted
         * with a "VAR_" prefix.
         *
         * Franko keeps variables, functions, and struct names in separate
         * namespaces, but C++ does not: a raw name such as "X" would collide
         * with a struct type of the same name in the generated code. The kind
         * prefixes keep every Franko namespace distinct in C++.
         *
         * Only namespace-level variables (globals) can need cross-namespace
         * qualification; locals and parameters are function-scoped and are
         * never qualified.
         */
        if (symbol instanceof GlobalVariableSymbol global) {
            return qualify(global.namespace, "VAR_" + symbol.name);
        }

        return "VAR_" + symbol.name;
    }

    private String emitFunctionName(FunctionSymbol symbol) {
        /*
         * Functions are emitted with a "FUNC_" prefix for the same
         * cross-namespace reason as variables.
         *
         * Exception: the Franko entry-point function "main" keeps its raw
         * name, because the generated program's C++ entry point IS the
         * Franko main function. main must be declared in the global
         * namespace; the semantic analyzer rejects a namespaced main.
         */
        if ("main".equals(symbol.name) && symbol.namespace == null) {
            return symbol.name;
        }

        return qualify(symbol.namespace, "FUNC_" + symbol.name);
    }

    /**
     * Emits a struct type name, qualified when the struct lives in
     * another namespace than the one currently being emitted.
     */
    private String emitStructName(StructSymbol symbol) {
        return qualify(symbol.namespace, "STRUCT_" + symbol.name);
    }

    /**
     * Qualifies a symbol's local C++ name for the emission context.
     *
     *   - same namespace as the emission context (or both global):
     *     the local name is used as-is,
     *
     *   - symbol in another namespace: "NS_ns::" + localName,
     *
     *   - global-namespace symbol referenced from inside a namespace
     *     block: "::" + localName, so the reference cannot be captured
     *     by a same-named symbol in that namespace.
     */
    private String qualify(String symbolNamespace, String localName) {
        if (symbolNamespace == null) {
            return currentEmitNamespace == null
                    ? localName
                    : "::" + localName;
        }

        if (symbolNamespace.equals(currentEmitNamespace)) {
            return localName;
        }

        return "NS_" + symbolNamespace + "::" + localName;
    }

    /**
     * Opens a C++ namespace block for emission and switches the
     * qualification context.
     *
     * The emitted C++ namespace is prefixed (NS_name) while
     * currentEmitNamespace keeps the Franko name, so qualification
     * comparisons stay in Franko terms.
     *
     * Null is a no-op: global-namespace items are emitted at global
     * scope.
     */
    private void openNamespace(String namespaceName) {
        if (namespaceName == null) {
            return;
        }

        emitLine("namespace NS_" + namespaceName);
        emitLine("{");
        indentLevel++;
        currentEmitNamespace = namespaceName;
    }

    /**
     * Closes a C++ namespace block opened by openNamespace and restores
     * the global emission context.
     *
     * Null is a no-op, matching openNamespace.
     */
    private void closeNamespace(String namespaceName) {
        if (namespaceName == null) {
            return;
        }

        indentLevel--;
        emitLine("}");
        currentEmitNamespace = null;
    }

    // ============================================================
    // Casts / Contextual Emission
    // ============================================================

    /**
     * Emits an expression in a context that expects targetType.
     *
     * This is used for:
     *
     *   - assignments,
     *   - returns,
     *   - function call arguments,
     *   - array sizes,
     *   - array indexes,
     *   - memset values,
     *   - ranged memset/memcpy/memmove arguments.
     *
     * Primitive casts are emitted explicitly to preserve Franko's contextual
     * constant typing and overload resolution.
     */
    private String emitExprAsType(
            SemanticExprNode expr,
            SemanticType targetType
    ) {
        String raw = emitExpr(expr);

        if (targetType instanceof SemanticPrimitiveType) {
            /*
             * Constants are fluid in Franko, so keep contextual casts for them.
             *
             * Examples:
             *
             *   uint8_t x;
             *   x = 1;
             *
             * should emit:
             *
             *   x = static_cast<uint8_t>(1);
             *
             * Function call arguments also rely on contextual primitive casts to
             * preserve Franko overload resolution.
             */
            if (expr.isConstant()) {
                return "static_cast<" + emitType(targetType) + ">(" + raw + ")";
            }

            /*
             * If the expression is already semantically the requested primitive
             * type, do not wrap it again.
             *
             * This avoids:
             *
             *   static_cast<uint32_t>(static_cast<uint32_t>((x + 1)))
             *
             * because emitBinOp(...) already emitted:
             *
             *   static_cast<uint32_t>((x + 1))
             */
            if (sameSemanticType(expr.type, targetType)) {
                return raw;
            }

            return "static_cast<" + emitType(targetType) + ">(" + raw + ")";
        }

        /*
         * Do not cast arrays or addresses unless Franko later defines explicit
         * conversions for them. Address arguments/returns are already strongly
         * typed and checked exactly.
         */
        return raw;
    }

    private String castToSemanticExprTypeIfNeeded(
            SemanticExprNode node,
            String raw
    ) {
        if (node.type instanceof SemanticPrimitiveType) {
            return "static_cast<" + emitType(node.type) + ">(" + raw + ")";
        }

        /*
         * Do not cast addresses or arrays.
         */
        return raw;
    }

    // ============================================================
    // Print Helpers
    // ============================================================

    private String emitPrintableExpr(SemanticExprNode expr) {
        String emitted = emitExpr(expr);

        if (expr.type instanceof SemanticPrimitiveType primitive) {
            /*
             * int8_t / uint8_t are usually aliases of char-like types.
             * Unary plus promotes them so std::cout prints a number rather than
             * a character.
             */
            if (primitive.kind == SemanticPrimitiveKind.INT8
                    || primitive.kind == SemanticPrimitiveKind.UINT8) {
                return "(+(" + emitted + "))";
            }

            return emitted;
        }

        if (expr.type instanceof SemanticAddrType) {
            /*
             * Avoid accidental char-pointer string printing for addresses.
             */
            return "static_cast<const void*>(" + emitted + ")";
        }

        if (expr instanceof SemanticNullLiteralNode) {
            /*
             * print(null) prints the null address as a pointer value.
             */
            return "static_cast<const void*>(" + emitted + ")";
        }

        return emitted;
    }

    // ============================================================
    // Output Helpers
    // ============================================================

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