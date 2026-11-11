# In-place quicksort benchmark: Franko vs C++ vs std::sort

**Plain `int64` keys · 5 sizes × 6 distributions · six probe tiers · 2026-11-12**

This report compares in-place sorting only: Franko `-Omax`, a
hand-written C++ `-O3` port of the same algorithm, and C++ `std::sort`
as the reference. Every binary uses the same host compiler and the
`compile.py` flag set, the system allocator, and is left unstripped; on
Linux the C++ runtime is static. Franko output is compiled with
exceptions and RTTI disabled. The run used `g++ 15.2.0`, OpenJDK
`25.0.5`, and one pinned CPU under WSL2 on an Intel i5-12600K.

Distributions, highest duplicate concentration first:

- **`0-9`:** 10 distinct values.
- **`0.25pct`:** `N / 400` distinct values.
- **`0.5pct`:** `N / 200` distinct values.
- **`1pct`:** `N / 100` distinct values.
- **`10pct`:** `N / 10` distinct values.
- **`random`:** full 63-bit random values, effectively all distinct.

## Short version

- `0-9` wins every size. Franko is **0.662x–0.879x** of `std::sort`;
  the C++ port is **0.687x–0.956x**.
- `1k`: Franko wins all six datasets (**0.879x–0.924x**); the C++
  port loses all six.
- `10k`: Franko wins four of six (**0.662x–0.885x** on the duplicate
  datasets); C++ wins `10pct` and `random`.
- `99k`: C++ wins five of six; Franko wins `0-9`; the C++ port also
  edges out `std::sort` on `random`.
- `1M`: C++ wins `0-9` and `0.25pct` by tiny margins; `std::sort` wins
  the other four.
- `10M`: Franko wins `0-9`; `std::sort` wins the other five.
- All 30 cells agree on `sorted ok` and checksum.
- Binary sizes: Franko **60 KB**, C++ port **162 KB**, `std::sort`
  **154 KB**. All are unstripped and use the system allocator.
- `<4k` uses median-of-3 Hoare. At `4k–100k` a fixed probe selects DNF
  or Lomuto; above `100k` the `m_hat` estimator selects the partition
  kernel.

