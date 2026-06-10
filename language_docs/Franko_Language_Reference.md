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

1. [Overview](01-overview.md)
2. [Source Files](02-source-files.md)
3. [Program Structure](03-program-structure.md)
4. [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md)
5. [Desugaring](05-desugaring.md)
6. [Types](06-types.md)
7. [Primitive Integer Types](07-primitive-integer-types.md)
8. [Integer Literals](08-integer-literals.md)
9. [Fluid Integer Constants](09-fluid-integer-constants.md)
10. [Constant Folding](10-constant-folding.md)
11. [Declarations](11-declarations.md)
12. [Functions](12-functions.md)
13. [Heap Variables and `alloc`](13-heap-variables-and-alloc.md)
14. [Assignment](14-assignment.md)
15. [Lvalues and Storage-Backed Expressions](15-lvalues-and-storage-backed-expressions.md)
16. [Operators](16-operators.md)
17. [Conditions](17-conditions.md)
18. [Statements](18-statements.md)
19. [Arrays](19-arrays.md)
20. [Array Intrinsics](20-array-intrinsics.md)
21. [Array Initializer Lists](21-array-initializer-lists.md)
22. [Addresses](22-addresses.md)
23. [`del` and Delete Checking](23-del-and-delete-checking.md)
24. [Type Equality](24-type-equality.md)
25. [Declaration Type Validity](25-declaration-type-validity.md)
26. [Unsupported or Restricted Features](26-unsupported-or-restricted-features.md)
27. [Runtime and Backend Notes](27-runtime-and-backend-notes.md)
28. [Examples](28-examples.md)

---

# 1. Overview

Franko is a small statically typed language with:

- fixed-width signed and unsigned integer types;
- dynamic arrays;
- static arrays;
- typed addresses using `addr<T>`;
- explicit address operations:
  - `getaddr(...)`;
  - `deref(...)`;
- user-defined functions;
- function overloading;
- function calls inside function bodies;
- `void` and non-`void` return types;
- global variable declarations;
- global function declarations;
- assignment;
- blocks;
- `if`;
- `while`;
- `return`;
- `print`;
- `del`;
- heap-owned variables using `alloc`;
- array intrinsics:
  - `arr(size)`;
  - `arr.uninit()`;
  - `arr.memset(value)`;
  - `arr.memcpy(source)`;
- array initializer lists as array element assignment sugar.

Franko currently does **not** implement:

- nested functions;
- first-class function values;
- anonymous functions or lambdas;
- function pointers;
- structs/classes;
- fields;
- general methods;
- generics;
- imports/modules;
- floating-point types;
- a distinct boolean type;
- string literals;
- implicit numeric conversions except for fluid integer constants.

Franko currently targets C++14.

---

# 2. Source Files

Franko source files commonly use the `.fr` extension.

Example:

```franko
func main() -> int32_t {
    int32_t x;
    x = 1;

    print(x);

    return 0;
}
```

---

# 3. Program Structure

A Franko program is a sequence of **top-level items**.

Current valid top-level items are:

- global variable declarations;
- function declarations.

Executable statements are **not** currently allowed in global scope.

Invalid global-scope items include:

- assignments;
- `print`;
- function calls;
- `if`;
- `while`;
- `return`;
- `del`;
- blocks;
- array intrinsic calls;
- array initializer assignments.

Execution should be rooted in a user-defined `main` function.

Example:

```franko
func main() -> int32_t {
    return 0;
}
```

---

# 4. Lexical Scoping and Symbols

Franko uses lexical scope.

Function bodies can resolve names from:

- their parameter scope;
- local block scopes;
- enclosing block scopes;
- global variable scope;
- global function declarations for calls.

## 4.1 Declaration Lookup

Variable references resolve to the nearest declaration in the current scope stack.

## 4.2 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

## 4.3 Shadowing

Shadowing in an inner block is allowed.

## 4.4 Use Before Declaration

Local variables must be declared before they are used within their lexical scope.

