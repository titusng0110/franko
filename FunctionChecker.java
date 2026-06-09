import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * ============================================================================
 * FUNCTION CHECKER
 * ============================================================================
 *
 * PURPOSE:
 * FunctionChecker validates function-level Franko legality rules over lowered
 * SemanticFunctionDeclNode objects.
 *
 * The SemanticAnalyzer has already:
 *
 *   - registered function signatures before body analysis,
 *   - resolved function declarations to FunctionSymbol objects,
 *   - resolved parameters to ParameterSymbol / VariableSymbol objects,
 *   - resolved return statements to their owning FunctionSymbol,
 *   - resolved function calls to selected FunctionSymbol overloads,
 *   - lowered the function body into SemanticStmtNode objects.
 *
 * FunctionChecker performs stricter function legality checks:
 *
 *   - function return type validity,
 *   - parameter declaration validity,
 *   - duplicate parameter name validation,
 *   - function body statement checking,
 *   - return; / return expr; legality,
 *   - return expression compatibility,
 *   - missing return in non-void functions.
 *
 * ----------------------------------------------------------------------------
 * RETURN RULES
 * ----------------------------------------------------------------------------
 *
 *   void function:
 *
 *      - does not require a return statement,
 *      - may use bare return;,
 *      - must not use return expr;.
 *
 *   non-void function:
 *
 *      - must contain at least one return statement,
 *      - must not use bare return;,
 *      - every return must return an expression,
 *      - returned expression must be compatible with declared return type.
 *
 * Return compatibility follows ordinary assignment compatibility:
 *
 *   - constants may return into primitive integer types if they fit,
 *   - nonconstant primitive expressions must exactly match,
 *   - address expressions must exactly match,
 *   - arrays cannot be returned directly.
 *
 * ----------------------------------------------------------------------------
 * RETURN PATH NOTE
 * ----------------------------------------------------------------------------
 *
 * This checker performs presence-based missing-return detection:
 *
 *      func f() -> int32_t { }
 *
 * is rejected because it contains no return.
 *
 * It does not perform full control-flow/path-sensitive proof such as:
 *
 *      func f(uint8_t x) -> int32_t {
 *          if (x) {
 *              return 1;
 *          }
 *      }
 *
 * Full return-path legality can be added later as a dedicated control-flow
 * checker if desired.
 */
public class FunctionChecker {
    private final DiagnosticBag diagnostics;
    private final DeclarationChecker declarations;
    private final StatementChecker statements;
    private final ExpressionChecker expressions;
    private final TypeChecker types;

