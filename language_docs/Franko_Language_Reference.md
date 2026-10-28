# Franko Language Reference

This document describes the Franko language as implemented by the current compiler pipeline:

```text
Franko source
  -> lexer + parser
  -> parser AST
  -> Desugarer
  -> SemanticAnalyzer
  -> LegalityChecker (declarations, expressions, statements, functions, types)
  -> PointerNullStateChecker
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
13. [Pool Allocation](13-pool-allocation.md)
14. [Assignment](14-assignment.md)
15. [Lvalues and Storage-Backed Expressions](15-lvalues-and-storage-backed-expressions.md)
16. [Casts](16-casts.md)
17. [Operators](17-operators.md)
18. [Conditions](18-conditions.md)
19. [Statements](19-statements.md)
20. [Arrays](20-arrays.md)
21. [Array Intrinsics](21-array-intrinsics.md)
22. [Array Initializer Lists](22-array-initializer-lists.md)
23. [String Literals](23-string-literals.md)
24. [Pointers](24-pointers.md)
25. [Structs](25-structs.md)
26. [Methods](26-methods.md)
27. [Memory Safety Checking](27-memory-safety-checking.md)
28. [Type Equality](28-type-equality.md)
29. [Declaration Type Validity](29-declaration-type-validity.md)
30. [Unsupported or Restricted Features](30-unsupported-or-restricted-features.md)
31. [Runtime and Backend Notes](31-runtime-and-backend-notes.md)
32. [Foreign Function Interface](32-foreign-function-interface.md)
33. [Examples](33-examples.md)

---

# 1. Overview

Franko is a small statically typed language with:

- fixed-width signed and unsigned integer types;
- structs with named fields (`struct X { int32_t n }`);
- struct member access using `obj.field` (see [Structs](25-structs.md));
- dynamic arrays;
- static arrays;
- typed pointers using `ptr<T>`;
- explicit pointer operations:
  - `getptr(...)`;
  - `$(...)`;
- user-defined functions;
- function overloading;
- function calls inside function bodies;
- struct methods with explicit `self` receivers (`ptr<X> self`, `mview<X> self`, `view<X> self`, see [Methods](26-methods.md));
- `void` and non-`void` return types;
- global variable declarations;
- global function declarations;
- assignment;
- blocks;
- `if`;
- `while`;
- `for`;
- `break` and `continue` (with an optional `break N` level, see [Statements](19-statements.md));
- `return`;
- `print`;
- string literals as read-only `view<array<byte, N>>` values;
- array intrinsics:
  - `arr(size)` (shorthand for `arr.init(size)`);
  - `arr.init(size)`;
  - `arr.init_zero(size)`;
  - `arr.resize(size)`;
  - `arr.uninit()`;
  - `arr.memset(value)` and `arr.memset(value, start, count)`;
  - `arr.memcpy(source)` and `arr.memcpy(source, dstStart, srcStart, count)`;
  - `arr.memmove(dstStart, srcStart, count)`;
  - `arr.length()`;
- array initializer lists as array element assignment sugar;
- imports of other source files (see [Source Files](02-source-files.md));
- namespaces, including nested namespaces, with `::`-qualified references (see [Program Structure](03-program-structure.md)).

Franko currently does **not** implement:

- nested functions;
- first-class function values;
- anonymous functions or lambdas;
- function pointers;
- general member calls beyond struct methods and the built-in array intrinsics;
- generics;
- floating-point types;
- a distinct boolean type;
- dedicated string types or a string library (strings are byte-array views, see [String Literals](23-string-literals.md));
- implicit numeric conversions except for fluid integer constants (explicit width changes use `cast<T>(expr)` for widening and `unsafe_cast<T>(expr)` for checked conversions);
- cast syntax;
- a ternary conditional operator;
- `switch` and `do`/`do-while`;
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

## Imports

A source file may import other source files with an import directive:

```franko
import "abc"
```

Imports are top-level items and may appear anywhere at global scope, mixed freely with struct, global variable, and function declarations.

The import path **must be a string literal**. The bare form `import abc` is a syntax error. Hyphens and spaces are ordinary characters inside the quoted path, so `import "my-lib/foo bar"` names the file `foo bar.fr` in the `my-lib` directory.

String literal paths support the same escape sequences as string literals. The path is completed as follows:

- `.fr` is appended when the file name has no extension.
- A present but non-`.fr` extension is rejected.

### Search Directory

The compiler accepts an import search directory with `-p <dir>`. The default is `import` relative to the compiler's working directory; the bundled compile scripts pass the compiler's own `import` directory explicitly.

A **prefix-free** path (one that does not start with `./`, `../`, or `/`) is looked up in two places, in order:

1. the search directory: `<search-dir>/<path>.fr`
2. the directory of the file that contains the import statement

The first location that holds a regular file wins, so a module supplied through `-p` shadows a same-named file next to the importing source file. A missing search directory is not an error; resolution simply falls through to the importing file's directory.

Paths with an explicit `./`, `../`, or `/` prefix skip the search directory and resolve relative to the importing file (or as an absolute path).

Examples (search directory `import/`):

| Source | Search first | Fall back to |
|---|---|---|
| `import "foo"` | `import/foo.fr` | `<file-dir>/foo.fr` |
| `import "lib/foo"` | `import/lib/foo.fr` | `<file-dir>/lib/foo.fr` |
| `import "lib/foo.fr"` | `import/lib/foo.fr` | `<file-dir>/lib/foo.fr` |
| `import "./lib/foo"` | — | `<file-dir>/lib/foo.fr` |
| `import "../shared/foo"` | — | `<parent-of-file-dir>/shared/foo.fr` |
| `import "/abs/foo"` | — | `/abs/foo.fr` |
| `import "foo.txt"` | error: must reference a `.fr` file | |

A quoted path whose last segment is empty (for example `import "abc/"`) is rejected because it has no file name.

Import paths are always written in POSIX form with forward slashes, on every platform. On Windows, a `/`-prefixed path is translated when it reaches the filesystem: `import "/abs/foo"` resolves to `X:\abs\foo.fr`, where `X:` is the drive of the current working directory, and `/` separators in the translated path become `\`.

### How Imports Compile

An import is resolved at compile time:

- the referenced file is parsed like any other source file;
- all of its top-level items (structs, global variables, functions) are compiled into the same global program as the importing file's items;
- imports are processed in declaration order, and an import of an already-processed file is skipped silently, so repeated and cyclic imports are safe (each file is compiled exactly once);
- an imported file's own imports are resolved relative to that file's directory (after first consulting the search directory).

Because every symbol lives in one global namespace, duplicate declarations across files are reported like duplicate declarations within one file.

### Built-in Modules

Importing certain modules through their canonical specifier (currently
only `import "franko/os"`) additionally activates compiler FFI
behavior: declared functions are given C++ definitions from a bridge
file instead of their Franko bodies. See [Foreign Function
Interface](32-foreign-function-interface.md) for the activation rule,
the bridge layout, and the verification checks.

---

# 3. Program Structure

A Franko program is a sequence of **top-level items**.

Current valid top-level items are:

- struct declarations;
- global variable declarations;
- function declarations;
- import directives;
- namespace declarations.

Struct declarations are described in full in [Structs](25-structs.md). Import directives are described in full in [Source Files](02-source-files.md).

## Namespaces

A namespace groups struct, global variable, and function declarations under a qualified name:

```franko
namespace util {
    struct Point {
        int32_t x = 1
        int32_t y = 2
    }

    int32_t offset = 100

    func doubleIt(int32_t v) -> int32_t {
        return v + v + offset
    }
}
```

A namespace body may contain only struct declarations, global variable declarations, function declarations, and nested namespace declarations. Imports and executable statements are not allowed inside a namespace body.

Nested namespaces may be written in two equivalent forms:

```franko
namespace a::b {
    int32_t v = 1
}
```

```franko
namespace a {
    namespace b {
        int32_t v = 1
    }
}
```

Both forms declare the same namespace, whose full path is `a::b`. A path header (containing `::`) is only valid at the top level: a namespace declared inside a namespace body must use a single name, and its full path is the enclosing path plus that name.

The same namespace may be declared more than once, in the same file or across imported files, in either form; the declarations merge into one namespace. Duplicate symbols inside the merged namespace are rejected exactly like global duplicates.

### Qualified References

Namespace members are referenced with `::`:

```franko
util::Point p
print(util::offset)
print(util::doubleIt(5))
util::offset = 5
print(a::b::helper(x))
```

A qualified name always resolves strictly inside the named namespace path, starting from the top level; there is no fallback to the global namespace, no search of enclosing namespaces, and no relative resolution through the current namespace. `a::w` does not find a symbol declared only in `a::b`, and `a::b::x` requires both `a` and `a::b` to be declared namespaces.

### Unqualified References

Inside a namespace, an unqualified name resolves in that namespace first, then falls back to the global namespace. Enclosing namespaces are not searched: code inside `a::b` does not see unqualified names declared in `a` unless they are also visible globally. Outside any namespace, only the global namespace is visible.

### Entry Point

The entry-point function `main` must be declared in the global namespace. A `main` inside a namespace is a compile error.

`main` must be declared in one of two shapes, and it cannot be overloaded:

```franko
func main() -> int32_t {
    return 0;
}

func main(view<array<array<byte>>> args) -> int32_t {
    return 0;
}
```

Any other shape (parameters of another type, a different return type) is a compile error.

In the args form, the parameter name is free (`args`, `notargs`, ...). The template wrapper converts the raw `argc`/`argv` into nested dynamic byte arrays: `args[0]` is the program name and each argument is its raw bytes with no NUL terminator (see [Runtime and Backend Notes](31-runtime-and-backend-notes.md)).

Executable statements are **not** currently allowed in global scope.

Invalid global-scope items include:

- assignments;
- `print`;
- function calls;
- `if`;
- `while`;
- `for`;
- `return`;
- blocks;
- array intrinsic calls;
- array initializer assignments.
- expression statements (bare expressions such as `1 + 2`);

Note that some invalid global-scope forms only become visible after desugaring: declaration initializer sugar is invalid because it desugars into executable statements (an assignment, an expression-statement call, or indexed element assignments).

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

## 4.7 Namespace Lookup

Namespaces add two lookup rules on top of lexical scope; see [Program Structure](03-program-structure.md) for the full reference.

Unqualified names used inside a namespace resolve in that namespace first, then fall back to the global namespace. Enclosing namespaces are never searched.

Qualified names (`a::b::x`) resolve strictly inside the named namespace path from the top level, with no fallback. The first segment must name a top-level namespace.

---

# 5. Desugaring

Some Franko source forms are accepted as convenient syntax and rewritten before semantic checking.

Desugaring does not change program meaning. It rewrites compact user-facing syntax into simpler core forms used by the semantic analyzer.

After desugaring, normal declaration, assignment, array initialization, and type checking rules apply.

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
```

is desugared to:

```franko
array<int> arr;
arr(20);
```

The call form itself (`arr(20)`) is later recognized as array intrinsic `init` sugar. See [Array Intrinsics](21-array-intrinsics.md).

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

Every string literal is hoisted to a literal-backed global `array<byte, N>`
holding its UTF-8 bytes (no null terminator is added) and replaced with a
`getview(...)` expression over that global, of type `view<array<byte, N>>`:

```franko
print($("ABC")[0]);
```

first becomes (one global per distinct literal text):

```franko
array<byte, 3> _STR_<hash>;
print($(getview(_STR_<hash>))[0]);
```

Identical literals share one global. Because a string literal is a view
value, it cannot initialize or assign an array: copy the bytes with
`memcpy` instead. See [String Literals](23-string-literals.md).

## 5.5 Braceless Control-Flow Bodies

`if`, `while`, and `for` bodies may be a single statement without braces:

```franko
if (x) print(x);
while (i < 10) i = i + 1;
for (; i < 10; i = i + 1) print(i);
```

During desugaring, a braceless body is wrapped in an implicit block:

```franko
if (x) { print(x); }
while (i < 10) { i = i + 1; }
for (; i < 10; i = i + 1) { print(i); }
```

This makes braceless and braced bodies scope identically: a declaration inside a braceless body is scoped to that implicit block and is not visible after the `if`/`while`/`for`. See [Statements](19-statements.md).

## 5.6 For Loop Desugaring

A `for` loop is lowered to declaration/assignment statements and a `while` loop:

```franko
for (int32_t i = 0; i < n; i = i + 1) {
    print(i)
}
```

becomes:

```franko
{
    int32_t i;
    i = 0;
    while (i < n) {
        print(i);
        i = i + 1;
    }
}
```

The whole loop becomes a block, so a loop variable declared in `init` is scoped to the loop and is not visible after it.

The `init` and `update` parts are optional simple statements. The condition is required. When present, the `update` runs at the end of every iteration, including the last. It is carried as the desugared `while` loop's epilogue rather than being appended to the body block, so `continue` runs it and `break` skips it, matching C's `for` semantics. See [Statements](19-statements.md).

The `init` and `update` statements go through the ordinary statement desugaring rules, so declaration initializer sugar in the header expands normally. See [Statements](19-statements.md).

## 5.7 Inferred Static Array Sizes (`?`)

A static array size may be written as `?` when the array is declared with an array-literal initializer:

```franko
array<int32_t, ?> a = [0, 1, 2, 3];
```

During desugaring, each `?` is replaced by the element count of the corresponding initializer level, so the declaration above becomes:

```franko
array<int32_t, 4> a;
a[0] = 0;
a[1] = 1;
a[2] = 2;
a[3] = 3;
```

Nested literals infer level by level:

```franko
array<array<int32_t, ?>, ?> m = [[1, 2], [3, 4]];
```

becomes `array<array<int32_t, 2>, 2> m` with indexed assignments. `?` may appear at any level of the nest, including middle levels.

Inference rules:

- `?` is legal only in a local declaration-assignment statement (`T name = literal;`) whose initializer is an array literal (string literals are `view<array<byte, N>>` values and do not participate);
- when `?` is used in any dimension, the whole array nest must be static (no dynamic `array<T>` level anywhere);
- when `?` is used in any dimension, the initializer must fit exactly at every level: a shorter list is an error (no partial fill), and sibling lists at each level must have equal length (perfect rectangle);
- an empty initializer list cannot infer a size;
- without `?`, the existing lenient rules are unchanged: shorter lists partially fill, and jagged lists are allowed.

Misplaced `?` (parameters, return types, struct fields, globals, pointers, call-style `arr(4)` initialization, non-literal initializers) is rejected during desugaring. See [Arrays](20-arrays.md) and [Array Initializer Lists](22-array-initializer-lists.md).

