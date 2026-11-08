# In-place quicksort benchmark: Franko vs C++ vs std::sort

**Plain `int64` keys · 5 sizes × 6 distributions · all chunk timing · 10M: 5, 1M: 10, 99k: 300, 10k: 3,000, 1k: 30,000 · 2026-11-09**

This report compares in-place sorting only: Franko `-Omax`, a
hand-written C++ `-O3` port of the same algorithm, and C++ `std::sort`
as the reference.

Distributions are listed from highest duplicate concentration to
lowest:

- **`0-9`:** 10 distinct values.
- **`0.25pct`:** `N / 400` distinct values.
- **`0.5pct`:** `N / 200` distinct values.
- **`1pct`:** `N / 100` distinct values.
- **`10pct`:** `N / 10` distinct values.
- **`random`:** full 63-bit random values, effectively all distinct.

All percentage datasets are relative to `N`.

The short version:

- `0-9` wins every size: Franko is **0.58x–0.87x** and C++ is
  **0.58x–0.86x** of `std::sort`.
- `0.25pct` is 1.02–1.05x slower at 10M/1M, but the fixed probes switch
  it to DNF at 99k/10k, where it is **0.76x–0.90x** of `std::sort`.
- `0.5pct` is 1.03–1.06x slower at 10M/1M; at 99k the probe misses and
  it stays on Lomuto, while at 10k the probe finds duplicates and it
  drops to **0.85x–0.90x**.
- `1pct` is roughly 1.0–1.08x at 10M/1M, 1.09–1.12x at 99k, and
  1.14–1.19x at 10k. At 1k the Hoare path makes it 0.88–0.90x.
- `10pct` and `random` are mostly at parity; the custom sorts are
  1–4% slower at 10M/1M and slightly faster at 1k.
- All timing is chunk-based now; 10M/1M are one process with 5/10
  sorts, so they reuse one allocation and are not directly comparable
  to the earlier separate-process rows.
- Franko vs hand-written C++ stays within about 5% on most cells; the
  smallest duplicate-heavy rows show gaps up to 19%.

---

## 1. Bottom line

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | **0.113715** | 0.113852 | 0.197340 |
| `10M` | `0.25pct` | 0.483435 | 0.482612 | **0.461432** |
| `10M` | `0.5pct` | 0.506484 | 0.504656 | **0.478946** |
| `10M` | `1pct` | 0.548177 | 0.542177 | **0.507301** |
| `10M` | `10pct` | 0.620321 | 0.619167 | **0.606066** |
| `10M` | `random` | 0.638278 | 0.624685 | **0.613260** |
| `1M` | `0-9` | **0.011032** | 0.011350 | 0.017195 |
| `1M` | `0.25pct` | 0.037768 | 0.037370 | **0.036604** |
| `1M` | `0.5pct` | 0.040051 | 0.039769 | **0.038781** |
| `1M` | `1pct` | 0.042748 | **0.041690** | 0.042318 |
| `1M` | `10pct` | 0.051714 | 0.051688 | **0.050757** |
| `1M` | `random` | 0.053041 | 0.051825 | **0.051318** |
| `99k` | `0-9` | **0.001055** | 0.001101 | 0.001490 |
| `99k` | `0.25pct` | **0.002413** | 0.002417 | 0.002697 |
| `99k` | `0.5pct` | 0.003263 | 0.003098 | **0.003061** |
| `99k` | `1pct` | 0.003698 | 0.003579 | **0.003293** |
| `99k` | `10pct` | 0.004230 | 0.004124 | **0.004113** |
| `99k` | `random` | 0.004222 | **0.004108** | 0.004231 |
| `10k` | `0-9` | **0.000079** | 0.000098 | 0.000129 |
| `10k` | `0.25pct` | **0.000135** | 0.000150 | 0.000178 |
| `10k` | `0.5pct` | **0.000174** | 0.000184 | 0.000205 |
| `10k` | `1pct` | 0.000276 | 0.000265 | **0.000232** |
| `10k` | `10pct` | 0.000325 | **0.000323** | **0.000323** |
| `10k` | `random` | 0.000330 | **0.000317** | 0.000329 |
| `1k` | `0-9` | 0.0000045341 | **0.0000045100** | 0.0000052226 |
| `1k` | `0.25pct` | 0.0000051415 | **0.0000050994** | 0.0000058857 |
| `1k` | `0.5pct` | 0.0000055297 | **0.0000055288** | 0.0000060223 |
| `1k` | `1pct` | **0.0000056596** | 0.0000057838 | 0.0000064319 |
| `1k` | `10pct` | **0.0000068514** | 0.0000071964 | 0.0000074028 |
| `1k` | `random` | **0.0000057504** | 0.0000058978 | 0.0000063051 |

