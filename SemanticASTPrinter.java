import java.math.BigInteger;

/**
 * ============================================================================
 * SEMANTIC AST PRINTER
 * ============================================================================
 *
 * PURPOSE:
 * SemanticASTPrinter prints the fully lowered, symbol-resolved, type-decorated
 * Semantic AST for debugging compiler phases.
 *
 * It prints:
 *
 *   - top-level items,
 *   - function declarations,
 *   - function symbols and signatures,
 *   - parameter variables,
 *   - return statements,
 *   - function call expressions,
 *   - variable symbols,
 *   - expression types,
 *   - folded constant values,
 *   - lvalue flags,
 *   - lowered array intrinsic nodes.
 *
 * ============================================================================
 */
public class SemanticASTPrinter {

    private static final String INDENT = "  ";

    private SemanticASTPrinter() {
        // Utility class; do not instantiate.
    }

    public static void print(SemanticASTNode node, int indent) {
        if (node == null) {
            line(indent, "<null SemanticASTNode>");
            return;
        }

        if (node instanceof SemanticProgramNode n) {
            printProgram(n, indent);
            return;
        }

        if (node instanceof SemanticFunctionDeclNode n) {
            printFunctionDecl(n, indent);
            return;
        }

        if (node instanceof SemanticStmtNode n) {
            printStmt(n, indent);
            return;
        }

        if (node instanceof SemanticExprNode n) {
            printExpr(n, indent);
            return;
        }

        line(indent, "Unknown SemanticASTNode: "
                + node.getClass().getSimpleName());
    }

    // ============================================================
    // Program / Top-level
    // ============================================================

    private static void printProgram(SemanticProgramNode node, int indent) {
        line(indent, "SemanticProgramNode");
        line(indent + 1, "topLevelItems: " + node.topLevelItems.size());

        for (int i = 0; i < node.topLevelItems.size(); i++) {
            line(indent + 1, "[" + i + "]");
            print(node.topLevelItems.get(i), indent + 2);
        }
    }

    private static void printFunctionDecl(
            SemanticFunctionDeclNode node,
            int indent
    ) {
        line(indent, "SemanticFunctionDeclNode");

        line(indent + 1, "symbol:");
        printFunctionSymbol(node.symbol, indent + 2);

        line(indent + 1, "parameterVariables: "
                + node.parameterVariables.size());

        for (int i = 0; i < node.parameterVariables.size(); i++) {
            line(indent + 1, "[" + i + "]");
            printVariableSymbol(
                    "parameter",
                    node.parameterVariables.get(i),
                    indent + 2
            );
        }

        line(indent + 1, "body:");
        printBlock(node.body, indent + 2);
    }

    // ============================================================
    // Statements
    // ============================================================

    private static void printStmt(SemanticStmtNode node, int indent) {
        if (node == null) {
            line(indent, "<null SemanticStmtNode>");
            return;
        }

        if (node instanceof SemanticBlockNode n) {
            printBlock(n, indent);
            return;
        }

        if (node instanceof SemanticVarDeclNode n) {
            printVarDecl(n, indent);
            return;
        }

        if (node instanceof SemanticAssignNode n) {
            printAssign(n, indent);
            return;
        }

        if (node instanceof SemanticIfNode n) {
            printIf(n, indent);
            return;
        }

        if (node instanceof SemanticWhileNode n) {
            printWhile(n, indent);
            return;
        }

        if (node instanceof SemanticDelNode n) {
            printDel(n, indent);
            return;
        }

        if (node instanceof SemanticPrintNode n) {
            printPrint(n, indent);
            return;
        }

        if (node instanceof SemanticReturnNode n) {
            printReturn(n, indent);
            return;
        }

        if (node instanceof SemanticExprStmtNode n) {
            printExprStmt(n, indent);
            return;
        }

        if (node instanceof SemanticArrayInitNode n) {
            printArrayInit(n, indent);
            return;
        }

        if (node instanceof SemanticArrayUninitNode n) {
            printArrayUninit(n, indent);
            return;
        }

        if (node instanceof SemanticArrayMemsetNode n) {
            printArrayMemset(n, indent);
            return;
        }

        if (node instanceof SemanticArrayMemcpyNode n) {
            printArrayMemcpy(n, indent);
            return;
        }

        line(indent, "Unknown SemanticStmtNode: "
                + node.getClass().getSimpleName());
    }

    private static void printBlock(SemanticBlockNode node, int indent) {
        if (node == null) {
            line(indent, "<null SemanticBlockNode>");
            return;
        }

        line(indent, "SemanticBlockNode");
        line(indent + 1, "statements: " + node.statements.size());

        for (int i = 0; i < node.statements.size(); i++) {
            line(indent + 1, "[" + i + "]");
            printStmt(node.statements.get(i), indent + 2);
        }
    }

    private static void printVarDecl(SemanticVarDeclNode node, int indent) {
        line(indent, "SemanticVarDeclNode");
        printVariableSymbol("symbol", node.symbol, indent + 1);
    }

