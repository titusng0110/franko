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

        if (node.symbol.kind == SymbolKind.GLOBAL
                && node.symbol.literalBytes == null
                && node.symbol.literalValues == null
                && !types.isPassableByValueType(node.symbol.type)) {
            diagnostics.error("Global variable '"
                + node.symbol.qualifiedName()
                + "' has type '"
                + types.describeSafe(node.symbol.type)
                + "'; global variables are implicitly const and must be"
                + " passable by value. Global storage is initialized once"
                + " before main and never mutated, so it cannot hold an"
                + " array or an aggregate with pointer or array members. Use a primitive,"
                + " enum, pointer, or passable-by-value struct/tuple, or"
                + " move the storage into a function.");
        } else if (node.symbol.isConst
                && !types.isPassableByValueType(node.symbol.type)) {
            diagnostics.error(constKindName(node.symbol)
                + " '"
                + node.symbol.qualifiedName()
                + "' has type '"
                + types.describeSafe(node.symbol.type)
                + "'; const is only allowed for passable-by-value types."
                + " Const storage must hold a complete value that can"
                + " be initialized and copied, which arrays and"
                + " aggregates with pointer or array members cannot provide."
                + " Use a primitive, enum, pointer, or passable-by-value"
                + " struct/tuple, or remove const.");
        }

        if (node.symbol.kind == SymbolKind.GLOBAL
                && node.symbol.type != null
                && node.symbol.type.kind == SastTypeKind.PTR
                && node.symbol.type.permission != PtrPermission.READ_ONLY) {
            diagnostics.error("Global variable '"
                + node.symbol.qualifiedName()
                + "' has type '"
                + types.describeSafe(node.symbol.type)
                + "'; only read-only view<T> globals are allowed. Pointers are downward-only values, and a mutable or owning global would let any function write through shared storage. Use view<T> for a read-only global, or move mutable storage into a function.");
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
            if (node.symbol.type != null
                    && node.symbol.type.kind == SastTypeKind.PTR) {
                validateGlobalStaticInitializer(node);
            } else {
                validateGlobalValueInitializer(node);
            }
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

    private void validateGlobalValueInitializer(SastNode node) {
        SastType type = node.symbol.type;
        SastNode init = node.staticInitializer;

        expressions.checkExpr(init);

        if (type == null || init.type == null) {
            return;
        }

        if (type.kind == SastTypeKind.ENUM) {
            if (init.kind != SastKind.ENUM_VARIANT) {
                diagnostics.error("Global variable '"
                    + node.symbol.qualifiedName()
                    + "' has an enum type, but its initializer is not an enum"
                    + " variant. Enums are nominal and are only initialized"
                    + " from their own variant references. Use "
                    + type.describe()
                    + "::<variant>, or leave the initializer out to use the"
                    + " first variant.");
                return;
            }
        } else if (type.kind == SastTypeKind.STRUCT
                || type.kind == SastTypeKind.TUPLE) {
            if (init.kind != SastKind.AGGREGATE_LITERAL) {
                diagnostics.error("Global variable '"
                    + node.symbol.qualifiedName()
                    + "' has an aggregate type, but its initializer is not"
                    + " an aggregate literal. Global aggregates are"
                    + " initialized once before main from static values. Use "
                    + type.describe()
                    + " { ... } with constant, null, enum, string, array,"
                    + " view, or nested aggregate values.");
                return;
            }
        } else {
            diagnostics.error("Global variable '"
                + node.symbol.qualifiedName()
                + "' has a static initializer, but its type is not a"
                + " pointer, enum, struct, or tuple. Primitive globals use"
                + " a constant initializer instead. Replace the initializer"
                + " with a constant expression of the declared type.");
            return;
        }

        types.ensureAssignable(
                type,
                init,
                "Invalid global initializer"
        );
    }

    private void validateGlobalStaticInitializer(SastNode node) {
        SastType type = node.symbol.type;

        if (!(type.kind == SastTypeKind.PTR)) {
            diagnostics.error("Variable '"
                + node.symbol.qualifiedName()
                + "' has a static address initializer, but only pointer "
                + "globals can be initialized with ptr/mview/view."
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
                + " explicit view/mview conversion.");
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
        checkDeclaredType(type, where, new HashMap<>(), new HashSet<>(), true);
    }

    private void checkDeclaredType(
        SastType type,
        String where,
        Map<String, StructWalkState> structWalkStates,
        Set<String> byValuePath,
        boolean pointersAllowedHere
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

        if (type.kind == SastTypeKind.PTR) {
            if (!pointersAllowedHere) {
                diagnostics.error(where
                        + " has type '"
                        + types.describeSafe(type)
                        + "': pointers cannot appear in struct fields, static array elements, dynamic array elements, tuple elements, or nested aggregates, recursively. Pointers are downward-only values that are passed to callees, never stored in aggregates. Remove the pointer from the aggregate, use a dynamic array with a separate length or a pool index from franko/pool for linked data, or keep the pointer as a direct variable, parameter, or read-only global.");
                return;
            }

            SastType referent = type.referencedType;

            if (referent == null) {
                return;
            }

            if (referent.kind == SastTypeKind.PTR) {
                checkDeclaredType(
                    referent,
                    where + " pointer referenced type",
                    structWalkStates,
                    new HashSet<>(),
                    true
                );
                return;
            }

            if (referent.kind == SastTypeKind.DYNAMIC_ARRAY
                    || referent.kind == SastTypeKind.STATIC_ARRAY
                    || referent.kind == SastTypeKind.STRUCT
                    || referent.kind == SastTypeKind.TUPLE) {
                checkDeclaredType(
                    referent,
                    where + " pointer referenced type",
                    structWalkStates,
                    new HashSet<>(),
                    false
                );
                return;
            }

            checkDeclaredType(
                referent,
                where + " pointer referenced type",
                structWalkStates,
                new HashSet<>(),
                true
            );
            return;
        }

        if (type.kind == SastTypeKind.DYNAMIC_ARRAY) {

            checkDeclaredType(
                type.elementType,
                where + " dynamic array element",
                structWalkStates,
                new HashSet<>(),
                false
            );
            return;
        }

        if (type.kind == SastTypeKind.STATIC_ARRAY) {
            validateStaticArraySize(type, where);

            checkDeclaredType(
                type.elementType,
                where + " static array element",
                structWalkStates,
                byValuePath,
                false
            );
            return;
        }

        if (type.kind == SastTypeKind.STRUCT) {
            validateStructType(
                type,
                where,
                structWalkStates,
                byValuePath,
                pointersAllowedHere
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
                    byValuePath,
                    false
                );
            }

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
        Set<String> byValuePath,
        boolean pointersAllowedHere
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
                    + " Break the cycle with a dynamic array edge or a pool index from franko/pool.");
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

            checkDeclaredType(
                field.type,
                fieldWhere,
                structWalkStates,
                nextByValuePath,
                false
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
            + " and struct and array values have no such"
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
            + "can have constant initializers (read-only view globals accept "
            + "null, view over a global, or a string "
            + "literal)."
            + " Global constant initializers are folded into static"
            + " storage, which only primitive values can represent"
            + " directly."
            + " Use a primitive global, or use one of the accepted"
            + " read-only forms such as null, view over"
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
