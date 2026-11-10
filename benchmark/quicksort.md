# In-place quicksort benchmark: Franko vs C++ vs std::sort

**Plain `int64` keys · 5 sizes × 6 distributions · six probe tiers · all chunk timing · 2026-11-10**

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

- `0-9` wins every size for Franko (**0.65x–0.96x**). C++ wins
  10M–10k but loses 1k (`1.06x`).
- `1k` is mixed: Franko wins four of six; C++ wins two. Random-pivot
  Hoare is weaker than the old median-of-3 path at this size.
- `10k` is strong for both: five of six wins, losing only `random`.
- `99k` is strong for both: five of six wins, losing only `random`.
- `1M` is mixed: Franko wins `0-9`/`0.5pct`; C++ wins `0-9`/`1pct`.
- `10M` is hard: only `0-9` and `10pct` win.
- The six-tier probe layout uses exact integer boundaries and probe
  sizes 16, 22, 29, 37, 49, 64.
- Small-sort cutoff is 24. Apart from the sample-median first pivot
  for `N >= 4k`, all pivots are random-table pivots.
- All 30 cells agree.

## 1. Results

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | **0.126342** | 0.129026 | 0.189624 |
| `10M` | `0.25pct` | 0.479565 | 0.463737 | **0.448151** |
| `10M` | `0.5pct` | 0.524064 | 0.506239 | **0.471142** |
| `10M` | `1pct` | 0.544911 | 0.527258 | **0.499672** |
| `10M` | `10pct` | 0.610854 | **0.608713** | 0.616242 |
| `10M` | `random` | 0.635071 | 0.619155 | **0.613879** |
| `1M` | `0-9` | 0.013211 | **0.012636** | 0.017691 |
| `1M` | `0.25pct` | 0.040283 | 0.038199 | **0.035904** |
| `1M` | `0.5pct` | **0.040389** | 0.041837 | 0.041382 |
| `1M` | `1pct` | 0.048641 | **0.042032** | 0.043873 |
| `1M` | `10pct` | 0.052035 | 0.053806 | **0.050884** |
| `1M` | `random` | 0.053720 | 0.051958 | **0.050165** |
| `99k` | `0-9` | **0.001080** | 0.001150 | 0.001443 |
| `99k` | `0.25pct` | 0.002489 | **0.002481** | 0.002642 |
| `99k` | `0.5pct` | 0.002873 | **0.002742** | 0.002947 |
| `99k` | `1pct` | 0.003167 | **0.003106** | 0.003175 |
| `99k` | `10pct` | **0.003899** | 0.003956 | 0.004040 |
| `99k` | `random` | 0.004224 | 0.004194 | **0.004123** |
| `10k` | `0-9` | **0.000080** | 0.000098 | 0.000123 |
| `10k` | `0.25pct` | **0.000135** | 0.000147 | 0.000171 |
| `10k` | `0.5pct` | **0.000169** | 0.000183 | 0.000195 |
| `10k` | `1pct` | **0.000202** | 0.000210 | 0.000225 |
| `10k` | `10pct` | **0.000303** | 0.000305 | 0.000315 |
| `10k` | `random` | 0.000345 | 0.000326 | **0.000320** |
| `1k` | `0-9` | **0.0000048629** | 0.0000053768 | 0.0000050547 |
| `1k` | `0.25pct` | **0.0000053287** | 0.0000053791 | 0.0000056424 |
| `1k` | `0.5pct` | **0.0000055748** | 0.0000058568 | 0.0000063112 |
| `1k` | `1pct` | **0.0000060644** | 0.0000064777 | 0.0000062352 |
| `1k` | `10pct` | 0.0000078920 | 0.0000080425 | **0.0000072512** |
| `1k` | `random` | 0.0000063947 | 0.0000067169 | **0.0000061712** |

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | ---: | ---: |
| `10M` | `0-9` | 0.666x | 0.680x |
| `10M` | `0.25pct` | 1.070x | 1.035x |
| `10M` | `0.5pct` | 1.112x | 1.074x |
| `10M` | `1pct` | 1.091x | 1.055x |
| `10M` | `10pct` | 0.991x | 0.988x |
| `10M` | `random` | 1.035x | 1.009x |
| `1M` | `0-9` | 0.747x | 0.714x |
| `1M` | `0.25pct` | 1.122x | 1.064x |
| `1M` | `0.5pct` | 0.976x | 1.011x |
| `1M` | `1pct` | 1.109x | 0.958x |
| `1M` | `10pct` | 1.023x | 1.057x |
| `1M` | `random` | 1.071x | 1.036x |
| `99k` | `0-9` | 0.749x | 0.797x |
| `99k` | `0.25pct` | 0.942x | 0.939x |
| `99k` | `0.5pct` | 0.975x | 0.930x |
| `99k` | `1pct` | 0.997x | 0.978x |
| `99k` | `10pct` | 0.965x | 0.979x |
| `99k` | `random` | 1.025x | 1.017x |
| `10k` | `0-9` | 0.646x | 0.793x |
| `10k` | `0.25pct` | 0.789x | 0.864x |
| `10k` | `0.5pct` | 0.869x | 0.943x |
| `10k` | `1pct` | 0.902x | 0.937x |
| `10k` | `10pct` | 0.964x | 0.968x |
| `10k` | `random` | 1.080x | 1.017x |
| `1k` | `0-9` | 0.962x | 1.064x |
| `1k` | `0.25pct` | 0.944x | 0.953x |
| `1k` | `0.5pct` | 0.883x | 0.928x |
| `1k` | `1pct` | 0.973x | 1.039x |
| `1k` | `10pct` | 1.088x | 1.109x |
| `1k` | `random` | 1.036x | 1.088x |

