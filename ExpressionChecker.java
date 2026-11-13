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

    public void checkExpr(SastNode node) {
        if (node == null) {
            return;
        }

        if (node.kind == SastKind.INT_LITERAL) {
            return;
        }

        if (node.kind == SastKind.NULL_LITERAL) {

            return;
        }

        if (node.kind == SastKind.VAR_EXPR) {
            visitVarExpr(node);
            return;
        }

        if (node.kind == SastKind.UNARY_OP) {
            visitUnaryOp(node);
            return;
        }

        if (node.kind == SastKind.BIN_OP) {
            visitBinOp(node);
            return;
        }

        if (node.kind == SastKind.ARRAY_ACCESS) {
            visitArrayAccess(node);
            return;
        }

        if (node.kind == SastKind.FUNCTION_CALL) {
            visitFunctionCall(node);
            return;
        }

        if (node.kind == SastKind.ARRAY_INTRINSIC_CALL) {
            arrayIntrinsicChecker.check(this, node);
            return;
        }

        if (node.kind == SastKind.AGGREGATE_LITERAL) {
            visitAggregateLiteral(node);
            return;
        }

        if (node.kind == SastKind.GET_PTR) {
            visitGetPtr(node);
            return;
        }

        if (node.kind == SastKind.DEREF) {
            visitDeref(node);
            return;
        }

        if (node.kind == SastKind.CAST) {
            visitCast(node);
            return;
        }

        if (node.kind == SastKind.MEMBER_ACCESS) {
            visitMemberAccess(node);
            return;
        }

        if (node.kind == SastKind.ENUM_VARIANT) {
            return;
        }

        diagnostics.error("Unknown semantic expression node: "
                + node.getClass().getSimpleName());
    }

    private void visitAggregateLiteral(SastNode node) {
        if (!types.isValueAssignableType(node.type)) {
            diagnostics.error(
                    "Aggregate literal type "
                    + node.type.describe()
                    + " is not value-assignable; only structs and tuples whose "
                    + "fields/elements are all value-assignable can be constructed "
                    + "as values. Aggregate construction copies the value into "
                    + "its destination, so every field or element must itself "
                    + "be a value type. Replace array or pointer-only fields "
                    + "with value types, or build the aggregate through a "
                    + "pointer."
            );
        }

        for (SastNode init : node.fields) {
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

    private void visitVarExpr(SastNode node) {
        if (node.symbol == null) {
            diagnostics.error("Variable expression has null symbol");
            return;
        }

        if (types.isVoidType(node.type)) {
            diagnostics.error("Variable '" + node.symbol.qualifiedName()
                    + "' has invalid void type. void denotes the absence of a "
                    + "value and cannot be stored in a variable. Declare the "
                    + "variable with a concrete type, or remove the "
                    + "declaration.");
        }
    }

    private void visitUnaryOp(SastNode node) {
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

    private void visitBinOp(SastNode node) {
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
                diagnostics.error("Division by zero in compile-time constant "
                        + "expression. The right operand is a folded constant "
                        + "with value zero, and division or remainder by zero "
                        + "has no defined result. Guard the divisor, or change "
                        + "the constant operand so it is nonzero.");
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
                        + node.right.type.describe()
                        + ". A constant left operand is fitted to the right "
                        + "operand's integer type before the shift is "
                        + "evaluated. Use a constant that fits that type, or "
                        + "change the right operand to the integer type you "
                        + "intend.");
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
            SastNode node,
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
                        + "': both operands must have the same enum type. "
                        + "Enums are nominal types, so different declarations "
                        + "remain distinct even with the same backing integer "
                        + "type. Compare values of the same enum type, or "
                        + "convert the enum to its backing integer with "
                        + "unsafe_cast and compare integers.");
                return;
            }

            SastType enumType = leftEnum ? node.left.type : node.right.type;
            SastType otherType = leftEnum ? node.right.type : node.left.type;
            diagnostics.error("Cannot compare enum type '" + enumType.describe()
                    + "' with '" + otherType.describe()
                    + "' using '" + op
                    + "': both operands must have the same enum type. Enums "
                    + "are nominal types, so an enum never compares directly "
                    + "with an integer or another enum. Compare two values of "
                    + "the same enum type, or convert the enum to its backing "
                    + "integer with unsafe_cast.");
            return;
        }

        boolean leftPtr = types.isPointerType(node.left.type);
        boolean rightPtr = types.isPointerType(node.right.type);
        boolean leftNull = types.isNullType(node.left.type);
        boolean rightNull = types.isNullType(node.right.type);

        if (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")) {
            if (leftPtr || rightPtr) {
                diagnostics.error("Pointer comparisons must only use equality "
                        + "operators ('==' or '!='), but got '" + op
                        + "'. Pointers have no defined ordering across objects, "
                        + "so only equality is meaningful. Use '==' or '!=', or "
                        + "compare array indices to order elements.");
                return;
            }

            if (leftNull || rightNull) {
                diagnostics.error("Null comparisons must only use equality "
                        + "operators ('==' or '!='), but got '" + op
                        + "'. Null is the empty pointer value and has no "
                        + "ordering relative to any pointer. Use '==' or '!=' "
                        + "to test for null.");
                return;
            }
        }

        if (leftNull || rightNull) {
            SastNode other = leftNull ? node.right : node.left;

            if (types.isNullType(other.type)) {

                return;
            }

            if (!types.isPointerType(other.type)) {
                diagnostics.error("Cannot compare null with non-pointer type '"
                        + other.type.describe()
                        + "'. Null is only a valid pointer value, and the "
                        + "comparison would have no pointer type to check. "
                        + "Compare against a pointer, or remove the null "
                        + "comparison for this value.");
            }
            return;
        }

        if (leftPtr && rightPtr) {
            if (!TypeChecker.sameType(node.left.type, node.right.type)) {
                diagnostics.error("Pointer comparison '" + op
                        + "' requires identical pointer types, got "
                        + node.left.type.describe()
                        + " and "
                        + node.right.type.describe()
                        + ". Franko compares pointers only when both point to "
                        + "the same type and permission, with no implicit "
                        + "pointer conversions in comparisons. Use pointers "
                        + "with identical pointee types and permissions, "
                        + "casting only to downgrade permission.");
            }
            return;
        }

        if (leftPtr || rightPtr) {
            diagnostics.error("Cannot compare pointer and non-pointer using '" + op
                    + "', got "
                    + node.left.type.describe()
                    + " and "
                    + node.right.type.describe()
                    + ". Only two pointers, two integers, two enums of the "
                    + "same type, or two structs of the same type can be "
                    + "compared. Compare the pointer with another pointer or "
                    + "null, or dereference it with $ to compare the "
                    + "pointee.");
            return;
        }

        types.ensureMixedIntegralOperandsWithConstantFit(
                node.left,
                node.right,
                op
        );
    }

    private void visitFunctionCall(SastNode node) {
        if (node.function == null) {
            diagnostics.error("Function call has null resolved function");
            return;
        }

        List<SastNode> args = node.args;
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
                        + gotUser
                        + ". Every declared user parameter except the "
                        + "implicit self receiver must be supplied exactly "
                        + "once. Add the missing arguments, or remove the "
                        + "extra ones to match the method declaration.");
            }
        } else if (args.size() != parameterTypes.size()) {
            diagnostics.error("Function call to '"
                    + node.function.signatureString()
                    + "' has wrong arity: expected "
                    + parameterTypes.size()
                    + " argument(s), got "
                    + args.size()
                    + ". Every declared parameter must be supplied exactly "
                    + "once, with optional parameters bound by name. Pass "
                    + "exactly one argument per parameter, or add the missing "
                    + "arguments.");
        }

        int count = Math.min(args.size(), parameterTypes.size());

        for (int i = 0; i < count; i++) {
            SastNode arg = args.get(i);
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
            SastNode arg,
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
                    + "' has invalid void type. void is not a value type, so "
                    + "no argument can be passed for this parameter. Remove "
                    + "the parameter, or give it a concrete value type.");
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
                    + "> instead. Arrays are passed by pointer in Franko, "
                    + "and an array-typed parameter would require copying the "
                    + "whole array by value. Declare the parameter as ptr<T> "
                    + "or view<T>, and pass getptr or getview of the array.");
            return;
        }

        if (types.isArrayType(arg.type)) {
            diagnostics.error("Argument " + (index + 1)
                    + " of call to '"
                    + function.signatureString()
                    + "' passes array value directly; use getptr(...) and an "
                    + "ptr<[...]> parameter. Array values are not copied into "
                    + "calls, so callees receive pointer access instead. Pass "
                    + "getptr(xs) for a mutable parameter or getview(xs) for a "
                    + "read-only parameter.");
            return;
        }

        if (types.isNullType(arg.type)) {
            if (!(parameterType.kind == SastTypeKind.PTR)) {
                diagnostics.error("Argument " + (index + 1)
                        + " of call to '"
                        + function.signatureString()
                        + "' passes null to non-pointer parameter type "
                        + types.describeSafe(parameterType)
                        + ". Null is only a valid pointer value and cannot "
                        + "stand in for a concrete value type. Pass a value "
                        + "of the parameter type, or declare the parameter as "
                        + "a pointer.");
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
                        + parameterType.describe()
                        + ". Fluid integer constants are inferred against the "
                        + "parameter type, and a value outside its range would "
                        + "be truncated. Pass a constant that fits the "
                        + "parameter type, or widen the parameter type.");
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
                + types.describeSafe(arg.type)
                + ". Franko has no implicit integer conversions; only exact "
                + "types and the documented pointer permission conversions "
                + "are accepted. Cast the argument explicitly with cast or "
                + "unsafe_cast, or pass a value of the expected type.");
    }

    private void visitArrayAccess(SastNode node) {
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

    private void ensureStaticArrayIndexInBounds(SastNode node) {
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
                    + size
                    + ". Static array indices must name an element slot "
                    + "inside the declared length, or the access would read "
                    + "or write adjacent storage. Use an index below "
                    + size
                    + ", or enlarge the array.");
        }
    }

    private void visitGetPtr(SastNode node) {
        checkExpr(node.target);

        String intrinsicName = PointerPermissions.creationIntrinsic(
                PointerPermissions.pointerPermission(node.type)
        );

        if (types.isVoidType(node.target.type)) {
            diagnostics.error("Operand of '" + intrinsicName
                    + "' cannot have void type. A void call produces no "
                    + "value, so there is no storage to point at. Remove the "
                    + "intrinsic, or pass a value-producing expression.");
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
                    + "' must be a pointable storage-backed lvalue. The "
                    + "intrinsic returns a pointer to real storage, and "
                    + "temporaries, literals, and call results have no stable "
                    + "address. Store the value in a variable, field, or "
                    + "array element, and take a pointer to that.");
            return;
        }

        Symbol storage = node.target.storageSymbol();

        if (storage != null
                && storage.isConst
                && PointerPermissions.pointerPermission(node.type)
                        != PtrPermission.READ_ONLY) {
            diagnostics.error("Cannot create a mutable pointer to const "
                    + "variable '"
                    + storage.name
                    + "'; const storage only allows getview(...). const makes "
                    + "the storage immutable, so a mutable or owning pointer "
                    + "would allow writes that violate the declaration. Use "
                    + "getview(...) instead, or remove const from '"
                    + storage.name
                    + "'.");
        }

        ensureRegionPermission(
                node.target,
                PointerPermissions.pointerPermission(node.type),
                intrinsicName + "(...)"
        );
    }

    public void ensureRegionPermission(
            SastNode target,
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
                + " here. An operation cannot use more permission than the "
                + "storage region already has, because that would allow "
                + "writes or ownership through a weaker view. Use a receiver "
                + "that is not const or view-restricted, or perform the "
                + "read-only operation instead.");
    }

    private void visitDeref(SastNode node) {        checkExpr(node.expr);

        if (!types.ensureValueExpression(node.expr, "Operand of '$'")) {
            return;
        }

        if (types.isNullType(node.expr.type)) {
            diagnostics.error("Cannot dereference null. Null is the empty "
                    + "pointer value and refers to no storage, so there is no "
                    + "pointee to read or write. Guard the pointer with a "
                    + "null check and dereference it only on the non-null "
                    + "path, or initialize it from live storage with "
                    + "getptr/getview/getmview.");
            return;
        }

        types.ensurePointerType(
                node.expr.type,
                "Operand of '$' must be an pointer type"
        );

        if (node.expr.type.kind == SastTypeKind.PTR
                && types.isVoidType(node.expr.type.referencedType)) {
            diagnostics.error("Cannot dereference ptr<void>. void has no "
                    + "size or layout, so the compiler cannot form a value "
                    + "from the pointee. Use a pointer with a concrete pointee "
                    + "type instead.");
        }
    }

    private void visitCast(SastNode node) {
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
                        + "'. Enums are nominal and have no conversion "
                        + "between distinct enum declarations, even with the "
                        + "same backing type. Convert through the backing "
                        + "integer with unsafe_cast, then to the target enum "
                        + "with unsafe_cast.");
                return;
            }

            if (node.expr.type.kind == SastTypeKind.PRIMITIVE) {
                if (!node.checked) {
                    diagnostics.error("'cast' cannot convert an integer to enum type '"
                            + node.type.enumSymbol.qualifiedName()
                            + "'; write unsafe_cast<"
                            + node.type.enumSymbol.qualifiedName()
                            + ">(...). Integer-to-enum conversion is "
                            + "runtime-checked because arbitrary integers may "
                            + "not name a declared variant. Use unsafe_cast<"
                            + node.type.enumSymbol.qualifiedName()
                            + ">(expr) to perform the checked conversion.");
                    return;
                }

                return;
            }

            diagnostics.error("'unsafe_cast' to enum type '"
                    + node.type.enumSymbol.qualifiedName()
                    + "' requires an integer operand, got "
                    + types.describeSafe(node.expr.type)
                    + ". The runtime check maps the operand's integer value "
                    + "onto the enum's backing type and declared variants. "
                    + "Pass an integer expression, converting it to the "
                    + "backing integer type first if needed.");
            return;
        }

        if (node.expr.type.kind == SastTypeKind.ENUM) {
            if (!(node.type.kind == SastTypeKind.PRIMITIVE)) {
                diagnostics.error("Cast target must be a primitive integer type, a pointer type, or an enum type, got "
                        + types.describeSafe(node.type)
                        + ". These are the only types with defined cast rules; "
                        + "arrays, structs, tuples, and void have no conversion. "
                        + "Cast to a primitive integer, pointer, or enum type, "
                        + "or remove the cast.");
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
                        + ">(...). Plain cast only permits conversions that "
                        + "preserve every source value, and narrowing or "
                        + "signedness changes can lose data. Use unsafe_cast<"
                        + node.type.describe()
                        + ">(...) to make the checked conversion explicit.");
                return;
            }

            return;
        }

        if (!(node.type.kind == SastTypeKind.PRIMITIVE)) {
            diagnostics.error("Cast target must be a primitive integer type, a pointer type, or an enum type, got "
                    + types.describeSafe(node.type)
                    + ". These are the only types with defined cast rules; "
                    + "arrays, structs, tuples, and void have no conversion. "
                    + "Cast to a primitive integer, pointer, or enum type, or "
                    + "remove the cast.");
            return;
        }

        if (!(node.expr.type.kind == SastTypeKind.PRIMITIVE)) {
            diagnostics.error("Operand of '" + syntax
                    + "' must be an integer expression, got "
                    + types.describeSafe(node.expr.type)
                    + ". A checked integer cast has no defined meaning for "
                    + "aggregates, arrays, pointers, or null. Pass an integer "
                    + "expression, or use cast<...> for a pointer permission "
                    + "downgrade.");
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
                    + ">(...) to make the checked narrowing explicit. Plain "
                    + "cast is reserved for conversions that preserve every "
                    + "source value, and narrowing or signedness changes can "
                    + "lose data. Use unsafe_cast<"
                    + node.type.describe()
                    + ">(...) for the runtime-checked conversion.");
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
            SastNode node,
            SastType targetPointer
    ) {
        if (node.checked) {
            diagnostics.error("'unsafe_cast' target must be a primitive "
                    + "integer type or an enum type, got "
                    + types.describeSafe(node.type)
                    + ". unsafe_cast performs checked integer conversions and "
                    + "cannot change pointer permissions. Use cast<T>(...) for "
                    + "a pointer permission downgrade, or cast to an integer "
                    + "target.");
            return;
        }

        if (types.isNullType(node.expr.type)) {
            return;
        }

        if (!(node.expr.type.kind == SastTypeKind.PTR)) {
            diagnostics.error("'cast' to "
                    + types.describeSafe(node.type)
                    + " requires a pointer or null operand, got "
                    + types.describeSafe(node.expr.type)
                    + ". Pointer casts only adjust permission or decay an "
                    + "array view; they never create a pointer from a "
                    + "non-pointer value. Pass a pointer or null, or create a "
                    + "pointer with getptr/getview/getmview.");
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
                            + "decay. An owning ptr<[T]> would claim ownership "
                            + "of storage that the original array still owns. "
                            + "Cast to mview<[T]> or view<[T]> instead, and "
                            + "keep the original array alive for the view's "
                            + "lifetime.");
                    return;
                }

                if (!node.expr.type.permission.allows(node.type.permission)) {
                    diagnostics.error("'cast' cannot raise pointer "
                            + "permissions: "
                            + types.describeSafe(node.expr.type)
                            + " cannot be cast to "
                            + types.describeSafe(node.type)
                            + "; permissions only downgrade (ptr -> mview -> "
                            + "view). Allowing a read-only view to become "
                            + "mutable or owning would let code write or free "
                            + "storage it does not control. Cast to the same "
                            + "or weaker permission, or obtain a stronger "
                            + "pointer from the original storage.");
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
                    + "mview<[T]>/view<[T]> (T must match). A pointer cast "
                    + "may change permission or array extent, but "
                    + "reinterpretation between pointee types would break "
                    + "type and memory safety. Keep the pointee type "
                    + "unchanged; for arrays, decay a static array view of "
                    + "the same element type.");
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
                    + "; permissions only downgrade (ptr -> mview -> view). "
                    + "Allowing a read-only view to become mutable or owning "
                    + "would let code write or free storage it does not "
                    + "control. Cast to the same or weaker permission, or "
                    + "obtain a stronger pointer from the original storage.");
        }
    }

    private void visitMemberAccess(SastNode node) {
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

    boolean isStorageBackedLValue(SastNode expr) {
        if (expr == null) {
            return false;
        }

        if (!expr.isLValue()) {
            return false;
        }

        if (expr.kind == SastKind.VAR_EXPR) {
            return true;
        }

        if (expr.kind == SastKind.DEREF) {
            return true;
        }

        if (expr.kind == SastKind.ARRAY_ACCESS) {
            return isStorageBackedLValue(expr.target);
        }

        if (expr.kind == SastKind.MEMBER_ACCESS) {
            return isStorageBackedLValue(expr.target);
        }

        return false;
    }

    public void ensureArraySizeCompatible(
            SastNode size,
            String message
    ) {
        ensureUInt32Compatible(
                size,
                message,
                false
        );
    }

    public void ensureArrayIndexCompatible(
            SastNode index,
            String message
    ) {
        ensureUInt32Compatible(
                index,
                message,
                true
        );
    }

    void ensureUInt32Compatible(
            SastNode expr,
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
                diagnostics.error(message + ": value cannot be negative. "
                        + "Indices and sizes are uint32 quantities, so a "
                        + "negative value has no representation. Use a "
                        + "non-negative constant or a uint32-typed value.");
                return;
            }

            if (!allowZero && expr.constantValue.signum() == 0) {
                diagnostics.error(message + ": value must be greater than zero. "
                        + "A zero size cannot describe any element storage "
                        + "for this operation. Pass a size of at least one.");
                return;
            }

            if (!TypeChecker.fitsBigIntegerToPrimitive(
                    expr.constantValue,
                    SemanticPrimitiveKind.UINT32
            )) {
                diagnostics.error(message + ": value does not fit in uint32. "
                        + "uint32 can represent at most 4294967295, and "
                        + "larger values would be truncated. Use a smaller "
                        + "value.");
            }

            return;
        }

        if (!(expr.type.kind == SastTypeKind.PRIMITIVE
                && expr.type.primitive == SemanticPrimitiveKind.UINT32)) {
            diagnostics.error(message + ": expected uint32, got "
                    + types.describeSafe(expr.type)
                    + ". Franko does not implicitly convert between integer "
                    + "types, and this position is fixed to uint32. Widen the "
                    + "value with cast<uint32>(...) when that is "
                    + "value-preserving, or use unsafe_cast<uint32>(...) for "
                    + "a checked conversion.");
        }
    }

    void ensureScalarCompatible(
            SastNode expr,
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
                    + types.describeSafe(expr.type)
                    + ". This argument must have exactly the requested "
                    + "primitive type, and Franko does not implicitly convert "
                    + "scalars. Cast the value explicitly to "
                    + kind.name().toLowerCase()
                    + ".");
        }
    }

    void ensureStaticArrayByteAccessInBounds(
            SastType arrayType,
            SastNode offsetExpr,
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
                    + byteWidth
                    + ". A byte access must cover only bytes inside the "
                    + "static array, or it would read or write adjacent "
                    + "storage. Reduce the offset or width so offset + width "
                    + "does not exceed the array size.");
        }
    }

    void ensureStaticArrayBitIndexInBounds(
            SastType arrayType,
            SastNode bitIndexExpr,
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
                    + bitIndex
                    + ". Bit intrinsics address bits inside the array's byte "
                    + "storage, so an index at or past the total bit count "
                    + "would touch neighboring storage. Use a bit index below "
                    + totalBits
                    + ", or enlarge the array.");
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
            SastNode startExpr,
            SastNode countExpr,
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
                    + count
                    + ". The range [start, start + count) must stay inside "
                    + "the array, or the operation would access storage "
                    + "beyond it. Reduce start or count so start + count does "
                    + "not exceed the array size.");
        }
    }

    void ensureMemsetValueCompatible(SastNode value) {
        if (value == null) {
            diagnostics.error("memset() fill value cannot be null. memset "
                    + "writes one uint8 byte value into every destination "
                    + "byte, and null is a pointer value. Pass a uint8 or char "
                    + "value between 0 and 255.");
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
                    + types.describeSafe(value.type)
                    + ". memset writes a single byte, so the fill value must "
                    + "have byte width. Cast the value with "
                    + "unsafe_cast<uint8>(...), or pass a byte literal.");
        }
    }

}
