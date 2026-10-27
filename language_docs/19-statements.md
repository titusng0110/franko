# 19. Statements

Franko supports the following statements inside function bodies and blocks:

- variable declarations;
- assignments;
- compound assignments (`+=`, `-=`, `*=`, `/=`, `%=`);
- increment and decrement statements (`x++`, `x--`);
- blocks;
- `if`;
- `while`;
- `for`;
- `return`;
- `print`;
- expression statements;
- function call statements;
- array intrinsic statement forms;
- array initializer assignments.

Executable statements are not allowed at global scope.

Assignments, compound assignments, and increment/decrement are statement forms only: `x++` never produces a value, so `print(x++)` does not parse. See [Assignment](14-assignment.md).

## 19.1 Blocks

A block is a sequence of statements enclosed in braces and creates a new lexical scope.

The braces of a function body are an exception: they do not create an additional scope beyond the function's parameter/body scope. See [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md).

## 19.2 If Statements

The condition must be an integer expression.

Braces are optional: the body may be a single statement. A braceless body is wrapped in an implicit block during desugaring, so declarations inside it are scoped to that block and are not visible after the `if`. See [Desugaring](05-desugaring.md).

## 19.3 While Statements

The condition must be an integer expression.

Braces are optional: the body may be a single statement. A braceless body is wrapped in an implicit block during desugaring, so declarations inside it are scoped to that block and are not visible after the `while`. See [Desugaring](05-desugaring.md).

## 19.4 For Statements

`for` loops use C-style headers:

```franko
for (init; condition; update) {
    body
}
```

- `init` and `update` are optional simple statements (declarations, assignments, compound assignments, increments, expression statements, and similar);
- the condition is required and must be an integer expression.

Valid:

```franko
for (int32_t i = 0; i < 5; i = i + 1) {
    print(i)
}

for (int32_t i = 0; i < 5; i++) {
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

The loop is desugared before semantic analysis:

```franko
for (int32_t i = 0; i < 5; i = i + 1) {
    print(i)
}
```

becomes:

```franko
{
    int32_t i;
    i = 0;
    while (i < 5) {
        print(i);
        i = i + 1;
    }
}
```

The wrapping block scopes the loop variable declared by `init`: it is not visible after the loop. The `update` statement runs at the end of every body iteration, including the last. It is carried as the desugared loop's epilogue, so `continue` runs it and `break` skips it. See [Break and Continue](19-statements.md) below.

Braces are optional: the body may be a single statement. A braceless body is wrapped in an implicit block during desugaring, so declarations inside it are scoped to that block and are not visible after the `for`. See [Desugaring](05-desugaring.md).

## 19.5 Break and Continue

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
for (int32_t i = 0; i < 5; i = i + 1) {
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
for (int32_t a = 0; a < 3; a = a + 1) {
    for (int32_t b = 0; b < 3; b = b + 1) {
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

On the generated-C++ level, `break` and `continue` lower to `goto` statements targeting per-loop labels, and labels are only generated for loops that are actually targeted. See [Runtime and Backend Notes](31-runtime-and-backend-notes.md).

## 19.6 Return Statements

A bare `return` is valid only in a `void` function.

A `return expr` statement is required for non-`void` functions.

## 19.7 Print Statements

`print` is a dedicated statement, not a function and not a member method:

```franko
print(x);
print(a, b, c);      // multiple arguments printed space-separated
print();              // prints a bare newline
```

The checker validates print arguments as expressions usable where a value is required (in particular, `void` expressions are rejected), but it does not impose a strict printable-type policy.

Current print limitation: only integer primitives and pointers are printable. The C++14 backend can print:

- integer primitives (with `int8_t`/`uint8_t` promoted so a number is shown rather than a character);
- pointers, including the `null` pointer (printed through `const void*`).

Arrays and structs cannot be printed. They are accepted by the checker, but the generated C++ does not compile because the backend has no output form for them. Print individual elements and fields instead: `print(xs[0])`, `print(s.x)`.

Array initializer lists and string literals are not expressions and cannot be printed directly.

Member-call printing such as `x.print()` is not supported; see [Unsupported or Restricted Features](30-unsupported-or-restricted-features.md).

## 19.8 Expression Statements

Function calls are commonly used as expression statements.

Array intrinsic calls are special expression-statement forms:

```franko
arr(10);
arr.memset(0);
arr.memcpy(getptr(other));
arr.uninit();
```
