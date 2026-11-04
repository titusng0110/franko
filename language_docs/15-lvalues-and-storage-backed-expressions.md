# 15. Lvalues and Storage-Backed Expressions

A storage-backed lvalue is an expression that refers to a real mutable storage location.

Current storage-backed lvalues are:

| Expression | Lvalue? |
|---|---|
| variable | yes |
| array element of storage-backed array | yes |
| `$(pointer)` | yes |
| struct field access whose base is an lvalue (`obj.field`) | yes |
| tuple element access whose base is an lvalue (`t.val`, `t._0`) | yes |
| integer literal | no |
| arithmetic expression | no |
| comparison expression | no |
| function call result | no |
| `getptr(...)` result | no |

Struct field lvalue-ness follows the base expression: `s.field` is an lvalue when `s` is a variable, a `$(...)`, a nested member access, or an array element of a storage-backed array (static or dynamic). Field access on a function call result is not storage-backed.

Tuple element access follows exactly the same rule: `t.val` and `t._0` are lvalues when `t` is storage-backed, and tuple elements can be assigned, passed to `getptr`/`getview`/`getmview`, and nested in longer access chains.

See [Structs](25-structs.md) for the complete struct reference and [Tuples](06-types.md#61-tuples) for tuple types.

This distinction matters for:

- assignment targets;
- compound assignment targets and `x++` / `x--` targets;
- `getptr(...)` operands;
- mutating array intrinsic receivers, including a `memcpy` destination.

Read-only sources (`memcpy`/`memcmp`/`memchr`/`memmem` sources) only need
to be pointer expressions; they do not need to be storage-backed
lvalues.
