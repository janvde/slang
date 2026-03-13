package nl.endevelopment.semantic.core

import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.semantic.Type

class CallResolver(private val builtins: BuiltinRegistry) {

    sealed class CallTarget {
        data class Builtin(val spec: BuiltinRegistry.BuiltinSpec) : CallTarget()
        data class Constructor(val className: String, val paramTypes: List<Type>) : CallTarget()
        data class Function(val name: String, val returnType: Type, val paramTypes: List<Type>) : CallTarget()
    }

    fun resolve(
        name: String,
        index: ProgramIndex,
        location: SourceLocation,
        fail: (SourceLocation, String) -> Nothing
    ): CallTarget {
        builtins.spec(name)?.let { return CallTarget.Builtin(it) }

        index.classes[name]?.let { classSig ->
            return CallTarget.Constructor(
                className = classSig.name,
                paramTypes = classSig.fields.map { it.type }
            )
        }

        index.functions[name]?.let { fn ->
            return CallTarget.Function(fn.name, fn.returnType, fn.paramTypes)
        }

        fail(location, "Undefined function '$name'.")
    }
}
