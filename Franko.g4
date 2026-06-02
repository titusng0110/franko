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

// ---------- RECEIVER EXPRESSIONS ----------

// Can be used for:
//   arr
//   arr[i]
//   map[r][c]
//   nested[i][j][k]
receiverExpr
    : IDENTIFIER (LBRACK expr RBRACK)*
    ;

// ---------- ARRAY / METHOD-LIKE OPERATIONS ----------

// Existing standalone array init statement: a(10)
// Keep this restricted to plain identifiers for now,
// since declaration sugar/desugaring already handles array creation nicely.
arrayInitStmt
    : IDENTIFIER LPAREN expr RPAREN
    ;

// Receiver-based uninit
arrayUninitStmt
    : receiverExpr DOT UNINIT LPAREN RPAREN
    ;

// Receiver-based memset
arrayMemsetStmt
    : receiverExpr DOT MEMSET LPAREN expr RPAREN
    ;

// Receiver-based memcpy
arrayMemcpyStmt
    : receiverExpr DOT MEMCPY LPAREN receiverExpr RPAREN
    ;

// ---------- PRINT ----------

printStmt
    : PRINT LPAREN exprList? RPAREN
    ;

exprList
    : expr (COMMA expr)*
    ;

// ---------- LVALUES ----------

// Supports:
//   x
//   arr[i]
//   map[r][c]
lvalue
    : IDENTIFIER (LBRACK expr RBRACK)*
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
    | postfixExpr                                 # PostfixExprOnly
    ;

// Postfix indexing supports chained [] in expressions
postfixExpr
    : primary (LBRACK expr RBRACK)*
    ;

primary
    : integerLiteral
    | IDENTIFIER
    | LPAREN expr RPAREN
    ;

// ---------- INTEGER LITERALS ----------

integerLiteral
    : INT_LITERAL
    | BIN_LITERAL
    | HEX_LITERAL
    ;

// ---------- TYPES ----------

type
    : INT8_T                              # Int8Type
    | INT16_T                             # Int16Type
    | INT32_T                             # Int32Type
    | INT64_T                             # Int64Type
    | UINT8_T                             # Uint8Type
    | UINT16_T                            # Uint16Type
    | UINT32_T                            # Uint32Type
    | UINT64_T                            # Uint64Type
    | ARRAY LT type GT                    # DynamicArrayType
    | ARRAY LT type COMMA integerLiteral GT  # StaticArrayType
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
INT8_T    : 'int8_t' ;
INT16_T   : 'int16_t' ;
INT32_T   : 'int32_t' | 'int' ;
INT64_T   : 'int64_t' ;

UINT8_T   : 'uint8_t' | 'char';
UINT16_T  : 'uint16_t' ;
UINT32_T  : 'uint32_t' ;
UINT64_T  : 'uint64_t' ;

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