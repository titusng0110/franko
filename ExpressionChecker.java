import java.math.BigInteger;
import java.util.List;
import java.util.Set;

public class ExpressionChecker {
    private static final Set<String> LOGICAL_OPS =
            Set.of("&&", "||");

    private static final Set<String> COMPARISON_OPS =
            Set.of("==", "!=", "<", ">", "<=", ">=");

    private static final Set<String> SHIFT_OPS =
            Set.of("<<", ">>");

    private static final Set<String> ARITHMETIC_OPS =
            Set.of("+", "-", "*", "/", "%");

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

    public void checkExpr(SemanticExprNode node) {
        if (node == null) {
            return;
        }

        if (node instanceof SemanticIntLiteralNode) {
            return;
        }

        if (node instanceof SemanticNullLiteralNode) {

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

        if (node instanceof SemanticGetPtrNode n) {
            visitGetPtr(n);
            return;
        }

        if (node instanceof SemanticDerefNode n) {
            visitDeref(n);
            return;
        }

        if (node instanceof SemanticCastExprNode n) {
            visitCast(n);
            return;
        }

        if (node instanceof SemanticMemberAccessNode n) {
            visitMemberAccess(n);
            return;
        }

        diagnostics.error("Unknown semantic expression node: "
                + node.getClass().getSimpleName());
    }

    private void visitVarExpr(SemanticVarExprNode node) {
        if (node.symbol == null) {
            diagnostics.error("Variable expression has null symbol");
            return;
        }

        if (types.isVoidType(node.type)) {
            diagnostics.error("Variable '" + node.symbol.qualifiedName() + "' has invalid void type");
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

            }

            default -> diagnostics.error("Unknown unary operator '" + node.op + "'");
        }
    }

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

