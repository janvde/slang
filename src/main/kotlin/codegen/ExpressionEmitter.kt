package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.semantic.Type
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

internal class DefaultExpressionEmitter(private val state: CodegenContext) : ExpressionEmitter {
    override fun emit(expr: Expr): LLVMValueRef {
        with(state) {
            return when (expr) {
                is Expr.Number -> LLVMConstInt(LLVMInt32TypeInContext(context), expr.value.toLong(), 0)
                is Expr.FloatLiteral -> LLVMConstReal(LLVMFloatTypeInContext(context), expr.value.toDouble())
                is Expr.BoolLiteral -> LLVMConstInt(LLVMInt1TypeInContext(context), if (expr.value) 1 else 0, 0)
                is Expr.StringLiteral -> LLVMBuildGlobalStringPtr(builder, expr.value, "str")
                is Expr.This -> {
                    val symbol = codegenSymbolTable["this"]
                        ?: throw Exception("'this' is only available inside class methods.")
                    LLVMBuildLoad2(builder, types.llvmTypeFor(symbol.type), symbol.ptr, "this")
                }

                is Expr.Variable -> {
                    val symbol = codegenSymbolTable[expr.name]
                        ?: throw Exception("Undefined variable during code generation: ${expr.name}")
                    LLVMBuildLoad2(builder, types.llvmTypeFor(symbol.type), symbol.ptr, expr.name)
                }

                is Expr.UnaryOp -> {
                    val operand = state.emit(expr.operand)
                    when (expr.operator) {
                        "!" -> {
                            val operandType = expressionTypes.inferExprType(expr.operand)
                            if (operandType != Type.BOOL) {
                                throw Exception("NOT operator requires Bool operand.")
                            }
                            LLVMBuildNot(builder, operand, "nottmp")
                        }

                        else -> throw Exception("Unknown unary operator: ${expr.operator}")
                    }
                }

                is Expr.Call -> calls.buildCall(expr, allowVoid = false)
                is Expr.MemberAccess -> buildMemberAccess(expr)
                is Expr.MemberCall -> calls.buildMemberCall(expr, allowVoid = false)
                is Expr.ListLiteral -> lists.buildListLiteral(expr)
                is Expr.Index -> lists.buildIndexExpr(expr)

                is Expr.BinaryOp -> {
                    if (expr.operator == "&&" || expr.operator == "||") {
                        return buildShortCircuitLogical(expr)
                    }

                    var left = state.emit(expr.left)
                    var right = state.emit(expr.right)
                    val leftType = expressionTypes.inferExprType(expr.left)
                    val rightType = expressionTypes.inferExprType(expr.right)

                    if (expr.operator == "+" && leftType == Type.STRING && rightType == Type.STRING) {
                        return strings.buildStringConcat(left, right)
                    }

                    if (leftType == Type.INT && rightType == Type.FLOAT) {
                        left = LLVMBuildSIToFP(builder, left, LLVMFloatTypeInContext(context), "inttofloat")
                    } else if (leftType == Type.FLOAT && rightType == Type.INT) {
                        right = LLVMBuildSIToFP(builder, right, LLVMFloatTypeInContext(context), "inttofloat")
                    }

                    val isFloat = (leftType == Type.FLOAT || rightType == Type.FLOAT)

                    when (expr.operator) {
                        "+" -> if (isFloat) LLVMBuildFAdd(builder, left, right, "faddtmp")
                        else LLVMBuildAdd(builder, left, right, "addtmp")

                        "-" -> if (isFloat) LLVMBuildFSub(builder, left, right, "fsubtmp")
                        else LLVMBuildSub(builder, left, right, "subtmp")

                        "*" -> if (isFloat) LLVMBuildFMul(builder, left, right, "fmultmp")
                        else LLVMBuildMul(builder, left, right, "multmp")

                        "/" -> if (isFloat) LLVMBuildFDiv(builder, left, right, "fdivtmp")
                        else LLVMBuildSDiv(builder, left, right, "divtmp")

                        "%" -> LLVMBuildSRem(builder, left, right, "modtmp")
                        ">" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealOGT, left, right, "fcmptmp")
                        else LLVMBuildICmp(builder, LLVMIntSGT, left, right, "cmptmp")

                        "<" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealOLT, left, right, "fcmptmp")
                        else LLVMBuildICmp(builder, LLVMIntSLT, left, right, "cmptmp")

                        ">=" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealOGE, left, right, "fcmptmp")
                        else LLVMBuildICmp(builder, LLVMIntSGE, left, right, "cmptmp")

                        "<=" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealOLE, left, right, "fcmptmp")
                        else LLVMBuildICmp(builder, LLVMIntSLE, left, right, "cmptmp")

                        "==" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealOEQ, left, right, "fcmptmp")
                        else LLVMBuildICmp(builder, LLVMIntEQ, left, right, "cmptmp")

