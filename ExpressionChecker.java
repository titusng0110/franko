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
    private final ArrayIntrinsicChecker arrayIntrinsicChecker;

    public ExpressionChecker(
            DiagnosticBag diagnostics,
            TypeChecker types
    ) {
        this.diagnostics = diagnostics;
        this.types = types;
        this.arrayIntrinsicChecker = new ArrayIntrinsicChecker(
                diagnostics,
                types
        );
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
            arrayIntrinsicChecker.check(this, n);
            return;
        }

        if (node instanceof SemanticAggregateLiteralNode n) {
            visitAggregateLiteral(n);
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

        if (node instanceof SemanticEnumVariantNode) {
            return;
        }

        diagnostics.error("Unknown semantic expression node: "
                + node.getClass().getSimpleName());
    }

    private void visitAggregateLiteral(SemanticAggregateLiteralNode node) {
        if (!types.isValueAssignableType(node.type)) {
            diagnostics.error(
                    "Aggregate literal type "
                    + node.type.describe()
                    + " is not value-assignable; only structs and tuples whose "
                    + "fields/elements are all value-assignable can be constructed "
                    + "as values"
            );
        }

        for (SemanticAggregateFieldInit init : node.fields) {
            checkExpr(init.value);

            types.ensureAssignable(
                    init.field.type,
                    init.value,
                    "Initializer for field '"
                    + init.field.name
                    + "' of "
                    + node.type.describe()
            );
        }
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

        if (!types.ensureValueExpression(node.expr, "Operand of unary '" + node.op + "'")) {
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

            case "~" -> {
                types.ensureIntegral(
                        node.expr.type,
                        "Unary '~' requires an integer operand"
                );

            }

            default -> diagnostics.error("Unknown unary operator '" + node.op + "'");
        }
    }

    private void visitBinOp(SemanticBinOpNode node) {
        checkExpr(node.left);
        checkExpr(node.right);

        String op = node.op;

        if (!types.ensureValueExpression(node.left, "Left operand of '" + op + "'")) {
            return;
        }

        if (!types.ensureValueExpression(node.right, "Right operand of '" + op + "'")) {
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
                    && node.right.type.kind == SastTypeKind.PRIMITIVE
                    && !TypeChecker.fitsBigIntegerToPrimitive(
                            node.left.constantValue,
                            node.right.type.primitive
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
        boolean leftEnum = node.left.type.kind == SastTypeKind.ENUM;
        boolean rightEnum = node.right.type.kind == SastTypeKind.ENUM;

        if (leftEnum || rightEnum) {
            if (leftEnum && rightEnum) {
                if (TypeChecker.sameType(node.left.type, node.right.type)) {
                    return;
                }

                diagnostics.error("Cannot compare '" + node.left.type.describe()
                        + "' with '" + node.right.type.describe()
                        + "' using '" + op
                        + "': both operands must have the same enum type");
                return;
            }

            SastType enumType = leftEnum ? node.left.type : node.right.type;
            SastType otherType = leftEnum ? node.right.type : node.left.type;
            diagnostics.error("Cannot compare enum type '" + enumType.describe()
                    + "' with '" + otherType.describe()
                    + "' using '" + op
                    + "': both operands must have the same enum type");
            return;
        }

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
            if (!TypeChecker.sameType(node.left.type, node.right.type)) {
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
        List<SastType> parameterTypes = node.function.parameterTypes();

        boolean isMethod = node.function.kind == SymbolKind.METHOD;

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
            SastType parameterType = parameterTypes.get(i);

            if (isMethod && i == 0) {
                checkExpr(arg);
                continue;
            }

            int displayIndex = isMethod ? i - 1 : i;

            checkExpr(arg);

            if (!types.ensureValueExpression(
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

        if (!TypeChecker.sameType(node.type, node.function.returnType())) {
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
            SastType parameterType,
            int index,
            Symbol function
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
                    + "' passes array value directly; use getptr(...) and an ptr<[...]> parameter");
            return;
        }

        if (types.isNullType(arg.type)) {
            if (!(parameterType.kind == SastTypeKind.PTR)) {
                diagnostics.error("Argument " + (index + 1)
                        + " of call to '"
                        + function.signatureString()
                        + "' passes null to non-pointer parameter type "
                        + types.describeSafe(parameterType));
            }
            return;
        }

        if (arg.isConstant()
                && parameterType.kind == SastTypeKind.PRIMITIVE) {
            if (!TypeChecker.fitsBigIntegerToPrimitive(
                    arg.constantValue,
                    parameterType.primitive
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

        if (TypeChecker.sameType(parameterType, arg.type)
                || PointerPermissions.isPointerConversion(arg.type, parameterType)) {
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

    private void visitArrayAccess(SemanticArrayAccessNode node) {
        checkExpr(node.target);

        if (!types.ensureValueExpression(node.target, "Indexed expression")) {
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
        if (!(node.target.type.kind == SastTypeKind.STATIC_ARRAY)) {
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
            size = types.parseIntegerLiteral(node.target.type.sizeLiteral);
        } catch (IllegalArgumentException ignored) {

            return;
        }

        if (index.compareTo(size) >= 0) {
            diagnostics.error("Static array index "
                    + index
                    + " is out of bounds for "
                    + node.target.type.describe()
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

        if (node.temporary) {
            if (!(node.type.kind == SastTypeKind.PTR && node.type.permission == PtrPermission.READ_ONLY)) {
                diagnostics.error("Internal error: temporary operator borrow"
                        + " is not a read-only view");
            }

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

    private void visitDeref(SemanticDerefNode node) {        checkExpr(node.expr);

        if (!types.ensureValueExpression(node.expr, "Operand of '$'")) {
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

        if (node.expr.type.kind == SastTypeKind.PTR
                && types.isVoidType(node.expr.type.referencedType)) {
            diagnostics.error("Cannot dereference ptr<void>");
        }
    }

    private void visitCast(SemanticCastExprNode node) {
        checkExpr(node.expr);

        String syntax = node.checked ? "unsafe_cast" : "cast";

        if (!types.ensureValueExpression(node.expr, "Operand of '" + syntax + "'")) {
            return;
        }

        if (node.type.kind == SastTypeKind.PTR) {
            visitPermissionCast(node, node.type);
            return;
        }

        if (node.type.kind == SastTypeKind.ENUM) {
            if (node.expr.type.kind == SastTypeKind.ENUM) {
                if (node.expr.type.enumSymbol == node.type.enumSymbol) {
                    return;
                }

                diagnostics.error("'" + syntax + "' cannot convert enum '"
                        + node.expr.type.enumSymbol.qualifiedName()
                        + "' to enum type '"
                        + node.type.enumSymbol.qualifiedName()
                        + "'");
                return;
            }

            if (node.expr.type.kind == SastTypeKind.PRIMITIVE) {
                if (!node.checked) {
                    diagnostics.error("'cast' cannot convert an integer to enum type '"
                            + node.type.enumSymbol.qualifiedName()
                            + "'; write unsafe_cast<"
                            + node.type.enumSymbol.qualifiedName()
                            + ">(...)");
                    return;
                }

                return;
            }

            diagnostics.error("'unsafe_cast' to enum type '"
                    + node.type.enumSymbol.qualifiedName()
                    + "' requires an integer operand, got "
                    + types.describeSafe(node.expr.type));
            return;
        }

        if (node.expr.type.kind == SastTypeKind.ENUM) {
            if (!(node.type.kind == SastTypeKind.PRIMITIVE)) {
                diagnostics.error("Cast target must be a primitive integer type, a pointer type, or an enum type, got "
                        + types.describeSafe(node.type));
                return;
            }

            if (types.isValuePreservingEnumToIntegerCast(node.expr.type, node.type.primitive)) {
                return;
            }

            if (!node.checked) {
                diagnostics.error("'cast' cannot lose data: enum '"
                        + node.expr.type.enumSymbol.qualifiedName()
                        + "' is backed by "
                        + node.expr.type.enumSymbol.backingKind.name().toLowerCase()
                        + ", which does not widen value-preservingly to "
                        + node.type.describe()
                        + "; write unsafe_cast<"
                        + node.type.describe()
                        + ">(...)");
                return;
            }

            return;
        }

        if (!(node.type.kind == SastTypeKind.PRIMITIVE)) {
            diagnostics.error("Cast target must be a primitive integer type, a pointer type, or an enum type, got "
                    + types.describeSafe(node.type));
            return;
        }

        if (!(node.expr.type.kind == SastTypeKind.PRIMITIVE)) {
            diagnostics.error("Operand of '" + syntax
                    + "' must be an integer expression, got "
                    + types.describeSafe(node.expr.type));
            return;
        }

        if (!node.checked
                && !TypeChecker.isValuePreservingIntegerWidening(
                        node.expr.type.primitive,
                        node.type.primitive)) {
            diagnostics.error("'cast' cannot lose data: conversion from "
                    + node.expr.type.describe()
                    + " to "
                    + node.type.describe()
                    + " is not a value-preserving widening; write unsafe_cast<"
                    + node.type.describe()
                    + ">(...) to make the checked narrowing explicit");
            return;
        }

        if (node.isConstant()) {
            types.requireConstantFitsPrimitive(
                    node.constantValue,
                    node.type.primitive,
                    "Cast to " + node.type.primitive.name().toLowerCase()
            );
        }
    }

    private void visitPermissionCast(
            SemanticCastExprNode node,
            SastType targetPointer
    ) {
        if (node.checked) {
            diagnostics.error("'unsafe_cast' target must be a primitive "
                    + "integer type or an enum type, got "
                    + types.describeSafe(node.type));
            return;
        }

        if (types.isNullType(node.expr.type)) {
            return;
        }

        if (!(node.expr.type.kind == SastTypeKind.PTR)) {
            diagnostics.error("'cast' to "
                    + types.describeSafe(node.type)
                    + " requires a pointer or null operand, got "
                    + types.describeSafe(node.expr.type));
            return;
        }

        if (!node.expr.type.referencedType.equals(
                node.type.referencedType)) {
            if (node.expr.type.referencedType.kind == SastTypeKind.STATIC_ARRAY
                    && node.type.referencedType.kind == SastTypeKind.DYNAMIC_ARRAY
                    && node.expr.type.referencedType.elementType.equals(
                            node.type.referencedType.elementType)) {
                if (node.type.permission == PtrPermission.OWNER) {
                    diagnostics.error("'cast' cannot decay ownership: "
                            + types.describeSafe(node.expr.type)
                            + " cannot be cast to "
                            + types.describeSafe(node.type)
                            + "; the static array's storage is borrowed by a "
                            + "hidden dynamic-array descriptor, so only "
                            + "mview<[T]> and view<[T]> accept the "
                            + "decay");
                    return;
                }

                if (!node.expr.type.permission.allows(node.type.permission)) {
                    diagnostics.error("'cast' cannot raise pointer "
                            + "permissions: "
                            + types.describeSafe(node.expr.type)
                            + " cannot be cast to "
                            + types.describeSafe(node.type)
                            + "; permissions only downgrade (ptr -> mview -> view)");
                    return;
                }

                return;
            }

            diagnostics.error("'cast' cannot convert between different "
                    + "pointee types: got "
                    + types.describeSafe(node.expr.type)
                    + ", expected "
                    + types.describeSafe(node.type)
                    + "; only mview<[T,N]>/view<[T,N]> decay to "
                    + "mview<[T]>/view<[T]> (T must match)");
            return;
        }

        if (node.expr.type.permission == node.type.permission) {
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

    boolean isStorageBackedLValue(SemanticExprNode expr) {
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

    void ensureUInt32Compatible(
            SemanticExprNode expr,
            String message,
            boolean allowZero
    ) {
        if (expr == null) {
            diagnostics.error(message + ": expression cannot be null");
            return;
        }

        checkExpr(expr);

        if (!types.ensureValueExpression(expr, message)) {
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
                diagnostics.error(message + ": value does not fit in uint32");
            }

            return;
        }

        if (!(expr.type.kind == SastTypeKind.PRIMITIVE
                && expr.type.primitive == SemanticPrimitiveKind.UINT32)) {
            diagnostics.error(message + ": expected uint32, got "
                    + types.describeSafe(expr.type));
        }
    }

    void ensureScalarCompatible(
            SemanticExprNode expr,
            SemanticPrimitiveKind kind,
            String message
    ) {
        if (expr == null) {
            diagnostics.error(message + ": expression cannot be null");
            return;
        }

        checkExpr(expr);

        if (!types.ensureValueExpression(expr, message)) {
            return;
        }

        if (expr.isConstant()) {
            types.requireConstantFitsPrimitive(
                    expr.constantValue,
                    kind,
                    message
            );
            return;
        }

        if (!(expr.type.kind == SastTypeKind.PRIMITIVE
                && expr.type.primitive == kind)) {
            diagnostics.error(message + ": expected "
                    + kind.name().toLowerCase()
                    + ", got "
                    + types.describeSafe(expr.type));
        }
    }

    void ensureStaticArrayByteAccessInBounds(
            SastType arrayType,
            SemanticExprNode offsetExpr,
            int byteWidth,
            String message
    ) {
        if (!(arrayType.kind == SastTypeKind.STATIC_ARRAY)) {
            return;
        }

        if (offsetExpr == null
                || !offsetExpr.isConstant()
                || offsetExpr.constantValue == null) {
            return;
        }

        BigInteger offset = offsetExpr.constantValue;

        if (offset.signum() < 0) {
            return;
        }

        SastType staticArray = arrayType;
        BigInteger size = staticArraySize(staticArray);

        if (size == null) {
            return;
        }

        if (offset.add(BigInteger.valueOf(byteWidth)).compareTo(size) > 0) {
            diagnostics.error(message
                    + " is out of bounds for "
                    + arrayType.describe()
                    + " with size "
                    + size
                    + ": byte offset "
                    + offset
                    + ", width "
                    + byteWidth);
        }
    }

    void ensureStaticArrayBitIndexInBounds(
            SastType arrayType,
            SemanticExprNode bitIndexExpr,
            String message
    ) {
        if (!(arrayType.kind == SastTypeKind.STATIC_ARRAY)) {
            return;
        }

        if (bitIndexExpr == null
                || !bitIndexExpr.isConstant()
                || bitIndexExpr.constantValue == null) {
            return;
        }

        BigInteger bitIndex = bitIndexExpr.constantValue;

        if (bitIndex.signum() < 0) {
            return;
        }

        SastType staticArray = arrayType;
        BigInteger size = staticArraySize(staticArray);

        if (size == null) {
            return;
        }

        BigInteger totalBits = size.multiply(BigInteger.valueOf(8));

        if (bitIndex.compareTo(totalBits) >= 0) {
            diagnostics.error(message
                    + " is out of bounds for "
                    + arrayType.describe()
                    + " with "
                    + totalBits
                    + " bits: bit index "
                    + bitIndex);
        }
    }

    private BigInteger staticArraySize(SastType staticArray) {
        try {
            return types.parseIntegerLiteral(staticArray.sizeLiteral);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    void ensureStaticArrayRangeInBounds(
            SastType arrayType,
            SemanticExprNode startExpr,
            SemanticExprNode countExpr,
            String message
    ) {
        if (!(arrayType.kind == SastTypeKind.STATIC_ARRAY)) {
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
            size = types.parseIntegerLiteral(arrayType.sizeLiteral);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        BigInteger end = start.add(count);

        if (start.compareTo(size) > 0 || end.compareTo(size) > 0) {
            diagnostics.error(message
                    + " is out of bounds for "
                    + arrayType.describe()
                    + " with size "
                    + size
                    + ": start "
                    + start
                    + ", count "
                    + count);
        }
    }

    void ensureMemsetValueCompatible(SemanticExprNode value) {
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

        if (!(value.type.kind == SastTypeKind.PRIMITIVE
                && value.type.primitive == SemanticPrimitiveKind.UINT8)) {
            diagnostics.error("memset() fill value must be uint8/char, got "
                    + types.describeSafe(value.type));
        }
    }

}
