# 32. Foreign Function Interface

Franko has no general native-call syntax. Operating-system access goes
through FFI intrinsics: functions that are *declared* in Franko and
*defined* in C++ by a bridge file the compiler splices into the
generated program. The currently available FFI module is `franko/os`;
its API is documented separately in `library_docs/os.md`. This chapter
describes the mechanics: how the compiler detects the module, where
the C++ lives, and which mistakes fail loudly instead of miscompiling.

## 32.1 Activation

FFI behavior switches on only when the program imports the module
through its canonical specifier:

```franko
import "franko/os"
```

`import "franko/os.fr"` normalizes to the same path text and activates
the bridge identically. Any other path that happens to reach the same
file (for example a `./` or `../` relative import) keeps it an
ordinary Franko file: the stub bodies compile as written and no bridge
is loaded. See [Source Files](02-source-files.md) for the general
import path rules.

## 32.2 Declaration File

`import/franko/os.fr` declares the module API with stub bodies only.
Every compiler phase between import resolution and code generation
runs unchanged: the declarations parse, desugar, type-check, and pass
legality and memory safety checking exactly like user-written Franko,
and calls resolve through the ordinary overload rules. The stubs
return failure values (`-1` for posix calls, `0` for win32 calls), so
if the bridge ever fails to attach, the program reports failure
instead of silently succeeding.

## 32.3 Bridge File

The real definitions live in `import/franko/os.ffi.cpp`, next to the
module. A `.cpp` file can sit there safely because imports only allow
`.fr` files: the bridge can never be picked up as a module itself.

The file is split into marker-delimited splice units:

```text
// @@franko-ffi:common               platform includes (windows.h / unistd.h)
// @@franko-ffi:posix.read            os::posix::read definition
// @@franko-ffi:posix.write           os::posix::write definition
// @@franko-ffi:win32.GetStdHandle    os::win32::GetStdHandle definition
// @@franko-ffi:win32.ReadFile        os::win32::ReadFile definition
// @@franko-ffi:win32.WriteFile       os::win32::WriteFile definition
```

Each unit ends with `// @@franko-ffi:end`. Every intrinsic block
defines its function under the exact mangled C++ name the backend
emits for the Franko declaration (see [Runtime and Backend
Notes](31-runtime-and-backend-notes.md) for the mangling rule): the
same trick the program templates use to call the mangled `main`.

```text
FUNC_read_HASH_be149563            os::posix::read
FUNC_write_HASH_731d748e           os::posix::write
FUNC_GetStdHandle_HASH_493245c2    os::win32::GetStdHandle
FUNC_ReadFile_HASH_50f286c1        os::win32::ReadFile
FUNC_WriteFile_HASH_c811d200       os::win32::WriteFile
```

(The hashes move whenever the `os.fr` signatures change: they are the
FNV-1a hash of the canonical signature key, which includes the
parameter pointer permissions — `read` fills an `mview<array<byte>>`
while `write` only reads a `view<array<byte>>`.)

Signatures must match the prototypes codegen emits for the `os.fr`
declarations, including the `NS_os::NS_posix` / `NS_os::NS_win32`
namespace nesting. The bridge is spliced after the program template's
`#include "FrankoRuntime.hpp"`, so `Franko_Dynamic_Array`,
`franko_panic`, and the fixed-width integer types are available.

## 32.4 Splicing

Codegen emits prototypes for every intrinsic (they act as forward
declarations) but never emits the stub bodies; the bridge provides the
only definitions. After emission, the compiler prepends the `common`
block plus one block per intrinsic the program actually calls.
Merely importing `franko/os` without calling anything adds zero C++
to the output, and an unused intrinsic leaves only its harmless
prototype behind.

## 32.5 Verification

Three mistakes fail at Franko-compile time instead of C++ link time:

- a missing bridge file (`os.ffi.cpp` next to the resolved `os.fr`)
  is an import-time error naming the expected path;
- every intrinsic declared in the program must have its mangled
  definition present in `ffi.cpp`, checked the same way the driver
  checks the program template's entry-point call. If `os.fr`
  signatures change without updating the bridge, compilation stops
  here, naming the drifted symbol;
- a known intrinsic name with anything but the exact intrinsic
  signature is an `os.fr`/`ffi.cpp` drift error, and any other
  function declared in `os::posix` or `os::win32` is an unsupported-
  function error. Stubs in those namespaces must never compile
  silently.

## 32.6 Runtime Contract

The bridge checks `count` against the buffer's array length before
every transfer: `count` beyond the end aborts via `franko_panic`,
consistent with Franko's other bounds checks, so the FFI cannot
corrupt memory. A null or uninitialized buffer returns the stub
failure value instead of crashing. The win32 blocks compile to a
`#error` off Windows, so a win32 call built for the wrong target
fails with a clear message at the C++ stage.

## 32.7 Namespace Ownership

The ownership rule in 32.5 applies only while the module is active.
Without `import "franko/os"`, `os::posix` and `os::win32` are
ordinary namespaces anyone may extend; the compiler owns them only
for programs that import the module through its canonical specifier.
