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

    public void checkVarDecl(SastNode node) {
        if (node == null) {
            diagnostics.error("Variable declaration node cannot be null");
            return;
        }

        if (node.symbol == null) {
            diagnostics.error("Variable declaration has null symbol");
            return;
        }

        if (node.symbol.isConst
                && !types.isValueAssignableType(node.symbol.type)) {
            diagnostics.error(constKindName(node.symbol)
                + " '"
                + node.symbol.qualifiedName()
                + "' has type '"
                + types.describeSafe(node.symbol.type)
                + "'; const is only allowed for value-assignable types."
                + " Const storage must hold a complete value that can"
                + " be initialized and copied, which arrays and"
                + " pointer-only aggregates cannot provide."
                + " Use a primitive, enum, pointer, or value-assignable"
                + " struct/tuple, or remove const.");
        }

        if (node.symbol.isConst
                && node.symbol.kind == SymbolKind.VARIABLE
                && node.kind == SastKind.VAR_DECL
                && !types.isFullyDefaultInitialized(node.symbol.type)) {
            diagnostics.warning(constKindName(node.symbol)
                + " '"
                + node.symbol.qualifiedName()
                + "' has no initializer and type '"
                + types.describeSafe(node.symbol.type)
                + "' is not fully default-initialized");
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

    private static String constKindName(Symbol symbol) {
        if (symbol.kind == SymbolKind.GLOBAL) {
            return "Const global";
        }

        if (symbol.kind == SymbolKind.PARAMETER) {
            return "Const parameter";
        }

        return "Const variable";
    }

    private void validateGlobalStaticInitializer(SastNode node) {
        SastType type = node.symbol.type;

        if (!(type.kind == SastTypeKind.PTR)) {
            diagnostics.error("Variable '"
                + node.symbol.qualifiedName()
                + "' has a static address initializer, but only pointer "
                + "globals can be initialized with getptr/getmview/getview."
                + " Those intrinsics produce an address, so the global"
                + " must be a pointer with a compatible referent type."
                + " Declare the global as ptr<T>, mview<T>, or view<T>,"
                + " or initialize the value another way.");
            return;
        }

        expressions.checkExpr(node.staticInitializer);

        if (node.staticInitializer.type == null) {
            return;
        }

        if (!TypeChecker.sameType(type, node.staticInitializer.type)
                && !PointerPermissions.isPointerConversion(
                        node.staticInitializer.type,
                        type)) {
            diagnostics.error("Variable '"
                + node.symbol.qualifiedName()
                + "' initializer has type "
                + types.describeSafe(node.staticInitializer.type)
                + ", expected "
                + types.describeSafe(type)
                + "."
                + " A static address initializer must denote an address"
                + " of the declared pointer type, allowing only"
                + " permission downgrades."
                + " Match the declared pointer type, or insert an"
                + " explicit getview/getmview conversion.");
        }
    }

    public void checkStructDecl(SastNode node) {
        if (node == null || node.structSymbol == null) {
            diagnostics.error("Struct declaration node cannot be null");
            return;
        }

        if (node.structSymbol.isTuple) {
            return;
        }

        checkDeclaredType(
            SastType.structType(node.structSymbol),
            "Struct '" + node.structSymbol.qualifiedName() + "'"
        );

        for (SastNode methodNode : node.methods) {
            if (methodNode == null || methodNode.symbol == null) {
                continue;
            }

            if (!(methodNode.symbol.kind == SymbolKind.METHOD)) {
                diagnostics.error("Struct '"
                        + node.structSymbol.qualifiedName()
                        + "' has a non-method function '"
                        + methodNode.symbol.name
                        + "'."
                        + " Functions declared inside a struct body must"
                        + " be methods that take self so they can attach"
                        + " to the struct."
                        + " Add a self parameter to make it a method, or"
                        + " move the function to global or namespace"
                        + " scope.");
                continue;
            }

            if (node.structSymbol.resolveField(methodNode.symbol.name) != null) {
                diagnostics.error("Method '"
                        + methodNode.symbol.qualifiedName()
                        + "' conflicts with field '"
                        + methodNode.symbol.name
                        + "' of struct '"
                        + node.structSymbol.qualifiedName()
                        + "'."
                        + " Fields and methods share member lookup syntax,"
                        + " so a call or access with that name would be"
                        + " ambiguous."
                        + " Rename the method or the field.");
            }
        }
    }

    public void checkDeclaredType(
        SastType type,
        String where
    ) {
        types.ensureNoOwnedPointerInAggregate(type, where);
        checkDeclaredType(type, where, new HashMap<>(), new HashSet<>());
    }

    private void checkDeclaredType(
        SastType type,
        String where,
        Map<String, StructWalkState> structWalkStates,
        Set<String> byValuePath
    ) {
        if (type == null) {
            diagnostics.error(where + " has null declared type");
            return;
        }

        if (type.kind == SastTypeKind.PRIMITIVE) {
            return;
        }

        if (type.kind == SastTypeKind.ENUM) {
            return;
        }

        if (type.kind == SastTypeKind.DYNAMIC_ARRAY) {

            checkDeclaredType(
                type.elementType,
                where + " dynamic array element",
                structWalkStates,
                new HashSet<>()
            );
            return;
        }

        if (type.kind == SastTypeKind.STATIC_ARRAY) {
            validateStaticArraySize(type, where);

            checkDeclaredType(
                type.elementType,
                where + " static array element",
                structWalkStates,
                byValuePath
            );
            return;
        }

        if (type.kind == SastTypeKind.STRUCT) {
            validateStructType(
                type,
                where,
                structWalkStates,
                byValuePath
            );
            return;
        }

        if (type.kind == SastTypeKind.TUPLE) {
            for (int i = 0; i < type.fields.size(); i++) {
                FieldSymbol field = type.fields.get(i);

                if (field == null) {
                    diagnostics.error(where
                            + " has a null tuple element at index " + i);
                    continue;
                }

                String elementWhere = where
                        + " tuple element '"
                        + field.name
                        + "'";

                if (types.isVoidType(field.type)) {
                    diagnostics.error(elementWhere + " cannot have void type."
                            + " Every tuple element must store a value, and"
                            + " void has no representation."
                            + " Give the element a concrete non-void type.");
                    continue;
                }

                checkDeclaredType(
                    field.type,
                    elementWhere,
                    structWalkStates,
                    byValuePath
                );
            }

            return;
        }

        if (type.kind == SastTypeKind.PTR) {

            checkDeclaredType(
                type.referencedType,
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
        SastType struct,
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
                    + "' cannot contain itself through its fields, tuple elements, or static array elements."
                    + " A by-value field needs the complete layout of its"
                    + " type, so a cycle would require infinite storage."
                    + " Break the cycle by storing the field through a"
                    + " ptr, mview, or view instead.");
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
                    + " duplicates an earlier field name."
                    + " Fields share one member lookup namespace, so"
                    + " duplicate names would make access ambiguous."
                    + " Rename or remove the duplicate field.");
                continue;
            }

            if (types.isVoidType(field.type)) {
                diagnostics.error(fieldWhere + " cannot have void type."
                    + " A struct field needs storage for a value, and void"
                    + " has no representation."
                    + " Give the field a concrete non-void type, or remove"
                    + " it.");
                continue;
            }

            types.ensureNoOwnedPointerAggregateMember(field.type, fieldWhere);

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
        if (field.type.kind == SastTypeKind.PRIMITIVE) {
            if (!TypeChecker.fitsBigIntegerToPrimitive(
                    field.foldedDefault,
                    field.type.primitive
            )) {
                diagnostics.error(where + " default value "
                    + field.foldedDefault
                    + " does not fit in "
                    + field.type.describe()
                    + "."
                    + " The default is stored in the field's fixed-width"
                    + " integer type, and an out-of-range value cannot"
                    + " be stored without truncation."
                    + " Choose a value within the type's range, or change"
                    + " the field type.");
            }

            return;
        }

        diagnostics.error(where
            + " has an initializer, but only primitive fields can have default values."
            + " Field defaults are folded into primitive constants,"
            + " and struct, array, and pointer values have no such"
            + " representation."
            + " Remove the default and initialize the field at each"
            + " declaration or with an aggregate literal.");
    }

    private void validateGlobalConstInitializer(SastNode node) {
        if (node.symbol.type.kind == SastTypeKind.PRIMITIVE) {
            if (!TypeChecker.fitsBigIntegerToPrimitive(
                    node.foldedConstInitializer,
                    node.symbol.type.primitive
            )) {
                diagnostics.error("Variable '"
                    + node.symbol.qualifiedName()
                    + "' initializer value "
                    + node.foldedConstInitializer
                    + " does not fit in "
                    + node.symbol.type.describe()
                    + "."
                    + " The initializer is stored in the global's"
                    + " fixed-width integer type, and an out-of-range"
                    + " value cannot be stored without truncation."
                    + " Choose a value within the type's range, or change"
                    + " the global's type.");
            }

            return;
        }

        diagnostics.error("Variable '"
            + node.symbol.qualifiedName()
            + "' has a constant initializer, but only primitive globals "
            + "can have constant initializers (pointer globals accept "
            + "null, getptr/getmview/getview over a global, or a string "
            + "literal)."
            + " Global constant initializers are folded into static"
            + " storage, which only primitive values can represent"
            + " directly."
            + " Use a primitive global, or use one of the accepted"
            + " pointer forms such as null, getptr/getmview/getview over"
            + " a global, or a string literal.");
    }

    private void validateStaticArraySize(
        SastType staticArray,
        String where
    ) {

        String literal = staticArray.sizeLiteral;

        try {
            BigInteger size = types.parseIntegerLiteral(literal);

            if (size.signum() <= 0) {
                diagnostics.error(where + " has invalid static array size "
                    + literal
                    + ": size must be greater than zero."
                    + " A static array's size determines its fixed"
                    + " storage layout, and a zero or negative length"
                    + " cannot allocate elements."
                    + " Use a positive integer constant for the size.");
                return;
            }

            if (!TypeChecker.fitsBigIntegerToPrimitive(
                size,
                SemanticPrimitiveKind.UINT32
            )) {
                diagnostics.error(where + " has invalid static array size "
                    + literal
                    + ": size does not fit uint32."
                    + " Franko stores static array lengths as uint32, so"
                    + " larger values cannot be represented."
                    + " Use a size no greater than 4294967295, or use a"
                    + " dynamic array instead.");
            }
        } catch (IllegalArgumentException e) {
            diagnostics.error(where + " has invalid static array size literal: "
                + literal
                + "."
                + " Static array sizes must be integer constants known"
                + " at compile time."
                + " Replace it with a literal or folded integer constant"
                + " expression.");
        }
    }
}
