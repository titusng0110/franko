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

addr<int> p;
addr<int32_t> q;
addr<array<int>> arrayPtr;
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

## 11.1 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

## 11.2 Shadowing

A variable may be redeclared in an inner block, creating a new variable that shadows the outer one.

## 11.3 Use Before Declaration

Local variables must be declared before they are used.

## 11.4 Global Variable Declarations

Variable declarations are allowed at global scope. Initializer sugar at global scope is currently invalid if it desugars into executable assignment statements.
