# In-place quicksort benchmark: Franko vs C++ vs std::sort

**Plain `int64` keys · 5 sizes × 6 distributions · six probe tiers · 2026-11-12**

This report compares in-place sorting only: Franko `-Omax`, a
hand-written C++ `-O3` port of the same algorithm, and C++ `std::sort`
as the reference. Every binary uses the same host compiler and the
`compile.py` flag set, the system allocator, and is left unstripped; on
Linux the C++ runtime is static, so the binary-size comparison is
controlled. The run used `g++ 15.2.0`, OpenJDK `25.0.5`, and one pinned
CPU under WSL2 on an Intel i5-12600K.

Distributions, highest duplicate concentration first:

- **`0-9`:** 10 distinct values.
- **`0.25pct`:** `N / 400` distinct values.
- **`0.5pct`:** `N / 200` distinct values.
- **`1pct`:** `N / 100` distinct values.
- **`10pct`:** `N / 10` distinct values.
- **`random`:** full 63-bit random values, effectively all distinct.

## Short version

- `0-9` wins every size. Franko is **0.654x–0.749x** of `std::sort`;
  the C++ port is **0.676x–0.971x**.
- `1k`: Franko wins five of six datasets (**0.890x–0.976x**); C++ wins
  only `random` (0.950x).
- `10k`: Franko wins four of six (**0.810x–0.920x** on the duplicate
  datasets); C++ wins `10pct` and `random`.
- `99k`: C++ wins four of six; Franko wins `0-9`; `random` goes to
  `std::sort`.
- `1M`: Franko wins only `0-9`; C++ wins only `1pct` by a hair; the
  other four go to `std::sort`.
- `10M`: only `0-9` wins for the custom implementations, with C++
  slightly ahead of Franko; `std::sort` wins the other five.
- All 30 cells agree on `sorted ok` and checksum.
- Binary sizes: Franko **205 KB**, C++ port **162 KB**, `std::sort`
  **154 KB**. All are unstripped and use the system allocator.
- `<4k` uses median-of-3 Hoare. At `4k–100k` a fixed probe selects DNF
  or Lomuto; above `100k` the `m_hat` estimator selects the partition
  kernel.

