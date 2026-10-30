# Red-black benchmark: Franko vs C++

**2,000,000 inserts + 2,000,000 deletes · 10 measured rounds per build · 2026-10-31**

This replaces the previous report with a controlled rerun. It adds two
things the earlier comparison could not answer:

1. **`-march=native`** for every build, so the portable and native
   variants can be compared directly.
2. **A same-data-structure C++ control** (`redblack_cpp_pool.cpp`): the
   Franko array-pool red-black tree, written independently in C++14.
   This separates "Franko's data structure is faster than `std::set`"
   from "Franko generates better code than a C++ compiler."

The short version: against idiomatic `std::set`, Franko is **1.18x
faster at maximum release optimization** and **2.00x faster under
matched AddressSanitizer builds**. Against a hand-written C++ port of
the *same* array-pool tree, Franko is **1.07x faster at max release**
and its checked `-O` build is **1.02x slower** than the C++ control's
unchecked `-O3` build. Most of the gap to `std::set` is the array-pool
design, not the language or code generator.

`-march=native` is not a free win on this workload. It is neutral for
Franko release builds, and it *regresses* two C++ builds substantially
(up to +22.6% time). Portable builds are the safer default.

---

## 1. Bottom line

| Question | Answer |
| --- | --- |
| Fastest portable build | Franko `-Omax`, **0.336 s** |
| Fastest native build | Franko `-Omax-native`, **0.334 s** |
| Franko `-Omax` vs C++ `std::set -O3` | **1.18x faster** |
| Franko `-Omax` vs same-data-structure C++ `-O3` | **1.07x faster** |
| Franko `-O` vs same-data-structure C++ `-O3` | **1.02x slower** (parity) |
| Sanitizer-matched, portable | **2.00x faster** than ASan `std::set` |
| Sanitizer-matched, same data structure | **1.05x slower** than the C++ control |
| Franko `default` → `-Omax` | **1.49x faster** |
| `-march=native` overall | no consistent win; regressions up to **+22.6%** |

Two defensible headline claims:

- **Against `std::set`:** Franko's array-pool implementation is
  **1.18x faster** at maximum release optimization and **2.00x faster**
  when both sides use the same `-O1 + ASan + system allocator` setup.
- **Against the same array-pool design in C++:** Franko is within
  roughly ±7% of hand-written C++. The large advantage over `std::set`
  comes from storage/allocation, not from a fundamentally faster
  red-black algorithm or a huge code-generation advantage.

The rest of this report shows the raw numbers and how those
conclusions follow.

---

## 2. What was measured

### 2.1 Workload

All three programs execute the same insert/delete workload:

1. Insert **2,000,000 distinct `int32` keys**. The key pattern walks
   inward from both ends of `[-999999, 1000000]`:
   `-999999, 1000000, -999998, 999999, ...`
2. Walk the tree in order, accumulating count and key sum. Franko and
   the C++ pool control also audit BST order, red-red violations, and
   black-height.
3. Delete all 2,000,000 keys in the same order.
4. Verify the tree is empty.

Every key is inserted once and deleted once. All three programs end
with an empty tree.

### 2.2 The three implementations

| Program | What it is | Storage |
| --- | --- | --- |
| `redblack_franko.fr` | Franko source, compiled by `compile.py` to C++14 and then by `g++` | One heap `[Node]` array + `NextAllocator` free list |
| `redblack_cpp.cpp` | Hand-written C++ reference using `std::set<int32_t>` | One heap allocation per node, libstdc++ RB tree |
| `redblack_cpp_pool.cpp` | Hand-written C++ port of the *same* array-pool tree as Franko | One `std::vector<Node>` + `std::vector<uint32_t>` free list |

The first comparison is the idiomatic one: what a Franko programmer
writes versus what a C++ programmer typically reaches for. The second
is the control: same data structure, same algorithm, same output, so
the remaining difference is language/code generation/runtime.

The C++ pool control prints byte-for-byte the same summary as Franko
(`count`, `sum`, `bad`, `bh`, `empty`, `used`). That makes output
equality a direct correctness check for the control.

### 2.3 Build matrix

