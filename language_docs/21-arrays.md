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

## 21.6 Array Lifetime Checking

The compiler validates intrinsic legality and checks the initialization
lifecycle of function-local dynamic array variables and owner `ptr<[T]>`
aliases. Definite use before init, use after `uninit`, invalid free, and
double free are compile errors; possible cases are warnings. Repeated `init`
is not diagnosed because an earlier call may have failed with `-1` and a
retry is valid; the runtime still aborts on a true double init. Missing an
explicit `uninit()` is a warning because the runtime destructor still frees
local buffers.

A call is checked against what the callee does: a callee can require an
initialized array, initialize, resize, or free the caller's array, or return
an element pointer into it. An owner `ptr<[T]>` or whole-array
`view<[T]>`/`mview<[T]>` points at the array object, which keeps the same
address across `init`, `resize`, and `uninit`; those whole-array pointers
remain valid. Only element pointers/views created from
`getptr`/`getmview`/`getview(d[i])` point into the element buffer and are
invalidated when the buffer is replaced or freed.

An element pointer/view is tied to the buffer that existed when it was
created. Using one whose buffer has definitely been replaced is an error; a
possibly stale one is a warning. This applies at dereference, indexing,
assignment through the alias, call argument, return, and creation intrinsics
over the alias. A whole-array view is not an element alias and is never
invalidated by `resize`/`uninit`. A call that may invalidate array parameter
`i` while using an element-pointer parameter `j` that aliases `i` produces a
possible cross-parameter invalidation warning. See
[Dynamic-Array Lifecycle Checking](32-memory-safety-checking.md#2910-dynamic-array-lifecycle-checking).

The check does not yet cover:

- dynamic arrays in struct fields or nested dynamic-array elements below the
  outermost tracked array;
- global dynamic-array lifecycle;
- runtime bounds violations for nonconstant indexes;
- allocation failure status of `init`/`init_zero`.

## 21.7 Owning Pointers in Arrays

An array may not store an owning `ptr<T>` value, directly or recursively:

```franko
[ptr<int32>] a;           // invalid
[ptr<int32>, 4] b;        // invalid
[[ptr<int32>]] c;         // invalid: nested
[view<ptr<int32>>] d;     // invalid: the view's referent contains a ptr
```

`view<T>` and `mview<T>` elements are allowed, as long as their referent
types do not recursively contain an owning pointer. This is a declaration-type
error:

```text
owning pointer type 'ptr<T>' cannot appear inside an array, struct, or
tuple, recursively (including through view<T>/mview<T> referents) ...
```

The reason is lifetime ownership: an owning pointer carries the right to
allocate and free storage. Embedding one in an array lets that responsibility
escape into unrelated code and makes the storage lifetime impossible to
reason about. Keep owning pointers in direct variables, parameters, or
globals, and use `view<T>`/`mview<T>` inside arrays. For linked or node-like
data, use the pool allocator from `franko/pool`.

When the goal is an array of arrays, prefer a nested array:

```franko
[[int32]] rows;       // good: the outer array owns the inner arrays
[ptr<[int32]>] rows;  // invalid: ownership of inner arrays is passed around
```

A nested dynamic array owns its inner arrays, and `init`, `resize`, and
`uninit` manage their storage and lifetime automatically. It is the safe way
to represent a dynamically sized matrix or a list of buffers.
