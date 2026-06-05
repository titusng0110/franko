public class ASTPrinter {

    public static void print(ASTNode node, int indent) {
        String pad = "  ".repeat(indent);

        if (node == null) {
            System.out.println(pad + "NULL NODE (BUG)");
            return;
        }

        // --- Statements & Blocks ---
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

        else if (node instanceof ExprStmtNode) {
            System.out.println(pad + "ExprStmt");
            print(((ExprStmtNode) node).expr, indent + 1);
        }

        // --- Declarations ---
        else if (node instanceof VarDeclNode) {
            VarDeclNode n = (VarDeclNode) node;
            System.out.println(
                pad + "VarDecl: " + typeToString(n.type) + " " + n.name +
                (n.isHeap ? " (heap)" : "")
            );
        }

        else if (node instanceof VarDeclInitNode) {
            VarDeclInitNode n = (VarDeclInitNode) node;
            System.out.println(
                pad + "VarDeclInit: " + typeToString(n.type) + " " + n.name +
                (n.isHeap ? " (heap)" : "")
            );
            print(n.init, indent + 1);
        }

        else if (node instanceof VarDeclArrayInitNode) {
            VarDeclArrayInitNode n = (VarDeclArrayInitNode) node;
            System.out.println(
                pad + "VarDeclArrayInit: " + typeToString(n.type) + " " + n.name +
                (n.isHeap ? " (heap)" : "")
            );
            print(n.size, indent + 1);
        }

        // --- Control Flow & Basic Statements ---
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

        // --- Expressions ---
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

        // --- Access & Calls (Refactored from specific array instructions) ---
        else if (node instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) node;
            System.out.println(pad + "ArrayAccess");
            System.out.println(pad + "  Target:");
            print(n.target, indent + 2);
            System.out.println(pad + "  Index:");
            print(n.index, indent + 2);
        }

        else if (node instanceof MemberAccessNode) {
            MemberAccessNode n = (MemberAccessNode) node;
            System.out.println(pad + "MemberAccess: " + n.memberName);
            print(n.target, indent + 1);
        }

        else if (node instanceof CallNode) {
            CallNode n = (CallNode) node;
            System.out.println(pad + "Call");
            System.out.println(pad + "  Callee:");
            print(n.callee, indent + 2);
            if (!n.args.isEmpty()) {
                System.out.println(pad + "  Args:");
                for (ASTNode arg : n.args) {
                    print(arg, indent + 2);
                }
            }
        }

        // --- Memory / Pointers ---
        else if (node instanceof GetAddrNode) {
            GetAddrNode n = (GetAddrNode) node;
            System.out.println(pad + "GetAddr");
            print(n.target, indent + 1);
        }

        else if (node instanceof DerefNode) {
            DerefNode n = (DerefNode) node;
            System.out.println(pad + "Deref");
            print(n.expr, indent + 1);
        }

        // --- Fallback ---
        else {
            System.out.println(pad + "Unknown node: " + node.getClass().getSimpleName());
        }
    }

    private static String typeToString(TypeNode type) {
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
            return "array<" + typeToString(t.elementType) + ">";
        }

        if (type instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode t = (StaticArrayTypeNode) type;
            return "array<" + typeToString(t.elementType) + "," + t.sizeLiteral + ">";
        }

        if (type instanceof AddrTypeNode) {
            AddrTypeNode t = (AddrTypeNode) type;
            return "addr<" + typeToString(t.referencedType) + ">";
        }

        return "<unknown-type>";
    }
}
