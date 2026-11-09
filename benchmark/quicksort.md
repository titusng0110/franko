# In-place quicksort benchmark: Franko vs C++ vs std::sort

**Plain `int64` keys · 5 sizes × 6 distributions · all chunk timing · 10M: 5, 1M: 10, 99k: 300, 10k: 3,000, 1k: 30,000 · 2026-11-10**

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

- `0-9` wins every size: Franko is **0.65x–0.84x** and C++ is
  **0.67x–0.90x** of `std::sort`.
- The sample-median first pivot is a clear win. **Every 1k cell now
  beats `std::sort`**: Franko is **0.83x–0.96x**, C++ **0.89x–0.97x**.
- `10k` is strong for both, especially Franko: **0.65x–0.89x** on the
  duplicate-heavy rows, with C++ at **0.74x–0.96x**.
- `99k` is mostly wins. Franko wins five of six (`0.76x–0.99x`);
  C++ wins five of six (`0.75x–0.98x`).
- `1M` is mixed: `0-9` and `10pct` win for Franko; `0-9` and `1pct`
  win for C++; `0.5pct` and `random` lose for both.
- `10M` remains the hard row. Only `0-9` wins; `10pct` is parity for
  Franko, and every other non-`0-9` row is a loss for both.
- The first pivot now comes from the preflight sample, so the first
  partition is nearly free; later partitions use the original single
  random pivot. This removes most of the median-of-3 overhead from the
  previous version.
- Franko and C++ are mostly within 5% at 10M/1M/99k. The remaining
  gaps are code layout/codegen effects at 10k (up to ~14%) and 1k
  (~6–8%).
- All 30 cells agree across the three modes.

---

## 1. Bottom line

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | 0.132451 | **0.128837** | 0.191412 |
| `10M` | `0.25pct` | 0.479979 | 0.473205 | **0.461141** |
| `10M` | `0.5pct` | 0.508383 | 0.502103 | **0.464202** |
| `10M` | `1pct` | 0.556396 | 0.531932 | **0.506325** |
| `10M` | `10pct` | 0.597375 | 0.610675 | **0.597114** |
| `10M` | `random` | 0.629714 | 0.613123 | **0.601216** |
| `1M` | `0-9` | 0.011820 | **0.011812** | 0.017527 |
| `1M` | `0.25pct` | 0.036599 | 0.035832 | **0.035700** |
| `1M` | `0.5pct` | 0.039433 | 0.040141 | **0.037928** |
| `1M` | `1pct` | 0.046033 | **0.043543** | 0.044496 |
| `1M` | `10pct` | **0.050430** | 0.052785 | 0.051852 |
| `1M` | `random` | 0.052600 | 0.052110 | **0.050894** |
| `99k` | `0-9` | 0.001119 | **0.001104** | 0.001466 |
| `99k` | `0.25pct` | 0.002517 | **0.002430** | 0.002606 |
| `99k` | `0.5pct` | 0.003027 | **0.002913** | 0.002965 |
| `99k` | `1pct` | 0.003307 | **0.003263** | 0.003331 |
| `99k` | `10pct` | **0.004010** | 0.004263 | 0.004060 |
| `99k` | `random` | 0.004422 | **0.004365** | 0.004474 |
| `10k` | `0-9` | **0.000086** | 0.000097 | 0.000131 |
| `10k` | `0.25pct` | **0.000141** | 0.000160 | 0.000179 |
| `10k` | `0.5pct` | 0.000183 | **0.000181** | 0.000209 |
| `10k` | `1pct` | **0.000217** | 0.000234 | 0.000243 |
| `10k` | `10pct` | **0.000321** | 0.000322 | 0.000322 |
| `10k` | `random` | 0.000356 | **0.000321** | 0.000341 |
| `1k` | `0-9` | **0.0000046257** | 0.0000049421 | 0.0000055147 |
| `1k` | `0.25pct` | **0.0000052125** | 0.0000055763 | 0.0000058954 |
| `1k` | `0.5pct` | **0.0000054790** | 0.0000059081 | 0.0000066093 |
| `1k` | `1pct` | **0.0000057499** | 0.0000058882 | 0.0000062538 |
| `1k` | `10pct` | **0.0000070993** | 0.0000071666 | 0.0000074139 |
| `1k` | `random` | **0.0000058918** | 0.0000059094 | 0.0000062193 |

