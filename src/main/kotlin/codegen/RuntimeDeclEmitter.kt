package codegen

class RuntimeDeclEmitter(
    private val codeGenerator: CodeGenerator
) {
    fun declareCoreRuntime() {
        codeGenerator.declareCoreRuntime()
    }
}