Global variable declarations are registered before function bodies are analyzed.

## 4.5 Undeclared Variables

Using an undeclared variable is illegal.

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

is accepted in statement contexts where declaration followed by assignment is legal, and is desugared to:

```franko
int x;
x = 10;
```

At global scope, declaration initializer sugar is currently invalid if it desugars into an assignment, because global executable statements are not allowed.

## 5.2 Allocated Dynamic Array Initialization Sugar

This:

```franko
alloc array<int> arr(20);
```

is desugared to:

```franko
alloc array<int> arr;
arr(20);
```

## 5.3 Array Initializer List Desugaring

This:

```franko
xs = [1, 2, 3];
```

is desugared to:

```franko
xs[0] = 1;
xs[1] = 2;
xs[2] = 3;
```

Nested initializer lists are recursively lowered to nested indexed assignments.

---

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

| Alias | Canonical type |
|---|---|
| `int` | `int32_t` |

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
func main() -> int32_t {
    uint8_t x;

    x = 255; // valid
    x = 256; // invalid: 256 does not fit uint8_t

    return 0;
}
```

Fluid constants also participate in function overload resolution. A folded integer constant may match a primitive integer parameter if its `BigInteger` value fits that parameter type.

---

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

The initializer must obey normal assignment rules and is only valid where the resulting assignment is legal.

## 11.1 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

## 11.2 Shadowing

A variable may be redeclared in an inner block, creating a new variable that shadows the outer one.

## 11.3 Use Before Declaration

Local variables must be declared before they are used.

## 11.4 Global Variable Declarations

Variable declarations are allowed at global scope. Initializer sugar at global scope is currently invalid if it desugars into executable assignment statements.

---

# 12. Functions

Franko supports user-defined functions.

Function declarations are global top-level items.

Function declarations are not valid inside blocks or inside other function bodies.

## 12.1 Function Declaration Syntax

```franko
func name(parameters) -> returnType {
    statements
}
```

## 12.2 Function Declaration Placement

Function declarations are valid only in global scope.

## 12.3 Function Parameters

Function parameters are treated as local variables inside the function body.

Duplicate parameter names inside the same function are invalid.

Parameters cannot have type `void`.

## 12.4 `void`

`void` is valid as a function return type. It is not an ordinary value type.

## 12.5 Function Signatures and Overloading

Franko supports function overloading.

A function signature is determined by:

- the function identifier;
- the ordered list of parameter types.

Return type is not part of the function signature.

## 12.6 Function Call Resolution

A function call is valid if and only if exactly one overload is applicable.

For nonconstant arguments, the argument type must exactly match the parameter type.

For folded integer constants, the constant may match a primitive integer parameter if its value fits that parameter type.

## 12.7 Bare Function Names

A bare function name is not a value.

## 12.8 Function Call Expressions

A function call expression has the return type of the selected function.

A `void`-returning function call is valid as an expression statement but invalid in value-producing contexts.

## 12.9 Return Statements

A `void` function may use bare `return` and must not use `return expr`.

A non-`void` function must return an expression compatible with the declared return type.

## 12.10 Function Return Types

A function return type must be either:

- `void`;
- a primitive integer type;
- an address type, `addr<T>`.

Arrays cannot currently be returned directly.

## 12.11 Forward References and Recursion

Function signatures are registered before function bodies are analyzed, allowing forward references, direct recursion, and mutual recursion.

---

# 13. Heap Variables and `alloc`

Franko supports heap-owned variables using `alloc`.

Syntax:

```franko
alloc int x;
alloc int32_t y;
alloc array<int> arr;
```

The `alloc` keyword marks the variable as heap-owned. Heap-owned variables may be deleted with `del`.

Non-heap variables cannot be deleted.

## 13.1 Allocated Dynamic Array Initialization Sugar

```franko
alloc array<int> arr(20);
```

is desugared to:

```franko
alloc array<int> arr;
arr(20);
```

---

# 14. Assignment

Assignment syntax:

```franko
target = value;
```

The left-hand side must be a storage-backed lvalue.

Valid assignment targets include:

- variables;
- array elements;
- dereferenced addresses.

## 14.1 Primitive Assignment

A primitive integer variable may be assigned:

1. a nonconstant expression of exactly the same primitive type; or
2. a constant expression that fits the target type.

## 14.2 Address Assignment

Address assignment requires exact address type equality.

Raw integers cannot be converted to addresses.

## 14.3 Array Assignment

Arrays cannot be directly assigned as values.

Invalid:

```franko
array<int32_t> a;
array<int32_t> b;

