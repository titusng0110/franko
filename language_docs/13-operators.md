# 13. Operators

Franko operators apply primarily to integer expressions.

Pointers have special comparison rules.

Arrays are not arithmetic values.

## 13.1 Operator Precedence

From highest precedence to lowest:

1. postfix expressions, function calls, array access `a[i]`, `$(a)`
2. unary `-x`, `!x`, `~x`
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

## 13.2 Unary Operators

Supported unary operators:

- logical NOT: `!x`
- bitwise NOT: `~x`
- unary minus: `-x`

Unary minus and bitwise NOT are also valid on unsigned integer operands. The result keeps the operand's type; narrowing to a target width happens through the ordinary contextual constant checks and explicit conversions.

For a nonconstant operand, bitwise NOT complements the value in that fixed width. A folded constant follows the arbitrary-precision rules in [Constant Folding](08-constant-folding.md): `~0` folds to `-1`, not to an all-bits-set value of the eventual target type.

Unsupported unary operators:

```franko
+x
```

## 13.3 Arithmetic Operators

Arithmetic operators: `+`, `-`, `*`, `/`, `%`.

Operands must be integers. Arrays and pointers are invalid.

`%` is the modulus (remainder) operator. Integer division truncates toward zero, and the remainder takes the sign of the dividend:

```franko
7 % 3      // 1
-7 % 3     // -1
7 % -3     // 1
-7 % -3    // -1
```

Division by zero in a compile-time constant expression is an error; see [Constant Folding](08-constant-folding.md).

This applies to `%` as well as `/`, and applies even when only the divisor is constant: any expression with a constant zero divisor, such as `x / 0` or `x % 0` where `x` is a nonconstant variable, is rejected at compile time.

Each arithmetic operator has a compound assignment form (`+=`, `-=`, `*=`, `/=`, `%=`) with exactly the same operand rules; see [Assignment](16-assignment.md).

## 13.4 Bitwise Operators

Bitwise operators: binary `&`, `|`, `^` and unary `~`.

Operands must be integers. Unary `~` preserves its operand's type.

Each binary bitwise operator has a compound assignment form (`&=`, `|=`, `^=`) with exactly the same operand rules; see [Assignment](16-assignment.md).

## 13.5 Shift Operators

Shift operators: `<<`, `>>`.

The left operand must be an integer.

The right operand must be an integer. Nonconstant shift counts must have an unsigned integer type. Constant shift counts must be nonnegative and must fit the unsigned variant of the left operand's primitive type.

## 13.6 Comparison Operators

Comparison operators produce `uint8`.

Integer comparisons may use mixed integer types subject to constant fitting rules.

Enum comparisons require both operands to have the exact same enum
type; all six operators (`==`, `!=`, `<`, `>`, `<=`, `>=`) are allowed
and compare backing integer values. Comparing an enum to an integer,
to `null`, to a pointer, to a struct, or to a different enum type is an
error. See [Enums](30-enums.md).

Pointer comparison is supported only between identical pointer types, and only with the equality operators `==` and `!=`. Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for pointers.

The `null` literal may be compared with a pointer using `==` and `!=` only. `null` cannot be compared with non-pointer values, and `0` is not `null`.

## 13.7 Logical Operators

Logical operators: `&&`, `||`, `!`.

Operands must be integers. Results are `uint8` values `0` or `1`.

## 13.8 No Pointer Arithmetic

Pointers are not integers and cannot participate in arithmetic, bitwise, or shift operations.

## 13.9 Result Types

Arithmetic (`+`, `-`, `*`, `/`), bitwise (`&`, `|`, `^`), and shift (`<<`, `>>`) expressions take the type of their nonconstant operand when exactly one operand is a folded integer constant, and the left operand's type otherwise (including when both operands are constants). Unary `~` preserves its operand's type.

Comparisons and logical operators always produce `uint8` regardless of operand types.

## 13.10 Struct Operator Overloading

Only structs can overload operators, and only comparison and assignment
operators participate. Free functions cannot use operator method names.
Operator methods are ordinary struct methods named exactly:

