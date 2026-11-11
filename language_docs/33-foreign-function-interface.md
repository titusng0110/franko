# 33. Foreign Function Interface

Franko has no general native-call syntax. Instead, a Franko module can
declare that some of its functions are implemented by a C++ bridge. The
bundled `franko/os` module uses this mechanism; user modules can use
exactly the same mechanism.

An FFI module is three sibling files:

```text
lib/mathx.fr
lib/mathx.module.json
lib/mathx.ffi.cpp
```

The `.fr` file is ordinary Franko: it declares the module's functions
(and any ordinary variables or structs the module needs). The
`.module.json` file names the function declarations that cross the FFI
boundary. The `.ffi.cpp` file contains the C++ implementations.

Import the module normally:

```franko
import "lib/mathx"
```

The compiler discovers `mathx.module.json` next to the resolved `mathx.fr`.

## 33.1 Module Descriptor

The descriptor lists only the functions that are bridged:

```json
{
  "format": "franko-module",
  "version": 1,
  "id": "example/mathx",
  "source": "mathx.fr",
  "bridge": "mathx.ffi.cpp",
  "functions": [
    {"namespace": "mathx", "name": "add"},
    {"namespace": "mathx", "name": "scale"}
  ]
}
```

- `bridge` is the default C++ bridge file, relative to the descriptor.
- Every entry in `functions` is bridged. There is no per-function
  `bridge: false` opt-out.
- A function can override the module bridge file with
  `"bridgeFile": "other.ffi.cpp"`.
- Names and namespaces must match the declarations in the `.fr` file. The
  `.fr` declaration supplies the signature; the descriptor does not
  duplicate it.
- The descriptor does not declare variables, structs, target filters, or
  target-dependent constants. Platform selection is ordinary Franko code
  using `#if` and the built-in `#IS_*` target constants.

## 33.2 Target Selection

Target-specific FFI calls are guarded with `#if`:

```franko
#if (#IS_WINDOWS) {
    os::win32::WriteFile(...)
} #else #if (#IS_LINUX || #IS_MACOS) {
    os::posix::write(...)
}
```

Only the selected branch is compiled. The other platform's functions do
not need to exist on the current target, so the module does not need
target filters. `franko/os` itself declares `os::posix` only for
linux/macos and `os::win32` only for Windows, so off-target FFI functions do not exist on that target at all. See
[Compile-Time Reflection](34-compile-time-reflection.md).

An unguarded off-target call still fails when building for that target.

## 33.3 Bridge Blocks

The bridge file is split into named blocks:

```cpp
// @@common
#include "FrankoRuntime.hpp"

static int32_t mathx_bias()
{
    return 0;
}
// @@end

// @@mathx::add
namespace NS_mathx {
int32_t FUNC_add_da8ad4dd(int32_t PARAM_a, int32_t PARAM_b)
{
    return PARAM_a + PARAM_b + mathx_bias();
}
}
// @@end
```

Rules:

- A block opens with `// @@<qualified-name>` and closes with `// @@end`.
- The block name is the function's Franko qualified name:
  - function `mathx::add` → `// @@mathx::add`
- `common` is reserved for shared includes, helpers, and declarations. It is
  included once when any bridged function from that bridge file is used.
- Put `#include "FrankoRuntime.hpp"` in the `common` block. It keeps each
  bridge valid for standalone C++ linting and exposes the runtime types
  (`Franko_Thin_Pointer`, `Franko_Fat_Pointer`, `franko_panic`, fixed-width
  integers). The header is `#pragma once`, so a repeated include is harmless.
- Bridge ABI: a pointer parameter or return whose pointee is not an array
  (`ptr<T>`, `mview<T>`, or `view<T>`) is `Franko_Thin_Pointer<T>` in C++;
  access its raw pointer through `.data`. A pointer to an array is
  `Franko_Fat_Pointer<element(T)>`.
- The bridge block must define a function under the exact C++ name generated
  for the Franko declaration. The compiler reports an error if the block is
  missing or the name does not match.
- Merely importing a module with a bridge adds no bridge code unless a
  bridged function is actually used.

## 33.4 The Bundled `franko/os` Module

`franko/os` is Franko's standard FFI module. It ships
`import/franko/os.module.json` and `import/franko/os.ffi.cpp` alongside
`import/franko/os.fr`. Its functions are documented in
[`franko/os`](../library_docs/os.md). It uses the same descriptor and
bridge rules described above.

## 33.5 Failure Modes

The compiler rejects an FFI module when:

- the descriptor is malformed or has an unsupported version;
- a listed function does not exist in the `.fr` file;
- a bridged function has no bridge file or no matching block;
- a bridged function block does not contain the expected generated name.

Bridged function signatures are limited to the C++ types the bridge author can spell directly in the prelude. Struct and tuple types are not part of the current bridge model: use primitive or pointer parameters/returns for bridged functions, and keep aggregate layouts on the Franko side.
