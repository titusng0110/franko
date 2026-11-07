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
  `view<String>` for read-only parameters, `mview<String>` for in-place
  mutation such as `clear`, `set`, and `erase`, and `ptr<String>` for
  allocation/lifecycle operations.
- The `_data` and `_data_size` fields, the `_reserve` method, and the
  `_compare` method are private by convention (`_` prefix): direct access
  from outside `std::String` warns. Use the public methods below instead.
- `StringList`'s `_data` and `_data_size` fields are private for the same
  reason: direct access from outside `std::StringList` warns.
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
An empty string allocates 64 bytes on first use; when the buffer is
full it grows to the next power of two (`64`, `128`, `256`, ...). This
also applies after `shrink_to_fit()`: an odd exact-fit capacity such as
41 returns to the ladder with a capacity of 64 on the next growth. The
buffer is never shrunk by `from`, `clear`, or `erase`. `clear()` resets
the length to 0 and keeps the buffer allocated for reuse;
`shrink_to_fit()` resizes it to the live length (freeing it when the
string is empty); `uninit()` frees it too.

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
func clear(mview<String> self) -> void
func shrink_to_fit(ptr<String> self) -> int32
func uninit(ptr<String> self) -> void

func append(ptr<String> self, byte payload) -> int32
func append(ptr<String> self, view<[byte]> payload) -> int32
func append(ptr<String> self, view<String> payload) -> int32

func from_integer(ptr<String> self, int8 value) -> int32
func from_integer(ptr<String> self, int16 value) -> int32
func from_integer(ptr<String> self, int32 value) -> int32
func from_integer(ptr<String> self, int64 value) -> int32
func from_integer(ptr<String> self, uint8 value) -> int32
func from_integer(ptr<String> self, uint16 value) -> int32
func from_integer(ptr<String> self, uint32 value) -> int32
func from_integer(ptr<String> self, uint64 value) -> int32

func to_int8(view<String> self) -> (int8 val, int32 err)
func to_int16(view<String> self) -> (int16 val, int32 err)
func to_int32(view<String> self) -> (int32 val, int32 err)
func to_int64(view<String> self) -> (int64 val, int32 err)
func to_uint8(view<String> self) -> (uint8 val, int32 err)
func to_uint16(view<String> self) -> (uint16 val, int32 err)
func to_uint32(view<String> self) -> (uint32 val, int32 err)
func to_uint64(view<String> self) -> (uint64 val, int32 err)

func find(view<String> self, byte needle) -> (uint32 idx, int32 err)
func find(view<String> self, view<[byte]> substring) -> (uint32 idx, int32 err)
func find(view<String> self, view<String> substring) -> (uint32 idx, int32 err)

func find_next(view<String> self, byte needle, uint32 start_idx) -> (uint32 idx, int32 err)
func find_next(view<String> self, view<[byte]> substring, uint32 start_idx) -> (uint32 idx, int32 err)
func find_next(view<String> self, view<String> substring, uint32 start_idx) -> (uint32 idx, int32 err)

func count(view<String> self, byte needle) -> uint32
func count(view<String> self, view<[byte]> substring) -> uint32
func count(view<String> self, view<String> substring) -> uint32

func count_from(view<String> self, byte needle, uint32 start_idx) -> uint32
func count_from(view<String> self, view<[byte]> substring, uint32 start_idx) -> uint32
func count_from(view<String> self, view<String> substring, uint32 start_idx) -> uint32

func get(view<String> self, uint32 idx) -> byte
func set(mview<String> self, uint32 idx, byte value) -> void

func get_slice(view<String> self, uint32 start, uint32 end, ptr<String> out) -> int32
func get_slice(view<String> self, uint32 start, uint32 end, ptr<[byte]> out) -> int32

func insert(ptr<String> self, byte payload, uint32 idx) -> int32
func insert(ptr<String> self, view<[byte]> payload, uint32 idx) -> int32
func insert(ptr<String> self, view<String> payload, uint32 idx) -> int32

func erase(mview<String> self, uint32 idx) -> int32
func erase(mview<String> self, uint32 start, uint32 end) -> int32

func split(view<String> self, byte splitter, ptr<StringList> out) -> int32
func split(view<String> self, view<[byte]> splitter, ptr<StringList> out) -> int32
func split(view<String> self, view<String> splitter, ptr<StringList> out) -> int32

