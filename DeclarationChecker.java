/**
 * DeclarationChecker validates declaration forms and updates the shared symbol table.
 */
public class DeclarationChecker {
    private final SemanticAnalyzer.Context ctx;
    private final ExpressionChecker expressions;
    private final TypeChecker types;

    public DeclarationChecker(
            SemanticAnalyzer.Context ctx,
            ExpressionChecker expressions,
            TypeChecker types) {
        this.ctx = ctx;
        this.expressions = expressions;
        this.types = types;
    }

    public void checkVarDecl(VarDeclNode node) {
        validateDeclaredType(node.type, "Variable '" + node.name + "'");
        ctx.declare(node.name, node.type, node.isHeap);
    }

    public void checkVarDeclInit(VarDeclInitNode node) {
        validateDeclaredType(node.type, "Variable '" + node.name + "'");

        // Analyze initializer before declaration so the variable is not visible
        // inside its own initializer.
        TypeNode initType = expressions.inferExprType(node.init);

        if (expressions.isIntegerLiteralExpr(node.init)) {
            // Literal path:
            // allow assignment if the literal numerically fits the declared type.
            expressions.ensureExprFitsTargetType(
                    node.init,
                    node.type,
                    "Cannot initialize variable '" + node.name + "' of type "
                            + types.typeToString(node.type)
            );
        } else {
            // Non-literal path:
            // strict same-type only, arrays not assignable at all.
            types.ensureAssignable(
                    node.type,
                    initType,
                    "Cannot initialize variable '" + node.name + "' of type "
                            + types.typeToString(node.type)
                            + " with expression of type "
                            + types.typeToString(initType)
            );
        }

        ctx.declare(node.name, node.type, node.isHeap);
    }

    public void checkVarDeclArrayInit(VarDeclArrayInitNode node) {
        validateDeclaredType(node.type, "Variable '" + node.name + "'");

        types.ensureDynamicArrayType(
                node.type,
                "Declaration-style array initialization requires a dynamic array type for '" + node.name + "'"
        );

        TypeNode sizeType = expressions.inferExprType(node.size);
        types.ensureIntegral(
                sizeType,
                "Array size expression for '" + node.name + "' must be an integral scalar, got "
                        + types.typeToString(sizeType)
        );

        // If the size expression is a literal, it must fit uint32_t and be non-negative.
        expressions.ensureExprFitsArraySize(
                node.size,
                "Array size expression for '" + node.name + "' is invalid"
        );

        ctx.declare(node.name, node.type, node.isHeap);
    }

    /**
     * Recursively validates declared types.
     *
     * In particular:
     *   - static array sizes must fit uint32_t
     *   - nested element types are checked recursively
     */
    private void validateDeclaredType(TypeNode type, String where) {
        if (type == null) {
            ctx.error(where + " has invalid null type");
            return;
        }

        if (type instanceof PrimitiveTypeNode) {
            return;
        }

        if (type instanceof DynamicArrayTypeNode) {
            DynamicArrayTypeNode t = (DynamicArrayTypeNode) type;
            validateDeclaredType(t.elementType, where);
            return;
        }

        if (type instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode t = (StaticArrayTypeNode) type;

            types.ensureArraySizeLiteralFitsUint32(
                    t.sizeLiteral,
                    where + " has invalid static array size"
            );

            validateDeclaredType(t.elementType, where);
            return;
        }

        ctx.error(where + " has unsupported declared type: " + type.getClass().getSimpleName());
    }
}
