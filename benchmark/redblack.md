# Red-black benchmark: Franko vs C++

**2,000,000 inserts + 2,000,000 deletes · 10 measured rounds per build · 2026-11-04**

This report compares Franko with C++ on the same red-black tree workload,
with two Franko allocator variants and two C++ references. The Franko
programs are compiled by `compile.py` to C++14 and then by `g++`; the
C++ references are compiled directly by `g++`.

The short version: against idiomatic C++ `std::set`, Franko is **1.21x
faster at maximum release optimization** and **1.73x faster under
matched AddressSanitizer builds**. Against a hand-written C++ port of
the *same* array-pool tree, Franko is **1.07x faster at max release**
and its checked `-O` build is **1.03x slower** than the C++ control's
unchecked `-O3` build. Under ASan, the unchecked C++ pool is **1.22x
faster** than Franko's checked default build. Most of the gap to
`std::set` is the array-pool design, not the language or code generator.

The report also compares `SparseSetAllocator`, a second Franko-only pool
implementation. It is essentially at parity with the existing
`NextAllocator` on this workload: 1.06x slower in the ASan default
build, equal in checked `-O`, and 1.01x slower in `-Omax`.

`-march=native` is again not a free win: it is flat to slightly
negative for most Franko builds, regresses the C++ pool `-O1` build by
10.1%, and regresses the `std::set` ASan build by 22.2%.

---

## 1. Bottom line

| Question | Answer |
| --- | --- |
| Fastest portable build | Franko `-Omax`, **0.330 s** (`sparse-Omax` 0.333 s) |
| Fastest native build | Franko `-Omax-native`, **0.332 s** |
| Fastest sparse-set build | Franko `sparse-Omax`, **0.333 s** |
| Franko `-Omax` vs C++ `std::set -O3` | **1.21x faster** |
| Franko `-Omax` vs same-data-structure C++ `-O3` | **1.07x faster** |
| Franko `-O` vs same-data-structure C++ `-O3` | **1.03x slower** |
| Sanitizer-matched, portable | **1.73x faster** than ASan `std::set` |
| Sanitizer-matched, same data structure | **1.22x slower** than the C++ control |
| Franko `default` → `-Omax` | **1.75x faster** |
| `SparseSetAllocator` vs `NextAllocator` | parity (within 1.06x) |
| `-march=native` overall | no consistent win; regressions up to **+22.2%** |

Two defensible headline claims:

- **Against `std::set`:** Franko's array-pool implementation is
  **1.21x faster** at maximum release optimization and **1.73x faster**
  when both sides use the same `-O1 + ASan + system allocator` setup.
- **Against the same array-pool design in C++:** Franko is within
  roughly ±7% of hand-written C++ at release. Under ASan, the checked
  Franko default build is **1.22x slower** than the unchecked C++
  control, while still **1.73x faster** than `std::set`. The large
  advantage over `std::set` comes from storage/allocation, not from a
  fundamentally faster red-black algorithm or a huge code-generation
  advantage.

The rest of this report shows the raw numbers and how those
conclusions follow.

---

## 2. What was measured

### 2.1 Workload

All four programs execute the same insert/delete workload:

1. Insert **2,000,000 distinct `int32` keys**. The key pattern walks
   inward from both ends of `[-999999, 1000000]`:
   `-999999, 1000000, -999998, 999999, ...`
2. Walk the tree in order, accumulating count and key sum. Franko and
   the C++ pool control also audit BST order, red-red violations, and
   black-height.
3. Delete all 2,000,000 keys in the same order.
4. Verify the tree is empty.

Every key is inserted once and deleted once. All programs end with an
empty tree.

### 2.2 The four implementations