| Group | Portable builds | Native builds | Notes |
| --- | --- | --- | --- |
| Franko | `default`, `-O`, `-Omax` | `default-native`, `-O-native`, `-Omax-native` | Native via `compile.py --target native` |
| C++ `std::set` | `cpp-O1-asan`, `cpp-O1`, `cpp-O3` | same three + `-march=native` | Static libstdc++/libgcc |
| C++ array-pool control | `cpp-pool-O1-asan`, `cpp-pool-O1`, `cpp-pool-O3` | same three + `-march=native` | Static libstdc++/libgcc |

Build modes:

| Label | Optimization | Runtime / allocator | Franko checks | ASan |
| --- | --- | --- | --- | --- |
| `default` | `-O1` | System allocator | Checked | Yes |
| `-O` | `-O3` | Static jemalloc | Checked | No |
| `-Omax` | `-O3` | Static jemalloc | Unchecked | No |
| `cpp-O1-asan` | `-O1` | System allocator | — | Yes |
| `cpp-O1` | `-O1` | System allocator | — | No |
| `cpp-O3` | `-O3` | System allocator | — | No |
| `cpp-pool-*` | `-O1` or `-O3` | System allocator | `std::vector` unchecked | ASan on `*-asan` |

`-march=native` maps to `-march=alderlake` on this machine. It enables
AVX2, BMI/BMI2, and other Alder Lake features, and implies Alder Lake
tuning. It also makes the binary host-specific.

### 2.4 Timing methodology

The extended harness is `benchmark/benchmark_matrix.py`:

```bash
uv run python benchmark/benchmark_matrix.py 10 --cpu 2
```

- All 18 binaries are built **before** any timing, so compiler heat and
  build order do not sit immediately in front of one mode's runs.
- Each binary gets one **untimed warm-up**.
- Then 10 measured rounds. Within each round the 18 modes are run in a
  **randomized order** (fixed seed), and the process is **pinned to one
  CPU** (`--cpu 2`). This spreads thermal/frequency drift and position
  effects across all modes instead of letting them accumulate on
  whichever mode runs last.
- Timing is whole-process wall clock with `time.perf_counter()`,
  including startup, the tree work, the audit/walk, output, and
  teardown.
- Every build's stdout is checked. Franko builds must agree with each
  other; `std::set` builds must agree with each other; the pool control
  must agree with Franko.

Absolute times are a little different from the simpler
`benchmark.py` run because of pinning, warm-up, and interleaving.
Comparisons **within this table** are the authoritative ones; a
cross-check against the simple harness is in §4.6.

---

## 3. Results

### 3.1 Portable builds

**Franko**

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| default | 0.501 | 0.500 | 0.495 | 0.511 | 0.005 | 1.0% | 436 |
| -O | 0.365 | 0.361 | 0.356 | 0.377 | 0.008 | 2.2% | 1000 |
| -Omax | 0.336 | 0.337 | 0.328 | 0.346 | 0.005 | 1.5% | 996 |

**C++ `std::set`**

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| cpp-O1-asan | 1.003 | 1.006 | 0.985 | 1.014 | 0.010 | 1.0% | 245 |
| cpp-O1 | 0.446 | 0.443 | 0.438 | 0.463 | 0.009 | 2.0% | 154 |
| cpp-O3 | 0.398 | 0.399 | 0.374 | 0.419 | 0.014 | 3.4% | 158 |

**C++ array-pool control**

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| cpp-pool-O1-asan | 0.475 | 0.474 | 0.466 | 0.485 | 0.006 | 1.2% | 418 |
| cpp-pool-O1 | 0.411 | 0.410 | 0.406 | 0.416 | 0.003 | 0.8% | 287 |
| cpp-pool-O3 | 0.360 | 0.361 | 0.347 | 0.376 | 0.008 | 2.3% | 287 |

### 3.2 Native builds

