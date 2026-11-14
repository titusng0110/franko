# 20. Generics

Franko supports compile-time generics for structs and functions. Generic
declarations are templates: every use supplies explicit type and value
arguments, and the compiler expands the template into an ordinary concrete
declaration before type checking and code generation.

Generics are deliberately simple:

- `#generic` marks a struct or function declaration as a template;
- `typename` declares a type parameter;
- a primitive integer type declares a value parameter;
- every use spells its arguments explicitly (`Box<int32>`, `foo<int32>(...)`);
- there is no type-argument inference;
- generic templates may be overloaded when their signatures differ;
- unused templates are never checked or emitted.

## 20.1 Generic Structs

A generic struct declares its parameters after the struct name:

```franko
#generic struct Box<typename T> {
    T value
}

#generic struct Buffer<typename T, int32 N> {
    [T, N] data
    int32 count = N
}
```

Use a generic struct by writing the arguments after its name in a type
position:

```franko
Box<int32> b
Buffer<uint8, 64> buf
```

The arguments are bound at compile time. `Box<int32>` and `Box<uint8>` are
distinct concrete struct types with distinct storage layouts.

A value parameter must have one of the eight primitive integer types:

```franko
#generic struct Grid<int32 Rows, int32 Cols> {
    [int32, Cols] rows
}
```

A generic declaration must declare at least one parameter:

```franko
#generic struct Bad { int32 value }   // error: missing generic parameter list
```

Value parameters can be used wherever a compile-time constant integer is
expected: static array sizes, primitive field defaults, and other generic
value arguments.

## 20.2 Generic Functions

A generic function declares its parameters after the function name:

```franko
#generic func identity<typename T>(T value) -> T {
    return value
}

#generic func first<typename T, int32 N>(view<[T, N]> values) -> T {
    return $(values)[0]
}
```

Call a generic function with explicit arguments:

```franko
stdout(identity<int32>(7))
stdout(first<int32, 3>(values))
stdout(identity<int32>(value = 7))
```

Generic calls accept the same named and optional arguments as ordinary calls.
A default may use a generic value parameter; it becomes a concrete constant
when the template is instantiated:

```franko
#generic func plus<int32 N>(int32 a, int32 b = N) -> int32 {
    return a + b
}

plus<7>(3)          // 10
plus<7>(a = 3)      // 10
```

A generic function name used without arguments is an error unless a concrete
function with the same name is also declared:

```franko
#generic func choose<typename T>(T value) -> T { return value }

choose(1)        // error: generic function requires type arguments
```

## 20.3 Explicit Arguments Only

Franko never infers generic arguments from ordinary parameters or return
types:

```franko
#generic func add<typename T>(T a, T b) -> T { return a + b }

add<int32>(1, 2)    // valid
add(1, 2)           // error: generic function requires type arguments
```

Type arguments must be types. Value arguments must be compile-time constant
integer expressions:

```franko
#generic func size<int32 N>() -> int32 { return N }

size<5>()           // valid
size<1 + 2>()       // valid: folded to 3
size<x>()           // error: x is not a compile-time constant
```

Value arguments are checked against the declared value-parameter type with
the ordinary constant-fit rules:

```franko
#generic func small<uint8 N>() -> uint8 { return N }

small<255>()        // valid
small<256>()        // error: 256 does not fit uint8
small<-1>()         // error: -1 does not fit uint8
```

Negative values are allowed for signed value parameters, and may still be
rejected later if they are used as a static array size.

## 20.4 Generic Methods

A method may declare its own `#generic` parameter list. Generic methods are
declared inside a struct body and are called with member syntax:

```franko
struct Plain {
    int32 value

    #generic func convert<typename T>(ptr<Plain> self, T value) -> T {
        return value
    }
}

Plain p
stdout(p.convert<int32>(7))
```

The method's parameters are bound explicitly at each call; there is no
inference. A method may combine its own generic parameters with the parameters
of its enclosing generic struct:

```franko
#generic struct Box<typename T> {
    T value

    #generic func replace<typename U>(ptr<Box<T>> self, U value) -> U {
        return value
    }
}

Box<int32> b
stdout(b.replace<uint8>(7))   // T = int32 from the receiver; U = uint8
```

Inside a generic struct, the receiver's concrete type binds the struct
parameters, and the angle brackets at the call bind only the method's own
parameters. The method may use both sets in its signature, body, defaults, and
compile-time reflection. Different concrete struct instances produce distinct
concrete methods.

The method's generic parameter names must be unique and must not reuse an
enclosing generic struct parameter name. Value parameters use one of the eight
primitive integer types. A generic method must declare at least one generic
parameter.

Call syntax supports named and optional arguments, and defaults are resolved
after the method is instantiated:

```franko
struct Counter {
    int32 value

    #generic func add<int32 N>(ptr<Counter> self, int32 amount = N) -> void {
        $(self).value = $(self).value + amount
        return
    }
}

Counter c
c.add<3>()
c.add<3>(amount = 5)
```

