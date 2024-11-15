package nl.endevelopment.semantic

enum class Type {
    INT,
    FLOAT,
    BOOL,
    STRING,
    VOID
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
