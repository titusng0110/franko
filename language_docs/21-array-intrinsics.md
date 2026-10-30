# 21. Array Intrinsics

Array operations are written using call syntax but lowered into special semantic nodes. They are **not** general user-defined function calls, and only these named intrinsics are recognized.

Intrinsics are **expressions**, not statement-only forms. Status-returning intrinsics may be used in value contexts:

```franko
int32 status;
status = xs.init(10);
```

Void-returning intrinsics are invalid in value contexts.

| Intrinsic | Receiver | Returns | Status |
|---|---|---|---|
| `target(size)`, `target.init(size)` | dynamic array | `int32` | `0` on success, `-1` on allocation failure |
| `target.init_zero(size)` | dynamic array | `int32` | `0` on success, `-1` on allocation failure |
| `target.resize(size)` | dynamic array | `int32` | `0` on success, `-1` on allocation failure |
| `target.uninit()` | dynamic array | `void` | — |
| `target.memset(value)` | array of trivial elements | `void` | — |
| `target.memset(value, start, count)` | array of trivial elements | `void` | — |
| `target.memcpy(source)` | array of copiable elements | `void` | — |
| `target.memcpy(source, dstStart, srcStart, count)` | array of copiable elements | `void` | — |
| `target.memmove(dstStart, srcStart, count)` | array of copiable elements | `void` | — |
| `target.memcmp(source)` | array of copiable elements | `int32` | negative, zero, or positive |
| `target.memcmp(source, dstStart, srcStart, count)` | array of copiable elements | `int32` | negative, zero, or positive |
| `target.length()` | any array | `uint32` | — |
| `target.memchr(value)` | `[byte]` / `[byte, N]` | `uint32` | index or `0xFFFFFFFF` |
| `target.memchr(value, start, count)` | `[byte]` / `[byte, N]` | `uint32` | index or `0xFFFFFFFF` |
| `target.memmem(needle)` | `[byte]` / `[byte, N]` | `uint32` | index or `0xFFFFFFFF` |
| `target.memmem(needle, start, count)` | `[byte]` / `[byte, N]` | `uint32` | index or `0xFFFFFFFF` |
| `target.read_uint16_LE(offset)` … `target.read_int64_BE(offset)` | `[byte]` / `[byte, N]` | `uint16`/`uint32`/`uint64`/`int16`/`int32`/`int64` | — |
| `target.write_uint16_LE(offset, value)` … `target.write_int64_BE(offset, value)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.read_bit(bit_idx)` | `[byte]` / `[byte, N]` | `byte` | — |
| `target.write_bit(bit_idx, value)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.flip_bit(bit_idx)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.bitwise_not_mask()` | `[byte]` / `[byte, N]` | `void` | — |
| `target.bitwise_xor_mask(mask)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.bitwise_and_mask(mask)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.bitwise_or_mask(mask)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.shift_bits_left(bit_count)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.shift_bits_right(bit_count)` | `[byte]` / `[byte, N]` | `void` | — |
| `target.count_set_bits()` | `[byte]` / `[byte, N]` | `uint64` | — |

The receiver of a mutating array intrinsic must be a storage-backed array lvalue. The read-only intrinsics (`length`, `memcmp`, `memchr`, `memmem`, the `read_*` accessors, `read_bit`, `count_set_bits`) accept any array-typed expression.

Examples of storage-backed array targets:

```franko
arr
$(p)
arrs[i]
```

Examples that are not storage-backed array targets:

```franko
getptr(arr)
x + y
1
```

## 21.1 Receiver Permissions

The array intrinsics participate in the pointer permission model (see
[Pointers](24-pointers.md)). When a receiver is reached through a
pointer dereference, the intrinsic needs that pointer's permission:

| Intrinsic | Required receiver permission |
|---|---|
| `init`, `init_zero`, `resize`, `uninit`, `target(size)` | allocation (`ptr`) |
| `memset`, `memcpy`, `memmove` (as target), `write_*`, `write_bit`, `flip_bit`, `bitwise_*_mask`, `shift_bits_*` | mutation (`mview` or `ptr`) |
| `length()`, `memcmp`, `memchr`, `memmem`, `read_*`, `read_bit`, `count_set_bits`, and element reads | read (any permission) |

The `memcpy`/`memcmp`/`memmem` sources are only read, so they may be any
pointer to an array.

