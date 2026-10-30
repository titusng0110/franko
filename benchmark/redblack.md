# Red-black benchmark: 2M inserts + 2M deletes, Franko vs C++

## Workloads

Both programs implement the same algorithm — a red-black tree — over
the same workload, so this compares generated Franko code against
hand-written C++ on equal terms, not just build flags against each
other.

**Franko (`benchmark/redblack_franko.fr`).** Iterative CLRS insertion
plus CLRS deletion (transplant, successor splice with parent tracking,
4-case × 2-side delete fixup with NIL-safe sibling handling). Nodes
live in one heap dynamic array (`[Node]`, `init(2000000)`); slot
indices come from `std::NextAllocator` (`import "franko/pool"`).
Links (`left`, `right`, `parent`) are `uint32` slot indices with
`0xFFFFFFFF` as NIL; colors are a `byte` field. An in-order audit walk
after inserts checks BST order, node count, key sum, red-red
violations, and black-height consistency.

**C++ (`benchmark/redblack_cpp.cpp`).** The same workload on
`std::set`, which is itself a red-black tree: insert the same 2M keys
in the same order, walk in order accumulating count + sum (mirrors the
Franko audit walk), erase all keys in the same order, print empty +
size. `std::set` manages its own nodes, so there is no pool to mimic.

Key pattern, both programs (all 2,000,000 keys distinct, covering
`[-999999, 1000000]` exactly once):

```text
-999999, 1000000, -999998, 999999, -999997, 999998, ...
```

even iterations insert `-999999 + i/2`, odd iterations insert
`1000000 - i/2`. Deletes replay the same pattern in the same order.

## How to run

```bash
uv run python benchmark/benchmark.py [reps]   # default reps = 10
```

Builds 3 Franko modes with `compile.py` and 3 C++ modes with `g++`,
runs each test `reps` times timed with `time.perf_counter()`, asserts
stdout agrees within each group (correctness cross-check), and prints
the table below. Headline number is the average.

| test | build |
| --- | --- |
| default | Franko, `-O1` + ASan + system allocator |
| -O | Franko, `-O3` + jemalloc, checked runtime |
| -Omax | Franko, `-O3` + jemalloc, unchecked runtime |
| cpp-O1-asan | `g++ -O1` + ASan + static libstdc++/libgcc |
| cpp-O1 | `g++ -O1` + static libstdc++/libgcc |
| cpp-O3 | `g++ -O3` + static libstdc++/libgcc |

## Results

Machine: 12th Gen Intel i5-12600K, 16 CPUs, WSL2 (Linux 6.18),
g++ 15.2.0. Measured 2026-10-30, average of 10 runs.

| test | run1 | run2 | run3 | run4 | run5 | run6 | run7 | run8 | run9 | run10 | avg (s) | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| default | 0.502 | 0.499 | 0.528 | 0.499 | 0.505 | 0.503 | 0.492 | 0.490 | 0.504 | 0.494 | 0.502 | 436 |
| -O | 0.373 | 0.360 | 0.353 | 0.359 | 0.356 | 0.354 | 0.350 | 0.355 | 0.359 | 0.352 | 0.357 | 1000 |
| -Omax | 0.351 | 0.337 | 0.338 | 0.350 | 0.333 | 0.342 | 0.331 | 0.353 | 0.345 | 0.336 | 0.342 | 996 |
| cpp-O1-asan | 1.053 | 1.014 | 1.026 | 0.977 | 1.030 | 0.989 | 1.014 | 0.980 | 0.982 | 1.009 | 1.007 | 245 |
| cpp-O1 | 0.434 | 0.442 | 0.453 | 0.442 | 0.463 | 0.438 | 0.443 | 0.442 | 0.452 | 0.437 | 0.445 | 154 |
| cpp-O3 | 0.384 | 0.404 | 0.396 | 0.393 | 0.412 | 0.401 | 0.413 | 0.417 | 0.408 | 0.391 | 0.402 | 158 |

Program output (identical in every rep, agrees within each group;
the key sums agree across groups too):

```text
franko: count= 2000000, sum= 1000000, bad= 0, bh= 20, empty= 1, used= 0
cpp:    count= 2000000, sum= 1000000, bad= 0, empty= 1, size= 0
```

Speedups (averages):

- **-O vs default: 1.40x** (`-O3` + jemalloc vs `-O1` + ASan).
- **-Omax vs -O: 1.05x** — bounds checks are nearly free on this
  workload (see below).
- **-Omax vs default: 1.47x** end to end.
- **cpp-O3 vs cpp-O1: 1.11x** — the optimizer finds little beyond
  `-O1` on this pointer-chase workload.
- **cpp-O1 vs cpp-O1-asan: 2.27x** — the ASan tax at `-O1` in
  hand-written C++.
- **franko default vs cpp-O1-asan: 2.01x** (Franko faster) — the
  apples-to-apples sanitizer comparison: same `-O1`, same ASan, same
  system allocator, and the checked Franko build runs in half the
  time of `std::set` under ASan.
- **franko -Omax vs cpp-O3: 1.18x** (Franko faster) — generated code
  beats hand-written C++ on the same algorithm.

## Reading the numbers

- **Same algorithm, Franko wins every pairing.** `-Omax` beats
  `cpp-O3` (0.342 vs 0.402), `-O` beats `cpp-O3` (0.357 vs 0.402),
  and default beats `cpp-O1-asan` twofold (0.502 vs 1.007). The
  structural reason is the storage design: Franko nodes sit in one
  contiguous array with a free-list pool (no per-node allocation,
  index links, cache-friendly audit walk), while `std::set` does one
  heap allocation per node and chases pointers everywhere, including
  its audit iteration. The allocator and layout dominate the
  algorithm here.
- **The sanitizer comparison is the sharpest one.** The C++ ASan tax
  at `-O1` is 2.27x, yet the Franko default build — ASan *plus*
  array bounds checks — runs at half the time of ASan-instrumented
  `std::set`. Bounds checks cost less than per-node allocation
  overhead.
- **`-Omax` vs `-O` is only 1.05x.** The red-black insert/delete
  paths do few checked array accesses per unit of work, and `-O3`
  predicts the remaining check branches well — there is almost
  nothing left for `-Omax` to strip on this shape of code. The
  `-Omax` flag pays off on check-denser workloads instead.
- Black-height 20 after 2M inserts is consistent with red-black
  balance (≤ 2·log₂(2M) ≈ 42); `used = 0` confirms every pool slot
  was returned. Both programs agree on count (2M) and key sum
  (1,000,000).

## Binary sizes

(`bin (KB)` column above; `benchmark.py` records these automatically
on every run.)

| test | bin (KB) | notes |
| --- | --- | --- |
| default | 436 | no jemalloc archive; ASan runtime links shared |
| -O | 1000 | statically linked jemalloc dominates |
| -Omax | 996 | same link as `-O`; check removal barely moves size |
| cpp-O1-asan | 245 | static libstdc++/libgcc; ASan runtime shared |
| cpp-O1 | 154 | static libstdc++/libgcc |
| cpp-O3 | 158 | static libstdc++/libgcc (no `-s` strip applied, unlike
the Franko `-O` builds, so a few KB of symbols remain) |

Both sides bundle their runtimes. The Franko `-O`/`-Omax` premium
(~6x over the C++ binaries) is the static jemalloc archive — the
known price of allocator independence: the binary needs no jemalloc
on the target machine.
