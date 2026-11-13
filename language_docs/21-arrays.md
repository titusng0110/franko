# 21. Arrays

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

## 21.1 Static Arrays

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
array-literal initializer (see [Shorthands and Equivalences](25-desugaring.md)):

```franko
view<[int32, ?]> a = [0, 1, 2, 3];   // ? = 4
view<[byte, ?]> b = [104, 105];      // ? = 2
```

(`?` requires a `view<[...]>` declaration; a bare static array such as
`[int32, ?]` is rejected. String literals are `view<[byte, N]>` values
and do not participate in `?` inference; see
[String Literals](24-string-literals.md).)

The current element count of any array is available through the `length()` intrinsic (see [Array Intrinsics](23-array-intrinsics.md)).

## 21.2 Dynamic Arrays

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

An initialized dynamic array may be resized with `resize`. See [Array Intrinsics](23-array-intrinsics.md).

Dynamic array declaration alone does not allocate element storage.

The array must be initialized before ordinary indexed use.

An initialized dynamic array owns constructed elements: for element types that need construction (for example nested dynamic arrays and pointers), `init` and `init_zero` default-construct every element. Inner arrays start in the uninitialized state (length `0`), ready for `init`, and pointer elements start null. Growing `resize` default-constructs the new tail elements, so pointer elements added by a resize are null too.

## 21.3 Array Access

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

## 21.4 Array Elements as Lvalues

Array elements are storage-backed lvalues when the array target is storage-backed.

Array elements may be pointed to:

```franko
ptr<int32> p;
p = getptr(xs[0]);
```

Nested array elements may also be lvalues when each target is storage-backed.

## 21.5 Array Direct Assignment Is Invalid

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

Array lifecycle and buffer-invalidation rules are covered in
[Dynamic-Array Lifecycle Checking](32-memory-safety-checking.md#3210-dynamic-array-lifecycle-checking).
Owning pointers are forbidden in arrays and other aggregates; see
[Owning Pointers in Aggregates](26-pointers.md#2613-owning-pointers-in-aggregates-are-forbidden).
