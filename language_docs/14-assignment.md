# 14. Assignment

Assignment syntax:

```franko
target = value;
target op= value;   // compound: += -= *= /= %=
```

The left-hand side must be a storage-backed lvalue.

Valid assignment targets include:

- variables;
- array elements;
- dereferenced pointers;
- struct field accesses (`obj.field`).

## 14.1 Primitive Assignment

A primitive integer variable may be assigned:

1. a nonconstant expression of exactly the same primitive type; or
2. a constant expression that fits the target type.

Moving between integer widths is never implicit: use the explicit
`cast<T>(expr)` intrinsic (see [Casts](16-casts.md)).

## 14.2 Pointer Assignment

Pointer assignment requires exact pointer type equality.

Raw integers cannot be converted to pointers.

## 14.3 Array Assignment

Arrays cannot be directly assigned as values.

Invalid:

```franko
[int32] a;
[int32] b;

a = b;
```

Array initializer assignment is not value assignment. It is element-assignment sugar:

```franko
a = [1, 2, 3];
```

lowers to:

```franko
a[0] = 1;
a[1] = 2;
a[2] = 3;
```

## 14.4 Struct Assignment

A struct may be assigned as a whole value if and only if all of its fields are assignable, recursively: assignable = primitive | pointer | struct-with-all-assignable-fields.

Valid:

```franko
struct X {
    int32 n
    ptr<Y> p
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

Structs with non-assignable fields can still be shared through `ptr<X>`: pass or store `getptr(s)`, and access fields through `$(p).field`.

## 14.5 Compound Assignment

Five compound assignment operators exist:

```franko
+= -= *= /= %=
```

Valid:

```franko
x += 1;
arr[i] -= 2;
obj.n *= 3;
$(p) /= 4;
x %= 7;
```

The target follows the same rules as plain assignment: a pointable, non-void storage-backed lvalue.

The value must satisfy the rules of the corresponding binary operator (`+ - * / %`, see [Operators](17-operators.md)) against the target:

- both operands are integers (arrays, pointers, and void are invalid);
- with two nonconstant operands, both sides must have exactly the same integer type;
- a constant right-hand side must fit the target's type;
- a constant zero divisor is rejected for `/=` and `%=`, exactly like `x / 0` and `x % 0`.

`target op= value` means exactly `target = target op value`, except the target expression is evaluated only once:

```franko
arr[f()] += 1;   // f() runs once
```

The backend emits the C++ compound assignment directly, so array indices, field chains, and dereferences in the target are never evaluated twice.

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

## 14.6 Increment and Decrement

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
    print(i)
}
```

Only the postfix statement forms exist. Prefix forms (`++x`) and value uses (`x = y++`, `print(x++)`) are syntax errors: `x++` never produces a value and cannot appear as a call argument. See [Statements](19-statements.md).

See [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md) for field-level assignment targets, and [Structs](25-structs.md) for the full assignability rules.
