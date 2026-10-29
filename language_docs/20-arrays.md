# 20. Arrays

Franko supports two array categories:

```franko
[T]      // dynamic array
[T, N]   // static array
```

Examples:

```franko
[int32] dynamicInts;
[uint8, 128] staticBytes;
```

Arrays may be nested:

```franko
[[int32]] nestedDynamic;
[[int32, 4], 8] matrix;
```

Arrays are not arithmetic values and cannot be directly assigned as ordinary values.

However, Franko supports **array literals** as `view<[T, N]>` values over constant global arrays; arrays receive their elements through `memcpy`. See [Array Literals](22-array-literals.md).

## 20.1 Static Arrays

A static array has a compile-time size:

```franko
[int32, 10] xs;
```

Static array size rules:

- size must be a compile-time integer constant;
- size must be greater than zero;
- size must fit `uint32`.

Valid:

```franko
[int32, 1] a;
[int32, 0x10] b;
[int32, 0b1000] c;
[int32, 1 + 2] d;
```

Invalid:

```franko
[int32, 0] a;
[int32, -1] b;
[int32, 999999999999999999999999999999999] c;
```

Static arrays do not use `arr(size)` initialization.

The size may be written as `?` to infer it from a declaration initializer (see [Desugaring](05-desugaring.md)):

```franko
[int32, ?] a = [0, 1, 2, 3];   // ? = 4
[byte, ?] b = [104, 105];        // ? = 2
```

(String literals are `view<[byte, N]>` values and do not
participate in `?` inference; see [String Literals](23-string-literals.md).)

The current element count of any array is available through the `length()` intrinsic (see [Array Intrinsics](21-array-intrinsics.md)).

## 20.2 Dynamic Arrays

A dynamic array has runtime size:

```franko
[int32] xs;
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
- size must be compatible with `uint32`;
- constant sizes must be greater than zero;
- constant sizes must fit `uint32`;
- nonconstant sizes must have exactly type `uint32`.

An initialized dynamic array may be resized with `resize`. See [Array Intrinsics](21-array-intrinsics.md).

Dynamic array declaration alone does not allocate element storage.

The array must be initialized before ordinary indexed use.

An initialized dynamic array owns constructed elements: for element types that need construction (for example nested dynamic arrays), `init` and `init_zero` default-construct every element into the uninitialized state, so inner arrays are immediately ready for `init` without further setup.

The call-initialization form is only valid for dynamic arrays:

```franko
int32 x(5);   // invalid: x is not a dynamic array
```

## 20.3 Array Access

Array indexing syntax:

```franko
arr[index]
```

The target must be an array.

Index rules:

- constant index:
  - must be nonnegative;
  - must fit `uint32`;
- nonconstant index:
  - must have exactly type `uint32`.

For static arrays, constant indexes are checked against the known static size when possible.

## 20.4 Array Elements as Lvalues

Array elements are storage-backed lvalues when the array target is storage-backed.

Array elements may be pointed to:

```franko
ptr<int32> p;
p = getptr(xs[0]);
```

Nested array elements may also be lvalues when each target is storage-backed.

## 20.5 Array Direct Assignment Is Invalid

Arrays cannot be assigned directly as ordinary values.

Invalid:

```franko
[int32] a;
[int32] b;

a = b;
```

Use one of:

```franko
a.memcpy(getptr(b));
a.memset(0);
a.uninit();
```

An array literal is not an array value; arrays receive literal
elements through `memcpy` (see [Array Literals](22-array-literals.md)).

## 20.6 Array Lifetime Limitations

The checker validates intrinsic legality, but it does not fully prove:

- dynamic array initialized before indexing;
- dynamic array not used after `uninit`;
- dynamic array not initialized twice;
- all array accesses are within bounds.