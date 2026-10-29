# 9. Fluid Integer Constants

Integer literals and folded constant integer expressions are **fluid**.

This means their initial semantic type is not always binding. Instead, when a constant is used in a typed context, the compiler checks whether the constant value fits that context.

Example:

```franko
func main() -> int32 {
    uint8 x;

    x = 255; // valid
    x = 256; // invalid: 256 does not fit uint8

    return 0;
}
```

Fluid constants also participate in function overload resolution. A folded integer constant may match a primitive integer parameter if its `BigInteger` value fits that parameter type.

When a constant fits several overloads, the call is resolved by a constant-fit preference, not rejected: the overload whose parameter type equals the constant's own type wins when the value fits there; otherwise the narrowest fitting type wins, with signed preferred over unsigned at equal width. For example, `print(1)` selects `print(int32)` (a literal's own type), `print(5000000000)` selects `print(int64)`, and `print(100)` selects `print(int16)` over `print(uint16)` when `int32` is not among the overloads. A call is ambiguous only if several overloads tie on both permission-downgrade and constant-fit costs.