All per-sort means are chunk wall time divided by the repetition count.

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | ---: | ---: |
| `10M` | `0-9` | 0.692x | 0.673x |
| `10M` | `0.25pct` | 1.041x | 1.026x |
| `10M` | `0.5pct` | 1.095x | 1.082x |
| `10M` | `1pct` | 1.099x | 1.051x |
| `10M` | `10pct` | 1.000x | 1.023x |
| `10M` | `random` | 1.047x | 1.020x |
| `1M` | `0-9` | 0.674x | 0.674x |
| `1M` | `0.25pct` | 1.025x | 1.004x |
| `1M` | `0.5pct` | 1.040x | 1.058x |
| `1M` | `1pct` | 1.035x | 0.979x |
| `1M` | `10pct` | 0.973x | 1.018x |
| `1M` | `random` | 1.034x | 1.024x |
| `99k` | `0-9` | 0.763x | 0.753x |
| `99k` | `0.25pct` | 0.966x | 0.932x |
| `99k` | `0.5pct` | 1.021x | 0.982x |
| `99k` | `1pct` | 0.993x | 0.980x |
| `99k` | `10pct` | 0.988x | 1.050x |
| `99k` | `random` | 0.988x | 0.976x |
| `10k` | `0-9` | 0.651x | 0.736x |
| `10k` | `0.25pct` | 0.786x | 0.896x |
| `10k` | `0.5pct` | 0.876x | 0.865x |
| `10k` | `1pct` | 0.892x | 0.963x |
| `10k` | `10pct` | 0.997x | 1.002x |
| `10k` | `random` | 1.044x | 0.941x |
| `1k` | `0-9` | 0.839x | 0.896x |
| `1k` | `0.25pct` | 0.884x | 0.946x |
| `1k` | `0.5pct` | 0.829x | 0.894x |
| `1k` | `1pct` | 0.919x | 0.942x |
| `1k` | `10pct` | 0.958x | 0.967x |
| `1k` | `random` | 0.947x | 0.950x |

A value below `1.00x` means the implementation is faster than
`std::sort`.

Key points:

- **`0-9` is a clean win at every size.** Both implementations are
  0.65–0.90x of `std::sort`.
- **1k is now a full sweep.** All six datasets beat `std::sort` for
  both implementations; no 1k cell is a loss.
- **10k favors the custom sorts.** Franko is 0.65–0.89x on the
  duplicate-heavy rows, and C++ is 0.74–0.96x there.
- **99k is mostly positive.** Both win five of six; the exceptions are
  Franko `0.5pct` (1.02x) and C++ `10pct` (1.05x).
- **1M is mixed.** Franko wins `0-9` and `10pct`; C++ wins `0-9` and
  `1pct`; `0.5pct` and `random` lose for both.
- **10M remains hard.** Only `0-9` wins; every other row is a loss for
  C++, and Franko is at parity on `10pct` but loses the rest.
- **The sample median removed the previous regression.** The first
  pivot is reused from the preflight sample, so the first partition
  does not pay an extra pivot search; subsequent partitions use the
  original cheap random pivot.

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

- **`N >= 100000`:** the `m_hat` estimator. It draws
  `k = clamp(N / 1000, 100, 65535)` stratified samples, heap-sorts their
  indices by key, and counts equal-key sample pairs. If `m_hat > 70`,
  the sort uses DNF; otherwise Lomuto.
- **`40000 <= N < 100000`:** a fixed 60-element randomized static
  probe. Any duplicate selects DNF, otherwise Lomuto.
- **`20000 <= N < 40000`:** the same rule with 38 samples.
- **`10000 <= N < 20000`:** the same rule with 27 samples.
- **`5000 <= N < 10000`:** the same rule with 19 samples.
- **`N < 5000`:** Hoare partitioning with the plain median-of-3 pivot
  on every partition.

