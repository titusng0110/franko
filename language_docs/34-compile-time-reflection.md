# 34. Compile-Time Reflection

Franko provides compile-time reflection: target constants, compile-time
branch selection, and type introspection. These features let one program
adapt to its target and inspect declared types without running any code.

## 34.1 Importing the module

```franko
import "franko/static"
```

`franko/static` is a built-in module. There is no `static.fr` file to add
to your project; importing it makes the `static` namespace available.
Without the import, using `static::` reports:

```text
Unknown namespace 'static'; import "franko/static" to use compile-time reflection
```

Repeated imports are harmless.

## 34.2 Target constants

The module provides five constants:

| Constant | Value |
|---|---|
| `static::IS_LINUX` | `1` when targeting linux, else `0` |
| `static::IS_WINDOWS` | `1` when targeting windows, else `0` |
| `static::IS_MACOS` | `1` when targeting macos, else `0` |
| `static::IS_X86_64` | `1` when targeting x86_64, else `0` |
| `static::IS_ARM64` | `1` when targeting arm64, else `0` |

All five have type `byte` (`uint8`). Their values come from the
`--target <os>-<arch>` option:

| OS values | Architecture values |
|---|---|
| `linux`, `windows`, `macos` | `x86_64`, `arm64` |

Aliases accepted by `--target` are `darwin` for `macos`, `x64` or
`amd64` for `x86_64`, and `aarch64` for `arm64`. The `compile.py` driver
supplies the option and defaults it to the host target.

Exactly one OS constant and one architecture constant are `1` for a
given target; the others are `0`. They are constants, not variables:

```franko
int32 os_count = static::IS_LINUX + static::IS_WINDOWS + static::IS_MACOS;
int32 arch_count = static::IS_X86_64 + static::IS_ARM64;

print(os_count);    // 1
print(arch_count);  // 1

static::IS_LINUX = 1;   // error: assignment target must be a storage-backed lvalue
```

The constants can be used in ordinary expressions and in `static_if`.
They cannot be used in declaration positions that require a literal,
such as static array sizes, global primitive initializers, or field
defaults.

## 34.3 `static_if` / `static_else`

`static_if` selects code at compile time:

```franko
static_if (constantExpression) {
    ...
} static_else {
    ...
}
```

The condition is an ordinary expression, but it must be a compile-time
constant integer. Any nonzero constant selects the then-branch; zero
selects the else-branch. If the condition is false and there is no
`static_else`, the whole statement disappears.

`static_if` and `static_else` are statement forms only. They are valid
inside function and method bodies, including nested blocks and loop
bodies. They are not top-level items: imports, globals, structs, and
functions cannot be conditionally declared. A standalone `static_else`
is a syntax error.

Braceless bodies behave like ordinary `if` bodies: they are wrapped in
an implicit block, so declarations inside them do not escape.

```franko
static_if (static::IS_WINDOWS) print("windows")
static_else print("other")
```

The condition can also be a compile-time introspection result:

```franko
static_if (static::is_primitive(x)) {
    print("primitive")
} static_else {
    print("not primitive")
}
```

Chaining is written with `static_else static_if`:

```franko
import "franko/static"

func main() -> int32 {
    static_if (static::IS_WINDOWS) {
        print("windows")
    } static_else static_if (static::IS_LINUX) {
        print("linux")
    } static_else {
        print("other")
    }
    return 0
}
```

### The unselected branch

Only the selected branch is compiled and checked. The unselected branch
is not checked against declared names or types, so it may refer to
functions, variables, or types that do not exist on the current target.
Syntax and literal rules still apply to both branches. This is what makes
target selection work across platforms:

```franko
static_if (static::IS_WINDOWS) {
    // Ignored on Linux/macOS: win32 symbols and FFI calls need not
    // exist on this target.
    os::win32::WriteFile(...)
} static_else {
    os::posix::write(...)
}
```

Consequences:

- A selected branch containing `return` counts for the function's
  return check; a dead branch does not.
- A selected branch containing `break` or `continue` is checked against
  the surrounding loop depth; a dead branch is not.
- `static::panic` in a dead branch is ignored and does not stop
  compilation.
- The selected branch keeps ordinary block scoping.
- A runtime `if` is evaluated when the program runs; `static_if` is
  chosen before the program runs.

### Condition errors

A non-constant condition is rejected:

```text
static_if condition must be a compile-time constant
```

A non-integer condition is rejected:

```text
static_if condition must be an integer, got <type>
```

## 34.4 Static introspection functions

The `static` namespace also provides compile-time introspection
functions. They inspect the declared type of a variable; they do not
read its value. Their results are available while compiling:

- predicate results are integer constants (`0` or `1`);
- `strcmp` produces an integer constant;
- `what_type` produces a compile-time string (and a read-only byte view
  when used as an ordinary expression);
- `panic` stops compilation.

They can be used in ordinary expressions and in `static_if`, but not in
declaration positions that require a literal, such as static array
sizes, global primitive initializers, or field defaults. They are
built-in compile-time functions, not first-class functions: they cannot
be stored in a variable or passed as a callback.