        if (op.equals("/") || op.equals("%")) {
            if (node.right.isConstant()
                    && BigInteger.ZERO.equals(node.right.constantValue)) {
                diagnostics.error("Division by zero in compile-time constant expression");
            }
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

            if (node.left.isConstant()
                    && !node.right.isConstant()
                    && node.right.type instanceof SemanticPrimitiveType rightPrimitive
                    && !TypeChecker.fitsBigIntegerToPrimitive(
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
        boolean leftPtr = types.isPointerType(node.left.type);
        boolean rightPtr = types.isPointerType(node.right.type);
        boolean leftNull = types.isNullType(node.left.type);
        boolean rightNull = types.isNullType(node.right.type);

        if (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")) {
            if (leftPtr || rightPtr) {
                diagnostics.error("Pointer comparisons must only use equality operators ('==' or '!='), but got '" + op + "'.");
                return;
            }

            if (leftNull || rightNull) {
                diagnostics.error("Null comparisons must only use equality operators ('==' or '!='), but got '" + op + "'.");
                return;
            }
        }

        if (leftNull || rightNull) {
            SemanticExprNode other = leftNull ? node.right : node.left;

            if (types.isNullType(other.type)) {

                return;
            }

            if (!types.isPointerType(other.type)) {
                diagnostics.error("Cannot compare null with non-pointer type '"
                        + other.type.describe()
                        + "'");
            }
            return;
        }

        if (leftPtr && rightPtr) {
            if (!types.sameType(node.left.type, node.right.type)) {
                diagnostics.error("Pointer comparison '" + op
                        + "' requires identical pointer types, got "
                        + node.left.type.describe()
                        + " and "
                        + node.right.type.describe());
            }
            return;
        }

        if (leftPtr || rightPtr) {
            diagnostics.error("Cannot compare pointer and non-pointer using '" + op
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

    private void visitFunctionCall(SemanticFunctionCallNode node) {
        if (node.function == null) {
            diagnostics.error("Function call has null resolved function");
            return;
        }

        List<SemanticExprNode> args = node.args;
        List<SemanticType> parameterTypes = node.function.parameterTypes();

        boolean isMethod = node.function instanceof MethodSymbol;

        if (isMethod) {
            int expectedUser = Math.max(0, parameterTypes.size() - 1);
            int gotUser = Math.max(0, args.size() - 1);

            if (args.size() != parameterTypes.size()) {
                diagnostics.error("Method call to '"
                        + node.function.signatureString()
                        + "' has wrong arity: expected "
                        + expectedUser
                        + " argument(s) (excluding self), got "
                        + gotUser);
            }
        } else if (args.size() != parameterTypes.size()) {
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

            if (isMethod && i == 0) {
                checkExpr(arg);
                continue;
            }

            int displayIndex = isMethod ? i - 1 : i;

            checkExpr(arg);

            if (!ensureValueExpression(
                    arg,
                    "Argument " + (displayIndex + 1)
                            + " of call to '"
                            + node.function.signatureString()
                            + "'"
            )) {
                continue;
            }

            ensureArgumentCompatibleWithParameter(
                    arg,
                    parameterType,
                    displayIndex,
                    node.function
            );
        }

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
                    + "; use ptr<"
                    + parameterType.describe()
                    + "> instead");
            return;
        }

        if (types.isArrayType(arg.type)) {
            diagnostics.error("Argument " + (index + 1)
                    + " of call to '"
                    + function.signatureString()
                    + "' passes array value directly; use getptr(...) and an ptr<array<...>> parameter");
            return;
        }

        if (types.isNullType(arg.type)) {
            if (!(parameterType instanceof SemanticPtrType)) {
                diagnostics.error("Argument " + (index + 1)
                        + " of call to '"
                        + function.signatureString()
                        + "' passes null to non-pointer parameter type "
                        + types.describeSafe(parameterType));
            }
            return;
        }

        if (arg.isConstant()
                && parameterType instanceof SemanticPrimitiveType primitive) {
            if (!TypeChecker.fitsBigIntegerToPrimitive(
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

        if (types.sameType(parameterType, arg.type)
                || types.isPermissionDowngrade(arg.type, parameterType)) {
            return;
        }

        diagnostics.error("Argument " + (index + 1)
                + " of call to '"
                + function.signatureString()
                + "' has incompatible type: expected "
                + types.describeSafe(parameterType)
                + ", got "
                + types.describeSafe(arg.type));
    }

    private void visitArrayIntrinsicCall(SemanticArrayIntrinsicCallNode node) {
        if (node.kind == null) {
            diagnostics.error("Array intrinsic call has null kind");
            return;
        }

        checkExpr(node.receiver);

        switch (node.kind) {
            case INIT, INIT_ZERO, RESIZE, UNINIT -> ensureRegionPermission(
                    node.receiver,
                    PtrPermission.OWNER,
                    node.kind.runtimeName + "()"
            );

            case MEMSET, MEMCPY, MEMMOVE -> ensureRegionPermission(
                    node.receiver,
                    PtrPermission.MUTABLE,
                    node.kind.runtimeName + "()"
            );

            case LENGTH -> { }
        }

        switch (node.kind) {
            case INIT, INIT_ZERO, RESIZE -> visitArrayAllocIntrinsic(node);
            case UNINIT -> visitArrayUninitIntrinsic(node);
            case MEMSET -> visitArrayMemsetIntrinsic(node);
            case MEMCPY -> visitArrayMemcpyIntrinsic(node);
            case MEMMOVE -> visitArrayMemmoveIntrinsic(node);
            case LENGTH -> visitArrayLengthIntrinsic(node);
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

        SemanticExprNode sourcePtr = node.args.get(0);
        checkExpr(sourcePtr);

        SemanticType sourceArrayType = null;

        if (ensureValueExpression(
                sourcePtr,
                "memcpy() source pointer"
        )) {
            if (!(sourcePtr.type instanceof SemanticPtrType ptr)) {
                diagnostics.error("memcpy() source must be an pointer to an array, got "
                        + types.describeSafe(sourcePtr.type));
            } else if (!types.isArrayType(ptr.referencedType)) {
                diagnostics.error("memcpy() source must be an pointer to an array, got ptr<"
                        + types.describeSafe(ptr.referencedType)
                        + ">");
            } else {
                sourceArrayType = ptr.referencedType;
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

    private void visitArrayLengthIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 0) {
            diagnostics.error("Array intrinsic 'length' expects exactly 0 arguments, got "
                    + node.args.size());
            return;
        }

        types.ensureArrayType(
                node.receiver.type,
                "length() receiver must be an array"
        );

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticPrimitiveType(SemanticPrimitiveKind.UINT32)
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

    private void visitGetPtr(SemanticGetPtrNode node) {
        checkExpr(node.target);

        String intrinsicName = PointerPermissions.creationIntrinsic(
                PointerPermissions.pointerPermission(node.type)
        );

        if (types.isVoidType(node.target.type)) {
            diagnostics.error("Operand of '" + intrinsicName
                    + "' cannot have void type");
            return;
        }

        if (!isStorageBackedLValue(node.target)) {
            diagnostics.error("Operand of '" + intrinsicName
                    + "' must be a pointable storage-backed lvalue");
            return;
        }

        ensureRegionPermission(
                node.target,
                PointerPermissions.pointerPermission(node.type),
                intrinsicName + "(...)"
        );
    }

    public void ensureRegionPermission(
            SemanticExprNode target,
            PtrPermission required,
            String what
    ) {
        PtrPermission region = PointerPermissions.regionPermission(target);

        if (region.allows(required)) {
            return;
        }

        String requiredWord = required == PtrPermission.OWNER
                ? "allocation rights (ptr)"
                : "mutation rights (mview or ptr)";

        String regionWord = region == PtrPermission.READ_ONLY
                ? "'view'-protected (read-only)"
                : "only 'mview'-accessible";

        diagnostics.error(what
                + " requires "
                + requiredWord
                + " over its target, but the target is "
                + regionWord
                + " here");
    }

    private void visitDeref(SemanticDerefNode node) {
        checkExpr(node.expr);

        if (!ensureValueExpression(node.expr, "Operand of '$'")) {
            return;
        }

        if (types.isNullType(node.expr.type)) {
            diagnostics.error("Cannot dereference null");
            return;
        }

        types.ensurePointerType(
                node.expr.type,
                "Operand of '$' must be an pointer type"
        );

        if (node.expr.type instanceof SemanticPtrType ptr
                && types.isVoidType(ptr.referencedType)) {
            diagnostics.error("Cannot dereference ptr<void>");
        }
    }

    private void visitCast(SemanticCastExprNode node) {
        checkExpr(node.expr);

        String syntax = node.checked ? "unsafe_cast" : "cast";

        if (!ensureValueExpression(node.expr, "Operand of '" + syntax + "'")) {
            return;
        }

        if (node.type instanceof SemanticPtrType targetPointer) {
            visitPermissionCast(node, targetPointer);
            return;
        }

        if (!(node.type instanceof SemanticPrimitiveType targetPrimitive)) {
            diagnostics.error("Cast target must be a primitive integer type "
                    + "or a pointer type, got "
                    + types.describeSafe(node.type));
            return;
        }

        if (!(node.expr.type instanceof SemanticPrimitiveType sourcePrimitive)) {
            diagnostics.error("Operand of '" + syntax
                    + "' must be an integer expression, got "
                    + types.describeSafe(node.expr.type));
            return;
        }

        if (!node.checked
                && !TypeChecker.isValuePreservingIntegerWidening(
                        sourcePrimitive.kind,
                        targetPrimitive.kind)) {
            diagnostics.error("'cast' cannot lose data: conversion from "
                    + sourcePrimitive.describe()
                    + " to "
                    + targetPrimitive.describe()
                    + " is not a value-preserving widening; write unsafe_cast<"
                    + targetPrimitive.describe()
                    + ">(...) to make the checked narrowing explicit");
            return;
        }

        if (node.isConstant()) {
            types.requireConstantFitsPrimitive(
                    node.constantValue,
                    targetPrimitive.kind,
                    "Cast to " + targetPrimitive.kind.name()
            );
        }
    }

    private void visitPermissionCast(
            SemanticCastExprNode node,
            SemanticPtrType targetPointer
    ) {
        if (node.checked) {
            diagnostics.error("'unsafe_cast' target must be a primitive "
                    + "integer type, got "
                    + types.describeSafe(node.type));
            return;
        }

        if (types.isNullType(node.expr.type)) {
            return;
        }

        if (!(node.expr.type instanceof SemanticPtrType sourcePointer)) {
            diagnostics.error("'cast' to "
                    + types.describeSafe(node.type)
                    + " requires a pointer or null operand, got "
                    + types.describeSafe(node.expr.type));
            return;
        }

        if (!sourcePointer.referencedType.equals(
                targetPointer.referencedType)) {
            diagnostics.error("'cast' cannot convert between different "
                    + "pointee types: got "
                    + types.describeSafe(node.expr.type)
                    + ", expected "
                    + types.describeSafe(node.type));
            return;
        }

        if (sourcePointer.permission == targetPointer.permission) {
            return;
        }

        if (!PointerPermissions.isPermissionDowngrade(
                node.expr.type,
                node.type)) {
            diagnostics.error("'cast' cannot raise pointer permissions: "
                    + types.describeSafe(node.expr.type)
                    + " cannot be cast to "
                    + types.describeSafe(node.type)
                    + "; permissions only downgrade (ptr -> mview -> view)");
        }
    }

    private void visitMemberAccess(SemanticMemberAccessNode node) {
        if (node.field == null) {
            diagnostics.error("Member access expression has null field symbol");
            return;
        }

        if (node.target == null) {
            diagnostics.error("Member access expression has null target");
            return;
        }

        checkExpr(node.target);
    }

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

        if (expr instanceof SemanticMemberAccessNode memberAccess) {
            return isStorageBackedLValue(memberAccess.target);
        }

        return false;
    }

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

            if (!TypeChecker.fitsBigIntegerToPrimitive(
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
