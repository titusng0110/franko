# 6. Types

Franko has the following semantic type categories:

```text
primitive integers
structs
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