## 1. Results

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | 0.129415 | **0.128781** | 0.190501 |
| `10M` | `0.25pct` | 0.496381 | 0.473648 | **0.457753** |
| `10M` | `0.5pct` | 0.529675 | 0.508040 | **0.476505** |
| `10M` | `1pct` | 0.563987 | 0.545671 | **0.512050** |
| `10M` | `10pct` | 0.615054 | 0.616998 | **0.603708** |
| `10M` | `random` | 0.645519 | 0.630979 | **0.613843** |
| `1M` | `0-9` | **0.011810** | 0.012141 | 0.016977 |
| `1M` | `0.25pct` | 0.037442 | 0.038089 | **0.036502** |
| `1M` | `0.5pct` | 0.040922 | 0.040494 | **0.039136** |
| `1M` | `1pct` | 0.045192 | **0.042772** | 0.042812 |
| `1M` | `10pct` | 0.051620 | 0.052199 | **0.050722** |
| `1M` | `random` | 0.054124 | 0.053313 | **0.051989** |
| `99k` | `0-9` | **0.001114** | 0.001125 | 0.001487 |
| `99k` | `0.25pct` | 0.002603 | **0.002527** | 0.002687 |
| `99k` | `0.5pct` | 0.002946 | **0.002811** | 0.003005 |
| `99k` | `1pct` | 0.003319 | **0.003197** | 0.003268 |
| `99k` | `10pct` | 0.004108 | **0.004089** | 0.004127 |
| `99k` | `random` | 0.004386 | 0.004290 | **0.004237** |
| `10k` | `0-9` | **0.000085** | 0.000101 | 0.000130 |
| `10k` | `0.25pct` | **0.000144** | 0.000152 | 0.000178 |
| `10k` | `0.5pct` | **0.000184** | 0.000189 | 0.000204 |
| `10k` | `1pct` | **0.000214** | 0.000216 | 0.000232 |
| `10k` | `10pct` | 0.000315690 | **0.000315792** | 0.000324070 |
| `10k` | `random` | 0.000343787 | **0.000330262** | 0.000330824 |
| `1k` | `0-9` | **0.000004627** | 0.000005048 | 0.000005199 |
| `1k` | `0.25pct` | **0.000005419** | 0.000005991 | 0.000005819 |
| `1k` | `0.5pct` | **0.000005877** | 0.000006294 | 0.000006133 |
| `1k` | `1pct` | **0.000005874** | 0.000006217 | 0.000006461 |
| `1k` | `10pct` | **0.000007149** | 0.000007415 | 0.000007446 |
| `1k` | `random` | 0.000006207 | **0.000006042** | 0.000006360 |

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | ---: | ---: |
| `10M` | `0-9` | 0.679x | 0.676x |
| `10M` | `0.25pct` | 1.084x | 1.035x |
| `10M` | `0.5pct` | 1.112x | 1.066x |
| `10M` | `1pct` | 1.101x | 1.066x |
| `10M` | `10pct` | 1.019x | 1.022x |
| `10M` | `random` | 1.052x | 1.028x |
| `1M` | `0-9` | 0.696x | 0.715x |
| `1M` | `0.25pct` | 1.026x | 1.043x |
| `1M` | `0.5pct` | 1.046x | 1.035x |
| `1M` | `1pct` | 1.056x | 0.999x |
| `1M` | `10pct` | 1.018x | 1.029x |
| `1M` | `random` | 1.041x | 1.025x |
| `99k` | `0-9` | 0.749x | 0.757x |
| `99k` | `0.25pct` | 0.968x | 0.941x |
| `99k` | `0.5pct` | 0.980x | 0.935x |
| `99k` | `1pct` | 1.015x | 0.978x |
| `99k` | `10pct` | 0.995x | 0.991x |
| `99k` | `random` | 1.035x | 1.012x |
| `10k` | `0-9` | 0.654x | 0.778x |
| `10k` | `0.25pct` | 0.810x | 0.853x |
| `10k` | `0.5pct` | 0.903x | 0.924x |
| `10k` | `1pct` | 0.920x | 0.930x |
| `10k` | `10pct` | 0.976x | 0.974x |
| `10k` | `random` | 1.039x | 0.998x |
| `1k` | `0-9` | 0.890x | 0.971x |
| `1k` | `0.25pct` | 0.931x | 1.030x |
| `1k` | `0.5pct` | 0.958x | 1.026x |
| `1k` | `1pct` | 0.909x | 0.962x |
| `1k` | `10pct` | 0.960x | 0.996x |
| `1k` | `random` | 0.976x | 0.950x |

A value below `1.00x` is faster than `std::sort`.

### 1.3 Wins by size

| size | Franko | C++ |
| --- | ---: | ---: |
| `10M` | 0/6 | 1/6 |
| `1M` | 1/6 | 1/6 |
| `99k` | 1/6 | 4/6 |
| `10k` | 4/6 | 2/6 |
| `1k` | 5/6 | 1/6 |

Key observations:

- `0-9` is the only dataset that wins at every size for both custom
  implementations.
- `1k` favors Franko: five wins, with the C++ port winning only
  `random`.
- `10k` favors Franko on the duplicate-heavy rows; C++ takes `10pct`
  and `random`.
- `99k` is mixed: C++ wins four rows, Franko takes `0-9`, and
  `std::sort` takes `random`.
- `1M` and `10M` are dominated by `std::sort`; only `0-9` goes to a
  custom implementation, and at `10M` the C++ port edges out Franko.

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
read-only sortedness check, sort, verification, and checksum. Python
times the process wall time and divides by the repetition count.
Random data does not trigger the sortedness short-circuit. The
whole-workload per-key cost includes `O(N)` generation/check/checksum.

Each binary gets one untimed warm-up per size/dataset. Measured mode
order is shuffled per cell with a fixed seed, and the benchmark process
is pinned to CPU 2.

