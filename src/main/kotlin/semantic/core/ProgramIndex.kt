package nl.endevelopment.semantic.core

import nl.endevelopment.ast.MethodDef
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type

/**
 * Side-effect free index of top-level program declarations.
 */
class ProgramIndex private constructor(
    val classes: Map<String, ClassSignature>,
    val functions: Map<String, FunctionSignature>
) {
    data class FunctionSignature(
        val name: String,
        val returnType: Type,
        val paramTypes: List<Type>,
        val location: SourceLocation
    )

    data class FieldSignature(
        val name: String,
        val type: Type,
        val mutable: Boolean,
        val location: SourceLocation
    )

    data class MethodSignature(
        val name: String,
        val returnType: Type,
        val paramTypes: List<Type>,
        val location: SourceLocation,
        val ast: MethodDef
    )

    data class ClassSignature(
        val name: String,
        val location: SourceLocation,
        val fields: List<FieldSignature>,
        val methods: List<MethodSignature>
    )

    companion object {
        fun from(program: Program): ProgramIndex {
            val classes = linkedMapOf<String, ClassSignature>()
            val functions = linkedMapOf<String, FunctionSignature>()

            program.statements.filterIsInstance<Stmt.ClassDef>().forEach { classDef ->
                val fields = classDef.fields.map {
                    FieldSignature(it.name, it.type, it.mutable, it.location)
                }
                val methods = classDef.methods.map {
                    MethodSignature(it.name, it.returnType, it.params.map { p -> p.type }, it.location, it)
                }
                classes[classDef.name] = ClassSignature(classDef.name, classDef.location, fields, methods)
            }

            program.statements.filterIsInstance<Stmt.FunctionDef>().forEach { fn ->
                functions[fn.name] = FunctionSignature(
                    name = fn.name,
                    returnType = fn.returnType,
                    paramTypes = fn.params.map { it.type },
                    location = fn.location
                )
            }

            return ProgramIndex(classes = classes, functions = functions)
        }
    }
}
