
# Franko Language (v0.1)

A minimal systems programming language with explicit memory control.

This project uses:
- ANTLR4 (C++ target)
- Custom grammar (`Franko.g4`)
- C++ runtime (ANTLR4 v4.13.2)

---

# ✅ Requirements

- Linux (tested on Ubuntu)
- g++ (with C++17 support)
- Java (for ANTLR tool)
- CMake

---

# ✅ Step 1: Install ANTLR tool (Java)

```bash
antlr4
````

If not installed, install it however you prefer (package manager or jar alias).

***

# ✅ Step 2: Generate C++ parser

```bash
antlr4 -Dlanguage=Cpp Franko.g4
```

This generates:

* FrankoLexer.cpp / .h
* FrankoParser.cpp / .h
* FrankoListener.\*
* FrankoBaseListener.\*

***

# ✅ Step 3: Build ANTLR4 C++ runtime (v4.13.2)

Clone/download:

```
antlr4-cpp-runtime-4.13.2-source/
```

***

## ✅ IMPORTANT: build from ROOT (not runtime/)

```bash
cd antlr4-cpp-runtime-4.13.2-source

rm -rf build
mkdir build
cd build

cmake -DANTLR4_BUILD_CPP_TESTS=OFF ..
make -j$(nproc)
sudo make install
```

***

## ✅ Refresh linker

```bash
sudo ldconfig
```

***

# ✅ Step 4: Build Franko compiler

From project root:

```bash
g++ -std=c++17 \
    FrankoLexer.cpp \
    FrankoParser.cpp \
    FrankoBaseListener.cpp \
    FrankoListener.cpp \
    main.cpp \
    -I/usr/local/include/antlr4-runtime \
    -L/usr/local/lib \
    -lantlr4-runtime \
    -o franko
```

***

# ✅ Step 5: Run

```bash
./franko test.fr
```

***

# ✅ Output Example

```text
(program (statement ...) ...)
```

This is the parse tree of your input program.

***

# ✅ Project Structure

```
Franko.g4              # Grammar
FrankoLexer.*          # Generated
FrankoParser.*         # Generated
FrankoListener.*       # Generated

main.cpp               # Entry point

test.fr                # Example program
```

***

# ✅ Notes

* Uses ANTLR C++ runtime v4.13.2
* Fully native C++ pipeline (no Java at runtime)
* Java is only used to run ANTLR tool
* Parse tree replaces `grun`

***

# 🚀 Next Steps

* Add Visitor (`-visitor`)
* Build semantic checker
* Generate slab-based C++ output

````

---

# ✅ Optional: add a quick sanity script (nice bonus)

You can also add:

```bash
#!/bin/bash
antlr4 -Dlanguage=Cpp Franko.g4
g++ -std=c++17 *.cpp \
    -I/usr/local/include/antlr4-runtime \
    -L/usr/local/lib \
    -lantlr4-runtime \
    -o franko
````

***

# 🎯 One-line takeaway

> ✅ You now have a reproducible, documented pipeline for building and running your Franko compiler entirely in C++.

