# In-place quicksort benchmark: Franko vs C++ vs std::sort

**Plain `int64` keys · 5 sizes × 6 distributions · all chunk timing · 10M: 5, 1M: 10, 99k: 300, 10k: 3,000, 1k: 30,000 · 2026-11-10**

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

- `0-9` wins every size: Franko **0.66x–0.87x**, C++ **0.61x–0.90x**
  of `std::sort`.
- `1k` is strong for both: Franko wins all six (**0.87x–1.00x**);
  C++ wins five of six and loses only `10pct` (1.01x).
- `10k` splits: C++ is **0.61x–0.96x** on five rows and loses `10pct`;
  Franko is **0.74x–0.99x** on five rows and loses `random`.
- `99k`: C++ wins five of six (loses `random`); Franko wins four of
  six (loses `1pct` and `random`).
- `1M`: C++ wins `0-9` and `10pct`; Franko wins only `0-9`.
- `10M`: only `0-9` wins; every non-`0-9` row loses.
- The code now uses separate function families for `<5k`, `<100k`, and
  `>=100k`, with separate DNF and Lomuto variants and no shared
  partition kernels.
- The median-of-3 network now places the median directly at `lo` with
  3 comparisons and no extra pivot swap.
- All 30 cells agree.

---

## 1. Bottom line

### 1.1 Mean seconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | 0.129250 | **0.128033** | 0.195265 |
| `10M` | `0.25pct` | 0.517474 | 0.486473 | **0.471848** |
| `10M` | `0.5pct` | 0.562107 | 0.523781 | **0.493400** |
| `10M` | `1pct` | 0.583472 | 0.552453 | **0.512826** |
| `10M` | `10pct` | 0.633383 | 0.620386 | **0.605342** |
| `10M` | `random` | 0.645092 | 0.649202 | **0.622965** |
| `1M` | `0-9` | 0.012968 | **0.012003** | 0.017557 |
| `1M` | `0.25pct` | 0.038992 | 0.040616 | **0.037119** |
| `1M` | `0.5pct` | 0.044135 | 0.040916 | **0.038701** |
| `1M` | `1pct` | 0.045143 | 0.043098 | **0.042815** |
| `1M` | `10pct` | 0.053028 | **0.052316** | 0.052768 |
| `1M` | `random` | 0.054752 | 0.054311 | **0.052594** |
| `99k` | `0-9` | 0.001147 | **0.001107** | 0.001489 |
| `99k` | `0.25pct` | 0.002569 | **0.002468** | 0.002655 |
| `99k` | `0.5pct` | 0.003079 | **0.002882** | 0.003180 |
| `99k` | `1pct` | 0.003460 | **0.003257** | 0.003367 |
| `99k` | `10pct` | 0.004080 | **0.004044** | 0.004128 |
| `99k` | `random` | 0.004233 | 0.004223 | **0.004150** |
| `10k` | `0-9` | 0.000094 | **0.000077** | 0.000127 |
| `10k` | `0.25pct` | 0.000151 | **0.000136** | 0.000178 |
| `10k` | `0.5pct` | 0.000184 | **0.000164** | 0.000198 |
| `10k` | `1pct` | 0.000224 | **0.000208** | 0.000233 |
| `10k` | `10pct` | **0.000317** | 0.000324 | 0.000319 |
| `10k` | `random` | 0.000331 | **0.000315** | 0.000328 |
| `1k` | `0-9` | **0.0000044815** | 0.0000046509 | 0.0000051444 |
| `1k` | `0.25pct` | **0.0000051593** | 0.0000051976 | 0.0000057066 |
| `1k` | `0.5pct` | 0.0000056796 | **0.0000055633** | 0.0000061573 |
| `1k` | `1pct` | **0.0000055933** | 0.0000060008 | 0.0000062313 |
| `1k` | `10pct` | **0.0000069148** | 0.0000074250 | 0.0000073256 |
| `1k` | `random` | 0.0000060976 | **0.0000060325** | 0.0000061009 |

All per-sort means are chunk wall time divided by the repetition count.

### 1.2 Slowdown vs `std::sort`

