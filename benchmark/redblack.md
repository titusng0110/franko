# Red-black benchmark: Franko vs C++

**2,000,000 inserts + 2,000,000 deletes · 10 measured rounds per build · 2026-11-13**

This report compares Franko with C++ on the same red-black tree workload,
using two Franko allocator variants and two C++ references. Every build
uses the system allocator, is compiled by the same host toolchain, and is
left unstripped. Generated Franko C++ is compiled with exceptions and
RTTI disabled. The run used `g++ 15.2.0`, OpenJDK `25.0.5`, and WSL2 on
an Intel i5-12600K.

The headline is structural rather than code-generation-specific. Against
idiomatic `std::set`, Franko's array-pool tree is **1.24x slower at
maximum release optimization** but **1.08x faster under matched
AddressSanitizer builds**. Against a hand-written C++ port of the *same*
array-pool tree, the C++ control is **1.08x faster at max release**,
**1.17x faster when Franko uses the checked `-O` build**, and **1.17x
faster under ASan**. The release gap is therefore mostly code generation
and container layout, while the ASan result reflects the different
allocation strategy.

The second Franko allocator, `SparseSetAllocator`, is at parity with the
existing `NextAllocator` on this workload: 1.00x–1.05x, with the two
allocators trading small wins.

`-march=native` is flat for Franko (within 2%), gives no reliable gain
to the `std::set` release builds, and regresses the C++ pool `-O1` build
by 11.5% and the `std::set` ASan build by 41.9%.

---

## 1. Bottom line

| Question | Answer |
| --- | --- |
| Fastest portable Franko build | Franko `-Omax`, **0.381 s** (`sparse-Omax`) |
| Fastest native Franko build | Franko `-Omax-native`, **0.375 s** |
| Fastest sparse-set build | `sparse-Omax`, **0.381 s** |
| Franko `-Omax` vs C++ `std::set -O3` | **1.24x slower** |
| Franko `-Omax` vs same-data-structure C++ `-O3` | **1.08x slower** |
| Franko `-O` vs same-data-structure C++ `-O3` | **1.17x slower** |
| Sanitizer-matched, portable | **1.08x faster** than ASan `std::set` |
| Sanitizer-matched, same data structure | **1.17x slower** than the C++ control |
| Franko `default` → `-Omax` | **1.44x faster** |
| `SparseSetAllocator` vs `NextAllocator` | parity (1.00x–1.05x) |
| `-march=native` overall | flat for Franko; C++ regressions up to **+41.9%** |

Two defensible headline claims:

- **Against `std::set`:** the array-pool design is **1.24x slower** at
  maximum release optimization but **1.08x faster** when both sides use
  the same `-O1 + ASan + system allocator` setup.
- **Against the same array-pool design in C++:** Franko is close at
  release but not ahead. The C++ control is **1.08x faster** at max
  release, **1.17x faster** against Franko's checked `-O` build, and
  **1.17x faster** under ASan. The ASan gap is a storage effect, not
  evidence of a fundamentally faster red-black algorithm or a large
  code-generation advantage.

The rest of this report shows the raw numbers and how those conclusions
follow.

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
| `redblack_franko.fr` | Franko source with `NextAllocator`, compiled by `compile.py` to C++14 | One heap `[Node]` array + free-list pool |
| `redblack_franko_sparse.fr` | Same Franko tree with `SparseSetAllocator`; Franko-only | One heap `[Node]` array + sparse-set pool |
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
| `-O` / `sparse-O` | `-O3` | System allocator | Checked | No |
| `-Omax` / `sparse-Omax` | `-O3` | System allocator | Unchecked | No |
| `cpp-O1-asan` | `-O1` | System allocator | — | Yes |
| `cpp-O1` | `-O1` | System allocator | — | No |
| `cpp-O3` | `-O3` | System allocator | — | No |
| `cpp-pool-*` | `-O1` or `-O3` | System allocator | `std::vector` unchecked | ASan on `*-asan` |

