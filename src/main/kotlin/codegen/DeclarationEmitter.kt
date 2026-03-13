package codegen

import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt

class DeclarationEmitter(
    private val codeGenerator: CodeGenerator
) {
    fun emitProgramDeclarations(program: Program) {
        val classDefs = program.statements.filterIsInstance<Stmt.ClassDef>()
        codeGenerator.declareClassTypesPhase(classDefs)

        program.statements.filterIsInstance<Stmt.FunctionDef>()
            .forEach { codeGenerator.declareFunctionPhase(it) }

        classDefs.forEach { classDef ->
            classDef.methods.forEach { method ->
                codeGenerator.declareMethodPhase(classDef.name, method)
            }
        }
    }
}
