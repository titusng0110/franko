# 1. Overview

Franko is a small statically typed language with:

- fixed-width signed and unsigned integer types;
- structs with named fields (`struct X { int32 n }`);
- tuple types with optional element names (`(int32 val, int32 err)`);
- struct member access using `obj.field` (see [Structs](26-structs.md));
- tuple element access using `t.element` or `t._0` (see [Tuples](28-tuples.md));
- dynamic arrays;
- static arrays;
- typed pointers using `ptr<T>`, `mview<T>`, and `view<T>`;
- explicit pointer operations:
  - `getptr(...)`, `getmview(...)`, and `getview(...)`;
  - `$(...)`;
- user-defined functions;
- function overloading;
- function calls inside function bodies;
- struct methods with explicit `self` receivers (`ptr<X> self`, `mview<X> self`, `view<X> self`, see [Methods](27-methods.md));
- `void` and non-`void` return types;
- global variable declarations;
- global function declarations;
- `const` storage on locals, parameters, and globals for value-assignable
  types (see [Declarations](18-declarations.md));
- assignment;
- blocks;
- `if`;
- `while`;
- `for`;
- `break` and `continue` (with an optional `break N` level, see [Statements](17-statements.md));
- `return`;
- string literals as read-only `view<[byte, N]>` values;
- array intrinsics:
  - `arr.init(size)`;
  - `arr.init_zero(size)`;
  - `arr.resize(size)`;
  - `arr.uninit()`;
  - `arr.memset(value)` and `arr.memset(value, start, count)`;
  - `arr.memcpy(source)` and `arr.memcpy(source, dst_start, src_start, count)`;
  - `arr.memmove(dst_start, src_start, count)`;
  - `arr.memcmp(source)` and `arr.memcmp(source, dst_start, src_start, count)`;
  - `arr.length()`;
  - byte-array searches: `arr.memchr(...)`, `arr.memmem(...)`;
  - byte-array endian accessors: `arr.read_uint16_LE(offset)` … `arr.write_int64_BE(offset, value)`;
  - byte-array bit intrinsics: `read_bit`, `write_bit`, `flip_bit`,
    `bitwise_*_mask`, `shift_bits_*`, `count_set_bits`
    (see [Array Intrinsics](22-array-intrinsics.md));
- array literals as `view<[T, N]>` values over immutable constant
  storage (element type inferred, see [Array Literals](21-array-literals.md));
- struct and tuple aggregate literals, plus local field-wise declaration
  initializers (see [Aggregate Literals](29-aggregate-literals.md));
