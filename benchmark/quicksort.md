# In-place quicksort benchmark: Franko vs C++ vs std::sort

**Plain `int64` keys · 4 sizes × 4 distributions · 10M: 5 runs, 1M: 10 runs, 100k: 100 per chunk, 10k: 1000 per chunk · 2026-11-08**

This report compares in-place sorting only: Franko `-Omax`, a
hand-written C++ `-O3` port of the same algorithm, and C++ `std::sort`
as the reference.

The four distributions are:

- **`0-9`:** values uniform in `0..9`, i.e. 10 distinct values.
- **`1pct`:** values modulo 100,000.
- **`10pct`:** values modulo 1,000,000.
- **`random`:** full 63-bit random values, effectively all distinct.

The four sizes are **10M, 1M, 100k, and 10k** keys, giving a 16-cell
matrix for each implementation.

The short version:

- At **10M**, the adaptive DNF path makes Franko and C++ in-place
  **0.64x–0.65x** of `std::sort` on `0-9` — about **1.5x faster**. On
  `1pct`, `10pct`, and `random`, Franko is 1.14x, 1.01x, and 1.02x of
  `std::sort`; C++ is 1.05x, 1.02x, and 1.01x.
- At **1M**, `0-9` remains **0.70x–0.71x** of `std::sort`; the other
  three datasets are within about 1.5%.
- At **100k and 10k**, the `0-9` ordering flips: the implementations
  are **1.12x–1.38x slower** than `std::sort`, because `N <= 100000`
  always uses Lomuto and the DNF path is not selected. High-cardinality
  datasets are at parity or slightly faster.
- Per-key cost falls sharply with size because the data fits in smaller
  caches: `random` costs about **64 ns/key at 10M**, **53–54 ns/key at
  1M**, **42–43 ns/key at 100k**, and **32–34 ns/key at 10k**.
- Franko and hand-written C++ stay within about **9%** of each other.

---

## 1. Bottom line

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | 0.137853 | **0.134501** | 0.211242 |
| `10M` | `1pct` | 0.588427 | 0.544909 | **0.517123** |
| `10M` | `10pct` | 0.618550 | 0.623798 | **0.611243** |
| `10M` | `random` | 0.640923 | 0.634834 | **0.628468** |
| `1M` | `0-9` | **0.013500** | 0.013717 | 0.019308 |
| `1M` | `1pct` | 0.051867 | **0.051597** | 0.051973 |
| `1M` | `10pct` | 0.055014 | **0.054073** | 0.054890 |
| `1M` | `random` | 0.054205 | 0.053532 | **0.053411** |
| `100k` | `0-9` | 0.001854 | 0.001732 | **0.001553** |
| `100k` | `1pct` | 0.004328 | **0.004259** | 0.004382 |
| `100k` | `10pct` | 0.004226 | **0.004119** | 0.004234 |
| `100k` | `random` | 0.004333 | **0.004220** | 0.004285 |
| `10k` | `0-9` | 0.000173 | 0.000159 | **0.000126** |
| `10k` | `1pct` | 0.000325 | **0.000314** | 0.000353 |
| `10k` | `10pct` | 0.000334 | **0.000326** | 0.000333 |
| `10k` | `random` | 0.000325 | **0.000322** | 0.000336 |

For 100k and 10k, the per-sort mean is the chunk wall time divided by
100 or 1000 sorts respectively. The chunk includes data generation,
sorting, verification, and checksum for every sort.

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | ---: | ---: |
| `10M` | `0-9` | **0.653x** | **0.637x** |
| `10M` | `1pct` | 1.138x | 1.054x |
| `10M` | `10pct` | 1.012x | 1.021x |
| `10M` | `random` | 1.020x | 1.010x |
| `1M` | `0-9` | **0.699x** | **0.710x** |
| `1M` | `1pct` | 0.998x | **0.993x** |
| `1M` | `10pct` | 1.002x | **0.985x** |
| `1M` | `random` | 1.015x | 1.002x |
| `100k` | `0-9` | 1.194x | 1.116x |
| `100k` | `1pct` | 0.988x | **0.972x** |
| `100k` | `10pct` | 0.998x | **0.973x** |
| `100k` | `random` | 1.011x | **0.985x** |
| `10k` | `0-9` | 1.378x | 1.269x |
| `10k` | `1pct` | **0.921x** | **0.890x** |
| `10k` | `10pct` | 1.001x | **0.979x** |
| `10k` | `random` | **0.965x** | **0.957x** |

