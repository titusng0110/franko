grammar Franko;

@lexer::members {
    private int groupingDepth = 0;
}

// ---------- PARSER ----------

program
    : separators* (topLevelItem separators*)* EOF
    ;

topLevelItem
    : structDecl
    | functionDecl
    | globalVarDecl
    | importDecl
    | namespaceDecl
    ;

// ---------- NAMESPACES ----------
//
// Namespace syntax:
//
//   namespace util {
//       struct Point { int32_t x }
//       int32_t offset = 100
//       func doubleIt(int32_t v) -> int32_t { ... }
//   }
//
// A namespace body may contain only:
//
//   - struct declarations,
//   - global variable declarations,
//   - function declarations,
//   - nested namespace declarations.
//
// Imports, executable statements, and statements of any other kind are
// not allowed inside a namespace body; namespaceItem makes them syntax
// errors rather than semantic ones.
//
// Nested namespaces may be written in two equivalent forms:
//
//   namespace a::b {
//       ...
//   }
//
//   namespace a {
//       namespace b {
//           ...
//       }
//   }
//
// Both forms declare the same namespace, whose full path is "a::b".
// A path header (containing '::') is only valid at the top level: a
// namespace declared inside a namespace body must use a single name,
// and its full path is the enclosing path plus that name. Path headers
// nested inside a namespace body are rejected by the semantic
// analyzer.
//
// The same namespace may be declared more than once, in the same file
// or across imported files, in either form; its contents merge into
// one namespace. Duplicate symbols inside the merged namespace are
// rejected by the semantic analyzer exactly like global duplicates.
//
// Members are referenced with the '::' qualifier:
//
//   util::Point p
//   print(util::offset)
//   print(util::doubleIt(5))
//   a::b::helper(x)
//
// Inside a namespace, an unqualified name resolves in that namespace
// first, then falls back to the global namespace; enclosing
// namespaces are not searched. A qualified name always resolves
// strictly inside the named namespace path, starting from the top
// level; there is no fallback to the global namespace and no
// relative resolution through the current namespace.
namespaceDecl
    : NAMESPACE IDENTIFIER (COLONCOLON IDENTIFIER)* separators* LBRACE separators* (namespaceItem separators*)* RBRACE
    ;

namespaceItem
    : structDecl
    | functionDecl
    | globalVarDecl
    | namespaceDecl
    ;

// A qualified name requires at least one '::', so it can never be
// ambiguous with a plain IDENTIFIER reference.
qualifiedName
    : IDENTIFIER (COLONCOLON IDENTIFIER)+
    ;

// ---------- IMPORTS ----------
//
// Import syntax:
//
//   import "abc"
//   import "lib/util"
//   import "lib/util.fr"
//   import "./lib/util"
//   import "../shared/util"
//   import "my-lib/foo bar"
//
// The import path must be a string literal. A bare name such as
// `import util` is a syntax error.
//
// Path characters are unrestricted inside the string literal: hyphens
// and spaces are ordinary characters, so `import "my-lib/foo bar"`
// names the file "foo bar.fr" in the "my-lib" directory. Paths always
// use the POSIX '/' separator.
//
// Path resolution (performed by ImportResolver):
//
//   - the string is escape-decoded like any other string literal;
//
//   - './' is NOT prepended; a prefix-free path is looked up in two
//     places, in order:
//
//       1. the import search directory supplied to the compiler with
//          -p (default: ./import, relative to the compiler directory),
//       2. the directory of the file containing the import statement.
//
//     The first existing match wins, so a module supplied through -p
//     shadows a same-named file next to the importing source file.
//
//   - paths with an explicit './', '../', or '/' prefix skip the
//     search directory and resolve relative to the importing file
//     (or as an absolute path);
//
//   - '.fr' is appended when the file name has no extension, and a
//     present non-.fr extension is rejected.
//
// Import paths always use the POSIX form with '/' separators; on
// Windows, a '/'-prefixed string literal path is translated to the
// current drive when it reaches the filesystem (see ImportResolver).
//
// The import statement is a top-level item so it may appear anywhere at
// global scope, mixed freely with struct, function, and global variable
// declarations.
importDecl
    : IMPORT STRING_LITERAL
    ;