| Program | What it is | Storage |
| --- | --- | --- |
| `redblack_franko.fr` | Franko source with `std::NextAllocator`, compiled by `compile.py` to C++14 and then by `g++` | One heap `[Node]` array + free-list pool |
| `redblack_franko_sparse.fr` | Same Franko tree with `std::SparseSetAllocator`; Franko-only | One heap `[Node]` array + sparse-set pool |
| `redblack_cpp.cpp` | Hand-written C++ reference using `std::set<int32_t>` | One heap allocation per node, libstdc++ RB tree |
| `redblack_cpp_pool.cpp` | Hand-written C++ port of the *same* array-pool tree as Franko | One `std::vector<Node>` + `std::vector<uint32_t>` free list |

The first comparison is the idiomatic one: what a Franko programmer
writes versus what a C++ programmer typically reaches for. The pool
control is the same-data-structure comparison: same algorithm, same
output, so the remaining difference is language/code generation/runtime.
The sparse-set variant isolates the allocator choice inside Franko; it
has no C++ control.

All Franko and pool-control builds print the same summary
(`count`, `sum`, `bad`, `bh`, `empty`, `used`). The `std::set` reference
prints `size` instead of `bh`/`used`, but agrees on count, sum, order,
and emptiness.

### 2.3 Allocators

Both Franko pools use a heap `[uint32]` array (or two, for the sparse
set) and return 32-bit slot indices used as tree links. Both have O(1)
`new_index`/`free_index` and an `exist` check.

| Allocator | Free-list/slot layout | `write_active_indices` | Trade-off |
| --- | --- | --- | --- |
| `NextAllocator` | Singly linked free list through one `_next` array | O(capacity) scan | One array, LIFO reuse |
| `SparseSetAllocator` | `_dense` live list + `_sparse` index→position | O(used) walk | Two arrays, swap-remove reuse, O(used) enumeration |

The red-black tree only calls `init`, `new_index`, `free_index`,
`used`, and `uninit`, so the two pools exercise the same tree code and
differ only in allocation/release bookkeeping.

### 2.4 Build matrix

| Group | Portable builds | Native builds | Count |
| --- | --- | --- | --- |
| Franko `NextAllocator` | `default`, `-O`, `-Omax` | same three + `-march=native` | 6 |
| Franko `SparseSetAllocator` | `sparse-default`, `sparse-O`, `sparse-Omax` | same three + `-march=native` | 6 |
| C++ `std::set` | `cpp-O1-asan`, `cpp-O1`, `cpp-O3` | same three + `-march=native` | 6 |
| C++ array-pool control | `cpp-pool-O1-asan`, `cpp-pool-O1`, `cpp-pool-O3` | same three + `-march=native` | 6 |

Build modes:

| Label | Optimization | Runtime / allocator | Franko checks | ASan |
| --- | --- | --- | --- | --- |
| `default` / `sparse-default` | `-O1` | System allocator | Checked | Yes |
| `-O` / `sparse-O` | `-O3` | Static jemalloc | Checked | No |
| `-Omax` / `sparse-Omax` | `-O3` | Static jemalloc | Unchecked | No |
| `cpp-O1-asan` | `-O1` | System allocator | — | Yes |
| `cpp-O1` | `-O1` | System allocator | — | No |
| `cpp-O3` | `-O3` | System allocator | — | No |
| `cpp-pool-*` | `-O1` or `-O3` | System allocator | `std::vector` unchecked | ASan on `*-asan` |

`-march=native` maps to `-march=alderlake` on this machine. It enables
AVX2, BMI/BMI2, and other Alder Lake features, and implies Alder Lake
tuning. It also makes the binary host-specific.

### 2.5 Timing methodology

The harness is `benchmark/benchmark_matrix.py`:

```bash
uv run python benchmark/benchmark_matrix.py 10 --cpu 2
```

- All 24 binaries are built **before** any timing, so compiler heat and
  build order do not sit immediately in front of one mode's runs.
- Each binary gets one **untimed warm-up**.
- Then 10 measured rounds. Within each round the 24 modes are run in a
  **randomized order** (fixed seed), and the process is **pinned to one
  CPU** (`--cpu 2`). This spreads thermal/frequency drift and position
  effects across all modes instead of letting them accumulate on
  whichever mode happens to run last.
