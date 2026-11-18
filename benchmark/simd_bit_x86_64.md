# x86_64 SIMD and scalar bit intrinsics vs naive Franko

**19 `franko/simd` x86 functions × 3 buffer sizes (64 / 512 / 4096 bytes) + 8 `franko/bit` functions · Intel i5-12600K (WSL2) · `-march=native` · 2026-11-19**

This is the x86_64 report; the ARM64 counterpart is
`simd_bit_arm64.md`. It measures the two Franko intrinsic modules against
naive scalar Franko implementations:

- `franko/simd`, x86_64 namespace, excluding the AES round functions
  (`aesenc_si256`/`aesdec_si256` need AES-NI, which this build enables,
  but they are not throughput operations in the same sense; they were
  excluded from this comparison).
- `franko/bit`, all eight functions.

The naive implementations are ordinary Franko loops: single loads hoisted
into locals, canonical branches, no SIMD, no branchless bit-tricks. Both
the module call and the naive loop are compiled with `-Omax` (C++
`-O3`, bounds checks stripped) **plus `-march=native`**. The naive side
is deliberately allowed to be optimized by the compiler, including
256-bit AVX2 auto-vectorization; that is part of the measurement.

The `-march=native` flag matters twice here. In
`import/franko/simd.ffi.cpp`, `blendv_epi8` is a scalar loop unless
`__SSE4_1__` is defined, and `alignr_epi8`, `abs_epi8`, and
`shuffle_epi8` are scalar loops unless `__SSSE3__` is defined, so a
portable `-O3` build (x86-64 baseline, SSE2 only) benchmarks
scalar-vs-scalar on those four rows. Every number below is from a native
build, where each row additionally executes its AVX2 32-byte tier ahead of
the 128-bit path. The naive side gains AVX2 auto-vectorization from the
same flag.

## Short version

- Simple bytewise ops are at parity at all three sizes (**0.94x–1.24x**,
  with `abs` reaching 1.70x at 4096 bytes). Both sides emit 256-bit code,
  so the intrinsic wins nothing on pure elementwise arithmetic and logic.
- Where the naive loop stays scalar, the gap is large and grows with
  size: `sad` **7.07x → 40.19x → 26.02x**, `max`/`min` **5.8x → 20.5–21.9x
  → 27.7–30.2x**, `adds` **2.51x → 14.81x → 18.14x**, `shuffle` **3.29x →
  22.81x → 29.55x**, `blendv` **6.63x → 17.84x → 12.33x**, `alignr`
  **2.22x → 13.33x → 11.56x**, unpack **2.58x/2.86x → 9.38x/10.30x →
  11.75x/13.63x**, `packus` **1.72x → 4.93x → 5.00x**.
- `abs_epi8` is the closest row (**1.06x–1.70x**): the naive abs loop
  vectorizes well, so even real `pabsb` gains little on throughput.
- For `franko/bit` (one scalar value per call, no buffer), `popcount64`
  is the largest win at **51.18x**, followed by `popcount32` at **21.15x**
  and `clz64` at **20.47x**. `ctz` wins modestly (**2.62x–3.12x**). Both
  `bswap` cases are at parity — the compiler recognizes the naive
  byte-reversal loop and emits the same `rev` the intrinsic uses.

## Method

- **Host:** Intel i5-12600K, WSL2 (Linux 6.18.58.1-microsoft-standard),
  glibc 2.43, x86_64 (SSSE3, SSE4.1/4.2, AVX2, AES per `lscpu`), pinned
  to CPU 2.
- **Build:** Franko `-Omax` **with `-march=native`** (`--native`),
  system allocator, `FRANKO_BOUNDS_UNCHECKED`. Native affects both
  sides: the module gets its AVX2/SSSE3/SSE4.1/AES paths, the naive loops
  get AVX/AVX2 auto-vectorization.
- **Calls per case:** 4,000,000 at 64 bytes, 2,000,000 at 512 bytes,
  1,000,000 at 4096 bytes.
- **Timing:** `std::mono_time()`; median of five process runs. This run
  executed alone on the machine — nothing else ran concurrently.
- **SIMD buffer sizes:** 64, 512, and 4096 bytes per call, i.e. 4, 32,
  and 256 sixteen-byte vectors.
