# 30. Aggregate Literals

An **aggregate literal** constructs a struct or tuple value directly in an
expression. Franko has two aggregate literal forms: a struct literal and a
tuple literal. It also has a separate, local-only **field-wise declaration
initializer** that assigns individual fields of a newly declared aggregate
without constructing a temporary value.

```franko
struct Literal {
    int32 x
    int32 def = 2
}

Literal lit = Literal { x = 0, def = 1 }
```

Aggregate literals are expressions, but they are only valid in the specific
value-consuming contexts listed in [Allowed Expression Contexts](#306-allowed-expression-contexts).
They are local-only: no global aggregate initializers exist.

## 30.1 Struct Literal Expressions

A struct literal spells the struct type followed by a braced list of named
field initializers:

```franko
Literal { x = 0, def = 1 }
```

Rules:

- the literal creates a temporary `Literal` value;
- fields are always named; positional initialization such as
  `Literal { 0, 1 }` is invalid;
- field order is free;
- duplicate field names are errors;
- unknown field names are errors;
- omitted fields use the same default construction as a local declaration:
  - fields with defaults receive their default value;
  - pointer fields are null;
  - omitted fields without defaults remain indeterminate;
- `Literal {}` is legal and default-constructs the temporary;
- field initializer expressions are evaluated in source order;
- construction itself never calls `OPERATOR_MOV`;
- the temporary is created only if `Literal` is value-assignable.

Examples:

```franko
Literal lit = Literal { x = 0, def = 1 }   // legal
Literal lit = Literal { def = 1, x = 0 }   // legal: order free
Literal lit = Literal { x = 0 }            // legal: def gets default 2
Literal lit = Literal { def = 0 }          // legal: x remains uninitialized
Literal lit = Literal {}                   // legal: defaults applied
```

## 30.2 Tuple Literal Expressions

A tuple literal spells the tuple type followed by a braced list of named
element initializers:

```franko
(int32 first, int32 second) tup = (int32, int32) { _0 = 1, _1 = 2 }
(int32 first, int32 second) tup = (int32 x, int32 y) { x = 1, y = 2 }
```

Rules:

- the literal type spelling must be all-named or all-unnamed:
  - `(int32 x, int32 y) { x = ..., y = ... }` is legal;
  - `(int32, int32) { _0 = ..., _1 = ... }` is legal;
  - `(int32, int32 y) { _0 = ..., y = ... }` is illegal;
- when the literal type is all-named, initializer labels must be those names;
- when the literal type is all-unnamed, labels must be `_0`, `_1`, …, **or**
  every entry may be positional: `(int32, int32) { 0, 1 }`;
- positional entries fill `_0`, `_1`, … in source order;
- a positional list may be partial and fills the first N elements:
  `(int32, int32) { 0 }` initializes `_0` only;
- a positional list may not have more entries than the tuple has elements;
- positional and named entries cannot be mixed in one tuple initializer;
- positional entries are invalid for struct literals and named tuple
  spellings;
- tuple names are not part of tuple identity, so the resulting value assigns
  to any structurally identical tuple type;
- the tuple must be value-assignable; a tuple containing an array cannot be
  constructed as a literal;
- partial initialization is legal:
  - omitted pointer elements are null;
  - omitted non-pointer elements are indeterminate;
- `(int32, int32) {}` is legal and default-constructs the temporary tuple;
- a bare `(1, 2)` is not a tuple expression: the type must be spelled;
- `(uint32, int32) { 20, -1 }` is a legal all-unnamed positional literal;
- `(int32 x, int32 y) { 1, 2 }` is invalid: positional entries are not
  allowed for a named tuple spelling.

Examples:

```franko
(int32 first, int32 second) tup = (int32, int32) { _0 = 1, _1 = 2 }        // legal
(int32 first, int32 second) tup = (int32 x, int32 y) { x = 1, y = 2 }      // legal
(int32 first, int32 second) tup = (int32, int32 y) { _0 = 1, y = 2 }       // illegal
(int32 first, int32 second) tup = (int32, int32) { _1 = 2, _0 = 1 }        // legal: order free
(int32 first, int32 second) tup = (int32, int32) { _0 = 1 }                // legal: partial
(int32, int32) empty = (int32, int32) {}                                    // legal

(int32, int32) positional = (int32, int32) { 0, 1 }    // legal: _0 = 0, _1 = 1
(int32, int32) partial = (int32, int32) { 0 }          // legal: _0 = 0 only
(int32 x, int32 y) named = (int32 x, int32 y) { 0, 1 } // illegal: named spelling
(int32, int32) mixed = (int32, int32) { 0, _1 = 1 }    // illegal: mixed entries
(int32, int32) too_many = (int32, int32) { 0, 1, 2 }   // illegal: too many entries
```

Tuple literal element names follow the declaration-site alias rules of
[Tuples](29-tuples.md): the literal's own spelling determines the labels it
accepts, while the resulting value still has the same structural tuple type
as a differently named declaration.

## 30.3 Field-Wise Declaration Initializers

A local declaration may initialize individual fields directly by placing a
braced field list immediately after the declared name:

```franko
Literal lit { x = 0, def = 1 }
Buffer b { cap = 20 }
(int32 first, int32 second) tup { first = 1, second = 2 }
(int32, int32) pair { 0, 1 }
(int32, int32) partial { 0 }
```

This is declaration initialization that behaves as one ordinary field
assignment per named field:

```franko
Literal lit
lit.x = 0
lit.def = 1
```

Rules:

- only struct and tuple declarations support this form;
- struct fields are always named;
- named tuple elements use their declared names;
- an all-unnamed tuple declaration may use positional entries instead:
  `(int32, int32) pair { 0, 1 }`;
- positional entries fill the first N elements and may be partial:
  `(int32, int32) pair { 0 }` initializes `_0` only;
- positional and named tuple entries cannot be mixed;
- positional entries may not exceed the tuple element count;
- field order is free;
- duplicate field names are errors;
- unknown field names are errors;
- omitted fields keep the declaration's default-constructed state;
- no temporary aggregate value is created;
- no `OPERATOR_MOV` is invoked;
- each named field is assigned directly using ordinary assignment rules;
- this form works for pointer-only aggregates;
- `Buffer b {}` is legal and equivalent to `Buffer b`;
- this form is local-only.

The brace must follow the identifier immediately. A newline before the brace
ends the declaration, so a following block is not consumed:

```franko
Literal lit
{
    // an ordinary block statement, not a field initializer
}
```

Because the field-wise form uses ordinary assignment, an array field still
cannot be assigned from another array or a literal:

```franko
struct Buffer {
    int32 cap
    [byte] buf
}

[byte] existing_arr

Buffer b { cap = 20 }                 // legal
Buffer b { cap = 20, buf = existing_arr }   // illegal: arrays are not assignable
```

## 30.4 Default Construction and Partial Initialization

Struct literal temporary construction is exactly local declaration
construction:

- fields with defaults receive their default value;
- pointer fields are null;
- omitted no-default non-pointer fields are indeterminate;
- named field initializer expressions are evaluated in source order.

Tuple literals have no field defaults:

- omitted pointer elements are null;
- omitted non-pointer elements are indeterminate;
- named element initializer expressions are evaluated in source order.

The field-wise declaration form also evaluates its named field expressions in
source order, but only after the declaration itself has default-constructed
the aggregate.

Reading an omitted indeterminate field or element before assigning it is
undefined behavior, exactly like reading any other uninitialized local.

## 30.5 Value Assignability

A type is **value-assignable** when:

- it is a primitive integer;
- it is an enum;
- it is a pointer;
- it is a struct whose fields are all value-assignable, recursively;
- it is a tuple whose elements are all value-assignable, recursively.

Rules:

- struct literal construction requires the struct to be value-assignable;
- tuple literal construction requires the tuple to be value-assignable;
- field-wise declaration initialization does not require the aggregate
  itself to be value-assignable; each named field only needs to be
  assignable under ordinary assignment rules.

Consequences:

```franko
struct Buffer {
    int32 cap
    [byte] buf
}

Buffer b { cap = 20 }                 // legal: field-wise
Buffer b = Buffer { cap = 20 }        // illegal: Buffer is not value-assignable
Buffer b { cap = 20, buf = arr }      // illegal: arrays are not assignable
Buffer b = existing_buffer            // may use OPERATOR_MOV if declared
```

`#is_value_assignable` is exactly the predicate aggregate literal
construction uses. It reports `0` for arrays and for structs or tuples with a
non-value-assignable part.

## 30.6 Allowed Expression Contexts

An aggregate literal expression is only valid as:

- the entire right-hand side of an assignment;
- the entire right-hand side of a declaration initializer;
- the entire return expression;
- an entire function or method call argument;
- an entire field value inside another aggregate literal.

It is not a general subexpression.

Valid:

```franko
Literal lit = Literal { x = 1 }
Literal lit
lit = Literal { x = 2 }
return Literal { x = 3 }
f(Literal { x = 4 })
Outer o = Outer { inner = Inner { x = 5 } }
```

Invalid:

```franko
Literal { x = 1 }.x       // illegal: literal is not a member-access base
Literal { x = 1 }         // illegal: standalone expression statement
1 + Literal { x = 1 }     // illegal: operand
cast<int32>(Literal {})   // illegal: cast operand
getview(Literal {})       // illegal: creation-intrinsic operand
```

The sole purpose of aggregate literals is constructing a value to assign,
return, or pass by value. Nested literals are allowed only as entire field
values of another aggregate literal.

## 30.7 Evaluation Order

Field initializer expressions are evaluated in the order they are written,
not in field declaration order. Field order is otherwise free.

```franko
Literal a = Literal { def = first(), x = second() }
// first() runs before second()
```

Nested aggregate literals evaluate their own fields in source order at the
point where the enclosing field value is evaluated.

## 30.8 `OPERATOR_MOV` Interaction

A struct that is value-assignable cannot declare `OPERATOR_MOV`; its
whole-value assignment is always the built-in field copy. Because aggregate
literal construction requires the aggregate to be value-assignable, this is
exactly the set of structs that can appear as a literal. The declaration

```franko
Literal lit = Literal { x = 0, def = 1 }
```

therefore means:

```franko
Literal lit                 // default-construct lit: defaults applied, pointers null

Literal temp                // temporary literal
temp.x = 0                  // explicit fields, source order
temp.def = 1

lit = temp                  // built-in whole-value assignment: field copy
```

Pointer-only structs cannot be constructed as literal values at all. They use
the field-wise declaration form, and may use `OPERATOR_MOV` only for an
ordinary non-literal whole-value assignment:

```franko
struct Buffer {
    int32 cap
    [byte] buf

    func OPERATOR_MOV(ptr<Buffer> self, view<Buffer> other) -> void {
        // custom copy
        return
    }
}

Buffer b { cap = 20 }                 // field-wise, no temporary
Buffer source { cap = 10 }            // field-wise, no temporary
Buffer copy = source                  // ordinary assignment: calls OPERATOR_MOV
Buffer bad = Buffer { cap = 20 }      // error: Buffer is not value-assignable
```

Truth table:

| Construct | Temporary aggregate? | Requires aggregate value-assignable? | Uses `OPERATOR_MOV`? |
|---|---|---|---|
| `Literal lit = Literal { x = 0 }` | yes | yes | no — value-assignable structs cannot declare it; built-in field copy |
| `Literal lit { x = 0 }` | no | no | no — direct field assignments |
| `Buffer b { cap = 20 }` | no | no | no |
| `Buffer b = Buffer { cap = 20 }` | yes | yes → **error** | irrelevant; the literal is already illegal |
| `Buffer b = existing_buffer` | no | depends on the assignment rule | can use `OPERATOR_MOV` if declared/applicable |

Const aggregates follow the same forms:

```franko
const Literal lit = Literal { x = 1 }   // one-time const initialization
const Literal fieldwise { x = 2 }       // field-wise const initialization
```

See [Declarations](18-declarations.md#188-const-storage).

## 30.9 Pointer-Only Aggregates and Out-Parameters

A struct or tuple with an array field or element is pointer-only: it has no
built-in whole-value assignment and cannot be constructed as an aggregate
literal value. Use the field-wise declaration form for local initialization:

```franko
struct Buffer {
    int32 cap
    [byte] buf
}

Buffer b { cap = 20 }                 // legal: direct per-field initialization
```

A pointer-only aggregate also cannot be a return type. A function that would
return one is rejected with guidance to return a pointer or use an
out-parameter:

```franko
func foo() -> Buffer { ... }          // error at declaration

func foo(ptr<Buffer> outbuf) -> int32 {
    // do something through outbuf
    return 0
}
```

The same rule applies to a tuple containing an array:

```franko
func make() -> (int32, [int32, 3]) { ... }     // error at declaration
```

## 30.10 Global-Scope Restriction

Aggregate literals and field-wise declaration initializers are local-only.
Global scope remains declaration-only, and its initializer forms are
unchanged: constant expressions, statically representable addresses, string
literals, and array literals.

Invalid:

```franko
Literal global_lit = Literal { x = 1 }     // invalid: global aggregate literal
Literal global_lit { x = 1 }               // invalid: global field initializer
```

Initialize a global aggregate in an initializer function called from `main`,
or use the ordinary global forms where they apply.

## 30.11 Diagnostics and Invalid Examples

Required diagnostics include:

- duplicate aggregate field initializer;
- unknown struct field;
- unknown tuple element;
- tuple literal type mixing names and default names;
- positional entries on a struct literal or a named/mixed tuple spelling;
- positional entries exceeding the tuple element count;
- positional and named tuple entries mixed in one initializer;
- aggregate literal of non-value-assignable type;
- aggregate literal used outside an allowed context;
- non-value-assignable return type with pointer/out-parameter guidance;
- field-wise initializer on a non-struct/non-tuple declaration;
- field-wise assignment of an array field;
- global aggregate literal or global field initializer rejection.

Examples:

```franko
Literal { x = 1 }.x                         // aggregate literal outside an allowed context
Literal { x = 1 }                           // aggregate literal outside an allowed context
1 + Literal { x = 1 }                       // aggregate literal outside an allowed context

Buffer b = Buffer { cap = 20 }              // non-value-assignable literal

(int32, int32 y) t = (int32, int32 y) {
    _0 = 1,
    y = 2
}                                           // mixed tuple literal names

(int32, int32) t = (int32, int32) { _0 = 1, _2 = 2 }   // unknown tuple element
(int32, int32) t = (int32, int32) { _0 = 1, _0 = 2 }   // duplicate tuple element

(int32 x, int32 y) t = (int32 x, int32 y) { 0, 1 }        // positional named-tuple entry
(int32, int32) t = (int32, int32) { 0, 1, 2 }             // too many positional entries
(int32, int32) t = (int32, int32) { 0, _1 = 1 }           // mixed entries
int32 x { field = 1 }                       // field initializer requires a struct or tuple declaration
```

## 30.12 Complete Example

```franko
import "franko/stdio"

struct Literal {
    int32 x
    int32 def = 2
}

struct Inner {
    int32 value = 7
}

struct Outer {
    Inner inner
    int32 ok = 1
}

struct Buffer {
    int32 cap
    [byte] buf
}

func make_literal() -> Literal {
    return Literal { x = 4, def = 5 }
}

func main() -> int32 {
    Literal a = Literal { def = 1, x = 0 }
    Literal b { def = 3, x = 2 }
    Literal c = make_literal()

    (int32 left, int32 right) pair = (int32, int32) { _1 = 2, _0 = 1 }
    (int32, int32) positional = (int32, int32) { 5, 6 }
    (int32, int32) partial { 7 }
    (int32 x, int32 y) named = (int32 x, int32 y) { x = 3, y = 4 }

    Outer outer = Outer { inner = Inner { value = 42 }, ok = 2 }
    Buffer buf { cap = 20 }

    stdout(a.x + a.def)              // 1
    stdout(b.x + b.def)              // 5
    stdout(c.x + c.def)              // 9
    stdout(pair.left + pair.right)   // 3
    stdout(positional._0 + positional._1)   // 11
    stdout(partial._0)               // 7
    stdout(named.x + named.y)        // 7
    stdout(outer.inner.value + outer.ok)   // 44
    stdout(buf.cap)                  // 20

    return 0
}
```
