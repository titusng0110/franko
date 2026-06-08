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
//   - any ordinary Franko type
//   - void
//
// void is intentionally NOT part of the normal type rule.
// This prevents declarations such as:
//   void x;
//   array<void> xs;
//   addr<void> p;
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

varDecl
    : type IDENTIFIER declSuffix?
    | ALLOC type IDENTIFIER declSuffix?
    ;

declSuffix
    : ASSIGN expr        # DeclAssignInit
    | LPAREN expr RPAREN # DeclArrayInit
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

assignStmt
    : lvalue ASSIGN expr
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
// Precedence (lowest to highest):
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
postfixExpr
    : primary postfixSuffix*
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
    ;

primary
    : integerLiteral
    | IDENTIFIER
    | getAddrExpr
    | derefExpr
    | LPAREN expr RPAREN
    ;

getAddrExpr
    : GETADDR LPAREN lvalue RPAREN
    ;

derefExpr
    : DEREF LPAREN expr RPAREN
    ;

// ---------- INTEGER LITERALS ----------

integerLiteral
    : INT_LITERAL
    | BIN_LITERAL
    | HEX_LITERAL
    ;

// ---------- TYPES ----------

type
    : INT8_T                                # Int8Type
    | INT16_T                               # Int16Type
    | INT32_T                               # Int32Type
    | INT64_T                               # Int64Type
    | UINT8_T                               # Uint8Type
    | UINT16_T                              # Uint16Type
    | UINT32_T                              # Uint32Type
    | UINT64_T                              # Uint64Type
    | ARRAY LT type GT                      # DynamicArrayType
    | ARRAY LT type COMMA integerLiteral GT # StaticArrayType
    | ADDR LT type GT                       # AddrType
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

UINT8_T  : 'uint8_t' | 'char' ;
UINT16_T : 'uint16_t' ;
UINT32_T : 'uint32_t' ;
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