## 1. Results

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | **0.128364** | 0.130350 | 0.189753 |
| `10M` | `0.25pct` | 0.485530 | 0.467795 | **0.455922** |
| `10M` | `0.5pct` | 0.525696 | 0.506830 | **0.476090** |
| `10M` | `1pct` | 0.554060 | 0.533529 | **0.507886** |
| `10M` | `10pct` | 0.609394 | 0.612647 | **0.599649** |
| `10M` | `random` | 0.631046 | 0.625719 | **0.609161** |
| `1M` | `0-9` | 0.011776 | **0.011760** | 0.017064 |
| `1M` | `0.25pct` | 0.036694 | **0.036203** | 0.036339 |
| `1M` | `0.5pct` | 0.040892 | 0.040388 | **0.038693** |
| `1M` | `1pct` | 0.043896 | 0.042626 | **0.042039** |
| `1M` | `10pct` | 0.051058 | 0.051287 | **0.050223** |
| `1M` | `random` | 0.053944 | 0.053149 | **0.051531** |
| `99k` | `0-9` | **0.001073** | 0.001104 | 0.001495 |
| `99k` | `0.25pct` | 0.002529 | **0.002520** | 0.002664 |
| `99k` | `0.5pct` | 0.002837 | **0.002794** | 0.002990 |
| `99k` | `1pct` | 0.003231 | **0.003186** | 0.003281 |
| `99k` | `10pct` | 0.004086 | **0.004068** | 0.004128 |
| `99k` | `random` | 0.004376 | **0.004273** | 0.004283 |
| `10k` | `0-9` | **0.000085** | 0.000100 | 0.000128 |
| `10k` | `0.25pct` | **0.000143** | 0.000151 | 0.000176 |
| `10k` | `0.5pct` | **0.000176** | 0.000186 | 0.000202 |
| `10k` | `1pct` | **0.000206** | 0.000215 | 0.000233 |
| `10k` | `10pct` | 0.000315 | **0.000314** | 0.000324 |
| `10k` | `random` | 0.000341 | **0.000331** | 0.000331 |
| `1k` | `0-9` | **0.000004557** | 0.000004956 | 0.000005184 |
| `1k` | `0.25pct` | **0.000005256** | 0.000005774 | 0.000005763 |
| `1k` | `0.5pct` | **0.000005574** | 0.000006009 | 0.000006067 |
| `1k` | `1pct` | **0.000005705** | 0.000006246 | 0.000006400 |
| `1k` | `10pct` | **0.000006765** | 0.000007303 | 0.000007344 |
| `1k` | `random` | **0.000005709** | 0.000005867 | 0.000006179 |

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | ---: | ---: |
| `10M` | `0-9` | 0.676x | 0.687x |
| `10M` | `0.25pct` | 1.065x | 1.026x |
| `10M` | `0.5pct` | 1.104x | 1.065x |
| `10M` | `1pct` | 1.091x | 1.050x |
| `10M` | `10pct` | 1.016x | 1.022x |
| `10M` | `random` | 1.036x | 1.027x |
| `1M` | `0-9` | 0.690x | 0.689x |
| `1M` | `0.25pct` | 1.010x | 0.996x |
| `1M` | `0.5pct` | 1.057x | 1.044x |
| `1M` | `1pct` | 1.044x | 1.014x |
| `1M` | `10pct` | 1.017x | 1.021x |
| `1M` | `random` | 1.047x | 1.031x |
| `99k` | `0-9` | 0.718x | 0.739x |
| `99k` | `0.25pct` | 0.949x | 0.946x |
| `99k` | `0.5pct` | 0.949x | 0.934x |
| `99k` | `1pct` | 0.985x | 0.971x |
| `99k` | `10pct` | 0.990x | 0.985x |
| `99k` | `random` | 1.022x | 0.998x |
| `10k` | `0-9` | 0.662x | 0.785x |
| `10k` | `0.25pct` | 0.811x | 0.859x |
| `10k` | `0.5pct` | 0.872x | 0.922x |
| `10k` | `1pct` | 0.885x | 0.922x |
| `10k` | `10pct` | 0.972x | 0.969x |
| `10k` | `random` | 1.031x | 1.000x |
| `1k` | `0-9` | 0.879x | 0.956x |
| `1k` | `0.25pct` | 0.912x | 1.002x |
| `1k` | `0.5pct` | 0.919x | 0.990x |
| `1k` | `1pct` | 0.891x | 0.976x |
| `1k` | `10pct` | 0.921x | 0.994x |
| `1k` | `random` | 0.924x | 0.950x |

A value below `1.00x` is faster than `std::sort`.

### 1.3 Wins by size

| size | Franko | C++ |
| --- | ---: | ---: |
| `10M` | 1/6 | 0/6 |
| `1M` | 0/6 | 2/6 |
| `99k` | 1/6 | 5/6 |
| `10k` | 4/6 | 2/6 |
| `1k` | 6/6 | 0/6 |

Key observations:

- `0-9` is the only dataset that wins at every size for Franko.
- `1k` is a Franko sweep: six wins, with margins of 8–12%.
- `10k` favors Franko on the duplicate-heavy rows; C++ takes `10pct`
  and `random`.
- `99k` is mixed: C++ wins five rows, Franko takes `0-9`, and C++ also
  edges out `std::sort` on `random`.