## 5.8 Constant Expression Evaluation

Every other `constExpr` slot is evaluated during desugaring, alongside `?` inference:

```franko
array<int32_t, 0x10> xs;   // size becomes 16
int8_t g = 1 + 2;          // initializer becomes 3 (globals)
struct S {                 // field default becomes 8
    int32_t n = 1 << 3
}
```

Static array sizes, global initializers, and struct field defaults all reach semantic analysis as canonical decimal literals. Semantic analysis then only validates the values (positive sizes, `uint32_t` range, initializer fit) without folding anything again.

Evaluation failures are desugaring diagnostics:

```franko
array<int32_t, 10 / 0> b;   // invalid: Division by zero in constant expression
```

Constant expressions inside ordinary code are not `constExpr` slots and are unaffected: they are still folded later, during semantic analysis. See [Constant Folding](10-constant-folding.md).

## 5.9 Increment and Decrement Desugaring

Postfix increment and decrement statements are sugar for compound assignment:

```franko
x++;
x--;
```

are lowered to:

```franko
x += 1;
x -= 1;
```

before semantic analysis, so every rule that applies to `+=` / `-=` (integer storage-backed lvalue target, operand rules, single evaluation of the target) applies identically to `x++` / `x--`. The literal `1` remains a fluid integer constant. See [Assignment](14-assignment.md).

---

# 6. Types

Franko has the following semantic type categories:

```text
primitive integers
structs
dynamic arrays
static arrays
typed pointers (ptr / mview / view)
function return-only void
```

There is currently no separate semantic boolean type. Boolean-like results are represented as `uint8_t`.

Structs are nominal types: two distinct struct declarations are distinct types even when their field layouts are identical. See [Type Equality](28-type-equality.md), [Declarations](11-declarations.md), and the complete struct reference in [Structs](25-structs.md).

`void` is not an ordinary value type. It is valid only where the language explicitly permits it, currently as a function return type.

Pointers come in three permission levels — `ptr<T>` (owner), `mview<T>` (mutable view), `view<T>` (read-only view) — one capability model for all of them. See [Pointers](24-pointers.md).

A static array size may be the inferred-size marker `?` (for example `array<int32_t, ?>`) in source text, but only in a local declaration with an array-literal initializer; the desugarer evaluates every size (inferred or explicit) into a canonical constant before semantic analysis, so semantic types only ever carry evaluated sizes.

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

The backend emits very large decimal literals with a trailing `ULL` suffix so they remain well-formed C++14 (see [Runtime and Backend Notes](31-runtime-and-backend-notes.md)).

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

Franko splits constant handling into evaluation and validation.

**Evaluation** computes the value of a syntactically pure constant expression. This happens before semantic analysis, in the desugarer, for every `constExpr` slot: static array sizes, global initializers, and struct field defaults.

Examples:

```franko
1 + 2       // evaluated to 3
4 * 5       // evaluated to 20
7 % 3       // evaluated to 1
10 == 10    // evaluated to 1
!0          // evaluated to 1
1 << 3      // evaluated to 8
```

`array<int32_t, 0x10>` reaches semantic analysis as `array<int32_t, 16>`; `int8_t g = 1 + 2` reaches it as `int8_t g = 3`.

**Validation** checks the computed value in context. This stays in semantic analysis and later checkers, because it needs declared types: positivity and `uint32_t` range for array sizes, fit for global and field initializers, primitive-only field defaults.

Evaluation failures are reported before semantic analysis:

```franko
array<int, 10 / 0> bytes;   // invalid: Division by zero in constant expression
array<int, 10 % 0> bytes;   // invalid: Division by zero in constant expression
```

Modulus folding uses truncated division, so the remainder keeps the sign of the dividend, matching the generated C++ (see [Operators](17-operators.md)).

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

Array and pointer declarations:

```franko
array<int> xs;
array<int32_t> ys;
array<uint8_t, 128> buffer;
array<array<byte, 20>, 12> grid;

ptr<int> p;
ptr<int32_t> q;
ptr<array<int>> arrayPtr;
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

Variable declarations are allowed at global scope. Global variables are statically allocated.

Two different initializer forms exist for declarations, and they follow different rules at global scope.

**Constant initializers.** A global declaration may end with `= constExpr`:

```franko
int32_t g = 1 + 2
uint64_t mask = 1 << 8
```

The initializer must be a constant expression built from literals, so it is folded at compile time and may not reference variables. Only primitive globals accept this form: struct, array, and ptr globals reject `= constExpr` as a type mismatch.

**Static address initializers.** A pointer-typed global may be
initialized with `null`, with `getptr`/`getmview`/`getview` over another
global variable, or with a string literal (which is a view over a
hoisted global; see [String Literals](23-string-literals.md)):

```franko
array<byte, 5> greeting
view<array<byte, 5>> v = getview(greeting)
view<array<byte, 5>> s = "hello"
ptr<array<byte, 5>> p = null
```

The initializer must be a statically representable address of the right
type (a permission downgrade is allowed, an upgrade is not).

**Ordinary declaration initializer sugar.** The initializer forms described in this chapter and in [Assignment](14-assignment.md) — `= expr` with a nonconstant expression, array initializer lists `= [ ... ]`, and the dynamic-array call-init form `xs(n)` — desugar into executable statements (an assignment, an expression-statement call, or indexed element assignments). Executable statements are not allowed at global scope, so these forms are invalid on global declarations:

```franko
int32_t g = h                     // invalid: the initializer must be constant
array<int32_t, 3> xs = [1, 2, 3]  // invalid: desugars into indexed assignments
array<byte, 6> s = "hello"        // invalid: strings cannot initialize arrays
```

The constant-initializer and static-address forms above are the only
ways to initialize a global at its declaration.

## 11.5 Uninitialized Values

A **local** declaration without an initializer default-initializes the variable:

- primitive integer variables start with an indeterminate value; reading one before its first assignment is undefined behavior in the generated C++;
- static array elements start with indeterminate values;
- dynamic arrays start empty (length 0, no element storage) and must be initialized with `init` or `init_zero` before indexed use;
- **pointer variables are the exception: an `ptr<T>` declaration is guaranteed null-initialized** (see [Pointers](24-pointers.md)).

There is no implicit zero-initialization for locals other than pointers. Use `init_zero` for zeroed dynamic array storage.

**Global** declarations are statically allocated: in the generated C++ they live in static storage, so primitives and static array elements are zero-initialized before `main` runs, and dynamic arrays start empty. Global constant initializers (see Global Variable Declarations above) are applied on top of that.

## 11.6 Struct Fields

Struct fields are declared with the same `type name` syntax as variables, inside a top-level `struct` declaration:

```franko
struct X {
    int32_t num = 0
    array<int32_t, 4> xs
    ptr<Y> next
}
```

Field defaults must be constant expressions on primitive fields only, and duplicate field names are rejected.

The complete struct reference — field rules, by-value cycles, assignability, member access, and globals — is in [Structs](25-structs.md).

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
- an pointer type `ptr<T>`, where `T` is any valid declared type. Arrays and nested pointers are allowed as the referenced type, so `ptr<array<int32_t>>`, `ptr<array<int32_t, 8>>`, and `ptr<ptr<int32_t>>` are all passable; or
- a struct type whose fields are all assignable value types, recursively. Assignable = primitive | pointer | struct-with-all-assignable-fields. Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing one of them is pointer-only: pass `ptr<X>` and `getptr(s)` instead. See [Structs](25-structs.md).

Bare array types are not passable. To pass an array to a function, declare an `ptr<array<T>>` (or `ptr<array<T, N>>`) parameter and pass `getptr(array)` at the call site.

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

Emitted C++ names carry the return type even though overload resolution does not: each function emits as `FUNC_f_HASH_<8hex>`, where the suffix is the FNV-1a 32-bit hash of the canonical signature key `fn(P1,P2,...)->R` (declared parameter types in order, then the return type, each spelled exactly as written: `int32_t`, `uint64_t`, `void`, `array<T>`, `array<T,N>`, `ptr<T>`, `mview<T>`, `view<T>`, qualified struct names). The backend therefore never relies on g++ overload resolution. The entry-point `main` emits its mangled name like every other function: the generated C++ entry point is a small wrapper in the program template that calls it (`ProgramTemplate.cpp` calls the no-args `main`, `ProgramTemplateWithRawCArgs.cpp` converts `argc`/`argv` and calls the args-form `main`).

## 12.6 Function Call Resolution

A function call is valid if and only if exactly one overload is applicable.

For nonconstant arguments, the argument type must exactly match the
parameter type, with the single exception of the pointer permission
downgrade: a `ptr`/`mview`/`view` argument may be passed where a weaker
permission over an identical referenced type is expected (see
[Pointers](24-pointers.md)). There are no other implicit conversions: pass
`cast<T>(x)` explicitly to move a value across integer widths (see
[Casts](16-casts.md)).

For folded integer constants, the constant may match a primitive integer parameter if its value fits that parameter type. If a constant argument fits the parameter types of multiple overloads, the call is ambiguous and is rejected.

Overload preference: among applicable overloads, ones matched without any
permission downgrade win over downgrading ones, and among the rest the
ones relying on the fewest downgrades win. If several overloads remain
tied, the call is ambiguous and is rejected.

## 12.7 Bare Function Names

Variables and functions live in separate namespaces.

A bare function name is not a value. An identifier used as a value resolves to a variable first; if the identifier only matches a function name, it is rejected:

```franko
func f() -> int32_t {
    return 1;
}

f;    // invalid: Function name 'f' cannot be used as a value
```

In a call position, `name(...)` resolves to a function even when a non-array variable with the same name is in scope. The one exception is a dynamic array variable: `xs(10)` on a dynamic array is array `init` shorthand, not a function call. See [Array Intrinsics](21-array-intrinsics.md).

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
- an pointer type, `ptr<T>`; or
- a struct type whose fields are all assignable value types, recursively (see [Structs](25-structs.md)).

Arrays cannot currently be returned directly.

## 12.11 Forward References and Recursion

Function signatures are registered before function bodies are analyzed, allowing forward references, direct recursion, and mutual recursion.

Struct methods follow the same signature, overload, and return rules with an explicit `self` first parameter of type `ptr<X>`, `mview<X>`, or `view<X>`; see [Methods](26-methods.md).

---

# 13. Pool Allocation

Franko has no per-object heap allocator. The two storage classes a variable
can have are automatic storage (a local, whose lifetime is its enclosing
block) and static storage (a global, whose lifetime is the program).

Dynamic arrays are the one kind of storage a program manages explicitly:
`init`, `init_zero`, `resize`, and `uninit` pair up over a single array.

```franko
array<int32_t> xs;
xs.init(20);
// ... use xs ...
xs.uninit();
```

When a program needs many same-sized objects that come and go
independently, it allocates one dynamic array up front and hands out
integer indices into it. The `franko/pool` module supplies that scheme
directly as four index-pool allocators, each tracking which slots are live,
which are free, and (for the generational variants) how many times a slot
has been recycled.

```franko
import "franko/pool"

std::SparseSetAllocator pool;
pool.init(1024);

uint32_t idx = pool.new_index();
// ... use idx as a row index into the program's own payload array ...
pool.free_index(idx);