// ---------- STRUCTS ----------
//
// Simple struct syntax:
//
//   struct X {
//       int32_t num = 0
//       int64_t longnum = 1 + 2
//   }
//
// A struct body contains field declarations and method declarations.
// Methods are function declarations whose first parameter must be
// literally `ptr<X> self`, where X is the enclosing struct (see
// Methods). No assignments, no generics.
//
// Field initializers, if present, must be constExpr.
//
// Separators between members are optional, matching block syntax:
// a trailing separator before '}' is not required.
//
// Valid:
//   struct X {
//       int32_t a
//       int64_t b = 123
//       array<int32_t, 4> xs
//       func increment(ptr<X> self) -> void { ... }
//   }
//
// Invalid:
//   struct X {
//       int32_t a = f()
//       a = 1
//       func bad(int32_t self) -> void {}
//   }
//
structDecl
    : STRUCT IDENTIFIER separators* LBRACE separators* (structMember separators*)* RBRACE
    ;

structMember
    : structFieldDecl
    | functionDecl
    ;

structFieldDecl
    : type IDENTIFIER constDeclSuffix?
    ;

// ---------- GLOBAL VARIABLES ----------
//
// Global variables may have optional constexpr-style initialization.
//
// Valid:
//   int32_t g
//   int32_t g = 1 + 2
//   uint64_t mask = 1 << 8
//   X globalStruct
//   ptr<X> p = null
//   view<array<byte, 5>> s = "hello"
//   ptr<array<byte, 5>> q = getptr(otherGlobal)
//
// Invalid:
//   int32_t g = f()
//   int32_t g = other
//   int32_t g = arr[0]
//
// A pointer-typed global may also be initialized with a statically
// representable address: getptr/getmview/getview over a global variable
// (string literals desugar to getview over a hoisted global).
globalVarDecl
    : type IDENTIFIER globalVarInitializer?
    ;

globalVarInitializer
    : ASSIGN globalVarInitValue
    ;

globalVarInitValue
    : constExpr
    | stringLiteral
    | getPtrExpr
    | getViewExpr
    | getMViewExpr
    | nullLiteral
    ;

// ---------- CONST DECLARATION INITIALIZERS ----------
//
// Reusable declaration initializer for places where initialization
// must be compile-time evaluable.
//
// Used by:
//   - struct field declarations
//   - global variable declarations
//
// This intentionally accepts constExpr, not expr.
constDeclSuffix
    : ASSIGN constDeclInitializer
    ;

constDeclInitializer
    : constExpr
    ;

// ---------- FUNCTIONS ----------
//
// Function syntax:
//
//   func name(type param, type param) -> returnType {
//       return expr;
//   }
//
// returnType may be:
//   - any ordinary Franko type allowed as a function return type semantically
//   - void
//
// void is intentionally NOT part of the normal type rule.
// This prevents declarations such as:
//   void x;
//   array<void> xs;
//   ptr<void> p.
//
functionDecl
    : FUNC IDENTIFIER LPAREN parameterList? RPAREN ARROW returnType separators* block
    ;

parameterList
    : parameter (COMMA parameter)*
    ;

parameter
    : type (IDENTIFIER | SELF)
    ;

returnType
    : type
    | VOID
    ;

// ---------- STATEMENTS ----------

statement
    : simpleStmt   # SimpleStatement
    | ifStmt       # IfStatement
    | whileStmt    # WhileStatement
    | forStmt      # ForStatement
    | breakStmt    # BreakStatement
    | continueStmt # ContinueStatement
    | block        # BlockStatement
    ;

// Reusable separator rule
separators
    : (SEMI | NEWLINE)+
    ;

