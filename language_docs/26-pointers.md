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
view<int32> v;
ptr<[int32]> ap;
```

Pointers are values. They can be assigned and passed down to callees, but
they cannot be stored in aggregates, returned, or written back through
writable pointer-to-pointer parameters. Pointer stacks are allowed, and only read-only
pointer globals are allowed.

Pointers are typed, assignable, copyable, dereferenceable, and comparable.

Pointers are not arithmetic values.

`ptr<void>`, `mview<void>`, and `view<void>` are invalid because `void`
is not an ordinary value type.

## 26.1 Creating Pointers

Pointer values are created with intrinsics over storage-backed lvalues:

- `ptr(expr)` creates a `ptr<T>`.
- `mview(expr)` creates an `mview<T>`.
- `view(expr)` creates a `view<T>`.

If the operand has type `T`, the result has the corresponding pointer
type. Creating a pointer demands that the operand's storage carries at
least the requested permission: `ptr` needs owner access,
`mview` needs write access, `view` always works. This is what
prevents laundering a `view` into a `ptr`.

User-written `view`/`mview`/`ptr` require a storage-backed
lvalue. The only exception is the implicit borrow used to pass an rvalue
operand to an overloaded struct operator; it is not available to user code,
so `view(5)` or `view(make_x())` remains invalid. See [Struct Operator Overloading](13-operators.md#1310-struct-operator-overloading).

Const storage allows only `view`:

```franko
const int32 x = 1
view<int32> v = view(x)    // valid
ptr<int32> p = ptr(x)      // error
mview<int32> m = mview(x)  // error
```

This limits a const struct receiver to `view<X> self` methods.
`$(p)` follows the pointee's own type, not the constness of the variable
holding `p`: when `p` is a const `ptr<int32>`, `$(p) = 5` is valid and
`p = ...` is not. See [Declarations](18-declarations.md#188-const-storage).

Every global variable is implicitly const, so `ptr(global)` and
`mview(global)` are rejected; use `view(global)` for read-only
access. Taking a mutable pointer to a field or element of a global is
rejected for the same reason.

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

## 26.3 Stacked Pointers

A pointer referent may itself be a pointer, to unlimited depth:

```franko
ptr<ptr<int32>> a
view<mview<ptr<int32>>> b
```

Stacks are ordinary pointer values. A local `ptr<ptr<T>>` may be
downgraded to `view<ptr<T>>` at a call. This preserves stacked-pointer
passing without letting a callee write a pointer into caller storage.

A pointer may refer to a struct, array, or tuple only when that aggregate
contains no pointers.

## 26.4 Pointers Cannot Appear in Aggregates

`ptr<T>`, `mview<T>`, and `view<T>` are forbidden in all aggregate value
storage, recursively:

- struct fields;
- static array elements;
- dynamic array elements;
- tuple elements;
- nested structs, tuples, and arrays inside those positions;
- aggregates that are the referent of a pointer.

There is no owned-versus-view distinction for aggregate membership: all
three pointer permissions are rejected.

```franko
struct Bad {
    view<int32> link    // invalid: pointer member
}

[ptr<int32>] a          // invalid: array element is a pointer
[int32, 4] b            // valid: no pointers
(view<int32>, int32) p  // invalid: tuple element is a pointer
ptr<[ptr<int32>]> q     // invalid: referent aggregate contains a pointer
ptr<ptr<int32>> ok      // valid: pointer stack
view<ptr<int32>> vok    // valid: read-only pointer to a pointer
```

Use a dynamic array with a separate length, or a pool index from
`franko/pool` for linked data, when a node-like shape is needed. See
[Pool Allocation](33-pool-allocation.md).

## 26.5 Assignment and Implicit Downgrades

The implicit conversions for nonconstant values are pointer
conversions: the permission downgrades below over an identical
referenced type, and the static-to-dynamic array pointer view decay of
[§26.5.1](#2651-static-to-dynamic-array-pointer-views):

- `ptr<T>` implicitly casts to `mview<T>` and `view<T>`;
- `mview<T>` implicitly casts to `view<T>`;
- passing a `view<T>` where an `mview<T>` or `ptr<T>` is expected is a
  compile error.

```franko
ptr<[byte]> p = ptr(buf);
mview<[byte]> m = p;          // implicit downgrade
view<[byte]> v = p;           // implicit downgrade
view<[byte]> v2 = m;          // implicit downgrade

