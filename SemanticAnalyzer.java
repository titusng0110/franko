import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SemanticAnalyzer performs a lightweight semantic pass before code generation.
 *
 * What it checks:
 *  - declaration before use
 *  - duplicate declarations in the same scope
 *  - block scoping / shadowing rules
 *  - type checking for expressions, assignments, initializers, conditions
 *  - array indexing / array operations validity
 *  - delete only on heap-allocated variables
 *  - detect use-after-delete for simple variable references
 *
 * This analyzer is intentionally conservative and works with both raw AST and
 * partially/fully desugared ASTs.
 */
public class SemanticAnalyzer {

    public static final class SemanticException extends RuntimeException {
        public SemanticException(String message) {
            super(message);
        }
    }

    private static final class Symbol {
        final TypeNode type;
        final boolean isHeap;
        boolean deleted;

        Symbol(TypeNode type, boolean isHeap) {
            this.type = type;
            this.isHeap = isHeap;
            this.deleted = false;
        }
    }

    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
    private final List<String> errors = new ArrayList<>();

    private static final PrimitiveTypeNode INT32 = new PrimitiveTypeNode(PrimitiveKind.INT32);

    public void analyze(ASTNode root) {
        scopes.clear();
        errors.clear();

        pushScope();
        visitStatement(root);
        popScope();

        if (!errors.isEmpty()) {
            throw new SemanticException(formatErrors());
        }
    }

    public List<String> getErrors() {
        return List.copyOf(errors);
    }

    // ============================================================
    // Statement traversal
    // ============================================================

    private void visitStatement(ASTNode node) {
        if (node == null) return;

        if (node instanceof ProgramNode) {
            ProgramNode n = (ProgramNode) node;
            for (ASTNode stmt : n.statements) {
                visitStatement(stmt);
            }
            return;
        }

        if (node instanceof BlockNode) {
            BlockNode n = (BlockNode) node;
            pushScope();
            for (ASTNode stmt : n.statements) {
                visitStatement(stmt);
            }
            popScope();
            return;
        }

        if (node instanceof VarDeclNode) {
            visitVarDecl((VarDeclNode) node);
            return;
        }

        if (node instanceof VarDeclInitNode) {
            visitVarDeclInit((VarDeclInitNode) node);
            return;
        }

        if (node instanceof VarDeclArrayInitNode) {
            visitVarDeclArrayInit((VarDeclArrayInitNode) node);
            return;
        }

        if (node instanceof AssignNode) {
            visitAssign((AssignNode) node);
            return;
        }

        if (node instanceof IfNode) {
            visitIf((IfNode) node);
            return;
        }

        if (node instanceof WhileNode) {
            visitWhile((WhileNode) node);
            return;
        }

        if (node instanceof ArrayInitNode) {
            visitArrayInit((ArrayInitNode) node);
            return;
        }

        if (node instanceof ArrayUninitNode) {
            visitArrayUninit((ArrayUninitNode) node);
            return;
        }

        if (node instanceof ArrayMemsetNode) {
            visitArrayMemset((ArrayMemsetNode) node);
            return;
        }

        if (node instanceof ArrayMemcpyNode) {
            visitArrayMemcpy((ArrayMemcpyNode) node);
            return;
        }

        if (node instanceof DelNode) {
            visitDelete((DelNode) node);
            return;
        }

        if (node instanceof PrintNode) {
            visitPrint((PrintNode) node);
            return;
        }

        // Expression as statement
        inferExprType(node);
    }

    private void visitVarDecl(VarDeclNode node) {
        declare(node.name, node.type, node.isHeap);
    }

    private void visitVarDeclInit(VarDeclInitNode node) {
        // Initializer is checked before declaring the symbol, so the variable is
        // not visible inside its own initializer. This also allows access to any
        // shadowed outer variable of the same name.
        TypeNode initType = inferExprType(node.init);
        ensureAssignable(node.type, initType, node.init,
                "Cannot initialize variable '" + node.name + "' of type " + typeToString(node.type)
                        + " with expression of type " + typeToString(initType));
        declare(node.name, node.type, node.isHeap);
    }

