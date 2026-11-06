# 9. Types

Franko has the following semantic type categories:

```text
primitive integers
structs
tuples
enums
dynamic arrays
static arrays
typed pointers (ptr / mview / view)
function return-only void
```

There is no separate boolean type. Boolean-like results are represented as
`uint8`.

Each category has its own detailed chapter:

| Type category | Chapter |
|---|---|
| Primitive integers | [Primitive Integer Types](05-primitive-integer-types.md) |
| Static arrays and dynamic arrays | [Arrays](20-arrays.md) |
| Array and string literal values | [Array Literals](21-array-literals.md), [String Literals](23-string-literals.md) |
| Pointers (`ptr`, `mview`, `view`) | [Pointers](25-pointers.md) |
| Structs | [Structs](26-structs.md) |
| Tuples | [Tuples](28-tuples.md) |
| Enums | [Enums](30-enums.md) |
| `void` | [Functions](19-functions.md) |

Structs are nominal types: two distinct struct declarations are distinct
types even when their field layouts are identical. See
[Type Equality](10-type-equality.md), [Declarations](18-declarations.md), and
[Structs](26-structs.md).

Tuples are structural aggregate types: their identity depends on the ordered
element types, not on the element names written at a declaration. See
[Tuples](28-tuples.md) and [Type Equality](10-type-equality.md).

Enums are nominal types backed by one of the eight primitive integer
types: two distinct enum declarations are distinct types even when their
backing types and variants match. See [Enums](30-enums.md) and
[Type Equality](10-type-equality.md).

`void` is not an ordinary value type. It is valid only where the language
explicitly permits it, currently as a function return type.

Pointers come in three permission levels — `ptr<T>` (owner), `mview<T>`
(mutable view), `view<T>` (read-only view) — one capability model for all of
them. See [Pointers](25-pointers.md).

A static array size may be written as the inferred-size marker `?` (for
example `view<[int32, ?]>`) only in a local `view` declaration with an
array-literal initializer. The size is inferred at compile time, so every type
carries a concrete size.