| size | dataset | franko-inplace | cpp-inplace |
| --- | --- | ---: | ---: |
| `10M` | `0-9` | 0.662x | 0.656x |
| `10M` | `0.25pct` | 1.097x | 1.031x |
| `10M` | `0.5pct` | 1.139x | 1.062x |
| `10M` | `1pct` | 1.138x | 1.077x |
| `10M` | `10pct` | 1.046x | 1.025x |
| `10M` | `random` | 1.036x | 1.042x |
| `1M` | `0-9` | 0.739x | 0.684x |
| `1M` | `0.25pct` | 1.050x | 1.094x |
| `1M` | `0.5pct` | 1.140x | 1.057x |
| `1M` | `1pct` | 1.054x | 1.007x |
| `1M` | `10pct` | 1.005x | 0.991x |
| `1M` | `random` | 1.041x | 1.033x |
| `99k` | `0-9` | 0.770x | 0.743x |
| `99k` | `0.25pct` | 0.968x | 0.930x |
| `99k` | `0.5pct` | 0.968x | 0.906x |
| `99k` | `1pct` | 1.028x | 0.967x |
| `99k` | `10pct` | 0.988x | 0.980x |
| `99k` | `random` | 1.020x | 1.018x |
| `10k` | `0-9` | 0.741x | 0.607x |
| `10k` | `0.25pct` | 0.851x | 0.766x |
| `10k` | `0.5pct` | 0.931x | 0.831x |
| `10k` | `1pct` | 0.962x | 0.892x |
| `10k` | `10pct` | 0.994x | 1.014x |
| `10k` | `random` | 1.009x | 0.960x |
| `1k` | `0-9` | 0.871x | 0.904x |
| `1k` | `0.25pct` | 0.904x | 0.911x |
| `1k` | `0.5pct` | 0.922x | 0.904x |
| `1k` | `1pct` | 0.898x | 0.963x |
| `1k` | `10pct` | 0.944x | 1.014x |
| `1k` | `random` | 0.999x | 0.989x |

A value below `1.00x` means the implementation is faster than
`std::sort`.

Key points:

- **`0-9` wins every size.** The range is 0.61–0.90x overall.
- **1k favors Franko slightly.** Franko wins all six; C++ wins five
  and loses `10pct` by about 1%.
- **10k favors C++.** C++ reaches 0.61x on `0-9` and wins five of six;
  Franko wins five of six but its margins are smaller.
- **99k favors C++.** C++ wins five of six; Franko loses `1pct` and
  `random`.
- **1M and 10M are hard.** At 1M C++ wins `0-9` and `10pct` while
  Franko wins only `0-9`; at 10M only `0-9` wins.
- **The separated function families cost Franko on hot rows.** C++ is
  up to 22% faster at 10k and 7–8% faster on several 10M/1M/99k rows;
  Franko remains faster on a few 1k rows.

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

The implementation is split into three independent families:

- **`N < 5000`:** `quicksort_lt_5k` / `quicksort_lt_5k_rec`, using
  `median3_partition_lt_5k` for every partition.
- **`5000 <= N < 100000`:** `quicksort_lt_100k_dnf` /
  `quicksort_lt_100k_lomuto` and their `_rec` variants.
- **`N >= 100000`:** `quicksort_gt_100k_dnf` /
  `quicksort_gt_100k_lomuto`, their `_second` layer-2 functions, and
  their `_rec` variants.

The preflight is tiered with half-open ranges:

- **`N >= 100000`:** the `m_hat` estimator. It draws
  `k = clamp(N / 1000, 100, 65535)` stratified samples and heap-sorts
  the sampled indices. If `m_hat > 70`, DNF is selected; otherwise
  Lomuto. The sorted sample also provides the 25th, 50th, and 75th
  percentile values and positions.
- **`40000 <= N < 100000`:** a 60-element randomized static probe.
  Any duplicate selects DNF, otherwise Lomuto.
- **`20000 <= N < 40000`:** the same rule with 38 samples.
- **`10000 <= N < 20000`:** the same rule with 27 samples.
- **`5000 <= N < 10000`:** the same rule with 19 samples.
- **`N < 5000`:** no preflight; plain Hoare median-of-3.

