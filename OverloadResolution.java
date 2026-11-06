import java.util.ArrayList;
import java.util.List;

final class OverloadResolution {

    static int constantFitCost(
            CallArg arg,
            SastType parameterType
    ) {
        if (arg == null || arg.isLiteral()) {
            return 0;
        }

        if (arg.lowered == null || !arg.lowered.isConstant()) {
            return 0;
        }

        if (!(parameterType.kind == SastTypeKind.PRIMITIVE)) {
            return 0;
        }

        if (TypeChecker.sameType(arg.lowered.type, parameterType)) {
            return 0;
        }

        int bits = TypeChecker.bitWidth(parameterType.primitive);
        int unsignedPenalty = TypeChecker.isSigned(parameterType.primitive) ? 0 : 1;

        return 1 + 2 * bits + unsignedPenalty;
    }

    static boolean sameParameterSignature(
            List<Symbol> a,
            List<Symbol> b
    ) {
        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            Symbol left = a.get(i);
            Symbol right = b.get(i);

            if (!TypeChecker.sameType(left.type, right.type)) {
                return false;
            }
        }

        return true;
    }

    static boolean sameMethodUserParameterSignature(
            List<Symbol> a,
            List<Symbol> b
    ) {
        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 1; i < a.size(); i++) {
            Symbol left = a.get(i);
            Symbol right = b.get(i);

            if (!TypeChecker.sameType(left.type, right.type)) {
                return false;
            }
        }

        return true;
    }

    static boolean isApplicableFunction(
            Symbol fn,
            List<CallArg> args
    ) {
        if (fn.arity() != args.size()) {
            return false;
        }

        List<SastType> parameterTypes = fn.parameterTypes();

        for (int i = 0; i < args.size(); i++) {
            SastType parameterType = parameterTypes.get(i);
            CallArg arg = args.get(i);

            if (!isArgumentApplicableToParameter(arg, parameterType)) {
                return false;
            }
        }

        return true;
    }

    static boolean isApplicableMethod(
            Symbol method,
            List<CallArg> userArgs
    ) {
        if (method.parameters.size() != userArgs.size() + 1) {
            return false;
        }

        for (int i = 0; i < userArgs.size(); i++) {
            SastType parameterType =
                    method.parameters.get(i + 1).type;
            CallArg arg = userArgs.get(i);

            if (!isArgumentApplicableToParameter(arg, parameterType)) {
                return false;
            }
        }

        return true;
    }

    static int countPermissionDowngrades(
            List<CallArg> args,
            List<SastType> parameterTypes
    ) {
        int count = 0;

        for (int i = 0; i < args.size(); i++) {
            CallArg arg = args.get(i);

            if (arg == null) {
                continue;
            }

            SastType parameterType = parameterTypes.get(i);

            if (arg.isLiteral()) {
                count += arrayLiteralDowngradeCost(arg.literal, parameterType);
                continue;
            }

            if (arg.lowered == null || arg.lowered.type == null) {
                continue;
            }

            if (!TypeChecker.sameType(arg.lowered.type, parameterType)
                    && arg.lowered.type.kind == SastTypeKind.PTR) {
                count += PointerPermissions.isStaticArrayViewDecay(
                        arg.lowered.type,
                        parameterType)
                        ? 2
                        : 1;
            }
        }

        return count;
    }

    static int countMethodPermissionDowngrades(
            Symbol method,
            List<CallArg> userArgs
    ) {
        int count = 0;

        for (int i = 0; i < userArgs.size(); i++) {
            CallArg arg = userArgs.get(i);

            if (arg == null) {
                continue;
            }

            SastType parameterType = method.parameters.get(i + 1).type;

            if (arg.isLiteral()) {
                count += arrayLiteralDowngradeCost(arg.literal, parameterType);
                continue;
            }

            if (arg.lowered == null || arg.lowered.type == null) {
                continue;
            }

            if (!TypeChecker.sameType(arg.lowered.type, parameterType)
                    && arg.lowered.type.kind == SastTypeKind.PTR) {
                count += PointerPermissions.isStaticArrayViewDecay(
                        arg.lowered.type,
                        parameterType)
                        ? 2
                        : 1;
            }
        }

        return count;
    }

    static boolean isOperatorArgumentApplicable(
            Symbol method,
            CallArg arg
    ) {
        if (method.parameters.size() != 2) {
            return false;
        }

        SastType parameterType = method.parameters.get(1).type;

        if (!(parameterType.kind == SastTypeKind.PTR)
                || parameterType.permission != PtrPermission.READ_ONLY) {
            return false;
        }

        SastType referenced = parameterType.referencedType;

        if (arg.isLiteral()) {
            return isArrayLiteralShapeApplicable(arg.literal, referenced);
        }

        if (arg.lowered == null || arg.lowered.type == null) {
            return false;
        }

        SastNode lowered = arg.lowered;

        if (lowered.type.kind == SastTypeKind.NULL) {
            return false;
        }

        if (lowered.isConstant()
                && referenced.kind == SastTypeKind.PRIMITIVE) {
            return TypeChecker.fitsBigIntegerToPrimitive(
                    lowered.constantValue,
                    referenced.primitive
            );
        }

        if (TypeChecker.sameType(lowered.type, referenced)) {
            return true;
        }

        return lowered.type.kind == SastTypeKind.PTR
                && referenced.kind == SastTypeKind.PTR
                && PointerPermissions.isPointerConversion(
                        lowered.type,
                        referenced);
    }

    static List<Symbol> preferOperatorConstantFitMatches(
            List<Symbol> matches,
            CallArg arg
    ) {
        if (matches.size() <= 1) {
            return matches;
        }

        int best = Integer.MAX_VALUE;

        for (Symbol method : matches) {
            best = Math.min(
                    best,
                    operatorConstantFitCost(
                            arg,
                            method.parameters.get(1).type)
            );
        }

        List<Symbol> preferred = new ArrayList<>();

        for (Symbol method : matches) {
            if (operatorConstantFitCost(
                    arg,
                    method.parameters.get(1).type) == best) {
                preferred.add(method);
            }
        }

        return preferred;
    }

    static int operatorConstantFitCost(
            CallArg arg,
            SastType parameterType
    ) {
        if (arg.isLiteral() || arg.lowered == null
                || !arg.lowered.isConstant()) {
            return 0;
        }

        if (parameterType.kind != SastTypeKind.PTR
                || parameterType.referencedType.kind != SastTypeKind.PRIMITIVE) {
            return 0;
        }


        if (TypeChecker.sameType(arg.lowered.type, parameterType.referencedType)) {
            return 0;
        }

        int bits = TypeChecker.bitWidth(parameterType.referencedType.primitive);
        int unsignedPenalty = TypeChecker.isSigned(parameterType.referencedType.primitive) ? 0 : 1;

        return 1 + 2 * bits + unsignedPenalty;
    }

    static int operatorArgumentDowngradeCost(
            CallArg arg,
            SastType parameterType
    ) {
        if (!(parameterType.kind == SastTypeKind.PTR)) {
            return 0;
        }

        SastType referenced = parameterType.referencedType;

        if (arg.isLiteral()) {
            return arrayLiteralDowngradeCost(arg.literal, referenced);
        }

        if (arg.lowered == null || arg.lowered.type == null) {
            return 0;
        }

        if (TypeChecker.sameType(arg.lowered.type, referenced)) {
            return 0;
        }

        if (arg.lowered.type.kind == SastTypeKind.PTR
                && referenced.kind == SastTypeKind.PTR) {
            return PointerPermissions.isStaticArrayViewDecay(
                    arg.lowered.type,
                    referenced)
                    ? 2
                    : 1;
        }

        return 0;
    }

    static boolean isArgumentApplicableToParameter(
            CallArg arg,
            SastType parameterType
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

        SastNode lowered = arg.lowered;

        if (lowered.type.kind == SastTypeKind.NULL) {
            return parameterType.kind == SastTypeKind.PTR;
        }

        if (lowered.isConstant()
                && parameterType.kind == SastTypeKind.PRIMITIVE) {
            return TypeChecker.fitsBigIntegerToPrimitive(
                    lowered.constantValue,
                    parameterType.primitive
            );
        }

        if (TypeChecker.sameType(lowered.type, parameterType)) {
            return true;
        }

        return PointerPermissions.isPointerConversion(lowered.type, parameterType);
    }

    static boolean isArrayLiteralShapeApplicable(
            AstNode literal,
            SastType parameterType
    ) {
        if (!(parameterType.kind == SastTypeKind.PTR)
                || parameterType.permission != PtrPermission.READ_ONLY) {
            return false;
        }

        SastType referenced = parameterType.referencedType;

        if (!(referenced.kind == SastTypeKind.STATIC_ARRAY)
                && !(referenced.kind == SastTypeKind.DYNAMIC_ARRAY)) {
            return false;
        }

        return arrayLiteralRowsMatch(literal, referenced, true);
    }

    static int arrayLiteralDowngradeCost(
            AstNode literal,
            SastType parameterType
    ) {
        if (!(parameterType.kind == SastTypeKind.PTR)
                || parameterType.permission != PtrPermission.READ_ONLY) {
            return 0;
        }

        if (!(parameterType.referencedType.kind == SastTypeKind.STATIC_ARRAY)
                && !(parameterType.referencedType.kind == SastTypeKind.DYNAMIC_ARRAY)) {
            return 0;
        }

        if (!arrayLiteralRowsMatch(literal, parameterType.referencedType, true)) {
            return 0;
        }

        return parameterType.referencedType.kind == SastTypeKind.DYNAMIC_ARRAY ? 2 : 0;
    }

    static boolean arrayLiteralRowsMatch(
            AstNode literal,
            SastType arrayType,
            boolean outermost
    ) {
        if (literal == null || literal.elements.isEmpty()) {
            return false;
        }

        SastType elementShape;

        if (arrayType.kind == SastTypeKind.STATIC_ARRAY) {
            SastType probe = SastType.staticArray(
                    arrayType.elementType,
                    Integer.toString(literal.elements.size())
            );

            if (!probe.equals(arrayType)) {
                return false;
            }

            elementShape = arrayType.elementType;
        } else if (arrayType.kind == SastTypeKind.DYNAMIC_ARRAY
                && outermost) {
            elementShape = arrayType.elementType;
        } else {
            return false;
        }

        boolean anyNested = false;
        boolean anyScalar = false;

        for (AstNode element : literal.elements) {
            if (element.kind == AstKind.ARRAY_LITERAL) {
                anyNested = true;
            } else {
                anyScalar = true;
            }
        }

        if (anyNested && anyScalar) {
            return false;
        }

        if (anyNested) {
            if (!(elementShape.kind == SastTypeKind.STATIC_ARRAY)
                    && !(elementShape.kind == SastTypeKind.DYNAMIC_ARRAY)) {
                return false;
            }

            for (AstNode element : literal.elements) {
                if (!arrayLiteralRowsMatch(
                        element,
                        elementShape,
                        false)) {
                    return false;
                }
            }

            return true;
        }

        return elementShape.kind == SastTypeKind.PRIMITIVE
                || elementShape.kind == SastTypeKind.ENUM;
    }

    static List<Symbol> preferExactPermissionFunctionMatches(
            List<Symbol> matches,
            List<CallArg> args
    ) {
        if (matches.size() <= 1) {
            return matches;
        }

        int best = Integer.MAX_VALUE;

        for (Symbol fn : matches) {
            best = Math.min(
                    best,
                    countPermissionDowngrades(args, fn.parameterTypes()));
        }

        List<Symbol> preferred = new ArrayList<>();

        for (Symbol fn : matches) {
            if (countPermissionDowngrades(args, fn.parameterTypes()) == best) {
                preferred.add(fn);
            }
        }

        return preferred;
    }

    static List<Symbol> preferExactPermissionMethodMatches(
            List<Symbol> matches,
            List<CallArg> userArgs
    ) {
        if (matches.size() <= 1) {
            return matches;
        }

        int best = Integer.MAX_VALUE;

        for (Symbol method : matches) {
            best = Math.min(
                    best,
                    countMethodPermissionDowngrades(method, userArgs));
        }

        List<Symbol> preferred = new ArrayList<>();

        for (Symbol method : matches) {
            if (countMethodPermissionDowngrades(method, userArgs) == best) {
                preferred.add(method);
            }
        }

        return preferred;
    }

    static List<Symbol> preferExactPermissionOperatorMatches(
            List<Symbol> matches,
            CallArg arg
    ) {
        if (matches.size() <= 1) {
            return matches;
        }

        int best = Integer.MAX_VALUE;

        for (Symbol method : matches) {
            best = Math.min(
                    best,
                    operatorArgumentDowngradeCost(
                            arg,
                            method.parameters.get(1).type));
        }

        List<Symbol> preferred = new ArrayList<>();

        for (Symbol method : matches) {
            if (operatorArgumentDowngradeCost(
                    arg,
                    method.parameters.get(1).type) == best) {
                preferred.add(method);
            }
        }

        return preferred;
    }

    static List<Symbol> preferConstantFitFunctions(
            List<Symbol> matches,
            List<CallArg> args
    ) {
        if (matches.size() <= 1) {
            return matches;
        }

        int best = Integer.MAX_VALUE;

        for (Symbol fn : matches) {
            best = Math.min(
                    best,
                    totalConstantFitCost(args, fn.parameterTypes()));
        }

        List<Symbol> preferred = new ArrayList<>();

        for (Symbol fn : matches) {
            if (totalConstantFitCost(args, fn.parameterTypes()) == best) {
                preferred.add(fn);
            }
        }

        return preferred;
    }

    static List<Symbol> preferConstantFitMethods(
            List<Symbol> matches,
            List<CallArg> userArgs
    ) {
        if (matches.size() <= 1) {
            return matches;
        }

        int best = Integer.MAX_VALUE;

        for (Symbol m : matches) {
            best = Math.min(best, totalMethodConstantFitCost(m, userArgs));
        }

        List<Symbol> preferred = new ArrayList<>();

        for (Symbol m : matches) {
            if (totalMethodConstantFitCost(m, userArgs) == best) {
                preferred.add(m);
            }
        }

        return preferred;
    }

    private static int totalConstantFitCost(
            List<CallArg> args,
            List<SastType> parameterTypes
    ) {
        int total = 0;

        for (int i = 0; i < args.size(); i++) {
            total += constantFitCost(args.get(i), parameterTypes.get(i));
        }

        return total;
    }

    private static int totalMethodConstantFitCost(
            Symbol method,
            List<CallArg> userArgs
    ) {
        int total = 0;

        for (int i = 0; i < userArgs.size(); i++) {
            SastType parameterType =
                    method.parameters.get(i + 1).type;
            total += constantFitCost(userArgs.get(i), parameterType);
        }

        return total;
    }

    static String formatCallSignature(
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
}
