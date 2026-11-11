# Red-black benchmark: Franko vs C++

**2,000,000 inserts + 2,000,000 deletes · 10 measured rounds per build · 2026-11-12**

This report compares Franko with C++ on the same red-black tree workload,
using two Franko allocator variants and two C++ references. Every build
uses the system allocator, is compiled by the same host toolchain, and is
left unstripped. The run used `g++ 15.2.0`, OpenJDK `25.0.5`, and one
pinned CPU under WSL2 on an Intel i5-12600K.

The headline is structural rather than code-generation-specific. Against
idiomatic `std::set`, Franko's array-pool tree is **1.04x faster at
maximum release optimization** and **1.66x faster under matched
AddressSanitizer builds**. Against a hand-written C++ port of the *same*
array-pool tree, the C++ control is **1.07x faster at max release**,
**1.22x faster when Franko uses the checked `-O` build**, and **1.19x
faster under ASan**. Most of the advantage over `std::set` therefore
comes from storage and allocation, not from a large code-generation
advantage.

The second Franko allocator, `SparseSetAllocator`, is at parity with the
existing `NextAllocator` on this workload: 1.00x–1.02x, with
`NextAllocator` slightly ahead in the release builds.

`-march=native` is flat for Franko (within 0.4%), gives no reliable gain
to the `std::set` release builds, and regresses the C++ pool `-O1` build
by 10.6%, the C++ pool `-O3` build by 3.6%, and the `std::set` ASan build
by 23.7%.

---

## 1. Bottom line

| Question | Answer |
| --- | --- |
| Fastest portable Franko build | Franko `-Omax`, **0.386 s** (`sparse-Omax` 0.392 s) |
| Fastest native Franko build | Franko `-Omax-native`, **0.386 s** (`sparse-Omax-native` 0.392 s) |
| Fastest sparse-set build | `sparse-Omax` / `sparse-Omax-native`, **0.392 s** |
| Franko `-Omax` vs C++ `std::set -O3` | **1.04x faster** |
| Franko `-Omax` vs same-data-structure C++ `-O3` | **1.07x slower** |
| Franko `-O` vs same-data-structure C++ `-O3` | **1.22x slower** |
| Sanitizer-matched, portable | **1.66x faster** than ASan `std::set` |
| Sanitizer-matched, same data structure | **1.19x slower** than the C++ control |
| Franko `default` → `-Omax` | **1.51x faster** |
| `SparseSetAllocator` vs `NextAllocator` | parity (1.00x–1.02x) |
| `-march=native` overall | flat for Franko; C++ regressions up to **+23.7%** |

Two defensible headline claims:

- **Against `std::set`:** the array-pool design is **1.04x faster** at
  maximum release optimization and **1.66x faster** when both sides use
  the same `-O1 + ASan + system allocator` setup.
- **Against the same array-pool design in C++:** Franko is close at
  release but not ahead. The C++ control is **1.07x faster** at max
  release, **1.22x faster** against Franko's checked `-O` build, and
  **1.19x faster** under ASan. The large `std::set` gap is a storage
  effect, not evidence of a fundamentally faster red-black algorithm or
  a large code-generation advantage.

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
Linux, so the binary-size comparison is controlled. `-march=native`
maps to `-march=alderlake` on this machine: AVX2, BMI, and BMI2 are
enabled, and the binary becomes host-specific.

### 2.5 Timing methodology

The harness is `benchmark/redblack_benchmark.py`:

