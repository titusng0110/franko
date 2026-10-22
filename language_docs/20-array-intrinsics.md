# 20. Array Intrinsics

Array operations are written using call syntax but lowered into special semantic nodes. They are **not** general user-defined function calls, and only these named intrinsics are recognized.

Intrinsics are **expressions**, not statement-only forms. Status-returning intrinsics may be used in value contexts:

```franko
int32_t status;
status = xs.init(10);
```

Void-returning intrinsics are invalid in value contexts.

| Intrinsic | Receiver | Returns | Status |
|---|---|---|---|
| `target(size)`, `target.init(size)` | dynamic array | `int32_t` | `0` on success, `-1` on allocation failure |
| `target.init_zero(size)` | dynamic array | `int32_t` | `0` on success, `-1` on allocation failure |
| `target.resize(size)` | dynamic array | `int32_t` | `0` on success, `-1` on allocation failure |
| `target.uninit()` | dynamic array | `void` | — |
| `target.memset(value)` | any array | `void` | — |
| `target.memset(value, start, count)` | any array | `void` | — |
| `target.memcpy(source)` | any array | `void` | — |
| `target.memcpy(source, dstStart, srcStart, count)` | any array | `void` | — |
| `target.memmove(dstStart, srcStart, count)` | any array | `void` | — |

The receiver of an array intrinsic must be a storage-backed array lvalue.

Examples of storage-backed array targets:

```franko
arr
deref(p)
arrs[i]
```

Examples that are not storage-backed array targets:

```franko
getaddr(arr)
x + y
1
```

## 20.1 `init`, `init_zero`, and `target(size)`

Dynamic array initialization.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must have dynamic array type;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32_t`; or
  - a nonconstant expression of exactly type `uint32_t`.

`init` allocates uninitialized element storage. `init_zero` allocates zeroed element storage. `target(size)` is shorthand for `target.init(size)`.

## 20.2 `resize`

Resizes an already-initialized dynamic array.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must be a dynamic array;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32_t`; or
  - a nonconstant expression of exactly type `uint32_t`.

## 20.3 `uninit`

Frees a dynamic array's element storage and resets it to the uninitialized state.

Rules:

- receiver must be a storage-backed lvalue;
- receiver must be a dynamic array;
- takes exactly zero arguments.

## 20.4 `memset`

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
  - a constant fitting `uint8_t`; or
  - a nonconstant expression of exactly type `uint8_t`;
- `start` and `count` must be:
  - nonnegative constants fitting `uint32_t`; or
  - nonconstant expressions of exactly type `uint32_t`;
- for static arrays, constant start/count ranges are checked against the static size at compile time.

## 20.5 Memsetable Element Types

Current memsetable types:

| Element type | Memsetable? |
|---|---|
| primitive integers | yes |
| static arrays of memsetable elements | yes |
| structs whose fields are all memsetable, recursively | yes |
| dynamic arrays | no |
| addresses | no |

## 20.6 `memcpy`

Copies array contents byte-wise according to the generated C++ array model.

Forms:

```franko
target.memcpy(source)                          // whole array
target.memcpy(source, dstStart, srcStart, count)  // element ranges
```

Rules:

- target must be a storage-backed lvalue array;
- source must be an **address to an array** (`addr<array<T>>` or `addr<array<T, N>>`), typically produced with `getaddr(...)`; passing an array value directly is invalid;
- both target and source may be dynamic or static arrays;
- target and source element types must match exactly;
- static/dynamic shape does not need to match;
- static lengths do not need to match;
- target and source element types must be memcpyable;
- `dstStart`, `srcStart`, and `count` must be:
  - nonnegative constants fitting `uint32_t`; or
  - nonconstant expressions of exactly type `uint32_t`;
- for static arrays, constant start/count ranges on both the destination and the source are checked against the static sizes at compile time.

## 20.7 `memmove`

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
  - nonnegative constants fitting `uint32_t`; or
  - nonconstant expressions of exactly type `uint32_t`;
- for static arrays, constant ranges are checked against the static size at compile time.

## 20.8 Memcpyable Element Types

Current memcpyable types:

| Element type | Memcpyable? |
|---|---|
| primitive integers | yes |
| addresses | yes |
| static arrays of memcpyable elements | yes |
| structs whose fields are all memcpyable, recursively | yes |
| dynamic arrays | no |