pool.uninit();
```

Slot indices are cheap to copy, compare, and store, and a generational
pool distinguishes a live index from one that was recycled, so a stale
handle is detectable. The pool tracks indices only; the payload that an
index selects belongs to the program.

The pool allocator API is documented in
`library_docs/pool.md`.

---

# 14. Assignment

Assignment syntax:

```franko
target = value;
target op= value;   // compound: += -= *= /= %=
```

The left-hand side must be a storage-backed lvalue.

Valid assignment targets include:

- variables;
- array elements;
- dereferenced pointers;
- struct field accesses (`obj.field`).

## 14.1 Primitive Assignment

A primitive integer variable may be assigned:

1. a nonconstant expression of exactly the same primitive type; or
2. a constant expression that fits the target type.

Moving between integer widths is never implicit: use the explicit
`cast<T>(expr)` intrinsic (see [Casts](16-casts.md)).

## 14.2 Pointer Assignment

Pointer assignment requires exact pointer type equality.

Raw integers cannot be converted to pointers.

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

A struct may be assigned as a whole value if and only if all of its fields are assignable, recursively: assignable = primitive | pointer | struct-with-all-assignable-fields.

Valid:

```franko
struct X {
    int32_t n
    ptr<Y> p
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
a = b;    // invalid: struct with non-assignable fields is pointer-only
```

Structs with non-assignable fields can still be shared through `ptr<X>`: pass or store `getptr(s)`, and access fields through `$(p).field`.

## 14.5 Compound Assignment

Five compound assignment operators exist:

```franko
+= -= *= /= %=
```

Valid:

```franko
x += 1;
arr[i] -= 2;
obj.n *= 3;
$(p) /= 4;
x %= 7;
```

The target follows the same rules as plain assignment: a pointable, non-void storage-backed lvalue.

The value must satisfy the rules of the corresponding binary operator (`+ - * / %`, see [Operators](17-operators.md)) against the target:

- both operands are integers (arrays, pointers, and void are invalid);
- with two nonconstant operands, both sides must have exactly the same integer type;
- a constant right-hand side must fit the target's type;
- a constant zero divisor is rejected for `/=` and `%=`, exactly like `x / 0` and `x % 0`.

`target op= value` means exactly `target = target op value`, except the target expression is evaluated only once:

```franko
arr[f()] += 1;   // f() runs once
```

The backend emits the C++ compound assignment directly, so array indices, field chains, and dereferences in the target are never evaluated twice.

Invalid:

```franko
array<int32_t, 3> xs = [1, 2, 3];
xs += 1;         // arrays are not arithmetic values

int32_t v = 1;
ptr<int32_t> p = getptr(v);
p += 1;          // pointers are not arithmetic values

int32_t x = 1;
int64_t y = 2;
x += y;          // operands must have the same integer type
```

The right-hand side may not be an array initializer list (`xs += [1, 2]` does not parse); initializer lists remain plain-`=` sugar only. See [Array Initializer Lists](22-array-initializer-lists.md).

## 14.6 Increment and Decrement

Postfix increment and decrement are statements:

```franko
x++;
x--;
```

They are exactly equivalent to:

```franko
x += 1;
x -= 1;
```

so every compound-assignment rule above applies unchanged: the target must be an integer, non-void storage-backed lvalue (`xs++` and `p++` are invalid for the same reason `xs += 1` and `p += 1` are invalid).

```franko
for (int32_t i = 0; i < n; i++) {
    print(i)
}
```

Only the postfix statement forms exist. Prefix forms (`++x`) and value uses (`print(x++)`, `x = y++`) are syntax errors: `x++` never produces a value. See [Statements](19-statements.md).

See [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md) for field-level assignment targets, and [Structs](25-structs.md) for the full assignability rules.

---

# 15. Lvalues and Storage-Backed Expressions

A storage-backed lvalue is an expression that refers to a real mutable storage location.

Current storage-backed lvalues are:

| Expression | Lvalue? |
|---|---|
| variable | yes |
| array element of storage-backed array | yes |
| `$(pointer)` | yes |
| struct field access whose base is an lvalue (`obj.field`) | yes |
| integer literal | no |
| arithmetic expression | no |
| comparison expression | no |
| function call result | no |
| `getptr(...)` result | no |

Struct field lvalue-ness follows the base expression: `s.field` is an lvalue when `s` is a variable, a `$(...)`, a nested member access, or an array element of a storage-backed array (static or dynamic). Field access on a function call result is not storage-backed.

See [Structs](25-structs.md) for the complete struct reference.

This distinction matters for:

- assignment targets;
- compound assignment targets and `x++` / `x--` targets;
- `getptr(...)` operands;
- array intrinsic receivers;
- `memcpy` sources;
- array initializer assignment targets.

---

# 16. Casts

Franko has no implicit numeric conversions: a value of one integer
type never silently becomes another (see [Assignment](14-assignment.md)).
Moving a value across integer widths is always spelled out with one of
two explicit conversion intrinsics:

- `cast<T>(expr)` — value-preserving **widening** and pointer permission
  **downgrades**;
- `unsafe_cast<T>(expr)` — any integer conversion that can lose data
  (runtime-checked).

```franko
uint8_t a = 200;
uint16_t b = cast<uint16_t>(a);        // widening: plain static_cast

int32_t big = 300;
uint8_t small = unsafe_cast<uint8_t>(big);   // checked: throws at run time
```

Like `$(...)` and `getptr`, both are compiler intrinsics, not function
calls: they cannot be overloaded, shadowed, or passed around.

## 16.1 Cast Syntax

```franko
cast<uint16_t>(a)
cast<view<array<byte>>>(p)
unsafe_cast<int32_t>(big)
```

`cast<T>` accepts a full type as its target: one of the eight primitive
integer types, or a pointer type (`ptr<T>`, `mview<T>`, `view<T>`).
Arrays, structs, and `void` are rejected at check time. The operand must
match the target category: an integer expression for integer targets, a
pointer or `null` for pointer targets.

`unsafe_cast<T>` is restricted to the eight primitive integer types
(`int8_t` … `uint64_t`, including the `int` / `uint` / `byte` aliases);
a pointer target does not parse. The operand must be an integer
expression.

A cast is never an lvalue:

```franko
cast<uint16_t>(a) = 5;   // invalid
```

Casts compose freely with other expressions:

```franko
uint8_t cmp = 1 < 2;
int32_t wide = cast<int32_t>(cmp);
print(unsafe_cast<uint8_t>(wide));
```

## 16.2 Static (Value-Preserving) Casts

When every value of the source type is representable in the target
type, the cast is statically safe and compiles to a plain
`static_cast` with zero runtime cost:

- strictly wider, same signedness:
  `uint8_t -> uint16_t -> uint32_t -> uint64_t` and
  `int8_t -> int16_t -> int32_t -> int64_t`;
- unsigned to signed of strictly greater width:
  `uint8_t -> int16_t`, `uint16_t -> int32_t`, `uint32_t -> int64_t`.

These are the conversions `cast<T>` performs. Casting to the type the
expression already has is allowed and compiles to the value unchanged.

## 16.3 Checked Casts (`unsafe_cast<T>`)

Every conversion that can lose data — narrowing, any signed-to-unsigned
conversion, `uint64_t -> int64_t`, and same-width signedness changes —
must be written `unsafe_cast<T>(expr)`. It compiles to a runtime-checked
`franko_unsafe_cast<T>(expr)` call. The check compares the value
against the target type's range and throws on overflow, so a bad
conversion aborts instead of wrapping or truncating.

```franko
int32_t big = 300;
uint8_t small = unsafe_cast<uint8_t>(big);   // throws: 300 does not fit uint8_t
```

A checked cast that fits succeeds normally:

```franko
uint32_t w = 70000;
int32_t s = unsafe_cast<int32_t>(w);   // valid: 70000 fits int32_t
print(s);
```

Writing `cast<T>` where the conversion can lose data is a compile error
that points at `unsafe_cast<T>`: the two spellings keep the dangerous
conversions visibly marked at every call site.

## 16.4 Constant Casts

A conversion of a folded constant expression is validated at compile
time: the value must fit the target type or compilation fails. There is
no wraparound, at compile time or at run time.

```franko
uint8_t ok = unsafe_cast<uint8_t>(200);    // valid
uint8_t bad = unsafe_cast<uint8_t>(300);   // invalid: 300 does not fit uint8_t
```

## 16.5 Pointer Permission Casts

`cast<T>` with a pointer target is an explicit **permission downgrade**
(see [Pointers](24-pointers.md)): the referenced type must stay
identical and the permission may only weaken
(`ptr -> mview -> view`).

```franko
array<byte> buf;
buf.init(16);
ptr<array<byte>> p = getptr(buf);
view<array<byte>> v = cast<view<array<byte>>>(p);   // explicit downgrade
mview<array<byte>> m = cast<mview<array<byte>>>(p);
view<array<byte>> v2 = cast<view<array<byte>>>(m);
```

Explicit downgrades are never required — the same conversions happen
implicitly wherever a lower permission is expected — but they can be
written for clarity. Casting *upwards* (`view` to `mview`/`ptr`) or
across different pointee types is invalid:

```franko
ptr<array<byte>> q = cast<ptr<array<byte>>>(v);   // invalid: raises permissions
```

Raw integers can never be converted to pointers.

## 16.6 What Casts Do Not Cover

- Casts do not appear in constant-expression slots (static array
  sizes, global initializers, struct field defaults): those contexts
  accept only plain integer literals and operators.
- Calls, returns, assignments, and operators still require exact
  type equality on their operands, with the single exception of the
  implicit pointer permission downgrades. `cast<T>` produces a value of
  exactly `T`, which is what makes those positions accept it.
  A value that must cross widths on the way into a call is converted
  into a temporary first:

```franko
func f(int64_t x) -> int64_t {
    return x;
}

func main() -> int32_t {
    int32_t v = 5;
    int64_t r = f(cast<int64_t>(v));
    print(r);
    return 0;
}
```

---

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

---

# 18. Conditions

`if`, `while`, and `for` conditions must be integer expressions.

`for` conditions are checked after desugaring, when the loop has been lowered to a `while` loop. See [Statements](19-statements.md).

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
ptr<int32_t> p;

if (p) {
    print(1);
}
```

To test pointers, compare them explicitly:

```franko
if (p == q) {
    print(1);
}
```

---

# 19. Statements

Franko supports the following statements inside function bodies and blocks:

- variable declarations;
- assignments;
- compound assignments (`+=`, `-=`, `*=`, `/=`, `%=`);
- increment and decrement statements (`x++`, `x--`);
- blocks;
- `if`;
- `while`;
- `for`;
- `return`;
- `print`;
- expression statements;
- function call statements;
- array intrinsic statement forms;
- array initializer assignments.

Executable statements are not allowed at global scope.

Assignments, compound assignments, and increment/decrement are statement forms only: `x++` never produces a value, so `print(x++)` does not parse. See [Assignment](14-assignment.md).

## 19.1 Blocks

A block is a sequence of statements enclosed in braces and creates a new lexical scope.

The braces of a function body are an exception: they do not create an additional scope beyond the function's parameter/body scope. See [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md).

## 19.2 If Statements

The condition must be an integer expression.

Braces are optional: the body may be a single statement. A braceless body is wrapped in an implicit block during desugaring, so declarations inside it are scoped to that block and are not visible after the `if`. See [Desugaring](05-desugaring.md).

## 19.3 While Statements

The condition must be an integer expression.

Braces are optional: the body may be a single statement. A braceless body is wrapped in an implicit block during desugaring, so declarations inside it are scoped to that block and are not visible after the `while`. See [Desugaring](05-desugaring.md).

## 19.4 For Statements

`for` loops use C-style headers:

```franko
for (init; condition; update) {
    body
}
```

- `init` and `update` are optional simple statements (declarations, assignments, compound assignments, increments, expression statements, and similar);
- the condition is required and must be an integer expression.

Valid:

```franko
for (int32_t i = 0; i < 5; i = i + 1) {
    print(i)
}

for (int32_t i = 0; i < 5; i++) {
    print(i)
}

for (; i < 5; i = i + 1) {
    print(i)
}

for (i = 0; i < 5;) {
    print(i)
    i = i + 1
}
```

The loop is desugared before semantic analysis:

```franko
for (int32_t i = 0; i < 5; i = i + 1) {
    print(i)
}
```

becomes:

```franko
{
    int32_t i;
    i = 0;
    while (i < 5) {
        print(i);
        i = i + 1;
    }
}
```

The wrapping block scopes the loop variable declared by `init`: it is not visible after the loop. The `update` statement runs at the end of every body iteration, including the last. It is carried as the desugared loop's epilogue, so `continue` runs it and `break` skips it. See [Break and Continue](19-statements.md) below.

Braces are optional: the body may be a single statement. A braceless body is wrapped in an implicit block during desugaring, so declarations inside it are scoped to that block and are not visible after the `for`. See [Desugaring](05-desugaring.md).

## 19.5 Break and Continue

`break` exits the innermost enclosing loop. `continue` skips the rest of the innermost loop's body and proceeds to the next iteration:

```franko
while (i < 10) {
    i = i + 1;
    if (i == 5) {
        break;
    }
    if (i == 3) {
        continue;
    }
    print(i);
}
```

In a `for` loop, `continue` still runs the `update` (the loop's final body statement), and `break` skips it, matching C's `for` semantics:

```franko
for (int32_t i = 0; i < 5; i = i + 1) {
    if (i == 2) {
        continue;    // i = i + 1 still runs
    }
    if (i == 4) {
        break;       // i = i + 1 does not run
    }
    print(i);
}
```

`break` accepts an optional integer constant expression `N` that selects how many nested loops to exit. `N` must be strictly greater than 0 and no larger than the enclosing loop depth. A plain `break` is equivalent to `break 1`:

```franko
for (int32_t a = 0; a < 3; a = a + 1) {
    for (int32_t b = 0; b < 3; b = b + 1) {
        if (b == 1) {
            break 2;    // exits both loops
        }
        print(b);
    }
}
```

`N` is evaluated with the constant-expression rules, so expressions such as `break 1 + 1` are permitted and fold at compile time. A level that is not a positive constant integer, or that exceeds the enclosing loop depth, is an error.

`continue` does not take a level: `continue 2` is invalid, and `continue` always targets the innermost loop.

`break` and `continue` are reserved keywords. They are only valid inside a loop; using either outside every loop is an error. They may not appear in a `for` header's `init` or `update` positions.

On the generated-C++ level, `break` and `continue` lower to `goto` statements targeting per-loop labels, and labels are only generated for loops that are actually targeted. See [Runtime and Backend Notes](31-runtime-and-backend-notes.md).

## 19.6 Return Statements

A bare `return` is valid only in a `void` function.

A `return expr` statement is required for non-`void` functions.

## 19.7 Print Statements

`print` is a dedicated statement, not a function and not a member method:

```franko
print(x);
print(a, b, c);      // multiple arguments printed space-separated
print();              // prints a bare newline
```

The checker validates print arguments as expressions usable where a value is required (in particular, `void` expressions are rejected), but it does not impose a strict printable-type policy.

Current print limitation: only integer primitives and pointers are printable. The C++14 backend can print:

- integer primitives (with `int8_t`/`uint8_t` promoted so a number is shown rather than a character);
- pointers, including the `null` pointer (printed through `const void*`).

Arrays and structs cannot be printed. They are accepted by the checker, but the generated C++ does not compile because the backend has no output form for them. Print individual elements and fields instead: `print(xs[0])`, `print(s.x)`.

Array initializer lists are not expressions and cannot be printed directly. String literals are expressions (`view<array<byte, N>>`), but `print` shows their global's address rather than the text; print individual bytes instead.

Member-call printing such as `x.print()` is not supported; see [Unsupported or Restricted Features](30-unsupported-or-restricted-features.md).

## 19.8 Expression Statements

Function calls are commonly used as expression statements.

Array intrinsic calls are special expression-statement forms:

```franko
arr(10);
arr.memset(0);
arr.memcpy(getptr(other));
arr.uninit();
```

---

# 20. Arrays

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

However, Franko supports **array initializer lists** as assignment sugar for assigning individual elements into an existing array target. See [Array Initializer Lists](22-array-initializer-lists.md).

## 20.1 Static Arrays

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

The size may be written as `?` to infer it from a declaration initializer (see [Desugaring](05-desugaring.md)):

```franko
array<int32_t, ?> a = [0, 1, 2, 3];   // ? = 4
array<byte, ?> b = [104, 105];        // ? = 2
```

(String literals are `view<array<byte, N>>` values and do not
participate in `?` inference; see [String Literals](23-string-literals.md).)

The current element count of any array is available through the `length()` intrinsic (see [Array Intrinsics](21-array-intrinsics.md)).

## 20.2 Dynamic Arrays

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

An initialized dynamic array may be resized with `resize`. See [Array Intrinsics](21-array-intrinsics.md).

Dynamic array declaration alone does not allocate element storage.

The array must be initialized before ordinary indexed use.

An initialized dynamic array owns constructed elements: for element types that need construction (for example nested dynamic arrays), `init` and `init_zero` default-construct every element into the uninitialized state, so inner arrays are immediately ready for `init` without further setup.

The call-initialization form is only valid for dynamic arrays:

```franko
int32_t x(5);   // invalid: x is not a dynamic array
```

## 20.3 Array Access

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

## 20.4 Array Elements as Lvalues

Array elements are storage-backed lvalues when the array target is storage-backed.

Array elements may be pointed to:

```franko
ptr<int32_t> p;
p = getptr(xs[0]);
```

Nested array elements may also be lvalues when each target is storage-backed.

## 20.5 Array Direct Assignment Is Invalid

Arrays cannot be assigned directly as ordinary values.

Invalid:

```franko
array<int32_t> a;
array<int32_t> b;

a = b;
```

Use one of:

```franko
a.memcpy(getptr(b));
a.memset(0);
a.uninit();
```

Array initializer assignment is not whole-array value assignment.

## 20.6 Array Lifetime Limitations

The checker validates intrinsic legality, but it does not fully prove:

- dynamic array initialized before indexing;
- dynamic array not used after `uninit`;
- dynamic array not initialized twice;
- all array accesses are within bounds.

---

# 21. Array Intrinsics

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
| `target.length()` | any array | `uint32_t` | — |

The receiver of a mutating array intrinsic must be a storage-backed array lvalue.

Examples of storage-backed array targets:

```franko
arr
$(p)
arrs[i]
```

Examples that are not storage-backed array targets:

```franko
getptr(arr)
x + y
1
```

`length()` is read-only, so its receiver may be any array-typed expression; it does not need to be a storage-backed lvalue.

## 21.1 Receiver Permissions

The array intrinsics participate in the pointer permission model (see
[Pointers](24-pointers.md)). When a receiver is reached through a
pointer dereference, the intrinsic needs that pointer's permission:

| Intrinsic | Required receiver permission |
|---|---|
| `init`, `init_zero`, `resize`, `uninit`, `target(size)` | allocation (`ptr`) |
| `memset`, `memcpy`, `memmove` (as target) | mutation (`mview` or `ptr`) |
| `length()` and element reads | read (any permission) |

The `memcpy`/`memmove` *source* is only read, so it may be any pointer
to an array.

```franko
view<array<byte>> v = getview(buf);
$(v)[0] = 1;         // blocked: view is read-only
$(v).memset(0);      // blocked: mutating intrinsic
$(v).length();       // allowed

mview<array<byte>> m = getmview(dyn);
$(m)[0] = 1;         // allowed
$(m).init(10);       // blocked: init needs allocation rights (ptr)
```

## 21.2 `init`, `init_zero`, and `target(size)`

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

Element types that need construction (for example nested dynamic arrays) are default-constructed by `init` and `init_zero`, so inner arrays start in the uninitialized state (length `0`), ready for `init`; trivially constructible element types keep raw (`init`) or zeroed (`init_zero`) storage with no per-element work.

## 21.3 `resize`

Resizes an already-initialized dynamic array. Growing default-constructs the new tail elements; shrinking destroys the dropped tail elements. Trivial element types skip both.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must be a dynamic array;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32_t`; or
  - a nonconstant expression of exactly type `uint32_t`.

## 21.4 `uninit`

Frees a dynamic array's element storage and resets it to the uninitialized state. Constructed elements are destroyed before the buffer is freed; trivially destructible element types skip destruction.

Rules:

- receiver must be a storage-backed lvalue;
- receiver must be a dynamic array;
- takes exactly zero arguments.

## 21.5 `memset`

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

## 21.6 Memsetable Element Types

Current memsetable types:

| Element type | Memsetable? |
|---|---|
| primitive integers | yes |
| static arrays of memsetable elements | yes |
| structs whose fields are all memsetable, recursively | yes |
| dynamic arrays | no |
| pointers | no |

## 21.7 `memcpy`

Copies array contents byte-wise according to the generated C++ array model.

Forms:

```franko
target.memcpy(source)                          // whole array
target.memcpy(source, dstStart, srcStart, count)  // element ranges
```

Rules:

- target must be a storage-backed lvalue array;
- source must be an **pointer to an array** (`ptr<array<T>>` or `ptr<array<T, N>>`), typically produced with `getptr(...)`; passing an array value directly is invalid;
- both target and source may be dynamic or static arrays;
- target and source element types must match exactly;
- static/dynamic shape does not need to match;
- static lengths do not need to match;
- target and source element types must be memcpyable;
- `dstStart`, `srcStart`, and `count` must be:
  - nonnegative constants fitting `uint32_t`; or
  - nonconstant expressions of exactly type `uint32_t`;
- for static arrays, constant start/count ranges on both the destination and the source are checked against the static sizes at compile time.

## 21.8 `memmove`

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

## 21.9 Memcpyable Element Types

Current memcpyable types:

| Element type | Memcpyable? |
|---|---|
| primitive integers | yes |
| pointers | yes |
| static arrays of memcpyable elements | yes |
| structs whose fields are all memcpyable, recursively | yes |
| dynamic arrays | no |

## 21.10 `length`

Returns the element count of an array as `uint32_t`.

Form:

```franko
target.length()
```

Rules:

- takes exactly zero arguments;
- receiver must have array type (dynamic or static);
- `length()` is read-only: unlike the mutating intrinsics, the receiver does not need to be a storage-backed lvalue, and calling it never initializes, modifies, or uninitializes the array;
- a struct method named `length` still dispatches as a method: struct-typed receivers are never treated as array intrinsics.

Dynamic arrays report their runtime length. A dynamic array that has never been initialized, or that has been uninitialized with `uninit()`, reports `0`; `length()` never aborts:

```franko
array<int32_t> xs;
print(xs.length());   // 0
xs.init(4);
print(xs.length());   // 4
xs.resize(10);
print(xs.length());   // 10
xs.uninit();
print(xs.length());   // 0
```

Static arrays report their size as a compile-time constant: `ys.length()` for `array<int32_t, 3> ys` compiles to the constant `3`, with no runtime read.

Nested receivers report the outermost dimension; index first for inner dimensions:

```franko
array<array<int32_t, 2>, 3> m;
print(m.length());      // 3
print(m[0].length());   // 2
```

`length()` returns a value, so it may be used in any value context: arithmetic, loop bounds, assignments, and `print`.

---

# 22. Array Initializer Lists

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

## 22.1 Array Initializer Lists Are Not General Expression Values

Array initializer lists do not have standalone types.

Invalid:

```franko
[1, 2, 3];
print([1, 2, 3]);
foo([1, 2, 3]);
return [1, 2, 3];
```

String literals are ordinary `view<array<byte, N>>` values, so they are
allowed as elements only when the element type is a view (for example
`array<view<array<byte, 3>>, 2> xs = ["abc", "def"];`). They cannot
initialize array-typed elements; copy their bytes with `memcpy` instead
(see [String Literals](23-string-literals.md)).

Array initializer lists are only meaningful in assignment-style contexts where there is an explicit array assignment target.

## 22.2 Array Initializer Assignment

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

## 22.3 Declaration Initializer Form

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

is **not rejected at compile time**. The generated indexed assignments then access an uninitialized dynamic array, and the runtime aborts with a "Franko_Dynamic_Array not initialized" error. Dynamic array initialization is not statically tracked; see [Arrays](20-arrays.md).

Correct:

```franko
array<int32_t> dyn;
dyn(3);
dyn = [1, 2, 3];
dyn.uninit();
```

The initializer list itself never emits `dyn(3)`.

## 22.4 Static Arrays

For static arrays, initializer lists lower to indexed assignments.

If the initializer list is shorter than the static array length, only the listed elements are assigned. Unlisted elements are untouched.

If the initializer list is longer than the static array length, the generated indexed assignment is rejected by ordinary static array bounds checking.

There is no special “initializer length must exactly match static length” rule. The ordinary generated assignments determine validity.

Exception: when a `?` inferred size is used in any dimension of the declaration, the initializer must fit exactly at every level. A shorter list is a desugaring error (partial fill is not allowed with `?`), sibling lists must have equal length, and an empty list cannot infer a size. See [Desugaring](05-desugaring.md).

## 22.5 Dynamic Arrays

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

## 22.6 Element Expressions

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

## 22.7 Nested Initializer Lists

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

## 22.8 Nested Dynamic Arrays

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

## 22.9 Empty Initializer Lists

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

## 22.10 Array Initializer Lists and Global Scope

Array initializer lists are assignment sugar.

Therefore, they are only valid where the resulting assignment statements are valid.

At global scope, executable statements are not allowed.

Invalid:

```franko
array<int32_t, 3> xs;
xs = [1, 2, 3];
```

Declaration initializer sugar at global scope is also invalid if it desugars into executable statements (assignments, expression-statement calls, or indexed element assignments).

## 22.11 Summary of Array Initializer Lists

Array initializer lists in Franko are:

- not general expressions;
- not array values;
- allowed as assignment initializer syntax;
- allowed in declaration initializers through ordinary declaration-assignment sugar;
- lowered to recursive indexed assignments;
- allowed to contain ordinary expressions;
- not required to contain compile-time constants;
- partial when shorter than the target array (unless `?` inference is used, which requires an exact fit);
- rejected for static arrays only when generated indexes are out of bounds;
- not responsible for allocating, resizing, or initializing dynamic arrays;
- intended for dynamic arrays only when the target dynamic storage already exists (this is not enforced at compile time; missing initialization fails at runtime);
- recursively applicable to nested arrays.

---

# 23. String Literals

Franko string literals are **read-only views of byte arrays**. A string
literal is an ordinary expression whose type is `view<array<byte, N>>`
(see [Pointers](24-pointers.md)): a read-only pointer to a static byte
array of `N` UTF-8 bytes, where `N` is the literal's byte length.

```franko
"Franko"           // view<array<byte, 6>>
"Hello, World!"    // view<array<byte, 13>>
```

## 23.1 A String Literal Is a View Value

Each distinct literal is hoisted to its own global `array<byte, N>`
holding the UTF-8 bytes, and the literal expression is `getview(...)`
over it. Identical literal texts share one global. No null terminator is
appended: `N` is exactly the number of content bytes.

Because the literal is a pointer, it must be dereferenced with `$(...)`
to reach the bytes:

```franko
print($("abc").length());   // 3
print($("abc")[0]);         // 97
```

A string literal may appear anywhere an expression may appear:

```franko
view<array<byte, 3>> s = "abc";     // declaration initializer
s = "xyz";                          // assignment to a view variable
print($("hi").length());            // print argument
take("hi");                         // function argument
return "hi";                        // return value (with a view return type)
```

## 23.2 UTF-8 Bytes

A string literal stores the literal's **UTF-8 bytes**: one array element
per byte. Multi-byte sequences expand accordingly:

```franko
print($("你好").length());   // 6: 228 189 160 229 165 189
```

## 23.3 Escapes

The supported escape sequences are `\\ \" \a \b \f \n \r \t \v`.
`\u`, `\x`, and `\0` are not supported, and a trailing backslash is
invalid. Any other escape is a lex error.

```franko
print($("\t\n\"\\"")[0]);   // 9
```

## 23.4 Strings and Byte Buffers

Arrays are value types and are **not assignable from strings**: a
`view<array<byte, N>>` cannot initialize or assign an array. Copy the
bytes with the `memcpy` intrinsic instead — a string literal is a valid
`memcpy` source:

```franko
array<byte, 6> buf;
buf.memcpy("hello");      // copies 5 bytes; buf[5] is untouched
print(buf[0]);            // 104
```

For dynamic arrays, size the buffer first:

```franko
array<byte> buf;
buf.init($("hello").length());
buf.memcpy("hello");
```

For the same reason `array<byte, ?>` cannot infer its size from a string
literal; declare the size explicitly or infer from an array literal.

## 23.5 Global String Views

A pointer-typed global may be initialized with a string literal (or with
`getptr`/`getmview`/`getview` over another global, or `null`):

```franko
view<array<byte, 5>> GREETING = "hello";

func main() -> int32_t {
    print($(GREETING).length());   // 5
    return 0;
}
```

## 23.6 Printing Strings

`print` is unchanged (it is superseded by a future io class): a string
literal passed to `print` prints as the address of its global byte
array, like any other pointer. Print individual bytes instead:

```franko
print($("hi")[0]);   // 104
print($("hi")[1]);   // 105
```

## 23.7 Empty Strings

An empty string literal `""` is not supported: a string literal is a
`view<array<byte, N>>` with `N >= 1`.

---

# 24. Pointers

Franko has a capability-based pointer system with three pointer types,
representing strict levels of permission:

| Type | Meaning | C++ Emission | Allowed Operations |
|---|---|---|---|
| `ptr<T>` | Owner | `T*` | Read, Write, Allocate, Free |
| `mview<T>` | Writer | `T*` | Read, Write |
| `view<T>` | Reader | `const T*` | Read Only |

(`ptr` = pointer, `mview` = mutable view, `view` = view.) All three are
thin pointers; the permissions are enforced entirely at compile time.

A pointer value refers to a storage location containing a value of type `T`.

Examples:

```franko
ptr<int32_t> p;
mview<uint8_t> b;
view<ptr<int32_t>> pp;
ptr<array<int32_t>> ap;
```

Pointers are typed, assignable, copyable, dereferenceable, and comparable.

Pointers are not arithmetic values.

`ptr<void>`, `mview<void>`, and `view<void>` are invalid because `void`
is not an ordinary value type.

## 24.1 Creating Pointers

Views are created with intrinsics over storage-backed lvalues, in the
same shape as `getptr`:

- `getptr(expr)` creates a `ptr<T>`.
- `getmview(expr)` creates an `mview<T>`.
- `getview(expr)` creates a `view<T>`.

If the operand has type `T`, the result has the corresponding pointer
type. Creating a pointer demands that the operand's storage carries at
least the requested permission: `getptr` needs owner access,
`getmview` needs write access, `getview` always works. This is what
prevents laundering a `view` into a `ptr`.

## 24.2 Dereferencing with `$`

If the operand has type `ptr<T>`, `mview<T>`, or `view<T>`, then
`$(...)` has type `T`.

`$(...)` is also an lvalue when the pointer carries write permission:

```franko
$(p)[i] = x;    // array element access (mutating: needs ptr or mview)
$(v)[i];        // reading an element (any permission)
$(v).field;     // struct field access
$(v).length();  // intrinsic over the pointee
```

## 24.3 Nested Pointers

Franko supports pointers to pointers, of any permission:

```franko
view<ptr<int32_t>> v;   // read-only view of a ptr<int32_t> slot
```

## 24.4 Assignment and Implicit Downgrades

The **only** implicit conversion in Franko is a pointer permission
downgrade over an identical referenced type:

- `ptr<T>` implicitly casts to `mview<T>` and `view<T>`;
- `mview<T>` implicitly casts to `view<T>`;
- passing a `view<T>` where an `mview<T>` or `ptr<T>` is expected is a
  compile error.

```franko
ptr<array<byte>> p = getptr(buf);
mview<array<byte>> m = p;          // implicit downgrade
view<array<byte>> v = p;           // implicit downgrade
view<array<byte>> v2 = m;          // implicit downgrade

ptr<array<byte>> q = v;            // invalid: raises permissions
```

Downgrades apply to assignment, return values, and function arguments.
Every other position requires exact type equality. Raw integers cannot
be converted to pointers.

Explicit downgrades may be written with `cast` (see [Casts](16-casts.md)):

```franko
view<array<byte>> v = cast<view<array<byte>>>(p);
```

## 24.5 Pointer Comparison

Pointers may be compared using only the equality operators:

```franko
==
!=
```

Both operands must have identical pointer types.

Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for pointers.

Pointer comparison produces `uint8_t`.

## 24.6 No Pointer Arithmetic

Pointers are not integers and do not support arithmetic.

## 24.7 Pointers of Arrays

Arrays may be pointed to:

```franko
array<int32_t> arr;
ptr<array<int32_t>> p;

p = getptr(arr);
$(p)(10);
```

Array initializer assignment through a dereferenced array pointer is valid when the dereference produces a storage-backed array lvalue:

```franko
array<int32_t, 3> xs;
ptr<array<int32_t, 3>> p;

p = getptr(xs);

$(p) = [1, 2, 3];
```

Equivalent to:

```franko
$(p)[0] = 1;
$(p)[1] = 2;
$(p)[2] = 3;
```

## 24.8 Permission Propagation (Nested Types)

Permissions apply to the **value** being pointed to. Because arrays and
structs are value types, permissions propagate transitively downward
through them — and they stop at pointer boundaries.

### Rule 1: Value Types Are Transitive

A `view` of a nested array locks every dimension:

```franko
view<array<array<byte>>> v;
// $(v)[i] reads are fine; $(v)[i][j] = x is blocked, and so is
// $(v)[i].init(...): the entire nested structure is read-only.
```

An `mview` of a nested array may write every level's elements, but the
lifecycle intrinsics are blocked at *every* level — they need allocation
rights (`ptr`), and an index or member access never leaves the region the
pointer dereference created:

```franko
mview<array<array<byte>>> m;
$(m)[0][1] = 5;     // allowed: element writes at every level
$(m)[0].init(2);    // blocked: init needs allocation rights (ptr)
$(m).init(2);       // blocked: the same rule applies to the outer array
```

To run lifecycle intrinsics on a nested array, reach it through a `ptr`:
either hold the outer pointer (`ptr<array<array<byte>>>`) or extract an
inner `ptr` (Rule 2).

### Rule 2: Pointer Boundaries (Indirection)

If a container holds pointer values, the container's permission governs
only **the pointer slots** (whether they can be overwritten). The memory
behind those pointers follows each pointer's own type:

```franko
mview<array<view<array<byte>>>> v;
$(v)[0] = getview(x);        // allowed: overwrite the slot
$($(v)[0])[0] = 5;           // blocked: the extracted view is read-only
```

```franko
view<array<ptr<array<byte>>>> v;
$(v)[0] = getptr(x);         // blocked: the outer view locks its slots
$($(v)[0])[0] = 5;           // allowed: through the inner ptr
$($(v)[0]).init(10);         // allowed: full allocation rights on the pointee
```

Summary: `view` locks down the memory it directly touches. If that
memory contains raw values (like nested arrays), those values are
locked. If it contains pointers, the pointer *variables* are locked, but
the memory they point to follows its own type rules.

## 24.9 The null Literal

`null` is a literal of type `null`. It is valid only where a pointer of
any permission is expected:

```franko
ptr<int32_t> p;
p = null;               // valid

int32_t x = null;       // invalid: null is only valid for pointer types
p = 0;                  // invalid: 0 is an integer, not null
```

`null` and `0` are different values. A pointer cannot be assigned or compared against the integer `0`; use `null` instead.

`null` may be compared with a pointer using equality only:

```franko
if (p == null) {
    // p is null on this path
}
if (p != null) {
    // p is not null on this path
}
```

Comparing `null` with a non-pointer value is invalid. Relational comparison (`<`, `>`) of `null` is invalid.

`null` may be passed to pointer parameters and returned from pointer
functions.

`print(null)` prints the null pointer as a pointer value.

### 24.9.1 Null-Initialized Pointer Declarations

Pointer declarations are **guaranteed null-initialized**, unlike ordinary declarations, which start uninitialized:

```franko
ptr<int32_t> p;        // p is null here, guaranteed
print(p == null);       // true
```

This guarantee also applies to pointer-typed struct fields and to
`mview`/`view` variables.

### 24.9.2 Nullness Certification

The compiler tracks whether a pointer variable is null. The only
expressions that certify a pointer as non-null are the creation
intrinsics `getptr(...)`, `getmview(...)`, and `getview(...)`:

```franko
ptr<int32_t> p;            // p is null
$(p);                   // error: cannot dereference null

p = getptr(x);             // p is certified non-null
$(p);                   // valid

ptr<int32_t> q = p;        // q copies p's state: non-null
ptr<int32_t> r = q;        // r copies q's state: non-null
```

Assignment from another pointer variable copies that variable's exact state, so a null state propagates through copies and a certification propagates transitively.

Assignment from values of unknown provenance (call results, parameters, struct-field or array-element reads) yields a maybe-null state: the compiler treats such a variable as possibly null, and dereferencing it is a warning, not an error.

The analysis is **intra-procedural**: no nullness facts cross function
boundaries (a returned pointer is always maybe-null at the call site),
and dangling pointers are not checked.

### 24.9.3 Dereferencing and Warnings

Dereferencing a variable that is **definitely null** is an error.

Dereferencing a variable that is **possibly null** (null on some path, for example after a branch merge) is a **warning**, not an error:

```franko
ptr<int32_t> p;
if (cond != 0) {
    p = getptr(x);
} else {
    p = null;
}
$(p);                   // warning: possibly null
```

Guarding with `p != null` removes the warning, because the compiler refines the variable's state inside the guard.

## 24.10 Capability Linter

To encourage safe, data-oriented design, the compiler tracks the
highest permission each `ptr`/`mview` local variable and parameter
actually exercises and emits a non-fatal **warning** recommending a
downgrade when elevated permissions go unused (global variables are not
linted: their uses span functions, so no per-use summary would be
meaningful):

- a `ptr<T>` that never exercises allocation or lifecycle rights
  (no `init`/`init_zero`/`resize`/`uninit`, no use where a `ptr<T>` is
  expected):

  > *Warning: 'ptr<T> p' never exercises allocation or lifecycle permissions. Consider downgrading to 'mview<T>' for tighter security.*

- a `ptr<T>` or `mview<T>` that is only ever read from:

  > *Warning: 'mview<T> v' is only ever read from. Consider downgrading to 'view<T>' for maximum safety.*

Exercising *allocation* means lifecycle intrinsics, `getptr` over the
variable's region, or using it where a `ptr<T>` is expected (argument,
return, assignment into a `ptr` slot, method `self`). *Mutation* means
assignment through its region, `memset`/`memcpy`/`memmove`, or use where
an `mview<T>` is expected. *Read* covers everything else, including
`length()`.

Because these are warnings, they do not fail the build. They exist to
passively teach developers to default to immutable, non-owning pointers
(`view` and `mview`) whenever possible, minimizing the risk of accidental
memory corruption.

## 24.11 Pointer Lifetime Limitations

Pointer lifetime is not fully tracked.

The compiler does not fully track dangling pointers or aliasing through pointers. A pointer may outlive its pointee, and the compiler does not detect that case at compile time; a runtime check for this is planned as a later change.

---

# 25. Structs

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

Structs are nominal types: two distinct struct declarations are distinct types even when their field layouts are identical (see [Type Equality](28-type-equality.md)). This chapter is the complete struct reference; running examples live in [Examples](33-examples.md).

## 25.1 Declaring a Struct

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
- Struct names live in their own namespace: a struct may share its name with a variable or a function. The compiler emits a warning for such clashes and the generated C++ uses kind-prefixed names, so the generated code is unambiguous (see [Runtime and Backend Notes](31-runtime-and-backend-notes.md)).

A struct body contains field declarations and method declarations (see [Methods](26-methods.md)). Assignments, statements, nested functions outside methods, and nested struct declarations are invalid inside a struct body.

## 25.2 Field Declarations

A field declaration uses the same `type name` syntax as a variable declaration, plus an optional constant default:

```franko
struct Buffer {
    array<int32_t> data
    int32_t size = 0
}
```

Rules:

- Field types may be: primitive integer types, static arrays (nested static arrays included), dynamic arrays, pointers (`ptr<T>`), and other declared struct types.
- `void` is not a valid field type, and a field type naming an undeclared struct is rejected.
- Dynamic array fields are allocated explicitly with the allocation call (`field(n)`, see [Array Intrinsics](21-array-intrinsics.md)).
- Duplicate field names within a struct are rejected.
- Fields are kept in declaration order; that order is also the generated field order.
- Field names are ordinary identifiers; keywords cannot be field names.

## 25.3 Field Default Values

A field may declare a default value with `= constExpr`:

```franko
struct Counter {
    int32_t count = 0
    uint32_t limit = 100
}
```

Rules:

- Defaults must be constant expressions (`constExpr`) and are folded at compile time (see [Constant Folding](10-constant-folding.md)).
- Only primitive fields can have default values. Struct, array, and ptr fields reject `= constExpr` as a type mismatch.
- A folded default must fit the field's primitive type; an out-of-range default is rejected (for example `int8_t x = 300`).

## 25.4 Using Struct Types

A struct name used in a type position is resolved against the struct namespace:

- Unknown struct names are rejected with an "unknown struct type" error.
- Field types may reference structs declared anywhere in the program, including structs declared later in the file.
- Struct types compose with the other type constructors: `Point p`, `array<Point> xs`, `array<Point, 4> grid`, `ptr<Point> pp`, and `array<array<Point, 3>, 2> cells` are all valid declared types.

## 25.5 By-Value Cycles

A struct may not reach itself by value through its fields. The by-value edges are:

- a field whose type is a struct;
- a field whose type is a static array of structs (recursively, including nested static arrays).

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
    ptr<Node> next        // valid: pointer edge
    array<Node> kids       // valid: dynamic array edge
}
```

## 25.6 Assignability and Passing by Value

A type is **assignable** when a value of that type can participate in ordinary assignment with `=`:

- primitive integer types are assignable;
- pointer types `ptr<T>` are assignable;
- a struct is assignable if and only if all of its fields are assignable, recursively.

Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing any of them is **pointer-only**.

Consequences:

- **Whole-value assignment.** For an assignable struct, `a = b;` copies every field. For an pointer-only struct the assignment is rejected (see [Assignment](14-assignment.md)); share the struct through `ptr<X>` and `getptr(s)` instead.
- **By-value parameters.** A parameter may have an assignable struct type (see [Functions](12-functions.md)); the argument is copied at the call.
- **By-value returns.** A function may return an assignable struct type.
- Diamond-shaped field graphs are fine: a struct with two fields of the same struct type is assignable as long as that type is assignable.

Arrays of structs follow the usual element rules: `array<X, N>` is `memset`-able only when every field of `X` is `memset`-able (pointers excluded), and `memcpy`-able when every field is `memcpy`-able (pointers included). See [Array Intrinsics](21-array-intrinsics.md).

Structs are not integers: operators and `if`/`while` conditions reject whole structs, and `print` cannot print a whole struct — the checker accepts it, but the generated C++ does not compile (see [Statements](19-statements.md)). Operate on fields instead: `print(s.x)`.

## 25.7 Member Access and Field Lvalues

Member access uses dot syntax. Access chains compose with every storage form:

```franko
s.field               // local struct
$(p).field        // through an pointer
$(a.next).field   // another hop through an ptr field: deref the link first
xs[i].field           // element of a static or dynamic array
```

- `s.field` is a storage-backed lvalue exactly when its base expression is one, so field accesses can be assigned to: `s.field = v;`, `$(p).field = v;`, `xs[i].field = v;` (see [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md)).
- `getptr(s.field)` is valid and yields `ptr<T>` for the field's type `T`.
- A field may itself be an array, and array intrinsics work on fields:
  `b.data(4)` (allocation shorthand for `b.data.init(4)`), `b.data.init_zero(n)`, `b.data.memset(0)`, `b.data.memcpy(getptr(other))`, `b.data[i]`, and `b.data.uninit()`.
- Struct methods also use member-call syntax (`s.increment()`); see [Methods](26-methods.md). Only struct methods and the built-in array intrinsics use member-call syntax.

## 25.8 Initialization Semantics

- **Local declarations.** A local struct without an initializer is default-constructed: fields with defaults receive their default value; fields without defaults start with indeterminate values, like ordinary local variables (see [Declarations](11-declarations.md)).
- **Declaration initializer sugar.** `P b = a;` and `P c = f();` are valid for locals and copy the whole struct value; the sugar desugars into a declaration plus a whole-value assignment.
- **Global declarations.** Global structs are statically allocated. Fields with defaults receive their defaults; fields without defaults are zero-initialized in the generated C++ (static storage). This is an implementation characteristic of the current backend; do not rely on it in portable code — give every field an explicit default or assign before use.

## 25.9 Struct Globals

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

## 25.10 Backend Notes

The C++14 backend:

- emits each struct as a C++ `struct STRUCT_X { ... };` definition, preceded by a forward-declaration block (`struct STRUCT_X;`) so pointers may reference structs declared later in source order;
- orders struct definitions topologically over by-value field dependencies, so a struct used by value inside another is defined first (`ptr` and dynamic-array fields impose no ordering constraint);
- emits field defaults as C++ non-static data member initializers (NSDMI), e.g. `int32_t num = 0;`;
- emits member access as `target.field`.
- emits methods as mangled free functions (`METH_f_OFSTRUCT_X_HASH_<8hex>`); see [Methods](26-methods.md).

## 25.11 Restrictions Summary

- Structs are declared only at top level; no local or nested struct declarations.
- Struct methods use member-call syntax alongside the built-in array intrinsics (see [Methods](26-methods.md)).
- No operators on whole structs: arithmetic, comparison, and conditions reject them; `print` cannot print a whole struct — print individual fields instead.
- No struct literals: a struct value is built field-by-field, copied from another struct, or returned by a function.
- Field defaults are `constExpr` on primitive fields only.
- A struct may not reach itself by value (through struct fields or static array elements); `ptr<...>` and dynamic-array fields break the cycle.
- A struct with any non-assignable field is pointer-only: whole-value assignment, by-value parameters, and by-value returns are rejected; use `ptr<X>`.
- Duplicate struct names and duplicate field names are rejected.
- The complete list of current language restrictions is in [Unsupported or Restricted Features](30-unsupported-or-restricted-features.md).

---

# 26. Methods

A struct may declare methods: functions bound to the struct whose first parameter is explicitly `self` (`ptr<X> self`, `mview<X> self`, or `view<X> self`). Methods are called with member-call syntax and lower to ordinary function calls with a synthesized `getptr`/`getmview`/`getview(receiver)` first argument matching the declared `self` type (see [Structs](25-structs.md) for fields and [Functions](12-functions.md) for free functions).

```franko
struct X {
    int32_t y = 0

