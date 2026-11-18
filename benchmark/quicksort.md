# In-place quicksort benchmark: Franko vs C++ vs std::sort

**Plain `int64` keys · 5 sizes × 6 distributions · nine-input small-sort network · 2026-11-19**

This report compares in-place sorting only: Franko `-Omax` calling
`std::quicksort<int64>(mview(values), 0, count)` from
`franko/algorithm`, a hand-written C++ `-O3` port of the same
preprocessing and partition design, and C++ `std::sort` as the
reference. Every binary uses the same host compiler and the
`compile.py` flag set, the system allocator, and is left unstripped; on
Linux the C++ runtime is static. Franko output is compiled with
exceptions and RTTI disabled. The Franko binary links the public
`franko/algorithm` module instead of carrying a private copy of the
sorting code. The run used `g++ 15.2.0`, OpenJDK `25.0.5` (Temurin),
WSL2 on an Intel i5-12600K, pinned to CPU 2. The Java toolchain was
rebuilt from current sources before the run; the Franko binary is
41 KB after the runtime slimdown.

Distributions, highest duplicate concentration first:

- **`0-9`:** 10 distinct values.
- **`0.25pct`:** `N / 400` distinct values.
- **`0.5pct`:** `N / 200` distinct values.
- **`1pct`:** `N / 100` distinct values.
- **`10pct`:** `N / 10` distinct values.
- **`random`:** full 63-bit random values, effectively all distinct.

## Short version

- `0-9` is the strongest family for both in-place ports: all ten
  in-place cells beat `std::sort` (Franko **0.629x–0.868x**, C++
  **0.630x–0.913x**). Franko is the fastest of the three on every
  `0-9` row.
- `1k`: Franko wins all six (**0.852x–0.922x** vs `std::sort`). The C++
  port beats `std::sort` on five rows; `0.25pct` is its only loss
  (**1.007x**).
- `10k`: three wins each for Franko and the C++ port; `std::sort`
  wins none. All twelve in-place cells beat `std::sort`.
- `99k`: Franko wins five of six and is the fastest in-place port on
  every row except `random`; `std::sort` wins `random` only.
- `1M`: Franko wins `0-9`, `1pct`, and `10pct`; `std::sort` wins the
  other three.
- `10M`: Franko wins `0-9` and `std::sort` wins the other five.
- All 30 cells agree on `sorted ok` and checksum.
- Binary sizes: Franko **41 KB**, C++ port **162 KB**, `std::sort`
  **154 KB**. All are unstripped and use the system allocator.
- Franko uses the 9-input sorting network and insertion sort for
  10–24 elements; the C++ port still uses its 8-input network and
  insertion sort for 9–24.
- `<4k` uses median-of-3 Hoare. At `4k–100k` a fixed probe selects DNF
  or Lomuto; above `100k` the `m_hat` estimator selects the partition
  kernel.

