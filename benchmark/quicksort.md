# In-place quicksort benchmark: Franko vs C++ vs std::sort

**Plain `int64` keys · 5 sizes × 6 distributions · nine-input small-sort network · 2026-11-14**

This report compares in-place sorting only: Franko `-Omax` calling
`std::quicksort<int64>(getmview(values), 0, count)` from
`franko/algorithm`, a hand-written C++ `-O3` port of the same
preprocessing and partition design, and C++ `std::sort` as the
reference. Every binary uses the same host compiler and the
`compile.py` flag set, the system allocator, and is left unstripped; on
Linux the C++ runtime is static. Franko output is compiled with
exceptions and RTTI disabled. The Franko binary now links the public
`franko/algorithm` module instead of carrying a private copy of the
sorting code. The run used `g++ 15.2.0`, OpenJDK `25.0.5`, WSL2 on an
Intel i5-12600K, pinned to CPU 2.

Distributions, highest duplicate concentration first:

- **`0-9`:** 10 distinct values.
- **`0.25pct`:** `N / 400` distinct values.
- **`0.5pct`:** `N / 200` distinct values.
- **`1pct`:** `N / 100` distinct values.
- **`10pct`:** `N / 10` distinct values.
- **`random`:** full 63-bit random values, effectively all distinct.

## Short version

- `0-9` is the strongest family for both in-place ports: all ten
  in-place cells beat `std::sort` (Franko **0.617x–0.891x**, C++
  **0.645x–0.946x**). Franko leads at `1M`/`99k`/`10k`/`1k`; the C++
  port leads at `10M`.
- `1k`: Franko wins five of six (**0.887x–0.951x**); the C++ port
  takes `random` (**0.887x**). Both in-place ports beat `std::sort` on
  every row except C++ `0.25pct` (**1.011x**).
- `10k`: Franko wins four of six (**0.617x–0.890x**); the C++ port
  takes `10pct` and `random`. All six C++ cells and five Franko cells
  beat `std::sort`.
- `99k`: the C++ port wins four of six and Franko takes `0-9`;
  `std::sort` wins `random`.
- `1M`: Franko wins `0-9`, the C++ port wins `1pct`, and `std::sort`
  wins the other four.
