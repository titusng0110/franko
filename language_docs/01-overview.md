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
- `for`;
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
- `switch`, `do`/`do-while`, `break`, and `continue`;
- multiple declarators in one declaration (`int a, b;`).

Franko currently targets C++14.