package nl.endevelopment.interpreter.runtime

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.interpreter.Value
import kotlin.math.abs

class BuiltinRuntime(
    private val numericOps: NumericOps
) {
    fun evalBuiltIn(expr: Expr.Call, evaluate: (Expr) -> Value): Value {
        return when (expr.name) {
            "len" -> evalLen(expr, evaluate)
            "substr" -> evalSubstr(expr, evaluate)
            "contains" -> evalContains(expr, evaluate)
            "to_int" -> evalToInt(expr, evaluate)
            "min" -> evalMin(expr, evaluate)
            "max" -> evalMax(expr, evaluate)
            "abs" -> evalAbs(expr, evaluate)
            else -> throw RuntimeException(expr.location.format("Unknown built-in function '${expr.name}'."))
        }
    }

    private fun evalLen(expr: Expr.Call, evaluate: (Expr) -> Value): Value {
        if (expr.args.size != 1) {
            throw RuntimeException(expr.location.format("len expects 1 argument, got ${expr.args.size}."))
        }
        val value = evaluate(expr.args[0])
        return when (value) {
            is Value.ListValue -> Value.IntValue(value.elements.size)
            is Value.StringValue -> Value.IntValue(value.value.length)
            else -> throw RuntimeException(expr.args[0].location.format("len expects a List or String argument."))
        }
    }

    private fun evalSubstr(expr: Expr.Call, evaluate: (Expr) -> Value): Value {
        if (expr.args.size != 3) {
            throw RuntimeException(expr.location.format("substr expects 3 arguments, got ${expr.args.size}."))
        }
        val source = evaluate(expr.args[0]) as? Value.StringValue
            ?: throw RuntimeException(expr.args[0].location.format("substr expects argument 1 to be String."))
        val start = evaluate(expr.args[1]) as? Value.IntValue
            ?: throw RuntimeException(expr.args[1].location.format("substr expects argument 2 to be Int."))
        val length = evaluate(expr.args[2]) as? Value.IntValue
            ?: throw RuntimeException(expr.args[2].location.format("substr expects argument 3 to be Int."))

        val startIndex = start.value
        val lengthValue = length.value
        val endIndex = startIndex + lengthValue
        if (startIndex < 0 || lengthValue < 0 || startIndex > source.value.length || endIndex > source.value.length) {
            throw RuntimeException(
                expr.location.format(
                    "substr indices out of bounds: start=$startIndex, length=$lengthValue, source length=${source.value.length}."
                )
            )
        }

        return Value.StringValue(source.value.substring(startIndex, endIndex))
    }

    private fun evalContains(expr: Expr.Call, evaluate: (Expr) -> Value): Value {
        if (expr.args.size != 2) {
            throw RuntimeException(expr.location.format("contains expects 2 arguments, got ${expr.args.size}."))
        }
        val source = evaluate(expr.args[0]) as? Value.StringValue
            ?: throw RuntimeException(expr.args[0].location.format("contains expects argument 1 to be String."))
        val needle = evaluate(expr.args[1]) as? Value.StringValue
            ?: throw RuntimeException(expr.args[1].location.format("contains expects argument 2 to be String."))
        return Value.BoolValue(source.value.contains(needle.value))
    }

    private fun evalToInt(expr: Expr.Call, evaluate: (Expr) -> Value): Value {
        if (expr.args.size != 1) {
            throw RuntimeException(expr.location.format("to_int expects 1 argument, got ${expr.args.size}."))
        }
        val source = evaluate(expr.args[0]) as? Value.StringValue
            ?: throw RuntimeException(expr.args[0].location.format("to_int expects a String argument."))

        val parsed = source.value.trim().toIntOrNull()
            ?: throw RuntimeException(expr.location.format("to_int could not parse '${source.value}' as Int."))
        return Value.IntValue(parsed)
    }

    private fun evalMin(expr: Expr.Call, evaluate: (Expr) -> Value): Value {
        if (expr.args.size != 2) {
            throw RuntimeException(expr.location.format("min expects 2 arguments, got ${expr.args.size}."))
        }
        val left = evaluate(expr.args[0])
        val right = evaluate(expr.args[1])
        return minMaxValue(expr.location, left, right, chooseMin = true)
    }

    private fun evalMax(expr: Expr.Call, evaluate: (Expr) -> Value): Value {
        if (expr.args.size != 2) {
            throw RuntimeException(expr.location.format("max expects 2 arguments, got ${expr.args.size}."))
        }
        val left = evaluate(expr.args[0])
        val right = evaluate(expr.args[1])
        return minMaxValue(expr.location, left, right, chooseMin = false)
    }

    private fun minMaxValue(location: SourceLocation, left: Value, right: Value, chooseMin: Boolean): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> {
                if (chooseMin) Value.IntValue(minOf(left.value, right.value))
                else Value.IntValue(maxOf(left.value, right.value))
            }

            numericOps.isNumericValue(left) && numericOps.isNumericValue(right) -> {
                val leftFloat = numericOps.asFloat(left)
                val rightFloat = numericOps.asFloat(right)
                if (chooseMin) Value.FloatValue(minOf(leftFloat, rightFloat))
                else Value.FloatValue(maxOf(leftFloat, rightFloat))
            }

            else -> throw RuntimeException(location.format("min/max expects numeric arguments."))
        }
    }

    private fun evalAbs(expr: Expr.Call, evaluate: (Expr) -> Value): Value {
        if (expr.args.size != 1) {
            throw RuntimeException(expr.location.format("abs expects 1 argument, got ${expr.args.size}."))
        }

        return when (val value = evaluate(expr.args[0])) {
            is Value.IntValue -> Value.IntValue(abs(value.value))
            is Value.FloatValue -> Value.FloatValue(abs(value.value))
            else -> throw RuntimeException(expr.args[0].location.format("abs expects a numeric argument."))
        }
    }
}