    private static void printAssign(SemanticAssignNode node, int indent) {
        line(indent, "SemanticAssignNode");

        line(indent + 1, "target:");
        printExpr(node.target, indent + 2);

        line(indent + 1, "value:");
        printExpr(node.value, indent + 2);
    }

    private static void printIf(SemanticIfNode node, int indent) {
        line(indent, "SemanticIfNode");

        line(indent + 1, "condition:");
        printExpr(node.condition, indent + 2);

        line(indent + 1, "thenBranch:");
        printStmt(node.thenBranch, indent + 2);

        line(indent + 1, "elseBranch:");
        if (node.elseBranch == null) {
            line(indent + 2, "<none>");
        } else {
            printStmt(node.elseBranch, indent + 2);
        }
    }

    private static void printWhile(SemanticWhileNode node, int indent) {
        line(indent, "SemanticWhileNode");

        line(indent + 1, "condition:");
        printExpr(node.condition, indent + 2);

        line(indent + 1, "body:");
        printStmt(node.body, indent + 2);
    }

    private static void printDel(SemanticDelNode node, int indent) {
        line(indent, "SemanticDelNode");
        printVariableSymbol("symbol", node.symbol, indent + 1);
    }

    private static void printPrint(SemanticPrintNode node, int indent) {
        line(indent, "SemanticPrintNode");
        line(indent + 1, "args: " + node.args.size());

        for (int i = 0; i < node.args.size(); i++) {
            line(indent + 1, "[" + i + "]");
            printExpr(node.args.get(i), indent + 2);
        }
    }

    private static void printReturn(SemanticReturnNode node, int indent) {
        line(indent, "SemanticReturnNode");

        line(indent + 1, "function:");
        printFunctionSymbol(node.function, indent + 2);

        line(indent + 1, "value:");
        if (node.value == null) {
            line(indent + 2, "<bare return>");
        } else {
            printExpr(node.value, indent + 2);
        }
    }

    private static void printExprStmt(SemanticExprStmtNode node, int indent) {
        line(indent, "SemanticExprStmtNode");

        line(indent + 1, "expr:");
        printExpr(node.expr, indent + 2);
    }

    // ============================================================
    // Array Intrinsics
    // ============================================================

    private static void printArrayInit(SemanticArrayInitNode node, int indent) {
        line(indent, "SemanticArrayInitNode");

        line(indent + 1, "target:");
        printExpr(node.target, indent + 2);

        line(indent + 1, "size:");
        printExpr(node.size, indent + 2);
    }

    private static void printArrayUninit(
            SemanticArrayUninitNode node,
            int indent
    ) {
        line(indent, "SemanticArrayUninitNode");

        line(indent + 1, "receiver:");
        printExpr(node.receiver, indent + 2);
    }

    private static void printArrayMemset(
            SemanticArrayMemsetNode node,
            int indent
    ) {
        line(indent, "SemanticArrayMemsetNode");

        line(indent + 1, "receiver:");
        printExpr(node.receiver, indent + 2);

        line(indent + 1, "value:");
        printExpr(node.value, indent + 2);
    }

    private static void printArrayMemcpy(
            SemanticArrayMemcpyNode node,
            int indent
    ) {
        line(indent, "SemanticArrayMemcpyNode");

        line(indent + 1, "target:");
        printExpr(node.target, indent + 2);

        line(indent + 1, "source:");
        printExpr(node.source, indent + 2);
    }

    // ============================================================
    // Expressions
    // ============================================================

    private static void printExpr(SemanticExprNode node, int indent) {
        if (node == null) {
            line(indent, "<null SemanticExprNode>");
            return;
        }

        if (node instanceof SemanticIntLiteralNode n) {
            printIntLiteral(n, indent);
            return;
        }

        if (node instanceof SemanticVarExprNode n) {
            printVarExpr(n, indent);
            return;
        }

        if (node instanceof SemanticUnaryOpNode n) {
            printUnaryOp(n, indent);
            return;
        }

        if (node instanceof SemanticBinOpNode n) {
            printBinOp(n, indent);
            return;
        }

        if (node instanceof SemanticArrayAccessNode n) {
            printArrayAccess(n, indent);
            return;
        }

        if (node instanceof SemanticFunctionCallNode n) {
            printFunctionCall(n, indent);
            return;
        }

        if (node instanceof SemanticGetAddrNode n) {
            printGetAddr(n, indent);
            return;
        }

        if (node instanceof SemanticDerefNode n) {
            printDeref(n, indent);
            return;
        }

        line(indent, "Unknown SemanticExprNode: "
                + node.getClass().getSimpleName());

        printExprMetadata(node, indent + 1);
    }

    private static void printIntLiteral(
            SemanticIntLiteralNode node,
            int indent
    ) {
        line(indent, "SemanticIntLiteralNode");
        printExprMetadata(node, indent + 1);
        line(indent + 1, "rawValue: " + quote(node.rawValue));
    }

    private static void printVarExpr(
            SemanticVarExprNode node,
            int indent
    ) {
        line(indent, "SemanticVarExprNode");
        printExprMetadata(node, indent + 1);
        printVariableSymbol("symbol", node.symbol, indent + 1);
    }