```bash
uv run python benchmark/redblack_benchmark.py 10 --cpu 2
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

Absolute times are sensitive to the host, WSL2 scheduling, and CPU
frequency. Comparisons **within this report** are the authoritative
ones.

---

## 3. Results

### 3.1 Franko `NextAllocator`

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| default | 0.582 | 0.577 | 0.565 | 0.608 | 0.015 | 2.5% | 2122 |
| default-native | 0.581 | 0.578 | 0.569 | 0.612 | 0.012 | 2.0% | 2123 |
| -O | 0.439 | 0.439 | 0.431 | 0.447 | 0.006 | 1.3% | 27 |
| -O-native | 0.439 | 0.438 | 0.436 | 0.443 | 0.002 | 0.5% | 27 |
| -Omax | 0.386 | 0.384 | 0.380 | 0.396 | 0.004 | 1.1% | 27 |
| -Omax-native | 0.386 | 0.385 | 0.381 | 0.399 | 0.005 | 1.3% | 27 |

### 3.2 Franko `SparseSetAllocator`

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sparse-default | 0.582 | 0.580 | 0.571 | 0.599 | 0.008 | 1.3% | 2131 |
| sparse-default-native | 0.579 | 0.579 | 0.567 | 0.592 | 0.008 | 1.3% | 2132 |
| sparse-O | 0.447 | 0.448 | 0.434 | 0.454 | 0.006 | 1.3% | 27 |
| sparse-O-native | 0.445 | 0.445 | 0.439 | 0.453 | 0.004 | 1.0% | 27 |
| sparse-Omax | 0.392 | 0.393 | 0.385 | 0.399 | 0.005 | 1.2% | 27 |
| sparse-Omax-native | 0.392 | 0.392 | 0.383 | 0.398 | 0.004 | 1.1% | 27 |

### 3.3 C++ `std::set`

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| cpp-O1-asan | 0.964 | 0.965 | 0.943 | 0.977 | 0.011 | 1.2% | 2154 |
| cpp-O1-asan-native | 1.192 | 1.197 | 1.168 | 1.213 | 0.016 | 1.3% | 2151 |
| cpp-O1 | 0.453 | 0.456 | 0.436 | 0.462 | 0.009 | 1.9% | 154 |
| cpp-O1-native | 0.453 | 0.455 | 0.443 | 0.461 | 0.007 | 1.5% | 154 |
| cpp-O3 | 0.400 | 0.399 | 0.389 | 0.414 | 0.007 | 1.9% | 158 |
| cpp-O3-native | 0.405 | 0.406 | 0.392 | 0.416 | 0.008 | 1.9% | 158 |

### 3.4 C++ array-pool control

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| cpp-pool-O1-asan | 0.487 | 0.486 | 0.473 | 0.531 | 0.017 | 3.5% | 2326 |
| cpp-pool-O1-asan-native | 0.491 | 0.486 | 0.475 | 0.524 | 0.018 | 3.6% | 2327 |
| cpp-pool-O1 | 0.415 | 0.414 | 0.410 | 0.421 | 0.004 | 1.1% | 287 |
| cpp-pool-O1-native | 0.459 | 0.459 | 0.452 | 0.465 | 0.004 | 0.9% | 287 |
| cpp-pool-O3 | 0.360 | 0.359 | 0.348 | 0.374 | 0.008 | 2.2% | 287 |
| cpp-pool-O3-native | 0.372 | 0.372 | 0.368 | 0.378 | 0.003 | 0.9% | 287 |

### 3.5 Native effect

| Base build | Portable (s) | Native (s) | Native delta |
| --- | ---: | ---: | ---: |
| Franko default | 0.582 | 0.581 | **-0.3%** |
| Franko -O | 0.439 | 0.439 | **-0.0%** |
| Franko -Omax | 0.386 | 0.386 | **-0.0%** |
| Sparse default | 0.582 | 0.579 | **-0.4%** |
| Sparse -O | 0.447 | 0.445 | **-0.4%** |
| Sparse -Omax | 0.392 | 0.392 | **-0.1%** |
| cpp-O1-asan | 0.964 | 1.192 | **+23.7%** |
| cpp-O1 | 0.453 | 0.453 | **-0.1%** |
| cpp-O3 | 0.400 | 0.405 | **+1.3%** |
| cpp-pool-O1-asan | 0.487 | 0.491 | **+0.6%** |
| cpp-pool-O1 | 0.415 | 0.459 | **+10.6%** |
| cpp-pool-O3 | 0.360 | 0.372 | **+3.6%** |

Negative means native is faster. Franko's native builds are flat: all
six Franko/sparse build pairs are within 0.4% of portable. The large
regressions are in the C++ references, especially the ASan `std::set`
build and the C++ pool `-O1` build.

### 3.6 Head-to-head

**Franko vs C++ `std::set`**

| Comparison | Faster | Speedup | Context |
| --- | --- | ---: | --- |
| default vs cpp-O1-asan | Franko default | **1.66x** | Matched `-O1` + ASan + system allocator |
| default-native vs cpp-O1-asan-native | Franko default-native | 2.05x | Matched, but native hurts `std::set` ASan |
| -O vs cpp-O1 | Franko -O | **1.03x** | Franko `-O3` vs C++ `-O1` |
| -O-native vs cpp-O1-native | Franko -O-native | 1.03x | Same, native |
| -O vs cpp-O3 | C++ `std::set` -O3 | **1.10x** | Franko checked release vs C++ max |
| -O-native vs cpp-O3-native | C++ `std::set` -O3-native | 1.08x | Same, native |
| -Omax vs cpp-O3 | Franko -Omax | **1.04x** | Max vs max |
| -Omax-native vs cpp-O3-native | Franko -Omax-native | 1.05x | Max vs max, native |

**Franko vs the same-data-structure C++ control**

| Comparison | Faster | Speedup | Context |
| --- | --- | ---: | --- |
| -Omax vs cpp-pool-O3 | C++ pool -O3 | **1.07x** | Same array-pool design, both release |
| -Omax-native vs cpp-pool-O3-native | C++ pool -O3-native | 1.04x | Native |
| -O vs cpp-pool-O3 | C++ pool -O3 | **1.22x** | Franko checked vs C++ unchecked |
| -O-native vs cpp-pool-O3-native | C++ pool -O3-native | 1.18x | Same, native |
| default vs cpp-pool-O1-asan | C++ pool -O1-asan | **1.19x** | Same design, both ASan; Franko carries checks |
| default-native vs cpp-pool-O1-asan-native | C++ pool -O1-asan-native | 1.18x | Native, both ASan |

### 3.7 Franko allocator variants

| Comparison | Faster | Speedup | Context |
| --- | --- | ---: | --- |
| default vs sparse-default | Sparse | **1.00x** | Portable, ASan |
| -O vs sparse-O | NextAllocator | **1.02x** | Portable, checked release |
| -Omax vs sparse-Omax | NextAllocator | **1.02x** | Portable, unchecked release |
| default-native vs sparse-default-native | Sparse | 1.00x | Native, ASan |
| -O-native vs sparse-O-native | NextAllocator | 1.02x | Native, checked release |
| -Omax-native vs sparse-Omax-native | NextAllocator | 1.02x | Native, unchecked release |

The sparse-set pool is effectively tied with the free-list pool in the
ASan builds and about 2% slower in the release builds. Its intended
advantage — O(used) `write_active_indices` instead of O(capacity) — is
not exercised by this workload.

### 3.8 Decomposing the gap

**Release**

| Step | Ratio | Interpretation |
| --- | --- | --- |
| `std::set -O3` → C++ pool `-O3` | **1.11x** | Same language, better storage/allocator |
| C++ pool `-O3` → Franko `-Omax` | **1.07x** | Same design, C++ control faster |
| `std::set -O3` → Franko `-Omax` | **1.04x** | Net storage gain after the code-generation difference |

**Sanitizer-matched**

| Step | Effect | Interpretation |
| --- | --- | --- |
| ASan `std::set` → ASan C++ pool | **1.98x faster** | Per-node allocation is very expensive under ASan |
| ASan C++ pool → Franko default | **1.19x slower** | Checked Franko build vs unchecked C++ control |
| ASan `std::set` → Franko default | **1.66x faster** | Combined; storage gain partly returned as check cost |

So the sanitizer gap is not evidence that Franko's code generator is
2x better. It is evidence that `std::set`'s per-node allocation strategy
is punished much harder by ASan than a bulk pool. At release, the
storage gain is 1.11x and the generated-code difference costs Franko
1.07x, leaving a net 1.04x advantage over `std::set`.

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
linked-list update. That matches the measured 2% release difference and
the tie under ASan. On this workload the choice is mostly about the
enumeration API: the sparse set's O(used) `write_active_indices` is
useful when live slots must be visited; this tree never does that.

### 4.3 Is Franko's code generation competitive?

The control answers this more honestly than the `std::set` comparison:

- **Release:** the hand-written C++ pool is **1.07x faster** than Franko
  `-Omax`. Franko's checked `-O` build is **1.22x slower** than the same
  C++ pool. That is the same ballpark, with a modest advantage for the
  C++ control in both cases.
- **ASan:** the hand-written C++ pool at `-O1-asan` is **1.19x faster**
  than Franko default. Franko default also carries its own bounds and
  state checks on top of ASan, while the C++ control is unchecked, so
  part of that 19% is expected.
- **Against `std::set`:** the same-data-structure control removes most of
  Franko's advantage at max release, confirming that the 1.04x release
  win over `std::set` is mostly the data structure.

Generated code is competitive with careful hand-written C++ on the same
algorithm. It is not a large code-generation win.

### 4.4 The `-march=native` surprise

`-march=native` is often treated as a free performance flag. On this
workload it is not:

- **Franko builds:** all six Franko/sparse build pairs are within 0.4%
  of portable. The effect is effectively zero.
- **C++ `std::set`:** `-O1` is unchanged, `-O3` is 1.3% slower, and the
  ASan build is **23.7% slower**.
- **C++ array-pool control:** `-O3` is 3.6% slower, `-O1` is **10.6%
  slower**, and the ASan build is 0.6% slower.

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

All measured modes have a coefficient of variation between **0.5% and
3.6%**. The largest spreads are `cpp-pool-O1-asan-native` (3.6%),
`cpp-pool-O1-asan` (3.5%), and Franko `default` (2.5%); several Franko
and pool modes are below 1.5%. Differences below ~5% should be read as
small effects, not precise constants. Most of the secondary comparisons
in this report are in that band; the 1.66x sanitizer gap and the 1.22x
checked-release gap against the C++ pool are not.

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
   state checks; the C++ ASan control is unchecked. The 1.19x
   default-vs-pool gap therefore includes check overhead and is not a
   pure code-generation comparison.
5. **Native builds are host-specific.** `-march=native` produced
   `-march=alderlake` here. These binaries will not run on older CPUs.
6. **Whole-process timing.** Insert, audit/walk, delete, output, and
   teardown are one number. We cannot say how much of the gap is
   insertion versus deletion.
7. **WSL2 and one machine.** The benchmark runs under WSL2 on a
   12th-gen i5-12600K. CPU frequency, host scheduling, and WSL2
   virtualisation add noise despite CPU pinning.
8. **No hardware counters.** `perf` is not available in this WSL2
   environment, so cache misses, branch misses, and instructions per
   cycle are inferred from timing and layout, not measured.
9. **Binary size depends on build class.** Release binaries are
   unstripped and use the system allocator: Franko is 27 KB, the C++
   pool is 287 KB, and `std::set` is 154–158 KB. ASan builds are
   2.1–2.3 MB because they carry instrumentation and debug info. The
   release comparison is fair because all binaries use the same
   compiler, system allocator, and static C++ runtime.
10. **No allocator-vs-system control.** Franko and C++ both use the
    system allocator; the workload's pool makes only two large
    allocations, so a different allocator would not be expected to
    change the result materially.

---

## 6. Recommendations

For this codebase and workload:

- **Use portable `-Omax` for maximum Franko speed.** It is the fastest
  Franko build (0.386 s for `NextAllocator`, 0.392 s for
  `SparseSetAllocator`) and avoids host-specific binaries.
- **Do not add `-march=native` by default.** It gives no reliable
  Franko gain and can regress C++ controls substantially.
- **Keep `NextAllocator` unless live-slot enumeration matters.**
  `SparseSetAllocator` is at parity under ASan and about 2% slower in
  release on this workload; its O(used) enumeration is its real
  advantage.
- **Compare Franko to the same data structure when making language
  claims.** Against the C++ pool control, Franko is 1.07x slower at max
  release and 1.22x slower in checked `-O`; under ASan the checked
  default build is 1.19x slower than the unchecked control. Against
  `std::set`, the array-pool design is the main advantage.
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

These are the 10 measured rounds per mode in the order recorded. The
mode order within each round was randomized; the columns are round
numbers, not a fixed execution sequence. Times are rounded to three
decimals; the summary statistics in §3 are computed from the unrounded
timings.

**Franko `NextAllocator`**

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| default | 0.587 | 0.608 | 0.608 | 0.565 | 0.572 | 0.580 | 0.574 | 0.576 | 0.574 | 0.577 | 0.582 | 2122 |
| default-native | 0.612 | 0.586 | 0.577 | 0.580 | 0.569 | 0.580 | 0.572 | 0.578 | 0.579 | 0.574 | 0.581 | 2123 |
| -O | 0.443 | 0.437 | 0.438 | 0.440 | 0.432 | 0.431 | 0.431 | 0.443 | 0.447 | 0.445 | 0.439 | 27 |
| -O-native | 0.439 | 0.437 | 0.440 | 0.437 | 0.437 | 0.436 | 0.438 | 0.440 | 0.443 | 0.438 | 0.439 | 27 |
| -Omax | 0.385 | 0.384 | 0.389 | 0.396 | 0.382 | 0.383 | 0.384 | 0.380 | 0.389 | 0.386 | 0.386 | 27 |
| -Omax-native | 0.383 | 0.386 | 0.383 | 0.385 | 0.386 | 0.383 | 0.384 | 0.381 | 0.399 | 0.386 | 0.386 | 27 |

**Franko `SparseSetAllocator`**

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sparse-default | 0.580 | 0.583 | 0.578 | 0.588 | 0.580 | 0.599 | 0.582 | 0.571 | 0.579 | 0.575 | 0.582 | 2131 |
| sparse-default-native | 0.584 | 0.587 | 0.567 | 0.585 | 0.574 | 0.576 | 0.577 | 0.570 | 0.592 | 0.581 | 0.579 | 2132 |
| sparse-O | 0.445 | 0.443 | 0.448 | 0.434 | 0.444 | 0.448 | 0.450 | 0.453 | 0.451 | 0.454 | 0.447 | 27 |
| sparse-O-native | 0.443 | 0.444 | 0.440 | 0.445 | 0.445 | 0.439 | 0.445 | 0.453 | 0.449 | 0.450 | 0.445 | 27 |
| sparse-Omax | 0.389 | 0.396 | 0.396 | 0.387 | 0.385 | 0.391 | 0.390 | 0.395 | 0.395 | 0.399 | 0.392 | 27 |
| sparse-Omax-native | 0.396 | 0.391 | 0.394 | 0.393 | 0.392 | 0.383 | 0.392 | 0.398 | 0.390 | 0.391 | 0.392 | 27 |

**C++ `std::set`**

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| cpp-O1-asan | 0.975 | 0.964 | 0.977 | 0.967 | 0.943 | 0.954 | 0.957 | 0.974 | 0.972 | 0.956 | 0.964 | 2154 |
| cpp-O1-asan-native | 1.205 | 1.213 | 1.198 | 1.195 | 1.210 | 1.198 | 1.186 | 1.175 | 1.173 | 1.168 | 1.192 | 2151 |
| cpp-O1 | 0.462 | 0.455 | 0.456 | 0.448 | 0.457 | 0.460 | 0.461 | 0.442 | 0.453 | 0.436 | 0.453 | 154 |
| cpp-O1-native | 0.443 | 0.446 | 0.455 | 0.454 | 0.450 | 0.458 | 0.460 | 0.461 | 0.443 | 0.456 | 0.453 | 154 |
| cpp-O3 | 0.399 | 0.403 | 0.395 | 0.398 | 0.408 | 0.399 | 0.399 | 0.391 | 0.389 | 0.414 | 0.400 | 158 |
| cpp-O3-native | 0.398 | 0.392 | 0.400 | 0.407 | 0.410 | 0.407 | 0.399 | 0.416 | 0.414 | 0.405 | 0.405 | 158 |

**C++ array-pool control**

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| cpp-pool-O1-asan | 0.492 | 0.486 | 0.493 | 0.486 | 0.531 | 0.475 | 0.475 | 0.476 | 0.473 | 0.487 | 0.487 | 2326 |
| cpp-pool-O1-asan-native | 0.491 | 0.486 | 0.475 | 0.486 | 0.520 | 0.478 | 0.491 | 0.524 | 0.479 | 0.477 | 0.491 | 2327 |
| cpp-pool-O1 | 0.419 | 0.421 | 0.414 | 0.411 | 0.410 | 0.414 | 0.419 | 0.412 | 0.410 | 0.421 | 0.415 | 287 |
| cpp-pool-O1-native | 0.463 | 0.465 | 0.458 | 0.452 | 0.459 | 0.457 | 0.455 | 0.462 | 0.459 | 0.461 | 0.459 | 287 |
| cpp-pool-O3 | 0.353 | 0.374 | 0.355 | 0.364 | 0.348 | 0.361 | 0.358 | 0.353 | 0.369 | 0.360 | 0.360 | 287 |
| cpp-pool-O3-native | 0.368 | 0.373 | 0.372 | 0.376 | 0.371 | 0.378 | 0.372 | 0.373 | 0.375 | 0.368 | 0.372 | 287 |

---

## Appendix B: files

| File | Role |
| --- | --- |
| `benchmark/redblack_benchmark.py` | 24-mode harness (two Franko allocators, native + C++ controls) |
| `benchmark/redblack_franko.fr` | Franko benchmark source, `NextAllocator` |
| `benchmark/redblack_franko_sparse.fr` | Franko benchmark source, `SparseSetAllocator` |
| `benchmark/redblack_cpp.cpp` | C++ `std::set` reference |
| `benchmark/redblack_cpp_pool.cpp` | C++ same-data-structure control |
| `benchmark/redblack.md` | This report |
