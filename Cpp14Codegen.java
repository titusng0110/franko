import java.util.ArrayList;
import java.util.List;

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
 *   - function calls are resolved to FunctionSymbol overloads,
 *   - expression types are already inferred,
 *   - invalid operators were rejected,
 *   - invalid assignments were rejected,
 *   - invalid address arithmetic was rejected,
 *   - invalid array intrinsic usage was rejected,
 *   - invalid function return usage was rejected.
 *
 * ----------------------------------------------------------------------------
 * FUNCTION REPRESENTATION
 * ----------------------------------------------------------------------------
 *
 * Franko:
 *
 *   func main() -> int32_t {
 *       return 0;
 *   }
 *
 * emits as:
 *
 *   int32_t main()
 *   {
 *       return static_cast<int32_t>(0);
 *   }
 *
 * Function prototypes are emitted before function definitions so Franko forward
 * references and mutual recursion compile in C++.
 *
 * Function calls are emitted using the selected FunctionSymbol. Primitive
 * arguments are cast to the selected parameter type to preserve Franko overload
 * resolution in C++.
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
 * Array intrinsics are lowered by SemanticAnalyzer into expression nodes:
 *
 *   target(size)                         -> SemanticArrayIntrinsicCallNode INIT
 *   target.init(size)                    -> SemanticArrayIntrinsicCallNode INIT
 *   target.init_zero(size)               -> SemanticArrayIntrinsicCallNode INIT_ZERO
 *   target.resize(size)                  -> SemanticArrayIntrinsicCallNode RESIZE
 *   target.uninit()                      -> SemanticArrayIntrinsicCallNode UNINIT
 *   target.memset(value)                 -> SemanticArrayIntrinsicCallNode MEMSET
 *   target.memset(value, start, count)   -> SemanticArrayIntrinsicCallNode MEMSET
 *   target.memcpy(sourceAddr)            -> SemanticArrayIntrinsicCallNode MEMCPY
 *   target.memcpy(sourceAddr, dstStart, srcStart, count)
 *                                      -> SemanticArrayIntrinsicCallNode MEMCPY
 *   target.memmove(dstStart, srcStart, count)
 *                                      -> SemanticArrayIntrinsicCallNode MEMMOVE
 *
 * Because array intrinsics are expressions, status-returning intrinsics such as
 * init/init_zero/resize may appear in value contexts after checking:
 *
 *   status = xs.init(10);
 *
 * Void-returning intrinsics are emitted the same way, but MasterChecker rejects
 * their use in value contexts.
 *
 * ============================================================================
 */
public class Cpp14Codegen {

    private final StringBuilder out = new StringBuilder();
    private int indentLevel = 0;

