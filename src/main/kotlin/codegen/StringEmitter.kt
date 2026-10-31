package codegen

import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

/** Emits string length and concatenation through the existing C runtime declarations. */
internal class StringEmitter(private val state: CodegenContext) {
    fun buildStringLength(strPtr: LLVMValueRef): LLVMValueRef {
        with(state) {
            val strlenFn = LLVMGetNamedFunction(module, "strlen") ?: runtime.declareStrlen()
            val args = PointerPointer<LLVMValueRef>(1).apply { put(0, strPtr) }
            val len64 = LLVMBuildCall2(
                builder,
                strlenFuncType,
                strlenFn,
                args,
                1,
                "strlen_call"
            ) ?: throw Exception("Failed to build call to strlen.")
            return LLVMBuildTrunc(builder, len64, LLVMInt32TypeInContext(context), "strlen_i32")
        }
    }

    fun buildStringConcat(left: LLVMValueRef, right: LLVMValueRef): LLVMValueRef {
        with(state) {
            val strlenFn = LLVMGetNamedFunction(module, "strlen") ?: runtime.declareStrlen()
            val mallocFn = LLVMGetNamedFunction(module, "malloc") ?: runtime.declareMalloc()
            val strcpyFn = LLVMGetNamedFunction(module, "strcpy") ?: runtime.declareStrcpy()
            val strcatFn = LLVMGetNamedFunction(module, "strcat") ?: runtime.declareStrcat()

            val lenLeftArgs = PointerPointer<LLVMValueRef>(1).apply { put(0, left) }
            val lenLeft = LLVMBuildCall2(
                builder,
                strlenFuncType,
                strlenFn,
                lenLeftArgs,
                1,
                "left_len"
            ) ?: throw Exception("Failed to build strlen call for left string.")

            val lenRightArgs = PointerPointer<LLVMValueRef>(1).apply { put(0, right) }
            val lenRight = LLVMBuildCall2(
                builder,
                strlenFuncType,
                strlenFn,
                lenRightArgs,
                1,
                "right_len"
            ) ?: throw Exception("Failed to build strlen call for right string.")

            val totalLen = LLVMBuildAdd(builder, lenLeft, lenRight, "concat_len")
            val allocLen = LLVMBuildAdd(
                builder,
                totalLen,
                LLVMConstInt(LLVMInt64TypeInContext(context), 1, 0),
                "concat_alloc_len"
            )

            val mallocArgs = PointerPointer<LLVMValueRef>(1).apply { put(0, allocLen) }
            val concatPtr = LLVMBuildCall2(
                builder,
                mallocFuncType,
                mallocFn,
                mallocArgs,
                1,
                "concat_buf"
            ) ?: throw Exception("Failed to allocate memory for string concatenation.")

            val copyArgs = PointerPointer<LLVMValueRef>(2).apply {
                put(0, concatPtr)
                put(1, left)
            }
            LLVMBuildCall2(
                builder,
                strcpyFuncType,
                strcpyFn,
                copyArgs,
                2,
                "strcpy_call"
            ) ?: throw Exception("Failed to build strcpy call for string concatenation.")

            val appendArgs = PointerPointer<LLVMValueRef>(2).apply {
                put(0, concatPtr)
                put(1, right)
            }
            LLVMBuildCall2(
                builder,
                strcatFuncType,
                strcatFn,
                appendArgs,
                2,
                "strcat_call"
            ) ?: throw Exception("Failed to build strcat call for string concatenation.")

            return concatPtr
        }
    }
}
