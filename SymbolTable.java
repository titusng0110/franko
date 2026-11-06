import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    private final Map<String, EnumSymbol> enums =
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
        this(new DiagnosticBag());
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
        enums.clear();
        declaredVariableNames.clear();
        globalNamespaceVariableNames.clear();
        namespaces.clear();
        currentNamespace = null;
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
        Map<String, EnumSymbol> targetEnums = targetEnums();

        if (targetEnums.containsKey(symbol.name)) {
            error("Duplicate declaration of struct '" + symbol.qualifiedName() + "'");
            return false;
        }

        StructSymbol existing = target.get(symbol.name);

        if (existing != null) {
            error("Duplicate declaration of struct '" + symbol.qualifiedName() + "'");
            return false;
        }

        target.put(symbol.name, symbol);
        return true;
    }

    public boolean declareEnum(EnumSymbol symbol) {
        Map<String, EnumSymbol> target = targetEnums();
        Map<String, StructSymbol> targetStructs = targetStructs();

        if (targetStructs.containsKey(symbol.name)) {
            error("Duplicate declaration of enum '" + symbol.qualifiedName() + "'");
            return false;
        }

        EnumSymbol existing = target.get(symbol.name);

        if (existing != null) {
            error("Duplicate declaration of enum '" + symbol.qualifiedName() + "'");
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

    public EnumSymbol resolveEnum(String name) {
        if (currentNamespace != null) {
            EnumSymbol sym = currentNamespace.enums.get(name);

            if (sym != null) {
                return sym;
            }
        }

        return enums.get(name);
    }

    public EnumSymbol resolveEnumIn(NamespaceSymbol namespace, String name) {
        if (namespace == null) {
            return enums.get(name);
        }

        return namespace.enums.get(name);
    }

    public EnumSymbol resolveEnumReference(List<String> enumPath) {
        if (enumPath == null || enumPath.isEmpty()) {
            return null;
        }

        if (enumPath.size() == 1) {
            return resolveEnum(enumPath.get(0));
        }

        List<String> namespaceSegments = enumPath.subList(0, enumPath.size() - 1);
        String enumName = enumPath.get(enumPath.size() - 1);
        NamespaceSymbol namespace = resolveNamespace(namespaceSegments);

        if (namespace == null) {
            return null;
        }

        return resolveEnumIn(namespace, enumName);
    }

    private Map<String, StructSymbol> targetStructs() {
        return currentNamespace == null
                ? structs
                : currentNamespace.structs;
    }

    private Map<String, EnumSymbol> targetEnums() {
        return currentNamespace == null
                ? enums
                : currentNamespace.enums;
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
            if (OverloadResolution.sameParameterSignature(existing.parameters, fn.parameters)) {
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
            if (OverloadResolution.sameMethodUserParameterSignature(
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
        return resolveMethodWithLiterals(owner, name, CallArg.listOf(args));
    }

    public MethodSymbol resolveMethodWithLiterals(
            StructSymbol owner,
            String name,
            List<CallArg> userArgs
    ) {
        List<MethodSymbol> overloads = owner.methods.get(name);

        return resolveMethodFromOverloads(owner, name, overloads, userArgs);
    }

    public MethodSymbol resolveOperatorMethodWithLiterals(
            StructSymbol owner,
            String name,
            CallArg arg
    ) {
        List<MethodSymbol> overloads = owner.methods.get(name);

        if (overloads == null || overloads.isEmpty()) {
            return null;
        }

        List<MethodSymbol> matches = new ArrayList<>();

        for (MethodSymbol method : overloads) {
            if (OverloadResolution.isOperatorArgumentApplicable(method, arg)) {
                matches.add(method);
            }
        }

        matches = OverloadResolution.preferOperatorConstantFitMatches(matches, arg);

        matches = OverloadResolution.preferExactPermissionMatches(
                matches,
                method -> OverloadResolution.operatorArgumentDowngradeCost(
                        arg,
                        method.parameters.get(1).type
                )
        );

        if (matches.size() == 1) {
            return matches.get(0);
        }

        if (matches.size() > 1) {
            error("Ambiguous call to overloaded method '"
                    + owner.qualifiedName()
                    + "."
                    + OverloadResolution.formatCallSignature(
                            name,
                            List.of(arg.describeForSignature()))
                    + "': multiple overloads match equally well (no single "
                    + "overload has the lowest constant-fit and "
                    + "permission-downgrade costs)");

            return matches.get(0);
        }

        return null;
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
            if (OverloadResolution.isApplicableMethod(m, userArgs)) {
                matches.add(m);
            }
        }

        matches = OverloadResolution.preferExactPermissionMatches(
                matches,
                m -> OverloadResolution.countMethodPermissionDowngrades(m, userArgs)
        );

        matches = OverloadResolution.preferConstantFitMethods(matches, userArgs);

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
                    + OverloadResolution.formatCallSignature(name, argTypes)
                    + "': multiple overloads match equally well (no single "
                    + "overload has the lowest constant-fit and "
                    + "permission-downgrade costs)");

            return matches.get(0);
        }

        return null;
    }

    public FunctionSymbol resolveFunction(
            String name,
            List<SemanticExprNode> args
    ) {
        return resolveFunctionWithLiterals(name, CallArg.listOf(args));
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
        return resolveFunctionInWithLiterals(namespace, name, CallArg.listOf(args));
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
            if (OverloadResolution.isApplicableFunction(fn, args)) {
                matches.add(fn);
            }
        }

        matches = OverloadResolution.preferExactPermissionMatches(
                matches,
                fn -> OverloadResolution.countPermissionDowngrades(args, fn.parameterTypes())
        );

        matches = OverloadResolution.preferConstantFitFunctions(matches, args);

        if (matches.size() == 1) {
            return matches.get(0);
        }

        List<String> argTypes = args.stream()
                .map(arg -> arg == null ? "<null>" : arg.describeForSignature())
                .toList();

        if (matches.size() > 1) {
            error("Ambiguous call to overloaded function '"
                    + OverloadResolution.formatCallSignature(name, argTypes)
                    + "': multiple overloads match equally well (no single "
                    + "overload has the lowest constant-fit and "
                    + "permission-downgrade costs)");

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

    public List<Object> allSymbols() {
        List<Object> out = new ArrayList<>();

        out.addAll(globalVariables.values());
        addFunctionSymbols(out, functions.values());
        addStructSymbols(out, structs.values());
        out.addAll(enums.values());

        for (NamespaceSymbol namespace : allNamespaces()) {
            out.addAll(namespace.globalVariables.values());
            addFunctionSymbols(out, namespace.functions.values());
            addStructSymbols(out, namespace.structs.values());
            out.addAll(namespace.enums.values());
        }

        return out;
    }

    private static void addFunctionSymbols(
            List<Object> out,
            Collection<List<FunctionSymbol>> groups
    ) {
        for (List<FunctionSymbol> overloads : groups) {
            for (FunctionSymbol function : overloads) {
                out.add(function);
                out.addAll(function.parameters);
            }
        }
    }

    private static void addStructSymbols(
            List<Object> out,
            Collection<StructSymbol> structs
    ) {
        for (StructSymbol struct : structs) {
            out.add(struct);
            out.addAll(struct.fields);

            for (List<MethodSymbol> overloads : struct.methods.values()) {
                for (MethodSymbol method : overloads) {
                    out.add(method);
                    out.addAll(method.parameters);
                }
            }
        }
    }

    public Set<String> structNames() {
        return Set.copyOf(structs.keySet());
    }

    public void warnStructNameClashes() {

        Set<String> globalSpaceVariableNames =
                new HashSet<>(declaredVariableNames);
        globalSpaceVariableNames.addAll(globalNamespaceVariableNames);

        warnStructNameClashesInSpace(
                structs,
                enums,
                functions,
                globalSpaceVariableNames
        );

        for (NamespaceSymbol namespace : allNamespaces()) {
            warnStructNameClashesInSpace(
                    namespace.structs,
                    namespace.enums,
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
            diagnostics.warning("Namespace '"
                    + qualifiedClashName
                    + "' shares its name with struct '"
                    + qualifiedClashName
                    + "'");
        }

        if (scopeFunctions.containsKey(simpleName)) {
            diagnostics.warning("Namespace '"
                    + qualifiedClashName
                    + "' shares its name with function '"
                    + qualifiedClashName
                    + "'");
        }

        if (scopeVariableNames.contains(simpleName)) {
            diagnostics.warning("Namespace '"
                    + qualifiedClashName
                    + "' shares its name with a variable '"
                    + qualifiedClashName
                    + "'");
        }
    }

    private void warnStructNameClashesInSpace(
            Map<String, StructSymbol> spaceStructs,
            Map<String, EnumSymbol> spaceEnums,
            Map<String, List<FunctionSymbol>> spaceFunctions,
            Set<String> spaceVariableNames
    ) {
        for (StructSymbol structSymbol : spaceStructs.values()) {
            String structName = structSymbol.qualifiedName();

            if (spaceVariableNames.contains(structSymbol.name)) {
                diagnostics.warning("Variable '"
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

                diagnostics.warning("Function '"
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

        for (EnumSymbol enumSymbol : spaceEnums.values()) {
            String enumName = enumSymbol.qualifiedName();

            if (spaceVariableNames.contains(enumSymbol.name)) {
                diagnostics.warning("Variable '"
                        + enumName
                        + "' shares its name with enum '"
                        + enumName
                        + "'; generated C++ uses VAR_"
                        + enumSymbol.name
                        + " for the variable and ENUM_"
                        + enumSymbol.name
                        + " for the enum type");
            }

            if (spaceFunctions.containsKey(enumSymbol.name)) {
                List<FunctionSymbol> clashFunctions =
                        spaceFunctions.get(enumSymbol.name);

                StringBuilder emitted = new StringBuilder();

                for (int i = 0; i < clashFunctions.size(); i++) {
                    if (i > 0) {
                        emitted.append(", ");
                    }

                    emitted.append(clashFunctions.get(i).emittedLocalName());
                }

                diagnostics.warning("Function '"
                        + enumName
                        + "' shares its name with enum '"
                        + enumName
                        + "'; generated C++ uses "
                        + emitted
                        + " for the function and ENUM_"
                        + enumSymbol.name
                        + " for the enum type");
            }
        }
    }

    public void error(String msg) {
        diagnostics.error(msg);
    }

}
