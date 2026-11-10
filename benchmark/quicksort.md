# In-place quicksort benchmark: Franko vs C++ vs std::sort

**Plain `int64` keys · 5 sizes × 6 distributions · six probe tiers · doubled chunk repetitions · 2026-11-10**

This report compares in-place sorting only: Franko `-Omax`, a
hand-written C++ `-O3` port of the same algorithm, and C++ `std::sort`
as the reference.

Distributions, highest duplicate concentration first:

- **`0-9`:** 10 distinct values.
- **`0.25pct`:** `N / 400` distinct values.
- **`0.5pct`:** `N / 200` distinct values.
- **`1pct`:** `N / 100` distinct values.
- **`10pct`:** `N / 10` distinct values.
- **`random`:** full 63-bit random values, effectively all distinct.

## Short version

- `0-9` wins every size for both implementations. Franko ranges
  **0.69x–0.90x**; C++ ranges **0.69x–0.99x**.
- `1k`: Franko wins all six (**0.90x–0.97x**); C++ wins five and loses
  only `0.25pct` (1.03x).
- `10k`: C++ wins all six (**0.79x–0.99x**); Franko wins five and
  loses only `random` (1.04x).
- `99k`: C++ wins five of six (loses `random`); Franko wins four
  (loses `1pct` and `random`).
- `1M` and `10M`: only `0-9` wins for both.
- `<4k` uses median-of-3 Hoare. For `N >= 4k`, the sample median seeds
  the first partition and later pivots are random-table pivots.
- All 30 cells agree.

## 1. Results

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | **0.128077** | 0.128169 | 0.185409 |
| `10M` | `0.25pct` | 0.481429 | 0.458673 | **0.445574** |
| `10M` | `0.5pct` | 0.514019 | 0.494922 | **0.461779** |
| `10M` | `1pct` | 0.542867 | 0.522671 | **0.495824** |
| `10M` | `10pct` | 0.594343 | 0.596498 | **0.581903** |
| `10M` | `random` | 0.627235 | 0.616011 | **0.597042** |
| `1M` | `0-9` | **0.011757** | 0.011856 | 0.016485 |
| `1M` | `0.25pct` | 0.036459 | 0.035999 | **0.035384** |
| `1M` | `0.5pct` | 0.040478 | 0.039469 | **0.038341** |
| `1M` | `1pct` | 0.042838 | 0.041528 | **0.041389** |
| `1M` | `10pct` | 0.050058 | 0.050123 | **0.049420** |
| `1M` | `random` | 0.053074 | 0.052211 | **0.050315** |
| `99k` | `0-9` | **0.001095** | 0.001097 | 0.001469 |
| `99k` | `0.25pct` | 0.002544 | **0.002475** | 0.002633 |
| `99k` | `0.5pct` | 0.002869 | **0.002766** | 0.002940 |
| `99k` | `1pct` | 0.003240 | **0.003137** | 0.003205 |
| `99k` | `10pct` | **0.003983** | 0.003988 | 0.004036 |
| `99k` | `random` | 0.004306 | 0.004191 | **0.004148** |
| `10k` | `0-9` | **0.000086** | 0.000098 | 0.000125 |
| `10k` | `0.25pct` | **0.000142** | 0.000149 | 0.000172 |
| `10k` | `0.5pct` | **0.000181** | 0.000184 | 0.000201 |
| `10k` | `1pct` | **0.000208** | 0.000212 | 0.000226 |
| `10k` | `10pct` | **0.000306** | 0.000308 | 0.000322 |
| `10k` | `random` | 0.000339 | **0.000324** | 0.000327 |
| `1k` | `0-9` | **0.0000045565** | 0.0000049981 | 0.0000050507 |
| `1k` | `0.25pct` | **0.0000052256** | 0.0000058990 | 0.0000057414 |
| `1k` | `0.5pct` | **0.0000057194** | 0.0000059251 | 0.0000059850 |
| `1k` | `1pct` | **0.0000057824** | 0.0000060329 | 0.0000062332 |
| `1k` | `10pct` | **0.0000068941** | 0.0000070369 | 0.0000070883 |
| `1k` | `random` | 0.0000058957 | **0.0000057277** | 0.0000060985 |

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | ---: | ---: |
| `10M` | `0-9` | 0.691x | 0.691x |
| `10M` | `0.25pct` | 1.080x | 1.029x |
| `10M` | `0.5pct` | 1.113x | 1.072x |
| `10M` | `1pct` | 1.095x | 1.054x |
| `10M` | `10pct` | 1.021x | 1.025x |
| `10M` | `random` | 1.051x | 1.032x |
| `1M` | `0-9` | 0.713x | 0.719x |
| `1M` | `0.25pct` | 1.030x | 1.017x |
| `1M` | `0.5pct` | 1.056x | 1.029x |
| `1M` | `1pct` | 1.035x | 1.003x |
| `1M` | `10pct` | 1.013x | 1.014x |
| `1M` | `random` | 1.055x | 1.038x |
| `99k` | `0-9` | 0.746x | 0.747x |
| `99k` | `0.25pct` | 0.966x | 0.940x |
| `99k` | `0.5pct` | 0.976x | 0.941x |
| `99k` | `1pct` | 1.011x | 0.979x |
| `99k` | `10pct` | 0.987x | 0.988x |
| `99k` | `random` | 1.038x | 1.010x |
| `10k` | `0-9` | 0.686x | 0.786x |
| `10k` | `0.25pct` | 0.827x | 0.864x |
| `10k` | `0.5pct` | 0.901x | 0.915x |
| `10k` | `1pct` | 0.923x | 0.940x |
| `10k` | `10pct` | 0.952x | 0.957x |
| `10k` | `random` | 1.037x | 0.991x |
| `1k` | `0-9` | 0.902x | 0.990x |
| `1k` | `0.25pct` | 0.910x | 1.027x |
| `1k` | `0.5pct` | 0.956x | 0.990x |
| `1k` | `1pct` | 0.928x | 0.968x |
| `1k` | `10pct` | 0.973x | 0.993x |
| `1k` | `random` | 0.967x | 0.939x |