    private static final SemanticType UINT8_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8);

    private static final SemanticType UINT32_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.UINT32);

    /**
     * Generates the Franko program as C++14 source content intended to replace
     * __FRANKO_PROGRAM__ inside ProgramTemplate.cpp.
     */
    public String generate(SemanticASTNode root) {
        if (root == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot generate code for null Semantic AST"
            );
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

        if (node instanceof SemanticFunctionDeclNode fn) {
            emitFunctionDecl(fn);
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

    /**
     * Emits top-level items.
     *
     * Current Franko global-scope rule:
     *
     *   global scope may contain only:
     *
     *     - global variable declarations,
     *     - function declarations.
     *
     * Codegen emits in dependency-safe C++ order:
     *
     *   1. all global variable declarations,
     *   2. all function prototypes,
     *   3. all function definitions.
     *
     * This matters because Franko allows functions to reference global variables
     * declared later in source order:
     *
     *   func main() -> int32_t {
     *       g = 1
     *       return 0
     *   }
     *
     *   uint32_t g
     *
     * The generated C++ must declare g before main's function body.
     */
    private void emitProgram(SemanticProgramNode node) {
        List<SemanticVarDeclNode> globals = new ArrayList<>();
        List<SemanticFunctionDeclNode> functions = new ArrayList<>();

        /*
         * Partition top-level items.
         *
         * SemanticAnalyzer should already ensure only global variable declarations
         * and function declarations appear here. The fallback error below is
         * defensive.
         */
        for (SemanticASTNode item : node.topLevelItems) {
            if (item instanceof SemanticVarDeclNode global) {
                globals.add(global);
                continue;
            }

            if (item instanceof SemanticFunctionDeclNode fn) {
                functions.add(fn);
                continue;
            }

            if (item == null) {
                throw new IllegalStateException(
                        "Internal compiler error: null top-level item in SemanticProgramNode"
                );
            }

            throw new IllegalStateException(
                    "Internal compiler error: unsupported global top-level item for codegen: "
                            + item.getClass().getSimpleName()
            );
        }

        /*
         * 1. Emit global variables first.
         */
        if (!globals.isEmpty()) {
            for (SemanticVarDeclNode global : globals) {
                emitVarDecl(global);
            }

            emitLine("");
        }

        /*
         * 2. Emit function prototypes.
         *
         * This supports forward references, direct recursion, and mutual recursion.
         */
        if (!functions.isEmpty()) {
            for (SemanticFunctionDeclNode fn : functions) {
                emitFunctionPrototype(fn);
            }

            emitLine("");
        }

        /*
         * 3. Emit function definitions.
         *
         * Definitions are emitted in source order relative to other functions.
         */
        for (SemanticFunctionDeclNode fn : functions) {
            emitFunctionDecl(fn);
            emitLine("");
        }
    }

    // ============================================================
    // Functions
    // ============================================================

    private void emitFunctionPrototype(SemanticFunctionDeclNode node) {
        emitLine(emitFunctionHeader(node) + ";");
    }

    private void emitFunctionDecl(SemanticFunctionDeclNode node) {
        emitLine(emitFunctionHeader(node));

        if (node.body == null) {
            throw new IllegalStateException(
                    "Internal compiler error: function '"
                            + node.symbol.name
                            + "' has null body"
            );
        }

        emitBlock(node.body);
    }

    private String emitFunctionHeader(SemanticFunctionDeclNode node) {
        if (node == null || node.symbol == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null function declaration or symbol"
            );
        }

        StringBuilder sb = new StringBuilder();

        sb.append(emitType(node.symbol.returnType()));
        sb.append(" ");
        sb.append(emitFunctionName(node.symbol));
        sb.append("(");

        List<ParameterSymbol> params = node.symbol.parameters;

        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            ParameterSymbol param = params.get(i);
            sb.append(emitType(param.type));
            sb.append(" ");
            sb.append(emitSymbolName(param));
        }

        sb.append(")");

        return sb.toString();
    }

    // ============================================================
    // Statements
    // ============================================================

    private void emitStmt(SemanticStmtNode node) {
        if (node == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot emit null statement"
            );
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

        if (node instanceof SemanticReturnNode ret) {
            emitReturn(ret);
            return;
        }

        if (node instanceof SemanticExprStmtNode exprStmt) {
            emitLine(emitExpr(exprStmt.expr) + ";");
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
            emitLine(cppType + "* " + name + " = static_cast<" + cppType + "*>(je_malloc(sizeof(" + cppType + ")));");
            emitLine("if (!" + name + ") throw std::bad_alloc();");
            emitLine("new (" + name + ") " + cppType + ";");
        } else {
            emitLine(cppType + " " + name + ";");
        }
    }

    private void emitAssign(SemanticAssignNode node) {
        emitLine(
                emitLValue(node.target)
                        + " = "
                        + emitExprAsType(node.value, node.target.type)
                        + ";"
        );
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

        emitLine(emitSymbolName(sym) + "->~" + emitType(sym.type) + "();");
        emitLine("je_free(" + emitSymbolName(sym) + ");");
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

    private void emitReturn(SemanticReturnNode node) {
        if (node.value == null) {
            emitLine("return;");
            return;
        }

        SemanticType targetType = node.function != null
                ? node.function.returnType()
                : node.value.type;

        emitLine("return " + emitExprAsType(node.value, targetType) + ";");
    }

    // ============================================================
    // Expressions
    // ============================================================

    private String emitExpr(SemanticExprNode node) {
        if (node == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot emit null expression"
            );
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

        if (node instanceof SemanticFunctionCallNode call) {
            return emitFunctionCall(call);
        }

        if (node instanceof SemanticArrayIntrinsicCallNode intrinsic) {
            return emitArrayIntrinsicCall(intrinsic);
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
         * Binary literals are valid in C++14.
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
                + emitExprAsType(
                        node.index,
                        UINT32_TYPE
                )
                + "])";
    }

    private String emitFunctionCall(SemanticFunctionCallNode node) {
        FunctionSymbol fn = node.function;

        if (fn == null) {
            throw new IllegalStateException(
                    "Internal compiler error: function call has null resolved function"
            );
        }

        StringBuilder sb = new StringBuilder();

        sb.append(emitFunctionName(fn));
        sb.append("(");

        List<SemanticType> parameterTypes = fn.parameterTypes();

        if (parameterTypes.size() != node.args.size()) {
            throw new IllegalStateException(
                    "Internal compiler error: resolved function call arity mismatch for "
                            + fn.fullSignatureString()
                            + ": expected "
                            + parameterTypes.size()
                            + ", got "
                            + node.args.size()
            );
        }

        for (int i = 0; i < node.args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            SemanticExprNode arg = node.args.get(i);
            SemanticType paramType = parameterTypes.get(i);

            /*
             * This cast is essential for preserving Franko overload resolution.
             *
             * Franko has already selected exactly one overload. C++ overload
             * resolution should not get a chance to reinterpret literal or
             * folded-constant arguments differently.
             */
            sb.append(emitExprAsType(arg, paramType));
        }

        sb.append(")");

        return sb.toString();
    }

    private String emitArrayIntrinsicCall(SemanticArrayIntrinsicCallNode node) {
        if (node.kind == null) {
            throw new IllegalStateException(
                    "Internal compiler error: array intrinsic call has null kind"
            );
        }

        validateArrayIntrinsicArityForCodegen(node);

        StringBuilder sb = new StringBuilder();

        sb.append(emitLValue(node.receiver));
        sb.append(".");
        sb.append(node.kind.runtimeName);
        sb.append("(");

        for (int i = 0; i < node.args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(emitArrayIntrinsicArg(node, i));
        }

        sb.append(")");

        return sb.toString();
    }

    private String emitArrayIntrinsicArg(
            SemanticArrayIntrinsicCallNode node,
            int index
    ) {
        SemanticExprNode arg = node.args.get(index);

        return switch (node.kind) {
            case INIT, INIT_ZERO, RESIZE ->
                    emitExprAsType(arg, UINT32_TYPE);

            case UNINIT ->
                    throw new IllegalStateException(
                            "Internal compiler error: uninit() should not have arguments"
                    );

            case MEMSET -> {
                if (index == 0) {
                    yield emitExprAsType(arg, UINT8_TYPE);
                }

                yield emitExprAsType(arg, UINT32_TYPE);
            }

            case MEMCPY -> {
                if (index == 0) {
                    /*
                     * memcpy source is addr<array<...>>.
                     *
                     * Example:
                     *
                     *   dst.memcpy(getaddr(src))
                     *
                     * emits:
                     *
                     *   dst.memcpy((&src))
                     *
                     * The runtime expects a pointer to the source array object.
                     */
                    yield emitExpr(arg);
                }

                yield emitExprAsType(arg, UINT32_TYPE);
            }

            case MEMMOVE ->
                    emitExprAsType(arg, UINT32_TYPE);
        };
    }

    private void validateArrayIntrinsicArityForCodegen(
            SemanticArrayIntrinsicCallNode node
    ) {
        boolean ok = switch (node.kind) {
            case INIT, INIT_ZERO, RESIZE ->
                    node.args.size() == 1;

            case UNINIT ->
                    node.args.isEmpty();

            case MEMSET ->
                    node.args.size() == 1 || node.args.size() == 3;

            case MEMCPY ->
                    node.args.size() == 1 || node.args.size() == 4;

            case MEMMOVE ->
                    node.args.size() == 3;
        };

        if (!ok) {
            throw new IllegalStateException(
                    "Internal compiler error: invalid arity for array intrinsic '"
                            + node.kind.runtimeName
                            + "': got "
                            + node.args.size()
            );
        }
    }

    private String emitGetAddr(SemanticGetAddrNode node) {
        /*
         * Franko getaddr(lvalue) -> C++ &lvalue
         */
        return "(&" + emitLValue(node.target) + ")";
    }

    private String emitDeref(SemanticDerefNode node) {
        /*
         * Franko deref(p) -> C++ (*p)
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
        if (type instanceof SemanticVoidType) {
            return "void";
        }

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
    // Symbol / Name Emission
    // ============================================================

    private String emitVarAccess(VariableSymbol symbol) {
        if (symbol == null) {
            throw new IllegalStateException(
                    "Internal compiler error: null symbol in codegen"
            );
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
         */
        return symbol.name;
    }

    private String emitFunctionName(FunctionSymbol symbol) {
        /*
         * Franko supports overloads by parameter type/order.
         * C++ also supports overloads by parameter type/order.
         *
         * Because Franko rejects declarations that differ only by return type,
         * the raw function name can be emitted directly.
         */
        return symbol.name;
    }

    // ============================================================
    // Casts / Contextual Emission
    // ============================================================

    /**
     * Emits an expression in a context that expects targetType.
     *
     * This is used for:
     *
     *   - assignments,
     *   - returns,
     *   - function call arguments,
     *   - array sizes,
     *   - array indexes,
     *   - memset values,
     *   - ranged memset/memcpy/memmove arguments.
     *
     * Primitive casts are emitted explicitly to preserve Franko's contextual
     * constant typing and overload resolution.
     */
    private String emitExprAsType(
            SemanticExprNode expr,
            SemanticType targetType
    ) {
        String raw = emitExpr(expr);

        if (targetType instanceof SemanticPrimitiveType) {
            /*
             * Constants are fluid in Franko, so keep contextual casts for them.
             *
             * Examples:
             *
             *   uint8_t x;
             *   x = 1;
             *
             * should emit:
             *
             *   x = static_cast<uint8_t>(1);
             *
             * Function call arguments also rely on contextual primitive casts to
             * preserve Franko overload resolution.
             */
            if (expr.isConstant()) {
                return "static_cast<" + emitType(targetType) + ">(" + raw + ")";
            }

            /*
             * If the expression is already semantically the requested primitive
             * type, do not wrap it again.
             *
             * This avoids:
             *
             *   static_cast<uint32_t>(static_cast<uint32_t>((x + 1)))
             *
             * because emitBinOp(...) already emitted:
             *
             *   static_cast<uint32_t>((x + 1))
             */
            if (sameSemanticType(expr.type, targetType)) {
                return raw;
            }

            return "static_cast<" + emitType(targetType) + ">(" + raw + ")";
        }

        /*
         * Do not cast arrays or addresses unless Franko later defines explicit
         * conversions for them. Address arguments/returns are already strongly
         * typed and checked exactly.
         */
        return raw;
    }

    private String castToSemanticExprTypeIfNeeded(
            SemanticExprNode node,
            String raw
    ) {
        if (node.type instanceof SemanticPrimitiveType) {
            return "static_cast<" + emitType(node.type) + ">(" + raw + ")";
        }

        /*
         * Do not cast addresses or arrays.
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

    private boolean sameSemanticType(SemanticType a, SemanticType b) {
        if (a == null || b == null) {
            return false;
        }

        return a.equals(b);
    }
}