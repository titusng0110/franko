# Franko Compiler Internals

This document describes the architecture of the Franko compiler: how the phases
connect (call graph), what each Java source file does, and the cross-cutting
design rules that were historically kept as comments inside the source files.

- **Language**: Franko (`.fr`) — a small statically-typed language with fluid
  integer constants, dynamic/static arrays, pointers with null-state checking,
  structs with methods, namespaces, and imports.
- **Compiler**: ~28 hand-written Java files at the repo root, plus an ANTLR4
  grammar (`Franko.g4`). The compiler is a **transpiler to C++14**: it emits one
  C++ translation unit, splices it into a program template, and the resulting
  `.cpp` is compiled with the host C++ toolchain.
- **Runtime**: `include/FrankoRuntime.hpp` (arrays, `franko_safe_cast`),
  optional jemalloc (`third_party/`).

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
   │  PointerNullStateChecker (intra-procedural pointer null dataflow)
   ▼
checked Semantic AST
   │  OsFfi.verifyBridge (if franko/os imported: every used intrinsic must have
   │                     its mangled definition in os.ffi.cpp)
   │  Cpp14Codegen      (emits the C++ body: structs, globals, functions)
   ▼
generated C++ body
   │  template splice   (ProgramTemplate.cpp or ProgramTemplateWithRawCArgs.cpp,
   │                     selected by sema.entryKind(); the body replaces
   │                     __FRANKO_PROGRAM__)
   ▼
output .cpp  (then compiled with the host C++ compiler)
```

Non-fatal warnings (semantic warnings, pointer maybe-null warnings) are printed
between phases; errors abort with a `RuntimeException` carrying the formatted
`DiagnosticBag` contents.

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
    Main --> PointerNullStateChecker
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
| `Main` | `FrankoLexer/Parser`, `FrankoASTVisitor`, `ImportResolver`, `OsFfi`, `Desugarer`, `SemanticAnalyzer`, `LegalityChecker`, `BranchPruner`, `PointerNullStateChecker`, `Cpp14Codegen`, `TargetInfo`, `ASTPrinter`, `SemanticASTPrinter` |
| `ImportResolver` | `FrankoLexer/Parser`, `FrankoASTVisitor`, `ASTNode` |
| `Desugarer` | `ConstExpressionEvaluator`, `StringLiteralDecoder`, `DiagnosticBag`, `ASTNode` |
| `SemanticAnalyzer` | `SymbolTable`, `ExpressionAnalyzer`, `ArrayLowerer`, `ConstExpressionEvaluator`, `OsFfi`, `TargetInfo` |
| `ExpressionAnalyzer` | `SymbolTable`, `ConstExpressionEvaluator`, `ArrayLowerer` (mutually with), `TypeChecker` (static: `isValuePreservingIntegerWidening`) |
| `ArrayLowerer` | `ExpressionAnalyzer`, `SymbolTable` |
| `ConstExpressionEvaluator` | `SymbolTable` (diagnostics only) |
| `LegalityChecker` | `FunctionChecker`, `DeclarationChecker`, `StatementChecker`, `ExpressionChecker` |
| `BranchPruner` | reads/writes `SemanticASTNode` trees only (called by `Main`); no other compiler classes |
| `FunctionChecker` | `StatementChecker`, `ExpressionChecker`, `DeclarationChecker`, `TypeChecker` |
| `StatementChecker` | `DeclarationChecker`, `ExpressionChecker`, `TypeChecker` |
| `DeclarationChecker` | `TypeChecker` (incl. static `fitsBigIntegerToPrimitive`) |
| `ExpressionChecker` | `TypeChecker` |
| `PointerNullStateChecker` | `DiagnosticBag` only (deliberately standalone) |
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
  checker, `PointerNullStateChecker`, `Cpp14Codegen`, `OsFfi`,
  `SemanticASTPrinter`.
- `SemanticType.java` — structural type system (overload resolution, mangling
  keys).
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
`array<T>` / `array<T, size>` / `ptr<T>` and the `?` inferred-size alternative.
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
`BreakNode`/`ContinueNode` with optional const level, `ForNode`, `PrintNode`,
`ReturnNode`); declarations (`FunctionDeclNode`, `ParameterNode`,
`StructDeclNode`, `StructFieldDeclNode`, `GlobalVarDeclNode`, `VarDeclNode`,
`VarDeclInitNode`, `VarDeclArrayInitNode`, `ImportNode`, `NamespaceNode`,
`QualifiedNameNode`); types (`PrimitiveKind` enum, `PrimitiveTypeNode`,
`VoidTypeNode`, `DynamicArrayTypeNode`, `StaticArrayTypeNode`, `PtrTypeNode`,
`StructTypeNode`); expressions (`VarNode`, `IntNode`, `InferredSizeNode`,
`ArrayLiteralNode`, `StringLiteralNode`, `UnaryOpNode`, `BinOpNode`,
`ArrayAccessNode`, `MemberAccessNode`, `CallNode`, `GetPtrNode`, `DerefNode`,
`CastNode`, `NullNode`). The field layout is the ABI between all phases.
Lifecycle rules that used to live as comments here:
- `ForNode`, `IncDecNode`, `VarDeclInitNode`, `VarDeclArrayInitNode`,
  `ImportNode`, `ArrayLiteralNode` are **pre-desugaring sugar**: none may reach
  `SemanticAnalyzer` (the Desugarer asserts this).
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
assignment (or indexed assignments for array/string initializers), `x++/--` →
`x += 1`, `for` → block + `while` (update as while-epilogue so `continue` is
C-like), `?` array sizes → concrete constants via the rectangle rule, string
literals → UTF-8 byte array literals (initializer contexts only), and every
`constExpr` slot → a canonical decimal `IntNode` via
`ConstExpressionEvaluator`. Error-recovery style: it accumulates diagnostics in
a `DiagnosticBag` (placeholder values on failure) and `Main` checks
`hasErrors()`/`formatErrors()` right after the pass. Private control-flow
signal: `InferenceFailure` (a `RuntimeException`) for `?`-size failures,
converted to a diagnostic. Throws if an `ImportNode` ever reaches it.

#### `StringLiteralDecoder`
Static utility: decodes Franko string-literal escapes
(`\\ \" \a \b \f \n \r \t \v`; rejects `\u \x \0` and unknown/trailing escapes
with `RuntimeException`) and converts a `StringLiteralNode` into an
`ArrayLiteralNode` of unsigned UTF-8 bytes (`toUtf8ByteArrayLiteral`), no null
terminator. Called by `Desugarer` (string initializers) and `FrankoASTVisitor`
(import paths).

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
`exprAnalyzer.setArrayLowerer(...)`).

