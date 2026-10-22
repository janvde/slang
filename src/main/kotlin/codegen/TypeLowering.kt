package codegen

import nl.endevelopment.semantic.Type
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

internal class TypeLowering(private val state: CodegenContext) {
    fun llvmTypeFor(type: Type): LLVMTypeRef {
        with(state) {
            return when (type) {
                Type.INT -> LLVMInt32TypeInContext(context)
                Type.FLOAT -> LLVMFloatTypeInContext(context)
                Type.BOOL -> LLVMInt1TypeInContext(context)
                Type.STRING -> LLVMPointerType(LLVMInt8TypeInContext(context), 0)
                is Type.LIST -> LLVMPointerType(listStructType, 0)
                Type.VOID -> LLVMVoidTypeInContext(context)
                is Type.CLASS -> {
                    val structType = classStructTypes[type.name]
                        ?: throw Exception("Unknown class '${type.name}'.")
                    LLVMPointerType(structType, 0)
                }
            }
        }
    }
}
