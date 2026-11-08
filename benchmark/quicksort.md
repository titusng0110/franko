# Quicksort benchmark matrix: Franko vs C++

**10,000,000 `int64` keys per dataset · 4 datasets · 5 measured rounds per cell · 2026-11-08**

This report compares Franko and C++ across four input distributions. It
covers only maximum release builds: Franko `-Omax` and C++ `-O3`.

The four datasets are:

- **`0-9`:** values uniform in `0..9`, i.e. 10 distinct values.
- **`1pct`:** values modulo 100,000, i.e. about 1% of `N` distinct.
- **`10pct`:** values modulo 1,000,000, i.e. about 10% of `N` distinct.
- **`random`:** full 63-bit random values, effectively all distinct.

Each dataset is generated internally; there is no bulk input or output.
The sort then performs one checksum/verification pass.

The short version:

- On `random`, Franko in-place is **0.690 s**, **1.10x slower** than
  `std::sort`; on `10pct`, **0.631 s**, **1.03x slower**; on `1pct`,
  **0.588 s**, **1.12x slower**.
- On `0-9`, the adaptive DNF path makes Franko in-place the fastest
  build at **0.144 s**, **1.49x faster** than `std::sort`.
- The index sort is 2.2–2.4x slower than in-place on `1pct`, `10pct`,
  and `random`, and 1.65x slower on `0-9`.
- Franko code generation is at parity with hand-written C++ on the same
  algorithm: within roughly ±8%, and within 2% on most cases.
- Franko binaries are **924–928 KB** because jemalloc is statically
  linked; the C++ binaries are **154 KB**.

---

## 1. Bottom line

| dataset | fastest build | Franko in-place | `std::sort` | Franko in-place vs std |
| --- | --- | ---: | ---: | ---: |
| `0-9` | `franko-inplace`, **0.144 s** | 0.144 s | 0.214 s | **1.49x faster** |
| `1pct` | `cpp-std-sort`, 0.523 s | 0.588 s | 0.523 s | **1.12x slower** |
| `10pct` | `cpp-std-sort`, 0.611 s | 0.631 s | 0.611 s | **1.03x slower** |
| `random` | `cpp-std-sort`, 0.627 s | 0.690 s | 0.627 s | **1.10x slower** |

Key points:

- **Duplicate-heavy data is the Franko win.** The `0-9` case selects DNF
  and beats `std::sort` by 1.49x.
- **Mostly-distinct data favors Lomuto and `std::sort`.** The gap is
  1.03x on `10pct` and 1.10x on `random`.
- **The index sort pays for its indirection.** It is 2.2–2.4x slower than
  in-place on the three high-cardinality datasets.
- **Franko code generation is competitive.** Franko is within 2% of the
  hand-written C++ port on most dataset/mode combinations, with the
  largest gap at 8.5% (`1pct` in-place).
- **Binary size is allocator-dominated.** The Franko builds are about
  6.0x larger because `-Omax` statically links jemalloc.

---

## 2. What was measured

### 2.1 Workload and datasets

All five programs generate the same splitmix64 stream:

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

For `N = 10,000,000`, `1pct` draws from 100,000 possible values and
`10pct` from 1,000,000. Frequencies are uniform within each dataset.

Each program sorts the values, performs one verification and checksum
pass, and prints only:

```text
sorted ok
checksum <value>
```

There is no bulk input or output of the 10,000,000 keys. Generation, the
adaptive sample, sorting, and verification are all included in the
measured wall clock.

### 2.2 The five implementations

| Program | What it is | Storage |
| --- | --- | --- |
| `quicksort_index_franko.fr` | Franko `-Omax`, indirect index sort | Data + index array + permutation buffer |
| `quicksort_inplace_franko.fr` | Franko `-Omax`, in-place value sort | Data only |
| `quicksort_index_cpp.cpp` | Hand-written C++ `-O3` port of the index sort | Data + index array + permutation buffer |
| `quicksort_inplace_cpp.cpp` | Hand-written C++ `-O3` port of the in-place sort | Data only |
| `quicksort_std_cpp.cpp` | C++ `-O3`, `std::sort` | Data only |

The two index programs sort a `uint32` index array `0..N-1` by comparing
`values[order[i]]`. Only indices move during the sort. The final O(N)
pass applies the permutation:

```franko
moved[i] = values[order[i]]
values[i] = moved[i]
```

