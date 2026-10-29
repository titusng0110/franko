# 24. Pointers

Franko has a capability-based pointer system with three pointer types,
representing strict levels of permission:

| Type | Meaning | C++ Emission | Allowed Operations |
|---|---|---|---|
| `ptr<T>` | Owner | `T*` | Read, Write, Allocate, Free |
| `mview<T>` | Writer | `T*` | Read, Write |
| `view<T>` | Reader | `const T*` | Read Only |

(`ptr` = pointer, `mview` = mutable view, `view` = view.) All three are
thin pointers; the permissions are enforced entirely at compile time.

A pointer value refers to a storage location containing a value of type `T`.

Examples:

```franko
ptr<int32_t> p;
mview<uint8_t> b;
view<ptr<int32_t>> pp;
ptr<array<int32_t>> ap;
```

Pointers are typed, assignable, copyable, dereferenceable, and comparable.

Pointers are not arithmetic values.

`ptr<void>`, `mview<void>`, and `view<void>` are invalid because `void`
is not an ordinary value type.

## 24.1 Creating Pointers

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

## 24.2 Dereferencing with `$`

If the operand has type `ptr<T>`, `mview<T>`, or `view<T>`, then
`$(...)` has type `T`.

`$(...)` is also an lvalue when the pointer carries write permission:

```franko
$(p)[i] = x;    // array element access (mutating: needs ptr or mview)
$(v)[i];        // reading an element (any permission)
$(v).field;     // struct field access
$(v).length();  // intrinsic over the pointee
```

## 24.3 Nested Pointers

Franko supports pointers to pointers, of any permission:

```franko
view<ptr<int32_t>> v;   // read-only view of a ptr<int32_t> slot
```

## 24.4 Assignment and Implicit Downgrades

