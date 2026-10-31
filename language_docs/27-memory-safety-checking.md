# 27. Memory Safety Checking

Franko checks two independent properties of pointers: **null state**
and **dangling storage**. Both checks cover all three pointer types
(`ptr`, `mview`, and `view`).

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

- an ordinary `ptr<T>` declaration is **null-initialized**, so it starts in the null state;
- assigning the `null` literal makes the target null;
- assigning `getptr(...)`, `getmview(...)`, or `getview(...)` makes the target pointing; these are the **only** certifications of a non-null pointer;
- assigning another pointer variable copies that variable's exact state, so a null state propagates through copies and certification propagates transitively;
- assigning a value of unknown provenance (a call result, a parameter, a struct-field or array-element read) yields a maybe-null state;
- at function entry, every tracked pointer variable is maybe-null, because parameters and globals carry values the compiler cannot see. Exceptions: a method's `self` parameter enters pointing, because the receiver is a storage-backed lvalue; and the global args-form `main`'s `view<[[byte]]>` parameter enters pointing, since the template wrapper always passes a live pointer.

## 27.3 Dereference Checking

- `$(p)` where `p` is **null** is an **error**;
- `$(p)` where `p` is **possibly null** is a **warning**.

A variable warns at most once per check, so a path-sensitive traversal that revisits the same deref site does not repeat the warning.

## 27.4 Branch Handling

`if` / `else` is analyzed by checking each branch from the pre-branch state and merging the states of the continuing paths.

A branch that **definitely returns** does not contribute its state to the code following the `if`, because that path never continues there.

Branch-sensitive analysis is limited to a maximum nesting depth (12). Beyond that depth the compiler falls back to a conservative path-insensitive merge.

## 27.5 Loop Handling

`while` loops are handled with a bounded abstraction:

- the body is checked once;
- the state after the loop merges the "zero iterations" and "one iteration" states, since a loop may execute zero times.

Loop termination is not proven; the post-loop state is conservative.

`for` loops are desugared to blocks and `while` loops before this check runs, so they receive the same treatment. See [Desugaring](05-desugaring.md).

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

A third, independent check warns when a `ptr`/`mview` local variable or
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
12 nested branches, then merged conservatively, and `while` loops merge
the zero-trip, back-edge, and `break` states. `for` loops receive the same
treatment after desugaring.

Null guards refine what is known about the storage too:

- in an `if (p == null)` path, `p` is null, so any recorded local or
  global storage origin for `p` is discarded;
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
