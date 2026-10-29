# 32. Examples

Most executable examples should be placed inside a function body, typically `main`, because global scope allows only declarations.

## 32.1 Basic Integers

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

## 32.2 Static Array Copy From a Literal

An array literal is a `view<array<T, N>>` value, so arrays receive its
elements through `memcpy` (see [Array Literals](22-array-literals.md)):

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs.memcpy([1, 2, 3]);

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

## 32.3 Static Partial Copy From a Literal

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;

    xs.memset(0);
    xs.memcpy([1, 2]);

    return 0;
}
```

`xs[2]` is untouched: memcpy copies `min(source, target)` elements.

## 32.4 Dynamic Array Copy From a Literal

```franko
func main() -> int32_t {
    array<int32_t> xs;

    xs(3);

    xs.memcpy([10, 20, 30]);

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    xs.uninit();

    return 0;
}
```

## 32.5 Constant Expressions Inside Array Literals

```franko
func main() -> int32_t {
    view<array<int32_t, 4>> v = [1, 5, 10, 1 + 2];

    print($(v)[0]);
    print($(v)[1]);
    print($(v)[2]);
    print($(v)[3]);

    return 0;
}
```

Elements are compile-time constant expressions; runtime values are
rejected because a literal is hoisted to a constant global array.

## 32.6 Nested Static Array Literals

```franko
func main() -> int32_t {
    array<array<int32_t, 2>, 2> matrix;

    matrix.memcpy([
        [1, 2],
        [3, 4]
    ]);

    print(matrix[0][0]);
    print(matrix[1][1]);

    return 0;
}
```

Nested literals are rectangular and static at every level.

## 32.7 Nested Dynamic Arrays

A nested literal has static rows, so dynamic rows are filled one at a
time:

```franko
func main() -> int32_t {
    array<array<int32_t>> matrix;

    matrix(2);
    matrix[0](3);
    matrix[1](3);

    matrix[0].memcpy([1, 2, 3]);
    matrix[1].memcpy([4, 5, 6]);

    matrix[0].uninit();
    matrix[1].uninit();
    matrix.uninit();

    return 0;
}
```

## 32.8 Declaration Initializer With an Array Literal

A `view` declaration accepts a literal directly; its element type and
length are inferred from the literal and checked against the declared
type:

```franko
func main() -> int32_t {
    view<array<int32_t, 3>> v = [1, 2, 3];
    view<array<int32_t, ?>> w = [4, 5, 6, 7];

    print($(v)[0]);
    print($(w).length());    // 4

    return 0;
}
```

## 32.9 Dynamic View Decay From a Literal

A literal decays to `view<array<T>>` wherever a dynamic view is
expected:

```franko
func take(view<array<int32_t>> v) -> int32_t {
    return $(v)[0];
}

func main() -> int32_t {
    view<array<int32_t>> d = [1, 2, 3];

    print($(d)[2]);
    print(take([4, 5, 6]));

    return 0;
}
```

## 32.10 Array Copy Through Pointer

```franko
func main() -> int32_t {
    array<int32_t, 3> xs;
    ptr<array<int32_t, 3>> p;

    p = getptr(xs);

    $(p).memcpy([1, 2, 3]);

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

## 32.11 String Literal Bytes

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

## 32.12 Nested Static Array Grid

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

## 32.13 Status-Returning Intrinsics

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

## 32.14 Struct Declaration and Field Access

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

## 32.15 Struct Field Defaults

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

## 32.16 Whole-Struct Assignment

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

## 32.17 Structs Passed by Value and by Pointer

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

## 32.18 Structs with Array Fields

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

## 32.19 Struct Globals

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

## 32.20 Array `length()`

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

## 32.21 Inferred Static Array Sizes (`?`)

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

## 32.22 Command-Line Arguments

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

Running the binary with two arguments prints `2`, then each argument's length. Each argument arrives as its raw bytes with no NUL terminator, and the args parameter needs no null guard: the template wrapper always passes a live pointer (see [Compiler Internals](../compiler_docs.md)).