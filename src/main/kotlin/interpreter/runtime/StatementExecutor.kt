package nl.endevelopment.interpreter.runtime

import nl.endevelopment.ast.Stmt
import nl.endevelopment.interpreter.Value

class StatementExecutor(
    private val context: RuntimeContext,
    private val declarationLoader: DeclarationLoader,
    private val evaluator: ExpressionEvaluator
) {
    fun executeBlock(statements: List<Stmt>) {
        for (stmt in statements) {
            execute(stmt)
        }
    }

    fun execute(stmt: Stmt) {
        when (stmt) {
            is Stmt.LetStmt -> handleLetStmt(stmt)
            is Stmt.VarStmt -> handleVarStmt(stmt)
            is Stmt.AssignStmt -> handleAssignStmt(stmt)
            is Stmt.MemberAssignStmt -> handleMemberAssignStmt(stmt)
            is Stmt.PrintStmt -> handlePrintStmt(stmt)
            is Stmt.IfStmt -> handleIfStmt(stmt)
            is Stmt.WhileStmt -> handleWhileStmt(stmt)
            is Stmt.ForStmt -> handleForStmt(stmt)
            is Stmt.BreakStmt -> handleBreakStmt(stmt)
            is Stmt.ContinueStmt -> handleContinueStmt(stmt)
            is Stmt.FunctionDef -> declarationLoader.registerFunction(stmt)
            is Stmt.ReturnStmt -> handleReturnStmt(stmt)
            is Stmt.ExprStmt -> handleExprStmt(stmt)
            is Stmt.ClassDef -> declarationLoader.registerClass(stmt)
        }
    }

    private fun handleWhileStmt(stmt: Stmt.WhileStmt) {
        context.variableEnv.enterScope()
        try {
            context.loopDepth++
            while (true) {
                val conditionValue = evaluator.evaluate(stmt.condition, allowVoid = false)
                val conditionResult = evaluator.evaluateCondition(conditionValue, stmt.condition.location)
                if (!conditionResult) {
                    break
                }

                try {
                    for (s in stmt.body) {
                        execute(s)
                    }
                } catch (_: ContinueSignal) {
                    continue
                } catch (_: BreakSignal) {
                    break
                } catch (e: ReturnSignal) {
                    throw e
                }
            }
        } finally {
            context.loopDepth--
            context.variableEnv.exitScope()
        }
    }

    private fun handleForStmt(stmt: Stmt.ForStmt) {
        context.variableEnv.enterScope()
        try {
            context.loopDepth++
            stmt.init?.let { execute(it) }
            while (true) {
                val shouldContinue = stmt.condition?.let {
                    evaluator.evaluateCondition(evaluator.evaluate(it, allowVoid = false), it.location)
                } ?: true

                if (!shouldContinue) {
                    break
                }

                try {
                    executeBlock(stmt.body)
                } catch (_: ContinueSignal) {
                    stmt.update?.let { execute(it) }
                    continue
                } catch (_: BreakSignal) {
                    break
                } catch (e: ReturnSignal) {
                    throw e
                }

                stmt.update?.let { execute(it) }
            }
        } finally {
            context.loopDepth--
            context.variableEnv.exitScope()
        }
    }

    private fun handleBreakStmt(stmt: Stmt.BreakStmt) {
        if (context.loopDepth <= 0) {
            throw RuntimeException(stmt.location.format("'break' is only allowed inside a loop."))
        }
        throw BreakSignal()
    }

    private fun handleContinueStmt(stmt: Stmt.ContinueStmt) {
        if (context.loopDepth <= 0) {
            throw RuntimeException(stmt.location.format("'continue' is only allowed inside a loop."))
        }
        throw ContinueSignal()
    }

    private fun handleLetStmt(stmt: Stmt.LetStmt) {
        val value = evaluator.evaluate(stmt.expr, allowVoid = false)
        context.variableEnv.define(stmt.name, value, immutable = true)
    }

    private fun handleVarStmt(stmt: Stmt.VarStmt) {
        val value = evaluator.evaluate(stmt.expr, allowVoid = false)
        context.variableEnv.define(stmt.name, value, immutable = false)
    }

    private fun handleAssignStmt(stmt: Stmt.AssignStmt) {
        val value = evaluator.evaluate(stmt.expr, allowVoid = false)
        context.variableEnv.set(stmt.name, value, stmt.location)
    }

    private fun handleMemberAssignStmt(stmt: Stmt.MemberAssignStmt) {
        val target = evaluator.evaluate(stmt.target, allowVoid = false)
        if (target !is Value.ObjectValue) {
            throw RuntimeException(stmt.target.location.format("Field assignment target must be a class instance."))
        }

        val classInfo = context.classes[target.className]
            ?: throw RuntimeException(stmt.location.format("Unknown class '${target.className}'."))
        val fieldInfo = classInfo.fieldInfo[stmt.member]
            ?: throw RuntimeException(stmt.location.format("Class '${target.className}' has no field '${stmt.member}'."))
        if (!fieldInfo.mutable) {
            throw RuntimeException(stmt.location.format("Cannot assign to immutable field '${stmt.member}' in class '${target.className}'."))
        }

        target.fields[stmt.member] = evaluator.evaluate(stmt.expr, allowVoid = false)
    }

    private fun handlePrintStmt(stmt: Stmt.PrintStmt) {
        val value = evaluator.evaluate(stmt.expr, allowVoid = false)
        println(evaluator.valueToString(value))
    }

    private fun handleIfStmt(stmt: Stmt.IfStmt) {
        val conditionValue = evaluator.evaluate(stmt.condition, allowVoid = false)
        val conditionResult = evaluator.evaluateCondition(conditionValue, stmt.condition.location)

        context.variableEnv.enterScope()
        try {
            if (conditionResult) {
                executeBlock(stmt.thenBranch)
            } else if (stmt.elseBranch != null) {
                executeBlock(stmt.elseBranch)
            }
        } finally {
            context.variableEnv.exitScope()
        }
    }

    private fun handleReturnStmt(stmt: Stmt.ReturnStmt) {
        if (context.functionDepth <= 0) {
            throw RuntimeException(stmt.location.format("Return statement is only allowed inside a function."))
        }
        val value = stmt.expr?.let { evaluator.evaluate(it, allowVoid = false) } ?: Value.VoidValue
        throw ReturnSignal(value)
    }

    private fun handleExprStmt(stmt: Stmt.ExprStmt) {
        evaluator.evaluate(stmt.expr, allowVoid = true)
    }
}
