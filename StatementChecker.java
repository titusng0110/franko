/**
 * StatementChecker traverses statements and delegates to the declaration and
 * expression/type helpers where appropriate.
 */
public class StatementChecker {
    private final SemanticAnalyzer.Context ctx;
    private final DeclarationChecker declarations;
    private final ExpressionChecker expressions;
    private final TypeChecker types;

    public StatementChecker(
            SemanticAnalyzer.Context ctx,
            DeclarationChecker declarations,
            ExpressionChecker expressions,
            TypeChecker types) {
        this.ctx = ctx;
        this.declarations = declarations;
        this.expressions = expressions;
        this.types = types;
    }

    public void visitStatement(ASTNode node) {
        if (node == null) return;

        if (node instanceof ProgramNode) {
            ProgramNode n = (ProgramNode) node;
            for (ASTNode stmt : n.statements) {
                visitStatement(stmt);
            }
            return;
        }

        if (node instanceof BlockNode) {
            BlockNode n = (BlockNode) node;
            ctx.pushScope();
            for (ASTNode stmt : n.statements) {
                visitStatement(stmt);
            }
            ctx.popScope();
            return;
        }

        if (node instanceof VarDeclNode) {
            declarations.checkVarDecl((VarDeclNode) node);
            return;
        }

        if (node instanceof VarDeclInitNode) {
            declarations.checkVarDeclInit((VarDeclInitNode) node);
            return;
        }

        if (node instanceof VarDeclArrayInitNode) {
            declarations.checkVarDeclArrayInit((VarDeclArrayInitNode) node);
            return;
        }

        if (node instanceof AssignNode) {
            visitAssign((AssignNode) node);
            return;
        }

        if (node instanceof IfNode) {
            visitIf((IfNode) node);
            return;
        }

        if (node instanceof WhileNode) {
            visitWhile((WhileNode) node);
            return;
        }

        if (node instanceof ArrayInitNode) {
            visitArrayInit((ArrayInitNode) node);
            return;
        }

        if (node instanceof ArrayUninitNode) {
            visitArrayUninit((ArrayUninitNode) node);
            return;
        }

        if (node instanceof ArrayMemsetNode) {
            visitArrayMemset((ArrayMemsetNode) node);
            return;
        }

        if (node instanceof ArrayMemcpyNode) {
            visitArrayMemcpy((ArrayMemcpyNode) node);
            return;
        }

        if (node instanceof DelNode) {
            visitDelete((DelNode) node);
            return;
        }

        if (node instanceof PrintNode) {
            visitPrint((PrintNode) node);
            return;
        }

        if (isExpressionNode(node)) {
            expressions.inferExprType(node);
            return;
        }

        ctx.error("Unsupported AST node in statement context: " + node.getClass().getSimpleName());
    }

    private void visitAssign(AssignNode node) {
        TypeNode lhsType = expressions.inferLValueType(node.target);
        TypeNode rhsType = expressions.inferExprType(node.value);

        if (expressions.isIntegerLiteralExpr(node.value)) {
            // Literal path:
            // allow assignment if the literal numerically fits the target type.
            expressions.ensureExprFitsTargetType(
                    node.value,
                    lhsType,
                    "Cannot assign integer literal to target of type " + types.typeToString(lhsType)
            );
        } else {
            // Non-literal path:
            // strict same-type only, arrays not assignable at all.
            types.ensureAssignable(
                    lhsType,
                    rhsType,
                    "Cannot assign expression of type " + types.typeToString(rhsType)
                            + " to target of type " + types.typeToString(lhsType)
            );
        }
    }

    private void visitIf(IfNode node) {
        TypeNode condType = expressions.inferExprType(node.condition);
        types.ensureConditionType(condType, "if condition");
        visitStatement(node.thenBranch);
        if (node.elseBranch != null) {
            visitStatement(node.elseBranch);
        }
    }

    private void visitWhile(WhileNode node) {
        TypeNode condType = expressions.inferExprType(node.condition);
        types.ensureConditionType(condType, "while condition");
        visitStatement(node.body);
    }

    private void visitArrayInit(ArrayInitNode node) {
        SemanticAnalyzer.Symbol sym = ctx.resolve(node.name);
        if (sym == null) {
            ctx.error("Array init uses undeclared variable '" + node.name + "'");
            expressions.inferExprType(node.size);
            return;
        }

        if (sym.deleted) {
            ctx.error("Array init on deleted variable '" + node.name + "'");
        }

        types.ensureDynamicArrayType(
                sym.type,
                "Array init statement '" + node.name + "(...)' requires '" + node.name
                        + "' to be of dynamic array type"
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
    }

    private void visitArrayUninit(ArrayUninitNode node) {
        TypeNode receiverType = expressions.inferExprType(node.receiver);
        types.ensureArrayType(
                receiverType,
                "uninit() receiver must be an array, got " + types.typeToString(receiverType)
        );
    }

    private void visitArrayMemset(ArrayMemsetNode node) {
        TypeNode receiverType = expressions.inferExprType(node.receiver);
        types.ensureArrayType(
                receiverType,
                "memset() receiver must be an array, got " + types.typeToString(receiverType)
        );

        if (!types.isMemsetable(receiverType)) {
            ctx.error("memset() receiver type is not memsetable: " + types.typeToString(receiverType));
        }

        // Current temporary rule:
        // memset fill values must currently be int32_t.
        TypeNode valueType = expressions.inferExprType(node.value);
        if (!(valueType instanceof PrimitiveTypeNode)
                || ((PrimitiveTypeNode) valueType).kind != PrimitiveKind.INT32) {
            ctx.error("memset() fill value must currently be int32_t, got " + types.typeToString(valueType));
        }

        // If the fill value is literally written as an integer literal, also ensure it
        // actually fits int32_t.
        expressions.ensureExprFitsTargetType(
                node.value,
                new PrimitiveTypeNode(PrimitiveKind.INT32),
                "memset() fill value is invalid"
        );
    }

    private void visitArrayMemcpy(ArrayMemcpyNode node) {
        TypeNode targetType = expressions.inferExprType(node.target);
        TypeNode sourceType = expressions.inferExprType(node.source);

        types.ensureArrayType(
                targetType,
                "memcpy() target must be an array, got " + types.typeToString(targetType)
        );

        types.ensureArrayType(
                sourceType,
                "memcpy() source must be an array, got " + types.typeToString(sourceType)
        );

        if (!types.sameType(targetType, sourceType)) {
            ctx.error(
                    "memcpy() requires identical source/target array types, got "
                            + types.typeToString(targetType) + " and " + types.typeToString(sourceType)
            );
        }
    }

    private void visitDelete(DelNode node) {
        SemanticAnalyzer.Symbol sym = ctx.resolve(node.name);
        if (sym == null) {
            ctx.error("Cannot delete undeclared variable '" + node.name + "'");
            return;
        }
        if (!sym.isHeap) {
            ctx.error("Cannot delete non-heap variable '" + node.name + "'");
        }
        if (sym.deleted) {
            ctx.error("Variable '" + node.name + "' has already been deleted");
        }
        sym.deleted = true;
    }

    private void visitPrint(PrintNode node) {
        for (ASTNode arg : node.args) {
            expressions.inferExprType(arg);
        }
    }

    private boolean isExpressionNode(ASTNode node) {
        return node instanceof IntNode
                || node instanceof VarNode
                || node instanceof UnaryOpNode
                || node instanceof BinOpNode
                || node instanceof ArrayAccessNode;
    }
}