The in-place programs compare and swap the `int64` values directly.

### 2.3 Adaptive algorithm

All Franko variants and both hand-written C++ ports use the same
adaptive algorithm:

- **Random pivot table:** 128 hardcoded 32-bit values in `RANDOM_PIVOTS`.
  A global `pivot_cursor` advances once per partition.
- **Division-free pivot selection:** the 32-bit sample is reduced to
  `0..count-1` with a 64-bit multiply-high:
  ```text
  (sample * count) >> 32
  ```
  This works for any `count` up to `0xFFFFFFFF`.
- **Read-only sortedness check:** before sorting, one read-only scan
  checks whether the range is already sorted. If so, quicksort is
  skipped. The check performs no writes.
- **Adaptive partition choice:** the preflight takes a 0.1% stratified
  sample of data indices. Sample offsets are derived from
  `RANDOM_PIVOTS` with a cheap bitwise mix. The sample buffer stores
  `uint32` indices, not 64-bit values, so it uses 4 bytes per sample.
  The sample is sorted with a local heapsort that compares values
  through the indices. Equal runs are counted and the average
  duplicate-group size is estimated as:
  ```text
  m_hat = collisions * N / (k * (k - 1) / 2)
  ```
  If `m_hat > 30`, the sort uses a Dutch national flag partition
  (`< pivot`, `== pivot`, `> pivot`); otherwise it uses Lomuto. DNF's
  equal block is final and is not passed to either recursive call.
- **Sorting-network base cases:** subproblems with at most 5 elements
  finish in the standard optimal-size networks for 2 through 5 inputs.

The `m_hat > 30` rule selects DNF for `0-9` and `1pct`, and Lomuto for
`10pct` and `random`.

### 2.4 Build matrix

| Group | Optimization / runtime | Flags |
| --- | --- | --- |
| Franko `-Omax` | `-O3`, static jemalloc, unchecked | `FRANKO_USE_JEMALLOC=1`, `FRANKO_BOUNDS_UNCHECKED=1` |
| C++ `-O3` | `-O3`, system allocator | `g++ -O3 -std=c++17`, static libstdc++/libgcc |

Only these two build modes are covered. No sanitizer or checked build is
included in this report.

### 2.5 Timing methodology

The harness is `benchmark/quicksort_benchmark.py`:

```bash
uv run python benchmark/quicksort_benchmark.py 5 --cpu 2
```

- All five binaries are built before any timing.
- Each binary gets one untimed warm-up per dataset.
- Then 5 measured rounds per dataset. Within each round the five modes
  run in randomized order (fixed seed), and the process is pinned to
  CPU 2.
- Timing is whole-process wall clock with `time.perf_counter()`,
  including generation, the adaptive sample, sorting, the final
  permutation for the index variants, and verification/checksum.
- Every mode's stdout is checked per dataset. All five modes must agree
  within each dataset.

This run used `g++ 15.2.0` under WSL2 on a 12th-gen Intel i5-12600K.
Absolute times are host-sensitive; comparisons within this report are
the authoritative ones.

---

## 3. Results

### 3.1 Mean seconds

| dataset | franko-index | franko-inplace | cpp-index | cpp-inplace | cpp-std-sort |
| --- | ---: | ---: | ---: | ---: | ---: |
| `0-9` | 0.238 | **0.144** | 0.252 | 0.145 | 0.214 |
| `1pct` | 1.350 | 0.588 | 1.320 | 0.542 | **0.523** |
| `10pct` | 1.453 | 0.631 | 1.459 | 0.628 | **0.611** |
| `random` | 1.515 | 0.690 | 1.520 | 0.688 | **0.627** |

### 3.2 Slowdown vs `std::sort`

| dataset | franko-index | franko-inplace | cpp-index | cpp-inplace |
| --- | ---: | ---: | ---: | ---: |
| `0-9` | 1.11x | **0.67x** | 1.17x | **0.68x** |
| `1pct` | 2.58x | 1.12x | 2.52x | 1.04x |
| `10pct` | 2.38x | 1.03x | 2.39x | 1.03x |
| `random` | 2.42x | 1.10x | 2.42x | 1.10x |

A value below `1.00x` means the build is faster than `std::sort`.

### 3.3 Detailed statistics