A value below `1.00x` means the implementation is faster than
`std::sort`.

Key points:

- **DNF is the large-N duplicate-heavy win.** On `0-9` at 10M and 1M,
  Franko and C++ in-place are 1.4–1.6x faster than `std::sort`.
- **The 100k threshold is visible.** At 100k and 10k, `N <= 100000`
  forces Lomuto, so `0-9` no longer benefits from DNF and becomes
  1.12–1.38x slower than `std::sort`.
- **High-cardinality data is close to `std::sort`.** The largest loss
  is Franko on `1pct` at 10M (1.14x); most other high-cardinality cells
  are within 1–2%, and several small-size cells are slightly faster.
- **Cache effects dominate scaling.** The same `random` workload costs
  about 64 ns/key at 10M but 32–34 ns/key at 10k.
- **Franko vs hand-written C++** is within about 9%; C++ is usually
  slightly ahead on the small `0-9` cells.

---

## 2. What was measured

### 2.1 Sizes and datasets

All programs generate the same splitmix64 stream:

```text
state = 0x9E3779B97F4A7C15
repeat:
    state += 0x9E3779B97F4A7C15
    z = state
    z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9
    z = (z ^ (z >> 27)) * 0x94D049BB133111EB
    z = z ^ (z >> 31)
```

The dataset selector then maps `z` to the requested distribution:

| dataset | value |
| --- | --- |
| `0-9` | `z % 10` |
| `1pct` | `z % 100000` |
| `10pct` | `z % 1000000` |
| `random` | `z & 0x7FFFFFFFFFFFFFFF` |

Sizes:

| size | keys |
| --- | ---: |
| `10M` | 10,000,000 |
| `1M` | 1,000,000 |
| `100k` | 100,000 |
| `10k` | 10,000 |

Each mode sorts, verifies, and computes a checksum. Timing is
whole-process wall clock, so generation, sorting, verification, and
checksum are all included.

### 2.2 Implementations

| Program | What it is |
| --- | --- |
| `quicksort_inplace_franko.fr` | Franko `-Omax`, in-place value sort |
| `quicksort_inplace_cpp.cpp` | Hand-written C++ `-O3` port, in-place value sort |
| `quicksort_std_cpp.cpp` | C++ `-O3`, `std::sort` reference |

The in-place programs compare and swap the `int64` values directly.
Only these three builds are covered.

### 2.3 Algorithm

Franko and the hand-written C++ port use the same adaptive algorithm:

- **Random pivot table:** 128 hardcoded 32-bit values in `RANDOM_PIVOTS`.
  A global `pivot_cursor` advances once per partition.
- **Division-free pivot selection:** `(sample * count) >> 32`.
- **Read-only sortedness preflight:** one scan checks whether the range
  is already sorted; if so, the sort is skipped.
- **Small-input rule:** when `N <= 100000`, the sort always uses Lomuto.
  Lomuto receives a depth budget of `2 * floor(log2(N))`; if the budget
  is exhausted, the remaining range is finished with heapsort.
- **Large-input adaptive choice:** when `N > 100000`, a stratified sample
  of `k = N / 1000` indices is sorted and the average duplicate-group
  size is estimated. If `m_hat > 55`, the sort uses a Dutch national
  flag partition; otherwise it uses Lomuto.
- **Fixed small-subproblem cutoff:** all recursion stops at 32 elements.
  Sizes 2–8 use sorting networks; sizes 9–32 use insertion sort.
  The heapsort fallback also dispatches ranges of at most 32 elements to
  the same base case.
- **Iterative control flow:** the larger partition stays in a while loop
  and only the smaller side recurses, bounding recursion depth by
  O(log N).
- **Comparison discipline:** all element comparisons use only `<`.

The `N <= 100000` rule is important for this report: it applies to the
100k and 10k rows, so `use_dnf` is always 0 there. The estimator and DNF
are only used in the 10M and 1M rows.

