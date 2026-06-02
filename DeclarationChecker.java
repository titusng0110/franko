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
        ctx.declare(node.name, node.type, node.isHeap);
    }

    public void checkVarDeclInit(VarDeclInitNode node) {
        // Analyze initializer before declaration so the variable is not visible
        // inside its own initializer.
        TypeNode initType = expressions.inferExprType(node.init);
        types.ensureAssignable(node.type, initType,
                "Cannot initialize variable '" + node.name + "' of type " + types.typeToString(node.type)
                        + " with expression of type " + types.typeToString(initType));
        ctx.declare(node.name, node.type, node.isHeap);
    }

    public void checkVarDeclArrayInit(VarDeclArrayInitNode node) {
        types.ensureDynamicArrayType(node.type,
                "Declaration-style array initialization requires a dynamic array type for '" + node.name + "'");
        TypeNode sizeType = expressions.inferExprType(node.size);
        types.ensureIntegral(sizeType,
                "Array size expression for '" + node.name + "' must be an integral scalar, got "
                        + types.typeToString(sizeType));
        ctx.declare(node.name, node.type, node.isHeap);
    }
}
