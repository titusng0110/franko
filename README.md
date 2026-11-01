# Franko

Franko is a small statically typed language that compiles to C++14 and
builds native executables.

***

## Requirements

Required on every platform:

* [Java 25](https://adoptium.net/) (`java` and `javac` on `PATH`).
  Windows ARM64 has no JDK 25 build yet; use the preinstalled JDK 23 and
  set `FRANKO_JAVA_RELEASE=23`
* a C++14 compiler: `g++` or `clang++` on `PATH` (set `CXX` to pick one
  explicitly)
* [uv](https://docs.astral.sh/uv/) with Python 3.13+ (for the build and
  test scripts)

The build and test scripts are plain Python (stdlib only), so the same
commands work on Linux, macOS and Windows.

Platform notes:

* **Linux x86_64 / arm64**: `g++` or `clang++`
* **Windows x86_64**: MinGW-w64 `g++` (recommended: [x86_64 POSIX SEH
  UCRT MinGW-w64](https://github.com/niXman/mingw-builds-binaries/releases));
  add its `bin` directory (e.g. `C:\x86_64-16.2.0-release-posix-seh-ucrt-rt_v14-rev1\mingw64\bin`) to `PATH` before
  running the scripts
* **Windows ARM64**: MSYS2 `clangarm64` `clang++` (install
  `mingw-w64-clang-aarch64-clang` and add `C:\msys64\clangarm64\bin` to
  `PATH`)
* **macOS (x86_64 / arm64)**: Apple clang or `g++`

The repository already ships everything else: the ANTLR jar, the Franko
runtime headers, and bundled jemalloc for every supported platform.

***

## Install

```bash
git clone https://github.com/titusng0110/franko.git
cd franko
uv run python update_toolchain.py
```

The last command builds the Franko compiler toolchain. Re-run it whenever
the grammar or compiler sources change (`compile.py` also builds it
automatically if it is missing).

On Windows ARM64, set `FRANKO_JAVA_RELEASE=23` before running it (or pass
`--java-release 23`).

***

## Compile a program

```bash
uv run python compile.py programs/bfs.fr
```

This generates `programs/bfs.cpp` and a native executable next to the
source (`programs/bfs.out` on Linux/macOS, `programs/bfs.exe` on
Windows):

```bash
./programs/bfs.out          # Linux / macOS
```

```powershell
.\programs\bfs.exe          # Windows
```

There are two build modes plus an unchecked max-speed mode:

* **default** — on Linux and macOS: `-O1` with AddressSanitizer
  (including leak checking) and the system allocator. Use this while
  developing and testing: it reports memory errors and leaks (for example
  a missing `arr.uninit()`) at run time. On Windows, AddressSanitizer is
  unsupported, so the default is the optimized `-O` build below.
* **`-O`** — optimized (`-O3`) with the bundled jemalloc allocator and
  no sanitizer. Use this for the final fast binary.
* **`-Omax`** — like `-O`, but against an unchecked runtime: array
  bounds, bit/offset, and `unsafe_cast` range checks are stripped.
  Out-of-range accesses are then undefined behavior. Null and
  uninitialized-state checks remain. For developers who have already
  validated their program and want maximum speed.

```bash
uv run python compile.py -O programs/bfs.fr
uv run python compile.py -Omax programs/bfs.fr
```

For valid programs, all modes implement the same language with identical
compiler diagnostics and program output; only the allocator, the
optimization level, and the runtime checking differ.

### Target selection

```bash
uv run python compile.py --target native programs/bfs.fr
```

`--target native` builds for the host CPU and adds `-march=native` to the
C++ step, so the binary requires a matching CPU. You can also pass an
explicit `<os>-<arch>` target (`linux`, `windows`, or `macos` with
`x86_64` or `arm64`). A target that differs from the host runs only the
Franko-to-C++ transpilation and skips the native C++ build.

***

## Example programs

Working Franko programs are in [`programs/`](programs/). Read these
first — they cover most of what you need to write Franko programs:

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

Compile and run any of them the same way as above.

***

## Documentation

* **[Franko Language Reference](language_docs/Franko_Language_Reference.md)**
  — the language: types, declarations, functions, arrays, pointers,
  structs, methods, and all the checker rules. The split per-chapter
  sources live in [`language_docs/`](language_docs/).
* **[Compiler Internals](compiler_docs.md)** — how the compiler works:
  the phase pipeline, code generation to C++14, the runtime headers, and
  the build/test tooling.

For library modules (`franko/io`, `franko/limit`, `franko/os`,
`franko/pool`), see [`library_docs/`](library_docs/).
