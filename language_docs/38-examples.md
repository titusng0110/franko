# 38. Examples

Most executable examples should be placed inside a function body, typically `main`, because global scope allows only declarations.

Examples that print assume `import "franko/stdio"` at the top of the file.

## 38.1 Basic Integers

```franko
func main() -> int32 {
    int32 x;
    int32 y;

    x = 10;
    y = x + 5;

    stdout(y);

    return 0;
}
```

## 38.2 Static Array Copy From a Literal

An array literal is a `view<[T, N]>` value, so arrays receive its
elements through `memcpy` (see [Array Literals](22-array-literals.md)):

```franko
func main() -> int32 {
    [int32, 3] xs;

    xs.memcpy([1, 2, 3]);

    stdout(xs[0]);
    stdout(xs[1]);
    stdout(xs[2]);

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

    stdout(xs[0]);
    stdout(xs[1]);
    stdout(xs[2]);

    return 0;
}
```

## 38.3 Static Partial Copy From a Literal

```franko
func main() -> int32 {
    [int32, 3] xs;

    xs.memset(0);
    xs.memcpy([1, 2]);

    return 0;
}
```

`xs[2]` is untouched: memcpy copies `min(source, target)` elements.

## 38.4 Dynamic Array Copy From a Literal

```franko
func main() -> int32 {
    [int32] xs;

    xs.init(3);

    xs.memcpy([10, 20, 30]);

    stdout(xs[0]);
    stdout(xs[1]);
    stdout(xs[2]);

    xs.uninit();

    return 0;
}
```

## 38.5 Constant Expressions Inside Array Literals

```franko
func main() -> int32 {
    view<[int32, 4]> v = [1, 5, 10, 1 + 2];

    stdout($(v)[0]);
    stdout($(v)[1]);
    stdout($(v)[2]);
    stdout($(v)[3]);

    return 0;
}
```

Elements are compile-time constant expressions; runtime values are
rejected because a literal is backed by immutable constant storage.

## 38.6 Nested Static Array Literals

```franko
func main() -> int32 {
    [[int32, 2], 2] matrix;

    matrix.memcpy([
        [1, 2],
        [3, 4]
    ]);

    stdout(matrix[0][0]);
    stdout(matrix[1][1]);

    return 0;
}
```

Nested literals are rectangular and static at every level.

## 38.7 Nested Dynamic Arrays

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

## 38.8 Declaration Initializer With an Array Literal

A `view` declaration accepts a literal directly; its element type and
length are inferred from the literal and checked against the declared
type:

```franko
func main() -> int32 {
    view<[int32, 3]> v = [1, 2, 3];
    view<[int32, ?]> w = [4, 5, 6, 7];

    stdout($(v)[0]);
    stdout($(w).length());    // 4

    return 0;
}
```

## 38.9 Dynamic View Decay From a Literal

A literal decays to `view<[T]>` wherever a dynamic view is
expected:

```franko
func take(view<[int32]> v) -> int32 {
    return $(v)[0];
}

func main() -> int32 {
    view<[int32]> d = [1, 2, 3];

    stdout($(d)[2]);
    stdout(take([4, 5, 6]));

    return 0;
}
```

## 38.10 Array Copy Through Pointer

```franko
func main() -> int32 {
    [int32, 3] xs;
    ptr<[int32, 3]> p;

    p = getptr(xs);

    $(p).memcpy([1, 2, 3]);

    stdout(xs[0]);
    stdout(xs[1]);
    stdout(xs[2]);

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

    stdout(xs[0]);
    stdout(xs[1]);
    stdout(xs[2]);

    return 0;
}
```

## 38.11 String Literal Bytes

```franko
func main() -> int32 {
    [byte] str;     // dynamic array: str.init(200) and str.uninit() require one

    str.init(200);
    str.memset(0);
    str.memcpy("Hello,\tWorld!\n\"Franko\"");   // 22 bytes copied in

    for (uint i = 0; i < 22; i++) {
        stdout(str[i]);
    }

    str.uninit();
    return 0;
}
```

A string literal materializes as a `view<[byte, N]>` when passed to
`memcpy`; byte buffers receive its content through that intrinsic. See
[String Literals](24-string-literals.md).

