# 1. Overview

Franko is a small statically typed language with:

- fixed-width signed and unsigned integer types;
- structs with named fields (`struct X { int32_t n }`);
- struct member access using `obj.field` (see [Structs](25-structs.md));
- dynamic arrays;
- static arrays;
- `ndarray<...>` sugar for nested static arrays;
- typed pointers using `ptr<T>`;
- explicit pointer operations:
  - `getptr(...)`;
  - `$(...)`;
- user-defined functions;
- function overloading;
- function calls inside function bodies;
- struct methods with explicit `ptr<X> self` receivers (see [Methods](26-methods.md));
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
- general string expressions (string literals are initializer sugar only, see [String Literals](23-string-literals.md));
- implicit numeric conversions except for fluid integer constants (explicit width changes use the `cast<T>(expr)` intrinsic);
- cast syntax;
- a ternary conditional operator;
- `switch` and `do`/`do-while`;
- multiple declarators in one declaration (`int a, b;`).

Franko currently targets C++14.