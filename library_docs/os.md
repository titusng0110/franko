# franko/os

The `franko/os` module is Franko's low-level operating-system interface.
It exposes a small set of OS syscalls as ordinary Franko functions with
FFI bridge definitions (see [Foreign Function Interface](../language_docs/32-foreign-function-interface.md)
in the language reference for the bridge mechanics).

```franko
import "franko/os"
```

Module files:

```text
import/franko/os.fr      // Franko declarations (stub bodies)
import/franko/os.ffi.cpp // C++ bridge definitions spliced into the build
```

Design rules:

- POSIX semantics are the ground truth; the win32 surface mirrors the
  Win32 File API (`fileapi.h`) with POSIX-shaped compromises where noted.
- Only low-level, non-derived primitives are exposed.
- Buffers are always `ptr<array<byte>>` (a pointer to a dynamic byte
  array). The bridge checks `count` against the array length and aborts
  via `franko_panic` when `count` exceeds it, so the FFI cannot corrupt
  memory. A null or uninitialized buffer returns the stub failure value
  instead of crashing.
- Stub bodies in `os.fr` report failure (`-1` for posix, `0` for win32),
  so a missing bridge can never look like silent success.

## os::IS_* target constants

Compile-time constants that name the target the program is compiled
for, so one program can pick its code path per platform:

```franko
byte IS_LINUX = 0     // 1 when targeting linux
byte IS_WINDOWS = 0   // 1 when targeting windows
byte IS_MACOS = 0     // 1 when targeting macos
byte IS_X86_64 = 0    // 1 when targeting x86_64
byte IS_ARM64 = 0     // 1 when targeting arm64
```

The target comes from the compiler's `--target <os>-<arch>` flag
(`linux-x86_64`, `windows-x86_64`, `macos-arm64`, ...) and is
auto-detected from the host when the flag is omitted.

The compiler does two things with them:

- **It rewrites the initializers** above to the target's `0`/`1`
  before semantic analysis, so the emitted C++ globals carry the
  target's values.
- **It folds every reference into an integer literal**, so a use such
  as `os::IS_WINDOWS` behaves exactly like the constant `1` or `0`:
  conditions fold, and assignment or `getptr(...)` on them is
  rejected with the ordinary "not a storage-backed lvalue" error
  rather than needing a special rule.

Because the condition folds, the backend prunes the untaken branch
before emission. That matters for the intrinsics: a win32 call inside
the false branch of a Windows check never reaches code generation, so
its bridge block is not spliced and the program compiles on every
target. Both branches are still fully type-checked and
memory-safety-checked.

```franko
import "franko/os"

func main() -> int32_t {
    array<byte> msg;
    msg.init(5);
    msg = "hello";

    if (os::IS_WINDOWS == 1) {
        uint64_t h = os::win32::GetStdHandle(os::win32::STD_OUTPUT_HANDLE);
        os::win32::WriteFile(h, getptr(msg), 5, null);
    } else {
        os::posix::write(os::posix::STDOUT_FILENO, getptr(msg), 5);
    }

    msg.uninit();
    return 0;
}
```

## os::posix

POSIX file-descriptor I/O, available on the non-Windows targets
(linux, macos): the bridge calls `::read` / `::write` from
`<unistd.h>`, and on Windows the block is a `#error` — there is no
`_read` / `_write` fallback. The Franko compiler rejects a live posix
call when the target is Windows, so guard it with `os::IS_WINDOWS`
(see [os::IS_* target constants](#osis_is-target-constants)) when the
program must compile for both.

```franko
int32_t STDIN_FILENO = 0
int32_t STDOUT_FILENO = 1
int32_t STDERR_FILENO = 2
```

### os::posix::read

```franko
func read(int32_t fd, ptr<array<byte>> buf, uint64_t count) -> int64_t
```

Reads up to `count` bytes from file descriptor `fd` into `buf`.
Returns the number of bytes read, `0` at end of input, or `-1` on
error or on a null/uninitialized buffer.

### os::posix::write

```franko
func write(int32_t fd, ptr<array<byte>> buf, uint64_t count) -> int64_t
```

Writes up to `count` bytes from `buf` to file descriptor `fd`.
Returns the number of bytes written, or `-1` on error or on a
null/uninitialized buffer. A successful write may write fewer bytes
than requested.

Example:

```franko
import "franko/os"

func main() -> int32_t {
    array<byte> msg;
    msg.init(5);
    msg = "hello";
    int64_t n = os::posix::write(os::posix::STDOUT_FILENO, getptr(msg), 5);
    print(n);
    msg.uninit();
    return 0;
}
```

Note: raw fd writes bypass the `std::cout` buffer that `print` uses,
so interleaving several raw writes with `print` calls orders the bytes
surprisingly (the same is true in C). Prefer a single write per
output section in golden-tested programs.

## os::win32

Win32 File API I/O. Windows-only: calling any of these functions in
code that is actually reachable when compiling for another target
fails at Franko-compile time with the target named in the diagnostic.
A call inside the dead side of a target check (see
[os::IS_* target constants](#osis_is-target-constants) above) is pruned
before that check runs, so portable programs still compile anywhere;
the `#error` bodies in `os.ffi.cpp` remain as a second line of
defense. `lpOverlapped` is dropped throughout: the bridge always
passes `NULL` (synchronous I/O).

```franko
uint32_t STD_INPUT_HANDLE = 0xFFFFFFF6
uint32_t STD_OUTPUT_HANDLE = 0xFFFFFFF5
uint32_t STD_ERROR_HANDLE = 0xFFFFFFF4
```

### os::win32::GetStdHandle

```franko
func GetStdHandle(uint32_t nStdHandle) -> uint64_t
```

Returns the `HANDLE` for a standard device as a 64-bit value, for use
as the `hFile` argument below. Pass one of the `STD_*_HANDLE`
constants. Returns `0` (`NULL`) or `0xFFFFFFFFFFFFFFFF`
(`INVALID_HANDLE_VALUE`) on failure.

### os::win32::ReadFile

```franko
func ReadFile(uint64_t hFile, ptr<array<byte>> buf, uint32_t count, ptr<uint32_t> bytesRead) -> int32_t
```

Reads up to `count` bytes into `buf`. Returns nonzero on success,
zero on failure or on a null/uninitialized buffer. `bytesRead`
receives the transferred byte count and may be `null`.

### os::win32::WriteFile

```franko
func WriteFile(uint64_t hFile, ptr<array<byte>> buf, uint32_t count, ptr<uint32_t> bytesWritten) -> int32_t
```

Writes up to `count` bytes from `buf`. Returns nonzero on success,
zero on failure or on a null/uninitialized buffer. `bytesWritten`
receives the transferred byte count and may be `null`.

Example:

```franko
import "franko/os"

func main() -> int32_t {
    uint64_t h = os::win32::GetStdHandle(os::win32::STD_OUTPUT_HANDLE);
    array<byte> msg;
    msg.init(5);
    msg = "hello";
    int32_t ok = os::win32::WriteFile(h, getptr(msg), 5, null);
    print(ok);
    msg.uninit();
    return 0;
}
```

## Tests

Module tests live in `library_tests/os/` (run with
`uv run python library_tests/run_library_tests.py`):

```text
library_tests/os/posix/success/   // read/write behavior goldens
library_tests/os/posix/runtime/   // buffer-bound aborts (exit status only)
library_tests/os/posix/failure/   // rejected misuses (pinned diagnostics)
library_tests/os/win32/success/   // File API goldens (Windows only)
library_tests/os/target/success/  // IS_* constants + target-selected I/O
library_tests/os/target/failure/  // off-target calls, writes to constants
```
