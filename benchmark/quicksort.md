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

- `0-9` wins every size: Franko is **0.62x–0.82x** and C++ is
  **0.61x–0.87x** of `std::sort`.
- `0.25pct` is 1.02x slower at 10M, then wins every smaller size:
  **0.96x** at 1M, **0.90x** at 99k, **0.76x–0.83x** at 10k, and
  **0.89x–0.92x** at 1k.
- `0.5pct` is 1.05x–1.08x slower at 10M, then wins: **0.96x–0.97x** at
  1M, **0.90x–0.91x** at 99k, **0.83x–0.94x** at 10k, and
  **0.85x–0.86x** at 1k.
- `1pct` is 1.07x–1.08x slower at 10M, then wins at 1M/99k/10k
  (**0.90x–0.98x**) and at 1k (**0.89x–0.90x**).
- `10pct` and `random` are near parity. The custom sorts are 4–6%
  slower at 10M, within ±6% elsewhere, and `random` at 1k is the one
  small-size loss (**1.03x–1.04x**).
- The larger probes now find duplicates at 99k and 10k, so `0.5pct`
  and `1pct` switch to DNF and beat `std::sort` there instead of
  losing on Lomuto.
- Franko vs hand-written C++ stays within about 5% on most cells; the
  smallest duplicate-heavy rows show gaps up to 19%.

---

## 1. Bottom line

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | 0.121658 | **0.119057** | 0.196514 |
| `10M` | `0.25pct` | 0.489818 | 0.489713 | **0.478536** |
| `10M` | `0.5pct` | 0.523238 | 0.509588 | **0.483692** |
| `10M` | `1pct` | 0.555392 | 0.550987 | **0.514083** |
| `10M` | `10pct` | 0.615637 | 0.621422 | **0.591825** |
| `10M` | `random` | 0.632323 | 0.626084 | **0.599399** |
| `1M` | `0-9` | **0.011466** | 0.012192 | 0.018273 |
| `1M` | `0.25pct` | **0.036994** | 0.037731 | 0.038648 |
| `1M` | `0.5pct` | 0.040405 | **0.040302** | 0.041793 |
| `1M` | `1pct` | **0.043388** | 0.043672 | 0.044361 |
| `1M` | `10pct` | 0.054087 | 0.053249 | **0.052178** |
| `1M` | `random` | 0.056527 | **0.054214** | 0.054651 |
| `99k` | `0-9` | **0.001064** | 0.001093 | 0.001527 |
| `99k` | `0.25pct` | **0.002375** | 0.002395 | 0.002654 |
| `99k` | `0.5pct` | 0.002790 | **0.002758** | 0.003056 |
| `99k` | `1pct` | 0.003183 | **0.003171** | 0.003322 |
| `99k` | `10pct` | 0.004162 | 0.004180 | **0.004147** |
| `99k` | `random` | 0.004172 | **0.004121** | 0.004178 |
| `10k` | `0-9` | **0.000080** | 0.000099 | 0.000126 |
| `10k` | `0.25pct` | **0.000133** | 0.000145 | 0.000175 |
| `10k` | `0.5pct` | **0.000169** | 0.000190 | 0.000203 |
| `10k` | `1pct` | **0.000205** | 0.000221 | 0.000227 |
| `10k` | `10pct` | 0.000323 | **0.000321** | 0.000325 |
| `10k` | `random` | 0.000332 | **0.000316** | 0.000336 |
| `1k` | `0-9` | **0.0000043478** | 0.0000046220 | 0.0000053269 |
| `1k` | `0.25pct` | **0.0000050928** | 0.0000053022 | 0.0000057434 |
| `1k` | `0.5pct` | **0.0000051752** | 0.0000052330 | 0.0000060706 |
| `1k` | `1pct` | **0.0000056143** | 0.0000056501 | 0.0000063090 |
| `1k` | `10pct` | 0.0000072502 | **0.0000071072** | 0.0000072774 |
| `1k` | `random` | 0.0000062839 | 0.0000062233 | **0.0000060583** |

