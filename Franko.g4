grammar Franko;

@lexer::members {
    private int groupingDepth = 0;
}

// ---------- PARSER ----------

program
    : separators* (statement separators*)* EOF
    ;

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

simpleStmt
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

// ---------- ASSIGNMENT ----------

assignStmt
    : lvalue ASSIGN expr
    ;

// ---------- ARRAY ----------

// Existing standalone array init statement: a(10)
arrayInitStmt
    : IDENTIFIER LPAREN expr RPAREN
    ;

// a.uninit()
arrayUninitStmt
    : IDENTIFIER DOT UNINIT LPAREN RPAREN
    ;

// a.memset(0)
arrayMemsetStmt
    : IDENTIFIER DOT MEMSET LPAREN expr RPAREN
    ;

// a.memcpy(b)
arrayMemcpyStmt
    : IDENTIFIER DOT MEMCPY LPAREN IDENTIFIER RPAREN
    ;

// ---------- PRINT ----------

printStmt
    : PRINT LPAREN exprList? RPAREN
    ;

exprList
    : expr (COMMA expr)*
    ;

// ---------- LVALUES ----------

lvalue
    : IDENTIFIER
    | IDENTIFIER LBRACK expr RBRACK
    ;

// ---------- EXPRESSION STATEMENT ----------

exprStmt
    : expr
    ;

// ---------- EXPRESSIONS ----------

expr
    : MINUS expr                                  # UnaryMinus
    | expr op=(STAR | SLASH) expr                 # MulDiv
    | expr op=(PLUS | MINUS) expr                 # AddSub
    | expr op=(EQ | NEQ | LE | GE | LT | GT) expr # Compare
    | atom                                        # AtomExpr
    ;

atom
    : INT_LITERAL
    | IDENTIFIER
    | IDENTIFIER LBRACK expr RBRACK
    | LPAREN expr RPAREN
    ;

// ---------- TYPES ----------

type
    : INT32_T                             # Int32Type
    | UINT32_T                            # Uint32Type
    | FLOAT32_T                           # Float32Type
    | CHAR8_T                             # Char8Type
    | ARRAY LT type GT                    # DynamicArrayType
    | ARRAY LT type COMMA INT_LITERAL GT  # StaticArrayType
    ;

// ---------- LEXER ----------

// Keywords
ALLOC    : 'alloc' ;
DEL      : 'del' ;
PRINT    : 'print' ;
UNINIT   : 'uninit' ;
MEMSET   : 'memset' ;
MEMCPY   : 'memcpy' ;
IF       : 'if' ;
ELSE     : 'else' ;
WHILE    : 'while' ;

// Primitive / type keywords
// If you want int as an alias of int32_t:
INT32_T   : 'int32_t' | 'int' ;
UINT32_T  : 'uint32_t' ;
FLOAT32_T : 'float32_t' ;
CHAR8_T   : 'char8_t' ;
ARRAY     : 'array' ;

// Operators / punctuation
EQ     : '==' ;
NEQ    : '!=' ;
LE     : '<=' ;
GE     : '>=' ;

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

// IMPORTANT: do NOT include braces in groupingDepth
// because newlines inside { } should remain statement separators.
LBRACE : '{' ;
RBRACE : '}' ;

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