- Timing is whole-process wall clock with `time.perf_counter()`,
  including startup, the tree work, the audit/walk, output, and
  teardown.
- Every build's stdout is checked. Franko builds must agree with each
  other; `std::set` builds must agree with each other; the pool control
  must agree with Franko; the sparse-set group must agree with both.

This run used `g++ 15.2.0` and OpenJDK `25.0.5` under WSL2 on the host
described in §5. Absolute times are sensitive to the host, WSL2
scheduling, and CPU frequency. Comparisons **within this report** are
the authoritative ones.

---

## 3. Results

### 3.1 Franko `NextAllocator`

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| default | 0.576 | 0.573 | 0.565 | 0.601 | 0.012 | 2.1% | 426 |
| default-native | 0.585 | 0.583 | 0.578 | 0.597 | 0.006 | 1.1% | 428 |
| -O | 0.362 | 0.360 | 0.354 | 0.377 | 0.008 | 2.2% | 1000 |
| -O-native | 0.363 | 0.359 | 0.357 | 0.388 | 0.010 | 2.6% | 1000 |
| -Omax | 0.330 | 0.329 | 0.324 | 0.338 | 0.004 | 1.4% | 1000 |
| -Omax-native | 0.332 | 0.330 | 0.326 | 0.341 | 0.005 | 1.6% | 1000 |

### 3.2 Franko `SparseSetAllocator`

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| sparse-default | 0.609 | 0.609 | 0.595 | 0.625 | 0.008 | 1.4% | 431 |
| sparse-default-native | 0.592 | 0.593 | 0.584 | 0.606 | 0.006 | 1.0% | 432 |
| sparse-O | 0.362 | 0.361 | 0.357 | 0.374 | 0.005 | 1.3% | 1000 |
| sparse-O-native | 0.366 | 0.363 | 0.362 | 0.390 | 0.008 | 2.3% | 1000 |
| sparse-Omax | 0.333 | 0.334 | 0.323 | 0.340 | 0.005 | 1.5% | 1000 |
| sparse-Omax-native | 0.334 | 0.332 | 0.330 | 0.347 | 0.005 | 1.5% | 1000 |

### 3.3 C++ `std::set`

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| cpp-O1-asan | 0.993 | 0.992 | 0.979 | 1.022 | 0.012 | 1.2% | 245 |
| cpp-O1-asan-native | 1.214 | 1.211 | 1.202 | 1.234 | 0.011 | 0.9% | 246 |
| cpp-O1 | 0.442 | 0.445 | 0.419 | 0.457 | 0.011 | 2.5% | 154 |
| cpp-O1-native | 0.442 | 0.441 | 0.430 | 0.458 | 0.009 | 2.1% | 154 |
| cpp-O3 | 0.398 | 0.400 | 0.382 | 0.411 | 0.009 | 2.2% | 158 |
| cpp-O3-native | 0.397 | 0.396 | 0.383 | 0.417 | 0.010 | 2.5% | 158 |

### 3.4 C++ array-pool control

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| cpp-pool-O1-asan | 0.473 | 0.473 | 0.466 | 0.482 | 0.006 | 1.2% | 418 |
| cpp-pool-O1-asan-native | 0.475 | 0.476 | 0.464 | 0.486 | 0.008 | 1.6% | 419 |
| cpp-pool-O1 | 0.409 | 0.407 | 0.400 | 0.420 | 0.006 | 1.5% | 287 |
| cpp-pool-O1-native | 0.451 | 0.451 | 0.441 | 0.460 | 0.006 | 1.4% | 287 |
| cpp-pool-O3 | 0.353 | 0.351 | 0.342 | 0.366 | 0.007 | 1.9% | 287 |
| cpp-pool-O3-native | 0.367 | 0.366 | 0.349 | 0.381 | 0.011 | 3.1% | 287 |

