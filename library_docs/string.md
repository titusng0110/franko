# franko/string

`franko/string` provides `std::String`, a dynamically sized byte string
backed by a Franko dynamic array. Strings are pointer-only: they own a
`[byte]` buffer, so they cannot be passed or returned by value.

```franko
import "franko/string"
```

Module files:

```text
import/franko/string.fr    // pure Franko, no C++ bridge
```

Design rules:

- `std::String` lives in `namespace std`, like the other standard
  modules.
- A string only stores bytes; it is not NUL-terminated.
- `length()` returns the number of stored bytes, not the internal
  growth bookkeeping.
- Strings are pointer-only because `_data` is a dynamic array. Use
  `view<String>` for read-only parameters and `ptr<String>` for
  mutation/lifecycle parameters.
- The `_data` and `_data_size` fields, the `_reserve` method, and the
  `_compare` method are private by convention (`_` prefix): direct access
  from outside `std::String` warns. Use the public methods below instead.
- Methods return `int32` status where allocation or copying can fail:
  `0` on success and `-1` on failure.
- `is_utf8_compliant()` returns `uint8`: `1` for valid UTF-8 and `0`
  otherwise.

## Storage

```franko
struct String {
    [byte] _data          // actual buffer; _data.length() is its capacity
    uint32 _data_size = 0 // number of live bytes
}
```

`_data` is a real buffer and may be larger than the current string
contents. `_data.length()` is the buffer capacity; `_data_size` is the
number of live bytes and is what `length()` returns. Bytes past
`_data_size` are unused buffer space.

The buffer size is internal and is not exposed through the public API.
`_reserve` grows the buffer when more room is needed, and clearing a
string keeps the buffer allocated for reuse. `uninit()` frees it.

A declaration starts empty: `_data` is uninitialized (capacity 0) and
`_data_size` is 0.

## API

```franko
struct String { ... }

func from(ptr<String> self, view<[byte]> in) -> int32
func from(ptr<String> self, view<String> in) -> int32

func to(view<String> self, ptr<[byte]> out) -> int32
func to(view<String> self, ptr<String> out) -> int32

func length(view<String> self) -> uint32
func is_utf8_compliant(view<String> self) -> uint8
func uninit(ptr<String> self) -> void
```

### from

```franko
func from(ptr<String> self, view<[byte]> in) -> int32
func from(ptr<String> self, view<String> in) -> int32
```

Replaces the string contents. The byte-view form accepts dynamic arrays,
static arrays (through the usual static-to-dynamic view decay), string
literals, and other byte views. The `String` form copies another string.
A null input view clears the string.

Returns `0` on success and `-1` when the backing buffer cannot be sized.

```franko
std::String s
s.from("hello")
print(s.length())      // 5

[byte] bytes
bytes.init(3)
bytes[0] = 65
bytes[1] = 66
bytes[2] = 67
s.from(getview(bytes)) // "ABC"
bytes.uninit()
```

### to

```franko
func to(view<String> self, ptr<[byte]> out) -> int32
func to(view<String> self, ptr<String> out) -> int32
```

The byte-array form copies the string into `out` and resizes `out` to the
exact string length. An empty source releases `out`. A null `out` returns
`-1`.

The `String` form replaces `out` with a copy of the source string. A null
`out` returns `-1`.

```franko
std::String s
s.from("hello")

[byte] out
s.to(getptr(out))
print(out.length())    // 5
print(getview(out))    // hello
out.uninit()

std::String copy
s.to(getptr(copy))
print(copy == s)       // 1
copy.uninit()
```

### length

```franko
func length(view<String> self) -> uint32
```

Returns the number of live bytes currently stored in the string. This is
the logical string length, not the buffer capacity. A null or empty
string reports `0`.

```franko
std::String s
print(s.length())      // 0
s.from("hello")
print(s.length())      // 5
```

### is_utf8_compliant

```franko
func is_utf8_compliant(view<String> self) -> uint8
```

Returns `1` when the stored bytes are a valid UTF-8 sequence and `0`
otherwise. A null or empty string is valid.

The check follows UTF-8 byte rules:

- ASCII bytes (`0x00`–`0x7F`), including embedded NUL, are valid;
- two-byte sequences must start with `0xC2`–`0xDF`;
- three-byte sequences must not be overlong, and UTF-16 surrogate
  encodings (`U+D800`–`U+DFFF`) are rejected;
- four-byte sequences must not be overlong and must not exceed
  `U+10FFFF`;
- stray continuation bytes, truncated sequences, and invalid lead bytes
  (`0xC0`, `0xC1`, `0xF5`–`0xFF`) are rejected.

The function validates the encoding only; it does not normalize text or
reject noncharacters.

```franko
std::String s
s.from("hello")
print(s.is_utf8_compliant())   // 1

[byte] invalid
invalid.init(2)
invalid[0] = 0xC0              // overlong two-byte lead
invalid[1] = 0x80
s.from(getview(invalid))
print(s.is_utf8_compliant())   // 0
invalid.uninit()
```

### uninit

```franko
func uninit(ptr<String> self) -> void
```

Frees the backing buffer and returns the string to the empty state. Safe
to call on an already empty string.

## Operator methods

`std::String` overloads comparison and assignment operators.

### Comparisons

| Operator | Method |
|---|---|
| `==` | `OPERATOR_EQ` |
| `!=` | `OPERATOR_NEQ` |
| `<` | `OPERATOR_LT` |
| `>` | `OPERATOR_GT` |
| `<=` | `OPERATOR_LTEQ` |
| `>=` | `OPERATOR_GTEQ` |

Comparisons are lexicographic over the stored bytes using the array
`memcmp` intrinsic. On a shared prefix, the shorter string is smaller.
Empty strings and default-constructed strings compare safely.

```franko
std::String a
std::String b
a = "abc"
b = "abd"
print(a == b)   // 0
print(a < b)    // 1
print(a <= b)   // 1
```

### Assignment and append

| Syntax | Method |
|---|---|
| `=` | `OPERATOR_MOV` |
| `+=` | `OPERATOR_ADDMOV` |
| `*=` | `OPERATOR_MULMOV` |

Available overloads:

```franko
func OPERATOR_MOV(ptr<String> self, view<[byte]> other) -> void
func OPERATOR_MOV(ptr<String> self, view<String> other) -> void
func OPERATOR_MOV(ptr<String> self, view<view<[byte]>> other) -> void

func OPERATOR_ADDMOV(ptr<String> self, view<[byte]> other) -> void
func OPERATOR_ADDMOV(ptr<String> self, view<String> other) -> void
func OPERATOR_ADDMOV(ptr<String> self, view<view<[byte]>> other) -> void

func OPERATOR_MULMOV(ptr<String> self, view<uint32> other) -> void
```

The `view<view<[byte]>>` forms exist so string literals work with `=` and
`+=`; a string literal is itself a `view<[byte, N]>` value. Byte arrays
use the `view<[byte]>` forms and other strings use the `view<String>`
forms.

`*=` repeats the string. A multiplier of `0` clears the contents, `1` is
a no-op, and larger values repeat the bytes.

```franko
std::String s
s = "ab"
s += "cd"        // "abcd"
s += s           // "abcdabcd"
s *= 3           // "abcdabcdabcdabcdabcdabcd"
print(s.length()) // 24

s *= 0
print(s.length()) // 0
```

## Example

```franko
import "franko/io"
import "franko/string"

func main() -> int32 {
    std::String greeting
    greeting.from("hello")
    greeting += " world"
    greeting *= 2

    print(greeting.length())
    print(greeting.is_utf8_compliant())

    [byte] out
    greeting.to(getptr(out))
    print(getview(out))
    out.uninit()

    std::String other
    other = greeting
    print(greeting == other)
    print(greeting != other)

    other.uninit()
    greeting.uninit()
    return 0
}
```

## Tests

```text
library_tests/string/basic/success/from_to/               // from/to overloads, views, static arrays
library_tests/string/basic/success/reserve/               // growth across 127/128-byte payloads and clearing
library_tests/string/basic/success/utf8/                  // valid and invalid UTF-8 sequences
library_tests/string/operators/success/comparisons/       // all six comparators, prefixes, empties
library_tests/string/operators/success/assign_append_multiply/ // =, +=, *=, clear
```