### 2.4 Timing methodology

The harness is `benchmark/quicksort_benchmark.py`:

```bash
uv run python benchmark/quicksort_benchmark.py --cpu 2
```

- **10M:** 5 measured runs per mode and dataset.
- **1M:** 10 measured runs per mode and dataset.
- **100k:** 100 sorts inside one process. Python times the whole chunk
  and divides by 100.
- **10k:** 1000 sorts inside one process. Python times the whole chunk
  and divides by 1000.
- One untimed warm-up per binary, size, and dataset.
- Each process is pinned to CPU 2.

For 100k and 10k, the binary accepts a repeat count and loops
generation + sort + verification + checksum internally. This pays
process startup once per cell instead of once per sort. The reported
mean is still a per-sort mean.

This run used `g++ 15.2.0` under WSL2 on a 12th-gen Intel i5-12600K.
Absolute times are host-sensitive.

---

## 3. Results

### 3.1 Per-key nanoseconds

Dividing the per-sort mean by the key count shows the cache effect.
These are whole-workload costs (generation + sort + verification +
checksum), not pure sort time:

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | 13.79 | **13.45** | 21.12 |
| `10M` | `1pct` | 58.84 | 54.49 | **51.71** |
| `10M` | `10pct` | 61.85 | 62.38 | **61.12** |
| `10M` | `random` | 64.09 | 63.48 | **62.85** |
| `1M` | `0-9` | **13.50** | 13.72 | 19.31 |
| `1M` | `1pct` | 51.87 | **51.60** | 51.97 |
| `1M` | `10pct` | 55.01 | **54.07** | 54.89 |
| `1M` | `random` | 54.20 | 53.53 | **53.41** |
| `100k` | `0-9` | 18.54 | 17.32 | **15.53** |
| `100k` | `1pct` | 43.28 | **42.59** | 43.82 |
| `100k` | `10pct` | 42.26 | **41.19** | 42.34 |
| `100k` | `random` | 43.33 | **42.20** | 42.85 |
| `10k` | `0-9` | 17.30 | 15.90 | **12.60** |
| `10k` | `1pct` | 32.50 | **31.40** | 35.30 |
| `10k` | `10pct` | 33.40 | **32.60** | 33.30 |
| `10k` | `random` | 32.50 | **32.20** | 33.60 |

The `0-9` row is the exception: at 10M/1M, DNF removes equal keys in
bulk and keeps the per-key cost near 13.5–13.8 ns even though the data
does not fit in cache. At 100k/10k, Lomuto handles the duplicate groups
one pivot at a time, and the per-key cost rises to 15.5–18.5 ns.

### 3.2 Detailed statistics for individual runs

Only 10M and 1M have multiple independent process-level samples; 100k
and 10k are single chunk measurements.

