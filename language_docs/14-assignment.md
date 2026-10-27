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
- dereferenced addresses;
- struct field accesses (`obj.field`).

## 14.1 Primitive Assignment

A primitive integer variable may be assigned:

1. a nonconstant expression of exactly the same primitive type; or
2. a constant expression that fits the target type.

Moving between integer widths is never implicit: use the explicit
`cast<T>(expr)` intrinsic (see [Casts](16-casts.md)).

## 14.2 Address Assignment

Address assignment requires exact address type equality.

Raw integers cannot be converted to addresses.

## 14.3 Array Assignment

Arrays cannot be directly assigned as values.

Invalid:

```franko
array<int32_t> a;
array<int32_t> b;

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

A struct may be assigned as a whole value if and only if all of its fields are assignable, recursively: assignable = primitive | address | struct-with-all-assignable-fields.

Valid:

```franko
struct X {
    int32_t n
    addr<Y> p
}

X a;
X b;
a = b;    // whole-value assignment
```

Invalid (struct contains a static array field):

```franko
struct W {
    array<int32_t, 4> xs
}

W a;
W b;
a = b;    // invalid: struct with non-assignable fields is address-only
```

Structs with non-assignable fields can still be shared through `addr<X>`: pass or store `getaddr(s)`, and access fields through `$(p).field`.

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

The target follows the same rules as plain assignment: an addressable, non-void storage-backed lvalue.

The value must satisfy the rules of the corresponding binary operator (`+ - * / %`, see [Operators](17-operators.md)) against the target:

- both operands are integers (arrays, addresses, and void are invalid);
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
array<int32_t, 3> xs = [1, 2, 3];
xs += 1;         // arrays are not arithmetic values

int32_t v = 1;
addr<int32_t> p = getaddr(v);
p += 1;          // addresses are not arithmetic values

int32_t x = 1;
int64_t y = 2;
x += y;          // operands must have the same integer type
```

The right-hand side may not be an array initializer list (`xs += [1, 2]` does not parse); initializer lists remain plain-`=` sugar only. See [Array Initializer Lists](22-array-initializer-lists.md).

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
for (int32_t i = 0; i < n; i++) {
    print(i)
}
```

Only the postfix statement forms exist. Prefix forms (`++x`) and value uses (`print(x++)`, `x = y++`) are syntax errors: `x++` never produces a value. See [Statements](19-statements.md).

See [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md) for field-level assignment targets, and [Structs](25-structs.md) for the full assignability rules.
