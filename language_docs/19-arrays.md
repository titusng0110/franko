# 19. Arrays

Franko supports two array categories:

```franko
array<T>      // dynamic array
array<T, N>   // static array
```

Examples:

```franko
array<int32_t> dynamicInts;
array<uint8_t, 128> staticBytes;
```

Arrays may be nested:

```franko
array<array<int32_t>> nestedDynamic;
array<array<int32_t, 4>, 8> matrix;
```

Arrays are not arithmetic values and cannot be directly assigned as ordinary values.

However, Franko supports **array initializer lists** as assignment sugar for assigning individual elements into an existing array target. See [Array Initializer Lists](21-array-initializer-lists.md).

## 19.1 Static Arrays

A static array has a compile-time size:

```franko
array<int32_t, 10> xs;
```

Static array size rules:

- size must be a compile-time integer constant;
- size must be greater than zero;
- size must fit `uint32_t`.

Valid:

```franko
array<int32_t, 1> a;
array<int32_t, 0x10> b;
array<int32_t, 0b1000> c;
array<int32_t, 1 + 2> d;
```

Invalid:

```franko
array<int32_t, 0> a;
array<int32_t, -1> b;
array<int32_t, 999999999999999999999999999999999> c;
```

Static arrays do not use `arr(size)` initialization.

The size may be written as `?` to infer it from a declaration initializer (see [Desugaring](05-desugaring.md)):

```franko
array<int32_t, ?> a = [0, 1, 2, 3];   // ? = 4
array<byte, ?> s = "hello";           // ? = 5
```

The current element count of any array is available through the `length()` intrinsic (see [Array Intrinsics](20-array-intrinsics.md)).

## 19.2 `ndarray` Sugar

`ndarray<T, D1, D2, ..., Dn>` is syntactic sugar for nested static arrays:

```franko
ndarray<byte, 12, 20, 30>
```

means:

```franko
array<array<array<byte, 30>, 20>, 12>
```

Access order follows the dimension list: `arr[i][j][k]` indexes `i` along `12`, `j` along `20`, and `k` along `30`.

Each dimension follows the same static-array size rules above (a positive compile-time integer constant folding to a value that fits `uint32_t`).

Any dimension may be `?` when the `ndarray` is declared with an array-literal initializer; each `?` is inferred independently from the corresponding literal level:

```franko
ndarray<int32_t, ?, ?> g = [[1, 2, 3], [4, 5, 6]];   // 2 by 3
ndarray<byte, 2, ?, 3> h = [[[1, 2, 3], [4, 5, 6]], [[7, 8, 9], [10, 11, 12]]];
```

## 19.3 Dynamic Arrays

A dynamic array has runtime size:

```franko
array<int32_t> xs;
```

Dynamic arrays are initialized with the array intrinsic `init` (or one of its aliases):

```franko
xs(10);             // shorthand for xs.init(10)
xs.init(10);        // allocate uninitialized storage
xs.init_zero(10);   // allocate zeroed storage
```

Rules for dynamic array initialization:

- target must be a storage-backed lvalue;
- target must be a dynamic array;
- exactly one argument is required;
- size must be compatible with `uint32_t`;
- constant sizes must be greater than zero;
- constant sizes must fit `uint32_t`;
- nonconstant sizes must have exactly type `uint32_t`.

An initialized dynamic array may be resized with `resize`. See [Array Intrinsics](20-array-intrinsics.md).

Dynamic array declaration alone does not allocate element storage.

The array must be initialized before ordinary indexed use.

The call-initialization form is only valid for dynamic arrays:

```franko
int32_t x(5);   // invalid: x is not a dynamic array
```

## 19.4 Array Access

Array indexing syntax:

```franko
arr[index]
```

The target must be an array.

Index rules:

- constant index:
  - must be nonnegative;
  - must fit `uint32_t`;
- nonconstant index:
  - must have exactly type `uint32_t`.

For static arrays, constant indexes are checked against the known static size when possible.

## 19.5 Array Elements as Lvalues

Array elements are storage-backed lvalues when the array target is storage-backed.

Array elements may be addressed:

```franko
addr<int32_t> p;
p = getaddr(xs[0]);
```

Nested array elements may also be lvalues when each target is storage-backed.

## 19.6 Array Direct Assignment Is Invalid

Arrays cannot be assigned directly as ordinary values.

Invalid:

```franko
array<int32_t> a;
array<int32_t> b;

a = b;
```

Use one of:

```franko
a.memcpy(getaddr(b));
a.memset(0);
a.uninit();
```

Array initializer assignment is not whole-array value assignment.

## 19.7 Array Lifetime Limitations

The checker validates intrinsic legality, but it does not fully prove:

- dynamic array initialized before indexing;
- dynamic array not used after `uninit`;
- dynamic array not initialized twice;
- all array accesses are within bounds.