| size | dataset | mode | mean | median | min | max | SD | CV |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `10M` | `0-9` | franko-inplace | 0.137853 | 0.138169 | 0.135475 | 0.140673 | 0.001944 | 1.4% |
| `10M` | `0-9` | cpp-inplace | 0.134501 | 0.134771 | 0.132598 | 0.136358 | 0.001400 | 1.0% |
| `10M` | `0-9` | cpp-std-sort | 0.211242 | 0.210184 | 0.207932 | 0.216669 | 0.003356 | 1.6% |
| `10M` | `1pct` | franko-inplace | 0.588427 | 0.588492 | 0.584141 | 0.592988 | 0.003271 | 0.6% |
| `10M` | `1pct` | cpp-inplace | 0.544909 | 0.546118 | 0.536879 | 0.548504 | 0.004602 | 0.8% |
| `10M` | `1pct` | cpp-std-sort | 0.517123 | 0.514960 | 0.506593 | 0.533429 | 0.010199 | 2.0% |
| `10M` | `10pct` | franko-inplace | 0.618550 | 0.617170 | 0.612019 | 0.630201 | 0.006995 | 1.1% |
| `10M` | `10pct` | cpp-inplace | 0.623798 | 0.626667 | 0.617336 | 0.630714 | 0.006100 | 1.0% |
| `10M` | `10pct` | cpp-std-sort | 0.611243 | 0.611946 | 0.606312 | 0.613901 | 0.002990 | 0.5% |
| `10M` | `random` | franko-inplace | 0.640923 | 0.638290 | 0.634337 | 0.654488 | 0.007933 | 1.2% |
| `10M` | `random` | cpp-inplace | 0.634834 | 0.635546 | 0.629861 | 0.640282 | 0.004003 | 0.6% |
| `10M` | `random` | cpp-std-sort | 0.628468 | 0.629410 | 0.623295 | 0.634798 | 0.004770 | 0.8% |
| `1M` | `0-9` | franko-inplace | 0.013500 | 0.013445 | 0.013026 | 0.014223 | 0.000328 | 2.4% |
| `1M` | `0-9` | cpp-inplace | 0.013717 | 0.013557 | 0.013251 | 0.015083 | 0.000533 | 3.9% |
| `1M` | `0-9` | cpp-std-sort | 0.019308 | 0.019073 | 0.018753 | 0.020184 | 0.000530 | 2.7% |
| `1M` | `1pct` | franko-inplace | 0.051867 | 0.051517 | 0.051020 | 0.054172 | 0.001010 | 1.9% |
| `1M` | `1pct` | cpp-inplace | 0.051597 | 0.051436 | 0.050944 | 0.052833 | 0.000559 | 1.1% |
| `1M` | `1pct` | cpp-std-sort | 0.051973 | 0.052123 | 0.050386 | 0.053670 | 0.001013 | 1.9% |
| `1M` | `10pct` | franko-inplace | 0.055014 | 0.054791 | 0.053790 | 0.056579 | 0.000966 | 1.8% |
| `1M` | `10pct` | cpp-inplace | 0.054073 | 0.054063 | 0.052582 | 0.056650 | 0.001259 | 2.3% |
| `1M` | `10pct` | cpp-std-sort | 0.054890 | 0.054803 | 0.052935 | 0.056559 | 0.001022 | 1.9% |
| `1M` | `random` | franko-inplace | 0.054205 | 0.054125 | 0.053365 | 0.055147 | 0.000554 | 1.0% |
| `1M` | `random` | cpp-inplace | 0.053532 | 0.053053 | 0.052330 | 0.056881 | 0.001314 | 2.5% |
| `1M` | `random` | cpp-std-sort | 0.053411 | 0.053165 | 0.052720 | 0.054843 | 0.000644 | 1.2% |

The coefficient of variation is 0.5–3.9% across these 24 cells.

### 3.3 Chunk timings for 100k and 10k

| size | dataset | mode | chunk s | reps | mean/sort |
| --- | --- | --- | ---: | ---: | ---: |
| `100k` | `0-9` | franko-inplace | 0.185433 | 100 | 0.001854 |
| `100k` | `0-9` | cpp-inplace | 0.173224 | 100 | 0.001732 |
| `100k` | `0-9` | cpp-std-sort | 0.155281 | 100 | 0.001553 |
| `100k` | `1pct` | franko-inplace | 0.432752 | 100 | 0.004328 |
| `100k` | `1pct` | cpp-inplace | 0.425888 | 100 | 0.004259 |
| `100k` | `1pct` | cpp-std-sort | 0.438180 | 100 | 0.004382 |
| `100k` | `10pct` | franko-inplace | 0.422619 | 100 | 0.004226 |
| `100k` | `10pct` | cpp-inplace | 0.411944 | 100 | 0.004119 |
| `100k` | `10pct` | cpp-std-sort | 0.423356 | 100 | 0.004234 |
| `100k` | `random` | franko-inplace | 0.433333 | 100 | 0.004333 |
| `100k` | `random` | cpp-inplace | 0.421991 | 100 | 0.004220 |
| `100k` | `random` | cpp-std-sort | 0.428472 | 100 | 0.004285 |
| `10k` | `0-9` | franko-inplace | 0.173070 | 1000 | 0.000173 |
| `10k` | `0-9` | cpp-inplace | 0.159387 | 1000 | 0.000159 |
| `10k` | `0-9` | cpp-std-sort | 0.125589 | 1000 | 0.000126 |
| `10k` | `1pct` | franko-inplace | 0.324702 | 1000 | 0.000325 |
| `10k` | `1pct` | cpp-inplace | 0.314012 | 1000 | 0.000314 |
| `10k` | `1pct` | cpp-std-sort | 0.352528 | 1000 | 0.000353 |
| `10k` | `10pct` | franko-inplace | 0.333594 | 1000 | 0.000334 |
| `10k` | `10pct` | cpp-inplace | 0.326189 | 1000 | 0.000326 |
| `10k` | `10pct` | cpp-std-sort | 0.333172 | 1000 | 0.000333 |
| `10k` | `random` | franko-inplace | 0.324553 | 1000 | 0.000325 |
| `10k` | `random` | cpp-inplace | 0.321863 | 1000 | 0.000322 |
| `10k` | `random` | cpp-std-sort | 0.336348 | 1000 | 0.000336 |