// ---------- BLOCK ----------

block
    : LBRACE separators* (statement separators*)* RBRACE
    ;

// ---------- CONTROL FLOW ----------

ifStmt
    : IF LPAREN expr RPAREN separators* statement
      (separators* ELSE separators* statement)?
    ;

whileStmt
    : WHILE LPAREN expr RPAREN separators* statement
    ;

// ---------- BREAK / CONTINUE ----------
//
// break exits one or more enclosing loops. An optional integer constant
// expression N selects how many nested loops to exit (strictly > 0 and
// <= the enclosing loop depth); it defaults to 1, so `break` and
// `break 1` are equivalent.
//
// continue targets the innermost enclosing loop only and always runs
// that loop's final body statement(s) (the update of a desugared for
// loop) before re-testing the condition. `continue N` is not valid; an
// optional level is parsed so it can be rejected with a clear semantic
// error rather than silently desugaring into two statements.
//
breakStmt
    : BREAK constExpr?
    ;

continueStmt
    : CONTINUE constExpr?
    ;

// ---------- FOR ----------
//
// C-style counted loop:
//
//   for (init; condition; update) body
//
// Both init and update are optional simple statements:
//
//   for (int32_t i = 0; i < n; i = i + 1) { ... }
//   for (; i < n; i = i + 1) { ... }
//   for (i = 0; i < n;) { ... }
//
// The condition is required.
//
// The header is enclosed in parentheses, so the lexer suppresses
// newlines inside it and SEMI is the only separator.
//
// Desugaring lowers this to an init block plus a while loop whose body
// is followed by the update statements. See the Desugarer.
//
forStmt
    : FOR LPAREN forInit? SEMI expr SEMI forUpdate? RPAREN separators* statement
    ;

forInit
    : simpleStmt
    ;

forUpdate
    : simpleStmt
    ;

// ---------- SIMPLE STATEMENTS ----------
//
// NOTE:
// - standalone a(10) parses as a normal expression statement.
// - x.memset(...), x.memcpy(...), x.uninit() are parsed as
//   ordinary member-call expressions.
// - function calls also parse as expression statements.
// - compound assignments (x += 1) and increments (x++) are
//   dedicated statement forms; they are not expressions.
//
// Array intrinsics such as:
//   arr.memset(0)
//   arr.memcpy(other)
//   arr.uninit()
// should be recognized semantically from normal member-call syntax,
// not hardcoded in the lexer/parser.
simpleStmt
    : varDecl
    | returnStmt
    | assignStmt
    | compoundAssignStmt
    | incDecStmt
    | printStmt
    | exprStmt
    ;

// ---------- VARIABLE DECLARATION ----------
//
// Declaration initializer syntax supports ordinary expression initializers
// and array initializer-list syntax.
//
// Examples:
//   int32_t x = 1 + 2;
//   array<int32_t, 3> xs = [1, 2, 3];
//   array<int32_t> ys = [1, 2, 3];
//   array<array<byte, 12>, 12> grid;
//
// Important:
//   T x = [ ... ];
//
// is declaration sugar for:
//
//   T x;
//   x = [ ... ];
//
// The initializer list itself does not allocate, resize, or initialize
// dynamic array storage. It only lowers to indexed assignments.
//
varDecl
    : type IDENTIFIER declSuffix?
    ;

declSuffix
    : ASSIGN declInitializer # DeclAssignInit
    | LPAREN expr RPAREN     # DeclArrayInit
    ;

declInitializer
    : arrayLiteral
    | expr
    ;

// ---------- RETURN ----------
//
// Semantic checker decides:
//   - return expr; is invalid in void functions
//   - return; is invalid in non-void functions
//   - return outside a function is invalid
returnStmt
    : RETURN expr?
    ;