    func increment(ptr<X> self) -> void {
        $(self).y = $(self).y + 1
        return
    }
}

func main() -> int32_t {
    X s
    s.y = 41
    s.increment()
    print(s.y)
    return 0
}
```

## 26.1 Method Declaration Syntax

A struct body contains field declarations and method declarations:

```franko
struct X {
    int32_t y = 0
    func increment(ptr<X> self) -> void {
        $(self).y = $(self).y + 1
        return
    }
}
```

Rules:

- Methods are `func` declarations inside a `structDecl` body; separators between members are optional, matching field syntax.
- A method may declare any return type valid for free functions, including `void` (see [Functions](12-functions.md)).
- A method may declare additional parameters after `self`; they follow ordinary parameter rules.
- A method body follows ordinary function body rules, including return checking (see [Functions](12-functions.md)).
- Method names are ordinary identifiers; a method may not share its name with a field of the same struct, and duplicate method signatures are rejected (see [Declaration Type Validity](29-declaration-type-validity.md)).

## 26.2 The `self` Parameter

The first parameter of every method must be literally `self`, typed
`ptr<X> self`, `mview<X> self`, or `view<X> self`, where `X` is the
enclosing struct. The permission declares what the method does with its
receiver: an owning `self` may run lifecycle operations, a mutable
`self` may write, a read-only `self` may only read (see
[Pointers](24-pointers.md)):

```franko
struct X {
    int32_t y = 0
    func get(view<X> self) -> int32_t {
        return $(self).y
    }
    func set(mview<X> self, int32_t v) -> void {
        $(self).y = v
    }
}
```

Rules:

- A method with no parameters is rejected: `Method 'X.foo' must have 'ptr<X> self' as its first parameter (also allowed: 'mview<X> self', 'view<X> self')`.
- A method whose first parameter is not named `self` is rejected with the same diagnostic.
- A method whose `self` parameter does not have type `ptr<X>`, `mview<X>`, or `view<X>` for the enclosing struct `X` is rejected: `Method 'X.foo' has a 'self' parameter of type T; it must be ptr<X> (or mview<X> or view<X>)`. This covers by-value `X self`, non-pointer `int32_t self`, and `ptr<Y> self` for a different struct `Y`.
- Only the first parameter may be named `self`: `Only the first parameter of a method may be named 'self'`.
- Method overload identity **excludes `self`'s type**: a struct may not declare two methods with the same name and the same remaining parameter types but different `self` permissions.
- `self` is an ordinary pointer variable of its declared permission inside the body: `$(self).y`, `getptr($(self).y)`, and even `self = other` behave exactly like any other pointer variable. In particular `self.y` is invalid: member access requires a struct value, so fields are reached as `$(self).y` (see [Pointers](24-pointers.md)). Writing through a `view<X> self` is rejected like any other write through a view.
- Method names may coincide with array intrinsic names (`init`, `memset`, ...). Dispatch keys off the receiver type, not the name: a struct-typed receiver calls the method, while an array-typed receiver calls the intrinsic (see [Array Intrinsics](21-array-intrinsics.md)).

## 26.3 Method Calls and Receivers

A method is invoked with member-call syntax. The compiler prepends the
`self` argument, created with `getptr`, `getmview`, or `getview`
matching the method's declared `self` type:

```franko
s.increment()              // -> METH_increment_OFSTRUCT_X_HASH_<8hex>((&s))
$(p).increment()       // explicit deref for ptr<X> receivers
xs[i].increment()          // array element receiver
s.inner.increment()        // field receiver
```

The creation intrinsic enforces the receiver's permission: calling a
`ptr<X> self` method through a `view` receiver is rejected
(`getptr(...) requires allocation rights (ptr) over its target`), while
a `view<X> self` method is callable through any receiver. When the
receiver is an lvalue reached through an inner pointer (for example
`$($(v)[0]).increment()` where the element is a `ptr<X>`), that inner
pointer's own permission applies.

Rules:

- The receiver must have struct type; otherwise the call is rejected: `Method call 'foo' is only valid on struct values, got type T`.
- An `ptr<X>` receiver is rejected with a hint: `Method call 'foo' is only valid on struct values, got type ptr<X>; use $(p).foo(...) for pointer receivers`. There is no implicit auto-deref.
- The receiver must be a storage-backed lvalue: a variable, field, array element, or `$(...)` chain (see [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md)). Call results are rejected: `Method call receiver must be a storage-backed lvalue: a variable, field, array element, or $(...) expression`.
- Unknown methods and mismatched arguments are rejected: `No matching overload for method call 'X.foo(...)'`, with user argument types only (`self` is implicit and excluded from the printed signature). A struct-typed receiver never falls back to array-intrinsic diagnostics, even when the name coincides with one: `s.init()` with no such method reports the method-call diagnostic.
- Arity diagnostics exclude `self`: `Method call to 'X.foo' has wrong arity: expected N argument(s) (excluding self), got M`.
- Array-typed receivers keep their intrinsic meaning (`b.data.init(n)`, `b.data.memset(0)`); struct-typed receivers call methods. The two namespaces coexist because a receiver cannot be both types at once.
- Methods are never callable as free functions: `X.foo(getptr(s))` and `foo(getptr(s))` are rejected as ordinary overload failures.
- A method call lowers to `SemanticFunctionCallNode` with a synthesized creation-intrinsic (`getptr`/`getmview`/`getview`) first argument, so argument checking, return checking, and code generation reuse the ordinary function-call paths.

## 26.4 Fields vs Methods Name Resolution

Fields and methods share member-call syntax but resolve in separate namespaces:

- `s.field` resolves as field access when `field` names a struct field.
- `s.name(args)` resolves as a method call when `name` matches a method overload for the supplied argument types.
- A method may not share its name with a field of the same struct: `Method 'X.foo' conflicts with field 'foo' of struct 'X'`.
- `s.unknown()` is rejected as `No matching overload for method call 'X.unknown(...)'`, which is also what keeps the historical `member_call_non_intrinsic` rejection for genuinely unknown members.

## 26.5 Overloading

Methods overload exactly like free functions: overload identity is name plus ordered parameter types; return type and parameter names are not part of identity (see [Functions](12-functions.md)).

```franko
struct Counter {
    int32_t n = 0
    func add(ptr<Counter> self, int32_t v) -> void {
        $(self).n = $(self).n + v
        return
    }
    func add(ptr<Counter> self, int32_t a, int32_t b) -> void {
        $(self).n = $(self).n + a + b
        return
    }
}
```

Rules:

- The implicit `self` parameter (whatever its permission) does not distinguish overloads; overloads must differ in their user parameters.
- Duplicate method signatures are rejected: `Duplicate declaration of method 'X.foo(...)' in struct 'X'`.
- Ambiguous arguments are rejected like free-function ambiguity: `Ambiguous call to overloaded method 'X.foo(...)': multiple overloads match equally well (argument constants fit several signatures, or arguments rely on the same number of pointer permission downgrades)`.

## 26.6 `self` Is a Reserved Keyword

`self` is a lexer keyword, reserved everywhere:

```franko
int32_t self = 0          // invalid: parse error
func self() -> void {}    // invalid: parse error
struct self {}            // invalid: parse error
```

Rules:

- `self` cannot name a variable, parameter (except a method `self` parameter), field, function, struct, or method: ordinary declarations use `IDENTIFIER` and fail to parse on `self`.
- A free-function parameter named `self` parses (parameters accept `self` so method declarations share the rule) but is rejected semantically: `'self' is only valid as the first parameter of a struct method`.
- `self` as an expression (`self`, `getptr(self)`, `$(self)`) is valid only inside a method body, where it resolves to the `self` parameter. Outside a method it is rejected: `keyword 'self' is only valid inside a method body`.
- `x.self` parses (member names accept keywords) but always fails resolution, like `x.print`.

## 26.7 Scope Inside Method Bodies

A method body scopes exactly like a free-function body, plus the `self` parameter:

- Parameters (including `self`) are ordinary locals in the method's initial scope; locals may shadow field names (`int32_t y` inside `increment` is unrelated to `$(self).y`).
- A method may call other methods on the same object only through an explicit receiver: `$(self).other()`.
- `return` attaches to the enclosing method symbol, so void/non-void return rules apply unchanged (see [Functions](12-functions.md)).
- Name lookup inside a method follows ordinary lexical plus namespace rules; there is no implicit `this` field scope (see [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md)).

## 26.8 Methods and Pointer Checking

`self` is an `ptr<X>` parameter:

- `self` enters pointing: the call synthesizes `getptr`/`getmview`/`getview(receiver)` over a storage-backed lvalue, so dereferencing `self` without a null guard is valid and produces no warning. Reassigning `self` (e.g. `self = null`) flows through ordinary assignment state tracking.
- Pointer dereferences inside methods are tracked normally (see [Memory Safety Checking](27-memory-safety-checking.md)). The checker is intra-procedural.

## 26.9 Backend Notes

Methods emit as free C++ functions with a mangled name:

| Franko construct | Generated C++ name |
|---|---|
| method `f` of struct `X` | `METH_f_OFSTRUCT_X_HASH_<8hex>` |
| method `self` parameter | `VAR_self` (`STRUCT_X*`) |

The 8-hex-digit suffix is the FNV-1a 32-bit hash of the canonical signature key (declared parameter types in order, including `self` as `ptr<X>`, then the return type); see [Runtime and Backend Notes](31-runtime-and-backend-notes.md).

- The mangled name is namespace-qualified like `FUNC_` names: a method of `util::Counter` emits as e.g. `NS_util::METH_bump_OFSTRUCT_Counter_HASH_<8hex>` and is called as `NS_util::METH_bump_OFSTRUCT_X_HASH_<8hex>((&VAR_c))` from outside the namespace.
- `s.method(args)` emits as `METH_method_OFSTRUCT_X_HASH_<8hex>((&recv), args...)`, with each user argument cast to its parameter type to pin Franko overload resolution, exactly like free-function calls.
- Struct definitions emit no members for methods; prototypes emit in the function prototype phase (free functions first, then methods of the same namespace) and definitions emit in the function definition phase.
- A method named `main` mangles to `METH_main_OFSTRUCT_X_HASH_<8hex>` and cannot collide with the entry-point `main`.

## 26.10 Restrictions Summary

- Methods are declared only inside struct bodies; no free-standing method declarations and no nested struct declarations.
- Every method must have `ptr<X> self`, `mview<X> self`, or `view<X> self` as its first parameter; only the first parameter may be named `self`.
- `self` is a reserved keyword; `self.y` is invalid (write `$(self).y`).
- Receivers must be struct-valued storage-backed lvalues; `ptr<X>` receivers require explicit `$(p).method(...)`.
- Methods are never callable as free functions; there is no implicit `this` field access.
- A method name may coincide with an array intrinsic name; struct receivers dispatch to the method while array receivers dispatch to the intrinsic.
- No method pointers, no virtual dispatch, no inheritance: methods are syntactic sugar for functions with an explicit receiver.

---

# 27. Memory Safety Checking

After legality checking, the compiler runs a dedicated **pointer null-state pass** (`PointerNullStateChecker`) over pointer-typed variables (`ptr`, `mview`, and `view` alike).

The pass tracks one fact per pointer variable: whether it is definitely null, definitely pointing, or possibly null on the current execution path.

It checks:

- dereferencing a null pointer (an error);
- dereferencing a possibly-null pointer (a warning);
- that `null` is only used for pointer types (see [Types](06-types.md)).

The pass is **intra-procedural**: each function body is analyzed separately with a fresh state. Called functions are not analyzed for their effects on the caller's pointers, and no facts cross function boundaries (interprocedural nullability is deliberately not attempted).

## 27.1 Pointer States

For each pointer variable the checker tracks one of:

| State | Meaning |
|---|---|
| pointing | the variable holds a certified, real storage pointer |
| null | the variable is definitely null on the current path |
| maybe-null | the variable is null on some continuing paths |

## 27.2 Assignment Rules

- an ordinary `ptr<T>` declaration is **null-initialized**, so it starts in the null state;
- assigning the `null` literal makes the target null;
- assigning `getptr(...)`, `getmview(...)`, or `getview(...)` makes the target pointing; these are the **only** certifications of a non-null pointer;
- assigning another pointer variable copies that variable's exact state, so a null state propagates through copies and certification propagates transitively;
- assigning a value of unknown provenance (a call result, a parameter, a struct-field or array-element read) yields a maybe-null state;
- at function entry, every tracked pointer variable is maybe-null, because parameters and globals carry values the checker cannot see. Exceptions: a method's `self` parameter enters pointing, since the call synthesizes `getptr`/`getmview`/`getview(receiver)` over a storage-backed lvalue; and the global args-form `main`'s `view<array<array<byte>>>` parameter enters pointing, since the template wrapper always passes a live pointer.

## 27.3 Dereference Checking

- `$(p)` where `p` is **null** is an **error**;
- `$(p)` where `p` is **possibly null** is a **warning**.

A variable warns at most once per check, so a path-sensitive traversal that revisits the same deref site does not repeat the warning.

## 27.4 Branch Handling

`if` / `else` is analyzed by checking each branch from the pre-branch state and merging the states of the continuing paths.

A branch that **definitely returns** does not contribute its state to the code following the `if`, because that path never continues there.

Branch-sensitive analysis is limited to a maximum nesting depth (10). Beyond that depth the checker falls back to a conservative path-insensitive merge.

## 27.5 Loop Handling

`while` loops are handled with a bounded abstraction:

- the body is checked once;
- the state after the loop merges the "zero iterations" and "one iteration" states, since a loop may execute zero times.

Loop termination is not proven; the post-loop state is conservative.

`for` loops are desugared to blocks and `while` loops before this pass runs, so they receive the same treatment. See [Desugaring](05-desugaring.md).

## 27.6 Guard Refinement

Inside `if (p == null)` / `if (p != null)` (either operand order), the checker refines `p`'s state per path: the then-branch of `p != null` sees `p` as pointing, and the code after the loop/branch of a failed guard sees `p` as null.

```franko
ptr<int32_t> p;
if (p != null) {
    $(p);      // valid: p is refined to pointing inside the guard
}
$(p);          // error: p is provably null after the guard fails
```

When a guard's assertion contradicts a definite current state, that path is statically infeasible; the checker suppresses dereference diagnostics inside it and does not let its state flow onward.

```franko
ptr<int32_t> p;
p = getptr(x);
if (p != null) {
    $(p);      // valid
}
$(p);          // valid: p is still pointing; the else path was infeasible
```

Refinement is path-sensitive up to the nesting-depth limit described above; beyond the limit, refinement stops and states merge conservatively.

The same refinement runs for loop conditions, so `while (p != null) { ... }` sees `p` as pointing inside the body and as null after the loop.

## 27.7 Limitations

Current limitations of the pointer checker:

- values of unknown provenance (call results, parameters, field and element reads) are treated as maybe-null: dereferencing one is a warning, not an error, and a null value may still flow through them;
- only plain pointer **variables** are tracked. An pointer held in a struct field, an array element, or behind a `$(...)` chain is not tracked;
- dangling pointers are not detected at compile time; an pointer may outlive its pointee, and the compiler does not detect that case. A runtime check is planned as a later change;
- checks are intra-procedural; pointer effects of called functions are not analyzed;
- branch-path sensitivity is capped by the nesting-depth limit described above.

## 27.4 Capability Linting

A second, independent pass (`PointerPermissionChecker`) warns when a
`ptr`/`mview` local variable or parameter requests permissions it never
exercises, so developers default to the tightest pointer that fits. See
the "Capability Linter" section of [Pointers](24-pointers.md).

---

# 28. Type Equality

Franko uses exact, structural semantic type equality in many places. Type equality is implemented once (in the semantic type classes) and used consistently by overload resolution, assignment checking, return checking, and diagnostics.

## 28.1 Primitive Equality

Primitive types are equal only if they have the same kind.

```text
uint8_t  != uint16_t
int32_t  != uint32_t
int32_t  == int32_t
```

The aliases `int`, `uint`, and `byte` canonicalize to `int32_t`, `uint32_t`, and `uint8_t` respectively.

## 28.2 Dynamic Array Equality

Dynamic arrays are equal if their element types are equal.

```text
array<int32_t> == array<int32_t>
array<int32_t> != array<uint8_t>
```

## 28.3 Static Array Equality

Static arrays are equal if:

- element types are equal;
- sizes are numerically equal after unfolding the constant size expression.

The size comparison is numeric, not textual:

```text
array<int32_t, 10>     == array<int32_t, 0xA>
array<int32_t, 5 + 5>  == array<int32_t, 10>
array<int32_t, 10>     != array<int32_t, 20>
```

## 28.4 Pointer Equality

Pointer types are equal if their referenced types are equal **and** their
permissions match: `ptr<T>`, `mview<T>`, and `view<T>` are three distinct
types.

```text
ptr<array<byte>>     == ptr<array<byte>>
ptr<array<byte>>     != mview<array<byte>>
mview<array<byte>>   != view<array<byte>>
view<array<byte, 3>> != view<array<byte, 5>>
```

A pointer permission downgrade (`ptr<T>` to `mview<T>`/`view<T>`,
`mview<T>` to `view<T>`) is the single implicit conversion the language
allows; it does not make the types equal (see [Pointers](24-pointers.md)).

## 28.5 Function Signature Equality

Function signatures are equal if they have:

- the same function identifier;
- the same ordered list of parameter types.

Return type and parameter names are not part of function signature
equality. For methods, the `self` parameter type is not part of signature
equality either (see [Methods](26-methods.md)).

## 28.6 Struct Equality

Struct types are nominal: two struct declarations are distinct types even when their field layouts are identical.

```text
struct X { int32_t n }
struct Y { int32_t n }

