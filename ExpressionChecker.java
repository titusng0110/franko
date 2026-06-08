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
 * Important behavior in this version:
 *
 *   - Binary expression result typing may use the nonconstant side when one
 *     side is a fluid constant.
 *
 *   - Folded integer constants are represented by SemanticExprNode.constantValue.
 *     Therefore an expression like:
 *
 *         1 + 1
 *
 *     can participate in later contextual checks as a constant value 2.
 *
 *   - Function call expressions are checked after SemanticAnalyzer has already
 *     performed overload resolution.
 *
 *   - Function call argument compatibility is revalidated defensively:
 *
 *       * constant integer arguments may match primitive integer parameters
 *         if the constant value fits the parameter type;
 *
 *       * nonconstant arguments must exactly match the parameter type.
 *
 *   - A void-returning function call is allowed as an expression node because
 *     it may be used as an expression statement:
 *
 *         logDone();
 *
 *     But void expressions are rejected in value-producing expression contexts
 *     such as operands, array indexes, array sizes, getaddr targets, deref
 *     operands, and function call arguments.
 *
 *   - For shifts:
 *
 *         1 << x
 *
 *     may be decorated with x's type if x is nonconstant.
 *
 *   - However, RHS shift rules remain strict:
 *
 *         x << y
 *
 *     requires y to be unsigned integer if y is nonconstant.
 *
 *   - Constant fitting is still enforced:
 *
 *         x << 256
 *
 *     must be rejected if 256 does not fit the required unsigned shift-count
 *     type.
 *
 *   - Additional check:
 *
 *         999 << uint8Var
 *
 *     rejects if 999 does not fit uint8_t, because Option A result typing uses
 *     the nonconstant side as the contextual type when the other side is a
 *     literal/fluid constant.
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

    private final SemanticAnalyzer.Context ctx;
    private final TypeChecker types;

    public ExpressionChecker(
            SemanticAnalyzer.Context ctx,
            TypeChecker types
    ) {
        this.ctx = ctx;
        this.types = types;
    }

    /**
     * Validates a semantic expression node.
     *
     * This method assumes type inference has already happened. It does not
     * compute expression types; it only checks whether the already-inferred
     * expression is legal according to Franko's rules.
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

        ctx.error("Unknown semantic expression node: "
                + node.getClass().getSimpleName());
    }

    // ============================================================
    // Basic Expressions
    // ============================================================

    private void visitVarExpr(SemanticVarExprNode node) {
        if (node.symbol == null) {
            ctx.error("Variable expression has null symbol");
            return;
        }

        if (node.symbol.deleted) {
            ctx.error("Use of deleted variable '" + node.symbol.name + "'");
        }

        if (types.isVoidType(node.type)) {
            ctx.error("Variable '" + node.symbol.name + "' has invalid void type");
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
                 * If the operand is a folded constant, the analyzer already
                 * folded the negated BigInteger value into this node.
                 *
                 * Do not range-check here. The result remains a fluid constant
                 * until assigned to or otherwise checked against a contextual
                 * concrete integer type.
                 */
            }

            default -> {
                ctx.error("Unknown unary operator '" + node.op + "'");
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
            ctx.error("Division by zero in compile-time constant expression");
        }

        /*
         * Logical operators:
         *
         *   && ||
         *
         * Franko rules:
         *
         *   - operands must be integer expressions,
         *   - mixed integer types are allowed,
         *   - if one side is a constant, it must fit the other side's type,
         *   - result is uint8_t, already assigned by SemanticAnalyzer.
         */
        if (LOGICAL_OPS.contains(op)) {
            types.ensureMixedIntegralOperandsWithConstantFit(
                    node.left,
                    node.right,
                    op
            );
            return;
        }

        /*
         * Comparison operators:
         *
         *   == != < > <= >=
         */
        if (COMPARISON_OPS.contains(op)) {
            visitComparison(node, op);
            return;
        }

        /*
         * Shift operators:
         *
         *   << >>
         */
        if (SHIFT_OPS.contains(op)) {
            types.ensureValidShiftOperands(
                    node.left,
                    node.right,
                    op
            );

            ensureLeftShiftLiteralFitsRightSideWhenNeeded(node, op);

            return;
        }

        /*
         * Arithmetic operators:
         *
         *   + - * /
         */
        if (ARITHMETIC_OPS.contains(op)) {
            types.ensureSameIntegralTypeOrFit(
                    node.left,
                    node.right,
                    op
            );
            return;
        }

        /*
         * Bitwise operators:
         *
         *   & | ^
         */
        if (BITWISE_OPS.contains(op)) {
            types.ensureSameIntegralTypeOrFit(
                    node.left,
                    node.right,
                    op
            );
            return;
        }

        ctx.error("Unknown binary operator '" + op + "'");
    }

    private void visitComparison(
            SemanticBinOpNode node,
            String op
    ) {
        boolean leftAddr = types.isAddressType(node.left.type);
        boolean rightAddr = types.isAddressType(node.right.type);

        if (leftAddr && rightAddr) {
            if (!types.sameType(node.left.type, node.right.type)) {
                ctx.error("Address comparison '" + op
                        + "' requires identical address types, got "
                        + node.left.type.describe()
                        + " and "
                        + node.right.type.describe());
            }
            return;
        }

        if (leftAddr || rightAddr) {
            ctx.error("Cannot compare address and non-address using '" + op
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
     * Because SemanticAnalyzer decorates:
     *
     *   1 << x
     *
     * using x's type when x is nonconstant, the left fluid constant should also
     * be required to fit x's concrete primitive type.
     *
     * Example:
     *
     *   uint8_t x;
     *
     *   1 << x;     valid, 1 fits uint8_t
     *   999 << x;   invalid, 999 does not fit uint8_t
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
             * ensureValidShiftOperands should already report that the RHS is not
             * an integer. Avoid producing a confusing duplicate error here.
             */
            return;
        }

        if (!types.fitsBigIntegerToPrimitive(
                node.left.constantValue,
                rightPrimitive.kind
        )) {
            ctx.error("Left constant operand of shift '" + op
                    + "' does not fit contextual type "
                    + node.right.type.describe());
        }
    }

    // ============================================================
    // Function Calls
    // ============================================================

    /**
     * Checks a user-defined function call after overload resolution.
     *
     * The SemanticAnalyzer is responsible for selecting exactly one overload.
     * This checker defensively verifies that the selected function symbol is
     * internally consistent with the lowered argument expressions.
     *
     * Function call argument compatibility:
     *
     *   - constant integer arguments may match primitive integer parameters if
     *     the constant value fits the parameter type;
     *
     *   - nonconstant arguments must exactly match the parameter type;
     *
     *   - void arguments are never valid;
     *
     *   - parameter names are irrelevant.
     *
     * Important:
     *
     *   This method does not reject void-returning calls globally.
     *
     *   A call like:
     *
     *       logDone();
     *
     *   is valid as an expression statement if logDone returns void.
     *
     *   Value contexts such as assignment RHS, return expr, if condition, array
     *   size, etc. are responsible for rejecting void expressions.
     */
    private void visitFunctionCall(SemanticFunctionCallNode node) {
        if (node.function == null) {
            ctx.error("Function call has null resolved function");
            return;
        }

        List<SemanticExprNode> args = node.args;
        List<SemanticType> parameterTypes = node.function.parameterTypes();

        if (args.size() != parameterTypes.size()) {
            ctx.error("Function call to '"
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
            ctx.error("Function call expression type mismatch for '"
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
            ctx.error("Argument " + (index + 1)
                    + " of call to '"
                    + function.signatureString()
                    + "' is null");
            return;
        }

        if (parameterType == null) {
            ctx.error("Parameter " + (index + 1)
                    + " of function '"
                    + function.signatureString()
                    + "' has null type");
            return;
        }

        if (types.isVoidType(parameterType)) {
            ctx.error("Parameter " + (index + 1)
                    + " of function '"
                    + function.signatureString()
                    + "' has invalid void type");
            return;
        }

        /*
         * Fluid/folded integer constants may be passed to primitive integer
         * parameters if they fit.
         *
         * This supports:
         *
         *   func f(uint8_t x) -> void {}
         *   f(1 + 1);
         *
         * where the analyzer folds 1 + 1 to constantValue 2.
         */
        if (arg.isConstant()
                && parameterType instanceof SemanticPrimitiveType primitive) {
            if (!types.fitsBigIntegerToPrimitive(
                    arg.constantValue,
                    primitive.kind
            )) {
                ctx.error("Argument " + (index + 1)
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
            ctx.error("Argument " + (index + 1)
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
    }

    // ============================================================
    // Address Expressions
    // ============================================================

    private void visitGetAddr(SemanticGetAddrNode node) {
        checkExpr(node.target);

        if (types.isVoidType(node.target.type)) {
            ctx.error("Operand of 'getaddr' cannot have void type");
            return;
        }

        if (!isStorageBackedLValue(node.target)) {
            ctx.error("Operand of 'getaddr' must be an addressable storage-backed lvalue");
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
            ctx.error("Cannot dereference addr<void>");
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
            ctx.error(message + ": size expression cannot be null");
            return;
        }

        checkExpr(size);

        if (!ensureValueExpression(size, message)) {
            return;
        }

        if (size.isConstant()) {
            if (size.constantValue == null) {
                ctx.error(message + ": missing constant size value");
                return;
            }

            if (size.constantValue.signum() <= 0) {
                ctx.error(message + ": array size must be greater than zero");
            } else if (!types.fitsBigIntegerToPrimitive(
                    size.constantValue,
                    SemanticPrimitiveKind.UINT32
            )) {
                ctx.error(message + ": array size does not fit in uint32_t");
            }

            return;
        }

        if (!(size.type instanceof SemanticPrimitiveType pt
                && pt.kind == SemanticPrimitiveKind.UINT32)) {
            ctx.error(message + ": expected uint32_t, got "
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
            ctx.error(message + ": index expression cannot be null");
            return;
        }

        checkExpr(index);

        if (!ensureValueExpression(index, message)) {
            return;
        }

        if (index.isConstant()) {
            if (index.constantValue == null) {
                ctx.error(message + ": missing constant index value");
                return;
            }

            if (index.constantValue.signum() < 0) {
                ctx.error(message + ": array index cannot be negative");
                return;
            }

            if (!types.fitsBigIntegerToPrimitive(
                    index.constantValue,
                    SemanticPrimitiveKind.UINT32
            )) {
                ctx.error(message + ": array index does not fit in uint32_t");
            }

            return;
        }

        if (!(index.type instanceof SemanticPrimitiveType pt
                && pt.kind == SemanticPrimitiveKind.UINT32)) {
            ctx.error(message + ": expected uint32_t, got "
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
            ctx.error(where + " cannot be null");
            return false;
        }

        if (types.isVoidType(expr.type)) {
            ctx.error(where + " cannot be void");
            return false;
        }

        return true;
    }
}