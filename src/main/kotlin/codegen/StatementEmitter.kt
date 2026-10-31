package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type
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
                is Stmt.PrintStmt -> printing.emitPrint(stmt.expr, stmt.expr.location)
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
            val exprValue = caster.castValueIfNeeded(rawExprValue, expressionTypes.inferExprType(stmt.expr), stmt.type)
            val varPtr = LLVMBuildAlloca(builder, types.llvmTypeFor(stmt.type), stmt.name)
            LLVMBuildStore(builder, exprValue, varPtr)
            codegenSymbolTable[stmt.name] = SymbolInfo(stmt.type, varPtr, immutable = true)
        }
    }

    private fun emitVarStmt(stmt: Stmt.VarStmt) {
        with(state) {
            val rawExprValue = state.emit(stmt.expr)
            val exprValue = caster.castValueIfNeeded(rawExprValue, expressionTypes.inferExprType(stmt.expr), stmt.type)
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
            val exprValue = caster.castValueIfNeeded(rawExprValue, expressionTypes.inferExprType(stmt.expr), symbol.type)
            LLVMBuildStore(builder, exprValue, symbol.ptr)
        }
    }

    private fun emitMemberAssignStmt(stmt: Stmt.MemberAssignStmt) {
        with(state) {
            val targetType = expressionTypes.inferExprType(stmt.target)
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
            val value = caster.castValueIfNeeded(rawValue, expressionTypes.inferExprType(stmt.expr), field.type)
            LLVMBuildStore(builder, value, fieldPtr)
        }
    }

    private fun emitIfStmt(stmt: Stmt.IfStmt) {
        with(state) {
            if (expressionTypes.inferExprType(stmt.condition) != Type.BOOL) {
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
            if (expressionTypes.inferExprType(stmt.condition) != Type.BOOL) {
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
                if (expressionTypes.inferExprType(stmt.condition) != Type.BOOL) {
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
                    caster.castValueIfNeeded(raw, expressionTypes.inferExprType(expr), expected)
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
                is Expr.Call -> calls.buildCall(expr, allowVoid = true)
                is Expr.MemberCall -> calls.buildMemberCall(expr, allowVoid = true)
                else -> state.emit(expr)
            }
        }
    }
}
