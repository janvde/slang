package nl.endevelopment.interpreter

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.MethodDef
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type

class Interpreter {

    private data class ClassFieldInfo(val mutable: Boolean)
    private data class ClassRuntimeInfo(
        val fieldOrder: List<String>,
        val fieldInfo: Map<String, ClassFieldInfo>,
        val methods: Map<String, MethodDef>
    )

    private val variableEnv = VariableEnvironment()
    private val functions = mutableMapOf<String, Stmt.FunctionDef>()
    private val classes = mutableMapOf<String, ClassRuntimeInfo>()
    private var functionDepth = 0
    private var loopDepth = 0

    private class ReturnSignal(val value: Value) : RuntimeException(null, null, false, false)
    private class BreakSignal : RuntimeException(null, null, false, false)
    private class ContinueSignal : RuntimeException(null, null, false, false)

    fun interpret(program: Program) {
        variableEnv.enterScope()
        try {
            program.statements.filterIsInstance<Stmt.ClassDef>().forEach { registerClass(it) }
            program.statements.filterIsInstance<Stmt.FunctionDef>().forEach { registerFunction(it) }
            program.statements.filterNot { it is Stmt.FunctionDef || it is Stmt.ClassDef }.forEach { execute(it) }
        } catch (e: RuntimeException) {
            println("Runtime Error: ${e.message}")
        } finally {
            variableEnv.exitScope()
        }
    }

    private fun executeBlock(statements: List<Stmt>) {
        for (stmt in statements) {
            execute(stmt)
        }
    }

    private fun execute(stmt: Stmt) {
        when (stmt) {
            is Stmt.LetStmt -> handleLetStmt(stmt)
            is Stmt.VarStmt -> handleVarStmt(stmt)
            is Stmt.AssignStmt -> handleAssignStmt(stmt)
            is Stmt.MemberAssignStmt -> handleMemberAssignStmt(stmt)
            is Stmt.PrintStmt -> handlePrintStmt(stmt)
            is Stmt.IfStmt -> handleIfStmt(stmt)
            is Stmt.WhileStmt -> handleWhileStmt(stmt)
            is Stmt.ForStmt -> handleForStmt(stmt)
            is Stmt.BreakStmt -> handleBreakStmt(stmt)
            is Stmt.ContinueStmt -> handleContinueStmt(stmt)
            is Stmt.FunctionDef -> registerFunction(stmt)
            is Stmt.ReturnStmt -> handleReturnStmt(stmt)
            is Stmt.ExprStmt -> handleExprStmt(stmt)
            is Stmt.ClassDef -> registerClass(stmt)
        }
    }

    private fun handleWhileStmt(stmt: Stmt.WhileStmt) {
        variableEnv.enterScope()
        try {
            loopDepth++
            while (true) {
                val conditionValue = evaluate(stmt.condition, allowVoid = false)
                val conditionResult = evaluateCondition(conditionValue, stmt.condition.location)
                if (!conditionResult) {
                    break
                }

                try {
                    for (s in stmt.body) {
                        execute(s)
                    }
                } catch (_: ContinueSignal) {
                    continue
                } catch (e: BreakSignal) {
                    break
                } catch (e: ReturnSignal) {
                    throw e
                }
            }
        } finally {
            loopDepth--
            variableEnv.exitScope()
        }
    }

    private fun handleForStmt(stmt: Stmt.ForStmt) {
        variableEnv.enterScope()
        try {
            loopDepth++
            stmt.init?.let { execute(it) }
            while (true) {
                val shouldContinue = stmt.condition?.let {
                    evaluateCondition(evaluate(it, allowVoid = false), it.location)
                } ?: true

                if (!shouldContinue) {
                    break
                }

                try {
                    executeBlock(stmt.body)
                } catch (_: ContinueSignal) {
                    stmt.update?.let { execute(it) }
                    continue
                } catch (_: BreakSignal) {
                    break
                } catch (e: ReturnSignal) {
                    throw e
                }

                stmt.update?.let { execute(it) }
            }
        } finally {
            loopDepth--
            variableEnv.exitScope()
        }
    }

    private fun handleBreakStmt(stmt: Stmt.BreakStmt) {
        if (loopDepth <= 0) {
            throw RuntimeException(stmt.location.format("'break' is only allowed inside a loop."))
        }
        throw BreakSignal()
    }

    private fun handleContinueStmt(stmt: Stmt.ContinueStmt) {
        if (loopDepth <= 0) {
            throw RuntimeException(stmt.location.format("'continue' is only allowed inside a loop."))
        }
        throw ContinueSignal()
    }