## 1. Results

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | --- | --- | --- |
| 10M | 0-9 | **0.110692** | 0.110847 | 0.175987 |
| 10M | 0.25pct | 0.449904 | 0.443038 | **0.428580** |
| 10M | 0.5pct | 0.481815 | 0.481944 | **0.446024** |
| 10M | 1pct | 0.508634 | 0.506288 | **0.478200** |
| 10M | 10pct | 0.571772 | 0.573317 | **0.565810** |
| 10M | random | 0.605260 | 0.598794 | **0.589959** |
| 1M | 0-9 | **0.010187** | 0.010282 | 0.015167 |
| 1M | 0.25pct | 0.033755 | 0.033569 | **0.032997** |
| 1M | 0.5pct | 0.036955 | 0.036378 | **0.035732** |
| 1M | 1pct | **0.038959** | 0.040312 | 0.039281 |
| 1M | 10pct | **0.047327** | 0.047686 | 0.047528 |
| 1M | random | 0.052916 | 0.050922 | **0.049838** |
| 99k | 0-9 | **0.000982** | 0.000985 | 0.001354 |
| 99k | 0.25pct | **0.002246** | 0.002266 | 0.002405 |
| 99k | 0.5pct | **0.002546** | 0.002594 | 0.002767 |
| 99k | 1pct | **0.002911** | 0.002930 | 0.002983 |
| 99k | 10pct | **0.003732** | 0.003799 | 0.003896 |
| 99k | random | 0.004158 | 0.004143 | **0.004091** |
| 10k | 0-9 | **0.000082** | 0.000088 | 0.000114 |
| 10k | 0.25pct | 0.000127 | **0.000126** | 0.000153 |
| 10k | 0.5pct | 0.000161 | **0.000161** | 0.000179 |
| 10k | 1pct | **0.000189** | 0.000191 | 0.000210 |
| 10k | 10pct | **0.000280** | 0.000284 | 0.000299 |
| 10k | random | 0.000317 | **0.000315** | 0.000324 |
| 1k | 0-9 | **0.000004** | 0.000004 | 0.000004 |
| 1k | 0.25pct | **0.000003** | 0.000003 | 0.000003 |
| 1k | 0.5pct | **0.000003** | 0.000004 | 0.000004 |
| 1k | 1pct | **0.000004** | 0.000004 | 0.000004 |
| 1k | 10pct | **0.000005** | 0.000005 | 0.000005 |
| 1k | random | **0.000005** | 0.000005 | 0.000006 |

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | --- | --- |
| 10M | 0-9 | 0.629x | 0.630x |
| 10M | 0.25pct | 1.050x | 1.034x |
| 10M | 0.5pct | 1.080x | 1.081x |
| 10M | 1pct | 1.064x | 1.059x |
| 10M | 10pct | 1.011x | 1.013x |
| 10M | random | 1.026x | 1.015x |
| 1M | 0-9 | 0.672x | 0.678x |
| 1M | 0.25pct | 1.023x | 1.017x |
| 1M | 0.5pct | 1.034x | 1.018x |
| 1M | 1pct | 0.992x | 1.026x |
| 1M | 10pct | 0.996x | 1.003x |
| 1M | random | 1.062x | 1.022x |
| 99k | 0-9 | 0.725x | 0.727x |
| 99k | 0.25pct | 0.934x | 0.942x |
| 99k | 0.5pct | 0.920x | 0.937x |
| 99k | 1pct | 0.976x | 0.982x |
| 99k | 10pct | 0.958x | 0.975x |
| 99k | random | 1.016x | 1.013x |
| 10k | 0-9 | 0.717x | 0.769x |
| 10k | 0.25pct | 0.829x | 0.821x |
| 10k | 0.5pct | 0.898x | 0.896x |
| 10k | 1pct | 0.898x | 0.906x |
| 10k | 10pct | 0.936x | 0.949x |
| 10k | random | 0.978x | 0.972x |
| 1k | 0-9 | 0.868x | 0.913x |
| 1k | 0.25pct | 0.902x | 1.007x |
| 1k | 0.5pct | 0.905x | 0.999x |
| 1k | 1pct | 0.864x | 0.939x |
| 1k | 10pct | 0.922x | 0.961x |
| 1k | random | 0.852x | 0.857x |

### 1.3 Wins by size

| size | Franko | C++ | `std::sort` |
| --- | ---: | ---: | ---: |
| 10M | 1/6 | 0/6 | 5/6 |
| 1M | 3/6 | 0/6 | 3/6 |
| 99k | 5/6 | 0/6 | 1/6 |
| 10k | 3/6 | 3/6 | 0/6 |
| 1k | 6/6 | 0/6 | 0/6 |

Key observations:

- `0-9` is the only dataset where both in-place ports beat `std::sort`
  at every size, and Franko is the fastest of the three on all five
  `0-9` rows.
- `1k` is a Franko sweep: six of six against `std::sort`
  (**0.852x–0.922x**). The C++ port beats `std::sort` on five rows;
  `0.25pct` is its only loss (**1.007x**).
- `10k` splits three-three between Franko and the C++ port;
  `std::sort` wins none, and all twelve in-place cells beat it.
- `99k` is Franko territory this run: five of six rows, losing only
  `random` to `std::sort`. The C++ port is never the fastest cell but
  is within 2% of Franko on every row.
- `1M` splits three-three between Franko (`0-9`, `1pct`, `10pct`)
  and `std::sort`. `1M` `1pct` is a Franko-only win (**0.992x** while
  the C++ port posts 1.026x).
