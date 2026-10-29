# 22. Array Literals

Franko **array literals** are read-only views of static arrays, exactly
like [string literals](23-string-literals.md) are views of byte arrays.

An array literal is written with square brackets:

```franko
[1, 2, 3]
```

An array literal is an **ordinary expression** whose type is
`view<[T, N]>` (see [Pointers](24-pointers.md)): a read-only
pointer to a static array of `N` elements, where `N` is the number of
elements written and `T` is the **inferred element type**.

```franko
[1, 2, 3]        // view<[int32, 3]> when T is inferred as int32
[0, 255]         // view<[uint8, 2]> when T is inferred as uint8
```

## 22.1 Element Type Inference

The element type `T` is always an integer primitive type. It is
**inferred from the surrounding code** — a literal carries no element
type of its own:

- **assignment**: the target's element type (a `view<[T, ...]>`
  variable, or an array target whose element type guides the inference
  before the assignment is rejected — see 22.5);
- **returns**: the function's return type;
- **call arguments**: the parameter type (function, method, or array
  intrinsic parameters such as `memcpy`'s source).

Outside these contexts a literal has nothing to infer from and is
rejected:

```franko
[1, 2, 3];              // invalid: bare expression statement
take([1, 2, 3]);        // valid when take's parameter is
                        // view<[int32]> (or view<[T, ...]>)
```

Constants in a literal are fluid like everywhere else: each element is
checked against the inferred `T`'s range.

```franko
view<[uint8, 3]> b = [0, 127, 256];   // invalid: 256 does not fit
```

## 22.2 Constant Elements Only

Every element must be a **compile-time constant expression** (integer
literals and operators over them). A literal is hoisted to a constant
global array — one per distinct content and element type, shared by all
uses, so returning a literal or calling a function that returns one is
always safe, including recursively:

```franko
func first() -> view<[int32, 3]> {
    return [1, 2, 3];       // backed by one constant global
}

view<[int32, 3]> a = [1 + 1, 2 * 3, 1 << 3];   // constant expressions
```

Runtime values cannot appear in a literal:

```franko
int32 v = 5;
view<[int32, 3]> t = [v, v + 1, 2];   // invalid: v is not constant
```

Compute runtime values into a declared array with ordinary element
assignments, or copy a constant literal and modify it.

## 22.3 Length and Type

A literal has exactly as many elements as written: `[1, 2, 3]` is
`view<[int32, 3]>`, never a shorter or longer array. An empty
literal `[]` is not supported (like `""`, `N >= 1`).

```franko
view<[int32, 2]> v = [1, 2, 3];   // invalid: lengths 2 and 3 differ
view<[int32, 3]> v = [1, 2, 3];   // valid
```

Because the literal is a pointer, elements are reached through a
dereference of a view variable:

```franko
view<[int32, 3]> v = [1, 2, 3];
print($(v)[0]);            // 1
```

Indexing a literal directly is not supported: `[1, 2, 3][0]` is
rejected — and a bare literal under `$` has no inference context
either. Assign the literal to a `view` variable first and index
through that.

## 22.4 Nested Array Literals

Nested literals are static at **every** level and therefore must be
**perfectly rectangular**: all rows at each level have the same length.

```franko
view<[[int32, 2], 2]> m = [[1, 2], [3, 4]];   // valid
view<[[int32, 2], 2]> m = [[1, 2], [3]];      // invalid: jagged
```

The type is `view<[[T, M], N]>`: `N` rows of `M` elements.
Each row is itself a literal of type `view<[T, M]>` when separated,
so a row length mismatch is a type error.

When the literal decays to a dynamic view (22.6), only the outermost
dimension becomes dynamic; inner dimensions stay static:

```franko
func solve(view<[[int32, 13]]> maze) -> void {
    // rows are exactly 13 cells wide: the width is enforced by the type
}

solve([[1, 2, 3]]);            // invalid: rows are 3 wide, not 13
solve(grid);                   // valid for a view<[[int32, 13], 12]>
```

This one-way decay is how a function enforces a required row width or
maze size: a caller can decay *down* to `view<[[T, M]]>`, but
nothing with a different `M` can ever reach it.

## 22.5 Arrays Are Not Assignable From Literals

Arrays are value types; an array literal is a view. A literal cannot
initialize or assign an array — copy the elements with `memcpy`
instead, exactly as with string literals:

```franko
[int32, 3] buf;
buf.memcpy([1, 2, 3]);       // copies 3 elements
[int32, 4] xs;
xs.memset(0);
xs.memcpy([7, 8]);           // copies 2 elements; xs[2], xs[3] untouched
```

For dynamic arrays, size the storage first:

```franko
[int32] dyn;
dyn(3);
dyn.memcpy([1, 2, 3]);
```

A shorter literal copies a prefix; `memcpy` copies
`min(source, target)` elements at runtime.

## 22.6 Views of Literals

The literal's own type is `view<[T, N]>`. The ordinary static→
dynamic view decay (see [Pointers](24-pointers.md) 24.4) applies, so
it is assignable wherever a `view<[T]>` is expected:

```franko
func take(view<[int32]> v) -> int32 {
    return $(v)[0];
}

take([1, 2, 3]);                        // decays to view<[int32]>
view<[int32]> d = [1, 2, 3, 4];  // decay in a declaration
```

When the decay applies directly to a literal in one of these positions,
the converted view is itself a hoisted constant global (a
dynamic-array header over the literal's constant storage), not a
statement-scoped temporary. Returning or keeping such a view is therefore
always safe — including from recursive functions — unlike a decay of an
ordinary local, which must not outlive its scope (see
[Pointers](24-pointers.md) 24.4.1).

Permissions never upgrade: a literal (read-only) cannot initialize an
`mview` or `ptr` variable.

## 22.7 `?` Size Inference

A static size may be written `?` in a `view<[...]>` declaration
with an array-literal initializer; the size is the literal's length at
that level:

```franko
view<[int32, ?]> v = [1, 2, 3];              // view<[int32, 3]>
view<[[int32, ?], ?]> m = [[1, 2], [3, 4]];
```

The rules are the same as for other `?` inference (see
[Desugaring](05-desugaring.md)): `?` is only allowed in a local
declaration with an array-literal initializer, the array nest must be
static at every level, and the fit must be exact when `?` is used.

## 22.8 Global Literals

A pointer-typed global may be initialized with an array literal of
constant elements, like a string literal:

```franko
view<[int32, 4]> G = [1, 2, 3, 4];

func main() -> int32 {
    print($(G)[3]);         // 4
    return 0;
}
```

Array-typed globals are not assignable from literals (22.5); use
element assignments in an initializer function.

## 22.9 Summary

Array literals in Franko are:

- ordinary expressions of type `view<[T, N]>`;
- element type `T` inferred from assignment, return, or call-argument
  contexts (integer primitives only);
- compile-time constant expressions only, hoisted to shared constant
  global arrays (recursion-safe, like string literals);
- exactly as long as written (`N >= 1`);
- rectangular when nested, with inner dimensions static;
- decayable to `view<[T]>` (outermost dimension only);
- never assignable to arrays — copy with `memcpy`;
- inferrable in size via `?` in `view` declarations.