func join(ptr<String> self, view<StringList> in) -> int32
func join(ptr<String> self, byte joiner, view<StringList> in) -> int32
func join(ptr<String> self, view<[byte]> joiner, view<StringList> in) -> int32
func join(ptr<String> self, view<String> joiner, view<StringList> in) -> int32
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

### clear

```franko
func clear(mview<String> self) -> void
```

Resets the logical string length to 0 while keeping the backing buffer
allocated. The next `from`, `append`, or other content operation reuses
that buffer, so repeatedly clearing and refilling a string does not
reallocate. Use `uninit()` to release the buffer instead.

```franko
std::String s
s.from("hello")
print(s.length())   // 5
s.clear()
print(s.length())   // 0
s.from("reuse")     // reuses the existing buffer
print(getview(s))   // reuse
```

### shrink_to_fit

```franko
func shrink_to_fit(ptr<String> self) -> int32
```

Resizes the backing buffer to the live string length, releasing unused
capacity. The resulting capacity may be any number, including an odd
one; the next growth rounds back up to the power-of-two ladder (64,
128, 256, ...). An empty string frees the buffer entirely. Returns `0`
on success and `-1` when the resize fails.

```franko
std::String s
s.from("hello")
s.append(" world")
s.erase(5, 11)      // "hello", buffer still has room
print(s.shrink_to_fit())   // 0: buffer trimmed back to 5 bytes
print(getview(s))          // hello
s.clear()
print(s.shrink_to_fit())   // 0: empty string frees its buffer
print(s.length())          // 0
```

### append

```franko
func append(ptr<String> self, byte payload) -> int32
func append(ptr<String> self, view<[byte]> payload) -> int32
func append(ptr<String> self, view<String> payload) -> int32
```

Appends one byte, a byte view, or another string. Returns `0` on success
and `-1` on overflow or allocation failure. A null or empty payload is a
successful no-op. Appending the string's own buffer or the string itself
is handled correctly.

The `+=` operator calls `append` and ignores its status.

```franko
std::String s
s.from("ab")
s.append(99)          // "abc"
s.append("de")        // "abcde"
s += "fg"             // "abcdefg"
```

### from_integer

```franko
func from_integer(ptr<String> self, int8 value) -> int32
func from_integer(ptr<String> self, int16 value) -> int32
func from_integer(ptr<String> self, int32 value) -> int32
func from_integer(ptr<String> self, int64 value) -> int32
func from_integer(ptr<String> self, uint8 value) -> int32
func from_integer(ptr<String> self, uint16 value) -> int32
func from_integer(ptr<String> self, uint32 value) -> int32
func from_integer(ptr<String> self, uint64 value) -> int32
```

Replaces the string with the decimal representation of an integer.
Signed negative values include a leading `-`; zero becomes `"0"`.
Returns `0` on success and `-1` when the buffer cannot grow. The
existing buffer is reused.

```franko
std::String s
int32 n = -123
s.from_integer(n)
print(getview(s))   // -123

uint64 big = 18446744073709551615
s.from_integer(big)
print(getview(s))   // 18446744073709551615
```

### to_int8, to_int16, to_int32, to_int64, to_uint8, to_uint16, to_uint32, to_uint64

```franko
func to_int8(view<String> self) -> (int8 val, int32 err)
func to_int16(view<String> self) -> (int16 val, int32 err)
func to_int32(view<String> self) -> (int32 val, int32 err)
func to_int64(view<String> self) -> (int64 val, int32 err)
func to_uint8(view<String> self) -> (uint8 val, int32 err)
func to_uint16(view<String> self) -> (uint16 val, int32 err)
func to_uint32(view<String> self) -> (uint32 val, int32 err)
func to_uint64(view<String> self) -> (uint64 val, int32 err)
```

Parses the string as an integer. Decimal, `0b`/`0B` binary, and
`0x`/`0X` hexadecimal forms are accepted. Decimal input may start with
one `-`; a leading `+` is not part of Franko's grammar and is rejected.
Binary and hexadecimal input must not have a sign. Leading zeros are
allowed. Whitespace, underscores, extra signs, and trailing characters
are rejected. An empty or null string returns `(0, -1)`.

Returns `(val, err)`: `err` is `0` on success and `-1` on invalid input
or overflow; `val` is `0` on failure. The target type's range is
checked, including signed minimum values. For unsigned targets, a
negative decimal value is rejected unless its magnitude is zero.

