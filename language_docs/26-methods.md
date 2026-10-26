# 26. Methods

A struct may declare methods: functions bound to the struct whose first parameter is explicitly `addr<X> self`. Methods are called with member-call syntax and lower to ordinary function calls with a synthesized `getaddr(receiver)` first argument (see [Structs](25-structs.md) for fields and [Functions](12-functions.md) for free functions).

```franko
struct X {
    int32_t y = 0

    func increment(addr<X> self) -> void {
        deref(self).y = deref(self).y + 1
        return
    }
}

func main() -> int32_t {
    X s
    s.y = 41
    s.increment()
    print(s.y)
    return 0
}
```

## 26.1 Method Declaration Syntax

A struct body contains field declarations and method declarations:

```franko
struct X {
    int32_t y = 0
    func increment(addr<X> self) -> void {
        deref(self).y = deref(self).y + 1
        return
    }
}
```

Rules:

- Methods are `func` declarations inside a `structDecl` body; separators between members are optional, matching field syntax.
- A method may declare any return type valid for free functions, including `void` (see [Functions](12-functions.md)).
- A method may declare additional parameters after `self`; they follow ordinary parameter rules.
- A method body follows ordinary function body rules, including return checking (see [Functions](12-functions.md)).
- Method names are ordinary identifiers; a method may not share its name with a field of the same struct, and duplicate method signatures are rejected (see [Declaration Type Validity](29-declaration-type-validity.md)).

## 26.2 The `self` Parameter

The first parameter of every method must be literally `addr<X> self`, where `X` is the enclosing struct:

```franko
struct X {
    int32_t y = 0
    func get(addr<X> self) -> int32_t {
        return deref(self).y
    }
}
```

Rules:

- A method with no parameters is rejected: `Method 'X.foo' must have 'addr<X> self' as its first parameter`.
- A method whose first parameter is not named `self` is rejected with the same diagnostic.
- A method whose `self` parameter does not have type `addr<X>` for the enclosing struct `X` is rejected: `Method 'X.foo' has a 'self' parameter of type T; it must be addr<X>`. This covers by-value `X self`, non-address `int32_t self`, and `addr<Y> self` for a different struct `Y`.
- Only the first parameter may be named `self`: `Only the first parameter of a method may be named 'self'`.
- `self` is an ordinary `addr<X>` variable inside the body: `deref(self).y`, `getaddr(deref(self).y)`, and even `self = other` behave exactly like any other address variable. In particular `self.y` is invalid: `self` has type `addr<X>`, and member access requires a struct value, so fields are reached as `deref(self).y` (see [Addresses](24-addresses.md)).
- Method names may coincide with array intrinsic names (`init`, `memset`, ...). Dispatch keys off the receiver type, not the name: a struct-typed receiver calls the method, while an array-typed receiver calls the intrinsic (see [Array Intrinsics](21-array-intrinsics.md)).

## 26.3 Method Calls and Receivers

A method is invoked with member-call syntax. The compiler prepends `getaddr(receiver)` as the `self` argument:

```franko
s.increment()              // -> METH_increment_OFSTRUCT_X_HASH_<8hex>((&s))
deref(p).increment()       // explicit deref for addr<X> receivers
xs[i].increment()          // array element receiver
s.inner.increment()        // field receiver
```

Rules:

- The receiver must have struct type; otherwise the call is rejected: `Method call 'foo' is only valid on struct values, got type T`.
- An `addr<X>` receiver is rejected with a hint: `Method call 'foo' is only valid on struct values, got type addr<X>; use deref(p).foo(...) for address receivers`. There is no implicit auto-deref.
- The receiver must be a storage-backed lvalue: a variable, field, array element, or `deref(...)` chain (see [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md)). Call results are rejected: `Method call receiver must be a storage-backed lvalue: a variable, field, array element, or deref(...) expression`.
- Unknown methods and mismatched arguments are rejected: `No matching overload for method call 'X.foo(...)'`, with user argument types only (`self` is implicit and excluded from the printed signature). A struct-typed receiver never falls back to array-intrinsic diagnostics, even when the name coincides with one: `s.init()` with no such method reports the method-call diagnostic.
- Arity diagnostics exclude `self`: `Method call to 'X.foo' has wrong arity: expected N argument(s) (excluding self), got M`.
- Array-typed receivers keep their intrinsic meaning (`b.data.init(n)`, `b.data.memset(0)`); struct-typed receivers call methods. The two namespaces coexist because a receiver cannot be both types at once.
- Methods are never callable as free functions: `X.foo(getaddr(s))` and `foo(getaddr(s))` are rejected as ordinary overload failures.
- A method call lowers to `SemanticFunctionCallNode` with a synthesized `getaddr(receiver)` first argument, so argument checking, return checking, and code generation reuse the ordinary function-call paths.

## 26.4 Fields vs Methods Name Resolution

Fields and methods share member-call syntax but resolve in separate namespaces:

- `s.field` resolves as field access when `field` names a struct field.
- `s.name(args)` resolves as a method call when `name` matches a method overload for the supplied argument types.
- A method may not share its name with a field of the same struct: `Method 'X.foo' conflicts with field 'foo' of struct 'X'`.
- `s.unknown()` is rejected as `No matching overload for method call 'X.unknown(...)'`, which is also what keeps the historical `member_call_non_intrinsic` rejection for genuinely unknown members.

