// Value.kt
package nl.endevelopment.interpreter

sealed class Value {
    data class IntValue(val value: Int) : Value()
    data class FloatValue(val value: Float) : Value()
    data class BoolValue(val value: Boolean) : Value()
    data class StringValue(val value: String) : Value()
    data class ListValue(val elements: List<Value>) : Value()
    data class ObjectValue(val className: String, val fields: MutableMap<String, Value>) : Value()
    object VoidValue : Value()
}
