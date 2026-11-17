# franko/bit

Scalar bit operations backed by compiler intrinsics.

```franko
import "franko/bit"
```

Module files:

```text
import/franko/bit.fr
import/franko/bit.module.json
import/franko/bit.ffi.cpp
```

## API

| Function | Arguments | Returns |
|---|---|---|
| `bit::ctz32` | `(uint32 value)` | `uint32` |
| `bit::ctz64` | `(uint64 value)` | `uint32` |
| `bit::clz32` | `(uint32 value)` | `uint32` |
| `bit::clz64` | `(uint64 value)` | `uint32` |
| `bit::popcount32` | `(uint32 value)` | `uint32` |
| `bit::popcount64` | `(uint64 value)` | `uint32` |
| `bit::bswap32` | `(uint32 value)` | `uint32` |
| `bit::bswap64` | `(uint64 value)` | `uint64` |

`ctz` counts trailing zero bits, `clz` counts leading zero bits, and
`popcount` counts set bits. `bswap` reverses the byte order.

Zero is defined for every count operation:

- `bit::ctz32(0)` is `32`
- `bit::ctz64(0)` is `64`
- `bit::clz32(0)` is `32`
- `bit::clz64(0)` is `64`

## Example

```franko
import "franko/stdio"
import "franko/bit"

func main() -> int32 {
    stdout(bit::ctz32(8))       // 3
    stdout(bit::clz64(1))       // 63
    stdout(bit::popcount32(0xF0F0))  // 8
    stdout(bit::bswap32(0x11223344)) // 1144201745
    return 0
}
```
