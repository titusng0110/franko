# 7. Fluid Integer Constants

Integer literals and folded constant integer expressions are **fluid**.

This means their initial type is not always binding. Instead, when a constant is used in a typed context, the compiler checks whether the constant value fits that context.

Example:

```franko
func main() -> int32 {
    uint8 x;

    x = 255; // valid
    x = 256; // invalid: 256 does not fit uint8

    return 0;
}
```

Fluid constants also participate in function overload resolution. A folded integer constant may match a primitive integer parameter if its arbitrary-precision value fits that parameter type.

When a constant fits several overloads, the call is resolved by a constant-fit preference, not rejected: the overload whose parameter type equals the constant's own type wins when the value fits there; otherwise the narrowest fitting type wins, with signed preferred over unsigned at equal width. For example, `stdout(1)` selects `stdout(int32)` (a literal's own type), `stdout(5000000000)` selects `stdout(int64)`, and `stdout(100)` selects `stdout(int16)` over `stdout(uint16)` when `int32` is not among the overloads. A call is ambiguous only if several overloads tie on both permission-downgrade and constant-fit costs.
