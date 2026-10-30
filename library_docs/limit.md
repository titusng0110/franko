# franko/limit

Integer type limits: one global per bound per primitive integer type,
named after the C conventions (`INT32_MAX`, `UINT8_MIN`, ...).

```franko
import "franko/limit"
```

Module files:

```text
import/franko/limit.fr    // the constants, wrapped in namespace std
```

## API

```franko
namespace std {
    int8 INT8_MIN = -128
    int8 INT8_MAX = 127

    int16 INT16_MIN = -32768
    int16 INT16_MAX = 32767

    int32 INT32_MIN = -2147483648
    int32 INT32_MAX = 2147483647

    int64 INT64_MIN = -9223372036854775808
    int64 INT64_MAX = 9223372036854775807

    uint8 UINT8_MIN = 0
    uint8 UINT8_MAX = 255

    uint16 UINT16_MIN = 0
    uint16 UINT16_MAX = 65535

    uint32 UINT32_MIN = 0
    uint32 UINT32_MAX = 4294967295

    uint64 UINT64_MIN = 0
    uint64 UINT64_MAX = 18446744073709551615
}
```

Reference them with `::`, like every other std module:

```franko
import "franko/limit"

func main() -> int32 {
    int64 n = 0;
    if (n == std::INT64_MIN) {
        print("bottom");
    }
    uint32 cap = std::UINT32_MAX;
    return 0;
}
```

## Notes

The language has no `const` yet, so these are **ordinary global
variables** with compile-time constant initializers, not literal
constants:

- Treat them as read-only: nothing stops an assignment, but don't.
- Each one has exactly the type of its bound (`INT8_MIN` is `int8`,
  `UINT64_MAX` is `uint64`), so no implicit width conversions apply:
  moving a value across widths works like for any variable, with
  `cast<T>` / `unsafe_cast<T>`.
- They do not get the fluid-constant fit rules: `byte b = std::UINT8_MAX`
  is valid because both sides are `uint8`, while `uint16 x = std::UINT8_MAX`
  is rejected like any `uint8`-to-`uint16` assignment.
- They cannot appear in constant-expression slots (static array sizes,
  struct field defaults, `break N` levels): those accept literals and
  operators only.
- The aliases `int`, `uint`, and `byte` are `int32`, `uint32`, and
  `uint8`, so those limits are `INT32_*`, `UINT32_*`, and `UINT8_*`.