    private fun handleLetStmt(stmt: Stmt.LetStmt) {
        val value = evaluate(stmt.expr, allowVoid = false)
        variableEnv.define(stmt.name, value, immutable = true)
    }

    private fun handleVarStmt(stmt: Stmt.VarStmt) {
        val value = evaluate(stmt.expr, allowVoid = false)
        variableEnv.define(stmt.name, value, immutable = false)
    }

    private fun handleAssignStmt(stmt: Stmt.AssignStmt) {
        val value = evaluate(stmt.expr, allowVoid = false)
        variableEnv.set(stmt.name, value, stmt.location)
    }

    private fun handleMemberAssignStmt(stmt: Stmt.MemberAssignStmt) {
        val target = evaluate(stmt.target, allowVoid = false)
        if (target !is Value.ObjectValue) {
            throw RuntimeException(stmt.target.location.format("Field assignment target must be a class instance."))
        }

        val classInfo = classes[target.className]
            ?: throw RuntimeException(stmt.location.format("Unknown class '${target.className}'."))
        val fieldInfo = classInfo.fieldInfo[stmt.member]
            ?: throw RuntimeException(stmt.location.format("Class '${target.className}' has no field '${stmt.member}'."))
        if (!fieldInfo.mutable) {
            throw RuntimeException(stmt.location.format("Cannot assign to immutable field '${stmt.member}' in class '${target.className}'."))
        }

        target.fields[stmt.member] = evaluate(stmt.expr, allowVoid = false)
    }

    private fun handlePrintStmt(stmt: Stmt.PrintStmt) {
        val value = evaluate(stmt.expr, allowVoid = false)
        println(valueToString(value))
    }

    private fun handleIfStmt(stmt: Stmt.IfStmt) {
        val conditionValue = evaluate(stmt.condition, allowVoid = false)
        val conditionResult = evaluateCondition(conditionValue, stmt.condition.location)

        variableEnv.enterScope()
        try {
            if (conditionResult) {
                executeBlock(stmt.thenBranch)
            } else if (stmt.elseBranch != null) {
                executeBlock(stmt.elseBranch)
            }
        } finally {
            variableEnv.exitScope()
        }
    }

    private fun evaluate(expr: Expr, allowVoid: Boolean = false): Value {
        return when (expr) {
            is Expr.Number -> Value.IntValue(expr.value)
            is Expr.FloatLiteral -> Value.FloatValue(expr.value)
            is Expr.BoolLiteral -> Value.BoolValue(expr.value)
            is Expr.StringLiteral -> Value.StringValue(expr.value)
            is Expr.This -> variableEnv.get("this", expr.location)
            is Expr.Variable -> variableEnv.get(expr.name, expr.location)
            is Expr.UnaryOp -> evaluateUnaryOp(expr)
            is Expr.BinaryOp -> evaluateBinaryOp(expr)
            is Expr.Call -> callFunction(expr, allowVoid)
            is Expr.MemberAccess -> evaluateMemberAccess(expr)
            is Expr.MemberCall -> callMemberFunction(expr, allowVoid)
            is Expr.ListLiteral -> Value.ListValue(expr.elements.map { evaluate(it, allowVoid = false) })
            is Expr.Index -> evaluateIndex(expr)
        }
    }

    private fun registerClass(stmt: Stmt.ClassDef) {
        if (classes.containsKey(stmt.name)) {
            throw RuntimeException(stmt.location.format("Class '${stmt.name}' is already defined."))
        }

        val fieldInfo = linkedMapOf<String, ClassFieldInfo>()
        val fieldOrder = mutableListOf<String>()
        stmt.fields.forEach { field ->
            if (fieldInfo.containsKey(field.name)) {
                throw RuntimeException(field.location.format("Field '${field.name}' is already defined in class '${stmt.name}'."))
            }
            fieldInfo[field.name] = ClassFieldInfo(field.mutable)
            fieldOrder.add(field.name)
        }

        val methods = linkedMapOf<String, MethodDef>()
        stmt.methods.forEach { method ->
            if (methods.containsKey(method.name)) {
                throw RuntimeException(method.location.format("Method '${method.name}' is already defined in class '${stmt.name}'."))
            }
            methods[method.name] = method
        }

        classes[stmt.name] = ClassRuntimeInfo(
            fieldOrder = fieldOrder,
            fieldInfo = fieldInfo,
            methods = methods
        )
    }

