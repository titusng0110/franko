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
- Buffers are always `addr<array<byte>>` (a pointer to a dynamic byte
  array). The bridge checks `count` against the array length and aborts
  via `franko_panic` when `count` exceeds it, so the FFI cannot corrupt
  memory. A null or uninitialized buffer returns the stub failure value
  instead of crashing.
- Stub bodies in `os.fr` report failure (`-1` for posix, `0` for win32),
  so a missing bridge can never look like silent success.

## os::posix

POSIX file-descriptor I/O. Works on every target: on Windows the bridge
maps these to `_read` / `_write` from `<io.h>`.

```franko
int32_t STDIN_FILENO = 0
int32_t STDOUT_FILENO = 1
int32_t STDERR_FILENO = 2
```

### os::posix::read

```franko
func read(int32_t fd, addr<array<byte>> buf, uint64_t count) -> int64_t
```

Reads up to `count` bytes from file descriptor `fd` into `buf`.
Returns the number of bytes read, `0` at end of input, or `-1` on
error or on a null/uninitialized buffer.

### os::posix::write

```franko
func write(int32_t fd, addr<array<byte>> buf, uint64_t count) -> int64_t
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
    int64_t n = os::posix::write(os::posix::STDOUT_FILENO, getaddr(msg), 5);
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

Win32 File API I/O. Windows-only: calling any of these functions in a
program compiled for another target fails at the C++ stage with a
clear `#error` instead of a link error. `lpOverlapped` is dropped
throughout: the bridge always passes `NULL` (synchronous I/O).

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
func ReadFile(uint64_t hFile, addr<array<byte>> buf, uint32_t count, addr<uint32_t> bytesRead) -> int32_t
```

Reads up to `count` bytes into `buf`. Returns nonzero on success,
zero on failure or on a null/uninitialized buffer. `bytesRead`
receives the transferred byte count and may be `null`.

### os::win32::WriteFile

```franko
func WriteFile(uint64_t hFile, addr<array<byte>> buf, uint32_t count, addr<uint32_t> bytesWritten) -> int32_t
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
    int32_t ok = os::win32::WriteFile(h, getaddr(msg), 5, null);
    print(ok);
    msg.uninit();
    return 0;
}
```

## Tests

Module tests live in `library_tests/os/` (run with
`library_tests/run_library_tests.sh` or
`library_tests/run_library_tests.ps1`):

```text
library_tests/os/posix/success/   // read/write behavior goldens
library_tests/os/posix/runtime/   // buffer-bound aborts (exit status only)
library_tests/os/posix/failure/   // rejected misuses (pinned diagnostics)
library_tests/os/win32/success/   // File API goldens (Windows only)
```