```franko
std::String s
s.from("-123")
(int32 val, int32 err) n = s.to_int32()
print(n.val)    // -123
print(n.err)    // 0

s.from("0x7F")
(uint8 byte_val, int32 byte_err) b = s.to_uint8()
print(b.val)    // 127
print(b.err)    // 0

s.from("-0x7F")
b = s.to_uint8()
print(b.val)    // 0
print(b.err)    // -1: signs are not allowed on hexadecimal input
```

### find, find_next, count, count_from

```franko
func find(view<String> self, byte needle) -> (uint32 idx, int32 err)
func find(view<String> self, view<[byte]> substring) -> (uint32 idx, int32 err)
func find(view<String> self, view<String> substring) -> (uint32 idx, int32 err)

func find_next(view<String> self, byte needle, uint32 start_idx) -> (uint32 idx, int32 err)
func find_next(view<String> self, view<[byte]> substring, uint32 start_idx) -> (uint32 idx, int32 err)
func find_next(view<String> self, view<String> substring, uint32 start_idx) -> (uint32 idx, int32 err)

func count(view<String> self, byte needle) -> uint32
func count(view<String> self, view<[byte]> substring) -> uint32
func count(view<String> self, view<String> substring) -> uint32

func count_from(view<String> self, byte needle, uint32 start_idx) -> uint32
func count_from(view<String> self, view<[byte]> substring, uint32 start_idx) -> uint32
func count_from(view<String> self, view<String> substring, uint32 start_idx) -> uint32
```

`find` returns the first occurrence. `find_next` starts at `start_idx`
(inclusive). Both return `(idx, err)`: `err` is `0` when found and `-1`
when not found; `idx` is `0` on failure. An empty or null substring
matches at index `0` for `find` and at `start_idx` for `find_next`; a
`start_idx` greater than the string length reports not found.

`count` counts occurrences; `count_from` starts at `start_idx`. Substring
counts include overlapping occurrences. An empty or null substring occurs
`length() + 1` times from index `0` (one occurrence per position,
including the end).

```franko
std::String s
s.from("a,b,,c,")
(uint32 idx, int32 err) hit = s.find(44)       // (1, 0)
hit = s.find("c,")                             // (5, 0)
hit = s.find("zz")                             // (0, -1)
print(s.count(44))                             // 4
print(s.count_from(44, 4))                     // 2
```

### get and set

```franko
func get(view<String> self, uint32 idx) -> byte
func set(mview<String> self, uint32 idx, byte value) -> void
```

`get` returns the byte at `idx`, or `0` for a null string or an
out-of-range index. `set` writes the byte at `idx`; a null string or an
out-of-range index is ignored. Callers that need strict bounds checking
should test `length()` first.

```franko
std::String s
s.from("abc")
print(s.get(1))    // 98
s.set(1, 66)       // "aBc"
```

### get_slice

```franko
func get_slice(view<String> self, uint32 start, uint32 end, ptr<String> out) -> int32
func get_slice(view<String> self, uint32 start, uint32 end, ptr<[byte]> out) -> int32
```

Copies `[start, end)` (`end` exclusive) into `out`, completely replacing
its contents. Returns `0` on success and `-1` for a null `out`, an
invalid range, or an allocation failure. An empty slice clears `out`.
Slicing a string into itself is safe.

```franko
std::String s
s.from("abcdef")
std::String part
s.get_slice(1, 4, getptr(part))   // "bcd"
[byte] bytes
s.get_slice(2, 5, getptr(bytes))  // "cde"
bytes.uninit()
part.uninit()
```

### insert and erase

```franko
func insert(ptr<String> self, byte payload, uint32 idx) -> int32
func insert(ptr<String> self, view<[byte]> payload, uint32 idx) -> int32
func insert(ptr<String> self, view<String> payload, uint32 idx) -> int32

func erase(mview<String> self, uint32 idx) -> int32
func erase(mview<String> self, uint32 start, uint32 end) -> int32
```

`insert` inserts before `idx`; valid indices are `0` through
`length()` inclusive. Returns `0` on success and `-1` for an invalid
index, overflow, or allocation failure. Inserting an empty payload at a
valid index is a successful no-op.

`erase` removes the byte at `idx`, or the range `[start, end)`.
Returns `0` on success and `-1` for an invalid index or range.