// ---------- ASSIGNMENT ----------
//
// Assignment supports ordinary expression assignment and array initializer-list
// assignment.
//
// Array initializer assignment:
//
//   arr = [a, b, c];
//
// is not whole-array assignment. It is syntactic sugar for:
//
//   arr[0] = a;
//   arr[1] = b;
//   arr[2] = c;
//
// The initializer list does not allocate, resize, or initialize dynamic arrays.
// Dynamic arrays must already be initialized before the generated indexed
// assignments are valid.
//
// ---------- COMPOUND ASSIGNMENT ----------
//
// Compound assignment operators:
//
//   += -= *= /= %=
//
// The target is an ordinary lvalue and the right-hand side is an ordinary
// expr (array initializer lists are NOT accepted on this side).
//
//   x += 1;
//   arr[i] *= 2;
//   $(p) -= 3;
//
// Semantically `x op= v` is exactly `x = x op v`, except the target is
// evaluated only once: the backend emits the C++ compound assignment
// directly. `x++` and `x--` are equivalent to `x += 1` and `x -= 1`
// (see incDecStmt and the Desugarer).
assignStmt
    : lvalue ASSIGN assignmentInitializer
    ;

compoundAssignStmt
    : lvalue compoundAssignOp expr
    ;

compoundAssignOp
    : PLUS_ASSIGN
    | MINUS_ASSIGN
    | STAR_ASSIGN
    | SLASH_ASSIGN
    | PERCENT_ASSIGN
    ;

// ---------- INCREMENT / DECREMENT ----------
//
// Postfix statement forms only:
//
//   x++;
//   x--;
//
// Both are sugar for `x += 1` and `x -= 1`, lowered by the Desugarer.
// Prefix forms (`++x`) and value uses (`print(x++)`) are syntax errors:
// these operators exist only as statements.
incDecStmt
    : lvalue (PLUSPLUS | MINUSMINUS)
    ;

assignmentInitializer
    : arrayLiteral
    | expr
    ;

// ---------- PRINT ----------
//
// Keeping print as a dedicated statement for now.
//
// `obj.print()` does NOT work: `print` is a lexer keyword, so although a
// `.print()` member suffix parses syntactically, the semantic analyzer
// rejects ordinary member calls on non-struct receivers (and `print` can
// never be a struct method name a program can declare, since method names
// are IDENTIFIERs). Use the print(...)
// statement form instead.
//
// If you later want global user-defined function print(...),
// you should consider removing this statement form too and
// treating print as a builtin or normal function resolved semantically.
printStmt
    : PRINT LPAREN exprList? RPAREN
    ;

exprList
    : expr (COMMA expr)*
    ;

// ---------- LVALUES ----------
//
// Supports struct field assignment:
//   x
//   arr[i]
//   obj.field
//   obj.field[i]
//   $(p)
//   $(p).field
//   $(p)[i]
//   ($(p)).field[i]
//
// Intentionally does NOT allow function-call results as lvalues.
lvalue
    : lvalueAtom lvalueSuffix*
    ;

lvalueAtom
    : IDENTIFIER
    | SELF
    | qualifiedName
    | derefExpr
    | LPAREN lvalue RPAREN
    ;

lvalueSuffix
    : indexSuffix
    | memberSuffix
    ;

// ---------- EXPRESSION STATEMENT ----------

exprStmt
    : expr
    ;

// ---------- EXPRESSIONS ----------
//
// Precedence lowest to highest:
//   ||
//   &&
//   |
//   ^
//   &
//   == !=
//   < <= > >=
//   << >>
//   + -
//   * / %
//   unary (- !)
//   postfix [] () .
//
// Array initializer lists are intentionally NOT expressions.
// They are assignment/declaration initializer forms only.

expr
    : logicalOrExpr
    ;

logicalOrExpr
    : logicalAndExpr (OROR logicalAndExpr)*
    ;

logicalAndExpr
    : bitwiseOrExpr (ANDAND bitwiseOrExpr)*
    ;

bitwiseOrExpr
    : bitwiseXorExpr (PIPE bitwiseXorExpr)*
    ;

