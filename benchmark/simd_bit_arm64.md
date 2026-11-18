# ARM64 SIMD and scalar bit intrinsics vs naive Franko

**20 `franko/simd` ARM64 functions × 3 buffer sizes (64 / 512 / 4096 bytes) + 8 `franko/bit` functions · Apple M5 MacBook Air · `-march=native` · 2026-11-19**

This is the ARM64 counterpart to `simd_bit_x86_64.md`. It measures the
`franko/simd` ARM surface against naive scalar Franko implementations,
plus `franko/bit` against its naive scalar loops.

The module's target ceiling is Apple M3: ARMv8.6-A with
AES/PMULL/CRC32/DotProd, 128-bit NEON, no I8MM and no SVE. The benchmark
host is an Apple M5 MacBook Air (a superset for every measured operation),
so treat the absolute ns/op as this machine's numbers and compare the
speedups. AES and PMULL are omitted here because they are not throughput
operations in the same sense.

## Short version

- Simple bytewise ops are at parity at every size (**0.94x–1.01x**): both
  the module and the naive loop become 128-bit NEON, so the intrinsic buys
  no extra throughput on pure elementwise logic/arithmetic.
- `vqaddq_u8` is the exception in that group: the naive saturating branch
  loop stays scalar, so the module wins **3.53x / 2.34x / 2.86x**.
- Byte rearrangement is where the module wins big and the gap survives
  large buffers: `vextq_u8` **23.19x / 19.11x / 22.43x**, `vqtbl1q_u8`
  **9.72x / 7.11x / 8.88x**, and `vzip1q_u8` **8.28x / 3.22x / 3.96x**.
- `vzip2q_u8` wins at 64 bytes (**8.77x**) but the naive loop wins at
  512 bytes (**0.55x**) and reaches parity at 4096 bytes (**0.98x**):
  LLVM recognizes that interleave and emits vector `zip2` itself.
- The narrowing/widening pair goes the other way at large sizes:
  `vqmovun_s16` (**1.32x / 0.66x / 0.71x**) and `vpaddlq_u16`
  (**0.97x / 0.68x / 0.73x**) are faster as auto-vectorized naive loops
  once the per-call floor amortizes.
- `vabsq_s8` is parity at 64/512 bytes and **0.71x** at 4096 bytes for the
  same reason: LLVM vectorizes the naive absolute-value loop well.
- For `franko/bit`, `popcount64` is the largest win at **101.44x**,
  followed by `popcount32` at **45.95x** and `clz64` at **22.64x**.
  `ctz32` wins **13.66x**, `ctz64` **6.36x**, and `clz32` **8.95x**.
  Both `bswap` cases are at parity.

## Method

- **Host:** Apple M5 MacBook Air, macOS 27.2.1, arm64 (128-bit NEON,
  AES, PMULL, CRC32, DotProd).
- **Build:** Franko `-Omax` (C++ `-O3`, `FRANKO_BOUNDS_UNCHECKED`) with
  `--native`. Every measured ARM function is baseline NEON, so the
  `-march` choice does not change instruction selection here; it only
  affects auto-vectorization of the naive side.
- **Calls per case:** 4,000,000 at 64 bytes, 2,000,000 at 512 bytes,
  1,000,000 at 4096 bytes.
- **Timing:** `std::mono_time()`; median of five process runs. No CPU
  pinning (macOS does not expose the Linux affinity interface).
- **Buffer sizes:** 64, 512, and 4096 bytes per call, i.e. 4, 32, and 256
  sixteen-byte vectors.
- **`vqmovun_s16`:** 64 / 512 / 4096 signed 16-bit lanes to the same
  number of bytes (the source footprint is twice the target footprint).
- **`vpaddlq_u16`:** 64 / 512 / 4096 output `uint32` lanes, each from two
  adjacent `uint16` lanes. Both the target and source footprints are
  `4 * count` bytes.
- **`vextq_u8`:** shift 3, with `target` as the low vector and `source`
  as the high vector.
- **`vbslq_u8`:** 64/512/4096-byte mask using `0xFF`/`0x00` bytes plus one
  `0x0F` byte; the select uses all mask bits.
- **`vqtbl1q_u8`:** index bytes covering the full `0..255` range; only
  complete 16-byte blocks are written.
- **`vabsq_s8`:** 64 / 512 / 4096 signed bytes.

Each timed loop resets all input buffers from master copies. During the
loop, one input byte rotates per iteration and a rotating target byte is
checksummed. This keeps the work alive and prevents the compiler from
hoisting or dead-store-eliminating the operation. The module and naive
checksums matched exactly for every case, size, and run.

The reported ns/op includes the harness overhead: one rotating input write
and one rotating target read per call. It is throughput per call, not
single-instruction latency. The overhead is a large share at 64 bytes and
negligible at 4096 bytes.