For `N >= 100000`, the first partition uses the sample median. Layer 2
uses the 25th percentile for the left side and the 75th percentile for
the right side. The Lomuto first partition tracks those two sampled
positions through its swaps, so layer 2 needs no search. If a
percentile equals the median, that side falls back to the random
pivot. Layer 3 onward uses the random pivot.

For `5000 <= N < 100000`, the first partition uses the sample median;
layer 2 onward uses the random pivot.

For `N < 5000`, every partition uses the median-of-3 network:

```text
if (v[lo]  < v[mid]) swap(v[lo],  v[mid]);
if (v[hi]  < v[lo])  swap(v[hi],  v[lo]);
if (v[lo]  < v[mid]) swap(v[lo],  v[mid]);
```

This is the optimal median-at-`lo` network: 3 comparisons and no extra
pivot swap.

Every read from `RANDOM_PIVOTS` advances `pivot_cursor`, including
sample reads. The cursor resets at the start of each sort.

DNF and Lomuto use separate partition functions at every layer; there
is no per-recursion scheme branch.

The DNF choice for this benchmark is:

| size | DNF datasets | Lomuto datasets |
| --- | --- | --- |
| `10M` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `1M` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `99k` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `10k` | `0-9`, `0.25pct`, `0.5pct`, `1pct` | `10pct`, `random` |
| `1k` | none | none — Hoare median-of-3 |

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
times the whole process and divides by the repetition count.

The run used `g++ 15.2.0` under WSL2 on a 12th-gen Intel i5-12600K,
pinned to CPU 2. Absolute times are host-sensitive.

---

## 3. Results

### 3.1 Per-key nanoseconds

| size | dataset | franko-inplace | cpp-inplace | cpp-std-sort |
| --- | --- | ---: | ---: | ---: |
| `10M` | `0-9` | 12.93 | **12.80** | 19.53 |
| `10M` | `0.25pct` | 51.75 | 48.65 | **47.18** |
| `10M` | `0.5pct` | 56.21 | 52.38 | **49.34** |
| `10M` | `1pct` | 58.35 | 55.25 | **51.28** |
| `10M` | `10pct` | 63.34 | 62.04 | **60.53** |
| `10M` | `random` | 64.51 | 64.92 | **62.30** |
| `1M` | `0-9` | 12.97 | **12.00** | 17.56 |
| `1M` | `0.25pct` | 38.99 | 40.62 | **37.12** |
| `1M` | `0.5pct` | 44.14 | 40.92 | **38.70** |
| `1M` | `1pct` | 45.14 | 43.10 | **42.82** |
| `1M` | `10pct` | 53.03 | **52.32** | 52.77 |
| `1M` | `random` | 54.75 | 54.31 | **52.59** |
| `99k` | `0-9` | 11.59 | **11.18** | 15.04 |
| `99k` | `0.25pct` | 25.95 | **24.93** | 26.81 |
| `99k` | `0.5pct` | 31.10 | **29.11** | 32.12 |
| `99k` | `1pct` | 34.95 | **32.90** | 34.01 |
| `99k` | `10pct` | 41.21 | **40.85** | 41.70 |
| `99k` | `random` | 42.75 | 42.66 | **41.92** |
| `10k` | `0-9` | 9.43 | **7.73** | 12.73 |
| `10k` | `0.25pct` | 15.14 | **13.63** | 17.79 |
| `10k` | `0.5pct` | 18.40 | **16.43** | 19.76 |
| `10k` | `1pct` | 22.42 | **20.78** | 23.29 |
| `10k` | `10pct` | **31.74** | 32.40 | 31.95 |
| `10k` | `random` | 33.13 | **31.54** | 32.84 |
| `1k` | `0-9` | **4.48** | 4.65 | 5.14 |
| `1k` | `0.25pct` | **5.16** | 5.20 | 5.71 |
| `1k` | `0.5pct` | 5.68 | **5.56** | 6.16 |
| `1k` | `1pct` | **5.59** | 6.00 | 6.23 |
| `1k` | `10pct` | **6.91** | 7.42 | 7.33 |
| `1k` | `random` | 6.10 | **6.03** | 6.10 |

