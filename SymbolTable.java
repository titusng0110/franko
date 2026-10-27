import java.math.BigInteger;
import java.util.*;

/**
 * ============================================================================
 * SYMBOL TABLE
 * Scope Management, Symbol Registration, and Function Overload Resolution
 * ============================================================================
 *
 * This class is the refactored form of SemanticAnalyzer.Context.
 *
 * It owns:
 *
 *   - lexical variable scopes,
 *   - global VarDeclNode -> VariableSymbol registration metadata,
 *   - function overload tables,
 *   - FunctionDeclNode -> FunctionSymbol registration metadata.
 *
 * Diagnostics are delegated to DiagnosticBag.
 */
public final class SymbolTable {

    // ============================================================
    // Scope State
    // ============================================================

    private final Deque<Map<String, VariableSymbol>> scopes =
            new ArrayDeque<>();

    /*
     * Global-namespace variables, in declaration order.
     *
     * Globals live here rather than in the bottom lexical scope so
     * unqualified lookup can implement the namespace chain exactly:
     * lexical scopes (locals) -> current namespace globals -> global
     * namespace globals.
     */
    private final Map<String, VariableSymbol> globalVariables =
            new LinkedHashMap<>();

    /*
     * Raw global VarDeclNode -> registered global symbol.
     *
     * Used so global declarations are registered before function body
     * analysis, then reused when lowering the final SemanticProgramNode.
     */
    private final IdentityHashMap<VarDeclNode, VariableSymbol> varDeclSymbols =
            new IdentityHashMap<>();

    // ============================================================
    // Function State
    // ============================================================

    /*
     * Function overload table.
     *
     * Declaration identity:
     *
     *   function name + ordered parameter types
     *
     * Parameter names and return type are not part of the signature.
     */
    private final Map<String, List<FunctionSymbol>> functions =
            new LinkedHashMap<>();

    /*
     * Raw FunctionDeclNode -> registered FunctionSymbol.
     *
     * Used for two-phase function analysis:
     *
     *   1. register all signatures,
     *   2. analyze bodies using the exact registered symbol.
     */
    private final IdentityHashMap<FunctionDeclNode, FunctionSymbol> functionDeclSymbols =
            new IdentityHashMap<>();

    // ============================================================
    // Struct State
    // ============================================================

    /*
     * Type-name namespace for struct declarations.
     *
     * Kept separate from variable scopes and the function overload table:
     * struct names live in their own namespace, so a struct may share a name
     * with a variable or a function.
     */
    private final Map<String, StructSymbol> structs =
            new LinkedHashMap<>();

    /*
     * Names of local variables and parameters declared through declare(),
     * across all lexical scopes. Used only for name-clash warnings;
     * scopes themselves remain the source of truth for visibility.
     */
    private final Set<String> declaredVariableNames =
            new HashSet<>();

    /*
     * Names of namespace-level variables declared in the global
     * namespace. Kept separate from declaredVariableNames and from each
     * namespace's variableNames so clash warnings stay scoped to one
     * namespace.
     */
    private final Set<String> globalNamespaceVariableNames =
            new HashSet<>();

    // ============================================================
    // Namespace State
    // ============================================================

    /*
     * Declared top-level namespaces, in first-declaration order.
     *
     * Nested namespaces live in their parent's children map, not
     * here. Redeclaring a path merges into the existing symbol
     * chain.
     */
    private final Map<String, NamespaceSymbol> namespaces =
            new LinkedHashMap<>();

    /*
     * The namespace whose items are currently being analyzed, or null
     * while the global namespace is being analyzed.
     *
     * Registration and lookup APIs route through this context so
     * SemanticAnalyzer does not have to pass the namespace explicitly
     * on every call.
     */
    private NamespaceSymbol currentNamespace;

    // ============================================================
    // Diagnostics
    // ============================================================

    private final DiagnosticBag diagnostics;

    public SymbolTable() {
        this(new DiagnosticBag("Semantic analysis failed:"));
    }