- `10M` belongs to `std::sort` except `0-9`, where both in-place
  ports win by ~37%.
- The two custom implementations are close everywhere: the largest
  gaps are under 4% (see 4.5).

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

Each probe takes one random offset from each of `s` equal-length
segments, insertion-sorts the sampled indices by value, and selects DNF
if any adjacent pair is equal. The first pivot is the median of the
probe sample. The upper-column `m80` is evaluated at the tier's
exclusive upper boundary.

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
share the DNF and Lomuto kernels. Lomuto's kernel takes the pivot
position directly, so there is no separate swap wrapper. There is no
per-recursion `use_dnf` test.

The pivot table is used only for sampling and random pivots. Every
read advances `pivot_cursor`; it resets at the start of each sort.

### 2.3 Shared machinery

- Small-sort cutoff **24**: Franko uses networks for 2–9 and insertion
  sort for 10–24; the C++ port uses networks for 2–8 and insertion
  sort for 9–24.
- Depth limit `2 * floor_log2(N)` with heapsort fallback.
- Inside each recursive driver, only the smaller side recurses and the
  larger side loops. Entry functions perform one partition and invoke
  the matching driver on each side; total stack depth is `O(log N)`.
- All element comparisons use only `<`.
- The public `franko/algorithm` entry point uses half-open
  `[start, end)` ranges; the benchmark passes `0` and `count`.

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
timed sort, verification, and checksum. The timer runs around the
whole Franko `quicksort` call — including its sortedness preflight,
probe, and partition dispatch — and around the equivalent C++ check
and dispatch. Generation and checksum are outside the measured region.
Each binary times itself — Franko with `std::mono_time()`, C++ with
`std::chrono::steady_clock` — prints `elapsed_ns <total>`, and Python
only parses that value and divides by the repetition count. Random
data does not trigger the sortedness short-circuit.

Each binary gets one untimed warm-up per size/dataset. Measured mode
order is shuffled per cell with a fixed seed. The benchmark process is
pinned to CPU 2. This run executed alone on the machine: no other
benchmark ran concurrently.

## 3. Per-key nanoseconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| 10M | 0-9 | **11.07** | 11.08 | 17.60 |
| 10M | 0.25pct | 44.99 | 44.30 | **42.86** |
| 10M | 0.5pct | 48.18 | 48.19 | **44.60** |
| 10M | 1pct | 50.86 | 50.63 | **47.82** |
| 10M | 10pct | 57.18 | 57.33 | **56.58** |
| 10M | random | 60.53 | 59.88 | **59.00** |
| 1M | 0-9 | **10.19** | 10.28 | 15.17 |
| 1M | 0.25pct | 33.75 | 33.57 | **33.00** |
| 1M | 0.5pct | 36.95 | 36.38 | **35.73** |
| 1M | 1pct | **38.96** | 40.31 | 39.28 |
| 1M | 10pct | **47.33** | 47.69 | 47.53 |
| 1M | random | 52.92 | 50.92 | **49.84** |
| 99k | 0-9 | **9.92** | 9.95 | 13.68 |
| 99k | 0.25pct | **22.69** | 22.88 | 24.29 |
| 99k | 0.5pct | **25.72** | 26.20 | 27.95 |
| 99k | 1pct | **29.41** | 29.59 | 30.13 |
| 99k | 10pct | **37.69** | 38.38 | 39.36 |
| 99k | random | 42.00 | 41.85 | **41.32** |
| 10k | 0-9 | **8.18** | 8.77 | 11.41 |
| 10k | 0.25pct | 12.67 | **12.56** | 15.29 |
| 10k | 0.5pct | 16.11 | **16.08** | 17.94 |
| 10k | 1pct | **18.89** | 19.06 | 21.03 |
| 10k | 10pct | **27.97** | 28.37 | 29.89 |
| 10k | random | 31.69 | **31.48** | 32.39 |
| 1k | 0-9 | **3.59** | 3.77 | 4.14 |
| 1k | 0.25pct | **3.07** | 3.43 | 3.40 |
| 1k | 0.5pct | **3.30** | 3.64 | 3.65 |
| 1k | 1pct | **3.51** | 3.82 | 4.07 |
| 1k | 10pct | **4.58** | 4.78 | 4.97 |
| 1k | random | **4.80** | 4.83 | 5.64 |

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

