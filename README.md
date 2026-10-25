
# Franko

Franko is a programming language compiler that emits C++14 and builds native executables.

For language details, see:

[Franko Language Reference](https://github.com/titusng0110/franko/blob/main/language_docs/Franko_Language_Reference.md)

***

## Requirements

Required on every platform:

* Java 25
* a C++14 compiler (`g++`)
* [uv](https://docs.astral.sh/uv/) with Python 3.13+

The build and test scripts are plain Python (stdlib only), so one command
works identically on Linux, macOS and Windows — there are no separate
shell/PowerShell scripts to keep in sync.

### Linux x86_64 / arm64

* `g++`

The repository already includes:

* ANTLR jar
* Franko runtime headers
* bundled jemalloc for Linux x86_64 and Linux arm64

### Windows x64

* MinGW-w64 `g++`

Recommended Windows compiler:

[x86_64 POSIX SEH UCRT MinGW-w64 16.1.0](https://github.com/niXman/mingw-builds-binaries/releases)

The scripts find `g++.exe` on `PATH` first and then probe the usual MSYS2
locations (`C:\msys64\ucrt64\bin`, ...), so no manual `PATH` fixup is
needed.

The repository already includes:

* ANTLR jar
* Franko runtime headers
* bundled jemalloc for Windows x64

### macOS (x86_64 / arm64)

* Apple clang or `g++`

The repository already includes bundled jemalloc for macOS x86_64 and
macOS arm64.

***

## Clone

```bash
git clone https://github.com/titusng0110/franko.git
cd franko
````

***

## Build the Compiler Toolchain

Run this whenever the grammar or compiler source changes:

```bash
uv run python update_toolchain.py
```

The steps are non-interactive, so this is also what CI runs. `compile.py`
builds the toolchain automatically when it is missing.

***

## Compile a Franko Program

```bash
uv run python compile.py programs/bfs.fr
```

This generates:

```text
programs/bfs.cpp
programs/bfs.out     (Linux / macOS)
programs/bfs.exe     (Windows)
```

Run it:

```bash
./programs/bfs.out
```

```powershell
.\programs\bfs.exe
```

***

## Example Programs

Example Franko programs are in:

```text
programs/
```

Examples include:

```text
bfs.fr
mergesort.fr
null.fr
quicksort.fr
sieve.fr
string.fr
```

The `memorytest.fr`, `nulltest.fr`, and `nulllegal.fr` programs are checker torture tests that are expected to fail compilation with diagnostics.

Compile any example the same way:

```bash
uv run python compile.py programs/quicksort.fr
./programs/quicksort.out
```

```powershell
uv run python compile.py programs\quicksort.fr
.\programs\quicksort.exe
```

***

## Testing

All three suites are Python runners, so the command is the same on every
platform. Build the toolchain first (`uv run python update_toolchain.py`)
if you have not run it yet; the suites will tell you if it is missing.

### Smoke test (example programs)

Compiles every program in `programs/`, runs it, and compares stdout with the checked-in `.expected` file.

```bash
uv run python programs/run_programs.py
```

### Full spec suite

Runs all checker and runtime cases:

- `tests/failure/` programs must be rejected by the compiler;
- `tests/success/` programs must compile, run, and match `.expected`;
- `tests/runtime/` programs must compile, then abort at run time.

```bash
uv run python tests/run_specs_tests.py
```

### Library tests

Tests the importable standard-library modules in `library_tests/`
(`tests/` covers the language itself and never imports library modules):

- `library_tests/<module>/<suite>/success/` programs must compile, run, and match `.expected`;
- `library_tests/<module>/<suite>/failure/` programs must be rejected by the compiler;
- `library_tests/<module>/<suite>/runtime/` programs must compile, then abort at run time.

Suites named `win32` run on Windows only and are skipped elsewhere; suites
named `posix` run on non-Windows platforms only and are skipped on
Windows. Optional `main.args` feeds command-line arguments and
`main.stdin` feeds stdin to the test binary.

```bash
uv run python library_tests/run_library_tests.py
```

***

## Clean Generated Outputs

Generated files are written next to the source file.

For example:

```text
programs/bfs.fr
programs/bfs.cpp
programs/bfs.out
programs/bfs.exe
```

You can delete generated `.cpp`, `.out`, or `.exe` files manually if needed.

***



## Notes

Franko currently uses a C++14 backend.

`compile.py` generates C++ first, then invokes `g++` to produce a native executable.

The test suites do the same per case, then compare the program output
against the checked-in `.expected` files (comparisons are newline-safe, so
a CRLF checkout behaves like an LF one).

