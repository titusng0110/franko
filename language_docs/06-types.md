# 6. Types

Franko has the following semantic type categories:

```text
primitive integers
dynamic arrays
static arrays
typed addresses
function return-only void
```

There is currently no separate semantic boolean type. Boolean-like results are represented as `uint8_t`.

`void` is not an ordinary value type. It is valid only where the language explicitly permits it, currently as a function return type.
