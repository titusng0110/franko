# 25. Declaration Type Validity

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
addr<int32_t> p;
addr<array<int32_t>> ap;
```

Invalid:

```franko
void x;
array<void> xs;
addr<void> p;
```

A function return type has separate rules. It may be:

- `void`;
- a primitive integer type;
- an address type.

Array return types are invalid.
