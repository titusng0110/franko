# 17. Operators

Franko operators apply primarily to integer expressions.

Pointers have special comparison rules.

Arrays are not arithmetic values.

## 17.1 Operator Precedence

From highest precedence to lowest:

1. postfix expressions, function calls, array access `a[i]`, `$(a)`
2. unary `-x`, `!x`
3. multiplicative `*`, `/`, `%`
4. additive `+`, `-`
5. shift `<<`, `>>`
6. relational `<`, `<=`, `>`, `>=`
7. equality `==`, `!=`
8. bitwise AND `&`
9. bitwise XOR `^`
10. bitwise OR `|`
11. logical AND `&&`
12. logical OR `||`

Binary operators are intended to associate left-to-right.

## 17.2 Unary Operators

Supported unary operators:

- logical NOT: `!x`
- unary minus: `-x`

Unary minus is also valid on unsigned integer operands. The result keeps the operand's type; narrowing to a target width happens through the ordinary contextual constant checks and the generated C++ casts.

Unsupported unary operators:

```franko
~x
+x
```

## 17.3 Arithmetic Operators

Arithmetic operators: `+`, `-`, `*`, `/`, `%`.

Operands must be integers. Arrays and pointers are invalid.

`%` is the modulus (remainder) operator. Integer division truncates toward zero, and the remainder takes the sign of the dividend:

```franko
7 % 3      // 1
-7 % 3     // -1
7 % -3     // 1
-7 % -3    // -1
```

Division by zero in a compile-time constant expression is an error; see [Constant Folding](10-constant-folding.md).

This applies to `%` as well as `/`, and applies even when only the divisor is constant: any expression with a constant zero divisor, such as `x / 0` or `x % 0` where `x` is a nonconstant variable, is rejected at compile time.

Each arithmetic operator has a compound assignment form (`+=`, `-=`, `*=`, `/=`, `%=`) with exactly the same operand rules; see [Assignment](14-assignment.md).

## 17.4 Bitwise Operators

Bitwise operators: `&`, `|`, `^`.

Operands must be integers.

## 17.5 Shift Operators

Shift operators: `<<`, `>>`.

The left operand must be an integer.

The right operand must be an integer. Nonconstant shift counts must have an unsigned integer type. Constant shift counts must be nonnegative and must fit the unsigned variant of the left operand's primitive type.

## 17.6 Comparison Operators

Comparison operators produce `uint8_t`.

Integer comparisons may use mixed integer types subject to constant fitting rules.

Pointer comparison is supported only between identical pointer types, and only with the equality operators `==` and `!=`. Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for pointers.

The `null` literal may be compared with an pointer using `==` and `!=` only. `null` cannot be compared with non-pointer values, and `0` is not `null`.

## 17.7 Logical Operators

Logical operators: `&&`, `||`, `!`.

Operands must be integers. Results are `uint8_t` values `0` or `1`.

## 17.8 No Pointer Arithmetic

Pointers are not integers and cannot participate in arithmetic, bitwise, or shift operations.

## 17.9 Result Types

Arithmetic (`+`, `-`, `*`, `/`), bitwise (`&`, `|`, `^`), and shift (`<<`, `>>`) expressions take the type of their nonconstant operand when exactly one operand is a folded integer constant, and the left operand's type otherwise (including when both operands are constants).

Comparisons and logical operators always produce `uint8_t` regardless of operand types.