a = b;
```

Array initializer assignment is not value assignment. It is element-assignment sugar:

```franko
a = [1, 2, 3];
```

lowers to:

```franko
a[0] = 1;
a[1] = 2;
a[2] = 3;
```

---

# 15. Lvalues and Storage-Backed Expressions

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
| function call result | no |
| `getaddr(...)` result | no |

This distinction matters for:

- assignment targets;
- `getaddr(...)` operands;
- array intrinsic receivers;
- `memcpy` sources;
- array initializer assignment targets.

---

# 16. Operators

Franko operators apply primarily to integer expressions.

Addresses have special comparison rules.

Arrays are not arithmetic values.

## 16.1 Operator Precedence

From highest precedence to lowest:

1. postfix expressions, function calls, array access `a[i]`, `deref(a)`
2. unary `-x`, `!x`
3. multiplicative `*`, `/`
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

## 16.2 Unary Operators

Supported unary operators:

- logical NOT: `!x`
- unary minus: `-x`

Unsupported unary operators:

```franko
~x
+x
```

## 16.3 Arithmetic Operators

Arithmetic operators: `+`, `-`, `*`, `/`.

Operands must be integers. Arrays and addresses are invalid.

The modulus operator `%` is not currently implemented.

## 16.4 Bitwise Operators

Bitwise operators: `&`, `|`, `^`.

Operands must be integers.

## 16.5 Shift Operators

Shift operators: `<<`, `>>`.

The right operand must be an integer. Nonconstant shift counts must have an unsigned integer type.

## 16.6 Comparison Operators

Comparison operators produce `uint8_t`.

Integer comparisons may use mixed integer types subject to constant fitting rules.

Address comparison is supported only between identical address types.

## 16.7 Logical Operators

Logical operators: `&&`, `||`, `!`.

Operands must be integers. Results are `uint8_t` values `0` or `1`.

## 16.8 No Address Arithmetic

Addresses are not integers and cannot participate in arithmetic, bitwise, or shift operations.

---

# 17. Conditions

`if` and `while` conditions must be integer expressions.

Valid:

```franko
int32_t x;

