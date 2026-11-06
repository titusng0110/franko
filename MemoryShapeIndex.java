import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class MemoryShapeIndex {
    static final class Shape {
        final boolean containsPointer;
        final boolean containsDynamicArray;
        final boolean containsOwnerArrayPointer;

        Shape(
                boolean containsPointer,
                boolean containsDynamicArray,
                boolean containsOwnerArrayPointer
        ) {
            this.containsPointer = containsPointer;
            this.containsDynamicArray = containsDynamicArray;
            this.containsOwnerArrayPointer = containsOwnerArrayPointer;
        }
    }

    private static final Shape NONE = new Shape(false, false, false);

    private final Map<SemanticType, Shape> cache = new HashMap<>();
    private final Set<StructSymbol> visiting = new HashSet<>();

    Shape shapeOf(SemanticType type) {
        if (type == null) {
            return NONE;
        }

        Shape cached = cache.get(type);

        if (cached != null) {
            return cached;
        }

        Shape shape = compute(type);
        cache.put(type, shape);
        return shape;
    }

    boolean containsPointer(SemanticType type) {
        return shapeOf(type).containsPointer;
    }

    boolean containsDynamicArray(SemanticType type) {
        return shapeOf(type).containsDynamicArray;
    }

    boolean containsOwnerArrayPointer(SemanticType type) {
        return shapeOf(type).containsOwnerArrayPointer;
    }

    private Shape compute(SemanticType type) {
        if (type instanceof SemanticPrimitiveType
                || type instanceof SemanticEnumType
                || type instanceof SemanticVoidType
                || type instanceof SemanticNullType
                || type instanceof SemanticFunctionType) {
            return NONE;
        }

        if (type instanceof SemanticPtrType pointer) {
            boolean ownerArray = pointer.permission == PtrPermission.OWNER
                    && (pointer.referencedType instanceof SemanticDynamicArrayType
                        || pointer.referencedType instanceof SemanticStaticArrayType);

            return new Shape(true, false, ownerArray);
        }

        if (type instanceof SemanticDynamicArrayType) {
            return new Shape(false, true, false);
        }

        if (type instanceof SemanticStaticArrayType array) {
            return shapeOf(array.elementType);
        }

        if (type instanceof SemanticStructType struct) {
            return structShape(struct.structSymbol);
        }

        if (type instanceof SemanticTupleType tuple) {
            boolean pointer = false;
            boolean dynamic = false;
            boolean ownerArray = false;

            for (FieldSymbol field : tuple.fields) {
                Shape fieldShape = shapeOf(field.type);
                pointer |= fieldShape.containsPointer;
                dynamic |= fieldShape.containsDynamicArray;
                ownerArray |= fieldShape.containsOwnerArrayPointer;
            }

            return new Shape(pointer, dynamic, ownerArray);
        }

        return NONE;
    }

    private Shape structShape(StructSymbol symbol) {
        if (symbol == null || !visiting.add(symbol)) {
            return NONE;
        }

        boolean pointer = false;
        boolean dynamic = false;
        boolean ownerArray = false;

        for (FieldSymbol field : symbol.fields) {
            Shape fieldShape = shapeOf(field.type);
            pointer |= fieldShape.containsPointer;
            dynamic |= fieldShape.containsDynamicArray;
            ownerArray |= fieldShape.containsOwnerArrayPointer;
        }

        visiting.remove(symbol);

        return new Shape(pointer, dynamic, ownerArray);
    }
}
