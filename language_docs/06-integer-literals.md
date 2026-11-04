# 6. Integer Literals

Integer literals are evaluated with arbitrary precision before contextual type checking; no intermediate literal arithmetic overflows.

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

There is no octal literal syntax. Write decimal literals without
leading zeros: `075` is not an octal escape hatch and its meaning is not
portable.