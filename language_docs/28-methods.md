# 28. Methods

A struct may declare methods: functions bound to the struct whose first parameter is explicitly `self` (`ptr<X> self`, `mview<X> self`, or `view<X> self`). Methods are called with member-call syntax; the receiver is passed through `getptr`/`getmview`/`getview(receiver)` matching the declared `self` type (see [Structs](27-structs.md) for fields and [Functions](19-functions.md) for free functions).

```franko
struct X {
    int32 y = 0

    func increment(ptr<X> self) -> void {
        $(self).y = $(self).y + 1
        return
    }
}

func main() -> int32 {
    X s
    s.y = 41
    s.increment()
    stdout(s.y)
    return 0
}
```

## 28.1 Method Declaration Syntax

A struct body contains field declarations and method declarations:

```franko
struct X {
    int32 y = 0
    func increment(ptr<X> self) -> void {
        $(self).y = $(self).y + 1
        return
    }
}
```

Rules:

- Methods are `func` declarations inside a struct body; separators between members are optional, matching field syntax.
- A method may declare any return type valid for free functions, including `void` (see [Functions](19-functions.md)).
- A method may declare additional parameters after `self`; they follow ordinary parameter rules.
- A method body follows ordinary function body rules, including return checking (see [Functions](19-functions.md)).
- Method names are ordinary identifiers; a method may not share its name with a field of the same struct, and duplicate method signatures are rejected (see [Declaration Type Validity](11-declaration-type-validity.md)).
- By convention method names use `lower_case_snake_case`. A method whose name starts with `_` is private to its struct and warns when called from outside it; see [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md) and [Naming Conventions](37-naming-conventions.md).

## 28.2 The `self` Parameter

The first parameter of every method must be literally `self`, typed
`ptr<X> self`, `mview<X> self`, or `view<X> self`, where `X` is the
enclosing struct. The permission declares what the method does with its
receiver: an owning `self` may run lifecycle operations, a mutable
`self` may write, a read-only `self` may only read (see
[Pointers](26-pointers.md)):

```franko
struct X {
    int32 y = 0
    func get(view<X> self) -> int32 {
        return $(self).y
    }
    func set(mview<X> self, int32 v) -> void {
        $(self).y = v
    }
}
```

Rules:

- A method with no parameters is rejected: `Method 'X.foo' must have 'ptr<X> self' as its first parameter (also allowed: 'mview<X> self', 'view<X> self')`.
- A method whose first parameter is not named `self` is rejected with the same diagnostic.
- A method whose `self` parameter does not have type `ptr<X>`, `mview<X>`, or `view<X>` for the enclosing struct `X` is rejected: `Method 'X.foo' has a 'self' parameter of type T; it must be ptr<X> (or mview<X> or view<X>)`. This covers by-value `X self`, non-pointer `int32 self`, and `ptr<Y> self` for a different struct `Y`.
- Only the first parameter may be named `self`: `Only the first parameter of a method may be named 'self'`.
- Method overload identity **excludes `self`'s type**: a struct may not declare two methods with the same name and the same remaining parameter types but different `self` permissions.
- `self` is an ordinary pointer variable of its declared permission inside the body: `$(self).y` and `getptr($(self).y)` behave exactly like any other pointer variable. In particular `self.y` is invalid: member access requires a struct value, so fields are reached as `$(self).y` (see [Pointers](26-pointers.md)). Writing through a `view<X> self` is rejected like any other write through a view.
- The `self` slot is **implicitly const**. Reassigning it (`self = other`) is
  rejected with `Cannot assign to const variable 'self'`. Writing
  `const self` is allowed but redundant. `const` only makes the pointer slot
  immutable; the declared `ptr`/`mview`/`view` permission still governs the
  pointee. It is not part of the method signature or overload identity.
- A const struct receiver accepts only `view<X> self` methods. The hidden
  receiver argument is `getptr`/`getmview`/`getview(receiver)` matching the
  declared `self` type, so `ptr<X> self` and `mview<X> self` calls on a const
  value fail the const-pointer rule, while `view<X> self` is allowed.
- Method names may coincide with array intrinsic names (`init`, `memset`, ...). Dispatch keys off the receiver type, not the name: a struct-typed receiver calls the method, while an array-typed receiver calls the intrinsic (see [Array Intrinsics](23-array-intrinsics.md)).

## 28.3 Method Calls and Receivers

A method is invoked with member-call syntax. The receiver is passed as
the `self` argument, using `getptr`, `getmview`, or `getview` matching
the method's declared `self` type:

