# Franko Language Reference

This document describes the Franko language as implemented by the current compiler pipeline:

```text
Franko source
  -> lexer + parser
  -> parser AST
  -> Desugarer
  -> SemanticAnalyzer
  -> LegalityChecker (declarations, expressions, statements, functions, types)
  -> MemorySafetyChecker
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
22. [String Literals](22-string-literals.md)
23. [Addresses](23-addresses.md)
24. [Structs](24-structs.md)
25. [`del` and Delete Checking](25-del-and-delete-checking.md)
26. [Memory Safety Checking](26-memory-safety-checking.md)
27. [Type Equality](27-type-equality.md)
28. [Declaration Type Validity](28-declaration-type-validity.md)
29. [Unsupported or Restricted Features](29-unsupported-or-restricted-features.md)
30. [Runtime and Backend Notes](30-runtime-and-backend-notes.md)
31. [Examples](31-examples.md)

---

# 1. Overview

Franko is a small statically typed language with:

- fixed-width signed and unsigned integer types;
- structs with named fields (`struct X { int32_t n }`);
- struct member access using `obj.field` (see [Structs](24-structs.md));
- dynamic arrays;
- static arrays;
- `ndarray<...>` sugar for nested static arrays;
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
- string literals as byte-array initializer sugar;
- array intrinsics:
  - `arr(size)` (shorthand for `arr.init(size)`);
  - `arr.init(size)`;
  - `arr.init_zero(size)`;
  - `arr.resize(size)`;
  - `arr.uninit()`;
  - `arr.memset(value)` and `arr.memset(value, start, count)`;
  - `arr.memcpy(source)` and `arr.memcpy(source, dstStart, srcStart, count)`;
  - `arr.memmove(dstStart, srcStart, count)`;
- array initializer lists as array element assignment sugar.

Franko currently does **not** implement:

- nested functions;
- first-class function values;
- anonymous functions or lambdas;
- function pointers;
- general methods (only the built-in array intrinsics use member-call syntax);
- generics;
- imports/modules;
- floating-point types;
- a distinct boolean type;
- general string expressions (string literals are initializer sugar only, see [String Literals](22-string-literals.md));
- implicit numeric conversions except for fluid integer constants.
- cast syntax;
- compound assignment operators (`+=` and similar), increment, and decrement operators;
- a ternary conditional operator;
- `switch`, `do`/`do-while`, `for`, `break`, and `continue`;
- multiple declarators in one declaration (`int a, b;`).

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

## Statement Separators

Statements are terminated by a semicolon (`;`) or by one or more newlines. Both forms can be mixed freely, and consecutive separators are allowed.

Example (newline-separated, no semicolons):

```franko
func main() -> int32_t {
    int32_t x
    x = 1
    print(x)
    return 0
}
```

Newlines inside parentheses `(...)` and brackets `[...]` are ignored, so expressions, argument lists, and initializer lists may span multiple lines. Newlines inside braces `{...}` are not ignored: they remain statement separators.

## Comments

Line comments start with `//` and run to the end of the line:

```franko
// a whole-line comment
int32_t x  // a comment may follow code on the same line
```

There are no block comments.

---

# 3. Program Structure

A Franko program is a sequence of **top-level items**.

Current valid top-level items are:

- struct declarations;
- global variable declarations;
- function declarations.

Struct declarations are described in full in [Structs](24-structs.md).

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
- expression statements (bare expressions such as `1 + 2`);

Note that some invalid global-scope forms only become visible after desugaring: declaration initializer sugar is invalid because it desugars into executable statements (an assignment, an expression-statement call, or indexed element assignments).

The `alloc` modifier is not valid at global scope; global variables are always statically allocated. The same rule applies inside struct bodies: `alloc` is not valid for struct fields. See [Heap Variables and `alloc`](13-heap-variables-and-alloc.md).

Execution should be rooted in a user-defined `main` function.

Example:

```franko
struct Point {
    int32_t x
    int32_t y = 0
}

int32_t count = 0

func main() -> int32_t {
    return 0;
}
```

---

# 4. Lexical Scoping and Symbols

Franko uses lexical scope.

Function bodies can resolve names from:

- the function's parameter and body scope (parameters and body-level declarations share one scope);
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

## 4.6 Function Body Scope

The braces of a function body do not create an additional scope. Parameters and the function body's top-level declarations share a single scope.

Therefore a body-level declaration that reuses a parameter name is a duplicate-declaration error:

```franko
func f(int32_t x) -> int32_t {
    int32_t x;   // invalid: duplicate declaration of 'x'
    return x;
}
```

Only nested `{ ... }` blocks inside the body create new scopes.

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

## 5.2 Dynamic Array Call-Init Declaration Sugar

This:

```franko
array<int> arr(20);
alloc array<int> arr2(20);
```

is desugared to:

```franko
array<int> arr;
arr(20);
```

```franko
alloc array<int> arr2;
arr2(20);
```

The call form itself (`arr(20)`) is later recognized as array intrinsic `init` sugar. See [Array Intrinsics](20-array-intrinsics.md).

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

## 5.4 String Literal Desugaring

String literals are converted to array initializer lists of UTF-8 byte values before ordinary initializer lowering:

```franko
s = "ABC";
```

first becomes:

```franko
s = [65, 66, 67];
```

and is then lowered to indexed byte assignments. No null terminator is added.

String literals are only valid in assignment/declaration initializer contexts; they are not general expressions. See [String Literals](22-string-literals.md).

## 5.5 `ndarray` Type Desugaring

`ndarray<T, D1, D2, ..., Dn>` is sugar for nested static arrays:

```franko
ndarray<byte, 12, 20, 30>
```

desugars to:

```franko
array<array<array<byte, 30>, 20>, 12>
```

so that `arr[i][j][k]` indexes `i` along the first dimension (`12`), `j` along the second (`20`), and `k` along the last (`30`). See [Arrays](19-arrays.md).

## 5.6 Braceless Control-Flow Bodies

`if` and `while` bodies may be a single statement without braces:

```franko
if (x) print(x);
while (i < 10) i = i + 1;
```

During desugaring, a braceless body is wrapped in an implicit block:

```franko
if (x) { print(x); }
while (i < 10) { i = i + 1; }
```

This makes braceless and braced bodies scope identically: a declaration inside a braceless body is scoped to that implicit block and is not visible after the `if`/`while`. See [Statements](18-statements.md).

---

# 6. Types

Franko has the following semantic type categories:

```text
primitive integers
structs
dynamic arrays
static arrays
typed addresses
function return-only void
```

There is currently no separate semantic boolean type. Boolean-like results are represented as `uint8_t`.

Structs are nominal types: two distinct struct declarations are distinct types even when their field layouts are identical. See [Type Equality](27-type-equality.md), [Declarations](11-declarations.md), and the complete struct reference in [Structs](24-structs.md).

`void` is not an ordinary value type. It is valid only where the language explicitly permits it, currently as a function return type.

`ndarray<T, D1, D2, ...>` is not a separate semantic type category. It is syntactic sugar desugared into nested static array types before semantic analysis.

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
| `uint` | `uint32_t` |
| `byte` | `uint8_t` |

No other aliases (such as `char` or `bool`) are currently defined.

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
ndarray<byte, 12, 20> grid;   // sugar for array<array<byte, 20>, 12>

addr<int> p;
addr<int32_t> q;
addr<array<int>> arrayPtr;
```

Heap declarations use `alloc`:

```franko
alloc int32_t h;
alloc array<int32_t> hs;
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

Dynamic array call-init declaration sugar:

```franko
array<int32_t> d(10);
```

is equivalent to:

```franko
array<int32_t> d;
d(10);
```

The call form is only valid for dynamic arrays. Using it on a non-array declaration is an error:

```franko
int32_t x(5);   // invalid: x is not a dynamic array
```

## 11.1 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

## 11.2 Shadowing

A variable may be redeclared in an inner block, creating a new variable that shadows the outer one.

## 11.3 Use Before Declaration

Local variables must be declared before they are used.

## 11.4 Global Variable Declarations

Variable declarations are allowed at global scope.

Global variable declarations may have an optional constant initializer:

```franko
int32_t g = 1 + 2
uint64_t mask = 1 << 8
```

The initializer must be a constant expression (`constExpr`) and is folded at compile time. Only primitive globals can have initializers; struct, array, and addr globals reject `= constExpr` as a type mismatch.

Ordinary declaration initializer sugar remains invalid at global scope because it desugars into executable statements (an assignment, an expression-statement call, or indexed element assignments), and executable statements are not allowed at global scope.