ptr<[byte]> q = v;            // invalid: raises permissions
```

These conversions apply to assignment and function arguments. Every other
position requires exact type equality. Raw integers cannot be converted
to pointers. Pointer values never flow back up through returns or
aggregate stores.

Explicit downgrades may be written with `cast` (see [Casts](12-casts.md)):

```franko
view<[byte]> v = cast<view<[byte]>>(p);
```

### 26.5.1 Static-to-Dynamic Array Pointer Views

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
mview<[int32, 4]> m = mview(xs);
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
permission downgrades: assignment and function arguments.
Comparing pointers still requires identical pointer types.

Decay does not copy elements or allocate storage, and it creates no
hidden array object: the converted view continues to refer to the same
array.

## 26.6 Pointer Comparison

Pointers may be compared using only the equality operators:

```franko
==
!=
```

Both operands must have identical pointer types.

Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for pointers.

Pointer comparison produces `uint8`.

## 26.7 No Pointer Arithmetic

Pointers are not integers and do not support arithmetic.

## 26.8 Pointers of Arrays

Arrays may be pointed to:

```franko
[int32] arr;
ptr<[int32]> p;

p = ptr(arr);
```

Array literals can be copied through a dereferenced array pointer when
the dereference produces a storage-backed array lvalue:

```franko
[int32, 3] xs;
ptr<[int32, 3]> p;

p = ptr(xs);

$(p).memcpy([1, 2, 3]);
```

This is equivalent to writing the elements one by one:

```franko
$(p)[0] = 1;
$(p)[1] = 2;
$(p)[2] = 3;
```

Arrays cannot hold pointers, so a pointer to an array always points at
non-pointer element storage.

## 26.9 Pointer Globals

Only read-only pointer globals are allowed:

- `view<T>` globals are allowed.
- `ptr<T>` globals are rejected.
- `mview<T>` globals are rejected.

```franko
view<[byte, 5]> greeting = "hello"   // valid: read-only global view
view<[int32, 3]> numbers = [1, 2, 3] // valid

ptr<int32> p                        // invalid as a global
mview<[byte]> m                     // invalid as a global
```

A `view` global provides read-only access without exposing mutable
storage. String and array literal globals are the standard examples.

## 26.10 Pointer Parameters and Returns

Pointers may be parameters. Pointer parameters keep compile-time
defaults.

Functions and methods cannot return any pointer type.

```franko
func read(view<[byte]> buf) -> int32 {
    return $(buf)[0]
}

func bad() -> view<[byte]> {   // invalid: pointer return
    return greeting
}
```

Writable pointer-to-pointer parameters are forbidden:

- `ptr<ptr<T>>`, `ptr<view<T>>`, `ptr<mview<T>>`;
- `mview<ptr<T>>`, `mview<view<T>>`, `mview<mview<T>>`;
- any equivalent nested form.

Read-only pointer-to-pointer parameters are allowed:

- `view<ptr<T>>`, `view<view<T>>`, `view<mview<T>>`.

```franko
func take(view<ptr<int32>> p) -> void {  // valid
    return
}

