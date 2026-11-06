# 37. Examples

Most executable examples should be placed inside a function body, typically `main`, because global scope allows only declarations.

Examples that print assume `import "franko/io"` at the top of the file.

## 37.1 Basic Integers

```franko
func main() -> int32 {
    int32 x;
    int32 y;

    x = 10;
    y = x + 5;

    print(y);

    return 0;
}
```

## 37.2 Static Array Copy From a Literal

An array literal is a `view<[T, N]>` value, so arrays receive its
elements through `memcpy` (see [Array Literals](21-array-literals.md)):

```franko
func main() -> int32 {
    [int32, 3] xs;

    xs.memcpy([1, 2, 3]);

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

Equivalent core form:

```franko
func main() -> int32 {
    [int32, 3] xs;

    xs[0] = 1;
    xs[1] = 2;
    xs[2] = 3;

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    return 0;
}
```

## 37.3 Static Partial Copy From a Literal

```franko
func main() -> int32 {
    [int32, 3] xs;

    xs.memset(0);
    xs.memcpy([1, 2]);

    return 0;
}
```

`xs[2]` is untouched: memcpy copies `min(source, target)` elements.

## 37.4 Dynamic Array Copy From a Literal

```franko
func main() -> int32 {
    [int32] xs;

    xs.init(3);

    xs.memcpy([10, 20, 30]);

    print(xs[0]);
    print(xs[1]);
    print(xs[2]);

    xs.uninit();

    return 0;
}
```

## 37.5 Constant Expressions Inside Array Literals

```franko
func main() -> int32 {
    view<[int32, 4]> v = [1, 5, 10, 1 + 2];

    print($(v)[0]);
    print($(v)[1]);
    print($(v)[2]);
    print($(v)[3]);

    return 0;
}
```

Elements are compile-time constant expressions; runtime values are
rejected because a literal is backed by immutable constant storage.

## 37.6 Nested Static Array Literals

```franko
func main() -> int32 {
    [[int32, 2], 2] matrix;

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

## 37.7 Nested Dynamic Arrays

A nested literal has static rows, so dynamic rows are filled one at a
time:

```franko
func main() -> int32 {
    [[int32]] matrix;

    matrix.init(2);
    matrix[0].init(3);
    matrix[1].init(3);

    matrix[0].memcpy([1, 2, 3]);
    matrix[1].memcpy([4, 5, 6]);

    matrix[0].uninit();
    matrix[1].uninit();
    matrix.uninit();

    return 0;
}
```

## 37.8 Declaration Initializer With an Array Literal

A `view` declaration accepts a literal directly; its element type and
length are inferred from the literal and checked against the declared
type:

```franko
func main() -> int32 {
    view<[int32, 3]> v = [1, 2, 3];
    view<[int32, ?]> w = [4, 5, 6, 7];

    print($(v)[0]);
    print($(w).length());    // 4

    return 0;
}
```

## 37.9 Dynamic View Decay From a Literal

A literal decays to `view<[T]>` wherever a dynamic view is
expected:

```franko
func take(view<[int32]> v) -> int32 {
    return $(v)[0];
}

func main() -> int32 {
    view<[int32]> d = [1, 2, 3];

    print($(d)[2]);
    print(take([4, 5, 6]));

    return 0;
}
```

## 37.10 Array Copy Through Pointer

```franko
func main() -> int32 {
    [int32, 3] xs;
    ptr<[int32, 3]> p;

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
func main() -> int32 {
    [int32, 3] xs;
    ptr<[int32, 3]> p;

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

## 37.11 String Literal Bytes

```franko
func main() -> int32 {
    [byte] str;     // dynamic array: str.init(200) and str.uninit() require one

    str.init(200);
    str.memset(0);
    str.memcpy("Hello,\tWorld!\n\"Franko\"");   // 22 bytes copied in

    for (uint i = 0; i < 22; i++) {
        print(str[i]);
    }

    str.uninit();
    return 0;
}
```

A string literal is a `view<[byte, N]>`; byte buffers receive its
content through `memcpy`. See [String Literals](23-string-literals.md).

## 37.12 Nested Static Array Grid

```franko
func fill(ptr<[[byte, 3], 2]> grid) -> void {
    $(grid).memset(0);
    $(grid)[0][1] = 7;
}

func main() -> int32 {
    [[byte, 3], 2] grid;

    fill(getptr(grid));

    print(grid[0][1]);
    print(grid[1][2]);

    return 0;
}
```

`[[byte, 3], 2]` is a 2 by 3 grid: `grid[r][c]` indexes `r` along the outer size (`2`) and `c` along the inner size (`3`).

## 37.13 Status-Returning Intrinsics

```franko
func main() -> int32 {
    [int32] xs;
    int32 status = 0;

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

## 37.14 Struct Declaration and Field Access

```franko
struct Point {
    int32 x;
    int32 y
}

func main() -> int32 {
    Point p = Point { x = 10, y = 20 };

    print(p.x);
    print(p.y);

    return 0;
}
```

The separator before `}` is optional.

A field-wise declaration initializer is the direct alternative:

```franko
func main() -> int32 {
    Point p { x = 10, y = 20 };

    print(p.x);
    print(p.y);

    return 0;
}
```

## 37.15 Struct Field Defaults

Field defaults are constant expressions; only primitive fields can have them.

```franko
struct Counter {
    int32 count = 0;
    uint32 limit = 100
}

func main() -> int32 {
    Counter c;

    print(c.count);   // 0
    print(c.limit);   // 100

    return 0;
}
```

## 37.16 Whole-Struct Assignment

A struct is assignable as a whole value only if all of its fields are assignable, recursively.

```franko
struct Pair {
    int32 a;
    int32 b
}

func main() -> int32 {
    Pair p = Pair { a = 1, b = 2 };
    Pair q;

    q = p;            // whole-value copy

    print(q.a);       // 1
    print(q.b);       // 2

    return 0;
}
```

## 37.17 Structs Passed by Value and by Pointer

Assignable structs may be passed and returned by value; pointer-only structs are accessed through a pointer (`ptr<X>` for ownership/lifecycle, or `mview<X>`/`view<X>` for access).

```franko
struct Point {
    int32 x;
    int32 y
}

func dist2(Point p) -> int32 {
    return p.x * p.x + p.y * p.y;
}

func move(ptr<Point> p) -> void {
    $(p).x = $(p).x + 1;
    $(p).y = $(p).y + 1;
}

func main() -> int32 {
    Point p = Point { x = 3, y = 4 };

    print(dist2(p));   // 25

    move(getptr(p));

    print(p.x);        // 4
    print(p.y);        // 5

    return 0;
}
```

## 37.18 Structs with Array Fields

An array field makes the struct non-assignable: the struct is pointer-only and its arrays are managed through the usual intrinsics.

```franko
struct Buffer {
    [int32] data;
    int32 size = 0
}

func main() -> int32 {
    Buffer b;

    b.data.init(4);     // allocate the dynamic array field
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

Dynamic array fields are not allocated as part of the struct declaration; allocate them explicitly with `field.init(n)` as above.

## 37.19 Struct Globals

Global variable declarations accept constant initializers.

```franko
struct Point {
    int32 x = 0;
    int32 y = 0
}

int32 scale = 2;
Point origin;

func main() -> int32 {
    origin.x = scale;

    print(origin.x);   // 2
    print(scale);      // 2

    return 0;
}
```

Global declarations are checked before `main` runs; executable statements remain invalid at global scope.

## 37.20 Array `length()`

```franko
func main() -> int32 {
    [int32] xs;
    xs.init(4);
    print(xs.length());   // 4

    [int32, 3] ys;
    ys.memcpy([10, 20, 30]);
    print(ys.length());   // 3 (compile-time constant)

    for (uint32 i = 0; i < ys.length(); i = i + 1) {
        print(ys[i]);     // 10 20 30
    }

    xs.uninit();
    print(xs.length());   // 0
    return 0;
}
```

## 37.21 Inferred Static Array Sizes (`?`)

```franko
func main() -> int32 {
    view<[int32, ?]> a = [0, 1, 2, 3];
    print($(a).length());   // 4

    view<[byte, ?]> s = [104, 101, 108, 108, 111];
    print($(s).length());   // 5

    view<[[int32, ?], ?]> m = [[1, 2], [3, 4]];
    print($(m).length());      // 2
    print($(m)[0].length());   // 2

    return 0;
}
```

## 37.22 Command-Line Arguments

```franko
func main(view<[[byte]]> args) -> int32 {
    uint32 n = $(args).length();
    print(n - 1);   // user argument count (args[0] is the program name)

    for (uint32 i = 1; i < n; i++) {
        print($(args)[i].length());   // byte length of argument i
    }

    return 0;
}
```

Running the binary with two arguments prints `2`, then each argument's length. Each argument arrives as its raw bytes with no NUL terminator, and the args parameter always receives a live view, so it needs no null guard.

## 37.23 Byte-Array Search, Endian, and Bit Intrinsics

```franko
import "franko/io"

func main() -> int32 {
    [byte, 8] packet;
    packet.memset(0);

    // Big-endian header: 0xCAFEBABE, then a little-endian length.
    packet.write_uint32_BE(0, 0xCAFEBABE);
    packet.write_uint16_LE(4, 300);
    print(packet.read_uint32_BE(0));   // 3405691582
    print(packet.read_uint16_LE(4));   // 300

    // Search the payload with memchr / memmem.
    packet[6] = 65;
    packet[7] = 66;
    print(packet.memchr(65));          // 6
    print(packet.memmem("AB"));        // 6
    print(packet.memchr(67));          // 4294967295 (not found)

    // Compare whole byte arrays.
    [byte, 8] copy;
    copy.memset(0);
    copy.memcpy(getptr(packet));
    print(copy.memcmp(getptr(packet)) == 0);   // 1

    // Bit flags across byte boundaries.
    [byte, 2] flags;
    flags.memset(0);
    flags.write_bit(15, 1);
    print(flags.read_bit(15));         // 1
    print(flags.count_set_bits());     // 1

    return 0;
}
```

## 37.24 Tuple Values

Named and unnamed tuple declarations, assignment across different names, and a function round-trip:

```franko
import "franko/io"

func swap((int32 x, int32 y) pair) -> (int32 left, int32 right) {
    return (int32 left, int32 right) {
        left = pair.y,
        right = pair.x
    }
}

func main() -> int32 {
    (int32 val, int32 err) res = (int32 val, int32 err) { val = 1, err = 2 }
    (int32, int32) lazy = (int32, int32) { _0 = 3, _1 = 4 }

    (uint hello, int world) a = (uint hello, int world) { hello = 5, world = 6 }
    (uint different, int name) b = a
    b.different = b.different + 1

    (int32 low, int32 high) swapped = swap(res)

    print(res.val)                 // 1
    print(lazy._1)                 // 4
    print(b.different)             // 6
    print(b.name)                  // 6
    print(swapped.low)             // 2
    print(swapped.high)            // 1
    print(#is_same_type(res, lazy))   // 1
    print(#what_type(res))            // (int32,int32)

    return 0
}
```

## 37.25 Aggregate Literals

Struct literals, tuple literals, nested literals, and field-wise declaration
initialization:

```franko
import "franko/io"

struct Literal {
    int32 x
    int32 def = 2
}

struct Inner {
    int32 value = 7
}

struct Outer {
    Inner inner
    int32 ok = 1
}

func main() -> int32 {
    Literal a = Literal { def = 1, x = 0 }
    Literal b { def = 3, x = 2 }
    Literal c = Literal {}

    (int32, int32) pair = (int32, int32) { _1 = 2, _0 = 1 }
    (int32, int32) positional = (int32, int32) { 5, 6 }
    (int32, int32) partial { 7 }
    (int32 left, int32 right) named = (int32 left, int32 right) {
        left = 3,
        right = 4
    }

    Outer outer = Outer { inner = Inner { value = 42 }, ok = 2 }

    print(a.x + a.def)                    // 1
    print(b.x + b.def)                    // 5
    print(c.def)                          // 2
    print(pair._0 + pair._1)              // 3
    print(positional._0 + positional._1)  // 11
    print(partial._0)                     // 7
    print(named.left + named.right)       // 7
    print(outer.inner.value + outer.ok)   // 44

    return 0
}
```

See [Aggregate Literals](29-aggregate-literals.md) for the complete rules,
including the all-named/all-unnamed tuple spelling rule and the
`OPERATOR_MOV` distinction.
