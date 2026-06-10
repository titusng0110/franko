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

The current checker validates print arguments as expressions but does not impose a strict print-type policy.

Array initializer lists are not expressions and cannot be printed directly.

## 18.6 Expression Statements

Function calls are commonly used as expression statements.

Array intrinsic calls are special expression-statement forms:

```franko
arr(10);
arr.memset(0);
arr.memcpy(other);
arr.uninit();
```
