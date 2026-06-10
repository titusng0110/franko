# 15. Lvalues and Storage-Backed Expressions

A storage-backed lvalue is an expression that refers to a real mutable storage location.

Current storage-backed lvalues are:

| Expression | Lvalue? |
|---|---|
| variable | yes |
| array element of storage-backed array | yes |
| `deref(address)` | yes |
| integer literal | no |
| arithmetic expression | no |
| comparison expression | no |
| function call result | no |
| `getaddr(...)` result | no |

This distinction matters for:

- assignment targets;
- `getaddr(...)` operands;
- array intrinsic receivers;
- `memcpy` sources;
- array initializer assignment targets.