```franko
s.increment()              // local struct receiver
$(p).increment()       // explicit deref for ptr<X> receivers
xs[i].increment()          // array element receiver
s.inner.increment()        // field receiver
```

The creation intrinsic enforces the receiver's permission: calling a
`ptr<X> self` method through a `view` receiver is rejected
(`getptr(...) requires allocation rights (ptr) over its target`), while
a `view<X> self` method is callable through any receiver. When the
receiver is an lvalue reached through an inner pointer (for example
`$($(v)[0]).increment()` where the element is an `mview<X>`), that inner
pointer's own permission applies. Owning `ptr<X>` cannot be stored in an
array, struct, or tuple; see
[Owning Pointers in Aggregates](26-pointers.md#2513-owning-pointers-in-aggregates-are-forbidden).

Rules:

- The receiver must have struct type; otherwise the call is rejected: `Method call 'foo' is only valid on struct values, got type T`.
- An `ptr<X>` receiver is rejected with a hint: `Method call 'foo' is only valid on struct values, got type ptr<X>; use $(p).foo(...) for pointer receivers`. There is no implicit auto-deref.
- The receiver must be a storage-backed lvalue: a variable, field, array element, or `$(...)` chain (see [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md)). Call results are rejected: `Method call receiver must be a storage-backed lvalue: a variable, field, array element, or $(...) expression`.
- Unknown methods and mismatched arguments are rejected: `No matching overload for method call 'X.foo(...)'`, with user argument types only (`self` is implicit and excluded from the printed signature). A struct-typed receiver never falls back to array-intrinsic diagnostics, even when the name coincides with one: `s.init()` with no such method reports the method-call diagnostic.
- Arity diagnostics exclude `self`: `Method call to 'X.foo' has wrong arity: expected N argument(s) (excluding self), got M`.
- Array-typed receivers keep their intrinsic meaning (`b.data.init(n)`, `b.data.memset(0)`); struct-typed receivers call methods. The two namespaces coexist because a receiver cannot be both types at once.
- Methods are never callable as free functions: `X.foo(getptr(s))` and `foo(getptr(s))` are rejected as ordinary overload failures.

Ordinary methods support named and optional user arguments. `self` is hidden
and is never nameable, cannot have a default, and is never part of the call's
argument list:

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
c.add(self = 1)     // invalid: self is implicit
```

Operator methods are the exception: they are fixed-shape and reject both
defaults and named arguments. See
[Functions](19-functions.md#1915-named-and-optional-arguments).

## 28.4 Fields vs Methods Name Resolution

Fields and methods share member-call syntax but resolve in separate namespaces:

- `s.field` resolves as field access when `field` names a struct field.
- `s.name(args)` resolves as a method call when `name` matches a method overload for the supplied argument types.
- A method may not share its name with a field of the same struct: `Method 'X.foo' conflicts with field 'foo' of struct 'X'`.
- `s.unknown()` is rejected as `No matching overload for method call 'X.unknown(...)'`, which is also what keeps the historical `member_call_non_intrinsic` rejection for genuinely unknown members.

## 28.5 Overloading

Methods overload exactly like free functions: overload identity is name plus ordered parameter types; return type and parameter names are not part of identity (see [Functions](19-functions.md)).

```franko
struct Counter {
    int32 n = 0
    func add(ptr<Counter> self, int32 v) -> void {
        $(self).n = $(self).n + v
        return
    }
    func add(ptr<Counter> self, int32 a, int32 b) -> void {
        $(self).n = $(self).n + a + b
        return
    }
}
```

Rules:

- The implicit `self` parameter (whatever its permission) does not distinguish overloads; overloads must differ in their user parameters.
- Duplicate method signatures are rejected: `Duplicate declaration of method 'X.foo(...)' in struct 'X'`.
- Ambiguous arguments are rejected like free-function ambiguity: `Ambiguous call to overloaded method 'X.foo(...)': multiple overloads match equally well (argument constants fit several signatures, or arguments rely on the same number of pointer permission downgrades)`.

## 28.6 `self` Is a Reserved Keyword

`self` is a lexer keyword, reserved everywhere:

```franko
int32 self = 0          // invalid: parse error
func self() -> void {}    // invalid: parse error
struct self {}            // invalid: parse error
```

Rules:

- `self` cannot name a variable, parameter (except a method `self` parameter), field, function, struct, or method: ordinary declarations use `IDENTIFIER` and fail to parse on `self`.
- A free-function parameter named `self` parses (parameters accept `self` so method declarations share the rule) but is rejected semantically: `'self' is only valid as the first parameter of a struct method`.
- `self` as an expression (`self`, `getptr(self)`, `$(self)`) is valid only inside a method body, where it resolves to the `self` parameter. Outside a method it is rejected: `keyword 'self' is only valid inside a method body`.
- `x.self` parses (member names accept keywords) but always fails resolution. A method named `stdout` is an ordinary method like any other.

## 28.7 Scope Inside Method Bodies

A method body scopes exactly like a free-function body, plus the `self` parameter:

- Parameters (including `self`) are ordinary locals in the method's initial scope; locals may shadow field names (`int32 y` inside `increment` is unrelated to `$(self).y`).
- A method may call other methods on the same object only through an explicit receiver: `$(self).other()`.
- `return` is checked against the enclosing method, so void/non-void return rules apply unchanged (see [Functions](19-functions.md)).
- Name lookup inside a method follows ordinary lexical plus namespace rules; there is no implicit `this` field scope (see [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md)).

## 28.8 Methods and Pointer Checking

`self` is a pointer parameter with the declared permission:

- The receiver is a storage-backed lvalue, and `self` is non-null at method entry, so dereferencing it without a null guard is valid and produces no warning. Reassigning `self` is rejected by the implicit const rule.
- Pointer dereferences inside methods are tracked normally (see [Memory Safety Checking](32-memory-safety-checking.md)).

## 28.9 Restrictions Summary

- Methods are declared only inside struct bodies; no free-standing method declarations and no nested struct declarations.
- Every method must have `ptr<X> self`, `mview<X> self`, or `view<X> self` as its first parameter; only the first parameter may be named `self`.
- `self` is a reserved keyword; `self.y` is invalid (write `$(self).y`).
- The `self` slot is implicitly const; reassigning it is an error.
- Receivers must be struct-valued storage-backed lvalues; `ptr<X>` receivers require explicit `$(p).method(...)`.
- Methods are never callable as free functions; there is no implicit `this` field access.
- A method name may coincide with an array intrinsic name; struct receivers dispatch to the method while array receivers dispatch to the intrinsic.
- No method pointers, no virtual dispatch, no inheritance: methods behave like functions with an explicit receiver.

## 28.10 Operator Methods

A struct may declare operator methods using the reserved names:

```
OPERATOR_EQ OPERATOR_NEQ OPERATOR_GT OPERATOR_LT OPERATOR_GTEQ OPERATOR_LTEQ
OPERATOR_MOV OPERATOR_ADDMOV OPERATOR_SUBMOV OPERATOR_MULMOV OPERATOR_DIVMOV
OPERATOR_MODMOV OPERATOR_ORMOV OPERATOR_ANDMOV OPERATOR_XORMOV
```

Comparison methods must have exactly this shape:

```franko
func OPERATOR_EQ(view<X> self, view<X> other) -> uint8
```

Assignment methods must have exactly this shape:

```franko
func OPERATOR_MOV(ptr<X> self, view<T> other) -> void
```

Rules:

- Only struct methods may use these names. A free function with an
  `OPERATOR_*` name is rejected.
- An invalid operator method signature is a compile error at the
  declaration even if the operator is never used.
- Comparison methods require two parameters, the first named `self` and
  typed `view<X>`, the second typed `view<X>` and not named `self`, and
  return `uint8`.
- Assignment methods require two parameters, the first named `self` and
  typed `ptr<X>`, the second a `view<T>` for any non-void `T` and not
  named `self`, and return `void`.
- Each operator has its own method; there is no implicit `!=` from
  `==`, and no implicit compound form from a plain operator.
- Operator methods are still ordinary methods and remain callable with
  member-call syntax: `a.OPERATOR_EQ(getview(b))`.

Operator syntax is shorthand for a method call:

```franko
a == b       // a.OPERATOR_EQ(getview(b))
a = b        // a.OPERATOR_MOV(getview(b))
a += b       // a.OPERATOR_ADDMOV(getview(b))
```

Rvalue operands are borrowed for the duration of the full expression (see
[Operators](13-operators.md#1310-struct-operator-overloading)).

The `other` parameter is call-scoped: it may only be used during the operator
call. Storing it into caller-owned or global storage is rejected; storing it
in a local variable is allowed because the local dies before the operator
call returns (see
[Memory Safety Checking](32-memory-safety-checking.md#299-dangling-pointer-checking)).
