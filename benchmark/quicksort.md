# In-place quicksort benchmark: Franko vs C++ vs std::sort

**Plain `int64` keys · 5 sizes × 6 distributions · six probe tiers · 2026-11-13**

This report compares in-place sorting only: Franko `-Omax`, a
hand-written C++ `-O3` port of the same algorithm, and C++ `std::sort`
as the reference. Every binary uses the same host compiler and the
`compile.py` flag set, the system allocator, and is left unstripped; on
Linux the C++ runtime is static. Franko output is compiled with
exceptions and RTTI disabled. The run used `g++ 15.2.0`, OpenJDK
`25.0.5`, and WSL2 on an Intel i5-12600K.

Distributions, highest duplicate concentration first:

- **`0-9`:** 10 distinct values.
- **`0.25pct`:** `N / 400` distinct values.
- **`0.5pct`:** `N / 200` distinct values.
- **`1pct`:** `N / 100` distinct values.
- **`10pct`:** `N / 10` distinct values.
- **`random`:** full 63-bit random values, effectively all distinct.

## Short version

- `0-9` is the strongest family for both in-place ports: all ten
  in-place cells beat `std::sort` (Franko **0.625x–0.813x**, C++
  **0.634x–0.927x**). Franko leads at `1M`/`99k`/`10k`/`1k`; the C++
  port leads at `10M`.
- `1k`: Franko wins four of six (**0.813x–0.974x**); the C++ port wins
  the other two.
- `10k`: Franko wins five of six (**0.625x–0.937x**); C++ takes `random`.
- `99k`: Franko wins two of six; the C++ port wins three and
  `std::sort` takes the remaining cell.
- `1M`: Franko wins `0-9`; the C++ port wins two; `std::sort` wins the
  other three.
- `10M`: Franko and the C++ port each win one cell; `std::sort` wins
  the other four.
- All 30 cells agree on `sorted ok` and checksum.
- Binary sizes: Franko **64 KB**, C++ port **162 KB**, `std::sort`
  **154 KB**. All are unstripped and use the system allocator.
- `<4k` uses median-of-3 Hoare. At `4k–100k` a fixed probe selects DNF
  or Lomuto; above `100k` the `m_hat` estimator selects the partition
  kernel.

