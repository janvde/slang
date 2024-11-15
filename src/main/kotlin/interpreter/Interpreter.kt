// Interpreter.kt
package nl.endevelopment.interpreter

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt

class Interpreter() {

    private val variableEnv = VariableEnvironment()

    /**
     * Starts interpreting the given program.
     */
    fun interpret(program: Program) {
        // Initialize global scope for variable values
        variableEnv.enterScope()
        try {
            executeBlock(program.statements)
        } catch (e: RuntimeException) {
            println("Runtime Error: ${e.message}")
        } finally {
            variableEnv.exitScope()
        }
    }

    /**
     * Executes a block of statements within the current scope.
     */
    private fun executeBlock(statements: List<Stmt>) {
        for (stmt in statements) {
            execute(stmt)
        }
    }

    /**
     * Executes a single statement.
     */
    private fun execute(stmt: Stmt) {
        when (stmt) {
            is Stmt.LetStmt -> handleLetStmt(stmt)
            is Stmt.PrintStmt -> handlePrintStmt(stmt)
            is Stmt.IfStmt -> handleIfStmt(stmt)
        }
    }

    /**
     * Handles variable declaration and assignment.
     */
    private fun handleLetStmt(stmt: Stmt.LetStmt) {
        // Evaluate the expression
        val value = evaluate(stmt.expr)

        // Define the variable in the environment
        variableEnv.define(stmt.name, value)
    }

    /**
     * Handles print statements.
     */
    private fun handlePrintStmt(stmt: Stmt.PrintStmt) {
        val value = evaluate(stmt.expr)
        println(valueToString(value))
    }

    /**
     * Handles if statements with optional else branches.
     */
    private fun handleIfStmt(stmt: Stmt.IfStmt) {
        val conditionValue = evaluate(stmt.condition)
        val conditionResult = evaluateCondition(conditionValue)

        // Enter a new scope for the branch
        variableEnv.enterScope()
        try {
            if (conditionResult) {
                executeBlock(stmt.thenBranch)
            } else if (stmt.elseBranch != null) {
                executeBlock(stmt.elseBranch)
            }
        } finally {
            // Exit the branch scope
            variableEnv.exitScope()
        }
    }

    /**
     * Evaluates an expression and returns its value.
     */
    private fun evaluate(expr: Expr): Value {
        return when (expr) {
            is Expr.Number -> Value.IntValue(expr.value)
            is Expr.Variable -> variableEnv.get(expr.name)
            is Expr.BinaryOp -> evaluateBinaryOp(expr)
        }
    }

    /**
     * Evaluates a binary operation expression.
     * Assumes that types are already verified by the SemanticAnalyzer.
     */
    private fun evaluateBinaryOp(expr: Expr.BinaryOp): Value {
        val leftVal = evaluate(expr.left)
        val rightVal = evaluate(expr.right)

        return when (expr.operator) {
            "+" -> add(leftVal, rightVal)
            "-" -> subtract(leftVal, rightVal)
            "*" -> multiply(leftVal, rightVal)
            "/" -> divide(leftVal, rightVal)
            "%" -> modulo(leftVal, rightVal)
            "==" -> equalsOp(leftVal, rightVal)
            "!=" -> notEqualsOp(leftVal, rightVal)
            "<" -> lessThan(leftVal, rightVal)
            "<=" -> lessThanOrEqual(leftVal, rightVal)
            ">" -> greaterThan(leftVal, rightVal)
            ">=" -> greaterThanOrEqual(leftVal, rightVal)
            else -> throw RuntimeException("Unknown operator '${expr.operator}'.")
        }
    }

