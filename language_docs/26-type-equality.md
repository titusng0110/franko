# 26. Type Equality

Franko uses exact, structural semantic type equality in many places. Type equality is implemented once (in the semantic type classes) and used consistently by overload resolution, assignment checking, return checking, and diagnostics.

## 26.1 Primitive Equality

Primitive types are equal only if they have the same kind.

```text
uint8_t  != uint16_t
int32_t  != uint32_t
int32_t  == int32_t
```

The aliases `int`, `uint`, and `byte` canonicalize to `int32_t`, `uint32_t`, and `uint8_t` respectively.

## 26.2 Dynamic Array Equality

Dynamic arrays are equal if their element types are equal.

```text
array<int32_t> == array<int32_t>
array<int32_t> != array<uint8_t>
```

## 26.3 Static Array Equality

Static arrays are equal if:

- element types are equal;
- sizes are numerically equal after unfolding the constant size expression.

The size comparison is numeric, not textual:

```text
array<int32_t, 10>     == array<int32_t, 0xA>
array<int32_t, 5 + 5>  == array<int32_t, 10>
array<int32_t, 10>     != array<int32_t, 20>
```

## 26.4 Address Equality

Address types are equal if their referenced types are equal.

## 26.5 Function Signature Equality

Function signatures are equal if they have:

- the same function identifier;
- the same ordered list of parameter types.

Return type and parameter names are not part of function signature equality.