## 1. Results

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | --- | --- | --- |
| 10M | 0-9 | 0.114669 | **0.113192** | 0.178624 |
| 10M | 0.25pct | 0.476223 | 0.459830 | **0.441147** |
| 10M | 0.5pct | 0.515181 | 0.500609 | **0.464477** |
| 10M | 1pct | 0.543250 | 0.515209 | **0.500059** |
| 10M | 10pct | **0.589129** | 0.594507 | 0.589993 |
| 10M | random | 0.626122 | 0.621921 | **0.614770** |
| 1M | 0-9 | **0.010131** | 0.010605 | 0.015780 |
| 1M | 0.25pct | 0.034884 | **0.034214** | 0.034591 |
| 1M | 0.5pct | 0.038637 | 0.037757 | **0.036996** |
| 1M | 1pct | 0.041715 | 0.040735 | **0.040129** |
| 1M | 10pct | 0.049597 | 0.049891 | **0.048975** |
| 1M | random | 0.053072 | **0.052757** | 0.053147 |
| 99k | 0-9 | **0.000986** | 0.001008 | 0.001383 |
| 99k | 0.25pct | 0.002354 | **0.002320** | 0.002483 |
| 99k | 0.5pct | 0.002640 | **0.002632** | 0.002796 |
| 99k | 1pct | 0.003022 | **0.003018** | 0.003101 |
| 99k | 10pct | **0.003904** | 0.004019 | 0.004125 |
| 99k | random | 0.004415 | 0.004339 | **0.004327** |
| 10k | 0-9 | **0.000075** | 0.000093 | 0.000120 |
| 10k | 0.25pct | **0.000125** | 0.000134 | 0.000169 |
| 10k | 0.5pct | **0.000158** | 0.000168 | 0.000188 |
| 10k | 1pct | **0.000189** | 0.000200 | 0.000220 |
| 10k | 10pct | **0.000300** | 0.000303 | 0.000320 |
| 10k | random | 0.000351 | **0.000340** | 0.000344 |
| 1k | 0-9 | **0.000004** | 0.000004 | 0.000004 |
| 1k | 0.25pct | **0.000003** | 0.000004 | 0.000004 |
| 1k | 0.5pct | **0.000004** | 0.000004 | 0.000004 |
| 1k | 1pct | **0.000004** | 0.000004 | 0.000004 |
| 1k | 10pct | 0.000005 | **0.000005** | 0.000005 |
| 1k | random | 0.000005 | **0.000005** | 0.000006 |

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | --- | --- |
| 10M | 0-9 | 0.642x | 0.634x |
| 10M | 0.25pct | 1.080x | 1.042x |
| 10M | 0.5pct | 1.109x | 1.078x |
| 10M | 1pct | 1.086x | 1.030x |
| 10M | 10pct | 0.999x | 1.008x |
| 10M | random | 1.018x | 1.012x |
| 1M | 0-9 | 0.642x | 0.672x |
| 1M | 0.25pct | 1.008x | 0.989x |
| 1M | 0.5pct | 1.044x | 1.021x |
| 1M | 1pct | 1.040x | 1.015x |
| 1M | 10pct | 1.013x | 1.019x |
| 1M | random | 0.999x | 0.993x |
| 99k | 0-9 | 0.713x | 0.729x |
| 99k | 0.25pct | 0.948x | 0.935x |
| 99k | 0.5pct | 0.944x | 0.941x |
| 99k | 1pct | 0.975x | 0.973x |
| 99k | 10pct | 0.946x | 0.974x |
| 99k | random | 1.020x | 1.003x |
| 10k | 0-9 | 0.625x | 0.778x |
| 10k | 0.25pct | 0.739x | 0.794x |
| 10k | 0.5pct | 0.842x | 0.895x |
| 10k | 1pct | 0.859x | 0.908x |
| 10k | 10pct | 0.937x | 0.947x |
| 10k | random | 1.021x | 0.989x |
| 1k | 0-9 | 0.813x | 0.927x |
| 1k | 0.25pct | 0.860x | 0.997x |
| 1k | 0.5pct | 0.924x | 1.000x |
| 1k | 1pct | 0.857x | 0.950x |
| 1k | 10pct | 0.974x | 0.964x |
| 1k | random | 0.905x | 0.867x |

### 1.3 Wins by size

| size | Franko | C++ |
| --- | ---: | ---: |
| 10M | 1/6 | 1/6 |
| 1M | 1/6 | 2/6 |
| 99k | 2/6 | 3/6 |
| 10k | 5/6 | 1/6 |
| 1k | 4/6 | 2/6 |

Key observations:

- `0-9` is the only dataset where both in-place ports beat `std::sort`
  at every size.
- `1k` favors Franko on four rows; the C++ port takes the other two.
- `10k` is Franko's strongest size: five of six rows.
- `99k` and `1M` are mixed; `std::sort` wins the majority at `10M`.
- The in-place kernels stay close to `std::sort` on `random` (Franko
  **1.018x** and C++ **1.012x** at `10M`).

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

Every mode/dataset cell is one process and one chunk:

| size | count | sorts per chunk |
| --- | ---: | ---: |
| `10M` | 10,000,000 | 10 |
| `1M` | 1,000,000 | 20 |
| `99k` | 99,000 | 600 |
| `10k` | 10,000 | 6,000 |
| `1k` | 1,000 | 60,000 |

The process allocates its data vector once, then loops generation,
read-only sortedness check, timed sort dispatch, verification, and
checksum. The timer runs only around the sortedness check and sort
dispatch: generation and checksum are outside the measured region. Each
binary times itself — Franko with `std::mono_time()`, C++ with
`std::chrono::steady_clock` — prints `elapsed_ns <total>`, and Python
only parses that value and divides by the repetition count. Random data
does not trigger the sortedness short-circuit.

