import java.math.BigInteger;
import java.util.*;

public final class SymbolTable {

    private final Deque<Map<String, VariableSymbol>> scopes =
            new ArrayDeque<>();

    private final Map<String, VariableSymbol> globalVariables =
            new LinkedHashMap<>();

    private final IdentityHashMap<VarDeclNode, VariableSymbol> varDeclSymbols =
            new IdentityHashMap<>();

    private final Map<String, List<FunctionSymbol>> functions =
            new LinkedHashMap<>();

    private final IdentityHashMap<FunctionDeclNode, FunctionSymbol> functionDeclSymbols =
            new IdentityHashMap<>();

    private final Map<String, StructSymbol> structs =
            new LinkedHashMap<>();

    private final Set<String> declaredVariableNames =
            new HashSet<>();

    private final Set<String> globalNamespaceVariableNames =
            new HashSet<>();

    private final Map<String, NamespaceSymbol> namespaces =
            new LinkedHashMap<>();

    private NamespaceSymbol currentNamespace;

    private final DiagnosticBag diagnostics;

    public SymbolTable() {
        this(new DiagnosticBag("Semantic analysis failed:"));
    }

    public SymbolTable(DiagnosticBag diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public void clear() {
        clearSymbols();
        diagnostics.clear();
    }

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

    public void clearErrors() {
        diagnostics.clear();
    }

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

    public NamespaceSymbol resolveNamespace(String path) {
        return resolveNamespace(splitNamespacePath(path));
    }

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

    public void setCurrentNamespace(NamespaceSymbol namespace) {
        this.currentNamespace = namespace;
    }

    public NamespaceSymbol currentNamespace() {
        return currentNamespace;
    }

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

    public StructSymbol resolveStruct(String name) {
        if (currentNamespace != null) {
            StructSymbol sym = currentNamespace.structs.get(name);

            if (sym != null) {
                return sym;
            }
        }

        return structs.get(name);
    }

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

    public boolean declareGlobalNamespaceVariable(VariableSymbol sym) {
        if (globalVariables.containsKey(sym.name)) {
            return false;
        }

        globalVariables.put(sym.name, sym);
        globalNamespaceVariableNames.add(sym.name);
        return true;
    }

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

    public FunctionSymbol findRegisteredFunction(FunctionDeclNode decl) {
        if (currentNamespace != null) {
            FunctionSymbol sym = currentNamespace.functionDeclSymbols.get(decl);

            if (sym != null) {
                return sym;
            }
        }

        return functionDeclSymbols.get(decl);
    }

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
            if (sameMethodUserParameterSignature(
                    existing.parameters,
                    method.parameters)) {
                error("Duplicate declaration of method '"
                        + method.signatureString()
                        + "' in struct '"
                        + owner.qualifiedName()
                        + "'"
                        + (existing.parameters.size() > 1
                                ? ": method overload identity excludes the "
                                    + "self parameter type"
                                : ""));
                return;
            }
        }

