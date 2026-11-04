# 6. Types

Franko has the following semantic type categories:

```text
primitive integers
structs
tuples
dynamic arrays
static arrays
typed pointers (ptr / mview / view)
function return-only void
```

There is currently no separate boolean type. Boolean-like results are represented as `uint8`.

Structs are nominal types: two distinct struct declarations are distinct types even when their field layouts are identical. See [Type Equality](28-type-equality.md), [Declarations](11-declarations.md), and the complete struct reference in [Structs](25-structs.md).

`void` is not an ordinary value type. It is valid only where the language explicitly permits it, currently as a function return type.

Pointers come in three permission levels — `ptr<T>` (owner), `mview<T>` (mutable view), `view<T>` (read-only view) — one capability model for all of them. See [Pointers](24-pointers.md).

A static array size may be written as the inferred-size marker `?` (for example `view<[int32, ?]>`) only in a local `view` declaration with an array-literal initializer. The size is inferred at compile time, so every type carries a concrete size.

## 6.1 Tuples

A tuple is an ordered aggregate written as a parenthesized, comma-separated list of at least two element types:

```franko
(int32, int32) pair
(int32 val, int32 err) result
(int32 val, uint8) mixed
```

Element names are optional and are aliases for that declaration or type use only. Unnamed elements have the effective names `_0`, `_1`, and so on; mixed forms such as `(int32 val, uint8)` give `val` and `_1`.

Tuple type identity is structural and depends only on the ordered element types:

```franko
(int32 val, int32 err) == (int32, int32)
(int32, uint32) != (uint32, int32)
```

Names do not participate in equality, so a value declared with one set of names can be assigned to a tuple declared with another set. Order does participate, so swapping element types produces a different tuple type.

Tuples nest, may contain structs, and may be elements of arrays or pointer referents wherever the ordinary type rules allow it:

```franko
((int32, int32) inner, int32 outer) nested
[(int32, int32), 4] table
ptr<(int32, int32)> tuplePtr
```

There are no tuple literals, destructuring forms, tuple methods, tuple operators, or tuple casts. A tuple value comes from a declaration, a field write, assignment from another tuple, or a function return. See [Declarations](11-declarations.md#117-tuple-declarations), [Assignment](14-assignment.md#148-tuple-assignment), and [Unsupported or Restricted Features](30-unsupported-or-restricted-features.md#3018-tuple-restrictions).