All per-sort means are chunk wall time divided by the repetition count.

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | ---: | ---: |
| `10M` | `0-9` | **0.619x** | **0.606x** |
| `10M` | `0.25pct` | 1.024x | 1.023x |
| `10M` | `0.5pct` | 1.082x | 1.054x |
| `10M` | `1pct` | 1.080x | 1.072x |
| `10M` | `10pct` | 1.040x | 1.050x |
| `10M` | `random` | 1.055x | 1.045x |
| `1M` | `0-9` | **0.627x** | **0.667x** |
| `1M` | `0.25pct` | **0.957x** | **0.976x** |
| `1M` | `0.5pct` | **0.967x** | **0.964x** |
| `1M` | `1pct` | **0.978x** | **0.984x** |
| `1M` | `10pct` | 1.037x | 1.021x |
| `1M` | `random` | 1.034x | **0.992x** |
| `99k` | `0-9` | **0.696x** | **0.716x** |
| `99k` | `0.25pct` | **0.895x** | **0.902x** |
| `99k` | `0.5pct` | **0.913x** | **0.902x** |
| `99k` | `1pct` | **0.958x** | **0.955x** |
| `99k` | `10pct` | 1.003x | 1.008x |
| `99k` | `random` | **0.998x** | **0.986x** |
| `10k` | `0-9` | **0.639x** | **0.788x** |
| `10k` | `0.25pct` | **0.758x** | **0.828x** |
| `10k` | `0.5pct` | **0.833x** | **0.936x** |
| `10k` | `1pct` | **0.901x** | **0.974x** |
| `10k` | `10pct` | **0.994x** | **0.986x** |
| `10k` | `random` | **0.989x** | **0.941x** |
| `1k` | `0-9` | **0.816x** | **0.868x** |
| `1k` | `0.25pct` | **0.887x** | **0.923x** |
| `1k` | `0.5pct` | **0.853x** | **0.862x** |
| `1k` | `1pct` | **0.890x** | **0.896x** |
| `1k` | `10pct` | **0.996x** | **0.977x** |
| `1k` | `random` | 1.037x | 1.027x |

A value below `1.00x` means the implementation is faster than
`std::sort`.

Key points:

- **Duplicate-heavy `0-9` is a clean win at every size.** The custom
  sorts are 1.27–1.65x faster at 10M–10k and about 1.15–1.23x faster at
  1k.
- **The larger probes fix the small-size duplicate rows.** With 60
  samples at 99k and 27 at 10k, `0.5pct` and `1pct` now find duplicates
  and switch to DNF instead of losing on Lomuto.
- **`1M` is the crossover row.** All four duplicate-heavy percentage
  datasets (`0.25pct` through `1pct`) now beat `std::sort`, while `10pct`
  and `random` stay within 4%.
- **`std::sort` remains strongest on 10M high-cardinality data.** The
  custom sorts are 2–8% slower there.
- **Randomized Hoare is not a universal 1k win.** It still beats
  `std::sort` on every duplicate-heavy 1k row, but `random` at 1k is
  now 1.03x–1.04x slower.
- **All timing is chunk-based.** The 10M/1M rows reuse one data
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

- **`N >= 100000`:** the `m_hat` estimator. It draws
  `k = clamp(N / 1000, 100, 65535)` stratified samples, heap-sorts their
  indices by key, and counts equal-key sample pairs. If `m_hat > 70`,
  the sort uses DNF; otherwise Lomuto.
- **`40000 <= N < 100000`:** a fixed 60-element randomized static
  probe. Any duplicate selects DNF, otherwise Lomuto.
- **`20000 <= N < 40000`:** the same rule with 38 samples.
- **`10000 <= N < 20000`:** the same rule with 27 samples.
- **`5000 <= N < 10000`:** the same rule with 19 samples.
- **`N < 5000`:** Hoare partitioning with a randomized pivot.

For the estimator, let `pairs = k(k-1)/2` and let `collisions` be the
number of equal-key sample pairs. DNF is selected when
`collisions * N > 70 * pairs`, which is equivalent to an estimated
mean group size `m_hat = N * collisions / pairs > 70`.

