# 12. Functions

Franko supports user-defined functions.

Function declarations are global top-level items.

Function declarations are not valid inside blocks or inside other function bodies.

## 12.1 Function Declaration Syntax

```franko
func name(parameters) -> return_type {
    statements
}
```

By convention function names use `lower_case_snake_case`. A function whose
name starts with `__` is internal to its declaring namespace and produces a
warning when referenced from outside that namespace. Direct FFI mappings keep
the platform API spelling, such as `os::win32::ReadFile`; see
[Naming Conventions](33-naming-conventions.md).

## 12.2 Function Declaration Placement

Function declarations are valid only in global scope.

## 12.3 Function Parameters

Function parameters are treated as local variables inside the function body.

Duplicate parameter names inside the same function are invalid.

A parameter type must be a **passable type**:

- a primitive integer type;
- a pointer type (`ptr<T>`, `mview<T>`, or `view<T>`), where `T` is any valid declared type. Arrays and nested pointers are allowed as the referenced type, so `ptr<[int32]>`, `mview<[int32, 8]>`, and `view<ptr<int32>>` are all passable; or
- a struct type whose fields are all assignable value types, recursively. Assignable = primitive | pointer | struct-with-all-assignable-fields. Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing one of them is pointer-only: pass a pointer to it (for example `ptr<X>` with `getptr(s)` when ownership or lifecycle operations are needed, or `view<X>`/`mview<X>` for read/write access). See [Structs](25-structs.md); or
- a tuple type whose elements are all assignable value types, recursively. A tuple containing a static array, dynamic array, or `void` is pointer-only, exactly like a struct with such a field. See [Tuples](06-types.md#61-tuples).

Bare array types are not passable. To pass an array to a function, declare a pointer parameter (`ptr<[T]>`, `mview<[T]>`, or `view<[T]>`, and the corresponding static-size forms) and pass the matching `getptr`/`getmview`/`getview` expression at the call site.

`void` cannot be written as a parameter type.

## 12.4 `void`

`void` is valid as a function return type. It is not an ordinary value type.

## 12.5 Function Signatures and Overloading

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

## 12.6 Function Call Resolution

A function call is valid if and only if exactly one overload is applicable.

For nonconstant arguments, the argument type must exactly match the
parameter type, with the single exception of the pointer conversions:
permission downgrades over an identical referenced type (`ptr`/`mview`/
`view` may pass where a weaker permission is expected), and the
static-to-dynamic array pointer view decay (`mview`/`view` of
`[T, N]`, or a `ptr` source downgraded in the same step, may pass
where `mview`/`view` of `[T]` is expected; see
[Pointers](24-pointers.md)). There are no other implicit conversions: pass
`cast<T>(x)` explicitly to move a value across integer widths (see
[Casts](16-casts.md)).

For folded integer constants, the constant may match a primitive integer parameter if its value fits that parameter type. When a constant fits several overloads, the constant-fit preference applies: the parameter matching the constant's own type wins when the value fits, otherwise the narrowest fitting type wins (signed before unsigned at equal width). See [Fluid Integer Constants](09-fluid-integer-constants.md).

Overload preference: among applicable overloads, ones matched without any
conversion win over converting ones, and among the rest the ones relying
on the fewest conversions win, with a decay counted heavier than a pure
permission downgrade. Constant arguments then prefer the lowest constant-fit cost (own type, then narrowest, signed first). If several overloads remain
tied, the call is ambiguous and is rejected.

## 12.7 Bare Function Names

Variables and functions live in separate namespaces.

A bare function name is not a value. An identifier used as a value resolves to a variable first; if the identifier only matches a function name, it is rejected:

```franko
func f() -> int32 {
    return 1;
}

f;    // invalid: Function name 'f' cannot be used as a value
```

In a call position, `name(...)` resolves to a function even when a variable with the same name is in scope. Variables are not callable; using a variable name in call position is an error.

## 12.8 Function Call Expressions

A function call expression has the return type of the selected function.

A `void`-returning function call is valid as an expression statement but invalid in value-producing contexts.

## 12.9 Return Statements

A `void` function may use bare `return` and must not use `return expr`.

A non-`void` function must return an expression compatible with the declared return type.

A non-`void` function must contain at least one `return` statement somewhere in its body (a bare `return;` still counts for this presence check, but is separately rejected for lacking an expression). Missing-return detection is presence-based: a `return` nested inside an
`if` or `while` body counts as appearing in the function, and full
path-sensitive "all paths return" analysis is not performed.

## 12.10 Function Return Types

A function return type must be either:

- `void`;
- a primitive integer type;
- a pointer type (`ptr<T>`, `mview<T>`, or `view<T>`); or
- a struct type whose fields are all assignable value types, recursively (see [Structs](25-structs.md)); or
- a tuple type whose elements are all assignable value types, recursively (see [Tuples](06-types.md#61-tuples)).

Arrays cannot currently be returned directly. A tuple containing an array or another non-assignable element cannot be returned by value; use a pointer to the tuple instead.

## 12.11 Forward References and Recursion

Function signatures are known before function bodies are checked, allowing forward references, direct recursion, and mutual recursion.

Struct methods follow the same signature, overload, and return rules with an explicit `self` first parameter of type `ptr<X>`, `mview<X>`, or `view<X>`; see [Methods](26-methods.md).

## 12.12 Compile-Time Intrinsics

The functions in the `static` namespace (`static::is_primitive`,
`static::what_type`, `static::strcmp`, and the rest) are built-in
compile-time functions, not callable values. They are resolved at
compile time; they cannot be stored in a variable, passed as a function
argument, or referred to without a call. See
[Compile-Time Reflection](34-compile-time-reflection.md).