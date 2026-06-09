import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * ============================================================================
 * EXPRESSION CHECKER
 * ============================================================================
 *
 * PURPOSE:
 * ExpressionChecker validates expression-level legality rules on the fully
 * lowered and type-decorated Semantic AST.
 *
 * The SemanticAnalyzer has already:
 *
 *   - resolved variables to VariableSymbol objects,
 *   - resolved function calls to selected FunctionSymbol overloads,
 *   - assigned each expression a mechanically inferred SemanticType,
 *   - folded integer constants into BigInteger constantValue metadata where
 *     possible.
 *
 * ExpressionChecker does not infer expression types. It validates that the
 * already-lowered expression tree is legal according to Franko expression rules.
 *
 * ----------------------------------------------------------------------------
 * IMPORTANT: ARRAY INITIALIZER LISTS
 * ----------------------------------------------------------------------------
 *
 * Array initializer lists are not checked here as special syntax.
 *
 * They are eliminated by the Desugarer before semantic analysis:
 *
 *   xs = [1, 2, 3]
 *
 * becomes:
 *
 *   xs[0] = 1
 *   xs[1] = 2
 *   xs[2] = 3
 *
 * Therefore, array initializer-list correctness is checked through ordinary
 * expression and statement rules:
 *
 *   - array access legality,
 *   - static array constant-index bounds,
 *   - assignment compatibility,
 *   - runtime dynamic-array behavior.
 *
 * In particular:
 *
 *   - a shorter initializer list for a static array is allowed because it emits
 *     fewer assignments;
 *
 *   - a longer initializer list for a static array is rejected if it emits a
 *     constant out-of-bounds static array access;
 *
 *   - dynamic array bounds/initialization are not statically enforced here.
 *
 * ----------------------------------------------------------------------------
 * VOID VALUE-CONTEXT RULE
 * ----------------------------------------------------------------------------
 *
 * A void-returning function call may exist as a semantic expression node because
 * it can be valid as an expression statement:
 *
 *   logDone();
 *
 * But void expressions are rejected in value-producing expression contexts:
 *
 *   - operands,
 *   - array indexes,
 *   - array sizes,
 *   - getaddr targets,
 *   - deref operands,
 *   - function call arguments.
 *
 * Statement-level value contexts such as assignment RHS, print arguments, and
 * conditions are also checked by StatementChecker.
 *
 * ----------------------------------------------------------------------------
 * FLUID INTEGER CONSTANTS
 * ----------------------------------------------------------------------------
 *
 * Folded integer constants are represented by SemanticExprNode.constantValue.
 *
 * A folded expression such as:
 *
 *   1 + 1
 *
 * may be checked later against a concrete contextual type as the BigInteger
 * value 2.
 *
 * Binary expression result typing may use the nonconstant side when one side is
 * a fluid constant. ExpressionChecker therefore verifies that fluid constants
 * fit the contextual concrete type where needed.
 *
 * ----------------------------------------------------------------------------
 * FUNCTION CALLS
 * ----------------------------------------------------------------------------
 *
 * Function call expressions are checked after SemanticAnalyzer has already
 * performed overload resolution.
 *
 * Argument compatibility is revalidated defensively:
 *
 *   - constant integer arguments may match primitive integer parameters if the
 *     constant value fits the parameter type;
 *
 *   - nonconstant arguments must exactly match the parameter type;
 *
 *   - void arguments are never valid.
 *
 * ============================================================================
 */
public class ExpressionChecker {
    private static final Set<String> LOGICAL_OPS =
            Set.of("&&", "||");

    private static final Set<String> COMPARISON_OPS =
            Set.of("==", "!=", "<", ">", "<=", ">=");

    private static final Set<String> SHIFT_OPS =
            Set.of("<<", ">>");

    private static final Set<String> ARITHMETIC_OPS =
            Set.of("+", "-", "*", "/");

    private static final Set<String> BITWISE_OPS =
            Set.of("&", "|", "^");

    private final DiagnosticBag diagnostics;
    private final TypeChecker types;

    public ExpressionChecker(
            DiagnosticBag diagnostics,
            TypeChecker types
    ) {
        this.diagnostics = diagnostics;
        this.types = types;
    }

