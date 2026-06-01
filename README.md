
# Franko

Franko is an experimental systems-style programming language with explicit memory control, typed arrays, declaration desugaring, and structured control flow.

The compiler frontend is built with **ANTLR4 + Java**, lowers source code into a typed AST, runs a **desugaring pass**, and then generates **C++14** code against a small custom runtime.

---

## Current Status

Franko currently supports:

- scalar variable declarations
- heap variable declarations via `alloc`
- dynamic arrays
- static arrays
- array indexing
- array initialization / uninitialization
- `memset` / `memcpy`
- assignment
- arithmetic expressions
- comparison operators
- `if / else`
- `while`
- `print(...)`
- declaration sugar such as:
  - `int32_t x = 1;`
  - `array<int32_t> arr(20);`

Franko source is compiled through the following pipeline:

```text
Franko source
→ ANTLR parse tree
→ raw AST
→ desugared core AST
→ C++14 codegen
→ g++ executable
````

***

## Example

### Franko source

```franko
int32_t x;
int32_t y = 20;
x = 10;

if (x < y)
    print(x);

while (x != 0) {
    print(x);
    x = x - 1;
}
```

### Generated C++

```cpp
int32_t x;
int32_t y;
y = 20;
x = 10;
if ((x < y))
    std::cout << x << '\n';
while ((x != 0))
{
    std::cout << x << '\n';
    x = (x - 1);
}
```

***

## Features

### Types

* `int32_t`
* `uint32_t`
* `float32_t`
* `char8_t`
* `array<T>`
* `array<T, N>`

### Statements

* variable declaration
* assignment
* `del`
* array initialization: `a(10)`
* array uninitialization: `a.uninit()`
* array memset: `a.memset(0)`
* array memcpy: `a.memcpy(b)`
* `print(...)`
* `if / else`
* `while`
* blocks `{ ... }`

### Expressions

* integer literals
* variable references
* array indexing
* unary minus
* arithmetic:
  * `+`
  * `-`
  * `*`
  * `/`
* comparisons:
  * `==`
  * `!=`
  * `<`
  * `<=`
  * `>`
  * `>=`

### Syntactic Sugar

Franko currently desugars the following source forms into a smaller core AST:

* scalar declaration with initializer
  ```franko
  int32_t x = 1;
  ```

* heap scalar declaration with initializer
  ```franko
  alloc int32_t y = 10;
  ```

* dynamic array declaration with initialization
  ```franko
  array<int32_t> arr(20);
  alloc array<int32_t> buf(100);
  ```

***

## Requirements

* Linux
* Java
* ANTLR4 tool
* `g++` with C++14 support

***

## Project Structure

```text
.
├── ASTNode.java           # AST node definitions
├── ASTPrinter.java        # AST pretty-printer
├── Cpp14Codegen.java      # C++14 backend
├── Desugarer.java         # syntax sugar lowering pass
├── Franko.g4              # ANTLR grammar
├── FrankoASTVisitor.java  # parse tree -> AST
├── Main.java              # compiler entry point
├── TypeNode.java          # type hierarchy
├── ProgramTemplate.cpp    # C++ template
├── include/
│   └── FrankoRuntime.hpp  # runtime support
├── test.fr                # sample Franko program
├── update.sh              # rebuild/regenerate/run script
└── build/
    ├── generated/         # ANTLR-generated Java files
    ├── classes/           # compiled .class files
    ├── out.cpp            # generated C++ output
    └── out.out            # compiled executable
```

***

## Build / Run

Use the provided update script:

```bash
./update.sh
```

The script:

1. regenerates ANTLR sources
2. recompiles Java sources
3. parses the Franko source
4. prints the raw AST
5. prints the desugared AST
6. generates `build/out.cpp`
7. compiles the generated C++
8. runs the final executable

### Interactive Mode

The script is interactive and pauses before each step so you can inspect the pipeline as it runs.

Press:

* **Enter** to execute the next step
* **Ctrl+C** to abort

***

## Compiler Stages

### 1. Parse

ANTLR4 parses `test.fr` using `Franko.g4`.

### 2. Raw AST

`FrankoASTVisitor.java` converts the parse tree into a structured AST.

This raw AST may still contain sugar nodes such as:

* `VarDeclInitNode`
* `VarDeclArrayInitNode`

### 3. Desugaring

`Desugarer.java` lowers sugar into a smaller core AST.

Examples:

```franko
int32_t x = 1;
```

becomes:

```franko
int32_t x;
x = 1;
```

and:

```franko
array<int32_t> arr(20);
```

becomes:

```franko
array<int32_t> arr;
arr(20);
```

### 4. Code Generation

`Cpp14Codegen.java` emits C++14 from the desugared AST.

### 5. Native Compilation

`g++` compiles the generated C++ against `FrankoRuntime.hpp`.

***

## Type System

Franko uses a structured type representation in the compiler rather than raw strings.

### Internal type nodes

* `PrimitiveTypeNode`
* `DynamicArrayTypeNode`
* `StaticArrayTypeNode`

### Primitive kinds

* `INT32`
* `UINT32`
* `FLOAT32`
* `CHAR8`

This makes later semantic analysis and backend code generation much more robust.

***

## Runtime Model

Franko currently uses a small C++ runtime that provides:

* dynamic arrays
* static arrays
* bounds-checked indexing
* initialization / uninitialization
* `memset`
* `memcpy`

Heap variables declared with `alloc` are lowered to C++ heap objects.

***

## Example Program: Sieve of Eratosthenes

Franko is already capable of expressing nontrivial algorithms.

Example: generating primes up to 80 with a dynamic array and nested loops:

```franko
int32_t n = 80;
array<int32_t> isPrime(n + 1);

int32_t i = 0;
while (i <= n) {
    isPrime[i] = 1;
    i = i + 1;
}

isPrime[0] = 0;
isPrime[1] = 0;

int32_t p = 2;
while (p * p <= n) {
    if (isPrime[p] != 0) {
        int32_t j = p * p;
        while (j <= n) {
            isPrime[j] = 0;
            j = j + p;
        }
    }
    p = p + 1;
}

i = 2;
while (i <= n) {
    if (isPrime[i] != 0) {
        print(i);
    }
    i = i + 1;
}

isPrime.uninit();
```

***

## Roadmap

Planned next steps include:

* semantic analysis
  * undeclared-variable checking
  * invalid sugar detection
  * array legality checks
  * heap/stack legality checks
* `for` loop support
* better scoping / symbol management
* stronger type checking
* eventual lowering toward Franko’s long-term slab/offset execution model

***

## Notes

* Franko currently uses **Java at compile time**, not runtime.
* The language backend currently targets **C++14** as a convenient and debuggable intermediate target.
* The long-term direction is toward a more explicit execution/storage model.

***

## License

MIT

