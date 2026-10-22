package codegen

import nl.endevelopment.semantic.Type
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

internal class ValueCaster(private val state: CodegenContext) {
    fun castValueIfNeeded(value: LLVMValueRef, fromType: Type, toType: Type): LLVMValueRef {
        with(state) {
            if (fromType == toType) {
                return value
            }
            if (fromType == Type.INT && toType == Type.FLOAT) {
                return LLVMBuildSIToFP(builder, value, LLVMFloatTypeInContext(context), "int_to_float")
            }
            return value
        }
    }
}
