# 18. Declarations

A variable declaration introduces a named storage location.

Basic declaration syntax:

```franko
int x;
int32 y;
uint8 b;
uint32 i;
```

Array and pointer declarations:

```franko
[int] xs;
[int32] ys;
[uint8, 128] buffer;
[[byte, 20], 12] grid;

ptr<int> p;
ptr<int32> q;
ptr<[int]> array_ptr;
```

Declaration initializers are syntax sugar:

```franko
int32 x = 1;
```

is equivalent to:

```franko
int32 x;
x = 1;
```

The initializer must obey normal assignment rules and is only valid where the resulting assignment is legal.

## 18.1 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

## 18.2 Shadowing

A variable may be redeclared in an inner block, creating a new variable that shadows the outer one.

## 18.3 Use Before Declaration

Local variables must be declared before they are used.

## 18.4 Global Variable Declarations

Variable declarations are allowed at global scope. Global variables are statically allocated.

Two different initializer forms exist for declarations, and they follow different rules at global scope.

**Constant initializers.** A global declaration may end with `= constExpr`:

```franko
int32 g = 1 + 2
uint64 mask = 1 << 8
```

The initializer must be a constant expression built from literals, so it is folded at compile time and may not reference variables. Only primitive globals accept this form: struct, array, and ptr globals reject `= constExpr` as a type mismatch.

**Static address initializers.** A pointer-typed global may be
initialized with `null`, with `getptr`/`getmview`/`getview` over another
global variable, or with a string literal (which is a view over
immutable constant storage; see [String Literals](23-string-literals.md)):

```franko
[byte, 5] greeting
view<[byte, 5]> v = getview(greeting)
view<[byte, 5]> s = "hello"
ptr<[byte, 5]> p = null
```

The initializer must be a statically representable address of the right
type (a permission downgrade is allowed, an upgrade is not).

An array literal may initialize a pointer-typed global (it is a
`view<[T, N]>` value over immutable constant storage, like a string
literal):

```franko
view<[int32, 4]> G = [1, 2, 3, 4]   // valid
```

**Ordinary declaration initializer sugar.** The `= expr` form described in this chapter and in [Assignment](16-assignment.md), when the expression is not constant, is equivalent to an executable assignment statement. Executable statements are not allowed at global scope, so this form is invalid on global declarations:

```franko
int32 g = h                     // invalid: the initializer must be constant
[int32, 3] xs = [1, 2, 3]  // invalid: arrays are not assignable from literals
[byte, 6] s = "hello"        // invalid: strings cannot initialize arrays
```

The constant-initializer, static-address, and array-literal forms above
are the only ways to initialize a global at its declaration.

## 18.5 Uninitialized Values

A **local** declaration without an initializer default-initializes the variable:

- primitive integer variables start with an indeterminate value; reading one before its first assignment is undefined behavior;
- static array elements start with indeterminate values, except pointer elements, which are null;
- dynamic arrays start empty (length 0, no element storage) and must be initialized with `init` or `init_zero` before indexed use;
- **pointer storage is guaranteed null-initialized**: pointer variables, pointer struct fields, pointer tuple elements, pointer static-array elements, and pointer elements of initialized dynamic arrays are all null (see [Pointers](25-pointers.md));
- **enum storage is default-initialized to the first variant**: enum variables, enum struct fields, enum tuple elements, enum static-array elements, and enum elements of initialized dynamic arrays all start as the enum's first declared variant, even when its backing value is not `0` (see [Enums](30-enums.md)).

There is no implicit zero-initialization for locals other than pointer storage. Use `init_zero` for zeroed dynamic array storage.

**Global** declarations are statically allocated: primitives, static array elements, and aggregate storage without an explicit default start at zero (pointer storage starts null) before `main` runs, and dynamic arrays start empty. Global constant initializers and field defaults (see Global Variable Declarations above) are applied on top of that.

## 18.6 Struct Fields

Struct fields are declared with the same `type name` syntax as variables, inside a top-level `struct` declaration:

```franko
struct X {
    int32 num = 0
    [int32, 4] xs
    ptr<Y> next
}
```

Field defaults must be constant expressions on primitive fields only, and duplicate field names are rejected.

The complete struct reference — field rules, by-value cycles, assignability, member access, and globals — is in [Structs](26-structs.md).

Tuple declarations and tuple initialization are covered in
[Tuples](28-tuples.md).

## 18.7 Aggregate Field Initializers

A local struct or tuple declaration may initialize individual fields with a
braced field list immediately after the declared name:

```franko
Literal lit { x = 0, def = 1 }
Buffer b { cap = 20 }
(int32 first, int32 second) tup { first = 1, second = 2 }
(int32, int32) pair { 0, 1 }
(int32, int32) partial { 0 }
```

This is declaration sugar for a declaration followed by one ordinary field
assignment per named or positional entry:

```franko
Literal lit
lit.x = 0
lit.def = 1
```

Rules:

- struct fields are always named, tuple entries are named or positional, and
  order is free for named entries; duplicate or unknown names are rejected;
- positional entries are available only for all-unnamed tuple declarations,
  fill the first N elements, may be partial, and may not exceed the element
  count; positional and named entries cannot be mixed;
- omitted fields keep the declaration's default-constructed state;
- no temporary aggregate value is created and no `OPERATOR_MOV` is invoked;
- the form works for pointer-only aggregates because each field is assigned
  directly under ordinary assignment rules;
- an array field still cannot be assigned from another array or a literal;
- the brace must follow the identifier immediately; a newline ends the
  declaration;
- the form is local-only and is invalid at global scope.

The complete aggregate initialization reference is in
[Aggregate Literals](29-aggregate-literals.md).