    /**
     * Validates a semantic expression node.
     *
     * This method assumes expression type decoration has already happened.
     */
    public void checkExpr(SemanticExprNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticIntLiteralNode) {
            return;
        }

        if (node instanceof SemanticVarExprNode n) {
            visitVarExpr(n);
            return;
        }

        if (node instanceof SemanticUnaryOpNode n) {
            visitUnaryOp(n);
            return;
        }

        if (node instanceof SemanticBinOpNode n) {
            visitBinOp(n);
            return;
        }

        if (node instanceof SemanticArrayAccessNode n) {
            visitArrayAccess(n);
            return;
        }

        if (node instanceof SemanticFunctionCallNode n) {
            visitFunctionCall(n);
            return;
        }

        if (node instanceof SemanticGetAddrNode n) {
            visitGetAddr(n);
            return;
        }

        if (node instanceof SemanticDerefNode n) {
            visitDeref(n);
            return;
        }

        diagnostics.error("Unknown semantic expression node: "
                + node.getClass().getSimpleName());
    }

    // ============================================================
    // Basic Expressions
    // ============================================================

    private void visitVarExpr(SemanticVarExprNode node) {
        if (node.symbol == null) {
            diagnostics.error("Variable expression has null symbol");
            return;
        }

        if (node.symbol.deleted) {
            diagnostics.error("Use of deleted variable '" + node.symbol.name + "'");
        }

        if (types.isVoidType(node.type)) {
            diagnostics.error("Variable '" + node.symbol.name + "' has invalid void type");
        }
    }

    private void visitUnaryOp(SemanticUnaryOpNode node) {
        checkExpr(node.expr);

        if (!ensureValueExpression(node.expr, "Operand of unary '" + node.op + "'")) {
            return;
        }

        switch (node.op) {
            case "!" -> {
                types.ensureIntegral(
                        node.expr.type,
                        "Unary '!' requires an integer operand"
                );
            }

            case "-" -> {
                types.ensureIntegral(
                        node.expr.type,
                        "Unary '-' requires an integer operand"
                );

                /*
                 * If the operand is a folded constant, SemanticAnalyzer already
                 * folded the negated BigInteger into this node.
                 *
                 * Do not range-check here. The result remains fluid until a
                 * contextual check assigns it to a concrete integer type.
                 */
            }

            default -> {
                diagnostics.error("Unknown unary operator '" + node.op + "'");
            }
        }
    }

    // ============================================================
    // Binary Operators
    // ============================================================

    private void visitBinOp(SemanticBinOpNode node) {
        checkExpr(node.left);
        checkExpr(node.right);

        String op = node.op;

        if (!ensureValueExpression(node.left, "Left operand of '" + op + "'")) {
            return;
        }

        if (!ensureValueExpression(node.right, "Right operand of '" + op + "'")) {
            return;
        }

        if (op.equals("/")
                && node.right.isConstant()
                && BigInteger.ZERO.equals(node.right.constantValue)) {
            diagnostics.error("Division by zero in compile-time constant expression");
        }

        if (LOGICAL_OPS.contains(op)) {
            types.ensureMixedIntegralOperandsWithConstantFit(
                    node.left,
                    node.right,
                    op
            );
            return;
        }

        if (COMPARISON_OPS.contains(op)) {
            visitComparison(node, op);
            return;
        }

        if (SHIFT_OPS.contains(op)) {
            types.ensureValidShiftOperands(
                    node.left,
                    node.right,
                    op
            );

            ensureLeftShiftLiteralFitsRightSideWhenNeeded(node, op);

            return;
        }

        if (ARITHMETIC_OPS.contains(op)) {
            types.ensureSameIntegralTypeOrFit(
                    node.left,
                    node.right,
                    op
            );
            return;
        }

        if (BITWISE_OPS.contains(op)) {
            types.ensureSameIntegralTypeOrFit(
                    node.left,
                    node.right,
                    op
            );
            return;
        }

        diagnostics.error("Unknown binary operator '" + op + "'");
    }

    private void visitComparison(
            SemanticBinOpNode node,
            String op
    ) {
        boolean leftAddr = types.isAddressType(node.left.type);
        boolean rightAddr = types.isAddressType(node.right.type);

        if (leftAddr && rightAddr) {
            if (!types.sameType(node.left.type, node.right.type)) {
                diagnostics.error("Address comparison '" + op
                        + "' requires identical address types, got "
                        + node.left.type.describe()
                        + " and "
                        + node.right.type.describe());
            }
            return;
        }

        if (leftAddr || rightAddr) {
            diagnostics.error("Cannot compare address and non-address using '" + op
                    + "', got "
                    + node.left.type.describe()
                    + " and "
                    + node.right.type.describe());
            return;
        }

        types.ensureMixedIntegralOperandsWithConstantFit(
                node.left,
                node.right,
                op
        );
    }

    /**
     * Extra Option-A shift check.
     *
     * If SemanticAnalyzer decorates:
     *
     *   1 << x
     *
     * using x's type when x is nonconstant, then the left fluid constant must
     * also fit x's concrete primitive type.
     */
    private void ensureLeftShiftLiteralFitsRightSideWhenNeeded(
            SemanticBinOpNode node,
            String op
    ) {
        if (!SHIFT_OPS.contains(op)) {
            return;
        }

        if (!node.left.isConstant()) {
            return;
        }

        if (node.right.isConstant()) {
            return;
        }

        if (!(node.right.type instanceof SemanticPrimitiveType rightPrimitive)) {
            /*
             * ensureValidShiftOperands should already report that RHS is not an
             * integer. Avoid duplicate/confusing diagnostics here.
             */
            return;
        }

        if (!types.fitsBigIntegerToPrimitive(
                node.left.constantValue,
                rightPrimitive.kind
        )) {
            diagnostics.error("Left constant operand of shift '" + op
                    + "' does not fit contextual type "
                    + node.right.type.describe());
        }
    }

    // ============================================================
    // Function Calls
    // ============================================================

    private void visitFunctionCall(SemanticFunctionCallNode node) {
        if (node.function == null) {
            diagnostics.error("Function call has null resolved function");
            return;
        }

        List<SemanticExprNode> args = node.args;
        List<SemanticType> parameterTypes = node.function.parameterTypes();

        if (args.size() != parameterTypes.size()) {
            diagnostics.error("Function call to '"
                    + node.function.signatureString()
                    + "' has wrong arity: expected "
                    + parameterTypes.size()
                    + " argument(s), got "
                    + args.size());
        }

        int count = Math.min(args.size(), parameterTypes.size());

        for (int i = 0; i < count; i++) {
            SemanticExprNode arg = args.get(i);
            SemanticType parameterType = parameterTypes.get(i);

            checkExpr(arg);

            if (!ensureValueExpression(
                    arg,
                    "Argument " + (i + 1)
                            + " of call to '"
                            + node.function.signatureString()
                            + "'"
            )) {
                continue;
            }

            ensureArgumentCompatibleWithParameter(
                    arg,
                    parameterType,
                    i,
                    node.function
            );
        }

        /*
         * The semantic call expression should have the selected function's
         * declared return type.
         */
        if (!types.sameType(node.type, node.function.returnType())) {
            diagnostics.error("Function call expression type mismatch for '"
                    + node.function.signatureString()
                    + "': node has "
                    + types.describeSafe(node.type)
                    + ", selected function returns "
                    + types.describeSafe(node.function.returnType()));
        }
    }

    private void ensureArgumentCompatibleWithParameter(
            SemanticExprNode arg,
            SemanticType parameterType,
            int index,
            FunctionSymbol function
    ) {
        if (arg == null) {
            diagnostics.error("Argument " + (index + 1)
                    + " of call to '"
                    + function.signatureString()
                    + "' is null");
            return;
        }

        if (parameterType == null) {
            diagnostics.error("Parameter " + (index + 1)
                    + " of function '"
                    + function.signatureString()
                    + "' has null type");
            return;
        }

        if (types.isVoidType(parameterType)) {
            diagnostics.error("Parameter " + (index + 1)
                    + " of function '"
                    + function.signatureString()
                    + "' has invalid void type");
            return;
        }

        /*
         * Fluid/folded integer constants may be passed to primitive integer
         * parameters if they fit.
         */
        if (arg.isConstant()
                && parameterType instanceof SemanticPrimitiveType primitive) {
            if (!types.fitsBigIntegerToPrimitive(
                    arg.constantValue,
                    primitive.kind
            )) {
                diagnostics.error("Argument " + (index + 1)
                        + " of call to '"
                        + function.signatureString()
                        + "' has constant value "
                        + arg.constantValue
                        + " that does not fit parameter type "
                        + parameterType.describe());
            }
            return;
        }

        /*
         * Nonconstant expressions must exactly match the selected parameter
         * type. This covers primitive variables, address arguments, arrays, etc.
         */
        if (!types.sameType(parameterType, arg.type)) {
            diagnostics.error("Argument " + (index + 1)
                    + " of call to '"
                    + function.signatureString()
                    + "' has incompatible type: expected "
                    + types.describeSafe(parameterType)
                    + ", got "
                    + types.describeSafe(arg.type));
        }
    }

    // ============================================================
    // Array Access
    // ============================================================

    private void visitArrayAccess(SemanticArrayAccessNode node) {
        checkExpr(node.target);

        if (!ensureValueExpression(node.target, "Indexed expression")) {
            return;
        }

        types.ensureArrayType(
                node.target.type,
                "Indexed expression must be an array"
        );

        ensureArrayIndexCompatible(
                node.index,
                "Array index"
        );

        ensureStaticArrayIndexInBounds(node);
    }

    /**
     * Checks constant indexes into static arrays.
     *
     * This is the rule that rejects too-long initializer-list assignments after
     * desugaring.
     *
     * Example:
     *
     *   array<int, 3> xs;
     *   xs = [1, 2, 3, 4];
     *
     * desugars to:
     *
     *   xs[3] = 4;
     *
     * and this method rejects index 3 for static size 3.
     *
     * Dynamic arrays are not checked here because their bounds are runtime
     * properties in current Franko.
     */
    private void ensureStaticArrayIndexInBounds(SemanticArrayAccessNode node) {
        if (!(node.target.type instanceof SemanticStaticArrayType staticArray)) {
            return;
        }

        if (node.index == null || !node.index.isConstant()) {
            return;
        }

        BigInteger index = node.index.constantValue;

        if (index == null) {
            return;
        }

        /*
         * Negative indexes are already reported by ensureArrayIndexCompatible.
         */
        if (index.signum() < 0) {
            return;
        }

        BigInteger size;

        try {
            size = types.parseIntegerLiteral(staticArray.sizeLiteral);
        } catch (Exception ignored) {
            /*
             * DeclarationChecker reports invalid static array size metadata.
             * Avoid duplicate/confusing diagnostics here.
             */
            return;
        }

        if (index.compareTo(size) >= 0) {
            diagnostics.error("Static array index "
                    + index
                    + " is out of bounds for "
                    + staticArray.describe()
                    + " with size "
                    + size);
        }
    }

    // ============================================================
    // Address Expressions
    // ============================================================

    private void visitGetAddr(SemanticGetAddrNode node) {
        checkExpr(node.target);

        if (types.isVoidType(node.target.type)) {
            diagnostics.error("Operand of 'getaddr' cannot have void type");
            return;
        }

        if (!isStorageBackedLValue(node.target)) {
            diagnostics.error("Operand of 'getaddr' must be an addressable storage-backed lvalue");
        }
    }

    private void visitDeref(SemanticDerefNode node) {
        checkExpr(node.expr);

        if (!ensureValueExpression(node.expr, "Operand of 'deref'")) {
            return;
        }

        types.ensureAddressType(
                node.expr.type,
                "Operand of 'deref' must be an address type"
        );

        if (node.expr.type instanceof SemanticAddrType addr
                && types.isVoidType(addr.referencedType)) {
            diagnostics.error("Cannot dereference addr<void>");
        }
    }

    // ============================================================
    // LValue / Storage Helpers
    // ============================================================

    /**
     * Stronger than a plain node.isLValue() test.
     *
     * Today, Franko's storage-backed lvalues are:
     *
     *   - variables,
     *   - array elements whose target is storage-backed,
     *   - dereferenced addresses.
     */
    private boolean isStorageBackedLValue(SemanticExprNode expr) {
        if (expr == null) {
            return false;
        }

        if (!expr.isLValue()) {
            return false;
        }

        if (expr instanceof SemanticVarExprNode) {
            return true;
        }

        if (expr instanceof SemanticDerefNode) {
            return true;
        }

        if (expr instanceof SemanticArrayAccessNode arrayAccess) {
            return isStorageBackedLValue(arrayAccess.target);
        }

        /*
         * Future lvalue nodes, such as SemanticMemberAccessNode, should be
         * explicitly added above once their storage semantics are known.
         */
        return false;
    }

    // ============================================================
    // Array Size / Index Helpers
    // ============================================================

    /**
     * Checks expressions used as array sizes.
     *
     * Franko rule:
     *
     *   - constant size expressions must be positive and fit uint32_t,
     *   - nonconstant size expressions must have exactly uint32_t type,
     *   - void expressions are invalid as sizes.
     */
    public void ensureArraySizeCompatible(
            SemanticExprNode size,
            String message
    ) {
        if (size == null) {
            diagnostics.error(message + ": size expression cannot be null");
            return;
        }

        checkExpr(size);

        if (!ensureValueExpression(size, message)) {
            return;
        }

        if (size.isConstant()) {
            if (size.constantValue == null) {
                diagnostics.error(message + ": missing constant size value");
                return;
            }

            if (size.constantValue.signum() <= 0) {
                diagnostics.error(message + ": array size must be greater than zero");
            } else if (!types.fitsBigIntegerToPrimitive(
                    size.constantValue,
                    SemanticPrimitiveKind.UINT32
            )) {
                diagnostics.error(message + ": array size does not fit in uint32_t");
            }

            return;
        }

        if (!(size.type instanceof SemanticPrimitiveType pt
                && pt.kind == SemanticPrimitiveKind.UINT32)) {
            diagnostics.error(message + ": expected uint32_t, got "
                    + types.describeSafe(size.type));
        }
    }

    /**
     * Checks expressions used as array indexes.
     *
     * Franko array index rule:
     *
     *   - constant index expressions must be nonnegative and fit uint32_t,
     *   - nonconstant index expressions must have exactly uint32_t type,
     *   - void expressions are invalid as indexes.
     */
    public void ensureArrayIndexCompatible(
            SemanticExprNode index,
            String message
    ) {
        if (index == null) {
            diagnostics.error(message + ": index expression cannot be null");
            return;
        }

        checkExpr(index);

        if (!ensureValueExpression(index, message)) {
            return;
        }

        if (index.isConstant()) {
            if (index.constantValue == null) {
                diagnostics.error(message + ": missing constant index value");
                return;
            }

            if (index.constantValue.signum() < 0) {
                diagnostics.error(message + ": array index cannot be negative");
                return;
            }

            if (!types.fitsBigIntegerToPrimitive(
                    index.constantValue,
                    SemanticPrimitiveKind.UINT32
            )) {
                diagnostics.error(message + ": array index does not fit in uint32_t");
            }

            return;
        }

        if (!(index.type instanceof SemanticPrimitiveType pt
                && pt.kind == SemanticPrimitiveKind.UINT32)) {
            diagnostics.error(message + ": expected uint32_t, got "
                    + types.describeSafe(index.type));
        }
    }

    // ============================================================
    // Value / Void Helpers
    // ============================================================

    /**
     * Ensures an expression can be used where a value is required.
     *
     * This deliberately does not reject void expressions inside checkExpr(...)
     * globally, because a void-returning function call is valid as an
     * expression statement:
     *
     *   logDone();
     *
     * But operators, array indexes, array sizes, deref operands, getaddr
     * operands, and function arguments require real values.
     */
    private boolean ensureValueExpression(
            SemanticExprNode expr,
            String where
    ) {
        if (expr == null) {
            diagnostics.error(where + " cannot be null");
            return false;
        }

        if (types.isVoidType(expr.type)) {
            diagnostics.error(where + " cannot be void");
            return false;
        }

        return true;
    }
}