Every binary is unstripped and statically links libstdc++/libgcc on
Linux, so the binary-size comparison is controlled. Generated Franko
C++ is compiled with exceptions and RTTI disabled, which removes the C++
exception/unwind runtime. `-march=native` maps to `-march=alderlake` on
this machine: AVX2, BMI, and BMI2 are enabled, and the binary becomes
host-specific.

### 2.5 Timing methodology

The harness is `benchmark/redblack_benchmark.py`:

```bash
uv run python benchmark/redblack_benchmark.py 10 --cpu 2
```

- All 24 binaries are built **before** any timing, so compiler heat and
  build order do not sit immediately in front of one mode's runs.
- Each binary gets one **untimed warm-up**.
- Then 10 measured rounds. Within each round the 24 modes are run in a
  **randomized order** (fixed seed). The process can optionally be pinned
  to one CPU (`--cpu 2`); this run did not pin. Shuffling still spreads
  thermal/frequency drift and position effects across all modes instead
  of letting them accumulate on whichever mode happens to run last.
- Timing is internal to each binary. Franko uses `std::mono_time()`
  (`clock_gettime(CLOCK_MONOTONIC)` or `QueryPerformanceCounter`);
  C++ uses `std::chrono::steady_clock`. The timer covers the insert,
  audit, and delete workload and stops before output and teardown.
  Each run prints `elapsed_ns <total>`; Python only parses it.
- Every build's stdout is checked after removing the `elapsed_ns` line.
  Franko builds must agree with each
  other; `std::set` builds must agree with each other; the pool control
  must agree with Franko; the sparse-set group must agree with both.

Absolute times are sensitive to the host, WSL2 scheduling, and CPU
frequency. Comparisons **within this report** are the authoritative
ones.

---

## 3. Results

### 3.1 Franko `NextAllocator`

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| default | 0.551 | 0.550 | 0.538 | 0.577 | 0.010 | 1.9% | 2127 |
| default-native | 0.548 | 0.545 | 0.538 | 0.574 | 0.010 | 1.9% | 2128 |
| -O | 0.413 | 0.412 | 0.401 | 0.426 | 0.009 | 2.2% | 27 |
| -O-native | 0.412 | 0.410 | 0.400 | 0.437 | 0.011 | 2.7% | 27 |
| -Omax | 0.382 | 0.382 | 0.371 | 0.398 | 0.008 | 2.0% | 28 |
| -Omax-native | 0.375 | 0.378 | 0.359 | 0.384 | 0.009 | 2.4% | 28 |

### 3.2 Franko `SparseSetAllocator`

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sparse-default | 0.569 | 0.569 | 0.555 | 0.585 | 0.010 | 1.7% | 2132 |
| sparse-default-native | 0.578 | 0.574 | 0.564 | 0.622 | 0.017 | 2.9% | 2133 |
| sparse-O | 0.405 | 0.408 | 0.385 | 0.416 | 0.010 | 2.5% | 27 |
| sparse-O-native | 0.414 | 0.417 | 0.393 | 0.423 | 0.009 | 2.2% | 27 |
| sparse-Omax | 0.381 | 0.379 | 0.366 | 0.405 | 0.013 | 3.5% | 28 |
| sparse-Omax-native | 0.383 | 0.381 | 0.370 | 0.403 | 0.010 | 2.6% | 28 |

### 3.3 C++ `std::set`

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| cpp-O1-asan | 0.594 | 0.592 | 0.566 | 0.633 | 0.020 | 3.4% | 2167 |
| cpp-O1-asan-native | 0.843 | 0.840 | 0.790 | 0.918 | 0.035 | 4.1% | 2168 |
| cpp-O1 | 0.356 | 0.359 | 0.332 | 0.385 | 0.017 | 4.8% | 159 |
| cpp-O1-native | 0.351 | 0.352 | 0.336 | 0.369 | 0.009 | 2.7% | 159 |
| cpp-O3 | 0.308 | 0.308 | 0.296 | 0.323 | 0.010 | 3.2% | 158 |
| cpp-O3-native | 0.308 | 0.310 | 0.289 | 0.321 | 0.011 | 3.4% | 158 |

