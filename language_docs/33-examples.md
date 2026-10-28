# 33. Examples

Most executable examples should be placed inside a function body, typically `main`, because global scope allows only declarations.

## 33.1 Basic Integers

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

## 33.2 Static Array Initializer Assignment

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

## 33.3 Static Partial Initializer Assignment

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs = [1, 2];

    return 0;
}
```

`xs[2]` is untouched.

## 33.4 Dynamic Array Initializer Assignment

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

## 33.5 Runtime Expressions Inside Initializer Lists

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

## 33.6 Nested Static Arrays

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

## 33.7 Nested Dynamic Arrays

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

## 33.8 Declaration Initializer with Static Array

```franko
func main() -> int32_t {
    array<int32_t, 3> xs = [1, 2, 3];

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 33.9 Dynamic Array Declaration Initializer Without Initialization

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

## 33.10 Array Initializer Through Pointer

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;
    ptr<array<int32_t, 3>> p;

    p = getptr(xs);

    $(p) = [1, 2, 3];

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
    ptr<array<int32_t, 3>> p;

    p = getptr(xs);

    $(p)[0] = 1;
    $(p)[1] = 2;
    $(p)[2] = 3;

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 33.11 String Literal Bytes

```franko
func main() -> int32_t {
    array<byte> str;     // dynamic array: str(200) and str.uninit() require one
    uint i = 0;

    str(200);
    str.memset(0);
    str.memcpy("Hello,\tWorld!\n\"Franko\"");   // 22 bytes copied in

    while (i < 22) {
        print(str[i]);
        i = i + 1;
    }

    str.uninit();
    return 0;
}
```

A string literal is a `view<array<byte, N>>`; byte buffers receive its
content through `memcpy`. See [String Literals](23-string-literals.md).

## 33.12 Nested Static Array Grid

```franko
func fill(ptr<array<array<byte, 3>, 2>> grid) -> void {
    $(grid).memset(0);
    $(grid)[0][1] = 7;
}

func main() -> int32_t {
    array<array<byte, 3>, 2> grid;

    fill(getptr(grid));

    print(grid[0][1]);
    print(grid[1][2]);

    return 0;
}
```

`array<array<byte, 3>, 2>` is a 2 by 3 grid: `grid[r][c]` indexes `r` along the outer size (`2`) and `c` along the inner size (`3`).

## 33.13 Status-Returning Intrinsics

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

## 33.14 Struct Declaration and Field Access

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

## 33.15 Struct Field Defaults

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

## 33.16 Whole-Struct Assignment

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

## 33.17 Structs Passed by Value and by Pointer

Assignable structs may be passed and returned by value; pointer-only structs must go through `ptr<X>`.

```franko
struct Point {
    int32_t x;
    int32_t y
}

func dist2(Point p) -> int32_t {
    return p.x * p.x + p.y * p.y;
}

func move(ptr<Point> p) -> void {
    $(p).x = $(p).x + 1;
    $(p).y = $(p).y + 1;
}

func main() -> int32_t {
    Point p;

    p.x = 3;
    p.y = 4;

    print(dist2(p));   // 25

    move(getptr(p));

    print(p.x);        // 4
    print(p.y);        // 5

    return 0;
}
```

## 33.18 Structs with Array Fields

An array field makes the struct non-assignable: the struct is pointer-only and its arrays are managed through the usual intrinsics.

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

Dynamic array fields are not part of the struct declaration; allocate them explicitly with `field(n)` as above.

## 33.19 Struct Globals

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

## 33.20 Array `length()`

```franko
func main() -> int32_t {
    array<int32_t> xs;
    xs.init(4);
    print(xs.length());   // 4

    array<int32_t, 3> ys;
    ys = [10, 20, 30];
    print(ys.length());   // 3 (compile-time constant)

    for (uint32_t i = 0; i < ys.length(); i = i + 1) {
        print(ys[i]);     // 10 20 30
    }

    xs.uninit();
    print(xs.length());   // 0
    return 0;
}
```

## 33.21 Inferred Static Array Sizes (`?`)

```franko
func main() -> int32_t {
    array<int32_t, ?> a = [0, 1, 2, 3];
    print(a.length());   // 4

    array<byte, ?> s = [104, 101, 108, 108, 111];
    print(s.length());   // 5

    array<array<int32_t, ?>, ?> m = [[1, 2], [3, 4]];
    print(m.length());      // 2
    print(m[0].length());   // 2

    return 0;
}
```

## 33.22 Command-Line Arguments

```franko
func main(view<array<array<byte>>> args) -> int32_t {
    uint32_t n = $(args).length();
    print(n - 1);   // user argument count (args[0] is the program name)

    uint32_t i = 1;
    while (i < n) {
        print($(args)[i].length());   // byte length of argument i
        i = i + 1;
    }

    return 0;
}
```

Running the binary with two arguments prints `2`, then each argument's length. Each argument arrives as its raw bytes with no NUL terminator, and the args parameter needs no null guard: the template wrapper always passes a live pointer (see [Runtime and Backend Notes](31-runtime-and-backend-notes.md)).