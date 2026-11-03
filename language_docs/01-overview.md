# 1. Overview

Franko is a small statically typed language with:

- fixed-width signed and unsigned integer types;
- structs with named fields (`struct X { int32 n }`);
- struct member access using `obj.field` (see [Structs](25-structs.md));
- dynamic arrays;
- static arrays;
- typed pointers using `ptr<T>`, `mview<T>`, and `view<T>`;
- explicit pointer operations:
  - `getptr(...)`, `getmview(...)`, and `getview(...)`;
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
    (see [Array Intrinsics](21-array-intrinsics.md));
- array literals as `view<[T, N]>` values over immutable constant
  storage (element type inferred, see [Array Literals](22-array-literals.md));
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
- C-style cast syntax;
- a ternary conditional operator;
- `switch` and `do`/`do-while`;
- multiple declarators in one declaration (`int a, b;`).

Build instructions and compiler flags are in the repository
[README](../README.md).