    private static void printUnaryOp(
            SemanticUnaryOpNode node,
            int indent
    ) {
        line(indent, "SemanticUnaryOpNode");
        printExprMetadata(node, indent + 1);
        line(indent + 1, "op: " + quote(node.op));

        line(indent + 1, "expr:");
        printExpr(node.expr, indent + 2);
    }

    private static void printBinOp(
            SemanticBinOpNode node,
            int indent
    ) {
        line(indent, "SemanticBinOpNode");
        printExprMetadata(node, indent + 1);
        line(indent + 1, "op: " + quote(node.op));

        line(indent + 1, "left:");
        printExpr(node.left, indent + 2);

        line(indent + 1, "right:");
        printExpr(node.right, indent + 2);
    }

    private static void printArrayAccess(
            SemanticArrayAccessNode node,
            int indent
    ) {
        line(indent, "SemanticArrayAccessNode");
        printExprMetadata(node, indent + 1);

        line(indent + 1, "target:");
        printExpr(node.target, indent + 2);

        line(indent + 1, "index:");
        printExpr(node.index, indent + 2);
    }

    private static void printFunctionCall(
            SemanticFunctionCallNode node,
            int indent
    ) {
        line(indent, "SemanticFunctionCallNode");
        printExprMetadata(node, indent + 1);

        line(indent + 1, "function:");
        printFunctionSymbol(node.function, indent + 2);

        line(indent + 1, "args: " + node.args.size());
        for (int i = 0; i < node.args.size(); i++) {
            line(indent + 1, "[" + i + "]");
            printExpr(node.args.get(i), indent + 2);
        }
    }

    private static void printGetAddr(
            SemanticGetAddrNode node,
            int indent
    ) {
        line(indent, "SemanticGetAddrNode");
        printExprMetadata(node, indent + 1);

        line(indent + 1, "target:");
        printExpr(node.target, indent + 2);
    }

    private static void printDeref(
            SemanticDerefNode node,
            int indent
    ) {
        line(indent, "SemanticDerefNode");
        printExprMetadata(node, indent + 1);

        line(indent + 1, "expr:");
        printExpr(node.expr, indent + 2);
    }

    // ============================================================
    // Symbol / Metadata Helpers
    // ============================================================

    private static void printExprMetadata(SemanticExprNode node, int indent) {
        line(indent, "type: " + describeType(node.type));
        line(indent, "constantValue: " + describeConstant(node.constantValue));
        line(indent, "isConstant: " + node.isConstant());
        line(indent, "isLValue: " + node.isLValue());
    }

    private static void printVariableSymbol(
            String label,
            VariableSymbol symbol,
            int indent
    ) {
        if (symbol == null) {
            line(indent, label + ": <null>");
            return;
        }

        String kind = symbol instanceof ParameterSymbol
                ? "ParameterSymbol"
                : "VariableSymbol";

        line(indent, label + ": " + kind);
        line(indent + 1, "name: " + quote(symbol.name));
        line(indent + 1, "type: " + describeType(symbol.type));
        line(indent + 1, "isHeap: " + symbol.isHeap);
        line(indent + 1, "deleted: " + symbol.deleted);

        if (symbol instanceof ParameterSymbol param) {
            line(indent + 1, "index: " + param.index);
        }

        line(indent + 1, "identity: " + identity(symbol));
    }

    private static void printFunctionSymbol(
            FunctionSymbol symbol,
            int indent
    ) {
        if (symbol == null) {
            line(indent, "<null FunctionSymbol>");
            return;
        }

        line(indent, "FunctionSymbol");
        line(indent + 1, "name: " + quote(symbol.name));
        line(indent + 1, "signature: " + symbol.signatureString());
        line(indent + 1, "fullSignature: " + symbol.fullSignatureString());
        line(indent + 1, "returnType: " + describeType(symbol.returnType()));
        line(indent + 1, "type: " + describeType(symbol.type()));
        line(indent + 1, "arity: " + symbol.arity());
        line(indent + 1, "isBuiltin: " + symbol.isBuiltin);
        line(indent + 1, "identity: " + identity(symbol));

        line(indent + 1, "parameters: " + symbol.parameters.size());
        for (int i = 0; i < symbol.parameters.size(); i++) {
            line(indent + 1, "[" + i + "]");
            printVariableSymbol(
                    "parameter",
                    symbol.parameters.get(i),
                    indent + 2
            );
        }
    }

    private static String describeType(SemanticType type) {
        return type == null ? "<null>" : type.describe();
    }

    private static String describeConstant(BigInteger value) {
        return value == null ? "<none>" : value.toString();
    }

    private static String quote(String s) {
        if (s == null) {
            return "<null>";
        }

        return "\"" + escape(s) + "\"";
    }

    private static String escape(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String identity(Object obj) {
        if (obj == null) {
            return "<null>";
        }

        return obj.getClass().getSimpleName()
                + "@"
                + Integer.toHexString(System.identityHashCode(obj));
    }

    private static void line(int indent, String text) {
        System.out.println(INDENT.repeat(Math.max(0, indent)) + text);
    }
}