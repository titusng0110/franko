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

With AVX2, every operation below processes 32-byte vectors first, then
falls through to the documented 16-byte vectors and scalar tail. Without
AVX2 it processes 16-byte vectors and a scalar tail; the SSSE3/SSE4.1/AES
rows state their own scalar fallbacks. A trailing partial group is handled
by the scalar tail unless the description says otherwise.

### `xor_si256`

```franko
simd::x86::xor_si256(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads `target[dst_start, dst_start + count)` and
`source[src_start, src_start + count)`. Writes the bitwise XOR of each byte
pair back to the target range. Processes 16-byte vectors and a scalar tail.

### `and_si256`

```franko
simd::x86::and_si256(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes `target[i] & source[i]` back to the target
range. Processes 16-byte vectors and a scalar tail.

### `or_si256`

```franko
simd::x86::or_si256(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes `target[i] | source[i]` back to the target
range. Processes 16-byte vectors and a scalar tail.

### `andnot_si256`

```franko
simd::x86::andnot_si256(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
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

### `aesenc_si256`

```franko
simd::x86::aesenc_si256(mview<[byte]> target, view<[byte]> round_key, uint32 dst_start, uint32 src_start, uint32 count) -> int32
```

Reads the target range in 16-byte blocks and applies one AES encryption round
to each block using the 16-byte round key that starts at
`round_key[src_start]`. Writes each transformed block back to the target
range. Only complete 16-byte blocks are processed; a trailing group shorter
than 16 bytes is left unchanged.

Returns `0` on success. Returns a nonzero value and leaves the target
unchanged when AES-NI is unavailable at compile time. When `count` is
nonzero, the round-key range must contain at least 16 bytes.

### `aesdec_si256`

```franko
simd::x86::aesdec_si256(mview<[byte]> target, view<[byte]> round_key, uint32 dst_start, uint32 src_start, uint32 count) -> int32
```

Reads the target range in 16-byte blocks and applies one AES decryption round
to each block using the 16-byte round key that starts at
`round_key[src_start]`. Writes each transformed block back to the target
range. Only complete 16-byte blocks are processed; a trailing group shorter
than 16 bytes is left unchanged.

Returns `0` on success. Returns a nonzero value and leaves the target
unchanged when AES-NI is unavailable at compile time. When `count` is
nonzero, the round-key range must contain at least 16 bytes.

### `cmpeq_epi8`

```franko
simd::x86::cmpeq_epi8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes `0xFF` back to the target range where the
bytes are equal and `0x00` where they differ. Processes 32-byte vectors
with AVX2, otherwise 16-byte vectors, and a scalar tail.

### `movemask_epi8`

```franko
simd::x86::movemask_epi8(view<[byte]> source, uint32 src_start, uint32 count) -> uint64
```

Reads `source[src_start, src_start + count)` and compresses the most
significant bit of each byte into a bitmask: result bit `i` is the MSB of
byte `i`. The typical input is a `cmpeq_epi8` mask buffer. Requires
`count <= 64`; larger counts abort. Processes 32-byte vectors with AVX2,
otherwise 16-byte vectors, and scalar bit tests. ARM64 has no
single-instruction equivalent; there is no ARM function here. See the
cross-architecture notes for a composition recipe.

### `crc32_u8`

```franko
simd::x86::crc32_u8(uint32 crc, view<[byte]> data, uint32 src_start, uint32 count) -> uint32
```

Folds `data[src_start, src_start + count)` into the CRC-32C (Castagnoli,
reflected polynomial `0x82F63B78`) checksum starting from `crc` and returns
the updated value. This is the raw hardware update: there is no pre- or
post-inversion. To compute the common zlib/Ethernet-style CRC, start from
`0xFFFFFFFF` and invert the final value. Processes eight bytes at a time
with SSE4.2 and a scalar bitwise tail. This is a different polynomial than
ARM `crc32b_u8`; only ARM `crc32cb_u8` matches it.

### `cvtepu8_epi16`

```franko
simd::x86::cvtepu8_epi16(mview<[uint16]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads `count` bytes from `source[src_start, src_start + count)` and writes
them zero-extended to `count` unsigned 16-bit lanes at
`target[dst_start, dst_start + count)`. The previous contents of the target
range are not read. Processes 16 lanes at a time with AVX2, eight lanes
with SSE4.1, and a scalar tail.

### `dpbusd_epi32`

```franko
simd::x86::dpbusd_epi32(mview<[uint32]> acc, view<[byte]> a, view<[int8]> b, uint32 acc_start, uint32 a_start, uint32 b_start, uint32 count) -> void
```

Reads `count` unsigned 32-bit accumulators from
`acc[acc_start, acc_start + count)` and `4 * count` bytes from each of
`a[a_start, ...)` (unsigned) and `b[b_start, ...)` (signed). For each
output lane `i`, adds the dot product of the four unsigned `a` bytes with
the four signed `b` bytes, wrapping on overflow. Processes eight lanes at
a time with AVX2+VNNI, four lanes with AVX-VNNI, and a scalar tail.

### `ptest_epi8`

```franko
simd::x86::ptest_epi8(view<[byte]> a, view<[byte]> b, uint32 a_start, uint32 b_start, uint32 count) -> uint32
```

Reads both byte ranges and returns `1` when every byte position has
`(a[i] & b[i]) == 0`, otherwise returns `0` at the first intersecting
chunk. Processes 32-byte vectors with AVX2, otherwise 16-byte vectors
with SSE4.1, and a scalar tail with early exit. ARM64 has no
single-instruction equivalent; there is no ARM function here.

### `clmulepi64_epi64`

```franko
simd::x86::clmulepi64_epi64(mview<[uint64]> target, view<[uint64]> a, view<[uint64]> b, uint32 dst_start, uint32 a_start, uint32 b_start, uint32 count) -> void
```

Reads `count` unsigned 64-bit lanes from each of `a` and `b` and writes the
128-bit carryless product of each pair to
`target[dst_start, dst_start + 2 * count)` as interleaved low/high words:
`target[2*i]` is the low word, `target[2*i + 1]` the high word. The target
range must therefore hold `2 * count` lanes. Processes two products at a
time with VPCLMULQDQ and AVX2, one product with PCLMULQDQ, and a scalar
shift-and-xor tail.

### `stream_si256`

```franko
simd::x86::stream_si256(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Copies `source[src_start, src_start + count)` to
`target[dst_start, dst_start + count)` with non-temporal (streaming) stores,
which avoid polluting the cache and suit buffers larger than the last-level
cache. A short unaligned lead-in uses regular stores so the streaming loop
stays 32-byte (AVX2) or 16-byte aligned. There is no completion fence:
single-threaded use observes the copy normally, but cross-thread visibility
needs an explicit fence outside this module. ARM has no single-register
non-temporal SIMD store; ARM `vst1q_u8` below is a regular vector copy with
identical observable results.

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

Reads the target range in 16-byte blocks and executes the ARM `AESE`
instruction on each block using the 16-byte round key that starts at
`round_key[src_start]`: AddRoundKey, then SubBytes, then ShiftRows. Unlike
x86 `aesenc_si256`, this does not perform MixColumns. Writes each transformed
block back to the target range. Only complete 16-byte blocks are processed;
a trailing group shorter than 16 bytes is left unchanged.

Returns `0` on success. Returns a nonzero value and leaves the target
unchanged when the ARM AES extension is unavailable at compile time. When
`count` is nonzero, the round-key range must contain at least 16 bytes.

### `vaesdq_u8`

```franko
simd::arm::vaesdq_u8(mview<[byte]> target, view<[byte]> round_key, uint32 dst_start, uint32 src_start, uint32 count) -> int32
```

Reads the target range in 16-byte blocks and executes the ARM `AESD`
instruction on each block using the 16-byte round key that starts at
`round_key[src_start]`: AddRoundKey, then InvShiftRows, then InvSubBytes.
Unlike x86 `aesdec_si256`, this does not perform InvMixColumns. Writes each
transformed block back to the target range. Only complete 16-byte blocks are
processed; a trailing group shorter than 16 bytes is left unchanged.

Returns `0` on success. Returns a nonzero value and leaves the target
unchanged when the ARM AES extension is unavailable at compile time. When
`count` is nonzero, the round-key range must contain at least 16 bytes.

### `vceqq_u8`

```franko
simd::arm::vceqq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads both byte ranges. Writes `0xFF` back to the target range where the
bytes are equal and `0x00` where they differ. Processes 16-byte vectors and
a scalar tail.

### `crc32b_u8`

```franko
simd::arm::crc32b_u8(uint32 crc, view<[byte]> data, uint32 src_start, uint32 count) -> uint32
```

Folds `data[src_start, src_start + count)` into the CRC-32 (ISO 3309,
reflected polynomial `0xEDB88320`) checksum starting from `crc` and returns
the updated value. This is the raw `CRC32B` update: there is no pre- or
post-inversion. With `crc == 0` the result is the hardware accumulator, not
the zlib/Ethernet CRC; start from `0xFFFFFFFF` and invert the final value
for that convention. Processes eight bytes at a time with the ARM CRC
extension and a scalar bitwise tail. This is a different polynomial than
x86 `crc32_u8`; only ARM `crc32cb_u8` matches that one.

### `crc32cb_u8`

```franko
simd::arm::crc32cb_u8(uint32 crc, view<[byte]> data, uint32 src_start, uint32 count) -> uint32
```

Same shape as `crc32b_u8` but computes CRC-32C (Castagnoli, reflected
polynomial `0x82F63B78`), matching x86 `crc32_u8` byte for byte. Processes
eight bytes at a time with the ARM CRC extension and a scalar bitwise
tail.

### `vmovl_u8`

```franko
simd::arm::vmovl_u8(mview<[uint16]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads `count` bytes from `source[src_start, src_start + count)` and writes
them zero-extended to `count` unsigned 16-bit lanes at
`target[dst_start, dst_start + count)`. The previous contents of the target
range are not read. Processes 16 bytes and a scalar tail.

### `vcntq_u8`

```franko
simd::arm::vcntq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Reads `count` bytes and writes each byte's population count (`0..8`) to the
target range. The previous contents of the target range are not read.
Processes 16-byte vectors and a scalar tail. There is no x86 equivalent;
x86 code should use a scalar popcount loop.

### `vmull_p64`

```franko
simd::arm::vmull_p64(mview<[uint64]> target, view<[uint64]> a, view<[uint64]> b, uint32 dst_start, uint32 a_start, uint32 b_start, uint32 count) -> void
```

Reads `count` unsigned 64-bit lanes from each of `a` and `b` and writes the
128-bit carryless product of each pair to
`target[dst_start, dst_start + 2 * count)` as interleaved low/high words:
`target[2*i]` is the low word, `target[2*i + 1]` the high word. The target
range must therefore hold `2 * count` lanes. Requires the cryptography
extension; otherwise runs a scalar shift-and-xor tail.

### `vst1q_u8`

```franko
simd::arm::vst1q_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
```

Copies `source[src_start, src_start + count)` to
`target[dst_start, dst_start + count)` with regular 16-byte vector stores
and a scalar tail. This is the ARM counterpart of x86 `stream_si256`,
which uses non-temporal stores: ARM has no single-register non-temporal
SIMD store, so the observable copy is identical while only the x86 side
bypasses the cache.

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
- `movemask_epi8` and `ptest_epi8` exist only on x86: ARM64 has no
  single-instruction MSB compressor or intersection test. To port a
  `cmpeq`/`movemask` search loop, compare with `vceqq_u8` and scan the
  mask bytes with a scalar MSB test. To port `ptest`, use `vandq_u8`
  with `vmaxvq_u8`, or a scalar early-exit loop.
- `crc32_u8` (x86) is Castagnoli only. Its ARM match is `crc32cb_u8`,
  not `crc32b_u8`, which computes the ISO polynomial instead.
- `dpbusd_epi32` has no ARM counterpart here: its equivalent `USDOT`
  instruction is part of `FEAT_I8MM`, which Apple M3 does not implement.
- `clmulepi64_epi64` and `vmull_p64` share the interleaved low/high target
  layout: `target[2*i]` is the low word, `target[2*i + 1]` the high
  word, so the target range always holds `2 * count` lanes.

## Instruction requirements and fallbacks

x86_64:

- `xor_si256`, `and_si256`, `or_si256`, `andnot_si256`, `adds_epu8`,
  `subs_epu8`, `max_epu8`, `min_epu8`, `add_epi8`, `sub_epi8`, `avg_epu8`,
  `unpacklo_epi8`, `unpackhi_epi8`, `packus_epi16`, `sad_epu8`,
  `cmpeq_epi8`, `stream_si256`, and `movemask_epi8` use SSE2, with an
  additional AVX2 32-byte tier where one exists.
- `crc32_u8`, `cvtepu8_epi16`, and `ptest_epi8` use
  SSE4.1 when available, with an AVX2 tier where one exists, and otherwise
  run their scalar fallbacks.
- `blendv_epi8` uses SSE4.1 when available and otherwise runs its scalar
  fallback.
- `alignr_epi8`, `abs_epi8`, and `shuffle_epi8` use SSSE3 when available and
  otherwise run their scalar fallbacks.
- `dpbusd_epi32` uses AVX-VNNI (plus AVX2 for its 32-byte tier) when
  available and otherwise runs its scalar fallback.
- `clmulepi64_epi64` uses VPCLMULQDQ with AVX2 (four products per iteration, shuffled into place with lane stores), then PCLMULQDQ, and otherwise
  runs its scalar shift-and-xor fallback.
- `aesenc_si256` and `aesdec_si256` use VAES with AVX2, then AES-NI, and
  otherwise return a nonzero error code.
- `movemask_epi8` requires `count <= 64` in every build; larger counts
  abort instead of truncating.

ARM64:

- All non-AES operations use baseline AArch64 NEON instructions, except
  `vmull_p64` (cryptography extension) and `crc32b_u8`/`crc32cb_u8`
  (CRC extension), which run scalar fallbacks without them. The surface
  deliberately stops at the Apple M3 feature set: it exposes no I8MM
  (`USDOT`/`SMMLA`) or SVE wrappers.
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
