# franko/error

`franko/error` provides process-fatal panics. The only function is
`std::panic(reason)`, which writes `reason` as a Franko runtime panic
message and aborts the process.

```franko
import "franko/error"
```

Module files:

```text
import/franko/error.fr          // Franko declaration (stub body)
import/franko/error.module.json // module descriptor and bridge file
import/franko/error.ffi.cpp     // C++ bridge definition spliced into the build
```

The module is an FFI module: when `std::panic` is used, the compiler
replaces the stub with the bridge from `error.ffi.cpp` (see
[Foreign Function Interface](../language_docs/33-foreign-function-interface.md)).

Design rules:

- `std::panic` never returns; it calls `franko_panic`, which prints
  `Franko runtime panic: <reason>` to stderr and aborts.
- The reason is a `view<[byte]>`: string literals, byte arrays, and byte
  views all work. It does not need to be NUL-terminated.
- The bridge copies the reason into a fixed 512-byte buffer, so reasons
  longer than 511 bytes are truncated. No allocation is performed on the
  panic path.
- A null view, or a view of an uninitialized dynamic array, still aborts,
  using the fixed message `std::panic: null reason`.
- The stub body is never emitted when the bridge is active. Merely
  importing the module adds no C++ unless `std::panic` is used.

## API

```franko
namespace std {
    func panic(view<[byte]> reason) -> void
}
```

### std::panic

```franko
func panic(view<[byte]> reason) -> void
```

Copies `reason` into a NUL-terminated buffer, prints it through
`franko_panic`, and aborts the process. There is no return value and no
recovery.

```franko
import "franko/error"

func main() -> int32 {
    int32 status = 1
    if (status != 0) {
        std::panic("fatal: unexpected status")
    }
    return 0
}
```

The program writes:

```text
Franko runtime panic: fatal: unexpected status
```

and exits non-zero (SIGABRT on POSIX).

A dynamic reason exercises the same path:

```franko
import "franko/error"

func main() -> int32 {
    [byte] reason
    if (reason.init(8) != 0) {
        return 1
    }
    reason.memcpy("out of memory")
    std::panic(getview(reason))
    reason.uninit()
    return 0
}
```

A null reason panics with the bridge's fixed message:

```franko
view<[byte]> reason = null
std::panic(reason)   // Franko runtime panic: std::panic: null reason
```

## Tests

```text
library_tests/error/panic/runtime/string_reason/  // static string-literal reason
library_tests/error/panic/runtime/dynamic_reason/ // dynamic byte-view reason
library_tests/error/panic/runtime/null_reason/    // null reason
```

The cases are in the `runtime` category: they must compile and exit
non-zero when run. Panic text is intentionally not pinned.
