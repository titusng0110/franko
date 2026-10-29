# franko/io

User-level output for Franko programs. `print` is an ordinary overloaded
function here, not a compiler statement.

```franko
import "franko/io"
```

Module files:

```text
import/franko/io.fr      // Franko implementation (on top of franko/os)
```

`io` imports `franko/os` itself (via a file-relative `./os` import, so it
works regardless of the user's `-p` search directory) and writes through
the OS bridge: `os::posix::write` on linux/macos, `os::win32::WriteFile`
on Windows. Output therefore shares a channel with raw fd writes and
appears in program order.

Design rules:

- Single-argument `print(x)` writes `x` followed by `\n`.
- Two-argument `print(x, end)` writes `x` followed by `end` verbatim.
  A null or empty `end` means no suffix and no newline.
- `print()` writes a bare newline.
- Text (`view<[uint8]>`) is written as raw UTF-8 bytes, verbatim.
  A null or empty view prints nothing (not even a newline).
- Integers are written in decimal. `int8`/`uint8` print as numbers,
  not characters. Negative values print with a leading `-`, including
  the minimum value of each signed type.
- There are no overloads for pointers, arrays, or structs. Print
  elements, fields, or bytes instead.

## API

```franko
func print() -> void

func print(uint8 num) -> void
func print(uint16 num) -> void
func print(uint32 num) -> void
func print(uint64 num) -> void
func print(int8 num) -> void
func print(int16 num) -> void
func print(int32 num) -> void
func print(int64 num) -> void

func print(view<[uint8]> utf8str) -> void

func print(uint8 num, view<[uint8]> end) -> void
func print(uint16 num, view<[uint8]> end) -> void
func print(uint32 num, view<[uint8]> end) -> void
func print(uint64 num, view<[uint8]> end) -> void
func print(int8 num, view<[uint8]> end) -> void
func print(int16 num, view<[uint8]> end) -> void
func print(int32 num, view<[uint8]> end) -> void
func print(int64 num, view<[uint8]> end) -> void
```

The two-argument forms exist so multi-value lines keep their old
single-newline shape without a joiner overload: `print(a, " ")` writes
`a` plus one space, and a following `print(b)` finishes the line.

```franko
import "franko/io"

func main() -> int32 {
    print(42)       // `42` + newline
    print("hi")     // `hi` + newline
    print(1, " ")   // `1` + space
    print(2)        // `2` + newline -> line reads `1 2`
    print()         // blank line
    return 0
}
```

Integer constants resolve by the overload preference rule: the parameter
matching the constant's own type wins when the value fits, otherwise the
narrowest fitting type (signed before unsigned at equal width). See
[Fluid Integer Constants](../language_docs/09-fluid-integer-constants.md)
and [Functions](../language_docs/12-functions.md).

## Future API: line input

Not implemented yet. The planned input surface mirrors the output one:
thin target-independent wrappers over `os::posix::read` /
`os::win32::ReadFile` that read from stdin into a caller-provided
dynamic byte buffer (arrays cannot be returned directly, so input fills
an `mview` out-buffer like the pool allocators do):

```franko
// Proposed (not implemented):
// func readBytes(mview<[byte]> buf, uint32 count) -> int64
//   Read up to `count` bytes from stdin into `buf`; return bytes read,
//   0 at end of input, -1 on error.
//
// func readLine(mview<[byte]> buf, uint32 max) -> int64
//   Read until a newline (`\n`, tolerating `\r\n`) or `max` bytes;
//   return the byte count excluding the terminator.
```

## Tests

```text
library_tests/io/print/success/decimal/   // integer formatting per width
library_tests/io/print/success/string/    // text, custom end, bare newline
```
