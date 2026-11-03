# franko/static

The `franko/static` module exposes Franko's compile-time reflection
surface: target constants, `static_if` branch selection, and type
introspection. It is built into the compiler; there is no file on disk.

```franko
import "franko/static"
```

Importing it declares the `static` namespace. Without the import,
`static::` names are unknown and the compiler reports an explicit hint
to import `franko/static`.

## Target constants

```franko
byte IS_LINUX   // 1 when targeting linux, else 0
byte IS_WINDOWS // 1 when targeting windows, else 0
byte IS_MACOS   // 1 when targeting macos, else 0
byte IS_X86_64  // 1 when targeting x86_64, else 0
byte IS_ARM64   // 1 when targeting arm64, else 0
```

The values come from the required `--target <os>-<arch>` compiler flag.
They are compile-time constants: references fold to integer literals
and cannot be assigned to.

```franko
import "franko/static"
import "franko/io"

func main() -> int32 {
    print(static::IS_LINUX + static::IS_WINDOWS + static::IS_MACOS);
    print(static::IS_X86_64 + static::IS_ARM64);
    return 0;
}
```

Each group sums to `1` on every target.

## `static_if`

`static_if` selects a branch at compile time. Only the selected branch
is analyzed, so the other branch may name symbols that do not exist on
the current target.

```franko
import "franko/static"
import "franko/io"

func main() -> int32 {
    static_if (static::IS_WINDOWS) {
        print("windows");
    } static_else static_if (static::IS_LINUX) {
        print("linux");
    } static_else {
        print("other");
    }
    return 0;
}
```

The condition must be a compile-time constant integer. See
[Compile-Time Reflection](../language_docs/34-compile-time-reflection.md)
for the full syntax and dead-branch rules.

## Type introspection

Each function below takes a bare variable identifier (a local,
parameter, `self`, or qualified global) and returns a compile-time
constant.

| Function | Result |
|---|---|
| `static::is_primitive(x)` | `1` for primitive integer types |
| `static::is_memcpy_safe(x)` | `1` when byte-copying is safe (`memcpy`/`memcmp`) |
| `static::is_trivial(x)` | `1` when raw uninitialized storage is safe |
| `static::is_memset_safe(x)` | `1` when `memset` may byte-fill the type |
| `static::is_same_type(x, y)` | `1` for exact structural type equality |
| `static::what_type(x)` | canonical type spelling as a `view<[byte, N]>` |
| `static::is_matchable(x)` | `1` for primitives/pointers, or structs with `OPERATOR_EQ` |
| `static::is_comparable(x)` | `1` for primitives, or structs with `OPERATOR_LT` |
| `static::is_same_type_assignable(x)` | `1` when same-type assignment is legal |
| `static::is_passable_by_value(x)` | `1` when the type can be passed by value to a function |

`is_passable_by_value` is the built-in by-value parameter rule: primitives,
pointers, and structs whose fields are all passable by value. It does not
consider a custom `OPERATOR_MOV`; use `is_same_type_assignable` for that.

`static::strcmp(a, b)` compares two raw string literals bytewise and
returns `int32` (`-1`, `0`, or `1`). `static::panic(message)` aborts
compilation with the raw string literal as the diagnostic.

```franko
import "franko/static"
import "franko/io"

struct Point {
    int32 x
    int32 y

    func OPERATOR_EQ(view<Point> self, view<Point> other) -> uint8 {
        return $(self).x == $(other).x && $(self).y == $(other).y
    }
}

func main() -> int32 {
    int32 i = 1;
    Point p;

    print(static::is_primitive(i));            // 1
    print(static::is_matchable(p));            // 1
    print(static::what_type(p));               // Point
    print(static::strcmp("abc", "abd"));     // -1
    return 0;
}
```

`what_type` produces a real read-only byte view backed by immutable
static storage, so it can be printed or assigned but not passed to
`strcmp` or `panic`.

## FFI target selection

```franko
import "franko/static"
import "franko/os"

func main() -> int32 {
    [byte] msg;
    msg.init(5);
    msg.memcpy("hello");

    static_if (static::IS_WINDOWS) {
        uint64 h = os::win32::GetStdHandle(os::win32::STD_OUTPUT_HANDLE);
        os::win32::WriteFile(h, getview(msg), 5, null);
    } static_else static_if (static::IS_LINUX || static::IS_MACOS) {
        os::posix::write(os::posix::STDOUT_FILENO, getview(msg), 5);
    }

    msg.uninit();
    return 0;
}
```

Only the selected platform's FFI functions are verified and bridged.
