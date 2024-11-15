// Value.kt
package nl.endevelopment.interpreter

sealed class Value {
    data class IntValue(val value: Int) : Value()
    data class FloatValue(val value: Float) : Value()
    data class BoolValue(val value: Boolean) : Value()
    data class StringValue(val value: String) : Value()
    object VoidValue : Value()
}
