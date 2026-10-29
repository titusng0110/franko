# 20. Arrays

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

However, Franko supports **array literals** as `view<array<T, N>>` values over constant global arrays; arrays receive their elements through `memcpy`. See [Array Literals](22-array-literals.md).

## 20.1 Static Arrays

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
array<byte, ?> b = [104, 105];        // ? = 2
```

(String literals are `view<array<byte, N>>` values and do not
participate in `?` inference; see [String Literals](23-string-literals.md).)

The current element count of any array is available through the `length()` intrinsic (see [Array Intrinsics](21-array-intrinsics.md)).

## 20.2 Dynamic Arrays

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

An initialized dynamic array may be resized with `resize`. See [Array Intrinsics](21-array-intrinsics.md).

Dynamic array declaration alone does not allocate element storage.

The array must be initialized before ordinary indexed use.

An initialized dynamic array owns constructed elements: for element types that need construction (for example nested dynamic arrays), `init` and `init_zero` default-construct every element into the uninitialized state, so inner arrays are immediately ready for `init` without further setup.

The call-initialization form is only valid for dynamic arrays:

```franko
int32_t x(5);   // invalid: x is not a dynamic array
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
  - must fit `uint32_t`;
- nonconstant index:
  - must have exactly type `uint32_t`.

For static arrays, constant indexes are checked against the known static size when possible.

## 20.4 Array Elements as Lvalues

Array elements are storage-backed lvalues when the array target is storage-backed.

Array elements may be pointed to:

```franko
ptr<int32_t> p;
p = getptr(xs[0]);
```

Nested array elements may also be lvalues when each target is storage-backed.

## 20.5 Array Direct Assignment Is Invalid

Arrays cannot be assigned directly as ordinary values.

Invalid:

```franko
array<int32_t> a;
array<int32_t> b;

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