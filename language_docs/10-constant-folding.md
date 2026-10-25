# 10. Constant Folding

Franko splits constant handling into evaluation and validation.

**Evaluation** computes the value of a syntactically pure constant expression. This happens before semantic analysis, in the desugarer, for every `constExpr` slot: static array sizes, `ndarray` dimensions, global initializers, and struct field defaults.

Examples:

```franko
1 + 2       // evaluated to 3
4 * 5       // evaluated to 20
10 == 10    // evaluated to 1
!0          // evaluated to 1
1 << 3      // evaluated to 8
```

`array<int32_t, 0x10>` reaches semantic analysis as `array<int32_t, 16>`; `int8_t g = 1 + 2` reaches it as `int8_t g = 3`.

**Validation** checks the computed value in context. This stays in semantic analysis and later checkers, because it needs declared types: positivity and `uint32_t` range for array sizes, fit for global and field initializers, primitive-only field defaults.

Evaluation failures are reported before semantic analysis:

```franko
array<int, 10 / 0> bytes;   // invalid: Division by zero in constant expression
```

Evaluation is conservative.

Constants are evaluated using arbitrary-precision `BigInteger`, not fixed-width wrapping arithmetic.

Integer overflow is not applied during evaluation. Range checking happens when the value is used in a concrete type context:

```franko
int8_t g = 300;   // evaluated to 300, then rejected: does not fit in INT8
```

A shift whose constant right operand is negative cannot be evaluated; it is rejected during desugaring as an invalid constant expression. Negative shifts inside ordinary expressions are instead rejected later by the operator rules (see [Operators](17-operators.md)).

Constant expressions inside ordinary code (as opposed to `constExpr` slots) are still folded during semantic analysis, when expressions are lowered. Diagnostics there keep their existing wording:

```franko
int32_t x = 1 / 0;   // invalid: Division by zero in constant expression
```

`?` array size inference uses the same evaluator quietly: it only needs the value when one exists, and reports its own inference diagnostics otherwise (see [Desugaring](05-desugaring.md)).

A static array `length()` call is emitted as a compile-time constant, not as a runtime read (see [Array Intrinsics](21-array-intrinsics.md)).
