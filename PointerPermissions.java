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

    static String creationIntrinsic(PtrPermission permission) {
        return switch (permission) {
            case OWNER -> "getptr";
            case MUTABLE -> "getmview";
            case READ_ONLY -> "getview";
        };
    }
}