The static probes take one random offset from each equal segment, using
`RANDOM_PIVOTS` and a per-segment mix. Each probe is insertion-sorted
inline; a single adjacent duplicate selects DNF. `RANDOM_PIVOTS` is a
fixed 128-entry table, so "randomized" means pseudo-random offsets;
runs are deterministic.

DNF and Lomuto both use a randomized pivot drawn from
`RANDOM_PIVOTS`. The cursor resets at the start of each sort and
advances on every partition. Every path, including DNF and Hoare, runs
with a heapsort backup after `2 * floor_log2(N)` partition levels.

The DNF choice for this benchmark is:

| size | DNF datasets | Lomuto datasets |
| --- | --- | --- |
| `10M` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `1M` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `99k` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `10k` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `1k` | none | none — all use Hoare |

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
| `10M` | `0-9` | 12.17 | **11.91** | 19.65 |
| `10M` | `0.25pct` | 48.98 | 48.97 | **47.85** |
| `10M` | `0.5pct` | 52.32 | 50.96 | **48.37** |
| `10M` | `1pct` | 55.54 | 55.10 | **51.41** |
| `10M` | `10pct` | 61.56 | 62.14 | **59.18** |
| `10M` | `random` | 63.23 | 62.61 | **59.94** |
| `1M` | `0-9` | **11.47** | 12.19 | 18.27 |
| `1M` | `0.25pct` | **36.99** | 37.73 | 38.65 |
| `1M` | `0.5pct` | 40.41 | **40.30** | 41.79 |
| `1M` | `1pct` | **43.39** | 43.67 | 44.36 |
| `1M` | `10pct` | 54.09 | 53.25 | **52.18** |
| `1M` | `random` | 56.53 | **54.21** | 54.65 |
| `99k` | `0-9` | **10.74** | 11.04 | 15.43 |
| `99k` | `0.25pct` | **23.99** | 24.19 | 26.81 |
| `99k` | `0.5pct` | 28.18 | **27.86** | 30.87 |
| `99k` | `1pct` | 32.15 | **32.04** | 33.56 |
| `99k` | `10pct` | 42.04 | 42.22 | **41.89** |
| `99k` | `random` | 42.14 | **41.63** | 42.20 |
| `10k` | `0-9` | **8.02** | 9.89 | 12.55 |
| `10k` | `0.25pct` | **13.31** | 14.53 | 17.55 |
| `10k` | `0.5pct` | **16.90** | 19.00 | 20.30 |
| `10k` | `1pct` | **20.50** | 22.15 | 22.75 |
| `10k` | `10pct` | 32.32 | **32.08** | 32.53 |
| `10k` | `random` | 33.20 | **31.59** | 33.57 |
| `1k` | `0-9` | **4.35** | 4.62 | 5.33 |
| `1k` | `0.25pct` | **5.09** | 5.30 | 5.74 |
| `1k` | `0.5pct` | **5.18** | 5.23 | 6.07 |
| `1k` | `1pct` | **5.61** | 5.65 | 6.31 |
| `1k` | `10pct` | 7.25 | **7.11** | 7.28 |
| `1k` | `random` | 6.28 | 6.22 | **6.06** |

The 10M/1M per-key costs are higher than the 99k/10k rows because the
80 MB and 8 MB arrays do not fit in the same cache levels; the 1k row
fits in L1/L2 and is dominated by per-sort overhead.

### 3.2 Chunk timings

Full chunk wall times and per-sort means are in Appendix A.

---

## 4. Analysis

### 4.1 The larger probes move the DNF boundary

The probe sizes and the `m_hat > 70` threshold determine the
duplicate-heavy wins:

- `0-9` is DNF at every size from 10M to 10k and Hoare at 1k. It wins
  every size, by 1.27–1.65x at 10M–10k and 1.15–1.23x at 1k.
- `0.25pct` is DNF wherever a probe runs. At 10M it is 1.02x slower
  than `std::sort`; at 1M it wins at 0.96x, at 99k at 0.90x, at 10k at
  0.76x–0.83x, and at 1k the Hoare path wins at 0.89x–0.92x.
