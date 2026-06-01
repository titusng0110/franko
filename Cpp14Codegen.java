import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class VarInfo {
    String type;
    boolean isHeap;

    VarInfo(String type, boolean isHeap) {
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

        if (node instanceof VarDeclNode) {
            emitVarDecl((VarDeclNode) node);
            return;
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

    private void emitArrayInit(ArrayInitNode node) {
        emitLine(emitReceiver(node.name) + ".init(" + emitExpr(node.size) + ");");
    }

    private void emitArrayUninit(ArrayUninitNode node) {
        emitLine(emitReceiver(node.name) + ".uninit();");
    }

    private void emitArrayMemset(ArrayMemsetNode node) {
        emitLine(emitReceiver(node.name) + ".memset(" + emitExpr(node.value) + ");");
    }

    private void emitArrayMemcpy(ArrayMemcpyNode node) {
        emitLine(emitReceiver(node.target) + ".memcpy(" + emitReceiver(node.source) + ");");
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
            emitLine("std::cout << \"\";");
            return;
        }

        StringBuilder sb = new StringBuilder("std::cout");
        for (ASTNode arg : node.args) {
            sb.append(" << ").append(emitExpr(arg));
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
            return emitReceiver(n.name) + "[" + emitExpr(n.index) + "]";
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
            return emitReceiver(n.name) + "[" + emitExpr(n.index) + "]";
        }

        throw new RuntimeException("Unsupported lvalue node: " + node.getClass().getSimpleName());
    }

    private String emitType(String frankoType) {
        // Remove whitespace just in case
        frankoType = frankoType.replace(" ", "");

        switch (frankoType) {
            case "int32_t":
                return "int32_t";
            case "uint32_t":
                return "uint32_t";
            case "float32_t":
                return "float";
            case "char8_t":
                return "char";
            default:
                break;
        }

        if (!frankoType.startsWith("array<") || !frankoType.endsWith(">")) {
            throw new RuntimeException("Unsupported Franko type: " + frankoType);
        }

        String inner = frankoType.substring("array<".length(), frankoType.length() - 1);
        int split = findTopLevelComma(inner);

        if (split == -1) {
            String elemType = emitType(inner);
            return "Franko_Dynamic_Array<" + elemType + ">";
        } else {
            String elem = inner.substring(0, split);
            String size = inner.substring(split + 1);
            String elemType = emitType(elem);
            return "Franko_Static_Array<" + elemType + ", " + size + ">";
        }
    }

    /**
     * Finds the comma at the top nesting level inside array<...>.
     *
     * Examples:
     * - "int32_t" -> -1
     * - "int32_t,8" -> comma position
     * - "array<int32_t>,8" -> top-level comma position
     */
    private int findTopLevelComma(String s) {
        int depth = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return i;
            }
        }

        return -1;
    }

    private String emitVarAccess(String name) {
        VarInfo info = requireVar(name);

        if (info.isHeap) {
            return "(*" + name + ")";
        }

        return name;
    }

    private String emitReceiver(String name) {
        // Same underlying representation as variable access,
        // but semantically clearer when used for method calls / indexing.
        return emitVarAccess(name);
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
