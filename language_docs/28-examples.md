# 28. Examples

Most executable examples should be placed inside a function body, typically `main`, because global scope allows only declarations.

## 28.1 Basic Integers

```franko
func main() -> int32_t {
    int32_t x;
    int32_t y;

    x = 10;
    y = x + 5;

    print(y);

    return 0;
}
```

## 28.2 Static Array Initializer Assignment

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs = [1, 2, 3];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

Equivalent core form:

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs[0] = 1;
    xs[1] = 2;
    xs[2] = 3;

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 28.3 Static Partial Initializer Assignment

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs = [1, 2];

    return 0;
}
```

`xs[2]` is untouched.

## 28.4 Dynamic Array Initializer Assignment

```franko
func main() -> int32_t {
    array<int32_t> xs;

    xs(3);

    xs = [10, 20, 30];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    xs.uninit();

    return 0;
}
```

## 28.5 Runtime Expressions Inside Initializer Lists

```franko
func getValue() -> int32_t {
    return 10;
}

func main() -> int32_t {
    int32_t x;

    x = 5;

    array<int32_t> xs;

    xs(4);

    xs = [1, x, getValue(), 1 + 2];

    xs.uninit();

    return 0;
}
```

## 28.6 Nested Static Arrays

```franko
func main() -> int32_t {
    array<array<int32_t, 2>, 2> matrix;

    matrix = [
        [1, 2],
        [3, 4]
    ];

    print(matrix[0][0]);
    print(matrix[1][1]);

    return 0;
}
```

## 28.7 Nested Dynamic Arrays

```franko
func main() -> int32_t {
    array<array<int32_t>> matrix;

    matrix(2);
    matrix[0](3);
    matrix[1](3);

    matrix = [
        [1, 2, 3],
        [4, 5, 6]
    ];

    matrix[0].uninit();
    matrix[1].uninit();
    matrix.uninit();

    return 0;
}
```

## 28.8 Declaration Initializer with Static Array

```franko
func main() -> int32_t {
    array<int32_t, 3> xs = [1, 2, 3];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 28.9 Invalid Dynamic Array Declaration Initializer

```franko
func main() -> int32_t {
    array<int32_t> xs = [1, 2, 3];

    return 0;
}
```

Invalid because this desugars to indexed assignments without initializing dynamic storage.

Correct:

```franko
func main() -> int32_t {
    array<int32_t> xs;

    xs(3);

    xs = [1, 2, 3];

    xs.uninit();

    return 0;
}
```

## 28.10 Array Initializer Through Address

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;
    addr<array<int32_t, 3>> p;

    p = getaddr(xs);

    deref(p) = [1, 2, 3];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

Equivalent core form:

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;
    addr<array<int32_t, 3>> p;

    p = getaddr(xs);

    deref(p)[0] = 1;
    deref(p)[1] = 2;
    deref(p)[2] = 3;

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```
