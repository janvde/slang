// VariableEnvironment.kt
package nl.endevelopment.interpreter

class VariableEnvironment {
    private val scopes: MutableList<MutableMap<String, Value>> = mutableListOf()
    private val immutables: MutableSet<String> = mutableSetOf()

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
        if (scopes.isNotEmpty()) {
            val exitingScope = scopes.removeAt(scopes.size - 1)
            // Remove immutability markers for variables in the exiting scope
            immutables.removeAll(exitingScope.keys)
        }
    }

    /**
     * Defines a new variable in the current scope.
     * Throws an exception if no scope is available.
     */
    fun define(name: String, value: Value, immutable: Boolean = true) {
        if (scopes.isNotEmpty()) {
            scopes.last()[name] = value
            if (immutable) {
                immutables.add(name)
            }
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
     * Sets the value of an existing variable. Checks immutability constraint.
     * Throws an exception if the variable is not found or is immutable.
     */
    fun set(name: String, value: Value) {
        if (immutables.contains(name)) {
            throw RuntimeException("Cannot reassign immutable variable '$name'.")
        }

        for (scope in scopes.asReversed()) {
            if (scope.containsKey(name)) {
                scope[name] = value
                return
            }
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
