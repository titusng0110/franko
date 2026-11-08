# Quicksort benchmark: Franko vs C++

**10,000,000 signed `int64` keys · 10 measured rounds · 2026-11-08**

This report compares Franko and C++ on sorting 10,000,000 `int64`
keys. It covers only maximum release builds: Franko `-Omax` and C++
`-O3`. There are two Franko variants and three C++ references:

- **index sort:** sort a `uint32` index array by `values[order[i]]`, then
  apply the permutation to the data in a final O(N) pass;
- **in-place:** sort the `int64` values directly;
- **`std::sort`:** the plain C++ `<algorithm>` reference.

The short version: Franko's in-place version runs in **0.755 s**,
**1.19x slower** than `std::sort`; the hand-written C++ port of the same
in-place algorithm runs in **0.722 s**, so Franko's generated code is
within **5%** of hand-written C++ on this workload. The index variant is
**2.11x slower** than the in-place Franko variant, so the index
indirection and final permutation dominate the gap. Franko binaries are
**936–948 KB** because jemalloc is statically linked; the C++ binaries
are **154–158 KB**.

---

## 1. Bottom line

| Question | Answer |
| --- | --- |
| Fastest Franko build | `franko-inplace`, **0.755 s** |
| Fastest overall | `cpp-std-sort`, **0.637 s** |
| Franko in-place vs `std::sort` | **1.19x slower** |
| Franko index vs `std::sort` | **2.51x slower** |
| Franko in-place vs C++ same algorithm | **1.05x slower** |
| Franko index vs C++ same algorithm | **1.03x slower** |
| Franko index vs Franko in-place | **2.11x slower** |
| C++ index vs C++ in-place | **2.15x slower** |
| Franko binary size | **936–948 KB** |
| C++ binary size | **154–158 KB** |

The main conclusions:

- The in-place algorithm is close to `std::sort`: **1.13x** slower in
  C++ and **1.19x** slower in Franko.
- Franko's code generation is competitive with hand-written C++ on the
  same algorithm: **1.03–1.05x** slower.
- Sorting an index array instead of values costs about **2.1x** in both
  languages. The final permutation and the extra data structure, not the
  language, explain most of the index-variant gap.
- Franko's larger binary is primarily the statically linked
  jemalloc runtime.

---

## 2. What was measured

### 2.1 Workload

All five programs generate the same 10,000,000 nonnegative `int64` keys
with a splitmix64 stream:

```text
state = 0x9E3779B97F4A7C15
repeat:
    state += 0x9E3779B97F4A7C15
    z = state
    z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9
    z = (z ^ (z >> 27)) * 0x94D049BB133111EB
    z = z ^ (z >> 31)
    value = z & 0x7FFFFFFFFFFFFFFF
```

Each program then sorts the values, performs one verification and
checksum pass, and prints only:

```text
sorted ok
checksum 5946973223987570847
```

There is no bulk input or output of the 10,000,000 keys. Generation,
sorting, and verification are all included in the measured wall clock.

### 2.2 The five implementations

| Program | What it is | Storage |
| --- | --- | --- |
| `quicksort_index_franko.fr` | Franko `-Omax`, indirect index sort | Data + index array + permutation buffer |
| `quicksort_inplace_franko.fr` | Franko `-Omax`, in-place value sort | Data only |
| `quicksort_index_cpp.cpp` | Hand-written C++ `-O3` port of the index sort | Data + index array + permutation buffer |
| `quicksort_inplace_cpp.cpp` | Hand-written C++ `-O3` port of the in-place sort | Data only |
| `quicksort_std_cpp.cpp` | C++ `-O3`, `std::sort` | Data only |

The two index programs sort an index array of `uint32` values `0..N-1` by
comparing `values[order[i]]`. Only indices move during the sort. The
final O(N) pass applies the permutation:

```franko
moved[i] = values[order[i]]
values[i] = moved[i]
```

The in-place programs compare and swap the `int64` values directly.

### 2.3 Algorithm

All Franko variants and both hand-written C++ ports use the same
algorithm:

- **Random pivot table:** 64 hardcoded 32-bit values in `RANDOM_PIVOTS`.
  A global `pivot_cursor` advances once per partition.
- **Division-free pivot selection:** the 32-bit sample is reduced to
  `0..count-1` with a 64-bit multiply-high:
  ```text
  (sample * count) >> 32
  ```
  This works for any `count` up to `0xFFFFFFFF`.
