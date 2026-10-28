# franko/pool

The `franko/pool` module is Franko's pool-allocator library. It hands
out integer indices ("slots") into a fixed-capacity table instead of
per-object pointers, so a program reuses storage deterministically,
iterates live objects densely, and can cheaply tell a stale reference
from a live one.

```franko
import "franko/pool"
```

Module files:

```text
import/franko/pool.fr    // pure Franko, no C++ bridge
```

Design rules:

- A pool allocates *indices*, not objects. The payload that index `idx`
  selects — an element of a dynamic array, a row of a component table —
  belongs to the program; the pool never touches it. `uninit()` frees
  the pool's own tables, never the payload.
- Status codes are `int32_t`: `0` on success, `-1` on failure. Sizes,
  indices, and generations are `uint32_t`.
- `new_index()` and `generation()` report failure with the `_END`
  sentinel (`0xFFFFFFFF`) instead of a status code.
- Methods are called on a pool lvalue and declare the permission they
  need: `ptr<X> self` for the lifecycle operations (`init`, `uninit`,
  `grow`), `mview<X> self` for `new_index`/`free_index`, and
  `view<X> self` for the observers. The compiler synthesizes the
  matching `getptr(...)`/`getmview(...)`/`getview(...)`.
- A pool owns dynamic arrays, so it is pointer-only: it cannot be
  passed or returned by value, and no assignment copies its storage.
- Lifecycle: a declaration produces the empty pool (`capacity() == 0`,
  `used() == 0`), `init(C)` prepares it, `grow(newC)` enlarges it,
  `uninit()` frees it. A pool may be `init`-ed again after `uninit()`.

## Sentinels

Two `uint32_t` sentinel values run through the whole module, spelled as
literals in Franko code:

```franko
// _END       = 0xFFFFFFFF   end of the free list, failure result
// _ALLOCATED = 0xFFFFFFFE   slot is live
```

They are documented names, not exported constants: write
`0xFFFFFFFF` / `0xFFFFFFFE` in your own code. A slot holds `_ALLOCATED`
exactly when it is live; the free-list pools also store the next free
index in the slot, with `_END` terminating the list.

## Common API

All four allocators expose the same nine methods, so the API is
documented once. The signatures below are spelled with
`NextAllocator`; substitute whichever allocator you use.

### init

```franko
func init(ptr<NextAllocator> self, uint32_t C) -> int32_t
```

Prepares `C` slots (`C > 0`), all free, and returns `0`. Returns `-1`
when the pool is already initialized, when `C == 0`, or when the slot
tables cannot be allocated. Calling `init` on a live pool does not
reset it: the pool keeps its current state and the call fails.

### uninit

```franko
func uninit(ptr<NextAllocator> self) -> int32_t
```

Frees the slot tables and returns the pool to its empty state
(`capacity() == 0`, `used() == 0`). Returns `0` on success, `-1` when
the pool is not initialized. Live indices are dropped without any
per-slot action — release the payloads yourself before `uninit`.

### grow

```franko
func grow(ptr<NextAllocator> self, uint32_t newC) -> int32_t
```

Enlarges the pool to `newC` slots (`newC > capacity()`). Existing
slots keep their indices, their live/free state, and their contents.
Returns `0` on success, `-1` when the pool is not initialized, when
`newC` is not greater than the current capacity, or when the tables
cannot be resized. A failed grow leaves the pool at its old capacity.

### new_index

```franko
func new_index(mview<NextAllocator> self) -> uint32_t
```

Takes one free slot and returns its index. Returns `0xFFFFFFFF`
(`_END`) when the pool is not initialized or when every slot is live —
check for `_END` before using the result, or call `grow` and retry.
The caller owns the payload of the returned index until it is passed
to `free_index`.

### free_index

```franko
func free_index(mview<NextAllocator> self, uint32_t idx) -> int32_t
```

Returns the live slot `idx` to the pool. Returns `0` on success, `-1`
when `idx` is not a live slot — which covers never-allocated indices,
already-released indices (double release), and indices beyond
`capacity()`. A failed release leaves the pool unchanged.

### exist

```franko
func exist(view<NextAllocator> self, uint32_t idx) -> uint8_t
```

Returns `1` when slot `idx` is live, `0` otherwise (never allocated,
already released, out of range, or pool not initialized). Never fails
and never reads out of bounds.

### write_active_indices

```franko
func write_active_indices(view<NextAllocator> self, mview<array<uint32_t>> out) -> int32_t
```

Writes every live index into `out`, which must hold at least
`used()` elements. Returns `0` on success, `-1` when `out` is null,
when the pool is not initialized, or when `out` is too small. The
order depends on the allocator (see each allocator below).

### used

```franko
func used(view<NextAllocator> self) -> uint32_t
```

Returns the number of live slots. Starts at `0` after `init`, rises on
every successful `new_index` and falls on every successful
`free_index`.

### capacity

```franko
func capacity(view<NextAllocator> self) -> uint32_t
```

Returns the number of slots the pool currently holds: `C` after
`init(C)`, `newC` after `grow(newC)`, `0` after `uninit`.

## Generational handles

The two generational allocators add one method.

### generation

```franko
func generation(view<GenerationalNextAllocator> self, uint32_t idx) -> uint32_t
```

Returns the generation counter of slot `idx`. A counter starts at `0`
and is bumped by every successful `free_index` of that slot, so it
follows the slot across reuse. An `(index, generation)` pair captured
at allocation time therefore identifies one specific allocation: when
the slot is reused, `generation(idx)` no longer matches and the pair
proves the reference is stale. Returns `0xFFFFFFFF` (`_END`) when the
pool is not initialized or `idx` is out of range.

## std::NextAllocator

