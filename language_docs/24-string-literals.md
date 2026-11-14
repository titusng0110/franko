# 24. String Literals

Franko string literals are **untyped compile-time string constants**. A
string literal has no type of its own until a value context materializes it.
It then becomes a read-only `view<[byte, N]>` over immutable UTF-8 byte
storage (see [Pointers](26-pointers.md)).

```franko
"Franko"           // 6 UTF-8 bytes when materialized
"Hello, World!"    // 13 UTF-8 bytes when materialized
```

A compile-time string can also be produced by `#what_type(...)`.

## 24.1 Materialization

A string literal materializes as a byte view only when it is used as:

- the right-hand side of an assignment or declaration initializer;
- a field value in an aggregate initializer;
- an argument to a function, method, or compiler intrinsic;
- a return value;
- a parameter default value.

```franko
view<[byte, 3]> s = "abc";     // declaration initializer
s = "xyz";                     // assignment
take("hi");                    // function argument
return "hi";                   // return value
stdout("hello");               // function argument
buf.memcpy("hello");           // intrinsic argument
```

Every other **value** position rejects the literal because it has no type.
Compile-time `+` and comparisons are separate folding contexts (see
[24.2](#242-compile-time-concatenation-and-comparison)); they are not
materialization. In particular, a literal cannot be dereferenced, indexed,
accessed as a member, cast, or used in a condition:

```franko
$("abc")             // invalid: a string literal has no type
"abc"[0]             // invalid
"abc".length()       // invalid
cast<view<[byte, 3]>>("abc")   // invalid
if ("abc") { }       // invalid
```

To read the bytes of a literal, materialize it into a view first:

```franko
view<[byte, 3]> s = "abc";
stdout($(s).length());   // 3
stdout($(s)[0]);         // 97
```

Once materialized, the resulting view is an ordinary runtime value and
follows the normal pointer rules.

## 24.2 Compile-Time Concatenation and Comparison

A string literal supports exactly two compile-time constant operations:

- `+` concatenates UTF-8 bytes;
- `==`, `!=`, `<`, `>`, `<=`, and `>=` compare UTF-8 bytes
  lexicographically.

Both operands must be compile-time strings. The operation folds before any
materialization, so no intermediate byte storage is created.

```franko
stdout("hello" + " " + "world")        // hello world
stdout("a" < "b")                      // 1
stdout("abc" == "abc")                 // 1
stdout("abc" != "abd")                 // 1
```

`#what_type(...)` is also a compile-time string and participates in the same
folding:

```franko
stdout("type=" + #what_type(int32))    // type=int32
stdout(#what_type(int32) == "int32")   // 1

#if (#what_type(int32) == "int32") {
    stdout("int32 branch")
}
```

A comparison of a compile-time string with a runtime value is an error. A
string literal has no type, so it is never inferred into a pointer
comparison:

```franko
view<[byte, 3]> v = "abc"
v == "abc"      // invalid: runtime view compared with a string literal
```

Compare two materialized views when pointer equality is intended, or compare
two compile-time strings when byte equality is intended.

Other operators, including `-`, `*`, `/`, `%`, bitwise operators, and logical
operators, reject compile-time strings.

## 24.3 UTF-8 Bytes

A string literal stores the literal's **UTF-8 bytes**: one array element
per byte. Multi-byte sequences expand accordingly:

```franko
view<[byte, 6]> s = "你好";
stdout($(s).length());   // 6: 228 189 160 229 165 189
```

## 24.4 Escapes

The supported escape sequences are `\\ \" \a \b \f \n \r \t \v`.
`\u`, `\x`, and `\0` are not supported, and a trailing backslash is
invalid. Any other escape is a lex error.

```franko
view<[byte, 4]> s = "\t\n\"\\";
stdout($(s)[0]);   // 9
```

## 24.5 Strings and Byte Buffers

Arrays are value types and are **not assignable from strings**: a
`view<[byte, N]>` cannot initialize or assign an array. Copy the
bytes with the `memcpy` intrinsic instead — a materialized string is a valid
`memcpy` source:

```franko
[byte, 6] buf;
buf.memcpy("hello");      // copies 5 bytes; buf[5] is untouched
stdout(buf[0]);            // 104
```

For dynamic arrays, size the buffer first:

```franko
view<[byte, 5]> hello = "hello";
[byte] buf;
buf.init($(hello).length());
buf.memcpy(hello);
```

`[byte, ?]` cannot infer its size from a string literal; declare the size
explicitly. (`?` size inference works in `view<[...]>` declarations with an
array literal; see [Array Literals](22-array-literals.md).)

## 24.6 Global String Views

A pointer-typed global may be initialized with a string expression, including
a folded concatenation:

```franko
view<[byte, 5]> GREETING = "he" + "llo";

func main() -> int32 {
    stdout($(GREETING).length());   // 5
    return 0;
}
```

A non-pointer global cannot hold a string view.

## 24.7 Empty Strings

An empty compile-time string materializes as the `null` view. Emptiness is
decided **after folding**:

```franko
view<[byte]> a = "";             // null view
view<[byte]> b = "" + "";        // null view
view<[byte, 5]> c = "" + "hello"; // "hello", not null
```

Because an empty result is `null`, `stdout("")` is ambiguous between the text
and `std::String` overloads, just like `stdout(null)`. Use a typed null view or
`stdout()` when a newline is intended.

## 24.8 No Casting

String literals and compile-time string expressions cannot be cast:

```franko
cast<view<[byte, 3]>>("abc")       // invalid
unsafe_cast<int32>("abc")          // invalid
```

Materialize the literal into a byte view first; casts apply to typed values.
