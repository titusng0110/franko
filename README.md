
# Franko

Franko is a programming language compiler that emits C++14 and builds native executables.

For language details, see:

[Franko Language Reference](https://github.com/titusng0110/franko/blob/main/language_docs/Franko_Language_Reference.md)

***

## Requirements

### Linux x86_64

Required:

* Java 25
* `g++`
* Bash

The repository already includes:

* ANTLR jar
* Franko runtime headers
* bundled jemalloc for Linux x86_64

### Windows x64

Required:

* Java 25
* MinGW-w64 `g++`
* PowerShell 5.1 or PowerShell 7+

Recommended Windows compiler:

[x86_64 POSIX SEH UCRT MinGW-w64 16.1.0](https://github.com/niXman/mingw-builds-binaries/releases)

The repository already includes:

* ANTLR jar
* Franko runtime headers
* bundled jemalloc for Windows x64

***

## Clone

```bash
git clone https://github.com/titusng0110/franko.git
cd franko
````

***

## Build the Compiler Toolchain

Run this whenever the grammar or compiler source changes.

### Linux x86\_64

```bash
./update_linux-x86_64.sh
```

### Windows x64

```powershell
.\update_windows-x64.ps1
```

***

## Compile a Franko Program

### Linux x86\_64

```bash
./compile_linux-x86_64.sh programs/bfs.fr
```

This generates:

```text
programs/bfs.cpp
programs/bfs.out
```

Run it:

```bash
./programs/bfs.out
```

### Windows x64

```powershell
.\compile_windows-x64.ps1 .\programs\bfs.fr
```

This generates:

```text
programs\bfs.cpp
programs\bfs.exe
```

Run it:

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

### Linux

```bash
./compile_linux-x86_64.sh programs/quicksort.fr
./programs/quicksort.out
```

### Windows

```powershell
.\compile_windows-x64.ps1 .\programs\quicksort.fr
.\programs\quicksort.exe
```

***

## Testing

### Smoke test (example programs)

Compiles every program in `programs/`, runs it, and compares stdout with the checked-in `.expected` file.

```bash
./programs/run_programs.sh
```

```powershell
.\programs\run_programs.ps1
```

### Full spec suite

Runs all checker and runtime cases:

- `tests/failure/` programs must be rejected by the compiler;
- `tests/success/` programs must compile, run, and match `.expected`;
- `tests/runtime/` programs must compile, then abort at run time.

```bash
./tests/run_specs_tests.sh
```

```powershell
.\tests\run_specs_tests.ps1
```

### Library tests

Tests the importable standard-library modules in `library_tests/`
(`tests/` covers the language itself and never imports library modules):

- `library_tests/<module>/<suite>/success/` programs must compile, run, and match `.expected`;
- `library_tests/<module>/<suite>/failure/` programs must be rejected by the compiler;
- `library_tests/<module>/<suite>/runtime/` programs must compile, then abort at run time.

Suites named `win32` run on Windows only and are skipped elsewhere.
Optional `main.args` feeds command-line arguments and `main.stdin`
feeds stdin to the test binary.

```bash
./library_tests/run_library_tests.sh
```

```powershell
.\library_tests\run_library_tests.ps1
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

The compile scripts generate C++ first, then invoke `g++` to produce a native executable.

