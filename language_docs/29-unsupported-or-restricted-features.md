# 29. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

## 29.1 Function Restrictions

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

## 29.2 No General Methods

Only the built-in array intrinsics use member-call syntax. All other member calls are rejected, including:

```franko
x.print();          // unsupported member call: print is a statement keyword
x.something(1);     // unsupported member call
```

## 29.3 Struct Restrictions

Structs are supported as user-defined aggregate types with named fields, subject to these restrictions:

- struct fields cannot use `alloc`;
- struct field initializers must be constant expressions, and only primitive fields can have them (struct, array, and addr fields reject `= constExpr` as a type mismatch);
- a struct may not reach itself by value through struct fields or static array elements; `addr<Node>` fields and `array<Node>` (dynamic array) fields break the cycle and are allowed;
- a struct is assignable as a whole value (assignment, by-value parameter, by-value return) only if all of its fields are assignable, recursively: assignable = primitive | address | struct-with-all-assignable-fields. Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing any of them is address-only: it can be used only through `addr<X>`;
- structs have no methods: only the built-in array intrinsics use member-call syntax, and method-like calls such as `s.foo()` are rejected.

The complete struct reference, including the reasoning behind each restriction, is in [Structs](24-structs.md).

## 29.4 No Floating-Point Types

Only integer primitives are supported.

## 29.5 No Separate Boolean Type

Boolean results use `uint8_t`.

## 29.6 No Implicit Numeric Conversions for Nonconstants

Nonconstant numeric assignments require exact type equality.

## 29.7 Arrays Are Not Assignable as Values

Arrays cannot be assigned directly as ordinary values.

Array initializer assignment is an exception only in syntax shape, not in value semantics.

## 29.8 Array Initializer Lists Are Not General Values

Array initializer lists are not first-class values.

Invalid:

```franko
[1, 2, 3];
print([1, 2, 3]);
foo([1, 2, 3]);
return [1, 2, 3];
```

## 29.9 No Full Dynamic Array Lifetime Checking

The checker validates intrinsic legality, but it does not fully prove dynamic array lifetime and bounds properties.

## 29.10 Delete Checking Is Limited

Delete checking is symbol-based, not alias-aware, and branch-sensitive only up to a nesting-depth limit (8); beyond that depth the analysis falls back to a conservative path-insensitive merge. See [Memory Safety Checking](26-memory-safety-checking.md) for the full list of rules and limitations.

## 29.11 Address Lifetime Is Not Fully Tracked

The compiler does not fully track dangling addresses or ownership.

## 29.12 Print Checking Is Loose

The checker validates print arguments as expressions but does not enforce a strict printable-type set. The backend can print integer primitives and addresses; printing other types (for example arrays) does not produce compilable C++. See [Statements](18-statements.md).

Array initializer lists and string literals are not expressions and therefore cannot be printed directly.

## 29.13 Unsupported Operators

The following operators do not exist in the grammar; source code using them fails to parse:

```franko
%   // no modulus operator
~x  // no bitwise NOT operator
+x  // no unary plus operator
```

## 29.14 String Literals Are Initializer-Only

String literals are supported as byte-array initializer sugar (see [String Literals](22-string-literals.md)), but they are not general expression values.

Invalid:

```franko
print("hello");
foo("hello");
return "hello";
```

## 29.15 No `break` or `continue`

Only `if`, `while`, and `for` control flow is implemented. There is no `break` or `continue` statement, so a `for` loop cannot be exited early and its `update` always runs at the end of every body iteration. There is also no `switch`, no `do`/`do-while`, and no `goto`.

## 29.16 Global Scope Is Declaration-Only

Only struct declaration nodes, variable declaration nodes, and function declaration nodes are valid at global scope.

Executable statements are invalid at global scope.

## 29.17 No Casts, Compound Assignment, Increments, Ternary, or Multiple Declarators

The following forms do not exist in the grammar:

- cast syntax (there is no explicit conversion operator; fluid integer constants are the only cross-width mechanism);
- compound assignment operators (`+=`, `-=`, `*=`, `/=`, and similar) — only plain `=` exists;
- increment and decrement operators (`++`, `--`);
- the ternary conditional operator (`?:`);
- multiple declarators in one declaration (`int a, b;` is invalid; declare each variable separately).