A value below `1.00x` is faster than `std::sort`.

### 1.3 Wins by size

| size | Franko | C++ |
| --- | ---: | ---: |
| `10M` | 1/6 | 1/6 |
| `1M` | 1/6 | 1/6 |
| `99k` | 4/6 | 5/6 |
| `10k` | 5/6 | 6/6 |
| `1k` | 6/6 | 5/6 |

Key observations:

- `0-9` is the only dataset that wins at every size.
- `1k` is now a Franko sweep; C++ loses only `0.25pct`.
- `10k` is a C++ sweep; Franko loses only `random`.
- `99k` is the last strong large row: both lose `random`, and Franko
  also loses `1pct`.
- `1M` and `10M` are hard: only `0-9` wins.

## 2. Algorithm

### 2.1 Six exact probe tiers

For `4000 <= N < 100000`, the static probe size is:

| N range | probe `s` | `m80` lower | `m80` upper |
| --- | ---: | ---: | ---: |
| `4,000 <= N < 6,941` | 16 | 50.06 | 86.86 |
| `6,941 <= N < 12,050` | 22 | 46.00 | 79.87 |
| `12,050 <= N < 19,604` | 29 | 46.00 | 74.84 |
| `19,604 <= N < 34,364` | 37 | 46.00 | 80.64 |
| `34,364 <= N < 58,603` | 49 | 46.00 | 78.45 |
| `58,603 <= N < 100,000` | 64 | 46.00 | 78.50 |

`m80` is the average duplicate-group size at 80% detection. Tier 2–6
lower bounds are the smallest integers with `m80 > 46` for the chosen
integer probe size. The 4k floor is fixed and coarse: `s=17` gives
`m80=44.35`, so `s=16` is the largest probe satisfying `m80 > 46`.

Each probe takes one random offset per segment, insertion-sorts the
sampled indices by value, and selects DNF if any adjacent pair is
equal. The upper-column `m80` is evaluated at the tier's exclusive
upper boundary.

### 2.2 Regimes

- **`N < 4000`:** Hoare partition with the optimal median-of-3
  network (`lo`, `mid`, `hi`), no preflight.
- **`4000 <= N < 100000`:** static probe; any duplicate selects DNF,
  otherwise Lomuto. The first pivot is the median of the probe sample.
- **`N >= 100000`:** the `m_hat` estimator with
  `k = clamp(N/1000, 100, 20000)` samples. It heap-sorts the sampled
  indices by key. Let `pairs = k(k-1)/2` and let `collisions` be the
  number of equal-key sample pairs; DNF is selected when
  `collisions * N > 70 * pairs`, i.e. `m_hat > 70`. The first pivot is
  the median of the sample.

For both `N >= 4000` paths, layer 2 onward uses random-table pivots.
DNF and Lomuto have separate recursive drivers; the partition loops
share the DNF and Lomuto kernels. Lomuto’s kernel takes the pivot
position directly, so there is no separate swap wrapper. There is no
per-recursion `use_dnf` test.