### 3.4 C++ array-pool control

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| cpp-pool-O1-asan | 0.471 | 0.465 | 0.448 | 0.510 | 0.021 | 4.4% | 2335 |
| cpp-pool-O1-asan-native | 0.482 | 0.489 | 0.454 | 0.499 | 0.018 | 3.7% | 2336 |
| cpp-pool-O1 | 0.410 | 0.407 | 0.398 | 0.428 | 0.010 | 2.4% | 287 |
| cpp-pool-O1-native | 0.457 | 0.456 | 0.442 | 0.482 | 0.012 | 2.7% | 287 |
| cpp-pool-O3 | 0.354 | 0.353 | 0.333 | 0.377 | 0.014 | 3.9% | 287 |
| cpp-pool-O3-native | 0.360 | 0.363 | 0.345 | 0.371 | 0.010 | 2.9% | 287 |

### 3.5 Native effect

| base build | portable (s) | native (s) | native delta |
| --- | ---: | ---: | ---: |
| default | 0.551 | 0.548 | -0.6% |
| -O | 0.413 | 0.412 | -0.1% |
| -Omax | 0.382 | 0.375 | -2.0% |
| sparse-default | 0.569 | 0.578 | +1.5% |
| sparse-O | 0.405 | 0.414 | +2.3% |
| sparse-Omax | 0.381 | 0.383 | +0.5% |
| cpp-O1-asan | 0.594 | 0.843 | +41.9% |
| cpp-O1 | 0.356 | 0.351 | -1.5% |
| cpp-O3 | 0.308 | 0.308 | -0.1% |
| cpp-pool-O1-asan | 0.471 | 0.482 | +2.3% |
| cpp-pool-O1 | 0.410 | 0.457 | +11.5% |
| cpp-pool-O3 | 0.354 | 0.360 | +1.8% |

### 3.6 Head-to-head

**Franko vs C++ `std::set`**

| comparison | faster | speedup | note |
| --- | --- | ---: | --- |
| default vs cpp-O1-asan | default | 1.08x | matched -O1 + ASan + system allocator |
| default-native vs cpp-O1-asan-native | default-native | 1.54x | matched, native |
| -O vs cpp-O1 | cpp-O1 | 1.16x | Franko -O3 vs C++ -O1 |
| -O-native vs cpp-O1-native | cpp-O1-native | 1.17x | same, native |
| -O vs cpp-O3 | cpp-O3 | 1.34x | Franko release vs C++ max |
| -O-native vs cpp-O3-native | cpp-O3-native | 1.34x | same, native |
| -Omax vs cpp-O3 | cpp-O3 | 1.24x | max vs max |
| -Omax-native vs cpp-O3-native | cpp-O3-native | 1.22x | max vs max, native |

**Franko vs the same-data-structure C++ control**

| comparison | faster | speedup | note |
| --- | --- | ---: | --- |
| -Omax vs cpp-pool-O3 | cpp-pool-O3 | 1.08x | same array-pool design, release |
| -Omax-native vs cpp-pool-O3-native | cpp-pool-O3-native | 1.04x | same, native |
| -O vs cpp-pool-O3 | cpp-pool-O3 | 1.17x | Franko checked vs C++ unchecked |
| -O-native vs cpp-pool-O3-native | cpp-pool-O3-native | 1.14x | same, native |
| default vs cpp-pool-O1-asan | cpp-pool-O1-asan | 1.17x | same design, ASan |
| default-native vs cpp-pool-O1-asan-native | cpp-pool-O1-asan-native | 1.14x | same, native, ASan |

### 3.7 Franko allocator variants

| comparison | faster | speedup | note |
| --- | --- | ---: | --- |
| default vs sparse-default | default | 1.03x | portable, ASan |
| default-native vs sparse-default-native | default-native | 1.05x | native, ASan |
| -O vs sparse-O | sparse-O | 1.02x | portable, checked release |
| -O-native vs sparse-O-native | -O-native | 1.00x | native, checked release |
| -Omax vs sparse-Omax | sparse-Omax | 1.00x | portable, unchecked release |
| -Omax-native vs sparse-Omax-native | -Omax-native | 1.02x | native, unchecked release |

### 3.8 Decomposing the gap