## ARM64 SIMD, 64-byte buffers

Median of runs, 4,000,000 calls per case.

| function | Franko module ns/op | Naive Franko ns/op | Speedup |
|---|---:|---:|---:|
| `veorq_u8` | 1.062 | 1.075 | 1.01x |
| `vandq_u8` | 1.064 | 1.061 | 1.00x |
| `vorrq_u8` | 1.058 | 1.061 | 1.00x |
| `vbicq_u8` | 1.063 | 1.069 | 1.00x |
| `vqaddq_u8` | 1.049 | 3.708 | 3.53x |
| `vqsubq_u8` | 1.052 | 1.054 | 1.00x |
| `vmaxq_u8` | 1.060 | 1.072 | 1.01x |
| `vminq_u8` | 1.065 | 1.060 | 1.00x |
| `vaddq_u8` | 1.058 | 1.063 | 1.00x |
| `vsubq_u8` | 1.070 | 1.064 | 0.99x |
| `vrhaddq_u8` | 1.064 | 1.062 | 1.00x |
| `vbslq_u8` | 1.068 | 1.062 | 0.99x |
| `vextq_u8` | 1.069 | 24.787 | 23.19x |
| `vzip1q_u8` | 1.062 | 8.793 | 8.28x |
| `vzip2q_u8` | 1.086 | 9.519 | 8.77x |
| `vqmovun_s16` | 1.114 | 1.474 | 1.32x |
| `vabdq_u8` | 1.055 | 1.051 | 1.00x |
| `vpaddlq_u16` | 3.114 | 3.027 | 0.97x |
| `vabsq_s8` | 1.077 | 1.068 | 0.99x |
| `vqtbl1q_u8` | 1.066 | 10.361 | 9.72x |

## ARM64 SIMD, 512-byte buffers

Median of runs, 2,000,000 calls per case.

| function | Franko module ns/op | Naive Franko ns/op | Speedup |
|---|---:|---:|---:|
| `veorq_u8` | 10.220 | 10.150 | 0.99x |
| `vandq_u8` | 10.288 | 10.153 | 0.99x |
| `vorrq_u8` | 10.289 | 10.146 | 0.99x |
| `vbicq_u8` | 10.256 | 10.188 | 0.99x |
| `vqaddq_u8` | 10.405 | 24.331 | 2.34x |
| `vqsubq_u8` | 10.219 | 10.248 | 1.00x |
| `vmaxq_u8` | 10.290 | 10.180 | 0.99x |
| `vminq_u8` | 10.305 | 10.146 | 0.98x |
| `vaddq_u8` | 10.293 | 10.168 | 0.99x |
| `vsubq_u8` | 10.243 | 10.172 | 0.99x |
| `vrhaddq_u8` | 10.293 | 10.164 | 0.99x |
| `vbslq_u8` | 10.635 | 10.646 | 1.00x |
| `vextq_u8` | 10.382 | 198.440 | 19.11x |
| `vzip1q_u8` | 10.370 | 33.388 | 3.22x |
| `vzip2q_u8` | 10.383 | 5.721 | 0.55x |
| `vqmovun_s16` | 16.934 | 11.224 | 0.66x |
| `vabdq_u8` | 10.332 | 10.180 | 0.99x |
| `vpaddlq_u16` | 34.748 | 23.564 | 0.68x |
| `vabsq_s8` | 4.997 | 4.992 | 1.00x |
| `vqtbl1q_u8` | 10.581 | 75.204 | 7.11x |

## ARM64 SIMD, 4096-byte buffers

Median of runs, 1,000,000 calls per case.

| function | Franko module ns/op | Naive Franko ns/op | Speedup |
|---|---:|---:|---:|
| `veorq_u8` | 69.572 | 65.683 | 0.94x |
| `vandq_u8` | 69.552 | 65.614 | 0.94x |
| `vorrq_u8` | 69.445 | 65.717 | 0.95x |
| `vbicq_u8` | 69.568 | 65.717 | 0.94x |
| `vqaddq_u8` | 69.581 | 198.974 | 2.86x |
| `vqsubq_u8` | 69.599 | 65.756 | 0.94x |
| `vmaxq_u8` | 69.620 | 65.795 | 0.95x |
| `vminq_u8` | 69.642 | 65.764 | 0.94x |
| `vaddq_u8` | 69.494 | 65.749 | 0.95x |
| `vsubq_u8` | 69.609 | 65.780 | 0.94x |
| `vrhaddq_u8` | 69.585 | 65.696 | 0.94x |
| `vbslq_u8` | 75.277 | 74.615 | 0.99x |
| `vextq_u8` | 69.592 | 1561.232 | 22.43x |
| `vzip1q_u8` | 67.515 | 267.432 | 3.96x |
| `vzip2q_u8` | 69.641 | 67.993 | 0.98x |
| `vqmovun_s16` | 127.103 | 89.882 | 0.71x |
| `vabdq_u8` | 69.591 | 65.689 | 0.94x |
| `vpaddlq_u16` | 266.796 | 195.448 | 0.73x |
| `vabsq_s8` | 67.710 | 48.171 | 0.71x |
| `vqtbl1q_u8` | 67.451 | 598.671 | 8.88x |