- struct operator overloading for comparisons and assignments through
  reserved `OPERATOR_*` methods (see [Operators](13-operators.md#1310-struct-operator-overloading));
- enums as nominal types backed by primitive integers, with
  `==`, `!=`, ordering, assignment, `cast`/`unsafe_cast` conversions,
  and `#is_enum` (see [Enums](30-enums.md));
- compile-time reflection:
  - `#if` / `#else` compile-time conditionals in statements and at
    global, namespace, and struct declaration level;
  - target constants `#IS_LINUX`, `#IS_WINDOWS`, `#IS_MACOS`,
    `#IS_X86_64`, and `#IS_ARM64`;
  - type predicates such as `#has_default_value`, `#is_memcpy_safe`,
    `#is_same_type`, and `#is_enum`, accepting either a type or a value;
  - canonical type spelling with `#what_type`;
  - compile-time strings with `#strcat`, `#strcmp`, and `#panic`
    (see [Compile-Time Reflection](34-compile-time-reflection.md));
- imports of other source files (see [Source Files](02-source-files.md));
- namespaces, including nested namespaces, with `::`-qualified references (see [Program Structure](03-program-structure.md));
- FFI modules described by sidecar manifests and C++ bridge blocks (see
  [Foreign Function Interface](33-foreign-function-interface.md));
- standard modules including `franko/error`, `franko/io`, `franko/os`,
  `franko/pool`, `franko/limit`, and `franko/string`;
- compiler checks for pointer null state, dangling frame pointers, dynamic
  array lifecycle, unused pointer permissions, and private identifier access
  (see [Memory Safety Checking](31-memory-safety-checking.md) and
  [Naming Conventions](36-naming-conventions.md)).

Franko currently does **not** implement:

- nested functions;
- first-class function values;
- anonymous functions or lambdas;
- function pointers;
- general member calls beyond struct methods and the built-in array intrinsics;
- generics;
- floating-point types;
- a distinct boolean type;
- a built-in string type (strings are byte-array views; the standard
  `franko/string` module provides the `std::String` struct, see
  [String Literals](23-string-literals.md));
- implicit numeric conversions except for fluid integer constants (explicit width changes use `cast<T>(expr)` for widening and `unsafe_cast<T>(expr)` for checked conversions);
- C-style cast syntax;
- a ternary conditional operator;
- `switch` and `do`/`do-while`;
- multiple declarators in one declaration (`int a, b;`);
- tuple destructuring, tuple methods, tuple operators, and tuple casts (see [Tuples](28-tuples.md#2810-restrictions)).

The repository also includes a VS Code extension with syntax highlighting,
bracket matching, comment toggling, and snippets for `.fr` and `.franko`
files (see [`editors/vscode`](../editors/vscode/README.md)).

Build instructions and compiler flags are in the repository
[README](../README.md).

---

# 2. Source Files

Franko source files commonly use the `.fr` extension.

Example:

```franko
func main() -> int32 {
    int32 x;
    x = 1;

    print(x);

    return 0;
}
```

## Statement Separators

Statements are terminated by a semicolon (`;`) or by one or more newlines. Both forms can be mixed freely, and consecutive separators are allowed.

Example (newline-separated, no semicolons):

```franko
func main() -> int32 {
    int32 x
    x = 1
    print(x)
    return 0
}
```

Newlines inside parentheses `(...)` and brackets `[...]` are ignored, so expressions, argument lists, and array literals may span multiple lines. Newlines inside aggregate literal braces `{...}` are list whitespace: fields may be spread across lines. Newlines inside ordinary statement braces remain statement separators.

## Comments

Line comments start with `//` and run to the end of the line:

```franko
// a whole-line comment
int32 x  // a comment may follow code on the same line
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

The compiler has a single import search directory: by default the
compiler's own `import/` directory. The bundled `compile.py` always uses
that directory, which is where the standard modules live.

A **prefix-free** path (one that does not start with `./`, `../`, or `/`)
is looked up in two places, in order:

1. the search directory: `<search-dir>/<path>.fr`
2. the directory of the file that contains the import statement

The first location that holds a regular file wins, so a module supplied
through the search directory shadows a same-named file next to the
importing source file. A missing search directory is not an error;
resolution simply falls through to the importing file's directory.

Paths with an explicit `./`, `../`, or `/` prefix skip the search
directory and resolve relative to the importing file (or as an absolute
path).

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

### Standard Library Import Paths

The compiler's bundled standard library lives under its own
`./import/franko/` directory. The import prefix `franko/` is reserved for
those standard modules:

```franko
import "franko/io"
import "franko/os"
import "franko/pool"
```

User code must not add files under the compiler's `import/franko/` directory
or use the `franko/` prefix for user modules. Use a project-relative path or
your own search-directory path instead, such as `import "./lib/my_module"` or
`import "myproject/my_module"` (see [Naming Conventions](36-naming-conventions.md)).

### How Imports Resolve

An import is resolved at compile time:

- the referenced file is read like any other source file;
- all of its top-level items (structs, global variables, functions) become part of the same program as the importing file's items;
- imports are processed in declaration order, and an import of an already-processed file is skipped silently, so repeated and cyclic imports are safe (each file is processed exactly once);
- an imported file's own imports are resolved relative to that file's directory (after first consulting the search directory).

Namespaces still separate symbols by path; duplicate declarations that end up in the same namespace are reported like duplicates within one file.

### FFI Modules

An imported module may ship a sibling `<module>.module.json` descriptor that
marks functions as implemented by a C++ bridge. The bundled `franko/os`
module is the standard example. See
[Foreign Function Interface](33-foreign-function-interface.md) for the
descriptor format, bridge block naming, and examples. A module without a
descriptor is just an ordinary Franko module.

---

# 3. Program Structure

A Franko program is a sequence of **top-level items**.

Current valid top-level items are:

- struct declarations;
- global variable declarations;
- function declarations;
- import directives;
- namespace declarations;
- `#if` declaration conditionals that select among the above (except
  imports).

Struct declarations are described in full in [Structs](26-structs.md). Import directives are described in full in [Source Files](02-source-files.md).

## Namespaces

A namespace groups struct, global variable, and function declarations under a qualified name:

```franko
namespace util {
    struct Point {
        int32 x = 1
        int32 y = 2
    }

    int32 offset = 100

    func double_it(int32 v) -> int32 {
        return v + v + offset
    }
}
```

A namespace body may contain struct declarations, global variable declarations, function declarations, nested namespace declarations, and `#if` declaration conditionals. Imports and executable statements are not allowed inside a namespace body.

Nested namespaces may be written in two equivalent forms:

```franko
namespace a::b {
    int32 v = 1
}
```

```franko
namespace a {
    namespace b {
        int32 v = 1
    }
}
```

Both forms declare the same namespace, whose full path is `a::b`. A path header (containing `::`) is only valid at the top level: a namespace declared inside a namespace body must use a single name, and its full path is the enclosing path plus that name.

The same namespace may be declared more than once, in the same file or across imported files, in either form; the declarations merge into one namespace. Duplicate symbols inside the merged namespace are rejected exactly like global duplicates.

By convention namespace names are short, lowercase, and underscore-free; a two-word name is written as one word (for example `bignum`). Names starting with `__` mark functions and structs that are internal to their namespace. See [Naming Conventions](36-naming-conventions.md).

### Qualified References

Namespace members are referenced with `::`:

```franko
util::Point p
util::offset = 5
a::b::helper(x)
```

A qualified name always resolves strictly inside the named namespace path, starting from the top level; there is no fallback to the global namespace, no search of enclosing namespaces, and no relative resolution through the current namespace. `a::w` does not find a symbol declared only in `a::b`, and `a::b::x` requires both `a` and `a::b` to be declared namespaces.

### Unqualified References

Inside a namespace, an unqualified name resolves in that namespace first, then falls back to the global namespace. Enclosing namespaces are not searched: code inside `a::b` does not see unqualified names declared in `a` unless they are also visible globally. Outside any namespace, only the global namespace is visible.

### Entry Point

The entry-point function `main` must be declared in the global namespace. A `main` inside a namespace is a compile error.

`main` must be declared in one of two shapes, and it cannot be overloaded:

```franko
func main() -> int32 {
    return 0;
}

func main(view<[[byte]]> args) -> int32 {
    return 0;
}
```

Any other shape (parameters of another type, a different return type) is a compile error.

In the args form, the parameter name is free (`args`, `notargs`, ...). `args[0]` is the program name, and each argument is its raw bytes with no NUL terminator.

Executable statements are **not** currently allowed in global scope.

Invalid global-scope items include:

- assignments;
- function calls;
- `if`;
- `while`;
- `for`;
- `return`;
- blocks;
- array intrinsic calls;
- array initializer assignments.
- expression statements (bare expressions such as `1 + 2`);

Note that some invalid global-scope forms are only invalid in their executable form: a non-constant declaration initializer performs an assignment, an expression-statement call, or indexed element assignments, all of which are executable statements.

Execution should be rooted in a user-defined `main` function.

Example:

```franko
struct Point {
    int32 x
    int32 y = 0
}

int32 count = 0

func main() -> int32 {
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
func f(int32 x) -> int32 {
    int32 x;   // invalid: duplicate declaration of 'x'
    return x;
}
```

Only nested `{ ... }` blocks inside the body create new scopes.

## 4.7 Namespace Lookup

Namespaces add two lookup rules on top of lexical scope; see [Program Structure](03-program-structure.md) for the full reference.

Unqualified names used inside a namespace resolve in that namespace first, then fall back to the global namespace. Enclosing namespaces are never searched.

Qualified names (`a::b::x`) resolve strictly inside the named namespace path from the top level, with no fallback. The first segment must name a top-level namespace.

## 4.8 Identifier Visibility

Franko uses leading underscores to mark non-public declarations:

- a struct field or method whose name starts with one or more underscores
  (`_name`, `__name`, ...) is **private to its struct**;
- a free function, struct, or enum whose name starts with two or more underscores
  (`__name`, `___name`, ...) is **internal to its namespace**.

Access is allowed inside the declaration's own scope:

- a private field or method may be used by methods declared in the same
  struct; access from any free function or from a method of another struct
  warns;
- a namespace-internal function, struct, or enum may be referenced from its declaring
  namespace or from a namespace nested under it; access from any other
  namespace warns. Global-namespace internal names are accessible only from
  global-scope code.

These are compiler warnings, not errors: the program still compiles and runs.
The complete naming style for structs, functions, methods, variables, fields,
namespaces, constants, and standard-library imports is in
[Naming Conventions](36-naming-conventions.md).

---

# 5. Primitive Integer Types

Franko supports fixed-width signed and unsigned integer types.

| Type | Meaning | Range |
|---|---|---|
| `int8` | signed 8-bit integer | `-128` to `127` |
| `int16` | signed 16-bit integer | `-32768` to `32767` |
| `int32` | signed 32-bit integer | `-2147483648` to `2147483647` |
| `int64` | signed 64-bit integer | `-9223372036854775808` to `9223372036854775807` |
| `uint8` | unsigned 8-bit integer | `0` to `255` |
| `uint16` | unsigned 16-bit integer | `0` to `65535` |
| `uint32` | unsigned 32-bit integer | `0` to `4294967295` |
| `uint64` | unsigned 64-bit integer | `0` to `18446744073709551615` |

## 5.1 Integer Type Aliases

| Alias | Canonical type |
|---|---|
| `int` | `int32` |
| `uint` | `uint32` |
| `byte` | `uint8` |

No other aliases (such as `char` or `bool`) are currently defined.

## 5.2 Boolean-Like Values

Franko currently does not have a separate `bool` type.

Boolean-like results from comparison and logical operators use `uint8`, with:

```text
0 = false
1 = true
```

---

# 6. Integer Literals

Integer literals are evaluated with arbitrary precision before contextual type checking; no intermediate literal arithmetic overflows.

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

There is no octal literal syntax. Write decimal literals without
leading zeros: `075` is not an octal escape hatch and its meaning is not
portable.

---

# 7. Fluid Integer Constants

Integer literals and folded constant integer expressions are **fluid**.

This means their initial type is not always binding. Instead, when a constant is used in a typed context, the compiler checks whether the constant value fits that context.

Example:

```franko
func main() -> int32 {
    uint8 x;

    x = 255; // valid
    x = 256; // invalid: 256 does not fit uint8

    return 0;
}
```

Fluid constants also participate in function overload resolution. A folded integer constant may match a primitive integer parameter if its arbitrary-precision value fits that parameter type.

When a constant fits several overloads, the call is resolved by a constant-fit preference, not rejected: the overload whose parameter type equals the constant's own type wins when the value fits there; otherwise the narrowest fitting type wins, with signed preferred over unsigned at equal width. For example, `print(1)` selects `print(int32)` (a literal's own type), `print(5000000000)` selects `print(int64)`, and `print(100)` selects `print(int16)` over `print(uint16)` when `int32` is not among the overloads. A call is ambiguous only if several overloads tie on both permission-downgrade and constant-fit costs.

---

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

---

# 9. Types

Franko has the following semantic type categories:

```text
primitive integers
structs
tuples
enums
dynamic arrays
static arrays
typed pointers (ptr / mview / view)
function return-only void
```

There is no separate boolean type. Boolean-like results are represented as
`uint8`.

Each category has its own detailed chapter:

| Type category | Chapter |
|---|---|
| Primitive integers | [Primitive Integer Types](05-primitive-integer-types.md) |
| Static arrays and dynamic arrays | [Arrays](20-arrays.md) |
| Array and string literal values | [Array Literals](21-array-literals.md), [String Literals](23-string-literals.md) |
| Pointers (`ptr`, `mview`, `view`) | [Pointers](25-pointers.md) |
| Structs | [Structs](26-structs.md) |
| Tuples | [Tuples](28-tuples.md) |
| Enums | [Enums](30-enums.md) |
| `void` | [Functions](19-functions.md) |

Structs are nominal types: two distinct struct declarations are distinct
types even when their field layouts are identical. See
[Type Equality](10-type-equality.md), [Declarations](18-declarations.md), and
[Structs](26-structs.md).

Tuples are structural aggregate types: their identity depends on the ordered
element types, not on the element names written at a declaration. See
[Tuples](28-tuples.md) and [Type Equality](10-type-equality.md).

Enums are nominal types backed by one of the eight primitive integer
types: two distinct enum declarations are distinct types even when their
backing types and variants match. See [Enums](30-enums.md) and
[Type Equality](10-type-equality.md).

`void` is not an ordinary value type. It is valid only where the language
explicitly permits it, currently as a function return type.

Pointers come in three permission levels — `ptr<T>` (owner), `mview<T>`
(mutable view), `view<T>` (read-only view) — one capability model for all of
them. See [Pointers](25-pointers.md).

A static array size may be written as the inferred-size marker `?` (for
example `view<[int32, ?]>`) only in a local `view` declaration with an
array-literal initializer. The size is inferred at compile time, so every type
carries a concrete size.

---

# 10. Type Equality

Franko uses exact, structural semantic type equality in many places. Type equality is implemented once (in the semantic type classes) and used consistently by overload resolution, assignment checking, return checking, and diagnostics.

## 10.1 Primitive Equality

Primitive types are equal only if they have the same kind.

```text
uint8  != uint16
int32  != uint32
int32  == int32
```

The aliases `int`, `uint`, and `byte` canonicalize to `int32`, `uint32`, and `uint8` respectively.

## 10.2 Dynamic Array Equality

Dynamic arrays are equal if their element types are equal.

```text
[int32] == [int32]
[int32] != [uint8]
```

## 10.3 Static Array Equality

Static arrays are equal if:

- element types are equal;
- sizes are numerically equal after unfolding the constant size expression.

The size comparison is numeric, not textual:

```text
[int32, 10]     == [int32, 0xA]
[int32, 5 + 5]  == [int32, 10]
[int32, 10]     != [int32, 20]
```

## 10.4 Pointer Equality

Pointer types are equal if their referenced types are equal **and** their
permissions match: `ptr<T>`, `mview<T>`, and `view<T>` are three distinct
types.

```text
ptr<[byte]>     == ptr<[byte]>
ptr<[byte]>     != mview<[byte]>
mview<[byte]>   != view<[byte]>
view<[byte, 3]> != view<[byte, 5]>
```

The pointer conversions — permission downgrades (`ptr<T>` to
`mview<T>`/`view<T>`, `mview<T>` to `view<T>`) and the static-to-dynamic
array pointer view decay (`mview`/`view` of `[T, N]` to `mview`/
`view` of `[T]`, optionally combined with a downgrade from a `ptr`
source) — are the only implicit conversions the language allows for
nonconstant values; they do not make the types equal (see [Pointers](25-pointers.md)).

## 10.5 Function Signature Equality

Function signatures are equal if they have:

- the same function identifier;
- the same ordered list of parameter types.

Return type and parameter names are not part of function signature
equality. For methods, the `self` parameter type is not part of signature
equality either (see [Methods](27-methods.md)).

## 10.6 Struct Equality

Struct types are nominal: two struct declarations are distinct types even when their field layouts are identical.

```text
struct X { int32 n }
struct Y { int32 n }

X != Y
X == X
```

Duplicate struct names are rejected, so each struct name denotes exactly one declared type. See [Structs](26-structs.md) for the complete struct reference.

Tuple equality is covered in [Tuples](28-tuples.md#283-type-identity).

## 10.7 Enum Equality

Enum types are nominal: two enum declarations are distinct types even
when their backing types and variants are identical.

```text
enum<int32> X { a, b }
enum<int32> Y { a, b }

X != Y
X == X
```

Duplicate enum names are rejected, so each enum name denotes exactly
one declared type. An enum name shares one type-name namespace with
struct names: an enum and a struct may not use the same name in the
same scope. See [Enums](30-enums.md) for the complete enum reference.

---

# 11. Declaration Type Validity

A declared variable type is valid if it is one of:

- primitive integer type;
- a declared struct type;
- a declared enum type;
- a tuple of valid non-`void` element types (at least two elements);
- dynamic array of a valid non-`void` type;
- static array of a valid non-`void` type with valid size;
- pointer of a valid non-`void` type.

Valid:

```franko
struct X { int32 n }

enum<uint8> Status { spawning, alive }

int32 x;
X s;
Status st;
(int32, int32) pair;
(int32 val, X) named;
[X] xs;
[uint8, 64] bytes;
[[byte, 20], 12] grid;
ptr<int32> p;
ptr<[int32]> ap;
ptr<(int32, int32)> tp;
```

Invalid:

```franko
void x;
[void] xs;
ptr<void> p;
(int32, void) pair;      // invalid: void cannot be a tuple element
struct S { void field }   // invalid: void cannot be a struct field
T s;                       // invalid when no struct named T is declared
```

Static arrays nest: every level of `[[byte, 20], 12]` must satisfy the static array size rules (see [Arrays](20-arrays.md)).

A function return type has separate rules. It may be:

- `void`;
- a primitive integer type;
- a declared enum type;
- a pointer type (`ptr<T>`, `mview<T>`, or `view<T>`); or
- a struct whose fields are all assignable value types, recursively; or
- a tuple whose elements are all assignable value types, recursively.

Array return types are invalid. A tuple containing an array or another non-assignable element cannot be returned by value; return a pointer to it instead.

---

# 12. Casts

Franko has no implicit numeric conversions: a value of one integer
type never silently becomes another (see [Assignment](16-assignment.md)).
Moving a value across integer widths is always spelled out with one of
two explicit conversion intrinsics:

- `cast<T>(expr)` — value-preserving **widening** and pointer permission
  **downgrades**;
- `unsafe_cast<T>(expr)` — any integer conversion that can lose data
  (runtime-checked).

```franko
uint8 a = 200;
uint16 b = cast<uint16>(a);        // widening: no runtime cost

int32 big = 300;
uint8 small = unsafe_cast<uint8>(big);   // checked: throws at run time
```

Like `$(...)` and `getptr`, both are compiler intrinsics, not function
calls: they cannot be overloaded, shadowed, or passed around.

## 12.1 Cast Syntax

```franko
cast<uint16>(a)
cast<view<[byte]>>(p)
unsafe_cast<int32>(big)
```

`cast<T>` accepts a full type as its target: one of the eight primitive
integer types, an enum type, or a pointer type (`ptr<T>`, `mview<T>`,
`view<T>`). Arrays, structs, tuples, and `void` are rejected at check
time. The operand must match the target category: an integer expression
for integer targets, an enum value for enum targets, a pointer or
`null` for pointer targets.

`unsafe_cast<T>` accepts a full type as its target: one of the eight
primitive integer types (`int8` … `uint64`, including the `int` /
`uint` / `byte` aliases) or an enum type. Array, struct, tuple,
pointer, and `void` targets are rejected at check time. The operand
must be an integer expression, except that an integer-to-enum
conversion requires `unsafe_cast` and an enum source converts to an
integer target.

A cast is never an lvalue:

```franko
cast<uint16>(a) = 5;   // invalid
```

Casts compose freely with other expressions:

```franko
uint8 cmp = 1 < 2;
int32 wide = cast<int32>(cmp);
print(unsafe_cast<uint8>(wide));
```

## 12.2 Static (Value-Preserving) Casts

When every value of the source type is representable in the target
type, the cast is statically safe and has no runtime cost:

- strictly wider, same signedness:
  `uint8 -> uint16 -> uint32 -> uint64` and
  `int8 -> int16 -> int32 -> int64`;
- unsigned to signed of strictly greater width:
  `uint8 -> int16`, `uint16 -> int32`, `uint32 -> int64`.

These are the conversions `cast<T>` performs. Casting to the type the
expression already has is allowed and compiles to the value unchanged.

## 12.3 Checked Casts (`unsafe_cast<T>`)

Every conversion that can lose data — narrowing, any signed-to-unsigned
conversion, `uint64 -> int64`, and same-width signedness changes —
must be written `unsafe_cast<T>(expr)`. It performs a runtime range
check against the target type's limits and throws on overflow, so a bad
conversion aborts instead of wrapping or truncating.

```franko
int32 big = 300;
uint8 small = unsafe_cast<uint8>(big);   // throws: 300 does not fit uint8
```

A checked cast that fits succeeds normally:

```franko
uint32 w = 70000;
int32 s = unsafe_cast<int32>(w);   // valid: 70000 fits int32
print(s);
```

Writing `cast<T>` where the conversion can lose data is a compile error
that points at `unsafe_cast<T>`: the two spellings keep the dangerous
conversions visibly marked at every call site.

## 12.4 Enum Casts

Enums convert to and from raw integers with the same strict split.
See [Enums](30-enums.md) for the complete rules.

| Source | Target | `cast` | `unsafe_cast` |
|---|---|---|---|
| enum | exact backing integer | allowed | allowed |
| enum | strictly wider compatible integer | allowed | allowed |
| enum | narrower / signedness-incompatible integer | error, suggest `unsafe_cast` | allowed, runtime-checked |
| enum | another enum | error | error |
| integer | enum | error, suggest `unsafe_cast` | allowed, runtime-checked |
| integer | integer | widening rule | checked conversion |
| pointer | pointer | permission rules | error |
| anything else | anything else | rejected | rejected |

Enum-related conversions are always runtime-checked, even for constant
values known to be invalid: `unsafe_cast<BigState>(999)` compiles and
aborts at runtime instead of failing at compile time.

## 12.5 Constant Casts

A conversion of a folded constant expression is validated at compile
time: the value must fit the target type or compilation fails. There is
no wraparound, at compile time or at run time.

```franko
uint8 ok = unsafe_cast<uint8>(200);    // valid
uint8 bad = unsafe_cast<uint8>(300);   // invalid: 300 does not fit uint8
```

## 12.6 Pointer Permission Casts

`cast<T>` with a pointer target is an explicit **permission downgrade**
(see [Pointers](25-pointers.md)): the referenced type must stay
identical and the permission may only weaken
(`ptr -> mview -> view`).

```franko
[byte] buf;
buf.init(16);
ptr<[byte]> p = getptr(buf);
view<[byte]> v = cast<view<[byte]>>(p);   // explicit downgrade
mview<[byte]> m = cast<mview<[byte]>>(p);
view<[byte]> v2 = cast<view<[byte]>>(m);
```

Explicit downgrades are never required — the same conversions happen
implicitly wherever a lower permission is expected — but they can be
written for clarity. Casting *upwards* (`view` to `mview`/`ptr`) or
across different pointee types is invalid:

```franko
ptr<[byte]> q = cast<ptr<[byte]>>(v);   // invalid: raises permissions
```

The same `cast` syntax spells the static-to-dynamic array pointer view
decay: `mview<[T, N]>`/`view<[T, N]>` (and downgrade
combinations from a `ptr` source) cast to `mview<[T]>`/
`view<[T]>`, outermost dimension only
(see [Pointers](25-pointers.md)):

```franko
[int32, 4] xs;
mview<[int32, 4]> m = getmview(xs);
mview<[int32]> d = cast<mview<[int32]>>(m);
```

Decaying to `ptr<[T]>` is invalid: the result would claim ownership
of storage the static array owns.

The decay does not copy elements or allocate storage: the converted
view refers to the same array and keeps the static array's length. No
new array object is created.

Raw integers can never be converted to pointers.

## 12.7 What Casts Do Not Cover

- A tuple type can be written as a cast target because `cast<T>` accepts a full type, but tuple casts are rejected: `cast<(int32, int32)>(x)` is a compile error. Tuples have no conversion operations.
- Casts do not appear in constant-expression slots (static array
  sizes, global initializers, struct field defaults): those contexts
  accept only plain integer literals and operators.
- Calls, returns, assignments, and operators still require exact
  type equality on their operands, with the single exception of the
  implicit pointer conversions (permission downgrades and the
  static-to-dynamic array pointer view decay). `cast<T>` produces a value of
  exactly `T`, which is what makes those positions accept it.
  A value that must cross widths on the way into a call is converted
  into a temporary first:

```franko
func f(int64 x) -> int64 {
    return x;
}

func main() -> int32 {
    int32 v = 5;
    int64 r = f(cast<int64>(v));
    print(r);
    return 0;
}
```

---

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

---

# 14. Conditions

`if`, `while`, and `for` conditions must be integer expressions. A
`for` condition follows the same rule as a `while` condition (the header
condition is required). See [Statements](17-statements.md).

Valid:

```franko
int32 x;

if (x) {
    print(x);
}
```

Invalid:

```franko
[int32] arr;

if (arr) {
    print(1);
}
```

Invalid:

```franko
ptr<int32> p;

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

## Static Conditions

`#if` uses a different rule: its condition must be a compile-time
constant integer, and only the selected branch is used. See
[Compile-Time Reflection](34-compile-time-reflection.md).

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
| tuple element access whose base is an lvalue (`t.val`, `t._0`) | yes |
| integer literal | no |
| arithmetic expression | no |
| comparison expression | no |
| aggregate literal | no |
| function call result | no |
| `getptr(...)` result | no |

Const storage is storage-backed but **not a mutable assignment target**. If
the root of an access path is a const variable, `x = ...`, `x += ...`,
`x.field = ...`, and `x[i] = ...` are rejected. A `$(p)` boundary starts a new
storage region: `$(p) = ...` and `$(p).field = ...` are allowed even when the
pointer variable `p` itself is const, because the write targets the pointee,
not the const pointer slot. `getptr`/`getmview` over const storage are
rejected; `getview` is allowed.

Struct field lvalue-ness follows the base expression: `s.field` is an lvalue when `s` is a variable, a `$(...)`, a nested member access, or an array element of a storage-backed array (static or dynamic). Field access on a function call result is not storage-backed.

Tuple element access follows exactly the same rule: `t.val` and `t._0` are lvalues when `t` is storage-backed, and tuple elements can be assigned, passed to `getptr`/`getview`/`getmview`, and nested in longer access chains.

See [Structs](26-structs.md) for the complete struct reference and [Tuples](28-tuples.md) for tuple types.

This distinction matters for:

- assignment targets;
- compound assignment targets and `x++` / `x--` targets;
- `getptr(...)` operands;
- mutating array intrinsic receivers, including a `memcpy` destination.

Read-only sources (`memcpy`/`memcmp`/`memchr`/`memmem` sources) only need
to be pointer expressions; they do not need to be storage-backed
lvalues.

---

# 16. Assignment

Assignment syntax:

```franko
target = value;
target op= value;   // compound: += -= *= /= %= &= |= ^=
```

The left-hand side must be a storage-backed lvalue.

Valid assignment targets include:

- variables;
- array elements;
- dereferenced pointers;
- struct field accesses (`obj.field`).

## 16.0 Const Storage

A const variable cannot be assigned after its declaration:

```franko
const int32 x = 1
x = 2          // error: Cannot assign to const variable 'x'
x += 1         // error
x++            // error: equivalent to x += 1
```

The restriction follows the storage root. `p.field = 1`, `p[i] = 1`, and
`p = v` are all rejected when `p` is const, but `$(p) = v` and
`$(p).field = v` write the pointee and are allowed when `p` is a const
pointer. `getptr(constValue)` and `getmview(constValue)` are rejected;
`getview(constValue)` is allowed.

Const storage must be initialized by its declaration:

```franko
const Point p = Point { x = 10, y = 20 }
const Point q { x = 30 }
const (int32, int32) pair = (int32, int32) { 1, 2 }
```

Plain and field-wise declaration initialization both initialize the const
target; a later assignment is always an error. See
[Declarations](18-declarations.md#188-const-storage).

## 16.1 Primitive Assignment

A primitive integer variable may be assigned:

1. a nonconstant expression of exactly the same primitive type; or
2. a constant expression that fits the target type.

Moving between integer widths is never implicit: use the explicit
`cast<T>(expr)` intrinsic (see [Casts](12-casts.md)).

## 16.2 Pointer Assignment

Pointer assignment accepts an exactly matching pointer type, or one of
the implicit pointer conversions:

- a permission downgrade over an identical referenced type
  (`ptr<T>` to `mview<T>`/`view<T>`, `mview<T>` to `view<T>`);
- the outermost static-to-dynamic array view decay
  (`mview<[T, N]>`/`view<[T, N]>` to `mview<[T]>`/`view<[T]>`, optionally
  combined with a downgrade from `ptr<[T, N]>` to `mview<[T]>`/`view<[T]>`).

Casting upward or across different pointee types is invalid. Raw
integers cannot be converted to pointers. The `null` literal is valid for
any pointer target. See [Pointers](25-pointers.md) and
[Casts](12-casts.md).

## 16.3 Array Assignment

Arrays cannot be directly assigned as values, and an array literal
cannot initialize or assign an array: a literal is a read-only
`view<[T, N]>` value, not an array value.

Invalid:

```franko
[int32] a;
[int32] b;

a = b;              // arrays cannot be directly assigned
a = [1, 2, 3];      // an array literal is a view, not an array value
```

Copy elements instead:

```franko
[int32, 3] xs;
xs.memcpy([1, 2, 3]);        // copies min(3, 3) elements

[int32] ys;
ys.init(3);
ys.memcpy(getptr(xs));       // dynamic target is initialized first
```

See [Array Literals](21-array-literals.md) for literal-source rules.

## 16.4 Struct Assignment

A struct may be assigned as a whole value if and only if all of its fields are assignable, recursively: assignable = primitive | pointer | struct-with-all-assignable-fields. Tuple assignment follows the same recursive rule; see [Tuple Assignment](28-tuples.md#286-assignment).

A struct or tuple value can be constructed directly on the right-hand side with an aggregate literal. The literal creates a temporary value, which is then assigned by the built-in whole-value rules; the constructed type must be value-assignable, so no custom `OPERATOR_MOV` exists for it. See [Aggregate Literals](29-aggregate-literals.md).

Valid:

```franko
struct X {
    int32 n
    ptr<X> next
}

X a;
X b;
a = b;    // whole-value assignment
```

Invalid (struct contains a static array field):

```franko
struct W {
    [int32, 4] xs
}

W a;
W b;
a = b;    // invalid: struct with non-assignable fields is pointer-only
```

Structs with non-assignable fields can still be shared through a pointer
(for example `ptr<X>` with `getptr(s)`, or `mview`/`view` when only
read/write access is needed), and fields are accessed through
`$(p).field`.

An enum is assigned as a whole value: both sides must have the exact
same enum type. An enum cannot be assigned to or from an integer or
from a different enum type; convert explicitly with `cast` or
`unsafe_cast` (see [Enums](30-enums.md)).

## 16.5 Compound Assignment

Eight compound assignment operators exist:

```franko
+= -= *= /= %= &= |= ^=
```

Valid:

```franko
x += 1;
arr[i] -= 2;
obj.n *= 3;
$(p) /= 4;
x %= 7;
flags &= 0xFF;
mask |= 1 << 3;
bits ^= 0x0F;
```

The target follows the same rules as plain assignment: a non-void storage-backed lvalue.

The value must satisfy the rules of the corresponding binary operator (`+ - * / % & | ^`, see [Operators](13-operators.md)) against the target:

- both operands are integers (arrays, pointers, and void are invalid);
- with two nonconstant operands, both sides must have exactly the same integer type;
- a constant right-hand side must fit the target's type;
- a constant zero divisor is rejected for `/=` and `%=`, exactly like `x / 0` and `x % 0`.

`target op= value` means exactly `target = target op value`, except the target expression is evaluated only once:

```franko
arr[f()] += 1;   // f() runs once
```

The target expression is evaluated once; array indices, field chains, and dereferences in the target are never evaluated twice.

Invalid:

```franko
[int32, 3] xs = [1, 2, 3];
xs += 1;         // arrays are not arithmetic values

int32 v = 1;
ptr<int32> p = getptr(v);
p += 1;          // pointers are not arithmetic values

int32 x = 1;
int64 y = 2;
x += y;          // operands must have the same integer type
```

The right-hand side may not be an array literal (`xs += [1, 2]` is rejected: operands must be integers). See [Array Literals](21-array-literals.md).

## 16.6 Increment and Decrement

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
for (int32 i = 0; i < n; i++) {
    print(i)
}
```

Only the postfix statement forms exist. Prefix forms (`++x`) and value uses (`x = y++`, `print(x++)`) are syntax errors: `x++` never produces a value and cannot appear as a call argument. See [Statements](17-statements.md).

See [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md) for field-level assignment targets, and [Structs](26-structs.md) for the full assignability rules.

## 16.7 Operator Assignment

A struct may customize assignment by declaring operator methods:

| Syntax | Method |
|---|---|
| `=` | `OPERATOR_MOV` |
| `+=` | `OPERATOR_ADDMOV` |
| `-=` | `OPERATOR_SUBMOV` |
| `*=` | `OPERATOR_MULMOV` |
| `/=` | `OPERATOR_DIVMOV` |
| `%=` | `OPERATOR_MODMOV` |
| `\|=` | `OPERATOR_ORMOV` |
| `&=` | `OPERATOR_ANDMOV` |
| `^=` | `OPERATOR_XORMOV` |

Each assignment operator method has the shape:

```franko
func OPERATOR_MOV(ptr<X> self, view<T> other) -> void
```

The assignment target must still be a storage-backed lvalue of the struct
type `X`. `T` may be any non-void value type, including a nested view
type, and multiple `view<T>` overloads are allowed.

Plain `=` first tries to resolve an applicable `OPERATOR_MOV` overload.
If none applies, it falls back to the built-in whole-struct assignment
rules described in [Struct Assignment](#164-struct-assignment). Compound
assignment has no built-in struct fallback: if no overload applies, the
assignment is rejected.

A value-assignable struct cannot declare `OPERATOR_MOV`; its whole-value
assignment is always the built-in field copy. A pointer-only struct can opt
into whole-value assignment with `OPERATOR_MOV` even though built-in
assignment refuses it:

```franko
struct Blob {
    [int32, 4] xs

    func OPERATOR_MOV(ptr<Blob> self, view<Blob> other) -> void {
        $(self).xs.memcpy(getview($(other).xs))
        return
    }
}

Blob a
Blob b
b = a          // calls b.OPERATOR_MOV(getview(a))
```

The right-hand side is always borrowed as `view<T>` where `T` is its
static type. An lvalue is passed with `getview`; an rvalue is borrowed for
the duration of the full expression. A view value needs a `view<view<...>>`
parameter:

```franko
OPERATOR_MOV(ptr<X> self, view<[byte]> other)        // x = arr
OPERATOR_MOV(ptr<X> self, view<view<[byte]>> other)  // x = "hello"
```

Integer constants are fluid-fitted to `view<primitive>` parameters and
materialized as a temporary of the referenced primitive type.

The borrowed view is valid only for the duration of the operator call.
An operator method must not store or forward it into caller-owned or
global storage; the compiler rejects that pattern (see
[Memory Safety Checking](31-memory-safety-checking.md#299-dangling-pointer-checking)).
A local `view<T>` copy inside the operator body is allowed because the
local dies before the operator call returns.

See [Operators](13-operators.md#1310-struct-operator-overloading) for the
complete operator overload rules.

---

# 17. Statements

Franko supports the following statements inside function bodies and blocks:

- variable declarations;
- declaration initialization (`T x = expr`, `T x { field = expr }`, and the
  const forms; see [Declarations](18-declarations.md));
- assignments;
- compound assignments (`+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`);
- increment and decrement statements (`x++`, `x--`);
- blocks;
- `if`;
- `#if` / `#else`;
- `while`;
- `for`;
- `return`;
- expression statements;
- function call statements;
- array intrinsic statement forms.

Executable statements are not allowed at global scope.

Assignments, compound assignments, and increment/decrement are statement forms only: `x++` never produces a value, so it cannot appear as a call argument (for example, `print(x++)` does not parse). See [Assignment](16-assignment.md).

## 17.1 Blocks

A block is a sequence of statements enclosed in braces and creates a new lexical scope.

The braces of a function body are an exception: they do not create an additional scope beyond the function's parameter/body scope. See [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md).

## 17.2 If Statements

The condition must be an integer expression.

Braces are optional: the body may be a single statement. A braceless body behaves as if it were wrapped in an implicit block, so declarations inside it are scoped to that block and are not visible after the `if`. See [Shorthands and Equivalences](24-desugaring.md).

## 17.3 While Statements

The condition must be an integer expression.

Braces are optional: the body may be a single statement. A braceless body behaves as if it were wrapped in an implicit block, so declarations inside it are scoped to that block and are not visible after the `while`. See [Shorthands and Equivalences](24-desugaring.md).

## 17.4 For Statements

`for` loops use C-style headers:

```franko
for (init; condition; update) {
    body
}
```

- `init` and `update` are optional header statements: declarations, assignments, compound assignments, increments, and expression statements. `return` is not allowed in either position;
- the condition is required and must be an integer expression.

Prefer `for` for loops with a clear initialization, condition, and update, such as counted iteration; use `while` when the condition is the only loop control.

Valid:

```franko
for (int32 i = 0; i < 5; i = i + 1) {
    print(i)
}

for (int32 i = 0; i < 5; i++) {
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

The loop is equivalent to:

```franko
for (int32 i = 0; i < 5; i = i + 1) {
    print(i)
}
```

which behaves as:

```franko
{
    int32 i;
    i = 0;
    while (i < 5) {
        print(i);
        i = i + 1;
    }
}
```

The wrapping block scopes the loop variable declared by `init`: it is not visible after the loop. The `update` statement runs at the end of every body iteration, including the last; `continue` runs it and `break` skips it. See [Break and Continue](17-statements.md) below.

Braces are optional: the body may be a single statement. A braceless body behaves as if it were wrapped in an implicit block, so declarations inside it are scoped to that block and are not visible after the `for`. See [Shorthands and Equivalences](24-desugaring.md).

## 17.5 Break and Continue

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
for (int32 i = 0; i < 5; i = i + 1) {
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
for (int32 a = 0; a < 3; a = a + 1) {
    for (int32 b = 0; b < 3; b = b + 1) {
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

## 17.6 Return Statements

A bare `return` is valid only in a `void` function.

A `return expr` statement is required for non-`void` functions.

## 17.7 Output (`franko/io`)

Output is a library function, not a statement. Import `franko/io` and call the overloaded `print(...)` functions (see [franko/io](../library_docs/io.md) for the full API):

```franko
import "franko/io"

func main() -> int32 {
    print(42)        // `42` followed by a newline
    print("hi")      // text followed by a newline
    print(1, " ")    // `1` followed by a space (no newline)
    print(2)         // `2` followed by a newline
    print()          // bare newline
    return 0
}
```

A single-argument `print(x)` writes `x` followed by `\n`; a two-argument `print(x, end)` writes `x` followed by `end` verbatim (a null or empty `end` means no suffix and no newline). `print()` writes a bare newline. Overloads exist for all eight integer types, for `view<[uint8]>` text, and for `view<std::String>` from `franko/string`; a null or empty view/string prints nothing. `print_err` mirrors every `print` overload on stderr, with the same suffix and null/empty behavior.

`print` has no overloads for pointers or arrays. Struct printing is limited to `std::String`; print individual elements, fields, or bytes for other structs: `print(xs[0])`, `print(s.x)`. A string literal prints as text (it is a `view<[byte, N]>` value that decays to the `view<[uint8]>` overload); to print a single byte numerically, index it: `print(s[0])`. A bare `print(null)` is ambiguous between the text and `std::String` overloads; use a typed null view when a null text argument is intended.

Integer constants resolve by the overload preference rule: the parameter matching the constant's own type wins when the value fits, otherwise the narrowest fitting type (signed before unsigned at equal width). See [Functions](19-functions.md).

Member-call printing such as `x.print()` is not supported; see [Unsupported or Restricted Features](35-unsupported-or-restricted-features.md).

## 17.8 Expression Statements

Function calls are commonly used as expression statements.

Array intrinsic calls are special expression-statement forms:

```franko
arr.init(10);
arr.memset(0);
arr.memcpy(getptr(other));
arr.uninit();
```

## 17.9 Compile-Time If Statements

`#if` and `#else` are compile-time statement forms:

```franko
#if (constantExpression) {
    ...
} #else {
    ...
}
```

The condition must be a compile-time constant integer. Only the selected
branch is used; the other branch is ignored entirely. Braceless bodies
are allowed, and chaining uses `#else #if`.
A standalone `#else` is a syntax error.
`#if` also works at declaration level: at global scope, inside a
namespace, and inside a struct. Selected declarations and statements are
inserted directly into the enclosing scope, so compile-time branch braces
do not create a scope. See
[Compile-Time Reflection](34-compile-time-reflection.md).

---

# 18. Declarations

A variable declaration introduces a named storage location.

Basic declaration syntax:

```franko
int x;
int32 y;
uint8 b;
uint32 i;
```

Array and pointer declarations:

```franko
[int] xs;
[int32] ys;
[uint8, 128] buffer;
[[byte, 20], 12] grid;

ptr<int> p;
ptr<int32> q;
ptr<[int]> array_ptr;
```

Declaration initialization is its own statement form:

```franko
int32 x = 1;
```

It declares `x` and initializes it once. Declaration initialization is the
only place a `const` variable can receive its value, and it is evaluated
exactly once as part of the declaration.

The initializer must obey normal assignment rules and is only valid where the resulting assignment is legal.

## 18.1 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

## 18.2 Shadowing

A variable may be redeclared in an inner block, creating a new variable that shadows the outer one.

## 18.3 Use Before Declaration

Local variables must be declared before they are used.

## 18.4 Global Variable Declarations

Variable declarations are allowed at global scope. Global variables are statically allocated.

Two different initializer forms exist for declarations, and they follow different rules at global scope.

**Constant initializers.** A global declaration may end with `= constExpr`:

```franko
int32 g = 1 + 2
uint64 mask = 1 << 8
```

The initializer must be a constant expression built from literals, so it is folded at compile time and may not reference variables. Only primitive globals accept this form: struct, array, and ptr globals reject `= constExpr` as a type mismatch.

**Static address initializers.** A pointer-typed global may be
initialized with `null`, with `getptr`/`getmview`/`getview` over another
global variable, or with a string literal (which is a view over
immutable constant storage; see [String Literals](23-string-literals.md)):

```franko
[byte, 5] greeting
view<[byte, 5]> v = getview(greeting)
view<[byte, 5]> s = "hello"
ptr<[byte, 5]> p = null
```

The initializer must be a statically representable address of the right
type (a permission downgrade is allowed, an upgrade is not).

An array literal may initialize a pointer-typed global (it is a
`view<[T, N]>` value over immutable constant storage, like a string
literal):

```franko
view<[int32, 4]> G = [1, 2, 3, 4]   // valid
```

**Ordinary declaration initialization.** The `= expr` form described in this chapter and in [Assignment](16-assignment.md), when the expression is not constant, performs an executable initialization. Executable statements are not allowed at global scope, so this form is invalid on global declarations:

```franko
int32 g = h                     // invalid: the initializer must be constant
[int32, 3] xs = [1, 2, 3]  // invalid: arrays are not assignable from literals
[byte, 6] s = "hello"        // invalid: strings cannot initialize arrays
```

The constant-initializer, static-address, and array-literal forms above
are the only ways to initialize a global at its declaration.

## 18.5 Uninitialized Values

A **local** declaration without an initializer default-initializes the variable:

- primitive integer variables start with an indeterminate value; reading one before its first assignment is undefined behavior;
- static array elements start with indeterminate values, except pointer elements, which are null;
- dynamic arrays start empty (length 0, no element storage) and must be initialized with `init` or `init_zero` before indexed use;
- **pointer storage is guaranteed null-initialized**: pointer variables, pointer struct fields, pointer tuple elements, pointer static-array elements, and pointer elements of initialized dynamic arrays are all null (see [Pointers](25-pointers.md));
- **enum storage is default-initialized to the first variant**: enum variables, enum struct fields, enum tuple elements, enum static-array elements, and enum elements of initialized dynamic arrays all start as the enum's first declared variant, even when its backing value is not `0` (see [Enums](30-enums.md)).

There is no implicit zero-initialization for locals other than pointer storage. Use `init_zero` for zeroed dynamic array storage.

**Global** declarations are statically allocated: primitives, static array elements, and aggregate storage without an explicit default start at zero (pointer storage starts null) before `main` runs, and dynamic arrays start empty. Global constant initializers and field defaults (see Global Variable Declarations above) are applied on top of that.

## 18.6 Struct Fields

Struct fields are declared with the same `type name` syntax as variables, inside a top-level `struct` declaration:

```franko
struct X {
    int32 num = 0
    [int32, 4] xs
    ptr<Y> next
}
```

Field defaults must be constant expressions on primitive fields only, and duplicate field names are rejected.

The complete struct reference — field rules, by-value cycles, assignability, member access, and globals — is in [Structs](26-structs.md).

Tuple declarations and tuple initialization are covered in
[Tuples](28-tuples.md).

## 18.7 Aggregate Field Initializers

A local struct or tuple declaration may initialize individual fields with a
braced field list immediately after the declared name:

```franko
Literal lit { x = 0, def = 1 }
Buffer b { cap = 20 }
(int32 first, int32 second) tup { first = 1, second = 2 }
(int32, int32) pair { 0, 1 }
(int32, int32) partial { 0 }
```

This is declaration initialization: one ordinary field assignment per named
or positional entry, kept as part of the declaration:

```franko
Literal lit
lit.x = 0
lit.def = 1
```

Rules:

- struct fields are always named, tuple entries are named or positional, and
  order is free for named entries; duplicate or unknown names are rejected;
- positional entries are available only for all-unnamed tuple declarations,
  fill the first N elements, may be partial, and may not exceed the element
  count; positional and named entries cannot be mixed;
- omitted fields keep the declaration's default-constructed state;
- no temporary aggregate value is created and no `OPERATOR_MOV` is invoked;
- the form works for pointer-only aggregates because each field is assigned
  directly under ordinary assignment rules;
- an array field still cannot be assigned from another array or a literal;
- the brace must follow the identifier immediately; a newline ends the
  declaration;
- the form is local-only and is invalid at global scope.

The complete aggregate initialization reference is in
[Aggregate Literals](29-aggregate-literals.md).

## 18.8 Const Storage

`const` is a storage modifier on variables, parameters, and globals. It is not
a type: it is not part of a function signature, overload identity, or
`#what_type`.

Syntax:

```franko
const T x = expr          // local, plain declaration initialization
const P p { field = v }   // local, field-wise declaration initialization
const T G = globalInit    // global
func f(const T p) -> ...  // parameter, including const self
```

`const` is legal only when the declared type is value-assignable:

- a primitive integer;
- an enum;
- a pointer (`ptr<T>`, `mview<T>`, `view<T>`);
- a struct all of whose fields are value-assignable, recursively;
- a tuple all of whose elements are value-assignable, recursively.

It is illegal on static arrays, dynamic arrays, structs or tuples containing
an array or another non-value-assignable part, `void`, and struct fields.
Using `const` on an invalid type is an error, for example:

```text
Const variable 'xs' has type '[int32]'; const is only allowed for value-assignable types
```

The rules:

1. **No assignment.** A const variable cannot be the target of `=` or a
   compound assignment (`+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`), and
   `x++`/`x--` are rejected for the same reason. A field or element of a const
   variable is also const storage. A dereference starts a new storage region:
   `$(p) = v` is allowed when `p` is a const pointer; only `p = v` is
   rejected.
2. **No mutable pointer to const storage.** `getptr(constVar)` and
   `getmview(constVar)` are rejected; `getview(constVar)` is allowed.
3. **Declaration initialization only.** A const variable is initialized by
   its declaration. Both the plain form `const T x = expr` and the field-wise
   form `const P p { field = v }` are declaration initialization, not a
   separate assignment; no later assignment is allowed.
4. **Uninitialized const locals warn when not fully default-initialized.**
   `const int32 x` warns because `int32` has no default value. `const Point p`
   does not warn when every field is fully defaulted. Parameters and globals
   never receive this warning: parameters are initialized by the caller and
   globals are zero/default-initialized before `main`.
5. **No const fields.** `const int32 f` inside a struct is rejected:
   `Struct fields cannot be const`. A value-assignable struct may itself be
   const.
6. **Const struct receivers allow only `view<X> self` methods.** The hidden
   receiver argument uses `getptr`, `getmview`, or `getview` matching the
   declared `self` type, so `ptr<X> self` and `mview<X> self` calls on a const
   value are rejected and `view<X> self` is allowed. Direct field reads are
   allowed; field writes are rejected.
7. **Const parameters are allowed.** `const int32 a`, `const Point p`, and
   `const self` are legal when the parameter type is value-assignable.
   `const` is not part of overload identity or mangling: `f(const int32)` and
   `f(int32)` are duplicate declarations. A method's `self` slot is always
   implicitly const; writing `const self` is allowed but redundant.
8. **Const globals are allowed** for value-assignable types, using the
   existing global initializer forms (constant initializer, static address,
   string literal, or array literal through a pointer type). A const global
   cannot be assigned from any function.

`#is_value_assignable` is exactly the type predicate `const` requires.

---

# 19. Functions

Franko supports user-defined functions.

Function declarations are global top-level items.

Function declarations are not valid inside blocks or inside other function bodies.

## 19.1 Function Declaration Syntax

```franko
func name(parameters) -> return_type {
    statements
}
```

By convention function names use `lower_case_snake_case`. A function whose
name starts with `__` is internal to its declaring namespace and produces a
warning when referenced from outside that namespace. Direct FFI mappings keep
the platform API spelling, such as `os::win32::ReadFile`; see
[Naming Conventions](36-naming-conventions.md).

## 19.2 Function Declaration Placement

Function declarations are valid only in global scope.

## 19.3 Function Parameters

Function parameters are treated as local variables inside the function body.

A parameter may be declared `const`: `func sum(const int32 a, const int32 b)`
or `func f(const Point p)`. Const parameters have the same assignment
restrictions as const locals: the parameter cannot be reassigned, and only
`getview` may take a mutable pointer to it. A const parameter's type must be
value-assignable. `const self` is allowed in methods and only makes the
pointer slot itself immutable; the declared `ptr`/`mview`/`view` permission
still governs the pointee.

`const` is not part of a parameter type, a function signature, or overload
identity: `f(const int32)` and `f(int32)` are duplicate declarations.

Duplicate parameter names inside the same function are invalid.

A parameter type must be a **passable type**:

- a primitive integer type;
- a declared enum type (see [Enums](30-enums.md));
- a pointer type (`ptr<T>`, `mview<T>`, or `view<T>`), where `T` is any valid declared type. Arrays and nested pointers are allowed as the referenced type, so `ptr<[int32]>`, `mview<[int32, 8]>`, and `view<ptr<int32>>` are all passable; or
- a struct type whose fields are all assignable value types, recursively. Assignable = primitive | pointer | struct-with-all-assignable-fields. Static arrays and dynamic arrays are not assignable, so a struct containing one of them is pointer-only: pass a pointer to it (for example `ptr<X>` with `getptr(s)` when ownership or lifecycle operations are needed, or `view<X>`/`mview<X>` for read/write access). See [Structs](26-structs.md); or
- a tuple type whose elements are all assignable value types, recursively. A tuple containing a static array or dynamic array is pointer-only, exactly like a struct with such a field. `void` cannot be a tuple element. See [Tuples](28-tuples.md).

Bare array types are not passable. To pass an array to a function, declare a pointer parameter (`ptr<[T]>`, `mview<[T]>`, or `view<[T]>`, and the corresponding static-size forms) and pass the matching `getptr`/`getmview`/`getview` expression at the call site.

`void` cannot be written as a parameter type.

## 19.4 `void`

`void` is valid as a function return type. It is not an ordinary value type.

## 19.5 Function Signatures and Overloading

Franko supports function overloading.

A function signature is determined by:

- the function identifier;
- the ordered list of parameter types.

Return type is not part of the function signature.

Declaring two functions with the same identifier and the same ordered parameter types is an error, even if their return types differ.

Return type is not part of overload identity, but two declarations with
the same identifier and parameter types are still an error when their
return types differ. Functions may be called recursively.

Tuple element names are not part of a function signature. These two
declarations have the same normalized signature and are duplicates:

```franko
func f((int32 a, int32 b) x) -> int32 { ... }
func f((int32 c, int32 d) y) -> int32 { ... }
```

The normalized signature is:

```text
fn((int32, int32)) -> int32
```

For example, `func foo((int32 x, int32 y) coord, uint8 data) -> (int32 val, int32 err)`
normalizes to `fn((int32, int32), uint8) -> (int32, int32)`.

## 19.6 Function Call Resolution

A function call is valid if and only if exactly one overload is applicable.

For nonconstant arguments, the argument type must exactly match the
parameter type, with the single exception of the pointer conversions:
permission downgrades over an identical referenced type (`ptr`/`mview`/
`view` may pass where a weaker permission is expected), and the
static-to-dynamic array pointer view decay (`mview`/`view` of
`[T, N]`, or a `ptr` source downgraded in the same step, may pass
where `mview`/`view` of `[T]` is expected; see
[Pointers](25-pointers.md)). There are no other implicit conversions: pass
`cast<T>(x)` explicitly to move a value across integer widths (see
[Casts](12-casts.md)).

For folded integer constants, the constant may match a primitive integer parameter if its value fits that parameter type. When a constant fits several overloads, the constant-fit preference applies: the parameter matching the constant's own type wins when the value fits, otherwise the narrowest fitting type wins (signed before unsigned at equal width). See [Fluid Integer Constants](07-fluid-integer-constants.md).

Overload preference: among applicable overloads, ones matched without any
conversion win over converting ones, and among the rest the ones relying
on the fewest conversions win, with a decay counted heavier than a pure
permission downgrade. Constant arguments then prefer the lowest constant-fit cost (own type, then narrowest, signed first). If several overloads remain
tied, the call is ambiguous and is rejected.

## 19.7 Bare Function Names

Variables and functions live in separate namespaces.

A bare function name is not a value. An identifier used as a value resolves to a variable first; if the identifier only matches a function name, it is rejected:

```franko
func f() -> int32 {
    return 1;
}

f;    // invalid: Function name 'f' cannot be used as a value
```

In a call position, `name(...)` resolves to a function even when a variable with the same name is in scope. Variables are not callable; using a variable name in call position is an error.

## 19.8 Function Call Expressions

A function call expression has the return type of the selected function.

A `void`-returning function call is valid as an expression statement but invalid in value-producing contexts.

## 19.9 Return Statements

A `void` function may use bare `return` and must not use `return expr`.

A non-`void` function must return an expression compatible with the declared return type. A struct or tuple value may be constructed directly in the return expression with an aggregate literal:

```franko
struct Literal {
    int32 x
    int32 def = 0
}

func make() -> Literal {
    return Literal { x = 1, def = 2 }
}
```

A non-`void` function must contain at least one `return` statement somewhere in its body (a bare `return;` still counts for this presence check, but is separately rejected for lacking an expression). Missing-return detection is presence-based: a `return` nested inside an
`if` or `while` body counts as appearing in the function, and the compiler
does not prove that every path returns.

## 19.10 Function Return Types

A function return type must be either:

- `void`;
- a primitive integer type;
- a declared enum type (see [Enums](30-enums.md));
- a pointer type (`ptr<T>`, `mview<T>`, or `view<T>`); or
- a struct type whose fields are all assignable value types, recursively (see [Structs](26-structs.md)); or
- a tuple type whose elements are all assignable value types, recursively (see [Tuples](28-tuples.md)).

Arrays cannot currently be returned directly. A tuple containing an array or another non-assignable element cannot be returned by value; use a pointer to the tuple instead.

A struct containing an array is pointer-only and cannot be returned by value either. The compiler rejects such a declaration with guidance to return a pointer or write through an out-parameter:

```franko
struct Buffer {
    int32 cap
    [byte] buf
}

func foo() -> Buffer { ... }        // error at declaration

func foo(ptr<Buffer> outbuf) -> int32 {
    // write the result through outbuf
    return 0
}
```

The correct pattern for pointer-only output is the `ptr<Buffer>` out-parameter form. See [Aggregate Literals](29-aggregate-literals.md).

## 19.11 Forward References and Recursion

Function signatures are known before function bodies are checked, allowing forward references, direct recursion, and mutual recursion.

Struct methods follow the same signature, overload, and return rules with an explicit `self` first parameter of type `ptr<X>`, `mview<X>`, or `view<X>`; see [Methods](27-methods.md).

## 19.12 Compile-Time Intrinsics

The built-in `#name(...)` functions (`#is_primitive`, `#what_type`,
`#strcmp`, `#strcat`, and the rest) are compile-time functions, not
callable values. They are resolved at compile time; they cannot be stored
in a variable, passed as a function argument, or referred to without a
call. See [Compile-Time Reflection](34-compile-time-reflection.md).

---

# 20. Arrays

Franko supports two array categories:

```franko
[T]      // dynamic array
[T, N]   // static array
```

Examples:

```franko
[int32] dynamic_ints;
[uint8, 128] static_bytes;
```

Arrays may be nested:

```franko
[[int32]] nested_dynamic;
[[int32, 4], 8] matrix;
```

Arrays are not arithmetic values and cannot be directly assigned as ordinary values.

However, Franko supports **array literals** as `view<[T, N]>` values over immutable constant storage; arrays receive their elements through `memcpy`. See [Array Literals](21-array-literals.md).

## 20.1 Static Arrays

A static array has a compile-time size:

```franko
[int32, 10] xs;
```

Static array size rules:

- size must be a compile-time integer constant;
- size must be greater than zero;
- size must fit `uint32`.

Valid:

```franko
[int32, 1] a;
[int32, 0x10] b;
[int32, 0b1000] c;
[int32, 1 + 2] d;
```

Invalid:

```franko
[int32, 0] a;
[int32, -1] b;
[int32, 999999999999999999999999999999999] c;
```

Static arrays cannot use `init`; fill them with `memset` or `memcpy`.

The size may be written as `?` in a `view` declaration with an
array-literal initializer (see [Shorthands and Equivalences](24-desugaring.md)):

```franko
view<[int32, ?]> a = [0, 1, 2, 3];   // ? = 4
view<[byte, ?]> b = [104, 105];      // ? = 2
```

(`?` requires a `view<[...]>` declaration; a bare static array such as
`[int32, ?]` is rejected. String literals are `view<[byte, N]>` values
and do not participate in `?` inference; see
[String Literals](23-string-literals.md).)

The current element count of any array is available through the `length()` intrinsic (see [Array Intrinsics](22-array-intrinsics.md)).

## 20.2 Dynamic Arrays

A dynamic array has runtime size:

```franko
[int32] xs;
```

Dynamic arrays are initialized with the array intrinsics `init` and `init_zero`:

```franko
xs.init(10);        // allocate uninitialized storage
xs.init_zero(10);   // allocate zeroed storage
```

Rules for dynamic array initialization:

- target must be a storage-backed lvalue;
- target must be a dynamic array;
- exactly one argument is required;
- size must be compatible with `uint32`;
- constant sizes must be greater than zero;
- constant sizes must fit `uint32`;
- nonconstant sizes must have exactly type `uint32`.

An initialized dynamic array may be resized with `resize`. See [Array Intrinsics](22-array-intrinsics.md).

Dynamic array declaration alone does not allocate element storage.

The array must be initialized before ordinary indexed use.

An initialized dynamic array owns constructed elements: for element types that need construction (for example nested dynamic arrays and pointers), `init` and `init_zero` default-construct every element. Inner arrays start in the uninitialized state (length `0`), ready for `init`, and pointer elements start null. Growing `resize` default-constructs the new tail elements, so pointer elements added by a resize are null too.

## 20.3 Array Access

Array indexing syntax:

```franko
arr[index]
```

The target must be an array.

Index rules:

- constant index:
  - must be nonnegative;
  - must fit `uint32`;
- nonconstant index:
  - must have exactly type `uint32`.

For static arrays, constant indexes are checked against the known static size when possible.

## 20.4 Array Elements as Lvalues

Array elements are storage-backed lvalues when the array target is storage-backed.

Array elements may be pointed to:

```franko
ptr<int32> p;
p = getptr(xs[0]);
```

Nested array elements may also be lvalues when each target is storage-backed.

## 20.5 Array Direct Assignment Is Invalid

Arrays cannot be assigned directly as ordinary values.

Invalid:

```franko
[int32] a;
[int32] b;

a = b;
```

Use one of:

```franko
a.memcpy(getptr(b));
a.memset(0);
a.uninit();
```

An array literal is not an array value; arrays receive literal
elements through `memcpy` (see [Array Literals](21-array-literals.md)).

## 20.6 Array Lifetime Checking

The compiler validates intrinsic legality and checks the initialization
lifecycle of function-local dynamic array variables and owner `ptr<[T]>`
aliases. Definite use before init, use after `uninit`, invalid free, and
double free are compile errors; possible cases are warnings. Repeated `init`
is not diagnosed because an earlier call may have failed with `-1` and a
retry is valid; the runtime still aborts on a true double init. Missing an
explicit `uninit()` is a warning because the runtime destructor still frees
local buffers.

A call is checked against what the callee does: a callee can require an
initialized array, initialize, resize, or free the caller's array, or return
an element pointer into it. An owner `ptr<[T]>` or whole-array
`view<[T]>`/`mview<[T]>` points at the array object, which keeps the same
address across `init`, `resize`, and `uninit`; those whole-array pointers
remain valid. Only element pointers/views created from
`getptr`/`getmview`/`getview(d[i])` point into the element buffer and are
invalidated when the buffer is replaced or freed.

An element pointer/view is tied to the buffer that existed when it was
created. Using one whose buffer has definitely been replaced is an error; a
possibly stale one is a warning. This applies at dereference, indexing,
assignment through the alias, call argument, return, and creation intrinsics
over the alias. A whole-array view is not an element alias and is never
invalidated by `resize`/`uninit`. A call that may invalidate array parameter
`i` while using an element-pointer parameter `j` that aliases `i` produces a
possible cross-parameter invalidation warning. See
[Dynamic-Array Lifecycle Checking](31-memory-safety-checking.md#2910-dynamic-array-lifecycle-checking).

The check does not yet cover:

- dynamic arrays in struct fields or nested dynamic-array elements below the
  outermost tracked array;
- global dynamic-array lifecycle;
- runtime bounds violations for nonconstant indexes;
- allocation failure status of `init`/`init_zero`.

---

# 21. Array Literals

Franko **array literals** are read-only views of static arrays, exactly
like [string literals](23-string-literals.md) are views of byte arrays.

An array literal is written with square brackets:

```franko
[1, 2, 3]
```

An array literal is an **ordinary expression** whose type is
`view<[T, N]>` (see [Pointers](25-pointers.md)): a read-only
pointer to a static array of `N` elements, where `N` is the number of
elements written and `T` is the **inferred element type**.

```franko
[1, 2, 3]        // view<[int32, 3]> when T is inferred as int32
[0, 255]         // view<[uint8, 2]> when T is inferred as uint8
```

## 21.1 Element Type Inference

The element type `T` is always an integer primitive type or an enum
type. It is **inferred from the surrounding code** — a literal carries
no element type of its own:

- **assignment**: the target's element type (a `view<[T, ...]>`
  variable, or an array target whose element type guides the inference
  before the assignment is rejected — see 22.5);
- **returns**: the function's return type;
- **call arguments**: the parameter type (function, method, or array
  intrinsic parameters such as `memcpy`'s source).

Outside these contexts a literal has nothing to infer from and is
rejected:

```franko
[1, 2, 3];              // invalid: bare expression statement
take([1, 2, 3]);        // valid when take's parameter is
                        // view<[int32]> (or view<[T, ...]>)
```

Constants in a literal are fluid like everywhere else: each element is
checked against the inferred `T`'s range. Enum literals take enum
variant references instead of integer constants: each element must be
a variant of the inferred enum type (see [Enums](30-enums.md)).

```franko
view<[uint8, 3]> b = [0, 127, 256];   // invalid: 256 does not fit
```

## 21.2 Constant Elements Only

Every element must be a **compile-time constant expression** (integer
literals and operators over them). The literal's constant elements are
stored once per distinct content and element type and shared by all
uses, so returning a literal or calling a function that returns one is
always safe, including recursively:

```franko
func first() -> view<[int32, 3]> {
    return [1, 2, 3];       // backed by shared constant storage
}

view<[int32, 3]> a = [1 + 1, 2 * 3, 1 << 3];   // constant expressions
```

Runtime values cannot appear in a literal:

```franko
int32 v = 5;
view<[int32, 3]> t = [v, v + 1, 2];   // invalid: v is not constant
```

The same holds for enums: only variant references are allowed, so a
runtime enum variable or any non-variant enum expression is rejected:

```franko
enum<uint8> Status { spawning, alive }

Status s = Status::spawning;
view<[Status, 2]> bad = [s, Status::alive];   // invalid: s is not a variant
view<[Status, 2]> good = [Status::spawning, Status::alive];   // valid
```

Compute runtime values into a declared array with ordinary element
assignments, or copy a constant literal and modify it.

## 21.3 Length and Type

A literal has exactly as many elements as written: `[1, 2, 3]` is
`view<[int32, 3]>`, never a shorter or longer array. An empty
literal `[]` is not supported (like `""`, `N >= 1`).

```franko
view<[int32, 2]> v = [1, 2, 3];   // invalid: lengths 2 and 3 differ
view<[int32, 3]> v = [1, 2, 3];   // valid
```

Because the literal is a pointer, elements are reached through a
dereference of a view variable:

```franko
view<[int32, 3]> v = [1, 2, 3];
print($(v)[0]);            // 1
```

Indexing a literal directly is not supported: `[1, 2, 3][0]` is
rejected — and a bare literal under `$` has no inference context
either. Assign the literal to a `view` variable first and index
through that.

## 21.4 Nested Array Literals

Nested literals are static at **every** level and therefore must be
**perfectly rectangular**: all rows at each level have the same length.

```franko
view<[[int32, 2], 2]> m = [[1, 2], [3, 4]];   // valid
view<[[int32, 2], 2]> m = [[1, 2], [3]];      // invalid: jagged
```

The type is `view<[[T, M], N]>`: `N` rows of `M` elements.
Each row is itself a literal of type `view<[T, M]>` when separated,
so a row length mismatch is a type error.

When the literal decays to a dynamic view (22.6), only the outermost
dimension becomes dynamic; inner dimensions stay static:

```franko
func solve(view<[[int32, 13]]> maze) -> void {
    // rows are exactly 13 cells wide: the width is enforced by the type
}

solve([[1, 2, 3]]);            // invalid: rows are 3 wide, not 13
solve(grid);                   // valid for a view<[[int32, 13], 12]>
```

This one-way decay is how a function enforces a required row width or
maze size: a caller can decay *down* to `view<[[T, M]]>`, but
nothing with a different `M` can ever reach it.

## 21.5 Arrays Are Not Assignable From Literals

Arrays are value types; an array literal is a view. A literal cannot
initialize or assign an array — copy the elements with `memcpy`
instead, exactly as with string literals:

```franko
[int32, 3] buf;
buf.memcpy([1, 2, 3]);       // copies 3 elements
[int32, 4] xs;
xs.memset(0);
xs.memcpy([7, 8]);           // copies 2 elements; xs[2], xs[3] untouched
```

For dynamic arrays, size the storage first:

```franko
[int32] dyn;
dyn.init(3);
dyn.memcpy([1, 2, 3]);
```

A shorter literal copies a prefix; `memcpy` copies
`min(source, target)` elements at runtime.

## 21.6 Views of Literals

The literal's own type is `view<[T, N]>`. The ordinary static→
dynamic view decay (see [Pointers](25-pointers.md) 24.4) applies, so
it is assignable wherever a `view<[T]>` is expected:

```franko
func take(view<[int32]> v) -> int32 {
    return $(v)[0];
}

take([1, 2, 3]);                        // decays to view<[int32]>
view<[int32]> d = [1, 2, 3, 4];  // decay in a declaration
```

When the decay applies directly to a literal in one of these positions,
the converted view refers to the literal's constant storage and keeps
its length. No copy or new array object is created. Returning or
keeping such a view is therefore always safe — including from recursive
functions — because the literal's storage has static lifetime.
Permission rules are unchanged: a literal (read-only) cannot initialize
an `mview` or `ptr` variable.

## 21.7 `?` Size Inference

A static size may be written `?` in a `view<[...]>` declaration
with an array-literal initializer; the size is the literal's length at
that level:

```franko
view<[int32, ?]> v = [1, 2, 3];              // view<[int32, 3]>
view<[[int32, ?], ?]> m = [[1, 2], [3, 4]];
```

The rules are the same as for other `?` inference (see
[Shorthands and Equivalences](24-desugaring.md)): `?` is only allowed in a local
declaration with an array-literal initializer, the array nest must be
static at every level, and the fit must be exact when `?` is used.

## 21.8 Global Literals

A pointer-typed global may be initialized with an array literal of
constant elements, like a string literal:

```franko
view<[int32, 4]> G = [1, 2, 3, 4];

func main() -> int32 {
    print($(G)[3]);         // 4
    return 0;
}
```

`G` refers to the literal's constant `[int32, 4]` storage; the literal is
not copied.

Array-typed globals are not assignable from literals (22.5); use
element assignments in an initializer function.

## 21.9 Summary

Array literals in Franko are:

- ordinary expressions of type `view<[T, N]>`;
- element type `T` inferred from assignment, return, or call-argument
  contexts (integer primitives or enums);
- compile-time constant expressions only, backed by shared constant
  storage with static lifetime (recursion-safe, like string literals);
- exactly as long as written (`N >= 1`);
- rectangular when nested, with inner dimensions static;
- decayable to `view<[T]>` (outermost dimension only);
- never assignable to arrays — copy with `memcpy`;
- inferrable in size via `?` in `view` declarations.

---

# 22. Array Intrinsics

Array operations are written using member-call syntax, but they are built-in
intrinsics recognized by the compiler, **not** general user-defined
function calls. Only the named intrinsics below are recognized.

Intrinsics are **expressions**, not statement-only forms. Status-returning intrinsics may be used in value contexts:

```franko
int32 status;
status = xs.init(10);
```

Void-returning intrinsics are invalid in value contexts.

For function-local dynamic arrays, the compiler also reports definite use
before init, use after `uninit`, invalid free, and double free.
Repeated `init` is not diagnosed because an earlier call may have failed
with `-1` and a retry is valid. See
[Dynamic-Array Lifecycle Checking](31-memory-safety-checking.md#2910-dynamic-array-lifecycle-checking).

| Intrinsic | Receiver | Returns | Status |
|---|---|---|---|
| `target.init(size)` | dynamic array | `int32` | `0` on success, `-1` on allocation failure |
| `target.init_zero(size)` | dynamic array | `int32` | `0` on success, `-1` on allocation failure |
| `target.resize(size)` | dynamic array | `int32` | `0` on success, `-1` on allocation failure |
| `target.uninit()` | dynamic array | `void` | — |
| `target.memset(value)` | array of memset-safe elements | `void` | — |
| `target.memset(value, start, count)` | array of memset-safe elements | `void` | — |
| `target.memcpy(source)` | array of memcpy-safe elements | `void` | — |
| `target.memcpy(source, dst_start, src_start, count)` | array of memcpy-safe elements | `void` | — |
| `target.memmove(dst_start, src_start, count)` | array of memcpy-safe elements | `void` | — |
| `target.memcmp(source)` | array of memcpy-safe elements | `int32` | negative, zero, or positive |
| `target.memcmp(source, dst_start, src_start, count)` | array of memcpy-safe elements | `int32` | negative, zero, or positive |
| `target.length()` | any array | `uint32` | — |
| `target.memchr(value)` | `[byte]` / `[byte, N]` | `uint32` | index or `0xFFFFFFFF` |
| `target.memchr(value, start, count)` | `[byte]` / `[byte, N]` | `uint32` | index or `0xFFFFFFFF` |
| `target.memmem(needle)` | `[byte]` / `[byte, N]` | `uint32` | index or `0xFFFFFFFF` |
| `target.memmem(needle, start, count)` | `[byte]` / `[byte, N]` | `uint32` | index or `0xFFFFFFFF` |
| `target.read_uint16_LE(offset)` … `target.read_int64_BE(offset)` | `[byte]` / `[byte, N]` | `uint16`/`uint32`/`uint64`/`int16`/`int32`/`int64` | — |
| `target.write_uint16_LE(offset, value)` … `target.write_int64_BE(offset, value)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.read_bit(bit_idx)` | `[byte]` / `[byte, N]` | `byte` | — |
| `target.write_bit(bit_idx, value)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.flip_bit(bit_idx)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.bitwise_not_mask()` | `[byte]` / `[byte, N]` | `void` | — |
| `target.bitwise_xor_mask(mask)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.bitwise_and_mask(mask)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.bitwise_or_mask(mask)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.shift_bits_left(bit_count)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.shift_bits_right(bit_count)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.count_set_bits()` | `[byte]` / `[byte, N]` | `uint64` | — |

The receiver of a mutating array intrinsic must be a storage-backed array lvalue. The read-only intrinsics (`length`, `memcmp`, `memchr`, `memmem`, the `read_*` accessors, `read_bit`, `count_set_bits`) accept any array-typed expression.

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

## 22.1 Receiver Permissions

The array intrinsics participate in the pointer permission model (see
[Pointers](25-pointers.md)). When a receiver is reached through a
pointer dereference, the intrinsic needs that pointer's permission:

| Intrinsic | Required receiver permission |
|---|---|
| `init`, `init_zero`, `resize`, `uninit` | allocation (`ptr`) |
| `memset`, `memcpy`, `memmove` (as target), `write_*`, `write_bit`, `flip_bit`, `bitwise_*_mask`, `shift_bits_*` | mutation (`mview` or `ptr`) |
| `length()`, `memcmp`, `memchr`, `memmem`, `read_*`, `read_bit`, `count_set_bits`, and element reads | read (any permission) |

The `memcpy`/`memcmp`/`memmem` sources are only read, so they may be any
pointer to an array.

```franko
view<[byte]> v = getview(buf);
$(v)[0] = 1;         // blocked: view is read-only
$(v).memset(0);      // blocked: mutating intrinsic
$(v).memchr(65);     // allowed: read-only intrinsic
$(v).read_bit(3);    // allowed: read-only intrinsic
$(v).write_bit(3, 1); // blocked: mutating intrinsic
$(v).length();       // allowed

mview<[byte]> m = getmview(dyn);
$(m)[0] = 1;         // allowed
$(m).write_bit(3, 1); // allowed
$(m).init(10);       // blocked: init needs allocation rights (ptr)
```

## 22.2 `init` and `init_zero`

Dynamic array initialization.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must have dynamic array type;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32`; or
  - a nonconstant expression of exactly type `uint32`.

`init` allocates uninitialized element storage. `init_zero` allocates zeroed element storage.

Element types that need construction (for example nested dynamic arrays and pointers) are default-constructed by `init` and `init_zero`: inner arrays start in the uninitialized state (length `0`), ready for `init`, and pointer elements start null. Element types that are trivially default-constructible keep raw (`init`) or zeroed (`init_zero`) storage with no per-element work.

## 22.3 `resize`

Resizes an already-initialized dynamic array. Growing default-constructs the new tail elements, so pointer elements added by the growth start null; shrinking destroys the dropped tail elements. Element types with a trivial default constructor skip construction, and trivially destructible element types skip destruction.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must be a dynamic array;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32`; or
  - a nonconstant expression of exactly type `uint32`.

## 22.4 `uninit`

Frees a dynamic array's element storage and resets it to the uninitialized state. Constructed elements are destroyed before the buffer is freed; trivially destructible element types skip destruction.

Rules:

- receiver must be a storage-backed lvalue;
- receiver must be a dynamic array;
- takes exactly zero arguments.

## 22.5 `memset`

Fills an array byte-wise.

Forms:

```franko
target.memset(value)                    // whole array
target.memset(value, start, count)      // element range
```

Rules:

- receiver must be a storage-backed lvalue;
- receiver may be a dynamic or static array;
- receiver element type must be memset-safe;
- the whole-array form takes exactly one argument; the ranged form takes exactly three;
- fill value must be:
  - a constant fitting `uint8`; or
  - a nonconstant expression of exactly type `uint8`;
- `start` and `count` must be:
  - nonnegative constants fitting `uint32`; or
  - nonconstant expressions of exactly type `uint32`;
- for static arrays, constant start/count ranges are checked against the static size at compile time.

## 22.6 Memset-Safe Element Types

Current memset-safe types:

| Element type | Memset-Safe? |
|---|---|
| primitive integers | yes |
| enums | no |
| static arrays of memset-safe elements | yes |
| structs whose fields are all memset-safe, recursively | yes |
| dynamic arrays | no |
| pointers | no |

`#is_memset_safe(x)` exposes this predicate at compile time (see
[Compile-Time Reflection](34-compile-time-reflection.md)). It is
deliberately distinct from `#has_default_value`: a struct with a field
default is memset-safe and also has a defined default value.

## 22.7 `memcpy`

Copies array contents byte-wise.

Forms:

```franko
target.memcpy(source)                          // whole array
target.memcpy(source, dst_start, src_start, count)  // element ranges
```

Rules:

- target must be a storage-backed lvalue array;
- source must be a **pointer to an array** (`ptr<[T]>`, `mview<[T]>`, `view<[T]>`, or the corresponding `[T, N]` forms), typically produced with `getptr(...)`, `getmview(...)`, or `getview(...)`, or an array/string literal (its element type is inferred from the target); passing an array value directly is invalid;
- both target and source may be dynamic or static arrays;
- target and source element types must match exactly;
- static/dynamic shape does not need to match;
- static lengths do not need to match;
- target and source element types must be memcpy-safe;
- `dst_start`, `src_start`, and `count` must be:
  - nonnegative constants fitting `uint32`; or
  - nonconstant expressions of exactly type `uint32`;
- for static arrays, constant start/count ranges on both the destination and the source are checked against the static sizes at compile time.

The whole-array form copies the smaller of the two lengths; bytes past the
destination's length are never written.

## 22.8 `memmove`

Copies an element range within one array, overlapping ranges included.

Form:

```franko
target.memmove(dst_start, src_start, count)
```

Rules:

- receiver must be a storage-backed lvalue array (dynamic or static);
- takes exactly three arguments;
- receiver element type must be memcpy-safe;
- `dst_start`, `src_start`, and `count` must be:
  - nonnegative constants fitting `uint32`; or
  - nonconstant expressions of exactly type `uint32`;
- for static arrays, constant ranges are checked against the static size at compile time.

## 22.9 Memcpy-Safe Element Types

Current memcpy-safe types:

| Element type | Memcpy-Safe? |
|---|---|
| primitive integers | yes |
| enums | yes |
| pointers | yes |
| static arrays of memcpy-safe elements | yes |
| structs whose fields are all memcpy-safe, recursively | yes |
| dynamic arrays | no |

## 22.10 `length`

Returns the element count of an array as `uint32`.

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
[int32] xs;
print(xs.length());   // 0
xs.init(4);
print(xs.length());   // 4
xs.resize(10);
print(xs.length());   // 10
xs.uninit();
print(xs.length());   // 0
```

Static arrays report their size as a compile-time constant: `ys.length()` for `[int32, 3] ys` compiles to the constant `3`, with no runtime read.

Nested receivers report the outermost dimension; index first for inner dimensions:

```franko
[[int32, 2], 3] m;
print(m.length());      // 3
print(m[0].length());   // 2
```

`length()` returns a value, so it may be used in any value context: arithmetic, loop bounds, assignments, and `print`.

## 22.11 `memcmp`

Compares array contents byte-wise. The receiver and the source must have
identical element types, and that element type must be memcpy-safe.

Forms:

```franko
target.memcmp(source)                             // whole arrays
target.memcmp(source, dst_start, src_start, count)  // element ranges
```

Rules:

- receiver may be a dynamic or static array; it does not need to be a storage-backed lvalue;
- source must be a **pointer to an array** (`ptr<[T]>`, `mview<[T]>`, `view<[T]>`, or the static-array forms), typically produced with `getptr(...)`, `getmview(...)`, or `getview(...)`, or an array literal whose element type is inferred from the receiver;
- both receiver and source element types must be memcpy-safe;
- `dst_start`, `src_start`, and `count` must be:
  - nonnegative constants fitting `uint32`; or
  - nonconstant expressions of exactly type `uint32`;
- for static arrays, constant ranges on both sides are checked at compile time.

The result is `int32`, following the usual byte-comparison convention: a
negative value when the receiver compares less, zero when the compared
bytes are equal, and a positive value when the receiver compares greater.
The sign is what is guaranteed, not a specific magnitude. The whole-array
form compares the shared prefix and then orders by length (the shorter
array is less when the shared prefix matches); the ranged form compares
exactly `count` elements.

```franko
[int32, 3] a
[int32, 3] b
a.memset(0);
b.memset(0);
if (a.memcmp(getptr(b)) == 0) {
    print("equal");
}
```

## 22.12 Byte-Array Intrinsics

The remaining intrinsics exist only for byte arrays: receivers whose element
type is `byte` (`uint8`). Both `[byte]` and `[byte, N]` (dynamic and static)
are accepted; any other element type is a compile error.

All byte offsets are zero-based. Bounds violations, use of an uninitialized
dynamic array, and other invalid accesses abort at runtime through the same
panic mechanism as array indexing and the other intrinsics. For static arrays,
constant offsets are also checked at compile time.

### `memchr` and `memmem`

Find a byte or a byte sequence inside the receiver.

Forms:

```franko
target.memchr(value)                  // search the whole array
target.memchr(value, start, count)    // search a range
target.memmem(needle)                 // search the whole array
target.memmem(needle, start, count)   // search a range
```

Rules:

- receiver element type must be `byte`;
- `value` must be `byte`;
- `needle` must be a pointer to a byte array (`ptr`/`mview`/`view` over
  `[byte]` or `[byte, N]`), an array literal, or a string literal (strings
  are already `view<[byte, N]>` values);
- `start` and `count` must be nonnegative constants fitting `uint32`, or
  nonconstant expressions of exactly type `uint32`; static ranges are
  checked at compile time;
- both return `uint32`: the **absolute** element index of the first match,
  or `0xFFFFFFFF` when there is no match;
- `memchr` on an empty range returns `0xFFFFFFFF`; an empty needle matches
  at `start`.

```franko
[byte, 5] hay
hay.memset(0);
hay[1] = 65;
hay[2] = 66;
hay[3] = 66;
print(hay.memchr(65));          // 1
print(hay.memchr(67));          // 4294967295
print(hay.memmem("BB"));        // 2
```

### Endian integer accessors

Read or write a fixed-width integer at a byte offset with an explicit byte
order.

Forms:

```franko
target.read_uint16_LE(offset)          // -> uint16
target.read_uint16_BE(offset)          // -> uint16
target.read_uint32_LE(offset)          // -> uint32
target.read_uint32_BE(offset)          // -> uint32
target.read_uint64_LE(offset)          // -> uint64
target.read_uint64_BE(offset)          // -> uint64
target.read_int16_LE(offset)           // -> int16
target.read_int16_BE(offset)           // -> int16
target.read_int32_LE(offset)           // -> int32
target.read_int32_BE(offset)           // -> int32
target.read_int64_LE(offset)           // -> int64
target.read_int64_BE(offset)           // -> int64

target.write_uint16_LE(offset, value)  // value: uint16
target.write_uint16_BE(offset, value)
target.write_uint32_LE(offset, value)  // value: uint32
target.write_uint32_BE(offset, value)
target.write_uint64_LE(offset, value)  // value: uint64
target.write_uint64_BE(offset, value)
target.write_int16_LE(offset, value)   // value: int16
target.write_int16_BE(offset, value)
target.write_int32_LE(offset, value)   // value: int32
target.write_int32_BE(offset, value)
target.write_int64_LE(offset, value)   // value: int64
target.write_int64_BE(offset, value)
```

Rules:

- receiver element type must be `byte`;
- `offset` is a byte offset and must be a nonnegative constant fitting
  `uint32`, or a nonconstant expression of exactly type `uint32`;
- the access must fit: `offset + width <= target.length()`; otherwise the
  call aborts. For static arrays, a constant offset is checked at compile
  time;
- `value` must be exactly the accessor's integer type, or a constant that
  fits it;
- `LE` stores/loads the least significant byte first, `BE` the most
  significant byte first;
- reads and writes are independent unaligned byte loads/stores, so no
  alignment requirement applies.

```franko
[byte, 8] header
header.write_uint32_LE(0, 0x12345678);
print(header.read_uint32_LE(0));   // 305419896
print(header.read_uint32_BE(0));   // 2018915346

header.write_int16_BE(4, -2);
print(header.read_int16_BE(4));    // -2
```

### Bit intrinsics

Operate on the receiver as one long little-endian bit string: bit `i` lives
in byte `i / 8` at bit position `i % 8` counted from the least significant
bit. A valid bit index satisfies `bit_idx < 8 * target.length()`.

Forms:

```franko
target.read_bit(bit_idx)               // -> byte (0 or 1)
target.write_bit(bit_idx, value)       // value: byte
target.flip_bit(bit_idx)
target.bitwise_not_mask()
target.bitwise_xor_mask(mask)          // mask: byte
target.bitwise_and_mask(mask)          // mask: byte
target.bitwise_or_mask(mask)           // mask: byte
target.shift_bits_left(bit_count)      // bit_count: uint64
target.shift_bits_right(bit_count)     // bit_count: uint64
target.count_set_bits()                // -> uint64
```

Rules:

- receiver element type must be `byte`;
- `bit_idx` and `bit_count` must be nonnegative constants fitting `uint64`,
  or nonconstant expressions of exactly type `uint64`;
- `read_bit` returns `0` or `1`; a nonzero `write_bit` value sets the bit,
  zero clears it;
- `flip_bit` toggles one bit;
- `bitwise_not_mask` toggles every bit and `bitwise_*_mask` applies the
  given byte mask to every byte of the array;
- `shift_bits_left`/`shift_bits_right` shift the whole bit string toward
  more/less significant bit positions, filling vacated bits with zero;
  shifting by at least `8 * target.length()` bits zeroes the whole array;
- `count_set_bits` returns the total number of set bits as `uint64`;
- `read_bit`, `write_bit`, and `flip_bit` abort when `bit_idx` is out of
  range. For static arrays, a constant bit index is checked at compile
  time;
- the mutating bit intrinsics need mutation rights on the receiver;
  `read_bit` and `count_set_bits` are read-only and may be called through a
  `view`.

```franko
[byte, 2] flags
flags.memset(0);
flags.write_bit(0, 1);
flags.write_bit(9, 1);
print(flags.count_set_bits());   // 2
print(flags.read_bit(1));        // 0
flags.flip_bit(1);
print(flags.read_bit(1));        // 1
flags.shift_bits_left(1);
print(flags.read_bit(10));       // 1
```

---

# 23. String Literals

Franko string literals are **read-only views of byte arrays**. A string
literal is an ordinary expression whose type is `view<[byte, N]>`
(see [Pointers](25-pointers.md)): a read-only pointer to a static byte
array of `N` UTF-8 bytes, where `N` is the literal's byte length.

```franko
"Franko"           // view<[byte, 6]>
"Hello, World!"    // view<[byte, 13]>
```

## 23.1 A String Literal Is a View Value

Each distinct literal is backed by immutable `[byte, N]` storage
holding the UTF-8 bytes, and the literal expression is a read-only view
over it. Identical literal texts share one backing store. No null
terminator is appended: `N` is exactly the number of content bytes.

Because the literal is a pointer, it must be dereferenced with `$(...)`
to reach the bytes:

```franko
print($("abc").length());   // 3
print($("abc")[0]);         // 97
```

A string literal may appear anywhere an expression may appear:

```franko
view<[byte, 3]> s = "abc";     // declaration initializer
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
`view<[byte, N]>` cannot initialize or assign an array. Copy the
bytes with the `memcpy` intrinsic instead — a string literal is a valid
`memcpy` source:

```franko
[byte, 6] buf;
buf.memcpy("hello");      // copies 5 bytes; buf[5] is untouched
print(buf[0]);            // 104
```

For dynamic arrays, size the buffer first:

```franko
[byte] buf;
buf.init($("hello").length());
buf.memcpy("hello");
```

For the same reason `[byte, ?]` cannot infer its size from a string
literal; declare the size explicitly. (`?` size inference works in
`view<[...]>` declarations with an array literal; see
[Array Literals](21-array-literals.md).)

## 23.5 Global String Views

A pointer-typed global may be initialized with a string literal (or with
`getptr`/`getmview`/`getview` over another global, or `null`):

```franko
view<[byte, 5]> GREETING = "hello";

func main() -> int32 {
    print($(GREETING).length());   // 5
    return 0;
}
```

## 23.6 Printing Strings

`print` (from `franko/io`) prints text: a string literal is a `view<[byte, N]>` value that decays to the `print(view<[uint8]>)` overload, so it is written verbatim followed by a newline. Print individual byte values by indexing:

```franko
import "franko/io"

print("hi")          // writes `hi` + newline
print($("hi")[0])    // 104
print($("hi")[1])    // 105
```

## 23.7 Empty Strings

An empty string literal `""` is not supported: a string literal is a
`view<[byte, N]>` with `N >= 1`.

---

# 24. Shorthands and Equivalences

Some Franko source forms are convenient shorthand for simpler forms. The
shorthand and its equivalent have the same meaning; the normal
declaration, assignment, array initialization, and type rules apply to
the equivalent form.

## 24.1 Declaration Initialization

This:

```franko
int x = 10;
```

is a declaration-initialization statement, not shorthand for a separate
assignment. It declares `x` and initializes it as one construct. The same
holds for the field-wise form and for `const`:

```franko
Literal lit { x = 0 }
const int32 y = 10
const Point p { x = 1 }
```

Declaration initialization is evaluated once as part of the declaration; a
`const` variable can therefore be initialized here and nowhere else. At
global scope only the existing global initializer
forms are valid (constant initializer, static address, string literal, or an
array literal through a pointer). See
[Declarations](18-declarations.md#188-const-storage).

## 24.2 Array and Aggregate Literal Values

Array literals are not assignment shorthand: they are `view<[T, N]>` values
over immutable constant storage, exactly like string literals. Their element
type is inferred from the surrounding assignment, return, or call-argument
context. See [Array Literals](21-array-literals.md).

Aggregate literals are likewise ordinary values, not statement shorthand: they
construct a temporary struct or tuple value. See
[Aggregate Literals](29-aggregate-literals.md).

## 24.3 String Literal Values

Every string literal is a read-only `view<[byte, N]>` over immutable
storage holding its UTF-8 bytes. No null terminator is added, and
identical literal texts share the same storage:

```franko
print($("ABC")[0]);
```

The literal has type `view<[byte, 3]>` and can be indexed after a
dereference. Because a string literal is a view value, it cannot
initialize or assign an array: copy the bytes with `memcpy` instead. See
[String Literals](23-string-literals.md).

## 24.4 Braceless Control-Flow Bodies

`if`, `while`, and `for` bodies may be a single statement without braces:

```franko
if (x) print(x);
while (i < 10) i = i + 1;
for (; i < 10; i = i + 1) print(i);
```

A braceless body behaves as if it were wrapped in an implicit block:

```franko
if (x) { print(x); }
while (i < 10) { i = i + 1; }
for (; i < 10; i = i + 1) { print(i); }
```

This makes braceless and braced bodies scope identically: a declaration
inside a braceless body is scoped to that implicit block and is not
visible after the `if`/`while`/`for`. See [Statements](17-statements.md).

## 24.5 For Loop Equivalence

A `for` loop is equivalent to declaration/assignment statements and a
`while` loop:

```franko
for (int32 i = 0; i < n; i = i + 1) {
    print(i)
}
```

is equivalent to:

```franko
{
    int32 i;
    i = 0;
    while (i < n) {
        print(i);
        i = i + 1;
    }
}
```

The whole loop behaves as a block, so a loop variable declared in `init`
is scoped to the loop and is not visible after it.

The `init` and `update` parts are optional header statements (every
simple-statement form except `return`). The condition is required. When
present, `update` runs at the end of every iteration, including the
last: `continue` runs it, and `break` skips it, matching C's `for`
semantics. See [Statements](17-statements.md).

The `init` and `update` statements follow the ordinary statement rules, so
declaration initialization in the header is handled normally.
See [Statements](17-statements.md).

## 24.6 Inferred Static Array Sizes (`?`)

A static array size may be written as `?` in a `view<[...]>`
declaration with an array-literal initializer:

```franko
view<[int32, ?]> v = [0, 1, 2, 3];
```

Each `?` is inferred from the element count of the corresponding literal
level, so the declaration above is `view<[int32, 4]> v`. Nested literals
infer level by level:

```franko
view<[[int32, ?], ?]> m = [[1, 2], [3, 4]];
```

is `view<[[int32, 2], 2]> m`. `?` may appear at any level of the nest,
including middle levels.

Inference rules:

- `?` is legal only in a local declaration-assignment statement
  (`view<[...]> name = literal;`) whose initializer is an array
  literal (string literals are `view<[byte, N]>` values and do not
  participate);
- the declared type must be a `view`: an array literal is a view value,
  so it never initializes an array declaration;
- when `?` is used in any dimension, the whole array nest must be static
  (no dynamic `[T]` level anywhere);
- when `?` is used in any dimension, the literal must fit exactly at
  every level: a shorter literal is an error (the literal's length is
  its type), and sibling lists at each level must have equal length
  (perfect rectangle);
- an empty array literal is invalid in general, so it cannot infer a
  size.

Misplaced `?` (parameters, return types, struct fields, globals,
pointers, non-literal initializers) is rejected. See
[Arrays](20-arrays.md) and [Array Literals](21-array-literals.md).

## 24.7 Constant Expression Evaluation

Constant expressions are evaluated at compile time in every
constant-expression position:

```franko
[int32, 0x10] xs;   // size becomes 16
int8 g = 1 + 2;          // initializer becomes 3 (globals)
struct S {                 // field default becomes 8
    int32 n = 1 << 3
}
```

Static array sizes, global initializers, struct field defaults, and
`break N` levels are reduced to concrete values. The values are then
validated in context (positive sizes, `uint32` range, initializer fit)
without folding anything again.

Evaluation failures are compile errors:

```franko
[int32, 10 / 0] b;   // invalid: Division by zero in constant expression
```

Constant expressions inside ordinary code are also folded at compile
time. See [Constant Folding](08-constant-folding.md).

## 24.8 Increment and Decrement Equivalence

Postfix increment and decrement statements are equivalent to compound
assignment:

```franko
x++;
x--;
```

are equivalent to:

```franko
x += 1;
x -= 1;
```

Every rule that applies to `+=` / `-=` (integer storage-backed lvalue
target, operand rules, single evaluation of the target) applies
identically to `x++` / `x--`. The literal `1` remains a fluid integer
constant. See [Assignment](16-assignment.md).

## 24.9 Aggregate Field Initialization

A field-wise declaration initializer is declaration initialization, not a
separate assignment statement. It initializes one ordinary field at a time,
in source order:

```franko
Literal lit { def = 1, x = 0 }
```

behaves as:

```franko
Literal lit
lit.def = 1
lit.x = 0
```

but is evaluated as declaration initialization, so it can initialize a
`const` aggregate and never invokes `OPERATOR_MOV`. An
all-unnamed tuple declaration accepts positional entries, which are
written to `_0`, `_1`, … in order and fill the first N elements:

```franko
(int32, int32) pair { 0, 1 }
```

is equivalent to:

```franko
(int32, int32) pair
pair._0 = 0
pair._1 = 1
```

A partial list such as `{ 0 }` writes only `_0`. The brace must immediately
follow the identifier; a newline ends the declaration. Duplicate field
initializers and positional lists that exceed the tuple size are rejected. Positional entries are invalid for structs and named or
mixed tuple spellings, and positional and named entries cannot be mixed. No
temporary aggregate value is created, so the form also works for pointer-only
aggregates; each entry must still obey ordinary assignment rules. See
[Aggregate Literals](29-aggregate-literals.md).

---

# 25. Pointers

Franko has a capability-based pointer system with three pointer types,
representing strict levels of permission:

| Type | Meaning | Allowed Operations |
|---|---|---|
| `ptr<T>` | Owner | Read, Write, Allocate, Free |
| `mview<T>` | Writer | Read, Write |
| `view<T>` | Reader | Read Only |

(`ptr` = pointer, `mview` = mutable view, `view` = view.) The three
types differ only in the permissions they grant; no pointer owns its
pointee automatically, and no pointer is tied to the lifetime of the
storage it refers to.

A pointer value refers to a storage location containing a value of type `T`.

Examples:

```franko
ptr<int32> p;
mview<uint8> b;
view<ptr<int32>> pp;
ptr<[int32]> ap;
```

Pointers are typed, assignable, copyable, dereferenceable, and comparable.

Pointers are not arithmetic values.

`ptr<void>`, `mview<void>`, and `view<void>` are invalid because `void`
is not an ordinary value type.

## 25.1 Creating Pointers

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

User-written `getview`/`getmview`/`getptr` require a storage-backed
lvalue. The only exception is the implicit borrow used to pass an rvalue
operand to an overloaded struct operator; it is not available to user code,
so `getview(5)` or `getview(make_x())` remains invalid. See [Struct Operator Overloading](13-operators.md#1310-struct-operator-overloading).

Const storage allows only `getview`:

```franko
const int32 x = 1
view<int32> v = getview(x)    // valid
ptr<int32> p = getptr(x)      // error
mview<int32> m = getmview(x)  // error
```

This limits a const struct receiver to `view<X> self` methods.
`$(p)` follows the pointee's own type, not the constness of the variable
holding `p`: when `p` is a const `ptr<int32>`, `$(p) = 5` is valid and
`p = ...` is not. See [Declarations](18-declarations.md#188-const-storage).

## 25.2 Dereferencing with `$`

If the operand has type `ptr<T>`, `mview<T>`, or `view<T>`, then
`$(...)` has type `T`.

`$(...)` is an lvalue. Reading through it is allowed with any pointer
permission; writing through it requires mutation rights (`ptr` or
`mview`):

```franko
$(p)[i] = x;    // array element access (mutating: needs ptr or mview)
$(v)[i];        // reading an element (any permission)
$(v).field;     // struct field access
$(v).length();  // intrinsic over the pointee
```

## 25.3 Nested Pointers

Franko supports pointers to pointers, of any permission:

```franko
view<ptr<int32>> v;   // read-only view of a ptr<int32> slot
```

## 25.4 Assignment and Implicit Downgrades

The implicit conversions for nonconstant values are pointer
conversions: the permission downgrades below over an identical
referenced type, and the static-to-dynamic array pointer view decay of
[§25.4.1](#2541-static-to-dynamic-array-pointer-views):

- `ptr<T>` implicitly casts to `mview<T>` and `view<T>`;
- `mview<T>` implicitly casts to `view<T>`;
- passing a `view<T>` where an `mview<T>` or `ptr<T>` is expected is a
  compile error.

```franko
ptr<[byte]> p = getptr(buf);
mview<[byte]> m = p;          // implicit downgrade
view<[byte]> v = p;           // implicit downgrade
view<[byte]> v2 = m;          // implicit downgrade

ptr<[byte]> q = v;            // invalid: raises permissions
```

These conversions apply to assignment, return values, and function
arguments. Every other position requires exact type equality. Raw
integers cannot be converted to pointers.

Explicit downgrades may be written with `cast` (see [Casts](12-casts.md)):

```franko
view<[byte]> v = cast<view<[byte]>>(p);
```

### 25.4.1 Static-to-Dynamic Array Pointer Views

A pointer to a **static** array implicitly converts to a pointer to a
**dynamic** array of the same element type, in the unidirectional form:

- `mview<[T, N]>` and `view<[T, N]>` implicitly (and explicitly)
  cast to `mview<[T]>` and `view<[T]>`;
- the conversion may carry a permission downgrade in the same step, so
  `ptr<[T, N]>` converts to `mview<[T]>`/`view<[T]>` and
  `mview<[T, N]>` converts to `view<[T]>`;
- `T` may itself be a static or dynamic array, but only the **outermost**
  dimension decays: `view<[[int32, 2], 3]>` converts to
  `view<[[int32, 2]]>`, never to `view<[[int32]]>`
  (the element layout viewed through the two types must stay identical);
- the reverse direction is invalid, and ownership never decays:
  `ptr<[T, N]>` cannot convert to `ptr<[T]>`.

```franko
[int32, 4] xs;
mview<[int32, 4]> m = getmview(xs);
view<[int32]> v = m;             // implicit decay
mview<[int32]> d = cast<mview<[int32]>>(m);   // explicit

print($(v).length());                   // 4
$(m)[1] = 99;                           // writes hit xs directly
```

The conversion is safe because permissions are propagated to the
result: `.init`, `.init_zero`, `.resize`, and `.uninit` are forbidden on
`mview`/`view` results (and on a decayed `ptr` — which is why `ptr` never
decays), so the storage behind the converted view is never reallocated
or freed through it. The conversion is allowed in the same positions as
permission downgrades: assignment, return values, and function arguments.
Comparing pointers still requires identical pointer types.

Decay does not copy elements or allocate storage, and it creates no
hidden array object: the converted view continues to refer to the same
array. In particular:

- returning a decayed parameter or global view is safe;
- a returned decayed view still refers to the original static storage;
- returning a decayed view of **local** array storage remains an
  ordinary frame-storage escape and is still rejected, exactly like
  returning any other pointer to a local.

Literal views refer to constant storage with static lifetime (see
[Array Literals](21-array-literals.md)), so returning or keeping one is
safe.

## 25.5 Pointer Comparison

Pointers may be compared using only the equality operators:

```franko
==
!=
```

Both operands must have identical pointer types.

Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for pointers.

Pointer comparison produces `uint8`.

## 25.6 No Pointer Arithmetic

Pointers are not integers and do not support arithmetic.

## 25.7 Pointers of Arrays

Arrays may be pointed to:

```franko
[int32] arr;
ptr<[int32]> p;

p = getptr(arr);
$(p)(10);
```

Array literals can be copied through a dereferenced array pointer when
the dereference produces a storage-backed array lvalue:

```franko
[int32, 3] xs;
ptr<[int32, 3]> p;

p = getptr(xs);

$(p).memcpy([1, 2, 3]);
```

This is equivalent to writing the elements one by one:

```franko
$(p)[0] = 1;
$(p)[1] = 2;
$(p)[2] = 3;
```

## 25.8 Permission Propagation (Nested Types)

Permissions apply to the **value** being pointed to. Because arrays and
structs are value types, permissions propagate transitively downward
through them — and they stop at pointer boundaries.

### Rule 1: Value Types Are Transitive

A `view` of a nested array locks every dimension:

```franko
view<[[byte]]> v;
// $(v)[i] reads are fine; $(v)[i][j] = x is blocked, and so is
// $(v)[i].init(...): the entire nested structure is read-only.
```

An `mview` of a nested array may write every level's elements, but the
lifecycle intrinsics are blocked at *every* level — they need allocation
rights (`ptr`), and an index or member access never leaves the region the
pointer dereference created:

```franko
mview<[[byte]]> m;
$(m)[0][1] = 5;     // allowed: element writes at every level
$(m)[0].init(2);    // blocked: init needs allocation rights (ptr)
$(m).init(2);       // blocked: the same rule applies to the outer array
```

To run lifecycle intrinsics on a nested array, reach it through a `ptr`:
either hold the outer pointer (`ptr<[[byte]]>`) or extract an
inner `ptr` (Rule 2).

### Rule 2: Pointer Boundaries (Indirection)

If a container holds pointer values, the container's permission governs
only **the pointer slots** (whether they can be overwritten). The memory
behind those pointers follows each pointer's own type:

```franko
mview<[view<[byte]>]> v;
$(v)[0] = getview(x);        // allowed: overwrite the slot
$($(v)[0])[0] = 5;           // blocked: the extracted view is read-only
```

```franko
view<[ptr<[byte]>]> v;
$(v)[0] = getptr(x);         // blocked: the outer view locks its slots
$($(v)[0])[0] = 5;           // allowed: through the inner ptr
$($(v)[0]).init(10);         // allowed: full allocation rights on the pointee
```

Summary: `view` locks down the memory it directly touches. If that
memory contains raw values (like nested arrays), those values are
locked. If it contains pointers, the pointer *variables* are locked, but
the memory they point to follows its own type rules.

These rules hold at **every** level of nesting: through an `mview`,
`memset`/`memcpy`/`memmove`, the byte/bit mutators, and element writes on an
*inner* nested array are allowed (`$(m)[0].memcpy(src)` is fine), while
`init`/`init_zero`/`resize`/`uninit` on an inner nested array are blocked
exactly like on the outermost one (`$(m)[0].init(...)` is not).

## 25.9 The null Literal

`null` is a literal of type `null`. It is valid only where a pointer of
any permission is expected:

```franko
ptr<int32> p;
p = null;               // valid

int32 x = null;       // invalid: null is only valid for pointer types
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

`print(null)` matches the `print(view<[uint8]>)` overload and prints nothing (a null view is treated as empty).

### 25.9.1 Null-Initialized Pointer Storage

Pointer storage is **guaranteed null-initialized**, unlike ordinary storage, which starts indeterminate:

```franko
ptr<int32> p;        // p is null here, guaranteed
print(p == null);       // true
```

The guarantee covers every pointer storage slot:

- pointer variables (`ptr<T>`, `mview<T>`, and `view<T>`);
- pointer-typed struct fields;
- pointer tuple elements;
- pointer elements of local static arrays;
- pointer elements of initialized dynamic arrays, after `init`, `init_zero`, or a growing `resize`.

A global static array is zero-initialized, so its pointer elements are null before `main` runs. The same rule applies recursively to pointer storage nested inside structs, tuples, and arrays.

### 25.9.2 Nullness Certification

The compiler tracks whether a pointer variable is null. The only
expressions that certify a pointer as non-null are the creation
intrinsics `getptr(...)`, `getmview(...)`, and `getview(...)`:

```franko
ptr<int32> p;            // p is null
$(p);                   // error: cannot dereference null

p = getptr(x);             // p is certified non-null
$(p);                   // valid

ptr<int32> q = p;        // q copies p's state: non-null
ptr<int32> r = q;        // r copies q's state: non-null
```

Assignment from another pointer variable copies that variable's exact state, so a null state propagates through copies and a certification propagates transitively.

Assignment from values of unknown provenance (call results, parameters, struct-field or array-element reads) yields a maybe-null state: the compiler treats such a variable as possibly null, and dereferencing it is a warning, not an error.

The check also follows pointers across function calls. If a callee always
dereferences a parameter, passing a definitely-null argument is an error and
passing a possibly-null argument is a warning. A parameter guarded with
`p != null` on the dereferencing path does not require a non-null argument.
A callee's return carries the null state of the value it returns, and a callee
that writes through a `ptr<ptr<T>>` parameter can update the caller's pointer
variable. Each function still reports its own local maybe-null warnings.
Dangling pointers are checked separately (see
[Pointer Lifetime Limitations](#2511-pointer-lifetime-limitations) and
[Memory Safety Checking](31-memory-safety-checking.md)).

### 25.9.3 Dereferencing and Warnings

Dereferencing a variable that is **definitely null** is an error.

Dereferencing a variable that is **possibly null** (null on some path, for example after an `if`/`else`) is a **warning**, not an error:

```franko
ptr<int32> p;
if (cond != 0) {
    p = getptr(x);
} else {
    p = null;
}
$(p);                   // warning: possibly null
```

Guarding with `p != null` removes the warning, because the compiler refines the variable's state inside the guard.

## 25.10 Capability Linter

To encourage safe, data-oriented design, the compiler tracks the
highest permission each `ptr`/`mview` local variable and parameter
actually exercises and produces a non-fatal **warning** recommending a
downgrade when elevated permissions go unused (global variables are not
linted: their uses span functions, so a per-variable recommendation is not
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
assignment through its region, `memset`/`memcpy`/`memmove`, the byte/bit
mutators (`write_*`, `write_bit`, `flip_bit`, `bitwise_*_mask`,
`shift_bits_*`), or use where an `mview<T>` is expected. *Read* covers
everything else, including `length()`, `memcmp`, `memchr`, `memmem`, the
`read_*` accessors, `read_bit`, and `count_set_bits`.

Because these are warnings, they do not fail the build. They exist to
passively teach developers to default to immutable, non-owning pointers
(`view` and `mview`) whenever possible, minimizing the risk of accidental
memory corruption.

## 25.11 Pointer Lifetime Limitations

Pointer lifetime is not fully tracked, but the compiler detects dangling
pointers returned from a function, stored into global or caller-owned
storage, forwarded through callees that perform such a store, and used after
an inner block or loop body has ended.

The compiler rejects a `return` of a pointer to a local variable, a
parameter, or any field/element of one, including through pointer copies,
`$(...)` aliases, and branches. It also rejects a dereference of a pointer
whose pointee is a local variable from a block that has already ended. Pointer
values are followed through function calls, so an escape performed by a
callee is reported at the call site.

A return that only *may* refer to local storage after a branch is a
warning; a definite escape is an error. Safe returns include pointer
parameters, pointers obtained from global storage or from
`getptr($(pointerParameter))`, literal-backed views, existing global
views, and decayed views of parameters or global storage. A decayed view
of local array storage is rejected as an ordinary frame-storage escape:
the view refers to the local array itself.

What remains untracked:

- pointers hidden inside returned structs or tuples;
- pointer fields inside structs or other aggregates when the escape does not
  go through a direct global or out-parameter store;
- global dynamic-array lifecycle;
- escapes that occur only through very deep or recursive call chains.

Dynamic-array buffer lifetime across `resize`/`uninit` is checked separately
(see
[Dynamic-Array Lifecycle Checking](31-memory-safety-checking.md#2910-dynamic-array-lifecycle-checking)).

## 25.12 Prefer Pool Indices to Linked Nodes

For many programs, pool allocation is a safer and faster alternative to
linked nodes built from pointers. Instead of storing a `ptr<Node> next`
link, give each live object an index from a pool allocator, store object
data in parallel payload arrays, and store links as `uint32` indices.
The pool recycles indices deterministically, and its generational
variants detect stale handles. The full API is in
[franko/pool](../library_docs/pool.md).

```franko
import "franko/io"
import "franko/pool"

func main() -> int32 {
    [int32] payload;
    [uint32] next;
    payload.init(8);
    next.init(8);

    std::SparseSetAllocator nodes;
    nodes.init(8);

    (uint32 idx, int32 err) first = nodes.new_index();
    (uint32 idx, int32 err) second = nodes.new_index();
    if (first.err != 0 || second.err != 0) {
        return 1;
    }
    uint32 a = first.idx;
    uint32 b = second.idx;

    payload[a] = 10;
    payload[b] = 20;
    next[a] = b;                // replaces a node's `next` pointer
    next[b] = 0xFFFFFFFF;       // end of the list

    uint32 i = a;
    while (i != 0xFFFFFFFF) {
        print(payload[i]);
        i = next[i];
    }

    nodes.free_index(a);
    nodes.free_index(b);
    nodes.uninit();
    next.uninit();
    payload.uninit();
    return 0;
}
```

A node handle is now the pair of its pool index and (for a generational
pool) its generation; releasing a slot invalidates stale handles instead
of leaving a dangling pointer.

---

# 26. Structs

A struct is a user-defined aggregate type with named, ordered fields:

```franko
struct Point {
    int32 x
    int32 y = 0
}

Point p;
p.x = 3;
p.y = 4;
print(p.x + p.y);
```

Structs are nominal types: two distinct struct declarations are distinct types even when their field layouts are identical (see [Type Equality](10-type-equality.md)). This chapter is the complete struct reference; running examples live in [Examples](37-examples.md).

## 26.1 Declaring a Struct

Struct declarations are top-level items, alongside global variable declarations and function declarations:

```franko
struct X {
    int32 a
    int64 b = 123
    [int32, 4] xs
}
```

Rules:

- `struct` declarations may only appear at global scope; they cannot be declared inside functions.
- Separators between fields are optional, matching block syntax: a trailing separator before `}` is not required, and `;` and newlines may be mixed freely.
- Duplicate struct names are rejected. Each struct name denotes exactly one declared type.
- Struct names live in their own namespace: a struct may share its name with a variable or a function. The compiler emits a warning for such clashes.
- By convention struct names use `CapitalLetterCamelCase`; a struct whose name starts with `__` is internal to its namespace and warns when named from outside it. See [Naming Conventions](36-naming-conventions.md).

A struct body contains field declarations, method declarations (see [Methods](27-methods.md)), and `#if` conditionals that select fields or methods. Assignments, statements, nested functions outside methods, and nested struct declarations are invalid inside a struct body.

## 26.2 Field Declarations

A field declaration uses the same `type name` syntax as a variable declaration, plus an optional constant default:

```franko
struct Buffer {
    [int32] data
    int32 size = 0
}
```

Rules:

- Field types may be: primitive integer types, enum types, static arrays (nested static arrays included), dynamic arrays, pointer types (`ptr<T>`, `mview<T>`, `view<T>`), tuple types, and other declared struct types.
- `void` is not a valid field type, and a field type naming an undeclared struct is rejected.
- Dynamic array fields are allocated explicitly with `field.init(n)` (see [Array Intrinsics](22-array-intrinsics.md)).
- Duplicate field names within a struct are rejected.
- Fields are kept in declaration order.
- Field names are ordinary identifiers; keywords cannot be field names. By convention they use `lower_case_snake_case`: a field whose name starts with `_` is private to the struct and warns when accessed from outside it. See [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md) and [Naming Conventions](36-naming-conventions.md).

## 26.3 Field Default Values

A field may declare a default value with `= constExpr`:

```franko
struct Counter {
    int32 count = 0
    uint32 limit = 100
}
```

Rules:

- Defaults must be constant expressions (`constExpr`) and are folded at compile time (see [Constant Folding](08-constant-folding.md)).
- Only primitive fields can have default values. Struct, array, and ptr fields reject `= constExpr` as a type mismatch.
- A folded default must fit the field's primitive type; an out-of-range default is rejected (for example `int8 x = 300`).

## 26.4 Using Struct Types

A struct name used in a type position is resolved against the struct namespace:

- Unknown struct names are rejected with an "unknown struct type" error.
- Field types may reference structs declared anywhere in the program, including structs declared later in the file.
- Struct types compose with the other type constructors: `Point p`, `[Point] xs`, `[Point, 4] grid`, `ptr<Point> pp`, and `[[Point, 3], 2] cells` are all valid declared types.

## 26.5 By-Value Cycles

A struct may not reach itself by value through its fields. The by-value edges are:

- a field whose type is a struct;
- a field whose type is a static array of structs (recursively, including nested static arrays);
- a field whose type is a tuple containing a struct by value (recursively through tuple elements).

Tuple element types are by-value edges for cycle checking: a struct cannot contain itself through a tuple field.

An infinite by-value chain would have no size, so such cycles are rejected at compile time:

```franko
struct Node {
    int32 v
    [Node, 4] kids    // invalid: static array of Node inside Node
}
```

A tuple edge has the same effect:

```franko
struct S {
    (int32, S) t     // invalid: tuple element S is a by-value edge
}
```

Indirection breaks the cycle and is allowed:

```franko
struct Node {
    int32 v
    ptr<Node> next        // valid: pointer edge
    [Node] kids       // valid: dynamic array edge
}
```

## 26.6 Assignability and Passing by Value

A type is **assignable** when a value of that type can participate in ordinary assignment with `=`:

- primitive integer types are assignable;
- pointer types (`ptr<T>`, `mview<T>`, `view<T>`) are assignable;
- a struct is assignable if and only if all of its fields are assignable, recursively;
- a tuple is assignable if and only if all of its elements are assignable, recursively.

Static arrays and dynamic arrays are not assignable, so a struct or tuple containing one is **pointer-only**. `void` cannot be a struct field or tuple element.

Consequences:

- **Whole-value assignment.** For an assignable struct, `a = b;` copies every field. For a pointer-only struct the built-in assignment is rejected (see [Assignment](16-assignment.md)); share the struct through a pointer (`ptr<X>` when ownership or lifecycle operations are needed, or `mview<X>`/`view<X>` for field access), or opt into custom whole-value assignment with `OPERATOR_MOV` (see [Operator Assignment](16-assignment.md#167-operator-assignment)). A value-assignable struct cannot declare `OPERATOR_MOV`; its whole-value assignment is always the built-in field copy.
- **Const structs.** A value-assignable struct may be declared `const`
  (`const Point p`). Its fields may be read, but not written. Only a
  `view<X> self` method may be called on a const struct; `ptr<X> self` and
  `mview<X> self` calls are rejected. Struct fields themselves cannot be
  `const` (`Struct fields cannot be const`). See
  [Declarations](18-declarations.md#188-const-storage).
- **Aggregate literals.** An assignable struct value can be constructed directly with `X { field = value, ... }`; the temporary requires the struct to be value-assignable. For pointer-only structs use the field-wise declaration form `X x { field = value }` instead. See [Aggregate Literals](29-aggregate-literals.md).
- **By-value parameters.** A parameter may have an assignable struct type (see [Functions](19-functions.md)); the argument is copied at the call.
- **By-value returns.** A function may return an assignable struct type.
- Diamond-shaped field graphs are fine: a struct with two fields of the same struct type is assignable as long as that type is assignable.

Arrays of structs follow the usual element rules: `[X, N]` is `memset`-able only when every field of `X` is `memset`-able (pointers excluded), and `memcpy`-able when every field is `memcpy`-able (pointers included). See [Array Intrinsics](22-array-intrinsics.md).

Structs are not integers: arithmetic, bitwise, shift, and logical operators reject whole structs, and `if`/`while` conditions reject bare struct values; `print` has no overload for a whole struct (see [Statements](17-statements.md)). The exception is the overloadable comparison operators (`==`, `!=`, `<`, `>`, `<=`, `>=`) and assignment operators (`=`, `+=`, ...), which use `OPERATOR_*` methods (see [Operators](13-operators.md#1310-struct-operator-overloading)). Operate on fields for everything else: `print(s.x)`.

## 26.7 Member Access and Field Lvalues

Member access uses dot syntax. Access chains compose with every storage form:

```franko
s.field               // local struct
$(p).field        // through a pointer
$(a.next).field   // another hop through a ptr field: deref the link first
xs[i].field           // element of a static or dynamic array
```

- `s.field` is a storage-backed lvalue exactly when its base expression is one, so field accesses can be assigned to: `s.field = v;`, `$(p).field = v;`, `xs[i].field = v;` (see [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md)).
- `getptr(s.field)` is valid and yields `ptr<T>` for the field's type `T`.
- A field may itself be an array, and array intrinsics work on fields:
  `b.data.init(4)`, `b.data.init_zero(n)`, `b.data.memset(0)`, `b.data.memcpy(getptr(other))`, `b.data[i]`, and `b.data.uninit()`.
- Struct methods also use member-call syntax (`s.increment()`); see [Methods](27-methods.md). Only struct methods and the built-in array intrinsics use member-call syntax.
- A field whose type is a tuple is accessed with the tuple's own element names; tuple elements are lvalues by the same base-expression rule. See [Tuples](28-tuples.md).

## 26.8 Initialization Semantics

- **Local declarations.** A local struct without an initializer is default-constructed: fields with defaults receive their default value; enum fields default to their enum's first variant; fields without defaults start with indeterminate values, like ordinary local variables, except pointer fields, which are null (see [Declarations](18-declarations.md)).
- **Declaration initialization.** `P b = a;` and `P c = f();` are valid for locals and copy the whole struct value as part of the declaration.
- **Aggregate literals.** `P p = P { field = value, ... };` default-constructs a temporary, assigns the named fields in source order, then assigns the whole temporary to the declaration target. Empty and partial literals are legal. The struct must be value-assignable to be constructed this way, even when it declares `OPERATOR_MOV`. See [Aggregate Literals](29-aggregate-literals.md).
- **Field-wise declaration initializers.** `P p { field = value, ... }` declares `p` and assigns each named field directly, without a temporary and without invoking `OPERATOR_MOV`. This form works for pointer-only structs.
- **Global declarations.** Global structs are statically allocated. Fields without explicit defaults start at zero (pointer fields start null, enum fields start at their enum's first variant); fields with defaults receive their default values. Global initialization happens before `main` runs.

Tuples follow the same rules for their elements: local tuple elements without a value are indeterminate, except pointer elements, which are null; global tuple storage is zero-initialized, and declaration initialization copies a whole tuple value.

## 26.9 Struct Globals

A struct-typed global is declared like any other global:

```franko
struct Point {
    int32 x = 0
    int32 y = 0
}

Point origin;
int32 scale = 2;
```

- Global variable initializers (`= constExpr`) are for primitive globals only; a struct-typed global rejects `= constExpr` as a type mismatch.
- Struct globals are declared before `main` runs; executable statements remain invalid at global scope (see [Program Structure](03-program-structure.md)).

## 26.10 Restrictions Summary

- Structs are declared only at top level; no local or nested struct declarations.
- Struct methods use member-call syntax alongside the built-in array intrinsics (see [Methods](27-methods.md)).
- No operators on whole structs except overloadable comparisons and assignments: arithmetic, bitwise, shift, and logical operators reject them, and bare struct conditions are invalid; `print` has no struct overload — print individual fields instead. Comparisons and assignments use `OPERATOR_*` methods (see [Struct Operator Overloading](13-operators.md#1310-struct-operator-overloading)).
- Aggregate literals construct assignable struct values as expressions; literal construction requires the struct to be value-assignable. Local field-wise declaration initializers initialize any struct, including pointer-only structs, one field at a time.
- Field defaults are `constExpr` on primitive fields only.
- A struct may not reach itself by value (through struct fields, tuple elements, or static array elements); `ptr<...>` and dynamic-array fields break the cycle.
- A struct or tuple with any non-assignable element is pointer-only: built-in whole-value assignment, by-value parameters, and by-value returns are rejected; use a pointer (`ptr<X>` for ownership/lifecycle, or `mview<X>`/`view<X>` for access), or declare `OPERATOR_MOV` for custom whole-value struct assignment.
- Duplicate struct names and duplicate field names are rejected.
- The complete list of current language restrictions is in [Unsupported or Restricted Features](35-unsupported-or-restricted-features.md).

---

# 27. Methods

A struct may declare methods: functions bound to the struct whose first parameter is explicitly `self` (`ptr<X> self`, `mview<X> self`, or `view<X> self`). Methods are called with member-call syntax; the receiver is passed through `getptr`/`getmview`/`getview(receiver)` matching the declared `self` type (see [Structs](26-structs.md) for fields and [Functions](19-functions.md) for free functions).

```franko
struct X {
    int32 y = 0

    func increment(ptr<X> self) -> void {
        $(self).y = $(self).y + 1
        return
    }
}

func main() -> int32 {
    X s
    s.y = 41
    s.increment()
    print(s.y)
    return 0
}
```

## 27.1 Method Declaration Syntax

A struct body contains field declarations and method declarations:

```franko
struct X {
    int32 y = 0
    func increment(ptr<X> self) -> void {
        $(self).y = $(self).y + 1
        return
    }
}
```

Rules:

- Methods are `func` declarations inside a struct body; separators between members are optional, matching field syntax.
- A method may declare any return type valid for free functions, including `void` (see [Functions](19-functions.md)).
- A method may declare additional parameters after `self`; they follow ordinary parameter rules.
- A method body follows ordinary function body rules, including return checking (see [Functions](19-functions.md)).
- Method names are ordinary identifiers; a method may not share its name with a field of the same struct, and duplicate method signatures are rejected (see [Declaration Type Validity](11-declaration-type-validity.md)).
- By convention method names use `lower_case_snake_case`. A method whose name starts with `_` is private to its struct and warns when called from outside it; see [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md) and [Naming Conventions](36-naming-conventions.md).

## 27.2 The `self` Parameter

The first parameter of every method must be literally `self`, typed
`ptr<X> self`, `mview<X> self`, or `view<X> self`, where `X` is the
enclosing struct. The permission declares what the method does with its
receiver: an owning `self` may run lifecycle operations, a mutable
`self` may write, a read-only `self` may only read (see
[Pointers](25-pointers.md)):

```franko
struct X {
    int32 y = 0
    func get(view<X> self) -> int32 {
        return $(self).y
    }
    func set(mview<X> self, int32 v) -> void {
        $(self).y = v
    }
}
```

Rules:

- A method with no parameters is rejected: `Method 'X.foo' must have 'ptr<X> self' as its first parameter (also allowed: 'mview<X> self', 'view<X> self')`.
- A method whose first parameter is not named `self` is rejected with the same diagnostic.
- A method whose `self` parameter does not have type `ptr<X>`, `mview<X>`, or `view<X>` for the enclosing struct `X` is rejected: `Method 'X.foo' has a 'self' parameter of type T; it must be ptr<X> (or mview<X> or view<X>)`. This covers by-value `X self`, non-pointer `int32 self`, and `ptr<Y> self` for a different struct `Y`.
- Only the first parameter may be named `self`: `Only the first parameter of a method may be named 'self'`.
- Method overload identity **excludes `self`'s type**: a struct may not declare two methods with the same name and the same remaining parameter types but different `self` permissions.
- `self` is an ordinary pointer variable of its declared permission inside the body: `$(self).y` and `getptr($(self).y)` behave exactly like any other pointer variable. In particular `self.y` is invalid: member access requires a struct value, so fields are reached as `$(self).y` (see [Pointers](25-pointers.md)). Writing through a `view<X> self` is rejected like any other write through a view.
- The `self` slot is **implicitly const**. Reassigning it (`self = other`) is
  rejected with `Cannot assign to const variable 'self'`. Writing
  `const self` is allowed but redundant. `const` only makes the pointer slot
  immutable; the declared `ptr`/`mview`/`view` permission still governs the
  pointee. It is not part of the method signature or overload identity.
- A const struct receiver accepts only `view<X> self` methods. The hidden
  receiver argument is `getptr`/`getmview`/`getview(receiver)` matching the
  declared `self` type, so `ptr<X> self` and `mview<X> self` calls on a const
  value fail the const-pointer rule, while `view<X> self` is allowed.
- Method names may coincide with array intrinsic names (`init`, `memset`, ...). Dispatch keys off the receiver type, not the name: a struct-typed receiver calls the method, while an array-typed receiver calls the intrinsic (see [Array Intrinsics](22-array-intrinsics.md)).

## 27.3 Method Calls and Receivers

A method is invoked with member-call syntax. The receiver is passed as
the `self` argument, using `getptr`, `getmview`, or `getview` matching
the method's declared `self` type:

```franko
s.increment()              // local struct receiver
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

## 27.4 Fields vs Methods Name Resolution

Fields and methods share member-call syntax but resolve in separate namespaces:

- `s.field` resolves as field access when `field` names a struct field.
- `s.name(args)` resolves as a method call when `name` matches a method overload for the supplied argument types.
- A method may not share its name with a field of the same struct: `Method 'X.foo' conflicts with field 'foo' of struct 'X'`.
- `s.unknown()` is rejected as `No matching overload for method call 'X.unknown(...)'`, which is also what keeps the historical `member_call_non_intrinsic` rejection for genuinely unknown members.

## 27.5 Overloading

Methods overload exactly like free functions: overload identity is name plus ordered parameter types; return type and parameter names are not part of identity (see [Functions](19-functions.md)).

```franko
struct Counter {
    int32 n = 0
    func add(ptr<Counter> self, int32 v) -> void {
        $(self).n = $(self).n + v
        return
    }
    func add(ptr<Counter> self, int32 a, int32 b) -> void {
        $(self).n = $(self).n + a + b
        return
    }
}
```

Rules:

- The implicit `self` parameter (whatever its permission) does not distinguish overloads; overloads must differ in their user parameters.
- Duplicate method signatures are rejected: `Duplicate declaration of method 'X.foo(...)' in struct 'X'`.
- Ambiguous arguments are rejected like free-function ambiguity: `Ambiguous call to overloaded method 'X.foo(...)': multiple overloads match equally well (argument constants fit several signatures, or arguments rely on the same number of pointer permission downgrades)`.

## 27.6 `self` Is a Reserved Keyword

`self` is a lexer keyword, reserved everywhere:

```franko
int32 self = 0          // invalid: parse error
func self() -> void {}    // invalid: parse error
struct self {}            // invalid: parse error
```

Rules:

- `self` cannot name a variable, parameter (except a method `self` parameter), field, function, struct, or method: ordinary declarations use `IDENTIFIER` and fail to parse on `self`.
- A free-function parameter named `self` parses (parameters accept `self` so method declarations share the rule) but is rejected semantically: `'self' is only valid as the first parameter of a struct method`.
- `self` as an expression (`self`, `getptr(self)`, `$(self)`) is valid only inside a method body, where it resolves to the `self` parameter. Outside a method it is rejected: `keyword 'self' is only valid inside a method body`.
- `x.self` parses (member names accept keywords) but always fails resolution. A method named `print` is an ordinary method like any other.

## 27.7 Scope Inside Method Bodies

A method body scopes exactly like a free-function body, plus the `self` parameter:

- Parameters (including `self`) are ordinary locals in the method's initial scope; locals may shadow field names (`int32 y` inside `increment` is unrelated to `$(self).y`).
- A method may call other methods on the same object only through an explicit receiver: `$(self).other()`.
- `return` is checked against the enclosing method, so void/non-void return rules apply unchanged (see [Functions](19-functions.md)).
- Name lookup inside a method follows ordinary lexical plus namespace rules; there is no implicit `this` field scope (see [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md)).

## 27.8 Methods and Pointer Checking

`self` is a pointer parameter with the declared permission:

- The receiver is a storage-backed lvalue, and `self` is non-null at method entry, so dereferencing it without a null guard is valid and produces no warning. Reassigning `self` is rejected by the implicit const rule.
- Pointer dereferences inside methods are tracked normally (see [Memory Safety Checking](31-memory-safety-checking.md)).

## 27.9 Restrictions Summary

- Methods are declared only inside struct bodies; no free-standing method declarations and no nested struct declarations.
- Every method must have `ptr<X> self`, `mview<X> self`, or `view<X> self` as its first parameter; only the first parameter may be named `self`.
- `self` is a reserved keyword; `self.y` is invalid (write `$(self).y`).
- The `self` slot is implicitly const; reassigning it is an error.
- Receivers must be struct-valued storage-backed lvalues; `ptr<X>` receivers require explicit `$(p).method(...)`.
- Methods are never callable as free functions; there is no implicit `this` field access.
- A method name may coincide with an array intrinsic name; struct receivers dispatch to the method while array receivers dispatch to the intrinsic.
- No method pointers, no virtual dispatch, no inheritance: methods behave like functions with an explicit receiver.

## 27.10 Operator Methods

A struct may declare operator methods using the reserved names:

```
OPERATOR_EQ OPERATOR_NEQ OPERATOR_GT OPERATOR_LT OPERATOR_GTEQ OPERATOR_LTEQ
OPERATOR_MOV OPERATOR_ADDMOV OPERATOR_SUBMOV OPERATOR_MULMOV OPERATOR_DIVMOV
OPERATOR_MODMOV OPERATOR_ORMOV OPERATOR_ANDMOV OPERATOR_XORMOV
```

Comparison methods must have exactly this shape:

```franko
func OPERATOR_EQ(view<X> self, view<X> other) -> uint8
```

Assignment methods must have exactly this shape:

```franko
func OPERATOR_MOV(ptr<X> self, view<T> other) -> void
```

Rules:

- Only struct methods may use these names. A free function with an
  `OPERATOR_*` name is rejected.
- An invalid operator method signature is a compile error at the
  declaration even if the operator is never used.
- Comparison methods require two parameters, the first named `self` and
  typed `view<X>`, the second typed `view<X>` and not named `self`, and
  return `uint8`.
- Assignment methods require two parameters, the first named `self` and
  typed `ptr<X>`, the second a `view<T>` for any non-void `T` and not
  named `self`, and return `void`.
- Each operator has its own method; there is no implicit `!=` from
  `==`, and no implicit compound form from a plain operator.
- Operator methods are still ordinary methods and remain callable with
  member-call syntax: `a.OPERATOR_EQ(getview(b))`.

Operator syntax is shorthand for a method call:

```franko
a == b       // a.OPERATOR_EQ(getview(b))
a = b        // a.OPERATOR_MOV(getview(b))
a += b       // a.OPERATOR_ADDMOV(getview(b))
```

Rvalue operands are borrowed for the duration of the full expression (see
[Operators](13-operators.md#1310-struct-operator-overloading)).

The `other` parameter is call-scoped: it may only be used during the operator
call. Storing it into caller-owned or global storage is rejected; storing it
in a local variable is allowed because the local dies before the operator
call returns (see
[Memory Safety Checking](31-memory-safety-checking.md#299-dangling-pointer-checking)).

---

# 28. Tuples

A tuple is an ordered aggregate of two or more values. Tuples are useful for
returning several values from a function or grouping a fixed set of related
fields without declaring a named struct.

```franko
(int32 val, int32 err) result
(int32, int32) pair
(uint32 index, int32 error) allocated
```

Tuple element names are optional. A tuple without names uses `_0`, `_1`, and
so on.

---

## 28.1 Tuple Syntax

A tuple type is a parenthesized, comma-separated list of element types:

```franko
(int32, int32)
(int32 val, int32 err)
(int32 val, uint8)
```

Rules:

- at least two elements are required; `(int32)` is not a tuple type;
- each element has a type;
- each element may carry an optional declaration-site name;
- tuple types may appear wherever an ordinary declared type is allowed;
- `void` is not a valid tuple element type.

A tuple declaration uses the tuple type followed by the variable name:

```franko
(int32 val, int32 err) res
(int32, int32) lazy
(uint hello, int world) a
```

A tuple literal spells the tuple type followed by a braced field list:

```franko
(int32, int32) pair = (int32, int32) { _0 = 1, _1 = 2 }
(int32 first, int32 second) named =
        (int32 x, int32 y) { x = 1, y = 2 }
```

The literal type spelling must be all-named or all-unnamed; a mixed spelling
such as `(int32, int32 y) { _0 = 1, y = 2 }` is invalid. Named entries use
the spelling's names. An all-unnamed literal also accepts positional entries,
which fill the first N elements in order and may be partial:

```franko
(int32, int32) pair = (int32, int32) { 0, 1 }
(int32, int32) partial = (int32, int32) { 0 }
```

Positional and named entries cannot be mixed, positional entries are invalid
for named tuple spellings, and the positional count may not exceed the tuple
size. Order is free for named entries, partial and empty literals are legal,
and omitted pointer elements are null. Constructing a tuple literal requires
the tuple to be value-assignable. See
[Aggregate Literals](29-aggregate-literals.md).

A bare parenthesized value list such as `(1, 2)` is not a tuple expression:
the type must be spelled and entries must be named or positional.

---

## 28.2 Element Names

Element names are aliases for one declaration or type use. They are not part
of the tuple type.

```franko
(int32 val, int32 err) res
res.val = 1
res.err = 2

(int32, int32) lazy
lazy._0 = 3
lazy._1 = 4
```

Unnamed elements receive the effective names `_0`, `_1`, and so on. Mixed
forms are allowed; `(int32 val, uint8)` gives the names `val` and `_1`.

The same structural tuple type may be written with different names:

```franko
(uint hello, int world) a
(uint different, int name) b
b = a
```

`a` and `b` are the same tuple type because their ordered element types are
the same. `b.different` and `b.name` are valid for `b`; `b.hello` is not,
because `b`'s declaration did not declare that alias.

---

## 28.3 Type Identity

Tuple identity is structural:

- the number of elements matters;
- the ordered element types matter;
- element names do not matter.

```text
(int32, int32)              == (int32 val, int32 err)
(int32, int32)              == (int32 left, int32 right)
(int32, int32)              == (int32, int32)

(int32, uint32)             != (uint32, int32)
(int32, int32)              != (int32, int32, int32)
(int32, [int32, 3])         == (int32, [int32, 3])
```

Nested tuple types compare recursively by the same rule. See
[Type Equality](10-type-equality.md).

---

## 28.4 Declarations and Initialization

A local tuple declaration without an initializer follows the ordinary local
initialization rules:

```franko
(int32, int32) pair
pair._0 = 1
pair._1 = 2
```

- local tuple elements without a value are indeterminate until assigned, except pointer elements, which are null;
- global tuple storage is zero-initialized before `main` runs;
- tuple values are produced by declarations, element writes, aggregate
  literals, whole-tuple assignment, and function returns.

A tuple literal can initialize a tuple directly:

```franko
(int32, int32) pair = (int32, int32) { _1 = 2, _0 = 1 }
```

A local tuple declaration can also use the field-wise form:

```franko
(int32 first, int32 second) pair { first = 1, second = 2 }
(int32, int32) positional { 0, 1 }
(int32, int32) partial { 0 }
```

The positional field-wise form is available only for all-unnamed tuple
declarations. See [Aggregate Literals](29-aggregate-literals.md) for both
forms.

Declaration initialization works normally:

```franko
(int32, int32) x = f()
```

is shorthand for:

```franko
(int32, int32) x
x = f()
```

The right-hand side must be a tuple of the same structural type.

---

## 28.5 Element Access and Lvalues

Tuple elements are accessed with dot syntax:

```franko
res.val
res.err
lazy._0
```

A tuple element is a storage-backed lvalue when its base is storage-backed.
The same rule used for struct fields applies:

```franko
res.val = 1
lazy._0 = 3
getptr(res.val)
getview(lazy._1)
```

Tuple element access composes with the ordinary access chains:

```franko
$(ptrToTuple)._0
arrayOfTuples[i].err
```

---

## 28.6 Assignment

Whole-tuple assignment uses the built-in aggregate assignment rule:

```franko
(int32 val, int32 err) a
(int32 left, int32 right) b

b = a
```

The ordered element types must match exactly; names are irrelevant.

A tuple may be declared `const` when every element is value-assignable:

```franko
const (int32, int32) pair = (int32, int32) { 1, 2 }
const (int32, int32) defaults
```

The const rules are the usual ones: no later assignment, no mutable pointer
through `getptr`/`getmview`, and `getview` only. A tuple containing an array
is pointer-only and cannot be `const`. See
[Declarations](18-declarations.md#188-const-storage).

A tuple is assignable as a whole only when every element is an assignable
value type:

- primitive integer;
- enum;
- pointer;
- struct whose fields are all assignable;
- another assignable tuple.

A tuple containing a static array or dynamic array is pointer-only:

```franko
(int32, [int32, 3]) a
(int32, [int32, 3]) b
a = b          // invalid: non-assignable element
```

For a pointer-only tuple, assign individual elements or pass the tuple through
a pointer.

Tuples cannot declare `OPERATOR_MOV` or any other operator method, so
whole-tuple assignment always uses the built-in rule.

---

## 28.7 Functions, Parameters, and Returns

A tuple can be passed by value or returned when every element is an
assignable value type.

```franko
func swap((int32 x, int32 y) pair) -> (int32 left, int32 right) {
    return (int32 left, int32 right) {
        left = pair.y,
        right = pair.x
    }
}
```

A tuple containing a static array or dynamic array cannot be passed by value
or returned by value. Use a pointer to the tuple instead:

```franko
(int32, [int32, 3]) value
ptr<(int32, [int32, 3])> p = getptr(value)
```

Tuple element names are not part of the function signature. These two
declarations have the same parameter type and are duplicates:

```franko
func f((int32 a, int32 b) x) -> int32 { ... }
func f((int32 c, int32 d) y) -> int32 { ... }
```

---

## 28.8 Composition

Tuples compose with the other type constructors:

```franko
((int32, int32) inner, int32 outer) nested
[(int32, int32), 4] table
ptr<(int32, int32)> tuplePtr
struct Wrapper {
    (int32, int32) pair
}
```

Rules:

- tuples may nest;
- a tuple may contain structs;
- a tuple may contain enums;
- a tuple may be an array element or a pointer referent;
- a struct may have a tuple field;
- a tuple element may itself be a static or dynamic array, but then the tuple
  is pointer-only.

A struct that contains a tuple by value follows the ordinary struct
by-value-cycle rule: a struct may not reach itself through a tuple element.

---

## 28.9 Compile-Time Reflection

Tuple types participate in the normal reflection predicates. A tuple is:

- assignable by value iff every element is assignable;
- value-assignable iff every element is value-assignable;
- memset-safe iff every element is memset-safe;
- memcpy-safe iff every element is memcpy-safe;
- a type with a default value iff at least one element's type has a default.

Tuples have no operator methods, so `#is_matchable` and `#is_comparable`
return `0` for a tuple. `#is_assignable` is built-in element
assignability for tuples.

```franko
(int32, int32) pair
print(#is_value_assignable(pair))      // 1
print(#has_default_value(pair))        // 0
print(#what_type(pair))                 // (int32,int32)
print(#is_same_type(pair, (int32 val, int32 err))) // 1
```

`#what_type` prints the canonical tuple form `(int32,int32)` with element
names removed and no spaces after commas. See [Compile-Time Reflection](34-compile-time-reflection.md).

---

## 28.10 Restrictions

Tuples are deliberately limited to ordinary aggregate values:

- tuple literals spell the tuple type and use named entries or, for
  all-unnamed spellings, positional entries; a bare parenthesized list such
  as `(1, 2)` is not an expression;
- positional entries are rejected for named/mixed tuple spellings and for
  struct literals; positional and named entries cannot be mixed;
- no destructuring declarations or tuple-pattern parameters;
- no tuple methods, including intrinsic-named calls such as `t.length()`;
- no tuple operators, including `OPERATOR_*` overloads;
- no tuple casts (`cast<(int32, int32)>(x)` is rejected);
- no named tuple type aliases; write the tuple type at each declaration or
  signature;
- at least two elements are required;
- a tuple containing a static array or dynamic array is pointer-only.

Tuple values are produced by declarations, element writes, aggregate
literals, whole-tuple assignment, and function returns.

---

## 28.11 Complete Example

```franko
import "franko/io"

func split((int32 left, int32 right) pair) -> (int32 first, int32 second) {
    return (int32 first, int32 second) {
        first = pair.left,
        second = pair.right
    }
}

func main() -> int32 {
    (int32 x, int32 y) pair = (int32 x, int32 y) { x = 10, y = 20 }

    (int32 first, int32 second) result = split(pair)
    print(result.first)
    print(result.second)
    print(#what_type(pair))
    return 0
}
```

Output:

```text
10
20
(int32,int32)
```

---

# 29. Aggregate Literals

An **aggregate literal** constructs a struct or tuple value directly in an
expression. Franko has two aggregate literal forms: a struct literal and a
tuple literal. It also has a separate, local-only **field-wise declaration
initializer** that assigns individual fields of a newly declared aggregate
without constructing a temporary value.

```franko
struct Literal {
    int32 x
    int32 def = 2
}

Literal lit = Literal { x = 0, def = 1 }
```

Aggregate literals are expressions, but they are only valid in the specific
value-consuming contexts listed in [Allowed Expression Contexts](#296-allowed-expression-contexts).
They are local-only: no global aggregate initializers exist.

## 29.1 Struct Literal Expressions

A struct literal spells the struct type followed by a braced list of named
field initializers:

```franko
Literal { x = 0, def = 1 }
```

Rules:

- the literal creates a temporary `Literal` value;
- fields are always named; positional initialization such as
  `Literal { 0, 1 }` is invalid;
- field order is free;
- duplicate field names are errors;
- unknown field names are errors;
- omitted fields use the same default construction as a local declaration:
  - fields with defaults receive their default value;
  - pointer fields are null;
  - omitted fields without defaults remain indeterminate;
- `Literal {}` is legal and default-constructs the temporary;
- field initializer expressions are evaluated in source order;
- construction itself never calls `OPERATOR_MOV`;
- the temporary is created only if `Literal` is value-assignable.

Examples:

```franko
Literal lit = Literal { x = 0, def = 1 }   // legal
Literal lit = Literal { def = 1, x = 0 }   // legal: order free
Literal lit = Literal { x = 0 }            // legal: def gets default 2
Literal lit = Literal { def = 0 }          // legal: x remains uninitialized
Literal lit = Literal {}                   // legal: defaults applied
```

## 29.2 Tuple Literal Expressions

A tuple literal spells the tuple type followed by a braced list of named
element initializers:

```franko
(int32 first, int32 second) tup = (int32, int32) { _0 = 1, _1 = 2 }
(int32 first, int32 second) tup = (int32 x, int32 y) { x = 1, y = 2 }
```

Rules:

- the literal type spelling must be all-named or all-unnamed:
  - `(int32 x, int32 y) { x = ..., y = ... }` is legal;
  - `(int32, int32) { _0 = ..., _1 = ... }` is legal;
  - `(int32, int32 y) { _0 = ..., y = ... }` is illegal;
- when the literal type is all-named, initializer labels must be those names;
- when the literal type is all-unnamed, labels must be `_0`, `_1`, …, **or**
  every entry may be positional: `(int32, int32) { 0, 1 }`;
- positional entries fill `_0`, `_1`, … in source order;
- a positional list may be partial and fills the first N elements:
  `(int32, int32) { 0 }` initializes `_0` only;
- a positional list may not have more entries than the tuple has elements;
- positional and named entries cannot be mixed in one tuple initializer;
- positional entries are invalid for struct literals and named tuple
  spellings;
- tuple names are not part of tuple identity, so the resulting value assigns
  to any structurally identical tuple type;
- the tuple must be value-assignable; a tuple containing an array cannot be
  constructed as a literal;
- partial initialization is legal:
  - omitted pointer elements are null;
  - omitted non-pointer elements are indeterminate;
- `(int32, int32) {}` is legal and default-constructs the temporary tuple;
- a bare `(1, 2)` is not a tuple expression: the type must be spelled;
- `(uint32, int32) { 20, -1 }` is a legal all-unnamed positional literal;
- `(int32 x, int32 y) { 1, 2 }` is invalid: positional entries are not
  allowed for a named tuple spelling.

Examples:

```franko
(int32 first, int32 second) tup = (int32, int32) { _0 = 1, _1 = 2 }        // legal
(int32 first, int32 second) tup = (int32 x, int32 y) { x = 1, y = 2 }      // legal
(int32 first, int32 second) tup = (int32, int32 y) { _0 = 1, y = 2 }       // illegal
(int32 first, int32 second) tup = (int32, int32) { _1 = 2, _0 = 1 }        // legal: order free
(int32 first, int32 second) tup = (int32, int32) { _0 = 1 }                // legal: partial
(int32, int32) empty = (int32, int32) {}                                    // legal

(int32, int32) positional = (int32, int32) { 0, 1 }    // legal: _0 = 0, _1 = 1
(int32, int32) partial = (int32, int32) { 0 }          // legal: _0 = 0 only
(int32 x, int32 y) named = (int32 x, int32 y) { 0, 1 } // illegal: named spelling
(int32, int32) mixed = (int32, int32) { 0, _1 = 1 }    // illegal: mixed entries
(int32, int32) too_many = (int32, int32) { 0, 1, 2 }   // illegal: too many entries
```

Tuple literal element names follow the declaration-site alias rules of
[Tuples](28-tuples.md): the literal's own spelling determines the labels it
accepts, while the resulting value still has the same structural tuple type
as a differently named declaration.

## 29.3 Field-Wise Declaration Initializers

A local declaration may initialize individual fields directly by placing a
braced field list immediately after the declared name:

```franko
Literal lit { x = 0, def = 1 }
Buffer b { cap = 20 }
(int32 first, int32 second) tup { first = 1, second = 2 }
(int32, int32) pair { 0, 1 }
(int32, int32) partial { 0 }
```

This is declaration initialization that behaves as one ordinary field
assignment per named field:

```franko
Literal lit
lit.x = 0
lit.def = 1
```

Rules:

- only struct and tuple declarations support this form;
- struct fields are always named;
- named tuple elements use their declared names;
- an all-unnamed tuple declaration may use positional entries instead:
  `(int32, int32) pair { 0, 1 }`;
- positional entries fill the first N elements and may be partial:
  `(int32, int32) pair { 0 }` initializes `_0` only;
- positional and named tuple entries cannot be mixed;
- positional entries may not exceed the tuple element count;
- field order is free;
- duplicate field names are errors;
- unknown field names are errors;
- omitted fields keep the declaration's default-constructed state;
- no temporary aggregate value is created;
- no `OPERATOR_MOV` is invoked;
- each named field is assigned directly using ordinary assignment rules;
- this form works for pointer-only aggregates;
- `Buffer b {}` is legal and equivalent to `Buffer b`;
- this form is local-only.

The brace must follow the identifier immediately. A newline before the brace
ends the declaration, so a following block is not consumed:

```franko
Literal lit
{
    // an ordinary block statement, not a field initializer
}
```

Because the field-wise form uses ordinary assignment, an array field still
cannot be assigned from another array or a literal:

```franko
struct Buffer {
    int32 cap
    [byte] buf
}

[byte] existing_arr

Buffer b { cap = 20 }                 // legal
Buffer b { cap = 20, buf = existing_arr }   // illegal: arrays are not assignable
```

## 29.4 Default Construction and Partial Initialization

Struct literal temporary construction is exactly local declaration
construction:

- fields with defaults receive their default value;
- pointer fields are null;
- omitted no-default non-pointer fields are indeterminate;
- named field initializer expressions are evaluated in source order.

Tuple literals have no field defaults:

- omitted pointer elements are null;
- omitted non-pointer elements are indeterminate;
- named element initializer expressions are evaluated in source order.

The field-wise declaration form also evaluates its named field expressions in
source order, but only after the declaration itself has default-constructed
the aggregate.

Reading an omitted indeterminate field or element before assigning it is
undefined behavior, exactly like reading any other uninitialized local.

## 29.5 Value Assignability

A type is **value-assignable** when:

- it is a primitive integer;
- it is an enum;
- it is a pointer;
- it is a struct whose fields are all value-assignable, recursively;
- it is a tuple whose elements are all value-assignable, recursively.

Rules:

- struct literal construction requires the struct to be value-assignable;
- tuple literal construction requires the tuple to be value-assignable;
- field-wise declaration initialization does not require the aggregate
  itself to be value-assignable; each named field only needs to be
  assignable under ordinary assignment rules.

Consequences:

```franko
struct Buffer {
    int32 cap
    [byte] buf
}

Buffer b { cap = 20 }                 // legal: field-wise
Buffer b = Buffer { cap = 20 }        // illegal: Buffer is not value-assignable
Buffer b { cap = 20, buf = arr }      // illegal: arrays are not assignable
Buffer b = existing_buffer            // may use OPERATOR_MOV if declared
```

`#is_value_assignable` is exactly the predicate aggregate literal
construction uses. It reports `0` for arrays and for structs or tuples with a
non-value-assignable part.

## 29.6 Allowed Expression Contexts

An aggregate literal expression is only valid as:

- the entire right-hand side of an assignment;
- the entire right-hand side of a declaration initializer;
- the entire return expression;
- an entire function or method call argument;
- an entire field value inside another aggregate literal.

It is not a general subexpression.

Valid:

```franko
Literal lit = Literal { x = 1 }
Literal lit
lit = Literal { x = 2 }
return Literal { x = 3 }
f(Literal { x = 4 })
Outer o = Outer { inner = Inner { x = 5 } }
```

Invalid:

```franko
Literal { x = 1 }.x       // illegal: literal is not a member-access base
Literal { x = 1 }         // illegal: standalone expression statement
1 + Literal { x = 1 }     // illegal: operand
cast<int32>(Literal {})   // illegal: cast operand
getview(Literal {})       // illegal: creation-intrinsic operand
```

The sole purpose of aggregate literals is constructing a value to assign,
return, or pass by value. Nested literals are allowed only as entire field
values of another aggregate literal.

## 29.7 Evaluation Order

Field initializer expressions are evaluated in the order they are written,
not in field declaration order. Field order is otherwise free.

```franko
Literal a = Literal { def = first(), x = second() }
// first() runs before second()
```

Nested aggregate literals evaluate their own fields in source order at the
point where the enclosing field value is evaluated.

## 29.8 `OPERATOR_MOV` Interaction

A struct that is value-assignable cannot declare `OPERATOR_MOV`; its
whole-value assignment is always the built-in field copy. Because aggregate
literal construction requires the aggregate to be value-assignable, this is
exactly the set of structs that can appear as a literal. The declaration

```franko
Literal lit = Literal { x = 0, def = 1 }
```

therefore means:

```franko
Literal lit                 // default-construct lit: defaults applied, pointers null

Literal temp                // temporary literal
temp.x = 0                  // explicit fields, source order
temp.def = 1

lit = temp                  // built-in whole-value assignment: field copy
```

Pointer-only structs cannot be constructed as literal values at all. They use
the field-wise declaration form, and may use `OPERATOR_MOV` only for an
ordinary non-literal whole-value assignment:

```franko
struct Buffer {
    int32 cap
    [byte] buf

    func OPERATOR_MOV(ptr<Buffer> self, view<Buffer> other) -> void {
        // custom copy
        return
    }
}

Buffer b { cap = 20 }                 // field-wise, no temporary
Buffer source { cap = 10 }            // field-wise, no temporary
Buffer copy = source                  // ordinary assignment: calls OPERATOR_MOV
Buffer bad = Buffer { cap = 20 }      // error: Buffer is not value-assignable
```

Truth table:

| Construct | Temporary aggregate? | Requires aggregate value-assignable? | Uses `OPERATOR_MOV`? |
|---|---|---|---|
| `Literal lit = Literal { x = 0 }` | yes | yes | no — value-assignable structs cannot declare it; built-in field copy |
| `Literal lit { x = 0 }` | no | no | no — direct field assignments |
| `Buffer b { cap = 20 }` | no | no | no |
| `Buffer b = Buffer { cap = 20 }` | yes | yes → **error** | irrelevant; the literal is already illegal |
| `Buffer b = existing_buffer` | no | depends on the assignment rule | can use `OPERATOR_MOV` if declared/applicable |

Const aggregates follow the same forms:

```franko
const Literal lit = Literal { x = 1 }   // one-time const initialization
const Literal fieldwise { x = 2 }       // field-wise const initialization
```

See [Declarations](18-declarations.md#188-const-storage).

## 29.9 Pointer-Only Aggregates and Out-Parameters

A struct or tuple with an array field or element is pointer-only: it has no
built-in whole-value assignment and cannot be constructed as an aggregate
literal value. Use the field-wise declaration form for local initialization:

```franko
struct Buffer {
    int32 cap
    [byte] buf
}

Buffer b { cap = 20 }                 // legal: direct per-field initialization
```

A pointer-only aggregate also cannot be a return type. A function that would
return one is rejected with guidance to return a pointer or use an
out-parameter:

```franko
func foo() -> Buffer { ... }          // error at declaration

func foo(ptr<Buffer> outbuf) -> int32 {
    // do something through outbuf
    return 0
}
```

The same rule applies to a tuple containing an array:

```franko
func make() -> (int32, [int32, 3]) { ... }     // error at declaration
```

## 29.10 Global-Scope Restriction

Aggregate literals and field-wise declaration initializers are local-only.
Global scope remains declaration-only, and its initializer forms are
unchanged: constant expressions, statically representable addresses, string
literals, and array literals.

Invalid:

```franko
Literal global_lit = Literal { x = 1 }     // invalid: global aggregate literal
Literal global_lit { x = 1 }               // invalid: global field initializer
```

Initialize a global aggregate in an initializer function called from `main`,
or use the ordinary global forms where they apply.

## 29.11 Diagnostics and Invalid Examples

Required diagnostics include:

- duplicate aggregate field initializer;
- unknown struct field;
- unknown tuple element;
- tuple literal type mixing names and default names;
- positional entries on a struct literal or a named/mixed tuple spelling;
- positional entries exceeding the tuple element count;
- positional and named tuple entries mixed in one initializer;
- aggregate literal of non-value-assignable type;
- aggregate literal used outside an allowed context;
- non-value-assignable return type with pointer/out-parameter guidance;
- field-wise initializer on a non-struct/non-tuple declaration;
- field-wise assignment of an array field;
- global aggregate literal or global field initializer rejection.

Examples:

```franko
Literal { x = 1 }.x                         // aggregate literal outside an allowed context
Literal { x = 1 }                           // aggregate literal outside an allowed context
1 + Literal { x = 1 }                       // aggregate literal outside an allowed context

Buffer b = Buffer { cap = 20 }              // non-value-assignable literal

(int32, int32 y) t = (int32, int32 y) {
    _0 = 1,
    y = 2
}                                           // mixed tuple literal names

(int32, int32) t = (int32, int32) { _0 = 1, _2 = 2 }   // unknown tuple element
(int32, int32) t = (int32, int32) { _0 = 1, _0 = 2 }   // duplicate tuple element

(int32 x, int32 y) t = (int32 x, int32 y) { 0, 1 }        // positional named-tuple entry
(int32, int32) t = (int32, int32) { 0, 1, 2 }             // too many positional entries
(int32, int32) t = (int32, int32) { 0, _1 = 1 }           // mixed entries
int32 x { field = 1 }                       // field initializer requires a struct or tuple declaration
```

## 29.12 Complete Example

```franko
import "franko/io"

struct Literal {
    int32 x
    int32 def = 2
}

struct Inner {
    int32 value = 7
}

struct Outer {
    Inner inner
    int32 ok = 1
}

struct Buffer {
    int32 cap
    [byte] buf
}

func make_literal() -> Literal {
    return Literal { x = 4, def = 5 }
}

func main() -> int32 {
    Literal a = Literal { def = 1, x = 0 }
    Literal b { def = 3, x = 2 }
    Literal c = make_literal()

    (int32 left, int32 right) pair = (int32, int32) { _1 = 2, _0 = 1 }
    (int32, int32) positional = (int32, int32) { 5, 6 }
    (int32, int32) partial { 7 }
    (int32 x, int32 y) named = (int32 x, int32 y) { x = 3, y = 4 }

    Outer outer = Outer { inner = Inner { value = 42 }, ok = 2 }
    Buffer buf { cap = 20 }

    print(a.x + a.def)              // 1
    print(b.x + b.def)              // 5
    print(c.x + c.def)              // 9
    print(pair.left + pair.right)   // 3
    print(positional._0 + positional._1)   // 11
    print(partial._0)               // 7
    print(named.x + named.y)        // 7
    print(outer.inner.value + outer.ok)   // 44
    print(buf.cap)                  // 20

    return 0
}
```

---

# 30. Enums

An enum is a nominal type backed by one of the eight primitive integer
types. Enums model logical states, not raw numbers: they support
equality, ordering, and assignment, but no arithmetic or bitwise
operations and no implicit conversions to or from integers.

```franko
enum<uint8> AiState {
    sleeping,
    hunting,
    attacking,
    dead
}
```

Enums are top-level items, valid at global scope or inside a namespace.
They are not valid inside structs. See [Program Structure](03-program-structure.md).

## 30.1 Declaration Syntax

Form 1 — all explicit values:

```franko
enum<int32> Types {
    type1 = 1,
    type2 = 10,
    type3 = 20
}
```

Form 2 — implicit auto-increment:

```franko
enum<int32> States {
    state1,   // 0
    state2,   // 1
    state3    // 2
}
```

Rules:

- The backing type is required and must be one of the eight primitive
  integer types. Arrays, pointers, and structs are invalid.
- Every variant identifier must be unique.
- Every variant must evaluate to a strictly unique underlying integer
  value.
- An enum must either provide an explicit `= constExpr` for **every**
  variant or for **none**; mixing explicit and implicit values is a
  compile error.
- Explicit values must be compile-time constant expressions and must
  fit the declared backing type.
- With no explicit values, the first variant is `0` and each subsequent
  variant increments the previous value by `1`.
- Implicit auto-increment wraps using standard two's-complement
  arithmetic when it exceeds the backing type's maximum:
  - signed: `2147483647 + 1` wraps to `-2147483648` and remains legal
    if unique;
  - unsigned: `255 + 1` wraps to `0`, which is a duplicate if `0` was
    already used.
- Variants are accessed through namespace resolution on the enum type
  name:

```franko
Types::type1
States::state2
```

## 30.2 Default Value

The first declared variant is the enum's absolute default value. Enums
never start as uninitialized or indeterminate memory. An enum variable may be
`const` (`const Status s = Status::alive` or `const Status s`), because enum
types are value-assignable; the const rules then forbid later assignment and
mutable pointer creation. See
[Declarations](18-declarations.md#188-const-storage).

- Local variables: `Types t;` initializes to the first variant.
- Global variables: `Types g;` initializes to the first variant before
  `main` runs, even when the backing value is not `0`.
- Struct fields: a struct containing an enum gains a default value for
  that field.
- Dynamic arrays: `arr.init(n)` and `arr.init_zero(n)` populate every
  element with the first variant.
- `init_zero` returns byte-zeroed memory, but explicitly
  default-constructs elements whose type has a default value. The zero
  bytes are safely overwritten with the first variant.

Valid:

```franko
enum<uint8> Status {
    spawning = 10,  // this is the default value
    alive = 20
}

struct Entity {
    int32 id
    Status state    // defaults to Status::spawning
}

func main() -> int32 {
    Entity e;
    print(e.state == Status::spawning); // 1 (true)
    return 0;
}
```

## 30.3 Operators

Enums are logical states, not raw numbers.

Allowed:

- equality: `==`, `!=`
- ordering: `<`, `>`, `<=`, `>=` (using backing integer values)
- assignment: `=`

Both operands in a comparison or assignment must have the exact same
enum type. An enum cannot be compared to an integer or to a different
enum type.

Disallowed:

- arithmetic: `+`, `-`, `*`, `/`, `%`
- bitwise: `&`, `|`, `^`, `~`, `<<`, `>>`
- compound assignment: `+=`, `-=`, etc.

Invalid:

```franko
Status s = Status::spawning;
s = s + 1;               // arithmetic is banned
if (s == 0) { ... }      // cannot compare enum to int
```

## 30.4 Type Identity and Casting

Enums are nominal. Two enums with the same backing type and variants
are distinct and incompatible. There are no implicit conversions
between enums and integers. See [Type Equality](10-type-equality.md).

Explicit casts transition between enums and raw integers. Enums obey
the strict split between statically safe `cast<T>` and
runtime-checked `unsafe_cast<T>` (see [Casts](12-casts.md)).

**`cast<T>` — value-preserving / safe**

Allowed from an enum to its exact backing type, or to a strictly wider
compatible integer type:

```franko
enum<uint8> State { a, b }
uint8 exact = cast<uint8>(State::b);     // valid: exact backing type
int32 wider = cast<int32>(State::b);     // valid: value-preserving widening
```

**`unsafe_cast<T>` — checked / can lose data or abort**

A superset of `cast`. It also permits:

- **Integer to enum:** the integer must match a declared variant;
  otherwise the program aborts at runtime.
- **Enum to a narrower or signedness-incompatible integer:** the value
  must fit the target type; otherwise the program aborts at runtime.

```franko
enum<uint32> BigState { a = 0, b = 300 }

uint8 narrow = unsafe_cast<uint8>(BigState::a); // succeeds (0 fits)
uint8 crash = unsafe_cast<uint8>(BigState::b);  // aborts (300 does not fit uint8)

BigState s1 = unsafe_cast<BigState>(0);         // succeeds (maps to a)
BigState s2 = unsafe_cast<BigState>(999);       // aborts (999 is not a variant)
```

Enum-related constant casts are intentionally runtime-checked. They are
not rejected at compile time merely because the literal is known to be
invalid.

## 30.5 Memory Safety Constraints

Enums participate in array intrinsic checking:

- `#is_memset_safe(x)` is `0` for enums. `memset` cannot be called on
  an enum array.
- A struct or tuple containing an enum is not memset-safe.
- `#is_memcpy_safe(x)` is `1`. Copying already-valid enums
  byte-for-byte is safe.

Invalid:

```franko
[Status, 10] states;
states.memset(0);    // invalid: Status is not memset-safe
```

Valid:

```franko
[Status, 3] states;
states.memcpy([Status::alive, Status::alive, Status::alive]); // valid
```

Array literals may therefore use enum element types when their elements
are enum variant references (see [Array Literals](21-array-literals.md)).

## 30.6 Compile-Time Reflection

| Predicate | Result | Reason |
|---|---|---|
| `#is_primitive` | `0` | Enums are distinct nominal types, not primitives. |
| `#is_enum` | `1` | The type is an enum. |
| `#has_default_value` | `1` | The first variant is always applied. |
| `#is_memset_safe` | `0` | Banned to prevent invalid state-machine tags. |
| `#is_memcpy_safe` | `1` | Safe to copy valid types. |
| `#is_assignable` | `1` | `=` is fully supported. |
| `#is_value_assignable` | `1` | Enums can be passed and returned by value. |
| `#is_matchable` | `1` | Built-in `==` and `!=` are supported. |
| `#is_comparable` | `1` | Built-in `<`, `>`, `<=`, `>=` are supported. |
| `#what_type` | string | The enum's nominal spelling, e.g. `"Status"`. |

`#is_enum` accepts a type or an identifier/value, like the other type
predicates, and returns `uint8`. See
[Compile-Time Reflection](34-compile-time-reflection.md).

## 30.7 Complete Example: Data-Oriented State Machine

Franko avoids OOP. Behavior is driven by data classes and strict enum
state machines. This example uses the implicit auto-increment form.

```franko
import "franko/io"

// Auto-increments: sleeping=0, hunting=1, attacking=2, dead=3
enum<uint8> AiState {
    sleeping,
    hunting,
    attacking,
    dead
}

struct Enemy {
    int32 id
    int32 health = 100
    AiState state    // Auto-defaults to AiState::sleeping (0)
}

// Systems process data in bulk based on enum state
func update_enemies(mview<[Enemy]> enemies) -> void {
    for (uint32 i = 0; i < $(enemies).length(); i++) {

        if ($(enemies)[i].state == AiState::sleeping) {
            // Wake up logic
            $(enemies)[i].state = AiState::hunting;
        }
        else if ($(enemies)[i].state == AiState::hunting) {
            // Chase logic
            if ($(enemies)[i].health < 50) {
                $(enemies)[i].state = AiState::attacking;
            }
        }
        else if ($(enemies)[i].state >= AiState::attacking) {
            // Handles attacking and dead states via ordering operator
            if ($(enemies)[i].health <= 0) {
                $(enemies)[i].state = AiState::dead;
            }
        }
    }
    return;
}

func main() -> int32 {
    [Enemy] mob_pool;
    mob_pool.init_zero(10); // Safe: zero-fills, then writes AiState::sleeping (0) to each entity

    update_enemies(getmview(mob_pool));

    print(mob_pool[0].state == AiState::hunting); // 1 (true)

    mob_pool.uninit();
    return 0;
}
```

## 30.8 Naming Convention

- Enum type names use `CapitalLetterCamelCase`:
  - `Status`
  - `AiState`
  - `NetworkState`
- Enum variants use `camelCaseStartingWithLowerCaseLetter`:
  - `Types::type1`
  - `Types::complexType`
  - `Status::pending`
  - `AiState::sleeping`
  - `AiState::attacking`

See [Naming Conventions](36-naming-conventions.md).

---

# 31. Memory Safety Checking

Franko checks two independent properties of pointers: **null state**
and **dangling storage**. Both checks cover all three pointer types
(`ptr`, `mview`, and `view`). A third check tracks the initialization
lifecycle and buffer reuse of function-local dynamic arrays (see
[Dynamic-Array Lifecycle Checking](#2910-dynamic-array-lifecycle-checking)).

The checks also follow values across function calls. Passing a null pointer to
a function that uses it, forwarding a pointer to local storage into global or
caller-owned storage through a callee, or changing a caller's dynamic array
through a callee are diagnosed at the relevant call site. Messages are prefixed
with `Intraprocedural:` when the problem is inside the function being checked
and with `Interprocedural:` when it is discovered at a call.

The null-state check covers:

- dereferencing a null pointer (an error);
- dereferencing a possibly-null pointer (a warning);
- that `null` is only used for pointer types (see [Types](09-types.md)).

The null-state check also covers values crossing function boundaries: a
callee's parameter use becomes a requirement on the caller's argument, a
callee's return carries the null state of the value it returns, and a
callee that writes through a `ptr<ptr<T>>` parameter can update the caller's
pointer variable. A function still reports its own local maybe-null warnings
as well.

## 31.1 Pointer States

For each pointer variable the compiler tracks one of:

| State | Meaning |
|---|---|
| pointing | the variable holds a certified, real storage pointer |
| null | the variable is definitely null on the current path |
| maybe-null | the variable is null on some continuing paths |

## 31.2 Assignment Rules

- an ordinary pointer declaration is **null-initialized**, so it starts in the null state;
- assigning the `null` literal makes the target null;
- assigning `getptr(...)`, `getmview(...)`, or `getview(...)` makes the target pointing; these are the **only** certifications of a non-null pointer;
- assigning another pointer variable copies that variable's exact state, so a null state propagates through copies and certification propagates transitively;
- assigning a value of unknown provenance (a call result, a parameter, a struct-field or array-element read) yields a maybe-null state;
- at function entry, a pointer parameter or global starts as maybe-null because the compiler cannot see the value it receives. Exceptions: a method's `self` parameter starts pointing, because the receiver is a storage-backed lvalue; and the args-form `main` parameter starts pointing, because it always receives a live view.

## 31.3 Dereference Checking

- `$(p)` where `p` is **null** is an **error**;
- `$(p)` where `p` is **possibly null** is a **warning**.

A variable warns at most once per check, so revisiting the same dereference on
another path does not repeat the warning.

## 31.4 Branch Handling

`if` / `else` is checked branch by branch. Only branches that continue past the
statement affect later checks. A branch that **definitely returns** does not
affect the code following the `if`, because that path never continues there.
A branch whose condition contradicts a known state cannot execute and produces
no diagnostics.

Very deeply nested conditions may be handled more conservatively, so some
warnings can be missed.

## 31.5 Loop Handling

`while` loops are checked without proving termination:

- the body is checked once;
- the state after the loop accounts for zero iterations, normal exits, and
  `break`;
- `break` and `continue` paths are handled separately;
- if neither a normal exit nor a `break` is possible, the loop is treated as
  non-falling-through, so its tail is not checked.

Loop termination is not proven; the post-loop state is conservative.

`for` loops receive the same treatment as the equivalent block and `while` loop, so they are handled the same way. See [Shorthands and Equivalences](24-desugaring.md).

## 31.6 Guard Refinement

Inside `if (p == null)` / `if (p != null)` (either operand order), the compiler refines `p`'s state per path: the then-branch of `p != null` sees `p` as pointing, and the code after the loop/branch of a failed guard sees `p` as null.

```franko
ptr<int32> p;
if (p != null) {
    $(p);      // valid: p is refined to pointing inside the guard
}
$(p);          // error: p is provably null after the guard fails
```

When a guard's assertion contradicts a definite current state, that path is statically infeasible; the compiler suppresses dereference diagnostics inside it and does not let its state flow onward.

```franko
ptr<int32> p;
p = getptr(x);
if (p != null) {
    $(p);      // valid
}
$(p);          // valid: p is still pointing; the else path was infeasible
```

The same refinement runs for loop conditions, so `while (p != null) { ... }` sees `p` as pointing inside the body and as null after the loop.

## 31.7 Limitations

Current limitations of the pointer null-state check:

- values of unknown provenance (parameters, field and element reads, and unresolved call results) are treated as maybe-null: dereferencing one is a warning, not an error, and a null value may still flow through them;
- only plain pointer **variables** are tracked. A pointer held in a struct field, an array element, or behind a `$(...)` chain is not tracked;
- pointers hidden inside returned structs or tuples are not tracked;
- global null-state writes are not tracked across functions;
- only one level of pointer-to-pointer writes is followed;
- deeply nested conditions may reduce precision;
- recursive or very deep call chains are handled conservatively, so some
  nullness problems may be missed;
- a non-variable dereference such as `$(f())` is an error when the resolved return state is definitely null and a warning when it is possibly null.

## 31.8 Capability Linting

A separate, independent check warns when a `ptr`/`mview` local variable or
parameter requests permissions it never exercises, so developers default
to the tightest pointer that fits. See the "Capability Linter" section of
[Pointers](25-pointers.md).

## 31.9 Dangling-Pointer Checking

The compiler checks `return` values for pointers that outlive the storage
they refer to, rejects pointer stores that move frame-scoped storage into
global or caller-owned storage, and detects use of a pointer after its
inner block or loop body has ended. The check follows pointers through
function calls: a frame pointer forwarded through one or more functions into
global or caller-owned storage is diagnosed at the call. Each function still
reports its own return/store/lifetime diagnostics, and code removed as
unreachable produces no diagnostics.

### 31.9.1 What Is Tracked

Taking a pointer to a local or parameter and returning it is rejected:

```franko
int32 x
ptr<int32> p = getptr(x)
ptr<int32> q = p
return q                        // error: x dies with the frame
```

Taking a pointer to a pointer parameter, or dereferencing one, is
caller-owned and safe to return:

```franko
func ok(ptr<int32> p) -> ptr<int32> {
    return getptr($(p));       // points at the caller's storage
}
```

The check follows assignments, pointer copies, `$(...)` aliases, and
`getptr`/`getmview`/`getview` of lvalues. Pointer parameters and globals
are never assumed to point into the current function's frame.

### 31.9.2 Path and Loop Handling

`if`/`else` is followed branch by branch, and a branch whose condition cannot
hold is not diagnosed. For `while`, the state after the loop accounts for zero
iterations, normal exits, and `break`; `continue` paths are kept separate, and
a loop with no possible exit is treated as non-falling-through. `for` loops
receive the same treatment as their equivalent block and `while` loop.

Null guards refine what is known about the storage too:

- in an `if (p == null)` path, `p` is null, so returning it on that path is
  safe;
- in an `if (p != null)` path, the surviving value is marked definitely
  non-null, so a later return can be classified as definitely dangling
  rather than only possibly dangling;
- `if (p != null)` over a pointer that is definitely null is infeasible;
- `if (p == null)` over a pointer that is definitely non-null is
  infeasible; every `getptr`/`getmview`/`getview` result is definitely
  non-null, even when the storage it points at is caller-owned;
- a return inside an infeasible path is not diagnosed, because that path
  never executes.

On the condition-false exit of `while (p != null)`, `p` is null, so
returning `p` is safe on that path even though the loop body saw a
non-null pointer. Paths that leave the loop through `break` are handled
separately and may still return a non-null `p`.

### 31.9.3 The Return Rule

At a pointer-typed `return`:

- a value that definitely refers to the current frame → **error**;
- a value that may refer to the current frame after a branch, or
  may refer elsewhere → **warning**.

The diagnostic names the local variable or parameter:

```text
Intraprocedural: Returning a pointer to local variable 'x' from function 'bad' (dangling after return)
Intraprocedural: Returning a pointer to parameter 'x' from function 'bad' (dangling after return)
Intraprocedural: Returning a pointer that may refer to local variable 'x' from function 'maybe' (possibly dangling after return)
```

### 31.9.4 Decayed Views

Static-to-dynamic array view decay does not create a new storage object:
the converted view refers to the same array. Therefore:

- returning a decayed parameter or global view is **safe** and is not
  diagnosed;
- a returned decayed view still refers to the original static storage;
- returning a decayed view of **local** array storage is still a normal
  frame-storage escape and is rejected by the same return rule as any
  other pointer to a local.

Literal views refer to constant storage with static lifetime, so
returning or keeping one is safe. Global initializers are never checked
as function bodies.

### 31.9.5 The Store Rule

A pointer store is rejected when the stored value refers to current-frame
storage and the target storage is caller-owned or global:

```franko
ptr<int32> global_p

func bad() -> void {
    int32 x
    global_p = getptr(x)      // error: x dies with this frame
    return
}
```

The same rule applies to stores through a pointer parameter:

```franko
func bad(ptr<ptr<int32>> out) -> void {
    int32 x
    $(out) = getptr(x)        // error: caller storage outlives x
    return
}
```

The diagnostic is:

```text
Intraprocedural: Cannot store a view of frame or operator-borrowed storage into a global or caller-owned location
```

A local target is intentionally allowed. For an operator method's borrowed
`other` parameter this is safe because the local dies before the operator call
returns. Storing a view of an inner local into a longer-lived local is still
allowed and can still dangle; that is the pre-existing local-view hazard.
Forwarding the pointer through a callee that stores it is also caught.

### 31.9.6 Limitations

The check intentionally does not detect:

- pointers hidden inside returned structs or tuples;
- pointer fields inside structs or other aggregates when the escape does not
  go through a direct global or out-parameter store;
- global dynamic-array lifecycle;
- every loop-carried lifetime fact: loops are checked conservatively, so a
  pointer that becomes dangling only on a later iteration may be missed;
- every escape through a recursive or very deep call chain.

The store rule closes the direct global/out-parameter store. An operator
method's `other` parameter is treated as call-scoped borrowed storage, so
storing it into caller-owned or global storage is rejected. Forwarding a
borrowed or ordinary argument through a callee that stores it is also caught.
Later checks do not assume that a call left a global pointer untouched.

## 31.10 Dynamic-Array Lifecycle Checking

The compiler also tracks whether a function-local dynamic array has
allocated element storage, whether element pointers still refer to its current
buffer, and what a called function does to the array. A callee can require an
initialized array, initialize or free the caller's array, or return an element
pointer into it. Each function or method still starts with a fresh local
lifecycle state.

### 31.10.1 Array States

Each tracked dynamic array is in one of three states:

| State | Meaning |
|---|---|
| `UNINITED` | no element storage; never initialized, or already `uninit`ed |
| `INITED` | element storage is allocated |
| `MAYBE_INITED` | initialized on some paths, or a call may have changed it |

A separate history flag distinguishes a never-initialized array from one
that was already freed. That only changes diagnostic wording; it is not
a fourth lifecycle state.

### 31.10.2 What Is Tracked

The check covers function-local dynamic array variables and owner
`ptr<[T]>` values obtained from `getptr(localArray)`, including pointer
copies, casts, and `getptr($(p))` aliases.

It does not yet track:

- dynamic arrays stored in struct fields;
- individual elements of nested dynamic arrays below the outermost tracked
  array;
- global dynamic array variables.

Global owner pointer variables are not trusted across functions: they enter
each function unknown, can be certified by a local assignment such as
`G = getptr(d)`, and are forgotten after any function call. Global dynamic
array lifecycle is not tracked at all.

Element pointers/views (`getptr`/`getmview`/`getview(d[i])`) refer to the
element buffer. Whole-array `view<[T]>`/`mview<[T]>` values refer to the array
object and are not invalidated when the buffer changes.

A call is checked against what the callee does: a callee that only reads or
writes elements does not change the caller's lifecycle state; a callee that
requires an initialized array reports a definite or possible misuse; a callee
that calls `init` makes the caller array `INITED`; and a callee that calls
`resize`/`uninit` can invalidate element pointers. Passing an `mview<[T]>` or
`view<[T]>` cannot initialize, resize, or free the array, so it does not change
the caller's lifecycle state.

### 31.10.3 Operations

| Operation | Required state | Result |
|---|---|---|
| `d.init(n)`, `d.init_zero(n)` | any | `INITED`; repeated init is not diagnosed |
| `d.resize(n)` | `INITED` | stays `INITED` |
| `d.uninit()` | `INITED` | `UNINITED`; invalid free if never initialized, double free if already uninitialized |
| `d[i]`, `d.memset`, `d.memcpy` destination, `d.memmove` | `INITED` | unchanged |
| `d.memcmp`, `d.memchr`, `d.memmem`, `d.read_*`, `d.read_bit`, `d.count_set_bits` | `INITED` | unchanged |
| `d.length()` | any state | unchanged; safe after `uninit` |

`d.memcpy`, `d.memcmp`, and `d.memmem` also check the first argument's array
source when it is a tracked dynamic array.

A use of a definitely `UNINITED` array is an error; a use of a
`MAYBE_INITED` array is a warning.

`uninit()` on a definitely never-initialized array is an **invalid
free**. `uninit()` on an array that was already uninitialized is a
**double free**. Both are errors when definite; possible cases are
warnings.

Repeated `init`/`init_zero` is deliberately not diagnosed: the first
call may have returned `-1`, and retrying is valid. The runtime still
aborts if the array really was initialized already.

### 31.10.4 Guard Traps

A comparison of `d.length()` against a constant refines the state on
each path:

| Guard | True path | False path |
|---|---|---|
| `d.length() == 0` | `UNINITED` | `INITED` |
| `d.length() != 0` | `INITED` | `UNINITED` |
| `d.length() > 0`, `>= 1` | `INITED` | `UNINITED` |
| `d.length() <= 0`, `< 1` | `UNINITED` | `INITED` |

Nonzero constant comparisons also certify the nonzero side as `INITED`.
A branch that contradicts the known state cannot execute, so it produces no
diagnostics:

```franko
[int32] d;
d.init(4);
if (d.length() == 0) {
    d[0] = 1;       // not diagnosed: this path cannot execute
}
```

The inverse form is also rejected when it is definitely wrong:

```franko
[int32] d;
if (d.length() > 0) {
    d[0] = 1;       // not diagnosed: this path cannot execute
}
```

### 31.10.5 Branches and Loops

`if`/`else` is followed branch by branch. Loops account for zero iterations,
normal exits, `break`, and `continue`, and apply the condition-false
refinement to each exit path. A loop with no possible normal exit or `break`
is treated as non-falling-through, so its tail is not checked. This means:

```franko
[int32] d;
while (d.length() == 0) {
    d.init(4);
}
d[0] = 1;           // valid: the loop exited with d initialized
```

### 31.10.6 Buffer Invalidation

An element pointer/view is tied to the buffer that existed when it was
created. `init`/`init_zero`, `resize`, and `uninit` replace or release that
buffer. Using an element pointer whose buffer has definitely been replaced or
freed is an **error**; using one whose buffer may have been replaced is a
**warning**.

The check applies at dereference, indexing, assignment through the alias, call
argument, return, and creation intrinsics over the alias. An owner `ptr<[T]>`
or a whole-array `view<[T]>`/`mview<[T]>` refers to the stable array object
and is **not** invalidated by `init`/`resize`/`uninit`; only
element pointers/views into the element buffer are invalidated. A returned
element alias keeps its association with the caller's array, so resizing after
the call is diagnosed. When a callee may invalidate array parameter `i` and
may use element-pointer parameter `j`, and the caller passes an element alias
of `i` as `j`, the compiler emits a possible cross-parameter invalidation
warning.

### 31.10.7 Missing `uninit`

A local dynamic array's runtime destructor already frees its buffer when
the array goes out of scope. Missing an explicit `uninit()` is therefore
a **warning**, not a memory leak error:

```text
Intraprocedural: Dynamic array 'd' is still initialized at return from function 'f'
(missing explicit uninit)
```

The warning is suppressed when the array escaped through a global, an
aggregate store, or a call that may let it escape, because the compiler can no
longer prove the state.

### 31.10.8 Limitations

The check does not detect:

- lifecycle misuse of struct-field or nested-element arrays;
- lifecycle tracking for global dynamic arrays;
- allocation failure of `init`/`init_zero` (the successful path is modeled);
- every loop-carried lifecycle fact: loops are checked conservatively;
- every effect of a recursive or very deep call chain;
- use of an element pointer after its root array's scope ends; that is
  reported as a dangling-pointer error instead.

---

# 32. Pool Allocation

Franko has no per-object heap allocator. The two storage classes a variable
can have are automatic storage (a local, whose lifetime is its enclosing
block) and static storage (a global, whose lifetime is the program).

Dynamic arrays are the one kind of storage a program manages explicitly:
`init`, `init_zero`, `resize`, and `uninit` pair up over a single array.

```franko
[int32] xs;
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

(uint32 idx, int32 err) allocated = pool.new_index();
if (allocated.err != 0) {
    // pool is uninitialized or exhausted
}
// ... use allocated.idx as a row index into the program's own payload array ...
pool.free_index(allocated.idx);

pool.uninit();
```

Slot indices are cheap to copy, compare, and store, and a generational
pool distinguishes a live index from one that was recycled, so a stale
handle is detectable. The pool tracks indices only; the payload that an
index selects belongs to the program.

The pool allocator API is documented in
[`franko/pool`](../library_docs/pool.md).

---

# 33. Foreign Function Interface

Franko has no general native-call syntax. Instead, a Franko module can
declare that some of its functions are implemented by a C++ bridge. The
bundled `franko/os` module uses this mechanism; user modules can use
exactly the same mechanism.

An FFI module is three sibling files:

```text
lib/mathx.fr
lib/mathx.module.json
lib/mathx.ffi.cpp
```

The `.fr` file is ordinary Franko: it declares the module's functions
(and any ordinary variables or structs the module needs). The
`.module.json` file names the function declarations that cross the FFI
boundary. The `.ffi.cpp` file contains the C++ implementations.

Import the module normally:

```franko
import "lib/mathx"
```

The compiler discovers `mathx.module.json` next to the resolved `mathx.fr`.

## 33.1 Module Descriptor

The descriptor lists only the functions that are bridged:

```json
{
  "format": "franko-module",
  "version": 1,
  "id": "example/mathx",
  "source": "mathx.fr",
  "bridge": "mathx.ffi.cpp",
  "functions": [
    {"namespace": "mathx", "name": "add"},
    {"namespace": "mathx", "name": "scale"}
  ]
}
```

- `bridge` is the default C++ bridge file, relative to the descriptor.
- Every entry in `functions` is bridged. There is no per-function
  `bridge: false` opt-out.
- A function can override the module bridge file with
  `"bridgeFile": "other.ffi.cpp"`.
- Names and namespaces must match the declarations in the `.fr` file. The
  `.fr` declaration supplies the signature; the descriptor does not
  duplicate it.
- The descriptor does not declare variables, structs, target filters, or
  target-dependent constants. Platform selection is ordinary Franko code
  using `#if` and the built-in `#IS_*` target constants.

## 33.2 Target Selection

Target-specific FFI calls are guarded with `#if`:

```franko
#if (#IS_WINDOWS) {
    os::win32::WriteFile(...)
} #else #if (#IS_LINUX || #IS_MACOS) {
    os::posix::write(...)
}
```

Only the selected branch is compiled. The other platform's functions do
not need to exist on the current target, so the module does not need
target filters. `franko/os` itself declares `os::posix` only for
linux/macos and `os::win32` only for Windows, so off-target FFI functions do not exist on that target at all. See
[Compile-Time Reflection](34-compile-time-reflection.md).

An unguarded off-target call still fails when building for that target.

## 33.3 Bridge Blocks

The bridge file is split into named blocks:

```cpp
// @@common
#include "FrankoRuntime.hpp"

static int32_t mathx_bias()
{
    return 0;
}
// @@end

// @@mathx::add
namespace NS_mathx {
int32_t FUNC_add_da8ad4dd(int32_t VAR_a, int32_t VAR_b)
{
    return VAR_a + VAR_b + mathx_bias();
}
}
// @@end
```

Rules:

- A block opens with `// @@<qualified-name>` and closes with `// @@end`.
- The block name is the function's Franko qualified name:
  - function `mathx::add` → `// @@mathx::add`
- `common` is reserved for shared includes, helpers, and declarations. It is
  included once when any bridged function from that bridge file is used.
- Put `#include "FrankoRuntime.hpp"` in the `common` block. It keeps each
  bridge valid for standalone C++ linting and exposes the runtime types
  (`Franko_Thin_Pointer`, `Franko_Fat_Pointer`, `franko_panic`, fixed-width
  integers). The header is `#pragma once`, so a repeated include is harmless.
- Bridge ABI: a pointer parameter or return whose pointee is not an array
  (`ptr<T>`, `mview<T>`, or `view<T>`) is `Franko_Thin_Pointer<T>` in C++;
  access its raw pointer through `.data`. A pointer to an array is
  `Franko_Fat_Pointer<element(T)>`.
- The bridge block must define a function under the exact C++ name generated
  for the Franko declaration. The compiler reports an error if the block is
  missing or the name does not match.
- Merely importing a module with a bridge adds no bridge code unless a
  bridged function is actually used.

## 33.4 The Bundled `franko/os` Module

`franko/os` is Franko's standard FFI module. It ships
`import/franko/os.module.json` and `import/franko/os.ffi.cpp` alongside
`import/franko/os.fr`. Its functions are documented in
[`franko/os`](../library_docs/os.md). It uses the same descriptor and
bridge rules described above.

## 33.5 Failure Modes

The compiler rejects an FFI module when:

- the descriptor is malformed or has an unsupported version;
- a listed function does not exist in the `.fr` file;
- a bridged function has no bridge file or no matching block;
- a bridged function block does not contain the expected generated name.

Bridged function signatures are limited to the C++ types the bridge author can spell directly in the prelude. Struct and tuple types are not part of the current bridge model: use primitive or pointer parameters/returns for bridged functions, and keep aggregate layouts on the Franko side.

---

# 34. Compile-Time Reflection

Franko provides compile-time reflection through `#` expressions and `#if`
compile-time conditionals. These are built into the compiler: there is no
module to import and no runtime value of a reflected type is created.

Compile-time reflection covers:

- target constants such as `#IS_LINUX`;
- type predicates over declared types and variables;
- canonical type spelling with `#what_type`;
- compile-time string comparison, concatenation, and panic;
- `#if` branch selection inside functions, at global scope, inside
  namespaces, and inside structs.

---

## Mental Model

Think of `#` as meaning **"the compiler evaluates this now"**.

There are two related features:

- `#name` / `#name(...)` is a compile-time value or function call.
- `#if` / `#else` chooses which code exists in the compiled program.

Compile-time reflection happens while the program is being checked. By the
time the program runs, every `#if` has already chosen one branch, and every
`#` expression has folded to an ordinary value or declaration. None of the
compile-time machinery remains at run time.

A useful way to read this chapter:

1. `#if` is not runtime `if`. Runtime `if` chooses what happens when the
   program runs; `#if` chooses what the program contains at all.
2. `#if` is a splice. The selected branch is inserted into the surrounding
   scope, and the unselected branch disappears.
3. Types used in reflection are compile-time information. They never become
   runtime values.

---

## 34.1 Compile-Time Names

A compile-time name starts with `#`:

```franko
#IS_LINUX
#IS_WINDOWS
#IS_MACOS
#IS_X86_64
#IS_ARM64
```

These five names evaluate to `byte` constants `0` or `1` for the selected
compilation target. Exactly one OS constant and one architecture constant
are `1`.

```franko
int32 os_count = #IS_LINUX + #IS_WINDOWS + #IS_MACOS;
int32 arch_count = #IS_X86_64 + #IS_ARM64;

print(os_count);    // 1
print(arch_count);  // 1
```

`#` names are not variables. They cannot be assigned to, passed around, or
stored:

```franko
#IS_LINUX = 1;   // invalid: not a storage-backed lvalue
```

The target names can be used in ordinary expressions and in `#if`
conditions. They cannot be used in positions that require a literal
constant expression, such as static array sizes, global primitive
initializers, or field defaults.

---

## 34.2 `#if` / `#else`

`#if` selects code at compile time:

```franko
#if (constantExpression) {
    ...
} #else {
    ...
}
```

The condition is an ordinary expression, but it must be a compile-time
constant integer. Any nonzero constant selects the then-branch; zero
selects the else-branch. If the condition is false and there is no
`#else`, the whole construct disappears.

Chaining uses `#else #if`:

```franko
#if (#IS_WINDOWS) {
    print("windows")
} #else #if (#IS_LINUX) {
    print("linux")
} #else {
    print("other")
}
```

Braceless bodies are allowed:

```franko
#if (#IS_WINDOWS) print("windows")
#else print("other")
```

A standalone `#else` is a syntax error. `#if` is not allowed in a `for`
header.

### Compile-Time Scoping

`#if` is a compile-time splice, not a runtime block. This is deliberately
different from runtime `if` scoping.

Before folding:

```franko
#if (#IS_LINUX) {
    int32 platform = 1
} #else {
    int32 platform = 2
}
print(platform)
```

After folding on a Linux target, the program is exactly:

```franko
int32 platform = 1
print(platform)
```

The braces around the selected branch do not exist after folding. This is
why `platform` is visible after the `#if`. Compare with runtime `if`:

```franko
if (condition) {
    int32 hidden = 1
}
// hidden is not visible here: runtime if creates a block scope

#if (1) {
    int32 visible = 2
}
// visible is visible here: #if splices into the enclosing scope
```

The same rule applies at declaration level:

```franko
struct Device {
    int32 id

    #if (#IS_WINDOWS) {
        func close(ptr<Device> self) -> void { return }
    } #else {
        func close(mview<Device> self) -> void { return }
    }
}
```

After folding on Windows, the struct is exactly:

```franko
struct Device {
    int32 id
    func close(ptr<Device> self) -> void { return }
}
```

Rules:

- At statement level, the selected branch's statements are inserted
  directly into the enclosing statement list.
- At declaration level, the selected branch's declarations are inserted
  directly into the enclosing global, namespace, or struct scope.
- Branch braces are grouping only. They do not create a scope.
- Nested `#if` behaves the same way.
- An unselected branch is not part of the program at all.

Runtime `if`, `while`, and `for` keep their ordinary block-scoping rules.
Only `#if` uses compile-time splicing.

### The Unselected Branch

Only the selected branch becomes part of the program; it is the only branch
checked and resolved. The unselected branch may refer to symbols or types
that do not exist on the current target. Syntax rules still apply to both
branches.

Consequences:

- a selected `return` counts for the function's return check; a dead one
  does not;
- a selected `break`/`continue` is checked against the enclosing loop; a
  dead one is not;
- `#panic` in a dead branch is ignored;
- functions, globals, structs, fields, and methods declared in a dead
  branch are not part of the program.

### Condition Errors

```text
#if condition must be a compile-time constant
#if condition must be an integer, got <type>
```

---

## 34.3 Declaration-Level `#if`

`#if` may wrap declarations at global scope, inside a namespace, or inside
a struct:

```franko
#if (#IS_LINUX) {
    struct PlatformBuffer {
        [byte] data
    }
} #else {
    struct PlatformBuffer {
        [byte] data
        uint32 extra
    }
}
```

Inside a namespace:

```franko
namespace platform {
    #if (#IS_WINDOWS) {
        int32 kind = 1
    } #else {
        int32 kind = 2
    }
}
```

Inside a struct:

```franko
struct Device {
    int32 id

    #if (#IS_WINDOWS) {
        func close(ptr<Device> self) -> void { return }
    } #else {
        func close(mview<Device> self) -> void { return }
    }
}
```

Rules:

- Declaration-level `#if` may wrap structs, global variables, functions,
  and nested namespaces.
- It may wrap struct fields and methods.
- It may not wrap `import` directives. Imports are always unconditional.
- Selected declarations are inserted directly into the enclosing scope.
- Declarations outside any `#if` are visible for reflection throughout
  their scope, including forward references.
- Declarations inside a selected branch become visible when that branch
  is inserted; later conditions in the same scope can inspect them.
- Declarations in an unselected branch are never registered.

---

## 34.4 Type and Value Arguments

The type predicates and `#what_type` accept either a variable/global
identifier or a type:

```franko
struct Point {
    int32 x
}

Point p;
int32 i;

print(#has_default_value(p));       // from a variable
print(#has_default_value(Point));   // from a type
print(#has_default_value(int32));
print(#has_default_value([int32, 4]));
print(#has_default_value(ptr<Point>));
print(#has_default_value((int32, int32)));
```

A bare identifier resolves as a variable first when that name exists;
otherwise it resolves as a type. Primitive, array, pointer, and tuple
syntax is always a type.

Types used in reflection are compile-time only. They never become runtime
values, parameters, locals, or storage.

At declaration level, conditions may inspect types and globally visible
variables. Local variables do not exist at declaration level.

`void` is not a type argument. It is valid only as a function return type,
and is rejected in struct fields, tuple elements, array elements, and pointer
referents.

---

## 34.5 Introspection Functions

All introspection calls use `#name(...)`.

| Function | Argument contract | Result | Semantics |
|---|---|---|---|
| `#is_primitive(x)` | one type or value | `uint8` | `1` iff the type is a primitive integer |
| `#is_enum(x)` | one type or value | `uint8` | `1` iff the type is an enum |
| `#is_memcpy_safe(x)` | one type or value | `uint8` | `1` iff the type is memcpy-safe: primitive, enum, pointer, static array of memcpy-safe elements, struct with all fields memcpy-safe, or tuple with all elements memcpy-safe. Dynamic arrays are not memcpy-safe. |
| `#has_default_value(x)` | one type or value | `uint8` | `1` iff at least one part of the type has a defined default: enums (first variant), pointers (null), dynamic arrays (empty), static arrays whose element type has a default, structs with any field default or any field whose type has a default, and tuples with any such element. `0` for primitive integers, static arrays of primitives, and aggregates whose parts are all raw-indeterminate. |
| `#is_memset_safe(x)` | one type or value | `uint8` | `1` iff the type is safe to byte-fill with `memset`: primitives, static arrays of memset-safe elements, structs whose fields are all memset-safe, and tuples whose elements are all memset-safe. Enums, pointers, and dynamic arrays are not. Field defaults do not affect this predicate. |
| `#is_same_type(x, y)` | two types/values | `uint8` | `1` iff the types are exactly equal; tuple element order matters, names do not |
| `#what_type(x)` | one type or value | compile-time string | canonical type spelling with no spaces after commas, including `(int32,int32)` for tuples; matches the function signature hashing key |
| `#strcat(a, b, ...)` | one or more compile-time strings | compile-time string | concatenates all arguments |
| `#strcmp(a, b)` | two compile-time strings | `int32` | lexicographic byte comparison; negative, zero, or positive |
| `#panic(message)` | one compile-time string | `void` | stops compilation with the message |
| `#is_matchable(x)` | one type or value | `uint8` | `1` for primitive integers, enums, and pointers; for structs, `1` iff a valid `OPERATOR_EQ(view<X> self, view<X> other) -> uint8` exists; tuples and arrays are `0` |
| `#is_comparable(x)` | one type or value | `uint8` | `1` for primitive integers and enums; for structs, `1` iff a valid `OPERATOR_LT(view<X> self, view<X> other) -> uint8` exists; tuples, pointers, and arrays are `0` |
| `#is_assignable(x)` | one type or value | `uint8` | `1` for primitives, enums, and pointers; for structs, `1` iff built-in whole-value assignment is allowed or a valid `OPERATOR_MOV(ptr<X> self, view<X> other) -> void` exists (only pointer-only structs may declare it); for tuples, `1` iff every element is assignable (tuples cannot declare `OPERATOR_MOV`); arrays are `0` |
| `#is_value_assignable(x)` | one type or value | `uint8` | `1` iff a value of the type can be passed as a by-value function parameter: primitives, enums, pointers, structs whose fields are all value-assignable, and tuples whose elements are all value-assignable. Static arrays and dynamic arrays are not. |

The predicates are built-in compile-time functions. They cannot be stored
in variables, passed as callbacks, or referred to without a call.

They cannot be used in positions that require a literal constant
expression, such as static array sizes, global primitive initializers, or
field defaults.

---

## 34.6 `#what_type` Spellings

`#what_type` produces a compile-time string. When used as an ordinary
expression, it materializes as a read-only `view<[byte, N]>` backed by
constant storage. It can be printed, assigned to a byte view, or passed to
a function accepting a byte view.

When consumed by `#strcmp`, `#panic`, or `#strcat`, it stays a compile-time
string and no runtime string is created.

Spellings use canonical Franko type names. Commas are followed directly by
the next item with no space, so the spelling matches the function signature
hashing key:

| Type | `#what_type` result |
|---|---|
| `int` / `int32` | `int32` |
| `byte` | `uint8` |
| `uint` | `uint32` |
| `[int32, 3]` | `[int32,3]` |
| `[int32]` | `[int32]` |
| `ptr<int32>` | `ptr<int32>` |
| `view<[byte, 5]>` | `view<[uint8,5]>` |
| `(int32 val, int32 err)` | `(int32,int32)` |
| `(uint8, [int32, 3])` | `(uint8,[int32,3])` |
| `a::b::Point` | `a::b::Point` |
| `Status` | `Status` |
| `ns::Status` | `ns::Status` |

`byte` is an alias for `uint8`, so canonical spellings use `uint8`.

---

## 34.7 Compile-Time Strings

`#strcmp`, `#panic`, and `#strcat` accept compile-time strings:

- raw string literals;
- `#what_type(...)`;
- `#strcat(...)` results.

Runtime `view<[byte]>` values and variables are rejected, even when they
were initialized from a compile-time string. Empty string arguments and
empty concatenation results are rejected consistently with ordinary
string literals (`N >= 1`).

`#strcat` accepts one or more arguments and concatenates them:

```franko
print(#strcat("Franko", " ", "reflection"))
print(#strcat(#what_type(int32), "_", #what_type(uint8)))

#if (#strcmp(#strcat("a", "b"), "ab") == 0) {
    print("equal")
}
```

`#strcmp` compares decoded UTF-8 bytes lexicographically:

```franko
print(#strcmp("abc", "abd"));   // -1
print(#strcmp("abc", "abc"));   // 0
print(#strcmp("abd", "abc"));   // 1
print(#strcmp("ab", "abc"));    // -1 (prefix sorts first)
```

`#panic` stops compilation with its message:

```franko
int32 i = 1;

#panic("unsupported configuration");
#panic(#what_type(i));   // reports: int32
```

`#panic` may also be used as a declaration-level item inside an `#if`
branch:

```franko
#if (#IS_LINUX || #IS_WINDOWS || #IS_MACOS) {
    ...
} #else {
    #panic("this target is not supported")
}
```

The selected `#panic` is reported during compilation, before other checks.
In a dead `#if` branch it is ignored.

---

## 34.8 Choosing a Predicate

| Question | Predicate |
|---|---|
| Can I copy this type byte-for-byte with `memcpy`/`memcmp`? | `#is_memcpy_safe(x)` |
| Can I byte-fill this type with `memset`? | `#is_memset_safe(x)` |
| Does at least one part of this type have a defined default value? | `#has_default_value(x)` |
| Can I compare two values for equality? | `#is_matchable(x)` |
| Can I order two values with `<`? | `#is_comparable(x)` |
| Can I assign one value of this type to another? | `#is_assignable(x)` |
| Can I assign or pass a value of this type by value? | `#is_value_assignable(x)` |

`#is_memcpy_safe` covers primitives, pointers, static arrays of memcpy-safe
elements, structs whose fields are all memcpy-safe, and tuples whose
elements are all memcpy-safe. Dynamic arrays are not memcpy-safe because
copying one would duplicate ownership of its elements.

`#is_memset_safe` covers primitives, static arrays of memset-safe elements,
structs whose field types are all memset-safe, and tuples whose element
types are all memset-safe. Field defaults do not affect it because
`memset` deliberately overwrites storage.

`#has_default_value` asks whether at least one part of a value has a
defined default. Pointers default to null and dynamic arrays to empty.
A static array follows its element type. A struct or tuple returns `1`
when any field or element has a default or its type has one; it does not
claim that every part is initialized. A struct with a pointer field and a
primitive field therefore returns `1` even though the primitive field
remains indeterminate. Primitive integers and static arrays of primitives
return `0`.

```franko
struct WithDefault {
    int32 x = 5
}

struct Blob {
    [byte] data
}

WithDefault d;
Blob b;

print(#is_memset_safe(d));           // 1
print(#has_default_value(d));        // 1: field default
print(#is_memcpy_safe(b));           // 0: dynamic array field
print(#has_default_value(b));        // 1: dynamic array field
print(#is_assignable(b));            // 0: no built-in assignment
```

A pointer-only struct that is not assignable by default can opt in with an
applicable `OPERATOR_MOV`; `#is_assignable` then returns `1`. A value-assignable
struct cannot declare `OPERATOR_MOV` — its whole-value assignment is always
the built-in field copy — so for it `#is_assignable` and
`#is_value_assignable` are both `1` for the same reason. Tuples cannot declare
operator methods, so for a tuple `#is_assignable` is exactly built-in element
assignability.

`#is_value_assignable` covers primitives, pointers, structs whose fields
are all value-assignable, and tuples whose elements are all
value-assignable. This is the same rule as built-in whole-value assignment
and by-value parameters; it does not consider a custom `OPERATOR_MOV`.
It is also the exact predicate aggregate literal construction uses: a
struct or tuple literal is legal only when this predicate returns `1`.

```franko
struct WithArray {
    [int32, 3] values
}

WithArray w;
print(#is_value_assignable(w)); // 0: static array field
```

`#is_same_type` compares exact types. Aliases normalize first, so `int` and
`int32` are the same type.

`#is_matchable` covers equality, and `#is_comparable` covers ordering.
Pointers support equality but not ordering, so a pointer is matchable but
not comparable. A struct opts in with the corresponding operator method:

```franko
struct Point {
    int32 x

    func OPERATOR_EQ(view<Point> self, view<Point> other) -> uint8 {
        return $(self).x == $(other).x
    }

    func OPERATOR_LT(view<Point> self, view<Point> other) -> uint8 {
        return $(self).x < $(other).x
    }
}

Point p;
print(#is_matchable(Point));  // 1
print(#is_comparable(p));     // 1
```

---

## 34.9 FFI Target Selection

The intended use of declaration-level `#if` is to select platform-specific
declarations and calls. Only the selected branch is compiled, so the other
platform's functions do not need to exist on the current target:

```franko
import "franko/os"
import "franko/io"

func main() -> int32 {
    [byte] msg;
    msg.init(5);
    msg.memcpy("hello");

    #if (#IS_WINDOWS) {
        uint64 h = os::win32::GetStdHandle(os::win32::STD_OUTPUT_HANDLE);
        os::win32::WriteFile(h, getview(msg), 5, null);
    } #else #if (#IS_LINUX || #IS_MACOS) {
        os::posix::write(os::posix::STDIN_FILENO, getview(msg), 5);
    }

    msg.uninit();
    return 0;
}
```

An unguarded off-target FFI call still fails when building for that target.

---

## 34.10 Restrictions and Edge Cases

- `#if` cannot appear in a `for` header.
- The `#if` condition must be a compile-time constant integer. A local
  variable is not a compile-time constant, even when initialized with a
  literal.
- Target constants and introspection calls cannot be used in declaration
  positions that require a literal: static array sizes, global primitive
  initializers, and field defaults.
- `#if` is statement-and-declaration only; it never produces a value.
- `#` reflection functions are built-in compile-time functions, not
  callable values.
- A runtime `view<[byte]>` variable cannot be passed to `#strcmp`,
  `#strcat`, or `#panic`; those arguments must be compile-time strings.
- Empty string literals are rejected in `#strcmp`, `#strcat`, and
  `#panic`.
- Declaration-level `#if` cannot wrap imports.

## 34.11 Common Mistakes

A local variable is not a compile-time constant:

```franko
int32 x = 1
#if (x) { ... }   // error: #if condition must be a compile-time constant
```

Use `#IS_*` or a reflection call instead:

```franko
#if (#IS_LINUX) { ... }                 // valid
#if (#has_default_value(x)) { ... }     // valid
```

A `#` name is not a runtime variable:

```franko
#IS_LINUX = 1                 // invalid
int32 x = #IS_LINUX           // valid: folds to 0 or 1
```

Do not use target constants or reflection in literal-only declaration
positions:

```franko
[int32, #IS_LINUX] xs         // invalid
int32 g = #IS_LINUX           // invalid: global const initializer
```

Do not expect `#if` to create a scope:

```franko
#if (1) { int32 value = 3 }
print(value)                  // valid: compile-time splice
```

A runtime view is not a compile-time string:

```franko
view<[byte, 5]> spelling = #what_type(int32)
#strcmp(spelling, "int32")    // invalid: not a compile-time string
#strcmp(#what_type(int32), "int32") // valid
```

Remember that the unselected branch is still parsed. Its syntax must be
valid, even though its names and types are never analyzed.

---

# 35. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

## 35.1 Function Restrictions

- function declarations are valid only in global scope;
- nested functions are not supported;
- first-class function values are not supported;
- function pointers are not supported;
- anonymous functions/lambdas are not supported;
- parameter names are not used in overload resolution;
- return type is not used in overload resolution;
- arrays cannot be returned by value;
- arrays cannot be passed as parameters by value; use a pointer parameter (`ptr<[...]>`, `mview<[...]>`, or `view<[...]>`, see [Functions](19-functions.md));
- `void` calls cannot be used in value-producing contexts;
- a full all-paths-return proof is not currently performed.

## 35.2 No General Member Calls

Struct methods and the built-in array intrinsics use member-call syntax (see [Methods](27-methods.md) and [Array Intrinsics](22-array-intrinsics.md)). All other member calls are rejected, including:

```franko
x.print();          // unsupported member call: no such struct method
x.something(1);     // unsupported member call: unknown method
```

## 35.3 Struct Restrictions

Structs are supported as user-defined aggregate types with named fields, subject to these restrictions:

- struct field initializers must be constant expressions, and only primitive fields can have them (struct, array, and ptr fields reject `= constExpr` as a type mismatch);
- a struct may not reach itself by value through struct fields or static array elements; `ptr<Node>` fields and `[Node]` (dynamic array) fields break the cycle and are allowed;
- a struct is assignable as a whole value (assignment, by-value parameter, by-value return) only if all of its fields are assignable, recursively: assignable = primitive | pointer | struct-with-all-assignable-fields. Static arrays and dynamic arrays are not assignable, so a struct containing one of them is pointer-only: access it through a pointer (`ptr<X>` for ownership/lifecycle, or `mview<X>`/`view<X>` for access), or declare `OPERATOR_MOV` to provide custom whole-value assignment. A value-assignable struct cannot declare `OPERATOR_MOV`; its whole-value assignment is always the built-in field copy. Tuple elements follow the same assignability rule, but tuples cannot declare `OPERATOR_MOV` (see [Tuples](28-tuples.md));
- structs have methods with explicit `self` receivers of type `ptr<X>`, `mview<X>`, or `view<X>` (see [Methods](27-methods.md)); the `self` slot is implicitly const and cannot be reassigned; general member calls beyond struct methods and array intrinsics, such as `s.foo()` for an unknown `foo`, are rejected;
- comparison and assignment operators on structs are only those with `OPERATOR_*` methods; arithmetic, bitwise, shift, and logical operators are not overloadable (see [Operators](13-operators.md#1310-struct-operator-overloading)).

The complete struct reference, including the reasoning behind each restriction, is in [Structs](26-structs.md).

## 35.4 No Floating-Point Types

Only integer primitives are supported.

## 35.5 No Separate Boolean Type

Boolean results use `uint8`.

## 35.6 No Implicit Numeric Conversions for Nonconstants

Nonconstant numeric expressions require exact type equality wherever a
typed value is expected. Moving between integer widths is always
explicit through the `cast<T>(expr)` intrinsic (value-preserving widening) or `unsafe_cast<T>(expr)` (checked conversions that can lose data) (see [Casts](12-casts.md)).

## 35.7 Arrays Are Not Assignable as Values

Arrays cannot be assigned directly as ordinary values, including from
array literals: copy literal elements with `memcpy` (see
[Array Literals](21-array-literals.md)).

## 35.8 Array Literals Need an Inference Context

An array literal's element type is inferred from an assignment,
return, or call-argument context. Without one, the literal is
rejected:

```franko
[1, 2, 3];                 // invalid: bare expression statement
foo([1, 2, 3]);            // invalid unless foo's parameter is a view<[...]>
return [1, 2, 3];          // invalid unless the return type is a view<[...]>
```

Array literal elements must be compile-time constant expressions
(runtime values cannot appear in one).

## 35.9 Dynamic Array Lifetime Checking Is Partial

The compiler tracks the init/uninit lifecycle of function-local dynamic
array variables and owner `ptr<[T]>` aliases, including what called functions
require and change. It rejects definite use before init, use after `uninit`,
invalid free, and double free; possible cases and missing explicit `uninit()`
calls are warnings. Repeated `init` is not diagnosed because an earlier call
may have failed with `-1` and a retry is valid. Element pointers/views created
from `getptr`/`getmview`/`getview(d[i])` are diagnosed when a
`resize`/`uninit` invalidates them; owner pointers and whole-array views point
at the stable array object and remain valid. The check
does not track dynamic arrays in struct fields or nested elements below the
outermost tracked array, global dynamic-array lifecycle, allocation failure
status, or all runtime bounds properties. See
[Dynamic-Array Lifecycle Checking](31-memory-safety-checking.md#2910-dynamic-array-lifecycle-checking).

## 35.10 Pointer Lifetime Is Not Fully Tracked

The compiler does not fully track dangling pointers or ownership. It detects
returning a pointer to the current function's own frame storage, storing a
pointer to frame or operator-borrowed storage into a global or caller-owned
location, using a pointer after an inner block or loop body has ended, and
forwarding a frame pointer through called functions that store it. Pointers hidden
inside returned structs or tuples and pointer fields inside aggregates remain
untracked. Decayed static-array views refer to their original array, so they
are safe to return when that storage is a parameter or global; a decayed view
of local array storage is still caught as a normal frame-storage escape.
Escapes through very deep or recursive call chains may be missed. See
[Dangling-Pointer Checking](31-memory-safety-checking.md#299-dangling-pointer-checking).

## 35.11 Output Is a Library Function

Output is provided by `franko/io`, not by the compiler. The overloaded `print(...)` functions accept the eight integer types and `view<[uint8]>` text; there are no overloads for pointers, arrays, or structs, so passing one is a "No matching overload" error. See [Statements](17-statements.md) and [franko/io](../library_docs/io.md).

## 35.12 Unsupported Operators

The unary plus operator does not exist in the language:

```franko
+x  // no unary plus operator
```

Shift compound assignments (`<<=`, `>>=`) also do not exist: assignment has plain `=` plus the arithmetic (`+=`, `-=`, `*=`, `/=`, `%=`) and bitwise (`&=`, `|=`, `^=`) compound forms (see [Assignment](16-assignment.md)).

Struct comparison and assignment operators are overloadable through `OPERATOR_*` methods. Arithmetic, bitwise, shift, logical, and unary operators are not overloadable; a struct operand is still rejected for those operators. A value-assignable struct cannot declare `OPERATOR_MOV`; only pointer-only structs may opt into custom whole-value assignment. Compound assignment methods remain available on value-assignable structs. `const` is limited to value-assignable locals, parameters, and globals; it cannot be used on arrays, pointer-only aggregates, `void`, or struct fields. See [Operators](13-operators.md#1310-struct-operator-overloading) and [Declarations](18-declarations.md#188-const-storage).

## 35.13 String Literals Cannot Initialize Arrays

A string literal is a `view<[byte, N]>` value, not a byte-array
value (see [String Literals](23-string-literals.md)). Arrays are not
assignable, so a string literal cannot initialize or assign an array;
copy the bytes with `memcpy` instead.

Invalid:

```franko
[byte, 6] s = "hello";    // invalid
s = "hello";                   // invalid
[byte, ?] s = "hello";    // invalid: '?' requires a view declaration
```

Valid:

```franko
[byte, 6] s;
s.memcpy("hello");
```

## 35.14 No `switch` or `do`/`do-while`

Only `if`, `while`, `for`, `break`, and `continue` control flow is implemented. There is no `switch`, no `do`/`do-while`, and no user-visible `goto`; see [Statements](17-statements.md).

## 35.15 Global Scope Is Declaration-Only

Only struct declarations, global variable declarations, function declarations, namespace declarations, imports, and `#if` declaration conditionals are valid at global scope.

Executable statements are invalid at global scope. Aggregate literals and field-wise declaration initializers are local-only; global aggregate initializers are rejected. See [Aggregate Literals](29-aggregate-literals.md).

## 35.16 No C-Style Casts, Ternary, or Multiple Declarators

The following forms do not exist in the grammar:

- C-style cast syntax (`(T)x` does not exist; explicit conversions use the `cast<T>(expr)` and `unsafe_cast<T>(expr)` intrinsics, and fluid integer constants still convert implicitly when they fit);
- the ternary conditional operator (`?:`);
- multiple declarators in one declaration (`int a, b;` is invalid; declare each variable separately).

## 35.17 Operator Overload Lifetime Limitation

Operator operands may be rvalues: an rvalue operand is borrowed for the
duration of the full expression. The operator method must not retain the
borrowed `other` view beyond the call.

The direct escape is closed by the operator signatures (`uint8`/`void`
returns) and by the dangling-pointer store rule: storing the borrowed
view into caller-owned or global storage is rejected. Storing it in a
local `view<T>` is allowed because the local dies before the operator
call returns. Forwarding the borrowed view to a callee that stores it is
also rejected. Ordinary non-operator methods with `view<T>` parameters are
checked the same way when their arguments are forwarded to a function that
stores them. See
[Memory Safety Checking](31-memory-safety-checking.md#299-dangling-pointer-checking)
and [Operators](13-operators.md#1310-struct-operator-overloading).

## 35.18 Tuple Restrictions

Tuple restrictions are documented in
[Tuples](28-tuples.md#2810-restrictions).

## 35.19 Enum Restrictions

- Enums are declared only at global or namespace scope; no local, nested, or struct-member enum declarations.
- An enum must provide explicit values for every variant or for none; mixing is rejected.
- Every variant name and every variant value must be unique; implicit wrapping that collides is rejected.
- Explicit values must fit the backing type; only the eight primitive integer types are valid backings.
- No arithmetic, bitwise, shift, logical, or compound-assignment operators on enums: only `==`, `!=`, `<`, `>`, `<=`, `>=`, and `=` are supported.
- No implicit conversions between enums and integers; cross-width moves use `cast` (safe) or `unsafe_cast` (checked, runtime-aborted on invalid variants or out-of-range values).
- Integer-to-enum casts (even constant ones like `unsafe_cast<BigState>(999)`) compile and abort at runtime instead of failing at compile time.
- Enum array elements are not memset-safe.
- Enums have no methods, no `OPERATOR_*` overloading, and no tuple-style destructuring.

The complete enum reference is in [Enums](30-enums.md).

---

# 36. Naming Conventions

Franko does not require particular spellings for user-defined names, but
consistent names make programs easier to read and make the standard library
easier to navigate. The conventions in this chapter are the recommended style
for Franko code.

Only the private-member and namespace-internal access rules in §4.8 produce
compiler warnings. The other conventions in this chapter are style guidance.

## 36.1 Summary

| Item | Convention | Examples |
|---|---|---|
| Struct | `CapitalLetterCamelCase` | `BigInteger`, `Point`, `SparseSetAllocator` |
| Internal struct | `__` + `CapitalLetterCamelCase` | `__SecretStruct` |
| Enum | `CapitalLetterCamelCase` | `Status`, `AiState`, `NetworkState` |
| Internal enum | `__` + `CapitalLetterCamelCase` | `__SecretState` |
| Enum variant | `camelCaseStartingWithLowerCaseLetter` | `Types::type1`, `Types::complexType` |
| Function | `lower_case_snake_case` | `read_line`, `new_index` |
| Method | `lower_case_snake_case` | `write_active_indices`, `get_capacity` |
| Private method | `_` + `lower_case_snake_case` | `_secret_function` |
| Internal function | `__` + `lower_case_snake_case` | `__private_helper` |
| Variable | `lower_case_snake_case` | `byte_count`, `is_ready` |
| Field | `lower_case_snake_case` | `capacity`, `next_index` |
| Private field | `_` + `lower_case_snake_case` | `_secret_field`, `_capacity` |
| Namespace | short, lowercase, no underscores | `bignum`, `os`, `win32` |
| Constant | `ALL_CAPS_WITH_UNDERSCORES` | `IS_WINDOWS`, `STDOUT_FILENO` |
| Private member constant | `_` + `ALL_CAPS_WITH_UNDERSCORES` | `_END`, `_ALLOCATED` |
| Internal constant | `__` + `ALL_CAPS_WITH_UNDERSCORES` | `__SECRET_CONSTANT` |

## 36.2 Structs

Struct names use `CapitalLetterCamelCase`:

```franko
struct BigInteger {
    int32 value
}

struct SparseSetAllocator {
    [uint32] _sparse
    [uint32] _dense
}
```

A struct that is internal to its namespace keeps the same base spelling after
the two-underscore prefix:

```franko
namespace bignum {
    struct __SecretStruct {
        int32 value
    }
}
```

## 36.3 Functions and Methods

Function and method names use `lower_case_snake_case`:

```franko
func count_items(view<[byte]> buffer) -> uint32 {
    return 0
}

struct Counter {
    int32 count = 0

    func add_value(view<Counter> self, int32 value) -> void {
        return
    }
}
```

A private method keeps the same base spelling after the single-underscore
prefix, and an internal function keeps it after the two-underscore prefix:

```franko
struct Counter {
    int32 count = 0

    func _secret_function(view<Counter> self) -> int32 {
        return $(self).count
    }
}

func __private_helper() -> int32 {
    return 2
}
```

Direct foreign-function mappings are the exception: the function name
preserves the platform API spelling, even when that is not snake case. The
namespace remains lowercase and underscore-free:

```franko
os::win32::GetStdHandle(os::win32::STD_OUTPUT_HANDLE)
os::win32::ReadFile(...)
os::win32::WriteFile(...)
```

This exception exists only for direct FFI mappings such as the Win32 file
API: a mapping like `os::win32::ReadHandle()` keeps the Windows spelling even
though an ordinary Franko function would be written `read_handle`. Ordinary
Franko functions and methods still use snake case.

Built-in names are fixed and are not renamed. Array intrinsics (`memcpy`,
`memset`, `length`, `read_uint16_LE`, ...) and pointer intrinsics (`getptr`,
`getmview`, `getview`) keep their existing spelling.

Operator methods are the other fixed-name exception: `OPERATOR_EQ`,
`OPERATOR_MOV`, and the other reserved `OPERATOR_*` names are exact,
uppercase, and not renamed to snake case. They are reserved for struct
operator methods and rejected on free functions. See
[Operator Methods](27-methods.md#2710-operator-methods).

## 36.4 Variables and Fields

Variable and field names use `lower_case_snake_case`:

```franko
int32 byte_count = 0
uint32 is_ready = 1

struct Buffer {
    [byte] data
    uint32 capacity = 0
}
```

A private field keeps the same base spelling after the single-underscore
prefix:

```franko
struct Buffer {
    [byte] data
    uint32 _capacity = 0
}
```

## 36.5 Namespaces

Namespace names should be short, lowercase, and free of underscores. When a
name really needs two words, concatenate them instead of inserting an
underscore:

```franko
namespace bignum {
    struct BigInteger {
        int32 value
    }
}

namespace os::win32 {
}
```

Use `bignum::BigInteger` and `os::win32`, not `big_num::BigInteger` or
`os::win_32`.

## 36.6 Constants

Values intended to be constants use `ALL_CAPS_WITH_UNDERSCORES`.
`const` globals follow the same convention; the compiler does not enforce the
casing, but `const int32 MAX_SIZE = 10` is the recommended style:

```franko
byte IS_WINDOWS = 0
int32 STDOUT_FILENO = 1
```

A constant that belongs to a private member scope uses the single-underscore
prefix:

```franko
int32 _END = 0xFFFFFFFF
int32 _ALLOCATED = 0xFFFFFFFE
```

An internal constant adds the two-underscore prefix:

```franko
int32 __SECRET_CONSTANT = 42
```

## 36.7 Private and Internal Prefixes

Underscore prefixes communicate visibility:

- one leading underscore `_` marks a private struct member: `_secret_field`,
  `_secret_function`, `_capacity`, `_END`;
- two leading underscores `__` mark a namespace-internal declaration:
  `__private_helper`, `__SecretStruct`, `__SecretState`, `__SECRET_CONSTANT`.

The prefix composes with the base convention for the kind of declaration. For
example, a private field uses snake case after `_`, while an internal struct
uses CamelCase after `__`.

Tuple element names are declaration-site aliases: `(int32 val,
int32 err)` and `(int32 left, int32 right)` are the same tuple type, and the
names are only used for member access in their own declaration. Unnamed tuple
elements are accessed as `_0`, `_1`, and so on. These names are not struct
members, so the `_` private-field warning does not apply to them.

The compiler currently warns about the following public accesses:

- accessing a field or method whose name starts with `_` from outside its
  declaring struct;
- calling a free function or naming a struct or enum whose name starts
  with `__` from outside its declaring namespace.

See [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md) for the
exact access rules. These warnings do not reject the program. Other naming
conventions in this chapter are not enforced by the compiler.

## 36.8 Standard Library Imports

The compiler's bundled standard library is stored under `./import/franko/`.
The import prefix `franko/` is reserved for those standard modules:

```franko
import "franko/io"
import "franko/os"
import "franko/pool"
```

User modules must not be placed under the compiler's `import/franko/`
directory and must not use the `franko/` import prefix. Put user modules in
your own directory or a project-relative path instead:

```franko
import "./lib/my_module"
import "myproject/my_module"
```

See [Source Files](02-source-files.md) for the import search rules.

## 36.9 Compiler-Enforced Rules

The naming conventions are mostly documentation and style. The compiler only
enforces the access restrictions for the reserved prefixes:

- `_`-prefixed fields and methods are private to their struct;
- `__`-prefixed free functions, structs, and enums are internal to their
  namespace.

The struct casing, function and variable casing, namespace spelling, constant
casing, and `franko/` import reservation are conventions that should be
followed by new code but are not currently diagnosed by the compiler.

---

# 37. Examples

Most executable examples should be placed inside a function body, typically `main`, because global scope allows only declarations.

Examples that print assume `import "franko/io"` at the top of the file.

## 37.1 Basic Integers

```franko
func main() -> int32 {
    int32 x;
    int32 y;

    x = 10;
    y = x + 5;

    print(y);

    return 0;
}
```

## 37.2 Static Array Copy From a Literal

An array literal is a `view<[T, N]>` value, so arrays receive its
elements through `memcpy` (see [Array Literals](21-array-literals.md)):

```franko
func main() -> int32 {
    [int32, 3] xs;

    xs.memcpy([1, 2, 3]);

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

Equivalent core form:

```franko
func main() -> int32 {
    [int32, 3] xs;

    xs[0] = 1;
    xs[1] = 2;
    xs[2] = 3;

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 37.3 Static Partial Copy From a Literal

```franko
func main() -> int32 {
    [int32, 3] xs;

    xs.memset(0);
    xs.memcpy([1, 2]);

    return 0;
}
```

`xs[2]` is untouched: memcpy copies `min(source, target)` elements.

## 37.4 Dynamic Array Copy From a Literal

```franko
func main() -> int32 {
    [int32] xs;

    xs.init(3);

    xs.memcpy([10, 20, 30]);

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    xs.uninit();

    return 0;
}
```

## 37.5 Constant Expressions Inside Array Literals

```franko
func main() -> int32 {
    view<[int32, 4]> v = [1, 5, 10, 1 + 2];

    print($(v)[0]);
    print($(v)[1]);
    print($(v)[2]);
    print($(v)[3]);

    return 0;
}
```

Elements are compile-time constant expressions; runtime values are
rejected because a literal is backed by immutable constant storage.

## 37.6 Nested Static Array Literals

```franko
func main() -> int32 {
    [[int32, 2], 2] matrix;

    matrix.memcpy([
        [1, 2],
        [3, 4]
    ]);

    print(matrix[0][0]);
    print(matrix[1][1]);

    return 0;
}
```

Nested literals are rectangular and static at every level.

## 37.7 Nested Dynamic Arrays

A nested literal has static rows, so dynamic rows are filled one at a
time:

```franko
func main() -> int32 {
    [[int32]] matrix;

    matrix.init(2);
    matrix[0].init(3);
    matrix[1].init(3);

    matrix[0].memcpy([1, 2, 3]);
    matrix[1].memcpy([4, 5, 6]);

    matrix[0].uninit();
    matrix[1].uninit();
    matrix.uninit();

    return 0;
}
```

## 37.8 Declaration Initializer With an Array Literal

A `view` declaration accepts a literal directly; its element type and
length are inferred from the literal and checked against the declared
type:

```franko
func main() -> int32 {
    view<[int32, 3]> v = [1, 2, 3];
    view<[int32, ?]> w = [4, 5, 6, 7];

    print($(v)[0]);
    print($(w).length());    // 4

    return 0;
}
```

## 37.9 Dynamic View Decay From a Literal

A literal decays to `view<[T]>` wherever a dynamic view is
expected:

```franko
func take(view<[int32]> v) -> int32 {
    return $(v)[0];
}

func main() -> int32 {
    view<[int32]> d = [1, 2, 3];

    print($(d)[2]);
    print(take([4, 5, 6]));

    return 0;
}
```

## 37.10 Array Copy Through Pointer

```franko
func main() -> int32 {
    [int32, 3] xs;
    ptr<[int32, 3]> p;

    p = getptr(xs);

    $(p).memcpy([1, 2, 3]);

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

Equivalent core form:

```franko
func main() -> int32 {
    [int32, 3] xs;
    ptr<[int32, 3]> p;

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

## 37.11 String Literal Bytes

```franko
func main() -> int32 {
    [byte] str;     // dynamic array: str.init(200) and str.uninit() require one

    str.init(200);
    str.memset(0);
    str.memcpy("Hello,\tWorld!\n\"Franko\"");   // 22 bytes copied in

    for (uint i = 0; i < 22; i++) {
        print(str[i]);
    }

    str.uninit();
    return 0;
}
```

A string literal is a `view<[byte, N]>`; byte buffers receive its
content through `memcpy`. See [String Literals](23-string-literals.md).

## 37.12 Nested Static Array Grid

```franko
func fill(ptr<[[byte, 3], 2]> grid) -> void {
    $(grid).memset(0);
    $(grid)[0][1] = 7;
}

func main() -> int32 {
    [[byte, 3], 2] grid;

    fill(getptr(grid));

    print(grid[0][1]);
    print(grid[1][2]);

    return 0;
}
```

`[[byte, 3], 2]` is a 2 by 3 grid: `grid[r][c]` indexes `r` along the outer size (`2`) and `c` along the inner size (`3`).

## 37.13 Status-Returning Intrinsics

```franko
func main() -> int32 {
    [int32] xs;
    int32 status = 0;

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

## 37.14 Struct Declaration and Field Access

```franko
struct Point {
    int32 x;
    int32 y
}

func main() -> int32 {
    Point p = Point { x = 10, y = 20 };

    print(p.x);
    print(p.y);

    return 0;
}
```

The separator before `}` is optional.

A field-wise declaration initializer is the direct alternative:

```franko
func main() -> int32 {
    Point p { x = 10, y = 20 };

    print(p.x);
    print(p.y);

    return 0;
}
```

## 37.15 Struct Field Defaults

Field defaults are constant expressions; only primitive fields can have them.

```franko
struct Counter {
    int32 count = 0;
    uint32 limit = 100
}

func main() -> int32 {
    Counter c;

    print(c.count);   // 0
    print(c.limit);   // 100

    return 0;
}
```

## 37.16 Whole-Struct Assignment

A struct is assignable as a whole value only if all of its fields are assignable, recursively.

```franko
struct Pair {
    int32 a;
    int32 b
}

func main() -> int32 {
    Pair p = Pair { a = 1, b = 2 };
    Pair q;

    q = p;            // whole-value copy

    print(q.a);       // 1
    print(q.b);       // 2

    return 0;
}
```

## 37.17 Structs Passed by Value and by Pointer

Assignable structs may be passed and returned by value; pointer-only structs are accessed through a pointer (`ptr<X>` for ownership/lifecycle, or `mview<X>`/`view<X>` for access).

```franko
struct Point {
    int32 x;
    int32 y
}

func dist2(Point p) -> int32 {
    return p.x * p.x + p.y * p.y;
}

func move(ptr<Point> p) -> void {
    $(p).x = $(p).x + 1;
    $(p).y = $(p).y + 1;
}

func main() -> int32 {
    Point p = Point { x = 3, y = 4 };

    print(dist2(p));   // 25

    move(getptr(p));

    print(p.x);        // 4
    print(p.y);        // 5

    return 0;
}
```

## 37.18 Structs with Array Fields

An array field makes the struct non-assignable: the struct is pointer-only and its arrays are managed through the usual intrinsics.

```franko
struct Buffer {
    [int32] data;
    int32 size = 0
}

func main() -> int32 {
    Buffer b;

    b.data.init(4);     // allocate the dynamic array field
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

Dynamic array fields are not allocated as part of the struct declaration; allocate them explicitly with `field.init(n)` as above.

## 37.19 Struct Globals

Global variable declarations accept constant initializers.

```franko
struct Point {
    int32 x = 0;
    int32 y = 0
}

int32 scale = 2;
Point origin;

func main() -> int32 {
    origin.x = scale;

    print(origin.x);   // 2
    print(scale);      // 2

    return 0;
}
```

Global declarations are checked before `main` runs; executable statements remain invalid at global scope.

## 37.20 Array `length()`

```franko
func main() -> int32 {
    [int32] xs;
    xs.init(4);
    print(xs.length());   // 4

    [int32, 3] ys;
    ys.memcpy([10, 20, 30]);
    print(ys.length());   // 3 (compile-time constant)

    for (uint32 i = 0; i < ys.length(); i = i + 1) {
        print(ys[i]);     // 10 20 30
    }

    xs.uninit();
    print(xs.length());   // 0
    return 0;
}
```

## 37.21 Inferred Static Array Sizes (`?`)

```franko
func main() -> int32 {
    view<[int32, ?]> a = [0, 1, 2, 3];
    print($(a).length());   // 4

    view<[byte, ?]> s = [104, 101, 108, 108, 111];
    print($(s).length());   // 5

    view<[[int32, ?], ?]> m = [[1, 2], [3, 4]];
    print($(m).length());      // 2
    print($(m)[0].length());   // 2

    return 0;
}
```

## 37.22 Command-Line Arguments

```franko
func main(view<[[byte]]> args) -> int32 {
    uint32 n = $(args).length();
    print(n - 1);   // user argument count (args[0] is the program name)

    for (uint32 i = 1; i < n; i++) {
        print($(args)[i].length());   // byte length of argument i
    }

    return 0;
}
```

Running the binary with two arguments prints `2`, then each argument's length. Each argument arrives as its raw bytes with no NUL terminator, and the args parameter always receives a live view, so it needs no null guard.

## 37.23 Byte-Array Search, Endian, and Bit Intrinsics

```franko
import "franko/io"

func main() -> int32 {
    [byte, 8] packet;
    packet.memset(0);

    // Big-endian header: 0xCAFEBABE, then a little-endian length.
    packet.write_uint32_BE(0, 0xCAFEBABE);
    packet.write_uint16_LE(4, 300);
    print(packet.read_uint32_BE(0));   // 3405691582
    print(packet.read_uint16_LE(4));   // 300

    // Search the payload with memchr / memmem.
    packet[6] = 65;
    packet[7] = 66;
    print(packet.memchr(65));          // 6
    print(packet.memmem("AB"));        // 6
    print(packet.memchr(67));          // 4294967295 (not found)

    // Compare whole byte arrays.
    [byte, 8] copy;
    copy.memset(0);
    copy.memcpy(getptr(packet));
    print(copy.memcmp(getptr(packet)) == 0);   // 1

    // Bit flags across byte boundaries.
    [byte, 2] flags;
    flags.memset(0);
    flags.write_bit(15, 1);
    print(flags.read_bit(15));         // 1
    print(flags.count_set_bits());     // 1

    return 0;
}
```

## 37.24 Tuple Values

Named and unnamed tuple declarations, assignment across different names, and a function round-trip:

```franko
import "franko/io"

func swap((int32 x, int32 y) pair) -> (int32 left, int32 right) {
    return (int32 left, int32 right) {
        left = pair.y,
        right = pair.x
    }
}

func main() -> int32 {
    (int32 val, int32 err) res = (int32 val, int32 err) { val = 1, err = 2 }
    (int32, int32) lazy = (int32, int32) { _0 = 3, _1 = 4 }

    (uint hello, int world) a = (uint hello, int world) { hello = 5, world = 6 }
    (uint different, int name) b = a
    b.different = b.different + 1

    (int32 low, int32 high) swapped = swap(res)

    print(res.val)                 // 1
    print(lazy._1)                 // 4
    print(b.different)             // 6
    print(b.name)                  // 6
    print(swapped.low)             // 2
    print(swapped.high)            // 1
    print(#is_same_type(res, lazy))   // 1
    print(#what_type(res))            // (int32,int32)

    return 0
}
```

## 37.25 Aggregate Literals

Struct literals, tuple literals, nested literals, and field-wise declaration
initialization:

```franko
import "franko/io"

struct Literal {
    int32 x
    int32 def = 2
}

struct Inner {
    int32 value = 7
}

struct Outer {
    Inner inner
    int32 ok = 1
}

func main() -> int32 {
    Literal a = Literal { def = 1, x = 0 }
    Literal b { def = 3, x = 2 }
    Literal c = Literal {}

    (int32, int32) pair = (int32, int32) { _1 = 2, _0 = 1 }
    (int32, int32) positional = (int32, int32) { 5, 6 }
    (int32, int32) partial { 7 }
    (int32 left, int32 right) named = (int32 left, int32 right) {
        left = 3,
        right = 4
    }

    Outer outer = Outer { inner = Inner { value = 42 }, ok = 2 }

    print(a.x + a.def)                    // 1
    print(b.x + b.def)                    // 5
    print(c.def)                          // 2
    print(pair._0 + pair._1)              // 3
    print(positional._0 + positional._1)  // 11
    print(partial._0)                     // 7
    print(named.left + named.right)       // 7
    print(outer.inner.value + outer.ok)   // 44

    return 0
}
```

See [Aggregate Literals](29-aggregate-literals.md) for the complete rules,
including the all-named/all-unnamed tuple spelling rule and the
`OPERATOR_MOV` distinction.

## 37.26 Const Storage

```franko
import "franko/io"

struct Point {
    int32 x = 0
    int32 y = 0

    func sum(view<Point> self) -> int32 {
        return $(self).x + $(self).y
    }
}

const int32 MAX_POINTS = 10
const Point ORIGIN

func sum(const int32 a, const int32 b) -> int32 {
    return a + b
}

func main() -> int32 {
    const int32 width = 3
    const Point p = Point { x = 4, y = 5 }
    const Point q { x = 6 }
    const (int32, int32) pair = (int32, int32) { 1, 2 }

    int32 value = 7
    const ptr<int32> link = getptr(value)
    $(link) = 8                     // pointee write is allowed

    view<Point> v = getview(p)      // const storage: getview only

    print(width)                    // 3
    print(p.sum())                  // 9
    print(q.x)                      // 6
    print(pair._1)                  // 2
    print($(v).y)                   // 5
    print(value)                    // 8
    print(MAX_POINTS)               // 10
    print(ORIGIN.x)                 // 0
    print(sum(1, 2))                // 3
    return 0
}
```

See [Declarations](18-declarations.md#188-const-storage).