    private void visitVarDeclArrayInit(VarDeclArrayInitNode node) {
        ensureDynamicArrayType(node.type,
                "Declaration-style array initialization requires a dynamic array type for '" + node.name + "'");
        TypeNode sizeType = inferExprType(node.size);
        ensureIntegral(sizeType,
                "Array size expression for '" + node.name + "' must be an integral scalar, got " + typeToString(sizeType));
        declare(node.name, node.type, node.isHeap);
    }

    private void visitAssign(AssignNode node) {
        TypeNode lhsType = inferLValueType(node.target);
        TypeNode rhsType = inferExprType(node.value);
        ensureAssignable(lhsType, rhsType, node.value,
                "Cannot assign expression of type " + typeToString(rhsType)
                        + " to target of type " + typeToString(lhsType));
    }

    private void visitIf(IfNode node) {
        TypeNode condType = inferExprType(node.condition);
        ensureConditionType(condType, "if condition");
        visitStatement(node.thenBranch);
        if (node.elseBranch != null) {
            visitStatement(node.elseBranch);
        }
    }

    private void visitWhile(WhileNode node) {
        TypeNode condType = inferExprType(node.condition);
        ensureConditionType(condType, "while condition");
        visitStatement(node.body);
    }

    private void visitArrayInit(ArrayInitNode node) {
        Symbol sym = resolve(node.name);
        if (sym == null) {
            error("Array init uses undeclared variable '" + node.name + "'");
            inferExprType(node.size);
            return;
        }

        if (sym.deleted) {
            error("Array init on deleted variable '" + node.name + "'");
        }

        ensureDynamicArrayType(sym.type,
                "Array init statement '" + node.name + "(...)' requires '" + node.name + "' to be of dynamic array type");

        TypeNode sizeType = inferExprType(node.size);
        ensureIntegral(sizeType,
                "Array size expression for '" + node.name + "' must be an integral scalar, got " + typeToString(sizeType));
    }

    private void visitArrayUninit(ArrayUninitNode node) {
        TypeNode receiverType = inferExprType(node.receiver);
        ensureArrayType(receiverType,
                "uninit() receiver must be an array, got " + typeToString(receiverType));
    }

    
    private void visitArrayMemset(ArrayMemsetNode node) {
        TypeNode receiverType = inferExprType(node.receiver);
        ensureArrayType(receiverType,
                "memset() receiver must be an array, got " + typeToString(receiverType));

        if (!isMemsetable(receiverType)) {
            error("memset() receiver type is not memsetable: " + typeToString(receiverType));
        }

        // For now, only allow int32_t as the memset fill value.
        TypeNode valueType = inferExprType(node.value);
        if (!(valueType instanceof PrimitiveTypeNode)
                || ((PrimitiveTypeNode) valueType).kind != PrimitiveKind.INT32) {
            error("memset() fill value must currently be int32_t, got " + typeToString(valueType));
        }
    }


    private void visitArrayMemcpy(ArrayMemcpyNode node) {
        TypeNode targetType = inferExprType(node.target);
        TypeNode sourceType = inferExprType(node.source);

        ensureArrayType(targetType,
                "memcpy() target must be an array, got " + typeToString(targetType));
        ensureArrayType(sourceType,
                "memcpy() source must be an array, got " + typeToString(sourceType));

        if (!sameType(targetType, sourceType)) {
            error("memcpy() requires identical source/target array types, got "
                    + typeToString(targetType) + " and " + typeToString(sourceType));
        }
    }

    private void visitDelete(DelNode node) {
        Symbol sym = resolve(node.name);
        if (sym == null) {
            error("Cannot delete undeclared variable '" + node.name + "'");
            return;
        }
        if (!sym.isHeap) {
            error("Cannot delete non-heap variable '" + node.name + "'");
        }
        if (sym.deleted) {
            error("Variable '" + node.name + "' has already been deleted");
        }
        sym.deleted = true;
    }

