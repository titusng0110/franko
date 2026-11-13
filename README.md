# Franko

Franko is a small statically typed language that compiles to C++14 and
builds native executables.

## Why Franko?

Franko's philosophy is clean, non-cryptic syntax like Python; low-level control without C++ footguns; and a much easier learning curve than Rust.
Its defining features are:

- **Pointer capabilities.** `ptr<T>` owns, `mview<T>` mutates, and `view<T>`
  reads. The permission lattice is enforced by the compiler, with no raw
  pointer arithmetic or ownership annotations.
- **No hidden heap allocation or copying.** Dynamic arrays have a monopoly
  on the heap: they are explicitly `init`/`resize`/`uninit`ed. No garbage collection (GC), implicit deep copy, or hidden
  memory allocation.
- **Whole-program memory safety, not just local checks.** Three interprocedural
  analyzers run over an exact call graph with summary-based BFS and must/may
  facts: null-state analysis tracks pointers, pointee cells, argument
  requirements, and call results; dangling-pointer analysis tracks frame
  liveness through blocks, loops, breaks, returns, and calls, catching escapes
  and use-after-scope; dynamic-array lifecycle analysis tracks aliases and
  `init`/`resize`/`uninit` state across calls, catching double-frees, stale
  aliases, and leaks. No ownership annotations are required.
- **Compile-time generics.** `#generic` structs and
  functions take explicit type and value arguments (`Box<int32>`,
  `Buffer<uint8, 64>`, `identity<int32>(x)`). Templates expand to ordinary
  concrete declarations before checking and code generation, with no type
  erasure, vtables, or runtime representation.
- **Compile-time `#if` / `#else` splices.** Select statements or declarations
  at function, global, namespace, and struct scope. The selected branch is
  ordinary code; the other branch does not exist. No trait solver, SFINAE, or
  template metaprogramming. There are built-in type predicates such as `#is_memcpy_safe`, `#is_value_assignable`, and `#is_comparable`.
- **Named and optional arguments.** Calls may use `f(name = value)`; parameters
  may declare compile-time defaults such as `func f(int32 x, int32 y = 0)`.
  Ordinary methods support the same syntax, with `self` implicit.
- **Byte-level array intrinsics without pointer arithmetic.** `memcpy`,
  `memset`, `memmove`, `memcmp`, searches, endian access, and bit operations
  are built in. They work on arrays and views directly, so byte-level work
  needs no pointer arithmetic; the language enforces their receiver and
  element rules.
- **FFI without binding generators.** Small JSON sidecars plus C++ bridge
  blocks; target selection uses the same `#if`.
- **Native performance, readable output.** Ahead-of-time C++14 with no VM or
  GC. The [red-black benchmark](benchmark/redblack.md) is within about 7% of
  a same-data-structure C++ control and beats `std::set` at `-Omax`; the
  [in-place quicksort benchmark](benchmark/quicksort.md) is competitive on
  duplicate-heavy inputs. Generated source is ordinary C++14.

## A taste

```franko
import "franko/stdio"

struct Counter {
    int32 value = 0

    func add(mview<Counter> self, int32 amount) -> void {
        $(self).value = $(self).value + amount
    }

    func get(view<Counter> self) -> int32 {
        return $(self).value
    }
}

func main() -> int32 {
    Counter c
    c.add(41)
    c.add(1)
    stdout("count: ", end = null)
    stdout(c.get())

    #if (#IS_WINDOWS) {
        stdout("windows")
    } #else {
        stdout("posix")
    }

    for (int32 i = 0; i < 3; i++) {
        for (int32 j = 0; j < 3; j++) {
            if (j == 1) { break 2 }   // exits both loops
            stdout(i + j)
        }
    }

    return 0
}
```

## Requirements

