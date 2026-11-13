# 19. Functions

Franko supports user-defined functions.

Function declarations are global top-level items.

Function declarations are not valid inside blocks or inside other function bodies.

## 19.1 Function Declaration Syntax

```franko
func name(parameters) -> return_type {
    statements
}
```

By convention function names use `lower_case_snake_case`. A function whose
name starts with `__` is internal to its declaring namespace and produces a
warning when referenced from outside that namespace. Direct FFI mappings keep
the platform API spelling, such as `os::win32::ReadFile`; see
[Naming Conventions](37-naming-conventions.md).

## 19.2 Function Declaration Placement

Function declarations are valid only in global scope.

## 19.3 Function Parameters

Function parameters are treated as local variables inside the function body.

A parameter may be declared `const`: `func sum(const int32 a, const int32 b)`
or `func f(const Point p)`. Const parameters have the same assignment
restrictions as const locals: the parameter cannot be reassigned, and only
`getview` may take a mutable pointer to it. A const parameter's type must be
value-assignable. `const self` is allowed in methods and only makes the
pointer slot itself immutable; the declared `ptr`/`mview`/`view` permission
still governs the pointee.

`const` is not part of a parameter type, a function signature, or overload
identity: `f(const int32)` and `f(int32)` are duplicate declarations.

Duplicate parameter names inside the same function are invalid.

A parameter type must be a **passable type**:

