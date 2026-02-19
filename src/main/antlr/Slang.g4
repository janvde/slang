// Slang.g4
grammar Slang;

// Parser Rules

program
    : statement* EOF
    ;

statement
    : letStmt
    | printStmt
    | ifStmt
    ;

letStmt
    : 'let' IDENT ':' type '=' expr ';'
    ;

printStmt
    : 'print' '(' expr ')' ';'
    ;

ifStmt
    : 'if' '(' expr ')' block ('else' block)?
    ;

block
    : '{' statement* '}'
    ;

expr
    : expr op=('*'|'/') expr         # MulDivExpr
    | expr op=('+'|'-') expr         # AddSubExpr
    | expr op=('>'|'>='|'<'|'<=') expr # ComparisonExpr
    | expr op=('=='|'!=') expr       # EqualityExpr
    | '(' expr ')'                   # ParenExpr
    | NUMBER                         # NumberExpr
    | IDENT                          # VariableExpr
    ;

// Lexer Rules

type
    : 'Int'
    | 'String'
    | 'Bool'
    ;

IDENT
    : [a-zA-Z_][a-zA-Z_0-9]*
    ;

NUMBER
    : [0-9]+
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

COMMENT
    : '//' ~[\r\n]* -> skip
    ;

LINE_COMMENT
    : '/*' .*? '*/' -> skip
    ;

// Operators and Punctuation
// Define literals directly in parser rules
