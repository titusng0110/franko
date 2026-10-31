# 27. Memory Safety Checking

After legality checking, the compiler runs two dedicated pointer passes:
the **pointer null-state pass** (`PointerNullStateChecker`) and the
**dangling-pointer pass** (`DanglingPointerChecker`). Both cover all three
pointer types (`ptr`, `mview`, and `view`).

The null-state pass tracks one fact per pointer variable: whether it is
definitely null, definitely pointing, or possibly null on the current
execution path. The dangling-pointer pass tracks where a returned
pointer's storage lives (see
[Dangling-Pointer Checking](#279-dangling-pointer-checking)).

The null-state pass checks:

- dereferencing a null pointer (an error);
- dereferencing a possibly-null pointer (a warning);
- that `null` is only used for pointer types (see [Types](06-types.md)).

The pass is **intra-procedural**: each function body is analyzed separately with a fresh state. Called functions are not analyzed for their effects on the caller's pointers, and no facts cross function boundaries (interprocedural nullability is deliberately not attempted).

## 27.1 Pointer States

For each pointer variable the checker tracks one of:

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
- at function entry, every tracked pointer variable is maybe-null, because parameters and globals carry values the checker cannot see. Exceptions: a method's `self` parameter enters pointing, since the call synthesizes `getptr`/`getmview`/`getview(receiver)` over a storage-backed lvalue; and the global args-form `main`'s `view<[[byte]]>` parameter enters pointing, since the template wrapper always passes a live pointer.

## 27.3 Dereference Checking

- `$(p)` where `p` is **null** is an **error**;
- `$(p)` where `p` is **possibly null** is a **warning**.

A variable warns at most once per check, so a path-sensitive traversal that revisits the same deref site does not repeat the warning.

## 27.4 Branch Handling

`if` / `else` is analyzed by checking each branch from the pre-branch state and merging the states of the continuing paths.

A branch that **definitely returns** does not contribute its state to the code following the `if`, because that path never continues there.

Branch-sensitive analysis is limited to a maximum nesting depth (10). Beyond that depth the checker falls back to a conservative path-insensitive merge.

## 27.5 Loop Handling

`while` loops are handled with a bounded abstraction:

- the body is checked once;
- the state after the loop merges the "zero iterations" and "one iteration" states, since a loop may execute zero times.

Loop termination is not proven; the post-loop state is conservative.

`for` loops are desugared to blocks and `while` loops before this pass runs, so they receive the same treatment. See [Desugaring](05-desugaring.md).

## 27.6 Guard Refinement

Inside `if (p == null)` / `if (p != null)` (either operand order), the checker refines `p`'s state per path: the then-branch of `p != null` sees `p` as pointing, and the code after the loop/branch of a failed guard sees `p` as null.

```franko
ptr<int32> p;
if (p != null) {
    $(p);      // valid: p is refined to pointing inside the guard
}
$(p);          // error: p is provably null after the guard fails
```

When a guard's assertion contradicts a definite current state, that path is statically infeasible; the checker suppresses dereference diagnostics inside it and does not let its state flow onward.

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

Current limitations of the pointer null-state checker:

- values of unknown provenance (call results, parameters, field and element reads) are treated as maybe-null: dereferencing one is a warning, not an error, and a null value may still flow through them;
- only plain pointer **variables** are tracked. A pointer held in a struct field, an array element, or behind a `$(...)` chain is not tracked;
- dangling pointers are only detected for values escaping a function by `return`; a pointer may still outlive its pointee through calls, globals, aggregates, or block exits (see [Dangling-Pointer Checking](#279-dangling-pointer-checking));
- checks are intra-procedural; pointer effects of called functions are not analyzed;
- branch-path sensitivity is capped by the nesting-depth limit described above.

## 27.8 Capability Linting

A third, independent pass (`PointerPermissionChecker`) warns when a
`ptr`/`mview` local variable or parameter requests permissions it never
exercises, so developers default to the tightest pointer that fits. See
the "Capability Linter" section of [Pointers](24-pointers.md).

## 27.9 Dangling-Pointer Checking

`DanglingPointerChecker` runs after branch/declaration pruning and after
the null-state pass. It is **return-only** and **intra-procedural**: each
function or method body is analyzed separately, and only `return`
expressions are classified. Dead functions and pruned branches therefore
cannot produce diagnostics.

### 27.9.1 Tracked Origins

For every pointer-typed variable (local, parameter, or global), the pass
tracks an immutable set of possible storage origins:

| Origin | Meaning |
|---|---|
| frame | the pointer may refer to a local variable's or parameter's current-frame slot |
| global | the pointer may refer to a global variable's storage |
| decay | the pointer points at a hidden static-to-dynamic descriptor in the current frame |
| unknown | caller-owned or untracked: parameters, call results, `null`, pointer-typed field/element reads |

Origins flow through assignments and pointer copies, through `$(...)`
aliases when the dereferenced pointer targets a tracked pointer variable,
and through `getptr`/`getmview`/`getview` of lvalues:

```franko
int32 x
ptr<int32> p = getptr(x)       // p -> frame(x)
ptr<int32> q = p                // q -> frame(x)
return q                        // error: x dies with the frame
```

Taking the address of a pointer parameter or dereferencing a pointer
parameter is caller-owned and safe to return:

```franko
func ok(ptr<int32> p) -> ptr<int32> {
    return getptr($(p));       // points at the caller's storage
}
```

At function entry every tracked variable starts as `unknown`, so pointer
parameters and globals are never assumed to point into the current frame.

### 27.9.2 The Return Rule

At a pointer-typed `return`, the pass classifies the value's origins:

- any decay origin → **error** (the descriptor is always stack-local);
- frame origins with no `unknown` and no `global` alternative →
  **error** (definitely dangling);
- frame origins mixed with caller-owned/global alternatives → **warning**
  (possibly dangling after a branch merge).

The diagnostic names the local variable or parameter:

```text
Returning a pointer to local variable 'x' from function 'bad' (dangling after return)
Returning a pointer to parameter 'x' from function 'bad' (dangling after return)
Returning a pointer that may refer to local variable 'x' from function 'maybe' (possibly dangling after return)
```

### 27.9.3 Decayed Views

A static-to-dynamic array view decay synthesizes a hidden
`Franko_Dynamic_Array` descriptor in the current stack frame. Returning a
non-literal decayed view is always an error, even when the array data is
global or the decay comes from a branch merge:

```franko
func bad(view<[int32, 4]> m) -> view<[int32]> {
    return m;                    // error: descriptor dies with 'bad'
}
```

The direct form (the decay happens at the `return` or in a top-level
`cast`) gets a detailed message explaining the descriptor lifetime and
two fixes:

- take `view<[T]>` as the parameter and let the caller decay at the call
  site, then consume the view without returning it;
- return `view<[T,N]>` and let the caller perform the decay on the result.

A decay already carried by a variable or copy gets a shorter message:

```franko
func bad(view<[int32, 4]> m) -> view<[int32]> {
    view<[int32]> d = m;
    return d;                    // error: 'd' holds a decayed view
}
```

Literal-backed views are safe: an array or string literal that decays
immediately points at a hoisted constant global descriptor, not at a
stack temporary. Existing global dynamic views are safe for the same
reason. Global initializers are never analyzed as function bodies.

### 27.9.4 Limitations

The pass intentionally does not detect:

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
