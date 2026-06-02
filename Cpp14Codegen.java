import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class Cpp14Codegen {
    
    private static final class VarInfo {
        final boolean isHeap;
        final TypeNode type;

        VarInfo(TypeNode type, boolean isHeap) {
            this.type = type;
            this.isHeap = isHeap;
        }
    }

    private boolean isCompileTimeIntegerConstantExpr(ASTNode expr) {
        if (expr == null) return false;

        if (expr instanceof IntNode) {
            return true;
        }

        if (expr instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) expr;
            if ("-".equals(n.op) || "!".equals(n.op)) {
                return isCompileTimeIntegerConstantExpr(n.expr);
            }
            return false;
        }

        if (expr instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) expr;
            return isCompileTimeIntegerConstantExpr(n.left)
                    && isCompileTimeIntegerConstantExpr(n.right);
        }

        return false;
    }
    
    private TypeNode inferExprTypeForCodegen(ASTNode node) {
        if (node instanceof IntNode) {
            return new PrimitiveTypeNode(PrimitiveKind.INT32);
        }

        if (node instanceof VarNode) {
            VarInfo info = requireVarForCodegen(((VarNode) node).name);
            return info.type;
        }

        if (node instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) node;
            TypeNode targetType = inferExprTypeForCodegen(n.target);

            if (targetType instanceof DynamicArrayTypeNode) {
                return ((DynamicArrayTypeNode) targetType).elementType;
            }

            if (targetType instanceof StaticArrayTypeNode) {
                return ((StaticArrayTypeNode) targetType).elementType;
            }

            throw new IllegalStateException(
                "Internal compiler error: non-array target reached codegen in ArrayAccessNode"
            );
        }

        if (node instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) node;
            TypeNode operandType = inferExprTypeForCodegen(n.expr);

            if ("-".equals(n.op)) {
                // Franko unary minus result type = operand type
                return operandType;
            }

            if ("!".equals(n.op)) {
                // Franko logical-not result type = uint8_t / char
                return new PrimitiveTypeNode(PrimitiveKind.UINT8);
            }

            throw new IllegalStateException(
                "Internal compiler error: unsupported unary operator in codegen type inference: " + n.op
            );
        }

        if (node instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) node;
            String op = n.op;

            boolean leftConst = isCompileTimeIntegerConstantExpr(n.left);
            boolean rightConst = isCompileTimeIntegerConstantExpr(n.right);

            TypeNode left = inferExprTypeForCodegen(n.left);
            TypeNode right = inferExprTypeForCodegen(n.right);

            // logical
            if ("&&".equals(op) || "||".equals(op)) {
                return new PrimitiveTypeNode(PrimitiveKind.UINT8);
            }

            // comparison
            if ("==".equals(op) || "!=".equals(op)
                    || "<".equals(op) || ">".equals(op)
                    || "<=".equals(op) || ">=".equals(op)) {
                return new PrimitiveTypeNode(PrimitiveKind.UINT8);
            }

            // bitwise
            if ("&".equals(op) || "|".equals(op) || "^".equals(op)) {
                if (leftConst && rightConst) {
                    return new PrimitiveTypeNode(PrimitiveKind.INT32);
                }
                if (leftConst && !rightConst) {
                    return right;
                }
                if (!leftConst && rightConst) {
                    return left;
                }
                return left;
            }

            // arithmetic
            if ("+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op)) {
                if (leftConst && rightConst) {
                    return new PrimitiveTypeNode(PrimitiveKind.INT32);
                }
                if (leftConst && !rightConst) {
                    return right;
                }
                if (!leftConst && rightConst) {
                    return left;
                }
                return left;
            }

            // shift
            if ("<<".equals(op) || ">>".equals(op)) {
                if (leftConst && rightConst) {
                    return new PrimitiveTypeNode(PrimitiveKind.INT32);
                }
                if (leftConst && !rightConst) {
                    // Your current semantic rule keeps literal lhs default-int32 in mixed shifts
                    return new PrimitiveTypeNode(PrimitiveKind.INT32);
                }
                if (!leftConst && rightConst) {
                    return left;
                }
                return left;
            }

            throw new IllegalStateException(
                "Internal compiler error: unsupported binary operator in codegen type inference: " + op
            );
        }

        throw new IllegalStateException(
            "Internal compiler error: unsupported expression node in codegen type inference: "
            + node.getClass().getSimpleName()
        );
    }
    
    private boolean isPrimitiveIntegerType(TypeNode t) {
        return t instanceof PrimitiveTypeNode;
    }
    
    private String emitCastToFrankoType(TypeNode type, String exprText) {
        return "static_cast<" + emitType(type) + ">(" + exprText + ")";
    }

    private final Deque<Map<String, VarInfo>> scopes = new ArrayDeque<>();
    private final StringBuilder out = new StringBuilder();
    private int indentLevel = 0;

    /**
     * Generates ONLY the Franko program body.
     *
     * Intended for replacing __FRANKO_PROGRAM__ inside ProgramTemplate.cpp.
     */
    public String generate(ASTNode root) {
        scopes.clear();
        out.setLength(0);
        indentLevel = 0;

        pushScope();
        emitStmt(root);
        popScope();

        return out.toString();
    }

    private void emitStmt(ASTNode node) {
        if (node == null) {
            throw new IllegalStateException("Internal compiler error: cannot emit null AST node");
        }

        if (node instanceof ProgramNode) {
            ProgramNode p = (ProgramNode) node;
            for (ASTNode stmt : p.statements) {
                emitStmt(stmt);
            }
            return;
        }

        if (node instanceof BlockNode) {
            emitBlock((BlockNode) node);
            return;
        }

        if (node instanceof IfNode) {
            emitIf((IfNode) node);
            return;
        }

        if (node instanceof WhileNode) {
            emitWhile((WhileNode) node);
            return;
        }

        if (node instanceof VarDeclNode) {
            emitVarDecl((VarDeclNode) node);
            return;
        }

        // These sugar nodes should have been removed by Desugarer.
        if (node instanceof VarDeclInitNode || node instanceof VarDeclArrayInitNode) {
            throw new IllegalStateException(
                "Internal compiler error: sugar node reached codegen. "
                + "Run Desugarer before Cpp14Codegen: "
                + node.getClass().getSimpleName()
            );
        }

        if (node instanceof AssignNode) {
            emitAssign((AssignNode) node);
            return;
        }

        if (node instanceof ArrayInitNode) {
            emitArrayInit((ArrayInitNode) node);
            return;
        }

        if (node instanceof ArrayUninitNode) {
            emitArrayUninit((ArrayUninitNode) node);
            return;
        }

        if (node instanceof ArrayMemsetNode) {
            emitArrayMemset((ArrayMemsetNode) node);
            return;
        }

        if (node instanceof ArrayMemcpyNode) {
            emitArrayMemcpy((ArrayMemcpyNode) node);
            return;
        }

        if (node instanceof DelNode) {
            emitDel((DelNode) node);
            return;
        }

        if (node instanceof PrintNode) {
            emitPrint((PrintNode) node);
            return;
        }

        // Expression statement
        if (node instanceof IntNode
                || node instanceof VarNode
                || node instanceof UnaryOpNode
                || node instanceof BinOpNode
                || node instanceof ArrayAccessNode) {
            emitLine(emitExpr(node) + ";");
            return;
        }

        throw new IllegalStateException(
            "Internal compiler error: unsupported statement node for codegen: "
            + node.getClass().getSimpleName()
        );
    }

    private void emitBlock(BlockNode node) {
        emitLine("{");
        indentLevel++;

        pushScope();
        for (ASTNode stmt : node.statements) {
            emitStmt(stmt);
        }
        popScope();

        indentLevel--;
        emitLine("}");
    }

    private void emitIf(IfNode node) {
        emitLine("if (" + emitExpr(node.condition) + ")");
        emitControlledStmt(node.thenBranch);

        if (node.elseBranch != null) {
            emitLine("else");
            emitControlledStmt(node.elseBranch);
        }
    }

    private void emitWhile(WhileNode node) {
        emitLine("while (" + emitExpr(node.condition) + ")");
        emitControlledStmt(node.body);
    }

    /**
     * Emits a statement as the body of if/while.
     *
     * If it's already a block, emit it directly:
     *   if (cond)
     *   { ... }
     *
     * Otherwise indent a single statement:
     *   if (cond)
     *       x = 1;
     */
    private void emitControlledStmt(ASTNode node) {
        if (node instanceof BlockNode) {
            emitStmt(node);
        } else {
            indentLevel++;
            emitStmt(node);
            indentLevel--;
        }
    }

    private void emitVarDecl(VarDeclNode node) {
        declareVarForCodegen(node.name, new VarInfo(node.type, node.isHeap));

        String cppType = emitType(node.type);

        if (node.isHeap) {
            emitLine(
                cppType + "* " + node.name
                + " = static_cast<" + cppType + "*>(std::malloc(sizeof(" + cppType + ")));"
            );
        } else {
            emitLine(cppType + " " + node.name + ";");
        }
    }

    private void emitAssign(AssignNode node) {
        String lhs = emitLValue(node.target);
        String rhs = emitExpr(node.value);
        emitLine(lhs + " = " + rhs + ";");
    }

    /**
     * ArrayInit is still name-based in your AST, because declaration sugar lowers:
     *
     *   array<int> arr(10);
     *
     * into:
     *   VarDeclNode(arr)
     *   ArrayInitNode("arr", 10)
     */
    private void emitArrayInit(ArrayInitNode node) {
        emitLine(emitVarAccess(node.name) + ".init(" + emitExpr(node.size) + ");");
    }

    private void emitArrayUninit(ArrayUninitNode node) {
        emitLine(emitExpr(node.receiver) + ".uninit();");
    }

    private void emitArrayMemset(ArrayMemsetNode node) {
        emitLine(emitExpr(node.receiver) + ".memset(" + emitExpr(node.value) + ");");
    }

    private void emitArrayMemcpy(ArrayMemcpyNode node) {
        emitLine(
            emitExpr(node.target)
            + ".memcpy("
            + emitExpr(node.source)
            + ");"
        );
    }

    private void emitDel(DelNode node) {
        // SemanticAnalyzer is responsible for validating:
        // - variable exists
        // - variable is heap-allocated
        // - variable has not already been deleted
        emitLine("std::free(" + node.name + ");");
    }

    private void emitPrint(PrintNode node) {
        if (node.args == null || node.args.isEmpty()) {
            emitLine("std::cout << '\\n';");
            return;
        }

        StringBuilder sb = new StringBuilder("std::cout");

        for (int i = 0; i < node.args.size(); i++) {
            if (i > 0) {
                sb.append(" << ' '");
            }

            ASTNode arg = node.args.get(i);
            TypeNode argType = inferExprTypeForCodegen(arg);
            String emitted = emitExpr(arg);

            if (argType instanceof PrimitiveTypeNode) {
                PrimitiveKind kind = ((PrimitiveTypeNode) argType).kind;
                if (kind == PrimitiveKind.INT8 || kind == PrimitiveKind.UINT8) {
                    emitted = "(+(" + emitted + "))";
                }
            }

            sb.append(" << ").append(emitted);
        }

        sb.append(" << '\\n';");
        emitLine(sb.toString());
    }

    private String emitExpr(ASTNode node) {
        if (node instanceof IntNode) {
            // Preserve the original literal spelling exactly.
            return ((IntNode) node).value;
        }

        if (node instanceof VarNode) {
            return emitVarAccess(((VarNode) node).name);
        }

        if (node instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) node;
            String inner = emitExpr(n.expr);
            String raw = "(" + n.op + inner + ")";

            TypeNode resultType = inferExprTypeForCodegen(node);

            // Preserve Franko result type at this expression node.
            // This is especially important for:
            //   !x   -> uint8_t instead of C++ bool
            //   -x   -> operand type (e.g. int8_t / uint8_t / etc.)
            if (isPrimitiveIntegerType(resultType)) {
                return emitCastToFrankoType(resultType, raw);
            }

            return raw;
        }

        if (node instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) node;
            String left = emitExpr(n.left);
            String right = emitExpr(n.right);
            String raw = "(" + left + " " + n.op + " " + right + ")";

            TypeNode resultType = inferExprTypeForCodegen(node);

            // Preserve Franko result types:
            //   comparisons/logicals -> uint8_t (not C++ bool)
            //   arithmetic/bitwise/shift -> Franko-defined result type
            if (isPrimitiveIntegerType(resultType)) {
                return emitCastToFrankoType(resultType, raw);
            }

            return raw;
        }

        if (node instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) node;
            return emitExpr(n.target) + "[" + emitExpr(n.index) + "]";
        }

        throw new IllegalStateException(
            "Internal compiler error: unsupported expression node for codegen: "
            + node.getClass().getSimpleName()
        );
    }

    private String emitLValue(ASTNode node) {
        if (node instanceof VarNode) {
            return emitVarAccess(((VarNode) node).name);
        }

        if (node instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) node;
            return emitExpr(n.target) + "[" + emitExpr(n.index) + "]";
        }

        throw new IllegalStateException(
            "Internal compiler error: unsupported lvalue node for codegen: "
            + node.getClass().getSimpleName()
        );
    }

    /**
     * Emits a C++ type from the Franko TypeNode hierarchy.
     */
    private String emitType(TypeNode type) {
        if (type instanceof PrimitiveTypeNode) {
            PrimitiveKind kind = ((PrimitiveTypeNode) type).kind;

            return switch (kind) {
                case INT8 -> "int8_t";
                case INT16 -> "int16_t";
                case INT32 -> "int32_t";
                case INT64 -> "int64_t";
                case UINT8 -> "uint8_t";
                case UINT16 -> "uint16_t";
                case UINT32 -> "uint32_t";
                case UINT64 -> "uint64_t";
            };
        }

        if (type instanceof DynamicArrayTypeNode) {
            DynamicArrayTypeNode t = (DynamicArrayTypeNode) type;
            return "Franko_Dynamic_Array<" + emitType(t.elementType) + ">";
        }

        if (type instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode t = (StaticArrayTypeNode) type;
            return "Franko_Static_Array<" + emitType(t.elementType) + ", " + t.sizeLiteral + ">";
        }

        throw new IllegalStateException(
            "Internal compiler error: unsupported Franko type node in codegen: "
            + type.getClass().getSimpleName()
        );
    }

    private String emitVarAccess(String name) {
        VarInfo info = requireVarForCodegen(name);

        if (info.isHeap) {
            return "(*" + name + ")";
        }

        return name;
    }

    private void pushScope() {
        scopes.push(new LinkedHashMap<>());
    }

    private void popScope() {
        Map<String, VarInfo> popped = scopes.poll();
        if (popped == null) {
            throw new IllegalStateException("Internal compiler error: attempted to pop empty codegen scope stack");
        }
    }

    private void declareVarForCodegen(String name, VarInfo info) {
        Map<String, VarInfo> current = scopes.peek();
        if (current == null) {
            throw new IllegalStateException(
                "Internal compiler error: no active codegen scope when declaring '" + name + "'"
            );
        }

        if (current.containsKey(name)) {
            throw new IllegalStateException(
                "Internal compiler error: duplicate declaration reached codegen in same scope for '"
                + name + "'"
            );
        }

        current.put(name, info);
    }

    private VarInfo requireVarForCodegen(String name) {
        for (Map<String, VarInfo> scope : scopes) {
            VarInfo info = scope.get(name);
            if (info != null) {
                return info;
            }
        }

        throw new IllegalStateException(
            "Internal compiler error: codegen encountered unknown variable '"
            + name
            + "'. SemanticAnalyzer should have rejected this earlier."
        );
    }

    private void emitLine(String s) {
        for (int i = 0; i < indentLevel; i++) {
            out.append("    ");
        }
        out.append(s).append('\n');
    }
}