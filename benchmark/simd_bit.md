# ARM64 SIMD and scalar bit intrinsics vs naive Franko

**20 ARM64 SIMD functions + 8 `franko/bit` functions · 4,000,000 calls each · Apple M5 · 2026-11-17**

This report measures the two Franko intrinsic modules against naive scalar
Franko implementations:

- `franko/simd`, ARM64 namespace, excluding `vaeseq_u8` and `vaesdq_u8`
  (AES was excluded by request).
- `franko/bit`, all eight functions.

The naive implementations are ordinary Franko loops. Both the module call
and the naive loop are compiled with `-Omax` (C++ `-O3`, bounds checks
stripped). The naive side is deliberately allowed to be optimized by the
compiler; that is part of the measurement.

## Short version

- Simple bytewise operations are only **2.9x–3.5x** faster. `-O3`
  auto-vectorizes the naive byte loop, so both sides are often using NEON
  instructions.
- Branch-heavy and data-rearranging operations show the real gap:
  `vbslq_u8` **42x**, `vextq_u8` **25x**, `vabsq_s8` **26x**,
  `vmaxq_u8`/`vminq_u8` **16x**, `vqtbl1q_u8` **10x**,
  `vzip1q_u8`/`vzip2q_u8` **9.5x–10.4x**.
- `vqmovun_s16` is **1.9x** and `vpaddlq_u16` is **1.3x**. Their naive
  loops auto-vectorize well, and `vpaddlq_u16` produces only four `uint32`
  lanes per instruction.
- `popcount64` is the largest scalar win at **115x**, followed by
  `popcount32` at **46x** and `clz64` at **23x**.
- `bswap32` and `bswap64` show essentially no gain. The compiler
  recognizes the naive byte-reversal loop and emits the same `rev`
  instruction the intrinsic uses.

## Method

- **Host:** Apple M5, macOS 27.2.1, native arm64.
- **Build:** Franko `-Omax`, system allocator, `FRANKO_BOUNDS_UNCHECKED`.
- **Calls per case:** 4,000,000.
- **Timing:** `std::mono_time()`; median of five process runs.
- **SIMD buffer size:** 64 bytes per call, so most cases process four
  16-byte vectors.
- **`vqmovun_s16`:** 64 signed 16-bit lanes to 64 bytes.
- **`vpaddlq_u16`:** 128 unsigned 16-bit lanes to 64 unsigned 32-bit lanes.
- **`vabsq_s8`:** 64 signed bytes.
- **`vextq_u8`:** shift count 3.
- **`vbslq_u8`:** 64-byte mask, including mixed mask bytes, not only
  all-zero/all-one bytes.
- **`vqtbl1q_u8`:** 64 index bytes, including out-of-range indices.

Each timed loop resets all input buffers from master copies. During the loop,
one input byte rotates per iteration and a rotating target byte is
checksummed. This keeps the work alive and prevents the compiler from
hoisting or dead-store-eliminating most of the operation. The checksum from
the module path and the naive path matched exactly for every case and every
run.

The reported ns/op includes the harness overhead: one rotating input write
and one rotating target read per call. It is throughput per call, not
single-instruction latency.

## ARM64 SIMD

| Function | Franko module ns/op | Naive Franko ns/op | Speedup |
|---|---:|---:|---:|
| `veorq_u8` | 1.046 | 3.127 | 2.99x |
| `vandq_u8` | 1.058 | 3.106 | 2.94x |
| `vorrq_u8` | 1.065 | 3.137 | 2.95x |
| `vbicq_u8` | 1.056 | 3.135 | 2.97x |
| `vqaddq_u8` | 1.040 | 3.603 | 3.46x |
| `vqsubq_u8` | 1.052 | 3.029 | 2.88x |
| `vmaxq_u8` | 1.056 | 17.049 | 16.14x |
| `vminq_u8` | 1.040 | 16.956 | 16.31x |
| `vaddq_u8` | 1.051 | 3.097 | 2.95x |
| `vsubq_u8` | 1.045 | 3.139 | 3.00x |
| `vrhaddq_u8` | 1.056 | 3.111 | 2.94x |
| `vbslq_u8` | 1.057 | 44.815 | 42.41x |
| `vextq_u8` | 1.052 | 25.953 | 24.68x |
| `vzip1q_u8` | 1.063 | 10.087 | 9.49x |
| `vzip2q_u8` | 1.060 | 10.995 | 10.37x |
| `vqmovun_s16` | 1.133 | 2.111 | 1.86x |
| `vabdq_u8` | 1.048 | 3.128 | 2.99x |
| `vpaddlq_u16` | 3.120 | 3.919 | 1.26x |
| `vabsq_s8` | 1.079 | 27.689 | 25.67x |
| `vqtbl1q_u8` | 1.054 | 10.928 | 10.37x |

