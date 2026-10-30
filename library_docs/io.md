# franko/io

User-level output and input for Franko programs. `print` is an ordinary
overloaded function here, not a compiler statement, and `read_bytes` /
`read_line` fill caller-provided buffers with stdin data.

```franko
import "franko/io"
```

Module files:

```text
import/franko/io.fr      // Franko implementation (on top of franko/os)
```

`io` imports `franko/os` itself (via a file-relative `./os` import, so it
works regardless of the user's `-p` search directory) and talks through
the OS bridge: `os::posix::write` / `os::posix::read` on linux/macos,
`os::win32::WriteFile` / `os::win32::ReadFile` on Windows. Output
therefore shares a channel with raw fd writes and appears in program
order, and input shares one with raw fd reads.

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
- Input fills a caller-provided buffer (arrays cannot be returned
  directly, so `read_bytes` / `read_line` write through an `mview`
  out-buffer, like the pool allocators do) and reports how much
  arrived as `int64`. A null buffer, or a `count`/`max` larger than
  the buffer, returns `-1` without consuming any input.

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

func print(view<[uint8]> utf8str, view<[uint8]> end) -> void

func read_bytes(mview<[byte]> buf, uint32 count) -> int64
func read_line(mview<[byte]> buf, uint32 max) -> int64
```

The two-argument forms exist so multi-value lines keep their old
single-newline shape without a joiner overload: `print(a, " ")` writes
`a` plus one space, and a following `print(b)` finishes the line. The
text form writes partial lines (prompts, greetings) the same way:
`print("What's your name? ", null)` writes the prompt with no newline.

```franko
import "franko/io"

func main() -> int32 {
    print(42)       // `42` + newline
    print("hi")     // `hi` + newline
    print(1, " ")   // `1` + space
    print(2)        // `2` + newline -> line reads `1 2`
    print()         // blank line
    print("Hi, ", null)  // `Hi, ` with no suffix
    print("you")    // `you` + newline -> line reads `Hi, you`
    return 0
}
```

Integer constants resolve by the overload preference rule: the parameter
matching the constant's own type wins when the value fits, otherwise the
narrowest fitting type (signed before unsigned at equal width). See
[Fluid Integer Constants](../language_docs/09-fluid-integer-constants.md)
and [Functions](../language_docs/12-functions.md).

## Input

Both read functions read from stdin through the OS bridge and fill a
buffer the caller owns. The return value is the number of bytes stored,
`0` at end of input, or `-1` on error.

### read_bytes

```franko
func read_bytes(mview<[byte]> buf, uint32 count) -> int64
```

Read up to `count` bytes from stdin into `buf`. Returns the number of
bytes read, `0` at end of input, or `-1` on error (a null `buf`, or
`count` larger than the buffer's length).

A short result is normal: the return value may be smaller than `count`
even before end of input. Loop until the return is `0` (or the wanted
total arrives) when the exact byte count matters. Bytes past the
returned count are untouched.

```franko
import "franko/io"

func main() -> int32 {
    [byte] buf;
    buf.init(64);
    int64 n = read_bytes(getmview(buf), 64);
    print(n);           // bytes read (or 0 at end of input, -1 on error)
    buf.uninit();
    return 0;
}
```

### read_line

```franko
func read_line(mview<[byte]> buf, uint32 max) -> int64
```

Read one line from stdin into `buf`, at most `max` bytes. Reading stops
at a `\n`, at `max` bytes, or at end of input. Returns the number of
bytes stored, or `-1` on error (a null `buf`, `max` zero, or `max`
larger than the buffer's length).

- The line ending is consumed but not stored: the returned count is the
  line's length. A `\r\n` ending counts as one terminator — the `\r`
  is dropped too — so lines read the same from an interactive Windows
  console and from a pipe.
- A line longer than `max` is split: the remainder, including its line
  ending, stays in stdin for the next call.
- A final line without a terminator (end of input mid-line) is returned
  normally.
- A `0` return means an empty line **or** end of input with nothing
  pending. Read again (or track the expected line count) to tell them
  apart; `-1` always means an error.

```franko
import "franko/io"

func main() -> int32 {
    [byte] name;
    name.init(32);
    print("What's your name? ", null);
    int64 len = read_line(getmview(name), 32);   // name without the newline
    print(len);
    name.uninit();
    return 0;
}
```

## Tests

```text
library_tests/io/print/success/decimal/   // integer formatting per width
library_tests/io/print/success/string/    // text, custom end, bare newline
library_tests/io/print/success/text_end/  // print(text, end), null text/end
library_tests/io/read/success/read_bytes/      // chunked reads, short read, EOF
library_tests/io/read/success/read_line/       // lines, empty line, EOF
library_tests/io/read/success/read_line_long/  // over-long line splitting
library_tests/io/read/success/read_errors/     // null buffer, bad count/max
```
