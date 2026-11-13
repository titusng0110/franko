# 33. Pool Allocation

Franko has no per-object heap allocator. The two storage classes a variable
can have are automatic storage (a local, whose lifetime is its enclosing
block) and static storage (a global, whose lifetime is the program).

Dynamic arrays are the one kind of storage a program manages explicitly:
`init`, `init_zero`, `resize`, and `uninit` pair up over a single array.

```franko
[int32] xs;
xs.init(20);
// ... use xs ...
xs.uninit();
```

When a program needs many same-sized objects that come and go
independently, it allocates one dynamic array up front and hands out
integer indices into it. The `franko/pool` module supplies that scheme
directly as four index-pool allocators, each tracking which slots are live,
which are free, and (for the generational variants) how many times a slot
has been recycled.

```franko
import "franko/pool"

std::SparseSetAllocator pool;
pool.init(1024);

(uint32 idx, int32 err) allocated = pool.new_index();
if (allocated.err != 0) {
    // pool is uninitialized or exhausted
}
// ... use allocated.idx as a row index into the program's own payload array ...
pool.free_index(allocated.idx);

pool.uninit();
```

Slot indices are cheap to copy, compare, and store, and a generational
pool distinguishes a live index from one that was recycled, so a stale
handle is detectable. The pool tracks indices only; the payload that an
index selects belongs to the program.

## 33.1 Prefer Pool Indices to Linked Nodes

For many programs, pool allocation is a safer and faster alternative to
linked nodes built from pointers. Instead of a `ptr<Node> next` field
(which is not allowed inside a struct), give each live object an index from a
pool allocator, store object data in parallel payload arrays, and store links
as `uint32` indices. The pool recycles indices deterministically, and its
generational variants detect stale handles. The full API is in
[franko/pool](../library_docs/pool.md).

```franko
import "franko/stdio"
import "franko/pool"

func main() -> int32 {
    [int32] payload;
    [uint32] next;
    payload.init(8);
    next.init(8);

    std::SparseSetAllocator nodes;
    nodes.init(8);

    (uint32 idx, int32 err) first = nodes.new_index();
    (uint32 idx, int32 err) second = nodes.new_index();
    if (first.err != 0 || second.err != 0) {
        return 1;
    }
    uint32 a = first.idx;
    uint32 b = second.idx;

    payload[a] = 10;
    payload[b] = 20;
    next[a] = b;                // replaces a node's `next` pointer
    next[b] = 0xFFFFFFFF;       // end of the list

    uint32 i = a;
    while (i != 0xFFFFFFFF) {
        stdout(payload[i]);
        i = next[i];
    }

    nodes.free_index(a);
    nodes.free_index(b);
    nodes.uninit();
    next.uninit();
    payload.uninit();
    return 0;
}
```

A node handle is now the pair of its pool index and (for a generational
pool) its generation; releasing a slot invalidates stale handles instead
of leaving a dangling pointer.

The pool allocator API is documented in
[`franko/pool`](../library_docs/pool.md).
