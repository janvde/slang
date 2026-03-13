package nl.endevelopment.interpreter.runtime

import nl.endevelopment.ast.MethodDef
import nl.endevelopment.ast.Stmt
import nl.endevelopment.interpreter.VariableEnvironment
import nl.endevelopment.semantic.core.BuiltinRegistry

data class ClassFieldRuntimeInfo(val mutable: Boolean)

data class ClassRuntimeInfo(
    val fieldOrder: List<String>,
    val fieldInfo: Map<String, ClassFieldRuntimeInfo>,
    val methods: Map<String, MethodDef>
)

class RuntimeContext {
    val variableEnv = VariableEnvironment()
    val functions = mutableMapOf<String, Stmt.FunctionDef>()
    val classes = mutableMapOf<String, ClassRuntimeInfo>()
    var functionDepth: Int = 0
    var loopDepth: Int = 0
    val builtins: Set<String> = BuiltinRegistry().names()
}
