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

***

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
12. [Functions](#12-functions)
13. [Heap Variables and `alloc`](#13-heap-variables-and-alloc)
14. [Assignment](#14-assignment)
15. [Lvalues and Storage-Backed Expressions](#15-lvalues-and-storage-backed-expressions)
16. [Operators](#16-operators)
17. [Conditions](#17-conditions)
18. [Statements](#18-statements)
19. [Arrays](#19-arrays)
20. [Array Intrinsics](#20-array-intrinsics)
21. [Addresses](#21-addresses)
22. [`del` and Delete Checking](#22-del-and-delete-checking)
23. [Type Equality](#23-type-equality)
24. [Declaration Type Validity](#24-declaration-type-validity)
25. [Unsupported or Restricted Features](#25-unsupported-or-restricted-features)
26. [Runtime and Backend Notes](#26-runtime-and-backend-notes)
27. [Examples](#27-examples)

***

# 1. Overview

Franko is a small statically typed language with:

* fixed-width signed and unsigned integer types,
* dynamic arrays,
* static arrays,
* typed addresses using `addr<T>`,
* explicit address operations:
  * `getaddr(...)`,
  * `deref(...)`,
* user-defined functions,
* function overloading,
* function calls inside function bodies,
* `void` and non-`void` return types,
* global variable declarations,
* global function declarations,
* assignment,
* blocks,
* `if`,
* `while`,
* `return`,
* `print`,
* `del`,
* heap-owned variables using `alloc`,
* array intrinsics:
  * `arr(size)`,
  * `arr.uninit()`,
  * `arr.memset(value)`,
  * `arr.memcpy(source)`.

Franko currently does **not** implement:

* nested functions,
* first-class function values,
* anonymous functions or lambdas,
* function pointers,
* structs/classes,
* fields,
* general methods,
* generics,
* imports/modules,
* floating-point types,
* a distinct boolean type,
* strings as a checked semantic type,
* implicit numeric conversions except for fluid integer constants.

Franko currently targets C++14.

***

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

***

# 3. Program Structure

A Franko program is a sequence of **top-level items**.

Current valid top-level items are:

* global variable declarations;
* function declarations.

Executable statements are **not** currently allowed in global scope.

Invalid global-scope items include:

* assignments;
* `print`;
* function calls;
* `if`;
* `while`;
* `return`;
* `del`;
* blocks;
* array intrinsic calls.

Execution should be rooted in a user-defined `main` function.

Example:

```franko
func main() -> int32_t {
    return 0;
}
```

Valid global declarations:

```franko
uint32_t counter;

func main() -> int32_t {
    counter = 1;
    return 0;
}
```

Invalid global executable statement:

```franko
uint32_t counter;

counter = 1; // invalid at global scope
```

Invalid global function call:

```franko
func hello() -> void {
    print(1);
}

hello(); // invalid at global scope
```

Function calls are valid inside function bodies:

```franko
func hello() -> void {
    print(1);
}

func main() -> int32_t {
    hello();
    return 0;
}
```

Blocks inside function bodies introduce nested lexical scopes:

```franko
func main() -> int32_t {
    int32_t x;
    x = 0;

    {
        int32_t y;
        y = 1;
        x = y;
    }

    return x;
}
```

***

# 4. Lexical Scoping and Symbols

Franko uses lexical scope.

Function bodies can resolve names from:

* their parameter scope,
* local block scopes,
* enclosing block scopes,
* global variable scope,
* global function declarations for calls.

***

## 4.1 Declaration Lookup

Variable references resolve to the nearest declaration in the current scope stack.

```franko
uint32_t g;

func main() -> int32_t {
    int32_t x;

    {
        int32_t y;
        x = 1; // refers to outer local x
        y = 2; // refers to inner y
        g = 3; // refers to global g
    }

    return x;
}
```

***

## 4.2 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

Invalid:

```franko
func main() -> int32_t {
    int32_t x;
    uint8_t x; // invalid

    return 0;
}
```

Duplicate global variable declarations are also invalid:

```franko
int32_t x;
uint8_t x; // invalid
```

***

## 4.3 Shadowing

Shadowing in an inner block is allowed.

```franko
func main() -> int32_t {
    int32_t x;

    {
        uint8_t x; // allowed: different inner scope
        x = 1;     // refers to inner x
    }

    x = 2;         // refers to outer x

    return x;
}
```

***

## 4.4 Use Before Declaration

Local variables must be declared before they are used within their lexical scope.

Invalid:

```franko
func main() -> int32_t {
    x = 10; // invalid: local x has not been declared
    int32_t x;

    return 0;
}
```

Global variable declarations are registered before function bodies are analyzed.

Therefore, a function may reference a global variable declared later in the source file:

```franko
func main() -> int32_t {
    g = 10;
    return 0;
}

uint32_t g;
```

This is valid because `g` is a global variable declaration.

However, global scope itself may contain only variable declarations and function declarations. Global assignments are still invalid:

```franko
uint32_t g;

g = 10; // invalid: assignment is not allowed at global scope
```

***

## 4.5 Undeclared Variables

Using an undeclared variable is illegal.

```franko
func main() -> int32_t {
    x = 1; // invalid if x was never declared
    return 0;
}
```

***

# 5. Desugaring

Some Franko source forms are accepted as convenient syntax and rewritten before semantic checking.

Desugaring does not change program meaning. It rewrites compact user-facing syntax into simpler core forms used by the semantic analyzer.

After desugaring, normal declaration, assignment, array initialization, heap, and type checking rules apply.

***

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

So:

```franko
int x = 10;
```

semantically behaves exactly like a declaration followed by an assignment.

The initializer must obey normal assignment rules.

Valid inside a function body:

```franko
func main() -> int32_t {
    uint8_t x = 255;
    return 0;
}
```

Invalid:

```franko
func main() -> int32_t {
    uint8_t x = 256; // invalid: 256 does not fit uint8_t
    return 0;
}
```

At global scope, declaration initializer sugar is currently invalid if it desugars into an assignment, because global executable statements are not allowed.

Invalid:

```franko
uint32_t g = 1; // invalid at global scope for now
```

Use:

```franko
uint32_t g;

func main() -> int32_t {
    g = 1;
    return 0;
}
```

***

## 5.2 Allocated Dynamic Array Initialization Sugar

This:

```franko
alloc array<int> arr(20);
```

is accepted in statement contexts where the desugared initialization statement is legal, and is desugared to:

```franko
alloc array<int> arr;
arr(20);
```

So:

```franko
alloc array<int> arr(20);
```

means:

1. declare `arr` as a heap-owned dynamic array of `int`;
2. initialize `arr` with dynamic size `20`.

The desugared `arr(20);` still follows the normal dynamic array initialization rules.

At global scope this form is invalid for now because the initialization component is an executable statement.

***

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

***

# 7. Primitive Integer Types

Franko supports fixed-width signed and unsigned integer types.

| Type       | Meaning                 | Range                                           |
| ---------- | ----------------------- | ----------------------------------------------- |
| `int8_t`   | signed 8-bit integer    | `-128` to `127`                                 |
| `int16_t`  | signed 16-bit integer   | `-32768` to `32767`                             |
| `int32_t`  | signed 32-bit integer   | `-2147483648` to `2147483647`                   |
| `int64_t`  | signed 64-bit integer   | `-9223372036854775808` to `9223372036854775807` |
| `uint8_t`  | unsigned 8-bit integer  | `0` to `255`                                    |
| `uint16_t` | unsigned 16-bit integer | `0` to `65535`                                  |
| `uint32_t` | unsigned 32-bit integer | `0` to `4294967295`                             |
| `uint64_t` | unsigned 64-bit integer | `0` to `18446744073709551615`                   |

***

## 7.1 Integer Type Aliases

Franko supports the convenient alias:

| Alias | Canonical type |
| ----- | -------------- |
| `int` | `int32_t`      |

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

***

## 7.2 Boolean-Like Values

Franko currently does not have a separate `bool` type.

Boolean-like results from comparison and logical operators use `uint8_t`, with:

```text
0 = false
1 = true
```

***

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

* `+123` may be accepted as part of integer literal syntax.
* Unary `+x` is **not** currently implemented as a semantic unary operator.

***

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

Another example:

```franko
func main() -> int32_t {
    uint8_t x;
    x = 10;

    x = x + 1;   // valid: 1 fits uint8_t
    x = x + 255; // valid: 255 fits uint8_t
    x = x + 256; // invalid: 256 does not fit uint8_t

    return 0;
}
```

Pure constant expressions remain fluid until used in a contextual position:

```franko
func main() -> int32_t {
    uint8_t x;

    x = 1 + 2; // valid: folded to 3, fits uint8_t
    x = 1000;  // invalid: does not fit uint8_t

    return 0;
}
```

Fluid constants also participate in function overload resolution. A folded integer constant may match a primitive integer parameter if its `BigInteger` value fits that parameter type.

***

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
1 / 0    // division by zero error during checking
1 << -1  // invalid shift count
```

Constants are folded using arbitrary-precision `BigInteger`, not fixed-width wrapping arithmetic.

Integer overflow is not applied during folding. Range checking happens when the value is used in a concrete type context.

***

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

***

## 11.1 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

Invalid:

```franko
func main() -> int32_t {
    int x;
    uint8_t x; // invalid: duplicate declaration in the same scope

    return 0;
}
```

***

## 11.2 Shadowing

A variable may be redeclared in an inner block, creating a new variable that shadows the outer one.

```franko
func main() -> int32_t {
    int x;

    {
        uint8_t x;
        x = 1; // refers to inner x
    }

    x = 2; // refers to outer x

    return x;
}
```

***

## 11.3 Use Before Declaration

Local variables must be declared before they are used.

Invalid:

```franko
func main() -> int32_t {
    x = 10; // invalid if x has not been declared
    int32_t x;

    return 0;
}
```

Global variables are registered before function bodies are checked, so a function may refer to a later global declaration.

***

## 11.4 Global Variable Declarations

Variable declarations are allowed at global scope.

Global variable declarations create storage that can be referenced by function bodies.

Example:

```franko
uint32_t counter;

func main() -> int32_t {
    counter = 1;
    print(counter);
    return 0;
}
```

Global variables may be declared before or after functions that use them:

```franko
func main() -> int32_t {
    g = 123;
    return 0;
}

uint32_t g;
```

This is valid because the compiler registers all global variable declarations before analyzing function bodies.

Only declarations are allowed at global scope. Initializer sugar at global scope is not currently allowed if it desugars into an assignment.

Invalid:

```franko
uint32_t g = 1; // invalid at global scope for now
```

Invalid:

```franko
uint32_t g;

g = 1; // invalid: global assignment
```

Global variables follow the same declaration type validity rules as local variables.

Duplicate global variable declarations are invalid:

```franko
uint32_t x;
uint8_t x; // invalid: duplicate global declaration
```

***

# 12. Functions

Franko supports user-defined functions.

Function declarations are global top-level items.

Function declarations are not valid inside blocks or inside other function bodies.

***

## 12.1 Function Declaration Syntax

A function declaration uses the `func` keyword:

```franko
func name(parameters) -> returnType {
    statements
}
```

A function declaration must have:

* the `func` keyword;
* an identifier;
* a parameter list, even if empty;
* an explicit return type after `->`;
* a block body.

Example:

```franko
func main() -> int32_t {
    return 0;
}
```

Empty parameter lists are written with `()`:

```franko
func hello() -> void {
    print(1);
}
```

***

## 12.2 Function Declaration Placement

Function declarations are valid only in global scope.

Valid:

```franko
func f() -> int32_t {
    return 1;
}
```

Invalid:

```franko
func main() -> int32_t {
    func nested() -> int32_t {
        return 1;
    }

    return 0;
}
```

Nested functions are not currently supported.

***

## 12.3 Function Parameters

Function parameters are treated as local variables inside the function body.

Parameter names are required because they define variable names available inside the function body.

Example:

```franko
func addOne(uint32_t x) -> uint32_t {
    return x + 1;
}
```

Duplicate parameter names inside the same function are invalid:

```franko
func bad(int32_t x, uint32_t x) -> int32_t {
    return x;
}
```

This is invalid because parameters occupy the same initial function-body scope.

Parameters cannot have type `void`.

Invalid:

```franko
func f(void x) -> void {
}
```

***

## 12.4 `void`

`void` is valid as a function return type.

```franko
func logDone() -> void {
    print(1);
}
```

`void` is not an ordinary value type.

It is invalid for:

* variables;
* array element types;
* address referenced types;
* function parameters.

Invalid:

```franko
void x;
array<void> xs;
addr<void> p;

func f(void x) -> void {
}
```

`void` is allowed only where the language explicitly permits it, currently as a function return type.

***

## 12.5 Function Signatures and Overloading

Franko supports function overloading.

Function declarations are grouped by function identifier.

A function signature is determined by:

* the function identifier;
* the ordered list of parameter types.

Parameter order is part of the function signature.

Parameter names are not part of the function signature.

Return type is not part of the function signature.

Valid overloads:

```franko
func f(int32_t x) -> int32_t {
    return x;
}

func f(uint32_t x) -> uint32_t {
    return x;
}
```

These are valid because the signatures differ:

```text
f(int32_t)
f(uint32_t)
```

Valid overloads by parameter order:

```franko
func f(int32_t x, uint32_t y) -> int32_t {
    return x;
}

func f(uint32_t y, int32_t x) -> int32_t {
    return x;
}
```

These are different signatures:

```text
f(int32_t, uint32_t)
f(uint32_t, int32_t)
```

Duplicate declarations are invalid even if parameter names differ:

```franko
func f(int32_t x) -> int32_t {
    return x;
}

func f(int32_t y) -> int32_t {
    return y;
}
```

Duplicate declarations are invalid even if return types differ:

```franko
func f(int32_t x) -> int32_t {
    return x;
}

func f(int32_t x) -> uint32_t {
    return 0;
}
```

Both declarations have the same signature:

```text
f(int32_t)
```

Duplicate function declarations are compilation errors.

A duplicate function declaration is not inserted into the overload table and does not participate in overload resolution.

***

## 12.6 Function Call Resolution

A function call resolves by:

* callee identifier;
* ordered argument expressions.

Parameter names are not used in call resolution.

A function call is valid if and only if exactly one overload is applicable.

An overload is applicable when each argument matches the corresponding parameter in order.

For nonconstant arguments:

* the argument type must exactly match the parameter type.

For folded integer constants:

* the constant is fluid;
* it may match a primitive integer parameter if its `BigInteger` value fits that parameter type.

Example:

```franko
func f(uint8_t x) -> void {
}

func main() -> int32_t {
    f(255); // valid
    return 0;
}
```

Invalid:

```franko
func f(uint8_t x) -> void {
}

func main() -> int32_t {
    f(256); // invalid: 256 does not fit uint8_t
    return 0;
}
```

Ambiguous:

```franko
func f(uint8_t x) -> void {
}

func f(uint32_t x) -> void {
}

func main() -> int32_t {
    f(1); // invalid: 1 fits both uint8_t and uint32_t
    return 0;
}
```

If no overload is applicable, the call is invalid.

If more than one overload is applicable, the call is ambiguous and invalid.

Address arguments match only by exact address type.

Array arguments currently cannot be passed by value because arrays are not assignable value types in ordinary Franko assignment. To pass arrays to functions, use addresses to arrays.

Example:

```franko
func clear(addr<array<int32_t>> p) -> void {
    deref(p).memset(0);
}
```

***

## 12.7 Bare Function Names

A bare function name is not a value.

Invalid:

```franko
func f() -> int32_t {
    return 1;
}

func main() -> int32_t {
    print(f); // invalid
    return 0;
}
```

Function values are not currently supported.

***

## 12.8 Function Call Expressions

A function call expression has the return type of the selected function.

```franko
func getValue() -> int32_t {
    return 10;
}

func main() -> int32_t {
    int32_t x;
    x = getValue();
    return x;
}
```

A `void`-returning function call is valid as an expression statement:

```franko
func logDone() -> void {
    print(1);
}

func main() -> int32_t {
    logDone();
    return 0;
}
```

A `void`-returning function call is invalid in value-producing contexts:

```franko
func logDone() -> void {
}

func main() -> int32_t {
    int32_t x;
    x = logDone(); // invalid
    return 0;
}
```

***

## 12.9 Return Statements

Return checking is handled by later legality/type checkers, not by the initial semantic analyzer.

A `void` function:

* is not required to contain a return statement;
* may use bare `return`;
* must not use `return expr`.

Valid:

```franko
func f() -> void {
}
```

Valid:

```franko
func f() -> void {
    return;
}
```

Invalid:

```franko
func f() -> void {
    return 1;
}
```

A non-`void` function:

* must contain a return statement with an expression;
* must not use bare `return`;
* must return an expression compatible with the declared return type.

Valid:

```franko
func getValue() -> int32_t {
    return 10;
}
```

Invalid:

```franko
func getValue() -> int32_t {
}
```

Invalid:

```franko
func getValue() -> int32_t {
    return;
}
```

Return compatibility for non-`void` functions:

* constant integer expressions may be accepted if the constant value fits in the declared return type;
* nonconstant expressions must have exactly the same semantic type as the declared return type;
* returned address expressions must exactly match the declared address type.

***

## 12.10 Function Return Types

A function return type must be either:

* `void`;
* a primitive integer type;
* an address type, `addr<T>`.

Invalid function return types currently include:

* dynamic arrays, `array<T>`;
* static arrays, `array<T, N>`.

Arrays cannot be returned directly because arrays are not assignable in Franko.

Returning an array by value would imply an implicit array copy or assignment, but Franko requires array movement to be explicit through array intrinsics and addresses.

If a function needs to operate on an array, pass an address to the array:

```franko
func fill(addr<array<int32_t>> p) -> void {
    deref(p).memset(0);
}
```

If a function needs to return access to an array, return an address to the array:

```franko
func getArray(addr<array<int32_t>> p) -> addr<array<int32_t>> {
    return p;
}
```

For static arrays, use an address to the exact static array type:

```franko
func getBuffer(
    addr<array<uint8_t, 128>> p
) -> addr<array<uint8_t, 128>> {
    return p;
}
```

Invalid dynamic array return:

```franko
func makeArray() -> array<int32_t> {
    array<int32_t> arr;
    arr(10);
    return arr;
}
```

Invalid static array return:

```franko
func makeBuffer() -> array<uint8_t, 128> {
    array<uint8_t, 128> buffer;
    buffer.memset(0);
    return buffer;
}
```

`void` is a special valid function return type. It does not represent an assignable value.

***

## 12.11 Forward References and Recursion

Function signatures are registered before function bodies are analyzed.

Therefore, function bodies may reference functions declared later in the same program.

Valid forward reference:

```franko
func a() -> int32_t {
    return b();
}

func b() -> int32_t {
    return 1;
}
```

Direct recursion is allowed if the recursive call matches the function's registered signature:

```franko
func countdown(int32_t x) -> int32_t {
    return countdown(x - 1);
}
```

Mutual recursion is allowed if all involved function signatures are registered before body analysis:

```franko
func even(int32_t x) -> int32_t {
    return odd(x - 1);
}

func odd(int32_t x) -> int32_t {
    return even(x - 1);
}
```

Termination and full return-path legality are separate checker concerns.

***

# 13. Heap Variables and `alloc`

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
func main() -> int32_t {
    alloc int x;

    x = 10;
    del x;

    return 0;
}
```

Non-heap variables cannot be deleted.

Invalid:

```franko
func main() -> int32_t {
    int x;

    del x; // invalid: x is not heap-owned

    return 0;
}
```

***

## 13.1 Allocated Dynamic Array Initialization Sugar

Allocated dynamic arrays may use compact declaration-plus-initialization syntax in statement contexts:

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

1. declare `arr` as a heap-owned dynamic array of `int`;
2. initialize `arr` with dynamic size `20`.

The size expression must satisfy normal dynamic array size rules.

***

# 14. Assignment

Assignment syntax:

```franko
target = value;
```

The left-hand side must be a storage-backed lvalue.

Valid assignment targets include:

* variables,
* array elements,
* dereferenced addresses.

Examples:

```franko
func main() -> int32_t {
    int32_t x;
    x = 1;

    array<int32_t> arr;
    arr(10);
    arr[0] = 42;

    addr<int32_t> p;
    p = getaddr(x);
    deref(p) = 100;

    return 0;
}
```

Invalid assignment targets:

```franko
1 = x;           // invalid
x + y = 3;       // invalid
getaddr(x) = p;  // invalid
```

***

## 14.1 Primitive Assignment

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

***

## 14.2 Address Assignment

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

***

## 14.3 Array Assignment

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

***

# 15. Lvalues and Storage-Backed Expressions

A storage-backed lvalue is an expression that refers to a real mutable storage location.

Current storage-backed lvalues are:

| Expression                            | Lvalue? |
| ------------------------------------- | ------- |
| variable                              | yes     |
| array element of storage-backed array | yes     |
| `deref(address)`                      | yes     |
| integer literal                       | no      |
| arithmetic expression                 | no      |
| comparison expression                 | no      |
| function call result                  | no      |
| `getaddr(...)` result                 | no      |

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

* assignment targets,
* `getaddr(...)` operands,
* array intrinsic receivers,
* `memcpy` sources.

***

# 16. Operators

Franko operators apply primarily to integer expressions.

Addresses have special comparison rules.

Arrays are not arithmetic values.

***

## 16.1 Operator Precedence

From highest precedence to lowest:

| Precedence | Operators / Forms                                                    |    |    |
| ---------: | -------------------------------------------------------------------- | -- | -- |
|          1 | postfix expressions, function calls, array access `a[i]`, `deref(a)` |    |    |
|          2 | unary `-x`, `!x`                                                     |    |    |
|          3 | multiplicative `*`, `/`                                              |    |    |
|          4 | additive `+`, `-`                                                    |    |    |
|          5 | shift `<<`, `>>`                                                     |    |    |
|          6 | relational `<`, `<=`, `>`, `>=`                                      |    |    |
|          7 | equality `==`, `!=`                                                  |    |    |
|          8 | bitwise AND `&`                                                      |    |    |
|          9 | bitwise XOR `^`                                                      |    |    |
|         10 | bitwise OR \`                                                        | \` |    |
|         11 | logical AND `&&`                                                     |    |    |
|         12 | logical OR \`                                                        |    | \` |

Binary operators are intended to associate left-to-right.

***

## 16.2 Unary Operators

### 16.2.1 Logical NOT

Syntax:

```franko
!x
```

Rules:

* operand must be an integer;
* zero is false;
* nonzero is true;
* result type is `uint8_t`;
* result value is `0` or `1`.

Example:

```franko
uint8_t b;
b = !0; // b = 1
```

### 16.2.2 Unary Minus

Syntax:

```franko
-x
```

Rules:

* operand must be an integer;
* result type is the operand type;
* constants are folded as arbitrary-precision values before contextual checking.

Example:

```franko
int32_t x;
x = -1;
```

### 16.2.3 Unsupported Unary Operators

Unsupported unary operators include:

```franko
~x // not currently implemented
+x // not currently implemented as a semantic unary operator
```

Note that `+123` may be accepted as a literal form, but unary `+x` is not a supported semantic unary operator.

***

## 16.3 Arithmetic Operators

Arithmetic operators:

```franko
+
-
*
/
```

Rules:

* operands must be integers;
* arrays are invalid;
* addresses are invalid;
* both nonconstant operands must have exactly the same integer type;
* constants must fit the other side's concrete type;
* if exactly one operand is constant and the other operand is nonconstant, the result type is the nonconstant operand's type;
* if both operands are nonconstant, they must have exactly the same primitive integer type, and the result type is that shared type;
* if both operands are constant, the expression is folded when possible and remains a fluid constant until used in a contextual position.

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

***

## 16.4 Bitwise Operators

Bitwise operators:

```franko
&
|
^
```

Rules:

* operands must be integers;
* arrays are invalid;
* addresses are invalid;
* both nonconstant operands must have exactly the same integer type;
* constants must fit the other side's concrete type;
* if exactly one operand is constant and the other operand is nonconstant, the result type is the nonconstant operand's type;
* if both operands are nonconstant, they must have exactly the same primitive integer type, and the result type is that shared type;
* if both operands are constant, the expression is folded when possible and remains a fluid constant until used in a contextual position.

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

***

## 16.5 Shift Operators

Shift operators:

```franko
<<
>>
```

Rules:

* left operand must be an integer;
* right operand must be an integer;
* if the right operand is a nonconstant expression, it must have an unsigned integer type;
* if the right operand is a constant, it must be nonnegative and must fit the unsigned variant of the left operand's type;
* if the left operand is a constant and the right operand is nonconstant, the left constant must fit the right operand's concrete type;
* if exactly one operand is constant and the other operand is nonconstant, the result type is the nonconstant operand's type;
* if both operands are nonconstant, the result type is the left operand's type; the right operand is only the shift count;
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

For pure constant shifts, the expression remains a fluid constant until used in a contextual position:

```franko
uint8_t b;

b = 1 << 3; // valid: folded to 8, and 8 fits uint8_t
b = 1 << 8; // invalid when assigned to uint8_t: folded to 256
```

***

## 16.6 Comparison Operators

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

* operands may be any integer types;
* mixed integer types are allowed;
* if one side is a constant and the other side is nonconstant, the constant must fit the nonconstant side's concrete type;
* result type is `uint8_t`;
* result value is `0` or `1`.

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

Address comparison is also supported, but only between identical address types.

***

## 16.7 Logical Operators

Logical operators:

```franko
&&
||
!
```

Rules for `&&` and `||`:

* operands must be integers;
* mixed integer types are allowed;
* constants must fit the other side's concrete type when paired with a nonconstant;
* zero is false;
* nonzero is true;
* result type is `uint8_t`;
* result value is `0` or `1`;
* `&&` and `||` are intended to short-circuit.

Example:

```franko
int32_t x;
uint8_t b;

if (x && b) {
    print(1);
}
```

Addresses are not valid operands to logical operators.

***

## 16.8 No Address Arithmetic

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

***

# 17. Conditions

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

***

# 18. Statements

Franko supports the following statements inside function bodies and blocks:

* variable declarations;
* assignments;
* blocks;
* `if`;
* `while`;
* `return`;
* `print`;
* `del`;
* expression statements;
* function call statements;
* array intrinsic statement forms.

Executable statements are not allowed at global scope.

***

## 18.1 Blocks

A block is a sequence of statements enclosed in braces:

```franko
{
    int32_t x;
    x = 1;
    print(x);
}
```

Blocks create a new lexical scope.

***

## 18.2 If Statements

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

***

## 18.3 While Statements

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

***

## 18.4 Return Statements

Return statements are valid inside function bodies.

Bare return:

```franko
return;
```

Return with expression:

```franko
return expr;
```

A bare `return` is valid only in a `void` function.

A `return expr` statement is required for non-`void` functions.

Return expression compatibility is checked against the declared return type.

***

## 18.5 Print Statements

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

***

## 18.6 Expression Statements

A legal expression may appear as a statement.

Function calls are commonly used as expression statements when the return value is ignored or when the function returns `void`.

Example:

```franko
func logDone() -> void {
    print(1);
}

func main() -> int32_t {
    logDone();
    return 0;
}
```

Array intrinsic calls are special expression-statement forms:

```franko
arr(10);
arr.memset(0);
arr.memcpy(other);
arr.uninit();
```

Most non-call expressions have no side effects, so although they may be parsed as expression statements, they are usually not useful:

```franko
x + 1;
```

A `void`-returning function call is valid as an expression statement, but invalid in value-producing contexts.

***

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

Arrays are not arithmetic values and cannot be directly assigned.

***

## 19.1 Static Arrays

A static array has a compile-time size:

```franko
array<int32_t, 10> xs;
```

Static array size rules:

* size must be a valid integer literal;
* size must be greater than zero;
* size must fit `uint32_t`.

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

***

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

* target must be a storage-backed lvalue;
* target must be a dynamic array;
* exactly one argument is required;
* size must be compatible with `uint32_t`;
* constant sizes must be greater than zero;
* constant sizes must fit `uint32_t`;
* nonconstant sizes must have exactly type `uint32_t`.

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

***

## 19.3 Array Access

Array indexing syntax:

```franko
arr[index]
```

The target must be an array.

Index rules:

* constant index:
  * must be nonnegative;
  * must fit `uint32_t`;
* nonconstant index:
  * must have exactly type `uint32_t`.

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

***

## 19.4 Array Elements as Lvalues

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

***

## 19.5 Array Direct Assignment Is Invalid

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

***

## 19.6 Array Lifetime Limitations

The checker validates intrinsic legality, but it does not fully prove:

* dynamic array initialized before indexing;
* dynamic array not used after `uninit`;
* dynamic array not initialized twice;
* all array accesses are within bounds.

***

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

Valid:

```franko
arr.memset(0);
```

Invalid:

```franko
x = arr.memset(0); // invalid: intrinsic is not an expression value
```

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

***

## 20.1 `target(size)`

Dynamic array initialization.

Example:

```franko
array<int32_t> arr;

arr(100);
```

Rules:

* available only on dynamic arrays;
* target must be a storage-backed lvalue;
* target must have dynamic array type;
* exactly one argument is required;
* size must be:
  * a positive constant fitting `uint32_t`, or
  * a nonconstant expression of exactly type `uint32_t`.

Valid through an address:

```franko
array<int32_t> arr;
addr<array<int32_t>> p;

p = getaddr(arr);

deref(p)(100);
```

Valid for nested dynamic array elements when the target element is storage-backed:

```franko
array<array<int32_t>> arrs;

arrs(3);
arrs10;
```

Invalid because static arrays are not dynamically initialized:

```franko
array<int32_t, 10> arr;

arr(10); // invalid: static array
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

arr(-1); // invalid
arr(0);  // invalid
```

***

## 20.2 `target.uninit()`

Dynamic array uninitialization.

Example:

```franko
array<int32_t> arr;

arr(100);
arr.uninit();
```

Rules:

* receiver must be a storage-backed lvalue;
* receiver must be a dynamic array;
* takes exactly zero arguments.

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

## 20.3 `target.memset(value)`

Fills an array byte-wise.

Example:

```franko
array<uint8_t> arr;

arr(100);
arr.memset(0);
```

Rules:

* receiver must be a storage-backed lvalue;
* receiver may be a dynamic or static array;
* receiver element type must be memsetable;
* takes exactly one argument;
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

bytes.memset(x); // invalid
```

***

## 20.4 Memsetable Element Types

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

Valid:

```franko
array<array<uint8_t, 4>, 8> bytes;

bytes.memset(0);
```

Invalid:

```franko
array<array<int32_t>, 8> nestedDynamicArrays;

nestedDynamicArrays.memset(0); // invalid
```

***

## 20.5 `target.memcpy(source)`

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

* target must be a storage-backed lvalue array;
* source must be a storage-backed lvalue array;
* both target and source may be dynamic or static arrays;
* target and source element types must match exactly;
* static/dynamic shape does not need to match;
* static lengths do not need to match;
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

a.memcpy(b); // invalid
```

Invalid because the source is not storage-backed:

```franko
array<int32_t> a;
addr<array<int32_t>> p;

a.memcpy(getaddr(a)); // invalid
```

Invalid argument counts:

```franko
array<int32_t> a;
array<int32_t> b;
array<int32_t> c;

a.memcpy();     // invalid
a.memcpy(b, c); // invalid
```

***

## 20.6 Memcpyable Element Types

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

Valid:

```franko
array<array<int32_t, 4>, 8> matrixA;
array<array<int32_t, 4>, 8> matrixB;

matrixA.memcpy(matrixB);
```

Invalid:

```franko
array<array<int32_t>, 8> a;
array<array<int32_t>, 8> b;

a.memcpy(b); // invalid
```

***

# 21. Addresses

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

* typed;
* assignable;
* copyable;
* dereferenceable;
* comparable.

Addresses are not arithmetic values.

`addr<void>` is invalid because `void` is not an ordinary value type.

***

## 21.1 Creating Addresses with `getaddr`

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

***

## 21.2 Dereferencing with `deref`

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

***

## 21.3 Nested Addresses

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

***

## 21.4 Address Assignment

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

***

## 21.5 Address Comparison

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

***

## 21.6 No Address Arithmetic

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

***

## 21.7 Addresses of Arrays

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

***

## 21.8 Address Lifetime Limitations

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

***

# 22. `del` and Delete Checking

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

***

## 22.1 Delete Checking Limitations

Current limitations:

* delete checking is symbol-based;
* aliasing through addresses is not fully tracked;
* dangling addresses are not fully detected;
* delete state checking is path-insensitive and traversal-based;
* deleting in one branch may affect later checking conservatively.

Example of limitation:

```franko
if (cond) {
    del x;
}

print(x); // may be rejected because x was marked deleted during checking
```

The compiler does not perform full ownership, lifetime, alias, or dangling-address analysis.

***

# 23. Type Equality

Franko uses exact semantic type equality in many places.

***

## 23.1 Primitive Equality

Primitive types are equal only if they have the same kind.

```franko
uint8_t  != uint16_t
int32_t  != uint32_t
int32_t  == int32_t
```

The alias `int` canonicalizes to `int32_t`.

***

## 23.2 Dynamic Array Equality

Dynamic arrays are equal if their element types are equal.

```franko
array<int32_t> == array<int32_t>
array<int32_t> != array<uint8_t>
```

***

## 23.3 Static Array Equality

Static arrays are equal if:

* element types are equal;
* sizes are numerically equal.

For example, these sizes are considered equal if parsed successfully:

```franko
array<int32_t, 16>
array<int32_t, 0x10>
array<int32_t, 0b10000>
```

***

## 23.4 Address Equality

Address types are equal if their referenced types are equal.

```franko
addr<int32_t> == addr<int32_t>
addr<int32_t> != addr<uint8_t>
```

***

## 23.5 Function Signature Equality

Function signatures are equal if they have:

* the same function identifier;
* the same ordered list of parameter types.

Return type is not part of function signature equality.

Parameter names are not part of function signature equality.

Therefore these are duplicate signatures:

```franko
func f(int32_t x) -> int32_t {
    return x;
}

func f(int32_t y) -> uint32_t {
    return 0;
}
```

Both have signature:

```text
f(int32_t)
```

***

# 24. Declaration Type Validity

A declared variable type is valid if it is one of:

* primitive integer type;
* dynamic array of a valid non-`void` type;
* static array of a valid non-`void` type with valid size;
* address of a valid non-`void` type.

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

Invalid static sizes:

```franko
array<int32_t, 0> a;
array<int32_t, -1> b;
array<int32_t, 999999999999999999999999999999999> c;
```

A function return type has separate rules. It may be:

* `void`;
* a primitive integer type;
* an address type.

Array return types are invalid.

***

# 25. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

***

## 25.1 Function Restrictions

User-defined functions and general function calls are implemented.

Current function restrictions:

* function declarations are valid only in global scope;
* nested functions are not supported;
* first-class function values are not supported;
* function pointers are not supported;
* anonymous functions/lambdas are not supported;
* methods are not supported;
* parameter names are not used in overload resolution;
* return type is not used in overload resolution;
* arrays cannot be returned by value;
* `void` calls cannot be used in value-producing contexts;
* full path-sensitive return analysis is not currently performed.

***

## 25.2 No General Methods

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

***

## 25.3 No Structs or Fields

There are no user-defined aggregate types beyond arrays.

General member access is not currently implemented.

***

## 25.4 No Floating-Point Types

Only integer primitives are supported.

***

## 25.5 No Separate Boolean Type

Boolean results use `uint8_t`.

***

## 25.6 No Implicit Numeric Conversions for Nonconstants

This is invalid:

```franko
uint8_t a;
uint32_t b;

a = b;
```

Constants are special and may fit contextually.

***

## 25.7 Arrays Are Not Assignable

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

***

## 25.8 No Full Array Lifetime Checking

The checker validates intrinsic legality, but it does not fully prove:

* dynamic array initialized before indexing;
* dynamic array not used after `uninit`;
* dynamic array not initialized twice;
* all array accesses are within bounds.

***

## 25.9 Delete Checking Is Limited

Delete checking is currently:

* symbol-based;
* path-insensitive;
* not alias-aware.

It does not fully track dangling addresses or ownership.

***

## 25.10 Address Lifetime Is Not Fully Tracked

This may not be fully diagnosed:

```franko
addr<int32_t> p;

p = getaddr(x);
del x;

// The compiler may not fully know that p is dangling.
deref(p);
```

***

## 25.11 Print Checking Is Loose

The checker validates print arguments as expressions but does not enforce a strict printable-type set.

***

## 25.12 Unsupported Operators

The following are not currently implemented:

```franko
%   // modulus is not implemented
~x  // bitwise NOT is not implemented
+x  // unary plus is not implemented as a semantic unary operator
```

***

## 25.13 Global Scope Is Declaration-Only

Only the following nodes are valid at global scope:

* variable declaration nodes;
* function declaration nodes.

The following are invalid at global scope:

```franko
x = 1;
print(x);
f();
if (x) {}
while (x) {}
return 0;
del x;
{
    int32_t x;
}
arr(10);
arr.memset(0);
```

***

# 26. Runtime and Backend Notes

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

***

# 27. Examples

Most executable examples should be placed inside a function body, typically `main`, because global scope allows only declarations.

***

## 27.1 Basic Integers

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

***

## 27.2 Using `int` Alias

```franko
func main() -> int {
    int x;
    x = 10;

    print(x);

    return 0;
}
```

Equivalent to:

```franko
func main() -> int32_t {
    int32_t x;
    x = 10;

    print(x);

    return 0;
}
```

***

## 27.3 Constants Fitting Smaller Types

```franko
func main() -> int32_t {
    uint8_t b;

    b = 255; // valid
    // b = 256; // invalid

    return 0;
}
```

***

## 27.4 Dynamic Array

```franko
func main() -> int32_t {
    array<int32_t> xs;
    uint32_t i;

    xs(10);

    i = 0;
    xs[i] = 42;

    print(xs[i]);

    xs.uninit();

    return 0;
}
```

***

## 27.5 Static Array

```franko
func main() -> int32_t {
    array<uint8_t, 128> buffer;

    buffer.memset(0);
    buffer[0] = 255;

    return 0;
}
```

***

## 27.6 Array Copy

```franko
func main() -> int32_t {
    array<int32_t> a;
    array<int32_t, 10> b;

    a(10);

    b.memset(0);
    a.memcpy(b);

    return 0;
}
```

***

## 27.7 Heap Variable

```franko
func main() -> int32_t {
    alloc int x;

    x = 10;
    print(x);

    del x;

    return 0;
}
```

***

## 27.8 Allocated Dynamic Array Initialization Sugar

```franko
func main() -> int32_t {
    alloc array<int> arr(20);

    arr.memset(0);

    del arr;

    return 0;
}
```

Equivalent to:

```franko
func main() -> int32_t {
    alloc array<int> arr;
    arr(20);

    arr.memset(0);

    del arr;

    return 0;
}
```

***

## 27.9 Addresses

```franko
func main() -> int32_t {
    int32_t x;
    addr<int32_t> p;

    x = 1;
    p = getaddr(x);

    deref(p) = deref(p) + 1;

    print(x);

    return 0;
}
```

***

## 27.10 Address of Array Element

```franko
func main() -> int32_t {
    array<int32_t> arr;
    addr<int32_t> p;

    arr(60);
    arr[30] = 123;

    p = getaddr(arr[30]);

    print(deref(p));

    return 0;
}
```

***

## 27.11 Nested Addresses

```franko
func main() -> int32_t {
    int32_t x;
    addr<int32_t> p;
    addr<addr<int32_t>> pp;

    x = 1;

    p = getaddr(x);
    pp = getaddr(p);

    deref(deref(pp)) = 5;

    print(x);

    return 0;
}
```

***

## 27.12 Address Comparison

```franko
func main() -> int32_t {
    int32_t x;
    int32_t y;

    addr<int32_t> a;
    addr<int32_t> b;

    a = getaddr(x);
    b = getaddr(y);

    if (a != b) {
        print(1);
    }

    return 0;
}
```

***

## 27.13 Dynamic Array Through Address

```franko
func main() -> int32_t {
    array<int32_t> arr;
    addr<array<int32_t>> p;

    arr(10);
    p = getaddr(arr);

    deref(p).memset(0);
    deref(p).uninit();

    return 0;
}
```

This is valid because `memset` and `uninit` accept storage-backed array lvalues.

***

## 27.14 Valid Empty-Parameter `main`

```franko
func main() -> int {
    return 0;
}
```

Valid if `int` is the canonical alias for `int32_t`.

***

## 27.15 Valid `void` Function

```franko
func voidfunc() -> void {
}
```

A `void` function does not need to return.

***

## 27.16 Valid `void` Function with Bare Return

```franko
func logDone() -> void {
    return;
}
```

***

## 27.17 Invalid `void` Function Returning a Value

```franko
func badVoid() -> void {
    return 1;
}
```

Invalid because `void` functions must not return an expression.

***

## 27.18 Valid Non-`void` Function

```franko
func getValue() -> int32_t {
    return 10;
}
```

***

## 27.19 Invalid Non-`void` Function with No Return

```franko
func getValue() -> int32_t {
}
```

Invalid because a non-`void` function must return an expression.

***

## 27.20 Invalid Non-`void` Function with Bare Return

```franko
func getValue() -> int32_t {
    return;
}
```

Invalid because a non-`void` function must return an expression.

***

## 27.21 Valid Overloads by Parameter Type

```franko
func f(int32_t x) -> int32_t {
    return x;
}

func f(uint32_t x) -> uint32_t {
    return x;
}
```

Valid because the ordered parameter type lists differ:

```text
f(int32_t)
f(uint32_t)
```

***

## 27.22 Valid Overloads by Parameter Order

```franko
func f(int32_t x, uint32_t y) -> int32_t {
    return x;
}

func f(uint32_t y, int32_t x) -> int32_t {
    return x;
}
```

Valid because parameter order is part of the function signature:

```text
f(int32_t, uint32_t)
f(uint32_t, int32_t)
```

***

## 27.23 Invalid Duplicate with Different Parameter Names

```franko
func f(int32_t x) -> int32_t {
    return x;
}

func f(int32_t y) -> int32_t {
    return y;
}
```

Invalid because parameter names are not part of the function signature.

Both declarations have:

```text
f(int32_t)
```

***

## 27.24 Invalid Duplicate with Different Return Types

```franko
func f(int32_t x) -> int32_t {
    return x;
}

func f(int32_t x) -> uint32_t {
    return 0;
}
```

Invalid because return type is not part of the function signature.

Both declarations have:

```text
f(int32_t)
```

***

## 27.25 Invalid Duplicate Parameter Names Inside One Function

```franko
func bad(int32_t x, uint32_t x) -> int32_t {
    return x;
}
```

Invalid because parameters are variables inside the function body, and the same scope cannot contain two variables named `x`.

***

## 27.26 Valid Forward Reference

```franko
func a() -> int32_t {
    return b();
}

func b() -> int32_t {
    return 1;
}
```

Valid because function signatures are registered before function bodies are analyzed.

***

## 27.27 Valid Direct Recursion

```franko
func countdown(int32_t x) -> int32_t {
    return countdown(x - 1);
}
```

Syntactically and symbolically valid if the recursive call matches the registered signature.

Termination and full return-path legality are separate checker concerns.

***

## 27.28 Valid Mutual Recursion

```franko
func even(int32_t x) -> int32_t {
    return odd(x - 1);
}

func odd(int32_t x) -> int32_t {
    return even(x - 1);
}
```

Valid at the function-resolution level because both signatures are registered before body analysis.

***

## 27.29 Valid Constant Argument Overload Resolution

```franko
func f(uint8_t x) -> void {
    print(x);
}

func main() -> int32_t {
    f(255); // valid: 255 fits uint8_t
    return 0;
}
```

***

## 27.30 Invalid Constant Argument Overload Resolution

```franko
func f(uint8_t x) -> void {
    print(x);
}

func main() -> int32_t {
    f(256); // invalid: 256 does not fit uint8_t
    return 0;
}
```

***

## 27.31 Ambiguous Constant Argument Overload Resolution

```franko
func f(uint8_t x) -> void {
}

func f(uint32_t x) -> void {
}

func main() -> int32_t {
    f(1); // invalid: 1 fits both uint8_t and uint32_t
    return 0;
}
```

***

## 27.32 Valid Global Variable Used Before Source Declaration

```franko
func main() -> int32_t {
    g = 10;
    print(g);
    return 0;
}

uint32_t g;
```

Valid because global variable declarations are registered before function bodies are analyzed.

***

## 27.33 Invalid Global Assignment

```franko
uint32_t g;

g = 10; // invalid: assignment is not allowed at global scope
```

***

## 27.34 Valid Address Return

```franko
func getPointer(addr<int32_t> p) -> addr<int32_t> {
    return p;
}
```

Valid because `addr<int32_t>` is assignable.

***

## 27.35 Valid Address-to-Dynamic-Array Return

```franko
func getArray(addr<array<int32_t>> p) -> addr<array<int32_t>> {
    return p;
}
```

Valid because the return type is an address type, not an array type.

***

## 27.36 Valid Address-to-Static-Array Return

```franko
func getBuffer(
    addr<array<uint8_t, 128>> p
) -> addr<array<uint8_t, 128>> {
    return p;
}
```

Valid because `addr<array<uint8_t, 128>>` is assignable.

***

## 27.37 Invalid Dynamic Array Return

```franko
func makeArray() -> array<int32_t> {
    array<int32_t> arr;

    arr(10);

    return arr;
}
```

Invalid because `array<int32_t>` is not assignable and therefore cannot be a function return type.

Use an address-returning design instead.

***

## 27.38 Invalid Static Array Return

```franko
func makeBuffer() -> array<uint8_t, 128> {
    array<uint8_t, 128> buffer;

    buffer.memset(0);

    return buffer;
}
```

Invalid because `array<uint8_t, 128>` is not assignable and therefore cannot be a function return type.

***

## 27.39 Recommended Array-Through-Address Pattern

```franko
func fill(addr<array<int32_t>> p) -> void {
    deref(p).memset(0);
}

func main() -> int32_t {
    array<int32_t> arr;
    addr<array<int32_t>> p;

    arr(10);

    p = getaddr(arr);

    fill(p);

    return 0;
}
```

The function receives an address to the array and mutates the caller-owned array explicitly.

***

## 27.40 Recommended Copied-Array Pattern

If the user wants a separate array result, the program should explicitly create and copy array storage instead of returning an array by value.

```franko
func copyInto(
    addr<array<int32_t>> target,
    addr<array<int32_t>> source
) -> void {
    deref(target).memcpy(deref(source));
}

func main() -> int32_t {
    array<int32_t> a;
    array<int32_t> b;

    addr<array<int32_t>> pa;
    addr<array<int32_t>> pb;

    a(10);
    b(10);

    pa = getaddr(a);
    pb = getaddr(b);

    copyInto(pb, pa);

    return 0;
}
```

This keeps allocation, copying, and ownership explicit.
