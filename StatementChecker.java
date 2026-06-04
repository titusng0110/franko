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

        expressions.ensureExprAssignableToType(
                node.value,
                lhsType,
                "Cannot assign expression to target of type " + types.typeToString(lhsType)
        );
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

        expressions.ensureArraySizeExprCompatible(node.size, "Array size expression for '" + node.name + "' is invalid");
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

        TypeNode element;
        if (receiverType instanceof DynamicArrayTypeNode) {
            element = ((DynamicArrayTypeNode) receiverType).elementType;
        } else if (receiverType instanceof StaticArrayTypeNode) {
            element = ((StaticArrayTypeNode) receiverType).elementType;
        } else {
            // Should be unreachable due to ensureArrayType above
            throw new IllegalStateException("Internal compiler error: expected array type, got " + types.typeToString(receiverType));
        }

        if (!types.isMemsetable(element)) {
            ctx.error("memset() receiver elementtype is not memsetable: " + types.typeToString(receiverType));
        }

        PrimitiveTypeNode byteType = new PrimitiveTypeNode(PrimitiveKind.UINT8);

        if (expressions.isCompileTimeIntegerConstantExpr(node.value)) {
            // Literal path:
            // allow any integer literal that fits in uint8_t
            expressions.ensureExprFitsTargetType(
                    node.value,
                    byteType,
                    "memset() fill value must be a byte literal fitting uint8_t"
            );
        } else {
            // Non-literal path:
            // strict same type: require uint8_t / char
            TypeNode valueType = expressions.inferExprType(node.value);
            if (!(valueType instanceof PrimitiveTypeNode)
                    || ((PrimitiveTypeNode) valueType).kind != PrimitiveKind.UINT8) {
                ctx.error("memset() fill value must currently be uint8_t/char, got "
                        + types.typeToString(valueType));
            }
        }
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

        TypeNode targetElement;
        if (targetType instanceof DynamicArrayTypeNode) {
            targetElement = ((DynamicArrayTypeNode) targetType).elementType;
        } else if (targetType instanceof StaticArrayTypeNode) {
            targetElement = ((StaticArrayTypeNode) targetType).elementType;
        } else {
            // Should be unreachable due to ensureArrayType above
            throw new IllegalStateException("Internal compiler error: expected array type, got " + types.typeToString(targetType));
        }
        if (!types.isMemcpyable(targetElement)) {
            ctx.error("memcpy() target array element type is not memcpyable: " + types.typeToString(targetType));
        }

        TypeNode sourceElement;
        if (sourceType instanceof DynamicArrayTypeNode) {
            sourceElement = ((DynamicArrayTypeNode) sourceType).elementType;
        } else if (sourceType instanceof StaticArrayTypeNode) {
            sourceElement = ((StaticArrayTypeNode) sourceType).elementType;
        } else {
            // Should be unreachable due to ensureArrayType above
            throw new IllegalStateException("Internal compiler error: expected array type, got " + types.typeToString(sourceType));
        }
        if (!types.isMemcpyable(sourceElement)) {
            ctx.error("memcpy() source array element type is not memcpyable: " + types.typeToString(sourceType));
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
