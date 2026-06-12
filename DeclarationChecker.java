import java.math.BigInteger;
import java.util.Objects;

/**
 * ============================================================================
 * DECLARATION CHECKER
 * ========================================================================= * * ============================================================================
 * Current declaration-level rules:
 *
 *   - primitive integer types are valid,
 *
 *   - dynamic array types are valid if their element type is valid,
 *
 *   - static array types are valid if:
 *       * their element type is valid,
 *       * their canonical size string is parseable as an integer,
 *       * their size is greater than zero,
 *       * their size fits uint32_t,
 *
 *   - address types are valid if their referenced type is valid.
 *
 * Notes:
 *
 *   Static array size expressions are mechanically folded before this checker.
 *   For example:
 *
 *       array<int, 1 + 2>
 *
 *   reaches this checker as a SemanticStaticArrayType with canonical size "3".
 *
 *   If folding fails, SemanticAnalyzer currently canonicalizes the size to
 *   "0" as fallback metadata. This checker will then reject it because static
 *   array size zero is invalid.
 *
 *   Static array size zero is rejected because the generated C++ backend uses:
 *
 *       T data[N];
 *
 *   and zero-length arrays are not standard C++.
 *
 * ============================================================================
 *
 *
 * PURPOSE:
 * DeclarationChecker validates declaration-level legality rules over lowered
 * Semantic AST declaration nodes.
 *
 * The SemanticAnalyzer has already:
 *
 *   - resolved declaration names into VariableSymbol objects,
 *   - converted parser TypeNode objects into SemanticType objects,
 *   - folded static array size expressions into canonical decimal size strings
 *     where possible.
 *
 * DeclarationChecker is responsible for checking whether declared variable
 * types are legal Franko variable types.
*/
public class DeclarationChecker {
    private final DiagnosticBag diagnostics;
    @SuppressWarnings("unused")
    private final ExpressionChecker expressions;
    private final TypeChecker types;

    public DeclarationChecker(
        DiagnosticBag diagnostics,
        ExpressionChecker expressions,
        TypeChecker types
    ) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
        this.expressions = Objects.requireNonNull(expressions);
        this.types = Objects.requireNonNull(types);
    }

    public void checkVarDecl(SemanticVarDeclNode node) {
        if (node == null) {
            diagnostics.error("Variable declaration node cannot be null");
            return;
        }

        if (node.symbol == null) {
            diagnostics.error("Variable declaration has null symbol");
            return;
        }

        checkDeclaredType(
            node.symbol.type,
            "Variable '" + node.symbol.name + "'"
        );
    }

    public void checkDeclaredType(
        SemanticType type,
        String where
    ) {
        if (type == null) {
            diagnostics.error(where + " has null declared type");
            return;
        }

        if (type instanceof SemanticPrimitiveType) {
            return;
        }

        if (type instanceof SemanticDynamicArrayType dynamicArray) {
            checkDeclaredType(
                dynamicArray.elementType,
                where + " dynamic array element"
            );
            return;
        }

        if (type instanceof SemanticStaticArrayType staticArray) {
            validateStaticArraySize(staticArray, where);

            checkDeclaredType(
                staticArray.elementType,
                where + " static array element"
            );
            return;
        }

        if (type instanceof SemanticAddrType address) {
            checkDeclaredType(
                address.referencedType,
                where + " address referenced type"
            );
            return;
        }

        /*
         * Function types exist preemptively in your semantic type model, but
         * the current parser does not expose function declarations/variables.
         * Treat unexpected declaration types as invalid for now.
         */
        diagnostics.error(where + " has unsupported declared type: "
            + types.describeSafe(type));
    }

    private void validateStaticArraySize(
        SemanticStaticArrayType staticArray,
        String where
    ) {
        
        /*
        * staticArray.sizeLiteral is a canonical folded decimal string produced by
        * SemanticAnalyzer, not necessarily the original source spelling.
        */

        String literal = staticArray.sizeLiteral;

        try {
            BigInteger size = types.parseIntegerLiteral(literal);

            if (size.signum() <= 0) {
                diagnostics.error(where + " has invalid static array size "
                    + literal
                    + ": size must be greater than zero");
                return;
            }

            if (!types.fitsBigIntegerToPrimitive(
                size,
                SemanticPrimitiveKind.UINT32
            )) {
                diagnostics.error(where + " has invalid static array size "
                    + literal
                    + ": size does not fit uint32_t");
            }
        } catch (Exception e) {
            diagnostics.error(where + " has invalid static array size literal: "
                + literal);
        }
    }
}