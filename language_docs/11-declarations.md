# 11. Declarations

A variable declaration introduces a named storage location.

Basic declaration syntax:

```franko
int x;
int32_t y;
uint8_t b;
uint32_t i;
```

Array and pointer declarations:

```franko
array<int> xs;
array<int32_t> ys;
array<uint8_t, 128> buffer;
array<array<byte, 20>, 12> grid;

ptr<int> p;
ptr<int32_t> q;
ptr<array<int>> arrayPtr;
```

Declaration initializers are syntax sugar:

```franko
int32_t x = 1;
```

is equivalent to:

```franko
int32_t x;
x = 1;
```

The initializer must obey normal assignment rules and is only valid where the resulting assignment is legal.

Dynamic array call-init declaration sugar:

```franko
array<int32_t> d(10);
```

is equivalent to:

```franko
array<int32_t> d;
d(10);
```

The call form is only valid for dynamic arrays. Using it on a non-array declaration is an error:

```franko
int32_t x(5);   // invalid: x is not a dynamic array
```

## 11.1 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

## 11.2 Shadowing

A variable may be redeclared in an inner block, creating a new variable that shadows the outer one.

## 11.3 Use Before Declaration

Local variables must be declared before they are used.

## 11.4 Global Variable Declarations

Variable declarations are allowed at global scope. Global variables are statically allocated.

Two different initializer forms exist for declarations, and they follow different rules at global scope.

**Constant initializers.** A global declaration may end with `= constExpr`:

```franko
int32_t g = 1 + 2
uint64_t mask = 1 << 8
```

The initializer must be a constant expression built from literals, so it is folded at compile time and may not reference variables. Only primitive globals accept this form: struct, array, and ptr globals reject `= constExpr` as a type mismatch.

**Static address initializers.** A pointer-typed global may be
initialized with `null`, with `getptr`/`getmview`/`getview` over another
global variable, or with a string literal (which is a view over a
hoisted global; see [String Literals](23-string-literals.md)):

```franko
array<byte, 5> greeting
view<array<byte, 5>> v = getview(greeting)
view<array<byte, 5>> s = "hello"
ptr<array<byte, 5>> p = null
```

The initializer must be a statically representable address of the right
type (a permission downgrade is allowed, an upgrade is not).

An array literal may initialize a pointer-typed global (it is a
`view<array<T, N>>` value over a constant global, like a string
literal):

```franko
view<array<int32_t, 4>> G = [1, 2, 3, 4]   // valid
```

**Ordinary declaration initializer sugar.** The initializer forms described in this chapter and in [Assignment](14-assignment.md) — `= expr` with a nonconstant expression, and the dynamic-array call-init form `xs(n)` — desugar into executable statements (an assignment or an expression-statement call). Executable statements are not allowed at global scope, so these forms are invalid on global declarations:

```franko
int32_t g = h                     // invalid: the initializer must be constant
array<int32_t, 3> xs = [1, 2, 3]  // invalid: arrays are not assignable from literals
array<byte, 6> s = "hello"        // invalid: strings cannot initialize arrays
```

The constant-initializer, static-address, and array-literal forms above
are the only ways to initialize a global at its declaration.

## 11.5 Uninitialized Values

A **local** declaration without an initializer default-initializes the variable:

- primitive integer variables start with an indeterminate value; reading one before its first assignment is undefined behavior in the generated C++;
- static array elements start with indeterminate values;
- dynamic arrays start empty (length 0, no element storage) and must be initialized with `init` or `init_zero` before indexed use;
- **pointer variables are the exception: an `ptr<T>` declaration is guaranteed null-initialized** (see [Pointers](24-pointers.md)).

There is no implicit zero-initialization for locals other than pointers. Use `init_zero` for zeroed dynamic array storage.

**Global** declarations are statically allocated: in the generated C++ they live in static storage, so primitives and static array elements are zero-initialized before `main` runs, and dynamic arrays start empty. Global constant initializers (see Global Variable Declarations above) are applied on top of that.

## 11.6 Struct Fields

Struct fields are declared with the same `type name` syntax as variables, inside a top-level `struct` declaration:

```franko
struct X {
    int32_t num = 0
    array<int32_t, 4> xs
    ptr<Y> next
}
```

Field defaults must be constant expressions on primitive fields only, and duplicate field names are rejected.

The complete struct reference — field rules, by-value cycles, assignability, member access, and globals — is in [Structs](25-structs.md).