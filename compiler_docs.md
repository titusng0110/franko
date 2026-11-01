# Franko Compiler Internals

This document describes the architecture of the Franko compiler: how the phases
connect (call graph), what each Java source file does, and the cross-cutting
design rules that were historically kept as comments inside the source files.
From now on please do not add comments in the Java source files. All comments
about the compiler design should reside here.

- **Language**: Franko (`.fr`) — a small statically-typed language with fluid
  integer constants, dynamic/static arrays, a capability-based pointer system
  (`ptr`/`mview`/`view`), structs with methods, namespaces, and imports.
- **Compiler**: ~28 hand-written Java files at the repo root, plus an ANTLR4
  grammar (`Franko.g4`). The compiler is a **transpiler to C++14**: it emits one
  C++ translation unit, splices it into a program template, and the resulting
  `.cpp` is compiled with the host C++ toolchain.
- **Runtime**: `include/FrankoRuntime.hpp`, one header for all build
  modes: `Franko_Dynamic_Array<T>` (owning dynamic arrays), raw C++ static
  arrays, `Franko_Fat_Pointer<T>` (pointers to arrays; scalar and struct
  pointers are raw `T*`), the byte intrinsics, and `franko_unsafe_cast`. Two C++ defines select the mode:
  `FRANKO_USE_JEMALLOC=1` links the bundled jemalloc (`third_party/`)
  instead of the system allocator, and `FRANKO_BOUNDS_UNCHECKED=1`
  strips bounds/range/bit/offset checks. Defaults (no defines) are the
  checked system-allocator ASan build.

---

## 1. Pipeline overview

The driver is `Main.java`. A program flows through these phases:

```
source .fr
   │  FrankoLexer / FrankoParser (ANTLR, generated from Franko.g4)
   ▼
ParseTree
   │  FrankoASTVisitor  (parse tree → raw AST of ASTNode classes)
   ▼
Raw AST (ProgramNode)
   │  ImportResolver    (recursively parses imported .fr files, splices their
   │                     top-level items in place of ImportNodes → one flat AST)
   │  OsFfi.applyTargetConstants (if "franko/os" imported: rewrites the five
   │                     os::IS_* placeholder globals to 0/1 for the target)
   ▼
Import-resolved AST
   │  Desugarer         (lowers syntactic sugar to a small core AST,
   │                     folds every constExpr slot, collects diagnostics)
   ▼
Desugared AST
   │  SemanticAnalyzer  (builds the typed, symbol-resolved Semantic AST;
   │                     uses SymbolTable, ExpressionAnalyzer, ArrayLowerer,
   │                     ConstExpressionEvaluator)
   ▼
Semantic AST (SemanticProgramNode)
   │  LegalityChecker   (orchestrates TypeChecker, ExpressionChecker,
   │                     DeclarationChecker, StatementChecker, FunctionChecker)
   │  BranchPruner       (truth-table-based conditional/loop pruning on the
   │                     Semantic AST; removes dead branches and dead loops)
   │  InactiveDeclarationPruner (zero-reference pruning: structs, functions,
   │                     and variables never used in surviving code are
   │                     removed from the Semantic AST)
   │  PointerNullStateChecker (intra-procedural pointer null dataflow)
   │  DanglingPointerChecker (return-escape provenance: pointers to frame
   │                          storage and decayed static-array views)
   │  DynamicArrayMemoryChecker (local dynamic-array init/uninit lifecycle,
   │                             guards, loops, and owner-pointer aliases)
   │  PointerPermissionChecker (capability linter: unused-permission
   │                            warnings for ptr/mview variables)
   ▼
checked Semantic AST
   │  OsFfi.verifyBridge (if franko/os imported: every used intrinsic must have
   │                     its mangled definition in os.ffi.cpp)
   │  Cpp14Codegen      (emits the C++ body: structs, globals, functions)
   ▼
generated C++ body
   │  template splice   (ProgramTemplate.cpp or ProgramTemplateWithRawCArgs.cpp,
   │                     selected by sema.entryKind(); the body replaces
   │                     __FRANKO_PROGRAM__ and the runtime #include replaces
   │                     __FRANKO_RUNTIME_HEADER__)
   ▼
output .cpp  (then compiled with the host C++ compiler)
```

Non-fatal warnings (semantic warnings, pointer maybe-null warnings,
dangling-pointer warnings, dynamic-array lifecycle warnings) are printed
between phases; errors abort with a `RuntimeException` carrying the
formatted `DiagnosticBag` contents.

---

## 2. Call graph

### 2.1 Phase driver graph

```mermaid
flowchart TD
    Main --> FrankoLexer["FrankoLexer / FrankoParser (generated)"]
    FrankoLexer --> FrankoASTVisitor
    FrankoASTVisitor --> ASTNode["ASTNode (raw AST)"]
    Main --> ImportResolver
    ImportResolver --> FrankoLexer
    ImportResolver --> FrankoASTVisitor
    Main --> OsFfi
    Main --> Desugarer
    Main --> SemanticAnalyzer
    SemanticAnalyzer --> SymbolTable
    SemanticAnalyzer --> ExpressionAnalyzer
    ExpressionAnalyzer <--> ArrayLowerer
    ExpressionAnalyzer <--> ArrayLiteralLowerer
    ArrayLiteralLowerer --> SemanticAnalyzer
    ExpressionAnalyzer --> SymbolTable
    ExpressionAnalyzer --> ConstExpressionEvaluator
    ExpressionAnalyzer --> TypeChecker
    ArrayLowerer --> SymbolTable
    ConstExpressionEvaluator --> SymbolTable
    SemanticAnalyzer --> SemanticASTNode["SemanticASTNode + SemanticType + Symbol"]
    Main --> LegalityChecker
    LegalityChecker --> TypeChecker
    LegalityChecker --> ExpressionChecker
    LegalityChecker --> StatementChecker
    LegalityChecker --> DeclarationChecker
    LegalityChecker --> FunctionChecker
    FunctionChecker --> StatementChecker
    FunctionChecker --> ExpressionChecker
    FunctionChecker --> DeclarationChecker
    StatementChecker --> DeclarationChecker
    StatementChecker --> ExpressionChecker
    ExpressionChecker --> TypeChecker
    DeclarationChecker --> TypeChecker
    Main --> BranchPruner
    Main --> InactiveDeclarationPruner
    Main --> PointerNullStateChecker
    Main --> DanglingPointerChecker
    Main --> DynamicArrayMemoryChecker
    Main --> PointerPermissionChecker
    PointerPermissionChecker -. reads Semantic AST only .-> SemanticASTNode
    Main --> Cpp14Codegen
    Cpp14Codegen --> Mangler
    Cpp14Codegen --> OsFfi
    OsFfi --> TargetInfo
    OsFfi --> Symbol
    Symbol --> Mangler
    SemanticAnalyzer --> TargetInfo
    Desugarer --> ConstExpressionEvaluator
    Desugarer --> StringLiteralDecoder
    FrankoASTVisitor --> StringLiteralDecoder
    SemanticASTPrinter -. verbose .-> Main
    ASTPrinter -. verbose .-> Main
    DiagnosticBag -. used by all checkers .- TypeChecker
```

### 2.2 ASCII call-edge matrix

`caller → callee` edges (instance calls unless noted):

| Caller | Calls into |
|---|---|
| `Main` | `FrankoLexer/Parser`, `FrankoASTVisitor`, `ImportResolver`, `OsFfi`, `Desugarer`, `SemanticAnalyzer`, `LegalityChecker`, `BranchPruner`, `InactiveDeclarationPruner`, `PointerNullStateChecker`, `DanglingPointerChecker`, `DynamicArrayMemoryChecker`, `PointerPermissionChecker`, `Cpp14Codegen`, `TargetInfo`, `ASTPrinter`, `SemanticASTPrinter` |
| `ImportResolver` | `FrankoLexer/Parser`, `FrankoASTVisitor`, `ASTNode` |
| `Desugarer` | `ConstExpressionEvaluator`, `StringLiteralDecoder`, `DiagnosticBag`, `ASTNode` |
| `SemanticAnalyzer` | `SymbolTable`, `ExpressionAnalyzer`, `ArrayLowerer`, `ArrayLiteralLowerer`, `ConstExpressionEvaluator`, `OsFfi`, `TargetInfo` |
| `ExpressionAnalyzer` | `SymbolTable`, `ConstExpressionEvaluator`, `ArrayLowerer` (mutually with), `TypeChecker` (static: `isValuePreservingIntegerWidening`) |
| `ArrayLowerer` | `ExpressionAnalyzer`, `SymbolTable` |
| `ArrayLiteralLowerer` | `ExpressionAnalyzer` (element lowering), `TypeChecker` (static `fitsBigIntegerToPrimitive`), `SemanticAnalyzer` (via `Hoister`) |
| `ConstExpressionEvaluator` | `SymbolTable` (diagnostics only) |
| `LegalityChecker` | `FunctionChecker`, `DeclarationChecker`, `StatementChecker`, `ExpressionChecker` |
| `BranchPruner` | reads/writes `SemanticASTNode` trees only (called by `Main`); no other compiler classes |
| `InactiveDeclarationPruner` | reads/writes `SemanticASTNode` trees only (called by `Main`); no other compiler classes |
| `FunctionChecker` | `StatementChecker`, `ExpressionChecker`, `DeclarationChecker`, `TypeChecker` |
| `StatementChecker` | `DeclarationChecker`, `ExpressionChecker`, `TypeChecker` |
| `DeclarationChecker` | `TypeChecker` (incl. static `fitsBigIntegerToPrimitive`) |
| `ExpressionChecker` | `TypeChecker` |
| `PointerNullStateChecker` | `DiagnosticBag` only (deliberately standalone) |
| `DanglingPointerChecker` | `DiagnosticBag`, `PointerPermissions` (deliberately standalone) |
| `DynamicArrayMemoryChecker` | `DiagnosticBag` only (deliberately standalone) |
| `PointerPermissionChecker` | `DiagnosticBag`, `PointerPermissions` (deliberately standalone) |
| `Cpp14Codegen` | `OsFfi`, `TargetInfo`, `Symbol`/`Mangler` (mangled names), `SymbolTable.splitNamespacePath` (static) |
| `SymbolTable` | `DiagnosticBag`, `Symbol`, `SemanticType` |
| `Symbol` | `SemanticType`, `Mangler` (static `hashHex`) |
| `OsFfi` | `TargetInfo`, `Symbol`/semantic node classes |
| `TypeChecker`, `DiagnosticBag` | leaf utilities (JDK only) |

Called-by is the inverse of the table above (e.g. `TypeChecker` is called by
every checker plus `ExpressionAnalyzer`/`DeclarationChecker` statically).

### 2.3 Data model ownership

- `ASTNode.java` — raw AST data model, produced by `FrankoASTVisitor`,
  rewritten by `Desugarer`/`ImportResolver`/`OsFfi`, read by `ASTPrinter` and
  `ConstExpressionEvaluator`.
- `SemanticASTNode.java` — lowered typed AST, produced by
  `SemanticAnalyzer`/`ExpressionAnalyzer`/`ArrayLowerer`, consumed by every
  checker, `PointerNullStateChecker`, `DanglingPointerChecker`,
  `DynamicArrayMemoryChecker`, `PointerPermissionChecker`, `Cpp14Codegen`,
  `OsFfi`, `SemanticASTPrinter`.
- `SemanticType.java` — structural type system (overload resolution, mangling
  keys). Also hosts `PtrPermission` (OWNER/MUTABLE/READ_ONLY), the permission
  lattice shared with the raw AST (`ASTNode.PtrTypeNode.kind`,
  `GetPtrNode.kind`) and every permission rule.
- `Symbol.java` — symbol hierarchy (variables, functions, methods, structs,
  fields, namespaces) plus the C++ mangling policy.

---

## 3. Per-file reference

### 3.1 Front end

#### `Franko.g4` (grammar, 1131 lines)
The single source of truth for Franko syntax: combined ANTLR4 lexer+parser
grammar. Generates (via `update_toolchain.py`) `FrankoLexer`, `FrankoParser`,
`FrankoBaseVisitor/Listener` and friends into `build/generated/`. Key rules:
`program`/`topLevelItem`, namespace/import/struct/function declarations, a
labeled-alternative `statement` rule, a full precedence-climbing expression
layer, a mirrored `constExpr` sub-grammar, and the `type` rule with
`[T]` / `[T, size]` / `ptr<T>` / `mview<T>` / `view<T>` and the `?`
inferred-size alternative. Pointer permissions are created by the intrinsic
forms `getptr(lvalue)` / `getmview(lvalue)` / `getview(lvalue)` and consumed
by `$(expr)` dereferences; conversions are `cast<T>(expr)` (integer widening
or pointer permission downgrade; the target is a full `type`) and
`unsafe_cast<T>(expr)` (checked integer conversions; target restricted to
`primitiveType` at parse time). Array literals are ordinary `primary`
expressions (an array literal is a `view<[T, N]>` value, §4.13).
Global initializers accept `constExpr`, a
string literal, an array literal, a `getptr`/`getmview`/`getview` over a
global, or `null`.
Notable lexer details: `int/byte/uint` keyword aliases for the sized integer
types, and a `groupingDepth` member on the lexer that suppresses NEWLINE
tokens inside `(` and `[` (braces are deliberately excluded so newlines stay
statement separators there).