The 10M/1M per-key costs are higher because the 80 MB and 8 MB arrays
do not fit in the same cache levels; the 1k row fits in L1/L2.

### 3.2 Chunk timings

Full chunk wall times and per-sort means are in Appendix A.

---

## 4. Analysis

### 4.1 Regime split

The three families remove the old per-recursion `use_dnf` test and the
old shared partition kernels. The trade-off is code size: Franko emits
each partition function separately, so the hot paths pay more call and
I-cache overhead than the hand-written C++.

- `0-9` wins every size and is the least sensitive to the split.
- At 1k the Hoare path is unaffected by the DNF/Lomuto split and stays
  competitive.
- At 10k and on the large duplicate-heavy rows, the duplicated
  functions help C++ more than Franko.

### 4.2 All-chunk timing

The 10M and 1M rows are one process per cell with 5/10 sorts inside it
and one data allocation. Absolute values are not directly comparable
to separate-process measurements.

### 4.3 Cache effects

| dataset | 10M | 1M | 99k | 10k | 1k |
| --- | ---: | ---: | ---: | ---: | ---: |
| `1pct` (Franko) | 58.3 ns | 45.1 ns | 34.9 ns | 22.4 ns | 5.6 ns |
| `10pct` (Franko) | 63.3 ns | 53.0 ns | 41.2 ns | 31.7 ns | 6.9 ns |
| `random` (Franko) | 64.5 ns | 54.8 ns | 42.8 ns | 33.1 ns | 6.1 ns |

At 10M the array exceeds L3; at 1M it fits in L3; at 99k it fits in
L2; at 10k/1k it fits in L1/L2.

### 4.4 Franko versus hand-written C++

The separated function families widened the codegen gap on hot rows:

- `10k` `0-9`: Franko 0.000094 s vs C++ 0.000077 s (C++ 18% faster);
- `10k` `0.5pct`: Franko 0.000184 s vs C++ 0.000164 s (C++ 11% faster);
- `10k` `0.25pct`: Franko 0.000151 s vs C++ 0.000136 s (C++ 10% faster);
- `1M` `0-9`: Franko 0.012968 s vs C++ 0.012003 s (C++ 7.4% faster);
- `1M` `0.5pct`: Franko 0.044135 s vs C++ 0.040916 s (C++ 7.3% faster);
- `10M` `0.5pct`: Franko 0.562107 s vs C++ 0.523781 s (C++ 6.8% faster);
- `99k` `0.5pct`: Franko 0.003079 s vs C++ 0.002882 s (C++ 6.4% faster);
- `1k` `1pct`: Franko 0.0000056 s vs C++ 0.0000060 s (Franko 6.8% faster).

At 10M/1M/99k the largest gaps are now in C++'s favor; 1k still favors
Franko on a few duplicate-heavy rows.

### 4.5 The algorithm versus `std::sort`

- The custom sorts win every `0-9` cell.
- At 1k Franko wins all six; C++ wins five and loses `10pct`.
- At 10k C++ wins five of six and Franko wins five of six.
- At 99k C++ wins five of six; Franko wins four of six.
- At 1M C++ wins `0-9` and `10pct`; Franko wins only `0-9`.
- At 10M only `0-9` wins.

### 4.6 Noise

Every cell is a single chunk measurement. The 1k row is 4–7 µs per
sort and printed to 1 µs; its ratios are approximate. The other rows
are 10–300 ms per chunk.

### 4.7 Time and space complexity

Let `N` be the element count, `d` the number of distinct keys, and
`k = clamp(floor(N / 1000), 100, 65535)` the estimator sample size.

Time:

