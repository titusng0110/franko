import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class OverloadResolution {

    static int findParameter(Symbol fn, String name, int start) {
        if (fn == null || name == null) {
            return -1;
        }

        for (int i = Math.max(0, start); i < fn.parameters.size(); i++) {
            if (name.equals(fn.parameters.get(i).name)) {
                return i;
            }
        }

        return -1;
    }

    static int[] bindArguments(
            Symbol fn,
            List<CallArg> args,
            boolean method
    ) {
        if (fn == null || args == null) {
            return null;
        }

        int start = method ? 1 : 0;

        if (fn.parameters.size() < start) {
            return null;
        }

        int[] argAt = new int[fn.parameters.size()];
        Arrays.fill(argAt, -1);
        int position = start;
        boolean namedSeen = false;

        for (int i = 0; i < args.size(); i++) {
            CallArg arg = args.get(i);

            if (arg == null) {
                return null;
            }

            if (!arg.isNamed()) {
                if (namedSeen) {
                    return null;
                }

                if (position >= fn.parameters.size()) {
                    return null;
                }

                argAt[position++] = i;
                continue;
            }

            namedSeen = true;
            int target = findParameter(fn, arg.name, start);

            if (target < 0 || argAt[target] >= 0) {
                return null;
            }

            argAt[target] = i;
        }

        boolean anyPositionalRequired = false;
        boolean anyNamedRequired = false;

        for (int p = start; p < argAt.length; p++) {
            Symbol parameter = fn.parameters.get(p);
            boolean supplied = argAt[p] >= 0;
            boolean optional = parameter.parameterDefault != null;

            if (!supplied && !optional) {
                return null;
            }

            if (!supplied) {
                continue;
            }

            CallArg arg = args.get(argAt[p]);

            if (optional && !arg.isNamed()) {
                return null;
            }

            if (!optional) {
                if (arg.isNamed()) {
                    anyNamedRequired = true;
                } else {
                    anyPositionalRequired = true;
                }
            }
        }

        if (anyPositionalRequired && anyNamedRequired) {
            return null;
        }

        return argAt;
    }

    static String describeBindingProblem(
            String callName,
            Symbol fn,
            List<CallArg> args,
            boolean method
    ) {
        if (fn == null || args == null) {
            return null;
        }

        int start = method ? 1 : 0;

        if (fn.parameters.size() < start) {
            return null;
        }

        if (method) {
            for (CallArg arg : args) {
                if (arg != null && "self".equals(arg.name)) {
                    return "Named argument 'self' is not valid in a method"
                            + " call; self is implicit";
                }
            }
        }

        int[] argAt = new int[fn.parameters.size()];
        Arrays.fill(argAt, -1);
        int position = start;
        boolean namedSeen = false;

        for (int i = 0; i < args.size(); i++) {
            CallArg arg = args.get(i);

            if (arg == null) {
                return null;
            }

            if (!arg.isNamed()) {
                if (namedSeen) {
                    return "Positional arguments must appear before named"
                            + " arguments in call to '" + callName + "'";
                }

                if (position >= fn.parameters.size()) {
                    return null;
                }

                argAt[position++] = i;
                continue;
            }

            namedSeen = true;
            int target = findParameter(fn, arg.name, start);

            if (target < 0) {
                return "Named argument '" + arg.name + "' does not match any"
                        + " parameter of '" + callName + "'";
            }

            if (argAt[target] >= 0) {
                if (args.get(argAt[target]).isNamed()) {
                    return "Named argument '" + arg.name + "' is specified"
                            + " more than once in call to '" + callName + "'";
                }

                return "Argument for parameter '" + arg.name + "' is already"
                        + " supplied positionally";
            }

            argAt[target] = i;
        }

        boolean anyPositionalRequired = false;
        boolean anyNamedRequired = false;
        String optionalPositional = null;
        String missingRequired = null;

        for (int p = start; p < argAt.length; p++) {
            Symbol parameter = fn.parameters.get(p);
            boolean supplied = argAt[p] >= 0;
            boolean optional = parameter.parameterDefault != null;

            if (!supplied) {
                if (!optional && missingRequired == null) {
                    missingRequired = parameter.name;
                }

                continue;
            }

            CallArg arg = args.get(argAt[p]);

            if (optional) {
                if (!arg.isNamed() && optionalPositional == null) {
                    optionalPositional = parameter.name;
                }
            } else if (arg.isNamed()) {
                anyNamedRequired = true;
            } else {
                anyPositionalRequired = true;
            }
        }

        if (anyPositionalRequired && anyNamedRequired) {
            return "Compulsory arguments in call to '" + callName + "' must"
                    + " be either all named or all unnamed";
        }

        if (optionalPositional != null) {
            return "Optional parameter '" + optionalPositional + "' of '"
                    + callName + "' must be passed by name";
        }

        if (missingRequired != null) {
            return "Missing required argument '" + missingRequired
                    + "' in call to '" + callName + "'";
        }

        return null;
    }

    static List<Symbol> preferFewestDefaultFills(
            List<Symbol> matches,
            List<CallArg> args,
            boolean method
    ) {
        if (matches.size() <= 1) {
            return matches;
        }

        int best = Integer.MAX_VALUE;

        for (Symbol fn : matches) {
            best = Math.min(best, countDefaultFills(fn, args, method));
        }

        List<Symbol> preferred = new ArrayList<>();

        for (Symbol fn : matches) {
            if (countDefaultFills(fn, args, method) == best) {
                preferred.add(fn);
            }
        }

        return preferred;
    }

    private static int countDefaultFills(
            Symbol fn,
            List<CallArg> args,
            boolean method
    ) {
        int[] argAt = bindArguments(fn, args, method);

        if (argAt == null) {
            return Integer.MAX_VALUE;
        }

        int start = method ? 1 : 0;
        int count = 0;

        for (int p = start; p < argAt.length; p++) {
            if (argAt[p] < 0) {
                count++;
            }
        }

        return count;
    }

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

        if (!arg.lowered.untypedConstant
                && TypeChecker.sameType(arg.lowered.type, parameterType)) {
            return 0;
        }

        return TypeChecker.primitiveFitCost(
                arg.lowered.constantValue,
                parameterType.primitive);
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
        int[] argAt = bindArguments(fn, args, false);

        if (argAt == null) {
            return false;
        }

        for (int p = 0; p < fn.parameters.size(); p++) {
            int argIndex = argAt[p];

            if (argIndex < 0) {
                continue;
            }

            if (!isArgumentApplicableToParameter(
                    args.get(argIndex),
                    fn.parameters.get(p).type)) {
                return false;
            }
        }

        return true;
    }

    static boolean isApplicableMethod(
            Symbol method,
            List<CallArg> userArgs
    ) {
        int[] argAt = bindArguments(method, userArgs, true);

        if (argAt == null) {
            return false;
        }

        for (int p = 1; p < method.parameters.size(); p++) {
            int argIndex = argAt[p];

            if (argIndex < 0) {
                continue;
            }

            if (!isArgumentApplicableToParameter(
                    userArgs.get(argIndex),
                    method.parameters.get(p).type)) {
                return false;
            }
        }

        return true;
    }

    static int countPermissionDowngrades(
            Symbol fn,
            List<CallArg> args,
            boolean method
    ) {
        int[] argAt = bindArguments(fn, args, method);

        if (argAt == null) {
            return Integer.MAX_VALUE;
        }

        int start = method ? 1 : 0;
        int count = 0;

        for (int p = start; p < fn.parameters.size(); p++) {
            int argIndex = argAt[p];

            if (argIndex < 0) {
                continue;
            }

            CallArg arg = args.get(argIndex);
            SastType parameterType = fn.parameters.get(p).type;

            if (arg == null) {
                continue;
            }

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


        if (!arg.lowered.untypedConstant
                && TypeChecker.sameType(
                        arg.lowered.type,
                        parameterType.referencedType)) {
            return 0;
        }

        return TypeChecker.primitiveFitCost(
                arg.lowered.constantValue,
                parameterType.referencedType.primitive);
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
                    countPermissionDowngrades(fn, args, false));
        }

        List<Symbol> preferred = new ArrayList<>();

        for (Symbol fn : matches) {
            if (countPermissionDowngrades(fn, args, false) == best) {
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
                    countPermissionDowngrades(method, userArgs, true));
        }

        List<Symbol> preferred = new ArrayList<>();

        for (Symbol method : matches) {
            if (countPermissionDowngrades(method, userArgs, true) == best) {
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
                    totalConstantFitCost(fn, args, false));
        }

        List<Symbol> preferred = new ArrayList<>();

        for (Symbol fn : matches) {
            if (totalConstantFitCost(fn, args, false) == best) {
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
            best = Math.min(best, totalConstantFitCost(m, userArgs, true));
        }

        List<Symbol> preferred = new ArrayList<>();

        for (Symbol m : matches) {
            if (totalConstantFitCost(m, userArgs, true) == best) {
                preferred.add(m);
            }
        }

        return preferred;
    }

    private static int totalConstantFitCost(
            Symbol fn,
            List<CallArg> args,
            boolean method
    ) {
        int[] argAt = bindArguments(fn, args, method);

        if (argAt == null) {
            return Integer.MAX_VALUE;
        }

        int start = method ? 1 : 0;
        int total = 0;

        for (int p = start; p < fn.parameters.size(); p++) {
            int argIndex = argAt[p];

            if (argIndex < 0) {
                continue;
            }

            total += constantFitCost(
                    args.get(argIndex),
                    fn.parameters.get(p).type);
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