if (x) {
    print(x);
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

# 18. Statements

Franko supports the following statements inside function bodies and blocks:

- variable declarations;
- assignments;
- blocks;
- `if`;
- `while`;
- `return`;
- `print`;
- `del`;
- expression statements;
- function call statements;
- array intrinsic statement forms;
- array initializer assignments.

Executable statements are not allowed at global scope.

## 18.1 Blocks

A block is a sequence of statements enclosed in braces and creates a new lexical scope.

## 18.2 If Statements

The condition must be an integer expression.

## 18.3 While Statements

The condition must be an integer expression.

## 18.4 Return Statements

A bare `return` is valid only in a `void` function.

A `return expr` statement is required for non-`void` functions.

## 18.5 Print Statements

The current checker validates print arguments as expressions but does not impose a strict print-type policy.

Array initializer lists are not expressions and cannot be printed directly.

## 18.6 Expression Statements

Function calls are commonly used as expression statements.

Array intrinsic calls are special expression-statement forms:

```franko
arr(10);
arr.memset(0);
arr.memcpy(other);
arr.uninit();
```

---

# 19. Arrays

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

Arrays are not arithmetic values and cannot be directly assigned as ordinary values.

However, Franko supports **array initializer lists** as assignment sugar for assigning individual elements into an existing array target. See [Array Initializer Lists](21-array-initializer-lists.md).

## 19.1 Static Arrays

A static array has a compile-time size:

```franko
array<int32_t, 10> xs;
```

Static array size rules:

- size must be a compile-time integer constant;
- size must be greater than zero;
- size must fit `uint32_t`.

Valid:

```franko
array<int32_t, 1> a;
array<int32_t, 0x10> b;
array<int32_t, 0b1000> c;
array<int32_t, 1 + 2> d;
```

Invalid:

```franko
array<int32_t, 0> a;
array<int32_t, -1> b;
array<int32_t, 999999999999999999999999999999999> c;
```

Static arrays do not use `arr(size)` initialization.

## 19.2 Dynamic Arrays

A dynamic array has runtime size:

```franko
array<int32_t> xs;
```

Dynamic arrays are initialized using direct-call array initialization syntax:

```franko
xs(10);
```

Rules for dynamic array initialization:

- target must be a storage-backed lvalue;
- target must be a dynamic array;
- exactly one argument is required;
- size must be compatible with `uint32_t`;
- constant sizes must be greater than zero;
- constant sizes must fit `uint32_t`;
- nonconstant sizes must have exactly type `uint32_t`.

Dynamic array declaration alone does not allocate element storage.

The array must be initialized before ordinary indexed use.

## 19.3 Array Access

Array indexing syntax:

```franko
arr[index]
```

The target must be an array.

Index rules:

- constant index:
  - must be nonnegative;
  - must fit `uint32_t`;
- nonconstant index:
  - must have exactly type `uint32_t`.

For static arrays, constant indexes are checked against the known static size when possible.

## 19.4 Array Elements as Lvalues

Array elements are storage-backed lvalues when the array target is storage-backed.

Array elements may be addressed:

```franko
addr<int32_t> p;
p = getaddr(xs[0]);
```

Nested array elements may also be lvalues when each target is storage-backed.

## 19.5 Array Direct Assignment Is Invalid

Arrays cannot be assigned directly as ordinary values.

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

Array initializer assignment is not whole-array value assignment.

## 19.6 Array Lifetime Limitations

The checker validates intrinsic legality, but it does not fully prove:

- dynamic array initialized before indexing;
- dynamic array not used after `uninit`;
- dynamic array not initialized twice;
- all array accesses are within bounds.

---

# 20. Array Intrinsics

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

The receiver or target of an array intrinsic must usually be a storage-backed array lvalue.

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

## 20.1 `target(size)`

Dynamic array initialization.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must have dynamic array type;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32_t`; or
  - a nonconstant expression of exactly type `uint32_t`.

## 20.2 `target.uninit()`

Dynamic array uninitialization.

Rules:

- receiver must be a storage-backed lvalue;
- receiver must be a dynamic array;
- takes exactly zero arguments.

## 20.3 `target.memset(value)`

Fills an array byte-wise.

Rules:

- receiver must be a storage-backed lvalue;
- receiver may be a dynamic or static array;
- receiver element type must be memsetable;
- takes exactly one argument;
- fill value must be:
  - a constant fitting `uint8_t`; or
  - a nonconstant expression of exactly type `uint8_t`.

## 20.4 Memsetable Element Types

Current memsetable types:

| Element type | Memsetable? |
|---|---|
| primitive integers | yes |
| static arrays of memsetable elements | yes |
| dynamic arrays | no |
| addresses | no |

## 20.5 `target.memcpy(source)`

Copies array contents byte-wise according to the generated C++ array model.

Rules:

- target must be a storage-backed lvalue array;
- source must be a storage-backed lvalue array;
- both target and source may be dynamic or static arrays;
- target and source element types must match exactly;
- static/dynamic shape does not need to match;
- static lengths do not need to match;
- element type must be memcpyable.

## 20.6 Memcpyable Element Types

Current memcpyable types:

| Element type | Memcpyable? |
|---|---|
| primitive integers | yes |
| addresses | yes |
| static arrays of memcpyable elements | yes |
| dynamic arrays | no |

---

# 21. Array Initializer Lists

Franko supports **array initializer lists** as array element assignment sugar.

An array initializer list is written with square brackets:

```franko
[1, 2, 3]
```

An array initializer list is **not** a standalone array value.

It is **not** a general expression.

It is a compact syntax for assigning listed elements into an existing array target.

The core rule is:

```franko
target = [a, b, c];
```

is equivalent to:

```franko
target[0] = a;
target[1] = b;
target[2] = c;
```

No allocation, resizing, or dynamic array initialization is performed by the initializer list itself.

## 21.1 Array Initializer Lists Are Not General Expression Values

Array initializer lists do not have standalone types.

Invalid:

```franko
[1, 2, 3];
print([1, 2, 3]);
foo([1, 2, 3]);
return [1, 2, 3];
```

Array initializer lists are only meaningful in assignment-style contexts where there is an explicit array assignment target.

## 21.2 Array Initializer Assignment

An array initializer list may appear on the right-hand side of an assignment:

```franko
arr = [1, 2, 3];
```

This is not whole-array assignment. It is recursively desugared into indexed element assignments:

```franko
arr[0] = 1;
arr[1] = 2;
arr[2] = 3;
```

The normal expression, assignment, indexing, and array-use rules apply to the generated statements.

Initializer elements are ordinary expressions. They do not need to be compile-time constants.

## 21.3 Declaration Initializer Form

Array initializer lists may also appear in declaration-assignment syntax:

```franko
array<int32_t, 3> xs = [1, 2, 3];
```

This is treated as ordinary declaration initializer sugar:

```franko
array<int32_t, 3> xs;
xs = [1, 2, 3];
```

which then lowers to:

```franko
array<int32_t, 3> xs;
xs[0] = 1;
xs[1] = 2;
xs[2] = 3;
```

For dynamic arrays, the initializer list still does **not** initialize storage.

Invalid for a freshly declared dynamic array:

```franko
array<int32_t> dyn = [1, 2, 3];
```

Correct:

```franko
array<int32_t> dyn;
dyn(3);
dyn = [1, 2, 3];
dyn.uninit();
```

The initializer list itself never emits `dyn(3)`.

## 21.4 Static Arrays

For static arrays, initializer lists lower to indexed assignments.

If the initializer list is shorter than the static array length, only the listed elements are assigned. Unlisted elements are untouched.

If the initializer list is longer than the static array length, the generated indexed assignment is rejected by ordinary static array bounds checking.

There is no special “initializer length must exactly match static length” rule. The ordinary generated assignments determine validity.

## 21.5 Dynamic Arrays

Array initializer lists do not initialize, allocate, resize, or reallocate dynamic arrays.

Correct:

```franko
array<int32_t> dyn;
dyn(3);
dyn = [1, 2, 3];
dyn.uninit();
```

Invalid:

```franko
array<int32_t> dyn;
dyn = [1, 2, 3];
```

because it lowers to indexed assignments without first initializing `dyn`.

## 21.6 Element Expressions

Initializer list elements are ordinary expressions.

If an element expression is invalid, or if its value cannot be assigned to the array element type, the ordinary expression or assignment checker rejects the generated assignment.

Example:

```franko
array<uint8_t, 3> bytes;
bytes = [0, 127, 256];
```

lowers to:

```franko
bytes[0] = 0;
bytes[1] = 127;
bytes[2] = 256;
```

The assignment checker rejects `bytes[2] = 256` because `256` does not fit `uint8_t`.

## 21.7 Nested Initializer Lists

Nested initializer lists represent recursive indexed assignment into nested arrays.

Example:

```franko
array<array<int32_t, 2>, 2> matrix;

matrix = [
    [1, 2],
    [3, 4]
];
```

is equivalent to:

```franko
matrix[0][0] = 1;
matrix[0][1] = 2;
matrix[1][0] = 3;
matrix[1][1] = 4;
```

A nested initializer list is only meaningful when the corresponding target element is itself an array.

## 21.8 Nested Dynamic Arrays

Initializer lists do not allocate nested dynamic arrays.

Correct:

```franko
array<array<int32_t>> matrix;

matrix(2);
matrix[0](3);
matrix[1](3);

matrix = [
    [1, 2, 3],
    [4, 5, 6]
];

matrix[0].uninit();
matrix[1].uninit();
matrix.uninit();
```

Invalid:

```franko
array<array<int32_t>> matrix;

matrix(2);

matrix = [
    [1, 2, 3],
    [4, 5, 6]
];
```

because the outer dynamic array is initialized, but `matrix[0]` and `matrix[1]` are not initialized.

The rule is simple:

> Initializer lists assign elements. They never allocate dynamic array storage at any level.

## 21.9 Empty Initializer Lists

An empty initializer list emits no assignments.

Example:

```franko
array<int32_t, 3> xs;
xs = [];
```

No elements are assigned.

For dynamic arrays:

```franko
array<int32_t> xs;
xs = [];
```

also emits no assignments and does not initialize `xs`.

This is allowed as a no-op initializer assignment unless another rule rejects it.

## 21.10 Array Initializer Lists and Global Scope

Array initializer lists are assignment sugar.

Therefore, they are only valid where the resulting assignment statements are valid.

At global scope, executable statements are not allowed.

Invalid:

```franko
array<int32_t, 3> xs;
xs = [1, 2, 3];
```

Declaration initializer sugar at global scope is also invalid if it desugars into executable assignments.

## 21.11 Summary of Array Initializer Lists

Array initializer lists in Franko are:

- not general expressions;
- not array values;
- allowed as assignment initializer syntax;
- allowed in declaration initializers through ordinary declaration-assignment sugar;
- lowered to recursive indexed assignments;
- allowed to contain ordinary expressions;
- not required to contain compile-time constants;
- partial when shorter than the target array;
- rejected for static arrays only when generated indexes are out of bounds;
- not responsible for allocating, resizing, or initializing dynamic arrays;
- valid for dynamic arrays only when the target dynamic storage already exists;
- recursively applicable to nested arrays.

---

# 22. Addresses

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

Addresses are typed, assignable, copyable, dereferenceable, and comparable.

Addresses are not arithmetic values.

`addr<void>` is invalid because `void` is not an ordinary value type.

## 22.1 Creating Addresses with `getaddr`

The operand must be a storage-backed lvalue.

If the operand has type `T`, then `getaddr(operand)` has type `addr<T>`.

## 22.2 Dereferencing with `deref`

If the operand has type `addr<T>`, then `deref(...)` has type `T`.

`deref(...)` is also an lvalue.

## 22.3 Nested Addresses

Franko supports addresses to addresses.

## 22.4 Address Assignment

Address assignment requires exact type equality.

Raw integers cannot be converted to addresses.

## 22.5 Address Comparison

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

Address comparison produces `uint8_t`.

## 22.6 No Address Arithmetic

Addresses are not integers and do not support arithmetic.

## 22.7 Addresses of Arrays

Arrays may be addressed.

Example:

```franko
array<int32_t> arr;
addr<array<int32_t>> p;

p = getaddr(arr);
deref(p)(10);
```

Array initializer assignment through a dereferenced array address is valid when the dereference produces a storage-backed array lvalue:

```franko
array<int32_t, 3> xs;
addr<array<int32_t, 3>> p;

p = getaddr(xs);

deref(p) = [1, 2, 3];
```

Equivalent to:

```franko
deref(p)[0] = 1;
deref(p)[1] = 2;
deref(p)[2] = 3;
```

## 22.8 Address Lifetime Limitations

Address lifetime is not fully tracked.

The compiler does not fully track dangling addresses or aliasing through addresses.

---

# 23. `del` and Delete Checking

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

Non-heap variables cannot be deleted.

Double delete is invalid.

After deletion, direct use of the variable is invalid.

Dynamic array `uninit()` is separate from `del`.

## 23.1 Delete Checking Limitations

Current limitations:

- delete checking is symbol-based;
- aliasing through addresses is not fully tracked;
- dangling addresses are not fully detected;
- delete state checking is path-insensitive and traversal-based;
- deleting in one branch may affect later checking conservatively.

The compiler does not perform full ownership, lifetime, alias, or dangling-address analysis.

---

# 24. Type Equality

Franko uses exact semantic type equality in many places.

## 24.1 Primitive Equality

Primitive types are equal only if they have the same kind.

```text
uint8_t  != uint16_t
int32_t  != uint32_t
int32_t  == int32_t
```

The alias `int` canonicalizes to `int32_t`.

## 24.2 Dynamic Array Equality

Dynamic arrays are equal if their element types are equal.

```text
array<int32_t> == array<int32_t>
array<int32_t> != array<uint8_t>
```

## 24.3 Static Array Equality

Static arrays are equal if:

- element types are equal;
- sizes are numerically equal.

## 24.4 Address Equality

Address types are equal if their referenced types are equal.

## 24.5 Function Signature Equality

Function signatures are equal if they have:

- the same function identifier;
- the same ordered list of parameter types.

Return type and parameter names are not part of function signature equality.

---

# 25. Declaration Type Validity

A declared variable type is valid if it is one of:

- primitive integer type;
- dynamic array of a valid non-`void` type;
- static array of a valid non-`void` type with valid size;
- address of a valid non-`void` type.

Valid:

```franko
int32_t x;
array<int32_t> xs;
array<uint8_t, 64> bytes;
addr<int32_t> p;
addr<array<int32_t>> ap;
```

Invalid:

```franko
void x;
array<void> xs;
addr<void> p;
```

A function return type has separate rules. It may be:

- `void`;
- a primitive integer type;
- an address type.

Array return types are invalid.

---

# 26. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

## 26.1 Function Restrictions

- function declarations are valid only in global scope;
- nested functions are not supported;
- first-class function values are not supported;
- function pointers are not supported;
- anonymous functions/lambdas are not supported;
- methods are not supported;
- parameter names are not used in overload resolution;
- return type is not used in overload resolution;
- arrays cannot be returned by value;
- `void` calls cannot be used in value-producing contexts;
- full path-sensitive return analysis is not currently performed.

## 26.2 No General Methods

Only array intrinsic member calls are recognized.

## 26.3 No Structs or Fields

There are no user-defined aggregate types beyond arrays.

## 26.4 No Floating-Point Types

Only integer primitives are supported.

## 26.5 No Separate Boolean Type

Boolean results use `uint8_t`.

## 26.6 No Implicit Numeric Conversions for Nonconstants

Nonconstant numeric assignments require exact type equality.

## 26.7 Arrays Are Not Assignable as Values

Arrays cannot be assigned directly as ordinary values.

Array initializer assignment is an exception only in syntax shape, not in value semantics.

## 26.8 Array Initializer Lists Are Not General Values

Array initializer lists are not first-class values.

Invalid:

```franko
[1, 2, 3];
print([1, 2, 3]);
foo([1, 2, 3]);
return [1, 2, 3];
```

## 26.9 No Full Array Lifetime Checking

The checker validates intrinsic legality, but it does not fully prove dynamic array lifetime and bounds properties.

## 26.10 Delete Checking Is Limited

Delete checking is symbol-based, path-insensitive, and not alias-aware.

## 26.11 Address Lifetime Is Not Fully Tracked

The compiler does not fully track dangling addresses or ownership.

## 26.12 Print Checking Is Loose

The checker validates print arguments as expressions but does not enforce a strict printable-type set.

Array initializer lists are not expressions and therefore cannot be printed directly.

## 26.13 Unsupported Operators

The following are not currently implemented:

```franko
%   // modulus is not implemented
~x  // bitwise NOT is not implemented
+x  // unary plus is not implemented as a semantic unary operator
```

## 26.14 No String Literals Yet

String literals are not currently implemented.

Invalid:

```franko
print("hello");
```

Invalid:

```franko
array<uint8_t, 5> hello;
hello = "hello";
```

## 26.15 Global Scope Is Declaration-Only

Only variable declaration nodes and function declaration nodes are valid at global scope.

Executable statements are invalid at global scope.

---

# 27. Runtime and Backend Notes

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

Function signatures are registered before function bodies are analyzed so that forward references, direct recursion, and mutual recursion can be resolved.

Global variable declarations are also registered before function bodies are analyzed, so functions can reference globals declared later in source order.

Array initializer lists are lowered before ordinary semantic checking.

For example:

```franko
xs = [1, 2, 3];
```

is lowered to:

```franko
xs[0] = 1;
xs[1] = 2;
xs[2] = 3;
```

Nested initializer lists are lowered recursively.

The backend does not need to implement initializer lists as runtime values because they are not values in Franko.

---

# 28. Examples

Most executable examples should be placed inside a function body, typically `main`, because global scope allows only declarations.

## 28.1 Basic Integers

```franko
func main() -> int32_t {
    int32_t x;
    int32_t y;

    x = 10;
    y = x + 5;

    print(y);

    return 0;
}
```

## 28.2 Static Array Initializer Assignment

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs = [1, 2, 3];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

Equivalent core form:

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs[0] = 1;
    xs[1] = 2;
    xs[2] = 3;

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 28.3 Static Partial Initializer Assignment

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs = [1, 2];

    return 0;
}
```

`xs[2]` is untouched.

## 28.4 Dynamic Array Initializer Assignment

```franko
func main() -> int32_t {
    array<int32_t> xs;

    xs(3);

    xs = [10, 20, 30];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    xs.uninit();

    return 0;
}
```

## 28.5 Runtime Expressions Inside Initializer Lists

```franko
func getValue() -> int32_t {
    return 10;
}

func main() -> int32_t {
    int32_t x;

    x = 5;

    array<int32_t> xs;

    xs(4);

    xs = [1, x, getValue(), 1 + 2];

    xs.uninit();

    return 0;
}
```

## 28.6 Nested Static Arrays

```franko
func main() -> int32_t {
    array<array<int32_t, 2>, 2> matrix;

    matrix = [
        [1, 2],
        [3, 4]
    ];

    print(matrix[0][0]);
    print(matrix[1][1]);

    return 0;
}
```

## 28.7 Nested Dynamic Arrays

```franko
func main() -> int32_t {
    array<array<int32_t>> matrix;

    matrix(2);
    matrix[0](3);
    matrix[1](3);

    matrix = [
        [1, 2, 3],
        [4, 5, 6]
    ];

    matrix[0].uninit();
    matrix[1].uninit();
    matrix.uninit();

    return 0;
}
```

## 28.8 Declaration Initializer with Static Array

```franko
func main() -> int32_t {
    array<int32_t, 3> xs = [1, 2, 3];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 28.9 Invalid Dynamic Array Declaration Initializer

```franko
func main() -> int32_t {
    array<int32_t> xs = [1, 2, 3];

    return 0;
}
```

Invalid because this desugars to indexed assignments without initializing dynamic storage.

Correct:

```franko
func main() -> int32_t {
    array<int32_t> xs;

    xs(3);

    xs = [1, 2, 3];

    xs.uninit();

    return 0;
}
```

## 28.10 Array Initializer Through Address

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;
    addr<array<int32_t, 3>> p;

    p = getaddr(xs);

    deref(p) = [1, 2, 3];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

Equivalent core form:

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;
    addr<array<int32_t, 3>> p;

    p = getaddr(xs);

    deref(p)[0] = 1;
    deref(p)[1] = 2;
    deref(p)[2] = 3;

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```