A value below `1.00x` is faster than `std::sort`.

### 1.3 Wins by size

| size | Franko | C++ |
| --- | ---: | ---: |
| `10M` | 2/6 | 2/6 |
| `1M` | 2/6 | 2/6 |
| `99k` | 5/6 | 5/6 |
| `10k` | 5/6 | 5/6 |
| `1k` | 4/6 | 2/6 |

Key observations:

- `0-9` is the only dataset that wins at every size for Franko.
- `10M` wins are `0-9` and `10pct` for both implementations.
- `1M` wins are Franko `0-9`/`0.5pct`, C++ `0-9`/`1pct`.
- `99k` and `10k` lose only `random`.
- At `1k`, both lose `10pct` and `random`; C++ also loses `0-9` and
  `1pct`. The random-pivot Hoare path is the weak point.

## 2. Algorithm

### 2.1 Six exact probe tiers

For `4000 <= N < 100000`, the static probe size is chosen by tier:

| N range | probe `s` | `m80` lower | `m80` upper |
| --- | ---: | ---: | ---: |
| `4,000 <= N < 6,941` | 16 | 50.06 | 86.86 |
| `6,941 <= N < 12,050` | 22 | 46.00 | 79.87 |
| `12,050 <= N < 19,604` | 29 | 46.00 | 74.84 |
| `19,604 <= N < 34,364` | 37 | 46.00 | 80.64 |
| `34,364 <= N < 58,603` | 49 | 46.00 | 78.45 |
| `58,603 <= N < 100,000` | 64 | 46.00 | 78.50 |

Each probe takes one random offset from each of its `s` segments,
insertion-sorts the sampled indices by value, and selects DNF if any
adjacent pair is equal. The upper-column `m80` is evaluated at the
tier's exclusive upper boundary, i.e. the next tier's lower bound.

`m80` is the average duplicate-group size at 80% duplicate-detection
probability. Tier 2–6 lower bounds are the smallest integers with
`m80 > 46` for the chosen integer probe size. The 4k floor is fixed
and is the coarse case: `s=17` gives `m80=44.35`, so `s=16` is the
largest probe that satisfies `m80 > 46` at 4k.

### 2.2 Regimes

