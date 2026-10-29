# 5. Desugaring

Some Franko source forms are accepted as convenient syntax and rewritten before semantic checking.

Desugaring does not change program meaning. It rewrites compact user-facing syntax into simpler core forms used by the semantic analyzer.

After desugaring, normal declaration, assignment, array initialization, and type checking rules apply.

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
[int] arr(20);
```

is desugared to:

```franko
[int] arr;
arr(20);
```

The call form itself (`arr(20)`) is later recognized as array intrinsic `init` sugar. See [Array Intrinsics](21-array-intrinsics.md).

## 5.3 Array Literal Desugaring

Array literals are not assignment sugar: they are `view<[T, N]>`
values over hoisted constant global arrays, exactly like string
literals. An array literal is never rewritten into statements; its
element type is inferred from the surrounding assignment, return, or
call-argument context during semantic analysis. See
[Array Literals](22-array-literals.md).

## 5.4 String Literal Desugaring

Every string literal is hoisted to a literal-backed global `[byte, N]`
holding its UTF-8 bytes (no null terminator is added) and replaced with a
`getview(...)` expression over that global, of type `view<[byte, N]>`:

```franko
print($("ABC")[0]);
```

first becomes (one global per distinct literal text):

```franko
[byte, 3] _STR_<hash>;
print($(getview(_STR_<hash>))[0]);
```

Identical literals share one global. Because a string literal is a view
value, it cannot initialize or assign an array: copy the bytes with
`memcpy` instead. See [String Literals](23-string-literals.md).

## 5.5 Braceless Control-Flow Bodies

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

## 5.6 For Loop Desugaring

A `for` loop is lowered to declaration/assignment statements and a `while` loop:

```franko
for (int32 i = 0; i < n; i = i + 1) {
    print(i)
}
```

becomes:

```franko
{
    int32 i;
    i = 0;
    while (i < n) {
        print(i);
        i = i + 1;
    }
}
```

The whole loop becomes a block, so a loop variable declared in `init` is scoped to the loop and is not visible after it.

The `init` and `update` parts are optional simple statements. The condition is required. When present, the `update` runs at the end of every iteration, including the last. It is carried as the desugared `while` loop's epilogue rather than being appended to the body block, so `continue` runs it and `break` skips it, matching C's `for` semantics. See [Statements](19-statements.md).

The `init` and `update` statements go through the ordinary statement desugaring rules, so declaration initializer sugar in the header expands normally. See [Statements](19-statements.md).

## 5.7 Inferred Static Array Sizes (`?`)

A static array size may be written as `?` in a `view<[...]>`
declaration with an array-literal initializer:

```franko
view<[int32, ?]> v = [0, 1, 2, 3];
```

During desugaring, each `?` is replaced by the element count of the
corresponding literal level, so the declaration above becomes
`view<[int32, 4]> v`. Nested literals infer level by level:

```franko
view<[[int32, ?], ?]> m = [[1, 2], [3, 4]];
```

becomes `view<[[int32, 2], 2]> m`. `?` may appear at any
level of the nest, including middle levels.

Inference rules:

- `?` is legal only in a local declaration-assignment statement
  (`view<[...]> name = literal;`) whose initializer is an array
  literal (string literals are `view<[byte, N]>` values and do not
  participate);
- the declared type must be a `view`: an array literal is a view value,
  so it never initializes an array declaration;
- when `?` is used in any dimension, the whole array nest must be static
  (no dynamic `[T]` level anywhere);
- when `?` is used in any dimension, the literal must fit exactly at
  every level: a shorter literal is an error (the literal's length is
  its type), and sibling lists at each level must have equal length
  (perfect rectangle);
- an empty array literal is invalid in general, so it cannot infer a
  size.

Misplaced `?` (parameters, return types, struct fields, globals,
pointers, call-style `arr(4)` initialization, non-literal initializers)
is rejected during desugaring. See [Arrays](20-arrays.md) and
[Array Literals](22-array-literals.md).

## 5.8 Constant Expression Evaluation

Every other `constExpr` slot is evaluated during desugaring, alongside `?` inference:

```franko
[int32, 0x10] xs;   // size becomes 16
int8 g = 1 + 2;          // initializer becomes 3 (globals)
struct S {                 // field default becomes 8
    int32 n = 1 << 3
}
```

Static array sizes, global initializers, and struct field defaults all reach semantic analysis as canonical decimal literals. Semantic analysis then only validates the values (positive sizes, `uint32` range, initializer fit) without folding anything again.

Evaluation failures are desugaring diagnostics:

```franko
[int32, 10 / 0] b;   // invalid: Division by zero in constant expression
```

Constant expressions inside ordinary code are not `constExpr` slots and are unaffected: they are still folded later, during semantic analysis. See [Constant Folding](10-constant-folding.md).

## 5.9 Increment and Decrement Desugaring

Postfix increment and decrement statements are sugar for compound assignment:

```franko
x++;
x--;
```

are lowered to:

```franko
x += 1;
x -= 1;
```

before semantic analysis, so every rule that applies to `+=` / `-=` (integer storage-backed lvalue target, operand rules, single evaluation of the target) applies identically to `x++` / `x--`. The literal `1` remains a fluid integer constant. See [Assignment](14-assignment.md).