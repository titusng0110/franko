/**
 * TypeChecker contains type-system helpers used by the other checkers.
 *
 * It does not orchestrate statement traversal; it only answers questions about
 * types and reports type-related semantic errors through the shared context.
 */
public class TypeChecker {
    private final SemanticAnalyzer.Context ctx;
    private static final PrimitiveTypeNode INT32 = new PrimitiveTypeNode(PrimitiveKind.INT32);

    public TypeChecker(SemanticAnalyzer.Context ctx) {
        this.ctx = ctx;
    }

    public TypeNode int32Type() {
        return INT32;
    }

    public boolean sameType(TypeNode a, TypeNode b) {
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;

        if (a instanceof PrimitiveTypeNode) {
            PrimitiveTypeNode pa = (PrimitiveTypeNode) a;
            PrimitiveTypeNode pb = (PrimitiveTypeNode) b;
            return pa.kind == pb.kind;
        }

        if (a instanceof DynamicArrayTypeNode) {
            DynamicArrayTypeNode da = (DynamicArrayTypeNode) a;
            DynamicArrayTypeNode db = (DynamicArrayTypeNode) b;
            return sameType(da.elementType, db.elementType);
        }

        if (a instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode sa = (StaticArrayTypeNode) a;
            StaticArrayTypeNode sb = (StaticArrayTypeNode) b;
            return sa.size == sb.size && sameType(sa.elementType, sb.elementType);
        }

        return false;
    }

    public boolean isPrimitive(TypeNode t) {
        return t instanceof PrimitiveTypeNode;
    }

    public boolean isNumeric(TypeNode t) {
        if (!(t instanceof PrimitiveTypeNode)) return false;
        PrimitiveTypeNode p = (PrimitiveTypeNode) t;
        return p.kind == PrimitiveKind.INT32
                || p.kind == PrimitiveKind.UINT32
                || p.kind == PrimitiveKind.FLOAT32
                || p.kind == PrimitiveKind.CHAR8;
    }

    public boolean isIntegral(TypeNode t) {
        if (!(t instanceof PrimitiveTypeNode)) return false;
        PrimitiveTypeNode p = (PrimitiveTypeNode) t;
        return p.kind == PrimitiveKind.INT32
                || p.kind == PrimitiveKind.UINT32
                || p.kind == PrimitiveKind.CHAR8;
    }

    public boolean isArrayType(TypeNode t) {
        return t instanceof DynamicArrayTypeNode || t instanceof StaticArrayTypeNode;
    }

    public boolean isMemsetable(TypeNode t) {
        if (t == null) return false;

        if (t instanceof PrimitiveTypeNode) {
            return true;
        }

        if (t instanceof DynamicArrayTypeNode) {
            return false;
        }

        if (t instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode s = (StaticArrayTypeNode) t;
            return isMemsetable(s.elementType);
        }

        // Future extension point for user structs:
        // if (t instanceof StructTypeNode) {
        //     StructTypeNode s = (StructTypeNode) t;
        //     for (TypeNode fieldType : s.fieldTypes()) {
        //         if (!isMemsetable(fieldType)) return false;
        //     }
        //     return true;
        // }

        return false;
    }

    public void ensureArrayType(TypeNode t, String message) {
        if (!isArrayType(t)) {
            ctx.error(message);
        }
    }

    public void ensureDynamicArrayType(TypeNode t, String message) {
        if (!(t instanceof DynamicArrayTypeNode)) {
            ctx.error(message + ", got " + typeToString(t));
        }
    }

    public void ensureNumeric(TypeNode t, String message) {
        if (!isNumeric(t)) {
            ctx.error(message);
        }
    }

    public void ensureIntegral(TypeNode t, String message) {
        if (!isIntegral(t)) {
            ctx.error(message);
        }
    }

    public void ensureConditionType(TypeNode t, String where) {
        if (isArrayType(t) || !isPrimitive(t)) {
            ctx.error(where + " must be a scalar primitive expression, got " + typeToString(t));
        }
    }

    /**
     * Assignment compatibility policy:
     *  - exact type matches are allowed
     *  - primitive-to-primitive assignment is currently allowed
     *  - arrays require exact structural match
     */
    public void ensureAssignable(TypeNode target, TypeNode value, String messageOnFailure) {
        if (target == null || value == null) {
            ctx.error(messageOnFailure);
            return;
        }

        if (sameType(target, value)) {
            return;
        }

        if (isArrayType(target) || isArrayType(value)) {
            ctx.error(messageOnFailure);
            return;
        }

        if (isPrimitive(target) && isPrimitive(value)) {
            return;
        }

        ctx.error(messageOnFailure);
    }

    public boolean areComparable(TypeNode a, TypeNode b) {
        if (sameType(a, b)) return true;
        return isPrimitive(a) && isPrimitive(b);
    }

    public TypeNode numericPromotion(TypeNode a, TypeNode b) {
        PrimitiveKind ka = kindOf(a);
        PrimitiveKind kb = kindOf(b);

        if (ka == PrimitiveKind.FLOAT32 || kb == PrimitiveKind.FLOAT32) {
            return new PrimitiveTypeNode(PrimitiveKind.FLOAT32);
        }
        if (ka == PrimitiveKind.UINT32 || kb == PrimitiveKind.UINT32) {
            return new PrimitiveTypeNode(PrimitiveKind.UINT32);
        }
        return new PrimitiveTypeNode(PrimitiveKind.INT32);
    }

    public PrimitiveKind kindOf(TypeNode t) {
        if (!(t instanceof PrimitiveTypeNode)) {
            return PrimitiveKind.INT32;
        }
        return ((PrimitiveTypeNode) t).kind;
    }

    public TypeNode elementTypeOf(TypeNode t) {
        if (t instanceof DynamicArrayTypeNode) {
            return ((DynamicArrayTypeNode) t).elementType;
        }
        if (t instanceof StaticArrayTypeNode) {
            return ((StaticArrayTypeNode) t).elementType;
        }
        ctx.error("Attempted to fetch element type of non-array type " + typeToString(t));
        return INT32;
    }

    public String typeToString(TypeNode t) {
        if (t == null) return "<null>";

        if (t instanceof PrimitiveTypeNode) {
            PrimitiveKind k = ((PrimitiveTypeNode) t).kind;
            return switch (k) {
                case INT32 -> "int32_t";
                case UINT32 -> "uint32_t";
                case FLOAT32 -> "float32_t";
                case CHAR8 -> "char8_t";
            };
        }

        if (t instanceof DynamicArrayTypeNode) {
            return "array<" + typeToString(((DynamicArrayTypeNode) t).elementType) + ">";
        }

        if (t instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode s = (StaticArrayTypeNode) t;
            return "array<" + typeToString(s.elementType) + ", " + s.size + ">";
        }

        return t.getClass().getSimpleName();
    }
}
