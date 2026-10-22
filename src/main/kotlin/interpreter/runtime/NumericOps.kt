package nl.endevelopment.interpreter.runtime

import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.interpreter.Value

class NumericOps {
    fun add(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.IntValue(left.value + right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.FloatValue(left.value + right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.FloatValue(left.value.toFloat() + right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.FloatValue(left.value + right.value.toFloat())
            left is Value.StringValue && right is Value.StringValue -> Value.StringValue(left.value + right.value)
            else -> throw RuntimeException(location.format("Unsupported operand types for '+': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    fun subtract(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.IntValue(left.value - right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.FloatValue(left.value - right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.FloatValue(left.value.toFloat() - right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.FloatValue(left.value - right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '-': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    fun multiply(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.IntValue(left.value * right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.FloatValue(left.value * right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.FloatValue(left.value.toFloat() * right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.FloatValue(left.value * right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '*': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    fun divide(left: Value, right: Value, location: SourceLocation): Value {
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

    fun modulo(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> {
                if (right.value == 0) throw RuntimeException(location.format("Modulo by zero."))
                Value.IntValue(left.value % right.value)
            }
            else -> throw RuntimeException(location.format("Unsupported operand types for '%': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    fun lessThan(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value < right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value < right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.BoolValue(left.value.toFloat() < right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.BoolValue(left.value < right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '<': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    fun lessThanOrEqual(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value <= right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value <= right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.BoolValue(left.value.toFloat() <= right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.BoolValue(left.value <= right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '<=': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    fun greaterThan(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value > right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value > right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.BoolValue(left.value.toFloat() > right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.BoolValue(left.value > right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '>': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    fun greaterThanOrEqual(left: Value, right: Value, location: SourceLocation): Value {
        return when {
            left is Value.IntValue && right is Value.IntValue -> Value.BoolValue(left.value >= right.value)
            left is Value.FloatValue && right is Value.FloatValue -> Value.BoolValue(left.value >= right.value)
            left is Value.IntValue && right is Value.FloatValue -> Value.BoolValue(left.value.toFloat() >= right.value)
            left is Value.FloatValue && right is Value.IntValue -> Value.BoolValue(left.value >= right.value.toFloat())
            else -> throw RuntimeException(location.format("Unsupported operand types for '>=': ${left::class.simpleName} and ${right::class.simpleName}."))
        }
    }

    fun isNumericValue(value: Value): Boolean {
        return value is Value.IntValue || value is Value.FloatValue
    }

    fun asFloat(value: Value): Float {
        return when (value) {
            is Value.IntValue -> value.value.toFloat()
            is Value.FloatValue -> value.value
            else -> throw IllegalArgumentException("Expected numeric value.")
        }
    }
}