The `alloc` modifier is not valid at global scope; global variables are always statically allocated.

## 11.5 Uninitialized Values

A **local** declaration without an initializer default-initializes the variable:

- primitive integer variables start with an indeterminate value; reading one before its first assignment is undefined behavior in the generated C++;
- static array elements start with indeterminate values;
- dynamic arrays start empty (length 0, no element storage) and must be initialized with `init` or `init_zero` before indexed use;
- **address variables are the exception: an `addr<T>` declaration is guaranteed null-initialized** (see [Addresses](23-addresses.md));
- heap variables (`alloc`) are allocated and default-constructed, so they follow the same rules as above, except that the storage of an `alloc`'d address is also null-initialized.

There is no implicit zero-initialization for locals other than addresses. Use `init_zero` for zeroed dynamic array storage.

**Global** declarations are statically allocated: in the generated C++ they live in static storage, so primitives and static array elements are zero-initialized before `main` runs, and dynamic arrays start empty. Global constant initializers (see Global Variable Declarations above) are applied on top of that.

## 11.6 Struct Fields

Struct fields are declared with the same `type name` syntax as variables, inside a top-level `struct` declaration:

```franko
struct X {
    int32_t num = 0
    array<int32_t, 4> xs
    addr<Y> next
}
```

`alloc` is not valid for struct fields, field defaults must be constant expressions on primitive fields only, and duplicate field names are rejected.

The complete struct reference — field rules, by-value cycles, assignability, member access, globals, and heap structs — is in [Structs](24-structs.md).

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

A parameter type must be a **passable type**:

- a primitive integer type;
- an address type `addr<T>`, where `T` is any valid declared type. Arrays and nested addresses are allowed as the referenced type, so `addr<array<int32_t>>`, `addr<array<int32_t, 8>>`, and `addr<addr<int32_t>>` are all passable; or
- a struct type whose fields are all assignable value types, recursively. Assignable = primitive | address | struct-with-all-assignable-fields. Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing one of them is address-only: pass `addr<X>` and `getaddr(s)` instead. See [Structs](24-structs.md).

Bare array types are not passable. To pass an array to a function, declare an `addr<array<T>>` (or `addr<array<T, N>>`) parameter and pass `getaddr(array)` at the call site.

`void` cannot be written as a parameter type at all: the grammar only accepts an ordinary type in parameter positions, and `void` is not an ordinary type.

## 12.4 `void`

`void` is valid as a function return type. It is not an ordinary value type.

## 12.5 Function Signatures and Overloading

Franko supports function overloading.

A function signature is determined by:

- the function identifier;
- the ordered list of parameter types.

Return type is not part of the function signature.

Declaring two functions with the same identifier and the same ordered parameter types is an error, even if their return types differ.

## 12.6 Function Call Resolution

A function call is valid if and only if exactly one overload is applicable.

For nonconstant arguments, the argument type must exactly match the parameter type.

For folded integer constants, the constant may match a primitive integer parameter if its value fits that parameter type. If a constant argument fits the parameter types of multiple overloads, the call is ambiguous and is rejected.

## 12.7 Bare Function Names

Variables and functions live in separate namespaces.

A bare function name is not a value. An identifier used as a value resolves to a variable first; if the identifier only matches a function name, it is rejected:

```franko
func f() -> int32_t {
    return 1;
}

f;    // invalid: Function name 'f' cannot be used as a value
```

In a call position, `name(...)` resolves to a function even when a non-array variable with the same name is in scope. The one exception is a dynamic array variable: `xs(10)` on a dynamic array is array `init` shorthand, not a function call. See [Array Intrinsics](20-array-intrinsics.md).

## 12.8 Function Call Expressions

A function call expression has the return type of the selected function.

A `void`-returning function call is valid as an expression statement but invalid in value-producing contexts.

## 12.9 Return Statements

A `void` function may use bare `return` and must not use `return expr`.

A non-`void` function must return an expression compatible with the declared return type.

A non-`void` function must contain at least one `return` statement somewhere in its body (a bare `return;` still counts for this presence check, but is separately rejected for lacking an expression). The checker's missing-return detection is presence-based: a `return` nested inside an `if` or `while` body counts as appearing in the function, and full path-sensitive "all paths return" analysis is not performed.

## 12.10 Function Return Types

A function return type must be either:

- `void`;
- a primitive integer type;
- an address type, `addr<T>`; or
- a struct type whose fields are all assignable value types, recursively (see [Structs](24-structs.md)).

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

`alloc` is a declaration modifier only. There is no standalone allocation statement, and a variable cannot be re-allocated: redeclaring the same name is a duplicate-declaration error, and after `del x` the name `x` cannot be allocated again in the same scope.

`alloc` is only valid for local declarations inside function bodies and blocks. Global variables are always statically allocated; `alloc` at global scope is a compile-time error.

Non-heap variables cannot be deleted.

Heap variables must be deleted before the end of their scope and before returning; leaks, double deletes, and use-after-delete are checked by the memory safety pass. See [Memory Safety Checking](26-memory-safety-checking.md).

Heap allocation is not zero-initialized. A heap variable is allocated and default-constructed, so a primitive heap variable starts with an indeterminate value, and a heap dynamic array starts empty (length 0, no element storage). Use `init_zero` for zeroed dynamic array storage. Allocation failure raises `std::bad_alloc`.

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
- dereferenced addresses;
- struct field accesses (`obj.field`).

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

## 14.4 Struct Assignment

A struct may be assigned as a whole value if and only if all of its fields are assignable, recursively: assignable = primitive | address | struct-with-all-assignable-fields.

Valid:

```franko
struct X {
    int32_t n
    addr<Y> p
}

X a;
X b;
a = b;    // whole-value assignment
```

Invalid (struct contains a static array field):

```franko
struct W {
    array<int32_t, 4> xs
}

W a;
W b;
a = b;    // invalid: struct with non-assignable fields is address-only
```

Structs with non-assignable fields can still be shared through `addr<X>`: pass or store `getaddr(s)`, and access fields through `deref(p).field`.

See [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md) for field-level assignment targets, and [Structs](24-structs.md) for the full assignability rules.

---

# 15. Lvalues and Storage-Backed Expressions

A storage-backed lvalue is an expression that refers to a real mutable storage location.

Current storage-backed lvalues are:

| Expression | Lvalue? |
|---|---|
| variable | yes |
| array element of storage-backed array | yes |
| `deref(address)` | yes |
| struct field access whose base is an lvalue (`obj.field`) | yes |
| integer literal | no |
| arithmetic expression | no |
| comparison expression | no |
| function call result | no |
| `getaddr(...)` result | no |

Struct field lvalue-ness follows the base expression: `s.field` is an lvalue when `s` is a variable, a `deref(...)`, a nested member access, or an array element of a storage-backed array (static or dynamic). Field access on a function call result is not storage-backed.

See [Structs](24-structs.md) for the complete struct reference.

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

Unary minus is also valid on unsigned integer operands. The result keeps the operand's type; narrowing to a target width happens through the ordinary contextual constant checks and the generated C++ casts.

Unsupported unary operators:

```franko
~x
+x
```

## 16.3 Arithmetic Operators

Arithmetic operators: `+`, `-`, `*`, `/`.

Operands must be integers. Arrays and addresses are invalid.

The modulus operator `%` is not currently implemented.

Division by zero in a compile-time constant expression is an error; see [Constant Folding](10-constant-folding.md).

This applies even when only the divisor is constant: any expression with a constant zero divisor, such as `x / 0` where `x` is a nonconstant variable, is rejected at compile time.

## 16.4 Bitwise Operators

Bitwise operators: `&`, `|`, `^`.

Operands must be integers.

## 16.5 Shift Operators

Shift operators: `<<`, `>>`.

The left operand must be an integer.

The right operand must be an integer. Nonconstant shift counts must have an unsigned integer type. Constant shift counts must be nonnegative and must fit the unsigned variant of the left operand's primitive type.

## 16.6 Comparison Operators

Comparison operators produce `uint8_t`.

Integer comparisons may use mixed integer types subject to constant fitting rules.

Address comparison is supported only between identical address types, and only with the equality operators `==` and `!=`. Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for addresses.

The `null` literal may be compared with an address using `==` and `!=` only. `null` cannot be compared with non-address values, and `0` is not `null`.

## 16.7 Logical Operators

Logical operators: `&&`, `||`, `!`.

Operands must be integers. Results are `uint8_t` values `0` or `1`.

## 16.8 No Address Arithmetic

Addresses are not integers and cannot participate in arithmetic, bitwise, or shift operations.

## 16.9 Result Types

