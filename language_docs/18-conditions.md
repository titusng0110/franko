# 18. Conditions

`if`, `while`, and `for` conditions must be integer expressions. A
`for` condition follows the same rule as a `while` condition (the header
condition is required). See [Statements](19-statements.md).

Valid:

```franko
int32 x;

if (x) {
    print(x);
}
```

Invalid:

```franko
[int32] arr;

if (arr) {
    print(1);
}
```

Invalid:

```franko
ptr<int32> p;

if (p) {
    print(1);
}
```

To test pointers, compare them explicitly:

```franko
if (p == q) {
    print(1);
}
```
