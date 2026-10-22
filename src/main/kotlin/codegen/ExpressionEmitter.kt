package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.semantic.Type
import nl.endevelopment.semantic.core.CallResolver
import nl.endevelopment.semantic.core.TypeRules
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

internal class DefaultExpressionEmitter(private val state: CodegenContext) : ExpressionEmitter {
    private fun buildListLiteral(expr: Expr.ListLiteral): LLVMValueRef {
        with(state) {
            val listType = inferExprType(expr) as? Type.LIST
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
                    val elemType = inferExprType(element)
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

    fun inferExprType(expr: Expr): Type {
        with(state) {
            return when (expr) {
                is Expr.Number -> Type.INT
                is Expr.FloatLiteral -> Type.FLOAT
                is Expr.BoolLiteral -> Type.BOOL
                is Expr.StringLiteral -> Type.STRING
                is Expr.This -> {
                    val className = currentClassName
                        ?: throw Exception("'this' is only available inside class methods.")
                    Type.CLASS(className)
                }

                is Expr.Variable -> codegenSymbolTable[expr.name]?.type
                    ?: throw Exception("Undefined variable during type inference: ${expr.name}")

                is Expr.UnaryOp -> when (expr.operator) {
                    "!" -> Type.BOOL
                    else -> throw Exception("Unknown unary operator: ${expr.operator}")
                }

                is Expr.Call -> {
                    val target = callResolver.resolve(expr.name, programIndex, expr.location) { location, message ->
                        throw Exception(location.format(message))
                    }
                    when (target) {
                        is CallResolver.CallTarget.Builtin -> inferBuiltInCallType(expr)
                        is CallResolver.CallTarget.Constructor -> Type.CLASS(target.className)
                        is CallResolver.CallTarget.Function -> target.returnType
                    }
                }

                is Expr.MemberAccess -> {
                    val receiverType = inferExprType(expr.receiver)
                    val className = (receiverType as? Type.CLASS)?.name
                        ?: throw Exception("Member access requires class instance receiver.")
                    val classInfo = classes[className]
                        ?: throw Exception("Unknown class '$className'.")
                    classInfo.fieldsByName[expr.member]?.type
                        ?: throw Exception("Class '$className' has no field '${expr.member}'.")
                }

                is Expr.MemberCall -> {
                    val receiverType = inferExprType(expr.receiver)
                    val className = (receiverType as? Type.CLASS)?.name
                        ?: throw Exception("Method call requires class instance receiver.")
                    val classInfo = classes[className]
                        ?: throw Exception("Unknown class '$className'.")
                    classInfo.methods[expr.method]?.returnType
                        ?: throw Exception("Class '$className' has no method '${expr.method}'.")
                }

                is Expr.ListLiteral -> inferListLiteralType(expr)
                is Expr.Index -> {
                    val targetType = inferExprType(expr.target) as? Type.LIST
                        ?: throw Exception("Indexing is only supported on List values.")
                    targetType.elementType ?: Type.INT
                }
                is Expr.BinaryOp -> when (expr.operator) {
                    ">", "<", ">=", "<=", "==", "!=", "&&", "||" -> Type.BOOL
                    "+" -> {
                        val leftType = inferExprType(expr.left)
                        val rightType = inferExprType(expr.right)
                        when {
                            leftType == Type.STRING && rightType == Type.STRING -> Type.STRING
                            leftType == Type.INT && rightType == Type.INT -> Type.INT
                            TypeRules.isNumeric(leftType) && TypeRules.isNumeric(rightType) -> Type.FLOAT
                            else -> throw Exception("Unsupported operand types for '+': $leftType and $rightType.")
                        }
                    }

                    "-", "*", "/" -> {
                        val leftType = inferExprType(expr.left)
                        val rightType = inferExprType(expr.right)
                        when {
                            leftType == Type.INT && rightType == Type.INT -> Type.INT
                            TypeRules.isNumeric(leftType) && TypeRules.isNumeric(rightType) -> Type.FLOAT
                            else -> throw Exception("Unsupported operand types for '${expr.operator}': $leftType and $rightType.")
                        }
                    }

                    "%" -> Type.INT
                    else -> Type.INT
                }
            }
        }
    }

    private fun inferListLiteralType(expr: Expr.ListLiteral): Type {
        with(state) {
            if (expr.elements.isEmpty()) {
                return Type.LIST(Type.INT)
            }

            var elementType = inferExprType(expr.elements.first())
            expr.elements.drop(1).forEach { element ->
                val nextType = inferExprType(element)
                if (elementType == nextType) {
                    return@forEach
                }
                if (TypeRules.isNumeric(elementType) && TypeRules.isNumeric(nextType)) {
                    elementType = Type.FLOAT
                    return@forEach
                }
                throw Exception(
                    "List literal element types must be homogeneous. Found $elementType and $nextType."
                )
            }
            return Type.LIST(elementType)
        }
    }

    private fun inferBuiltInCallType(expr: Expr.Call): Type {
        with(state) {
            return when (expr.name) {
                "len" -> {
                    if (expr.args.size != 1) {
                        throw Exception("len expects 1 argument, got ${expr.args.size}.")
                    }
                    Type.INT
                }

                "substr" -> {
                    if (expr.args.size != 3) {
                        throw Exception("substr expects 3 arguments, got ${expr.args.size}.")
                    }
                    Type.STRING
                }

                "contains" -> {
                    if (expr.args.size != 2) {
                        throw Exception("contains expects 2 arguments, got ${expr.args.size}.")
                    }
                    Type.BOOL
                }

                "to_int" -> {
                    if (expr.args.size != 1) {
                        throw Exception("to_int expects 1 argument, got ${expr.args.size}.")
                    }
                    Type.INT
                }

                "min", "max" -> {
                    if (expr.args.size != 2) {
                        throw Exception("${expr.name} expects 2 arguments, got ${expr.args.size}.")
                    }
                    val left = inferExprType(expr.args[0])
                    val right = inferExprType(expr.args[1])
                    if (!TypeRules.isNumeric(left) || !TypeRules.isNumeric(right)) {
                        throw Exception("${expr.name} expects numeric arguments.")
                    }
                    if (left == Type.INT && right == Type.INT) Type.INT else Type.FLOAT
                }

                "abs" -> {
                    if (expr.args.size != 1) {
                        throw Exception("abs expects 1 argument, got ${expr.args.size}.")
                    }
                    val argType = inferExprType(expr.args[0])
                    if (!TypeRules.isNumeric(argType)) {
                        throw Exception("abs expects a numeric argument.")
                    }
                    argType
                }
                else -> throw Exception("Unknown built-in function '${expr.name}'.")
            }
        }
    }

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
                            val operandType = inferExprType(expr.operand)
                            if (operandType != Type.BOOL) {
                                throw Exception("NOT operator requires Bool operand.")
                            }
                            LLVMBuildNot(builder, operand, "nottmp")
                        }

                        else -> throw Exception("Unknown unary operator: ${expr.operator}")
                    }
                }

                is Expr.Call -> buildCall(expr, allowVoid = false)
                is Expr.MemberAccess -> buildMemberAccess(expr)
                is Expr.MemberCall -> buildMemberCall(expr, allowVoid = false)
                is Expr.ListLiteral -> buildListLiteral(expr)
                is Expr.Index -> buildIndexExpr(expr)

                is Expr.BinaryOp -> {
                    if (expr.operator == "&&" || expr.operator == "||") {
                        return buildShortCircuitLogical(expr)
                    }

                    var left = state.emit(expr.left)
                    var right = state.emit(expr.right)
                    val leftType = inferExprType(expr.left)
                    val rightType = inferExprType(expr.right)

                    if (expr.operator == "+" && leftType == Type.STRING && rightType == Type.STRING) {
                        return buildStringConcat(left, right)
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
            val receiverType = inferExprType(expr.receiver)
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

            if (inferExprType(expr.left) != Type.BOOL || inferExprType(expr.right) != Type.BOOL) {
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
                val coercedArg = caster.castValueIfNeeded(rawArg, inferExprType(arg), info.paramTypes[index])
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
            val receiverType = inferExprType(expr.receiver)
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
                val coercedArg = caster.castValueIfNeeded(rawArg, inferExprType(arg), methodInfo.paramTypes[index])
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
                val value = caster.castValueIfNeeded(rawValue, inferExprType(expr.args[index]), field.type)
                LLVMBuildStore(builder, value, fieldPtr)
            }

            return objPtr
        }
    }

    fun buildListLength(listPtr: LLVMValueRef): LLVMValueRef {
        with(state) {
            val lenPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 0, "list_len_ptr")
            val lenVal = LLVMBuildLoad2(builder, LLVMInt64TypeInContext(context), lenPtr, "list_len")
            return LLVMBuildTrunc(builder, lenVal, LLVMInt32TypeInContext(context), "list_len_i32")
        }
    }

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

    private fun buildStringConcat(left: LLVMValueRef, right: LLVMValueRef): LLVMValueRef {
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

    private fun buildIndexExpr(expr: Expr.Index): LLVMValueRef {
        with(state) {
            val targetType = inferExprType(expr.target) as? Type.LIST
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