        overloads.add(method);
        owner.methodDeclSymbols.put(decl, method);
    }

    public MethodSymbol findRegisteredMethod(
            StructSymbol owner,
            FunctionDeclNode decl
    ) {
        return owner.methodDeclSymbols.get(decl);
    }

    public MethodSymbol resolveMethod(
            StructSymbol owner,
            String name,
            List<SemanticExprNode> args
    ) {
        return resolveMethodWithLiterals(owner, name, toCallArgs(args));
    }

    public MethodSymbol resolveMethodWithLiterals(
            StructSymbol owner,
            String name,
            List<CallArg> userArgs
    ) {
        List<MethodSymbol> overloads = owner.methods.get(name);

        return resolveMethodFromOverloads(owner, name, overloads, userArgs);
    }

    static List<CallArg> toCallArgs(List<SemanticExprNode> args) {
        List<CallArg> out = new ArrayList<>(args.size());

        for (SemanticExprNode arg : args) {
            out.add(CallArg.lowered(arg));
        }

        return out;
    }

    static final class CallArg {
        final SemanticExprNode lowered;
        final ArrayLiteralNode literal;

        private CallArg(SemanticExprNode lowered, ArrayLiteralNode literal) {
            this.lowered = lowered;
            this.literal = literal;
        }

        static CallArg lowered(SemanticExprNode expr) {
            return new CallArg(Objects.requireNonNull(expr), null);
        }

        static CallArg literal(ArrayLiteralNode literal) {
            return new CallArg(null, Objects.requireNonNull(literal));
        }

        boolean isLiteral() {
            return literal != null;
        }

        SemanticType type() {
            return isLiteral() ? null : lowered.type;
        }

        String describeForSignature() {
            if (isLiteral()) {
                return "[array literal with "
                        + literal.elements.size()
                        + " element(s)]";
            }

            return lowered.type == null ? "<null>" : lowered.type.describe();
        }
    }

    private MethodSymbol resolveMethodFromOverloads(
            StructSymbol owner,
            String name,
            List<MethodSymbol> overloads,
            List<CallArg> userArgs
    ) {
        if (overloads == null || overloads.isEmpty()) {
            return null;
        }

        List<MethodSymbol> matches = new ArrayList<>();

        for (MethodSymbol m : overloads) {
            if (isApplicableMethod(m, userArgs)) {
                matches.add(m);
            }
        }

        matches = preferExactPermissionMatches(
                matches,
                m -> countMethodPermissionDowngrades(m, userArgs)
        );

        if (matches.size() == 1) {
            return matches.get(0);
        }

        List<String> argTypes = userArgs.stream()
                .map(arg -> arg == null ? "<null>" : arg.describeForSignature())
                .toList();

        if (matches.size() > 1) {
            error("Ambiguous call to overloaded method '"
                    + owner.qualifiedName()
                    + "."
                    + formatCallSignature(name, argTypes)
                    + "': multiple overloads match equally well (argument "
                    + "constants fit several signatures, or arguments rely "
                    + "on the same number of pointer permission downgrades)");

            return matches.get(0);
        }

        return null;
    }

    public FunctionSymbol resolveFunction(
            String name,
            List<SemanticExprNode> args
    ) {
        return resolveFunctionWithLiterals(name, toCallArgs(args));
    }

    public FunctionSymbol resolveFunctionWithLiterals(
            String name,
            List<CallArg> args
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

    public FunctionSymbol resolveFunctionIn(
            NamespaceSymbol namespace,
            String name,
            List<SemanticExprNode> args
    ) {
        return resolveFunctionInWithLiterals(namespace, name, toCallArgs(args));
    }

    public FunctionSymbol resolveFunctionInWithLiterals(
            NamespaceSymbol namespace,
            String name,
            List<CallArg> args
    ) {
        List<FunctionSymbol> overloads = namespace == null
                ? functions.get(name)
                : namespace.functions.get(name);

        return resolveFromOverloads(name, overloads, args);
    }

    private FunctionSymbol resolveFromOverloads(
            String name,
            List<FunctionSymbol> overloads,
            List<CallArg> args
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

        matches = preferExactPermissionMatches(
                matches,
                fn -> countPermissionDowngrades(args, fn.parameterTypes())
        );

        if (matches.size() == 1) {
            return matches.get(0);
        }

        List<String> argTypes = args.stream()
                .map(arg -> arg == null ? "<null>" : arg.describeForSignature())
                .toList();

        if (matches.size() > 1) {
            error("Ambiguous call to overloaded function '"
                    + formatCallSignature(name, argTypes)
                    + "': multiple overloads match equally well (argument "
                    + "constants fit several signatures, or arguments rely "
                    + "on the same number of pointer permission downgrades)");

            return matches.get(0);
        }

        return null;
    }

    private static <T> List<T> preferExactPermissionMatches(
            List<T> matches,
            java.util.function.ToIntFunction<T> downgradeCount
    ) {
        if (matches.size() <= 1) {
            return matches;
        }

        int best = Integer.MAX_VALUE;

        for (T match : matches) {
            best = Math.min(best, downgradeCount.applyAsInt(match));
        }

        List<T> preferred = new ArrayList<>();

        for (T match : matches) {
            if (downgradeCount.applyAsInt(match) == best) {
                preferred.add(match);
            }
        }

        return preferred;
    }

    public boolean hasAnyFunctionNamed(String name) {
        if (currentNamespace != null
                && currentNamespace.functions.containsKey(name)) {
            return true;
        }

        List<FunctionSymbol> overloads = functions.get(name);
        return overloads != null && !overloads.isEmpty();
    }

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

    public Set<String> structNames() {
        return Set.copyOf(structs.keySet());
    }

    public void warning(String msg) {
        diagnostics.warning(msg);
    }

    public List<String> warnings() {
        return diagnostics.getWarnings();
    }

    public boolean hasWarnings() {
        return diagnostics.hasWarnings();
    }

    public void warnStructNameClashes() {

        Set<String> globalSpaceVariableNames =
                new HashSet<>(declaredVariableNames);
        globalSpaceVariableNames.addAll(globalNamespaceVariableNames);

        warnStructNameClashesInSpace(
                structs,
                functions,
                globalSpaceVariableNames
        );

        for (NamespaceSymbol namespace : allNamespaces()) {
            warnStructNameClashesInSpace(
                    namespace.structs,
                    namespace.functions,
                    namespace.variableNames
            );
        }

        for (NamespaceSymbol namespace : allNamespaces()) {
            warnNamespaceNameClash(namespace);
        }
    }

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

    private static boolean sameMethodUserParameterSignature(
            List<ParameterSymbol> a,
            List<ParameterSymbol> b
    ) {
        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 1; i < a.size(); i++) {
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
            List<CallArg> args
    ) {
        if (fn.arity() != args.size()) {
            return false;
        }

        List<SemanticType> parameterTypes = fn.parameterTypes();

        for (int i = 0; i < args.size(); i++) {
            SemanticType parameterType = parameterTypes.get(i);
            CallArg arg = args.get(i);

            if (!isArgumentApplicableToParameter(arg, parameterType)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isApplicableMethod(
            MethodSymbol method,
            List<CallArg> userArgs
    ) {
        if (method.parameters.size() != userArgs.size() + 1) {
            return false;
        }

        for (int i = 0; i < userArgs.size(); i++) {
            SemanticType parameterType =
                    method.parameters.get(i + 1).type;
            CallArg arg = userArgs.get(i);

            if (!isArgumentApplicableToParameter(arg, parameterType)) {
                return false;
            }
        }

        return true;
    }

    private static int countPermissionDowngrades(
            List<CallArg> args,
            List<SemanticType> parameterTypes
    ) {
        int count = 0;

        for (int i = 0; i < args.size(); i++) {
            CallArg arg = args.get(i);

            if (arg == null) {
                continue;
            }

            SemanticType parameterType = parameterTypes.get(i);

            if (arg.isLiteral()) {
                count += arrayLiteralDowngradeCost(arg.literal, parameterType);
                continue;
            }

            if (arg.lowered == null || arg.lowered.type == null) {
                continue;
            }

            if (!sameType(arg.lowered.type, parameterType)
                    && arg.lowered.type instanceof SemanticPtrType) {
                count += PointerPermissions.isStaticArrayViewDecay(
                        arg.lowered.type,
                        parameterType)
                        ? 2
                        : 1;
            }
        }

        return count;
    }

    private static int countMethodPermissionDowngrades(
            MethodSymbol method,
            List<CallArg> userArgs
    ) {
        int count = 0;

        for (int i = 0; i < userArgs.size(); i++) {
            CallArg arg = userArgs.get(i);

            if (arg == null) {
                continue;
            }

            SemanticType parameterType = method.parameters.get(i + 1).type;

            if (arg.isLiteral()) {
                count += arrayLiteralDowngradeCost(arg.literal, parameterType);
                continue;
            }

            if (arg.lowered == null || arg.lowered.type == null) {
                continue;
            }

            if (!sameType(arg.lowered.type, parameterType)
                    && arg.lowered.type instanceof SemanticPtrType) {
                count += PointerPermissions.isStaticArrayViewDecay(
                        arg.lowered.type,
                        parameterType)
                        ? 2
                        : 1;
            }
        }

        return count;
    }

    private static boolean isArgumentApplicableToParameter(
            CallArg arg,
            SemanticType parameterType
    ) {
        if (arg == null || parameterType == null) {
            return false;
        }

        if (arg.isLiteral()) {
            return isArrayLiteralShapeApplicable(arg.literal, parameterType);
        }

        if (arg.lowered == null || arg.lowered.type == null) {
            return false;
        }

        SemanticExprNode lowered = arg.lowered;

        if (lowered.type instanceof SemanticNullType) {
            return parameterType instanceof SemanticPtrType;
        }

        if (lowered.isConstant()
                && parameterType instanceof SemanticPrimitiveType primitive) {
            return fitsBigIntegerToPrimitive(
                    lowered.constantValue,
                    primitive.kind
            );
        }

        if (sameType(lowered.type, parameterType)) {
            return true;
        }

        return PointerPermissions.isPointerConversion(lowered.type, parameterType);
    }

    static boolean isArrayLiteralShapeApplicable(
            ArrayLiteralNode literal,
            SemanticType parameterType
    ) {
        if (!(parameterType instanceof SemanticPtrType ptr)
                || ptr.permission != PtrPermission.READ_ONLY) {
            return false;
        }

        SemanticType referenced = ptr.referencedType;

        if (!(referenced instanceof SemanticStaticArrayType)
                && !(referenced instanceof SemanticDynamicArrayType)) {
            return false;
        }

        return arrayLiteralRowsMatch(literal, referenced, true);
    }

    private static int arrayLiteralDowngradeCost(
            ArrayLiteralNode literal,
            SemanticType parameterType
    ) {
        if (!(parameterType instanceof SemanticPtrType ptr)
                || ptr.permission != PtrPermission.READ_ONLY) {
            return 0;
        }

        if (!(ptr.referencedType instanceof SemanticStaticArrayType)
                && !(ptr.referencedType instanceof SemanticDynamicArrayType)) {
            return 0;
        }

        if (!arrayLiteralRowsMatch(literal, ptr.referencedType, true)) {
            return 0;
        }

        return ptr.referencedType instanceof SemanticDynamicArrayType ? 2 : 0;
    }

    private static boolean arrayLiteralRowsMatch(
            ArrayLiteralNode literal,
            SemanticType arrayType,
            boolean outermost
    ) {
        if (literal == null || literal.elements.isEmpty()) {
            return false;
        }

        SemanticType elementShape;

        if (arrayType instanceof SemanticStaticArrayType staticArray) {
            SemanticStaticArrayType probe = new SemanticStaticArrayType(
                    staticArray.elementType,
                    Integer.toString(literal.elements.size())
            );

            if (!probe.equals(staticArray)) {
                return false;
            }

            elementShape = staticArray.elementType;
        } else if (arrayType instanceof SemanticDynamicArrayType dynamicArray
                && outermost) {
            elementShape = dynamicArray.elementType;
        } else {
            return false;
        }

        boolean anyNested = false;
        boolean anyScalar = false;

        for (ASTNode element : literal.elements) {
            if (element instanceof ArrayLiteralNode) {
                anyNested = true;
            } else {
                anyScalar = true;
            }
        }

        if (anyNested && anyScalar) {
            return false;
        }

        if (anyNested) {
            if (!(elementShape instanceof SemanticStaticArrayType)
                    && !(elementShape instanceof SemanticDynamicArrayType)) {
                return false;
            }

            for (ASTNode element : literal.elements) {
                if (!arrayLiteralRowsMatch(
                        (ArrayLiteralNode) element,
                        elementShape,
                        false)) {
                    return false;
                }
            }

            return true;
        }

        return elementShape instanceof SemanticPrimitiveType;
    }

    static boolean isPermissionDowngrade(
            SemanticType sourceType,
            SemanticType targetType
    ) {
        return PointerPermissions.isPermissionDowngrade(sourceType, targetType);
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

        return a.equals(b) || a.describe().equals(b.describe());
    }

    private static String formatCallSignature(
            String name,
            List<String> argTypes
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append(name).append("(");

        for (int i = 0; i < argTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            String type = argTypes.get(i);
            sb.append(type == null ? "<null>" : type);
        }

        sb.append(")");

        return sb.toString();
    }

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