Arithmetic (`+`, `-`, `*`, `/`), bitwise (`&`, `|`, `^`), and shift (`<<`, `>>`) expressions take the type of their nonconstant operand when exactly one operand is a folded integer constant, and the left operand's type otherwise (including when both operands are constants).

Comparisons and logical operators always produce `uint8_t` regardless of operand types.

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

The braces of a function body are an exception: they do not create an additional scope beyond the function's parameter/body scope. See [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md).

## 18.2 If Statements

The condition must be an integer expression.

Braces are optional: the body may be a single statement. A braceless body is wrapped in an implicit block during desugaring, so declarations inside it are scoped to that block and are not visible after the `if`. See [Desugaring](05-desugaring.md).

## 18.3 While Statements

The condition must be an integer expression.

Braces are optional: the body may be a single statement. A braceless body is wrapped in an implicit block during desugaring, so declarations inside it are scoped to that block and are not visible after the `while`. See [Desugaring](05-desugaring.md).

## 18.4 Return Statements

A bare `return` is valid only in a `void` function.

A `return expr` statement is required for non-`void` functions.

## 18.5 Print Statements

`print` is a dedicated statement, not a function and not a member method:

```franko
print(x);
print(a, b, c);      // multiple arguments printed space-separated
print();              // prints a bare newline
```

The checker validates print arguments as expressions usable where a value is required (in particular, `void` expressions are rejected), but it does not impose a strict printable-type policy.

The C++14 backend can actually print:

- integer primitives (with `int8_t`/`uint8_t` promoted so a number is shown rather than a character);
- addresses (printed through `const void*`).

Other value types, such as arrays and structs, are accepted by the checker but have no backend print form, so printing them does not produce compilable C++. Print individual fields instead: `print(s.x)`.

Array initializer lists and string literals are not expressions and cannot be printed directly.

Member-call printing such as `x.print()` is not supported; see [Unsupported or Restricted Features](29-unsupported-or-restricted-features.md).

## 18.6 Expression Statements

Function calls are commonly used as expression statements.

Array intrinsic calls are special expression-statement forms:

```franko
arr(10);
arr.memset(0);
arr.memcpy(getaddr(other));
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

## 19.2 `ndarray` Sugar

`ndarray<T, D1, D2, ..., Dn>` is syntactic sugar for nested static arrays:

```franko
ndarray<byte, 12, 20, 30>
```

means:

```franko
array<array<array<byte, 30>, 20>, 12>
```

Access order follows the dimension list: `arr[i][j][k]` indexes `i` along `12`, `j` along `20`, and `k` along `30`.

Each dimension follows the same static-array size rules above (a positive compile-time integer constant folding to a value that fits `uint32_t`).

## 19.3 Dynamic Arrays

A dynamic array has runtime size:

```franko
array<int32_t> xs;
```

Dynamic arrays are initialized with the array intrinsic `init` (or one of its aliases):

```franko
xs(10);             // shorthand for xs.init(10)
xs.init(10);        // allocate uninitialized storage
xs.init_zero(10);   // allocate zeroed storage
```

Rules for dynamic array initialization:

- target must be a storage-backed lvalue;
- target must be a dynamic array;
- exactly one argument is required;
- size must be compatible with `uint32_t`;
- constant sizes must be greater than zero;
- constant sizes must fit `uint32_t`;
- nonconstant sizes must have exactly type `uint32_t`.

An initialized dynamic array may be resized with `resize`. See [Array Intrinsics](20-array-intrinsics.md).

Dynamic array declaration alone does not allocate element storage.

The array must be initialized before ordinary indexed use.

The call-initialization form is only valid for dynamic arrays:

```franko
int32_t x(5);   // invalid: x is not a dynamic array
```

## 19.4 Array Access

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

## 19.5 Array Elements as Lvalues

Array elements are storage-backed lvalues when the array target is storage-backed.

Array elements may be addressed:

```franko
addr<int32_t> p;
p = getaddr(xs[0]);
```

Nested array elements may also be lvalues when each target is storage-backed.

## 19.6 Array Direct Assignment Is Invalid

Arrays cannot be assigned directly as ordinary values.

Invalid:

```franko
array<int32_t> a;
array<int32_t> b;

a = b;
```

Use one of:

```franko
a.memcpy(getaddr(b));
a.memset(0);
a.uninit();
```

Array initializer assignment is not whole-array value assignment.

## 19.7 Array Lifetime Limitations

The checker validates intrinsic legality, but it does not fully prove:

- dynamic array initialized before indexing;
- dynamic array not used after `uninit`;
- dynamic array not initialized twice;
- all array accesses are within bounds.

---

# 20. Array Intrinsics

Array operations are written using call syntax but lowered into special semantic nodes. They are **not** general user-defined function calls, and only these named intrinsics are recognized.

Intrinsics are **expressions**, not statement-only forms. Status-returning intrinsics may be used in value contexts:

```franko
int32_t status;
status = xs.init(10);
```

Void-returning intrinsics are invalid in value contexts.

| Intrinsic | Receiver | Returns | Status |
|---|---|---|---|
| `target(size)`, `target.init(size)` | dynamic array | `int32_t` | `0` on success, `-1` on allocation failure |
| `target.init_zero(size)` | dynamic array | `int32_t` | `0` on success, `-1` on allocation failure |
| `target.resize(size)` | dynamic array | `int32_t` | `0` on success, `-1` on allocation failure |
| `target.uninit()` | dynamic array | `void` | — |
| `target.memset(value)` | any array | `void` | — |
| `target.memset(value, start, count)` | any array | `void` | — |
| `target.memcpy(source)` | any array | `void` | — |
| `target.memcpy(source, dstStart, srcStart, count)` | any array | `void` | — |
| `target.memmove(dstStart, srcStart, count)` | any array | `void` | — |

The receiver of an array intrinsic must be a storage-backed array lvalue.

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

## 20.1 `init`, `init_zero`, and `target(size)`

Dynamic array initialization.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must have dynamic array type;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32_t`; or
  - a nonconstant expression of exactly type `uint32_t`.

`init` allocates uninitialized element storage. `init_zero` allocates zeroed element storage. `target(size)` is shorthand for `target.init(size)`.

## 20.2 `resize`

Resizes an already-initialized dynamic array.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must be a dynamic array;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32_t`; or
  - a nonconstant expression of exactly type `uint32_t`.

## 20.3 `uninit`

Frees a dynamic array's element storage and resets it to the uninitialized state.

Rules:

- receiver must be a storage-backed lvalue;
- receiver must be a dynamic array;
- takes exactly zero arguments.

## 20.4 `memset`

Fills an array byte-wise.

Forms:

```franko
target.memset(value)                    // whole array
target.memset(value, start, count)      // element range
```

Rules:

- receiver must be a storage-backed lvalue;
- receiver may be a dynamic or static array;
- receiver element type must be memsetable;
- the whole-array form takes exactly one argument; the ranged form takes exactly three;
- fill value must be:
  - a constant fitting `uint8_t`; or
  - a nonconstant expression of exactly type `uint8_t`;
- `start` and `count` must be:
  - nonnegative constants fitting `uint32_t`; or
  - nonconstant expressions of exactly type `uint32_t`;
- for static arrays, constant start/count ranges are checked against the static size at compile time.

## 20.5 Memsetable Element Types

Current memsetable types:

| Element type | Memsetable? |
|---|---|
| primitive integers | yes |
| static arrays of memsetable elements | yes |
| structs whose fields are all memsetable, recursively | yes |
| dynamic arrays | no |
| addresses | no |

## 20.6 `memcpy`

Copies array contents byte-wise according to the generated C++ array model.

Forms:

```franko
target.memcpy(source)                          // whole array
target.memcpy(source, dstStart, srcStart, count)  // element ranges
```

Rules:

- target must be a storage-backed lvalue array;
- source must be an **address to an array** (`addr<array<T>>` or `addr<array<T, N>>`), typically produced with `getaddr(...)`; passing an array value directly is invalid;
- both target and source may be dynamic or static arrays;
- target and source element types must match exactly;
- static/dynamic shape does not need to match;
- static lengths do not need to match;
- target and source element types must be memcpyable;
- `dstStart`, `srcStart`, and `count` must be:
  - nonnegative constants fitting `uint32_t`; or
  - nonconstant expressions of exactly type `uint32_t`;
- for static arrays, constant start/count ranges on both the destination and the source are checked against the static sizes at compile time.

## 20.7 `memmove`

Copies an element range within one array, overlapping ranges included.

Form:

```franko
target.memmove(dstStart, srcStart, count)
```

Rules:

- receiver must be a storage-backed lvalue array (dynamic or static);
- takes exactly three arguments;
- receiver element type must be memcpyable;
- `dstStart`, `srcStart`, and `count` must be:
  - nonnegative constants fitting `uint32_t`; or
  - nonconstant expressions of exactly type `uint32_t`;
- for static arrays, constant ranges are checked against the static size at compile time.

## 20.8 Memcpyable Element Types

Current memcpyable types:

| Element type | Memcpyable? |
|---|---|
| primitive integers | yes |
| addresses | yes |
| static arrays of memcpyable elements | yes |
| structs whose fields are all memcpyable, recursively | yes |
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

String literals are allowed as elements of array initializer lists (see [String Literals](22-string-literals.md)).

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

Using the initializer list on a freshly declared dynamic array without first initializing it:

```franko
array<int32_t> dyn = [1, 2, 3];
```

is **not rejected at compile time**. The generated indexed assignments then access an uninitialized dynamic array, and the runtime aborts with a "Franko_Dynamic_Array not initialized" error. Dynamic array initialization is not statically tracked; see [Arrays](19-arrays.md).

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

Incorrect:

```franko
array<int32_t> dyn;
dyn = [1, 2, 3];
```

This lowers to indexed assignments without first initializing `dyn`. It is not a compile-time error; the generated code accesses an uninitialized dynamic array and fails at runtime.

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

Incorrect:

```franko
array<array<int32_t>> matrix;

