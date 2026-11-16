# franko/os

The `franko/os` module is Franko's low-level operating-system interface.
It exposes a small set of OS syscalls as ordinary Franko functions with
FFI bridge definitions (see
[Foreign Function Interface](../language_docs/34-foreign-function-interface.md)
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
- **The standard streams belong to `franko/stdio`.** `os::posix::read` and
  `os::posix::write` abort when passed fd `0`, `1`, or `2`.
  `os::win32::ReadFile` and `os::win32::WriteFile` abort when passed the
  standard input, output, or error handle. Use `franko/stdio` for all
  standard I/O; use `franko/os` only for other file descriptors or file
  handles.
- `os::win32::GetStdHandle` and the `STD*_FILENO` / `STD*_HANDLE` constants
  are not exposed. There is no supported way to obtain a standard handle
  from Franko code.
- Buffers are always a pointer to a dynamic byte array, carrying the
  permission the call needs: `view<[byte]>` for the read-only surfaces
  (`write`, `WriteFile`) and `mview<[byte]>` for the ones that fill the
  buffer (`read`, `ReadFile`). The out-parameters (`bytesRead`,
  `bytesWritten`) are `mview<uint32>`: written through, never allocated,
  and nullable. The bridge checks `count` against the array length and
  aborts via `franko_panic` when `count` exceeds it, so the FFI cannot
  corrupt memory. A null or uninitialized buffer returns the stub failure
  value instead of crashing.
- Stub bodies in `os.fr` report failure (`-1` for posix, `0` for win32),
  so a missing bridge can never look like silent success.
- These are FFI primitives and intentionally keep the OS/C return
  conventions: `os::posix::read`/`write` return a byte count or `-1`, and
  the win32 functions return a status plus an out-parameter. They are
  not tuple-based; `franko/stdio` wraps its input path into
  `(int64 val, int32 err)` tuples.

## Target selection

Compile-time target constants are built into the language (see
[Compile-Time Reflection](../language_docs/35-compile-time-reflection.md)):

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
fold to integer literals, so assignment or `ptr(...)` on them is
rejected with the ordinary "not a storage-backed lvalue" error.

Select platform-specific FFI calls with `#if`:

```franko
import "franko/os"
import "franko/stdio"

func main() -> int32 {
    int64 value = 0;

    #if (#IS_WINDOWS) {
        os::win32::QueryPerformanceCounter(mview(value));
    } #else #if (#IS_LINUX || #IS_MACOS) {
        int64 sec = 0;
        int64 nanosec = 0;
        os::posix::clock_gettime(
                os::posix::CLOCK_MONOTONIC,
                mview(sec),
                mview(nanosec)
        );
        value = sec;
    }

    stdout(value);
    return 0;
}
```

Only the selected branch is analyzed, verified against the module
descriptor, and emitted. The descriptor lists both platform families;
module validation accepts declarations from either branch, while FFI
verification and code generation only see the selected one. The other
platform's FFI functions are never required to exist on this target.

## os::posix

POSIX file-descriptor I/O and clocks, available on linux and macos.
`os.fr` declares the `os::posix` namespace only for those targets; the
bridge calls `::read`, `::write`, and `::clock_gettime` from
`<unistd.h>` / `<time.h>`. Guard portable posix calls with
`#if (#IS_LINUX || #IS_MACOS)` (see
[Target selection](#target-selection)) when the program must compile
for Windows too.

```franko
int32 CLOCK_REALTIME = 0
int32 CLOCK_MONOTONIC = 1    // 6 on macOS
```

### os::posix::read

```franko
func read(int32 fd, mview<[byte]> buf, uint64 count) -> int64
```

Reads up to `count` bytes from file descriptor `fd` into `buf`.
Returns the number of bytes read, `0` at end of input, or `-1` on
error or on a null/uninitialized buffer.

File descriptors `0`, `1`, and `2` are reserved for `franko/stdio`;
passing one aborts through `franko_panic`.

### os::posix::write

```franko
func write(int32 fd, view<[byte]> buf, uint64 count) -> int64
```

Writes up to `count` bytes from `buf` to file descriptor `fd`.
Returns the number of bytes written, or `-1` on error or on a
null/uninitialized buffer. A successful write may write fewer bytes
than requested.

File descriptors `0`, `1`, and `2` are reserved for `franko/stdio`;
passing one aborts through `franko_panic`.

### os::posix::clock_gettime

```franko
func clock_gettime(int32 clock_id, mview<int64> sec, mview<int64> nanosec) -> int32
```

Reads the selected POSIX clock into `sec` and `nanosec` through the
out-parameters. Returns `0` on success and `-1` on error. Pass
`os::posix::CLOCK_REALTIME` for wall-clock Unix time, or
`os::posix::CLOCK_MONOTONIC` for elapsed-time measurements. The bridge
calls `::clock_gettime` directly; the second and nanosecond fields are
copied from the native `struct timespec`.

## os::win32

Win32 File API I/O and timing. Windows-only: `os.fr` declares the
`os::win32` namespace only for Windows, so an off-target call is a
compile error. Guard portable win32 calls with `#if (#IS_WINDOWS)` (see
[Target selection](#target-selection) above): the untaken branch is
never analyzed, so portable programs still compile anywhere.
`lpOverlapped` is dropped throughout: the bridge always passes `NULL`
(synchronous I/O).

Standard input, output, and error handles are reserved for
`franko/stdio`. `ReadFile` and `WriteFile` abort through `franko_panic`
when passed one. There is no `GetStdHandle` wrapper and no standard
handle constants.

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

## Timing primitives

`os::win32` exposes the raw Windows performance and wall-clock counters.
These are thin bridges, not the higher-level `franko/time` API.

### os::win32::QueryPerformanceCounter

```franko
func QueryPerformanceCounter(mview<int64> counter) -> int32
```

Reads the current value of the high-resolution performance counter into
`counter`. Returns nonzero on success and `0` on failure. The counter is
monotonic; convert it to nanoseconds with
`os::win32::QueryPerformanceFrequency`.

### os::win32::QueryPerformanceFrequency

```franko
func QueryPerformanceFrequency(mview<int64> frequency) -> int32
```

Reads the performance-counter frequency in counts per second into
`frequency`. Returns nonzero on success and `0` on failure.

### os::win32::GetSystemTimePreciseAsFileTime

```franko
func GetSystemTimePreciseAsFileTime() -> uint64
```

Returns the current UTC wall-clock time as a Windows `FILETIME` value:
100-nanosecond intervals since 1601-01-01 UTC. The bridge calls
`::GetSystemTimePreciseAsFileTime` and combines the low and high `DWORD`
halves. Use [`franko/time`](time.md) for Unix timestamps.

## Tests

Module tests live in `library_tests/os/` (run with
`uv run python library_tests/run_library_tests.py`):

```text
library_tests/os/posix/  // clock_gettime (non-Windows)
library_tests/os/win32/  // performance and wall-clock primitives (Windows)
```

Standard-stream read/write coverage is intentionally omitted; those calls
are policy violations owned by `franko/stdio`.
