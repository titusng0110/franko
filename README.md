# Franko

Franko is a small statically typed language that compiles to C++14 and
builds native executables.

## Why Franko?

Franko's philosophy is clean, non-cryptic syntax like Python; low-level control without C++ footguns; and a much easier learning curve than Rust.
Its defining features are:

- **Pointer capabilities.** `ptr<T>` owns, `mview<T>` mutates, and `view<T>`
  reads. The permission lattice is enforced by the compiler, with no raw
  pointer arithmetic or ownership annotations.
- **Interprocedural memory safety checkers.** The compiler follows pointer
  values across calls and checks null state, dangling frame pointers, and
  dynamic-array lifecycle.
- **Compile-time `#if` / `#else` splices.** Select statements or declarations
  at function, global, namespace, and struct scope. The selected branch is
  ordinary code; the other branch does not exist. No trait solver, SFINAE, or
  template metaprogramming. There are built-in type predicates such as `#is_memcpy_safe`, `#is_value_assignable`, and `#is_comparable`.
- **No hidden heap allocation or copying.** Dynamic arrays have a monopoly
  on the heap: they are explicitly `init`/`resize`/`uninit`ed. No garbage collection (GC), implicit deep copy, or hidden
  memory allocation.
- **Readable, regular syntax.** C-like but predictable, with explicit `$(x)`
  dereferencing and no semicolons. Declaration is always `TYPE IDENTIFIER`.
- **Small type model.** Fixed-width integers, enums, structs with methods and
  operators, tuples, aggregate literals, namespaces, arrays, and typed
  pointers. No inheritance or implicit integer type casting.
- **Byte-level array intrinsics without pointer arithmetic.** `memcpy`,
  `memset`, `memmove`, `memcmp`, searches, endian access, and bit operations
  are built in. They work on arrays and views directly, so byte-level work
  needs no pointer arithmetic; the language enforces their receiver and
  element rules.
- **FFI without binding generators.** Small JSON sidecars plus C++ bridge
  blocks; target selection uses the same `#if`.
- **Native performance, readable output.** Ahead-of-time C++14 with no VM or
  GC; the [red-black benchmark](benchmark/redblack.md) is competitive with C++
  and beats `std::set` at `-Omax`, and the generated source is ordinary C++14.

## A taste

```franko
import "franko/io"

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
    print(c.get())

    #if (#IS_WINDOWS) {
        print("windows")
    } #else {
        print("posix")
    }

    for (int32 i = 0; i < 3; i++) {
        for (int32 j = 0; j < 3; j++) {
            if (j == 1) { break 2 }   // exits both loops
            print(i + j)
        }
    }

    return 0
}
```

## Requirements

- [Java 25](https://adoptium.net/) (`java` and `javac` on `PATH`).
  On Windows ARM64, use JDK 23 and set `FRANKO_JAVA_RELEASE=23`.
- A C++14 compiler: `g++` or `clang++` on `PATH` (set `CXX` to choose
  one explicitly).
- [uv](https://docs.astral.sh/uv/) with Python 3.13+.

Platform notes:

* **Linux x86_64 / arm64**: `g++` or `clang++`
* **Windows x86_64**: MinGW-w64 `g++` (recommended: [x86_64 POSIX SEH
  UCRT MinGW-w64](https://github.com/niXman/mingw-builds-binaries/releases));
  add its `bin` directory (for example
  `C:\x86_64-16.2.0-release-posix-seh-ucrt-rt_v14-rev1\mingw64\bin`) to
  `PATH` before running the scripts
* **Windows ARM64**: MSYS2 `clangarm64` `clang++` (install
  `mingw-w64-clang-aarch64-clang`, add `C:\msys64\clangarm64\bin` to
  `PATH`)
* **macOS (x86_64 / arm64)**: Apple clang or `g++`

The repository already ships the ANTLR jar, the Franko runtime headers,
and bundled jemalloc for every supported platform.

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

Build modes:

* **Default**: on Linux/macOS, `-O1` with AddressSanitizer and leak
  checking. On Windows, AddressSanitizer is unsupported, so the default
  is `-O`.
* **`-O`**: optimized (`-O3`) with bundled jemalloc, no sanitizer.
* **`-Omax`**: like `-O`, but with bounds, bit/offset, and
  `unsafe_cast` range checks stripped. Out-of-range access is undefined
  behavior; null and uninitialized-state checks remain.

```bash
uv run python compile.py -O programs/binary_codec.fr
uv run python compile.py -Omax programs/binary_codec.fr
uv run python compile.py --target native programs/binary_codec.fr
```

`--target native` adds `-march=native`; an explicit `<os>-<arch>` target
(for example `linux-x86_64`) transpiles without building a native binary
on a different host.

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
uv run python language_tests/run_language_tests.py
uv run python language_tests/run_omax_smoke.py
uv run python library_tests/run_library_tests.py
uv run python programs/run_programs.py
```

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
* **[Library Reference](library_docs/)** — `franko/error`, `franko/io`,
  `franko/limit`, `franko/os`, `franko/pool`, and `franko/string`.
