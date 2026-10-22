# 3. Program Structure

A Franko program is a sequence of **top-level items**.

Current valid top-level items are:

- struct declarations;
- global variable declarations;
- function declarations.

Struct declarations are described in full in [Structs](24-structs.md).

Executable statements are **not** currently allowed in global scope.

Invalid global-scope items include:

- assignments;
- `print`;
- function calls;
- `if`;
- `while`;
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