| test | mean (s) | median | min | max | SD | CV | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| default-native | 0.507 | 0.504 | 0.499 | 0.532 | 0.009 | 1.9% | 437 |
| -O-native | 0.365 | 0.364 | 0.356 | 0.371 | 0.005 | 1.3% | 1000 |
| -Omax-native | 0.334 | 0.331 | 0.328 | 0.351 | 0.008 | 2.3% | 1000 |
| cpp-O1-asan-native | 1.229 | 1.235 | 1.198 | 1.264 | 0.019 | 1.6% | 246 |
| cpp-O1-native | 0.449 | 0.448 | 0.426 | 0.469 | 0.014 | 3.2% | 154 |
| cpp-O3-native | 0.398 | 0.398 | 0.380 | 0.416 | 0.012 | 3.1% | 158 |
| cpp-pool-O1-asan-native | 0.482 | 0.478 | 0.470 | 0.515 | 0.013 | 2.7% | 419 |
| cpp-pool-O1-native | 0.454 | 0.453 | 0.448 | 0.461 | 0.004 | 1.0% | 287 |
| cpp-pool-O3-native | 0.370 | 0.371 | 0.355 | 0.382 | 0.009 | 2.5% | 287 |

### 3.3 Native effect

| Base build | Portable (s) | Native (s) | Native delta |
| --- | --- | --- | --- |
| default | 0.501 | 0.507 | **+1.0%** |
| -O | 0.365 | 0.365 | **-0.2%** |
| -Omax | 0.336 | 0.334 | **-0.8%** |
| cpp-O1-asan | 1.003 | 1.229 | **+22.6%** |
| cpp-O1 | 0.446 | 0.449 | **+0.8%** |
| cpp-O3 | 0.398 | 0.398 | **0.0%** |
| cpp-pool-O1-asan | 0.475 | 0.482 | **+1.4%** |
| cpp-pool-O1 | 0.411 | 0.454 | **+10.5%** |
| cpp-pool-O3 | 0.360 | 0.370 | **+2.8%** |

Negative means native is faster. For Franko release builds the effect
is noise-level. The two large positives are real regressions (§4.1).

### 3.4 Head-to-head

**Franko vs C++ `std::set`**

| Comparison | Faster | Speedup | Context |
| --- | --- | --- | --- |
| default vs cpp-O1-asan | Franko default | **2.00x** | Matched `-O1` + ASan + system allocator |
| default-native vs cpp-O1-asan-native | Franko default-native | 2.43x | Matched, but native hurts `std::set` ASan |
| -O vs cpp-O1 | Franko -O | **1.22x** | Franko uses `-O3` + jemalloc |
| -O vs cpp-O3 | Franko -O | **1.09x** | Franko `-O` vs C++ max |
| -Omax vs cpp-O3 | Franko -Omax | **1.18x** | Max vs max |
| -Omax-native vs cpp-O3-native | Franko -Omax-native | 1.19x | Max vs max, native |

**Franko vs the same-data-structure C++ control**

| Comparison | Faster | Speedup | Context |
| --- | --- | --- | --- |
| -Omax vs cpp-pool-O3 | Franko -Omax | **1.07x** | Same array-pool design, both release |
| -O vs cpp-pool-O3 | C++ pool -O3 | **1.02x** | Franko checked vs C++ unchecked |
| default vs cpp-pool-O1-asan | C++ pool -O1-asan | **1.05x** | Same design, both ASan |
| -Omax-native vs cpp-pool-O3-native | Franko -Omax-native | 1.11x | Native, but pool native regresses |
| default-native vs cpp-pool-O1-asan-native | C++ pool | 1.05x | Native, both ASan |

### 3.5 Decomposing the gap

The control lets us split the Franko-vs-`std::set` result into a
data-structure component and a code-generation component. These are
ratios of means, not a formal factorial experiment, but they are
informative:

**Release**

| Step | Ratio | Interpretation |
| --- | --- | --- |
| `std::set -O3` → C++ pool `-O3` | **1.11x** | Same language, better storage/allocator |
| C++ pool `-O3` → Franko `-Omax` | **1.07x** | Same design, generated code advantage |
| `std::set -O3` → Franko `-Omax` | **1.18x** | Combined |

**Sanitizer-matched**