| dataset | mode | mean | median | min | max | SD | CV |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `0-9` | franko-index | 0.238 | 0.237 | 0.227 | 0.251 | 0.009 | 3.6% |
| `0-9` | franko-inplace | 0.144 | 0.145 | 0.141 | 0.145 | 0.002 | 1.4% |
| `0-9` | cpp-index | 0.252 | 0.251 | 0.244 | 0.261 | 0.006 | 2.4% |
| `0-9` | cpp-inplace | 0.145 | 0.144 | 0.143 | 0.149 | 0.003 | 2.0% |
| `0-9` | cpp-std-sort | 0.214 | 0.214 | 0.210 | 0.218 | 0.003 | 1.5% |
| `1pct` | franko-index | 1.350 | 1.354 | 1.335 | 1.360 | 0.011 | 0.8% |
| `1pct` | franko-inplace | 0.588 | 0.587 | 0.575 | 0.597 | 0.008 | 1.4% |
| `1pct` | cpp-index | 1.320 | 1.319 | 1.312 | 1.335 | 0.009 | 0.7% |
| `1pct` | cpp-inplace | 0.542 | 0.543 | 0.532 | 0.551 | 0.009 | 1.6% |
| `1pct` | cpp-std-sort | 0.523 | 0.526 | 0.513 | 0.531 | 0.007 | 1.4% |
| `10pct` | franko-index | 1.453 | 1.449 | 1.445 | 1.465 | 0.008 | 0.6% |
| `10pct` | franko-inplace | 0.631 | 0.631 | 0.622 | 0.645 | 0.009 | 1.4% |
| `10pct` | cpp-index | 1.459 | 1.447 | 1.442 | 1.492 | 0.023 | 1.6% |
| `10pct` | cpp-inplace | 0.628 | 0.627 | 0.622 | 0.638 | 0.006 | 1.0% |
| `10pct` | cpp-std-sort | 0.611 | 0.607 | 0.603 | 0.622 | 0.008 | 1.3% |
| `random` | franko-index | 1.515 | 1.511 | 1.506 | 1.536 | 0.012 | 0.8% |
| `random` | franko-inplace | 0.690 | 0.687 | 0.686 | 0.699 | 0.006 | 0.8% |
| `random` | cpp-index | 1.520 | 1.524 | 1.509 | 1.529 | 0.010 | 0.6% |
| `random` | cpp-inplace | 0.688 | 0.683 | 0.678 | 0.713 | 0.014 | 2.1% |
| `random` | cpp-std-sort | 0.627 | 0.623 | 0.619 | 0.640 | 0.009 | 1.4% |

### 3.4 Binary size

| build | bin (KB) | relative to C++ |
| --- | ---: | ---: |
| franko-index | 928 | **6.0x** |
| franko-inplace | 924 | **6.0x** |
| cpp-index | 154 | 1.00x |
| cpp-inplace | 154 | 1.00x |
| cpp-std-sort | 154 | 1.00x |

The Franko binaries are larger because `-Omax` statically links jemalloc.
Both sides link libstdc++/libgcc statically, so the remaining difference
is largely the allocator runtime.

### 3.5 Correctness

All five programs produced identical output within each dataset:

| dataset | checksum |
| --- | ---: |
| `0-9` | 44990029 |
| `1pct` | 500019587679 |
| `10pct` | 4999269187679 |
| `random` | 5946973223987570847 |

The checksum is the wrapping sum of the sorted `int64` values cast to
`uint64`.

---

## 4. Analysis

### 4.1 The adaptive estimator works as intended

The collision estimator selects the partition scheme per dataset:

| dataset | estimated duplicate group | partition |
| --- | ---: | --- |
| `0-9` | enormous | **DNF** |
| `1pct` | ~100 | **DNF** |
| `10pct` | ~10 | Lomuto |
| `random` | ~1 | Lomuto |

The `0-9` result is the payoff: Franko in-place runs in **0.144 s**,
which is **1.49x faster** than `std::sort` at 0.214 s. The same input
would be catastrophic for Lomuto, because each duplicate group would be
peeled one pivot at a time.

The `1pct` case also selects DNF. Franko in-place is 0.588 s versus
`std::sort` at 0.523 s, so DNF is close but no longer faster than
`std::sort`. At `10pct` and `random`, the estimator selects Lomuto and
the gap to `std::sort` settles at 1.03–1.10x.

### 4.2 Index indirection is the dominant index cost

The index variants are slower than the in-place variants in every
dataset:

