import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ArrayLiteralLowerer {

    public interface Hoister {
        SemanticVarExprNode hoistArrayLiteral(
                SemanticStaticArrayType arrayType,
                List<BigInteger> values
        );

        SemanticVarExprNode hoistLiteralViewHeader(
                GlobalVariableSymbol dataSymbol
        );
    }

    private static final SemanticType FALLBACK_TYPE =
            new SemanticPrimitiveType(SemanticPrimitiveKind.INT32);

    private final SymbolTable ctx;
    private final ExpressionAnalyzer exprAnalyzer;

    private Hoister hoister = null;

    public ArrayLiteralLowerer(
            SymbolTable ctx,
            ExpressionAnalyzer exprAnalyzer
    ) {
        this.ctx = Objects.requireNonNull(ctx);
        this.exprAnalyzer = Objects.requireNonNull(exprAnalyzer);
    }

    public void setHoister(Hoister hoister) {
        this.hoister = Objects.requireNonNull(hoister);
    }

    public SemanticExprNode lower(ArrayLiteralNode lit, SemanticType expectedType) {
        if (lit.elements.isEmpty()) {
            ctx.error("Empty array literals are not supported: an array "
                    + "literal is a view<array<T, N>> value with N >= 1");
            return hoistFallback(lit);
        }

        SemanticType shape = literalShape(expectedType);

        if (shape == null) {
            ctx.error("Cannot infer the element type of an array literal here: "
                    + "array literals are view<array<T, N>> values and need an "
                    + "assignment, return, or call-argument context with a "
                    + "view<array<integer type>> shape");
            return hoistFallback(lit);
        }

        LiteralValues lowered = lowerLevel(lit, elementTypeOf(shape));

        if (lowered == null) {
            return hoistFallback(lit);
        }

        return hoist(lowered.arrayType, lowered.values);
    }

    private static SemanticType literalShape(SemanticType expectedType) {
        if (expectedType instanceof SemanticPtrType ptr) {
            SemanticType referenced = ptr.referencedType;

            if (referenced instanceof SemanticStaticArrayType
                    || referenced instanceof SemanticDynamicArrayType) {
                return referenced;
            }

            return null;
        }

        if (expectedType instanceof SemanticStaticArrayType
                || expectedType instanceof SemanticDynamicArrayType) {
            return expectedType;
        }

        return null;
    }

    private static SemanticType elementTypeOf(SemanticType shape) {
        if (shape instanceof SemanticStaticArrayType staticArray) {
            return staticArray.elementType;
        }

        if (shape instanceof SemanticDynamicArrayType dynamicArray) {
            return dynamicArray.elementType;
        }

        return null;
    }

    private static final class LiteralValues {
        final SemanticStaticArrayType arrayType;
        final List<BigInteger> values;

        LiteralValues(
                SemanticStaticArrayType arrayType,
                List<BigInteger> values
        ) {
            this.arrayType = arrayType;
            this.values = values;
        }
    }

    private LiteralValues lowerLevel(
            ArrayLiteralNode lit,
            SemanticType elementShape
    ) {
        if (lit.elements.isEmpty()) {
            ctx.error("Empty array literals are not supported: an array "
                    + "literal is a view<array<T, N>> value with N >= 1");
            return null;
        }

        if (elementShape instanceof SemanticPrimitiveType primitive) {
            return lowerScalarLevel(lit, primitive);
        }

        if (elementShape instanceof SemanticStaticArrayType
                || elementShape instanceof SemanticDynamicArrayType) {
            return lowerNestedLevel(lit, elementShape);
        }

        ctx.error("Array literals only support integer element types, got "
                + (elementShape == null ? "<null>" : elementShape.describe()));

        return null;
    }

    private LiteralValues lowerScalarLevel(
            ArrayLiteralNode lit,
            SemanticPrimitiveType primitive
    ) {
        List<BigInteger> values = new ArrayList<>();
        boolean reportedNonConstant = false;

        for (ASTNode element : lit.elements) {
            if (element instanceof ArrayLiteralNode) {
                ctx.error("Array literal elements must be all nested array "
                        + "literals or all integer expressions here");
                return null;
            }

            SemanticExprNode lowered = exprAnalyzer.analyzeExpr(element);

            if (!(lowered.type instanceof SemanticPrimitiveType)) {
                ctx.error("Array literal elements must be integer expressions, got "
                        + lowered.type.describe());
                return null;
            }

            if (!lowered.isConstant()) {
                if (!reportedNonConstant) {
                    ctx.error("Array literal elements must be compile-time "
                            + "constant expressions: a literal is hoisted to a "
                            + "constant global array, like a string literal");
                    reportedNonConstant = true;
                }

                values.add(BigInteger.ZERO);
                continue;
            }

            if (!TypeChecker.fitsBigIntegerToPrimitive(
                    lowered.constantValue,
                    primitive.kind)) {
                ctx.error("Array literal element "
                        + lowered.constantValue
                        + " does not fit in "
                        + primitive.describe());
            }

            values.add(lowered.constantValue);
        }

        return new LiteralValues(
                new SemanticStaticArrayType(
                        primitive,
                        Integer.toString(lit.elements.size())
                ),
                values
        );
    }

    private LiteralValues lowerNestedLevel(
            ArrayLiteralNode lit,
            SemanticType elementShape
    ) {
        SemanticType rowElementShape = elementTypeOf(elementShape);

        List<BigInteger> values = new ArrayList<>();
        SemanticStaticArrayType rowType = null;

        for (ASTNode element : lit.elements) {
            if (!(element instanceof ArrayLiteralNode row)) {
                ctx.error("Expected a nested array literal here: the context "
                        + "element type is "
                        + (elementShape == null
                                ? "<null>"
                                : elementShape.describe()));
                return null;
            }

            LiteralValues loweredRow = lowerLevel(row, rowElementShape);

            if (loweredRow == null) {
                return null;
            }

            if (rowType == null) {
                rowType = loweredRow.arrayType;
            } else if (!rowType.equals(loweredRow.arrayType)) {
                ctx.error("All rows of a nested array literal must have the "
                        + "same length: array literals are static arrays");
                return null;
            }

            values.addAll(loweredRow.values);
        }

        return new LiteralValues(
                new SemanticStaticArrayType(
                        rowType,
                        Integer.toString(lit.elements.size())
                ),
                values
        );
    }

    private SemanticExprNode hoist(
            SemanticStaticArrayType arrayType,
            List<BigInteger> values
    ) {
        if (hoister == null) {
            ctx.error("Internal compiler error: no array literal hoister "
                    + "wired into the array literal lowerer");
            return new SemanticIntLiteralNode(
                    FALLBACK_TYPE,
                    BigInteger.ZERO,
                    "0"
            );
        }

        SemanticVarExprNode global =
                hoister.hoistArrayLiteral(arrayType, values);

        return new SemanticGetPtrNode(
                new SemanticViewType(arrayType),
                global
        );
    }

    public SemanticExprNode resolveImmediateDecay(
            SemanticExprNode expr,
            SemanticType expectedType
    ) {
        if (!(expr instanceof SemanticGetPtrNode get)) {
            return expr;
        }

        if (!(get.target instanceof SemanticVarExprNode var)) {
            return expr;
        }

        if (!(var.symbol instanceof GlobalVariableSymbol data)) {
            return expr;
        }

        if (data.literalBytes == null && data.literalValues == null) {
            return expr;
        }

        if (data.literalHeaderData != null) {
            return expr;
        }

        if (!PointerPermissions.isStaticArrayViewDecay(
                get.type,
                expectedType)) {
            return expr;
        }

        if (hoister == null) {
            ctx.error("Internal compiler error: no array literal hoister "
                    + "wired into the array literal lowerer");
            return expr;
        }

        SemanticVarExprNode header =
                hoister.hoistLiteralViewHeader(data);

        return new SemanticGetPtrNode(
                new SemanticViewType((SemanticDynamicArrayType) header.type),
                header
        );
    }

    private SemanticExprNode hoistFallback(ArrayLiteralNode lit) {
        SemanticStaticArrayType shape = fallbackShapeType(lit.elements);
        List<BigInteger> values = new ArrayList<>();
        appendZeroValues(values, shape);

        return hoist(shape, values);
    }

    private static SemanticStaticArrayType fallbackShapeType(
            List<ASTNode> elements
    ) {
        int size = Math.max(1, elements.size());

        boolean nested = !elements.isEmpty();

        for (ASTNode element : elements) {
            if (!(element instanceof ArrayLiteralNode)) {
                nested = false;
                break;
            }
        }

        if (nested) {
            ArrayLiteralNode firstRow = (ArrayLiteralNode) elements.get(0);

            return new SemanticStaticArrayType(
                    fallbackShapeType(firstRow.elements),
                    Integer.toString(size)
            );
        }

        return new SemanticStaticArrayType(
                FALLBACK_TYPE,
                Integer.toString(size)
        );
    }

    private static void appendZeroValues(
            List<BigInteger> values,
            SemanticType shape
    ) {
        if (shape instanceof SemanticStaticArrayType staticArray) {
            for (int i = 0; i < parseSize(staticArray.sizeLiteral); i++) {
                appendZeroValues(values, staticArray.elementType);
            }

            return;
        }

        values.add(BigInteger.ZERO);
    }

    private static int parseSize(String sizeLiteral) {
        try {
            return new BigInteger(sizeLiteral.trim(), 10).intValueExact();
        } catch (Exception e) {
            return 1;
        }
    }
}
