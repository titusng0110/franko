# 21. Array Intrinsics

Array operations are written using call syntax but lowered into special semantic nodes. They are **not** general user-defined function calls, and only these named intrinsics are recognized.

Intrinsics are **expressions**, not statement-only forms. Status-returning intrinsics may be used in value contexts:

```franko
int32 status;
status = xs.init(10);
```

Void-returning intrinsics are invalid in value contexts.

| Intrinsic | Receiver | Returns | Status |
|---|---|---|---|
| `target(size)`, `target.init(size)` | dynamic array | `int32` | `0` on success, `-1` on allocation failure |
| `target.init_zero(size)` | dynamic array | `int32` | `0` on success, `-1` on allocation failure |
| `target.resize(size)` | dynamic array | `int32` | `0` on success, `-1` on allocation failure |
| `target.uninit()` | dynamic array | `void` | — |
| `target.memset(value)` | any array | `void` | — |
| `target.memset(value, start, count)` | any array | `void` | — |
| `target.memcpy(source)` | any array | `void` | — |
| `target.memcpy(source, dstStart, srcStart, count)` | any array | `void` | — |
| `target.memmove(dstStart, srcStart, count)` | any array | `void` | — |
| `target.length()` | any array | `uint32` | — |

The receiver of a mutating array intrinsic must be a storage-backed array lvalue.

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

`length()` is read-only, so its receiver may be any array-typed expression; it does not need to be a storage-backed lvalue.

## 21.1 Receiver Permissions

The array intrinsics participate in the pointer permission model (see
[Pointers](24-pointers.md)). When a receiver is reached through a
pointer dereference, the intrinsic needs that pointer's permission:

| Intrinsic | Required receiver permission |
|---|---|
| `init`, `init_zero`, `resize`, `uninit`, `target(size)` | allocation (`ptr`) |
| `memset`, `memcpy`, `memmove` (as target) | mutation (`mview` or `ptr`) |
| `length()` and element reads | read (any permission) |

The `memcpy`/`memmove` *source* is only read, so it may be any pointer
to an array.

```franko
view<[byte]> v = getview(buf);
$(v)[0] = 1;         // blocked: view is read-only
$(v).memset(0);      // blocked: mutating intrinsic
$(v).length();       // allowed

mview<[byte]> m = getmview(dyn);
$(m)[0] = 1;         // allowed
$(m).init(10);       // blocked: init needs allocation rights (ptr)
```

## 21.2 `init`, `init_zero`, and `target(size)`

Dynamic array initialization.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must have dynamic array type;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32`; or
  - a nonconstant expression of exactly type `uint32`.

`init` allocates uninitialized element storage. `init_zero` allocates zeroed element storage. `target(size)` is shorthand for `target.init(size)`.

Element types that need construction (for example nested dynamic arrays) are default-constructed by `init` and `init_zero`, so inner arrays start in the uninitialized state (length `0`), ready for `init`; trivially constructible element types keep raw (`init`) or zeroed (`init_zero`) storage with no per-element work.

## 21.3 `resize`

Resizes an already-initialized dynamic array. Growing default-constructs the new tail elements; shrinking destroys the dropped tail elements. Trivial element types skip both.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must be a dynamic array;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32`; or
  - a nonconstant expression of exactly type `uint32`.

## 21.4 `uninit`

Frees a dynamic array's element storage and resets it to the uninitialized state. Constructed elements are destroyed before the buffer is freed; trivially destructible element types skip destruction.

Rules:

- receiver must be a storage-backed lvalue;
- receiver must be a dynamic array;
- takes exactly zero arguments.

## 21.5 `memset`

Fills an array byte-wise.

Forms:

```franko
target.memset(value)                    // whole array
target.memset(value, start, count)      // element range
```

Rules:

- receiver must be a storage-backed lvalue;
- receiver may be a dynamic or static array;
- receiver element type must be memsetable;
- the whole-array form takes exactly one argument; the ranged form takes exactly three;
- fill value must be:
  - a constant fitting `uint8`; or
  - a nonconstant expression of exactly type `uint8`;
- `start` and `count` must be:
  - nonnegative constants fitting `uint32`; or
  - nonconstant expressions of exactly type `uint32`;
- for static arrays, constant start/count ranges are checked against the static size at compile time.

## 21.6 Memsetable Element Types

Current memsetable types:

| Element type | Memsetable? |
|---|---|
| primitive integers | yes |
| static arrays of memsetable elements | yes |
| structs whose fields are all memsetable, recursively | yes |
| dynamic arrays | no |
| pointers | no |

## 21.7 `memcpy`

Copies array contents byte-wise according to the generated C++ array model.

Forms:

```franko
target.memcpy(source)                          // whole array
target.memcpy(source, dstStart, srcStart, count)  // element ranges
```

Rules:

- target must be a storage-backed lvalue array;
- source must be an **pointer to an array** (`ptr<[T]>` or `ptr<[T, N]>`), typically produced with `getptr(...)`, or an array literal (its element type is inferred from the target); passing an array value directly is invalid;
- both target and source may be dynamic or static arrays;
- target and source element types must match exactly;
- static/dynamic shape does not need to match;
- static lengths do not need to match;
- target and source element types must be memcpyable;
- `dstStart`, `srcStart`, and `count` must be:
  - nonnegative constants fitting `uint32`; or
  - nonconstant expressions of exactly type `uint32`;
- for static arrays, constant start/count ranges on both the destination and the source are checked against the static sizes at compile time.

## 21.8 `memmove`

Copies an element range within one array, overlapping ranges included.

Form:

```franko
target.memmove(dstStart, srcStart, count)
```

Rules:

- receiver must be a storage-backed lvalue array (dynamic or static);
- takes exactly three arguments;
- receiver element type must be memcpyable;
- `dstStart`, `srcStart`, and `count` must be:
  - nonnegative constants fitting `uint32`; or
  - nonconstant expressions of exactly type `uint32`;
- for static arrays, constant ranges are checked against the static size at compile time.

## 21.9 Memcpyable Element Types

Current memcpyable types:

| Element type | Memcpyable? |
|---|---|
| primitive integers | yes |
| pointers | yes |
| static arrays of memcpyable elements | yes |
| structs whose fields are all memcpyable, recursively | yes |
| dynamic arrays | no |

## 21.10 `length`

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