### 3.5 Native effect

| Base build | Portable (s) | Native (s) | Native delta |
| --- | --- | --- | --- |
| Franko default | 0.576 | 0.585 | **+1.7%** |
| Franko -O | 0.362 | 0.363 | **+0.3%** |
| Franko -Omax | 0.330 | 0.332 | **+0.7%** |
| Sparse default | 0.609 | 0.592 | **-2.8%** |
| Sparse -O | 0.362 | 0.366 | **+1.0%** |
| Sparse -Omax | 0.333 | 0.334 | **+0.4%** |
| cpp-O1-asan | 0.993 | 1.214 | **+22.2%** |
| cpp-O1 | 0.442 | 0.442 | **-0.2%** |
| cpp-O3 | 0.398 | 0.397 | **-0.3%** |
| cpp-pool-O1-asan | 0.473 | 0.475 | **+0.3%** |
| cpp-pool-O1 | 0.409 | 0.451 | **+10.1%** |
| cpp-pool-O3 | 0.353 | 0.367 | **+4.1%** |

Negative means native is faster. Franko's only native win is the sparse
default build at 2.8%; the other five Franko modes are flat or slightly
slower. The large regressions are all in the C++ references.

### 3.6 Head-to-head

**Franko vs C++ `std::set`**

| Comparison | Faster | Speedup | Context |
| --- | --- | --- | --- |
| default vs cpp-O1-asan | Franko default | **1.73x** | Matched `-O1` + ASan + system allocator |
| default-native vs cpp-O1-asan-native | Franko default-native | 2.07x | Matched, but native hurts `std::set` ASan |
| -O vs cpp-O1 | Franko -O | **1.22x** | Franko uses `-O3` + jemalloc |
| -O vs cpp-O3 | Franko -O | **1.10x** | Franko `-O` vs C++ max |
| -Omax vs cpp-O3 | Franko -Omax | **1.21x** | Max vs max |
| -Omax-native vs cpp-O3-native | Franko -Omax-native | 1.19x | Max vs max, native |

**Franko vs the same-data-structure C++ control**

| Comparison | Faster | Speedup | Context |
| --- | --- | --- | --- |
| -Omax vs cpp-pool-O3 | Franko -Omax | **1.07x** | Same array-pool design, both release |
| -O vs cpp-pool-O3 | C++ pool -O3 | **1.03x** | Franko checked vs C++ unchecked |
| default vs cpp-pool-O1-asan | C++ pool -O1-asan | **1.22x** | Same design, both ASan; Franko carries checks |
| -Omax-native vs cpp-pool-O3-native | Franko -Omax-native | 1.11x | Native, but pool native regresses |
| default-native vs cpp-pool-O1-asan-native | C++ pool | 1.23x | Native, both ASan |

### 3.7 Franko allocator variants

| Comparison | Faster | Speedup | Context |
| --- | --- | --- | --- |
| default vs sparse-default | NextAllocator | **1.06x** | Portable, ASan |
| -O vs sparse-O | parity | **1.00x** | Portable, checked release |
| -Omax vs sparse-Omax | NextAllocator | **1.01x** | Portable, unchecked release |
| default-native vs sparse-default-native | NextAllocator | 1.01x | Native, ASan |
| -O-native vs sparse-O-native | NextAllocator | 1.01x | Native, checked release |
| -Omax-native vs sparse-Omax-native | NextAllocator | 1.01x | Native, unchecked release |

The sparse-set pool has a small checked-build overhead and is at parity
in `-Omax`. Its intended advantage — O(used) `write_active_indices`
instead of O(capacity) — is not exercised by this workload.

### 3.8 Decomposing the gap

**Release**