    private void visitPrint(PrintNode node) {
        for (ASTNode arg : node.args) {
            inferExprType(arg);
        }
    }

    // ============================================================
    // Expression typing
    // ============================================================

    private TypeNode inferExprType(ASTNode node) {
        if (node == null) {
            error("Internal error: null expression encountered");
            return INT32;
        }

        if (node instanceof IntNode) {
            return INT32;
        }

        if (node instanceof VarNode) {
            VarNode n = (VarNode) node;
            Symbol sym = resolve(n.name);
            if (sym == null) {
                error("Use of undeclared variable '" + n.name + "'");
                return INT32;
            }
            if (sym.deleted) {
                error("Use of deleted variable '" + n.name + "'");
            }
            return sym.type;
        }

        if (node instanceof UnaryOpNode) {
            UnaryOpNode n = (UnaryOpNode) node;
            TypeNode exprType = inferExprType(n.expr);
            if (!"-".equals(n.op)) {
                error("Unsupported unary operator '" + n.op + "'");
                return exprType;
            }
            ensureNumeric(exprType,
                    "Unary '-' requires a numeric scalar operand, got " + typeToString(exprType));
            return numericPromotion(exprType, null);
        }

        if (node instanceof BinOpNode) {
            BinOpNode n = (BinOpNode) node;
            TypeNode left = inferExprType(n.left);
            TypeNode right = inferExprType(n.right);
            String op = n.op;

            if ("+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op)) {
                ensureNumeric(left,
                        "Left operand of '" + op + "' must be numeric scalar, got " + typeToString(left));
                ensureNumeric(right,
                        "Right operand of '" + op + "' must be numeric scalar, got " + typeToString(right));
                return numericPromotion(left, right);
            }

            if ("==".equals(op) || "!=".equals(op) || "<".equals(op) || ">".equals(op)
                    || "<=".equals(op) || ">=".equals(op)) {
                if (isArrayType(left) || isArrayType(right)) {
                    error("Comparison operator '" + op + "' does not support array operands: "
                            + typeToString(left) + " and " + typeToString(right));
                } else if (!areComparable(left, right)) {
                    error("Operands of comparison '" + op + "' are not comparable: "
                            + typeToString(left) + " and " + typeToString(right));
                }
                // No bool type in the AST yet; comparisons are treated as int32-like scalar conditions.
                return INT32;
            }

            error("Unsupported binary operator '" + op + "'");
            return left;
        }

        if (node instanceof ArrayAccessNode) {
            ArrayAccessNode n = (ArrayAccessNode) node;
            TypeNode targetType = inferExprType(n.target);
            TypeNode indexType = inferExprType(n.index);

            ensureArrayType(targetType,
                    "Indexed expression must be an array, got " + typeToString(targetType));
            ensureIntegral(indexType,
                    "Array index must be an integral scalar, got " + typeToString(indexType));

            return elementTypeOf(targetType);
        }

        // Allow statement-like nodes to be analyzed when they appear unexpectedly.
        if (node instanceof ProgramNode || node instanceof BlockNode || node instanceof AssignNode
                || node instanceof IfNode || node instanceof WhileNode || node instanceof ArrayInitNode
                || node instanceof ArrayUninitNode || node instanceof ArrayMemsetNode
                || node instanceof ArrayMemcpyNode || node instanceof DelNode
                || node instanceof PrintNode || node instanceof VarDeclNode
                || node instanceof VarDeclInitNode || node instanceof VarDeclArrayInitNode) {
            visitStatement(node);
            return INT32;
        }

        error("Unsupported AST node in expression context: " + node.getClass().getSimpleName());
        return INT32;
    }

    private TypeNode inferLValueType(ASTNode node) {
        if (node instanceof VarNode || node instanceof ArrayAccessNode) {
            return inferExprType(node);
        }
        error("Left-hand side is not assignable: " + node.getClass().getSimpleName());
        return INT32;
    }

