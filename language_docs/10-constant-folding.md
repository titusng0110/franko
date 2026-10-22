# 10. Constant Folding

The semantic analyzer folds pure integer constant expressions when safe.

Examples:

```franko
1 + 2       // folded to 3
4 * 5       // folded to 20
10 == 10    // folded to 1
!0          // folded to 1
1 << 3      // folded to 8
```

Folding is conservative.

Constants are folded using arbitrary-precision `BigInteger`, not fixed-width wrapping arithmetic.

Integer overflow is not applied during folding. Range checking happens when the value is used in a concrete type context.

Division by zero in a foldable constant expression is rejected during semantic analysis:

```franko
1 / 0        // invalid: Division by zero in constant expression
array<int, 10 / 0> bytes;   // invalid for the same reason
```

A shift whose constant right operand is negative is not folded; it is rejected later by the operator rules (see [Operators](16-operators.md)).