- **`packus_epi16`:** 64 / 512 / 4096 signed 16-bit lanes to the same
  number of bytes.
- **`abs_epi8`:** 64 / 512 / 4096 signed bytes.
- **`alignr_epi8`:** shift count 3 (Intel operand order: target is the
  high vector, source the low vector).
- **`blendv_epi8`:** 64/512/4096-byte mask using `0xFF`/`0x00` bytes
  plus one `0x0F` byte; selection is by bit 7.
- **`shuffle_epi8`:** index bytes covering the full `0..255` range at
  every size.
- **`sad_epu8`:** whole-buffer reduction returning one `uint64`.

Each timed loop resets all input buffers from master copies. During the loop,
one input byte rotates per iteration and a rotating target byte (or the
reduction result) is checksummed. This keeps the work alive and prevents the
compiler from hoisting or dead-store-eliminating most of the operation. The
checksum from the module path and the naive path matched exactly for every
case, every size, and every run.

The reported ns/op includes the harness overhead: one rotating input write
and one rotating target read per call. It is throughput per call, not
single-instruction latency. The overhead is constant per call, so it is a
large share at 64 bytes and negligible at 4096 bytes.

Naive semantics match the bridge implementation (Intel intrinsic
semantics): `andnot_si256` computes `~target & source` per
`_mm256_andnot_si256`, `shuffle_epi8` zeroes on bit 7 and otherwise uses
the low four index bits, and `alignr_epi8` follows
`_mm_alignr_epi8(target, source, shift)` with the target chunk as the
high half.

## x86_64 SIMD, 64-byte buffers

Median of runs, 4,000,000 calls per case.

| function | Franko module ns/op | Naive Franko ns/op | Speedup |
|---|---:|---:|---:|
| `xor_si256` | 4.986 | 5.006 | 1.00x |
| `and_si256` | 4.899 | 5.046 | 1.03x |
| `or_si256` | 4.802 | 4.901 | 1.02x |
| `andnot_si256` | 5.040 | 5.286 | 1.05x |
| `adds_epu8` | 4.851 | 12.182 | 2.51x |
| `subs_epu8` | 4.924 | 4.908 | 1.00x |
| `max_epu8` | 4.823 | 28.059 | 5.82x |
| `min_epu8` | 4.977 | 28.713 | 5.77x |
| `add_epi8` | 4.968 | 5.007 | 1.01x |
| `sub_epi8` | 4.841 | 5.032 | 1.04x |
| `avg_epu8` | 4.927 | 5.012 | 1.02x |
| `blendv_epi8` | 5.143 | 34.120 | 6.63x |
| `alignr_epi8` | 4.770 | 10.609 | 2.22x |
| `unpacklo_epi8` | 4.958 | 12.789 | 2.58x |
| `unpackhi_epi8` | 4.947 | 14.147 | 2.86x |
| `packus_epi16` | 5.984 | 10.279 | 1.72x |
| `sad_epu8` | 6.601 | 46.662 | 7.07x |
| `abs_epi8` | 5.204 | 5.527 | 1.06x |
| `shuffle_epi8` | 4.922 | 16.171 | 3.29x |

## x86_64 SIMD, 512-byte buffers

Median of runs, 2,000,000 calls per case.

| function | Franko module ns/op | Naive Franko ns/op | Speedup |
|---|---:|---:|---:|
| `xor_si256` | 6.121 | 5.848 | 0.96x |
| `and_si256` | 6.166 | 5.842 | 0.95x |
| `or_si256` | 6.238 | 5.834 | 0.94x |
| `andnot_si256` | 6.253 | 5.887 | 0.94x |
| `adds_epu8` | 5.509 | 81.603 | 14.81x |
| `subs_epu8` | 5.638 | 6.323 | 1.12x |
| `max_epu8` | 6.013 | 123.494 | 20.54x |
| `min_epu8` | 5.660 | 124.154 | 21.94x |
| `add_epi8` | 5.546 | 5.902 | 1.06x |
| `sub_epi8` | 5.659 | 5.923 | 1.05x |
| `avg_epu8` | 5.466 | 6.429 | 1.18x |
| `blendv_epi8` | 8.159 | 145.563 | 17.84x |
| `alignr_epi8` | 5.113 | 68.141 | 13.33x |
| `unpacklo_epi8` | 5.502 | 51.614 | 9.38x |
| `unpackhi_epi8` | 5.441 | 56.010 | 10.29x |
| `packus_epi16` | 13.652 | 67.321 | 4.93x |
| `sad_epu8` | 8.327 | 334.702 | 40.19x |
| `abs_epi8` | 5.551 | 6.219 | 1.12x |
| `shuffle_epi8` | 5.365 | 122.353 | 22.81x |

