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

The call form itself (`arr(20)`) is later recognized as array intrinsic `init` sugar. See [Array Intrinsics](21-array-intrinsics.md).

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

String literals are only valid in assignment/declaration initializer contexts; they are not general expressions. See [String Literals](23-string-literals.md).

## 5.5 `ndarray` Type Desugaring

`ndarray<T, D1, D2, ..., Dn>` is sugar for nested static arrays:

```franko
ndarray<byte, 12, 20, 30>
```

desugars to:

```franko
array<array<array<byte, 30>, 20>, 12>
```

so that `arr[i][j][k]` indexes `i` along the first dimension (`12`), `j` along the second (`20`), and `k` along the last (`30`). See [Arrays](20-arrays.md).

## 5.6 Braceless Control-Flow Bodies

`if`, `while`, and `for` bodies may be a single statement without braces:

```franko
if (x) print(x);
while (i < 10) i = i + 1;
for (; i < 10; i = i + 1) print(i);
```

During desugaring, a braceless body is wrapped in an implicit block:

```franko
if (x) { print(x); }
while (i < 10) { i = i + 1; }
for (; i < 10; i = i + 1) { print(i); }
```

This makes braceless and braced bodies scope identically: a declaration inside a braceless body is scoped to that implicit block and is not visible after the `if`/`while`/`for`. See [Statements](19-statements.md).

## 5.7 For Loop Desugaring

A `for` loop is lowered to declaration/assignment statements and a `while` loop:

```franko
for (int32_t i = 0; i < n; i = i + 1) {
    print(i)
}
```

becomes:

```franko
{
    int32_t i;
    i = 0;
    while (i < n) {
        print(i);
        i = i + 1;
    }
}
```

The whole loop becomes a block, so a loop variable declared in `init` is scoped to the loop and is not visible after it.

The `init` and `update` parts are optional simple statements. The condition is required. When present, the `update` is appended to the end of the loop body so it runs on every iteration, including the last. Franko has no `break` or `continue`, so this lowering is exact.

The `init` and `update` statements go through the ordinary statement desugaring rules, so declaration initializer sugar in the header expands normally. See [Statements](19-statements.md).

## 5.8 Inferred Static Array Sizes (`?`)

A static array size (or `ndarray` dimension) may be written as `?` when the array is declared with an array-literal or string-literal initializer:

```franko
array<int32_t, ?> a = [0, 1, 2, 3];
```

During desugaring, each `?` is replaced by the element count of the corresponding initializer level, so the declaration above becomes:

```franko
array<int32_t, 4> a;
a[0] = 0;
a[1] = 1;
a[2] = 2;
a[3] = 3;
```

String literals count UTF-8 bytes (`array<byte, ?> s = "hello"` infers `5`), and nested literals infer level by level:

```franko
array<array<int32_t, ?>, ?> m = [[1, 2], [3, 4]];
```

becomes `array<array<int32_t, 2>, 2> m` with indexed assignments. `?` may appear in any dimension, including middle `ndarray` dimensions.

Inference rules:

- `?` is legal only in a local declaration-assignment statement (`T name = literal;`, including `alloc` form) whose initializer is an array literal or a string literal;
- when `?` is used in any dimension, the whole array nest must be static (no dynamic `array<T>` level anywhere);
- when `?` is used in any dimension, the initializer must fit exactly at every level: a shorter list is an error (no partial fill), and sibling lists at each level must have equal length (perfect rectangle);
- an empty initializer list cannot infer a size;
- without `?`, the existing lenient rules are unchanged: shorter lists partially fill, and jagged lists are allowed.

Misplaced `?` (parameters, return types, struct fields, globals, addresses, call-style `arr(4)` initialization, non-literal initializers) is rejected during desugaring. See [Arrays](20-arrays.md) and [Array Initializer Lists](22-array-initializer-lists.md).

## 5.9 Constant Expression Evaluation

Every other `constExpr` slot is evaluated during desugaring, alongside `?` inference:

```franko
array<int32_t, 0x10> xs;   // size becomes 16
int8_t g = 1 + 2;          // initializer becomes 3 (globals)
struct S {                 // field default becomes 8
    int32_t n = 1 << 3
}
```

Static array sizes, `ndarray` dimensions, global initializers, and struct field defaults all reach semantic analysis as canonical decimal literals. Semantic analysis then only validates the values (positive sizes, `uint32_t` range, initializer fit) without folding anything again.

Evaluation failures are desugaring diagnostics:

```franko
array<int32_t, 10 / 0> b;   // invalid: Division by zero in constant expression
```

Constant expressions inside ordinary code are not `constExpr` slots and are unaffected: they are still folded later, during semantic analysis. See [Constant Folding](10-constant-folding.md).