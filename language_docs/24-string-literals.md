# 24. String Literals

Franko string literals are **read-only views of byte arrays**. A string
literal is an ordinary expression whose type is `view<[byte, N]>`
(see [Pointers](26-pointers.md)): a read-only pointer to a static byte
array of `N` UTF-8 bytes, where `N` is the literal's byte length.

```franko
"Franko"           // view<[byte, 6]>
"Hello, World!"    // view<[byte, 13]>
```

## 24.1 A String Literal Is a View Value

Each distinct literal is backed by immutable `[byte, N]` storage
holding the UTF-8 bytes, and the literal expression is a read-only view
over it. Identical literal texts share one backing store. No null
terminator is appended: `N` is exactly the number of content bytes.

Because the literal is a pointer, it must be dereferenced with `$(...)`
to reach the bytes:

```franko
stdout($("abc").length());   // 3
stdout($("abc")[0]);         // 97
```

A string literal may appear anywhere an expression may appear:

```franko
view<[byte, 3]> s = "abc";     // declaration initializer
s = "xyz";                          // assignment to a view variable
stdout($("hi").length());            // print argument
take("hi");                         // function argument
return "hi";                        // return value (with a view return type)
```

## 24.2 UTF-8 Bytes

A string literal stores the literal's **UTF-8 bytes**: one array element
per byte. Multi-byte sequences expand accordingly:

```franko
stdout($("你好").length());   // 6: 228 189 160 229 165 189
```

## 24.3 Escapes

The supported escape sequences are `\\ \" \a \b \f \n \r \t \v`.
`\u`, `\x`, and `\0` are not supported, and a trailing backslash is
invalid. Any other escape is a lex error.

```franko
stdout($("\t\n\"\\"")[0]);   // 9
```

## 24.4 Strings and Byte Buffers

Arrays are value types and are **not assignable from strings**: a
`view<[byte, N]>` cannot initialize or assign an array. Copy the
bytes with the `memcpy` intrinsic instead — a string literal is a valid
`memcpy` source:

```franko
[byte, 6] buf;
buf.memcpy("hello");      // copies 5 bytes; buf[5] is untouched
stdout(buf[0]);            // 104
```

For dynamic arrays, size the buffer first:

```franko
[byte] buf;
buf.init($("hello").length());
buf.memcpy("hello");
```

For the same reason `[byte, ?]` cannot infer its size from a string
literal; declare the size explicitly. (`?` size inference works in
`view<[...]>` declarations with an array literal; see
[Array Literals](22-array-literals.md).)

## 24.5 Global String Views

A pointer-typed global may be initialized with a string literal (or with
`getptr`/`getmview`/`getview` over another global, or `null`):

```franko
view<[byte, 5]> GREETING = "hello";

func main() -> int32 {
    stdout($(GREETING).length());   // 5
    return 0;
}
```

## 24.6 Printing Strings

`stdout` (from `franko/stdio`) prints text: a string literal is a `view<[byte, N]>` value that decays to the `stdout(view<[uint8]>)` overload, so it is written verbatim followed by a newline. Print individual byte values by indexing:

```franko
import "franko/stdio"

stdout("hi")          // writes `hi` + newline
stdout($("hi")[0])    // 104
stdout($("hi")[1])    // 105
```

## 24.7 Empty Strings

An empty string literal `""` is the `null` view: it is valid wherever a
`view<[byte, ...]>` value is expected. It has no bytes and no element
storage; dereferencing or indexing it is invalid until a non-null view is
assigned.

```franko
view<[byte]> empty = "";       // valid: null view
view<[byte, 5]> nil = "";      // valid: null view
```

Because `""` is `null`, `stdout("")` is ambiguous between the text and
`std::String` overloads, just like `stdout(null)`. Use a typed null view or
`stdout()` when a newline is intended.