    public SymbolTable(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    /**
     * Clears symbol-table state and diagnostics.
     *
     * This preserves the old Context/SymbolTable behavior.
     */
    public void clear() {
        clearSymbols();
        diagnostics.clear();
    }

    /**
     * Clears only scope/function/declaration registration state.
     *
     * Useful if a caller wants to preserve diagnostics.
     */
    public void clearSymbols() {
        scopes.clear();
        varDeclSymbols.clear();
        functions.clear();
        functionDeclSymbols.clear();
        structs.clear();
        declaredVariableNames.clear();
        globalNamespaceVariableNames.clear();
        namespaces.clear();
        currentNamespace = null;
    }

    /**
     * Clears only diagnostics.
     *
     * Useful for checker phases that use SymbolTable as a shared diagnostic
     * context but do not want to disturb symbol state.
     */
    public void clearErrors() {
        diagnostics.clear();
    }

    // ============================================================
    // Namespaces
    // ============================================================

    /**
     * Returns the namespace with the given full path, creating it (and
     * any missing intermediate namespaces) on first declaration so
     * later declarations of the same path merge into the same symbol.
     *
     * Both declaration forms funnel through the path: "a::b" from a
     * path header and "b" nested inside "a" resolve to the same
     * symbol.
     */
    public NamespaceSymbol declareNamespace(String path) {
        List<String> segments = splitNamespacePath(path);

        NamespaceSymbol current = null;

        for (String segment : segments) {
            if (current == null) {
                NamespaceSymbol root = namespaces.get(segment);

                if (root == null) {
                    root = new NamespaceSymbol(segment, null);
                    namespaces.put(segment, root);
                }

                current = root;
            } else {
                NamespaceSymbol child = current.children.get(segment);

                if (child == null) {
                    child = new NamespaceSymbol(
                            current.name + "::" + segment,
                            current
                    );
                    current.children.put(segment, child);
                }

                current = child;
            }
        }

        return current;
    }

    /**
     * Resolves a namespace by full path, or null when no namespace
     * with that path was declared.
     */
    public NamespaceSymbol resolveNamespace(String path) {
        return resolveNamespace(splitNamespacePath(path));
    }

    /**
     * Resolves a namespace by path segments, or null when no
     * namespace with that path was declared. An empty segment list
     * never resolves.
     */
    public NamespaceSymbol resolveNamespace(List<String> segments) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }

        NamespaceSymbol current = namespaces.get(segments.get(0));

        for (int i = 1; i < segments.size() && current != null; i++) {
            current = current.children.get(segments.get(i));
        }

