package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.semantic.Type
import nl.endevelopment.semantic.core.TypeRules
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

internal class BuiltinCallEmitter(private val state: CodegenContext) {
    fun emit(expr: Expr.Call): LLVMValueRef {
        with(state) {
            return when (expr.name) {
                "len" -> {
                    if (expr.args.size != 1) {
                        fail(expr.location, "len expects 1 argument, got ${expr.args.size}.")
                    }
                    val argType = expressions.inferExprType(expr.args[0])
                    val argValue = state.emit(expr.args[0])
                    return when (argType) {
                        is Type.LIST -> expressions.buildListLength(argValue)
                        Type.STRING -> expressions.buildStringLength(argValue)
                        else -> fail(expr.args[0].location, "len expects a List or String argument.")
                    }
                }

                "substr" -> buildSubstrCall(expr)
                "contains" -> buildContainsCall(expr)
                "to_int" -> buildToIntCall(expr)
                "min" -> buildMinOrMaxCall(expr, findMin = true)
                "max" -> buildMinOrMaxCall(expr, findMin = false)
                "abs" -> buildAbsCall(expr)
                else -> throw Exception("Unknown built-in function '${expr.name}'.")
            }
        }
    }

    private fun buildSubstrCall(expr: Expr.Call): LLVMValueRef {
        with(state) {
            if (expr.args.size != 3) {
                fail(expr.location, "substr expects 3 arguments, got ${expr.args.size}.")
            }

            val sourceType = expressions.inferExprType(expr.args[0])
            val startType = expressions.inferExprType(expr.args[1])
            val lengthType = expressions.inferExprType(expr.args[2])
            if (sourceType != Type.STRING || startType != Type.INT || lengthType != Type.INT) {
                fail(expr.location, "substr expects (String, Int, Int).")
            }

            val source = state.emit(expr.args[0])
            val start = state.emit(expr.args[1])
            val length = state.emit(expr.args[2])

            val strncpyFn = LLVMGetNamedFunction(module, "strncpy") ?: runtime.declareStrncpy()
            val mallocFn = LLVMGetNamedFunction(module, "malloc") ?: runtime.declareMalloc()

            val start64 = LLVMBuildSExt(builder, start, LLVMInt64TypeInContext(context), "substr_start_64")
            val length64 = LLVMBuildSExt(builder, length, LLVMInt64TypeInContext(context), "substr_len_64")

            val sourceOffsetIndices = PointerPointer<LLVMValueRef>(1).apply { put(0, start64) }
            val sourceSlice = LLVMBuildGEP2(
                builder,
                LLVMInt8TypeInContext(context),
                source,
                sourceOffsetIndices,
                1,
                "substr_slice_start"
            )

            val allocLength = LLVMBuildAdd(
                builder,
                length64,
                LLVMConstInt(LLVMInt64TypeInContext(context), 1, 0),
                "substr_alloc_len"
            )
            val mallocArgs = PointerPointer<LLVMValueRef>(1).apply { put(0, allocLength) }
            val outBuffer = LLVMBuildCall2(
                builder,
                mallocFuncType,
                mallocFn,
                mallocArgs,
                1,
                "substr_out_buf"
            ) ?: throw Exception("Failed to allocate substr buffer.")

            val strncpyArgs = PointerPointer<LLVMValueRef>(3).apply {
                put(0, outBuffer)
                put(1, sourceSlice)
                put(2, length64)
            }
            LLVMBuildCall2(
                builder,
                strncpyFuncType,
                strncpyFn,
                strncpyArgs,
                3,
                "strncpy_call"
            ) ?: throw Exception("Failed to build strncpy call for substr.")

            val nullTermIndices = PointerPointer<LLVMValueRef>(1).apply { put(0, length64) }
            val nullTermPtr = LLVMBuildGEP2(
                builder,
                LLVMInt8TypeInContext(context),
                outBuffer,
                nullTermIndices,
                1,
                "substr_null_term_ptr"
            )
            LLVMBuildStore(builder, LLVMConstInt(LLVMInt8TypeInContext(context), 0, 0), nullTermPtr)
            return outBuffer
        }
    }

