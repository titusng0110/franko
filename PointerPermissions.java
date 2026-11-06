final class PointerPermissions {
    private PointerPermissions() {}

    static PtrPermission regionPermission(SemanticExprNode expr) {
        if (expr instanceof SemanticDerefNode deref) {
            return pointerPermission(deref.expr.type);
        }

        if (expr instanceof SemanticArrayAccessNode access) {
            return regionPermission(access.target);
        }

        if (expr instanceof SemanticMemberAccessNode member) {
            return regionPermission(member.target);
        }

        return PtrPermission.OWNER;
    }

    static PtrPermission pointerPermission(SastType type) {
        return type.kind == SastTypeKind.PTR
                ? type.permission
                : PtrPermission.OWNER;
    }

    static boolean isPermissionDowngrade(
            SastType sourceType,
            SastType targetType
    ) {
        return sourceType.kind == SastTypeKind.PTR
                && targetType.kind == SastTypeKind.PTR
                && sourceType.referencedType.equals(targetType.referencedType)
                && sourceType.permission.allows(targetType.permission);
    }

    static boolean isStaticArrayViewDecay(
            SastType sourceType,
            SastType targetType
    ) {
        if (sourceType.kind != SastTypeKind.PTR
                || targetType.kind != SastTypeKind.PTR) {
            return false;
        }

        if (targetType.permission == PtrPermission.OWNER) {
            return false;
        }

        if (!sourceType.permission.allows(targetType.permission)) {
            return false;
        }

        if (sourceType.referencedType.kind != SastTypeKind.STATIC_ARRAY
                || targetType.referencedType.kind != SastTypeKind.DYNAMIC_ARRAY) {
            return false;
        }

        return sourceType.referencedType.elementType.equals(
                targetType.referencedType.elementType);
    }

    static boolean isPointerConversion(
            SastType sourceType,
            SastType targetType
    ) {
        return isPermissionDowngrade(sourceType, targetType)
                || isStaticArrayViewDecay(sourceType, targetType);
    }

    static String creationIntrinsic(PtrPermission permission) {
        return switch (permission) {
            case OWNER -> "getptr";
            case MUTABLE -> "getmview";
            case READ_ONLY -> "getview";
        };
    }
}
