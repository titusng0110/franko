# 12. Functions

Franko supports user-defined functions.

Function declarations are global top-level items.

Function declarations are not valid inside blocks or inside other function bodies.

## 12.1 Function Declaration Syntax

```franko
func name(parameters) -> returnType {
    statements
}
```

## 12.2 Function Declaration Placement

Function declarations are valid only in global scope.

## 12.3 Function Parameters

Function parameters are treated as local variables inside the function body.

Duplicate parameter names inside the same function are invalid.

A parameter type must be a **passable type**:

- a primitive integer type;
- an pointer type `ptr<T>`, where `T` is any valid declared type. Arrays and nested pointers are allowed as the referenced type, so `ptr<[int32]>`, `ptr<[int32, 8]>`, and `ptr<ptr<int32>>` are all passable; or
- a struct type whose fields are all assignable value types, recursively. Assignable = primitive | pointer | struct-with-all-assignable-fields. Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing one of them is pointer-only: pass `ptr<X>` and `getptr(s)` instead. See [Structs](25-structs.md).

Bare array types are not passable. To pass an array to a function, declare an `ptr<[T]>` (or `ptr<[T, N]>`) parameter and pass `getptr(array)` at the call site.

`void` cannot be written as a parameter type at all: the grammar only accepts an ordinary type in parameter positions, and `void` is not an ordinary type.

## 12.4 `void`

`void` is valid as a function return type. It is not an ordinary value type.

## 12.5 Function Signatures and Overloading

Franko supports function overloading.

A function signature is determined by:

- the function identifier;
- the ordered list of parameter types.

Return type is not part of the function signature.

Declaring two functions with the same identifier and the same ordered parameter types is an error, even if their return types differ.

Emitted C++ names carry the return type even though overload resolution does not: each function emits as `FUNC_f_HASH_<8hex>`, where the suffix is the FNV-1a 32-bit hash of the canonical signature key `fn(P1,P2,...)->R` (declared parameter types in order, then the return type, each spelled exactly as written: `int32`, `uint64`, `void`, `[T]`, `[T,N]`, `ptr<T>`, `mview<T>`, `view<T>`, qualified struct names). The backend therefore never relies on g++ overload resolution. The entry-point `main` emits its mangled name like every other function: the generated C++ entry point is a small wrapper in the program template that calls it (`ProgramTemplate.cpp` calls the no-args `main`, `ProgramTemplateWithRawCArgs.cpp` converts `argc`/`argv` and calls the args-form `main`).

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

For folded integer constants, the constant may match a primitive integer parameter if its value fits that parameter type. If a constant argument fits the parameter types of multiple overloads, the call is ambiguous and is rejected.

Overload preference: among applicable overloads, ones matched without any
conversion win over converting ones, and among the rest the ones relying
on the fewest conversions win, with a decay counted heavier than a pure
permission downgrade. If several overloads remain
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

In a call position, `name(...)` resolves to a function even when a non-array variable with the same name is in scope. The one exception is a dynamic array variable: `xs(10)` on a dynamic array is array `init` shorthand, not a function call. See [Array Intrinsics](21-array-intrinsics.md).

## 12.8 Function Call Expressions

A function call expression has the return type of the selected function.

A `void`-returning function call is valid as an expression statement but invalid in value-producing contexts.

## 12.9 Return Statements

A `void` function may use bare `return` and must not use `return expr`.

A non-`void` function must return an expression compatible with the declared return type.

A non-`void` function must contain at least one `return` statement somewhere in its body (a bare `return;` still counts for this presence check, but is separately rejected for lacking an expression). The checker's missing-return detection is presence-based: a `return` nested inside an `if` or `while` body counts as appearing in the function, and full path-sensitive "all paths return" analysis is not performed.

## 12.10 Function Return Types

A function return type must be either:

- `void`;
- a primitive integer type;
- an pointer type, `ptr<T>`; or
- a struct type whose fields are all assignable value types, recursively (see [Structs](25-structs.md)).

Arrays cannot currently be returned directly.

## 12.11 Forward References and Recursion

Function signatures are registered before function bodies are analyzed, allowing forward references, direct recursion, and mutual recursion.

Struct methods follow the same signature, overload, and return rules with an explicit `self` first parameter of type `ptr<X>`, `mview<X>`, or `view<X>`; see [Methods](26-methods.md).