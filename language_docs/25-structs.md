# 25. Structs

A struct is a user-defined aggregate type with named, ordered fields:

```franko
struct Point {
    int32 x
    int32 y = 0
}

Point p;
p.x = 3;
p.y = 4;
print(p.x + p.y);
```

Structs are nominal types: two distinct struct declarations are distinct types even when their field layouts are identical (see [Type Equality](28-type-equality.md)). This chapter is the complete struct reference; running examples live in [Examples](32-examples.md).

## 25.1 Declaring a Struct

Struct declarations are top-level items, alongside global variable declarations and function declarations:

```franko
struct X {
    int32 a
    int64 b = 123
    [int32, 4] xs
}
```

Rules:

- `struct` declarations may only appear at global scope; they cannot be declared inside functions.
- Separators between fields are optional, matching block syntax: a trailing separator before `}` is not required, and `;` and newlines may be mixed freely.
- Duplicate struct names are rejected. Each struct name denotes exactly one declared type.
- Struct names live in their own namespace: a struct may share its name with a variable or a function. The compiler emits a warning for such clashes.

A struct body contains field declarations and method declarations (see [Methods](26-methods.md)). Assignments, statements, nested functions outside methods, and nested struct declarations are invalid inside a struct body.

## 25.2 Field Declarations

A field declaration uses the same `type name` syntax as a variable declaration, plus an optional constant default:

```franko
struct Buffer {
    [int32] data
    int32 size = 0
}
```

Rules:

- Field types may be: primitive integer types, static arrays (nested static arrays included), dynamic arrays, pointers (`ptr<T>`), and other declared struct types.
- `void` is not a valid field type, and a field type naming an undeclared struct is rejected.
- Dynamic array fields are allocated explicitly with the allocation call (`field(n)`, see [Array Intrinsics](21-array-intrinsics.md)).
- Duplicate field names within a struct are rejected.
- Fields are kept in declaration order; that order is also the generated field order.
- Field names are ordinary identifiers; keywords cannot be field names.

## 25.3 Field Default Values

A field may declare a default value with `= constExpr`:

```franko
struct Counter {
    int32 count = 0
    uint32 limit = 100
}
```

Rules:

- Defaults must be constant expressions (`constExpr`) and are folded at compile time (see [Constant Folding](10-constant-folding.md)).
- Only primitive fields can have default values. Struct, array, and ptr fields reject `= constExpr` as a type mismatch.
- A folded default must fit the field's primitive type; an out-of-range default is rejected (for example `int8 x = 300`).

## 25.4 Using Struct Types

A struct name used in a type position is resolved against the struct namespace:

- Unknown struct names are rejected with an "unknown struct type" error.
- Field types may reference structs declared anywhere in the program, including structs declared later in the file.
- Struct types compose with the other type constructors: `Point p`, `[Point] xs`, `[Point, 4] grid`, `ptr<Point> pp`, and `[[Point, 3], 2] cells` are all valid declared types.

## 25.5 By-Value Cycles

A struct may not reach itself by value through its fields. The by-value edges are:

- a field whose type is a struct;
- a field whose type is a static array of structs (recursively, including nested static arrays).

An infinite by-value chain would have no size, so such cycles are rejected at compile time:

```franko
struct Node {
    int32 v
    [Node, 4] kids    // invalid: static array of Node inside Node
}
```

Indirection breaks the cycle and is allowed:

```franko
struct Node {
    int32 v
    ptr<Node> next        // valid: pointer edge
    [Node] kids       // valid: dynamic array edge
}
```

## 25.6 Assignability and Passing by Value

A type is **assignable** when a value of that type can participate in ordinary assignment with `=`:

- primitive integer types are assignable;
- pointer types `ptr<T>` are assignable;
- a struct is assignable if and only if all of its fields are assignable, recursively.

Static arrays, dynamic arrays, and `void` are not assignable, so a struct containing any of them is **pointer-only**.

Consequences:

- **Whole-value assignment.** For an assignable struct, `a = b;` copies every field. For an pointer-only struct the assignment is rejected (see [Assignment](14-assignment.md)); share the struct through `ptr<X>` and `getptr(s)` instead.
- **By-value parameters.** A parameter may have an assignable struct type (see [Functions](12-functions.md)); the argument is copied at the call.
- **By-value returns.** A function may return an assignable struct type.
- Diamond-shaped field graphs are fine: a struct with two fields of the same struct type is assignable as long as that type is assignable.

