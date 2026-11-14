# 25. Shorthands and Equivalences

Some Franko source forms are convenient shorthand for simpler forms. The
shorthand and its equivalent have the same meaning; the normal
declaration, assignment, array initialization, and type rules apply to
the equivalent form.

## 25.1 Declaration Initialization

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
forms are valid (constant initializer, static address, string expression, or an
array literal through a pointer). See
[Declarations](18-declarations.md#188-const-storage).

## 25.2 Array and Aggregate Literal Values

Array literals and aggregate literals are ordinary values, not declaration
shorthand. See [Array Literals](22-array-literals.md) and
[Aggregate Literals](30-aggregate-literals.md).

## 25.3 String Literal Values

String literals are untyped compile-time constants. They fold with `+` and
comparisons and materialize as read-only `view<[byte, N]>` values over
immutable UTF-8 storage in value positions. See
[String Literals](24-string-literals.md).

## 25.4 Braceless Control-Flow Bodies

`if`, `while`, and `for` bodies may be a single statement without braces:

```franko
if (x) stdout(x);
while (i < 10) i = i + 1;
for (; i < 10; i = i + 1) stdout(i);
```

A braceless body behaves as if it were wrapped in an implicit block:

```franko
if (x) { stdout(x); }
while (i < 10) { i = i + 1; }
for (; i < 10; i = i + 1) { stdout(i); }
```

This makes braceless and braced bodies scope identically: a declaration
inside a braceless body is scoped to that implicit block and is not
visible after the `if`/`while`/`for`. See [Statements](17-statements.md).

## 25.5 For Loop Equivalence

A `for` loop is equivalent to declaration/assignment statements and a
`while` loop:

```franko
for (int32 i = 0; i < n; i = i + 1) {
    stdout(i)
}
```

is equivalent to:

```franko
{
    int32 i;
    i = 0;
    while (i < n) {
        stdout(i);
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

## 25.6 Inferred Static Array Sizes (`?`)

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
- an empty array literal becomes `null` and carries no length, so it
  cannot infer a size.

Misplaced `?` (parameters, return types, struct fields, globals,
pointers, non-literal initializers) is rejected. See
[Arrays](21-arrays.md) and [Array Literals](22-array-literals.md).

## 25.7 Constant Expression Evaluation

Constant-expression evaluation and folding are documented in
[Constant Folding](08-constant-folding.md).

## 25.8 Increment and Decrement Equivalence

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

## 25.9 Aggregate Field Initialization

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
[Aggregate Literals](30-aggregate-literals.md).

## 25.10 Named Arguments and Defaults

Named arguments and omitted optional arguments are resolved during semantic
analysis. A call is rewritten to a fully positional call in parameter order;
each omitted optional argument is inserted as a copy of the declaration's
compiled default:

```franko
func f(int32 a, int32 b = 5) -> int32 { return a + b }

f(1)          // semantic call: f(1, 5)
f(b = 7, a = 1)   // semantic call: f(1, 7)
```

The default expression is resolved once at its declaration, using the
parameter type as the expected type. Only compile-time forms are allowed
(integer constant expressions, `null`, strings, array literals, enum variants,
folded `#` expressions, and value-assignable struct literals). The inserted
argument is an ordinary semantic value; C++ code generation sees the same
full-arity call it would see if the argument had been written explicitly.
See [Functions](19-functions.md#1915-named-and-optional-arguments).
