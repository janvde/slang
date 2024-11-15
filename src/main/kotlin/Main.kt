package nl.endevelopment

import nl.endevelopment.compiler.Compiler
import java.io.File

fun main(args: Array<String>) {
    if (args.size != 2) {
        println("Usage: Compiler <source_file> <output_ir_file>")
    }

    val sourceFilePath = args.getOrNull(0)
        ?: Compiler::class.java.getResource("/example.slang")?.path
        ?: throw IllegalArgumentException("Resource file 'example.slang' not found")

    val outputIRFilePath = args.getOrNull(1) ?: "output.ll"

    val sourceCode = File(sourceFilePath).readText()

    val compiler = Compiler()
    compiler.compile(sourceCode, outputIRFilePath)
}
