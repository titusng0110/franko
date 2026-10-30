# AVL benchmark: 2M inserts + 2M deletes, Franko vs C++

## Workloads

**Franko (`benchmark/AVL_franko.fr`).** An AVL tree holding 2,000,000
`int32` keys. Nodes live in one heap dynamic array (`[Node]`,
`init(2000000)`); slot indices come from `std::NextAllocator`
(`import "franko/pool"`), the same index-pool design as
`programs/redblack.fr`. Insert and delete are recursive and rebalance
with single/double rotations; links are `uint32` slot indices with
`0xFFFFFFFF` as NIL.

**C++ (`benchmark/AVL_cpp.cpp`).** The same workload on `std::set`:
insert the same 2M keys in the same order, print the size, erase all
keys in the same order, print empty + size. `std::set` manages its own
nodes, so there is no pool to mimic — only the insert / verify /
delete / verify flow.

Key pattern, both programs (all 2,000,000 keys distinct, covering
`[-999999, 1000000]` exactly once):

```text
-999999, 1000000, -999998, 999999, -999997, 999998, ...
```

even iterations insert `-999999 + i/2`, odd iterations insert
`1000000 - i/2`. Deletes replay the same pattern in the same order.

Correctness was established first on a 200-key Franko version with a
full in-order verification walk (count = 200, order intact, then empty
with the pool drained). The benchmark files keep only O(1) tail
output, so the timed region is pure insert + delete work.

## How to run

```bash
uv run python benchmark/benchmark.py [reps]   # default reps = 10
```

Builds 3 Franko modes with `compile.py` and 2 C++ modes with plain
`g++`, runs each test `reps` times timed with `time.perf_counter()`,
asserts stdout agrees within each group (correctness cross-check), and
prints the table below. Headline number is the average.

| test | build |
| --- | --- |
| default | Franko, `-O0` + ASan + system allocator |
| -O | Franko, `-O3` + jemalloc, checked runtime |
| -Omax | Franko, `-O3` + jemalloc, unchecked runtime |
| cpp-O0 | `g++ -O0 -std=c++14` + static libstdc++/libgcc |
| cpp-O3 | `g++ -O3 -std=c++14` + static libstdc++/libgcc |

## Results

Machine: 12th Gen Intel i5-12600K, 16 CPUs, WSL2 (Linux 6.18),
g++ 15.2.0. Measured 2026-10-30, average of 10 runs.

| test | run1 | run2 | run3 | run4 | run5 | run6 | run7 | run8 | run9 | run10 | avg (s) | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| default | 3.794 | 3.661 | 3.617 | 3.659 | 3.550 | 3.617 | 3.576 | 3.559 | 3.587 | 3.552 | 3.617 | 373 |
| -O | 0.503 | 0.476 | 0.485 | 0.483 | 0.494 | 0.481 | 0.484 | 0.490 | 0.502 | 0.495 | 0.489 | 996 |
| -Omax | 0.408 | 0.402 | 0.399 | 0.400 | 0.395 | 0.404 | 0.399 | 0.397 | 0.395 | 0.386 | 0.399 | 996 |
| cpp-O0 | 1.406 | 1.404 | 1.385 | 1.376 | 1.400 | 1.437 | 1.412 | 1.382 | 1.402 | 1.396 | 1.400 | 302 |
| cpp-O3 | 0.399 | 0.394 | 0.399 | 0.407 | 0.407 | 0.372 | 0.414 | 0.380 | 0.376 | 0.385 | 0.393 | 158 |

Program output (identical in every rep, agrees within each group):

```text
franko: height= 26, empty= 1, used= 0
cpp:    size= 2000000, empty= 1, size= 0
```

Speedups (averages):

- **-O vs default: 7.39x** (`-O3` + jemalloc vs `-O0` + ASan).
- **-Omax vs -O: 1.23x** — the isolated price of bounds checking and
  `unsafe_cast` range checking on a check-heavy workload.
- **-Omax vs default: 9.08x** end to end.
- **cpp-O3 vs cpp-O0: 3.56x**.
- **cpp-O3 vs franko -Omax: 1.01x** — essentially tied.

## Reading the numbers

- **Franko `-Omax` matches handwritten C++** (0.405 s vs 0.399 s).
  The Franko program is an index-based AVL over one contiguous node
  array with a free-list pool: no per-node allocation, cache-friendly
  traversal. `std::set` is a red-black tree doing one heap allocation
  per node. The pool design pays off — generated Franko code runs even
  with hand-written C++ on this workload.
- **Franko default (3.64 s) vs cpp-O0 (1.41 s) is not apples-to-apples:**
  the Franko default build carries full AddressSanitizer
  instrumentation (`-O0` + ASan shadow checks on every access) while
  the C++ `-O0` build has no sanitizer. The ~2.6x gap between them is
  roughly the ASan tax on a pointer-chase workload.
- **The ~1.2x `-O` → `-Omax` gap** isolates the runtime-header check
  overhead (same `-O3`, same jemalloc, only the panics removed) —
  about a fifth of optimized runtime on this workload, which is the
  `-Omax` use case: validate first, strip for speed.
- Height 26 after 2M inserts is consistent with AVL balance
  (1.44·log₂(2M) ≈ 30); `used = 0` confirms every pool slot was
  returned.

## Binary sizes

(`bin (KB)` column above; `benchmark.py` records these automatically
on every run.)

| test | bin (KB) | notes |
| --- | --- | --- |
| default | 373 | no jemalloc archive; ASan runtime links shared |
| -O | 996 | statically linked jemalloc dominates (~600 KB+) |
| -Omax | 996 | byte-identical size to `-O` (different code — verified
by hash — but the difference is absorbed in section alignment under
the jemalloc bulk) |
| cpp-O0 | 302 | static libstdc++/libgcc; `-O0` codegen is bulkier |
| cpp-O3 | 158 | static libstdc++/libgcc (no `-s` strip applied, unlike
the Franko `-O` builds, so a few KB of symbols remain) |

Both sides now bundle their runtimes, so the comparison is fair: the
C++ binaries carry static libstdc++/libgcc (~140 KB over shared), and
the Franko `-O`/`-Omax` binaries carry those plus the static jemalloc
archive. The remaining ~6x size gap (996 vs 158 KB) is essentially the
jemalloc archive — the known price of the bundled-allocator model
(allocator independence: the binary needs no jemalloc on the target
machine).