X != Y
X == X
```

Duplicate struct names are rejected, so each struct name denotes exactly one declared type. See [Structs](25-structs.md) for the complete struct reference.

---

# 29. Declaration Type Validity

A declared variable type is valid if it is one of:

- primitive integer type;
- a declared struct type;
- dynamic array of a valid non-`void` type;
- static array of a valid non-`void` type with valid size;
- pointer of a valid non-`void` type.

Valid:

```franko
struct X { int32_t n }

int32_t x;
X s;
array<X> xs;
array<uint8_t, 64> bytes;
array<array<byte, 20>, 12> grid;
ptr<int32_t> p;
ptr<array<int32_t>> ap;
```

Invalid:

```franko
void x;
array<void> xs;
ptr<void> p;
T s;    // invalid when no struct named T is declared
```

Static arrays nest: every level of `array<array<byte, 20>, 12>` must satisfy the static array size rules (see [Arrays](20-arrays.md)).

A function return type has separate rules. It may be:

- `void`;
- a primitive integer type;
- an pointer type; or
- a struct whose fields are all assignable value types, recursively.

Array return types are invalid.

---

# 30. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

## 30.1 Function Restrictions

- function declarations are valid only in global scope;
- nested functions are not supported;
- first-class function values are not supported;
- function pointers are not supported;
- anonymous functions/lambdas are not supported;
- parameter names are not used in overload resolution;
- return type is not used in overload resolution;
- arrays cannot be returned by value;
- arrays cannot be passed as parameters by value; use ptr<array<...>> parameters (see [Functions](12-functions.md));
- `void` calls cannot be used in value-producing contexts;
- full path-sensitive return analysis is not currently performed.

## 30.2 No General Member Calls

Struct methods and the built-in array intrinsics use member-call syntax (see [Methods](26-methods.md) and [Array Intrinsics](21-array-intrinsics.md)). All other member calls are rejected, including:

```franko
x.print();          // unsupported member call: print is a statement keyword
x.something(1);     // unsupported member call: unknown method
```

## 30.3 Struct Restrictions

Structs are supported as user-defined aggregate types with named fields, subject to these restrictions:

- struct field initializers must be constant expressions, and only primitive fields can have them (struct, array, and ptr fields reject `= constExpr` as a type mismatch);
- a struct may not reach itself by value through struct fields or static array elements; `ptr<Node>` fields and `array<Node>` (dynamic array) fields break the cycle and are allowed;
- a struct is assignable as a whole value (assignment, by-value parameter, by-value return) only if all of its fields are assignable, recursively: assignable = primitive | pointer | struct-with-all-assignable-fields. Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing any of them is pointer-only: it can be used only through `ptr<X>`;
- structs have methods with explicit `self` receivers of type `ptr<X>`, `mview<X>`, or `view<X>` (see [Methods](26-methods.md)); general member calls beyond struct methods and array intrinsics, such as `s.foo()` for an unknown `foo`, are rejected.

The complete struct reference, including the reasoning behind each restriction, is in [Structs](25-structs.md).

## 30.4 No Floating-Point Types

Only integer primitives are supported.

## 30.5 No Separate Boolean Type

Boolean results use `uint8_t`.

## 30.6 No Implicit Numeric Conversions for Nonconstants

Nonconstant numeric expressions require exact type equality wherever a
typed value is expected. Moving between integer widths is always
explicit through the `cast<T>(expr)` intrinsic (value-preserving widening) or `unsafe_cast<T>(expr)` (checked conversions that can lose data) (see [Casts](16-casts.md)).

## 30.7 Arrays Are Not Assignable as Values

Arrays cannot be assigned directly as ordinary values.

Array initializer assignment is an exception only in syntax shape, not in value semantics.

## 30.8 Array Initializer Lists Are Not General Values

Array initializer lists are not first-class values.

Invalid:

```franko
[1, 2, 3];
print([1, 2, 3]);
foo([1, 2, 3]);
return [1, 2, 3];
```

## 30.9 No Full Dynamic Array Lifetime Checking

The checker validates intrinsic legality, but it does not fully prove dynamic array lifetime and bounds properties.

## 30.10 Pointer Lifetime Is Not Fully Tracked

The compiler does not fully track dangling pointers or ownership.

## 30.11 Print Checking Is Loose

The checker validates print arguments as expressions but does not enforce a strict printable-type set. Only integer primitives and pointers (including `null`) are actually printable. Arrays and structs cannot be printed: the checker accepts them, but the generated C++ does not compile because the backend has no output form for them. See [Statements](19-statements.md).

Array initializer lists are not expressions and therefore cannot be printed directly. String literals are expressions (read-only views), but `print` shows their global's address rather than the text; print individual bytes instead.

## 30.12 Unsupported Operators

The following operators do not exist in the language. The lexer rejects the character `~` outright ("is not a recognizable token"), and a unary `+` fails to parse:

```franko
~x  // no bitwise NOT operator
+x  // no unary plus operator
```

Bitwise and shift compound assignments also do not exist (`&=`, `|=`, `^=`, `<<=`, `>>=`): assignment has only plain `=` plus the five arithmetic compound forms `+=`, `-=`, `*=`, `/=`, `%=` (see [Assignment](14-assignment.md)).

## 30.13 String Literals Cannot Initialize Arrays

A string literal is a `view<array<byte, N>>` value, not byte-array
sugar (see [String Literals](23-string-literals.md)). Arrays are not
assignable, so a string literal cannot initialize or assign an array;
copy the bytes with `memcpy` instead.

Invalid:

```franko
array<byte, 6> s = "hello";    // invalid
s = "hello";                   // invalid
array<byte, ?> s = "hello";    // invalid: '?' infers from array literals
```

Valid:

```franko
array<byte, 6> s;
s.memcpy("hello");
```

## 30.14 No `switch` or `do`/`do-while`

Only `if`, `while`, `for`, `break`, and `continue` control flow is implemented. There is no `switch`, no `do`/`do-while`, and no user-visible `goto` (the generated C++ uses `goto` internally to lower `break` and `continue`; see [Statements](19-statements.md)).

## 30.15 Global Scope Is Declaration-Only

Only struct declaration nodes, variable declaration nodes, and function declaration nodes are valid at global scope.

Executable statements are invalid at global scope.

## 30.16 No C-Style Casts, Ternary, or Multiple Declarators

The following forms do not exist in the grammar:

- C-style cast syntax (`(T)x` does not exist; explicit conversions use the `cast<T>(expr)` and `unsafe_cast<T>(expr)` intrinsics, and fluid integer constants still convert implicitly when they fit);
- the ternary conditional operator (`?:`);
- multiple declarators in one declaration (`int a, b;` is invalid; declare each variable separately).

---

# 31. Runtime and Backend Notes

The current compiler targets C++14.

## 31.1 Pipeline Order

```text
Franko source
  -> lexer + parser
  -> parser AST
  -> Desugarer (sugar lowering, `?` inference, constExpr evaluation)
  -> SemanticAnalyzer (validation of evaluated values, no re-folding, entry-point selection)
  -> LegalityChecker
  -> PointerNullStateChecker
  -> C++14 code generation
  -> program template wrapper (entry-point call + argv conversion)