For the estimator, let `pairs = k(k-1)/2` and let `collisions` be the
number of equal-key sample pairs. DNF is selected when
`collisions * N > 70 * pairs`, which is equivalent to an estimated
mean group size `m_hat = N * collisions / pairs > 70`.

The static probes take one random offset from each equal segment. Each
sample is insertion-sorted by value; a single adjacent duplicate
selects DNF. The estimator heap-sorts its sampled indices by key.

For `N >= 5000`, the preflight sample also supplies the first pivot:
the median of the sample. DNF uses that value directly, and Lomuto
swaps the median sample position to `hi`. After the first partition,
every recursive partition uses the original single random pivot from
`RANDOM_PIVOTS`. For `N < 5000`, the Hoare path uses the plain
median-of-3 pivot (`lo`, `mid`, `hi`) on every partition and does not
use a sample-derived first pivot.

Every read from `RANDOM_PIVOTS` advances `pivot_cursor`, whether it is
a sample-selection read or a later pivot-selection read. The cursor
resets at the start of each sort, so the sample consumes the first
words and the subsequent partitions continue from there.

The DNF choice for this benchmark is:

| size | DNF datasets | Lomuto datasets |
| --- | --- | --- |
| `10M` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `1M` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `99k` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `10k` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `1k` | none | none — all use Hoare median-of-3 |

The five benchmark sizes exercise four of the tiers: the `m_hat`
estimator at 10M/1M, the 60-sample probe at 99k, the 27-sample probe at
10k, and the Hoare path at 1k. The 38- and 19-sample tiers cover
20k–40k and 5k–10k respectively and are not represented by these five
sizes.

The rest of the machinery is shared:

- **Fixed cutoff:** recursion stops at 32 elements. Sizes 2–8 use
  sorting networks; sizes 9–32 use insertion sort.
- **Iterative control flow:** the larger partition stays in a while
  loop; only the smaller side recurses.
- **Comparison discipline:** all element comparisons use only `<`.

### 2.4 Timing methodology

All rows are timed as one chunk per mode and dataset:

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
| `10M` | `0-9` | 13.25 | **12.88** | 19.14 |
| `10M` | `0.25pct` | 48.00 | 47.32 | **46.11** |
| `10M` | `0.5pct` | 50.84 | 50.21 | **46.42** |
| `10M` | `1pct` | 55.64 | 53.19 | **50.63** |
| `10M` | `10pct` | 59.74 | 61.07 | **59.71** |
| `10M` | `random` | 62.97 | 61.31 | **60.12** |
| `1M` | `0-9` | 11.82 | **11.81** | 17.53 |
| `1M` | `0.25pct` | 36.60 | 35.83 | **35.70** |
| `1M` | `0.5pct` | 39.43 | 40.14 | **37.93** |
| `1M` | `1pct` | 46.03 | **43.54** | 44.50 |
| `1M` | `10pct` | **50.43** | 52.79 | 51.85 |
| `1M` | `random` | 52.60 | 52.11 | **50.89** |
| `99k` | `0-9` | 11.30 | **11.15** | 14.81 |
| `99k` | `0.25pct` | 25.43 | **24.55** | 26.33 |
| `99k` | `0.5pct` | 30.58 | **29.42** | 29.95 |
| `99k` | `1pct` | 33.40 | **32.96** | 33.64 |
| `99k` | `10pct` | **40.50** | 43.06 | 41.01 |
| `99k` | `random` | 44.66 | **44.09** | 45.19 |
| `10k` | `0-9` | **8.56** | 9.68 | 13.15 |
| `10k` | `0.25pct` | **14.05** | 16.02 | 17.88 |
| `10k` | `0.5pct` | 18.33 | **18.09** | 20.92 |
| `10k` | `1pct` | **21.71** | 23.42 | 24.33 |
| `10k` | `10pct` | **32.05** | 32.23 | 32.15 |
| `10k` | `random` | 35.62 | **32.09** | 34.11 |
| `1k` | `0-9` | **4.63** | 4.94 | 5.51 |
| `1k` | `0.25pct` | **5.21** | 5.58 | 5.90 |
| `1k` | `0.5pct` | **5.48** | 5.91 | 6.61 |
| `1k` | `1pct` | **5.75** | 5.89 | 6.25 |
| `1k` | `10pct` | **7.10** | 7.17 | 7.41 |
| `1k` | `random` | **5.89** | 5.91 | 6.22 |