- **Bubble pass:** before sorting, one bubble-sort pass over the range
  doubles as a sortedness check. If no swap occurs, the range is already
  sorted. Otherwise the pass leaves the largest key at the end, and the
  quicksort call skips that slot.
- **Sorting-network base cases:** subproblems with at most 9 elements
  finish in the standard optimal-size networks for 2 through 9 inputs.
- **Lomuto partition** around the sampled pivot.

The compare-exchange and swap bodies in the sorting networks are written
out inline.

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
uv run python benchmark/quicksort_benchmark.py 10 --cpu 2
```

- All five binaries are built before any timing.
- Each binary gets one untimed warm-up.
- Then 10 measured rounds. Within each round the five modes run in a
  randomized order (fixed seed), and the process is pinned to CPU 2.
- Timing is whole-process wall clock with `time.perf_counter()`,
  including generation, sorting, the final permutation for the index
  variants, and the verification/checksum pass.
- Every mode's stdout is checked. All five must print the same
  checksum.

This run used `g++ 15.2.0` under WSL2 on a 12th-gen Intel i5-12600K.
Absolute times are host-sensitive; comparisons within this report are
the authoritative ones.

---

## 3. Results

### 3.1 Time and binary size

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| franko-index | 1.596 | 1.593 | 1.568 | 1.633 | 0.024 | 1.5% | 948 |
| franko-inplace | 0.755 | 0.754 | 0.739 | 0.771 | 0.011 | 1.5% | 936 |
| cpp-index | 1.554 | 1.551 | 1.538 | 1.580 | 0.014 | 0.9% | 158 |
| cpp-inplace | 0.722 | 0.723 | 0.703 | 0.735 | 0.010 | 1.4% | 154 |
| cpp-std-sort | 0.637 | 0.638 | 0.620 | 0.648 | 0.009 | 1.4% | 154 |

### 3.2 Relative times

| comparison | slower factor |
| --- | --- |
| franko-index vs cpp-std-sort | **2.51x** |
| franko-inplace vs cpp-std-sort | **1.19x** |
| cpp-index vs cpp-std-sort | **2.44x** |
| cpp-inplace vs cpp-std-sort | **1.13x** |
| franko-index vs franko-inplace | **2.11x** |
| cpp-index vs cpp-inplace | **2.15x** |
| franko-inplace vs cpp-inplace | **1.05x** |
| franko-index vs cpp-index | **1.03x** |

### 3.3 Relative binary size

| build | bin (KB) | relative to C++ |
| --- | --- | --- |
| franko-index | 948 | **6.2x** |
| franko-inplace | 936 | **6.1x** |
| cpp-index | 158 | 1.03x |
| cpp-inplace | 154 | 1.00x |
| cpp-std-sort | 154 | 1.00x |

The Franko binaries are larger because `-Omax` statically links
jemalloc and libstdc++/libgcc. The C++ references are also statically
linked against libstdc++/libgcc, so the remaining size difference is
largely the allocator runtime.

### 3.4 Correctness

All five programs produced the same output:

```text
sorted ok
checksum 5946973223987570847
```

The checksum is the wrapping sum of the sorted `int64` values cast to
`uint64`.

---

## 4. Analysis

### 4.1 Index indirection is the dominant cost

The index versions are about 2.1x slower than the in-place versions in
both languages:

| comparison | slower factor |
| --- | --- |
| Franko index / Franko in-place | 2.11x |
| C++ index / C++ in-place | 2.15x |

The index sort does more work by design:

- it allocates and initializes a 40 MB `uint32` index array for the 10M
  keys;
- every comparison follows an extra indirection through `order[i]`;
- the final O(N) permutation reads and writes the 80 MB data array
  through a temporary buffer.

The in-place sort touches only the 80 MB data array and compares values
directly. Since both languages show roughly the same 2.1x index
penalty, this is an algorithmic/data-layout cost, not a Franko-specific
one.

### 4.2 Franko code generation is competitive

Comparing the same algorithm across languages:

| comparison | Franko slowdown |
| --- | --- |
| in-place vs C++ in-place | **1.05x** |
| index vs C++ index | **1.03x** |

The generated C++ is within a few percent of careful hand-written C++.
The small differences are close to the run-to-run noise floor, so the
honest reading is parity on this workload.

### 4.3 The algorithm versus `std::sort`

`std::sort` is still the fastest option:

- the same in-place algorithm is 1.13x slower in C++ and 1.19x slower in
  Franko;
- the index variants are 2.4–2.5x slower, but most of that is the
  index/final-permutation overhead, not the quicksort itself.

`std::sort` uses introsort with insertion-sort small cases and a
heapsort fallback, so it has an advantage over a simpler quicksort with
a one-pass bubble check and sorting-network leaves. The gap is modest.

### 4.4 Binary size

The Franko `-Omax` binaries are about **6.1–6.2x** larger than the C++
references. The sort code itself is tiny; the difference is primarily the
static jemalloc runtime. If binary size matters more than allocator
performance, a non-jemalloc Franko build would be the relevant
comparison, but that build mode is outside this report.

### 4.5 Noise

The coefficient of variation is **0.9–1.5%** across all five modes.
Differences below about 3% should be treated cautiously; the 1.03–1.05x
code-generation comparisons are near that floor. The 1.19x and 2.1–2.5x
comparisons are well outside it.

---

## 5. Limitations

1. **One workload, one input distribution.** All keys come from the same
   splitmix64 stream. Sorted, reverse-sorted, duplicate-heavy, and
   adversarial inputs would exercise different pivot and partition
   behavior.
2. **`std::sort` is not a plain quicksort.** It is introsort with
   insertion-sort and heapsort fallbacks, so it is a strong reference,
   not an exact algorithm match.
3. **The index variants do extra work by design.** They are not
   apples-to-apples with `std::sort`; they are the cost of producing a
   sorted permutation while leaving the data array untouched during the
   sort.
4. **Whole-process timing.** Generation, sorting, the final permutation,
   verification, and checksum are one number. Generation and
   verification are identical workloads in both languages, but they are
   not zero.
5. **One machine.** The run is under WSL2 on a 12th-gen i5-12600K with
   CPU pinning. Absolute times are host-sensitive.
6. **Only maximum release builds.** This report intentionally covers
   only Franko `-Omax` and C++ `-O3`. Checked/sanitizer builds are not
   included.
7. **Binary size includes static runtimes.** The sizes are not directly
   comparable to dynamically linked builds.

---

## 6. Reproduce

```bash
uv run python benchmark/quicksort_benchmark.py 10 --cpu 2
```

The harness builds and runs:

- `benchmark/quicksort_index_franko.fr`
- `benchmark/quicksort_inplace_franko.fr`
- `benchmark/quicksort_index_cpp.cpp`
- `benchmark/quicksort_inplace_cpp.cpp`
- `benchmark/quicksort_std_cpp.cpp`

Generated `.cpp` and `.out` files are build artifacts and are ignored by
git.

---

## Appendix A: raw measured runs

The 10 measured rounds per mode, in the order recorded. The mode order
within each round was randomized.

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| franko-index | 1.568 | 1.598 | 1.633 | 1.605 | 1.622 | 1.623 | 1.588 | 1.569 | 1.578 | 1.574 | 1.596 | 948 |
| franko-inplace | 0.746 | 0.753 | 0.765 | 0.771 | 0.764 | 0.754 | 0.743 | 0.748 | 0.739 | 0.766 | 0.755 | 936 |
| cpp-index | 1.543 | 1.559 | 1.580 | 1.554 | 1.549 | 1.560 | 1.546 | 1.574 | 1.538 | 1.540 | 1.554 | 158 |
| cpp-inplace | 0.703 | 0.725 | 0.734 | 0.726 | 0.735 | 0.720 | 0.731 | 0.710 | 0.716 | 0.721 | 0.722 | 154 |
| cpp-std-sort | 0.620 | 0.634 | 0.648 | 0.642 | 0.647 | 0.641 | 0.638 | 0.638 | 0.635 | 0.624 | 0.637 | 154 |

---

## Appendix B: files

| File | Role |
| --- | --- |
| `benchmark/quicksort_benchmark.py` | builds and times all five modes |
| `benchmark/quicksort_index_franko.fr` | Franko `-Omax`, indirect index sort |
| `benchmark/quicksort_inplace_franko.fr` | Franko `-Omax`, in-place value sort |
| `benchmark/quicksort_index_cpp.cpp` | C++ `-O3`, same index algorithm |
| `benchmark/quicksort_inplace_cpp.cpp` | C++ `-O3`, same in-place algorithm |
| `benchmark/quicksort_std_cpp.cpp` | C++ `-O3`, `std::sort` reference |
| `benchmark/quicksort.md` | This report |
