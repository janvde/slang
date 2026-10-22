package nl.endevelopment.semantic.core

import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.semantic.Type

object TypeRules {
    fun isNumeric(type: Type): Boolean = type == Type.INT || type == Type.FLOAT

    fun ensureAssignable(
        expected: Type,
        actual: Type,
        location: SourceLocation,
        formatType: (Type) -> String,
        fail: (SourceLocation, String) -> Nothing
    ) {
        if (expected == actual) return

        if (expected == Type.FLOAT && actual == Type.INT) return

        if (expected is Type.LIST && actual is Type.LIST) {
            if (expected.elementType == null || actual.elementType == null) return
            if (expected.elementType == actual.elementType) return
            fail(location, "Type mismatch: expected ${formatType(expected)}, got ${formatType(actual)}.")
        }

        fail(location, "Type mismatch: expected ${formatType(expected)}, got ${formatType(actual)}.")
    }

    fun mergeListElementTypes(
        current: Type,
        next: Type,
        index: Int,
        location: SourceLocation,
        formatType: (Type) -> String,
        fail: (SourceLocation, String) -> Nothing
    ): Type {
        if (current == next) return current
        if (isNumeric(current) && isNumeric(next)) return Type.FLOAT
        fail(location, "List element at index $index has type ${formatType(next)}, expected ${formatType(current)}.")
    }

    fun isTypeCompatibleForList(expectedElement: Type, actualElement: Type): Boolean {
        if (expectedElement == actualElement) return true
        return expectedElement == Type.FLOAT && actualElement == Type.INT
    }
}
