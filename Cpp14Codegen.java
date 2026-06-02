import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class Cpp14Codegen {
    private static final class VarInfo {
        final boolean isHeap;

        VarInfo(boolean isHeap) {
            this.isHeap = isHeap;
        }
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
        declareVarForCodegen(node.name, new VarInfo(node.isHeap));

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
            sb.append(" << ").append(emitExpr(node.args.get(i)));
        }

        sb.append(" << '\\n';");
        emitLine(sb.toString());
    }

    private String emitExpr(ASTNode node) {
        if (node instanceof IntNode) {
            // Preserve the source literal spelling exactly.
            // This allows decimal, binary (0b...), and hex (0x...) forms
            // to pass directly through to C++14.
            return ((IntNode) node).value;
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