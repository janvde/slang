package nl.endevelopment.interpreter.runtime

import nl.endevelopment.ast.Stmt

class DeclarationLoader(
    private val context: RuntimeContext
) {
    fun registerClass(stmt: Stmt.ClassDef) {
        if (context.classes.containsKey(stmt.name)) {
            throw RuntimeException(stmt.location.format("Class '${stmt.name}' is already defined."))
        }

        val fieldInfo = linkedMapOf<String, ClassFieldRuntimeInfo>()
        val fieldOrder = mutableListOf<String>()
        stmt.fields.forEach { field ->
            if (fieldInfo.containsKey(field.name)) {
                throw RuntimeException(field.location.format("Field '${field.name}' is already defined in class '${stmt.name}'."))
            }
            fieldInfo[field.name] = ClassFieldRuntimeInfo(field.mutable)
            fieldOrder.add(field.name)
        }

        val methods = linkedMapOf<String, nl.endevelopment.ast.MethodDef>()
        stmt.methods.forEach { method ->
            if (methods.containsKey(method.name)) {
                throw RuntimeException(method.location.format("Method '${method.name}' is already defined in class '${stmt.name}'."))
            }
            methods[method.name] = method
        }

        context.classes[stmt.name] = ClassRuntimeInfo(
            fieldOrder = fieldOrder,
            fieldInfo = fieldInfo,
            methods = methods
        )
    }

    fun registerFunction(stmt: Stmt.FunctionDef) {
        if (stmt.name in context.builtins) {
            throw RuntimeException(stmt.location.format("Cannot redefine built-in function '${stmt.name}'."))
        }
        if (context.functions.containsKey(stmt.name)) {
            throw RuntimeException(stmt.location.format("Function '${stmt.name}' is already defined."))
        }
        context.functions[stmt.name] = stmt
    }
}