    private fun registerFunction(stmt: Stmt.FunctionDef) {
        if (stmt.name == "len") {
            throw RuntimeException(stmt.location.format("Cannot redefine built-in function 'len'."))
        }
        if (functions.containsKey(stmt.name)) {
            throw RuntimeException(stmt.location.format("Function '${stmt.name}' is already defined."))
        }
        functions[stmt.name] = stmt
    }

    private fun callFunction(expr: Expr.Call, allowVoid: Boolean): Value {
        if (expr.name == "len") {
            return evalLen(expr)
        }

        classes[expr.name]?.let { classInfo ->
            return constructObject(expr, classInfo)
        }

        val function = functions[expr.name]
            ?: throw RuntimeException(expr.location.format("Undefined function '${expr.name}'."))

        if (expr.args.size != function.params.size) {
            throw RuntimeException(expr.location.format("Function '${expr.name}' expects ${function.params.size} arguments, got ${expr.args.size}."))
        }
        if (!allowVoid && function.returnType == Type.VOID) {
            throw RuntimeException(expr.location.format("Cannot use void function '${expr.name}' in an expression."))
        }

        val argValues = expr.args.map { evaluate(it, allowVoid = false) }

        functionDepth++
        variableEnv.enterScope()
        try {
            function.params.forEachIndexed { index, param ->
                variableEnv.define(param.name, argValues[index], immutable = false)
            }
            try {
                executeBlock(function.body)
            } catch (signal: ReturnSignal) {
                validateReturnType(function.returnType, signal.value, expr.location)
                return signal.value
            }
        } finally {
            variableEnv.exitScope()
            functionDepth--
        }

        if (function.returnType != Type.VOID) {
            throw RuntimeException(expr.location.format("Function '${function.name}' must return a value."))
        }
        return Value.VoidValue
    }

    private fun callMemberFunction(expr: Expr.MemberCall, allowVoid: Boolean): Value {
        val receiver = evaluate(expr.receiver, allowVoid = false)
        if (receiver !is Value.ObjectValue) {
            throw RuntimeException(expr.receiver.location.format("Method call requires a class instance receiver."))
        }

        val classInfo = classes[receiver.className]
            ?: throw RuntimeException(expr.location.format("Unknown class '${receiver.className}'."))
        val method = classInfo.methods[expr.method]
            ?: throw RuntimeException(expr.location.format("Class '${receiver.className}' has no method '${expr.method}'."))

        if (expr.args.size != method.params.size) {
            throw RuntimeException(expr.location.format("Method '${expr.method}' expects ${method.params.size} arguments, got ${expr.args.size}."))
        }
        if (!allowVoid && method.returnType == Type.VOID) {
            throw RuntimeException(expr.location.format("Cannot use void method '${expr.method}' in an expression."))
        }

        val argValues = expr.args.map { evaluate(it, allowVoid = false) }

        functionDepth++
        variableEnv.enterScope()
        try {
            variableEnv.define("this", receiver, immutable = true)
            method.params.forEachIndexed { index, param ->
                variableEnv.define(param.name, argValues[index], immutable = false)
            }
            try {
                executeBlock(method.body)
            } catch (signal: ReturnSignal) {
                validateReturnType(method.returnType, signal.value, expr.location)
                return signal.value
            }
        } finally {
            variableEnv.exitScope()
            functionDepth--
        }

        if (method.returnType != Type.VOID) {
            throw RuntimeException(expr.location.format("Method '${expr.method}' must return a value."))
        }
        return Value.VoidValue
    }

    private fun constructObject(expr: Expr.Call, classInfo: ClassRuntimeInfo): Value {
        if (expr.args.size != classInfo.fieldOrder.size) {
            throw RuntimeException(expr.location.format("Constructor '${expr.name}' expects ${classInfo.fieldOrder.size} arguments, got ${expr.args.size}."))
        }

        val fields = mutableMapOf<String, Value>()
        classInfo.fieldOrder.forEachIndexed { index, fieldName ->
            fields[fieldName] = evaluate(expr.args[index], allowVoid = false)
        }

        return Value.ObjectValue(expr.name, fields)
    }

    private fun handleReturnStmt(stmt: Stmt.ReturnStmt) {
        if (functionDepth <= 0) {
            throw RuntimeException(stmt.location.format("Return statement is only allowed inside a function."))
        }
        val value = stmt.expr?.let { evaluate(it, allowVoid = false) } ?: Value.VoidValue
        throw ReturnSignal(value)
    }

