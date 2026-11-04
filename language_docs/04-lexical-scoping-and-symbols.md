# 4. Lexical Scoping and Symbols

Franko uses lexical scope.

Function bodies can resolve names from:

- the function's parameter and body scope (parameters and body-level declarations share one scope);
- local block scopes;
- enclosing block scopes;
- global variable scope;
- global function declarations for calls.

## 4.1 Declaration Lookup

Variable references resolve to the nearest declaration in the current scope stack.

## 4.2 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

## 4.3 Shadowing

Shadowing in an inner block is allowed.

## 4.4 Use Before Declaration

Local variables must be declared before they are used within their lexical scope.

Global variable declarations are registered before function bodies are analyzed.

## 4.5 Undeclared Variables

Using an undeclared variable is illegal.

## 4.6 Function Body Scope

The braces of a function body do not create an additional scope. Parameters and the function body's top-level declarations share a single scope.

Therefore a body-level declaration that reuses a parameter name is a duplicate-declaration error:

```franko
func f(int32 x) -> int32 {
    int32 x;   // invalid: duplicate declaration of 'x'
    return x;
}
```

Only nested `{ ... }` blocks inside the body create new scopes.

## 4.7 Namespace Lookup

Namespaces add two lookup rules on top of lexical scope; see [Program Structure](03-program-structure.md) for the full reference.

Unqualified names used inside a namespace resolve in that namespace first, then fall back to the global namespace. Enclosing namespaces are never searched.

Qualified names (`a::b::x`) resolve strictly inside the named namespace path from the top level, with no fallback. The first segment must name a top-level namespace.

## 4.8 Identifier Visibility

Franko uses leading underscores to mark non-public declarations:

- a struct field or method whose name starts with one or more underscores
  (`_name`, `__name`, ...) is **private to its struct**;
- a free function or struct whose name starts with two or more underscores
  (`__name`, `___name`, ...) is **internal to its namespace**.

Access is allowed inside the declaration's own scope:

- a private field or method may be used by methods declared in the same
  struct; access from any free function or from a method of another struct
  warns;
- a namespace-internal function or struct may be referenced from its declaring
  namespace or from a namespace nested under it; access from any other
  namespace warns. Global-namespace internal names are accessible only from
  global-scope code.

These are compiler warnings, not errors: the program still compiles and runs.
The complete naming style for structs, functions, methods, variables, fields,
namespaces, constants, and standard-library imports is in
[Naming Conventions](34-naming-conventions.md).