matrix(2);

matrix = [
    [1, 2, 3],
    [4, 5, 6]
];
```

because the outer dynamic array is initialized, but `matrix[0]` and `matrix[1]` are not. This is not rejected at compile time; the first generated indexed assignment into the uninitialized inner arrays aborts at runtime.

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

The assignment target is still resolved and checked even though no indexed
assignments are generated: the target expression must be a valid, declared
expression, and an undeclared or otherwise invalid target is still diagnosed.

## 21.10 Array Initializer Lists and Global Scope

Array initializer lists are assignment sugar.

Therefore, they are only valid where the resulting assignment statements are valid.

At global scope, executable statements are not allowed.

Invalid:

```franko
array<int32_t, 3> xs;
xs = [1, 2, 3];
```

Declaration initializer sugar at global scope is also invalid if it desugars into executable statements (assignments, expression-statement calls, or indexed element assignments).

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
- intended for dynamic arrays only when the target dynamic storage already exists (this is not enforced at compile time; missing initialization fails at runtime);
- recursively applicable to nested arrays.

---

# 22. String Literals

Franko supports string literals as **initializer sugar** for byte arrays.

A string literal is written with double quotes:

```franko
"Franko"
"Hello, World!"
```

## 22.1 A String Literal Is Not an Expression Value

A string literal is **not** a general expression value. Like array initializer lists, string literals are meaningful only in assignment/initializer contexts where there is an explicit array target.

A string literal may appear:

- as an assignment initializer: `s = "text";`
- as a declaration initializer: `array<byte, N> s = "text";`
- as an element of an array initializer list: `rows = ["one", "two"];`

It is invalid in ordinary expression positions such as:

```franko
print("hi");        // invalid: print arguments are expressions
foo("hi");          // invalid: function arguments are expressions
return "hi";        // invalid: return values are expressions
"hi"                // invalid: a string literal is not an expression statement
```

## 22.2 Desugaring to UTF-8 Bytes

A string literal desugars into an array initializer list containing the literal's **UTF-8 bytes** as ordinary integer constants:

```franko
"ABC"
```

becomes:

```franko
[65, 66, 67]
```

Multi-byte UTF-8 sequences expand to one element per byte:

```franko
"你好"
```

becomes:

```franko
[228, 189, 160, 229, 165, 189]
```

**No null terminator is appended.**

## 22.3 String Assignment

Because a string literal is array initializer sugar, this:

```franko
array<byte, 6> s;
s = "ABCDE";
```

desugars through the ordinary array initializer lowering to:

```franko
array<byte, 6> s;
s[0] = 65;
s[1] = 66;
s[2] = 67;
s[3] = 68;
s[4] = 69;
```

`s[5]` is untouched.

As with other initializer lists:

- the initializer list itself does **not** initialize, allocate, or resize storage;
- a dynamic array target must already be initialized before string assignment — this requirement is enforced by the runtime, not at compile time (accessing an uninitialized dynamic array aborts with a "Franko_Dynamic_Array not initialized" error);
- assignment into a static array that is too small is rejected by ordinary static array bounds checking.

The target may be any array whose element type can hold byte constants `0..255` under normal assignment rules. `array<byte, N>` (or `array<uint8_t, N>`) is the idiomatic target, but wider integer element types also accept each byte value.

## 22.4 String Declaration Initializers

```franko
array<byte, 6> s = "ABCDE";
```

is ordinary declaration initializer sugar, equivalent to:

```franko
array<byte, 6> s;
s = "ABCDE";
```

which then lowers to the per-byte assignments shown above.

## 22.5 Strings Inside Array Initializer Lists

String literals may appear as elements of an array initializer list. Each string element becomes a nested byte list:

```franko
array<array<byte, 3>, 2> m;
m = ["hi", "ok"];
```

lowers to:

```franko
m[0][0] = 104;  // 'h'
m[0][1] = 105;  // 'i'
m[1][0] = 111;  // 'o'
m[1][1] = 107;  // 'k'
```

The inner target element must itself be an array, and ordinary element assignment rules apply to each generated byte assignment.

## 22.6 Escape Sequences

The lexer accepts the following escapes inside string literals:

| Escape | Meaning |
|---|---|
| `\\` | backslash |
| `\"` | double quote |
| `\a` | bell |
| `\b` | backspace |
| `\f` | form feed |
| `\n` | line feed |
| `\r` | carriage return |
| `\t` | horizontal tab |
| `\v` | vertical tab |

String literals may also contain:

- raw Unicode characters;
- literal tabs;
- literal newlines (multi-line string literals are allowed).

They may **not** contain:

- a raw, unescaped `"`;
- a raw, unescaped backslash;
- unsupported escapes such as `\u1234`, `\xFF`, `\0`, or any other `\<char>` not listed above.

There is no string concatenation operator and no interpolation syntax.

## 22.7 Example

```franko
func main() -> int32_t {
    array<byte> str;     // dynamic array: str(200) and str.uninit() require one
    uint i = 0;

    str(200);
    str.memset(0);
    str = "Hello,\tWorld!\n\"Franko\"";

    while (i < 22) {
        print(str[i]);
        i = i + 1;
    }

    str.uninit();
    return 0;
}
```

See also [Array Initializer Lists](21-array-initializer-lists.md).

---

# 23. Addresses

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

## 23.1 Creating Addresses with `getaddr`

The operand must be a storage-backed lvalue.

If the operand has type `T`, then `getaddr(operand)` has type `addr<T>`.

## 23.2 Dereferencing with `deref`

If the operand has type `addr<T>`, then `deref(...)` has type `T`.

`deref(...)` is also an lvalue.

## 23.3 Nested Addresses

Franko supports addresses to addresses.

## 23.4 Address Assignment

Address assignment requires exact type equality.

Raw integers cannot be converted to addresses.

## 23.5 Address Comparison

Addresses may be compared using only the equality operators:

```franko
==
!=
```

Both operands must have identical address types.

Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for addresses.

Address comparison produces `uint8_t`.

## 23.6 No Address Arithmetic

Addresses are not integers and do not support arithmetic.

## 23.7 Addresses of Arrays

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

## 23.8 The null Literal

`null` is a literal of type `null`. It is valid only where an address is expected:

```franko
addr<int32_t> p;
p = null;               // valid

int32_t x = null;       // invalid: null is only valid for address types
p = 0;                  // invalid: 0 is an integer, not null
```

`null` and `0` are different values. An address cannot be assigned or compared against the integer `0`; use `null` instead.

`null` may be compared with an address using equality only:

```franko
if (p == null) {
    // p is null on this path
}
if (p != null) {
    // p is not null on this path
}
```

Comparing `null` with a non-address value is invalid. Relational comparison (`<`, `>`) of `null` is invalid.

`null` may be passed to `addr<T>` parameters and returned from `addr<T>` functions.

`print(null)` prints the null address as a pointer value.

### 23.8.1 Null-Initialized Address Declarations

Address declarations are **guaranteed null-initialized**, unlike ordinary declarations, which start uninitialized:

```franko
addr<int32_t> p;        // p is null here, guaranteed
print(p == null);       // true
```

This guarantee also applies to address-typed struct fields and to the heap storage of an `alloc`'d address.

### 23.8.2 Nullness Certification

