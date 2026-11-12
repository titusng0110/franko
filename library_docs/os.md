# franko/os

The `franko/os` module is Franko's low-level operating-system interface.
It exposes a small set of OS syscalls as ordinary Franko functions with
FFI bridge definitions (see [Foreign Function Interface](../language_docs/31-foreign-function-interface.md)
in the language reference for the module descriptor, bridge rules, and
runtime behavior).

```franko
import "franko/os"
```

Module files:

```text
import/franko/os.fr          // Franko declarations (stub bodies)
import/franko/os.module.json // module descriptor and bridge file
import/franko/os.ffi.cpp     // C++ bridge definitions spliced into the build
```

Design rules:

- POSIX semantics are the ground truth; the win32 surface mirrors the
  Win32 File API (`fileapi.h`) with POSIX-shaped compromises where noted.
- Only low-level, non-derived primitives are exposed.
- Buffers are always a pointer to a dynamic byte array, carrying the
  permission the call needs: `view<[byte]>` for the read-only
  surfaces (`write`, `WriteFile`) and `mview<[byte]>` for the ones
  that fill the buffer (`read`, `ReadFile`). The out-parameters
  (`bytesRead`, `bytesWritten`) are `mview<uint32>`: written through,
  never allocated, and nullable. The bridge checks `count` against the
  array length and aborts via `franko_panic` when `count` exceeds it,
  so the FFI cannot corrupt memory. A null or uninitialized buffer
  returns the stub failure value instead of crashing.
- Stub bodies in `os.fr` report failure (`-1` for posix, `0` for win32),
  so a missing bridge can never look like silent success.
- These are FFI primitives and intentionally keep the OS/C return
  conventions: `os::posix::read`/`write` return a byte count or `-1`, and
  the win32 functions return a status plus an out-parameter. They are
  not tuple-based; `franko/io` wraps the input path into
  `(int64 val, int32 err)` tuples.

## Target selection

Compile-time target constants are built into the language (see
[Compile-Time Reflection](../language_docs/32-compile-time-reflection.md)):

```franko
#IS_LINUX    // 1 when targeting linux
#IS_WINDOWS  // 1 when targeting windows
#IS_MACOS    // 1 when targeting macos
#IS_X86_64   // 1 when targeting x86_64
#IS_ARM64    // 1 when targeting arm64
```

The target comes from the compiler's required
`--target <os>-<arch>` flag (`linux-x86_64`, `windows-x86_64`,
`macos-arm64`, ...); `compile.py` defaults it to the host. References
fold to integer literals, so assignment or `getptr(...)` on them is
rejected with the ordinary "not a storage-backed lvalue" error.

Select platform-specific FFI calls with `#if`:

```franko
import "franko/os"

func main() -> int32 {
    [byte] msg;
    msg.init(5);
    msg.memcpy("hello");

    #if (#IS_WINDOWS) {
        uint64 h = os::win32::GetStdHandle(os::win32::STD_OUTPUT_HANDLE);
        os::win32::WriteFile(h, getview(msg), 5, null);
    } #else #if (#IS_LINUX || #IS_MACOS) {
        os::posix::write(os::posix::STDOUT_FILENO, getview(msg), 5);
    }

    msg.uninit();
    return 0;
}
```

Only the selected branch is analyzed, verified against the module
descriptor, and emitted. The descriptor lists both platform families;
module validation accepts declarations from either branch, while FFI
verification and code generation only see the selected one. The other
platform's FFI functions are never required to exist on this target.

## os::posix