```franko
view<[byte]> v = getview(buf);
$(v)[0] = 1;         // blocked: view is read-only
$(v).memset(0);      // blocked: mutating intrinsic
$(v).memchr(65);     // allowed: read-only intrinsic
$(v).read_bit(3);    // allowed: read-only intrinsic
$(v).write_bit(3, 1); // blocked: mutating intrinsic
$(v).length();       // allowed

mview<[byte]> m = getmview(dyn);
$(m)[0] = 1;         // allowed
$(m).write_bit(3, 1); // allowed
$(m).init(10);       // blocked: init needs allocation rights (ptr)
```

## 21.2 `init`, `init_zero`, and `target(size)`

Dynamic array initialization.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must have dynamic array type;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32`; or
  - a nonconstant expression of exactly type `uint32`.

`init` allocates uninitialized element storage. `init_zero` allocates zeroed element storage. `target(size)` is shorthand for `target.init(size)`.

Element types that need construction (for example nested dynamic arrays) are default-constructed by `init` and `init_zero`, so inner arrays start in the uninitialized state (length `0`), ready for `init`; trivially constructible element types keep raw (`init`) or zeroed (`init_zero`) storage with no per-element work.

## 21.3 `resize`

Resizes an already-initialized dynamic array. Growing default-constructs the new tail elements; shrinking destroys the dropped tail elements. Trivial element types skip both.

Rules:

- available only on dynamic arrays;
- target must be a storage-backed lvalue;
- target must be a dynamic array;
- exactly one argument is required;
- size must be:
  - a positive constant fitting `uint32`; or
  - a nonconstant expression of exactly type `uint32`.

## 21.4 `uninit`

Frees a dynamic array's element storage and resets it to the uninitialized state. Constructed elements are destroyed before the buffer is freed; trivially destructible element types skip destruction.

Rules:

- receiver must be a storage-backed lvalue;
- receiver must be a dynamic array;
- takes exactly zero arguments.

## 21.5 `memset`

Fills an array byte-wise.

Forms:

```franko
target.memset(value)                    // whole array
target.memset(value, start, count)      // element range
```

Rules:

- receiver must be a storage-backed lvalue;
- receiver may be a dynamic or static array;
- receiver element type must be trivial;
- the whole-array form takes exactly one argument; the ranged form takes exactly three;
- fill value must be:
  - a constant fitting `uint8`; or
  - a nonconstant expression of exactly type `uint8`;
- `start` and `count` must be:
  - nonnegative constants fitting `uint32`; or
  - nonconstant expressions of exactly type `uint32`;
- for static arrays, constant start/count ranges are checked against the static size at compile time.

## 21.6 Trivial Element Types

Current trivial types:

| Element type | Trivial? |
|---|---|
| primitive integers | yes |
| static arrays of trivial elements | yes |
| structs whose fields are all trivial, recursively | yes |
| dynamic arrays | no |
| pointers | no |

## 21.7 `memcpy`

Copies array contents byte-wise according to the generated C++ array model.

Forms:

```franko
target.memcpy(source)                          // whole array
target.memcpy(source, dstStart, srcStart, count)  // element ranges
```

Rules:

- target must be a storage-backed lvalue array;
- source must be an **pointer to an array** (`ptr<[T]>` or `ptr<[T, N]>`), typically produced with `getptr(...)`, or an array literal (its element type is inferred from the target); passing an array value directly is invalid;
- both target and source may be dynamic or static arrays;
- target and source element types must match exactly;
- static/dynamic shape does not need to match;
- static lengths do not need to match;
- target and source element types must be copiable;
- `dstStart`, `srcStart`, and `count` must be:
  - nonnegative constants fitting `uint32`; or
  - nonconstant expressions of exactly type `uint32`;
- for static arrays, constant start/count ranges on both the destination and the source are checked against the static sizes at compile time.

The whole-array form copies the smaller of the two lengths; bytes past the
destination's length are never written.

## 21.8 `memmove`

Copies an element range within one array, overlapping ranges included.

Form:

```franko
target.memmove(dstStart, srcStart, count)
```

Rules:

- receiver must be a storage-backed lvalue array (dynamic or static);
- takes exactly three arguments;
- receiver element type must be copiable;
- `dstStart`, `srcStart`, and `count` must be:
  - nonnegative constants fitting `uint32`; or
  - nonconstant expressions of exactly type `uint32`;
- for static arrays, constant ranges are checked against the static size at compile time.

## 21.9 Copiable Element Types

Current copiable types:

| Element type | Copiable? |
|---|---|
| primitive integers | yes |
| pointers | yes |
| static arrays of copiable elements | yes |
| structs whose fields are all copiable, recursively | yes |
| dynamic arrays | no |

## 21.10 `length`

Returns the element count of an array as `uint32`.

Form:

```franko
target.length()
```

Rules:

- takes exactly zero arguments;
- receiver must have array type (dynamic or static);
- `length()` is read-only: unlike the mutating intrinsics, the receiver does not need to be a storage-backed lvalue, and calling it never initializes, modifies, or uninitializes the array;
- a struct method named `length` still dispatches as a method: struct-typed receivers are never treated as array intrinsics.

Dynamic arrays report their runtime length. A dynamic array that has never been initialized, or that has been uninitialized with `uninit()`, reports `0`; `length()` never aborts:

```franko
[int32] xs;
print(xs.length());   // 0
xs.init(4);
print(xs.length());   // 4
xs.resize(10);
print(xs.length());   // 10
xs.uninit();
print(xs.length());   // 0
```

Static arrays report their size as a compile-time constant: `ys.length()` for `[int32, 3] ys` compiles to the constant `3`, with no runtime read.

Nested receivers report the outermost dimension; index first for inner dimensions:

```franko
[[int32, 2], 3] m;
print(m.length());      // 3
print(m[0].length());   // 2
```

`length()` returns a value, so it may be used in any value context: arithmetic, loop bounds, assignments, and `print`.

## 21.11 `memcmp`

Compares array contents byte-wise. The receiver and the source must have
identical element types, and that element type must be copiable.

Forms:

```franko
target.memcmp(source)                             // whole arrays
target.memcmp(source, dstStart, srcStart, count)  // element ranges
```

Rules:

- receiver may be a dynamic or static array; it does not need to be a storage-backed lvalue;
- source must be a **pointer to an array** (`ptr<[T]>`, `mview<[T]>`, `view<[T]>`, or the static-array forms), typically produced with `getptr(...)`, `getmview(...)`, or `getview(...)`, or an array literal whose element type is inferred from the receiver;
- both receiver and source element types must be copiable;
- `dstStart`, `srcStart`, and `count` must be:
  - nonnegative constants fitting `uint32`; or
  - nonconstant expressions of exactly type `uint32`;
- for static arrays, constant ranges on both sides are checked at compile time.

The result is `int32`, following the usual byte-comparison convention: a
negative value when the receiver compares less, zero when the compared
bytes are equal, and a positive value when the receiver compares greater.
The sign is what is guaranteed, not a specific magnitude. The whole-array
form compares the shared prefix and then orders by length (the shorter
array is less when the shared prefix matches); the ranged form compares
exactly `count` elements.

```franko
[int32, 3] a
[int32, 3] b
a.memset(0);
b.memset(0);
if (a.memcmp(getptr(b)) == 0) {
    print("equal");
}
```

## 21.12 Byte-Array Intrinsics

The remaining intrinsics exist only for byte arrays: receivers whose element
type is `byte` (`uint8`). Both `[byte]` and `[byte, N]` (dynamic and static)
are accepted; any other element type is a compile error.

All byte offsets are zero-based. Bounds violations, use of an uninitialized
dynamic array, and other invalid accesses abort at runtime through the same
panic mechanism as array indexing and the other intrinsics. For static arrays,
constant offsets are also checked at compile time.

### `memchr` and `memmem`

Find a byte or a byte sequence inside the receiver.

Forms:

```franko
target.memchr(value)                  // search the whole array
target.memchr(value, start, count)    // search a range
target.memmem(needle)                 // search the whole array
target.memmem(needle, start, count)   // search a range
```

Rules:

- receiver element type must be `byte`;
- `value` must be `byte`;
- `needle` must be a pointer to a byte array (`ptr`/`mview`/`view` over
  `[byte]` or `[byte, N]`), an array literal, or a string literal (strings
  are already `view<[byte, N]>` values);
- `start` and `count` must be nonnegative constants fitting `uint32`, or
  nonconstant expressions of exactly type `uint32`; static ranges are
  checked at compile time;
- both return `uint32`: the **absolute** element index of the first match,
  or `0xFFFFFFFF` when there is no match;
- `memchr` on an empty range returns `0xFFFFFFFF`; an empty needle matches
  at `start`.

```franko
[byte, 5] hay
hay.memset(0);
hay[1] = 65;
hay[2] = 66;
hay[3] = 66;
print(hay.memchr(65));          // 1
print(hay.memchr(67));          // 4294967295
print(hay.memmem("BB"));        // 2
```

### Endian integer accessors

Read or write a fixed-width integer at a byte offset with an explicit byte
order.

Forms:

```franko
target.read_uint16_LE(offset)          // -> uint16
target.read_uint16_BE(offset)          // -> uint16
target.read_uint32_LE(offset)          // -> uint32
target.read_uint32_BE(offset)          // -> uint32
target.read_uint64_LE(offset)          // -> uint64
target.read_uint64_BE(offset)          // -> uint64
target.read_int16_LE(offset)           // -> int16
target.read_int16_BE(offset)           // -> int16
target.read_int32_LE(offset)           // -> int32
target.read_int32_BE(offset)           // -> int32
target.read_int64_LE(offset)           // -> int64
target.read_int64_BE(offset)           // -> int64