#### `FrankoASTVisitor.java`
Parse-tree → raw-AST lowerer: the single `FrankoBaseVisitor<ASTNode>`
implementation. Runs first, once per source file (and once per imported file
via `ImportResolver`). Left-folds precedence levels into `BinOpNode` chains
(`foldSimpleBinaryRule`), reconstructs `<<`/`>>` from split `<` `<` / `>` `>`
tokens, builds access/call/postfix chains from `lvalueSuffix` rules, joins
`a::b` namespace headers into one name string, and dispatches declarations,
statements, types, and the `constExpr` sub-grammar to their node classes.
Called by `Main` (root) and `ImportResolver.collectFile` (imports); calls
`StringLiteralDecoder.decodeRawContent` for import-path strings.

#### `ASTNode.java`
All raw AST node classes in one file (package-private, constructors only):
`ProgramNode`, `BlockNode`, `ExprStmtNode`; statements (`AssignNode`,
`IncDecNode`, `IfNode`, `WhileNode` with its desugared-for update epilogue,
`BreakNode`/`ContinueNode` with optional const level, `ForNode`,
`ReturnNode`); declarations (`FunctionDeclNode`, `ParameterNode`,
`StructDeclNode`, `StructFieldDeclNode`, `GlobalVarDeclNode`, `VarDeclNode`,
`VarDeclInitNode`, `VarDeclArrayInitNode`, `ImportNode`, `NamespaceNode`,
`QualifiedNameNode`); types (`PrimitiveKind` enum, `PrimitiveTypeNode`,
`VoidTypeNode`, `DynamicArrayTypeNode`, `StaticArrayTypeNode`, `PtrTypeNode`
(carrying a `PtrPermission kind`: `ptr`/`mview`/`view`), `StructTypeNode`);
expressions (`VarNode`, `IntNode`, `InferredSizeNode`, `ArrayLiteralNode`,
`StringLiteralNode`, `UnaryOpNode`, `BinOpNode`, `ArrayAccessNode`,
`MemberAccessNode`, `CallNode`, `GetPtrNode` (kind: which of
`getptr`/`getmview`/`getview` was written), `DerefNode`, `CastNode` (a full
`TypeNode` target plus an `unsafe` flag distinguishing `cast` from
`unsafe_cast`), `NullNode`). `GlobalVarDeclNode.literalBytes` is non-null
only for string-literal globals hoisted by the Desugarer (§4.10): a
literal-backed `[byte, N]` whose value is the decoded UTF-8 payload.
Array-literal globals hoisted during semantic analysis carry
`GlobalVariableSymbol.literalValues` (row-major flat) instead (§4.13);
nesting is derived from the declared static array shape.
The field layout is the ABI between all phases.
Lifecycle rules that used to live as comments here:
- `ForNode`, `IncDecNode`, `VarDeclInitNode`, `VarDeclArrayInitNode`,
  `ImportNode` are **pre-desugaring sugar**: none may reach
  `SemanticAnalyzer` (the Desugarer asserts this). `ArrayLiteralNode` is
  deliberately NOT sugar: it survives desugaring and is lowered during
  semantic analysis (§4.13), where its element type is inferred.
- `GlobalVarDeclNode` is deliberately distinct from `VarDeclNode` because the
  Desugarer keys top-level handling off node classes and global const
  initializers must never be rewritten into assignment statements.
- `WhileNode.update` exists only so desugared for-loops run their update
  epilogue on `continue` (see §4.1).

#### `ASTPrinter`
Debug pretty-printer for the raw AST (single class, all static). Used only
under `--verbose` by `Main` at three checkpoints: raw, import-resolved,
desugared. A giant `instanceof` dispatch over every `ASTNode` class; also
`typeToString` (renders types as Franko source spellings) and
`exprToInlineString` (compact one-line expressions for static array sizes).

#### `ImportResolver`
Resolves `import "…"` immediately after AST construction, before desugaring.
For each `ImportNode` it lexes/parses/visits the imported file with the same
front end and splices its top-level items into the root `ProgramNode`, so the
whole program becomes one flat AST. Path resolution: `.fr` extension is
mandatory; relative paths resolve against the importing file; unqualified
paths search the `-p` search dir (default `./import`) before the importing
file's directory; real-path canonicalization dedups diamonds; files are marked
processed *before* splicing so import cycles terminate. Records the import
graph (`importGraph()`), detects the canonical `franko/os` builtin module
(`importedBuiltinModules()`, `frankoOsModuleFile()`) which activates the FFI
and target-constant machinery in `Main`, and contains a testable
`translateWindowsAbsolutePath` helper. Also contains a
`CollectingErrorListener` that prefixes lexer/parser errors of imported files
with the file name.

#### `Desugarer`
The desugaring pass (after import resolution, before semantic analysis). Lowers
syntactic sugar to a small core AST: declaration initializers → decl +
assignment, `x++/--` →
`x += 1`, `for` → block + `while` (update as while-epilogue so `continue` is
C-like), `?` array sizes in `view<[...]>` declarations → concrete
constants via the rectangle rule (array
literals only), string literals → hoisted literal-backed globals plus a
`getview(<global>)` expression of type `view<[byte, N]>` (§4.10), and
every `constExpr` slot → a canonical decimal `IntNode` via
`ConstExpressionEvaluator`. Array literals are passed through untouched
(§4.13). Global initializers pass through the statically
representable forms (`null`, `getptr`/`getmview`/`getview` over a global,
an array literal) and reject everything else. Error-recovery style: it accumulates diagnostics in
a `DiagnosticBag` (placeholder values on failure) and `Main` checks
`hasErrors()`/`formatErrors()` right after the pass. Private control-flow
signal: `InferenceFailure` (a `RuntimeException`) for `?`-size failures,
converted to a diagnostic. Throws if an `ImportNode` ever reaches it.

#### `StringLiteralDecoder`
Static utility: decodes Franko string-literal escapes
(`\\ \" \a \b \f \n \r \t \v`; rejects `\u \x \0` and unknown/trailing escapes
with `RuntimeException`) and converts a `StringLiteralNode` into its UTF-8
byte payload (`toUtf8Bytes`, unsigned bytes, no null terminator) for the
hoisted string globals. Called by `Desugarer` (string hoisting) and
`FrankoASTVisitor` (import paths).

#### `TargetInfo`
Immutable compilation target (canonical `<os>-<arch>` pair). Single source of
truth behind the `os::IS_*` constants in `import/franko/os.fr`. Key API:
`detectHost()` (JVM `os.name`/`os.arch` normalization), `parse(spec)` (accepts
`x64/amd64/aarch64/darwin` aliases), `constantFor(name)` (the 0/1 value of one
`IS_*` constant), and predicates (`isWindows`, `isArm64`, …). Used by `Main`,
`OsFfi`, `SemanticAnalyzer` (constant stamping) and `Cpp14Codegen`
(target-allowed intrinsic checks).

#### `OsFfi`
Compiler side of the `import "franko/os"` foreign-function bridge. Five known
intrinsics (posix read/write; win32 GetStdHandle/ReadFile/WriteFile), each
recognized by namespace+name+exact signature (`kindOf`). Responsibilities:
- `applyTargetConstants` — rewrites the five `os::IS_*` placeholder globals in
  the raw AST to the target's 0/1 values before desugaring (only the canonical
  `franko/os` import activates this).
- `checkTargetAllowed` — rejects live calls to wrong-target intrinsics (posix
  on Windows, win32 off Windows); must run after constant-dead-branch pruning.
- `parseBlocks`/`buildBridge` — the bridge file `os.ffi.cpp` is split into
  `// @@franko-ffi:<name>` marker-delimited blocks; codegen prepends the
  `common` block plus one block per *used* intrinsic, in first-use order.
- `collectKinds`/`verifyBridge` — walks the semantic program, verifies each
  used intrinsic's emitted mangled name matches the pinned
  `expectedEmittedName`, and (in `Main`) that every used intrinsic has its
  definition present in the bridge text at Franko-compile time instead of C++
  link time. Known intrinsic names with drifted signatures are hard errors
  ("os.fr/ffi.cpp drift"); stubs must never compile silently.

#### `ConstExpressionEvaluator`
Compile-time integer constant folding for the `constExpr` subset
(integers, unary `-`/`!`, binary integer operators) over `BigInteger`.
Instance methods report diagnostics through `SymbolTable` (`parseIntegerLiteralSafe`,
`foldUnary`, `foldBinary`); static pure methods return `null` on failure
(`tryFold`, `tryFoldUnary`, `tryFoldBinary`, `parseIntegerLiteralPure`) or
throw `UnevaluableConstant` (`evaluatePure`, used by the Desugarer to
canonicalize const slots; the exception carries an optional ready-to-report
diagnostic, e.g. for division by zero). `%` uses `BigInteger.remainder` to match
C++14 truncated remainder semantics. Called by `Desugarer`, `SemanticAnalyzer`
(`break N` levels) and `ExpressionAnalyzer`.

### 3.2 Semantic core

