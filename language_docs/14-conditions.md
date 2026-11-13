# 14. Conditions

`if`, `while`, and `for` conditions must be integer expressions. A
`for` condition follows the same rule as a `while` condition (the header
condition is required). See [Statements](17-statements.md).

Valid:

```franko
int32 x;

if (x) {
    stdout(x);
}
```

Invalid:

```franko
[int32] arr;

if (arr) {
    stdout(1);
}
```

Invalid:

```franko
ptr<int32> p;

if (p) {
    stdout(1);
}
```

To test pointers, compare them explicitly:

```franko
if (p == q) {
    stdout(1);
}
```

## Static Conditions

`#if` uses a different rule: its condition must be a compile-time
constant integer, and only the selected branch is used. See
[Compile-Time Reflection](35-compile-time-reflection.md).