| Step | Ratio | Interpretation |
| --- | --- | --- |
| ASan `std::set` → ASan C++ pool | **2.11x** | Per-node allocation is very expensive under ASan |
| ASan C++ pool → Franko default | **1.05x** | Hand-written C++ is slightly faster here |
| ASan `std::set` → Franko default | **2.00x** | Combined; almost all of it is storage |

So the large sanitizer gap is not evidence that Franko's code
generator is 2x better. It is evidence that `std::set`'s per-node
allocation strategy is punished much harder by ASan than a bulk pool.
At release, the data-structure advantage and the code-generation
advantage are both real but modest: 1.11x × 1.07x ≈ 1.18x.

---

## 4. Analysis

### 4.1 The `-march=native` surprise

`-march=native` is often treated as a free performance flag. On this
workload it is not:

- **Franko release:** `-O` is unchanged (-0.2%), `-Omax` is 0.8%
  faster. That is within run noise and not worth losing portability.
- **C++ `std::set`:** `-O1` and `-O3` are unchanged, but the ASan build
  is **22.6% slower**. This is not a fluke: a separate 10-round
  replication measured **+23.1%**.
- **C++ array-pool control:** `-O3` is 2.8% slower, and the `-O1` build
  is **10.5% slower**. The `-O1` regression replicated at **+10.2%**.

To see whether this is one phenomenon, a smaller follow-up experiment
(5 rounds each) split `-march=native` into its two independent parts:
the instruction-set level and the tuning target.

| Build | Baseline | `-march=native` | `-march=x86-64-v3` | `-march=alderlake -mtune=generic` | `-mtune=native` |
| --- | --- | --- | --- | --- | --- |
| `cpp-O1-asan` | 1.006 s | 1.234 s (+22.6%) | 1.234 s (+22.6%) | 1.251 s (+24.3%) | 1.010 s (+0.3%) |
| `cpp-pool-O1` | 0.414 s | 0.460 s (+11.0%) | 0.412 s (-0.7%) | 0.417 s (+0.7%) | 0.460 s (+11.1%) |

The two regressions have different triggers:

- For `std::set` under ASan, the newer-ISA `-march` variants (including
  generic `x86-64-v3`) trigger the slowdown; `-mtune=native` alone does
  not.
- For the array-pool control at `-O1`, Alder Lake **tuning** triggers
  it; the same ISA level with generic tuning does not.

The exact microarchitectural reason is not established here. Both are
branchy, scalar, pointer/index-chasing workloads with no meaningful
vector loop to speed up, so the most likely explanation is different
instruction selection, inlining, and code layout disturbing branch
prediction/I-cache behaviour. The practical lesson is simpler:
`-march=native` is code-shape-dependent and must be measured, not
assumed.

It also means the **native sanitizer comparison is misleading**:
Franko default-native vs `std::set` ASan-native is 2.43x, but the
`std::set` side is itself 22.6% slower than its portable build, so that
ratio is inflated. The portable matched result, **2.00x**, is the fair
number.

### 4.2 Franko build modes

| Change | Portable | Native |
| --- | --- | --- |
| `default` → `-O` | 1.37x | 1.39x |
| `-O` → `-Omax` | 1.09x | 1.09x |
| `default` → `-Omax` | 1.49x | 1.52x |

- `default` is `-O1` + ASan + system allocator. `-O` switches to `-O3`,
  jemalloc, and a checked non-ASan runtime. The 1.37x gain is the
  combination, not a single flag.
- `-O` → `-Omax` removes Franko's bounds and `unsafe_cast` checks. The
  9% gain here (5% in the earlier run) is consistent with "checks are
  cheap but not free" on this shape of code. Both builds use the same
  `-O3` + jemalloc link, so this is the cleanest bounds-check
  measurement in the set.
- `default` → `-Omax` is 1.49x end to end.

The earlier simple-harness run measured 1.39x / 1.05x / 1.46x. The
direction is the same; the exact `-Omax` step moves between ~5% and
~9% depending on the run. Treat it as a small, workload-dependent win,
not a fixed number.

### 4.3 Why the array pool wins

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

