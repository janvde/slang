package nl.endevelopment.compiler

import codegen.CodeGenerator
import codegen.LLVMCodeGenerator
import nl.endevelopment.ast.Program
import nl.endevelopment.interpreter.Interpreter
import nl.endevelopment.lexer.RegexLexer
import nl.endevelopment.parser.ASTBuilder
import nl.endevelopment.parser.SlangLexer
import nl.endevelopment.parser.SlangParser
import nl.endevelopment.utils.Utils
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File

class Compiler {
    /**
     * Compiles the given source code and outputs LLVM IR.
     *
     * @param sourceCode The source code to compile.
     * @param outputIRFilePath The file path where the LLVM IR will be written.
     */
    fun compile(sourceCode: String, outputIRFilePath: String) {

        val codeGenerator = LLVMCodeGenerator()
        try {
//            // 1. Lexical Analysis
//            val lexer = RegexLexer(sourceCode) // Assume a RegexLexer class exists
//            val tokens = lexer.tokenize()
//            Utils.log("Lexical Analysis Complete: ${tokens.size} tokens found.")
//
//            // 2. Syntax Analysis
//            val parser = Parser(tokens) // Assume a Parser class exists
//            val ast = parser.parse() as Program
//            Utils.log("Syntax Analysis Complete: AST generated.")


            // 1. Lexical Analysis
            val lexer = SlangLexer(CharStreams.fromString(sourceCode))
            val tokens = CommonTokenStream(lexer)

            // 2. Parsing tokens
            val parser = SlangParser(tokens)
            val tree = parser.program()

            // 3. Building AST
            val astBuilder = ASTBuilder()
            val ast = astBuilder.visit(tree) as Program


            // 4. Interpreter execution
            val interpreter = Interpreter()
            Utils.log("Executing with interpreter...")
            interpreter.interpret(ast)

            // 5. Code Generation
            val ir = codeGenerator.generate(ast)
            codeGenerator.writeIRToFile(outputIRFilePath, ir)
            Utils.log("Code Generation Complete: LLVM IR generated at '$outputIRFilePath'.")
//             codeGenerator.printIR()

            // 6. (Optional) Compile LLVM IR to Executable
            val executablePath = outputIRFilePath.removeSuffix(".ll") // e.g., output.ll -> output
            compileLLVMIR(outputIRFilePath, executablePath)
            Utils.log("Executable successfully compiled to '$executablePath'.")

            // 6. Dispose of CodeGenerator Resources
//            codeGenerator.dispose()

        } catch (e: Exception) {
            Utils.log("Compilation Error: ${e.message}")
//            codeGenerator.dispose()
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