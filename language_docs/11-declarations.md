# 11. Declarations

A variable declaration introduces a named storage location.

Basic declaration syntax:

```franko
int x;
int32_t y;
uint8_t b;
uint32_t i;
```

Array and address declarations:

```franko
array<int> xs;
array<int32_t> ys;
array<uint8_t, 128> buffer;
ndarray<byte, 12, 20> grid;   // sugar for array<array<byte, 20>, 12>

addr<int> p;
addr<int32_t> q;
addr<array<int>> arrayPtr;
```

Heap declarations use `alloc`:

```franko
alloc int32_t h;
alloc array<int32_t> hs;
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

Variable declarations are allowed at global scope.

Declaration initializer sugar is invalid at global scope because it desugars into executable statements (an assignment, an expression-statement call, or indexed element assignments), and executable statements are not allowed at global scope.

The `alloc` modifier is not valid at global scope; global variables are always statically allocated.

## 11.5 Uninitialized Values

A declaration without an initializer default-initializes the variable:

- primitive integer variables start with an indeterminate value; reading one before its first assignment is undefined behavior in the generated C++;
- static array elements start with indeterminate values;
- dynamic arrays start empty (length 0, no element storage) and must be initialized with `init` or `init_zero` before indexed use;
- heap variables (`alloc`) are allocated and default-constructed, so they follow the same rules as above.

There is no implicit zero-initialization. Use `init_zero` for zeroed dynamic array storage.