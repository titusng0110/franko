# 3. Program Structure

A Franko program is a sequence of **top-level items**.

Current valid top-level items are:

- struct declarations;
- global variable declarations;
- function declarations;
- import directives;
- namespace declarations.

Struct declarations are described in full in [Structs](24-structs.md). Import directives are described in full in [Source Files](02-source-files.md).

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

A namespace body may contain only struct declarations, global variable declarations, and function declarations. Imports, executable statements, and nested namespaces are not allowed inside a namespace body.

The same namespace may be declared more than once, in the same file or across imported files; the declarations merge into one namespace. Duplicate symbols inside the merged namespace are rejected exactly like global duplicates.

### Qualified References

Namespace members are referenced with `::`:

```franko
util::Point p
print(util::offset)
print(util::doubleIt(5))
util::offset = 5
```

A qualified name always resolves strictly inside the named namespace; there is no fallback to the global namespace. More than one `::` level (for example `a::b::c`) is rejected while nested namespaces are unsupported.

### Unqualified References

Inside a namespace, an unqualified name resolves in that namespace first, then falls back to the global namespace. Outside any namespace, only the global namespace is visible.

### Entry Point

The entry-point function `main` must be declared in the global namespace. A `main` inside a namespace is a compile error.

Executable statements are **not** currently allowed in global scope.

Invalid global-scope items include:

- assignments;
- `print`;
- function calls;
- `if`;
- `while`;
- `for`;
- `return`;
- `del`;
- blocks;
- array intrinsic calls;
- array initializer assignments.
- expression statements (bare expressions such as `1 + 2`);

Note that some invalid global-scope forms only become visible after desugaring: declaration initializer sugar is invalid because it desugars into executable statements (an assignment, an expression-statement call, or indexed element assignments).

The `alloc` modifier is not valid at global scope; global variables are always statically allocated. The same rule applies inside struct bodies: `alloc` is not valid for struct fields. See [Heap Variables and `alloc`](13-heap-variables-and-alloc.md).

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
