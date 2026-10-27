# 29. Declaration Type Validity

A declared variable type is valid if it is one of:

- primitive integer type;
- a declared struct type;
- dynamic array of a valid non-`void` type;
- static array of a valid non-`void` type with valid size;
- pointer of a valid non-`void` type.

Valid:

```franko
struct X { int32_t n }

int32_t x;
X s;
array<X> xs;
array<uint8_t, 64> bytes;
ndarray<byte, 12, 20> grid;   // sugar for array<array<byte, 20>, 12>
ptr<int32_t> p;
ptr<array<int32_t>> ap;
```

Invalid:

```franko
void x;
array<void> xs;
ptr<void> p;
T s;    // invalid when no struct named T is declared
```

`ndarray` declarations are valid exactly when their unfolded nested static array type is valid: every dimension must satisfy the static array size rules (see [Arrays](20-arrays.md)).

A function return type has separate rules. It may be:

- `void`;
- a primitive integer type;
- an pointer type; or
- a struct whose fields are all assignable value types, recursively.

Array return types are invalid.