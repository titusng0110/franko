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

    private final Map<SastType, Shape> cache = new HashMap<>();
    private final Set<StructSymbol> visiting = new HashSet<>();

    Shape shapeOf(SastType type) {
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

    boolean containsPointer(SastType type) {
        return shapeOf(type).containsPointer;
    }

    boolean containsDynamicArray(SastType type) {
        return shapeOf(type).containsDynamicArray;
    }

    boolean containsOwnerArrayPointer(SastType type) {
        return shapeOf(type).containsOwnerArrayPointer;
    }

    private Shape compute(SastType type) {
        if (type.kind == SastTypeKind.PRIMITIVE
                || type.kind == SastTypeKind.ENUM
                || type.kind == SastTypeKind.VOID
                || type.kind == SastTypeKind.NULL
                || type.kind == SastTypeKind.FUNCTION) {
            return NONE;
        }

        if (type.kind == SastTypeKind.PTR) {
            boolean ownerArray = type.permission == PtrPermission.OWNER
                    && (type.referencedType.kind == SastTypeKind.DYNAMIC_ARRAY
                        || type.referencedType.kind == SastTypeKind.STATIC_ARRAY);

            return new Shape(true, false, ownerArray);
        }

        if (type.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return new Shape(false, true, false);
        }

        if (type.kind == SastTypeKind.STATIC_ARRAY) {
            return shapeOf(type.elementType);
        }

        if (type.kind == SastTypeKind.STRUCT) {
            return structShape(type.structSymbol);
        }

        if (type.kind == SastTypeKind.TUPLE) {
            boolean pointer = false;
            boolean dynamic = false;

            for (FieldSymbol field : type.fields) {
                Shape fieldShape = shapeOf(field.type);
                pointer |= fieldShape.containsPointer;
                dynamic |= fieldShape.containsDynamicArray;
            }

            return new Shape(pointer, dynamic, false);
        }

        return NONE;
    }

    private Shape structShape(StructSymbol symbol) {
        if (symbol == null || !visiting.add(symbol)) {
            return NONE;
        }

        boolean pointer = false;
        boolean dynamic = false;

        for (FieldSymbol field : symbol.fields) {
            Shape fieldShape = shapeOf(field.type);
            pointer |= fieldShape.containsPointer;
            dynamic |= fieldShape.containsDynamicArray;
        }

        visiting.remove(symbol);

        return new Shape(pointer, dynamic, false);
    }
}
