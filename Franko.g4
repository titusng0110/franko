grammar Franko;

// ---------- PARSER ----------

program
    : statement* EOF
    ;

// Statements

statement
    : varDecl ';'
    | delStmt ';'
    | assignStmt ';'
    | arrayInitStmt ';'
    | arrayUninitStmt ';'
    | exprStmt ';'
    ;

// Variable declaration

varDecl
    : type IDENTIFIER                // slab / stack variable
    | 'alloc' type IDENTIFIER        // heap variable
    ;

// Delete

delStmt
    : 'del' IDENTIFIER
    ;

// Assignment

assignStmt
    : lvalue '=' expr
    ;

// ---------- ARRAY ----------

// Initialization: x(10)
arrayInitStmt
    : IDENTIFIER '(' expr ')'
    ;

// Uninitialization: x.uninit()
arrayUninitStmt
    : IDENTIFIER '.' 'uninit' '(' ')'
    ;

// L-values

lvalue
    : IDENTIFIER
    | IDENTIFIER '[' expr ']'
    ;

// ---------- EXPRESSION STATEMENT ----------
exprStmt
    : expr
    ;


// ---------- EXPRESSIONS ----------

expr
    : expr op=('*' | '/') expr        # MulDiv
    | expr op=('+' | '-') expr        # AddSub
    | atom                            # AtomExpr
    ;

atom
    : INT_LITERAL
    | IDENTIFIER
    | IDENTIFIER '[' expr ']'
    | '(' expr ')'
    ;

// ---------- TYPES ----------

type
    : 'int32_t'
    | 'uint32_t'
    | 'float32_t'
    | 'char8_t'
    | 'array' '<' type '>'
    ;

// ---------- LEXER ----------

IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]* ;

INT_LITERAL : [0-9]+ ;

WS : [ \t\r\n]+ -> skip ;