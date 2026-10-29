# 7. Primitive Integer Types

Franko supports fixed-width signed and unsigned integer types.

| Type | Meaning | Range |
|---|---|---|
| `int8` | signed 8-bit integer | `-128` to `127` |
| `int16` | signed 16-bit integer | `-32768` to `32767` |
| `int32` | signed 32-bit integer | `-2147483648` to `2147483647` |
| `int64` | signed 64-bit integer | `-9223372036854775808` to `9223372036854775807` |
| `uint8` | unsigned 8-bit integer | `0` to `255` |
| `uint16` | unsigned 16-bit integer | `0` to `65535` |
| `uint32` | unsigned 32-bit integer | `0` to `4294967295` |
| `uint64` | unsigned 64-bit integer | `0` to `18446744073709551615` |

## 7.1 Integer Type Aliases

| Alias | Canonical type |
|---|---|
| `int` | `int32` |
| `uint` | `uint32` |
| `byte` | `uint8` |

No other aliases (such as `char` or `bool`) are currently defined.

## 7.2 Boolean-Like Values

Franko currently does not have a separate `bool` type.

Boolean-like results from comparison and logical operators use `uint8`, with:

```text
0 = false
1 = true
```