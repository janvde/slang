package nl.endevelopment.semantic.typechecker

import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.core.BuiltinRegistry

internal class DeclarationCollectorPass(
    private val state: TypeCheckState,
    private val builtins: BuiltinRegistry
) {
    fun collect(program: Program) {
        program.statements.filterIsInstance<Stmt.ClassDef>().forEach { declareClass(it) }
        program.statements.filterIsInstance<Stmt.FunctionDef>().forEach { declareFunction(it) }
    }

    private fun declareClass(stmt: Stmt.ClassDef) {
        val existingClass = state.classes[stmt.name]
        if (existingClass != null) {
            DiagnosticFactory.failWithRelated(
                stmt.location,
                "Class '${stmt.name}' is already defined.",
                existingClass.declarationLocation
            )
        }

        val existingFunction = state.functions[stmt.name]
        if (existingFunction != null) {
            DiagnosticFactory.failWithRelated(
                stmt.location,
                "Class '${stmt.name}' conflicts with function '${stmt.name}'.",
                existingFunction.location
            )
        }

        val fields = linkedMapOf<String, ClassFieldInfo>()
        stmt.fields.forEach { field ->
            val existingField = fields[field.name]
            if (existingField != null) {
                DiagnosticFactory.failWithRelated(
                    field.location,
                    "Field '${field.name}' is already defined in class '${stmt.name}'.",
                    existingField.location
                )
            }
            fields[field.name] = ClassFieldInfo(field.type, field.mutable, field.location)
        }

        val methods = linkedMapOf<String, MethodInfo>()
        stmt.methods.forEach { method ->
            val existingMethod = methods[method.name]
            if (existingMethod != null) {
                DiagnosticFactory.failWithRelated(
                    method.location,
                    "Method '${method.name}' is already defined in class '${stmt.name}'.",
                    existingMethod.location
                )
            }
            methods[method.name] = MethodInfo(method.returnType, method.params.map { it.type }, method.location)
        }

        state.classes[stmt.name] = ClassInfo(
            declarationLocation = stmt.location,
            fields = fields,
            methods = methods
        )
    }

    private fun declareFunction(stmt: Stmt.FunctionDef) {
        if (builtins.isBuiltin(stmt.name)) {
            DiagnosticFactory.fail(stmt.location, "Cannot redefine built-in function '${stmt.name}'.")
        }

        val existingClass = state.classes[stmt.name]
        if (existingClass != null) {
            DiagnosticFactory.failWithRelated(
                stmt.location,
                "Function '${stmt.name}' conflicts with class '${stmt.name}'.",
                existingClass.declarationLocation
            )
        }

        val existingFunction = state.functions[stmt.name]
        if (existingFunction != null) {
            DiagnosticFactory.failWithRelated(
                stmt.location,
                "Function '${stmt.name}' is already defined.",
                existingFunction.location
            )
        }

        state.functions[stmt.name] = FunctionInfo(stmt.returnType, stmt.params.map { it.type }, stmt.location)
    }
}
