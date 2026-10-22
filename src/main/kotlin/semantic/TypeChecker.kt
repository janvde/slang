package nl.endevelopment.semantic

import nl.endevelopment.ast.Program
import nl.endevelopment.semantic.core.BuiltinRegistry
import nl.endevelopment.semantic.core.CallResolver
import nl.endevelopment.semantic.typechecker.DeclarationCollectorPass
import nl.endevelopment.semantic.typechecker.ExprTyper
import nl.endevelopment.semantic.typechecker.StatementCheckerPass
import nl.endevelopment.semantic.typechecker.TypeCheckState

class TypeChecker {
    private val builtins = BuiltinRegistry()
    private val callResolver = CallResolver(builtins)

    fun check(program: Program) {
        val state = TypeCheckState()
        state.reset(program)

        val declarationPass = DeclarationCollectorPass(state, builtins)
        val exprTyper = ExprTyper(state, builtins, callResolver)
        val statementPass = StatementCheckerPass(state, exprTyper)

        state.scopes.enterScope()
        try {
            declarationPass.collect(program)
            statementPass.check(program)
        } finally {
            state.scopes.exitScope()
        }
    }
}