```

## 31.2 Memory Allocation

The generated code allocates through bundled jemalloc (`je_malloc`, `je_calloc`, `je_realloc`, `je_free`).

Dynamic array element buffers are allocated by the array runtime methods and freed by `uninit()` or by the array object's destructor.

Element construction follows the C++ type of the elements: buffers for trivially default-constructible element types keep raw storage, while any other element type is default-constructed (placement new) when the buffer is allocated. `uninit()` and the array destructor destroy constructed elements before freeing the buffer. Trivially destructible element types skip destruction entirely.

## 31.3 Array Representation

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

The dynamic array length is stored in a `count` field with a `length()` accessor returning `uint32_t`. The field was renamed from `length` because C++ does not allow a field and a method with the same name. Reading `length()` on an uninitialized dynamic array returns `0` and never aborts; every other uninitialized access still aborts. Static arrays keep plain contiguous `T data[N]` storage with no length field or method: `staticarr.length()` in Franko source is emitted by the compiler as a constant, never as a runtime call.

Zero length and uninitialized coincide exactly: `init(0)` and `resize(0)` abort, and `uninit()` restores `data == nullptr, count == 0`, so `length() == 0` holds exactly when the array is uninitialized — there is no initialized empty array.

Element lifetime in the dynamic array runtime:

- `init(n)` and `init_zero(n)` default-construct all `n` elements when the element type needs construction; trivially constructible element types keep raw (`init`) or zeroed (`init_zero`) storage with no per-element work.
- `resize(n)` default-constructs the grown tail when growing and destroys the dropped tail when shrinking; trivial element types skip both.
- `uninit()` and the array destructor destroy all `count` elements before freeing the buffer; trivially destructible element types skip destruction.
- `je_realloc` byte-moves the existing elements on `resize`, which is fine for the runtime's trivially-relocatable element types.

## 31.4 Ordering of Top-Level Analysis

Function signatures are registered before function bodies are analyzed so that forward references, direct recursion, and mutual recursion can be resolved.

Global variable declarations are also registered before function bodies are analyzed, so functions can reference globals declared later in source order.

## 31.5 Desugaring Before Checking

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

String literals are converted to byte array literals in the same pass (see [String Literals](23-string-literals.md)).

`?` static array sizes are inferred during the same pass: each marker is replaced by the corresponding initializer level's element count before type analysis, so the backend only ever sees folded constant sizes. Static `length()` calls are likewise emitted as constants.

Every other `constExpr` slot is also evaluated in this pass: static array sizes, global initializers, and struct field defaults become canonical decimal literals before semantic analysis (see [Constant Folding](10-constant-folding.md)). Semantic analysis validates the resulting values (positivity, range, fit) without re-folding them. Evaluation failures such as division by zero are reported here, as desugaring diagnostics.

The backend does not need to implement initializer lists or strings as runtime values because they are not values in Franko.

## 31.6 Integer Literal Emission

Large integer literals are emitted so that they remain well-formed C++14. In particular, an unsuffixed decimal literal whose value exceeds `LLONG_MAX` is emitted with a trailing `ULL` suffix, because decimal literals in C++14 do not fall back to unsigned integer types.

## 31.7 Print Emission

`print` arguments are emitted as `std::cout` insertions:

- plain integer primitives are inserted directly;
- `int8_t`/`uint8_t` values are promoted with unary `+` so a number is printed rather than a character;
- pointers are cast to `const void*` to avoid accidental string printing.

See [Statements](19-statements.md) for which types are meaningfully printable.

## 31.8 Identifier Emission and Name Clashes

Franko keeps struct type names, variable names, and function names in separate namespaces, but C++14 does not. To keep the generated code unambiguous, every declared identifier is emitted with a kind prefix:

| Franko construct | Generated C++ name |
|---|---|
| struct type `X` | `STRUCT_X` |
| variable `x` (local, global, parameter) | `VAR_x` |
| function `f` | `FUNC_f_HASH_<8hex>` |
| method `f` of struct `X` | `METH_f_OFSTRUCT_X_HASH_<8hex>` |
| namespace `n` | `NS_n` |
| the entry-point function `main` | `FUNC_main_HASH_<8hex>` like every other function (called by the template wrapper) |

So a declaration `X s;` is emitted as `STRUCT_X VAR_s;`.

Because of the prefixes, a variable, function, and struct may share one source name without colliding in the generated C++.

Each function and method name carries an 8-hex-digit suffix: the FNV-1a 32-bit hash of its canonical signature key. The key spells the declared parameter types in order (including `self` for methods), then the return type, each exactly as written in source (`int32_t`, `uint64_t`, `void`, `array<T>`, `array<T,N>` with canonical folded decimal size and no space, `ptr<T>`, qualified struct names such as `a::b::X`), joined as `fn(P1,P2,...)->R`. For example `func f(int32_t x) -> int32_t` hashes the key `fn(int32_t)->int32_t`. Overloads of one name therefore emit distinct C++ names without relying on g++ overload resolution.

Method names mangle with both the method and the owning struct (`METH_f_OFSTRUCT_X_HASH_<8hex>`), so methods on different structs never collide; `self` emits as an ordinary `VAR_self` parameter of type `STRUCT_X*`.

Namespaces are emitted as C++ namespaces with the `NS_` prefix, so a Franko reference `n::f` becomes e.g. `NS_n::FUNC_f_HASH_<8hex>`. Unqualified references inside a namespace stay unqualified, and global-namespace symbols referenced from inside a namespace are rooted with `::`.

When a variable or function shares its name with a declared struct, the compiler emits a **warning** during semantic analysis (the program still compiles):

```text
==== Warnings ====
  - Variable 'X' shares its name with struct 'X'; generated C++ uses VAR_X for the variable and STRUCT_X for the struct type
