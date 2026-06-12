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
 *   - void arguments are never valid;
 *
 *   - arrays cannot be passed directly as arguments;
 *
 *   - functions that need array access should take addr<array<T>> or
 *     addr<array<T, N>>, and callers should pass getaddr(array).
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

        if (node instanceof SemanticArrayIntrinsicCallNode n) {
            visitArrayIntrinsicCall(n);
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

            default -> diagnostics.error("Unknown unary operator '" + node.op + "'");
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

            /*
             * Extra Option-A shift check.
             *
             * If SemanticAnalyzer decorates:
             *
             *   1 << x
             *
             * using x's type when x is nonconstant, then the left fluid
             * constant must also fit x's concrete primitive type.
             */
            if (node.left.isConstant()
                    && !node.right.isConstant()
                    && node.right.type instanceof SemanticPrimitiveType rightPrimitive
                    && !types.fitsBigIntegerToPrimitive(
                            node.left.constantValue,
                            rightPrimitive.kind
                    )) {
                diagnostics.error("Left constant operand of shift '" + op
                        + "' does not fit contextual type "
                        + node.right.type.describe());
            }

            return;
        }

        if (ARITHMETIC_OPS.contains(op) || BITWISE_OPS.contains(op)) {
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

        if (types.isArrayType(parameterType)) {
            diagnostics.error("Parameter " + (index + 1)
                    + " of function '"
                    + function.signatureString()
                    + "' has invalid array type "
                    + parameterType.describe()
                    + "; use addr<"
                    + parameterType.describe()
                    + "> instead");
            return;
        }

        if (types.isArrayType(arg.type)) {
            diagnostics.error("Argument " + (index + 1)
                    + " of call to '"
                    + function.signatureString()
                    + "' passes array value directly; use getaddr(...) and an addr<array<...>> parameter");
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
         * type. This covers primitive variables, address arguments, etc.
         *
         * Arrays are intentionally not included here as value arguments.
         * Array access through functions should use addr<array<T>> or
         * addr<array<T, N>>.
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
    // Array Intrinsic Calls
    // ============================================================

    private void visitArrayIntrinsicCall(SemanticArrayIntrinsicCallNode node) {
        if (node.kind == null) {
            diagnostics.error("Array intrinsic call has null kind");
            return;
        }

        checkExpr(node.receiver);

        switch (node.kind) {
            case INIT, INIT_ZERO, RESIZE -> visitArrayAllocIntrinsic(node);
            case UNINIT -> visitArrayUninitIntrinsic(node);
            case MEMSET -> visitArrayMemsetIntrinsic(node);
            case MEMCPY -> visitArrayMemcpyIntrinsic(node);
            case MEMMOVE -> visitArrayMemmoveIntrinsic(node);
        }
    }

    private void visitArrayAllocIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 1) {
            diagnostics.error("Array intrinsic '"
                    + node.kind.runtimeName
                    + "' expects exactly 1 argument, got "
                    + node.args.size());
            return;
        }

        if (!isStorageBackedLValue(node.receiver)) {
            diagnostics.error(node.kind.runtimeName
                    + "() receiver must be a storage-backed lvalue");
        }

        if (!types.isDynamicArrayType(node.receiver.type)) {
            diagnostics.error(node.kind.runtimeName
                    + "() receiver must be a dynamic array, got "
                    + types.describeSafe(node.receiver.type));
        }

        ensureArraySizeCompatible(
                node.args.get(0),
                node.kind.runtimeName + "() size"
        );

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticPrimitiveType(SemanticPrimitiveKind.INT32)
        );
    }

    private void visitArrayUninitIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 0) {
            diagnostics.error("Array intrinsic 'uninit' expects exactly 0 arguments, got "
                    + node.args.size());
            return;
        }

        if (!isStorageBackedLValue(node.receiver)) {
            diagnostics.error("uninit() receiver must be a storage-backed lvalue");
        }

        if (!types.isDynamicArrayType(node.receiver.type)) {
            diagnostics.error("uninit() receiver must be a dynamic array, got "
                    + types.describeSafe(node.receiver.type));
        }

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticVoidType()
        );
    }

    private void visitArrayMemsetIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 1 && node.args.size() != 3) {
            diagnostics.error("Array intrinsic 'memset' expects either 1 or 3 arguments, got "
                    + node.args.size());
            return;
        }

        if (!isStorageBackedLValue(node.receiver)) {
            diagnostics.error("memset() receiver must be a storage-backed lvalue");
        }

        types.ensureArrayType(
                node.receiver.type,
                "memset() receiver must be an array"
        );

        SemanticType elementType = types.elementTypeOfArray(node.receiver.type);

        if (elementType != null && !types.isMemsetable(elementType)) {
            diagnostics.error("memset() receiver element type is not memsetable: "
                    + elementType.describe());
        }

        SemanticExprNode value = node.args.get(0);

        checkExpr(value);

        if (ensureValueExpression(
                value,
                "memset() fill value"
        )) {
            ensureMemsetValueCompatible(value);
        }

        if (node.args.size() == 3) {
            ensureUInt32Compatible(
                    node.args.get(1),
                    "memset() start",
                    true
            );

            ensureUInt32Compatible(
                    node.args.get(2),
                    "memset() count",
                    true
            );

            ensureStaticArrayRangeInBounds(
                    node.receiver.type,
                    node.args.get(1),
                    node.args.get(2),
                    "memset() range"
            );
        }

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticVoidType()
        );
    }

    private void visitArrayMemcpyIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 1 && node.args.size() != 4) {
            diagnostics.error("Array intrinsic 'memcpy' expects either 1 or 4 arguments, got "
                    + node.args.size());
            return;
        }

        if (!isStorageBackedLValue(node.receiver)) {
            diagnostics.error("memcpy() target must be a storage-backed lvalue");
        }

        types.ensureArrayType(
                node.receiver.type,
                "memcpy() target must be an array"
        );

        SemanticExprNode sourceAddr = node.args.get(0);
        checkExpr(sourceAddr);

        SemanticType sourceArrayType = null;

        if (ensureValueExpression(
                sourceAddr,
                "memcpy() source address"
        )) {
            if (!(sourceAddr.type instanceof SemanticAddrType addr)) {
                diagnostics.error("memcpy() source must be an address to an array, got "
                        + types.describeSafe(sourceAddr.type));
            } else if (!types.isArrayType(addr.referencedType)) {
                diagnostics.error("memcpy() source must be an address to an array, got addr<"
                        + types.describeSafe(addr.referencedType)
                        + ">");
            } else {
                sourceArrayType = addr.referencedType;
            }
        }

        SemanticType targetArrayType = node.receiver.type;

        if (targetArrayType != null && sourceArrayType != null) {
            SemanticType targetElem = types.elementTypeOfArray(targetArrayType);
            SemanticType sourceElem = types.elementTypeOfArray(sourceArrayType);

            if (targetElem != null && sourceElem != null
                    && !types.sameType(targetElem, sourceElem)) {
                diagnostics.error("memcpy() requires source and target arrays to have identical element types, got "
                        + targetElem.describe()
                        + " and "
                        + sourceElem.describe());
            }

            if (targetElem != null && !types.isMemcpyable(targetElem)) {
                diagnostics.error("memcpy() target array element type is not memcpyable: "
                        + targetElem.describe());
            }

            if (sourceElem != null && !types.isMemcpyable(sourceElem)) {
                diagnostics.error("memcpy() source array element type is not memcpyable: "
                        + sourceElem.describe());
            }
        }

        if (node.args.size() == 4) {
            SemanticExprNode dstStart = node.args.get(1);
            SemanticExprNode srcStart = node.args.get(2);
            SemanticExprNode count = node.args.get(3);

            ensureUInt32Compatible(
                    dstStart,
                    "memcpy() destination start",
                    true
            );

            ensureUInt32Compatible(
                    srcStart,
                    "memcpy() source start",
                    true
            );

            ensureUInt32Compatible(
                    count,
                    "memcpy() count",
                    true
            );

            ensureStaticArrayRangeInBounds(
                    targetArrayType,
                    dstStart,
                    count,
                    "memcpy() destination range"
            );

            ensureStaticArrayRangeInBounds(
                    sourceArrayType,
                    srcStart,
                    count,
                    "memcpy() source range"
            );
        }

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticVoidType()
        );
    }

    private void visitArrayMemmoveIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 3) {
            diagnostics.error("Array intrinsic 'memmove' expects exactly 3 arguments, got "
                    + node.args.size());
            return;
        }

        if (!isStorageBackedLValue(node.receiver)) {
            diagnostics.error("memmove() receiver must be a storage-backed lvalue");
        }

        types.ensureArrayType(
                node.receiver.type,
                "memmove() receiver must be an array"
        );

        SemanticType receiverElem =
                types.elementTypeOfArray(node.receiver.type);

        if (receiverElem != null && !types.isMemcpyable(receiverElem)) {
            diagnostics.error("memmove() receiver array element type is not memmoveable: "
                    + receiverElem.describe());
        }

        SemanticExprNode dstStart = node.args.get(0);
        SemanticExprNode srcStart = node.args.get(1);
        SemanticExprNode count = node.args.get(2);

        ensureUInt32Compatible(
                dstStart,
                "memmove() destination start",
                true
        );

        ensureUInt32Compatible(
                srcStart,
                "memmove() source start",
                true
        );

        ensureUInt32Compatible(
                count,
                "memmove() count",
                true
        );

        ensureStaticArrayRangeInBounds(
                node.receiver.type,
                dstStart,
                count,
                "memmove() destination range"
        );

        ensureStaticArrayRangeInBounds(
                node.receiver.type,
                srcStart,
                count,
                "memmove() source range"
        );

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticVoidType()
        );
    }

    private void ensureArrayIntrinsicReturnType(
            SemanticArrayIntrinsicCallNode node,
            SemanticType expected
    ) {
        if (!types.sameType(node.type, expected)) {
            diagnostics.error("Array intrinsic '"
                    + node.kind.runtimeName
                    + "' expression type mismatch: node has "
                    + types.describeSafe(node.type)
                    + ", expected "
                    + types.describeSafe(expected));
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

        if (index == null || index.signum() < 0) {
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
        ensureUInt32Compatible(
                size,
                message,
                false
        );
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
        ensureUInt32Compatible(
                index,
                message,
                true
        );
    }

    private void ensureUInt32Compatible(
            SemanticExprNode expr,
            String message,
            boolean allowZero
    ) {
        if (expr == null) {
            diagnostics.error(message + ": expression cannot be null");
            return;
        }

        checkExpr(expr);

        if (!ensureValueExpression(expr, message)) {
            return;
        }

        if (expr.isConstant()) {
            if (expr.constantValue == null) {
                diagnostics.error(message + ": missing constant value");
                return;
            }

            if (expr.constantValue.signum() < 0) {
                diagnostics.error(message + ": value cannot be negative");
                return;
            }

            if (!allowZero && expr.constantValue.signum() == 0) {
                diagnostics.error(message + ": value must be greater than zero");
                return;
            }

            if (!types.fitsBigIntegerToPrimitive(
                    expr.constantValue,
                    SemanticPrimitiveKind.UINT32
            )) {
                diagnostics.error(message + ": value does not fit in uint32_t");
            }

            return;
        }

        if (!(expr.type instanceof SemanticPrimitiveType pt
                && pt.kind == SemanticPrimitiveKind.UINT32)) {
            diagnostics.error(message + ": expected uint32_t, got "
                    + types.describeSafe(expr.type));
        }
    }

    private void ensureStaticArrayRangeInBounds(
            SemanticType arrayType,
            SemanticExprNode startExpr,
            SemanticExprNode countExpr,
            String message
    ) {
        if (!(arrayType instanceof SemanticStaticArrayType staticArray)) {
            return;
        }

        if (startExpr == null
                || countExpr == null
                || !startExpr.isConstant()
                || !countExpr.isConstant()) {
            return;
        }

        BigInteger start = startExpr.constantValue;
        BigInteger count = countExpr.constantValue;

        if (start == null
                || count == null
                || start.signum() < 0
                || count.signum() < 0) {
            return;
        }

        BigInteger size;

        try {
            size = types.parseIntegerLiteral(staticArray.sizeLiteral);
        } catch (Exception ignored) {
            return;
        }

        BigInteger end = start.add(count);

        if (start.compareTo(size) > 0 || end.compareTo(size) > 0) {
            diagnostics.error(message
                    + " is out of bounds for "
                    + staticArray.describe()
                    + " with size "
                    + size
                    + ": start "
                    + start
                    + ", count "
                    + count);
        }
    }

    // ============================================================
    // Array Intrinsic Argument Helpers
    // ============================================================

    private void ensureMemsetValueCompatible(SemanticExprNode value) {
        if (value == null) {
            diagnostics.error("memset() fill value cannot be null");
            return;
        }

        if (value.isConstant()) {
            types.requireConstantFitsPrimitive(
                    value.constantValue,
                    SemanticPrimitiveKind.UINT8,
                    "memset() fill value"
            );
            return;
        }

        if (!(value.type instanceof SemanticPrimitiveType pt
                && pt.kind == SemanticPrimitiveKind.UINT8)) {
            diagnostics.error("memset() fill value must be uint8_t/char, got "
                    + types.describeSafe(value.type));
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