The 10M/1M per-key costs are higher than the 99k/10k rows because the
80 MB and 8 MB arrays do not fit in the same cache levels; the 1k row
fits in L1/L2 and is dominated by per-sort overhead.

### 3.2 Chunk timings

Full chunk wall times and per-sort means are in Appendix A.

---

## 4. Analysis

### 4.1 Sample-median first pivot

The first pivot is taken from the sample that the preflight already
had to read. That makes it effectively free: no extra pivot search, no
extra table reads, and no extra compare-swaps. The sample median is a
much better pivot than a single random draw, so the first partition is
well balanced. Subsequent partitions revert to the cheap single random
pivot, which keeps the per-partition overhead low.

The dataset pattern:

- `0-9` is DNF at 10M–10k and Hoare at 1k. It wins every size.
- `0.25pct` is DNF at 10M–10k and Hoare at 1k. Both win 99k–1k; 10M is
  a loss for both, and 1M is a small Franko loss with C++ near parity.
- `0.5pct` is DNF at 10M–10k and Hoare at 1k. Both win 10k/1k; C++
  also wins 99k, while Franko loses 99k narrowly; 10M/1M are losses.
- `1pct` is DNF at 10M–10k and Hoare at 1k. C++ wins 1M–1k; Franko
  wins 99k–1k; 10M is a loss for both.
- `10pct` is Lomuto at 10M–10k and Hoare at 1k. Franko wins or ties
  every size; C++ wins only 1k.
- `random` is Lomuto at 10M–10k and Hoare at 1k. C++ wins 10k/1k,
  Franko wins 99k/1k, and 10M/1M are losses for both.

### 4.2 All-chunk timing and run-to-run variation

The 10M and 1M rows are one process per cell, with 5 or 10 sorts
inside it and one data-vector allocation. That removes 4/9 process
starts and repeated 80 MB/8 MB allocations per cell. The comparison
between modes inside this report is still valid, but the absolute
values should not be compared directly to reports that timed each sort
in its own process.

The single-chunk design also means the absolute numbers move between
runs with CPU state. The 1k row is especially sensitive: its per-sort
times are 4–7 µs and the `std::sort` reference moves by several percent
between runs, which shifts the ratios.

### 4.3 Cache effects

The high-cardinality rows show the expected per-key scaling:

| dataset | 10M | 1M | 99k | 10k | 1k |
| --- | ---: | ---: | ---: | ---: | ---: |
| `1pct` (Franko) | 55.6 ns | 46.0 ns | 33.4 ns | 21.7 ns | 5.8 ns |
| `10pct` (Franko) | 59.7 ns | 50.4 ns | 40.5 ns | 32.1 ns | 7.1 ns |
| `random` (Franko) | 63.0 ns | 52.6 ns | 44.7 ns | 35.6 ns | 5.9 ns |

At 10M the 80 MB array exceeds L3; at 1M the 8 MB array fits in L3;
at 99k the 0.8 MB array fits in L2; at 10k and 1k the arrays fit in
L1/L2. The 1k row is also where the Hoare path runs.

### 4.4 Franko versus hand-written C++

Franko and the C++ port are mostly within 5% at 10M/1M/99k. The
remaining gaps are codegen/layout-sensitive and show up at the small
sizes:

- `10k` `0-9`: Franko 0.000086 s vs C++ 0.000097 s (Franko 11% faster);
- `10k` `0.25pct`: Franko 0.000141 s vs C++ 0.000160 s (Franko 12% faster);
- `10k` `1pct`: Franko 0.000217 s vs C++ 0.000234 s (Franko 7.3% faster);
- `10k` `random`: Franko 0.000356 s vs C++ 0.000321 s (C++ 9.8% faster);
- `1k` `0-9`: Franko 0.0000046 s vs C++ 0.0000049 s (Franko 6.3% faster);
- `1k` `0.5pct`: Franko 0.0000055 s vs C++ 0.0000059 s (Franko 7.3% faster);
- `1M` `1pct`: Franko 0.046033 s vs C++ 0.043543 s (C++ 5.4% faster);
- `10M` `1pct`: Franko 0.556396 s vs C++ 0.531932 s (C++ 4.4% faster).

The 10k and 1k differences are dominated by code layout/inlining
choices rather than the pivot rule; the 10M/1M/99k rows are mostly
within 5%.

### 4.5 The algorithm versus `std::sort`

- The custom sorts win every `0-9` cell.
- Both win all six 1k cells.
- At 10k, Franko wins five of six (loses `random`), and C++ wins five
  of six (loses `10pct`).
- At 99k, both win five of six: Franko loses `0.5pct`, C++ loses
  `10pct`.
- At 1M, Franko wins `0-9` and `10pct`; C++ wins `0-9` and `1pct`.
- At 10M, only `0-9` wins; `10pct` is parity for Franko, and the other
  non-`0-9` rows lose for both.

### 4.6 Noise

Every cell is a single chunk measurement, so there is no run-to-run
standard deviation. The chunk means are stable enough for the large
rows, but the 1k row is only 4–7 µs per sort, and its per-sort mean is
printed to 1 µs resolution. The 1k slowdown ratios should be read as
approximate. The other rows are 10–300 ms per chunk and are not
resolution-limited.

### 4.7 Time and space complexity

Let `N` be the element count, `d` the number of distinct keys, and
`k = clamp(floor(N / 1000), 100, 65535)` the estimator sample size.

Time:

| stage | cost |
| --- | --- |
| estimator preflight (`N >= 100000`) | `O(k)` sampling + `O(k log k)` index heap sort |
| static probe (`5000 <= N < 100000`) | `O(s^2)` insertion sort with `s <= 60`, so `O(1)` |
| first pivot | taken from the preflight sample, `O(1)` extra |
| later pivot selection | `O(1)` per partition |
| partitioning | `O(N log N)` expected and worst case |
| small sorts (`<= 32` elements) | `O(1)` per leaf, `O(N)` total |
| data generation / sortedness check / checksum (harness) | `O(N)` |

The partition stage is an introsort-style quicksort: the depth limit is
`2 * floor_log2(N)`, and any subproblem that exhausts it is heap-sorted.
Every level costs `O(N)` across the live subproblems, there are at most
`O(log N)` levels, and the heapsorted subproblems are disjoint, so the
partition stage is `O(N log N)` in the worst case. The same bound holds
for DNF, Lomuto, and Hoare. With the sample-median first pivot and
random pivots afterwards, the expected cost is also `O(N log N)`.

Duplicate structure only helps the partition stage. DNF removes the
whole equal block at each partition. For the uniform-frequency datasets
used here, the partition tree over `d` distinct values has expected
depth `O(log d)`, so the partition stage costs `O(N log d)`; when all
keys are equal it is `O(N)`. The estimator preflight is still paid:
`O(k log k)`, which is `O(N log N)` with a `1/1000` constant in the
uncapped range and `O(1)` once `k` hits the 65535 cap. Overall the sort
is `O(N log N)` expected and worst case.

Space:

| N range | preflight storage | recursion stack | auxiliary space |
| --- | --- | --- | --- |
| `N >= 100000` | `k` u32 indices = `4k` bytes, max 262,140 B | `O(log N)` | `O(k + log N)` |
| `40000 <= N < 100000` | 60 u32 = 240 B | `O(log N)` | `O(log N)` |
| `20000 <= N < 40000` | 38 u32 = 152 B | `O(log N)` | `O(log N)` |
| `10000 <= N < 20000` | 27 u32 = 108 B | `O(log N)` | `O(log N)` |
| `5000 <= N < 10000` | 19 u32 = 76 B | `O(log N)` | `O(log N)` |
| `N < 5000` | none | `O(log N)` | `O(log N)` |

