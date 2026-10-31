package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.semantic.Type
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

/** Emits function, method, and constructor calls in expression or statement position. */
internal class CallEmitter(private val state: CodegenContext) {
    fun buildCall(expr: Expr.Call, allowVoid: Boolean): LLVMValueRef {
        with(state) {
            if (expr.name in builtInFunctions) {
                return builtinCalls.emit(expr)
            }

            classes[expr.name]?.let { classInfo ->
                return buildClassConstructorCall(classInfo, expr)
            }

            val info = functions[expr.name]
                ?: throw Exception("Undefined function during code generation: ${expr.name}")

            if (expr.args.size != info.paramTypes.size) {
                throw Exception("Function '${expr.name}' expects ${info.paramTypes.size} arguments, got ${expr.args.size}.")
            }
            if (!allowVoid && info.returnType == Type.VOID) {
                throw Exception("Cannot use void function '${expr.name}' in an expression.")
            }

            val args = PointerPointer<LLVMValueRef>(expr.args.size.toLong())
            expr.args.forEachIndexed { index, arg ->
                val rawArg = state.emit(arg)
                val coercedArg = caster.castValueIfNeeded(rawArg, expressionTypes.inferExprType(arg), info.paramTypes[index])
                args.put(index.toLong(), coercedArg)
            }

            val callValue = LLVMBuildCall2(
                builder,
                info.llvmType,
                info.llvmFunc,
                args,
                expr.args.size,
                if (info.returnType == Type.VOID) "" else "calltmp"
            ) ?: throw Exception("Failed to build call for function '${expr.name}'.")
            return callValue
        }
    }

    fun buildMemberCall(expr: Expr.MemberCall, allowVoid: Boolean): LLVMValueRef {
        with(state) {
            val receiverType = expressionTypes.inferExprType(expr.receiver)
            val classType = receiverType as? Type.CLASS
                ?: throw Exception("Method call requires class instance receiver.")
            val classInfo = classes[classType.name]
                ?: throw Exception("Unknown class '${classType.name}'.")
            val methodInfo = classInfo.methods[expr.method]
                ?: throw Exception("Class '${classType.name}' has no method '${expr.method}'.")

            if (expr.args.size != methodInfo.paramTypes.size) {
                throw Exception("Method '${expr.method}' expects ${methodInfo.paramTypes.size} arguments, got ${expr.args.size}.")
            }
            if (!allowVoid && methodInfo.returnType == Type.VOID) {
                throw Exception("Cannot use void method '${expr.method}' in an expression.")
            }

            val args = PointerPointer<LLVMValueRef>((expr.args.size + 1).toLong())
            args.put(0L, state.emit(expr.receiver))
            expr.args.forEachIndexed { index, arg ->
                val rawArg = state.emit(arg)
                val coercedArg = caster.castValueIfNeeded(rawArg, expressionTypes.inferExprType(arg), methodInfo.paramTypes[index])
                args.put((index + 1).toLong(), coercedArg)
            }

            val callValue = LLVMBuildCall2(
                builder,
                methodInfo.llvmType,
                methodInfo.llvmFunc,
                args,
                expr.args.size + 1,
                if (methodInfo.returnType == Type.VOID) "" else "method_call"
            ) ?: throw Exception("Failed to build call for method '${expr.method}'.")

            return callValue
        }
    }

    private fun buildClassConstructorCall(classInfo: ClassInfo, expr: Expr.Call): LLVMValueRef {
        with(state) {
            if (expr.args.size != classInfo.fields.size) {
                fail(expr.location, "Constructor '${classInfo.name}' expects ${classInfo.fields.size} arguments, got ${expr.args.size}.")
            }

            val mallocFn = LLVMGetNamedFunction(module, "malloc") ?: runtime.declareMalloc()
            val sizeValue = LLVMSizeOf(classInfo.structType)
            val mallocArgs = PointerPointer<LLVMValueRef>(1).apply { put(0, sizeValue) }
            val rawPtr = LLVMBuildCall2(
                builder,
                mallocFuncType,
                mallocFn,
                mallocArgs,
                1,
                "malloc_obj"
            ) ?: throw Exception("Failed to build call to malloc.")

            val objPtrType = LLVMPointerType(classInfo.structType, 0)
            val objPtr = LLVMBuildBitCast(builder, rawPtr, objPtrType, "obj_ptr")

            classInfo.fields.forEachIndexed { index, field ->
                val fieldPtr = LLVMBuildStructGEP2(builder, classInfo.structType, objPtr, index, "${field.name}_ptr")
                val rawValue = state.emit(expr.args[index])
                val value = caster.castValueIfNeeded(rawValue, expressionTypes.inferExprType(expr.args[index]), field.type)
                LLVMBuildStore(builder, value, fieldPtr)
            }

            return objPtr
        }
    }
}
