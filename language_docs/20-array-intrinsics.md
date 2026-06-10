# 20. Array Intrinsics

Array operations are written using call syntax but lowered into special semantic nodes.

Supported array intrinsics:

```franko
target(size)
target.uninit()
target.memset(value)
target.memcpy(source)
```

These are not general function calls.

They are only recognized in statement position.

The receiver or target of an array intrinsic must usually be a storage-backed array lvalue.

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

## 20.1 `target(size)`

Dynamic array initialization.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must have dynamic array type;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32_t`; or
  - a nonconstant expression of exactly type `uint32_t`.

## 20.2 `target.uninit()`

Dynamic array uninitialization.

Rules:

- receiver must be a storage-backed lvalue;
- receiver must be a dynamic array;
- takes exactly zero arguments.

## 20.3 `target.memset(value)`

Fills an array byte-wise.

Rules:

- receiver must be a storage-backed lvalue;
- receiver may be a dynamic or static array;
- receiver element type must be memsetable;
- takes exactly one argument;
- fill value must be:
  - a constant fitting `uint8_t`; or
  - a nonconstant expression of exactly type `uint8_t`.

## 20.4 Memsetable Element Types

Current memsetable types:

| Element type | Memsetable? |
|---|---|
| primitive integers | yes |
| static arrays of memsetable elements | yes |
| dynamic arrays | no |
| addresses | no |

## 20.5 `target.memcpy(source)`

Copies array contents byte-wise according to the generated C++ array model.

Rules:

- target must be a storage-backed lvalue array;
- source must be a storage-backed lvalue array;
- both target and source may be dynamic or static arrays;
- target and source element types must match exactly;
- static/dynamic shape does not need to match;
- static lengths do not need to match;
- element type must be memcpyable.

## 20.6 Memcpyable Element Types

Current memcpyable types:

| Element type | Memcpyable? |
|---|---|
| primitive integers | yes |
| addresses | yes |
| static arrays of memcpyable elements | yes |
| dynamic arrays | no |
