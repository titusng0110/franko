# 26. Pointers

Franko has a capability-based pointer system with three pointer types,
representing strict levels of permission:

| Type | Meaning | Allowed Operations |
|---|---|---|
| `ptr<T>` | Owner | Read, Write, Allocate, Free |
| `mview<T>` | Writer | Read, Write |
| `view<T>` | Reader | Read Only |

(`ptr` = pointer, `mview` = mutable view, `view` = view.) The three
types differ only in the permissions they grant; no pointer owns its
pointee automatically, and no pointer is tied to the lifetime of the
storage it refers to.

A pointer value refers to a storage location containing a value of type `T`.

Examples:

```franko
ptr<int32> p;
mview<uint8> b;
view<ptr<int32>> pp;
ptr<[int32]> ap;
```

Pointers are typed, assignable, copyable, dereferenceable, and comparable.

Pointers are not arithmetic values.

`ptr<void>`, `mview<void>`, and `view<void>` are invalid because `void`
is not an ordinary value type.

## 26.1 Creating Pointers

Views are created with intrinsics over storage-backed lvalues, in the
same shape as `getptr`:

- `getptr(expr)` creates a `ptr<T>`.
- `getmview(expr)` creates an `mview<T>`.
- `getview(expr)` creates a `view<T>`.

If the operand has type `T`, the result has the corresponding pointer
type. Creating a pointer demands that the operand's storage carries at
least the requested permission: `getptr` needs owner access,
`getmview` needs write access, `getview` always works. This is what
prevents laundering a `view` into a `ptr`.

