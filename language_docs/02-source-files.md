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

## Imports

A source file may import other source files with an import directive:

```franko
import importPath
```

Imports are top-level items and may appear anywhere at global scope, mixed freely with struct, global variable, and function declarations.

The import path is either a string literal or an import name:

```franko
import lib/util
import "util"
```

Import names may contain digits, ASCII letters, underscores, and `/` as a separator between path segments. A `/` may not be the first or last character, so `import /abc` and `import abc/` are syntax errors. Digits may appear in any position, including the first, so `import 123abc` is a valid import name.

Paths are always resolved relative to the directory of the file that contains the import statement.

### Import Name Form

The import name form resolves to `./<name>.fr`:

```franko
import lib/abc      // ./lib/abc.fr
import util         // ./util.fr
```

### String Literal Form

String literal paths support the same escape sequences as string literals. The path is completed as follows:

- `./` is prepended when the path has no directory prefix. The prefixes `./`, `../`, and `/` all count as directory prefixes and are used as written.
- `.fr` is appended when the file name has no extension.
- A present but non-`.fr` extension is rejected.

Examples (relative to the importing file's directory):

| Source | Resolved path |
|---|---|
| `import "foo"` | `./foo.fr` |
| `import "lib/foo"` | `./lib/foo.fr` |
| `import "lib/foo.fr"` | `./lib/foo.fr` |
| `import "./lib/foo"` | `./lib/foo.fr` |
| `import "../shared/foo"` | `../shared/foo.fr` |
| `import "/abs/foo"` | `/abs/foo.fr` |
| `import "foo.txt"` | error: must reference a `.fr` file |

Import paths are always written in POSIX form with forward slashes, on every platform. On Windows, a `/`-prefixed path is translated when it reaches the filesystem: `import "/abs/foo"` resolves to `X:\abs\foo.fr`, where `X:` is the drive of the current working directory, and `/` separators in the translated path become `\`.

### How Imports Compile

An import is resolved at compile time:

- the referenced file is parsed like any other source file;
- all of its top-level items (structs, global variables, functions) are compiled into the same global program as the importing file's items;
- imports are processed in declaration order, and an import of an already-processed file is skipped silently, so repeated and cyclic imports are safe (each file is compiled exactly once);
- an imported file's own imports are resolved relative to that file's directory.

Because every symbol lives in one global namespace, duplicate declarations across files are reported like duplicate declarations within one file.