#### `SemanticAnalyzer`
The lowering hub: converts the desugared raw AST into the typed,
symbol-resolved Semantic AST. It deliberately does **not** enforce full
language correctness (that is the legality cluster's job). Entry:
`analyze(ASTNode)` → `SemanticProgramNode`; throws `SemanticException` (static
nested class) when errors accumulated. Program analysis runs five internal
phases:
1. register struct names (placeholder symbols),
2. resolve struct fields, fold field defaults,
2b. register method signatures,
3. register global variables (fold const initializers; stamp `os::IS_*`
   compile-time constants),
4. register all function signatures (enables forward references/recursion),
5. lower items in source order (structs+methods, globals, function bodies).

Also determines `entryKind()` (`NONE`/`NO_ARGS`/`ARGS`) which selects the C++
program template in `Main`, collects non-fatal `warnings()`, and owns the
`ExpressionAnalyzer` (constructed with the shared `SymbolTable` +
`ConstExpressionEvaluator`; the `ArrayLowerer` is wired in via
`exprAnalyzer.setArrayLowerer(...)` and `analyzeNormalType` is wired in as
the cast-target type resolver via `exprAnalyzer.setTypeResolver(...)`).
Globals additionally accept the statically representable initializers
(`null`, `getptr`/`getmview`/`getview` over a global, a constant array
literal) and carry the UTF-8
payload of hoisted string-literal globals on `GlobalVariableSymbol`. It
also owns array-literal hoisting (`hoistArrayLiteralGlobal`, wired into
`ArrayLiteralLowerer` through the `Hoister` interface): each distinct
(element type, values) pair becomes one `_ARR_<hash>` global registered in
the global-namespace table (no namespace, so calls from any namespace see
the same global) and prepended to the program items.
The entry-point signature must remain exactly `view<[[byte]]>`
(read-only) for the raw-args `main`; the template marshals argv behind a
const pointer, so the program cannot mutate its arguments.

#### `SemanticASTNode`
The lowered AST data model (pure data classes): program/namespace/block
nodes, `SemanticFunctionDeclNode` (symbol + parameter variables + body),
`SemanticStructDeclNode`, `SemanticVarDeclNode` (with folded const
initializer), statements (`SemanticAssignNode` with op text and
`isCompound()/baseOp()`, `SemanticIfNode`, `SemanticWhileNode` with the
desugared-for update epilogue, `SemanticBreakNode` with levels,
`SemanticContinueNode`, `SemanticReturnNode` carrying the
owning `FunctionSymbol`), the array-intrinsic vocabulary
(`SemanticArrayIntrinsicKind`: INIT, INIT_ZERO, RESIZE, UNINIT, MEMSET, MEMCPY,
MEMMOVE, LENGTH, MEMCMP, MEMCHR, MEMMEM, the twenty-four
`READ_*`/`WRITE_*_{LE,BE}` endian accessors, READ_BIT, WRITE_BIT, FLIP_BIT,
BITWISE_NOT_MASK, BITWISE_XOR_MASK, BITWISE_AND_MASK, BITWISE_OR_MASK,
SHIFT_BITS_LEFT, SHIFT_BITS_RIGHT, COUNT_SET_BITS — each carrying a
signature descriptor: runtime member name, return kind, dynamic-only flag,
element rule (none/trivial/copiable/byte), receiver permission, allowed
arities, and per-argument kinds; `SemanticArrayIntrinsicCallNode` with
separate receiver + args and full/ranged form predicates), and expressions
(all carrying `type`,
`constantValue: BigInteger`, `isConstant()`; `isLValue()` abstract).
`SemanticGetPtrNode` covers all three creation intrinsics; the permission
created is `type`'s (`SemanticPtrType.permission`), which is exactly the
level the target region must grant. `SemanticCastExprNode` has a `checked`
flag: `false` → plain `static_cast` (`cast`), `true` → runtime-checked
`franko_unsafe_cast` (`unsafe_cast`). `SemanticVarDeclNode` carries either a
folded `BigInteger` initializer (primitive globals) or a `staticInitializer`
expression (pointer globals initialized with a statically representable
address).

#### `SemanticType`
The structural type system: `SemanticPrimitiveType` (8 kinds),
`SemanticVoidType` (return-type only), `SemanticDynamicArrayType`
(`[E]`), `SemanticStaticArrayType` (`[E,N]` — size stored as the
canonical folded decimal so `[int32,5+5]` equals `[int32,10]`),
`SemanticPtrType` (the pointer family; see below),
`SemanticNullType` (type of `null`; `mangleKey()` throws),
`SemanticStructType` (nominal identity via its `StructSymbol`),
`SemanticFunctionType` (with `signatureKey()` like
`fn(int32,ptr<std.X>)->void` for mangling). Two distinct text APIs:
`describe()` (diagnostic text, free to change) and `mangleKey()` (canonical
C++-spelling key embedded in generated names — must stay stable). Structural
`equals`/`hashCode` everywhere.

The pointer family is one class with a `PtrPermission` level (OWNER
`ptr<T>`, MUTABLE `mview<T>`, READ_ONLY `view<T>`), exposed as the
subclasses `SemanticPtrType`/`SemanticMViewType`/`SemanticViewType` so
every `instanceof SemanticPtrType` check covers all three while type
equality keeps them distinct (`getClass()` + referenced type). Emission
depends on the referenced type: a pointer to an array becomes
`Franko_Fat_Pointer<element(T)>`, while a pointer to a scalar, struct, or
another pointer is raw `T*`; permission is not part of the C++ type
(see §4.9). `mangleKey()` spells them `ptr<…>`/`mview<…>`/`view<…>`, so owner
signatures (and every pinned mangling) are unchanged.
`PtrPermission.allows(other)` encodes the downgrade lattice: permissions
only ever downgrade (owner → mutable → read-only), so a stronger level
allows a weaker-or-equal one. `SemanticPtrType.withPermission` re-wraps a
referenced type at another level (used by `OsFfi` when matching expected
intrinsic signatures).

#### `Symbol`
The symbol hierarchy and mangling policy:
- `VariableSymbol` (+ optional namespace), `ParameterSymbol`,
  `GlobalVariableSymbol`.
- `FunctionSymbol` — parameters, namespace, `isBuiltin`; `mangledName()` =
  `FUNC_<name>_HASH_<8 hex>`; `emittedLocalName()` for codegen. Overload
  identity is name + ordered parameter types; **return type is intentionally
  not part of overload identity** (so `f(int32)->int32` and
  `f(int32)->uint32` conflict).
- `MethodSymbol` — adds `owner: StructSymbol`; emitted as
  `METH_<name>_OFSTRUCT_<Struct>_HASH_<hex>` free functions taking a raw
  `STRUCT_X*` for `VAR_self` (a struct pointer is scalar, so it uses the
  raw-pointer representation; the self permission is compile-time only).
  **Method overload identity excludes the `self` parameter's type**: name +
  the remaining parameter types must be unique, so a struct may not carry
  both a `view<X> self` and an `mview<X> self` method with otherwise
  identical signatures.
- `GlobalVariableSymbol` — adds `compileTimeConstantValue` (os `IS_*`
  folding), `literalBytes` (the UTF-8 payload of a hoisted string-literal
  global, §4.10), and `literalValues` (the row-major folded element values
  of a hoisted array-literal global, §4.13).
- `FieldSymbol`, `StructSymbol` (ordered fields, per-name method overloads,
  field/method clash detection).
- `NamespaceSymbol` — parent/children tree with per-namespace tables for
  structs, functions, globals.

The hash in every mangled name is `Mangler.hashHex(signatureKey())` (FNV-1a
32-bit), so overloads emit distinct C++ names without relying on g++
overload resolution.

#### `SymbolTable`
Scope/symbol environment for semantic analysis (refactored out of an earlier
`SemanticAnalyzer.Context`). Owns: lexical variable scopes
(`pushScope/popScope/declare/resolve` with lexical → current-namespace globals
→ global-namespace globals lookup order); namespace declaration/resolution and
`setCurrentNamespace`; struct tables with namespace-chain fallback
(`resolveStruct`) vs strict (`resolveStructIn`); function/method overload
registration and resolution (`resolveFunction/resolveMethod` — arity +
type applicability, constant-fluid arguments may widen, nonconstants must
match exactly **or be a pointer conversion** (permission downgrade or
array-view decay), ambiguity is an
error; among applicable overloads exact permission matches win over
downgrading ones, then pure downgrades, then decays, then a constant-fit
preference (the parameter matching the constant's own type wins when the
value fits, otherwise the narrowest fitting type, signed before unsigned
at equal width — so `print(1)` selects `print(int32)` and
`print(5000000000)` selects `print(int64)`), then ambiguity —
the fewest-downgrades tally weights a decay twice a pure downgrade). Method overload
identity excludes `self`'s type: `resolveMethod` matches name + user
arguments only, and the caller synthesizes the hidden `self` argument with
`getptr`/`getmview`/`getview` matching the resolved method's declared self
type (an under-privileged receiver then fails the ordinary creation-intrinsic
permission rule); `CallArg` (a lowered expression or a not-yet-lowered
`ArrayLiteralNode`) so overload resolution can run before a literal
argument's element type is inferred — a pending literal is applicable to
a `view<[...]>` parameter whose shape matches the literal's
(`isArrayLiteralShapeApplicable`, exact length = exact match, dynamic =
decay cost 2); raw-decl→symbol registration maps;
name-clash warnings (struct names live in a separate namespace so clashing
with variables/functions is legal — the C++ backend's kind prefixes
`VAR_`/`FUNC_`/`STRUCT_` keep generated names unambiguous); and diagnostic
delegation to `DiagnosticBag`. `splitNamespacePath` is a static helper also
used by `Cpp14Codegen`.

#### `ExpressionAnalyzer`
Expression lowering *inside* the SemanticAnalyzer phase: raw `ASTNode`
expressions → `SemanticExprNode` trees with symbol resolution, overload
resolution, mechanical type inference, and folded `BigInteger` constants.
Explicitly does **not** validate assignment compatibility, operator legality,
or argument correctness. Notable behavior: array intrinsic calls are tried
first via `ArrayLowerer.tryLowerIntrinsicCall`; method calls lower to plain
calls whose hidden self argument is synthesized as
`getptr`/`getmview`/`getview(receiver)` **matching the method's declared self
type** (pointer receivers need explicit `$(p).method(...)`; the creation
intrinsic's permission rule is what rejects an under-qualified receiver);
`getptr`/`getmview`/`getview` lower to `SemanticGetPtrNode` with the
corresponding pointer permission; qualified `ns::name` references resolve
strictly against declared namespaces, and `os::IS_*` constants fold into
integer literals; `inferBinaryResultType` implements the fluid-constant rule
(when one operand is a folded constant, the result takes the other side's
type); same-type casts are returned unchanged (no cast node emitted);
`cast<T>()` lowers mechanically (target resolved through the wired type
resolver) and `unsafe_cast<T>()` always lowers as a checked cast; the
legality checkers decide whether a `cast` is a permitted widening,
permission downgrade, or array-view decay. Array literals are delegated
to `ArrayLiteralLowerer` with an optional expected type (the inference
context of §4.13: assignment target type, return type, or call parameter
type); call arguments are lowered in two phases through
`SymbolTable.CallArg` so a literal argument participates in overload
resolution before its element type is known (§4.13).

#### `ArrayLowerer`
Frontend lowering of array-intrinsic call syntax to
`SemanticArrayIntrinsicCallNode`s during semantic analysis: named member
intrinsics (`xs.init(n)`, `xs.init_zero()`, `xs.resize(n)`, `xs.uninit()`,
`xs.memset(v)`/`xs.memset(s,n,v)`, `xs.memcpy(src)`/`xs.memcpy(src,sr,dst,n)`,
`xs.memmove(src,n)`, `xs.length()`, `xs.memcmp(src)`/`xs.memcmp(src,sr,dst,n)`,
the byte-array search/endian/bit intrinsics, and the dynamic-array init shorthand
`xs(size)` → `xs.init(size)`. Type-directed dispatch: a struct-typed receiver
is a method call, never an intrinsic, even when a method name coincides with an
intrinsic name; `shouldSuppressCallDiagnostic` avoids misleading "no matching
overload" errors when fallthrough would be noise. The shorthand disambiguator
"peeks" at member-call receivers before full callee analysis to avoid spurious
errors alongside real method resolution. The per-kind signature descriptor
drives arity checks, receiver lvalue requirements (mutating kinds only), and
argument lowering; pointer arguments (`memcpy`/`memcmp` sources, `memmem`
needles) receive an expected `view<[elem]>` context so array/string literals
infer their element type. Mutually coupled with `ExpressionAnalyzer` (analyzes
intrinsic arguments through it).

#### `ArrayLiteralLowerer`
Lowers `ArrayLiteralNode` expressions during semantic analysis into
`view<[T, N]>` values (§4.13). The element type `T` comes from the
context passed in by `ExpressionAnalyzer` (an assignment target, return
type, or call parameter of array shape); sizes come from the literal
itself. Elements must be compile-time constant expressions of integer
type: every literal is hoisted to a literal-backed global
(`GlobalVariableSymbol.literalValues`, deduplicated by element type +
values, named `_ARR_<hash>`) and lowered to `getview(<global>)` — exactly
like string literals. Non-constant elements, jagged nested rows, empty
literals, and context-less literals are diagnostics; on failure it hoists
a zero-filled fallback global so the rest of the pipeline still sees a
well-typed view. The hoisting itself is delegated through the `Hoister`
interface to `SemanticAnalyzer` (which owns global registration).

### 3.3 Legality checkers

All five run under `LegalityChecker` on the already-lowered Semantic AST, each
reporting into a shared `DiagnosticBag`. `SemanticAnalyzer` deliberately
pre-establishes symbols/types so the checkers can focus on rules.

#### `LegalityChecker`
The orchestrator ("master checker"): constructs `TypeChecker`,
`ExpressionChecker`, `DeclarationChecker`, `StatementChecker`,
`FunctionChecker` in that dependency order, then `check(semaAST)` dispatches
each node (`checkFunction` / `checkStructDecl` / `checkStmt` / defensive
`checkExpr`) and throws `SemanticException` if the bag has errors. It is
invoked by `Main`, *not* by `SemanticAnalyzer`.

#### `FunctionChecker`
Function-level legality: valid return type, valid parameters (no duplicates,
assignable value types), method `self` shape (`ptr<X>`/`mview<X>`/`view<X>
self` must be the first parameter and no other parameter may be named
`self`), return statement rules
(`return;` only in void functions; `return expr;` must be assignable to the
return type, pointer conversions allowed), and **presence-based
missing-return detection**: a non-void
function must syntactically contain a return somewhere in its body (recursing
through blocks/ifs/whiles; the desugared for-update epilogue runs inside the
loop so returns there count). It does not prove full control-flow coverage.
Intercepts `SemanticReturnNode` itself and delegates other statements to
`StatementChecker`.

#### `DeclarationChecker`
Declaration-level legality: declared types must be legal Franko types
(primitive, array, ptr/mview/view, struct; void and arrays-as-values
rejected),
static array sizes must be constant, positive, and fit `uint32` (zero is
rejected — the backend emits `T data[N]`), struct field graphs must not have
infinite-size **by-value** cycles (cycle detection via `StructWalkState`;
the by-value path is intentionally reset when descending through dynamic array
elements because those live behind a pointer), method names must not collide
with field names, and folded const initializers/field defaults must fit their
primitive type. Pointer globals initialized with a statically representable
address (`getptr`/`getmview`/`getview` over a global) are validated here:
the initializer must be a pointer type match or pointer conversion
(permission downgrade or array-view decay).

#### `StatementChecker`
Statement-level legality: assignment targets must be storage-backed lvalues
(storage semantics: variables, derefs, and array/member accesses rooted at
storage — stronger than `SemanticExprNode.isLValue()`), non-void LHS, exact/
fitting RHS per `TypeChecker.ensureAssignable` (pointer conversions
allowed); assignment through an lvalue path requires **mutation rights** over
the target's storage region (§4.9 blocks writes through `view`); compound-assign
rules with
constant-zero-divisor rejection; `if`/`while` conditions must be non-void
integral expressions; and the void-value-context
rule: a void call may not appear where a value is required (`x = logDone();`,
`if (logDone()) {}`). Break/continue are no-ops here (already validated by
`SemanticAnalyzer`); a stray `SemanticReturnNode` triggers a defensive error
because `FunctionChecker` should have intercepted it.

#### `ExpressionChecker`
Expression *legality* on the already-inferred tree (it never infers types):
operator category rules (arithmetic/bitwise need same integral types or one
fitting constant; comparisons/logical allow mixed integrals with constant fit;
shifts need a non-negative constant or unsigned right operand), pointer/null
comparisons (only `==`/`!=`, matching pointer types), function-call argument
revalidation (constants must fit, non-constants match exactly or are a
pointer conversion — permission downgrade or array-view decay —, no
void/array arguments), array intrinsic
argument rules **and receiver permission rules** (lifecycle intrinsics
`init`/`init_zero`/`resize`/`uninit` need an OWNER region;
`memset`/`memcpy`/`memmove`, `write_*`, `write_bit`, `flip_bit`,
`bitwise_*_mask`, and `shift_bits_*` targets need mutation rights;
`length`, `memcmp`, `memchr`, `memmem`, `read_*`, `read_bit`, and
`count_set_bits` are always fine); per-family argument rules (element-type equality
and `isCopiable` for `memcpy`/`memcmp`, identical/`isTrivial` for `memset`,
byte-only receivers for the search/endian/bit families, pointer-to-array
sources with element-type checks, scalar arguments checked as exact types or
fitting constants), constant static-array index/range/byte-offset/bit-index
bounds, cast rules
(`cast` = value-preserving integer widening, pointer permission downgrade
over an identical referenced type, or the static-to-dynamic array view
decay — whose to-`ptr` form gets a dedicated "cannot decay ownership"
error; `unsafe_cast` = checked integer
conversion with compile-time constant range checks), creation-intrinsic
legality (`getptr`/`getmview`/`getview` need an equally strong storage
region, §4.9), deref legality, and void-in-value-context rules. Carries
the "fluid integer constants" model: folded constants stay fluid until a
context constrains them.

#### `TypeChecker`
The shared leaf utility: type predicates (`isVoidType`, `isIntegral`,
`isPointerType`, `isDynamicArrayType`, …), classification
(`isAssignableValueType`, `isValidFunctionReturnType`), `ensure*` helpers that
report diagnostics, assignment compatibility (`ensureAssignable`), operator
operand rules (`ensureSameIntegralTypeOrFit`,
`ensureMixedIntegralOperandsWithConstantFit`, `ensureValidShiftOperands`),
constant-fit machinery (`requireConstantFitsType`, static
`fitsBigIntegerToPrimitive`, `bitWidth`, `isSigned`,
`unsignedVariantOf`/`signedVariantOf`, static
`isValuePreservingIntegerWidening` — which also drives cast selection in
`ExpressionAnalyzer`), pointer conversions
(`isPermissionDowngrade`, `isStaticArrayViewDecay`, `isPointerConversion` —
delegating wrappers over
`PointerPermissions`), literal parsing (`parseIntegerLiteral`:
0b/0x/decimal), element classification (`isTrivial` — memset-filled
byte-wise; `isCopiable` — memcpy/memcmp-copied byte-wise; `isByteType` — the
byte/bit intrinsic element type) — recursive with path-scoped cycle detection
so diamond-shaped field graphs stay fine while by-value cycles terminate.
`ensureAssignable`
accepts the pointer conversions alongside exact matches and
also special-cases array targets fed a pointer-to-array source: the error
suggests `memcpy`, and names string literals or array literals explicitly
when the source is a hoisted literal view (`getview(<global>)` over a
`literalBytes`/`literalValues` global).
It does not traverse the AST.

#### `PointerNullStateChecker`
Intra-procedural pointer null-state dataflow, run after legality checking and
deliberately independent of the checker cluster (only `DiagnosticBag`). It
is **strictly intra-procedural by design** — no facts cross function
boundaries (interprocedural nullability is an unsolved problem), and state is
reset at every function entry; the only cross-function assumptions are the
two documented entry seeds below. One
lattice fact per pointer-typed variable (`ptr`/`mview`/`view` are all
tracked): `POINTING` / `NULL` / `MAYBE_NULL`,
initialized to `MAYBE_NULL` at function entry (except method `self` and
`main`'s args param, seeded `POINTING`). Transfer rules: pointer decls are guaranteed NULL-initialized;
`p = getptr(x)` / `getmview(x)` / `getview(x)` → POINTING (the only
certifications); `p = null` → NULL; `p = q` copies state; anything else →
MAYBE_NULL. Guard refinement: `p == null` / `p != null` conditions refine
states on each path; exploration is path-sensitive up to
`MAX_BRANCH_DEPTH = 12`, with an insensitive fallback beyond the cap.
Loops track break/continue jump states via snapshot slots (`LoopFrame`,
preallocated `PointerState[][]` store), merge back-edge states, and compute
zero-trip exit states. Reports deref of definitely-NULL as error and
maybe-null as warning (each pointer warns at most once per check site).

#### `DanglingPointerChecker`
Return-escape provenance analysis, run after both pruners and after
`PointerNullStateChecker` (so dead functions/branches and dead returns
cannot produce diagnostics), deliberately independent of the checker
cluster (only `DiagnosticBag`, `PointerPermissions`, and the semantic AST).
It is **return-only and intra-procedural**: each function/method body is
analyzed separately with a fresh state, and only `return` expressions are
classified. Passing a pointer to a callee, storing one into a global or
out-parameter, pointers hidden inside returned structs/arrays, use after
an inner block, dynamic-array buffer lifetime, and nullness are out of
scope (nullness stays `PointerNullStateChecker`'s job).

The abstract domain is one immutable origin set per pointer-typed
variable (locals, parameters, and globals alike), indexed by
`IdentityHashMap<VariableSymbol, Integer>`:

- `FRAME(s)` — the pointer may refer to symbol `s`'s current-frame slot
  (a local or parameter);
- `GLOBAL(s)` — the pointer may refer to global `s`'s storage;
- `UNKNOWN` — caller-owned/untracked (pointer parameters, call results,
  pointer-typed field/element reads); it may also be null;
- `EMPTY` — definitely null: no frame/global origin and no `UNKNOWN`;
- **definitely non-null** — set for every `getptr`/`getmview`/`getview`
  result, even when its storage origin is `UNKNOWN` (for example
  `getptr($(p))` over a pointer parameter).

Merging is set union. Nullness is a separate definite-non-null flag: a
merged value is definitely non-null only when every continuing path is,
so a null-or-frame value is a *possible* escape (warning) rather than a
definite one (error).
Snapshots store immutable `OriginSet` references, so branch/loop
snapshots are cheap and never alias mutable state. The checker
preallocates the contiguous `OriginSet[][]` snapshot store from an AST
demand estimate, like `PointerNullStateChecker`, and grows it only if the
estimate is low.

`storageOrigins(expr)` maps an lvalue to the region it occupies
(variable → `FRAME`/`GLOBAL`; `$(p)` → `valueOrigins(p)`; member/index
access → its base's storage; cast → its operand). `valueOrigins(expr)`
maps a pointer value to what it points at: `getptr`/`getmview`/`getview`
→ `storageOrigins` of the target; a pointer variable → its current state;
`$(p)` whose result is a pointer → the states of the tracked pointer
cells in `valueOrigins(p)` (with `UNKNOWN` if any cell is untracked);
`cast<T>(e)` → `valueOrigins(e)`; call results and pointer-typed
field/element reads → `UNKNOWN`.

Transfer rules mirror `PointerNullStateChecker` structurally, including
its bounded path-sensitive branch handling, guard feasibility/refinement,
and loop exit refinement:

- pointer declarations start `EMPTY` (they are null-initialized and null
  does not dangle); at function entry every tracked symbol is `UNKNOWN`,
  including parameters and globals;
- a direct pointer-variable assignment is a strong update with the RHS
  origins; a store through `$(d)` strong-updates the single tracked
  pointer cell `valueOrigins(d)` resolves to; any other pointer-typed
  store path invalidates all tracked states to `UNKNOWN`;
- static-to-dynamic view decay adds no origin: it preserves the fat
  pointer's mode/count and creates no frame object, so a decayed
  parameter/global view is safe to return, while a decayed view of local
  array storage carries that local's `FRAME` origin and is caught by the
  ordinary rule; global initializer decays are never analyzed;
- function calls emit no escape diagnostic, but conservatively forget
  state: all global pointer states, tracked pointer cells reachable from
  pointer-typed arguments, and pointer locals whose address was taken by
  `getptr`/`getmview` become `UNKNOWN`;
- `if`/`else` analyzes both branches from the pre-branch snapshot and
  merges only continuing paths. Exploration is path-sensitive to
  `MAX_BRANCH_DEPTH = 12` nested branches, then falls back to a
  path-insensitive merge;
- a null guard refines the origin state: the `p == null` path sets `p`
  to `EMPTY`; `p != null` over an `EMPTY` pointer and `p == null` over a
  definitely-pointing pointer are infeasible. Returns inside an
  infeasible path are not diagnosed;
- `while` analyzes the body once, replays the update epilogue for normal
  and `continue` edges, merges zero-trip and back-edge states, then
  merges `break` snapshots. The condition-false exit of
  `while (p != null)` refines `p` to `EMPTY` before `break` states are
  merged;
- block-local pointer states are reset to `UNKNOWN` when the block ends.

At a return of pointer type, the checker classifies the value's origins:
`mayDangle = FRAME non-empty`;
`definiteDangle = FRAME non-empty, no UNKNOWN, no GLOBAL, and definitely non-null`;
`possibleDangle = mayDangle and not definiteDangle`. Definite frame
escapes are errors; possible escapes after a branch merge are warnings.
There is no separate decay classification: a decayed view is just a fat
pointer over its original storage.

#### `DynamicArrayMemoryChecker`
Intra-procedural lifecycle analysis for function-local dynamic arrays,
run after both pruners and before `PointerPermissionChecker`. Tracks
`UNINITED`/`INITED`/`MAYBE_INITED`, owner `ptr<[T]>` alias sets,
`length()` guard refinement, loop exits, and owner-pointer call
invalidation. Missing explicit `uninit()` is a warning because
`Franko_Dynamic_Array`'s destructor already frees local buffers. Struct
fields, nested dynamic-array elements, element pointers, and
`mview`/`view` aliases are not tracked.

#### `PointerPermissionChecker`
The capability linter: run after branch/declaration pruning (so only
surviving code counts) and before codegen, warnings-only. Per function it
tracks the **high-water mark** of permissions exercised by each local/parameter
of type `ptr<T>`/`mview<T>` and warns when the declared permission is never
used:
- *Allocation (ptr)*: lifecycle intrinsics, `getptr` over the variable's
  region, or any use where a `ptr<T>` is expected (argument, return value,
  assignment into a `ptr`-typed slot, method `self`).
- *Mutation (mview)*: assignment through the variable's region,
  `memset`/`memcpy`/`memmove`, the byte/bit mutators (`write_*`,
  `write_bit`, `flip_bit`, `bitwise_*_mask`, `shift_bits_*`), or use where an
  `mview<T>` is expected.
- *Read (view)*: reads through the region, `length`, `memcmp`, `memchr`,
  `memmem`, the `read_*` accessors, `read_bit`, `count_set_bits`, or use
  where a `view<T>` is expected.

Attribution follows the pointer-boundary rule (§4.9): an operation through
`$(v)` credits `v` at the level its region is used, but once the access path
crosses another dereference the operation belongs to the extracted inner
pointer and `v` is credited read-only (so `$($(v)[0]).init()` exercises the
inner `ptr`, and `v` is correctly told to become a `view`).

Implementation notes:
- Only function-local variables and parameters are tracked. **Global
  variables are never linted**: their uses span functions, so a
  per-function high-water mark would recommend downgrades based on an
  arbitrary slice of the program.
- Rights are credited per use and the strongest level wins. A bare pointer
  variable consumed as a value is credited at the level the surrounding
  context requires (`requiredLevel`: a pointer-typed slot demands its own
  permission level); a storage path (deref, index, member access) only ever
  credits its region as read.
- The state map is cleared per function and at most one warning is emitted
  per variable: a variable that is only ever read is told to become a
  `view` even when declared `ptr`, a `ptr` exercised at most at the
  mutation level is told to become an `mview`, and a never-credited
  variable is left to `InactiveDeclarationPruner`.
- Warnings never fail the build (`check` reports into its own bag; `Main`
  prints them without an error check).

### 3.4 Branch pruning

#### `BranchPruner`
Runs on the Semantic AST **after** `LegalityChecker` and **before**
`PointerNullStateChecker` (invoked by `Main`; prints no phase header). It is a
functional, bottom-up rewrite pass: semantic AST nodes are immutable
(`public final` fields), so the pruner rebuilds changed nodes and returns the
original instances when nothing changed. It prunes conditionally dead code and
normalizes loop conditions using a **truth-table evaluation** of the
condition's boolean structure:

- **Connective structure**: `&&`, `||`, `!` nodes form the propositional
  skeleton of the formula.
- **Atoms**: effect-free `uint8`-typed leaves — comparisons
  (`== != < > <= >=`) over pure operands, and `uint8`-typed variable, member,
  deref, or array-access reads. Syntactically identical atoms (structural
  comparison with **symbol object identity** for variables/fields — never
  names, which may shadow) share one truth-table variable.
  `==`/`!=` pairs with identical operands are canonicalized to one variable
  and its negation, so classic null-guard patterns like
  `p == null || p != null` (tautology) and `p == null && p != null`
  (contradiction) are detected.
- **Effect rule**: any function call or array intrinsic call anywhere in the
  condition blocks pruning of that statement entirely — a pruned condition is
  *discarded* and never evaluated, so a call's side effects must survive.
- **Trap policy**: derefs (`$(p)`) and array indexing are allowed as atoms
  even though they can trap; when a condition is pruned, such a trap
  disappears with it (deliberate trade-off).
- **Enumeration**: at most `MAX_ATOMS = 10` variables (≤ 1024 rows); atoms are
  treated as independent propositional variables, which over-approximates
  reality — so "true on all rows" / "false on all rows" soundly implies the
  condition is a tautology / contradiction. More than 10 atoms → no pruning.
  Zero atoms recovers plain constant folding (a folded `constantValue`
  decides immediately).
- **Outcomes**: an always-true `if` keeps only its pruned then-branch; an
  always-false `if` keeps only its pruned else-branch, or disappears
  entirely when there is no else; an always-false `while` is removed with its
  body and update epilogue; an always-true `while` is normalized to
  `while (1)`; anything undecided is left in place with recursion into its
  branches/body/update.
- **Scoping rule**: a pruned branch is returned as-is (a block stays a block,
  a single statement stays a single statement) and is never flattened into the
  parent block — flattening could collide same-named locals (both emitted as
  `VAR_x`) in the generated C++. `checkEmittedNameCollisions` remains as the
  final safety net.
- **Effect on later phases**: all checker passes and codegen see the
  pruned tree, so diagnostics and code are never produced for dead
  branches, and dead loops vanish from the emitted C++.

### 3.5 Inactive declaration pruning

#### `InactiveDeclarationPruner`
Runs on the Semantic AST **immediately after `BranchPruner`** and before
`PointerNullStateChecker` (invoked by `Main`; prints nothing). It removes
declarations that nothing in the surviving code uses, so they are never
emitted into C++ at all. The rule is zero-reference pruning — no liveness or
dead-store analysis:

- **Functions**: kept iff reachable via the call graph from the entry point
  `main` (call closure over `SemanticFunctionCallNode` symbols — includes
  methods; recursion and overloads are resolved symbols so each is counted
  separately). `main` is always kept (its mangled name is pinned in the
  program templates). Call sites on the *target* of an assignment count
  (e.g. `xs[idx()] += 10` keeps `idx`).
- **Variables**: kept iff referenced anywhere in kept function bodies.
  Assignments count as uses (an initializing assignment keeps its variable),
  so the only variables pruned are declared-but-never-mentioned ones —
  including variables whose only uses disappeared with a BranchPruner cut.
  A kept global additionally keeps alive whatever its static address
  initializer references (e.g. hoisted string-literal globals), transitively.
- **Structs**: kept iff a `SemanticStructType` for them appears in surviving
  code: kept function signatures, kept variable declarations (globals and
  locals), expression types in kept bodies, or the receiver of a called
  method. Struct liveness is expanded transitively through the fields and
  kept methods of live structs (by-value composition). A kept struct prunes
  its never-called methods; a pruned struct takes all its methods with it.
- If pruning empties a namespace, the namespace node itself is dropped.
  Blocks are never flattened (same scoping rule as `BranchPruner`).
- If no `main` is found (cannot happen after successful analysis), the pass
  is a no-op.
- **Interaction with the os FFI bridge**: `OsFfi.verifyBridge` runs later and
  walks the surviving declarations, so unused os intrinsics pruned here no
  longer require a bridge block; used ones are kept and verified as before.

### 3.6 Backend

#### `Cpp14Codegen`
The C++14 backend: walks the checked Semantic AST and emits the translation
unit body that replaces `__FRANKO_PROGRAM__`. It performs **no** legality
checking — it trusts every prior phase. Emission order is dependency-safe:
struct forward declarations → struct definitions topologically sorted by
by-value field dependency (with NSDMI initializers for folded field defaults,
`= nullptr` for pointer fields) → global forward declarations (`extern`
per global, reopening each `NS_<segment>`) → global definitions → function
prototypes →
function definitions, all under mangled names and inside `NS_<segment>`
namespaces where applicable. The global forward declarations let a
pointer global's static address initializer reference a global declared
later in the source (or across namespaces); uninitialized pointer
variables (globals and locals alike) emit `= nullptr`, implementing the
guaranteed null-initialization rule. Statement lowering: `if` with constant-dead-
branch pruning, `while` with goto-based `L_brk_<id>`/`L_cont_<id>` labels plus
the desugared for-update epilogue (continue label placed *before* the
epilogue so `continue` runs the update and `break` skips it).
Expression lowering is structural. A pointer to an array emits
`Franko_Fat_Pointer<element(T)>`, where `element(T)` is the immediate
element type (`ptr<[U, N]>` and `ptr<[U]>` both emit
`Franko_Fat_Pointer<U>`). A pointer to a scalar, struct, or another
pointer emits raw `T*`. The fat pointer records the array mode: 2
static array with count `N`, or 3 dynamic array with the
`Franko_Dynamic_Array<T>` descriptor pointer. `getptr`/`getmview`/
`getview(x)` emit `(&x)` for a scalar/struct lvalue,
`franko_make_fat_pointer(x, N, 2)` for a static array, and
`franko_make_dynamic_fat_pointer(&x)` for a dynamic array (a distinct
name, because a static array of `Franko_Dynamic_Array<T>` is
pointer-indistinguishable from a `Franko_Dynamic_Array<T>` lvalue). A
dereference emits `(*p)` for scalar/struct pointers,
`(*static_cast<Franko_Dynamic_Array<T>*>(p.data))` for dynamic arrays,
and `franko_static_array_ref<T, N>(static_cast<T*>(p.data))` for static
arrays. Method `self` is a struct pointer, so it is raw `STRUCT_X*` at
the call boundary. Static arrays are raw C++ arrays (`int32_t VAR_x[3][2]`, outer
dimension first); locals, globals, fields, and parameters use a
declarator emitter, while return types and template arguments use a
type-id emitter. Pre-folded constants are emitted directly; contextual
`static_cast` via `emitExprAsType` preserves Franko's constant typing and
already-chosen overloads; `unsafe_cast` emits `franko_unsafe_cast`.

Array indexing through a raw static array emits
`franko_static_array_at(array, index)` in ASan and `-O` builds; `-Omax`
emits bare `array[index]` (the `uncheckedStaticArrayAccess` codegen flag,
set only from `-Omax`). Indexing through a pointer emits the fat
pointer's checked `operator[]`, including for a decayed view (`view<[T]>`
whose runtime mode is still 2). Dynamic arrays always use their own
checked `operator[]`, in every mode, so null/uninitialized checks
survive `-Omax`.

Array intrinsics emit `receiver.<runtimeName>(args)`; lifecycle
intrinsics (`init`, `init_zero`, `resize`, `uninit`) stay direct calls on
the dynamic array lvalue, while every other intrinsic wraps its receiver
in a fat pointer: a dereferenced receiver is already a fat pointer and is
reused as-is (keeping a decayed view's mode 2), a static array lvalue
becomes `franko_make_fat_pointer(receiver, N, 2)`, and a dynamic array
lvalue becomes `franko_make_dynamic_fat_pointer(&receiver)`. Scalar
arguments are cast to the argument descriptor's primitive type, pointer
arguments pass through.

Static-to-dynamic array view decay is representation-preserving: both
sides are the same `Franko_Fat_Pointer<U>` and the value keeps mode 2
with count `N`. `emitExprAsType` and `emitCast` therefore emit the value
unchanged, with no temporary descriptor and no prelude buffering.
Literal-backed string and array globals emit as `const uint8_t
VAR__STR_<hash>[N] = {…}` / `const int32_t VAR__ARR_<hash>[N] = {…}`
(constant initialization, so static-init order is irrelevant; nested
literals use one brace level per dimension), and pointer globals with
static initializers emit the fat pointer over them directly. A
`checkEmittedNameCollisions` guard catches 32-bit hash collisions between
distinct signatures. When `franko/os` was imported, `generate()` prepends
the `OsFfi.buildBridge` splice (common block + one block per used
intrinsic) and suppresses the os.fr stub bodies; each intrinsic call site
is also checked with `OsFfi.checkTargetAllowed`.

#### `Mangler`
Stateless FNV-1a 32-bit hash utility: `hashHex(key)` → 8 lowercase hex digits
over the UTF-8 bytes of the canonical signature key. Deterministic across
runs/JVMs/platforms. Called only from `Symbol` (`FunctionSymbol.mangledName`,
`MethodSymbol.mangledName`), but its outputs are pinned in three places:
`OsFfi.expectedEmittedName`, both program templates
(`FUNC_main_HASH_d7d11c43` / `FUNC_main_HASH_747cdf98`), and `Main`'s
entry-point sanity check.

#### `ProgramTemplate.cpp` / `ProgramTemplateWithRawCArgs.cpp`
The fixed program skeleton codegen deliberately does not emit: the runtime
`#include` (a `__FRANKO_RUNTIME_HEADER__` placeholder), the `__FRANKO_PROGRAM__` insertion point, and
the real C `main` wrapper that calls the mangled Franko `main`. The no-args
template calls `FUNC_main_HASH_d7d11c43()` directly; the raw-C-args
template marshals `argc`/`argv` byte-for-byte (no null terminators) into
`Franko_Dynamic_Array<Franko_Dynamic_Array<uint8_t>>` and calls
`FUNC_main_HASH_747cdf98(franko_make_dynamic_fat_pointer(&frankoArgs))`.
`Main` sanity-checks that the chosen template contains the expected entry
call — a cheap guard against template/mangling drift, mirroring
`OsFfi.verifyBridge`. An explicit `-t <template>` skips the check. Every
stock template includes `"FrankoRuntime.hpp"` through
`__FRANKO_RUNTIME_HEADER__`; the build mode is selected later by the C++
step's `-DFRANKO_USE_JEMALLOC` / `-DFRANKO_BOUNDS_UNCHECKED` flags
(`compile.native_compile`), so `Main` no longer chooses a header. A stock
template missing the placeholder is rejected, while a custom `-t`
template without it keeps its pinned `#include` untouched.

#### `SemanticASTPrinter` / `ASTPrinter`
Debug printers (`--verbose` only): `ASTPrinter` for the raw AST at three
checkpoints, `SemanticASTPrinter` for the final semantic AST (types,
constant values, lvalue flags, mangled names, symbol identity hashes).

### 3.7 Support

#### `DiagnosticBag`
Phase-agnostic error/warning accumulator with a configurable header and
numbered `formatErrors()`. Knows nothing about symbols, scopes, ASTs, or
types. Used by `SymbolTable` (semantic phase), `Desugarer`, `Main` (legality
and pointer-state bags), and every checker.

#### `Main`
The driver: CLI parsing (`-o`, `-t`, `-p`, `-v`, `-O`, `--target`), the phase
sequence shown in §1, template selection by `sema.entryKind()`, template
sanity checks, and output writing. Its nested `LexErrorCollector` turns ANTLR
lexer "token recognition" errors into hard failures (the lexer would otherwise
silently skip unrecognizable characters, letting programs like `a % b` or
`~a` compile into a different program).

---

## 4. Cross-cutting design rules

### 4.1 Desugaring contracts (Desugarer)
- `for (init; cond; update) body` → `{ init; while (cond) { body; update; } }`
  where `update` is carried as the while node's epilogue so `continue` runs it
  and `break` skips it — exactly C-like semantics. Codegen places the continue
  label before the epilogue.
- `x++`/`x--` → `x += 1`/`x -= 1`.
- `view<[...]> x = [a,b,c]` is **not** rewritten: the array literal
  reaches semantic analysis as an expression and is lowered to a hoisted
  literal view (§4.13). Assigning a literal to an array is a checked error
  that suggests `memcpy`.
- `view<[T, ?]>` size inference: the literal must be a perfect static
  rectangle (no jagged); inference failure is an error, with size-1
  placeholder recovery.
- Every `constExpr` slot (array sizes, global const initializers, field
  defaults, cast constants, break levels) is folded here to a canonical
  decimal `IntNode` — later phases see only integers.
- String literals are hoisted to literal-backed globals and replaced with
  `getview(<global>)` everywhere (§4.10); array literals get the same
  treatment during semantic analysis (§4.13). Assigning a literal to an
  array is a checked error that suggests `memcpy`.

### 4.2 Fluid integer constants
Integer literals and folded constant expressions are **fluid**: a constant is
checked by its `BigInteger` value against the target type's range, not by the
type it would have in C++. Consequences:
- One operand of an arithmetic/comparison/compound-assign expression may be a
  fitting constant; then the result takes the other operand's type
  (`inferBinaryResultType`).
- Overflow of a constant in the target range is an error; runtime values are
  exact-type-checked.
- `cast<T>(...)` performs only value-preserving integer widening (plain
  `static_cast`); a conversion that can lose data must be written
  `unsafe_cast<T>(...)`, whose constants are range-checked at compile time
  (`ExpressionChecker.visitCast`) and whose runtime values emit the checked
  `franko_unsafe_cast`.

### 4.3 Name mangling
- `FUNC_<name>_HASH_<8hex>` / `METH_<name>_OFSTRUCT_<Struct>_HASH_<8hex>`,
  where the hash is FNV-1a 32-bit of the canonical `signatureKey()`.
- `STRUCT_<name>`, `VAR_<name>`, `NS_<segment>` prefixes/namespaces make
  kinds unambiguous, so struct names may legally clash with variable/function
  names.
- Overload identity = name + ordered parameter types (return type excluded).
- Pinned-hash drift guards: `Main` (template entry call), `OsFfi`
  (`expectedEmittedName`).

### 4.4 os FFI bridge
- Activation: only the canonical `import "franko/os"` (recorded by
  `ImportResolver`) turns on target constants, target gating, and bridge
  splicing; a user-defined `os` namespace without that import is untouched.
- `os.ffi.cpp` is a marker-structured file (`// @@franko-ffi:<name>` …
  `// @@franko-ffi:end`, plus `common`); codegen emits the used intrinsic
  bodies verbatim under their pinned mangled names and suppresses the Franko
  stub bodies.
- Signature drift on a known intrinsic name is a hard compile error; stubs
  must never silently compile.
- `os.ffi.cpp` is split into `common` plus one block per intrinsic:
  `posix.read`, `posix.write`, `win32.GetStdHandle`, `win32.ReadFile`,
  `win32.WriteFile`. Each block is spliced verbatim under the same pinned
  mangled name codegen emits for the `os.fr` declaration
  (`FUNC_read_HASH_247cb6de`, `FUNC_write_HASH_3a46712f`,
  `FUNC_GetStdHandle_HASH_31d0af80`, `FUNC_ReadFile_HASH_955d9645`,
  `FUNC_WriteFile_HASH_ab68bdd0`); the hashes move with signature changes,
  including pointer permissions.
- Codegen emits prototypes for every intrinsic but never the stub bodies;
  the bridge supplies the definitions. The splice runs after the template’s
  `#include "FrankoRuntime.hpp"`, so the runtime types are available.
  Merely importing `franko/os` without calling an intrinsic adds no C++
  definition (only the harmless prototypes).
- Buffer parameters are array pointers, so they are
  `Franko_Fat_Pointer<uint8_t>` by value. The bridge checks the runtime
  mode (2 static array, 3 dynamic array), treats a null or uninitialized
  buffer as its failure return value, and reads through
  `VAR_buf.data` (mode 2) or
  `static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->data`
  (mode 3). Scalar out-parameters such as `mview<uint32> bytesRead` are
  raw `uint32_t*` (scalar pointers are raw); the bridge null-checks the
  pointer and writes through it.
- Verification is a hard compile error for a known intrinsic with the
  wrong signature, for an unsupported function in `os::posix` /
  `os::win32`, and for a missing `os.ffi.cpp` or a missing mangled
  definition (`OsFfi.verifyBridge`, mirroring the program template’s
  entry-call check).
- Runtime contract: `count` is checked against the buffer’s array length
  before each transfer and aborts through `franko_panic` on overflow; a
  null or uninitialized buffer returns the stub failure value instead of
  crashing. win32 blocks compile to `#error` off Windows.

### 4.5 Pointer null-state model (PointerNullStateChecker)
- Strictly intra-procedural: one analysis per function, state reset at every
  function entry, no facts cross function boundaries (interprocedural
  nullability is deliberately not attempted). Dangling pointers are handled
  separately by the return-only `DanglingPointerChecker` (§4.14), and
  dynamic-array init/uninit lifecycle by `DynamicArrayMemoryChecker`
  (§4.15).
- `getptr(x)`/`getmview(x)`/`getview(x)` are the only certifications of
  POINTING; every other value source is MAYBE_NULL unless it is `null` (NULL)
  or a pointer copy.
- Deref of definitely-NULL = error; maybe-null deref = warning.
- Guards (`p == null`/`p != null`) refine per-path; branch exploration is
  path-sensitive to depth 12, then falls back to insensitive checking.
- Loops include zero-trip and back-edge exits only when the
  condition-false guard is feasible for that state, merge `break` and
  `continue` snapshots separately, and treat a loop with no feasible exit
  as non-falling-through.

### 4.6 Analyzer vs checker split
- `ExpressionAnalyzer` (SemanticAnalyzer phase) **infers**: it resolves
  symbols and overloads and decorates types/constants but validates nothing.
- `ExpressionChecker`/`StatementChecker`/`DeclarationChecker`/`FunctionChecker`
  (LegalityChecker cluster) **validate** the already-inferred tree; they never
  infer types.
- `TypeChecker` is the shared predicate/diagnostic library both sides use.

---

### 4.7 Conditional branch pruning (BranchPruner)
- A condition is pruned only when it is a **tautology or contradiction** under
  a truth table of at most 12 effect-free `uint8`-typed atoms; `==`/`!=`
  pairs over identical operands are the same variable and its negation.
- Pruning **discards** the condition, so conditions containing function or
  array-intrinsic calls are never pruned (effects must survive); derefs and
  array indexes may be atoms, meaning a trap in a pruned condition disappears
  (deliberate).
- `if`: true → then-branch only; false → else-branch only, or the statement
  disappears. `while`: false → loop removed entirely; true → condition
  normalized to `1`.
- Pruned branches are spliced as-is, never flattened into parent blocks
  (keeps `VAR_x` name scopes intact in the emitted C++).
- Runs after all checking and before pointer null-state analysis, so dead
  code receives no null-state diagnostics and never reaches codegen.

### 4.8 Inactive declaration pruning (InactiveDeclarationPruner)
- Runs right after branch pruning so declarations orphaned by branch cuts
  become prunable.
- Zero-reference rule, per kind: functions must be call-reachable from
  `main`; variables must be mentioned somewhere in kept bodies (assignments
  are mentions); structs must appear as a type in kept code (signatures,
  kept variable declarations, expression types, method receivers), expanded
  transitively through fields and kept methods.
- No dead-store elimination: a variable that is only written still counts as
  used. No unused-field pruning. Unused methods of kept structs are pruned.
- Pruning never flattens blocks and never touches `main`.

## 4.9 Pointer permissions (capability model)

Franko has one pointer family with three permission levels (`PtrPermission`,
strongest first): `ptr<T>` (OWNER), `mview<T>` (MUTABLE), and `view<T>`
(READ_ONLY). Permissions are enforced entirely by the checkers and are
not represented in the C++ type. A pointer to an array emits
`Franko_Fat_Pointer<element(T)>`, whose runtime mode records static
array (2) or dynamic array (3) and whose count records a static array's
length. A pointer to a scalar, struct, or another pointer emits raw
`T*`, which keeps scalar/struct method calls and pointer fields at the
raw-pointer ABI. This split is deliberate: a scalar pointer has no array
mode or count to carry, and passing a 16-byte fat pointer by value for
every method `self`/pointer parameter makes ASan debug builds
materialize an instrumented stack copy per call. Array pointers are the
only representation that needs `{data, count, mode}`.

**The region rule.** Every storage-backed lvalue access path touches one
*storage region*. The region's permission is the permission of the operand
type of the innermost pointer boundary — the **last** `$(...)` dereference
reached on the way down from the root of the access path (the one nearest the
leaf). Index and member accesses stay inside the region created
above them; each dereference **resets** the region from the pointer's own
type. A bare variable's own storage is always owner-protected. Consequences:

* writes (assignment, compound assignment), `memset`/`memcpy`/`memmove`
  targets, and the byte/bit mutators (`write_*`, `write_bit`, `flip_bit`,
  `bitwise_*_mask`, `shift_bits_*`) need a region allowing MUTABLE;
* `init`/`init_zero`/`resize`/`uninit` (and the `xs(size)` shorthand) need an
  OWNER region;
* reads, `length`, `memcmp`, `memchr`, `memmem`, the `read_*` accessors,
  `read_bit`, `count_set_bits`, and memcpy/memcmp/memmem *sources* are always
  fine;
* `getptr(l)` requires an OWNER region, `getmview(l)` a MUTABLE-or-stronger
  one, `getview(l)` anything — this is what makes `view` airtight (no
  laundering through `getptr($(v))`) while `$($(v)[0]).init()` still works
  when the extracted element is itself a `ptr` (Rule 2).

**Nested propagation.** Permissions propagate transitively through value
types (arrays, structs) and stop at pointer boundaries: `view<[[T]]>`
locks the inner arrays too, while `view<[ptr<[T]>]>` locks only the
stored pointers (they cannot be overwritten) — the memory behind them follows
each pointer's own type. Lifecycle intrinsics need an OWNER region at *every*
level: `$(m)[0].init(...)` through an `mview` is blocked exactly like
`$(m).init(...)`; only an extracted `ptr` (Rule 2) restores allocation
rights. `PointerPermissions` is the single home of the permission rules:
`regionPermission` (the region rule above), `isPermissionDowngrade` (the
identical-referenced-type downgrade), `isStaticArrayViewDecay` (the
outermost-dimension static-to-dynamic array decay), `isPointerConversion`
(their union: the full implicit-conversion predicate), and
`creationIntrinsic` (which of
`getptr`/`getmview`/`getview` creates a given permission level, used in
diagnostics); the checkers, `SymbolTable`, `TypeChecker`
(whose three conversion predicates are delegating wrappers), and
`PointerPermissionChecker` all share it.

**Conversions.** The **only** implicit conversions in all of Franko are
pointer conversions, allowed in assignments, returns, and argument
passing (overload resolution prefers exact matches, then pure permission
downgrades, then decays — see below):
- a pointer permission downgrade over an identical referenced type
  (`ptr→mview`, `ptr→view`, `mview→view`);
- the **static-to-dynamic array pointer view decay**: `mview<[T,N]>`/
  `view<[T,N]>` (source `mview`/`view`, or a `ptr` source combined
  with a downgrade) convert to `mview<[T]>`/`view<[T]>`. Only
  the outermost dimension decays (`T` must match exactly, and `T` may
  itself be a static or dynamic array), the direction is unidirectional,
  and the target is never `ptr` (ownership could free storage the
  static array owns). Explicit form: `cast<mview<[T]>>(m)`.

Everything else is exact. Explicit downgrades may be
written `cast<view<T>>(p)`; casting upwards or across pointee types is an
error. Integer `cast` is value-preserving widening only; `unsafe_cast`
carries the checked conversions (`franko_unsafe_cast`).

**Decay implementation.** Decay is representation-preserving.
`ptr<[T, N]>`/`mview<[T, N]>`/`view<[T, N]>` and their dynamic
counterparts all emit `Franko_Fat_Pointer<T>`; a static array pointer
carries mode 2 with count `N`, and a dynamic array pointer mode 3 with
the `Franko_Dynamic_Array<T>` descriptor address. Scalar/struct
pointers are raw `T*` and are never involved in decay. Converting
`view<[T, N]>` to `view<[T]>` creates no object: the C++ value is copied
unchanged and remains mode 2/count `N`. `Cpp14Codegen` therefore emits
nothing for the conversion (no prelude, no `_TMP_VIEW_*`, no temporary
header), and a decay in a loop condition needs no goto rewrite. The same
holds for `cast<...>` and for argument/return/assignment conversions.
Because there is no descriptor, `DanglingPointerChecker` has no decay
origin (§4.14): returning a decayed parameter or global view is safe,
while returning a decayed view of local array storage is the ordinary
frame-storage escape. Lifecycle intrinsics stay blocked on the result
(the region rule), and `ptr` never decays.

**Method `self`.** `self` may be `ptr<X> self`, `mview<X> self`, or
`view<X> self`. The hidden self argument at a call site is synthesized as
`getptr`/`getmview`/`getview(receiver)` matching the declared self type, so
an under-qualified receiver is rejected by the creation-intrinsic rule above.
Method overload identity excludes `self`'s type.

## 4.10 String literal hoisting (Desugarer)

A string literal is **not** byte-array sugar: it is a value of type
`view<[byte, N]>` (N = UTF-8 byte length) over a hoisted, literal-backed
global `[byte, N]` named `VAR__STR_<content-hash>`. Identical literals
share one global; empty literals are rejected (`N >= 1`). The C++ emission is
a `const uint8_t VAR__STR_<hash>[N] = {…}` (constant initialization),
so declaration order and static-init order never matter. A string
literal expression is `franko_make_fat_pointer(VAR__STR_<hash>, N, 2)`;
decaying it to `view<[byte]>` copies the same mode-2 fat pointer.

Consequences of strings being views:
* `s = "text"` / `[byte, N] s = "text"` are compile errors suggesting
  `dst.memcpy("text")` — the literal is a valid memcpy source;
* `?` size inference accepts array literals only (in `view` declarations,
  §4.13);
* pointer-typed globals may be initialized with `null` or a
  `getptr`/`getmview`/`getview` over a global, enabling
  `view<[byte, 5]> G = "hello";`;
* `print("hi")` prints the text `hi` through the `franko/io` overload for `view<[uint8]>`.

## 4.11 Runtime array and allocation semantics

The generated code includes the single `include/FrankoRuntime.hpp`.
The C++ build flags select the mode:

| Build | Defines | Allocator | Checks |
|---|---|---|---|
| ASan/default | none | system `malloc`/`calloc`/`realloc`/`free` | full |
| `-O` | `FRANKO_USE_JEMALLOC=1` | bundled jemalloc (`je_*`) | full |
| `-Omax` | `FRANKO_USE_JEMALLOC=1` `FRANKO_BOUNDS_UNCHECKED=1` | bundled jemalloc | bounds/range/bit/offset/unsafe_cast stripped |

All modes share the same array semantics (`Franko_Dynamic_Array`,
`Franko_Fat_Pointer`, static-array helpers, byte kernels); only the
allocator and the checked blocks differ.

Dynamic array element buffers are allocated by the array runtime methods
and freed by `uninit()` or by the array object's destructor. Static
arrays are raw C++ arrays and are never heap-allocated. Decay creates no
descriptor, so there is no borrowed-header marker.

Element construction follows the C++ type of the elements: buffers for
trivially default-constructible element types keep raw storage, while any
other element type is default-constructed (placement new) when the buffer
is allocated. `uninit()` and the array destructor destroy constructed
elements before freeing the buffer. Trivially destructible element types
skip destruction entirely. Element lifetime in the dynamic array runtime:

- `init(n)` and `init_zero(n)` default-construct all `n` elements when the
  element type needs construction; trivially constructible element types
  keep raw (`init`) or zeroed (`init_zero`) storage with no per-element
  work.
- `resize(n)` default-constructs the grown tail when growing and destroys
  the dropped tail when shrinking; trivial element types skip both. The
  dropped tail is destroyed **before** the buffer is reallocated (its
  storage must still be alive for the elements' destructors to run), and
  on a failed shrink the surviving `count` is narrowed so already-destroyed
  elements are never destroyed again.
- `uninit()` and the array destructor destroy all `count` elements before
  freeing the buffer; trivially destructible element types skip
  destruction.
- `realloc` byte-moves the existing elements on `resize`, which is fine
  for the runtime's trivially-relocatable element types.

Array `memcpy` is designed to support the combinations
`Static<T,N>.memcpy(Static<T,M>)`, `Static<T,N>.memcpy(Dynamic<T>)`,
`Dynamic<T>.memcpy(Dynamic<T>)`, `Dynamic<T>.memcpy(Static<T,M>)`.
`memcmp` supports the same four shape combinations in both the whole-array
and ranged forms; the whole-array form compares the shared prefix with
`std::memcmp` and orders by length on a tie, returning `int32`.
Static array sizes must be greater than zero because generated C++ uses
fixed-size arrays, and zero-length C++ arrays are not standard. Static and
dynamic arrays runtime-check indexes and intrinsics and raise
`std::runtime_error` (via `franko_panic`) on violations such as
out-of-bounds access, use of an uninitialized dynamic array, double
initialization, or zero-size allocation.

The byte-array intrinsics (`memchr`/`memmem`, the endian accessors, the bit
intrinsics) share one kernel layer (`franko_byte_arrays`) of free templates
over `(pointer, length)` pairs; each `Franko_Fat_Pointer` member is a thin
delegate that supplies its class tag and the data/count for its runtime
mode (static storage for mode 2, the `Franko_Dynamic_Array` descriptor for
mode 3). Byte intrinsics exist only on `Franko_Fat_Pointer`, never on
`Franko_Dynamic_Array` or raw static arrays; codegen wraps those receivers
in a temporary fat pointer. `memchr`/`memmem` return `MEM_NOT_FOUND` (`0xFFFFFFFF`) on no
match; the endian accessors are unaligned byte loads/stores built on
`std::memcpy`; bit index `i` means byte `i / 8`, bit `i % 8` counted from
the LSB. Every kernel is SFINAE-restricted to `uint8` elements at the C++
layer as a backstop behind the compiler's byte-only rule.

The dynamic array length is stored in a `count` field with a `length()`
accessor returning `uint32` (a field and method may not share a name in
C++). Reading `length()` on an uninitialized dynamic array returns `0`
and never aborts; every other uninitialized access still aborts. Static
arrays keep plain contiguous `T data[N]` storage with no length field or
method: `staticarr.length()` in Franko source is emitted as a constant,
never as a runtime call. Zero length and uninitialized coincide exactly:
`init(0)` and `resize(0)` abort, and `uninit()` restores
`data == nullptr, count == 0`, so `length() == 0` holds exactly when the
array is uninitialized — there is no initialized empty array.

## 4.12 Emission details

**Integer literals.** Large integer literals are emitted so that they
remain well-formed C++14: an unsuffixed decimal literal whose value
exceeds `LLONG_MAX` is emitted with a trailing `ULL` suffix, because
decimal literals in C++14 do not fall back to unsigned integer types.

**Output.** There is no builtin print statement: output is provided by the
`franko/io` library, whose overloaded `print(...)` functions write through
the `franko/os` bridge (`os::posix::write` / `os::win32::WriteFile`).
Generated C++ contains no `std::cout` insertions.

**Mangling key spelling.** The 8-hex-digit suffix is the FNV-1a 32-bit
hash of the canonical signature key: declared parameter types in order
(including `self` for methods), then the return type, each exactly as
written in source (`int32`, `uint64`, `void`, `[T]`,
`[T,N]` with canonical folded decimal size and no space, `ptr<T>`,
qualified struct names such as `a::b::X`), joined as
`fn(P1,P2,...)->R` (§4.3). When a variable or function shares its name
with a declared struct, semantic analysis emits a warning (the program
still compiles):

```text
==== Warnings ====
  - Variable 'X' shares its name with struct 'X'; generated C++ uses VAR_X for the variable and STRUCT_X for the struct type
```

Struct definitions are emitted after a forward-declaration block
(`struct STRUCT_X;` for every declared struct), so `ptr<X>` fields and
pointer references may name structs declared later in source order.

**Entry-point wrapper and argv.** The generated C++ entry point is not the
Franko `main` itself: the Franko `main` emits its mangled name like every
other function, and a small wrapper in the program template owns the real
C++ `int main` (§3.6). The argv conversion follows these rules:
`args.length()` equals `argc`, with `args[0]` holding the program name;
each argument is its raw bytes with **no** NUL terminator appended
(consistent with string literals, which also append no terminator); each
inner array is default-constructed by the runtime `init` into the
uninitialized Franko state (`length()` `0`), so an empty argument simply
stays uninitialized; after the Franko `main` returns, the outer array's
destructor destroys the converted inner arrays and frees both levels —
inner and outer arrays the program already released with `uninit()` are
skipped safely.

**Break / continue lowering.** `break` and `continue` lower to `goto`
statements targeting per-loop labels, so multi-level `break N` works
uniformly and the same scheme serves `while` and desugared `for` loops.
Each loop receives a unique label id, and labels are only generated for
loops that are actually targeted:

- `continue` emits `goto L_cont_<id>;` where `L_cont_<id>: ;` sits at the
  end of the loop body, immediately before the loop's epilogue (the
  desugared `for` update), so the label runs the epilogue, matching C's
  `for` semantics.
- `break` emits `goto L_brk_<id>;` where `L_brk_<id>: ;` is placed
  immediately after the loop, so the epilogue is skipped.
- `break N` targets the label of the Nth enclosing loop; `continue` always
  targets the innermost loop.

When a `continue` targets a loop, the body is emitted inside an inner
block with the label after it:

```cpp
while (cond)
{
    {
        /* body */
    }
    L_cont_0: ;
    /* update */
}
```

The inner block keeps the label out of the scope of the body's
declarations: a C++ `goto` may not jump forward past a variable's
initialization within the same scope. Loops with no `break` or `continue`
targeting them emit exactly the code they did before the feature existed:
no labels, no `goto`, no extra blocks.

## 4.13 Array literal values (ArrayLiteralLowerer)

An array literal `[e1, …, en]` is **not** assignment sugar: it is a value
of type `view<[T, N]>` (N = literal length) over a hoisted,
literal-backed global, exactly like a string literal (§4.10). Nested
literals type bottom-up to `view<[[T, M], N]>`; they are static
at every level, so rows must be **perfectly rectangular**. `T` is an
integer primitive.

**Element type inference.** A literal carries no element type; `T` is
taken from the context `ExpressionAnalyzer` passes down (the inference
sites are exactly: assignment RHS — including declaration-initializer
sugar and global initializers —, `return` expressions, and call arguments
including `memcpy` sources, whose context is the receiver's element type
inferred by `ArrayLowerer`). No context (bare statement,
operator operand, `$` operand, …) is the "cannot infer" diagnostic. The
context may also be an array type (so `arr = [1,2,3]` reports the
memcpy-suggestion error with a well-typed literal) or a non-`view`
pointer (the literal types, then the ordinary permission rules reject
the upgrade). Lengths come from the literal itself and are checked by
type equality or the outermost static→dynamic decay
(`view<[T,N]>` → `view<[T]>`); the decay is one-way and
outermost-only, which is how a parameter like
`view<[[int32, 13]]>` enforces a maze row width.

**Constant elements only.** Every element must be a compile-time constant
expression (fluid-constant fit against `T` applies). Non-constant
elements are rejected: a literal is hoisted to a `const` global, so it is
always safe to return one or to return one from a recursive function —
there is no statement-scoped storage and no dangling case. Hoisting is
per (element type, row-major values) pair, so identical literals share
one `_ARR_<hash>` global. C++ emission is a raw C++ array,
`const int32_t VAR__ARR_<hash>[N] = {…}` (outer dimension first, one brace
level per dimension, e.g. `{{1, 2}, {3, 4}}`); no wrapper struct is
emitted.

**Immediate decay on literals.** A literal is hoisted as one constant
global `_ARR_<hash>`; its expression is a fat pointer over that storage
(mode 2, count `N`). Decaying it at an inference site
(`view<[T,N]>` → `view<[T]>`) is representation-preserving: the same
mode-2 value is used, with no `_VIEW` header global and no
`GlobalVariableSymbol.literalHeaderData`. Returning or keeping such a
view is safe, including from recursive functions, and
`InactiveDeclarationPruner` only has to follow the literal data global
(the expression's `getptr` target).

**Overload resolution.** Call arguments lower in two phases
(`SymbolTable.CallArg`): a pending literal matches any `view<[...]>`
parameter of compatible shape (exact length = exact match, dynamic outer
= decay), and is re-lowered with the selected parameter type afterwards;
if several overloads remain it is an ambiguity error.

**Globals.** A pointer-typed global may be initialized with a constant
array literal (`view<[int32, 4]> G = [1,2,3,4];`); array-typed
globals may not (memcpy suggestion, §4.10 parity).

## 4.14 Dangling-pointer model (DanglingPointerChecker)

A standalone return-only pass that rejects pointer/view values escaping a
function while referring to storage owned by that function's frame. It
runs after `BranchPruner`, `InactiveDeclarationPruner`, and
`PointerNullStateChecker`, so only surviving code and live functions are
analyzed.

**Origin lattice.** Each tracked pointer variable holds an immutable
origin set:

- `FRAME(s)` — may refer to local/parameter `s`'s current-frame slot;
- `GLOBAL(s)` — may refer to global `s`'s storage;
- `UNKNOWN` — caller-owned/untracked: pointer parameters, call results,
  pointer-typed field/element reads; it may also be null;
- `EMPTY` — definitely null: no frame/global origin and no `UNKNOWN`;
- **definitely non-null** — set for every
  `getptr`/`getmview`/`getview` result, even when its storage origin is
  `UNKNOWN` (for example `getptr($(p))` over a pointer parameter).

Merging is union. Nullness is a separate definite-non-null flag: a
merged value is definitely non-null only when every continuing path is,
so null-or-frame is a possible escape (warning), not a definite one
(error). At function entry
every tracked symbol — parameters, globals, and locals — starts
`UNKNOWN`. `storageOrigins` follows lvalue paths: a variable is
`FRAME`/`GLOBAL`, `$(p)` is `valueOrigins(p)`, member/index access
inherits its base's storage, and a cast inherits its operand's.
`valueOrigins` maps pointer values: creation intrinsics use the target's
storage; pointer variables use their current state; a pointer-typed
`$(p)` reads the states of the tracked pointer cells `p` may point at
(any untracked cell contributes `UNKNOWN`); a cast inherits its
operand's origins; calls and pointer-typed field/element reads are
`UNKNOWN`; `null` is `EMPTY`.

**Transfers.** Pointer declarations start `EMPTY` (they are
null-initialized and null does not dangle). Direct pointer-variable
assignment is a strong update; `$(d) = value` strong-updates the single
tracked pointer cell `valueOrigins(d)` resolves to, and any other
pointer-typed store path invalidates every tracked state.
Static-to-dynamic decay is representation-preserving and adds no origin;
a decayed parameter/global view therefore keeps the value's
caller-owned/global state, while a decayed view of a local array keeps
that local's `FRAME` origin. Calls emit no escape diagnostic but forget
state: global pointer states, tracked cells reachable from pointer-typed
arguments, and address-taken pointer locals (from `getptr`/`getmview`)
become `UNKNOWN`.

`if`/`else` analyzes both branches from the pre-branch snapshot and
merges only continuing paths. Like the null-state check, exploration is
path-sensitive to `MAX_BRANCH_DEPTH = 12` nested branches and then falls
back to a path-insensitive merge. A null guard refines the origin state:
the `p == null` path sets `p` to `EMPTY`; the `p != null` path marks the
surviving value definitely non-null (so a guarded return can be a
definite dangle rather than only a possible one). `p != null` over an
`EMPTY` pointer and `p == null` over a definitely-pointing pointer are
infeasible, and returns inside those dead paths are not diagnosed.
`while` analyzes the body once and replays the update epilogue for
normal and `continue` edges. Each zero-trip/back-edge exit is included
only when the condition-false guard is feasible for that state; `break`
snapshots are merged separately. A loop with no feasible exit is treated
as non-falling-through. Block-local pointer states reset at block end.

**Return classification.** `mayDangle = FRAME non-empty`;
`definiteDangle = FRAME non-empty, no UNKNOWN, no GLOBAL, and definitely non-null`;
`possibleDangle = mayDangle and not definiteDangle`. Definite frame
escapes are errors; possible escapes after a branch merge are warnings.
There is no decay-specific diagnostic; a returned decayed view is simply
a pointer over its backing storage.

**Safe returns.** Pointer parameters and `getptr($(p))` over a pointer
parameter are caller-owned (`UNKNOWN`); `getptr(G)` is global; literal
array/string values (including decayed literals) point at hoisted
constant globals; an existing global view is `UNKNOWN`; decayed views of
parameters or globals are safe; and a local pointer reassigned to a
global address before the return carries only `GLOBAL`. Global
initializers are never analyzed because global declarations are not
function bodies.

**v1 limitations.** No interprocedural escape facts, no global/
out-parameter store escapes, no pointers hidden inside returned
aggregates, no use-after-block detection, no dynamic-array buffer
pointer invalidation across `resize`/`uninit`, and loops are analyzed
once rather than to a fixed point.

## 4.15 Dynamic-array lifecycle model (DynamicArrayMemoryChecker)

A standalone intra-procedural pass over function-local dynamic arrays.
It runs after `BranchPruner`, `InactiveDeclarationPruner`, and
`DanglingPointerChecker`, so only surviving code is analyzed. Each
function starts with fresh array and alias state.

**Lifecycle lattice.** One value per tracked local dynamic array:

- `UNINITED` — no element storage;
- `INITED` — element storage is allocated;
- `MAYBE_INITED` — initialized on some continuing paths, or possibly
  changed by an owner-pointer call.

A separate three-valued `FreedState` (`NEVER` / `MAYBE` / `YES`)
distinguishes use-before-init from use-after-`uninit()`; it does not add
a fourth lifecycle state. Merges produce `MAYBE_INITED` whenever the
paths disagree; `FreedState` merges to `MAYBE` on disagreement.

**Tracked entities.** Function-local variables of type `[T]`, including
the outer value of nested dynamic arrays. Also tracked are owner
`ptr<[T]>` values whose origins are `getptr(localArray)`, pointer copies,
casts, and `getptr($(p))`. Global dynamic arrays themselves, dynamic
array fields, nested elements, element pointers, and
`mview<[T]>`/`view<[T]>` aliases are untracked. `mview`/`view` call
arguments never invalidate array state because those permissions cannot
allocate or free storage.

Origin sets are `ArrayOrigin` values (a set of `VariableSymbol`
identity plus an `UNKNOWN` flag). Exactly one known origin enables a
strong update or definite diagnostic; multiple known or unknown origins
degrade to `MAYBE_INITED` or no diagnostic.

**Transfers.**

- local dynamic array declaration → `UNINITED`, `FreedState.NEVER`;
- `init(n)`, `init_zero(n)`, and `d(n)` → `INITED`; repeated init is
  not diagnosed because an earlier init may have failed with `-1` and a
  retry is valid;
- `resize(n)` requires `INITED` and leaves the array `INITED`;
- `uninit()` requires `INITED`, then stores `UNINITED` +
  `FreedState.YES`; calling it before init is an invalid free, and
  calling it when already uninitialized is a double free;
- element access, `memset`, `memcpy` destination, `memmove`, and every
  read/search/bit intrinsic except `length()` require `INITED`;
- `length()` is always safe, including after `uninit()`;
- `getptr`/`getmview`/`getview` create pointers without changing
  lifecycle state;
- pointer assignments copy origin sets; `null` clears them;
- owner `ptr<[T]>` call arguments mark the target `MAYBE_INITED` and
  escaped. Arrays whose owner pointer is stored into a global or an
  owner-pointer-typed field/element are also escaped. Escaped arrays are
  havocked to `MAYBE_INITED` by later calls or untracked lifecycle
  operations, and are excluded from leak warnings. Global pointer
  origins are forgotten after calls, like address-taken locals.

**Length guards.** Direct comparisons of `d.length()` with a constant
are recognized in either operand order. The zero-asserting paths
(`== 0`, `<= 0`, `< 1`) refine `d` to `UNINITED`; the nonzero-asserting
paths (`!= 0`, `> 0`, `>= 1`, `== k` for `k > 0`) refine it to `INITED`.
Infeasible paths (for example `length() == 0` on an `INITED` array) are
suppressed and do not contribute state. Nonzero constant comparisons
certify only the nonzero side.

**Branches and loops.** `if`/`else` uses the same pre-branch snapshot,
continuing-path merge, no-else fallthrough, infeasible-branch
suppression, and dead-tail handling as the null/dangling checks.
Exploration is path-sensitive to `MAX_BRANCH_DEPTH = 12`; beyond that it
falls back to a path-insensitive merge.

`while` analyzes the body once and replays the update epilogue for
normal and `continue` edges. Exit states are filtered and refined per
path before merging: the zero-trip path is included only when the
condition-false guard is feasible at entry, and each back-edge state is
included only when the condition-false guard is feasible after that
iteration. `break` snapshots are merged separately. If neither a normal
exit nor a `break` is feasible, the loop is treated as
non-falling-through, so its tail is not analyzed. `for` loops receive
the same treatment after desugaring.

**Diagnostics and severity.** Definite use before init, use after
`uninit()`, invalid free, and double free are errors. `MAYBE_INITED`
equivalents are warnings. Repeated `init` is deliberately not diagnosed because
`init`/`init_zero` can fail with `-1` and a retry is valid; the runtime
still aborts on a true double init. Missing explicit `uninit()` at a
return or scope exit is a warning, not a leak error, because the runtime
destructor frees local dynamic arrays. Leak warnings are suppressed for
escaped arrays.

**v1 limitations.** No struct-field or nested-element tracking, no
`mview`/`view` alias tracking for use checks, no interprocedural callee
summaries, no allocation-failure status modeling (`init` is treated as
successful, so repeated init is left to the runtime), no
element-buffer pointer invalidation after `resize`/`uninit`, and no loop
fixed point.

## 5. Build & test quick reference

```bash
# Build the toolchain (ANTLR generation + javac) — required after any .java edit:
uv run python update_toolchain.py
# The exact steps (scripts/franko_tools.py::build_toolchain):
#   java -cp .:lib/antlr-4.13.2-complete.jar org.antlr.v4.Tool -visitor -o build/generated Franko.g4
#   javac --release 25 -cp .:lib/antlr-4.13.2-complete.jar:build/generated \
#         -d build/classes <root *.java> <build/generated/*.java>

# Compile a Franko program to C++:
uv run python compile.py path/to/test.fr          # wraps java -cp build/classes Main ...

# Test suites (same order as CI):
uv run python programs/run_programs.py
uv run python language_tests/run_language_tests.py
uv run python language_tests/run_omax_smoke.py
uv run python library_tests/run_library_tests.py
```

- Success fixtures: compiled binary must exit 0 and stdout must exactly match
  `main.expected` (optional `main.warnings` substring checks and
  `main.forbidden` anti-substring checks against the compiler output).
- Failure fixtures: compiler must exit non-zero; optional `.errors` substrings
  must appear.
- Runtime fixtures: compiler succeeds, binary exits non-zero.
- `-Omax` is intentionally outside the normal asan/opt matrix (out-of-range
  access is undefined behavior there); `language_tests/run_omax_smoke.py`
  compiles and runs representative success fixtures with `-Omax` and pins the
  bare static-index emission in the generated C++.
- `build/` and `.antlr/` are generated and gitignored; never edit them.

## 6. Adding a new phase or check — where things go

| You want to… | Put it in |
|---|---|
| Add syntax | `Franko.g4` + `FrankoASTVisitor` + a node in `ASTNode.java` |
| Remove/rename syntax sugar | `Desugarer` |
| Simplify or remove dead branches/loops | `BranchPruner` |
| Remove unused structs/functions/variables | `InactiveDeclarationPruner` |
| Resolve a new kind of name | `ExpressionAnalyzer` + `SymbolTable` |
| Add a type rule shared by checks | `TypeChecker` |
| Add a language-level legality error | the relevant checker under `LegalityChecker` |
| Track new dataflow facts | `PointerNullStateChecker`/`DanglingPointerChecker`/`DynamicArrayMemoryChecker` (or a new standalone checker invoked from `Main`) |
| Detect returned pointers to frame storage / decayed views | `DanglingPointerChecker` |
| Detect dynamic-array lifecycle misuse | `DynamicArrayMemoryChecker` |
| Emit new C++ constructs | `Cpp14Codegen` (+ `SemanticASTNode`/`SemanticType` as needed) |
| Add a runtime intrinsic | runtime header + `SemanticArrayIntrinsicKind` signature descriptor + `ArrayLowerer`/`ExpressionChecker` validation + `Cpp14Codegen` emission |
| Add an os FFI call | `import/franko/os.fr` + `os.ffi.cpp` block + `OsFfi` signatures |
| Add/adjust a pointer permission rule | the region rule lives in `PointerPermissions`; enforce it in the relevant checker (`ExpressionChecker`/`StatementChecker`) and teach use-site attribution to `PointerPermissionChecker` (§4.9) |
| Change how string literals are represented | `Desugarer` (hoisting, §4.10) + `StringLiteralDecoder` + `GlobalVariableSymbol.literalBytes` + `Cpp14Codegen.emitVarDecl` |
| Change how array literals are represented | `ArrayLiteralLowerer` (§4.13) + `SemanticAnalyzer.hoistArrayLiteralGlobal` + `GlobalVariableSymbol.literalValues` + `SymbolTable.CallArg`/`isArrayLiteralShapeApplicable` + `Cpp14Codegen.emitStaticArrayLiteralInitializer` |
