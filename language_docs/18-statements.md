# 18. Statements

Franko supports the following statements inside function bodies and blocks:

- variable declarations;
- assignments;
- blocks;
- `if`;
- `while`;
- `return`;
- `print`;
- `del`;
- expression statements;
- function call statements;
- array intrinsic statement forms;
- array initializer assignments.

Executable statements are not allowed at global scope.

## 18.1 Blocks

A block is a sequence of statements enclosed in braces and creates a new lexical scope.

## 18.2 If Statements

The condition must be an integer expression.

## 18.3 While Statements

The condition must be an integer expression.

## 18.4 Return Statements

A bare `return` is valid only in a `void` function.

A `return expr` statement is required for non-`void` functions.

## 18.5 Print Statements

`print` is a dedicated statement, not a function and not a member method:

```franko
print(x);
print(a, b, c);      // multiple arguments printed space-separated
print();              // prints a bare newline
```

The checker validates print arguments as expressions usable where a value is required (in particular, `void` expressions are rejected), but it does not impose a strict printable-type policy.

The C++14 backend can actually print:

- integer primitives (with `int8_t`/`uint8_t` promoted so a number is shown rather than a character);
- addresses (printed through `const void*`).

Other value types, such as arrays, are accepted by the checker but have no backend print form, so printing them does not produce compilable C++.

Array initializer lists and string literals are not expressions and cannot be printed directly.

Member-call printing such as `x.print()` is not supported; see [Unsupported or Restricted Features](28-unsupported-or-restricted-features.md).

## 18.6 Expression Statements

Function calls are commonly used as expression statements.

Array intrinsic calls are special expression-statement forms:

```franko
arr(10);
arr.memset(0);
arr.memcpy(getaddr(other));
arr.uninit();
```
