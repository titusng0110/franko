# 10. Type Equality

Franko uses exact, structural semantic type equality in many places. Type equality is implemented once (in the semantic type classes) and used consistently by overload resolution, assignment checking, return checking, and diagnostics.

## 10.1 Primitive Equality

Primitive types are equal only if they have the same kind.

```text
uint8  != uint16
int32  != uint32
int32  == int32
```

The aliases `int`, `uint`, and `byte` canonicalize to `int32`, `uint32`, and `uint8` respectively.

## 10.2 Dynamic Array Equality

Dynamic arrays are equal if their element types are equal.

```text
[int32] == [int32]
[int32] != [uint8]
```

## 10.3 Static Array Equality

Static arrays are equal if:

- element types are equal;
- sizes are numerically equal after unfolding the constant size expression.

The size comparison is numeric, not textual:

```text
[int32, 10]     == [int32, 0xA]
[int32, 5 + 5]  == [int32, 10]
[int32, 10]     != [int32, 20]
```

## 10.4 Pointer Equality

Pointer types are equal if their referenced types are equal **and** their
permissions match: `ptr<T>`, `mview<T>`, and `view<T>` are three distinct
types.

```text
ptr<[byte]>     == ptr<[byte]>
ptr<[byte]>     != mview<[byte]>
mview<[byte]>   != view<[byte]>
view<[byte, 3]> != view<[byte, 5]>
```

The pointer conversions — permission downgrades (`ptr<T>` to
`mview<T>`/`view<T>`, `mview<T>` to `view<T>`) and the static-to-dynamic
array pointer view decay (`mview`/`view` of `[T, N]` to `mview`/
`view` of `[T]`, optionally combined with a downgrade from a `ptr`
source) — are the only implicit conversions the language allows for
nonconstant values; they do not make the types equal (see [Pointers](26-pointers.md)).

## 10.5 Function Signature Equality

Function signatures are equal if they have:

- the same function identifier;
- the same ordered list of parameter types.

Return type and parameter names are not part of function signature
equality. For methods, the `self` parameter type is not part of signature
equality either (see [Methods](28-methods.md)).

## 10.6 Struct Equality

Struct types are nominal: two struct declarations are distinct types even when their field layouts are identical.

```text
struct X { int32 n }
struct Y { int32 n }

X != Y
X == X
```

Duplicate struct names are rejected, so each struct name denotes exactly one declared type. See [Structs](27-structs.md) for the complete struct reference.

Tuple equality is covered in [Tuples](29-tuples.md#283-type-identity).

## 10.7 Enum Equality

Enum types are nominal: two enum declarations are distinct types even
when their backing types and variants are identical.

```text
enum<int32> X { a, b }
enum<int32> Y { a, b }

X != Y
X == X
```

Duplicate enum names are rejected, so each enum name denotes exactly
one declared type. An enum name shares one type-name namespace with
struct names: an enum and a struct may not use the same name in the
same scope. See [Enums](31-enums.md) for the complete enum reference.

## 10.8 Generic Instance Equality

A generic struct use expands to one concrete nominal struct instance. Two
uses are the same type only when they are the same template instance with the
same explicit arguments:

```text
Box<int32> == Box<int32>
Box<int32> != Box<uint8>
Box<int32> != Other<int32>
```

Type arguments are compared after alias normalization (`int` is `int32`,
`byte` is `uint8`, `uint` is `uint32`) and after canonical constant folding
for value arguments (`Box<int32, 1 + 2> == Box<int32, 3>`). See
[Generics](20-generics.md).
