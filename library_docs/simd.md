# franko/simd

Architecture-specific byte-array SIMD operations backed by native C++
intrinsics.

```franko
import "franko/simd"
```

Module files:

```text
import/franko/simd.fr
import/franko/simd.module.json
import/franko/simd.ffi.cpp
```

> ## Design philosophy: native instructions, not a portable SIMD layer
>
> `franko/simd` exists to make the actual x86_64 intrinsics and ARM64 NEON
> intrinsics directly usable from Franko code. It is a thin bridge with
> buffer plumbing, bounds checks, and scalar fallbacks. It is **not** a
> unified cross-platform SIMD API, and it does **not** try to make x86 and
> ARM behave the same when the underlying instructions do not.
>
> Consequences:
>
> - x86 functions follow the Intel intrinsic's operation and operand meaning.
> - ARM functions follow the NEON intrinsic's operation and operand meaning.
> - A paired operation can differ between the two architectures. Read the
>   per-function sections; do not assume that `simd::arm::x` is a drop-in
>   replacement for `simd::x86::y`.
> - The shared part is only the Franko calling shape: mutable `target` first,
>   read-only inputs next, then element offsets and `count`, plus range
>   checking outside `-Omax`.
> - If code must run on both architectures, write two explicit branches with
>   `#if (#IS_X86_64)` and `#if (#IS_ARM64)` and honor each architecture's
>   documented contract.
> - Where the instruction has a fixed immediate, the wrapper performs a
>   dispatch. Where the instruction is unavailable, the bridge uses a scalar
>   fallback or returns the documented error code.
>
> The most important differences are collected in
> [Cross-architecture notes](#cross-architecture-notes): the shuffle index
> rules, `blendv_epi8` versus `vbslq_u8`, and the opposite operand roles of
> `alignr_epi8` and `vextq_u8`.

The `.fr` surface is selected by `#if (#IS_X86_64)` / `#if (#IS_ARM64)`.
Every function is `#inline`. The bridge checks all buffer ranges unless
`-Omax` defines `FRANKO_BOUNDS_UNCHECKED`; null and uninitialized dynamic
arrays are still rejected in every build mode.

## Conventions

- `target` is a mutable view (`mview<T>`). Unless a function says otherwise,
  the operation reads the original `target` elements and writes the result
  back to the same positions.
- `source`, `mask`, `indices`, `round_key`, `left`, and `right` are read-only
  views (`view<T>`).
- `dst_start`, `src_start`, and `mask_start` are element offsets, not byte
  offsets. For `byte` and `int8` buffers one element is one byte; for
  `int16`, `uint16`, and `uint32` buffers one element is 2 or 4 bytes.
- `count` is the number of elements read from or written to a range. A range
  written as `[start, start + count)` means indices `start` through
  `start + count - 1`.
- `count == 0` is a no-op for functions returning `void`; `sad_epu8` returns
  `0`; the AES functions return `0`.
- Out-of-range access aborts with `franko_panic`. The exception is `-Omax`,
  which removes these range checks together with the other runtime bounds
  checks.
- Functions process full 16-byte vectors and then a scalar tail unless their
  description says that trailing bytes are left unchanged.
- Non-crypto operations fall back to scalar loops when the required x86
  instruction set is not available at compile time.

## x86_64

### `xor_si128`

```franko
simd::x86::xor_si128(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads `target[dst_start, dst_start + count)` and
`source[src_start, src_start + count)`. Writes the bitwise XOR of each byte
pair back to the target range. Processes 16-byte vectors and a scalar tail.

### `and_si128`

```franko
simd::x86::and_si128(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes `target[i] & source[i]` back to the target
range. Processes 16-byte vectors and a scalar tail.

### `or_si128`

```franko
simd::x86::or_si128(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes `target[i] | source[i]` back to the target
range. Processes 16-byte vectors and a scalar tail.

### `andnot_si128`

```franko
simd::x86::andnot_si128(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes `target[i] & ~source[i]` back to the target
range. Processes 16-byte vectors and a scalar tail.

### `adds_epu8`

```franko
simd::x86::adds_epu8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both unsigned byte ranges. Writes the saturating sum
`min(255, target[i] + source[i])` back to the target range. Processes
16-byte vectors and a scalar tail.

### `subs_epu8`

```franko
simd::x86::subs_epu8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both unsigned byte ranges. Writes the saturating difference
`max(0, target[i] - source[i])` back to the target range. Processes
16-byte vectors and a scalar tail.

### `max_epu8`

```franko
simd::x86::max_epu8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both unsigned byte ranges. Writes the larger byte of each pair back to
the target range. Processes 16-byte vectors and a scalar tail.

### `min_epu8`

```franko
simd::x86::min_epu8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both unsigned byte ranges. Writes the smaller byte of each pair back to
the target range. Processes 16-byte vectors and a scalar tail.

### `add_epi8`

```franko
simd::x86::add_epi8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes the wrapping sum
`(target[i] + source[i]) & 0xFF` back to the target range. Processes 16-byte
vectors and a scalar tail.

### `sub_epi8`

```franko
simd::x86::sub_epi8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes the wrapping difference
`(target[i] - source[i]) & 0xFF` back to the target range. Processes 16-byte
vectors and a scalar tail.

### `avg_epu8`

```franko
simd::x86::avg_epu8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both unsigned byte ranges. Writes the rounded average
`(target[i] + source[i] + 1) >> 1` back to the target range. Processes
16-byte vectors and a scalar tail.

### `blendv_epi8`

```franko
simd::x86::blendv_epi8(mview<[byte]> target, view<[byte]> source, view<[byte]> mask, uint32 dst_start, uint32 src_start, uint32 mask_start, uint32 count) -> void
```

Reads `target[dst_start, dst_start + count)`,
`source[src_start, src_start + count)`, and
`mask[mask_start, mask_start + count)`. For each byte, if bit 7 of the mask
byte is set, the result is the source byte; otherwise the result is the
original target byte. Writes the result to the target range. Other mask bits
are ignored. Processes 16-byte vectors and a scalar tail.

### `alignr_epi8`

```franko
simd::x86::alignr_epi8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count, uint32 shift) -> void
```

Reads `target[dst_start, dst_start + count)` as the high vector and
`source[src_start, src_start + count)` as the low vector. This matches the
operand order of `_mm_alignr_epi8(target, source, shift)`. Processes each
16-byte chunk as the concatenation `target_chunk || source_chunk` and writes
the window starting at `shift` back to the target chunk.

For output lane `j` inside a chunk, the value is
`j + shift < 16 ? source_chunk[j + shift] : target_chunk[j + shift - 16]`.
`shift = 0` copies the source chunk into the target chunk. `shift` must be
`0..15`; values `16` and above write zero to the entire target range. A
partial final chunk treats bytes outside `count` as zero.

### `unpacklo_epi8`

```franko
simd::x86::unpacklo_epi8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Interleaves the low eight bytes of each 16-byte
chunk: `target[0], source[0], target[1], source[1]`, continuing through
`target[7], source[7]`. Writes the interleaved bytes back to the target range.
A partial final chunk treats bytes outside `count` as zero.

### `unpackhi_epi8`

```franko
simd::x86::unpackhi_epi8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Interleaves the high eight bytes of each 16-byte
chunk: `target[8], source[8], target[9], source[9]`, continuing through
`target[15], source[15]`. Writes the interleaved bytes back to the target
range. A partial final chunk treats bytes outside `count` as zero.

### `packus_epi16`

```franko
simd::x86::packus_epi16(mview<[byte]> target, view<[int16]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads `count` signed 16-bit lanes from
`source[src_start, src_start + count)`. Writes `count` unsigned bytes to
`target[dst_start, dst_start + count)`, clamping each lane to `0..255`.
Negative lanes become `0`, lanes above `255` become `255`, and lanes already
in range are unchanged. The previous contents of the target range are not
read. Processes eight lanes at a time and a scalar tail.

### `sad_epu8`

```franko
simd::x86::sad_epu8(view<[byte]> left, view<[byte]> right, uint32 left_start, uint32 right_start, uint32 count) -> uint64
```

Reads `left[left_start, left_start + count)` and
`right[right_start, right_start + count)`. Returns the total sum of absolute
differences:

```text
result = sum(abs(left[i] - right[i])) for i in [0, count)
```

Both inputs are read-only and there is no target buffer. Processes 16-byte
vectors with `_mm_sad_epu8` and a scalar tail.

ARM64 has no single-instruction SAD reduction. To build the same checksum
there, use `vabdq_u8` for the byte differences, widen those bytes to
`uint16`, then use `vpaddlq_u16` and sum its `uint32` lanes.

### `abs_epi8`

```franko
simd::x86::abs_epi8(mview<[int8]> target, view<[int8]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads `count` signed bytes from `source[src_start, src_start + count)`.
Writes the absolute value of each byte to
`target[dst_start, dst_start + count)`. The previous contents of the target
range are not read. The `-128` lane remains `-128`, matching the hardware
result. Processes 16-byte vectors when SSSE3 is available and a scalar tail.

### `shuffle_epi8`

```franko
simd::x86::shuffle_epi8(mview<[byte]> target, view<[byte]> indices, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Uses the target range as a 16-byte lookup table and reads control bytes from
`indices[src_start, src_start + count)`. For each control byte, the result is
`0` when bit 7 is set, otherwise the table byte selected by the low four bits
of the control byte. Writes the result back to the target range.

Only complete 16-byte blocks are processed. A trailing group shorter than
16 bytes is left unchanged.

### `aesenc_si128`

```franko
simd::x86::aesenc_si128(mview<[byte]> target, view<[byte]> round_key, uint32 dst_start, uint32 src_start, uint32 count) -> int32
```

Reads the target range in 16-byte blocks and applies one AES encryption round
to each block using the 16-byte round key that starts at
`round_key[src_start]`. Writes each transformed block back to the target
range. Only complete 16-byte blocks are processed; a trailing group shorter
than 16 bytes is left unchanged.

Returns `0` on success. Returns a nonzero value and leaves the target
unchanged when AES-NI is unavailable at compile time. When `count` is
nonzero, the round-key range must contain at least 16 bytes.

### `aesdec_si128`

```franko
simd::x86::aesdec_si128(mview<[byte]> target, view<[byte]> round_key, uint32 dst_start, uint32 src_start, uint32 count) -> int32
```

Reads the target range in 16-byte blocks and applies one AES decryption round
to each block using the 16-byte round key that starts at
`round_key[src_start]`. Writes each transformed block back to the target
range. Only complete 16-byte blocks are processed; a trailing group shorter
than 16 bytes is left unchanged.

Returns `0` on success. Returns a nonzero value and leaves the target
unchanged when AES-NI is unavailable at compile time. When `count` is
nonzero, the round-key range must contain at least 16 bytes.

## ARM64

### `veorq_u8`

```franko
simd::arm::veorq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads `target[dst_start, dst_start + count)` and
`source[src_start, src_start + count)`. Writes the bitwise XOR of each byte
pair back to the target range. Processes 16-byte vectors and a scalar tail.

### `vandq_u8`

```franko
simd::arm::vandq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes `target[i] & source[i]` back to the target
range. Processes 16-byte vectors and a scalar tail.

### `vorrq_u8`

```franko
simd::arm::vorrq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes `target[i] | source[i]` back to the target
range. Processes 16-byte vectors and a scalar tail.

### `vbicq_u8`

```franko
simd::arm::vbicq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes `target[i] & ~source[i]` back to the target
range. Processes 16-byte vectors and a scalar tail.

### `vqaddq_u8`

```franko
simd::arm::vqaddq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both unsigned byte ranges. Writes the saturating sum
`min(255, target[i] + source[i])` back to the target range. Processes
16-byte vectors and a scalar tail.

### `vqsubq_u8`

```franko
simd::arm::vqsubq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both unsigned byte ranges. Writes the saturating difference
`max(0, target[i] - source[i])` back to the target range. Processes
16-byte vectors and a scalar tail.

### `vmaxq_u8`

```franko
simd::arm::vmaxq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both unsigned byte ranges. Writes the larger byte of each pair back to
the target range. Processes 16-byte vectors and a scalar tail.

### `vminq_u8`

```franko
simd::arm::vminq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both unsigned byte ranges. Writes the smaller byte of each pair back to
the target range. Processes 16-byte vectors and a scalar tail.

### `vaddq_u8`

```franko
simd::arm::vaddq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes the wrapping sum
`(target[i] + source[i]) & 0xFF` back to the target range. Processes 16-byte
vectors and a scalar tail.

### `vsubq_u8`

```franko
simd::arm::vsubq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes the wrapping difference
`(target[i] - source[i]) & 0xFF` back to the target range. Processes 16-byte
vectors and a scalar tail.

### `vrhaddq_u8`

```franko
simd::arm::vrhaddq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both unsigned byte ranges. Writes the rounded average
`(target[i] + source[i] + 1) >> 1` back to the target range. Processes
16-byte vectors and a scalar tail.

### `vbslq_u8`

```franko
simd::arm::vbslq_u8(mview<[byte]> target, view<[byte]> source, view<[byte]> mask, uint32 dst_start, uint32 src_start, uint32 mask_start, uint32 count) -> void
```

Reads `target[dst_start, dst_start + count)`,
`source[src_start, src_start + count)`, and
`mask[mask_start, mask_start + count)`. For each byte, computes the exact
NEON bitwise select:

```text
target[i] = (target[i] & mask[i]) | (source[i] & ~mask[i])
```

A mask byte of `0xFF` selects the target byte, `0x00` selects the source
byte, and intermediate values mix bits from both. Writes the result to the
target range. Processes 16-byte vectors and a scalar tail.

The Franko parameter order stays `target, source, mask` to match the rest of
the module. The bridge calls the NEON intrinsic as
`vbslq_u8(mask, target, source)`, which uses the mask as its first argument.

### `vextq_u8`

```franko
simd::arm::vextq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count, uint32 shift) -> void
```

Reads `target[dst_start, dst_start + count)` as the low vector and
`source[src_start, src_start + count)` as the high vector. Processes each
16-byte chunk as the concatenation `target_chunk || source_chunk` and writes
the window starting at `shift` back to the target chunk.

For output lane `j` inside a chunk, the value is
`j + shift < 16 ? target_chunk[j + shift] : source_chunk[j + shift - 16]`.
`shift` must be `0..15`; values `16` and above write zero to the entire target
range. A partial final chunk treats bytes outside `count` as zero. The
immediate shift is expanded into a 16-way runtime dispatch.

### `vzip1q_u8`

```franko
simd::arm::vzip1q_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Interleaves the low eight bytes of each 16-byte
chunk: `target[0], source[0], target[1], source[1]`, continuing through
`target[7], source[7]`. Writes the interleaved bytes back to the target range.
A partial final chunk treats bytes outside `count` as zero.

### `vzip2q_u8`

```franko
simd::arm::vzip2q_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Interleaves the high eight bytes of each 16-byte
chunk: `target[8], source[8], target[9], source[9]`, continuing through
`target[15], source[15]`. Writes the interleaved bytes back to the target
range. A partial final chunk treats bytes outside `count` as zero.

### `vqmovun_s16`

```franko
simd::arm::vqmovun_s16(mview<[byte]> target, view<[int16]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads `count` signed 16-bit lanes from
`source[src_start, src_start + count)`. Writes `count` unsigned bytes to
`target[dst_start, dst_start + count)`, clamping each lane to `0..255`.
Negative lanes become `0`, lanes above `255` become `255`, and lanes already
in range are unchanged. The previous contents of the target range are not
read. Processes eight lanes at a time and a scalar tail.

### `vabdq_u8`

```franko
simd::arm::vabdq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both unsigned byte ranges. Writes the unsigned absolute difference
`abs(target[i] - source[i])` back to the target range. Processes 16-byte
vectors and a scalar tail.

### `vpaddlq_u16`

```franko
simd::arm::vpaddlq_u16(mview<[uint32]> target, view<[uint16]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads `2 * count` unsigned 16-bit lanes from
`source[src_start, src_start + 2 * count)`. Writes `count` unsigned 32-bit
lanes to `target[dst_start, dst_start + count)`, where each output is the sum
of two adjacent input lanes:

```text
target[i] = source[2 * i] + source[2 * i + 1]
```

The previous contents of the target range are not read. Processes four
output lanes at a time and a scalar tail. The source range must contain
`2 * count` lanes.

### `vabsq_s8`

```franko
simd::arm::vabsq_s8(mview<[int8]> target, view<[int8]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads `count` signed bytes from `source[src_start, src_start + count)`.
Writes the absolute value of each byte to
`target[dst_start, dst_start + count)`. The previous contents of the target
range are not read. The `-128` lane remains `-128`, matching the hardware
result. Processes 16-byte vectors and a scalar tail.

### `vqtbl1q_u8`

```franko
simd::arm::vqtbl1q_u8(mview<[byte]> target, view<[byte]> indices, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Uses the target range as a 16-byte lookup table and reads control bytes from
`indices[src_start, src_start + count)`. A control byte in `0..15` selects
the matching table byte; a control byte of `16` or greater produces `0`.
Writes the result back to the target range.

Only complete 16-byte blocks are processed. A trailing group shorter than
16 bytes is left unchanged. Note that x86 `shuffle_epi8` uses only the low
four bits of a control byte when bit 7 is clear, while `vqtbl1q_u8` treats
every value above `15` as zero.

### `vaeseq_u8`

```franko
simd::arm::vaeseq_u8(mview<[byte]> target, view<[byte]> round_key, uint32 dst_start, uint32 src_start, uint32 count) -> int32
```

Reads the target range in 16-byte blocks and applies one AES encryption round
to each block using the 16-byte round key that starts at
`round_key[src_start]`. Writes each transformed block back to the target
range. Only complete 16-byte blocks are processed; a trailing group shorter
than 16 bytes is left unchanged.

Returns `0` on success. Returns a nonzero value and leaves the target
unchanged when the ARM AES extension is unavailable at compile time. When
`count` is nonzero, the round-key range must contain at least 16 bytes.

### `vaesdq_u8`

```franko
simd::arm::vaesdq_u8(mview<[byte]> target, view<[byte]> round_key, uint32 dst_start, uint32 src_start, uint32 count) -> int32
```

Reads the target range in 16-byte blocks and applies one AES decryption round
to each block using the 16-byte round key that starts at
`round_key[src_start]`. Writes each transformed block back to the target
range. Only complete 16-byte blocks are processed; a trailing group shorter
than 16 bytes is left unchanged.

Returns `0` on success. Returns a nonzero value and leaves the target
unchanged when the ARM AES extension is unavailable at compile time. When
`count` is nonzero, the round-key range must contain at least 16 bytes.

## Cross-architecture notes

- `blendv_epi8` uses bit 7 of each mask byte to select the source byte.
  `vbslq_u8` is the exact NEON bitwise select: `0xFF` selects the target
  byte, `0x00` selects the source byte, and other mask values mix bits. They
  are not interchangeable for arbitrary masks.
- `shuffle_epi8` and `vqtbl1q_u8` agree for control bytes `0..15`. They differ
  for control bytes `16..127`: x86 uses the low four bits, while ARM produces
  zero. Both leave a trailing partial block unchanged.
- `alignr_epi8` follows `_mm_alignr_epi8(target, source, shift)` and treats
  `target` as the high vector. `vextq_u8` follows
  `vextq_u8(target, source, shift)` and treats `target` as the low vector.
  Their operand roles are therefore opposite. On x86, `shift == 0` copies
  the source into the target; on ARM, `shift == 0` leaves the target
  unchanged.
- `packus_epi16` and `vqmovun_s16` narrow one contiguous range of signed
  16-bit lanes into unsigned bytes. The x86 implementation processes eight
  lanes at a time, which is equivalent to applying the two-input
  `_mm_packus_epi16` to adjacent halves of the source range.
- `sad_epu8` is a total reduction returning one `uint64`. ARM64 has no
  single-instruction equivalent; use `vabdq_u8`, widen the byte differences
  to `uint16`, then use `vpaddlq_u16`.
- `packus_epi16`, `vqmovun_s16`, `abs_epi8`, `vabsq_s8`, and `vpaddlq_u16`
  write the target range without reading its previous contents.

## Instruction requirements and fallbacks

x86_64:

- `xor_si128`, `and_si128`, `or_si128`, `andnot_si128`, `adds_epu8`,
  `subs_epu8`, `max_epu8`, `min_epu8`, `add_epi8`, `sub_epi8`, `avg_epu8`,
  `unpacklo_epi8`, `unpackhi_epi8`, `packus_epi16`, and `sad_epu8` use SSE2.
- `blendv_epi8` uses SSE4.1 when available and otherwise runs its scalar
  fallback.
- `alignr_epi8`, `abs_epi8`, and `shuffle_epi8` use SSSE3 when available and
  otherwise run their scalar fallbacks.
- `aesenc_si128` and `aesdec_si128` use AES-NI when available and otherwise
  return a nonzero error code.

ARM64:

- All non-AES operations use baseline AArch64 NEON instructions.
- `vaeseq_u8` and `vaesdq_u8` use the ARM AES extension when available and
  otherwise return a nonzero error code.

## Example

```franko
import "franko/stdio"
import "franko/simd"

func main() -> int32 {
    [byte, 16] a
    [byte, 16] b
    uint32 i = 0
    while (i < 16) {
        a[i] = unsafe_cast<byte>(i + 1)
        b[i] = unsafe_cast<byte>(200)
        i = i + 1
    }

    #if (#IS_ARM64) {
        simd::arm::vaddq_u8(mview(a), view(b), 0, 0, 16)
    } #else #if (#IS_X86_64) {
        simd::x86::add_epi8(mview(a), view(b), 0, 0, 16)
    }
    stdout(a[0])   // 201
    return 0
}
```

The two `add` operations are byte-for-byte equivalent, so this example needs
no architecture-specific mask. Blend examples are intentionally separate:
`blendv_epi8` uses bit 7 of each mask byte, while `vbslq_u8` uses the full
mask byte as a bitwise selector.