| Operator | Method |
|---|---|
| `==` | `OPERATOR_EQ` |
| `!=` | `OPERATOR_NEQ` |
| `>` | `OPERATOR_GT` |
| `<` | `OPERATOR_LT` |
| `>=` | `OPERATOR_GTEQ` |
| `<=` | `OPERATOR_LTEQ` |
| `=` | `OPERATOR_MOV` |
| `+=` | `OPERATOR_ADDMOV` |
| `-=` | `OPERATOR_SUBMOV` |
| `*=` | `OPERATOR_MULMOV` |
| `/=` | `OPERATOR_DIVMOV` |
| `%=` | `OPERATOR_MODMOV` |
| `\|=` | `OPERATOR_ORMOV` |
| `&=` | `OPERATOR_ANDMOV` |
| `^=` | `OPERATOR_XORMOV` |

Comparison operators require exactly:

```franko
func OPERATOR_EQ(view<X> self, view<X> other) -> uint8
```

Both operands must be values of the same nominal struct type `X`.
`view<X>`, `mview<X>`, and `ptr<X>` operands do not participate in struct
comparison; dereference or copy the value first. Comparing structs of
different types, or a struct with a non-struct, is an error.

There is no implicit fallback between operators: `!=` needs
`OPERATOR_NEQ`, and each ordering operator needs its own method. An
operator comparison still produces the method's declared return type,
which a valid method must declare as `uint8`.

Assignment operators use the mapping in the table and require:

```franko
func OPERATOR_MOV(ptr<X> self, view<T> other) -> void
```

`T` may be any non-void value type, and multiple overloads are allowed.
For plain `=`, the compiler first tries an applicable `OPERATOR_MOV`
overload; if none applies it falls back to the normal whole-struct
assignment rules. Compound assignment has no built-in struct fallback:
`a += b` requires a matching `OPERATOR_ADDMOV`.

A struct that is value-assignable (all fields value-assignable, recursively)
**cannot declare `OPERATOR_MOV`**: its whole-value assignment is always the
built-in field copy. This is a declaration error even if the operator is never
used. Compound assignment methods (`OPERATOR_ADDMOV` … `OPERATOR_XORMOV`)
remain allowed. Pointer-only structs may still declare `OPERATOR_MOV` to opt
into custom whole-value assignment; see
[Operator Assignment](16-assignment.md#167-operator-assignment).

Operator methods may still be called explicitly:

```franko
a.OPERATOR_EQ(getview(b))
```

Operator operands may be arbitrary value expressions. Lvalue operands use
their address; rvalue operands are borrowed for the duration of the full
expression. User-written `getview`
still requires a storage-backed lvalue, so `getview(5)` and
`getview(make_x())` remain invalid. The left-hand side of an assignment
must still be a storage-backed lvalue.

The right-hand operand is always borrowed as `view<T>` where `T` is its
static type. A view value therefore needs a `view<view<...>>` parameter:

```franko
OPERATOR_MOV(ptr<X> self, view<[byte]> other)        // x = arr
OPERATOR_MOV(ptr<X> self, view<view<[byte]>> other)  // x = "hello"
```

When the parameter is itself a view of a view, overload resolution checks
the parameter's inner referenced type and applies the ordinary pointer
conversion there. Thus the inner `view<[byte, 10]>` value decays to
`view<[byte]>` for a `view<view<[byte]>>` parameter.

Integer constants use the ordinary fluid-constant fitting rules against
`view<primitive>` parameters: an exact natural-type match wins, otherwise
the narrowest fitting type wins with signed before unsigned at equal
width.

Tuple values have no operator methods and cannot use the struct `OPERATOR_*` overloading mechanism. Arithmetic, bitwise, shift, logical, comparison, and compound-assignment operators reject tuple operands just like whole struct values. Whole-tuple assignment is the only operator-like operation a tuple supports, and it always uses the built-in assignment rule.

Enum values have no operator methods either: their `==`, `!=`, `<`,
`>`, `<=`, `>=`, and `=` are built in and require the exact same enum
type on both sides. Arithmetic, bitwise, shift, logical, and
compound-assignment operators reject enum operands. See [Enums](30-enums.md).

See [Assignment](16-assignment.md) and [Methods](27-methods.md) for the
full operator rules.
