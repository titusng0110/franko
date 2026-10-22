# 6. Types

Franko has the following semantic type categories:

```text
primitive integers
structs
dynamic arrays
static arrays
typed addresses
function return-only void
```

There is currently no separate semantic boolean type. Boolean-like results are represented as `uint8_t`.

Structs are nominal types: two distinct struct declarations are distinct types even when their field layouts are identical. See [Type Equality](27-type-equality.md), [Declarations](11-declarations.md), and the complete struct reference in [Structs](24-structs.md).

`void` is not an ordinary value type. It is valid only where the language explicitly permits it, currently as a function return type.

`ndarray<T, D1, D2, ...>` is not a separate semantic type category. It is syntactic sugar desugared into nested static array types before semantic analysis.