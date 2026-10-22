package codegen

import nl.endevelopment.semantic.Type
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

internal class RuntimeDeclEmitter(private val state: CodegenContext) {
    private fun declareListType() {
        with(state) {
            val elements = PointerPointer<LLVMTypeRef>(2)
            elements.put(0L, LLVMInt64TypeInContext(context))
            elements.put(1L, charPtrType)
            LLVMStructSetBody(listStructType, elements, 2, 0)
        }
    }

    internal fun declareCoreRuntime() {
        with(state) {
            declareListType()
            declarePrintf()
            declareFormatStrings()
        }
    }

    fun declarePrintf(): LLVMValueRef {
        with(state) {
            val printfReturnType = LLVMInt32TypeInContext(context)
            val printfParamType = LLVMPointerType(LLVMInt8TypeInContext(context), 0)

            val printfParamTypes = PointerPointer<LLVMTypeRef>(1)
            printfParamTypes.put(0, printfParamType)

            printfFuncType = LLVMFunctionType(
                printfReturnType,
                printfParamTypes,
                1,
                1
            )

            return LLVMAddFunction(module, "printf", printfFuncType)
        }
    }

    fun declareStrlen(): LLVMValueRef {
        with(state) {
            return LLVMAddFunction(module, "strlen", strlenFuncType)
        }
    }

    fun declareMalloc(): LLVMValueRef {
        with(state) {
            val mallocParamTypes = PointerPointer<LLVMTypeRef>(1)
            mallocParamTypes.put(0, LLVMInt64TypeInContext(context))
            mallocFuncType = LLVMFunctionType(
                charPtrType,
                mallocParamTypes,
                1,
                0
            )
            return LLVMAddFunction(module, "malloc", mallocFuncType)
        }
    }

    fun declareStrcpy(): LLVMValueRef {
        with(state) {
            val paramTypes = PointerPointer<LLVMTypeRef>(2)
            paramTypes.put(0, charPtrType)
            paramTypes.put(1, charPtrType)
            strcpyFuncType = LLVMFunctionType(charPtrType, paramTypes, 2, 0)
            return LLVMAddFunction(module, "strcpy", strcpyFuncType)
        }
    }

    fun declareStrcat(): LLVMValueRef {
        with(state) {
            val paramTypes = PointerPointer<LLVMTypeRef>(2)
            paramTypes.put(0, charPtrType)
            paramTypes.put(1, charPtrType)
            strcatFuncType = LLVMFunctionType(charPtrType, paramTypes, 2, 0)
            return LLVMAddFunction(module, "strcat", strcatFuncType)
        }
    }

    fun declareStrncpy(): LLVMValueRef {
        with(state) {
            val paramTypes = PointerPointer<LLVMTypeRef>(3)
            paramTypes.put(0, charPtrType)
            paramTypes.put(1, charPtrType)
            paramTypes.put(2, LLVMInt64TypeInContext(context))
            strncpyFuncType = LLVMFunctionType(charPtrType, paramTypes, 3, 0)
            return LLVMAddFunction(module, "strncpy", strncpyFuncType)
        }
    }

    fun declareStrstr(): LLVMValueRef {
        with(state) {
            val paramTypes = PointerPointer<LLVMTypeRef>(2)
            paramTypes.put(0, charPtrType)
            paramTypes.put(1, charPtrType)
            strstrFuncType = LLVMFunctionType(charPtrType, paramTypes, 2, 0)
            return LLVMAddFunction(module, "strstr", strstrFuncType)
        }
    }

    fun declareAtoi(): LLVMValueRef {
        with(state) {
            val paramTypes = PointerPointer<LLVMTypeRef>(1)
            paramTypes.put(0, charPtrType)
            atoiFuncType = LLVMFunctionType(LLVMInt32TypeInContext(context), paramTypes, 1, 0)
            return LLVMAddFunction(module, "atoi", atoiFuncType)
        }
    }

    private fun declareFormatStrings() {
        with(state) {
            fun createGlobalString(name: String, value: String): LLVMValueRef {
                val strLength = value.length
                val arrayType = LLVMArrayType(LLVMInt8TypeInContext(context), strLength + 1)
                val global = LLVMAddGlobal(module, arrayType, name)
                LLVMSetLinkage(global, LLVMPrivateLinkage)
                val initializer = LLVMConstStringInContext(context, value, strLength, 0)
                LLVMSetInitializer(global, initializer)
                LLVMSetGlobalConstant(global, 1)
                return global
            }

            formatStrings[Type.INT] = createGlobalString("fmt_int", "%d\n")
            formatStrings[Type.STRING] = createGlobalString("fmt_str", "%s\n")
            formatStrings[Type.FLOAT] = createGlobalString("fmt_float", "%f\n")
            formatStrings[Type.BOOL] = createGlobalString("fmt_bool", "%d\n")
        }
    }
}