```franko
std::String s
s.from("abcd")
s.insert(45, 2)        // "ab-cd"
s.insert("XY", 0)      // "XYab-cd"
s.erase(0)             // "Yab-cd"
s.erase(1, 3)          // "Y-cd"
```

### split and join

```franko
func split(view<String> self, byte splitter, ptr<StringList> out) -> int32
func split(view<String> self, view<[byte]> splitter, ptr<StringList> out) -> int32
func split(view<String> self, view<String> splitter, ptr<StringList> out) -> int32

func join(ptr<String> self, view<StringList> in) -> int32
func join(ptr<String> self, byte joiner, view<StringList> in) -> int32
func join(ptr<String> self, view<[byte]> joiner, view<StringList> in) -> int32
func join(ptr<String> self, view<String> joiner, view<StringList> in) -> int32
```

`split` completely replaces the live contents of `out` with the
segments between splitter occurrences; the list buffer is reused.
Leading, trailing, and consecutive delimiters produce empty segments. An empty or null splitter does not split: the whole string
becomes the only segment. Returns `0` on success and `-1` for a null
`out` or an allocation failure.

`join` completely replaces `self` with the elements of `in`, separated
by the joiner. The no-joiner form concatenates directly. A null list
clears `self`; a null or empty joiner adds no separator. Returns `0` on
success and `-1` on overflow or allocation failure. Joining a list that
contains `self` is safe.

```franko
std::String text
text.from("a,b,,c,")
std::StringList parts
text.split(44, getptr(parts))       // ["a", "b", "", "c", ""]

std::String joined
joined.join(44, getview(parts))     // "a,b,,c,"
joined.join(getview(parts))         // "abc"
```

## StringList

```franko
struct StringList {
    [String] _data         // buffer; _data.length() is capacity
    uint32 _data_size = 0  // number of live elements
}
```

`StringList` is a dynamic array of `String` values with a reusable
buffer. `_data.length()` is the buffer capacity; `_data_size` is the
live element count and is what `length()` returns. Like `String`, both
fields are private by convention (`_` prefix): direct access from
outside `std::StringList` warns. An empty list allocates
64 element slots on first use; when the buffer is full it grows to the
next power of two (`64`, `128`, `256`, ...). This also applies after
`shrink_to_fit()` leaves an odd exact-fit capacity. The buffer is never
shrunk by `from`, `clear`, or `erase`, so repeated appends do not
reallocate on every call. `shrink_to_fit()` resizes it to the live
element count (freeing it when the list is empty).

Every live element owns its own String buffer. `from` replaces the live
contents and keeps the list buffer; `clear` resets every live element in
place, keeping both the list buffer and each element's String buffer for
reuse; `uninit` frees every live element and the buffer.

`StringList` is pointer-only for the same reason as `String`: it
contains a dynamic array. Use `view<StringList>` for read-only
parameters, `mview<StringList>` for in-place mutation such as `clear`,
and `ptr<StringList>` for allocation/lifecycle operations.

Passing a null view to `from` releases the live elements and keeps the
list buffer for reuse. `clear()` does the same without needing a null
view, and also keeps each element's String buffer for reuse. Call
`uninit()` to release everything.

### StringList API

```franko
struct StringList { ... }

func length(view<StringList> self) -> uint32
func clear(mview<StringList> self) -> void
func shrink_to_fit(ptr<StringList> self) -> int32
func uninit(ptr<StringList> self) -> void

func from(ptr<StringList> self, view<StringList> in) -> int32

func append(ptr<StringList> self, view<[byte]> in) -> int32
func append(ptr<StringList> self, view<String> in) -> int32

func OPERATOR_ADDMOV(ptr<StringList> self, view<[byte]> other) -> void
func OPERATOR_ADDMOV(ptr<StringList> self, view<String> other) -> void
func OPERATOR_ADDMOV(ptr<StringList> self, view<view<[byte]>> other) -> void

func find(view<StringList> self, view<String> needle) -> (uint32 idx, int32 err)
func find(view<StringList> self, view<[byte]> needle) -> (uint32 idx, int32 err)
func find_next(view<StringList> self, view<String> needle, uint32 start_idx) -> (uint32 idx, int32 err)
func find_next(view<StringList> self, view<[byte]> needle, uint32 start_idx) -> (uint32 idx, int32 err)

func count(view<StringList> self, view<String> needle) -> uint32
func count(view<StringList> self, view<[byte]> needle) -> uint32
func count_from(view<StringList> self, view<String> needle, uint32 start_idx) -> uint32
func count_from(view<StringList> self, view<[byte]> needle, uint32 start_idx) -> uint32

func get(view<StringList> self, uint32 idx, ptr<String> out) -> int32
func get(view<StringList> self, uint32 idx, ptr<[byte]> out) -> int32

func set(ptr<StringList> self, uint32 idx, view<String> value) -> int32
func set(ptr<StringList> self, uint32 idx, view<[byte]> value) -> int32

func insert(ptr<StringList> self, view<String> payload, uint32 idx) -> int32
func insert(ptr<StringList> self, view<[byte]> payload, uint32 idx) -> int32

func erase(ptr<StringList> self, uint32 idx) -> int32
func erase(ptr<StringList> self, uint32 start, uint32 end) -> int32
```

