// Slang.g4
grammar Slang;

// Parser Rules

program
    : topLevelStatement* EOF
    ;

topLevelStatement
    : functionDef
    | statement
    ;

statement
    : letStmt
    | printStmt
    | ifStmt
    | returnStmt
    | callStmt
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

functionDef
    : 'fn' IDENT '(' paramList? ')' ':' type block
    ;

paramList
    : param (',' param)*
    ;

param
    : IDENT ':' type
    ;

returnStmt
    : 'return' expr? ';'
    ;

callStmt
    : IDENT '(' argList? ')' ';'
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
    | IDENT '(' argList? ')'         # CallExpr
    | NUMBER                         # NumberExpr
    | IDENT                          # VariableExpr
    ;

argList
    : expr (',' expr)*
    ;

// Lexer Rules

type
    : 'Int'
    | 'String'
    | 'Bool'
    | 'Void'
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
