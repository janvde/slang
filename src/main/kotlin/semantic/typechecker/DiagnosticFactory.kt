package nl.endevelopment.semantic.typechecker

import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.semantic.Type

internal object DiagnosticFactory {
    fun fail(location: SourceLocation, message: String): Nothing {
        throw RuntimeException(location.format(message))
    }

    fun failWithRelated(location: SourceLocation, message: String, related: SourceLocation): Nothing {
        val details = if (related.line > 0 && related.column > 0) {
            "$message Note: previous declaration is at line ${related.line}, col ${related.column}."
        } else {
            message
        }
        fail(location, details)
    }

    fun formatType(type: Type): String = type.toString()

    fun nameForOperator(type: Type): String {
        return when (type) {
            Type.INT -> "INT"
            Type.FLOAT -> "FLOAT"
            Type.BOOL -> "BOOL"
            Type.STRING -> "STRING"
            is Type.LIST -> "LIST"
            Type.VOID -> "VOID"
            is Type.CLASS -> type.name
        }
    }
}