**Release**

| Step | Ratio | Interpretation |
| --- | --- | --- |
| `std::set -O3` → C++ pool `-O3` | **1.15x slower** | Same language; `std::set` wins this workload |
| C++ pool `-O3` → Franko `-Omax` | **1.08x slower** | Same design, C++ control faster |
| `std::set -O3` → Franko `-Omax` | **1.24x slower** | Combined release gap |

**Sanitizer-matched**

| Step | Effect | Interpretation |
| --- | --- | --- |
| ASan `std::set` → ASan C++ pool | **1.26x faster** | Per-node allocation is expensive under ASan |
| ASan C++ pool → Franko default | **1.17x slower** | Checked Franko build vs unchecked C++ control |
| ASan `std::set` → Franko default | **1.08x faster** | Combined storage gain under ASan |

The sanitizer result is not evidence that Franko's code generator is
faster. It is evidence that `std::set`'s per-node allocation strategy is
punished harder by ASan than a bulk pool. At release, `std::set` is
1.15x faster than the C++ pool control, and the generated-code
difference costs Franko another 1.08x, leaving Franko 1.24x behind
`std::set`.

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
linked-list update. That matches the measured 1–5% difference. On this
workload the choice is mostly about the enumeration API: the sparse
set's O(used) `write_active_indices` is useful when live slots must be
visited; this tree never does that.

### 4.3 Is Franko's code generation competitive?

The control answers this more honestly than the `std::set` comparison:

- **Release:** the hand-written C++ pool is **1.08x faster** than Franko
  `-Omax`. Franko's checked `-O` build is **1.17x slower** than the same
  C++ pool. That is close, with a modest advantage for the C++ control
  in both cases.
- **ASan:** the hand-written C++ pool at `-O1-asan` is **1.17x faster**
  than Franko default. Franko default also carries its own bounds and
  state checks on top of ASan, while the C++ control is unchecked, so
  part of that 17% is expected.
- **Against `std::set`:** the control shows that allocation layout is not
  the whole story. Under ASan, Franko is **1.08x faster** than
  `std::set`; at release, `std::set` is **1.24x faster** than Franko.

Generated code is competitive with careful hand-written C++ on the same
algorithm. It is not a large code-generation win.

### 4.4 The `-march=native` surprise

`-march=native` is often treated as a free performance flag. On this
workload it is not:

- **Franko builds:** all six Franko/sparse build pairs are within 2% of
  portable. The effect is effectively zero.
- **C++ `std::set`:** `-O1` is 1.5% faster, `-O3` is flat, and the
  ASan build is **41.9% slower**.
- **C++ array-pool control:** `-O3` is 1.8% slower, `-O1` is **11.5%
  slower**, and the ASan build is 2.3% slower.

The ASan `std::set` regression is the largest effect. The C++ pool
regressions are also clear. These are branchy, scalar,
pointer/index-chasing workloads with no meaningful vector loop;
different instruction selection, inlining, and code layout are the likely
causes. The practical lesson: `-march=native` is code-shape-dependent
and must be measured, not assumed.

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

All measured modes have a coefficient of variation between **1.7% and
4.8%**. The largest spreads are `cpp-O1` (4.8%),
`cpp-pool-O1-asan` (4.4%), `cpp-O1-asan-native` (4.1%), and
`cpp-pool-O3` (3.9%); several Franko modes are below 2.5%.
Differences below ~5% should be read as small effects, not precise
constants. Most of the secondary comparisons in this report are in that
band; the 1.24x release gap and the 1.17x ASan control gap are not.

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
4. **Checked vs unchecked under ASan.** Franko default keeps bounds and
   state checks; the C++ ASan control is unchecked. The 1.17x
   default-vs-pool gap therefore includes check overhead and is not a
   pure code-generation comparison.
5. **Native builds are host-specific.** `-march=native` produced
   `-march=alderlake` here. These binaries will not run on older CPUs.
6. **Workload-only timing.** Insert, audit/walk, and delete are one
   internal measurement. Output, initialization, and teardown are
   excluded. We cannot say how much of the gap is insertion versus
   deletion.