The pivot table is used only for sampling and random pivots. Every
read advances `pivot_cursor`; it resets at the start of each sort.

### 2.3 Shared machinery

- Small-sort cutoff **24**: networks for 2–8, insertion sort for 9–24.
- Depth limit `2 * floor_log2(N)` with heapsort fallback.
- Inside each recursive driver, only the smaller side recurses and the
  larger side loops. Entry functions perform one partition and invoke
  the matching driver on each side; total stack depth is `O(log N)`.
- All element comparisons use only `<`.

### 2.4 Timing methodology

Every mode/dataset cell is one process and one chunk. Repetitions are
doubled relative to the previous matrix:

| size | count | sorts per chunk |
| --- | ---: | ---: |
| `10M` | 10,000,000 | 10 |
| `1M` | 1,000,000 | 20 |
| `99k` | 99,000 | 600 |
| `10k` | 10,000 | 6,000 |
| `1k` | 1,000 | 60,000 |

The process allocates its data vector once, then loops generation,
read-only sortedness check, sort, verification, and checksum. Python
times the process wall time and divides by the repetition count.
Random data does not trigger the sortedness short-circuit. The
whole-workload per-key cost includes `O(N)` generation/check/checksum.

## 3. Per-key nanoseconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | **12.81** | 12.82 | 18.54 |
| `10M` | `0.25pct` | 48.14 | 45.87 | **44.56** |
| `10M` | `0.5pct` | 51.40 | 49.49 | **46.18** |
| `10M` | `1pct` | 54.29 | 52.27 | **49.58** |
| `10M` | `10pct` | 59.43 | 59.65 | **58.19** |
| `10M` | `random` | 62.72 | 61.60 | **59.70** |
| `1M` | `0-9` | **11.76** | 11.86 | 16.48 |
| `1M` | `0.25pct` | 36.46 | 36.00 | **35.38** |
| `1M` | `0.5pct` | 40.48 | 39.47 | **38.34** |
| `1M` | `1pct` | 42.84 | 41.53 | **41.39** |
| `1M` | `10pct` | 50.06 | 50.12 | **49.42** |
| `1M` | `random` | 53.07 | 52.21 | **50.31** |
| `99k` | `0-9` | **11.06** | 11.08 | 14.84 |
| `99k` | `0.25pct` | 25.70 | **25.00** | 26.60 |
| `99k` | `0.5pct` | 28.98 | **27.94** | 29.70 |
| `99k` | `1pct` | 32.73 | **31.68** | 32.37 |
| `99k` | `10pct` | **40.23** | 40.29 | 40.77 |
| `99k` | `random` | 43.50 | 42.33 | **41.90** |
| `10k` | `0-9` | **8.56** | 9.81 | 12.48 |
| `10k` | `0.25pct` | **14.23** | 14.86 | 17.20 |
| `10k` | `0.5pct` | **18.13** | 18.42 | 20.13 |
| `10k` | `1pct` | **20.84** | 21.23 | 22.58 |
| `10k` | `10pct` | **30.62** | 30.78 | 32.17 |
| `10k` | `random` | 33.90 | **32.38** | 32.69 |
| `1k` | `0-9` | **4.56** | 5.00 | 5.05 |
| `1k` | `0.25pct` | **5.23** | 5.90 | 5.74 |
| `1k` | `0.5pct` | **5.72** | 5.93 | 5.99 |
| `1k` | `1pct` | **5.78** | 6.03 | 6.23 |
| `1k` | `10pct` | **6.89** | 7.04 | 7.09 |
| `1k` | `random` | 5.90 | **5.73** | 6.10 |

## 4. Analysis

### 4.1 Probe tiers

The six-tier layout keeps DNF choices unchanged on this matrix:

| size | DNF | Lomuto |
| --- | --- | --- |
| `10M` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `1M` | same | same |
| `99k` | same | same |
| `10k` | same | same |
| `1k` | none — Hoare | none — Hoare |

The 10k tier uses 22 samples; the 99k tier uses 64.

### 4.2 Median-of-3 below 4k

The `<4k` median-of-3 Hoare path restores the small-size behavior:

- Franko wins all six 1k datasets.
- C++ wins five, losing only `0.25pct`.

This is a clear improvement over the random-pivot Hoare variant.

### 4.3 Large sizes

At 10M/1M, only `0-9` wins. The `10pct` rows are now small losses at
10M (1.02x) and 1M (1.01x). Duplicate-heavy percentage rows remain
above 1.0x at 10M/1M.

