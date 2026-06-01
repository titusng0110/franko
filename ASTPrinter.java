// ================= AST PRINTER =================

public class ASTPrinter {

    public static void print(ASTNode node, int indent) {
        String pad = "  ".repeat(indent);

        if (node == null) {
            System.out.println(pad + "NULL NODE (BUG)");
            return;
        }

        if (node instanceof ProgramNode) {
            System.out.println(pad + "Program");
            for (ASTNode stmt : ((ProgramNode) node).statements) {
                print(stmt, indent + 1);
            }
        }

        else if (node instanceof BlockNode) {
            System.out.println(pad + "Block");
            for (ASTNode stmt : ((BlockNode) node).statements) {
                print(stmt, indent + 1);
            }
        }

        else if (node instanceof VarDeclNode) {
            VarDeclNode n = (VarDeclNode) node;
            System.out.println(pad + "VarDecl: " + typeToString(n.type) + " " + n.name +
                (n.isHeap ? " (heap)" : ""));
        }

        else if (node instanceof VarDeclInitNode) {
            VarDeclInitNode n = (VarDeclInitNode) node;
            System.out.println(pad + "VarDeclInit: " + typeToString(n.type) + " " + n.name +
                (n.isHeap ? " (heap)" : ""));
            print(n.init, indent + 1);
        }

        else if (node instanceof VarDeclArrayInitNode) {
            VarDeclArrayInitNode n = (VarDeclArrayInitNode) node;
            System.out.println(pad + "VarDeclArrayInit: " + typeToString(n.type) + " " + n.name +
                (n.isHeap ? " (heap)" : ""));
            print(n.size, indent + 1);
        }

        else if (node instanceof AssignNode) {
            System.out.println(pad + "Assign");
            print(((AssignNode) node).target, indent + 1);
            print(((AssignNode) node).value, indent + 1);
        }

        else if (node instanceof IfNode) {
            IfNode n = (IfNode) node;
            System.out.println(pad + "If");
            System.out.println(pad + "  Condition:");
            print(n.condition, indent + 2);
            System.out.println(pad + "  Then:");
            print(n.thenBranch, indent + 2);
            if (n.elseBranch != null) {
                System.out.println(pad + "  Else:");
                print(n.elseBranch, indent + 2);
            }
        }

        else if (node instanceof WhileNode) {
            WhileNode n = (WhileNode) node;
            System.out.println(pad + "While");
            System.out.println(pad + "  Condition:");
            print(n.condition, indent + 2);
            System.out.println(pad + "  Body:");
            print(n.body, indent + 2);
        }

        else if (node instanceof VarNode) {
            System.out.println(pad + "Var: " + ((VarNode) node).name);
        }

        else if (node instanceof IntNode) {
            System.out.println(pad + "Int: " + ((IntNode) node).value);
        }

        else if (node instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) node;
            System.out.println(pad + "UnaryOp: " + n.op);
            print(n.expr, indent + 1);
        }

        else if (node instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) node;
            System.out.println(pad + "BinOp: " + n.op);
            print(n.left, indent + 1);
            print(n.right, indent + 1);
        }

        else if (node instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) node;
            System.out.println(pad + "ArrayAccess: " + n.name);
            print(n.index, indent + 1);
        }

        else if (node instanceof ArrayInitNode) {
            ArrayInitNode n = (ArrayInitNode) node;
            System.out.println(pad + "ArrayInit: " + n.name);
            print(n.size, indent + 1);
        }

        else if (node instanceof ArrayUninitNode) {
            System.out.println(pad + "ArrayUninit: " + ((ArrayUninitNode) node).name);
        }

        else if (node instanceof ArrayMemsetNode) {
            ArrayMemsetNode n = (ArrayMemsetNode) node;
            System.out.println(pad + "ArrayMemset: " + n.name);
            print(n.value, indent + 1);
        }

        else if (node instanceof ArrayMemcpyNode) {
            ArrayMemcpyNode n = (ArrayMemcpyNode) node;
            System.out.println(pad + "ArrayMemcpy: " + n.target + " <- " + n.source);
        }

        else if (node instanceof DelNode) {
            System.out.println(pad + "Delete: " + ((DelNode) node).name);
        }

        else if (node instanceof PrintNode) {
            PrintNode n = (PrintNode) node;
            System.out.println(pad + "Print");
            for (ASTNode arg : n.args) {
                print(arg, indent + 1);
            }
        }

        else {
            System.out.println(pad + "Unknown node: " + node.getClass());
        }
    }

    private static String typeToString(TypeNode type) {
        if (type instanceof PrimitiveTypeNode) {
            PrimitiveKind kind = ((PrimitiveTypeNode) type).kind;
            return switch (kind) {
                case INT32 -> "int32_t";
                case UINT32 -> "uint32_t";
                case FLOAT32 -> "float32_t";
                case CHAR8 -> "char8_t";
            };
        }

        if (type instanceof DynamicArrayTypeNode) {
            DynamicArrayTypeNode t = (DynamicArrayTypeNode) type;
            return "array<" + typeToString(t.elementType) + ">";
        }

        if (type instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode t = (StaticArrayTypeNode) type;
            return "array<" + typeToString(t.elementType) + "," + t.size + ">";
        }

        return "<unknown-type>";
    }
}