The compiler tracks whether an address variable is null. The only expression that certifies an address as non-null is `getaddr(...)`:

```franko
addr<int32_t> p;            // p is null
deref(p);                   // error: cannot dereference null

p = getaddr(x);             // p is certified non-null
deref(p);                   // valid

addr<int32_t> q = p;        // q copies p's state: non-null
addr<int32_t> r = q;        // r copies q's state: non-null
```

Assignment from another address variable copies that variable's exact state, so a null state propagates through copies and an `getaddr` certification propagates transitively.

Assignment from values of unknown provenance (call results, parameters, struct-field or array-element reads) yields an unknown state.

### 23.8.3 Dereferencing and Warnings

Dereferencing a variable that is **definitely null** is an error.

Dereferencing a variable that is **possibly null** (null on some path, for example after a branch merge) is a **warning**, not an error:

```franko
addr<int32_t> p;
if (cond != 0) {
    p = getaddr(x);
} else {
    p = null;
}
deref(p);                   // warning: possibly null
```

Guarding with `p != null` removes the warning, because the compiler refines the variable's state inside the guard.

## 23.9 Address Lifetime Limitations

Address lifetime is not fully tracked.

The compiler does not fully track dangling addresses or aliasing through addresses. In particular, dereferencing an address whose pointee was freed by `del` cannot be caught at compile time when the address was copied before the delete; a runtime check for this is planned as a later change.

---

# 24. Structs

A struct is a user-defined aggregate type with named, ordered fields:

```franko
struct Point {
    int32_t x
    int32_t y = 0
}

Point p;
p.x = 3;
p.y = 4;
print(p.x + p.y);
```

Structs are nominal types: two distinct struct declarations are distinct types even when their field layouts are identical (see [Type Equality](27-type-equality.md)). This chapter is the complete struct reference; running examples live in [Examples](31-examples.md).

## 24.1 Declaring a Struct

Struct declarations are top-level items, alongside global variable declarations and function declarations:

```franko
struct X {
    int32_t a
    int64_t b = 123
    array<int32_t, 4> xs
}
```

Rules:

- `struct` declarations may only appear at global scope; they cannot be declared inside functions.
- Separators between fields are optional, matching block syntax: a trailing separator before `}` is not required, and `;` and newlines may be mixed freely.
- Duplicate struct names are rejected. Each struct name denotes exactly one declared type.
- Struct names live in their own namespace: a struct may share its name with a variable or a function. The compiler emits a warning for such clashes and the generated C++ uses kind-prefixed names, so the generated code is unambiguous (see [Runtime and Backend Notes](30-runtime-and-backend-notes.md)).

A struct body may only contain field declarations. Assignments, statements, nested functions, and nested struct declarations are invalid inside a struct body.

## 24.2 Field Declarations

A field declaration uses the same `type name` syntax as a variable declaration, plus an optional constant default:

```franko
struct Buffer {
    array<int32_t> data
    int32_t size = 0
}
```

Rules:

- Field types may be: primitive integer types, static arrays (including `ndarray<...>` sugar), dynamic arrays, addresses (`addr<T>`), and other declared struct types.
- `void` is not a valid field type, and a field type naming an undeclared struct is rejected.
- `alloc` is not valid for struct fields. Allocate dynamic array fields explicitly with the allocation call (`field(n)`, see [Array Intrinsics](20-array-intrinsics.md)).
- Duplicate field names within a struct are rejected.
- Fields are kept in declaration order; that order is also the generated field order.
- Field names are ordinary identifiers; keywords cannot be field names.

## 24.3 Field Default Values

A field may declare a default value with `= constExpr`:

```franko
struct Counter {
    int32_t count = 0
    uint32_t limit = 100
}
```

Rules:

- Defaults must be constant expressions (`constExpr`) and are folded at compile time (see [Constant Folding](10-constant-folding.md)).
- Only primitive fields can have default values. Struct, array, and addr fields reject `= constExpr` as a type mismatch.
- A folded default must fit the field's primitive type; an out-of-range default is rejected (for example `int8_t x = 300`).

## 24.4 Using Struct Types

A struct name used in a type position is resolved against the struct namespace:

- Unknown struct names are rejected with an "unknown struct type" error.
- Field types may reference structs declared anywhere in the program, including structs declared later in the file.
- Struct types compose with the other type constructors: `Point p`, `array<Point> xs`, `array<Point, 4> grid`, `addr<Point> pp`, and `ndarray<Point, 2, 3> cells` are all valid declared types.

## 24.5 By-Value Cycles

A struct may not reach itself by value through its fields. The by-value edges are:

- a field whose type is a struct;
- a field whose type is a static array of structs (recursively, including `ndarray` sugar).

An infinite by-value chain would have no size, so such cycles are rejected at compile time:

```franko
struct Node {
    int32_t v
    array<Node, 4> kids    // invalid: static array of Node inside Node
}
```

Indirection breaks the cycle and is allowed:

```franko
struct Node {
    int32_t v
    addr<Node> next        // valid: address edge
    array<Node> kids       // valid: dynamic array edge
}
```

## 24.6 Assignability and Passing by Value

A type is **assignable** when a value of that type can participate in ordinary assignment with `=`:

- primitive integer types are assignable;
- address types `addr<T>` are assignable;
- a struct is assignable if and only if all of its fields are assignable, recursively.

Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing any of them is **address-only**.

Consequences:

- **Whole-value assignment.** For an assignable struct, `a = b;` copies every field. For an address-only struct the assignment is rejected (see [Assignment](14-assignment.md)); share the struct through `addr<X>` and `getaddr(s)` instead.
- **By-value parameters.** A parameter may have an assignable struct type (see [Functions](12-functions.md)); the argument is copied at the call.
- **By-value returns.** A function may return an assignable struct type.
- Diamond-shaped field graphs are fine: a struct with two fields of the same struct type is assignable as long as that type is assignable.

Arrays of structs follow the usual element rules: `array<X, N>` is `memset`-able only when every field of `X` is `memset`-able (addresses excluded), and `memcpy`-able when every field is `memcpy`-able (addresses included). See [Array Intrinsics](20-array-intrinsics.md).

Structs are not integers: operators, `if`/`while` conditions, and `print` reject whole structs. Operate on fields instead.

## 24.7 Member Access and Field Lvalues

Member access uses dot syntax. Access chains compose with every storage form:

```franko
s.field               // local struct
deref(p).field        // through an address
deref(a.next).field   // another hop through an addr field: deref the link first
xs[i].field           // element of a static or dynamic array
```

- `s.field` is a storage-backed lvalue exactly when its base expression is one, so field accesses can be assigned to: `s.field = v;`, `deref(p).field = v;`, `xs[i].field = v;` (see [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md)).
- `getaddr(s.field)` is valid and yields `addr<T>` for the field's type `T`.
- A field may itself be an array, and array intrinsics work on fields:
  `b.data(4)` (allocation shorthand for `b.data.init(4)`), `b.data.init_zero(n)`, `b.data.memset(0)`, `b.data.memcpy(getaddr(other))`, `b.data[i]`, and `b.data.uninit()`.
- Method-like calls on structs (`s.foo()`) are rejected; only the built-in array intrinsics use member-call syntax.

## 24.8 Initialization Semantics

- **Local declarations.** A local struct without an initializer is default-constructed: fields with defaults receive their default value; fields without defaults start with indeterminate values, like ordinary local variables (see [Declarations](11-declarations.md)).
- **Declaration initializer sugar.** `P b = a;` and `P c = f();` are valid for locals and copy the whole struct value; the sugar desugars into a declaration plus a whole-value assignment.
- **Global declarations.** Global structs are statically allocated. Fields with defaults receive their defaults; fields without defaults are zero-initialized in the generated C++ (static storage). This is an implementation characteristic of the current backend; do not rely on it in portable code — give every field an explicit default or assign before use.
- **Heap declarations.** `alloc X s;` allocates and default-constructs the struct: defaults apply, and dynamic-array fields start empty (length 0, no element storage). See [Heap Variables and `alloc`](13-heap-variables-and-alloc.md).

## 24.9 Struct Globals

A struct-typed global is declared like any other global:

```franko
struct Point {
    int32_t x = 0
    int32_t y = 0
}

Point origin;
int32_t scale = 2;
```

- Global variable initializers (`= constExpr`) are for primitive globals only; a struct-typed global rejects `= constExpr` as a type mismatch.
- Struct globals are declared before `main` runs; executable statements remain invalid at global scope (see [Program Structure](03-program-structure.md)).

## 24.10 Heap Structs and Memory Safety

A heap struct is tracked like any other heap variable:

