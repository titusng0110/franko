# 18. Conditions

`if`, `while`, and `for` conditions must be integer expressions.

`for` conditions are checked after desugaring, when the loop has been lowered to a `while` loop. See [Statements](19-statements.md).

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
