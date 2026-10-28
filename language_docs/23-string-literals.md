# 23. String Literals

Franko string literals are **read-only views of byte arrays**. A string
literal is an ordinary expression whose type is `view<array<byte, N>>`
(see [Pointers](24-pointers.md)): a read-only pointer to a static byte
array of `N` UTF-8 bytes, where `N` is the literal's byte length.

```franko
"Franko"           // view<array<byte, 6>>
"Hello, World!"    // view<array<byte, 13>>
```

## 23.1 A String Literal Is a View Value

Each distinct literal is hoisted to its own global `array<byte, N>`
holding the UTF-8 bytes, and the literal expression is `getview(...)`
over it. Identical literal texts share one global. No null terminator is
appended: `N` is exactly the number of content bytes.

Because the literal is a pointer, it must be dereferenced with `$(...)`
to reach the bytes:

```franko
print($("abc").length());   // 3
print($("abc")[0]);         // 97
```

A string literal may appear anywhere an expression may appear:

```franko
view<array<byte, 3>> s = "abc";     // declaration initializer
s = "xyz";                          // assignment to a view variable
print($("hi").length());            // print argument
take("hi");                         // function argument
return "hi";                        // return value (with a view return type)
```

## 23.2 UTF-8 Bytes

A string literal stores the literal's **UTF-8 bytes**: one array element
per byte. Multi-byte sequences expand accordingly:

```franko
print($("你好").length());   // 6: 228 189 160 229 165 189
```

## 23.3 Escapes

The supported escape sequences are `\\ \" \a \b \f \n \r \t \v`.
`\u`, `\x`, and `\0` are not supported, and a trailing backslash is
invalid. Any other escape is a lex error.

```franko
print($("\t\n\"\\"")[0]);   // 9
```

## 23.4 Strings and Byte Buffers

Arrays are value types and are **not assignable from strings**: a
`view<array<byte, N>>` cannot initialize or assign an array. Copy the
bytes with the `memcpy` intrinsic instead — a string literal is a valid
`memcpy` source:

```franko
array<byte, 6> buf;
buf.memcpy("hello");      // copies 5 bytes; buf[5] is untouched
print(buf[0]);            // 104
```

For dynamic arrays, size the buffer first:

```franko
array<byte> buf;
buf.init($("hello").length());
buf.memcpy("hello");
```

For the same reason `array<byte, ?>` cannot infer its size from a string
literal; declare the size explicitly or infer from an array literal.

## 23.5 Global String Views

A pointer-typed global may be initialized with a string literal (or with
`getptr`/`getmview`/`getview` over another global, or `null`):

```franko
view<array<byte, 5>> GREETING = "hello";

func main() -> int32_t {
    print($(GREETING).length());   // 5
    return 0;
}
```

## 23.6 Printing Strings

`print` is unchanged (it is superseded by a future io class): a string
literal passed to `print` prints as the address of its global byte
array, like any other pointer. Print individual bytes instead:

```franko
print($("hi")[0]);   // 104
print($("hi")[1]);   // 105
```

## 23.7 Empty Strings

An empty string literal `""` is not supported: a string literal is a
`view<array<byte, N>>` with `N >= 1`.
