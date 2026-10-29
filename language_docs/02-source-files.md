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
import "abc"
```

Imports are top-level items and may appear anywhere at global scope, mixed freely with struct, global variable, and function declarations.

The import path **must be a string literal**. The bare form `import abc` is a syntax error. Hyphens and spaces are ordinary characters inside the quoted path, so `import "my-lib/foo bar"` names the file `foo bar.fr` in the `my-lib` directory.

String literal paths support the same escape sequences as string literals. The path is completed as follows:

- `.fr` is appended when the file name has no extension.
- A present but non-`.fr` extension is rejected.

### Search Directory

The compiler accepts an import search directory with `-p <dir>`. The default is `import` relative to the compiler's working directory; the bundled compile scripts pass the compiler's own `import` directory explicitly.

A **prefix-free** path (one that does not start with `./`, `../`, or `/`) is looked up in two places, in order:

1. the search directory: `<search-dir>/<path>.fr`
2. the directory of the file that contains the import statement

The first location that holds a regular file wins, so a module supplied through `-p` shadows a same-named file next to the importing source file. A missing search directory is not an error; resolution simply falls through to the importing file's directory.

Paths with an explicit `./`, `../`, or `/` prefix skip the search directory and resolve relative to the importing file (or as an absolute path).

Examples (search directory `import/`):

| Source | Search first | Fall back to |
|---|---|---|
| `import "foo"` | `import/foo.fr` | `<file-dir>/foo.fr` |
| `import "lib/foo"` | `import/lib/foo.fr` | `<file-dir>/lib/foo.fr` |
| `import "lib/foo.fr"` | `import/lib/foo.fr` | `<file-dir>/lib/foo.fr` |
| `import "./lib/foo"` | — | `<file-dir>/lib/foo.fr` |
| `import "../shared/foo"` | — | `<parent-of-file-dir>/shared/foo.fr` |
| `import "/abs/foo"` | — | `/abs/foo.fr` |
| `import "foo.txt"` | error: must reference a `.fr` file | |

A quoted path whose last segment is empty (for example `import "abc/"`) is rejected because it has no file name.

Import paths are always written in POSIX form with forward slashes, on every platform. On Windows, a `/`-prefixed path is translated when it reaches the filesystem: `import "/abs/foo"` resolves to `X:\abs\foo.fr`, where `X:` is the drive of the current working directory, and `/` separators in the translated path become `\`.

### How Imports Compile

An import is resolved at compile time:

- the referenced file is parsed like any other source file;
- all of its top-level items (structs, global variables, functions) are compiled into the same global program as the importing file's items;
- imports are processed in declaration order, and an import of an already-processed file is skipped silently, so repeated and cyclic imports are safe (each file is compiled exactly once);
- an imported file's own imports are resolved relative to that file's directory (after first consulting the search directory).

Because every symbol lives in one global namespace, duplicate declarations across files are reported like duplicate declarations within one file.

### Built-in Modules

Importing certain modules through their canonical specifier (currently
only `import "franko/os"`) additionally activates compiler FFI
behavior: declared functions are given C++ definitions from a bridge
file instead of their Franko bodies. See [Foreign Function
Interface](31-foreign-function-interface.md) for the activation rule,
the bridge layout, and the verification checks.