bitwiseXorExpr
    : bitwiseAndExpr (CARET bitwiseAndExpr)*
    ;

bitwiseAndExpr
    : equalityExpr (AMP equalityExpr)*
    ;

equalityExpr
    : relationalExpr ((EQ | NEQ) relationalExpr)*
    ;

relationalExpr
    : shiftExpr ((LE | GE | LT | GT) shiftExpr)*
    ;

shiftExpr
    : additiveExpr ((LT LT | GT GT) additiveExpr)*
    ;

additiveExpr
    : multiplicativeExpr ((PLUS | MINUS) multiplicativeExpr)*
    ;

multiplicativeExpr
    : unaryExpr ((STAR | SLASH | PERCENT) unaryExpr)*
    ;

unaryExpr
    : MINUS unaryExpr   # UnaryMinus
    | BANG unaryExpr    # LogicalNot
    | postfixExpr       # PostfixExprOnly
    ;

// ---------- POSTFIX / MEMBER / CALL / INDEX ----------
//
// Supports:
//   a[i]
//   foo(x)
//   foo(x, y)
//   obj.field
//   obj.method()
//   obj.method(x)[i]
//   $(p).field
//   $(p).memset(0)
//   (foo(x)).bar(y)[z]
//
// Does NOT support direct postfixing of array initializer lists:
//   [1,2,3][0]          invalid
//   [[0,0],[1,1]][0][1] invalid
postfixExpr
    : primary postfixSuffix*
    ;

primary
    : integerLiteral
    | stringLiteral
    | nullLiteral
    | IDENTIFIER
    | SELF
    | qualifiedName
    | getPtrExpr
    | getViewExpr
    | getMViewExpr
    | derefExpr
    | castExpr
    | unsafeCastExpr
    | LPAREN expr RPAREN
    ;

postfixSuffix
    : indexSuffix
    | callSuffix
    | memberSuffix
    ;

indexSuffix
    : LBRACK expr RBRACK
    ;

callSuffix
    : LPAREN argumentList? RPAREN
    ;

argumentList
    : expr (COMMA expr)*
    ;

memberSuffix
    : DOT memberName
    ;

// ---------- MEMBER NAMES ----------
//
// Important:
// Some words such as 'print', 'if', 'while', and type names are lexer
// keywords in this grammar, so they do NOT tokenize as IDENTIFIER.
//
// If such words should be syntactically allowed after '.', the parser
// must explicitly allow those keyword tokens here.
//
// Ordinary words such as 'memset', 'memcpy', and 'uninit' are no longer
// lexer keywords. They tokenize as IDENTIFIER and therefore work through
// the IDENTIFIER alternative below.
memberName
    : IDENTIFIER
    | SELF
    | FUNC
    | RETURN
    | VOID
    | STRUCT
    | PRINT
    | IF
    | ELSE
    | WHILE
    | FOR
    | BREAK
    | CONTINUE
    | PTR
    | MVIEW
    | VIEW
    | GETPTR
    | GETMVIEW
    | GETVIEW
    | UNSAFE_CAST
    | INT8_T
    | INT16_T
    | INT32_T
    | INT64_T
    | UINT8_T
    | UINT16_T
    | UINT32_T
    | UINT64_T
    | ARRAY
    ;

// ---------- CONSTANT EXPRESSIONS ----------
//
// Used for:
//   - static array size expressions
//   - global variable initializers
//   - struct field default initializers
//
// A static array size is either an explicit constant expression or an
// inferred-size marker `?`:
//
//   array<int32_t, 10>   explicit size
//   array<int32_t, ?>    size inferred from a declaration initializer
//
// `?` is only meaningful in a declaration with an array-literal or
// string-literal initializer; the desugarer rejects it everywhere else.
//
// A constExpr is syntactically restricted to expressions that can be
// computed from the expression alone:
//
//   - integer literals
//   - unary -
//   - logical !
//   - supported binary integer operators
//   - parentheses
//
// It intentionally excludes:
//
//   - identifiers
//   - function calls
//   - array indexing
//   - member access
//   - getptr(...)
//   - $(...)
//   - array initializer lists
//   - string literals
//
// The desugarer evaluates every constExpr slot into a canonical
// decimal literal before semantic analysis, and rejects
// non-computable expressions such as:
//   1 / 0
//   1 << -1
//
// Semantic analysis then performs only contextual validation:
// positivity, uint32_t range, and fit against the declared type.