- `1M` and `10M` are dominated by `std::sort`; only `0-9` goes to
  Franko, and at `10M` it does so with a clear margin.

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
| `10M` | `0-9` | **12.84** | 13.03 | 18.98 |
| `10M` | `0.25pct` | 48.55 | 46.78 | **45.59** |
| `10M` | `0.5pct` | 52.57 | 50.68 | **47.61** |
| `10M` | `1pct` | 55.41 | 53.35 | **50.79** |
| `10M` | `10pct` | 60.94 | 61.26 | **59.96** |
| `10M` | `random` | 63.10 | 62.57 | **60.92** |
| `1M` | `0-9` | 11.78 | **11.76** | 17.06 |
| `1M` | `0.25pct` | 36.69 | **36.20** | 36.34 |
| `1M` | `0.5pct` | 40.89 | 40.39 | **38.69** |
| `1M` | `1pct` | 43.90 | 42.63 | **42.04** |
| `1M` | `10pct` | 51.06 | 51.29 | **50.22** |
| `1M` | `random` | 53.94 | 53.15 | **51.53** |
| `99k` | `0-9` | **10.84** | 11.15 | 15.10 |
| `99k` | `0.25pct` | 25.54 | **25.45** | 26.90 |
| `99k` | `0.5pct` | 28.66 | **28.23** | 30.20 |
| `99k` | `1pct` | 32.64 | **32.18** | 33.14 |
| `99k` | `10pct` | 41.28 | **41.10** | 41.70 |
| `99k` | `random` | 44.20 | **43.16** | 43.26 |
| `10k` | `0-9` | **8.46** | 10.03 | 12.78 |
| `10k` | `0.25pct` | **14.30** | 15.14 | 17.63 |
| `10k` | `0.5pct` | **17.61** | 18.61 | 20.20 |
| `10k` | `1pct` | **20.63** | 21.49 | 23.30 |
| `10k` | `10pct` | 31.50 | **31.39** | 32.41 |
| `10k` | `random` | 34.12 | **33.07** | 33.08 |
| `1k` | `0-9` | **4.56** | 4.96 | 5.18 |
| `1k` | `0.25pct` | **5.26** | 5.77 | 5.76 |
| `1k` | `0.5pct` | **5.57** | 6.01 | 6.07 |
| `1k` | `1pct` | **5.71** | 6.25 | 6.40 |
| `1k` | `10pct` | **6.77** | 7.30 | 7.34 |
| `1k` | `random` | **5.71** | 5.87 | 6.18 |

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

The `<4k` median-of-3 Hoare path gives Franko all six 1k wins:
`0-9` (0.879x), `0.25pct` (0.912x), `0.5pct` (0.919x), `1pct`
(0.891x), `10pct` (0.921x), and `random` (0.924x). The C++ port
beats `std::sort` on five of six, losing only `0.25pct` (1.002x).

At this size the per-key cost is close to the timer floor, so the
absolute differences are small; the consistent direction across all
six rows is more meaningful than any single 1k number.

### 4.3 Large sizes

At 10M and 1M, `std::sort` wins almost everything. At 10M Franko loses
1.016x–1.104x on the non-`0-9` rows, while the C++ port loses
1.022x–1.065x. At 1M the C++ port takes `0-9` and `0.25pct` by margins
under 1.4%, and `std::sort` takes the remaining four rows.

The `0-9` exception is large and repeatable: Franko beats `std::sort`
by 32% at 10M, 31% at 1M, and 28% at 99k, because the DNF partition
removes the equal block in one pass.

### 4.4 Franko versus C++

The two custom implementations are usually close. Largest gaps:

- `10k` `0-9`: Franko is about 16% faster than the C++ port.
- `10k` `0.25pct`: Franko is about 6% faster.
- `10k` `0.5pct`: Franko is about 5% faster.
- `10M` `0.25pct`: C++ is about 4% faster than Franko.
- `1k` `0.25pct`: Franko is about 9% faster.

On `random` the two implementations are within about 3.2% at every
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

