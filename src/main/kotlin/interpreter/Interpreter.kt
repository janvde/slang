package nl.endevelopment.interpreter

import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.interpreter.runtime.BuiltinRuntime
import nl.endevelopment.interpreter.runtime.DeclarationLoader
import nl.endevelopment.interpreter.runtime.ExpressionEvaluator
import nl.endevelopment.interpreter.runtime.NumericOps
import nl.endevelopment.interpreter.runtime.RuntimeContext
import nl.endevelopment.interpreter.runtime.StatementExecutor

class Interpreter {
    private val context = RuntimeContext()
    private val declarationLoader = DeclarationLoader(context)
    private val numericOps = NumericOps()
    private val builtinRuntime = BuiltinRuntime(numericOps)
    private val expressionEvaluator = ExpressionEvaluator(context, builtinRuntime, numericOps)
    private val statementExecutor = StatementExecutor(context, declarationLoader, expressionEvaluator)

    init {
        expressionEvaluator.executeBlock = statementExecutor::executeBlock
    }

    fun interpret(program: Program) {
        context.variableEnv.enterScope()
        try {
            program.statements.filterIsInstance<Stmt.ClassDef>().forEach { declarationLoader.registerClass(it) }
            program.statements.filterIsInstance<Stmt.FunctionDef>().forEach { declarationLoader.registerFunction(it) }
            program.statements
                .filterNot { it is Stmt.FunctionDef || it is Stmt.ClassDef }
                .forEach { statementExecutor.execute(it) }
        } catch (e: RuntimeException) {
            println("Runtime Error: ${e.message}")
        } finally {
            context.variableEnv.exitScope()
        }
    }
}
