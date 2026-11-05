# 32. Compile-Time Reflection

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

Compile-time reflection happens during compilation. By the time the
program is fully checked and code is generated, every `#if` has already
chosen one branch, and every `#` expression has folded to an ordinary value
or declaration. The generated C++ contains none of the compile-time
machinery.

A useful way to read this chapter:

1. `#if` is not runtime `if`. Runtime `if` chooses what happens when the
   program runs; `#if` chooses what the program contains at all.
2. `#if` is a splice. The selected branch is inserted into the surrounding
   scope, and the unselected branch disappears.
3. Types used in reflection are compile-time information. They never become
   runtime values.

---

## 32.1 Compile-Time Names

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

## 32.2 `#if` / `#else`

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

Only the selected branch is compiled, checked, resolved, and emitted. The
unselected branch may refer to symbols or types that do not exist on the
current target. Syntax rules still apply to both branches.

Consequences:

- a selected `return` counts for the function's return check; a dead one
  does not;
- a selected `break`/`continue` is checked against the enclosing loop; a
  dead one is not;
- `#panic` in a dead branch is ignored;
- functions, globals, structs, fields, and methods declared in a dead
  branch do not enter the symbol table.

### Condition Errors

```text
#if condition must be a compile-time constant
#if condition must be an integer, got <type>
```

---

## 32.3 Declaration-Level `#if`

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
- Selected declarations are spliced into the enclosing scope.
- Declarations outside any `#if` are visible for reflection throughout
  their scope, including forward references.
- Declarations inside a selected branch become visible when that branch
  is spliced; later conditions in the same scope can inspect them.
- Declarations in an unselected branch are never registered.

---

## 32.4 Type and Value Arguments

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

## 32.5 Introspection Functions

All introspection calls use `#name(...)`.

| Function | Argument contract | Result | Semantics |
|---|---|---|---|
| `#is_primitive(x)` | one type or value | `uint8` | `1` iff the type is a primitive integer |
| `#is_memcpy_safe(x)` | one type or value | `uint8` | `1` iff the type is memcpy-safe: primitive, pointer, static array of memcpy-safe elements, struct with all fields memcpy-safe, or tuple with all elements memcpy-safe. Dynamic arrays are not memcpy-safe. |
| `#has_default_value(x)` | one type or value | `uint8` | `1` iff at least one part of the type has a defined default: pointers (null), dynamic arrays (empty), static arrays whose element type has a default, structs with any field default or any field whose type has a default, and tuples with any such element. `0` for primitive integers, static arrays of primitives, and aggregates whose parts are all raw-indeterminate. |
| `#is_memset_safe(x)` | one type or value | `uint8` | `1` iff the type is safe to byte-fill with `memset`: primitives, static arrays of memset-safe elements, structs whose fields are all memset-safe, and tuples whose elements are all memset-safe. Pointers and dynamic arrays are not. Field defaults do not affect this predicate. |
| `#is_same_type(x, y)` | two types/values | `uint8` | `1` iff the types are exactly equal; tuple element order matters, names do not |
| `#what_type(x)` | one type or value | compile-time string | canonical type spelling with no spaces after commas, including `(int32,int32)` for tuples; matches the function signature hashing key |
| `#strcat(a, b, ...)` | one or more compile-time strings | compile-time string | concatenates all arguments |
| `#strcmp(a, b)` | two compile-time strings | `int32` | lexicographic byte comparison; negative, zero, or positive |
| `#panic(message)` | one compile-time string | `void` | stops compilation with the message |
| `#is_matchable(x)` | one type or value | `uint8` | `1` for primitive integers and pointers; for structs, `1` iff a valid `OPERATOR_EQ(view<X> self, view<X> other) -> uint8` exists; tuples and arrays are `0` |
| `#is_comparable(x)` | one type or value | `uint8` | `1` for primitive integers; for structs, `1` iff a valid `OPERATOR_LT(view<X> self, view<X> other) -> uint8` exists; tuples, pointers, and arrays are `0` |
| `#is_assignable(x)` | one type or value | `uint8` | `1` for primitives and pointers; for structs, `1` iff built-in whole-value assignment is allowed or a valid `OPERATOR_MOV(ptr<X> self, view<X> other) -> void` exists; for tuples, `1` iff every element is assignable (tuples cannot declare `OPERATOR_MOV`); arrays are `0` |
| `#is_pure_value(x)` | one type or value | `uint8` | `1` iff a value of the type can be passed as a by-value function parameter: primitives, pointers, structs whose fields are all pure values, and tuples whose elements are all pure values. Static arrays and dynamic arrays are not. |

The predicates are built-in compile-time functions. They cannot be stored
in variables, passed as callbacks, or referred to without a call.

They cannot be used in positions that require a literal constant
expression, such as static array sizes, global primitive initializers, or
field defaults.

---

## 32.6 `#what_type` Spellings

`#what_type` produces a compile-time string. When used as an ordinary
expression, it materializes as a read-only `view<[byte, N]>` backed by
constant storage. It can be printed, assigned to a byte view, or passed to
a function accepting a byte view.

When consumed by `#strcmp`, `#panic`, or `#strcat`, it stays a compile-time
string and no string storage is emitted.

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

`byte` is an alias for `uint8`, so canonical spellings use `uint8`.

---

## 32.7 Compile-Time Strings

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

## 32.8 Choosing a Predicate

| Question | Predicate |
|---|---|
| Can I copy this type byte-for-byte with `memcpy`/`memcmp`? | `#is_memcpy_safe(x)` |
| Can I byte-fill this type with `memset`? | `#is_memset_safe(x)` |
| Does at least one part of this type have a defined default value? | `#has_default_value(x)` |
| Can I compare two values for equality? | `#is_matchable(x)` |
| Can I order two values with `<`? | `#is_comparable(x)` |
| Can I assign one value of this type to another? | `#is_assignable(x)` |
| Can I pass a value of this type to a function by value? | `#is_pure_value(x)` |

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

A struct that is not assignable by default can opt in with a same-type
`OPERATOR_MOV`; `#is_assignable` then returns `1`. Tuples cannot
declare operator methods, so for a tuple this predicate is exactly built-in
element assignability.

`#is_pure_value` covers primitives, pointers, structs whose fields
are all pure values, and tuples whose elements are all pure values. This is
the same rule as built-in whole-value assignment and by-value parameters; it
does not consider a custom `OPERATOR_MOV`.

```franko
struct WithArray {
    [int32, 3] values
}

WithArray w;
print(#is_pure_value(w)); // 0: static array field
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

## 32.9 FFI Target Selection

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

## 32.10 Restrictions and Edge Cases

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

## 32.11 Common Mistakes

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
