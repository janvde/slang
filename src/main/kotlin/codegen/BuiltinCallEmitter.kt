package codegen

import nl.endevelopment.ast.Expr
import org.bytedeco.llvm.LLVM.LLVMValueRef

class BuiltinCallEmitter(
    private val codeGenerator: CodeGenerator
) {
    fun emit(expr: Expr.Call): LLVMValueRef {
        return codeGenerator.emitBuiltinCall(expr)
    }
}