    // ============================================================
    // Scope / symbol helpers
    // ============================================================

    private void pushScope() {
        scopes.push(new LinkedHashMap<>());
    }

    private void popScope() {
        scopes.pop();
    }

    private void declare(String name, TypeNode type, boolean isHeap) {
        Map<String, Symbol> current = scopes.peek();
        if (current.containsKey(name)) {
            error("Duplicate declaration of variable '" + name + "' in the same scope");
            return;
        }
        current.put(name, new Symbol(type, isHeap));
    }

    private Symbol resolve(String name) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol sym = scope.get(name);
            if (sym != null) {
                return sym;
            }
        }
        return null;
    }

    // ============================================================
    // Type helpers
    // ============================================================

    private boolean sameType(TypeNode a, TypeNode b) {
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;

        if (a instanceof PrimitiveTypeNode) {
            PrimitiveTypeNode pa = (PrimitiveTypeNode) a;
            PrimitiveTypeNode pb = (PrimitiveTypeNode) b;
            return pa.kind == pb.kind;
        }

        if (a instanceof DynamicArrayTypeNode) {
            DynamicArrayTypeNode da = (DynamicArrayTypeNode) a;
            DynamicArrayTypeNode db = (DynamicArrayTypeNode) b;
            return sameType(da.elementType, db.elementType);
        }

        if (a instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode sa = (StaticArrayTypeNode) a;
            StaticArrayTypeNode sb = (StaticArrayTypeNode) b;
            return sa.size == sb.size && sameType(sa.elementType, sb.elementType);
        }

        return false;
    }

    private boolean isPrimitive(TypeNode t) {
        return t instanceof PrimitiveTypeNode;
    }

    private boolean isNumeric(TypeNode t) {
        if (!(t instanceof PrimitiveTypeNode)) return false;
        PrimitiveTypeNode p = (PrimitiveTypeNode) t;
        return p.kind == PrimitiveKind.INT32
                || p.kind == PrimitiveKind.UINT32
                || p.kind == PrimitiveKind.FLOAT32
                || p.kind == PrimitiveKind.CHAR8;
    }

    private boolean isIntegral(TypeNode t) {
        if (!(t instanceof PrimitiveTypeNode)) return false;
        PrimitiveTypeNode p = (PrimitiveTypeNode) t;
        return p.kind == PrimitiveKind.INT32
                || p.kind == PrimitiveKind.UINT32
                || p.kind == PrimitiveKind.CHAR8;
    }

    private boolean isArrayType(TypeNode t) {
        return t instanceof DynamicArrayTypeNode || t instanceof StaticArrayTypeNode;
    }

    private boolean isMemsetable(TypeNode t) {
        if (t == null) return false;

        // Primitive scalars are memsetable
        if (t instanceof PrimitiveTypeNode) {
            return true;
        }

        // Dynamic arrays are NOT memsetable
        if (t instanceof DynamicArrayTypeNode) {
            return false;
        }

        // Static arrays are memsetable iff their element type is memsetable
        if (t instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode s = (StaticArrayTypeNode) t;
            return isMemsetable(s.elementType);
        }

        // Future-proofing for user-defined structs:
        // if (t instanceof StructTypeNode) {
        //     StructTypeNode s = (StructTypeNode) t;
        //     for (FieldDeclNode field : s.fields) {
        //         if (!isMemsetable(field.type)) {
        //             return false;
        //         }
        //     }
        //     return true;
        // }

        return false;
    }

    private void ensureArrayType(TypeNode t, String message) {
        if (!isArrayType(t)) {
            error(message);
        }
    }

    private void ensureDynamicArrayType(TypeNode t, String message) {
        if (!(t instanceof DynamicArrayTypeNode)) {
            error(message + ", got " + typeToString(t));
        }
    }

    private void ensureNumeric(TypeNode t, String message) {
        if (!isNumeric(t)) {
            error(message);
        }
    }

    private void ensureIntegral(TypeNode t, String message) {
        if (!isIntegral(t)) {
            error(message);
        }
    }

    private void ensureConditionType(TypeNode t, String where) {
        if (isArrayType(t) || !isPrimitive(t)) {
            error("" + where + " must be a scalar primitive expression, got " + typeToString(t));
        }
    }

    /**
     * Assignment compatibility.
     *
     * Current policy:
     *  - exact type matches are allowed
     *  - numeric primitive widening/convenience is allowed
     *      * int literal (typed internally as int32) may initialize/assign any primitive numeric type
     *      * primitive numeric to primitive numeric is allowed
     *  - arrays require exact structural match
     */
    private void ensureAssignable(TypeNode target, TypeNode value, ASTNode valueNode, String messageOnFailure) {
        if (target == null || value == null) {
            error(messageOnFailure);
            return;
        }

        if (sameType(target, value)) {
            return;
        }

        if (isArrayType(target) || isArrayType(value)) {
            error(messageOnFailure);
            return;
        }

        if (isPrimitive(target) && isPrimitive(value)) {
            // Flexible scalar assignment policy for this language stage.
            return;
        }

        error(messageOnFailure);
    }

    private boolean areComparable(TypeNode a, TypeNode b) {
        if (sameType(a, b)) return true;
        return isPrimitive(a) && isPrimitive(b);
    }

    private TypeNode numericPromotion(TypeNode a, TypeNode b) {
        PrimitiveKind ka = kindOf(a);
        PrimitiveKind kb = kindOf(b);

        if (ka == PrimitiveKind.FLOAT32 || kb == PrimitiveKind.FLOAT32) {
            return new PrimitiveTypeNode(PrimitiveKind.FLOAT32);
        }
        if (ka == PrimitiveKind.UINT32 || kb == PrimitiveKind.UINT32) {
            return new PrimitiveTypeNode(PrimitiveKind.UINT32);
        }
        return new PrimitiveTypeNode(PrimitiveKind.INT32);
    }

    private PrimitiveKind kindOf(TypeNode t) {
        if (!(t instanceof PrimitiveTypeNode)) {
            return PrimitiveKind.INT32;
        }
        return ((PrimitiveTypeNode) t).kind;
    }

    private TypeNode elementTypeOf(TypeNode t) {
        if (t instanceof DynamicArrayTypeNode) {
            return ((DynamicArrayTypeNode) t).elementType;
        }
        if (t instanceof StaticArrayTypeNode) {
            return ((StaticArrayTypeNode) t).elementType;
        }
        error("Attempted to fetch element type of non-array type " + typeToString(t));
        return INT32;
    }

    private String typeToString(TypeNode t) {
        if (t == null) return "<null>";

        if (t instanceof PrimitiveTypeNode) {
            PrimitiveKind k = ((PrimitiveTypeNode) t).kind;
            return switch (k) {
                case INT32 -> "int32_t";
                case UINT32 -> "uint32_t";
                case FLOAT32 -> "float32_t";
                case CHAR8 -> "char8_t";
            };
        }

        if (t instanceof DynamicArrayTypeNode) {
            return "array<" + typeToString(((DynamicArrayTypeNode) t).elementType) + ">";
        }

        if (t instanceof StaticArrayTypeNode) {
            StaticArrayTypeNode s = (StaticArrayTypeNode) t;
            return "array<" + typeToString(s.elementType) + ", " + s.size + ">";
        }

        return t.getClass().getSimpleName();
    }

    // ============================================================
    // Error handling
    // ============================================================

    private void error(String msg) {
        errors.add(msg);
    }

    private String formatErrors() {
        StringBuilder sb = new StringBuilder();
        sb.append("Semantic analysis failed with ")
          .append(errors.size())
          .append(" error(s):\n");
        for (int i = 0; i < errors.size(); i++) {
            sb.append("  ")
              .append(i + 1)
              .append(". ")
              .append(errors.get(i))
              .append('\n');
        }
        return sb.toString();
    }
}
