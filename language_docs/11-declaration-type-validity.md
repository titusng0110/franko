# 11. Declaration Type Validity

A declared variable type is valid if it is one of:

- primitive integer type;
- a declared struct type;
- a declared enum type;
- a tuple of valid non-`void` element types (at least two elements);
- dynamic array of a valid non-`void` type;
- static array of a valid non-`void` type with valid size;
- pointer of a valid non-`void` type.

An **owning** pointer (`ptr<T>`) may not appear inside an array, struct, or
tuple, recursively. `mview<T>` and `view<T>` are allowed in aggregates, but
their referent types may not recursively contain an owning `ptr<T>`. This is a
type-validity error, not a warning; see
[Pointers](25-pointers.md#2513-owning-pointers-in-aggregates-are-forbidden).

Valid:

```franko
struct X { int32 n }

enum<uint8> Status { spawning, alive }

int32 x;
X s;
Status st;
(int32, int32) pair;
(int32 val, X) named;
[X] xs;
[uint8, 64] bytes;
[[byte, 20], 12] grid;
ptr<int32> p;
ptr<[int32]> ap;
ptr<(int32, int32)> tp;
struct Views { view<int32> a; mview<int32> b }
[view<int32>] view_array;
```

Invalid:

```franko
void x;
[void] xs;
ptr<void> p;
(int32, void) pair;      // invalid: void cannot be a tuple element
struct S { void field }   // invalid: void cannot be a struct field
T s;                       // invalid when no struct named T is declared
[ptr<int32>] ptrs;        // invalid: owning ptr inside an array
struct S { ptr<int32> p } // invalid: owning ptr inside a struct
(ptr<int32>, int32) pair; // invalid: owning ptr inside a tuple
[view<ptr<int32>>] nested; // invalid: a view whose referent is an owning ptr
ptr<[ptr<int32>]> p;       // invalid: pointer to an array of owning ptrs
```

Static arrays nest: every level of `[[byte, 20], 12]` must satisfy the static array size rules (see [Arrays](20-arrays.md)).

A function return type has separate rules. It may be:

- `void`;
- a primitive integer type;
- a declared enum type;
- a pointer type (`ptr<T>`, `mview<T>`, or `view<T>`); or
- a struct whose fields are all assignable value types, recursively; or
- a tuple whose elements are all assignable value types, recursively.

Array return types are invalid. A tuple containing an array or another non-assignable element cannot be returned by value; return a pointer to it instead.