# 16. Casts

Franko has no implicit numeric conversions: a value of one integer
type never silently becomes another (see [Assignment](14-assignment.md)).
Moving a value across integer widths is always spelled out with the
explicit `cast<T>(expr)` intrinsic.

```franko
uint8_t a = 200;
uint16_t b = cast<uint16_t>(a);

int32_t big = 300;
uint8_t small = cast<uint8_t>(big);
```

Like `$(...)` and `getaddr`, `cast<T>` is a compiler intrinsic, not a
function call: it cannot be overloaded, shadowed, or passed around.

## 16.1 Cast Syntax

```franko
cast<uint16_t>(a)
```

The target `T` must be one of the eight primitive integer types
(`int8_t` … `uint64_t`, including the `int` / `uint` / `byte` aliases).
Anything else — addresses, arrays, structs, `void` — is rejected at
parse time. The operand must be an integer expression; casting an
address, array, struct, null, or void value is a compile error.

A cast is never an lvalue:

```franko
cast<uint16_t>(a) = 5;   // invalid
```

Casts compose freely with other expressions:

```franko
uint8_t cmp = 1 < 2;
int32_t wide = cast<int32_t>(cmp);
print(cast<uint8_t>(wide));
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

Casting to the type the expression already has is allowed and
compiles to the value unchanged.

## 16.3 Checked Casts

Every other conversion between distinct types — narrowing, any
signed-to-unsigned conversion, `uint64_t -> int64_t`, and
same-width signedness changes — compiles to a runtime-checked
`franko_safe_cast<T>(expr)` call. The check compares the value
against the target type's range and throws on overflow, so a bad
cast aborts instead of wrapping or truncating.

```franko
int32_t big = 300;
uint8_t small = cast<uint8_t>(big);   // throws: 300 does not fit uint8_t
```

A checked cast that fits succeeds normally:

```franko
uint32_t w = 70000;
int32_t s = cast<int32_t>(w);   // valid: 70000 fits int32_t
print(s);
```

## 16.4 Constant Casts

A cast of a folded constant expression is validated at compile time:
the value must fit the target type or compilation fails. There is no
wraparound, at compile time or at run time.

```franko
uint8_t ok = cast<uint8_t>(200);    // valid
uint8_t bad = cast<uint8_t>(300);   // invalid: 300 does not fit uint8_t
```

## 16.5 What Casts Do Not Cover

- There are no casts for addresses, arrays, structs, or void. Raw
  integers cannot be converted to addresses.
- Casts do not appear in constant-expression slots (static array
  sizes, global initializers, struct field defaults): those contexts
  accept only plain integer literals and operators.
- Calls, returns, assignments, and operators still require exact
  type equality on their operands; `cast<T>` produces a value of
  exactly `T`, which is what makes those positions accept it.
  Since there is no other conversion syntax, a value that must
  cross widths on the way into a call is cast into a temporary
  first:

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