### 4.4 Franko versus C++

The implementations are usually close. Largest gaps:

- `10k` `0-9`: Franko ~15% faster.
- `1k` `0.25pct`: Franko ~13% faster.
- `1k` `0-9`: Franko ~10% faster.
- `10k` `random`: C++ ~5% faster.
- `10M` `0.25pct`: C++ ~5% faster.

These are code layout and generated-code call-overhead effects.

## 5. Complexity

Time:

| stage | cost |
| --- | --- |
| static probe (`4k <= N < 100k`) | `O(s^2)` insertion sort, `s <= 64`, so `O(1)` |
| estimator (`N >= 100k`) | `O(k)` sampling + `O(k log k)` index heap sort |
| first pivot (`N >= 4k`) | median of the preflight sample, `O(1)` extra |
| later pivots | `O(1)` per partition |
| partitioning | `O(N log N)` expected and worst case |
| small sorts (`<= 24`) | `O(1)` per leaf, `O(N)` total |
| generation / sortedness check / checksum | `O(N)` |

DNF removes the equal block, so duplicate-heavy data behaves like
`O(N log d)`. Space:

| N range | preflight storage | stack |
| --- | --- | --- |
| `N >= 100000` | `k` u32 indices, max 20,000 = 80 KB | `O(log N)` |
| `4k <= N < 100k` | 64 u32 scratch = 256 B | `O(log N)` |
| `N < 4000` | none | `O(log N)` |

Total space is `O(N)` for the in-place `8N`-byte input array.

## 6. Limitations

1. Single chunk per cell; doubling repetitions reduces but does not
   provide independent per-sort samples.
2. 10M/1M reuse one allocation across 10/20 sorts.
3. 1k timer resolution is 1 µs.
4. Uniform synthetic frequencies.
5. Probe false negatives exist; the 4k tier can miss a 65-distinct
   input about 13% of the time.
6. Every path is depth-limited and falls back to heapsort.
7. `std::sort` is introsort, a strong but different reference.
8. One machine, WSL2, i5-12600K, CPU 2.
9. Only `-Omax`/`-O3` release builds.
10. Binary sizes: Franko 960 KB, C++ 162 KB, `std::sort` 154 KB.

## 7. Reproduce

```bash
uv run python benchmark/quicksort_benchmark.py --cpu 2
```

Arguments: `3`=`0-9`, `5`=`0.25pct`, `4`=`0.5pct`, `2`=`1pct`,
`1`=`10pct`, `0`=`random`. Repetitions are 10/20/600/6000/60000 for
10M/1M/99k/10k/1k.

## Appendix A: chunk timings

