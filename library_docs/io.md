# franko/io

User-level output and input for Franko programs. `print` is an ordinary
overloaded function here, not a compiler statement; `print_err` mirrors
it on stderr. `read_bytes` fills a caller-provided byte buffer, and
`read_line` reads one line into a `std::String`.

```franko
import "franko/io"
```

Module files:

```text
import/franko/io.fr      // Franko implementation (on top of franko/os and franko/string)
```

`io` imports the standard `franko/os` and `franko/string` modules via
file-relative `./os` and `./string` imports, so they work regardless of
the user's `-p` search directory, and talks through the OS bridge:
`os::posix::write` / `os::posix::read` on linux/macos,
`os::win32::WriteFile` / `os::win32::ReadFile` on Windows.
Output therefore shares a channel with raw fd writes and appears in
program order, and input shares one with raw fd reads.

Design rules:

- Single-argument `print(x)` writes `x` followed by `\n`.
- Two-argument `print(x, end)` writes `x` followed by `end` verbatim.
  A null or empty `end` means no suffix and no newline.
- `print()` writes a bare newline.
- Text (`view<[uint8]>`) is written as raw UTF-8 bytes, verbatim.
  A null or empty view prints nothing (not even a newline).
- `std::String` text is written as its live bytes, not the backing
  buffer's unused capacity. A null or empty string prints nothing (not
  even a newline) unless a non-empty `end` argument is supplied.
- Integers are written in decimal. `int8`/`uint8` print as numbers,
  not characters. Negative values print with a leading `-`, including
  the minimum value of each signed type.
- Integer output is formatted into a 25-byte stack buffer. The separator
  is appended there when it fits, so the whole line is written in one
  call; a longer separator is written in a second call. This applies to
  both `print` and `print_err`.
- `print_err` mirrors every `print` overload on stderr. It has the same
  null/empty and separator rules.
- There are no overloads for pointers or arrays. Print elements or
  bytes instead. Struct printing is limited to `std::String`.
- `read_bytes` fills a caller-provided byte buffer and returns
  `(uint32 val, int32 err)`. `val` is the number of bytes stored; `err`
  is `0` on success and `-1` on error. A null buffer, or a `count`
  larger than the buffer, returns `(0, -1)` without consuming any
  input. `read_line` fills a `std::String` and returns `int32`:
  `0` on success, `-1` on a read/allocation error, and `-2` when the
  line is too long.

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

func print(view<std::String> str) -> void
func print(view<std::String> str, view<std::String> end) -> void
func print(view<std::String> str, view<[byte]> end) -> void

func print_err() -> void

func print_err(uint8 num) -> void
func print_err(uint16 num) -> void
func print_err(uint32 num) -> void
func print_err(uint64 num) -> void
func print_err(int8 num) -> void
func print_err(int16 num) -> void
func print_err(int32 num) -> void
func print_err(int64 num) -> void

func print_err(view<[uint8]> utf8str) -> void

func print_err(uint8 num, view<[uint8]> end) -> void
func print_err(uint16 num, view<[uint8]> end) -> void
func print_err(uint32 num, view<[uint8]> end) -> void
func print_err(uint64 num, view<[uint8]> end) -> void
func print_err(int8 num, view<[uint8]> end) -> void
func print_err(int16 num, view<[uint8]> end) -> void
func print_err(int32 num, view<[uint8]> end) -> void
func print_err(int64 num, view<[uint8]> end) -> void

func print_err(view<[uint8]> utf8str, view<[uint8]> end) -> void

func print_err(view<std::String> str) -> void
func print_err(view<std::String> str, view<std::String> end) -> void
func print_err(view<std::String> str, view<[byte]> end) -> void

func read_bytes(mview<[byte]> buf, uint32 count) -> (uint32 val, int32 err)
func read_line(ptr<std::String> out) -> int32
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
[Fluid Integer Constants](../language_docs/07-fluid-integer-constants.md)
and [Functions](../language_docs/19-functions.md).

## print_err

Every `print` overload has a `print_err` counterpart that writes to
stderr instead of stdout. The semantics are identical: one argument
adds `\n`, a second argument is written verbatim, a null or empty text
view prints nothing, and integers are formatted in decimal.

```franko
import "franko/io"

func main() -> int32 {
    print_err("error: ")     // prompt-style, no newline
    print_err(42)            // `42` + newline
    print_err()              // blank line on stderr
    print("done")            // stdout
    return 0
}
```