- [Java 25](https://adoptium.net/) (`java` and `javac` on `PATH`).
  On Windows ARM64, use JDK 23 and set `FRANKO_JAVA_RELEASE=23`.
- A C++14 compiler.
  * **Windows x86_64 / ARM64**: Visual Studio (or VS Build Tools) with
    the **Desktop development with C++** workload. The scripts find
    `cl.exe` with `vswhere` and load the host-appropriate
    `vcvars64.bat` / `vcvarsarm64.bat` environment automatically. A
    MinGW `g++` or `clang++` on `PATH` is still accepted as a fallback
    (set `CXX` to choose one explicitly); it does not support
    AddressSanitizer, so those builds default to `-O`.
  * **Linux x86_64 / arm64**: `g++` (default) or `clang++` on `PATH`
    (set `CXX` to choose one explicitly).
  * **macOS (x86_64 / arm64)**: `clang++` (default) or `g++` on `PATH`
    (set `CXX` to choose one explicitly).
- [uv](https://docs.astral.sh/uv/) with Python 3.13+.

The repository already ships the ANTLR jar, the Franko runtime headers,
and a bundled jemalloc for Linux. macOS and Windows use the system
allocator; on Linux, jemalloc is opt-in with `--jemalloc`.

## Install

```bash
git clone https://github.com/titusng0110/franko.git
cd franko
uv run python update_toolchain.py
```

This builds the compiler toolchain. Re-run it after changing the grammar
or compiler sources; `compile.py` also builds it automatically if it is
missing. On Windows ARM64, set `FRANKO_JAVA_RELEASE=23` first, or pass
`--java-release 23`.

## Compile a program

```bash
uv run python compile.py programs/binary_codec.fr
```

This writes `programs/binary_codec.cpp` and a native executable next to the
source (`programs/binary_codec.out` on Linux/macOS,
`programs/binary_codec.exe` on Windows).

`-o` overrides the generated `.cpp` path, and the executable follows that
stem. `-p` overrides the import search directory (default `./import`):

```bash
uv run python compile.py -o build/app.cpp -p ./import programs/app.fr
```

Build modes:

* **Default**: AddressSanitizer with the system allocator (`-O1
  -fsanitize=address` on GCC/Clang, cl.exe with no `/O` option plus
  `/fsanitize=address` on Windows). The Windows g++/clang++ fallback has
  no ASan runtime, so it defaults to `-O` instead.
* **`-O`**: optimized (`-O3` on GCC/Clang, `/O2` on MSVC), no
  sanitizer.
* **`-Omax`**: like `-O`, but with bounds, bit/offset, and
  `unsafe_cast` range checks stripped. Out-of-range access is undefined
  behavior; null and uninitialized-state checks remain.
* **`--jemalloc`**: Linux only, and only with `-O` or `-Omax`; links the
  bundled jemalloc instead of the system allocator. It is never used on
  macOS or Windows, and it cannot be combined with the ASan default build.

Generated C++ is compiled with C++ exceptions and RTTI disabled:
`-fno-exceptions -fno-rtti` with GCC/Clang, `/EHs-c- /GR-` with MSVC.
Runtime violations go through `franko_panic`, which prints and aborts
instead of throwing.

Debug binaries carry their sanitizer runtime where the toolchain allows
it: MSVC builds copy `clang_rt.asan_dynamic-<arch>.dll` and the
`vcruntime140*.dll` files next to the executable (Microsoft no longer
ships a static ASan runtime, and the ASan DLL itself uses the dynamic
CRT), and Linux links `-static-libasan`. Apple clang only provides the
dynamic ASan runtime, so macOS ASan binaries use the toolchain's dylib.
Linux also links the C++ runtime statically; macOS has no static libc++.
Release MSVC builds use `/MT`, so they depend only on `KERNEL32.dll`.

```bash
uv run python compile.py -O programs/binary_codec.fr
uv run python compile.py -O --jemalloc programs/binary_codec.fr
uv run python compile.py -Omax programs/binary_codec.fr
uv run python compile.py --target native programs/binary_codec.fr
```

`--target native` adds `-march=native` with GCC/Clang (MSVC has no
equivalent); an explicit `<os>-<arch>` target (for example
`linux-x86_64`) transpiles without building a native binary on a
different host.

## Example programs

Working programs live in [`programs/`](programs/):

```text
programs/greetings.fr    stdin greeting (the one std::String demo)
programs/ledger.fr       command-driven account ledger
programs/wordfreq.fr     pool-backed word-frequency trie
programs/calculator.fr   integer expression parser/evaluator
programs/toposort.fr     dependency topological sort
programs/dijkstra.fr     weighted shortest paths
programs/lru_cache.fr    pool-backed LRU cache
programs/binary_codec.fr byte/bit/endian packet codec
programs/life.fr         Conway's Game of Life
programs/sudoku.fr       bitmask backtracking Sudoku solver
```

See [`programs/README.md`](programs/README.md) for what each program
demonstrates, its input format, and the shared byte-text helpers. Compile
and run them the same way as above.

The smoke test keeps generated `.cpp` files next to each source so the
C++14 output can be inspected; they are git-ignored.

## Tests

Run the same suites as CI:

```bash
uv run python programs/run_programs.py
uv run python language_tests/run_language_tests.py
uv run python library_tests/run_library_tests.py
```

`run_programs.py` builds and runs every program in both the default and
`-Omax` modes. The language and library suites use the default build mode.
On Windows, CI forces MSVC
`cl.exe` (`CXX=cl`) and verifies that the selected compiler family is
MSVC; the MinGW fallback remains available for local builds but is not
used by CI.

## Editor support

A VS Code syntax-highlighting extension lives in
[`editors/vscode`](editors/vscode/). See its README for local packaging and
installation instructions.

## Documentation

* **[Franko Language Reference](language_docs/Franko_Language_Reference.md)**
  — the language, checker rules, and standard modules. Per-chapter sources
  are in [`language_docs/`](language_docs/).
* **[Compiler Internals](compiler_docs.md)** — the compiler pipeline,
  C++14 generation, runtime, and build/test tooling.
* **[Library Reference](library_docs/)** — `franko/error`, `franko/stdio`,
  `franko/limit`, `franko/os`, `franko/pool`, `franko/string`, and
  `franko/time`.
