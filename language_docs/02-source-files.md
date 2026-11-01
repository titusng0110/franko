# 2. Source Files

Franko source files commonly use the `.fr` extension.

Example:

```franko
func main() -> int32 {
    int32 x;
    x = 1;

    print(x);

    return 0;
}
```

## Statement Separators

Statements are terminated by a semicolon (`;`) or by one or more newlines. Both forms can be mixed freely, and consecutive separators are allowed.

Example (newline-separated, no semicolons):

```franko
func main() -> int32 {
    int32 x
    x = 1
    print(x)
    return 0
}
```

Newlines inside parentheses `(...)` and brackets `[...]` are ignored, so expressions, argument lists, and array literals may span multiple lines. Newlines inside braces `{...}` are not ignored: they remain statement separators.

## Comments

Line comments start with `//` and run to the end of the line:

```franko
// a whole-line comment
int32 x  // a comment may follow code on the same line
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

The compiler has a single import search directory: by default the
compiler's own `import/` directory. The bundled `compile.py` always uses
that directory, which is where the standard modules live.

A **prefix-free** path (one that does not start with `./`, `../`, or `/`)
is looked up in two places, in order:

1. the search directory: `<search-dir>/<path>.fr`
2. the directory of the file that contains the import statement

The first location that holds a regular file wins, so a module supplied
through the search directory shadows a same-named file next to the
importing source file. A missing search directory is not an error;
resolution simply falls through to the importing file's directory.

Paths with an explicit `./`, `../`, or `/` prefix skip the search
directory and resolve relative to the importing file (or as an absolute
path).

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

### Standard Library Import Paths

The compiler's bundled standard library lives under its own
`./import/franko/` directory. The import prefix `franko/` is reserved for
those standard modules:

```franko
import "franko/io"
import "franko/os"
```

User code must not add files under the compiler's `import/franko/` directory
or use the `franko/` prefix for user modules. Use a project-relative path or
your own search-directory path instead, such as `import "./lib/my_module"` or
`import "myproject/my_module"` (see [Naming Conventions](33-naming-conventions.md)).

### How Imports Resolve

An import is resolved at compile time:

- the referenced file is read like any other source file;
- all of its top-level items (structs, global variables, functions) become part of the same program as the importing file's items;
- imports are processed in declaration order, and an import of an already-processed file is skipped silently, so repeated and cyclic imports are safe (each file is processed exactly once);
- an imported file's own imports are resolved relative to that file's directory (after first consulting the search directory).

Namespaces still separate symbols by path; duplicate declarations that end up in the same namespace are reported like duplicates within one file.

### Built-in Modules

Importing a module whose resolved target path ends in `franko/os.fr`
(normally the bundled `import/franko/os.fr`; the usual spelling is
`import "franko/os"`) additionally activates built-in FFI behavior.
The standard `franko/io` module also reaches it through its internal
relative import. See [Foreign Function
Interface](31-foreign-function-interface.md) for the activation rule and
the module contract.
