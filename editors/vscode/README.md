# Franko for Visual Studio Code

Syntax highlighting, bracket matching, comment toggling, and snippets for
the Franko language (`.fr` and `.franko`).

## Features

- TextMate syntax highlighting for Franko
- `//` line comments
- Bracket matching and auto-closing for `{}`, `[]`, and `()`
- Function, struct, namespace, if, for, and tuple snippets
- Generic struct and generic function snippets, plus `#generic` and
  `typename` highlighting

This is a declarative TextMate highlighting extension. It does not provide
compiler diagnostics, IntelliSense, or a language server.

The grammar covers the syntax in `Franko.g4`: primitive integer types and
aliases, `ptr`/`mview`/`view`, tuple types, `#if`/`#else`,
`$()` dereference, `cast`/`unsafe_cast`, `getptr`/`getmview`/`getview`,
namespace qualification with `::`, return arrows, and compound assignment
operators.

Highlighting categories are separated by TextMate scope:

- `func`, `struct`, `namespace`, and `import` share the declaration category.
- `if`, `else`, `while`, `for`, `break`, `continue`, and `return` share the
  control-flow category.
- `#if` and `#else` use a compile-time-control category.
- `#IS_*` target constants and `#name(...)` reflection functions use
  compile-time constant/function categories.
- All primitive integer types and their `int`/`uint`/`byte` aliases share one
  storage-type category.
- `ptr`, `mview`, and `view` share a pointer-type category.
- `null` and `self` keep their own literal/language categories.

Actual colors are chosen by the active VS Code theme, not by the grammar
itself.

## Install locally

Build the extension package from this directory:

```bash
npm install
npm run package
```

Then install the generated `.vsix`:

```bash
code --install-extension franko-0.1.0.vsix
```

Reload VS Code after installation.

## `.fr` file association

The extension registers both `.fr` and `.franko`. If another extension also
claims `.fr`, set the language mode explicitly for this workspace:

```json
{
  "files.associations": {
    "*.fr": "franko"
  }
}
```

You can also use **Change Language Mode** from the Command Palette and choose
**Franko**.

## Development

Open this directory in VS Code and press `F5` to launch an Extension
Development Host, then open any `.fr` file.

Useful checks:

```bash
npm install
npm run package
```

## Publishing

Create a Visual Studio Marketplace publisher, set the `publisher` field in
`package.json` to that publisher ID, then:

```bash
npx @vscode/vsce publish
```

## License

MIT. See the repository root `LICENSE`.