constExpr
    : constLogicalOrExpr
    ;

// A single static array size: either an explicit constant expression
// or an inferred-size `?` marker.
arraySize
    : constExpr  # ExplicitArraySize
    | QUESTION   # InferredArraySize
    ;

constLogicalOrExpr
    : constLogicalAndExpr (OROR constLogicalAndExpr)*
    ;

constLogicalAndExpr
    : constBitwiseOrExpr (ANDAND constBitwiseOrExpr)*
    ;

constBitwiseOrExpr
    : constBitwiseXorExpr (PIPE constBitwiseXorExpr)*
    ;

constBitwiseXorExpr
    : constBitwiseAndExpr (CARET constBitwiseAndExpr)*
    ;

constBitwiseAndExpr
    : constEqualityExpr (AMP constEqualityExpr)*
    ;

constEqualityExpr
    : constRelationalExpr ((EQ | NEQ) constRelationalExpr)*
    ;

constRelationalExpr
    : constShiftExpr ((LE | GE | LT | GT) constShiftExpr)*
    ;

constShiftExpr
    : constAdditiveExpr ((LT LT | GT GT) constAdditiveExpr)*
    ;

constAdditiveExpr
    : constMultiplicativeExpr ((PLUS | MINUS) constMultiplicativeExpr)*
    ;

constMultiplicativeExpr
    : constUnaryExpr ((STAR | SLASH | PERCENT) constUnaryExpr)*
    ;

constUnaryExpr
    : MINUS constUnaryExpr
    | BANG constUnaryExpr
    | constPrimary
    ;

constPrimary
    : integerLiteral
    | LPAREN constExpr RPAREN
    ;

// ---------- ARRAY INITIALIZER LISTS ----------
//
// Array initializer lists are not normal expressions.
//
// They may appear in assignment initializer contexts:
//
//   xs = [1, 2, 3];
//
// and declaration initializer contexts:
//
//   array<int32_t, 3> xs = [1, 2, 3];
//
// Declaration form:
//
//   T x = [ ... ];
//
// first desugars to:
//
//   T x;
//   x = [ ... ];
//
// Then:
//
//   x = [a, b, c];
//
// desugars to:
//
//   x[0] = a;
//   x[1] = b;
//   x[2] = c;
//
// Nested initializer lists recursively lower to indexed assignments:
//
//   matrix = [[1, 2], [3, 4]];
//
// becomes:
//
//   matrix[0][0] = 1;
//   matrix[0][1] = 2;
//   matrix[1][0] = 3;
//   matrix[1][1] = 4;
//
// Scalar elements are ordinary expressions, not constExpr.
//
// Invalid expression examples:
//   [1, 2, 3];
//   print([1, 2, 3]);
//   f([1, 2, 3]);
//   [1, 2, 3][0];
//
// Also invalid as global or struct field initializers:
//   int32_t g = [1, 2, 3]
//   struct X { array<int32_t, 3> xs = [1, 2, 3] }
//
arrayLiteral
    : LBRACK arrayLiteralElements? RBRACK
    ;

arrayLiteralElements
    : arrayLiteralElement (COMMA arrayLiteralElement)*
    ;

arrayLiteralElement
    : arrayLiteral
    | expr
    ;

getPtrExpr
    : GETPTR LPAREN lvalue RPAREN
    ;

getViewExpr
    : GETVIEW LPAREN lvalue RPAREN
    ;

getMViewExpr
    : GETMVIEW LPAREN lvalue RPAREN
    ;

