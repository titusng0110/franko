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

- `0-9` wins every size: Franko is **0.62x–0.96x** and C++ is
  **0.59x–0.90x** of `std::sort`.
- `0.25pct`: C++ wins at 1M–1k (**0.79x–0.97x**) and loses only at 10M
  (1.04x). Franko wins at 99k–1k (**0.79x–0.99x**) and loses at 10M/1M
  (1.06x–1.12x).
- `0.5pct`: C++ wins at 1M–1k (**0.87x–0.97x**) and loses at 10M
  (1.04x). Franko wins at 10k/1k (0.87x–0.98x), ties 99k (1.00x), and
  loses at 10M/1M (1.05x–1.14x).
- `1pct`: C++ wins at 99k–1k (**0.89x–0.98x**) and loses at 10M/1M
  (1.02x–1.06x). Franko wins at 10k/1k (0.90x–0.98x) and loses at
  10M/1M/99k (1.03x–1.13x).
- `10pct` and `random` are near parity. Both lose 10M/1M by 4–6%; the
  small-size cells are mixed, with C++ generally closer to `std::sort`.
- The same pivot rule now runs through DNF, Lomuto, and Hoare: first
  partition and any `hi - lo > 10000` subrange use randomized
  median-of-3; the rest use plain median-of-3.
- Franko vs C++ is no longer within 5% on the hot duplicate-heavy rows:
  C++ is faster by up to about 9% at 10M/1M/99k. Franko is faster on
  `10k` `0-9` by about 7%.
- All 30 cells agree across the three modes.

---

## 1. Bottom line

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | 0.120232 | **0.114584** | 0.194273 |
| `10M` | `0.25pct` | 0.518950 | 0.480637 | **0.461737** |
| `10M` | `0.5pct` | 0.541770 | 0.495138 | **0.475294** |
| `10M` | `1pct` | 0.575541 | 0.536530 | **0.507512** |
| `10M` | `10pct` | 0.623917 | 0.626242 | **0.596932** |
| `10M` | `random` | 0.644525 | 0.636813 | **0.607677** |
| `1M` | `0-9` | **0.011446** | 0.011498 | 0.016859 |
| `1M` | `0.25pct` | 0.038271 | **0.035043** | 0.036173 |
| `1M` | `0.5pct` | 0.041809 | **0.038945** | 0.040018 |
| `1M` | `1pct` | 0.045849 | 0.042929 | **0.042130** |
| `1M` | `10pct` | 0.051764 | 0.051430 | **0.049608** |
| `1M` | `random` | 0.052927 | 0.052721 | **0.050873** |
| `99k` | `0-9` | 0.001105 | **0.001063** | 0.001491 |
| `99k` | `0.25pct` | 0.002595 | **0.002478** | 0.002703 |
| `99k` | `0.5pct` | 0.002977 | **0.002754** | 0.002967 |
| `99k` | `1pct` | 0.003363 | **0.003189** | 0.003264 |
| `99k` | `10pct` | 0.004242 | **0.004061** | 0.004101 |
| `99k` | `random` | 0.004252 | 0.004256 | **0.004085** |
| `10k` | `0-9` | **0.000087** | 0.000094 | 0.000124 |
| `10k` | `0.25pct` | **0.000135** | 0.000135 | 0.000171 |
| `10k` | `0.5pct` | **0.000170** | 0.000172 | 0.000197 |
| `10k` | `1pct` | 0.000202 | **0.000200** | 0.000224 |
| `10k` | `10pct` | 0.000310 | **0.000303** | 0.000312 |
| `10k` | `random` | 0.000321 | 0.000319 | **0.000317** |
| `1k` | `0-9` | 0.0000048369 | **0.0000045426** | 0.0000050362 |
| `1k` | `0.25pct` | 0.0000055838 | **0.0000053327** | 0.0000056267 |
| `1k` | `0.5pct` | 0.0000057550 | **0.0000054322** | 0.0000058704 |
| `1k` | `1pct` | 0.0000060831 | **0.0000058061** | 0.0000062203 |
| `1k` | `10pct` | 0.0000074031 | 0.0000071717 | **0.0000070199** |
| `1k` | `random` | 0.0000062225 | **0.0000059072** | 0.0000059150 |

