# franko/algorithm

The `franko/algorithm` module provides in-place generic sorting algorithms in
`namespace std`.

```franko
import "franko/algorithm"
```

Call them as `std::quicksort<T>(...)` and `std::heapsort<T>(...)`.

Module file:

```text
import/franko/algorithm.fr    // pure Franko, no C++ bridge
```

## Requirements

Both algorithms are generic and require an explicit element type:

```franko
std::quicksort<int32>(getmview(values))
std::heapsort<int32>(getmview(values))
```

The element type `T` must support ordering with `<` and value assignment.
Primitive integers, enums, and structs with the required `OPERATOR_LT` and
assignment support qualify. The module rejects an unsupported `T` with a
compile-time `#panic`.

All sorting is done in place. A null receiver or an empty range is a no-op.
Ranges are **half-open**: `start` is included and `end` is excluded.

## quicksort

```franko
func quicksort(mview<[T]> values) -> void
func quicksort(mview<[T]> values, uint32 start, uint32 end) -> void
func quicksort(ptr<[T]> values) -> void
func quicksort(ptr<[T]> values, uint32 start, uint32 end) -> void
```

The one-argument forms sort the whole array. The ranged forms sort
`[start, end)`.

```franko
[int32] values;
values.init(4);
values[0] = 4;
values[1] = 1;
values[2] = 3;
values[3] = 2;

std::quicksort<int32>(getmview(values), 0, values.length());
values.uninit();
```

The `ptr<[T]>` overloads are for pointer-only element types such as
`std::String`, whose whole-value assignment requires owner rights over the
elements.

Quicksort first checks whether the range is already sorted, then selects
between its small-range network/insertion path, its duplicate-aware
three-way partition path, and its Lomuto partition path. A bounded depth
limit falls back to heapsort.

## heapsort

```franko
func heapsort(mview<[T]> values) -> void
func heapsort(mview<[T]> values, uint32 start, uint32 end) -> void
func heapsort(ptr<[T]> values) -> void
func heapsort(ptr<[T]> values, uint32 start, uint32 end) -> void
```

The one-argument forms sort the whole array. The ranged forms sort
`[start, end)`.

Use the `ptr<[T]>` overloads for pointer-only element types such as
`std::String`, where whole-value assignment needs owner rights over the
elements:

```franko
[std::String] names;
names.init(3);
names[0] = "gamma";
names[1] = "alpha";
names[2] = "beta";
std::heapsort<std::String>(getptr(names));
names.uninit();
```

Heapsort uses an insertion-sort path for small ranges and otherwise builds
and drains a binary max-heap in place.

## Internal names

Every helper in the module starts with `__`; only `quicksort` and `heapsort`
are public API.
