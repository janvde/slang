package nl.endevelopment.compiler

import codegen.CodeGenerator
import nl.endevelopment.ast.Program
import nl.endevelopment.interpreter.Interpreter
import nl.endevelopment.parser.ASTBuilder
import nl.endevelopment.parser.SlangLexer
import nl.endevelopment.parser.SlangParser
import nl.endevelopment.semantic.TypeChecker
import nl.endevelopment.utils.Utils
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import java.io.File

class Compiler {

    private class CollectingErrorListener : BaseErrorListener() {
        val errors = mutableListOf<String>()

        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String?,
            e: RecognitionException?
        ) {
            val detail = msg ?: "syntax error"
            errors.add("Error at line $line, col ${charPositionInLine + 1}: $detail")
        }
    }

    /**
     * Compiles the given source code and outputs LLVM IR.
     *
     * @param sourceCode The source code to compile.
     * @param outputIRFilePath The file path where the LLVM IR will be written.
     */
    fun compile(sourceCode: String, outputIRFilePath: String) {

        val codeGenerator = CodeGenerator()
        try {
            // 1. Lexical Analysis
            val errorListener = CollectingErrorListener()
            val lexer = SlangLexer(CharStreams.fromString(sourceCode))
            lexer.removeErrorListeners()
            lexer.addErrorListener(errorListener)
            val tokens = CommonTokenStream(lexer)

            // 2. Parsing tokens
            val parser = SlangParser(tokens)
            parser.removeErrorListeners()
            parser.addErrorListener(errorListener)
            val tree = parser.program()
            if (errorListener.errors.isNotEmpty()) {
                throw Exception(errorListener.errors.joinToString("\n"))
            }

            // 3. Building AST
            val astBuilder = ASTBuilder()
            val ast = astBuilder.visit(tree) as Program

            // 4. Type checking
            val typeChecker = TypeChecker()
            typeChecker.check(ast)

            // 5. Interpreter execution
            val interpreter = Interpreter()
            Utils.log("Executing with interpreter...")
            interpreter.interpret(ast)

            // 6. Code Generation using LLVM C API
            codeGenerator.generate(ast)
            codeGenerator.writeIRToFile(outputIRFilePath)
            Utils.log("Code Generation Complete: LLVM IR generated at '$outputIRFilePath'.")

            // 7. (Optional) Compile LLVM IR to Executable
            try {
                val executablePath = outputIRFilePath.removeSuffix(".ll")
                compileLLVMIR(outputIRFilePath, executablePath)
                Utils.log("Executable successfully compiled to '$executablePath'.")
            } catch (e: Exception) {
                Utils.log("Note: Could not compile to native executable (llc/clang not found). LLVM IR is available at '$outputIRFilePath'.")
                Utils.log("To compile manually: llc -filetype=obj $outputIRFilePath -o ${outputIRFilePath.removeSuffix(".ll")}.o && clang ${outputIRFilePath.removeSuffix(".ll")}.o -o ${outputIRFilePath.removeSuffix(".ll")}")
            }

            // 8. Dispose of CodeGenerator Resources
            codeGenerator.dispose()

        } catch (e: Exception) {
            Utils.log("Compilation Error: ${e.message}")
            codeGenerator.dispose()
        }
    }

    /**
     * (Optional) Compiles the LLVM IR file to an executable using LLVM tools.
     *
     * @param irFilePath The path to the LLVM IR file.
     * @param executablePath The desired path for the compiled executable.
     */
    private fun compileLLVMIR(irFilePath: String, executablePath: String) {
        // Ensure that LLVM tools (llc and clang) are installed and accessible
        val llcCommand = "llc"
        val clangCommand = "clang"

        // Step 1: Convert LLVM IR to object file using llc
        val objectFilePath = irFilePath.removeSuffix(".ll") + ".o"
        val llcProcess = ProcessBuilder(llcCommand, "-filetype=obj", irFilePath, "-o", objectFilePath)
            .redirectErrorStream(true)
            .start()
        llcProcess.inputStream.bufferedReader().use { output ->
            output.lines().forEach { line ->
                println(line)
            }
        }
        if (llcProcess.waitFor() != 0) {
            throw Exception("llc failed to compile LLVM IR.")
        }

        // Step 2: Link the object file to create an executable using clang
        val clangProcess = ProcessBuilder(clangCommand, objectFilePath, "-o", executablePath)
            .redirectErrorStream(true)
            .start()
        clangProcess.inputStream.bufferedReader().use { output ->
            output.lines().forEach { line ->
                println(line)
            }
        }
        if (clangProcess.waitFor() != 0) {
            throw Exception("clang failed to link the object file into an executable.")
        }

        // (Optional) Delete the intermediate object file
        File(objectFilePath).delete()
    }
}