## `franko/bit`

Median of runs, 4,000,000 calls per case.

| function | Franko module ns/op | Naive Franko ns/op | Speedup |
|---|---:|---:|---:|
| `ctz32` | 0.090 | 1.229 | 13.66x |
| `ctz64` | 0.090 | 0.572 | 6.36x |
| `clz32` | 0.237 | 2.121 | 8.95x |
| `clz64` | 0.241 | 5.451 | 22.64x |
| `popcount32` | 0.203 | 9.351 | 45.95x |
| `popcount64` | 0.175 | 17.777 | 101.44x |
| `bswap32` | 0.175 | 0.171 | 0.98x |
| `bswap64` | 0.213 | 0.213 | 1.00x |

## Interpretation

Three groups, separated by what LLVM does with the naive loop.

**Parity where both vectorize.** Bitwise logic, wrapping arithmetic,
average, saturating subtract, select, absolute value at 64/512 bytes, and
the narrow/widen pair at 64 bytes all measure within a few percent. Both
sides emit 128-bit NEON, so the module's win is only avoiding per-call
scalar overhead at small sizes.

**Large wins where the naive loop stays scalar.** `vextq_u8` (byte window
across two vectors), `vqtbl1q_u8` (table lookup), `vzip1q_u8` (low-half
interleave), and `vqaddq_u8` (saturating branch) defeat the
auto-vectorizer at every size. `vextq_u8` is the standout: the naive loop
copies a 16-byte block per chunk and branches per byte, while the module
executes one `EXT` per chunk.

**Naive wins where LLVM recognizes the pattern.** `vzip2q_u8` becomes a
vector `ZIP2` in the naive loop, so it beats the module at 512 bytes and
matches it at 4096. `vqmovun_s16` and `vpaddlq_u16` auto-vectorize into
efficient narrowing/pairwise-add sequences that beat the module at 512
and 4096 bytes. `vabsq_s8` similarly vectorizes well and edges ahead at
4096 bytes. This is the same phenomenon the x86 report saw for `abs`.

**Absolute throughput.** At 4096 bytes the module moves 4 KB in
~69–70 ns for simple and rearranging ops (~59 GB/s), while
`vqmovun_s16` (4 KB target + 8 KB source) takes ~127 ns (~94 GB/s) and
`vpaddlq_u16` (16 KB + 16 KB) takes ~267 ns (~120 GB/s). These are
L1-resident streaming numbers on this chip.

For `franko/bit`, the picture is the same as x86: `popcount` and `clz`
win big because LLVM does not lower the naive shift-and-count loops to
`CNT`/`CLZ`; `ctz` wins modestly because the compiler does recognize the
trailing-zero loop; `bswap` is parity because the naive byte-reversal loop
becomes the same `REV` instruction.

## Caveats

- The host is an Apple M5, not an M3. The module surface is M3-ceiling
  code, but absolute ns/op will differ on M3; compare speedups.
- macOS does not expose Linux-style CPU affinity, so these runs are not
  pinned. Medians of five runs are reported.
- The harness adds one byte write and one byte read per call. At 64 bytes
  that is a visible share; at 4096 bytes it is negligible.
- The naive implementations are intentionally ordinary Franko, compiled
  with the same `-Omax` and allowed to auto-vectorize.
- Byte-level exactness was verified, not assumed: module and naive
  checksums matched for every case, size, and run, including the
  `vbslq` full-bit select, the `vextq` shift-3 window, the `vqtbl1q`
  zero-above-15 rule, and the `vzip` partial-block rules.
- The module deliberately exposes no I8MM (`USDOT`/`SMMLA`) or SVE
  wrappers, matching the Apple M3 feature set.
- AES/PMULL round functions are excluded from the throughput comparison.

## Reproduce

```bash
uv run python benchmark/simd_bit_benchmark.py --native
```

The harness picks `simd_x86_64_vs_naive.fr` on x86_64 and
`simd_arm64_vs_naive.fr` on arm64, builds both fixtures with `-Omax`
(plus `-march=native` with `--native`), runs each five times, verifies
that the module and naive checksums match, and prints one table per
buffer size on the SIMD fixture plus the `franko/bit` table.