        return current;
    }

    /**
     * Splits a namespace path ("a::b") into its segments.
     */
    public static List<String> splitNamespacePath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException(
                    "Internal error: empty namespace path");
        }

        String[] parts = path.split("::", -1);

        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException(
                        "Internal error: malformed namespace path '"
                                + path
                                + "'");
            }
        }

        return List.of(parts);
    }

    /**
     * All declared namespaces in first-declaration order, depth-first:
     * each namespace is followed by its nested namespaces.
     */
    public Collection<NamespaceSymbol> allNamespaces() {
        List<NamespaceSymbol> out = new ArrayList<>();

        for (NamespaceSymbol root : namespaces.values()) {
            collectNamespaceTree(root, out);
        }

        return List.copyOf(out);
    }

    private static void collectNamespaceTree(
            NamespaceSymbol symbol,
            List<NamespaceSymbol> out
    ) {
        out.add(symbol);

        for (NamespaceSymbol child : symbol.children.values()) {
            collectNamespaceTree(child, out);
        }
    }

    /**
     * Sets the namespace whose items are currently being analyzed.
     *
     * Registration and lookup route through this context. Null selects
     * the global namespace.
     */
    public void setCurrentNamespace(NamespaceSymbol namespace) {
        this.currentNamespace = namespace;
    }

    public NamespaceSymbol currentNamespace() {
        return currentNamespace;
    }

    // ============================================================
    // Struct Namespace
    // ============================================================

    /**
     * Declares a struct by name in the type-name namespace of the
     * namespace currently being analyzed (or the global namespace).
     *
     * Duplicate struct names are rejected within the same namespace;
     * nominal typing means each declaration is a distinct type.
     *
     * @return true when the struct was registered.
     */
    public boolean declareStruct(StructSymbol symbol) {
        Map<String, StructSymbol> target = targetStructs();

        StructSymbol existing = target.get(symbol.name);

        if (existing != null) {
            error("Duplicate declaration of struct '" + symbol.qualifiedName() + "'");
            return false;
        }

        target.put(symbol.name, symbol);
        return true;
    }

    /**
     * Resolves a struct name with the namespace chain: the namespace
     * currently being analyzed first, then the global namespace.
     */
    public StructSymbol resolveStruct(String name) {
        if (currentNamespace != null) {
            StructSymbol sym = currentNamespace.structs.get(name);

            if (sym != null) {
                return sym;
            }
        }

        return structs.get(name);
    }

    /**
     * Resolves a struct name strictly inside one namespace, with no
     * fallback to the global namespace.
     */
    public StructSymbol resolveStructIn(NamespaceSymbol namespace, String name) {
        if (namespace == null) {
            return structs.get(name);
        }

        return namespace.structs.get(name);
    }

    private Map<String, StructSymbol> targetStructs() {
        return currentNamespace == null
                ? structs
                : currentNamespace.structs;
    }

    public DiagnosticBag diagnostics() {
        return diagnostics;
    }

    public void pushScope() {
        scopes.push(new LinkedHashMap<>());
    }

    public void popScope() {
        scopes.pop();
    }

    public boolean declare(VariableSymbol sym) {
        Map<String, VariableSymbol> current = scopes.peek();

        if (current == null) {
            throw new IllegalStateException("Internal error: no active scope.");
        }

        if (current.containsKey(sym.name)) {
            error("Duplicate declaration of variable '" + sym.qualifiedName() + "' in the same scope");
            return false;
        }

        current.put(sym.name, sym);
        declaredVariableNames.add(sym.name);
        return true;
    }

    /**
     * Declares a namespace-level variable (a global) into the global
     * namespace, or into the namespace currently being analyzed.
     *
     * Duplicate names are rejected within the same namespace.
     *
     * @return true when the variable was registered.
     */
    public boolean declareGlobalVariable(VariableSymbol sym) {
        Map<String, VariableSymbol> target = targetGlobalVariables();

        if (target.containsKey(sym.name)) {
            error("Duplicate declaration of variable '" + sym.qualifiedName() + "'");
            return false;
        }

        target.put(sym.name, sym);

        if (currentNamespace == null) {
            globalNamespaceVariableNames.add(sym.name);
        } else {
            currentNamespace.variableNames.add(sym.name);
        }

        return true;
    }

    /**
     * Resolves a variable name with the full lookup chain:
     *
     *   1. lexical scopes (locals and parameters),
     *   2. the namespace currently being analyzed, if any,
     *   3. the global namespace.
     */
    public VariableSymbol resolve(String name) {
        for (Map<String, VariableSymbol> scope : scopes) {
            VariableSymbol sym = scope.get(name);

            if (sym != null) {
                return sym;
            }
        }

        if (currentNamespace != null) {
            VariableSymbol sym = currentNamespace.globalVariables.get(name);

            if (sym != null) {
                return sym;
            }
        }

        return globalVariables.get(name);
    }

    /**
     * Resolves a variable name strictly inside one namespace, with no
     * fallback to the global namespace.
     */
    public VariableSymbol resolveGlobalVarIn(NamespaceSymbol namespace, String name) {
        if (namespace == null) {
            return globalVariables.get(name);
        }

        return namespace.globalVariables.get(name);
    }

    private Map<String, VariableSymbol> targetGlobalVariables() {
        return currentNamespace == null
                ? globalVariables
                : currentNamespace.globalVariables;
    }

    public void registerVarDeclSymbol(
            VarDeclNode decl,
            VariableSymbol sym
    ) {
        varDeclSymbols.put(decl, sym);
    }

    public VariableSymbol findRegisteredVarDeclSymbol(VarDeclNode decl) {
        return varDeclSymbols.get(decl);
    }

    public void declareFunction(FunctionDeclNode decl, FunctionSymbol fn) {
        Map<String, List<FunctionSymbol>> targetFunctions =
                currentNamespace == null ? functions : currentNamespace.functions;

        IdentityHashMap<FunctionDeclNode, FunctionSymbol> targetDeclSymbols =
                currentNamespace == null
                        ? functionDeclSymbols
                        : currentNamespace.functionDeclSymbols;

        List<FunctionSymbol> overloads =
                targetFunctions.computeIfAbsent(fn.name, ignored -> new ArrayList<>());

        for (FunctionSymbol existing : overloads) {
            if (sameParameterSignature(existing.parameters, fn.parameters)) {
                error("Duplicate declaration of function '"
                        + fn.signatureString()
                        + "' in the same scope");
                return;
            }
        }

        overloads.add(fn);
        targetDeclSymbols.put(decl, fn);
    }

    /**
     * Finds the FunctionSymbol registered for a raw declaration.
     *
     * Looks in the namespace currently being analyzed first, then in the
     * global namespace, mirroring the registration route.
     */
    public FunctionSymbol findRegisteredFunction(FunctionDeclNode decl) {
        if (currentNamespace != null) {
            FunctionSymbol sym = currentNamespace.functionDeclSymbols.get(decl);

            if (sym != null) {
                return sym;
            }
        }

        return functionDeclSymbols.get(decl);
    }

    /**
     * Declares a method in its owning struct's method table.
     *
     * Methods live only on the struct; they are never registered as free
     * functions, so `increment(...)` is never callable without a receiver.
     */
    public void declareMethod(
            StructSymbol owner,
            FunctionDeclNode decl,
            MethodSymbol method
    ) {
        if (owner.hasFieldName(method.name)) {
            error("Method '"
                    + method.qualifiedName()
                    + "' conflicts with field '"
                    + method.name
                    + "' of struct '"
                    + owner.qualifiedName()
                    + "'");
            return;
        }

        List<MethodSymbol> overloads = owner.methods.computeIfAbsent(
                method.name,
                ignored -> new ArrayList<>()
        );

        for (MethodSymbol existing : overloads) {
            if (sameParameterSignature(existing.parameters, method.parameters)) {
                error("Duplicate declaration of method '"
                        + method.signatureString()
                        + "' in struct '"
                        + owner.qualifiedName()
                        + "'");
                return;
            }
        }

        overloads.add(method);
        owner.methodDeclSymbols.put(decl, method);
    }

    /**
     * Finds the MethodSymbol registered for a raw method declaration.
     */
    public MethodSymbol findRegisteredMethod(
            StructSymbol owner,
            FunctionDeclNode decl
    ) {
        return owner.methodDeclSymbols.get(decl);
    }

    /**
     * Resolves a method call against one struct's method overloads.
     *
     * Returns null when no overload applies; reports ambiguity when
     * folded constants fit multiple overloads.
     */
    public MethodSymbol resolveMethod(
            StructSymbol owner,
            String name,
            List<SemanticExprNode> args
    ) {
        List<MethodSymbol> overloads = owner.methods.get(name);

        return resolveMethodFromOverloads(owner, name, overloads, args);
    }

    private MethodSymbol resolveMethodFromOverloads(
            StructSymbol owner,
            String name,
            List<MethodSymbol> overloads,
            List<SemanticExprNode> args
    ) {
        if (overloads == null || overloads.isEmpty()) {
            return null;
        }

        List<MethodSymbol> matches = new ArrayList<>();

        for (MethodSymbol m : overloads) {
            if (isApplicableFunction(m, args)) {
                matches.add(m);
            }
        }

        if (matches.size() == 1) {
            return matches.get(0);
        }

        List<SemanticType> argTypes = args.stream()
                .map(arg -> arg == null ? null : arg.type)
                .toList();

        if (matches.size() > 1) {
            error("Ambiguous call to overloaded method '"
                    + owner.qualifiedName()
                    + "."
                    + formatCallSignature(name, argTypes)
                    + "': argument constants fit multiple overloads");

            return matches.get(0);
        }

        return null;
    }

    /**
     * Resolves a function call with the namespace chain.
     *
     * When the namespace currently being analyzed declares any overload
     * of the name, only those overloads are considered; otherwise the
     * global namespace's overloads are used.
     */
    public FunctionSymbol resolveFunction(
            String name,
            List<SemanticExprNode> args
    ) {
        List<FunctionSymbol> overloads;

        if (currentNamespace != null
                && currentNamespace.functions.containsKey(name)) {
            overloads = currentNamespace.functions.get(name);
        } else {
            overloads = functions.get(name);
        }

        return resolveFromOverloads(name, overloads, args);
    }

    /**
     * Resolves a function call strictly inside one namespace, with no
     * fallback to the global namespace.
     */
    public FunctionSymbol resolveFunctionIn(
            NamespaceSymbol namespace,
            String name,
            List<SemanticExprNode> args
    ) {
        List<FunctionSymbol> overloads = namespace == null
                ? functions.get(name)
                : namespace.functions.get(name);

        return resolveFromOverloads(name, overloads, args);
    }

    private FunctionSymbol resolveFromOverloads(
            String name,
            List<FunctionSymbol> overloads,
            List<SemanticExprNode> args
    ) {
        if (overloads == null || overloads.isEmpty()) {
            return null;
        }

        List<FunctionSymbol> matches = new ArrayList<>();

        for (FunctionSymbol fn : overloads) {
            if (isApplicableFunction(fn, args)) {
                matches.add(fn);
            }
        }

        if (matches.size() == 1) {
            return matches.get(0);
        }

        List<SemanticType> argTypes = args.stream()
                .map(arg -> arg == null ? null : arg.type)
                .toList();

        if (matches.size() > 1) {
            error("Ambiguous call to overloaded function '"
                    + formatCallSignature(name, argTypes)
                    + "': argument constants fit multiple overloads");

            /*
             * Return one candidate as fallback so analysis can continue.
             * The call is already invalid because an error was reported.
             */
            return matches.get(0);
        }

        return null;
    }

    public boolean hasAnyFunctionNamed(String name) {
        if (currentNamespace != null
                && currentNamespace.functions.containsKey(name)) {
            return true;
        }

        List<FunctionSymbol> overloads = functions.get(name);
        return overloads != null && !overloads.isEmpty();
    }

    /**
     * Whether one namespace declares any overload of the given name,
     * with no fallback to the global namespace.
     */
    public boolean hasAnyFunctionNamedIn(NamespaceSymbol namespace, String name) {
        List<FunctionSymbol> overloads = namespace == null
                ? functions.get(name)
                : namespace.functions.get(name);

        return overloads != null && !overloads.isEmpty();
    }

    public List<FunctionSymbol> getFunctionsByName(String name) {
        return List.copyOf(functions.getOrDefault(name, List.of()));
    }

    public Collection<FunctionSymbol> allFunctions() {
        List<FunctionSymbol> out = new ArrayList<>();

        for (List<FunctionSymbol> overloads : functions.values()) {
            out.addAll(overloads);
        }

        return List.copyOf(out);
    }

    // ============================================================
    // Name-Clash Warnings
    // ============================================================

    /**
     * Returns the names of all declared structs.
     */
    public Set<String> structNames() {
        return Set.copyOf(structs.keySet());
    }

    /**
     * Records a non-fatal warning.
     *
     * Warnings never fail compilation; the driver prints them after a
     * successful semantic analysis.
     */
    public void warning(String msg) {
        diagnostics.warning(msg);
    }

    public List<String> warnings() {
        return diagnostics.getWarnings();
    }

    public boolean hasWarnings() {
        return diagnostics.hasWarnings();
    }

    /**
     * Warns when a declared variable or function shares its name with a
     * declared struct.
     *
     * Franko keeps struct names in a separate namespace, so this is legal.
     * The C++ backend emits kind-prefixed names (VAR_X, FUNC_f_HASH_...,
     * STRUCT_X), so the generated code is unambiguous, but the shared
     * source name is usually confusing and worth calling out.
     *
     * Called once at the end of program analysis, when struct, variable, and
     * function names are all registered.
     */
    public void warnStructNameClashes() {
        /*
         * Global space: consider local and parameter names as well as
         * global-level variable names, preserving the original behavior
         * for programs without namespaces. Namespace-level variables are
         * deliberately excluded: they belong to their own namespace.
         */
        Set<String> globalSpaceVariableNames =
                new HashSet<>(declaredVariableNames);
        globalSpaceVariableNames.addAll(globalNamespaceVariableNames);

        warnStructNameClashesInSpace(
                structs,
                functions,
                globalSpaceVariableNames
        );

        /*
         * Each namespace is a separate space: only that namespace's own
         * namespace-level variables can clash with its structs. Variables
         * and structs in other namespaces are unrelated symbols.
         */
        for (NamespaceSymbol namespace : allNamespaces()) {
            warnStructNameClashesInSpace(
                    namespace.structs,
                    namespace.functions,
                    namespace.variableNames
            );
        }

        /*
         * A namespace name shares its enclosing scope with that
         * scope's struct, function, and variable names. This is legal
         * (Franko namespaces are their own name space), but the shared
         * name is usually confusing and worth calling out. Reported
         * names are fully qualified, so a clash with the parent scope
         * reads as "Namespace 'a::b' shares its name with struct
         * 'a::b'".
         */
        for (NamespaceSymbol namespace : allNamespaces()) {
            warnNamespaceNameClash(namespace);
        }
    }

    /**
     * Warns when a namespace's simple name collides with a struct,
     * function, or variable name declared in its enclosing scope (the
     * global namespace for top-level namespaces, the parent namespace
     * otherwise).
     */
    private void warnNamespaceNameClash(NamespaceSymbol namespace) {
        Map<String, StructSymbol> scopeStructs;
        Map<String, List<FunctionSymbol>> scopeFunctions;
        Set<String> scopeVariableNames;

        if (namespace.parent == null) {
            scopeStructs = structs;
            scopeFunctions = functions;
            scopeVariableNames = globalNamespaceVariableNames;
        } else {
            scopeStructs = namespace.parent.structs;
            scopeFunctions = namespace.parent.functions;
            scopeVariableNames = namespace.parent.variableNames;
        }

        String simpleName = namespace.simpleName();

        /*
         * The clash partner's qualified name always equals this
         * namespace's full path: parent path + "::" + simple name, or
         * the simple name itself at the top level.
         */
        String qualifiedClashName = namespace.name;

        if (scopeStructs.containsKey(simpleName)) {
            warning("Namespace '"
                    + qualifiedClashName
                    + "' shares its name with struct '"
                    + qualifiedClashName
                    + "'");
        }

        if (scopeFunctions.containsKey(simpleName)) {
            warning("Namespace '"
                    + qualifiedClashName
                    + "' shares its name with function '"
                    + qualifiedClashName
                    + "'");
        }

        if (scopeVariableNames.contains(simpleName)) {
            warning("Namespace '"
                    + qualifiedClashName
                    + "' shares its name with a variable '"
                    + qualifiedClashName
                    + "'");
        }
    }

    /**
     * Warns when a struct in one namespace shares its name with a
     * variable or function declared in that same namespace.
     */
    private void warnStructNameClashesInSpace(
            Map<String, StructSymbol> spaceStructs,
            Map<String, List<FunctionSymbol>> spaceFunctions,
            Set<String> spaceVariableNames
    ) {
        for (StructSymbol structSymbol : spaceStructs.values()) {
            String structName = structSymbol.qualifiedName();

            if (spaceVariableNames.contains(structSymbol.name)) {
                warning("Variable '"
                        + structName
                        + "' shares its name with struct '"
                        + structName
                        + "'; generated C++ uses VAR_"
                        + structSymbol.name
                        + " for the variable and STRUCT_"
                        + structSymbol.name
                        + " for the struct type");
            }

            if (spaceFunctions.containsKey(structSymbol.name)) {
                List<FunctionSymbol> clashFunctions =
                        spaceFunctions.get(structSymbol.name);

                StringBuilder emitted = new StringBuilder();

                for (int i = 0; i < clashFunctions.size(); i++) {
                    if (i > 0) {
                        emitted.append(", ");
                    }

                    emitted.append(clashFunctions.get(i).emittedLocalName());
                }

                warning("Function '"
                        + structName
                        + "' shares its name with struct '"
                        + structName
                        + "'; generated C++ uses "
                        + emitted
                        + " for the function and STRUCT_"
                        + structSymbol.name
                        + " for the struct type");
            }
        }
    }

    private static boolean sameParameterSignature(
            List<ParameterSymbol> a,
            List<ParameterSymbol> b
    ) {
        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            ParameterSymbol left = a.get(i);
            ParameterSymbol right = b.get(i);

            if (!sameType(left.type, right.type)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isApplicableFunction(
            FunctionSymbol fn,
            List<SemanticExprNode> args
    ) {
        if (fn.arity() != args.size()) {
            return false;
        }

        List<SemanticType> parameterTypes = fn.parameterTypes();

        for (int i = 0; i < args.size(); i++) {
            SemanticType parameterType = parameterTypes.get(i);
            SemanticExprNode arg = args.get(i);

            if (!isArgumentApplicableToParameter(arg, parameterType)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isArgumentApplicableToParameter(
            SemanticExprNode arg,
            SemanticType parameterType
    ) {
        if (arg == null || parameterType == null || arg.type == null) {
            return false;
        }

        /*
         * A null literal argument applies only to pointer parameters.
         */
        if (arg.type instanceof SemanticNullType) {
            return parameterType instanceof SemanticPtrType;
        }

        /*
         * Folded integer constants are fluid during call resolution.
         *
         * A constant argument may match any primitive integer parameter
         * whose range can represent the constant value.
         */
        if (arg.isConstant()
                && parameterType instanceof SemanticPrimitiveType primitive) {
            return fitsBigIntegerToPrimitive(
                    arg.constantValue,
                    primitive.kind
            );
        }

        /*
         * Nonconstant expressions must match exactly.
         */
        return sameType(arg.type, parameterType);
    }

    private static boolean fitsBigIntegerToPrimitive(
            BigInteger value,
            SemanticPrimitiveKind kind
    ) {
        if (value == null || kind == null) {
            return false;
        }

        int bits = bitWidth(kind);
        boolean signed = isSigned(kind);

        BigInteger two = BigInteger.valueOf(2);

        BigInteger min = signed
                ? two.pow(bits - 1).negate()
                : BigInteger.ZERO;

        BigInteger max = signed
                ? two.pow(bits - 1).subtract(BigInteger.ONE)
                : two.pow(bits).subtract(BigInteger.ONE);

        return value.compareTo(min) >= 0
                && value.compareTo(max) <= 0;
    }

    private static int bitWidth(SemanticPrimitiveKind kind) {
        return switch (kind) {
            case INT8, UINT8 -> 8;
            case INT16, UINT16 -> 16;
            case INT32, UINT32 -> 32;
            case INT64, UINT64 -> 64;
        };
    }

    private static boolean isSigned(SemanticPrimitiveKind kind) {
        return switch (kind) {
            case INT8, INT16, INT32, INT64 -> true;
            case UINT8, UINT16, UINT32, UINT64 -> false;
        };
    }

    private static boolean sameType(SemanticType a, SemanticType b) {
        if (a == null || b == null) {
            return false;
        }

        /*
         * SemanticType classes should implement structural equals/hashCode.
         * The describe() fallback keeps overload resolution useful even if
         * a future SemanticType subclass forgets to override equality.
         */
        return a.equals(b) || a.describe().equals(b.describe());
    }

    private static String formatCallSignature(
            String name,
            List<SemanticType> argTypes
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append(name).append("(");

        for (int i = 0; i < argTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            SemanticType type = argTypes.get(i);
            sb.append(type == null ? "<null>" : type.describe());
        }

        sb.append(")");

        return sb.toString();
    }

    // ============================================================
    // Diagnostics Delegation
    // ============================================================

    public void error(String msg) {
        diagnostics.error(msg);
    }

    public List<String> getErrors() {
        return diagnostics.getErrors();
    }

    public boolean hasErrors() {
        return diagnostics.hasErrors();
    }

    public String formatErrors() {
        return diagnostics.formatErrors();
    }

    public String formatErrors(String header) {
        return diagnostics.formatErrors(header);
    }
}