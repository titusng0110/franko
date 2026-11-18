# 11. Declaration Type Validity

A declared variable type is valid if it is one of:

- primitive integer type;
- a declared struct type;
- a declared enum type;
- a tuple of valid non-`void` element types (at least two elements);
- dynamic array of a valid non-`void` type;
- static array of a valid non-`void` type with valid size;
- pointer of a valid non-`void` type.

Pointers (`ptr<T>`, `mview<T>`, and `view<T>`) are forbidden in all
aggregate value storage, recursively:

- struct fields;
- static array elements;
- dynamic array elements;
- tuple elements;
- nested structs, tuples, and arrays inside those positions;
- aggregates that are the referent of a pointer.

There is no owned-versus-view distinction for aggregate membership: all
three pointer permissions are rejected. A pointer referent may itself be a
pointer to unlimited depth (pointer stacks such as `ptr<ptr<T>>` are
ordinary values), but a pointer may refer to a struct, array, or tuple
only when that aggregate contains no pointers. This is a type-validity
error; see
[Pointers Cannot Appear in Aggregates](26-pointers.md#264-pointers-cannot-appear-in-aggregates)
and [Stacked Pointers](26-pointers.md#263-stacked-pointers).

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
ptr<ptr<int32>> pp;
view<ptr<int32>> vp;
```

Invalid:

```franko
void x;
[void] xs;
ptr<void> p;
(int32, void) pair;      // invalid: void cannot be a tuple element
struct S { void field }   // invalid: void cannot be a struct field
T s;                       // invalid when no struct named T is declared
[ptr<int32>] ptrs;        // invalid: pointer inside an array
[view<int32>] views;      // invalid: all pointer permissions banned
struct S { ptr<int32> p } // invalid: pointer inside a struct
struct T { view<int32> v } // invalid: view field still banned
(ptr<int32>, int32) pair; // invalid: pointer inside a tuple
(view<int32>, int32) vpair; // invalid: view element still banned
ptr<[ptr<int32>]> p;       // invalid: referent aggregate contains a pointer
ptr<[view<int32>]> q;      // invalid: referent aggregate contains a view
ptr<(ptr<int32>, int32)> r; // invalid: referent tuple contains a pointer
```

Static arrays nest: every level of `[[byte, 20], 12]` must satisfy the static array size rules (see [Arrays](21-arrays.md)).

Global variables have an additional restriction: their type must be
**passable by value** (primitive, enum, pointer, or a struct/tuple with
no pointer or array members). Arrays and aggregates with pointer or array
members can only be local. Only read-only `view<T>` pointer globals are
allowed; `ptr<T>` and `mview<T>` globals are rejected. See
[Declarations](18-declarations.md#184-global-variable-declarations) and
[Pointer Globals](26-pointers.md#269-pointer-globals).

A function return type has separate rules. It may be:

- `void`;
- a primitive integer type;
- a declared enum type;
- a struct with no pointer or array members, recursively; or
- a tuple with no pointer or array members, recursively.

Array and pointer return types are invalid. A struct or tuple containing
a pointer, a static array, or a dynamic array cannot be returned by
value; write the result through an out-parameter that points at
non-pointer storage instead.