target.write_uint16_LE(offset, value)  // value: uint16
target.write_uint16_BE(offset, value)
target.write_uint32_LE(offset, value)  // value: uint32
target.write_uint32_BE(offset, value)
target.write_uint64_LE(offset, value)  // value: uint64
target.write_uint64_BE(offset, value)
target.write_int16_LE(offset, value)   // value: int16
target.write_int16_BE(offset, value)
target.write_int32_LE(offset, value)   // value: int32
target.write_int32_BE(offset, value)
target.write_int64_LE(offset, value)   // value: int64
target.write_int64_BE(offset, value)
```

Rules:

- receiver element type must be `byte`;
- `offset` is a byte offset and must be a nonnegative constant fitting
  `uint32`, or a nonconstant expression of exactly type `uint32`;
- the access must fit: `offset + width <= target.length()`; otherwise the
  call aborts. For static arrays, a constant offset is checked at compile
  time;
- `value` must be exactly the accessor's integer type, or a constant that
  fits it;
- `LE` stores/loads the least significant byte first, `BE` the most
  significant byte first;
- reads and writes are independent unaligned byte loads/stores, so no
  alignment requirement applies.

```franko
[byte, 8] header
header.write_uint32_LE(0, 0x12345678);
print(header.read_uint32_LE(0));   // 305419896
print(header.read_uint32_BE(0));   // 2018915346