## 38.12 Nested Static Array Grid

```franko
func fill(ptr<[[byte, 3], 2]> grid) -> void {
    $(grid).memset(0);
    $(grid)[0][1] = 7;
}

func main() -> int32 {
    [[byte, 3], 2] grid;

    fill(getptr(grid));

    stdout(grid[0][1]);
    stdout(grid[1][2]);

    return 0;
}
```

`[[byte, 3], 2]` is a 2 by 3 grid: `grid[r][c]` indexes `r` along the outer size (`2`) and `c` along the inner size (`3`).

## 38.13 Status-Returning Intrinsics

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

    stdout(status);

    xs.uninit();
    return 0;
}
```

## 38.14 Struct Declaration and Field Access

```franko
struct Point {
    int32 x;
    int32 y
}

func main() -> int32 {
    Point p = Point { x = 10, y = 20 };

    stdout(p.x);
    stdout(p.y);

    return 0;
}
```

The separator before `}` is optional.

A field-wise declaration initializer is the direct alternative:

```franko
func main() -> int32 {
    Point p { x = 10, y = 20 };

    stdout(p.x);
    stdout(p.y);

    return 0;
}
```

## 38.15 Struct Field Defaults

Field defaults are constant expressions; only primitive fields can have them.

```franko
struct Counter {
    int32 count = 0;
    uint32 limit = 100
}

func main() -> int32 {
    Counter c;

    stdout(c.count);   // 0
    stdout(c.limit);   // 100

    return 0;
}
```

## 38.16 Whole-Struct Assignment

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

    stdout(q.a);       // 1
    stdout(q.b);       // 2

    return 0;
}
```

## 38.17 Structs Passed by Value and by Pointer

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

    stdout(dist2(p));   // 25

    move(getptr(p));

    stdout(p.x);        // 4
    stdout(p.y);        // 5

    return 0;
}
```

## 38.18 Structs with Array Fields

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

    stdout(b.data[0]);
    stdout(b.size);

    b.data.uninit();
    return 0;
}
```

Dynamic array fields are not allocated as part of the struct declaration; allocate them explicitly with `field.init(n)` as above.

## 38.19 Struct Globals

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

    stdout(origin.x);   // 2
    stdout(scale);      // 2

    return 0;
}
```

Global declarations are checked before `main` runs; executable statements remain invalid at global scope.

## 38.20 Array `length()`

```franko
func main() -> int32 {
    [int32] xs;
    xs.init(4);
    stdout(xs.length());   // 4

    [int32, 3] ys;
    ys.memcpy([10, 20, 30]);
    stdout(ys.length());   // 3 (compile-time constant)

    for (uint32 i = 0; i < ys.length(); i = i + 1) {
        stdout(ys[i]);     // 10 20 30
    }

    xs.uninit();
    stdout(xs.length());   // 0
    return 0;
}
```

## 38.21 Inferred Static Array Sizes (`?`)

```franko
func main() -> int32 {
    view<[int32, ?]> a = [0, 1, 2, 3];
    stdout($(a).length());   // 4

    view<[byte, ?]> s = [104, 101, 108, 108, 111];
    stdout($(s).length());   // 5

    view<[[int32, ?], ?]> m = [[1, 2], [3, 4]];
    stdout($(m).length());      // 2
    stdout($(m)[0].length());   // 2

    return 0;
}
```

## 38.22 Command-Line Arguments

```franko
func main(view<[[byte]]> args) -> int32 {
    uint32 n = $(args).length();
    stdout(n - 1);   // user argument count (args[0] is the program name)

    for (uint32 i = 1; i < n; i++) {
        stdout($(args)[i].length());   // byte length of argument i
    }

    return 0;
}
```

Running the binary with two arguments prints `2`, then each argument's length. Each argument arrives as its raw bytes with no NUL terminator, and the args parameter always receives a live view, so it needs no null guard.

## 38.23 Byte-Array Search, Endian, and Bit Intrinsics

```franko
import "franko/stdio"