| size | dataset | mode | chunk s | reps | mean/sort |
| --- | --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | franko-inplace | 1.280772 | 10 | 0.128077 |
| `10M` | `0-9` | cpp-inplace | 1.281691 | 10 | 0.128169 |
| `10M` | `0-9` | cpp-std-sort | 1.854091 | 10 | 0.185409 |
| `10M` | `0.25pct` | franko-inplace | 4.814289 | 10 | 0.481429 |
| `10M` | `0.25pct` | cpp-inplace | 4.586727 | 10 | 0.458673 |
| `10M` | `0.25pct` | cpp-std-sort | 4.455739 | 10 | 0.445574 |
| `10M` | `0.5pct` | franko-inplace | 5.140186 | 10 | 0.514019 |
| `10M` | `0.5pct` | cpp-inplace | 4.949223 | 10 | 0.494922 |
| `10M` | `0.5pct` | cpp-std-sort | 4.617788 | 10 | 0.461779 |
| `10M` | `1pct` | franko-inplace | 5.428673 | 10 | 0.542867 |
| `10M` | `1pct` | cpp-inplace | 5.226707 | 10 | 0.522671 |
| `10M` | `1pct` | cpp-std-sort | 4.958236 | 10 | 0.495824 |
| `10M` | `10pct` | franko-inplace | 5.943432 | 10 | 0.594343 |
| `10M` | `10pct` | cpp-inplace | 5.964984 | 10 | 0.596498 |
| `10M` | `10pct` | cpp-std-sort | 5.819028 | 10 | 0.581903 |
| `10M` | `random` | franko-inplace | 6.272350 | 10 | 0.627235 |
| `10M` | `random` | cpp-inplace | 6.160113 | 10 | 0.616011 |
| `10M` | `random` | cpp-std-sort | 5.970425 | 10 | 0.597042 |
| `1M` | `0-9` | franko-inplace | 0.235148 | 20 | 0.011757 |
| `1M` | `0-9` | cpp-inplace | 0.237112 | 20 | 0.011856 |
| `1M` | `0-9` | cpp-std-sort | 0.329697 | 20 | 0.016485 |
| `1M` | `0.25pct` | franko-inplace | 0.729179 | 20 | 0.036459 |
| `1M` | `0.25pct` | cpp-inplace | 0.719979 | 20 | 0.035999 |
| `1M` | `0.25pct` | cpp-std-sort | 0.707685 | 20 | 0.035384 |
| `1M` | `0.5pct` | franko-inplace | 0.809552 | 20 | 0.040478 |
| `1M` | `0.5pct` | cpp-inplace | 0.789374 | 20 | 0.039469 |
| `1M` | `0.5pct` | cpp-std-sort | 0.766822 | 20 | 0.038341 |
| `1M` | `1pct` | franko-inplace | 0.856769 | 20 | 0.042838 |
| `1M` | `1pct` | cpp-inplace | 0.830569 | 20 | 0.041528 |
| `1M` | `1pct` | cpp-std-sort | 0.827780 | 20 | 0.041389 |
| `1M` | `10pct` | franko-inplace | 1.001157 | 20 | 0.050058 |
| `1M` | `10pct` | cpp-inplace | 1.002465 | 20 | 0.050123 |
| `1M` | `10pct` | cpp-std-sort | 0.988396 | 20 | 0.049420 |
| `1M` | `random` | franko-inplace | 1.061483 | 20 | 0.053074 |
| `1M` | `random` | cpp-inplace | 1.044221 | 20 | 0.052211 |
| `1M` | `random` | cpp-std-sort | 1.006291 | 20 | 0.050315 |
| `99k` | `0-9` | franko-inplace | 0.657173 | 600 | 0.001095 |
| `99k` | `0-9` | cpp-inplace | 0.657989 | 600 | 0.001097 |
| `99k` | `0-9` | cpp-std-sort | 0.881337 | 600 | 0.001469 |
| `99k` | `0.25pct` | franko-inplace | 1.526682 | 600 | 0.002544 |
| `99k` | `0.25pct` | cpp-inplace | 1.485167 | 600 | 0.002475 |
| `99k` | `0.25pct` | cpp-std-sort | 1.579794 | 600 | 0.002633 |
| `99k` | `0.5pct` | franko-inplace | 1.721602 | 600 | 0.002869 |
| `99k` | `0.5pct` | cpp-inplace | 1.659892 | 600 | 0.002766 |
| `99k` | `0.5pct` | cpp-std-sort | 1.764042 | 600 | 0.002940 |
| `99k` | `1pct` | franko-inplace | 1.944172 | 600 | 0.003240 |
| `99k` | `1pct` | cpp-inplace | 1.882032 | 600 | 0.003137 |
| `99k` | `1pct` | cpp-std-sort | 1.922871 | 600 | 0.003205 |
| `99k` | `10pct` | franko-inplace | 2.389922 | 600 | 0.003983 |
| `99k` | `10pct` | cpp-inplace | 2.393015 | 600 | 0.003988 |
| `99k` | `10pct` | cpp-std-sort | 2.421605 | 600 | 0.004036 |
| `99k` | `random` | franko-inplace | 2.583858 | 600 | 0.004306 |
| `99k` | `random` | cpp-inplace | 2.514541 | 600 | 0.004191 |
| `99k` | `random` | cpp-std-sort | 2.489043 | 600 | 0.004148 |
| `10k` | `0-9` | franko-inplace | 0.513711 | 6000 | 0.000086 |
| `10k` | `0-9` | cpp-inplace | 0.588522 | 6000 | 0.000098 |
| `10k` | `0-9` | cpp-std-sort | 0.748984 | 6000 | 0.000125 |
| `10k` | `0.25pct` | franko-inplace | 0.853624 | 6000 | 0.000142 |
| `10k` | `0.25pct` | cpp-inplace | 0.891781 | 6000 | 0.000149 |
| `10k` | `0.25pct` | cpp-std-sort | 1.031939 | 6000 | 0.000172 |
| `10k` | `0.5pct` | franko-inplace | 1.088070 | 6000 | 0.000181 |
| `10k` | `0.5pct` | cpp-inplace | 1.104912 | 6000 | 0.000184 |
| `10k` | `0.5pct` | cpp-std-sort | 1.207639 | 6000 | 0.000201 |
| `10k` | `1pct` | franko-inplace | 1.250576 | 6000 | 0.000208 |
| `10k` | `1pct` | cpp-inplace | 1.273889 | 6000 | 0.000212 |
| `10k` | `1pct` | cpp-std-sort | 1.354534 | 6000 | 0.000226 |
| `10k` | `10pct` | franko-inplace | 1.837422 | 6000 | 0.000306 |
| `10k` | `10pct` | cpp-inplace | 1.846680 | 6000 | 0.000308 |
| `10k` | `10pct` | cpp-std-sort | 1.930067 | 6000 | 0.000322 |
| `10k` | `random` | franko-inplace | 2.033938 | 6000 | 0.000339 |
| `10k` | `random` | cpp-inplace | 1.943092 | 6000 | 0.000324 |
| `10k` | `random` | cpp-std-sort | 1.961223 | 6000 | 0.000327 |
| `1k` | `0-9` | franko-inplace | 0.273389 | 60000 | 0.0000050000 |
| `1k` | `0-9` | cpp-inplace | 0.299883 | 60000 | 0.0000050000 |
| `1k` | `0-9` | cpp-std-sort | 0.303043 | 60000 | 0.0000050000 |
| `1k` | `0.25pct` | franko-inplace | 0.313538 | 60000 | 0.0000050000 |
| `1k` | `0.25pct` | cpp-inplace | 0.353940 | 60000 | 0.0000060000 |
| `1k` | `0.25pct` | cpp-std-sort | 0.344482 | 60000 | 0.0000060000 |
| `1k` | `0.5pct` | franko-inplace | 0.343164 | 60000 | 0.0000060000 |
| `1k` | `0.5pct` | cpp-inplace | 0.355506 | 60000 | 0.0000060000 |
| `1k` | `0.5pct` | cpp-std-sort | 0.359101 | 60000 | 0.0000060000 |
| `1k` | `1pct` | franko-inplace | 0.346944 | 60000 | 0.0000060000 |
| `1k` | `1pct` | cpp-inplace | 0.361976 | 60000 | 0.0000060000 |
| `1k` | `1pct` | cpp-std-sort | 0.373994 | 60000 | 0.0000060000 |
| `1k` | `10pct` | franko-inplace | 0.413649 | 60000 | 0.0000070000 |
| `1k` | `10pct` | cpp-inplace | 0.422215 | 60000 | 0.0000070000 |
| `1k` | `10pct` | cpp-std-sort | 0.425297 | 60000 | 0.0000070000 |
| `1k` | `random` | franko-inplace | 0.353742 | 60000 | 0.0000060000 |
| `1k` | `random` | cpp-inplace | 0.343662 | 60000 | 0.0000060000 |
| `1k` | `random` | cpp-std-sort | 0.365907 | 60000 | 0.0000060000 |