```franko
alloc X s;
del s;
```

- `del s` runs the struct's destructor, which destroys member dynamic arrays, so deleting a heap struct does not leak its member arrays (see [`del` and Delete Checking](25-del-and-delete-checking.md)).
- Nested dynamic-array fields are not tracked as separate heap objects; see [Memory Safety Checking](26-memory-safety-checking.md) for the exact tracking rules and the `uninit()` element caveat.

## 24.11 Backend Notes

The C++14 backend:

- emits each struct as a C++ `struct struct_X { ... };` definition, preceded by a forward-declaration block (`struct struct_X;`) so addresses may reference structs declared later in source order;
- orders struct definitions topologically over by-value field dependencies, so a struct used by value inside another is defined first (`addr` and dynamic-array fields impose no ordering constraint);
- emits field defaults as C++ non-static data member initializers (NSDMI), e.g. `int32_t num = 0;`;
- emits member access as `target.field`, with heap variables lowered through `(*sym)`.

## 24.12 Restrictions Summary

- Structs are declared only at top level; no local or nested struct declarations.
- No methods: only the built-in array intrinsics use member-call syntax.
- No operators on whole structs: arithmetic, comparison, and conditions reject them; `print` accepts only individual fields.
- No struct literals: a struct value is built field-by-field, copied from another struct, or returned by a function.
- `alloc` is invalid for fields; field defaults are `constExpr` on primitive fields only.
- A struct may not reach itself by value (through struct fields or static array elements); `addr<...>` and dynamic-array fields break the cycle.
- A struct with any non-assignable field is address-only: whole-value assignment, by-value parameters, and by-value returns are rejected; use `addr<X>`.
- Duplicate struct names and duplicate field names are rejected.
- The complete list of current language restrictions is in [Unsupported or Restricted Features](29-unsupported-or-restricted-features.md).

---

# 25. `del` and Delete Checking

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

Dynamic array `uninit()` is separate from `del`. `uninit()` frees a heap or stack dynamic array's element buffer; `del` frees a heap-allocated variable object (and, for a heap dynamic array, also its element buffer through the object destructor).

Checking of double delete, use-after-delete, and leaks is performed by the dedicated memory safety pass. See [Memory Safety Checking](26-memory-safety-checking.md).

## 25.1 Delete Checking Limitations

Current limitations:

- delete checking is symbol-based;
- aliasing through addresses is not fully tracked;
- dangling addresses are not fully detected;
- delete state checking is branch-sensitive up to a nesting-depth limit, then path-insensitive;
- deleting in one branch may affect later checking conservatively.

The compiler does not perform full ownership, lifetime, alias, or dangling-address analysis.

---

# 26. Memory Safety Checking

After legality checking, the compiler runs a dedicated **memory safety pass** (`MemorySafetyChecker`) over heap-owned variables.

This pass owns transient memory-state analysis. It checks, per heap variable:

- `del` of non-heap variables;
- double delete;
- use after delete;
- leaks at the end of a scope;
- leaks at return points;
- deletion state merged across branch paths.

The pass is **intra-procedural**: each function body is analyzed separately with a fresh memory state. Called functions are not analyzed for their effects on the caller's heap variables.

## 26.1 Memory States

For each heap variable the checker tracks one of:

| State | Meaning |
|---|---|
| alive | allocated and not deleted on the current path |
| deleted | definitely deleted on the current path |
| maybe-deleted | deleted on some continuing paths and alive on others |
| untracked | internal; variable is registered but not currently live in this traversal |

`maybe-deleted` arises when branch paths merge with different deletion outcomes. Uses and deletes of a `maybe-deleted` variable are invalid because one path may be a use-after-free or double-free.

## 26.2 Delete Rules

- The `del` operand must be a heap variable. Deleting a non-heap variable is an error.
- Deleting an already-deleted variable is an error.
- Deleting a possibly-deleted variable after a branch merge is an error.

## 26.3 Use Rules

- Using a deleted variable is an error.
- Using a possibly-deleted variable after a branch merge is an error.

## 26.4 Leak Rules

- A heap variable must be deleted before its **scope** exits normally; otherwise it leaks ("missing del").
- A heap variable must be deleted before **returning** from the enclosing function; live heap variables at a return statement are reported as leaks.

`alloc` is invalid at global scope, so every heap variable is local to a function or block and is covered by the scope and return leak rules above.

For heap dynamic arrays, remember that `uninit()` only frees the array's internal buffer. The heap array object itself (`alloc array<T>`) must still be freed with `del`.

## 26.5 Branch Handling

`if` / `else` is analyzed by checking each branch from the pre-branch state and merging the states of the continuing paths:

```franko
if (cond != 0) {
    del x;
}
print(x);      // error: x may have already been deleted
```

A branch that **definitely returns** does not contribute its deletion state to the code following the `if`, because that path never continues there:

```franko
if (cond != 0) {
    del x;
    return 0;      // x deleted, but this path exits
}
print(1);          // fine
del x;             // fine
```

Branch-sensitive analysis is limited to a maximum nesting depth (8). Beyond that depth the checker falls back to a conservative path-insensitive merge.

## 26.6 Loop Handling

`while` loops are handled with a bounded abstraction:

- the body is checked once for internal memory errors;
- the body is checked a second time starting from the post-body state, which catches simple loop double-frees such as deleting the same variable on every iteration;
- the state after the loop merges the "zero iterations" and "one iteration" states, since a loop may execute zero times.

Loop termination is not proven; the post-loop state is conservative.

## 26.7 Address Null-State Tracking

Alongside the heap matrix, the checker tracks a separate null-state matrix for address-typed variables, with three states:

| State | Meaning |
|---|---|
| pointing | the variable holds a certified, real storage address |
| null | the variable is definitely null on the current path |
| maybe-null | the variable is null on some continuing paths |

Rules:

- an ordinary `addr<T>` declaration is **null-initialized**, so it starts in the null state;
- an `alloc addr<T>` declaration points at freshly allocated storage and starts in the pointing state;
- assigning the `null` literal makes the target null;
- assigning `getaddr(...)` makes the target pointing; this is the **only** certification of a non-null address;
- assigning another address variable copies that variable's exact state, so a null state propagates through copies and certification propagates transitively;
- assigning a value of unknown provenance (a call result, a parameter, a struct-field or array-element read) yields an unknown state;
- at function entry, every tracked address variable is unknown, because parameters and globals carry values the checker cannot see.

Dereference checking:

- `deref(p)` where `p` is **null** is an **error**;
- `deref(p)` where `p` is **possibly null** is a **warning**.

### 26.7.1 Guard Refinement

Inside `if (p == null)` / `if (p != null)` (either operand order), the checker refines `p`'s state per path: the then-branch of `p != null` sees `p` as pointing, and the code after the loop/branch of a failed guard sees `p` as null.

```franko
addr<int32_t> p;
if (p != null) {
    deref(p);      // valid: p is refined to pointing inside the guard
}
deref(p);          // error: p is provably null after the guard fails
```

When a guard's assertion contradicts a definite current state, that path is statically infeasible; the checker suppresses dereference diagnostics inside it and does not let its state flow onward.

Refinement is path-sensitive up to the same nesting-depth limit as the heap matrix (see Section 26.5); beyond the limit, refinement stops and states merge conservatively.

### 26.7.2 Address Null-State Limitations

- unknown states (call results, parameters, field and element reads) are trusted as non-null by dereference checking, matching pre-`null` behavior; a null value may still flow through them;
- dangling addresses (see the aliasing limitation below) are not detected at compile time; a runtime check is planned as a later change.

## 26.8 Limitations

Current limitations of the memory safety checker:

- checking is **symbol-based**; it does not perform alias analysis, so it does not detect use-after-free through a stored address:

```franko
alloc int x;
addr<int> p;
p = getaddr(x);
del x;
print(deref(p));   // NOT detected by the current checker
```

- checks are intra-procedural; heap effects of called functions are not analyzed;
- branch-path sensitivity is capped by the nesting-depth limit described above;
- it does not model partial heap-layout ownership beyond the single-object `alloc` model.

## 26.9 Structs and Nested Arrays

Memory safety tracking is keyed on heap variables, so a heap struct behaves like any other heap variable:

```franko
alloc X s;
del s;    // checked like any other del
```

Notes on structs with nested dynamic-array fields:

- nested dynamic-array fields are **not** tracked as separate heap objects;
- when a heap struct variable is deleted (`del s`), the generated C++ runs the struct's destructor, which destroys the member arrays, so `alloc X s; del s;` does not leak the inner arrays;
- dynamic array **elements** are not destructed: `uninit()` frees the element storage without running element destructors, so inner dynamic arrays inside struct elements are not freed automatically. Free inner arrays explicitly (per element) before `uninit()`, or store only primitive and address fields in structs used as dynamic array elements.

