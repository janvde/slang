package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.semantic.Type
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

/** Emits scalar and list output using the existing printf formats. */
internal class PrintEmitter(private val state: CodegenContext) {
    fun emitPrint(expr: Expr, location: SourceLocation) {
        with(state) {
            val exprValue = state.emit(expr)
            val printfFunc = LLVMGetNamedFunction(module, "printf") ?: runtime.declarePrintf()
            val exprType = expressionTypes.inferExprType(expr)

            if (exprType is Type.LIST) {
                emitPrintList(exprValue, exprType.elementType ?: Type.INT, printfFunc, location)
                return
            }

            if (exprType is Type.CLASS) {
                fail(location, "Printing class instances is not supported in v1.")
            }

            val formatStr = formatStrings[exprType]
                ?: throw Exception("No format string found for type: $exprType")

            val zero = LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0)
            val indices = PointerPointer<LLVMValueRef>(2).apply {
                put(0, zero)
                put(1, zero)
            }

            val gepFormatStr = LLVMBuildGEP2(
                builder,
                LLVMInt8TypeInContext(context),
                formatStr,
                indices,
                2,
                "fmt_ptr"
            ) ?: throw Exception("Failed to build GEP for format string")

            buildPrintfCall(printfFunc, gepFormatStr, exprValue)
        }
    }

    private fun buildPrintfCall(
        printfFunc: LLVMValueRef,
        formatPtr: LLVMValueRef,
        value: LLVMValueRef
    ) {
        with(state) {
            val args = PointerPointer<LLVMValueRef>(2).apply {
                put(0, formatPtr)
                put(1, value)
            }

            LLVMBuildCall2(
                builder,
                printfFuncType,
                printfFunc,
                args,
                2,
                "callprintf"
            ) ?: throw Exception("Failed to build call to printf")
        }
    }

    private fun emitPrintList(
        listPtr: LLVMValueRef,
        elementType: Type,
        printfFunc: LLVMValueRef,
        location: SourceLocation
    ) {
        with(state) {
            if (elementType is Type.CLASS || elementType is Type.LIST) {
                fail(location, "Printing nested/object lists is not supported in v1 native mode.")
            }

            val lenPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 0, "list_len_ptr")
            val dataPtrPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 1, "list_data_ptr")

            val lenVal = LLVMBuildLoad2(builder, LLVMInt64TypeInContext(context), lenPtr, "list_len")
            val dataPtr = LLVMBuildLoad2(
                builder,
                charPtrType,
                dataPtrPtr,
                "list_data"
            )
            val typedDataPtr = LLVMBuildBitCast(
                builder,
                dataPtr,
                LLVMPointerType(types.llvmTypeFor(elementType), 0),
                "typed_list_data"
            )

            val currentBlock = LLVMGetInsertBlock(builder)
            val parentFunction = LLVMGetBasicBlockParent(currentBlock)

            val loopBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "list_loop")
            val bodyBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "list_body")
            val afterBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "list_after")

            val indexPtr = LLVMBuildAlloca(builder, LLVMInt64TypeInContext(context), "list_index")
            LLVMBuildStore(builder, LLVMConstInt(LLVMInt64TypeInContext(context), 0, 0), indexPtr)
            LLVMBuildBr(builder, loopBlock)

            LLVMPositionBuilderAtEnd(builder, loopBlock)
            val indexVal = LLVMBuildLoad2(builder, LLVMInt64TypeInContext(context), indexPtr, "list_index_val")
            val loopCond = LLVMBuildICmp(builder, LLVMIntSLT, indexVal, lenVal, "list_loop_cond")
            LLVMBuildCondBr(builder, loopCond, bodyBlock, afterBlock)

            LLVMPositionBuilderAtEnd(builder, bodyBlock)
            val elemIndices = PointerPointer<LLVMValueRef>(1).apply {
                put(0, indexVal)
            }
            val elemPtr = LLVMBuildGEP2(
                builder,
                types.llvmTypeFor(elementType),
                typedDataPtr,
                elemIndices,
                1,
                "list_elem_ptr"
            )
            val elemVal = LLVMBuildLoad2(builder, types.llvmTypeFor(elementType), elemPtr, "list_elem")

            val formatStr = formatStrings[elementType]
                ?: throw Exception("No format string found for type: $elementType")
            val zero = LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0)
            val indices = PointerPointer<LLVMValueRef>(2).apply {
                put(0, zero)
                put(1, zero)
            }
            val gepFormatStr = LLVMBuildGEP2(
                builder,
                LLVMInt8TypeInContext(context),
                formatStr,
                indices,
                2,
                "fmt_ptr"
            ) ?: throw Exception("Failed to build GEP for format string")
            buildPrintfCall(printfFunc, gepFormatStr, elemVal)

            val nextIndex = LLVMBuildAdd(
                builder,
                indexVal,
                LLVMConstInt(LLVMInt64TypeInContext(context), 1, 0),
                "list_index_next"
            )
            LLVMBuildStore(builder, nextIndex, indexPtr)
            LLVMBuildBr(builder, loopBlock)

            LLVMPositionBuilderAtEnd(builder, afterBlock)
        }
    }
}