## Appendix B: checksums

| size | dataset | checksum |
| --- | --- | ---: |
| `10M` | `0-9` | 449900290 |
| `10M` | `0.25pct` | 1250131126790 |
| `10M` | `0.5pct` | 2499794876790 |
| `10M` | `1pct` | 5000195876790 |
| `10M` | `10pct` | 49992691876790 |
| `10M` | `random` | 4129500018747053622 |
| `1M` | `0-9` | 89930140 |
| `1M` | `0.25pct` | 24992675540 |
| `1M` | `0.5pct` | 49963525540 |
| `1M` | `1pct` | 99923325540 |
| `1M` | `10pct` | 1000166125540 |
| `1M` | `random` | 2187870184574339684 |
| `99k` | `0-9` | 268120200 |
| `99k` | `0.25pct` | 7318752000 |
| `99k` | `0.5pct` | 14691193200 |
| `99k` | `1pct` | 29362102200 |
| `99k` | `10pct` | 294295012200 |
| `99k` | `random` | 2461398163493776040 |
| `10k` | `0-9` | 273444000 |
| `10k` | `0.25pct` | 725484000 |
| `10k` | `0.5pct` | 1480884000 |
| `10k` | `1pct` | 2958384000 |
| `10k` | `10pct` | 29739384000 |
| `10k` | `random` | 17951153846905590848 |
| `1k` | `0-9` | 275340000 |
| `1k` | `0.25pct` | 32340000 |
| `1k` | `0.5pct` | 120840000 |
| `1k` | `1pct` | 275340000 |
| `1k` | `10pct` | 2965740000 |
| `1k` | `random` | 17648461125796523232 |

All three modes agree in every cell.
