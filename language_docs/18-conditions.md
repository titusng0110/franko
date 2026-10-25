# 18. Conditions

`if`, `while`, and `for` conditions must be integer expressions.

`for` conditions are checked after desugaring, when the loop has been lowered to a `while` loop. See [Statements](19-statements.md).

Valid:

```franko
int32_t x;

if (x) {
    print(x);
}
```

Invalid:

```franko
array<int32_t> arr;

if (arr) {
    print(1);
}
```

Invalid:

```franko
addr<int32_t> p;

if (p) {
    print(1);
}
```

To test addresses, compare them explicitly:

```franko
if (p == q) {
    print(1);
}
```