All per-sort means are chunk wall time divided by the repetition count.

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | ---: | ---: |
| `10M` | `0-9` | **0.576x** | **0.577x** |
| `10M` | `0.25pct` | 1.048x | 1.046x |
| `10M` | `0.5pct` | 1.057x | 1.054x |
| `10M` | `1pct` | 1.081x | 1.069x |
| `10M` | `10pct` | 1.024x | 1.022x |
| `10M` | `random` | 1.041x | 1.019x |
| `1M` | `0-9` | **0.642x** | **0.660x** |
| `1M` | `0.25pct` | 1.032x | 1.021x |
| `1M` | `0.5pct` | 1.033x | 1.025x |
| `1M` | `1pct` | 1.010x | **0.985x** |
| `1M` | `10pct` | 1.019x | 1.018x |
| `1M` | `random` | 1.034x | 1.010x |
| `99k` | `0-9` | **0.708x** | **0.739x** |
| `99k` | `0.25pct` | **0.895x** | **0.896x** |
| `99k` | `0.5pct` | 1.066x | 1.012x |
| `99k` | `1pct` | 1.123x | 1.087x |
| `99k` | `10pct` | 1.028x | 1.003x |
| `99k` | `random` | **0.998x** | **0.971x** |
| `10k` | `0-9` | **0.612x** | **0.760x** |
| `10k` | `0.25pct` | **0.758x** | **0.843x** |
| `10k` | `0.5pct` | **0.849x** | **0.898x** |
| `10k` | `1pct` | 1.190x | 1.142x |
| `10k` | `10pct` | 1.006x | 1.000x |
| `10k` | `random` | 1.003x | **0.964x** |
| `1k` | `0-9` | **0.868x** | **0.864x** |
| `1k` | `0.25pct` | **0.874x** | **0.866x** |
| `1k` | `0.5pct` | **0.918x** | **0.918x** |
| `1k` | `1pct` | **0.880x** | **0.899x** |
| `1k` | `10pct` | **0.926x** | **0.972x** |
| `1k` | `random` | **0.912x** | **0.935x** |

A value below `1.00x` means the implementation is faster than
`std::sort`.

Key points:

- **Duplicate-heavy `0-9` is a clean win at every size.** The custom
  sorts are 1.2–1.7x faster at 10M–10k and about 1.15x faster at 1k.
- **The new relative percentage datasets change the small sizes.** At
  99k/10k, `0.25pct` and `0.5pct` are genuinely duplicate-heavy, and
  the fixed probes often switch them to DNF.
- **The probe boundary is visible.** `0.5pct` at 99k and `1pct` at
  99k/10k miss duplicates in their fixed samples, stay on Lomuto, and
  lose to `std::sort`.
- **`std::sort` remains strongest on 10M high-cardinality data.** The
  custom sorts are 1–8% slower there.
- **All timing is chunk-based.** The 10M/1M rows now reuse one data
  allocation across 5/10 sorts, so they are not directly comparable to
  the old separate-process rows.

---

## 2. What was measured

### 2.1 Sizes, datasets, and exact distinct counts

All programs generate the same splitmix64 stream and map `z` through
the dataset modulus. Percentage datasets are relative to `N`:

| dataset | distinct values |
| --- | --- |
| `0-9` | `z % 10` |
| `0.25pct` | `z % max(1, N / 400)` |
| `0.5pct` | `z % max(1, N / 200)` |
| `1pct` | `z % max(1, N / 100)` |
| `10pct` | `z % max(1, N / 10)` |
| `random` | `z & 0x7FFFFFFFFFFFFFFF` |

