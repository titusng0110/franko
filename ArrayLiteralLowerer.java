import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ArrayLiteralLowerer {

    private static final SastType FALLBACK_TYPE =
            SastType.primitive(SemanticPrimitiveKind.INT32);

    private final SymbolTable ctx;
    private final ExpressionAnalyzer exprAnalyzer;

    private final ArrayLiteralHoister hoister;

    public ArrayLiteralLowerer(
            SymbolTable ctx,
            ExpressionAnalyzer exprAnalyzer,
            ArrayLiteralHoister hoister
    ) {
        this.ctx = Objects.requireNonNull(ctx);
        this.exprAnalyzer = Objects.requireNonNull(exprAnalyzer);
        this.hoister = Objects.requireNonNull(hoister);
    }

    public SastNode lower(AstNode lit, SastType expectedType) {
        if (lit.elements.isEmpty()) {
            return SastNode.nullLiteral(lit.span, SastType.nullType());
        }

        SastType shape = literalShape(expectedType);

        if (shape == null) {
            ctx.error("Cannot infer the element type of an array literal here. "
                    + "Array literals are view<[T, N]> values with no element "
                    + "type of their own, so they need an assignment, return, "
                    + "or call-argument context with a view<[integer or enum "
                    + "type]> shape. Assign the literal to a view<[T, N]> "
                    + "variable, return it from a function with that return "
                    + "type, or pass it to a parameter with that shape.");
            return hoistFallback(lit);
        }

        LiteralValues lowered = lowerLevel(lit, elementTypeOf(shape));

        if (lowered == null) {
            return hoistFallback(lit);
        }

        return hoist(lowered.arrayType, lowered.values, lit.span);
    }

    private static SastType literalShape(SastType expectedType) {
        if (expectedType == null) {
            return null;
        }

        if (expectedType.kind == SastTypeKind.PTR) {
            SastType referenced = expectedType.referencedType;

            if (referenced.kind == SastTypeKind.STATIC_ARRAY
                    || referenced.kind == SastTypeKind.DYNAMIC_ARRAY) {
                return referenced;
            }

            return null;
        }

        if (expectedType.kind == SastTypeKind.STATIC_ARRAY
                || expectedType.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return expectedType;
        }

        return null;
    }

    private static SastType elementTypeOf(SastType shape) {
        if (shape.kind == SastTypeKind.STATIC_ARRAY) {
            return shape.elementType;
        }

        if (shape.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return shape.elementType;
        }

        return null;
    }

    private static final class LiteralValues {
        final SastType arrayType;
        final List<BigInteger> values;

        LiteralValues(
                SastType arrayType,
                List<BigInteger> values
        ) {
            this.arrayType = arrayType;
            this.values = values;
        }
    }

    private LiteralValues lowerLevel(
            AstNode lit,
            SastType elementShape
    ) {
        if (lit.elements.isEmpty()) {
            ctx.error("Empty array literals are not supported: an array "
                    + "literal is a view<[T, N]> value with N >= 1. An empty "
                    + "literal is the null view and carries no element type "
                    + "or inner dimension. Provide at least one element here, "
                    + "or use '[]' only where a null view is expected.");
            return null;
        }

        if (elementShape.kind == SastTypeKind.PRIMITIVE) {
            return lowerScalarLevel(lit, elementShape);
        }

        if (elementShape.kind == SastTypeKind.ENUM) {
            return lowerEnumLevel(lit, elementShape);
        }

        if (elementShape.kind == SastTypeKind.STATIC_ARRAY
                || elementShape.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return lowerNestedLevel(lit, elementShape);
        }

        ctx.error("Array literals only support integer or enum element types, got "
                + (elementShape == null ? "<null>" : elementShape.describe())
                + ". Array literals are hoisted to shared constant arrays, "
                + "which can hold only primitive integer values or enum "
                + "variants. Change the expected element type or use integer "
                + "and enum variant elements.");

        return null;
    }

    private LiteralValues lowerScalarLevel(
            AstNode lit,
            SastType primitive
    ) {
        List<BigInteger> values = new ArrayList<>();
        boolean reportedNonConstant = false;

        for (AstNode element : lit.elements) {
            if (element.kind == AstKind.ARRAY_LITERAL) {
                ctx.error("Array literal elements must be all nested array "
                        + "literals or all integer expressions here. Mixing "
                        + "rows and scalars would create a ragged array whose "
                        + "shape is not static at every level. Make every "
                        + "element at this level either an integer expression "
                        + "or a nested array literal.");
                return null;
            }

            SastNode lowered = exprAnalyzer.analyzeExpr(element);

            if (!(lowered.type.kind == SastTypeKind.PRIMITIVE)) {
                ctx.error("Array literal elements must be integer expressions, got "
                        + lowered.type.describe()
                        + ". A scalar array literal is stored as an integer "
                        + "constant array, so each element must have an integer "
                        + "primitive type. Replace the element with an integer "
                        + "expression or change the expected element type.");
                return null;
            }

            if (!lowered.isConstant()) {
                if (!reportedNonConstant) {
                    ctx.error("Array literal elements must be compile-time "
                            + "constant expressions. A literal is hoisted to a "
                            + "constant global array, like a string literal, so "
                            + "its elements cannot depend on runtime values. "
                            + "Replace the non-constant element with an integer "
                            + "constant expression or compute the value into a "
                            + "declared array with element assignments.");
                    reportedNonConstant = true;
                }

                values.add(BigInteger.ZERO);
                continue;
            }

            if (!TypeChecker.fitsBigIntegerToPrimitive(
                    lowered.constantValue,
                    primitive.primitive)) {
                ctx.error("Array literal element "
                        + lowered.constantValue
                        + " does not fit in "
                        + primitive.describe()
                        + ". Every element is stored as a value of the "
                        + "inferred element type, so it must be representable "
                        + "in that type. Use a value within the range of "
                        + primitive.describe()
                        + " or change the expected element type to a wider "
                        + "integer type.");
            }

            values.add(lowered.constantValue);
        }

        return new LiteralValues(
                SastType.staticArray(
                        primitive,
                        Integer.toString(lit.elements.size())
                ),
                values
        );
    }

    private LiteralValues lowerEnumLevel(
            AstNode lit,
            SastType enumType
    ) {
        List<BigInteger> values = new ArrayList<>();

        for (AstNode element : lit.elements) {
            if (element.kind == AstKind.ARRAY_LITERAL) {
                ctx.error("Array literal elements must be all nested array "
                        + "literals or all enum variant expressions here. "
                        + "Mixing rows and variants would create a ragged "
                        + "array whose shape is not static at every level. "
                        + "Make every element at this level either an enum "
                        + "variant or a nested array literal.");
                return null;
            }

            SastNode lowered = exprAnalyzer.analyzeExpr(element);

            if (!(lowered.kind == SastKind.ENUM_VARIANT)) {
                ctx.error("Array literal elements must be enum variants of type '"
                        + enumType.enumSymbol.qualifiedName()
                        + "', got "
                        + lowered.type.describe()
                        + ". An enum array literal is stored as constant "
                        + "variant values, so each element must name a variant "
                        + "of the inferred enum type. Replace the element with "
                        + "a variant of '"
                        + enumType.enumSymbol.qualifiedName()
                        + "'.");
                return null;
            }

            if (lowered.enumSymbol != enumType.enumSymbol) {
                ctx.error("Array literal element '"
                        + lowered.variant.name
                        + "' has enum type '"
                        + lowered.enumSymbol.qualifiedName()
                        + "', expected '"
                        + enumType.enumSymbol.qualifiedName()
                        + "'. All elements of an enum array literal must "
                        + "belong to the same enum so the hoisted array has "
                        + "one element type. Use a variant of '"
                        + enumType.enumSymbol.qualifiedName()
                        + "' or change the array's expected element type.");
                return null;
            }

            values.add(lowered.variant.value);
        }

        return new LiteralValues(
                SastType.staticArray(
                        enumType,
                        Integer.toString(lit.elements.size())
                ),
                values
        );
    }

    private LiteralValues lowerNestedLevel(
            AstNode lit,
            SastType elementShape
    ) {
        SastType rowElementShape = elementTypeOf(elementShape);

        List<BigInteger> values = new ArrayList<>();
        SastType rowType = null;

        for (AstNode element : lit.elements) {
            if (element.kind != AstKind.ARRAY_LITERAL) {
                ctx.error("Expected a nested array literal here: the context "
                        + "element type is "
                        + (elementShape == null
                                ? "<null>"
                                : elementShape.describe())
                        + ". A nested static array at this level needs each "
                        + "element to supply a complete row literal. Wrap this "
                        + "element's values in '[...]' or change the expected "
                        + "element type to a scalar.");
                return null;
            }

            LiteralValues loweredRow = lowerLevel(element, rowElementShape);

            if (loweredRow == null) {
                return null;
            }

            if (rowType == null) {
                rowType = loweredRow.arrayType;
            } else if (!rowType.equals(loweredRow.arrayType)) {
                ctx.error("All rows of a nested array literal must have the "
                        + "same length: array literals are static arrays. "
                        + "Every inner dimension is fixed at compile time, so "
                        + "a ragged row would have no single static array "
                        + "type. Add or remove elements so every row at this "
                        + "level has the same length.");
                return null;
            }

            values.addAll(loweredRow.values);
        }

        return new LiteralValues(
                SastType.staticArray(
                        rowType,
                        Integer.toString(lit.elements.size())
                ),
                values
        );
    }

    private SastNode hoist(
            SastType arrayType,
            List<BigInteger> values,
            SourceSpan span
    ) {
        if (hoister == null) {
            ctx.error("Internal compiler error: no array literal hoister "
                    + "wired into the array literal lowerer");
            return SastNode.intLiteral(
                    span,
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        SastNode global =
                hoister.hoistArrayLiteral(arrayType, values, span);

        return SastNode.getPtr(
                span,
                SastType.view(arrayType),
                global
        );
    }

    private SastNode hoistFallback(AstNode lit) {
        SastType shape = fallbackShapeType(lit.elements);
        List<BigInteger> values = new ArrayList<>();
        appendZeroValues(values, shape);

        return hoist(shape, values, lit.span);
    }

    private static SastType fallbackShapeType(
            List<AstNode> elements
    ) {
        int size = Math.max(1, elements.size());

        boolean nested = !elements.isEmpty();

        for (AstNode element : elements) {
            if (element.kind != AstKind.ARRAY_LITERAL) {
                nested = false;
                break;
            }
        }

        if (nested) {
            AstNode firstRow = elements.get(0);

            return SastType.staticArray(
                    fallbackShapeType(firstRow.elements),
                    Integer.toString(size)
            );
        }

        return SastType.staticArray(
                FALLBACK_TYPE,
                Integer.toString(size)
        );
    }

    private static void appendZeroValues(
            List<BigInteger> values,
            SastType shape
    ) {
        if (shape.kind == SastTypeKind.STATIC_ARRAY) {
            for (int i = 0; i < parseSize(shape.sizeLiteral); i++) {
                appendZeroValues(values, shape.elementType);
            }

            return;
        }

        values.add(BigInteger.ZERO);
    }

    private static int parseSize(String sizeLiteral) {
        try {
            return new BigInteger(sizeLiteral.trim(), 10).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return 1;
        }
    }
}