- `0.5pct` is DNF at every size from 10M to 10k. The 60-sample probe
  now finds duplicates at 99k, and the 27-sample probe finds them at
  10k. That turns the old 99k/10k losses into 0.83x–0.94x wins. Only
  10M remains a loss (1.05x–1.08x).
- `1pct` is DNF at every size from 10M to 10k as well. The larger
  probes find duplicates at 99k and 10k, where it now wins at
  0.90x–0.98x. At 10M it is 1.07x–1.08x slower.
- `10pct` and `random` are Lomuto at 10M–10k and Hoare at 1k. They are
  close to `std::sort`: 4–6% slower at 10M, within ±6% elsewhere, with
  the 1k `random` row at 1.03x–1.04x.

The main remaining 10M losses are the high-cardinality rows. At 10M
there are too many distinct keys for DNF to help, and the depth guard
keeps Lomuto on its introsort-like path.

### 4.2 All-chunk timing and run-to-run variation

The 10M and 1M rows are one process per cell, with 5 or 10 sorts
inside it and one data-vector allocation. That removes 4/9 process
starts and repeated 80 MB/8 MB allocations per cell. The 10M `0-9`
custom time is 0.122 s here, versus roughly 0.136 s in an earlier
separate-process matrix. The comparison between modes inside this
report is still valid, but the absolute values should not be compared
directly to reports that timed each sort in its own process.

The single-chunk design also means the absolute numbers move between
runs with CPU state. In particular, the 10M/1M `std::sort` times in
this run differ by a few percent from the previous matrix, which moves
the slowdown ratios even when the custom code is unchanged.

### 4.3 Cache effects

The high-cardinality rows show the expected per-key scaling:

| dataset | 10M | 1M | 99k | 10k | 1k |
| --- | ---: | ---: | ---: | ---: | ---: |
| `1pct` (Franko) | 55.5 ns | 43.4 ns | 32.2 ns | 20.5 ns | 5.6 ns |
| `10pct` (Franko) | 61.6 ns | 54.1 ns | 42.0 ns | 32.3 ns | 7.3 ns |
| `random` (Franko) | 63.2 ns | 56.5 ns | 42.1 ns | 33.2 ns | 6.3 ns |

At 10M the 80 MB array exceeds L3; at 1M the 8 MB array fits in L3;
at 99k the 0.8 MB array fits in L2; at 10k and 1k the arrays fit in
L1/L2. The 1k row is also where the randomized Hoare path starts.

### 4.4 Franko versus hand-written C++

Franko and the C++ port stay within about 5% on most cells. The
largest gaps are:

- `10k` `0-9`: Franko 0.000080 s vs C++ 0.000099 s (Franko 19% faster);
- `10k` `0.5pct`: Franko 0.000169 s vs C++ 0.000190 s (Franko 11% faster);
- `10k` `0.25pct`: Franko 0.000133 s vs C++ 0.000145 s (Franko 8.4%
  faster);
- `10k` `1pct`: Franko 0.000205 s vs C++ 0.000221 s (Franko 7.4% faster);
- `1M` `0-9`: Franko 0.011466 s vs C++ 0.012192 s (Franko 6.0% faster);
- `10k` `random`: Franko 0.000332 s vs C++ 0.000316 s (C++ 4.8% faster);
- `1M` `random`: Franko 0.056527 s vs C++ 0.054214 s (C++ 4.1% faster).

At 1k, the timer resolution is only 1 µs, so the printed per-sort
means are coarse; the exact chunk means keep both implementations
within about 6%.

### 4.5 The algorithm versus `std::sort`

- The custom sorts win every `0-9` cell, every `0.25pct`, `0.5pct`,
  and `1pct` cell at 1M/99k/10k/1k, and are near parity on `10pct`.
- `std::sort` wins every non-`0-9` 10M row (1.02x–1.08x) and the 1M
  `10pct` row (1.02x–1.04x); the other high-cardinality cells are close
  to parity.
- The only clear small-size loss is `random` at 1k, where randomized
  Hoare is 1.03x–1.04x slower than `std::sort`'s median-of-3 introsort.
  The 99k `10pct` rows are within 1%.