    private fun buildContainsCall(expr: Expr.Call): LLVMValueRef {
        with(state) {
            if (expr.args.size != 2) {
                fail(expr.location, "contains expects 2 arguments, got ${expr.args.size}.")
            }
            val haystackType = expressions.inferExprType(expr.args[0])
            val needleType = expressions.inferExprType(expr.args[1])
            if (haystackType != Type.STRING || needleType != Type.STRING) {
                fail(expr.location, "contains expects (String, String).")
            }

            val haystack = state.emit(expr.args[0])
            val needle = state.emit(expr.args[1])
            val strstrFn = LLVMGetNamedFunction(module, "strstr") ?: runtime.declareStrstr()
            val args = PointerPointer<LLVMValueRef>(2).apply {
                put(0, haystack)
                put(1, needle)
            }
            val result = LLVMBuildCall2(
                builder,
                strstrFuncType,
                strstrFn,
                args,
                2,
                "strstr_call"
            ) ?: throw Exception("Failed to build call to strstr.")
            return LLVMBuildICmp(
                builder,
                LLVMIntNE,
                result,
                LLVMConstNull(charPtrType),
                "contains_result"
            )
        }
    }

    private fun buildToIntCall(expr: Expr.Call): LLVMValueRef {
        with(state) {
            if (expr.args.size != 1) {
                fail(expr.location, "to_int expects 1 argument, got ${expr.args.size}.")
            }
            val argType = expressions.inferExprType(expr.args[0])
            if (argType != Type.STRING) {
                fail(expr.location, "to_int expects a String argument.")
            }

            val atoiFn = LLVMGetNamedFunction(module, "atoi") ?: runtime.declareAtoi()
            val arg = state.emit(expr.args[0])
            val args = PointerPointer<LLVMValueRef>(1).apply { put(0, arg) }
            return LLVMBuildCall2(
                builder,
                atoiFuncType,
                atoiFn,
                args,
                1,
                "atoi_call"
            ) ?: throw Exception("Failed to build call to atoi.")
        }
    }

    private fun buildMinOrMaxCall(expr: Expr.Call, findMin: Boolean): LLVMValueRef {
        with(state) {
            if (expr.args.size != 2) {
                fail(expr.location, "${expr.name} expects 2 arguments, got ${expr.args.size}.")
            }

            val leftType = expressions.inferExprType(expr.args[0])
            val rightType = expressions.inferExprType(expr.args[1])
            if (!TypeRules.isNumeric(leftType) || !TypeRules.isNumeric(rightType)) {
                fail(expr.location, "${expr.name} expects numeric arguments.")
            }

            var left = state.emit(expr.args[0])
            var right = state.emit(expr.args[1])
            val bothInt = leftType == Type.INT && rightType == Type.INT
            if (!bothInt) {
                left = caster.castValueIfNeeded(left, leftType, Type.FLOAT)
                right = caster.castValueIfNeeded(right, rightType, Type.FLOAT)
                val predicate = if (findMin) LLVMRealOLT else LLVMRealOGT
                val cmp = LLVMBuildFCmp(builder, predicate, left, right, "minmax_cmp")
                return LLVMBuildSelect(builder, cmp, left, right, "minmax_float")
            }

            val predicate = if (findMin) LLVMIntSLT else LLVMIntSGT
            val cmp = LLVMBuildICmp(builder, predicate, left, right, "minmax_cmp")
            return LLVMBuildSelect(builder, cmp, left, right, "minmax_int")
        }
    }

    private fun buildAbsCall(expr: Expr.Call): LLVMValueRef {
        with(state) {
            if (expr.args.size != 1) {
                fail(expr.location, "abs expects 1 argument, got ${expr.args.size}.")
            }

            val argType = expressions.inferExprType(expr.args[0])
            val arg = state.emit(expr.args[0])
            return when (argType) {
                Type.INT -> {
                    val zero = LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0)
                    val isNegative = LLVMBuildICmp(builder, LLVMIntSLT, arg, zero, "abs_int_is_neg")
                    val negated = LLVMBuildNeg(builder, arg, "abs_int_neg")
                    LLVMBuildSelect(builder, isNegative, negated, arg, "abs_int")
                }

                Type.FLOAT -> {
                    val zero = LLVMConstReal(LLVMFloatTypeInContext(context), 0.0)
                    val isNegative = LLVMBuildFCmp(builder, LLVMRealOLT, arg, zero, "abs_float_is_neg")
                    val negated = LLVMBuildFNeg(builder, arg, "abs_float_neg")
                    LLVMBuildSelect(builder, isNegative, negated, arg, "abs_float")
                }

                else -> fail(expr.location, "abs expects a numeric argument.")
            }
        }
    }
}
