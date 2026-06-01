import java.util.*;

// Base AST node
abstract class ASTNode {}

// Program
class ProgramNode extends ASTNode {
    List<ASTNode> statements;

    ProgramNode(List<ASTNode> statements) {
        this.statements = statements;
    }
}

// Variable declaration
class VarDeclNode extends ASTNode {
    String type;
    String name;
    boolean isHeap;

    VarDeclNode(String type, String name, boolean isHeap) {
        this.type = type;
        this.name = name;
        this.isHeap = isHeap;
    }
}

// Assignment
class AssignNode extends ASTNode {
    ASTNode target;
    ASTNode value;

    AssignNode(ASTNode target, ASTNode value) {
        this.target = target;
        this.value = value;
    }
}

// Variable reference
class VarNode extends ASTNode {
    String name;

    VarNode(String name) {
        this.name = name;
    }
}

// Integer literal
class IntNode extends ASTNode {
    int value;

    IntNode(int value) {
        this.value = value;
    }
}

class UnaryOpNode extends ASTNode {
    String op;
    ASTNode expr;

    UnaryOpNode(String op, ASTNode expr) {
        this.op = op;
        this.expr = expr;
    }
}


// Binary operation
class BinOpNode extends ASTNode {
    String op;
    ASTNode left;
    ASTNode right;

    BinOpNode(String op, ASTNode left, ASTNode right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }
}

// Array access
class ArrayAccessNode extends ASTNode {
    String name;
    ASTNode index;

    ArrayAccessNode(String name, ASTNode index) {
        this.name = name;
        this.index = index;
    }
}

// Array init
class ArrayInitNode extends ASTNode {
    String name;
    ASTNode size;

    ArrayInitNode(String name, ASTNode size) {
        this.name = name;
        this.size = size;
    }
}

// Array uninit
class ArrayUninitNode extends ASTNode {
    String name;

    ArrayUninitNode(String name) {
        this.name = name;
    }
}

// Delete
class DelNode extends ASTNode {
    String name;

    DelNode(String name) {
        this.name = name;
    }
}


// ================= VISITOR =================

public class FrankoASTVisitor extends FrankoBaseVisitor<ASTNode> {

    // program
    @Override
    public ASTNode visitProgram(FrankoParser.ProgramContext ctx) {
        List<ASTNode> statements = new ArrayList<>();

        for (FrankoParser.StatementContext stmt : ctx.statement()) {
            
            ASTNode node = visit(stmt);
            if (node != null) {
                statements.add(node);
            }

        }

        return new ProgramNode(statements);
    }

    
    @Override
    public ASTNode visitStatement(FrankoParser.StatementContext ctx) {
        if (ctx.varDecl() != null) return visit(ctx.varDecl());
        if (ctx.delStmt() != null) return visit(ctx.delStmt());
        if (ctx.assignStmt() != null) return visit(ctx.assignStmt());
        if (ctx.arrayInitStmt() != null) return visit(ctx.arrayInitStmt());
        if (ctx.arrayUninitStmt() != null) return visit(ctx.arrayUninitStmt());
        if (ctx.exprStmt() != null) return visit(ctx.exprStmt());

        return null; // safety fallback
    }

    // varDecl
    @Override
    public ASTNode visitVarDecl(FrankoParser.VarDeclContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        String type = ctx.type().getText();

        boolean isHeap = ctx.getText().startsWith("alloc");

        return new VarDeclNode(type, name, isHeap);
    }

    // assignment
    @Override
    public ASTNode visitAssignStmt(FrankoParser.AssignStmtContext ctx) {
        ASTNode target = visit(ctx.lvalue());
        ASTNode value = visit(ctx.expr());

        return new AssignNode(target, value);
    }

    // delete
    @Override
    public ASTNode visitDelStmt(FrankoParser.DelStmtContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        return new DelNode(name);
    }

    // array init: a(10)
    @Override
    public ASTNode visitArrayInitStmt(FrankoParser.ArrayInitStmtContext ctx) {
        return new ArrayInitNode(
            ctx.IDENTIFIER().getText(),
            visit(ctx.expr())
        );
    }

    // array uninit: a.uninit()
    @Override
    public ASTNode visitArrayUninitStmt(FrankoParser.ArrayUninitStmtContext ctx) {
        return new ArrayUninitNode(ctx.IDENTIFIER().getText());
    }

    // lvalue
    @Override
    public ASTNode visitLvalue(FrankoParser.LvalueContext ctx) {
        if (ctx.expr() == null) {
            return new VarNode(ctx.IDENTIFIER().getText());
        } else {
            return new ArrayAccessNode(
                ctx.IDENTIFIER().getText(),
                visit(ctx.expr())
            );
        }
    }

    // atoms
    @Override
    public ASTNode visitAtom(FrankoParser.AtomContext ctx) {
        if (ctx.INT_LITERAL() != null) {
            return new IntNode(Integer.parseInt(ctx.INT_LITERAL().getText()));
        }

        if (ctx.IDENTIFIER() != null && ctx.expr() == null) {
            return new VarNode(ctx.IDENTIFIER().getText());
        }

        if (ctx.IDENTIFIER() != null && ctx.expr() != null) {
            return new ArrayAccessNode(
                ctx.IDENTIFIER().getText(),
                visit(ctx.expr())
            );
        }

        return visit(ctx.expr());
    }

    
    @Override
    public ASTNode visitUnaryMinus(FrankoParser.UnaryMinusContext ctx) {
        return new UnaryOpNode(
            "-",
            visit(ctx.expr())
        );
    }


    // +
    @Override
    public ASTNode visitAddSub(FrankoParser.AddSubContext ctx) {
        return new BinOpNode(
            ctx.op.getText(),
            visit(ctx.expr(0)),
            visit(ctx.expr(1))
        );
    }

    // *
    @Override
    public ASTNode visitMulDiv(FrankoParser.MulDivContext ctx) {
        return new BinOpNode(
            ctx.op.getText(),
            visit(ctx.expr(0)),
            visit(ctx.expr(1))
        );
    }

        
    @Override
    public ASTNode visitExprStmt(FrankoParser.ExprStmtContext ctx) {
        return visit(ctx.expr());
    }

}

class ASTPrinter {

    public static void print(ASTNode node, int indent) {
        String pad = "  ".repeat(indent);

        if (node instanceof ProgramNode) {
            System.out.println(pad + "Program");
            for (ASTNode stmt : ((ProgramNode) node).statements) {
                print(stmt, indent + 1);
            }
        }

        else if (node instanceof VarDeclNode) {
            VarDeclNode n = (VarDeclNode) node;
            System.out.println(pad + "VarDecl: " + n.type + " " + n.name + 
                (n.isHeap ? " (heap)" : ""));
        }

        else if (node instanceof AssignNode) {
            System.out.println(pad + "Assign");
            print(((AssignNode) node).target, indent + 1);
            print(((AssignNode) node).value, indent + 1);
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
            System.out.println(pad + "ArrayUninit: " + 
                ((ArrayUninitNode) node).name);
        }

        else if (node instanceof DelNode) {
            System.out.println(pad + "Delete: " + 
                ((DelNode) node).name);
        }
        
        else if (node == null) {
            System.out.println(pad + "NULL NODE (BUG)");
            return;
        }

        else {
            System.out.println(pad + "Unknown node: " + node.getClass());
        }
    }
}
