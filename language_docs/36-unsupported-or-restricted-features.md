# 36. Unsupported or Restricted Features

The current Franko implementation intentionally leaves several areas restricted or incomplete.

## 36.1 Function Restrictions

- function declarations are valid only in global scope;
- nested functions are not supported;
- first-class function values are not supported;
- function pointers are not supported;
- anonymous functions/lambdas are not supported;
- parameter names are not used in overload resolution;
- return type is not used in overload resolution;
- arrays cannot be returned by value;
- pointers cannot be returned by value; functions and methods return `void` or a returnable-by-value type;
- arrays cannot be passed as parameters by value; use a pointer parameter (`ptr<[...]>`, `mview<[...]>`, or `view<[...]>`, see [Functions](19-functions.md));
- `void` calls cannot be used in value-producing contexts;
- a full all-paths-return proof is not currently performed.

## 36.2 No General Member Calls

Struct methods and the built-in array intrinsics use member-call syntax (see [Methods](28-methods.md) and [Array Intrinsics](23-array-intrinsics.md)). All other member calls are rejected, including:

```franko
x.stdout();          // unsupported member call: no such struct method
x.something(1);     // unsupported member call: unknown method
```

## 36.3 Struct Restrictions

Structs are supported as user-defined aggregate types with named fields, subject to these restrictions:

- struct field initializers must be constant expressions, and only primitive fields can have them (struct and array fields reject `= constExpr` as a type mismatch; pointer members are not allowed at all);
- a struct may not reach itself by value through struct fields or static array elements; `[Node]` (dynamic array) fields break the cycle and are allowed (pointer members are banned, so the cycle must be broken with a dynamic array edge or a pool index from `franko/pool`);
- a struct is assignable as a whole value only if all of its fields are assignable, recursively: assignable = primitive | enum | pointer | struct-with-all-assignable-fields with no pointer or array members. Static arrays and dynamic arrays are not assignable, so a struct containing one of them is neither assignable nor passable nor returnable by value: access it through a pointer (`ptr<X>` for ownership/lifecycle, or `mview<X>`/`view<X>` for access), or declare `OPERATOR_MOV` to provide custom whole-value assignment. A passable-by-value struct cannot declare `OPERATOR_MOV`; its whole-value assignment is always the built-in field copy. Tuple elements follow the same assignability rule, but tuples cannot declare `OPERATOR_MOV` (see [Tuples](29-tuples.md));
- structs have methods with explicit `self` receivers of type `ptr<X>`, `mview<X>`, or `view<X>` (see [Methods](28-methods.md)); the `self` slot is implicitly const and cannot be reassigned; general member calls beyond struct methods and array intrinsics, such as `s.foo()` for an unknown `foo`, are rejected;
- comparison and assignment operators on structs are only those with `OPERATOR_*` methods; arithmetic, bitwise, shift, and logical operators are not overloadable (see [Operators](13-operators.md#1310-struct-operator-overloading)).

The complete struct reference, including the reasoning behind each restriction, is in [Structs](27-structs.md).

## 36.4 No Floating-Point Types

Only integer primitives are supported.

## 36.5 No Separate Boolean Type

Boolean results use `uint8`.

## 36.6 No Implicit Numeric Conversions for Nonconstants

Nonconstant numeric expressions require exact type equality wherever a
typed value is expected. Moving between integer widths is always
explicit through the `cast<T>(expr)` intrinsic (value-preserving widening) or `unsafe_cast<T>(expr)` (checked conversions that can lose data) (see [Casts](12-casts.md)).

## 36.7 Arrays Are Not Assignable as Values

Arrays cannot be assigned directly as ordinary values, including from
array literals: copy literal elements with `memcpy` (see
[Array Literals](22-array-literals.md)).

## 36.8 Array Literals Need an Inference Context

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

## 36.9 Dynamic Array Lifetime Checking Is Partial

The compiler tracks the init/uninit lifecycle of function-local dynamic
array variables and owner `ptr<[T]>` aliases, including what called functions
require and change. It rejects definite use before init, use after `uninit`,
invalid free, and double free; possible cases and missing explicit `uninit()`
calls are warnings. Repeated `init` is not diagnosed because an earlier call
may have failed with `-1` and a retry is valid. Element pointers/views created
from `ptr`/`mview`/`view(d[i])` are diagnosed when a
`resize`/`uninit` invalidates them; owner pointers and whole-array views point
at the stable array object and remain valid. The check
does not track dynamic arrays in struct fields or nested elements below the
outermost tracked array, allocation failure status, or all runtime bounds
properties. Globals cannot be dynamic arrays. See
[Dynamic-Array Lifecycle Checking](32-memory-safety-checking.md#3210-dynamic-array-lifecycle-checking).

## 36.10 Pointer Lifetime Is Not Fully Tracked

The compiler does not fully track dangling pointers. It detects using a
pointer after its inner block or loop body has ended and passing such a
pointer as a call argument. Pointer returns, aggregate stores, mutable
global stores, and writable pointer-to-pointer parameters are rejected
by the type rules, so there is no return/store path left to track.
Decayed static-array views refer to their original array, so a decayed
view of local array storage dangles exactly like any other pointer to
that local once the block ends. Stacked-pointer chains, loop-carried
lifetimes, and escapes through very deep or recursive call chains may be
missed. See
[Dangling-Pointer Checking](32-memory-safety-checking.md#329-dangling-pointer-checking).

## 36.11 Output Is a Library Function

Output is provided by `franko/stdio`, not by the compiler. The overloaded `stdout(...)` functions accept the eight integer types and `view<[uint8]>` text; there are no overloads for pointers, arrays, or structs, so passing one is a "No matching overload" error. See [Statements](17-statements.md) and [franko/stdio](../library_docs/stdio.md).

## 36.12 Unsupported Operators

The unary plus operator does not exist in the language:

```franko
+x  // no unary plus operator
```

Shift compound assignments (`<<=`, `>>=`) also do not exist: assignment has plain `=` plus the arithmetic (`+=`, `-=`, `*=`, `/=`, `%=`) and bitwise (`&=`, `|=`, `^=`) compound forms (see [Assignment](16-assignment.md)).

Struct comparison and assignment operators are overloadable through `OPERATOR_*` methods. Arithmetic, bitwise, shift, logical, and unary operators are not overloadable; a struct operand is still rejected for those operators. A passable-by-value struct cannot declare `OPERATOR_MOV`; only non-passable structs without pointers may opt into custom whole-value assignment. Compound assignment methods remain available on passable-by-value structs. `const` is limited to passable-by-value locals, parameters, and globals; it cannot be used on arrays, aggregates with pointer or array members, `void`, or struct fields. Global variables are always implicitly const. See [Operators](13-operators.md#1310-struct-operator-overloading) and [Declarations](18-declarations.md#188-const-storage).

## 36.13 String Literals Cannot Initialize Arrays

A string literal materializes as a `view<[byte, N]>` value in value positions,
not as a byte-array value (see [String Literals](24-string-literals.md)).
Arrays are not assignable, so a string literal cannot initialize or assign an
array; copy the bytes with `memcpy` instead.

A string literal has no type of its own. It cannot be dereferenced, indexed,
accessed as a member, cast, or compared with a runtime view. It supports only
compile-time `+` concatenation and compile-time comparisons, and materializes
only as an assignment value, a function argument, or a return value. The
former `#strcat` and `#strcmp` intrinsics are removed; use `+` and the string
comparison operators instead.

Invalid:

```franko
[byte, 6] s = "hello";    // invalid
s = "hello";                   // invalid
[byte, ?] s = "hello";    // invalid: '?' requires a view declaration
$("hello");                    // invalid: no type to dereference
"hello" == view(s);         // invalid: runtime view comparison
cast<view<[byte, 5]>>("hello"); // invalid: no string-literal casts
```

Valid:

```franko
[byte, 6] s;
s.memcpy("hello");
```

## 36.14 No `switch` or `do`/`do-while`

Only `if`, `while`, `for`, `break`, and `continue` control flow is implemented. There is no `switch`, no `do`/`do-while`, and no user-visible `goto`; see [Statements](17-statements.md).

## 36.15 Global Scope Is Declaration-Only

Only struct declarations, global variable declarations, function declarations, namespace declarations, imports, and `#if` declaration conditionals are valid at global scope.

Executable statements are invalid at global scope. Global variables are implicitly const and must be passable by value, so arrays and aggregates with pointer or array members cannot be global. Static struct/tuple aggregate literals may initialize globals; field-wise declaration initializers remain local-only. See [Aggregate Literals](30-aggregate-literals.md) and [Declarations](18-declarations.md#184-global-variable-declarations).

## 36.16 No C-Style Casts, Ternary, or Multiple Declarators

The following forms do not exist in the grammar:

- C-style cast syntax (`(T)x` does not exist; explicit conversions use the `cast<T>(expr)` and `unsafe_cast<T>(expr)` intrinsics, and fluid integer constants still convert implicitly when they fit);
- the ternary conditional operator (`?:`);
- multiple declarators in one declaration (`int a, b;` is invalid; declare each variable separately).

## 36.17 Operator Overload Lifetime Limitation

Operator operands may be rvalues: an rvalue operand is borrowed for the
duration of the full expression. The operator method must not retain the
borrowed `other` view beyond the call.

The direct escape is closed by the operator signatures (`uint8`/`void`
returns): operator methods cannot return pointers, and pointers cannot be
stored in aggregates or written through writable pointer-to-pointer parameters,
so the borrowed view cannot be retained beyond the call. Using it after
the full expression ends is a use-after-scope error. Ordinary
non-operator methods with `view<T>` parameters are checked the same way.
See
[Memory Safety Checking](32-memory-safety-checking.md#329-dangling-pointer-checking)
and [Operators](13-operators.md#1310-struct-operator-overloading).

## 36.18 Tuple Restrictions

Tuple restrictions are documented in
[Tuples](29-tuples.md#2910-restrictions).

## 36.19 Enum Restrictions

- Enums are declared only at global or namespace scope; no local, nested, or struct-member enum declarations.
- An enum must provide explicit values for every variant or for none; mixing is rejected.
- Every variant name and every variant value must be unique; implicit wrapping that collides is rejected.
- Explicit values must fit the backing type; only the eight primitive integer types are valid backings.
- No arithmetic, bitwise, shift, logical, or compound-assignment operators on enums: only `==`, `!=`, `<`, `>`, `<=`, `>=`, and `=` are supported.
- No implicit conversions between enums and integers; cross-width moves use `cast` (safe) or `unsafe_cast` (checked, runtime-aborted on invalid variants or out-of-range values).
- Integer-to-enum casts (even constant ones like `unsafe_cast<BigState>(999)`) compile and abort at runtime instead of failing at compile time.
- Enum array elements are not memset-safe.
- Enums have no methods, no `OPERATOR_*` overloading, and no tuple-style destructuring.

The complete enum reference is in [Enums](31-enums.md).

## 36.20 Pointers Cannot Appear in Aggregates

No pointer (`ptr<T>`, `mview<T>`, or `view<T>`) can be stored inside an
array, struct, or tuple, and no pointer referent may be an aggregate that
contains a pointer. This includes `[ptr<T>]`, `[ptr<T>, N]`, struct fields
of type `ptr<T>` or `view<T>`, tuple elements of type `ptr<T>` or
`view<T>`, and `ptr<[ptr<T>]>` or `view<[view<T>]>` whose referent
aggregate contains a pointer. Pointer stacks such as `ptr<ptr<T>>` and
read-only `view<ptr<T>>` remain allowed. The declaration is rejected by
type checking.

The restriction keeps downward-only pointers from escaping into
unrelated storage. Keep pointers as direct variables, parameters, or
read-only globals, pass them into callees, and write aggregate or buffer
output through an out-parameter that points at non-pointer storage. For
linked or node-like data, use the pool allocator in `franko/pool` and
store slot indices, not pointers. For an array of arrays, use a nested
array such as `[[T]]`; the outer array owns the inner arrays and manages
their lifetime. See
[Pointers Cannot Appear in Aggregates](26-pointers.md#264-pointers-cannot-appear-in-aggregates).

## 36.21 Generic Restrictions

Generics are compile-time templates with explicit arguments. The following
are not supported in this version:

- generic enums;
- generic global variables;
- generic namespaces;
- a generic `main`;
- generic FFI-bridged functions;
- type-argument inference;
- generic operator methods;
- generic structs with pointer members (pointers are banned from all
  aggregates, so a generic struct cannot carry a pointer member or element);
- generic functions or methods that return pointers (pointer returns are
  banned for all functions);
- runtime generic representation, type erasure, vtables, or generic function
  values.

A generic struct name must carry explicit arguments in every type position,
including inside its own body. Write `Node<T>`, not `Node`. A non-generic
struct is still written bare.

Expansion is limited to 1024 generic instances per compilation and an
instantiation depth of 64.

See [Generics](20-generics.md).

## 36.22 Named and Optional Argument Restrictions

Named arguments and declaration-site defaults are supported for ordinary
functions and struct methods, with these restrictions:

- default values must be compile-time forms; runtime expressions such as
  variables, parameters, globals, calls, casts, dereferences, indexing, and
  member accesses are rejected;
- aggregate literal defaults are limited to passable-by-value struct literals
  whose field values use the same static forms;
- tuple aggregate-literal defaults are not part of the current default model;
- optional arguments must be passed by name, and positional arguments must
  precede named arguments;
- required arguments must be supplied either all positionally or all by name;
- `self` cannot have a default and cannot be passed by name;
- `OPERATOR_*` methods cannot declare defaults and reject named arguments;
- `main` cannot declare optional parameters;
- array intrinsics and compile-time `#` intrinsics do not accept named
  arguments;
- defaults are not runtime values: they are resolved once at the declaration
  and copied into each call site as ordinary positional arguments.

See [Functions](19-functions.md#1915-named-and-optional-arguments).
