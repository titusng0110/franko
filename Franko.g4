grammar Franko;

@lexer::members {
    private int groupingDepth = 0;
}

// ---------- PARSER ----------

program
    : separators* (topLevelItem separators*)* EOF
    ;

topLevelItem
    : functionDecl
    | statement
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
//   addr<void> p.
//
functionDecl
    : FUNC IDENTIFIER LPAREN parameterList? RPAREN ARROW returnType separators* block
    ;

parameterList
    : parameter (COMMA parameter)*
    ;

parameter
    : type IDENTIFIER
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

// ---------- SIMPLE STATEMENTS ----------
//
// NOTE:
// - standalone a(10) parses as a normal expression statement.
// - x.memset(...), x.memcpy(...), x.uninit() are parsed as
//   ordinary member-call expressions.
// - function calls also parse as expression statements.
//
// Array intrinsics such as:
//   arr.memset(0)
//   arr.memcpy(other)
//   arr.uninit()
// should be recognized semantically from normal member-call syntax,
// not hardcoded in the lexer/parser.
simpleStmt
    : varDecl
    | delStmt
    | returnStmt
    | assignStmt
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
//   ndarray<byte, 12, 12> grid;
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
    | ALLOC type IDENTIFIER declSuffix?
    ;

declSuffix
    : ASSIGN declInitializer # DeclAssignInit
    | LPAREN expr RPAREN     # DeclArrayInit
    ;

declInitializer
    : arrayLiteral
    | expr
    ;

// ---------- DELETE ----------

delStmt
    : DEL IDENTIFIER
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
assignStmt
    : lvalue ASSIGN assignmentInitializer
    ;

assignmentInitializer
    : arrayLiteral
    | expr
    ;

// ---------- PRINT ----------
//
// Keeping print as a dedicated statement for now.
// obj.print() still works as a generic member call expression.
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
// Supports future struct field assignment:
//   x
//   arr[i]
//   obj.field
//   obj.field[i]
//   deref(p)
//   deref(p).field
//   deref(p)[i]
//   (deref(p)).field[i]
//
// Intentionally does NOT allow function-call results as lvalues.
lvalue
    : lvalueAtom lvalueSuffix*
    ;

lvalueAtom
    : IDENTIFIER
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
//   * /
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
    : unaryExpr ((STAR | SLASH) unaryExpr)*
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
//   deref(p).field
//   deref(p).memset(0)
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
    | IDENTIFIER
    | getAddrExpr
    | derefExpr
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
    | FUNC
    | RETURN
    | VOID
    | ALLOC
    | DEL
    | PRINT
    | IF
    | ELSE
    | WHILE
    | ADDR
    | GETADDR
    | DEREF
    | INT8_T
    | INT16_T
    | INT32_T
    | INT64_T
    | UINT8_T
    | UINT16_T
    | UINT32_T
    | UINT64_T
    | ARRAY
    | NDARRAY
    ;

// ---------- CONSTANT EXPRESSIONS ----------
//
// Used for:
//   - static array size expressions
//   - ndarray dimension expressions
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
//   - getaddr(...)
//   - deref(...)
//   - array initializer lists
//   - string literals
//
// Semantic checker should still reject invalid constant expressions such as:
//   1 / 0
//   1 << -1
//
// Semantic checker should also perform arbitrary-precision folding and
// contextual range checking.

constExpr
    : constLogicalOrExpr
    ;

// Used by ndarray<T, N, N, N...>.
constExprList
    : constExpr (COMMA constExpr)*
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
    : constUnaryExpr ((STAR | SLASH) constUnaryExpr)*
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

getAddrExpr
    : GETADDR LPAREN lvalue RPAREN
    ;

derefExpr
    : DEREF LPAREN expr RPAREN
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
// ndarray is syntactic sugar for nested static arrays.
//
// Examples:
//   ndarray<byte, 12>
//      means:
//   array<byte, 12>
//
//   ndarray<byte, 12, 20>
//      means:
//   array<array<byte, 20>, 12>
//
//   ndarray<byte, 12, 20, 30>
//      means:
//   array<array<array<byte, 30>, 20>, 12>
//
// Access order follows the dimension list:
//
//   ndarray<byte, R, C> grid
//   grid[r][c]
//
//   ndarray<byte, X, Y, Z> cube
//   cube[x][y][z]
//
// Grammar only parses this form. The AST visitor or desugarer should lower
// ndarray<T, N, N, ...> to nested StaticArrayTypeNode values.
//
// Semantic checker should later require that each size expression is:
//   - a valid compile-time integer constant expression
//   - positive
//   - representable as uint32_t
//
// Note:
//   array<int, x>
//   array<int, f()>
//   array<int, arr[0]>
//   ndarray<int, x, 10>
//   ndarray<int, f(), 10>
//   ndarray<int, arr[0], 10>
//
// do not parse as static array / ndarray types because identifiers, calls,
// and indexing are not constExpr forms.
type
    : INT8_T                                   # Int8Type
    | INT16_T                                  # Int16Type
    | INT32_T                                  # Int32Type
    | INT64_T                                  # Int64Type
    | UINT8_T                                  # Uint8Type
    | UINT16_T                                 # Uint16Type
    | UINT32_T                                 # Uint32Type
    | UINT64_T                                 # Uint64Type
    | ARRAY LT type GT                         # DynamicArrayType
    | ARRAY LT type COMMA constExpr GT         # StaticArrayType
    | NDARRAY LT type COMMA constExprList GT   # NdArrayType
    | ADDR LT type GT                          # AddrType
    ;

// ---------- LEXER ----------

// Function keywords
FUNC   : 'func' ;
RETURN : 'return' ;
VOID   : 'void' ;

// Keywords
ALLOC   : 'alloc' ;
DEL     : 'del' ;
PRINT   : 'print' ;
IF      : 'if' ;
ELSE    : 'else' ;
WHILE   : 'while' ;
ADDR    : 'addr' ;
GETADDR : 'getaddr' ;
DEREF   : 'deref' ;

// Primitive / type keywords
INT8_T   : 'int8_t' ;
INT16_T  : 'int16_t' ;
INT32_T  : 'int32_t' | 'int' ;
INT64_T  : 'int64_t' ;

UINT8_T  : 'uint8_t' | 'byte' ;
UINT16_T : 'uint16_t' ;
UINT32_T : 'uint32_t' ;
UINT64_T : 'uint64_t' ;

ARRAY    : 'array' ;
NDARRAY  : 'ndarray' ;

// Operators / punctuation
EQ      : '==' ;
NEQ     : '!=' ;
LE      : '<=' ;
GE      : '>=' ;
ANDAND  : '&&' ;
OROR    : '||' ;
ARROW   : '->' ;

ASSIGN  : '=' ;
PLUS    : '+' ;
MINUS   : '-' ;
STAR    : '*' ;
SLASH   : '/' ;
BANG    : '!' ;
AMP     : '&' ;
PIPE    : '|' ;
CARET   : '^' ;
DOT     : '.' ;
COMMA   : ',' ;
SEMI    : ';' ;
LT      : '<' ;
GT      : '>' ;

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