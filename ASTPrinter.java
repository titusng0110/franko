public class ASTPrinter {

    public static void print(ASTNode node, int indent) {
        String pad = "  ".repeat(indent);

        if (node == null) {
            System.out.println(pad + "NULL NODE");
            return;
        }

        // ============================================================
        // Program / Top-level / Blocks
        // ============================================================

        if (node instanceof ProgramNode n) {
            System.out.println(pad + "Program");
            System.out.println(pad + "  topLevelItems: " + n.topLevelItems.size());

            for (int i = 0; i < n.topLevelItems.size(); i++) {
                System.out.println(pad + "  [" + i + "]");
                print(n.topLevelItems.get(i), indent + 2);
            }
        }

        else if (node instanceof BlockNode n) {
            System.out.println(pad + "Block");
            System.out.println(pad + "  statements: " + n.statements.size());

            for (int i = 0; i < n.statements.size(); i++) {
                System.out.println(pad + "  [" + i + "]");
                print(n.statements.get(i), indent + 2);
            }
        }

        // ============================================================
        // Function Declarations
        // ============================================================

        else if (node instanceof FunctionDeclNode n) {
            System.out.println(pad + "FunctionDecl: " + n.name);
            System.out.println(pad + "  returnType: " + typeToString(n.returnType));
            System.out.println(pad + "  parameters: " + n.parameters.size());

            for (int i = 0; i < n.parameters.size(); i++) {
                System.out.println(pad + "  [" + i + "]");
                print(n.parameters.get(i), indent + 2);
            }

            System.out.println(pad + "  body:");
            print(n.body, indent + 2);
        }

        else if (node instanceof ParameterNode n) {
            System.out.println(
                    pad + "Parameter: "
                            + typeToString(n.type)
                            + " "
                            + n.name
            );
        }

        // ============================================================
        // Statements
        // ============================================================

        else if (node instanceof ExprStmtNode n) {
            System.out.println(pad + "ExprStmt");
            print(n.expr, indent + 1);
        }

        else if (node instanceof VarDeclNode n) {
            System.out.println(
                    pad + "VarDecl: "
                            + typeToString(n.type)
                            + " "
                            + n.name
                            + (n.isHeap ? " (heap)" : "")
            );
        }

        else if (node instanceof VarDeclInitNode n) {
            System.out.println(
                    pad + "VarDeclInit: "
                            + typeToString(n.type)
                            + " "
                            + n.name
                            + (n.isHeap ? " (heap)" : "")
            );

            System.out.println(pad + "  init:");
            print(n.init, indent + 2);
        }

        else if (node instanceof VarDeclArrayInitNode n) {
            System.out.println(
                    pad + "VarDeclArrayInit: "
                            + typeToString(n.type)
                            + " "
                            + n.name
                            + (n.isHeap ? " (heap)" : "")
            );

            System.out.println(pad + "  size:");
            print(n.size, indent + 2);
        }

        else if (node instanceof AssignNode n) {
            System.out.println(pad + "Assign");

            System.out.println(pad + "  target:");
            print(n.target, indent + 2);

            System.out.println(pad + "  value:");
            print(n.value, indent + 2);
        }

        else if (node instanceof IfNode n) {
            System.out.println(pad + "If");

            System.out.println(pad + "  condition:");
            print(n.condition, indent + 2);

            System.out.println(pad + "  thenBranch:");
            print(n.thenBranch, indent + 2);

            System.out.println(pad + "  elseBranch:");
            if (n.elseBranch == null) {
                System.out.println(pad + "    <none>");
            } else {
                print(n.elseBranch, indent + 2);
            }
        }

        else if (node instanceof WhileNode n) {
            System.out.println(pad + "While");

            System.out.println(pad + "  condition:");
            print(n.condition, indent + 2);

            System.out.println(pad + "  body:");
            print(n.body, indent + 2);
        }

        else if (node instanceof DelNode n) {
            System.out.println(pad + "Delete: " + n.name);
        }

        else if (node instanceof PrintNode n) {
            System.out.println(pad + "Print");
            System.out.println(pad + "  args: " + n.args.size());

            for (int i = 0; i < n.args.size(); i++) {
                System.out.println(pad + "  [" + i + "]");
                print(n.args.get(i), indent + 2);
            }
        }

        else if (node instanceof ReturnNode n) {
            System.out.println(pad + "Return");

            if (n.value == null) {
                System.out.println(pad + "  value: <bare return>");
            } else {
                System.out.println(pad + "  value:");
                print(n.value, indent + 2);
            }
        }

        // ============================================================
        // Expressions
        // ============================================================

        else if (node instanceof VarNode n) {
            System.out.println(pad + "Var: " + n.name);
        }

        else if (node instanceof IntNode n) {
            System.out.println(pad + "Int: " + n.value);
        }

        else if (node instanceof UnaryOpNode n) {
            System.out.println(pad + "UnaryOp: " + n.op);
            print(n.expr, indent + 1);
        }

        else if (node instanceof BinOpNode n) {
            System.out.println(pad + "BinOp: " + n.op);

            System.out.println(pad + "  left:");
            print(n.left, indent + 2);

            System.out.println(pad + "  right:");
            print(n.right, indent + 2);
        }

        else if (node instanceof ArrayAccessNode n) {
            System.out.println(pad + "ArrayAccess");

            System.out.println(pad + "  target:");
            print(n.target, indent + 2);

            System.out.println(pad + "  index:");
            print(n.index, indent + 2);
        }

        else if (node instanceof MemberAccessNode n) {
            System.out.println(pad + "MemberAccess: " + n.memberName);

            System.out.println(pad + "  target:");
            print(n.target, indent + 2);
        }

        else if (node instanceof CallNode n) {
            System.out.println(pad + "Call");

            System.out.println(pad + "  callee:");
            print(n.callee, indent + 2);

            System.out.println(pad + "  args: " + n.args.size());
            for (int i = 0; i < n.args.size(); i++) {
                System.out.println(pad + "  [" + i + "]");
                print(n.args.get(i), indent + 2);
            }
        }

        else if (node instanceof GetAddrNode n) {
            System.out.println(pad + "GetAddr");
            print(n.target, indent + 1);
        }

        else if (node instanceof DerefNode n) {
            System.out.println(pad + "Deref");
            print(n.expr, indent + 1);
        }

        // ============================================================
        // Type Nodes, useful if printer is called directly on a TypeNode
        // ============================================================

        else if (node instanceof TypeNode n) {
            System.out.println(pad + "Type: " + typeToString(n));
        }

        // ============================================================
        // Fallback
        // ============================================================

        else {
            System.out.println(
                    pad + "Unknown node: "
                            + node.getClass().getSimpleName()
            );
        }
    }

    private static String typeToString(TypeNode type) {
        if (type == null) {
            return "<null-type>";
        }

        if (type instanceof PrimitiveTypeNode t) {
            return switch (t.kind) {
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

        if (type instanceof VoidTypeNode) {
            return "void";
        }

        if (type instanceof DynamicArrayTypeNode t) {
            return "array<" + typeToString(t.elementType) + ">";
        }

        if (type instanceof StaticArrayTypeNode t) {
            return "array<"
                    + typeToString(t.elementType)
                    + ", "
                    + t.sizeLiteral
                    + ">";
        }

        if (type instanceof AddrTypeNode t) {
            return "addr<" + typeToString(t.referencedType) + ">";
        }

        return "<unknown-type:" + type.getClass().getSimpleName() + ">";
    }
}