- **`N < 4000`:** Hoare partition with a random-table pivot.
- **`4000 <= N < 100000`:** static probe; any duplicate selects DNF,
  otherwise Lomuto. The first pivot is the median of the probe sample.
- **`N >= 100000`:** the `m_hat` estimator with
  `k = clamp(N/1000, 100, 20000)` samples. It heap-sorts the sampled
  indices by key. Let `pairs = k(k-1)/2` and let `collisions` be the
  number of equal-key sample pairs; DNF is selected when
  `collisions * N > 70 * pairs`, i.e. `m_hat > 70`. The first pivot is
  the median of the sample.

For both `N >= 4000` paths, layer 2 onward uses random-table pivots.
DNF and Lomuto have separate drivers; the partition loops use shared
kernels. There is no per-recursion `use_dnf` test.

The pivot table is used only for sampling and random pivots. Every
read advances `pivot_cursor`; it resets at the start of each sort.

### 2.3 Shared machinery

- Small-sort cutoff **24**: networks for 2–8, insertion sort for 9–24.
- Depth limit `2 * floor_log2(N)` with heapsort fallback.
- **Recursion discipline:** inside each recursive driver, only the
  smaller side recurses and the larger side loops. The entry functions
  perform one partition and then invoke the appropriate driver on each
  side, so total stack depth remains `O(log N)`.
- All element comparisons use only `<`.

### 2.4 Timing methodology

Each mode/dataset cell is one process and one chunk:

| size | count | sorts per chunk |
| --- | ---: | ---: |
| `10M` | 10,000,000 | 5 |
| `1M` | 1,000,000 | 10 |
| `99k` | 99,000 | 300 |
| `10k` | 10,000 | 3,000 |
| `1k` | 1,000 | 30,000 |

The process allocates its data vector once, then loops generation,
read-only sortedness check, sort, verification, and checksum. Python
times the whole process and divides by the repetition count. Random
data does not trigger the sortedness short-circuit. The measurement
therefore includes `O(N)` generation/check/checksum work plus the
sort; the per-key tables show that whole-workload cost.

## 3. Per-key nanoseconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | **12.63** | 12.90 | 18.96 |
| `10M` | `0.25pct` | 47.96 | 46.37 | **44.82** |
| `10M` | `0.5pct` | 52.41 | 50.62 | **47.11** |
| `10M` | `1pct` | 54.49 | 52.73 | **49.97** |
| `10M` | `10pct` | 61.09 | **60.87** | 61.62 |
| `10M` | `random` | 63.51 | 61.92 | **61.39** |
| `1M` | `0-9` | 13.21 | **12.64** | 17.69 |
| `1M` | `0.25pct` | 40.28 | 38.20 | **35.90** |
| `1M` | `0.5pct` | **40.39** | 41.84 | 41.38 |
| `1M` | `1pct` | 48.64 | **42.03** | 43.87 |
| `1M` | `10pct` | 52.04 | 53.81 | **50.88** |
| `1M` | `random` | 53.72 | 51.96 | **50.16** |
| `99k` | `0-9` | **10.91** | 11.62 | 14.57 |
| `99k` | `0.25pct` | 25.14 | **25.06** | 26.69 |
| `99k` | `0.5pct` | 29.02 | **27.70** | 29.77 |
| `99k` | `1pct` | 31.99 | **31.38** | 32.08 |
| `99k` | `10pct` | **39.39** | 39.96 | 40.81 |
| `99k` | `random` | 42.67 | 42.36 | **41.65** |
| `10k` | `0-9` | **7.98** | 9.80 | 12.35 |
| `10k` | `0.25pct` | **13.46** | 14.74 | 17.06 |
| `10k` | `0.5pct` | **16.92** | 18.35 | 19.46 |
| `10k` | `1pct` | **20.25** | 21.03 | 22.45 |
| `10k` | `10pct` | **30.34** | 30.48 | 31.47 |
| `10k` | `random` | 34.55 | 32.56 | **32.00** |
| `1k` | `0-9` | **4.86** | 5.38 | 5.05 |
| `1k` | `0.25pct` | **5.33** | 5.38 | 5.64 |
| `1k` | `0.5pct` | **5.57** | 5.86 | 6.31 |
| `1k` | `1pct` | **6.06** | 6.48 | 6.24 |
| `1k` | `10pct` | 7.89 | 8.04 | **7.25** |
| `1k` | `random` | 6.39 | 6.72 | **6.17** |

