package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

internal class DefaultStatementEmitter(private val state: CodegenContext) : StatementEmitter {
    override fun emit(stmt: Stmt) {
        with(state) {
            when (stmt) {
                is Stmt.IfStmt -> emitIfStmt(stmt)
                is Stmt.LetStmt -> emitLetStmt(stmt)
                is Stmt.VarStmt -> emitVarStmt(stmt)
                is Stmt.AssignStmt -> emitAssignStmt(stmt)
                is Stmt.MemberAssignStmt -> emitMemberAssignStmt(stmt)
                is Stmt.PrintStmt -> emitPrintStmt(stmt)
                is Stmt.WhileStmt -> emitWhileStmt(stmt)
                is Stmt.ForStmt -> emitForStmt(stmt)
                is Stmt.BreakStmt -> emitBreakStmt(stmt)
                is Stmt.ContinueStmt -> emitContinueStmt(stmt)
                is Stmt.FunctionDef -> {}
                is Stmt.ClassDef -> {}
                is Stmt.ReturnStmt -> emitReturnStmt(stmt)
                is Stmt.ExprStmt -> emitExprStmt(stmt)
            }
        }
    }

    private fun emitLetStmt(stmt: Stmt.LetStmt) {
        with(state) {
            val rawExprValue = state.emit(stmt.expr)
            val exprValue = caster.castValueIfNeeded(rawExprValue, expressions.inferExprType(stmt.expr), stmt.type)
            val varPtr = LLVMBuildAlloca(builder, types.llvmTypeFor(stmt.type), stmt.name)
            LLVMBuildStore(builder, exprValue, varPtr)
            codegenSymbolTable[stmt.name] = SymbolInfo(stmt.type, varPtr, immutable = true)
        }
    }

    private fun emitVarStmt(stmt: Stmt.VarStmt) {
        with(state) {
            val rawExprValue = state.emit(stmt.expr)
            val exprValue = caster.castValueIfNeeded(rawExprValue, expressions.inferExprType(stmt.expr), stmt.type)
            val varPtr = LLVMBuildAlloca(builder, types.llvmTypeFor(stmt.type), stmt.name)
            LLVMBuildStore(builder, exprValue, varPtr)
            codegenSymbolTable[stmt.name] = SymbolInfo(stmt.type, varPtr, immutable = false)
        }
    }

    private fun emitAssignStmt(stmt: Stmt.AssignStmt) {
        with(state) {
            val symbol = codegenSymbolTable[stmt.name]
                ?: throw Exception("Undefined variable '${stmt.name}'.")

            if (symbol.immutable) {
                throw Exception("Cannot reassign immutable variable '${stmt.name}'.")
            }

            val rawExprValue = state.emit(stmt.expr)
            val exprValue = caster.castValueIfNeeded(rawExprValue, expressions.inferExprType(stmt.expr), symbol.type)
            LLVMBuildStore(builder, exprValue, symbol.ptr)
        }
    }

    private fun emitMemberAssignStmt(stmt: Stmt.MemberAssignStmt) {
        with(state) {
            val targetType = expressions.inferExprType(stmt.target)
            val classType = targetType as? Type.CLASS
                ?: fail(stmt.target.location, "Field assignment target must be a class instance.")
            val classInfo = classes[classType.name]
                ?: fail(stmt.target.location, "Unknown class '${classType.name}'.")
            val field = classInfo.fieldsByName[stmt.member]
                ?: fail(stmt.location, "Class '${classType.name}' has no field '${stmt.member}'.")

            if (!field.mutable) {
                fail(stmt.location, "Cannot assign to immutable field '${stmt.member}' in class '${classType.name}'.")
            }

            val targetValue = state.emit(stmt.target)
            val fieldPtr = LLVMBuildStructGEP2(builder, classInfo.structType, targetValue, field.index, "${stmt.member}_ptr")
            val rawValue = state.emit(stmt.expr)
            val value = caster.castValueIfNeeded(rawValue, expressions.inferExprType(stmt.expr), field.type)
            LLVMBuildStore(builder, value, fieldPtr)
        }
    }

    private fun emitPrintStmt(stmt: Stmt.PrintStmt) {
        with(state) {
            emitPrint(stmt.expr, stmt.expr.location)
        }
    }

    private fun emitPrint(expr: Expr, location: SourceLocation) {
        with(state) {
            val exprValue = state.emit(expr)
            val printfFunc = LLVMGetNamedFunction(module, "printf") ?: runtime.declarePrintf()
            val exprType = expressions.inferExprType(expr)

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

    private fun emitIfStmt(stmt: Stmt.IfStmt) {
        with(state) {
            if (expressions.inferExprType(stmt.condition) != Type.BOOL) {
                fail(stmt.condition.location, "If condition must be Bool.")
            }
            val condValue = state.emit(stmt.condition)

            val currentBlock = LLVMGetInsertBlock(builder)
            val parentFunction = LLVMGetBasicBlockParent(currentBlock)

            val thenBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "then")
            val elseBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "else")
            val mergeBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "merge")

            LLVMBuildCondBr(builder, condValue, thenBlock, elseBlock)

            LLVMPositionBuilderAtEnd(builder, thenBlock)
            stmt.thenBranch.forEach { state.emit(it) }
            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                LLVMBuildBr(builder, mergeBlock)
            }

