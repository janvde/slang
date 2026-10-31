package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.semantic.Type
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

/** Emits list construction, element reads, and length using the shared list layout. */
internal class ListEmitter(private val state: CodegenContext) {
    fun buildListLiteral(expr: Expr.ListLiteral): LLVMValueRef {
        with(state) {
            val listType = expressionTypes.inferExprType(expr) as? Type.LIST
                ?: throw Exception("List literal must infer to a List type.")
            val elementType = listType.elementType ?: Type.INT
            val elementLLVMType = types.llvmTypeFor(elementType)

            val length = expr.elements.size
            val lenValue = LLVMConstInt(LLVMInt64TypeInContext(context), length.toLong(), 0)
            val dataPtr = if (length == 0) {
                LLVMConstNull(charPtrType)
            } else {
                val arrayType = LLVMArrayType(elementLLVMType, length)
                val arrayAlloca = LLVMBuildAlloca(builder, arrayType, "list_data")
                expr.elements.forEachIndexed { index, element ->
                    val rawElemValue = state.emit(element)
                    val elemType = expressionTypes.inferExprType(element)
                    val elemValue = caster.castValueIfNeeded(rawElemValue, elemType, elementType)
                    val indices = PointerPointer<LLVMValueRef>(2).apply {
                        put(0, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
                        put(1, LLVMConstInt(LLVMInt32TypeInContext(context), index.toLong(), 0))
                    }
                    val elemPtr = LLVMBuildGEP2(
                        builder,
                        arrayType,
                        arrayAlloca,
                        indices,
                        2,
                        "list_elem_ptr"
                    )
                    LLVMBuildStore(builder, elemValue, elemPtr)
                }

                val dataIndices = PointerPointer<LLVMValueRef>(2).apply {
                    put(0, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
                    put(1, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
                }
                val rawDataPtr = LLVMBuildGEP2(
                    builder,
                    arrayType,
                    arrayAlloca,
                    dataIndices,
                    2,
                    "list_data_ptr"
                )
                LLVMBuildBitCast(builder, rawDataPtr, charPtrType, "list_data_i8")
            }

            val listAlloca = LLVMBuildAlloca(builder, listStructType, "list_struct")
            val lenPtr = LLVMBuildStructGEP2(builder, listStructType, listAlloca, 0, "list_len_ptr")
            LLVMBuildStore(builder, lenValue, lenPtr)
            val dataPtrPtr = LLVMBuildStructGEP2(builder, listStructType, listAlloca, 1, "list_data_ptr")
            LLVMBuildStore(builder, dataPtr, dataPtrPtr)

            return listAlloca
        }
    }

    fun buildListLength(listPtr: LLVMValueRef): LLVMValueRef {
        with(state) {
            val lenPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 0, "list_len_ptr")
            val lenVal = LLVMBuildLoad2(builder, LLVMInt64TypeInContext(context), lenPtr, "list_len")
            return LLVMBuildTrunc(builder, lenVal, LLVMInt32TypeInContext(context), "list_len_i32")
        }
    }

    fun buildIndexExpr(expr: Expr.Index): LLVMValueRef {
        with(state) {
            val targetType = expressionTypes.inferExprType(expr.target) as? Type.LIST
                ?: fail(expr.target.location, "Indexing is only supported on List values.")
            val elementType = targetType.elementType ?: Type.INT

            val listPtr = state.emit(expr.target)
            val indexValue = state.emit(expr.index)
            val dataPtrPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 1, "list_data_ptr")
            val dataPtrI8 = LLVMBuildLoad2(
                builder,
                charPtrType,
                dataPtrPtr,
                "list_data"
            )
            val dataPtr = LLVMBuildBitCast(
                builder,
                dataPtrI8,
                LLVMPointerType(types.llvmTypeFor(elementType), 0),
                "typed_list_data"
            )
            val elemIndices = PointerPointer<LLVMValueRef>(1).apply {
                put(0, indexValue)
            }
            val elemPtr = LLVMBuildGEP2(
                builder,
                types.llvmTypeFor(elementType),
                dataPtr,
                elemIndices,
                1,
                "list_elem_ptr"
            )
            return LLVMBuildLoad2(builder, types.llvmTypeFor(elementType), elemPtr, "list_elem")
        }
    }
}