| stage | cost |
| --- | --- |
| estimator preflight (`N >= 100000`) | `O(k)` sampling + `O(k log k)` index heap sort |
| static probe (`5000 <= N < 100000`) | `O(s^2)` insertion sort with `s <= 60`, so `O(1)` |
| first pivot | taken from the preflight sample, `O(1)` extra |
| later pivot selection | `O(1)` per partition |
| partitioning | `O(N log N)` expected and worst case |
| small sorts (`<= 32` elements) | `O(1)` per leaf, `O(N)` total |
| data generation / sortedness check / checksum (harness) | `O(N)` |

The depth limit is `2 * floor_log2(N)`; exhausted subproblems are
heap-sorted. Partitioning is `O(N log N)` expected and worst case.
DNF removes the equal block, so duplicate-heavy data behaves like
`O(N log d)`; all-equal input is `O(N)` at the partition stage.

Space:

| N range | preflight storage | recursion stack | auxiliary space |
| --- | --- | --- | --- |
| `N >= 100000` | `k` u32 indices = `4k` bytes, max 262,140 B | `O(log N)` | `O(k + log N)` |
| `40000 <= N < 100000` | 60 u32 = 240 B | `O(log N)` | `O(log N)` |
| `20000 <= N < 40000` | 38 u32 = 152 B | `O(log N)` | `O(log N)` |
| `10000 <= N < 20000` | 27 u32 = 108 B | `O(log N)` | `O(log N)` |
| `5000 <= N < 10000` | 19 u32 = 76 B | `O(log N)` | `O(log N)` |
| `N < 5000` | none | `O(log N)` | `O(log N)` |

The input array is `8N` bytes and sorted in place: total space `O(N)`.
For `100000 <= N <= 65,535,999`, `k = floor(N / 1000)`, so the
estimator uses about `0.001N` u32 elements (`0.004N` bytes): 40 KB at
10M and 4 KB at 1M. Above that, the sample is capped at 65,535 entries
(about 256 KiB).

---

## 5. Limitations

1. **No independent per-sort samples.** All cells are one chunk; no SD
   or CV table.
2. **10M/1M chunk reuse.** One allocation is reused across 5/10 sorts.
3. **1k timer resolution.** The 1k means are 4–7 µs, printed to 1 µs.
4. **Synthetic uniform frequencies.** Real skew changes probe behavior.
5. **Fixed probes are heuristics.** The 38- and 19-sample tiers are not
   exercised by the five benchmark sizes.
6. **Every path is depth-limited.** DNF, Lomuto, and Hoare all fall back
   to heapsort after `2 * floor_log2(N)` levels.
7. **`std::sort` is introsort.** It is a strong reference, not an exact
   algorithm match.
8. **One machine.** WSL2 on a 12th-gen i5-12600K with CPU pinning.
9. **Only maximum release builds.** Franko `-Omax`, C++ `-O3`.
10. **Binary size includes static runtimes.** Franko is 956 KB; C++ is
    163 KB and `std::sort` 154 KB.
11. **Tuned constants.** Estimator threshold, sample clamp, probe
    sizes, 32-element cutoff, and the `2 * log2(N)` depth factor are
    tuned to this workload.