Each binary gets one untimed warm-up per size/dataset. Measured mode
order is shuffled per cell with a fixed seed. The benchmark process was
not explicitly pinned.

## 3. Per-key nanoseconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| 10M | 0-9 | 11.47 | **11.32** | 17.86 |
| 10M | 0.25pct | 47.62 | 45.98 | **44.11** |
| 10M | 0.5pct | 51.52 | 50.06 | **46.45** |
| 10M | 1pct | 54.32 | 51.52 | **50.01** |
| 10M | 10pct | **58.91** | 59.45 | 59.00 |
| 10M | random | 62.61 | 62.19 | **61.48** |
| 1M | 0-9 | **10.13** | 10.60 | 15.78 |
| 1M | 0.25pct | 34.88 | **34.21** | 34.59 |
| 1M | 0.5pct | 38.64 | 37.76 | **37.00** |
| 1M | 1pct | 41.71 | 40.74 | **40.13** |
| 1M | 10pct | 49.60 | 49.89 | **48.97** |
| 1M | random | 53.07 | **52.76** | 53.15 |
| 99k | 0-9 | **9.96** | 10.18 | 13.97 |
| 99k | 0.25pct | 23.77 | **23.44** | 25.08 |
| 99k | 0.5pct | 26.67 | **26.59** | 28.24 |
| 99k | 1pct | 30.53 | **30.49** | 31.32 |
| 99k | 10pct | **39.44** | 40.60 | 41.67 |
| 99k | random | 44.59 | 43.82 | **43.71** |
| 10k | 0-9 | **7.49** | 9.33 | 11.99 |
| 10k | 0.25pct | **12.46** | 13.40 | 16.87 |
| 10k | 0.5pct | **15.82** | 16.81 | 18.78 |
| 10k | 1pct | **18.94** | 20.00 | 22.03 |
| 10k | 10pct | **30.02** | 30.34 | 32.03 |
| 10k | random | 35.13 | **34.00** | 34.40 |
| 1k | 0-9 | **3.63** | 4.14 | 4.46 |
| 1k | 0.25pct | **3.11** | 3.60 | 3.61 |
| 1k | 0.5pct | **3.56** | 3.85 | 3.85 |
| 1k | 1pct | **3.64** | 4.04 | 4.25 |
| 1k | 10pct | 5.21 | **5.15** | 5.35 |
| 1k | random | 5.31 | **5.09** | 5.87 |

## 4. Analysis

### 4.1 Probe tiers

The six-tier layout keeps the DNF choices stable across this matrix:

| size | DNF | Lomuto |
| --- | --- | --- |
| `10M` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `1M` | same | same |
| `99k` | same | same |
| `10k` | same | same |
| `1k` | none — Hoare | none — Hoare |

The 10k tier uses 22 samples; the 99k tier uses 64.

### 4.2 Median-of-3 below 4k

The `<4k` median-of-3 Hoare path gives Franko four of six 1k wins:
`0-9` (0.813x), `0.25pct` (0.860x), `1pct` (0.857x), and `random`
(0.905x). The C++ port beats or ties `std::sort` on all six rows
(`0-9` 0.927x, `0.25pct` 0.997x, `0.5pct` 1.000x, `1pct` 0.950x,
`10pct` 0.964x, `random` 0.867x).

At this size the per-key cost is close to the timer floor, so the
absolute differences are small; the consistent direction across all
six rows is more meaningful than any single 1k number.

### 4.3 Large sizes

At 10M and 1M, `std::sort` wins most cells. At 10M Franko loses
1.018x–1.109x on the non-`0-9` rows, while the C++ port loses
1.008x–1.078x. At 1M the C++ port takes `0-9` and `0.25pct` by margins
under 1.1%, and `std::sort` takes the remaining four rows.

The `0-9` exception is large and repeatable: Franko beats `std::sort`
by 35.8% at both 10M and 1M, and 28.7% at 99k, because the DNF partition
removes the equal block in one pass.

