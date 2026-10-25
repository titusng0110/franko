# 14. Assignment

Assignment syntax:

```franko
target = value;
```

The left-hand side must be a storage-backed lvalue.

Valid assignment targets include:

- variables;
- array elements;
- dereferenced addresses;
- struct field accesses (`obj.field`).

## 14.1 Primitive Assignment

A primitive integer variable may be assigned:

1. a nonconstant expression of exactly the same primitive type;
2. a nonconstant expression of a narrower primitive integer type when the
   conversion is value-preserving (one-way widening, see below); or
3. a constant expression that fits the target type.

### 14.1.1 One-Way Integer Widening

In assignment positions only (plain assignment, which also covers
declaration initializers after desugaring), a nonconstant integer source
converts to a primitive integer target when every value of the source
type is representable in the target type:

- strictly wider, same signedness:
  `uint8_t -> uint16_t -> uint32_t -> uint64_t` and
  `int8_t -> int16_t -> int32_t -> int64_t`;
- unsigned to signed of strictly greater width:
  `uint8_t -> int16_t`, `uint16_t -> int32_t`, `uint32_t -> int64_t`.

Everything else stays an error: narrowing, same-width signedness
changes (`uint8_t -> int8_t`, `uint64_t -> int64_t`), and
signed-to-unsigned (`int32_t -> uint64_t`).

```franko
uint8_t a = 200;
uint16_t b;
b = a;          // valid: widening
uint16_t c = a; // valid: declaration initializers desugar to assignment

int64_t big = 5;
int32_t small;
small = big;    // invalid: narrowing
```

Widening never applies to function call arguments, return statements,
or operator operands, which keep exact type equality (see
[Functions](12-functions.md)). Franko has no cast syntax, so a value
that must cross widths on the way into a call is widened into a
temporary first:

```franko
func f(int64_t x) -> int64_t {
    return x;
}

func main() -> int32_t {
    int32_t v = 5;
    int64_t w = v;
    int64_t r = f(w);
    print(r);
    return 0;
}
```

## 14.2 Address Assignment

Address assignment requires exact address type equality.

Raw integers cannot be converted to addresses.

## 14.3 Array Assignment

Arrays cannot be directly assigned as values.

Invalid:

```franko
array<int32_t> a;
array<int32_t> b;

a = b;
```

Array initializer assignment is not value assignment. It is element-assignment sugar:

```franko
a = [1, 2, 3];
```

lowers to:

```franko
a[0] = 1;
a[1] = 2;
a[2] = 3;
```

## 14.4 Struct Assignment

A struct may be assigned as a whole value if and only if all of its fields are assignable, recursively: assignable = primitive | address | struct-with-all-assignable-fields.

Valid:

```franko
struct X {
    int32_t n
    addr<Y> p
}

X a;
X b;
a = b;    // whole-value assignment
```

Invalid (struct contains a static array field):

```franko
struct W {
    array<int32_t, 4> xs
}

W a;
W b;
a = b;    // invalid: struct with non-assignable fields is address-only
```

Structs with non-assignable fields can still be shared through `addr<X>`: pass or store `getaddr(s)`, and access fields through `deref(p).field`.

See [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md) for field-level assignment targets, and [Structs](24-structs.md) for the full assignability rules.
