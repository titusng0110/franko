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

Declaration initialization is its own statement form:

```franko
int32 x = 1;
```

It declares `x` and initializes it once. Declaration initialization is the
only place a `const` variable can receive its value, and it is evaluated
exactly once as part of the declaration.

The initializer must obey normal assignment rules and is only valid where the resulting assignment is legal.

## 18.1 Duplicate Declarations

Declaring the same variable name twice in the same scope is illegal.

## 18.2 Shadowing

A variable may be redeclared in an inner block, creating a new variable that shadows the outer one.

## 18.3 Use Before Declaration

Local variables must be declared before they are used.

## 18.4 Global Variable Declarations

Variable declarations are allowed at global scope and inside namespace
bodies. Both are **global variables**: a namespace only changes the
qualified name, not the rules.

Global rules:

- **Every global is implicitly `const`.** The `const` keyword is accepted
  but redundant. A global cannot be assigned, compound-assigned,
  incremented, or written through a field or element access.
- **The type must be value-assignable:** a primitive integer, an enum, a
  pointer (`ptr<T>`, `mview<T>`, `view<T>`), or a struct/tuple whose parts
  are all value-assignable. Arrays, dynamic arrays, and pointer-only
  aggregates cannot be globals.
- A global is initialized once before `main` and never mutated after that.
- `ptr(global)` and `mview(global)` are rejected; `view(global)`
  is allowed. A global `view` therefore provides read-only access without
  exposing mutable storage.

**Default initialization.** A global without an initializer starts with its
type's default value: primitives `0`, pointers `null`, enums their first
variant, and structs/tuples recursively zero/default-initialized. Declared
primitive field defaults are applied on top of that zero state.

**Allowed initializers.** A global may be initialized with a static value:

- an integer constant expression for a primitive global;
- an enum variant reference for an enum global;
- `null` for a pointer global;
- a compile-time string expression for a pointer global;
- an array literal for a pointer global (a `view<[T, N]>` value over
  immutable constant storage);
- `view` over another global (a read-only static address);
- a struct or tuple aggregate literal whose entries are recursively static
  values, including nested aggregate literals, enum variants, `null`,
  compile-time strings, array literals, and `view` of globals.

Omitted fields in a global aggregate literal receive their declared default
or the type default (`0`, `null`, first enum variant, recursively). This is
the same zero/default state as an uninitialized global.

```franko
struct Point {
    int32 x
    int32 y = 5
    view<int32> link
}

enum<uint8> Mode { idle = 5, active = 9 }

int32 G = 7
Mode mode = Mode::active
Point origin = Point { x = 1 }          // y = 5, link = null
Point blank                             // x = 0, y = 5, link = null
(int32, int32) pair = (int32, int32) { 4 }   // _0 = 4, _1 = 0
view<int32> g_view = view(G)
view<[byte, 5]> greeting = "hello"
view<[int32, 3]> numbers = [1, 2, 3]

namespace util {
    int32 offset = 100                  // namespace global, same rules
}
```

`Point origin { x = 1 }` field-wise initialization remains local-only;
global aggregates use the `Point origin = Point { ... }` literal form.

**Rejected initializers.** Runtime expressions are not allowed at global
scope. Calls, casts, dereferences, indexing, member reads, and reads of
other variables are compile errors:

```franko
int32 a = otherGlobal               // invalid: reads a runtime global
Point p = Point { x = readInput() } // invalid: runtime call
int32 b = cast<int32>(someValue)    // invalid: runtime cast
```

Executable statements are not allowed at global scope.

## 18.5 Uninitialized Values

A **local** declaration without an initializer default-initializes the variable:

- primitive integer variables start with an indeterminate value; reading one before its first assignment is undefined behavior;
- static array elements start with indeterminate values, except pointer elements, which are null;
- dynamic arrays start empty (length 0, no element storage) and must be initialized with `init` or `init_zero` before indexed use;
- **pointer storage is guaranteed null-initialized**: pointer variables, pointer struct fields, pointer tuple elements, pointer static-array elements, and pointer elements of initialized dynamic arrays are all null (see [Pointers](26-pointers.md));
- **enum storage is default-initialized to the first variant**: enum variables, enum struct fields, enum tuple elements, enum static-array elements, and enum elements of initialized dynamic arrays all start as the enum's first declared variant, even when its backing value is not `0` (see [Enums](31-enums.md)).