All per-sort means are chunk wall time divided by the repetition count.

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | ---: | ---: |
| `10M` | `0-9` | 0.619x | 0.590x |
| `10M` | `0.25pct` | 1.124x | 1.041x |
| `10M` | `0.5pct` | 1.140x | 1.042x |
| `10M` | `1pct` | 1.134x | 1.057x |
| `10M` | `10pct` | 1.045x | 1.049x |
| `10M` | `random` | 1.061x | 1.048x |
| `1M` | `0-9` | 0.679x | 0.682x |
| `1M` | `0.25pct` | 1.058x | 0.969x |
| `1M` | `0.5pct` | 1.045x | 0.973x |
| `1M` | `1pct` | 1.088x | 1.019x |
| `1M` | `10pct` | 1.043x | 1.037x |
| `1M` | `random` | 1.040x | 1.036x |
| `99k` | `0-9` | 0.741x | 0.713x |
| `99k` | `0.25pct` | 0.960x | 0.916x |
| `99k` | `0.5pct` | 1.004x | 0.928x |
| `99k` | `1pct` | 1.030x | 0.977x |
| `99k` | `10pct` | 1.035x | 0.990x |
| `99k` | `random` | 1.041x | 1.042x |
| `10k` | `0-9` | 0.701x | 0.757x |
| `10k` | `0.25pct` | 0.792x | 0.793x |
| `10k` | `0.5pct` | 0.865x | 0.872x |
| `10k` | `1pct` | 0.902x | 0.893x |
| `10k` | `10pct` | 0.995x | 0.970x |
| `10k` | `random` | 1.012x | 1.006x |
| `1k` | `0-9` | 0.960x | 0.902x |
| `1k` | `0.25pct` | 0.992x | 0.948x |
| `1k` | `0.5pct` | 0.980x | 0.925x |
| `1k` | `1pct` | 0.978x | 0.933x |
| `1k` | `10pct` | 1.055x | 1.022x |
| `1k` | `random` | 1.052x | 0.999x |

A value below `1.00x` means the implementation is faster than
`std::sort`.

Key points:

- **`0-9` remains a clean win at every size.** The custom sorts are
  1.04–1.69x faster, with the narrowest margin at 1k.
- **C++ wins most duplicate-heavy cells outside 10M.** It wins
  `0.25pct` and `0.5pct` from 1M through 1k, and `1pct` from 99k
  through 1k.
- **Franko wins the smaller duplicate-heavy cells.** Its wins are
  concentrated at 99k/10k/1k for `0.25pct`, 10k/1k for `0.5pct`, and
  10k/1k for `1pct`.
- **10M is still the hard row.** Every non-`0-9` dataset loses there:
  C++ by 4–6%, Franko by 5–14%.
- **1k is mixed but positive.** Both implementations win 4 of the 6
  datasets; `10pct` is a small loss for both, and `random` is a Franko
  loss while C++ is at parity.
- **The Franko/C++ gap widened.** The generated Franko code emits the
  pivot-position helpers as separate functions, while the hand-written
  C++ inlines them; this is visible on the partition-heavy rows.

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
- **`N < 5000`:** Hoare partitioning.

For the estimator, let `pairs = k(k-1)/2` and let `collisions` be the
number of equal-key sample pairs. DNF is selected when
`collisions * N > 70 * pairs`, which is equivalent to an estimated
mean group size `m_hat = N * collisions / pairs > 70`.

The static probes take one random offset from each equal segment, using
`RANDOM_PIVOTS` and a per-segment mix. Each probe is insertion-sorted
inline; a single adjacent duplicate selects DNF. `RANDOM_PIVOTS` is a
fixed 128-entry table, so "randomized" means pseudo-random offsets;
runs are deterministic.