## 4. Analysis

### 4.1 Probe-tier effect

The six tiers make the `10k` and `99k` probes more consistent than the
old 4-tier layout. DNF choices are unchanged on this matrix:

| size | DNF | Lomuto |
| --- | --- | --- |
| `10M` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `1M` | same | same |
| `99k` | same | same |
| `10k` | same | same |
| `1k` | none — Hoare | none — Hoare |

The 10k tier now uses 22 samples instead of 27; the 99k tier uses 64
instead of 60. Both still classify the benchmark rows the same way.

### 4.2 Small-size weakness

The random-pivot Hoare path is the clear weak point at 1k:

- Franko loses `10pct` (1.088x) and `random` (1.036x).
- C++ loses `0-9` (1.064x), `1pct` (1.039x), `10pct` (1.109x), and
  `random` (1.088x).

The old median-of-3 Hoare path was stronger here. This is the main
regression of the current random-pivot design.

### 4.3 Large sizes

At 10M, the duplicate-heavy DNF rows still lose to `std::sort`; the
custom advantage is concentrated on `0-9` and `10pct`. At 1M the DNF
rows are mixed: `0-9` wins for both, Franko wins `0.5pct`, C++ wins
`1pct`, and both lose `0.25pct`. The depth guard keeps all paths
`O(N log N)`.

### 4.4 Franko versus C++