    private fun handleExprStmt(stmt: Stmt.ExprStmt) {
        evaluate(stmt.expr, allowVoid = true)
    }

    private fun evalLen(expr: Expr.Call): Value {
        if (expr.args.size != 1) {
            throw RuntimeException(expr.location.format("len expects 1 argument, got ${expr.args.size}."))
        }
        val value = evaluate(expr.args[0], allowVoid = false)
        return when (value) {
            is Value.ListValue -> Value.IntValue(value.elements.size)
            is Value.StringValue -> Value.IntValue(value.value.length)
            else -> throw RuntimeException(expr.args[0].location.format("len expects a List or String argument."))
        }
    }

    private fun evaluateMemberAccess(expr: Expr.MemberAccess): Value {
        val receiver = evaluate(expr.receiver, allowVoid = false)
        if (receiver !is Value.ObjectValue) {
            throw RuntimeException(expr.receiver.location.format("Member access requires a class instance receiver."))
        }

        return receiver.fields[expr.member]
            ?: throw RuntimeException(expr.location.format("Class '${receiver.className}' has no field '${expr.member}'."))
    }

    private fun evaluateIndex(expr: Expr.Index): Value {
        val target = evaluate(expr.target, allowVoid = false)
        val indexValue = evaluate(expr.index, allowVoid = false)
        if (indexValue !is Value.IntValue) {
            throw RuntimeException(expr.index.location.format("List index must be an Int."))
        }
        return when (target) {
            is Value.ListValue -> {
                val idx = indexValue.value
                if (idx < 0 || idx >= target.elements.size) {
                    throw RuntimeException(expr.index.location.format("List index out of bounds: $idx."))
                }
                target.elements[idx]
            }
            else -> throw RuntimeException(expr.target.location.format("Indexing is only supported on Lists."))
        }
    }

    private fun evaluateUnaryOp(expr: Expr.UnaryOp): Value {
        val operandValue = evaluate(expr.operand, allowVoid = false)
        return when (expr.operator) {
            "!" -> {
                if (operandValue !is Value.BoolValue) {
                    throw RuntimeException(expr.location.format("NOT operator requires Bool operand."))
                }
                Value.BoolValue(!operandValue.value)
            }
            else -> throw RuntimeException(expr.location.format("Unknown unary operator '${expr.operator}'."))
        }
    }

    private fun validateReturnType(expected: Type, value: Value, location: SourceLocation) {
        when (expected) {
            Type.INT -> if (value !is Value.IntValue) throw RuntimeException(location.format("Function return type mismatch. Expected Int."))
            Type.FLOAT -> if (value !is Value.FloatValue && value !is Value.IntValue) throw RuntimeException(location.format("Function return type mismatch. Expected Float."))
            Type.BOOL -> if (value !is Value.BoolValue) throw RuntimeException(location.format("Function return type mismatch. Expected Bool."))
            Type.STRING -> if (value !is Value.StringValue) throw RuntimeException(location.format("Function return type mismatch. Expected String."))
            Type.LIST -> if (value !is Value.ListValue) throw RuntimeException(location.format("Function return type mismatch. Expected List."))
            Type.VOID -> if (value !is Value.VoidValue) throw RuntimeException(location.format("Function return type mismatch. Expected void."))
            is Type.CLASS -> {
                if (value !is Value.ObjectValue || value.className != expected.name) {
                    throw RuntimeException(location.format("Function return type mismatch. Expected ${expected.name}."))
                }
            }
        }
    }

