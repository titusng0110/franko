# 8. Constant Folding

Franko splits constant handling into evaluation and validation.

**Evaluation** computes the value of a syntactically pure constant
expression at compile time. It applies to every constant-expression
position: static array sizes, global initializers, struct field defaults,
and `break N` levels.

Examples:

```franko
1 + 2       // evaluated to 3
4 * 5       // evaluated to 20
7 % 3       // evaluated to 1
10 == 10    // evaluated to 1
!0          // evaluated to 1
~0          // evaluated to -1
1 << 3      // evaluated to 8
```

`[int32, 0x10]` is compiled as `[int32, 16]`; `int8 g = 1 + 2` is
compiled as `int8 g = 3`.

**Validation** checks the computed value in context, using the declared
types: positivity and `uint32` range for array sizes, fit for global and
field initializers, primitive-only field defaults.

Evaluation failures are compile errors:

```franko
[int, 10 / 0] bytes;   // invalid: Division by zero in constant expression
[int, 10 % 0] bytes;   // invalid: Division by zero in constant expression
```

Modulus folding uses truncated division, so the remainder keeps the sign
of the dividend (see [Operators](13-operators.md)).

Evaluation is conservative.

Constants are evaluated using arbitrary-precision integer arithmetic,
not fixed-width wrapping arithmetic.

Integer overflow is not applied during evaluation. Range checking happens
when the value is used in a concrete type context:

```franko
int8 g = 300;   // evaluated to 300, then rejected: does not fit in int8
```

A shift whose constant right operand is negative cannot be evaluated; it
is rejected as an invalid constant expression in a constant-expression
position. Negative shifts inside ordinary expressions are rejected by
the operator rules (see [Operators](13-operators.md)).

Constant expressions inside ordinary code (as opposed to the positions
above) are also folded at compile time. Diagnostics keep their existing
wording:

```franko
int32 x = 1 / 0;   // invalid: Division by zero in constant expression
```

`?` array size inference uses the same evaluator quietly: it only needs
the value when one exists, and reports its own inference diagnostics
otherwise (see [Shorthands and Equivalences](24-desugaring.md)).

A static array `length()` call is a compile-time constant, not a runtime
read (see [Array Intrinsics](22-array-intrinsics.md)).
