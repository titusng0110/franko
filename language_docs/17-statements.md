# 17. Statements

Franko supports the following statements inside function bodies and blocks:

- variable declarations;
- declaration initialization (`T x = expr`, `T x { field = expr }`, and the
  const forms; see [Declarations](18-declarations.md));
- assignments;
- compound assignments (`+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`);
- increment and decrement statements (`x++`, `x--`);
- blocks;
- `if`;
- `#if` / `#else`;
- `while`;
- `for`;
- `return`;
- expression statements;
- function call statements;
- array intrinsic statement forms.

Executable statements are not allowed at global scope.

Assignments, compound assignments, and increment/decrement are statement forms only: `x++` never produces a value, so it cannot appear as a call argument (for example, `print(x++)` does not parse). See [Assignment](16-assignment.md).

## 17.1 Blocks

A block is a sequence of statements enclosed in braces and creates a new lexical scope.

The braces of a function body are an exception: they do not create an additional scope beyond the function's parameter/body scope. See [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md).

## 17.2 If Statements

The condition must be an integer expression.

Braces are optional: the body may be a single statement. A braceless body behaves as if it were wrapped in an implicit block, so declarations inside it are scoped to that block and are not visible after the `if`. See [Shorthands and Equivalences](25-desugaring.md).

## 17.3 While Statements

The condition must be an integer expression.

Braces are optional: the body may be a single statement. A braceless body behaves as if it were wrapped in an implicit block, so declarations inside it are scoped to that block and are not visible after the `while`. See [Shorthands and Equivalences](25-desugaring.md).

## 17.4 For Statements

`for` loops use C-style headers:

```franko
for (init; condition; update) {
    body
}
```

- `init` and `update` are optional header statements: declarations, assignments, compound assignments, increments, and expression statements. `return` is not allowed in either position;
- the condition is required and must be an integer expression.

Prefer `for` for loops with a clear initialization, condition, and update, such as counted iteration; use `while` when the condition is the only loop control.

Valid:

```franko
for (int32 i = 0; i < 5; i = i + 1) {
    print(i)
}

for (int32 i = 0; i < 5; i++) {
    print(i)
}

for (; i < 5; i = i + 1) {
    print(i)
}

for (i = 0; i < 5;) {
    print(i)
    i = i + 1
}
```

The loop is equivalent to:

```franko
for (int32 i = 0; i < 5; i = i + 1) {
    print(i)
}
```

which behaves as:

```franko
{
    int32 i;
    i = 0;
    while (i < 5) {
        print(i);
        i = i + 1;
    }
}
```

The wrapping block scopes the loop variable declared by `init`: it is not visible after the loop. The `update` statement runs at the end of every body iteration, including the last; `continue` runs it and `break` skips it. See [Break and Continue](17-statements.md) below.

Braces are optional: the body may be a single statement. A braceless body behaves as if it were wrapped in an implicit block, so declarations inside it are scoped to that block and are not visible after the `for`. See [Shorthands and Equivalences](25-desugaring.md).

## 17.5 Break and Continue

`break` exits the innermost enclosing loop. `continue` skips the rest of the innermost loop's body and proceeds to the next iteration:

```franko
while (i < 10) {
    i = i + 1;
    if (i == 5) {
        break;
    }
    if (i == 3) {
        continue;
    }
    print(i);
}
```

In a `for` loop, `continue` still runs the `update` (the loop's final body statement), and `break` skips it, matching C's `for` semantics:

```franko
for (int32 i = 0; i < 5; i = i + 1) {
    if (i == 2) {
        continue;    // i = i + 1 still runs
    }
    if (i == 4) {
        break;       // i = i + 1 does not run
    }
    print(i);
}
```

`break` accepts an optional integer constant expression `N` that selects how many nested loops to exit. `N` must be strictly greater than 0 and no larger than the enclosing loop depth. A plain `break` is equivalent to `break 1`:

```franko
for (int32 a = 0; a < 3; a = a + 1) {
    for (int32 b = 0; b < 3; b = b + 1) {
        if (b == 1) {
            break 2;    // exits both loops
        }
        print(b);
    }
}
```

`N` is evaluated with the constant-expression rules, so expressions such as `break 1 + 1` are permitted and fold at compile time. A level that is not a positive constant integer, or that exceeds the enclosing loop depth, is an error.

`continue` does not take a level: `continue 2` is invalid, and `continue` always targets the innermost loop.

`break` and `continue` are reserved keywords. They are only valid inside a loop; using either outside every loop is an error. They may not appear in a `for` header's `init` or `update` positions.

## 17.6 Return Statements

A bare `return` is valid only in a `void` function.

A `return expr` statement is required for non-`void` functions.

## 17.7 Output (`franko/io`)

Output is a library function, not a statement. Import `franko/io` and call the overloaded `print(...)` functions (see [franko/io](../library_docs/io.md) for the full API):

```franko
import "franko/io"

func main() -> int32 {
    print(42)        // `42` followed by a newline
    print("hi")      // text followed by a newline
    print(1, " ")    // `1` followed by a space (no newline)
    print(2)         // `2` followed by a newline
    print()          // bare newline
    return 0
}
```

A single-argument `print(x)` writes `x` followed by `\n`; a two-argument `print(x, end)` writes `x` followed by `end` verbatim (a null or empty `end` means no suffix and no newline). `print()` writes a bare newline. Overloads exist for all eight integer types, for `view<[uint8]>` text, and for `view<std::String>` from `franko/string`; a null or empty view/string prints nothing. `print_err` mirrors every `print` overload on stderr, with the same suffix and null/empty behavior.

`print` has no overloads for pointers or arrays. Struct printing is limited to `std::String`; print individual elements, fields, or bytes for other structs: `print(xs[0])`, `print(s.x)`. A string literal prints as text (it is a `view<[byte, N]>` value that decays to the `view<[uint8]>` overload); to print a single byte numerically, index it: `print(s[0])`. A bare `print(null)` is ambiguous between the text and `std::String` overloads; use a typed null view when a null text argument is intended.

Integer constants resolve by the overload preference rule: the parameter matching the constant's own type wins when the value fits, otherwise the narrowest fitting type (signed before unsigned at equal width). See [Functions](19-functions.md).

Member-call printing such as `x.print()` is not supported; see [Unsupported or Restricted Features](36-unsupported-or-restricted-features.md).

## 17.8 Expression Statements

Function calls are commonly used as expression statements.

Array intrinsic calls are special expression-statement forms:

```franko
arr.init(10);
arr.memset(0);
arr.memcpy(getptr(other));
arr.uninit();
```

## 17.9 Compile-Time If Statements

`#if` and `#else` are compile-time statement forms:

```franko
#if (constantExpression) {
    ...
} #else {
    ...
}
```

The condition must be a compile-time constant integer. Only the selected
branch is used; the other branch is ignored entirely. Braceless bodies
are allowed, and chaining uses `#else #if`.
A standalone `#else` is a syntax error.
`#if` also works at declaration level: at global scope, inside a
namespace, and inside a struct. Selected declarations and statements are
inserted directly into the enclosing scope, so compile-time branch braces
do not create a scope. See
[Compile-Time Reflection](35-compile-time-reflection.md).
