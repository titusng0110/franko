# 33. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

## 33.1 Function Restrictions

- function declarations are valid only in global scope;
- nested functions are not supported;
- first-class function values are not supported;
- function pointers are not supported;
- anonymous functions/lambdas are not supported;
- parameter names are not used in overload resolution;
- return type is not used in overload resolution;
- arrays cannot be returned by value;
- arrays cannot be passed as parameters by value; use a pointer parameter (`ptr<[...]>`, `mview<[...]>`, or `view<[...]>`, see [Functions](19-functions.md));
- `void` calls cannot be used in value-producing contexts;
- a full all-paths-return proof is not currently performed.

## 33.2 No General Member Calls

Struct methods and the built-in array intrinsics use member-call syntax (see [Methods](27-methods.md) and [Array Intrinsics](22-array-intrinsics.md)). All other member calls are rejected, including:

```franko
x.print();          // unsupported member call: no such struct method
x.something(1);     // unsupported member call: unknown method
```

## 33.3 Struct Restrictions

Structs are supported as user-defined aggregate types with named fields, subject to these restrictions:

- struct field initializers must be constant expressions, and only primitive fields can have them (struct, array, and ptr fields reject `= constExpr` as a type mismatch);
- a struct may not reach itself by value through struct fields or static array elements; `ptr<Node>` fields and `[Node]` (dynamic array) fields break the cycle and are allowed;
- a struct is assignable as a whole value (assignment, by-value parameter, by-value return) only if all of its fields are assignable, recursively: assignable = primitive | pointer | struct-with-all-assignable-fields. Static arrays and dynamic arrays are not assignable, so a struct containing one of them is pointer-only: access it through a pointer (`ptr<X>` for ownership/lifecycle, or `mview<X>`/`view<X>` for access), or declare `OPERATOR_MOV` to provide custom whole-value assignment. Tuple elements follow the same assignability rule, but tuples cannot declare `OPERATOR_MOV` (see [Tuples](28-tuples.md));
- structs have methods with explicit `self` receivers of type `ptr<X>`, `mview<X>`, or `view<X>` (see [Methods](27-methods.md)); general member calls beyond struct methods and array intrinsics, such as `s.foo()` for an unknown `foo`, are rejected;
- comparison and assignment operators on structs are only those with `OPERATOR_*` methods; arithmetic, bitwise, shift, and logical operators are not overloadable (see [Operators](13-operators.md#1310-struct-operator-overloading)).

The complete struct reference, including the reasoning behind each restriction, is in [Structs](26-structs.md).

## 33.4 No Floating-Point Types

Only integer primitives are supported.

## 33.5 No Separate Boolean Type

Boolean results use `uint8`.

## 33.6 No Implicit Numeric Conversions for Nonconstants

Nonconstant numeric expressions require exact type equality wherever a
typed value is expected. Moving between integer widths is always
explicit through the `cast<T>(expr)` intrinsic (value-preserving widening) or `unsafe_cast<T>(expr)` (checked conversions that can lose data) (see [Casts](12-casts.md)).

## 33.7 Arrays Are Not Assignable as Values

Arrays cannot be assigned directly as ordinary values, including from
array literals: copy literal elements with `memcpy` (see
[Array Literals](21-array-literals.md)).

## 33.8 Array Literals Need an Inference Context

An array literal's element type is inferred from an assignment,
return, or call-argument context. Without one, the literal is
rejected:

```franko
[1, 2, 3];                 // invalid: bare expression statement
foo([1, 2, 3]);            // invalid unless foo's parameter is a view<[...]>
return [1, 2, 3];          // invalid unless the return type is a view<[...]>
```

Array literal elements must be compile-time constant expressions
(runtime values cannot appear in one).

## 33.9 Dynamic Array Lifetime Checking Is Partial

The compiler tracks the init/uninit lifecycle of function-local dynamic
array variables and owner `ptr<[T]>` aliases, including callee requirements
and post-states through summaries. It rejects definite use before init, use
after `uninit`, invalid free, and double free; possible cases and missing
explicit `uninit()` calls are warnings. Repeated `init` is not diagnosed
because an earlier call may have failed with `-1` and a retry is valid.
Element pointers/views created from `getptr`/`getmview`/`getview(d[i])` are
tracked as buffer aliases and diagnosed when a `resize`/`uninit` invalidates
them; owner pointers and whole-array views point at the stable
`Franko_Dynamic_Array` descriptor and remain valid. The check does not track
dynamic arrays in struct fields or nested elements below the outermost
tracked array, global dynamic-array lifecycle, allocation failure status, or
all runtime bounds properties. See
[Dynamic-Array Lifecycle Checking](29-memory-safety-checking.md#2910-dynamic-array-lifecycle-checking).

## 33.10 Pointer Lifetime Is Not Fully Tracked

The compiler does not fully track dangling pointers or ownership. It detects
returning a pointer to the current function's own frame storage, storing a
pointer to frame or operator-borrowed storage into a global or caller-owned
location, using a pointer after an inner block or loop body has ended, and
forwarding a frame pointer through callees that capture it. Pointers hidden
inside returned structs or tuples and field-sensitive struct tracking remain
untracked. Decayed static-array views refer to their original array, so they
are safe to return when that storage is a parameter or global; a decayed view
of local array storage is still caught as a normal frame-storage escape.
Recursion and the 2048-node call-graph budget fall back to a silent
conservative result. See
[Dangling-Pointer Checking](29-memory-safety-checking.md#299-dangling-pointer-checking).

## 33.11 Output Is a Library Function

Output is provided by `franko/io`, not by the compiler. The overloaded `print(...)` functions accept the eight integer types and `view<[uint8]>` text; there are no overloads for pointers, arrays, or structs, so passing one is a "No matching overload" error. See [Statements](17-statements.md) and [franko/io](../library_docs/io.md).

## 33.12 Unsupported Operators

The unary plus operator does not exist in the language:

```franko
+x  // no unary plus operator
```

Shift compound assignments (`<<=`, `>>=`) also do not exist: assignment has plain `=` plus the arithmetic (`+=`, `-=`, `*=`, `/=`, `%=`) and bitwise (`&=`, `|=`, `^=`) compound forms (see [Assignment](16-assignment.md)).

Struct comparison and assignment operators are overloadable through `OPERATOR_*` methods. Arithmetic, bitwise, shift, logical, and unary operators are not overloadable; a struct operand is still rejected for those operators. See [Operators](13-operators.md#1310-struct-operator-overloading).

## 33.13 String Literals Cannot Initialize Arrays

A string literal is a `view<[byte, N]>` value, not byte-array
sugar (see [String Literals](23-string-literals.md)). Arrays are not
assignable, so a string literal cannot initialize or assign an array;
copy the bytes with `memcpy` instead.

Invalid:

```franko
[byte, 6] s = "hello";    // invalid
s = "hello";                   // invalid
[byte, ?] s = "hello";    // invalid: '?' requires a view declaration
```

Valid:

```franko
[byte, 6] s;
s.memcpy("hello");
```

## 33.14 No `switch` or `do`/`do-while`

Only `if`, `while`, `for`, `break`, and `continue` control flow is implemented. There is no `switch`, no `do`/`do-while`, and no user-visible `goto`; see [Statements](17-statements.md).

## 33.15 Global Scope Is Declaration-Only

Only struct declarations, global variable declarations, function declarations, namespace declarations, imports, and `#if` declaration conditionals are valid at global scope.

Executable statements are invalid at global scope.

## 33.16 No C-Style Casts, Ternary, or Multiple Declarators

The following forms do not exist in the grammar:

- C-style cast syntax (`(T)x` does not exist; explicit conversions use the `cast<T>(expr)` and `unsafe_cast<T>(expr)` intrinsics, and fluid integer constants still convert implicitly when they fit);
- the ternary conditional operator (`?:`);
- multiple declarators in one declaration (`int a, b;` is invalid; declare each variable separately).

## 33.17 Operator Overload Lifetime Limitation

Operator operands may be rvalues: the compiler borrows them through a
temporary whose lifetime is the full expression. This is the C++ `const&`
temporary model, not Rust-style lifetime tracking. The operator method
must not retain the borrowed `other` view beyond the call.

The direct escape is closed by the operator signatures (`uint8`/`void`
returns) and by the dangling-pointer store rule: storing the borrowed
view into caller-owned or global storage is rejected. Storing it in a
local `view<T>` is allowed because the local dies before the operator
call returns. Forwarding the borrowed view to a callee that stores it is
now covered by the interprocedural capture summary. Ordinary non-operator
methods with `view<T>` parameters are also covered when their arguments are
forwarded to a capturing callee. See
[Memory Safety Checking](29-memory-safety-checking.md#299-dangling-pointer-checking)
and [Operators](13-operators.md#1310-struct-operator-overloading).

## 33.18 Tuple Restrictions

Tuple restrictions are documented in
[Tuples](28-tuples.md#2810-restrictions).