| dataset | Franko index / in-place | C++ index / in-place |
| --- | ---: | ---: |
| `0-9` | 1.65x | 1.74x |
| `1pct` | 2.30x | 2.44x |
| `10pct` | 2.30x | 2.32x |
| `random` | 2.20x | 2.21x |

The index sort does more work by design:

- it allocates and initializes a 40 MB `uint32` index array for 10M keys;
- every comparison follows an extra indirection through `order[i]`;
- the final O(N) permutation reads and writes the 80 MB data array
  through a temporary buffer.

The in-place sort touches only the 80 MB data array and compares values
directly. The index penalty is smaller on `0-9` because DNF makes the
in-place path especially fast, so the fixed index overhead is a larger
relative fraction.

### 4.3 Franko versus hand-written C++

Comparing the same algorithm across languages:

| dataset | index | in-place |
| --- | ---: | ---: |
| `0-9` | 0.94x | 0.99x |
| `1pct` | 1.02x | 1.08x |
| `10pct` | 1.00x | 1.00x |
| `random` | 1.00x | 1.00x |

A value below `1.00x` means Franko is faster. The generated C++ is at
parity with careful hand-written C++ for most cases; the largest gap is
8.5% on the `1pct` in-place case, and Franko is 5.6% faster on the `0-9`
index case. This is competitive code generation, not a large penalty.

### 4.4 The algorithm versus `std::sort`

`std::sort` is the fastest build on `1pct`, `10pct`, and `random`:

- `1pct`: in-place Franko 1.12x slower, in-place C++ 1.04x slower;
- `10pct`: in-place Franko 1.03x slower, in-place C++ 1.03x slower;
- `random`: in-place Franko 1.10x slower, in-place C++ 1.10x slower.

On `0-9`, the ordering flips: in-place Franko is **1.49x faster** and
in-place C++ is **1.48x faster** than `std::sort`, because DNF removes
the duplicate groups from the recursion entirely. `std::sort` handles
duplicates well, but it cannot match a partition scheme that groups all
equal keys at once.

### 4.5 Binary size

The Franko `-Omax` binaries are about **6.0x** larger than the C++
references. The sort code itself is tiny; the difference is primarily
the static jemalloc runtime. If binary size matters more than allocator
performance, a non-jemalloc Franko build would be the relevant
comparison, but that build mode is outside this report.

### 4.6 Noise

The coefficient of variation is **0.6–3.6%** across all 20 cells. Most
cells are below 2%. The largest spreads are `0-9/franko-index` (3.6%),
`0-9/cpp-index` (2.4%), and `random/cpp-inplace` (2.1%). Differences
below about 4% should be read as small effects; the 1.49x, 2.2–2.4x,
and 0.67x results are well outside that band.

---

## 5. Limitations

1. **Five measured rounds per cell.** The matrix is wide, so each cell
   gets 5 rounds rather than the 10 used in the single-dataset report.
   The coefficients of variation are low enough for the headline
   comparisons, but small differences should be treated cautiously.
2. **Synthetic datasets.** `1pct` and `10pct` are uniform-frequency
   datasets with 100,000 and 1,000,000 distinct values. Real data can
   have skewed frequencies, which changes both the estimator and the
   partition behavior.
3. **Adaptive estimator is a heuristic.** The 0.1% sample and the
   `m_hat > 30` cutoff are tuned for non-adversarial data. Periodic or
   adversarial inputs can evade the sample; this is not a worst-case
   guarantee.
4. **`std::sort` is not a plain quicksort.** It is introsort with
   insertion-sort and heapsort fallbacks, so it is a strong reference,
   not an exact algorithm match.
5. **The index variants do extra work by design.** They are not
   apples-to-apples with `std::sort`; they are the cost of producing a
   sorted permutation while leaving the data array untouched during the
   sort.
6. **Whole-process timing.** Generation, the adaptive sample, sorting,
   the final permutation, verification, and checksum are one number.
   Generation and verification are identical workloads in both
   languages, but they are not zero.
7. **One machine.** The run is under WSL2 on a 12th-gen i5-12600K with
   CPU pinning. Absolute times are host-sensitive.
8. **Only maximum release builds.** This report intentionally covers
   only Franko `-Omax` and C++ `-O3`. Checked/sanitizer builds are not
   included.
9. **Binary size includes static runtimes.** The sizes are not directly
   comparable to dynamically linked builds.

---

