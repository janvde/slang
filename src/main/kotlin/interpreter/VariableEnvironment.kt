// VariableEnvironment.kt
package nl.endevelopment.interpreter

class VariableEnvironment {
    private val scopes: MutableList<MutableMap<String, Value>> = mutableListOf()

    /**
     * Enters a new scope by adding a new empty map.
     */
    fun enterScope() {
        scopes.add(mutableMapOf())
    }

    /**
     * Exits the current scope by removing the last map.
     */
    fun exitScope() {
        if (scopes.isNotEmpty()) scopes.removeAt(scopes.size - 1)
    }

    /**
     * Defines a new variable in the current scope.
     * Throws an exception if no scope is available.
     */
    fun define(name: String, value: Value) {
        if (scopes.isNotEmpty()) {
            scopes.last()[name] = value
        } else {
            throw Exception("No scope available to define variable '$name'.")
        }
    }

    /**
     * Retrieves the value of a variable by searching from the innermost to outermost scope.
     * Throws an exception if the variable is not found.
     */
    fun get(name: String): Value {
        for (scope in scopes.asReversed()) {
            scope[name]?.let { return it }
        }
        throw RuntimeException("Undefined variable '$name'.")
    }

    /**
     * Retrieves the value of a variable in the current scope only.
     * Returns null if the variable is not found in the current scope.
     */
    fun getInCurrentScope(name: String): Value? {
        return if (scopes.isNotEmpty()) {
            scopes.last()[name]
        } else {
            null
        }
    }
}
