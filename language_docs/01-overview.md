# 1. Overview

Franko is a small statically typed language with:

- fixed-width signed and unsigned integer types;
- structs with named fields (`struct X { int32 n }`);
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
- string literals as read-only `view<[byte, N]>` values;
- array intrinsics:
  - `arr(size)` (shorthand for `arr.init(size)`);
  - `arr.init(size)`;
  - `arr.init_zero(size)`;
  - `arr.resize(size)`;
  - `arr.uninit()`;
  - `arr.memset(value)` and `arr.memset(value, start, count)`;
  - `arr.memcpy(source)` and `arr.memcpy(source, dstStart, srcStart, count)`;
  - `arr.memmove(dstStart, srcStart, count)`;
  - `arr.memcmp(source)` and `arr.memcmp(source, dstStart, srcStart, count)`;
  - `arr.length()`;
  - byte-array searches: `arr.memchr(...)`, `arr.memmem(...)`;
  - byte-array endian accessors: `arr.read_uint16_LE(offset)` … `arr.write_int64_BE(offset, value)`;
  - byte-array bit intrinsics: `read_bit`, `write_bit`, `flip_bit`,
    `bitwise_*_mask`, `shift_bits_*`, `count_set_bits`
    (see [Array Intrinsics](21-array-intrinsics.md));
- array literals as `view<[T, N]>` values over constant global
  arrays (element type inferred, see [Array Literals](22-array-literals.md));
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

## Compiling programs

Build a program with the bundled compile script:

```bash
uv run python compile.py programs/bfs.fr
```

This is the default debug mode: the generated code is compiled with
`-O1` plus AddressSanitizer (including leak checking) and uses the
system allocator. For an optimized binary, pass `-O`:

```bash
uv run python compile.py -O programs/bfs.fr
```

`-O` compiles with `-O3`, no sanitizer, and the bundled jemalloc
allocator instead of the system one. A third mode, `-Omax`, is the same
optimized build but swaps in an unchecked runtime: all array bounds
checks and `unsafe_cast` range checks are stripped.

```bash
uv run python compile.py -Omax programs/bfs.fr
```

All modes implement the same language with identical compiler
diagnostics and program output; only the allocator, the optimization
level, and the runtime checking differ. Debug mode reports memory
errors and leaks (for example a missing `arr.uninit()`) at run time, so
develop and test without `-O` and use `-O` for the final fast binary.

`-Omax` is for developers who have already validated their program and
want maximum speed. Out-of-range array access and out-of-range
`unsafe_cast` values are **undefined behavior** in this mode — they
silently read or write whatever memory is there and silently wrap or
truncate cast results, instead of aborting like the checked modes. Prefer
`-O` unless profiling shows the checks themselves are the bottleneck.