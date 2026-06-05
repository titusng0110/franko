/**
 * ============================================================================
 * CPP14 CODE GENERATOR
 * ============================================================================
 *
 * PURPOSE:
 * Cpp14Codegen emits C++14 code from the fully lowered, type-checked Semantic
 * AST.
 *
 * This generator assumes these phases have already succeeded:
 *
 *   1. Parser
 *   2. AST visitor
 *   3. Desugarer
 *   4. SemanticAnalyzer
 *   5. MasterChecker
 *
 * Therefore, this code generator does not perform legality checking. It trusts
 * that:
 *
 *   - variables are resolved to VariableSymbol objects,
 *   - expression types are already inferred,
 *   - invalid operators were rejected,
 *   - invalid assignments were rejected,
 *   - invalid address arithmetic was rejected,
 *   - invalid array intrinsic usage was rejected.
 *
 * ----------------------------------------------------------------------------
 * ADDRESS REPRESENTATION
 * ----------------------------------------------------------------------------
 *
 * Franko:
 *
 *   addr<T>
 *
 * is emitted as:
 *
 *   T*
 *
 * Franko:
 *
 *   getaddr(x)
 *
 * is emitted as:
 *
 *   (&x)
 *
 * Franko:
 *
 *   deref(p)
 *
 * is emitted as:
 *
 *   (*p)
 *
 * Because the checker rejects pointer arithmetic and raw numeric address
 * construction, the generated C++ pointer representation remains constrained
 * to Franko's safe typed-address model.
 *
 * ----------------------------------------------------------------------------
 * ARRAY REPRESENTATION
 * ----------------------------------------------------------------------------
 *
 * Franko dynamic arrays:
 *
 *   array<T>
 *
 * emit as:
 *
 *   Franko_Dynamic_Array<T>
 *
 * Franko static arrays:
 *
 *   array<T, N>
 *
 * emit as:
 *
 *   Franko_Static_Array<T, N>
 *
 * Array intrinsics are lowered by SemanticAnalyzer:
 *
 *   arr(size)          -> SemanticArrayInitNode
 *   arr.uninit()       -> SemanticArrayUninitNode
 *   arr.memset(value)  -> SemanticArrayMemsetNode
 *   arr.memcpy(src)    -> SemanticArrayMemcpyNode
 *
 * ============================================================================
 */
public class Cpp14Codegen {

    private final StringBuilder out = new StringBuilder();
    private int indentLevel = 0;

    /**
     * Generates only the Franko program body.
     *
     * Intended for replacing __FRANKO_PROGRAM__ inside ProgramTemplate.cpp.
     */
    public String generate(SemanticASTNode root) {
        if (root == null) {
            throw new IllegalStateException("Internal compiler error: cannot generate code for null Semantic AST");
        }

        out.setLength(0);
        indentLevel = 0;

        emitNode(root);

        return out.toString();
    }

    // ============================================================
    // Root Dispatch
    // ============================================================

    private void emitNode(SemanticASTNode node) {
        if (node instanceof SemanticProgramNode program) {
            emitProgram(program);
            return;
        }

        if (node instanceof SemanticStmtNode stmt) {
            emitStmt(stmt);
            return;
        }

        if (node instanceof SemanticExprNode expr) {
            emitLine(emitExpr(expr) + ";");
            return;
        }

        throw new IllegalStateException(
            "Internal compiler error: unsupported SemanticASTNode for codegen: "
                + node.getClass().getSimpleName()
        );
    }

    private void emitProgram(SemanticProgramNode node) {
        for (SemanticStmtNode stmt : node.statements) {
            emitStmt(stmt);
        }
    }

    // ============================================================
    // Statements
    // ============================================================