Exact distinct counts per size:

| size | keys | `0.25pct` | `0.5pct` | `1pct` | `10pct` |
| --- | ---: | ---: | ---: | ---: | ---: |
| `10M` | 10,000,000 | 25,000 | 50,000 | 100,000 | 1,000,000 |
| `1M` | 1,000,000 | 2,500 | 5,000 | 10,000 | 100,000 |
| `99k` | 99,000 | 247 | 495 | 990 | 9,900 |
| `10k` | 10,000 | 25 | 50 | 100 | 1,000 |
| `1k` | 1,000 | 2 | 5 | 10 | 100 |

### 2.2 Implementations

| Program | What it is |
| --- | --- |
| `quicksort_inplace_franko.fr` | Franko `-Omax`, in-place value sort |
| `quicksort_inplace_cpp.cpp` | Hand-written C++ `-O3` port, in-place value sort |
| `quicksort_std_cpp.cpp` | C++ `-O3`, `std::sort` reference |

### 2.3 Algorithm

Franko and the hand-written C++ port use the same adaptive algorithm.
The preflight is tiered with half-open ranges:

- **`N >= 100000`:** a stratified sample of `k = N / 1000` indices is
  sorted and the average duplicate-group size is estimated. If
  `m_hat > 60`, the sort uses DNF; otherwise Lomuto. There is no depth
  guard on this path.
- **`20000 <= N < 100000`:** a fixed 31-element sample is drawn, one
  random offset from each equal segment, using `RANDOM_PIVOTS`. It is
  insertion-sorted inline; any duplicate selects DNF, otherwise
  Lomuto. Lomuto gets a `1.75 * log2(N)` depth budget.
- **`2000 <= N < 20000`:** the same rule with a fixed 15-element
  sample. Lomuto gets a `2 * log2(N)` depth budget.
- **`N < 2000`:** Hoare partitioning with a median-of-3 pivot, no
  depth guard, and the 32-element small-sort cutoff.

The DNF choice for this benchmark is:

| size | DNF datasets | Lomuto datasets |
| --- | --- | --- |
| `10M` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `1M` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `99k` | `0-9`, `0.25pct` | `0.5pct`, `1pct`, `10pct`, `random` |
| `10k` | `0-9`, `0.25pct`, `0.5pct` | `1pct`, `10pct`, `random` |
| `1k` | none (Hoare median-of-3) | all |

The 10M/1M rows use the estimator because `N >= 100000`. The 99k row
uses the 31-element probe; the 10k row uses the 15-element probe. The
1k row uses the `<2000` Hoare path.

The rest of the machinery is shared:

- **Fixed cutoff:** recursion stops at 32 elements. Sizes 2–8 use
  sorting networks; sizes 9–32 use insertion sort.
- **Iterative control flow:** the larger partition stays in a while
  loop; only the smaller side recurses.
- **Comparison discipline:** all element comparisons use only `<`.

### 2.4 Timing methodology

All rows are now timed as one chunk per mode and dataset:

| size | count | sorts per chunk |
| --- | ---: | ---: |
| `10M` | 10,000,000 | 5 |
| `1M` | 1,000,000 | 10 |
| `99k` | 99,000 | 300 |
| `10k` | 10,000 | 3,000 |
| `1k` | 1,000 | 30,000 |

The binary allocates its data vector once per process and loops
generation + sort + verification + checksum inside the chunk. Python
times the whole process and divides by the repetition count. This
amortizes process startup and allocation over the chunk. There are no
individual per-sort samples; each cell is a single chunk measurement.

The run used `g++ 15.2.0` under WSL2 on a 12th-gen Intel i5-12600K,
pinned to CPU 2. Absolute times are host-sensitive.

---

## 3. Results

### 3.1 Per-key nanoseconds

