
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
quicksort.fr
sieve.fr
string.fr
```

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

