# 31. Runtime and Backend Notes

The current compiler targets C++14.

## 31.1 Pipeline Order

```text
Franko source
  -> lexer + parser
  -> parser AST
  -> Desugarer (sugar lowering, `?` inference, constExpr evaluation)
  -> SemanticAnalyzer (validation of evaluated values, no re-folding, entry-point selection)
  -> LegalityChecker
  -> MemorySafetyChecker
  -> C++14 code generation
  -> program template wrapper (entry-point call + argv conversion)
```

## 31.2 Memory Allocation

The generated code allocates through bundled jemalloc (`je_malloc`, `je_calloc`, `je_realloc`, `je_free`).

Heap variables (`alloc T x`) compile to a jemalloc-allocated pointer with placement construction, and `del x` compiles to the object destructor plus `je_free`.

Dynamic array element buffers are allocated by the array runtime methods and freed by `uninit()` or by the array object's destructor.

Element construction follows the C++ type of the elements: buffers for trivially default-constructible element types keep raw storage, while any other element type is default-constructed (placement new) when the buffer is allocated. `uninit()` and the array destructor destroy constructed elements before freeing the buffer. Trivially destructible element types skip destruction entirely.

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

The dynamic array length is stored in a `count` field with a `length()` accessor returning `uint32_t`. The field was renamed from `length` because C++ does not allow a field and a method with the same name. Reading `length()` on an uninitialized dynamic array returns `0` and never aborts; every other uninitialized access still aborts. Static arrays keep plain contiguous `T data[N]` storage with no length field or method: `staticarr.length()` in Franko source is emitted by the compiler as a constant, never as a runtime call.

Zero length and uninitialized coincide exactly: `init(0)` and `resize(0)` abort, and `uninit()` restores `data == nullptr, count == 0`, so `length() == 0` holds exactly when the array is uninitialized — there is no initialized empty array.

Element lifetime in the dynamic array runtime:

- `init(n)` and `init_zero(n)` default-construct all `n` elements when the element type needs construction; trivially constructible element types keep raw (`init`) or zeroed (`init_zero`) storage with no per-element work.
- `resize(n)` default-constructs the grown tail when growing and destroys the dropped tail when shrinking; trivial element types skip both.
- `uninit()` and the array destructor destroy all `count` elements before freeing the buffer; trivially destructible element types skip destruction.
- `je_realloc` byte-moves the existing elements on `resize`, which is fine for the runtime's trivially-relocatable element types.

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

`?` static array sizes are inferred during the same pass: each marker is replaced by the corresponding initializer level's element count (strings count as UTF-8 bytes) before type analysis, so the backend only ever sees folded constant sizes. Static `length()` calls are likewise emitted as constants.

Every other `constExpr` slot is also evaluated in this pass: static array sizes, `ndarray` dimensions, global initializers, and struct field defaults become canonical decimal literals before semantic analysis (see [Constant Folding](10-constant-folding.md)). Semantic analysis validates the resulting values (positivity, range, fit) without re-folding them. Evaluation failures such as division by zero are reported here, as desugaring diagnostics.

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
| function `f` | `FUNC_f_HASH_<8hex>` |
| method `f` of struct `X` | `METH_f_OFSTRUCT_X_HASH_<8hex>` |
| namespace `n` | `NS_n` |
| the entry-point function `main` | `FUNC_main_HASH_<8hex>` like every other function (called by the template wrapper) |

So a declaration `X s;` is emitted as `STRUCT_X VAR_s;`.

Because of the prefixes, a variable, function, and struct may share one source name without colliding in the generated C++.

Each function and method name carries an 8-hex-digit suffix: the FNV-1a 32-bit hash of its canonical signature key. The key spells the declared parameter types in order (including `self` for methods), then the return type, each exactly as written in source (`int32_t`, `uint64_t`, `void`, `array<T>`, `array<T,N>` with canonical folded decimal size and no space, `addr<T>`, qualified struct names such as `a::b::X`), joined as `fn(P1,P2,...)->R`. For example `func f(int32_t x) -> int32_t` hashes the key `fn(int32_t)->int32_t`. Overloads of one name therefore emit distinct C++ names without relying on g++ overload resolution.

Method names mangle with both the method and the owning struct (`METH_f_OFSTRUCT_X_HASH_<8hex>`), so methods on different structs never collide; `self` emits as an ordinary `VAR_self` parameter of type `STRUCT_X*`.

Namespaces are emitted as C++ namespaces with the `NS_` prefix, so a Franko reference `n::f` becomes e.g. `NS_n::FUNC_f_HASH_<8hex>`. Unqualified references inside a namespace stay unqualified, and global-namespace symbols referenced from inside a namespace are rooted with `::`.

When a variable or function shares its name with a declared struct, the compiler emits a **warning** during semantic analysis (the program still compiles):

```text
==== Warnings ====
  - Variable 'X' shares its name with struct 'X'; generated C++ uses VAR_X for the variable and STRUCT_X for the struct type
```

Struct definitions are emitted after a forward-declaration block (`struct STRUCT_X;` for every declared struct), so `addr<X>` fields and pointer references may name structs declared later in source order.

## 31.9 Entry-Point Wrapper and Command-Line Arguments

The generated C++ entry point is not the Franko `main` itself. The Franko `main` emits its mangled name like every other function, and a small wrapper in the program template owns the real C++ `int main`:

- `ProgramTemplate.cpp` provides `int main()` and calls the no-args Franko `main` (`FUNC_main_HASH_d8625720`, the hash of `fn()->int32_t`).
- `ProgramTemplateWithRawCArgs.cpp` provides `int main(int argc, char** argv)`, converts the raw C arguments into a `Franko_Dynamic_Array<Franko_Dynamic_Array<uint8_t>>`, and calls the args-form Franko `main` (`FUNC_main_HASH_3f161798`, the hash of `fn(addr<array<array<uint8_t>>>)->int32_t`).

The compiler selects the template from the declared entry-point shape; an explicit `-t` template overrides the selection.

The argv conversion follows these rules:

- `args.length()` equals `argc`, with `args[0]` holding the program name;
- each argument is its raw bytes with **no** NUL terminator appended (consistent with string literals, which also append no terminator);
- each inner array is default-constructed by the runtime `init` into the uninitialized Franko state (`length()` `0`), so an empty argument simply stays uninitialized;
- after the Franko `main` returns, the outer array's destructor destroys the converted inner arrays and frees both levels; inner and outer arrays the program already released with `uninit()` are skipped safely.