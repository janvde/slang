package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type
import nl.endevelopment.semantic.core.ProgramIndex
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

/** Coordinates code generation; emitters own the LLVM instruction and declaration logic. */
class CodeGenerator {
    private val state = CodegenContext()

    fun generate(program: Program) {
        with(state) {
            programIndex = ProgramIndex.from(program)
            val classDefs = program.statements.filterIsInstance<Stmt.ClassDef>()

            declarations.emitProgramDeclarations(program)

            program.statements.filterIsInstance<Stmt.FunctionDef>()
                .forEach { declarations.generateFunctionBody(it) }

            classDefs.forEach { classDef ->
                classDef.methods.forEach { method ->
                    declarations.generateMethodBody(classDef.name, method)
                }
            }

            val mainFuncType = LLVMFunctionType(
                LLVMInt32TypeInContext(context),
                LLVMTypeRef(),
                0,
                0
            )
            val mainFunction = LLVMAddFunction(module, "main", mainFuncType)
            val entry = LLVMAppendBasicBlockInContext(context, mainFunction, "entry")
            LLVMPositionBuilderAtEnd(builder, entry)

            val previousSymbols = codegenSymbolTable
            codegenSymbolTable = mutableMapOf()

            val previousReturnType = currentFunctionReturnType
            currentFunctionReturnType = Type.INT

            val previousClass = currentClassName
            currentClassName = null

            program.statements.forEach {
                if (it !is Stmt.FunctionDef && it !is Stmt.ClassDef) {
                    if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                        state.emit(it)
                    }
                }
            }

            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                LLVMBuildRet(builder, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
            }

            currentFunctionReturnType = previousReturnType
            currentClassName = previousClass
            codegenSymbolTable = previousSymbols
        }
    }

    fun printIR() {
        with(state) {
            val ir = LLVMPrintModuleToString(module)
            println(ir.string)
            LLVMDisposeMessage(ir)
        }
    }

    fun writeIRToFile(filename: String) {
        with(state) {
            val ir = LLVMPrintModuleToString(module)
            java.io.File(filename).writeText(ir.string)
            LLVMDisposeMessage(ir)
        }
    }

    fun dispose() {
        with(state) {
            LLVMDisposeBuilder(builder)
            LLVMDisposeModule(module)
            LLVMContextDispose(context)
        }
    }

    fun visit(stmt: Stmt) = state.emit(stmt)
    fun visit(expr: Expr): LLVMValueRef = state.emit(expr)
}
