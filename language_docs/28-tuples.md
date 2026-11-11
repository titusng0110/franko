# 28. Tuples

A tuple is an ordered aggregate of two or more values. Tuples are useful for
returning several values from a function or grouping a fixed set of related
fields without declaring a named struct.

```franko
(int32 val, int32 err) result
(int32, int32) pair
(uint32 index, int32 error) allocated
```

Tuple element names are optional. A tuple without names uses `_0`, `_1`, and
so on.

---

## 28.1 Tuple Syntax

A tuple type is a parenthesized, comma-separated list of element types:

```franko
(int32, int32)
(int32 val, int32 err)
(int32 val, uint8)
```

Rules:

- at least two elements are required; `(int32)` is not a tuple type;
- each element has a type;
- each element may carry an optional declaration-site name;
- tuple types may appear wherever an ordinary declared type is allowed;
- `void` is not a valid tuple element type;
- an owning `ptr<T>` may not appear in a tuple element, recursively; see [Owning Pointers in Aggregates](25-pointers.md#2513-owning-pointers-in-aggregates-are-forbidden).

A tuple declaration uses the tuple type followed by the variable name:

```franko
(int32 val, int32 err) res
(int32, int32) lazy
(uint hello, int world) a
```

A tuple literal spells the tuple type followed by a braced field list:

```franko
(int32, int32) pair = (int32, int32) { _0 = 1, _1 = 2 }
(int32 first, int32 second) named =
        (int32 x, int32 y) { x = 1, y = 2 }
```

The literal type spelling must be all-named or all-unnamed; a mixed spelling
such as `(int32, int32 y) { _0 = 1, y = 2 }` is invalid. Named entries use
the spelling's names. An all-unnamed literal also accepts positional entries,
which fill the first N elements in order and may be partial:

```franko
(int32, int32) pair = (int32, int32) { 0, 1 }
(int32, int32) partial = (int32, int32) { 0 }
```

Positional and named entries cannot be mixed, positional entries are invalid
for named tuple spellings, and the positional count may not exceed the tuple
size. Order is free for named entries, partial and empty literals are legal,
and omitted pointer elements are null. Constructing a tuple literal requires
the tuple to be value-assignable. See
[Aggregate Literals](29-aggregate-literals.md).

A bare parenthesized value list such as `(1, 2)` is not a tuple expression:
the type must be spelled and entries must be named or positional.

---

## 28.2 Element Names

Element names are aliases for one declaration or type use. They are not part
of the tuple type.

```franko
(int32 val, int32 err) res
res.val = 1
res.err = 2

(int32, int32) lazy
lazy._0 = 3
lazy._1 = 4
```

Unnamed elements receive the effective names `_0`, `_1`, and so on. Mixed
forms are allowed; `(int32 val, uint8)` gives the names `val` and `_1`.

The same structural tuple type may be written with different names:

```franko
(uint hello, int world) a
(uint different, int name) b
b = a
```

`a` and `b` are the same tuple type because their ordered element types are
the same. `b.different` and `b.name` are valid for `b`; `b.hello` is not,
because `b`'s declaration did not declare that alias.

---

## 28.3 Type Identity

Tuple identity is structural:

- the number of elements matters;
- the ordered element types matter;
- element names do not matter.

```text
(int32, int32)              == (int32 val, int32 err)
(int32, int32)              == (int32 left, int32 right)
(int32, int32)              == (int32, int32)

(int32, uint32)             != (uint32, int32)
(int32, int32)              != (int32, int32, int32)
(int32, [int32, 3])         == (int32, [int32, 3])
```

Nested tuple types compare recursively by the same rule. See
[Type Equality](10-type-equality.md).

---

## 28.4 Declarations and Initialization

A local tuple declaration without an initializer follows the ordinary local
initialization rules:

```franko
(int32, int32) pair
pair._0 = 1
pair._1 = 2
```

- local tuple elements without a value are indeterminate until assigned, except pointer elements, which are null;
- global tuple storage is zero-initialized before `main` runs;
- tuple values are produced by declarations, element writes, aggregate
  literals, whole-tuple assignment, and function returns.

A tuple literal can initialize a tuple directly:

```franko
(int32, int32) pair = (int32, int32) { _1 = 2, _0 = 1 }
```

A local tuple declaration can also use the field-wise form:

```franko
(int32 first, int32 second) pair { first = 1, second = 2 }
(int32, int32) positional { 0, 1 }
(int32, int32) partial { 0 }
```

The positional field-wise form is available only for all-unnamed tuple
declarations. See [Aggregate Literals](29-aggregate-literals.md) for both
forms.

Declaration initialization works normally:

```franko
(int32, int32) x = f()
```

is shorthand for:

```franko
(int32, int32) x
x = f()
```

The right-hand side must be a tuple of the same structural type.

---

## 28.5 Element Access and Lvalues

Tuple elements are accessed with dot syntax:

```franko
res.val
res.err
lazy._0
```

A tuple element is a storage-backed lvalue when its base is storage-backed.
The same rule used for struct fields applies:

```franko
res.val = 1
lazy._0 = 3
getptr(res.val)
getview(lazy._1)
```

Tuple element access composes with the ordinary access chains:

```franko
$(ptrToTuple)._0
arrayOfTuples[i].err
```

---

## 28.6 Assignment

Whole-tuple assignment uses the built-in aggregate assignment rule:

```franko
(int32 val, int32 err) a
(int32 left, int32 right) b

b = a
```

The ordered element types must match exactly; names are irrelevant.

A tuple may be declared `const` when every element is value-assignable:

```franko
const (int32, int32) pair = (int32, int32) { 1, 2 }
const (int32, int32) defaults
```

The const rules are the usual ones: no later assignment, no mutable pointer
through `getptr`/`getmview`, and `getview` only. A tuple containing an array
is pointer-only and cannot be `const`. See
[Declarations](18-declarations.md#188-const-storage).

A tuple is assignable as a whole only when every element is an assignable
value type:

- primitive integer;
- enum;
- pointer;
- struct whose fields are all assignable;
- another assignable tuple.

A tuple containing a static array or dynamic array is pointer-only:

```franko
(int32, [int32, 3]) a
(int32, [int32, 3]) b
a = b          // invalid: non-assignable element
```

For a pointer-only tuple, assign individual elements or pass the tuple through
a pointer.

Tuples cannot declare `OPERATOR_MOV` or any other operator method, so
whole-tuple assignment always uses the built-in rule.

---

## 28.7 Functions, Parameters, and Returns

A tuple can be passed by value or returned when every element is an
assignable value type.

```franko
func swap((int32 x, int32 y) pair) -> (int32 left, int32 right) {
    return (int32 left, int32 right) {
        left = pair.y,
        right = pair.x
    }
}
```

A tuple containing a static array or dynamic array cannot be passed by value
or returned by value. Use a pointer to the tuple instead:

```franko
(int32, [int32, 3]) value
ptr<(int32, [int32, 3])> p = getptr(value)
```

Tuple element names are not part of the function signature. These two
declarations have the same parameter type and are duplicates:

```franko
func f((int32 a, int32 b) x) -> int32 { ... }
func f((int32 c, int32 d) y) -> int32 { ... }
```

---

## 28.8 Composition

Tuples compose with the other type constructors:

```franko
((int32, int32) inner, int32 outer) nested
[(int32, int32), 4] table
ptr<(int32, int32)> tuplePtr
struct Wrapper {
    (int32, int32) pair
}
```

Rules:

- tuples may nest;
- a tuple may contain structs;
- a tuple may contain enums;
- a tuple may be an array element or a pointer referent;
- a struct may have a tuple field;
- a tuple element may itself be a static or dynamic array, but then the tuple
  is pointer-only.

A struct that contains a tuple by value follows the ordinary struct
by-value-cycle rule: a struct may not reach itself through a tuple element.

---

## 28.9 Compile-Time Reflection

Tuple types participate in the normal reflection predicates. A tuple is:

- assignable by value iff every element is assignable;
- value-assignable iff every element is value-assignable;
- memset-safe iff every element is memset-safe;
- memcpy-safe iff every element is memcpy-safe;
- a type with a default value iff at least one element's type has a default.

Tuples have no operator methods, so `#is_matchable` and `#is_comparable`
return `0` for a tuple. `#is_assignable` is built-in element
assignability for tuples.

```franko
(int32, int32) pair
print(#is_value_assignable(pair))      // 1
print(#has_default_value(pair))        // 0
print(#what_type(pair))                 // (int32,int32)
print(#is_same_type(pair, (int32 val, int32 err))) // 1
```

`#what_type` prints the canonical tuple form `(int32,int32)` with element
names removed and no spaces after commas. See [Compile-Time Reflection](34-compile-time-reflection.md).

---

## 28.10 Restrictions

Tuples are deliberately limited to ordinary aggregate values:

- tuple literals spell the tuple type and use named entries or, for
  all-unnamed spellings, positional entries; a bare parenthesized list such
  as `(1, 2)` is not an expression;
- positional entries are rejected for named/mixed tuple spellings and for
  struct literals; positional and named entries cannot be mixed;
- no destructuring declarations or tuple-pattern parameters;
- no tuple methods, including intrinsic-named calls such as `t.length()`;
- no tuple operators, including `OPERATOR_*` overloads;
- no tuple casts (`cast<(int32, int32)>(x)` is rejected);
- no named tuple type aliases; write the tuple type at each declaration or
  signature;
- at least two elements are required;
- a tuple containing a static array or dynamic array is pointer-only;
- an owning `ptr<T>` cannot be a tuple element, recursively (see [Owning Pointers in Aggregates](25-pointers.md#2513-owning-pointers-in-aggregates-are-forbidden)).

Tuple values are produced by declarations, element writes, aggregate
literals, whole-tuple assignment, and function returns.

---

## 28.11 Complete Example

```franko
import "franko/io"

func split((int32 left, int32 right) pair) -> (int32 first, int32 second) {
    return (int32 first, int32 second) {
        first = pair.left,
        second = pair.right
    }
}

func main() -> int32 {
    (int32 x, int32 y) pair = (int32 x, int32 y) { x = 10, y = 20 }

    (int32 first, int32 second) result = split(pair)
    print(result.first)
    print(result.second)
    print(#what_type(pair))
    return 0
}
```

Output:

```text
10
20
(int32,int32)
```
