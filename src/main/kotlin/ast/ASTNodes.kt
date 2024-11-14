package nl.endevelopment.ast

import nl.endevelopment.semantic.Type

// ASTNodes.kt
sealed class ASTNode

// Expressions
sealed class Expr : ASTNode() {
    data class Number(val value: Int) : Expr()
    data class Variable(val name: String) : Expr()
    data class BinaryOp(val left: Expr, val operator: String, val right: Expr) : Expr()
}


// Statements
sealed class Stmt : ASTNode() {
    data class LetStmt(val name: String, val type: Type, val expr: Expr) : Stmt()
    data class PrintStmt(val expr: Expr) : Stmt()
    data class IfStmt(val condition: Expr, val thenBranch: List<Stmt>, val elseBranch: List<Stmt>?) : Stmt()
}


// Program
data class Program(val statements: List<Stmt>) : ASTNode()
