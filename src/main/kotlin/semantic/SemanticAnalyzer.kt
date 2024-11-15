package nl.endevelopment.semantic

import nl.endevelopment.ast.*


/**
 * The SemanticAnalyzer performs type checking and scope resolution using the AST.
 */
class SemanticAnalyzer {
    private val symbolTable = SemanticSymbolTable()

    fun analyze(program: Program) {
        symbolTable.enterScope() // Enter global scope
        program.statements.forEach { analyze(it) }
        symbolTable.exitScope() // Exit global scope
    }

    private fun analyze(stmt: Stmt) {
        when (stmt) {
            is Stmt.LetStmt -> {
                val exprType = analyze(stmt.expr)
                if (exprType != stmt.type) {
                    throw Exception("Type mismatch for variable '${stmt.name}': expected ${stmt.type}, found ${exprType}")
                }
                symbolTable.define(stmt.name, stmt.type)
            }
            is Stmt.PrintStmt -> {
                val exprType = analyze(stmt.expr)
                if (exprType != Type.INT && exprType != Type.FLOAT && exprType != Type.BOOL) {
                    throw Exception("Print statement can only handle INT, FLOAT, or BOOL types.")
                }
            }
            is Stmt.IfStmt -> {
                val condType = analyze(stmt.condition)
                if (condType != Type.BOOL) {
                    throw Exception("Condition in IfStmt must be of type BOOL.")
                }
                // Enter new scope for thenBranch
                symbolTable.enterScope()
                stmt.thenBranch.forEach { analyze(it) }
                symbolTable.exitScope()

                // Enter new scope for elseBranch if it exists
                if (stmt.elseBranch != null) {
                    symbolTable.enterScope()
                    stmt.elseBranch.forEach { analyze(it) }
                    symbolTable.exitScope()
                }
            }
        }
    }

    private fun analyze(expr: Expr): Type {
        return when (expr) {
            is Expr.Number -> Type.INT
            is Expr.Variable -> {
                val varType = symbolTable.lookup(expr.name)
                    ?: throw Exception("Undefined variable: ${expr.name}")
                varType
            }
            is Expr.BinaryOp -> {
                val leftType = analyze(expr.left)
                val rightType = analyze(expr.right)
                if (leftType != rightType) {
                    throw Exception("Type mismatch in binary operation: ${leftType} vs ${rightType}")
                }
                // For simplicity, assume binary operations result in the same type
                leftType
            }
        }
    }

    fun getSymbolTable(): SemanticSymbolTable {
        return symbolTable
    }
}