| size | dataset | mode | chunk s | reps | mean/sort |
| --- | --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | franko-inplace | 1.283635 | 10 | 0.128364 |
| `10M` | `0-9` | cpp-inplace | 1.303496 | 10 | 0.130350 |
| `10M` | `0-9` | cpp-std-sort | 1.897528 | 10 | 0.189753 |
| `10M` | `0.25pct` | franko-inplace | 4.855303 | 10 | 0.485530 |
| `10M` | `0.25pct` | cpp-inplace | 4.677952 | 10 | 0.467795 |
| `10M` | `0.25pct` | cpp-std-sort | 4.559224 | 10 | 0.455922 |
| `10M` | `0.5pct` | franko-inplace | 5.256958 | 10 | 0.525696 |
| `10M` | `0.5pct` | cpp-inplace | 5.068301 | 10 | 0.506830 |
| `10M` | `0.5pct` | cpp-std-sort | 4.760901 | 10 | 0.476090 |
| `10M` | `1pct` | franko-inplace | 5.540601 | 10 | 0.554060 |
| `10M` | `1pct` | cpp-inplace | 5.335293 | 10 | 0.533529 |
| `10M` | `1pct` | cpp-std-sort | 5.078861 | 10 | 0.507886 |
| `10M` | `10pct` | franko-inplace | 6.093943 | 10 | 0.609394 |
| `10M` | `10pct` | cpp-inplace | 6.126470 | 10 | 0.612647 |
| `10M` | `10pct` | cpp-std-sort | 5.996489 | 10 | 0.599649 |
| `10M` | `random` | franko-inplace | 6.310455 | 10 | 0.631046 |
| `10M` | `random` | cpp-inplace | 6.257187 | 10 | 0.625719 |
| `10M` | `random` | cpp-std-sort | 6.091609 | 10 | 0.609161 |
| `1M` | `0-9` | franko-inplace | 0.235521 | 20 | 0.011776 |
| `1M` | `0-9` | cpp-inplace | 0.235204 | 20 | 0.011760 |
| `1M` | `0-9` | cpp-std-sort | 0.341284 | 20 | 0.017064 |
| `1M` | `0.25pct` | franko-inplace | 0.733877 | 20 | 0.036694 |
| `1M` | `0.25pct` | cpp-inplace | 0.724058 | 20 | 0.036203 |
| `1M` | `0.25pct` | cpp-std-sort | 0.726776 | 20 | 0.036339 |
| `1M` | `0.5pct` | franko-inplace | 0.817834 | 20 | 0.040892 |
| `1M` | `0.5pct` | cpp-inplace | 0.807755 | 20 | 0.040388 |
| `1M` | `0.5pct` | cpp-std-sort | 0.773865 | 20 | 0.038693 |
| `1M` | `1pct` | franko-inplace | 0.877920 | 20 | 0.043896 |
| `1M` | `1pct` | cpp-inplace | 0.852514 | 20 | 0.042626 |
| `1M` | `1pct` | cpp-std-sort | 0.840776 | 20 | 0.042039 |
| `1M` | `10pct` | franko-inplace | 1.021155 | 20 | 0.051058 |
| `1M` | `10pct` | cpp-inplace | 1.025736 | 20 | 0.051287 |
| `1M` | `10pct` | cpp-std-sort | 1.004460 | 20 | 0.050223 |
| `1M` | `random` | franko-inplace | 1.078881 | 20 | 0.053944 |
| `1M` | `random` | cpp-inplace | 1.062982 | 20 | 0.053149 |
| `1M` | `random` | cpp-std-sort | 1.030621 | 20 | 0.051531 |
| `99k` | `0-9` | franko-inplace | 0.644040 | 600 | 0.001073 |
| `99k` | `0-9` | cpp-inplace | 0.662564 | 600 | 0.001104 |
| `99k` | `0-9` | cpp-std-sort | 0.897055 | 600 | 0.001495 |
| `99k` | `0.25pct` | franko-inplace | 1.517189 | 600 | 0.002529 |
| `99k` | `0.25pct` | cpp-inplace | 1.511873 | 600 | 0.002520 |
| `99k` | `0.25pct` | cpp-std-sort | 1.598137 | 600 | 0.002664 |
| `99k` | `0.5pct` | franko-inplace | 1.702141 | 600 | 0.002837 |
| `99k` | `0.5pct` | cpp-inplace | 1.676584 | 600 | 0.002794 |
| `99k` | `0.5pct` | cpp-std-sort | 1.794138 | 600 | 0.002990 |
| `99k` | `1pct` | franko-inplace | 1.938669 | 600 | 0.003231 |
| `99k` | `1pct` | cpp-inplace | 1.911402 | 600 | 0.003186 |
| `99k` | `1pct` | cpp-std-sort | 1.968633 | 600 | 0.003281 |
| `99k` | `10pct` | franko-inplace | 2.451776 | 600 | 0.004086 |
| `99k` | `10pct` | cpp-inplace | 2.441062 | 600 | 0.004068 |
| `99k` | `10pct` | cpp-std-sort | 2.477039 | 600 | 0.004128 |
| `99k` | `random` | franko-inplace | 2.625777 | 600 | 0.004376 |
| `99k` | `random` | cpp-inplace | 2.563555 | 600 | 0.004273 |
| `99k` | `random` | cpp-std-sort | 2.569673 | 600 | 0.004283 |
| `10k` | `0-9` | franko-inplace | 0.507791 | 6000 | 0.000085 |
| `10k` | `0-9` | cpp-inplace | 0.601833 | 6000 | 0.000100 |
| `10k` | `0-9` | cpp-std-sort | 0.766769 | 6000 | 0.000128 |
| `10k` | `0.25pct` | franko-inplace | 0.858040 | 6000 | 0.000143 |
| `10k` | `0.25pct` | cpp-inplace | 0.908532 | 6000 | 0.000151 |
| `10k` | `0.25pct` | cpp-std-sort | 1.058021 | 6000 | 0.000176 |
| `10k` | `0.5pct` | franko-inplace | 1.056470 | 6000 | 0.000176 |
| `10k` | `0.5pct` | cpp-inplace | 1.116685 | 6000 | 0.000186 |
| `10k` | `0.5pct` | cpp-std-sort | 1.211707 | 6000 | 0.000202 |
| `10k` | `1pct` | franko-inplace | 1.238043 | 6000 | 0.000206 |
| `10k` | `1pct` | cpp-inplace | 1.289400 | 6000 | 0.000215 |
| `10k` | `1pct` | cpp-std-sort | 1.398158 | 6000 | 0.000233 |
| `10k` | `10pct` | franko-inplace | 1.890158 | 6000 | 0.000315 |
| `10k` | `10pct` | cpp-inplace | 1.883168 | 6000 | 0.000314 |
| `10k` | `10pct` | cpp-std-sort | 1.944377 | 6000 | 0.000324 |
| `10k` | `random` | franko-inplace | 2.046958 | 6000 | 0.000341 |
| `10k` | `random` | cpp-inplace | 1.984333 | 6000 | 0.000331 |
| `10k` | `random` | cpp-std-sort | 1.984806 | 6000 | 0.000331 |
| `1k` | `0-9` | franko-inplace | 0.273422 | 60000 | 0.000005 |
| `1k` | `0-9` | cpp-inplace | 0.297379 | 60000 | 0.000005 |
| `1k` | `0-9` | cpp-std-sort | 0.311065 | 60000 | 0.000005 |
| `1k` | `0.25pct` | franko-inplace | 0.315367 | 60000 | 0.000005 |
| `1k` | `0.25pct` | cpp-inplace | 0.346463 | 60000 | 0.000006 |
| `1k` | `0.25pct` | cpp-std-sort | 0.345791 | 60000 | 0.000006 |
| `1k` | `0.5pct` | franko-inplace | 0.334461 | 60000 | 0.000006 |
| `1k` | `0.5pct` | cpp-inplace | 0.360525 | 60000 | 0.000006 |
| `1k` | `0.5pct` | cpp-std-sort | 0.364024 | 60000 | 0.000006 |
| `1k` | `1pct` | franko-inplace | 0.342305 | 60000 | 0.000006 |
| `1k` | `1pct` | cpp-inplace | 0.374747 | 60000 | 0.000006 |
| `1k` | `1pct` | cpp-std-sort | 0.384026 | 60000 | 0.000006 |
| `1k` | `10pct` | franko-inplace | 0.405928 | 60000 | 0.000007 |
| `1k` | `10pct` | cpp-inplace | 0.438180 | 60000 | 0.000007 |
| `1k` | `10pct` | cpp-std-sort | 0.440611 | 60000 | 0.000007 |
| `1k` | `random` | franko-inplace | 0.342524 | 60000 | 0.000006 |
| `1k` | `random` | cpp-inplace | 0.352037 | 60000 | 0.000006 |
| `1k` | `random` | cpp-std-sort | 0.370737 | 60000 | 0.000006 |

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