#### `SemanticASTNode`
The lowered AST data model (pure data classes): program/namespace/block
nodes, `SemanticFunctionDeclNode` (symbol + parameter variables + body),
`SemanticStructDeclNode`, `SemanticVarDeclNode` (with folded const
initializer), statements (`SemanticAssignNode` with op text and
`isCompound()/baseOp()`, `SemanticIfNode`, `SemanticWhileNode` with the
desugared-for update epilogue, `SemanticBreakNode` with levels,
`SemanticContinueNode`, `SemanticPrintNode`, `SemanticReturnNode` carrying the
owning `FunctionSymbol`), the array-intrinsic vocabulary
(`SemanticArrayIntrinsicKind`: INIT, INIT_ZERO, RESIZE, UNINIT, MEMSET, MEMCPY,
MEMMOVE, LENGTH — with `returnType()`/`returnsVoid()`;
`SemanticArrayIntrinsicCallNode` with separate receiver + args and full/ranged
memset/memcpy predicates), and expressions (all carrying `type`,
`constantValue: BigInteger`, `isConstant()`; `isLValue()` abstract).
`SemanticCastExprNode` has a `checked` flag: unchecked → `static_cast`, checked
→ runtime-checked `franko_safe_cast`.

#### `SemanticType`
The structural type system: `SemanticPrimitiveType` (8 kinds),
`SemanticVoidType` (return-type only), `SemanticDynamicArrayType`
(`array<E>`), `SemanticStaticArrayType` (`array<E,N>` — size stored as the
canonical folded decimal so `array<int32_t,5+5>` equals `array<int32_t,10>`),
`SemanticPtrType`, `SemanticNullType` (type of `null`; `mangleKey()` throws),
`SemanticStructType` (nominal identity via its `StructSymbol`),
`SemanticFunctionType` (with `signatureKey()` like
`fn(int32_t,ptr<std.X>)->void` for mangling). Two distinct text APIs:
`describe()` (diagnostic text, free to change) and `mangleKey()` (canonical
C++-spelling key embedded in generated names — must stay stable). Structural
`equals`/`hashCode` everywhere.