12. **Separated function families cost Franko.** The three-regime,
    DNF/Lomuto-specific functions avoid hot branches but duplicate
    partition code; Franko's generated code pays more call/I-cache
    overhead on the partition-heavy rows.

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
| `10M` | `0-9` | franko-inplace | 0.646251 | 5 | 0.129250 |
| `10M` | `0-9` | cpp-inplace | 0.640164 | 5 | 0.128033 |
| `10M` | `0-9` | cpp-std-sort | 0.976326 | 5 | 0.195265 |
| `10M` | `0.25pct` | franko-inplace | 2.587371 | 5 | 0.517474 |
| `10M` | `0.25pct` | cpp-inplace | 2.432366 | 5 | 0.486473 |
| `10M` | `0.25pct` | cpp-std-sort | 2.359239 | 5 | 0.471848 |
| `10M` | `0.5pct` | franko-inplace | 2.810536 | 5 | 0.562107 |
| `10M` | `0.5pct` | cpp-inplace | 2.618905 | 5 | 0.523781 |
| `10M` | `0.5pct` | cpp-std-sort | 2.467000 | 5 | 0.493400 |
| `10M` | `1pct` | franko-inplace | 2.917359 | 5 | 0.583472 |
| `10M` | `1pct` | cpp-inplace | 2.762265 | 5 | 0.552453 |
| `10M` | `1pct` | cpp-std-sort | 2.564132 | 5 | 0.512826 |
| `10M` | `10pct` | franko-inplace | 3.166916 | 5 | 0.633383 |
| `10M` | `10pct` | cpp-inplace | 3.101930 | 5 | 0.620386 |
| `10M` | `10pct` | cpp-std-sort | 3.026708 | 5 | 0.605342 |
| `10M` | `random` | franko-inplace | 3.225462 | 5 | 0.645092 |
| `10M` | `random` | cpp-inplace | 3.246009 | 5 | 0.649202 |
| `10M` | `random` | cpp-std-sort | 3.114824 | 5 | 0.622965 |
| `1M` | `0-9` | franko-inplace | 0.129677 | 10 | 0.012968 |
| `1M` | `0-9` | cpp-inplace | 0.120033 | 10 | 0.012003 |
| `1M` | `0-9` | cpp-std-sort | 0.175568 | 10 | 0.017557 |
| `1M` | `0.25pct` | franko-inplace | 0.389923 | 10 | 0.038992 |
| `1M` | `0.25pct` | cpp-inplace | 0.406161 | 10 | 0.040616 |
| `1M` | `0.25pct` | cpp-std-sort | 0.371194 | 10 | 0.037119 |
| `1M` | `0.5pct` | franko-inplace | 0.441351 | 10 | 0.044135 |
| `1M` | `0.5pct` | cpp-inplace | 0.409162 | 10 | 0.040916 |
| `1M` | `0.5pct` | cpp-std-sort | 0.387009 | 10 | 0.038701 |
| `1M` | `1pct` | franko-inplace | 0.451425 | 10 | 0.045143 |
| `1M` | `1pct` | cpp-inplace | 0.430981 | 10 | 0.043098 |
| `1M` | `1pct` | cpp-std-sort | 0.428153 | 10 | 0.042815 |
| `1M` | `10pct` | franko-inplace | 0.530276 | 10 | 0.053028 |
| `1M` | `10pct` | cpp-inplace | 0.523164 | 10 | 0.052316 |
| `1M` | `10pct` | cpp-std-sort | 0.527680 | 10 | 0.052768 |
| `1M` | `random` | franko-inplace | 0.547524 | 10 | 0.054752 |
| `1M` | `random` | cpp-inplace | 0.543114 | 10 | 0.054311 |
| `1M` | `random` | cpp-std-sort | 0.525935 | 10 | 0.052593 |
| `99k` | `0-9` | franko-inplace | 0.344171 | 300 | 0.001147 |
| `99k` | `0-9` | cpp-inplace | 0.331995 | 300 | 0.001107 |
| `99k` | `0-9` | cpp-std-sort | 0.446785 | 300 | 0.001489 |
| `99k` | `0.25pct` | franko-inplace | 0.770790 | 300 | 0.002569 |
| `99k` | `0.25pct` | cpp-inplace | 0.740382 | 300 | 0.002468 |
| `99k` | `0.25pct` | cpp-std-sort | 0.796367 | 300 | 0.002655 |
| `99k` | `0.5pct` | franko-inplace | 0.923641 | 300 | 0.003079 |
| `99k` | `0.5pct` | cpp-inplace | 0.864458 | 300 | 0.002882 |
| `99k` | `0.5pct` | cpp-std-sort | 0.954052 | 300 | 0.003180 |
| `99k` | `1pct` | franko-inplace | 1.037931 | 300 | 0.003460 |
| `99k` | `1pct` | cpp-inplace | 0.977142 | 300 | 0.003257 |
| `99k` | `1pct` | cpp-std-sort | 1.010142 | 300 | 0.003367 |
| `99k` | `10pct` | franko-inplace | 1.224067 | 300 | 0.004080 |
| `99k` | `10pct` | cpp-inplace | 1.213330 | 300 | 0.004044 |
| `99k` | `10pct` | cpp-std-sort | 1.238489 | 300 | 0.004128 |
| `99k` | `random` | franko-inplace | 1.269766 | 300 | 0.004233 |
| `99k` | `random` | cpp-inplace | 1.267008 | 300 | 0.004223 |
| `99k` | `random` | cpp-std-sort | 1.245020 | 300 | 0.004150 |
| `10k` | `0-9` | franko-inplace | 0.282946 | 3000 | 0.000094 |
| `10k` | `0-9` | cpp-inplace | 0.231858 | 3000 | 0.000077 |
| `10k` | `0-9` | cpp-std-sort | 0.381919 | 3000 | 0.000127 |
| `10k` | `0.25pct` | franko-inplace | 0.454138 | 3000 | 0.000151 |
| `10k` | `0.25pct` | cpp-inplace | 0.408759 | 3000 | 0.000136 |
| `10k` | `0.25pct` | cpp-std-sort | 0.533796 | 3000 | 0.000178 |
| `10k` | `0.5pct` | franko-inplace | 0.552082 | 3000 | 0.000184 |
| `10k` | `0.5pct` | cpp-inplace | 0.492803 | 3000 | 0.000164 |
| `10k` | `0.5pct` | cpp-std-sort | 0.592914 | 3000 | 0.000198 |
| `10k` | `1pct` | franko-inplace | 0.672481 | 3000 | 0.000224 |
| `10k` | `1pct` | cpp-inplace | 0.623359 | 3000 | 0.000208 |
| `10k` | `1pct` | cpp-std-sort | 0.698794 | 3000 | 0.000233 |
| `10k` | `10pct` | franko-inplace | 0.952186 | 3000 | 0.000317 |
| `10k` | `10pct` | cpp-inplace | 0.972103 | 3000 | 0.000324 |
| `10k` | `10pct` | cpp-std-sort | 0.958402 | 3000 | 0.000319 |
| `10k` | `random` | franko-inplace | 0.993951 | 3000 | 0.000331 |
| `10k` | `random` | cpp-inplace | 0.946233 | 3000 | 0.000315 |
| `10k` | `random` | cpp-std-sort | 0.985283 | 3000 | 0.000328 |
| `1k` | `0-9` | franko-inplace | 0.134445 | 30000 | 0.0000040000 |
| `1k` | `0-9` | cpp-inplace | 0.139526 | 30000 | 0.0000050000 |
| `1k` | `0-9` | cpp-std-sort | 0.154332 | 30000 | 0.0000050000 |
| `1k` | `0.25pct` | franko-inplace | 0.154779 | 30000 | 0.0000050000 |
| `1k` | `0.25pct` | cpp-inplace | 0.155927 | 30000 | 0.0000050000 |
| `1k` | `0.25pct` | cpp-std-sort | 0.171199 | 30000 | 0.0000060000 |
| `1k` | `0.5pct` | franko-inplace | 0.170389 | 30000 | 0.0000060000 |
| `1k` | `0.5pct` | cpp-inplace | 0.166899 | 30000 | 0.0000060000 |
| `1k` | `0.5pct` | cpp-std-sort | 0.184720 | 30000 | 0.0000060000 |
| `1k` | `1pct` | franko-inplace | 0.167799 | 30000 | 0.0000060000 |
| `1k` | `1pct` | cpp-inplace | 0.180023 | 30000 | 0.0000060000 |
| `1k` | `1pct` | cpp-std-sort | 0.186938 | 30000 | 0.0000060000 |
| `1k` | `10pct` | franko-inplace | 0.207444 | 30000 | 0.0000070000 |
| `1k` | `10pct` | cpp-inplace | 0.222749 | 30000 | 0.0000070000 |
| `1k` | `10pct` | cpp-std-sort | 0.219768 | 30000 | 0.0000070000 |
| `1k` | `random` | franko-inplace | 0.182929 | 30000 | 0.0000060000 |
| `1k` | `random` | cpp-inplace | 0.180975 | 30000 | 0.0000060000 |
| `1k` | `random` | cpp-std-sort | 0.183026 | 30000 | 0.0000060000 |

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