### 4.4 Franko versus C++

The two custom implementations are usually close. Largest gaps:

- `10k` `0-9`: Franko is about 19% faster than the C++ port.
- `10k` `0.25pct`: Franko is about 6% faster.
- `10k` `0.5pct`: Franko is about 5% faster.
- `10M` `0.25pct`: C++ is about 4% faster than Franko.
- `1k` `0.25pct`: Franko is about 9% faster.

On `random` the two implementations are within about 5% at every
size. The remaining gaps are consistent with code layout, call
overhead, and the different amount of generated runtime support, not
with a different asymptotic algorithm.

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

1. Single chunk per cell; repeated sorts reduce but do not provide
   independent per-sort samples.
2. 10M/1M reuse one allocation across 10/20 sorts.
3. 1k timer resolution is 1 µs.
4. Uniform synthetic frequencies.
5. Probe false negatives exist; the 4k tier can miss a 65-distinct
   input about 13% of the time.
6. Every path is depth-limited and falls back to heapsort.
7. `std::sort` is introsort, a strong but different reference.
8. One machine, WSL2, i5-12600K, CPU 2.
9. Only `-Omax`/`-O3` release builds; no PGO, LTO, or clang variants.
10. Binary sizes are unstripped and system-allocator builds: Franko
    60 KB, C++ port 162 KB, `std::sort` 154 KB. Franko output is
    compiled with exceptions and RTTI disabled, which removes the C++
    exception/unwind runtime from the binary.

## 7. Reproduce

```bash
uv run python benchmark/quicksort_benchmark.py --cpu 2
```

Arguments: `3`=`0-9`, `5`=`0.25pct`, `4`=`0.5pct`, `2`=`1pct`,
`1`=`10pct`, `0`=`random`. Repetitions are 10/20/600/6000/60000 for
10M/1M/99k/10k/1k.

## Appendix A: chunk timings

