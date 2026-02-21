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
    | varStmt
    | assignStmt
    | printStmt
    | ifStmt
    | returnStmt
    | callStmt
    ;

letStmt
    : 'let' IDENT ':' type '=' expr ';'
    ;

varStmt
    : 'var' IDENT ':' type '=' expr ';'
    ;

assignStmt
    : IDENT '=' expr ';'
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
    : TRUE                           # BoolTrueExpr
    | FALSE                          # BoolFalseExpr
    | expr '[' expr ']'              # IndexExpr
    | expr op=('*'|'/'|'%') expr     # MulDivExpr
    | expr op=('+'|'-') expr         # AddSubExpr
    | expr op=('>'|'>='|'<'|'<=') expr # ComparisonExpr
    | expr op=('=='|'!=') expr       # EqualityExpr
    | '(' expr ')'                   # ParenExpr
    | IDENT '(' argList? ')'         # CallExpr
    | listLiteral                    # ListExpr
    | STRING_LITERAL                 # StringExpr
    | FLOAT                          # FloatExpr
    | NUMBER                         # NumberExpr
    | IDENT                          # VariableExpr
    ;

argList
    : expr (',' expr)*
    ;

listLiteral
    : '[' (expr (',' expr)*)? ']'
    ;

// Lexer Rules

type
    : 'Int'
    | 'Float'
    | 'String'
    | 'Bool'
    | 'Void'
    | 'List'
    ;

TRUE : 'true';
FALSE : 'false';

IDENT
    : [a-zA-Z_][a-zA-Z_0-9]*
    ;

STRING_LITERAL
    : '"' (~["\\\r\n] | '\\' [tnr"\\])* '"'
    ;

FLOAT
    : [0-9]+ '.' [0-9]+
    | [0-9]+ 'e' [+-]? [0-9]+
    | [0-9]+ '.' [0-9]+ 'e' [+-]? [0-9]+
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