Arrays of structs follow the usual element rules: `[X, N]` is `memset`-able only when every field of `X` is `memset`-able (pointers excluded), and `memcpy`-able when every field is `memcpy`-able (pointers included). See [Array Intrinsics](21-array-intrinsics.md).

Structs are not integers: operators and `if`/`while` conditions reject whole structs, and `print` has no overload for a whole struct (see [Statements](19-statements.md)). Operate on fields instead: `print(s.x)`.

## 25.7 Member Access and Field Lvalues

Member access uses dot syntax. Access chains compose with every storage form:

```franko
s.field               // local struct
$(p).field        // through an pointer
$(a.next).field   // another hop through an ptr field: deref the link first
xs[i].field           // element of a static or dynamic array
```

- `s.field` is a storage-backed lvalue exactly when its base expression is one, so field accesses can be assigned to: `s.field = v;`, `$(p).field = v;`, `xs[i].field = v;` (see [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md)).
- `getptr(s.field)` is valid and yields `ptr<T>` for the field's type `T`.
- A field may itself be an array, and array intrinsics work on fields:
  `b.data(4)` (allocation shorthand for `b.data.init(4)`), `b.data.init_zero(n)`, `b.data.memset(0)`, `b.data.memcpy(getptr(other))`, `b.data[i]`, and `b.data.uninit()`.
- Struct methods also use member-call syntax (`s.increment()`); see [Methods](26-methods.md). Only struct methods and the built-in array intrinsics use member-call syntax.

## 25.8 Initialization Semantics

- **Local declarations.** A local struct without an initializer is default-constructed: fields with defaults receive their default value; fields without defaults start with indeterminate values, like ordinary local variables (see [Declarations](11-declarations.md)).
- **Declaration initializer sugar.** `P b = a;` and `P c = f();` are valid for locals and copy the whole struct value; the sugar desugars into a declaration plus a whole-value assignment.
- **Global declarations.** Global structs are statically allocated. Fields with defaults receive their defaults; fields without defaults are zero-initialized in the generated C++ (static storage). This is an implementation characteristic of the current backend; do not rely on it in portable code — give every field an explicit default or assign before use.

## 25.9 Struct Globals

A struct-typed global is declared like any other global:

```franko
struct Point {
    int32 x = 0
    int32 y = 0
}

Point origin;
int32 scale = 2;
```

- Global variable initializers (`= constExpr`) are for primitive globals only; a struct-typed global rejects `= constExpr` as a type mismatch.
- Struct globals are declared before `main` runs; executable statements remain invalid at global scope (see [Program Structure](03-program-structure.md)).

## 25.10 Backend Notes

The C++14 backend:

- emits each struct as a C++ `struct STRUCT_X { ... };` definition, preceded by a forward-declaration block (`struct STRUCT_X;`) so pointers may reference structs declared later in source order;
- orders struct definitions topologically over by-value field dependencies, so a struct used by value inside another is defined first (`ptr` and dynamic-array fields impose no ordering constraint);
- emits field defaults as C++ non-static data member initializers (NSDMI), e.g. `int32 num = 0;`;
- emits member access as `target.field`.
- emits methods as mangled free functions (`METH_f_OFSTRUCT_X_HASH_<8hex>`); see [Methods](26-methods.md).

## 25.11 Restrictions Summary

- Structs are declared only at top level; no local or nested struct declarations.
- Struct methods use member-call syntax alongside the built-in array intrinsics (see [Methods](26-methods.md)).
- No operators on whole structs: arithmetic, comparison, and conditions reject them; `print` has no struct overload — print individual fields instead.
- No struct literals: a struct value is built field-by-field, copied from another struct, or returned by a function.
- Field defaults are `constExpr` on primitive fields only.
- A struct may not reach itself by value (through struct fields or static array elements); `ptr<...>` and dynamic-array fields break the cycle.
- A struct with any non-assignable field is pointer-only: whole-value assignment, by-value parameters, and by-value returns are rejected; use `ptr<X>`.
- Duplicate struct names and duplicate field names are rejected.
- The complete list of current language restrictions is in [Unsupported or Restricted Features](30-unsupported-or-restricted-features.md).