            LLVMPositionBuilderAtEnd(builder, elseBlock)
            stmt.elseBranch?.forEach { state.emit(it) }
            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                LLVMBuildBr(builder, mergeBlock)
            }

            LLVMPositionBuilderAtEnd(builder, mergeBlock)
        }
    }

    private fun emitWhileStmt(stmt: Stmt.WhileStmt) {
        with(state) {
            val currentBlock = LLVMGetInsertBlock(builder)
            val parentFunction = LLVMGetBasicBlockParent(currentBlock)
            if (expressions.inferExprType(stmt.condition) != Type.BOOL) {
                fail(stmt.condition.location, "While condition must be Bool.")
            }

            val condBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "while_cond")
            val bodyBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "while_body")
            val afterBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "while_after")

            LLVMBuildBr(builder, condBlock)

            LLVMPositionBuilderAtEnd(builder, condBlock)
            val condValue = state.emit(stmt.condition)

            LLVMBuildCondBr(builder, condValue, bodyBlock, afterBlock)

            LLVMPositionBuilderAtEnd(builder, bodyBlock)
            loopTargets.add(LoopTarget(continueBlock = condBlock, breakBlock = afterBlock))
            try {
                stmt.body.forEach { state.emit(it) }
            } finally {
                loopTargets.removeAt(loopTargets.size - 1)
            }
            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                LLVMBuildBr(builder, condBlock)
            }

            LLVMPositionBuilderAtEnd(builder, afterBlock)
        }
    }

    private fun emitForStmt(stmt: Stmt.ForStmt) {
        with(state) {
            stmt.init?.let { state.emit(it) }

            val currentBlock = LLVMGetInsertBlock(builder)
            val parentFunction = LLVMGetBasicBlockParent(currentBlock)

            val condBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "for_cond")
            val bodyBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "for_body")
            val updateBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "for_update")
            val afterBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "for_after")

            LLVMBuildBr(builder, condBlock)

            LLVMPositionBuilderAtEnd(builder, condBlock)
            if (stmt.condition != null) {
                if (expressions.inferExprType(stmt.condition) != Type.BOOL) {
                    fail(stmt.condition.location, "For-loop condition must be Bool.")
                }
                val condValue = state.emit(stmt.condition)
                LLVMBuildCondBr(builder, condValue, bodyBlock, afterBlock)
            } else {
                LLVMBuildBr(builder, bodyBlock)
            }

            LLVMPositionBuilderAtEnd(builder, bodyBlock)
            loopTargets.add(LoopTarget(continueBlock = updateBlock, breakBlock = afterBlock))
            try {
                stmt.body.forEach { state.emit(it) }
            } finally {
                loopTargets.removeAt(loopTargets.size - 1)
            }
            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                LLVMBuildBr(builder, updateBlock)
            }

            LLVMPositionBuilderAtEnd(builder, updateBlock)
            stmt.update?.let { state.emit(it) }
            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                LLVMBuildBr(builder, condBlock)
            }

            LLVMPositionBuilderAtEnd(builder, afterBlock)
        }
    }

    private fun emitBreakStmt(stmt: Stmt.BreakStmt) {
        with(state) {
            val target = loopTargets.lastOrNull()
                ?: fail(stmt.location, "'break' is only allowed inside a loop.")
            LLVMBuildBr(builder, target.breakBlock)
        }
    }

    private fun emitContinueStmt(stmt: Stmt.ContinueStmt) {
        with(state) {
            val target = loopTargets.lastOrNull()
                ?: fail(stmt.location, "'continue' is only allowed inside a loop.")
            LLVMBuildBr(builder, target.continueBlock)
        }
    }

    private fun emitReturnStmt(stmt: Stmt.ReturnStmt) {
        with(state) {
            val expectedReturnType = currentFunctionReturnType
                ?: throw Exception("Return statement is only valid inside a function.")
            val returnValue = stmt.expr?.let { expr ->
                val raw = state.emit(expr)
                val expected = expectedReturnType
                if (expected != null) {
                    caster.castValueIfNeeded(raw, expressions.inferExprType(expr), expected)
                } else {
                    raw
                }
            }

            if (expectedReturnType == Type.VOID) {
                if (returnValue != null) {
                    throw Exception("Void function should not return a value.")
                }
                LLVMBuildRetVoid(builder)
                return
            }

            if (returnValue == null) {
                throw Exception("Non-void function must return a value.")
            }
            LLVMBuildRet(builder, returnValue)
        }
    }

    private fun emitExprStmt(stmt: Stmt.ExprStmt) {
        with(state) {
            when (val expr = stmt.expr) {
                is Expr.Call -> expressions.buildCall(expr, allowVoid = true)
                is Expr.MemberCall -> expressions.buildMemberCall(expr, allowVoid = true)
                else -> state.emit(expr)
            }
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
