grammar Franko;

@lexer::members {
    private int groupingDepth = 0;
}

// ---------- PARSER ----------

program
    : separators* (statement (separators+ statement)*)? separators* EOF
    ;

statement
    : varDecl
    | delStmt
    | assignStmt
    | arrayInitStmt
    | arrayUninitStmt
    | arrayMemsetStmt
    | arrayMemcpyStmt
    | printStmt
    | exprStmt
    ;

separators
    : (SEMI | NEWLINE)+
    ;

// Variable declaration
varDecl
    : type IDENTIFIER
    | ALLOC type IDENTIFIER
    ;

// Delete
delStmt
    : DEL IDENTIFIER
    ;

// Assignment
assignStmt
    : lvalue ASSIGN expr
    ;

// ---------- ARRAY ----------

// Initialization: a(10)
arrayInitStmt
    : IDENTIFIER LPAREN expr RPAREN
    ;

// Uninitialization: a.uninit()
arrayUninitStmt
    : IDENTIFIER DOT UNINIT LPAREN RPAREN
    ;

// Memset: a.memset(0)
arrayMemsetStmt
    : IDENTIFIER DOT MEMSET LPAREN expr RPAREN
    ;

// Memcpy: a.memcpy(b)
arrayMemcpyStmt
    : IDENTIFIER DOT MEMCPY LPAREN IDENTIFIER RPAREN
    ;

// Print: print(x, y, z)
printStmt
    : PRINT LPAREN exprList? RPAREN
    ;

exprList
    : expr (COMMA expr)*
    ;

lvalue
    : IDENTIFIER
    | IDENTIFIER LBRACK expr RBRACK
    ;

exprStmt
    : expr
    ;

expr
    : MINUS expr                     # UnaryMinus
    | expr op=(STAR | SLASH) expr    # MulDiv
    | expr op=(PLUS | MINUS) expr    # AddSub
    | atom                           # AtomExpr
    ;

atom
    : INT_LITERAL
    | IDENTIFIER
    | IDENTIFIER LBRACK expr RBRACK
    | LPAREN expr RPAREN
    ;

type
    : INT32_T
    | UINT32_T
    | FLOAT32_T
    | CHAR8_T
    | ARRAY LT type GT
    | ARRAY LT type COMMA INT_LITERAL GT
    ;

// ---------- LEXER ----------

// Keywords
ALLOC    : 'alloc' ;
DEL      : 'del' ;
PRINT    : 'print' ;
UNINIT   : 'uninit' ;
MEMSET   : 'memset' ;
MEMCPY   : 'memcpy' ;

INT32_T   : 'int32_t' ;
UINT32_T  : 'uint32_t' ;
FLOAT32_T : 'float32_t' ;
CHAR8_T   : 'char8_t' ;
ARRAY     : 'array' ;

// Operators / punctuation
ASSIGN : '=' ;
PLUS   : '+' ;
MINUS  : '-' ;
STAR   : '*' ;
SLASH  : '/' ;
DOT    : '.' ;
COMMA  : ',' ;
SEMI   : ';' ;
LT     : '<' ;
GT     : '>' ;

// Grouping with depth tracking
LPAREN : '(' { groupingDepth++; } ;
RPAREN : ')' { if (groupingDepth > 0) groupingDepth--; } ;

LBRACK : '[' { groupingDepth++; } ;
RBRACK : ']' { if (groupingDepth > 0) groupingDepth--; } ;

// Identifiers / literals
IDENTIFIER  : [a-zA-Z_][a-zA-Z0-9_]* ;
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