The 40-byte `std::set` node figure is `sizeof` on this
toolchain/libstdc++; allocator metadata adds more. The pool's total
payload is about 48 MB, roughly 60% of `std::set`'s node payload
before allocator overhead.

That explains both the release gap and the sanitizer gap:

- In release, the contiguous array and 32-bit links mean fewer cache
  misses and less memory traffic per tree step, and the free list turns
  allocation/release into two array operations. The C++ pool control
  captures this and gets within 7% of Franko.
- Under ASan, every `std::set` node allocation gets redzones and
  allocator bookkeeping. The array pool gets one big region, so ASan's
  per-node cost largely disappears. That is why `std::set`'s ASan tax
  is **2.25x**, while the pool's is only **1.16x** at the same `-O1`
  level.

The trade-off is real: 32-bit indices cap the tree at ~4.29 billion
nodes, whereas `std::set` uses full pointers. For 2M nodes that is
irrelevant; for a general-purpose container it would matter. The pool
also reserves the full 2M-slot array up front; `std::set` grows on
demand.

### 4.4 Is Franko's code generation better?

The control answers this more honestly than the `std::set` comparison:

- **Release:** Franko `-Omax` is **1.07x faster** than the hand-written
  C++ pool at `-O3`. Franko `-O` (checked) is **1.02x slower** than the
  same C++ pool. So generated Franko code is approximately at parity,
  with a small advantage in the unchecked build and a small deficit
  when the checked runtime is included.
- **ASan:** The hand-written C++ pool at `-O1-asan` is **1.05x faster**
  than Franko default. Franko default also carries its own bounds
  checks on top of ASan, so some of that 5% is expected.
- **Against `std::set`:** the same-data-structure control removes most
  of Franko's advantage, confirming that the 1.18x release win is
  mostly the data structure, not the compiler.

This is a good result for the language: generated code is competitive
with careful hand-written C++ on the same algorithm. It is not a 2x
code-generation win.

### 4.5 Correctness

All builds produced identical output within their group, and the pool
control matched Franko exactly:

```text
Franko / C++ pool control:
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
- `bh=20` is the black-height after inserts. For 2M nodes this is well
  within the red-black bound (`≤ 2·log2(2M+1) ≈ 42`) and is consistent
  across every rep.
- `used=0` confirms every pool slot was returned; `empty=1` and
  `size=0` confirm every key was deleted.
- Franko's audit is stronger than the `std::set` check: it verifies
  red-red violations and black-height, while the C++ reference only
  checks order, count, and sum. If anything, the extra audit work
  slightly penalizes Franko in the timing.

The C++ pool control uses the same audit and produces the same
`bh`/`used` values, so the control is not just "some C++ program that
also prints 2M"; it is structurally equivalent.

### 4.6 Noise and reproducibility

All measured modes have a coefficient of variation between **0.8% and
3.4%**. The largest spreads are `cpp-O3` (3.4%) and `cpp-O1-native`
(3.2%). Differences below ~5% should be read as small effects, not
precise constants.

Cross-check against the earlier simple `benchmark.py` run on the same
machine (unpinned, no warm-up, sequential modes):

| Build | Earlier simple run | This matrix (portable) | Change |
| --- | --- | --- | --- |
| default | 0.503 | 0.501 | -0.3% |
| -O | 0.362 | 0.365 | +0.9% |
| -Omax | 0.345 | 0.336 | -2.5% |
| cpp-O1-asan | 1.001 | 1.003 | +0.2% |
| cpp-O1 | 0.437 | 0.446 | +1.9% |
| cpp-O3 | 0.381 | 0.398 | +4.6% |

The portable numbers are consistent to within a few percent. The
`std::set -O3` value moved the most (4.6%), which is why the exact
"Franko vs C++ max" ratio has ranged from **1.10x to 1.18x** across
the last runs. The direction (Franko slightly faster) is stable; the
magnitude is not precise to two decimals. Quote **~1.1–1.2x**, not
1.18x as a universal constant.

The two large native regressions were independently reproduced
(+23.1% for `std::set` ASan, +10.2% for pool `-O1`), so they are not
single-run outliers. The five-round probe in §4.1 is a follow-up, not
part of the main 10-round table.

---

## 5. Limitations

1. **One workload, one key pattern.** Keys arrive from both ends of the
   range and are deleted in the same order. A sorted, random, or
   adversarial insertion order would exercise different rebalancing
   paths. This is not a general "Franko is X times faster" result.
2. **Different implementations in the main comparison.** Franko's
   array-pool tree is not `std::set`. The control narrows this, but it
   is still a hand-written C++ port, not a formally identical program.
3. **Allocator and runtime confounds.** Franko `-O`/`-Omax` link
   jemalloc; the C++ builds use the system allocator. The effect is
   likely small here because the pool makes only two large allocations,
   but it is not controlled. Conversely, `std::set` could be given a
   pool allocator, which would likely close much of the gap.
4. **Native builds are host-specific.** `-march=native` produced
   `-march=alderlake` here. These binaries will not run on older CPUs.
   The native regressions may be specific to GCC 15.2 and this CPU.
5. **Whole-process timing.** Insert, audit/walk, delete, output, and
   teardown are one number. We cannot say how much of the gap is
   insertion versus deletion. The audit is also asymmetric: Franko does
   more verification work than the C++ reference, which biases against
   Franko slightly.
6. **WSL2 and one machine.** The benchmark runs under WSL2 on a
   12th-gen i5-12600K. CPU frequency, host scheduling, and WSL2
   virtualisation add noise. `taskset`/`--cpu 2` reduces migration but
   cannot control the host.
7. **No hardware counters.** `perf` is not available in this WSL2
   environment, so cache misses, branch misses, and instructions per
   cycle are inferred from timing and layout, not measured.
8. **Binary size is dominated by runtimes.** The Franko `-O`/`-Omax`
   binaries are ~1 MB because jemalloc is statically linked; the C++
   binaries are 154–158 KB with static libstdc++/libgcc. This is not a
   code-size comparison between languages.

---

## 6. Recommendations

For this codebase and workload:

- **Use portable `-Omax` for maximum Franko speed.** It is the fastest
  Franko build and avoids host-specific binaries. `-O` is the safer
  checked release; the check cost is ~5–9% here.
- **Do not add `-march=native` by default.** It gives at most a
  sub-1% Franko release gain and can regress other builds badly. If you
  want it, benchmark each target and keep a portable fallback.
- **Compare Franko to the same data structure when making language
  claims.** Against the C++ pool control, Franko is within a few
  percent; against `std::set`, the array-pool design is the main
  advantage.
- **For C++ users, the actionable item is allocation.** A pool
  allocator for `std::set`, or a custom array-pool tree, captures most
  of the difference. `std::set` itself is a general-purpose container
  with different trade-offs, not a like-for-like benchmark opponent.
- **For future measurements:** add phase timings (insert / audit /
  delete), peak RSS, PGO/LTO variants, clang, bare-metal Linux, and
  more key orders. A formal confidence interval or paired test would
  strengthen the small-difference claims.

---

## 7. Reproduce

```bash
# Extended matrix: portable + native + same-data-structure control
uv run python benchmark/benchmark_matrix.py 10 --cpu 2