The implementations are close on most cells. The largest gaps are at
10k (`0-9` up to ~23% in Franko's favor) and at 1M `1pct` (~16% in
C++'s favor). The 1k `0-9` row also shows a ~11% Franko advantage.
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
| generation / sortedness check / checksum (harness) | `O(N)` |

DNF removes the equal block, so duplicate-heavy data behaves like
`O(N log d)`. Space:

| N range | preflight storage | stack |
| --- | --- | --- |
| `N >= 100000` | `k` u32 indices, max 20,000 = 80 KB | `O(log N)` |
| `4k <= N < 100k` | 64 u32 scratch = 256 B | `O(log N)` |
| `N < 4000` | none | `O(log N)` |

Total space is `O(N)` for the in-place `8N`-byte input array.

## 6. Limitations

1. Single chunk per cell; no SD/CV table.
2. 10M/1M reuse one allocation.
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
`1`=`10pct`, `0`=`random`. Repetitions are 5/10/300/3000/30000 for
10M/1M/99k/10k/1k.

## Appendix A: chunk timings

| size | dataset | mode | chunk s | reps | mean/sort |
| --- | --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | franko-inplace | 0.631708 | 5 | 0.126342 |
| `10M` | `0-9` | cpp-inplace | 0.645129 | 5 | 0.129026 |
| `10M` | `0-9` | cpp-std-sort | 0.948122 | 5 | 0.189624 |
| `10M` | `0.25pct` | franko-inplace | 2.397823 | 5 | 0.479565 |
| `10M` | `0.25pct` | cpp-inplace | 2.318683 | 5 | 0.463737 |
| `10M` | `0.25pct` | cpp-std-sort | 2.240756 | 5 | 0.448151 |
| `10M` | `0.5pct` | franko-inplace | 2.620322 | 5 | 0.524064 |
| `10M` | `0.5pct` | cpp-inplace | 2.531195 | 5 | 0.506239 |
| `10M` | `0.5pct` | cpp-std-sort | 2.355712 | 5 | 0.471142 |
| `10M` | `1pct` | franko-inplace | 2.724556 | 5 | 0.544911 |
| `10M` | `1pct` | cpp-inplace | 2.636288 | 5 | 0.527258 |
| `10M` | `1pct` | cpp-std-sort | 2.498359 | 5 | 0.499672 |
| `10M` | `10pct` | franko-inplace | 3.054270 | 5 | 0.610854 |
| `10M` | `10pct` | cpp-inplace | 3.043567 | 5 | 0.608713 |
| `10M` | `10pct` | cpp-std-sort | 3.081212 | 5 | 0.616242 |
| `10M` | `random` | franko-inplace | 3.175354 | 5 | 0.635071 |
| `10M` | `random` | cpp-inplace | 3.095775 | 5 | 0.619155 |
| `10M` | `random` | cpp-std-sort | 3.069396 | 5 | 0.613879 |
| `1M` | `0-9` | franko-inplace | 0.132111 | 10 | 0.013211 |
| `1M` | `0-9` | cpp-inplace | 0.126361 | 10 | 0.012636 |
| `1M` | `0-9` | cpp-std-sort | 0.176910 | 10 | 0.017691 |
| `1M` | `0.25pct` | franko-inplace | 0.402835 | 10 | 0.040283 |
| `1M` | `0.25pct` | cpp-inplace | 0.381986 | 10 | 0.038199 |
| `1M` | `0.25pct` | cpp-std-sort | 0.359041 | 10 | 0.035904 |
| `1M` | `0.5pct` | franko-inplace | 0.403888 | 10 | 0.040389 |
| `1M` | `0.5pct` | cpp-inplace | 0.418371 | 10 | 0.041837 |
| `1M` | `0.5pct` | cpp-std-sort | 0.413815 | 10 | 0.041382 |
| `1M` | `1pct` | franko-inplace | 0.486409 | 10 | 0.048641 |
| `1M` | `1pct` | cpp-inplace | 0.420323 | 10 | 0.042032 |
| `1M` | `1pct` | cpp-std-sort | 0.438731 | 10 | 0.043873 |
| `1M` | `10pct` | franko-inplace | 0.520353 | 10 | 0.052035 |
| `1M` | `10pct` | cpp-inplace | 0.538058 | 10 | 0.053806 |
| `1M` | `10pct` | cpp-std-sort | 0.508844 | 10 | 0.050884 |
| `1M` | `random` | franko-inplace | 0.537202 | 10 | 0.053720 |
| `1M` | `random` | cpp-inplace | 0.519581 | 10 | 0.051958 |
| `1M` | `random` | cpp-std-sort | 0.501649 | 10 | 0.050165 |
| `99k` | `0-9` | franko-inplace | 0.324126 | 300 | 0.001080 |
| `99k` | `0-9` | cpp-inplace | 0.345148 | 300 | 0.001150 |
| `99k` | `0-9` | cpp-std-sort | 0.432832 | 300 | 0.001443 |
| `99k` | `0.25pct` | franko-inplace | 0.746581 | 300 | 0.002489 |
| `99k` | `0.25pct` | cpp-inplace | 0.744160 | 300 | 0.002481 |
| `99k` | `0.25pct` | cpp-std-sort | 0.792674 | 300 | 0.002642 |
| `99k` | `0.5pct` | franko-inplace | 0.861888 | 300 | 0.002873 |
| `99k` | `0.5pct` | cpp-inplace | 0.822700 | 300 | 0.002742 |
| `99k` | `0.5pct` | cpp-std-sort | 0.884216 | 300 | 0.002947 |
| `99k` | `1pct` | franko-inplace | 0.950142 | 300 | 0.003167 |
| `99k` | `1pct` | cpp-inplace | 0.931912 | 300 | 0.003106 |
| `99k` | `1pct` | cpp-std-sort | 0.952650 | 300 | 0.003175 |
| `99k` | `10pct` | franko-inplace | 1.169751 | 300 | 0.003899 |
| `99k` | `10pct` | cpp-inplace | 1.186931 | 300 | 0.003956 |
| `99k` | `10pct` | cpp-std-sort | 1.211998 | 300 | 0.004040 |
| `99k` | `random` | franko-inplace | 1.267344 | 300 | 0.004224 |
| `99k` | `random` | cpp-inplace | 1.258163 | 300 | 0.004194 |
| `99k` | `random` | cpp-std-sort | 1.236895 | 300 | 0.004123 |
| `10k` | `0-9` | franko-inplace | 0.239271 | 3000 | 0.000080 |
| `10k` | `0-9` | cpp-inplace | 0.293859 | 3000 | 0.000098 |
| `10k` | `0-9` | cpp-std-sort | 0.370382 | 3000 | 0.000123 |
| `10k` | `0.25pct` | franko-inplace | 0.403882 | 3000 | 0.000135 |
| `10k` | `0.25pct` | cpp-inplace | 0.442210 | 3000 | 0.000147 |
| `10k` | `0.25pct` | cpp-std-sort | 0.511710 | 3000 | 0.000171 |
| `10k` | `0.5pct` | franko-inplace | 0.507476 | 3000 | 0.000169 |
| `10k` | `0.5pct` | cpp-inplace | 0.550427 | 3000 | 0.000183 |
| `10k` | `0.5pct` | cpp-std-sort | 0.583857 | 3000 | 0.000195 |
| `10k` | `1pct` | franko-inplace | 0.607415 | 3000 | 0.000202 |
| `10k` | `1pct` | cpp-inplace | 0.631039 | 3000 | 0.000210 |
| `10k` | `1pct` | cpp-std-sort | 0.673622 | 3000 | 0.000225 |
| `10k` | `10pct` | franko-inplace | 0.910336 | 3000 | 0.000303 |
| `10k` | `10pct` | cpp-inplace | 0.914261 | 3000 | 0.000305 |
| `10k` | `10pct` | cpp-std-sort | 0.944031 | 3000 | 0.000315 |
| `10k` | `random` | franko-inplace | 1.036486 | 3000 | 0.000345 |
| `10k` | `random` | cpp-inplace | 0.976756 | 3000 | 0.000326 |
| `10k` | `random` | cpp-std-sort | 0.960040 | 3000 | 0.000320 |
| `1k` | `0-9` | franko-inplace | 0.145886 | 30000 | 0.0000050000 |
| `1k` | `0-9` | cpp-inplace | 0.161304 | 30000 | 0.0000050000 |
| `1k` | `0-9` | cpp-std-sort | 0.151640 | 30000 | 0.0000050000 |
| `1k` | `0.25pct` | franko-inplace | 0.159861 | 30000 | 0.0000050000 |
| `1k` | `0.25pct` | cpp-inplace | 0.161373 | 30000 | 0.0000050000 |
| `1k` | `0.25pct` | cpp-std-sort | 0.169271 | 30000 | 0.0000060000 |
| `1k` | `0.5pct` | franko-inplace | 0.167245 | 30000 | 0.0000060000 |
| `1k` | `0.5pct` | cpp-inplace | 0.175705 | 30000 | 0.0000060000 |
| `1k` | `0.5pct` | cpp-std-sort | 0.189336 | 30000 | 0.0000060000 |
| `1k` | `1pct` | franko-inplace | 0.181932 | 30000 | 0.0000060000 |
| `1k` | `1pct` | cpp-inplace | 0.194332 | 30000 | 0.0000060000 |
| `1k` | `1pct` | cpp-std-sort | 0.187056 | 30000 | 0.0000060000 |
| `1k` | `10pct` | franko-inplace | 0.236759 | 30000 | 0.0000080000 |
| `1k` | `10pct` | cpp-inplace | 0.241276 | 30000 | 0.0000080000 |
| `1k` | `10pct` | cpp-std-sort | 0.217536 | 30000 | 0.0000070000 |
| `1k` | `random` | franko-inplace | 0.191841 | 30000 | 0.0000060000 |
| `1k` | `random` | cpp-inplace | 0.201506 | 30000 | 0.0000070000 |
| `1k` | `random` | cpp-std-sort | 0.185137 | 30000 | 0.0000060000 |

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

All three modes agree in every cell.