Every partition scheme uses the same pivot rule: the first partition,
and any subrange with `hi - lo > 10000`, takes the median of three
random positions; every other partition takes the median of `lo`,
`mid`, and `hi`. DNF uses the selected pivot value in place, Lomuto
swaps it to `hi`, and Hoare swaps it to `lo`. The pivot cursor resets
at the start of each sort and advances on every random draw. Every
path, including DNF, Lomuto, and Hoare, runs with a heapsort backup
after `2 * floor_log2(N)` partition levels.

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
| `10M` | `0-9` | 12.02 | **11.46** | 19.43 |
| `10M` | `0.25pct` | 51.90 | 48.06 | **46.17** |
| `10M` | `0.5pct` | 54.18 | 49.51 | **47.53** |
| `10M` | `1pct` | 57.55 | 53.65 | **50.75** |
| `10M` | `10pct` | 62.39 | 62.62 | **59.69** |
| `10M` | `random` | 64.45 | 63.68 | **60.77** |
| `1M` | `0-9` | **11.45** | 11.50 | 16.86 |
| `1M` | `0.25pct` | 38.27 | **35.04** | 36.17 |
| `1M` | `0.5pct` | 41.81 | **38.94** | 40.02 |
| `1M` | `1pct` | 45.85 | 42.93 | **42.13** |
| `1M` | `10pct` | 51.76 | 51.43 | **49.61** |
| `1M` | `random` | 52.93 | 52.72 | **50.87** |
| `99k` | `0-9` | 11.16 | **10.74** | 15.06 |
| `99k` | `0.25pct` | 26.21 | **25.03** | 27.31 |
| `99k` | `0.5pct` | 30.07 | **27.82** | 29.97 |
| `99k` | `1pct` | 33.97 | **32.22** | 32.97 |
| `99k` | `10pct` | 42.85 | **41.02** | 41.42 |
| `99k` | `random` | 42.95 | 42.99 | **41.27** |
| `10k` | `0-9` | **8.68** | 9.38 | 12.39 |
| `10k` | `0.25pct` | **13.50** | 13.51 | 17.05 |
| `10k` | `0.5pct` | **17.03** | 17.15 | 19.67 |
| `10k` | `1pct` | 20.16 | **19.97** | 22.35 |
| `10k` | `10pct` | 31.02 | **30.25** | 31.18 |
| `10k` | `random` | 32.05 | 31.86 | **31.66** |
| `1k` | `0-9` | 4.84 | **4.54** | 5.04 |
| `1k` | `0.25pct` | 5.58 | **5.33** | 5.63 |
| `1k` | `0.5pct` | 5.75 | **5.43** | 5.87 |
| `1k` | `1pct` | 6.08 | **5.81** | 6.22 |
| `1k` | `10pct` | 7.40 | 7.17 | **7.02** |
| `1k` | `random` | 6.22 | **5.91** | 5.91 |

The 10M/1M per-key costs are higher than the 99k/10k rows because the
80 MB and 8 MB arrays do not fit in the same cache levels; the 1k row
fits in L1/L2 and is dominated by per-sort overhead.

### 3.2 Chunk timings

Full chunk wall times and per-sort means are in Appendix A.

---

## 4. Analysis

### 4.1 The shared pivot rule

The randomized median-of-3 used on the first pass and on subranges
above 10000 costs three random draws and a few compare-swaps, but it
gives a much better pivot than a single random draw. The plain
median-of-3 used afterwards is cheaper and still avoids the worst
splits.

The effect is visible in the dataset pattern:

- `0-9` is DNF at 10M–10k and Hoare at 1k. It wins every size, with
  the narrowest margin at 1k.
- `0.25pct` is DNF at 10M–10k and Hoare at 1k. C++ wins 1M–1k;
  Franko wins 99k–1k. The 10M row is a loss for both, and 1M is also a
  Franko loss.
- `0.5pct` is DNF at 10M–10k and Hoare at 1k. C++ wins 1M–1k; Franko
  wins 10k/1k and ties 99k. C++ loses only 10M, while Franko loses
  10M/1M.
- `1pct` is DNF at 10M–10k and Hoare at 1k. C++ wins 99k–1k; Franko
  wins 10k/1k. Both lose 10M/1M, and 99k is a small Franko loss.
- `10pct` and `random` are Lomuto at 10M–10k and Hoare at 1k. They are
  near parity; the 10M/1M rows are 4–6% slower, and the small-size
  cells are mixed.

### 4.2 All-chunk timing and run-to-run variation

The 10M and 1M rows are one process per cell, with 5 or 10 sorts
inside it and one data-vector allocation. That removes 4/9 process
starts and repeated 80 MB/8 MB allocations per cell. The 10M `0-9`
custom time is 0.12 s here, versus roughly 0.136 s in an earlier
separate-process matrix. The comparison between modes inside this
report is still valid, but the absolute values should not be compared
directly to reports that timed each sort in its own process.

The single-chunk design also means the absolute numbers move between
runs with CPU state. The 1k row is especially sensitive: its per-sort
times are 4–7 µs and the `std::sort` reference moved by several percent
between runs, which shifts the ratios.

### 4.3 Cache effects

The high-cardinality rows show the expected per-key scaling:

| dataset | 10M | 1M | 99k | 10k | 1k |
| --- | ---: | ---: | ---: | ---: | ---: |
| `1pct` (Franko) | 57.6 ns | 45.8 ns | 34.0 ns | 20.2 ns | 6.1 ns |
| `10pct` (Franko) | 62.4 ns | 51.8 ns | 42.9 ns | 31.0 ns | 7.4 ns |
| `random` (Franko) | 64.5 ns | 52.9 ns | 43.0 ns | 32.1 ns | 6.2 ns |

At 10M the 80 MB array exceeds L3; at 1M the 8 MB array fits in L3;
at 99k the 0.8 MB array fits in L2; at 10k and 1k the arrays fit in
L1/L2. The 1k row is also where the Hoare path starts.

### 4.4 Franko versus hand-written C++

The hand-written C++ port is now faster on most partition-heavy cells.
The largest gaps are:

- `10M` `0.5pct`: Franko 0.541770 s vs C++ 0.495138 s (C++ 8.6% faster);
- `1M` `0.25pct`: Franko 0.038271 s vs C++ 0.035043 s (C++ 8.4% faster);
- `99k` `0.5pct`: Franko 0.002977 s vs C++ 0.002754 s (C++ 7.5% faster);
- `10M` `0.25pct`: Franko 0.518950 s vs C++ 0.480637 s (C++ 7.4% faster);
- `1M` `0.5pct`: Franko 0.041809 s vs C++ 0.038945 s (C++ 6.8% faster);
- `10M` `1pct`: Franko 0.575541 s vs C++ 0.536530 s (C++ 6.8% faster);
- `10k` `0-9`: Franko 0.000087 s vs C++ 0.000094 s (Franko 7.4% faster);
- `1k` `0-9`: Franko 0.0000048369 s vs C++ 0.0000045426 s (C++ 6.1% faster).

The pattern is consistent with code generation: Franko emits the
pivot-position helpers as separate generated functions, while the
hand-written C++ inlines its `static inline` helpers. The extra call
overhead is small per partition but visible on the 10M/1M/99k rows,
where there are many partitions. The 1k row also shows the effect,
though its timer resolution is coarse.

### 4.5 The algorithm versus `std::sort`

- The custom sorts win every `0-9` cell.
- C++ wins `0.25pct` and `0.5pct` from 1M through 1k, and `1pct` from
  99k through 1k.
- Franko wins `0.25pct` at 99k–1k, `0.5pct` at 10k/1k (and ties 99k),
  and `1pct` at 10k/1k.
- Every non-`0-9` 10M row is a loss for both; C++ is 1.04x–1.06x and
  Franko 1.05x–1.14x.
- At 1k both win four of six datasets; `10pct` is a small loss for
  both, and `random` is a Franko loss with C++ at parity.

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
| pivot selection | `O(1)` per partition |
| partitioning | `O(N log N)` expected and worst case |
| small sorts (`<= 32` elements) | `O(1)` per leaf, `O(N)` total |
| data generation / sortedness check / checksum (harness) | `O(N)` |

The partition stage is an introsort-style quicksort: the depth limit is
`2 * floor_log2(N)`, and any subproblem that exhausts it is heap-sorted.
Every level costs `O(N)` across the live subproblems, there are at most
`O(log N)` levels, and the heapsorted subproblems are disjoint, so the
partition stage is `O(N log N)` in the worst case. The same bound holds
for DNF, Lomuto, and Hoare. With randomized or median-of-3 pivots on
random input, the expected cost is also `O(N log N)`; median-of-3
reduces the constant by making the splits more balanced.

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
10. **Binary size includes static runtimes.** Franko is 948 KB;
    C++ in-place is 162 KB and `std::sort` 154 KB.
11. **Tuned constants.** The estimator threshold, sample-size clamp,
    probe sizes, 32-element cutoff, and `2 * log2(N)` depth factor are
    tuned to this workload.