    private void emitStmt(SemanticStmtNode node) {
        if (node == null) {
            throw new IllegalStateException("Internal compiler error: cannot emit null statement");
        }

        if (node instanceof SemanticProgramNode program) {
            emitProgram(program);
            return;
        }

        if (node instanceof SemanticBlockNode block) {
            emitBlock(block);
            return;
        }

        if (node instanceof SemanticVarDeclNode decl) {
            emitVarDecl(decl);
            return;
        }

        if (node instanceof SemanticAssignNode assign) {
            emitAssign(assign);
            return;
        }

        if (node instanceof SemanticIfNode ifNode) {
            emitIf(ifNode);
            return;
        }

        if (node instanceof SemanticWhileNode whileNode) {
            emitWhile(whileNode);
            return;
        }

        if (node instanceof SemanticDelNode del) {
            emitDel(del);
            return;
        }

        if (node instanceof SemanticPrintNode print) {
            emitPrint(print);
            return;
        }

        if (node instanceof SemanticExprStmtNode exprStmt) {
            emitLine(emitExpr(exprStmt.expr) + ";");
            return;
        }

        if (node instanceof SemanticArrayInitNode init) {
            emitArrayInit(init);
            return;
        }

        if (node instanceof SemanticArrayUninitNode uninit) {
            emitArrayUninit(uninit);
            return;
        }

        if (node instanceof SemanticArrayMemsetNode memset) {
            emitArrayMemset(memset);
            return;
        }

        if (node instanceof SemanticArrayMemcpyNode memcpy) {
            emitArrayMemcpy(memcpy);
            return;
        }

        throw new IllegalStateException(
            "Internal compiler error: unsupported semantic statement for codegen: "
                + node.getClass().getSimpleName()
        );
    }

    private void emitBlock(SemanticBlockNode node) {
        emitLine("{");
        indentLevel++;

        for (SemanticStmtNode stmt : node.statements) {
            emitStmt(stmt);
        }

        indentLevel--;
        emitLine("}");
    }

    private void emitVarDecl(SemanticVarDeclNode node) {
        VariableSymbol sym = node.symbol;

        String cppType = emitType(sym.type);
        String name = emitSymbolName(sym);

        if (sym.isHeap) {
            /*
             * Use new/delete rather than malloc/free.
             *
             * This matters for array wrapper structs because default member
             * initializers such as:
             *
             *   uint32_t length = 0;
             *   T* data = nullptr;
             *
             * are only reliably initialized through construction.
             */
            emitLine(cppType + "* " + name + " = new " + cppType + "();");
        } else {
            emitLine(cppType + " " + name + ";");
        }
    }

    private void emitAssign(SemanticAssignNode node) {
        emitLine(emitLValue(node.target) + " = " + emitExpr(node.value) + ";");
    }

    private void emitIf(SemanticIfNode node) {
        emitLine("if (" + emitExpr(node.condition) + ")");
        emitControlledStmt(node.thenBranch);

        if (node.elseBranch != null) {
            emitLine("else");
            emitControlledStmt(node.elseBranch);
        }
    }

    private void emitWhile(SemanticWhileNode node) {
        emitLine("while (" + emitExpr(node.condition) + ")");
        emitControlledStmt(node.body);
    }

    private void emitControlledStmt(SemanticStmtNode node) {
        if (node instanceof SemanticBlockNode) {
            emitStmt(node);
            return;
        }

        indentLevel++;
        emitStmt(node);
        indentLevel--;
    }

    private void emitDel(SemanticDelNode node) {
        VariableSymbol sym = node.symbol;

        if (!sym.isHeap) {
            throw new IllegalStateException(
                "Internal compiler error: non-heap del reached codegen for variable '"
                    + sym.name
                    + "'. MasterChecker should have rejected this."
            );
        }

        emitLine("delete " + emitSymbolName(sym) + ";");
        emitLine(emitSymbolName(sym) + " = nullptr;");
    }

    private void emitPrint(SemanticPrintNode node) {
        if (node.args == null || node.args.isEmpty()) {
            emitLine("std::cout << '\\n';");
            return;
        }

        StringBuilder sb = new StringBuilder("std::cout");

        for (int i = 0; i < node.args.size(); i++) {
            if (i > 0) {
                sb.append(" << ' '");
            }

            SemanticExprNode arg = node.args.get(i);
            sb.append(" << ").append(emitPrintableExpr(arg));
        }

        sb.append(" << '\\n';");

        emitLine(sb.toString());
    }