- a primitive integer type;
- a declared enum type (see [Enums](31-enums.md));
- a pointer type (`ptr<T>`, `mview<T>`, or `view<T>`), where `T` is any valid declared type. Arrays and nested pointers are allowed as the referenced type, so `ptr<[int32]>`, `mview<[int32, 8]>`, and `view<ptr<int32>>` are all passable; an owning `ptr<T>` may not appear inside an array, struct, or tuple, so `ptr<[ptr<int32>]>` is rejected (see [Owning Pointers in Aggregates](26-pointers.md#2613-owning-pointers-in-aggregates-are-forbidden)); or
- a struct type whose fields are all assignable value types, recursively. Assignable = primitive | pointer | struct-with-all-assignable-fields. Static arrays and dynamic arrays are not assignable, so a struct containing one of them is pointer-only: pass a pointer to it (for example `ptr<X>` with `getptr(s)` when ownership or lifecycle operations are needed, or `view<X>`/`mview<X>` for read/write access). See [Structs](27-structs.md); or
- a tuple type whose elements are all assignable value types, recursively. A tuple containing a static array or dynamic array is pointer-only, exactly like a struct with such a field. `void` cannot be a tuple element. See [Tuples](29-tuples.md).

Bare array types are not passable. To pass an array to a function, declare a pointer parameter (`ptr<[T]>`, `mview<[T]>`, or `view<[T]>`, and the corresponding static-size forms) and pass the matching `getptr`/`getmview`/`getview` expression at the call site.

`void` cannot be written as a parameter type.

## 19.4 `void`

`void` is valid as a function return type. It is not an ordinary value type.

## 19.5 Function Signatures and Overloading

Franko supports function overloading.

A function signature is determined by:

- the function identifier;
- the ordered list of parameter types.

Return type is not part of the function signature.

Declaring two functions with the same identifier and the same ordered parameter types is an error, even if their return types differ.

Return type is not part of overload identity, but two declarations with
the same identifier and parameter types are still an error when their
return types differ. Functions may be called recursively.

Tuple element names are not part of a function signature. These two
declarations have the same normalized signature and are duplicates:

```franko
func f((int32 a, int32 b) x) -> int32 { ... }
func f((int32 c, int32 d) y) -> int32 { ... }
```

The normalized signature is:

```text
fn((int32, int32)) -> int32
```

For example, `func foo((int32 x, int32 y) coord, uint8 data) -> (int32 val, int32 err)`
normalizes to `fn((int32, int32), uint8) -> (int32, int32)`.

## 19.6 Function Call Resolution

A function call is valid if and only if exactly one overload is applicable.

For nonconstant arguments, the argument type must exactly match the
parameter type, with the single exception of the pointer conversions:
permission downgrades over an identical referenced type (`ptr`/`mview`/
`view` may pass where a weaker permission is expected), and the
static-to-dynamic array pointer view decay (`mview`/`view` of
`[T, N]`, or a `ptr` source downgraded in the same step, may pass
where `mview`/`view` of `[T]` is expected; see
[Pointers](26-pointers.md)). There are no other implicit conversions: pass
`cast<T>(x)` explicitly to move a value across integer widths (see
[Casts](12-casts.md)).

For folded integer constants, the constant may match a primitive integer parameter if its value fits that parameter type. An untyped constant uses the narrowest fitting parameter type, with signed preferred over unsigned at equal width; an explicitly typed constant prefers an exact parameter type before falling back to that rule. See [Fluid Integer Constants](07-fluid-integer-constants.md).

Overload preference: among applicable overloads, ones matched without any
conversion win over converting ones, and among the rest the ones relying
on the fewest conversions win, with a decay counted heavier than a pure
permission downgrade. Constant arguments then prefer the lowest constant-fit cost (exact type first for typed constants, otherwise narrowest, signed first). If several overloads remain
tied, the call is ambiguous and is rejected.

## 19.7 Bare Function Names

Variables and functions live in separate namespaces.

A bare function name is not a value. An identifier used as a value resolves to a variable first; if the identifier only matches a function name, it is rejected:

```franko
func f() -> int32 {
    return 1;
}

f;    // invalid: Function name 'f' cannot be used as a value
```

In a call position, `name(...)` resolves to a function even when a variable with the same name is in scope. Variables are not callable; using a variable name in call position is an error.

## 19.8 Function Call Expressions

A function call expression has the return type of the selected function.

A `void`-returning function call is valid as an expression statement but invalid in value-producing contexts.

## 19.9 Return Statements

A `void` function may use bare `return` and must not use `return expr`.

A non-`void` function must return an expression compatible with the declared return type. A struct or tuple value may be constructed directly in the return expression with an aggregate literal:

```franko
struct Literal {
    int32 x
    int32 def = 0
}

func make() -> Literal {
    return Literal { x = 1, def = 2 }
}
```

A non-`void` function must contain at least one `return` statement somewhere in its body (a bare `return;` still counts for this presence check, but is separately rejected for lacking an expression). Missing-return detection is presence-based: a `return` nested inside an
`if` or `while` body counts as appearing in the function, and the compiler
does not prove that every path returns.

## 19.10 Function Return Types

A function return type must be either:

- `void`;
- a primitive integer type;
- a declared enum type (see [Enums](31-enums.md));
- a pointer type (`ptr<T>`, `mview<T>`, or `view<T>`); or
- a struct type whose fields are all assignable value types, recursively (see [Structs](27-structs.md)); or
- a tuple type whose elements are all assignable value types, recursively (see [Tuples](29-tuples.md)).

Arrays cannot currently be returned directly. A tuple containing an array or another non-assignable element cannot be returned by value; use a pointer to the tuple instead.

A struct containing an array is pointer-only and cannot be returned by value either. The compiler rejects such a declaration with guidance to return a pointer or write through an out-parameter:

```franko
struct Buffer {
    int32 cap
    [byte] buf
}

func foo() -> Buffer { ... }        // error at declaration

func foo(ptr<Buffer> outbuf) -> int32 {
    // write the result through outbuf
    return 0
}
```

The correct pattern for pointer-only output is the `ptr<Buffer>` out-parameter form. See [Aggregate Literals](30-aggregate-literals.md).

## 19.11 Forward References and Recursion

Function signatures are known before function bodies are checked, allowing forward references, direct recursion, and mutual recursion.

Struct methods follow the same signature, overload, and return rules with an explicit `self` first parameter of type `ptr<X>`, `mview<X>`, or `view<X>`; see [Methods](28-methods.md).

## 19.12 Compile-Time Intrinsics

The built-in `#name(...)` functions are compile-time expressions, not
functions or callable values. Their full rules are in
[Compile-Time Reflection](35-compile-time-reflection.md).

## 19.13 Inlining Modifiers

A function declaration may carry exactly one inlining modifier before
`func`:

```franko
#inline func add_one(int32 x) -> int32 {
    return x + 1;
}

#noinline func trace(int32 x) -> void {
    stdout(x);
    return;
}
```

`#inline` asks the compiler to always inline the function. `#noinline`
asks it never to inline the function. With no modifier, the backend decides
using its normal heuristics. The modifiers apply to free functions,
namespace functions, and struct methods, including declarations selected
by `#if`.

Inlining modifiers do not change behavior or call syntax. They are not part
of the function name, type, signature, call resolution, or overload identity:
`#inline func f(int32 x)` and `func f(int32 x)` are duplicate declarations.

An always-inlined function cannot participate in recursion. The compiler
checks the function call graph and rejects `#inline` when the function can
reach itself:

```franko
#inline func f(int32 n) -> int32 {
    return f(n - 1);       // invalid: #inline function is directly recursive
}

#inline func a(int32 n) -> int32 { return b(n); }
func b(int32 n) -> int32 { return a(n); }   // invalid: a -> b -> a
```

The check covers direct self-recursion, mutual recursion, and recursion
through methods. It uses the whole Franko call graph: marking a function in
the cycle `#noinline` does not exempt an `#inline` function from the rule.
`#noinline` functions may be recursive normally.

The modifiers only describe functions that the Franko compiler emits.
FFI-bridged functions are provided by their bridge and ignore them.

## 19.14 Generic Functions

A function declaration may be a `#generic` template. Generic function
declarations, explicit type/value arguments, overload signatures, and call
resolution are documented in [Generics](20-generics.md).

## 19.15 Named and Optional Arguments

A call may pass arguments by parameter name, and a parameter may declare a
default value:

```franko
func f(int32 a, int32 b, int32 c = 0) -> int32 {
    return a + b + c
}

f(1, 2)                 // valid: c defaults to 0
f(a = 1, b = 2)         // valid: all required arguments named
f(b = 2, a = 1)         // valid: named order is free
f(c = 3, a = 1, b = 2)  // valid: named order is free
f(1, c = 3)             // valid: positional required arguments, named optional argument
```

The call rules are:

- required arguments must be supplied either all positionally or all by name;
- positional arguments must precede named arguments;
- optional arguments must be passed by name;
- a parameter may be supplied at most once;
- named arguments must match a declared parameter name.

Invalid:

```franko
f(1, c = 3, b = 2)      // invalid: required a is positional, b is named
f(1, b = 2)             // invalid: required a is positional, b is named
f(a = 1, 2)             // invalid: positional argument after a named one
f(1, a = 2)             // invalid: a is supplied twice
f(1, 2, 3)              // invalid: optional c must be named
```

Optional parameters trail all required parameters. A default must be a
compile-time value; runtime expressions are rejected:

```franko
func f(int32 a, int32 b = 5) -> int32 { return a + b }   // valid
func g(int32 a, int32 b = a) -> int32 { return a + b }   // invalid: runtime default
```

Allowed default forms are:

- integer constant expressions (`1 + 2`, `1 << 3`, `#IS_LINUX`, folded `#`
  predicates);
- `null` for a pointer parameter;
- string literals;
- array literals;
- enum variant references;
- aggregate struct literals whose struct is value-assignable.

An aggregate literal default restricts each field initializer to the same
static forms: constant expressions, `null`, string literals, array literals,
enum variants, folded `#` expressions, and nested value-assignable struct
literals.

```franko
struct Literal {
    int32 x
    int32 def = 2
}

enum<uint8> Status { spawning, alive }

func text(view<[byte, 5]> s = "hello") -> byte { return $(s)[0] }
func numbers(view<[int32]> xs = [1, 2, 3]) -> uint32 { return $(xs).length() }
func state(Status s = Status::alive) -> uint8 { return s == Status::alive }
func literal(Literal lit = Literal { x = 1 }) -> int32 { return lit.x + lit.def }
```

Defaults are resolved at the declaration and copied into each omitted argument
at the semantic call site. C++ emission sees an ordinary full-arity call.

Overload resolution prefers an overload that needs no defaults over an
otherwise equal overload that does:

```franko
func choose(int32 a) -> int32 { return 1 }
func choose(int32 a, int32 b = 0) -> int32 { return 2 }

choose(1)          // 1
choose(1, b = 3)   // 2
```

Ordinary struct methods support named and optional user arguments. `self` is
implicit and is never nameable:

```franko
struct Counter {
    int32 n = 0

    func add(ptr<Counter> self, int32 amount = 1) -> void {
        $(self).n = $(self).n + amount
        return
    }
}

Counter c
c.add()
c.add(amount = 5)
```

Restrictions:

- `self` cannot have a default and cannot be passed by name;
- `OPERATOR_*` methods cannot declare defaults and reject named arguments;
- `main` cannot declare optional parameters;
- array intrinsics and compile-time `#` intrinsics do not accept named
  arguments.

Diagnostics include:

```text
Positional arguments must appear before named arguments in call to 'f'
Named argument 'x' is specified more than once in call to 'f'
Named argument 'x' does not match any parameter of 'f'
Compulsory arguments in call to 'f' must be either all named or all unnamed
Optional parameter 'x' of 'f' must be passed by name
Missing required argument 'x' in call to 'f'
Optional parameter 'b' in function 'f' must follow all required parameters
Default value for parameter 'b' of function 'f' must be a compile-time value
```
