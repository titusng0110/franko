# 24. Shorthands and Equivalences

Some Franko source forms are convenient shorthand for simpler forms. The
shorthand and its equivalent have the same meaning; the normal
declaration, assignment, array initialization, and type rules apply to
the equivalent form.

## 24.1 Declaration Initialization

This:

```franko
int x = 10;
```

is a declaration-initialization statement, not shorthand for a separate
assignment. It declares `x` and initializes it as one construct. The same
holds for the field-wise form and for `const`:

```franko
Literal lit { x = 0 }
const int32 y = 10
const Point p { x = 1 }
```

Declaration initialization is evaluated once as part of the declaration; a
`const` variable can therefore be initialized here and nowhere else. At
global scope only the existing global initializer
forms are valid (constant initializer, static address, string literal, or an
array literal through a pointer). See
[Declarations](18-declarations.md#188-const-storage).

## 24.2 Array and Aggregate Literal Values

Array literals are not assignment shorthand: they are `view<[T, N]>` values
over immutable constant storage, exactly like string literals. Their element
type is inferred from the surrounding assignment, return, or call-argument
context. See [Array Literals](21-array-literals.md).

Aggregate literals are likewise ordinary values, not statement shorthand: they
construct a temporary struct or tuple value. See
[Aggregate Literals](29-aggregate-literals.md).

## 24.3 String Literal Values

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

## 24.4 Braceless Control-Flow Bodies

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
visible after the `if`/`while`/`for`. See [Statements](17-statements.md).

## 24.5 For Loop Equivalence

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
semantics. See [Statements](17-statements.md).

The `init` and `update` statements follow the ordinary statement rules, so
declaration initialization in the header is handled normally.
See [Statements](17-statements.md).

## 24.6 Inferred Static Array Sizes (`?`)

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
pointers, non-literal initializers) is rejected. See
[Arrays](20-arrays.md) and [Array Literals](21-array-literals.md).

## 24.7 Constant Expression Evaluation

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
time. See [Constant Folding](08-constant-folding.md).

## 24.8 Increment and Decrement Equivalence

Postfix increment and decrement statements are equivalent to compound
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
constant. See [Assignment](16-assignment.md).

## 24.9 Aggregate Field Initialization

A field-wise declaration initializer is declaration initialization, not a
separate assignment statement. It initializes one ordinary field at a time,
in source order:

```franko
Literal lit { def = 1, x = 0 }
```

behaves as:

```franko
Literal lit
lit.def = 1
lit.x = 0
```

but is evaluated as declaration initialization, so it can initialize a
`const` aggregate and never invokes `OPERATOR_MOV`. An
all-unnamed tuple declaration accepts positional entries, which are
written to `_0`, `_1`, … in order and fill the first N elements:

```franko
(int32, int32) pair { 0, 1 }
```

is equivalent to:

```franko
(int32, int32) pair
pair._0 = 0
pair._1 = 1
```

A partial list such as `{ 0 }` writes only `_0`. The brace must immediately
follow the identifier; a newline ends the declaration. Duplicate field
initializers and positional lists that exceed the tuple size are rejected. Positional entries are invalid for structs and named or
mixed tuple spellings, and positional and named entries cannot be mixed. No
temporary aggregate value is created, so the form also works for pointer-only
aggregates; each entry must still obey ordinary assignment rules. See
[Aggregate Literals](29-aggregate-literals.md).
