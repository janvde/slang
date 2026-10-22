package nl.endevelopment.semantic.typechecker

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type

internal class StatementCheckerPass(
    private val state: TypeCheckState,
    private val exprTyper: ExprTyper
) {
    fun check(program: Program) {
        program.statements.forEach { checkStatement(it) }
    }

    private fun checkStatement(stmt: Stmt) {
        when (stmt) {
            is Stmt.LetStmt -> {
                ensureNotDefinedInCurrentScope(stmt.name, stmt.location)
                val exprType = exprTyper.inferExprType(stmt.expr, stmt.type)
                exprTyper.ensureAssignable(stmt.type, exprType, stmt.location)
                state.scopes.define(stmt.name, stmt.type, immutable = true, stmt.location)
            }

            is Stmt.VarStmt -> {
                ensureNotDefinedInCurrentScope(stmt.name, stmt.location)
                val exprType = exprTyper.inferExprType(stmt.expr, stmt.type)
                exprTyper.ensureAssignable(stmt.type, exprType, stmt.location)
                state.scopes.define(stmt.name, stmt.type, immutable = false, stmt.location)
            }

            is Stmt.AssignStmt -> {
                val variable = state.scopes.lookup(stmt.name)
                    ?: DiagnosticFactory.fail(stmt.location, "Undefined variable '${stmt.name}'.")
                if (variable.immutable) {
                    DiagnosticFactory.fail(stmt.location, "Cannot reassign immutable variable '${stmt.name}'.")
                }
                val exprType = exprTyper.inferExprType(stmt.expr, variable.type)
                exprTyper.ensureAssignable(variable.type, exprType, stmt.location)
            }

            is Stmt.MemberAssignStmt -> {
                val targetType = exprTyper.inferExprType(stmt.target)
                val className = (targetType as? Type.CLASS)?.name
                    ?: DiagnosticFactory.fail(stmt.target.location, "Field assignment target must be a class instance.")
                val classInfo = state.classes[className]
                    ?: DiagnosticFactory.fail(stmt.target.location, "Unknown class '$className'.")
                val fieldInfo = classInfo.fields[stmt.member]
                    ?: DiagnosticFactory.fail(stmt.location, "Class '$className' has no field '${stmt.member}'.")
                if (!fieldInfo.mutable) {
                    DiagnosticFactory.fail(stmt.location, "Cannot assign to immutable field '${stmt.member}' in class '$className'.")
                }
                val valueType = exprTyper.inferExprType(stmt.expr, fieldInfo.type)
                exprTyper.ensureAssignable(fieldInfo.type, valueType, stmt.location)
            }

            is Stmt.PrintStmt -> {
                val exprType = exprTyper.inferExprType(stmt.expr)
                if (exprType is Type.CLASS) {
                    DiagnosticFactory.fail(stmt.expr.location, "Printing class instances is not supported in v1.")
                }
            }

            is Stmt.IfStmt -> {
                val conditionType = exprTyper.inferExprType(stmt.condition)
                if (conditionType != Type.BOOL) {
                    DiagnosticFactory.fail(stmt.condition.location, "If condition must be Bool, got ${DiagnosticFactory.formatType(conditionType)}.")
                }

                state.scopes.enterScope()
                try {
                    stmt.thenBranch.forEach { checkStatement(it) }
                } finally {
                    state.scopes.exitScope()
                }

                stmt.elseBranch?.let { elseBranch ->
                    state.scopes.enterScope()
                    try {
                        elseBranch.forEach { checkStatement(it) }
                    } finally {
                        state.scopes.exitScope()
                    }
                }
            }

            is Stmt.WhileStmt -> {
                val conditionType = exprTyper.inferExprType(stmt.condition)
                if (conditionType != Type.BOOL) {
                    DiagnosticFactory.fail(stmt.condition.location, "While condition must be Bool, got ${DiagnosticFactory.formatType(conditionType)}.")
                }
                state.loopDepth++
                state.scopes.enterScope()
                try {
                    stmt.body.forEach { checkStatement(it) }
                } finally {
                    state.scopes.exitScope()
                    state.loopDepth--
                }
            }

            is Stmt.ForStmt -> {
                state.scopes.enterScope()
                try {
                    stmt.init?.let { checkStatement(it) }

                    stmt.condition?.let { cond ->
                        val conditionType = exprTyper.inferExprType(cond)
                        if (conditionType != Type.BOOL) {
                            DiagnosticFactory.fail(cond.location, "For-loop condition must be Bool, got ${DiagnosticFactory.formatType(conditionType)}.")
                        }
                    }

                    state.loopDepth++
                    try {
                        stmt.body.forEach { checkStatement(it) }
                        stmt.update?.let { checkForUpdate(it) }
                    } finally {
                        state.loopDepth--
                    }
                } finally {
                    state.scopes.exitScope()
                }
            }

            is Stmt.BreakStmt -> {
                if (state.loopDepth <= 0) {
                    DiagnosticFactory.fail(stmt.location, "'break' is only allowed inside a loop.")
                }
            }

            is Stmt.ContinueStmt -> {
                if (state.loopDepth <= 0) {
                    DiagnosticFactory.fail(stmt.location, "'continue' is only allowed inside a loop.")
                }
            }

            is Stmt.FunctionDef -> checkFunctionBody(stmt)

            is Stmt.ReturnStmt -> {
                val expected = state.currentReturnType
                    ?: DiagnosticFactory.fail(stmt.location, "Return statement is only allowed inside a function or method.")
                val actual = stmt.expr?.let { exprTyper.inferExprType(it, expected) } ?: Type.VOID
                exprTyper.ensureAssignable(expected, actual, stmt.location)
            }

            is Stmt.ExprStmt -> {
                exprTyper.inferExprType(stmt.expr)
            }

            is Stmt.ClassDef -> checkClassBody(stmt)
        }
    }

    private fun checkClassBody(stmt: Stmt.ClassDef) {
        val classInfo = state.classes[stmt.name]
            ?: DiagnosticFactory.fail(stmt.location, "Unknown class '${stmt.name}'.")

        val previousClassName = state.currentClassName
        val previousReturnType = state.currentReturnType
        state.currentClassName = stmt.name

        try {
            stmt.methods.forEach { method ->
                val methodInfo = classInfo.methods[method.name]
                    ?: DiagnosticFactory.fail(method.location, "Unknown method '${method.name}' in class '${stmt.name}'.")

                state.currentReturnType = methodInfo.returnType
                state.scopes.enterScope()
                try {
                    state.scopes.define("this", Type.CLASS(stmt.name), immutable = true, method.location)
                    method.params.forEach { param ->
                        ensureNotDefinedInCurrentScope(param.name, param.location)
                        state.scopes.define(param.name, param.type, immutable = false, param.location)
                    }
                    method.body.forEach { checkStatement(it) }
                } finally {
                    state.scopes.exitScope()
                }
            }
        } finally {
            state.currentClassName = previousClassName
            state.currentReturnType = previousReturnType
        }
    }

    private fun checkFunctionBody(stmt: Stmt.FunctionDef) {
        val info = state.functions[stmt.name]
            ?: DiagnosticFactory.fail(stmt.location, "Unknown function '${stmt.name}'.")
        val previousReturnType = state.currentReturnType
        val previousClassName = state.currentClassName
        state.currentClassName = null
        state.currentReturnType = info.returnType

        state.scopes.enterScope()
        try {
            stmt.params.forEach { param ->
                ensureNotDefinedInCurrentScope(param.name, param.location)
                state.scopes.define(param.name, param.type, immutable = false, param.location)
            }
            stmt.body.forEach { checkStatement(it) }
        } finally {
            state.scopes.exitScope()
            state.currentReturnType = previousReturnType
            state.currentClassName = previousClassName
        }
    }

    private fun checkForUpdate(stmt: Stmt) {
        when (stmt) {
            is Stmt.AssignStmt,
            is Stmt.MemberAssignStmt -> checkStatement(stmt)

            is Stmt.ExprStmt -> {
                if (stmt.expr !is Expr.Call && stmt.expr !is Expr.MemberCall) {
                    DiagnosticFactory.fail(stmt.location, "For-loop update must be an assignment or call expression.")
                }
                exprTyper.inferExprType(stmt.expr)
            }

            else -> DiagnosticFactory.fail(stmt.location, "For-loop update must be an assignment or call expression.")
        }
    }

    private fun ensureNotDefinedInCurrentScope(name: String, location: nl.endevelopment.ast.SourceLocation) {
        val existing = state.scopes.lookupCurrentScope(name)
        if (existing != null) {
            DiagnosticFactory.failWithRelated(
                location,
                "Variable '$name' is already defined in this scope.",
                existing.location
            )
        }
    }
}
