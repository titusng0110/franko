# 7. Fluid Integer Constants

Integer literals and folded constant integer expressions have **no type of
their own**. A constant is typed by the context in which it is used.

Example:

```franko
func main() -> int32 {
    uint8 x;

    x = 255; // valid: 255 is inferred as uint8
    x = 256; // invalid: 256 does not fit uint8

    return 0;
}
```

A constant infers its type from:

- an assignment target;
- a declaration initializer;
- a function or method return type;
- a call parameter;
- an aggregate literal field or tuple element;
- an array-literal element type;
- an integer cast target.

When no context supplies a type, the constant receives the smallest signed
primitive that holds its value:

```text
0 .. 127              -> int8
128 .. 32767          -> int16
32768 .. 2147483647   -> int32
...                   -> int64
```

A nonnegative value above the `int64` maximum uses `uint64`. A value that fits
no primitive integer type is a compile error.

Compile-time intrinsic results are already typed: predicates and `#IS_*`
constants are `uint8`, and `#strcmp` is `int32`. These typed results keep
their declared result type rather than becoming context-inferred literals.

## 7.1 Overload Resolution

A constant may match any primitive integer parameter whose type can represent
its value. When several overloads fit, the narrowest fitting parameter type
wins, with signed preferred over unsigned at equal width.

```franko
func g(int16 x) -> int32 { return 1 }
func g(uint16 y) -> int32 { return 2 }

g(100);   // selects g(int16): same width, signed wins
```

An explicitly typed constant first prefers an exact parameter type when one
exists. Otherwise it uses the same narrowest-fit rule.

A call is ambiguous only when several overloads tie on both
permission-downgrade and constant-fit costs.

## 7.2 Casts

The target of an integer cast is an inference context:

```franko
uint8 a = cast<uint8>(200);   // valid: 200 is inferred as uint8
uint8 b = cast<uint8>(300);   // invalid: 300 does not fit uint8
```

`unsafe_cast<T>` also checks the constant value at compile time and rejects a
value outside `T`'s range. A checked cast whose constant value fits succeeds,
just like an ordinary cast.
