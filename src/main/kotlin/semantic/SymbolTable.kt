package nl.endevelopment.semantic

sealed class Type {
    data object INT : Type()
    data object FLOAT : Type()
    data object BOOL : Type()
    data object STRING : Type()
    data class LIST(val elementType: Type? = null) : Type()
    data object VOID : Type()
    data class CLASS(val name: String) : Type()

    companion object {
        fun fromName(name: String): Type {
            return when (name) {
                "Int" -> INT
                "Float" -> FLOAT
                "Bool" -> BOOL
                "String" -> STRING
                // Backward-compatible bare List defaults to List[Int].
                "List" -> LIST(INT)
                "Void" -> VOID
                else -> CLASS(name)
            }
        }
    }

    override fun toString(): String {
        return when (this) {
            INT -> "Int"
            FLOAT -> "Float"
            BOOL -> "Bool"
            STRING -> "String"
            is LIST -> elementType?.let { "List[$it]" } ?: "List"
            VOID -> "Void"
            is CLASS -> name
        }
    }
}

class SemanticSymbolTable {
    private val scopes: MutableList<MutableMap<String, Type>> = mutableListOf()

    fun enterScope() {
        scopes.add(mutableMapOf())
    }

    fun exitScope() {
        if (scopes.isNotEmpty()) scopes.removeAt(scopes.size - 1)
    }

    fun define(name: String, type: Type) {
        if (scopes.isNotEmpty()) {
            scopes.last()[name] = type
        } else {
            throw Exception("No scope available to define variable '$name'.")
        }
    }

    fun lookup(name: String): Type? {
        for (scope in scopes.asReversed()) {
            scope[name]?.let { return it }
        }
        return null
    }

    fun lookupInCurrentScope(name: String): Type? {
        return if (scopes.isNotEmpty()) {
            scopes.last()[name]
        } else {
            null
        }
    }
}
