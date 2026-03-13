package codegen

import nl.endevelopment.semantic.Type
import org.bytedeco.llvm.LLVM.LLVMValueRef

class ValueCaster(
    private val codeGenerator: CodeGenerator
) {
    fun cast(value: LLVMValueRef, fromType: Type, toType: Type): LLVMValueRef {
        return codeGenerator.castValue(value, fromType, toType)
    }
}