### 3.4 Correctness

All three modes produced identical output within each size/dataset cell:

| size | dataset | checksum |
| --- | --- | ---: |
| `10M` | `0-9` | 44990029 |
| `10M` | `1pct` | 500019587679 |
| `10M` | `10pct` | 4999269187679 |
| `10M` | `random` | 5946973223987570847 |
| `1M` | `0-9` | 4496507 |
| `1M` | `1pct` | 50008306277 |
| `1M` | `10pct` | 500529406277 |
| `1M` | `random` | 14866788768196358277 |
| `100k` | `0-9` | 45154800 |
| `100k` | `1pct` | 501804613800 |
| `100k` | `10pct` | 5004754613800 |
| `100k` | `random` | 13266229027527042088 |
| `10k` | `0-9` | 45574000 |
| `10k` | `1pct` | 502497564000 |
| `10k` | `10pct` | 5025497564000 |
| `10k` | `random` | 15289688356957299552 |

For 100k and 10k, the checksum is accumulated over the 100 or 1000
repetitions in the chunk. The checksum is the wrapping sum of the sorted
`int64` values cast to `uint64`.

---

## 4. Analysis

### 4.1 The 0-9 crossover is the 100k threshold

The `0-9` rows change behavior at 100k:

- At **10M and 1M**, `N > 100000`, the estimator sees a very large
  duplicate-group estimate, and both in-place sorts use DNF. All equal
  keys are grouped in one partition and removed from the recursion.
  Franko and C++ run in **0.65x–0.71x** of `std::sort`.
- At **100k and 10k**, `N <= 100000`, the small-input rule always uses
  Lomuto. DNF is not selected, so duplicate groups are peeled one pivot
  at a time. Franko and C++ are now **1.12x–1.38x** slower than
  `std::sort`.

This is not a DNF regression; it is the configured cutoff. The 100k
threshold is doing exactly what it was asked to do, but the `0-9`
results show the cost of disabling DNF on duplicate-heavy small inputs.

The `m_hat` cutoff is 55. A Franko-only 10M smoke test at 2.5% distinct
(`m ≈ 40`) found Lomuto about 10% faster than forced DNF; at 2.0%
(`m ≈ 50`) Lomuto was 3.6% faster, and at 1.8% (`m ≈ 55`) it was 1.2%
faster. At 1.5% (`m ≈ 67`) and 1.0% (`m ≈ 100`) the two paths were
effectively tied. The measurable crossover is therefore around
`m ≈ 65–100`, and 55 is a conservative choice that avoids the clear DNF
loss at `m ≈ 40` while still giving DNF the very duplicate-heavy cases.

The estimator is collision-based, so for a fixed number of distinct
values, non-uniform frequencies only increase `m_hat`; skew pushes the
choice toward DNF, not away from it.

### 4.2 Cache effects dominate the size scaling

The high-cardinality rows show a clear memory-hierarchy effect:

| dataset | 10M | 1M | 100k | 10k |
| --- | ---: | ---: | ---: | ---: |
| `1pct` (Franko) | 58.8 ns | 51.9 ns | 43.3 ns | 32.5 ns |
| `10pct` (Franko) | 61.9 ns | 55.0 ns | 42.3 ns | 33.4 ns |
| `random` (Franko) | 64.1 ns | 54.2 ns | 43.3 ns | 32.5 ns |