### StringList methods

- `length()` returns `_data_size`, or `0` for a null list.
- `clear()` clears every live element in place and resets `_data_size`
  to 0, keeping both the list's backing buffer and each element's
  String buffer allocated for reuse.
- `shrink_to_fit()` resizes the backing buffer to `_data_size`, releasing
  unused element slots. The capacity may become odd; the next growth
  returns to the power-of-two ladder. An empty list frees the buffer
  entirely. Returns `0` on success and `-1` when the resize fails.
- `uninit()` frees every live element and the backing buffer, returning
  the list to the empty unallocated state.
- `from` replaces every live element with a copy of another list's live
  elements and keeps the buffer. A null input clears the list. Passing
  the list's own view is a no-op.
- `append` adds one element at the end, either from a byte array or
  from another string. The `+=` operator calls `append` and ignores its
  status. Use `append` for individual elements; use `from` to copy a
  whole list.
- `find`, `find_next`, `count`, and `count_from` compare whole elements,
  not substrings. They use the same `(idx, err)` convention as String:
  `err` is `0` when found and `-1` otherwise; `idx` is `0` on failure.
- `get` copies element `idx` into `out`, completely replacing it.
  Returns `0` on success and `-1` for a null destination or an
  out-of-range index.
- `set` replaces element `idx` with a copy of `value`.
- `insert` inserts a new element before `idx`; valid indices are `0`
  through `length()` inclusive.
- `erase` removes one element or the range `[start, end)` and uninitializes
  the dropped elements; the buffer is kept.
- `StringList` has no `get_slice`.

```franko
std::StringList list
list.append("aa")
list.append("bb")
list.append("aa")

print(list.length())          // 3
print(list.count("aa"))       // 2

(uint32 idx, int32 err) hit = list.find_next("aa", 1)
print(hit.idx)                // 2
print(hit.err)                // 0

std::String out
list.get(1, getptr(out))      // "bb"
out.uninit()

list.set(1, "cc")
list.insert("dd", 2)
list.erase(0)
print(list.length())          // 3

list += "ee"                  // append through OPERATOR_ADDMOV
print(list.length())          // 4

list.clear()                  // release live elements, keep buffer
print(list.length())          // 0
list.uninit()                 // release the buffer
```

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
forms. An existing buffer is reused; when it is too small it grows to
the next power of two.

`*=` repeats the string. A multiplier of `0` clears the contents while
keeping the buffer, `1` is a no-op, and larger values repeat the bytes.

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
library_tests/string/basic/success/reserve/               // 63/64/65-byte growth boundaries, clear/reuse, shrink_to_fit
library_tests/string/basic/success/utf8/                  // valid and invalid UTF-8 sequences
library_tests/string/operators/success/comparisons/       // all six comparators, prefixes, empties
library_tests/string/operators/success/assign_append_multiply/ // =, +=, *=, clear
library_tests/string/methods/success/from_integer/        // all eight integer widths, signs, min/max
library_tests/string/methods/success/to_integer/          // decimal/binary/hex parsing, minus signs, overflow
library_tests/string/methods/success/find_count/          // find/find_next/count/count_from, all needle forms
library_tests/string/methods/success/slice_edit/          // append/get_slice/insert/erase, aliasing, bounds
library_tests/string/methods/success/split_join/          // split/join, empty and aliased separators
library_tests/string/stringlist/success/basic/            // StringList from/append/find/get/set/insert/erase/clear/shrink/odd-regrow
```