## 3. Per-key nanoseconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | 12.94 | **12.88** | 19.05 |
| `10M` | `0.25pct` | 49.64 | 47.36 | **45.78** |
| `10M` | `0.5pct` | 52.97 | 50.80 | **47.65** |
| `10M` | `1pct` | 56.40 | 54.57 | **51.21** |
| `10M` | `10pct` | 61.51 | 61.70 | **60.37** |
| `10M` | `random` | 64.55 | 63.10 | **61.38** |
| `1M` | `0-9` | **11.81** | 12.14 | 16.98 |
| `1M` | `0.25pct` | 37.44 | 38.09 | **36.50** |
| `1M` | `0.5pct` | 40.92 | 40.49 | **39.14** |
| `1M` | `1pct` | 45.19 | **42.77** | 42.81 |
| `1M` | `10pct` | 51.62 | 52.20 | **50.72** |
| `1M` | `random` | 54.12 | 53.31 | **51.99** |
| `99k` | `0-9` | **11.25** | 11.37 | 15.02 |
| `99k` | `0.25pct` | 26.29 | **25.53** | 27.14 |
| `99k` | `0.5pct` | 29.76 | **28.39** | 30.36 |
| `99k` | `1pct` | 33.53 | **32.29** | 33.01 |
| `99k` | `10pct` | 41.49 | **41.30** | 41.69 |
| `99k` | `random` | 44.31 | 43.33 | **42.80** |
| `10k` | `0-9` | **8.50** | 10.12 | 13.01 |
| `10k` | `0.25pct` | **14.44** | 15.20 | 17.82 |
| `10k` | `0.5pct` | **18.44** | 18.87 | 20.43 |
| `10k` | `1pct` | **21.37** | 21.60 | 23.24 |
| `10k` | `10pct` | 31.62 | **31.58** | 32.41 |
| `10k` | `random` | 34.38 | **33.03** | 33.08 |
| `1k` | `0-9` | **4.63** | 5.05 | 5.20 |
| `1k` | `0.25pct` | **5.42** | 5.99 | 5.82 |
| `1k` | `0.5pct` | **5.88** | 6.29 | 6.13 |
| `1k` | `1pct` | **5.87** | 6.22 | 6.46 |
| `1k` | `10pct` | **7.15** | 7.42 | 7.45 |
| `1k` | `random` | 6.21 | **6.04** | 6.36 |

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

The `<4k` median-of-3 Hoare path gives Franko five wins at 1k:
`0-9` (0.890x), `0.25pct` (0.931x), `0.5pct` (0.958x), `1pct`
(0.909x), and `10pct` (0.960x). The C++ port wins only `random` at
0.950x, while `std::sort` is fastest on none of the six rows.

At this size the per-key cost is close to the timer floor, so the
absolute differences are small; the consistent direction across five
rows is more meaningful than any single 1k number.

### 4.3 Large sizes

At 10M and 1M, `std::sort` wins every row except `0-9`. For duplicate
heavy rows the custom implementations lose 1.02x–1.11x; for `random`
the loss is 1.03x–1.05x. At 1M the C++ port is essentially tied with
`std::sort` on `1pct` (0.999x) and is within 4.3% on the other
non-`0-9` rows.

The `0-9` exception is large and repeatable: both custom
implementations beat `std::sort` by roughly 28–34% at 10M/1M and by
23–25% at 99k, because the DNF partition removes the equal block in one
pass.

### 4.4 Franko versus C++

The two custom implementations are usually close. Largest gaps:

- `10k` `0-9`: Franko is about 16% faster than the C++ port.
- `10k` `0.25pct`: Franko is about 5% faster.
- `99k` `0.25pct` / `0.5pct`: C++ is about 3–5% faster.
- `10M` `0.25pct`: C++ is about 5% faster than Franko.
- `1k` `0.25pct`: Franko is about 10% faster.