- `10M`: the C++ port wins `0-9` and `std::sort` wins the other five.
- All 30 cells agree on `sorted ok` and checksum.
- Binary sizes: Franko **53 KB**, C++ port **162 KB**, `std::sort`
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
| 10M | 0-9 | 0.112862 | **0.111429** | 0.172636 |
| 10M | 0.25pct | 0.475210 | 0.441467 | **0.427620** |
| 10M | 0.5pct | 0.509493 | 0.491495 | **0.449033** |
| 10M | 1pct | 0.548924 | 0.515521 | **0.482113** |
| 10M | 10pct | 0.582409 | 0.580425 | **0.577214** |
| 10M | random | 0.619815 | 0.610477 | **0.601731** |
| 1M | 0-9 | **0.010412** | 0.010556 | 0.015771 |
| 1M | 0.25pct | 0.036200 | 0.034351 | **0.033933** |
| 1M | 0.5pct | 0.040512 | 0.038087 | **0.036893** |
| 1M | 1pct | 0.042502 | **0.040230** | 0.040958 |
| 1M | 10pct | 0.048239 | 0.048016 | **0.047581** |
| 1M | random | 0.052906 | 0.051303 | **0.050279** |
| 99k | 0-9 | **0.000983** | 0.000996 | 0.001360 |
| 99k | 0.25pct | 0.002370 | **0.002273** | 0.002418 |
| 99k | 0.5pct | 0.002636 | **0.002585** | 0.002785 |
| 99k | 1pct | 0.003088 | **0.002938** | 0.003001 |
| 99k | 10pct | 0.003799 | **0.003782** | 0.003927 |
| 99k | random | 0.004239 | 0.004215 | **0.004151** |
| 10k | 0-9 | **0.000072** | 0.000089 | 0.000117 |
| 10k | 0.25pct | **0.000119** | 0.000126 | 0.000158 |
| 10k | 0.5pct | **0.000157** | 0.000169 | 0.000185 |
| 10k | 1pct | **0.000189** | 0.000193 | 0.000212 |
| 10k | 10pct | 0.000292 | **0.000289** | 0.000306 |
| 10k | random | 0.000330 | **0.000318** | 0.000325 |
| 1k | 0-9 | **0.000004** | 0.000004 | 0.000004 |
| 1k | 0.25pct | **0.000003** | 0.000003 | 0.000003 |
| 1k | 0.5pct | **0.000004** | 0.000004 | 0.000004 |
| 1k | 1pct | **0.000004** | 0.000004 | 0.000004 |
| 1k | 10pct | **0.000005** | 0.000005 | 0.000005 |
| 1k | random | 0.000005 | **0.000005** | 0.000005 |

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | --- | --- |
| 10M | 0-9 | 0.654x | 0.645x |
| 10M | 0.25pct | 1.111x | 1.032x |
| 10M | 0.5pct | 1.135x | 1.095x |
| 10M | 1pct | 1.139x | 1.069x |
| 10M | 10pct | 1.009x | 1.006x |
| 10M | random | 1.030x | 1.015x |
| 1M | 0-9 | 0.660x | 0.669x |
| 1M | 0.25pct | 1.067x | 1.012x |
| 1M | 0.5pct | 1.098x | 1.032x |
| 1M | 1pct | 1.038x | 0.982x |
| 1M | 10pct | 1.014x | 1.009x |
| 1M | random | 1.052x | 1.020x |
| 99k | 0-9 | 0.723x | 0.732x |
| 99k | 0.25pct | 0.980x | 0.940x |
| 99k | 0.5pct | 0.946x | 0.928x |
| 99k | 1pct | 1.029x | 0.979x |
| 99k | 10pct | 0.968x | 0.963x |
| 99k | random | 1.021x | 1.016x |
| 10k | 0-9 | 0.617x | 0.763x |
| 10k | 0.25pct | 0.753x | 0.797x |
| 10k | 0.5pct | 0.844x | 0.910x |
| 10k | 1pct | 0.890x | 0.909x |
| 10k | 10pct | 0.953x | 0.944x |
| 10k | random | 1.015x | 0.980x |
| 1k | 0-9 | 0.891x | 0.946x |
| 1k | 0.25pct | 0.927x | 1.011x |
| 1k | 0.5pct | 0.948x | 0.986x |
| 1k | 1pct | 0.887x | 0.944x |
| 1k | 10pct | 0.951x | 0.966x |
| 1k | random | 0.938x | 0.887x |

### 1.3 Wins by size

| size | Franko | C++ | `std::sort` |
| --- | ---: | ---: | ---: |
| 10M | 0/6 | 1/6 | 5/6 |
| 1M | 1/6 | 1/6 | 4/6 |
| 99k | 1/6 | 4/6 | 1/6 |
| 10k | 4/6 | 2/6 | 0/6 |
| 1k | 5/6 | 1/6 | 0/6 |

Key observations:

- `0-9` is the only dataset where both in-place ports beat `std::sort`
  at every size.
- `1k` and `10k` favor Franko: five of six and four of six rows
  respectively.
- `99k` favors the C++ port, which takes four rows; `std::sort` only
  wins `random`.
- `1M` and `10M` are mostly `std::sort` territory, with the `0-9`
  exception shared by both in-place ports and a C++ win on `1M`
  `1pct`.
- The in-place kernels stay close to `std::sort` on `random` (Franko
  **1.030x** and C++ **1.015x** at `10M`).

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
share the DNF and Lomuto kernels. Lomuto’s kernel takes the pivot
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
pinned to CPU 2.