## x86_64 SIMD, 4096-byte buffers

Median of runs, 1,000,000 calls per case.

| function | Franko module ns/op | Naive Franko ns/op | Speedup |
|---|---:|---:|---:|
| `xor_si256` | 32.387 | 35.328 | 1.09x |
| `and_si256` | 32.359 | 35.962 | 1.11x |
| `or_si256` | 35.094 | 35.602 | 1.01x |
| `andnot_si256` | 35.303 | 43.749 | 1.24x |
| `adds_epu8` | 35.703 | 647.783 | 18.14x |
| `subs_epu8` | 33.461 | 33.170 | 0.99x |
| `max_epu8` | 32.552 | 902.986 | 27.74x |
| `min_epu8` | 33.236 | 1004.223 | 30.21x |
| `add_epi8` | 32.822 | 33.677 | 1.03x |
| `sub_epi8` | 35.314 | 33.677 | 0.95x |
| `avg_epu8` | 34.599 | 34.311 | 0.99x |
| `blendv_epi8` | 84.820 | 1046.138 | 12.33x |
| `alignr_epi8` | 40.321 | 466.017 | 11.56x |
| `unpacklo_epi8` | 35.155 | 413.211 | 11.75x |
| `unpackhi_epi8` | 33.552 | 457.399 | 13.63x |
| `packus_epi16` | 109.061 | 544.761 | 5.00x |
| `sad_epu8` | 88.835 | 2311.197 | 26.02x |
| `abs_epi8` | 31.925 | 54.254 | 1.70x |
| `shuffle_epi8` | 38.576 | 1139.966 | 29.55x |

## `franko/bit`

Median of runs, 4,000,000 calls per case.

| function | Franko module ns/op | Naive Franko ns/op | Speedup |
|---|---:|---:|---:|
| `ctz32` | 0.460 | 1.204 | 2.62x |
| `ctz64` | 0.502 | 1.566 | 3.12x |
| `clz32` | 0.447 | 4.069 | 9.10x |
| `clz64` | 0.459 | 9.389 | 20.46x |
| `popcount32` | 0.454 | 9.596 | 21.14x |
| `popcount64` | 0.454 | 23.217 | 51.14x |
| `bswap32` | 0.123 | 0.115 | 0.93x |
| `bswap64` | 0.274 | 0.275 | 1.00x |

## Interpretation

Two groups, separated by whether the compiler vectorizes the naive loop
under `-march=native`.

**Parity where both vectorize.** Bitwise logic, wrapping arithmetic,
average, saturating subtract, and absolute value measure within ~25% of
each other at every size (`abs` at 4 KB is the widest at 1.70x). Both
sides emit 256-bit code here (the module through its AVX2 tiers, the
naive loop through auto-vectorization), so per-call overhead and loop
shape decide small differences, not the instruction set.

**Large wins where only the module vectorizes.** `max`/`min` (branch),
`adds` (saturating branch), `blendv` (mask test), `alignr`/`shuffle`
(byte rearrangement), unpack/packus (lane movement), and `sad`
(reduction) defeat the auto-vectorizer at every size, so the gap opens
as the per-call floor amortizes:

| function | 64 B | 512 B | 4096 B |
|---|---:|---:|---:|
| `adds_epu8` | 2.51x | 14.81x | 18.14x |
| `max_epu8` | 5.82x | 20.54x | 27.74x |
| `min_epu8` | 5.77x | 21.94x | 30.21x |
| `blendv_epi8` | 6.63x | 17.84x | 12.33x |
| `alignr_epi8` | 2.22x | 13.33x | 11.56x |
| `unpacklo_epi8` | 2.58x | 9.38x | 11.75x |
| `unpackhi_epi8` | 2.86x | 10.29x | 13.63x |
| `packus_epi16` | 1.72x | 4.93x | 5.00x |
| `sad_epu8` | 7.07x | 40.19x | 26.02x |
| `shuffle_epi8` | 3.29x | 22.81x | 29.55x |

