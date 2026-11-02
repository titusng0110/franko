# Franko

Franko is a small statically typed language that compiles to C++14 and
builds native executables.

## Why Franko?

- **A small, no-brainer syntax.** C-like and regular, with explicit
  `self` receivers and no hidden control flow.
- **Easy type model.** Fixed-width integers, structs, arrays, and pointers;
  no inheritance or implicit width changes.
- **Compile-time memory safety.** Per-function (intraprocedural) checks
  catch null dereferences, dangling returns, and dynamic-array lifecycle
  misuse. Pointer permissions are explicit: `ptr<T>` owns, `mview<T>`
  mutates, and `view<T>` only reads.
- **Fast, statically checked array intrinsics.** `memcpy`, `memset`,
  `memmove`, `memcmp`, searches, endian accessors, and bit operations are
  built in; element types, ranges, and receiver permissions are checked
  at compile time.
- **Native performance.** Ahead-of-time C++14 output with no VM or GC;
  the [red-black benchmark](benchmark/redblack.md) is competitive with
  C++ and beats `std::set` at `-Omax`.
- **Readable C++14 output.** C++14 is mature and very portable, and the
  generated C++ is ordinary source you can inspect and debug.

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
uv run python compile.py programs/bfs.fr
```

This writes `programs/bfs.cpp` and a native executable next to the source
(`programs/bfs.out` on Linux/macOS, `programs/bfs.exe` on Windows).

Build modes:

* **Default**: on Linux/macOS, `-O1` with AddressSanitizer and leak
  checking. On Windows, AddressSanitizer is unsupported, so the default
  is `-O`.
* **`-O`**: optimized (`-O3`) with bundled jemalloc, no sanitizer.
* **`-Omax`**: like `-O`, but with bounds, bit/offset, and
  `unsafe_cast` range checks stripped. Out-of-range access is undefined
  behavior; null and uninitialized-state checks remain.

```bash
uv run python compile.py -O programs/bfs.fr
uv run python compile.py -Omax programs/bfs.fr
uv run python compile.py --target native programs/bfs.fr
```

`--target native` adds `-march=native`; an explicit `<os>-<arch>` target
(for example `linux-x86_64`) transpiles without building a native binary
on a different host.

## JSON pipeline artifacts

`--emit-json` writes a snapshot after every top-level compiler pass next
to the generated `.cpp`:

```bash
uv run python compile.py --emit-json programs/bfs.fr
```

This produces `programs/bfs.raw.ast.json`, `bfs.desugared.ast.json`,
`bfs.imports.ast.json`, one `.sast.json` per semantic pass, and
`bfs.diagnostic.json`. Imported files get per-file AST snapshots under
`programs/bfs.ast/`. Diagnostics print with `file:line:column:` locations.
Round-trip validation is available without a new dependency:

```bash
java -cp build/classes CompilerJsonSelfTest programs/bfs.checked.sast.json
```

## Example programs

Working programs live in [`programs/`](programs/):

```text
programs/bfs.fr          breadth-first search
programs/quicksort.fr    quicksort
programs/mergesort.fr    mergesort
programs/sieve.fr        prime sieve
programs/redblack.fr     red-black tree
programs/stack.fr        stack data structure
programs/string.fr       string handling
programs/args.fr         command-line arguments
programs/greetings.fr    reading stdin
```

Compile and run them the same way as above.

## Documentation

* **[Franko Language Reference](language_docs/Franko_Language_Reference.md)**
  — the language, checker rules, and standard modules. Per-chapter sources
  are in [`language_docs/`](language_docs/).
* **[Compiler Internals](compiler_docs.md)** — the compiler pipeline,
  C++14 generation, runtime, and build/test tooling.
* **[Library Reference](library_docs/)** — `franko/io`, `franko/limit`,
  `franko/os`, and `franko/pool`.