Both streams share the same channel as raw fd writes: `print` uses the
stdout fd/handle and `print_err` uses the stderr fd/handle. On a
terminal or when both streams are redirected to the same file, writes
appear in program order.

## std::String output

The `std::String` overloads accept a read-only `view<std::String>` and
write only the live bytes reported by `length()`, not the backing
buffer's unused capacity. A null or empty string writes nothing; the
two-argument forms still write a non-empty `end` argument.

A bare `print(null)` is ambiguous between the text and `std::String`
overloads. Declare a typed null view (`view<[uint8]> nil = null`) when a
null text argument is intended.

```franko
import "franko/io"
import "franko/string"

func main() -> int32 {
    std::String s
    s.from("hello")
    print(getview(s))                 // hello + newline
    print(getview(s), "!")            // hello!
    print()                           // newline

    std::String end
    end.from(" world")
    print(getview(s), getview(end))   // hello world
    print()                           // newline

    s.uninit()
    end.uninit()
    return 0
}
```

## Input

Both read functions read from stdin through the OS bridge and share an
internal 8192-byte input buffer; bytes read ahead stay buffered for the
next call. `read_bytes` fills a caller-owned byte buffer and returns
`(uint32 val, int32 err)`; `read_line` fills a `std::String` and returns
`int32`.

### read_bytes

```franko
func read_bytes(mview<[byte]> buf, uint32 count) -> (uint32 val, int32 err)
```

Read up to `count` bytes from stdin into `buf`. Returns `(val, err)`,
where `val` is the number of bytes read and `err` is `0` on success or
`-1` on error (a null `buf`, or `count` larger than the buffer's
length). End of input is `(0, 0)`.

A short result is normal: `val` may be smaller than `count` even before
end of input. Loop until `val` is `0` (or the wanted total arrives) when
the exact byte count matters. Bytes past `val` are untouched.

```franko
import "franko/io"

func main() -> int32 {
    [byte] buf;
    buf.init(64);
    (uint32 val, int32 err) result = read_bytes(getmview(buf), 64);
    if (result.err != 0) {
        print(result.err);
    } else {
        print(result.val);   // bytes read, or 0 at end of input
    }
    buf.uninit();
    return 0;
}
```

### read_line

```franko
func read_line(ptr<std::String> out) -> int32
```

Reads one line from stdin into `out`, completely replacing its contents.
Reading stops at `\n`, `\r\n`, or end of input. Returns `0` on success,
`-1` for a null `out`, a read error, or an allocation failure, and `-2`
for a line longer than 81920 bytes.

- The line ending is consumed but not stored. A `\r\n` ending counts as
  one terminator; the `\r` is dropped too, so lines read the same from an
  interactive Windows console and from a pipe.
- The line is accumulated in a local buffer sized to the exact length of
  the first chunk. When the line does not fit, the buffer doubles up to
  a maximum of 81920 bytes of line content.
- A line longer than 81920 bytes is consumed through its line ending and
  reported as `-2`; `out` is left empty. `-2` means "line too long", not
  a memory or read error.
- A final line without a terminator (end of input mid-line) is returned
  normally.
- An empty string means an empty line **or** end of input with nothing
  pending. Read again (or track the expected line count) to tell them
  apart; `-1` and `-2` always mean an error.

```franko
import "franko/io"
import "franko/string"

func main() -> int32 {
    print("What's your name? ", null);
    std::String name;
    if (read_line(getptr(name)) != 0) {
        print("input error");
    } else {
        print(name.length());   // name length without the newline
    }
    name.uninit();
    return 0;
}
```

## Tests

```text
library_tests/io/print/success/decimal/   // integer formatting per width
library_tests/io/print/success/string/    // text, custom end, bare newline
library_tests/io/print/success/text_end/  // print(text, end), null text/end
library_tests/io/print/success/std_string/ // print(std::String), String/byte ends
library_tests/io/print/success/err_overloads/ // every print_err overload, merged streams
library_tests/io/read/success/read_bytes/      // chunked reads, short read, EOF
library_tests/io/read/success/read_line/       // lines, empty line, EOF
library_tests/io/read/success/read_line_long/  // buffer growth, long line
library_tests/io/read/success/read_line_limit/ // 81920-byte limit, -2, resync
library_tests/io/read/success/read_errors/     // null buffers, bad count, EOF
```
