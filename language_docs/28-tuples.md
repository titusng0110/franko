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
- `void` is not a valid tuple element type.

A tuple declaration uses the tuple type followed by the variable name:

```franko
(int32 val, int32 err) res
(int32, int32) lazy
(uint hello, int world) a
```

There is no tuple literal syntax. `(1, 2)` is not a tuple expression.

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
- tuple values are produced by declarations, element writes, whole-tuple
  assignment, and function returns.

Declaration-initializer sugar works normally:

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

A tuple is assignable as a whole only when every element is an assignable
value type:

- primitive integer;
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
    (int32 first, int32 second) copy = pair
    (int32 upper, int32 lower) result
    result.upper = copy.second
    result.lower = copy.first
    return result
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
- a pure value iff every element is a pure value;
- memset-safe iff every element is memset-safe;
- memcpy-safe iff every element is memcpy-safe;
- a type with a default value iff at least one element's type has a default.

Tuples have no operator methods, so `#is_matchable` and `#is_comparable`
return `0` for a tuple. `#is_assignable` is built-in element
assignability for tuples.

```franko
(int32, int32) pair
print(#is_pure_value(pair))            // 1
print(#has_default_value(pair))        // 0
print(#what_type(pair))                // (int32,int32)
print(#is_same_type(pair, (int32 val, int32 err))) // 1
```

`#what_type` prints the canonical tuple form `(int32,int32)` with element
names removed and no spaces after commas. See [Compile-Time Reflection](32-compile-time-reflection.md).

---

## 28.10 Restrictions

Tuples are deliberately limited to ordinary aggregate values:

- no tuple literals or construction sugar (`(1, 2)` is not an expression);
- no destructuring declarations or tuple-pattern parameters;
- no tuple methods, including intrinsic-named calls such as `t.length()`;
- no tuple operators, including `OPERATOR_*` overloads;
- no tuple casts (`cast<(int32, int32)>(x)` is rejected);
- no named tuple type aliases; write the tuple type at each declaration or
  signature;
- at least two elements are required;
- a tuple containing a static array or dynamic array is pointer-only.

Tuple values are produced by declarations, element writes, whole-tuple
assignment, and function returns.

---

## 28.11 Complete Example

```franko
import "franko/io"

func split((int32 left, int32 right) pair) -> (int32 first, int32 second) {
    (int32 first, int32 second) result
    result.first = pair.left
    result.second = pair.right
    return result
}

func main() -> int32 {
    (int32 x, int32 y) pair
    pair.x = 10
    pair.y = 20

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
