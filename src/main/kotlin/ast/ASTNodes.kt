package nl.endevelopment.ast

import nl.endevelopment.semantic.Type

sealed class ASTNode

data class SourceLocation(val line: Int, val column: Int) {
    companion object {
        val UNKNOWN = SourceLocation(-1, -1)
    }

    fun format(message: String): String {
        return if (line > 0 && column > 0) {
            "Error at line $line, col $column: $message"
        } else {
            message
        }
    }
}

// Expressions
sealed class Expr(open val location: SourceLocation = SourceLocation.UNKNOWN) : ASTNode() {
    data class Number(val value: Int, override val location: SourceLocation = SourceLocation.UNKNOWN) : Expr(location)
    data class FloatLiteral(val value: Float, override val location: SourceLocation = SourceLocation.UNKNOWN) : Expr(location)
    data class BoolLiteral(val value: Boolean, override val location: SourceLocation = SourceLocation.UNKNOWN) : Expr(location)
    data class StringLiteral(val value: String, override val location: SourceLocation = SourceLocation.UNKNOWN) : Expr(location)
    data class Variable(val name: String, override val location: SourceLocation = SourceLocation.UNKNOWN) : Expr(location)
    data class UnaryOp(val operator: String, val operand: Expr, override val location: SourceLocation = SourceLocation.UNKNOWN) : Expr(location)
    data class BinaryOp(val left: Expr, val operator: String, val right: Expr, override val location: SourceLocation = SourceLocation.UNKNOWN) : Expr(location)
    data class Call(val name: String, val args: List<Expr>, override val location: SourceLocation = SourceLocation.UNKNOWN) : Expr(location)
    data class ListLiteral(val elements: List<Expr>, override val location: SourceLocation = SourceLocation.UNKNOWN) : Expr(location)
    data class Index(val target: Expr, val index: Expr, override val location: SourceLocation = SourceLocation.UNKNOWN) : Expr(location)
}

// Statements
sealed class Stmt(open val location: SourceLocation = SourceLocation.UNKNOWN) : ASTNode() {
    data class LetStmt(val name: String, val type: Type, val expr: Expr, override val location: SourceLocation = SourceLocation.UNKNOWN) : Stmt(location)
    data class VarStmt(val name: String, val type: Type, val expr: Expr, override val location: SourceLocation = SourceLocation.UNKNOWN) : Stmt(location)
    data class AssignStmt(val name: String, val expr: Expr, override val location: SourceLocation = SourceLocation.UNKNOWN) : Stmt(location)
    data class PrintStmt(val expr: Expr, override val location: SourceLocation = SourceLocation.UNKNOWN) : Stmt(location)
    data class IfStmt(
        val condition: Expr,
        val thenBranch: List<Stmt>,
        val elseBranch: List<Stmt>?,
        override val location: SourceLocation = SourceLocation.UNKNOWN
    ) : Stmt(location)

    data class WhileStmt(val condition: Expr, val body: List<Stmt>, override val location: SourceLocation = SourceLocation.UNKNOWN) : Stmt(location)

    data class ForStmt(
        val init: Stmt?,
        val condition: Expr?,
        val update: Stmt?,
        val body: List<Stmt>,
        override val location: SourceLocation = SourceLocation.UNKNOWN
    ) : Stmt(location)

    data class BreakStmt(override val location: SourceLocation = SourceLocation.UNKNOWN) : Stmt(location)
    data class ContinueStmt(override val location: SourceLocation = SourceLocation.UNKNOWN) : Stmt(location)

    data class FunctionDef(
        val name: String,
        val params: List<Param>,
        val returnType: Type,
        val body: List<Stmt>,
        override val location: SourceLocation = SourceLocation.UNKNOWN
    ) : Stmt(location)

    data class ReturnStmt(val expr: Expr?, override val location: SourceLocation = SourceLocation.UNKNOWN) : Stmt(location)
    data class ExprStmt(val expr: Expr, override val location: SourceLocation = SourceLocation.UNKNOWN) : Stmt(location)
}

data class Param(val name: String, val type: Type, val location: SourceLocation = SourceLocation.UNKNOWN) : ASTNode()

// Program
data class Program(val statements: List<Stmt>) : ASTNode()