Whole-workload cost (generation + sort + verification + checksum)
divided by `N`:

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | **11.37** | 11.39 | 19.73 |
| `10M` | `0.25pct` | 48.34 | 48.26 | **46.14** |
| `10M` | `0.5pct` | 50.65 | 50.47 | **47.89** |
| `10M` | `1pct` | 54.82 | 54.22 | **50.73** |
| `10M` | `10pct` | 62.03 | 61.92 | **60.61** |
| `10M` | `random` | 63.83 | 62.47 | **61.33** |
| `1M` | `0-9` | **11.03** | 11.35 | 17.20 |
| `1M` | `0.25pct` | 37.77 | 37.37 | **36.60** |
| `1M` | `0.5pct` | 40.05 | 39.77 | **38.78** |
| `1M` | `1pct` | 42.75 | **41.69** | 42.32 |
| `1M` | `10pct` | 51.71 | 51.69 | **50.76** |
| `1M` | `random` | 53.04 | 51.83 | **51.32** |
| `99k` | `0-9` | **10.66** | 11.12 | 15.05 |
| `99k` | `0.25pct` | **24.37** | 24.41 | 27.24 |
| `99k` | `0.5pct` | 32.96 | 31.29 | **30.92** |
| `99k` | `1pct` | 37.35 | 36.15 | **33.26** |
| `99k` | `10pct` | 42.73 | 41.66 | **41.55** |
| `99k` | `random` | 42.65 | **41.49** | 42.74 |
| `10k` | `0-9` | **7.90** | 9.80 | 12.90 |
| `10k` | `0.25pct` | **13.50** | 15.00 | 17.80 |
| `10k` | `0.5pct` | **17.40** | 18.40 | 20.50 |
| `10k` | `1pct` | 27.60 | 26.50 | **23.20** |
| `10k` | `10pct` | 32.50 | **32.30** | **32.30** |
| `10k` | `random` | 33.00 | **31.70** | 32.90 |
| `1k` | `0-9` | 4.53 | **4.51** | 5.22 |
| `1k` | `0.25pct` | 5.14 | **5.10** | 5.89 |
| `1k` | `0.5pct` | 5.53 | **5.53** | 6.02 |
| `1k` | `1pct` | **5.66** | 5.78 | 6.43 |
| `1k` | `10pct` | **6.85** | 7.20 | 7.40 |
| `1k` | `random` | **5.75** | 5.90 | 6.31 |

The 10M/1M per-key costs are higher than the 99k/10k rows because the
80 MB and 8 MB arrays do not fit in the same cache levels; the 1k row
fits in L1/L2 and is dominated by per-sort overhead.

### 3.2 Chunk timings

Full chunk wall times and per-sort means are in Appendix A.

---

## 4. Analysis

### 4.1 DNF wins the truly duplicate-heavy rows

The new relative percentage datasets make the small-size duplicate
structure explicit:

- `0-9` is DNF at every size from 10M to 10k and Hoare at 1k. It wins
  every size, by 1.2–1.7x at 10M–10k.
- `0.25pct` is DNF at 10M/1M, 99k, and 10k. At 99k it is 0.895x of
  `std::sort`; at 10k it is 0.758x/0.843x. At 10M/1M the DNF overhead
  is slightly larger than `std::sort`'s duplicate handling (1.02–1.05x
  slower), and at 1k the Hoare path is 0.87x.
- `0.5pct` is DNF at 10M/1M and 10k, but the 31-element probe misses
  duplicates at 99k, so that row stays on Lomuto (1.066x/1.012x). At
  10k the probe finds duplicates and the row drops to 0.849x/0.898x.
- `1pct` is DNF only at 10M/1M. The 31-element and 15-element probes
  miss duplicates at 99k and 10k, so those rows stay on Lomuto and are
  1.09–1.19x slower than `std::sort`.
- `10pct` and `random` are Lomuto at 10M–10k and Hoare at 1k. They are
  close to `std::sort`: 1–4% slower at 10M/1M, near parity at 99k/10k,
  and faster at 1k.

The probe boundary is the main small-size story: a missed duplicate
sample costs the `0.5pct` 99k and `1pct` 99k/10k rows.

### 4.2 All-chunk timing changes the large-size baseline

