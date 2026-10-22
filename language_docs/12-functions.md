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

Parameters cannot have type `void`.

## 12.4 `void`

`void` is valid as a function return type. It is not an ordinary value type.

## 12.5 Function Signatures and Overloading

Franko supports function overloading.

A function signature is determined by:

- the function identifier;
- the ordered list of parameter types.

Return type is not part of the function signature.

Declaring two functions with the same identifier and the same ordered parameter types is an error, even if their return types differ.

## 12.6 Function Call Resolution

A function call is valid if and only if exactly one overload is applicable.

For nonconstant arguments, the argument type must exactly match the parameter type.

For folded integer constants, the constant may match a primitive integer parameter if its value fits that parameter type. If a constant argument fits the parameter types of multiple overloads, the call is ambiguous and is rejected.

## 12.7 Bare Function Names

A bare function name is not a value.

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
- an address type, `addr<T>`.

Arrays cannot currently be returned directly.

## 12.11 Forward References and Recursion

Function signatures are registered before function bodies are analyzed, allowing forward references, direct recursion, and mutual recursion.