The remaining losses are small and concentrated at 10M, the 1M/99k
`10pct` rows, and `random` at 1k.

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
| partitioning | `O(N log N)` expected and worst case |
| small sorts (`<= 32` elements) | `O(1)` per leaf, `O(N)` total |
| data generation / sortedness check / checksum (harness) | `O(N)` |

The partition stage is an introsort-style quicksort: the depth limit is
`2 * floor_log2(N)`, and any subproblem that exhausts it is heap-sorted.
Every level costs `O(N)` across the live subproblems, there are at most
`O(log N)` levels, and the heapsorted subproblems are disjoint, so the
partition stage is `O(N log N)` in the worst case. The same bound holds
for DNF, Lomuto, and Hoare. With randomized pivots on random input, the
expected cost is also `O(N log N)`.

Duplicate structure only helps the partition stage. DNF removes the
whole equal block at each partition. For the uniform-frequency datasets
used here, the partition tree over `d` distinct values has expected
depth `O(log d)`, so the partition stage costs `O(N log d)`; when all
keys are equal it is `O(N)`. The estimator preflight is still paid:
`O(k log k)`, which is `O(N log N)` with a `1/1000` constant in the
uncapped range and `O(1)` once `k` hits the 65535 cap. Overall the sort
is `O(N log N)` expected and worst case; the `0-9` rows show the
duplicate-heavy constant-factor benefit.

Space:

| N range | preflight storage | recursion stack | auxiliary space |
| --- | --- | --- | --- |
| `N >= 100000` | `k` u32 indices = `4k` bytes, max 262,140 B | `O(log N)` | `O(k + log N)` |
| `40000 <= N < 100000` | 60 i64 = 480 B | `O(log N)` | `O(log N)` |
| `20000 <= N < 40000` | 38 i64 = 304 B | `O(log N)` | `O(log N)` |
| `10000 <= N < 20000` | 27 i64 = 216 B | `O(log N)` | `O(log N)` |
| `5000 <= N < 10000` | 19 i64 = 152 B | `O(log N)` | `O(log N)` |
| `N < 5000` | none | `O(log N)` | `O(log N)` |

The input array is `8N` bytes and is sorted in place, so total space is
`O(N)`. Auxiliary space is `O(k + log N)` for `N >= 100000` and
`O(log N)` below that; the sample and the recursion stack do not
overlap, so the peak is their maximum. For
`100000 <= N <= 65,535,999`, `k = floor(N / 1000)`, so the estimator
uses about `0.001N` u32 elements (`0.004N` bytes): 40 KB at 10M and
4 KB at 1M. At `N >= 65,536,000` the sample is capped at 65,535
entries, i.e. 262,140 bytes (about 256 KiB), so it stops growing with
`N`. The static probes are 152–480 bytes and the recursion stack is
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
10. **Binary size includes static runtimes.** Franko is 944 KB;
    C++ in-place is 158 KB and `std::sort` 154 KB.
