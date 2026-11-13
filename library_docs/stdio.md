# franko/stdio

User-level standard I/O for Franko programs. `stdout` is an ordinary
overloaded function here, not a compiler statement; `stderr` mirrors it on
the standard error stream. `stdin_bytes` fills a caller-provided byte buffer,
and `stdin_line` reads one line into a `std::String`.

```franko
import "franko/stdio"
```

Module files:

```text
import/franko/stdio.fr    // Franko implementation (on top of franko/os and franko/string)
```

`stdio` imports the standard `franko/os` and `franko/string` modules via
file-relative `./os` and `./string` imports, so they work regardless of the
user's `-p` search directory, and talks through the OS bridge:
`os::posix::write` / `os::posix::read` on linux/macos,
`os::win32::WriteFile` / `os::win32::ReadFile` on Windows.
Output therefore shares a channel with raw fd writes and appears in program
order, and input shares one with raw fd reads.

Design rules:

- `stdout()` writes a bare newline.
- `stdout(value)` writes `value` followed by `\n`.
- `stdout(value, end = suffix)` writes `value` followed by `end` verbatim.
  A null or empty `end` means no suffix and no newline.
- Because `end` is an optional parameter, a call that supplies it must name
  it: `stdout(42, end = " ")`. A one-argument call uses the default newline.
- Text (`view<[uint8]>`) is written as raw UTF-8 bytes, verbatim.
- `std::String` text is written as its live bytes, not the backing buffer's
  unused capacity.
- Integers are written in decimal. `int8`/`uint8` print as numbers, not
  characters. Negative values print with a leading `-`, including the
  minimum value of each signed type.
- Integer output is formatted into a 25-byte stack buffer. The separator is
  appended there when it fits, so the whole line is written in one call; a
  longer separator is written in a second call. This applies to both
  `stdout` and `stderr`.
- `stderr` mirrors every `stdout` overload on stderr. It has the same
  null/empty and separator rules.
- There are no overloads for pointers or arrays. Print individual elements or
  bytes instead. Struct printing is limited to `std::String`.
- `stdin_bytes` fills a caller-provided byte buffer and returns
  `(uint32 val, int32 err)`. `val` is the number of bytes stored; `err`
  is `0` on success and `-1` on error. A null buffer, or a `count`
  larger than the buffer, returns `(0, -1)` without consuming any
  input. `stdin_line` fills a `std::String` and returns `int32`:
  `0` on success, `-1` on a read/allocation error, and `-2` when the
  line is too long.

## API

```franko
func stdout() -> void

func stdout(uint8 num, view<[uint8]> end = "\n") -> void
func stdout(uint16 num, view<[uint8]> end = "\n") -> void
func stdout(uint32 num, view<[uint8]> end = "\n") -> void
func stdout(uint64 num, view<[uint8]> end = "\n") -> void
func stdout(int8 num, view<[uint8]> end = "\n") -> void
func stdout(int16 num, view<[uint8]> end = "\n") -> void
func stdout(int32 num, view<[uint8]> end = "\n") -> void
func stdout(int64 num, view<[uint8]> end = "\n") -> void

func stdout(view<[uint8]> utf8str, view<[uint8]> end = "\n") -> void
func stdout(view<std::String> str, view<std::String> end) -> void
func stdout(view<std::String> str, view<[byte]> end = "\n") -> void

func stderr() -> void

func stderr(uint8 num, view<[uint8]> end = "\n") -> void
func stderr(uint16 num, view<[uint8]> end = "\n") -> void
func stderr(uint32 num, view<[uint8]> end = "\n") -> void
func stderr(uint64 num, view<[uint8]> end = "\n") -> void
func stderr(int8 num, view<[uint8]> end = "\n") -> void
func stderr(int16 num, view<[uint8]> end = "\n") -> void
func stderr(int32 num, view<[uint8]> end = "\n") -> void
func stderr(int64 num, view<[uint8]> end = "\n") -> void

func stderr(view<[uint8]> utf8str, view<[uint8]> end = "\n") -> void
func stderr(view<std::String> str, view<std::String> end) -> void
func stderr(view<std::String> str, view<[byte]> end = "\n") -> void

func stdin_bytes(mview<[byte]> buf, uint32 count) -> (uint32 val, int32 err)
func stdin_line(ptr<std::String> out) -> int32
```

The `end` forms let multi-value lines keep their single-newline shape without
a joiner overload: `stdout(a, end = " ")` writes `a` plus one space, and a
following `stdout(b)` finishes the line. The text form writes partial lines
(prompts, greetings) the same way: `stdout("What's your name? ", end = null)`
writes the prompt with no newline.