The 10M and 1M rows are now one process per cell, with 5 or 10 sorts
inside it and one data-vector allocation. That removes 4/9 process
starts and repeated 80 MB/8 MB allocations per cell. The 10M `0-9`
custom time is 0.114 s here, versus roughly 0.136 s in the previous
separate-process matrix. The comparison between modes inside this
report is still valid, but the absolute values should not be compared
directly to reports that timed each sort in its own process.

### 4.3 Cache effects

The high-cardinality rows show the expected per-key scaling:

| dataset | 10M | 1M | 99k | 10k | 1k |
| --- | ---: | ---: | ---: | ---: | ---: |
| `1pct` (Franko) | 54.8 ns | 42.8 ns | 37.4 ns | 27.6 ns | 5.7 ns |
| `10pct` (Franko) | 62.0 ns | 51.7 ns | 42.7 ns | 32.5 ns | 6.9 ns |
| `random` (Franko) | 63.8 ns | 53.0 ns | 42.7 ns | 33.0 ns | 5.8 ns |

At 10M the 80 MB array exceeds L3; at 1M the 8 MB array fits in L3;
at 99k the 0.8 MB array fits in L2; at 10k and 1k the arrays fit in
L1/L2. The 1k row is also where the Hoare median-of-3 path starts.

### 4.4 Franko versus hand-written C++

Franko and the C++ port stay within about 5% on most cells. The
largest gaps are:

- `10M` `0-9`: Franko 0.113715 s vs C++ 0.113852 s (0.1% faster);
- `1M` `0-9`: Franko 0.011032 s vs C++ 0.011350 s (2.8% faster);
- `10k` `0-9`: Franko 0.000079 s vs C++ 0.000098 s (19% faster);
- `10k` `0.25pct`: Franko 0.000135 s vs C++ 0.000150 s (10% faster).

At 1k, the timer resolution is only 1 µs, so those ratios are coarse;
the exact chunk means show the two implementations within about 3%.

### 4.5 The algorithm versus `std::sort`

- The custom sorts win every `0-9` cell, every `0.25pct` cell at
  99k/10k, `0.5pct` at 10k, and every 1k cell.
- `std::sort` wins the high-cardinality 10M/1M rows (1–8%) and the
  `0.5pct`/`1pct` rows where the probe misses duplicates.
- At 99k/10k, the custom sorts are at or slightly faster than
  `std::sort` on the Lomuto-only high-cardinality rows.

The remaining losses are small and concentrated where the fixed probe
does not see a duplicate.

### 4.6 Noise

Every cell is a single chunk measurement, so there is no run-to-run
standard deviation. The chunk means are stable enough for the large
rows, but the 1k row is only 5–7 µs per sort, and its per-sort mean is
printed to 1 µs resolution. The 1k slowdown ratios should be read as
approximate. The other rows are 10–300 ms per chunk and are not
resolution-limited.

---

## 5. Limitations

1. **No independent per-sort samples.** All cells are one chunk. There
   is no SD or CV table; differences of a few percent are not
   statistically resolved.
2. **10M/1M chunk reuse.** One allocation is reused across 5/10 sorts.
   Absolute 10M/1M times are lower than separate-process measurements.
3. **1k timer resolution.** The 1k per-sort means are 5–7 µs and are
   printed to 1 µs; those ratios are coarse.
4. **Synthetic uniform frequencies.** The percentage datasets are
   uniform over `N/400`, `N/200`, `N/100`, and `N/10` values. Skewed
   real data changes the estimator and probe behavior.
5. **Fixed probes are heuristics.** The 31- and 15-element samples take
   one random offset per equal segment; they can miss duplicates.
   `0.5pct` at 99k and `1pct` at 99k/10k are the visible misses.
6. **The `<2000` path has no depth guard.** It relies on Hoare
   median-of-3 and the 32-element small-sort cutoff.
7. **`std::sort` is introsort.** It is a strong reference, not an exact
   algorithm match.
8. **One machine.** WSL2 on a 12th-gen i5-12600K with CPU pinning.
   Absolute times are host-sensitive.
9. **Only maximum release builds.** Franko `-Omax` and C++ `-O3`;
   checked/sanitizer builds are not included.
10. **Binary size includes static runtimes.** Franko is 944 KB;
    C++ in-place is 162 KB and `std::sort` 154 KB.