User-written `getview`/`getmview`/`getptr` require a storage-backed
lvalue. The only exception is the implicit borrow used to pass an rvalue
operand to an overloaded struct operator; it is not available to user code,
so `getview(5)` or `getview(make_x())` remains invalid. See [Struct Operator Overloading](13-operators.md#1310-struct-operator-overloading).

Const storage allows only `getview`:

```franko
const int32 x = 1
view<int32> v = getview(x)    // valid
ptr<int32> p = getptr(x)      // error
mview<int32> m = getmview(x)  // error
```

This limits a const struct receiver to `view<X> self` methods.
`$(p)` follows the pointee's own type, not the constness of the variable
holding `p`: when `p` is a const `ptr<int32>`, `$(p) = 5` is valid and
`p = ...` is not. See [Declarations](18-declarations.md#188-const-storage).

## 26.2 Dereferencing with `$`

If the operand has type `ptr<T>`, `mview<T>`, or `view<T>`, then
`$(...)` has type `T`.

`$(...)` is an lvalue. Reading through it is allowed with any pointer
permission; writing through it requires mutation rights (`ptr` or
`mview`):

```franko
$(p)[i] = x;    // array element access (mutating: needs ptr or mview)
$(v)[i];        // reading an element (any permission)
$(v).field;     // struct field access
$(v).length();  // intrinsic over the pointee
```

## 26.3 Nested Pointers

Franko supports pointers to pointers, of any permission:

```franko
view<ptr<int32>> v;   // read-only view of a ptr<int32> slot
```

## 26.4 Assignment and Implicit Downgrades

The implicit conversions for nonconstant values are pointer
conversions: the permission downgrades below over an identical
referenced type, and the static-to-dynamic array pointer view decay of
[§25.4.1](#2641-static-to-dynamic-array-pointer-views):

- `ptr<T>` implicitly casts to `mview<T>` and `view<T>`;
- `mview<T>` implicitly casts to `view<T>`;
- passing a `view<T>` where an `mview<T>` or `ptr<T>` is expected is a
  compile error.

```franko
ptr<[byte]> p = getptr(buf);
mview<[byte]> m = p;          // implicit downgrade
view<[byte]> v = p;           // implicit downgrade
view<[byte]> v2 = m;          // implicit downgrade

ptr<[byte]> q = v;            // invalid: raises permissions
```

These conversions apply to assignment, return values, and function
arguments. Every other position requires exact type equality. Raw
integers cannot be converted to pointers.

Explicit downgrades may be written with `cast` (see [Casts](12-casts.md)):

```franko
view<[byte]> v = cast<view<[byte]>>(p);
```

### 26.4.1 Static-to-Dynamic Array Pointer Views

A pointer to a **static** array implicitly converts to a pointer to a
**dynamic** array of the same element type, in the unidirectional form:

- `mview<[T, N]>` and `view<[T, N]>` implicitly (and explicitly)
  cast to `mview<[T]>` and `view<[T]>`;
- the conversion may carry a permission downgrade in the same step, so
  `ptr<[T, N]>` converts to `mview<[T]>`/`view<[T]>` and
  `mview<[T, N]>` converts to `view<[T]>`;
- `T` may itself be a static or dynamic array, but only the **outermost**
  dimension decays: `view<[[int32, 2], 3]>` converts to
  `view<[[int32, 2]]>`, never to `view<[[int32]]>`
  (the element layout viewed through the two types must stay identical);
- the reverse direction is invalid, and ownership never decays:
  `ptr<[T, N]>` cannot convert to `ptr<[T]>`.

```franko
[int32, 4] xs;
mview<[int32, 4]> m = getmview(xs);
view<[int32]> v = m;             // implicit decay
mview<[int32]> d = cast<mview<[int32]>>(m);   // explicit

stdout($(v).length());                   // 4
$(m)[1] = 99;                           // writes hit xs directly
```

The conversion is safe because permissions are propagated to the
result: `.init`, `.init_zero`, `.resize`, and `.uninit` are forbidden on
`mview`/`view` results (and on a decayed `ptr` — which is why `ptr` never
decays), so the storage behind the converted view is never reallocated
or freed through it. The conversion is allowed in the same positions as
permission downgrades: assignment, return values, and function arguments.
Comparing pointers still requires identical pointer types.

Decay does not copy elements or allocate storage, and it creates no
hidden array object: the converted view continues to refer to the same
array. In particular:

- returning a decayed parameter or global view is safe;
- a returned decayed view still refers to the original static storage;
- returning a decayed view of **local** array storage remains an
  ordinary frame-storage escape and is still rejected, exactly like
  returning any other pointer to a local.

Literal views refer to constant storage with static lifetime (see
[Array Literals](22-array-literals.md)), so returning or keeping one is
safe.

## 26.5 Pointer Comparison

Pointers may be compared using only the equality operators:

```franko
==
!=
```

Both operands must have identical pointer types.

Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for pointers.

Pointer comparison produces `uint8`.

## 26.6 No Pointer Arithmetic

Pointers are not integers and do not support arithmetic.

## 26.7 Pointers of Arrays

Arrays may be pointed to:

```franko
[int32] arr;
ptr<[int32]> p;

p = getptr(arr);
$(p)(10);
```

Array literals can be copied through a dereferenced array pointer when
the dereference produces a storage-backed array lvalue:

```franko
[int32, 3] xs;
ptr<[int32, 3]> p;

p = getptr(xs);

$(p).memcpy([1, 2, 3]);
```

This is equivalent to writing the elements one by one:

```franko
$(p)[0] = 1;
$(p)[1] = 2;
$(p)[2] = 3;
```

## 26.8 Permission Propagation (Nested Types)

Permissions apply to the **value** being pointed to. Because arrays and
structs are value types, permissions propagate transitively downward
through them — and they stop at pointer boundaries.

### Rule 1: Value Types Are Transitive

A `view` of a nested array locks every dimension:

```franko
view<[[byte]]> v;
// $(v)[i] reads are fine; $(v)[i][j] = x is blocked, and so is
// $(v)[i].init(...): the entire nested structure is read-only.
```

An `mview` of a nested array may write every level's elements, but the
lifecycle intrinsics are blocked at *every* level — they need allocation
rights (`ptr`), and an index or member access never leaves the region the
pointer dereference created:

```franko
mview<[[byte]]> m;
$(m)[0][1] = 5;     // allowed: element writes at every level
$(m)[0].init(2);    // blocked: init needs allocation rights (ptr)
$(m).init(2);       // blocked: the same rule applies to the outer array
```

To run lifecycle intrinsics on a nested array, reach it through a `ptr`:
either hold the outer pointer (`ptr<[[byte]]>`) or extract an
inner `ptr` (Rule 2).

### Rule 2: Pointer Boundaries (Indirection)

If a container holds pointer values, the container's permission governs
only **the pointer slots** (whether they can be overwritten). The memory
behind those pointers follows each pointer's own type:

```franko
mview<[view<[byte]>]> v;
$(v)[0] = getview(x);        // allowed: overwrite the slot
$($(v)[0])[0] = 5;           // blocked: the extracted view is read-only
```

```franko
view<[mview<[byte]>]> v;
$(v)[0] = getmview(x);       // blocked: the outer view locks its slots
$($(v)[0])[0] = 5;           // allowed: through the inner mview
```

Summary: `view` locks down the memory it directly touches. If that
memory contains raw values (like nested arrays), those values are
locked. If it contains pointers, the pointer *variables* are locked, but
the memory they point to follows its own type rules.

These rules hold at **every** level of nesting: through an `mview`,
`memset`/`memcpy`/`memmove`, the byte/bit mutators, and element writes on an
*inner* nested array are allowed (`$(m)[0].memcpy(src)` is fine), while
`init`/`init_zero`/`resize`/`uninit` on an inner nested array are blocked
exactly like on the outermost one (`$(m)[0].init(...)` is not).

## 26.9 The null Literal

`null` is a literal of type `null`. It is valid only where a pointer of
any permission is expected:

```franko
ptr<int32> p;
p = null;               // valid

int32 x = null;       // invalid: null is only valid for pointer types
p = 0;                  // invalid: 0 is an integer, not null
```

`null` and `0` are different values. A pointer cannot be assigned or compared against the integer `0`; use `null` instead.

`null` may be compared with a pointer using equality only:

```franko
if (p == null) {
    // p is null on this path
}
if (p != null) {
    // p is not null on this path
}
```

Comparing `null` with a non-pointer value is invalid. Relational comparison (`<`, `>`) of `null` is invalid.

`null` may be passed to pointer parameters and returned from pointer
functions.

A bare `stdout(null)` is ambiguous between the text and `std::String` overloads; use a typed null view when a null text argument is intended.

### 26.9.1 Null-Initialized Pointer Storage

Pointer storage is **guaranteed null-initialized**, unlike ordinary storage, which starts indeterminate:

```franko
ptr<int32> p;        // p is null here, guaranteed
stdout(p == null);       // true
```

The guarantee covers every pointer storage slot:

- pointer variables (`ptr<T>`, `mview<T>`, and `view<T>`);
- pointer-typed struct fields;
- pointer tuple elements;
- pointer elements of local static arrays;
- pointer elements of initialized dynamic arrays, after `init`, `init_zero`, or a growing `resize`.

A global static array is zero-initialized, so its pointer elements are null before `main` runs. The same rule applies recursively to pointer storage nested inside structs, tuples, and arrays.

### 26.9.2 Nullness Certification

The compiler tracks whether a pointer variable is null. The only
expressions that certify a pointer as non-null are the creation
intrinsics `getptr(...)`, `getmview(...)`, and `getview(...)`:

```franko
ptr<int32> p;            // p is null
$(p);                   // error: cannot dereference null

p = getptr(x);             // p is certified non-null
$(p);                   // valid

ptr<int32> q = p;        // q copies p's state: non-null
ptr<int32> r = q;        // r copies q's state: non-null
```

Assignment from another pointer variable copies that variable's exact state, so a null state propagates through copies and a certification propagates transitively.

Assignment from values of unknown provenance (call results, parameters, struct-field or array-element reads) yields a maybe-null state: the compiler treats such a variable as possibly null, and dereferencing it is a warning, not an error.

The check also follows pointers across function calls. If a callee always
dereferences a parameter, passing a definitely-null argument is an error and
passing a possibly-null argument is a warning. A parameter guarded with
`p != null` on the dereferencing path does not require a non-null argument.
A callee's return carries the null state of the value it returns, and a callee
that writes through a `ptr<ptr<T>>` parameter can update the caller's pointer
variable. Each function still reports its own local maybe-null warnings.
Dangling pointers are checked separately (see
[Pointer Lifetime Limitations](#2611-pointer-lifetime-limitations) and
[Memory Safety Checking](32-memory-safety-checking.md)).

### 26.9.3 Dereferencing and Warnings

Dereferencing a variable that is **definitely null** is an error.

Dereferencing a variable that is **possibly null** (null on some path, for example after an `if`/`else`) is a **warning**, not an error:

```franko
ptr<int32> p;
if (cond != 0) {
    p = getptr(x);
} else {
    p = null;
}
$(p);                   // warning: possibly null
```

Guarding with `p != null` removes the warning, because the compiler refines the variable's state inside the guard. Compound guards work the same way: `if (p == null || q == null) { return }` proves both pointers non-null on the continuation path, and `if (p != null && q != null)` proves both non-null inside the branch. A dereference that is only possibly null on some alternatives is a warning; a definite null dereference on every reaching alternative is an error.

## 26.10 Capability Linter

The capability linter warns when a `ptr` or `mview` local or parameter never
uses the permissions it requests. The full warning rules are in
[Capability Linting](32-memory-safety-checking.md#328-capability-linting).

## 26.11 Pointer Lifetime Limitations

Dangling-pointer checking, escape rules, and their remaining limitations are
documented in [Dangling-Pointer Checking](32-memory-safety-checking.md#329-dangling-pointer-checking).

## 26.12 Prefer Pool Indices to Linked Nodes

Pool allocation and index-based links are covered in
[Pool Allocation](33-pool-allocation.md#331-prefer-pool-indices-to-linked-nodes).

## 26.13 Owning Pointers in Aggregates Are Forbidden

An owning pointer `ptr<T>` cannot be stored inside an array, struct, or
tuple, recursively. The compiler rejects the declaration at the legality
checking stage:

```franko
[ptr<int32>] a;              // invalid: array element is an owning pointer
[ptr<int32>, 8] b;           // invalid: same for a static array
struct Node { ptr<Node> next } // invalid: owning pointer field
(ptr<int32>, int32) pair;    // invalid: owning pointer tuple element
[view<ptr<int32>>] views;    // invalid: the view's referent is a ptr
ptr<[ptr<int32>]> nested;    // invalid: pointer to an array of owning pointers
```

These are allowed:

```franko
view<int32> v;               // views may be aggregate elements
mview<int32> m;
[view<int32>] views;
struct Views { view<int32> a; mview<int32> b }
ptr<ptr<int32>> pp;          // pointer to a standalone pointer slot
view<ptr<int32>> vp;         // standalone view of a pointer slot
```

The distinction is ownership. An owning `ptr<T>` carries the right to
allocate and free the storage it refers to. If it is embedded in an
aggregate, that responsibility travels with the aggregate into unrelated
code. The compiler can no longer tell where the storage is released, and a
same-looking value may be an owner in one place and an alias in another.
Keeping owning pointers out of aggregates keeps allocation and free
responsibility local and makes lifetime reasoning tractable.

Use these alternatives:

- `view<T>` or `mview<T>` inside arrays, structs, and tuples when only
  access is needed;
- direct `ptr<T>` variables, parameters, and globals when ownership is
  needed;
- the pool allocator from `franko/pool` for linked or node-like structures.
  Store slot indices, not owning pointers.

For nested arrays, use nested dynamic arrays instead of arrays of owning
pointers:

```franko
[[int32]] rows;        // good: the outer array owns the inner arrays
[ptr<[int32]>] rows;   // rejected: ownership escapes through the array
```

A nested dynamic array owns its inner arrays. `init`, `resize`, and `uninit`
manage the inner storage and lifetime as part of the outer array, so there is
no separate allocation capability to leak or dangle.
