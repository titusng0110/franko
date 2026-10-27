# 30. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

## 30.1 Function Restrictions

- function declarations are valid only in global scope;
- nested functions are not supported;
- first-class function values are not supported;
- function pointers are not supported;
- anonymous functions/lambdas are not supported;
- parameter names are not used in overload resolution;
- return type is not used in overload resolution;
- arrays cannot be returned by value;
- arrays cannot be passed as parameters by value; use ptr<array<...>> parameters (see [Functions](12-functions.md));
- `void` calls cannot be used in value-producing contexts;
- full path-sensitive return analysis is not currently performed.

## 30.2 No General Member Calls

Struct methods and the built-in array intrinsics use member-call syntax (see [Methods](26-methods.md) and [Array Intrinsics](21-array-intrinsics.md)). All other member calls are rejected, including:

```franko
x.print();          // unsupported member call: print is a statement keyword
x.something(1);     // unsupported member call: unknown method
```

## 30.3 Struct Restrictions

Structs are supported as user-defined aggregate types with named fields, subject to these restrictions:

- struct field initializers must be constant expressions, and only primitive fields can have them (struct, array, and ptr fields reject `= constExpr` as a type mismatch);
- a struct may not reach itself by value through struct fields or static array elements; `ptr<Node>` fields and `array<Node>` (dynamic array) fields break the cycle and are allowed;
- a struct is assignable as a whole value (assignment, by-value parameter, by-value return) only if all of its fields are assignable, recursively: assignable = primitive | pointer | struct-with-all-assignable-fields. Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing any of them is pointer-only: it can be used only through `ptr<X>`;
- structs have methods with explicit `ptr<X> self` receivers (see [Methods](26-methods.md)); general member calls beyond struct methods and array intrinsics, such as `s.foo()` for an unknown `foo`, are rejected.

The complete struct reference, including the reasoning behind each restriction, is in [Structs](25-structs.md).

## 30.4 No Floating-Point Types

Only integer primitives are supported.

## 30.5 No Separate Boolean Type

Boolean results use `uint8_t`.

## 30.6 No Implicit Numeric Conversions for Nonconstants

Nonconstant numeric expressions require exact type equality wherever a
typed value is expected. Moving between integer widths is always
explicit through the `cast<T>(expr)` intrinsic (see [Casts](16-casts.md)).

## 30.7 Arrays Are Not Assignable as Values

Arrays cannot be assigned directly as ordinary values.

Array initializer assignment is an exception only in syntax shape, not in value semantics.

## 30.8 Array Initializer Lists Are Not General Values

Array initializer lists are not first-class values.

Invalid:

```franko
[1, 2, 3];
print([1, 2, 3]);
foo([1, 2, 3]);
return [1, 2, 3];
```

## 30.9 No Full Dynamic Array Lifetime Checking

The checker validates intrinsic legality, but it does not fully prove dynamic array lifetime and bounds properties.

## 30.10 Pointer Lifetime Is Not Fully Tracked

The compiler does not fully track dangling pointers or ownership.

## 30.11 Print Checking Is Loose

The checker validates print arguments as expressions but does not enforce a strict printable-type set. Only integer primitives and pointers (including `null`) are actually printable. Arrays and structs cannot be printed: the checker accepts them, but the generated C++ does not compile because the backend has no output form for them. See [Statements](19-statements.md).

Array initializer lists and string literals are not expressions and therefore cannot be printed directly.

## 30.12 Unsupported Operators

The following operators do not exist in the language. The lexer rejects the character `~` outright ("is not a recognizable token"), and a unary `+` fails to parse:

```franko
~x  // no bitwise NOT operator
+x  // no unary plus operator
```

Bitwise and shift compound assignments also do not exist (`&=`, `|=`, `^=`, `<<=`, `>>=`): assignment has only plain `=` plus the five arithmetic compound forms `+=`, `-=`, `*=`, `/=`, `%=` (see [Assignment](14-assignment.md)).

## 30.13 String Literals Are Initializer-Only

String literals are supported as byte-array initializer sugar (see [String Literals](23-string-literals.md)), but they are not general expression values.

Invalid:

```franko
print("hello");
foo("hello");
return "hello";
```

## 30.14 No `switch` or `do`/`do-while`

Only `if`, `while`, `for`, `break`, and `continue` control flow is implemented. There is no `switch`, no `do`/`do-while`, and no user-visible `goto` (the generated C++ uses `goto` internally to lower `break` and `continue`; see [Statements](19-statements.md)).

## 30.15 Global Scope Is Declaration-Only

Only struct declaration nodes, variable declaration nodes, and function declaration nodes are valid at global scope.

Executable statements are invalid at global scope.

## 30.16 No C-Style Casts, Ternary, or Multiple Declarators

The following forms do not exist in the grammar:

- C-style cast syntax (`(T)x` does not exist; explicit conversions use the `cast<T>(expr)` intrinsic, and fluid integer constants still convert implicitly when they fit);
- the ternary conditional operator (`?:`);
- multiple declarators in one declaration (`int a, b;` is invalid; declare each variable separately).