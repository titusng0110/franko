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
- standard modules including `franko/io`, `franko/os`, `franko/pool`,
  `franko/limit`, and `franko/string`;
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