Struct semantics are described in [Structs](24-structs.md).

See also [`del` and Delete Checking](25-del-and-delete-checking.md) and [Heap Variables and `alloc`](13-heap-variables-and-alloc.md).

---

# 27. Type Equality

Franko uses exact, structural semantic type equality in many places. Type equality is implemented once (in the semantic type classes) and used consistently by overload resolution, assignment checking, return checking, and diagnostics.

## 27.1 Primitive Equality

Primitive types are equal only if they have the same kind.

```text
uint8_t  != uint16_t
int32_t  != uint32_t
int32_t  == int32_t
```

The aliases `int`, `uint`, and `byte` canonicalize to `int32_t`, `uint32_t`, and `uint8_t` respectively.

## 27.2 Dynamic Array Equality

Dynamic arrays are equal if their element types are equal.

```text
array<int32_t> == array<int32_t>
array<int32_t> != array<uint8_t>
```

## 27.3 Static Array Equality

Static arrays are equal if:

- element types are equal;
- sizes are numerically equal after unfolding the constant size expression.

The size comparison is numeric, not textual:

```text
array<int32_t, 10>     == array<int32_t, 0xA>
array<int32_t, 5 + 5>  == array<int32_t, 10>
array<int32_t, 10>     != array<int32_t, 20>
```

## 27.4 Address Equality

Address types are equal if their referenced types are equal.

## 27.5 Function Signature Equality

Function signatures are equal if they have:

- the same function identifier;
- the same ordered list of parameter types.

Return type and parameter names are not part of function signature equality.

## 27.6 Struct Equality

Struct types are nominal: two struct declarations are distinct types even when their field layouts are identical.

```text
struct X { int32_t n }
struct Y { int32_t n }

X != Y
X == X
```

Duplicate struct names are rejected, so each struct name denotes exactly one declared type. See [Structs](24-structs.md) for the complete struct reference.

---

# 28. Declaration Type Validity

A declared variable type is valid if it is one of:

- primitive integer type;
- a declared struct type;
- dynamic array of a valid non-`void` type;
- static array of a valid non-`void` type with valid size;
- address of a valid non-`void` type.

Valid:

```franko
struct X { int32_t n }

int32_t x;
X s;
array<X> xs;
array<uint8_t, 64> bytes;
ndarray<byte, 12, 20> grid;   // sugar for array<array<byte, 20>, 12>
addr<int32_t> p;
addr<array<int32_t>> ap;
```

Invalid:

```franko
void x;
array<void> xs;
addr<void> p;
T s;    // invalid when no struct named T is declared
```

`ndarray` declarations are valid exactly when their unfolded nested static array type is valid: every dimension must satisfy the static array size rules (see [Arrays](19-arrays.md)).

A function return type has separate rules. It may be:

- `void`;
- a primitive integer type;
- an address type; or
- a struct whose fields are all assignable value types, recursively.

Array return types are invalid.

---

# 29. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

## 29.1 Function Restrictions

- function declarations are valid only in global scope;
- nested functions are not supported;
- first-class function values are not supported;
- function pointers are not supported;
- anonymous functions/lambdas are not supported;
- parameter names are not used in overload resolution;
- return type is not used in overload resolution;
- arrays cannot be returned by value;
- arrays cannot be passed as parameters by value; use addr<array<...>> parameters (see [Functions](12-functions.md));
- `void` calls cannot be used in value-producing contexts;
- full path-sensitive return analysis is not currently performed.

## 29.2 No General Methods

Only the built-in array intrinsics use member-call syntax. All other member calls are rejected, including:

```franko
x.print();          // unsupported member call: print is a statement keyword
x.something(1);     // unsupported member call
```

## 29.3 Struct Restrictions

Structs are supported as user-defined aggregate types with named fields, subject to these restrictions:

- struct fields cannot use `alloc`;
- struct field initializers must be constant expressions, and only primitive fields can have them (struct, array, and addr fields reject `= constExpr` as a type mismatch);
- a struct may not reach itself by value through struct fields or static array elements; `addr<Node>` fields and `array<Node>` (dynamic array) fields break the cycle and are allowed;
- a struct is assignable as a whole value (assignment, by-value parameter, by-value return) only if all of its fields are assignable, recursively: assignable = primitive | address | struct-with-all-assignable-fields. Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing any of them is address-only: it can be used only through `addr<X>`;
- structs have no methods: only the built-in array intrinsics use member-call syntax, and method-like calls such as `s.foo()` are rejected.

The complete struct reference, including the reasoning behind each restriction, is in [Structs](24-structs.md).

## 29.4 No Floating-Point Types

Only integer primitives are supported.

## 29.5 No Separate Boolean Type

Boolean results use `uint8_t`.

## 29.6 No Implicit Numeric Conversions for Nonconstants

Nonconstant numeric assignments require exact type equality.

## 29.7 Arrays Are Not Assignable as Values

Arrays cannot be assigned directly as ordinary values.

Array initializer assignment is an exception only in syntax shape, not in value semantics.

## 29.8 Array Initializer Lists Are Not General Values

Array initializer lists are not first-class values.

Invalid:

```franko
[1, 2, 3];
print([1, 2, 3]);
foo([1, 2, 3]);
return [1, 2, 3];
```

## 29.9 No Full Dynamic Array Lifetime Checking

The checker validates intrinsic legality, but it does not fully prove dynamic array lifetime and bounds properties.

## 29.10 Delete Checking Is Limited

Delete checking is symbol-based, not alias-aware, and branch-sensitive only up to a nesting-depth limit (8); beyond that depth the analysis falls back to a conservative path-insensitive merge. See [Memory Safety Checking](26-memory-safety-checking.md) for the full list of rules and limitations.

## 29.11 Address Lifetime Is Not Fully Tracked

The compiler does not fully track dangling addresses or ownership.

## 29.12 Print Checking Is Loose

The checker validates print arguments as expressions but does not enforce a strict printable-type set. The backend can print integer primitives and addresses; printing other types (for example arrays) does not produce compilable C++. See [Statements](18-statements.md).

Array initializer lists and string literals are not expressions and therefore cannot be printed directly.

## 29.13 Unsupported Operators

The following operators do not exist in the grammar; source code using them fails to parse:

```franko
%   // no modulus operator
~x  // no bitwise NOT operator
+x  // no unary plus operator
```

## 29.14 String Literals Are Initializer-Only

String literals are supported as byte-array initializer sugar (see [String Literals](22-string-literals.md)), but they are not general expression values.

Invalid:

```franko
print("hello");
foo("hello");
return "hello";
```

## 29.15 No `for` Loops, `break`, or `continue`

Only `if` and `while` control flow is implemented. There are no C-style `for` loops and no `break`/`continue` statements. There is also no `switch`, no `do`/`do-while`, and no `goto`.

## 29.16 Global Scope Is Declaration-Only

Only struct declaration nodes, variable declaration nodes, and function declaration nodes are valid at global scope.

Executable statements are invalid at global scope.

## 29.17 No Casts, Compound Assignment, Increments, Ternary, or Multiple Declarators

The following forms do not exist in the grammar:

- cast syntax (there is no explicit conversion operator; fluid integer constants are the only cross-width mechanism);
- compound assignment operators (`+=`, `-=`, `*=`, `/=`, and similar) — only plain `=` exists;
- increment and decrement operators (`++`, `--`);
- the ternary conditional operator (`?:`);
- multiple declarators in one declaration (`int a, b;` is invalid; declare each variable separately).

---

# 30. Runtime and Backend Notes

The current compiler targets C++14.

## 30.1 Pipeline Order

```text
Franko source
  -> lexer + parser
  -> parser AST
  -> Desugarer
  -> SemanticAnalyzer
  -> LegalityChecker
  -> MemorySafetyChecker
  -> C++14 code generation
```

## 30.2 Memory Allocation

The generated code allocates through bundled jemalloc (`je_malloc`, `je_calloc`, `je_realloc`, `je_free`).

Heap variables (`alloc T x`) compile to a jemalloc-allocated pointer with placement construction, and `del x` compiles to the object destructor plus `je_free`.

Dynamic array element buffers are allocated by the array runtime methods and freed by `uninit()` or by the array object's destructor.

## 30.3 Array Representation

Some Franko rules mirror the generated C++ representation, especially array behavior.

For example, array `memcpy` is designed to support combinations such as:

