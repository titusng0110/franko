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
- arrays cannot be passed as parameters by value; use addr<array<...>> parameters (see [Functions](12-functions.md));
- `void` calls cannot be used in value-producing contexts;
- full path-sensitive return analysis is not currently performed.

## 30.2 No General Member Calls

Struct methods and the built-in array intrinsics use member-call syntax (see [Methods](25-methods.md) and [Array Intrinsics](20-array-intrinsics.md)). All other member calls are rejected, including:

```franko
x.print();          // unsupported member call: print is a statement keyword
x.something(1);     // unsupported member call: unknown method
```

## 30.3 Struct Restrictions

Structs are supported as user-defined aggregate types with named fields, subject to these restrictions:

- struct fields cannot use `alloc`;
- struct field initializers must be constant expressions, and only primitive fields can have them (struct, array, and addr fields reject `= constExpr` as a type mismatch);
- a struct may not reach itself by value through struct fields or static array elements; `addr<Node>` fields and `array<Node>` (dynamic array) fields break the cycle and are allowed;
- a struct is assignable as a whole value (assignment, by-value parameter, by-value return) only if all of its fields are assignable, recursively: assignable = primitive | address | struct-with-all-assignable-fields. Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing any of them is address-only: it can be used only through `addr<X>`;
- structs have methods with explicit `addr<X> self` receivers (see [Methods](25-methods.md)); general member calls beyond struct methods and array intrinsics, such as `s.foo()` for an unknown `foo`, are rejected.

The complete struct reference, including the reasoning behind each restriction, is in [Structs](24-structs.md).

## 30.4 No Floating-Point Types

Only integer primitives are supported.

## 30.5 No Separate Boolean Type

Boolean results use `uint8_t`.

## 30.6 No Implicit Numeric Conversions for Nonconstants

Nonconstant numeric assignments require exact type equality.

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

## 30.10 Delete Checking Is Limited

Delete checking is symbol-based, not alias-aware, and branch-sensitive only up to a nesting-depth limit (10); beyond that depth the analysis falls back to a conservative path-insensitive merge. See [Memory Safety Checking](27-memory-safety-checking.md) for the full list of rules and limitations.

## 30.11 Address Lifetime Is Not Fully Tracked

The compiler does not fully track dangling addresses or ownership.

## 30.12 Print Checking Is Loose

The checker validates print arguments as expressions but does not enforce a strict printable-type set. Only integer primitives and addresses (including `null`) are actually printable. Arrays and structs cannot be printed: the checker accepts them, but the generated C++ does not compile because the backend has no output form for them. See [Statements](18-statements.md).

Array initializer lists and string literals are not expressions and therefore cannot be printed directly.

## 30.13 Unsupported Operators

The following operators do not exist in the language. The lexer rejects the characters `%` and `~` outright ("is not a recognizable token"), and a unary `+` fails to parse:

```franko
%   // no modulus operator
~x  // no bitwise NOT operator
+x  // no unary plus operator
```

## 30.14 String Literals Are Initializer-Only

String literals are supported as byte-array initializer sugar (see [String Literals](22-string-literals.md)), but they are not general expression values.

Invalid:

```franko
print("hello");
foo("hello");
return "hello";
```

## 30.15 No `break` or `continue`

Only `if`, `while`, and `for` control flow is implemented. There is no `break` or `continue` statement, so a `for` loop cannot be exited early and its `update` always runs at the end of every body iteration. There is also no `switch`, no `do`/`do-while`, and no `goto`.

## 30.16 Global Scope Is Declaration-Only

Only struct declaration nodes, variable declaration nodes, and function declaration nodes are valid at global scope.

Executable statements are invalid at global scope.

## 30.17 No Casts, Compound Assignment, Increments, Ternary, or Multiple Declarators

The following forms do not exist in the grammar:

- cast syntax (there is no explicit conversion operator; fluid integer constants are the only cross-width mechanism);
- compound assignment operators (`+=`, `-=`, `*=`, `/=`, and similar) — only plain `=` exists;
- increment and decrement operators (`++`, `--`);
- the ternary conditional operator (`?:`);
- multiple declarators in one declaration (`int a, b;` is invalid; declare each variable separately).