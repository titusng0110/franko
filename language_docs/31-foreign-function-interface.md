# 31. Foreign Function Interface

Franko has no general native-call syntax. Operating-system access goes
through the built-in `franko/os` module. Import it with the canonical
specifier:

```franko
import "franko/os"
```

Its functions and constants are then ordinary Franko declarations; the
API is documented in [`franko/os`](../library_docs/os.md). The rest of
this chapter describes the language-visible behavior of the module.

## 31.1 Activation

FFI behavior switches on when the resolved import target path ends in
`franko/os.fr` (normally the bundled module). The supported spelling is
`import "franko/os"`; `import "franko/os.fr"` resolves to the same
module and activates it identically. The standard `franko/io` module
also reaches it through its internal relative import.

A user file that merely defines an `os` namespace does not activate the
bridge. See [Source Files](02-source-files.md) for the general import
path rules.

## 31.2 Type Checking

Module functions are checked exactly like user-written Franko, and calls
resolve through the ordinary overload rules.

A known intrinsic with any other signature is a compile error, and any
other function declared in `os::posix` or `os::win32` is an
unsupported-function error while the module is active. A call that is
only valid on a different target is also rejected at compile time, so
the dead branch of a target check never has to compile on the wrong
platform.

## 31.3 Runtime Contract

The module checks `count` against the buffer's array length before every
transfer: a count beyond the end aborts, consistent with Franko's other
bounds checks, so the FFI cannot corrupt memory. A null or uninitialized
buffer returns the failure value (`-1` for posix calls, `0` for win32
calls) instead of crashing.

## 31.4 Namespace Ownership

`os::posix` and `os::win32` are owned by the module only while it is
active. Without `import "franko/os"`, they are ordinary namespaces
anyone may extend; the compiler owns them only for programs that import
the module through its canonical specifier.