    // ============================================================
    // Array Intrinsics
    // ============================================================

    private void emitArrayInit(SemanticArrayInitNode node) {
        emitLine(
            emitVarAccess(node.symbol)
                + ".init("
                + emitExpr(node.size)
                + ");"
        );
    }

    private void emitArrayUninit(SemanticArrayUninitNode node) {
        emitLine(
            emitExpr(node.receiver)
                + ".uninit();"
        );
    }

    private void emitArrayMemset(SemanticArrayMemsetNode node) {
        emitLine(
            emitExpr(node.receiver)
                + ".memset("
                + emitExpr(node.value)
                + ");"
        );
    }

    private void emitArrayMemcpy(SemanticArrayMemcpyNode node) {
        emitLine(
            emitExpr(node.target)
                + ".memcpy("
                + emitExpr(node.source)
                + ");"
        );
    }

    // ============================================================
    // Expressions
    // ============================================================

    private String emitExpr(SemanticExprNode node) {
        if (node == null) {
            throw new IllegalStateException("Internal compiler error: cannot emit null expression");
        }

        if (node instanceof SemanticIntLiteralNode literal) {
            return emitIntLiteral(literal);
        }

        if (node instanceof SemanticVarExprNode var) {
            return emitVarAccess(var.symbol);
        }

        if (node instanceof SemanticUnaryOpNode unary) {
            return emitUnaryOp(unary);
        }

        if (node instanceof SemanticBinOpNode bin) {
            return emitBinOp(bin);
        }

        if (node instanceof SemanticArrayAccessNode access) {
            return emitArrayAccess(access);
        }

        if (node instanceof SemanticGetAddrNode getAddr) {
            return emitGetAddr(getAddr);
        }

        if (node instanceof SemanticDerefNode deref) {
            return emitDeref(deref);
        }

        throw new IllegalStateException(
            "Internal compiler error: unsupported semantic expression for codegen: "
                + node.getClass().getSimpleName()
        );
    }

    private String emitIntLiteral(SemanticIntLiteralNode node) {
        /*
         * Preserve exact source spelling:
         *
         *   123
         *   0b1010
         *   0xff
         *
         * Contextual casts are emitted by parent expressions or assignments
         * when required by C++ implicit conversion rules.
         */
        return node.rawValue;
    }

    private String emitUnaryOp(SemanticUnaryOpNode node) {
        String inner = emitExpr(node.expr);
        String raw = "(" + node.op + inner + ")";

        return castToSemanticExprTypeIfNeeded(node, raw);
    }

    private String emitBinOp(SemanticBinOpNode node) {
        String left = emitExpr(node.left);
        String right = emitExpr(node.right);

        String raw = "(" + left + " " + node.op + " " + right + ")";

        return castToSemanticExprTypeIfNeeded(node, raw);
    }

    private String emitArrayAccess(SemanticArrayAccessNode node) {
        return "("
            + emitExpr(node.target)
            + "["
            + emitExpr(node.index)
            + "])";
    }

    private String emitGetAddr(SemanticGetAddrNode node) {
        /*
         * Franko getaddr(lvalue) -> C++ &lvalue
         *
         * Use emitLValue rather than emitExpr so invalid temporary expressions
         * cannot accidentally be address-taken by codegen.
         */
        return "(&" + emitLValue(node.target) + ")";
    }

    private String emitDeref(SemanticDerefNode node) {
        /*
         * Franko deref(p) -> C++ (*p)
         *
         * The expression is also an lvalue in Franko, and C++ (*p) is an lvalue.
         */
        return "(*" + emitExpr(node.expr) + ")";
    }

    // ============================================================
    // LValues
    // ============================================================