On `random` the two implementations are within about 4% at every size.
The remaining gaps are consistent with code layout, call overhead, and
the different amount of generated runtime support, not with a different
asymptotic algorithm.

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
    205 KB, C++ port 162 KB, `std::sort` 154 KB. The Franko binary
    includes the Franko runtime; the C++ binaries include static
    libstdc++/libgcc.

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
| `10M` | `0-9` | franko-inplace | 1.294152 | 10 | 0.129415 |
| `10M` | `0-9` | cpp-inplace | 1.287808 | 10 | 0.128781 |
| `10M` | `0-9` | cpp-std-sort | 1.905005 | 10 | 0.190500 |
| `10M` | `0.25pct` | franko-inplace | 4.963805 | 10 | 0.496381 |
| `10M` | `0.25pct` | cpp-inplace | 4.736482 | 10 | 0.473648 |
| `10M` | `0.25pct` | cpp-std-sort | 4.577530 | 10 | 0.457753 |
| `10M` | `0.5pct` | franko-inplace | 5.296753 | 10 | 0.529675 |
| `10M` | `0.5pct` | cpp-inplace | 5.080397 | 10 | 0.508040 |
| `10M` | `0.5pct` | cpp-std-sort | 4.765048 | 10 | 0.476505 |
| `10M` | `1pct` | franko-inplace | 5.639865 | 10 | 0.563987 |
| `10M` | `1pct` | cpp-inplace | 5.456714 | 10 | 0.545671 |
| `10M` | `1pct` | cpp-std-sort | 5.120505 | 10 | 0.512050 |
| `10M` | `10pct` | franko-inplace | 6.150537 | 10 | 0.615054 |
| `10M` | `10pct` | cpp-inplace | 6.169978 | 10 | 0.616998 |
| `10M` | `10pct` | cpp-std-sort | 6.037080 | 10 | 0.603708 |
| `10M` | `random` | franko-inplace | 6.455192 | 10 | 0.645519 |
| `10M` | `random` | cpp-inplace | 6.309789 | 10 | 0.630979 |
| `10M` | `random` | cpp-std-sort | 6.138431 | 10 | 0.613843 |
| `1M` | `0-9` | franko-inplace | 0.236204 | 20 | 0.011810 |
| `1M` | `0-9` | cpp-inplace | 0.242822 | 20 | 0.012141 |
| `1M` | `0-9` | cpp-std-sort | 0.339531 | 20 | 0.016977 |
| `1M` | `0.25pct` | franko-inplace | 0.748842 | 20 | 0.037442 |
| `1M` | `0.25pct` | cpp-inplace | 0.761784 | 20 | 0.038089 |
| `1M` | `0.25pct` | cpp-std-sort | 0.730039 | 20 | 0.036502 |
| `1M` | `0.5pct` | franko-inplace | 0.818438 | 20 | 0.040922 |
| `1M` | `0.5pct` | cpp-inplace | 0.809885 | 20 | 0.040494 |
| `1M` | `0.5pct` | cpp-std-sort | 0.782729 | 20 | 0.039136 |
| `1M` | `1pct` | franko-inplace | 0.903839 | 20 | 0.045192 |
| `1M` | `1pct` | cpp-inplace | 0.855435 | 20 | 0.042772 |
| `1M` | `1pct` | cpp-std-sort | 0.856233 | 20 | 0.042812 |
| `1M` | `10pct` | franko-inplace | 1.032406 | 20 | 0.051620 |
| `1M` | `10pct` | cpp-inplace | 1.043984 | 20 | 0.052199 |
| `1M` | `10pct` | cpp-std-sort | 1.014436 | 20 | 0.050722 |
| `1M` | `random` | franko-inplace | 1.082485 | 20 | 0.054124 |
| `1M` | `random` | cpp-inplace | 1.066269 | 20 | 0.053313 |
| `1M` | `random` | cpp-std-sort | 1.039784 | 20 | 0.051989 |
| `99k` | `0-9` | franko-inplace | 0.668364 | 600 | 0.001114 |
| `99k` | `0-9` | cpp-inplace | 0.675257 | 600 | 0.001125 |
| `99k` | `0-9` | cpp-std-sort | 0.892084 | 600 | 0.001487 |
| `99k` | `0.25pct` | franko-inplace | 1.561510 | 600 | 0.002603 |
| `99k` | `0.25pct` | cpp-inplace | 1.516490 | 600 | 0.002527 |
| `99k` | `0.25pct` | cpp-std-sort | 1.612387 | 600 | 0.002687 |
| `99k` | `0.5pct` | franko-inplace | 1.767461 | 600 | 0.002946 |
| `99k` | `0.5pct` | cpp-inplace | 1.686424 | 600 | 0.002811 |
| `99k` | `0.5pct` | cpp-std-sort | 1.803172 | 600 | 0.003005 |
| `99k` | `1pct` | franko-inplace | 1.991399 | 600 | 0.003319 |
| `99k` | `1pct` | cpp-inplace | 1.918136 | 600 | 0.003197 |
| `99k` | `1pct` | cpp-std-sort | 1.961054 | 600 | 0.003268 |
| `99k` | `10pct` | franko-inplace | 2.464561 | 600 | 0.004108 |
| `99k` | `10pct` | cpp-inplace | 2.453381 | 600 | 0.004089 |
| `99k` | `10pct` | cpp-std-sort | 2.476284 | 600 | 0.004127 |
| `99k` | `random` | franko-inplace | 2.631740 | 600 | 0.004386 |
| `99k` | `random` | cpp-inplace | 2.573982 | 600 | 0.004290 |
| `99k` | `random` | cpp-std-sort | 2.542377 | 600 | 0.004237 |
| `10k` | `0-9` | franko-inplace | 0.510178 | 6000 | 0.000085 |
| `10k` | `0-9` | cpp-inplace | 0.607177 | 6000 | 0.000101 |
| `10k` | `0-9` | cpp-std-sort | 0.780535 | 6000 | 0.000130 |
| `10k` | `0.25pct` | franko-inplace | 0.866320 | 6000 | 0.000144 |
| `10k` | `0.25pct` | cpp-inplace | 0.911770 | 6000 | 0.000152 |
| `10k` | `0.25pct` | cpp-std-sort | 1.069481 | 6000 | 0.000178 |
| `10k` | `0.5pct` | franko-inplace | 1.106586 | 6000 | 0.000184 |
| `10k` | `0.5pct` | cpp-inplace | 1.132303 | 6000 | 0.000189 |
| `10k` | `0.5pct` | cpp-std-sort | 1.225958 | 6000 | 0.000204 |
| `10k` | `1pct` | franko-inplace | 1.282329 | 6000 | 0.000214 |
| `10k` | `1pct` | cpp-inplace | 1.295985 | 6000 | 0.000216 |
| `10k` | `1pct` | cpp-std-sort | 1.394168 | 6000 | 0.000232 |
| `10k` | `10pct` | franko-inplace | 1.897140 | 6000 | 0.000316 |
| `10k` | `10pct` | cpp-inplace | 1.894752 | 6000 | 0.000316 |
| `10k` | `10pct` | cpp-std-sort | 1.944421 | 6000 | 0.000324 |
| `10k` | `random` | franko-inplace | 2.062720 | 6000 | 0.000344 |
| `10k` | `random` | cpp-inplace | 1.981572 | 6000 | 0.000330 |
| `10k` | `random` | cpp-std-sort | 1.984945 | 6000 | 0.000331 |
| `1k` | `0-9` | franko-inplace | 0.277607 | 60000 | 0.000005 |
| `1k` | `0-9` | cpp-inplace | 0.302880 | 60000 | 0.000005 |
| `1k` | `0-9` | cpp-std-sort | 0.311933 | 60000 | 0.000005 |
| `1k` | `0.25pct` | franko-inplace | 0.325154 | 60000 | 0.000005 |
| `1k` | `0.25pct` | cpp-inplace | 0.359447 | 60000 | 0.000006 |
| `1k` | `0.25pct` | cpp-std-sort | 0.349146 | 60000 | 0.000006 |
| `1k` | `0.5pct` | franko-inplace | 0.352649 | 60000 | 0.000006 |
| `1k` | `0.5pct` | cpp-inplace | 0.377619 | 60000 | 0.000006 |
| `1k` | `0.5pct` | cpp-std-sort | 0.367955 | 60000 | 0.000006 |
| `1k` | `1pct` | franko-inplace | 0.352428 | 60000 | 0.000006 |
| `1k` | `1pct` | cpp-inplace | 0.372996 | 60000 | 0.000006 |
| `1k` | `1pct` | cpp-std-sort | 0.387674 | 60000 | 0.000006 |
| `1k` | `10pct` | franko-inplace | 0.428965 | 60000 | 0.000007 |
| `1k` | `10pct` | cpp-inplace | 0.444928 | 60000 | 0.000007 |
| `1k` | `10pct` | cpp-std-sort | 0.446773 | 60000 | 0.000007 |
| `1k` | `random` | franko-inplace | 0.372440 | 60000 | 0.000006 |
| `1k` | `random` | cpp-inplace | 0.362502 | 60000 | 0.000006 |
| `1k` | `random` | cpp-std-sort | 0.381607 | 60000 | 0.000006 |

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
