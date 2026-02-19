package nl.endevelopment.ast

import nl.endevelopment.semantic.Type

// ASTNodes.kt
sealed class ASTNode

// Expressions
sealed class Expr : ASTNode() {
    data class Number(val value: Int) : Expr()
    data class Variable(val name: String) : Expr()
    data class BinaryOp(val left: Expr, val operator: String, val right: Expr) : Expr()
    data class Call(val name: String, val args: List<Expr>) : Expr()
}


// Statements
sealed class Stmt : ASTNode() {
    data class LetStmt(val name: String, val type: Type, val expr: Expr) : Stmt()
    data class PrintStmt(val expr: Expr) : Stmt()
    data class IfStmt(val condition: Expr, val thenBranch: List<Stmt>, val elseBranch: List<Stmt>?) : Stmt()
    data class FunctionDef(
        val name: String,
        val params: List<Param>,
        val returnType: Type,
        val body: List<Stmt>
    ) : Stmt()

    data class ReturnStmt(val expr: Expr?) : Stmt()

    data class ExprStmt(val expr: Expr) : Stmt()
}

data class Param(val name: String, val type: Type) : ASTNode()

// Program
data class Program(val statements: List<Stmt>) : ASTNode()