The 64-byte column understates every one of these: the intrinsic side
sits near its ~5 ns per-call floor while the naive side already pays
full per-byte cost. By 512 bytes the ranking stabilizes near its bulk
value.

One methodology note: the naive baseline was revised once mid-project
(canonical `max`/`min` branches, loads hoisted into locals). That made
the naive loops faster and is the honest direction, but it also left the
64-byte `unpacklo` row the least stable in the report (LLVM sometimes
vectorizes the cleaned-up tail at 64 bytes). The 512- and 4096-byte rows
are stable across runs.

**Absolute throughput check.** At 4096 bytes the module moves 4 KB in
~32–36 ns for simple and interleave ops (~115–125 GB/s, the 256-bit
L1-resident streaming rate), ~40 ns for `alignr`, ~39 ns for `shuffle`,
~85–89 ns for `blendv`/`sad`, ~110 ns for `packus`, and ~32 ns for
`abs`. These are L1-resident streaming numbers on this chip.

For `franko/bit`, the picture splits three ways. `popcount` and `clz`
win big (9x–51x): LLVM does not turn the naive shift-and-count loops
into `popcnt`/`lzcnt`. (`popcount64` at 51.1x is the largest speedup in
this report.) `ctz` wins modestly (2.62x–3.12x) because the compiler
*does* recognize the naive trailing-zero loop and emits `tzcnt` — the
intrinsic mostly saves loop overhead. `bswap` is parity: the naive
rotation loop becomes the same `rev` the intrinsic uses.

The bit cases report sub-nanosecond medians. That is throughput across
4,000,000 back-to-back calls with the loop overhead mostly amortized —
superscalar issue of single-cycle instructions — not single-call
latency. The relative column is the useful part.

## Caveats

- A portable (non-`-march=native`) build leaves `blendv` on its SSE4.1
  scalar fallback and `alignr`/`abs`/`shuffle` on their SSSE3 scalar
  fallbacks, and AES returns an error code. The numbers above are all
  native builds; do not compare them against portable-build speedups
  for those four rows.
- The harness itself adds one byte write and one byte read per call. The
  absolute ns/op is therefore slightly higher for both sides; the relative
  comparison is the useful part. At 64 bytes the harness is a visible
  share; at 4096 bytes it is negligible.
- The naive implementations are intentionally ordinary Franko. They were
  compiled with the same `-Omax -march=native` and were not blocked from
  auto-vectorizing. That is the point of the comparison.
- Byte-level exactness was verified, not assumed: module and naive
  checksums matched for every case, size, and run, including the
  `andnot` operand order, the `shuffle` zeroing rule, and the `alignr`
  shift-3 window.
- The x86 module surface has no per-lane unsigned absolute-difference or
  pairwise-widening-sum op, so there is no `vabd`/`vpaddlq` row here;
  `sad_epu8` covers the abs-diff story as a reduction instead.
- AES round functions were excluded from both sides.
- `-Omax` removes the module's range checks, matching the unchecked release
  build. A checked build would add overhead to the module side.
- WSL2 virtualizes this CPU; absolute timings may differ from bare metal,
  but both sides ran under the same virtualization. Medians of five runs
  are reported; the first process run can be slower while the CPU ramps up.
- Do not compare absolute ns/op against machines used for earlier
  revisions of this report; compare speedups.

## Reproduce

```bash
uv run python benchmark/simd_bit_benchmark.py --cpu 2 --native
```

The harness picks the SIMD fixture by host architecture
(`simd_x86_64_vs_naive.fr` on x86_64, `simd_arm64_vs_naive.fr` on
arm64), builds both fixtures with `-Omax` (plus `-march=native` with
`--native`), runs each five times, verifies that the module and naive
checksums match, and prints one table per buffer size on the SIMD
fixture. Use `--runs N` for more samples and `--cpu N` to pin on systems
that support CPU affinity. Without `--native`, the SSSE3/SSE4.1-gated
rows (`blendv`, `alignr`, `abs`, `shuffle`) run their scalar fallbacks
on the module side.