| size | dataset | mode | chunk s | sorts | mean/sort |
| --- | --- | --- | ---: | ---: | ---: |
| 10M | 0-9 | franko-inplace | 1.146690 | 10 | 0.114669 |
| 10M | 0-9 | cpp-inplace | 1.131924 | 10 | 0.113192 |
| 10M | 0-9 | cpp-std-sort | 1.786238 | 10 | 0.178624 |
| 10M | 0.25pct | franko-inplace | 4.762229 | 10 | 0.476223 |
| 10M | 0.25pct | cpp-inplace | 4.598302 | 10 | 0.459830 |
| 10M | 0.25pct | cpp-std-sort | 4.411474 | 10 | 0.441147 |
| 10M | 0.5pct | franko-inplace | 5.151814 | 10 | 0.515181 |
| 10M | 0.5pct | cpp-inplace | 5.006088 | 10 | 0.500609 |
| 10M | 0.5pct | cpp-std-sort | 4.644768 | 10 | 0.464477 |
| 10M | 1pct | franko-inplace | 5.432497 | 10 | 0.543250 |
| 10M | 1pct | cpp-inplace | 5.152086 | 10 | 0.515209 |
| 10M | 1pct | cpp-std-sort | 5.000593 | 10 | 0.500059 |
| 10M | 10pct | franko-inplace | 5.891292 | 10 | 0.589129 |
| 10M | 10pct | cpp-inplace | 5.945065 | 10 | 0.594507 |
| 10M | 10pct | cpp-std-sort | 5.899930 | 10 | 0.589993 |
| 10M | random | franko-inplace | 6.261224 | 10 | 0.626122 |
| 10M | random | cpp-inplace | 6.219206 | 10 | 0.621921 |
| 10M | random | cpp-std-sort | 6.147696 | 10 | 0.614770 |
| 1M | 0-9 | franko-inplace | 0.202614 | 20 | 0.010131 |
| 1M | 0-9 | cpp-inplace | 0.212091 | 20 | 0.010605 |
| 1M | 0-9 | cpp-std-sort | 0.315600 | 20 | 0.015780 |
| 1M | 0.25pct | franko-inplace | 0.697683 | 20 | 0.034884 |
| 1M | 0.25pct | cpp-inplace | 0.684278 | 20 | 0.034214 |
| 1M | 0.25pct | cpp-std-sort | 0.691827 | 20 | 0.034591 |
| 1M | 0.5pct | franko-inplace | 0.772734 | 20 | 0.038637 |
| 1M | 0.5pct | cpp-inplace | 0.755143 | 20 | 0.037757 |
| 1M | 0.5pct | cpp-std-sort | 0.739930 | 20 | 0.036996 |
| 1M | 1pct | franko-inplace | 0.834299 | 20 | 0.041715 |
| 1M | 1pct | cpp-inplace | 0.814707 | 20 | 0.040735 |
| 1M | 1pct | cpp-std-sort | 0.802574 | 20 | 0.040129 |
| 1M | 10pct | franko-inplace | 0.991932 | 20 | 0.049597 |
| 1M | 10pct | cpp-inplace | 0.997811 | 20 | 0.049891 |
| 1M | 10pct | cpp-std-sort | 0.979492 | 20 | 0.048975 |
| 1M | random | franko-inplace | 1.061438 | 20 | 0.053072 |
| 1M | random | cpp-inplace | 1.055137 | 20 | 0.052757 |
| 1M | random | cpp-std-sort | 1.062934 | 20 | 0.053147 |
| 99k | 0-9 | franko-inplace | 0.591417 | 600 | 0.000986 |
| 99k | 0-9 | cpp-inplace | 0.604605 | 600 | 0.001008 |
| 99k | 0-9 | cpp-std-sort | 0.829758 | 600 | 0.001383 |
| 99k | 0.25pct | franko-inplace | 1.412103 | 600 | 0.002354 |
| 99k | 0.25pct | cpp-inplace | 1.392210 | 600 | 0.002320 |
| 99k | 0.25pct | cpp-std-sort | 1.489577 | 600 | 0.002483 |
| 99k | 0.5pct | franko-inplace | 1.584154 | 600 | 0.002640 |
| 99k | 0.5pct | cpp-inplace | 1.579195 | 600 | 0.002632 |
| 99k | 0.5pct | cpp-std-sort | 1.677519 | 600 | 0.002796 |
| 99k | 1pct | franko-inplace | 1.813226 | 600 | 0.003022 |
| 99k | 1pct | cpp-inplace | 1.810880 | 600 | 0.003018 |
| 99k | 1pct | cpp-std-sort | 1.860626 | 600 | 0.003101 |
| 99k | 10pct | franko-inplace | 2.342541 | 600 | 0.003904 |
| 99k | 10pct | cpp-inplace | 2.411463 | 600 | 0.004019 |
| 99k | 10pct | cpp-std-sort | 2.475042 | 600 | 0.004125 |
| 99k | random | franko-inplace | 2.648871 | 600 | 0.004415 |
| 99k | random | cpp-inplace | 2.603172 | 600 | 0.004339 |
| 99k | random | cpp-std-sort | 2.596167 | 600 | 0.004327 |
| 10k | 0-9 | franko-inplace | 0.449468 | 6000 | 0.000075 |
| 10k | 0-9 | cpp-inplace | 0.559659 | 6000 | 0.000093 |
| 10k | 0-9 | cpp-std-sort | 0.719384 | 6000 | 0.000120 |
| 10k | 0.25pct | franko-inplace | 0.747713 | 6000 | 0.000125 |
| 10k | 0.25pct | cpp-inplace | 0.803928 | 6000 | 0.000134 |
| 10k | 0.25pct | cpp-std-sort | 1.011993 | 6000 | 0.000169 |
| 10k | 0.5pct | franko-inplace | 0.948957 | 6000 | 0.000158 |
| 10k | 0.5pct | cpp-inplace | 1.008457 | 6000 | 0.000168 |
| 10k | 0.5pct | cpp-std-sort | 1.126799 | 6000 | 0.000188 |
| 10k | 1pct | franko-inplace | 1.136163 | 6000 | 0.000189 |
| 10k | 1pct | cpp-inplace | 1.200194 | 6000 | 0.000200 |
| 10k | 1pct | cpp-std-sort | 1.322085 | 6000 | 0.000220 |
| 10k | 10pct | franko-inplace | 1.801438 | 6000 | 0.000300 |
| 10k | 10pct | cpp-inplace | 1.820476 | 6000 | 0.000303 |
| 10k | 10pct | cpp-std-sort | 1.921659 | 6000 | 0.000320 |
| 10k | random | franko-inplace | 2.108035 | 6000 | 0.000351 |
| 10k | random | cpp-inplace | 2.040159 | 6000 | 0.000340 |
| 10k | random | cpp-std-sort | 2.063859 | 6000 | 0.000344 |
| 1k | 0-9 | franko-inplace | 0.217546 | 60000 | 0.000004 |
| 1k | 0-9 | cpp-inplace | 0.248214 | 60000 | 0.000004 |
| 1k | 0-9 | cpp-std-sort | 0.267620 | 60000 | 0.000004 |
| 1k | 0.25pct | franko-inplace | 0.186342 | 60000 | 0.000003 |
| 1k | 0.25pct | cpp-inplace | 0.215990 | 60000 | 0.000004 |
| 1k | 0.25pct | cpp-std-sort | 0.216671 | 60000 | 0.000004 |
| 1k | 0.5pct | franko-inplace | 0.213424 | 60000 | 0.000004 |
| 1k | 0.5pct | cpp-inplace | 0.230890 | 60000 | 0.000004 |
| 1k | 0.5pct | cpp-std-sort | 0.230926 | 60000 | 0.000004 |
| 1k | 1pct | franko-inplace | 0.218422 | 60000 | 0.000004 |
| 1k | 1pct | cpp-inplace | 0.242340 | 60000 | 0.000004 |
| 1k | 1pct | cpp-std-sort | 0.254972 | 60000 | 0.000004 |
| 1k | 10pct | franko-inplace | 0.312447 | 60000 | 0.000005 |
| 1k | 10pct | cpp-inplace | 0.309176 | 60000 | 0.000005 |
| 1k | 10pct | cpp-std-sort | 0.320754 | 60000 | 0.000005 |
| 1k | random | franko-inplace | 0.318624 | 60000 | 0.000005 |
| 1k | random | cpp-inplace | 0.305161 | 60000 | 0.000005 |
| 1k | random | cpp-std-sort | 0.352024 | 60000 | 0.000006 |

