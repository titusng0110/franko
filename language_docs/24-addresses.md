# 24. Addresses

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

## 24.1 Creating Addresses with `getaddr`

The operand must be a storage-backed lvalue.

If the operand has type `T`, then `getaddr(operand)` has type `addr<T>`.

## 24.2 Dereferencing with `deref`

If the operand has type `addr<T>`, then `deref(...)` has type `T`.

`deref(...)` is also an lvalue.

## 24.3 Nested Addresses

Franko supports addresses to addresses.

## 24.4 Address Assignment

Address assignment requires exact type equality.

Raw integers cannot be converted to addresses.

## 24.5 Address Comparison

Addresses may be compared using only the equality operators:

```franko
==
!=
```

Both operands must have identical address types.

Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for addresses.

Address comparison produces `uint8_t`.

## 24.6 No Address Arithmetic

Addresses are not integers and do not support arithmetic.

## 24.7 Addresses of Arrays

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

## 24.8 The null Literal

`null` is a literal of type `null`. It is valid only where an address is expected:

```franko
addr<int32_t> p;
p = null;               // valid

int32_t x = null;       // invalid: null is only valid for address types
p = 0;                  // invalid: 0 is an integer, not null
```

`null` and `0` are different values. An address cannot be assigned or compared against the integer `0`; use `null` instead.

`null` may be compared with an address using equality only:

```franko
if (p == null) {
    // p is null on this path
}
if (p != null) {
    // p is not null on this path
}
```

Comparing `null` with a non-address value is invalid. Relational comparison (`<`, `>`) of `null` is invalid.

`null` may be passed to `addr<T>` parameters and returned from `addr<T>` functions.

`print(null)` prints the null address as a pointer value.

### 24.8.1 Null-Initialized Address Declarations

Address declarations are **guaranteed null-initialized**, unlike ordinary declarations, which start uninitialized:

```franko
addr<int32_t> p;        // p is null here, guaranteed
print(p == null);       // true
```

This guarantee also applies to address-typed struct fields.

### 24.8.2 Nullness Certification

The compiler tracks whether an address variable is null. The only expression that certifies an address as non-null is `getaddr(...)`:

```franko
addr<int32_t> p;            // p is null
deref(p);                   // error: cannot dereference null

p = getaddr(x);             // p is certified non-null
deref(p);                   // valid

addr<int32_t> q = p;        // q copies p's state: non-null
addr<int32_t> r = q;        // r copies q's state: non-null
```

Assignment from another address variable copies that variable's exact state, so a null state propagates through copies and an `getaddr` certification propagates transitively.

Assignment from values of unknown provenance (call results, parameters, struct-field or array-element reads) yields a maybe-null state: the compiler treats such a variable as possibly null, and dereferencing it is a warning, not an error.

### 24.8.3 Dereferencing and Warnings

Dereferencing a variable that is **definitely null** is an error.

Dereferencing a variable that is **possibly null** (null on some path, for example after a branch merge) is a **warning**, not an error:

```franko
addr<int32_t> p;
if (cond != 0) {
    p = getaddr(x);
} else {
    p = null;
}
deref(p);                   // warning: possibly null
```

Guarding with `p != null` removes the warning, because the compiler refines the variable's state inside the guard.

## 24.9 Address Lifetime Limitations

Address lifetime is not fully tracked.

The compiler does not fully track dangling addresses or aliasing through addresses. An address may outlive its pointee, and the compiler does not detect that case at compile time; a runtime check for this is planned as a later change.
