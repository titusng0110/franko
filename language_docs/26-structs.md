# 26. Structs

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

Structs are nominal types: two distinct struct declarations are distinct types even when their field layouts are identical (see [Type Equality](10-type-equality.md)). This chapter is the complete struct reference; running examples live in [Examples](37-examples.md).

## 26.1 Declaring a Struct

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
- By convention struct names use `CapitalLetterCamelCase`; a struct whose name starts with `__` is internal to its namespace and warns when named from outside it. See [Naming Conventions](36-naming-conventions.md).

A struct body contains field declarations, method declarations (see [Methods](27-methods.md)), and `#if` conditionals that select fields or methods. Assignments, statements, nested functions outside methods, and nested struct declarations are invalid inside a struct body.

## 26.2 Field Declarations

A field declaration uses the same `type name` syntax as a variable declaration, plus an optional constant default:

```franko
struct Buffer {
    [int32] data
    int32 size = 0
}
```

Rules:

- Field types may be: primitive integer types, enum types, static arrays (nested static arrays included), dynamic arrays, pointer types (`ptr<T>`, `mview<T>`, `view<T>`), tuple types, and other declared struct types.
- `void` is not a valid field type, and a field type naming an undeclared struct is rejected.
- Dynamic array fields are allocated explicitly with `field.init(n)` (see [Array Intrinsics](22-array-intrinsics.md)).
- Duplicate field names within a struct are rejected.
- Fields are kept in declaration order.
- Field names are ordinary identifiers; keywords cannot be field names. By convention they use `lower_case_snake_case`: a field whose name starts with `_` is private to the struct and warns when accessed from outside it. See [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md) and [Naming Conventions](36-naming-conventions.md).

## 26.3 Field Default Values

A field may declare a default value with `= constExpr`:

```franko
struct Counter {
    int32 count = 0
    uint32 limit = 100
}
```

Rules:

- Defaults must be constant expressions (`constExpr`) and are folded at compile time (see [Constant Folding](08-constant-folding.md)).
- Only primitive fields can have default values. Struct, array, and ptr fields reject `= constExpr` as a type mismatch.
- A folded default must fit the field's primitive type; an out-of-range default is rejected (for example `int8 x = 300`).

## 26.4 Using Struct Types

A struct name used in a type position is resolved against the struct namespace:

- Unknown struct names are rejected with an "unknown struct type" error.
- Field types may reference structs declared anywhere in the program, including structs declared later in the file.
- Struct types compose with the other type constructors: `Point p`, `[Point] xs`, `[Point, 4] grid`, `ptr<Point> pp`, and `[[Point, 3], 2] cells` are all valid declared types.

## 26.5 By-Value Cycles

A struct may not reach itself by value through its fields. The by-value edges are:

- a field whose type is a struct;
- a field whose type is a static array of structs (recursively, including nested static arrays);
- a field whose type is a tuple containing a struct by value (recursively through tuple elements).

Tuple element types are by-value edges for cycle checking: a struct cannot contain itself through a tuple field.

An infinite by-value chain would have no size, so such cycles are rejected at compile time:

```franko
struct Node {
    int32 v
    [Node, 4] kids    // invalid: static array of Node inside Node
}
```

A tuple edge has the same effect:

```franko
struct S {
    (int32, S) t     // invalid: tuple element S is a by-value edge
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

## 26.6 Assignability and Passing by Value

A type is **assignable** when a value of that type can participate in ordinary assignment with `=`:

- primitive integer types are assignable;
- pointer types (`ptr<T>`, `mview<T>`, `view<T>`) are assignable;
- a struct is assignable if and only if all of its fields are assignable, recursively;
- a tuple is assignable if and only if all of its elements are assignable, recursively.

Static arrays and dynamic arrays are not assignable, so a struct or tuple containing one is **pointer-only**. `void` cannot be a struct field or tuple element.

Consequences:

- **Whole-value assignment.** For an assignable struct, `a = b;` copies every field. For a pointer-only struct the built-in assignment is rejected (see [Assignment](16-assignment.md)); share the struct through a pointer (`ptr<X>` when ownership or lifecycle operations are needed, or `mview<X>`/`view<X>` for field access), or opt into custom whole-value assignment with `OPERATOR_MOV` (see [Operator Assignment](16-assignment.md#167-operator-assignment)). A value-assignable struct cannot declare `OPERATOR_MOV`; its whole-value assignment is always the built-in field copy.
- **Const structs.** A value-assignable struct may be declared `const`
  (`const Point p`). Its fields may be read, but not written. Only a
  `view<X> self` method may be called on a const struct; `ptr<X> self` and
  `mview<X> self` calls are rejected. Struct fields themselves cannot be
  `const` (`Struct fields cannot be const`). See
  [Declarations](18-declarations.md#188-const-storage).
- **Aggregate literals.** An assignable struct value can be constructed directly with `X { field = value, ... }`; the temporary requires the struct to be value-assignable. For pointer-only structs use the field-wise declaration form `X x { field = value }` instead. See [Aggregate Literals](29-aggregate-literals.md).
- **By-value parameters.** A parameter may have an assignable struct type (see [Functions](19-functions.md)); the argument is copied at the call.
- **By-value returns.** A function may return an assignable struct type.
- Diamond-shaped field graphs are fine: a struct with two fields of the same struct type is assignable as long as that type is assignable.

Arrays of structs follow the usual element rules: `[X, N]` is `memset`-able only when every field of `X` is `memset`-able (pointers excluded), and `memcpy`-able when every field is `memcpy`-able (pointers included). See [Array Intrinsics](22-array-intrinsics.md).

Structs are not integers: arithmetic, bitwise, shift, and logical operators reject whole structs, and `if`/`while` conditions reject bare struct values; `print` has no overload for a whole struct (see [Statements](17-statements.md)). The exception is the overloadable comparison operators (`==`, `!=`, `<`, `>`, `<=`, `>=`) and assignment operators (`=`, `+=`, ...), which use `OPERATOR_*` methods (see [Operators](13-operators.md#1310-struct-operator-overloading)). Operate on fields for everything else: `print(s.x)`.

## 26.7 Member Access and Field Lvalues

Member access uses dot syntax. Access chains compose with every storage form:

```franko
s.field               // local struct
$(p).field        // through a pointer
$(a.next).field   // another hop through a ptr field: deref the link first
xs[i].field           // element of a static or dynamic array
```

- `s.field` is a storage-backed lvalue exactly when its base expression is one, so field accesses can be assigned to: `s.field = v;`, `$(p).field = v;`, `xs[i].field = v;` (see [lvalues and storage-backed expressions](15-lvalues-and-storage-backed-expressions.md)).
- `getptr(s.field)` is valid and yields `ptr<T>` for the field's type `T`.
- A field may itself be an array, and array intrinsics work on fields:
  `b.data.init(4)`, `b.data.init_zero(n)`, `b.data.memset(0)`, `b.data.memcpy(getptr(other))`, `b.data[i]`, and `b.data.uninit()`.
- Struct methods also use member-call syntax (`s.increment()`); see [Methods](27-methods.md). Only struct methods and the built-in array intrinsics use member-call syntax.
- A field whose type is a tuple is accessed with the tuple's own element names; tuple elements are lvalues by the same base-expression rule. See [Tuples](28-tuples.md).

## 26.8 Initialization Semantics

- **Local declarations.** A local struct without an initializer is default-constructed: fields with defaults receive their default value; enum fields default to their enum's first variant; fields without defaults start with indeterminate values, like ordinary local variables, except pointer fields, which are null (see [Declarations](18-declarations.md)).
- **Declaration initialization.** `P b = a;` and `P c = f();` are valid for locals and copy the whole struct value as part of the declaration.
- **Aggregate literals.** `P p = P { field = value, ... };` default-constructs a temporary, assigns the named fields in source order, then assigns the whole temporary to the declaration target. Empty and partial literals are legal. The struct must be value-assignable to be constructed this way, even when it declares `OPERATOR_MOV`. See [Aggregate Literals](29-aggregate-literals.md).
- **Field-wise declaration initializers.** `P p { field = value, ... }` declares `p` and assigns each named field directly, without a temporary and without invoking `OPERATOR_MOV`. This form works for pointer-only structs.
- **Global declarations.** Global structs are statically allocated. Fields without explicit defaults start at zero (pointer fields start null, enum fields start at their enum's first variant); fields with defaults receive their default values. Global initialization happens before `main` runs.

Tuples follow the same rules for their elements: local tuple elements without a value are indeterminate, except pointer elements, which are null; global tuple storage is zero-initialized, and declaration initialization copies a whole tuple value.

## 26.9 Struct Globals

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

## 26.10 Restrictions Summary

- Structs are declared only at top level; no local or nested struct declarations.
- Struct methods use member-call syntax alongside the built-in array intrinsics (see [Methods](27-methods.md)).
- No operators on whole structs except overloadable comparisons and assignments: arithmetic, bitwise, shift, and logical operators reject them, and bare struct conditions are invalid; `print` has no struct overload — print individual fields instead. Comparisons and assignments use `OPERATOR_*` methods (see [Struct Operator Overloading](13-operators.md#1310-struct-operator-overloading)).
- Aggregate literals construct assignable struct values as expressions; literal construction requires the struct to be value-assignable. Local field-wise declaration initializers initialize any struct, including pointer-only structs, one field at a time.
- Field defaults are `constExpr` on primitive fields only.
- A struct may not reach itself by value (through struct fields, tuple elements, or static array elements); `ptr<...>` and dynamic-array fields break the cycle.
- A struct or tuple with any non-assignable element is pointer-only: built-in whole-value assignment, by-value parameters, and by-value returns are rejected; use a pointer (`ptr<X>` for ownership/lifecycle, or `mview<X>`/`view<X>` for access), or declare `OPERATOR_MOV` for custom whole-value struct assignment.
- Duplicate struct names and duplicate field names are rejected.
- The complete list of current language restrictions is in [Unsupported or Restricted Features](35-unsupported-or-restricted-features.md).
