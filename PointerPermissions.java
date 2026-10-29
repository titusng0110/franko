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

    static PtrPermission pointerPermission(SemanticType type) {
        return type instanceof SemanticPtrType ptr
                ? ptr.permission
                : PtrPermission.OWNER;
    }

    static boolean isPermissionDowngrade(
            SemanticType sourceType,
            SemanticType targetType
    ) {
        return sourceType instanceof SemanticPtrType source
                && targetType instanceof SemanticPtrType target
                && source.referencedType.equals(target.referencedType)
                && source.permission.allows(target.permission);
    }

    static boolean isStaticArrayViewDecay(
            SemanticType sourceType,
            SemanticType targetType
    ) {
        if (!(sourceType instanceof SemanticPtrType source)
                || !(targetType instanceof SemanticPtrType target)) {
            return false;
        }

        if (target.permission == PtrPermission.OWNER) {
            return false;
        }

        if (!source.permission.allows(target.permission)) {
            return false;
        }

        if (!(source.referencedType instanceof SemanticStaticArrayType staticArray)
                || !(target.referencedType instanceof SemanticDynamicArrayType dynamicArray)) {
            return false;
        }

        return staticArray.elementType.equals(dynamicArray.elementType);
    }

    static boolean isPointerConversion(
            SemanticType sourceType,
            SemanticType targetType
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
