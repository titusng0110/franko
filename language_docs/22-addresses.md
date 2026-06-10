# 22. Addresses

Franko has typed addresses:

```franko
addr<T>
```

An address value refers to a storage location containing a value of type `T`.

Examples:

```franko
addr<int32_t> p;
addr<uint8_t> b;
addr<addr<int32_t>> pp;
addr<array<int32_t>> ap;
```

Addresses are typed, assignable, copyable, dereferenceable, and comparable.

Addresses are not arithmetic values.

`addr<void>` is invalid because `void` is not an ordinary value type.

## 22.1 Creating Addresses with `getaddr`

The operand must be a storage-backed lvalue.

If the operand has type `T`, then `getaddr(operand)` has type `addr<T>`.

## 22.2 Dereferencing with `deref`

If the operand has type `addr<T>`, then `deref(...)` has type `T`.

`deref(...)` is also an lvalue.

## 22.3 Nested Addresses

Franko supports addresses to addresses.

## 22.4 Address Assignment

Address assignment requires exact type equality.

Raw integers cannot be converted to addresses.

## 22.5 Address Comparison

Addresses may be compared using:

```franko
==
!=
<
>
<=
>=
```

Both operands must have identical address types.

Address comparison produces `uint8_t`.

## 22.6 No Address Arithmetic

Addresses are not integers and do not support arithmetic.

## 22.7 Addresses of Arrays

Arrays may be addressed.

Example:

```franko
array<int32_t> arr;
addr<array<int32_t>> p;

p = getaddr(arr);
deref(p)(10);
```

Array initializer assignment through a dereferenced array address is valid when the dereference produces a storage-backed array lvalue:

```franko
array<int32_t, 3> xs;
addr<array<int32_t, 3>> p;

p = getaddr(xs);

deref(p) = [1, 2, 3];
```

Equivalent to:

```franko
deref(p)[0] = 1;
deref(p)[1] = 2;
deref(p)[2] = 3;
```

## 22.8 Address Lifetime Limitations

Address lifetime is not fully tracked.

The compiler does not fully track dangling addresses or aliasing through addresses.
