package nl.endevelopment.interpreter

import nl.endevelopment.ast.SourceLocation

class VariableEnvironment {
    private val scopes: MutableList<MutableMap<String, Value>> = mutableListOf()
    private val immutables: MutableSet<String> = mutableSetOf()

    fun enterScope() {
        scopes.add(mutableMapOf())
    }

    fun exitScope() {
        if (scopes.isNotEmpty()) {
            val exitingScope = scopes.removeAt(scopes.size - 1)
            immutables.removeAll(exitingScope.keys)
        }
    }

    fun define(name: String, value: Value, immutable: Boolean = true) {
        if (scopes.isNotEmpty()) {
            scopes.last()[name] = value
            if (immutable) {
                immutables.add(name)
            }
        } else {
            throw RuntimeException("No scope available to define variable '$name'.")
        }
    }

    fun get(name: String, location: SourceLocation = SourceLocation.UNKNOWN): Value {
        for (scope in scopes.asReversed()) {
            scope[name]?.let { return it }
        }
        throw RuntimeException(location.format("Undefined variable '$name'."))
    }

    fun set(name: String, value: Value, location: SourceLocation = SourceLocation.UNKNOWN) {
        if (immutables.contains(name)) {
            throw RuntimeException(location.format("Cannot reassign immutable variable '$name'."))
        }

        for (scope in scopes.asReversed()) {
            if (scope.containsKey(name)) {
                scope[name] = value
                return
            }
        }
        throw RuntimeException(location.format("Undefined variable '$name'."))
    }

    fun getInCurrentScope(name: String): Value? {
        return scopes.lastOrNull()?.get(name)
    }
}