    private fun evaluateBinaryOp(expr: Expr.BinaryOp): Value {
        if (expr.operator == "&&") {
            val leftVal = evaluate(expr.left, allowVoid = false)
            if (leftVal !is Value.BoolValue) {
                throw RuntimeException(expr.location.format("AND operator requires Bool operands."))
            }
            if (!leftVal.value) {
                return Value.BoolValue(false)
            }
            val rightVal = evaluate(expr.right, allowVoid = false)
            if (rightVal !is Value.BoolValue) {
                throw RuntimeException(expr.location.format("AND operator requires Bool operands."))
            }
            return Value.BoolValue(rightVal.value)
        }

        if (expr.operator == "||") {
            val leftVal = evaluate(expr.left, allowVoid = false)
            if (leftVal !is Value.BoolValue) {
                throw RuntimeException(expr.location.format("OR operator requires Bool operands."))
            }
            if (leftVal.value) {
                return Value.BoolValue(true)
            }
            val rightVal = evaluate(expr.right, allowVoid = false)
            if (rightVal !is Value.BoolValue) {
                throw RuntimeException(expr.location.format("OR operator requires Bool operands."))
            }
            return Value.BoolValue(rightVal.value)
        }

        val leftVal = evaluate(expr.left, allowVoid = false)
        val rightVal = evaluate(expr.right, allowVoid = false)

        return when (expr.operator) {
            "+" -> add(leftVal, rightVal, expr.location)
            "-" -> subtract(leftVal, rightVal, expr.location)
            "*" -> multiply(leftVal, rightVal, expr.location)
            "/" -> divide(leftVal, rightVal, expr.location)
            "%" -> modulo(leftVal, rightVal, expr.location)
            "==" -> Value.BoolValue(leftVal == rightVal)
            "!=" -> Value.BoolValue(leftVal != rightVal)
            "<" -> lessThan(leftVal, rightVal, expr.location)
            "<=" -> lessThanOrEqual(leftVal, rightVal, expr.location)
            ">" -> greaterThan(leftVal, rightVal, expr.location)
            ">=" -> greaterThanOrEqual(leftVal, rightVal, expr.location)
            else -> throw RuntimeException(expr.location.format("Unknown operator '${expr.operator}'."))
        }
    }

    private fun add(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.IntValue(left.value + right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.FloatValue(left.value + right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.FloatValue(left.value.toFloat() + right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.FloatValue(left.value + right.value.toFloat())
            left is Value.StringValue && right is Value.StringValue -> Value.StringValue(left.value + right.value)
            else -> throw RuntimeException(location.format("Unsupported operand types for '+': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    private fun subtract(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.IntValue(left.value - right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.FloatValue(left.value - right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.FloatValue(left.value.toFloat() - right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.FloatValue(left.value - right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '-': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    private fun multiply(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.IntValue(left.value * right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.FloatValue(left.value * right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.FloatValue(left.value.toFloat() * right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.FloatValue(left.value * right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '*': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    private fun divide(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> {
                if (right.value == 0) throw RuntimeException(location.format("Division by zero."))
                Value.IntValue(left.value / right.value)
            }
            left is Value.FloatValue && right is Value.FloatValue -> {
                if (right.value == 0.0f) throw RuntimeException(location.format("Division by zero."))
                Value.FloatValue(left.value / right.value)
            }
            left is Value.IntValue && right is Value.FloatValue -> {
                if (right.value == 0.0f) throw RuntimeException(location.format("Division by zero."))
                Value.FloatValue(left.value.toFloat() / right.value)
            }
            left is Value.FloatValue && right is Value.IntValue -> {
                if (right.value == 0) throw RuntimeException(location.format("Division by zero."))
                Value.FloatValue(left.value / right.value.toFloat())
            }
            else -> throw RuntimeException(location.format("Unsupported operand types for '/': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    private fun modulo(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> {
                if (right.value == 0) throw RuntimeException(location.format("Modulo by zero."))
                Value.IntValue(left.value % right.value)
            }
            else -> throw RuntimeException(location.format("Unsupported operand types for '%': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    private fun lessThan(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value < right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value < right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.BoolValue(left.value.toFloat() < right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.BoolValue(left.value < right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '<': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    private fun lessThanOrEqual(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value <= right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value <= right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.BoolValue(left.value.toFloat() <= right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.BoolValue(left.value <= right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '<=': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    private fun greaterThan(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value > right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value > right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.BoolValue(left.value.toFloat() > right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.BoolValue(left.value > right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '>': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    private fun greaterThanOrEqual(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value >= right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value >= right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.BoolValue(left.value.toFloat() >= right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.BoolValue(left.value >= right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '>=': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    private fun valueToString(value: Value): String {
        return when (value) {
            is Value.IntValue -> value.value.toString()
            is Value.FloatValue -> value.value.toString()
            is Value.BoolValue -> value.value.toString()
            is Value.StringValue -> value.value
            is Value.ListValue -> value.elements.joinToString(prefix = "[", postfix = "]") { valueToString(it) }
            is Value.ObjectValue -> "<${value.className} object>"
            is Value.VoidValue -> "void"
        }
    }

    private fun evaluateCondition(value: Value, location: SourceLocation): Boolean {
        return when (value) {
            is Value.BoolValue -> value.value
            else -> throw RuntimeException(location.format("Condition must evaluate to Bool."))
        }
    }
}