The `<4k` median-of-3 Hoare path gives Franko all six 1k wins against
`std::sort` (**0.852x–0.922x**). The C++ port beats `std::sort` on five
rows; `0.25pct` is its only loss (**1.007x**). Against each other,
Franko leads all six rows, by 1% on `random` and up to ~10% on
`0.25pct` (3.07 vs 3.43 ns/key).

At this size each sort takes only a few microseconds, so fixed
per-call overheads and code layout account for a larger share of the
difference; the consistent direction across the rows is more meaningful
than any single 1k number.

### 4.3 Small and mid sizes

At `10k`, both in-place ports beat `std::sort` everywhere: all twelve
cells. Franko's largest lead is `0-9`, where it is about 7% faster
than the C++ port and 28% faster than `std::sort`. Both the 9-input
network and the static 22-sample probe are active at this size.

At `99k`, Franko takes five rows and is the fastest in-place port on
all of them; the C++ port trails by 0.3%–2%. Ten of the twelve
in-place cells beat `std::sort`. `std::sort` recovers `random`, where
the extra preflight and the custom partition cost more than
introsort's simpler path.

### 4.4 Large sizes

At 10M and 1M, `std::sort` wins most cells. At 10M Franko loses
1.011x–1.080x on the non-`0-9` rows, while the C++ port loses
1.013x–1.081x. At 1M Franko takes `0-9`, `1pct`, and `10pct`, and
`std::sort` takes the remaining three rows.

The `0-9` exception is large and repeatable: Franko beats `std::sort`
by 37.1% at 10M, 32.8% at 1M, and 27.5% at 99k, because the DNF
partition removes the equal block in one pass.

### 4.5 Franko versus C++

Head-to-head, Franko wins 20 of 30 cells and the C++ port wins 10.
Notable gaps:

- `1k` `0.25pct`: Franko is about 10% faster than the C++ port.
- `10k` `0-9`: Franko is about 7% faster than the C++ port.
- `1k` `0.5pct`/`1pct`: Franko is about 9%/8% faster.
- `1M` `random`: C++ is about 4% faster than Franko.
- `1M` `0.5pct`: C++ is about 1.6% faster.
- `10M` `0.25pct`: C++ is about 1.5% faster.

Everywhere else the two implementations are within about 1%. The
remaining gaps are consistent with code layout, call overhead, the
9-input network difference, and the different amount of generated
runtime support, not with a different asymptotic algorithm.

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
3. 1k sorts take only a few microseconds, so fixed per-call overheads
   are a larger share of their measurement.
4. Uniform synthetic frequencies.
5. Probe false negatives exist; the 4k tier can miss a 65-distinct
   input about 13% of the time.
6. Every path is depth-limited and falls back to heapsort.
7. `std::sort` is introsort, a strong but different reference.
8. One machine, WSL2, i5-12600K, CPU 2.
9. Only `-Omax`/`-O3` release builds; no PGO, LTO, or clang variants.
10. The C++ port still uses the 8-input network while Franko uses the
    9-input network, so the two in-place ports are not identical at
    `N = 9`.
11. Binary sizes are unstripped and system-allocator builds: Franko
    41 KB, C++ port 162 KB, `std::sort` 154 KB. Franko output is
    compiled with exceptions and RTTI disabled, which removes the C++
    exception/unwind runtime from the binary.

## 7. Reproduce

```bash
uv run python benchmark/quicksort_benchmark.py --cpu 2
```

Arguments: `3`=`0-9`, `5`=`0.25pct`, `4`=`0.5pct`, `2`=`1pct`,
`1`=`10pct`, `0`=`random`. Repetitions are 10/20/600/6000/60000 for
10M/1M/99k/10k/1k. Add `--only-franko` to time only the library
implementation.

## Appendix A: chunk timings

