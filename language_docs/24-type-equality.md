# 24. Type Equality

Franko uses exact semantic type equality in many places.

## 24.1 Primitive Equality

Primitive types are equal only if they have the same kind.

```text
uint8_t  != uint16_t
int32_t  != uint32_t
int32_t  == int32_t
```

The alias `int` canonicalizes to `int32_t`.

## 24.2 Dynamic Array Equality

Dynamic arrays are equal if their element types are equal.

```text
array<int32_t> == array<int32_t>
array<int32_t> != array<uint8_t>
```

## 24.3 Static Array Equality

Static arrays are equal if:

- element types are equal;
- sizes are numerically equal.

## 24.4 Address Equality

Address types are equal if their referenced types are equal.

## 24.5 Function Signature Equality

Function signatures are equal if they have:

- the same function identifier;
- the same ordered list of parameter types.

Return type and parameter names are not part of function signature equality.