11. **Tuned constants.** The estimator cutoff, sample thresholds,
    sample sizes, 32-element cutoff, depth factors, and `m_hat > 60`
    are tuned to this workload.

---

## 6. Reproduce

```bash
uv run python benchmark/quicksort_benchmark.py --cpu 2
```

The harness builds and runs:

- `benchmark/quicksort_inplace_franko.fr`
- `benchmark/quicksort_inplace_cpp.cpp`
- `benchmark/quicksort_std_cpp.cpp`

Each binary accepts `<dataset> <count> <repeat>`:

| argument | dataset |
| --- | --- |
| `3` | `0-9` |
| `5` | `0.25pct` |
| `4` | `0.5pct` |
| `2` | `1pct` |
| `1` | `10pct` |
| `0` | `random` |

The harness uses 5/10/300/3000/30000 repetitions for
10M/1M/99k/10k/1k respectively, one chunk per cell.

---

## Appendix A: chunk timings

| size | dataset | mode | chunk s | reps | mean/sort |
| --- | --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | franko-inplace | 0.568577 | 5 | 0.113715 |
| `10M` | `0-9` | cpp-inplace | 0.569262 | 5 | 0.113852 |
| `10M` | `0-9` | cpp-std-sort | 0.986701 | 5 | 0.197340 |
| `10M` | `0.25pct` | franko-inplace | 2.417176 | 5 | 0.483435 |
| `10M` | `0.25pct` | cpp-inplace | 2.413060 | 5 | 0.482612 |
| `10M` | `0.25pct` | cpp-std-sort | 2.307162 | 5 | 0.461432 |
| `10M` | `0.5pct` | franko-inplace | 2.532421 | 5 | 0.506484 |
| `10M` | `0.5pct` | cpp-inplace | 2.523280 | 5 | 0.504656 |
| `10M` | `0.5pct` | cpp-std-sort | 2.394731 | 5 | 0.478946 |
| `10M` | `1pct` | franko-inplace | 2.740884 | 5 | 0.548177 |
| `10M` | `1pct` | cpp-inplace | 2.710883 | 5 | 0.542177 |
| `10M` | `1pct` | cpp-std-sort | 2.536505 | 5 | 0.507301 |
| `10M` | `10pct` | franko-inplace | 3.101606 | 5 | 0.620321 |
| `10M` | `10pct` | cpp-inplace | 3.095837 | 5 | 0.619167 |
| `10M` | `10pct` | cpp-std-sort | 3.030328 | 5 | 0.606066 |
| `10M` | `random` | franko-inplace | 3.191391 | 5 | 0.638278 |
| `10M` | `random` | cpp-inplace | 3.123424 | 5 | 0.624685 |
| `10M` | `random` | cpp-std-sort | 3.066302 | 5 | 0.613260 |
| `1M` | `0-9` | franko-inplace | 0.110317 | 10 | 0.011032 |
| `1M` | `0-9` | cpp-inplace | 0.113504 | 10 | 0.011350 |
| `1M` | `0-9` | cpp-std-sort | 0.171951 | 10 | 0.017195 |
| `1M` | `0.25pct` | franko-inplace | 0.377683 | 10 | 0.037768 |
| `1M` | `0.25pct` | cpp-inplace | 0.373699 | 10 | 0.037370 |
| `1M` | `0.25pct` | cpp-std-sort | 0.366038 | 10 | 0.036604 |
| `1M` | `0.5pct` | franko-inplace | 0.400511 | 10 | 0.040051 |
| `1M` | `0.5pct` | cpp-inplace | 0.397693 | 10 | 0.039769 |
| `1M` | `0.5pct` | cpp-std-sort | 0.387814 | 10 | 0.038781 |
| `1M` | `1pct` | franko-inplace | 0.427483 | 10 | 0.042748 |
| `1M` | `1pct` | cpp-inplace | 0.416897 | 10 | 0.041690 |
| `1M` | `1pct` | cpp-std-sort | 0.423185 | 10 | 0.042318 |
| `1M` | `10pct` | franko-inplace | 0.517140 | 10 | 0.051714 |
| `1M` | `10pct` | cpp-inplace | 0.516878 | 10 | 0.051688 |
| `1M` | `10pct` | cpp-std-sort | 0.507568 | 10 | 0.050757 |
| `1M` | `random` | franko-inplace | 0.530410 | 10 | 0.053041 |
| `1M` | `random` | cpp-inplace | 0.518249 | 10 | 0.051825 |
| `1M` | `random` | cpp-std-sort | 0.513176 | 10 | 0.051318 |
| `99k` | `0-9` | franko-inplace | 0.316379 | 300 | 0.001055 |
| `99k` | `0-9` | cpp-inplace | 0.330233 | 300 | 0.001101 |
| `99k` | `0-9` | cpp-std-sort | 0.447110 | 300 | 0.001490 |
| `99k` | `0.25pct` | franko-inplace | 0.724007 | 300 | 0.002413 |
| `99k` | `0.25pct` | cpp-inplace | 0.725076 | 300 | 0.002417 |
| `99k` | `0.25pct` | cpp-std-sort | 0.809067 | 300 | 0.002697 |
| `99k` | `0.5pct` | franko-inplace | 0.978914 | 300 | 0.003263 |
| `99k` | `0.5pct` | cpp-inplace | 0.929276 | 300 | 0.003098 |
| `99k` | `0.5pct` | cpp-std-sort | 0.918367 | 300 | 0.003061 |
| `99k` | `1pct` | franko-inplace | 1.109373 | 300 | 0.003698 |
| `99k` | `1pct` | cpp-inplace | 1.073673 | 300 | 0.003579 |
| `99k` | `1pct` | cpp-std-sort | 0.987831 | 300 | 0.003293 |
| `99k` | `10pct` | franko-inplace | 1.268925 | 300 | 0.004230 |
| `99k` | `10pct` | cpp-inplace | 1.237302 | 300 | 0.004124 |
| `99k` | `10pct` | cpp-std-sort | 1.233900 | 300 | 0.004113 |
| `99k` | `random` | franko-inplace | 1.266726 | 300 | 0.004222 |
| `99k` | `random` | cpp-inplace | 1.232525 | 300 | 0.004108 |
| `99k` | `random` | cpp-std-sort | 1.269284 | 300 | 0.004231 |
| `10k` | `0-9` | franko-inplace | 0.237706 | 3000 | 0.000079 |
| `10k` | `0-9` | cpp-inplace | 0.294363 | 3000 | 0.000098 |
| `10k` | `0-9` | cpp-std-sort | 0.385621 | 3000 | 0.000129 |
| `10k` | `0.25pct` | franko-inplace | 0.404717 | 3000 | 0.000135 |
| `10k` | `0.25pct` | cpp-inplace | 0.450795 | 3000 | 0.000150 |
| `10k` | `0.25pct` | cpp-std-sort | 0.535331 | 3000 | 0.000178 |
| `10k` | `0.5pct` | franko-inplace | 0.522662 | 3000 | 0.000174 |
| `10k` | `0.5pct` | cpp-inplace | 0.552225 | 3000 | 0.000184 |
| `10k` | `0.5pct` | cpp-std-sort | 0.613846 | 3000 | 0.000205 |
| `10k` | `1pct` | franko-inplace | 0.826686 | 3000 | 0.000276 |
| `10k` | `1pct` | cpp-inplace | 0.795389 | 3000 | 0.000265 |
| `10k` | `1pct` | cpp-std-sort | 0.695857 | 3000 | 0.000232 |
| `10k` | `10pct` | franko-inplace | 0.975183 | 3000 | 0.000325 |
| `10k` | `10pct` | cpp-inplace | 0.968035 | 3000 | 0.000323 |
| `10k` | `10pct` | cpp-std-sort | 0.970446 | 3000 | 0.000323 |
| `10k` | `random` | franko-inplace | 0.990780 | 3000 | 0.000330 |
| `10k` | `random` | cpp-inplace | 0.951023 | 3000 | 0.000317 |
| `10k` | `random` | cpp-std-sort | 0.987816 | 3000 | 0.000329 |
| `1k` | `0-9` | franko-inplace | 0.136023 | 30000 | 0.0000045341 |
| `1k` | `0-9` | cpp-inplace | 0.135301 | 30000 | 0.0000045100 |
| `1k` | `0-9` | cpp-std-sort | 0.156678 | 30000 | 0.0000052226 |
| `1k` | `0.25pct` | franko-inplace | 0.154244 | 30000 | 0.0000051415 |
| `1k` | `0.25pct` | cpp-inplace | 0.152981 | 30000 | 0.0000050994 |
| `1k` | `0.25pct` | cpp-std-sort | 0.176570 | 30000 | 0.0000058857 |
| `1k` | `0.5pct` | franko-inplace | 0.165891 | 30000 | 0.0000055297 |
| `1k` | `0.5pct` | cpp-inplace | 0.165863 | 30000 | 0.0000055288 |
| `1k` | `0.5pct` | cpp-std-sort | 0.180669 | 30000 | 0.0000060223 |
| `1k` | `1pct` | franko-inplace | 0.169788 | 30000 | 0.0000056596 |
| `1k` | `1pct` | cpp-inplace | 0.173514 | 30000 | 0.0000057838 |
| `1k` | `1pct` | cpp-std-sort | 0.192958 | 30000 | 0.0000064319 |
| `1k` | `10pct` | franko-inplace | 0.205543 | 30000 | 0.0000068514 |
| `1k` | `10pct` | cpp-inplace | 0.215893 | 30000 | 0.0000071964 |
| `1k` | `10pct` | cpp-std-sort | 0.222083 | 30000 | 0.0000074028 |
| `1k` | `random` | franko-inplace | 0.172513 | 30000 | 0.0000057504 |
| `1k` | `random` | cpp-inplace | 0.176934 | 30000 | 0.0000058978 |
| `1k` | `random` | cpp-std-sort | 0.189152 | 30000 | 0.0000063051 |