Generic and concrete methods with the same name coexist; a call without angle
brackets selects only concrete methods, and a call with angle brackets selects
the matching generic template group. `#inline` and `#noinline` apply to generic
methods exactly as they do to generic functions, and `#generic` must precede
the inlining modifier.

Operator methods are fixed-shape and cannot be generic. `#generic` on a
reserved `OPERATOR_*` method is rejected.

A method template that is never instantiated is never checked and never
emitted. Expansion limits and recursion behavior match generic functions: at
most 1024 generic instances per compilation and an instantiation depth of 64.

## 20.5 Self References

Inside a generic struct body, every use of the generic struct name must spell
its type and value arguments explicitly. There is no injected-class-name
shorthand and no inference:

```franko
#generic struct Node<typename T> {
    T value
    view<Node<T>> next          // the current instance
    [Node<T>] children          // dynamic array of the same instance
}
```

A bare `Node` is rejected in every type position, including the receiver type
of a non-generic method:

```franko
#generic struct Node<typename T> {
    T value
    view<Node> next                 // error: generic Node needs arguments
    func get(view<Node> self) -> T { return $(self).value }   // error
}
```

A self reference with different arguments means a different instance:

```franko
#generic struct Link<typename T> {
    T value
    view<Link<uint8>> other
}
```

The current instance is always written with the struct's own parameters, such
as `Node<T>` or `Box<T, N>`. Concrete arguments such as `Link<uint8>` select the
corresponding instance. This distinction also applies inside generic methods:

```franko
#generic struct Box<typename T> {
    T value

    #generic func link<typename U>(ptr<Box<T>> self, ptr<Box<U>> other) -> void {
        return
    }
}
```

Here `Box<T>` is the receiver's instance, while `Box<U>` is the instance
selected by the method's `U`. When `U = T`, both spellings resolve to the same
generated struct type; otherwise they are different instances.

A normal non-generic struct is still written bare. Thus `ptr<Box>` always
means a non-generic `Box`, while `ptr<Box<T>>` means a generic instance.

## 20.6 Nested Generic Types

Generic arguments compose with the ordinary type constructors:

```franko
#generic struct Pair<typename A, typename B> {
    A first
    B second
}

Pair<int32, Box<uint8>> nested
view<[Pair<int32, uint8>]> pairs
ptr<Pair<int32, uint8>> pair_ptr
```

Only the outer type needs to be named at the use site; nested generic uses
are expanded as part of binding the arguments.

## 20.7 Aggregate Literals

A generic struct literal spells the instance type, for example
`Box<int32> { value = 7 }`. Literal construction and field-wise declaration
initialization follow the ordinary rules in
[Aggregate Literals](30-aggregate-literals.md); the instance must be
value-assignable, so pointer-only generic structs use the field-wise form.

## 20.8 Namespaces and Imports

Generic declarations follow ordinary namespace and import rules:

```franko
namespace util {
    #generic struct Box<typename T> {
        T value
    }

    #generic func make<typename T>(T value) -> Box<T> {
        return Box<T> { value = value }
    }
}

util::Box<int32> b = util::make<int32>(1)
```

A generic template may live in an imported file and be instantiated from any
file that imports it.

## 20.9 Compile-Time Reflection

Compile-time reflection runs after a template is instantiated, so type
predicates see the concrete argument:

```franko
#generic func describe<typename T>(T value) -> int32 {
    #if (#is_primitive(T)) {
        return 1
    } #else {
        return 0
    }
}
```

`describe<int32>(1)` selects the first branch; `describe<Point>(p)` selects
the second.

The same applies to `#what_type`, `#is_same_type`, string `+` folding, string
comparisons, and the `#IS_*` target constants:

```franko
#generic func spelling<typename T>() -> view<[byte]> {
    return #what_type(T)
}
```

Predicates may also be applied to values whose type is a type parameter:

```franko
#generic func has_default<typename T>(T value) -> uint8 {
    return #has_default_value(value)
}
```

An uninstantiated template is never analyzed, so its reflection is never
evaluated. A template that is instantiated with several arguments gets an
independent result for each instance.

## 20.10 Compile-Time Directives

`#panic` and `#warning` are compile-time directives. In a generic template
they fire only for surviving selected paths of an instantiated template:

```franko
#generic func check<typename T>() -> int32 {
    #if (#is_same_type(T, int32)) {
        #warning("instantiated " + #what_type(T))
        return 1
    } #else {
        #panic("unsupported " + #what_type(T))
    }
}
```

`check<int32>()` emits the warning. `check<uint8>()` emits the panic.
A directive in a template that is never instantiated is ignored, as is a
directive in an unselected `#if` branch.

## 20.11 Unconstrained Parameters and Per-Instance Checking

A type parameter is unconstrained: `T` may be any valid type. The template
body is checked only after substitution, so an operation must be valid for
each concrete argument:

```franko
#generic func add<typename T>(T a, T b) -> T {
    return a + b
}

add<int32>(1, 2)        // valid
add<Point>(p, q)        // error: + is not defined for struct values
```

A template whose body is invalid for every possible argument still compiles
as long as it is never instantiated:

```franko
#generic func unused<typename T>(T value) -> T {
    return value + value     // never checked
}
```

