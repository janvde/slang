package nl.endevelopment.semantic

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.ast.Stmt

class TypeChecker {
    private data class VariableInfo(val type: Type, val immutable: Boolean)
    private data class FunctionInfo(val returnType: Type, val paramTypes: List<Type>)

    private val scopes = mutableListOf<MutableMap<String, VariableInfo>>()
    private val functions = mutableMapOf<String, FunctionInfo>()
    private var currentReturnType: Type? = null
    private var loopDepth = 0

    fun check(program: Program) {
        scopes.clear()
        functions.clear()
        currentReturnType = null
        loopDepth = 0

        enterScope()
        try {
            program.statements.filterIsInstance<Stmt.FunctionDef>().forEach { declareFunction(it) }
            program.statements.forEach { checkStatement(it) }
        } finally {
            exitScope()
        }
    }

    private fun declareFunction(stmt: Stmt.FunctionDef) {
        if (stmt.name == "len") {
            fail(stmt.location, "Cannot redefine built-in function 'len'.")
        }
        if (functions.containsKey(stmt.name)) {
            fail(stmt.location, "Function '${stmt.name}' is already defined.")
        }
        functions[stmt.name] = FunctionInfo(stmt.returnType, stmt.params.map { it.type })
    }

    private fun checkStatement(stmt: Stmt) {
        when (stmt) {
            is Stmt.LetStmt -> {
                ensureNotDefinedInCurrentScope(stmt.name, stmt.location)
                val exprType = inferExprType(stmt.expr)
                ensureAssignable(stmt.type, exprType, stmt.location)
                define(stmt.name, stmt.type, immutable = true)
            }

            is Stmt.VarStmt -> {
                ensureNotDefinedInCurrentScope(stmt.name, stmt.location)
                val exprType = inferExprType(stmt.expr)
                ensureAssignable(stmt.type, exprType, stmt.location)
                define(stmt.name, stmt.type, immutable = false)
            }

            is Stmt.AssignStmt -> {
                val variable = lookup(stmt.name)
                    ?: fail(stmt.location, "Undefined variable '${stmt.name}'.")
                if (variable.immutable) {
                    fail(stmt.location, "Cannot reassign immutable variable '${stmt.name}'.")
                }
                val exprType = inferExprType(stmt.expr)
                ensureAssignable(variable.type, exprType, stmt.location)
            }

            is Stmt.PrintStmt -> {
                inferExprType(stmt.expr)
            }

            is Stmt.IfStmt -> {
                val conditionType = inferExprType(stmt.condition)
                if (conditionType != Type.BOOL) {
                    fail(stmt.condition.location, "If condition must be Bool, got $conditionType.")
                }
                enterScope()
                try {
                    stmt.thenBranch.forEach { checkStatement(it) }
                } finally {
                    exitScope()
                }

                stmt.elseBranch?.let { elseBranch ->
                    enterScope()
                    try {
                        elseBranch.forEach { checkStatement(it) }
                    } finally {
                        exitScope()
                    }
                }
            }

            is Stmt.WhileStmt -> {
                val conditionType = inferExprType(stmt.condition)
                if (conditionType != Type.BOOL) {
                    fail(stmt.condition.location, "While condition must be Bool, got $conditionType.")
                }
                loopDepth++
                enterScope()
                try {
                    stmt.body.forEach { checkStatement(it) }
                } finally {
                    exitScope()
                    loopDepth--
                }
            }

            is Stmt.ForStmt -> {
                enterScope()
                try {
                    stmt.init?.let { checkStatement(it) }

                    stmt.condition?.let { cond ->
                        val conditionType = inferExprType(cond)
                        if (conditionType != Type.BOOL) {
                            fail(cond.location, "For-loop condition must be Bool, got $conditionType.")
                        }
                    }

                    loopDepth++
                    try {
                        stmt.body.forEach { checkStatement(it) }
                        stmt.update?.let { checkForUpdate(it) }
                    } finally {
                        loopDepth--
                    }
                } finally {
                    exitScope()
                }
            }

            is Stmt.BreakStmt -> {
                if (loopDepth <= 0) {
                    fail(stmt.location, "'break' is only allowed inside a loop.")
                }
            }

            is Stmt.ContinueStmt -> {
                if (loopDepth <= 0) {
                    fail(stmt.location, "'continue' is only allowed inside a loop.")
                }
            }

            is Stmt.FunctionDef -> {
                val info = functions[stmt.name] ?: fail(stmt.location, "Unknown function '${stmt.name}'.")
                val previousReturnType = currentReturnType
                currentReturnType = info.returnType

                enterScope()
                try {
                    stmt.params.forEach { param ->
                        ensureNotDefinedInCurrentScope(param.name, param.location)
                        define(param.name, param.type, immutable = false)
                    }
                    stmt.body.forEach { checkStatement(it) }
                } finally {
                    exitScope()
                    currentReturnType = previousReturnType
                }
            }

            is Stmt.ReturnStmt -> {
                val expected = currentReturnType
                    ?: fail(stmt.location, "Return statement is only allowed inside a function.")
                val actual = stmt.expr?.let { inferExprType(it) } ?: Type.VOID
                ensureAssignable(expected, actual, stmt.location)
            }

            is Stmt.ExprStmt -> {
                inferExprType(stmt.expr)
            }
        }
    }

    private fun checkForUpdate(stmt: Stmt) {
        when (stmt) {
            is Stmt.AssignStmt -> checkStatement(stmt)
            is Stmt.ExprStmt -> {
                if (stmt.expr !is Expr.Call) {
                    fail(stmt.location, "For-loop update must be an assignment or function call.")
                }
                inferExprType(stmt.expr)
            }
            else -> fail(stmt.location, "For-loop update must be an assignment or function call.")
        }
    }