    /**
     * Handles addition.
     */
    private fun add(left: Value, right: Value): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.IntValue(left.value + right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.FloatValue(left.value + right.value)
            left is Value.StringValue && right is Value.StringValue -> Value.StringValue(left.value + right.value)
            else -> throw RuntimeException("Unsupported operand types for '+': ${left::class.simpleName} and ${right::class.simpleName}.")
        }
    }

    /**
     * Handles subtraction.
     */
    private fun subtract(left: Value, right: Value): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.IntValue(left.value - right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.FloatValue(left.value - right.value)
            else -> throw RuntimeException("Unsupported operand types for '-': ${left::class.simpleName} and ${right::class.simpleName}.")
        }
    }

    /**
     * Handles multiplication.
     */
    private fun multiply(left: Value, right: Value): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.IntValue(left.value * right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.FloatValue(left.value * right.value)
            else -> throw RuntimeException("Unsupported operand types for '*': ${left::class.simpleName} and ${right::class.simpleName}.")
        }
    }

    /**
     * Handles division.
     */
    private fun divide(left: Value, right: Value): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> {
                if (right.value == 0) throw RuntimeException("Division by zero.")
                Value.IntValue(left.value / right.value)
            }

            left is Value.FloatValue && right is Value.FloatValue -> {
                if (right.value == 0.0f) throw RuntimeException("Division by zero.")
                Value.FloatValue(left.value / right.value)
            }

            else -> throw RuntimeException("Unsupported operand types for '/': ${left::class.simpleName} and ${right::class.simpleName}.")
        }
    }

    /**
     * Handles modulo operation.
     */
    private fun modulo(left: Value, right: Value): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> {
                if (right.value == 0) throw RuntimeException("Modulo by zero.")
                Value.IntValue(left.value % right.value)
            }

            else -> throw RuntimeException("Unsupported operand types for '%': ${left::class.simpleName} and ${right::class.simpleName}.")
        }
    }

    /**
     * Handles equality check.
     */
    private fun equalsOp(left: Value, right: Value): Value {
        return Value.BoolValue(left == right)
    }

    /**
     * Handles inequality check.
     */
    private fun notEqualsOp(left: Value, right: Value): Value {
        return Value.BoolValue(left != right)
    }

    /**
     * Handles less than comparison.
     */
    private fun lessThan(left: Value, right: Value): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value < right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value < right.value)
            else -> throw RuntimeException("Unsupported operand types for '<': ${left::class.simpleName} and ${right::class.simpleName}.")
        }
    }

    /**
     * Handles less than or equal comparison.
     */
    private fun lessThanOrEqual(left: Value, right: Value): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value <= right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value <= right.value)
            else -> throw RuntimeException("Unsupported operand types for '<=': ${left::class.simpleName} and ${right::class.simpleName}.")
        }
    }

    /**
     * Handles greater than comparison.
     */
    private fun greaterThan(left: Value, right: Value): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value > right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value > right.value)
            else -> throw RuntimeException("Unsupported operand types for '>': ${left::class.simpleName} and ${right::class.simpleName}.")
        }
    }

    /**
     * Handles greater than or equal comparison.
     */
    private fun greaterThanOrEqual(left: Value, right: Value): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value >= right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value >= right.value)
            else -> throw RuntimeException("Unsupported operand types for '>=': ${left::class.simpleName} and ${right::class.simpleName}.")
        }
    }

    /**
     * Converts a Value to its string representation for printing.
     */
    private fun valueToString(value: Value): String {
        return when (value) {
            is Value.IntValue -> value.value.toString()
            is Value.FloatValue -> value.value.toString()
            is Value.BoolValue -> value.value.toString()
            is Value.StringValue -> value.value
            is Value.VoidValue -> "void"
        }
    }

    /**
     * Evaluates a condition represented by a Value.
     * In this interpreter, booleans are directly used, and integers/floats interpret non-zero as true.
     */
    private fun evaluateCondition(value: Value): Boolean {
        return when (value) {
            is Value.BoolValue -> value.value
            is Value.IntValue -> value.value != 0
            is Value.FloatValue -> value.value != 0.0f
            else -> throw RuntimeException("Unsupported type for condition evaluation: ${value::class.simpleName}.")
        }
    }
}
