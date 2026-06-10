# 1. Overview

Franko is a small statically typed language with:

- fixed-width signed and unsigned integer types;
- dynamic arrays;
- static arrays;
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
- `return`;
- `print`;
- `del`;
- heap-owned variables using `alloc`;
- array intrinsics:
  - `arr(size)`;
  - `arr.uninit()`;
  - `arr.memset(value)`;
  - `arr.memcpy(source)`;
- array initializer lists as array element assignment sugar.

Franko currently does **not** implement:

- nested functions;
- first-class function values;
- anonymous functions or lambdas;
- function pointers;
- structs/classes;
- fields;
- general methods;
- generics;
- imports/modules;
- floating-point types;
- a distinct boolean type;
- string literals;
- implicit numeric conversions except for fluid integer constants.

Franko currently targets C++14.