```franko
import "franko/stdio"

func main() -> int32 {
    stdout(42)                      // `42` + newline
    stdout("hi")                    // `hi` + newline
    stdout(1, end = " ")            // `1` + space
    stdout(2)                       // `2` + newline -> line reads `1 2`
    stdout()                        // blank line
    stdout("Hi, ", end = null)      // `Hi, ` with no suffix
    stdout("you")                   // `you` + newline -> line reads `Hi, you`
    return 0
}
```

Integer constants resolve by the overload preference rule: an untyped
constant uses the narrowest fitting type (signed before unsigned at equal
width), while an explicitly typed constant prefers an exact match first. See
[Fluid Integer Constants](../language_docs/07-fluid-integer-constants.md)
and [Functions](../language_docs/19-functions.md).

## stderr

Every `stdout` overload has a `stderr` counterpart that writes to
the standard error stream instead. The semantics are identical: one argument
adds `\n`, a named `end` argument is written verbatim, and integers are
formatted in decimal.

```franko
import "franko/stdio"

func main() -> int32 {
    stderr("error: ")        // prompt-style, no newline
    stderr(42)               // `42` + newline
    stderr()                 // blank line on stderr
    stdout("done")           // stdout
    return 0
}
```

Both streams share the same channel as raw fd writes: `stdout` uses the
stdout fd/handle and `stderr` uses the stderr fd/handle. On a terminal or
when both streams are redirected to the same file, writes appear in program
order.

## std::String output

The `std::String` overloads accept a read-only `view<std::String>` and write
only the live bytes reported by `length()`, not the backing buffer's unused
capacity. A null or empty string writes no text, but a non-empty `end` is
still written. The `view<std::String>` end overload is a required argument;
the `view<[byte]>` end overload defaults to a newline.

A bare `stdout(null)` is ambiguous between the text and `std::String`
overloads. Declare a typed null view (`view<[uint8]> nil = null`) when a
null text argument is intended. An empty string literal `""` is also the
null view, so `stdout("")` is ambiguous in the same way; use `stdout()` when
a newline is intended.

```franko
import "franko/stdio"
import "franko/string"

func main() -> int32 {
    std::String s
    s.from("hello")
    stdout(getview(s))                 // hello + newline
    stdout(getview(s), end = "!")      // hello!
    stdout()                           // newline

    std::String end
    end.from(" world")
    stdout(getview(s), getview(end))   // hello world
    stdout()                           // newline

    s.uninit()
    end.uninit()
    return 0
}
```

## Input

Both read functions read from stdin through the OS bridge and share an
internal 8192-byte input buffer; bytes read ahead stay buffered for the
next call. `stdin_bytes` fills a caller-owned byte buffer and returns
`(uint32 val, int32 err)`; `stdin_line` fills a `std::String` and returns
`int32`.

### stdin_bytes

```franko
func stdin_bytes(mview<[byte]> buf, uint32 count) -> (uint32 val, int32 err)
```

Read up to `count` bytes from stdin into `buf`, looping over the shared
8192-byte input buffer until `count` bytes have been stored, end of
input is reached, or a read error occurs. Returns `(val, err)`, where
`val` is the number of bytes stored and `err` is `0` on success or `-1`
on error (a null `buf`, or `count` larger than the buffer's length).
End of input is `(0, 0)`.

A request larger than 8192 bytes is filled from as many chunks as
needed. A `val` smaller than `count` means end of input; when `err` is
`-1`, `val` holds the bytes stored before the error. Bytes past `val`
are untouched.

```franko
import "franko/stdio"

func main() -> int32 {
    [byte] buf;
    buf.init(64);
    (uint32 val, int32 err) result = stdin_bytes(getmview(buf), 64);
    if (result.err != 0) {
        stdout(result.err);
    } else {
        stdout(result.val);   // bytes read, or 0 at end of input
    }
    buf.uninit();
    return 0;
}
```

### stdin_line

```franko
func stdin_line(ptr<std::String> out) -> int32
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
import "franko/stdio"
import "franko/string"

func main() -> int32 {
    stdout("What's your name? ", end = null);
    std::String name;
    if (stdin_line(getptr(name)) != 0) {
        stdout("input error");
    } else {
        stdout(name.length());   // name length without the newline
    }
    name.uninit();
    return 0;
}
```

## Tests

```text
library_tests/stdio/free_functions/  // every stdout/stderr overload, stdin_bytes, stdin_line
```