There is no implicit zero-initialization for locals other than pointer storage. Use `init_zero` for zeroed dynamic array storage.

**Global** declarations are statically allocated and initialized before
`main` runs: primitives and aggregate storage without an explicit default
start at zero, pointer storage starts null, enums start at their first
variant, and struct/tuple storage is zero/default-initialized recursively.
Declared field defaults and global initializers are applied on top of that.
Globals cannot be arrays or dynamic arrays; those types must be local.

## 18.6 Struct Fields

Struct and tuple field declarations, field defaults, duplicate names, and
by-value cycles are described in [Structs](27-structs.md) and
[Tuples](29-tuples.md). A field uses the same `type name` syntax as a
variable declaration.

## 18.7 Aggregate Field Initializers

A local struct or tuple declaration may initialize individual fields with a
braced list. The field-wise form, positional tuple entries, and its
`OPERATOR_MOV` behavior are part of [Aggregate Literals](30-aggregate-literals.md).
It is declaration initialization and is local-only.

## 18.8 Const Storage

`const` is a storage modifier on variables, parameters, and globals. It is not
a type: it is not part of a function signature, overload identity, or
`#what_type`.

Syntax:

```franko
const T x = expr          // local, plain declaration initialization
const P p { field = v }   // local, field-wise declaration initialization
const T G = globalInit    // global
func f(const T p) -> ...  // parameter, including const self
```

`const` is legal only when the declared type is value-assignable:

- a primitive integer;
- an enum;
- a pointer (`ptr<T>`, `mview<T>`, `view<T>`);
- a struct all of whose fields are value-assignable, recursively;
- a tuple all of whose elements are value-assignable, recursively.

It is illegal on static arrays, dynamic arrays, structs or tuples containing
an array or another non-value-assignable part, `void`, and struct fields.
Using `const` on an invalid type is an error, for example:

```text
Const variable 'xs' has type '[int32]'; const is only allowed for value-assignable types
```

The rules:

1. **No assignment.** A const variable cannot be the target of `=` or a
   compound assignment (`+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`), and
   `x++`/`x--` are rejected for the same reason. A field or element of a const
   variable is also const storage. A dereference starts a new storage region:
   `$(p) = v` is allowed when `p` is a const pointer; only `p = v` is
   rejected.
2. **No mutable pointer to const storage.** `ptr(constVar)` and
   `mview(constVar)` are rejected; `view(constVar)` is allowed.
3. **Declaration initialization only.** A const variable is initialized by
   its declaration. Both the plain form `const T x = expr` and the field-wise
   form `const P p { field = v }` are declaration initialization, not a
   separate assignment; no later assignment is allowed.
4. **Uninitialized const locals warn when not fully default-initialized.**
   `const int32 x` warns because `int32` has no default value. `const Point p`
   does not warn when every field is fully defaulted. Parameters and globals
   never receive this warning: parameters are initialized by the caller and
   globals are zero/default-initialized before `main`.
5. **No const fields.** `const int32 f` inside a struct is rejected:
   `Struct fields cannot be const`. A value-assignable struct may itself be
   const.
6. **Const struct receivers allow only `view<X> self` methods.** The hidden
   receiver argument uses `ptr`, `mview`, or `view` matching the
   declared `self` type, so `ptr<X> self` and `mview<X> self` calls on a const
   value are rejected and `view<X> self` is allowed. Direct field reads are
   allowed; field writes are rejected.
7. **Const parameters are allowed.** `const int32 a`, `const Point p`, and
   `const self` are legal when the parameter type is value-assignable.
   `const` is not part of overload identity or mangling: `f(const int32)` and
   `f(int32)` are duplicate declarations. A method's `self` slot is always
   implicitly const; writing `const self` is allowed but redundant.
8. **Every global is implicitly const.** The `const` keyword is optional
   and redundant on globals. Globals use the static initializer forms in
   [Global Variable Declarations](#184-global-variable-declarations) and
   cannot be assigned from any function. Namespace variables follow the
   same rule.

`#is_value_assignable` is exactly the type predicate `const` requires.