## Appendix B: checksums

| size | dataset | checksum |
| --- | --- | ---: |
| `10M` | `0-9` | 224950145 |
| `10M` | `0.25pct` | 625065563395 |
| `10M` | `0.5pct` | 1249897438395 |
| `10M` | `1pct` | 2500097938395 |
| `10M` | `10pct` | 24996345938395 |
| `10M` | `random` | 11288122046228302619 |
| `1M` | `0-9` | 44965070 |
| `1M` | `0.25pct` | 12496337770 |
| `1M` | `0.5pct` | 24981762770 |
| `1M` | `1pct` | 49961662770 |
| `1M` | `10pct` | 500083062770 |
| `1M` | `random` | 1093935092287169842 |
| `99k` | `0-9` | 134060100 |
| `99k` | `0.25pct` | 3659376000 |
| `99k` | `0.5pct` | 7345596600 |
| `99k` | `1pct` | 14681051100 |
| `99k` | `10pct` | 147147506100 |
| `99k` | `random` | 1230699081746888020 |
| `10k` | `0-9` | 136722000 |
| `10k` | `0.25pct` | 362742000 |
| `10k` | `0.5pct` | 740442000 |
| `10k` | `1pct` | 1479192000 |
| `10k` | `10pct` | 14869692000 |
| `10k` | `random` | 8975576923452795424 |
| `1k` | `0-9` | 137670000 |
| `1k` | `0.25pct` | 16170000 |
| `1k` | `0.5pct` | 60420000 |
| `1k` | `1pct` | 137670000 |
| `1k` | `10pct` | 1482870000 |
| `1k` | `random` | 18047602599753037424 |

These are totals over the chunk repetitions. All three modes agree in
every cell.

## Appendix C: files

| File | Role |
| --- | --- |
| `benchmark/quicksort_benchmark.py` | builds and times the 5-size × 6-dataset matrix |
| `benchmark/quicksort_inplace_franko.fr` | Franko `-Omax`, in-place value sort |
| `benchmark/quicksort_inplace_cpp.cpp` | C++ `-O3`, same in-place algorithm |
| `benchmark/quicksort_std_cpp.cpp` | C++ `-O3`, `std::sort` reference |
| `benchmark/quicksort.md` | This report |
