# 27. Memory Safety Checking

Franko checks two independent properties of pointers: **null state**
and **dangling storage**. Both checks cover all three pointer types
(`ptr`, `mview`, and `view`). A third, separate intra-procedural check
tracks the initialization lifecycle of function-local dynamic arrays
(see
[Dynamic-Array Lifecycle Checking](#2710-dynamic-array-lifecycle-checking)).

The null-state check tracks one fact per pointer variable: whether it is
definitely null, definitely pointing, or possibly null on the current
execution path. The dangling-storage check tracks where a returned
pointer's storage lives (see
[Dangling-Pointer Checking](#279-dangling-pointer-checking)).

The null-state check covers:

- dereferencing a null pointer (an error);
- dereferencing a possibly-null pointer (a warning);
- that `null` is only used for pointer types (see [Types](06-types.md)).

The null-state check is **intra-procedural**: each function body is analyzed separately with a fresh state. Called functions are not analyzed for their effects on the caller's pointers, and no facts cross function boundaries (interprocedural nullability is deliberately not attempted).

## 27.1 Pointer States

For each pointer variable the compiler tracks one of:

| State | Meaning |
|---|---|
| pointing | the variable holds a certified, real storage pointer |
| null | the variable is definitely null on the current path |
| maybe-null | the variable is null on some continuing paths |

## 27.2 Assignment Rules

- an ordinary pointer declaration is **null-initialized**, so it starts in the null state;
- assigning the `null` literal makes the target null;
- assigning `getptr(...)`, `getmview(...)`, or `getview(...)` makes the target pointing; these are the **only** certifications of a non-null pointer;
- assigning another pointer variable copies that variable's exact state, so a null state propagates through copies and certification propagates transitively;
- assigning a value of unknown provenance (a call result, a parameter, a struct-field or array-element read) yields a maybe-null state;
- at function entry, every tracked pointer variable is maybe-null, because parameters and globals carry values the compiler cannot see. Exceptions: a method's `self` parameter enters pointing, because the receiver is a storage-backed lvalue; and the args-form `main` parameter enters pointing, because it always receives a live view.

## 27.3 Dereference Checking

- `$(p)` where `p` is **null** is an **error**;
- `$(p)` where `p` is **possibly null** is a **warning**.

A variable warns at most once per check, so a path-sensitive traversal that revisits the same deref site does not repeat the warning.

## 27.4 Branch Handling

`if` / `else` is analyzed by checking each branch from the pre-branch state and merging the states of the continuing paths.

A branch that **definitely returns** does not contribute its state to the code following the `if`, because that path never continues there.

Branch-sensitive analysis is bounded by a maximum nesting depth. Beyond that depth the compiler falls back to a conservative path-insensitive merge.

## 27.5 Loop Handling

`while` loops are handled with a bounded abstraction:

- the body is checked once;
- zero-trip and back-edge exit states are included only when the
  condition-false guard is feasible for that state;
- `break` and `continue` states are merged separately;
- if neither a normal exit nor a `break` is feasible, the loop is
  treated as non-falling-through, so its tail is not analyzed.

Loop termination is not proven; the post-loop state is conservative.

`for` loops receive the same treatment as the equivalent block and `while` loop, so they are handled the same way. See [Desugaring](05-desugaring.md).

## 27.6 Guard Refinement

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

Refinement is path-sensitive up to the nesting-depth limit described above; beyond the limit, refinement stops and states merge conservatively.

The same refinement runs for loop conditions, so `while (p != null) { ... }` sees `p` as pointing inside the body and as null after the loop.

## 27.7 Limitations

Current limitations of the pointer null-state check:

- values of unknown provenance (call results, parameters, field and element reads) are treated as maybe-null: dereferencing one is a warning, not an error, and a null value may still flow through them;
- only plain pointer **variables** are tracked. A pointer held in a struct field, an array element, or behind a `$(...)` chain is not tracked;
- dangling pointers are only detected for values escaping a function by `return`; a pointer may still outlive its pointee through calls, globals, aggregates, or block exits (see [Dangling-Pointer Checking](#279-dangling-pointer-checking));
- checks are intra-procedural; pointer effects of called functions are not analyzed;
- branch-path sensitivity is capped by the nesting-depth limit described above.

## 27.8 Capability Linting

A separate, independent check warns when a `ptr`/`mview` local variable or
parameter requests permissions it never exercises, so developers default
to the tightest pointer that fits. See the "Capability Linter" section of
[Pointers](24-pointers.md).

## 27.9 Dangling-Pointer Checking

The compiler checks `return` values for pointers that outlive the storage
they refer to. The check is **return-only and intra-procedural**: each
function or method body is analyzed separately, and only `return`
expressions are classified. Dead functions and pruned branches cannot
produce diagnostics.

### 27.9.1 What Is Tracked

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

The analysis follows assignments, pointer copies, `$(...)` aliases, and
`getptr`/`getmview`/`getview` of lvalues. Pointer parameters and globals
are never assumed to point into the current frame.

### 27.9.2 Path and Loop Handling

The dangling-storage check uses the same bounded path-sensitive traversal
as the null-state check: `if`/`else` is followed branch by branch up to
the nesting-depth limit, then merged conservatively. For `while`, each
zero-trip and back-edge exit is included only when the condition-false
guard is feasible for that state; `break` and `continue` states are
merged separately, and a loop with no feasible exit is treated as
non-falling-through. `for` loops receive the same treatment as their
equivalent block and `while` loop.

Null guards refine what is known about the storage too:

- in an `if (p == null)` path, `p` is null, so any recorded local or
  global storage origin for `p` is discarded;
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
non-null pointer. Paths that leave the loop through `break` are merged
separately and may still return a non-null `p`.

### 27.9.3 The Return Rule

At a pointer-typed `return`:

- a value that definitely refers to the current frame → **error**;
- a value that may refer to the current frame after a branch merge, or
  may refer elsewhere → **warning**.

The diagnostic names the local variable or parameter:

```text
Returning a pointer to local variable 'x' from function 'bad' (dangling after return)
Returning a pointer to parameter 'x' from function 'bad' (dangling after return)
Returning a pointer that may refer to local variable 'x' from function 'maybe' (possibly dangling after return)
```

### 27.9.4 Decayed Views

Static-to-dynamic array view decay does not create a new storage object:
the converted view refers to the same array. Therefore:

- returning a decayed parameter or global view is **safe** and is not
  diagnosed;
- a returned decayed view still refers to the original static storage;
- returning a decayed view of **local** array storage is still a normal
  frame-storage escape and is rejected by the same return rule as any
  other pointer to a local.

Literal views refer to constant storage with static lifetime, so
returning or keeping one is safe. Global initializers are never analyzed
as function bodies.

### 27.9.5 Limitations

The check intentionally does not detect:

- pointers escaping through function calls, out-parameters, or stores into
  globals;
- pointers hidden inside returned structs or arrays;
- use after an inner block ends but before the function returns;
- dynamic-array buffer invalidation after `resize`/`uninit`;
- interprocedural ownership or lifetime facts;
- loop fixed points (the body is analyzed once, so loop-carried state is
  merged conservatively).

Calls do conservatively forget state, so later diagnostics do not assume a
callee left pointer variables untouched. None of these cases produce a
false positive, but they can produce false negatives.

## 27.10 Dynamic-Array Lifecycle Checking

The compiler also tracks whether a function-local dynamic array has
allocated element storage. The check is **intra-procedural**: each
function or method starts with a fresh lifecycle state.

### 27.10.1 Array States

Each tracked dynamic array is in one of three states:

| State | Meaning |
|---|---|
| `UNINITED` | no element storage; never initialized, or already `uninit`ed |
| `INITED` | element storage is allocated |
| `MAYBE_INITED` | initialized on some paths, or possibly changed by a call |

A separate history flag distinguishes a never-initialized array from one
that was already freed. That only changes diagnostic wording; it is not
a fourth lifecycle state.

### 27.10.2 What Is Tracked

The check covers function-local dynamic array variables and owner
`ptr<[T]>` values obtained from `getptr(localArray)`, including pointer
copies, casts, and `getptr($(p))` aliases.

It does not yet track:

- dynamic arrays stored in struct fields;
- individual elements of nested dynamic arrays;
- element pointers such as `getptr(d[0])`;
- `mview<[T]>` and `view<[T]>` aliases;
- global dynamic array variables.

Global owner pointer variables are not trusted across functions: they
enter each function unknown, can be certified by a local assignment such
as `G = getptr(d)`, and are forgotten after any function call. Global
dynamic array lifecycle is not tracked at all.

Passing an owner `ptr<[T]>` to a function marks the array
`MAYBE_INITED`, because the callee may call `init`, `resize`, or
`uninit`. Passing an `mview<[T]>` or `view<[T]>` does not change the
state, because those permissions cannot allocate or free storage.

### 27.10.3 Operations

| Operation | Required state | Result |
|---|---|---|
| `d.init(n)`, `d.init_zero(n)`, `d(n)` | any | `INITED`; repeated init is not diagnosed |
| `d.resize(n)` | `INITED` | stays `INITED` |
| `d.uninit()` | `INITED` | `UNINITED`; invalid free if never initialized, double free if already uninitialized |
| `d[i]`, `d.memset`, `d.memcpy` destination, `d.memmove` | `INITED` | unchanged |
| `d.memcmp`, `d.memchr`, `d.memmem`, `d.read_*`, `d.read_bit`, `d.count_set_bits` | `INITED` | unchanged |
| `d.length()` | any state | unchanged; safe after `uninit` |

A use of a definitely `UNINITED` array is an error; a use of a
`MAYBE_INITED` array is a warning.

`uninit()` on a definitely never-initialized array is an **invalid
free**. `uninit()` on an array that was already uninitialized is a
**double free**. Both are errors when definite; possible cases are
warnings.

Repeated `init`/`init_zero` is deliberately not diagnosed: the first
call may have returned `-1`, and retrying is valid. The runtime still
aborts if the array really was initialized already.

### 27.10.4 Guard Traps

A comparison of `d.length()` against a constant refines the state on
each path:

| Guard | True path | False path |
|---|---|---|
| `d.length() == 0` | `UNINITED` | `INITED` |
| `d.length() != 0` | `INITED` | `UNINITED` |
| `d.length() > 0`, `>= 1` | `INITED` | `UNINITED` |
| `d.length() <= 0`, `< 1` | `UNINITED` | `INITED` |

Nonzero constant comparisons also certify the nonzero side as `INITED`.
The compiler uses the same infeasible-branch suppression as the
null-state check:

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

### 27.10.5 Branches and Loops

`if`/`else` is path-sensitive to the same bounded branch depth as the
other checks. Loops merge zero-trip, back-edge, `break`, and `continue`
states, and apply the condition-false refinement to each exit path. A
loop with no feasible normal exit or `break` is treated as
non-falling-through, so its tail is not analyzed. This means:

```franko
[int32] d;
while (d.length() == 0) {
    d.init(4);
}
d[0] = 1;           // valid: the loop exited with d initialized
```

### 27.10.6 Missing `uninit`

A local dynamic array's runtime destructor already frees its buffer when
the array goes out of scope. Missing an explicit `uninit()` is therefore
a **warning**, not a memory leak error:

```text
Dynamic array 'd' is still initialized at return from function 'f'
(missing explicit uninit)
```

The warning is suppressed when the array escaped through a global,
aggregate store, or owner-pointer call, because the compiler can no
longer prove the state.

### 27.10.7 Limitations

The check does not detect:

- lifecycle misuse of struct-field or nested-element arrays;
- uninitialized reads performed through `mview`/`view` aliases;
- callee effects precisely (owner-pointer calls become `MAYBE_INITED`);
- allocation failure of `init`/`init_zero` (the successful path is
  modeled);
- element-pointer invalidation after `resize` or `uninit`;
- loop fixed points (each loop body is analyzed once).
