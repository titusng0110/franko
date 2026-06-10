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

Execution should be rooted in a user-defined `main` function.

Example:

```franko
func main() -> int32_t {
    return 0;
}
```
