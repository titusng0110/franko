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

Methods of a generic struct use the struct's parameters directly:

```franko
#generic struct Counter<typename T> {
    T value

    func add(ptr<Counter> self, T amount) -> void {
        $(self).value = $(self).value + amount
        return
    }

    func get(view<Counter> self) -> T {
        return $(self).value
    }
}

Counter<int32> c
c.add(5)
stdout(c.get())
```

A method cannot declare its own independent generic parameter list in this
version:

```franko
struct Plain {
    int32 value

    #generic func bad<typename T>(ptr<Plain> self, T v) -> T { return v }
    // error: independent generic methods are not supported
}
```

Method overload resolution, `self` permissions, and operator methods work
exactly as they do on concrete structs. The generated concrete instance is an
ordinary struct.

## 20.5 Self References

Inside a generic struct body, the bare struct name and a use with the current
arguments both mean the current instantiation:

```franko
#generic struct Node<typename T> {
    T value
    view<Node> next          // view<Node<T>>
    [Node<T>] children       // dynamic array of the same instance
}
```

A self reference with different arguments means a different instance:

```franko
#generic struct Link<typename T> {
    T value
    view<Link<uint8>> other
}
```

Self references are resolved before the generated declaration is checked, so
`view<Node>` in `Node<int32>` becomes `view<Node__...>`.

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

A generic struct literal spells the arguments in the type:

```franko
Box<int32> b = Box<int32> { value = 7 }
Pair<int32, uint8> p = Pair<int32, uint8> { first = 1, second = 2 }
```

As with concrete structs, a literal requires the instance to be
value-assignable. A generic struct with an array field is pointer-only, so use
the field-wise declaration form instead:

```franko
#generic struct Buffer<typename T, int32 N> {
    [T, N] data
}

Buffer<int32, 4> b { }      // valid: direct field initialization
```

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

The same applies to `#what_type`, `#is_same_type`, `#strcmp`, `#strcat`, and
the `#IS_*` target constants:

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
        #warning(#strcat("instantiated ", #what_type(T)))
        return 1
    } #else {
        #panic(#strcat("unsupported ", #what_type(T)))
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

## 20.13 Overloading Rules

Generic template overloading is not supported in this version. A namespace
may contain at most one generic function template with a given name,
regardless of its parameter list:

```franko
#generic func f<typename T>(T value) -> T { return value }
#generic func f<int32 N>() -> int32 { return N }
// error: duplicate generic template 'f'
```

Concrete functions may share a name with a generic template. A call with
explicit arguments uses the template; a call without arguments uses the
concrete overloads:

```franko
#generic func f<typename T>(T value) -> T { return value }
func f(int32 value) -> int32 { return value }

f(1)            // concrete f(int32)
f<uint8>(1)     // generic instance
```

A generic struct name may not collide with a concrete struct or enum name in
the same scope.

## 20.14 Restrictions

The following are not supported in this version:

- generic enums;
- generic global variables;
- generic namespaces;
- a generic `main`;
- generic FFI-bridged functions;
- type-argument inference;
- generic template overloading;
- independent generic methods (methods with their own parameter list);
- runtime generic representation, type erasure, vtables, or generic function
  values.

`#generic` is a declaration modifier and `typename` is a reserved keyword.
They cannot be used as identifiers.

`#generic` must precede `#inline`/`#noinline`:

```franko
#generic #inline func fast<typename T>(T value) -> T { return value }  // valid
#inline #generic func bad<typename T>(T value) -> T { return value }   // invalid
```

## 20.15 Grammar Disambiguation

A suffix of the form `<...>(` immediately after a name is parsed as a generic
call. A comparison chain that ends with a parenthesized operand must be
parenthesized explicitly:

```franko
foo<int32>(1)       // generic call

(a < b) > (c)       // comparisons
a < b > (c)         // parsed as a generic call; write (a < b) > (c)
```

## 20.16 Complete Example

```franko
import "franko/stdio"

#generic struct Pair<typename A, typename B> {
    A first
    B second

    func sum(view<Pair> self) -> A {
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

## 20.17 Naming Conventions

Type parameters conventionally use short uppercase names: `T`, `U`, `K`, `V`.
Value parameters use short descriptive uppercase names such as `N`, `ROWS`,
or `COLS`. Generic struct names and generic function names follow the same
conventions as their concrete counterparts.