| Step | Ratio | Interpretation |
| --- | --- | --- |
| `std::set -O3` → C++ pool `-O3` | **1.13x** | Same language, better storage/allocator |
| C++ pool `-O3` → Franko `-Omax` | **1.07x** | Same design, generated-code difference |
| `std::set -O3` → Franko `-Omax` | **1.21x** | Combined |

**Sanitizer-matched**

| Step | Ratio | Interpretation |
| --- | --- | --- |
| ASan `std::set` → ASan C++ pool | **2.10x** | Per-node allocation is very expensive under ASan |
| ASan C++ pool → Franko default | **1.22x** | Checked Franko build vs unchecked C++ control |
| ASan `std::set` → Franko default | **1.73x** | Combined; most of it is storage |

So the large sanitizer gap is not evidence that Franko's code
generator is 2x better. It is evidence that `std::set`'s per-node
allocation strategy is punished much harder by ASan than a bulk pool.
At release, the data-structure advantage and the code-generation
advantage are both real but modest: 1.13x × 1.07x ≈ 1.21x. Under ASan,
storage is worth 2.10x, but the checked Franko build gives back 1.22x,
leaving 1.73x end to end.

---

## 4. Analysis

### 4.1 Why the array pool wins

The two storage designs are very different at 2M nodes:

| Property | Franko / C++ pool | `std::set` |
| --- | --- | --- |
| Node payload | 20 B (`int32` key + colour + three `uint32` indices) | 40 B libstdc++ `_Rb_tree_node<int32_t>` |
| 2M nodes | 40 MB | 80 MB |
| Free-list array | 8 MB (`uint32` per slot) | — |
| Allocations | 2 large allocations | 2M `new` + 2M `delete` |
| Links | 32-bit indices | 64-bit pointers |
| Layout | Contiguous array | Scattered nodes |
| ASan overhead | One redzone around each big region | Redzones/metadata per node |

The pool's total payload is about 48 MB, roughly 60% of `std::set`'s
node payload before allocator overhead. That explains both the release
gap and the sanitizer gap: the contiguous array and 32-bit links mean
fewer cache misses and less memory traffic per tree step, and ASan's
per-node cost largely disappears.

The trade-off is real: 32-bit indices cap the tree at ~4.29 billion
nodes, and the pool reserves the full slot array up front.

### 4.2 `NextAllocator` vs `SparseSetAllocator`

Both pools are O(1) for allocation and release. The sparse set performs
more work per operation (two array accesses and four stores in
`free_index`, plus the `exist` check), while the free list is a single
linked-list update. That matches the measured 1.06x ASan-default
difference and the parity-to-1.01x release differences. In `-Omax` the
gap is about 1%, small enough to read as parity.

The sparse set's O(used) `write_active_indices` would matter for a
caller that enumerates live slots after many frees; this tree never
does. Choose `NextAllocator` for the lowest per-operation overhead and
`SparseSetAllocator` when live-slot enumeration is hot.

### 4.3 Is Franko's code generation competitive?

The control answers this more honestly than the `std::set` comparison:

- **Release:** Franko `-Omax` is **1.07x faster** than the hand-written
  C++ pool at `-O3`. Franko `-O` (checked) is **1.03x slower** than the
  same C++ pool unchecked. That is parity, with a small advantage in
  the unchecked build and a small deficit when the checked runtime is
  included.
- **ASan:** The hand-written C++ pool at `-O1-asan` is **1.22x faster**
  than Franko default. Franko default also carries its own bounds and
  state checks on top of ASan, while the C++ control is unchecked, so
  some of that 22% is expected.
- **Against `std::set`:** the same-data-structure control removes most
  of Franko's advantage, confirming that the 1.21x release win is
  mostly the data structure.

Generated code is competitive with careful hand-written C++ on the same
algorithm. It is not a 2x code-generation win.

### 4.4 The `-march=native` surprise

`-march=native` is often treated as a free performance flag. On this
workload it is not:

- **Franko builds:** `-O` is **0.3% slower**, `-Omax` is **0.7%
  slower**, and default is **1.7% slower**. The sparse default build is
  2.8% faster, but sparse `-O` and `-Omax` are 1.0% and 0.4% slower.
- **C++ `std::set`:** `-O1` and `-O3` are unchanged, but the ASan build
  is **22.2% slower**.
- **C++ array-pool control:** `-O3` is 4.1% slower, and the `-O1` build
  is **10.1% slower**.

The only clear Franko win is sparse default at 2.8%; the other five
modes are flat to slightly slower. The large regressions are branchy,
scalar, pointer/index-chasing workloads with no meaningful vector loop;
different instruction selection, inlining, and code layout are the
likely causes. The practical lesson: `-march=native` is
code-shape-dependent and must be measured, not assumed.

### 4.5 Correctness

All builds produced identical output within their group, and both
allocator variants matched the pool control exactly:

```text
Franko NextAllocator / Franko SparseSetAllocator / C++ pool control:
count=2000000
sum=1000000
bad=0
bh=20
empty=1
used=0

C++ std::set:
count=2000000
sum=1000000
bad=0
empty=1
size=0
```

- Both sides agree on count (2,000,000) and key sum (1,000,000).
- `bad=0` means the in-order walk was strictly increasing.
- `bh=20` is the black-height after inserts, consistent across every
  rep.
- `used=0` confirms every pool slot was returned; `empty=1` and
  `size=0` confirm every key was deleted.
- Franko's audit is stronger than the `std::set` check: it verifies
  red-red violations and black-height, while the C++ reference only
  checks order, count, and sum. If anything, the extra audit work
  slightly penalizes Franko.

### 4.6 Noise

All measured modes have a coefficient of variation between **0.9% and
3.1%**. The largest spreads are `cpp-pool-O3-native` (3.1%),
`-O-native` (2.6%), `cpp-O3-native` (2.5%), and `cpp-O1` (2.5%).
Differences below ~5% should be read as small effects, not precise
constants. Most of the secondary comparisons in this report are in that
band; the 1.21x and 1.73x headline gaps are not.

---

## 5. Limitations

1. **One workload, one key pattern.** Keys arrive from both ends of the
   range and are deleted in the same order. A sorted, random, or
   adversarial insertion order would exercise different rebalancing
   paths.
2. **Different implementations in the main comparison.** Franko's
   array-pool tree is not `std::set`. The control narrows this, but it
   is still a hand-written C++ port, not a formally identical program.
3. **Sparse-set control.** The sparse-set variant has no independent
   C++ control; its comparison is against the other Franko allocator on
   identical tree code.
4. **Allocator and runtime confounds.** Franko `-O`/`-Omax` link
   jemalloc; the C++ builds use the system allocator. The effect is
   likely small here because the pool makes only two large allocations,
   but it is not controlled.
5. **Checked vs unchecked under ASan.** Franko default keeps bounds and
   state checks; the C++ ASan control is unchecked. The 1.22x
   default-vs-pool gap therefore includes check overhead and is not a
   pure code-generation comparison.
6. **Native builds are host-specific.** `-march=native` produced
   `-march=alderlake` here. These binaries will not run on older CPUs.
7. **Whole-process timing.** Insert, audit/walk, delete, output, and
   teardown are one number. We cannot say how much of the gap is
   insertion versus deletion.
8. **WSL2 and one machine.** The benchmark runs under WSL2 on a
   12th-gen i5-12600K. CPU frequency, host scheduling, and WSL2
   virtualisation add noise despite CPU pinning.
9. **No hardware counters.** `perf` is not available in this WSL2
   environment, so cache misses, branch misses, and instructions per
   cycle are inferred from timing and layout, not measured.
10. **Binary size is dominated by runtimes.** The Franko release
    binaries are ~1 MB because jemalloc is statically linked; the
    `std::set` binaries are 154–158 KB and the C++ pool is 287 KB, both
    with static libstdc++/libgcc.