POSIX file-descriptor I/O, available on linux and macos. `os.fr`
declares the `os::posix` namespace only for those targets; the bridge
calls `::read` / `::write` from `<unistd.h>`. Guard portable posix calls
with `#if (#IS_LINUX || #IS_MACOS)` (see
[Target selection](#target-selection)) when the program must compile
for Windows too.

```franko
int32 STDIN_FILENO = 0
int32 STDOUT_FILENO = 1
int32 STDERR_FILENO = 2
```

### os::posix::read

```franko
func read(int32 fd, mview<[byte]> buf, uint64 count) -> int64
```

Reads up to `count` bytes from file descriptor `fd` into `buf`.
Returns the number of bytes read, `0` at end of input, or `-1` on
error or on a null/uninitialized buffer.

### os::posix::write

```franko
func write(int32 fd, view<[byte]> buf, uint64 count) -> int64
```

Writes up to `count` bytes from `buf` to file descriptor `fd`.
Returns the number of bytes written, or `-1` on error or on a
null/uninitialized buffer. A successful write may write fewer bytes
than requested.

Example:

```franko
import "franko/os"
import "franko/io"

func main() -> int32 {
    [byte] msg;
    msg.init(5);
    msg.memcpy("hello");
    int64 n = os::posix::write(os::posix::STDOUT_FILENO, getview(msg), 5);
    print(n);
    msg.uninit();
    return 0;
}
```

Note: `print` (from `franko/io`) goes through the same OS bridge as raw fd writes, so interleaved output appears in program order.

## os::win32

Win32 File API I/O. Windows-only: `os.fr` declares the `os::win32`
namespace only for Windows, so an off-target call is a compile error.
Guard portable win32 calls with `#if (#IS_WINDOWS)` (see
[Target selection](#target-selection) above): the untaken branch is
never analyzed, so portable programs still compile anywhere.
`lpOverlapped` is dropped throughout: the bridge always passes `NULL`
(synchronous I/O).

```franko
uint32 STD_INPUT_HANDLE = 0xFFFFFFF6
uint32 STD_OUTPUT_HANDLE = 0xFFFFFFF5
uint32 STD_ERROR_HANDLE = 0xFFFFFFF4
```

### os::win32::GetStdHandle

```franko
func GetStdHandle(uint32 nStdHandle) -> uint64
```

Returns the `HANDLE` for a standard device as a 64-bit value, for use
as the `hFile` argument below. Pass one of the `STD_*_HANDLE`
constants. Returns `0` (`NULL`) or `0xFFFFFFFFFFFFFFFF`
(`INVALID_HANDLE_VALUE`) on failure.

### os::win32::ReadFile

```franko
func ReadFile(uint64 hFile, mview<[byte]> buf, uint32 count, mview<uint32> bytesRead) -> int32
```

Reads up to `count` bytes into `buf`. Returns nonzero on success,
zero on failure or on a null/uninitialized buffer. `bytesRead`
receives the transferred byte count and may be `null`.

### os::win32::WriteFile

```franko
func WriteFile(uint64 hFile, view<[byte]> buf, uint32 count, mview<uint32> bytesWritten) -> int32
```

Writes up to `count` bytes from `buf`. Returns nonzero on success,
zero on failure or on a null/uninitialized buffer. `bytesWritten`
receives the transferred byte count and may be `null`.

Example:

```franko
import "franko/os"
import "franko/io"

func main() -> int32 {
    uint64 h = os::win32::GetStdHandle(os::win32::STD_OUTPUT_HANDLE);
    [byte] msg;
    msg.init(5);
    msg.memcpy("hello");
    int32 ok = os::win32::WriteFile(h, getview(msg), 5, null);
    print(ok);
    msg.uninit();
    return 0;
}
```

## Tests

Module tests live in `library_tests/os/` (run with
`uv run python library_tests/run_library_tests.py`):

```text
library_tests/os/posix/  // read/write and fd constants (non-Windows)
library_tests/os/win32/  // GetStdHandle/ReadFile/WriteFile and handle constants (Windows)
```

The POSIX fixture exercises `read`, `write`, and the stdio fd constants; the
Win32 fixture exercises `GetStdHandle`, `ReadFile`, `WriteFile`, and the
standard-handle constants.