```

Struct definitions are emitted after a forward-declaration block (`struct STRUCT_X;` for every declared struct), so `ptr<X>` fields and pointer references may name structs declared later in source order.

## 31.9 Entry-Point Wrapper and Command-Line Arguments

The generated C++ entry point is not the Franko `main` itself. The Franko `main` emits its mangled name like every other function, and a small wrapper in the program template owns the real C++ `int main`:

- `ProgramTemplate.cpp` provides `int main()` and calls the no-args Franko `main` (`FUNC_main_HASH_d8625720`, the hash of `fn()->int32_t`).
- `ProgramTemplateWithRawCArgs.cpp` provides `int main(int argc, char** argv)`, converts the raw C arguments into a `Franko_Dynamic_Array<Franko_Dynamic_Array<uint8_t>>`, and calls the args-form Franko `main` (`FUNC_main_HASH_db73581e`, the hash of `fn(view<array<array<uint8_t>>>)->int32_t`).

The compiler selects the template from the declared entry-point shape; an explicit `-t` template overrides the selection.

The argv conversion follows these rules:

- `args.length()` equals `argc`, with `args[0]` holding the program name;
- each argument is its raw bytes with **no** NUL terminator appended (consistent with string literals, which also append no terminator);
- each inner array is default-constructed by the runtime `init` into the uninitialized Franko state (`length()` `0`), so an empty argument simply stays uninitialized;
- after the Franko `main` returns, the outer array's destructor destroys the converted inner arrays and frees both levels; inner and outer arrays the program already released with `uninit()` are skipped safely.
## 31.10 Break / Continue Lowering

`break` and `continue` lower to `goto` statements targeting per-loop labels, so multi-level `break N` works uniformly and the same scheme serves `while` and desugared `for` loops. Each loop receives a unique label id, and labels are only generated for loops that are actually targeted:

- `continue` emits `goto L_cont_<id>;` where `L_cont_<id>: ;` sits at the end of the loop body, immediately before the loop's epilogue (the desugared `for` update). The label therefore runs the epilogue, matching C's `for` semantics.
- `break` emits `goto L_brk_<id>;` where `L_brk_<id>: ;` is placed immediately after the loop, so the epilogue is skipped.
- `break N` targets the label of the Nth enclosing loop; `continue` always targets the innermost loop.

When a `continue` targets a loop, the body is emitted inside an inner block with the label after it:

```cpp
while (cond)
{
    {
        /* body */
    }
    L_cont_0: ;
    /* update */
}
```

The inner block keeps the label out of the scope of the body's declarations: a C++ `goto` may not jump forward past a variable's initialization within the same scope.

Loops with no `break` or `continue` targeting them emit exactly the code they did before the feature existed: no labels, no `goto`, no extra blocks.

---

# 32. Foreign Function Interface

Franko has no general native-call syntax. Operating-system access goes
through FFI intrinsics: functions that are *declared* in Franko and
*defined* in C++ by a bridge file the compiler splices into the
generated program. The currently available FFI module is `franko/os`;
its API is documented separately in `library_docs/os.md`. This chapter
describes the mechanics: how the compiler detects the module, where
the C++ lives, and which mistakes fail loudly instead of miscompiling.

## 32.1 Activation

FFI behavior switches on only when the program imports the module
through its canonical specifier:

```franko
import "franko/os"
```

`import "franko/os.fr"` normalizes to the same path text and activates
the bridge identically. Any other path that happens to reach the same
file (for example a `./` or `../` relative import) keeps it an
ordinary Franko file: the stub bodies compile as written and no bridge
is loaded. See [Source Files](02-source-files.md) for the general
import path rules.

## 32.2 Declaration File

`import/franko/os.fr` declares the module API with stub bodies only.
Every compiler phase between import resolution and code generation
runs unchanged: the declarations parse, desugar, type-check, and pass
legality and memory safety checking exactly like user-written Franko,
and calls resolve through the ordinary overload rules. The stubs
return failure values (`-1` for posix calls, `0` for win32 calls), so
if the bridge ever fails to attach, the program reports failure
instead of silently succeeding.

## 32.3 Bridge File

The real definitions live in `import/franko/os.ffi.cpp`, next to the
module. A `.cpp` file can sit there safely because imports only allow
`.fr` files: the bridge can never be picked up as a module itself.

The file is split into marker-delimited splice units:

```text
// @@franko-ffi:common               platform includes (windows.h / unistd.h)
// @@franko-ffi:posix.read            os::posix::read definition
// @@franko-ffi:posix.write           os::posix::write definition
// @@franko-ffi:win32.GetStdHandle    os::win32::GetStdHandle definition
// @@franko-ffi:win32.ReadFile        os::win32::ReadFile definition
// @@franko-ffi:win32.WriteFile       os::win32::WriteFile definition
```

Each unit ends with `// @@franko-ffi:end`. Every intrinsic block
defines its function under the exact mangled C++ name the backend
emits for the Franko declaration (see [Runtime and Backend
Notes](31-runtime-and-backend-notes.md) for the mangling rule): the
same trick the program templates use to call the mangled `main`.

