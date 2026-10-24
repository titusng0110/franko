# 31. Runtime and Backend Notes

The current compiler targets C++14.

## 31.1 Pipeline Order

```text
Franko source
  -> lexer + parser
  -> parser AST
  -> Desugarer
  -> SemanticAnalyzer
  -> LegalityChecker
  -> MemorySafetyChecker
  -> C++14 code generation
```

## 31.2 Memory Allocation

The generated code allocates through bundled jemalloc (`je_malloc`, `je_calloc`, `je_realloc`, `je_free`).

Heap variables (`alloc T x`) compile to a jemalloc-allocated pointer with placement construction, and `del x` compiles to the object destructor plus `je_free`.

Dynamic array element buffers are allocated by the array runtime methods and freed by `uninit()` or by the array object's destructor.

## 31.3 Array Representation

Some Franko rules mirror the generated C++ representation, especially array behavior.

For example, array `memcpy` is designed to support combinations such as:

```text
Static<T, N>.memcpy(Static<T, M>)
Static<T, N>.memcpy(Dynamic<T>)
Dynamic<T>.memcpy(Dynamic<T>)
Dynamic<T>.memcpy(Static<T, M>)
```

Static array sizes must be greater than zero because generated C++ uses fixed-size arrays, and zero-length C++ arrays are not standard.

Static and dynamic arrays runtime-check indexes and intrinsics and raise `std::runtime_error` (via the runtime panic helper) on violations such as out-of-bounds access, use of an uninitialized dynamic array, double initialization, or zero-size allocation.

## 31.4 Ordering of Top-Level Analysis

Function signatures are registered before function bodies are analyzed so that forward references, direct recursion, and mutual recursion can be resolved.

Global variable declarations are also registered before function bodies are analyzed, so functions can reference globals declared later in source order.

## 31.5 Desugaring Before Checking

Array initializer lists are lowered before ordinary semantic checking.

For example:

```franko
xs = [1, 2, 3];
```

is lowered to:

```franko
xs[0] = 1;
xs[1] = 2;
xs[2] = 3;
```

Nested initializer lists are lowered recursively.

String literals are converted to byte array literals in the same pass (see [String Literals](22-string-literals.md)).

`ndarray` types are lowered to nested static arrays before type analysis.

The backend does not need to implement initializer lists or strings as runtime values because they are not values in Franko.

## 31.6 Integer Literal Emission

Large integer literals are emitted so that they remain well-formed C++14. In particular, an unsuffixed decimal literal whose value exceeds `LLONG_MAX` is emitted with a trailing `ULL` suffix, because decimal literals in C++14 do not fall back to unsigned integer types.

## 31.7 Print Emission

`print` arguments are emitted as `std::cout` insertions:

- plain integer primitives are inserted directly;
- `int8_t`/`uint8_t` values are promoted with unary `+` so a number is printed rather than a character;
- addresses are cast to `const void*` to avoid accidental string printing.

See [Statements](18-statements.md) for which types are meaningfully printable.

## 31.8 Identifier Emission and Name Clashes

Franko keeps struct type names, variable names, and function names in separate namespaces, but C++14 does not. To keep the generated code unambiguous, every declared identifier is emitted with a kind prefix:

| Franko construct | Generated C++ name |
|---|---|
| struct type `X` | `STRUCT_X` |
| variable `x` (local, global, parameter, heap) | `VAR_x` |
| function `f` | `FUNC_f` |
| method `f` of struct `X` | `METH_f_OFSTRUCT_X` |
| namespace `n` | `NS_n` |
| the entry-point function `main` | `main` (unprefixed: the generated C++ entry point IS the Franko `main`) |

So a declaration `X s;` is emitted as `STRUCT_X VAR_s;`.

Because of the prefixes, a variable, function, and struct may share one source name without colliding in the generated C++.

Method names mangle with both the method and the owning struct (`METH_f_OFSTRUCT_X`), so methods on different structs never collide; `self` emits as an ordinary `VAR_self` parameter of type `STRUCT_X*`.

Namespaces are emitted as C++ namespaces with the `NS_` prefix, so a Franko reference `n::f` becomes `NS_n::FUNC_f`. Unqualified references inside a namespace stay unqualified, and global-namespace symbols referenced from inside a namespace are rooted with `::`.

When a variable or function shares its name with a declared struct, the compiler emits a **warning** during semantic analysis (the program still compiles):

```text
==== Warnings ====
  - Variable 'X' shares its name with struct 'X'; generated C++ uses VAR_X for the variable and STRUCT_X for the struct type
```

Struct definitions are emitted after a forward-declaration block (`struct STRUCT_X;` for every declared struct), so `addr<X>` fields and pointer references may name structs declared later in source order.