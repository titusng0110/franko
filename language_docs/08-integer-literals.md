# 8. Integer Literals

Integer literals are represented internally as arbitrary-precision `BigInteger` values before contextual type checking.

Supported literal forms:

```franko
123       // decimal
0xFF      // hexadecimal
0XFF      // hexadecimal (case-insensitive prefix)
0b1010    // binary
0B1010    // binary (case-insensitive prefix)
```

Negative values are normally represented by unary negation:

```franko
-1
```

which is part of expression syntax, not part of the literal.

There are no integer literals with a leading sign, and there are no integer suffixes such as:

```franko
u
L
ULL
```

There is no unary `+` operator and no `+123` literal form:

```franko
+123    // invalid: parsed as an unmatched '+' token
```

There is no documented octal literal syntax. Decimal literals with leading zeros (for example `075`) are currently passed to the C++14 backend verbatim, where they may be interpreted as octal by the C++ compiler; avoid leading zeros in decimal literals.

The backend emits very large decimal literals with a trailing `ULL` suffix so they remain well-formed C++14 (see [Runtime and Backend Notes](30-runtime-and-backend-notes.md)).