# 31. Foreign Function Interface

Franko has no general native-call syntax. Instead, a Franko module can
declare that some of its variables, structs, or functions are implemented by
a C++ bridge. The bundled `franko/os` module uses this mechanism; user modules
can use exactly the same mechanism.

An FFI module is three sibling files:

```text
lib/mathx.fr
lib/mathx.module.json
lib/mathx.ffi.cpp
```

The `.fr` file is ordinary Franko: it declares the module's variables,
structs, and functions. The `.module.json` file names the declarations that
cross the FFI boundary. The `.ffi.cpp` file contains the C++ implementations.

Import the module normally:

```franko
import "lib/mathx"
```

The compiler discovers `mathx.module.json` next to the resolved `mathx.fr`.

## 31.1 Module Descriptor

The descriptor has three declaration lists:

```json
{
  "format": "franko-module",
  "version": 1,
  "id": "example/mathx",
  "source": "mathx.fr",
  "bridge": "mathx.ffi.cpp",

  "variables": [
    {
      "namespace": "mathx",
      "name": "MAGIC",
      "compileTimeConstant": true,
      "target": {
        "default": 77
      }
    }
  ],

  "structs": [],

  "functions": [
    {
      "namespace": "mathx",
      "name": "add",
      "bridge": true
    },
    {
      "namespace": "mathx",
      "name": "scale",
      "bridge": true,
      "targets": {
        "os": ["linux", "macos"]
      }
    }
  ]
}
```

- `bridge` is the default C++ bridge file, relative to the descriptor.
- `bridged` variables default to `false`; bridged structs and functions
  default to `true`. Set `bridge: true` or `bridge: false` explicitly when
  needed.
- A declaration can override the module bridge file with
  `"bridgeFile": "other.ffi.cpp"`.
- Names and namespaces must match the declarations in the `.fr` file. The
  `.fr` declaration supplies the type/signature; the descriptor does not
  duplicate it.

The descriptor always accepts `variables`, `structs`, and `functions`, so a
module can declare future bridge entities without changing its file shape.
Current releases fully implement function bridging and target-dependent
variables; struct bridging and non-constant bridged variables are reserved
for future bridge kinds.

## 31.2 Target-Dependent Variables

A variable listed in `variables` can be a compile-time constant that depends
on the compilation target:

```json
{
  "namespace": "os",
  "name": "IS_LINUX",
  "compileTimeConstant": true,
  "target": {
    "default": 0,
    "overrides": [
      {"when": {"os": "linux"}, "value": 1}
    ]
  }
}
```

The `default` value applies when no override matches. Overrides are checked in
order and the first match wins. A `when` object can filter on `os`, `arch`, or
`canonical`:

```json
{"when": {"arch": "x86_64"}, "value": 1}
{"when": {"canonical": "windows-arm64"}, "value": 1}
```

The declared Franko variable must have an integer type, and the value must fit
that type. `compileTimeConstant: true` makes references fold to the value, so
the variable can be used in ordinary `if` conditions and constant expressions.

Target-dependent variables are fully implemented. Bridged storage variables
(`bridge: true` on a non-constant variable) are accepted by the descriptor
schema for future use.

## 31.3 Target-Gated Functions

Functions and structs can restrict the targets on which they are valid:

```json
{
  "namespace": "os::posix",
  "name": "write",
  "bridge": true,
  "targets": {"os": ["linux", "macos"]}
}
```

A live call to a function whose target filter does not match the compilation
target is a compile-time error. A call in a branch that is eliminated by
constant folding is not live and does not trigger the error, which is how
portable target-selection code is written.

## 31.4 Bridge Blocks

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
int32_t FUNC_add_HASH_1d94001d(int32_t VAR_a, int32_t VAR_b)
{
    return VAR_a + VAR_b + mathx_bias();
}
}
// @@end
```

Rules:

- A block opens with `// @@<qualified-name>` and closes with `// @@end`.
- The block name is the declaration's Franko qualified name:
  - variable `mathx::MAGIC` → `// @@mathx::MAGIC`
  - struct `mathx::Point` → `// @@mathx::Point`
  - function `mathx::add` → `// @@mathx::add`
- `common` is reserved for shared includes, helpers, and declarations. It is
  emitted once when any bridged declaration from that bridge file is used.
- Put `#include "FrankoRuntime.hpp"` in the `common` block. Bridge files are
  spliced into the generated program, which already includes the runtime, but
  the include keeps each bridge valid for standalone C++ linting and gives the
  compiler a clear place to expose the runtime types (`Franko_Fat_Pointer`,
  `franko_panic`, fixed-width integers). The header is `#pragma once`, so the
  duplicate include is harmless.
- The bridge block must define a function under the exact mangled C++ name the
  compiler emits for the Franko declaration. The compiler checks this and
  reports a compile-time error if the block is missing or the name does not
  match.
- Merely importing a module with a bridge adds no C++ unless a bridged
  declaration is actually used.

## 31.5 The Bundled `franko/os` Module

`franko/os` is the compiler's standard FFI module. It ships
`import/franko/os.module.json` and `import/franko/os.ffi.cpp` alongside
`import/franko/os.fr`. Its functions and constants are documented in
[`franko/os`](../library_docs/os.md). Nothing about it is special to the
compiler: it uses the same descriptor and bridge rules described above.

## 31.6 Failure Modes

The compiler rejects an FFI module when:

- the descriptor is malformed or has an unsupported version;
- a listed declaration does not exist in the `.fr` file;
- a bridged declaration has no bridge file or no matching block;
- a bridged function block does not contain the expected mangled name;
- a target-dependent constant is missing, mistyped, or out of range;
- a live call targets a function unavailable on the selected target.
