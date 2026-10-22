# 2. Source Files

Franko source files commonly use the `.fr` extension.

Example:

```franko
func main() -> int32_t {
    int32_t x;
    x = 1;

    print(x);

    return 0;
}
```

## Statement Separators

Statements are terminated by a semicolon (`;`) or by one or more newlines. Both forms can be mixed freely, and consecutive separators are allowed.

Example (newline-separated, no semicolons):

```franko
func main() -> int32_t {
    int32_t x
    x = 1
    print(x)
    return 0
}
```

Newlines inside parentheses `(...)` and brackets `[...]` are ignored, so expressions, argument lists, and initializer lists may span multiple lines. Newlines inside braces `{...}` are not ignored: they remain statement separators.

## Comments

Line comments start with `//` and run to the end of the line:

```franko
// a whole-line comment
int32_t x  // a comment may follow code on the same line
```

There are no block comments.
