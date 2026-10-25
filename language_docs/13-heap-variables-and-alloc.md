# 13. Heap Variables and `alloc`

Franko supports heap-owned variables using `alloc`.

Syntax:

```franko
alloc int x;
alloc int32_t y;
alloc array<int> arr;
```

The `alloc` keyword marks the variable as heap-owned. Heap-owned variables may be deleted with `del`.

`alloc` is a declaration modifier only. There is no standalone allocation statement, and a variable cannot be re-allocated: redeclaring the same name is a duplicate-declaration error, and after `del x` the name `x` cannot be allocated again in the same scope.

`alloc` is only valid for local declarations inside function bodies and blocks. Global variables are always statically allocated; `alloc` at global scope is a compile-time error.

Non-heap variables cannot be deleted.

Heap variables must be deleted before the end of their scope and before returning; leaks, double deletes, and use-after-delete are checked by the memory safety pass. See [Memory Safety Checking](28-memory-safety-checking.md).

Heap allocation is not zero-initialized. A heap variable is allocated and default-constructed, so a primitive heap variable starts with an indeterminate value, and a heap dynamic array starts empty (length 0, no element storage). Use `init_zero` for zeroed dynamic array storage. Allocation failure raises `std::bad_alloc`.

## 13.1 Allocated Dynamic Array Initialization Sugar

```franko
alloc array<int> arr(20);
```

is desugared to:

```franko
alloc array<int> arr;
arr(20);
```
