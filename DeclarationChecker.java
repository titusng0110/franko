import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DeclarationChecker {
    private final DiagnosticBag diagnostics;
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

        if (node.staticInitializer != null) {
            validateGlobalStaticInitializer(node);
        }
    }

    private void validateGlobalStaticInitializer(SemanticVarDeclNode node) {
        SemanticType type = node.symbol.type;

        if (!(type instanceof SemanticPtrType)) {
            diagnostics.error("Variable '"
                + node.symbol.qualifiedName()
                + "' has a static address initializer, but only pointer "
                + "globals can be initialized with getptr/getmview/getview");
            return;
        }

        expressions.checkExpr(node.staticInitializer);

        if (node.staticInitializer.type == null) {
            return;
        }

        if (!types.sameType(type, node.staticInitializer.type)
                && !types.isPointerConversion(
                        node.staticInitializer.type,
                        type)) {
            diagnostics.error("Variable '"
                + node.symbol.qualifiedName()
                + "' initializer has type "
                + types.describeSafe(node.staticInitializer.type)
                + ", expected "
                + types.describeSafe(type));
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

            checkDeclaredType(
                pointer.referencedType,
                where + " pointer referenced type",
                structWalkStates,
                new HashSet<>()
            );
            return;
        }

        diagnostics.error(where + " has unsupported declared type: "
            + types.describeSafe(type));
    }

    private enum StructWalkState {
        NOT_VISITED,
        IN_PROGRESS,
        DONE
    }

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
            + "' has a constant initializer, but only primitive globals "
            + "can have constant initializers (pointer globals accept "
            + "null, getptr/getmview/getview over a global, or a string "
            + "literal)");
    }

    private void validateStaticArraySize(
        SemanticStaticArrayType staticArray,
        String where
    ) {

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
