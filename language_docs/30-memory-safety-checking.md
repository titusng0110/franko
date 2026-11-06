# 30. Memory Safety Checking

Franko checks two independent properties of pointers: **null state**
and **dangling storage**. Both checks cover all three pointer types
(`ptr`, `mview`, and `view`). A third check tracks the initialization
lifecycle and buffer reuse of function-local dynamic arrays (see
[Dynamic-Array Lifecycle Checking](#2910-dynamic-array-lifecycle-checking)).

The checks also follow values across function calls. Passing a null pointer to
a function that uses it, forwarding a pointer to local storage into global or
caller-owned storage through a callee, or changing a caller's dynamic array
through a callee are diagnosed at the relevant call site. Messages are prefixed
with `Intraprocedural:` when the problem is inside the function being checked
and with `Interprocedural:` when it is discovered at a call.

The null-state check covers:

- dereferencing a null pointer (an error);
- dereferencing a possibly-null pointer (a warning);
- that `null` is only used for pointer types (see [Types](09-types.md)).

The null-state check also covers values crossing function boundaries: a
callee's parameter use becomes a requirement on the caller's argument, a
callee's return carries the null state of the value it returns, and a
callee that writes through a `ptr<ptr<T>>` parameter can update the caller's
pointer variable. A function still reports its own local maybe-null warnings
as well.

## 30.1 Pointer States

For each pointer variable the compiler tracks one of:

| State | Meaning |
|---|---|
| pointing | the variable holds a certified, real storage pointer |
| null | the variable is definitely null on the current path |
| maybe-null | the variable is null on some continuing paths |

## 30.2 Assignment Rules

- an ordinary pointer declaration is **null-initialized**, so it starts in the null state;
- assigning the `null` literal makes the target null;
- assigning `getptr(...)`, `getmview(...)`, or `getview(...)` makes the target pointing; these are the **only** certifications of a non-null pointer;
- assigning another pointer variable copies that variable's exact state, so a null state propagates through copies and certification propagates transitively;
- assigning a value of unknown provenance (a call result, a parameter, a struct-field or array-element read) yields a maybe-null state;
- at function entry, a pointer parameter or global starts as maybe-null because the compiler cannot see the value it receives. Exceptions: a method's `self` parameter starts pointing, because the receiver is a storage-backed lvalue; and the args-form `main` parameter starts pointing, because it always receives a live view.

## 30.3 Dereference Checking

- `$(p)` where `p` is **null** is an **error**;
- `$(p)` where `p` is **possibly null** is a **warning**.

A variable warns at most once per check, so revisiting the same dereference on
another path does not repeat the warning.

## 30.4 Branch Handling

`if` / `else` is checked branch by branch. Only branches that continue past the
statement affect later checks. A branch that **definitely returns** does not
affect the code following the `if`, because that path never continues there.
A branch whose condition contradicts a known state cannot execute and produces
no diagnostics.

Very deeply nested conditions may be handled more conservatively, so some
warnings can be missed.

## 30.5 Loop Handling

`while` loops are checked without proving termination:

- the body is checked once;
- the state after the loop accounts for zero iterations, normal exits, and
  `break`;
- `break` and `continue` paths are handled separately;
- if neither a normal exit nor a `break` is possible, the loop is treated as
  non-falling-through, so its tail is not checked.

Loop termination is not proven; the post-loop state is conservative.

`for` loops receive the same treatment as the equivalent block and `while` loop, so they are handled the same way. See [Desugaring](24-desugaring.md).

## 30.6 Guard Refinement

Inside `if (p == null)` / `if (p != null)` (either operand order), the compiler refines `p`'s state per path: the then-branch of `p != null` sees `p` as pointing, and the code after the loop/branch of a failed guard sees `p` as null.

```franko
ptr<int32> p;
if (p != null) {
    $(p);      // valid: p is refined to pointing inside the guard
}
$(p);          // error: p is provably null after the guard fails
```

When a guard's assertion contradicts a definite current state, that path is statically infeasible; the compiler suppresses dereference diagnostics inside it and does not let its state flow onward.

```franko
ptr<int32> p;
p = getptr(x);
if (p != null) {
    $(p);      // valid
}
$(p);          // valid: p is still pointing; the else path was infeasible
```

The same refinement runs for loop conditions, so `while (p != null) { ... }` sees `p` as pointing inside the body and as null after the loop.

## 30.7 Limitations

Current limitations of the pointer null-state check:

- values of unknown provenance (parameters, field and element reads, and unresolved call results) are treated as maybe-null: dereferencing one is a warning, not an error, and a null value may still flow through them;
- only plain pointer **variables** are tracked. A pointer held in a struct field, an array element, or behind a `$(...)` chain is not tracked;
- pointers hidden inside returned structs or tuples are not tracked;
- global null-state writes are not tracked across functions;
- only one level of pointer-to-pointer writes is followed;
- deeply nested conditions may reduce precision;
- recursive or very deep call chains are handled conservatively, so some
  nullness problems may be missed;
- a non-variable dereference such as `$(f())` is an error when the resolved return state is definitely null and a warning when it is possibly null.

## 30.8 Capability Linting

A separate, independent check warns when a `ptr`/`mview` local variable or
parameter requests permissions it never exercises, so developers default
to the tightest pointer that fits. See the "Capability Linter" section of
[Pointers](25-pointers.md).

## 30.9 Dangling-Pointer Checking

The compiler checks `return` values for pointers that outlive the storage
they refer to, rejects pointer stores that move frame-scoped storage into
global or caller-owned storage, and detects use of a pointer after its
inner block or loop body has ended. The check follows pointers through
function calls: a frame pointer forwarded through one or more functions into
global or caller-owned storage is diagnosed at the call. Each function still
reports its own return/store/lifetime diagnostics, and code removed as
unreachable produces no diagnostics.

### 30.9.1 What Is Tracked

Taking a pointer to a local or parameter and returning it is rejected:

```franko
int32 x
ptr<int32> p = getptr(x)
ptr<int32> q = p
return q                        // error: x dies with the frame
```

Taking a pointer to a pointer parameter, or dereferencing one, is
caller-owned and safe to return:

```franko
func ok(ptr<int32> p) -> ptr<int32> {
    return getptr($(p));       // points at the caller's storage
}
```

The check follows assignments, pointer copies, `$(...)` aliases, and
`getptr`/`getmview`/`getview` of lvalues. Pointer parameters and globals
are never assumed to point into the current function's frame.

### 30.9.2 Path and Loop Handling

`if`/`else` is followed branch by branch, and a branch whose condition cannot
hold is not diagnosed. For `while`, the state after the loop accounts for zero
iterations, normal exits, and `break`; `continue` paths are kept separate, and
a loop with no possible exit is treated as non-falling-through. `for` loops
receive the same treatment as their equivalent block and `while` loop.

Null guards refine what is known about the storage too:

- in an `if (p == null)` path, `p` is null, so returning it on that path is
  safe;
- in an `if (p != null)` path, the surviving value is marked definitely
  non-null, so a later return can be classified as definitely dangling
  rather than only possibly dangling;
- `if (p != null)` over a pointer that is definitely null is infeasible;
- `if (p == null)` over a pointer that is definitely non-null is
  infeasible; every `getptr`/`getmview`/`getview` result is definitely
  non-null, even when the storage it points at is caller-owned;
- a return inside an infeasible path is not diagnosed, because that path
  never executes.

On the condition-false exit of `while (p != null)`, `p` is null, so
returning `p` is safe on that path even though the loop body saw a
non-null pointer. Paths that leave the loop through `break` are handled
separately and may still return a non-null `p`.

### 30.9.3 The Return Rule

At a pointer-typed `return`:

- a value that definitely refers to the current frame → **error**;
- a value that may refer to the current frame after a branch, or
  may refer elsewhere → **warning**.

The diagnostic names the local variable or parameter:

```text
Intraprocedural: Returning a pointer to local variable 'x' from function 'bad' (dangling after return)
Intraprocedural: Returning a pointer to parameter 'x' from function 'bad' (dangling after return)
Intraprocedural: Returning a pointer that may refer to local variable 'x' from function 'maybe' (possibly dangling after return)
```

### 30.9.4 Decayed Views

Static-to-dynamic array view decay does not create a new storage object:
the converted view refers to the same array. Therefore:

- returning a decayed parameter or global view is **safe** and is not
  diagnosed;
- a returned decayed view still refers to the original static storage;
- returning a decayed view of **local** array storage is still a normal
  frame-storage escape and is rejected by the same return rule as any
  other pointer to a local.

Literal views refer to constant storage with static lifetime, so
returning or keeping one is safe. Global initializers are never checked
as function bodies.

### 30.9.5 The Store Rule

A pointer store is rejected when the stored value refers to current-frame
storage and the target storage is caller-owned or global:

```franko
ptr<int32> global_p

func bad() -> void {
    int32 x
    global_p = getptr(x)      // error: x dies with this frame
    return
}
```

The same rule applies to stores through a pointer parameter:

```franko
func bad(ptr<ptr<int32>> out) -> void {
    int32 x
    $(out) = getptr(x)        // error: caller storage outlives x
    return
}
```

The diagnostic is:

```text
Intraprocedural: Cannot store a view of frame or operator-borrowed storage into a global or caller-owned location
```

A local target is intentionally allowed. For an operator method's borrowed
`other` parameter this is safe because the local dies before the operator call
returns. Storing a view of an inner local into a longer-lived local is still
allowed and can still dangle; that is the pre-existing local-view hazard.
Forwarding the pointer through a callee that stores it is also caught.

### 30.9.6 Limitations

The check intentionally does not detect:

- pointers hidden inside returned structs or tuples;
- pointer fields inside structs or other aggregates when the escape does not
  go through a direct global or out-parameter store;
- global dynamic-array lifecycle;
- every loop-carried lifetime fact: loops are checked conservatively, so a
  pointer that becomes dangling only on a later iteration may be missed;
- every escape through a recursive or very deep call chain.

The store rule closes the direct global/out-parameter store. An operator
method's `other` parameter is treated as call-scoped borrowed storage, so
storing it into caller-owned or global storage is rejected. Forwarding a
borrowed or ordinary argument through a callee that stores it is also caught.
Later checks do not assume that a call left a global pointer untouched.

## 30.10 Dynamic-Array Lifecycle Checking

The compiler also tracks whether a function-local dynamic array has
allocated element storage, whether element pointers still refer to its current
buffer, and what a called function does to the array. A callee can require an
initialized array, initialize or free the caller's array, or return an element
pointer into it. Each function or method still starts with a fresh local
lifecycle state.

### 30.10.1 Array States

Each tracked dynamic array is in one of three states:

| State | Meaning |
|---|---|
| `UNINITED` | no element storage; never initialized, or already `uninit`ed |
| `INITED` | element storage is allocated |
| `MAYBE_INITED` | initialized on some paths, or a call may have changed it |

A separate history flag distinguishes a never-initialized array from one
that was already freed. That only changes diagnostic wording; it is not
a fourth lifecycle state.

### 30.10.2 What Is Tracked

The check covers function-local dynamic array variables and owner
`ptr<[T]>` values obtained from `getptr(localArray)`, including pointer
copies, casts, and `getptr($(p))` aliases.

It does not yet track:

- dynamic arrays stored in struct fields;
- individual elements of nested dynamic arrays below the outermost tracked
  array;
- global dynamic array variables.

Global owner pointer variables are not trusted across functions: they enter
each function unknown, can be certified by a local assignment such as
`G = getptr(d)`, and are forgotten after any function call. Global dynamic
array lifecycle is not tracked at all.

Element pointers/views (`getptr`/`getmview`/`getview(d[i])`) refer to the
element buffer. Whole-array `view<[T]>`/`mview<[T]>` values refer to the array
object and are not invalidated when the buffer changes.

A call is checked against what the callee does: a callee that only reads or
writes elements does not change the caller's lifecycle state; a callee that
requires an initialized array reports a definite or possible misuse; a callee
that calls `init` makes the caller array `INITED`; and a callee that calls
`resize`/`uninit` can invalidate element pointers. Passing an `mview<[T]>` or
`view<[T]>` cannot initialize, resize, or free the array, so it does not change
the caller's lifecycle state.

### 30.10.3 Operations

| Operation | Required state | Result |
|---|---|---|
| `d.init(n)`, `d.init_zero(n)` | any | `INITED`; repeated init is not diagnosed |
| `d.resize(n)` | `INITED` | stays `INITED` |
| `d.uninit()` | `INITED` | `UNINITED`; invalid free if never initialized, double free if already uninitialized |
| `d[i]`, `d.memset`, `d.memcpy` destination, `d.memmove` | `INITED` | unchanged |
| `d.memcmp`, `d.memchr`, `d.memmem`, `d.read_*`, `d.read_bit`, `d.count_set_bits` | `INITED` | unchanged |
| `d.length()` | any state | unchanged; safe after `uninit` |

`d.memcpy`, `d.memcmp`, and `d.memmem` also check the first argument's array
source when it is a tracked dynamic array.

A use of a definitely `UNINITED` array is an error; a use of a
`MAYBE_INITED` array is a warning.

`uninit()` on a definitely never-initialized array is an **invalid
free**. `uninit()` on an array that was already uninitialized is a
**double free**. Both are errors when definite; possible cases are
warnings.

Repeated `init`/`init_zero` is deliberately not diagnosed: the first
call may have returned `-1`, and retrying is valid. The runtime still
aborts if the array really was initialized already.

### 30.10.4 Guard Traps

A comparison of `d.length()` against a constant refines the state on
each path:

| Guard | True path | False path |
|---|---|---|
| `d.length() == 0` | `UNINITED` | `INITED` |
| `d.length() != 0` | `INITED` | `UNINITED` |
| `d.length() > 0`, `>= 1` | `INITED` | `UNINITED` |
| `d.length() <= 0`, `< 1` | `UNINITED` | `INITED` |

Nonzero constant comparisons also certify the nonzero side as `INITED`.
A branch that contradicts the known state cannot execute, so it produces no
diagnostics:

```franko
[int32] d;
d.init(4);
if (d.length() == 0) {
    d[0] = 1;       // not diagnosed: this path cannot execute
}
```

The inverse form is also rejected when it is definitely wrong:

```franko
[int32] d;
if (d.length() > 0) {
    d[0] = 1;       // not diagnosed: this path cannot execute
}
```

### 30.10.5 Branches and Loops

`if`/`else` is followed branch by branch. Loops account for zero iterations,
normal exits, `break`, and `continue`, and apply the condition-false
refinement to each exit path. A loop with no possible normal exit or `break`
is treated as non-falling-through, so its tail is not checked. This means:

```franko
[int32] d;
while (d.length() == 0) {
    d.init(4);
}
d[0] = 1;           // valid: the loop exited with d initialized
```

### 30.10.6 Buffer Invalidation

An element pointer/view is tied to the buffer that existed when it was
created. `init`/`init_zero`, `resize`, and `uninit` replace or release that
buffer. Using an element pointer whose buffer has definitely been replaced or
freed is an **error**; using one whose buffer may have been replaced is a
**warning**.

The check applies at dereference, indexing, assignment through the alias, call
argument, return, and creation intrinsics over the alias. An owner `ptr<[T]>`
or a whole-array `view<[T]>`/`mview<[T]>` refers to the stable array object
and is **not** invalidated by `init`/`resize`/`uninit`; only
element pointers/views into the element buffer are invalidated. A returned
element alias keeps its association with the caller's array, so resizing after
the call is diagnosed. When a callee may invalidate array parameter `i` and
may use element-pointer parameter `j`, and the caller passes an element alias
of `i` as `j`, the compiler emits a possible cross-parameter invalidation
warning.

### 30.10.7 Missing `uninit`

A local dynamic array's runtime destructor already frees its buffer when
the array goes out of scope. Missing an explicit `uninit()` is therefore
a **warning**, not a memory leak error:

```text
Intraprocedural: Dynamic array 'd' is still initialized at return from function 'f'
(missing explicit uninit)
```

The warning is suppressed when the array escaped through a global, an
aggregate store, or a call that may let it escape, because the compiler can no
longer prove the state.

### 30.10.8 Limitations

The check does not detect:

- lifecycle misuse of struct-field or nested-element arrays;
- lifecycle tracking for global dynamic arrays;
- allocation failure of `init`/`init_zero` (the successful path is modeled);
- every loop-carried lifecycle fact: loops are checked conservatively;
- every effect of a recursive or very deep call chain;
- use of an element pointer after its root array's scope ends; that is
  reported as a dangling-pointer error instead.