12. **Generated-code helper calls.** Franko emits the pivot-position
    helpers as separate functions; the hand-written C++ inlines them.
    This contributes to the 4–9% Franko/C++ gap on partition-heavy
    rows.

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
| `10M` | `0-9` | franko-inplace | 0.601162 | 5 | 0.120232 |
| `10M` | `0-9` | cpp-inplace | 0.572918 | 5 | 0.114584 |
| `10M` | `0-9` | cpp-std-sort | 0.971363 | 5 | 0.194273 |
| `10M` | `0.25pct` | franko-inplace | 2.594752 | 5 | 0.518950 |
| `10M` | `0.25pct` | cpp-inplace | 2.403187 | 5 | 0.480637 |
| `10M` | `0.25pct` | cpp-std-sort | 2.308687 | 5 | 0.461737 |
| `10M` | `0.5pct` | franko-inplace | 2.708850 | 5 | 0.541770 |
| `10M` | `0.5pct` | cpp-inplace | 2.475691 | 5 | 0.495138 |
| `10M` | `0.5pct` | cpp-std-sort | 2.376469 | 5 | 0.475294 |
| `10M` | `1pct` | franko-inplace | 2.877704 | 5 | 0.575541 |
| `10M` | `1pct` | cpp-inplace | 2.682648 | 5 | 0.536530 |
| `10M` | `1pct` | cpp-std-sort | 2.537558 | 5 | 0.507512 |
| `10M` | `10pct` | franko-inplace | 3.119586 | 5 | 0.623917 |
| `10M` | `10pct` | cpp-inplace | 3.131208 | 5 | 0.626242 |
| `10M` | `10pct` | cpp-std-sort | 2.984662 | 5 | 0.596932 |
| `10M` | `random` | franko-inplace | 3.222624 | 5 | 0.644525 |
| `10M` | `random` | cpp-inplace | 3.184065 | 5 | 0.636813 |
| `10M` | `random` | cpp-std-sort | 3.038387 | 5 | 0.607677 |
| `1M` | `0-9` | franko-inplace | 0.114459 | 10 | 0.011446 |
| `1M` | `0-9` | cpp-inplace | 0.114980 | 10 | 0.011498 |
| `1M` | `0-9` | cpp-std-sort | 0.168593 | 10 | 0.016859 |
| `1M` | `0.25pct` | franko-inplace | 0.382708 | 10 | 0.038271 |
| `1M` | `0.25pct` | cpp-inplace | 0.350431 | 10 | 0.035043 |
| `1M` | `0.25pct` | cpp-std-sort | 0.361732 | 10 | 0.036173 |
| `1M` | `0.5pct` | franko-inplace | 0.418087 | 10 | 0.041809 |
| `1M` | `0.5pct` | cpp-inplace | 0.389446 | 10 | 0.038945 |
| `1M` | `0.5pct` | cpp-std-sort | 0.400182 | 10 | 0.040018 |
| `1M` | `1pct` | franko-inplace | 0.458485 | 10 | 0.045848 |
| `1M` | `1pct` | cpp-inplace | 0.429286 | 10 | 0.042929 |
| `1M` | `1pct` | cpp-std-sort | 0.421295 | 10 | 0.042129 |
| `1M` | `10pct` | franko-inplace | 0.517641 | 10 | 0.051764 |
| `1M` | `10pct` | cpp-inplace | 0.514297 | 10 | 0.051430 |
| `1M` | `10pct` | cpp-std-sort | 0.496085 | 10 | 0.049608 |
| `1M` | `random` | franko-inplace | 0.529265 | 10 | 0.052927 |
| `1M` | `random` | cpp-inplace | 0.527209 | 10 | 0.052721 |
| `1M` | `random` | cpp-std-sort | 0.508726 | 10 | 0.050873 |
| `99k` | `0-9` | franko-inplace | 0.331491 | 300 | 0.001105 |
| `99k` | `0-9` | cpp-inplace | 0.318972 | 300 | 0.001063 |
| `99k` | `0-9` | cpp-std-sort | 0.447379 | 300 | 0.001491 |
| `99k` | `0.25pct` | franko-inplace | 0.778371 | 300 | 0.002595 |
| `99k` | `0.25pct` | cpp-inplace | 0.743252 | 300 | 0.002478 |
| `99k` | `0.25pct` | cpp-std-sort | 0.811024 | 300 | 0.002703 |
| `99k` | `0.5pct` | franko-inplace | 0.893118 | 300 | 0.002977 |
| `99k` | `0.5pct` | cpp-inplace | 0.826245 | 300 | 0.002754 |
| `99k` | `0.5pct` | cpp-std-sort | 0.889968 | 300 | 0.002967 |
| `99k` | `1pct` | franko-inplace | 1.008886 | 300 | 0.003363 |
| `99k` | `1pct` | cpp-inplace | 0.956849 | 300 | 0.003189 |
| `99k` | `1pct` | cpp-std-sort | 0.979338 | 300 | 0.003264 |
| `99k` | `10pct` | franko-inplace | 1.272697 | 300 | 0.004242 |
| `99k` | `10pct` | cpp-inplace | 1.218399 | 300 | 0.004061 |
| `99k` | `10pct` | cpp-std-sort | 1.230229 | 300 | 0.004101 |
| `99k` | `random` | franko-inplace | 1.275627 | 300 | 0.004252 |
| `99k` | `random` | cpp-inplace | 1.276695 | 300 | 0.004256 |
| `99k` | `random` | cpp-std-sort | 1.225610 | 300 | 0.004085 |
| `10k` | `0-9` | franko-inplace | 0.260459 | 3000 | 0.000087 |
| `10k` | `0-9` | cpp-inplace | 0.281336 | 3000 | 0.000094 |
| `10k` | `0-9` | cpp-std-sort | 0.371606 | 3000 | 0.000124 |
| `10k` | `0.25pct` | franko-inplace | 0.404950 | 3000 | 0.000135 |
| `10k` | `0.25pct` | cpp-inplace | 0.405443 | 3000 | 0.000135 |
| `10k` | `0.25pct` | cpp-std-sort | 0.511530 | 3000 | 0.000171 |
| `10k` | `0.5pct` | franko-inplace | 0.510750 | 3000 | 0.000170 |
| `10k` | `0.5pct` | cpp-inplace | 0.514633 | 3000 | 0.000172 |
| `10k` | `0.5pct` | cpp-std-sort | 0.590239 | 3000 | 0.000197 |
| `10k` | `1pct` | franko-inplace | 0.604926 | 3000 | 0.000202 |
| `10k` | `1pct` | cpp-inplace | 0.598951 | 3000 | 0.000200 |
| `10k` | `1pct` | cpp-std-sort | 0.670622 | 3000 | 0.000224 |
| `10k` | `10pct` | franko-inplace | 0.930678 | 3000 | 0.000310 |
| `10k` | `10pct` | cpp-inplace | 0.907581 | 3000 | 0.000303 |
| `10k` | `10pct` | cpp-std-sort | 0.935330 | 3000 | 0.000312 |
| `10k` | `random` | franko-inplace | 0.961547 | 3000 | 0.000321 |
| `10k` | `random` | cpp-inplace | 0.955803 | 3000 | 0.000319 |
| `10k` | `random` | cpp-std-sort | 0.949778 | 3000 | 0.000317 |
| `1k` | `0-9` | franko-inplace | 0.145108 | 30000 | 0.0000050000 |
| `1k` | `0-9` | cpp-inplace | 0.136278 | 30000 | 0.0000050000 |
| `1k` | `0-9` | cpp-std-sort | 0.151085 | 30000 | 0.0000050000 |
| `1k` | `0.25pct` | franko-inplace | 0.167514 | 30000 | 0.0000060000 |
| `1k` | `0.25pct` | cpp-inplace | 0.159982 | 30000 | 0.0000050000 |
| `1k` | `0.25pct` | cpp-std-sort | 0.168801 | 30000 | 0.0000060000 |
| `1k` | `0.5pct` | franko-inplace | 0.172650 | 30000 | 0.0000060000 |
| `1k` | `0.5pct` | cpp-inplace | 0.162966 | 30000 | 0.0000050000 |
| `1k` | `0.5pct` | cpp-std-sort | 0.176113 | 30000 | 0.0000060000 |
| `1k` | `1pct` | franko-inplace | 0.182492 | 30000 | 0.0000060000 |
| `1k` | `1pct` | cpp-inplace | 0.174183 | 30000 | 0.0000060000 |
| `1k` | `1pct` | cpp-std-sort | 0.186608 | 30000 | 0.0000060000 |
| `1k` | `10pct` | franko-inplace | 0.222094 | 30000 | 0.0000070000 |
| `1k` | `10pct` | cpp-inplace | 0.215150 | 30000 | 0.0000070000 |
| `1k` | `10pct` | cpp-std-sort | 0.210596 | 30000 | 0.0000070000 |
| `1k` | `random` | franko-inplace | 0.186674 | 30000 | 0.0000060000 |
| `1k` | `random` | cpp-inplace | 0.177217 | 30000 | 0.0000060000 |
| `1k` | `random` | cpp-std-sort | 0.177449 | 30000 | 0.0000060000 |

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
