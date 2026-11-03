# 20. Arrays

Franko supports two array categories:

```franko
[T]      // dynamic array
[T, N]   // static array
```

Examples:

```franko
[int32] dynamic_ints;
[uint8, 128] static_bytes;
```

Arrays may be nested:

```franko
[[int32]] nested_dynamic;
[[int32, 4], 8] matrix;
```

Arrays are not arithmetic values and cannot be directly assigned as ordinary values.

However, Franko supports **array literals** as `view<[T, N]>` values over immutable constant storage; arrays receive their elements through `memcpy`. See [Array Literals](22-array-literals.md).

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

Static arrays cannot use `init`; fill them with `memset` or `memcpy`.

The size may be written as `?` in a `view` declaration with an
array-literal initializer (see [Desugaring](05-desugaring.md)):

```franko
view<[int32, ?]> a = [0, 1, 2, 3];   // ? = 4
view<[byte, ?]> b = [104, 105];      // ? = 2
```

(`?` requires a `view<[...]>` declaration; a bare static array such as
`[int32, ?]` is rejected. String literals are `view<[byte, N]>` values
and do not participate in `?` inference; see
[String Literals](23-string-literals.md).)

The current element count of any array is available through the `length()` intrinsic (see [Array Intrinsics](21-array-intrinsics.md)).

## 20.2 Dynamic Arrays

A dynamic array has runtime size:

```franko
[int32] xs;
```

Dynamic arrays are initialized with the array intrinsics `init` and `init_zero`:

```franko
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

## 20.6 Array Lifetime Checking

The compiler validates intrinsic legality and also tracks the
initialization lifecycle of function-local dynamic array variables and
owner `ptr<[T]>` aliases. Definite use before init, use after `uninit`,
invalid free, and double free are compile errors; possible cases are
warnings.
Repeated `init` is not diagnosed because an earlier call may have failed
with `-1` and a retry is valid; the runtime still aborts on a true
double init. Missing an explicit `uninit()` is a warning because the
runtime destructor still frees local buffers. See
[Dynamic-Array Lifecycle Checking](27-memory-safety-checking.md#2710-dynamic-array-lifecycle-checking).

The check does not yet cover:

- dynamic arrays in struct fields or nested dynamic-array elements;
- `mview<[T]>`/`view<[T]>` aliases;
- callee effects beyond marking owner-pointer arguments as possibly
  initialized;
- runtime bounds violations for nonconstant indexes.