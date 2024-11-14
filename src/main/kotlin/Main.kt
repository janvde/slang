package nl.endevelopment

import nl.endevelopment.compiler.Compiler
import java.io.File

fun main(args: Array<String>) {
    if (args.size != 2) {
        println("Usage: Compiler <source_file> <output_ir_file>")
    }

    val sourceFilePath = args.getOrNull(0) ?: "C:\\Users\\janva\\IdeaProjects\\slang\\src\\main\\resources\\example.slang"
    val outputIRFilePath = args.getOrNull(1) ?: "output.ll"

    val sourceCode = File(sourceFilePath).readText()

    val compiler = Compiler()
    compiler.compile(sourceCode, outputIRFilePath)
}
