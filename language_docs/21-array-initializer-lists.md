# 21. Array Initializer Lists

Franko supports **array initializer lists** as array element assignment sugar.

An array initializer list is written with square brackets:

```franko
[1, 2, 3]
```

An array initializer list is **not** a standalone array value.

It is **not** a general expression.

It is a compact syntax for assigning listed elements into an existing array target.

The core rule is:

```franko
target = [a, b, c];
```

is equivalent to:

```franko
target[0] = a;
target[1] = b;
target[2] = c;
```

No allocation, resizing, or dynamic array initialization is performed by the initializer list itself.

## 21.1 Array Initializer Lists Are Not General Expression Values

Array initializer lists do not have standalone types.

Invalid:

```franko
[1, 2, 3];
print([1, 2, 3]);
foo([1, 2, 3]);
return [1, 2, 3];
```

Array initializer lists are only meaningful in assignment-style contexts where there is an explicit array assignment target.

## 21.2 Array Initializer Assignment

An array initializer list may appear on the right-hand side of an assignment:

```franko
arr = [1, 2, 3];
```

This is not whole-array assignment. It is recursively desugared into indexed element assignments:

```franko
arr[0] = 1;
arr[1] = 2;
arr[2] = 3;
```

The normal expression, assignment, indexing, and array-use rules apply to the generated statements.

Initializer elements are ordinary expressions. They do not need to be compile-time constants.

## 21.3 Declaration Initializer Form

Array initializer lists may also appear in declaration-assignment syntax:

```franko
array<int32_t, 3> xs = [1, 2, 3];
```

This is treated as ordinary declaration initializer sugar:

```franko
array<int32_t, 3> xs;
xs = [1, 2, 3];
```

which then lowers to:

```franko
array<int32_t, 3> xs;
xs[0] = 1;
xs[1] = 2;
xs[2] = 3;
```

For dynamic arrays, the initializer list still does **not** initialize storage.

Invalid for a freshly declared dynamic array:

```franko
array<int32_t> dyn = [1, 2, 3];
```

Correct:

```franko
array<int32_t> dyn;
dyn(3);
dyn = [1, 2, 3];
dyn.uninit();
```

The initializer list itself never emits `dyn(3)`.

## 21.4 Static Arrays

For static arrays, initializer lists lower to indexed assignments.

If the initializer list is shorter than the static array length, only the listed elements are assigned. Unlisted elements are untouched.

If the initializer list is longer than the static array length, the generated indexed assignment is rejected by ordinary static array bounds checking.

There is no special “initializer length must exactly match static length” rule. The ordinary generated assignments determine validity.

## 21.5 Dynamic Arrays

Array initializer lists do not initialize, allocate, resize, or reallocate dynamic arrays.

Correct:

```franko
array<int32_t> dyn;
dyn(3);
dyn = [1, 2, 3];
dyn.uninit();
```

Invalid:

```franko
array<int32_t> dyn;
dyn = [1, 2, 3];
```

because it lowers to indexed assignments without first initializing `dyn`.

## 21.6 Element Expressions

Initializer list elements are ordinary expressions.

If an element expression is invalid, or if its value cannot be assigned to the array element type, the ordinary expression or assignment checker rejects the generated assignment.

Example:

```franko
array<uint8_t, 3> bytes;
bytes = [0, 127, 256];
```

lowers to:

```franko
bytes[0] = 0;
bytes[1] = 127;
bytes[2] = 256;
```

The assignment checker rejects `bytes[2] = 256` because `256` does not fit `uint8_t`.

## 21.7 Nested Initializer Lists

Nested initializer lists represent recursive indexed assignment into nested arrays.

Example:

```franko
array<array<int32_t, 2>, 2> matrix;

matrix = [
    [1, 2],
    [3, 4]
];
```

is equivalent to:

```franko
matrix[0][0] = 1;
matrix[0][1] = 2;
matrix[1][0] = 3;
matrix[1][1] = 4;
```

A nested initializer list is only meaningful when the corresponding target element is itself an array.

## 21.8 Nested Dynamic Arrays

Initializer lists do not allocate nested dynamic arrays.

Correct:

```franko
array<array<int32_t>> matrix;

matrix(2);
matrix[0](3);
matrix[1](3);

matrix = [
    [1, 2, 3],
    [4, 5, 6]
];

matrix[0].uninit();
matrix[1].uninit();
matrix.uninit();
```

Invalid:

```franko
array<array<int32_t>> matrix;

matrix(2);

matrix = [
    [1, 2, 3],
    [4, 5, 6]
];
```

because the outer dynamic array is initialized, but `matrix[0]` and `matrix[1]` are not initialized.

The rule is simple:

> Initializer lists assign elements. They never allocate dynamic array storage at any level.

## 21.9 Empty Initializer Lists

An empty initializer list emits no assignments.

Example:

```franko
array<int32_t, 3> xs;
xs = [];
```

No elements are assigned.

For dynamic arrays:

```franko
array<int32_t> xs;
xs = [];
```

also emits no assignments and does not initialize `xs`.

This is allowed as a no-op initializer assignment unless another rule rejects it.

## 21.10 Array Initializer Lists and Global Scope

Array initializer lists are assignment sugar.

Therefore, they are only valid where the resulting assignment statements are valid.

At global scope, executable statements are not allowed.

Invalid:

```franko
array<int32_t, 3> xs;
xs = [1, 2, 3];
```

Declaration initializer sugar at global scope is also invalid if it desugars into executable assignments.

## 21.11 Summary of Array Initializer Lists

Array initializer lists in Franko are:

- not general expressions;
- not array values;
- allowed as assignment initializer syntax;
- allowed in declaration initializers through ordinary declaration-assignment sugar;
- lowered to recursive indexed assignments;
- allowed to contain ordinary expressions;
- not required to contain compile-time constants;
- partial when shorter than the target array;
- rejected for static arrays only when generated indexes are out of bounds;
- not responsible for allocating, resizing, or initializing dynamic arrays;
- valid for dynamic arrays only when the target dynamic storage already exists;
- recursively applicable to nested arrays.
