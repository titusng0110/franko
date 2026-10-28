# 3. Program Structure

A Franko program is a sequence of **top-level items**.

Current valid top-level items are:

- struct declarations;
- global variable declarations;
- function declarations;
- import directives;
- namespace declarations.

Struct declarations are described in full in [Structs](25-structs.md). Import directives are described in full in [Source Files](02-source-files.md).

## Namespaces

A namespace groups struct, global variable, and function declarations under a qualified name:

```franko
namespace util {
    struct Point {
        int32_t x = 1
        int32_t y = 2
    }

    int32_t offset = 100

    func doubleIt(int32_t v) -> int32_t {
        return v + v + offset
    }
}
```

A namespace body may contain only struct declarations, global variable declarations, function declarations, and nested namespace declarations. Imports and executable statements are not allowed inside a namespace body.

Nested namespaces may be written in two equivalent forms:

```franko
namespace a::b {
    int32_t v = 1
}
```

```franko
namespace a {
    namespace b {
        int32_t v = 1
    }
}
```

Both forms declare the same namespace, whose full path is `a::b`. A path header (containing `::`) is only valid at the top level: a namespace declared inside a namespace body must use a single name, and its full path is the enclosing path plus that name.

The same namespace may be declared more than once, in the same file or across imported files, in either form; the declarations merge into one namespace. Duplicate symbols inside the merged namespace are rejected exactly like global duplicates.

### Qualified References

Namespace members are referenced with `::`:

```franko
util::Point p
print(util::offset)
print(util::doubleIt(5))
util::offset = 5
print(a::b::helper(x))
```

A qualified name always resolves strictly inside the named namespace path, starting from the top level; there is no fallback to the global namespace, no search of enclosing namespaces, and no relative resolution through the current namespace. `a::w` does not find a symbol declared only in `a::b`, and `a::b::x` requires both `a` and `a::b` to be declared namespaces.

### Unqualified References

Inside a namespace, an unqualified name resolves in that namespace first, then falls back to the global namespace. Enclosing namespaces are not searched: code inside `a::b` does not see unqualified names declared in `a` unless they are also visible globally. Outside any namespace, only the global namespace is visible.

### Entry Point

The entry-point function `main` must be declared in the global namespace. A `main` inside a namespace is a compile error.

`main` must be declared in one of two shapes, and it cannot be overloaded:

```franko
func main() -> int32_t {
    return 0;
}

func main(view<array<array<byte>>> args) -> int32_t {
    return 0;
}
```

Any other shape (parameters of another type, a different return type) is a compile error.

In the args form, the parameter name is free (`args`, `notargs`, ...). The template wrapper converts the raw `argc`/`argv` into nested dynamic byte arrays: `args[0]` is the program name and each argument is its raw bytes with no NUL terminator (see [Runtime and Backend Notes](31-runtime-and-backend-notes.md)).

Executable statements are **not** currently allowed in global scope.

Invalid global-scope items include:

- assignments;
- `print`;
- function calls;
- `if`;
- `while`;
- `for`;
- `return`;
- blocks;
- array intrinsic calls;
- array initializer assignments.
- expression statements (bare expressions such as `1 + 2`);

Note that some invalid global-scope forms only become visible after desugaring: declaration initializer sugar is invalid because it desugars into executable statements (an assignment, an expression-statement call, or indexed element assignments).

Execution should be rooted in a user-defined `main` function.

Example:

```franko
struct Point {
    int32_t x
    int32_t y = 0
}

int32_t count = 0

func main() -> int32_t {
    return 0;
}
```