// Dereference: `$(X)`. The operand is a full expr; the result is an
// lvalue of the pointee type (so `$(p).field`, `$(p)[i]`, and
// `$(p) = v` are all valid). Always parenthesized: `$X` is a syntax
// error. Chains nest: `$($(pp))`.
derefExpr
    : DEREF LPAREN expr RPAREN
    ;

// Explicit conversion. Two forms:
//
//   cast<T>(expr)
//     - integer target: value-preserving widening only (emits plain
//       static_cast); a conversion that can lose data must be written
//       with unsafe_cast instead
//     - pointer target (ptr/mview/view): explicit permission downgrade
//       only; casting upwards is invalid
//
//   unsafe_cast<T>(expr)
//     - arbitrary integer conversion; range-checked at runtime through
//       franko_unsafe_cast (and range-checked at compile time when the
//       operand is a constant). The target is restricted to the eight
//       primitive integer types at parse time.
castExpr
    : CAST LT type GT LPAREN expr RPAREN
    ;

unsafeCastExpr
    : UNSAFE_CAST LT primitiveType GT LPAREN expr RPAREN
    ;

primitiveType
    : INT8_T
    | INT16_T
    | INT32_T
    | INT64_T
    | UINT8_T
    | UINT16_T
    | UINT32_T
    | UINT64_T
    ;

// ---------- LITERALS ----------

integerLiteral
    : INT_LITERAL
    | BIN_LITERAL
    | HEX_LITERAL
    ;

stringLiteral
    : STRING_LITERAL
    ;

nullLiteral
    : NULL
    ;

// ---------- TYPES ----------
//
// Static array sizes accept constExpr.
//
// Examples:
//   array<int, 10>
//   array<int, 1 + 1>
//   array<int, 2 * 5>
//   array<array<int, 1 + 1>, 2 + 2>
//
// Static arrays nest, so multidimensional arrays are written as
// nested static array types:
//
//   array<array<byte, 20>, 12>
//
// Access order follows the nesting:
//
//   array<array<byte, C>, R> grid
//   grid[r][c]
//
// Semantic checker should later require that each size expression is:
//   - a valid compile-time integer constant expression
//     (or `?`, which the desugarer resolves to a constant before this phase)
//   - positive
//   - representable as uint32_t
//
// Note:
//   array<int, x>
//   array<int, f()>
//   array<int, arr[0]>
//
// do not parse as static array types because identifiers, calls,
// and indexing are not constExpr forms. `?` is the only non-constExpr form
// allowed as a size, and only the desugarer accepts it (in declarations
// with an array-literal or string-literal initializer).
type
    : INT8_T                                   # Int8Type
    | INT16_T                                  # Int16Type
    | INT32_T                                  # Int32Type
    | INT64_T                                  # Int64Type
    | UINT8_T                                  # Uint8Type
    | UINT16_T                                 # Uint16Type
    | UINT32_T                                 # Uint32Type
    | UINT64_T                                 # Uint64Type
    | IDENTIFIER                               # StructType
    | qualifiedName                            # QualifiedStructType
    | ARRAY LT type GT                         # DynamicArrayType
    | ARRAY LT type COMMA arraySize GT        # StaticArrayType
    | PTR LT type GT                          # PtrType
    | MVIEW LT type GT                        # MViewType
    | VIEW LT type GT                         # ViewType
    ;

// ---------- LEXER ----------

// Function / declaration keywords
FUNC   : 'func' ;
RETURN : 'return' ;
VOID   : 'void' ;
STRUCT : 'struct' ;

// Keywords
PRINT   : 'print' ;
IF      : 'if' ;
ELSE    : 'else' ;
WHILE   : 'while' ;
FOR     : 'for' ;
BREAK    : 'break' ;
CONTINUE : 'continue' ;
PTR    : 'ptr' ;
MVIEW  : 'mview' ;
VIEW   : 'view' ;
GETPTR : 'getptr' ;
GETMVIEW : 'getmview' ;
GETVIEW  : 'getview' ;
// Dereference prefix operator. Syntax is `$(X)`, always parenthesized.
DEREF   : '$' ;
CAST    : 'cast' ;
UNSAFE_CAST : 'unsafe_cast' ;
SELF    : 'self' ;
NULL    : 'null' ;