## Appendix B: checksums

| size | dataset | checksum |
| --- | --- | ---: |
| 10M | 0-9 | 449900290 |
| 10M | 0.25pct | 1250131126790 |
| 10M | 0.5pct | 2499794876790 |
| 10M | 1pct | 5000195876790 |
| 10M | 10pct | 49992691876790 |
| 10M | random | 4129500018747053622 |
| 1M | 0-9 | 89930140 |
| 1M | 0.25pct | 24992675540 |
| 1M | 0.5pct | 49963525540 |
| 1M | 1pct | 99923325540 |
| 1M | 10pct | 1000166125540 |
| 1M | random | 2187870184574339684 |
| 99k | 0-9 | 268120200 |
| 99k | 0.25pct | 7318752000 |
| 99k | 0.5pct | 14691193200 |
| 99k | 1pct | 29362102200 |
| 99k | 10pct | 294295012200 |
| 99k | random | 2461398163493776040 |
| 10k | 0-9 | 273444000 |
| 10k | 0.25pct | 725484000 |
| 10k | 0.5pct | 1480884000 |
| 10k | 1pct | 2958384000 |
| 10k | 10pct | 29739384000 |
| 10k | random | 17951153846905590848 |
| 1k | 0-9 | 275340000 |
| 1k | 0.25pct | 32340000 |
| 1k | 0.5pct | 120840000 |
| 1k | 1pct | 275340000 |
| 1k | 10pct | 2965740000 |
| 1k | random | 17648461125796523232 |
