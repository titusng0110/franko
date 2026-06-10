# 9. Fluid Integer Constants

Integer literals and folded constant integer expressions are **fluid**.

This means their initial semantic type is not always binding. Instead, when a constant is used in a typed context, the compiler checks whether the constant value fits that context.

Example:

```franko
func main() -> int32_t {
    uint8_t x;

    x = 255; // valid
    x = 256; // invalid: 256 does not fit uint8_t

    return 0;
}
```

Fluid constants also participate in function overload resolution. A folded integer constant may match a primitive integer parameter if its `BigInteger` value fits that parameter type.
