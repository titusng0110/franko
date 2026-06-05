import java.math.BigInteger;
import java.util.Objects;

/**
 * ============================================================================
 * DECLARATION CHECKER
 * ============================================================================
 *
 * PURPOSE:
 * DeclarationChecker validates declaration-level legality rules over lowered
 * Semantic AST declaration nodes.
 *
 * The SemanticAnalyzer has already:
 *
 *   - resolved declaration names into VariableSymbol objects,
 *   - converted parser TypeNode objects into SemanticType objects,
 *   - preserved static array size literals as source-spelled strings.
 *
 * DeclarationChecker is responsible for checking whether declared types are
 * legal Franko variable types.
 *
 * Current declaration-level rules:
 *
 *   - primitive integer types are valid,
 *   - dynamic array types are valid if their element type is valid,
 *   - static array types are valid if:
 *       * their element type is valid,
 *       * their size literal is a valid integer literal,
 *       * their size is greater than zero,
 *       * their size fits uint32_t,
 *   - address types are valid if their referenced type is valid.
 *
 * Notes:
 *
 *   Static array size zero is rejected because the generated C++ backend uses:
 *
 *       T data[N];
 *
 *   and zero-length arrays are not standard C++.
 *
 * ============================================================================
 */
public class DeclarationChecker {
    private final SemanticAnalyzer.Context ctx;
    @SuppressWarnings("unused")
    private final ExpressionChecker expressions;
    private final TypeChecker types;

    public DeclarationChecker(
        SemanticAnalyzer.Context ctx,
        ExpressionChecker expressions,
        TypeChecker types
    ) {
        this.ctx = Objects.requireNonNull(ctx);
        this.expressions = Objects.requireNonNull(expressions);
        this.types = Objects.requireNonNull(types);
    }

    public void checkVarDecl(SemanticVarDeclNode node) {
        if (node == null) {
            ctx.error("Variable declaration node cannot be null");
            return;
        }

        if (node.symbol == null) {
            ctx.error("Variable declaration has null symbol");
            return;
        }

        validateDeclaredType(
            node.symbol.type,
            "Variable '" + node.symbol.name + "'"
        );
    }

    private void validateDeclaredType(
        SemanticType type,
        String where
    ) {
        if (type == null) {
            ctx.error(where + " has null declared type");
            return;
        }

        if (type instanceof SemanticPrimitiveType) {
            return;
        }

        if (type instanceof SemanticDynamicArrayType dynamicArray) {
            validateDeclaredType(
                dynamicArray.elementType,
                where + " dynamic array element"
            );
            return;
        }

        if (type instanceof SemanticStaticArrayType staticArray) {
            validateStaticArraySize(staticArray, where);

            validateDeclaredType(
                staticArray.elementType,
                where + " static array element"
            );
            return;
        }

        if (type instanceof SemanticAddrType address) {
            validateDeclaredType(
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
        ctx.error(where + " has unsupported declared type: "
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
                ctx.error(where + " has invalid static array size "
                    + literal
                    + ": size must be greater than zero");
                return;
            }

            if (!types.fitsBigIntegerToPrimitive(
                size,
                SemanticPrimitiveKind.UINT32
            )) {
                ctx.error(where + " has invalid static array size "
                    + literal
                    + ": size does not fit uint32_t");
            }
        } catch (Exception e) {
            ctx.error(where + " has invalid static array size literal: "
                + literal);
        }
    }
}