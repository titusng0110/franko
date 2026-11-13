# 16. Assignment

Assignment syntax:

```franko
target = value;
target op= value;   // compound: += -= *= /= %= &= |= ^=
```

The left-hand side must be a storage-backed lvalue.

Valid assignment targets include:

- variables;
- array elements;
- dereferenced pointers;
- struct field accesses (`obj.field`).

## 16.0 Const Storage

A const variable cannot be assigned after its declaration:

```franko
const int32 x = 1
x = 2          // error: Cannot assign to const variable 'x'
x += 1         // error
x++            // error: equivalent to x += 1
```

The restriction follows the storage root. `p.field = 1`, `p[i] = 1`, and
`p = v` are all rejected when `p` is const, but `$(p) = v` and
`$(p).field = v` write the pointee and are allowed when `p` is a const
pointer. `getptr(constValue)` and `getmview(constValue)` are rejected;
`getview(constValue)` is allowed.

Const storage must be initialized by its declaration:

```franko
const Point p = Point { x = 10, y = 20 }
const Point q { x = 30 }
const (int32, int32) pair = (int32, int32) { 1, 2 }
```

Plain and field-wise declaration initialization both initialize the const
target; a later assignment is always an error. See
[Declarations](18-declarations.md#188-const-storage).

## 16.1 Primitive Assignment

A primitive integer variable may be assigned:

1. a nonconstant expression of exactly the same primitive type; or
2. a constant expression that fits the target type.

Moving between integer widths is never implicit: use the explicit
`cast<T>(expr)` intrinsic (see [Casts](12-casts.md)).

## 16.2 Pointer Assignment

Pointer assignment accepts an exactly matching pointer type, or one of
the implicit pointer conversions:

- a permission downgrade over an identical referenced type
  (`ptr<T>` to `mview<T>`/`view<T>`, `mview<T>` to `view<T>`);
- the outermost static-to-dynamic array view decay
  (`mview<[T, N]>`/`view<[T, N]>` to `mview<[T]>`/`view<[T]>`, optionally
  combined with a downgrade from `ptr<[T, N]>` to `mview<[T]>`/`view<[T]>`).

Casting upward or across different pointee types is invalid. Raw
integers cannot be converted to pointers. The `null` literal is valid for
any pointer target. See [Pointers](26-pointers.md) and
[Casts](12-casts.md).

## 16.3 Array Assignment

Arrays cannot be directly assigned as values, and an array literal
cannot initialize or assign an array: a literal is a read-only
`view<[T, N]>` value, not an array value.

Invalid:

```franko
[int32] a;
[int32] b;

a = b;              // arrays cannot be directly assigned
a = [1, 2, 3];      // an array literal is a view, not an array value
```

Copy elements instead:

```franko
[int32, 3] xs;
xs.memcpy([1, 2, 3]);        // copies min(3, 3) elements

[int32] ys;
ys.init(3);
ys.memcpy(getptr(xs));       // dynamic target is initialized first
```

See [Array Literals](22-array-literals.md) for literal-source rules.

## 16.4 Struct Assignment

A struct may be assigned as a whole value if and only if all of its fields are assignable, recursively: assignable = primitive | pointer | struct-with-all-assignable-fields. Tuple assignment follows the same recursive rule; see [Tuple Assignment](29-tuples.md#286-assignment).

A struct or tuple value can be constructed directly on the right-hand side with an aggregate literal. The literal creates a temporary value, which is then assigned by the built-in whole-value rules; the constructed type must be value-assignable, so no custom `OPERATOR_MOV` exists for it. See [Aggregate Literals](30-aggregate-literals.md).

Valid:

```franko
struct X {
    int32 n
    view<X> next
}

X a;
X b;
a = b;    // whole-value assignment
```

Invalid (struct contains a static array field):

```franko
struct W {
    [int32, 4] xs
}

W a;
W b;
a = b;    // invalid: struct with non-assignable fields is pointer-only
```

Structs with non-assignable fields can still be shared through a pointer
(for example `ptr<X>` with `getptr(s)`, or `mview`/`view` when only
read/write access is needed), and fields are accessed through
`$(p).field`.

An enum is assigned as a whole value: both sides must have the exact
same enum type. An enum cannot be assigned to or from an integer or
from a different enum type; convert explicitly with `cast` or
`unsafe_cast` (see [Enums](31-enums.md)).

## 16.5 Compound Assignment

Eight compound assignment operators exist:

```franko
+= -= *= /= %= &= |= ^=
```

Valid:

```franko
x += 1;
arr[i] -= 2;
obj.n *= 3;
$(p) /= 4;
x %= 7;
flags &= 0xFF;
mask |= 1 << 3;
bits ^= 0x0F;
```

The target follows the same rules as plain assignment: a non-void storage-backed lvalue.

The value must satisfy the rules of the corresponding binary operator (`+ - * / % & | ^`, see [Operators](13-operators.md)) against the target:

- both operands are integers (arrays, pointers, and void are invalid);
- with two nonconstant operands, both sides must have exactly the same integer type;
- a constant right-hand side must fit the target's type;
- a constant zero divisor is rejected for `/=` and `%=`, exactly like `x / 0` and `x % 0`.

`target op= value` means exactly `target = target op value`, except the target expression is evaluated only once:

```franko
arr[f()] += 1;   // f() runs once
```

The target expression is evaluated once; array indices, field chains, and dereferences in the target are never evaluated twice.

Invalid:

```franko
[int32, 3] xs = [1, 2, 3];
xs += 1;         // arrays are not arithmetic values

int32 v = 1;
ptr<int32> p = getptr(v);
p += 1;          // pointers are not arithmetic values

int32 x = 1;
int64 y = 2;
x += y;          // operands must have the same integer type
```

The right-hand side may not be an array literal (`xs += [1, 2]` is rejected: operands must be integers). See [Array Literals](22-array-literals.md).

## 16.6 Increment and Decrement

Postfix increment and decrement are statements:

```franko
x++;
x--;
```

They are exactly equivalent to:

```franko
x += 1;
x -= 1;
```

so every compound-assignment rule above applies unchanged: the target must be an integer, non-void storage-backed lvalue (`xs++` and `p++` are invalid for the same reason `xs += 1` and `p += 1` are invalid).

```franko
for (int32 i = 0; i < n; i++) {
    stdout(i)
}
```

Only the postfix statement forms exist. Prefix forms (`++x`) and value uses (`x = y++`, `stdout(x++)`) are syntax errors: `x++` never produces a value and cannot appear as a call argument. See [Statements](17-statements.md).

See [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md) for field-level assignment targets, and [Structs](27-structs.md) for the full assignability rules.

## 16.7 Operator Assignment

A struct may customize assignment by declaring operator methods:

| Syntax | Method |
|---|---|
| `=` | `OPERATOR_MOV` |
| `+=` | `OPERATOR_ADDMOV` |
| `-=` | `OPERATOR_SUBMOV` |
| `*=` | `OPERATOR_MULMOV` |
| `/=` | `OPERATOR_DIVMOV` |
| `%=` | `OPERATOR_MODMOV` |
| `\|=` | `OPERATOR_ORMOV` |
| `&=` | `OPERATOR_ANDMOV` |
| `^=` | `OPERATOR_XORMOV` |

Each assignment operator method has the shape:

```franko
func OPERATOR_MOV(ptr<X> self, view<T> other) -> void
```

The assignment target must still be a storage-backed lvalue of the struct
type `X`. `T` may be any non-void value type, including a nested view
type, and multiple `view<T>` overloads are allowed.

Plain `=` first tries to resolve an applicable `OPERATOR_MOV` overload.
If none applies, it falls back to the built-in whole-struct assignment
rules described in [Struct Assignment](#164-struct-assignment). Compound
assignment has no built-in struct fallback: if no overload applies, the
assignment is rejected.

A value-assignable struct cannot declare `OPERATOR_MOV`; its whole-value
assignment is always the built-in field copy. A pointer-only struct can opt
into whole-value assignment with `OPERATOR_MOV` even though built-in
assignment refuses it:

```franko
struct Blob {
    [int32, 4] xs

    func OPERATOR_MOV(ptr<Blob> self, view<Blob> other) -> void {
        $(self).xs.memcpy(getview($(other).xs))
        return
    }
}

Blob a
Blob b
b = a          // calls b.OPERATOR_MOV(getview(a))
```

The right-hand side is always borrowed as `view<T>` where `T` is its
static type. An lvalue is passed with `getview`; an rvalue is borrowed for
the duration of the full expression. A view value needs a `view<view<...>>`
parameter:

```franko
OPERATOR_MOV(ptr<X> self, view<[byte]> other)        // x = arr
OPERATOR_MOV(ptr<X> self, view<view<[byte]>> other)  // x = "hello"
```

Integer constants are fluid-fitted to `view<primitive>` parameters and
materialized as a temporary of the referenced primitive type.

The borrowed view is valid only for the duration of the operator call.
An operator method must not store or forward it into caller-owned or
global storage; the compiler rejects that pattern (see
[Memory Safety Checking](32-memory-safety-checking.md#299-dangling-pointer-checking)).
A local `view<T>` copy inside the operator body is allowed because the
local dies before the operator call returns.

See [Operators](13-operators.md#1310-struct-operator-overloading) for the
complete operator overload rules.