    private fun inferExprType(expr: Expr): Type {
        return when (expr) {
            is Expr.Number -> Type.INT
            is Expr.FloatLiteral -> Type.FLOAT
            is Expr.BoolLiteral -> Type.BOOL
            is Expr.StringLiteral -> Type.STRING
            is Expr.Variable -> lookup(expr.name)?.type
                ?: fail(expr.location, "Undefined variable '${expr.name}'.")

            is Expr.UnaryOp -> {
                val operandType = inferExprType(expr.operand)
                when (expr.operator) {
                    "!" -> {
                        if (operandType != Type.BOOL) {
                            fail(expr.location, "NOT operator requires Bool operand, got $operandType.")
                        }
                        Type.BOOL
                    }
                    else -> fail(expr.location, "Unknown unary operator '${expr.operator}'.")
                }
            }

            is Expr.BinaryOp -> {
                if (expr.operator == "&&" || expr.operator == "||") {
                    val left = inferExprType(expr.left)
                    val right = inferExprType(expr.right)
                    if (left != Type.BOOL || right != Type.BOOL) {
                        fail(expr.location, "Logical operators require Bool operands.")
                    }
                    return Type.BOOL
                }

                val leftType = inferExprType(expr.left)
                val rightType = inferExprType(expr.right)
                when (expr.operator) {
                    "+" -> when {
                        leftType == Type.INT && rightType == Type.INT -> Type.INT
                        isNumeric(leftType) && isNumeric(rightType) -> Type.FLOAT
                        else -> fail(expr.location, "Unsupported operand types for '+': $leftType and $rightType.")
                    }
                    "-", "*", "/" -> when {
                        leftType == Type.INT && rightType == Type.INT -> Type.INT
                        isNumeric(leftType) && isNumeric(rightType) -> Type.FLOAT
                        else -> fail(expr.location, "Unsupported operand types for '${expr.operator}': $leftType and $rightType.")
                    }
                    "%" -> {
                        if (leftType != Type.INT || rightType != Type.INT) {
                            fail(expr.location, "Modulo requires Int operands.")
                        }
                        Type.INT
                    }
                    ">", "<", ">=", "<=" -> {
                        if (!isNumeric(leftType) || !isNumeric(rightType)) {
                            fail(expr.location, "Comparison '${expr.operator}' requires numeric operands.")
                        }
                        Type.BOOL
                    }
                    "==", "!=" -> {
                        if (leftType != rightType && !(isNumeric(leftType) && isNumeric(rightType))) {
                            fail(expr.location, "Equality '${expr.operator}' requires compatible operand types.")
                        }
                        Type.BOOL
                    }
                    else -> fail(expr.location, "Unknown operator '${expr.operator}'.")
                }
            }

            is Expr.Call -> {
                if (expr.name == "len") {
                    if (expr.args.size != 1) {
                        fail(expr.location, "len expects 1 argument, got ${expr.args.size}.")
                    }
                    val argType = inferExprType(expr.args[0])
                    if (argType != Type.LIST && argType != Type.STRING) {
                        fail(expr.args[0].location, "len expects a List or String argument, got $argType.")
                    }
                    return Type.INT
                }

                val fn = functions[expr.name]
                    ?: fail(expr.location, "Undefined function '${expr.name}'.")
                if (expr.args.size != fn.paramTypes.size) {
                    fail(expr.location, "Function '${expr.name}' expects ${fn.paramTypes.size} arguments, got ${expr.args.size}.")
                }

                expr.args.forEachIndexed { index, arg ->
                    val argType = inferExprType(arg)
                    ensureAssignable(fn.paramTypes[index], argType, arg.location)
                }
                fn.returnType
            }

            is Expr.ListLiteral -> {
                expr.elements.forEachIndexed { index, element ->
                    val elementType = inferExprType(element)
                    if (elementType != Type.INT) {
                        fail(element.location, "List element at index $index must be Int, got $elementType.")
                    }
                }
                Type.LIST
            }

            is Expr.Index -> {
                val targetType = inferExprType(expr.target)
                if (targetType != Type.LIST) {
                    fail(expr.target.location, "Indexing is only supported on List values.")
                }
                val indexType = inferExprType(expr.index)
                if (indexType != Type.INT) {
                    fail(expr.index.location, "List index must be Int, got $indexType.")
                }
                Type.INT
            }
        }
    }

    private fun ensureAssignable(expected: Type, actual: Type, location: SourceLocation) {
        if (expected == actual) {
            return
        }
        if (expected == Type.FLOAT && actual == Type.INT) {
            return
        }
        fail(location, "Type mismatch: expected $expected, got $actual.")
    }

    private fun isNumeric(type: Type): Boolean = type == Type.INT || type == Type.FLOAT

    private fun ensureNotDefinedInCurrentScope(name: String, location: SourceLocation) {
        if (lookupCurrentScope(name) != null) {
            fail(location, "Variable '$name' is already defined in this scope.")
        }
    }

    private fun enterScope() {
        scopes.add(mutableMapOf())
    }

    private fun exitScope() {
        if (scopes.isNotEmpty()) {
            scopes.removeAt(scopes.size - 1)
        }
    }

    private fun define(name: String, type: Type, immutable: Boolean) {
        scopes.last()[name] = VariableInfo(type, immutable)
    }

    private fun lookup(name: String): VariableInfo? {
        for (scope in scopes.asReversed()) {
            scope[name]?.let { return it }
        }
        return null
    }

    private fun lookupCurrentScope(name: String): VariableInfo? {
        return scopes.lastOrNull()?.get(name)
    }

    private fun fail(location: SourceLocation, message: String): Nothing {
        throw RuntimeException(location.format(message))
    }
}
