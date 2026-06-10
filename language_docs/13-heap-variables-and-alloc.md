# 13. Heap Variables and `alloc`

Franko supports heap-owned variables using `alloc`.

Syntax:

```franko
alloc int x;
alloc int32_t y;
alloc array<int> arr;
```

The `alloc` keyword marks the variable as heap-owned. Heap-owned variables may be deleted with `del`.

Non-heap variables cannot be deleted.

## 13.1 Allocated Dynamic Array Initialization Sugar

```franko
alloc array<int> arr(20);
```

is desugared to:

```franko
alloc array<int> arr;
arr(20);
```
