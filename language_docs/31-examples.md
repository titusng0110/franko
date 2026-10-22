# 31. Examples

Most executable examples should be placed inside a function body, typically `main`, because global scope allows only declarations.

## 31.1 Basic Integers

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

## 31.2 Static Array Initializer Assignment

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

## 31.3 Static Partial Initializer Assignment

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs = [1, 2];

    return 0;
}
```

`xs[2]` is untouched.

## 31.4 Dynamic Array Initializer Assignment

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

## 31.5 Runtime Expressions Inside Initializer Lists

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

## 31.6 Nested Static Arrays

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

## 31.7 Nested Dynamic Arrays

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

## 31.8 Declaration Initializer with Static Array

```franko
func main() -> int32_t {
    array<int32_t, 3> xs = [1, 2, 3];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 31.9 Dynamic Array Declaration Initializer Without Initialization

```franko
func main() -> int32_t {
    array<int32_t> xs = [1, 2, 3];

    return 0;
}
```

This desugars to indexed assignments without initializing dynamic storage. It is not rejected at compile time; the generated code accesses an uninitialized dynamic array and the runtime aborts with a "Franko_Dynamic_Array not initialized" error.

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

## 31.10 Array Initializer Through Address

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

## 31.11 String Literal Assignment

```franko
func main() -> int32_t {
    array<byte> str;     // dynamic array: str(200) and str.uninit() require one
    uint i = 0;

    str(200);
    str.memset(0);
    str = "Hello,\tWorld!\n\"Franko\"";

    while (i < 22) {
        print(str[i]);
        i = i + 1;
    }

    str.uninit();
    return 0;
}
```

See [String Literals](22-string-literals.md).

## 31.12 `ndarray` Grid

```franko
func fill(addr<ndarray<byte, 2, 3>> grid) -> void {
    deref(grid).memset(0);
    deref(grid)[0][1] = 7;
}

func main() -> int32_t {
    ndarray<byte, 2, 3> grid;

    fill(getaddr(grid));

    print(grid[0][1]);
    print(grid[1][2]);

    return 0;
}
```

`ndarray<byte, 2, 3>` is sugar for `array<array<byte, 3>, 2>`.

## 31.13 Status-Returning Intrinsics

```franko
func main() -> int32_t {
    array<int32_t> xs;
    int32_t status = 0;

    status = xs.init_zero(4);

    if (status != 0) {
        return -1;
    }

    status = xs.resize(8);

    if (status != 0) {
        xs.uninit();
        return -1;
    }

    print(status);

    xs.uninit();
    return 0;
}
```

## 31.14 Struct Declaration and Field Access

```franko
struct Point {
    int32_t x;
    int32_t y
}

func main() -> int32_t {
    Point p;

    p.x = 10;
    p.y = 20;

    print(p.x);
    print(p.y);

    return 0;
}
```

The separator before `}` is optional.

## 31.15 Struct Field Defaults

Field defaults are constant expressions; only primitive fields can have them.

```franko
struct Counter {
    int32_t count = 0;
    uint32_t limit = 100
}

func main() -> int32_t {
    Counter c;

    print(c.count);   // 0
    print(c.limit);   // 100

    return 0;
}
```

## 31.16 Whole-Struct Assignment

A struct is assignable as a whole value only if all of its fields are assignable, recursively.

```franko
struct Pair {
    int32_t a;
    int32_t b
}

func main() -> int32_t {
    Pair p;
    Pair q;

    p.a = 1;
    p.b = 2;

    q = p;            // whole-value copy

    print(q.a);       // 1
    print(q.b);       // 2

    return 0;
}
```

## 31.17 Structs Passed by Value and by Address

Assignable structs may be passed and returned by value; address-only structs must go through `addr<X>`.

```franko
struct Point {
    int32_t x;
    int32_t y
}

func dist2(Point p) -> int32_t {
    return p.x * p.x + p.y * p.y;
}

func move(addr<Point> p) -> void {
    deref(p).x = deref(p).x + 1;
    deref(p).y = deref(p).y + 1;
}

func main() -> int32_t {
    Point p;

    p.x = 3;
    p.y = 4;

    print(dist2(p));   // 25

    move(getaddr(p));

    print(p.x);        // 4
    print(p.y);        // 5

    return 0;
}
```

## 31.18 Structs with Array Fields

An array field makes the struct non-assignable: the struct is address-only and its arrays are managed through the usual intrinsics.

```franko
struct Buffer {
    array<int32_t> data;
    int32_t size = 0
}

func main() -> int32_t {
    Buffer b;

    b.data(4);          // allocate the dynamic array field
    b.data.memset(0);
    b.size = 4;

    b.data[0] = 10;
    b.data[1] = 20;

    print(b.data[0]);
    print(b.size);

    b.data.uninit();
    return 0;
}
```

`alloc` is not allowed in struct fields; allocate dynamic array fields explicitly with `field(n)` as above.

## 31.19 Structs on the Heap

```franko
struct Node {
    int32_t value;
    addr<Node> next
}

func main() -> int32_t {
    alloc Node a;
    alloc Node b;

    a.value = 1;
    b.value = 2;

    a.next = getaddr(b);
    print(deref(a.next).value);   // follows the addr field: deref first

    del a;
    del b;
    return 0;
}
```

`addr<Node>` fields break by-value cycles, so self-referential struct definitions like this are valid.

Franko has no null addresses. An address field that is never assigned is indeterminate, so only follow links that have been explicitly assigned (here `b.next` is never set and is never read).

## 31.20 Struct Globals

Global variable declarations accept constant initializers.

```franko
struct Point {
    int32_t x = 0;
    int32_t y = 0
}

int32_t scale = 2;
Point origin;

func main() -> int32_t {
    origin.x = scale;

    print(origin.x);   // 2
    print(scale);      // 2

    return 0;
}
```

Global declarations are checked before `main` runs; executable statements remain invalid at global scope.