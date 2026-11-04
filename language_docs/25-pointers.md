# 25. Pointers

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

## 25.1 Creating Pointers

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
lvalue. The compiler-generated borrow used to pass an rvalue operand to
an overloaded struct operator is the only exception: it is internal to
operator lowering, and `getview(5)` or `getview(make_x())` remains
invalid in user code. See [Struct Operator Overloading](13-operators.md#1310-struct-operator-overloading).

## 25.2 Dereferencing with `$`

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

## 25.3 Nested Pointers

Franko supports pointers to pointers, of any permission:

```franko
view<ptr<int32>> v;   // read-only view of a ptr<int32> slot
```

## 25.4 Assignment and Implicit Downgrades

The implicit conversions for nonconstant values are pointer
conversions: the permission downgrades below over an identical
referenced type, and the static-to-dynamic array pointer view decay of
[§25.4.1](#2541-static-to-dynamic-array-pointer-views):

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

### 25.4.1 Static-to-Dynamic Array Pointer Views

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

print($(v).length());                   // 4
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
[Array Literals](21-array-literals.md)), so returning or keeping one is
safe.

## 25.5 Pointer Comparison

Pointers may be compared using only the equality operators:

```franko
==
!=
```

Both operands must have identical pointer types.

Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for pointers.

Pointer comparison produces `uint8`.

## 25.6 No Pointer Arithmetic

Pointers are not integers and do not support arithmetic.

## 25.7 Pointers of Arrays

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

## 25.8 Permission Propagation (Nested Types)

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
view<[ptr<[byte]>]> v;
$(v)[0] = getptr(x);         // blocked: the outer view locks its slots
$($(v)[0])[0] = 5;           // allowed: through the inner ptr
$($(v)[0]).init(10);         // allowed: full allocation rights on the pointee
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

## 25.9 The null Literal

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

`print(null)` matches the `print(view<[uint8]>)` overload and prints nothing (a null view is treated as empty).

### 25.9.1 Null-Initialized Pointer Declarations

Pointer declarations are **guaranteed null-initialized**, unlike ordinary declarations, which start uninitialized:

```franko
ptr<int32> p;        // p is null here, guaranteed
print(p == null);       // true
```

This guarantee also applies to pointer-typed struct fields and to
`mview`/`view` variables. Pointer elements inside a local static array
follow the ordinary static-array rule and are indeterminate until
assigned; a global static array is zero-initialized, so its pointer
elements are null before `main` runs.

### 25.9.2 Nullness Certification

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

The analysis is **interprocedural through summaries**. A callee's
parameter dereferences become requirements on the caller's arguments, so
passing a definitely-null argument to a parameter that is always
dereferenced is an error and passing a possibly-null argument is a warning.
A parameter guarded with `p != null` on the dereferencing path produces no
requirement. Callee returns propagate their null-state alternatives, and a
callee that writes through a `ptr<ptr<T>>` parameter updates the caller's
pointer variable. Each function body still has its own intraprocedural
warnings. Dangling pointers are checked separately (see
[Pointer Lifetime Limitations](#2511-pointer-lifetime-limitations) and
[Memory Safety Checking](29-memory-safety-checking.md)).

### 25.9.3 Dereferencing and Warnings

Dereferencing a variable that is **definitely null** is an error.

Dereferencing a variable that is **possibly null** (null on some path, for example after a branch merge) is a **warning**, not an error:

```franko
ptr<int32> p;
if (cond != 0) {
    p = getptr(x);
} else {
    p = null;
}
$(p);                   // warning: possibly null
```

Guarding with `p != null` removes the warning, because the compiler refines the variable's state inside the guard.

## 25.10 Capability Linter

To encourage safe, data-oriented design, the compiler tracks the
highest permission each `ptr`/`mview` local variable and parameter
actually exercises and produces a non-fatal **warning** recommending a
downgrade when elevated permissions go unused (global variables are not
linted: their uses span functions, so no per-use summary would be
meaningful):

- a `ptr<T>` that never exercises allocation or lifecycle rights
  (no `init`/`init_zero`/`resize`/`uninit`, no use where a `ptr<T>` is
  expected):

  > *Warning: 'ptr<T> p' never exercises allocation or lifecycle permissions. Consider downgrading to 'mview<T>' for tighter security.*

- a `ptr<T>` or `mview<T>` that is only ever read from:

  > *Warning: 'mview<T> v' is only ever read from. Consider downgrading to 'view<T>' for maximum safety.*

Exercising *allocation* means lifecycle intrinsics, `getptr` over the
variable's region, or using it where a `ptr<T>` is expected (argument,
return, assignment into a `ptr` slot, method `self`). *Mutation* means
assignment through its region, `memset`/`memcpy`/`memmove`, the byte/bit
mutators (`write_*`, `write_bit`, `flip_bit`, `bitwise_*_mask`,
`shift_bits_*`), or use where an `mview<T>` is expected. *Read* covers
everything else, including `length()`, `memcmp`, `memchr`, `memmem`, the
`read_*` accessors, `read_bit`, and `count_set_bits`.

Because these are warnings, they do not fail the build. They exist to
passively teach developers to default to immutable, non-owning pointers
(`view` and `mview`) whenever possible, minimizing the risk of accidental
memory corruption.

## 25.11 Pointer Lifetime Limitations

Pointer lifetime is not fully tracked, but the compiler detects dangling
pointers returned from a function, stored into global or caller-owned
storage, forwarded through callees that perform such a store, and used after
an inner block or loop body has ended.

The compiler rejects a `return` of a pointer to a local variable, a
parameter, or any field/element of one, including through pointer copies,
`$(...)` aliases, and branch merges. It also rejects a dereference of a
pointer whose pointee is a local variable from a block that has already
ended, and it follows capture/return summaries through called functions.

A return that only *may* refer to local storage after a branch merge is a
warning; a definite escape is an error. Safe returns include pointer
parameters, pointers obtained from global storage or from
`getptr($(pointerParameter))`, literal-backed views, existing global
views, and decayed views of parameters or global storage. A decayed view
of local array storage is rejected as an ordinary frame-storage escape:
the view refers to the local array itself.

What remains untracked:

- pointers hidden inside returned structs or tuples;
- field-sensitive struct tracking;
- global dynamic-array lifecycle;
- pointers escaping through aggregate stores that are not modeled as a
  direct global/out-parameter sink;
- recursion and the 2048-node call-graph budget (silent conservative
  result).

Dynamic-array buffer lifetime across `resize`/`uninit` is checked separately
by the dynamic-array lifecycle checker (see
[Dynamic-Array Lifecycle Checking](29-memory-safety-checking.md#2910-dynamic-array-lifecycle-checking)).

## 25.12 Prefer Pool Indices to Linked Nodes

For many programs, pool allocation is a safer and faster alternative to
linked nodes built from pointers. Instead of storing a `ptr<Node> next`
link, give each live object an index from a pool allocator, store object
data in parallel payload arrays, and store links as `uint32` indices.
The pool recycles indices deterministically, and its generational
variants detect stale handles. The full API is in
[franko/pool](../library_docs/pool.md).

```franko
import "franko/io"
import "franko/pool"

func main() -> int32 {
    [int32] payload;
    [uint32] next;
    payload.init(8);
    next.init(8);

    std::SparseSetAllocator nodes;
    nodes.init(8);

    (uint32 idx, int32 err) first = nodes.new_index();
    (uint32 idx, int32 err) second = nodes.new_index();
    if (first.err != 0 || second.err != 0) {
        return 1;
    }
    uint32 a = first.idx;
    uint32 b = second.idx;

    payload[a] = 10;
    payload[b] = 20;
    next[a] = b;                // replaces a node's `next` pointer
    next[b] = 0xFFFFFFFF;       // end of the list

    uint32 i = a;
    while (i != 0xFFFFFFFF) {
        print(payload[i]);
        i = next[i];
    }

    nodes.free_index(a);
    nodes.free_index(b);
    nodes.uninit();
    next.uninit();
    payload.uninit();
    return 0;
}
```

A node handle is now the pair of its pool index and (for a generational
pool) its generation; releasing a slot invalidates stale handles instead
of leaving a dangling pointer.