---

## 6. Recommendations

For this codebase and workload:

- **Use portable `-Omax` for maximum Franko speed.** It is the fastest
  Franko build (0.330 s for `NextAllocator`, 0.333 s for
  `SparseSetAllocator`) and avoids host-specific binaries.
- **Do not add `-march=native` by default.** It gives no reliable
  Franko release gain and can regress C++ controls substantially.
- **Keep `NextAllocator` unless live-slot enumeration matters.**
  `SparseSetAllocator` is at parity in release but 1.06x slower in the
  ASan default build on this workload; its O(used) enumeration is its
  real advantage.
- **Compare Franko to the same data structure when making language
  claims.** Against the C++ pool control, Franko is within about 7% at
  release; under ASan the checked default build is 1.22x
  slower than the unchecked control. Against `std::set`, the array-pool
  design is the main advantage.
- **For C++ users, the actionable item is allocation.** A pool
  allocator for `std::set`, or a custom array-pool tree, captures most
  of the difference.

For future measurements: add phase timings (insert / audit / delete),
peak RSS, PGO/LTO variants, clang, bare-metal Linux, more key orders,
and an ASan + unchecked Franko build to separate sanitizer cost from
check cost. A formal confidence interval or paired test would strengthen
the small-difference claims.

---

## 7. Reproduce

```bash
uv run python update_toolchain.py
uv run python benchmark/benchmark_matrix.py 10 --cpu 2
```

The harness builds:

- Franko `NextAllocator` from `benchmark/redblack_franko.fr`;
- Franko `SparseSetAllocator` from `benchmark/redblack_franko_sparse.fr`;
- `std::set` from `benchmark/redblack_cpp.cpp`;
- the array-pool control from `benchmark/redblack_cpp_pool.cpp`.

All source and scripts are in `benchmark/`. Generated `.cpp` files
(such as `redblack_franko.cpp` and `redblack_franko_sparse.cpp`), `.out`
binaries, and `.bin` files are build artifacts and are ignored by git.

---

## Appendix A: raw measured runs

These are the 10 measured rounds per mode in the order recorded. The
mode order within each round was randomized; the columns are round
numbers, not a fixed execution sequence.

**Franko `NextAllocator`**

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| default | 0.601 | 0.572 | 0.568 | 0.574 | 0.565 | 0.595 | 0.568 | 0.566 | 0.573 | 0.574 | 0.576 | 426 |
| default-native | 0.581 | 0.583 | 0.589 | 0.580 | 0.583 | 0.597 | 0.584 | 0.583 | 0.578 | 0.594 | 0.585 | 428 |
| -O | 0.357 | 0.363 | 0.365 | 0.370 | 0.354 | 0.355 | 0.377 | 0.355 | 0.358 | 0.370 | 0.362 | 1000 |
| -O-native | 0.360 | 0.358 | 0.359 | 0.359 | 0.388 | 0.357 | 0.357 | 0.368 | 0.360 | 0.368 | 0.363 | 1000 |
| -Omax | 0.331 | 0.328 | 0.335 | 0.327 | 0.332 | 0.329 | 0.324 | 0.324 | 0.329 | 0.338 | 0.330 | 1000 |
| -Omax-native | 0.341 | 0.328 | 0.333 | 0.326 | 0.329 | 0.327 | 0.340 | 0.331 | 0.336 | 0.328 | 0.332 | 1000 |

