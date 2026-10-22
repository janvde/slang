package nl.endevelopment.semantic.typechecker

import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.semantic.Type

internal class ScopeStack {
    private val scopes = mutableListOf<MutableMap<String, VariableInfo>>()

    fun clear() {
        scopes.clear()
    }

    fun enterScope() {
        scopes.add(mutableMapOf())
    }

    fun exitScope() {
        if (scopes.isNotEmpty()) {
            scopes.removeAt(scopes.size - 1)
        }
    }

    fun define(name: String, type: Type, immutable: Boolean, location: SourceLocation) {
        scopes.last()[name] = VariableInfo(type, immutable, location)
    }

    fun lookup(name: String): VariableInfo? {
        for (scope in scopes.asReversed()) {
            scope[name]?.let { return it }
        }
        return null
    }

    fun lookupCurrentScope(name: String): VariableInfo? {
        return scopes.lastOrNull()?.get(name)
    }
}