| size | dataset | mode | chunk s | sorts | mean/sort |
| --- | --- | --- | ---: | ---: | ---: |
| 10M | 0-9 | franko-inplace | 1.106922 | 10 | 0.110692 |
| 10M | 0-9 | cpp-inplace | 1.108474 | 10 | 0.110847 |
| 10M | 0-9 | cpp-std-sort | 1.759867 | 10 | 0.175987 |
| 10M | 0.25pct | franko-inplace | 4.499042 | 10 | 0.449904 |
| 10M | 0.25pct | cpp-inplace | 4.430383 | 10 | 0.443038 |
| 10M | 0.25pct | cpp-std-sort | 4.285801 | 10 | 0.428580 |
| 10M | 0.5pct | franko-inplace | 4.818146 | 10 | 0.481815 |
| 10M | 0.5pct | cpp-inplace | 4.819443 | 10 | 0.481944 |
| 10M | 0.5pct | cpp-std-sort | 4.460236 | 10 | 0.446024 |
| 10M | 1pct | franko-inplace | 5.086339 | 10 | 0.508634 |
| 10M | 1pct | cpp-inplace | 5.062881 | 10 | 0.506288 |
| 10M | 1pct | cpp-std-sort | 4.782001 | 10 | 0.478200 |
| 10M | 10pct | franko-inplace | 5.717720 | 10 | 0.571772 |
| 10M | 10pct | cpp-inplace | 5.733171 | 10 | 0.573317 |
| 10M | 10pct | cpp-std-sort | 5.658103 | 10 | 0.565810 |
| 10M | random | franko-inplace | 6.052599 | 10 | 0.605260 |
| 10M | random | cpp-inplace | 5.987944 | 10 | 0.598794 |
| 10M | random | cpp-std-sort | 5.899590 | 10 | 0.589959 |
| 1M | 0-9 | franko-inplace | 0.203744 | 20 | 0.010187 |
| 1M | 0-9 | cpp-inplace | 0.205635 | 20 | 0.010282 |
| 1M | 0-9 | cpp-std-sort | 0.303344 | 20 | 0.015167 |
| 1M | 0.25pct | franko-inplace | 0.675096 | 20 | 0.033755 |
| 1M | 0.25pct | cpp-inplace | 0.671381 | 20 | 0.033569 |
| 1M | 0.25pct | cpp-std-sort | 0.659948 | 20 | 0.032997 |
| 1M | 0.5pct | franko-inplace | 0.739092 | 20 | 0.036955 |
| 1M | 0.5pct | cpp-inplace | 0.727569 | 20 | 0.036378 |
| 1M | 0.5pct | cpp-std-sort | 0.714633 | 20 | 0.035732 |
| 1M | 1pct | franko-inplace | 0.779186 | 20 | 0.038959 |
| 1M | 1pct | cpp-inplace | 0.806239 | 20 | 0.040312 |
| 1M | 1pct | cpp-std-sort | 0.785614 | 20 | 0.039281 |
| 1M | 10pct | franko-inplace | 0.946533 | 20 | 0.047327 |
| 1M | 10pct | cpp-inplace | 0.953722 | 20 | 0.047686 |
| 1M | 10pct | cpp-std-sort | 0.950566 | 20 | 0.047528 |
| 1M | random | franko-inplace | 1.058330 | 20 | 0.052916 |
| 1M | random | cpp-inplace | 1.018447 | 20 | 0.050922 |
| 1M | random | cpp-std-sort | 0.996751 | 20 | 0.049838 |
| 99k | 0-9 | franko-inplace | 0.589425 | 600 | 0.000982 |
| 99k | 0-9 | cpp-inplace | 0.590995 | 600 | 0.000985 |
| 99k | 0-9 | cpp-std-sort | 0.812476 | 600 | 0.001354 |
| 99k | 0.25pct | franko-inplace | 1.347711 | 600 | 0.002246 |
| 99k | 0.25pct | cpp-inplace | 1.359350 | 600 | 0.002266 |
| 99k | 0.25pct | cpp-std-sort | 1.443057 | 600 | 0.002405 |
| 99k | 0.5pct | franko-inplace | 1.527557 | 600 | 0.002546 |
| 99k | 0.5pct | cpp-inplace | 1.556324 | 600 | 0.002594 |
| 99k | 0.5pct | cpp-std-sort | 1.660205 | 600 | 0.002767 |
| 99k | 1pct | franko-inplace | 1.746770 | 600 | 0.002911 |
| 99k | 1pct | cpp-inplace | 1.757912 | 600 | 0.002930 |
| 99k | 1pct | cpp-std-sort | 1.789659 | 600 | 0.002983 |
| 99k | 10pct | franko-inplace | 2.239068 | 600 | 0.003732 |
| 99k | 10pct | cpp-inplace | 2.279667 | 600 | 0.003799 |
| 99k | 10pct | cpp-std-sort | 2.337797 | 600 | 0.003896 |
| 99k | random | franko-inplace | 2.494866 | 600 | 0.004158 |
| 99k | random | cpp-inplace | 2.485944 | 600 | 0.004143 |
| 99k | random | cpp-std-sort | 2.454587 | 600 | 0.004091 |
| 10k | 0-9 | franko-inplace | 0.490818 | 6000 | 0.000082 |
| 10k | 0-9 | cpp-inplace | 0.526107 | 6000 | 0.000088 |
| 10k | 0-9 | cpp-std-sort | 0.684571 | 6000 | 0.000114 |
| 10k | 0.25pct | franko-inplace | 0.759955 | 6000 | 0.000127 |
| 10k | 0.25pct | cpp-inplace | 0.753508 | 6000 | 0.000126 |
| 10k | 0.25pct | cpp-std-sort | 0.917245 | 6000 | 0.000153 |
| 10k | 0.5pct | franko-inplace | 0.966577 | 6000 | 0.000161 |
| 10k | 0.5pct | cpp-inplace | 0.964547 | 6000 | 0.000161 |
| 10k | 0.5pct | cpp-std-sort | 1.076593 | 6000 | 0.000179 |
| 10k | 1pct | franko-inplace | 1.133342 | 6000 | 0.000189 |
| 10k | 1pct | cpp-inplace | 1.143813 | 6000 | 0.000191 |
| 10k | 1pct | cpp-std-sort | 1.261814 | 6000 | 0.000210 |
| 10k | 10pct | franko-inplace | 1.678156 | 6000 | 0.000280 |
| 10k | 10pct | cpp-inplace | 1.702327 | 6000 | 0.000284 |
| 10k | 10pct | cpp-std-sort | 1.793544 | 6000 | 0.000299 |
| 10k | random | franko-inplace | 1.901474 | 6000 | 0.000317 |
| 10k | random | cpp-inplace | 1.888977 | 6000 | 0.000315 |
| 10k | random | cpp-std-sort | 1.943480 | 6000 | 0.000324 |
| 1k | 0-9 | franko-inplace | 0.215248 | 60000 | 0.000004 |
| 1k | 0-9 | cpp-inplace | 0.226443 | 60000 | 0.000004 |
| 1k | 0-9 | cpp-std-sort | 0.248118 | 60000 | 0.000004 |
| 1k | 0.25pct | franko-inplace | 0.184118 | 60000 | 0.000003 |
| 1k | 0.25pct | cpp-inplace | 0.205594 | 60000 | 0.000003 |
| 1k | 0.25pct | cpp-std-sort | 0.204203 | 60000 | 0.000003 |
| 1k | 0.5pct | franko-inplace | 0.198006 | 60000 | 0.000003 |
| 1k | 0.5pct | cpp-inplace | 0.218465 | 60000 | 0.000004 |
| 1k | 0.5pct | cpp-std-sort | 0.218727 | 60000 | 0.000004 |
| 1k | 1pct | franko-inplace | 0.210761 | 60000 | 0.000004 |
| 1k | 1pct | cpp-inplace | 0.229181 | 60000 | 0.000004 |
| 1k | 1pct | cpp-std-sort | 0.244024 | 60000 | 0.000004 |
| 1k | 10pct | franko-inplace | 0.274967 | 60000 | 0.000005 |
| 1k | 10pct | cpp-inplace | 0.286585 | 60000 | 0.000005 |
| 1k | 10pct | cpp-std-sort | 0.298159 | 60000 | 0.000005 |
| 1k | random | franko-inplace | 0.288222 | 60000 | 0.000005 |
| 1k | random | cpp-inplace | 0.289638 | 60000 | 0.000005 |
| 1k | random | cpp-std-sort | 0.338138 | 60000 | 0.000006 |

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
