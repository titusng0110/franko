import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

final class OverloadResolution {

    static int constantFitCost(
            CallArg arg,
            SemanticType parameterType
    ) {
        if (arg == null || arg.isLiteral()) {
            return 0;
        }

        if (arg.lowered == null || !arg.lowered.isConstant()) {
            return 0;
        }

        if (!(parameterType instanceof SemanticPrimitiveType primitive)) {
            return 0;
        }

        if (TypeChecker.sameType(arg.lowered.type, parameterType)) {
            return 0;
        }

        int bits = TypeChecker.bitWidth(primitive.kind);
        int unsignedPenalty = TypeChecker.isSigned(primitive.kind) ? 0 : 1;

        return 1 + 2 * bits + unsignedPenalty;
    }

    static boolean sameParameterSignature(
            List<ParameterSymbol> a,
            List<ParameterSymbol> b
    ) {
        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            ParameterSymbol left = a.get(i);
            ParameterSymbol right = b.get(i);

            if (!TypeChecker.sameType(left.type, right.type)) {
                return false;
            }
        }

        return true;
    }

    static boolean sameMethodUserParameterSignature(
            List<ParameterSymbol> a,
            List<ParameterSymbol> b
    ) {
        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 1; i < a.size(); i++) {
            ParameterSymbol left = a.get(i);
            ParameterSymbol right = b.get(i);

            if (!TypeChecker.sameType(left.type, right.type)) {
                return false;
            }
        }

        return true;
    }

    static boolean isApplicableFunction(
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

    static boolean isApplicableMethod(
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

    static int countPermissionDowngrades(
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

            if (!TypeChecker.sameType(arg.lowered.type, parameterType)
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

    static int countMethodPermissionDowngrades(
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

            if (!TypeChecker.sameType(arg.lowered.type, parameterType)
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

    static boolean isOperatorArgumentApplicable(
            MethodSymbol method,
            CallArg arg
    ) {
        if (method.parameters.size() != 2) {
            return false;
        }

        SemanticType parameterType = method.parameters.get(1).type;

        if (!(parameterType instanceof SemanticPtrType pointer)
                || pointer.permission != PtrPermission.READ_ONLY) {
            return false;
        }

        SemanticType referenced = pointer.referencedType;

        if (arg.isLiteral()) {
            return isArrayLiteralShapeApplicable(arg.literal, referenced);
        }

        if (arg.lowered == null || arg.lowered.type == null) {
            return false;
        }

        SemanticExprNode lowered = arg.lowered;

        if (lowered.type instanceof SemanticNullType) {
            return false;
        }

        if (lowered.isConstant()
                && referenced instanceof SemanticPrimitiveType primitive) {
            return TypeChecker.fitsBigIntegerToPrimitive(
                    lowered.constantValue,
                    primitive.kind
            );
        }

        if (TypeChecker.sameType(lowered.type, referenced)) {
            return true;
        }

        return lowered.type instanceof SemanticPtrType
                && referenced instanceof SemanticPtrType
                && PointerPermissions.isPointerConversion(
                        lowered.type,
                        referenced);
    }

    static List<MethodSymbol> preferOperatorConstantFitMatches(
            List<MethodSymbol> matches,
            CallArg arg
    ) {
        if (matches.size() <= 1) {
            return matches;
        }

        int best = Integer.MAX_VALUE;

        for (MethodSymbol method : matches) {
            best = Math.min(
                    best,
                    operatorConstantFitCost(
                            arg,
                            method.parameters.get(1).type)
            );
        }

        List<MethodSymbol> preferred = new ArrayList<>();

        for (MethodSymbol method : matches) {
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
            SemanticType parameterType
    ) {
        if (arg.isLiteral() || arg.lowered == null
                || !arg.lowered.isConstant()) {
            return 0;
        }

        if (!(parameterType instanceof SemanticPtrType pointer)
                || !(pointer.referencedType instanceof SemanticPrimitiveType primitive)) {
            return 0;
        }

        if (TypeChecker.sameType(arg.lowered.type, pointer.referencedType)) {
            return 0;
        }

        int bits = TypeChecker.bitWidth(primitive.kind);
        int unsignedPenalty = TypeChecker.isSigned(primitive.kind) ? 0 : 1;

        return 1 + 2 * bits + unsignedPenalty;
    }

    static int operatorArgumentDowngradeCost(
            CallArg arg,
            SemanticType parameterType
    ) {
        if (!(parameterType instanceof SemanticPtrType pointer)) {
            return 0;
        }

        SemanticType referenced = pointer.referencedType;

        if (arg.isLiteral()) {
            return arrayLiteralDowngradeCost(arg.literal, referenced);
        }

        if (arg.lowered == null || arg.lowered.type == null) {
            return 0;
        }

        if (TypeChecker.sameType(arg.lowered.type, referenced)) {
            return 0;
        }

        if (arg.lowered.type instanceof SemanticPtrType
                && referenced instanceof SemanticPtrType) {
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
            return TypeChecker.fitsBigIntegerToPrimitive(
                    lowered.constantValue,
                    primitive.kind
            );
        }

        if (TypeChecker.sameType(lowered.type, parameterType)) {
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

    static int arrayLiteralDowngradeCost(
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

    static boolean arrayLiteralRowsMatch(
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

    static <T> List<T> preferExactPermissionMatches(
            List<T> matches,
            ToIntFunction<T> downgradeCount
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

    static List<FunctionSymbol> preferConstantFitFunctions(
            List<FunctionSymbol> matches,
            List<CallArg> args
    ) {
        if (matches.size() <= 1) {
            return matches;
        }

        int best = Integer.MAX_VALUE;

        for (FunctionSymbol fn : matches) {
            best = Math.min(
                    best,
                    totalConstantFitCost(args, fn.parameterTypes()));
        }

        List<FunctionSymbol> preferred = new ArrayList<>();

        for (FunctionSymbol fn : matches) {
            if (totalConstantFitCost(args, fn.parameterTypes()) == best) {
                preferred.add(fn);
            }
        }

        return preferred;
    }

    static List<MethodSymbol> preferConstantFitMethods(
            List<MethodSymbol> matches,
            List<CallArg> userArgs
    ) {
        if (matches.size() <= 1) {
            return matches;
        }

        int best = Integer.MAX_VALUE;

        for (MethodSymbol m : matches) {
            best = Math.min(best, totalMethodConstantFitCost(m, userArgs));
        }

        List<MethodSymbol> preferred = new ArrayList<>();

        for (MethodSymbol m : matches) {
            if (totalMethodConstantFitCost(m, userArgs) == best) {
                preferred.add(m);
            }
        }

        return preferred;
    }

    private static int totalConstantFitCost(
            List<CallArg> args,
            List<SemanticType> parameterTypes
    ) {
        int total = 0;

        for (int i = 0; i < args.size(); i++) {
            total += constantFitCost(args.get(i), parameterTypes.get(i));
        }

        return total;
    }

    private static int totalMethodConstantFitCost(
            MethodSymbol method,
            List<CallArg> userArgs
    ) {
        int total = 0;

        for (int i = 0; i < userArgs.size(); i++) {
            SemanticType parameterType =
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