At 10M the 80 MB data array exceeds L3 and each partition pass pays
memory bandwidth. At 1M the 8 MB array fits in L3; at 100k the 0.8 MB
array fits in L2; at 10k the 80 KB array fits comfortably in L2. The
algorithm does the same comparisons per key, but the memory system does
not.

`0-9` breaks the pattern at 10M/1M because DNF removes equal keys in
bulk, keeping the per-key cost near 13.5–13.8 ns. At 100k/10k, Lomuto
no longer gets that benefit.

### 4.3 Franko versus hand-written C++

Franko and the C++ port stay within about 9% on every cell. The
largest gaps are:

- `10k` `0-9`: Franko 0.000173 s vs C++ 0.000159 s (8.8% slower);
- `10M` `1pct`: Franko 0.588 s vs C++ 0.545 s (8.0% slower);
- `100k` `0-9`: Franko 0.001854 s vs C++ 0.001732 s (7.0% slower);
- `10k` `1pct`: Franko 0.000325 s vs C++ 0.000314 s (3.5% slower).

Franko is slightly faster on `10pct` at 10M (0.8%) and `0-9` at 1M
(1.6%). This is code-generation parity, not a structural difference.
Both use the same pivot table, partition logic, depth budget, and
small-sort cutoffs.

### 4.4 The algorithm versus `std::sort`

`std::sort` remains a strong reference:

- It is fastest on the three high-cardinality datasets at 10M:
  Franko is 1.14x slower on `1pct`, 1.01x on `10pct`, and 1.02x on
  `random`; C++ is 1.05x, 1.02x, and 1.01x slower.
- At 1M, 100k, and 10k, the high-cardinality cells are essentially
  tied or slightly favor the custom sorts (0.89x–1.02x of `std::sort`).
- It wins the Lomuto-only `0-9` cells at 100k and 10k.
- On `0-9` at 10M/1M, the custom sorts win by 1.4–1.6x because DNF
  removes the duplicate groups entirely.

The custom sort's clear win is the large duplicate-heavy case; its
clear loss is the small duplicate-heavy case where DNF is disabled.

### 4.5 Noise and chunk measurements

For 10M and 1M, the coefficient of variation is 0.5–3.9% across 24
cells. Differences below about 4% should be read as small effects.

For 100k and 10k, each cell is a single chunk measurement, so there is
no run-to-run SD. The chunk amortizes process startup over 100 or 1000
sorts, but it still includes one process launch and 100/1000 rounds of
generation, sort, verification, and checksum. The per-sort means are
appropriate for comparing modes within a cell, but they are not
independent samples.

---

## 5. Limitations

1. **Uneven repetition counts.** 10M uses 5 runs and 1M uses 10 runs;
   100k and 10k use one chunk of 100 and 1000 sorts. The small-size
   cells have no independent SD.
2. **Whole-process timing.** Generation, sorting, verification, and
   checksum are included. They are identical across modes but not zero,
   and they matter proportionally more at 10k/100k.
3. **Synthetic datasets.** `1pct` and `10pct` use uniform frequencies;
   skewed real data changes both the estimator and the partition
   behavior.
4. **Estimator heuristic.** The 0.1% sample and the `m_hat > 55`
   threshold are tuned for non-adversarial data. Adversarial inputs can
   evade the sample.
5. **`std::sort` is introsort.** It is a strong reference, not an exact
   algorithm match.
6. **One machine.** WSL2 on a 12th-gen i5-12600K with CPU pinning.
   Absolute times are host-sensitive.
7. **Only maximum release builds.** Franko `-Omax` and C++ `-O3`;
   checked/sanitizer builds are not included.
8. **Binary size includes static runtimes.** Franko is 940 KB because
   jemalloc is statically linked; C++ is 158 KB and `std::sort` 154 KB.
9. **Tuned constants.** The 100k Lomuto cutoff, the 32-element
   recursion cutoff, the 2–8 network range, the depth budget, and the
   `m_hat > 55` threshold are tuned to this workload.

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

| argument | meaning |
| --- | --- |
| `3` | `0-9` |
| `2` | `1pct` |
| `1` | `10pct` |
| `0` | `random` |
| `count` | number of keys |
| `repeat` | number of sorts inside the process |

The harness uses the following matrix:

| size | count | repetitions | timing |
| --- | ---: | ---: | --- |
| `10M` | 10,000,000 | 5 | 5 separate processes |
| `1M` | 1,000,000 | 10 | 10 separate processes |
| `100k` | 100,000 | 100 | one chunk, divided by 100 |
| `10k` | 10,000 | 1000 | one chunk, divided by 1000 |

Generated `.cpp` and `.out` files are build artifacts and are ignored by
git.

---

## Appendix A: raw 10M runs

Five measured runs per mode and dataset.

| dataset | mode | r1 | r2 | r3 | r4 | r5 | mean |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `0-9` | franko-inplace | 0.138169 | 0.138224 | 0.135475 | 0.140673 | 0.136722 | 0.137853 |
| `0-9` | cpp-inplace | 0.134771 | 0.133809 | 0.136358 | 0.134967 | 0.132598 | 0.134501 |
| `0-9` | cpp-std-sort | 0.209494 | 0.211931 | 0.216669 | 0.210184 | 0.207932 | 0.211242 |
| `1pct` | franko-inplace | 0.586920 | 0.588492 | 0.584141 | 0.592988 | 0.589592 | 0.588427 |
| `1pct` | cpp-inplace | 0.536879 | 0.546118 | 0.545957 | 0.548504 | 0.547088 | 0.544909 |
| `1pct` | cpp-std-sort | 0.519064 | 0.511567 | 0.514960 | 0.506593 | 0.533429 | 0.517123 |
| `10pct` | franko-inplace | 0.618733 | 0.630201 | 0.614628 | 0.612019 | 0.617170 | 0.618550 |
| `10pct` | cpp-inplace | 0.617336 | 0.630714 | 0.626904 | 0.617366 | 0.626667 | 0.623798 |
| `10pct` | cpp-std-sort | 0.606312 | 0.613179 | 0.610878 | 0.611946 | 0.613901 | 0.611243 |
| `random` | franko-inplace | 0.640746 | 0.636755 | 0.638290 | 0.654488 | 0.634337 | 0.640923 |
| `random` | cpp-inplace | 0.632184 | 0.629861 | 0.640282 | 0.635546 | 0.636296 | 0.634834 |
| `random` | cpp-std-sort | 0.629410 | 0.630668 | 0.634798 | 0.624170 | 0.623295 | 0.628468 |

## Appendix B: raw 1M runs

Ten measured runs per mode and dataset.

| dataset | mode | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `0-9` | franko-inplace | 0.013591 | 0.013026 | 0.013354 | 0.013477 | 0.013196 | 0.014223 | 0.013581 | 0.013414 | 0.013765 | 0.013370 | 0.013500 |
| `0-9` | cpp-inplace | 0.013598 | 0.013420 | 0.013515 | 0.013251 | 0.013510 | 0.015083 | 0.013350 | 0.014096 | 0.013726 | 0.013615 | 0.013717 |
| `0-9` | cpp-std-sort | 0.019117 | 0.020148 | 0.019030 | 0.019774 | 0.018753 | 0.018891 | 0.018967 | 0.019260 | 0.018961 | 0.020184 | 0.019308 |
| `1pct` | franko-inplace | 0.051042 | 0.051164 | 0.052646 | 0.054172 | 0.052077 | 0.051099 | 0.051020 | 0.051825 | 0.051208 | 0.052420 | 0.051867 |
| `1pct` | cpp-inplace | 0.050944 | 0.051456 | 0.051416 | 0.051798 | 0.052006 | 0.052833 | 0.051067 | 0.051914 | 0.051232 | 0.051303 | 0.051597 |
| `1pct` | cpp-std-sort | 0.052187 | 0.052683 | 0.052672 | 0.050386 | 0.052689 | 0.052059 | 0.051378 | 0.050874 | 0.053670 | 0.051131 | 0.051973 |
| `10pct` | franko-inplace | 0.056563 | 0.053826 | 0.055252 | 0.054476 | 0.054742 | 0.054736 | 0.056579 | 0.055340 | 0.054839 | 0.053790 | 0.055014 |
| `10pct` | cpp-inplace | 0.052802 | 0.052582 | 0.054044 | 0.053409 | 0.054549 | 0.054082 | 0.054642 | 0.055160 | 0.052806 | 0.056650 | 0.054073 |
| `10pct` | cpp-std-sort | 0.054200 | 0.056559 | 0.056062 | 0.054748 | 0.052935 | 0.054858 | 0.055286 | 0.055434 | 0.054454 | 0.054361 | 0.054890 |
| `random` | franko-inplace | 0.054053 | 0.054294 | 0.053653 | 0.053844 | 0.055147 | 0.054710 | 0.054197 | 0.053365 | 0.054829 | 0.053956 | 0.054205 |
| `random` | cpp-inplace | 0.054199 | 0.053795 | 0.053184 | 0.052821 | 0.052330 | 0.052791 | 0.052654 | 0.053746 | 0.052923 | 0.056881 | 0.053532 |
| `random` | cpp-std-sort | 0.054843 | 0.053043 | 0.053184 | 0.052720 | 0.053590 | 0.053023 | 0.052848 | 0.053694 | 0.053146 | 0.054022 | 0.053411 |

