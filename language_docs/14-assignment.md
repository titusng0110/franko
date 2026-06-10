# 14. Assignment

Assignment syntax:

```franko
target = value;
```

The left-hand side must be a storage-backed lvalue.

Valid assignment targets include:

- variables;
- array elements;
- dereferenced addresses.

## 14.1 Primitive Assignment

A primitive integer variable may be assigned:

1. a nonconstant expression of exactly the same primitive type; or
2. a constant expression that fits the target type.

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
