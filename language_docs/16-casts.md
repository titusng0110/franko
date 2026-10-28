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
uint8_t a = 200;
uint16_t b = cast<uint16_t>(a);        // widening: plain static_cast

int32_t big = 300;
uint8_t small = unsafe_cast<uint8_t>(big);   // checked: throws at run time
```

Like `$(...)` and `getptr`, both are compiler intrinsics, not function
calls: they cannot be overloaded, shadowed, or passed around.

## 16.1 Cast Syntax

```franko
cast<uint16_t>(a)
cast<view<array<byte>>>(p)
unsafe_cast<int32_t>(big)
```

`cast<T>` accepts a full type as its target: one of the eight primitive
integer types, or a pointer type (`ptr<T>`, `mview<T>`, `view<T>`).
Arrays, structs, and `void` are rejected at check time. The operand must
match the target category: an integer expression for integer targets, a
pointer or `null` for pointer targets.

`unsafe_cast<T>` is restricted to the eight primitive integer types
(`int8_t` … `uint64_t`, including the `int` / `uint` / `byte` aliases);
a pointer target does not parse. The operand must be an integer
expression.

A cast is never an lvalue:

```franko
cast<uint16_t>(a) = 5;   // invalid
```

Casts compose freely with other expressions:

```franko
uint8_t cmp = 1 < 2;
int32_t wide = cast<int32_t>(cmp);
print(unsafe_cast<uint8_t>(wide));
```

## 16.2 Static (Value-Preserving) Casts

When every value of the source type is representable in the target
type, the cast is statically safe and compiles to a plain
`static_cast` with zero runtime cost:

- strictly wider, same signedness:
  `uint8_t -> uint16_t -> uint32_t -> uint64_t` and
  `int8_t -> int16_t -> int32_t -> int64_t`;
- unsigned to signed of strictly greater width:
  `uint8_t -> int16_t`, `uint16_t -> int32_t`, `uint32_t -> int64_t`.

These are the conversions `cast<T>` performs. Casting to the type the
expression already has is allowed and compiles to the value unchanged.

## 16.3 Checked Casts (`unsafe_cast<T>`)

Every conversion that can lose data — narrowing, any signed-to-unsigned
conversion, `uint64_t -> int64_t`, and same-width signedness changes —
must be written `unsafe_cast<T>(expr)`. It compiles to a runtime-checked
`franko_unsafe_cast<T>(expr)` call. The check compares the value
against the target type's range and throws on overflow, so a bad
conversion aborts instead of wrapping or truncating.

```franko
int32_t big = 300;
uint8_t small = unsafe_cast<uint8_t>(big);   // throws: 300 does not fit uint8_t
```

A checked cast that fits succeeds normally:

```franko
uint32_t w = 70000;
int32_t s = unsafe_cast<int32_t>(w);   // valid: 70000 fits int32_t
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
uint8_t ok = unsafe_cast<uint8_t>(200);    // valid
uint8_t bad = unsafe_cast<uint8_t>(300);   // invalid: 300 does not fit uint8_t
```

## 16.5 Pointer Permission Casts

`cast<T>` with a pointer target is an explicit **permission downgrade**
(see [Pointers](24-pointers.md)): the referenced type must stay
identical and the permission may only weaken
(`ptr -> mview -> view`).

```franko
array<byte> buf;
buf.init(16);
ptr<array<byte>> p = getptr(buf);
view<array<byte>> v = cast<view<array<byte>>>(p);   // explicit downgrade
mview<array<byte>> m = cast<mview<array<byte>>>(p);
view<array<byte>> v2 = cast<view<array<byte>>>(m);
```

Explicit downgrades are never required — the same conversions happen
implicitly wherever a lower permission is expected — but they can be
written for clarity. Casting *upwards* (`view` to `mview`/`ptr`) or
across different pointee types is invalid:

```franko
ptr<array<byte>> q = cast<ptr<array<byte>>>(v);   // invalid: raises permissions
```

Raw integers can never be converted to pointers.

## 16.6 What Casts Do Not Cover

- Casts do not appear in constant-expression slots (static array
  sizes, global initializers, struct field defaults): those contexts
  accept only plain integer literals and operators.
- Calls, returns, assignments, and operators still require exact
  type equality on their operands, with the single exception of the
  implicit pointer permission downgrades. `cast<T>` produces a value of
  exactly `T`, which is what makes those positions accept it.
  A value that must cross widths on the way into a call is converted
  into a temporary first:

```franko
func f(int64_t x) -> int64_t {
    return x;
}

func main() -> int32_t {
    int32_t v = 5;
    int64_t r = f(cast<int64_t>(v));
    print(r);
    return 0;
}
```
