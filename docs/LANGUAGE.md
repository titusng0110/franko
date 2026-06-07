# Franko Language Reference

This document describes the Franko language as implemented by the current compiler pipeline:

```text
Franko source
  -> parser AST
  -> desugared AST
  -> SemanticAnalyzer
  -> MasterChecker
  -> C++14 code generation
```

It documents both intended language behavior and important current implementation restrictions.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Source Files](#2-source-files)
3. [Program Structure](#3-program-structure)
4. [Lexical Scoping and Symbols](#4-lexical-scoping-and-symbols)
5. [Desugaring](#5-desugaring)
6. [Types](#6-types)
7. [Primitive Integer Types](#7-primitive-integer-types)
8. [Integer Literals](#8-integer-literals)
9. [Fluid Integer Constants](#9-fluid-integer-constants)
10. [Constant Folding](#10-constant-folding)
11. [Declarations](#11-declarations)
12. [Heap Variables and `alloc`](#12-heap-variables-and-alloc)
13. [Assignment](#13-assignment)
14. [Lvalues and Storage-Backed Expressions](#14-lvalues-and-storage-backed-expressions)
15. [Operators](#15-operators)
16. [Conditions](#16-conditions)
17. [Statements](#17-statements)
18. [Arrays](#18-arrays)
19. [Array Intrinsics](#19-array-intrinsics)
20. [Addresses](#20-addresses)
21. [`del` and Delete Checking](#21-del-and-delete-checking)
22. [Type Equality](#22-type-equality)
23. [Declaration Type Validity](#23-declaration-type-validity)
24. [Unsupported or Restricted Features](#24-unsupported-or-restricted-features)
25. [Runtime and Backend Notes](#25-runtime-and-backend-notes)
26. [Examples](#26-examples)

---

# 1. Overview

Franko is a small statically typed language with:

- fixed-width signed and unsigned integer types,
- dynamic arrays,
- static arrays,
- typed addresses using `addr<T>`,
- explicit address operations:
  - `getaddr(...)`,
  - `deref(...)`,
- assignment,
- blocks,
- `if`,
- `while`,
- `print`,
- `del`,
- heap-owned variables using `alloc`,
- array intrinsics:
  - `arr(size)`,
  - `arr.uninit()`,
  - `arr.memset(value)`,
  - `arr.memcpy(source)`.

Franko currently does **not** implement:

- user-defined functions,
- general function calls,
- structs/classes,
- fields,
- floating-point types,
- a distinct boolean type,
- strings as a checked semantic type,
- implicit numeric conversions except for fluid integer constants.

Franko currently targets C++14.

---

# 2. Source Files

Franko source files commonly use the `.fr` extension.

Example:

```franko
int32_t x;
x = 1;

print(x);
```

---

# 3. Program Structure

A Franko program is a sequence of statements.

```franko
int32_t x;
x = 10;

if (x > 0) {
    print(x);
}
```

Blocks introduce nested lexical scopes:

```franko
int32_t x;

{
    int32_t y;
    y = 1;
}

x = 2;
```

Variables declared inside a block are not visible outside that block.

---

# 4. Lexical Scoping and Symbols

Franko uses lexical scope.

## 4.1 Declaration Lookup

Variable references resolve to the nearest declaration in the current scope stack.

```franko
int32_t x;

{
    int32_t y;
    x = 1; // refers to outer x
    y = 2; // refers to inner y
}
```

## 4.2 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

```franko
int32_t x;
uint8_t x; // invalid
```

## 4.3 Shadowing

Shadowing in an inner block is allowed.

```franko
int32_t x;

{
    uint8_t x; // allowed: different inner scope
    x = 1;     // refers to inner x
}

x = 2;         // refers to outer x
```

## 4.4 Use Before Declaration

A variable must be declared before it is used.

Invalid:

```franko
x = 10; // invalid if x has not been declared
```

## 4.5 Undeclared Variables

Using an undeclared variable is illegal.

```franko
x = 1; // invalid if x was never declared
```

---

# 5. Desugaring

Some Franko source forms are accepted as convenient syntax and rewritten before semantic checking.

Desugaring does not change program meaning. It rewrites compact user-facing syntax into simpler core forms used by the semantic analyzer.

After desugaring, normal declaration, assignment, array initialization, heap, and type checking rules apply.

## 5.1 Declaration Initializer Sugar

This:

```franko
int x = 10;
```

is accepted and desugared to:

```franko
int x;
x = 10;
```

So:

```franko
int x = 10;
```

is legal Franko source, but semantically behaves exactly like a declaration followed by an assignment.

The initializer must obey normal assignment rules.

Valid:

```franko
uint8_t x = 255;
```

Invalid:

```franko
uint8_t x = 256; // invalid: 256 does not fit uint8_t
```

## 5.2 Allocated Dynamic Array Initialization Sugar

This:

```franko
alloc array<int> arr(20);
```

is accepted and desugared to:

```franko
alloc array<int> arr;
arr(20);
```

So:

```franko
alloc array<int> arr(20);
```

means:

1. declare `arr` as a heap-owned dynamic array of `int`,
2. initialize `arr` with dynamic size `20`.

The desugared `arr(20);` still follows the normal dynamic array initialization rules.

---

# 6. Types

Franko has the following semantic type categories:

```text
primitive integers
dynamic arrays
static arrays
typed addresses
```

There is currently no separate semantic boolean type. Boolean-like results are represented as `uint8_t`.

---

# 7. Primitive Integer Types

Franko supports fixed-width signed and unsigned integer types.

| Type | Meaning | Range |
|---|---|---|
| `int8_t` | signed 8-bit integer | `-128` to `127` |
| `int16_t` | signed 16-bit integer | `-32768` to `32767` |
| `int32_t` | signed 32-bit integer | `-2147483648` to `2147483647` |
| `int64_t` | signed 64-bit integer | `-9223372036854775808` to `9223372036854775807` |
| `uint8_t` | unsigned 8-bit integer | `0` to `255` |
| `uint16_t` | unsigned 16-bit integer | `0` to `65535` |
| `uint32_t` | unsigned 32-bit integer | `0` to `4294967295` |
| `uint64_t` | unsigned 64-bit integer | `0` to `18446744073709551615` |

## 7.1 Integer Type Aliases

Franko supports the convenient alias:

| Alias | Canonical type |
|---|---|
| `int` | `int32_t` |

Therefore these declarations are equivalent:

```franko
int x;
int32_t x;
```

The `int` spelling is accepted for convenience, but the compiler treats it as `int32_t`.

Example:

```franko
int x = 10;
```

is equivalent to:

```franko
int32_t x = 10;
```

which is then desugared to:

```franko
int32_t x;
x = 10;
```

If the parser defines aliases such as `char`, they should be understood as aliases to canonical primitive types. In the current semantic model, `char` behavior corresponds to `uint8_t`.

## 7.2 Boolean-Like Values

Franko currently does not have a separate `bool` type.

Boolean-like results from comparison and logical operators use `uint8_t`, with:

```text
0 = false
1 = true
```

---

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

---

# 9. Fluid Integer Constants

Integer literals and folded constant integer expressions are **fluid**.

This means their initial semantic type is not always binding. Instead, when a constant is used in a typed context, the compiler checks whether the constant value fits that context.

Example:

```franko
uint8_t x;

x = 255; // valid
x = 256; // invalid: 256 does not fit uint8_t
```

Another example:

```franko
uint8_t x;
x = 10;

x = x + 1;   // valid: 1 fits uint8_t
x = x + 255; // valid: 255 fits uint8_t
x = x + 256; // invalid: 256 does not fit uint8_t
```

Pure constant expressions remain fluid until used in a contextual position:

```franko
uint8_t x;

x = 1 + 2; // valid: folded to 3, fits uint8_t
x = 1000;  // invalid: does not fit uint8_t
```

# 10. Constant Folding

The semantic analyzer folds pure integer constant expressions when safe.

Examples:

```franko
1 + 2       // folded to 3
4 * 5       // folded to 20
10 == 10    // folded to 1
!0          // folded to 1
```

Folding is conservative.

If folding would require invalid arithmetic, the expression is not folded and later checking reports the error where applicable.

Examples:

```franko
1 / 0   // division by zero error during checking
1 << -1 // invalid shift count
```

Constants are folded using arbitrary-precision `BigInteger`, not fixed-width wrapping arithmetic.

Integer overflow is not applied during folding. Range checking happens when the value is used in a concrete type context.

---

# 11. Declarations

A variable declaration introduces a named storage location.

Basic declaration syntax:

```franko
int x;
int32_t y;
uint8_t b;
uint32_t i;
```

Array and address declarations:

```franko
array<int> xs;
array<int32_t> ys;
array<uint8_t, 128> buffer;

addr<int> p;
addr<int32_t> q;
addr<array<int>> arrayPtr;
```

Declaration initializers are syntax sugar:

```franko
int32_t x = 1;
```

is equivalent to:

```franko
int32_t x;
x = 1;
```

The initializer must obey normal assignment rules.

## 11.1 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

Invalid:

```franko
int x;
uint8_t x; // invalid: duplicate declaration in the same scope
```

## 11.2 Shadowing

A variable may be redeclared in an inner block, creating a new variable that shadows the outer one.

```franko
int x;

{
    uint8_t x;
    x = 1; // refers to inner x
}

x = 2; // refers to outer x
```

## 11.3 Use Before Declaration

A variable must be declared before it is used.

Invalid:

```franko
x = 10; // invalid if x has not been declared
```

---

# 12. Heap Variables and `alloc`

Franko supports heap-owned variables using `alloc`.

Syntax:

```franko
alloc int x;
alloc int32_t y;
alloc array<int> arr;
```

The `alloc` keyword marks the variable as heap-owned. Heap-owned variables may be deleted with `del`.

Example:

```franko
alloc int x;

x = 10;
del x;
```

Non-heap variables cannot be deleted.

Invalid:

```franko
int x;

del x; // invalid: x is not heap-owned
```

## 12.1 Allocated Dynamic Array Initialization Sugar

Allocated dynamic arrays may use compact declaration-plus-initialization syntax:

```franko
alloc array<int> arr(20);
```

This is desugared to:

```franko
alloc array<int> arr;
arr(20);
```

The `alloc` part affects heap ownership and delete rules.

The `arr(20);` part is ordinary dynamic array initialization after desugaring.

Therefore this:

```franko
alloc array<int> arr(20);
```

means:

1. declare `arr` as a heap-owned dynamic array of `int`,
2. initialize `arr` with dynamic size `20`.

The size expression must satisfy normal dynamic array size rules.

---

# 13. Assignment

Assignment syntax:

```franko
target = value;
```

The left-hand side must be a storage-backed lvalue.

Valid assignment targets include:

- variables,
- array elements,
- dereferenced addresses.

Examples:

```franko
int32_t x;
x = 1;

array<int32_t> arr;
arr(10);
arr[0] = 42;

addr<int32_t> p;
p = getaddr(x);
deref(p) = 100;
```

Invalid assignment targets:

```franko
1 = x;           // invalid
x + y = 3;       // invalid
getaddr(x) = p;  // invalid
```

## 13.1 Primitive Assignment

A primitive integer variable may be assigned:

1. a nonconstant expression of exactly the same primitive type, or
2. a constant expression that fits the target type.

Valid:

```franko
uint8_t x;
x = 255;
```

Invalid:

```franko
uint8_t x;
x = 256; // does not fit uint8_t
```

For nonconstant expressions, exact type equality is required.

Invalid:

```franko
uint8_t a;
uint32_t b;

a = b; // invalid
```

## 13.2 Address Assignment

Address assignment requires exact address type equality.

Valid:

```franko
int32_t x;
addr<int32_t> p;
addr<int32_t> q;

p = getaddr(x);
q = p;
```

Invalid:

```franko
int32_t x;
uint8_t b;

addr<int32_t> p;
addr<uint8_t> q;

p = q; // invalid
p = 0; // invalid
```

Raw integers cannot be converted to addresses.

Invalid:

```franko
addr<int32_t> p;

p = 0;
p = 123;
p = 0xdeadbeef;
```

## 13.3 Array Assignment

Arrays cannot be directly assigned.

Invalid:

```franko
array<int32_t> a;
array<int32_t> b;

a = b; // invalid
```

Use array intrinsics instead:

```franko
a.memcpy(b);
a.memset(0);
a.uninit();
```

depending on the intended operation.

---

# 14. Lvalues and Storage-Backed Expressions

A storage-backed lvalue is an expression that refers to a real mutable storage location.

Current storage-backed lvalues are:

| Expression | Lvalue? |
|---|---|
| variable | yes |
| array element of storage-backed array | yes |
| `deref(address)` | yes |
| integer literal | no |
| arithmetic expression | no |
| comparison expression | no |
| `getaddr(...)` result | no |

Valid:

```franko
x = 1;
arr[0] = 2;
deref(p) = 3;
```

Invalid:

```franko
(x + y) = 1;
getaddr(x) = p;
```

This distinction matters for:

- assignment targets,
- `getaddr(...)` operands,
- array intrinsic receivers,
- `memcpy` sources.

---

# 15. Operators

Franko operators apply primarily to integer expressions.

Addresses have special comparison rules.

Arrays are not arithmetic values.

---

## 15.1 Operator Precedence

From highest precedence to lowest:

| Precedence | Operators / Forms |
|---:|---|
| 1 | postfix expressions, array access `a[i]`, `deref(a)` |
| 2 | unary `-x`, `!x` |
| 3 | multiplicative `*`, `/` |
| 4 | additive `+`, `-` |
| 5 | shift `<<`, `>>` |
| 6 | relational `<`, `<=`, `>`, `>=` |
| 7 | equality `==`, `!=` |
| 8 | bitwise AND `&` |
| 9 | bitwise XOR `^` |
| 10 | bitwise OR `|` |
| 11 | logical AND `&&` |
| 12 | logical OR `||` |

Binary operators are intended to associate left-to-right.

---

## 15.2 Unary Operators

### 15.2.1 Logical NOT

Syntax:

```franko
!x
```

Rules:

- operand must be an integer,
- zero is false,
- nonzero is true,
- result type is `uint8_t`,
- result value is `0` or `1`.

Example:

```franko
uint8_t b;
b = !0; // b = 1
```

### 15.2.2 Unary Minus

Syntax:

```franko
-x
```

Rules:

- operand must be an integer,
- result type is the operand type,
- constants are folded as arbitrary-precision values before contextual checking.

Example:

```franko
int32_t x;
x = -1;
```

### 15.2.3 Unsupported Unary Operators

Unsupported unary operators include:

```franko
~x // not currently implemented
+x // not currently implemented as a semantic unary operator
```

Note that `+123` may be accepted as a literal form, but unary `+x` is not a supported semantic unary operator.

---

## 15.3 Arithmetic Operators

Arithmetic operators:

```franko
+
-
*
/
```

Rules:

- operands must be integers,
- arrays are invalid,
- addresses are invalid,
- both nonconstant operands must have exactly the same integer type,
- constants must fit the other side's concrete type,
- if exactly one operand is constant and the other operand is nonconstant, the result type is the nonconstant operand's type,
- if both operands are nonconstant, they must have exactly the same primitive integer type, and the result type is that shared type,
- if both operands are constant, the expression is folded when possible and remains a fluid constant until used in a contextual position.


Valid:

```franko
int32_t x;
int32_t y;

x = y + 1;
```

Invalid:

```franko
int32_t x;
uint32_t y;

x = x + y; // invalid: mixed nonconstant arithmetic
```

Invalid:

```franko
addr<int32_t> p;

p + 1; // invalid: address arithmetic is not allowed
```

Constant division by zero is rejected:

```franko
int32_t x;
x = 1 / 0; // invalid
```

The modulus operator `%` is not currently implemented.

---

## 15.4 Bitwise Operators

Bitwise operators:

```franko
&
|
^
```

Rules:

- operands must be integers,
- arrays are invalid,
- addresses are invalid,
- both nonconstant operands must have exactly the same integer type,
- constants must fit the other side's concrete type,
- if exactly one operand is constant and the other operand is nonconstant, the result type is the nonconstant operand's type,
- if both operands are nonconstant, they must have exactly the same primitive integer type, and the result type is that shared type,
- if both operands are constant, the expression is folded when possible and remains a fluid constant until used in a contextual position.

Valid:

```franko
uint8_t x;
uint8_t y;

x = x & y;
x = x | 1;
```

Invalid:

```franko
uint8_t x;
uint32_t y;

x = x & y; // invalid
```

---

## 15.5 Shift Operators

Shift operators:

```franko
<<
>>
```

Rules:

* left operand must be an integer,
* right operand must be an integer,
* if the right operand is a nonconstant expression, it must have an unsigned integer type,
* if the right operand is a constant, it must be nonnegative and must fit the unsigned variant of the left operand's type,
* if the left operand is a constant and the right operand is nonconstant, the left constant must fit the right operand's concrete type,
* if exactly one operand is constant and the other operand is nonconstant, the result type is the nonconstant operand's type,
* if both operands are nonconstant, the result type is the left operand's type; the right operand is only the shift count,
* if both operands are constant, the expression is folded when possible and remains a fluid constant until used in a contextual position.


Examples:

```franko
int32_t x;

x = x << 3; // valid
x = x >> 1; // valid
```

Invalid:

```franko
int32_t x;

x = x << -1; // invalid: negative shift count
```

For `uint8_t` left operands:

```franko
uint8_t b;

b = b << 7;   // valid
b = b << 255; // valid
b = b << 256; // invalid: 256 does not fit uint8_t
```

For nonconstant shift counts:

```franko
int32_t x;
uint32_t amount;

x = x << amount; // valid
```

Invalid:

```franko
int32_t x;
int32_t amount;

x = x << amount; // invalid: nonconstant shift count must be unsigned
```

When the left operand is a constant and the right operand is nonconstant, the result type is inferred from the nonconstant right operand, but the right operand must still be an unsigned integer type.

Valid:

```franko
uint8_t amount;

amount = 3;

uint8_t y;
y = 1 << amount; // valid: result type is uint8_t, and 1 fits uint8_t
```

Invalid:

```franko
uint8_t amount;

amount = 3;

uint8_t y;
y = 999 << amount; // invalid: 999 does not fit uint8_t
```

Invalid:

```franko
int32_t amount;

amount = 3;

int32_t y;
y = 1 << amount; // invalid: nonconstant shift count must be unsigned
```

For pure constant shifts, the expression remains a fluid constant until used in a contextual position:

```franko
uint8_t b;

b = 1 << 3; // valid: folded to 8, and 8 fits uint8_t
b = 1 << 8; // invalid when assigned to uint8_t: folded to 256, which does not fit uint8_t
```

---

## 15.6 Comparison Operators

Comparison operators:

```franko
==
!=
<
>
<=
>=
```

For integer operands:

- operands may be any integer types,
- mixed integer types are allowed,
- if one side is a constant and the other side is nonconstant, the constant must fit the nonconstant side's concrete type,
- result type is `uint8_t`,
- result value is `0` or `1`.

Valid:

```franko
uint8_t a;
uint32_t b;

if (a < b) {
    print(1);
}
```

Invalid:

```franko
uint8_t a;

if (a == 999) {
    print(1);
}
```

`999` does not fit `uint8_t`.

Address comparison is also supported, but only between identical address types. See [Address Comparison](#209-address-comparison).

---

## 15.7 Logical Operators

Logical operators:

```franko
&&
||
!
```

Rules for `&&` and `||`:

- operands must be integers,
- mixed integer types are allowed,
- constants must fit the other side's concrete type when compared with a nonconstant,
- zero is false,
- nonzero is true,
- result type is `uint8_t`,
- result value is `0` or `1`,
- `&&` and `||` are intended to short-circuit.

Example:

```franko
int32_t x;
uint8_t b;

if (x && b) {
    print(1);
}
```

Addresses are not valid operands to logical operators.

---

## 15.8 No Address Arithmetic

Addresses are not integers.

Invalid:

```franko
addr<int32_t> p;

p + 1;
p - 1;
p * 2;
p / 2;
p & p;
p | p;
p ^ p;
p << 1;
p >> 1;
```

Also invalid:

```franko
deref(p + 1);
```

Franko intentionally does not support pointer arithmetic.

---

# 16. Conditions

`if` and `while` conditions must be integer expressions.

Valid:

```franko
int32_t x;

if (x) {
    print(x);
}
```

Valid:

```franko
int32_t x;
int32_t y;

if (x < y) {
    print(1);
}
```

Invalid:

```franko
array<int32_t> arr;

if (arr) {
    print(1);
}
```

Invalid:

```franko
addr<int32_t> p;

if (p) {
    print(1);
}
```

To test addresses, compare them explicitly:

```franko
if (p == q) {
    print(1);
}
```

---

# 17. Statements

Franko supports:

- variable declarations,
- assignments,
- blocks,
- `if`,
- `while`,
- `print`,
- `del`,
- expression statements,
- array intrinsic statement forms.

---

## 17.1 Blocks

A block is a sequence of statements enclosed in braces:

```franko
{
    int32_t x;
    x = 1;
    print(x);
}
```

Blocks create a new lexical scope.

---

## 17.2 If Statements

Syntax:

```franko
if (condition) {
    statements
}
```

With `else`:

```franko
if (condition) {
    statements
} else {
    statements
}
```

The condition must be an integer expression.

Example:

```franko
int32_t x;

if (x > 0) {
    print(1);
} else {
    print(0);
}
```

---

## 17.3 While Statements

Syntax:

```franko
while (condition) {
    statements
}
```

The condition must be an integer expression.

Example:

```franko
uint32_t i;
i = 0;

while (i < 10) {
    print(i);
    i = i + 1;
}
```

---

## 17.4 Print Statements

Syntax:

```franko
print(expr);
print(expr1, expr2, expr3);
```

The current checker validates the expressions but does not impose a strict print-type policy.

Examples:

```franko
int32_t x;
x = 42;

print(x);
print(1, 2, 3);
```

Current limitation: printing arrays or other complex values may depend on backend code generation support. The semantic checker currently does not reject every backend-unprintable expression.

---

## 17.5 Expression Statements

A legal expression may appear as a statement.

Example:

```franko
x + 1;
```

However, many expression statements are only useful for their side effects, and most Franko expressions have no side effects.

Array intrinsic calls are special expression-statement forms:

```franko
arr(10);
arr.memset(0);
arr.memcpy(other);
arr.uninit();
```

General function calls are not currently implemented.

Invalid:

```franko
foo(1, 2);
```

unless `foo(1, 2)` matches a recognized intrinsic form.

---

# 18. Arrays

Franko supports two array categories:

```franko
array<T>      // dynamic array
array<T, N>   // static array
```

Examples:

```franko
array<int32_t> dynamicInts;
array<uint8_t, 128> staticBytes;
```

Arrays may be nested:

```franko
array<array<int32_t>> nestedDynamic;
array<array<int32_t, 4>, 8> matrix;
```

Arrays are not arithmetic values and cannot be directly assigned.

---

## 18.1 Static Arrays

A static array has a compile-time size:

```franko
array<int32_t, 10> xs;
```

Static array size rules:

- size must be a valid integer literal,
- size must be greater than zero,
- size must fit `uint32_t`.

Valid:

```franko
array<int32_t, 1> a;
array<int32_t, 0x10> b;
array<int32_t, 0b1000> c;
```

Invalid:

```franko
array<int32_t, 0> a;  // invalid
array<int32_t, -1> b; // invalid
array<int32_t, 999999999999999999999999999999999> c; // invalid
```

Static arrays do not use `arr(size)` initialization.

Invalid:

```franko
array<int32_t, 10> xs;

xs(10); // invalid: array init is only for dynamic arrays
```

---

## 18.2 Dynamic Arrays

A dynamic array has runtime size:

```franko
array<int32_t> xs;
```

Dynamic arrays are initialized using direct-call array initialization syntax:

```franko
xs(10);
```

Rules for dynamic array initialization:

- target must be a storage-backed lvalue,
- target must be a dynamic array,
- exactly one argument is required,
- size must be compatible with `uint32_t`,
- constant sizes must be greater than zero,
- constant sizes must fit `uint32_t`,
- nonconstant sizes must have exactly type `uint32_t`.

Valid:

```franko
array<int32_t> xs;

xs(10);
```

Valid:

```franko
array<int32_t> xs;
uint32_t n;

n = 100;
xs(n);
```

Invalid:

```franko
array<int32_t> xs;
int32_t n;

xs(n); // invalid: nonconstant size must be uint32_t
```

Invalid:

```franko
array<int32_t> xs;

xs(0); // invalid: size must be greater than zero
```

Static arrays are different:

```franko
array<int, 20> arr;
```

Static arrays already have their size in the type and are not initialized with `arr(size)`.

Invalid:

```franko
array<int, 20> arr;

arr(20); // invalid: arr is static, not dynamic
```

## 18.3 Array Access

Array indexing syntax:

```franko
arr[index]
```

The target must be an array.

Index rules:

- constant index:
  - must be nonnegative,
  - must fit `uint32_t`;
- nonconstant index:
  - must have exactly type `uint32_t`.

Valid:

```franko
array<int32_t> xs;
xs(10);

xs[0] = 1;
```

Valid:

```franko
array<int32_t> xs;
uint32_t i;

xs(10);
i = 3;

xs[i] = 42;
```

Invalid:

```franko
array<int32_t> xs;
int32_t i;

xs[i] = 42; // invalid: nonconstant index must be uint32_t
```

Invalid:

```franko
array<int32_t> xs;

xs[-1] = 42; // invalid
```

Current limitation: the checker validates index type/range but does not generally prove that an index is within the actual array length.

---

## 18.4 Array Elements as Lvalues

Array elements are storage-backed lvalues when the array target is storage-backed.

Valid:

```franko
array<int32_t> xs;
xs(10);

xs[0] = 123;
```

Array elements may be addressed:

```franko
addr<int32_t> p;

p = getaddr(xs[0]);
```

Nested array elements may also be lvalues when each target is storage-backed.

---

## 18.5 Array Direct Assignment Is Invalid

Arrays cannot be assigned directly.

Invalid:

```franko
array<int32_t> a;
array<int32_t> b;

a = b;
```

Use one of:

```franko
a.memcpy(b);
a.memset(0);
a.uninit();
```

depending on the intended operation.

---

## 18.6 Array Lifetime Limitations

The checker validates intrinsic legality, but it does not fully prove:

- dynamic array initialized before indexing,
- dynamic array not used after `uninit`,
- dynamic array not initialized twice,
- all array accesses are within bounds.

---

# 19. Array Intrinsics

Array operations are written using call syntax but lowered into special semantic nodes.

Supported array intrinsics:

```franko
target(size)
target.uninit()
target.memset(value)
target.memcpy(source)
```

These are not general function calls.

They are only recognized in statement position.

Valid:

```franko
arr.memset(0);
```

Invalid:

```franko
x = arr.memset(0); // invalid: intrinsic is not an expression value
```

The receiver or target of an array intrinsic must usually be a storage-backed array lvalue. This means the operation must apply to real mutable storage, such as:

* a variable,
* a dereferenced address,
* an array element whose containing array expression is storage-backed.

Examples of storage-backed array targets:

```franko
arr
deref(p)
arrs[i]
```

Examples that are not storage-backed array targets:

```franko
getaddr(arr)
x + y
1
```

***

## 19.1 `target(size)`

Dynamic array initialization.

Example:

```franko
array<int32_t> arr;

arr(100);
```

Rules:

* available only on dynamic arrays,
* target must be a storage-backed lvalue,
* target must have dynamic array type,
* exactly one argument is required,
* size must be:
  * a positive constant fitting `uint32_t`, or
  * a nonconstant expression of exactly type `uint32_t`.

Valid:

```franko
array<int32_t> arr;

arr(100);
```

Valid with a nonconstant `uint32_t` size:

```franko
array<int32_t> arr;
uint32_t n;

n = 100;
arr(n);
```

Valid through an address:

```franko
array<int32_t> arr;
addr<array<int32_t>> p;

p = getaddr(arr);

deref(p)(100);
```

This is valid because:

```franko
deref(p)
```

is a storage-backed lvalue of type:

```franko
array<int32_t>
```

Valid for nested dynamic array elements when the target element is storage-backed:

```franko
array<array<int32_t>> arrs;

arrs(3);
arrs[0](10);
```

Invalid because static arrays are not dynamically initialized:

```franko
array<int32_t, 10> arr;

arr(10); // invalid: static array
```

Invalid because the target is not a dynamic array:

```franko
int32_t x;

x(10); // invalid: x is not a dynamic array
```

Invalid argument counts:

```franko
array<int32_t> arr;

arr();     // invalid
arr(1, 2); // invalid
```

Invalid sizes:

```franko
array<int32_t> arr;

arr(-1); // invalid: size must be positive
arr(0);  // invalid: size must be positive
```

Invalid nonconstant size type:

```franko
array<int32_t> arr;
int32_t n;

n = 10;
arr(n); // invalid: nonconstant size must be uint32_t
```

***

## 19.2 `target.uninit()`

Dynamic array uninitialization.

Example:

```franko
array<int32_t> arr;

arr(100);
arr.uninit();
```

Rules:

* receiver must be a storage-backed lvalue,
* receiver must be a dynamic array,
* takes exactly zero arguments.

Valid:

```franko
array<int32_t> arr;

arr(100);
arr.uninit();
```

Valid through an address:

```franko
array<int32_t> arr;
addr<array<int32_t>> p;

arr(100);
p = getaddr(arr);

deref(p).uninit();
```

Invalid because static arrays cannot be uninitialized:

```franko
array<int32_t, 10> arr;

arr.uninit(); // invalid: static array
```

Invalid because `uninit` takes no arguments:

```franko
array<int32_t> arr;

arr.uninit(1); // invalid
```

***

## 19.3 `target.memset(value)`

Fills an array byte-wise.

Example:

```franko
array<uint8_t> arr;

arr(100);
arr.memset(0);
```

Rules:

* receiver must be a storage-backed lvalue,
* receiver may be a dynamic or static array,
* receiver element type must be memsetable,
* takes exactly one argument,
* fill value must be:
  * a constant fitting `uint8_t`, or
  * a nonconstant expression of exactly type `uint8_t`.

Valid on a dynamic array:

```franko
array<int32_t> arr;

arr(10);
arr.memset(0);
```

Valid on a static array:

```franko
array<uint8_t, 128> bytes;

bytes.memset(255);
```

Valid through an address:

```franko
array<int32_t> arr;
addr<array<int32_t>> p;

arr(10);
p = getaddr(arr);

deref(p).memset(0);
```

Invalid because the fill value does not fit `uint8_t`:

```franko
array<uint8_t, 128> bytes;

bytes.memset(256); // invalid
```

Invalid because a nonconstant fill value must have exactly type `uint8_t`:

```franko
array<uint8_t, 128> bytes;
uint32_t x;

bytes.memset(x); // invalid: nonconstant fill must be uint8_t
```

Invalid because the receiver is not an array:

```franko
int32_t x;

x.memset(0); // invalid
```

***

## 19.4 Memsetable Element Types

Current memsetable types:

| Element type                         | Memsetable? |
| ------------------------------------ | ----------- |
| primitive integers                   | yes         |
| static arrays of memsetable elements | yes         |
| dynamic arrays                       | no          |
| addresses                            | no          |

Examples:

```franko
array<int32_t> a;            // memsetable
array<array<int32_t, 4>> b;  // memsetable
array<array<int32_t>> c;     // not memsetable
array<addr<int32_t>> d;      // not memsetable
```

A static array element is memsetable only when its own element type is also memsetable.

Example:

```franko
array<array<uint8_t, 4>, 8> bytes;

bytes.memset(0); // valid
```

Invalid:

```franko
array<array<int32_t>, 8> nestedDynamicArrays;

nestedDynamicArrays.memset(0); // invalid: dynamic array elements are not memsetable
```

***

## 19.5 `target.memcpy(source)`

Copies array contents byte-wise according to the generated C++ array model.

Example:

```franko
array<int32_t> a;
array<int32_t> b;

a(10);
b(10);

a.memcpy(b);
```

Rules:

* target must be a storage-backed lvalue array,
* source must be a storage-backed lvalue array,
* both target and source may be dynamic or static arrays,
* target and source element types must match exactly,
* static/dynamic shape does not need to match,
* static lengths do not need to match,
* element type must be memcpyable.

Valid between dynamic arrays:

```franko
array<int32_t> a;
array<int32_t> b;

a(10);
b(10);

a.memcpy(b);
```

Valid between static arrays with different lengths:

```franko
array<int32_t, 10> a;
array<int32_t, 20> b;

a.memcpy(b);
```

Valid between dynamic and static arrays:

```franko
array<int32_t> d;
array<int32_t, 10> s;

d(10);

d.memcpy(s);
s.memcpy(d);
```

Valid through addresses:

```franko
array<int32_t> a;
array<int32_t> b;

addr<array<int32_t>> pa;
addr<array<int32_t>> pb;

a(10);
b(10);

pa = getaddr(a);
pb = getaddr(b);

deref(pa).memcpy(deref(pb));
```

Invalid because the element types differ:

```franko
array<int32_t> a;
array<uint8_t> b;

a.memcpy(b); // invalid: element types differ
```

Invalid because the source is not storage-backed:

```franko
array<int32_t> a;
addr<array<int32_t>> p;

a.memcpy(getaddr(a)); // invalid: source is an address, not an array lvalue
```

Invalid because `memcpy` takes exactly one argument:

```franko
array<int32_t> a;
array<int32_t> b;
array<int32_t> c;

a.memcpy();     // invalid
a.memcpy(b, c); // invalid
```

***

## 19.6 Memcpyable Element Types

Current memcpyable types:

| Element type                         | Memcpyable? |
| ------------------------------------ | ----------- |
| primitive integers                   | yes         |
| addresses                            | yes         |
| static arrays of memcpyable elements | yes         |
| dynamic arrays                       | no          |

Examples:

```franko
array<int32_t> a;            // memcpyable
array<addr<int32_t>> b;      // memcpyable
array<array<int32_t, 4>> c;  // memcpyable
array<array<int32_t>> d;     // not memcpyable
```

A static array element is memcpyable only when its own element type is also memcpyable.

Example:

```franko
array<array<int32_t, 4>, 8> matrixA;
array<array<int32_t, 4>, 8> matrixB;

matrixA.memcpy(matrixB); // valid
```

Invalid:

```franko
array<array<int32_t>, 8> a;
array<array<int32_t>, 8> b;

a.memcpy(b); // invalid: dynamic array elements are not memcpyable
```

# 20. Addresses

Franko has typed addresses:

```franko
addr<T>
```

An address value refers to a storage location containing a value of type `T`.

Examples:

```franko
addr<int32_t> p;
addr<uint8_t> b;
addr<addr<int32_t>> pp;
addr<array<int32_t>> ap;
```

Addresses are:

- typed,
- assignable,
- copyable,
- dereferenceable,
- comparable.

Addresses are not arithmetic values.

---

## 20.1 Creating Addresses with `getaddr`

Syntax:

```franko
getaddr(location)
```

The operand must be a storage-backed lvalue.

If the operand has type `T`, then:

```franko
getaddr(operand)
```

has type:

```franko
addr<T>
```

Valid:

```franko
int32_t x;
addr<int32_t> p;

p = getaddr(x);
```

Valid for array elements:

```franko
array<int32_t> arr;
addr<int32_t> p;

arr(60);
p = getaddr(arr[30]);
```

Valid for dereferenced addresses:

```franko
int32_t x;
addr<int32_t> p;
addr<addr<int32_t>> pp;

p = getaddr(x);
pp = getaddr(p);

deref(pp) = p;
```

Invalid:

```franko
getaddr(123);    // invalid
getaddr(x + 1);  // invalid
getaddr(x == y); // invalid
```

`getaddr(...)` itself is not an lvalue.

Invalid:

```franko
getaddr(x) = p; // invalid
```

---

## 20.2 Dereferencing with `deref`

Syntax:

```franko
deref(addressExpression)
```

If the operand has type:

```franko
addr<T>
```

then:

```franko
deref(...)
```

has type:

```franko
T
```

Example:

```franko
int32_t x;
addr<int32_t> p;

p = getaddr(x);

x = deref(p);
```

`deref(...)` is also an lvalue, so it may appear on the left side of assignment:

```franko
deref(p) = 10;
```

Invalid:

```franko
int32_t x;

deref(x); // invalid: x is not an address
```

---

## 20.3 Nested Addresses

Franko supports addresses to addresses.

Example:

```franko
int32_t x;
addr<int32_t> p;
addr<addr<int32_t>> pp;

p = getaddr(x);
pp = getaddr(p);
```

Then:

```franko
deref(pp)
```

has type:

```franko
addr<int32_t>
```

and:

```franko
deref(deref(pp))
```

has type:

```franko
int32_t
```

Example:

```franko
deref(deref(pp)) = 5;
```

---

## 20.4 Address Assignment

Address assignment requires exact type equality.

Valid:

```franko
int32_t x;
addr<int32_t> p;
addr<int32_t> q;

p = getaddr(x);
q = p;
```

Invalid:

```franko
int32_t x;
uint8_t b;

addr<int32_t> p;
addr<uint8_t> q;

p = q; // invalid
```

Invalid:

```franko
addr<int32_t> p;

p = 0;
p = 123;
p = 0xdeadbeef;
```

Raw integers cannot be converted to addresses.

---

## 20.5 Address Comparison

Addresses may be compared using:

```franko
==
!=
<
>
<=
>=
```

Both operands must have identical address types.

Valid:

```franko
int32_t x;
int32_t y;

addr<int32_t> a;
addr<int32_t> b;

a = getaddr(x);
b = getaddr(y);

if (a == b) {
    print(1);
}

if (a < b) {
    print(2);
}
```

Invalid:

```franko
addr<int32_t> a;
addr<uint8_t> b;

if (a == b) {
    print(1);
}
```

Invalid:

```franko
addr<int32_t> a;
int32_t x;

if (a == x) {
    print(1);
}
```

Address comparison produces `uint8_t`.

---

## 20.6 No Address Arithmetic

Addresses are not integers.

Invalid:

```franko
addr<int32_t> p;

p + 1;
p - 1;
p * 2;
p / 2;
p & p;
p | p;
p ^ p;
p << 1;
p >> 1;
```

Also invalid:

```franko
deref(p + 1);
```

Franko intentionally does not support pointer arithmetic.

---

## 20.7 Addresses of Arrays

Arrays may be addressed.

Example:

```franko
array<int32_t> arr;
addr<array<int32_t>> p;

p = getaddr(arr);
deref(p)(10);
```

Then:

```franko
deref(p).memset(0);
```

is valid if the dereferenced array satisfies the intrinsic rules.


---

## 20.8 Address Lifetime Limitations

Address lifetime is not fully tracked.

This may not be fully diagnosed:

```franko
addr<int32_t> p;

p = getaddr(x);
del x;

// The compiler may not fully know that p is dangling.
deref(p);
```

The compiler does not fully track dangling addresses or aliasing through addresses.

---

# 21. `del` and Delete Checking

The semantic model distinguishes between heap and non-heap variables.

Only heap variables may be deleted.

Delete syntax:

```franko
del name;
```

The `del` operand is a variable name, not a general expression.

Valid, assuming `x` is declared as a heap variable:

```franko
alloc int x;

del x;
```

Invalid:

```franko
int y;

del y; // invalid: y is not heap-owned
```

Invalid:

```franko
del missing; // invalid if missing is undeclared
```

Invalid:

```franko
alloc int x;

del x;
del x; // invalid: double delete
```

After deletion, direct use of the variable is invalid:

```franko
alloc int x;

del x;
print(x); // invalid
```

Dynamic array `uninit()` is separate from `del`.

## 21.1 Delete Checking Limitations

Current limitations:

- delete checking is symbol-based,
- aliasing through addresses is not fully tracked,
- dangling addresses are not fully detected,
- delete state checking is path-insensitive and traversal-based,
- deleting in one branch may affect later checking conservatively.

Example of limitation:

```franko
if (cond) {
    del x;
}

print(x); // may be rejected because x was marked deleted during checking
```

The compiler does not perform full ownership, lifetime, alias, or dangling-address analysis.

---

# 22. Type Equality

Franko uses exact semantic type equality in many places.

---

## 22.1 Primitive Equality

Primitive types are equal only if they have the same kind.

```franko
uint8_t  != uint16_t
int32_t  != uint32_t
int32_t  == int32_t
```

---

## 22.2 Dynamic Array Equality

Dynamic arrays are equal if their element types are equal.

```franko
array<int32_t> == array<int32_t>
array<int32_t> != array<uint8_t>
```

---

## 22.3 Static Array Equality

Static arrays are equal if:

- element types are equal,
- sizes are numerically equal.

For example, these sizes are considered equal if parsed successfully:

```franko
array<int32_t, 16>
array<int32_t, 0x10>
array<int32_t, 0b10000>
```

---

## 22.4 Address Equality

Address types are equal if their referenced types are equal.

```franko
addr<int32_t> == addr<int32_t>
addr<int32_t> != addr<uint8_t>
```

---

# 23. Declaration Type Validity

A declared type is valid if it is one of:

- primitive integer type,
- dynamic array of a valid type,
- static array of a valid type with valid size,
- address of a valid type.

Valid:

```franko
int32_t x;
array<int32_t> xs;
array<uint8_t, 64> bytes;
addr<int32_t> p;
addr<array<int32_t>> ap;
```

Invalid static sizes:

```franko
array<int32_t, 0> a;
array<int32_t, -1> b;
array<int32_t, 999999999999999999999999999999999> c;
```

---

# 24. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

---

## 24.1 No User-Defined Functions

Function declarations and general calls are not implemented.

General user-defined function calls are not supported:

```franko
foo();
foo(1);
foo(x, y);
```

The call syntax is currently used mainly to recognize array intrinsics:

```franko
arr(100);
arr.memset(0);
arr.memcpy(other);
arr.uninit();
```

Function types and function symbols exist in the internal semantic model preemptively, but source-level function semantics are not active yet.

---

## 24.2 No General Methods

Only array intrinsic member calls are recognized.

Supported member-call forms:

```franko
arr.uninit()
arr.memset(value)
arr.memcpy(source)
```

Invalid:

```franko
x.foo();
x.field;
```

unless implemented later by a future semantic pass.

---

## 24.3 No Structs or Fields

There are no user-defined aggregate types beyond arrays.

General member access is not currently implemented.

---

## 24.4 No Floating-Point Types

Only integer primitives are supported.

---

## 24.5 No Separate Boolean Type

Boolean results use `uint8_t`.

---

## 24.6 No Implicit Numeric Conversions for Nonconstants

This is invalid:

```franko
uint8_t a;
uint32_t b;

a = b;
```

Constants are special and may fit contextually.

---

## 24.7 Arrays Are Not Assignable

Arrays cannot be assigned directly.

Invalid:

```franko
array<int32_t> a;
array<int32_t> b;

a = b;
```

Use:

```franko
a.memcpy(b);
a.memset(0);
a.uninit();
```

depending on the intended operation.

---


## 24.8 No Full Array Lifetime Checking

The checker validates intrinsic legality, but it does not fully prove:

- dynamic array initialized before indexing,
- dynamic array not used after `uninit`,
- dynamic array not initialized twice,
- all array accesses are within bounds.

---

## 24.9 Delete Checking Is Limited

Delete checking is currently:

- symbol-based,
- path-insensitive,
- not alias-aware.

It does not fully track dangling addresses or ownership.

---

## 24.10 Address Lifetime Is Not Fully Tracked

This may not be fully diagnosed:

```franko
addr<int32_t> p;

p = getaddr(x);
del x;

// The compiler may not fully know that p is dangling.
deref(p);
```

---

## 24.11 Print Checking Is Loose

The checker validates print arguments as expressions but does not enforce a strict printable-type set.

---


## 24.12 Unsupported Operators

The following are not currently implemented:

```franko
%x   // modulus is not implemented
~x   // bitwise NOT is not implemented
+x   // unary plus is not implemented as a semantic unary operator
```

---

# 25. Runtime and Backend Notes

The current compiler targets C++14.

Some Franko rules mirror the generated C++ representation, especially array behavior.

For example, array `memcpy` is designed to support combinations such as:

```text
Static<T, N>.memcpy(Static<T, M>)
Static<T, N>.memcpy(Dynamic<T>)
Dynamic<T>.memcpy(Dynamic<T>)
Dynamic<T>.memcpy(Static<T, M>)
```

Static array sizes must be greater than zero because generated C++ uses fixed-size arrays, and zero-length C++ arrays are not standard.

---

# 26. Examples

## 26.1 Basic Integers

```franko
int32_t x;
int32_t y;

x = 10;
y = x + 5;

print(y);
```

---

## 26.2 Using `int` Alias

```franko
int x = 10;
print(x);
```

Equivalent to:

```franko
int32_t x;
x = 10;
print(x);
```

---

## 26.3 Constants Fitting Smaller Types

```franko
uint8_t b;

b = 255; // valid
// b = 256; // invalid
```

---

## 26.4 Dynamic Array

```franko
array<int32_t> xs;
uint32_t i;

xs(10);

i = 0;
xs[i] = 42;

print(xs[i]);

xs.uninit();
```

---

## 26.5 Static Array

```franko
array<uint8_t, 128> buffer;

buffer.memset(0);
buffer[0] = 255;
```

---

## 26.6 Array Copy

```franko
array<int32_t> a;
array<int32_t, 10> b;

a(10);

b.memset(0);
a.memcpy(b);
```

---

## 26.7 Heap Variable

```franko
alloc int x;

x = 10;
print(x);

del x;
```

---

## 26.8 Allocated Dynamic Array Initialization Sugar

```franko
alloc array<int> arr(20);

arr.memset(0);

del arr;
```

Equivalent to:

```franko
alloc array<int> arr;
arr(20);

arr.memset(0);

del arr;
```

---

## 26.9 Addresses

```franko
int32_t x;
addr<int32_t> p;

x = 1;
p = getaddr(x);

deref(p) = deref(p) + 1;

print(x);
```

---

## 26.10 Address of Array Element

```franko
array<int32_t> arr;
addr<int32_t> p;

arr(60);
arr[30] = 123;

p = getaddr(arr[30]);

print(deref(p));
```

---

## 26.11 Nested Addresses

```franko
int32_t x;
addr<int32_t> p;
addr<addr<int32_t>> pp;

x = 1;

p = getaddr(x);
pp = getaddr(p);

deref(deref(pp)) = 5;

print(x);
```

---

## 26.12 Address Comparison

```franko
int32_t x;
int32_t y;

addr<int32_t> a;
addr<int32_t> b;

a = getaddr(x);
b = getaddr(y);

if (a != b) {
    print(1);
}
```

---

## 26.13 Dynamic Array Through Address

```franko
array<int32_t> arr;
addr<array<int32_t>> p;

arr(10);
p = getaddr(arr);

deref(p).memset(0);
deref(p).uninit();
```

This is valid because `memset` and `uninit` accept storage-backed array lvalues.

---
