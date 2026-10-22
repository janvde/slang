package nl.endevelopment.semantic.typechecker

import nl.endevelopment.ast.Program
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.semantic.Type
import nl.endevelopment.semantic.core.ProgramIndex

internal data class VariableInfo(val type: Type, val immutable: Boolean, val location: SourceLocation)
internal data class FunctionInfo(val returnType: Type, val paramTypes: List<Type>, val location: SourceLocation)
internal data class MethodInfo(val returnType: Type, val paramTypes: List<Type>, val location: SourceLocation)
internal data class ClassFieldInfo(val type: Type, val mutable: Boolean, val location: SourceLocation)
internal data class ClassInfo(
    val declarationLocation: SourceLocation,
    val fields: Map<String, ClassFieldInfo>,
    val methods: Map<String, MethodInfo>
)

internal class TypeCheckState {
    val scopes = ScopeStack()
    val functions = mutableMapOf<String, FunctionInfo>()
    val classes = mutableMapOf<String, ClassInfo>()
    var currentReturnType: Type? = null
    var currentClassName: String? = null
    var loopDepth: Int = 0
    lateinit var programIndex: ProgramIndex

    fun reset(program: Program) {
        scopes.clear()
        functions.clear()
        classes.clear()
        currentReturnType = null
        currentClassName = null
        loopDepth = 0
        programIndex = ProgramIndex.from(program)
    }
}