## 26.5 Overloading

Methods overload exactly like free functions: overload identity is name plus ordered parameter types; return type and parameter names are not part of identity (see [Functions](12-functions.md)).

```franko
struct Counter {
    int32_t n = 0
    func add(addr<Counter> self, int32_t v) -> void {
        deref(self).n = deref(self).n + v
        return
    }
    func add(addr<Counter> self, int32_t a, int32_t b) -> void {
        deref(self).n = deref(self).n + a + b
        return
    }
}
```

Rules:

- The implicit `self` parameter (`addr<X>`) does not distinguish overloads; overloads must differ in their user parameters.
- Duplicate method signatures are rejected: `Duplicate declaration of method 'X.foo(...)' in struct 'X'`.
- Ambiguous constant arguments are rejected like free-function ambiguity: `Ambiguous call to overloaded method 'X.foo(...)': argument constants fit multiple overloads`.

## 26.6 `self` Is a Reserved Keyword

`self` is a lexer keyword, reserved everywhere:

```franko
int32_t self = 0          // invalid: parse error
func self() -> void {}    // invalid: parse error
struct self {}            // invalid: parse error
```

Rules:

- `self` cannot name a variable, parameter (except a method `self` parameter), field, function, struct, or method: ordinary declarations use `IDENTIFIER` and fail to parse on `self`.
- A free-function parameter named `self` parses (parameters accept `self` so method declarations share the rule) but is rejected semantically: `'self' is only valid as the first parameter of a struct method`.
- `self` as an expression (`self`, `getaddr(self)`, `deref(self)`) is valid only inside a method body, where it resolves to the `self` parameter. Outside a method it is rejected: `keyword 'self' is only valid inside a method body`.
- `x.self` parses (member names accept keywords) but always fails resolution, like `x.print`.

## 26.7 Scope Inside Method Bodies

A method body scopes exactly like a free-function body, plus the `self` parameter:

- Parameters (including `self`) are ordinary locals in the method's initial scope; locals may shadow field names (`int32_t y` inside `increment` is unrelated to `deref(self).y`).
- A method may call other methods on the same object only through an explicit receiver: `deref(self).other()`.
- `return` attaches to the enclosing method symbol, so void/non-void return rules apply unchanged (see [Functions](12-functions.md)).
- Name lookup inside a method follows ordinary lexical plus namespace rules; there is no implicit `this` field scope (see [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md)).

## 26.8 Methods and Address Checking

`self` is an `addr<X>` parameter:

- `self` enters pointing: the call synthesizes `getaddr(receiver)` over a storage-backed lvalue, so dereferencing `self` without a null guard is valid and produces no warning. Reassigning `self` (e.g. `self = null`) flows through ordinary assignment state tracking.
- Address dereferences inside methods are tracked normally (see [Memory Safety Checking](27-memory-safety-checking.md)). The checker is intra-procedural.

## 26.9 Backend Notes

Methods emit as free C++ functions with a mangled name:

| Franko construct | Generated C++ name |
|---|---|
| method `f` of struct `X` | `METH_f_OFSTRUCT_X_HASH_<8hex>` |
| method `self` parameter | `VAR_self` (`STRUCT_X*`) |

The 8-hex-digit suffix is the FNV-1a 32-bit hash of the canonical signature key (declared parameter types in order, including `self` as `addr<X>`, then the return type); see [Runtime and Backend Notes](31-runtime-and-backend-notes.md).

- The mangled name is namespace-qualified like `FUNC_` names: a method of `util::Counter` emits as e.g. `NS_util::METH_bump_OFSTRUCT_Counter_HASH_<8hex>` and is called as `NS_util::METH_bump_OFSTRUCT_X_HASH_<8hex>((&VAR_c))` from outside the namespace.
- `s.method(args)` emits as `METH_method_OFSTRUCT_X_HASH_<8hex>((&recv), args...)`, with each user argument cast to its parameter type to pin Franko overload resolution, exactly like free-function calls.
- Struct definitions emit no members for methods; prototypes emit in the function prototype phase (free functions first, then methods of the same namespace) and definitions emit in the function definition phase.
- A method named `main` mangles to `METH_main_OFSTRUCT_X_HASH_<8hex>` and cannot collide with the entry-point `main`.

## 26.10 Restrictions Summary

- Methods are declared only inside struct bodies; no free-standing method declarations and no nested struct declarations.
- Every method must have `addr<X> self` as its first parameter; only the first parameter may be named `self`.
- `self` is a reserved keyword; `self.y` is invalid (write `deref(self).y`).
- Receivers must be struct-valued storage-backed lvalues; `addr<X>` receivers require explicit `deref(p).method(...)`.
- Methods are never callable as free functions; there is no implicit `this` field access.
- A method name may coincide with an array intrinsic name; struct receivers dispatch to the method while array receivers dispatch to the intrinsic.
- No method pointers, no virtual dispatch, no inheritance: methods are syntactic sugar for functions with an explicit receiver.