header.write_int16_BE(4, -2);
print(header.read_int16_BE(4));    // -2
```

### Bit intrinsics

Operate on the receiver as one long little-endian bit string: bit `i` lives
in byte `i / 8` at bit position `i % 8` counted from the least significant
bit. A valid bit index satisfies `bit_idx < 8 * target.length()`.

Forms:

```franko
target.read_bit(bit_idx)               // -> byte (0 or 1)
target.write_bit(bit_idx, value)       // value: byte
target.flip_bit(bit_idx)
target.bitwise_not_mask()
target.bitwise_xor_mask(mask)          // mask: byte
target.bitwise_and_mask(mask)          // mask: byte
target.bitwise_or_mask(mask)           // mask: byte
target.shift_bits_left(bit_count)      // bit_count: uint64
target.shift_bits_right(bit_count)     // bit_count: uint64
target.count_set_bits()                // -> uint64
```

Rules:

- receiver element type must be `byte`;
- `bit_idx` and `bit_count` must be nonnegative constants fitting `uint64`,
  or nonconstant expressions of exactly type `uint64`;
- `read_bit` returns `0` or `1`; a nonzero `write_bit` value sets the bit,
  zero clears it;
- `flip_bit` toggles one bit;
- `bitwise_not_mask` toggles every bit and `bitwise_*_mask` applies the
  given byte mask to every byte of the array;
- `shift_bits_left`/`shift_bits_right` shift the whole bit string toward
  more/less significant bit positions, filling vacated bits with zero;
  shifting by at least `8 * target.length()` bits zeroes the whole array;
- `count_set_bits` returns the total number of set bits as `uint64`;
- `read_bit`, `write_bit`, and `flip_bit` abort when `bit_idx` is out of
  range. For static arrays, a constant bit index is checked at compile
  time;
- the mutating bit intrinsics need mutation rights on the receiver;
  `read_bit` and `count_set_bits` are read-only and may be called through a
  `view`.

```franko
[byte, 2] flags
flags.memset(0);
flags.write_bit(0, 1);
flags.write_bit(9, 1);
print(flags.count_set_bits());   // 2
print(flags.read_bit(1));        // 0
flags.flip_bit(1);
print(flags.read_bit(1));        // 1
flags.shift_bits_left(1);
print(flags.read_bit(10));       // 1
```