7. **WSL2 and one machine.** The benchmark runs under WSL2 on a
   12th-gen i5-12600K. CPU frequency, host scheduling, and WSL2
   virtualisation add noise; pinning is optional and was not used here.
8. **No hardware counters.** `perf` is not available in this WSL2
   environment, so cache misses, branch misses, and instructions per
   cycle are inferred from timing and layout, not measured.
9. **Binary size depends on build class.** Release binaries are
   unstripped and use the system allocator: Franko is 27 KB, the C++
   pool is 287 KB, and `std::set` is 154–158 KB. ASan builds are
   2.1–2.3 MB because they carry instrumentation and debug info.
   Generated Franko C++ also disables exceptions and RTTI, which
   removes the C++ exception/unwind runtime from the release binaries.
10. **No allocator-vs-system control.** Franko and C++ both use the
    system allocator; the workload's pool makes only two large
    allocations, so a different allocator would not be expected to
    change the result materially.

---

## 6. Recommendations

For this codebase and workload:

- **Use portable `-Omax` for maximum Franko speed.** It is the fastest
  portable Franko build (0.382 s for `NextAllocator`, 0.381 s for
  `SparseSetAllocator`); `-Omax-native` reaches 0.375 s at the cost of
  host-specific binaries.
- **Do not add `-march=native` by default.** It gives no reliable
  Franko gain and can regress C++ controls substantially.
- **Keep `NextAllocator` unless live-slot enumeration matters.**
  `SparseSetAllocator` is within 1–5% on this workload; its O(used)
  enumeration is its real advantage.