Free-list pool. A free slot stores the index of the next free slot, the
last free slot stores `_END`, and a live slot stores `_ALLOCATED`.
Allocation and release are O(1); `write_active_indices` walks the
whole table (O(`capacity()`)) and emits live indices in ascending
index order. Released slots are reused LIFO, so the most recently
released index comes back first.

```franko
std::NextAllocator a;
int32_t st = a.init(4);        // 0
uint32_t idx = a.new_index();  // 0
st = a.free_index(idx);        // 0
st = a.uninit();               // 0
```

Memory cost: one `uint32_t` per slot, plus one dynamic array.

## std::SparseSetAllocator

Sparse-set pool. `_dense` holds the live indices in allocation order
and `_sparse` maps an index to its position in `_dense`; a slot is live
exactly when `_sparse[idx] < used()` and `_dense[_sparse[idx]] == idx`.
Allocation and release are O(1), and `write_active_indices` copies only
the live indices (O(`used()`)) in allocation order — the natural order
for iterating live objects. Releasing a slot swaps it with the last
dense entry, so dense order changes as slots are released.

```franko
std::SparseSetAllocator a;
int32_t st = a.init(4);        // 0
uint32_t i = a.new_index();    // 0
uint32_t j = a.new_index();    // 1
st = a.free_index(i);          // 0
st = a.uninit();               // 0
```

Memory cost: two `uint32_t` per slot, plus two dynamic arrays.

## std::GenerationalNextAllocator

Identical to `std::NextAllocator`, plus a generation counter per slot
(see [Generational handles](#generational-handles)). `free_index`
bumps the counter of the slot it releases; `grow` adds new slots with
generation `0`.

```franko
std::GenerationalNextAllocator a;
int32_t st = a.init(4);          // 0
uint32_t idx = a.new_index();    // 0
uint32_t gen = a.generation(idx); // 0
st = a.free_index(idx);          // 0
gen = a.generation(idx);         // 1
st = a.uninit();                 // 0
```

Memory cost: two `uint32_t` per slot, plus two dynamic arrays.

## std::GenerationalSparseSetAllocator

Identical to `std::SparseSetAllocator`, plus a generation counter per
slot (see [Generational handles](#generational-handles)). `free_index`
bumps the counter of the slot it releases; `grow` adds new slots with
generation `0`.

```franko
std::GenerationalSparseSetAllocator a;
int32_t st = a.init(4);          // 0
uint32_t idx = a.new_index();    // 0
uint32_t gen = a.generation(idx); // 0
st = a.free_index(idx);          // 0
gen = a.generation(idx);         // 1
st = a.uninit();                 // 0
```

Memory cost: three `uint32_t` per slot, plus three dynamic arrays.

## Choosing an allocator

| Allocator                  | new/free | write_active_indices order | Reuse order  | Detects stale handles | Tables |
|----------------------------|----------|----------------------------|--------------|-----------------------|--------|
| `NextAllocator`            | O(1)     | ascending index, O(capacity) | LIFO       | no                    | 1      |
| `SparseSetAllocator`       | O(1)     | allocation order, O(used)  | swap with last | no                  | 2      |
| `GenerationalNextAllocator`| O(1)     | ascending index, O(capacity) | LIFO       | yes                   | 2      |
| `GenerationalSparseSetAllocator` | O(1) | allocation order, O(used)  | swap with last | yes               | 3      |

Pick the plain allocator when indices are used only while known live,
and the generational one when an index may outlive its allocation —
stored in a handle, kept across a loop iteration, or returned from a
function. Pick the sparse-set variant when the hot path iterates live
objects; pick the free-list variant when index stability between
releases matters more than iteration order.

## Example

A generational pool guarding a payload array: the handle records the
index and the generation, and a freed-then-reused slot no longer
matches the handle, so the stale reference is detected instead of
silently reading the new occupant's data.

```franko
import "franko/pool"

func main() -> int32_t {
    array<int32_t> payload;
    payload.init(8);

    std::GenerationalSparseSetAllocator pool;
    pool.init(8);

    // Allocate a slot and store data in its payload cell.
    uint32_t idx = pool.new_index();
    uint32_t gen = pool.generation(idx);
    payload[idx] = 42;
    print(payload[idx]);

    // Release the slot and take a new one: the slot comes back with
    // a bumped generation.
    pool.free_index(idx);
    uint32_t idx2 = pool.new_index();

    // The handle captured before the release is stale: the index is
    // live again, but the generation no longer matches.
    if (pool.exist(idx) == 1 && pool.generation(idx) == gen) {
        print(1);
    } else {
        print(0);
    }

    // A handle captured now matches.
    if (pool.exist(idx2) == 1 && pool.generation(idx2) == pool.generation(idx)) {
        print(1);
    } else {
        print(0);
    }

    pool.free_index(idx2);
    pool.uninit();
    payload.uninit();
    return 0
}
```

Output:

```text
42
0
1
```

The first check prints `0`: `idx` is live again (the pool handed it
straight back), but its generation was bumped by the release, so the
old handle no longer identifies the original allocation. The second
prints `1`: a handle captured after the release matches.

## Tests

Module tests live in `library_tests/pool/` (run with
`uv run python library_tests/run_library_tests.py`):

```text
library_tests/pool/next/success/              // free-list pool goldens
library_tests/pool/sparse_set/success/        // sparse-set pool goldens
library_tests/pool/gen_next/success/          // generational free-list goldens
library_tests/pool/gen_sparse_set/success/    // generational sparse-set goldens
```

Each suite covers one method — `init`, `uninit`, `grow`, `new_index`,
`free_index`, `exist`, `write_active_indices`, `used`, `capacity`, and
`generation` for the generational pools — with the success paths and
the failure paths (double release, out-of-range index, wrong state)
asserted line by line.
