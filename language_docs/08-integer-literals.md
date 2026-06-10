# 8. Integer Literals

Integer literals are represented internally as arbitrary-precision `BigInteger` values before contextual type checking.

Supported literal forms:

```franko
123
+123
0xFF
0XFF
0b1010
0B1010
```

Negative values are normally represented by unary negation:

```franko
-1
```

There are no integer suffixes such as:

```franko
u
L
ULL
```

There is no documented octal literal syntax in the current semantic checker.

Important distinction:

- `+123` may be accepted as part of integer literal syntax.
- Unary `+x` is **not** currently implemented as a semantic unary operator.
