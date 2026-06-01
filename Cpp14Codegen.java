import java.util.LinkedHashMap;
import java.util.Map;

class VarInfo {
    TypeNode type;
    boolean isHeap;

    VarInfo(TypeNode type, boolean isHeap) {
        this.type = type;
        this.isHeap = isHeap;
    }
}

public class Cpp14Codegen {
    private final Map<String, VarInfo> symbols = new LinkedHashMap<>();
    private final StringBuilder out = new StringBuilder();
    private int indentLevel = 0;

    /**
     * Generates ONLY the Franko program body.
     *
     * Intended for replacing __FRANKO_PROGRAM__ inside ProgramTemplate.cpp.
     */
    public String generate(ASTNode root) {
        symbols.clear();
        out.setLength(0);
        indentLevel = 0;

        emitStmt(root);

        return out.toString();
    }

    /**
     * Optional convenience helper:
     * inject the generated program body into a template string.
     */
    public String injectIntoTemplate(ASTNode root, String templateSource) {
        String body = indentBlock(generate(root), 1);
        return templateSource.replace("__FRANKO_PROGRAM__", body.trim());
    }

    private void emitStmt(ASTNode node) {
        if (node == null) {
            throw new RuntimeException("Cannot emit null AST node");
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
            throw new RuntimeException(
                "Sugar node reached codegen. Run Desugarer before Cpp14Codegen: "
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

        throw new RuntimeException(
            "Unsupported statement node for codegen: " + node.getClass().getSimpleName()
        );
    }

    private void emitBlock(BlockNode node) {
        emitLine("{");
        indentLevel++;
        for (ASTNode stmt : node.statements) {
            emitStmt(stmt);
        }
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
        symbols.put(node.name, new VarInfo(node.type, node.isHeap));

        String cppType = emitType(node.type);

        if (node.isHeap) {
            emitLine(cppType + "* " + node.name + " = new " + cppType + "();");
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
        emitLine(emitVarReceiver(node.name) + ".init(" + emitExpr(node.size) + ");");
    }

    private void emitArrayUninit(ArrayUninitNode node) {
        emitLine(emitReceiverExpr(node.receiver) + ".uninit();");
    }

    private void emitArrayMemset(ArrayMemsetNode node) {
        emitLine(emitReceiverExpr(node.receiver) + ".memset(" + emitExpr(node.value) + ");");
    }

    private void emitArrayMemcpy(ArrayMemcpyNode node) {
        emitLine(
            emitReceiverExpr(node.target)
            + ".memcpy("
            + emitReceiverExpr(node.source)
            + ");"
        );
    }

    private void emitDel(DelNode node) {
        VarInfo info = requireVar(node.name);

        if (!info.isHeap) {
            throw new RuntimeException("Cannot delete non-heap variable: " + node.name);
        }

        emitLine("delete " + node.name + ";");
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
            sb.append(" << ").append(emitExpr(node.args.get(i)));
        }

        sb.append(" << '\\n';");
        emitLine(sb.toString());
    }

    private String emitExpr(ASTNode node) {
        if (node instanceof IntNode) {
            return Integer.toString(((IntNode) node).value);
        }

        if (node instanceof VarNode) {
            return emitVarAccess(((VarNode) node).name);
        }

        if (node instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) node;
            return "(" + n.op + emitExpr(n.expr) + ")";
        }

        if (node instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) node;
            return "(" + emitExpr(n.left) + " " + n.op + " " + emitExpr(n.right) + ")";
        }

        if (node instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) node;
            return emitExpr(n.target) + "[" + emitExpr(n.index) + "]";
        }

        throw new RuntimeException(
            "Unsupported expression node for codegen: " + node.getClass().getSimpleName()
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

        throw new RuntimeException(
            "Unsupported lvalue node: " + node.getClass().getSimpleName()
        );
    }

    /**
     * Emits a receiver expression for method-like array operations.
     *
     * Examples:
     *   arr           -> arr   or (*arr) if heap
     *   arr[i]        -> arr[i]
     *   map[r][c]     -> map[r][c]
     *
     * Important:
     * - var receivers still respect heap-vs-nonheap via emitVarAccess
     * - general nested expressions recurse through emitExpr
     */
    private String emitReceiverExpr(ASTNode node) {
        return emitExpr(node);
    }

    /**
     * Emits a receiver for a plain variable name.
     * Used for name-based operations like ArrayInitNode.
     */
    private String emitVarReceiver(String name) {
        return emitVarAccess(name);
    }

    /**
     * Emits a C++ type from the Franko TypeNode hierarchy.
     */
    private String emitType(TypeNode type) {
        if (type instanceof PrimitiveTypeNode) {
            PrimitiveKind kind = ((PrimitiveTypeNode) type).kind;

            return switch (kind) {
                case INT32 -> "int32_t";
                case UINT32 -> "uint32_t";
                case FLOAT32 -> "float";
                case CHAR8 -> "char";
            };
        }

        if (type instanceof DynamicArrayTypeNode) {
            DynamicArrayTypeNode t = (DynamicArrayTypeNode) type;
            return "Franko_Dynamic_Array<" + emitType(t.elementType) + ">";
        }

        if (type instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode t = (StaticArrayTypeNode) type;
            return "Franko_Static_Array<" + emitType(t.elementType) + ", " + t.size + ">";
        }

        throw new RuntimeException(
            "Unsupported Franko type node in codegen: " + type.getClass().getSimpleName()
        );
    }

    private String emitVarAccess(String name) {
        VarInfo info = requireVar(name);

        if (info.isHeap) {
            return "(*" + name + ")";
        }

        return name;
    }

    private VarInfo requireVar(String name) {
        VarInfo info = symbols.get(name);

        if (info == null) {
            throw new RuntimeException("Unknown variable in codegen: " + name);
        }

        return info;
    }

    private void emitLine(String s) {
        for (int i = 0; i < indentLevel; i++) {
            out.append("    ");
        }
        out.append(s).append('\n');
    }

    private String indentBlock(String text, int levels) {
        String indent = "    ".repeat(levels);
        String[] lines = text.split("\\R", -1);

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!line.isEmpty()) {
                sb.append(indent).append(line);
            }
            sb.append('\n');
        }

        return sb.toString();
    }
}