**Franko `SparseSetAllocator`**

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sparse-default | 0.609 | 0.610 | 0.616 | 0.625 | 0.595 | 0.609 | 0.609 | 0.600 | 0.606 | 0.614 | 0.609 | 431 |
| sparse-default-native | 0.606 | 0.591 | 0.595 | 0.584 | 0.592 | 0.593 | 0.593 | 0.593 | 0.589 | 0.587 | 0.592 | 432 |
| sparse-O | 0.362 | 0.364 | 0.360 | 0.363 | 0.360 | 0.359 | 0.361 | 0.374 | 0.357 | 0.364 | 0.362 | 1000 |
| sparse-O-native | 0.390 | 0.363 | 0.363 | 0.364 | 0.363 | 0.362 | 0.366 | 0.362 | 0.365 | 0.364 | 0.366 | 1000 |
| sparse-Omax | 0.332 | 0.336 | 0.329 | 0.335 | 0.323 | 0.329 | 0.340 | 0.329 | 0.337 | 0.336 | 0.333 | 1000 |
| sparse-Omax-native | 0.347 | 0.331 | 0.335 | 0.330 | 0.332 | 0.332 | 0.331 | 0.335 | 0.334 | 0.331 | 0.334 | 1000 |

**C++ `std::set`**

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| cpp-O1-asan | 0.992 | 0.998 | 0.995 | 0.996 | 0.979 | 0.992 | 0.984 | 0.985 | 1.022 | 0.987 | 0.993 | 245 |
| cpp-O1-asan-native | 1.202 | 1.203 | 1.225 | 1.222 | 1.210 | 1.206 | 1.234 | 1.212 | 1.218 | 1.204 | 1.214 | 246 |
| cpp-O1 | 0.447 | 0.443 | 0.457 | 0.419 | 0.438 | 0.433 | 0.439 | 0.447 | 0.446 | 0.455 | 0.442 | 154 |
| cpp-O1-native | 0.446 | 0.432 | 0.451 | 0.430 | 0.439 | 0.438 | 0.431 | 0.458 | 0.442 | 0.449 | 0.442 | 154 |
| cpp-O3 | 0.405 | 0.395 | 0.400 | 0.385 | 0.395 | 0.400 | 0.382 | 0.400 | 0.411 | 0.404 | 0.398 | 158 |
| cpp-O3-native | 0.399 | 0.393 | 0.395 | 0.384 | 0.397 | 0.391 | 0.405 | 0.383 | 0.417 | 0.401 | 0.397 | 158 |

**C++ array-pool control**

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| cpp-pool-O1-asan | 0.476 | 0.474 | 0.471 | 0.468 | 0.482 | 0.477 | 0.466 | 0.467 | 0.482 | 0.472 | 0.474 | 418 |
| cpp-pool-O1-asan-native | 0.468 | 0.486 | 0.475 | 0.464 | 0.469 | 0.480 | 0.466 | 0.480 | 0.483 | 0.478 | 0.475 | 419 |
| cpp-pool-O1 | 0.407 | 0.415 | 0.408 | 0.406 | 0.400 | 0.406 | 0.404 | 0.416 | 0.420 | 0.412 | 0.409 | 287 |
| cpp-pool-O1-native | 0.450 | 0.452 | 0.459 | 0.441 | 0.445 | 0.449 | 0.444 | 0.460 | 0.453 | 0.454 | 0.451 | 287 |
| cpp-pool-O3 | 0.356 | 0.366 | 0.351 | 0.351 | 0.348 | 0.342 | 0.351 | 0.353 | 0.360 | 0.351 | 0.353 | 287 |
| cpp-pool-O3-native | 0.362 | 0.365 | 0.373 | 0.380 | 0.379 | 0.349 | 0.351 | 0.365 | 0.381 | 0.368 | 0.367 | 287 |

---

## Appendix B: files

| File | Role |
| --- | --- |
| `benchmark/benchmark_matrix.py` | 24-mode harness (two Franko allocators, native + C++ controls) |
| `benchmark/redblack_franko.fr` | Franko benchmark source, `NextAllocator` |
| `benchmark/redblack_franko_sparse.fr` | Franko benchmark source, `SparseSetAllocator` |
| `benchmark/redblack_cpp.cpp` | C++ `std::set` reference |
| `benchmark/redblack_cpp_pool.cpp` | C++ same-data-structure control |
| `benchmark/redblack.md` | This report |