## 3. Per-key nanoseconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| 10M | 0-9 | 11.29 | **11.14** | 17.26 |
| 10M | 0.25pct | 47.52 | 44.15 | **42.76** |
| 10M | 0.5pct | 50.95 | 49.15 | **44.90** |
| 10M | 1pct | 54.89 | 51.55 | **48.21** |
| 10M | 10pct | 58.24 | 58.04 | **57.72** |
| 10M | random | 61.98 | 61.05 | **60.17** |
| 1M | 0-9 | **10.41** | 10.56 | 15.77 |
| 1M | 0.25pct | 36.20 | 34.35 | **33.93** |
| 1M | 0.5pct | 40.51 | 38.09 | **36.89** |
| 1M | 1pct | 42.50 | **40.23** | 40.96 |
| 1M | 10pct | 48.24 | 48.02 | **47.58** |
| 1M | random | 52.91 | 51.30 | **50.28** |
| 99k | 0-9 | **9.93** | 10.06 | 13.74 |
| 99k | 0.25pct | 23.93 | **22.96** | 24.42 |
| 99k | 0.5pct | 26.63 | **26.11** | 28.13 |
| 99k | 1pct | 31.19 | **29.68** | 30.32 |
| 99k | 10pct | 38.38 | **38.20** | 39.66 |
| 99k | random | 42.81 | 42.58 | **41.93** |
| 10k | 0-9 | **7.21** | 8.91 | 11.68 |
| 10k | 0.25pct | **11.93** | 12.63 | 15.84 |
| 10k | 0.5pct | **15.66** | 16.88 | 18.54 |
| 10k | 1pct | **18.89** | 19.30 | 21.22 |
| 10k | 10pct | 29.17 | **28.91** | 30.61 |
| 10k | random | 32.97 | **31.83** | 32.49 |
| 1k | 0-9 | **3.63** | 3.85 | 4.07 |
| 1k | 0.25pct | **3.15** | 3.44 | 3.40 |
| 1k | 0.5pct | **3.51** | 3.65 | 3.70 |
| 1k | 1pct | **3.63** | 3.86 | 4.09 |
| 1k | 10pct | **4.85** | 4.93 | 5.10 |
| 1k | random | 5.15 | **4.87** | 5.49 |

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
`std::sort` (**0.887x–0.951x**). The C++ port beats `std::sort` on five
rows; `0.25pct` is its only loss (**1.011x**). Against each other,
Franko leads five rows and the C++ port takes `random`
(**0.887x** versus **0.938x**).

At this size each sort takes only a few microseconds, so fixed
per-call overheads and code layout account for a larger share of the
difference; the consistent direction across the rows is more meaningful
than any single 1k number.

### 4.3 Small and mid sizes

At `10k`, both in-place ports beat `std::sort` almost everywhere: all
six C++ cells and five Franko cells. Franko’s largest lead is `0-9`,
where it is about 19% faster than the C++ port and 38% faster than
`std::sort`. Both the 9-input network and the static 22-sample probe
are active at this size.

At `99k`, the C++ port takes four rows; Franko wins only `0-9`. The
in-place advantage over `std::sort` narrows, but nine of the twelve
in-place cells still beat it. `std::sort` recovers `random`, where the
extra preflight and the custom partition cost more than introsort’s
simpler path.

### 4.4 Large sizes

At 10M and 1M, `std::sort` wins most cells. At 10M Franko loses
1.009x–1.139x on the non-`0-9` rows, while the C++ port loses
1.006x–1.095x. At 1M Franko takes `0-9`, the C++ port takes `1pct` by
1.8% over `std::sort`, and `std::sort` takes the remaining four rows.

The `0-9` exception is large and repeatable: Franko beats `std::sort`
by 34.6% at 10M, 34.0% at 1M, and 27.7% at 99k, because the DNF
partition removes the equal block in one pass.

### 4.5 Franko versus C++

The two custom implementations are usually close. Notable gaps:

- `10k` `0-9`: Franko is about 19% faster than the C++ port.
- `10k` `0.5pct`: Franko is about 7% faster.
- `10k` `0.25pct`: Franko is about 6% faster.
- `10M` `0.25pct`: C++ is about 8% faster than Franko.
- `10M` `1pct`: C++ is about 6.5% faster.
- `1M` `0.5pct`: C++ is about 6% faster.

