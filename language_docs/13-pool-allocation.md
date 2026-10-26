# 13. Pool Allocation

Franko has no per-object heap allocator. The two storage classes a variable
can have are automatic storage (a local, whose lifetime is its enclosing
block) and static storage (a global, whose lifetime is the program).

Dynamic arrays are the one kind of storage a program manages explicitly:
`init`, `init_zero`, `resize`, and `uninit` pair up over a single array.

```franko
array<int32_t> xs;
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

uint32_t idx = pool.new_index();
// ... use idx as a row index into the program's own payload array ...
pool.free_index(idx);

pool.uninit();
```

Slot indices are cheap to copy, compare, and store, and a generational
pool distinguishes a live index from one that was recycled, so a stale
handle is detectable. The pool tracks indices only; the payload that an
index selects belongs to the program.

The pool allocator API is documented in
`library_docs/pool.md`.
