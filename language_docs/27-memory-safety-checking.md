# 27. Memory Safety Checking

After legality checking, the compiler runs a dedicated **address null-state pass** (`AddressNullStateChecker`) over address-typed variables.

The pass tracks one fact per address variable: whether it is definitely null, definitely pointing, or possibly null on the current execution path.

It checks:

- dereferencing a null address (an error);
- dereferencing a possibly-null address (a warning);
- that `null` is only used for address types (see [Types](06-types.md)).

The pass is **intra-procedural**: each function body is analyzed separately with a fresh state. Called functions are not analyzed for their effects on the caller's addresses.

## 27.1 Address States

For each address variable the checker tracks one of:

| State | Meaning |
|---|---|
| pointing | the variable holds a certified, real storage address |
| null | the variable is definitely null on the current path |
| maybe-null | the variable is null on some continuing paths |

## 27.2 Assignment Rules

- an ordinary `addr<T>` declaration is **null-initialized**, so it starts in the null state;
- assigning the `null` literal makes the target null;
- assigning `getaddr(...)` makes the target pointing; this is the **only** certification of a non-null address;
- assigning another address variable copies that variable's exact state, so a null state propagates through copies and certification propagates transitively;
- assigning a value of unknown provenance (a call result, a parameter, a struct-field or array-element read) yields a maybe-null state;
- at function entry, every tracked address variable is maybe-null, because parameters and globals carry values the checker cannot see. Exceptions: a method's `self` parameter enters pointing, since the call synthesizes `getaddr(receiver)` over a storage-backed lvalue; and the global args-form `main`'s `addr<array<array<byte>>>` parameter enters pointing, since the template wrapper always passes a live address.

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
addr<int32_t> p;
if (p != null) {
    $(p);      // valid: p is refined to pointing inside the guard
}
$(p);          // error: p is provably null after the guard fails
```

When a guard's assertion contradicts a definite current state, that path is statically infeasible; the checker suppresses dereference diagnostics inside it and does not let its state flow onward.

```franko
addr<int32_t> p;
p = getaddr(x);
if (p != null) {
    $(p);      // valid
}
$(p);          // valid: p is still pointing; the else path was infeasible
```

Refinement is path-sensitive up to the nesting-depth limit described above; beyond the limit, refinement stops and states merge conservatively.

The same refinement runs for loop conditions, so `while (p != null) { ... }` sees `p` as pointing inside the body and as null after the loop.

## 27.7 Limitations

Current limitations of the address checker:

- values of unknown provenance (call results, parameters, field and element reads) are treated as maybe-null: dereferencing one is a warning, not an error, and a null value may still flow through them;
- only plain address **variables** are tracked. An address held in a struct field, an array element, or behind a `$(...)` chain is not tracked;
- dangling addresses are not detected at compile time; an address may outlive its pointee, and the compiler does not detect that case. A runtime check is planned as a later change;
- checks are intra-procedural; address effects of called functions are not analyzed;
- branch-path sensitivity is capped by the nesting-depth limit described above.
