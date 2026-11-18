# 32. Memory Safety Checking

Franko checks two independent properties of pointers: **null state**
and **dangling storage**. Both checks cover all three pointer types
(`ptr`, `mview`, and `view`). A third check tracks the initialization
lifecycle and buffer reuse of function-local dynamic arrays (see
[Dynamic-Array Lifecycle Checking](#3210-dynamic-array-lifecycle-checking)).

The checks also follow values across function calls. Passing a null pointer to
a function that uses it, passing a dangling pointer as a call argument,
or changing a caller's dynamic array through a callee are diagnosed at
the relevant call site. Messages are prefixed
with `Intraprocedural:` when the problem is inside the function being checked
and with `Interprocedural:` when it is discovered at a call.

The null-state check covers:

- dereferencing a null pointer (an error);
- dereferencing a possibly-null pointer (a warning);
- that `null` is only used for pointer types (see [Types](09-types.md)).

The null-state check also covers values crossing function boundaries: a
callee's parameter use becomes a requirement on the caller's argument.
A function still reports its own local maybe-null warnings as well.

## 32.1 Pointer States

For each pointer variable the compiler tracks one of:

| State | Meaning |
|---|---|
| pointing | the variable holds a certified, real storage pointer |
| null | the variable is definitely null on the current path |
| maybe-null | the variable is null on some continuing paths |

## 32.2 Assignment Rules

- an ordinary pointer declaration is **null-initialized**, so it starts in the null state;
- assigning the `null` literal makes the target null;
- assigning `ptr(...)`, `mview(...)`, or `view(...)` makes the target pointing; these are the **only** certifications of a non-null pointer;
- assigning another pointer variable copies that variable's exact state, so a null state propagates through copies and certification propagates transitively;
- assigning a value of unknown provenance (a call result or a parameter) yields a maybe-null state;
- at function entry, a pointer parameter starts as maybe-null because the compiler cannot see the value it receives. A global view's state is known from its declaration: a global without a static initializer is null, and a global initialized from `view`/a literal view is pointing. Calls do not change a global view's state because globals are immutable. Exceptions for parameters: a method's `self` parameter starts pointing, because the receiver is a storage-backed lvalue; and the args-form `main` parameter starts pointing, because it always receives a live view.

## 32.3 Dereference Checking

- `$(p)` where `p` is **null** is an **error**;
- `$(p)` where `p` is **possibly null** is a **warning**.

A variable warns at most once per check, so revisiting the same dereference on
another path does not repeat the warning.

## 32.4 Branch Handling

`if` / `else` is checked branch by branch. Conditions may be combined with
`!`, `&&`, and `||`; the compiler splits a condition into the alternative
states it allows and follows each feasible alternative through the branch. Only
branches that continue past the statement affect later checks. A branch that
**definitely returns** does not affect the code following the `if`, because that
path never continues there. A branch whose condition contradicts a known state
cannot execute and produces no diagnostics.

Because alternatives are followed separately, a compound guard such as
`p != null && q != null` refines both pointers inside the branch, and a guard
such as `p == null || q == null` refines one pointer on each alternative. A
diagnostic is an error only when every alternative that reaches it finds a
definite violation; a violation on only some alternatives is a warning.

```franko
func guarded(ptr<int32> p, ptr<int32> q) -> int32 {
    if (p == null || q == null) {
        return -1
    }
    return $(p) + $(q)     // valid: both are non-null on every continuing path
}
```

Very deeply nested conditions may be handled more conservatively, so some
warnings can be missed.

## 32.5 Loop Handling

`while` loops are checked without proving termination:

- the body is checked once per feasible condition alternative;
- the state after the loop accounts for zero iterations, normal exits, and
  `break`;
- `break` and `continue` paths are handled separately;
- if neither a normal exit nor a `break` is possible, the loop is treated as
  non-falling-through, so its tail is not checked.

Loop termination is not proven; the post-loop state is conservative.

`for` loops receive the same treatment as the equivalent block and `while` loop, so they are handled the same way. See [Shorthands and Equivalences](25-desugaring.md).

## 32.6 Guard Refinement

Inside `if (p == null)` / `if (p != null)` (either operand order), the compiler refines `p`'s state per path: the then-branch of `p != null` sees `p` as pointing, and the code after the loop/branch of a failed guard sees `p` as null. The same applies to `!`, `&&`, and `||` combinations, and to `d.length()` comparisons for dynamic arrays.

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
p = ptr(x);
if (p != null) {
    $(p);      // valid
}
$(p);          // valid: p is still pointing; the else path was infeasible
```

The same refinement runs for loop conditions, so `while (p != null) { ... }` sees `p` as pointing inside the body and as null after the loop. A compound loop guard such as `while (p != null && q != null)` refines both pointers in the body and applies the corresponding alternatives to the exits.

## 32.7 Limitations

Current limitations of the pointer null-state check:

- values of unknown provenance (parameters and unresolved call results) are treated as maybe-null: dereferencing one is a warning, not an error, and a null value may still flow through them;
- only plain pointer **variables** are tracked. Pointers cannot be stored in
  aggregates, so there is no field or element state to track; a pointer
  reached only through a stacked-pointer dereference chain (for example
  `$($(pp))`) is tracked with reduced precision and some null flows
  through stacked pointers may be missed;
- functions cannot return pointers, so there is no returned-pointer null
  state to track;
- global view slots are immutable, so there is no global null-state write to track;
- deeply nested conditions may reduce precision;
- recursive or very deep call chains are handled conservatively, so some
  nullness problems may be missed;
- a non-variable dereference such as `$(f())` is an error when the resolved state is definitely null and a warning when it is possibly null.

## 32.8 Capability Linting

A separate, independent check warns when a `ptr`/`mview` local variable or
parameter requests permissions it never exercises, so developers default
to the tightest pointer that fits. See the "Capability Linter" section of
[Pointers](26-pointers.md).

## 32.9 Dangling-Pointer Checking

Pointers are downward-only values: they may be passed into callees but
never flow back up through returns, aggregates, mutable globals, or
writable pointer-to-pointer parameters. Those escapes are rejected by the type
rules (see [Pointers](26-pointers.md)). The dangling-pointer check covers
what remains: use of a pointer after its inner block or loop body has
ended, and passing such a pointer as a call argument. Each function
reports its own lifetime diagnostics, and code removed as unreachable
produces no diagnostics.

### 32.9.1 What Is Tracked

Taking `ptr`/`mview`/`view` of a local or parameter records a
frame origin for the resulting pointer. Copying that pointer, assigning
it to another pointer variable, or aliasing it through `$(...)` carries
the same origin. Globals never produce a frame origin, and pointer
parameters stay alive for the whole function body, so they are never
treated as dangling within their own function.

```franko
func use(ptr<int32> p) -> void {
    int32 x
    ptr<int32> q = ptr(x)
    $(q) = $(p)      // valid: both pointees are alive here
    return
}
```

An operator method's borrowed `other` parameter is call-scoped borrowed
storage: it is alive for the duration of the call expression and must
not be used after that expression ends.

### 32.9.2 Path and Loop Handling

`if`/`else` is followed branch by branch, and a branch whose condition cannot
hold is not diagnosed. For `while`, the state after the loop accounts for zero
iterations, normal exits, and `break`; `continue` paths are kept separate, and
a loop with no possible exit is treated as non-falling-through. `for` loops
receive the same treatment as their equivalent block and `while` loop.

Null guards refine what is known about the storage too:

- in an `if (p == null)` path, `p` is null, so there is no dangling
  storage to use on that path;
- in an `if (p != null)` path, the surviving value is marked definitely
  non-null, so a later dangling use can be classified as definitely
  dangling rather than only possibly dangling;
- `if (p != null)` over a pointer that is definitely null is infeasible;
- `if (p == null)` over a pointer that is definitely non-null is
  infeasible; every `ptr`/`mview`/`view` result is definitely
  non-null, even when the storage it points at is caller-owned;
- a use inside an infeasible path is not diagnosed, because that path
  never executes.

On the condition-false exit of `while (p != null)`, `p` is null, so
using `p` there is safe on that path even though the loop body saw a
non-null pointer. Paths that leave the loop through `break` are handled
separately and may still see a non-null `p`.

### 32.9.3 Inner-Block and Loop Use After Scope

A dereference or use whose frame origins are dead on every reaching
path is an **error**; a use that is dead on only some paths is a
**warning**. Diagnostics are deduplicated per site:

```text
Intraprocedural: Cannot dereference pointer to 'x' after its scope ended
Intraprocedural: Cannot use pointer to 'x' after its scope ended
Intraprocedural: Pointer may refer to 'x' after its scope ended
```

```franko
ptr<int32> p;
if (cond != 0) {
    int32 x
    p = ptr(x)
}
$(p)    // error or warning: x died with the inner block
```

### 32.9.4 Pointer Arguments

Each call argument is checked for pointer liveness before the call is
analyzed. Passing a pointer whose storage has already ended is diagnosed
at the call site with the same `Cannot use pointer ... after its scope
ended` wording, deduplicated per call site. Parameters stay alive for
the whole function, so forwarding a live parameter is always safe.

### 32.9.5 Decayed Views

Static-to-dynamic array view decay does not create a new storage object:
the converted view refers to the same array. A decayed view of
**local** array storage therefore dangles exactly like any other pointer
to that local once the block ends. Decayed views of parameter or global
storage stay alive with that storage.

Literal views refer to constant storage with static lifetime, so keeping
one is safe. Global initializers are never checked as function bodies.

### 32.9.6 Limitations

The check intentionally does not detect:

- stacked-pointer chains beyond plain-variable precision: a pointer
  reached only through several layers of `$($(pp))` dereferences may be
  missed, and loop-carried stacked-pointer facts are conservative;
- every loop-carried lifetime fact: loops are checked conservatively, so a
  pointer that becomes dangling only on a later iteration may be missed;
- every escape through a recursive or very deep call chain.

Pointers cannot be stored in aggregates and cannot be returned, so there
are no hidden aggregate or return paths to track. A view of an inner
local stored into a longer-lived local is still allowed and can still
dangle; that is the pre-existing local-view hazard.

## 32.10 Dynamic-Array Lifecycle Checking

The compiler also tracks whether a function-local dynamic array has
allocated element storage, whether element pointers still refer to its current
buffer, and what a called function does to the array. A callee can require an
initialized array, or initialize or free the caller's array. Each function
or method still starts with a fresh local lifecycle state.

### 32.10.1 Array States

Each tracked dynamic array is in one of three states:

| State | Meaning |
|---|---|
| `UNINITED` | no element storage; never initialized, or already `uninit`ed |
| `INITED` | element storage is allocated |
| `MAYBE_INITED` | initialized on some paths, or a call may have changed it |

A separate history flag distinguishes a never-initialized array from one
that was already freed. That only changes diagnostic wording; it is not
a fourth lifecycle state.

### 32.10.2 What Is Tracked

The check covers function-local dynamic array variables and owner
`ptr<[T]>` values obtained from `ptr(localArray)`, including pointer
copies, casts, and `ptr($(p))` aliases.

Owner-array parameters are checked twice inside the callee: once assuming the
parameter was already initialized by the caller, and once assuming it was
uninitialized. A problem that appears only under one assumption is a warning;
a problem that appears under both is an error. This is what catches
use-after-`uninit` and double free inside a function even when every caller
passes a valid array, while caller-side requirements still report passing an
uninitialized array as an error.

The two assumptions are used to find lifecycle misuse, not to compare the
entry and exit states. A function may legitimately initialize an owner-array
parameter it does not own and leave it initialized for the caller, and it may
legitimately release caller-owned storage as part of its contract. End-state
differences for a parameter are therefore not diagnosed. Missing-uninit
warnings apply only to function-local arrays the function itself owns; a
method that releases an array reached through `self` is likewise not treated
as releasing a direct parameter.

It does not yet track:

- dynamic arrays stored in struct fields;
- individual elements of nested dynamic arrays below the outermost tracked
  array.

Global variables cannot be dynamic arrays (they must be passable by value), so
there is no global dynamic-array lifecycle to track.

Element pointers/views (`ptr`/`mview`/`view(d[i])`) refer to the
element buffer. Whole-array `view<[T]>`/`mview<[T]>` values refer to the array
object and are not invalidated when the buffer changes.

A call is checked against what the callee does: a callee that only reads or
writes elements does not change the caller's lifecycle state; a callee that
requires an initialized array reports a definite or possible misuse; a callee
that calls `init` makes the caller array `INITED`; and a callee that calls
`resize`/`uninit` can invalidate element pointers. Passing an `mview<[T]>` or
`view<[T]>` cannot initialize, resize, or free the array, so it does not change
the caller's lifecycle state.

### 32.10.3 Operations

| Operation | Required state | Result |
|---|---|---|
| `d.init(n)`, `d.init_zero(n)` | any | `INITED`; repeated init is not diagnosed |
| `d.resize(n)` | `INITED` | stays `INITED` |
| `d.uninit()` | `INITED` | `UNINITED`; invalid free if never initialized, double free if already uninitialized |
| `d[i]`, `d.memset`, `d.memcpy` destination, `d.memmove`, `d.memset_*`, `d.write_bit`, `d.flip_bit`, `d.swap_bits`, `d.reverse_bits`, `d.shift_bits_*` | `INITED` | unchanged |
| `d.memcmp`, `d.memchr`, `d.memmem`, `d.read_*`, `d.read_bit`, `d.popcount`, `d.count_trailing_zeros`, `d.count_leading_zeros` | `INITED` | unchanged |
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

### 32.10.4 Guard Traps

A comparison of `d.length()` against a constant refines the state on
each path:

| Guard | True path | False path |
|---|---|---|
| `d.length() == 0` | `UNINITED` | `INITED` |
| `d.length() != 0` | `INITED` | `UNINITED` |
| `d.length() > 0`, `>= 1` | `INITED` | `UNINITED` |
| `d.length() <= 0`, `< 1` | `UNINITED` | `INITED` |

Nonzero constant comparisons also certify the nonzero side as `INITED`.
Guards may be combined with `!`, `&&`, and `||`; each feasible alternative
refines the state independently, so
`if (d.length() == 0 || c == 0)` treats the zero-length alternative as
`UNINITED` and the `c == 0` alternative as unconstrained. A branch that
contradicts the known state cannot execute, so it produces no diagnostics:

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

### 32.10.5 Branches and Loops

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

### 32.10.6 Buffer Invalidation

An element pointer/view is tied to the buffer that existed when it was
created. `init`/`init_zero`, `resize`, and `uninit` replace or release that
buffer. Using an element pointer whose buffer has definitely been replaced or
freed is an **error**; using one whose buffer may have been replaced is a
**warning**.

The check applies at dereference, indexing, assignment through the alias, call
argument, and creation intrinsics over the alias. An owner `ptr<[T]>`
or a whole-array `view<[T]>`/`mview<[T]>` refers to the stable array object
and is **not** invalidated by `init`/`resize`/`uninit`; only
element pointers/views into the element buffer are invalidated. When a
callee may invalidate array parameter `i` and
may use element-pointer parameter `j`, and the caller passes an element alias
of `i` as `j`, the compiler emits a possible cross-parameter invalidation
warning.

### 32.10.7 Missing `uninit`

A local dynamic array's runtime destructor already frees its buffer when
the array goes out of scope. Missing an explicit `uninit()` is therefore
a **warning**, not a memory leak error:

```text
Intraprocedural: Dynamic array 'd' is still initialized at return from function 'f'
(missing explicit uninit)
```

The warning is suppressed when the array escaped through a global or a call
that may let it escape, because the compiler can no longer prove the state.
The audit runs at every explicit `return` and at the implicit end of the
function, but only for function-local arrays the function owns.

Owner-array parameters are not checked for leaks: the function does not own
their storage. A function may initialize an owner-array parameter it received
uninitialized and leave it initialized for the caller, or it may release
caller-owned storage as part of its contract. Entry/exit state differences for
parameters are not diagnosed. The same applies to arrays reached through
`self`; they belong to the receiver, not to the function.

### 32.10.8 Limitations

The check does not detect:

- lifecycle misuse of struct-field or nested-element arrays;
- allocation failure of `init`/`init_zero` (the successful path is modeled);
- every loop-carried lifecycle fact: loops are checked conservatively;
- every effect of a recursive or very deep call chain;
- use of an element pointer after its root array's scope ends; that is
  reported as a dangling-pointer error instead.