11. **Tuned constants.** The estimator threshold, sample-size clamp,
    probe sizes, 32-element cutoff, and `2 * log2(N)` depth factor are
    tuned to this workload.

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
| `10M` | `0-9` | franko-inplace | 0.608289 | 5 | 0.121658 |
| `10M` | `0-9` | cpp-inplace | 0.595286 | 5 | 0.119057 |
| `10M` | `0-9` | cpp-std-sort | 0.982571 | 5 | 0.196514 |
| `10M` | `0.25pct` | franko-inplace | 2.449091 | 5 | 0.489818 |
| `10M` | `0.25pct` | cpp-inplace | 2.448567 | 5 | 0.489713 |
| `10M` | `0.25pct` | cpp-std-sort | 2.392678 | 5 | 0.478536 |
| `10M` | `0.5pct` | franko-inplace | 2.616190 | 5 | 0.523238 |
| `10M` | `0.5pct` | cpp-inplace | 2.547938 | 5 | 0.509588 |
| `10M` | `0.5pct` | cpp-std-sort | 2.418461 | 5 | 0.483692 |
| `10M` | `1pct` | franko-inplace | 2.776959 | 5 | 0.555392 |
| `10M` | `1pct` | cpp-inplace | 2.754937 | 5 | 0.550987 |
| `10M` | `1pct` | cpp-std-sort | 2.570415 | 5 | 0.514083 |
| `10M` | `10pct` | franko-inplace | 3.078186 | 5 | 0.615637 |
| `10M` | `10pct` | cpp-inplace | 3.107109 | 5 | 0.621422 |
| `10M` | `10pct` | cpp-std-sort | 2.959124 | 5 | 0.591825 |
| `10M` | `random` | franko-inplace | 3.161617 | 5 | 0.632323 |
| `10M` | `random` | cpp-inplace | 3.130418 | 5 | 0.626084 |
| `10M` | `random` | cpp-std-sort | 2.996993 | 5 | 0.599399 |
| `1M` | `0-9` | franko-inplace | 0.114662 | 10 | 0.011466 |
| `1M` | `0-9` | cpp-inplace | 0.121922 | 10 | 0.012192 |
| `1M` | `0-9` | cpp-std-sort | 0.182732 | 10 | 0.018273 |
| `1M` | `0.25pct` | franko-inplace | 0.369936 | 10 | 0.036994 |
| `1M` | `0.25pct` | cpp-inplace | 0.377309 | 10 | 0.037731 |
| `1M` | `0.25pct` | cpp-std-sort | 0.386479 | 10 | 0.038648 |
| `1M` | `0.5pct` | franko-inplace | 0.404051 | 10 | 0.040405 |
| `1M` | `0.5pct` | cpp-inplace | 0.403016 | 10 | 0.040302 |
| `1M` | `0.5pct` | cpp-std-sort | 0.417928 | 10 | 0.041793 |
| `1M` | `1pct` | franko-inplace | 0.433879 | 10 | 0.043388 |
| `1M` | `1pct` | cpp-inplace | 0.436725 | 10 | 0.043672 |
| `1M` | `1pct` | cpp-std-sort | 0.443609 | 10 | 0.044361 |
| `1M` | `10pct` | franko-inplace | 0.540870 | 10 | 0.054087 |
| `1M` | `10pct` | cpp-inplace | 0.532493 | 10 | 0.053249 |
| `1M` | `10pct` | cpp-std-sort | 0.521778 | 10 | 0.052178 |
| `1M` | `random` | franko-inplace | 0.565272 | 10 | 0.056527 |
| `1M` | `random` | cpp-inplace | 0.542143 | 10 | 0.054214 |
| `1M` | `random` | cpp-std-sort | 0.546508 | 10 | 0.054651 |
| `99k` | `0-9` | franko-inplace | 0.319088 | 300 | 0.001064 |
| `99k` | `0-9` | cpp-inplace | 0.327929 | 300 | 0.001093 |
| `99k` | `0-9` | cpp-std-sort | 0.458158 | 300 | 0.001527 |
| `99k` | `0.25pct` | franko-inplace | 0.712480 | 300 | 0.002375 |
| `99k` | `0.25pct` | cpp-inplace | 0.718538 | 300 | 0.002395 |
| `99k` | `0.25pct` | cpp-std-sort | 0.796299 | 300 | 0.002654 |
| `99k` | `0.5pct` | franko-inplace | 0.837088 | 300 | 0.002790 |
| `99k` | `0.5pct` | cpp-inplace | 0.827320 | 300 | 0.002758 |
| `99k` | `0.5pct` | cpp-std-sort | 0.916856 | 300 | 0.003056 |
| `99k` | `1pct` | franko-inplace | 0.954785 | 300 | 0.003183 |
| `99k` | `1pct` | cpp-inplace | 0.951440 | 300 | 0.003171 |
| `99k` | `1pct` | cpp-std-sort | 0.996612 | 300 | 0.003322 |
| `99k` | `10pct` | franko-inplace | 1.248481 | 300 | 0.004162 |
| `99k` | `10pct` | cpp-inplace | 1.254013 | 300 | 0.004180 |
| `99k` | `10pct` | cpp-std-sort | 1.244153 | 300 | 0.004147 |
| `99k` | `random` | franko-inplace | 1.251581 | 300 | 0.004172 |
| `99k` | `random` | cpp-inplace | 1.236374 | 300 | 0.004121 |
| `99k` | `random` | cpp-std-sort | 1.253467 | 300 | 0.004178 |
| `10k` | `0-9` | franko-inplace | 0.240709 | 3000 | 0.000080 |
| `10k` | `0-9` | cpp-inplace | 0.296687 | 3000 | 0.000099 |
| `10k` | `0-9` | cpp-std-sort | 0.376609 | 3000 | 0.000126 |
| `10k` | `0.25pct` | franko-inplace | 0.399219 | 3000 | 0.000133 |
| `10k` | `0.25pct` | cpp-inplace | 0.435891 | 3000 | 0.000145 |
| `10k` | `0.25pct` | cpp-std-sort | 0.526485 | 3000 | 0.000175 |
| `10k` | `0.5pct` | franko-inplace | 0.506939 | 3000 | 0.000169 |
| `10k` | `0.5pct` | cpp-inplace | 0.569862 | 3000 | 0.000190 |
| `10k` | `0.5pct` | cpp-std-sort | 0.608912 | 3000 | 0.000203 |
| `10k` | `1pct` | franko-inplace | 0.615065 | 3000 | 0.000205 |
| `10k` | `1pct` | cpp-inplace | 0.664373 | 3000 | 0.000221 |
| `10k` | `1pct` | cpp-std-sort | 0.682365 | 3000 | 0.000227 |
| `10k` | `10pct` | franko-inplace | 0.969581 | 3000 | 0.000323 |
| `10k` | `10pct` | cpp-inplace | 0.962495 | 3000 | 0.000321 |
| `10k` | `10pct` | cpp-std-sort | 0.975783 | 3000 | 0.000325 |
| `10k` | `random` | franko-inplace | 0.995943 | 3000 | 0.000332 |
| `10k` | `random` | cpp-inplace | 0.947755 | 3000 | 0.000316 |
| `10k` | `random` | cpp-std-sort | 1.007136 | 3000 | 0.000336 |
| `1k` | `0-9` | franko-inplace | 0.130433 | 30000 | 0.0000043478 |
| `1k` | `0-9` | cpp-inplace | 0.138659 | 30000 | 0.0000046220 |
| `1k` | `0-9` | cpp-std-sort | 0.159807 | 30000 | 0.0000053269 |
| `1k` | `0.25pct` | franko-inplace | 0.152785 | 30000 | 0.0000050928 |
| `1k` | `0.25pct` | cpp-inplace | 0.159065 | 30000 | 0.0000053022 |
| `1k` | `0.25pct` | cpp-std-sort | 0.172302 | 30000 | 0.0000057434 |
| `1k` | `0.5pct` | franko-inplace | 0.155256 | 30000 | 0.0000051752 |
| `1k` | `0.5pct` | cpp-inplace | 0.156990 | 30000 | 0.0000052330 |
| `1k` | `0.5pct` | cpp-std-sort | 0.182118 | 30000 | 0.0000060706 |
| `1k` | `1pct` | franko-inplace | 0.168429 | 30000 | 0.0000056143 |
| `1k` | `1pct` | cpp-inplace | 0.169503 | 30000 | 0.0000056501 |
| `1k` | `1pct` | cpp-std-sort | 0.189271 | 30000 | 0.0000063090 |
| `1k` | `10pct` | franko-inplace | 0.217506 | 30000 | 0.0000072502 |
| `1k` | `10pct` | cpp-inplace | 0.213216 | 30000 | 0.0000071072 |
| `1k` | `10pct` | cpp-std-sort | 0.218322 | 30000 | 0.0000072774 |
| `1k` | `random` | franko-inplace | 0.188516 | 30000 | 0.0000062839 |
| `1k` | `random` | cpp-inplace | 0.186700 | 30000 | 0.0000062233 |
| `1k` | `random` | cpp-std-sort | 0.181750 | 30000 | 0.0000060583 |

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