- **Compare Franko to the same data structure when making language
  claims.** Against the C++ pool control, Franko is 1.08x slower at max
  release, 1.17x slower in checked `-O`, and 1.17x slower under ASan.
  Under ASan the array-pool layout still beats `std::set`; at release
  `std::set` is faster than the pool control.
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
uv run python benchmark/redblack_benchmark.py 10 --cpu 2
```

The harness builds:

- Franko `NextAllocator` from `benchmark/redblack_franko.fr`;
- Franko `SparseSetAllocator` from `benchmark/redblack_franko_sparse.fr`;
- `std::set` from `benchmark/redblack_cpp.cpp`;
- the array-pool control from `benchmark/redblack_cpp_pool.cpp`.

All source and scripts are in `benchmark/`. Generated `.cpp` files,
`.out` binaries, and `.bin` files are build artifacts and are ignored by
git.

---

## Appendix A: raw measured runs

**Franko (array pool, generated C++)**
| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: | ---: |
| default | 0.577 | 0.538 | 0.548 | 0.549 | 0.543 | 0.552 | 0.551 | 0.552 | 0.555 | 0.543 | 0.551 | 2127 |
| default-native | 0.550 | 0.546 | 0.574 | 0.543 | 0.547 | 0.553 | 0.545 | 0.538 | 0.544 | 0.538 | 0.548 | 2128 |
| -O | 0.422 | 0.402 | 0.412 | 0.417 | 0.411 | 0.423 | 0.406 | 0.401 | 0.406 | 0.426 | 0.413 | 27 |
| -O-native | 0.414 | 0.412 | 0.418 | 0.408 | 0.404 | 0.420 | 0.437 | 0.402 | 0.408 | 0.400 | 0.412 | 27 |
| -Omax | 0.398 | 0.371 | 0.387 | 0.376 | 0.386 | 0.378 | 0.380 | 0.377 | 0.384 | 0.385 | 0.382 | 28 |
| -Omax-native | 0.383 | 0.381 | 0.372 | 0.384 | 0.380 | 0.380 | 0.376 | 0.369 | 0.359 | 0.361 | 0.375 | 28 |

**Franko (sparse-set pool, generated C++)**
| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: | ---: |
| sparse-default | 0.555 | 0.558 | 0.577 | 0.574 | 0.570 | 0.568 | 0.561 | 0.585 | 0.565 | 0.581 | 0.569 | 2132 |
| sparse-default-native | 0.573 | 0.564 | 0.575 | 0.577 | 0.564 | 0.568 | 0.574 | 0.573 | 0.622 | 0.587 | 0.578 | 2133 |
| sparse-O | 0.416 | 0.397 | 0.412 | 0.413 | 0.411 | 0.405 | 0.411 | 0.394 | 0.385 | 0.403 | 0.405 | 27 |
| sparse-O-native | 0.423 | 0.408 | 0.418 | 0.408 | 0.420 | 0.410 | 0.422 | 0.393 | 0.416 | 0.422 | 0.414 | 27 |
| sparse-Omax | 0.405 | 0.366 | 0.383 | 0.382 | 0.373 | 0.377 | 0.381 | 0.366 | 0.378 | 0.404 | 0.381 | 28 |
| sparse-Omax-native | 0.384 | 0.370 | 0.396 | 0.379 | 0.381 | 0.381 | 0.379 | 0.386 | 0.374 | 0.403 | 0.383 | 28 |

**C++ std::set reference**
| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: | ---: |
| cpp-O1-asan | 0.602 | 0.570 | 0.589 | 0.618 | 0.633 | 0.591 | 0.596 | 0.593 | 0.566 | 0.583 | 0.594 | 2167 |
| cpp-O1-asan-native | 0.821 | 0.790 | 0.870 | 0.842 | 0.838 | 0.812 | 0.837 | 0.859 | 0.842 | 0.918 | 0.843 | 2168 |
| cpp-O1 | 0.346 | 0.332 | 0.339 | 0.385 | 0.362 | 0.364 | 0.373 | 0.356 | 0.368 | 0.336 | 0.356 | 159 |
| cpp-O1-native | 0.356 | 0.353 | 0.349 | 0.357 | 0.369 | 0.346 | 0.351 | 0.336 | 0.338 | 0.353 | 0.351 | 159 |
| cpp-O3 | 0.296 | 0.297 | 0.308 | 0.323 | 0.303 | 0.311 | 0.323 | 0.308 | 0.300 | 0.313 | 0.308 | 158 |
| cpp-O3-native | 0.309 | 0.292 | 0.311 | 0.306 | 0.316 | 0.305 | 0.321 | 0.289 | 0.319 | 0.310 | 0.308 | 158 |

**C++ array-pool control (same data structure as Franko)**
| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: | ---: |
| cpp-pool-O1-asan | 0.470 | 0.448 | 0.462 | 0.467 | 0.510 | 0.498 | 0.453 | 0.453 | 0.487 | 0.462 | 0.471 | 2335 |
| cpp-pool-O1-asan-native | 0.458 | 0.459 | 0.491 | 0.493 | 0.499 | 0.498 | 0.488 | 0.454 | 0.486 | 0.492 | 0.482 | 2336 |
| cpp-pool-O1 | 0.411 | 0.399 | 0.413 | 0.404 | 0.406 | 0.408 | 0.405 | 0.398 | 0.428 | 0.425 | 0.410 | 287 |
| cpp-pool-O1-native | 0.464 | 0.442 | 0.482 | 0.447 | 0.451 | 0.464 | 0.462 | 0.448 | 0.462 | 0.445 | 0.457 | 287 |
| cpp-pool-O3 | 0.363 | 0.342 | 0.377 | 0.369 | 0.347 | 0.356 | 0.349 | 0.333 | 0.343 | 0.362 | 0.354 | 287 |
| cpp-pool-O3-native | 0.363 | 0.346 | 0.349 | 0.370 | 0.371 | 0.368 | 0.362 | 0.345 | 0.370 | 0.358 | 0.360 | 287 |

## Appendix B: files

| File | Role |
| --- | --- |
| `benchmark/redblack_benchmark.py` | 24-mode harness (two Franko allocators, native + C++ controls) |
| `benchmark/redblack_franko.fr` | Franko benchmark source, `NextAllocator` |
| `benchmark/redblack_franko_sparse.fr` | Franko benchmark source, `SparseSetAllocator` |
| `benchmark/redblack_cpp.cpp` | C++ `std::set` reference |
| `benchmark/redblack_cpp_pool.cpp` | C++ same-data-structure control |
| `benchmark/redblack.md` | This report |