#### `Symbol`
The symbol hierarchy and mangling policy:
- `VariableSymbol` (+ optional namespace), `ParameterSymbol`,
  `GlobalVariableSymbol` (extra `compileTimeConstantValue` for `os::IS_*` folding).
- `FunctionSymbol` — parameters, namespace, `isBuiltin`; `mangledName()` =
  `FUNC_<name>_HASH_<8 hex>`; `emittedLocalName()` for codegen. Overload
  identity is name + ordered parameter types; **return type is intentionally
  not part of overload identity** (so `f(int32_t)->int32_t` and
  `f(int32_t)->uint32_t` conflict).
- `MethodSymbol` — adds `owner: StructSymbol`; emitted as
  `METH_<name>_OFSTRUCT_<Struct>_HASH_<hex>` free functions taking
  `STRUCT_X* VAR_self`.
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
match exactly, ambiguity is an error); raw-decl→symbol registration maps;
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
calls with a synthesized `getptr(receiver)` self-argument (pointer receivers
need explicit `$(p).method(...)`); qualified `ns::name` references resolve
strictly against declared namespaces, and `os::IS_*` constants fold into
integer literals; `inferBinaryResultType` implements the fluid-constant rule
(when one operand is a folded constant, the result takes the other side's
type); same-type casts are returned unchanged (no cast node emitted);
`cast<T>()` chooses plain `static_cast` vs runtime-checked `franko_safe_cast`
via `TypeChecker.isValuePreservingIntegerWidening` (the single analyzer→checker
edge). Rejects leaked `ArrayLiteralNode` as a "Desugarer bug".

#### `ArrayLowerer`
Frontend lowering of array-intrinsic call syntax to
`SemanticArrayIntrinsicCallNode`s during semantic analysis: named member
intrinsics (`xs.init(n)`, `xs.init_zero()`, `xs.resize(n)`, `xs.uninit()`,
`xs.memset(v)`/`xs.memset(s,n,v)`, `xs.memcpy(src)`/`xs.memcpy(src,sr,dst,n)`,
`xs.memmove(src,n)`, `xs.length()`) plus the dynamic-array init shorthand
`xs(size)` → `xs.init(size)`. Type-directed dispatch: a struct-typed receiver
is a method call, never an intrinsic, even when a method name coincides with an
intrinsic name; `shouldSuppressCallDiagnostic` avoids misleading "no matching
overload" errors when fallthrough would be noise. The shorthand disambiguator
"peeks" at member-call receivers before full callee analysis to avoid spurious
errors alongside real method resolution. Mutually coupled with
`ExpressionAnalyzer` (analyzes intrinsic arguments through it).

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
assignable value types), method `self` shape (`ptr<X> self` must be the first
parameter and no other parameter may be named `self`), return statement rules
(`return;` only in void functions; `return expr;` must be assignable to the
return type), and **presence-based missing-return detection**: a non-void
function must syntactically contain a return somewhere in its body (recursing
through blocks/ifs/whiles; the desugared for-update epilogue runs inside the
loop so returns there count). It does not prove full control-flow coverage.
Intercepts `SemanticReturnNode` itself and delegates other statements to
`StatementChecker`.

#### `DeclarationChecker`
Declaration-level legality: declared types must be legal Franko types
(primitive, array, ptr, struct; void and arrays-as-values rejected),
static array sizes must be constant, positive, and fit `uint32_t` (zero is
rejected — the backend emits `T data[N]`), struct field graphs must not have
infinite-size **by-value** cycles (cycle detection via `StructWalkState`;
the by-value path is intentionally reset when descending through dynamic array
elements because those live behind a pointer), method names must not collide
with field names, and folded const initializers/field defaults must fit their
primitive type.

#### `StatementChecker`
Statement-level legality: assignment targets must be storage-backed lvalues
(storage semantics: variables, derefs, and array/member accesses rooted at
storage — stronger than `SemanticExprNode.isLValue()`), non-void LHS, exact/
fitting RHS per `TypeChecker.ensureAssignable`; compound-assign rules with
constant-zero-divisor rejection; `if`/`while` conditions must be non-void
integral expressions; `print` argument validity; and the void-value-context
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
revalidation (constants must fit, non-constants match exactly, no void/array
arguments), array intrinsic argument rules, constant static-array index bounds
(this is the rule rejecting over-long initializer lists), cast constant
range checks, getptr/deref legality, and void-in-value-context rules. Carries
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
`ExpressionAnalyzer`), literal parsing (`parseIntegerLiteral`:
0b/0x/decimal), and memset/memcpy element rules (`isMemsetable`,
`isMemcpyable` — recursive with path-scoped cycle detection so diamond-shaped
field graphs stay fine while by-value cycles terminate). It does not traverse
the AST.

