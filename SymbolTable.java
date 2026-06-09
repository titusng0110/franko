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
            error("Duplicate declaration of variable '" + sym.name + "' in the same scope");
            return false;
        }

        current.put(sym.name, sym);
        return true;
    }

    public VariableSymbol resolve(String name) {
        for (Map<String, VariableSymbol> scope : scopes) {
            VariableSymbol sym = scope.get(name);

            if (sym != null) {
                return sym;
            }
        }

        return null;
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
        List<FunctionSymbol> overloads =
                functions.computeIfAbsent(fn.name, ignored -> new ArrayList<>());

        for (FunctionSymbol existing : overloads) {
            if (sameParameterSignature(existing.parameters, fn.parameters)) {
                error("Duplicate declaration of function '"
                        + fn.signatureString()
                        + "' in the same scope");
                return;
            }
        }

        overloads.add(fn);
        functionDeclSymbols.put(decl, fn);
    }

    public FunctionSymbol findRegisteredFunction(FunctionDeclNode decl) {
        return functionDeclSymbols.get(decl);
    }

    public FunctionSymbol resolveFunction(
            String name,
            List<SemanticExprNode> args
    ) {
        List<FunctionSymbol> overloads = functions.get(name);

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
        List<FunctionSymbol> overloads = functions.get(name);
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