The implicit conversions in Franko are pointer conversions: the
permission downgrades below over an identical referenced type, and the
static-to-dynamic array pointer view decay of
[§24.4.1](#2441-static-to-dynamic-array-pointer-views):

- `ptr<T>` implicitly casts to `mview<T>` and `view<T>`;
- `mview<T>` implicitly casts to `view<T>`;
- passing a `view<T>` where an `mview<T>` or `ptr<T>` is expected is a
  compile error.

```franko
ptr<array<byte>> p = getptr(buf);
mview<array<byte>> m = p;          // implicit downgrade
view<array<byte>> v = p;           // implicit downgrade
view<array<byte>> v2 = m;          // implicit downgrade

ptr<array<byte>> q = v;            // invalid: raises permissions
```

These conversions apply to assignment, return values, and function
arguments. Every other position requires exact type equality. Raw
integers cannot be converted to pointers.

Explicit downgrades may be written with `cast` (see [Casts](16-casts.md)):

```franko
view<array<byte>> v = cast<view<array<byte>>>(p);
```

### 24.4.1 Static-to-Dynamic Array Pointer Views

A pointer to a **static** array implicitly converts to a pointer to a
**dynamic** array of the same element type, in the unidirectional form:

- `mview<array<T, N>>` and `view<array<T, N>>` implicitly (and explicitly)
  cast to `mview<array<T>>` and `view<array<T>>`;
- the conversion may carry a permission downgrade in the same step, so
  `ptr<array<T, N>>` converts to `mview<array<T>>`/`view<array<T>>` and
  `mview<array<T, N>>` converts to `view<array<T>>`;
- `T` may itself be a static or dynamic array, but only the **outermost**
  dimension decays: `view<array<array<int32_t, 2>, 3>>` converts to
  `view<array<array<int32_t, 2>>>`, never to `view<array<array<int32_t>>>`
  (element layouts must stay identical behind the one thin pointer);
- the reverse direction is invalid, and ownership never decays:
  `ptr<array<T, N>>` cannot convert to `ptr<array<T>>`.

```franko
array<int32_t, 4> xs;
mview<array<int32_t, 4>> m = getmview(xs);
view<array<int32_t>> v = m;             // implicit decay
mview<array<int32_t>> d = cast<mview<array<int32_t>>>(m);   // explicit

print($(v).length());                   // 4
$(m)[1] = 99;                           // writes hit xs directly
```

The conversion is safe because permissions are propagated to the
result: `.init`, `.init_zero`, `.resize`, and `.uninit` are forbidden on
`mview`/`view` results (and on a decayed `ptr` — which is why `ptr` never
decays), so the storage behind the converted view is never reallocated
or freed through it. The conversion is allowed in the same positions as
permission downgrades: assignment, return values, and function arguments.
Comparing pointers still requires identical pointer types. Like every
pointer in Franko, a converted view must not outlive the scope it was
created in — a view that escapes past it (returned, or stored into
longer-lived storage) is the usual untracked dangling-pointer case of
[Pointer Lifetime Limitations](#2411-pointer-lifetime-limitations),
exactly as if it had been created there with `getview`.

## 24.5 Pointer Comparison

Pointers may be compared using only the equality operators:

```franko
==
!=
```

Both operands must have identical pointer types.

Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for pointers.

Pointer comparison produces `uint8_t`.

## 24.6 No Pointer Arithmetic

Pointers are not integers and do not support arithmetic.

## 24.7 Pointers of Arrays

Arrays may be pointed to:

```franko
array<int32_t> arr;
ptr<array<int32_t>> p;

p = getptr(arr);
$(p)(10);
```

Array initializer assignment through a dereferenced array pointer is valid when the dereference produces a storage-backed array lvalue:

```franko
array<int32_t, 3> xs;
ptr<array<int32_t, 3>> p;

p = getptr(xs);

$(p) = [1, 2, 3];
```

Equivalent to:

```franko
$(p)[0] = 1;
$(p)[1] = 2;
$(p)[2] = 3;
```

## 24.8 Permission Propagation (Nested Types)

Permissions apply to the **value** being pointed to. Because arrays and
structs are value types, permissions propagate transitively downward
through them — and they stop at pointer boundaries.

### Rule 1: Value Types Are Transitive

A `view` of a nested array locks every dimension:

```franko
view<array<array<byte>>> v;
// $(v)[i] reads are fine; $(v)[i][j] = x is blocked, and so is
// $(v)[i].init(...): the entire nested structure is read-only.
```

An `mview` of a nested array may write every level's elements, but the
lifecycle intrinsics are blocked at *every* level — they need allocation
rights (`ptr`), and an index or member access never leaves the region the
pointer dereference created:

```franko
mview<array<array<byte>>> m;
$(m)[0][1] = 5;     // allowed: element writes at every level
$(m)[0].init(2);    // blocked: init needs allocation rights (ptr)
$(m).init(2);       // blocked: the same rule applies to the outer array
```

To run lifecycle intrinsics on a nested array, reach it through a `ptr`:
either hold the outer pointer (`ptr<array<array<byte>>>`) or extract an
inner `ptr` (Rule 2).

### Rule 2: Pointer Boundaries (Indirection)

If a container holds pointer values, the container's permission governs
only **the pointer slots** (whether they can be overwritten). The memory
behind those pointers follows each pointer's own type:

```franko
mview<array<view<array<byte>>>> v;
$(v)[0] = getview(x);        // allowed: overwrite the slot
$($(v)[0])[0] = 5;           // blocked: the extracted view is read-only
```

```franko
view<array<ptr<array<byte>>>> v;
$(v)[0] = getptr(x);         // blocked: the outer view locks its slots
$($(v)[0])[0] = 5;           // allowed: through the inner ptr
$($(v)[0]).init(10);         // allowed: full allocation rights on the pointee
```

Summary: `view` locks down the memory it directly touches. If that
memory contains raw values (like nested arrays), those values are
locked. If it contains pointers, the pointer *variables* are locked, but
the memory they point to follows its own type rules.

These rules hold at **every** level of nesting: through an `mview`,
`memset`/`memcpy`/`memmove` and element writes on an *inner* nested
array are allowed (`$(m)[0].memcpy(src)` is fine), while `init`/
`init_zero`/`resize`/`uninit` on an inner nested array are blocked
exactly like on the outermost one (`$(m)[0].init(...)` is not).

## 24.9 The null Literal

`null` is a literal of type `null`. It is valid only where a pointer of
any permission is expected:

```franko
ptr<int32_t> p;
p = null;               // valid

int32_t x = null;       // invalid: null is only valid for pointer types
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

`print(null)` prints the null pointer as a pointer value.

### 24.9.1 Null-Initialized Pointer Declarations

Pointer declarations are **guaranteed null-initialized**, unlike ordinary declarations, which start uninitialized:

```franko
ptr<int32_t> p;        // p is null here, guaranteed
print(p == null);       // true
```

This guarantee also applies to pointer-typed struct fields and to
`mview`/`view` variables.

### 24.9.2 Nullness Certification

The compiler tracks whether a pointer variable is null. The only
expressions that certify a pointer as non-null are the creation
intrinsics `getptr(...)`, `getmview(...)`, and `getview(...)`:

```franko
ptr<int32_t> p;            // p is null
$(p);                   // error: cannot dereference null

p = getptr(x);             // p is certified non-null
$(p);                   // valid

ptr<int32_t> q = p;        // q copies p's state: non-null
ptr<int32_t> r = q;        // r copies q's state: non-null
```

Assignment from another pointer variable copies that variable's exact state, so a null state propagates through copies and a certification propagates transitively.

Assignment from values of unknown provenance (call results, parameters, struct-field or array-element reads) yields a maybe-null state: the compiler treats such a variable as possibly null, and dereferencing it is a warning, not an error.

The analysis is **intra-procedural**: no nullness facts cross function
boundaries (a returned pointer is always maybe-null at the call site),
and dangling pointers are not checked.

### 24.9.3 Dereferencing and Warnings

Dereferencing a variable that is **definitely null** is an error.

Dereferencing a variable that is **possibly null** (null on some path, for example after a branch merge) is a **warning**, not an error:

```franko
ptr<int32_t> p;
if (cond != 0) {
    p = getptr(x);
} else {
    p = null;
}
$(p);                   // warning: possibly null
```

Guarding with `p != null` removes the warning, because the compiler refines the variable's state inside the guard.

## 24.10 Capability Linter

To encourage safe, data-oriented design, the compiler tracks the
highest permission each `ptr`/`mview` local variable and parameter
actually exercises and emits a non-fatal **warning** recommending a
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
assignment through its region, `memset`/`memcpy`/`memmove`, or use where
an `mview<T>` is expected. *Read* covers everything else, including
`length()`.

Because these are warnings, they do not fail the build. They exist to
passively teach developers to default to immutable, non-owning pointers
(`view` and `mview`) whenever possible, minimizing the risk of accidental
memory corruption.

## 24.11 Pointer Lifetime Limitations

Pointer lifetime is not fully tracked.

The compiler does not fully track dangling pointers or aliasing through pointers. A pointer may outlive its pointee, and the compiler does not detect that case at compile time; a runtime check for this is planned as a later change.