# Original six-mode harness (portable only)
uv run python benchmark/benchmark.py 10
```

The extended harness builds:

- Franko via `compile.py` (add `--target native` for the native group);
- `std::set` from `benchmark/redblack_cpp.cpp`;
- the array-pool control from `benchmark/redblack_cpp_pool.cpp`.

All source and scripts are in `benchmark/`. Generated `.cpp` files
(such as `redblack_franko.cpp`), `.out` binaries, and `.bin` files are
build artifacts and are ignored by git.

---

## Appendix A: raw measured runs

These are the 10 measured rounds per mode in the order recorded. The
mode order within each round was randomized; the columns are round
numbers, not a fixed execution sequence.

**Franko**

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| default | 0.495 | 0.501 | 0.497 | 0.509 | 0.511 | 0.503 | 0.503 | 0.498 | 0.498 | 0.499 | 0.501 | 436 |
| default-native | 0.506 | 0.504 | 0.503 | 0.508 | 0.502 | 0.499 | 0.532 | 0.507 | 0.505 | 0.500 | 0.507 | 437 |
| -O | 0.371 | 0.375 | 0.377 | 0.359 | 0.360 | 0.362 | 0.356 | 0.359 | 0.360 | 0.374 | 0.365 | 1000 |
| -O-native | 0.363 | 0.362 | 0.360 | 0.356 | 0.371 | 0.370 | 0.365 | 0.363 | 0.370 | 0.366 | 0.365 | 1000 |
| -Omax | 0.331 | 0.346 | 0.328 | 0.336 | 0.337 | 0.337 | 0.334 | 0.334 | 0.342 | 0.338 | 0.336 | 996 |
| -Omax-native | 0.330 | 0.328 | 0.330 | 0.332 | 0.351 | 0.334 | 0.329 | 0.331 | 0.328 | 0.343 | 0.334 | 1000 |

**C++ `std::set`**

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| cpp-O1-asan | 0.985 | 1.001 | 1.008 | 0.990 | 1.003 | 1.010 | 0.996 | 1.014 | 1.008 | 1.014 | 1.003 | 245 |
| cpp-O1-asan-native | 1.226 | 1.235 | 1.210 | 1.198 | 1.243 | 1.208 | 1.239 | 1.264 | 1.234 | 1.237 | 1.229 | 246 |
| cpp-O1 | 0.438 | 0.441 | 0.439 | 0.438 | 0.438 | 0.449 | 0.458 | 0.447 | 0.445 | 0.463 | 0.446 | 154 |
| cpp-O1-native | 0.447 | 0.426 | 0.438 | 0.437 | 0.469 | 0.469 | 0.460 | 0.454 | 0.449 | 0.441 | 0.449 | 154 |
| cpp-O3 | 0.399 | 0.389 | 0.374 | 0.399 | 0.406 | 0.386 | 0.419 | 0.409 | 0.412 | 0.390 | 0.398 | 158 |
| cpp-O3-native | 0.381 | 0.391 | 0.397 | 0.394 | 0.380 | 0.409 | 0.411 | 0.416 | 0.399 | 0.406 | 0.398 | 158 |

**C++ array-pool control**

| test | r1 | r2 | r3 | r4 | r5 | r6 | r7 | r8 | r9 | r10 | mean | bin (KB) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| cpp-pool-O1-asan | 0.474 | 0.483 | 0.466 | 0.475 | 0.474 | 0.478 | 0.472 | 0.485 | 0.476 | 0.471 | 0.475 | 418 |
| cpp-pool-O1-asan-native | 0.515 | 0.474 | 0.472 | 0.483 | 0.470 | 0.479 | 0.476 | 0.481 | 0.478 | 0.491 | 0.482 | 419 |
| cpp-pool-O1 | 0.413 | 0.408 | 0.410 | 0.406 | 0.407 | 0.414 | 0.410 | 0.408 | 0.414 | 0.416 | 0.411 | 287 |
| cpp-pool-O1-native | 0.448 | 0.461 | 0.453 | 0.450 | 0.448 | 0.458 | 0.453 | 0.452 | 0.457 | 0.457 | 0.454 | 287 |
| cpp-pool-O3 | 0.354 | 0.363 | 0.376 | 0.351 | 0.364 | 0.362 | 0.356 | 0.363 | 0.347 | 0.361 | 0.360 | 287 |
| cpp-pool-O3-native | 0.355 | 0.372 | 0.367 | 0.356 | 0.365 | 0.376 | 0.382 | 0.373 | 0.382 | 0.370 | 0.370 | 287 |

---

## Appendix B: files

| File | Role |
| --- | --- |
| `benchmark/benchmark.py` | Original six-mode portable harness |
| `benchmark/benchmark_matrix.py` | Extended 18-mode harness (native + control) |
| `benchmark/redblack_franko.fr` | Franko benchmark source |
| `benchmark/redblack_cpp.cpp` | C++ `std::set` reference |
| `benchmark/redblack_cpp_pool.cpp` | C++ same-data-structure control |
| `benchmark/redblack.md` | This report |
