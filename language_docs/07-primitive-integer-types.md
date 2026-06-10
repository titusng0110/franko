# 7. Primitive Integer Types

Franko supports fixed-width signed and unsigned integer types.

| Type | Meaning | Range |
|---|---|---|
| `int8_t` | signed 8-bit integer | `-128` to `127` |
| `int16_t` | signed 16-bit integer | `-32768` to `32767` |
| `int32_t` | signed 32-bit integer | `-2147483648` to `2147483647` |
| `int64_t` | signed 64-bit integer | `-9223372036854775808` to `9223372036854775807` |
| `uint8_t` | unsigned 8-bit integer | `0` to `255` |
| `uint16_t` | unsigned 16-bit integer | `0` to `65535` |
| `uint32_t` | unsigned 32-bit integer | `0` to `4294967295` |
| `uint64_t` | unsigned 64-bit integer | `0` to `18446744073709551615` |

## 7.1 Integer Type Aliases

| Alias | Canonical type |
|---|---|
| `int` | `int32_t` |

If the parser defines aliases such as `char`, they should be understood as aliases to canonical primitive types. In the current semantic model, `char` behavior corresponds to `uint8_t`.

## 7.2 Boolean-Like Values

Franko currently does not have a separate `bool` type.

Boolean-like results from comparison and logical operators use `uint8_t`, with:

```text
0 = false
1 = true
```
