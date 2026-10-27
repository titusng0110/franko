import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
 *   - pointer types are valid if their referenced type is valid.
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
            "Variable '" + node.symbol.qualifiedName() + "'"
        );

        if (node.foldedConstInitializer != null) {
            validateGlobalConstInitializer(node);
        }
    }

    public void checkStructDecl(SemanticStructDeclNode node) {
        if (node == null || node.symbol == null) {
            diagnostics.error("Struct declaration node cannot be null");
            return;
        }

        checkDeclaredType(
            new SemanticStructType(node.symbol),
            "Struct '" + node.symbol.qualifiedName() + "'"
        );

        for (SemanticFunctionDeclNode methodNode : node.methods) {
            if (methodNode == null || methodNode.symbol == null) {
                continue;
            }

            if (!(methodNode.symbol instanceof MethodSymbol method)) {
                diagnostics.error("Struct '"
                        + node.symbol.qualifiedName()
                        + "' has a non-method function '"
                        + methodNode.symbol.name
                        + "'");
                continue;
            }

            if (node.symbol.resolveField(method.name) != null) {
                diagnostics.error("Method '"
                        + method.qualifiedName()
                        + "' conflicts with field '"
                        + method.name
                        + "' of struct '"
                        + node.symbol.qualifiedName()
                        + "'");
            }
        }
    }

    public void checkDeclaredType(
        SemanticType type,
        String where
    ) {
        checkDeclaredType(type, where, new HashMap<>(), new HashSet<>());
    }

    private void checkDeclaredType(
        SemanticType type,
        String where,
        Map<String, StructWalkState> structWalkStates,
        Set<String> byValuePath
    ) {
        if (type == null) {
            diagnostics.error(where + " has null declared type");
            return;
        }

        if (type instanceof SemanticPrimitiveType) {
            return;
        }

        if (type instanceof SemanticDynamicArrayType dynamicArray) {
            /*
             * Dynamic array elements live behind a pointer, so struct cycles
             * through dynamic arrays are allowed: the by-value path is
             * intentionally reset here.
             */
            checkDeclaredType(
                dynamicArray.elementType,
                where + " dynamic array element",
                structWalkStates,
                new HashSet<>()
            );
            return;
        }

        if (type instanceof SemanticStaticArrayType staticArray) {
            validateStaticArraySize(staticArray, where);

            checkDeclaredType(
                staticArray.elementType,
                where + " static array element",
                structWalkStates,
                byValuePath
            );
            return;
        }

        if (type instanceof SemanticStructType struct) {
            validateStructType(
                struct,
                where,
                structWalkStates,
                byValuePath
            );
            return;
        }

        if (type instanceof SemanticPtrType pointer) {
            /*
             * ptr<T> is a pointer indirection, so struct cycles through
             * pointers are allowed: the by-value path is intentionally
             * reset here.
             */
            checkDeclaredType(
                pointer.referencedType,
                where + " pointer referenced type",
                structWalkStates,
                new HashSet<>()
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

    // ============================================================
    // Struct Declaration Validation
    // ============================================================

    /*
     * Per-walk validation states for struct declarations.
     *
     *   - NOT_VISITED: struct has not been reached in this walk,
     *   - IN_PROGRESS: struct fields are being validated. Re-entry via a
     *     by-value edge (struct field or static array element) means an
     *     infinite-size cycle; re-entry via an indirection edge (dynamic
     *     array element or ptr referenced type) is harmless because the
     *     in-progress frame is already validating this struct,
     *   - DONE: struct fully validated within this walk.
     */
    private enum StructWalkState {
        NOT_VISITED,
        IN_PROGRESS,
        DONE
    }

    /**
     * Validates a struct declaration referenced as a declared type.
     *
     * Rules:
     *
     *   - field names must be unique within the struct,
     *   - field types must be valid declared types (void is rejected),
     *   - a struct must not reach itself by value through struct fields or
     *     static array elements; ptr and dynamic array fields break the
     *     cycle and are allowed,
     *   - a field initializer, when present, must fit a primitive field
     *     type; struct, array, and ptr fields reject initializers as
     *     type mismatches.
     */
    private void validateStructType(
        SemanticStructType struct,
        String where,
        Map<String, StructWalkState> structWalkStates,
        Set<String> byValuePath
    ) {
        if (struct.structSymbol == null) {
            diagnostics.error(where + " has null struct symbol");
            return;
        }

        /*
         * Keyed by qualified name: different namespaces may declare
         * structs with the same simple name, and they are distinct
         * types with independent field graphs.
         */
        String structName = struct.structSymbol.qualifiedName();

        StructWalkState state = structWalkStates.getOrDefault(
            structName,
            StructWalkState.NOT_VISITED
        );

        if (state == StructWalkState.DONE) {
            return;
        }

        if (state == StructWalkState.IN_PROGRESS) {
            if (byValuePath.contains(structName)) {
                diagnostics.error(where + " creates a by-value struct cycle: "
                    + "struct '"
                    + structName
                    + "' cannot contain itself through its fields");
            }

            return;
        }

        structWalkStates.put(structName, StructWalkState.IN_PROGRESS);

        Set<String> nextByValuePath = new HashSet<>(byValuePath);
        nextByValuePath.add(structName);

        Set<String> seenFieldNames = new HashSet<>();

        for (FieldSymbol field : struct.structSymbol.fields) {
            if (field == null) {
                diagnostics.error("Struct '"
                    + structName
                    + "' contains a null field");
                continue;
            }

            String fieldWhere = where + " struct '"
                + structName
                + "' field '"
                + field.name
                + "'";

            if (!seenFieldNames.add(field.name)) {
                diagnostics.error(fieldWhere
                    + " duplicates an earlier field name");
                continue;
            }

            if (types.isVoidType(field.type)) {
                diagnostics.error(fieldWhere + " cannot have void type");
                continue;
            }

            checkDeclaredType(
                field.type,
                fieldWhere,
                structWalkStates,
                nextByValuePath
            );

            if (field.foldedDefault != null) {
                validateFieldDefault(field, fieldWhere);
            }
        }

        structWalkStates.put(structName, StructWalkState.DONE);
    }

    /**
     * Validates an optional struct field initializer.
     *
     * A field default must fit a primitive field type. Struct, array, and
     * ptr fields reject initializers as type mismatches.
     */
    private void validateFieldDefault(
        FieldSymbol field,
        String where
    ) {
        if (field.type instanceof SemanticPrimitiveType primitive) {
            if (!TypeChecker.fitsBigIntegerToPrimitive(
                    field.foldedDefault,
                    primitive.kind
            )) {
                diagnostics.error(where + " default value "
                    + field.foldedDefault
                    + " does not fit in "
                    + field.type.describe());
            }

            return;
        }

        diagnostics.error(where
            + " has an initializer, but only primitive fields can have default values");
    }

    /**
     * Validates a folded global const initializer against the declared type.
     *
     * In practice only primitive globals can have initializers; struct,
     * array, and ptr globals reject initializers as type mismatches.
     */
    private void validateGlobalConstInitializer(SemanticVarDeclNode node) {
        if (node.symbol.type instanceof SemanticPrimitiveType primitive) {
            if (!TypeChecker.fitsBigIntegerToPrimitive(
                    node.foldedConstInitializer,
                    primitive.kind
            )) {
                diagnostics.error("Variable '"
                    + node.symbol.qualifiedName()
                    + "' initializer value "
                    + node.foldedConstInitializer
                    + " does not fit in "
                    + node.symbol.type.describe());
            }

            return;
        }

        diagnostics.error("Variable '"
            + node.symbol.qualifiedName()
            + "' has an initializer, but only primitive globals can have initializers");
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

            if (!TypeChecker.fitsBigIntegerToPrimitive(
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