                        "!=" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealONE, left, right, "fcmptmp")
                        else LLVMBuildICmp(builder, LLVMIntNE, left, right, "cmptmp")

                        else -> throw Exception("Unknown operator: ${expr.operator}")
                    }
                }
            }
        }
    }

    private fun buildMemberAccess(expr: Expr.MemberAccess): LLVMValueRef {
        with(state) {
            val receiverType = expressionTypes.inferExprType(expr.receiver)
            val classType = receiverType as? Type.CLASS
                ?: throw Exception("Member access requires class instance receiver.")
            val classInfo = classes[classType.name]
                ?: throw Exception("Unknown class '${classType.name}'.")
            val field = classInfo.fieldsByName[expr.member]
                ?: throw Exception("Class '${classType.name}' has no field '${expr.member}'.")

            val receiverValue = state.emit(expr.receiver)
            val fieldPtr = LLVMBuildStructGEP2(builder, classInfo.structType, receiverValue, field.index, "${expr.member}_ptr")
            return LLVMBuildLoad2(builder, types.llvmTypeFor(field.type), fieldPtr, expr.member)
        }
    }

    private fun buildShortCircuitLogical(expr: Expr.BinaryOp): LLVMValueRef {
        with(state) {
            val currentBlock = LLVMGetInsertBlock(builder)
            val parentFunction = LLVMGetBasicBlockParent(currentBlock)

            if (expressionTypes.inferExprType(expr.left) != Type.BOOL || expressionTypes.inferExprType(expr.right) != Type.BOOL) {
                throw Exception("Logical operators require Bool operands.")
            }

            val rightBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "logical_right")
            val mergeBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "logical_merge")

            val leftValue = state.emit(expr.left)
            val leftEndBlock = LLVMGetInsertBlock(builder)

            if (expr.operator == "&&") {
                LLVMBuildCondBr(builder, leftValue, rightBlock, mergeBlock)
            } else {
                LLVMBuildCondBr(builder, leftValue, mergeBlock, rightBlock)
            }

            LLVMPositionBuilderAtEnd(builder, rightBlock)
            val rightValue = state.emit(expr.right)
            val rightEndBlock = LLVMGetInsertBlock(builder)
            LLVMBuildBr(builder, mergeBlock)

            LLVMPositionBuilderAtEnd(builder, mergeBlock)
            val phi = LLVMBuildPhi(builder, LLVMInt1TypeInContext(context), "logical_result")

            val incomingValues = PointerPointer<LLVMValueRef>(2)
            val incomingBlocks = PointerPointer<LLVMBasicBlockRef>(2)

            if (expr.operator == "&&") {
                incomingValues.put(0, LLVMConstInt(LLVMInt1TypeInContext(context), 0, 0))
                incomingBlocks.put(0, leftEndBlock)
                incomingValues.put(1, rightValue)
                incomingBlocks.put(1, rightEndBlock)
            } else {
                incomingValues.put(0, LLVMConstInt(LLVMInt1TypeInContext(context), 1, 0))
                incomingBlocks.put(0, leftEndBlock)
                incomingValues.put(1, rightValue)
                incomingBlocks.put(1, rightEndBlock)
            }

            LLVMAddIncoming(phi, incomingValues, incomingBlocks, 2)
            return phi
        }
    }
}