## 20.12 Recursion and Expansion Limits

Recursive generic functions work when the recursion reaches the same
concrete arguments again; the compiler reuses the instance it is already
building:

```franko
#generic func fact<int32 N>() -> int32 {
    #if (N <= 1) {
        return 1
    } #else {
        return N * fact<N - 1>()
    }
}

stdout(fact<5>())    // 120
```

The compiler limits expansion to 1024 generic instances per compilation and
an instantiation depth of 64. Exceeding either limit is a compile error.
Recursion with ever-changing arguments that never reaches a base case will
hit the depth limit.

## 20.13 Generic Template Signatures

Generic templates may be overloaded. A generic struct template is identified
by its name and the ordered types of its generic parameters:

```text
X<typename T, int32 N>   -> signature X<typename, int32>
X<typename T, typename U> -> signature X<typename, typename>
```

The template's fields and methods are not part of the signature. Two generic
struct templates with the same signature are duplicate declarations.

```franko
#generic struct X<typename T, int32 N> { T value }
#generic struct X<typename T, typename U> { T first; U second }

X<int32, 4> a        // first template: 4 is a value argument
X<int32, uint8> b    // second template: uint8 is a type argument
```

A generic function template is identified by its name, the ordered types of
its generic parameters, and the ordered ordinary parameter types:

```text
f<typename T>(T)              -> signature f<typename>(T)
f<int32 N>(int32)             -> signature f<int32>(int32)
f<typename T>(T, int32)       -> signature f<typename>(T, int32)
```

Generic parameter names and ordinary parameter names are not part of the
signature. The return type is not part of the signature either: two templates
that differ only in their return type are duplicates.

Concrete functions have their ordinary signature (`name` plus ordered
parameter types) and remain separate from generic templates:

```franko
#generic func f<typename T>(T value) -> T { return value }
func f(int32 value) -> int32 { return value }

f(1)            // concrete f(int32)
f<uint8>(1)     // generic instance
```

A generic struct name may not collide with a concrete struct or enum name in
the same scope.

## 20.14 Generic Call Resolution

A generic call supplies explicit type and value arguments. Those arguments
first select the generic templates that accept their kinds and values:

- a `typename` parameter accepts a type argument;
- a primitive value parameter accepts a compile-time integer argument that
  fits its declared type.

When several templates accept the same value argument through different value
parameter types, the narrowest fitting type wins, with signed preferred over
unsigned at equal width. If candidates remain tied, a generic struct use is
ambiguous and is rejected.

Every remaining generic function candidate is then instantiated, and ordinary
overload resolution chooses among the concrete instances using the actual
call arguments, including pointer permission downgrades, array view decay,
constant fitting, and optional arguments.

```franko
#generic func tag<typename T>(T value) -> int32 { return 1 }
#generic func tag<typename T>(T first, T second) -> int32 { return 2 }

tag<int32>(7)       // first template
tag<int32>(7, 8)    // second template
```

Different templates may produce instances with the same ordinary parameter
list when their generic argument lists differ; the generic arguments keep the
instances distinct.

## 20.15 Restrictions

The following are not supported in this version:

- generic enums;
- generic global variables;
- generic namespaces;
- a generic `main`;
- generic FFI-bridged functions;
- type-argument inference;
- generic operator methods;
- runtime generic representation, type erasure, vtables, or generic function
  values.

`#generic` is a declaration modifier and `typename` is a reserved keyword.
They cannot be used as identifiers.

`#generic` must precede `#inline`/`#noinline`:

```franko
#generic #inline func fast<typename T>(T value) -> T { return value }  // valid
#inline #generic func bad<typename T>(T value) -> T { return value }   // invalid
```

## 20.16 Grammar Disambiguation

A suffix of the form `<...>(` immediately after a name is parsed as a generic
call. A comparison chain that ends with a parenthesized operand must be
parenthesized explicitly:

```franko
foo<int32>(1)       // generic call

(a < b) > (c)       // comparisons
a < b > (c)         // parsed as a generic call; write (a < b) > (c)
```

## 20.17 Complete Example

```franko
import "franko/stdio"

#generic struct Pair<typename A, typename B> {
    A first
    B second

    func sum(view<Pair<A, B>> self) -> A {
        return $(self).first
    }
}

#generic func make_pair<typename A, typename B>(A a, B b) -> Pair<A, B> {
    return Pair<A, B> { first = a, second = b }
}

#generic func twice<int32 N>(int32 value) -> int32 {
    return value * N
}

func main() -> int32 {
    Pair<int32, uint8> p = make_pair<int32, uint8>(10, 20)
    stdout(p.first)
    stdout(p.second)
    stdout(p.sum())
    stdout(twice<3>(7))
    return 0
}
```

Output:

```text
10
20
10
21
```

## 20.18 Naming Conventions

Type parameters conventionally use short uppercase names: `T`, `U`, `K`, `V`.
Value parameters use short descriptive uppercase names such as `N`, `ROWS`,
or `COLS`. Generic struct names and generic function names follow the same
conventions as their concrete counterparts.
