# 26. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

## 26.1 Function Restrictions

- function declarations are valid only in global scope;
- nested functions are not supported;
- first-class function values are not supported;
- function pointers are not supported;
- anonymous functions/lambdas are not supported;
- methods are not supported;
- parameter names are not used in overload resolution;
- return type is not used in overload resolution;
- arrays cannot be returned by value;
- `void` calls cannot be used in value-producing contexts;
- full path-sensitive return analysis is not currently performed.

## 26.2 No General Methods

Only array intrinsic member calls are recognized.

## 26.3 No Structs or Fields

There are no user-defined aggregate types beyond arrays.

## 26.4 No Floating-Point Types

Only integer primitives are supported.

## 26.5 No Separate Boolean Type

Boolean results use `uint8_t`.

## 26.6 No Implicit Numeric Conversions for Nonconstants

Nonconstant numeric assignments require exact type equality.

## 26.7 Arrays Are Not Assignable as Values

Arrays cannot be assigned directly as ordinary values.

Array initializer assignment is an exception only in syntax shape, not in value semantics.

## 26.8 Array Initializer Lists Are Not General Values

Array initializer lists are not first-class values.

Invalid:

```franko
[1, 2, 3];
print([1, 2, 3]);
foo([1, 2, 3]);
return [1, 2, 3];
```

## 26.9 No Full Array Lifetime Checking

The checker validates intrinsic legality, but it does not fully prove dynamic array lifetime and bounds properties.

## 26.10 Delete Checking Is Limited

Delete checking is symbol-based, path-insensitive, and not alias-aware.

## 26.11 Address Lifetime Is Not Fully Tracked

The compiler does not fully track dangling addresses or ownership.

## 26.12 Print Checking Is Loose

The checker validates print arguments as expressions but does not enforce a strict printable-type set.

Array initializer lists are not expressions and therefore cannot be printed directly.

## 26.13 Unsupported Operators

The following are not currently implemented:

```franko
%   // modulus is not implemented
~x  // bitwise NOT is not implemented
+x  // unary plus is not implemented as a semantic unary operator
```

## 26.14 No String Literals Yet

String literals are not currently implemented.

Invalid:

```franko
print("hello");
```

Invalid:

```franko
array<uint8_t, 5> hello;
hello = "hello";
```

## 26.15 Global Scope Is Declaration-Only

Only variable declaration nodes and function declaration nodes are valid at global scope.

Executable statements are invalid at global scope.