func main() -> int32 {
    [byte, 8] packet;
    packet.memset(0);

    // Big-endian header: 0xCAFEBABE, then a little-endian length.
    packet.write_uint32_BE(0, 0xCAFEBABE);
    packet.write_uint16_LE(4, 300);
    stdout(packet.read_uint32_BE(0));   // 3405691582
    stdout(packet.read_uint16_LE(4));   // 300

    // Search the payload with memchr / memmem.
    packet[6] = 65;
    packet[7] = 66;
    stdout(packet.memchr(65));          // 6
    stdout(packet.memmem("AB"));        // 6
    stdout(packet.memchr(67));          // 4294967295 (not found)

    // Compare whole byte arrays.
    [byte, 8] copy;
    copy.memset(0);
    copy.memcpy(getptr(packet));
    stdout(copy.memcmp(getptr(packet)) == 0);   // 1

    // Bit flags across byte boundaries.
    [byte, 2] flags;
    flags.memset(0);
    flags.write_bit(15, 1);
    stdout(flags.read_bit(15));         // 1
    stdout(flags.count_set_bits());     // 1

    return 0;
}
```

## 38.24 Tuple Values

Named and unnamed tuple declarations, assignment across different names, and a function round-trip:

```franko
import "franko/stdio"

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

    stdout(res.val)                 // 1
    stdout(lazy._1)                 // 4
    stdout(b.different)             // 6
    stdout(b.name)                  // 6
    stdout(swapped.low)             // 2
    stdout(swapped.high)            // 1
    stdout(#is_same_type(res, lazy))   // 1
    stdout(#what_type(res))            // (int32,int32)

    return 0
}
```

## 38.25 Aggregate Literals

Struct literals, tuple literals, nested literals, and field-wise declaration
initialization:

```franko
import "franko/stdio"

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

    stdout(a.x + a.def)                    // 1
    stdout(b.x + b.def)                    // 5
    stdout(c.def)                          // 2
    stdout(pair._0 + pair._1)              // 3
    stdout(positional._0 + positional._1)  // 11
    stdout(partial._0)                     // 7
    stdout(named.left + named.right)       // 7
    stdout(outer.inner.value + outer.ok)   // 44

    return 0
}
```

See [Aggregate Literals](30-aggregate-literals.md) for the complete rules,
including the all-named/all-unnamed tuple spelling rule and the
`OPERATOR_MOV` distinction.

## 38.26 Const Storage

```franko
import "franko/stdio"

struct Point {
    int32 x = 0
    int32 y = 0

    func sum(view<Point> self) -> int32 {
        return $(self).x + $(self).y
    }
}

const int32 MAX_POINTS = 10
const Point ORIGIN

func sum(const int32 a, const int32 b) -> int32 {
    return a + b
}

func main() -> int32 {
    const int32 width = 3
    const Point p = Point { x = 4, y = 5 }
    const Point q { x = 6 }
    const (int32, int32) pair = (int32, int32) { 1, 2 }

    int32 value = 7
    const ptr<int32> link = getptr(value)
    $(link) = 8                     // pointee write is allowed

    view<Point> v = getview(p)      // const storage: getview only

    stdout(width)                    // 3
    stdout(p.sum())                  // 9
    stdout(q.x)                      // 6
    stdout(pair._1)                  // 2
    stdout($(v).y)                   // 5
    stdout(value)                    // 8
    stdout(MAX_POINTS)               // 10
    stdout(ORIGIN.x)                 // 0
    stdout(sum(1, 2))                // 3
    return 0
}
```

See [Declarations](18-declarations.md#188-const-storage).

## 38.27 Generic Structs and Functions

```franko
import "franko/stdio"

#generic struct Pair<typename A, typename B> {
    A first
    B second
}

#generic func make_pair<typename A, typename B>(A a, B b) -> Pair<A, B> {
    return Pair<A, B> { first = a, second = b }
}

#generic func repeat<int32 N>(int32 value) -> int32 {
    return value * N
}

func main() -> int32 {
    Pair<int32, uint8> p = make_pair<int32, uint8>(10, 20)
    stdout(p.first)              // 10
    stdout(p.second)             // 20
    stdout(repeat<3>(7))         // 21
    return 0
}
```

See [Generics](20-generics.md) for the complete reference.
