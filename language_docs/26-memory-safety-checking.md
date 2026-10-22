# 26. Memory Safety Checking

After legality checking, the compiler runs a dedicated **memory safety pass** (`MemorySafetyChecker`) over heap-owned variables.

This pass owns transient memory-state analysis. It checks, per heap variable:

- `del` of non-heap variables;
- double delete;
- use after delete;
- leaks at the end of a scope;
- leaks at return points;
- deletion state merged across branch paths.

The pass is **intra-procedural**: each function body is analyzed separately with a fresh memory state. Called functions are not analyzed for their effects on the caller's heap variables.

## 26.1 Memory States

For each heap variable the checker tracks one of:

| State | Meaning |
|---|---|
| alive | allocated and not deleted on the current path |
| deleted | definitely deleted on the current path |
| maybe-deleted | deleted on some continuing paths and alive on others |
| untracked | internal; variable is registered but not currently live in this traversal |

`maybe-deleted` arises when branch paths merge with different deletion outcomes. Uses and deletes of a `maybe-deleted` variable are invalid because one path may be a use-after-free or double-free.

## 26.2 Delete Rules

- The `del` operand must be a heap variable. Deleting a non-heap variable is an error.
- Deleting an already-deleted variable is an error.
- Deleting a possibly-deleted variable after a branch merge is an error.

## 26.3 Use Rules

- Using a deleted variable is an error.
- Using a possibly-deleted variable after a branch merge is an error.

## 26.4 Leak Rules

- A heap variable must be deleted before its **scope** exits normally; otherwise it leaks ("missing del").
- A heap variable must be deleted before **returning** from the enclosing function; live heap variables at a return statement are reported as leaks.

`alloc` is invalid at global scope, so every heap variable is local to a function or block and is covered by the scope and return leak rules above.

For heap dynamic arrays, remember that `uninit()` only frees the array's internal buffer. The heap array object itself (`alloc array<T>`) must still be freed with `del`.

## 26.5 Branch Handling

`if` / `else` is analyzed by checking each branch from the pre-branch state and merging the states of the continuing paths:

```franko
if (cond != 0) {
    del x;
}
print(x);      // error: x may have already been deleted
```

A branch that **definitely returns** does not contribute its deletion state to the code following the `if`, because that path never continues there:

```franko
if (cond != 0) {
    del x;
    return 0;      // x deleted, but this path exits
}
print(1);          // fine
del x;             // fine
```

Branch-sensitive analysis is limited to a maximum nesting depth (8). Beyond that depth the checker falls back to a conservative path-insensitive merge.

## 26.6 Loop Handling

`while` loops are handled with a bounded abstraction:

- the body is checked once for internal memory errors;
- the body is checked a second time starting from the post-body state, which catches simple loop double-frees such as deleting the same variable on every iteration;
- the state after the loop merges the "zero iterations" and "one iteration" states, since a loop may execute zero times.

Loop termination is not proven; the post-loop state is conservative.

## 26.7 Limitations

Current limitations of the memory safety checker:

- checking is **symbol-based**; it does not perform alias analysis, so it does not detect use-after-free through a stored address:

```franko
alloc int x;
addr<int> p;
p = getaddr(x);
del x;
print(deref(p));   // NOT detected by the current checker
```

- checks are intra-procedural; heap effects of called functions are not analyzed;
- branch-path sensitivity is capped by the nesting-depth limit described above;
- it does not model partial heap-layout ownership beyond the single-object `alloc` model.

## 26.8 Structs and Nested Arrays

Memory safety tracking is keyed on heap variables, so a heap struct behaves like any other heap variable:

```franko
alloc X s;
del s;    // checked like any other del
```

Notes on structs with nested dynamic-array fields:

- nested dynamic-array fields are **not** tracked as separate heap objects;
- when a heap struct variable is deleted (`del s`), the generated C++ runs the struct's destructor, which destroys the member arrays, so `alloc X s; del s;` does not leak the inner arrays;
- dynamic array **elements** are not destructed: `uninit()` frees the element storage without running element destructors, so inner dynamic arrays inside struct elements are not freed automatically. Free inner arrays explicitly (per element) before `uninit()`, or store only primitive and address fields in structs used as dynamic array elements.

Struct semantics are described in [Structs](24-structs.md).

See also [`del` and Delete Checking](25-del-and-delete-checking.md) and [Heap Variables and `alloc`](13-heap-variables-and-alloc.md).