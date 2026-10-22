# 5. Desugaring

Some Franko source forms are accepted as convenient syntax and rewritten before semantic checking.

Desugaring does not change program meaning. It rewrites compact user-facing syntax into simpler core forms used by the semantic analyzer.

After desugaring, normal declaration, assignment, array initialization, heap, and type checking rules apply.

## 5.1 Declaration Initializer Sugar

This:

```franko
int x = 10;
```

is accepted in statement contexts where declaration followed by assignment is legal, and is desugared to:

```franko
int x;
x = 10;
```

At global scope, declaration initializer sugar is currently invalid if it desugars into an assignment, because global executable statements are not allowed.

## 5.2 Dynamic Array Call-Init Declaration Sugar

This:

```franko
array<int> arr(20);
alloc array<int> arr2(20);
```

is desugared to:

```franko
array<int> arr;
arr(20);
```

```franko
alloc array<int> arr2;
arr2(20);
```

The call form itself (`arr(20)`) is later recognized as array intrinsic `init` sugar. See [Array Intrinsics](20-array-intrinsics.md).

## 5.3 Array Initializer List Desugaring

This:

```franko
xs = [1, 2, 3];
```

is desugared to:

```franko
xs[0] = 1;
xs[1] = 2;
xs[2] = 3;
```

Nested initializer lists are recursively lowered to nested indexed assignments.

## 5.4 String Literal Desugaring

String literals are converted to array initializer lists of UTF-8 byte values before ordinary initializer lowering:

```franko
s = "ABC";
```

first becomes:

```franko
s = [65, 66, 67];
```

and is then lowered to indexed byte assignments. No null terminator is added.

String literals are only valid in assignment/declaration initializer contexts; they are not general expressions. See [String Literals](22-string-literals.md).

## 5.5 `ndarray` Type Desugaring

`ndarray<T, D1, D2, ..., Dn>` is sugar for nested static arrays:

```franko
ndarray<byte, 12, 20, 30>
```

desugars to:

```franko
array<array<array<byte, 30>, 20>, 12>
```

so that `arr[i][j][k]` indexes `i` along the first dimension (`12`), `j` along the second (`20`), and `k` along the last (`30`). See [Arrays](19-arrays.md).

## 5.6 Braceless Control-Flow Bodies

`if` and `while` bodies may be a single statement without braces:

```franko
if (x) print(x);
while (i < 10) i = i + 1;
```

During desugaring, a braceless body is wrapped in an implicit block:

```franko
if (x) { print(x); }
while (i < 10) { i = i + 1; }
```

This makes braceless and braced bodies scope identically: a declaration inside a braceless body is scoped to that implicit block and is not visible after the `if`/`while`. See [Statements](18-statements.md).