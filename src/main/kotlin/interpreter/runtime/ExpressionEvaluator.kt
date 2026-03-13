package nl.endevelopment.interpreter.runtime

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.interpreter.Value
import nl.endevelopment.semantic.Type

class ExpressionEvaluator(
    private val context: RuntimeContext,
    private val builtinRuntime: BuiltinRuntime,
    private val numericOps: NumericOps
) {
    lateinit var executeBlock: (List<nl.endevelopment.ast.Stmt>) -> Unit

    fun evaluate(expr: Expr, allowVoid: Boolean = false): Value {
        return when (expr) {
            is Expr.Number -> Value.IntValue(expr.value)
            is Expr.FloatLiteral -> Value.FloatValue(expr.value)
            is Expr.BoolLiteral -> Value.BoolValue(expr.value)
            is Expr.StringLiteral -> Value.StringValue(expr.value)
            is Expr.This -> context.variableEnv.get("this", expr.location)
            is Expr.Variable -> context.variableEnv.get(expr.name, expr.location)
            is Expr.UnaryOp -> evaluateUnaryOp(expr)
            is Expr.BinaryOp -> evaluateBinaryOp(expr)
            is Expr.Call -> callFunction(expr, allowVoid)
            is Expr.MemberAccess -> evaluateMemberAccess(expr)
            is Expr.MemberCall -> callMemberFunction(expr, allowVoid)
            is Expr.ListLiteral -> Value.ListValue(expr.elements.map { evaluate(it, allowVoid = false) })
            is Expr.Index -> evaluateIndex(expr)
        }
    }

    fun evaluateCondition(value: Value, location: SourceLocation): Boolean {
        return when (value) {
            is Value.BoolValue -> value.value
            else -> throw RuntimeException(location.format("Condition must evaluate to Bool."))
        }
    }

    fun valueToString(value: Value): String {
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

    private fun callFunction(expr: Expr.Call, allowVoid: Boolean): Value {
        if (expr.name in context.builtins) {
            return builtinRuntime.evalBuiltIn(expr) { argExpr -> evaluate(argExpr, allowVoid = false) }
        }

        context.classes[expr.name]?.let { classInfo ->
            return constructObject(expr, classInfo)
        }

        val function = context.functions[expr.name]
            ?: throw RuntimeException(expr.location.format("Undefined function '${expr.name}'."))

        if (expr.args.size != function.params.size) {
            throw RuntimeException(expr.location.format("Function '${expr.name}' expects ${function.params.size} arguments, got ${expr.args.size}."))
        }
        if (!allowVoid && function.returnType == Type.VOID) {
            throw RuntimeException(expr.location.format("Cannot use void function '${expr.name}' in an expression."))
        }

        val argValues = expr.args.map { evaluate(it, allowVoid = false) }

        context.functionDepth++
        context.variableEnv.enterScope()
        try {
            function.params.forEachIndexed { index, param ->
                context.variableEnv.define(param.name, argValues[index], immutable = false)
            }
            try {
                executeBlock(function.body)
            } catch (signal: ReturnSignal) {
                validateReturnType(function.returnType, signal.value, expr.location)
                return signal.value
            }
        } finally {
            context.variableEnv.exitScope()
            context.functionDepth--
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

        val classInfo = context.classes[receiver.className]
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

        context.functionDepth++
        context.variableEnv.enterScope()
        try {
            context.variableEnv.define("this", receiver, immutable = true)
            method.params.forEachIndexed { index, param ->
                context.variableEnv.define(param.name, argValues[index], immutable = false)
            }
            try {
                executeBlock(method.body)
            } catch (signal: ReturnSignal) {
                validateReturnType(method.returnType, signal.value, expr.location)
                return signal.value
            }
        } finally {
            context.variableEnv.exitScope()
            context.functionDepth--
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
            "+" -> numericOps.add(leftVal, rightVal, expr.location)
            "-" -> numericOps.subtract(leftVal, rightVal, expr.location)
            "*" -> numericOps.multiply(leftVal, rightVal, expr.location)
            "/" -> numericOps.divide(leftVal, rightVal, expr.location)
            "%" -> numericOps.modulo(leftVal, rightVal, expr.location)
            "==" -> Value.BoolValue(leftVal == rightVal)
            "!=" -> Value.BoolValue(leftVal != rightVal)
            "<" -> numericOps.lessThan(leftVal, rightVal, expr.location)
            "<=" -> numericOps.lessThanOrEqual(leftVal, rightVal, expr.location)
            ">" -> numericOps.greaterThan(leftVal, rightVal, expr.location)
            ">=" -> numericOps.greaterThanOrEqual(leftVal, rightVal, expr.location)
            else -> throw RuntimeException(expr.location.format("Unknown operator '${expr.operator}'."))
        }
    }

    private fun validateReturnType(expected: Type, value: Value, location: SourceLocation) {
        if (!valueMatchesType(expected, value)) {
            throw RuntimeException(
                location.format("Function return type mismatch. Expected $expected, got ${inferRuntimeType(value)}.")
            )
        }
    }

    private fun valueMatchesType(expected: Type, value: Value): Boolean {
        return when (expected) {
            Type.INT -> value is Value.IntValue
            Type.FLOAT -> value is Value.FloatValue || value is Value.IntValue
            Type.BOOL -> value is Value.BoolValue
            Type.STRING -> value is Value.StringValue
            is Type.LIST -> {
                if (value !is Value.ListValue) {
                    return false
                }
                val elementType = expected.elementType
                if (elementType == null) {
                    return true
                }
                value.elements.all { valueMatchesType(elementType, it) }
            }

            Type.VOID -> value is Value.VoidValue
            is Type.CLASS -> value is Value.ObjectValue && value.className == expected.name
        }
    }

    private fun inferRuntimeType(value: Value): String {
        return when (value) {
            is Value.IntValue -> "Int"
            is Value.FloatValue -> "Float"
            is Value.BoolValue -> "Bool"
            is Value.StringValue -> "String"
            is Value.ListValue -> {
                if (value.elements.isEmpty()) {
                    "List"
                } else {
                    val first = inferRuntimeType(value.elements.first())
                    "List[$first]"
                }
            }

            is Value.ObjectValue -> value.className
            is Value.VoidValue -> "Void"
        }
    }
}