On `random` the two implementations are within about 4% at every size
above 1k; the 1k row is noisier because each sort is only a few
microseconds long. The remaining gaps are consistent with code layout,
call overhead, the 9-input network difference, and the different amount
of generated runtime support, not with a different asymptotic algorithm.

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
    53 KB, C++ port 162 KB, `std::sort` 154 KB. Franko output is
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
| 10M | 0-9 | franko-inplace | 1.128619 | 10 | 0.112862 |
| 10M | 0-9 | cpp-inplace | 1.114289 | 10 | 0.111429 |
| 10M | 0-9 | cpp-std-sort | 1.726356 | 10 | 0.172636 |
| 10M | 0.25pct | franko-inplace | 4.752104 | 10 | 0.475210 |
| 10M | 0.25pct | cpp-inplace | 4.414671 | 10 | 0.441467 |
| 10M | 0.25pct | cpp-std-sort | 4.276204 | 10 | 0.427620 |
| 10M | 0.5pct | franko-inplace | 5.094931 | 10 | 0.509493 |
| 10M | 0.5pct | cpp-inplace | 4.914955 | 10 | 0.491495 |
| 10M | 0.5pct | cpp-std-sort | 4.490331 | 10 | 0.449033 |
| 10M | 1pct | franko-inplace | 5.489243 | 10 | 0.548924 |
| 10M | 1pct | cpp-inplace | 5.155211 | 10 | 0.515521 |
| 10M | 1pct | cpp-std-sort | 4.821133 | 10 | 0.482113 |
| 10M | 10pct | franko-inplace | 5.824089 | 10 | 0.582409 |
| 10M | 10pct | cpp-inplace | 5.804253 | 10 | 0.580425 |
| 10M | 10pct | cpp-std-sort | 5.772140 | 10 | 0.577214 |
| 10M | random | franko-inplace | 6.198148 | 10 | 0.619815 |
| 10M | random | cpp-inplace | 6.104768 | 10 | 0.610477 |
| 10M | random | cpp-std-sort | 6.017310 | 10 | 0.601731 |
| 1M | 0-9 | franko-inplace | 0.208234 | 20 | 0.010412 |
| 1M | 0-9 | cpp-inplace | 0.211117 | 20 | 0.010556 |
| 1M | 0-9 | cpp-std-sort | 0.315422 | 20 | 0.015771 |
| 1M | 0.25pct | franko-inplace | 0.724005 | 20 | 0.036200 |
| 1M | 0.25pct | cpp-inplace | 0.687018 | 20 | 0.034351 |
| 1M | 0.25pct | cpp-std-sort | 0.678667 | 20 | 0.033933 |
| 1M | 0.5pct | franko-inplace | 0.810249 | 20 | 0.040512 |
| 1M | 0.5pct | cpp-inplace | 0.761745 | 20 | 0.038087 |
| 1M | 0.5pct | cpp-std-sort | 0.737866 | 20 | 0.036893 |
| 1M | 1pct | franko-inplace | 0.850036 | 20 | 0.042502 |
| 1M | 1pct | cpp-inplace | 0.804609 | 20 | 0.040230 |
| 1M | 1pct | cpp-std-sort | 0.819153 | 20 | 0.040958 |
| 1M | 10pct | franko-inplace | 0.964786 | 20 | 0.048239 |
| 1M | 10pct | cpp-inplace | 0.960317 | 20 | 0.048016 |
| 1M | 10pct | cpp-std-sort | 0.951612 | 20 | 0.047581 |
| 1M | random | franko-inplace | 1.058113 | 20 | 0.052906 |
| 1M | random | cpp-inplace | 1.026067 | 20 | 0.051303 |
| 1M | random | cpp-std-sort | 1.005575 | 20 | 0.050279 |
| 99k | 0-9 | franko-inplace | 0.589723 | 600 | 0.000983 |
| 99k | 0-9 | cpp-inplace | 0.597626 | 600 | 0.000996 |
| 99k | 0-9 | cpp-std-sort | 0.816016 | 600 | 0.001360 |
| 99k | 0.25pct | franko-inplace | 1.421738 | 600 | 0.002370 |
| 99k | 0.25pct | cpp-inplace | 1.363796 | 600 | 0.002273 |
| 99k | 0.25pct | cpp-std-sort | 1.450650 | 600 | 0.002418 |
| 99k | 0.5pct | franko-inplace | 1.581530 | 600 | 0.002636 |
| 99k | 0.5pct | cpp-inplace | 1.551078 | 600 | 0.002585 |
| 99k | 0.5pct | cpp-std-sort | 1.671121 | 600 | 0.002785 |
| 99k | 1pct | franko-inplace | 1.852947 | 600 | 0.003088 |
| 99k | 1pct | cpp-inplace | 1.762872 | 600 | 0.002938 |
| 99k | 1pct | cpp-std-sort | 1.800847 | 600 | 0.003001 |
| 99k | 10pct | franko-inplace | 2.279509 | 600 | 0.003799 |
| 99k | 10pct | cpp-inplace | 2.269082 | 600 | 0.003782 |
| 99k | 10pct | cpp-std-sort | 2.355926 | 600 | 0.003927 |
| 99k | random | franko-inplace | 2.543110 | 600 | 0.004239 |
| 99k | random | cpp-inplace | 2.529254 | 600 | 0.004215 |
| 99k | random | cpp-std-sort | 2.490611 | 600 | 0.004151 |
| 10k | 0-9 | franko-inplace | 0.432494 | 6000 | 0.000072 |
| 10k | 0-9 | cpp-inplace | 0.534694 | 6000 | 0.000089 |
| 10k | 0-9 | cpp-std-sort | 0.700743 | 6000 | 0.000117 |
| 10k | 0.25pct | franko-inplace | 0.715597 | 6000 | 0.000119 |
| 10k | 0.25pct | cpp-inplace | 0.757731 | 6000 | 0.000126 |
| 10k | 0.25pct | cpp-std-sort | 0.950592 | 6000 | 0.000158 |
| 10k | 0.5pct | franko-inplace | 0.939368 | 6000 | 0.000157 |
| 10k | 0.5pct | cpp-inplace | 1.012625 | 6000 | 0.000169 |
| 10k | 0.5pct | cpp-std-sort | 1.112533 | 6000 | 0.000185 |
| 10k | 1pct | franko-inplace | 1.133166 | 6000 | 0.000189 |
| 10k | 1pct | cpp-inplace | 1.157897 | 6000 | 0.000193 |
| 10k | 1pct | cpp-std-sort | 1.273460 | 6000 | 0.000212 |
| 10k | 10pct | franko-inplace | 1.749967 | 6000 | 0.000292 |
| 10k | 10pct | cpp-inplace | 1.734331 | 6000 | 0.000289 |
| 10k | 10pct | cpp-std-sort | 1.836739 | 6000 | 0.000306 |
| 10k | random | franko-inplace | 1.978014 | 6000 | 0.000330 |
| 10k | random | cpp-inplace | 1.909894 | 6000 | 0.000318 |
| 10k | random | cpp-std-sort | 1.949605 | 6000 | 0.000325 |
| 1k | 0-9 | franko-inplace | 0.217552 | 60000 | 0.000004 |
| 1k | 0-9 | cpp-inplace | 0.230966 | 60000 | 0.000004 |
| 1k | 0-9 | cpp-std-sort | 0.244171 | 60000 | 0.000004 |
| 1k | 0.25pct | franko-inplace | 0.189126 | 60000 | 0.000003 |
| 1k | 0.25pct | cpp-inplace | 0.206247 | 60000 | 0.000003 |
| 1k | 0.25pct | cpp-std-sort | 0.203937 | 60000 | 0.000003 |
| 1k | 0.5pct | franko-inplace | 0.210511 | 60000 | 0.000004 |
| 1k | 0.5pct | cpp-inplace | 0.218992 | 60000 | 0.000004 |
| 1k | 0.5pct | cpp-std-sort | 0.222002 | 60000 | 0.000004 |
| 1k | 1pct | franko-inplace | 0.217689 | 60000 | 0.000004 |
| 1k | 1pct | cpp-inplace | 0.231567 | 60000 | 0.000004 |
| 1k | 1pct | cpp-std-sort | 0.245309 | 60000 | 0.000004 |
| 1k | 10pct | franko-inplace | 0.291088 | 60000 | 0.000005 |
| 1k | 10pct | cpp-inplace | 0.295846 | 60000 | 0.000005 |
| 1k | 10pct | cpp-std-sort | 0.306172 | 60000 | 0.000005 |
| 1k | random | franko-inplace | 0.309281 | 60000 | 0.000005 |
| 1k | random | cpp-inplace | 0.292261 | 60000 | 0.000005 |
| 1k | random | cpp-std-sort | 0.329639 | 60000 | 0.000005 |

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
