# 3. Program Structure

A Franko program is a sequence of **top-level items**.

Current valid top-level items are:

- global variable declarations;
- function declarations.

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

The `alloc` modifier is not valid at global scope; global variables are always statically allocated. See [Heap Variables and `alloc`](13-heap-variables-and-alloc.md).

Execution should be rooted in a user-defined `main` function.

Example:

```franko
func main() -> int32_t {
    return 0;
}
```