    private String emitLValue(SemanticExprNode node) {
        if (node instanceof SemanticVarExprNode var) {
            return emitVarAccess(var.symbol);
        }

        if (node instanceof SemanticArrayAccessNode access) {
            return emitArrayAccess(access);
        }

        if (node instanceof SemanticDerefNode deref) {
            return emitDeref(deref);
        }

        throw new IllegalStateException(
            "Internal compiler error: unsupported lvalue expression for codegen: "
                + node.getClass().getSimpleName()
        );
    }

    // ============================================================
    // Types
    // ============================================================

    private String emitType(SemanticType type) {
        if (type instanceof SemanticPrimitiveType primitive) {
            return emitPrimitiveType(primitive.kind);
        }

        if (type instanceof SemanticDynamicArrayType dynamicArray) {
            return "Franko_Dynamic_Array<"
                + emitType(dynamicArray.elementType)
                + ">";
        }

        if (type instanceof SemanticStaticArrayType staticArray) {
            return "Franko_Static_Array<"
                + emitType(staticArray.elementType)
                + ", "
                + staticArray.sizeLiteral
                + ">";
        }

        if (type instanceof SemanticAddrType addr) {
            return emitType(addr.referencedType) + "*";
        }

        throw new IllegalStateException(
            "Internal compiler error: unsupported semantic type for codegen: "
                + type.getClass().getSimpleName()
        );
    }

    private String emitPrimitiveType(SemanticPrimitiveKind kind) {
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

    // ============================================================
    // Symbol / Variable Access
    // ============================================================

    private String emitVarAccess(VariableSymbol symbol) {
        if (symbol == null) {
            throw new IllegalStateException("Internal compiler error: null symbol in codegen");
        }

        if (symbol.isHeap) {
            return "(*" + emitSymbolName(symbol) + ")";
        }

        return emitSymbolName(symbol);
    }

    private String emitSymbolName(VariableSymbol symbol) {
        /*
         * Currently, SemanticAnalyzer rejects duplicate declarations only within
         * the same lexical scope, and C++ also supports block-local shadowing.
         *
         * Therefore the raw source name is currently sufficient.
         *
         * If Franko later permits symbols that are not valid C++ identifiers,
         * or if generated temporaries are introduced, centralize name mangling
         * here.
         */
        return symbol.name;
    }

    // ============================================================
    // Casts / Result Type Preservation
    // ============================================================

    private String castToSemanticExprTypeIfNeeded(
        SemanticExprNode node,
        String raw
    ) {
        if (node.type instanceof SemanticPrimitiveType) {
            return "static_cast<" + emitType(node.type) + ">(" + raw + ")";
        }

        /*
         * Do not cast addresses or arrays.
         *
         * Address comparisons are cast by the comparison node's uint8_t result
         * type, not by the address-valued subexpressions themselves.
         */
        return raw;
    }

    // ============================================================
    // Print Helpers
    // ============================================================

    private String emitPrintableExpr(SemanticExprNode expr) {
        String emitted = emitExpr(expr);

        if (expr.type instanceof SemanticPrimitiveType primitive) {
            /*
             * int8_t / uint8_t are usually aliases of char-like types.
             * Unary plus promotes them so std::cout prints a number rather than
             * a character.
             */
            if (primitive.kind == SemanticPrimitiveKind.INT8
                || primitive.kind == SemanticPrimitiveKind.UINT8) {
                return "(+(" + emitted + "))";
            }

            return emitted;
        }

        if (expr.type instanceof SemanticAddrType) {
            /*
             * Avoid accidental char-pointer string printing for addresses.
             *
             * Print address values as raw pointer addresses.
             */
            return "static_cast<const void*>(" + emitted + ")";
        }

        return emitted;
    }

    // ============================================================
    // Output Helpers
    // ============================================================

    private void emitLine(String s) {
        for (int i = 0; i < indentLevel; i++) {
            out.append("    ");
        }

        out.append(s).append('\n');
    }
}