```text
FUNC_read_HASH_be149563            os::posix::read
FUNC_write_HASH_731d748e           os::posix::write
FUNC_GetStdHandle_HASH_493245c2    os::win32::GetStdHandle
FUNC_ReadFile_HASH_50f286c1        os::win32::ReadFile
FUNC_WriteFile_HASH_c811d200       os::win32::WriteFile
```

(The hashes move whenever the `os.fr` signatures change: they are the
FNV-1a hash of the canonical signature key, which includes the
parameter pointer permissions — `read` fills an `mview<array<byte>>`
while `write` only reads a `view<array<byte>>`.)

Signatures must match the prototypes codegen emits for the `os.fr`
declarations, including the `NS_os::NS_posix` / `NS_os::NS_win32`
namespace nesting. The bridge is spliced after the program template's
`#include "FrankoRuntime.hpp"`, so `Franko_Dynamic_Array`,
`franko_panic`, and the fixed-width integer types are available.

## 32.4 Splicing

Codegen emits prototypes for every intrinsic (they act as forward
declarations) but never emits the stub bodies; the bridge provides the
only definitions. After emission, the compiler prepends the `common`
block plus one block per intrinsic the program actually calls.
Merely importing `franko/os` without calling anything adds zero C++
to the output, and an unused intrinsic leaves only its harmless
prototype behind.

## 32.5 Verification

Three mistakes fail at Franko-compile time instead of C++ link time:

- a missing bridge file (`os.ffi.cpp` next to the resolved `os.fr`)
  is an import-time error naming the expected path;
- every intrinsic declared in the program must have its mangled
  definition present in `ffi.cpp`, checked the same way the driver
  checks the program template's entry-point call. If `os.fr`
  signatures change without updating the bridge, compilation stops
  here, naming the drifted symbol;
- a known intrinsic name with anything but the exact intrinsic
  signature is an `os.fr`/`ffi.cpp` drift error, and any other
  function declared in `os::posix` or `os::win32` is an unsupported-
  function error. Stubs in those namespaces must never compile
  silently.

## 32.6 Runtime Contract

The bridge checks `count` against the buffer's array length before
every transfer: `count` beyond the end aborts via `franko_panic`,
consistent with Franko's other bounds checks, so the FFI cannot
corrupt memory. A null or uninitialized buffer returns the stub
failure value instead of crashing. The win32 blocks compile to a
`#error` off Windows, so a win32 call built for the wrong target
fails with a clear message at the C++ stage.

## 32.7 Namespace Ownership

The ownership rule in 32.5 applies only while the module is active.
Without `import "franko/os"`, `os::posix` and `os::win32` are
ordinary namespaces anyone may extend; the compiler owns them only
for programs that import the module through its canonical specifier.

---

# 33. Examples

Most executable examples should be placed inside a function body, typically `main`, because global scope allows only declarations.

## 33.1 Basic Integers

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

## 33.2 Static Array Initializer Assignment

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

## 33.3 Static Partial Initializer Assignment

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs = [1, 2];

    return 0;
}
```

`xs[2]` is untouched.

## 33.4 Dynamic Array Initializer Assignment

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

## 33.5 Runtime Expressions Inside Initializer Lists

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

## 33.6 Nested Static Arrays

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

## 33.7 Nested Dynamic Arrays

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

## 33.8 Declaration Initializer with Static Array

```franko
func main() -> int32_t {
    array<int32_t, 3> xs = [1, 2, 3];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 33.9 Dynamic Array Declaration Initializer Without Initialization

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

## 33.10 Array Initializer Through Pointer

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;
    ptr<array<int32_t, 3>> p;

    p = getptr(xs);

    $(p) = [1, 2, 3];

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
    ptr<array<int32_t, 3>> p;

    p = getptr(xs);

    $(p)[0] = 1;
    $(p)[1] = 2;
    $(p)[2] = 3;

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 33.11 String Literal Bytes

```franko
func main() -> int32_t {
    array<byte> str;     // dynamic array: str(200) and str.uninit() require one
    uint i = 0;

    str(200);
    str.memset(0);
    str.memcpy("Hello,\tWorld!\n\"Franko\"");   // 22 bytes copied in

    while (i < 22) {
        print(str[i]);
        i = i + 1;
    }

    str.uninit();
    return 0;
}
```

A string literal is a `view<array<byte, N>>`; byte buffers receive its
content through `memcpy`. See [String Literals](23-string-literals.md).

## 33.12 Nested Static Array Grid

```franko
func fill(ptr<array<array<byte, 3>, 2>> grid) -> void {
    $(grid).memset(0);
    $(grid)[0][1] = 7;
}

func main() -> int32_t {
    array<array<byte, 3>, 2> grid;

    fill(getptr(grid));

    print(grid[0][1]);
    print(grid[1][2]);

    return 0;
}
```

`array<array<byte, 3>, 2>` is a 2 by 3 grid: `grid[r][c]` indexes `r` along the outer size (`2`) and `c` along the inner size (`3`).

## 33.13 Status-Returning Intrinsics

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

## 33.14 Struct Declaration and Field Access

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

## 33.15 Struct Field Defaults

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

## 33.16 Whole-Struct Assignment

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

## 33.17 Structs Passed by Value and by Pointer

Assignable structs may be passed and returned by value; pointer-only structs must go through `ptr<X>`.

```franko
struct Point {
    int32_t x;
    int32_t y
}

func dist2(Point p) -> int32_t {
    return p.x * p.x + p.y * p.y;
}

func move(ptr<Point> p) -> void {
    $(p).x = $(p).x + 1;
    $(p).y = $(p).y + 1;
}

func main() -> int32_t {
    Point p;

    p.x = 3;
    p.y = 4;

    print(dist2(p));   // 25

    move(getptr(p));

    print(p.x);        // 4
    print(p.y);        // 5

    return 0;
}
```

## 33.18 Structs with Array Fields

An array field makes the struct non-assignable: the struct is pointer-only and its arrays are managed through the usual intrinsics.

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

Dynamic array fields are not part of the struct declaration; allocate them explicitly with `field(n)` as above.

## 33.19 Struct Globals

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

## 33.20 Array `length()`

```franko
func main() -> int32_t {
    array<int32_t> xs;
    xs.init(4);
    print(xs.length());   // 4

    array<int32_t, 3> ys;
    ys = [10, 20, 30];
    print(ys.length());   // 3 (compile-time constant)

    for (uint32_t i = 0; i < ys.length(); i = i + 1) {
        print(ys[i]);     // 10 20 30
    }

    xs.uninit();
    print(xs.length());   // 0
    return 0;
}
```

## 33.21 Inferred Static Array Sizes (`?`)

```franko
func main() -> int32_t {
    array<int32_t, ?> a = [0, 1, 2, 3];
    print(a.length());   // 4

    array<byte, ?> s = [104, 101, 108, 108, 111];
    print(s.length());   // 5

    array<array<int32_t, ?>, ?> m = [[1, 2], [3, 4]];
    print(m.length());      // 2
    print(m[0].length());   // 2

    return 0;
}
```

## 33.22 Command-Line Arguments

```franko
func main(view<array<array<byte>>> args) -> int32_t {
    uint32_t n = $(args).length();
    print(n - 1);   // user argument count (args[0] is the program name)

    uint32_t i = 1;
    while (i < n) {
        print($(args)[i].length());   // byte length of argument i
        i = i + 1;
    }

    return 0;
}
```

Running the binary with two arguments prints `2`, then each argument's length. Each argument arrives as its raw bytes with no NUL terminator, and the args parameter needs no null guard: the template wrapper always passes a live pointer (see [Runtime and Backend Notes](31-runtime-and-backend-notes.md)).
