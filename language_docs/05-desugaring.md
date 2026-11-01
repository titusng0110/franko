# 5. Desugaring

Some Franko source forms are convenient shorthand for simpler forms. The
shorthand and its equivalent have the same meaning; the normal
declaration, assignment, array initialization, and type rules apply to
the equivalent form.

## 5.1 Declaration Initializer Sugar

This:

```franko
int x = 10;
```

is accepted in statement contexts where a declaration followed by an
assignment is legal, and is shorthand for:

```franko
int x;
x = 10;
```

At global scope, declaration initializer sugar is currently invalid when
its equivalent form is an assignment, because executable statements are
not allowed at global scope.

## 5.2 Dynamic Array Call-Init Declaration Sugar

This:

```franko
[int] arr(20);
```

is shorthand for:

```franko
[int] arr;
arr(20);
```

The call form itself (`arr(20)`) is array intrinsic `init` shorthand. See
[Array Intrinsics](21-array-intrinsics.md).

## 5.3 Array Literal Desugaring

Array literals are not assignment sugar: they are `view<[T, N]>` values
over immutable constant storage, exactly like string literals. An array
literal is never rewritten into statements; its element type is inferred
from the surrounding assignment, return, or call-argument context. See
[Array Literals](22-array-literals.md).

## 5.4 String Literal Desugaring

Every string literal is a read-only `view<[byte, N]>` over immutable
storage holding its UTF-8 bytes. No null terminator is added, and
identical literal texts share the same storage:

```franko
print($("ABC")[0]);
```

The literal has type `view<[byte, 3]>` and can be indexed after a
dereference. Because a string literal is a view value, it cannot
initialize or assign an array: copy the bytes with `memcpy` instead. See
[String Literals](23-string-literals.md).

## 5.5 Braceless Control-Flow Bodies

`if`, `while`, and `for` bodies may be a single statement without braces:

```franko
if (x) print(x);
while (i < 10) i = i + 1;
for (; i < 10; i = i + 1) print(i);
```

A braceless body behaves as if it were wrapped in an implicit block:

```franko
if (x) { print(x); }
while (i < 10) { i = i + 1; }
for (; i < 10; i = i + 1) { print(i); }
```

This makes braceless and braced bodies scope identically: a declaration
inside a braceless body is scoped to that implicit block and is not
visible after the `if`/`while`/`for`. See [Statements](19-statements.md).

## 5.6 For Loop Desugaring

A `for` loop is equivalent to declaration/assignment statements and a
`while` loop:

```franko
for (int32 i = 0; i < n; i = i + 1) {
    print(i)
}
```

is equivalent to:

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

The whole loop behaves as a block, so a loop variable declared in `init`
is scoped to the loop and is not visible after it.

The `init` and `update` parts are optional header statements (every
simple-statement form except `return`). The condition is required. When
present, `update` runs at the end of every iteration, including the
last: `continue` runs it, and `break` skips it, matching C's `for`
semantics. See [Statements](19-statements.md).

The `init` and `update` statements follow the ordinary statement sugar
rules, so declaration initializer sugar in the header expands normally.
See [Statements](19-statements.md).

## 5.7 Inferred Static Array Sizes (`?`)

A static array size may be written as `?` in a `view<[...]>`
declaration with an array-literal initializer:

```franko
view<[int32, ?]> v = [0, 1, 2, 3];
```

Each `?` is inferred from the element count of the corresponding literal
level, so the declaration above is `view<[int32, 4]> v`. Nested literals
infer level by level:

```franko
view<[[int32, ?], ?]> m = [[1, 2], [3, 4]];
```

is `view<[[int32, 2], 2]> m`. `?` may appear at any level of the nest,
including middle levels.

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
is rejected. See [Arrays](20-arrays.md) and
[Array Literals](22-array-literals.md).

## 5.8 Constant Expression Evaluation

Constant expressions are evaluated at compile time in every
constant-expression position:

```franko
[int32, 0x10] xs;   // size becomes 16
int8 g = 1 + 2;          // initializer becomes 3 (globals)
struct S {                 // field default becomes 8
    int32 n = 1 << 3
}
```

Static array sizes, global initializers, struct field defaults, and
`break N` levels are reduced to concrete values. The values are then
validated in context (positive sizes, `uint32` range, initializer fit)
without folding anything again.

Evaluation failures are compile errors:

```franko
[int32, 10 / 0] b;   // invalid: Division by zero in constant expression
```

Constant expressions inside ordinary code are also folded at compile
time. See [Constant Folding](10-constant-folding.md).

## 5.9 Increment and Decrement Desugaring

Postfix increment and decrement statements are sugar for compound
assignment:

```franko
x++;
x--;
```

are equivalent to:

```franko
x += 1;
x -= 1;
```

Every rule that applies to `+=` / `-=` (integer storage-backed lvalue
target, operand rules, single evaluation of the target) applies
identically to `x++` / `x--`. The literal `1` remains a fluid integer
constant. See [Assignment](14-assignment.md).
