# 28. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

## 28.1 Function Restrictions

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

## 28.2 No General Methods

Only the built-in array intrinsics use member-call syntax. All other member calls are rejected, including:

```franko
x.print();          // unsupported member call: print is a statement keyword
x.something(1);     // unsupported member call
```

## 28.3 No Structs or Fields

There are no user-defined aggregate types beyond arrays.

## 28.4 No Floating-Point Types

Only integer primitives are supported.

## 28.5 No Separate Boolean Type

Boolean results use `uint8_t`.

## 28.6 No Implicit Numeric Conversions for Nonconstants

Nonconstant numeric assignments require exact type equality.

## 28.7 Arrays Are Not Assignable as Values

Arrays cannot be assigned directly as ordinary values.

Array initializer assignment is an exception only in syntax shape, not in value semantics.

## 28.8 Array Initializer Lists Are Not General Values

Array initializer lists are not first-class values.

Invalid:

```franko
[1, 2, 3];
print([1, 2, 3]);
foo([1, 2, 3]);
return [1, 2, 3];
```

## 28.9 No Full Dynamic Array Lifetime Checking

The checker validates intrinsic legality, but it does not fully prove dynamic array lifetime and bounds properties.

## 28.10 Delete Checking Is Limited

Delete checking is symbol-based, not alias-aware, and branch-sensitive only up to a nesting-depth limit (8); beyond that depth the analysis falls back to a conservative path-insensitive merge. See [Memory Safety Checking](25-memory-safety-checking.md) for the full list of rules and limitations.

## 28.11 Address Lifetime Is Not Fully Tracked

The compiler does not fully track dangling addresses or ownership.

## 28.12 Print Checking Is Loose

The checker validates print arguments as expressions but does not enforce a strict printable-type set. The backend can print integer primitives and addresses; printing other types (for example arrays) does not produce compilable C++. See [Statements](18-statements.md).

Array initializer lists and string literals are not expressions and therefore cannot be printed directly.

## 28.13 Unsupported Operators

The following operators do not exist in the grammar; source code using them fails to parse:

```franko
%   // no modulus operator
~x  // no bitwise NOT operator
+x  // no unary plus operator
```

## 28.14 String Literals Are Initializer-Only

String literals are supported as byte-array initializer sugar (see [String Literals](22-string-literals.md)), but they are not general expression values.

Invalid:

```franko
print("hello");
foo("hello");
return "hello";
```

## 28.15 No `for` Loops, `break`, or `continue`

Only `if` and `while` control flow is implemented. There are no C-style `for` loops and no `break`/`continue` statements. There is also no `switch`, no `do`/`do-while`, and no `goto`.

## 28.16 Global Scope Is Declaration-Only

Only variable declaration nodes and function declaration nodes are valid at global scope.

Executable statements are invalid at global scope.

## 28.17 No Casts, Compound Assignment, Increments, Ternary, or Multiple Declarators

The following forms do not exist in the grammar:

- cast syntax (there is no explicit conversion operator; fluid integer constants are the only cross-width mechanism);
- compound assignment operators (`+=`, `-=`, `*=`, `/=`, and similar) — only plain `=` exists;
- increment and decrement operators (`++`, `--`);
- the ternary conditional operator (`?:`);
- multiple declarators in one declaration (`int a, b;` is invalid; declare each variable separately).