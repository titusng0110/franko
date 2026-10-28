# 28. Type Equality

Franko uses exact, structural semantic type equality in many places. Type equality is implemented once (in the semantic type classes) and used consistently by overload resolution, assignment checking, return checking, and diagnostics.

## 28.1 Primitive Equality

Primitive types are equal only if they have the same kind.

```text
uint8_t  != uint16_t
int32_t  != uint32_t
int32_t  == int32_t
```

The aliases `int`, `uint`, and `byte` canonicalize to `int32_t`, `uint32_t`, and `uint8_t` respectively.

## 28.2 Dynamic Array Equality

Dynamic arrays are equal if their element types are equal.

```text
array<int32_t> == array<int32_t>
array<int32_t> != array<uint8_t>
```

## 28.3 Static Array Equality

Static arrays are equal if:

- element types are equal;
- sizes are numerically equal after unfolding the constant size expression.

The size comparison is numeric, not textual:

```text
array<int32_t, 10>     == array<int32_t, 0xA>
array<int32_t, 5 + 5>  == array<int32_t, 10>
array<int32_t, 10>     != array<int32_t, 20>
```

## 28.4 Pointer Equality

Pointer types are equal if their referenced types are equal **and** their
permissions match: `ptr<T>`, `mview<T>`, and `view<T>` are three distinct
types.

```text
ptr<array<byte>>     == ptr<array<byte>>
ptr<array<byte>>     != mview<array<byte>>
mview<array<byte>>   != view<array<byte>>
view<array<byte, 3>> != view<array<byte, 5>>
```

A pointer permission downgrade (`ptr<T>` to `mview<T>`/`view<T>`,
`mview<T>` to `view<T>`) is the single implicit conversion the language
allows; it does not make the types equal (see [Pointers](24-pointers.md)).

## 28.5 Function Signature Equality

Function signatures are equal if they have:

- the same function identifier;
- the same ordered list of parameter types.

Return type and parameter names are not part of function signature
equality. For methods, the `self` parameter type is not part of signature
equality either (see [Methods](26-methods.md)).

## 28.6 Struct Equality

Struct types are nominal: two struct declarations are distinct types even when their field layouts are identical.

```text
struct X { int32_t n }
struct Y { int32_t n }

X != Y
X == X
```

Duplicate struct names are rejected, so each struct name denotes exactly one declared type. See [Structs](25-structs.md) for the complete struct reference.