## Appendix C: chunk timings

For 100k, one chunk is 100 sorts; for 10k, one chunk is 1000 sorts.
The mean is the chunk divided by the repetition count.

| size | dataset | mode | chunk s | reps | mean/sort |
| --- | --- | --- | ---: | ---: | ---: |
| `100k` | `0-9` | franko-inplace | 0.185433 | 100 | 0.001854 |
| `100k` | `0-9` | cpp-inplace | 0.173224 | 100 | 0.001732 |
| `100k` | `0-9` | cpp-std-sort | 0.155281 | 100 | 0.001553 |
| `100k` | `1pct` | franko-inplace | 0.432752 | 100 | 0.004328 |
| `100k` | `1pct` | cpp-inplace | 0.425888 | 100 | 0.004259 |
| `100k` | `1pct` | cpp-std-sort | 0.438180 | 100 | 0.004382 |
| `100k` | `10pct` | franko-inplace | 0.422619 | 100 | 0.004226 |
| `100k` | `10pct` | cpp-inplace | 0.411944 | 100 | 0.004119 |
| `100k` | `10pct` | cpp-std-sort | 0.423356 | 100 | 0.004234 |
| `100k` | `random` | franko-inplace | 0.433333 | 100 | 0.004333 |
| `100k` | `random` | cpp-inplace | 0.421991 | 100 | 0.004220 |
| `100k` | `random` | cpp-std-sort | 0.428472 | 100 | 0.004285 |
| `10k` | `0-9` | franko-inplace | 0.173070 | 1000 | 0.000173 |
| `10k` | `0-9` | cpp-inplace | 0.159387 | 1000 | 0.000159 |
| `10k` | `0-9` | cpp-std-sort | 0.125589 | 1000 | 0.000126 |
| `10k` | `1pct` | franko-inplace | 0.324702 | 1000 | 0.000325 |
| `10k` | `1pct` | cpp-inplace | 0.314012 | 1000 | 0.000314 |
| `10k` | `1pct` | cpp-std-sort | 0.352528 | 1000 | 0.000353 |
| `10k` | `10pct` | franko-inplace | 0.333594 | 1000 | 0.000334 |
| `10k` | `10pct` | cpp-inplace | 0.326189 | 1000 | 0.000326 |
| `10k` | `10pct` | cpp-std-sort | 0.333172 | 1000 | 0.000333 |
| `10k` | `random` | franko-inplace | 0.324553 | 1000 | 0.000325 |
| `10k` | `random` | cpp-inplace | 0.321863 | 1000 | 0.000322 |
| `10k` | `random` | cpp-std-sort | 0.336348 | 1000 | 0.000336 |

## Appendix D: files

| File | Role |
| --- | --- |
| `benchmark/quicksort_benchmark.py` | builds and times the 4-size × 4-dataset matrix |
| `benchmark/quicksort_inplace_franko.fr` | Franko `-Omax`, in-place value sort |
| `benchmark/quicksort_inplace_cpp.cpp` | C++ `-O3`, same in-place algorithm |
| `benchmark/quicksort_std_cpp.cpp` | C++ `-O3`, `std::sort` reference |
| `benchmark/quicksort.md` | This report |
