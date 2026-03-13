package codegen

import nl.endevelopment.semantic.Type
import org.bytedeco.llvm.LLVM.LLVMTypeRef

class TypeLowering(
    private val codeGenerator: CodeGenerator
) {
    fun llvmTypeFor(type: Type): LLVMTypeRef {
        return codeGenerator.lowerType(type)
    }
}