## `franko/bit`

| Function | Franko module ns/op | Naive Franko ns/op | Speedup |
|---|---:|---:|---:|
| `ctz32` | 0.090 | 1.255 | 13.94x |
| `ctz64` | 0.089 | 0.626 | 7.01x |
| `clz32` | 0.229 | 2.070 | 9.03x |
| `clz64` | 0.235 | 5.481 | 23.27x |
| `popcount32` | 0.200 | 9.201 | 46.12x |
| `popcount64` | 0.170 | 19.546 | 115.15x |
| `bswap32` | 0.177 | 0.184 | 1.04x |
| `bswap64` | 0.207 | 0.210 | 1.01x |

## Interpretation

The naive byte loop is not a scalar loop after `-O3`. For simple bitwise,
wrapping, saturating, and average operations, LLVM vectorizes the 64-byte
loop into NEON instructions. The module still wins because it avoids the
per-byte loop structure and uses wider operations directly, but the gap is
only about 3x.

The gap grows when the naive loop has branches or cross-lane movement:

- `vmaxq_u8`/`vminq_u8`: the naive comparison-and-select loop is not
  vectorized as effectively as the direct `vmax`/`vmin`.
- `vbslq_u8`: the bitwise select is slower in the naive form, likely
  because the compiler does not combine the mask expansion and select as
  tightly.
- `vextq_u8`, `vzip1q_u8`, `vzip2q_u8`, and `vqtbl1q_u8`: these rearrange
  bytes across lanes, which is exactly what the NEON instructions are for.
- `vabsq_s8`: the naive loop has per-byte conditional behavior.

`vqmovun_s16` and `vpaddlq_u16` are the two cases where the module wins
least. The naive loops vectorize well, and `vpaddlq_u16` has a relatively
low output-per-instruction ratio. On a larger buffer the per-call overhead
matters less, but the pairwise widening operation remains a modest win.

For `franko/bit`, the situation is different. LLVM does not turn the naive
`popcount`, `ctz`, or `clz` loops into the corresponding scalar
instructions, so the intrinsic wins by a large margin. `bswap` is the
exception: the compiler recognizes the byte-reversal loop and emits `rev`,
so the module and the naive loop are effectively identical.

## Caveats

- These are **64-byte call** numbers. Per-call overhead is a larger share of
  the total than it would be for a large buffer, so the SIMD advantage here
  is conservative for bulk processing.
- The harness itself adds one byte write and one byte read per call. The
  absolute ns/op is therefore slightly higher for both sides; the relative
  comparison is the useful part.
- The naive implementations are intentionally ordinary Franko. They were
  compiled with the same `-Omax` and were not blocked from auto-vectorizing.
  That is the point of the comparison.
- AES was excluded by request.
- `-Omax` removes the module's range checks, matching the unchecked release
  build. A checked build would add overhead to the module side.
- Run-to-run numbers vary with CPU frequency and thermal state. The report
  uses medians of five runs; the first process run can be slower for the
  earliest cases while the CPU ramps up.

## Reproduce

```bash
uv run python benchmark/simd_bit_benchmark.py
```

The harness builds both fixtures with `-Omax`, runs each five times, verifies
that the module and naive checksums match, and prints the two tables above.
Use `--runs N` for more samples and `--cpu N` to pin on systems that support
CPU affinity.
