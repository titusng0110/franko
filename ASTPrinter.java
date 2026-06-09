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
        // Expressions / Initializer Forms
        // ============================================================

        else if (node instanceof VarNode n) {
            System.out.println(pad + "Var: " + n.name);
        }

        else if (node instanceof IntNode n) {
            System.out.println(pad + "Int: " + n.value);
        }

        else if (node instanceof ArrayLiteralNode n) {
            System.out.println(pad + "ArrayLiteral");
            System.out.println(pad + "  elements: " + n.elements.size());

            for (int i = 0; i < n.elements.size(); i++) {
                System.out.println(pad + "  [" + i + "]");
                print(n.elements.get(i), indent + 2);
            }
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

            System.out.println(pad + "  target:");
            print(n.target, indent + 2);
        }

        else if (node instanceof DerefNode n) {
            System.out.println(pad + "Deref");

            System.out.println(pad + "  expr:");
            print(n.expr, indent + 2);
        }

        // ============================================================
        // Type Nodes, useful if printer is called directly on a TypeNode
        // ============================================================

        else if (node instanceof PrimitiveTypeNode n) {
            System.out.println(pad + "PrimitiveType: " + typeToString(n));
        }

        else if (node instanceof VoidTypeNode n) {
            System.out.println(pad + "VoidType: " + typeToString(n));
        }

        else if (node instanceof DynamicArrayTypeNode n) {
            System.out.println(pad + "DynamicArrayType");
            System.out.println(pad + "  elementType:");
            print(n.elementType, indent + 2);
        }

        else if (node instanceof StaticArrayTypeNode n) {
            System.out.println(pad + "StaticArrayType");
            System.out.println(pad + "  elementType:");
            print(n.elementType, indent + 2);

            System.out.println(pad + "  sizeExpr:");
            print(n.sizeExpr, indent + 2);
        }

        else if (node instanceof AddrTypeNode n) {
            System.out.println(pad + "AddrType");
            System.out.println(pad + "  referencedType:");
            print(n.referencedType, indent + 2);
        }

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
                    + exprToInlineString(t.sizeExpr)
                    + ">";
        }

        if (type instanceof AddrTypeNode t) {
            return "addr<" + typeToString(t.referencedType) + ">";
        }

        return "<unknown-type:" + type.getClass().getSimpleName() + ">";
    }

    /**
     * Produces a compact one-line rendering of expression-like AST nodes.
     *
     * This is mainly used for type strings such as:
     *
     *   array<int32_t, 1 + 2>
     *
     * Static array sizes are represented as ASTNode sizeExpr now, not as
     * raw string literals.
     */
    private static String exprToInlineString(ASTNode expr) {
        if (expr == null) {
            return "<null-expr>";
        }

        if (expr instanceof IntNode n) {
            return n.value;
        }

        if (expr instanceof VarNode n) {
            return n.name;
        }

        if (expr instanceof UnaryOpNode n) {
            return "(" + n.op + exprToInlineString(n.expr) + ")";
        }

        if (expr instanceof BinOpNode n) {
            return "("
                    + exprToInlineString(n.left)
                    + " "
                    + n.op
                    + " "
                    + exprToInlineString(n.right)
                    + ")";
        }

        if (expr instanceof ArrayAccessNode n) {
            return exprToInlineString(n.target)
                    + "["
                    + exprToInlineString(n.index)
                    + "]";
        }

        if (expr instanceof MemberAccessNode n) {
            return exprToInlineString(n.target)
                    + "."
                    + n.memberName;
        }

        if (expr instanceof CallNode n) {
            StringBuilder sb = new StringBuilder();

            sb.append(exprToInlineString(n.callee));
            sb.append("(");

            for (int i = 0; i < n.args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                sb.append(exprToInlineString(n.args.get(i)));
            }

            sb.append(")");
            return sb.toString();
        }

        if (expr instanceof GetAddrNode n) {
            return "getaddr(" + exprToInlineString(n.target) + ")";
        }

        if (expr instanceof DerefNode n) {
            return "deref(" + exprToInlineString(n.expr) + ")";
        }

        if (expr instanceof ArrayLiteralNode n) {
            StringBuilder sb = new StringBuilder();

            sb.append("[");

            for (int i = 0; i < n.elements.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                sb.append(exprToInlineString(n.elements.get(i)));
            }

            sb.append("]");
            return sb.toString();
        }

        if (expr instanceof TypeNode t) {
            return typeToString(t);
        }

        return "<unknown-expr:" + expr.getClass().getSimpleName() + ">";
    }
}