// 'import' keyword. The import path is always a STRING_LITERAL.
IMPORT  : 'import' ;

// Namespaces
NAMESPACE     : 'namespace' ;
COLONCOLON    : '::' ;

// Primitive / type keywords
INT8_T   : 'int8_t' ;
INT16_T  : 'int16_t' ;
INT32_T  : 'int32_t' | 'int' ;
INT64_T  : 'int64_t' ;

UINT8_T  : 'uint8_t' | 'byte' ;
UINT16_T : 'uint16_t' ;
UINT32_T : 'uint32_t' | 'uint' ;
UINT64_T : 'uint64_t' ;

ARRAY    : 'array' ;

// Operators / punctuation
EQ      : '==' ;
NEQ     : '!=' ;
LE      : '<=' ;
GE      : '>=' ;
ANDAND  : '&&' ;
OROR    : '||' ;
ARROW   : '->' ;

// Compound assignment operators and increment / decrement.
// Longest match makes ordering against the single-character
// operators irrelevant, but they are grouped here together.
PLUS_ASSIGN    : '+=' ;
MINUS_ASSIGN   : '-=' ;
STAR_ASSIGN    : '*=' ;
SLASH_ASSIGN   : '/=' ;
PERCENT_ASSIGN : '%=' ;
PLUSPLUS       : '++' ;
MINUSMINUS     : '--' ;

ASSIGN  : '=' ;
PLUS    : '+' ;
MINUS   : '-' ;
STAR    : '*' ;
SLASH   : '/' ;
PERCENT : '%' ;
BANG    : '!' ;
AMP     : '&' ;
PIPE    : '|' ;
CARET   : '^' ;
DOT     : '.' ;
COMMA   : ',' ;
SEMI    : ';' ;
LT      : '<' ;
GT      : '>' ;
QUESTION: '?' ;

// Grouping with depth tracking
LPAREN : '(' { groupingDepth++; } ;
RPAREN : ')' { if (groupingDepth > 0) groupingDepth--; } ;

LBRACK : '[' { groupingDepth++; } ;
RBRACK : ']' { if (groupingDepth > 0) groupingDepth--; } ;

// IMPORTANT: do NOT include braces in groupingDepth
// because newlines inside { } should remain statement separators.
LBRACE : '{' ;
RBRACE : '}' ;

// Identifiers / literals
//
// String literals:
//   - start with "
//   - end with "
//   - may contain raw Unicode characters
//   - may contain literal tabs
//   - may contain literal newlines
//   - may not contain a raw unescaped "
//   - may not contain a raw unescaped backslash
//
// Supported escapes only:
//   \\
//   \"
//   \a
//   \b
//   \f
//   \n
//   \r
//   \t
//   \v
//
// No Unicode escape syntax is supported.
// So \u1234, \xFF, \0, and \q are invalid.
STRING_LITERAL
    : '"' (STRING_ESCAPE | ~["\\])* '"'
    ;

fragment STRING_ESCAPE
    : '\\' [\\"abfnrtv]
    ;

IDENTIFIER  : [a-zA-Z_][a-zA-Z0-9_]* ;
BIN_LITERAL : '0' [bB] [01]+ ;
HEX_LITERAL : '0' [xX] [0-9a-fA-F]+ ;
INT_LITERAL : [0-9]+ ;

// Comments
COMMENT : '//' ~[\r\n]* -> skip ;

// Horizontal whitespace
WS : [ \t]+ -> skip ;

// Newlines
NEWLINE
    : ('\r'? '\n')+
      {
          if (groupingDepth > 0) {
              skip();
          }
      }
    ;