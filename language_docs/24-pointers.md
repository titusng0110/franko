# 24. Pointers

Franko has typed pointers:

```franko
ptr<T>
```

An pointer value refers to a storage location containing a value of type `T`.

Examples:

```franko
ptr<int32_t> p;
ptr<uint8_t> b;
ptr<ptr<int32_t>> pp;
ptr<array<int32_t>> ap;
```

Pointers are typed, assignable, copyable, dereferenceable, and comparable.

Pointers are not arithmetic values.

`ptr<void>` is invalid because `void` is not an ordinary value type.

## 24.1 Creating Pointers with `getptr`

The operand must be a storage-backed lvalue.

If the operand has type `T`, then `getptr(operand)` has type `ptr<T>`.

## 24.2 Dereferencing with `$`

If the operand has type `ptr<T>`, then `$(...)` has type `T`.

`$(...)` is also an lvalue.

## 24.3 Nested Pointers

Franko supports pointers to pointers.

## 24.4 Pointer Assignment

Pointer assignment requires exact type equality.

Raw integers cannot be converted to pointers.

## 24.5 Pointer Comparison

Pointers may be compared using only the equality operators:

```franko
==
!=
```

Both operands must have identical pointer types.

Ordering comparisons (`<`, `>`, `<=`, `>=`) are invalid for pointers.

Pointer comparison produces `uint8_t`.

## 24.6 No Pointer Arithmetic

Pointers are not integers and do not support arithmetic.

## 24.7 Pointers of Arrays

Arrays may be pointed to.

Example:

```franko
array<int32_t> arr;
ptr<array<int32_t>> p;

p = getptr(arr);
$(p)(10);
```

Array initializer assignment through a dereferenced array pointer is valid when the dereference produces a storage-backed array lvalue:

```franko
array<int32_t, 3> xs;
ptr<array<int32_t, 3>> p;

p = getptr(xs);

$(p) = [1, 2, 3];
```

Equivalent to:

```franko
$(p)[0] = 1;
$(p)[1] = 2;
$(p)[2] = 3;
```

## 24.8 The null Literal

`null` is a literal of type `null`. It is valid only where an pointer is expected:

```franko
ptr<int32_t> p;
p = null;               // valid

int32_t x = null;       // invalid: null is only valid for pointer types
p = 0;                  // invalid: 0 is an integer, not null
```

`null` and `0` are different values. An pointer cannot be assigned or compared against the integer `0`; use `null` instead.

`null` may be compared with an pointer using equality only:

```franko
if (p == null) {
    // p is null on this path
}
if (p != null) {
    // p is not null on this path
}
```

Comparing `null` with a non-pointer value is invalid. Relational comparison (`<`, `>`) of `null` is invalid.

`null` may be passed to `ptr<T>` parameters and returned from `ptr<T>` functions.

`print(null)` prints the null pointer as a pointer value.

### 24.8.1 Null-Initialized Pointer Declarations

Pointer declarations are **guaranteed null-initialized**, unlike ordinary declarations, which start uninitialized:

```franko
ptr<int32_t> p;        // p is null here, guaranteed
print(p == null);       // true
```

This guarantee also applies to pointer-typed struct fields.

### 24.8.2 Nullness Certification

The compiler tracks whether an pointer variable is null. The only expression that certifies an pointer as non-null is `getptr(...)`:

```franko
ptr<int32_t> p;            // p is null
$(p);                   // error: cannot dereference null

p = getptr(x);             // p is certified non-null
$(p);                   // valid

ptr<int32_t> q = p;        // q copies p's state: non-null
ptr<int32_t> r = q;        // r copies q's state: non-null
```

Assignment from another pointer variable copies that variable's exact state, so a null state propagates through copies and an `getptr` certification propagates transitively.

Assignment from values of unknown provenance (call results, parameters, struct-field or array-element reads) yields a maybe-null state: the compiler treats such a variable as possibly null, and dereferencing it is a warning, not an error.

### 24.8.3 Dereferencing and Warnings

Dereferencing a variable that is **definitely null** is an error.

Dereferencing a variable that is **possibly null** (null on some path, for example after a branch merge) is a **warning**, not an error:

```franko
ptr<int32_t> p;
if (cond != 0) {
    p = getptr(x);
} else {
    p = null;
}
$(p);                   // warning: possibly null
```

Guarding with `p != null` removes the warning, because the compiler refines the variable's state inside the guard.

## 24.9 Pointer Lifetime Limitations

Pointer lifetime is not fully tracked.

The compiler does not fully track dangling pointers or aliasing through pointers. An pointer may outlive its pointee, and the compiler does not detect that case at compile time; a runtime check for this is planned as a later change.