| Function | Argument contract | Result | Semantics |
|---|---|---|---|
| `static::is_primitive(x)` | one variable identifier | `uint8` | `1` iff `x` has a primitive integer type |
| `static::is_memcpy_safe(x)` | one variable identifier | `uint8` | `1` iff the type is memcpy-safe: primitive, pointer, static array of memcpy-safe elements, struct with all fields memcpy-safe, or tuple with all elements memcpy-safe. Dynamic arrays are not memcpy-safe. |
| `static::is_trivial(x)` | one variable identifier | `uint8` | `1` iff raw uninitialized storage is safe: primitives, static arrays of trivial elements, and tuples of trivial elements are trivial; pointers, `mview`, `view`, dynamic arrays, and structs with any field default or any non-trivial field are not. |
| `static::is_memset_safe(x)` | one variable identifier | `uint8` | `1` iff the type is safe to byte-fill with `memset`: primitives, static arrays of memset-safe elements, structs whose field types are all memset-safe, and tuples whose element types are all memset-safe. Pointers, dynamic arrays, and `void` are not. Field defaults do not affect this predicate. |
| `static::is_same_type(x, y)` | two variable identifiers | `uint8` | `1` iff the declared types are exactly equal; tuple element order matters and element names do not |
| `static::what_type(x)` | one variable identifier | compile-time string (`view<[byte, N]>` in expression position) | canonical type spelling, including `(int32, int32)` for tuples |
| `static::strcmp(a, b)` | two compile-time strings | `int32` | lexicographic byte comparison; negative, zero, or positive |
| `static::panic(message)` | one compile-time string | `void` | stops compilation with the message |
| `static::is_matchable(x)` | one variable identifier | `uint8` | `1` for primitive integers and pointers; for structs, `1` iff a valid `OPERATOR_EQ(view<X> self, view<X> other) -> uint8` exists; tuples, arrays, and `void` are `0` |
| `static::is_comparable(x)` | one variable identifier | `uint8` | `1` for primitive integers; for structs, `1` iff a valid `OPERATOR_LT(view<X> self, view<X> other) -> uint8` exists; tuples, pointers, arrays, and `void` are `0` |
| `static::is_same_type_assignable(x)` | one variable identifier | `uint8` | `1` for primitives and pointers; for structs, `1` iff built-in whole-value assignment is allowed or a valid `OPERATOR_MOV(ptr<X> self, view<X> other) -> void` exists; for tuples, `1` iff every element is assignable (tuples cannot declare `OPERATOR_MOV`); arrays and `void` are `0` |
| `static::is_passable_by_value(x)` | one variable identifier | `uint8` | `1` iff a value of the type can be passed as a by-value function parameter: primitives, pointers, structs whose fields are all passable, and tuples whose elements are all passable. Static arrays, dynamic arrays, and `void` are not. |

### Identifier arguments

The type-introspection functions inspect a declared type; they do not
evaluate a value. Their argument must be a bare variable identifier:

- accepted: a local variable, a parameter, `self`, or a qualified global
  variable such as `ns::x`;
- rejected: string literals, `view<[byte]>` values, arithmetic
  expressions, field accesses, array elements, function calls, and
  struct names.

```franko
int32 x = 1;
print(static::is_primitive(x));     // 1
print(static::is_primitive(1));     // error
print(static::is_primitive(x + 1)); // error
```

### Static string arguments

`static::strcmp` and `static::panic` accept compile-time string
arguments. A compile-time string is a raw string literal or the result of
another compile-time string producer such as `static::what_type(...)`.
Runtime `view<[byte]>` values and variables are rejected, even when they
were initialized from a compile-time string. Empty string literals are
rejected consistently with the rest of the language (`N >= 1`).

```franko
print(static::strcmp("abc", "abd"));   // -1
print(static::strcmp("abc", "abc"));   // 0
print(static::strcmp("abd", "abc"));   // 1
print(static::strcmp("ab", "abc"));    // -1 (prefix sorts first)
```

Both operand positions accept `what_type`:

```franko
int32 i = 1;

print(static::strcmp(static::what_type(i), "int32")); // 0
print(static::strcmp("int32", static::what_type(i))); // 0
print(static::strcmp(static::what_type(i), "int64")); // -1
```

`static::panic` stops compilation with its message:

```franko
int32 i = 1;

static::panic("unsupported configuration");
static::panic(static::what_type(i));  // reports: int32
```

In a dead `static_if` branch it is ignored, so it does not stop
compilation.

### `what_type` spellings

`static::what_type(x)` produces a compile-time string. When used as an
ordinary expression, it materializes as a real `view<[byte, N]>` value
backed by read-only storage that lives for the whole program; it can be
printed, assigned, or passed to a function that accepts a byte view. When
passed to `static::strcmp` or `static::panic`, it is consumed at compile
time and no string storage is emitted.

Spellings use canonical Franko type names:

| Declared type | `what_type` result |
|---|---|
| `int` / `int32` | `int32` |
| `byte` | `uint8` |
| `uint` | `uint32` |
| `[int32, 3]` | `[int32, 3]` |
| `[int32]` | `[int32]` |
| `ptr<int32>` | `ptr<int32>` |
| `view<[byte, 5]>` | `view<[uint8, 5]>` |
| `(int32 val, int32 err)` | `(int32, int32)` |
| `(uint8, [int32, 3])` | `(uint8, [int32, 3])` |
| `a::b::Point` | `a::b::Point` |

`byte` is an alias for `uint8`, so canonical spellings use `uint8`.
The exact result is a `view<[byte, N]>` whose byte length is the UTF-8
length of the spelling.

## 34.5 Choosing a predicate

These predicates answer different questions:

| Question | Predicate |
|---|---|
| Can I copy this type byte-for-byte with `memcpy`/`memcmp`? | `static::is_memcpy_safe(x)` |
| Can I byte-fill this type with `memset`? | `static::is_memset_safe(x)` |
| Is raw uninitialized storage a valid value of this type? | `static::is_trivial(x)` |
| Can I compare two values for equality? | `static::is_matchable(x)` |
| Can I order two values with `<`? | `static::is_comparable(x)` |
| Can I assign one value of this type to another? | `static::is_same_type_assignable(x)` |
| Can I pass a value of this type to a function by value? | `static::is_passable_by_value(x)` |

`is_memcpy_safe` covers primitives, pointers, static arrays of memcpy-safe
elements, structs whose fields are all memcpy-safe, and tuples whose elements
are all memcpy-safe. Dynamic arrays are not memcpy-safe, because copying a
dynamic array would duplicate ownership of its elements.

`is_memset_safe` covers primitives, static arrays of memset-safe elements,
structs whose field types are all memset-safe, and tuples whose element types
are all memset-safe. Field defaults do not affect it, because `memset`
deliberately overwrites the storage.

`is_trivial` is stricter: it asks whether a value can be created from raw
storage. A struct with a field default is memset-safe but not trivial,
because raw storage would violate the default. Tuple elements recurse
through the same predicate; a tuple has no defaults of its own.

```franko
struct WithDefault {
    int32 x = 5
}

struct Blob {
    [byte] data
}

WithDefault d;
Blob b;

print(static::is_memset_safe(d));   // 1
print(static::is_trivial(d));      // 0: field default
print(static::is_memcpy_safe(b));     // 0: dynamic array field
print(static::is_trivial(b));      // 0: dynamic array field
print(static::is_same_type_assignable(b)); // 0: no built-in assignment
```

A struct that is not assignable by default can opt in with a same-type
`OPERATOR_MOV`; `is_same_type_assignable` then returns `1`. Tuples cannot
declare operator methods, so for a tuple this predicate is exactly built-in
element assignability.

`is_passable_by_value` covers primitives, pointers, structs whose
fields are all passable by value, and tuples whose elements are all passable
by value. This is the same rule as built-in
whole-value assignment; it does not consider a custom `OPERATOR_MOV`
(use `is_same_type_assignable` for that). Static arrays and dynamic arrays
are not passable by value, so a struct or tuple containing one is not either:

```franko
struct WithArray {
    [int32, 3] values
}

WithArray w;
print(static::is_passable_by_value(w)); // 0: static array field
```

`is_same_type` compares exact declared types. Aliases normalize first, so
`int` and `int32` are the same type:

```franko
int a;
int32 b;
print(static::is_same_type(a, b)); // 1
```

`is_matchable` covers equality, and `is_comparable` covers ordering.
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
print(static::is_matchable(p));  // 1
print(static::is_comparable(p)); // 1
```

## 34.6 FFI target selection

The intended use of `static_if` is to select platform-specific FFI
calls. Only the selected branch is compiled, so the other platform's
functions do not need to exist on this target:

```franko
import "franko/static"
import "franko/os"
import "franko/io"

func main() -> int32 {
    [byte] msg;
    msg.init(5);
    msg.memcpy("hello");

    static_if (static::IS_WINDOWS) {
        uint64 h = os::win32::GetStdHandle(os::win32::STD_OUTPUT_HANDLE);
        os::win32::WriteFile(h, getview(msg), 5, null);
    } static_else static_if (static::IS_LINUX || static::IS_MACOS) {
        os::posix::write(os::posix::STDOUT_FILENO, getview(msg), 5);
    }

    msg.uninit();
    return 0;
}
```

An unguarded off-target FFI call still fails when building for that
target.

## 34.7 Restrictions and edge cases

- `static_if` is not allowed at global scope.
- `static_if` cannot appear in a `for` header.
- The condition must be a compile-time constant integer. A local variable
  is not a compile-time constant, even when it is initialized with a
  literal.
- Target constants and introspection calls cannot be used in declaration
  positions that require a literal: static array sizes, global primitive
  initializers, and field defaults.
- Using `static::` without importing `franko/static` reports the import
  hint shown in [Importing the module](#341-importing-the-module).
- `static_if` is statement-only; it never produces a value.
- `static::` introspection functions are built-in compile-time
  functions, not callable values.
- A runtime `view<[byte]>` variable cannot be passed to `static::strcmp`
  or `static::panic`; those arguments must be compile-time strings.
- Empty string literals are rejected in `static::strcmp` and
  `static::panic`.