The input array is `8N` bytes and is sorted in place, so total space is
`O(N)`. Auxiliary space is `O(k + log N)` for `N >= 100000` and
`O(log N)` below that; the sample and the recursion stack do not
overlap, so the peak is their maximum. For
`100000 <= N <= 65,535,999`, `k = floor(N / 1000)`, so the estimator
uses about `0.001N` u32 elements (`0.004N` bytes): 40 KB at 10M and
4 KB at 1M. At `N >= 65,536,000` the sample is capped at 65,535
entries, i.e. 262,140 bytes (about 256 KiB), so it stops growing with
`N`. The static probes are 76–240 bytes and the recursion stack is
`O(log N)` frames because the algorithm recurses only into the smaller
side, so every tier below 100k uses constant-plus-logarithmic auxiliary
space.

---

## 5. Limitations

1. **No independent per-sort samples.** All cells are one chunk. There
   is no SD or CV table; differences of a few percent are not
   statistically resolved.
2. **10M/1M chunk reuse.** One allocation is reused across 5/10 sorts.
   Absolute 10M/1M times are lower than separate-process measurements.
3. **1k timer resolution.** The 1k per-sort means are 4–7 µs and are
   printed to 1 µs; those ratios are coarse.
4. **Synthetic uniform frequencies.** The percentage datasets are
   uniform over `N/400`, `N/200`, `N/100`, and `N/10` values. Skewed
   real data changes the estimator and probe behavior.
5. **Fixed probes are heuristics.** The 60-, 38-, 27-, and 19-element
   samples take one random offset per equal segment; they can miss
   duplicates. The 38- and 19-sample tiers are not exercised by the
   five benchmark sizes.
6. **Every path is depth-limited.** DNF, Lomuto, and Hoare all fall
   back to heapsort after `2 * floor_log2(N)` levels.
7. **`std::sort` is introsort.** It is a strong reference, not an exact
   algorithm match.
8. **One machine.** WSL2 on a 12th-gen i5-12600K with CPU pinning.
   Absolute times are host-sensitive.
9. **Only maximum release builds.** Franko `-Omax` and C++ `-O3`;
   checked/sanitizer builds are not included.
10. **Binary size includes static runtimes.** Franko is 956 KB;
    C++ in-place is 162 KB and `std::sort` 154 KB.
11. **Tuned constants.** The estimator threshold, sample-size clamp,
    probe sizes, 32-element cutoff, and `2 * log2(N)` depth factor are
    tuned to this workload.