#### `PointerNullStateChecker`
Intra-procedural pointer null-state dataflow, run after legality checking and
deliberately independent of the checker cluster (only `DiagnosticBag`). One
lattice fact per pointer-typed variable: `POINTING` / `NULL` / `MAYBE_NULL`,
initialized to `MAYBE_NULL` at function entry (except method `self` and
`main`'s args param, seeded `POINTING`). Transfer rules: pointer decls are
guaranteed NULL-initialized; `p = getptr(x)` → POINTING (the only
certification); `p = null` → NULL; `p = q` copies state; anything else →
MAYBE_NULL. Guard refinement: `p == null` / `p != null` conditions refine
states on each path; exploration is path-sensitive up to
`MAX_BRANCH_DEPTH = 10`, with an insensitive fallback beyond the cap.
Loops track break/continue jump states via snapshot slots (`LoopFrame`,
preallocated `PointerState[][]` store), merge back-edge states, and compute
zero-trip exit states. Reports deref of definitely-NULL as error and
maybe-null as warning (each pointer warns at most once per check site).

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
- **Atoms**: effect-free `uint8_t`-typed leaves — comparisons
  (`== != < > <= >=`) over pure operands, and `uint8_t`-typed variable, member,
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
- **Effect on later phases**: `PointerNullStateChecker` and codegen see the
  pruned tree, so null-state warnings and code are never produced for dead
  branches, and dead loops vanish from the emitted C++.

### 3.5 Backend

#### `Cpp14Codegen`
The C++14 backend: walks the checked Semantic AST and emits the translation
unit body that replaces `__FRANKO_PROGRAM__`. It performs **no** legality
checking — it trusts every prior phase. Emission order is dependency-safe:
struct forward declarations → struct definitions topologically sorted by
by-value field dependency (with NSDMI initializers for folded field defaults,
`= nullptr` for pointer fields) → global variables → function prototypes →
function definitions, all under mangled names and inside `NS_<segment>`
namespaces where applicable. Statement lowering: `if` with constant-dead-
branch pruning, `while` with goto-based `L_brk_<id>`/`L_cont_<id>` labels plus
the desugared for-update epilogue (continue label placed *before* the
epilogue so `continue` runs the update and `break` skips it), `std::cout`
print. Expression lowering is structural (`ptr<T>`→`T*`, `getptr(x)`→`(&x)`,
`$(p)`→`(*p)`, arrays → `Franko_Dynamic_Array<T>` /
`Franko_Static_Array<T,N>`, pre-folded constants emitted directly, contextual
`static_cast` via `emitExprAsType` to preserve Franko's constant typing and
already-chosen overloads, `franko_safe_cast` for checked casts). A
`checkEmittedNameCollisions` guard catches 32-bit hash collisions between
distinct signatures. When `franko/os` was imported, `generate()` prepends the
`OsFfi.buildBridge` splice (common block + one block per used intrinsic) and
suppresses the os.fr stub bodies; each intrinsic call site is also checked
with `OsFfi.checkTargetAllowed`.

#### `Mangler`
Stateless FNV-1a 32-bit hash utility: `hashHex(key)` → 8 lowercase hex digits
over the UTF-8 bytes of the canonical signature key. Deterministic across
runs/JVMs/platforms. Called only from `Symbol` (`FunctionSymbol.mangledName`,
`MethodSymbol.mangledName`), but its outputs are pinned in three places:
`OsFfi.expectedEmittedName`, both program templates
(`FUNC_main_HASH_d8625720` / `FUNC_main_HASH_5708d149`), and `Main`'s
entry-point sanity check.

#### `ProgramTemplate.cpp` / `ProgramTemplateWithRawCArgs.cpp`
The fixed program skeleton codegen deliberately does not emit: the
`#include "FrankoRuntime.hpp"`, the `__FRANKO_PROGRAM__` insertion point, and
the real C `main` wrapper that calls the mangled Franko `main`. The no-args
template calls `FUNC_main_HASH_d8625720()` directly; the raw-C-args template
marshals `argc`/`argv` byte-for-byte (no null terminators) into
`Franko_Dynamic_Array<Franko_Dynamic_Array<uint8_t>>` and calls
`FUNC_main_HASH_5708d149(&frankoArgs)`. `Main` sanity-checks that the chosen
template contains the expected entry call — a cheap guard against
template/mangling drift, mirroring `OsFfi.verifyBridge`. An explicit
`-t <template>` skips the check.

#### `SemanticASTPrinter` / `ASTPrinter`
Debug printers (`--verbose` only): `ASTPrinter` for the raw AST at three
checkpoints, `SemanticASTPrinter` for the final semantic AST (types,
constant values, lvalue flags, mangled names, symbol identity hashes).

### 3.6 Support

#### `DiagnosticBag`
Phase-agnostic error/warning accumulator with a configurable header and
numbered `formatErrors()`. Knows nothing about symbols, scopes, ASTs, or
types. Used by `SymbolTable` (semantic phase), `Desugarer`, `Main` (legality
and pointer-state bags), and every checker.

#### `Main`
The driver: CLI parsing (`-o`, `-t`, `-p`, `-v`, `--target`), the phase
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
- `T x = {a,b,c}` and `T x = [a,b,c]` → `x` decl plus `x[0]=a; x[1]=b; x[2]=c;`
  (recursive for nested arrays; strings become UTF-8 byte assignments).
- `array<T, ?>` size inference: initializer must be a perfect static rectangle
  (no partial fill, no jagged); inference failure is an error, with size-1
  placeholder recovery.
- Every `constExpr` slot (array sizes, global const initializers, field
  defaults, cast constants, break levels) is folded here to a canonical
  decimal `IntNode` — later phases see only integers.
- String literals desugar to UTF-8 byte array literals only in initializer
  contexts; elsewhere the analyzer reports a proper diagnostic rather than an
  internal error.

### 4.2 Fluid integer constants
Integer literals and folded constant expressions are **fluid**: a constant is
checked by its `BigInteger` value against the target type's range, not by the
type it would have in C++. Consequences:
- One operand of an arithmetic/comparison/compound-assign expression may be a
  fitting constant; then the result takes the other operand's type
  (`inferBinaryResultType`).
- Overflow of a constant in the target range is an error; runtime values are
  exact-type-checked.
- Narrowing casts of constants are range-checked at compile time
  (`ExpressionChecker.visitCast`); widening casts are value-preserving and emit
  plain `static_cast`, others emit `franko_safe_cast`.

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

### 4.5 Pointer null-state model (PointerNullStateChecker)
- `getptr(x)` is the only certification of POINTING; every other value source
  is MAYBE_NULL unless it is `null` (NULL) or a pointer copy.
- Deref of definitely-NULL = error; maybe-null deref = warning.
- Guards (`p == null`/`p != null`) refine per-path; branch exploration is
  path-sensitive to depth 10, then falls back to insensitive checking.
- Loops snapshot/merge states so breaks, continues, and zero-trip exits all
  get correct entry states.

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
  a truth table of at most 10 effect-free `uint8_t`-typed atoms; `==`/`!=`
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
uv run python library_tests/run_library_tests.py
```

- Success fixtures: compiled binary must exit 0 and stdout must exactly match
  `main.expected` (optional `main.warnings` substring checks).
- Failure fixtures: compiler must exit non-zero; optional `.errors` substrings
  must appear.
- Runtime fixtures: compiler succeeds, binary exits non-zero.
- `build/` and `.antlr/` are generated and gitignored; never edit them.

## 6. Adding a new phase or check — where things go

| You want to… | Put it in |
|---|---|
| Add syntax | `Franko.g4` + `FrankoASTVisitor` + a node in `ASTNode.java` |
| Remove/rename syntax sugar | `Desugarer` |
| Simplify or remove dead branches/loops | `BranchPruner` |
| Resolve a new kind of name | `ExpressionAnalyzer` + `SymbolTable` |
| Add a type rule shared by checks | `TypeChecker` |
| Add a language-level legality error | the relevant checker under `LegalityChecker` |
| Track new dataflow facts | `PointerNullStateChecker` (or a new standalone checker invoked from `Main`) |
| Emit new C++ constructs | `Cpp14Codegen` (+ `SemanticASTNode`/`SemanticType` as needed) |
| Add a runtime intrinsic | runtime header + `ArrayLowerer`/`SemanticArrayIntrinsicKind` + `ExpressionChecker` validation + `Cpp14Codegen` emission |
| Add an os FFI call | `import/franko/os.fr` + `os.ffi.cpp` block + `OsFfi` signatures |
