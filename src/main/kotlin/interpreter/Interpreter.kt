// Interpreter.kt
package nl.endevelopment.interpreter

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt

class Interpreter() {

    private val variableEnv = VariableEnvironment()
    private val functions = mutableMapOf<String, Stmt.FunctionDef>()
    private var functionDepth = 0

    private class ReturnSignal(val value: Value) : RuntimeException(null, null, false, false)

    /**
     * Starts interpreting the given program.
     */
    fun interpret(program: Program) {
        // Initialize global scope for variable values
        variableEnv.enterScope()
        try {
            // Register function definitions first
            program.statements.filterIsInstance<Stmt.FunctionDef>()
                .forEach { registerFunction(it) }

            // Execute top-level statements (excluding function definitions)
            program.statements.filterNot { it is Stmt.FunctionDef }
                .forEach { execute(it) }
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
            is Stmt.FunctionDef -> registerFunction(stmt)
            is Stmt.ReturnStmt -> handleReturnStmt(stmt)
            is Stmt.ExprStmt -> handleExprStmt(stmt)
        }
    }

    /**
     * Handles variable declaration and assignment.
     */
    private fun handleLetStmt(stmt: Stmt.LetStmt) {
        // Evaluate the expression
        val value = evaluate(stmt.expr, allowVoid = false)

        // Define the variable in the environment
        variableEnv.define(stmt.name, value)
    }

    /**
     * Handles print statements.
     */
    private fun handlePrintStmt(stmt: Stmt.PrintStmt) {
        val value = evaluate(stmt.expr, allowVoid = false)
        println(valueToString(value))
    }

    /**
     * Handles if statements with optional else branches.
     */
    private fun handleIfStmt(stmt: Stmt.IfStmt) {
        val conditionValue = evaluate(stmt.condition, allowVoid = false)
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
    private fun evaluate(expr: Expr, allowVoid: Boolean = false): Value {
        return when (expr) {
            is Expr.Number -> Value.IntValue(expr.value)
            is Expr.Variable -> variableEnv.get(expr.name)
            is Expr.BinaryOp -> evaluateBinaryOp(expr)
            is Expr.Call -> callFunction(expr, allowVoid)
            is Expr.ListLiteral -> Value.ListValue(expr.elements.map { evaluate(it, allowVoid = false) })
            is Expr.Index -> evaluateIndex(expr)
        }
    }

    private fun registerFunction(stmt: Stmt.FunctionDef) {
        if (stmt.name == "len") {
            throw RuntimeException("Cannot redefine built-in function 'len'.")
        }
        if (functions.containsKey(stmt.name)) {
            throw RuntimeException("Function '${stmt.name}' is already defined.")
        }
        functions[stmt.name] = stmt
    }

    private fun callFunction(expr: Expr.Call, allowVoid: Boolean): Value {
        if (expr.name == "len") {
            return evalLen(expr)
        }
        val function = functions[expr.name]
            ?: throw RuntimeException("Undefined function '${expr.name}'.")

        if (expr.args.size != function.params.size) {
            throw RuntimeException("Function '${expr.name}' expects ${function.params.size} arguments, got ${expr.args.size}.")
        }
        if (!allowVoid && function.returnType == nl.endevelopment.semantic.Type.VOID) {
            throw RuntimeException("Cannot use void function '${expr.name}' in an expression.")
        }

        val argValues = expr.args.map { evaluate(it, allowVoid = false) }

        functionDepth++
        variableEnv.enterScope()
        try {
            function.params.forEachIndexed { index, param ->
                variableEnv.define(param.name, argValues[index])
            }
            try {
                executeBlock(function.body)
            } catch (signal: ReturnSignal) {
                validateReturnType(function.returnType, signal.value)
                return signal.value
            }
        } finally {
            variableEnv.exitScope()
            functionDepth--
        }

        val defaultValue = Value.VoidValue
        if (function.returnType != nl.endevelopment.semantic.Type.VOID) {
            throw RuntimeException("Function '${function.name}' must return a value.")
        }
        return defaultValue
    }

    private fun handleReturnStmt(stmt: Stmt.ReturnStmt) {
        if (functionDepth <= 0) {
            throw RuntimeException("Return statement is only allowed inside a function.")
        }
        val value = stmt.expr?.let { evaluate(it, allowVoid = false) } ?: Value.VoidValue
        throw ReturnSignal(value)
    }

    private fun handleExprStmt(stmt: Stmt.ExprStmt) {
        evaluate(stmt.expr, allowVoid = true)
    }

    private fun evalLen(expr: Expr.Call): Value {
        if (expr.args.size != 1) {
            throw RuntimeException("len expects 1 argument, got ${expr.args.size}.")
        }
        val value = evaluate(expr.args[0], allowVoid = false)
        return when (value) {
            is Value.ListValue -> Value.IntValue(value.elements.size)
            else -> throw RuntimeException("len expects a List argument.")
        }
    }

    private fun evaluateIndex(expr: Expr.Index): Value {
        val target = evaluate(expr.target, allowVoid = false)
        val indexValue = evaluate(expr.index, allowVoid = false)
        if (indexValue !is Value.IntValue) {
            throw RuntimeException("List index must be an Int.")
        }
        return when (target) {
            is Value.ListValue -> {
                val idx = indexValue.value
                if (idx < 0 || idx >= target.elements.size) {
                    throw RuntimeException("List index out of bounds: $idx.")
                }
                target.elements[idx]
            }
            else -> throw RuntimeException("Indexing is only supported on Lists.")
        }
    }

    private fun validateReturnType(expected: nl.endevelopment.semantic.Type, value: Value) {
        when (expected) {
            nl.endevelopment.semantic.Type.INT -> if (value !is Value.IntValue) {
                throw RuntimeException("Function return type mismatch. Expected Int.")
            }
            nl.endevelopment.semantic.Type.FLOAT -> if (value !is Value.FloatValue) {
                throw RuntimeException("Function return type mismatch. Expected Float.")
            }
            nl.endevelopment.semantic.Type.BOOL -> if (value !is Value.BoolValue) {
                throw RuntimeException("Function return type mismatch. Expected Bool.")
            }
            nl.endevelopment.semantic.Type.STRING -> if (value !is Value.StringValue) {
                throw RuntimeException("Function return type mismatch. Expected String.")
            }
            nl.endevelopment.semantic.Type.LIST -> if (value !is Value.ListValue) {
                throw RuntimeException("Function return type mismatch. Expected List.")
            }
            nl.endevelopment.semantic.Type.VOID -> if (value !is Value.VoidValue) {
                throw RuntimeException("Function return type mismatch. Expected void.")
            }
        }
    }

    /**
     * Evaluates a binary operation expression.
     * Assumes that types are already verified by the SemanticAnalyzer.
     */
    private fun evaluateBinaryOp(expr: Expr.BinaryOp): Value {
        val leftVal = evaluate(expr.left, allowVoid = false)
        val rightVal = evaluate(expr.right, allowVoid = false)

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
            is Value.ListValue -> value.elements.joinToString(prefix = "[", postfix = "]") { valueToString(it) }
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