12. **Remaining Franko/C++ gaps are codegen/layout-sensitive.** They
    are mostly below 5% at 10M/1M/99k but reach about 14% at 10k and
    about 7% at 1k.

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
| `10M` | `0-9` | franko-inplace | 0.662255 | 5 | 0.132451 |
| `10M` | `0-9` | cpp-inplace | 0.644187 | 5 | 0.128837 |
| `10M` | `0-9` | cpp-std-sort | 0.957060 | 5 | 0.191412 |
| `10M` | `0.25pct` | franko-inplace | 2.399893 | 5 | 0.479979 |
| `10M` | `0.25pct` | cpp-inplace | 2.366023 | 5 | 0.473205 |
| `10M` | `0.25pct` | cpp-std-sort | 2.305706 | 5 | 0.461141 |
| `10M` | `0.5pct` | franko-inplace | 2.541916 | 5 | 0.508383 |
| `10M` | `0.5pct` | cpp-inplace | 2.510514 | 5 | 0.502103 |
| `10M` | `0.5pct` | cpp-std-sort | 2.321009 | 5 | 0.464202 |
| `10M` | `1pct` | franko-inplace | 2.781979 | 5 | 0.556396 |
| `10M` | `1pct` | cpp-inplace | 2.659662 | 5 | 0.531932 |
| `10M` | `1pct` | cpp-std-sort | 2.531626 | 5 | 0.506325 |
| `10M` | `10pct` | franko-inplace | 2.986873 | 5 | 0.597375 |
| `10M` | `10pct` | cpp-inplace | 3.053374 | 5 | 0.610675 |
| `10M` | `10pct` | cpp-std-sort | 2.985569 | 5 | 0.597114 |
| `10M` | `random` | franko-inplace | 3.148569 | 5 | 0.629714 |
| `10M` | `random` | cpp-inplace | 3.065615 | 5 | 0.613123 |
| `10M` | `random` | cpp-std-sort | 3.006082 | 5 | 0.601216 |
| `1M` | `0-9` | franko-inplace | 0.118203 | 10 | 0.011820 |
| `1M` | `0-9` | cpp-inplace | 0.118118 | 10 | 0.011812 |
| `1M` | `0-9` | cpp-std-sort | 0.175269 | 10 | 0.017527 |
| `1M` | `0.25pct` | franko-inplace | 0.365991 | 10 | 0.036599 |
| `1M` | `0.25pct` | cpp-inplace | 0.358316 | 10 | 0.035832 |
| `1M` | `0.25pct` | cpp-std-sort | 0.357000 | 10 | 0.035700 |
| `1M` | `0.5pct` | franko-inplace | 0.394330 | 10 | 0.039433 |
| `1M` | `0.5pct` | cpp-inplace | 0.401411 | 10 | 0.040141 |
| `1M` | `0.5pct` | cpp-std-sort | 0.379279 | 10 | 0.037928 |
| `1M` | `1pct` | franko-inplace | 0.460326 | 10 | 0.046033 |
| `1M` | `1pct` | cpp-inplace | 0.435430 | 10 | 0.043543 |
| `1M` | `1pct` | cpp-std-sort | 0.444964 | 10 | 0.044496 |
| `1M` | `10pct` | franko-inplace | 0.504304 | 10 | 0.050430 |
| `1M` | `10pct` | cpp-inplace | 0.527855 | 10 | 0.052786 |
| `1M` | `10pct` | cpp-std-sort | 0.518522 | 10 | 0.051852 |
| `1M` | `random` | franko-inplace | 0.525995 | 10 | 0.052600 |
| `1M` | `random` | cpp-inplace | 0.521104 | 10 | 0.052110 |
| `1M` | `random` | cpp-std-sort | 0.508935 | 10 | 0.050893 |
| `99k` | `0-9` | franko-inplace | 0.335552 | 300 | 0.001119 |
| `99k` | `0-9` | cpp-inplace | 0.331144 | 300 | 0.001104 |
| `99k` | `0-9` | cpp-std-sort | 0.439806 | 300 | 0.001466 |
| `99k` | `0.25pct` | franko-inplace | 0.755198 | 300 | 0.002517 |
| `99k` | `0.25pct` | cpp-inplace | 0.729065 | 300 | 0.002430 |
| `99k` | `0.25pct` | cpp-std-sort | 0.781923 | 300 | 0.002606 |
| `99k` | `0.5pct` | franko-inplace | 0.908227 | 300 | 0.003027 |
| `99k` | `0.5pct` | cpp-inplace | 0.873914 | 300 | 0.002913 |
| `99k` | `0.5pct` | cpp-std-sort | 0.889631 | 300 | 0.002965 |
| `99k` | `1pct` | franko-inplace | 0.991963 | 300 | 0.003307 |
| `99k` | `1pct` | cpp-inplace | 0.978801 | 300 | 0.003263 |
| `99k` | `1pct` | cpp-std-sort | 0.999246 | 300 | 0.003331 |
| `99k` | `10pct` | franko-inplace | 1.202898 | 300 | 0.004010 |
| `99k` | `10pct` | cpp-inplace | 1.278934 | 300 | 0.004263 |
| `99k` | `10pct` | cpp-std-sort | 1.217982 | 300 | 0.004060 |
| `99k` | `random` | franko-inplace | 1.326526 | 300 | 0.004422 |
| `99k` | `random` | cpp-inplace | 1.309381 | 300 | 0.004365 |
| `99k` | `random` | cpp-std-sort | 1.342158 | 300 | 0.004474 |
| `10k` | `0-9` | franko-inplace | 0.256880 | 3000 | 0.000086 |
| `10k` | `0-9` | cpp-inplace | 0.290391 | 3000 | 0.000097 |
| `10k` | `0-9` | cpp-std-sort | 0.394467 | 3000 | 0.000131 |
| `10k` | `0.25pct` | franko-inplace | 0.421576 | 3000 | 0.000141 |
| `10k` | `0.25pct` | cpp-inplace | 0.480694 | 3000 | 0.000160 |
| `10k` | `0.25pct` | cpp-std-sort | 0.536392 | 3000 | 0.000179 |
| `10k` | `0.5pct` | franko-inplace | 0.550031 | 3000 | 0.000183 |
| `10k` | `0.5pct` | cpp-inplace | 0.542800 | 3000 | 0.000181 |
| `10k` | `0.5pct` | cpp-std-sort | 0.627621 | 3000 | 0.000209 |
| `10k` | `1pct` | franko-inplace | 0.651327 | 3000 | 0.000217 |
| `10k` | `1pct` | cpp-inplace | 0.702638 | 3000 | 0.000234 |
| `10k` | `1pct` | cpp-std-sort | 0.729958 | 3000 | 0.000243 |
| `10k` | `10pct` | franko-inplace | 0.961650 | 3000 | 0.000321 |
| `10k` | `10pct` | cpp-inplace | 0.966849 | 3000 | 0.000322 |
| `10k` | `10pct` | cpp-std-sort | 0.964591 | 3000 | 0.000322 |
| `10k` | `random` | franko-inplace | 1.068638 | 3000 | 0.000356 |
| `10k` | `random` | cpp-inplace | 0.962741 | 3000 | 0.000321 |
| `10k` | `random` | cpp-std-sort | 1.023304 | 3000 | 0.000341 |
| `1k` | `0-9` | franko-inplace | 0.138772 | 30000 | 0.0000050000 |
| `1k` | `0-9` | cpp-inplace | 0.148262 | 30000 | 0.0000050000 |
| `1k` | `0-9` | cpp-std-sort | 0.165442 | 30000 | 0.0000060000 |
| `1k` | `0.25pct` | franko-inplace | 0.156375 | 30000 | 0.0000050000 |
| `1k` | `0.25pct` | cpp-inplace | 0.167290 | 30000 | 0.0000060000 |
| `1k` | `0.25pct` | cpp-std-sort | 0.176862 | 30000 | 0.0000060000 |
| `1k` | `0.5pct` | franko-inplace | 0.164369 | 30000 | 0.0000050000 |
| `1k` | `0.5pct` | cpp-inplace | 0.177242 | 30000 | 0.0000060000 |
| `1k` | `0.5pct` | cpp-std-sort | 0.198279 | 30000 | 0.0000070000 |
| `1k` | `1pct` | franko-inplace | 0.172497 | 30000 | 0.0000060000 |
| `1k` | `1pct` | cpp-inplace | 0.176646 | 30000 | 0.0000060000 |
| `1k` | `1pct` | cpp-std-sort | 0.187615 | 30000 | 0.0000060000 |
| `1k` | `10pct` | franko-inplace | 0.212978 | 30000 | 0.0000070000 |
| `1k` | `10pct` | cpp-inplace | 0.214998 | 30000 | 0.0000070000 |
| `1k` | `10pct` | cpp-std-sort | 0.222416 | 30000 | 0.0000070000 |
| `1k` | `random` | franko-inplace | 0.176754 | 30000 | 0.0000060000 |
| `1k` | `random` | cpp-inplace | 0.177281 | 30000 | 0.0000060000 |
| `1k` | `random` | cpp-std-sort | 0.186580 | 30000 | 0.0000060000 |

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