    public FunctionChecker(
            DiagnosticBag diagnostics,
            DeclarationChecker declarations,
            StatementChecker statements,
            ExpressionChecker expressions,
            TypeChecker types
    ) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
        this.declarations = Objects.requireNonNull(declarations);
        this.statements = Objects.requireNonNull(statements);
        this.expressions = Objects.requireNonNull(expressions);
        this.types = Objects.requireNonNull(types);
    }

    public void checkFunction(SemanticFunctionDeclNode node) {
        if (node == null) {
            diagnostics.error("Function declaration node cannot be null");
            return;
        }

        if (node.symbol == null) {
            diagnostics.error("Function declaration has null symbol");
            return;
        }

        validateReturnType(node.symbol);
        validateParameters(node);

        boolean sawReturn = false;

        if (node.body == null) {
            diagnostics.error("Function '" + node.symbol.name + "' has null body");
        } else {
            sawReturn = checkFunctionBody(node);
        }

        if (!types.isVoidType(node.symbol.returnType()) && !sawReturn) {
            diagnostics.error("Non-void function '"
                    + node.symbol.fullSignatureString()
                    + "' must contain a return statement with an expression");
        }
    }

    // ============================================================
    // Function-Level Validation
    // ============================================================

    private void validateParameters(SemanticFunctionDeclNode node) {
        Set<String> names = new HashSet<>();

        for (VariableSymbol param : node.parameterVariables) {
            if (param == null) {
                diagnostics.error("Function '"
                        + node.symbol.name
                        + "' has null parameter symbol");
                continue;
            }

            if (!names.add(param.name)) {
                diagnostics.error("Duplicate parameter name '"
                        + param.name
                        + "' in function '"
                        + node.symbol.name
                        + "'");
            }

            /*
             * Parameters are ordinary local variables inside the function body.
             * Therefore they use ordinary variable declaration legality.
             *
             * This rejects void parameters defensively if they ever appear in
             * the Semantic AST.
             */
            declarations.checkVarDecl(new SemanticVarDeclNode(param));
        }
    }

    private void validateReturnType(FunctionSymbol fn) {
        SemanticType returnType = fn.returnType();

        types.ensureValidFunctionReturnType(
                returnType,
                "Function '" + fn.fullSignatureString() + "'"
        );

        if (returnType == null) {
            return;
        }

        /*
        * void is specially valid as a function return type.
        */
        if (types.isVoidType(returnType)) {
            return;
        }

        /*
        * Primitive integer return types are ordinary assignable values.
        */
        if (returnType instanceof SemanticPrimitiveType) {
            return;
        }

        /*
        * addr<T> is valid as a function return type, but T itself must be an
        * ordinary non-void type. Arrays are allowed behind addresses.
        *
        * Examples:
        *
        *   addr<int32_t>                  valid
        *   addr<array<int32_t>>           valid
        *   addr<array<uint8_t, 128>>      valid
        *   addr<void>                    invalid
        */
        if (returnType instanceof SemanticAddrType address) {
            validateAddressReferencedReturnType(
                    address.referencedType,
                    "Return type of function '" + fn.fullSignatureString() + "'"
            );
        }
    }

    /**
     * A function may return addr<T>, including:
     *
     *   addr<array<int32_t>>
     *   addr<array<uint8_t, 128>>
     *
     * But void is not an ordinary referenced type:
     *
     *   addr<void>      invalid
     *
     * Also validate nested array element types and static array sizes
     * defensively.
     */
    private void validateAddressReferencedReturnType(
            SemanticType referencedType,
            String where
    ) {
        validateOrdinaryType(
                referencedType,
                where + " address referenced type"
        );
    }

    /**
     * Validates ordinary non-void types used inside address return types.
     *
     * This intentionally allows arrays here because:
     *
     *   addr<array<T>>
     *   addr<array<T, N>>
     *
     * are recommended Franko patterns for array access/return.
     */
    private void validateOrdinaryType(
            SemanticType type,
            String where
    ) {
        if (type == null) {
            diagnostics.error(where + " is null");
            return;
        }

        if (type instanceof SemanticVoidType) {
            diagnostics.error(where + " cannot be void");
            return;
        }

        if (type instanceof SemanticPrimitiveType) {
            return;
        }

        if (type instanceof SemanticAddrType address) {
            validateOrdinaryType(
                    address.referencedType,
                    where + " nested address referenced type"
            );
            return;
        }

        if (type instanceof SemanticDynamicArrayType dynamicArray) {
            validateOrdinaryType(
                    dynamicArray.elementType,
                    where + " dynamic array element type"
            );
            return;
        }

        if (type instanceof SemanticStaticArrayType staticArray) {
            validateStaticArraySize(staticArray, where);

            validateOrdinaryType(
                    staticArray.elementType,
                    where + " static array element type"
            );
            return;
        }

        diagnostics.error(where + " has unsupported type "
                + types.describeSafe(type));
    }

    private void validateStaticArraySize(
            SemanticStaticArrayType staticArray,
            String where
    ) {
        String literal = staticArray.sizeLiteral;

        try {
            BigInteger size = types.parseIntegerLiteral(literal);

            if (size.signum() <= 0) {
                diagnostics.error(where
                        + " has invalid static array size "
                        + literal
                        + ": size must be greater than zero");
                return;
            }

            if (!types.fitsBigIntegerToPrimitive(
                    size,
                    SemanticPrimitiveKind.UINT32
            )) {
                diagnostics.error(where
                        + " has invalid static array size "
                        + literal
                        + ": size does not fit uint32_t");
            }
        } catch (Exception e) {
            diagnostics.error(where
                    + " has invalid static array size literal: "
                    + literal);
        }
    }

    // ============================================================
    // Body / Statement Traversal
    // ============================================================

    private boolean checkFunctionBody(SemanticFunctionDeclNode fn) {
        boolean sawReturn = false;

        for (SemanticStmtNode stmt : fn.body.statements) {
            if (checkStmtInsideFunction(stmt, fn.symbol)) {
                sawReturn = true;
            }
        }

        return sawReturn;
    }

    /**
     * Checks statements inside a function.
     *
     * Important:
     *
     *   StatementChecker currently does not handle SemanticReturnNode.
     *   Therefore FunctionChecker must intercept returns and recursively
     *   traverse compound statements that may contain returns.
     */
    private boolean checkStmtInsideFunction(
            SemanticStmtNode stmt,
            FunctionSymbol function
    ) {
        if (stmt == null) {
            return false;
        }

        if (stmt instanceof SemanticReturnNode n) {
            visitReturn(n, function);
            return true;
        }

        if (stmt instanceof SemanticBlockNode n) {
            return visitBlock(n, function);
        }

        if (stmt instanceof SemanticIfNode n) {
            return visitIf(n, function);
        }

        if (stmt instanceof SemanticWhileNode n) {
            return visitWhile(n, function);
        }

        /*
         * All other statements can be delegated to StatementChecker because
         * they cannot directly contain nested statements/returns.
         */
        statements.checkStmt(stmt);
        return false;
    }

    private boolean visitBlock(
            SemanticBlockNode node,
            FunctionSymbol function
    ) {
        boolean sawReturn = false;

        for (SemanticStmtNode stmt : node.statements) {
            if (checkStmtInsideFunction(stmt, function)) {
                sawReturn = true;
            }
        }

        return sawReturn;
    }

    private boolean visitIf(
            SemanticIfNode node,
            FunctionSymbol function
    ) {
        expressions.checkExpr(node.condition);
        
        if (types.isVoidType(node.condition.type)) {
            diagnostics.error("if condition cannot be void");
            return false;
        }

        types.ensureIntegral(
                node.condition.type,
                "if condition must be an integer"
        );

        boolean thenReturns = checkStmtInsideFunction(
                node.thenBranch,
                function
        );

        boolean elseReturns = false;

        if (node.elseBranch != null) {
            elseReturns = checkStmtInsideFunction(
                    node.elseBranch,
                    function
            );
        }

        /*
         * Presence-based return detection, not path-sensitive proof.
         */
        return thenReturns || elseReturns;
    }

    private boolean visitWhile(
            SemanticWhileNode node,
            FunctionSymbol function
    ) {
        expressions.checkExpr(node.condition);

        if (types.isVoidType(node.condition.type)) {
            diagnostics.error("while condition cannot be void");
            return false;
        }

        types.ensureIntegral(
                node.condition.type,
                "while condition must be an integer"
        );

        /*
         * Presence-based only. A return inside a loop counts as a return
         * appearing in the function body, but this does not prove all paths
         * return.
         */
        return checkStmtInsideFunction(node.body, function);
    }

    // ============================================================
    // Return Checking
    // ============================================================

    private void visitReturn(
            SemanticReturnNode node,
            FunctionSymbol enclosingFunction
    ) {
        if (node.function == null) {
            diagnostics.error("Return statement has null owning function");
            return;
        }

        if (node.function != enclosingFunction) {
            diagnostics.error("Return statement is attached to function '"
                    + node.function.fullSignatureString()
                    + "' but appears while checking function '"
                    + enclosingFunction.fullSignatureString()
                    + "'");
        }

        SemanticType declaredReturnType = enclosingFunction.returnType();

        if (types.isVoidType(declaredReturnType)) {
            visitVoidFunctionReturn(node, enclosingFunction);
            return;
        }

        visitNonVoidFunctionReturn(node, enclosingFunction);
    }

    private void visitVoidFunctionReturn(
            SemanticReturnNode node,
            FunctionSymbol function
    ) {
        if (node.value == null) {
            /*
             * return; is valid in void functions.
             */
            return;
        }

        /*
         * Still check the expression so nested illegal constructs are reported.
         */
        expressions.checkExpr(node.value);

        diagnostics.error("Void function '"
                + function.fullSignatureString()
                + "' must not return a value");
    }

    private void visitNonVoidFunctionReturn(
            SemanticReturnNode node,
            FunctionSymbol function
    ) {
        if (node.value == null) {
            diagnostics.error("Non-void function '"
                    + function.fullSignatureString()
                    + "' must return an expression");
            return;
        }

        expressions.checkExpr(node.value);

        if (types.isVoidType(node.value.type)) {
            diagnostics.error("Non-void function '"
                    + function.fullSignatureString()
                    + "' cannot return a void expression");
            return;
        }

        types.ensureAssignable(
                function.returnType(),
                node.value,
                "Invalid return from function '" + function.fullSignatureString() + "'"
        );
    }
}