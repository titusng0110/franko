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
- `_data.length()` is the current string length. `_buffer_size` is the
  tracked growth capacity.
- Strings are pointer-only because `_data` is a dynamic array. Use
  `view<String>` for read-only parameters and `ptr<String>` for
  mutation/lifecycle parameters.
- The `_data` and `_buffer_size` fields are private by convention
  (`_` prefix): direct access from outside `std::String` warns. Use the
  methods below instead.
- Methods return `int32` status where allocation or copying can fail:
  `0` on success and `-1` on failure.

## Storage

```franko
struct String {
    [byte] _data
    uint32 _buffer_size = 0
}
```

`_data` always contains exactly the current bytes. `_buffer_size` is the
tracked capacity target used by growth:

- `from` starts the tracked capacity at `128` bytes when the payload is
  smaller than 128;
- when the payload is 128 bytes or larger, the tracked capacity becomes
  `payload length + 128`;
- appending and multiplying raise the tracked capacity the same way when
  the result no longer fits;
- clearing a string keeps its tracked capacity (at least 128);
- `uninit()` frees `_data` and resets the tracked capacity to 0.

A declaration starts empty: `_data` is uninitialized (length 0) and
`_buffer_size` is 0.

## API

```franko
struct String { ... }

func from(ptr<String> self, view<[byte]> in) -> int32
func from(ptr<String> self, view<String> in) -> int32

func to(view<String> self, ptr<[byte]> out) -> int32
func to(view<String> self, ptr<String> out) -> int32

func reserve(ptr<String> self, uint32 payload) -> int32

func length(view<String> self) -> uint32
func capacity(view<String> self) -> uint32
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

### reserve

```franko
func reserve(ptr<String> self, uint32 payload) -> int32
```

Prepares `_data` for a payload of exactly `payload` bytes and raises
`_buffer_size` to the tracked growth target. It is the allocation step
used by `from`, `+=`, and `*=`. Calling it directly resizes the backing
array, so on a non-empty string it changes the logical length as well.
`reserve(0)` releases `_data` while keeping the tracked capacity at least
128.

Returns `0` on success and `-1` when the backing array cannot be sized.

### length and capacity

```franko
func length(view<String> self) -> uint32
func capacity(view<String> self) -> uint32
```

`length()` returns `_data.length()`, the number of stored bytes.
`capacity()` returns the tracked `_buffer_size` value.

```franko
std::String s
print(s.length())      // 0
print(s.capacity())    // 0
s.from("hello")
print(s.length())      // 5
print(s.capacity())    // 128
```

### uninit

```franko
func uninit(ptr<String> self) -> void
```

Frees the backing buffer and resets `_buffer_size` to 0. Safe to call on
an already empty string.

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
a no-op, and larger values repeat the bytes. The tracked capacity is
raised when the repeated result needs more room.

```franko
std::String s
s = "ab"
s += "cd"        // "abcd"
s += s           // "abcdabcd"
s *= 3           // "abcdabcdabcdabcdabcdabcd"
print(s.length()) // 24

s *= 0
print(s.length()) // 0
print(s.capacity()) // 128
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
    print(greeting.capacity())

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
library_tests/string/basic/success/reserve/               // 128-byte default and payload + 128 growth
library_tests/string/operators/success/comparisons/       // all six comparators, prefixes, empties
library_tests/string/operators/success/assign_append_multiply/ // =, +=, *=, clear
```
