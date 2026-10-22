# 27. Declaration Type Validity

A declared variable type is valid if it is one of:

- primitive integer type;
- dynamic array of a valid non-`void` type;
- static array of a valid non-`void` type with valid size;
- address of a valid non-`void` type.

Valid:

```franko
int32_t x;
array<int32_t> xs;
array<uint8_t, 64> bytes;
ndarray<byte, 12, 20> grid;   // sugar for array<array<byte, 20>, 12>
addr<int32_t> p;
addr<array<int32_t>> ap;
```

Invalid:

```franko
void x;
array<void> xs;
addr<void> p;
```

`ndarray` declarations are valid exactly when their unfolded nested static array type is valid: every dimension must satisfy the static array size rules (see [Arrays](19-arrays.md)).

A function return type has separate rules. It may be:

- `void`;
- a primitive integer type;
- an address type.

Array return types are invalid.