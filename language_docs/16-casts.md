# 16. Casts

Franko has no implicit numeric conversions: a value of one integer
type never silently becomes another (see [Assignment](14-assignment.md)).
Moving a value across integer widths is always spelled out with one of
two explicit conversion intrinsics:

- `cast<T>(expr)` — value-preserving **widening** and pointer permission
  **downgrades**;
- `unsafe_cast<T>(expr)` — any integer conversion that can lose data
  (runtime-checked).

```franko
uint8 a = 200;
uint16 b = cast<uint16>(a);        // widening: plain static_cast

int32 big = 300;
uint8 small = unsafe_cast<uint8>(big);   // checked: throws at run time
```

Like `$(...)` and `getptr`, both are compiler intrinsics, not function
calls: they cannot be overloaded, shadowed, or passed around.

## 16.1 Cast Syntax

```franko
cast<uint16>(a)
cast<view<[byte]>>(p)
unsafe_cast<int32>(big)
```

`cast<T>` accepts a full type as its target: one of the eight primitive
integer types, or a pointer type (`ptr<T>`, `mview<T>`, `view<T>`).
Arrays, structs, and `void` are rejected at check time. The operand must
match the target category: an integer expression for integer targets, a
pointer or `null` for pointer targets.

`unsafe_cast<T>` is restricted to the eight primitive integer types
(`int8` … `uint64`, including the `int` / `uint` / `byte` aliases);
a pointer target does not parse. The operand must be an integer
expression.

A cast is never an lvalue:

```franko
cast<uint16>(a) = 5;   // invalid
```

Casts compose freely with other expressions:

```franko
uint8 cmp = 1 < 2;
int32 wide = cast<int32>(cmp);
print(unsafe_cast<uint8>(wide));
```

## 16.2 Static (Value-Preserving) Casts

When every value of the source type is representable in the target
type, the cast is statically safe and compiles to a plain
`static_cast` with zero runtime cost:

- strictly wider, same signedness:
  `uint8 -> uint16 -> uint32 -> uint64` and
  `int8 -> int16 -> int32 -> int64`;
- unsigned to signed of strictly greater width:
  `uint8 -> int16`, `uint16 -> int32`, `uint32 -> int64`.

These are the conversions `cast<T>` performs. Casting to the type the
expression already has is allowed and compiles to the value unchanged.

## 16.3 Checked Casts (`unsafe_cast<T>`)

Every conversion that can lose data — narrowing, any signed-to-unsigned
conversion, `uint64 -> int64`, and same-width signedness changes —
must be written `unsafe_cast<T>(expr)`. It compiles to a runtime-checked
`franko_unsafe_cast<T>(expr)` call. The check compares the value
against the target type's range and throws on overflow, so a bad
conversion aborts instead of wrapping or truncating.

```franko
int32 big = 300;
uint8 small = unsafe_cast<uint8>(big);   // throws: 300 does not fit uint8
```

A checked cast that fits succeeds normally:

```franko
uint32 w = 70000;
int32 s = unsafe_cast<int32>(w);   // valid: 70000 fits int32
print(s);
```

Writing `cast<T>` where the conversion can lose data is a compile error
that points at `unsafe_cast<T>`: the two spellings keep the dangerous
conversions visibly marked at every call site.

## 16.4 Constant Casts

A conversion of a folded constant expression is validated at compile
time: the value must fit the target type or compilation fails. There is
no wraparound, at compile time or at run time.

```franko
uint8 ok = unsafe_cast<uint8>(200);    // valid
uint8 bad = unsafe_cast<uint8>(300);   // invalid: 300 does not fit uint8
```

## 16.5 Pointer Permission Casts

`cast<T>` with a pointer target is an explicit **permission downgrade**
(see [Pointers](24-pointers.md)): the referenced type must stay
identical and the permission may only weaken
(`ptr -> mview -> view`).

```franko
[byte] buf;
buf.init(16);
ptr<[byte]> p = getptr(buf);
view<[byte]> v = cast<view<[byte]>>(p);   // explicit downgrade
mview<[byte]> m = cast<mview<[byte]>>(p);
view<[byte]> v2 = cast<view<[byte]>>(m);
```

Explicit downgrades are never required — the same conversions happen
implicitly wherever a lower permission is expected — but they can be
written for clarity. Casting *upwards* (`view` to `mview`/`ptr`) or
across different pointee types is invalid:

```franko
ptr<[byte]> q = cast<ptr<[byte]>>(v);   // invalid: raises permissions
```

The same `cast` syntax spells the static-to-dynamic array pointer view
decay: `mview<[T, N]>`/`view<[T, N]>` (and downgrade
combinations from a `ptr` source) cast to `mview<[T]>`/
`view<[T]>`, outermost dimension only
(see [Pointers](24-pointers.md)):

```franko
[int32, 4] xs;
mview<[int32, 4]> m = getmview(xs);
mview<[int32]> d = cast<mview<[int32]>>(m);
```

Decaying to `ptr<[T]>` is invalid: the result would claim ownership
of storage the static array owns.

Raw integers can never be converted to pointers.

## 16.6 What Casts Do Not Cover

- Casts do not appear in constant-expression slots (static array
  sizes, global initializers, struct field defaults): those contexts
  accept only plain integer literals and operators.
- Calls, returns, assignments, and operators still require exact
  type equality on their operands, with the single exception of the
  implicit pointer conversions (permission downgrades and the
  static-to-dynamic array pointer view decay). `cast<T>` produces a value of
  exactly `T`, which is what makes those positions accept it.
  A value that must cross widths on the way into a call is converted
  into a temporary first:

```franko
func f(int64 x) -> int64 {
    return x;
}

func main() -> int32 {
    int32 v = 5;
    int64 r = f(cast<int64>(v));
    print(r);
    return 0;
}
```