```text
Static<T, N>.memcpy(Static<T, M>)
Static<T, N>.memcpy(Dynamic<T>)
Dynamic<T>.memcpy(Dynamic<T>)
Dynamic<T>.memcpy(Static<T, M>)
```

Static array sizes must be greater than zero because generated C++ uses fixed-size arrays, and zero-length C++ arrays are not standard.

Static and dynamic arrays runtime-check indexes and intrinsics and raise `std::runtime_error` (via the runtime panic helper) on violations such as out-of-bounds access, use of an uninitialized dynamic array, double initialization, or zero-size allocation.

## 30.4 Ordering of Top-Level Analysis

Function signatures are registered before function bodies are analyzed so that forward references, direct recursion, and mutual recursion can be resolved.

Global variable declarations are also registered before function bodies are analyzed, so functions can reference globals declared later in source order.

## 30.5 Desugaring Before Checking

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

String literals are converted to byte array literals in the same pass (see [String Literals](22-string-literals.md)).

`ndarray` types are lowered to nested static arrays before type analysis.

The backend does not need to implement initializer lists or strings as runtime values because they are not values in Franko.

## 30.6 Integer Literal Emission

Large integer literals are emitted so that they remain well-formed C++14. In particular, an unsuffixed decimal literal whose value exceeds `LLONG_MAX` is emitted with a trailing `ULL` suffix, because decimal literals in C++14 do not fall back to unsigned integer types.

## 30.7 Print Emission

`print` arguments are emitted as `std::cout` insertions:

- plain integer primitives are inserted directly;
- `int8_t`/`uint8_t` values are promoted with unary `+` so a number is printed rather than a character;
- addresses are cast to `const void*` to avoid accidental string printing.

See [Statements](18-statements.md) for which types are meaningfully printable.

## 30.8 Identifier Emission and Name Clashes

Franko keeps struct type names, variable names, and function names in separate namespaces, but C++14 does not. To keep the generated code unambiguous, every declared identifier is emitted with a kind prefix:

| Franko construct | Generated C++ name |
|---|---|
| struct type `X` | `struct_X` |
| variable `x` (local, global, parameter, heap) | `var_x` |
| function `f` | `func_f` |
| the entry-point function `main` | `main` (unprefixed: the generated C++ entry point IS the Franko `main`) |

So a declaration `X s;` is emitted as `struct_X var_s;`.

Because of the prefixes, a variable, function, and struct may share one source name without colliding in the generated C++.

When a variable or function shares its name with a declared struct, the compiler emits a **warning** during semantic analysis (the program still compiles):

```text
==== Warnings ====
  - Variable 'X' shares its name with struct 'X'; generated C++ uses var_X for the variable and struct_X for the struct type
```

Struct definitions are emitted after a forward-declaration block (`struct struct_X;` for every declared struct), so `addr<X>` fields and pointer references may name structs declared later in source order.

---

# 31. Examples

Most executable examples should be placed inside a function body, typically `main`, because global scope allows only declarations.

## 31.1 Basic Integers

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

## 31.2 Static Array Initializer Assignment

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

## 31.3 Static Partial Initializer Assignment

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs = [1, 2];

    return 0;
}
```

`xs[2]` is untouched.

## 31.4 Dynamic Array Initializer Assignment

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

## 31.5 Runtime Expressions Inside Initializer Lists

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

## 31.6 Nested Static Arrays

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

## 31.7 Nested Dynamic Arrays

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

## 31.8 Declaration Initializer with Static Array

```franko
func main() -> int32_t {
    array<int32_t, 3> xs = [1, 2, 3];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 31.9 Dynamic Array Declaration Initializer Without Initialization

```franko
func main() -> int32_t {
    array<int32_t> xs = [1, 2, 3];

    return 0;
}
```

This desugars to indexed assignments without initializing dynamic storage. It is not rejected at compile time; the generated code accesses an uninitialized dynamic array and the runtime aborts with a "Franko_Dynamic_Array not initialized" error.

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

## 31.10 Array Initializer Through Address

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

## 31.11 String Literal Assignment

```franko
func main() -> int32_t {
    array<byte> str;     // dynamic array: str(200) and str.uninit() require one
    uint i = 0;

    str(200);
    str.memset(0);
    str = "Hello,\tWorld!\n\"Franko\"";

    while (i < 22) {
        print(str[i]);
        i = i + 1;
    }

    str.uninit();
    return 0;
}
```

See [String Literals](22-string-literals.md).

## 31.12 `ndarray` Grid

```franko
func fill(addr<ndarray<byte, 2, 3>> grid) -> void {
    deref(grid).memset(0);
    deref(grid)[0][1] = 7;
}

func main() -> int32_t {
    ndarray<byte, 2, 3> grid;

    fill(getaddr(grid));

    print(grid[0][1]);
    print(grid[1][2]);

    return 0;
}
```

`ndarray<byte, 2, 3>` is sugar for `array<array<byte, 3>, 2>`.

## 31.13 Status-Returning Intrinsics

```franko
func main() -> int32_t {
    array<int32_t> xs;
    int32_t status = 0;

    status = xs.init_zero(4);

    if (status != 0) {
        return -1;
    }

    status = xs.resize(8);

    if (status != 0) {
        xs.uninit();
        return -1;
    }

    print(status);

    xs.uninit();
    return 0;
}
```

## 31.14 Struct Declaration and Field Access

```franko
struct Point {
    int32_t x;
    int32_t y
}

func main() -> int32_t {
    Point p;

    p.x = 10;
    p.y = 20;

    print(p.x);
    print(p.y);

    return 0;
}
```

The separator before `}` is optional.

## 31.15 Struct Field Defaults

Field defaults are constant expressions; only primitive fields can have them.

```franko
struct Counter {
    int32_t count = 0;
    uint32_t limit = 100
}

func main() -> int32_t {
    Counter c;

    print(c.count);   // 0
    print(c.limit);   // 100

    return 0;
}
```

## 31.16 Whole-Struct Assignment

A struct is assignable as a whole value only if all of its fields are assignable, recursively.

```franko
struct Pair {
    int32_t a;
    int32_t b
}

func main() -> int32_t {
    Pair p;
    Pair q;

    p.a = 1;
    p.b = 2;

    q = p;            // whole-value copy

    print(q.a);       // 1
    print(q.b);       // 2

    return 0;
}
```

## 31.17 Structs Passed by Value and by Address

Assignable structs may be passed and returned by value; address-only structs must go through `addr<X>`.

```franko
struct Point {
    int32_t x;
    int32_t y
}

func dist2(Point p) -> int32_t {
    return p.x * p.x + p.y * p.y;
}

func move(addr<Point> p) -> void {
    deref(p).x = deref(p).x + 1;
    deref(p).y = deref(p).y + 1;
}

func main() -> int32_t {
    Point p;

    p.x = 3;
    p.y = 4;

    print(dist2(p));   // 25

    move(getaddr(p));

    print(p.x);        // 4
    print(p.y);        // 5

    return 0;
}
```

## 31.18 Structs with Array Fields

An array field makes the struct non-assignable: the struct is address-only and its arrays are managed through the usual intrinsics.

```franko
struct Buffer {
    array<int32_t> data;
    int32_t size = 0
}

func main() -> int32_t {
    Buffer b;

    b.data(4);          // allocate the dynamic array field
    b.data.memset(0);
    b.size = 4;

    b.data[0] = 10;
    b.data[1] = 20;

    print(b.data[0]);
    print(b.size);

    b.data.uninit();
    return 0;
}
```

`alloc` is not allowed in struct fields; allocate dynamic array fields explicitly with `field(n)` as above.

## 31.19 Structs on the Heap

```franko
struct Node {
    int32_t value;
    addr<Node> next
}

func main() -> int32_t {
    alloc Node a;
    alloc Node b;

    a.value = 1;
    b.value = 2;

    a.next = getaddr(b);
    print(deref(a.next).value);   // follows the addr field: deref first

    del a;
    del b;
    return 0;
}
```

`addr<Node>` fields break by-value cycles, so self-referential struct definitions like this are valid.

Franko has no null addresses. An address field that is never assigned is indeterminate, so only follow links that have been explicitly assigned (here `b.next` is never set and is never read).

## 31.20 Struct Globals

Global variable declarations accept constant initializers.

```franko
struct Point {
    int32_t x = 0;
    int32_t y = 0
}

int32_t scale = 2;
Point origin;

func main() -> int32_t {
    origin.x = scale;

    print(origin.x);   // 2
    print(scale);      // 2

    return 0;
}
```

Global declarations are checked before `main` runs; executable statements remain invalid at global scope.