func write(ptr<ptr<int32>> p) -> void {  // invalid
    return
}
```

Pointer values may flow into a callee and downward, but cannot flow back
up through returns, aggregates, mutable globals, or writable pointer
out-parameters. To produce aggregate or buffer output, write through an
out-parameter that points at non-pointer storage.

## 26.11 Permission Propagation (Nested Types)

Permissions apply to the **value** being pointed to. Because arrays and
structs are value types, permissions propagate transitively downward
through them — and they stop at pointer boundaries.

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
inner `ptr`.

Pointer stacks follow each pointer's own permission. A `view<ptr<T>>`
locks the outer pointer slot as read-only while the inner pointer keeps
its own rights.

## 26.12 The null Literal

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

`null` may be passed to pointer parameters. Functions cannot return
pointers, so `null` is never a pointer return value.

A bare `stdout(null)` is ambiguous between the text and `std::String` overloads; use a typed null view when a null text argument is intended.

### 26.12.1 Null-Initialized Pointer Storage

Pointer storage is **guaranteed null-initialized**, unlike ordinary storage, which starts indeterminate:

```franko
ptr<int32> p;        // p is null here, guaranteed
stdout(p == null);       // true
```

The guarantee covers every pointer storage slot:

- pointer variables (`ptr<T>`, `mview<T>`, and `view<T>`);
- pointer elements of local static arrays;
- pointer elements of initialized dynamic arrays, after `init`, `init_zero`, or a growing `resize`.

Global storage is statically initialized before `main`. Global arrays do
not exist, so no global array element storage is allocated.

### 26.12.2 Nullness Certification

The compiler tracks whether a pointer variable is null. The only
expressions that certify a pointer as non-null are the creation
intrinsics `ptr(...)`, `mview(...)`, and `view(...)`:

```franko
ptr<int32> p;            // p is null
$(p);                   // error: cannot dereference null

p = ptr(x);             // p is certified non-null
$(p);                   // valid

ptr<int32> q = p;        // q copies p's state: non-null
ptr<int32> r = q;        // r copies q's state: non-null
```

Assignment from another pointer variable copies that variable's exact state, so a null state propagates through copies and a certification propagates transitively.

Assignment from values of unknown provenance (call results, parameters) yields a maybe-null state: the compiler treats such a variable as possibly null, and dereferencing it is a warning, not an error.

The check also follows pointers across function calls. If a callee always
dereferences a parameter, passing a definitely-null argument is an error and
passing a possibly-null argument is a warning. A parameter guarded with
`p != null` on the dereferencing path does not require a non-null argument.
Each function still reports its own local maybe-null warnings.
Dangling pointers are checked separately (see
[Pointer Lifetime Limitations](#2614-pointer-lifetime-limitations) and
[Memory Safety Checking](32-memory-safety-checking.md)).

### 26.12.3 Dereferencing and Warnings

Dereferencing a variable that is **definitely null** is an error.

Dereferencing a variable that is **possibly null** (null on some path, for example after an `if`/`else`) is a **warning**, not an error:

```franko
ptr<int32> p;
if (cond != 0) {
    p = ptr(x);
} else {
    p = null;
}
$(p);                   // warning: possibly null
```

Guarding with `p != null` removes the warning, because the compiler refines the variable's state inside the guard. Compound guards work the same way: `if (p == null || q == null) { return }` proves both pointers non-null on the continuation path, and `if (p != null && q != null)` proves both non-null inside the branch. A dereference that is only possibly null on some alternatives is a warning; a definite null dereference on every reaching alternative is an error.

## 26.13 Capability Linter

The capability linter warns when a `ptr` or `mview` local or parameter never
uses the permissions it requests. The full warning rules are in
[Capability Linting](32-memory-safety-checking.md#328-capability-linting).

## 26.14 Pointer Lifetime Limitations

Dangling-pointer checking covers use of a pointer after its inner block or
loop body has ended, and passing a dangling pointer as a call argument.
Escape rules and their remaining limitations are documented in
[Dangling-Pointer Checking](32-memory-safety-checking.md#329-dangling-pointer-checking).

## 26.15 Prefer Pool Indices to Linked Nodes

Pool allocation and index-based links are covered in
[Pool Allocation](33-pool-allocation.md#331-prefer-pool-indices-to-linked-nodes).

Because all pointers, not just owning pointers, are banned from
aggregates, linked structures must use pool indices or dynamic arrays
rather than embedded pointers.