## 6. Reproduce

```bash
uv run python benchmark/quicksort_benchmark.py 5 --cpu 2
```

The harness builds and runs:

- `benchmark/quicksort_index_franko.fr`
- `benchmark/quicksort_inplace_franko.fr`
- `benchmark/quicksort_index_cpp.cpp`
- `benchmark/quicksort_inplace_cpp.cpp`
- `benchmark/quicksort_std_cpp.cpp`

The dataset selector is the first command-line argument:

| argument | dataset |
| --- | --- |
| `3` | `0-9` |
| `2` | `1pct` |
| `1` | `10pct` |
| `0` | `random` |

Generated `.cpp` and `.out` files are build artifacts and are ignored by
git.

---

## Appendix A: raw measured runs

Five measured rounds per dataset and mode. The mode order within each
round was randomized.

| dataset | mode | r1 | r2 | r3 | r4 | r5 | mean | bin (KB) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `0-9` | franko-index | 0.237 | 0.239 | 0.237 | 0.251 | 0.227 | 0.238 | 928 |
| `0-9` | franko-inplace | 0.145 | 0.145 | 0.141 | 0.145 | 0.142 | 0.144 | 924 |
| `0-9` | cpp-index | 0.261 | 0.251 | 0.244 | 0.250 | 0.252 | 0.252 | 154 |
| `0-9` | cpp-inplace | 0.148 | 0.149 | 0.144 | 0.143 | 0.143 | 0.145 | 154 |
| `0-9` | cpp-std-sort | 0.217 | 0.218 | 0.212 | 0.214 | 0.210 | 0.214 | 154 |
| `1pct` | franko-index | 1.358 | 1.354 | 1.335 | 1.341 | 1.360 | 1.350 | 928 |
| `1pct` | franko-inplace | 0.597 | 0.594 | 0.586 | 0.587 | 0.575 | 0.588 | 924 |
| `1pct` | cpp-index | 1.335 | 1.312 | 1.316 | 1.319 | 1.320 | 1.320 | 154 |
| `1pct` | cpp-inplace | 0.550 | 0.551 | 0.532 | 0.534 | 0.543 | 0.542 | 154 |
| `1pct` | cpp-std-sort | 0.531 | 0.526 | 0.519 | 0.528 | 0.513 | 0.523 | 154 |
| `10pct` | franko-index | 1.465 | 1.449 | 1.449 | 1.455 | 1.445 | 1.453 | 928 |
| `10pct` | franko-inplace | 0.645 | 0.627 | 0.631 | 0.632 | 0.622 | 0.631 | 924 |
| `10pct` | cpp-index | 1.475 | 1.442 | 1.447 | 1.492 | 1.442 | 1.459 | 154 |
| `10pct` | cpp-inplace | 0.638 | 0.628 | 0.625 | 0.622 | 0.627 | 0.628 | 154 |
| `10pct` | cpp-std-sort | 0.622 | 0.605 | 0.607 | 0.603 | 0.616 | 0.611 | 154 |
| `random` | franko-index | 1.536 | 1.506 | 1.514 | 1.511 | 1.508 | 1.515 | 928 |
| `random` | franko-inplace | 0.699 | 0.687 | 0.686 | 0.690 | 0.687 | 0.690 | 924 |
| `random` | cpp-index | 1.526 | 1.510 | 1.509 | 1.529 | 1.524 | 1.520 | 154 |
| `random` | cpp-inplace | 0.713 | 0.678 | 0.683 | 0.680 | 0.685 | 0.688 | 154 |
| `random` | cpp-std-sort | 0.640 | 0.619 | 0.623 | 0.620 | 0.633 | 0.627 | 154 |

---

## Appendix B: files

| File | Role |
| --- | --- |
| `benchmark/quicksort_benchmark.py` | builds and times the 4-dataset matrix |
| `benchmark/quicksort_index_franko.fr` | Franko `-Omax`, indirect index sort |
| `benchmark/quicksort_inplace_franko.fr` | Franko `-Omax`, in-place value sort |
| `benchmark/quicksort_index_cpp.cpp` | C++ `-O3`, same index algorithm |
| `benchmark/quicksort_inplace_cpp.cpp` | C++ `-O3`, same in-place algorithm |
| `benchmark/quicksort_std_cpp.cpp` | C++ `-O3`, `std::sort` reference |
| `benchmark/quicksort.md` | This report |
